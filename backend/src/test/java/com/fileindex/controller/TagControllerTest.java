package com.fileindex.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fileindex.config.AuthProperties;
import com.fileindex.config.SecurityConfig;
import com.fileindex.dto.TagCountDto;
import com.fileindex.dto.TagsDto;
import com.fileindex.exception.AiUnavailableException;
import com.fileindex.exception.NotFoundException;
import com.fileindex.service.TagService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TagController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AuthProperties.class)
@WithMockUser
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @Test
    void listsDistinctTagsWithCounts() throws Exception {
        when(tagService.listTags()).thenReturn(List.of(new TagCountDto("договор", 3), new TagCountDto("счёт", 1)));

        mockMvc.perform(get("/api/tags"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].tag").value("договор"))
            .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    void generatesTagsViaAi() throws Exception {
        when(tagService.generateTags("doc-1")).thenReturn(new TagsDto(List.of("договор", "аренда"), List.of("договор", "аренда")));

        mockMvc.perform(post("/api/files/doc-1/tags/generate"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[0]").value("договор"))
            .andExpect(jsonPath("$.aiTags[1]").value("аренда"));
    }

    @Test
    void returns503WhenAiIsNotConfigured() throws Exception {
        when(tagService.generateTags("doc-1")).thenThrow(new AiUnavailableException("не настроен ключ"));

        mockMvc.perform(post("/api/files/doc-1/tags/generate"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("не настроен ключ"));
    }

    @Test
    void addsTagManually() throws Exception {
        when(tagService.addTag(eq("doc-1"), eq("важное"))).thenReturn(new TagsDto(List.of("важное"), List.of()));

        mockMvc.perform(post("/api/files/doc-1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\": \"важное\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags[0]").value("важное"));
    }

    @Test
    void rejectsBlankTag() throws Exception {
        mockMvc.perform(post("/api/files/doc-1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\": \"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void removesTag() throws Exception {
        when(tagService.removeTag("doc-1", "важное")).thenReturn(new TagsDto(List.of(), List.of()));

        mockMvc.perform(delete("/api/files/doc-1/tags/важное"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void returns404ForUnknownFile() throws Exception {
        when(tagService.addTag("missing", "тег")).thenThrow(new NotFoundException("Файл не найден: missing"));

        mockMvc.perform(post("/api/files/missing/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag\": \"тег\"}"))
            .andExpect(status().isNotFound());
    }
}
