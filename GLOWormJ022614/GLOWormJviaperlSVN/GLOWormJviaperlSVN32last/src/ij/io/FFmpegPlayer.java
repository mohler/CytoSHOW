package ij.io;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;

import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class FFmpegPlayer {

    private static final String FFPROBE_PATH = "/usr/local/bin/ffprobe"; // Adjust if needed
    
    // We delegate all the heavy lifting to the Loader
    private FFmpegLoader loader;
    private ImagePlus attachedImp;

    private static class VideoMeta {
        int width, height, rotWidth, rotHeight;
        double fps;
        int frameCount;
    }

    public ImagePlus play(String sourcePath) {
        VideoMeta meta = probeVideo(sourcePath);
        if (meta == null) {
            IJ.error("Failed to probe video: " + sourcePath);
            return null;
        }

        IJ.log(String.format("CytoSHOW: %dx%d @ %.2f fps (%d frames)", 
            meta.rotWidth, meta.rotHeight, meta.fps, meta.frameCount));

        // 1. Initialize the Loader (The Engine)
        loader = new FFmpegLoader(sourcePath, meta.rotWidth, meta.rotHeight, meta.fps);
        loader.jumpTo(1); 
        
        // 2. Setup the Stack (The View)
        HoardStack stack = new HoardStack(meta.rotWidth, meta.rotHeight, meta.frameCount, sourcePath);
        attachedImp = new ImagePlus(sourcePath, stack);
        
        // 3. Setup Listeners (Cleanup & Scrubbing)
        ImagePlus.addImageListener(new ImageListener() {
            @Override
            public void imageOpened(ImagePlus imp) {
                if (imp == attachedImp) {
                    attachScrubListener(imp);
                }
            }

            @Override
            public void imageClosed(ImagePlus closedImp) {
                if (closedImp == attachedImp) {
                    stop();
                    ImagePlus.removeImageListener(this);
                }
            }

            @Override
            public void imageUpdated(ImagePlus imp) {}
        });

        return attachedImp;
    }

    /**
     * Attaches listeners for Mouse Release (Slider) and Wheel Stop (Debounce).
     */
    private void attachScrubListener(ImagePlus imp) {
        Window win = imp.getWindow();
        if (!(win instanceof StackWindow)) return;
        
        StackWindow swin = (StackWindow) win;
        
        // 1. Identify the controlling dimension (T or Z)
        ScrollbarWithLabel foundScroll = null;
        boolean isTime = false;
        
        if (swin.tSelector != null) {
            foundScroll = swin.tSelector;
            isTime = true;
        } else if (swin.zSelector != null) {
            foundScroll = swin.zSelector;
            isTime = false;
        }

        if (foundScroll != null) {
            final ScrollbarWithLabel targetScroll = foundScroll;
            final boolean finalIsTime = isTime;

            // --- A. SLIDER RELEASE LOGIC (Click & Drag) ---
            targetScroll.getScrollBar().addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    // Immediate jump on release
                    int targetFrame = targetScroll.getScrollBar().getValue();
                    jump(targetFrame);
                }
            });

            // --- B. MOUSE WHEEL LOGIC (Debounced) ---
            // 1. Create a timer that fires ONLY when scrolling stops for 250ms...or 220ms current test
            final javax.swing.Timer debounceTimer = new javax.swing.Timer(220, e -> {
                // This code runs on the EDT after the user stops scrolling
                int currentFrame = finalIsTime ? imp.getFrame() : imp.getSlice();
                jump(currentFrame);
            });
            debounceTimer.setRepeats(false); // Only fire once per scroll burst

            // 2. The Listener: Just keeps resetting the timer
            java.awt.event.MouseWheelListener wheelTracker = new java.awt.event.MouseWheelListener() {
                @Override
                public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                    // Reset the countdown every time the wheel moves
                    debounceTimer.restart();
                }
            };

            // 3. Attach to relevant components
            // Attach to Window (so scrolling over the image works)
            swin.addMouseWheelListener(wheelTracker);
            // Attach to Slider (so scrolling over the bar works)
            targetScroll.getScrollBar().addMouseWheelListener(wheelTracker);

            IJ.log("Scrubbing (Drag & Wheel) enabled on " + (isTime ? "Time" : "Z") + " axis.");
        }
    }
    
    
    public void jump(int frameIndex) {
        if (loader != null) {
            IJ.log("Seek: " + frameIndex);
            loader.jumpTo(frameIndex);
        }
    }

    public void stop() {
        if (loader != null) {
            loader.stopLoading();
        }
    }

    // --- PROBE LOGIC (Unchanged) ---
    private VideoMeta probeVideo(String path) {
        ProcessBuilder pb = new ProcessBuilder(
            FFPROBE_PATH, "-v", "error", "-count_packets", "-select_streams", "v:0",
            "-show_streams", "-of", "default=noprint_wrappers=1", path
        );

        VideoMeta meta = new VideoMeta();
        int rotation = 0;

        try {
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf('=');
                if (idx == -1) continue;
                String key = line.substring(0, idx).trim();
                String val = line.substring(idx + 1).trim();
                switch (key) {
                    case "width": meta.width = Integer.parseInt(val); break;
                    case "height": meta.height = Integer.parseInt(val); break;
                    case "avg_frame_rate": meta.fps = parseFps(val); break;
                    case "nb_read_packets": meta.frameCount = Integer.parseInt(val); break;
                    case "rotation": case "TAG:rotate": rotation = parseRotation(val); break;
                }
            }
            p.waitFor();

            if (Math.abs(rotation) == 90 || Math.abs(rotation) == 270) {
                meta.rotWidth = meta.height; meta.rotHeight = meta.width;
            } else {
                meta.rotWidth = meta.width; meta.rotHeight = meta.height;
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
        } catch (Exception e) { return 25.0; }
    }

    private int parseRotation(String val) {
        try { return Integer.parseInt(val); } catch (Exception e) { return 0; }
    }
}