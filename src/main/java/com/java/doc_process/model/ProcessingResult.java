package com.java.doc_process.model;

import java.time.Instant;

public record ProcessingResult(
        int version,
        String documentId,
        Instant processedAt,
        String extractedText,
        int characterCount,
        int wordCount) {
}
