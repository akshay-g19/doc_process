package com.java.doc_process.service;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.java.doc_process.exception.DocumentProcessingException;
import com.java.doc_process.modal.FileMetadata;
import com.java.doc_process.model.DocumentEvent;
import com.java.doc_process.model.ProcessingResult;

import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessor {

    private final DynamoDBService dynamoDBService;
    private final S3Service s3Service;
    private final DocumentTransformer transformer;
    private final ObjectMapper mapper;

    @Value("${aws.s3.bucket-name}")
    private String configuredBucket;

    @SqsListener("${aws.sqs.queue-name}")
    public void processQueue(String message) {
        DocumentEvent event = mapper.readValue(message, DocumentEvent.class);
        validateEvent(event);

        FileMetadata existing = dynamoDBService.findById(event.documentId());
        if (existing == null) {
            throw new DocumentProcessingException("Document metadata is missing", null);
        }
        if (!event.key().equals(existing.getInputS3Key())) {
            throw new DocumentProcessingException("Queue event does not match document metadata", null);
        }
        if ("COMPLETED".equals(existing.getStatus())) {
            log.info("Document {} state=COMPLETED duplicateDelivery=true", event.documentId());
            return;
        }

        try {
            dynamoDBService.markProcessing(event.documentId());
            byte[] contents = s3Service.download(event.bucket(), event.key());
            ProcessingResult result = transformer.transform(event.documentId(), contents);
            String outputKey = "output/" + event.documentId() + "/result.json";
            byte[] resultBytes = mapper.writeValueAsBytes(result);
            s3Service.uploadResult(event.bucket(), outputKey, resultBytes);
            dynamoDBService.markCompleted(event.documentId(), outputKey);
            log.info("Document {} state=COMPLETED", event.documentId());
        } catch (Exception processingFailure) {
            String safeError = "Processing failed (" + processingFailure.getClass().getSimpleName() + ")";
            try {
                dynamoDBService.markFailed(event.documentId(), safeError);
            } catch (RuntimeException statusFailure) {
                processingFailure.addSuppressed(statusFailure);
            }
            log.error("Document {} state=FAILED failureType={}",
                    event.documentId(),
                    processingFailure.getClass().getSimpleName());
            throw new DocumentProcessingException("Document processing failed", processingFailure);
        }
    }

    private void validateEvent(DocumentEvent event) {
        if (event == null
                || event.version() != DocumentEvent.CURRENT_VERSION
                || event.documentId() == null
                || event.documentId().isBlank()
                || event.key() == null
                || event.key().isBlank()
                || !event.key().startsWith("input/" + event.documentId() + "/")
                || !configuredBucket.equals(event.bucket())) {
            throw new DocumentProcessingException("Queue event is invalid or unsupported", null);
        }
    }
}
