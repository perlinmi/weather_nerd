# Tasks: Spring Boot 4 migration

## Specification and test baseline

- [x] T001 Record the migration scope and unchanged behavior.
  - Keep Java 25, both services, public contracts, and the existing testing
    approach.
  - Use Spring Boot 4's modular MVC, REST-client, and test starters and default
    Jackson 3 support.
  - References: `spec.md` Rules and Acceptance criteria 1–7.

## Migration

- [x] T002 Upgrade the shared Maven parent to Spring Boot 4.0.9.
  - Replace legacy web/test starters with Boot 4's dedicated starters based
    on each module's actual framework usage.
  - Preserve the existing WireMock version unless compatibility verification
    requires a justified adjustment.
  - References: `spec.md` Rules 1–2 and Acceptance criteria 1, 6.

- [x] T003 Migrate application and test code to Jackson 3.
  - Update mapper, tree, and exception imports/API usage; retain compatible
    annotation imports from `com.fasterxml.jackson.annotation`.
  - Preserve catalogue loading and HTTP response serialization behavior.
  - References: `spec.md` Rules 3 and Acceptance criteria 2–3.

- [x] T004 Validate both services and the complete deployment build.
  - Run module verification and full Java 25 Maven reactor verification.
  - Build both Docker images or otherwise verify the image build path and
    embedded Servlet container compatibility.
  - Run `git diff --check`.
  - References: `spec.md` Acceptance criteria 1, 4–7.

## Documentation

- [x] T005 Update stack documentation to describe Spring Boot 4.
  - Update `AGENTS.md` only where it states the current Spring Boot version.
  - Keep runtime and test instructions consistent with the migrated build.
  - References: `spec.md` Acceptance criterion 7.
