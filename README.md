# Trino Meilisearch Connector

Read-only Trino connector for Meilisearch.

## Installation

Download the plugin zip from the GitHub release for the version you want. If the version is 0.1.0:

```bash
curl -LO https://github.com/kassett/trino-meilisearch/releases/download/trino-meilisearch@0.1.0/trino-meilisearch-0.1.0.zip
curl -LO https://github.com/kassett/trino-meilisearch/releases/download/trino-meilisearch@0.1.0/trino-meilisearch-0.1.0.zip.sha256
shasum -a 256 -c trino-meilisearch-0.1.0.zip.sha256
```

Install it into Trino's plugin directory and restart Trino:

```bash
mkdir -p /usr/lib/trino/plugin/meilisearch
unzip trino-meilisearch-0.1.0.zip -d /usr/lib/trino/plugin/meilisearch
```

Create a catalog file at `/etc/trino/catalog/meilisearch.properties`:

```properties
connector.name=meilisearch
meilisearch.url=http://meilisearch:7700
meilisearch.api-key=masterKey
meilisearch.schema-name=default
meilisearch.schema.sample-size=100
meilisearch.page-size=100
```

Query a Meilisearch index as a Trino table:

```sql
SELECT title
FROM meilisearch.default.movies
WHERE _search = 'matrix';
```

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

Pull requests run unit tests plus Docker-backed integration tests against the real Docker Compose stack. Pushes to `main` do not rerun tests; release automation is handled by the release workflow.

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

`make integration-test` runs real Trino queries that join all three catalogs. CI starts the Compose stack explicitly with `make compose-up-test`, seeds the small deterministic fixture, then runs `make integration-test-external` so teardown remains owned by the workflow. Full-text search is exposed through a hidden `_search` column:

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
