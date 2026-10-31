package com.java.doc_process.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentValidatorTest {

    private final DocumentValidator validator = new DocumentValidator(16);

    @Test
    void acceptsUtf8PlainTextAndSanitizesTheKeyFilename() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "folder\\my notes.txt",
                "text/plain; charset=UTF-8",
                "hello\nworld".getBytes(StandardCharsets.UTF_8));

        ValidatedDocument result = validator.validate(file);

        assertThat(result.fileName()).isEqualTo("my notes.txt");
        assertThat(result.safeFileName()).isEqualTo("my_notes.txt");
        assertThat(result.contentType()).isEqualTo("text/plain");
        assertThat(result.contents()).containsExactly("hello\nworld".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsEmptyFiles() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void rejectsUnsupportedExtensionsAndMediaTypes() {
        MockMultipartFile wrongExtension =
                new MockMultipartFile("file", "document.pdf", "text/plain", "text".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile wrongType =
                new MockMultipartFile("file", "document.txt", "image/png", "text".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(wrongExtension))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".txt");
        assertThatThrownBy(() -> validator.validate(wrongType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plain-text");
    }

    @Test
    void rejectsOversizeAndInvalidUtf8Input() {
        MockMultipartFile tooLarge =
                new MockMultipartFile("file", "large.txt", "text/plain", new byte[17]);
        MockMultipartFile malformedUtf8 =
                new MockMultipartFile("file", "invalid.txt", "text/plain", new byte[]{(byte) 0xC3, 0x28});

        assertThatThrownBy(() -> validator.validate(tooLarge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("upload limit");
        assertThatThrownBy(() -> validator.validate(malformedUtf8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UTF-8");
    }
}
