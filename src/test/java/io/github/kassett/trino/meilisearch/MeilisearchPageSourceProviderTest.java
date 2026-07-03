package io.github.kassett.trino.meilisearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.trino.spi.Page;
import io.trino.spi.connector.ConnectorPageSource;
import io.trino.spi.connector.SourcePage;
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

import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.ID;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.NONE;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.SEARCH;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

final class MeilisearchPageSourceProviderTest
{
    private HttpServer server;
    private MeilisearchPageSourceProvider provider;

    @BeforeEach
    void startServer()
            throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        MeilisearchConfig config = new MeilisearchConfig(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Optional.empty(),
                "default",
                100,
                100,
                Optional.empty());
        provider = new MeilisearchPageSourceProvider(new MeilisearchClient(config));
        server.createContext("/indexes", exchange -> json(exchange, """
                {"results":[{"uid":"movies","primaryKey":"id"}],"total":1}
                """));
    }

    @AfterEach
    void stopServer()
    {
        server.stop(0);
    }

    @Test
    void readsDocumentPagesIntoTypedBlocks()
    {
        server.createContext("/indexes/movies/documents", exchange -> json(exchange, """
                {"results":[{"id":"m1","title":"Arrival","release_year":2016,"rating":7.9,"available":true}],"total":1}
                """));

        Page page = pageSource(
                new MeilisearchSplit("movies", 0, 1, false, ""),
                table(Optional.empty()),
                List.of(
                        new MeilisearchColumnHandle("_id", VARCHAR, ID),
                        new MeilisearchColumnHandle("title", VARCHAR, NONE),
                        new MeilisearchColumnHandle("release_year", BIGINT, NONE),
                        new MeilisearchColumnHandle("rating", DOUBLE, NONE),
                        new MeilisearchColumnHandle("available", BOOLEAN, NONE)))
                .getPage();

        assertThat(page.getPositionCount()).isEqualTo(1);
        assertThat(VARCHAR.getObjectValue(page.getBlock(0), 0)).isEqualTo("m1");
        assertThat(VARCHAR.getObjectValue(page.getBlock(1), 0)).isEqualTo("Arrival");
        assertThat(BIGINT.getObjectValue(page.getBlock(2), 0)).isEqualTo(2016L);
        assertThat(DOUBLE.getObjectValue(page.getBlock(3), 0)).isEqualTo(7.9);
        assertThat(BOOLEAN.getObjectValue(page.getBlock(4), 0)).isEqualTo(true);
    }

    @Test
    void readsSearchPagesAndReturnsSearchColumn()
    {
        server.createContext("/indexes/movies/search", exchange -> json(exchange, """
                {"hits":[{"id":"m2","title":"The Matrix"}],"estimatedTotalHits":1}
                """));

        Page page = pageSource(
                new MeilisearchSplit("movies", 0, 1, true, "matrix"),
                table(Optional.of("matrix")),
                List.of(
                        new MeilisearchColumnHandle("title", VARCHAR, NONE),
                        new MeilisearchColumnHandle("_search", VARCHAR, SEARCH)))
                .getPage();

        assertThat(page.getPositionCount()).isEqualTo(1);
        assertThat(VARCHAR.getObjectValue(page.getBlock(0), 0)).isEqualTo("The Matrix");
        assertThat(VARCHAR.getObjectValue(page.getBlock(1), 0)).isEqualTo("matrix");
    }

    private SourcePage pageSource(MeilisearchSplit split, MeilisearchTableHandle table, List<MeilisearchColumnHandle> columns)
    {
        ConnectorPageSource pageSource = provider.createPageSource(
                MeilisearchTransactionHandle.INSTANCE,
                null,
                split,
                table,
                Optional.empty(),
                List.copyOf(columns),
                null);
        SourcePage page = pageSource.getNextSourcePage();
        assertThat(page).isNotNull();
        return page;
    }

    private static MeilisearchTableHandle table(Optional<String> search)
    {
        return new MeilisearchTableHandle("default", "movies", Optional.empty(), search, Optional.empty(), Optional.empty());
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
