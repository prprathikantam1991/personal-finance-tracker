# Request Flows

## Statement import

```mermaid
sequenceDiagram
    participant U as User or watched folder
    participant API as Import API
    participant P as Parser adapter
    participant DB as SQLite
    U->>API: PDF/CSV statement
    API->>P: Extract text and parse institution layout
    P-->>API: Account metadata, summary, transactions
    API->>DB: Save import, snapshots, and non-duplicate rows
    API->>API: Detect transfers and apply category rules
    API-->>U: Review required or automatically confirmed result
```

## Assistant question

```mermaid
sequenceDiagram
    participant U as User
    participant UI as Assistant page
    participant B as Spring Boot
    participant L as LM Studio
    participant F as Finance tools
    UI->>B: conversation ID and question
    B->>B: Load bounded local history + structured session context
    B->>L: Prompt, bounded context, and approved tool definitions
    L-->>B: Requested tool call
    B->>F: Validated read-only operation
    F-->>B: Structured local result
    B->>L: Tool result
    L-->>B: Grounded response
    B->>B: Persist user turn, answer, and safe context update
    B-->>UI: Answer, tools used, and trace when multi-step
```

## Watched-folder automation

```text
Incoming folder scan → validate file → import pipeline
→ confident known-account import is confirmed → archive file
→ ambiguity or parsing issue remains visible for attention
```
