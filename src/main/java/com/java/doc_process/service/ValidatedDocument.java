package com.java.doc_process.service;

public record ValidatedDocument(
        byte[] contents,
        String fileName,
        String safeFileName,
        String contentType) {
}
