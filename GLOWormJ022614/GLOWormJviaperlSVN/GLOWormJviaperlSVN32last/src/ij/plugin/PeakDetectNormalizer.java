package ij.plugin;

import ij.*;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ImageProcessor;


public class PeakDetectNormalizer implements PlugIn {


    public void run(String arg) {
        // Get a list of IDs for all open images.
    	int options = 3112;
    	int measurements = 20958751;
    	ResultsTable rt = new ResultsTable();
        int[] impIDs = WindowManager.getIDList();
        if (impIDs == null || impIDs.length == 0) {
            IJ.error("No images are open.");
            return;
        }
        
        // Loop through each open image ID.
        for (int impID : impIDs) {
        	ImagePlus nextImp = WindowManager.getImage(impID);
        	IJ.log(nextImp.getTitle());
        	int maxValue = 0;
        	for (int z=1; z<=nextImp.getNSlices(); z++) {
        		nextImp.setPositionWithoutUpdate(nextImp.getChannel(),z,nextImp.getFrame());
        		int sliceMax = (int)nextImp.getProcessor().maxValue();
        		maxValue = sliceMax>maxValue?sliceMax:maxValue;
        	}
        	int span = 50;

        	ParticleAnalyzer pa = new ParticleAnalyzer (options,  measurements, null, 8, 128, 0.0d, 1.00d);
        	for (int z=1; z<=nextImp.getNSlices(); z++) {
        		nextImp.setPositionWithoutUpdate(nextImp.getChannel(),z,nextImp.getFrame());
        		ImageProcessor ip = nextImp.getProcessor();
        		for (int i=maxValue;i>=span;i=i-span) {
        			ip.setThreshold(i-span+1, i, ImageProcessor.RED_LUT);
        			pa.analyze(nextImp, ip);
        		}
        	}

        }

        IJ.log("Particle Peak Picking Plugin execution complete.");
    }
    
}
