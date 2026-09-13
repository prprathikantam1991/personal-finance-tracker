# Development Roadmap

## Completed

- **V1:** statement upload, parsing, review, and SQLite persistence.
- **V1.5:** accounts, duplicate/transfer detection, merchant/category automation, watched-folder imports.
- **V2:** accounts, transactions, Dashboard, history, trends, and reporting views.
- **V2 polish:** utilization, statement snapshots, reminders, recurring activity, merchant spending.
- **V3 foundation:** read-only finance tools, local LM Studio connection, and Assistant UI.

## Current V3 work

- Improve LLM-first tool selection and measure it against a maintained evaluation set.
- Improve multi-tool answers, citations/links back to supporting transactions, cancellation, and timeout behavior.
- Keep deterministic direct answers only as reliability fallback for unambiguous questions.

## Later

- **V4:** deliberate multi-step analysis agent using the same safe tool contracts.
- **V5:** optional production polish: Docker, backups, encryption planning, CI/CD, authentication, PostgreSQL, and deployment.
