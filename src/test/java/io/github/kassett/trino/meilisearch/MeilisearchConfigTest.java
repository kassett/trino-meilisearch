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
    void requiresUrl()
    {
        assertThatThrownBy(() -> MeilisearchConfig.from(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("meilisearch.url");
    }
}
