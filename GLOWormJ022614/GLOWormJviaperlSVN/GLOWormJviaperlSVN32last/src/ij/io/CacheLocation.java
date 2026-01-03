package ij.io;

import ij.IJ;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CacheLocation {

    // The legacy root folder
    private static final String ROOT_CACHE_NAME = "CytoSHOWCacheFiles";
    // The specific subfolder for your new "Governed Hoard" logic
    private static final String HOARD_SUBFOLDER = "ffmpegHoard";

    public static Path getHoardLocation() throws IOException {
        // 1. Get User Home via ImageJ (e.g., C:\Users\Steve\)
        String homeDir = IJ.getDirectory("home");
        
        if (homeDir == null) {
            homeDir = System.getProperty("user.home");
        }

        // 2. Build the full path: ~/CytoSHOWCacheFiles/ffmpegHoard/
        Path hoardPath = Paths.get(homeDir, ROOT_CACHE_NAME, HOARD_SUBFOLDER);

        // 3. Create the directory tree if it doesn't exist
        // This handles creating "CytoSHOWCacheFiles" AND "ffmpegHoard" automatically
        if (!Files.exists(hoardPath)) {
            try {
                Files.createDirectories(hoardPath);
                IJ.log("Initialized Hoard: " + hoardPath.toString());
            } catch (IOException e) {
                IJ.handleException(e);
                throw e;
            }
        }

        return hoardPath;
    }
}