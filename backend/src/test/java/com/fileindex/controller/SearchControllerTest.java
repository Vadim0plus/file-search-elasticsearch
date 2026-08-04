package com.fileindex.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fileindex.dto.HighlightFragmentDto;
import com.fileindex.dto.SearchHitDto;
import com.fileindex.dto.SearchQuery;
import com.fileindex.dto.SearchResponseDto;
import com.fileindex.service.SearchService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import com.fileindex.config.AuthProperties;
import com.fileindex.config.SecurityConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AuthProperties.class)
@WithMockUser
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @Test
    void returnsSearchResultsWithHighlightsAndDownloadUrl() throws Exception {
        SearchResponseDto response = new SearchResponseDto(1, 0, 20, List.of(
            new SearchHitDto(
                "id1",
                "/data/a.txt",
                "a.txt",
                "txt",
                10,
                Instant.parse("2024-01-01T00:00:00Z"),
                List.of(List.of(new HighlightFragmentDto("hello", true))),
                "/api/files/id1/download"
            )
        ));
        when(searchService.search(any())).thenReturn(response);

        mockMvc.perform(get("/api/search").param("q", "hello"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.results[0].id").value("id1"))
            .andExpect(jsonPath("$.results[0].downloadUrl").value("/api/files/id1/download"))
            .andExpect(jsonPath("$.results[0].highlights[0][0].text").value("hello"))
            .andExpect(jsonPath("$.results[0].highlights[0][0].matched").value(true));
    }

    @Test
    void clampsOversizedPageSizeBeforeCallingSearchService() throws Exception {
        when(searchService.search(any())).thenReturn(new SearchResponseDto(0, 0, 100, List.of()));

        mockMvc.perform(get("/api/search").param("q", "x").param("size", "9999"))
            .andExpect(status().isOk());

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchService).search(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(100);
    }
}
