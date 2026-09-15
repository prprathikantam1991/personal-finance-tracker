# V1 — Statement Import Foundation

**Status:** complete

V1 established the trusted local ledger. A person can upload a PDF or CSV statement, inspect parsed rows, correct or delete them, and save the result in SQLite.

## Implemented

- Angular Import and Review screens.
- Spring Boot upload, import-review, edit, delete, and confirm APIs.
- SQLite persistence for accounts, imports, transactions, and original local statement files.
- Text-based PDF extraction through Apache PDFBox and CSV import.
- Normalization of dates, descriptions, amounts, balances, and account identity.

## Core flow

`statement → parser → normalized rows → review → confirmed local ledger`

The original statement is retained locally for traceability. Imported transactions are not treated as trusted dashboard data until the import is confirmed.

## Intentional limits

V1 did not promise universal statement parsing, automatic category decisions, dashboards, or AI. Those were layered on only after this local, reviewable import path worked.

## Verification

Parser fixtures and Spring Boot tests verify known statement layouts. Real local statements remain the practical acceptance test for a new institution/layout.

See also: [original V1 specification](V1_IMPLEMENTATION_SPECIFICATION.md).
