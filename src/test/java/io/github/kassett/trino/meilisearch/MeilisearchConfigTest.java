package io.github.kassett.trino.meilisearch;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class MeilisearchConfigTest
{
    @Test
    void parsesDefaults()
    {
        MeilisearchConfig config = MeilisearchConfig.from(Map.of("meilisearch.url", "http://localhost:7700"));

        assertThat(config.url()).isEqualTo(URI.create("http://localhost:7700"));
        assertThat(config.apiKey()).isEmpty();
        assertThat(config.schemaName()).isEqualTo("default");
        assertThat(config.schemaSampleSize()).isEqualTo(100);
        assertThat(config.pageSize()).isEqualTo(100);
    }

    @Test
    void parsesExplicitProperties()
    {
        MeilisearchConfig config = MeilisearchConfig.from(Map.of(
                "meilisearch.url", "http://meilisearch:7700",
                "meilisearch.api-key", "masterKey",
                "meilisearch.schema-name", "search",
                "meilisearch.schema.sample-size", "25",
                "meilisearch.page-size", "500",
                "meilisearch.schema-file", "schema.json"));

        assertThat(config.url()).isEqualTo(URI.create("http://meilisearch:7700"));
        assertThat(config.apiKey()).contains("masterKey");
        assertThat(config.schemaName()).isEqualTo("search");
        assertThat(config.schemaSampleSize()).isEqualTo(25);
        assertThat(config.pageSize()).isEqualTo(500);
        assertThat(config.schemaFile()).hasValueSatisfying(path -> assertThat(path.toString()).isEqualTo("schema.json"));
    }

    @Test
    void ignoresBlankOptionalProperties()
    {
        MeilisearchConfig config = MeilisearchConfig.from(Map.of(
                "meilisearch.url", "http://localhost:7700",
                "meilisearch.api-key", " ",
                "meilisearch.schema-file", " "));

        assertThat(config.apiKey()).isEmpty();
        assertThat(config.schemaFile()).isEmpty();
    }

    @Test
    void requiresUrl()
    {
        assertThatThrownBy(() -> MeilisearchConfig.from(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meilisearch.url");
    }

    @Test
    void rejectsNonPositiveSizes()
    {
        assertThatThrownBy(() -> MeilisearchConfig.from(Map.of(
                "meilisearch.url", "http://localhost:7700",
                "meilisearch.page-size", "0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meilisearch.page-size");

        assertThatThrownBy(() -> MeilisearchConfig.from(Map.of(
                "meilisearch.url", "http://localhost:7700",
                "meilisearch.schema.sample-size", "-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meilisearch.schema.sample-size");
    }
}
