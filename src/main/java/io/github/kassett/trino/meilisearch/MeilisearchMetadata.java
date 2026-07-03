package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.connector.LimitApplicationResult;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import io.trino.spi.connector.SortItem;
import io.trino.spi.connector.TopNApplicationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MeilisearchMetadata
        implements ConnectorMetadata
{
    private final MeilisearchConfig config;
    private final MeilisearchClient client;
    private final SchemaResolver schemaResolver;

    public MeilisearchMetadata(MeilisearchConfig config, MeilisearchClient client, SchemaResolver schemaResolver)
    {
        this.config = config;
        this.client = client;
        this.schemaResolver = schemaResolver;
    }

    @Override
    public List<String> listSchemaNames(ConnectorSession session)
    {
        return List.of(config.schemaName());
    }

    @Override
    public ConnectorTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName, Optional<io.trino.spi.connector.ConnectorTableVersion> startVersion, Optional<io.trino.spi.connector.ConnectorTableVersion> endVersion)
    {
        if (!tableName.getSchemaName().equals(config.schemaName()) || client.getIndex(tableName.getTableName()).isEmpty()) {
            return null;
        }
        return new MeilisearchTableHandle(tableName.getSchemaName(), tableName.getTableName(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(ConnectorSession session, ConnectorTableHandle table)
    {
        MeilisearchTableHandle handle = (MeilisearchTableHandle) table;
        return new ConnectorTableMetadata(
                new SchemaTableName(handle.schemaName(), handle.indexName()),
                schemaResolver.columnMetadata(handle.indexName()));
    }

    @Override
    public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> schemaName)
    {
        if (schemaName.isPresent() && !schemaName.get().equals(config.schemaName())) {
            return List.of();
        }
        return client.listIndexes().stream()
                .map(index -> new SchemaTableName(config.schemaName(), index.uid()))
                .toList();
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(ConnectorSession session, ConnectorTableHandle tableHandle)
    {
        MeilisearchTableHandle handle = (MeilisearchTableHandle) tableHandle;
        Map<String, ColumnHandle> columns = new LinkedHashMap<>();
        for (MeilisearchColumn column : schemaResolver.columns(handle.indexName())) {
            columns.put(column.name(), new MeilisearchColumnHandle(column.name(), column.type(), column.specialColumn()));
        }
        return columns;
    }

    @Override
    public ColumnMetadata getColumnMetadata(ConnectorSession session, ConnectorTableHandle tableHandle, ColumnHandle columnHandle)
    {
        MeilisearchColumnHandle column = (MeilisearchColumnHandle) columnHandle;
        return ColumnMetadata.builder()
                .setName(column.name())
                .setType(column.type())
                .setHidden(column.specialColumn() == MeilisearchColumn.SpecialColumn.SEARCH)
                .build();
    }

    @Override
    public Map<SchemaTableName, List<ColumnMetadata>> listTableColumns(ConnectorSession session, SchemaTablePrefix prefix)
    {
        return listTables(session, prefix.getSchema())
                .stream()
                .filter(table -> prefix.getTable().isEmpty() || prefix.getTable().get().equals(table.getTableName()))
                .collect(LinkedHashMap::new, (map, table) -> map.put(table, schemaResolver.columnMetadata(table.getTableName())), LinkedHashMap::putAll);
    }

    @Override
    public Optional<LimitApplicationResult<ConnectorTableHandle>> applyLimit(ConnectorSession session, ConnectorTableHandle table, long limit)
    {
        MeilisearchTableHandle handle = (MeilisearchTableHandle) table;
        MeilisearchTableHandle newHandle = handle.withLimit(limit);
        if (newHandle.equals(handle)) {
            return Optional.empty();
        }
        return Optional.of(new LimitApplicationResult<>(newHandle, true, false));
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(ConnectorSession session, ConnectorTableHandle table, Constraint constraint)
    {
        MeilisearchTableHandle handle = (MeilisearchTableHandle) table;
        MeilisearchClient.MeilisearchSettings settings = client.getSettings(handle.indexName());
        Optional<String> filter = MeilisearchFilter.from(constraint, settings.filterableAttributes());
        Optional<String> search = SearchPredicate.from(constraint);
        MeilisearchTableHandle newHandle = handle.withFilter(filter).withSearch(search);
        if (newHandle.equals(handle)) {
            return Optional.empty();
        }
        return Optional.of(new ConstraintApplicationResult<>(newHandle, constraint.getSummary(), constraint.getExpression(), false));
    }

    @Override
    public Optional<TopNApplicationResult<ConnectorTableHandle>> applyTopN(ConnectorSession session, ConnectorTableHandle table, long topNCount, List<SortItem> sortItems, Map<String, ColumnHandle> assignments)
    {
        MeilisearchTableHandle handle = (MeilisearchTableHandle) table;
        Set<String> sortable = Set.copyOf(client.getSettings(handle.indexName()).sortableAttributes());
        List<MeilisearchTableHandle.Sort> sorts = sortItems.stream()
                .map(item -> assignments.get(item.getName()))
                .filter(MeilisearchColumnHandle.class::isInstance)
                .map(MeilisearchColumnHandle.class::cast)
                .filter(column -> sortable.contains(column.name()))
                .map(column -> new MeilisearchTableHandle.Sort(column.name(), sortItems.getFirst().getSortOrder()))
                .toList();
        if (sorts.size() != 1 || sortItems.size() != 1) {
            return Optional.empty();
        }
        MeilisearchTableHandle newHandle = handle.withSort(sorts).withLimit(topNCount);
        return Optional.of(new TopNApplicationResult<>(newHandle, true, false));
    }
}
