package ij.io;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.util.NativeTools;

public class FFMPEG_FileSaver {

    static File ffmpegBin;

    public static void saveVideo(String impIDstring, String outputFileString, String fpsString) {
        
        int id = 0;
        try {
             id = Integer.parseInt(impIDstring);
        } catch (NumberFormatException e) {
             System.err.println("Invalid Image ID format: " + impIDstring);
             return;
        }

        ImagePlus imp = WindowManager.getImage(id);
        if (imp == null) {
            System.err.println("Could not find open image with ID: " + id);
            return;
        }

        Process p = null;
        OutputStream ffmpegInput = null;

        try {
            int width = imp.getWidth();
            int height = imp.getHeight();
            
            // Start FFmpeg expecting PNG input
            p = startStreamProcess(outputFileString, fpsString, width, height);
            
            if (p == null) {
                System.err.println("Failed to launch FFmpeg executable.");
                return;
            }

            ffmpegInput = p.getOutputStream();
            ImageStack stack = imp.getStack();
            int nFrames = stack.getSize();

            System.out.println("DEBUG: Starting write loop for " + nFrames + " frames.");

            for (int i = 1; i <= nFrames; i++) {
                
                ij.process.ImageProcessor ip = stack.getProcessor(i);
                
                // Convert to BufferedImage for ImageIO
                BufferedImage bi = ip.getBufferedImage();
                
                try {
                    // Write PNG. This format safely concatenates in a pipe.
                    ImageIO.write(bi, "png", ffmpegInput);
                    ffmpegInput.flush();
                } catch (IOException pipeException) {
                    System.err.println("CRITICAL: Broken Pipe at frame " + i);
                    if (!p.isAlive()) {
                        System.err.println("FFmpeg exit code: " + p.exitValue());
                    }
                    throw pipeException; 
                }
                
                if(i % 10 == 0) ij.IJ.showStatus("Exporting frame " + i + "/" + nFrames);
            }
            
            ffmpegInput.close();
            p.waitFor();
            System.out.println("Conversion complete: " + outputFileString);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (p != null && p.isAlive()) p.destroy();
        }
    }

    private static Process startStreamProcess(String outputFileString, String fpsString, int width, int height) throws IOException {

        File outputFile = new File(outputFileString);
        Double fps = Double.valueOf(fpsString);

        try {
            ffmpegBin = NativeTools.getBundledBinary("ffmpeg");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        List<String> cmd = new ArrayList<>();

        cmd.add(ffmpegBin.getAbsolutePath());
        cmd.add("-y"); 

        // Input Configuration
        cmd.add("-f"); cmd.add("image2pipe"); 
        
        // SWITCH TO PNG
        cmd.add("-c:v"); cmd.add("png"); 
        
        cmd.add("-s"); cmd.add(width + "x" + height);
        cmd.add("-framerate"); cmd.add(String.valueOf(fps));
        cmd.add("-i"); cmd.add("-");          

        // Output Configuration
        String filename = outputFile.getName().toLowerCase();
        
        if (filename.endsWith(".mp4")) {
            cmd.add("-c:v"); cmd.add("libx264");
            cmd.add("-pix_fmt"); cmd.add("yuv420p");
            cmd.add("-crf"); cmd.add("20");
            cmd.add("-preset"); cmd.add("slow");
        } else if (filename.endsWith(".webm")) {
            cmd.add("-c:v"); cmd.add("libvpx-vp9");
            cmd.add("-b:v"); cmd.add("0");
            cmd.add("-crf"); cmd.add("30");
        } else if (filename.endsWith(".mov")) {
            cmd.add("-c:v"); cmd.add("prores_ks");
            cmd.add("-profile:v"); cmd.add("3");
        } else {
            cmd.add("-c:v"); cmd.add("libx264");
            cmd.add("-pix_fmt"); cmd.add("yuv420p");
            cmd.add("-crf"); cmd.add("20");
            cmd.add("-preset"); cmd.add("slow");
        }

        cmd.add("-vf");
        cmd.add("pad=ceil(iw/2)*2:ceil(ih/2)*2");
        cmd.add(outputFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true); 
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        System.out.println("Running FFmpeg: " + String.join(" ", cmd));
        
        return pb.start();
    }
}