package com.codebasecartographer.api.service;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class DragonflyQueueService {
    public static final String INDEXING_STREAM = "indexing-stream";
    public static final String INCREMENTAL_STREAM = "incremental-indexing-stream";
    
    public static final String CONSUMER_GROUP = "worker-group";

    private final RedisTemplate<String, Object> redisTemplate;

    public DragonflyQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void enqueue(String repoId){
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in(INDEXING_STREAM)
                .ofMap(Collections.singletonMap("repoId", repoId));
        redisTemplate.opsForStream().add(record);
    }

    public void complete(RecordId recordId){
        redisTemplate.opsForStream().acknowledge(INDEXING_STREAM, CONSUMER_GROUP, recordId);
        redisTemplate.opsForStream().delete(INDEXING_STREAM, recordId);
    }

    public void enqueueIncremental(String payloadJson) {
        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .in(INCREMENTAL_STREAM)
                .ofMap(Collections.singletonMap("payload", payloadJson));
        redisTemplate.opsForStream().add(record);
    }

    public void completeIncremental(RecordId recordId) {
        redisTemplate.opsForStream().acknowledge(INCREMENTAL_STREAM, CONSUMER_GROUP, recordId);
        redisTemplate.opsForStream().delete(INCREMENTAL_STREAM, recordId);
    }
}
