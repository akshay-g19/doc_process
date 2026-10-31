package com.java.doc_process.repository;

import com.java.doc_process.modal.FileMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class FileMetadataRepository {

    private final DynamoDbTable<FileMetadata> table;

    public FileMetadataRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamo-db.table-name}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(FileMetadata.class));
    }

    public void save(FileMetadata fileMetadata) {
        table.putItem(fileMetadata);
    }

    public FileMetadata findById(String fileId) {
        return table.getItem(Key.builder().partitionValue(fileId).build());
    }
}
