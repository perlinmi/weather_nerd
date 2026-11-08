# Implementation Plan: Permissions Based on Browser Header

## Summary

Implement the internal permissions endpoint described in `spec.md`, together
with focused tests and a service contract. The weather-service caller is
deferred because the weather module has no request flow defined yet. Access is
granted to Safari and Google Chrome and denied to all other, missing, or
malformed user-agent values.

## Technical Context

- **Repository state:** The `permissions/` and `weather/` modules currently
  contain no application code or Maven configuration. No Maven wrapper or
  parent `pom.xml` is present.
- **Target stack:** Java 25, Spring Boot 3.5.7, Maven, JUnit 5, and Mockito.
- **Dependencies:** Do not add a browser-parsing library or other dependencies
  without approval. Use a small, isolated classifier with documented,
  deterministic matching rules unless an existing dependency is established
  when the Maven structure is created.
- **API:** `POST /permissions/is_granted`, using the forwarded `User-Agent`
  request header and a JSON body containing the weather endpoint key. Return
  `200 OK` with no body for allowed endpoint/browser combinations; return
  `403 Forbidden` with `{"error":"Permission denied"}` otherwise.
- **Errors:** Keep expected access denial distinct from unexpected application
  errors. Return the specified denial body for this expected authorization
  result; use RFC 7807 problem details for unexpected API errors, consistent
  with the project convention.
- **Trust boundary:** `User-Agent` is client-controlled and spoofable. This
  feature classifies browser strings; it does not authenticate callers or
  establish a secure identity.

## Design

### Permissions service

1. Define a browser-role enum or equivalent domain type with `SAFARI`,
   `CHROME`, and `OTHER`.
2. Implement a focused classifier that treats missing/blank/malformed headers
   and unrecognized browsers as `OTHER`. Detect known non-Chrome browsers
   before Chrome markers, then classify Google Chrome; classify Safari only
   when it is identified without a competing browser identifier. Ignore
   version numbers.
3. Implement an authorization policy that grants `SAFARI` and `CHROME` and
   denies `OTHER`.
4. Add a thin controller at `POST /permissions/is_granted`. It delegates
   classification and policy evaluation to the service layer and maps results
   to the exact status/body contract.
5. Add service-level tests for classifier and authorization behavior, plus
   controller tests for status codes, empty success body, and the denial JSON
   body. Include overlapping tokens, common alternate-browser identifiers,
   missing values, and malformed values.

### Service contract

Document the endpoint under `specs/contracts/` as part of this feature even
though the weather-service caller is deferred. Keep method, path, headers, body
behavior, response statuses, and denial body aligned with `spec.md`.

## Implementation Sequence

1. Establish the parent Maven build, both module builds, and Maven wrapper using
   the declared Java/Spring stack; verify an empty build for both modules.
2. Add the contract document and implement the permissions classifier,
   authorization policy, endpoint, and focused tests.
3. Run focused permissions tests, then `./mvnw verify`; review the final contract,
   configuration, and error responses for consistency.

## Project Structure

The initial implementation should establish the parent Maven build, Maven
wrapper, and the two Spring Boot modules (`permissions/` and `weather/`)
following the declared project conventions. Keep service code in the existing
intended package structure:

- `permissions/src/main/java/.../{api,service,domain,config}`
- `permissions/src/test/java/...`
- `specs/contracts/` for the cross-service API contract

Use the repository's existing conventions and package naming when those
manifests and source roots are introduced; do not add unrelated service
features.

## Validation Plan

1. Verify the parent build compiles both modules with Java 25.
2. Run focused classifier and policy unit tests, including:
   - Google Chrome identifiers, across versions.
   - Safari identifiers, across versions.
   - Chrome strings containing a Safari compatibility token.
   - Known alternatives such as Edge and Opera, even when they contain Chrome
     compatibility tokens.
   - Missing, blank, malformed, and unrecognized values.
3. Run permissions controller tests to verify exact HTTP statuses and response
   bodies.
4. Run `./mvnw verify` after the modules and wrapper are available.

## Risks and Open Questions

- The spec leaves exact rules for less common or ambiguous user-agent strings
  open. Start with explicit, tested identifiers for common browsers and default
  all unmatched values to `OTHER`; refine the supported identifiers as needed.
- The project-wide RFC 7807 convention and the spec's concrete denial body are
  both preserved by treating a 403 as an expected authorization result and
  reserving RFC 7807 for unexpected API errors. Revisit this only if the
  project requires all 4xx responses to use problem details.
- A User-Agent can be forged. Do not rely on this check as authentication or
  protection against a malicious caller.
- Weather-service integration is deferred until the weather request flow and
  its denial behavior are specified.
- The repository has no build files yet. Establishing the Maven project and
  deciding its baseline Spring Boot 3.x version are prerequisites to execution.
- Java 25 is the only supported runtime and compiler target. The host shell
  lacks a local JDK 25 installation; Java 25 builds and tests can run in the
  Maven/Temurin 25 container.
