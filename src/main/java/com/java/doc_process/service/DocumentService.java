package com.java.doc_process.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.java.doc_process.exception.DocumentNotFoundException;
import com.java.doc_process.exception.QueuePublishException;
import com.java.doc_process.modal.FileMetadata;
import com.java.doc_process.model.DocumentEvent;
import com.java.doc_process.model.DocumentStatusResponse;
import com.java.doc_process.model.UploadResponse;

@Service
public class DocumentService {

    private final DocumentValidator validator;
    private final S3Service s3Service;
    private final DynamoDBService dynamoDBService;
    private final SQSService sqsService;
    private final String bucketName;

    public DocumentService(
            DocumentValidator validator,
            S3Service s3Service,
            DynamoDBService dynamoDBService,
            SQSService sqsService,
            @Value("${aws.s3.bucket-name}") String bucketName) {
        this.validator = validator;
        this.s3Service = s3Service;
        this.dynamoDBService = dynamoDBService;
        this.sqsService = sqsService;
        this.bucketName = bucketName;
    }

    public UploadResponse accept(MultipartFile file) throws java.io.IOException {
        ValidatedDocument validated = validator.validate(file);
        String documentId = UUID.randomUUID().toString();
        String inputKey = "input/" + documentId + "/" + validated.safeFileName();

        s3Service.upload(bucketName, inputKey, validated.contents(), validated.contentType());

        Instant now = Instant.now();
        FileMetadata metadata = new FileMetadata();
        metadata.setDocumentId(documentId);
        metadata.setFileName(validated.fileName());
        metadata.setContentType(validated.contentType());
        metadata.setInputS3Key(inputKey);
        metadata.setSize((long) validated.contents().length);
        metadata.setStatus("PENDING");
        metadata.setCreatedAt(now.toString());
        metadata.setUpdatedAt(now.toString());
        dynamoDBService.create(metadata);

        DocumentEvent event = new DocumentEvent(
                DocumentEvent.CURRENT_VERSION,
                documentId,
                bucketName,
                inputKey,
                validated.contentType());
        try {
            sqsService.publishMessage(event);
        } catch (RuntimeException publishFailure) {
            try {
                dynamoDBService.markFailed(documentId, "Could not enqueue document for processing");
            } catch (RuntimeException persistenceFailure) {
                publishFailure.addSuppressed(persistenceFailure);
            }
            throw new QueuePublishException(publishFailure);
        }
        return new UploadResponse(documentId, "PENDING");
    }

    public DocumentStatusResponse getStatus(String documentId) {
        FileMetadata metadata = dynamoDBService.findById(documentId);
        if (metadata == null) {
            throw new DocumentNotFoundException(documentId);
        }
        return new DocumentStatusResponse(
                metadata.getDocumentId(),
                metadata.getFileName(),
                metadata.getContentType(),
                metadata.getSize(),
                metadata.getStatus(),
                metadata.getInputS3Key(),
                metadata.getOutputS3Key(),
                parseInstant(metadata.getCreatedAt()),
                parseInstant(metadata.getUpdatedAt()),
                parseInstant(metadata.getProcessedAt()),
                metadata.getError());
    }

    private Instant parseInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
