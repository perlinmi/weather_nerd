# AGENTS.md

## Project
Monorepo with two Spring Boot services:
- `weather/`: fetches data from public Open-Meteo API and stores it.
- `permissions/`: internal permission servis who can all weather service
Specs live in `specs/` (Spec Kit). The contract between the services is `specs/contracts/`.

## Stack
- Java 25 only, Spring Boot 4.x, Maven (parent `pom.xml` with both modules)
- Tests: JUnit 5; prioritize HTTP end-to-end tests with WireMock stubs for
  outbound services. See `docs/testing.md`.
- Services talk to each other via RESTful API

## Commands
- Build and test everything: `./mvnw verify`
- weather service: `./mvnw -pl weather verify`
- permissions service: `./mvnw -pl permissions verify`
- Run locally: `docker compose up -d` then `./mvnw -pl service-a spring-boot:run`

## Workflow (spec-driven)
- Update the spec before the code. If code and spec disagree, fix the spec first.
- Work from `specs/<feature>/tasks.md`, one task at a time, one commit per task.
- Before writing tests, read `docs/testing.md`. Derive tests from the acceptance criteria in `spec.md`.

## Code conventions
- Packages: `com.weathernerd.<service>.{api,service,repository,domain,config,exception}`.
- Remove package directories when their last source file is removed; do not
  leave empty packages.
- Put all application-defined exception classes in the service's dedicated
  `exception` package, never alongside controllers, clients, or services.
- Controllers stay thin and contain no business logic
- Constructor injection only
- Errors: `@RestControllerAdvice` returning RFC 7807 problem details.
- External HTTP calls use `RestClient` with timeouts, and always send a User-Agent.
- External service URLs, including endpoint paths, must be configured in
  `application.yml` and overridden through environment variables when needed;
  never hardcode them in Java.
- Other runtime settings use `application.yml` and environment variables,
  never hardcoded.

## Rules
- Never commit secrets. Keep them in `.env` (git-ignored) and document them in `.env.example`.
- Don't add dependencies without asking.
- Do not call real external APIs in tests.