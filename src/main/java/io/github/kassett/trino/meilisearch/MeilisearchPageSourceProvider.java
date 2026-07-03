package io.github.kassett.trino.meilisearch;

import io.airlift.slice.Slices;
import io.trino.spi.Page;
import io.trino.spi.PageBuilder;
import io.trino.spi.block.BlockBuilder;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorPageSource;
import io.trino.spi.connector.ConnectorPageSourceProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableCredentials;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.DynamicFilter;
import io.trino.spi.connector.FixedPageSource;
import io.trino.spi.type.Type;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.ID;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.JSON;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.SEARCH;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.VarcharType.VARCHAR;

public final class MeilisearchPageSourceProvider
        implements ConnectorPageSourceProvider
{
    private final MeilisearchClient client;

    public MeilisearchPageSourceProvider(MeilisearchClient client)
    {
        this.client = client;
    }

    @Override
    public ConnectorPageSource createPageSource(ConnectorTransactionHandle transaction, ConnectorSession session, ConnectorSplit split, ConnectorTableHandle table, Optional<ConnectorTableCredentials> tableCredentials, List<ColumnHandle> columns, DynamicFilter dynamicFilter)
    {
        MeilisearchSplit meiliSplit = (MeilisearchSplit) split;
        MeilisearchTableHandle handle = (MeilisearchTableHandle) table;
        List<MeilisearchColumnHandle> meiliColumns = columns.stream()
                .map(MeilisearchColumnHandle.class::cast)
                .toList();
        List<String> fields = meiliColumns.stream()
                .filter(column -> column.specialColumn() != ID && column.specialColumn() != JSON && column.specialColumn() != SEARCH)
                .map(MeilisearchColumnHandle::name)
                .toList();
        MeilisearchClient.DocumentPage page = meiliSplit.search()
                ? client.search(meiliSplit.indexName(), meiliSplit.query(), fields, meiliSplit.offset(), meiliSplit.limit())
                : client.documents(meiliSplit.indexName(), fields, handle.filter(), handle.sort().map(MeilisearchTableHandle.Sort::meilisearch), meiliSplit.offset(), meiliSplit.limit());
        return new FixedPageSource(List.of(toPage(handle.indexName(), meiliColumns, page.documents(), handle.search())));
    }

    private Page toPage(String indexName, List<MeilisearchColumnHandle> columns, List<Map<String, Object>> documents, Optional<String> search)
    {
        List<Type> types = columns.stream().map(MeilisearchColumnHandle::type).toList();
        PageBuilder pageBuilder = new PageBuilder(types);
        Optional<String> primaryKey = client.getIndex(indexName).flatMap(MeilisearchClient.MeilisearchIndex::primaryKey);
        for (Map<String, Object> document : documents) {
            pageBuilder.declarePosition();
            for (int channel = 0; channel < columns.size(); channel++) {
                MeilisearchColumnHandle column = columns.get(channel);
                Object value = value(document, column, primaryKey, search);
                write(pageBuilder.getBlockBuilder(channel), column.type(), value);
            }
        }
        return pageBuilder.build();
    }

    private Object value(Map<String, Object> document, MeilisearchColumnHandle column, Optional<String> primaryKey, Optional<String> search)
    {
        if (column.specialColumn() == JSON) {
            return document;
        }
        if (column.specialColumn() == SEARCH) {
            return search.orElse(null);
        }
        if (column.specialColumn() == ID) {
            return primaryKey.map(document::get).orElse(null);
        }
        return document.get(column.name());
    }

    private void write(BlockBuilder blockBuilder, Type type, Object value)
    {
        if (value == null) {
            blockBuilder.appendNull();
            return;
        }
        if (type.equals(BOOLEAN)) {
            BOOLEAN.writeBoolean(blockBuilder, (Boolean) value);
            return;
        }
        if (type.equals(BIGINT)) {
            BIGINT.writeLong(blockBuilder, ((Number) value).longValue());
            return;
        }
        if (type.equals(DOUBLE)) {
            DOUBLE.writeDouble(blockBuilder, ((Number) value).doubleValue());
            return;
        }
        if (type.equals(VARCHAR)) {
            VARCHAR.writeSlice(blockBuilder, Slices.utf8Slice(value.toString()));
            return;
        }
        if (type.getDisplayName().equals("json")) {
            type.writeSlice(blockBuilder, Slices.utf8Slice(client.toJson(value)));
            return;
        }
        blockBuilder.appendNull();
    }
}
