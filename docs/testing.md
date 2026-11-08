# Testing strategy

## Principles

- **Prefer endpoint-level end-to-end tests.** Start the real Spring Boot
  application on a random port, send HTTP requests to its public API, and
  exercise the application path through routing, validation, authorization,
  services, serialization, and outbound clients.
- **Stub only dependencies outside the application under test.** Use WireMock
  HTTP servers for Open-Meteo and, when testing the weather service, the
  permissions service. Configure their URLs through test properties. Never
  call public or shared external services from tests.
- **Do not mock the application path under test.** Endpoint tests must use the
  real controllers, services, clients, and configuration. Do not use
  `@WebMvcTest` or mocked services to test controller behavior in isolation.
- **Test only behavior owned by this application.** Do not assert or duplicate
  behavior owned by an external provider or library, such as a provider's
  weather-code-to-description mapping. Verify the application's observable
  contract and its handling of the provider response instead.
- **Keep unit tests for nontrivial application-owned domain rules.** For
  example, test rain-forecast eligibility, including the Unicode-letter
  threshold. Do not add tests for simple data holders, getters, delegation,
  framework wiring, or trivial mappings.
- **Do not write quota-specific tests.** Request quota implementation details
  are not a test target. Test endpoint behavior without asserting internal
  quota counters, reservation races, or limits.
- **Optimize for readability.** Name tests after externally visible
  behavior; keep setup and assertions local to each scenario; use small,
  intention-revealing helpers for repeated HTTP stubs and fixtures. Avoid
  tests that assert implementation details or large, opaque shared fixtures.
  Parameterize only when each case has the same behavior and the resulting
  test remains easy to understand.

## End-to-end test shape

Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
and `TestRestTemplate` (or another real HTTP client) to call the application.
Start WireMock on dynamic ports for outbound HTTP dependencies, configure the
application with those complete endpoint URLs using `@DynamicPropertySource`,
and stop the servers after the test class. Reset stub mappings and request
journals between tests so cases remain isolated.

Weather-service endpoint tests should, as appropriate:

- call `GET /weather/cities` and `GET /weather/forecast/{cityId}` over HTTP;
- stub the permissions endpoint and weather provider, then verify permission
  decisions, forwarding of the caller's `User-Agent`, provider request inputs,
  and the final response shape;
- exercise meaningful success, denial, unknown-city, and dependency-failure
  behavior without asserting internal calls except where an external boundary
  contract requires it.

Permissions-service endpoint tests should call the real permissions API over
HTTP with representative Safari, Chrome, other, and missing user-agent cases.
Use focused domain tests for the owned browser-classification rule when they
add clarity beyond the endpoint scenarios.

## What not to test

- Controller-only behavior with mocked services.
- External provider/library business logic or mappings.
- Request quota counters, reservation mechanics, concurrency, or reset logic.
- Trivial DTO serialization tests, context-startup tests, and tests that only
  verify Spring can inject configured components.
- Private implementation details that are not part of an API contract or
  meaningful owned domain rule.

## Running tests

Run the relevant module while iterating, then the full reactor before
completion. Use Java 25 only:

```sh
./mvnw -pl weather verify
./mvnw -pl permissions verify
./mvnw verify
```

Keep external HTTP fully stubbed in all test runs. A test must not silently
fall back to a real provider or service if a stub is missing.