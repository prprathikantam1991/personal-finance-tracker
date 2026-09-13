# Personal Finance Tracker — V1 Implementation Specification

## Purpose

Build a local-first application that lets one person import a real checking, savings, or credit-card statement; inspect and correct the extracted transactions; and save the confirmed data locally.

**V1 definition of done:** upload one supported file, let the application identify or create the account when possible, review the extracted rows, edit or delete rows, confirm the import, and see the saved transactions after refreshing the application.

## Technology choices

- Frontend: Angular + TypeScript
- Backend: Java + Spring Boot
- Database: SQLite
- PDF processing: Apache PDFBox, for text-based PDFs only
- Original statement files: local filesystem only
- Tests: JUnit, Mockito, and Spring Boot Test

## Exact V1 features

1. Account identification and setup
   - During upload, extract account clues from the statement: institution, account type, masked account number/last four digits, account holder name when available, and statement period.
   - Match an existing account by normalized institution + last four digits. This is the primary stable account identity.
   - If no match exists and the parser has a reliable institution plus last four digits, automatically create the account and attach the import to it.
   - If the account cannot be identified confidently, show a compact confirmation step: choose an existing account or supply a name/type before the import can be confirmed.
   - Support account types: `CHECKING`, `SAVINGS`, `CREDIT_CARD`, and `OTHER`.
   - Store a display name, institution, optional last four digits, account type, and currency.

2. Statement upload and preview
   - Upload a CSV or a text-based PDF; account matching/creation happens as part of the upload.
   - Validate the file type and report parsing errors clearly.
   - Parse into an unsaved transaction preview; previewing must not write transaction data to SQLite.

3. Transaction review
   - Show each parsed transaction in an editable table.
   - Permit editing posted date, description, amount, optional balance, and optional category.
   - Permit deleting unwanted rows before confirmation.
   - Display parsing warnings and the preview row count.

4. Confirmed import and persisted transaction list
   - Confirming an import stores the import record and all reviewed transactions atomically.
   - Copy the original statement file into local application storage.
   - Show saved transactions with account and date-range filters.
   - Support edit and delete for saved transactions.

5. Account overview
   - Show all detected and manually created accounts in one place.
   - For checking, savings, and other deposit accounts, show the most recently imported statement balance and its `as of` date.
   - For credit cards, show the most recently imported statement balance, credit limit, available credit when supplied by the statement, and their `as of` date.
   - Clearly label these values as statement-derived, not live balances. V1 does not connect to a bank or card issuer.

## Supported statement types

| Format | V1 support | Approach |
| --- | --- | --- |
| CSV transaction export | Yes | Parse a known layout containing date, description, amount, and optionally balance. |
| Text-based PDF | Yes | Use PDFBox and a parser tailored to one known statement layout. |
| Scanned PDF | No | OCR is deliberately deferred. |
| OFX / QFX | No | Useful later, but not required for the first usable release. |

Start with one real CSV export if available; it is the quickest reliable end-to-end slice. Add one real text-PDF layout only after that flow works.

## Import workflow

1. User uploads a supported statement.
2. Backend validates the file, extracts account identity clues, and looks for a matching local account.
3. Backend uses the matching account, automatically creates a confidently new account, or returns an account-confirmation request when information is insufficient.
4. Backend parses the file into an unsaved transaction preview.
5. User reviews, edits, or deletes preview transactions. Account confirmation is shown only when required.
6. User confirms the import.
7. Backend saves the original file locally and persists the account (when newly created), import, and transactions in one database transaction.
8. User sees the saved transactions in the transaction list.

## Data model

### Account

| Field | Notes |
| --- | --- |
| `id` | Internal identifier |
| `name` | User-facing account name |
| `institution` | Optional bank/card issuer name |
| `type` | Checking, savings, credit card, or other |
| `lastFour` | Optional, not a full account number |
| `identityKey` | Normalized institution + last four digits when both are available; used for automatic matching |
| `currency` | Defaults to USD unless later configured otherwise |
| `createdAt` | Creation timestamp |

### Account snapshot

An account snapshot records the latest balance-related values obtained from an imported statement. It prevents the Accounts screen from presenting an old statement amount as a live balance.

| Field | Notes |
| --- | --- |
| `accountId` | Account to which the values belong |
| `asOfDate` | Date printed on the statement or statement end date |
| `statementBalanceMinor` | Latest statement or current balance reported by the source |
| `creditLimitMinor` | Credit-card limit when present; otherwise empty |
| `availableCreditMinor` | Available credit when present; otherwise empty |
| `sourceImportId` | Import from which the snapshot was extracted |

### Statement import

