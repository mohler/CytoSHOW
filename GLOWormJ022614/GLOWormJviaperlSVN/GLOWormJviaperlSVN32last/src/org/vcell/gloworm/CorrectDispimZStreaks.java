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
import ij.io.FileSaver;
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
//	ImagePlus imp;
//	ImageProcessor gaussianDiffIP;
//	private MaximumFinder mf;

	public void processStack(ImagePlus imp) {
		int slice = imp.getSlice();
		ImagePlus gaussianDiffImp = (new ImagePlus("http://fsbill.cam.uchc.edu/Xwords/z-x_Mask_ver_-32bkg_x255over408_15x33rect.tif"));
		gaussianDiffImp.getProcessor().setMinAndMax(0, 255);
		WindowManager.setTempCurrentImage(gaussianDiffImp);
		if (imp.getBitDepth() == 8)
			(new Converter()).run("8-bit");
		WindowManager.setTempCurrentImage(null);
		ImageProcessor gaussianDiffIP = gaussianDiffImp.getProcessor();
		GenericDialog gd = new GenericDialog("Specify Z Correction Options...");
//		IJ.log(Prefs.getPrefsDir());
		
		gd.addNumericField("MaskWidth", 1, 0);
		gd.addNumericField("Mask Scale Factor", 0.125, 5);
		gd.addNumericField("Max Tolerance", 10, 0);
		gd.addNumericField("Min Tolerance", 10, 0);
		gd.addNumericField("Iterations at Min Tol", 20, 0);
		gd.addNumericField("BlankWidth", 3, 0);
		gd.addNumericField("BlankHeight", 3, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int maskWidth = (int) gd.getNextNumber();
		double maskScaleFactor = (double) gd.getNextNumber();
		Prefs.set("Zstreak.maskScaleFactor", maskScaleFactor);
		int maxTolerance = (int) gd.getNextNumber();
		Prefs.set("Zstreak.maxTolerance", maxTolerance);
		int minTolerance = (int) gd.getNextNumber();
		Prefs.set("Zstreak.minTolerance", minTolerance);
		int iterations = (int) gd.getNextNumber();
		Prefs.set("Zstreak.iterations", iterations);
		int blankWidth = (int) gd.getNextNumber();
		Prefs.set("Zstreak.blankWidth", blankWidth);
		int blankHeight = (int) gd.getNextNumber();
		Prefs.set("Zstreak.blankHeight", blankHeight);
		Prefs.savePreferences();	
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

//			IJ.log("bkgdMode = "+bkgdMode+", bkgdMin = "+bkgdMin);
			IJ.log(""+s);
			ArrayList<String> maxCum = new ArrayList<String>();
			ImageProcessor targetIP = imp.getProcessor().duplicate();
			for (int t=minTolerance;t>maxTolerance;t--) {
				MaximumFinder mf = new MaximumFinder();
				Polygon maxPoly = mf.getMaxima(targetIP, t, false);

				int[] maxXs = new int[maxPoly.npoints];
				int[] maxYs = new int[maxPoly.npoints];

				maxXs = maxPoly.xpoints;
				maxYs = maxPoly.ypoints;
//				IJ.log(maxXs.length+" maxima");

				for (int n=0; n<maxXs.length; n++) {
					if (!maxCum.contains(maxXs[n]+","+maxYs[n])) {
						maxCum.add(maxXs[n]+","+maxYs[n]);
						ImageProcessor modIP = gaussianDiffIP.duplicate();
						modIP.multiply(maskScaleFactor * (((double)imp.getProcessor().getPixel(maxXs[n], maxYs[n])))/255);
						imp.getProcessor().copyBits(modIP, maxXs[n]-1, maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						targetIP.copyBits(modIP, maxXs[n]-1, maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						Color fgc = Toolbar.getForegroundColor();
						Toolbar.setForegroundColor(Color.BLACK);
						targetIP.fillOval(maxXs[n]-blankWidth/2, maxYs[n]-blankHeight/2, blankWidth, blankHeight);
						Toolbar.setForegroundColor(fgc);

					}
				}
			}
			for (int t=iterations;t>0;t--) {
				MaximumFinder mf = new MaximumFinder();
				Polygon maxPoly = mf.getMaxima(targetIP, minTolerance, false);

				int[] maxXs = new int[maxPoly.npoints];
				int[] maxYs = new int[maxPoly.npoints];

				maxXs = maxPoly.xpoints;
				maxYs = maxPoly.ypoints;
//				IJ.log(maxXs.length+" maxima");
				
				for (int n=0; n<maxXs.length; n++) {
					if (!maxCum.contains(maxXs[n]+","+maxYs[n])) {
						maxCum.add(maxXs[n]+","+maxYs[n]);
						ImageProcessor modIP = gaussianDiffIP.duplicate();
						modIP.multiply(maskScaleFactor * (((double)imp.getProcessor().getPixel(maxXs[n], maxYs[n])))/1000);
						imp.getProcessor().copyBits(modIP, maxXs[n]-1, maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						targetIP.copyBits(modIP, maxXs[n]-1, maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
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
			MQTVS_Duplicator duper = new MQTVS_Duplicator();
			ImagePlus impHS_dup = duper.run(impHS, 1, 1, 1, impHS.getNSlices(), f, f, 1, false);
			impHS_dup.setCalibration(impHS.getCalibration());
			Slicer slicer = new Slicer();
			slicer.setNointerpolate(false);
			slicer.setOutputZSpacing(1);  //pixels
//			IJ.showMessage(title, ""+slicer.getOutputZSpacing());
			Slicer.setStartAt("Top");
			if (roi ==null)
				impHS_dup.setRoi(0,0,impHS_dup.getWidth()-1, impHS_dup.getHeight()-1);
			else
				impHS_dup.setRoi(roi);
			ImagePlus impHS_duprs = slicer.reslice(impHS_dup);
			impHS_dup.close();
			impHS_dup.flush();
//			IJ.showMessage(title, ""+impHS_duprs.getWidth()+" "+impHS_duprs.getHeight());
			this.processStack(impHS_duprs);
			impHS_duprs.setRoi(0,0,impHS_duprs.getWidth()-1, impHS_duprs.getHeight()-1);
//			IJ.showMessage(title, ""+slicer.getOutputZSpacing());

			ImagePlus impHS_duprsrs = slicer.reslice(impHS_duprs);
			impHS_duprs.close();
			impHS_duprs.flush();

			IJ.saveAsTiff(impHS_duprsrs, path+titleShort+"_"+f+".tif");
			impHS_duprsrs.close();
			impHS_duprsrs.flush();

		}
	}

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.getNFrames() >= 1) {
			doHyperStack(imp);
			return;
		}
		
	}
}
