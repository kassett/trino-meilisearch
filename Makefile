TRINO_VERSION ?= 482
PLUGIN_DIR := target/meilisearch-plugin

.PHONY: build test ci coverage compose-up compose-up-test compose-down seed smoke integration-test integration-test-external benchmark-search clean-plugin

build:
	mvn -q clean package

test:
	mvn test

ci:
	mvn -q verify -DskipITs

coverage:
	mvn -q verify -DskipITs

compose-up: build
	docker compose up -d --force-recreate trino
	./docker/bin/seed-meilisearch.sh

compose-up-test: build
	docker compose up -d --force-recreate trino
	./docker/bin/seed-meilisearch.sh 4

compose-down:
	docker compose down -v

seed:
	./docker/bin/seed-meilisearch.sh

smoke:
	docker compose exec trino trino --execute "SELECT title, release_year FROM meilisearch.default.movies WHERE release_year >= 2000 ORDER BY release_year LIMIT 5"
	docker compose exec trino trino --execute "SELECT title FROM meilisearch.default.movies WHERE _search = 'matrix'"

integration-test:
	mvn -q verify

integration-test-external:
	MEILISEARCH_IT_EXTERNAL_STACK=true mvn -q verify

benchmark-search: build
	docker compose up -d --force-recreate trino
	./docker/bin/seed-meilisearch.sh $${MEILISEARCH_BENCHMARK_RECORDS:-50000}
	mvn -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=io.github.kassett.trino.meilisearch.benchmark.SearchParityBenchmark

clean-plugin:
	rm -rf $(PLUGIN_DIR)
