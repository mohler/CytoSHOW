package ij.io;

import ij.IJ;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.File;

public class CacheNaming {

    /**
     * Generates a safe, unique, and reversible(ish) filename for the hoard.
     * Format: [PathHash]_[SafeOriginalName]_[FrameIndex].[Extension]
     * * Example: 
     * Source: D:\My Data\Experiment 1\sample_video.avi
     * Frame:  105
     * Result: a7f3b9_sample_video_avi_0105.raw
     */
    public static String generateCacheName(String sourceAbsolutePath, int frameIndex, String extension) {
        
        // 1. CONDENSE: Hash the full Absolute Path
        // We use the path to ensure that "D:\Data\video.avi" and "D:\Backup\video.avi" 
        // don't overwrite each other in the flat hoard.
        String pathHash = getShortHash(sourceAbsolutePath);

        // 2. ELIMINATE BAD CHARACTERS: Make the original filename safe
        // Extract just the filename (e.g., "sample_video.avi")
        String originalName = new File(sourceAbsolutePath).getName();
        // Replace anything that isn't a letter, number, dot, or hyphen with an underscore
        String safeName = originalName.replaceAll("[^a-zA-Z0-9.-]", "_");

        // 3. FORMAT: Combine them into the final flat string
        // We pad the frame index to 6 digits for proper sorting in Explorer/Finder
        return String.format("%s_%s_%06d.%s", pathHash, safeName, frameIndex, extension);
    }

    // Helper: Generates a short, consistent hash (first 12 chars of SHA-256)
    private static String getShortHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Convert first 6 bytes to Hex (12 chars string) - ample collision resistance for this purpose
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                String hex = Integer.toHexString(0xff & encodedhash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback (extremely unlikely)
            return Integer.toHexString(input.hashCode());
        }
    }
}