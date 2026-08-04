package com.fileindex.controller;

import com.fileindex.dto.AddTagRequest;
import com.fileindex.dto.TagCountDto;
import com.fileindex.dto.TagsDto;
import com.fileindex.service.TagService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** All distinct tags in the index with document counts, for filter chips / autocomplete. */
    @GetMapping("/api/tags")
    public List<TagCountDto> listTags() throws IOException {
        return tagService.listTags();
    }

    @PostMapping("/api/files/{id}/tags/generate")
    public TagsDto generateTags(@PathVariable String id) {
        return tagService.generateTags(id);
    }

    @PostMapping("/api/files/{id}/tags")
    public TagsDto addTag(@PathVariable String id, @Valid @RequestBody AddTagRequest request) {
        return tagService.addTag(id, request.tag());
    }

    @DeleteMapping("/api/files/{id}/tags/{tag}")
    public TagsDto removeTag(@PathVariable String id, @PathVariable String tag) {
        return tagService.removeTag(id, tag);
    }
}
