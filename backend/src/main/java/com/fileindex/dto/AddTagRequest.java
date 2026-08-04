package com.fileindex.dto;

import jakarta.validation.constraints.NotBlank;

public record AddTagRequest(@NotBlank(message = "не должна быть пустой") String tag) {
}
