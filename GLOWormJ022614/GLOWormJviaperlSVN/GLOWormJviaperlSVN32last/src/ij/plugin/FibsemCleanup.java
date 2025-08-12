package ij.plugin;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.*;
import java.io.File;
import java.util.concurrent.*;

/**
 * This plugin processes each Z-slice of an open image using a
 * fixed-size thread pool for concurrent execution. This version
 * incorporates the user's specific macro logic, including the
 * handling of intermediate image objects and saving methods,
 * while ensuring thread safety.
 *
 * To use this plugin:
 * 1. Save the code as FibsemCleanup.java in your ImageJ/plugins folder.
 * 2. Restart ImageJ. The plugin will appear in the Plugins menu.
 * 3. Open one or more images you wish to process.
 * 4. Run the plugin from the menu.
 */
public class FibsemCleanup implements PlugIn {

    // A shared lock object to synchronize access to non-thread-safe ImageJ methods.
    private final Object lock = new Object();

    @Override
    public void run(String arg) {
        // Get a list of IDs for all open images.
        int[] impIDs = WindowManager.getIDList();
        if (impIDs == null || impIDs.length == 0) {
            IJ.error("No images are open.");
            return;
        }

        // Determine the number of available processors to create an optimal-sized thread pool.
        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(processors);
        IJ.log("Using a thread pool with " + processors + " threads.");

        // Loop through each open image ID.
        for (int impID : impIDs) {
            ImagePlus sourceImp = WindowManager.getImage(impID);
            if (sourceImp == null) {
                continue; // Skip if image is no longer available
            }

            // Set the current image temporarily to get its directory.
            synchronized (lock) {
                WindowManager.setTempCurrentImage(sourceImp);
            }
            String path = IJ.getDirectory("image");
            synchronized (lock) {
                WindowManager.setTempCurrentImage(null); // Clear the temporary current image
            }
            if (path == null) {
                IJ.error("Could not determine image directory.");
                return;
            }

            // Get image dimensions from the source ImagePlus object.
            int zDepth = sourceImp.getNSlices();
            String sourceName = sourceImp.getTitle();

            // Create a new directory for the processed images.
            String processedDirName = sourceName.substring(0, sourceName.lastIndexOf('.')) + "_processed";
            String savePath = path + processedDirName + File.separator;
            String destination = savePath + sourceName;

            try {
                new File(savePath).mkdirs();
            } catch (Exception e) {
                IJ.error("Could not create directory: " + savePath);
                continue;
            }

            // Loop through each Z-slice and submit a processing task to the executor.
            for (int z = 1; z <= zDepth; z++) {
                final int finalZ = z;
                
                // Submit the processing task for this slice to the thread pool.
                executor.submit(() -> {
                    try {
                        // Define the full path for the output file
                        String fullOutputPath = destination.replace(".tif", "") +"_"+IJ.pad(finalZ,6)+".tif";
                        
                        // Check if the output file already exists. If so, skip processing this slice.
                        if (new File(fullOutputPath).exists()) {
                            IJ.log("Skipping slice " + finalZ + ", output file already exists.");
                            return; 
                        }
                        
                        ImagePlus currentSlice;
                        // All operations that affect the global state (like WindowManager or IJ.run)
                        // must be synchronized to prevent race conditions.
                        
                        // Get the current slice to process using the user's preferred method.
                        synchronized (lock) {
                            sourceImp.setPosition(1, finalZ, 1);
                            currentSlice = new ImagePlus("CurrentSlice=" + finalZ, sourceImp.getProcessor());
                            WindowManager.setTempCurrentImage(currentSlice);
                        }


                        // --- Processing steps as per the macro ---
                        
                        // 1. Remove Outliers (Dark and Bright)
                        synchronized (lock) {
                            IJ.run("Remove Outliers...", "radius=2 threshold=50 which=Dark slice");
                            IJ.run("Remove Outliers...", "radius=2 threshold=50 which=Bright slice");
                        }

                        // 2. FFT
                        // The FFT command creates a new image window which we will capture.
                        synchronized (lock) {
                            IJ.run("FFT");
                        }
                        ImagePlus fftImp = WindowManager.getCurrentImage();
                        
                        // Check if the FFT image was created.
                        if (fftImp == null) {
                            IJ.error("FFT failed to create an image for slice " + finalZ);
                            if (currentSlice != null) {
                                synchronized (lock) {
                                    currentSlice.close();
                                }
                            }
                            return;
                        }

                        fftImp.hide();
                        fftImp.setTitle("FFTfwd");

                        // 3. Clear elliptical regions in FFT image
                        synchronized (lock) {
                            fftImp.setRoi(new EllipseRoi(864, 2051, 1980, 2051, 0.04));
                            IJ.run(fftImp, "Clear", "stack");
                            fftImp.setRoi(new EllipseRoi(2142, 2051, 3258, 2051, 0.04));
                            IJ.run(fftImp, "Clear", "stack");
                            fftImp.killRoi();
                        }
                        
                        // 4. Inverse FFT
                        synchronized (lock) {
                            WindowManager.setTempCurrentImage(fftImp);
                            IJ.run("Inverse FFT");
                        }
                        
                        ImagePlus invFFTimp = WindowManager.getCurrentImage();
                        if (invFFTimp == null) {
                            IJ.error("Inverse FFT failed for slice " + finalZ);
                            synchronized (lock) {
                                fftImp.close();
                                currentSlice.close();
                            }
                            return;
                        }

                        invFFTimp.hide();
                        invFFTimp.setTitle("invFFTslice");
                        
                        // Close the FFT image window to free up memory.
                        synchronized (lock) {
                            fftImp.close();
                        }
                        
                        // 5. Enhance Local Contrast (CLAHE)
                        synchronized (lock) {
                            WindowManager.setTempCurrentImage(invFFTimp);
                            IJ.run("Enhance Local Contrast (CLAHE)", "blocksize=127 histogram=256 maximum=2.7 mask=*None*");
                        }
                        
                        // 6. Remove Outliers (Dark and Bright) - Second pass
                        synchronized (lock) {
                            IJ.run("Remove Outliers...", "radius=2 threshold=50 which=Dark slice");
                            IJ.run("Remove Outliers...", "radius=2 threshold=50 which=Bright slice");
                        }
                        
                        // 7. Gaussian Blur
                        synchronized (lock) {
                            IJ.run("Gaussian Blur...", "sigma=0.008 scaled slice");
                        }
                        
                        // 8. Save the processed slice.
                        synchronized (lock) {
                            IJ.saveAs(invFFTimp, "Tiff", fullOutputPath);
                        }
                        
                        // Close the temporary images to free up memory.
                        synchronized (lock) {
                            currentSlice.close();
                            invFFTimp.close();
                        }
                    } catch (Exception e) {
                        IJ.error("Error processing slice " + finalZ + ": " + e.getMessage());
                    }
                });
            }
        }

        // Shut down the executor and wait for all tasks to complete.
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            IJ.error("Plugin execution was interrupted.");
        }
        IJ.log("Plugin execution complete.");
    }
}
