# Implementation Plan: Forecasts for Cities in the EU City-Limits Population List

## Summary

Generalize the existing Berlin forecast implementation into a public city
catalogue and a permission-gated forecast endpoint for each catalogue ID. Pin
all verifiable city rows in the selected EU city-limits source snapshot in
application resources; do not make population or location data requests at
runtime. Preserve the three-day
forecast response shape, authorization ordering, one-hour per-city cache,
stale fallback, and single-instance global quota, with the agreed hard limit
of 500 outbound provider HTTP requests per UTC day.

## Technical Context

- **Repository:** Java 25, Spring Boot 3.5.7, Maven multi-module build. The
  weather module already implements the Berlin controller, provider and
  permissions clients, response types, in-memory cache/single-flight refresh,
  RFC 7807 handling, and UTC daily quota. These are to be generalized rather
  than replaced wholesale.
- **Dependencies:** Spring Web and Spring Boot Starter Test are already
  declared. Do not add dependencies without approval. Use the existing
  `RestClient`, Jackson, and test support; tests must not call live services.
- **Catalogue:** Use the specified EU city-limits population list as a
  versioned snapshot. Store the snapshot retrieval date and per-city
  population, population as-of year, representative coordinates, and IANA
  timezone in the bundled application data.
  Include each city in the pinned source snapshot for which population,
  representative coordinates and timezone can be verified. Document any
  listed city omitted for unavailable required data.
- **API:** Add public `GET /weather/cities`, returning all 95 records in
  rank order with dataset source and snapshot date. Replace
  the Berlin-specific forecast handler with
  `GET /weather/forecast/{cityId}`. The `berlin` ID continues to work through
  the general route. Unknown IDs return 404 problem details. Forecast access remains
  permission-gated before cache lookup or provider access.
- **Provider:** Generalize the existing Open-Meteo DWD API request to use
  each catalogue entry's coordinates and timezone. Continue requesting the
  specified three-day fields, Celsius, and meters per second. Preserve
  timeouts and the configured outbound `User-Agent`.
- **Cache and quota:** Cache independently by city ID for one hour and
  coalesce refreshes for the same city. Keep one shared in-memory quota,
  raising its hard daily limit from 100 to 500. Count each outbound request,
  including retries and redirects, against the UTC date of dispatch.
- **Time and errors:** Use the injected `Clock`; compute forecast dates in
  the requested city's timezone and quota dates in UTC. Preserve stale
  fallback semantics and existing 403/429/502/503 RFC 7807 behavior.

## Design and Implementation Sequence

1. **Pin and model the catalogue**
   - Pin the EU city-limits source snapshot retrieved on 2026-11-07, containing
     95 city rows. Include all
     listed cities whose population, representative coordinates, and timezone
     can be verified; document omissions for
     unavailable data.
   - Add a typed city model and immutable catalogue loader backed by a
     versioned resource.
   - Validate IDs, rank uniqueness and ordering, coordinates, timezone IDs,
     and population metadata at load/test time.
2. **Expose the catalogue**
   - Add a thin controller and response DTO for `GET /weather/cities`.
   - Return all 95 pinned catalogue entries in rank order, with dataset source and
     `snapshotRetrievedAt`; do not call permissions or external services.
3. **Generalize provider and forecast orchestration**
   - Change the provider client to accept a catalogue city and use its
     coordinates and timezone for request construction and expected local
     dates.
   - Generalize Berlin-specific forecast assembly into city-specific
     orchestration and response metadata while preserving existing WMO
     descriptions, numeric weather codes, and units.
   - Replace the Berlin route with the city-ID route. Resolve unknown IDs to
     404 before permission or provider calls; for known IDs, check permissions
     before accessing cached data.
4. **Generalize caching and adjust quota**
   - Key cache entries and in-flight refreshes by city ID while keeping
     unrelated city refreshes independent.
   - Retain stale values after refresh failures or quota exhaustion, marking
     them stale and preserving their original `fetchedAt`, even if their
     forecast dates have passed.
   - Change the shared UTC daily quota limit to 500. Ensure retry and
     redirect dispatches continue to reserve separate quota slots.
5. **Remove obsolete Berlin-only surface**
   - Delete the Berlin-specific controller/service and update Berlin-only
     tests. Verify `berlin` resolves as a catalogue ID through the general
     route and is not handled by a static Berlin-specific route.
6. **Verify and document**
   - Update directly related feature documentation and service/API contract
     documentation if the catalogue endpoint or forecast path is represented
     there.
   - Run focused weather tests, then `./mvnw -pl weather verify` and
     `./mvnw verify`.

## Test Strategy

1. **Catalogue tests:** assert all 95 city rows in the pinned source snapshot
   are represented, with    ordered unique ranks,
   unique stable lowercase ASCII IDs, valid latitude/longitude and IANA
   timezones, and required population metadata. Verify dataset source and snapshot
   date. Assert the public response omits per-city citation fields.
2. **Catalogue endpoint tests:** verify public access, response shape and
   ordering, and no permissions/provider calls.
3. **Provider client tests:** verify city-specific coordinates, timezone,
   requested daily fields and units, date validation in each city's timezone,
   and rejection of malformed or unusable provider data using a test
   transport.
4. **Forecast/controller tests:** cover known and unknown IDs, replacement of
   the Berlin-specific route, exact existing forecast DTO shape, three local
   dates, and city metadata.
5. **Authorization tests:** verify the original `User-Agent` is forwarded and
   permission is checked before fresh cache, stale cache, or provider data can
   be accessed; verify denial and permission-service failure do not access
   the provider or return cached data.
6. **Cache/quota tests:** cover isolated per-city cache entries, one-hour
   expiry, same-city single-flight, independent different-city refreshes,
   stale fallback, UTC reset, concurrent reservation at the 500-request cap,
   and retries/redirects/failures counting against the cap.
7. **Regression and build:** run `./mvnw -pl weather verify`, then
   `./mvnw verify`. Do not call real external APIs in tests.

## Risks and Decisions

- **Catalogue accuracy and reproducibility:** City names, administrative
  boundaries, population years, and representative centres require
  verification against the recorded snapshot. Keep the final dataset
  reproducible and reviewable.
- **Source records and missing data:** Preserve the selected list's ordering
  and document any listed city omitted because required data cannot be
  verified.
- **Provider quota semantics:** The agreed limit is a hard global cap of 500
  requests per UTC day with no per-city reservation. Popular-city traffic can
  consume the budget before other cities are requested; without stale data,
  later misses return 429.
- **Single-instance state:** Cache and quota remain in memory and reset on
  restart. Multi-instance deployment requires shared cache/quota coordination
  and is out of scope.
- **Compatibility:** Removing the Berlin-only path is intentional. Berlin
  continues to work through its stable catalogue `cityId`; the forecast JSON
  remains otherwise compatible with the existing response contract.
