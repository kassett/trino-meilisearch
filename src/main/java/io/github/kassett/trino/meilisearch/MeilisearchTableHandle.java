package io.github.kassett.trino.meilisearch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.SortOrder;

import java.util.List;
import java.util.Optional;

public record MeilisearchTableHandle(
        @JsonProperty String schemaName,
        @JsonProperty String indexName,
        @JsonProperty Optional<String> filter,
        @JsonProperty Optional<String> search,
        @JsonProperty Optional<Long> limit,
        @JsonProperty Optional<Sort> sort)
        implements ConnectorTableHandle
{
    @JsonCreator
    public MeilisearchTableHandle {}

    public MeilisearchTableHandle withFilter(Optional<String> filter)
    {
        return new MeilisearchTableHandle(schemaName, indexName, filter, search, limit, sort);
    }

    public MeilisearchTableHandle withSearch(Optional<String> search)
    {
        return new MeilisearchTableHandle(schemaName, indexName, filter, search, limit, sort);
    }

    public MeilisearchTableHandle withLimit(long limit)
    {
        if (this.limit.isPresent() && this.limit.get() <= limit) {
            return this;
        }
        return new MeilisearchTableHandle(schemaName, indexName, filter, search, Optional.of(limit), sort);
    }

    public MeilisearchTableHandle withSort(List<Sort> sorts)
    {
        return sorts.size() == 1 ? new MeilisearchTableHandle(schemaName, indexName, filter, search, limit, Optional.of(sorts.getFirst())) : this;
    }

    public record Sort(@JsonProperty String column, @JsonProperty SortOrder order)
    {
        @JsonCreator
        public Sort {}

        String meilisearch()
        {
            return column + ":" + (order == SortOrder.ASC_NULLS_FIRST || order == SortOrder.ASC_NULLS_LAST ? "asc" : "desc");
        }
    }
}
