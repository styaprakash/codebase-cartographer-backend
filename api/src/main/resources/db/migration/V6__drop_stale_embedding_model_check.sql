-- Hibernate's ddl-auto:update created this CHECK constraint when the
-- EmbeddingModel enum had fewer values. It went stale when
-- OPENROUTER_QWEN_EMBEDDING was added. Drop it — application-level
-- validation in IndexingService (valueOf + isEnabled) is the source
-- of truth for allowed values.
ALTER TABLE repositories DROP CONSTRAINT IF EXISTS repositories_embedding_model_check;
