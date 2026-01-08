package ij.io;

import ij.IJ;
import ij.util.NativeTools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class FFmpegLoader {

    private static final int BACKWARD_CHUNK_SIZE = 40; 

    private File ffmpegBin = null;
    private final String sourcePath;
    private final int width;
    private final int height;
    private final double fps;
    private final int frameSize;

    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private Thread forwardThread;
    private Thread backwardThread;
    private volatile Process forwardProcess;
    private volatile Process backwardProcess;

    public FFmpegLoader(String sourcePath, int width, int height, double fps) {
        this.sourcePath = sourcePath;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.frameSize = width * height * 3;
        try {
			this.ffmpegBin = NativeTools.getBundledBinary("ffmpeg");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        if (ffmpegBin == null) {
        	IJ.log("Cannot find binary ffmpeg");
        	return;
        }
    }

    public synchronized void jumpTo(int startFrameIndex) {
        stopLoading();
        isLoading.set(true);

        forwardThread = new Thread(() -> runForwardStream(startFrameIndex), "FFmpeg-Fwd");
        forwardThread.start();

        backwardThread = new Thread(() -> runBackwardStream(startFrameIndex - 1), "FFmpeg-Bwd");
        backwardThread.start();
    }

    public synchronized void stopLoading() {
        isLoading.set(false);
        if (forwardProcess != null && forwardProcess.isAlive()) forwardProcess.destroyForcibly();
        if (backwardProcess != null && backwardProcess.isAlive()) backwardProcess.destroyForcibly();

        try {
            if (forwardThread != null) forwardThread.join(100);
            if (backwardThread != null) backwardThread.join(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void runForwardStream(int startFrame) {
        try {
            double startTime = startFrame / fps;
            ProcessBuilder pb = new ProcessBuilder(
                ffmpegBin.getAbsolutePath(), "-ss", String.format("%.3f", startTime), 
                "-i", sourcePath, "-f", "rawvideo", "-pix_fmt", "rgb24", "-v", "quiet", "pipe:1"
            );

            forwardProcess = pb.start();
            InputStream stream = forwardProcess.getInputStream();
            byte[] buffer = new byte[frameSize];
            int currentFrame = startFrame;

            while (isLoading.get()) {
                if (!readFully(stream, buffer)) break;
                HoardManager.writeFrame(sourcePath, currentFrame, buffer, width, height);
                currentFrame++;
            }
        } catch (Exception e) {
            IJ.log("Forward Loader Error: " + e.getMessage());
        } finally {
            if (forwardProcess != null) forwardProcess.destroy();
        }
    }

    private void runBackwardStream(int anchorFrame) {
        int currentAnchor = anchorFrame;
        while (isLoading.get() && currentAnchor >= 0) {
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
                    if (!isLoading.get()) break;
                    if (!readFully(stream, buffer)) break;
                    HoardManager.writeFrame(sourcePath, startFrame + i, buffer, width, height);
                }
                process.waitFor();
            } catch (Exception e) {
                IJ.log("Backward Loader Error: " + e.getMessage());
            } finally {
                if (process != null) process.destroy();
            }
            currentAnchor = startFrame - 1;
        }
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