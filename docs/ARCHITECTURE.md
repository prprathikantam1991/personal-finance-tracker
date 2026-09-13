# System Architecture

## Overview

```mermaid
flowchart TB
    UI[Angular 22 frontend] --> API[Spring Boot API]
    API --> IMPORT[Import and parser adapters]
    API --> FINANCE[Accounts, transactions, dashboard, notifications]
    API --> ASSISTANT[Assistant orchestration]
    IMPORT --> DB[(SQLite)]
    FINANCE --> DB
    ASSISTANT --> TOOLS[Read-only finance tools]
    TOOLS --> FINANCE
    ASSISTANT <--> LM[LM Studio on localhost]
```

## Ownership boundaries

- **Frontend:** presentation, filters, review interactions, and in-page Assistant conversation context.
- **Backend:** statement parsing, persistence, financial calculations, data validation, API contracts, and AI tool authorization.
- **SQLite:** local ledger, accounts, imports, transactions, category mappings, and statement snapshots.
- **LM Studio:** interprets questions and explains tool results. It has no database or filesystem permission.

## Data lifecycle

```text
Statement file → parser adapter → normalized transaction/statement fields
→ account match → duplicate and transfer detection → category/review state
→ confirmed local ledger → dashboard/accounts/notifications/assistant tools
```

## Related documents

- [Request flows](REQUEST_FLOWS.md)
- [Design decisions](DESIGN_DECISIONS.md)
- [Detailed AI Assistant Architecture](AI_ASSISTANT_ARCHITECTURE.md)
