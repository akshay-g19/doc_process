package com.java.doc_process.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.java.doc_process.model.DocumentEvent;
import com.java.doc_process.model.ProcessingResult;

@Component
public class DocumentTransformer {

    public ProcessingResult transform(String documentId, byte[] input) {
        String text = new String(input, StandardCharsets.UTF_8);
        int characterCount = text.codePointCount(0, text.length());
        int wordCount = text.isBlank() ? 0 : text.strip().split("\\s+").length;
        return new ProcessingResult(
                DocumentEvent.CURRENT_VERSION,
                documentId,
                Instant.now(),
                text,
                characterCount,
                wordCount);
    }
}
