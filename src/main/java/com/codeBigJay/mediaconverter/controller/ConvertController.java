package com.codeBigJay.mediaconverter.controller;

import com.codeBigJay.mediaconverter.model.ConversionStatus;
import com.codeBigJay.mediaconverter.service.ConversionService;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ConvertController {

    @Autowired
    private ConversionService conversionService;

    // Simple status endpoint to check if app is running
    @GetMapping("/status")
    public ResponseEntity<String> getAppStatus() {
        return ResponseEntity.ok("Application is running");
    }

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> convertFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded");
        }
        String ext = FilenameUtils.getExtension(file.getOriginalFilename()).toLowerCase();
        if (!isAllowedVideoExtension(ext)) {
            return ResponseEntity.badRequest().body("Unsupported file type.");
        }
        try {
            ConversionStatus status = conversionService.submitFileForConversion(file);
            return ResponseEntity.ok(status.getId());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed: " + ex.getMessage());
        }
    }

    @GetMapping("/download")
    public ResponseEntity<?> downloadFromUrl(@RequestParam("url") String url) {
        if (url == null || url.isBlank()) return ResponseEntity.badRequest().body("Missing url");
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        try {
            ConversionStatus status = conversionService.submitUrlForConversion(decoded);
            return ResponseEntity.ok(status.getId());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed: " + ex.getMessage());
        }
    }

    @GetMapping("/status/{id}")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        ConversionStatus status = conversionService.getStatus(id);
        if (status == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(status);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable String id) {
        File out = conversionService.getOutputFile(id);
        if (out == null || !out.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not available");
        }
        FileSystemResource resource = new FileSystemResource(out);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(out.getName()).build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().headers(headers).body(resource);
    }

    // Test endpoint for debugging
    @GetMapping("/test")
    public String test() {
        return "Backend is working!";
    }

    private boolean isAllowedVideoExtension(String ext) {
        return ext.equals("mp4") || ext.equals("mov") || ext.equals("mkv") || ext.equals("webm") || ext.equals("avi") || ext.equals("mpeg") || ext.equals("mpg");
    }
}