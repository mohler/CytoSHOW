package ij.plugin;

import ij.*;
import ij.process.*;
import ij.util.Tools;
import ij.plugin.FFT;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.RankFilters;
import ij.gui.*;
import ij.io.FileSaver;
import ij.plugin.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.*;

/**
 * Allows rapid build of Z-expanded image slices within a stack by virtue of axial projection of substacks to create a new slice-expanded image stack for the data.
 */
public class SliceExpander_MultiThread implements PlugIn {

    // A shared lock object to synchronize access to non-thread-safe ImageJ methods.
    private final Object lock = new Object();

    @Override
    public void run(String arg) {
        // Get a list of IDs for all open images.
        int zExpandFactor = (int)IJ.getNumber("Expand slice depth by what factor?", 10d);
        if (zExpandFactor < 2)
        	return;
        
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
        
        ArrayList<String> savePathList = new ArrayList<String>();

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
            String processedDirName = sourceName.replace(".tif", "") + "_zExpandX"+zExpandFactor;
            String savePath = path + processedDirName + File.separator;
            String destination = savePath + sourceName;

            try {
                new File(savePath).mkdirs();
            } catch (Exception e) {
                IJ.error("Could not create directory: " + savePath);
                continue;
            }
            
            final ThreadLocal<ImagePlus> subStackThreadedImp = ThreadLocal.withInitial(() -> new ImagePlus(sourceName+"_slice_XXXXXX", sourceImp.getProcessor()));

            final ThreadLocal<ZProjector> threadedZprojector = ThreadLocal.withInitial(() -> new ZProjector());
            
            final ThreadLocal<FileSaver> tiffThreadedSaver = ThreadLocal.withInitial(() -> new FileSaver(new ImagePlus()));

            // Define the full path for the output file
            final ThreadLocal<String> fullThreadedOutputPath = ThreadLocal.withInitial(() -> destination.replace(".tif", "") +"_XXXXXX.tif");

            ThreadLocal<FolderOpener> threadedFolderOpener = ThreadLocal.withInitial(() -> new FolderOpener());

            // Loop through each Z-slice and submit a processing task to the executor.
            for (int z = 1; z <= zDepth; z=z+zExpandFactor) {

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
                        threadedZprojector.get().setImage(sourceImp);
                        threadedZprojector.get().setStartSlice(finalZ);
                        threadedZprojector.get().setStopSlice(finalZ+zExpandFactor-1);
                        threadedZprojector.get().setMethod(ZProjector.MIN_METHOD);
                        
                        threadedZprojector.get().doProjection();
                        
                       // Save the projected slice.
                            tiffThreadedSaver.set(new FileSaver(threadedZprojector.get().getProjection()));
                            fullThreadedOutputPath.set(destination.replace(".tif", "") +"_"+IJ.pad(finalZ,6)+".tif");
                            tiffThreadedSaver.get().saveAsTiff(fullThreadedOutputPath.get());
                        
                        
                        // Close the temporary images to free up memory.
                            threadedZprojector.get().getProjection().close();
                            IJ.log("finished slice "+finalZ);
                        
                    } catch (Exception e) {
                        IJ.error("Error processing slice " + finalZ + ": " + e.getMessage());
                    }
                });
            }
            savePathList.add(savePath);
        }

        // Shut down the executor and wait for all tasks to complete.
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            IJ.error("Plugin execution was interrupted.");
        }
        for (String savePath:savePathList) {
            new FolderOpener().openFolder(savePath).show();
        }

        IJ.log("Plugin execution complete.");
    }
}
