# Finance Tracker API

Local Spring Boot backend for a local-first personal finance tracker. It stores data in SQLite and keeps uploaded statements on the local computer.

## How the application works

1. A user uploads a PDF/CSV statement or drops it into the watched incoming folder.
2. Spring Boot identifies the institution/account, selects a statement parser, extracts transactions and statement metadata, and persists the original file locally.
3. The import flow normalizes merchants, detects duplicate transactions and transfers, applies remembered category rules, then asks for review only where needed.
4. Confirmed transactions and statement snapshots feed Accounts, Dashboard, Notifications, and the V3 Assistant.
5. The Assistant never receives database access. It may only call allow-listed read-only finance tools; Spring Boot performs the calculation/query and returns structured evidence for the local model to explain.

For the complete architecture, request lifecycle, tool schemas, data rules, security boundaries, test plan, and V4 evolution, read [the AI Assistant Architecture](../docs/personal-finance-tracker/v3/AI_ASSISTANT_ARCHITECTURE.md).

## What it does today

- Imports text-based PDF and CSV statements from supported American Express, Bank of America, Discover, and Wells Fargo layouts.
- Detects or creates accounts, extracts account/card statement summaries, and prevents duplicate statement and transaction imports.
- Supports a watched incoming folder for automatic local imports.
- Detects transfers, normalizes merchants, applies remembered category rules, and provides review/confirmation workflows.
- Provides accounts (including saved card utilization snapshots), transaction search/filtering, dashboard summaries and trends, merchant spending, recurring-pattern detection, and statement/payment/APR reminders.

## Run locally

```powershell
mvn spring-boot:run
```

The API listens on `http://localhost:8080`.

- Health: `GET /actuator/health`
- Application health: `GET /api/health`
- SQLite database: `data/finance-tracker.db`
- Watched incoming folder: `W:\projects\finance-tracker-incoming`
- Processed-statement archive: `W:\projects\finance-tracker-archive`

## Useful endpoints

- `GET /api/accounts` — account snapshots and history
- `GET /api/imports` — statement import history
- `GET /api/transactions` — searchable, filterable transactions
- `GET /api/dashboard/summary` and `/api/dashboard/monthly-trends` — dashboard data
- `GET /api/dashboard/merchant-spending` — merchant-level spending
- `GET /api/reminders` and `/api/recurring-transactions` — forward-looking activity
- `GET /api/finance-tools/*` — V3 read-only assistant tool layer: summaries, category/merchant spending, period comparisons, credit utilization, recurring activity, and transaction search

## V3 assistant foundation

The `finance-tools` endpoints are the only data operations an AI assistant will be allowed to call. They are read-only, return structured results, and are independently testable before an LLM is introduced. The local assistant also handles common date phrases and direct per-card/merchant questions deterministically, so a small local model does not need to guess their meaning.

### Local LM Studio

V3 is configured to use LM Studio locally by default at `http://localhost:1234/v1` with model `gemma-4-e2b-it-qat`. Start a model server in LM Studio's Developer tab before using the assistant. Optional overrides are `LM_STUDIO_BASE_URL`, `LM_STUDIO_MODEL`, and `LM_STUDIO_API_KEY`; the key is unnecessary unless LM Studio authentication is enabled.

The UI sends the question plus the recent in-page conversation to `POST /api/assistant/chat`. `LocalAssistantService` supplies the model with a date-aware system prompt and a generated tool catalog. If the model requests a permitted tool, Spring Boot validates its arguments, executes the corresponding `FinanceToolsService` operation, and sends the structured result back to the model for a grounded answer. The response includes `toolsUsed`, which the UI shows as “Data used.”

## Tests

```powershell
mvn test
```
