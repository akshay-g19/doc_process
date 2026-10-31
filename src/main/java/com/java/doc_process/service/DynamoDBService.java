package com.java.doc_process.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.java.doc_process.modal.FileMetadata;
import com.java.doc_process.repository.FileMetadataRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DynamoDBService {

    private final FileMetadataRepository repository;

    public DynamoDBService(FileMetadataRepository repository) {
        this.repository = repository;
    }

    public void create(FileMetadata metadata) {
        repository.save(metadata);
        log.info("Document {} state={}", metadata.getDocumentId(), metadata.getStatus());
    }

    public FileMetadata findById(String documentId) {
        return repository.findById(documentId);
    }

    public void markProcessing(String documentId) {
        update(documentId, "PROCESSING", null, null, false);
    }

    public void markCompleted(String documentId, String outputKey) {
        update(documentId, "COMPLETED", outputKey, null, true);
    }

    public void markFailed(String documentId, String safeError) {
        update(documentId, "FAILED", null, safeError, true);
    }

    private void update(String documentId, String status, String outputKey, String error, boolean processed) {
        FileMetadata metadata = repository.findById(documentId);
        if (metadata == null) {
            throw new IllegalStateException("Document metadata is missing");
        }

        Instant now = Instant.now();
        metadata.setStatus(status);
        metadata.setUpdatedAt(now.toString());
        metadata.setError(error);
        if (outputKey != null) {
            metadata.setOutputS3Key(outputKey);
        }
        if (processed) {
            metadata.setProcessedAt(now.toString());
        }
        repository.save(metadata);
        log.info("Document {} state={}", documentId, status);
    }
}
