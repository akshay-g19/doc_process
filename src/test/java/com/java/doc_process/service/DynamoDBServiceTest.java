package com.java.doc_process.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.java.doc_process.modal.FileMetadata;
import com.java.doc_process.repository.FileMetadataRepository;

class DynamoDBServiceTest {

    private FileMetadataRepository repository;
    private DynamoDBService service;
    private FileMetadata metadata;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(FileMetadataRepository.class);
        service = new DynamoDBService(repository);
        metadata = new FileMetadata();
        metadata.setDocumentId("doc-1");
        metadata.setStatus("PENDING");
        metadata.setCreatedAt("2026-01-01T00:00:00Z");
        when(repository.findById("doc-1")).thenReturn(metadata);
    }

    @Test
    void advancesProcessingToCompletedAndPreservesCreatedTime() {
        service.markProcessing("doc-1");
        assertThat(metadata.getStatus()).isEqualTo("PROCESSING");
        assertThat(metadata.getError()).isNull();

        service.markCompleted("doc-1", "output/doc-1/result.json");

        assertThat(metadata.getStatus()).isEqualTo("COMPLETED");
        assertThat(metadata.getOutputS3Key()).isEqualTo("output/doc-1/result.json");
        assertThat(metadata.getProcessedAt()).isNotBlank();
        assertThat(metadata.getUpdatedAt()).isNotBlank();
        assertThat(metadata.getCreatedAt()).isEqualTo("2026-01-01T00:00:00Z");
        verify(repository, org.mockito.Mockito.times(2)).save(metadata);
    }

    @Test
    void recordsSafeFailureDetailsAndProcessedTimestamp() {
        service.markFailed("doc-1", "Processing failed (IllegalStateException)");

        assertThat(metadata.getStatus()).isEqualTo("FAILED");
        assertThat(metadata.getError()).isEqualTo("Processing failed (IllegalStateException)");
        assertThat(metadata.getProcessedAt()).isNotBlank();
        verify(repository).save(metadata);
    }
}
