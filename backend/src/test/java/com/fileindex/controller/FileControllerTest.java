package com.fileindex.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fileindex.model.IndexedFileDocument;
import com.fileindex.repository.IndexedFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import com.fileindex.config.AuthProperties;
import com.fileindex.config.SecurityConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FileController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AuthProperties.class)
@WithMockUser
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexedFileRepository repository;

    @Test
    void returns404WhenDocumentUnknown() throws Exception {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/files/missing/download")).andExpect(status().isNotFound());
    }

    @Test
    void returns404WhenIndexedFileWasDeletedFromDisk(@TempDir Path tempDir) throws Exception {
        Path neverCreated = tempDir.resolve("ghost.txt");
        IndexedFileDocument doc = IndexedFileDocument.builder()
            .id("ghost-id")
            .path(neverCreated.toString())
            .fileName("ghost.txt")
            .contentType("text/plain")
            .build();
        when(repository.findById("ghost-id")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/files/ghost-id/download")).andExpect(status().isNotFound());
    }

    @Test
    void streamsExistingFileWithAttachmentHeader(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        IndexedFileDocument doc = IndexedFileDocument.builder()
            .id("hello-id")
            .path(file.toString())
            .fileName("hello.txt")
            .contentType("text/plain")
            .build();
        when(repository.findById("hello-id")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/files/hello-id/download"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("hello.txt")))
            .andExpect(content().string("hello world"));
    }

    @Test
    void streamsExistingFileWithInlineDispositionForPreview(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello world");
        IndexedFileDocument doc = IndexedFileDocument.builder()
            .id("hello-id")
            .path(file.toString())
            .fileName("hello.txt")
            .contentType("text/plain")
            .build();
        when(repository.findById("hello-id")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/files/hello-id/preview"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")));
    }

    @Test
    void returnsDetailWithMetadataAndTruncationFlag() throws Exception {
        IndexedFileDocument doc = IndexedFileDocument.builder()
            .id("doc-id")
            .path("/data/report.docx")
            .fileName("report.docx")
            .extension("docx")
            .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .sizeBytes(1024)
            .author("Иван Иванов")
            .documentTitle("Квартальный отчёт")
            .content("short content")
            .build();
        when(repository.findById("doc-id")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/files/doc-id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.author").value("Иван Иванов"))
            .andExpect(jsonPath("$.title").value("Квартальный отчёт"))
            .andExpect(jsonPath("$.content").value("short content"))
            .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void truncatesLongContentInDetailResponse() throws Exception {
        String longContent = "a".repeat(60_000);
        IndexedFileDocument doc = IndexedFileDocument.builder()
            .id("long-id")
            .path("/data/big.txt")
            .fileName("big.txt")
            .content(longContent)
            .build();
        when(repository.findById("long-id")).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/files/long-id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.truncated").value(true));
    }
}
