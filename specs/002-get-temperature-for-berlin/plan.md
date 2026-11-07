# Implementation Plan: Berlin 3-Day Weather Forecast

## Summary

Implement `GET /weather/forecast/berlin` in the weather service. The endpoint
checks the caller through the permissions service before accessing cache data,
then returns a three-day Berlin-local forecast from Open-Meteo's DWD API,
subject to a one-hour cache and a 100-outbound-request-per-UTC-day limit.
Weather-service forwarding, caching, quota, and failure behavior follow
`spec.md`.

## Technical Context

- **Repository:** Java 21, Spring Boot 3.5.7, Maven multi-module build. The
  weather module currently contains only its application class and
  configuration; no forecast API, HTTP client, persistence, or tests exist.
- **Existing dependencies:** Spring Web and Spring Boot Starter Test are
  declared. Do not add a dependency without approval. In particular,
  WireMock is mentioned in project guidance but is not currently declared;
  prefer testing `RestClient` through Spring's test support or an injected
  transport seam unless WireMock is approved.
- **Provider:** Open-Meteo DWD API. Use coordinates `52.52, 13.405`,
  endpoint `/v1/dwd-icon`, `Europe/Berlin`, and daily fields
  `temperature_2m_min`, `temperature_2m_max`, `weather_code`,
  `precipitation_probability_max`, and `wind_speed_10m_max`. Request Celsius
  and meters per second. Configure the endpoint through
  application configuration/environment variables rather than hardcoding it
  in service logic.
- **HTTP:** Use Spring `RestClient`, with explicit connect/read timeouts and a
  configured non-empty `User-Agent` for outbound HTTP requests. Forward the
  original inbound `User-Agent` unchanged on the permissions request.
- **Time:** Use an injected `Clock` where practical. Derive forecast dates in
  `Europe/Berlin`; derive the quota day in UTC.
- **Quota:** The agreed deployment is single-instance. Use an atomic,
  in-memory counter that reserves a call before sending it, counts failed
  outbound attempts, enforces at most 100 per UTC day, and resets when the UTC
  date changes. Every actual outbound retry or redirect request must reserve
  its own quota slot.
- **Error responses:** Use RFC 7807 problem details for weather API errors.
  Distinguish expected permission denial (403), permissions-service outage or
  unexpected response (503), quota exhaustion without stale data (429), and
  provider failure/unusable data without stale data (502).
- **Trust:** The permissions check classifies a spoofable User-Agent; it does
  not authenticate the caller.

## Design

### Request and authorization flow

1. Add a thin controller for `GET /weather/forecast/berlin`.
2. Read the original request's `User-Agent`, including the absent-header case,
   and pass it unchanged to a permissions client.
3. Configure the permissions service base URL externally. Treat only 200 as
   permission granted and 403 as denied. Map denial to a weather-service 403
   RFC 7807 response. Map transport errors and other statuses to 503 RFC 7807.
4. Do not inspect or return cached forecast data, or call Open-Meteo, until
   permission has been granted.

### Provider client and response mapping

1. Create a focused Open-Meteo client using `RestClient`, configured endpoint,
   timeouts, and a non-empty service `User-Agent`.
2. Request the exact daily fields from the spec, using Berlin city-center
   coordinates, Celsius, meters per second, percentages, and
   `Europe/Berlin`.
3. Validate the response before caching: required daily arrays must be present,
   aligned, and contain exactly the expected dates and values for today plus
   the next two Berlin calendar days. Treat malformed, incomplete, or unusable
   provider data as a provider failure.
4. Map provider WMO codes to concise English summaries. Preserve numeric codes;
   map unrecognized codes to `"Unknown"`.
5. Map the provider payload to a stable response DTO with `city`, `country`,
   `timezone`, `source`, original provider `fetchedAt`, `stale`, and exactly
   three daily forecast entries. Keep units explicit in the JSON field names
   as defined by `spec.md`.

### Cache, refresh coalescing, and daily quota

1. Add a single-entry in-memory cache for the Berlin forecast. Store the
   provider fetch timestamp, cache expiry, and validated three-day data.
2. Treat the entry as fresh for one hour, but also check its forecast dates
   against the current Berlin date. A date rollover invalidates data that no
   longer covers today and the next two days, even if its one-hour TTL has not
   expired.
