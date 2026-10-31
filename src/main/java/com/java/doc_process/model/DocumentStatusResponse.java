package com.java.doc_process.model;

import java.time.Instant;

public record DocumentStatusResponse(
        String documentId,
        String fileName,
        String contentType,
        Long size,
        String status,
        String inputKey,
        String outputKey,
        Instant createdAt,
        Instant updatedAt,
        Instant processedAt,
        String error) {
}
