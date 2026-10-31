package com.java.doc_process.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.java.doc_process.exception.DocumentProcessingException;
import com.java.doc_process.modal.FileMetadata;
import com.java.doc_process.model.DocumentEvent;
import com.java.doc_process.model.ProcessingResult;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DocumentProcessorTest {

    private static final String DOCUMENT_ID = "doc-123";
    private static final String BUCKET = "documents";
    private static final String INPUT_KEY = "input/doc-123/source.txt";

    private DynamoDBService dynamoDBService;
    private S3Service s3Service;
    private ObjectMapper mapper;
    private DocumentProcessor processor;

    @BeforeEach
    void setUp() {
        dynamoDBService = org.mockito.Mockito.mock(DynamoDBService.class);
        s3Service = org.mockito.Mockito.mock(S3Service.class);
        mapper = JsonMapper.builder().build();
        processor = new DocumentProcessor(
                dynamoDBService,
                s3Service,
                new DocumentTransformer(),
                mapper);
        ReflectionTestUtils.setField(processor, "configuredBucket", BUCKET);
    }

    @Test
    void processesVersionedEventAndPersistsOutputBeforeCompleting() {
        FileMetadata metadata = new FileMetadata();
        metadata.setDocumentId(DOCUMENT_ID);
        metadata.setInputS3Key(INPUT_KEY);
        metadata.setStatus("PENDING");
        when(dynamoDBService.findById(DOCUMENT_ID)).thenReturn(metadata);
        when(s3Service.download(BUCKET, INPUT_KEY))
                .thenReturn("hello world".getBytes(StandardCharsets.UTF_8));

        processor.processQueue(eventJson(DocumentEvent.CURRENT_VERSION));

        verify(dynamoDBService).markProcessing(DOCUMENT_ID);
        ArgumentCaptor<byte[]> resultBytes = ArgumentCaptor.forClass(byte[].class);
        verify(s3Service).uploadResult(eq(BUCKET), eq("output/doc-123/result.json"), resultBytes.capture());
        ProcessingResult result = mapper.readValue(resultBytes.getValue(), ProcessingResult.class);
        assertThat(result.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(result.extractedText()).isEqualTo("hello world");
        assertThat(result.wordCount()).isEqualTo(2);
        verify(dynamoDBService).markCompleted(DOCUMENT_ID, "output/doc-123/result.json");
    }

    @Test
    void completedRedeliveryIsAcknowledgedWithoutRepeatingStorageWrites() {
        FileMetadata metadata = new FileMetadata();
        metadata.setDocumentId(DOCUMENT_ID);
        metadata.setInputS3Key(INPUT_KEY);
        metadata.setStatus("COMPLETED");
        when(dynamoDBService.findById(DOCUMENT_ID)).thenReturn(metadata);

        processor.processQueue(eventJson(DocumentEvent.CURRENT_VERSION));

        verify(s3Service, never()).download(any(), any());
        verify(s3Service, never()).uploadResult(any(), any(), any());
        verify(dynamoDBService, never()).markProcessing(DOCUMENT_ID);
    }

    @Test
    void processingFailuresAreRecordedAndPropagatedForQueueRetry() {
        FileMetadata metadata = new FileMetadata();
        metadata.setDocumentId(DOCUMENT_ID);
        metadata.setInputS3Key(INPUT_KEY);
        metadata.setStatus("PENDING");
        when(dynamoDBService.findById(DOCUMENT_ID)).thenReturn(metadata);
        when(s3Service.download(BUCKET, INPUT_KEY)).thenThrow(new IllegalStateException("private detail"));

        assertThatThrownBy(() -> processor.processQueue(eventJson(DocumentEvent.CURRENT_VERSION)))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessage("Document processing failed");

        verify(dynamoDBService).markFailed(
                eq(DOCUMENT_ID),
                eq("Processing failed (IllegalStateException)"));
        verify(s3Service, never()).uploadResult(any(), any(), any());
    }

    @Test
    void rejectsUnsupportedEventVersions() {
        assertThatThrownBy(() -> processor.processQueue(eventJson(2)))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("invalid or unsupported");
        verify(dynamoDBService, never()).findById(any());
    }

    private String eventJson(int version) {
        return mapper.writeValueAsString(new DocumentEvent(
                version,
                DOCUMENT_ID,
                BUCKET,
                INPUT_KEY,
                "text/plain"));
    }
}
