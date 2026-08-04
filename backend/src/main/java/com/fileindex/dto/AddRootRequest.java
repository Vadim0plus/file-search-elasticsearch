package com.fileindex.dto;

import jakarta.validation.constraints.NotBlank;

public record AddRootRequest(@NotBlank String path) {
}
