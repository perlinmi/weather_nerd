# Forecasts for Cities in the EU City-Limits Population List

## Summary

The weather service serves a three-day daily forecast for each city in a fixed
catalogue of cities ranked by city-proper/administrative population in the
source:

<https://en.wikipedia.org/wiki/List_of_cities_in_the_European_Union_by_population_within_city_limits>

The catalogue is a versioned snapshot selected at implementation time and is
not automatically updated. Include every city entry in the source snapshot
that has verifiable population, representative-coordinate, and timezone data
and provenance. Record the snapshot retrieval date and, for every city, its
population estimate, population as-of year, and cited source. The selected
source contained 95 city rows when retrieved on 2026-11-07.

## City inclusion and catalogue

- Rank cities by city-proper/administrative population, not by urban-area or
  metropolitan-area population.
- The catalogue is limited to the cities listed by the chosen European Union
  city-limits population source. Do not add non-EU cities from other lists.
- Identify each city with a stable lowercase ASCII `cityId` slug.
- If city-name slugs collide, disambiguate them with the country's ISO 3166-1
  alpha-2 code (for example, `city-name-fr`).
- Store a canonical city name, country, latitude, longitude, IANA timezone,
  population rank, population estimate, population as-of year, and source
  citations for the population, coordinates, and timezone.
- Use one representative city-centre coordinate per city; use the official
  city centre or administrative seat where available. Coordinates and timezone
  may come from separately cited reputable sources when not given by the
  population-list source.
- If a listed city lacks verifiable required data or provenance after checking
  reputable sources, omit that city from the pinned catalogue and document the
  omission.
- The source snapshot date and source citation are kept with the catalogue
  data. Do not fetch or recalculate the population ranking at runtime.

### City catalogue endpoint

- Method and path: `GET /weather/cities`
- Returns all 95 cities in the pinned catalogue in population rank order.
- No pagination or filtering is provided.
- Catalogue metadata is public and does not require a permissions check.

Each item contains `cityId`, `city`, `country`, `latitude`, `longitude`,
`timezone`, `rank`, `population`, and `populationAsOfYear`. Source citations
for population, coordinates, and timezone are retained in the bundled
catalogue data but are not returned by the API. The response also contains the
dataset source and `snapshotRetrievedAt` date.

## Forecast endpoint

- Method and path: `GET /weather/forecast/{cityId}`
- `cityId` must match one of the stable IDs in the catalogue. Unknown IDs
  return `404 Not Found` with RFC 7807 problem details.
- The Berlin-specific handler is removed. `GET /weather/forecast/berlin`
  remains valid because `berlin` is a catalogue `cityId` handled by the
  general city-ID route.
- The request has no body.
- The forecast covers today and the next two calendar days in that city's
  IANA timezone.
- Use the Open-Meteo DWD API `/v1/dwd-icon`. Pass the catalogue coordinates and
  timezone, and request daily fields
  `temperature_2m_min`, `temperature_2m_max`, `weather_code`,
  `precipitation_probability_max`, and `wind_speed_10m_max`, with
  `temperature_unit=celsius` and `wind_speed_unit=ms`.

## Successful forecast response

Return `200 OK` with a JSON object containing the city name, country, timezone,
source, original provider fetch time, stale flag, and exactly three daily
entries. Keep the existing forecast response fields; the response does not add
the catalogue `cityId` or population rank.

```json
{
  "city": "Berlin",
  "country": "Germany",
  "timezone": "Europe/Berlin",
  "source": "Open-Meteo DWD API",
  "fetchedAt": "2026-11-07T10:00:00Z",
  "stale": false,
  "forecast": [
    {
      "date": "2026-11-07",
      "minimumTemperatureCelsius": 4.2,
      "maximumTemperatureCelsius": 10.1,
      "summary": "Overcast",
      "weatherCode": 3,
      "precipitationProbability": 20,
      "windSpeedMetersPerSecond": 4.5
    },
    {
      "date": "2026-11-08",
      "minimumTemperatureCelsius": 3.8,
      "maximumTemperatureCelsius": 9.6,
      "summary": "Slight rain",
      "weatherCode": 61,
      "precipitationProbability": 60,
      "windSpeedMetersPerSecond": 5.1
    },
    {
      "date": "2026-11-09",
      "minimumTemperatureCelsius": 2.9,
      "maximumTemperatureCelsius": 8.7,
      "summary": "Partly cloudy",
      "weatherCode": 2,
      "precipitationProbability": 10,
      "windSpeedMetersPerSecond": 3.7
    }
  ]
}
```

