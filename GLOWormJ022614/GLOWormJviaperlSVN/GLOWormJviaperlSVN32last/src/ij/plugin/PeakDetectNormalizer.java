package ij.plugin;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Comparator;

import ij.*;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
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
        	int span = 30;

        	ParticleAnalyzer pa = new ParticleAnalyzer (options,  measurements, null, 8, 80, 0.7d, 1.00d);
        	
        	int[] nZspecRois = new int[nextImp.getNSlices()];
        	int totalRoiCount =0;
        	for (int z=1; z<=nextImp.getNSlices(); z++) {
        		nextImp.setPositionWithoutUpdate(nextImp.getChannel(),z,nextImp.getFrame());
        		ImageProcessor ip = nextImp.getProcessor();

        		for (int i=maxValue;i>=span;i=i-span) {

        			ip.setThreshold(i-span+1, maxValue, ImageProcessor.RED_LUT);
        			pa.analyze(nextImp, ip);

        		}

        		nZspecRois[z-1] = nextImp.getRoiManager().getCount() - totalRoiCount;
        		
        		//OVERLAP CHECKING BLOCKS --- now running once all intensity thresholds have been sampled for a given Z value.        	
        		ArrayList<Integer> redundantRoiIndexes = new ArrayList<Integer>();
        		Roi[] roisArray = nextRM.getFullRoisAsArray();
        		int roisLength = roisArray.length;

        		for (int maxCandidtateIndex = totalRoiCount; maxCandidtateIndex<roisLength; maxCandidtateIndex++) {   //outer loop through all rois

        			for (int likelySubPeakIndex = totalRoiCount; likelySubPeakIndex<roisLength; likelySubPeakIndex++ ) {    //inner loop through all rois

        				if ((roisArray[likelySubPeakIndex]!=(roisArray[maxCandidtateIndex]))) {    //supposed to skip identity comparisons for same roi

        					for (int pt=0; pt<roisArray[likelySubPeakIndex].getPolygon().npoints; pt++) {

        						if (roisArray[maxCandidtateIndex].getPolygon().contains(roisArray[likelySubPeakIndex].getPolygon().xpoints[pt], 
        								roisArray[likelySubPeakIndex].getPolygon().ypoints[pt])){

        							if (getArea(roisArray[likelySubPeakIndex].getPolygon()) > getArea(roisArray[maxCandidtateIndex].getPolygon())) {
        								if (!redundantRoiIndexes.contains(likelySubPeakIndex))
        									redundantRoiIndexes.add(likelySubPeakIndex);
        							} else {
        								if (!redundantRoiIndexes.contains(maxCandidtateIndex)) {
        									redundantRoiIndexes.add(maxCandidtateIndex);
        								}
        							}  // end if getArea else

        							break;

        						}  //end if overlap

        					} //end for pt
        				}else {       // for some reason, the double loop never gives me routing to this same-roi case.   WHY NOT??  Because I was using .equal instead of ==, duh!
        					continue;
        				}            // end if !equals

        			}   //inner roi loop

        		}   //outer roi loop

        		redundantRoiIndexes.sort(Comparator.naturalOrder());
        		
        		for (int s= redundantRoiIndexes.size()-1; s>=0; s--) {
        			int rri = redundantRoiIndexes.get(s);
        			nextRM.getFullListModel().removeElement(nextRM.getListModel().getElementAt(rri));
        			nextRM.getROIs().remove(nextRM.getListModel().getElementAt(rri));
        			nextRM.getListModel().remove(rri);
        		}
        		// END OVERLAP CHECKING/PURGE BLOCKS.

        		IJ.wait(0);
        		totalRoiCount = nextImp.getRoiManager().getCount();

        	}  // end for Z loop

        }  // end for impID loop

        
        IJ.log("Particle Peak Picking Plugin execution complete.");
    }
    
    
    public double getArea(Polygon p) {
		if (p==null) return Double.NaN;
		int carea = 0;
		int iminus1;
		for (int i=0; i<p.npoints; i++) {
			iminus1 = i-1;
			if (iminus1<0) iminus1=p.npoints-1;
			carea += (p.xpoints[i]+p.xpoints[iminus1])*(p.ypoints[i]-p.ypoints[iminus1]);
		}
		return (Math.abs(carea/2.0));
	}
    
}
