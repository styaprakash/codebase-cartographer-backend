# Production & Deployment Guide: Enterprise Rate Limiting

This document outlines the architectural patterns used in the Codebase Cartographer backend to handle API rate limits across different LLM providers, and provides instructions on how to scale the application for production.

## 1. Dynamic Rate Limiting (Resilience4j)

The backend uses Resilience4j to throttle outgoing requests and prevent `429 Too Many Requests` errors. To avoid hardcoding free-tier limits into the production codebase, the rate limiter in `application.yaml` is controlled by environment variables.

### Local Development (Free Tier)
By default, if no environment variables are provided, the backend falls back to Google's Free Tier limits:
- **15 requests per 60 seconds**

### Going Live (Paid Tier)
When you deploy the application to production and upgrade to a paid Gemini (or OpenRouter) API tier, **you do not need to change any code**. Simply inject the following environment variables into your production environment (e.g., via Docker Compose, Kubernetes ConfigMap, or standard Linux env vars):

```bash
# Example for a paid tier allowing 1000 requests per second
API_RATE_LIMIT=1000
API_RATE_REFRESH=1s
```
The backend will automatically detect these variables on startup and unleash full production speeds.

## 2. Provider-Specific Batch Sizing

Different LLM providers have vastly different optimal batch sizes:
- **Local Ollama:** Needs a small batch size (e.g., `5`) to prevent local GPU out-of-memory crashes and timeouts.
- **Cloud APIs (Gemini):** Can handle massive payloads (e.g., `100` chunks per request via `batchEmbedContents`), drastically reducing network overhead.

The `EmbeddingProvider` interface requires each provider to explicitly define its `getOptimalBatchSize()`. The core `IndexingService` dynamically respects this size. 

### Why this matters for production
By batching 100 chunks into a single HTTP request for Gemini, a repository with 1,000 files will only require ~10 API calls. This not only makes indexing incredibly fast but also saves massive amounts of network latency and significantly reduces the chance of ever hitting a rate limit, even on the free tier.
