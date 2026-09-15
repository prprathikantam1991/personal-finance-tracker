# Development Roadmap

## Completed

- **V1:** statement upload, parsing, review, and SQLite persistence.
- **V1.5:** accounts, duplicate/transfer detection, merchant/category automation, watched-folder imports.
- **V2:** accounts, transactions, Dashboard, history, trends, and reporting views.
- **V2 polish:** utilization, statement snapshots, reminders, recurring activity, merchant spending.
- **V3 foundation:** read-only finance tools, local LM Studio connection, and Assistant UI.

## Current V4 work

- Run bounded, multi-step, read-only analysis through the new Agent Run endpoint.
- Validate sequential tool calls against a maintained evaluation set, including timeout, cancellation, and evidence behavior.
- Keep the existing V3 Assistant as the simpler single-analysis experience while agent orchestration matures.

## Planned backlog

- **V5:** optional production polish: Docker, backups, encryption planning, CI/CD, PostgreSQL, and deployment.
- **V6:** cloud AI provider integrations — assess a targeted Spring AI or LangChain4j migration and optional AWS Bedrock, with explicit opt-in and minimum necessary data sent outside the computer. See GitHub Issue #19.
- **V7:** multi-user and connected finance — introduce authentication, strict user data isolation, then evaluate optional Plaid connections. See GitHub Issue #20.
- **V8:** mobile finance experience — decide between a responsive PWA and a native/cross-platform client after user identity and sync boundaries are established. See GitHub Issue #21.
