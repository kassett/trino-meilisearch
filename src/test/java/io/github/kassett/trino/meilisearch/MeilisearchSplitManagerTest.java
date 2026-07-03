package io.github.kassett.trino.meilisearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorSplitSource;
import io.trino.spi.connector.DynamicFilterSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class MeilisearchSplitManagerTest
{
    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void startServer()
            throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer()
    {
        server.stop(0);
    }

    @Test
    void plansDocumentSplitsByPageSize()
    {
        server.createContext("/indexes/movies/documents", exchange -> json(exchange, """
                {"results":[],"total":23}
                """));
        MeilisearchSplitManager splitManager = new MeilisearchSplitManager(config(10), new MeilisearchClient(config(10)));

        List<MeilisearchSplit> splits = splits(splitManager.getSplits(
                MeilisearchTransactionHandle.INSTANCE,
                null,
                handle(Optional.empty(), Optional.empty()),
                java.util.Set.of(),
                io.trino.spi.connector.Constraint.alwaysTrue()));

        assertThat(splits)
                .containsExactly(
                        new MeilisearchSplit("movies", 0, 10, false, ""),
                        new MeilisearchSplit("movies", 10, 10, false, ""),
                        new MeilisearchSplit("movies", 20, 3, false, ""));
    }

    @Test
    void capsDocumentSplitsAtLimit()
    {
        server.createContext("/indexes/movies/documents", exchange -> json(exchange, """
                {"results":[],"total":23}
                """));
        MeilisearchSplitManager splitManager = new MeilisearchSplitManager(config(10), new MeilisearchClient(config(10)));

        List<MeilisearchSplit> splits = splits(splitManager.getSplits(
                MeilisearchTransactionHandle.INSTANCE,
                null,
                handle(Optional.empty(), Optional.of(12L)),
                java.util.Set.of(),
                io.trino.spi.connector.Constraint.alwaysTrue()));

        assertThat(splits)
                .containsExactly(
                        new MeilisearchSplit("movies", 0, 10, false, ""),
                        new MeilisearchSplit("movies", 10, 2, false, ""));
    }

    @Test
    void plansSearchAsSingleSplit()
    {
        server.createContext("/indexes/movies/search", exchange -> json(exchange, """
                {"hits":[],"estimatedTotalHits":18}
                """));
        MeilisearchSplitManager splitManager = new MeilisearchSplitManager(config(10), new MeilisearchClient(config(10)));

        List<MeilisearchSplit> splits = splits(splitManager.getSplits(
                MeilisearchTransactionHandle.INSTANCE,
                null,
                handle(Optional.of("matrix"), Optional.of(5L)),
                java.util.Set.of(),
                io.trino.spi.connector.Constraint.alwaysTrue()));

        assertThat(splits)
                .containsExactly(new MeilisearchSplit("movies", 0, 5, true, "matrix"));
    }

    private MeilisearchConfig config(int pageSize)
    {
        return new MeilisearchConfig(baseUri, Optional.empty(), "default", 100, pageSize, Optional.empty());
    }

    private static MeilisearchTableHandle handle(Optional<String> search, Optional<Long> limit)
    {
        return new MeilisearchTableHandle("default", "movies", Optional.empty(), search, limit, Optional.empty());
    }

    private static List<MeilisearchSplit> splits(ConnectorSplitSource source)
    {
        List<ConnectorSplit> splits = source.getNextBatch(100, DynamicFilterSnapshot.EMPTY).join();
        assertThat(source.isFinished()).isTrue();
        return splits.stream()
                .map(MeilisearchSplit.class::cast)
                .toList();
    }

    private static void json(HttpExchange exchange, String response)
    {
        try (exchange) {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
