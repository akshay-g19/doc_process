package com.java.doc_process.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class DocumentTransformerTest {

    private final DocumentTransformer transformer = new DocumentTransformer();

    @Test
    void extractsTextAndCountsUnicodeCharactersAndWordsDeterministically() {
        var result = transformer.transform("doc-123", "héllo world\nagain".getBytes(StandardCharsets.UTF_8));

        assertThat(result.version()).isEqualTo(1);
        assertThat(result.documentId()).isEqualTo("doc-123");
        assertThat(result.extractedText()).isEqualTo("héllo world\nagain");
        assertThat(result.characterCount()).isEqualTo(17);
        assertThat(result.wordCount()).isEqualTo(3);
        assertThat(result.processedAt()).isNotNull();
    }

    @Test
    void countsEmptyTextAsZeroWords() {
        var result = transformer.transform("doc-empty", new byte[0]);

        assertThat(result.characterCount()).isZero();
        assertThat(result.wordCount()).isZero();
    }
}
