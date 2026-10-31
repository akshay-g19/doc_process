package com.java.doc_process.modal;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@DynamoDbBean
public class FileMetadata {

    private String documentId;
    private String fileName;
    private String contentType;
    private String inputS3Key;
    private String outputS3Key;
    private Long size;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String processedAt;
    private String error;

    @DynamoDbPartitionKey
    public String getDocumentId() {
        return documentId;
    }
}
