/*
 * Copyright 2025 Piotr Przybył
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.przybyl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import static org.assertj.core.api.Assertions.assertThat;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.util.List;

@Testcontainers(parallel = true)
public class EnterpriseySearcherIntTest {

    private static final String ELASTICSEARCH_VERSION = "9.0.0";
    private static final String INDEX_NAME = "catalogue";

    @Container
    private static final ElasticsearchContainer elasticsearchContainer =
        new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch").withTag(ELASTICSEARCH_VERSION))
            .withCopyToContainer(MountableFile.forClasspathResource("mapping.json"), "/tmp/mapping.json")
            .withCopyToContainer(MountableFile.forClasspathResource("data.ndjson"), "/tmp/data.ndjson")
            .withCopyToContainer(MountableFile.forClasspathResource("prices.ndjson"), "/tmp/prices.ndjson");

    // See image-text-encoder/README.md for details on how to build the Docker image.
    @Container
    private static final GenericContainer<?> textEncoderContainer = new GenericContainer<>(
            DockerImageName.parse("image-text-encoder:latest"))
            .withExposedPorts(5555)
            .waitingFor(Wait.forHttp("/health").forStatusCode(200));

    private static ElasticsearchClient esClient;

    @BeforeAll
    static void setup() throws IOException, InterruptedException {
        // Configure EnterpriseySearcher to use the text encoder container
        String textEncoderUrl = "http://" + textEncoderContainer.getHost() + ":" + textEncoderContainer.getMappedPort(5555) + "/encode_text";
        EnterpriseySearcher.setEncoderUrl(textEncoderUrl);

        // Create the low-level client with SSL context
        RestClientBuilder builder = RestClient.builder(
            HttpHost.create("https://" + elasticsearchContainer.getHttpHostAddress())
        );

        // Configure basic authentication
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            httpClientBuilder.setSSLContext(elasticsearchContainer.createSslContextFromCa());
            BasicCredentialsProvider credentialsProvider =
                new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials("elastic", "changeme")
            );
            return httpClientBuilder.setDefaultCredentialsProvider(
                credentialsProvider
            );
        });

        // Create the transport with a Jackson mapper
        RestClient restClient = builder.build();
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
        ElasticsearchTransport transport = new RestClientTransport(restClient, jsonpMapper);

        // Create the API client
        esClient = new ElasticsearchClient(transport);

        importElasticsearchData();
    }

    @AfterAll
    static void closeClient() throws IOException {
        if (esClient != null) {
            esClient.close();
        }
    }

    private static void importElasticsearchData() throws IOException, InterruptedException {

        var result = elasticsearchContainer.execInContainer(
            "curl", "https://localhost:9200/"+INDEX_NAME, "-u", "elastic:changeme",
            "--cacert", "/usr/share/elasticsearch/config/certs/http_ca.crt",
            "-X", "PUT",
            "-H", "Content-Type: application/json",
            "--data-binary", "@/tmp/mapping.ndjson"
        );
        assert result.getExitCode() == 0;

        result = elasticsearchContainer.execInContainer(
            "curl", "https://localhost:9200/_bulk?refresh=true", "-u", "elastic:changeme",
            "--cacert", "/usr/share/elasticsearch/config/certs/http_ca.crt",
            "-X", "POST",
            "-H", "Content-Type: application/x-ndjson",
            "--data-binary", "@/tmp/data.ndjson"
        );
        assert result.getExitCode() == 0;

        result = elasticsearchContainer.execInContainer(
            "curl", "https://localhost:9200/_bulk?refresh=true", "-u", "elastic:changeme",
            "--cacert", "/usr/share/elasticsearch/config/certs/http_ca.crt",
            "-X", "POST",
            "-H", "Content-Type: application/x-ndjson",
            "--data-binary", "@/tmp/prices.ndjson"
        );
        assert result.getExitCode() == 0;
    }

    @Test
    void testRunSearch() {
        // Given
        List<String> queries = List.of("orange", "apple", "Heckscheibenwaschanlage", "basket");

        // When
        List<SearchResult> results = EnterpriseySearcher.runSearch(queries, esClient, INDEX_NAME);

        // Then
        assertThat(results).hasSize(queries.size());

        // Verify orange results
        SearchResult orangeResult = results.getFirst();
        assertThat(orangeResult.query()).isEqualTo("orange");
        assertThat(orangeResult.toString()).containsSubsequence(
            "fruit",
            "irrigation",
            "oxford-shoes",
            "apples",
            "plums");


        // Verify apple results
        SearchResult appleResult = results.get(1);
        assertThat(appleResult.query()).isEqualTo("apple");
        assertThat(appleResult.toString()).containsSubsequence(
            "apples-2607193_1280.jpg",
            "apple-1868496_1280.jpg",
            "apple-256261_1280.jpg",
            "plums-940100_1280.jpg",
            "irrigation-2535785_1280.jpg"
        );

        // Verify Heckscheibenwaschanlage results
        SearchResult heckscheibenwaschanlageResult = results.get(2);
        assertThat(heckscheibenwaschanlageResult.query()).isEqualTo("Heckscheibenwaschanlage");
        assertThat(heckscheibenwaschanlageResult.toString()).containsSubsequence(
                "irrigation-2535785_1280.jpg",
                "water-sprinkler-2804691_1280.jpg"
        );

        // Verify basket results
        SearchResult basketResult = results.get(3);
        assertThat(basketResult.query()).isEqualTo("basket");
        assertThat(basketResult.toString()).containsSubsequence(
                "apples-2607193_1280.jpg",
                "irrigation-2535785_1280.jpg",
                "plums-1649602_1280.jpg",
                "portrait-5833683_1280.jpg",
                "apple-1868496_1280.jpg"
        );
    }
}
