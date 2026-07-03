package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.SortOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class MeilisearchTableHandleTest
{
    @Test
    void keepsSmallerExistingLimit()
    {
        MeilisearchTableHandle handle = handle().withLimit(10);

        assertThat(handle.withLimit(20)).isSameAs(handle);
        assertThat(handle.withLimit(5).limit()).contains(5L);
    }

    @Test
    void acceptsOnlySingleSort()
    {
        MeilisearchTableHandle handle = handle();
        MeilisearchTableHandle.Sort sort = new MeilisearchTableHandle.Sort("rating", SortOrder.DESC_NULLS_LAST);

        assertThat(handle.withSort(List.of(sort)).sort()).contains(sort);
        assertThat(handle.withSort(List.of(sort, new MeilisearchTableHandle.Sort("title", SortOrder.ASC_NULLS_LAST)))).isSameAs(handle);
    }

    @Test
    void formatsSortForMeilisearch()
    {
        assertThat(new MeilisearchTableHandle.Sort("title", SortOrder.ASC_NULLS_FIRST).meilisearch()).isEqualTo("title:asc");
        assertThat(new MeilisearchTableHandle.Sort("title", SortOrder.DESC_NULLS_FIRST).meilisearch()).isEqualTo("title:desc");
    }

    private static MeilisearchTableHandle handle()
    {
        return new MeilisearchTableHandle("default", "movies", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
