# Berlin 3-Day Weather Forecast

> Superseded: the Berlin-only route in this historical feature is replaced by
> the EU city-limits catalogue and `GET /weather/forecast/{cityId}` in
> [specs/003-major-european-cities/spec.md](../003-major-european-cities/spec.md).

## Summary

The weather service provides a REST endpoint for a three-day daily forecast for
Berlin, Germany, using the Open-Meteo DWD API. The forecast covers today and
the next two calendar days in the `Europe/Berlin` timezone.

Use Berlin city-center coordinates: latitude `52.52`, longitude `13.405`.
Use the Open-Meteo DWD API endpoint `/v1/dwd-icon` with daily fields
`temperature_2m_min`, `temperature_2m_max`, `weather_code`,
`precipitation_probability_max`, and `wind_speed_10m_max`, requesting
`temperature_unit=celsius`, `wind_speed_unit=ms`, and
`timezone=Europe/Berlin`.

## Endpoint

- Method and path: `GET /weather/forecast/berlin`
- Request body: none.
- The weather service checks caller permission before reading or returning
  forecast data. It forwards the original caller's `User-Agent` unchanged to
  the permissions service.
- A denied caller receives `403 Forbidden`; the weather service does not call
  the forecast provider.
- If the permissions service is unavailable or returns an unexpected response,
  fail closed with `503 Service Unavailable`. Do not call the forecast provider.

## Successful response

Return `200 OK` with a JSON object containing the city, timezone, source, time
the provider data was originally fetched, whether the returned data is stale,
and exactly three daily entries:

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

The values above illustrate the response shape. Dates are Berlin local calendar
dates. Temperatures are numeric Celsius values. `precipitationProbability` is a
percentage. Wind speed is in meters per second. `fetchedAt` is the original
provider retrieval time in UTC, including when a stale cached response is
served.

The numeric `weatherCode` is the provider's WMO weather interpretation code.
Map recognized codes using Open-Meteo's documented WMO descriptions to concise
English summaries. If a code is not recognized, set `summary` to `"Unknown"`
and preserve the numeric code in `weatherCode`.

## Authorization

- Call the permissions service before accessing or returning a cached forecast.
- Forward the original request's `User-Agent` to the permissions service.
- `200 OK` from permissions means continue.
- `403 Forbidden` means return `403 Forbidden` with RFC 7807 problem details.
- Any unexpected permissions response or connection failure means return
  `503 Service Unavailable` with RFC 7807 problem details.
- Do not call the forecast provider for denied or unauthorized-to-check
  requests.

## Caching and provider quota

- Cache the Berlin forecast for one hour.
- Requests using a fresh cached value do not call the provider.
- Coalesce simultaneous refreshes for the same forecast cache entry into one
  provider request.
- The weather service must not make more than 100 outbound forecast-provider
  HTTP requests in a UTC calendar day. Count each outbound HTTP request toward
  the cap.
- If an HTTP client retries a request or follows a redirect, count every
  outbound HTTP request separately against the UTC-day cap.
- The deployment is single-instance; an in-memory counter shared by requests
  in that instance is acceptable. Reset the counter at UTC midnight.
- If the daily quota is exhausted and stale forecast data is available, return
  it with `200 OK` and `"stale": true`.
- If a refresh fails for any reason and stale forecast data is available,
  return it with `200 OK` and `"stale": true`.
- If the quota is exhausted and no cached forecast is available, return
  `429 Too Many Requests` with RFC 7807 problem details.
- If the provider fails or returns unusable data and no cached forecast is
  available, return `502 Bad Gateway` with RFC 7807 problem details.

## Acceptance criteria

1. `GET /weather/forecast/berlin` returns a forecast for Berlin, Germany,
   covering today and the next two days in `Europe/Berlin`.
2. The response has city, country, timezone, source, `fetchedAt`, `stale`, and
   exactly three daily forecast entries.
3. Each daily entry has a Berlin-local date, numeric minimum and maximum
   temperatures in Celsius, human-readable weather summary, provider weather
   code, precipitation probability percentage, and wind speed in meters per
   second.
4. The original caller's `User-Agent` is forwarded unchanged to permissions.
   Permissions are checked before cached or provider forecast data is returned.
5. A permissions denial returns 403 problem details and does not call the
   provider. A permissions-service outage or unexpected response fails closed
   with 503 problem details and does not call the provider.
6. Forecast data is cached for one hour. Concurrent refreshes for the same
   forecast share a single provider request.
7. The service makes at most 100 outbound provider HTTP requests per UTC day
   in its single-instance deployment.
8. A provider quota exhaustion or refresh failure uses stale cached data when
   available and sets `"stale": true`; `fetchedAt` remains the original fetch
   time. Stale data may refer to a date window that has since passed.
9. With no cached data, quota exhaustion returns 429 problem details, while
   provider failure or unusable provider data returns 502 problem details.

## Clarifications resolved

- Berlin is represented by city-center coordinates `52.52, 13.405`.
- Weather summaries use Open-Meteo's documented WMO interpretation
  descriptions; unrecognized codes produce `"Unknown"` while retaining the
  numeric code.
- The initial deployment is single-instance, so an in-memory daily quota
  counter is acceptable. A future multi-instance deployment will require shared
  cache and quota state.
- The provider endpoint is `/v1/dwd-icon`, with the specified daily fields and
  units. Every outbound request, including retry and redirect requests, counts
  against the quota.