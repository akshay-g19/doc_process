package com.java.doc_process.controller;

import tools.jackson.databind.ObjectMapper;
import com.java.doc_process.repository.FileMetadataRepository;

import com.java.doc_process.service.S3Service;
import com.java.doc_process.service.SQSService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final FileMetadataRepository repository;
    private final S3Service s3Service;
    private final SQSService sqsService;
    private final ObjectMapper objectMapper;


    @GetMapping("/health")
    public ResponseEntity<String> healthCheck(){
        return ResponseEntity.ok().body("Application is up");
    }


    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadDocument(@RequestParam("file") MultipartFile file ,@RequestParam("bucket") String bucketName) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }
        String documentId = UUID.randomUUID().toString();
        String key = "input/" + documentId + "/orignal.txt";

        s3Service.upload(bucketName,key, file);

        Map<String, String> event = new HashMap<>();
        event.put("documentId", documentId);
        event.put("s3Key", key);

        sqsService.publishMessage(objectMapper.writeValueAsString(event));

        Map<String, String> response = new HashMap<>();
        response.put("documentId", documentId);
        response.put("Status", "Pending");

        return ResponseEntity.accepted().body(response);
    }
}
