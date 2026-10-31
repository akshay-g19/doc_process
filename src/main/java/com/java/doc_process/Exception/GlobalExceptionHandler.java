package com.java.doc_process.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.java.doc_process.model.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import software.amazon.awssdk.core.exception.SdkException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            DocumentNotFoundException exception,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Not Found", exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MultipartException.class})
    public ResponseEntity<ApiError> handleValidation(
            RuntimeException exception,
            HttpServletRequest request) {
        String message = exception instanceof IllegalArgumentException
                ? exception.getMessage()
                : "Invalid multipart upload";
        return response(HttpStatus.BAD_REQUEST, "Validation Error", message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                "File exceeds the configured upload limit", request);
    }

    @ExceptionHandler(QueuePublishException.class)
    public ResponseEntity<ApiError> handleQueueUnavailable(HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Queue Unavailable",
                "The document was stored but could not be queued. Its status is FAILED.", request);
    }

    @ExceptionHandler(SdkException.class)
    public ResponseEntity<ApiError> handleAwsFailure(HttpServletRequest request) {
        return response(HttpStatus.BAD_GATEWAY, "Storage Service Error",
                "A required storage service is unavailable", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI()));
    }
}
