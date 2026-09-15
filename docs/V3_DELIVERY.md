# V3 — Local AI Assistant

**Status:** implementation complete; validate with real questions before formally closing the parent iteration

V3 adds a local-language-model interface without allowing the model to become a source of truth or a system operator.

## Implemented

- Angular Assistant conversation screen backed by a local LM Studio model.
- OpenAI-compatible tool calling over the local LM Studio server.
- Allow-listed, read-only finance tools for summaries, categories, merchants, recurring activity, utilization, transactions, account context, and bounded account history.
- Session-only conversation context for unambiguous follow-up merchants and date ranges.
- Visible evidence showing local data use, selected period, and tool name.
- Configurable 60-second local-model read timeout and recovery messages for unavailable, slow, and malformed model responses.

## Safety boundary

The model can request a named tool. Spring Boot validates that tool and its arguments, reads structured finance data, then returns the result to the model for a natural-language answer. The model cannot run SQL, access files, change data, or call arbitrary network services.

## Configuration

LM Studio defaults to `http://localhost:1234/v1`. The backend accepts `LM_STUDIO_BASE_URL`, `LM_STUDIO_MODEL`, `LM_STUDIO_API_KEY`, and `LM_STUDIO_TIMEOUT_MS` environment variables. API tokens are optional for localhost but recommended when exposing the local server to a network.

See [AI Assistant Architecture](AI_ASSISTANT_ARCHITECTURE.md) for the complete design.
