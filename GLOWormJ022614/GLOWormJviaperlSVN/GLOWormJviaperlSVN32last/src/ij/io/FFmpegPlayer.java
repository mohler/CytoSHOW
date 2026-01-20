package ij.io;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.gui.HoardWindow;
import ij.util.NativeTools;

import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import javax.swing.Timer;

public class FFmpegPlayer {

    private FFmpegLoader loader;
    private ImagePlus attachedImp;
    
    // The Universal Debouncer
    // Fires 220ms after the LAST update event (scrolling stopped)
    private Timer debounceTimer; 

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

        // 1. Initialize Loader
        loader = new FFmpegLoader(sourcePath, meta.rotWidth, meta.rotHeight, meta.fps);
        loader.jumpTo(0); 
        
        // 2. Setup View
        HoardStack stack = new HoardStack(meta.rotWidth, meta.rotHeight, meta.frameCount, sourcePath);
        attachedImp = new ImagePlus(sourcePath, stack);
        
        // 3. THE FIX: Explicitly create the Custom Window.
        // This constructor registers the window with ImageJ's WindowManager.
        new HoardWindow(attachedImp); 
        
        // 4. Initialize Logic Immediately (Don't wait for 'imageOpened')
        setupDebouncer();
        
        // Force focus so Arrow Keys work instantly
        if (attachedImp.getWindow() != null) {
            attachedImp.getWindow().getCanvas().requestFocus();
        }

        // 5. Attach Maintenance Listeners (Cleanup & Updates)
        ImagePlus.addImageListener(new ImageListener() {
            @Override
            public void imageOpened(ImagePlus imp) {
                // No-op: We already initialized above.
            }

            @Override
            public void imageClosed(ImagePlus closedImp) {
                if (closedImp == attachedImp) {
                    stop();
                    ImagePlus.removeImageListener(this);
                }
            }

            @Override
            public void imageUpdated(ImagePlus imp) {
                if (imp == attachedImp) {
                    // This captures Arrow Keys, Mouse Wheel, ROI nudges, etc.
                    // (Passed through by HoardWindow's canvas listener)
                    if (debounceTimer != null) debounceTimer.restart();
                }
            }
        });

        return attachedImp;
    }
    
    
    private void setupDebouncer() {
        debounceTimer = new Timer(220, e -> {
            if (attachedImp == null) return;
            
            // 1. Get current position
            int c = attachedImp.getChannel();
            int z = attachedImp.getSlice();
            int t = attachedImp.getFrame();
            int currentLinearIndex = attachedImp.getStackIndex(c, z, t); 
            
            // 2. Sync Loader (Visual)
            jump(currentLinearIndex);

            // 3. Fire Scouts (Background)
            if (loader != null) {
                int nChannels = attachedImp.getNChannels();
                loader.scoutNeighbors(currentLinearIndex, nChannels, 1);
            }
        });
        debounceTimer.setRepeats(false);
    }
    
    /**
     * Adds the "Boomerang" listener to Scrollbars.
     * If user clicks the scrollbar, it steals focus. This forces it back to the Canvas
     * when they release the mouse, so Arrow Keys work again immediately.
     */
    private void fixScrollbarFocus(ImagePlus imp) {
        Window win = imp.getWindow();
        if (!(win instanceof StackWindow)) return;
        StackWindow swin = (StackWindow) win;
        
        MouseAdapter boomerang = new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                // Give focus back to the image so arrows work
                swin.getCanvas().requestFocus();
            }
        };

        if (swin.tSelector != null) swin.tSelector.getScrollBar().addMouseListener(boomerang);
        if (swin.zSelector != null) swin.zSelector.getScrollBar().addMouseListener(boomerang);
    }

    public void jump(int frameIndex) {
        if (loader != null) loader.jumpTo(frameIndex);
    }

    public void stop() {
        if (loader != null) loader.stopLoading();
        if (debounceTimer != null) debounceTimer.stop();
    }

    // --- PROBE LOGIC (Preserved) ---
    private VideoMeta probeVideo(String path) {
        File ffprobeBin = null;
        try {
            ffprobeBin = NativeTools.getBundledBinary("ffprobe");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (ffprobeBin != null) {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobeBin.getAbsolutePath(), "-v", "error", "-count_packets", "-select_streams", "v:0",
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
        return null;
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