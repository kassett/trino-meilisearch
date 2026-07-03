package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.airlift.slice.Slices.utf8Slice;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.NONE;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.SEARCH;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

final class SearchPredicateTest
{
    @Test
    void extractsSingleSearchValue()
    {
        ColumnHandle search = new MeilisearchColumnHandle("_search", VARCHAR, SEARCH);

        assertThat(SearchPredicate.from(new Constraint(TupleDomain.withColumnDomains(Map.of(
                search,
                Domain.singleValue(VARCHAR, utf8Slice("space opera")))))))
                .contains("space opera");
    }

    @Test
    void ignoresNonSearchColumns()
    {
        ColumnHandle title = new MeilisearchColumnHandle("title", VARCHAR, NONE);

        assertThat(SearchPredicate.from(new Constraint(TupleDomain.withColumnDomains(Map.of(
                title,
                Domain.singleValue(VARCHAR, utf8Slice("Arrival")))))))
                .isEmpty();
    }

    @Test
    void ignoresRangePredicates()
    {
        ColumnHandle search = new MeilisearchColumnHandle("_search", BIGINT, SEARCH);

        assertThat(SearchPredicate.from(new Constraint(TupleDomain.withColumnDomains(Map.of(
                search,
                Domain.create(io.trino.spi.predicate.ValueSet.ofRanges(io.trino.spi.predicate.Range.greaterThan(BIGINT, 1L)), false))))))
                .isEmpty();
    }
}
