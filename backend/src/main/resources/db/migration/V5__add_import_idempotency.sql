ALTER TABLE statement_imports ADD COLUMN source_hash TEXT;
CREATE INDEX idx_statement_imports_source_hash ON statement_imports(source_hash);
