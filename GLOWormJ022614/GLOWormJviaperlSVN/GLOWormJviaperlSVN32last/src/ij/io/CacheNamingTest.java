package ij.io;

import java.io.File;

public class CacheNamingTest {

    public static void main(String[] args) {
        System.out.println("--- TESTING SMART CACHE NAMING ---");

        // Scenario 1: Standard Windows Path
        testCase(
            "C:\\Users\\Steve\\Documents\\MyVideo.avi", 
            1
        );

        // Scenario 2: Identical filename, different folder (Should have different Hash prefix)
        testCase(
            "D:\\Backup\\MyVideo.avi", 
            1
        );

        // Scenario 3: Mac/Linux Path
        testCase(
            "/home/steve/data/experiment_run_1.mov", 
            500
        );

        // Scenario 4: Nasty characters in filename (Spaces, Parens, Symbols)
        testCase(
            "C:\\Data\\Project X (Final) [v2] #test!.mp4", 
            9999
        );
    }

    private static void testCase(String path, int frame) {
        System.out.println("Input Path:  " + path);
        System.out.println("Frame Index: " + frame);
        
        String result = CacheNaming.generateCacheName(path, frame, "raw");
        
        System.out.println("Generated:   " + result);
        
        // Validation Checks
        if (result.contains("\\") || result.contains("/")) {
            System.err.println("FAIL: Filename contains directory separators!");
        }
        if (result.matches(".*[^a-zA-Z0-9._-].*")) {
            System.err.println("FAIL: Filename contains illegal characters!");
        }
        
        System.out.println("------------------------------------------------");
    }
}