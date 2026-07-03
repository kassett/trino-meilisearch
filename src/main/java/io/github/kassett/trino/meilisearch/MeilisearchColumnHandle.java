package io.github.kassett.trino.meilisearch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.type.Type;

public record MeilisearchColumnHandle(
        @JsonProperty String name,
        @JsonProperty Type type,
        @JsonProperty MeilisearchColumn.SpecialColumn specialColumn)
        implements ColumnHandle
{
    @JsonCreator
    public MeilisearchColumnHandle {}
}
