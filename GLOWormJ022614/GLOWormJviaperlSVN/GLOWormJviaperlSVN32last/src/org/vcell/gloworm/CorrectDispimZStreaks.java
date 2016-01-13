package org.vcell.gloworm;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import javafx.scene.control.Spinner;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.ResultsTable;
import ij.plugin.Converter;
import ij.plugin.PlugIn;
import ij.plugin.Slicer;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.Resizer;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class CorrectDispimZStreaks implements PlugIn {
	ImagePlus imp;
	ImageProcessor gaussianDiffIP;
	private MaximumFinder mf;
	private int maskWidth;
	private double maskScaleFactor;
	private int maxTolerance;
	private int minTolerance;
	private int iterations;
	private int blankWidth;
	private int blankHeight;

	public void run(String arg) {
		imp = IJ.getImage();
		int slice = imp.getSlice();
		ImagePlus gaussianDiffImp = (new ImagePlus("http://fsbill.cam.uchc.edu/Xwords/z-x_Mask_ver_-32bkg_x255over408_15x33rect.tif"));
		gaussianDiffImp.getProcessor().setMinAndMax(0, 255);
		WindowManager.setTempCurrentImage(gaussianDiffImp);
		if (imp.getBitDepth() == 8)
			(new Converter()).run("8-bit");
		WindowManager.setTempCurrentImage(null);
		gaussianDiffIP = gaussianDiffImp.getProcessor();
		GenericDialog gd = new GenericDialog("Specify Z Correction Options...");
//		IJ.log(Prefs.getPrefsDir());
		
		gd.addNumericField("MaskWidth", 3, 0);
		gd.addNumericField("Mask Scale Factor", 0.125, 5);
		gd.addNumericField("Max Tolerance", 10, 0);
		gd.addNumericField("Min Tolerance", 10, 0);
		gd.addNumericField("Iterations at Min Tol", 50, 0);
		gd.addNumericField("BlankWidth", 3, 0);
		gd.addNumericField("BlankHeight", 3, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		 maskWidth = (int) gd.getNextNumber();
		 maskScaleFactor = (double) gd.getNextNumber();
		Prefs.set("Zstreak.maskScaleFactor", maskScaleFactor);
		 maxTolerance = (int) gd.getNextNumber();
		Prefs.set("Zstreak.maxTolerance", maxTolerance);
		 minTolerance = (int) gd.getNextNumber();
		Prefs.set("Zstreak.minTolerance", minTolerance);
		 iterations = (int) gd.getNextNumber();
		Prefs.set("Zstreak.iterations", iterations);
		 blankWidth = (int) gd.getNextNumber();
		Prefs.set("Zstreak.blankWidth", blankWidth);
		 blankHeight = (int) gd.getNextNumber();
		Prefs.set("Zstreak.blankHeight", blankHeight);
		Prefs.savePreferences();	
		if (imp.getNFrames()>1) {
			this.doHyperStack(imp);
			return;
		}

		imp.setSlice(1);

		gaussianDiffIP.setRoi((gaussianDiffIP.getWidth()-1-maskWidth)/2, 0, maskWidth, gaussianDiffIP.getHeight());
		gaussianDiffIP = gaussianDiffIP.crop();
		gaussianDiffImp = new ImagePlus(gaussianDiffImp.getTitle(),gaussianDiffIP);
		
		int bkgdMode = 0;
		int bkgdMin = 0;
		int[] histo = imp.getProcessor().getHistogram();
		//for mode value as bkgd
		for (int h = 0; h < histo.length; h++) {
			bkgdMode = histo[h] >bkgdMode? h: bkgdMode;
		}
		//for lowest non-zero value as bkgd
		for (int h = 0; h < histo.length; h++) {
			if (histo[h] >0) {
				bkgdMin = histo[h];
				h = histo.length;
			}
		}
		for (int s=1;s<=imp.getStackSize();s++) {
			imp.setSlice(s);

//			int bkgdMode = 0;
//			int bkgdMin = 0;
//			int[] histo = imp.getProcessor().getHistogram();
//			//for mode value as bkgd
//			for (int h = 0; h < histo.length; h++) {
//				bkgdMode = histo[h] >bkgdMode? h: bkgdMode;
//			}
//			//for lowest non-zero value as bkgd
//			for (int h = 0; h < histo.length; h++) {
//				if (histo[h] >0) {
//					bkgdMin = histo[h];
//					h = histo.length;
//				}
//			}

			IJ.log("bkgdMode = "+bkgdMode+", bkgdMin = "+bkgdMin);
			ArrayList<String> maxCum = new ArrayList<String>();
			ImageProcessor targetIP = imp.getProcessor().duplicate();
			for (int t=minTolerance;t>maxTolerance;t--) {
				mf = new MaximumFinder();
				Polygon maxPoly = mf.getMaxima(targetIP, t, false);

				int[] maxXs = new int[maxPoly.npoints];
				int[] maxYs = new int[maxPoly.npoints];

				maxXs = maxPoly.xpoints;
				maxYs = maxPoly.ypoints;
				IJ.log(maxXs.length+" maxima");

				for (int n=0; n<maxXs.length; n++) {
					if (!maxCum.contains(maxXs[n]+","+maxYs[n])) {
						maxCum.add(maxXs[n]+","+maxYs[n]);
						ImageProcessor modIP = gaussianDiffIP.duplicate();
						modIP.multiply(maskScaleFactor * (((double)imp.getProcessor().getPixel(maxXs[n], maxYs[n])))/255);
						imp.getProcessor().copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						targetIP.copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						Color fgc = Toolbar.getForegroundColor();
						Toolbar.setForegroundColor(Color.BLACK);
						targetIP.fillOval(maxXs[n]-blankWidth/2, maxYs[n]-blankHeight/2, blankWidth, blankHeight);
						Toolbar.setForegroundColor(fgc);

					}
				}
			}
			for (int t=iterations;t>0;t--) {
				mf = new MaximumFinder();
				Polygon maxPoly = mf.getMaxima(targetIP, minTolerance, false);

				int[] maxXs = new int[maxPoly.npoints];
				int[] maxYs = new int[maxPoly.npoints];

				maxXs = maxPoly.xpoints;
				maxYs = maxPoly.ypoints;
				IJ.log(maxXs.length+" maxima");
				
				for (int n=0; n<maxXs.length; n++) {
					if (!maxCum.contains(maxXs[n]+","+maxYs[n])) {
						maxCum.add(maxXs[n]+","+maxYs[n]);
						ImageProcessor modIP = gaussianDiffIP.duplicate();
						modIP.multiply(maskScaleFactor * (((double)imp.getProcessor().getPixel(maxXs[n], maxYs[n])))/255);
						imp.getProcessor().copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						targetIP.copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						Color fgc = Toolbar.getForegroundColor();
						Toolbar.setForegroundColor(Color.BLACK);
						targetIP.fillOval(maxXs[n]-blankWidth/2, maxYs[n]-blankHeight/2, blankWidth, blankHeight);
						Toolbar.setForegroundColor(fgc);

					}
				}
			}
//			for(int x=0;x<imp.getWidth();x++) {
//				for(int y=0;y<imp.getHeight();y++) {
//					if(imp.getProcessor().get(x,y) < bkgdMode) {
//						imp.getProcessor().set(x,y, bkgdMode);
//					}
//				}
//			}
		}
		imp.setSlice(slice);
		imp.updateAndDraw();
	}

	public void doHyperStack(ImagePlus impHS){
		String path= IJ.getDirectory("");
		Roi roi = impHS.getRoi();
		String title = impHS.getTitle();
		String[] titleChunks =  title.split( "[:"+File.separator+"]");
		int lot = titleChunks.length;
		String titleShort  = titleChunks[0]+titleChunks[lot-1];
		IJ.log(path+titleShort+"_#.tif");
		int[] dim = impHS.getDimensions();
		int w = dim[0];
		int h = dim[1];
		int c = dim[2];
		int z = dim[3];
		int t = dim[4];
		
		for(int f=1;f<=t;f++){
			IJ.log(path+titleShort+"_"+f+".tif");
			impHS.setRoi(roi);
			MQTVS_Duplicator duper = new MQTVS_Duplicator();
			ImagePlus impHS_dup = duper.run(impHS, 1, 1, 1, impHS.getNSlices(), f, f, 1, false);
			impHS_dup.setCalibration(impHS.getCalibration());
			impHS_dup.getCalibration().pixelWidth = impHS.getCalibration().pixelWidth;
			impHS_dup.getCalibration().pixelHeight = impHS.getCalibration().pixelHeight;
			impHS_dup.getCalibration().pixelDepth = impHS.getCalibration().pixelDepth;
//			impHS_dup.show();
			IJ.run(impHS_dup, "Select All", "");
			Slicer slicer = new Slicer();
			slicer.setNointerpolate(false); //clumsy, don't use true ever
			slicer.setOutputZSpacing(1);  
			Slicer.setStartAt("Top");
			ImagePlus impHS_duprs = slicer.reslice(impHS_dup);
			impHS_duprs.setCalibration(impHS.getCalibration());
			impHS_duprs.getCalibration().pixelWidth = impHS.getCalibration().pixelWidth;
			impHS_duprs.getCalibration().pixelHeight = impHS.getCalibration().pixelHeight;
			impHS_duprs.getCalibration().pixelDepth = impHS.getCalibration().pixelWidth;
//			impHS_duprs.show();
			IJ.run(impHS_duprs, "Correct diSPIM ZStreaks...", "maskwidth="+maskWidth+" mask="+maskScaleFactor+" max="+maxTolerance+" min="+minTolerance+" iterations="+iterations+" blankwidth="+blankWidth+" blankheight="+blankHeight+"");
			
			impHS_duprs.updateAndRepaintWindow();
//			IJ.runMacro("waitForUser(2);");
			slicer.setNointerpolate(false); //clumsy, don't use true ever
			slicer.setOutputZSpacing(1);  
			IJ.run(impHS_duprs, "Select All", "");
			ImagePlus impHS_duprsrs = slicer.reslice(impHS_duprs);
//			impHS_duprsrs.show();
//			IJ.runMacro("waitForUser(3);");
			IJ.saveAsTiff(impHS_duprsrs, path+titleShort+"_"+f+".tif");
			impHS_dup.close();
			impHS_dup.flush();
			impHS_duprs.close();
			impHS_duprs.flush();
			impHS_duprsrs.close();
			impHS_duprsrs.flush();

		}
	}
}
