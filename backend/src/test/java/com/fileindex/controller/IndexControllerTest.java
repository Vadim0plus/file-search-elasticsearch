package com.fileindex.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fileindex.config.AuthProperties;
import com.fileindex.config.SecurityConfig;
import com.fileindex.indexroot.IndexRoot;
import com.fileindex.indexroot.IndexRootStore;
import com.fileindex.service.FileIndexingService;
import com.fileindex.service.FileWatchService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Imports the real SecurityConfig (not just @WithMockUser) so CSRF-disabled behavior is
// exercised too - the default Boot security auto-configuration loaded otherwise still enables
// CSRF, which would 403 every POST/DELETE here regardless of authentication.
@WebMvcTest(IndexController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AuthProperties.class)
@WithMockUser
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexRootStore store;

    @MockitoBean
    private FileIndexingService fileIndexingService;

    @MockitoBean
    private FileWatchService fileWatchService;

    @Test
    void rejectsPathThatDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/roots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"/definitely/not/a/real/path/xyz\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void rejectsBlankPath() throws Exception {
        mockMvc.perform(post("/api/roots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"path\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns404WhenReindexingUnknownRoot() throws Exception {
        when(store.find("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/roots/missing/reindex"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns404WhenRemovingUnknownRoot() throws Exception {
        when(store.find("missing")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/roots/missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void uploadedFileIsSavedUnderTheRootDirectoryWithSanitizedName(@TempDir Path tempDir) throws Exception {
        IndexRoot root = new IndexRoot("r1", tempDir);
        when(store.find("r1")).thenReturn(Optional.of(root));

        MockMultipartFile file = new MockMultipartFile("file", "../../evil.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/roots/r1/upload").file(file))
            .andExpect(status().isAccepted());

        assertThat(Files.exists(tempDir.resolve("evil.txt"))).isTrue();
        assertThat(Files.readString(tempDir.resolve("evil.txt"))).isEqualTo("hello");
        // no path escaped the root: nothing was written outside tempDir
        assertThat(tempDir.getParent()).satisfies(parent ->
            assertThat(Files.exists(parent.resolve("evil.txt"))).isFalse());
    }

    @Test
    void rejectsUploadWithNoFileSelected(@TempDir Path tempDir) throws Exception {
        IndexRoot root = new IndexRoot("r1", tempDir);
        when(store.find("r1")).thenReturn(Optional.of(root));

        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/api/roots/r1/upload").file(emptyFile))
            .andExpect(status().isBadRequest());
    }

    @Test
    void returns404WhenUploadingToUnknownRoot() throws Exception {
        when(store.find("missing")).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes());

        mockMvc.perform(multipart("/api/roots/missing/upload").file(file))
            .andExpect(status().isNotFound());
    }
}
