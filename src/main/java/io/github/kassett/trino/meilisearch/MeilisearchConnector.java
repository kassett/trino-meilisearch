package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorPageSourceProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplitManager;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.transaction.IsolationLevel;
import io.trino.spi.type.Type;

public final class MeilisearchConnector
        implements Connector
{
    private final MeilisearchMetadata metadata;
    private final MeilisearchSplitManager splitManager;
    private final MeilisearchPageSourceProvider pageSourceProvider;

    public MeilisearchConnector(MeilisearchConfig config, MeilisearchClient client, Type jsonType)
    {
        SchemaResolver schemaResolver = new SchemaResolver(config, client, jsonType);
        this.metadata = new MeilisearchMetadata(config, client, schemaResolver);
        this.splitManager = new MeilisearchSplitManager(config, client);
        this.pageSourceProvider = new MeilisearchPageSourceProvider(client);
    }

    @Override
    public ConnectorTransactionHandle beginTransaction(IsolationLevel isolationLevel, boolean readOnly, boolean autoCommit)
    {
        return MeilisearchTransactionHandle.INSTANCE;
    }

    @Override
    public ConnectorMetadata getMetadata(ConnectorSession session, ConnectorTransactionHandle transactionHandle)
    {
        return metadata;
    }

    @Override
    public ConnectorSplitManager getSplitManager()
    {
        return splitManager;
    }

    @Override
    public ConnectorPageSourceProvider getPageSourceProvider()
    {
        return pageSourceProvider;
    }

    @Override
    public void shutdown() {}
}
