ALTER TABLE code_chunks
ADD COLUMN IF NOT EXISTS scope_chain VARCHAR(500),
ADD COLUMN IF NOT EXISTS entity_name VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_code_chunks_scope_chain ON code_chunks(scope_chain);
CREATE INDEX IF NOT EXISTS idx_code_chunks_entity_name ON code_chunks(entity_name);

COMMENT ON COLUMN code_chunks.scope_chain IS 'Hierarchical scope: ClassName > methodName';
COMMENT ON COLUMN code_chunks.entity_name IS 'Function/class/method name extracted via AST';
