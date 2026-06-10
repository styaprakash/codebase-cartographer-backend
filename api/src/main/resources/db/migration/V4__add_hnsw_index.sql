-- HNSW index for vector similarity search (pgvector >= 0.5.0 required)
-- This enables fast cosine similarity search on embeddings

-- Create HNSW index for vector similarity search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_code_chunks_embedding_hnsw
ON code_chunks
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Create supporting index for repo filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_code_chunks_repo_id
ON code_chunks (repo_id);

-- Create covering index for repo-scoped vector search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_code_chunks_repo_embedding
ON code_chunks (repo_id, id)
INCLUDE (embedding);
