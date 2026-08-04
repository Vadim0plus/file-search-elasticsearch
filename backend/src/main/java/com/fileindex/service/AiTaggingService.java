package com.fileindex.service;

import java.util.List;

/** Generates content-based tags for a file using an LLM. */
public interface AiTaggingService {

    List<String> generateTags(String fileName, String content);
}
