# V4 — Agentic Finance Workflows

**Status:** in progress — reliability and evaluation hardening underway

## Delivered so far

- A bounded sequential agent loop at `POST /api/assistant/agent-runs`.
- A model receives only an allow-listed catalog of read-only finance tools; it never receives database, SQL, filesystem, shell, or write access.
- Spring Boot validates every requested tool, applies date and result-size limits, and permits at most three tool rounds.
- Focused comparison tools return only the category and merchant evidence needed for a “why did this spending change?” question, instead of sending a broad transaction set to the model.
- Multi-step traces are displayed in Angular as human-readable agent activity.
- The Assistant screen now chooses the single-step or bounded multi-step route automatically; people ask naturally instead of selecting a technical “Assistant” or “Agent Run” mode. Agent activity remains visible only when the multi-step route is used.
- Assistant answers render a deliberately small Markdown subset—headings, bold text, lists, inline code, and simple tables—after escaping model content. The UI never trusts or bypasses sanitization for model-provided HTML.
- Period context is preserved for date-sensitive calls, optional presentation hints are removed, and redundant undated calls are stopped before they broaden a lookup.
- Assistant-mode tool calls receive the same server-side resolved-period normalization and validation as Agent Run calls, so a model cannot silently widen a month-specific question to all saved history.
- Final-answer handling detects provider output-budget truncation (and common unfinished endings), then regenerates once from the same verified tool results. A second incomplete result is replaced with a clear retry message rather than a misleading partial financial answer.
- Category-comparison responses include an application-calculated first-period-to-second-period direction, so the model is explicitly grounded on whether spending increased, decreased, or did not change before it explains merchant drivers.
- If a provider declines tool selection for a narrowly understood, calculation-backed question, the service falls back to the same read-only tools for (a) highest-card utilization plus a strict target paydown and (b) upcoming minimum-payment coverage by checking and savings cash. This is reliability fallback, not an LLM bypass for open-ended questions.
- LM Studio remains supported; Amazon Bedrock Mantle with Gemma 4 31B has been verified for real sequential tool use.
- The Mantle adapter supports an optional provider reasoning-effort setting, enabling a like-for-like evaluation of Gemma 4 E2B with `BEDROCK_REASONING_EFFORT=high`.
- Rolling local logs record safe operational metadata without recording prompts, answers, statements, transaction rows, or credentials.
- The `get_credit_paydown_plan` tool performs a cent-accurate, read-only calculation for strict utilization targets. For example, “below 10%” uses a maximum projected balance of $5,799.99—not $5,800.00—so the resulting utilization is truly below the target. It ranks cards by utilization, then current APR and promotional-APR expiry, while leaving the final payment decision with the user.

## Evaluation coverage

Synthetic evaluations verify successful sequential calls, optional tool hints, period preservation, duplicate prevention, multiple-call rejection, unknown-tool rejection, invalid-target rejection, overly broad date-range rejection, tool-round budget enforcement, focused category comparisons, truncation recovery, deterministic fallback, focused clarification for an ambiguous spending question, and selection of the strict-target credit-paydown tool. Calculator unit tests cover cent boundaries, unavailable card limits, and accounts already below a target. These tests use mocked tools and model responses; private financial answers are never committed. See [Agent evaluation and safety checks](AGENT_EVALUATION.md) for the scenario matrix, observability policy, and model-change checklist.

## Remaining before closure

1. Run and record a small manual evaluation checklist with the selected provider, without saving private answers.
2. Review the assistant’s clarification wording after normal use.
3. Confirm the visible trace and local logs are understandable, then close the V4 tracking work.

See [V4 implementation plan](V4_IMPLEMENTATION_PLAN.md) for the architecture and safety model.
