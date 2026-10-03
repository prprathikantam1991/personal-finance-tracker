# V4 — Agentic Finance Workflows

**Status:** in progress — reliability and evaluation hardening underway

## Delivered so far

- A bounded sequential agent loop at `POST /api/assistant/agent-runs`.
- A model receives only an allow-listed catalog of read-only finance tools; it never receives database, SQL, filesystem, shell, or write access.
- Spring Boot validates every requested tool, applies date and result-size limits, and permits at most three tool rounds.
- Focused comparison tools return only the category and merchant evidence needed for a “why did this spending change?” question, instead of sending a broad transaction set to the model.
- Multi-step traces are displayed in Angular as human-readable agent activity.
- Period context is preserved for date-sensitive calls, optional presentation hints are removed, and redundant undated calls are stopped before they broaden a lookup.
- LM Studio remains supported; Amazon Bedrock Mantle with Gemma 4 31B has been verified for real sequential tool use.
- The Mantle adapter supports an optional provider reasoning-effort setting, enabling a like-for-like evaluation of Gemma 4 E2B with `BEDROCK_REASONING_EFFORT=high`.
- Rolling local logs record safe operational metadata without recording prompts, answers, statements, transaction rows, or credentials.
- The `get_credit_paydown_plan` tool performs a cent-accurate, read-only calculation for strict utilization targets. For example, “below 10%” uses a maximum projected balance of $5,799.99—not $5,800.00—so the resulting utilization is truly below the target. It ranks cards by utilization, then current APR and promotional-APR expiry, while leaving the final payment decision with the user.

## Evaluation coverage

Synthetic evaluations verify successful sequential calls, optional tool hints, period preservation, duplicate prevention, multiple-call rejection, unknown-tool rejection, invalid-target rejection, overly broad date-range rejection, tool-round budget enforcement, focused category comparisons, deterministic fallback, focused clarification for an ambiguous spending question, and selection of the strict-target credit-paydown tool. Calculator unit tests cover cent boundaries, unavailable card limits, and accounts already below a target. These tests use mocked tools and model responses; private financial answers are never committed. See [Agent evaluation and safety checks](AGENT_EVALUATION.md) for the scenario matrix, observability policy, and model-change checklist.

## Remaining before closure

1. Run and record a small manual evaluation checklist with the selected provider, without saving private answers.
2. Review the assistant’s clarification wording after normal use.
3. Confirm the visible trace and local logs are understandable, then close the V4 tracking work.

See [V4 implementation plan](V4_IMPLEMENTATION_PLAN.md) for the architecture and safety model.
