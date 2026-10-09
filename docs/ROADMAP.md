# Development Roadmap

## Completed

- **V1:** statement upload, parsing, review, and SQLite persistence.
- **V1.5:** accounts, duplicate/transfer detection, merchant/category automation, watched-folder imports.
- **V2:** accounts, transactions, Dashboard, history, trends, and reporting views.
- **V2 polish:** utilization, statement snapshots, reminders, recurring activity, merchant spending.
- **V3 foundation:** read-only finance tools, local LM Studio connection, and Assistant UI.
- **V9:** persistent agent memory — backend-owned local conversations, bounded session context, refresh restoration, and explicit deletion. See [V9 memory delivery](V9_PERSISTENT_AGENT_MEMORY_PLAN.md).

## Current V4 work

- Run bounded, multi-step, read-only analysis through the new Agent Run endpoint.
- Re-run the selected-provider manual checklist after the latest grounding fixes, including traces, clarification, and safe failure behavior.
- Close V4 only after those real-provider checks pass; synthetic evaluation coverage is already in place.

## Planned backlog

- **V5:** optional production polish: Docker, backups, encryption planning, CI/CD, PostgreSQL, and deployment.
- **V6:** Spring AI integration and cloud-provider boundary — the migration foundation is in progress. It will preserve the existing trusted finance tools, keep the current custom assistant as the fallback until parity is proven, then add explicit provider opt-in for local OpenAI-compatible and AWS Bedrock Converse paths. See [V6 plan](V6_SPRING_AI_MIGRATION_PLAN.md) and GitHub Issue #19.
- **V7:** multi-user and connected finance — introduce authentication, strict user data isolation, then evaluate optional Plaid connections. See GitHub Issue #20.
- **V8:** mobile finance experience — decide between a responsive PWA and a native/cross-platform client after user identity and sync boundaries are established. See GitHub Issue #21.
- **V10:** explicit long-term Assistant preferences — local, visible, editable, and deletable preferences kept separate from chat history and ledger data. See [V10 plan](V10_EXPLICIT_MEMORY_PLAN.md).
