package ij.io;

import ij.IJ;
import ij.util.NativeTools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FFmpegLoader {

    private static final int BACKWARD_CHUNK_SIZE = 40; 

    private File ffmpegBin = null;
    private final String sourcePath;
    private final int width;
    private final int height;
    private final double fps;
    private final int frameSize;

    // REPLACED: AtomicBoolean isLoading -> volatile long loadGeneration
    // This is the "Ticket Number". Incrementing it invalidates all previous threads instantly.
    private volatile long loadGeneration = 0;
    
    // Process references for forced cleanup
    private volatile Process forwardProcess;
    private volatile Process backwardProcess;

    // Scout Service for 5D (Z/C) caching
    private final ExecutorService scoutService = Executors.newFixedThreadPool(2);

    public FFmpegLoader(String sourcePath, int width, int height, double fps) {
        this.sourcePath = sourcePath;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.frameSize = width * height * 3;
        
        try {
            this.ffmpegBin = NativeTools.getBundledBinary("ffmpeg");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- PLAYBACK ---

    public synchronized void jumpTo(int startFrameIndex) {
        // 1. INVALIDATE OLD THREADS
        // Incrementing this means any thread running with the old number will quit loop immediately.
        loadGeneration++; 
        long myGen = loadGeneration; // Capture the token for the new threads

        // 2. KILL OLD PROCESSES (Free up file handles/locks immediately)
        killProcesses();

        // 3. START NEW THREADS
        // We pass 'myGen' so the thread knows its identity
        new Thread(() -> runForwardStream(startFrameIndex, myGen), "FFmpeg-Fwd-" + myGen).start();
        new Thread(() -> runBackwardStream(startFrameIndex - 1, myGen), "FFmpeg-Bwd-" + myGen).start();
    }

    public synchronized void stopLoading() {
        // Just incrementing the generation is enough to signal all threads to stop.
        loadGeneration++;
        killProcesses();
    }
    
    private void killProcesses() {
        if (forwardProcess != null) forwardProcess.destroyForcibly();
        if (backwardProcess != null) backwardProcess.destroyForcibly();
    }

    private void runForwardStream(int startFrame, long myGen) {
        try {
            double startTime = startFrame / fps;
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegBin.getAbsolutePath(), "-ss", String.format("%.3f", startTime), 
                "-i", sourcePath, "-f", "rawvideo", "-pix_fmt", "rgb24", "-v", "quiet", "pipe:1"
            );

            forwardProcess = pb.start(); // Save ref so we can kill it later
            InputStream stream = forwardProcess.getInputStream();
            byte[] buffer = new byte[frameSize];
            int currentFrame = startFrame;

            // THE ROBUST CHECK:
            // "Am I still the current generation?"
            while (loadGeneration == myGen) {
                if (!readFully(stream, buffer)) break;
                HoardManager.writeFrame(sourcePath, currentFrame, buffer, width, height);
                currentFrame++;
            }
        } catch (Exception e) {
            // Ignored: Expected when process is killed during jump
        } finally {
            // We rely on killProcesses() in jumpTo/stopLoading to handle cleanup
        }
    }

    private void runBackwardStream(int anchorFrame, long myGen) {
        int currentAnchor = anchorFrame;
        
        while (loadGeneration == myGen && currentAnchor >= 0) {
            int startFrame = Math.max(0, currentAnchor - BACKWARD_CHUNK_SIZE + 1);
            int framesToRead = currentAnchor - startFrame + 1;
            if (framesToRead <= 0) break;

            double startTime = startFrame / fps;
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    ffmpegBin.getAbsolutePath(), "-ss", String.format("%.3f", startTime), "-i", sourcePath,
                    "-frames:v", String.valueOf(framesToRead), "-f", "rawvideo", 
                    "-pix_fmt", "rgb24", "-v", "quiet", "pipe:1"
                );
                
                process = pb.start();
                backwardProcess = process;
                InputStream stream = process.getInputStream();
                byte[] buffer = new byte[frameSize];

                for (int i = 0; i < framesToRead; i++) {
                    if (loadGeneration != myGen) break; // Token Check
                    if (!readFully(stream, buffer)) break;
                    HoardManager.writeFrame(sourcePath, startFrame + i, buffer, width, height);
                }
                process.waitFor();
            } catch (Exception e) {
                // Ignore interrupts
            } finally {
                if (process != null) process.destroy();
            }
            currentAnchor = startFrame - 1;
        }
    }

    // --- 5D SCOUTING ---

    public void scoutNeighbors(int currentLinearFrame, int strideZ, int strideC) {
        submitScoutJob(currentLinearFrame + strideZ);
        submitScoutJob(currentLinearFrame - strideZ);
    }

    private void submitScoutJob(int targetFrame) {
        if (targetFrame < 0) return;

        // Scouts don't need the Generation Token because they are short-lived atomic tasks.
        // They just grab one frame and die.
        scoutService.submit(() -> {
            Process p = null;
            try {
                double startTime = targetFrame / fps;
                ProcessBuilder pb = new ProcessBuilder(
                    ffmpegBin.getAbsolutePath(), "-ss", String.format("%.3f", startTime), 
                    "-i", sourcePath, "-frames:v", "1", "-f", "rawvideo", 
                    "-pix_fmt", "rgb24", "-v", "quiet", "pipe:1"
                );

                p = pb.start();
                InputStream stream = p.getInputStream();
                byte[] buffer = new byte[frameSize];

                if (readFully(stream, buffer)) {
                    HoardManager.writeFrame(sourcePath, targetFrame, buffer, width, height);
                }
                p.waitFor();
            } catch (Exception e) { 
                /* scout fail */ 
            } finally {
                if (p != null) p.destroy();
            }
        });
    }
    
    private boolean readFully(InputStream in, byte[] buffer) throws Exception {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read == -1) return false; 
            offset += read;
        }
        return true;
    }
}