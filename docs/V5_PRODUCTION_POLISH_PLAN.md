# V5 — Production Polish and Portability

**Status:** future

V5 is optional hardening after the local application and AI workflow are proven useful.

Potential work includes PostgreSQL, Docker packaging, backup/restore, encryption design, authentication for a multi-user deployment, exports, CI/CD, observability, and deployment guidance. Each item should become a focused Issue only when the actual use case justifies it.

V5 must preserve the project’s local-first default and must not turn cloud infrastructure into a prerequisite for using the tracker.

## Intended implementation direction

V5 is a portability and safety layer around the existing Angular → Spring Boot → database boundaries, not a rewrite of the finance domain. SQLite remains the default single-user store; PostgreSQL becomes an optional deployment profile. Docker packaging would run the backend and frontend with explicit mounted data/statement directories, while backup/export routines operate on those local artifacts. Authentication and encryption are deferred because they introduce key management and recovery responsibilities that are unnecessary for the current single-user local application.

See [System Architecture](ARCHITECTURE.md) and [design decisions](DESIGN_DECISIONS.md).
