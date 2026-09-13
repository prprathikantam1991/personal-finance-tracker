# Agent Guide — Personal Finance Tracker

## Project shape

- `backend/` is the Java 21 / Spring Boot / SQLite service.
- `frontend/` is the Angular 22 application.
- `docs/` holds human-facing architecture, decisions, flows, and specifications.
- `infrastructure/` and `scripts/` are intentionally documentation-only until their work is planned.

## Core principles

1. Keep financial data local-first. Never commit SQLite databases, statement PDFs, import folders, or API keys.
2. Preserve the statement import pipeline: raw statement → parser → normalized transaction → duplicate/transfer checks → category/review → persistence.
3. Treat confirmed finance data and saved statement snapshots as the source of truth for summaries and AI answers.
4. AI assistants may use only allow-listed, read-only finance tools in V3. Never let a model execute arbitrary SQL, shell commands, file operations, or write actions.
5. Update the relevant README and `docs/` document when a feature changes architecture, setup, data semantics, or user-visible behavior.

## Verification

- Backend: run `mvn test` from `backend/`.
- Frontend: run `npm run build` from `frontend/`.
- For local Assistant changes, test an end-to-end question through `POST /api/assistant/chat` while LM Studio is running.

## Documentation

- Start with `README.md` for setup and project overview.
- Use `docs/ARCHITECTURE.md` for system boundaries.
- Use `docs/REQUEST_FLOWS.md` for runtime behavior.
- Use `docs/DESIGN_DECISIONS.md` to record meaningful technical decisions.
- Use `docs/AI_ASSISTANT_ARCHITECTURE.md` for the detailed V3 assistant design.

## Delivery tracking

- GitHub Project #4 is the source of truth for delivery status. Keep it minimal: Version = iteration, Issue = meaningful unit of work, Status = Planned / In Progress / Done.
- Before meaningful implementation, use or create a focused GitHub Issue linked to the relevant version issue. Complete work only after verification, required documentation updates, an Issue reference in the commit or PR where practical, and closing the Issue.
- Do not reconstruct granular historical work for completed V1, V1.5, or V2. V3 is current; V4 is upcoming; V5 is future.
