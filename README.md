# Trino Meilisearch Connector

Read-only Trino connector for Meilisearch.

## Development

```bash
direnv allow
make test
make build
```

If `direnv` is not enabled, run commands through Nix:

```bash
nix develop -c make test
nix develop -c make build
```

## CI and Releases

Pull requests run `make ci`, which builds and tests without Docker-backed integration tests. Pushes to `main`, manual dispatches, and the nightly schedule run `make integration-test` against the real Docker Compose stack.

Releases use Changesets. Add a changeset for user-visible changes:

```bash
nix develop -c pnpm changeset
```

Merging the generated release PR updates `CHANGELOG.md`, applies the release version to `pom.xml` with Maven's `versions:set`, tags the release, and creates GitHub release notes.

## Local Docker Stack

```bash
nix develop -c make compose-up
nix develop -c make smoke
nix develop -c make integration-test
nix develop -c make compose-down
```

The local stack starts Trino, Meilisearch, Postgres, and MySQL. The seeded catalogs are:

- `meilisearch.default.movies`
- `postgres.public.movie_reviews`
- `mysql.movies.movie_financials`

`make integration-test` runs real Trino queries that join all three catalogs. Full-text search is exposed through a hidden `_search` column:

`docker/bin/seed-meilisearch.sh` accepts an optional record count. It defaults to `50000` mostly unique generated movie records, while the integration test passes `4` to keep the test fixture small and deterministic:

```bash
./docker/bin/seed-meilisearch.sh 100000
MEILISEARCH_SEED_BATCH_SIZE=5000 ./docker/bin/seed-meilisearch.sh 250000
```

Run a search parity benchmark to compare Trino `_search` against Meilisearch native `/search`:

```bash
nix develop -c make benchmark-search
MEILISEARCH_BENCHMARK_RECORDS=100000 SEARCH_BENCHMARK_ITERATIONS=25 nix develop -c make benchmark-search
```

The benchmark fails if Trino returns a different top-N ID list than Meilisearch for any query, then prints native and Trino latency summaries.

```sql
SELECT title
FROM meilisearch.default.movies
WHERE _search = 'matrix';
```

## Catalog Properties

```properties
connector.name=meilisearch
meilisearch.url=http://meilisearch:7700
meilisearch.api-key=masterKey
meilisearch.schema-name=default
meilisearch.schema.sample-size=100
meilisearch.page-size=100
```

Optional schema overrides can be supplied with `meilisearch.schema-file=/path/to/schema.json`.
The schema file shape is:

```json
{
  "movies": {
    "id": "bigint",
    "title": "varchar",
    "release_year": "bigint",
    "rating": "double",
    "genres": "json"
  }
}
```
