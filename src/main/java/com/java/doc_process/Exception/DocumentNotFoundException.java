package com.java.doc_process.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String documentId) {
        super("Document not found: " + documentId);
    }
}
