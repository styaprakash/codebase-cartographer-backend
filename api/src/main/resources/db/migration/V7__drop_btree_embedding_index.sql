-- Drop B-tree index that incorrectly INCLUDEs embedding vector(1536).
-- B-tree cannot store large vector values (exceeds 2704 byte max row size).
-- The HNSW index (idx_code_chunks_embedding_hnsw) handles vector similarity search.
-- The B-tree index on repo_id (idx_code_chunks_repo_id) handles repo filtering.
DROP INDEX IF EXISTS idx_code_chunks_repo_embedding;
