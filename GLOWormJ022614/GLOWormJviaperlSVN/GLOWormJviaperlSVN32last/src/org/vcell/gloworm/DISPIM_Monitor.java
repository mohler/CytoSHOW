package org.vcell.gloworm;

import java.awt.Button;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.YesNoCancelDialog;
import ij.io.FileInfo;
import ij.macro.MacroRunner;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.FFT;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.MultiFileInfoVirtualStack;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.SyncWindows;
import ij.process.ColorProcessor;

public class DISPIM_Monitor implements PlugIn {

	private boolean doDecon;

	public boolean isDoDecon() {
		return doDecon;
	}

	public void setDoDecon(boolean doDecon) {
		this.doDecon = doDecon;
	}

	public void run(String arg) {
		String[] args = arg.split("\\|");
		IJ.log(arg);
		int minA =0;
		int maxA = 255;
		String channelsA = "11";
		String modeA="composite";
		double vWidth = 0.163;
		double vHeight = 0.163;
		double vDepthRaw = 1.000;
		double vDepthDecon = 0.163;
		String vUnit = "micron";
		doDecon = true;
		int cropWidth = 325;
		int cropHeight = 425;

		String dir = "";
		dir = args[0];
		IJ.log(dir);
		//waitForUser("");
		while (!(new File(dir)).isDirectory()) {
			dir = IJ.getDirectory("Select top directory for diSPIM raw data");
		}
		IJ.log(dir);
		String tempDir = IJ.getDirectory("temp");
		String[] fileListA = {""};
		String[] fileListB = {""};
		//		fileListB = newArray("");
		String[] fileRanksA = {""};
		String[] fileRanksB = {""};
		String[] fileNumsA = {""};
		String[] fileNumsB = {""};
		String[] fileSortA = {""};
		String[] fileSortB = {""};
		String[] newTifListA = {""};
		String[] newTifListB = {""};
		String[] listA = {""};
		String[] listB = {""};
		ImagePlus impA = null;
		ImagePlus impB = null;
		ImagePlus impDF1 = null;
		ImagePlus impDF2 = null;

		String[] dirChunks = dir.split(IJ.isWindows()?"\\\\":"/");
		String dirName = dirChunks[dirChunks.length-1];

		IJ.saveString("", dir+"Big5DFileListA.txt");
		while(!(new File(dir+"Big5DFileListA.txt")).exists())
			IJ.wait(100);
		IJ.saveString("", dir+"Big5DFileListB.txt");
		while(!(new File(dir+"Big5DFileListB.txt")).exists())
			IJ.wait(100);
		IJ.saveString("", dir+"BigMAXFileListA.txt");
		while(!(new File(dir+"BigMAXFileListA.txt")).exists())
			IJ.wait(100);
		IJ.saveString("", dir+"BigMAXFileListB.txt");
		while(!(new File(dir+"BigMAXFileListB.txt")).exists())
			IJ.wait(100);

		int wavelengths;
		int zSlices;
		if(args.length>2){
			wavelengths = Integer.parseInt(args[1]);
			zSlices = Integer.parseInt(args[2]);
		} else {
			GenericDialog gd = new GenericDialog("Data Set Parameters?");
			gd.addNumericField("Wavelengths", 2, 0);
			gd.addNumericField("Z Slices/Stack", 49, 0);
			gd.showDialog();;
			wavelengths = (int) gd.getNextNumber();
			zSlices = (int) gd.getNextNumber();
		}

		fileListA = new File(""+dir+"SPIMA").list();
		fileListB = new File(""+dir+"SPIMB").list();
		fileRanksA = Arrays.copyOf(fileListA, fileListA.length);
		fileRanksB = Arrays.copyOf(fileListB, fileListB.length);
		fileNumsA = Arrays.copyOf(fileListA, fileListA.length);
		fileNumsB = Arrays.copyOf(fileListB, fileListB.length);
		fileSortA = Arrays.copyOf(fileListA, fileListA.length);
		fileSortB = Arrays.copyOf(fileListB, fileListB.length);

		String big5DFileListAString = ("");	    
		String big5DFileListBString =("");

		for (int a=0;a<fileListA.length;a++) {
			if(!fileListA[a].endsWith(".roi") && !fileListA[a].endsWith(".DS_Store") ){
				String sring = fileListA[a].replace("/", "");
				String subsring = sring;
				String prefix = "";
				double n = Double.NaN;
				try {
					n = Integer.parseInt(subsring);
				} catch(NumberFormatException e) {
					n = Double.NaN;
				}
				while (Double.isNaN(n)) {
					try {
						prefix = prefix + subsring.substring(0,1);
						subsring = subsring.substring(1);
						n = Integer.parseInt(subsring.split(" ")[0]);
						IJ.log(subsring);
						IJ.log(prefix);
					} catch(NumberFormatException ne) {
						n = Double.NaN;
					} catch(StringIndexOutOfBoundsException se) {
						n = Double.NaN;
					}
				}
				if(prefix.toLowerCase().startsWith("t")
						|| prefix.toLowerCase().startsWith("f"))
					prefix = "aaaaa"+prefix;
				int numer = Integer.parseInt(subsring.split(" ")[0]);
				IJ.log(subsring+" "+numer);
				fileNumsA[a] =prefix+ IJ.pad(numer,6)+"|"+sring;
				fileNumsB[a] =prefix+ IJ.pad(numer,6)+"|"+sring;
			} else {
				fileNumsA[a] = "";
				fileNumsB[a] = "";
			}

		}
		Arrays.sort(fileNumsA);
		Arrays.sort(fileNumsB);

		for (int r=0;r<fileNumsA.length;r++) {
			String[] splt =fileNumsA[r].split("\\|");
			if (splt.length > 1)
				fileSortA[r] = splt [1];
			else 
				fileSortA[r] = "";
			IJ.log(r+" "+" "+fileNumsA[r]+" "+fileSortA[r]);
			splt =fileNumsB[r].split("\\|");
			if (splt.length > 1)
				fileSortB[r] = splt[1];
			else 
				fileSortB[r] = "";

		}

		for (int d=0;d<fileSortA.length;d++) {
			boolean skipIt = false;
			String nextPathA =  dir + "SPIMA" + File.separator + fileSortA[d];
			String nextPathB =  dir + "SPIMB" + File.separator + fileSortB[d];
			IJ.log(nextPathA);
			IJ.log(nextPathB);
			if ( (new File(nextPathA)).isDirectory()  && (new File(nextPathB)).isDirectory()) {
				newTifListA = (new File(nextPathA)).list();
				newTifListB = (new File(nextPathB)).list();
				if (newTifListA.length != newTifListB.length || newTifListA.length < wavelengths*zSlices)
					skipIt = true;
				if(!skipIt) {
					Arrays.sort(newTifListA);		
					for (int f=0;f<newTifListA.length;f++) {
						while(!(new File(dir+"Big5DFileListA.txt")).exists())
							IJ.wait(100);
						if (!newTifListA[f].endsWith(".roi") && !newTifListA[f].endsWith(".DS_Store")
								&& big5DFileListAString.indexOf( nextPathA + File.separator+newTifListA[f])<0)
							IJ.append(nextPathA + File.separator +newTifListA[f], dir+"Big5DFileListA.txt");
					}
					Arrays.sort(newTifListB);		
					for (int f=0;f<newTifListB.length;f++) {
						while(!(new File(dir+"Big5DFileListB.txt")).exists())
							IJ.wait(100);
						if (!newTifListB[f].endsWith(".roi") && !newTifListB[f].endsWith(".DS_Store")
								&& big5DFileListBString.indexOf( nextPathB + File.separator+newTifListB[f])<0)
							IJ.append(nextPathB + File.separator +newTifListB[f], dir+"Big5DFileListB.txt");
					}
				}
			}

		}


		IJ.log(""+WindowManager.getImageCount());

		if ((new File(dir+"Big5DFileListA.txt" )).length() >0) {
			//		    IJ.run("Stack From List...", "open="+dir+"Big5DFileListA.txt use");
			impA = new ImagePlus();
			impA.setStack(new ListVirtualStack(dir+"Big5DFileListA.txt"));
			int stkNSlices = impA.getNSlices();

			impA.setTitle("SPIMA: "+dir);

			impA.setDimensions(wavelengths, zSlices, stkNSlices/(wavelengths*zSlices));
			IJ.log(wavelengths+" "+zSlices+" "+stkNSlices/(wavelengths*zSlices));
			if (wavelengths > 1){
				impA = new CompositeImage(impA);
				while (!impA.isComposite()) {
					IJ.wait(100);
					//selectWindow("SPIMA: "+dir);
				}
				impA.setPosition(1, 1, 1);	
				IJ.run("Green");
				impA.resetDisplayRange();
				impA.setPosition(2, 1, 1);	
				IJ.run("Red");
				impA.resetDisplayRange();
			}
			Calibration cal = impA.getCalibration();
			cal.pixelWidth = vWidth;
			cal.pixelHeight = vHeight;
			cal.pixelDepth = vDepthRaw;
			cal.setUnit(vUnit);

			impA.setPosition(wavelengths, zSlices/2, stkNSlices/(wavelengths*zSlices));	

			impA.resetDisplayRange();
			impA.setPosition(1, zSlices/2, stkNSlices/(wavelengths*zSlices));	
			impA.resetDisplayRange();
			if (impA.isComposite())
				((CompositeImage)impA).setMode(CompositeImage.COMPOSITE);
			impA.show();

		}

		if ((new File(dir+"Big5DFileListB.txt" )).length() >0) {
			//		    IJ.run("Stack From List...", "open="+dir+"Big5DFileListB.txt use");
			impB = new ImagePlus();
			impB.setStack(new ListVirtualStack(dir+"Big5DFileListB.txt"));
			int stkNSlices = impB.getNSlices();

			impB.setTitle("SPIMB: "+dir);

			impB.setDimensions(wavelengths, zSlices, stkNSlices/(wavelengths*zSlices));
			IJ.log(wavelengths+" "+zSlices+" "+stkNSlices/(wavelengths*zSlices));
			if (wavelengths > 1){
				impB = new CompositeImage(impB);
				while (!impB.isComposite()) {
					IJ.wait(100);
					//selectWindow("SPIMB: "+dir);
				}
				impB.setPosition(1, 1, 1);	
				IJ.run("Green");
				impB.resetDisplayRange();
				impB.setPosition(2, 1, 1);	
				IJ.run("Red");
				impB.resetDisplayRange();
			}
			Calibration cal = impB.getCalibration();
			cal.pixelWidth = vWidth;
			cal.pixelHeight = vHeight;
			cal.pixelDepth = vDepthRaw;
			cal.setUnit(vUnit);

			impB.setPosition(wavelengths, zSlices/2, stkNSlices/(wavelengths*zSlices));	

			impB.resetDisplayRange();
			impB.setPosition(1, zSlices/2, stkNSlices/(wavelengths*zSlices));	
			impB.resetDisplayRange();
			if (impB.isComposite())
				((CompositeImage)impB).setMode(CompositeImage.COMPOSITE);
			impB.show();

		}

		IJ.run("Tile");
		IJ.log(""+WindowManager.getImageCount());

		YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), "Deconvolve while aquiring?", "Would you like volumes to be deconvolved/fused \nas soon as they are captured?  \n\nChoose this option if you are ready \nto initiate time-lapse recording.");
		//		d.setVisible(true);
		if (d.cancelPressed()) {
			doDecon = false;
		} else if (d.yesPressed())
			doDecon = true;
		else
			doDecon = false;



		if (doDecon) {

			while (impA.getRoi()==null || impB.getRoi()==null
					|| impA.getRoi().getBounds().width!=cropWidth || impA.getRoi().getBounds().height!=cropHeight
					|| impB.getRoi().getBounds().width!=cropWidth || impB.getRoi().getBounds().height!=cropHeight) {
				WindowManager.setTempCurrentImage(impA);
				if (impA.getRoi()==null) {
					if (!((new File(dir+ dirName +"A_crop.roi")).canRead())) {
						IJ.makeRectangle(0,0,cropWidth,cropHeight);
					} else {
						IJ.open(dir+ dirName +"A_crop.roi");
					}
				} else if (impA.getRoi().getBounds().width!=cropWidth || impA.getRoi().getBounds().height!=cropHeight) {
					impA.setRoi(impA.getRoi().getBounds().x + (impA.getRoi().getBounds().width -cropWidth)/2,
							impA.getRoi().getBounds().y + (impA.getRoi().getBounds().height -cropHeight)/2,
							cropWidth,cropHeight);
					impA.setRoi(impA.getRoi().getBounds().x<0?0:impA.getRoi().getBounds().x,
							impA.getRoi().getBounds().y<0?0:impA.getRoi().getBounds().y,
									cropWidth,cropHeight);
				}
				WindowManager.setTempCurrentImage(impB);
				if (impB.getRoi()==null) {
					if (!((new File(dir+ dirName +"B_crop.roi")).canRead())) {
						IJ.makeRectangle(0,0,cropWidth,cropHeight);
					} else {
						IJ.open(dir+ dirName +"B_crop.roi");
					}
				} else if (impB.getRoi().getBounds().width!=cropWidth || impB.getRoi().getBounds().height!=cropHeight) {
					impB.setRoi(impB.getRoi().getBounds().x + (impB.getRoi().getBounds().width -cropWidth)/2,
							impB.getRoi().getBounds().y + (impB.getRoi().getBounds().height -cropHeight)/2,
							cropWidth,cropHeight);
					impB.setRoi(impB.getRoi().getBounds().x<0?0:impB.getRoi().getBounds().x,
							impB.getRoi().getBounds().y<0?0:impB.getRoi().getBounds().y,
									cropWidth,cropHeight);
				}
				WindowManager.setTempCurrentImage(null);

				IJ.runMacro("waitForUser(\"Select the regions containing the embryo\\\n for deconvolution/fusion processing\");");
			}
			IJ.saveAs(impA, "Selection", dir+dirName +"A_crop.roi");
			IJ.saveAs(impB, "Selection", dir+dirName +"B_crop.roi");

			int wasFrameA = impA.getFrame();
			int wasFrameB = impB.getFrame();
			int wasSliceA = impA.getSlice();
			int wasSliceB = impB.getSlice();
			int wasChannelA = impA.getChannel();
			int wasChannelB = impB.getChannel();
			
			if (impDF1 == null) {
				impDF1 = new ImagePlus();
				impDF1.setStack("Decon-Fuse-Ch1:"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution1"));
				int stkNSlicesDF = impDF1.getStackSize();
				impDF1.setOpenAsHyperStack(true);
				impDF1.setDimensions(1, 296, stkNSlicesDF/(1*296));
				impDF1.show();
			} else {
				impDF1.setStack("Decon-Fuse-Ch1:"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution1"));
				int stkNSlicesDF = impDF1.getStackSize();
				impDF1.setOpenAsHyperStack(true);
				impDF1.setDimensions(1, 296, stkNSlicesDF/(1*296));
				impDF1.setWindow(WindowManager.getCurrentWindow());
			}
			if (wavelengths ==2) {
				if (impDF2  == null) {
					impDF2 = new ImagePlus();
					impDF2.setStack("Decon-Fuse-Ch2:"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution2"));
					int stkNSlicesDF = impDF2.getStackSize();
					impDF2.setOpenAsHyperStack(true);
					impDF2.setDimensions(1, 296, stkNSlicesDF/(1*296));
					impDF2.show();
				} else {
					impDF2.setStack("Decon-Fuse-Ch2:"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution2"));
					int stkNSlicesDF = impDF2.getStackSize();
					impDF2.setOpenAsHyperStack(true);
					impDF2.setDimensions(1, 296, stkNSlicesDF/(1*296));
					impDF2.setWindow(WindowManager.getCurrentWindow());
				}

			}

			
			for (int f=1;f<=impA.getNFrames();f++) {

				impA.setPositionWithoutUpdate(impA.getChannel(), impA.getSlice(), f);
				impA.setPositionWithoutUpdate(impA.getChannel(), impA.getSlice(), f);

				String frameName = ((ListVirtualStack)impA.getStack()).getDirectory(impA.getCurrentSlice());
				String timecode = ""+(new Date()).getTime();

				if (	   !(new File(dir+ "SPIMA_Ch1_processed"+ File.separator + frameName+ File.separator + frameName+".tif")).canRead()
						|| (wavelengths==2 && !(new File(dir+ "SPIMA_Ch2_processed"+ File.separator + frameName+ File.separator + frameName+".tif")).canRead())
						|| !(new File(dir+ "SPIMB_Ch1_processed"+ File.separator + frameName+ File.separator + frameName+".tif")).canRead()
						|| (wavelengths==2 && !(new File(dir+ "SPIMA_Ch2_processed"+ File.separator + frameName+ File.separator + frameName+".tif")).canRead())
						) {
					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed\");");
					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed\"+File.separator+\""+frameName+"\");");
					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed\");");
					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed\"+File.separator+\""+frameName+"\");");
					if (wavelengths==2) {
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed\");");
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed\"+File.separator+\""+frameName+"\");");
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed\");");
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed\"+File.separator+\""+frameName+"\");");
					}

					ImageStack stackA1 = new ImageStack(325,425);
					ImageStack stackA2 = new ImageStack(325,425);
					impA.getWindow().setEnabled(false);
					for (int i=1; i<=impA.getNSlices(); i++) {
						impA.setPositionWithoutUpdate(1, i, f);
						stackA1.addSlice(impA.getProcessor().crop());					
						if (wavelengths==2) {
							impA.setPositionWithoutUpdate(2, i, f);
							stackA2.addSlice(impA.getProcessor().crop());					
						}
					}
					impA.getWindow().setEnabled(true);
					ImagePlus impXA1 = new ImagePlus();
					impXA1.setStack(stackA1);
					impXA1.setCalibration(impA.getCalibration());
					//				impXA1.getCalibration().pixelDepth = impXA1.getCalibration().pixelWidth;
					IJ.saveAs(impXA1,"Tiff", dir+ "SPIMA_Ch1_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					if (wavelengths==2) {
						ImagePlus impXA2 = new ImagePlus();
						impXA2.setStack(stackA2);
						impXA2.setCalibration(impA.getCalibration());
						//				impXA2.getCalibration().pixelDepth = impXA2.getCalibration().pixelWidth;
						IJ.saveAs(impXA2,"Tiff", dir+ "SPIMA_Ch2_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					}

					ImageStack stackB1 = new ImageStack(325,425);
					ImageStack stackB2 = new ImageStack(325,425);
					impB.getWindow().setEnabled(false);
					for (int i=1; i<=impB.getNSlices(); i++) {
						impB.setPositionWithoutUpdate(1, i, f);
						stackB1.addSlice(impB.getProcessor().crop());					
						if (wavelengths==2) {
							impB.setPositionWithoutUpdate(2, i, f);
							stackB2.addSlice(impB.getProcessor().crop());					
						}
					}
					impB.getWindow().setEnabled(true);
					ImagePlus impXB1 = new ImagePlus();
					impXB1.setStack(stackB1);
					impXB1.setCalibration(impB.getCalibration());
					//				impXB1.getCalibration().pixelDepth = impXB1.getCalibration().pixelWidth;
					IJ.saveAs(impXB1,"Tiff", dir+ "SPIMB_Ch1_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					if (wavelengths==2) {
						ImagePlus impXB2 = new ImagePlus();
						impXB2.setStack(stackB2);
						impXB2.setCalibration(impB.getCalibration());
						//				impXB2.getCalibration().pixelDepth = impXB2.getCalibration().pixelWidth;
						IJ.saveAs(impXB2,"Tiff", dir+ "SPIMB_Ch2_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					}

				}

				if (	   !(new File(dir+ "Deconvolution1"+ File.separator + "Decon_" + frameName+".tif")).canRead()
						|| (wavelengths==2 && !(new File(dir+ "Deconvolution2"+ File.separator + "Decon_" + frameName+".tif")).canRead())
						) {
					String deconString1 = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.1625\", \"res_y double 0.1625\", \"res_z double 1.0\", \"mtxFileDirectory string "+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimAFileDir string "+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimBFileDir string "+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"baseImage string "+frameName+"\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconvDirString string "+dir.replace("\\", "\\\\")+"Deconvolution1\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\")";
					IJ.wait(5000);

					new MacroRunner(
							"cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
									"cpuChunks = split(cpuPerformance,\"\\\"\");" +
									"x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +
									"while(x >50) {\n" +
									"	wait(10000);" +
									"	cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
									"	cpuChunks = split(cpuPerformance,\"\\\"\");" +
									"	x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +	
									"}" +
									"print(\""+frameName+"_1 processing...\");" +

							"			File.saveString(\'"+deconString1+"\', \""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".sct\");" + 


							"		    f = File.open(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".bat\");\n" + 
							"		    batStringD = \"@echo off\";\n" + 
							"		    print(f,batStringD);\n" + 
							"		    batStringC = \"C\\:\";\n" + 
							"		    print(f,batStringC);\n" + 
							"		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n" + 
							"		    print(f,batStringA);\n" + 
							"		    batStringB = \"start /LOW /b mipav -s \\\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".sct\\\" -hide\";\n" + 
							"		    print(f,batStringB);\n" + 
							"		    File.close(f);	    \n" + 

							"batJob = exec(\"cmd64\", \"/c\", \""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".bat\");" +
							"print(\""+frameName+"_1 complete.\");" +
							"delBat = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".bat\");" + 
							"delSct = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".sct\");" + 
							""
							);

					if (wavelengths==2) {
						String deconString2 = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.1625\", \"res_y double 0.1625\", \"res_z double 1.0\", \"mtxFileDirectory string "+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimAFileDir string "+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimBFileDir string "+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"baseImage string "+frameName+"\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconvDirString string "+dir.replace("\\", "\\\\")+"Deconvolution2\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\")";
						IJ.wait(5000);

						new MacroRunner(
								"cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
										"cpuChunks = split(cpuPerformance,\"\\\"\");" +
										"x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +
										"while(x >50) {\n" +
										"	wait(10000);" +
										"	cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
										"	cpuChunks = split(cpuPerformance,\"\\\"\");" +
										"	x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +	
										"}" +
										"print(\""+frameName+"_2 processing...\");" +

								"			File.saveString(\'"+deconString2+"\', \""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".sct\");" + 


								"		    f = File.open(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".bat\");\n" + 
								"		    batStringD = \"@echo off\";\n" + 
								"		    print(f,batStringD);\n" + 
								"		    batStringC = \"C\\:\";\n" + 
								"		    print(f,batStringC);\n" + 
								"		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n" + 
								"		    print(f,batStringA);\n" + 
								"		    batStringB = \"start /LOW /b mipav -s \\\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".sct\\\" -hide\";\n" + 
								"		    print(f,batStringB);\n" + 
								"		    File.close(f);	    \n" + 

								"batJob = exec(\"cmd64\", \"/c\", \""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".bat\");" +
								"print(\""+frameName+"_2 complete.\");" +
								"delBat = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".bat\");" + 
								"delSct = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".sct\");" + 
								""
								);
					}
				}
				//				IJ.wait(15000);
			}
			impA.setPosition(wasChannelA, wasSliceA, wasFrameA);
			impB.setPosition(wasChannelB, wasSliceB, wasFrameB);
		}		


		while (true) {
			boolean focus = false;
			listA = new File(""+dir+"SPIMA").list();
			listB = new File(""+dir+"SPIMB").list();
			big5DFileListAString = IJ.openAsString(dir+"Big5DFileListA.txt");	    
			big5DFileListBString = IJ.openAsString(dir+"Big5DFileListB.txt");

			while (fileListA.length == listA.length || fileListB.length == listB.length ) {
				if (IJ.escapePressed())
					if (!IJ.showMessageWithCancel("Cancel diSPIM Monitor Updates?"
							, "Monitoring of "+ dir+ " paused by Escape.\nClick OK to resume."))
						return;
					else
						IJ.resetEscape();
				listA = new File(""+dir+"SPIMA").list();
				listB = new File(""+dir+"SPIMB").list();
				IJ.wait(5000);
			}	
			//
			//			if (isOpen("Display Channels")) {
			//				selectWindow("Display Channels");
			//				run("Close");
			//			}
			//
			fileListA = new File(""+dir+"SPIMA").list();
			fileListB = new File(""+dir+"SPIMB").list();

			long modDateA = 0;
			String recentestA = "";
			for (int a=0;a<fileListA.length;a++) {
				if(!fileListA[a].endsWith(".roi") && !fileListA[a].endsWith(".DS_Store") ){
					if (modDateA < (new File(dir +"SPIMA" + File.separator + fileListA[a])).lastModified()) {
						modDateA = (new File(dir +"SPIMA" + File.separator + fileListA[a])).lastModified();
						recentestA = dir + "SPIMA" + File.separator + fileListA[a];
					}
				}
			}
			IJ.log(recentestA +"\n"+ modDateA);
			if ((new File(recentestA)).isDirectory()) {
				String[] newTifList = {""};
				while (newTifList.length < wavelengths*zSlices)
					newTifList = (new File(recentestA)).list();
				Arrays.sort(newTifList);		
				for (int f=0;f<newTifList.length;f++) {
					while(!(new File(dir+"Big5DFileListA.txt").exists()))
						IJ.wait(100);
					if (!newTifList[f].endsWith(".roi") && !newTifList[f].endsWith(".DS_Store")
							&& big5DFileListAString.indexOf(recentestA+newTifList[f])<0)
						IJ.append(recentestA + File.separator + newTifList[f], dir+"Big5DFileListA.txt");
				}
			}

			fileListA = new File(""+dir+"SPIMA").list();
			fileListB = new File(""+dir+"SPIMB").list();

			long modDateB = 0;
			String recentestB = "";
			String recentestBname = "";
			for (int a=0;a<fileListB.length;a++) {
				if(!fileListB[a].endsWith(".roi") && !fileListB[a].endsWith(".DS_Store") ){
					if (modDateB < (new File(dir +"SPIMB" + File.separator + fileListB[a])).lastModified()) {
						modDateB = (new File(dir +"SPIMB" + File.separator + fileListB[a])).lastModified();
						recentestB = dir + "SPIMB" + File.separator + fileListB[a];
						recentestBname = fileListB[a];
					}
				}
			}
			IJ.log(recentestB +"\n"+ modDateB);
			if(recentestBname.toLowerCase().startsWith("focus"))
				focus = true;
			if ((new File(recentestB)).isDirectory()) {
				String[] newTifList = {""};
				while (newTifList.length < wavelengths*zSlices)
					newTifList = (new File(recentestB)).list();
				Arrays.sort(newTifList);		
				for (int f=0;f<newTifList.length;f++) {
					while(!(new File(dir+"Big5DFileListB.txt").exists()))
						IJ.wait(100);
					if (!newTifList[f].endsWith(".roi") && !newTifList[f].endsWith(".DS_Store")
							&& big5DFileListBString.indexOf(recentestA+newTifList[f])<0)
						IJ.append(recentestB + File.separator + newTifList[f], dir+"Big5DFileListB.txt");
				}
			}
			boolean wasSynched = false;
			ArrayList<ImagePlus> synchedImpsArrayList = new ArrayList<ImagePlus>();
			if (SyncWindows.getInstance()!=null) {
				int v=0;
				while (SyncWindows.getInstance().getImageFromVector(v) != null) {
					wasSynched = true;
					synchedImpsArrayList.add(SyncWindows.getInstance().getImageFromVector(v));
					v++;
				}
				SyncWindows.getInstance().close();
			}
			int cA = impA.getChannel();
			int zA = impA.getSlice();
			int tA = impA.getFrame();
			ListVirtualStack stackA = new ListVirtualStack(dir+"Big5DFileListA.txt");
			int stkNSlicesA = stackA.getSize();
			impA.setStack(stackA, wavelengths, zSlices, stkNSlicesA/(wavelengths*zSlices));
			impA.setPosition(cA, zA, tA==impA.getNFrames()-1?impA.getNFrames():tA);
			impA.setWindow(WindowManager.getCurrentWindow());


			int cB = impB.getChannel();
			int zB = impB.getSlice();
			int tB = impB.getFrame();
			ListVirtualStack stackB = new ListVirtualStack(dir+"Big5DFileListB.txt");
			int stkNSlicesB = stackB.getSize();
			impB.setStack(stackB, wavelengths, zSlices, stkNSlicesB/(wavelengths*zSlices));
			impB.setPosition(cB, zB, tB==impB.getNFrames()-1?impB.getNFrames():tB);
			impB.setWindow(WindowManager.getCurrentWindow());

			if (wasSynched) {
				SyncWindows sw = new SyncWindows();
				for (ImagePlus impS:synchedImpsArrayList) {
					sw.addImp(impS);
				}
			}



			if (focus) {
				//SAD THAT I HAVE TO FAKE THIS, BUT NOT WORKING IN MY ATTEMPTS AT JAVA-ONLY...
				String fftMacroString = 
						"		    dir = \"" +dir.replace("\\", "\\\\")+"\";\n" + 
								"			autoFPath = dir+\"AutoFocusCommaSpace.txt\";" +
								"		    print(nImages);\n" + 
								"		    File.delete(autoFPath);\n" + 
								"			autoFocusString = \"\";\n" + 
								"			for (i=1;i<=nImages;i++){\n" + 
								"				print(nImages+\" \"+i);\n" + 
								"				\n" + 
								"				setBatchMode(true);\n" + 
								"				selectImage(i);\n" + 
								"		\n" + 
								"				source = getTitle();\n" + 
								"				Stack.getDimensions(width, height, channels, zDepth, frames);\n" + 
								"				Stack.getPosition(channel, slice, frame);\n" + 
								"				Stack.setPosition(channel, slice, frames);\n" + 
								"				for (z=0; z<zDepth; z++) { \n" + 
								"					Stack.setSlice(z+1);\n" + 
								"					run(\"FFT, no auto-scaling\");\n" + 
								"					if (z==0) {\n" + 
								"						rename(\"FFTstack\");	\n" + 
								"					} else {\n" + 
								"						run(\"Select All\");\n" + 
								"						run(\"Copy\");\n" + 
								"						close();\n" + 
								"						selectWindow(\"FFTstack\");\n" + 
								"						run(\"Add Slice\");\n" + 
								"						if (z>0)\n" + 
								"							Stack.setSlice(z+2);\n" + 
								"						run(\"Select All\");\n" + 
								"						run(\"Paste\");\n" + 
								"					}\n" + 
								"					selectWindow(source);\n" + 
								"				}\n" + 
								"				Stack.setPosition(channel, slice, frame);\n" + 
								"				selectWindow(\"FFTstack\");\n" + 
								"				makeOval(250, 250, 13, 13);\n" + 
								"				run(\"Clear\", \"stack\");\n" + 
								"				makeOval(220, 220, 73, 73);\n" + 
								"				run(\"Clear Outside\", \"stack\");\n" + 
								"				run(\"Plot Z-axis Profile\");\n" + 
								"				close();\n" + 
								"				selectWindow(\"FFTstack\");\n" + 
								"				close();\n" + 
								"				\n" + 
								"				sliceAvgs = newArray(zDepth);\n" + 
								"				List.clear;\n" + 
								"				for (z=1; z<zDepth; z++) { \n" + 
								"					sliceAvgs[z] = getResult(\"Mean\", z);\n" + 
								"					//print(sliceAvgs[z] );\n" + 
								"					List.set(sliceAvgs[z] , z);\n" + 
								"				}\n" + 
								"				\n" + 
								"				Array.sort(sliceAvgs);\n" + 
								"				print(source+\": Best focus in slice \"+List.get(sliceAvgs[zDepth-1]));\n" + 
								"				autoFocusString = autoFocusString + List.get(sliceAvgs[zDepth-1])+\", \";\n" + 
								"				selectWindow(\"Results\");\n" + 
								"				run(\"Close\");\n" + 
								"				setBatchMode(false);\n" + 
								"				selectWindow(source);\n" + 
								"				Stack.setPosition(channel, slice, frame);\n" + 
								"				updateDisplay();\n" + 
								"			}\n" + 
								"			File.saveString(autoFocusString, autoFPath);			\n" + 
								"";
				IJ.runMacro(fftMacroString);
			}		    


			if (doDecon) {
				int wasFrameA = impA.getFrame();
				int wasFrameB = impB.getFrame();
				int wasSliceA = impA.getSlice();
				int wasSliceB = impB.getSlice();
				int wasChannelA = impA.getChannel();
				int wasChannelB = impB.getChannel();
				WindowManager.setTempCurrentImage(impA);
				IJ.open(dir+ dirName +"B_crop.roi");
				WindowManager.setTempCurrentImage(impB);
				IJ.open(dir+ dirName +"B_crop.roi");
				WindowManager.setTempCurrentImage(null);

				for (int f=impA.getNFrames();f<=impA.getNFrames();f++) {

					impA.setPositionWithoutUpdate(impA.getChannel(), impA.getSlice(), f);
					impA.setPositionWithoutUpdate(impA.getChannel(), impA.getSlice(), f);

					String frameName = ((ListVirtualStack)impA.getStack()).getDirectory(impA.getCurrentSlice());
					String timecode = ""+(new Date()).getTime();

					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed\");");
					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed\"+File.separator+\""+frameName+"\");");
					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed\");");
					IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed\"+File.separator+\""+frameName+"\");");
					if (wavelengths==2) {
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed\");");
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed\"+File.separator+\""+frameName+"\");");
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed\");");
						IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed\"+File.separator+\""+frameName+"\");");
					}

					ImageStack stackA1 = new ImageStack(325,425);
					ImageStack stackA2 = new ImageStack(325,425);
					impA.getWindow().setEnabled(false);
					for (int i=1; i<=impA.getNSlices(); i++) {
						impA.setPositionWithoutUpdate(1, i, f);
						stackA1.addSlice(impA.getProcessor().crop());					
						if (wavelengths==2) {
							impA.setPositionWithoutUpdate(2, i, f);
							stackA2.addSlice(impA.getProcessor().crop());	
						}
					}
					impA.getWindow().setEnabled(true);
					ImagePlus impXA1 = new ImagePlus();
					impXA1.setStack(stackA1);
					impXA1.setCalibration(impA.getCalibration());
					//					impXA1.getCalibration().pixelDepth = impXA1.getCalibration().pixelWidth;
					IJ.saveAs(impXA1,"Tiff", dir+ "SPIMA_Ch1_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					if (wavelengths==2) {
						ImagePlus impXA2 = new ImagePlus();
						impXA2.setStack(stackA2);
						impXA2.setCalibration(impA.getCalibration());
						//					impXA2.getCalibration().pixelDepth = impXA2.getCalibration().pixelWidth;
						IJ.saveAs(impXA2,"Tiff", dir+ "SPIMA_Ch2_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					}
					ImageStack stackB1 = new ImageStack(325,425);
					ImageStack stackB2 = new ImageStack(325,425);
					impB.getWindow().setEnabled(false);
					for (int i=1; i<=impB.getNSlices(); i++) {
						impB.setPositionWithoutUpdate(1, i, f);
						stackB1.addSlice(impB.getProcessor().crop());					
						if (wavelengths==2) {
							impB.setPositionWithoutUpdate(2, i, f);
							stackB2.addSlice(impB.getProcessor().crop());	
						}
					}
					impB.getWindow().setEnabled(true);
					ImagePlus impXB1 = new ImagePlus();
					impXB1.setStack(stackB1);
					impXB1.setCalibration(impB.getCalibration());
					//					impXB1.getCalibration().pixelDepth = impXB1.getCalibration().pixelWidth;
					IJ.saveAs(impXB1,"Tiff", dir+ "SPIMB_Ch1_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					if (wavelengths==2) {
						ImagePlus impXB2 = new ImagePlus();
						impXB2.setStack(stackB2);
						impXB2.setCalibration(impB.getCalibration());
						//					impXB2.getCalibration().pixelDepth = impXB2.getCalibration().pixelWidth;
						IJ.saveAs(impXB2,"Tiff", dir+ "SPIMB_Ch2_processed"+ File.separator + frameName+ File.separator + frameName+".tif");
					}
					String deconString1 = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.1625\", \"res_y double 0.1625\", \"res_z double 1.0\", \"mtxFileDirectory string "+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimAFileDir string "+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimBFileDir string "+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"baseImage string "+frameName+"\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconvDirString string "+dir.replace("\\", "\\\\")+"Deconvolution1\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\")";
					IJ.wait(5000);

					new MacroRunner(
							"cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
									"cpuChunks = split(cpuPerformance,\"\\\"\");" +
									"x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +
									"while(x >50) {\n" +
									"	wait(10000);" +
									"	cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
									"	cpuChunks = split(cpuPerformance,\"\\\"\");" +
									"	x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +	
									"}" +
									"print(\""+frameName+"_1 processing...\");" +

							"			File.saveString(\'"+deconString1+"\', \""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".sct\");" + 


							"		    f = File.open(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".bat\");\n" + 
							"		    batStringD = \"@echo off\";\n" + 
							"		    print(f,batStringD);\n" + 
							"		    batStringC = \"C\\:\";\n" + 
							"		    print(f,batStringC);\n" + 
							"		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n" + 
							"		    print(f,batStringA);\n" + 
							"		    batStringB = \"start /LOW /b mipav -s \\\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".sct\\\" -hide\";\n" + 
							"		    print(f,batStringB);\n" + 
							"		    File.close(f);	    \n" + 

							"batJob = exec(\"cmd64\", \"/c\", \""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".bat\");" +
							"print(\""+frameName+"_1 complete.\");" +
							"delBat = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".bat\");" + 
							"delSct = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion1"+frameName+timecode+".sct\");" + 
							""
							);

					if (wavelengths==2) {
						String deconString2 = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.1625\", \"res_y double 0.1625\", \"res_z double 1.0\", \"mtxFileDirectory string "+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimAFileDir string "+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"spimBFileDir string "+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed"+ File.separator.replace("\\", "\\\\") + frameName+"\", \"baseImage string "+frameName+"\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconvDirString string "+dir.replace("\\", "\\\\")+"Deconvolution2\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\")";
						IJ.wait(5000);

						new MacroRunner(
								"cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
										"cpuChunks = split(cpuPerformance,\"\\\"\");" +
										"x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +
										"while(x >50) {\n" +
										"	wait(10000);" +
										"	cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");" +
										"	cpuChunks = split(cpuPerformance,\"\\\"\");" +
										"	x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); " +	
										"}" +
										"print(\""+frameName+"_2 processing...\");" +

								"			File.saveString(\'"+deconString2+"\', \""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".sct\");" + 


								"		    f = File.open(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".bat\");\n" + 
								"		    batStringD = \"@echo off\";\n" + 
								"		    print(f,batStringD);\n" + 
								"		    batStringC = \"C\\:\";\n" + 
								"		    print(f,batStringC);\n" + 
								"		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n" + 
								"		    print(f,batStringA);\n" + 
								"		    batStringB = \"start /LOW /b mipav -s \\\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".sct\\\" -hide\";\n" + 
								"		    print(f,batStringB);\n" + 
								"		    File.close(f);	    \n" + 

								"batJob = exec(\"cmd64\", \"/c\", \""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".bat\");" +
								"print(\""+frameName+"_2 complete.\");" +
								"delBat = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".bat\");" + 
								"delSct = File.delete(\""+tempDir.replace("\\", "\\\\")+"GenerateFusion2"+frameName+timecode+".sct\");" + 
								""
								);
					}
				}
				//					IJ.wait(15000);

				impA.setPosition(wasChannelA, wasSliceA, wasFrameA);
				impB.setPosition(wasChannelB, wasSliceB, wasFrameB);

				if (impDF1 == null) {
					impDF1 = new ImagePlus();
					impDF1.setStack("Decon-Fuse-Ch1"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution1"));
					int stkNSlicesDF = impDF1.getStackSize();
					impDF1.setOpenAsHyperStack(true);
					impDF1.setDimensions(1, 296, stkNSlicesDF/(1*296));
					impDF1.show();
				} else {
					impDF1.setStack("Decon-Fuse-Ch1"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution1"));
					int stkNSlicesDF = impDF1.getStackSize();
					impDF1.setOpenAsHyperStack(true);
					impDF1.setDimensions(1, 296, stkNSlicesDF/(1*296));
					impDF1.setWindow(WindowManager.getCurrentWindow());
				}
				if (wavelengths ==2) {
					if (impDF2  == null) {
						impDF2 = new ImagePlus();
						impDF2.setStack("Decon-Fuse-Ch2"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution2"));
						int stkNSlicesDF = impDF2.getStackSize();
						impDF2.setOpenAsHyperStack(true);
						impDF2.setDimensions(1, 296, stkNSlicesDF/(1*296));
						impDF2.show();
					} else {
						impDF2.setStack("Decon-Fuse-Ch2"+impA.getTitle().replace(impA.getTitle().split(":")[0],""),new MultiFileInfoVirtualStack(dir+"Deconvolution2"));
						int stkNSlicesDF = impDF2.getStackSize();
						impDF2.setOpenAsHyperStack(true);
						impDF2.setDimensions(1, 296, stkNSlicesDF/(1*296));
						impDF2.setWindow(WindowManager.getCurrentWindow());
					}

				}
			}		
		}
	}
}
