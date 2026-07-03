package io.github.kassett.trino.meilisearch.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

final class ComposeIntegrationIT
{
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
    private static final String EXTERNAL_STACK_ENV = "MEILISEARCH_IT_EXTERNAL_STACK";

    @BeforeAll
    static void startStack()
            throws Exception
    {
        if (!externalStack()) {
            command(Duration.ofSeconds(30), "docker", "compose", "down", "-v");
            command(COMMAND_TIMEOUT, "docker", "compose", "up", "-d");
            command(Duration.ofSeconds(30), "./docker/bin/seed-meilisearch.sh", "4");
        }
        waitForTrino();
    }

    @AfterAll
    static void stopStack()
            throws Exception
    {
        if (!externalStack()) {
            command(Duration.ofSeconds(30), "docker", "compose", "down", "-v");
        }
    }

    @Test
    void joinsMeilisearchPostgresAndMysql()
            throws SQLException
    {
        try (Connection connection = trinoConnection()) {
            assertRows(connection,
                    """
                    SELECT m.title, p.critic_score, y.gross_millions
                    FROM meilisearch.default.movies m
                    JOIN postgres.public.movie_reviews p ON m.id = p.movie_id
                    JOIN mysql.movies.movie_financials y ON p.movie_id = y.movie_id
                    WHERE m.title = 'Arrival'
                    """,
                    List.of(List.of("Arrival", 94, new BigDecimal("203.40"))));
        }
    }

    @Test
    void joinsSearchResultsWithPostgres()
            throws SQLException
    {
        try (Connection connection = trinoConnection()) {
            assertRows(connection,
                    """
                    SELECT m.title, p.review_count
                    FROM meilisearch.default.movies m
                    JOIN postgres.public.movie_reviews p ON m.id = p.movie_id
                    WHERE m._search = 'matrix'
                    """,
                    List.of(List.of("The Matrix", 241)));
        }
    }

    @Test
    void aggregatesAcrossAllThreeCatalogs()
            throws SQLException
    {
        try (Connection connection = trinoConnection()) {
            assertRows(connection,
                    """
                    SELECT count(*)
                    FROM meilisearch.default.movies m
                    JOIN postgres.public.movie_reviews p ON m.id = p.movie_id
                    JOIN mysql.movies.movie_financials y ON p.movie_id = y.movie_id
                    WHERE p.critic_score >= 90 AND y.gross_millions > 300
                    """,
                    List.of(List.of(2L)));
        }
    }

    private static void waitForTrino()
            throws InterruptedException
    {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            try (Connection connection = trinoConnection();
                    ResultSet ignored = connection.createStatement().executeQuery("SELECT 1")) {
                return;
            }
            catch (SQLException e) {
                Thread.sleep(1_000);
            }
        }
        fail("Timed out waiting for Trino JDBC endpoint");
    }

    private static Connection trinoConnection()
            throws SQLException
    {
        return DriverManager.getConnection("jdbc:trino://localhost:18080", "test", null);
    }

    private static boolean externalStack()
    {
        return Boolean.parseBoolean(System.getenv().getOrDefault(EXTERNAL_STACK_ENV, "false"));
    }

    private static void assertRows(Connection connection, String sql, List<List<Object>> expected)
            throws SQLException
    {
        try (ResultSet resultSet = connection.createStatement().executeQuery(sql)) {
            int columnCount = resultSet.getMetaData().getColumnCount();
            List<List<Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                List<Object> row = new ArrayList<>();
                for (int column = 1; column <= columnCount; column++) {
                    row.add(resultSet.getObject(column));
                }
                rows.add(row);
            }
            assertThat(rows).isEqualTo(expected);
        }
    }

    private static String command(Duration timeout, String... command)
            throws IOException, InterruptedException
    {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(timeout)) {
            process.destroyForcibly();
            fail("Timed out running command: " + String.join(" ", command));
        }
        String output = new String(process.getInputStream().readAllBytes());
        if (process.exitValue() != 0) {
            fail("Command failed: %s%nExit code: %s%nOutput:%n%s".formatted(String.join(" ", command), process.exitValue(), output));
        }
        return output;
    }
}
