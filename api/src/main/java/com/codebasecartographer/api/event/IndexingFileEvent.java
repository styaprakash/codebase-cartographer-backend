package com.codebasecartographer.api.event;

import org.springframework.context.ApplicationEvent;

public class IndexingFileEvent extends ApplicationEvent {
    private final String repoId;
    private final String status;
    private final String filePath;
    private final int progress;
    private final int totalFiles;

    public IndexingFileEvent(Object source, String repoId, String status, String filePath, int progress, int totalFiles) {
        super(source);
        this.repoId = repoId;
        this.status = status;
        this.filePath = filePath;
        this.progress = progress;
        this.totalFiles = totalFiles;
    }

    public String getRepoId() { return repoId; }
    public String getStatus() { return status; }
    public String getFilePath() { return filePath; }
    public int getProgress() { return progress; }
    public int getTotalFiles() { return totalFiles; }
}
