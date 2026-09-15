# V1.5 — Finance Automation

**Status:** complete

V1.5 reduced routine cleanup while keeping the statement and confirmed ledger as the source of truth.

## Implemented

- Automatic match-or-create account detection during imports.
- Duplicate protection for statements and transactions.
- Transfer and credit-card-payment recognition to avoid double-counting spend.
- Merchant normalization and remembered category rules.
- Review-required status for uncertain items and a transaction-level correction path.
- Local watched-folder imports with archive handling.

## Operating principle

Automation proposes or applies repeatable decisions. It never invents a transaction: imports, statement snapshots, and confirmed corrections remain inspectable locally.

## Data effects

Saved category rules apply to future normalized merchant descriptions. Transfer grouping marks both sides of an internal movement so expenses and cash flow do not count the same money twice.

## How it is built

Account identity is derived from institution, account type, and last four digits. The import service matches that identity to an existing SQLite account or creates it when it is confidently new. Each import also stores a source-file hash, while transaction duplicate checks compare the account, date, normalized description, and amount. The categorizer first applies a remembered merchant rule, then deterministic finance rules; uncertain rows stay reviewable. A transfer detector links compatible debit and credit rows after import, preserving both ledger entries but excluding the movement from spending calculations. The watched-folder scheduler reuses the same import service, rather than maintaining a second ingestion path.

See [System Architecture](ARCHITECTURE.md), [request flows](REQUEST_FLOWS.md), and [design decisions](DESIGN_DECISIONS.md).

## Verification

Imports are idempotent for repeated source files, and parser/category/transfer behavior is covered by backend tests plus review against local statements.
