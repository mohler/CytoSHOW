package ij.plugin;

import java.util.ArrayList;
import java.util.Comparator;

import ij.*;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
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
        	RoiManager nextRM = nextImp.getRoiManager();
        	IJ.log(nextImp.getTitle());
        	int maxValue = 0;
        	for (int z=1; z<=nextImp.getNSlices(); z++) {
        		nextImp.setPositionWithoutUpdate(nextImp.getChannel(),z,nextImp.getFrame());
        		int sliceMax = (int)nextImp.getProcessor().getMax();
        		maxValue = sliceMax>maxValue?sliceMax:maxValue;
        	}
        	int span = 15;

        	ParticleAnalyzer pa = new ParticleAnalyzer (options,  measurements, null, 8, 20, 0.7d, 1.00d);
        	
        	for (int z=1; z<=nextImp.getNSlices(); z++) {
        		nextImp.setPositionWithoutUpdate(nextImp.getChannel(),z,nextImp.getFrame());
        		ImageProcessor ip = nextImp.getProcessor();
            	
        		for (int i=maxValue;i>=span;i=i-span) {

        			ip.setThreshold(i-span+1, maxValue, ImageProcessor.RED_LUT);
        			pa.analyze(nextImp, ip);

        		}

        	}
        	
    		ArrayList<Integer> redundantRoiIndexes = new ArrayList<Integer>();
    		Roi[] roisArray = nextRM.getFullRoisAsArray();
    		int roisLength = roisArray.length;
			for (int sra=0; sra<roisLength; sra++) {   
				for (int nr=0; nr<roisLength; nr++ ) { 
					if (!(roisArray[nr].equals(roisArray[sra]))) {
						for (int pt=0; pt<roisArray[nr].getPolygon().npoints; pt++) {
							if (roisArray[sra].getPolygon().contains(roisArray[nr].getPolygon().xpoints[pt], 
																		roisArray[nr].getPolygon().ypoints[pt])){
								if (roisArray[nr].getPolygon().npoints > roisArray[sra].getPolygon().npoints) {
									redundantRoiIndexes.add(nr);
								} else {
									redundantRoiIndexes.add(sra);
								}
									break;
							}
						}
					}else {
						continue;
					}
				}
			}
			redundantRoiIndexes.sort(Comparator.naturalOrder());
			for (int s= redundantRoiIndexes.size()-1; s>=0; s--) {
				int rri = redundantRoiIndexes.get(s);
				nextRM.getROIs().remove(nextRM.getListModel().getElementAt(rri));
				nextImp.getRoiManager().getFullListModel().removeElement(nextRM.getListModel().getElementAt(rri));
				nextRM.getListModel().remove(rri);
			}

        	
        }

        
        IJ.log("Particle Peak Picking Plugin execution complete.");
    }
    
}
