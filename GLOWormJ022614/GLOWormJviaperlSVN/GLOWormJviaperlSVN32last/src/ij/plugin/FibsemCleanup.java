package ij.plugin;

import ij.*;
import ij.process.*;
import ij.util.Tools;
import ij.plugin.FFT;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import ij.plugin.filter.Transformer;
import ij.gui.*;
import ij.io.FileSaver;
import ij.plugin.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
//        processors = 1;
        ExecutorService executor = Executors.newFixedThreadPool(processors);
        IJ.log("Using a thread pool with " + processors + " threads.");

        // Loop through each open image ID.
        for (int impID : impIDs) {
            WindowManager.setTempCurrentImage(WindowManager.getImage(impID));

            ImagePlus sourceImp = WindowManager.getTempCurrentImage();
            if (sourceImp == null) {
                continue; // Skip if image is no longer available
            }
            String path = IJ.getDirectory("image");
            WindowManager.setTempCurrentImage(null); // Clear the temporary current image
            
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
            
//            final ThreadLocal<Integer> finalThreadedZ = ThreadLocal.withInitial(() -> new Integer(1));
            final ThreadLocal<ImagePlus> sliceThreadedImp = ThreadLocal.withInitial(() -> new ImagePlus(sourceName+"_slice_XXXXXX", sourceImp.getProcessor()));
            final ThreadLocal<FFT> fwdFFT = ThreadLocal.withInitial(() -> new FFT());
            final ThreadLocal<FFT> invFFT = ThreadLocal.withInitial(() -> new FFT());
            final ThreadLocal<RankFilters> rankFilterA = ThreadLocal.withInitial(() -> new RankFilters());
            final ThreadLocal<RankFilters> rankFilterB = ThreadLocal.withInitial(() -> new RankFilters());
            final ThreadLocal<GaussianBlur> gaussBlur = ThreadLocal.withInitial(() -> new GaussianBlur());
            final ThreadLocal<CLAHE> claheFilter = ThreadLocal.withInitial(() -> new CLAHE());
            final ThreadLocal<ImagePlus> invFFTimp = ThreadLocal.withInitial(() -> new ImagePlus("invFFTimp" +"_XXXXXX.tif", sourceImp.getProcessor()));
            final ThreadLocal<FileSaver> tiffSaver = ThreadLocal.withInitial(() -> new FileSaver(new ImagePlus()));
            final ThreadLocal<Transformer> transformer = ThreadLocal.withInitial(() -> new Transformer());

            // Define the full path for the output file
            final ThreadLocal<String> fullThreadedOutputPath = ThreadLocal.withInitial(() -> destination.replace(".tif", "") +"_XXXXXX.tif");

            // Loop through each Z-slice and submit a processing task to the executor.
            for (int z = 1; z <= zDepth; z++) {

                String fullOutputPath = destination.replace(".tif", "") +"_"+IJ.pad(z,6)+".tif";

                // Check if the output file already exists. If so, skip processing this slice.
                if ((new File(fullOutputPath)).exists()){
                	IJ.log("Skipping slice " + z + ", output file already exists.");
                	continue; 
                }

                final int finalZ = z;

                executor.submit(() -> {
                    try {

                        // --- Processing steps as per the macro ---
                        
                        sliceThreadedImp.set(new ImagePlus(sourceName+"_slice_"+IJ.pad(finalZ, 6), sourceImp.getStack().getProcessor(finalZ)));

                        // 1. Remove Outliers (Dark and Bright)
                        	rankFilterA.get().rank(sliceThreadedImp.get().getProcessor(), 2, RankFilters.OUTLIERS, 1, 50);
                        	rankFilterA.get().rank(sliceThreadedImp.get().getProcessor(), 2, RankFilters.OUTLIERS, 0, 50);
                       

                        // 2. FFT
                        // The FFT command creates a new image window which we will capture.
                           fwdFFT.get().setImp(sliceThreadedImp.get());
                           fwdFFT.get().run("fft hide");
//                           ImagePlus fftFwdImp = WindowManager.getCurrentImage();
//                           fftFwdImp.hide();
                        // 3. Clear elliptical regions in FFT image
                            fwdFFT.get().getFwdFHT().setRoi(new EllipseRoi(864, 2051, 1980, 2051, 0.04));
                            IJ.run(fwdFFT.get().getFwdFHT(), "Clear", "stack");
                            fwdFFT.get().getFwdFHT().setRoi(new EllipseRoi(2142, 2051, 3258, 2051, 0.04));
                            IJ.run(fwdFFT.get().getFwdFHT(), "Clear", "stack");
                            fwdFFT.get().getFwdFHT().killRoi();
                        
                        // 4. Inverse FFT
                        
                            invFFT.get().setImp(fwdFFT.get().getFwdFHT());
                            invFFT.get().run("inverse hide");
                        if (invFFT.get().getInvFHT() == null) {
                            IJ.error("Inverse FFT failed for slice " + finalZ);
                                fwdFFT.get().getImp().close();
                                fwdFFT.get().getFwdFHT().close();
                                sliceThreadedImp.get().close();
                            
                            return;
                        }
                        invFFTimp.set(new ImagePlus("invFFTimp" +"_"+IJ.pad(finalZ,6)+".tif",invFFT.get().getInvFHT().getProcessor()));
                        
                        // 5. Enhance Local Contrast (CLAHE)
                        
                            CLAHE.run(invFFTimp.get(), 127, 256, 2.7f, null, null);
                        
                            
                        // 6. Remove Outliers (Dark and Bright) - Second pass
                    		rankFilterB.get().rank(invFFTimp.get().getProcessor(), 2, RankFilters.OUTLIERS, 1, 50);
                    		rankFilterB.get().rank(invFFTimp.get().getProcessor(), 2, RankFilters.OUTLIERS, 0, 50);
                        
                        
                        // 7. Gaussian Blur
                            gaussBlur.get().blurGaussian(invFFTimp.get().getProcessor(), 0.008, 0.008, 0.002);
                            
                        // 8. Rotate right 90%
                            transformer.get().setup("right", invFFTimp.get());
                            transformer.get().run(null);
                        
                        
                        // 9. Save the processed slice.
                            tiffSaver.set(new FileSaver(invFFTimp.get()));
                            fullThreadedOutputPath.set(destination.replace(".tif", "") +"_"+IJ.pad(finalZ,6)+".tif");
                            tiffSaver.get().saveAsTiff(fullThreadedOutputPath.get());
                        
                        
                        // Close the temporary images to free up memory.
                            sliceThreadedImp.get().close();
                            invFFTimp.get().close();
                            IJ.log("finished slice "+finalZ);
                        
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
