package com.fileindex.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

/**
 * Wraps Tika's AutoDetectParser directly (rather than the simplified Tika facade) so we can
 * also pull document metadata (author/title/creation date) alongside the extracted text, in
 * addition to text from any supported file (plain text, PDF, DOCX, XLSX, ...).
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final Parser parser = new AutoDetectParser();

    public ExtractedContent extract(Path file) throws IOException {
        Metadata metadata = new Metadata();
        // no cap on extracted text length: files here are already bounded by max-file-size-mb
        BodyContentHandler handler = new BodyContentHandler(-1);

        try (InputStream in = Files.newInputStream(file)) {
            parser.parse(in, handler, metadata, new ParseContext());
        } catch (TikaException | SAXException e) {
            log.warn("Failed to extract text from {}: {}", file, e.getMessage());
        }

        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        Date created = metadata.getDate(TikaCoreProperties.CREATED);

        return new ExtractedContent(
            handler.toString(),
            contentType,
            metadata.get(TikaCoreProperties.CREATOR),
            metadata.get(TikaCoreProperties.TITLE),
            created != null ? created.toInstant() : null
        );
    }
}
