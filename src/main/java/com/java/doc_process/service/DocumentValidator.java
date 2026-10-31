package com.java.doc_process.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentValidator {

    private final long maximumBytes;

    public DocumentValidator(@Value("${app.upload.max-bytes:5242880}") long maximumBytes) {
        this.maximumBytes = maximumBytes;
    }

    public ValidatedDocument validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty .txt file is required");
        }
        if (file.getSize() > maximumBytes) {
            throw new IllegalArgumentException("File exceeds the configured upload limit");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("A file name is required");
        }

        String normalizedName = originalFileName.replace('\\', '/');
        String baseName = normalizedName.substring(normalizedName.lastIndexOf('/') + 1);
        if (!baseName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new IllegalArgumentException("Only .txt documents are supported");
        }

        String suppliedContentType = file.getContentType();
        if (suppliedContentType != null && !suppliedContentType.isBlank()) {
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(suppliedContentType);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid content type");
            }
            if (!mediaType.isCompatibleWith(MediaType.TEXT_PLAIN)
                    && !mediaType.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM)) {
                throw new IllegalArgumentException("Only plain-text documents are supported");
            }
        }

        byte[] contents = file.getBytes();
        if (contents.length > maximumBytes) {
            throw new IllegalArgumentException("File exceeds the configured upload limit");
        }
        if (contents.length == 0) {
            throw new IllegalArgumentException("A non-empty .txt file is required");
        }
        validateUtf8Text(contents);

        String safeFileName = baseName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeFileName.isBlank() || safeFileName.equals(".") || safeFileName.equals("..")) {
            safeFileName = "document.txt";
        }
        return new ValidatedDocument(contents, baseName, safeFileName, MediaType.TEXT_PLAIN_VALUE);
    }

    private void validateUtf8Text(byte[] contents) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contents))
                    .toString();
            if (decoded.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("File content is not valid plain text");
            }
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("File content must be valid UTF-8 text");
        }
    }
}