Temperatures are numeric Celsius values. Precipitation probability is a
percentage. Wind speed is in meters per second. Dates use the requested city's
local calendar. `fetchedAt` is the original provider retrieval time in UTC,
including when stale data is returned.

Map recognized WMO weather codes using Open-Meteo's documented descriptions.
For unrecognized codes, return `"Unknown"` in `summary` and preserve the
numeric `weatherCode`.

## Authorization

- Every forecast request checks the permissions service before reading or
  returning cached forecast data, and before calling Open-Meteo.
- Forward the original caller's `User-Agent` unchanged to
  `POST /permissions/is_granted`.
- `200 OK` from permissions means continue.
- A permissions `403 Forbidden` is returned by weather as `403 Forbidden` with
  RFC 7807 problem details.
- A permissions-service connection failure or unexpected status fails closed
  with `503 Service Unavailable` and RFC 7807 problem details.
- A denied or unverified caller must not receive cached data, and no provider
  call is made.
- The public city catalogue does not use this permissions check.

## Cache and provider request quota

- Cache forecasts independently per city for one hour.
- A fresh cached forecast avoids provider calls.
- Coalesce simultaneous refreshes for the same city into one provider request;
  different cities have independent refreshes.
- The deployment is single-instance. One in-memory quota is shared across all
  cities and permits at most 500 outbound forecast-provider HTTP requests per
  UTC calendar day.
- The quota is a hard global cap with no per-city reservation or allocation.
  Requests for some cities can consume the available quota before other cities
  are requested; when exhausted, a city can receive stale data or a 429 if no
  stale data exists.
- Every outbound HTTP request counts, including failed attempts, retries, and
  redirects. Count each request against the UTC date on which it is dispatched.
- When quota is exhausted or refresh fails, return stale data if available,
  with `stale: true` and the original `fetchedAt`. Stale data may cover dates
  that have already passed.
- With no stale data, quota exhaustion returns `429 Too Many Requests` with
  RFC 7807 problem details. Provider failure or unusable provider data returns
  `502 Bad Gateway` with RFC 7807 problem details.
- In-memory cache and quota state are lost on service restart. Supporting more
  than one weather-service instance requires shared cache and quota state and
  is out of scope for this version.

## Acceptance criteria

1. `GET /weather/cities` returns all 95 city records from the pinned source
   snapshot in population rank order, with the identifying, location,
   timezone, and population
   metadata. Source citations remain available in the bundled catalogue data,
   not in the response.
2. The city dataset has a recorded source snapshot retrieval date; every
   population estimate has its as-of year and citation, and every coordinate
   and timezone has a citation. Listed cities lacking verifiable required data
   or provenance are omitted and documented. The dataset is not updated
   automatically at runtime.
3. City inclusion follows the selected EU city-limits population list and
   city-proper/administrative population ranking.
4. Each city has a stable lowercase ASCII `cityId`; collisions are
   disambiguated with country ISO alpha-2 codes.
5. `GET /weather/forecast/{cityId}` serves forecasts only for catalogue IDs;
   unknown IDs return 404 problem details. The Berlin-specific route is
   removed.
6. A valid city forecast has exactly three daily entries for today and the
   next two dates in the city's timezone, with the established fields and
   units.
7. Forecast requests forward the original User-Agent and check permissions
   before cached or provider data is accessed. Denials return 403 and
   permissions outages return 503; neither path calls the provider or returns
   cached data.
8. Forecasts are cached for one hour per city, with single-flight refresh per
   city.
9. A hard global single-instance quota with no per-city allocation limits all
   cities to at most 500 outbound provider requests per UTC day; failed
   attempts, retries, and redirects each count.
10. Exhaustion or refresh failure returns stale forecast data when available,
    with `stale: true` and its original `fetchedAt`, even if its dates have
    passed.
11. Without stale data, quota exhaustion returns 429 problem details and
    provider failure/unusable data returns 502 problem details.

## Implementation notes

- Include all verified cities from the pinned source snapshot as application
  data with their provenance;
  do not rely on a live Wikipedia request at application startup or at runtime.
- Add tests that exercise the entire catalogue for count, rank uniqueness/order,
  unique IDs, valid coordinates/timezones, population provenance, and forecast
  support.
- Reuse the existing forecast response shape, permissions behavior, WMO
  mapping, caching, stale fallback, and quota behavior where possible, making
  the cache key and provider location/timezone city-specific and the quota
  global.
