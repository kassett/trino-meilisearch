package io.github.kassett.trino.meilisearch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.type.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.ID;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.JSON;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.NONE;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.SEARCH;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.VarcharType.VARCHAR;

public final class SchemaResolver
{
    private static final TypeReference<Map<String, Map<String, String>>> OVERRIDE_TYPE = new TypeReference<>() {};

    private final MeilisearchConfig config;
    private final MeilisearchClient client;
    private final Type jsonType;
    private final ObjectMapper mapper = new ObjectMapper();

    public SchemaResolver(MeilisearchConfig config, MeilisearchClient client, Type jsonType)
    {
        this.config = config;
        this.client = client;
        this.jsonType = jsonType;
    }

    public List<MeilisearchColumn> columns(String index)
    {
        Map<String, Type> fields = new LinkedHashMap<>();
        MeilisearchClient.DocumentPage page = client.documents(index, List.of(), java.util.Optional.empty(), java.util.Optional.empty(), 0, config.schemaSampleSize());
        for (Map<String, Object> document : page.documents()) {
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                fields.merge(entry.getKey(), infer(entry.getValue()), this::merge);
            }
        }
        loadOverrides().getOrDefault(index, Map.of()).forEach((name, typeName) -> fields.put(name, parseType(typeName)));

        List<MeilisearchColumn> columns = new ArrayList<>();
        columns.add(new MeilisearchColumn("_id", VARCHAR, ID));
        columns.add(new MeilisearchColumn("_json", jsonType, JSON));
        columns.add(new MeilisearchColumn("_search", VARCHAR, SEARCH));
        fields.forEach((name, type) -> {
            if (!name.equals("_id") && !name.equals("_json") && !name.equals("_search")) {
                columns.add(new MeilisearchColumn(name, type, NONE));
            }
        });
        return columns;
    }

    public List<ColumnMetadata> columnMetadata(String index)
    {
        return columns(index).stream()
                .map(column -> ColumnMetadata.builder()
                        .setName(column.name())
                        .setType(column.type())
                        .build())
                .toList();
    }

    private Map<String, Map<String, String>> loadOverrides()
    {
        return config.schemaFile()
                .map(path -> {
                    try {
                        return mapper.readValue(path.toFile(), OVERRIDE_TYPE);
                    }
                    catch (IOException e) {
                        throw new RuntimeException("Failed to read schema override file: " + path, e);
                    }
                })
                .orElse(Map.of());
    }

    private Type infer(Object value)
    {
        if (value instanceof Boolean) {
            return BOOLEAN;
        }
        if (value instanceof Integer || value instanceof Long) {
            return BIGINT;
        }
        if (value instanceof Float || value instanceof Double) {
            return DOUBLE;
        }
        if (value instanceof String) {
            return VARCHAR;
        }
        return jsonType;
    }

    private Type merge(Type left, Type right)
    {
        if (left.equals(right)) {
            return left;
        }
        if ((left.equals(BIGINT) && right.equals(DOUBLE)) || (left.equals(DOUBLE) && right.equals(BIGINT))) {
            return DOUBLE;
        }
        return jsonType;
    }

    private Type parseType(String typeName)
    {
        return switch (typeName.toLowerCase(Locale.ROOT)) {
            case "boolean" -> BOOLEAN;
            case "bigint", "integer", "int" -> BIGINT;
            case "double", "float", "real" -> DOUBLE;
            case "json" -> jsonType;
            case "varchar", "string" -> VARCHAR;
            default -> throw new IllegalArgumentException("Unsupported schema override type: " + typeName);
        };
    }
}
