package com.java.doc_process.exception;

public class QueuePublishException extends RuntimeException {
    public QueuePublishException(Throwable cause) {
        super("Could not enqueue document for processing", cause);
    }
}
