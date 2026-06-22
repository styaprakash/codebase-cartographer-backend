# Chat API & RAG Walkthrough

We've successfully built the new RAG-based Chat API using the Factory/Strategy pattern for dynamic LLM routing, mirroring the embedding architecture. 

## What Was Built

### 1. The Generative LLM Factory
- **[GenerativeLlmModel](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/enums/GenerativeLlmModel.java)**: Created an enum specifying supported models (`OLLAMA_LLAMA3` and `OPENAI_GPT4`).
- **[GenerativeLlmProvider](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/service/llmServices/providers/GenerativeLlmProvider.java)**: The core contract `generateResponse(String prompt)` for all LLM integrations.
- **[LlmProviderFactory](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/service/llmServices/factory/LlmProviderFactory.java)**: Spring DI automatically discovers beans implementing the provider interface, keeping the code scalable.

### 2. Resilience-Backed Providers
We implemented real HTTP calls via Spring's `RestClient` and added `resilience4j` into your `pom.xml`.
- **[OllamaLlmProvider](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/service/llmServices/providers/OllamaLlmProvider.java)**: Fully configured with `@Bulkhead(name = "local-ollama")`.
- **[OpenAiLlmProvider](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/service/llmServices/providers/OpenAiLlmProvider.java)**: Secured with `@RateLimiter(name = "premium-api")` and `@Retry(name = "premium-api")`.

### 3. Vector Database Retrieval
- **[CodeSearchService](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/service/CodeSearchService.java)**: Takes the user query, delegates embedding generation to the dynamic embedding factory (based on the repo's registered embedding model), and queries the vector DB via `findTopSimilarChunks`.

### 4. RAG Prompt Assembly & Chat Endpoint
- **[LlmGenerationService](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/service/LlmGenerationService.java)**: Orchestrates the pipeline: fetches context chunks, constructs a massive prompt with clear code markdown and filenames, dynamically loads the correct generative provider, and maps citations for the output.
- **[ChatController](file:///home/satya/Documents/Projects/codebase-cartographer-backend/codebase-cartographer-backend/api/src/main/java/com/codebasecartographer/api/controller/ChatController.java)**: The REST endpoint (`POST /api/chat/{repoId}`).

> [!TIP]
> Make sure your `application.yml` has the corresponding properties defined for the Resilience4j instances (`local-ollama` and `premium-api`).

## Verification
- We fixed an initial compile failure by safely adding the `resilience4j-spring-boot3` and `spring-boot-starter-aop` dependencies to the POM.
- Compilation is now successful via `mvn clean compile`.
