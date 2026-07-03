#!/usr/bin/env bash
set -euo pipefail

base_url="${MEILISEARCH_URL:-http://localhost:7700}"
api_key="${MEILISEARCH_API_KEY:-masterKey}"
record_count="${1:-${MEILISEARCH_SEED_RECORDS:-50000}}"
batch_size="${MEILISEARCH_SEED_BATCH_SIZE:-1000}"

case "$record_count" in
  ''|*[!0-9]*)
    echo "record count must be a non-negative integer" >&2
    exit 2
    ;;
esac

case "$batch_size" in
  ''|*[!0-9]*)
    echo "MEILISEARCH_SEED_BATCH_SIZE must be a positive integer" >&2
    exit 2
    ;;
esac

if (( batch_size <= 0 )); then
  echo "MEILISEARCH_SEED_BATCH_SIZE must be a positive integer" >&2
  exit 2
fi

until curl -fsS "$base_url/health" >/dev/null; do
  sleep 1
done

wait_task() {
  local task_uid="$1"
  local status
  while true; do
    status="$(curl -fsS "$base_url/tasks/$task_uid" \
      -H "Authorization: Bearer $api_key" | jq -r '.status')"
    case "$status" in
      succeeded)
        return 0
        ;;
      failed|canceled)
        curl -fsS "$base_url/tasks/$task_uid" -H "Authorization: Bearer $api_key" >&2
        return 1
        ;;
    esac
    sleep 1
  done
}

if curl -fsS "$base_url/indexes/movies" -H "Authorization: Bearer $api_key" >/dev/null; then
  task_uid="$(curl -fsS -X DELETE "$base_url/indexes/movies" \
    -H "Authorization: Bearer $api_key" | jq -r '.taskUid')"
  wait_task "$task_uid"
fi

task_uid="$(curl -fsS -X POST "$base_url/indexes" \
  -H "Authorization: Bearer $api_key" \
  -H "Content-Type: application/json" \
  --data '{"uid":"movies","primaryKey":"id"}' | jq -r '.taskUid')"
wait_task "$task_uid"

task_uid="$(curl -fsS -X PATCH "$base_url/indexes/movies/settings" \
  -H "Authorization: Bearer $api_key" \
  -H "Content-Type: application/json" \
  --data '{"filterableAttributes":["id","release_year","rating","title"],"sortableAttributes":["id","release_year","rating","title"]}' | jq -r '.taskUid')"
wait_task "$task_uid"

generate_batch() {
  local start="$1"
  local end="$2"

  jq -n --argjson start "$start" --argjson end "$end" '
    def genre($id):
      ["action", "animation", "drama", "science fiction", "fantasy", "thriller"][($id % 6)];

    [
      range($start; $end + 1) as $id |
      if $id == 1 then
        {
          id: 1,
          title: "The Matrix",
          overview: "A hacker discovers the truth about his reality.",
          release_year: 1999,
          rating: 8.7,
          genres: ["action", "science fiction"]
        }
      elif $id == 2 then
        {
          id: 2,
          title: "Spirited Away",
          overview: "A child enters a world of spirits.",
          release_year: 2001,
          rating: 8.6,
          genres: ["animation", "fantasy"]
        }
      elif $id == 3 then
        {
          id: 3,
          title: "Arrival",
          overview: "A linguist works to communicate with alien visitors.",
          release_year: 2016,
          rating: 7.9,
          genres: ["drama", "science fiction"]
        }
      elif $id == 4 then
        {
          id: 4,
          title: "Mad Max: Fury Road",
          overview: "Survivors flee across a desert wasteland.",
          release_year: 2015,
          rating: 8.1,
          genres: ["action"]
        }
      else
        {
          id: $id,
          title: ("Generated Movie " + ($id | tostring)),
          overview: ("Generated movie " + ($id | tostring) + " with synthetic but deterministic seed data."),
          release_year: (1980 + ($id % 45)),
          rating: (((50 + ($id % 50)) / 10) | tonumber),
          genres: [genre($id), genre($id + 2)]
        }
      end
    ]
  '
}

if (( record_count == 0 )); then
  exit 0
fi

start=1
while (( start <= record_count )); do
  end=$((start + batch_size - 1))
  if (( end > record_count )); then
    end="$record_count"
  fi

  task_uid="$(generate_batch "$start" "$end" | curl -fsS -X POST "$base_url/indexes/movies/documents" \
    -H "Authorization: Bearer $api_key" \
    -H "Content-Type: application/json" \
    --data-binary @- | jq -r '.taskUid')"
  wait_task "$task_uid"
  start=$((end + 1))
done
