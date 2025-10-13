package ij.plugin;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.ListModel;

import ij.*;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
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
    		ArrayList<Integer> coincidentHitRoiIndexes = new ArrayList<Integer>();

        	IJ.log(nextImp.getTitle());
        	int maxValue = 0;
        	for (int z=1; z<=nextImp.getNSlices(); z++) {
        		nextImp.setPositionWithoutUpdate(nextImp.getChannel(),z,nextImp.getFrame());
        		int sliceMax = (int)nextImp.getProcessor().getMax();
        		maxValue = sliceMax>maxValue?sliceMax:maxValue;
        	}
        	int span = 30;

        	ParticleAnalyzer pa = new ParticleAnalyzer (options,  measurements, null, 4, 80, 0.7d, 1.00d);
        	
        	int[] nZspecRois = new int[nextImp.getNSlices()];
        	int totalRoiCount =0;
        	for (int z=1; z<=nextImp.getNSlices(); z++) {
        		nextImp.setPositionWithoutUpdate(nextImp.getChannel(),z,nextImp.getFrame());
        		ImageProcessor ip = nextImp.getProcessor();
        		
        		//ROUND ONE: SEGEMENT PARTICLES AT SUCCESSIVELY DECREASING THRESHOLDED INTENSITY SLICES
        		for (int i=maxValue;i>=span;i=i-span) {

        			ip.setThreshold(i-span+1, maxValue, ImageProcessor.RED_LUT);
        			pa.analyze(nextImp, ip);

        		}

        		nZspecRois[z-1] = nextImp.getRoiManager().getCount() - totalRoiCount;
        		
        		//ROUND TWO:  OVERLAP CHECKING BLOCKS --- now running once all intensity thresholds have been sampled for a given Z value.        	
        		ArrayList<Integer> redundantRoiIndexes = new ArrayList<Integer>();
        		Roi[] roisRdTwoArray = nextRM.getFullRoisAsArray();
        		int roisRdTwoLength = roisRdTwoArray.length;

        		for (int threshPeakCandidtateIndex = totalRoiCount; threshPeakCandidtateIndex<roisRdTwoLength; threshPeakCandidtateIndex++) {   //outer loop through all rois

        			for (int likelySubPeakIndex = totalRoiCount; likelySubPeakIndex<roisRdTwoLength; likelySubPeakIndex++ ) {    //inner loop through all rois

        				if ((roisRdTwoArray[likelySubPeakIndex]!=(roisRdTwoArray[threshPeakCandidtateIndex]))) {    //supposed to skip identity comparisons for same roi

        					for (int pt=0; pt<roisRdTwoArray[likelySubPeakIndex].getPolygon().npoints; pt++) {

        						if (roisRdTwoArray[threshPeakCandidtateIndex].getPolygon().contains(roisRdTwoArray[likelySubPeakIndex].getPolygon().xpoints[pt], 
        								roisRdTwoArray[likelySubPeakIndex].getPolygon().ypoints[pt])){

        							if (getArea(roisRdTwoArray[likelySubPeakIndex].getPolygon()) > getArea(roisRdTwoArray[threshPeakCandidtateIndex].getPolygon())) {
        								if (!redundantRoiIndexes.contains(likelySubPeakIndex))
        									redundantRoiIndexes.add(likelySubPeakIndex);
        							} else {
        								if (!redundantRoiIndexes.contains(threshPeakCandidtateIndex)) {
        									redundantRoiIndexes.add(threshPeakCandidtateIndex);
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

        		ListModel<String> flm = nextRM.getFullListModel();
        		ListModel<String> lm = nextRM.getListModel();

        		redundantRoiIndexes.sort(Comparator.naturalOrder());

        		nextRM.getList().setModel(new DefaultListModel<String>());
        		for (int s= redundantRoiIndexes.size()-1; s>=0; s--) {
        			int rri = redundantRoiIndexes.get(s);

        			((DefaultListModel<String>) flm).removeElement(nextRM.getListModel().getElementAt(rri));
        			nextRM.getROIs().remove(nextRM.getListModel().getElementAt(rri));
        			((DefaultListModel<String>) lm).remove(rri);


        		}
        		nextRM.getList().setModel(lm);
         			
       		// END OVERLAP CHECKING/PURGE BLOCKS.

        		IJ.wait(0);
        		
        		// ROUND THREE: NOW FIND MAXIMA USING THE BUILT-IN ALGORITHM
                ByteProcessor typeP = new ByteProcessor(nextImp.getWidth(), nextImp.getHeight());     
                byte[] types = (byte[])typeP.getPixels();
                MaximumFinder maxFinder = new MaximumFinder();
                maxFinder.setup("", nextImp);
        		maxFinder.findMaxima(nextImp.getProcessor(), 30, ImageProcessor.NO_THRESHOLD, MaximumFinder.POINT_SELECTION, true, false);

        		//NOW ROUND FOUR: SEEKING DOUBLE-HITS THAT ARE BOTH FIND MAXIMA POSITIVE, AND SINKING THRESHOLD POSITIVE.        	
        		Roi[] roisRdFourArray = nextRM.getFullRoisAsArray();
        		int roisRdFourLength = roisRdFourArray.length;

        		for (int maxCandidateIndex = totalRoiCount; maxCandidateIndex<roisRdFourLength; maxCandidateIndex++) {   //outer loop through all rois
        			if (roisRdFourArray[maxCandidateIndex].getName().contains("Traced"))
        				continue;
        			for (int threshCandidateIndex = totalRoiCount; threshCandidateIndex<roisRdFourLength; threshCandidateIndex++ ) {    //inner loop through all rois
            			if (roisRdFourArray[threshCandidateIndex].getName().contains("Oval"))
            				continue;
            			if ((roisRdFourArray[threshCandidateIndex]!=(roisRdFourArray[maxCandidateIndex]))) {    //supposed to skip identity comparisons for same roi

            				for (int ptMaxCInd=0; ptMaxCInd<roisRdFourArray[maxCandidateIndex].getPolygon().npoints; ptMaxCInd++) {
            					for (int ptThrCInd=0; ptThrCInd<roisRdFourArray[threshCandidateIndex].getPolygon().npoints; ptThrCInd++) {


            						if (roisRdFourArray[threshCandidateIndex].getPolygon().contains(roisRdFourArray[maxCandidateIndex].getPolygon().xpoints[ptMaxCInd], 
            								roisRdFourArray[maxCandidateIndex].getPolygon().ypoints[ptMaxCInd])
            								|| roisRdFourArray[maxCandidateIndex].getPolygon().contains(roisRdFourArray[threshCandidateIndex].getPolygon().xpoints[ptThrCInd], 
            										roisRdFourArray[threshCandidateIndex].getPolygon().ypoints[ptThrCInd])){


            							if (getArea(roisRdFourArray[maxCandidateIndex].getPolygon()) > getArea(roisRdFourArray[threshCandidateIndex].getPolygon())) {
            								if (!coincidentHitRoiIndexes.contains(maxCandidateIndex))
            									coincidentHitRoiIndexes.add(maxCandidateIndex);
            							} else {
            								if (!coincidentHitRoiIndexes.contains(maxCandidateIndex)) {
            									coincidentHitRoiIndexes.add(maxCandidateIndex);
            								}
            							}  // end if reverse getArea compare else

            							break;

            						}  //end if overlap

            					} //end for ptThr...
            				} //end for ptMax...
            			}else {      
        					continue;
        				}   // end if != else

        			}   //inner roi loop

        		}   //outer roi loop

        		// END SEEKING DOUBLE-HITS....

        		totalRoiCount = nextImp.getRoiManager().getCount();

        	
        	}  // end for Z loop

    		coincidentHitRoiIndexes.sort(Comparator.naturalOrder());
    		Roi[] crossConfirmedRois = new Roi[coincidentHitRoiIndexes.size()];
    		
    		for (int s= coincidentHitRoiIndexes.size()-1; s>=0; s--) {
    			int rri = coincidentHitRoiIndexes.get(s);
    			crossConfirmedRois[s] = ((Roi)nextRM.getROIs().get(nextRM.getListModel().getElementAt(rri)).clone());
     		}

    		// recite steps involved in rm.delete() with none selected, but without worrying about internal rm flags...
    		nextRM.getROIs().clear();
    		nextRM.getRoisByNumbers().clear();
    		nextRM.getRoisByRootName().clear();
    		nextRM.getListModel().removeAllElements();
			nextRM.getFullListModel().removeAllElements();
    		
    		for (Roi ccRoi:crossConfirmedRois) {
    			nextRM.addRoi(ccRoi, false, ccRoi.getStrokeColor(), ccRoi.getFillColor(), -1, false);
    		}
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
