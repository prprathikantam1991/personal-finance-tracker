# Finance Tracker UI

Angular 22 web interface for the local-first Personal Finance Tracker.

## What it does today

- Imports PDF/CSV statements and shows a review workflow and import history.
- Displays accounts, card terms, statement summaries, account history, and overall/per-card credit utilization with prior-statement changes.
- Searches and filters transactions, including categories and remembered merchant categorization.
- Provides a dashboard with cash-flow trends, category and merchant spending, and selected-period comparison.
- Shows statement, payment-due, promotional-APR, and recurring-activity reminders in Notifications.
- Includes a local AI Finance Assistant powered by LM Studio, grounded exclusively in read-only finance tools, with follow-up context and common relative-date handling.

The UI communicates only with the local Spring Boot API at `http://localhost:8080`.

## How the UI works

The UI is a standalone Angular application that calls the local Spring Boot API; it does not access SQLite or statement files directly. Each page is a focused view over API data:

- **Imports** uploads statements, shows the import/review path, watched-folder status, and import history.
- **Accounts** shows saved account snapshots, card terms, utilization, and statement history.
- **Transactions** provides the inspectable ledger behind dashboard and assistant answers.
- **Dashboard** presents confirmed cash flow, spending, trends, comparisons, and merchant totals.
- **Notifications** collects upcoming statement, payment, promotional-APR, and recurring-activity information.
- **Assistant** sends a question and recent in-page conversation to the local backend, then displays the answer and the finance tools used as evidence.

The detailed assistant architecture—including the Angular-to-Spring request flow, LM Studio integration, tool-calling loop, safety model, and evaluation plan—is documented in [AI Assistant Architecture](../docs/personal-finance-tracker/v3/AI_ASSISTANT_ARCHITECTURE.md).

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
