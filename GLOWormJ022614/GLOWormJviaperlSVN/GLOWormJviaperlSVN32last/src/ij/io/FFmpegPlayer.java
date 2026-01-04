package ij.io;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

public class FFmpegPlayer {

    // Adjust paths if needed, or rely on system PATH
    private static final String FFMPEG_PATH = "/usr/local/bin/ffmpeg"; 
    private static final String FFPROBE_PATH = "/usr/local/bin/ffprobe";
    
    private Process ffmpegProcess;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile boolean wasInterrupted = false;

    // Inner class to hold probed metadata
    private static class VideoMeta {
        int width, height; // The dimensions used for the pixel array (display)
        int rotWidth, rotHeight; // The dimensions used for the ImagePlus (rotation-corrected display)
        int rawWidth, rawHeight; // The dimensions of the bytes on the wire (coded)
        int rotRawWidth, rotRawHeight; // The dimensions used for the rotated raw bytes (rotation-corrected stream)
        double fps;
        int frameCount;
    }

    public ImagePlus play(String sourcePath) {
        // 1. Probe (Robust Key-Value Mode)
        VideoMeta meta = probeVideo(sourcePath);
        if (meta == null) {
            IJ.error("Failed to probe video: " + sourcePath);
            return null;
        }

        IJ.log(String.format("Final Config: %dx%d @ %.2f fps (%d frames)", 
            meta.rotWidth, meta.rotHeight, meta.fps, meta.frameCount));

        // 2. Start Background Capture (PRODUCER)
        // Must start BEFORE the consumer to prime the pipe.
        startCapture(sourcePath, meta);
        
        // 3. Setup Stack (CONSUMER)
        // HoardStack expects the frame count and dims to match the ImagePlus
        HoardStack stack = new HoardStack(meta.rotWidth, meta.rotHeight, meta.frameCount, sourcePath);
        ImagePlus imp = new ImagePlus(sourcePath, stack);
        
        // 4. Safety: Abort ffmpeg if the user closes the window
        ImagePlus.addImageListener(new ImageListener() {
            @Override
            public void imageOpened(ImagePlus imp) {}

            @Override
            public void imageClosed(ImagePlus closedImp) {
                // If the closed image is OUR image
                if (closedImp == imp) {
                    stop(); // Kill the process
                    ImagePlus.removeImageListener(this); // Cleanup
                }
            }

            @Override
            public void imageUpdated(ImagePlus imp) {}
        });

        return imp;
    }

    private void startCapture(final String sourcePath, final VideoMeta meta) {
        isRunning.set(true);
        wasInterrupted = false; // Reset status

        Thread captureThread = new Thread(() -> {
            try {
                // RAW STREAM: No scaling (-s), no cropping (-vf).
                // We want the exact bytes on disk/wire to prevent stride misalignment.
                ProcessBuilder pb = new ProcessBuilder(
                    FFMPEG_PATH,
                    "-i", sourcePath,
                    "-f", "rawvideo",
                    "-pix_fmt", "rgb24",
                    "-v", "error",
                    "pipe:1"
                );

                ffmpegProcess = pb.start();
                InputStream stream = ffmpegProcess.getInputStream();
                
                // Calculate frame size in bytes: W * H * 3 (RGB)
                int frameSize = meta.rotWidth * meta.rotHeight * 3;
             // 1. The Raw Buffer (What ffmpeg sends, potentially padded)
                byte[] rawBuffer = new byte[meta.rawWidth * meta.rawHeight * 3];
                
                // 2. The Clean Buffer (What Hoard expects, exactly WxH)
                boolean needsCrop = (meta.rotRawWidth != meta.rotWidth) || (meta.rotRawHeight != meta.rotHeight);
                byte[] cleanBuffer = needsCrop ? new byte[meta.rotWidth * meta.rotHeight * 3] : rawBuffer;            
                
                int frameIndex = 1; // 1-based index for ImageJ

                // --- CAPTURE LOOP ---
                // We read until EOF (-1) or until stop() is called.
                while (isRunning.get()) {
                    // Read exactly one frame
                    int offset = 0;
                    while (offset < frameSize) {
                        int read = stream.read(rawBuffer, offset, frameSize - offset);
                        if (read < 0) break; // EOF
                        offset += read;
                    }
                    
                    if (offset < frameSize) {
                        break; // End of stream (or incomplete final frame)
                    }

                 // 3. CROP LOGIC: Strip padding if necessary
                    if (needsCrop) {
//                        for (int y = 0; y < meta.rotHeight; y++) {
//                            // Copy row by row, ignoring the extra padding bytes at the end of the raw row
//                            System.arraycopy(
//                                rawBuffer, y * meta.rotRawWidth * 3, // Source: Skip to row start
//                                cleanBuffer, y * meta.rotWidth * 3,  // Dest: Pack tightly
//                                meta.rotWidth * 3                    // Length: Valid pixels only
//                            );
//                        }
                    	cleanBuffer = rawBuffer.clone();
                    } else {									//No cropping enacted in either case
                    	cleanBuffer = rawBuffer.clone();
                    }
                    
                    // Feed the Hoard
                    // HoardManager will handle header generation and caching
                    HoardManager.writeFrame(sourcePath, frameIndex, cleanBuffer, meta.rotWidth, meta.rotHeight);
                    
                    frameIndex++;
                }

                // --- LOGGING LOGIC ---
                if (wasInterrupted) {
                    IJ.log("CAPTURE INTERRUPTED: User closed the window.");
                } else {
                    IJ.log("CAPTURE COMPLETE: Processed video, "+(frameIndex-1)+" frames.");
                }
                
                // Ensure process is dead
                if (ffmpegProcess.isAlive()) {
                    ffmpegProcess.destroy(); 
                }

            } catch (Exception e) {
                if (!wasInterrupted) {
                    IJ.handleException(e);
                }
            }
        });
        captureThread.start();
    }

