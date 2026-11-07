# Weather Insights: Overview

## Purpose
Give people a simple, reliable answer to "what will the weather be like?" for the cities they care about.

## Users
- **Everyday user:** checks a few favourite cities via a client app or script.
- **Developer (the owner):** uses the REST API to learn and experiment.

## Scope
**In scope**
- Fetch forecasts for configured cities from a public weather provider: https://open-meteo.com/en/docs/dwd-api
- Serve three-day forecasts for the 95 cities in a fixed, versioned
  EU city-proper population catalogue snapshot.
- Check permissions of the caller before calling public weather data

**Out of scope**
- Maps and radar images.
- Locations outside the fixed catalogue.

## System at a glance
| Service | Responsibility |
|---|---|
| `weather` | API for retrieving forcasts |
| `permissions` | checking permissions |

## Glossary
| Term | Meaning |
|---|---|


## Key business rules
- Retrieve forecasts from Open-Meteo and cache them for up to one hour, serving
  stale cached data when refreshes fail or the global daily provider-call quota
  is exhausted. Maintain separate caches per city and coalesce concurrent
  refreshes for the same city.
- Include all cities with verifiable data in the selected EU city-limits
  population-list snapshot. Use one representative city-centre coordinate and
  cite population, coordinate, and timezone sources.
- Check caller permissions before returning forecast data.
- Limit all cities together to 500 outbound provider HTTP requests per UTC day.
  This is a hard cap without per-city reservations; quota exhaustion can result
  in stale data or a 429 when no stale forecast is available.

## Constraints
- The provider's free usage limits must never be exceeded (budget: 500 calls per day).
- Data must always show its source and the time it was fetched.

## Success criteria
- A client gets a city's forecast in under 1 second from stored data.

## Related specs
- `specs/001-permissions-based-on-browser-header.md`
- `specs/002-get-temperature-for-berlin/spec.md`
- `specs/003-major-european-cities/spec.md`
