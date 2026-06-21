package com.codebasecartographer.api.event;

import org.springframework.context.ApplicationEvent;
import com.codebasecartographer.api.enums.RepositoryStatus;

public class IndexingStatusEvent extends ApplicationEvent {
    private final String repoId;
    private final RepositoryStatus status;
    private final String errorMessage;

    public IndexingStatusEvent(Object source, String repoId, RepositoryStatus status, String errorMessage) {
        super(source);
        this.repoId = repoId;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public String getRepoId() { return repoId; }
    public RepositoryStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}
