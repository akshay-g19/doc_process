package com.java.doc_process.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.java.doc_process.model.DocumentEvent;

import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SQSServiceTest {

    private SqsAsyncClient client;
    private ObjectMapper mapper;
    private SQSService service;

    @BeforeEach
    void setUp() {
        client = org.mockito.Mockito.mock(SqsAsyncClient.class);
        mapper = JsonMapper.builder().build();
        service = new SQSService(client, mapper, "http://localhost:4566/queue-url", "queue-name");
    }

    @Test
    void awaitsSendAndPublishesVersionedEventToConfiguredQueue() {
        when(client.sendMessage(org.mockito.ArgumentMatchers.any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        SendMessageResponse.builder().messageId("message-1").build()));
        DocumentEvent event = new DocumentEvent(1, "doc-1", "documents", "input/doc-1/file.txt", "text/plain");

        service.publishMessage(event);

        ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo("http://localhost:4566/queue-url");
        assertThat(mapper.readValue(request.getValue().messageBody(), DocumentEvent.class)).isEqualTo(event);
    }

    @Test
    void propagatesAnAsynchronousQueueSendFailure() {
        when(client.sendMessage(org.mockito.ArgumentMatchers.any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));

        assertThatThrownBy(() -> service.publishMessage(
                new DocumentEvent(1, "doc-1", "documents", "input/doc-1/file.txt", "text/plain")))
                .isInstanceOf(RuntimeException.class);
    }
}
