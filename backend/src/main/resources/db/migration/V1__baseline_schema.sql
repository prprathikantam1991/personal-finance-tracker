CREATE TABLE IF NOT EXISTS application_metadata (
    metadata_key TEXT PRIMARY KEY,
    metadata_value TEXT NOT NULL
);

INSERT OR IGNORE INTO application_metadata (metadata_key, metadata_value)
VALUES ('schema_version', 'baseline');