    public void stop() {
        // 1. Mark as interrupted so logging knows the context
        wasInterrupted = true;
        
        // 2. Signal the loop to break
        isRunning.set(false);
        
        // 3. Kill the process immediately
        if (ffmpegProcess != null && ffmpegProcess.isAlive()) {
            ffmpegProcess.destroyForcibly();
            // IJ.log("FFmpeg process terminated."); // Optional verbose log
        }
    }

 
    private VideoMeta probeVideo(String path) {
        // 1. ROBUST COMMAND:
        // -show_streams: Dumps ALL stream info (dims, tags, side data).
        // -count_frames: Forces accurate frame count.
        // -of default=noprint_wrappers=1: Outputs clean "key=value" lines.
        ProcessBuilder pb = new ProcessBuilder(
            FFPROBE_PATH,
            "-v", "error",
            "-count_frames",
            "-select_streams", "v:0",
            "-show_streams",
            "-of", "default=noprint_wrappers=1",
            path
        );

        VideoMeta meta = new VideoMeta();
        // Defaults
        int rotation = 0;

        try {
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;

            // 2. LINE-BY-LINE PARSING (The "Where-ever it lies" logic)
            while ((line = reader.readLine()) != null) {
                // Determine Key and Value
                int splitIdx = line.indexOf('=');
                if (splitIdx == -1) continue;
                
                String key = line.substring(0, splitIdx).trim();
                String val = line.substring(splitIdx + 1).trim();

                // 3. CAPTURE DATA
                switch (key) {
                    case "width":
                        meta.width = Integer.parseInt(val);
                        break;
                    case "height":
                        meta.height = Integer.parseInt(val);
                        break;
                    case "coded_width":
                        meta.rawWidth = Integer.parseInt(val);
                        break;
                    case "coded_height":
                        meta.rawHeight = Integer.parseInt(val);
                        break;
                    case "avg_frame_rate":
                        meta.fps = parseFps(val);
                        break;
                    case "nb_read_frames":
                        meta.frameCount = Integer.parseInt(val);
                        break;
                    // CAPTURE ROTATION (Standard Tag)
                    case "TAG:rotate": 
                        rotation = parseRotation(val);
                        break;
                    // CAPTURE ROTATION (Side Data - occurs in some containers)
                    case "rotation":
                        rotation = parseRotation(val);
                        break;
                }
            }
            p.waitFor();
            
            // 4. LOGIC RECONCILIATION
            // Fallback for frame count if 'nb_read_frames' was missing
            if (meta.frameCount == 0) {
                 IJ.log("Warning: nb_read_frames missing. Check ffprobe version.");
            }

            
            // Rotation Logic: Swap if 90 or 270 degrees
            if (Math.abs(rotation) == 90 || Math.abs(rotation) == 270) {
                meta.rotWidth = meta.height;
                meta.rotHeight = meta.width;
                meta.rotRawWidth = meta.rawHeight;
                meta.rotRawHeight = meta.rawWidth;
               IJ.log("Rotation Detected (" + rotation + "°). Swapping dims: " + meta.rotWidth + "x" + meta.rotHeight);
            } else {
                meta.rotWidth = meta.width;
                meta.rotHeight = meta.height;
                meta.rotRawWidth = meta.rawWidth;
                meta.rotRawHeight = meta.rawHeight;
            }
            
            return meta;

        } catch (Exception e) {
            IJ.handleException(e);
            return null;
        }
    }
    
    private double parseFps(String fpsString) {
        try {
            if (fpsString.contains("/")) {
                String[] parts = fpsString.split("/");
                return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            }
            return Double.parseDouble(fpsString);
        } catch (Exception e) {
            return 25.0; // Fail-safe default
        }
    }

    private int parseRotation(String val) {
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}