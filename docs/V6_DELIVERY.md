# V6 — Spring AI and Gemma Runtime

**Status:** complete

V6 moved the supported cloud Assistant integration from handwritten provider calls to Spring AI while preserving the local, read-only finance-tool boundary.

## Delivered

- Spring AI `ChatClient` drives Bedrock Mantle's OpenAI-compatible Chat Completions API.
- Google Gemma 4 31B is the supported model and `spring-ai-bedrock-mantle` is the default repository-launcher runtime.
- Eleven allow-listed, read-only finance tools remain behind `SpringAiFinanceTools`; the model cannot access SQL, statement files, imports, payments, or write APIs.
- Tool execution is captured in a request-local trace and returned as human-readable activity and evidence in the Angular Assistant.
- Provider timeouts, unavailable providers, and rejected tokens return safe user-facing RFC 9457 details without exposing credentials or finance data.
- The prior custom runtime remains available through `-Runtime custom` as a diagnostic rollback, not as the normal startup path.

## Verification

The Spring AI/Mantle/Gemma path was verified with real saved ledger data for:

- single-tool category spending and credit-utilization questions;
- multi-tool month comparison plus merchant questions;
- payments due plus checking/savings coverage;
- recurring activity plus account coverage;
- explicit date clarification for broad spending questions;
- grounded evidence and trace labels without raw tool syntax; and
- safe provider-error behavior.

`mvn test` passed **66 tests** on October 9, 2026, including agent evaluation, controller error handling, finance-tool façade, conversation persistence, parsing, and dashboard tests.

## Normal startup

Set the session-only Bedrock Mantle token, then use the repository launcher:

```powershell
$env:AWS_BEARER_TOKEN_BEDROCK = "your-bedrock-mantle-api-key"
./scripts/start-backend.ps1
```

See the [V6 migration plan](V6_SPRING_AI_MIGRATION_PLAN.md) for architecture, provider rationale, and the retained rollback path.
