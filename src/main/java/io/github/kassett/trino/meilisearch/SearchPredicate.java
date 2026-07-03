package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.airlift.slice.Slice;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public final class SearchPredicate
{
    private SearchPredicate() {}

    static Optional<String> from(Constraint constraint)
    {
        TupleDomain<ColumnHandle> summary = constraint.getSummary();
        if (summary.getDomains().isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<ColumnHandle, Domain> entry : summary.getDomains().get().entrySet()) {
            if (!(entry.getKey() instanceof MeilisearchColumnHandle column) || column.specialColumn() != MeilisearchColumn.SpecialColumn.SEARCH) {
                continue;
            }
            ValueSet values = entry.getValue().getValues();
            for (Range range : values.getRanges().getOrderedRanges()) {
                if (range.isSingleValue()) {
                    Object value = range.getSingleValue();
                    if (value instanceof Slice slice) {
                        return Optional.of(slice.toStringUtf8());
                    }
                    return Optional.of(new String((byte[]) value, StandardCharsets.UTF_8));
                }
            }
        }
        return Optional.empty();
    }
}
