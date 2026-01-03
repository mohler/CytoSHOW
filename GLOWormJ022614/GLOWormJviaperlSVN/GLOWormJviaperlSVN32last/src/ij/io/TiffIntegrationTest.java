package ij.io;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.Opener;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

public class TiffIntegrationTest {

    public static void main(String[] args) throws IOException {
        System.out.println("--- TESTING TIFF HOARD WRITER (Java 8) ---");

        // 1. Setup Dummy Data (Small 100x50 frame)
        int width = 100;
        int height = 50;
        int size = width * height * 3; // RGB
        byte[] rawData = new byte[size];
        
        // Fill with random noise so we can see it's real data
        new Random().nextBytes(rawData);

        // 2. Define a fake source file (to test naming)
        String fakeSource = "C:\\Data\\TestVideo.avi"; // Windows style
        if (IJ.isMacOSX() || IJ.isLinux()) {
            fakeSource = "/home/user/data/TestVideo.avi";
        }

        System.out.println("Generating Frame...");
        
        // 3. WRITE THE FRAME (Uses HoardManager + TiffPatcher)
        Path savedPath = HoardManager.writeFrame(fakeSource, 1, rawData, width, height);

        if (savedPath == null) {
            System.err.println("FATAL: Failed to write file.");
            return;
        }

        System.out.println("Saved to: " + savedPath.toString());

        // 4. VERIFY READABILITY (The moment of truth)
        // We use ImageJ's native Opener to ensure it likes our format.
        System.out.println("Attempting to open with ImageJ...");
        ImagePlus imp = new Opener().openImage(savedPath.toString());

        if (imp == null) {
            System.err.println("FAIL: ImageJ could not open the file!");
            return;
        }

        // 5. INSPECT TIFF STRUCTURE (The "Anti-Idiot" Check)
        FileInfo fi = imp.getOriginalFileInfo();
        
        System.out.println("\n--- IMAGEJ FILE INSPECTION ---");
        System.out.println("Width: " + imp.getWidth() + " (Expected: " + width + ")");
        System.out.println("Height: " + imp.getHeight() + " (Expected: " + height + ")");
        System.out.println("Type: " + getTypeName(imp.getType()));
        
        if (fi != null) {
            System.out.println("Rows Per Strip: " + fi.rowsPerStrip);
            
            // THE CRITICAL CHECK
            if (fi.rowsPerStrip == height) {
                System.out.println("✅ OPTIMIZATION VERIFIED: Image is a single contiguous strip.");
            } else {
                System.err.println("❌ WARNING: Image is fragmented! (RowsPerStrip=" + fi.rowsPerStrip + ")");
            }
        } else {
            System.err.println("Warning: Could not read FileInfo (TIFF header might be weird).");
        }

        imp.show(); // Verify visual (if you have the GUI running)
        System.out.println("\nTest Complete. If you see the image, we are golden.");
    }
    
    private static String getTypeName(int type) {
        switch (type) {
            case ImagePlus.COLOR_RGB: return "COLOR_RGB";
            case ImagePlus.GRAY8: return "GRAY8";
            default: return "Unknown (" + type + ")";
        }
    }
}