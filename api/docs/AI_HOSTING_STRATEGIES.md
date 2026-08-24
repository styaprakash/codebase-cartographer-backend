# Production AI Hosting Strategies

When taking Codebase Cartographer live, the architecture you choose to run your embedding and LLM models dictates your application's speed, reliability, and most importantly, your monthly server costs. 

Because generating AI embeddings requires massive mathematical computation, running models on your main Spring Boot web server (like we do locally with Ollama) will cause the web server to freeze and crash under user load.

This document outlines the three industry-standard approaches for hosting AI in production, ranked from the most budget-friendly to the most advanced.

---

## Approach 1: Managed APIs (Recommended for Startups)

Instead of managing any GPUs or servers yourself, you offload all AI computation to established providers like Google (Gemini), OpenAI, or OpenRouter.

### How it works
Your Spring Boot backend sends text over the internet to the provider, and they return the mathematical vectors.

### Setup Instructions
1. **Acquire API Keys:** Sign up for Google AI Studio or OpenRouter.
2. **Configure Environment:** Pass your keys into your production environment variables (e.g., `GEMINI_API_KEY`).
3. **Set Rate Limits:** Use the environment variables we built into `application.yaml` to unleash production speeds without changing code:
   ```bash
   API_RATE_LIMIT=1000
   API_RATE_REFRESH=1s
   ```

### Cost & Viability
* **Cost:** Extremely cheap. API providers charge fractions of a penny per 1 Million tokens. You only pay for exactly what you use. (Google Gemini currently offers a generous free tier).
* **Pros:** Zero maintenance, infinite auto-scaling, $0 fixed monthly server costs, no complex GPU management.
* **Cons:** Data privacy (you are sending code to third parties), subject to provider rate limits.

---

## Approach 2: Serverless GPUs (The Middle Ground)

If you have strict data privacy requirements and *must* use open-source models (like Qwen), but you have budget constraints, Serverless GPUs are the answer.

### How it works
Providers like **RunPod**, **Modal**, or **Baseten** allow you to rent Cloud GPUs by the exact second. Your GPU server "sleeps" (costing $0) when no users are active. When an indexing job hits your Redis queue, the GPU wakes up, processes the embeddings, and goes back to sleep.

### Setup Instructions
1. **Choose a Provider:** Create an account on RunPod or Modal.
2. **Deploy an Inference Engine:** Do not deploy Ollama. Deploy a production inference engine like **vLLM** or **Hugging Face TGI (Text Generation Inference)** via a Docker container on the serverless platform.
3. **Configure Backend:** Update your Spring Boot `application.yaml` (or custom provider class) to point its `baseUrl` away from `localhost:11434` to your new secure Serverless URL endpoint.

### Cost & Viability
* **Cost:** Cheap to Moderate. You pay roughly $0.20 to $0.50 per hour, but *only for the exact seconds the GPU is actively computing*.
* **Pros:** 100% data privacy (you own the model), no fixed monthly costs, auto-scales to zero.
* **Cons:** "Cold starts" (the GPU might take 10-20 seconds to wake up for the first request of the day).

---

## Approach 3: Dedicated 24/7 Inference Servers (Enterprise Scale)

This is the traditional architecture for large, well-funded companies with constant, non-stop user traffic.

### How it works
You rent a virtual machine packed with NVIDIA GPUs (like A10G or A100) on AWS, Google Cloud, or Lambda Cloud, and leave it running 24 hours a day, 7 days a week.

### Setup Instructions
1. **Provision a VM:** Rent a GPU instance (e.g., `g5.xlarge` on AWS).
2. **Install Drivers:** Install NVIDIA CUDA drivers.
3. **Deploy vLLM/TGI:** Run your inference engine via Docker Compose.
4. **Load Balancing:** As traffic grows, you provision multiple GPU servers and put an NGINX load balancer in front of them. Your Spring Boot backend sends requests to the load balancer, which distributes them across your GPU cluster.

### Cost & Viability
* **Cost:** Very Expensive. A single basic GPU server will cost **$400 to $1,000+ per month** in fixed costs, regardless of whether users are active or not.
* **Pros:** Absolute lowest latency (no cold starts), complete control over infrastructure, predictable throughput.
* **Cons:** High fixed monthly costs, complex DevOps maintenance, requires manual scaling.

---

## Summary Recommendation for Codebase Cartographer

When going Live, **start with Approach 1 (Managed APIs)**. Your Spring Boot backend is already heavily optimized for API batching and rate-limiting. This will give you the fastest time-to-market with the lowest financial risk.

If a large enterprise client demands that their code never leaves your infrastructure, deploy a separate instance of your application using **Approach 2 (Serverless GPUs)** specifically for them.
