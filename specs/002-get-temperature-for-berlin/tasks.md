# Tasks: Berlin 3-Day Weather Forecast

Tasks are ordered and intended to be completed one at a time, with one commit
per task. Write tests from the acceptance criteria before the implementation
they cover. Do not add dependencies without approval; the current plan calls
for using the existing Spring Web and Spring Boot Test dependencies.

- [x] T000 Configure provider and permissions endpoints, HTTP timeouts, and
  outbound User-Agent before implementing either client.
  - Add environment-overridable Open-Meteo and permissions-service base URLs,
    HTTP timeouts, and a non-empty service User-Agent in application
    configuration.
  - Document required non-default settings in `.env.example` if applicable;
    do not add secrets.
  - References: plan Configuration and operations.

## Contract and response model

- [x] T001 Define and test the public forecast response model.
  - Add tests for JSON field names, types, units, and exactly three daily
    entries, including city, country, timezone, source, `fetchedAt`, and
    `stale`.
  - Include Berlin-local dates and all requested daily values:
    minimum/maximum Celsius temperatures, summary, numeric WMO code,
    precipitation probability percentage, and wind speed in meters per second.
  - References: spec Successful response and acceptance criteria 1–3.

- [x] T002 Implement the forecast response/domain types.
  - Add typed response and daily forecast models that satisfy T001.
  - Use explicit field names/serialization so units remain clear and match
    `spec.md`.
  - References: spec Successful response; plan Provider client and response
    mapping.

## Provider mapping

- [x] T003 Add tests for WMO weather-code summaries.
  - Cover representative recognized codes from clear, cloudy, fog, drizzle,
    rain, snow, showers, and thunderstorm categories.
  - Verify unrecognized numeric codes are preserved and summarized as
    `"Unknown"`.
  - References: spec Successful response; plan Test Strategy 1.

- [x] T004 Implement the WMO summary mapper.
  - Map the documented Open-Meteo WMO interpretation codes to concise English
    descriptions; default unrecognized codes to `"Unknown"`.
  - Satisfy T003.
  - References: spec Clarifications resolved; plan Provider client and response
    mapping.

- [x] T005 Add tests for provider request parameters and payload validation.
  - Verify Berlin coordinates `52.52, 13.405`, `Europe/Berlin`, Celsius,
    meters per second, percentages, required daily fields, and the dates for
    today plus the next two Berlin calendar days.
  - Verify missing, misaligned, incomplete, malformed, or unexpected date
    arrays are rejected as unusable data.
  - Use a mocked HTTP transport; never call Open-Meteo.
  - References: spec Summary, successful response, and acceptance criteria 1–3;
    plan Test Strategy 2.

- [x] T006 Implement the Open-Meteo client and response mapper.
  - Use configured endpoint and `RestClient` with explicit timeouts and a
    non-empty configured service User-Agent.
  - Request and validate the provider fields/date range from T005.
  - Convert valid provider data to domain data with original fetch time and
    mapped WMO summaries.
  - Satisfy T005 without adding unapproved dependencies.
  - References: plan Provider client and response mapping; Test Strategy 2.

## Permissions gate

- [x] T007 Add tests for permissions client and access ordering.
  - Verify the original inbound User-Agent, including when absent, is passed
    unchanged to the permissions service.
  - Verify only HTTP 200 grants access, 403 denies, and transport failures or
    other statuses fail closed.
  - Verify permission check occurs before cache lookup/return and provider
    access; denied/outage cases must not expose cached data or call provider.
  - Mock HTTP interactions; do not call a real permissions service.
  - References: spec Authorization and acceptance criterion 4–5; plan Request
    and authorization flow; Test Strategy 4.

- [x] T008 Implement the permissions-service client and authorization flow.
  - Configure the permissions base URL externally and use `RestClient` with
    timeouts and the service's configured User-Agent.
  - Forward the original caller User-Agent unchanged.
  - Continue only on 200, map 403 to a denied result, and fail closed on
    connection errors or unexpected responses.
  - Ensure authorization precedes cache access and provider calls; satisfy T007.
  - References: spec Endpoint and Authorization; plan Request and authorization
    flow.

## Cache, quota, and refresh coordination

