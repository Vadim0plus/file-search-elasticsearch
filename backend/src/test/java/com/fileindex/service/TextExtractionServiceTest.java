package com.fileindex.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
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

    @Test
    void plainTextHasNoDocumentMetadata(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "no metadata here");

        ExtractedContent result = service.extract(file);

        assertThat(result.author()).isNull();
        assertThat(result.title()).isNull();
    }

    @Test
    void extractsAuthorAndTitleFromDocxCoreProperties(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("report.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Searchable body text.");

            POIXMLProperties.CoreProperties coreProperties = document.getProperties().getCoreProperties();
            coreProperties.setCreator("Иван Иванов");
            coreProperties.setTitle("Квартальный отчёт");

            try (OutputStream out = Files.newOutputStream(file)) {
                document.write(out);
            }
        }

        ExtractedContent result = service.extract(file);

        assertThat(result.text()).contains("Searchable body text.");
        assertThat(result.author()).isEqualTo("Иван Иванов");
        assertThat(result.title()).isEqualTo("Квартальный отчёт");
    }
}
