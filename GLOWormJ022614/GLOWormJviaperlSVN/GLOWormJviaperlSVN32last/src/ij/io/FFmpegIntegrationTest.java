package ij.io;

import ij.IJ;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class FFmpegIntegrationTest {

    public static void main(String[] args) {
        System.out.println("--- FFMPEG LIVE HOARD TEST ---");

        // 1. Set a tight limit for the test (200 MB)
        // This ensures we trigger the deletion logic quickly.
        CacheGovernor.setMaxHoardSize(200L * 1024 * 1024);
        System.out.println("Governor Limit: 200 MB");

        // Video Settings
        int width = 1280;
        int height = 720;
        int channels = 3; // RGB
        int frameSize = width * height * channels; // approx 2.7 MB per frame

        // 2. Build the "Legit" CLI Call
        // -f lavfi -i testsrc: Generates a test pattern video (no input file needed)
        // -f rawvideo: Output uncompressed pixel data
        // -pix_fmt rgb24: 3 bytes per pixel
        // pipe:1: Output to Standard Out (which Java will read)
        ProcessBuilder pb = new ProcessBuilder(
            "/Users/wmohler/Downloads/ffmpeg",
            "-f", "lavfi",
            "-i", "testsrc=size=1280x720:rate=30", 
            "-pix_fmt", "rgb24",
            "-f", "rawvideo",
            "pipe:1"
        );
        
        // Redirect stderr to console so we can see ffmpeg's debug info
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process ffmpeg = null;
        try {
            ffmpeg = pb.start();
            InputStream ffmpegOut = ffmpeg.getInputStream();

            byte[] frameBuffer = new byte[frameSize];
            int frameCount = 0;
            int bytesRead = 0;

            System.out.println("Starting capture loop. Watch folder: " + CacheLocation.getHoardLocation());
            
            // 3. Read loop: Pull exact frame sizes from the pipe
            // We loop until we hit 100 frames (approx 270MB) to guarantee we break the 200MB limit
            while (frameCount < 100) {
                
                // Read one full frame into the buffer
                int offset = 0;
                while (offset < frameSize) {
                    int count = ffmpegOut.read(frameBuffer, offset, frameSize - offset);
                    if (count < 0) break; // EOF
                    offset += count;
                }
                
                if (offset < frameSize) break; // Incomplete frame (End of stream)

                frameCount++;
                
                // 4. Feed the Hoard
                String filename = String.format("frame_%04d.raw", frameCount);
                Path p = HoardManager.writeChunk(filename, frameBuffer);

                if (p != null) {
                    System.out.println("Saved " + filename + " [" + (offset/1024/1024) + "MB]");
                }
            }

        } catch (IOException e) {
            System.err.println("Error running ffmpeg. Is it installed and in your PATH?");
            e.printStackTrace();
        } finally {
            if (ffmpeg != null) ffmpeg.destroy();
            // Restore default limit
            CacheGovernor.setMaxHoardSize(10L * 1024 * 1024 * 1024);
            System.out.println("Test Complete.");
        }
    }
}