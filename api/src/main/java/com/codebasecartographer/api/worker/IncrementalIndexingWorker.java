package com.codebasecartographer.api.worker;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import com.codebasecartographer.api.dto.IncrementalJobPayload;
import com.codebasecartographer.api.service.DragonflyQueueService;
import com.codebasecartographer.api.service.IndexingService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;

@Slf4j
@Component
@DependsOn({"redisConnectionFactory", "dragonflyQueueService"})
public class IncrementalIndexingWorker implements StreamListener<String, MapRecord<String, String, String>> {

    private final DragonflyQueueService dragonflyQueueService;
    private final IndexingService indexingService;
    private final ObjectMapper objectMapper;
    private final RedisConnectionFactory redisConnectionFactory;

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> listenerContainer;
    private Subscription subscription;

    public IncrementalIndexingWorker(DragonflyQueueService dragonflyQueueService,
                                     IndexingService indexingService,
                                     ObjectMapper objectMapper,
                                     RedisConnectionFactory redisConnectionFactory) {
        this.dragonflyQueueService = dragonflyQueueService;
        this.indexingService = indexingService;
        this.objectMapper = objectMapper;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @PostConstruct
    public void startWorker() {
        log.info("Starting Stream Listener for IncrementalIndexingWorker...");
        
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options = 
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofSeconds(1))
                .build();
                
        this.listenerContainer = StreamMessageListenerContainer.create(redisConnectionFactory, options);
        
        this.subscription = listenerContainer.receive(
            Consumer.from(DragonflyQueueService.CONSUMER_GROUP, "incremental-worker-node-1"),
            StreamOffset.create(DragonflyQueueService.INCREMENTAL_STREAM, ReadOffset.lastConsumed()),
            this
        );
        
        this.listenerContainer.start();
        log.info("IncrementalIndexingWorker stream listener started successfully");
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String payloadJson = message.getValue().get("payload");
        if (payloadJson == null) {
            log.warn("Received incremental stream message without payload: {}", message.getId());
            dragonflyQueueService.completeIncremental(message.getId());
            return;
        }

        try {
            log.info("Processing Incremental Job (Stream Record ID: {})", message.getId());
            IncrementalJobPayload payload = objectMapper.readValue(payloadJson, IncrementalJobPayload.class);
            indexingService.processIncrementalJob(payload);
        } catch (Exception e) {
            log.error("Failed to process incremental job", e);
        } finally {
            dragonflyQueueService.completeIncremental(message.getId());
        }
    }

    @PreDestroy
    public void stopWorker() {
        log.info("Shutting down IncrementalIndexingWorker...");
        if (this.listenerContainer != null) {
            this.listenerContainer.stop();
        }
        log.info("IncrementalIndexingWorker shut down complete");
    }
}
