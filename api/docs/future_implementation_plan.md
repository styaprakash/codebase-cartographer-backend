Codebase Cartographer: Distributed Architecture Migration Plan

Current State: Phase 1 (Modular Monolith)

Currently, Codebase Cartographer operates as a Modular Monolith.

Compute: The API server, SSE connection handling, GitHub Zip streaming, and Vector Chunking/Embedding all occur within a single Spring Boot JVM.

Queuing: Background tasks are managed via in-memory java.util.concurrent.BlockingQueue and ExecutorService.

Eventing: Real-time UI updates (SSE) are handled via internal Spring ApplicationEventPublisher.

Why this approach? This design prioritizes time-to-market, simplifies DevOps (single container deployment), and avoids the operational overhead of managing distributed tracing and external message brokers while user traffic is in its early stages.

When to Trigger Phase 2 & 3 (The Scaling Thresholds)

We will migrate away from the Modular Monolith only when the following thresholds are consistently met:

CPU/Memory Starvation: The AST chunking and embedding processes cause CPU spikes > 80%, resulting in degraded performance (latency) for standard API/web users.

Horizontal Scaling Discrepancy: The background indexing tasks require drastically more compute power than the web-facing API, meaning we need to scale "Workers" without unnecessarily scaling "Gateways".

Resilience Needs: If the monolithic server restarts during a 10-minute indexing job, that job's state is lost. If users demand guaranteed job completion, we must externalize state.

Phase 2: Incremental Webhook Indexing (CodeWiki Model)

Before splitting the servers, we will optimize compute usage by reducing full-repository re-indexes.

Trigger: When users require their indexed codebase to stay real-time synchronized with their active GitHub work.

Implementation: Introduce a GithubWebhookController. Instead of fetching the entire Archive Zip, the system will listen for GitHub push events to the default branch, parse the added, modified, and removed arrays, and only process those specific files.

Phase 3: The Distributed Handoff (API Gateway + Worker Nodes)

When the compute thresholds are breached, we will cleave the Modular Monolith into two independent deployable services using our existing Dragonfly (Redis) infrastructure.

The API Gateway

Role: Handles HTTP requests, Authentication, Webhooks, and maintains open SSE connections with the frontend browser.

Job Delegation: When an indexing request arrives, the Gateway executes XADD to push a job payload to a Redis Stream (cartographer:indexing_jobs).

SSE Reception: The Gateway subscribes to Redis Pub/Sub (cartographer:sse:repo:{repoId}). When it hears an event, it forwards it to the active browser SSE connection.

The Worker Nodes (Headless)

Role: Pure compute. No web server. Dedicated to streaming zips from GitHub, running AST logic, and hitting the LLM embedding endpoints.

Job Consumption: Workers use XREADGROUP to safely pull jobs from the Redis Stream, ensuring exactly-once processing via Consumer Groups. If a worker crashes, the Pending Entries List (PEL) allows another worker to claim the job.

SSE Broadcasting: As files are completed, the Worker executes PUBLISH to the Redis Pub/Sub channel to update the Gateway.

Migration Safety

Because Phase 1 uses a Modular Monolith (strict separation of Gateway and Worker packages internally), Phase 3 is a deployment and configuration change, not a ground-up rewrite.