package io.github.kassett.trino.meilisearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import io.trino.spi.connector.SortItem;
import io.trino.spi.connector.SortOrder;
import io.trino.spi.predicate.Domain;
import io.trino.spi.predicate.TupleDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static io.airlift.slice.Slices.utf8Slice;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.NONE;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.SEARCH;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.VarbinaryType.VARBINARY;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;

final class MeilisearchMetadataTest
{
    private HttpServer server;
    private MeilisearchMetadata metadata;

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
        MeilisearchClient client = new MeilisearchClient(config);
        metadata = new MeilisearchMetadata(config, client, new SchemaResolver(config, client, VARBINARY));
    }

    @AfterEach
    void stopServer()
    {
        server.stop(0);
    }

    @Test
    void listsSchemasTablesAndColumns()
    {
        indexes();
        documents();

        assertThat(metadata.listSchemaNames(null)).containsExactly("default");
        assertThat(metadata.listTables(null, Optional.empty())).containsExactly(new SchemaTableName("default", "movies"));
        assertThat(metadata.listTables(null, Optional.of("missing"))).isEmpty();

        ConnectorTableHandle table = metadata.getTableHandle(null, new SchemaTableName("default", "movies"), Optional.empty(), Optional.empty());
        assertThat(table).isInstanceOf(MeilisearchTableHandle.class);
        assertThat(metadata.getTableHandle(null, new SchemaTableName("other", "movies"), Optional.empty(), Optional.empty())).isNull();

        Map<String, ColumnHandle> handles = metadata.getColumnHandles(null, table);
        assertThat(handles.keySet()).contains("_id", "_json", "_search", "title", "release_year");
        assertThat(metadata.getColumnMetadata(null, table, handles.get("_search")).isHidden()).isTrue();
        assertThat(metadata.getTableMetadata(null, table).getColumns())
                .extracting(ColumnMetadata::getName)
                .contains("_id", "_json", "_search", "title", "release_year");
        assertThat(metadata.listTableColumns(null, new SchemaTablePrefix("default", "movies")))
                .containsOnlyKeys(new SchemaTableName("default", "movies"));
    }

    @Test
    void appliesLimitOnlyWhenItTightensTheHandle()
    {
        MeilisearchTableHandle table = table();

        assertThat(((MeilisearchTableHandle) metadata.applyLimit(null, table, 10).orElseThrow().getHandle()).limit()).contains(10L);
        assertThat(metadata.applyLimit(null, table.withLimit(5), 10)).isEmpty();
    }

    @Test
    void pushesFilterAndSearchPredicates()
    {
        settings();
        MeilisearchColumnHandle releaseYear = new MeilisearchColumnHandle("release_year", BIGINT, NONE);
        MeilisearchColumnHandle search = new MeilisearchColumnHandle("_search", VARCHAR, SEARCH);

        MeilisearchTableHandle handle = (MeilisearchTableHandle) metadata.applyFilter(
                null,
                table(),
                new Constraint(TupleDomain.withColumnDomains(Map.of(
                        releaseYear, Domain.singleValue(BIGINT, 1999L),
                        search, Domain.singleValue(VARCHAR, utf8Slice("matrix"))))))
                .orElseThrow()
                .getHandle();

        assertThat(handle.filter()).contains("release_year = 1999");
        assertThat(handle.search()).contains("matrix");
    }

    @Test
    void appliesTopNOnlyForSortableSingleColumn()
    {
        settings();
        MeilisearchColumnHandle rating = new MeilisearchColumnHandle("rating", BIGINT, NONE);
        MeilisearchColumnHandle title = new MeilisearchColumnHandle("title", VARCHAR, NONE);

        MeilisearchTableHandle sorted = (MeilisearchTableHandle) metadata.applyTopN(
                null,
                table(),
                3,
                java.util.List.of(new SortItem("rating", SortOrder.DESC_NULLS_LAST)),
                Map.of("rating", rating))
                .orElseThrow()
                .getHandle();
        assertThat(sorted.limit()).contains(3L);
        assertThat(sorted.sort().orElseThrow().meilisearch()).isEqualTo("rating:desc");

        assertThat(metadata.applyTopN(
                null,
                table(),
                3,
                java.util.List.of(new SortItem("title", SortOrder.ASC_NULLS_LAST)),
                Map.of("title", title)))
                .isEmpty();
    }

    private static MeilisearchTableHandle table()
    {
        return new MeilisearchTableHandle("default", "movies", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private void indexes()
    {
        server.createContext("/indexes", exchange -> json(exchange, """
                {"results":[{"uid":"movies","primaryKey":"id"}],"total":1}
                """));
    }

    private void documents()
    {
        server.createContext("/indexes/movies/documents", exchange -> json(exchange, """
                {"results":[{"id":1,"title":"The Matrix","release_year":1999}],"total":1}
                """));
    }

    private void settings()
    {
        server.createContext("/indexes/movies/settings", exchange -> json(exchange, """
                {"filterableAttributes":["release_year"],"sortableAttributes":["rating"]}
                """));
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