- [x] T009 Add cache, freshness, and Berlin-date tests.
  - With a fixed clock, verify a fresh value is reused within one hour and
    refresh occurs after expiry.
  - Verify forecasts that no longer cover today plus the next two
    `Europe/Berlin` dates are refreshed even if the one-hour TTL remains;
    such an expired-date forecast may still be returned as stale on refresh
    failure.
  - References: spec Caching and provider quota, acceptance criterion 6; plan
    Cache, refresh coalescing, and daily quota; Test Strategy 3.

- [x] T010 Implement the single-entry forecast cache.
  - Store validated forecast, original provider `fetchedAt`, and cache expiry.
  - Return fresh cache data without a provider request.
  - Invalidate data at one-hour expiry or when its date window no longer
    matches the current Berlin forecast dates.
  - Satisfy T009.
  - References: spec Caching and provider quota; plan cache design.

- [x] T011 Add single-flight refresh tests.
  - Verify concurrent refreshes for the same Berlin cache key produce one
    provider request and share the resulting forecast.
  - Verify provider failure is propagated to all waiters so stale fallback or
    the correct error can be selected.
  - References: spec Caching and provider quota, acceptance criterion 6; plan
    cache design; Test Strategy 3.

- [x] T012 Implement single-flight refresh coordination.
  - Coalesce concurrent refreshes for the Berlin forecast entry into one
    upstream request and share its result/error.
  - Satisfy T011 without holding broad locks during unrelated work.
  - References: spec Caching and provider quota; plan cache design.

- [x] T013 Add UTC daily quota tests.
  - With a fixed clock, verify at most 100 outbound attempts are reserved per
    UTC day, failed attempts consume a slot, and the counter resets at UTC
    midnight.
  - Verify concurrent reservations cannot exceed the cap.
  - Verify retries and redirect hops each consume a separate quota slot and
    are not allowed to dispatch when no quota remains.
  - References: spec Caching and provider quota, acceptance criterion 7; plan
    Test Strategy 3.

- [x] T014 Implement the in-memory daily provider-call quota.
  - Atomically reserve a slot before each outbound request, count attempted
    requests, enforce the 100-request maximum, and reset by UTC date.
  - Keep the documented single-instance assumption explicit.
  - Satisfy T013.
  - References: spec Caching and provider quota; plan quota design.

- [x] T015 Add stale fallback and no-cache error tests.
  - Verify expired cached data is returned with `stale: true` and unchanged
    original `fetchedAt` when quota is exhausted or refresh fails.
  - Verify no-cache quota exhaustion maps to 429, and no-cache provider failure
    or unusable data maps to 502.
  - Verify a successful refresh replaces the cache and returns `stale: false`.
  - References: spec Caching and provider quota and acceptance criteria 8–9;
    plan Test Strategy 3.

- [x] T016 Implement stale fallback and provider error decisions.
  - Use stale data on quota exhaustion or any refresh failure when available.
  - Otherwise distinguish exhausted quota (429) from provider error/unusable
    response (502).
  - Preserve original `fetchedAt`, set the stale flag correctly, and satisfy
    T015.
  - References: spec Caching and provider quota; plan cache/quota and error
    handling.

## API endpoint and configuration

- [x] T017 Add controller and RFC 7807 contract tests.
  - Verify `GET /weather/forecast/berlin` returns the agreed successful
    response shape and exactly three daily entries.
  - Verify denied permissions returns 403 problem details, permissions
    outage/unexpected response returns 503, quota exhaustion without cache
    returns 429, and provider failure without cache returns 502.
  - References: spec Endpoint, Successful response, Authorization, and
    acceptance criteria 1–9; plan Test Strategy 5.

- [x] T018 Implement the thin forecast controller and error mapping.
  - Expose `GET /weather/forecast/berlin` without a request body.
  - Delegate authorization and forecast work to services.
  - Return RFC 7807 problem details with the specified status for expected
    failures; do not leak upstream response bodies or internals.
  - Satisfy T017.
  - References: spec Endpoint and Authorization; plan error handling.

## Final verification

- [x] T019 Run focused and full verification.
  - Run the focused weather module tests, then `./mvnw -pl weather verify` and
    `./mvnw verify`.
  - Confirm tests cover response shape, provider validation, permissions
    ordering, cache/TTL/date rollover, single-flight refresh, UTC quota cap and
    reset, stale fallback, and RFC 7807 failures.
  - Confirm no tests call real APIs and no unapproved dependency was added.
  - Confirm all outbound provider requests, including retries and redirects,
    consume quota slots, and stale fallback may return expired-date data.
  - References: plan Test Strategy and spec acceptance criteria 1–9.
