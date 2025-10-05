package ij.plugin;

import ij.*;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;


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
        	IJ.log(WindowManager.getImage(impID).getTitle());
        	ParticleAnalyzer pa = new ParticleAnalyzer (options,  measurements, null, 8d, 28d, 0.0d, 1.00d);
        	pa.analyze(WindowManager.getImage(impID));
        }

        IJ.log("Particle Peak Picking Plugin execution complete.");
    }
    
}
