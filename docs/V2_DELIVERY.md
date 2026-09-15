# V2 — Tracker Views and Insights

**Status:** complete, with ongoing quality fixes tracked independently

V2 turned the trusted ledger into a useful day-to-day finance application.

## Implemented

- Account cards for checking, savings, and credit cards.
- Statement snapshots: balances, limits, utilization, APRs, due dates, statement cycles, fees, and interest where present.
- Account history and credit-utilization comparisons.
- Transactions search, account/date/category/review filters, and category correction.
- Dashboard totals for income, expenses, India remittance, and net cash flow.
- Category spending, merchant spending, cash-flow trends, and period comparisons.
- Notifications for statement dates, due dates, promotional APR expiry, and recurring activity.

## Financial semantics

Dashboard and reporting calculations use confirmed transactions. Transfers are excluded from spending. India remittance is presented separately from ordinary expenses so it can be understood without hiding the cash movement.

## How it is built

The dashboard calls dedicated Spring Boot summary, category, merchant, and monthly-trend endpoints. Each endpoint applies the same account/date/confirmed-transaction criteria before calculating totals in SQLite; the Angular dashboard only renders the returned result. Account cards combine stable account identity with the latest imported statement snapshot, so balance, utilization, APR, due date, and statement cycle do not need to be re-derived from transaction rows. Transaction filters run against the local ledger and link dashboard categories and merchants back to the exact matching rows.

See [System Architecture](ARCHITECTURE.md), [dashboard and account flows](REQUEST_FLOWS.md), and [design decisions](DESIGN_DECISIONS.md).

## Quality follow-up

Dashboard period alignment and merchant drill-down are tracked separately in GitHub Issue #14. Bank of America imports also stop before statement marketing/footer text, and repair historical card-payment, Zelle, and PSEG classifications in Issue #15. These do not change the completed V2 foundation; they improve the quality of its existing data.
