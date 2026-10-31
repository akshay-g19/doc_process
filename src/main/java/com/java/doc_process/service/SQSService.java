package com.java.doc_process.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.java.doc_process.model.DocumentEvent;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@Service
public class SQSService {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    private final String queueName;

    public SQSService(
            SqsAsyncClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${aws.sqs.queue-url:}") String queueUrl,
            @Value("${aws.sqs.queue-name}") String queueName) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        this.queueName = queueName;
    }

    public void publishMessage(DocumentEvent event) {
        String body = objectMapper.writeValueAsString(event);
        sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(resolveQueueUrl())
                        .messageBody(body)
                        .build())
                .join();
        log.info("Document {} state=PUBLISHED", event.documentId());
    }

    private String resolveQueueUrl() {
        if (queueUrl != null && !queueUrl.isBlank()) {
            return queueUrl;
        }
        return sqsClient.getQueueUrl(request -> request.queueName(queueName))
                .join()
                .queueUrl();
    }
}
