package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.NONE;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

final class MeilisearchFilterTest
{
    @Test
    void translatesFilterableRange()
    {
        ColumnHandle releaseYear = new MeilisearchColumnHandle("release_year", BIGINT, NONE);

        assertThat(MeilisearchFilter.from(
                new Constraint(TupleDomain.withColumnDomains(Map.of(releaseYear, Domain.create(io.trino.spi.predicate.ValueSet.ofRanges(io.trino.spi.predicate.Range.greaterThanOrEqual(BIGINT, 2000L)), false)))),
                List.of("release_year")))
                .contains("release_year >= 2000");
    }

    @Test
    void ignoresNonFilterableColumn()
    {
        ColumnHandle title = new MeilisearchColumnHandle("title", VARCHAR, NONE);

        assertThat(MeilisearchFilter.from(
                new Constraint(TupleDomain.withColumnDomains(Map.of(title, Domain.singleValue(VARCHAR, io.airlift.slice.Slices.utf8Slice("Arrival"))))),
                List.of("release_year")))
                .isEmpty();
    }
}
