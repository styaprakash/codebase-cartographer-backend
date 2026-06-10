-- Check if pgvector is installed and meets version requirement for HNSW
DO $$
DECLARE
    vector_version text;
BEGIN
    SELECT extversion INTO vector_version FROM pg_extension WHERE extname = 'vector';
    IF vector_version IS NULL THEN
        RAISE EXCEPTION 'pgvector extension is not installed.';
    ELSIF string_to_array(vector_version, '.')::int[] < ARRAY[0, 5, 0] THEN
        RAISE EXCEPTION 'pgvector version must be >= 0.5.0 for HNSW index support. Current version is %', vector_version;
    END IF;
END $$;
