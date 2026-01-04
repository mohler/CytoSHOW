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
        nImageSlices = count;
        names = new String[nImageSlices];
        labels = new String[nImageSlices];
       
        try {
            this.hoardDir = CacheLocation.getHoardLocation();
        } catch (IOException e) {
            throw new RuntimeException("HoardStack: Cannot access cache location.", e);
        }

     }
    // ImageJ calls this method to know how many scrollbar ticks to draw
    @Override
    public int getSize() {
        return nImageSlices;
    }
    
    /**
     * The Magic Method.
     * ImageJ calls this when the user scrolls to slice 'n'.
     * We calculate what the filename *should* be, and try to load it.
     */
    @Override
    public ImageProcessor getProcessor(int n) {
        String filename = CacheNaming.generateCacheName(sourceAbsolutePath, n, "tif");
        Path cachedFile = hoardDir.resolve(filename);

        // CLEANER FIX: If this is Frame 1, wait until Frame 2 exists.
        // This guarantees Frame 1 is fully written and closed by the producer.
        if (n == 1) {
            String nextFilename = CacheNaming.generateCacheName(sourceAbsolutePath, 2, "tif");
            waitForPath(hoardDir.resolve(nextFilename));
        }

        if (Files.exists(cachedFile)) {
            ImagePlus imp = IJ.openImage(cachedFile.toString());
            if (imp != null) {
                return imp.getProcessor();
            }
        }

        return createPlaceholder(n);
    }

    /**
     * Blocks for up to 2.5 seconds waiting for the path to exist.
     */
    private void waitForPath(Path p) {
        long start = System.currentTimeMillis();
        // 2500ms timeout prevents permanent hang if video is only 1 frame long
        while (!Files.exists(p) && (System.currentTimeMillis() - start < 25000)) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
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
        String msg = "Loading\nFrame\n" + n + "...";
        
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