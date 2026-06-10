package com.codebasecartographer.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Service
public class QueryCacheService {

    private static final Logger log = LoggerFactory.getLogger(QueryCacheService.class);
    private static final int CACHE_TTL_HOURS = 24;
    private static final String CACHE_KEY_PREFIX = "query:embedding:";

    private final RedisTemplate<String, byte[]> redisTemplate;

    public QueryCacheService(RedisTemplate<String, byte[]> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public float[] getCachedEmbedding(String query) {
        try {
            String key = generateCacheKey(query);
            byte[] data = redisTemplate.opsForValue().get(key);
            if (data != null) {
                log.info("CACHE HIT for query: {}", query);
                return deserializeFloatArray(data);
            }
            log.info("CACHE MISS for query: {}", query);
            return null;
        } catch (Exception e) {
            log.warn("Cache retrieval failed for query '{}': {}. Falling through to Ollama.", query, e.getMessage());
            return null;
        }
    }

    public void cacheEmbedding(String query, float[] embedding) {
        try {
            String key = generateCacheKey(query);
            byte[] data = serializeFloatArray(embedding);
            redisTemplate.opsForValue().set(key, data, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.debug("Cached embedding for query: {}", query);
        } catch (Exception e) {
            log.warn("Failed to cache embedding for query '{}': {}", query, e.getMessage());
        }
    }

    private String generateCacheKey(String query) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(query.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return CACHE_KEY_PREFIX + hexString;
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, falling back to hashCode-based key");
            return CACHE_KEY_PREFIX + Math.abs(query.trim().toLowerCase().hashCode());
        }
    }

    private byte[] serializeFloatArray(float[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(data.length * 4);
        buffer.asFloatBuffer().put(data);
        return buffer.array();
    }

    private float[] deserializeFloatArray(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        float[] result = new float[data.length / 4];
        buffer.asFloatBuffer().get(result);
        return result;
    }
}
