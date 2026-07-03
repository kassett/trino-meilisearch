package io.github.kassett.trino.meilisearch;

import io.trino.spi.type.Type;

public record MeilisearchColumn(String name, Type type, SpecialColumn specialColumn)
{
    public enum SpecialColumn
    {
        NONE,
        ID,
        JSON,
        SEARCH
    }
}
