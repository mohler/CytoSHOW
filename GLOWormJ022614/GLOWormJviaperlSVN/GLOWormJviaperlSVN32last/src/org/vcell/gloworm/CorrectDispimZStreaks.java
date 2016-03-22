package org.vcell.gloworm;

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.image.ImageProducer;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import javafx.scene.control.Spinner;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.io.Opener;
import ij.measure.ResultsTable;
import ij.plugin.Converter;
import ij.plugin.PlugIn;
import ij.plugin.Slicer;
import ij.plugin.filter.MaximumFinder;
import ij.plugin.Resizer;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.util.Tools;

import com.sun.management.OperatingSystemMXBean; 


public class CorrectDispimZStreaks implements PlugIn {
	ImagePlus imp;
	ImageProcessor gaussianDiffIP;
	private int maskWidth;
	private double maskScaleFactor;
	private int maxTolerance;
	private int minTolerance;
	private int iterations;
	private int blankWidth;
	private int blankHeight;
	private double origBkgdModeCutoffFactor;
	private static Checkbox monitorCheckbox;
	private ImagePlus monitorImp;
	private ImagePlus targetImp;
	private static JFrame monitorFrame;
	private String channelRange;
	//	private int[] maxXs;
	//	private int[] maxYs;
	private double cpuLimit;
	private double threadTimeLimit;
	private String outPath;
	private double bkgdFloorFactor;

