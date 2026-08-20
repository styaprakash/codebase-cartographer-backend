package com.codebasecartographer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.codebasecartographer.api.service.IndexingService;
import com.codebasecartographer.api.service.RepoService;
import com.codebasecartographer.api.enums.RepositoryStatus;

@SpringBootTest
public class FullIndexingTest {
    @Autowired
    private IndexingService indexingService;
    
    @Autowired
    private RepoService repoService;

    @Test
    public void testTriggerAndWorker() {
        try {
            System.out.println("Starting Trigger...");
            indexingService.triggerIndexing("7d19f76c-76bf-41fe-81b0-55c9d5a75caf", null, null, null, null, "OPENROUTER_QWEN_EMBEDDING_1536");
            System.out.println("Trigger done. Calling updateStatus...");
            repoService.updateStatus("7d19f76c-76bf-41fe-81b0-55c9d5a75caf", RepositoryStatus.INDEXING);
            System.out.println("updateStatus done.");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("FAILED_EXCEPTION: " + e.getMessage());
        }
    }
}
