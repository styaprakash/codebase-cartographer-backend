package com.codebasecartographer.api.service;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DragonflyQueueService {
    private static final String INDEXING_QUEUE = "indexing-queue";
    private static final String PROCESSING_QUEUE = "indexing-processing-queue";

    private final RedisTemplate<String, Object> redisTemplate;

    //constructor injection
    public DragonflyQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //Push the job into the queue
    public void enqueue(String repoId){
        redisTemplate.opsForList().leftPush(INDEXING_QUEUE, repoId);
    }

    //Pull the job from queue using the Reliable Queue Pattern (BRPOPLPUSH)
    public String dequeue(){
        return (String)redisTemplate.opsForList().rightPopAndLeftPush(
                INDEXING_QUEUE, PROCESSING_QUEUE, Duration.ofSeconds(2));
    } 

    //Remove the job from the processing queue once complete
    public void complete(String repoId){
        redisTemplate.opsForList().remove(PROCESSING_QUEUE, 1, repoId);
    }
}
