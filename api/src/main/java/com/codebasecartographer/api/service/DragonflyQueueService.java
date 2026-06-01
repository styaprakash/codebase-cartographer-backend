package com.codebasecartographer.api.service;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DragonflyQueueService {
    private static final String INDEXING_QUEUE = "indexing-queue";

    private final RedisTemplate<String, Object> redisTemplate;

    //constructor injection
    public DragonflyQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //Push the job into the queue
    public void enqueue(String repoId){
        redisTemplate.opsForList().leftPush(INDEXING_QUEUE, repoId);
    }

    //Pull the job from queue
    public String dequeue(){
        return (String)redisTemplate.opsForList().rightPop(INDEXING_QUEUE, Duration.ofSeconds(2));
    } 
}
