package com.fileindex.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Wraps Apache Tika's facade (AutoDetectParser + BodyContentHandler under the hood)
 * to turn any supported file (plain text, PDF, DOCX, XLSX, ...) into extracted text
 * plus a detected MIME type.
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final Tika tika;

    public TextExtractionService() {
        this.tika = new Tika();
        // no cap on extracted text length: files here are already bounded by max-file-size-mb
        this.tika.setMaxStringLength(-1);
    }

    public ExtractedContent extract(Path file) throws IOException {
        String contentType;
        try {
            contentType = tika.detect(file);
        } catch (IOException e) {
            contentType = "application/octet-stream";
        }
        String text;
        try (InputStream in = Files.newInputStream(file)) {
            text = tika.parseToString(in);
        } catch (TikaException e) {
            log.warn("Failed to extract text from {}: {}", file, e.getMessage());
            text = "";
        }
        return new ExtractedContent(text, contentType);
    }
}