| Field | Notes |
| --- | --- |
| `id` | Internal identifier |
| `accountId` | Account that owns this statement |
| `sourceFilename` | Original file name |
| `sourceType` | `CSV` or `PDF` |
| `fileHash` | Used to warn about a repeated identical import |
| `storagePath` | Local path to the retained source file |
| `importedAt` | Confirmation timestamp |
| `status` | `DRAFT`, `COMPLETED`, or `FAILED` |

### Transaction

| Field | Notes |
| --- | --- |
| `id` | Internal identifier |
| `accountId` | Owning account |
| `importId` | Import that created the transaction |
| `postedDate` | ISO-8601 local date |
| `descriptionRaw` | Exact source description/line for traceability |
| `descriptionNormalized` | Initial trimmed, whitespace-normalized description |
| `amountMinor` | Signed integer minor units, such as cents |
| `balanceMinor` | Optional signed integer minor units |
| `category` | Optional manual category |
| `notes` | Optional user note |
| `reviewStatus` | Initial state suitable for a later review queue |

## Data rules

- A transaction requires a posted date, raw description, and non-zero amount before confirmation.
- Store money as `BigDecimal` in Java and integer minor units in SQLite. Never use floating-point amounts.
- Signed amount convention: positive means money into the account; negative means money out.
- Preserve raw extracted data to support diagnosis of parser errors.
- Do not guess an ambiguous year from a statement date. Require a known statement period or parser rule.
- Do not auto-create an account from a merchant name, filename, or transaction descriptions alone. Automatic creation requires reliable account-level statement metadata.
- Do not treat an institution alone as a unique account identity; one institution can have several checking, savings, or card accounts.
- Account balances and card limits must retain an `as of` date. A V1 account snapshot is statement-derived and must never be described as a live balance.
- Warn when an identical completed import's file hash already exists, but do not automatically skip it in V1.

## SQLite schema outline

- `accounts`
- `account_snapshots`
- `statement_imports`
- `transactions`

Add indexes on `transactions(account_id, posted_date)`, `transactions(import_id)`, and `account_snapshots(account_id, as_of_date)`. Make `statement_imports.file_hash` unique for completed imports, or otherwise enforce that uniqueness in application logic while preserving failed-import records.

## Angular screens

1. **Imports** — upload statement, show detected account, and request account confirmation only when detection is uncertain.
2. **Review import** — edit/delete parsed rows and confirm the import.
3. **Transactions** — show saved transactions, filtered by account and date range.
4. **Accounts** — show every account with its type, institution, last four digits, most recent statement balance, `as of` date, and—where applicable—credit limit and available credit.

Keep preview edits in browser state until the user confirms the import.

## Spring Boot API contract

| Method | Route | Responsibility |
| --- | --- | --- |
| `POST` | `/api/accounts` | Create an account. |
| `GET` | `/api/accounts` | List accounts. |
| `GET` | `/api/accounts/{id}` | Return an account and its latest statement-derived balance/limit snapshot. |
| `POST` | `/api/imports/preview` | Accept a multipart file, identify/create/flag the account as appropriate, and return unsaved preview rows and warnings. |
| `POST` | `/api/imports` | Persist a user-confirmed edited preview atomically. |
| `GET` | `/api/transactions` | List transactions; filter by account and date range. |
| `PATCH` | `/api/transactions/{id}` | Edit a saved transaction. |
| `DELETE` | `/api/transactions/{id}` | Delete a saved transaction. |

## Implementation order

1. Create the Spring Boot and Angular foundations, SQLite migration support, local configuration, and health endpoint.
2. Implement account identity matching and account creation/listing.
3. Build the first complete CSV path: upload → identify/create/confirm account → parse preview → review → confirm → transaction list.
4. Add account snapshots to the import path and build the Accounts screen with statement-date labels.
5. Add PDFBox parsing for one known, real text-based statement format through the same preview workflow.
6. Add validation, clear errors, file-hash warnings, fixtures, and tests.
7. Verify against a real statement before beginning V1.5.

## V1 acceptance checks

- Unit-test CSV/PDF parsers with sanitized sample statements, including difficult dates and amounts.
- Integration-test successful import, validation failure, and database rollback behavior.
- Manually compare a real statement's row count, dates, descriptions, debit total, and credit total to its preview.
- Refresh the application after confirmation and verify saved transactions remain connected to the correct account and import.

## Explicitly out of scope

- Fully automatic account creation when a statement lacks reliable account-level metadata
- Duplicate detection beyond repeated-file warning
- Transfer detection and credit-card-payment matching
- Merchant normalization and automatic categorization
- Category rules, correction memory, review queue, or confidence scoring
- Dashboards, budgets, reports, search beyond basic filters, and recurring transaction detection
- OCR, bank connections, cloud sync/deployment, authentication, encryption-at-rest, backups, and AI/LLM features

These are V1.5+ capabilities. V1 must first prove that importing and correcting real statements is dependable.
