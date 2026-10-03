CREATE TABLE assistant_conversations (
    id TEXT PRIMARY KEY,
    created_at TEXT NOT NULL,
    last_active_at TEXT NOT NULL,
    title TEXT
);

CREATE TABLE assistant_messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    sequence_number INTEGER NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TEXT NOT NULL,
    execution_mode TEXT,
    tools_used TEXT,
    evidence TEXT,
    agent_steps TEXT,
    FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(id) ON DELETE CASCADE,
    UNIQUE (conversation_id, sequence_number)
);

CREATE INDEX idx_assistant_messages_conversation_sequence
    ON assistant_messages (conversation_id, sequence_number);

CREATE TABLE assistant_memory_summaries (
    conversation_id TEXT PRIMARY KEY,
    structured_context TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(id) ON DELETE CASCADE
);
