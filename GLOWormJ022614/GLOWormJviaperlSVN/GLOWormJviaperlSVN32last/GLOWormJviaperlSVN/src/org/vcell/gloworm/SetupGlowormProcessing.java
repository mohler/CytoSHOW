package org.vcell.gloworm;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.Line;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

public class SetupGlowormProcessing implements PlugIn {

	private String framePatternStr;
	private String channelPatternStr;

	public SetupGlowormProcessing() {
		// TODO Auto-generated constructor stub
	}

	public void run(String arg) {
		IJ.run("Colors...", "foreground=white background=black selection=yellow");
		IJ.run("Options...", "iterations=1 black pad edm=Overwrite count=1");
		IJ.run("Memory & Threads...", "parallel=1 keep run");

		ImagePlus imp = IJ.getImage();
		IJ.run(imp, "Properties...", "unit=micron pixel_width=0.1625 pixel_height=0.1625 voxel_depth=1 frame=["+Prefs.get("gloworm.framerate","300 sec")+"] origin=0,0");
		IJ.run("Properties...");
		Prefs.set("gloworm.framerate", ""+imp.getCalibration().frameInterval+" "+imp.getCalibration().getTimeUnit());
		String sourceDir = imp.getOriginalFileInfo().directory;
		IJ.log (sourceDir);

		String[] sourceFileList = new File(sourceDir).list();

		GenericDialog gd = new GenericDialog("Data Info");
		gd.addStringField("Author", Prefs.get("gloworm.authorName","BillMohler"), 25);
		gd.addStringField("Imaging System", Prefs.get("gloworm.imgsys","Blank;Blank2"), 25);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		String authorName = gd.getNextString();
		String imgSys = gd.getNextString();
		String[] imgSysChannels = imgSys.split("[; ,.:|]");
		IJ.log(imgSys+" = "+imgSysChannels.length+" channels");
		Prefs.set("gloworm.authorName", authorName);
		Prefs.set("gloworm.imgsys", imgSys);

		int[] tifCountW = new int[3];
		tifCountW[1]=0;
		tifCountW[2]=0;
		int[] maxTifSizeW = new int[3];
		maxTifSizeW[1] = 0;
		maxTifSizeW[2] = 0;

		String title = imp.getTitle();
		String[] titleW = new String[3];
		//getMinAndMax(UserMin, UserMax);
		double UserMin = imp.getProcessor().getMin();
		double UserMax = imp.getProcessor().getMax();

		String titleRoot = null;
		String titleSuffix = null;
		String titleBase = null;
		String fileDate = null;
		if (title.toLowerCase().matches(".*\\d+.*\\.tif")) {

			String inputStr = title;
			framePatternStr = "_t\\d+[_\\.]";
			channelPatternStr = "_w.*_t\\d+[_\\.]";
			Pattern framePattern = java.util.regex.Pattern.compile(framePatternStr);
			Matcher frameMatcher = framePattern.matcher(inputStr);
			int frameMatchStart = 0;
			int frameMatchEnd = 0;
			if (frameMatcher.find()){
				frameMatchStart = frameMatcher.start();
				frameMatchEnd = frameMatcher.end();
			}
			Pattern channelPattern = java.util.regex.Pattern.compile(channelPatternStr);
			Matcher channelMatcher = channelPattern.matcher(inputStr);
			int channelMatchStart = 0;
			int channelMatchEnd = 0;
			if (channelMatcher.find()){
				channelMatchStart = channelMatcher.start();
				channelMatchEnd = channelMatcher.end();
			}

			//testAutoWA4_w1488nm -15-_t164.TIF
			titleBase = title.substring(0, channelMatchStart+2).replace(" ","").replaceAll("[\\(\\)]","_");  //Necessary because spaces etc in js->ijm args mess things up!
			titleRoot = title.substring(0, frameMatchStart+2).replace(" ","").replaceAll("[\\(\\)]","_");  //Necessary because spaces etc in js->ijm args mess things up!
			titleSuffix = title.substring(frameMatchEnd-1).replace(" ","").replaceAll("[\\(\\)]","_");
			IJ.log(titleRoot+"..."+titleSuffix);
		}
		if (title.matches(".*\\d+\\.pic")) {
			titleRoot = title.replaceAll("(.*)\\d+\\.pic", "$1");
			titleSuffix = ".pic";
		}

		sourceFileList =  new File(sourceDir).list();
		String[][] dir2FileList = new String[3][2];

		for (int f=0;f<sourceFileList.length;f++) {
			File file = new File(sourceDir+sourceFileList[f]);
			IJ.runMacro("print(\"\"+File.getLength(\""+ sourceDir+sourceFileList[f] +"\"));");
			String[] logLines = IJ.getLog().split("\n");
			int fileSize = Integer.parseInt(logLines[logLines.length-1]);
			Pattern framePattern = java.util.regex.Pattern.compile(framePatternStr);
			Matcher frameMatcher = framePattern.matcher(file.getName());
			int frameMatchStart = 0;
			int frameMatchEnd = 0;
			if (frameMatcher.find()){
				frameMatchStart = frameMatcher.start();
				frameMatchEnd = frameMatcher.end();
			}
			Pattern channelPattern = java.util.regex.Pattern.compile(channelPatternStr);
			Matcher channelMatcher = channelPattern.matcher(file.getName());
			int channelMatchStart = 0;
			int channelMatchEnd = 0;
			if (channelMatcher.find()){
				channelMatchStart = channelMatcher.start();
				channelMatchEnd = channelMatcher.end();
			}
			if (file.getName().replace(" ","").replaceAll("[\\(\\)]","_").matches(titleBase+".*"+titleSuffix)
					&& file.getName().contains("_w1"))
				if (fileSize > maxTifSizeW[1]) {
					maxTifSizeW[1] = fileSize;
					titleW[1] = file.getName().substring(channelMatchStart+3, frameMatchStart+2).replace(" ","").replaceAll("[\\(\\)]","_");
				}
			if (file.getName().replace(" ","").replaceAll("[\\(\\)]","_").matches(titleBase+".*"+titleSuffix)
					&& file.getName().contains("_w2"))
				if (fileSize > maxTifSizeW[2]){
					maxTifSizeW[2] = fileSize;
					titleW[2] = file.getName().substring(channelMatchStart+3, frameMatchStart+2).replace(" ","").replaceAll("[\\(\\)]","_");
				}
			GregorianCalendar cal = new GregorianCalendar();
			cal.setTimeInMillis(file.lastModified());
			fileDate = ""+cal.get(Calendar.YEAR)+(cal.get(Calendar.MONTH)>8?"":"0")+(cal.get(Calendar.MONTH)+1)+(cal.get(Calendar.DAY_OF_MONTH)>9?"":"0")+cal.get(Calendar.DAY_OF_MONTH);
			IJ.log(fileDate);
		}
		IJ.log("maxTifSizeW[1]="+maxTifSizeW[1]);
		IJ.log("maxTifSizeW[2]="+maxTifSizeW[2]);

		String[] dir2 = new String[3];
		dir2[1] = (new File(sourceDir)).getParent() + "/" + (new File(sourceDir)).getName() + "Output"+titleBase+"1"+titleW[1]+"/";
		dir2[2] = (new File(sourceDir)).getParent() + "/" + (new File(sourceDir)).getName() + "Output"+titleBase+"2"+titleW[2]+"/";
		//dir2[1] =IJ.getDirectory("Choose an Output Directory ");
		IJ.log(dir2[1]);
//		IJ.log(""+(new File(dir2[1])).mkdirs());
		IJ.log(dir2[2]);
//		IJ.log(""+(new File(dir2[2])).mkdirs());

		for (int f=0;f<sourceFileList.length;f++) {
			IJ.runMacro("print(\"\"+File.getLength(\""+ sourceDir+sourceFileList[f] +"\"));");
			String[] logLines = IJ.getLog().split("\n");
			int thisSize = Integer.parseInt(logLines[logLines.length-1]);
			if (sourceFileList[f].replace(" ","").replaceAll("[\\(\\)]","_").matches(titleRoot+"\\d+"+titleSuffix)
					&& sourceFileList[f].toLowerCase().endsWith(".tif")
					&& thisSize >= maxTifSizeW[1]*0.95) {

				IJ.log("*"+sourceDir+sourceFileList[f]  +" "+ thisSize);
				tifCountW[1]++;
			}
			if (sourceFileList[f].replace(" ","").replaceAll("[\\(\\)]","_").matches(titleRoot+"\\d+"+titleSuffix)
					&& sourceFileList[f].toLowerCase().endsWith(".tif")
					&& thisSize >= maxTifSizeW[2]*0.95) {

				IJ.log("*"+sourceDir+sourceFileList[f]  +" "+ thisSize);
				tifCountW[2]++;
			}
		}
		IJ.log("matching tif count w1 = "+tifCountW[1]);
		IJ.log("matching tif count w2 = "+tifCountW[2]);

		int r=0;
//		IJ.runMacro("File.makeDirectory(\""+dir2[1] + "mov\");");
//		IJ.runMacro("File.makeDirectory(\""+dir2[2] + "mov\");");

		RoiManager rm = imp.getRoiManager();

		String x1s = "";
		String x2s = "";
		String y1s = "";
		String y2s = "";

		Roi[] rois = null;
		
		int lineWidth = 0;
		int imageWidth;
		if (rm !=null) {
			rois = rm.getFullRoisAsArray();
			boolean good = false;
			if ( rois.length%2 == 0) {
				good = true;
				IJ.log(rois.length +" rois in manager");
				for (int i=0;i<rois.length;i=i+2){
					if  (!rois[i].isArea() && rois[i+1].getType() != Roi.LINE) {
						good = false;
					} else {
						//unc14PHGFPa_030614_SDCMEmbryo4_slc_aspUK_auBillMohler_date030614_imgsysUCHCSDCM40X30pcStdby100ms_x_y_z36_t182_nmdxy0167_nmdz1000_msdt150000_minIn191_maxIn363
//						IJ.runMacro("File.makeDirectory(\""+dir2[1] + "mov\" + File.separator() + \""
//								+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//								+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//										+ "\");");
//						if (true) {
//							IJ.runMacro("File.makeDirectory(\""+dir2[1] + "mov\" + File.separator() + \""
//									+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//									+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//											+ "\");");
//						}
//						if (true) {
//							IJ.runMacro("File.makeDirectory(\""+dir2[1] + "mov\" + File.separator() + \""
//									+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//									+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//											+ "\");");
//						}
//						if (true) {
//							IJ.runMacro("File.makeDirectory(\""+dir2[2] + "mov\" + File.separator() + \""
//									+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//									+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//											+ "\");");
//						}
						rm.select(i+1);
						Roi roi = imp.getRoi();

						int x1 = ((Line)roi).x1;
						int x2= ((Line)roi).x2;
						int y1= ((Line)roi).y1;
						int y2= ((Line)roi).y2;

						if (x1s!="")
							x1s = x1s + "_";
						x1s = x1s + x1;
						if (x2s!="")
							x2s = x2s + "_";
						x2s = x2s + x2;
						if (y1s!="")
							y1s = y1s + "_";
						y1s = y1s + y1;
						if (y2s!="")
							y2s = y2s + "_";
						y2s = y2s + y2;

						IJ.log(roi.toString() );

						lineWidth = ((Line)roi).getWidth();

						imageWidth = imp.getWidth();

						IJ.log (""+imageWidth);

					}
				}
				if (good) {
					rm.select(-1);
					rm.runCommand("Save", sourceDir + titleBase + "_ROIset.zip");
				} else IJ.error("You need two selections for each Embryo to run GLOWorm, one Area followed by one Line");

			} else IJ.error("You need two selections for each Embryo to run GLOWorm, one Area followed by one Line");

		}

		if (tifCountW[1]!=tifCountW[2]){
			return;
		}

		IJ.saveString(" /UCHC/HPC/jdk7/jdk1.7.0_07/bin/java -Xmx1500m -jar /UCHC/HPC/ImageJ/ij.jar -ijpath /UCHC/HPC/ImageJ/ -port0 -macro /UCHC/HPC/wmohler_HPC/GLOWormChainMacroMMMDAStacksMultiSelectAutoExtend_051414.ijm "
				+ x1s + "," + y1s + "," + x2s + "," + y2s + "," + lineWidth
				+ "," + sourceDir + "," + tifCountW[1] + "," + titleBase + ",1" + titleW[1] + "," + titleSuffix
				+ ","+ "cycleIndex" + "," + dir2[1] + "," + UserMin + ","+ UserMax + ","+ maxTifSizeW[1]
				+ ","+ imp.getCalibration().pixelHeight + ","+ imp.getCalibration().pixelDepth + ","+ imp.getCalibration().frameInterval + ","+ imp.getNSlices()
				+ "," + authorName.replace(" ","").replaceAll("[\\(\\)]","_") + "," + imgSysChannels[0].replace(" ","").replaceAll("[\\(\\)]","_")  + "," + fileDate +"\n"
				
				+" /UCHC/HPC/jdk7/jdk1.7.0_07/bin/java -Xmx1500m -jar /UCHC/HPC/ImageJ/ij.jar -ijpath /UCHC/HPC/ImageJ/ -port0 -macro /UCHC/HPC/wmohler_HPC/GLOWormChainMacroMMMDAStacksMultiSelectAutoExtend_051414.ijm "
				+ x1s + "," + y1s + "," + x2s + "," + y2s + "," + lineWidth
				+ "," + sourceDir + "," + tifCountW[2] + "," + titleBase + ",2" + titleW[2] + "," + titleSuffix
				+ ","+ "cycleIndex" + "," + dir2[2] + "," + 200 + ","+ 450 + ","+ maxTifSizeW[2]
				+ ","+ imp.getCalibration().pixelHeight + ","+ imp.getCalibration().pixelDepth + ","+ imp.getCalibration().frameInterval + ","+ "1"
				+ "," + authorName.replace(" ","").replaceAll("[\\(\\)]","_") + "," + imgSysChannels[1].replace(" ","").replaceAll("[\\(\\)]","_")  + "," + fileDate +"", 
				
				""+ sourceDir +"GLOWormSetupExecArgs.txt");

		
		
//		int tifCountPrevious = 0;
//		boolean good;
//		String sceneString;
//		Process uploadProcess = null;
//		Process uploadProcess2 = null;
//		while (tifCountW[1] > tifCountPrevious) {
//			if (tifCountPrevious > 0) {
//				IJ.runMacro("File.delete(\"" + dir2[1]+"QTVRsAllDone.txt" + "\");");
//				IJ.runMacro("File.delete(\"" + dir2[2]+"QTVRsAllDone.txt" + "\");");
//
//			}
//
//			dir2FileList[1] = null;
//			while (dir2FileList[1] == null) {
//				dir2FileList[1] =  new File(dir2[1] + "mov" + File.separator + titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0]
//						+ "_Embryo"+rm.getCount()/2 + "_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]).list();
//			}
//			int movCount = 0;
//			for (int x=0; x<dir2FileList[1].length; x++) {
//				movCount = movCount +(dir2FileList[1][x].endsWith(".mov")?1:0);
//			}
//
//			Process[] processes = null;
//
//			IJ.log(movCount+"/"+tifCountW[1]+ " processing");
//			for (int cycleIndex = 0; cycleIndex <20; cycleIndex = cycleIndex+2) {
//				IJ.log(""+imp.getCalibration().pixelHeight + ','+ imp.getCalibration().pixelDepth);
//				try {
//					processes[cycleIndex] = Runtime.getRuntime().exec( " /UCHC/HPC/jdk7/jdk1.7.0_07/bin/java -Xmx1500m -jar /UCHC/HPC/ImageJ/ij.jar -ijpath /UCHC/HPC/ImageJ/ -port0 -macro /UCHC/HPC/wmohler_HPC/GLOWormChainMacroMMMDAStacksMultiSelectAutoExtend_051414.ijm "
//							+ x1s + "," + y1s + "," + x2s + "," + y2s + "," + lineWidth
//							+ "," + sourceDir + "," + tifCountW[1] + "," + titleBase + ",1" + titleW[1] + "," + titleSuffix
//							+ ","+ cycleIndex + "," + dir2[1] + "," + UserMin + ","+ UserMax + ","+ maxTifSizeW[1]
//									+ ","+ imp.getCalibration().pixelHeight + ","+ imp.getCalibration().pixelDepth
//									+ "," + authorName.replace(" ","").replaceAll("[\\(\\)]","_") + "," + imgSysChannels[0].replace(" ","").replaceAll("[\\(\\)]","_")  + "," + fileDate +"");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}  
//				try {
//					processes[cycleIndex+1] = Runtime.getRuntime().exec( " /UCHC/HPC/jdk7/jdk1.7.0_07/bin/java -Xmx1500m -jar /UCHC/HPC/ImageJ/ij.jar -ijpath /UCHC/HPC/ImageJ/ -port0 -macro /UCHC/HPC/wmohler_HPC/GLOWormChainMacroMMMDAStacksMultiSelectAutoExtend_051414.ijm "
//							+ x1s + "," + y1s + "," + x2s + "," + y2s + "," + lineWidth
//							+ "," + sourceDir + "," + tifCountW[2] + "," + titleBase + ",2" + titleW[2] + "," + titleSuffix
//							+ ","+ cycleIndex + "," + dir2[2] + "," + UserMin + ","+ UserMax + ","+ maxTifSizeW[2]
//									+ ","+ imp.getCalibration().pixelHeight + ","+ imp.getCalibration().pixelDepth
//									+ "," + authorName.replace(" ","").replaceAll("[\\(\\)]","_") + "," + imgSysChannels[1].replace(" ","").replaceAll("[\\(\\)]","_")  + "," + fileDate +"");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}  
//				IJ.wait(2000);
//			}
//
//			for (int cycleIndex = 0; cycleIndex <20; cycleIndex ++) {
//				try {
//					processes[cycleIndex].waitFor();
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
//
//
//			String slcGeneric;
//			String slcSpecific;
//			String slcGeneric2;
//			String slcSpecific2;
//			String prxGeneric;
//			String prxSpecific;
//			String pryGeneric;
//			String prySpecific;
//			for (int i=0;i<rois.length;i=i+2){
//				if  (!rois[i].isArea() && rois[i+1].getType() != Roi.LINE) {
//					good = false;
//				} else {
//					//unc14PHGFPa_030614_SDCMEmbryo4_slc_aspUK_auBillMohler_date030614_imgsysUCHCSDCM40X30pcStdby100ms_x_y_z36_t182_nmdxy0167_nmdz1000_msdt150000_minIn191_maxIn363
//
//
//					slcGeneric  = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0];
//					slcSpecific = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					slcGeneric2  = dir2[2] + "mov\" + File.separator() + \""
//							+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1];
//					slcSpecific2 = dir2[2] + "mov\" + File.separator() + \""
//							+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					prxGeneric  = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0];
//					prxSpecific = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					pryGeneric  = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0];
//					prySpecific = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					IJ.runMacro("File.rename(\""+ slcGeneric
//							+ "\" , \""+slcSpecific
//							+ "\");");
//					IJ.runMacro("File.rename(\""+ slcGeneric2
//							+ "\" , \""+slcSpecific2
//							+ "\");");
//					IJ.runMacro("File.rename(\""+prxGeneric
//							+ "\" , \""+prxSpecific
//							+ "\");");
//					IJ.runMacro("File.rename(\""+pryGeneric
//							+ "\" , \""+prySpecific
//							+ "\");");
//
//					sceneString = "Saved Scene for movies: \n"
//							+"genericFilePath = zSlices = FireLUT15-50_GenericAdjustment.adj\n"
//							+"genericFilePath2 = zSlices2 = GraysLUT1-255GenericAdjustment.adj\n"
//							+"\n"
//							+"Convert8bit = true\n"
//							+"VirtualStack = true\n"
//							+"MultipleMovies = true\n"
//							+"HyperStack = true\n"
//							+"StretchToFit = false\n"
//							+"ViewInOverlay = false\n"
//							+"HorizontalMontage = true\n"
//							+"SideSideStereo = false\n"
//							+"RedCyanStereo = false\n"
//							+"GridLayOut = false\n"
//							+"DisplayMode = 1 = composite\n"
//							+"Cposition = 1\n"
//							+"Zposition = 1\n"
//							+"Tposition = 1\n"
//							+"ZsustainROIs = 1\n"
//							+"TsustainROIs = 1\n"
//							+"AcquisitionComplete = false\n"
//							+"AcquisitionInterval = "+imp.getCalibration().frameInterval*1000+"\n"
//							+"End of parameter list";
//					IJ.saveString(sceneString.replace("genericFilePath", "/Volumes/GLOWORM_DATA/"
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//									.replace("genericFilePath2", "/Volumes/GLOWORM_DATA/"
//											+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//											+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//													+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//													+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//													+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//													.replace("zSlices",""+imp.getNSlices()).replace("zSlices2","1"), dir2[1]  
//															+ titleRoot.replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//															+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//																	+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//																	+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//																	+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SRscene.scn");
//					IJ.saveString(sceneString.replace("genericFilePath", "/Volumes/GLOWORM_DATA/"
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//									.replace("genericFilePath2", "/Volumes/GLOWORM_DATA/"
//											+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//											+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//													+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//													+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//													+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//													.replace("zSlices","36").replace("zSlices2","1"), dir2[1]  
//															+ titleRoot.replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//															+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//																	+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//																	+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//																	+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SRscene.scn");
//					IJ.saveString(sceneString.replace("genericFilePath", "/Volumes/GLOWORM_DATA/"
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//									.replace("genericFilePath2", "/Volumes/GLOWORM_DATA/"
//											+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//											+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//													+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//													+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//													+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//													.replace("zSlices","36").replace("zSlices2","1"), dir2[1]  
//															+ titleRoot.replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//															+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//																	+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//																	+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//																	+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SRscene.scn");
//
//				}
//			}
//
//
//			//IJ.runMacro("waitForUser;");
//			IJ.runMacro("call( \"ccam.worm.qt.QTVRUtil.main\", \""+dir2[1]+ ",liveUpdate\" );");
//			IJ.runMacro("call( \"ccam.worm.qt.QTVRUtil.main\", \""+dir2[2]+ ",liveUpdate\" );");
//
//
//
//			while(!(new File(dir2[2]+"QTVRsAllDone.txt").exists())) {
//				IJ.wait(1000);
//			}
//			IJ.log("not dead yet");
//			IJ.log ("start upload");
//			try {
//				uploadProcess = Runtime.getRuntime().exec( "/UCHC/HPC/wmohler_HPC/rsyncToGLOWorm.txt", null, (new File(dir2[1])));
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			try {
//				uploadProcess2 = Runtime.getRuntime().exec( "/UCHC/HPC/wmohler_HPC/rsyncToGLOWorm.txt", null, (new File(dir2[2])));
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			IJ.log ("uploading...");
//			try {
//				uploadProcess.waitFor();
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			try {
//				uploadProcess2.waitFor();
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//
//			IJ.log("upload complete");
//
//			IJ.wait(60000);
//
//			tifCountPrevious = tifCountW[1];
//			tifCountW[1] = 0;
//			sourceFileList =  new File(sourceDir).list();
//
//			for (int f=0;f<sourceFileList.length;f++) {
//				IJ.runMacro("print(\"\"+File.getLength(\""+ sourceDir+sourceFileList[f] +"\"));");
//				String[] logLines = IJ.getLog().split("\n");
//				int thisSize = Integer.parseInt(logLines[logLines.length-1]);
//				if (sourceFileList[f].toLowerCase().endsWith(".tif") && thisSize >= maxTifSizeW[1]*0.95
//						&& sourceFileList[f].replace(" ","").replaceAll("[\\(\\)]","_").matches(titleRoot+"\\d+"+titleSuffix)) {
//
//					IJ.log("*"+sourceDir+sourceFileList[f]  +" "+ thisSize);
//					tifCountW[1]++;
//				}
//			}
//			IJ.log("matching tif count = "+tifCountW[1]);
//
//			for (int i=0;i<rois.length;i=i+2){
//				if  (!rois[i].isArea() && rois[i+1].getType() != Roi.LINE) {
//					good = false;
//				} else {
//					//unc14PHGFPa_030614_SDCMEmbryo4_slc_aspUK_auBillMohler_date030614_imgsysUCHCSDCM40X30pcStdby100ms_x_y_z36_t182_nmdxy0167_nmdz1000_msdt150000_minIn191_maxIn363
//
//
//					slcGeneric  = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0];
//					slcSpecific = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					slcGeneric2  = dir2[2] + "mov\" + File.separator() + \""
//							+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1];
//					slcSpecific2 = dir2[2] + "mov\" + File.separator() + \""
//							+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					prxGeneric  = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0];
//					prxSpecific = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					pryGeneric  = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0];
//					prySpecific = dir2[1] + "mov\" + File.separator() + \""
//							+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//							+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//									+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//									+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//									+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_";
//
//					IJ.runMacro("File.rename(\""+ slcSpecific
//							+ "\" , \""+slcGeneric
//							+ "\");");
//					IJ.runMacro("File.rename(\""+ slcSpecific2
//							+ "\" , \""+slcGeneric2
//							+ "\");");
//					IJ.runMacro("File.rename(\""+prxSpecific
//							+ "\" , \""+prxGeneric
//							+ "\");");
//					IJ.runMacro("File.rename(\""+prySpecific
//							+ "\" , \""+pryGeneric
//							+ "\");");
//				}
//			}
//		}
//
//
//		for (int i=0;i<rois.length;i=i+2){
//			if  (!rois[i].isArea() && rois[i+1].getType() != Roi.LINE) {
//				good = false;
//			} else {
//
//				String slcProcessing = dir2[1] 
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//								+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//				String slcFinal = dir2[1] 
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//								+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//
//				String slcProcessing2 = dir2[2] 
//						+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//								+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//				String slcFinal2 = dir2[2] 
//						+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//								+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//
//				String prxProcessing = dir2[1] 
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//								+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//				String prxFinal = dir2[1] 
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//								+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//
//				String pryProcessing = dir2[1]
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//								+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//				String pryFinal = dir2[1] 
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//								+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax + "_SR.mov";
//
//				IJ.runMacro("File.rename(\""+ slcProcessing
//						+ "\" , \""+slcFinal
//						+ "\");");
//				IJ.runMacro("File.rename(\""+ slcProcessing2
//						+ "\" , \""+slcFinal2
//						+ "\");");
//				IJ.runMacro("File.rename(\""+prxProcessing
//						+ "\" , \""+prxFinal
//						+ "\");");
//				IJ.runMacro("File.rename(\""+pryProcessing
//						+ "\" , \""+pryFinal
//						+ "\");");
//
//				sceneString = "Saved Scene for movies: \n"
//						+"genericFilePath = zSlices = FireLUT15-50_GenericAdjustment.adj\n"
//						+"genericFilePath2 = zSlices2 = GraysLUT1-255GenericAdjustment.adj\n"
//						+"\n"
//						+"Convert8bit = true\n"
//						+"VirtualStack = true\n"
//						+"MultipleMovies = true\n"
//						+"HyperStack = true\n"
//						+"StretchToFit = false\n"
//						+"ViewInOverlay = false\n"
//						+"HorizontalMontage = true\n"
//						+"SideSideStereo = false\n"
//						+"RedCyanStereo = false\n"
//						+"GridLayOut = false\n"
//						+"DisplayMode = 1 = composite\n"
//						+"Cposition = 1\n"
//						+"Zposition = 1\n"
//						+"Tposition = 1\n"
//						+"ZsustainROIs = 1\n"
//						+"TsustainROIs = 1\n"
//						+"AcquisitionComplete = true\n"
//						+"End of parameter list";
//				IJ.saveString(sceneString.replace("genericFilePath", "/Volumes/GLOWORM_DATA/"
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//								+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//								.replace("genericFilePath2", "/Volumes/GLOWORM_DATA/"
//										+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//										+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//												+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//												+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//												+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//												.replace("zSlices",""+imp.getNSlices()).replace("zSlices2","1"), dir2[1]  
//														+ titleRoot.replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//														+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//																+ "_x" +"0"+ "_y" +"0"+ "_z" +imp.getNSlices()
//																+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//																+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SRscene.scn");
//				IJ.saveString(sceneString.replace("genericFilePath", "/Volumes/GLOWORM_DATA/"
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//								+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//								.replace("genericFilePath2", "/Volumes/GLOWORM_DATA/"
//										+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//										+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//												+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//												+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//												+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//												.replace("zSlices","36").replace("zSlices2","1"), dir2[1]  
//														+ titleRoot.replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//														+"_prx_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//																+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//																+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//																+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SRscene.scn");
//				IJ.saveString(sceneString.replace("genericFilePath", "/Volumes/GLOWORM_DATA/"
//						+ titleBase.concat("1"+titleW[1]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//						+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//								+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//								+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//								+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//								.replace("genericFilePath2", "/Volumes/GLOWORM_DATA/"
//										+ titleBase.concat("2"+titleW[2]).replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//										+"_slc_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[1]
//												+ "_x" +"0"+ "_y" +"0"+ "_z" +"1"
//												+ "_t" +tifCountW[1]+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//												+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SR.mov")
//												.replace("zSlices","36").replace("zSlices2","1"), dir2[1]  
//														+ titleRoot.replace(" ","").replaceAll("[\\(\\)]","_").split("_")[0] + "_Embryo" + (i +2)/2
//														+"_pry_au"+authorName+ "_date"+fileDate+ "_imgsys" +imgSysChannels[0]
//																+ "_x" +"0"+ "_y" +"0"+ "_z" +"36"
//																+ "_t" +"000"+ "_nmdxy" +imp.getCalibration().pixelHeight*1000+ "_nmdz" +imp.getCalibration().pixelDepth*1000
//																+ "_msdt" +imp.getCalibration().frameInterval*1000+ "_minIn" +UserMin+ "_maxIn" +UserMax+ "_SRscene.scn");
//
//
//			}
//		}
//
//		IJ.log("not dead yet");
//		IJ.log ("start final upload");
//		try {
//			uploadProcess = Runtime.getRuntime().exec( "/UCHC/HPC/wmohler_HPC/rsyncToGLOWorm.txt", null, (new File(dir2[1])));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		try {
//			uploadProcess2 = Runtime.getRuntime().exec( "/UCHC/HPC/wmohler_HPC/rsyncToGLOWorm.txt", null, (new File(dir2[2])));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		IJ.log ("uploading...");
//		try {
//			uploadProcess.waitFor();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		try {
//			uploadProcess2.waitFor();
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		IJ.log("final upload complete");
//
//		//cleanUpProcess = Runtime.getRuntime().exec( '/UCHC/HPC/wmohler_HPC/cleanUpGLOWorm.txt', null, (new File(dir2[1])));
//		//cleanUpProcess.waitFor();


	}

}
