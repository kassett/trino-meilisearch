package io.github.kassett.trino.meilisearch.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SearchParityBenchmark
{
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SearchParityBenchmark() {}

    public static void main(String[] args)
            throws Exception
    {
        Config config = Config.fromEnvironment();
        waitForTrino(config);

        System.out.printf("Search parity benchmark: queries=%s iterations=%d warmup=%d limit=%d%n",
                config.queries(), config.iterations(), config.warmupIterations(), config.limit());
        System.out.println("This benchmark asserts that Trino _search returns the same top-N IDs in the same order as Meilisearch /search.");
        System.out.println();

        for (String query : config.queries()) {
            Result nativeResult = runNative(config, query);
            Result trinoResult = runTrino(config, query);
            assertSameHits(query, nativeResult.hits(), trinoResult.hits());

            Samples nativeSamples = measure(config.warmupIterations(), config.iterations(), () -> runNative(config, query));
            Samples trinoSamples = measure(config.warmupIterations(), config.iterations(), () -> runTrino(config, query));

            System.out.printf("query=%s hits=%d parity=PASS%n", quote(query), nativeResult.hits().size());
            System.out.printf("  native meilisearch /search  avg=%7.2f ms p50=%7.2f ms p95=%7.2f ms min=%7.2f ms max=%7.2f ms%n",
                    nativeSamples.averageMillis(), nativeSamples.percentileMillis(50), nativeSamples.percentileMillis(95), nativeSamples.minMillis(), nativeSamples.maxMillis());
            System.out.printf("  trino WHERE _search        avg=%7.2f ms p50=%7.2f ms p95=%7.2f ms min=%7.2f ms max=%7.2f ms%n",
                    trinoSamples.averageMillis(), trinoSamples.percentileMillis(50), trinoSamples.percentileMillis(95), trinoSamples.minMillis(), trinoSamples.maxMillis());
            System.out.println();
        }
    }

    private static Samples measure(int warmupIterations, int iterations, ThrowingSupplier<Result> supplier)
            throws Exception
    {
        for (int i = 0; i < warmupIterations; i++) {
            supplier.get();
        }

        List<Long> nanos = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            supplier.get();
            nanos.add(System.nanoTime() - start);
        }
        return new Samples(nanos);
    }

    private static Result runNative(Config config, String query)
            throws IOException, InterruptedException
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("limit", config.limit());
        body.put("attributesToRetrieve", List.of("id", "title"));

        HttpRequest request = HttpRequest.newBuilder(config.meilisearchUrl().resolve("/indexes/movies/search"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + config.meilisearchApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Meilisearch search failed: " + response.statusCode() + " " + response.body());
        }

        List<Hit> hits = new ArrayList<>();
        for (JsonNode hit : MAPPER.readTree(response.body()).path("hits")) {
            hits.add(new Hit(hit.path("id").asLong(), hit.path("title").asText()));
        }
        return new Result(hits);
    }

    private static Result runTrino(Config config, String query)
            throws SQLException
    {
        String sql = """
                SELECT id, title
                FROM meilisearch.default.movies
                WHERE _search = '%s'
                LIMIT %d
                """.formatted(query.replace("'", "''"), config.limit());
        try (Connection connection = DriverManager.getConnection(config.trinoJdbcUrl(), "benchmark", null);
                ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            List<Hit> hits = new ArrayList<>();
            while (resultSet.next()) {
                hits.add(new Hit(resultSet.getLong("id"), resultSet.getString("title")));
            }
            return new Result(hits);
        }
    }

    private static void waitForTrino(Config config)
            throws InterruptedException
    {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection connection = DriverManager.getConnection(config.trinoJdbcUrl(), "benchmark", null);
                    ResultSet ignored = connection.createStatement().executeQuery("SELECT 1")) {
                return;
            }
            catch (SQLException e) {
                Thread.sleep(1_000);
            }
        }
        throw new IllegalStateException("Timed out waiting for Trino at " + config.trinoJdbcUrl());
    }

    private static void assertSameHits(String query, List<Hit> nativeHits, List<Hit> trinoHits)
    {
        List<Long> nativeIds = nativeHits.stream().map(Hit::id).toList();
        List<Long> trinoIds = trinoHits.stream().map(Hit::id).toList();
        if (!nativeIds.equals(trinoIds)) {
            throw new IllegalStateException("""
                    Search parity failed for query %s
                    Native Meilisearch IDs: %s
                    Trino _search IDs:      %s
                    """.formatted(quote(query), nativeIds, trinoIds));
        }
    }

    private static String quote(String value)
    {
        return "\"" + value + "\"";
    }

    private record Config(
            URI meilisearchUrl,
            String meilisearchApiKey,
            String trinoJdbcUrl,
            int limit,
            int warmupIterations,
            int iterations,
            List<String> queries)
    {
        private static Config fromEnvironment()
        {
            return new Config(
                    URI.create(env("MEILISEARCH_URL", "http://localhost:7700")),
                    env("MEILISEARCH_API_KEY", "masterKey"),
                    env("TRINO_JDBC_URL", "jdbc:trino://localhost:18080"),
                    integer("SEARCH_BENCHMARK_LIMIT", 20),
                    integer("SEARCH_BENCHMARK_WARMUP", 3),
                    integer("SEARCH_BENCHMARK_ITERATIONS", 10),
                    List.of(env("SEARCH_BENCHMARK_QUERIES", "matrix,arrival,generated movie 1000,science fiction,synthetic deterministic").split(",")));
        }

        private static String env(String name, String defaultValue)
        {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }

        private static int integer(String name, int defaultValue)
        {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
        }
    }

    private record Hit(long id, String title) {}

    private record Result(List<Hit> hits) {}

    private record Samples(List<Long> nanos)
    {
        private double averageMillis()
        {
            return nanos.stream().mapToDouble(Samples::millis).average().orElse(0);
        }

        private double minMillis()
        {
            return nanos.stream().mapToDouble(Samples::millis).min().orElse(0);
        }

        private double maxMillis()
        {
            return nanos.stream().mapToDouble(Samples::millis).max().orElse(0);
        }

        private double percentileMillis(int percentile)
        {
            List<Long> sorted = nanos.stream().sorted(Comparator.naturalOrder()).toList();
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
            return millis(sorted.get(Math.max(0, Math.min(index, sorted.size() - 1))));
        }

        private static double millis(long nanos)
        {
            return nanos / 1_000_000.0;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T>
    {
        T get()
                throws Exception;
    }
}
