package ij.io;

import ij.IJ;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public class HoardStack extends VirtualStack {

    private final String sourceAbsolutePath;
    private final Path hoardDir;
    private int nSlices;

    /**
     * @param width  Width of the video
     * @param height Height of the video
     * @param count  Total number of frames (estimated or exact)
     * @param sourceAbsolutePath The full path to the original video (used for naming hash)
     */
    public HoardStack(int width, int height, int count, String sourceAbsolutePath) {
        // We pass 'null' for the color model and 'hoardDir' string to super
        super(width, height, null, ""); 
        this.sourceAbsolutePath = sourceAbsolutePath;
        this.nSlices = count;
        
        try {
            this.hoardDir = CacheLocation.getHoardLocation();
        } catch (IOException e) {
            throw new RuntimeException("HoardStack: Cannot access cache location.", e);
        }

     }
    // ImageJ calls this method to know how many scrollbar ticks to draw
    @Override
    public int getSize() {
        return nSlices;
    }
    
    /**
     * The Magic Method.
     * ImageJ calls this when the user scrolls to slice 'n'.
     * We calculate what the filename *should* be, and try to load it.
     */
    @Override
    public ImageProcessor getProcessor(int n) {
        // 1. Calculate the expected filename (1-based index)
        String filename = CacheNaming.generateCacheName(sourceAbsolutePath, n, "tif");
        Path cachedFile = hoardDir.resolve(filename);

        // 2. Check if the frame has been captured/cached yet
        if (Files.exists(cachedFile)) {
            // LOAD IT
            // IJ.openImage is robust: handles the TIFF header we created
            ImagePlus imp = IJ.openImage(cachedFile.toString());
            if (imp != null) {
                return imp.getProcessor();
            }
        }

        // 3. Fallback (Frame missing or not yet captured)
        // Return a black/blank frame so the UI doesn't crash while waiting for ffmpeg
        return createPlaceholder(n);
    }

    /**
     * Creates a placeholder image with "Loading" text.
     * Useful if the user scrolls ahead of the capture stream.
     */
    private ImageProcessor createPlaceholder(int n) {
        // 1. Create the black background
        ImageProcessor ip = new ColorProcessor(getWidth(), getHeight());
        
        // 2. Set up the text properties
        ip.setColor(java.awt.Color.WHITE);
        
        // Scale font size based on image height (approx 5% of height)
        int fontSize = Math.max(12, getHeight() / 20); 
        ip.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, fontSize));
        
        // 3. Construct the message
        String msg = "Loading Frame " + n + "...";
        
        // 4. Center the text
        // getStringWidth is an ImageJ helper that calculates pixel width of the text
        int textWidth = ip.getStringWidth(msg);
        int x = (getWidth() - textWidth) / 2;
        int y = getHeight() / 2;
        
        // 5. Draw it
        ip.drawString(msg, x, y);
        
        return ip;
    }
    
    
    /**
     * Optimization: Delete the file from the hoard when the stack is closed?
     * Usually NO for a cache (keep it for next time), but if you want 
     * session-only caching, you can override trim() or close().
     * For now, we leave it to the Governor.
     */
}