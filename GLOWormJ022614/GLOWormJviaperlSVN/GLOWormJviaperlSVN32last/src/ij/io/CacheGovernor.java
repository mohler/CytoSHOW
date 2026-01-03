package ij.io;

import ij.IJ;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CacheGovernor {

    // 10 GB in bytes (10 * 1024 * 1024 * 1024)
    private static long maxHoardSize = 10L * 1024 * 1024 * 1024;

 // This allows the Test (or a future Prefs dialog) to change it.
    public static void setMaxHoardSize(long bytes) {
        maxHoardSize = bytes;
    }
    
    /**
     * Enforces the cache limit. Should be called *after* adding a new chunk,
     * or periodically (e.g., on startup or idle).
     */
    public static void govern() {
        try {
            Path hoardDir = CacheLocation.getHoardLocation();
            
            // 1. Calculate current total size
            long currentSize = calculateFolderSize(hoardDir);
            
            if (currentSize < maxHoardSize) {
                return; // We are within limits, nothing to do.
            }

            IJ.log("Hoard over limit (" + formatSize(currentSize) + "). Governance active...");

            // 2. Get all files, sorted by Last Modified Time (Oldest first)
            List<Path> files = getFilesSortedByAge(hoardDir);

            // 3. Delete oldest files until we are under the limit
            for (Path file : files) {
                long fileSize = Files.size(file);
                
                // Try to delete the file
                try {
                    Files.delete(file);
                    currentSize -= fileSize;
                    // IJ.log("Evicted: " + file.getFileName()); // Optional verbose logging
                } catch (IOException e) {
                    IJ.log("Failed to evict: " + file.getFileName());
                }

                // Check if we are safe yet
                if (currentSize < maxHoardSize) {
                    IJ.log("Hoard governed. New size: " + formatSize(currentSize));
                    break;
                }
            }

        } catch (IOException e) {
            IJ.handleException(e);
        }
    }

    // Helper: Sum size of all files in the directory (depth 1 only)
    private static long calculateFolderSize(Path folder) throws IOException {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); } 
                    catch (IOException e) { return 0L; }
                })
                .sum();
        }
    }

    // Helper: Return list of files sorted Oldest -> Newest
    private static List<Path> getFilesSortedByAge(Path folder) throws IOException {
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparingLong(p -> getFileTime(p)))
                .collect(Collectors.toList());
        }
    }

    // Helper: Safely get Last Modified Time
    private static long getFileTime(Path p) {
        try {
            return Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime().toMillis();
        } catch (IOException e) {
            return 0L; // Treat errors as very old files to delete them first
        }
    }

    // Helper: Human readable size for logging
    private static String formatSize(long bytes) {
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}