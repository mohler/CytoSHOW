package gloworm;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;

import java.sql.Struct;

import javax.swing.*; 
//import java.util.*; 
//import javax.accessibility.*; 

public class SATest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ImageJ.main(null);

		
//		IJ.run("Open...", "C:\\Documents and Settings\\All Users\\Documents\\My Pictures\\Sample Pictures");

//		IJ.run("Brightness/Contrast...");
//		IJ.getImage().getProcessor().setMinAndMax(100,160);

//		SwingUtilities.invokeLater(new Runnable() {
//		    public void run() {
//		           IJ.run("Brightness/Contrast...");
//		           IJ.getImage().getProcessor().setMinAndMax(100,160);
//		}});


//		Structure_Autoselector sa = new Structure_Autoselector();
//		Worm_Autoselector sa = new Worm_Autoselector();
//		sa.run("");

	}
/* 
	private static void setSavedMinAndMaxValues(ImagePlus imp, ImageProcessor ip, ContrastAdjuster ca, double savedMin, double savedMax) {
	    ca.min = savedMin;
	    ca.max = savedMax;
	    ca.setMinAndMax(ip, savedMin, savedMax);
	    if (ca.RGBImage)
	        ca.doMasking(imp, ip);
	    ca.updateScrollBars(null);
	    ca.updatePlot();
	    ca.updateLabels(imp, ip);
	    imp.updateAndDraw();
	    if (ca.RGBImage)
	        imp.unlock();
	}
*/
	
}
