#!/usr/bin/env bash
set -euo pipefail

api_url="${API_URL:-http://localhost:8080}"
date_local="$(TZ=America/Fortaleza date -d 'tomorrow' +%F)"
calendar_id="30000000-0000-4000-8000-000000000001"
offering_id="40000000-0000-4000-8000-000000000001"
idempotency_key="90000000-0000-4000-8000-000000000001"

curl --fail --silent --show-error "$api_url/v1/ready" >/dev/null
docker compose --profile smoke run --rm smoke-seed >/dev/null

profile="$(curl --fail --silent --show-error "$api_url/v1/public/tenants/umbra-smoke")"
calendars="$(curl --fail --silent --show-error "$api_url/v1/public/tenants/umbra-smoke/calendars")"
offerings="$(curl --fail --silent --show-error "$api_url/v1/public/tenants/umbra-smoke/offerings")"
slots="$(curl --fail --silent --show-error "$api_url/v1/public/tenants/umbra-smoke/available-slots?calendar_id=$calendar_id&offering_id=$offering_id&date=$date_local")"

jq -e '.slug == "umbra-smoke" and .currency_code == "BRL"' <<<"$profile" >/dev/null
jq -e '.[0].id == "30000000-0000-4000-8000-000000000001"' <<<"$calendars" >/dev/null
jq -e '.[0].title == "Corte Solar" and .[0].price_cents == 8000' <<<"$offerings" >/dev/null
start_at="$(jq -r '.available_start_times[] | select(endswith("T12:00:00Z"))' <<<"$slots" | head -n1)"
test -n "$start_at"

payload="$(jq -n --arg calendar_id "$calendar_id" --arg offering_id "$offering_id" --arg start_at "$start_at" '{calendar_id:$calendar_id, offering_id:$offering_id, start_at:$start_at, customer_name:"Umbra Smoke", customer_phone:"+5585999990000"}')"
first="$(curl --fail --silent --show-error -H 'Content-Type: application/json' -H "Idempotency-Key: $idempotency_key" -d "$payload" -w '\n%{http_code}' "$api_url/v1/public/tenants/umbra-smoke/appointments")"
second="$(curl --fail --silent --show-error -H 'Content-Type: application/json' -H "Idempotency-Key: $idempotency_key" -d "$payload" -w '\n%{http_code}' "$api_url/v1/public/tenants/umbra-smoke/appointments")"
first_id="$(head -n-1 <<<"$first" | jq -r .id)"
second_id="$(head -n-1 <<<"$second" | jq -r .id)"
test "$(tail -n1 <<<"$first")" = 201
test "$(tail -n1 <<<"$second")" = 200
test "$first_id" = "$second_id"
printf 'Umbra smoke passed: appointment %s\n' "$first_id"
