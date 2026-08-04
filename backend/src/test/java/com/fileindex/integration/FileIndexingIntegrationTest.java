package com.fileindex.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fileindex.dto.AddRootRequest;
import com.fileindex.dto.IndexRootDto;
import com.fileindex.dto.SearchResponseDto;
import com.fileindex.indexroot.IndexRootStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end test against a real Elasticsearch (Testcontainers): register a root, verify the
 * initial scan indexes its files, verify search + download, then verify the live WatchService
 * picks up an on-disk modification and deletion without a manual reindex call.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FileIndexingIntegrationTest {

    @Container
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0")
    ).withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void scansSearchesDownloadsAndLiveUpdatesAcrossFilesystemChanges(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("report.txt");
        Files.writeString(file, "The quarterly report contains searchable financial highlights.");

        IndexRootDto root = restTemplate.postForObject(
            baseUrl() + "/api/roots",
            new AddRootRequest(tempDir.toString()),
            IndexRootDto.class
        );
        assertThat(root).isNotNull();
        assertThat(root.id()).isNotBlank();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            IndexRootDto current = restTemplate.getForObject(baseUrl() + "/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.status()).isEqualTo(IndexRootStatus.WATCHING);
            assertThat(current.docCount()).isEqualTo(1);
        });

        SearchResponseDto initialSearch = search("quarterly");
        assertThat(initialSearch.total()).isEqualTo(1);
        String docId = initialSearch.results().get(0).id();
        assertThat(initialSearch.results().get(0).downloadUrl()).isEqualTo("/api/files/" + docId + "/download");
        assertThat(initialSearch.results().get(0).highlights()).isNotEmpty();

        ResponseEntity<String> download = restTemplate.getForEntity(baseUrl() + initialSearch.results().get(0).downloadUrl(), String.class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).contains("quarterly report");

        // watcher: modifying the file on disk should update the index without a manual reindex call
        Files.writeString(file, "Completely rewritten content mentioning astronomy instead.");
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            SearchResponseDto afterEdit = search("astronomy");
            assertThat(afterEdit.total()).isEqualTo(1);
        });
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            SearchResponseDto staleSearch = search("quarterly");
            assertThat(staleSearch.total()).isEqualTo(0);
        });

        // watcher: deleting the file on disk should remove it from the index
        Files.delete(file);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            IndexRootDto current = restTemplate.getForObject(baseUrl() + "/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.docCount()).isEqualTo(0);
        });

        restTemplate.delete(baseUrl() + "/api/roots/" + root.id());
    }

    private SearchResponseDto search(String query) {
        return restTemplate.getForObject(baseUrl() + "/api/search?q=" + query, SearchResponseDto.class);
    }
}
