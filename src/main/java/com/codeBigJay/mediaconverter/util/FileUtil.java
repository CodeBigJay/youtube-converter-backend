package com.codeBigJay.mediaconverter.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {

    public static void ensureDirectoryExists(Path path) throws Exception {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

    public static String sanitizeFilename(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._\\- ]", "_");
    }

    public static File resolveOutputFile(File storageDir, String id, String originalName) {
        String base = originalName;
        if (originalName.contains(".")) {
            base = originalName.substring(0, originalName.lastIndexOf('.'));
        }
        base = sanitizeFilename(base);
        String outName = id + "-" + base + ".mp3";
        return new File(storageDir, outName);
    }
}
