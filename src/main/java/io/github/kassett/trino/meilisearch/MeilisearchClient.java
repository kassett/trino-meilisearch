package io.github.kassett.trino.meilisearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MeilisearchClient
{
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final MeilisearchConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public MeilisearchClient(MeilisearchConfig config)
    {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    public List<MeilisearchIndex> listIndexes()
    {
        List<MeilisearchIndex> indexes = new ArrayList<>();
        int offset = 0;
        int limit = 100;
        while (true) {
            JsonNode response = get("/indexes?offset=" + offset + "&limit=" + limit);
            for (JsonNode node : response.path("results")) {
                indexes.add(new MeilisearchIndex(
                        node.path("uid").asText(),
                        node.path("primaryKey").isNull() ? Optional.empty() : Optional.ofNullable(node.path("primaryKey").asText(null))));
            }
            int total = response.path("total").asInt(indexes.size());
            if (indexes.size() >= total || response.path("results").isEmpty()) {
                return indexes;
            }
            offset += limit;
        }
    }

    public Optional<MeilisearchIndex> getIndex(String uid)
    {
        return listIndexes().stream()
                .filter(index -> index.uid().equals(uid))
                .findFirst();
    }

    public MeilisearchSettings getSettings(String index)
    {
        JsonNode response = get("/indexes/" + encode(index) + "/settings");
        return new MeilisearchSettings(
                strings(response.path("filterableAttributes")),
                strings(response.path("sortableAttributes")));
    }

    public DocumentPage documents(String index, List<String> fields, Optional<String> filter, Optional<String> sort, int offset, int limit)
    {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("offset", Integer.toString(offset));
        params.put("limit", Integer.toString(limit));
        if (!fields.isEmpty()) {
            params.put("fields", String.join(",", fields));
        }
        filter.ifPresent(value -> params.put("filter", value));
        sort.ifPresent(value -> params.put("sort", value));
        JsonNode response = get("/indexes/" + encode(index) + "/documents?" + query(params));
        return new DocumentPage(objects(response.path("results")), response.path("total").asInt());
    }

    public DocumentPage search(String index, String query, List<String> fields, int offset, int limit)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("offset", offset);
        body.put("limit", limit);
        if (!fields.isEmpty()) {
            body.put("attributesToRetrieve", fields);
        }
        JsonNode response = post("/indexes/" + encode(index) + "/search", body);
        int total = response.path("estimatedTotalHits").asInt(response.path("totalHits").asInt());
        return new DocumentPage(objects(response.path("hits")), total);
    }

    public String toJson(Object value)
    {
        try {
            return mapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    private JsonNode get(String path)
    {
        return send(request(path).GET().build());
    }

    private JsonNode post(String path, Object body)
    {
        return send(request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build());
    }

    private HttpRequest.Builder request(String path)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(config.url().resolve(path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json");
        config.apiKey().ifPresent(apiKey -> builder.header("Authorization", "Bearer " + apiKey));
        return builder;
    }

    private JsonNode send(HttpRequest request)
    {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Meilisearch request failed: " + response.statusCode() + " " + response.body());
            }
            return mapper.readTree(response.body());
        }
        catch (IOException e) {
            throw new RuntimeException("Meilisearch request failed: " + request.uri(), e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Meilisearch request interrupted: " + request.uri(), e);
        }
    }

    private List<String> strings(JsonNode node)
    {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode value : node) {
                values.add(value.asText());
            }
        }
        return values;
    }

    private List<Map<String, Object>> objects(JsonNode node)
    {
        List<Map<String, Object>> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode value : node) {
            values.add(mapper.convertValue(value, MAP_TYPE));
        }
        return values;
    }

    private static String query(Map<String, String> params)
    {
        return params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record MeilisearchIndex(String uid, Optional<String> primaryKey) {}

    public record MeilisearchSettings(List<String> filterableAttributes, List<String> sortableAttributes) {}

    public record DocumentPage(List<Map<String, Object>> documents, int total) {}
}
