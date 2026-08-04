package com.fileindex.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fileindex.dto.AddRootRequest;
import com.fileindex.dto.IndexRootDto;
import com.fileindex.dto.SearchResponseDto;
import com.fileindex.indexroot.IndexRootStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end tests against a real Elasticsearch (Testcontainers). Every test logs in first and
 * forwards the resulting session cookie on subsequent calls, since all /api/** endpoints now
 * require authentication.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FileIndexingIntegrationTest {

    @Container
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0")
    )
        .withEnv("xpack.security.enabled", "false")
        // ES startup time in this sandbox varies wildly (~20s to 90s+) depending on host load;
        // the default wait strategy timeout is too tight and causes flaky failures unrelated to
        // the code under test.
        .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders authHeaders;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private void login() {
        HttpHeaders formHeaders = new HttpHeaders();
        formHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", "admin");
        form.add("password", "admin");

        ResponseEntity<Void> response = restTemplate.exchange(
            baseUrl() + "/api/auth/login", HttpMethod.POST, new HttpEntity<>(form, formHeaders), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.COOKIE, response.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
    }

    private <T> T get(String path, Class<T> type, Object... uriVariables) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(authHeaders), type, uriVariables).getBody();
    }

    private <T> ResponseEntity<T> getEntity(String path, Class<T> type) {
        return restTemplate.exchange(baseUrl() + path, HttpMethod.GET, new HttpEntity<>(authHeaders), type);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.addAll(authHeaders);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(baseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, headers), type).getBody();
    }

    private void delete(String path) {
        restTemplate.exchange(baseUrl() + path, HttpMethod.DELETE, new HttpEntity<>(authHeaders), Void.class);
    }

    private SearchResponseDto search(String query) {
        // Pass the raw query as a URI template variable rather than pre-encoding it: RestTemplate
        // encodes template variables exactly once. Manually URL-encoding first and then handing
        // the result to RestTemplate double-encodes non-ASCII text (e.g. Cyrillic), producing a
        // garbled "q" the server can never match against.
        return get("/api/search?q={q}", SearchResponseDto.class, query);
    }

    @Test
    void scansSearchesDownloadsAndLiveUpdatesAcrossFilesystemChanges(@TempDir Path tempDir) throws IOException {
        login();
        Path file = tempDir.resolve("report.txt");
        Files.writeString(file, "The quarterly report contains searchable financial highlights.");

        IndexRootDto root = post("/api/roots", new AddRootRequest(tempDir.toString()), IndexRootDto.class);
        assertThat(root).isNotNull();
        assertThat(root.id()).isNotBlank();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            IndexRootDto current = get("/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.status()).isEqualTo(IndexRootStatus.WATCHING);
            assertThat(current.docCount()).isEqualTo(1);
        });

        SearchResponseDto initialSearch = search("quarterly");
        assertThat(initialSearch.total()).isEqualTo(1);
        String docId = initialSearch.results().get(0).id();
        assertThat(initialSearch.results().get(0).downloadUrl()).isEqualTo("/api/files/" + docId + "/download");
        assertThat(initialSearch.results().get(0).highlights()).isNotEmpty();

        ResponseEntity<String> download = getEntity(initialSearch.results().get(0).downloadUrl(), String.class);
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
            IndexRootDto current = get("/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.docCount()).isEqualTo(0);
        });

        delete("/api/roots/" + root.id());
    }

    @Test
    void searchMatchesRussianWordDeclensionsViaTheRussianAnalyzer(@TempDir Path tempDir) throws IOException {
        login();
        Path file = tempDir.resolve("doc.txt");
        // stored form is nominative singular "документов" not present; only "документами" (instrumental
        // plural) is in the text - a non-stemmed "standard" analyzer would NOT match a "документ" query.
        Files.writeString(file, "Мы работаем с документами и облаками данных.", StandardCharsets.UTF_8);

        IndexRootDto root = post("/api/roots", new AddRootRequest(tempDir.toString()), IndexRootDto.class);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            IndexRootDto current = get("/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.docCount()).isEqualTo(1);
        });

        SearchResponseDto result = search("документ");
        assertThat(result.total()).isEqualTo(1);

        delete("/api/roots/" + root.id());
    }

    @Test
    void uploadedFileIsIndexedByTheWatcherWithoutAnExplicitReindex(@TempDir Path tempDir) throws IOException {
        login();
        IndexRootDto root = post("/api/roots", new AddRootRequest(tempDir.toString()), IndexRootDto.class);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            IndexRootDto current = get("/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.status()).isEqualTo(IndexRootStatus.WATCHING);
        });

        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", new ByteArrayResource("Uploaded content mentioning spaceships.".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "uploaded.txt";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.addAll(authHeaders);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Void> uploadResponse = restTemplate.exchange(
            baseUrl() + "/api/roots/" + root.id() + "/upload",
            HttpMethod.POST,
            new HttpEntity<>(multipartBody, headers),
            Void.class
        );
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            SearchResponseDto result = search("spaceships");
            assertThat(result.total()).isEqualTo(1);
        });

        delete("/api/roots/" + root.id());
    }

    @Test
    void searchMatchesFileNameEvenThoughItIsNotPresentInTheContent(@TempDir Path tempDir) throws IOException {
        login();
        // The word "agreement" only appears in the file name, never in the body. The standard
        // tokenizer treats a dotted name like "agreement.txt" as a single token (same rule that
        // keeps "example.com" together) unless the fileName field uses the custom pattern
        // tokenizer, so this only passes once the filename analyzer actually splits it.
        Path file = tempDir.resolve("agreement.txt");
        Files.writeString(file, "Some unrelated content about the weather.", StandardCharsets.UTF_8);

        IndexRootDto root = post("/api/roots", new AddRootRequest(tempDir.toString()), IndexRootDto.class);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            IndexRootDto current = get("/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.docCount()).isEqualTo(1);
        });

        SearchResponseDto result = search("agreement");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.results().get(0).fileName()).isEqualTo("agreement.txt");

        delete("/api/roots/" + root.id());
    }

    @Test
    void emptyQueryBrowsesIndexedFilesSortedByMostRecentlyModifiedFirst(@TempDir Path tempDir) throws IOException {
        login();
        Path older = tempDir.resolve("older.txt");
        Path newer = tempDir.resolve("newer.txt");
        Files.writeString(older, "older file content");
        Files.writeString(newer, "newer file content");
        Files.setLastModifiedTime(older, FileTime.from(Instant.now().minusSeconds(3600)));
        Files.setLastModifiedTime(newer, FileTime.from(Instant.now()));

        IndexRootDto root = post("/api/roots", new AddRootRequest(tempDir.toString()), IndexRootDto.class);
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            IndexRootDto current = get("/api/roots/" + root.id(), IndexRootDto.class);
            assertThat(current.docCount()).isEqualTo(2);
        });

        SearchResponseDto result = search("");
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.results().get(0).fileName()).isEqualTo("newer.txt");
        assertThat(result.results().get(1).fileName()).isEqualTo("older.txt");

        delete("/api/roots/" + root.id());
    }
}
