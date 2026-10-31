package com.java.doc_process.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.java.doc_process.exception.DocumentNotFoundException;
import com.java.doc_process.exception.QueuePublishException;
import com.java.doc_process.modal.FileMetadata;
import com.java.doc_process.model.DocumentEvent;

class DocumentServiceTest {

    private S3Service s3Service;
    private DynamoDBService dynamoDBService;
    private SQSService sqsService;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        s3Service = org.mockito.Mockito.mock(S3Service.class);
        dynamoDBService = org.mockito.Mockito.mock(DynamoDBService.class);
        sqsService = org.mockito.Mockito.mock(SQSService.class);
        documentService = new DocumentService(
                new DocumentValidator(1024),
                s3Service,
                dynamoDBService,
                sqsService,
                "configured-input-bucket");
    }

    @Test
    void uploadsPersistsPendingMetadataAndPublishesVersionedEvent() throws Exception {
        byte[] content = "sample text".getBytes(StandardCharsets.UTF_8);
        var response = documentService.accept(
                new MockMultipartFile("file", "report.txt", "text/plain", content));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.documentId()).isNotBlank();
        String inputKey = "input/" + response.documentId() + "/report.txt";

        verify(s3Service).upload("configured-input-bucket", inputKey, content, "text/plain");
        ArgumentCaptor<FileMetadata> metadata = ArgumentCaptor.forClass(FileMetadata.class);
        verify(dynamoDBService).create(metadata.capture());
        assertThat(metadata.getValue().getDocumentId()).isEqualTo(response.documentId());
        assertThat(metadata.getValue().getInputS3Key()).isEqualTo(inputKey);
        assertThat(metadata.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(metadata.getValue().getCreatedAt()).isNotBlank();
        assertThat(metadata.getValue().getUpdatedAt()).isNotBlank();

        ArgumentCaptor<DocumentEvent> event = ArgumentCaptor.forClass(DocumentEvent.class);
        verify(sqsService).publishMessage(event.capture());
        assertThat(event.getValue().version()).isEqualTo(DocumentEvent.CURRENT_VERSION);
        assertThat(event.getValue().documentId()).isEqualTo(response.documentId());
        assertThat(event.getValue().bucket()).isEqualTo("configured-input-bucket");
        assertThat(event.getValue().key()).isEqualTo(inputKey);
    }

    @Test
    void recordsFailedStateAndDoesNotReturnAcceptedWhenQueuePublishFails() throws Exception {
        doThrow(new RuntimeException("internal queue details"))
                .when(sqsService).publishMessage(any(DocumentEvent.class));

        assertThatThrownBy(() -> documentService.accept(
                new MockMultipartFile("file", "report.txt", "text/plain", "text".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(QueuePublishException.class);

        ArgumentCaptor<FileMetadata> metadata = ArgumentCaptor.forClass(FileMetadata.class);
        verify(dynamoDBService).create(metadata.capture());
        verify(dynamoDBService).markFailed(
                eq(metadata.getValue().getDocumentId()),
                eq("Could not enqueue document for processing"));
    }

    @Test
    void mapsPersistedMetadataToStatusAndReturnsNotFoundForUnknownIds() {
        FileMetadata metadata = new FileMetadata();
        metadata.setDocumentId("doc-1");
        metadata.setFileName("report.txt");
        metadata.setContentType("text/plain");
        metadata.setSize(11L);
        metadata.setStatus("COMPLETED");
        metadata.setInputS3Key("input/doc-1/report.txt");
        metadata.setOutputS3Key("output/doc-1/result.json");
        metadata.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z").toString());
        metadata.setUpdatedAt(Instant.parse("2026-01-01T00:01:00Z").toString());
        metadata.setProcessedAt(Instant.parse("2026-01-01T00:01:00Z").toString());
        when(dynamoDBService.findById("doc-1")).thenReturn(metadata);
        when(dynamoDBService.findById("missing")).thenReturn(null);

        var status = documentService.getStatus("doc-1");

        assertThat(status.documentId()).isEqualTo("doc-1");
        assertThat(status.status()).isEqualTo("COMPLETED");
        assertThat(status.outputKey()).isEqualTo("output/doc-1/result.json");
        assertThat(status.processedAt()).isEqualTo(Instant.parse("2026-01-01T00:01:00Z"));
        assertThatThrownBy(() -> documentService.getStatus("missing"))
                .isInstanceOf(DocumentNotFoundException.class);
    }
}
