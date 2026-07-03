package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.NONE;
import static io.airlift.slice.Slices.utf8Slice;
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
                new Constraint(TupleDomain.withColumnDomains(Map.of(title, Domain.singleValue(VARCHAR, utf8Slice("Arrival"))))),
                List.of("release_year")))
                .isEmpty();
    }

    @Test
    void escapesStringLiterals()
    {
        ColumnHandle title = new MeilisearchColumnHandle("title", VARCHAR, NONE);

        assertThat(MeilisearchFilter.from(
                new Constraint(TupleDomain.withColumnDomains(Map.of(title, Domain.singleValue(VARCHAR, utf8Slice("Bob's \\ Movie"))))),
                List.of("title")))
                .contains("title = 'Bob\\'s \\\\ Movie'");
    }

    @Test
    void parenthesizesDisjunctions()
    {
        ColumnHandle releaseYear = new MeilisearchColumnHandle("release_year", BIGINT, NONE);

        assertThat(MeilisearchFilter.from(
                new Constraint(TupleDomain.withColumnDomains(Map.of(releaseYear, Domain.create(
                        ValueSet.ofRanges(Range.equal(BIGINT, 1999L), Range.equal(BIGINT, 2000L)),
                        false)))),
                List.of("release_year")))
                .contains("(release_year = 1999 OR release_year = 2000)");
    }

    @Test
    void skipsNullAllowedDomains()
    {
        ColumnHandle title = new MeilisearchColumnHandle("title", VARCHAR, NONE);

        assertThat(MeilisearchFilter.from(
                new Constraint(TupleDomain.withColumnDomains(Map.of(title, Domain.create(ValueSet.all(VARCHAR), true)))),
                List.of("title")))
                .isEmpty();
    }
}
