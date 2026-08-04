package com.fileindex.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextExtractionServiceTest {

    private final TextExtractionService service = new TextExtractionService();

    @Test
    void extractsPlainTextContentAndDetectsMimeType(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "Hello Elasticsearch world");

        ExtractedContent result = service.extract(file);

        assertThat(result.text()).contains("Hello Elasticsearch world");
        assertThat(result.contentType()).startsWith("text/plain");
    }

    @Test
    void extractsMarkdownAsText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("readme.md");
        Files.writeString(file, "# Title\n\nSome searchable body text.");

        ExtractedContent result = service.extract(file);

        assertThat(result.text()).contains("Title", "Some searchable body text");
    }

    @Test
    void doesNotThrowOnUnparsableContent(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("corrupt.pdf");
        // Looks like a PDF (magic bytes) but has no valid structure after that.
        Files.write(file, new byte[] {0x25, 0x50, 0x44, 0x46, 0x00, 0x01, 0x02, 0x03});

        ExtractedContent result = service.extract(file);

        assertThat(result).isNotNull();
        assertThat(result.text()).isNotNull();
    }
}
