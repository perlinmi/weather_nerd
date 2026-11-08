# Tasks: Permissions Based on Browser Header

Tasks are ordered and intended to be completed one at a time, with one commit
per task. Write the tests before the implementation tasks they cover.

## Project foundation

- [x] T001 Confirm and establish the Maven project foundation.
  - Approved dependencies: Spring Boot 3.x web starter in both modules and
    Spring Boot starter-test (JUnit 5, Mockito, MockMvc) for tests; no browser
    parser or database dependency.
  - Establish a Java 25 Maven parent build, `permissions` and `weather` modules,
    and Maven wrapper using the approved dependencies.
  - Use `com.weathernerd` as the valid shared Java package root.
  - Verify both modules build.
  - References: plan Technical Context, Implementation Sequence 1.

- [x] T002 Document the permissions service contract.
  - Add the contract under `specs/contracts/`.
  - Specify `POST /permissions/is_granted`, forwarded `User-Agent`, an endpoint
    key in the JSON request body, 200 with an empty body for a grant, and 403 with
    `{"error":"Permission denied"}` for a denial.
  - Keep this contract aligned with `spec.md`.
  - References: spec Endpoint contract; plan Service contract.

## Permissions service

- [x] T003 Add browser-classification tests.
  - Cover Safari and Google Chrome across versions, Chrome's Safari
    compatibility token, Edge and Opera with Chrome compatibility tokens,
    missing/blank/malformed values, and unrecognized browsers.
  - Assert each input maps to exactly one role and alternate browsers map to
    `OTHER`.
  - Tests should not call external services.
  - References: spec Browser classification, acceptance criteria 3–6; plan
    Permissions service 1–2 and Validation Plan 2.

- [x] T004 Implement browser-role classification.
  - Add the `SAFARI`, `CHROME`, and `OTHER` role type and a focused classifier.
  - Identify known alternate browsers before Chrome compatibility tokens;
    classify Google Chrome and Safari as specified; default unknown, missing,
    or malformed input to `OTHER`.
  - Ignore version ranges and satisfy T003.
  - References: spec Browser classification; plan Permissions service 1–2.

- [x] T005 Add authorization-policy tests.
  - Verify `SAFARI` and `CHROME` are granted and `OTHER` is denied.
  - References: spec Authorization policy, acceptance criterion 7; plan
    Permissions service 3.

- [x] T006 Implement the authorization policy and service.
  - Add the service-layer decision using the browser-role classification.
  - Grant `SAFARI` and `CHROME`; deny `OTHER`; satisfy T005.
  - References: spec Authorization policy; plan Permissions service 3.

- [x] T007 Add endpoint contract tests.
  - Exercise `POST /permissions/is_granted` with representative granted and
    denied User-Agent headers.
  - Assert 200 with an empty body for Safari/Chrome and 403 with the exact JSON
    body for Other, including missing/malformed headers.
  - Verify the endpoint key is provided in the JSON body and all roles share
    the same route.
  - References: spec Endpoint contract and acceptance criteria 1, 6–8; plan
    Permissions service 4–5 and Validation Plan 3.

- [x] T008 Implement the permissions endpoint.
  - Add the thin controller at `POST /permissions/is_granted`; read the
    User-Agent header and delegate to the service.
  - Return exactly the contract response for grant and denial.
  - Keep expected 403 denial distinct from unexpected errors; retain the
    project's RFC 7807 handling for unexpected API errors.
  - Satisfy T007.
  - References: spec Endpoint contract and Authorization policy; plan
    Permissions service 4 and Errors.

## Weather-service integration

## Final verification

- [x] T009 Run focused and full verification.
  - Run focused permissions tests, then `./mvnw verify`.
  - Confirm test coverage for classifier precedence, missing/malformed input,
    exact endpoint response bodies, and denial.
  - Review the service contract, configuration, and error responses for
    consistency with `spec.md`. The weather-service caller is deferred until a
    weather request flow is specified.
  - References: plan Validation Plan and Implementation Sequence 4.
