CREATE TABLE category_rules (
    id TEXT PRIMARY KEY,
    match_text TEXT NOT NULL COLLATE NOCASE UNIQUE,
    category TEXT NOT NULL,
    created_at TEXT NOT NULL,
    last_used_at TEXT
);

CREATE INDEX idx_category_rules_match_text ON category_rules(match_text);
