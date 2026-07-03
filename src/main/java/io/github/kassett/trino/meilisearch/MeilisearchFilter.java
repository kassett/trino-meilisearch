package io.github.kassett.trino.meilisearch;

import io.airlift.slice.Slice;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.Range;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.predicate.ValueSet;
import io.trino.spi.type.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.VarcharType.VARCHAR;

public final class MeilisearchFilter
{
    private MeilisearchFilter() {}

    public static Optional<String> from(Constraint constraint, List<String> filterableAttributes)
    {
        TupleDomain<ColumnHandle> summary = constraint.getSummary();
        if (summary.isAll() || summary.isNone()) {
            return Optional.empty();
        }
        Optional<Map<ColumnHandle, Domain>> domains = summary.getDomains();
        if (domains.isEmpty()) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<ColumnHandle, Domain> entry : domains.get().entrySet()) {
            if (!(entry.getKey() instanceof MeilisearchColumnHandle column) || !filterableAttributes.contains(column.name())) {
                continue;
            }
            Optional<String> filter = domain(column, entry.getValue());
            if (filter.isEmpty()) {
                continue;
            }
            parts.add(filter.get());
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" AND ", parts));
    }

    private static Optional<String> domain(MeilisearchColumnHandle column, Domain domain)
    {
        if (domain.isNullAllowed() || domain.isAll() || domain.isNone()) {
            return Optional.empty();
        }
        ValueSet values = domain.getValues();
        StringJoiner disjunction = new StringJoiner(" OR ");
        for (Range range : values.getRanges().getOrderedRanges()) {
            Optional<String> expression = range(column.name(), column.type(), range);
            expression.ifPresent(disjunction::add);
        }
        String result = disjunction.toString();
        if (result.isBlank()) {
            return Optional.empty();
        }
        if (result.contains(" OR ")) {
            return Optional.of("(" + result + ")");
        }
        return Optional.of(result);
    }

    private static Optional<String> range(String column, Type type, Range range)
    {
        if (range.isSingleValue()) {
            return Optional.of(column + " = " + literal(type, range.getSingleValue()));
        }
        List<String> parts = new ArrayList<>();
        if (!range.isLowUnbounded()) {
            parts.add(column + (range.isLowInclusive() ? " >= " : " > ") + literal(type, range.getLowBoundedValue()));
        }
        if (!range.isHighUnbounded()) {
            parts.add(column + (range.isHighInclusive() ? " <= " : " < ") + literal(type, range.getHighBoundedValue()));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(" AND ", parts));
    }

    private static String literal(Type type, Object value)
    {
        if (type.equals(VARCHAR)) {
            return "'" + text(value).replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (type.equals(BIGINT) || type.equals(DOUBLE) || type.equals(BOOLEAN)) {
            return value.toString();
        }
        return "'" + text(value).replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String text(Object value)
    {
        if (value instanceof Slice slice) {
            return slice.toStringUtf8();
        }
        return value.toString();
    }
}
