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
