package com.java.doc_process.model;

public record DocumentEvent(
        int version,
        String documentId,
        String bucket,
        String key,
        String contentType) {

    public static final int CURRENT_VERSION = 1;
}
