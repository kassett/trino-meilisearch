package io.github.kassett.trino.meilisearch;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public record MeilisearchConfig(
        URI url,
        Optional<String> apiKey,
        String schemaName,
        int schemaSampleSize,
        int pageSize,
        Optional<Path> schemaFile)
{
    static MeilisearchConfig from(Map<String, String> properties)
    {
        URI url = URI.create(require(properties, "meilisearch.url"));
        String schemaName = properties.getOrDefault("meilisearch.schema-name", "default");
        int sampleSize = parsePositive(properties, "meilisearch.schema.sample-size", 100);
        int pageSize = parsePositive(properties, "meilisearch.page-size", 100);
        return new MeilisearchConfig(
                url,
                Optional.ofNullable(properties.get("meilisearch.api-key")).filter(value -> !value.isBlank()),
                schemaName,
                sampleSize,
                pageSize,
                Optional.ofNullable(properties.get("meilisearch.schema-file")).filter(value -> !value.isBlank()).map(Path::of));
    }

    private static String require(Map<String, String> properties, String name)
    {
        String value = properties.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required catalog property: " + name);
        }
        return value;
    }

    private static int parsePositive(Map<String, String> properties, String name, int defaultValue)
    {
        String value = properties.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }
}
