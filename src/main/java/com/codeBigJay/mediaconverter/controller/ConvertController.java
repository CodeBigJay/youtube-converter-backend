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
import java.util.List;
import java.util.stream.Collectors;

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

    // Lists every file a conversion produced (1 entry for a single video, many for a playlist)
    @GetMapping("/files/{id}")
    public ResponseEntity<?> listFiles(@PathVariable String id) {
        List<File> files = conversionService.getOutputFiles(id);
        if (files.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No files available");
        }
        List<String> names = files.stream().map(File::getName).collect(Collectors.toList());
        return ResponseEntity.ok(names);
    }

    // If the conversion produced a single file, downloads it directly.
    // If it produced several (playlist), zips them and downloads the archive.
    @GetMapping("/download/{id}")
    public ResponseEntity<?> download(@PathVariable String id) {
        List<File> files = conversionService.getOutputFiles(id);
        if (files.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not available");
        }

        try {
            File out;
            if (files.size() == 1) {
                out = files.get(0);
            } else {
                out = conversionService.zipOutputFiles(id);
            }
            if (out == null || !out.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not available");
            }
            FileSystemResource resource = new FileSystemResource(out);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(out.getName()).build());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            return ResponseEntity.ok().headers(headers).body(resource);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to prepare download: " + ex.getMessage());
        }
    }

    // Downloads a single named file that belongs to this conversion id (used to fetch
    // one track out of a playlist instead of the whole zip).
    @GetMapping("/download/{id}/{filename:.+}")
    public ResponseEntity<?> downloadOne(@PathVariable String id, @PathVariable String filename) {
        List<File> files = conversionService.getOutputFiles(id);
        File match = files.stream()
                .filter(f -> f.getName().equals(filename))
                .findFirst()
                .orElse(null);
        if (match == null || !match.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not available");
        }
        FileSystemResource resource = new FileSystemResource(match);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(match.getName()).build());
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
