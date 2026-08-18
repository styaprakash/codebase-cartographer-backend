# Redis Streams Migration: Before & After Walkthrough

This document serves as a detailed historical record of how the queueing infrastructure was migrated from **Redis Lists** (acting as simple queues) to **Redis Streams** (acting as an enterprise message broker with Consumer Groups). 

The goal of this migration was to adopt RabbitMQ-like features natively in Dragonfly/Redis, including exactly-once processing, native acknowledgments (`XACK`), and event-driven consumer scaling without maintaining new infrastructure.

---

## 1. Automated Consumer Group Initialization

### **The Target File:** `RedisConfig.java`

#### **Before:**
The Redis configuration simply initialized the `RedisConnectionFactory` and listened for the `ApplicationReadyEvent` to perform a simple `.ping()` to verify connectivity on startup.

#### **After (Now):**
We now guarantee that the Stream and its Consumer Group exist before any worker attempts to listen.
- On the `ApplicationReadyEvent`, the system executes an `XGROUP CREATE` command for both the `indexing-stream` and `incremental-indexing-stream`.
- This ensures that the `worker-group` is primed to start tracking offsets and pending messages (PEL) immediately.

---

## 2. Producer Logic (XADD and XACK)

### **The Target File:** `DragonflyQueueService.java`

#### **Before:**
The service relied entirely on Redis List operations to act as a queue:
- **Enqueueing:** Used `opsForList().leftPush()` to push raw strings to `indexing-queue`.
- **Dequeueing:** Used a blocking RPOPLPUSH pattern (`opsForList().rightPopAndLeftPush()`) to atomically move a job from the pending queue to a separate `indexing-processing-queue`.
- **Completion:** Manually called `opsForList().remove()` to delete the item from the processing queue.

#### **After (Now):**
The manual queue-shuffling logic has been entirely deleted in favor of native stream commands:
- **Enqueueing:** Uses `opsForStream().add()` to execute an `XADD` command. The payload is now sent as a structured dictionary (`MapRecord`) instead of a raw string.
- **Dequeueing:** *The dequeue method was completely deleted.* The system is now push-based (event-driven) rather than pull-based (polling).
- **Completion:** The `complete()` method now receives a `RecordId` and executes `opsForStream().acknowledge()` (`XACK`), directly telling the Consumer Group that the job was successful. It then deletes the message to keep the stream's memory footprint near zero.

---

## 3. Consumer Logic (Event-Driven Workers)

### **The Target Files:** `IndexingWorker.java` & `IncrementalIndexingWorker.java`

#### **Before:**
The background workers were highly manual and complex:
- They created their own raw Java `Thread`.
- They utilized an infinite `while(running)` polling loop that constantly blocked the thread waiting for `dragonflyQueueService.dequeue()`.
- They had to catch and swallow a myriad of exceptions like `QueryTimeoutException` (which happens when Lettuce's command timeout is exceeded during a blocking list pop).
- If the Redis connection died, they had to parse the exception strings to figure out if they should break the while loop.

#### **After (Now):**
The architecture is now purely event-driven using Spring Data Redis's `StreamMessageListenerContainer`.
- **No More While Loops:** The manual threads and `while(running)` loops have been completely deleted.
- **`StreamListener` Implementation:** The classes now implement the `StreamListener<String, MapRecord<String, String, String>>` interface.
- **Registration:** In `@PostConstruct`, they create a `StreamMessageListenerContainer` and register themselves to listen to the Stream under the `worker-group` consumer group.
- **The Magic:** Spring handles all of the background `XREADGROUP` blocking calls automatically. The absolute millisecond a job is `XADD`ed to the stream by the API Gateway, Spring invokes the `onMessage()` callback in the worker, passing the job data directly.
- **Error Handling:** The `onMessage` block executes the AST logic/LLM calls. In the `finally` block, it fires the `XACK`. If the JVM crashes mid-execution, the `XACK` is never fired, and the message safely remains in the stream's Pending Entries List (PEL) for self-healing.
