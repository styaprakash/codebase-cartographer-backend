-- Step 1: Add new column for enum
ALTER TABLE repositories ADD COLUMN embedding_model_new VARCHAR(255);

-- Step 2: Map existing string values to Enum constants
UPDATE repositories 
SET embedding_model_new = 'QWEN3_EMBEDDING' 
WHERE embedding_model = 'qwen3-embedding:8b' OR embedding_model IS NULL;

-- Step 3: Drop old columns
ALTER TABLE repositories DROP COLUMN embedding_model;
ALTER TABLE repositories DROP COLUMN embedding_dimension;
ALTER TABLE repositories DROP COLUMN embedding_provider;

-- Step 4: Rename new column to original name
ALTER TABLE repositories RENAME COLUMN embedding_model_new TO embedding_model;
