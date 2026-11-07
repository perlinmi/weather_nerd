# Weather Service Contract

## List supported cities

`GET /weather/cities`

Public endpoint; no request body and no permissions check. Returns the pinned
EU city-limits population catalogue in rank order. The snapshot retrieved on
2026-11-07 contains 95 cities.

```json
{
  "source": "https://en.wikipedia.org/wiki/List_of_cities_in_the_European_Union_by_population_within_city_limits",
  "snapshotRetrievedAt": "2026-11-07",
  "cities": [
    {
      "cityId": "berlin",
      "city": "Berlin",
      "country": "Germany",
      "latitude": 52.516666666667,
      "longitude": 13.383333333333,
      "timezone": "Europe/Berlin",
      "rank": 1,
      "population": 3913644,
      "populationAsOfYear": 2025
    }
  ]
}
```

The response contains the full pinned catalogue. The service does not fetch
or update catalogue information at runtime.

## Retrieve a city forecast

`GET /weather/forecast/{cityId}`

No request body. `cityId` must match an ID returned by `GET /weather/cities`.
Unknown IDs return `404 Not Found` with RFC 7807 problem details.

Every request for a known city forwards the original caller's `User-Agent` to
`POST /permissions/is_granted` before cached forecast data is read or provider
data is requested. Permission denial returns `403 Forbidden` with RFC 7807
problem details. A permissions-service outage or unexpected response returns
`503 Service Unavailable` with RFC 7807 problem details.

The forecast covers today and the next two calendar days in the city's IANA
timezone. Temperature is in Celsius and wind speed is in meters per second. A
response includes the city and country, timezone, source, original provider
retrieval time in UTC, stale flag, and three daily weather forecast entries.

For city names containing fewer than seven Unicode letters, the response also
includes `rainForecast`, a three-entry array with each date and the
precipitation probability in percent. Accented letters count as one letter;
spaces, punctuation, and digits do not count. Names with seven or more letters
omit `rainForecast`. The generic weather entries do not contain a
`precipitationProbability` field. Stale data preserves its original
`fetchedAt` and any eligible city's rain forecast.

The dedicated Berlin-only handler has been removed. Berlin remains available
because `berlin` is a city ID handled by this general route.

### Provider cache and request limit

Forecasts are cached for one hour per city; simultaneous refreshes for the same
city share one provider request. A single-instance, in-memory global quota
allows at most 500 outbound forecast-provider HTTP requests per UTC day,
without per-city reservations. Each retry, redirect, and failed outbound
attempt counts. If refresh fails or the quota is exhausted, stale data is
returned when available; without stale data, quota exhaustion returns
`429 Too Many Requests` and provider failure returns `502 Bad Gateway`, both
with RFC 7807 problem details.
