# Redis / Dragonfly Architecture and Usage

## Overview

This document outlines how Redis (and Dragonfly, which serves as a highly performant, drop-in Redis replacement) is integrated into the Codebase Cartographer backend architecture. It details the specific use cases, the files involved, and their respective responsibilities within the system.

## Core System Roles

Currently, Redis/Dragonfly serves two primary foundational roles in the application:
1. **Reliable Task Queuing**: Managing background jobs for both full and incremental codebase indexing using Redis Lists.
2. **Data Caching**: Caching expensive query results to improve application response times, lower latency, and reduce redundant database or LLM calls using Redis Strings with TTLs (Time-to-Live).

---

## Why Use Dragonfly As A Queue?

While Dragonfly is primarily known as an in-memory datastore, it is heavily utilized in this system as a lightweight messaging queue. The primary benefits include:

1. **Asynchronous Processing (No Timeouts)**: Codebase indexing (downloading zips, AST parsing, chunking, hitting LLM API rate limits) is a slow, I/O and CPU-bound process. Moving this to a queue allows the HTTP request to immediately return a `202 Accepted` response instead of blocking and eventually timing out the client's browser.
2. **Fault Tolerance and Reliability**: By leveraging the `RPOPLPUSH` (Reliable Queue) pattern, if a worker process crashes mid-job (e.g., due to a server restart or Out of Memory error), the job is not lost. It remains preserved in a `PROCESSING_QUEUE` where it can be safely recovered and retried.
3. **Concurrency Control and Throttling**: A queue acts as a natural buffer. If a massive influx of indexing requests arrives simultaneously, the queue absorbs the spike while a fixed pool of background workers processes them at a controlled pace. This prevents the server from exhausting its resources and protects external LLM APIs from being overwhelmed.
4. **Future Scalability (Microservices Boundary)**: As the monolith grows, the queue establishes a clear architectural seam. It sets the stage for decoupling the lightweight API Gateway from the resource-intensive Headless Workers across different physical servers.

---

## File Breakdown and Responsibilities

### 1. Configuration Layer

#### [`application.yaml`](../src/main/resources/application.yaml)
- **Role**: Defines the externalized environment properties. It configures the Redis host, port, timeout settings, and specifies the use of the `lettuce` client driver for asynchronous and reactive connections.

#### [`RedisConfig.java`](../src/main/java/com/codebasecartographer/api/config/RedisConfig.java)
- **Role**: The core Spring configuration class for caching and datastore interactions.
  - Initializes the `RedisConnectionFactory`.
  - Provides custom `RedisTemplate` beans tailored for different serialization needs (e.g., `GenericJackson2JsonRedisSerializer` for JSON objects and `RedisSerializer.byteArray()` for raw binary caching).
  - Includes an `ApplicationReadyEvent` listener that acts as a health check to verify Redis connectivity immediately on application startup.

### 2. Task Queuing & Background Workers

#### [`DragonflyQueueService.java`](../src/main/java/com/codebasecartographer/api/service/DragonflyQueueService.java)
- **Role**: Serves as the centralized queue manager. It leverages Redis Lists to implement reliable queueing mechanisms.
  - **Reliable Queues**: Uses the Reliable Queue pattern (e.g., `rightPopAndLeftPush`) to safely move jobs from pending queues (`INDEXING_QUEUE`, `INCREMENTAL_QUEUE`) to active queues (`PROCESSING_QUEUE`, `INCREMENTAL_PROCESSING_QUEUE`).
  - **Fault Tolerance**: Ensures that if a worker process crashes mid-execution, the job is not lost and remains in the processing queue for recovery.

#### [`IndexingWorker.java`](../src/main/java/com/codebasecartographer/api/worker/IndexingWorker.java) & [`IncrementalIndexingWorker.java`](../src/main/java/com/codebasecartographer/api/worker/IncrementalIndexingWorker.java)
- **Role**: Dedicated, continuous background threads that poll the queues managed by `DragonflyQueueService`.
  - They depend directly on the `redisConnectionFactory` and gracefully shut down or pause if the Redis connection is lost.
  - They pull repository IDs or webhook payloads off the queues, perform the heavy lifting (AST parsing, chunking, and hitting LLM embedding endpoints), and ultimately acknowledge successful completion by removing the item from the processing list.

### 3. Caching Layer

#### [`QueryCacheService.java`](../src/main/java/com/codebasecartographer/api/service/QueryCacheService.java)
- **Role**: Provides a high-performance caching abstraction over Redis.
  - Uses `RedisTemplate.opsForValue()` to store and retrieve binary data (`byte[]`) representing cached query results.
  - Implements a caching strategy with automatic expiration (TTL) to ensure stale data is evicted and memory is optimized.

---

## Future Trajectory (Distributed Architecture)

As the application scales, the reliance on Dragonfly will expand to facilitate breaking the Modular Monolith into discrete, independently deployable services (API Gateway vs. Headless Workers). As outlined in the `future_implementation_plan.md`, upcoming features include:
- **Redis Streams (`XADD`/`XREADGROUP`)**: Migrating from Redis Lists to Streams to support Consumer Groups and exactly-once processing semantics for indexing jobs.
- **Redis Pub/Sub**: Utilizing publish/subscribe channels for real-time Server-Sent Events (SSE). Headless workers will `PUBLISH` indexing progress, and the API Gateway will `SUBSCRIBE` to forward these events to active browser clients.
