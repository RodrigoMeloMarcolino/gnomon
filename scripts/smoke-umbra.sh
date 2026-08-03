#!/usr/bin/env bash
set -euo pipefail

compose_project="${COMPOSE_PROJECT_NAME:-gnomon-umbra-smoke}"
smoke_api_port="${SMOKE_API_PORT:-18080}"
export POSTGRES_PORT="${SMOKE_POSTGRES_PORT:-15432}"
export REDIS_PORT="${SMOKE_REDIS_PORT:-16379}"
export KEYCLOAK_PORT="${SMOKE_KEYCLOAK_PORT:-18081}"
export API_PORT="$smoke_api_port"
api_url="${API_URL:-http://localhost:${smoke_api_port}}"
date_local="$(TZ=America/Fortaleza date -d 'tomorrow' +%F)"
calendar_id="30000000-0000-4000-8000-000000000001"
offering_id="40000000-0000-4000-8000-000000000001"
idempotency_key="90000000-0000-4000-8000-000000000001"

cleanup() {
  docker compose -p "$compose_project" down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker compose -p "$compose_project" --profile full up -d --build >/dev/null

ready=0
for _ in $(seq 1 60); do
  if curl --fail --silent --show-error "$api_url/v1/ready" >/dev/null; then
    ready=1
    break
  fi
  sleep 2
done
test "$ready" = 1

openapi_json="$(curl --fail --silent --show-error "$api_url/v3/api-docs")"
jq -e '.openapi and .paths and .components.securitySchemes.bearerAuth' <<<"$openapi_json" >/dev/null
jq -e '
  .paths["/v1/public/tenants/{tenantSlug}"] and
  .paths["/v1/public/tenants/{tenantSlug}/calendars"] and
  .paths["/v1/public/tenants/{tenantSlug}/offerings"] and
  .paths["/v1/public/tenants/{tenantSlug}/available-slots"] and
  .paths["/v1/public/tenants/{tenantSlug}/appointments"] and
  .paths["/v1/tenants"] and
  (.paths["/v1/tenants"].get.security[0].bearerAuth == []) and
  (.paths["/v1/public/tenants/{tenantSlug}"].get.security == null) and
  (.components.schemas.AvailableSlotsResponse.properties.available_start_times != null) and
  (.components.schemas.AvailableSlotsResponse.properties.availableStartTimes == null) and
  (.components.schemas.CreateAppointmentRequest.properties.calendar_id != null) and
  (.components.schemas.CreateAppointmentRequest.properties.calendarId == null) and
  (.components.schemas.ApiErrorResponse.properties.error != null)
' <<<"$openapi_json" >/dev/null
openapi_yaml="$(curl --fail --silent --show-error "$api_url/v3/api-docs.yaml")"
grep -Fq '/v1/public/tenants/{tenantSlug}/appointments:' <<<"$openapi_yaml"

assert_preflight() {
  local path="$1" method="$2" requested_headers="${3:-}"
  local headers
  local -a curl_args=(-X OPTIONS "$api_url$path"
    -H 'Origin: http://localhost:3000'
    -H "Access-Control-Request-Method: $method")
  if [[ -n "$requested_headers" ]]; then
    curl_args+=(-H "Access-Control-Request-Headers: $requested_headers")
  fi
  headers="$(curl --silent --show-error --dump-header - --output /dev/null \
    "${curl_args[@]}")"
  grep -Fqi 'access-control-allow-origin: http://localhost:3000' <<<"$headers"
}

assert_preflight '/v1/public/tenants/umbra-smoke' GET
assert_preflight '/v1/public/tenants/umbra-smoke/appointments' POST 'Content-Type, Idempotency-Key'
assert_preflight '/v1/tenants' GET Authorization

put_headers="$(curl --silent --show-error --dump-header - --output /dev/null \
  -X OPTIONS "$api_url/v1/tenants/umbra-smoke/calendars/$calendar_id/offerings" \
  -H 'Origin: http://localhost:3000' \
  -H 'Access-Control-Request-Method: PUT' \
  -H 'Access-Control-Request-Headers: Authorization, Content-Type')"
! grep -Fqi 'access-control-allow-origin:' <<<"$put_headers"

docker compose -p "$compose_project" --profile full --profile smoke run --rm smoke-seed >/dev/null

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
