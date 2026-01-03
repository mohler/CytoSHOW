package ij.io;

import ij.IJ;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class HoardManager {

    private static byte[] cachedHeader = null;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    /**
     * High-Level Method: Saves a video frame with an ImageJ-Optimized TIFF header.
     * Use this for your actual video data.
     */
    public static Path writeFrame(String sourceAbsolutePath, int frameIndex, byte[] rawData, int width, int height) {
        String cacheFilename = CacheNaming.generateCacheName(sourceAbsolutePath, frameIndex, "tif");
        
        // Generate Header (Only recalculate if dimensions change)
        if (cachedHeader == null || width != lastWidth || height != lastHeight) {
            cachedHeader = TiffPatcher.generateHeader(width, height);
            lastWidth = width;
            lastHeight = height;
        }

        // Delegate to the internal helper that handles header + body
        return writeChunkWithHeader(cacheFilename, cachedHeader, rawData);
    }

    /**
     * Low-Level Method: Writes raw bytes to the hoard.
     * This is the method your tests and legacy code expect.
     * It does NOT add a header. It just saves exactly what you give it.
     */
    public static Path writeChunk(String filename, byte[] data) {
        return writeChunkWithHeader(filename, null, data);
    }

    /**
     * Internal Helper: Handles the actual I/O and Governance.
     * Supports optional header (can be null).
     */
    private static Path writeChunkWithHeader(String filename, byte[] header, byte[] body) {
        try {
            Path hoardDir = CacheLocation.getHoardLocation();
            Path targetPath = hoardDir.resolve(filename);

            // Java 8 try-with-resources
            try (OutputStream out = Files.newOutputStream(targetPath, 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                
                // Write Header if present
                if (header != null && header.length > 0) {
                    out.write(header);
                }
                
                // Write Body
                out.write(body);
            }

            // Trigger the Governor to clean up old files
            CacheGovernor.govern(); 
            
            return targetPath;

        } catch (IOException e) {
            IJ.error("Hoard Write Failed: " + filename);
            IJ.handleException(e);
            return null;
        }
    }

    /**
     * Runs a startup diagnostic to remove corrupt (0-byte) or temporary orphan files
     * left over from previous crashes or ungraceful shutdowns.
     */
    public static void performSanityCheck() {
        IJ.showStatus("Checking CytoSHOW Cache health...");
        int removedCount = 0;

        try {
            Path hoardDir = CacheLocation.getHoardLocation();

            // Use DirectoryStream for efficient iteration without loading all into memory
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(hoardDir)) {
                for (Path entry : stream) {
                    // Skip directories, we only care about corrupt file chunks
                    if (Files.isDirectory(entry)) continue;

                    boolean shouldDelete = false;
                    String reason = "";

                    // Criterion 1: Zero-byte files (Corrupt/Empty)
                    if (Files.size(entry) == 0) {
                        shouldDelete = true;
                        reason = "0-byte corruption";
                    }
                    // Criterion 2: Obvious temporary files (Orphans)
                    // Adjust extensions based on what your ffmpeg process generates
                    else if (entry.toString().endsWith(".tmp") || entry.toString().endsWith(".part")) {
                        shouldDelete = true;
                        reason = "abandoned temp file";
                    }

                    if (shouldDelete) {
                        try {
                            Files.delete(entry);
                            removedCount++;
                            IJ.log("Sanity Check: Removed " + entry.getFileName() + " (" + reason + ")");
                        } catch (IOException e) {
                            IJ.log("Sanity Check: Could not delete " + entry.getFileName());
                        }
                    }
                }
            }

            if (removedCount > 0) {
                IJ.log("Sanity Check Complete: Cleaned up " + removedCount + " files.");
            }

        } catch (IOException e) {
            // Log but do not crash the app; cache might just be inaccessible or empty
            IJ.log("Sanity Check Warning: Could not scan hoard directory.");
        }
    }
}