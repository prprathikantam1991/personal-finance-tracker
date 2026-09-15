# V2.5 — Advanced Automation

**Status:** complete

V2.5 made regular use less manual without introducing cloud dependency.

## Implemented

- Multiple institution-specific PDF adapters for the supplied American Express, Bank of America, Discover, and Wells Fargo layouts.
- Account and statement-summary extraction beyond transactions, including card terms and bank activity fields when available.
- Import history and duplicate-safe replacement behavior.
- Scheduled watched-folder processing for local statements.
- Recurring-transaction detection based on confirmed transaction timing, merchant, and amount patterns.
- Reminder generation from statement cycles, payment due dates, and promotional APR dates.

## Limits

Every bank can change its layouts. Parser adapters are intentionally explicit and testable rather than pretending to scrape any document perfectly. OCR remains a later enhancement for scanned statements.

## How it is built

Each institution/layout has a small parser adapter with an explicit `supports()` check and parsing rules for its transaction section. Separate summary and card-terms extractors read statement-level facts such as balances, limits, APRs, and due dates. The import service persists these facts as a snapshot associated with the import and account, which makes account history and reminders possible even when a later statement changes. Recurring detection groups confirmed transactions by normalized merchant and category, then compares timing and amount across multiple occurrences; reminders are calculated from saved due dates, promotion end dates, and statement-cycle dates.

See [System Architecture](ARCHITECTURE.md), [request flows](REQUEST_FLOWS.md), and [design decisions](DESIGN_DECISIONS.md).
