package io.github.kassett.trino.meilisearch;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.trino.spi.connector.ConnectorTransactionHandle;

public enum MeilisearchTransactionHandle
        implements ConnectorTransactionHandle
{
    INSTANCE;

    @JsonCreator
    public static MeilisearchTransactionHandle instance()
    {
        return INSTANCE;
    }
}
