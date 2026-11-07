# Tasks: Forecasts for Cities in the EU City-Limits Population List

Tasks are ordered and intended to be completed one at a time, with one commit
per task. Write or update tests before the implementation tasks they cover.
Do not call live external APIs from tests.

## City catalogue and provenance

- [x] T001 Add city-catalogue validation tests.
  - Define test cases for all 95 city rows in the pinned source
    snapshot, consecutive unique ranks in
    order, unique lowercase ASCII IDs, valid coordinate ranges, valid IANA
    timezones, required population metadata, and non-empty population,
    coordinate, and timezone citations.
  - Verify the catalogue exposes a recorded snapshot date and source and is
    available without permissions or provider calls.
  - References: spec City inclusion and catalogue, acceptance criteria 1–4;
    plan Test Strategy 1.

- [x] T002 Pin and load the versioned city catalogue.
  - Pin the specified EU city-limits source snapshot retrieved on 2026-11-07.
    Include every listed city with verifiable required data and provenance;
    document any source row omitted for missing or unverifiable data.
  - Include stable IDs, canonical city and country names, rank, population,
    population as-of year, representative coordinates, IANA timezone, and
    citations for population, coordinates, and timezone.
  - Add an immutable typed catalogue model and loader backed by application
    data. Validate the required fields and satisfy T001.
  - References: spec Summary and City inclusion and catalogue; plan Design 1.

- [x] T003 Add public city-catalogue endpoint tests.
  - Verify `GET /weather/cities` returns all 95 pinned catalogue records in
    rank order with the required public fields, dataset source, and snapshot date.
  - Assert per-city source citations are not exposed in the response and
    requests do not call permissions or Open-Meteo.
  - References: spec City catalogue endpoint and acceptance criterion 1;
    plan Test Strategy 2.

- [x] T004 Implement `GET /weather/cities`.
  - Add public response DTOs and a thin controller returning the pinned
    catalogue and dataset metadata.
  - Keep source citations in bundled catalogue data only; do not make
    permission checks or external requests.
  - Satisfy T003.
  - References: spec City catalogue endpoint; plan Design 2.

## City-specific forecast provider

- [x] T005 Add city-specific Open-Meteo client tests.
  - Verify requests use the selected city's coordinates and IANA timezone,
    exact daily fields, Celsius, meters per second, and a three-day forecast.
  - Verify response date validation uses that city's local calendar and
    rejects missing, misaligned, malformed, or unusable data.
  - Use an isolated test transport; do not call Open-Meteo.
  - References: spec Forecast endpoint and Successful forecast response;
    plan Test Strategy 3.

- [x] T006 Generalize the Open-Meteo client for catalogue cities.
  - Accept a city record and build provider requests from its representative
    coordinates and timezone instead of Berlin constants.
  - Validate the provider's dates and daily fields in the city's timezone;
    preserve existing units, WMO summaries, timeout behavior, outbound
    `User-Agent`, retries, redirects, and quota reservation for every
    dispatched request.
  - Satisfy T005 without adding dependencies.
  - References: spec Forecast endpoint and Cache and provider request quota;
    plan Design 3 and Technical Context.

## City-specific forecast API and authorization

- [x] T007 Add city-ID forecast API tests.
  - Cover a known catalogue ID and an unknown ID.
  - Verify `berlin` is served through the city-ID route rather than a
    Berlin-specific handler.
  - Verify the successful response preserves the existing forecast JSON shape
    and units, identifies the selected city, and has exactly three local-date
    entries.
  - Assert an unknown ID returns 404 problem details without calling
    permissions or the provider.
  - References: spec Forecast endpoint, Successful forecast response, and
    acceptance criteria 5–6; plan Test Strategy 4.

