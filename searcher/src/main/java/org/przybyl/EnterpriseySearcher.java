/*
 * Copyright 2025 Marit van Dijk
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
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public class EnterpriseySearcher {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static String ENCODER_URL = System.getenv().getOrDefault("ENCODER_URL", "http://localhost:5555/encode_text");
    private static final int TOP_K = 5;

    public static void main(String[] args) {
//            List<String> queries = obtainQueries();
        List<String> queries = List.of("orange", "apple", "Heckscheibenwaschanlage", "computer", "basket");

        try (RestClient restClient = RestClient.builder(HttpHost.create(System.getenv("ES_URL")))
            .setDefaultHeaders(new org.apache.http.Header[]{
                new org.apache.http.message.BasicHeader("Authorization", "ApiKey " + System.getenv("ES_API_KEY"))
            })
            .build()) {

            JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
            ElasticsearchTransport transport = new RestClientTransport(restClient, jsonpMapper);
            ElasticsearchClient esClient = new ElasticsearchClient(transport);

            runSearch(queries, esClient, "catalogue")
                .forEach(System.out::println);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static List<String> obtainQueries() {
        List<String> queries = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter text to search: ");
        String searchText;
        while (!(searchText = scanner.nextLine()).isBlank()) {
            queries.add(searchText);
            System.out.print("Enter text to search: ");
        }
        return queries;
    }

    static List<SearchResult> runSearch(List<String> queries, ElasticsearchClient esClient, String indexName) {
        return queries.stream()
            .parallel()
            .map(query -> new QueryWithVector(query, obtainTextEmbedding(query)))
            .map(qwv -> executeSearch(qwv, indexName, esClient))
            .toList();
    }

    static SearchResult executeSearch(QueryWithVector qwv,
                                      String indexName,
                                      ElasticsearchClient esClient) {

        // kick off both searches on ForkJoinPool.commonPool()
        CompletableFuture<List<CatalogueItem>> knnSearchFuture =
            CompletableFuture.supplyAsync(() -> {
                try {
                    return performKnnSearch(qwv.getVector(), indexName, esClient);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });

        CompletableFuture<List<CatalogueItem>> classicSearchFuture =
            CompletableFuture.supplyAsync(() -> {
                try {
                    return performClassicSearch(qwv.getQuery(), indexName, esClient);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });

        // remember to cancel a future if the other one fails
        Function<Throwable, List<CatalogueItem>> cancelOther = ex -> {
            knnSearchFuture.cancel(true);
            classicSearchFuture.cancel(true);
            // re‑throw inside CompletionException so join() propagates it
            throw new CompletionException(ex);
        };
        knnSearchFuture.exceptionally(cancelOther);
        classicSearchFuture.exceptionally(cancelOther);

        return knnSearchFuture.thenCombine(classicSearchFuture, (k, c) -> {
                var combined = combineUsingRRF(Arrays.asList(k, c), 60, TOP_K);
                return new SearchResult(qwv.getQuery(), combined);
            })
            // waits, re‑throws on first failure
            .join();
    }

    static List<Float> obtainTextEmbedding(String text) {
        try {
            ObjectNode requestBody = OBJECT_MAPPER.createObjectNode();
            requestBody.put("text", text);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENCODER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to encode text. Status code: [%d], Response: [%s]"
                    .formatted(response.statusCode(), response.body()));
            }

            EmbeddingResponse embeddingResponse = OBJECT_MAPPER.readValue(response.body(), EmbeddingResponse.class);
            if (embeddingResponse.success()) {
                return embeddingResponse.embedding();
            }
            throw new IOException("Encoding operation failed: " + response.body());

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static List<CatalogueItem> performClassicSearch(String searchText, String indexName, ElasticsearchClient esClient) throws IOException {
        try {
            // First, check if the index exists
            checkTheIndexExists(esClient, indexName);

            // Build and execute the search request with a wildcard query to check if the filename contains searchText
            SearchResponse<CatalogueItem> response = esClient.search(s -> s
                    .index(indexName)
                    .query(q -> q
                        .wildcard(w -> w
                            .field("filename")
                            .wildcard("*" + searchText + "*")
                        )
                    )
                    .size(TOP_K)
                    .source(src -> src.filter(f -> f.includes("filename", "full-path", "price"))),
                CatalogueItem.class);

            return response.hits().hits().stream().map(Hit::source).toList();

        } catch (Exception e) {
            throw new IOException("Failed to perform BM25 search: " + e.getMessage(), e);
        }
    }

    /// Runs the vector search
    /// [See more](https://www.elastic.co/docs/solutions/search/vector/knn)
    static List<CatalogueItem> performKnnSearch(List<Float> queryVector, String indexName, ElasticsearchClient esClient) throws IOException {
        try {
            // First, check if the index exists
            checkTheIndexExists(esClient, indexName);

            // Set the number of desired nearest neighbors (k) and candidate oversampling factor
            int k = 5;
            int numCandidates = (int) (1.5 * k);

            // Build and execute the search request.
            // Note: The knn clause is added via the .knn() method.
            SearchResponse<CatalogueItem> response = esClient.search(s -> s
                    .index(indexName)
                    .knn(knn -> knn
                        .field("embedding")
                        .queryVector(queryVector)
                        .k(k)
                        .numCandidates(numCandidates)
                    )
                    // Also set the size of the search response to k
                    .size(k)
                    .source(src -> src.filter(f -> f.includes("filename", "full-path", "price"))),
                CatalogueItem.class);

            return response.hits().hits().stream().map(Hit::source).toList();

        } catch (Exception e) {
            throw new IOException("Failed to perform search: " + e.getMessage(), e);
        }
    }

    static void checkTheIndexExists(ElasticsearchClient esClient, String indexName) throws IOException {
        boolean indexExists = esClient.indices().exists(e -> e.index(indexName)).value();
        if (!indexExists) {
            throw new IOException("Index '" + indexName + "' does not exist");
        }
    }

    /// Merges results of various search algorithms
    /// [See more](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion)
    static List<CatalogueItem> combineUsingRRF(List<List<CatalogueItem>> searchResults, int k, int rankWindowSize) {
        // Create a map to store all unique items and their RRF scores
        Map<String, CatalogueItem> itemMap = new HashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        // Process all search results
        for (List<CatalogueItem> resultList : searchResults) {
            for (int i = 0; i < resultList.size(); i++) {
                CatalogueItem item = resultList.get(i);
                int rank = i + 1; // Ranks start from 1
                double score = 1.0 / (k + rank);

                itemMap.put(item.fullPath(), item);
                // Add to existing score if item already exists, otherwise set a new score
                rrfScores.merge(item.fullPath(), score, Double::sum);
            }
        }

        // Sort items by their RRF scores in descending order
        return itemMap.values().stream()
            .sorted((a, b) -> Double.compare(rrfScores.get(b.fullPath()), rrfScores.get(a.fullPath())))
            .limit(rankWindowSize) // Limit to rankWindowSize results
            .toList();
    }

    // Allow setting the encoder URL for testing
    public static void setEncoderUrl(String url) {
        ENCODER_URL = url;
    }
}

final class QueryWithVector {
    private final String query;
    private final List<Float> vector;

    QueryWithVector(String query, List<Float> vector) {
        this.query = query;
        this.vector = vector;
    }

    @Override
    public String toString() {
        return "QueryWithVector[" +
            "query=" + query + ", " +
            "vector=" + vector + ']';
    }

    public String getQuery() {
        return query;
    }

    public List<Float> getVector() {
        return vector;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (QueryWithVector) obj;
        return Objects.equals(this.query, that.query) &&
            Objects.equals(this.vector, that.vector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(query, vector);
    }

}

record Price(BigDecimal value, String currency) {
    @Override
    public String toString() {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
    }
}

record CatalogueItem(String filename, @JsonProperty("full-path") String fullPath, Price price) {
    public String toPriceString() {
        return String.format("%s (%s)", filename, formatPrice());
    }

    private String formatPrice() {
        if (price == null) {
            return "priceless";
        } else {
            return price.toString();
        }
    }
}

record SearchResult(String query, List<CatalogueItem> items) {
    @Override
    public String toString() {
        return String.format("%s: %s", query, items().stream().map(CatalogueItem::toPriceString).toList());
    }
}

record EmbeddingResponse(boolean success, int dimensions, List<Float> embedding) {
    @Override
    public String toString() {
        return new StringJoiner(", ", EmbeddingResponse.class.getSimpleName() + "[", "]")
            .add("success=" + success)
            .add("dimensions=" + dimensions)
            .add("embedding=" + embedding.subList(0, Math.min(embedding.size(), 5)))
            .toString();
    }
}
