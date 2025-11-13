package com.codeBigJay.mediaconverter.service;

import com.codeBigJay.mediaconverter.model.ConversionStatus;
import com.codeBigJay.mediaconverter.util.FileUtil;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class ConversionService {

    private final Logger logger = LoggerFactory.getLogger(ConversionService.class);

    @Value("${app.storage.dir:media-storage}")
    private String storageDirConfig;

    private Path storageDir;
    private final Map<String, ConversionStatus> statusMap = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final long MAX_DOWNLOAD_BYTES = 800L * 1024L * 1024L;

    @PostConstruct
    public void init() throws Exception {
        storageDir = Paths.get(storageDirConfig).toAbsolutePath().normalize();
        FileUtil.ensureDirectoryExists(storageDir);
        logger.info("Media storage directory: {}", storageDir.toString());
        
        // Test if yt-dlp is available
        testYtDlpAvailability();
    }

    private void testYtDlpAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder("yt-dlp", "--version");
            Process proc = pb.start();
            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                logger.info("✅ yt-dlp is available and working");
            } else {
                logger.warn("⚠️ yt-dlp may not be properly installed");
            }
        } catch (Exception e) {
            logger.error("❌ yt-dlp is not available: {}", e.getMessage());
        }
    }

    public ConversionStatus submitFileForConversion(MultipartFile file) throws IOException {
        String id = UUID.randomUUID().toString();
        ConversionStatus status = new ConversionStatus(id);
        statusMap.put(id, status);

        String originalFilename = FilenameUtils.getName(file.getOriginalFilename());
        String sanitized = FileUtil.sanitizeFilename(originalFilename);
        Path tempInput = storageDir.resolve(id + "-" + sanitized);
        try (InputStream is = file.getInputStream();
             OutputStream os = new FileOutputStream(tempInput.toFile())) {
            is.transferTo(os);
        }

        executor.submit(() -> runFfmpegConversion(id, tempInput.toFile(), sanitized, status));
        return status;
    }

    public ConversionStatus submitUrlForConversion(String url) throws Exception {
        logger.info("🔍 Processing URL: {}", url);
        
        URI uri = new URI(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are supported");
        }

        String host = uri.getHost();
        boolean isYouTube = isYouTubeUrl(host, url);

        String id = UUID.randomUUID().toString();
        ConversionStatus status = new ConversionStatus(id);
        statusMap.put(id, status);

        // Extensive debug logging
        logger.info("🎯 URL Analysis:");
        logger.info("   - Full URL: {}", url);
        logger.info("   - Host: {}", host);
        logger.info("   - Is YouTube: {}", isYouTube);
        logger.info("   - Conversion ID: {}", id);

        executor.submit(() -> {
            try { 
                if (isYouTube) {
                    logger.info("🎵 [{}] Using yt-dlp for YouTube download", id);
                    downloadYouTubeVideo(uri, id, status);
                } else {
                    logger.info("🌐 [{}] Using regular download method", id);
                    downloadAndConvert(uri, id, status);
                }
            } catch (Exception ex) {
                logger.error("❌ [{}] Error: {}", id, ex.getMessage(), ex);
                status.setState(ConversionStatus.State.FAILED);
                status.setMessage("Download failed: " + ex.getMessage());
            }
        });

        return status;
    }

    private boolean isYouTubeUrl(String host, String url) {
        if (host == null) {
            logger.warn("⚠️ Host is null for URL: {}", url);
            return false;
        }
        
        String lowerHost = host.toLowerCase();
        String lowerUrl = url.toLowerCase();
        
        // Comprehensive YouTube URL detection
        boolean isYT = lowerHost.contains("youtube.com") || 
                   lowerHost.contains("youtu.be") ||
                   lowerHost.contains("www.youtube.com") ||
                   lowerHost.contains("m.youtube.com") ||
                   lowerUrl.contains("youtube.com/watch") ||
                   lowerUrl.contains("youtu.be/") ||
                   lowerUrl.contains("youtube.com/shorts/");
    
        logger.debug("🔍 YouTube Detection:");
        logger.debug("   - Host: '{}'", lowerHost);
        logger.debug("   - URL: '{}'", lowerUrl);
        logger.debug("   - Result: {}", isYT);
        
        return isYT;
    }

    public ConversionStatus getStatus(String id) {
        return statusMap.get(id);
    }

    public File getOutputFile(String id) {
        ConversionStatus st = statusMap.get(id);
        if (st == null || st.getState() != ConversionStatus.State.COMPLETED) return null;
        return new File(st.getOutputFilename());
    }

    private void downloadYouTubeVideo(URI uri, String id, ConversionStatus status) throws Exception {
        status.setState(ConversionStatus.State.RUNNING);
        status.setMessage("Downloading from YouTube...");
        logger.info("📥 [{}] Starting YouTube download: {}", id, uri.toString());

        String sanitized = FileUtil.sanitizeFilename("youtube-audio");
        Path tempInput = storageDir.resolve(id + "-" + sanitized);

        // Use yt-dlp to download and convert YouTube video directly to MP3
        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-f", "bestaudio",  // Get best audio quality
                "--extract-audio",
                "--audio-format", "mp3",
                "--audio-quality", "0",   // Best quality
                "--embed-thumbnail",      // Embed thumbnail in MP3
                "--add-metadata",         // Add metadata
                "-o", tempInput + ".%(ext)s",
                uri.toString()
        );

        logger.info("🚀 [{}] Executing yt-dlp command", id);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader rdr = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = rdr.readLine()) != null) {
                output.append(line).append("\n");
                logger.info("[yt-dlp {}] {}", id, line); // Log all output for debugging
                
                // Parse progress from yt-dlp output
                if (line.contains("[download]") && line.contains("%")) {
                    try {
                        String percentStr = line.substring(line.indexOf("[download]") + 10).split("%")[0].trim();
                        int percent = Integer.parseInt(percentStr.replace(".", "").replace(",", ""));
                        status.setProgressPercent(Math.min(99, percent));
                        status.setMessage("Downloading from YouTube... " + percent + "%");
                    } catch (Exception e) {
                        // Ignore parsing errors
                    }
                }
            }
        }

        int rc = proc.waitFor();
        logger.info("📊 [{}] yt-dlp exit code: {}", id, rc);
        
        if (rc == 0) {
            // Find the actual downloaded file
            File downloadedFile = findDownloadedFile(tempInput);
            if (downloadedFile != null && downloadedFile.exists()) {
                status.setState(ConversionStatus.State.COMPLETED);
                status.setProgressPercent(100);
                status.setOutputFilename(downloadedFile.getAbsolutePath());
                status.setMessage("YouTube conversion completed!");
                logger.info("✅ [{}] YouTube conversion completed: {}", id, downloadedFile.getAbsolutePath());
            } else {
                // Try alternative file patterns
                downloadedFile = findAlternativeFiles(id);
                if (downloadedFile != null && downloadedFile.exists()) {
                    status.setState(ConversionStatus.State.COMPLETED);
                    status.setProgressPercent(100);
                    status.setOutputFilename(downloadedFile.getAbsolutePath());
                    status.setMessage("YouTube conversion completed!");
                    logger.info("✅ [{}] YouTube conversion completed (alternative): {}", id, downloadedFile.getAbsolutePath());
                } else {
                    logger.error("❌ [{}] Downloaded files not found. Output: {}", id, output);
                    throw new IOException("YouTube download completed but file not found. Check logs for details.");
                }
            }
        } else {
            logger.error("❌ [{}] yt-dlp failed. Output: {}", id, output);
            throw new IOException("YouTube download failed with code: " + rc + ". Check logs for details.");
        }
    }

    private File findDownloadedFile(Path tempInput) {
        String baseName = tempInput.toString();
        File[] possibleFiles = new File[]{
            new File(baseName + ".mp3"),
            new File(baseName + ".m4a"),
            new File(baseName + ".webm"),
            new File(baseName + ".mp4")
        };
        
        for (File file : possibleFiles) {
            if (file.exists()) {
                logger.debug("📁 Found downloaded file: {}", file.getAbsolutePath());
                return file;
            }
        }
        return null;
    }

    private File findAlternativeFiles(String id) {
        // Search for any files that start with the ID
        File storageDirFile = storageDir.toFile();
        File[] files = storageDirFile.listFiles((dir, name) -> name.startsWith(id));
        
        if (files != null && files.length > 0) {
            logger.debug("🔍 Found {} files with ID prefix: {}", files.length, id);
            for (File file : files) {
                logger.debug("📁 Checking file: {}", file.getName());
                if (file.isFile() && (file.getName().endsWith(".mp3") || 
                                      file.getName().endsWith(".m4a") || 
                                      file.getName().endsWith(".webm") || 
                                      file.getName().endsWith(".mp4"))) {
                    return file;
                }
            }
            // Return the first file found if no specific extension matches
            return files[0];
        }
        return null;
    }

    private void downloadAndConvert(URI uri, String id, ConversionStatus status) throws Exception {
        status.setState(ConversionStatus.State.RUNNING);
        status.setMessage("Starting download...");
        logger.info("📥 [{}] Starting regular download: {}", id, uri.toString());

        // Use realistic browser headers to avoid blocking
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        
        HttpRequest headReq = HttpRequest.newBuilder()
                .uri(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();

        String contentType = null;
        long contentLength = -1;
        try {
            HttpResponse<Void> headResp = httpClient.send(headReq, HttpResponse.BodyHandlers.discarding());
            logger.info("📊 [{}] HEAD response status: {}", id, headResp.statusCode());
            if (headResp.statusCode() >= 200 && headResp.statusCode() < 400) {
                contentType = headResp.headers().firstValue("Content-Type").orElse(null);
                String len = headResp.headers().firstValue("Content-Length").orElse(null);
                if (len != null) {
                    try { contentLength = Long.parseLong(len); } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception e) {
            logger.debug("[{}] HEAD failed: {}", id, e.getMessage());
        }

        logger.info("📄 [{}] Content-Type: {}, Content-Length: {}", id, contentType, contentLength);

        // Skip content type check for YouTube URLs (they should be handled by yt-dlp method)
        boolean isYouTube = isYouTubeUrl(uri.getHost(), uri.toString());
        if (contentType != null && !isYouTube) {
            contentType = contentType.toLowerCase();
            if (!(contentType.startsWith("video/") || contentType.startsWith("application/octet-stream") || contentType.contains("mp4") || contentType.contains("webm") || contentType.contains("mpeg"))) {
                throw new IllegalArgumentException("Remote content not a recognized video type: " + contentType);
            }
        } else if (isYouTube) {
            logger.warn("⚠️ [{}] YouTube URL reached regular download method! This should not happen.", id);
        }

        if (contentLength > 0 && contentLength > MAX_DOWNLOAD_BYTES) {
            throw new IllegalArgumentException("Remote file too large: " + (contentLength / (1024*1024)) + " MB");
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "identity") // Avoid gzip encoding issues
                .build();

        HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
        logger.info("📡 [{}] GET response status: {}", id, resp.statusCode());
        
        if (resp.statusCode() < 200 || resp.statusCode() >= 400) {
            throw new IOException("HTTP status: " + resp.statusCode());
        }

        if (contentLength <= 0) {
            String len = resp.headers().firstValue("Content-Length").orElse(null);
            if (len != null) {
                try { contentLength = Long.parseLong(len); } catch (NumberFormatException ignored) {}
            }
        }

        String guessedName = Paths.get(uri.getPath()).getFileName() != null ? Paths.get(uri.getPath()).getFileName().toString() : "remote-file";
        String sanitized = FileUtil.sanitizeFilename(guessedName);
        Path tempInput = storageDir.resolve(id + "-" + sanitized);
        File tempFile = tempInput.toFile();

        try (InputStream is = resp.body(); FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0L;
            long lastUpdate = System.currentTimeMillis();
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                totalRead += read;

                if (totalRead > MAX_DOWNLOAD_BYTES) {
                    fos.close();
                    tempFile.delete();
                    throw new IOException("Downloaded file exceeds maximum allowed size");
                }

                if (contentLength > 0) {
                    int percent = (int) ((totalRead * 100) / contentLength);
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 800) {
                        status.setProgressPercent(Math.min(99, percent));
                        status.setMessage("Downloading... " + percent + "%");
                        lastUpdate = now;
                    }
                } else {
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 2500) {
                        status.setMessage("Downloading... " + (totalRead / (1024*1024)) + " MB");
                        lastUpdate = now;
                    }
                }
            }
        }

        status.setProgressPercent(0);
        status.setMessage("Download complete. Queueing conversion.");
        executor.submit(() -> runFfmpegConversion(id, tempFile, sanitized, status));
    }

    private void runFfmpegConversion(String id, File inputFile, String originalName, ConversionStatus status) {
        status.setState(ConversionStatus.State.RUNNING);
        status.setMessage("Starting conversion...");
        logger.info("[{}] Converting file: {}", id, inputFile.getAbsolutePath());
        try {
            File outFile = FileUtil.resolveOutputFile(storageDir.toFile(), id, originalName);
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-i", inputFile.getAbsolutePath(),
                    "-vn",
                    "-ar", "44100",
                    "-ac", "2",
                    "-b:a", "192k",
                    outFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader rdr = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                long lastUpdate = System.currentTimeMillis();
                while ((line = rdr.readLine()) != null) {
                    logger.debug("[ffmpeg {}] {}", id, line);
                    if (line.contains("time=") || line.contains("Duration:")) {
                        try {
                            int idx = line.indexOf("time=");
                            if (idx >= 0) {
                                String timePart = line.substring(idx + 5).split(" ")[0].trim();
                                double seconds = parseTimeToSeconds(timePart);
                                double total = extractDurationFromLog(line);
                                if (total <= 0) total = probeDurationFromFile(inputFile);
                                if (total > 0) {
                                    int percent = (int) Math.min(100, (seconds / total) * 100);
                                    long now = System.currentTimeMillis();
                                    if (now - lastUpdate > 1000) {
                                        status.setProgressPercent(percent);
                                        status.setMessage("Converting... " + percent + "%");
                                        lastUpdate = now;
                                    }
                                }
                            }
                        } catch (Exception e) { }
                    }
                }
            }

            int rc = proc.waitFor();
            if (rc == 0 && outFile.exists()) {
                status.setState(ConversionStatus.State.COMPLETED);
                status.setProgressPercent(100);
                status.setOutputFilename(outFile.getAbsolutePath());
                status.setMessage("Completed");
                logger.info("[{}] Conversion completed: {}", id, outFile.getAbsolutePath());
                try { inputFile.delete(); } catch (Exception ignored) {}
            } else {
                status.setState(ConversionStatus.State.FAILED);
                status.setMessage("Conversion failed (ffmpeg rc: " + rc + ")");
                logger.error("[{}] ffmpeg rc={}", id, rc);
            }
        } catch (Exception ex) {
            logger.error("[{}] Conversion failed: {}", id, ex.getMessage(), ex);
            status.setState(ConversionStatus.State.FAILED);
            status.setMessage("Conversion failed: " + ex.getMessage());
        }
    }

    private static double parseTimeToSeconds(String hhmmss) {
        String[] parts = hhmmss.split(":");
        double seconds = 0.0;
        try {
            if (parts.length == 3) {
                seconds += Double.parseDouble(parts[0].replace(",", ".")) * 3600;
                seconds += Double.parseDouble(parts[1].replace(",", ".")) * 60;
                seconds += Double.parseDouble(parts[2].replace(",", "."));
            } else if (parts.length == 2) {
                seconds += Double.parseDouble(parts[0].replace(",", ".")) * 60;
                seconds += Double.parseDouble(parts[1].replace(",", "."));
            } else {
                seconds += Double.parseDouble(parts[0].replace(",", "."));
            }
        } catch (Exception e) { return 0.0; }
        return seconds;
    }

    private static double extractDurationFromLog(String line) {
        try {
            int idx = line.indexOf("Duration:");
            if (idx >= 0) {
                String part = line.substring(idx + 9).trim().split(",")[0].trim();
                return parseTimeToSeconds(part);
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    private double probeDurationFromFile(File inputFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    inputFile.getAbsolutePath());
            Process p = pb.start();
            try (BufferedReader rdr = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String out = rdr.readLine();
                p.waitFor(5, TimeUnit.SECONDS);
                if (out != null) {
                    double d = Double.parseDouble(out.trim());
                    return d;
                }
            }
        } catch (Exception e) { }
        return 0.0;
    }
}