- [x] T008 Add authorization-ordering tests for city forecasts.
  - Verify the original `User-Agent` is forwarded unchanged and permissions
    are checked before fresh-cache, stale-cache, or provider data is accessed.
  - Verify denial returns 403 and a permissions outage or unexpected status
    returns 503, without returning cache data or calling the provider.
  - References: spec Authorization and acceptance criterion 7; plan Test
    Strategy 5.

- [x] T009 Generalize forecast orchestration and routing.
  - Replace Berlin-specific forecast orchestration with city-ID lookup and
    city-specific response metadata.
  - Resolve unknown IDs to 404 before permission/provider calls. For known
    IDs, preserve permission checks before cache access and provider calls.
  - Keep the existing forecast response fields, WMO handling, RFC 7807 errors,
    and satisfy T007–T008.
  - References: spec Forecast endpoint, Authorization, and Successful forecast
    response; plan Design 3.

## Per-city cache and global quota

- [x] T010 Add multi-city cache and refresh-coalescing tests.
  - Verify cache entries are isolated by city, fresh for one hour, and
    refreshed when expired or when they no longer cover today and the next
    two dates in the city's timezone.
  - Verify concurrent refreshes for one city share a provider request while
    different cities refresh independently.
  - Verify refresh failure preserves and returns stale data with its original
    `fetchedAt`, including when the stale dates have passed.
  - References: spec Cache and provider request quota and acceptance
    criteria 8 and 10; plan Test Strategy 6.

- [x] T011 Generalize cache and single-flight refresh by city ID.
  - Replace the Berlin-only cache entry with per-city cache entries and
    per-city in-flight refreshes.
  - Preserve the one-hour freshness rule, local-date coverage check, stale
    fallback, original `fetchedAt`, and permission-before-cache ordering.
  - Satisfy T010.
  - References: spec Cache and provider request quota; plan Design 4.

- [x] T012 Add tests for the agreed 500-request global quota.
  - Verify all cities share one quota, reservations are thread-safe, the cap
    is not exceeded, and the counter resets at UTC midnight.
  - Verify failed attempts, retries, and redirects each consume a reservation;
    quota exhaustion serves stale data when available and otherwise returns
    429 problem details.
  - References: spec Cache and provider request quota and acceptance
    criteria 9–11; plan Test Strategy 6.

- [x] T013 Set the shared UTC daily quota limit to 500.
  - Update quota implementation and related configuration/tests from the
    former limit to the hard global limit of 500 outbound provider HTTP
    requests per UTC day, with no per-city reservation.
  - Preserve reservation before dispatch and stale/429 behavior; satisfy T012.
  - References: spec Cache and provider request quota; plan Design 4.

## Remove the Berlin-only route and verify

- [x] T014 Remove the obsolete Berlin-only implementation surface.
  - Remove the Berlin-specific controller and service after their behavior is
    covered by the city-ID route.
  - Update or replace Berlin-only tests and references; verify Berlin remains
    available using its catalogue ID and no static Berlin-specific handler
    remains.
  - References: spec Forecast endpoint and acceptance criterion 5; plan
    Design 5.

- [x] T015 Update related API documentation and contracts.
  - Document `GET /weather/cities` and `GET /weather/forecast/{cityId}`,
    including public catalogue access, authorization behavior, response
    fields, and error statuses, wherever the existing service contract
    documents these routes.
  - Keep the documentation aligned with the feature spec and omit per-city
    citations from the public response contract.
  - References: spec City catalogue endpoint, Forecast endpoint, and
    Authorization; plan Design 6.

- [x] T016 Run focused and full verification.
  - Run the focused weather tests, then `./mvnw -pl weather verify` and
    `./mvnw verify`.
  - Confirm the entire pinned catalogue passes its data-integrity checks;
    tests use no live external APIs; review response shapes, authorization
    ordering, cache behavior, quota limit, and error mappings.
  - Previous validation used an older local JDK with a version override;
    full reactor verification now passes in a Java 25 container.
  - References: spec Acceptance criteria; plan Test Strategy 7 and Design 6.