3. Coalesce concurrent refreshes for this cache entry so one upstream request
   populates the cache and waiting requests use the same result.
4. Before each outbound provider HTTP request, including any retry or redirect
   hop, atomically reserve one quota slot for the UTC date on which it is
   dispatched. Never permit the counter to exceed 100. Count attempted
   requests even if they time out or fail.
5. If quota is exhausted or refresh fails, return stale cached data when
   available, with `stale: true` and its original `fetchedAt`. On a successful
   refresh, replace the cache and return `stale: false`.
6. If no cache is available, return 429 for exhausted quota and 502 for
   provider failure or unusable data.

### Error handling

Use a weather-service `@RestControllerAdvice` that returns RFC 7807 problem
details for the specified 403, 429, 502, and 503 outcomes. Do not expose
upstream response bodies, internal URLs, or implementation details in error
responses. Log unexpected downstream errors using the repository's eventual
logging conventions without converting them into successful responses.

### Configuration and operations

Implement this configuration before the provider and permissions clients.
Add configuration for the Open-Meteo base URL and permissions-service base URL
in `weather/src/main/resources/application.yml`, with environment-variable
overrides. Configure HTTP timeouts and the outbound weather-service
`User-Agent` there as well. Document required settings in `.env.example` if
environment configuration requires values beyond safe defaults. Do not add
credentials or secrets.

## Suggested Project Structure

- `weather/src/main/java/com/weathernerd/weather/api/`
  - Forecast controller and RFC 7807 exception handling.
- `weather/src/main/java/com/weathernerd/weather/service/`
  - Permission-gated forecast orchestration and forecast cache/quota behavior.
- `weather/src/main/java/com/weathernerd/weather/client/`
  - Permissions and Open-Meteo `RestClient` adapters.
- `weather/src/main/java/com/weathernerd/weather/domain/`
  - Forecast response/domain types and WMO summary mapping.
- `weather/src/main/java/com/weathernerd/weather/config/`
  - Client, timeout, endpoint, and time configuration.
- `weather/src/test/java/com/weathernerd/weather/`
  - Unit, MVC, and isolated HTTP-client tests.

Follow current repository naming if a module convention is established while
implementing; keep controllers thin and use constructor injection.

## Test Strategy

1. **WMO mapping unit tests:** cover recognized clear/cloud/fog/drizzle/rain/
   snow/showers/thunderstorm codes and unrecognized-code fallback.
2. **Provider mapping tests:** verify requested parameters, Berlin timezone,
   exact date range, units, aligned arrays, and failures for incomplete or
   malformed provider payloads.
3. **Quota/cache unit tests with fixed clock:** fresh hit, one-hour expiry,
   Berlin date rollover, UTC quota reset, atomic cap at 100, failed attempts
   consuming slots, stale fallback on quota exhaustion and refresh failure,
   no-cache 429/502, and single-flight concurrent refresh.
4. **Permissions client/orchestration tests:** verify unchanged caller
   User-Agent, permission check before cache/provider access, 403 denial
   without provider call, and 503 on permissions outage or unexpected status.
5. **Controller tests:** assert the exact successful DTO shape, three forecast
   days, units, stale metadata, and RFC 7807 error status/body types.
6. **HTTP isolation:** use Spring test mock-request support or a test transport;
   never call Open-Meteo or a deployed permissions service from tests.
7. Run `./mvnw -pl weather verify`, then `./mvnw verify`.

## Risks and Decisions

- **WireMock approval:** WireMock is not in the current Maven dependencies.
  Do not add it without approval; existing Spring test support should be
  sufficient for HTTP isolation.
- **In-memory cache/quota:** Both are lost on restart and do not coordinate
  multiple instances. This is acceptable only for the agreed single-instance
  deployment. Revisit before scaling horizontally.
- **Provider code mapping:** Keep mapping explicit and tested against Open-Meteo
  WMO descriptions. Unknown codes remain visible numerically and use the
  `"Unknown"` summary.
- **Stale dates:** Per clarification, serving stale cached data is allowed even
  if the cached date window has passed; the response must still mark it stale.
- **Permissions endpoint:** The existing permissions feature uses
  `POST /permissions/is_granted` and returns a JSON 403 body. The weather
  service maps that denial to its own RFC 7807 response as specified here.
