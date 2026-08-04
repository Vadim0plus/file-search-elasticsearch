package com.fileindex.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Empty by default - AI tagging is disabled until an API key is configured. */
    private String apiKey = "";
    private String model = "claude-opus-5";

    /** Content sent to the model is truncated to this many characters to bound prompt cost. */
    private int maxContentChars = 8000;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
