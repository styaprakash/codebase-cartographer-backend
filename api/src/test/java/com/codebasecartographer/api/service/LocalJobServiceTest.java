package com.codebasecartographer.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.codebasecartographer.api.entity.LocalJob;
import com.codebasecartographer.api.enums.LocalJobStatus;
import com.codebasecartographer.api.exception.ServiceUnavailableException;
import com.codebasecartographer.api.grpc.proto.ServerMessage;
import com.codebasecartographer.api.grpc.registry.WorkerConnection;
import com.codebasecartographer.api.grpc.registry.WorkerRegistry;
import com.codebasecartographer.api.repository.LocalJobRepository;

@ExtendWith(MockitoExtension.class)
public class LocalJobServiceTest {

    @Mock
    private LocalJobRepository localJobRepository;

    @Mock
    private WorkerRegistry workerRegistry;

    @InjectMocks
    private LocalJobService localJobService;

    private WorkerConnection mockConnection;

    @BeforeEach
    void setUp() {
        mockConnection = mock(WorkerConnection.class);
    }

    @Test
    void submitJob_Success() {
        when(localJobRepository.save(any(LocalJob.class))).thenAnswer(i -> {
            LocalJob j = i.getArgument(0);
            if (j.getId() == null) {
                j.setId("test-job-id");
            }
            return j;
        });
        when(workerRegistry.getConnectionsForUser("user1")).thenReturn(List.of(mockConnection));
        when(mockConnection.getWorkerId()).thenReturn("worker1");
        when(workerRegistry.sendToWorker(eq("worker1"), any(ServerMessage.class))).thenReturn(true);

        LocalJob job = localJobService.submitJob("user1", "llama3", "Hello");

        assertNotNull(job);
        assertEquals(LocalJobStatus.DISPATCHED, job.getStatus());
        assertEquals("worker1", job.getWorkerId());
    }

    @Test
    void submitJob_NoWorker_ThrowsException() {
        when(localJobRepository.save(any(LocalJob.class))).thenAnswer(i -> {
            LocalJob j = i.getArgument(0);
            if (j.getId() == null) {
                j.setId("test-job-id");
            }
            return j;
        });
        when(workerRegistry.getConnectionsForUser("user1")).thenReturn(Collections.emptyList());

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class, () -> {
            localJobService.submitJob("user1", "llama3", "Hello");
        });
        
        assertEquals("No connected local compute worker found.", ex.getMessage());
    }

    @Test
    void handleJobAccepted_Success() {
        LocalJob job = LocalJob.builder().id("job1").workerId("worker1").status(LocalJobStatus.DISPATCHED).build();
        when(localJobRepository.findById("job1")).thenReturn(Optional.of(job));

        localJobService.handleJobAccepted("worker1", "job1");

        assertEquals(LocalJobStatus.ACCEPTED, job.getStatus());
        verify(localJobRepository).save(job);
    }

    @Test
    void handleJobAccepted_Duplicate_Ignores() {
        LocalJob job = LocalJob.builder().id("job1").workerId("worker1").status(LocalJobStatus.ACCEPTED).build();
        when(localJobRepository.findById("job1")).thenReturn(Optional.of(job));

        localJobService.handleJobAccepted("worker1", "job1");

        // Status should remain ACCEPTED, and save should not be called again
        assertEquals(LocalJobStatus.ACCEPTED, job.getStatus());
        verify(localJobRepository, never()).save(any(LocalJob.class));
    }

    @Test
    void handleWorkerDisconnect_FailsActiveJobs() {
        LocalJob job1 = LocalJob.builder().id("job1").workerId("worker1").status(LocalJobStatus.DISPATCHED).build();
        LocalJob job2 = LocalJob.builder().id("job2").workerId("worker1").status(LocalJobStatus.ACCEPTED).build();
        
        when(localJobRepository.findByWorkerIdAndStatusIn(eq("worker1"), any(List.class)))
            .thenReturn(List.of(job1, job2));

        localJobService.handleWorkerDisconnect("worker1");

        assertEquals(LocalJobStatus.FAILED, job1.getStatus());
        assertEquals("Worker disconnected.", job1.getError());
        assertEquals(LocalJobStatus.FAILED, job2.getStatus());
        
        verify(localJobRepository).save(job1);
        verify(localJobRepository).save(job2);
    }
}
