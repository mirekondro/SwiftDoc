package dk.easv.swiftdoc.model;

import java.time.LocalDateTime;

public class LogEntry {

    public enum Action { FILE_CREATED, FILE_DELETED }

    private final int logId;
    private final int userId;
    private final String username;
    private final Action action;
    private final int fileId;
    private final int documentId;
    private final LocalDateTime timestamp;

    public LogEntry(int logId, int userId, String username, Action action,
                    int fileId, int documentId, LocalDateTime timestamp) {
        this.logId = logId;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.fileId = fileId;
        this.documentId = documentId;
        this.timestamp = timestamp;
    }

    public int getLogId() { return logId; }
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public Action getAction() { return action; }
    public int getFileId() { return fileId; }
    public int getDocumentId() { return documentId; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
