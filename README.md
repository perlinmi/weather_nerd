# Weather Nerd

Weather Nerd is a Java 25 / Spring Boot application split into two services:

- **Weather service** exposes the customer-facing API, serves a pinned catalogue
  of 95 EU cities, and retrieves and caches forecasts from Open-Meteo.
- **Permissions service** is an internal service that checks whether the
  caller's browser may access the requested weather endpoint.

Forecasts cover today and the next two calendar days in the city's timezone.
Temperatures are in Celsius and wind speeds are in meters per second. Forecasts
are cached per city for one hour. If a provider refresh fails, the service
returns stale data when available. Outbound provider requests share an
in-memory limit of 500 requests per UTC day per running instance.

## Customer API

The customer-facing API is provided by the weather service. When running with
Docker Compose, it is available at `http://localhost:8088` by default. Set
`WEATHER_HOST_PORT` to change the host port.

### List supported cities

```http
GET /weather/cities
```

This endpoint is public and does not require a `User-Agent` or permission
check. It returns the pinned city catalogue in rank order; the catalogue is
packaged with the service and is not refreshed at runtime.

```bash
curl http://localhost:8088/weather/cities
```

The response includes the catalogue source and snapshot date, along with city
IDs, names, countries, coordinates, timezones, ranks, and population data.
Use a returned `cityId` in the forecast endpoints.

### Get a city forecast

```http
GET /weather/forecast/{cityId}
```

Requires a supported browser according to the permission model below. For
example:

```bash
curl -H 'User-Agent: Mozilla/5.0 Chrome/125.0.0.0 Safari/537.36' \
  http://localhost:8088/weather/forecast/berlin
```

An abbreviated response:

```json
{
  "city": "Berlin",
  "country": "Germany",
  "timezone": "Europe/Berlin",
  "source": "Open-Meteo DWD API",
  "fetchedAt": "2026-11-08T12:00:00Z",
  "stale": false,
  "forecast": [
    {
      "date": "2026-11-08",
      "minimumTemperatureCelsius": 4.2,
      "maximumTemperatureCelsius": 10.1,
      "summary": "Cloudy",
      "weatherCode": 3,
      "windSpeedMetersPerSecond": 5.4
    }
  ],
  "rainForecast": [
    {
      "date": "2026-11-08",
      "probability": 20
    }
  ]
}
```

`forecast` contains three daily entries. `rainForecast` is returned only for
city names with fewer than seven Unicode letters; it is omitted for other
cities.

### Get nearby city forecasts

```http
GET /weather/nearby/{cityId}
```

This endpoint is restricted to Chrome callers. It returns other catalogue
cities within an inclusive 200 km radius, nearest first, with their distance
and forecast when available. The origin city is not included.

```bash
curl -H 'User-Agent: Mozilla/5.0 Chrome/125.0.0.0 Safari/537.36' \
  http://localhost:8088/weather/nearby/berlin
```

An abbreviated response:

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
        "timezone": "Europe/Berlin",
        "source": "Open-Meteo DWD API",
        "fetchedAt": "2026-11-08T12:00:00Z",
        "stale": false,
        "forecast": []
      }
    }
  ]
}
```

The example city and forecast values above are illustrative. If a nearby
forecast cannot be retrieved and no stale value is available, that city remains
in `nearbyCities` but its `forecast` property is omitted. If there are no
nearby matches, the array is empty.

### Errors

Weather API errors use RFC 7807 problem details. Common statuses include:

| Status | Meaning |
| --- | --- |
| `403 Forbidden` | Browser is not permitted to access the endpoint |
| `404 Not Found` | City ID is not in the catalogue |
| `429 Too Many Requests` | Daily provider request limit is exhausted and no stale forecast is available |
| `502 Bad Gateway` | Provider failed or returned an unusable response and no stale forecast is available |
| `503 Service Unavailable` | Permission could not be verified |

## Permission model

Weather requests that need permission forward the caller's original
`User-Agent` to the internal permissions service before accessing or fetching
forecasts. The check includes the requested endpoint name. The permissions
service classifies callers into three roles:

| Role | Classification |
| --- | --- |
| `CHROME` | User-Agent contains a versioned Chrome token, unless a recognized non-Chrome browser token takes precedence |
| `SAFARI` | User-Agent contains both versioned `Version` and `Safari` tokens |
| `OTHER` | Missing, blank, malformed, or any other browser User-Agent |

The endpoint policy is:

| Endpoint key | Chrome | Safari | Other |
| --- | :---: | :---: | :---: |
| `forecast` | Allow | Allow | Deny |
| `nearby` | Allow | Deny | Deny |
| Missing or unknown key | Deny | Deny | Deny |

Permissions are checked by the internal
`POST /permissions/is_granted` endpoint. It expects a JSON body containing an
`endpoint` key and the original caller `User-Agent` header. A grant returns
`200 OK`; a denial returns `403 Forbidden`. Customers should call the weather
API rather than the internal permissions endpoint.

## Architecture

```mermaid
flowchart LR
    Customer[Customer] -->|GET /weather/cities| Weather[Weather service]
    Customer -->|GET /weather/forecast/{cityId}<br/>GET /weather/nearby/{cityId}| Weather

    Weather -->|User-Agent + endpoint key| Permissions[Permissions service]
    Permissions --> Classifier[User-Agent role classifier]
    Classifier --> Policy[Endpoint permission policy]
    Policy -->|Grant or deny decision| Permissions
    Permissions -->|200 grant / 403 deny| Weather

    Weather --> Catalogue[Pinned city catalogue]
    Weather --> Cache[Per-city forecast cache<br/>1 hour]
    Cache -->|Cache miss / refresh| Quota[In-memory daily request quota]
    Quota -->|Allowed outbound request| OpenMeteo[Open-Meteo API]
    OpenMeteo -->|Forecast data| Weather
```

The permissions service is an internal service-to-service dependency and is
not published on the host by the supplied Docker Compose configuration.
Forecasts use the cache and provider quota; the city catalogue endpoint uses
the packaged snapshot.

## Setup, build, and run

### Prerequisites

- Docker with Docker Compose v2 (`docker compose`)
- Java 25 and the included Maven Wrapper (`./mvnw`) if building or testing
  outside Docker

No API key is required. The weather service calls the public Open-Meteo API
when it needs to refresh a forecast.

### Build

Build both service images from the repository root:

```bash
docker compose build
```

Alternatively, build and run the Maven verification suite locally with Java 25:

```bash
./mvnw clean verify
```

### Start with Docker Compose

```bash
docker compose up -d
```

To build the images and start both services in one command:

```bash
docker compose up --build -d
```

The weather service is available at `http://localhost:8088` by default. Change
the host port by setting `WEATHER_HOST_PORT`, for example:

```bash
WEATHER_HOST_PORT=8090 docker compose up --build -d
```

View service logs with:

```bash
docker compose logs -f
```

Stop and remove the containers with:

```bash
docker compose down
```

To rebuild images after code changes, run `docker compose up --build -d` again.
Run the test suite with Java 25 and Maven using:

```bash
./mvnw verify
```
