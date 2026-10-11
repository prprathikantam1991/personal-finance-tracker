-- V7: merchant/category choices are personal. SQLite cannot replace the old
-- global unique constraint in place, so rebuild the small rules table.
CREATE TABLE category_rules_v7 (
    id TEXT PRIMARY KEY,
    match_text TEXT NOT NULL COLLATE NOCASE,
    category TEXT NOT NULL,
    created_at TEXT NOT NULL,
    last_used_at TEXT,
    owner_user_id TEXT NOT NULL,
    UNIQUE (owner_user_id, match_text)
);

INSERT INTO category_rules_v7 (id, match_text, category, created_at, last_used_at, owner_user_id)
SELECT id, match_text, category, created_at, last_used_at, owner_user_id FROM category_rules;

DROP TABLE category_rules;
ALTER TABLE category_rules_v7 RENAME TO category_rules;
CREATE INDEX idx_category_rules_owner_match_text ON category_rules (owner_user_id, match_text);
