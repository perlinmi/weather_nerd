# Weather Insights: Overview

## Purpose
Give people a simple, reliable answer to "what will the weather be like?" for the cities they care about.

## Users
- **Everyday user:** checks a few favourite cities via a client app or script.
- **Developer (the owner):** uses the REST API to learn and experiment.

## Scope
**In scope**
- Fetch forecasts for configured cities from a public weather provider: https://open-meteo.com/en/docs/dwd-api
- Check permissions of the caller before calling public weather data

**Out of scope**
- Maps and radar images.
- Locations outside the configured city list.

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
  stale cached data when refreshes fail or the daily provider-call quota is
  exhausted.
- Check caller permissions before returning forecast data.

## Constraints
- The provider's free usage limits must never be exceeded (budget: 100 calls per day).
- Data must always show its source and the time it was fetched.

## Success criteria
- A client gets a city's forecast in under 1 second from stored data.

## Related specs
- `specs/001-permissions-based-on-browser-header.md`
- `specs/002-get-temperature-for-berlin/spec.md`
