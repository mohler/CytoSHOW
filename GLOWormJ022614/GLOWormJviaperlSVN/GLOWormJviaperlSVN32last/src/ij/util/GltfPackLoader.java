package ij.util;

import ij.IJ;
import java.io.*;
import java.nio.file.*;

public class GltfPackLoader {

    public static String getExecutablePath() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        String resourcePath;
        String ext = "";

        if (os.contains("win")) {
            resourcePath = "/bin/win/gltfpack.exe";
            ext = ".exe";
        } else if (os.contains("mac")) {
            // Check for Apple Silicon (M1/M2/M3)
            // Note: If running under Rosetta, this might report x86_64, 
            // but the intel binary works fine on M1 via Rosetta anyway.
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                resourcePath = "/bin/mac/arm64/gltfpack";
            } else {
                resourcePath = "/bin/mac/intel/gltfpack"; 
            }
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            resourcePath = "/bin/linux/gltfpack";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + os);
        }

        // Extract to a temp folder unique to your app
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "cytoshow_gltf");
        if (!tempDir.exists()) tempDir.mkdirs();
        
        File exeFile = new File(tempDir, "gltfpack" + ext);
        
        // Simple caching: if file exists, assume it's good. 
        // (For production, you might want to check file size or deleteOnExit)
        if (!exeFile.exists()) {
            // Note the leading slash in resourcePath!
            try (InputStream in = GltfPackLoader.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    throw new FileNotFoundException("Internal Error: Could not find " + resourcePath + " inside the JAR.");
                }
                Files.copy(in, exeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // Ensure executable permissions on Mac/Linux
        if (!os.contains("win")) {
            exeFile.setExecutable(true);
        }

        return exeFile.getAbsolutePath();
    }
}