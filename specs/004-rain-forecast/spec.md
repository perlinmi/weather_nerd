# Rain forecast for short city names

## Summary

Extend `GET /weather/forecast/{cityId}` with a separate daily rain-probability
forecast for cities whose names contain fewer than seven Unicode letters.
Reuse Open-Meteo's daily precipitation probability for the same three dates as
the weather forecast. The rain-specific data is not included for city names
with seven or more letters.

## Clarifications

- Rain data is returned only by the forecast endpoint. `GET /weather/cities`
  remains unchanged; clients can infer eligibility from each canonical city
  name using the letter-count rule below.
- The rain probability uses Open-Meteo's daily
  `precipitation_probability_max` value.

## Rules

- Count Unicode code points classified as letters in the catalogue's canonical
  city name. Accented letters count as one; punctuation, spaces, and digits do
  not count.
- Include rain forecast data only when the letter count is strictly less than
  seven.
- For eligible cities, include `rainForecast` as an array of three entries,
  each with `date` and `probability` (integer percent), matching the weather
  forecast's dates and Open-Meteo daily `precipitation_probability_max`.
- For ineligible cities, omit `rainForecast` from the JSON response.
- Remove `precipitationProbability` from the generic `forecast` entries. Rain
  probability is represented only by the conditional `rainForecast` field.
- Authorization, caching, stale behavior, forecast dates, and provider quota
  remain shared with the existing city forecast request. Stale responses
  preserve rain forecast data when present.

## Acceptance criteria

1. A known city with fewer than seven Unicode letters returns the usual
   three-day weather forecast and a three-entry `rainForecast` array.
2. Each rain entry has the same date as its corresponding weather entry and
   the probability returned by the provider, expressed as an integer percent.
3. A city with seven or more Unicode letters has no `rainForecast` property
   in the response.
4. The generic weather forecast entries do not expose
   `precipitationProbability` for any city.
5. Accented letters count as letters, and names at exactly seven letters are
   ineligible.
6. Cached and stale responses retain the same eligibility and rain data as the
   freshly retrieved response.
7. Tests cover Berlin, Madrid, Ljubljana, Malmö, and Gdańsk without duplicating
   city fixture data.