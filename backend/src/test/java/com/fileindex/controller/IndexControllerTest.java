package com.fileindex.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fileindex.indexroot.IndexRootStore;
import com.fileindex.service.FileIndexingService;
import com.fileindex.service.FileWatchService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IndexController.class)
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
}
