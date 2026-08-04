package com.fileindex.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fileindex.config.AiProperties;
import com.fileindex.exception.AiUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Asks Claude to suggest tags for a file from its name and extracted content, using structured
 * outputs so the response is guaranteed to parse instead of needing a JSON-extraction retry loop.
 */
@Service
public class AnthropicTaggingService implements AiTaggingService {

    private static final Logger log = LoggerFactory.getLogger(AnthropicTaggingService.class);
    private static final int MIN_TAGS = 3;
    private static final int MAX_TAGS = 7;

    private static final String SYSTEM_PROMPT =
        """
        Ты помогаешь классифицировать файлы в системе поиска по документам. На основе имени \
        файла и его содержимого предложи от %d до %d коротких меток на русском языке в нижнем \
        регистре, отражающих тему, тип документа и ключевые сущности. Не используй расширение \
        файла как метку и не повторяй в метках само имя файла."""
            .formatted(MIN_TAGS, MAX_TAGS);

    private record TagSuggestions(
        @JsonPropertyDescription(
            "От " + MIN_TAGS + " до " + MAX_TAGS + " коротких меток на русском языке в нижнем регистре")
        List<String> tags) {
    }

    private final AiProperties properties;

    // Built once at startup rather than per-request: constructing the HTTP client is what's
    // expensive here, and a missing/blank key is a static configuration fact, not something
    // that can change between requests.
    private final AnthropicClient client;

    public AnthropicTaggingService(AiProperties properties) {
        this.properties = properties;
        this.client = properties.isEnabled()
            ? AnthropicOkHttpClient.builder().apiKey(properties.getApiKey()).build()
            : null;
    }

    @Override
    public List<String> generateTags(String fileName, String content) {
        if (client == null) {
            throw new AiUnavailableException("AI-теги не настроены: не задан ключ ANTHROPIC_API_KEY");
        }

        String userPrompt = "Имя файла: " + fileName + "\n\nСодержимое файла:\n"
            + truncate(content, properties.getMaxContentChars());

        StructuredMessageCreateParams<TagSuggestions> params = MessageCreateParams.builder()
            .model(properties.getModel())
            .maxTokens(1024L)
            .system(SYSTEM_PROMPT)
            .outputConfig(TagSuggestions.class)
            .addUserMessage(userPrompt)
            .build();

        try {
            return client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .findFirst()
                .map(block -> normalize(block.text().tags()))
                .orElseThrow(() -> new AiUnavailableException("AI не вернул метки"));
        } catch (AiUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // The SDK's error hierarchy differs by failure type (auth, rate limit, network,
            // malformed response) - collapse them all to one user-facing failure rather than
            // trying to react differently to each.
            log.warn("Anthropic tagging request failed", e);
            throw new AiUnavailableException("Не удалось получить метки от AI: " + e.getMessage(), e);
        }
    }

    private List<String> normalize(List<String> tags) {
        List<String> result = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) {
                continue;
            }
            String value = tag.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        return content.length() > maxChars ? content.substring(0, maxChars) : content;
    }
}
