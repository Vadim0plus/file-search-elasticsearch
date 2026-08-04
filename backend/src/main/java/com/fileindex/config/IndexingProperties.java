package com.fileindex.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.indexing")
public class IndexingProperties {

    private long maxFileSizeMb = 50;
    private int batchSize = 200;
    private long watchDebounceMs = 500;
    private List<String> excludedDirs = List.of(".git", "node_modules", "target", "build", "dist", ".idea");
    private String defaultRoot;

    public long maxFileSizeBytes() {
        return maxFileSizeMb * 1024 * 1024;
    }
}
