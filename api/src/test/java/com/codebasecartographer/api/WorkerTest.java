package com.codebasecartographer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.codebasecartographer.api.service.RepoService;
import com.codebasecartographer.api.enums.RepositoryStatus;

@SpringBootTest
public class WorkerTest {
    @Autowired
    private RepoService repoService;

    @Test
    public void testUpdateStatus() {
        try {
            repoService.updateStatus("7d19f76c-76bf-41fe-81b0-55c9d5a75caf", RepositoryStatus.INDEXING);
            System.out.println("SUCCESS: updateStatus worked");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("FAILED_EXCEPTION: " + e.getMessage());
        }
    }
}
