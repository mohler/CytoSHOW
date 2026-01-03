package ij.util;

import java.io.*;
import java.nio.file.*;

public class NativeTools {

    /**
     * Finds the correct binary for the current OS/Arch inside the JAR,
     * extracts it to a temp folder, and returns the executable File.
     */
	public static File getBundledBinary(String toolName) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        String subPath = "";
        String extension = "";

        if (os.contains("win")) {
            subPath = "win/";
            extension = ".exe";
        } else if (os.contains("mac")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                subPath = "mac/arm64/";
            } else {
                subPath = "mac/intel/";
            }
        } else {
             subPath = "linux/"; 
        }

        String resourcePath = "/bin/" + subPath + toolName + extension;
        
        InputStream in = NativeTools.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new FileNotFoundException("Binary not found inside JAR: " + resourcePath);
        }

        File tempExe = File.createTempFile(toolName + "-", extension);
        tempExe.deleteOnExit();
        
        // I fixed the order here: Copy FIRST, then set permissions.
        Files.copy(in, tempExe.toPath(), StandardCopyOption.REPLACE_EXISTING);
        tempExe.setExecutable(true); 
        
        return tempExe;
    }
}