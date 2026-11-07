# Implementation Plan: Conditional Rain Forecast

## Summary

Extend the city forecast response with a separate three-day rain-probability
forecast for catalogue city names containing fewer than seven Unicode letters.
Use Open-Meteo's existing daily `precipitation_probability_max` values, keep
their dates aligned with the weather forecast, and omit rain data for names
with seven or more letters. The city catalogue endpoint remains unchanged.

## Technical Context

- **Repository:** Java 25, Spring Boot 3.5.7, Maven multi-module build.
- **Affected module:** `weather/`. Forecast data is retrieved by the existing
  `OpenMeteoClient`, composed and cached by `CityForecastService`, and returned
  from `GET /weather/forecast/{cityId}`.
- **Dependencies:** No new dependencies. Use Java code-point iteration,
  `Character.isLetter`, the existing Jackson configuration, and current JUnit
  and Spring test support.
- **Provider data:** The Open-Meteo request already includes daily
  `precipitation_probability_max` and validates its unit, values, and date
  alignment. Preserve this value in a dedicated rain forecast model rather
  than the generic weather-entry model.
- **Eligibility:** Determine eligibility from the canonical city name in the
  pinned catalogue. Count Unicode code points for which `Character.isLetter`
  is true; accented letters count once, while whitespace, punctuation, and
  digits do not. Include rain data only for counts below seven.
- **API:** For eligible cities, return a `rainForecast` array with three
  `{ "date": ..., "probability": ... }` entries. Omit the property entirely
  for ineligible cities. Remove `precipitationProbability` from generic
  `forecast` entries. Do not add a rain-eligibility property to
  `GET /weather/cities`.
- **Caching and errors:** Continue to cache the complete response per city.
  Stale responses preserve the rain forecast, and authorization, cache expiry,
  provider failures, and quota behavior do not change.

## Design and Implementation Sequence

1. **Specify behavior**
   - Record the Unicode-letter rule, strict threshold, response shape,
     provider probability source, and unchanged city catalogue contract in
     `spec.md`.
2. **Add focused tests**
   - Reuse the shared five-city fixture: Berlin, Madrid, Ljubljana, Malmö, and
     Gdańsk.
   - Verify rain is present for eligible names and absent for ineligible ones.
   - Verify rain dates align with weather dates, response values match provider
     data, and stale responses retain conditional rain data.
   - Cover accented, decomposed Unicode, punctuation, and exactly-seven-letter
     cases in eligibility tests.
3. **Model and map rain data**
   - Add a small eligibility helper and a rain-day record.
   - Remove precipitation probability from `DailyForecast`.
   - Retain provider probability and dates separately in the provider result.
   - Add nullable rain data to the forecast response, omitting it from JSON
     when null; select it using the city eligibility rule in the service.
4. **Update documentation**
   - Update the weather service contract and the prior city-forecast example
     so they describe the new response fields accurately.
   - Record work items in `tasks.md`.
5. **Verify**
   - Run focused weather tests, `./mvnw -pl weather verify`, then
     `./mvnw verify` under Java 25. Tests must use mocks and must not call live
     external APIs.

## Test Strategy

1. Unit-test Unicode eligibility, including accented precomposed and
   decomposed names, non-letter characters, and the strict threshold.
2. Parameterize provider request/data mapping, endpoint serialization, and
   forecast service behavior over the shared five-city fixture.
3. Assert that generic forecast entries never serialize
   `precipitationProbability`.
4. Assert eligible cities serialize exactly three rain entries with matching
   dates and percentages, while ineligible cities omit `rainForecast`.
5. Assert cached and stale responses preserve rain data and eligibility.
6. Run targeted weather verification and the complete Java 25 Maven reactor.

## Risks and Decisions

- The rule is based on the canonical catalogue city name, not the country,
  city ID, localized display name, or browser locale.
- Unicode letter counting is code-point based, not grapheme-cluster based.
  This is explicit and tested; combining marks do not add a letter count.
- Existing clients that read `precipitationProbability` from generic daily
  entries must migrate to the conditional `rainForecast` field. The removal is
  intentional and documented.
- The catalogue remains a public, unchanged endpoint; clients can infer
  eligibility from the canonical city name and this feature's rule.
