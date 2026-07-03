package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.FixedSplitSource;

import java.util.ArrayList;
import java.util.List;

public final class MeilisearchSplitManager
        implements ConnectorSplitManager
{
    private final MeilisearchConfig config;
    private final MeilisearchClient client;

    public MeilisearchSplitManager(MeilisearchConfig config, MeilisearchClient client)
    {
        this.config = config;
        this.client = client;
    }

    @Override
    public ConnectorSplitSource getSplits(ConnectorTransactionHandle transaction, ConnectorSession session, ConnectorTableHandle table, java.util.Set<ColumnHandle> columns, Constraint constraint)
    {
        MeilisearchTableHandle handle = (MeilisearchTableHandle) table;
        int pageSize = config.pageSize();
        int total = handle.search()
                .map(query -> client.search(handle.indexName(), query, List.of(), 0, 1).total())
                .orElseGet(() -> client.documents(handle.indexName(), List.of(), handle.filter(), handle.sort().map(MeilisearchTableHandle.Sort::meilisearch), 0, 1).total());
        int planned = (int) Math.min(handle.limit().orElse((long) total), total);
        if (handle.search().isPresent()) {
            return new FixedSplitSource(List.of(new MeilisearchSplit(
                    handle.indexName(),
                    0,
                    planned,
                    true,
                    handle.search().orElseThrow())));
        }

        List<MeilisearchSplit> splits = new ArrayList<>();
        for (int offset = 0; offset < planned; offset += pageSize) {
            splits.add(new MeilisearchSplit(
                    handle.indexName(),
                    offset,
                    Math.min(pageSize, planned - offset),
                    handle.search().isPresent(),
                    handle.search().orElse("")));
        }
        return new FixedSplitSource(splits);
    }
}
