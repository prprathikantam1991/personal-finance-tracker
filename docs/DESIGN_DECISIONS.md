# Design Decisions

## Local-first storage

SQLite and local statement files are used initially because personal financial data should not require cloud hosting to be useful. PostgreSQL, encryption-at-rest, backups, and deployment are later production-polish decisions.

## Angular and Spring Boot

Angular fits the structured, form-heavy finance UI. Spring Boot uses the existing Java backend skill set and offers mature validation, testing, scheduling, and persistence support.

## Adapter-based parsing

Statements are not parsed with one universal regex. Each supported institution/layout has a parser adapter, while extraction, normalization, persistence, and review remain shared.

## Statement snapshots versus transactions

Transactions power spending/cash-flow analysis. Statement snapshots power account balances, card limits, APR information, due dates, and historical utilization. These are related but not interchangeable.

## LLM with tools, not direct database access

The model may request named read-only finance tools. Spring Boot validates arguments and performs all calculations. This prevents invented figures, arbitrary SQL, and unsafe writes.

## LLM-first with safety fallback

The preferred Assistant workflow is model-selected tool calling. A deterministic path may handle unambiguous common requests when a small local model cannot reliably resolve dates or tool arguments. It is a reliability fallback, not a replacement for the assistant architecture.

## No cloud infrastructure yet

Docker, Terraform, CloudFormation, authentication, CI/CD, and hosted deployment are intentionally deferred. Adding empty configuration before a deployment target exists would create maintenance burden without user value.
