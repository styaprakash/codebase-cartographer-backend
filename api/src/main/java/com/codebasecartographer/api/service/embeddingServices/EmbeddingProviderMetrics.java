package com.codebasecartographer.api.service.embeddingServices;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.codebasecartographer.api.enums.EmbeddingModel;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class EmbeddingProviderMetrics {
    // Tracks per-model metrics: latency, success, failure counts
    private final Map<EmbeddingModel, ModelMetrics> metricsMap =
            new ConcurrentHashMap<>();

    public void recordSuccess(EmbeddingModel model, long latencyMs) {
        getOrCreate(model).recordSuccess(latencyMs);
    }

    public void recordFailure(EmbeddingModel model) {
        getOrCreate(model).recordFailure();
    }

    public ModelMetrics getMetrics(EmbeddingModel model) {
        return metricsMap.getOrDefault(model, new ModelMetrics());
    }

    public ModelMetrics getOrCreate(EmbeddingModel model) {
        return metricsMap.computeIfAbsent(model, k -> new ModelMetrics());
    }

    @Data
    public static class ModelMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong totalFailures = new AtomicLong(0);
        private final AtomicLong totalLatencyMs = new AtomicLong(0);
        private final AtomicInteger activeJobs = new AtomicInteger(0);

        public void recordSuccess(long latencyMs) {
            totalRequests.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
            activeJobs.decrementAndGet();
        }

        public void recordFailure() {
            totalRequests.incrementAndGet();
            totalFailures.incrementAndGet();
            activeJobs.decrementAndGet();
        }

        public void markActive() {
            activeJobs.incrementAndGet();
        }

        public double getAvgLatencyMs() {
            long reqs = totalRequests.get() - totalFailures.get();
            return reqs > 0 ? (double) totalLatencyMs.get() / reqs : 0;
        }

        public double getFailureRate() {
            long total = totalRequests.get();
            return total > 0 ? (double) totalFailures.get() / total : 0;
        }
    }
}
