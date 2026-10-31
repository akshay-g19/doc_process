package com.java.doc_process.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.java.doc_process.model.ApiError;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsMissingDocumentsToStructuredNotFoundResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/unknown");

        var response = handler.handleNotFound(new DocumentNotFoundException("unknown"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.path()).isEqualTo("/api/documents/unknown");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void hidesUnexpectedInternalErrorDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents/upload");

        var response = handler.handleGeneralException(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