	public void run(String arg) {
		imp = IJ.getImage();
		int slice = imp.getSlice();
		//		ImagePlus gaussianDiffImp = (new ImagePlus("http://fsbill.cam.uchc.edu/Xwords/z-x_Mask_ver_-32bkg_x255over408_15x33rect.tif"));
		URL url = ImageWindow.class.getResource("images/z-x_Mask_ver_-32bkg_x255over408_15x33rect.tif");
		if (url==null) return;
		ImagePlus gaussianDiffImp = null;
		try {
			gaussianDiffImp = (new Opener()).openTiff(url.openStream(), "");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		gaussianDiffImp.getProcessor().setMinAndMax(0, 255);
		WindowManager.setTempCurrentImage(gaussianDiffImp);
		if (imp.getBitDepth() == 8)
			(new Converter()).run("8-bit");
		WindowManager.setTempCurrentImage(null);
		gaussianDiffIP = gaussianDiffImp.getProcessor();
		GenericDialog gd = new GenericDialog("Specify Z Correction Options...");
		//		IJ.log(Prefs.getPrefsDir());

		gd.addNumericField("MaskWidth", 1, 0);
		gd.addNumericField("Mask Scale Factor", 0.100, 3);
		gd.addNumericField("Max Tolerance", 10, 0);
		gd.addNumericField("Min Tolerance", 10, 0);
		gd.addNumericField("Iterations at Min Tol", 50, 0);
		gd.addNumericField("BlankWidth", 1, 0);
		gd.addNumericField("BlankHeight", 1, 0);
		gd.addNumericField("Bkgd Mode Cutoff Factor", 1.0, 2);
		gd.addNumericField("Floor Factor", 0.5, 2);

		gd.addNumericField("CPU max", 0.40, 2);
		gd.addNumericField("Thread time limit", 60, 1);
		gd.addStringField("Channel(s)", "1-"+imp.getNChannels());
		gd.addStringField("Path to output (optional)", "");
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
		origBkgdModeCutoffFactor = gd.getNextNumber();
		Prefs.set("Zstreak.bkgdModeCutoffFactor", origBkgdModeCutoffFactor);
		bkgdFloorFactor = gd.getNextNumber();
		Prefs.set("Zstreak.bkgdFloorFactor", bkgdFloorFactor);
		cpuLimit = gd.getNextNumber();
		Prefs.set("Zstreak.cpuLimit", cpuLimit);
		threadTimeLimit = gd.getNextNumber();
		Prefs.set("Zstreak.threadTimeLimit", threadTimeLimit);
		channelRange = gd.getNextString();
		Prefs.set("Zstreak.channelRange", channelRange);
		outPath = gd.getNextString().replace("spaceSPACE", " ");
		Prefs.set("Zstreak.outPath", outPath);
		Prefs.savePreferences();	
		if (monitorFrame == null || monitorCheckbox == null) { 
			monitorFrame = new JFrame("Monitor processing visually?");
			monitorCheckbox= new Checkbox("Monitor processing visually?");
			monitorFrame.add(monitorCheckbox);
			monitorFrame.pack();
			monitorFrame.setVisible(true);
		}
		if (imp.getNFrames()>1) {
			this.doHyperStack(imp);
			return;
		}
		
		targetImp = new ImagePlus("Process Monitor");
		monitorImp = new ImagePlus("Target Monitor");

		gaussianDiffIP.setRoi((gaussianDiffIP.getWidth()-1-maskWidth)/2, 0, maskWidth, gaussianDiffIP.getHeight());
		gaussianDiffIP = gaussianDiffIP.crop();
		double resolutionCorrection = 0.1625/(imp.getCalibration().pixelWidth/imp.getCalibration().pixelDepth);
		gaussianDiffIP = gaussianDiffIP.resize(maskWidth, (int)(gaussianDiffIP.getHeight()*resolutionCorrection), true);
		gaussianDiffImp = new ImagePlus(gaussianDiffImp.getTitle(),gaussianDiffIP);

		imp.setSlice(imp.getNSlices()/2);

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
		final boolean[] doneDestreak = new boolean[imp.getStackSize()];
		for (int ss=1;ss<=imp.getStackSize();ss++) {
			final int fss = ss;
			final ImageProcessor fIP= imp.getStack().getProcessor(ss);
			final int s = ss;
			final int fbkgdMode = bkgdMode;
			final int fbkgdMin = bkgdMin;
			OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);  
			// What % CPU load this current JVM is taking, from 0.0-1.0  
			double jvmCpuLoad = osBean.getProcessCpuLoad();  
			// What % load the overall system is at, from 0.0-1.0  
			double sysCpuLoad = osBean.getSystemCpuLoad();  
			while (sysCpuLoad > cpuLimit) {
				IJ.wait(1000);
				jvmCpuLoad = osBean.getProcessCpuLoad();  
				sysCpuLoad = osBean.getSystemCpuLoad();  
			}
			IJ.wait(100);
			Thread destreakThread = new Thread(new Runnable() {
				public void run() {

					IJ.log("bkgdMode = "+fbkgdMode+", bkgdMin = "+fbkgdMin);
					ArrayList<String> maxCum = new ArrayList<String>();
					ImageProcessor targetIP = fIP.duplicate();
					targetImp.setProcessor(targetIP);
					targetIP.setColor(Color.BLACK);
					monitorImp.setProcessor(targetIP);
					int[]  maxXs = new int[10];
					int[]  maxYs = new int[10];
					int minMax = 1000000;
					int bottomCount = 0;

					for (int t=minTolerance;t>maxTolerance;t--) {
						MaximumFinder mf = new MaximumFinder();
						Polygon maxPoly = mf.getMaxima(targetIP, t, false);

						maxXs = new int[maxPoly.npoints];
						maxYs = new int[maxPoly.npoints];

						maxXs = maxPoly.xpoints;
						maxYs = maxPoly.ypoints;
						IJ.log(maxXs.length+" maxima "+ imp.getTitle() +" x=" + fss + " bkmcof=" + origBkgdModeCutoffFactor);

						for (int n=0; n<maxXs.length; n++) {
							if (!maxCum.contains(maxXs[n]+","+maxYs[n])) {
								maxCum.add(maxXs[n]+","+maxYs[n]);
								ImageProcessor modIP = gaussianDiffIP.duplicate();
								modIP.multiply(maskScaleFactor * (((double)fIP.getPixel(maxXs[n], maxYs[n])))/255);
								fIP.copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
								targetIP.copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
								targetIP.fillOval(maxXs[n]-blankWidth/2, maxYs[n]-blankHeight/2, blankWidth, blankHeight);

							}
						}
					}


					//			for (int t=iterations;t>0;t--) {
					//			while (maxXs.length != minMax && bottomCount < 20 ) {
					boolean bottomedOut = false;
					long whileLoopStartTime = (new Date()).getTime();
//					double bkgdModeCutoffFactor = origBkgdModeCutoffFactor;
					
					double bkgdModeCutoffFactor = origBkgdModeCutoffFactor;

					while (!bottomedOut) {
						
//						if (ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class).getProcessCpuLoad() > cpuLimit &&
						if (
								(new Date()).getTime() - whileLoopStartTime > threadTimeLimit*1000 ) {
								bkgdModeCutoffFactor = bkgdModeCutoffFactor + .1;
								IJ.log("steppimg up bkgdModeCutoffFactor => "+ bkgdModeCutoffFactor);
						}

						bottomedOut = true;

						MaximumFinder mf = new MaximumFinder();
						Polygon maxPoly = mf.getMaxima(targetIP, minTolerance, false);

						maxXs = new int[maxPoly.npoints];
						maxYs = new int[maxPoly.npoints];

						maxXs = maxPoly.xpoints;
						maxYs = maxPoly.ypoints;
						IJ.log(maxXs.length+" maxima  "+ imp.getTitle() +" x=" + fss + " bkmcof=" + bkgdModeCutoffFactor);
						
						if (maxXs.length < 10)
							continue;
						
						for (int n=0; n<maxXs.length; n++) {

							if (!maxCum.contains(maxXs[n]+","+maxYs[n])) {
								maxCum.add(maxXs[n]+","+maxYs[n]);
								ImageProcessor modIP = gaussianDiffIP.duplicate();
								if (bkgdModeCutoffFactor * fbkgdMode < fIP.getPixel(maxXs[n], maxYs[n]))
									bottomedOut = false;
								modIP.multiply(maskScaleFactor * (((double)fIP.getPixel(maxXs[n], maxYs[n])))/255);
								fIP.copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
								targetIP.copyBits(modIP, maxXs[n], maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
								targetIP.fillOval(maxXs[n]-blankWidth/2, maxYs[n]-blankHeight/2, blankWidth, blankHeight);
							}
						}
					}
					fIP.subtract(bkgdFloorFactor*fbkgdMode);
					imp.getStack().getProcessor(s).setPixels(fIP.getPixels());
					doneDestreak[s-1] = true;
					IJ.append(""+ s + " "+ bkgdModeCutoffFactor, outPath+"zags_"+imp.getTitle().split("_")[0]+"_"+channelRange+".log");


					if (getMonitorStatus()) {				
						if (!monitorImp.isVisible())
							monitorImp.show();
						monitorImp.setProcessor(fIP);
						monitorImp.updateAndRepaintWindow();
						targetImp.setProcessor(targetIP);
						if (!targetImp.isVisible())
							targetImp.show();
						targetImp.updateAndRepaintWindow();
					}
				}
			}
					);
			
			destreakThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
				public void uncaughtException(Thread myThread, Throwable e) {
					IJ.handleException(e);
				}
			}
			);
			destreakThread.start();
			
		}

