package io.github.kassett.trino.meilisearch;

import io.trino.spi.connector.Connector;
import io.trino.spi.connector.ConnectorContext;
import io.trino.spi.connector.ConnectorFactory;

import java.util.Map;

public final class MeilisearchConnectorFactory
        implements ConnectorFactory
{
    @Override
    public String getName()
    {
        return "meilisearch";
    }

    @Override
    public Connector create(String catalogName, Map<String, String> config, ConnectorContext context)
    {
        MeilisearchConfig meiliConfig = MeilisearchConfig.from(config);
        MeilisearchClient client = new MeilisearchClient(meiliConfig);
        return new MeilisearchConnector(meiliConfig, client, context.getTypeManager().fromSqlType("json"));
    }
}
