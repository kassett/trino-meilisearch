package io.github.kassett.trino.meilisearch;

import io.trino.spi.Plugin;
import io.trino.spi.connector.ConnectorFactory;

import java.util.Set;

public final class MeilisearchPlugin
        implements Plugin
{
    @Override
    public Iterable<ConnectorFactory> getConnectorFactories()
    {
        return Set.of(new MeilisearchConnectorFactory());
    }
}
