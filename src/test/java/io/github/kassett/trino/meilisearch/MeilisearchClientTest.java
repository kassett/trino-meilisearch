package io.github.kassett.trino.meilisearch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class MeilisearchClientTest
{
    private HttpServer server;
    private MeilisearchClient client;

    @BeforeEach
    void startServer()
            throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        client = new MeilisearchClient(new MeilisearchConfig(
                java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Optional.of("masterKey"),
                "default",
                100,
                100,
                Optional.empty()));
    }

    @AfterEach
    void stopServer()
    {
        server.stop(0);
    }

    @Test
    void listsIndexesAcrossPages()
    {
        server.createContext("/indexes", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer masterKey");
            if (exchange.getRequestURI().getQuery().contains("offset=0")) {
                json(exchange, 200, """
                        {"results":[{"uid":"movies","primaryKey":"id"}],"total":2}
                        """);
                return;
            }
            json(exchange, 200, """
                    {"results":[{"uid":"books","primaryKey":null}],"total":2}
                    """);
        });

        assertThat(client.listIndexes())
                .containsExactly(
                        new MeilisearchClient.MeilisearchIndex("movies", Optional.of("id")),
                        new MeilisearchClient.MeilisearchIndex("books", Optional.empty()));
    }

    @Test
    void requestsDocumentsWithEncodedParameters()
    {
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/indexes/movies/2024/documents", exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            query.set(exchange.getRequestURI().getRawQuery());
            json(exchange, 200, """
                    {"results":[{"id":1,"title":"Arrival"}],"total":42}
                    """);
        });

        MeilisearchClient.DocumentPage page = client.documents(
                "movies/2024",
                List.of("title", "release_year"),
                Optional.of("title = 'Arrival'"),
                Optional.of("release_year:desc"),
                10,
                5);

        assertThat(path.get()).isEqualTo("/indexes/movies%2F2024/documents");
        assertThat(query.get()).isEqualTo("offset=10&limit=5&fields=title%2Crelease_year&filter=title+%3D+%27Arrival%27&sort=release_year%3Adesc");
        assertThat(page.total()).isEqualTo(42);
        assertThat(page.documents()).containsExactly(Map.of("id", 1, "title", "Arrival"));
    }

    @Test
    void postsSearchBody()
            throws IOException
    {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/indexes/movies/search", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            json(exchange, 200, """
                    {"hits":[{"id":2,"title":"The Matrix"}],"estimatedTotalHits":7}
                    """);
        });

        MeilisearchClient.DocumentPage page = client.search("movies", "matrix", List.of("id", "title"), 3, 4);

        assertThat(body.get()).isEqualTo("{\"q\":\"matrix\",\"offset\":3,\"limit\":4,\"attributesToRetrieve\":[\"id\",\"title\"]}");
        assertThat(page.total()).isEqualTo(7);
        assertThat(page.documents()).containsExactly(Map.of("id", 2, "title", "The Matrix"));
    }

    @Test
    void readsSettings()
    {
        server.createContext("/indexes/movies/settings", exchange -> json(exchange, 200, """
                {"filterableAttributes":["release_year"],"sortableAttributes":["rating"]}
                """));

        MeilisearchClient.MeilisearchSettings settings = client.getSettings("movies");

        assertThat(settings.filterableAttributes()).containsExactly("release_year");
        assertThat(settings.sortableAttributes()).containsExactly("rating");
    }

    @Test
    void failsOnErrorResponse()
    {
        server.createContext("/indexes", exchange -> json(exchange, 500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(client::listIndexes)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("boom");
    }

    private static void json(HttpExchange exchange, int status, String response)
    {
        try (exchange) {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
