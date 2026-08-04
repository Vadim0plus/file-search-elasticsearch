package com.fileindex.dto;

import jakarta.validation.constraints.NotBlank;

public record AddRootRequest(@NotBlank(message = "не должен быть пустым") String path) {
}
