package io.github.kassett.trino.meilisearch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.trino.spi.HostAddress;
import io.trino.spi.connector.ConnectorSplit;

import java.util.List;

public record MeilisearchSplit(
        @JsonProperty String indexName,
        @JsonProperty int offset,
        @JsonProperty int limit,
        @JsonProperty boolean search,
        @JsonProperty String query)
        implements ConnectorSplit
{
    @JsonCreator
    public MeilisearchSplit {}

    @Override
    public boolean isRemotelyAccessible()
    {
        return true;
    }

    @Override
    public List<HostAddress> getAddresses()
    {
        return List.of();
    }

    public Object getInfo()
    {
        return this;
    }
}
