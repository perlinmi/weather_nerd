# Spring Boot 4 migration

## Summary

Migrate the permissions and weather services from Spring Boot 3.5 to the
latest Spring Boot 4.0 maintenance release, preserving Java 25 as the sole
supported runtime and keeping the existing HTTP APIs and service behavior
unchanged.

## Rules

- Both Maven service modules use the same Spring Boot 4.0 parent and managed
  dependency versions.
- Keep the existing Spring MVC/Tomcat application architecture. Use Boot 4's
  dedicated MVC and blocking REST-client starters where required by each
  module.
- Use Spring Boot 4's default Jackson 3 integration. Migrate application and
  test code from Jackson 2 core/databind APIs without changing JSON contracts.
  Keep `com.fasterxml.jackson.annotation` imports where required by Jackson's
  compatible annotations artifact.
- Keep the existing JUnit, AssertJ, Spring HTTP end-to-end testing strategy
  and WireMock external-service stubs. Use Boot 4's matching test starters.
- Preserve the current Java 25 toolchain, container build, endpoints, error
  responses, configuration, permissions policies, and external HTTP behavior.
- Do not add a Jackson 2 compatibility layer or unrelated dependencies.

## Acceptance criteria

1. Both service modules build and run on Spring Boot 4 with Java 25.
2. The public API request and response contracts remain unchanged.
3. Spring Boot-managed JSON serialization/deserialization uses Jackson 3 and
   preserves existing response field names, optional fields, and date formats.
4. Both service end-to-end test suites pass with all external calls stubbed.
5. The full Maven reactor `verify` passes using the Java 25 container build.
6. Docker images continue to build and use a Servlet 6.1-compatible embedded
   server/runtime.
7. Project instructions and service documentation no longer describe Spring
   Boot 3 as the current stack.
