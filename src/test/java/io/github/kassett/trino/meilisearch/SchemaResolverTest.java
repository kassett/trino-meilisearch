package io.github.kassett.trino.meilisearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.type.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.ID;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.JSON;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.NONE;
import static io.github.kassett.trino.meilisearch.MeilisearchColumn.SpecialColumn.SEARCH;
import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.BooleanType.BOOLEAN;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.VarbinaryType.VARBINARY;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class SchemaResolverTest
{
    private static final Type JSON_TYPE = VARBINARY;

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
    void infersColumnsFromSampleDocuments()
    {
        documents("""
                {
                  "results": [
                    {"title":"Arrival","release_year":2016,"rating":7.9,"available":true,"genres":["sci-fi"],"mixed":1},
                    {"mixed":"later"}
                  ],
                  "total": 2
                }
                """);

        List<MeilisearchColumn> columns = resolver(config(Optional.empty())).columns("movies");

        assertThat(columns)
                .contains(
                        new MeilisearchColumn("_id", VARCHAR, ID),
                        new MeilisearchColumn("_json", JSON_TYPE, JSON),
                        new MeilisearchColumn("_search", VARCHAR, SEARCH),
                        new MeilisearchColumn("title", VARCHAR, NONE),
                        new MeilisearchColumn("release_year", BIGINT, NONE),
                        new MeilisearchColumn("rating", DOUBLE, NONE),
                        new MeilisearchColumn("available", BOOLEAN, NONE),
                        new MeilisearchColumn("genres", JSON_TYPE, NONE),
                        new MeilisearchColumn("mixed", JSON_TYPE, NONE));
    }

    @Test
    void appliesSchemaOverrides(@TempDir Path tempDir)
            throws IOException
    {
        documents("""
                {"results":[{"release_year":2016}],"total":1}
                """);
        Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {"movies":{"release_year":"varchar","score":"double","visible":"boolean"}}
                """);

        List<MeilisearchColumn> columns = resolver(config(Optional.of(schemaFile))).columns("movies");

        assertThat(columns)
                .contains(
                        new MeilisearchColumn("release_year", VARCHAR, NONE),
                        new MeilisearchColumn("score", DOUBLE, NONE),
                        new MeilisearchColumn("visible", BOOLEAN, NONE));
    }

    @Test
    void exposesColumnMetadata()
    {
        documents("""
                {"results":[{"title":"Arrival"}],"total":1}
                """);

        assertThat(resolver(config(Optional.empty())).columnMetadata("movies"))
                .extracting(ColumnMetadata::getName)
                .containsExactly("_id", "_json", "_search", "title");
    }

    @Test
    void rejectsUnsupportedOverrideTypes(@TempDir Path tempDir)
            throws IOException
    {
        documents("""
                {"results":[],"total":0}
                """);
        Path schemaFile = tempDir.resolve("schema.json");
        Files.writeString(schemaFile, """
                {"movies":{"bad":"timestamp"}}
                """);

        assertThatThrownBy(() -> resolver(config(Optional.of(schemaFile))).columns("movies"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamp");
    }

    private SchemaResolver resolver(MeilisearchConfig config)
    {
        return new SchemaResolver(config, new MeilisearchClient(config), JSON_TYPE);
    }

    private MeilisearchConfig config(Optional<Path> schemaFile)
    {
        return new MeilisearchConfig(baseUri, Optional.empty(), "default", 50, 100, schemaFile);
    }

    private void documents(String response)
    {
        server.createContext("/indexes/movies/documents", exchange -> json(exchange, response));
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
