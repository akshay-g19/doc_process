package com.java.doc_process.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.java.doc_process.model.ProcessingResult;
import com.java.doc_process.service.DocumentService;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Optional end-to-end test. It connects only to an already-running, bootstrapped Floci instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("floci")
@EnabledIfSystemProperty(named = "floci.integration", matches = "true")
class FlociDocumentProcessingTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Test
    void uploadIsProcessedAndResultIsPersistedToS3AndDynamoDb() throws Exception {
        String text = "Floci workflow " + UUID.randomUUID();
        var accepted = documentService.accept(new MockMultipartFile(
                "file",
                "floci-test.txt",
                "text/plain",
                text.getBytes(StandardCharsets.UTF_8)));

        var deadline = Instant.now().plusSeconds(45);
        var status = documentService.getStatus(accepted.documentId());
        while (!"COMPLETED".equals(status.status())
                && !"FAILED".equals(status.status())
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
            status = documentService.getStatus(accepted.documentId());
        }

        assertThat(status.status()).isEqualTo("COMPLETED");
        assertThat(status.outputKey()).isEqualTo("output/" + accepted.documentId() + "/result.json");

        ResponseBytes<GetObjectResponse> resultBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(status.outputKey())
                .build());
        ProcessingResult result = objectMapper.readValue(resultBytes.asByteArray(), ProcessingResult.class);
        assertThat(result.documentId()).isEqualTo(accepted.documentId());
        assertThat(result.extractedText()).isEqualTo(text);
        assertThat(result.wordCount()).isEqualTo(3);
    }
}
