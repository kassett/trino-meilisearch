package io.github.kassett.trino.meilisearch;

import io.trino.spi.Plugin;
import io.trino.spi.connector.ConnectorFactory;
import io.trino.spi.transaction.IsolationLevel;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static io.trino.spi.type.VarbinaryType.VARBINARY;
import static org.assertj.core.api.Assertions.assertThat;

final class MeilisearchConnectorTest
{
    @Test
    void exposesConnectorServices()
    {
        MeilisearchConfig config = new MeilisearchConfig(
                URI.create("http://localhost:7700"),
                Optional.empty(),
                "default",
                100,
                100,
                Optional.empty());
        MeilisearchConnector connector = new MeilisearchConnector(config, new MeilisearchClient(config), VARBINARY);

        assertThat(connector.beginTransaction(IsolationLevel.READ_COMMITTED, true, true)).isSameAs(MeilisearchTransactionHandle.INSTANCE);
        assertThat(connector.getMetadata(null, MeilisearchTransactionHandle.INSTANCE)).isInstanceOf(MeilisearchMetadata.class);
        assertThat(connector.getSplitManager()).isInstanceOf(MeilisearchSplitManager.class);
        assertThat(connector.getPageSourceProvider()).isInstanceOf(MeilisearchPageSourceProvider.class);
    }

    @Test
    void pluginRegistersFactory()
    {
        Plugin plugin = new MeilisearchPlugin();

        assertThat(plugin.getConnectorFactories())
                .singleElement()
                .extracting(ConnectorFactory::getName)
                .isEqualTo("meilisearch");
    }
}
