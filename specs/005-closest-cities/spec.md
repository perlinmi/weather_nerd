# Nearby city forecasts

## Summary

Add a Chrome-only weather endpoint that accepts a catalogue city ID and
returns the other catalogue cities within 200 km, ordered nearest first, with
their existing weather forecast when available.

## Clarifications

- The route is `GET /weather/nearby/{cityId}`. The path value is a catalogue
  city ID, not a display name. Match IDs case-insensitively and exactly.
- The origin city is excluded from the result.
- The weather service sends one permission check to
  `POST /permissions/is_granted` for the incoming request. It forwards the
  original `User-Agent` header and sends `{"endpoint":"nearby"}` as the JSON
  body. Authorization covers the nearby response's internal forecast lookups;
  those lookups do not trigger separate `forecast` permission checks.
- Nearby cities are selected by great-circle distance between catalogue
  coordinates, calculated with the haversine formula and a mean Earth radius
  of 6,371.0088 km. A city exactly 200 km away is included. The returned
  `distanceKm` is rounded to one decimal place.
- Results are ordered by unrounded distance, nearest first; ties are ordered by
  `cityId`.
- If no other city is within range, return an empty `nearbyCities` array.
- A city remains in the list if its forecast cannot be retrieved and no stale
  forecast is available because of a provider failure or exhausted provider
  quota. In that case, omit its `forecast` property. Permission-service
  failures and unexpected application errors fail the request.
- Access is decided by the permissions service using the original caller's
  `User-Agent` and the `nearby` endpoint key. Missing, malformed, non-Chrome,
  or otherwise denied callers receive `403`.

## Rules

- Resolve the origin ID against the pinned city catalogue. An unknown ID
  returns `404 Not Found` with RFC 7807 problem details.
- Before returning nearby city data or reading or requesting forecasts, call
  `POST /permissions/is_granted`, forwarding the original caller's
  `User-Agent` unchanged and passing `{"endpoint":"nearby"}` as JSON. Do not
  duplicate browser classification in the weather service.
- The permissions service grants the `nearby` endpoint only to callers
  classified as `CHROME`. Safari, other browsers, missing or malformed
  `User-Agent` values, and unknown or missing endpoint keys are denied.
- Include every other catalogue city whose haversine distance from the origin,
  using a mean Earth radius of 6,371.0088 km, is at most 200 km.
- For each nearby city, return its `cityId`, canonical `city` and `country`,
  its distance as `distanceKm`, and (when available) the existing city
  forecast response in a `forecast` property.
- Use the existing per-city forecast behavior, including cache, stale data,
  provider quota, and conditional rain forecast eligibility. A stale forecast
  is available and should be included.
- If an individual nearby forecast ends in the existing forecast-unavailable
  or quota-exhausted error and no stale forecast is available, omit only that
  city's `forecast` property and continue returning the other nearby cities.
  Keep the city and distance in the result. Do not suppress permission-service
  failures or unexpected application errors.
- Permission denial returns `403 Forbidden` with RFC 7807 problem details.
  If the permissions service cannot verify the caller, return
  `503 Service Unavailable` with RFC 7807 problem details.
- Do not change the behavior or authorization policy of
  `GET /weather/forecast/{cityId}`.

## Response shape

```json
{
  "originCityId": "berlin",
  "nearbyCities": [
    {
      "cityId": "example-city",
      "city": "Example City",
      "country": "Example Country",
      "distanceKm": 123.4,
      "forecast": {
        "city": "Example City",
        "country": "Example Country",
        "timezone": "Europe/Example",
        "source": "Open-Meteo DWD API",
        "fetchedAt": "2026-11-08T11:00:00Z",
        "stale": false,
        "forecast": []
      }
    },
    {
      "cityId": "another-city",
      "city": "Another City",
      "country": "Another Country",
      "distanceKm": 180.0
    }
  ]
}
```

The second item illustrates an unavailable forecast: the city remains listed,
but the `forecast` property is omitted. Forecast fields follow the existing
`GET /weather/forecast/{cityId}` response, including `rainForecast` only when
that city's canonical name meets the existing eligibility rule.

## Acceptance criteria

1. `GET /weather/nearby/{cityId}` returns the origin ID and all other catalogue
   cities within 200 km, with distances and forecasts when available.
2. The origin city is never included in its own nearby-city list.
3. Distances use catalogue coordinates and great-circle distance; the 200 km
   boundary is inclusive. Returned distances are in kilometers rounded to one
   decimal place.
4. Results are ordered by ascending unrounded distance, with `cityId` as a
   deterministic tie-breaker. No matches produce an empty array.
5. IDs match case-insensitively and exactly; unknown IDs return 404.
6. The weather service calls `POST /permissions/is_granted` once for the
   incoming nearby request, forwards the original `User-Agent`, and sends
   `{"endpoint":"nearby"}` in the JSON body before accessing cached or provider
   forecast data.
7. Only callers classified as Chrome by the permissions service may access
   the endpoint. Safari, other browsers, missing or malformed User-Agent
   values, unknown or missing endpoint keys, and denied callers receive 403.
8. The permissions service outage returns 503; it is not treated as an
   authorization denial.
9. Forecasts use existing forecast behavior, including cache, stale data,
   provider quota, and conditional rain forecast handling.
10. A forecast-unavailable or quota-exhausted error with no stale value does
    not fail the whole nearby response: its city and distance remain, its
    `forecast` property is absent, and other cities with available forecasts
    are returned. Permission-service failures and unexpected application
    errors fail the request.
11. The existing city forecast endpoint sends `{"endpoint":"forecast"}` to
    the same permissions endpoint and retains its current Safari+Chrome
    permissions policy.