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

## Quality follow-up

Dashboard period alignment and merchant drill-down are tracked separately in GitHub Issue #14. Bank of America imports also stop before statement marketing/footer text, and repair historical card-payment, Zelle, and PSEG classifications in Issue #15. These do not change the completed V2 foundation; they improve the quality of its existing data.
