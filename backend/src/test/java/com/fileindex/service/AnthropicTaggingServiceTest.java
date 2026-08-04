package com.fileindex.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fileindex.config.AiProperties;
import com.fileindex.exception.AiUnavailableException;
import org.junit.jupiter.api.Test;

class AnthropicTaggingServiceTest {

    @Test
    void throwsWhenNoApiKeyIsConfigured() {
        AiProperties properties = new AiProperties();
        properties.setApiKey("");
        AnthropicTaggingService service = new AnthropicTaggingService(properties);

        assertThatThrownBy(() -> service.generateTags("a.txt", "content"))
            .isInstanceOf(AiUnavailableException.class)
            .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