		boolean doneAllSlices = false;
		while (!doneAllSlices) {
			doneAllSlices = true;
			for (int b=1; b<doneDestreak.length; b++) {
				if (doneDestreak[b] == false) {
					doneAllSlices = false;
					continue;
				}
			}
		}
		imp.setSlice(slice);
		imp.updateAndDraw();
		targetImp.close();
		targetImp.flush();
		monitorImp.close();
		monitorImp.flush();
	}

	private boolean getMonitorStatus() {
		return (monitorCheckbox != null && monitorCheckbox.getState());
	}

	public void doHyperStack(ImagePlus impHS){
		String path= (new File(outPath)).canWrite()?outPath:IJ.getDirectory("Choose where to save output");
		Roi roi = impHS.getRoi();
		String title = impHS.getTitle();
		String[] titleChunks =  title.split( "[ _:\\"+File.separator+"]");
		int lot = titleChunks.length;
		String titleShort  = titleChunks[0]+titleChunks[lot-1];
		IJ.log(path+titleShort+"_#.tif");
		int[] dim = impHS.getDimensions();
		int w = impHS.getRoi().getBounds().width;
		int h = impHS.getRoi().getBounds().height;
		int c = dim[2];
		int z = dim[3];
		int t = dim[4];

		String[] range = Tools.split(channelRange, " -");
		double c1 = Tools.parseDouble(range[0]);
		double c2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
		int firstC = (Double.isNaN(c1)?1:(int)c1);
		int lastC = (Double.isNaN(c2)?firstC:(int)c2);
		if (firstC<1) 
			firstC =1;
		if (lastC>impHS.getNChannels()) 
			lastC = impHS.getNChannels();
		if (firstC>lastC) {
			firstC=1; 
			lastC=impHS.getNChannels();
		}

		Date currentDate = new Date();
		long msec = currentDate.getTime();	
		long sec = msec/1000;

		for(int f=1;f<=t;f++){
			for (int ch=firstC; ch<=lastC; ch++) {
				IJ.log(path+titleShort+"_"+ch+"_"+f+".tif");
				if ((new File(path+titleShort+"_"+ch+"_"+f+".tif")).canRead()) {
					IJ.log("already exists");
					continue;
				}
				impHS.setRoi(roi);
				MQTVS_Duplicator duper = new MQTVS_Duplicator();
				ImagePlus impHS_dup = duper.run(impHS, ch, ch, 1, impHS.getNSlices(), f, f, 1, false, sec);
				impHS_dup.setCalibration(impHS.getCalibration());
				impHS_dup.getCalibration().pixelWidth = impHS.getCalibration().pixelWidth;
				impHS_dup.getCalibration().pixelHeight = impHS.getCalibration().pixelHeight;
				impHS_dup.getCalibration().pixelDepth = impHS.getCalibration().pixelDepth;

				IJ.run(impHS_dup, "Select All", "");
				Slicer slicer = new Slicer();
				slicer.setNointerpolate(false); //clumsy, don't use true ever
				slicer.setOutputZSpacing(1);  
				Slicer.setStartAt("Top");
				ImagePlus impHS_duprs = slicer.reslice(impHS_dup);
				
				impHS_duprs.setTitle(titleShort+"_"+ch+"_"+f);
				impHS_duprs.setCalibration(impHS.getCalibration());
				impHS_duprs.getCalibration().pixelWidth = impHS.getCalibration().pixelWidth;
				impHS_duprs.getCalibration().pixelHeight = impHS.getCalibration().pixelHeight;
				impHS_duprs.getCalibration().pixelDepth = impHS.getCalibration().pixelWidth;
				String zagsLog = IJ.openAsString(path+"zags_"+titleShort+"_"+ch+".log");
				if (zagsLog != null && !zagsLog.startsWith("Error")) {
					String[] zagsLines = zagsLog.split("\\n");
//					double[] zagsBKMCOFs = new double[h];
//					for (int xz=zagsLines.length-1; xz>zagsLines.length-1-h; xz--)
//						zagsBKMCOFs[zagsLines.length-1-xz] = Double.parseDouble(zagsLines[xz].split(" ")[1]);
					double[] zagsBKMCOFs = new double[zagsLines.length];
					for (int xz=0; xz<zagsLines.length-1; xz++)
						zagsBKMCOFs[xz] = Double.parseDouble(zagsLines[xz].split(" ")[1]);
					double sum =0;
					Arrays.sort(zagsBKMCOFs);
					for (double zagsBKMCOF:zagsBKMCOFs)
						sum += zagsBKMCOF;
					origBkgdModeCutoffFactor = /*0.5**/sum/zagsLines.length; //mean from last tpt stack
												//not sure why I was taking half...
				}
				IJ.run(impHS_duprs, "Correct diSPIM ZStreaks...", "maskwidth="+maskWidth+" mask="+maskScaleFactor+" max="+maxTolerance+" min="+minTolerance+" iterations="+iterations+" blankwidth="+blankWidth+" blankheight="+blankHeight+" bkgd="+origBkgdModeCutoffFactor+" floor="+bkgdFloorFactor+" cpu="+cpuLimit+" thread="+threadTimeLimit+" channel(s)="+ch+" path=["+path.replace(" ", "spaceSPACE")+"]");

				impHS_duprs.updateAndRepaintWindow();

				slicer.setNointerpolate(false); //clumsy, don't use true ever
				slicer.setOutputZSpacing(1);  
				IJ.run(impHS_duprs, "Select All", "");
				ImagePlus impHS_duprsrs = slicer.reslice(impHS_duprs);

				IJ.saveAsTiff(impHS_duprsrs, path+titleShort+"_"+ch+"_"+f+".tif");
				impHS_dup.close();
				impHS_dup.flush();
				impHS_duprs.close();
				impHS_duprs.flush();
				impHS_duprsrs.close();
				impHS_duprsrs.flush();
			}
		}
	}
}
