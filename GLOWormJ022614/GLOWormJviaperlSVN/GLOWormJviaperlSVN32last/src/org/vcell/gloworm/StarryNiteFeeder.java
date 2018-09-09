package org.vcell.gloworm;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Polygon;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.VirtualStack;
import ij.WindowManager;
import ij.gui.EllipseRoi;
import ij.gui.Line;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.gui.WaitForUserDialog;
import ij.io.DirectoryChooser;
import ij.io.OpenDialog;
import ij.io.RoiDecoder;
import ij.plugin.Colors;
import ij.plugin.PlugIn;
import ij.plugin.RoiRotator;
import ij.plugin.frame.ColorLegend;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij.process.StackStatistics;

public class StarryNiteFeeder implements PlugIn {

	public void run(String arg) {
		Boolean autoLaunch=false;
		String[] argChunks = arg.split("\\|");
		String sourceDir = "";
		String[] sourceFileList = new String[0];
		String paramsPath = "";
		String outputDir = "";
		int skipFactor = 6;
		if (argChunks.length == 4) {
			autoLaunch = true;
			sourceDir = argChunks[0];
			paramsPath = argChunks[1];
			outputDir = argChunks[2];
			skipFactor = Integer.parseInt(argChunks[3]);
		}
		if (sourceDir != ""){
			sourceFileList = (new File(sourceDir).list());
		}
		if (paramsPath == "") {
			OpenDialog.setDefaultDirectory(Prefs.get("StarryNiteFeeder.parameterFileDirectory",""));
			OpenDialog.setLastName(Prefs.get("StarryNiteFeeder.parameterFileName",""));
			paramsPath = new OpenDialog("Parameter file for StarryNite?", OpenDialog.getDefaultDirectory(), OpenDialog.getLastName()).getPath();
			//		String[] baseParamChunks = baseParameterFilePath.split(">>");
			//		String timeRangePrompt="";
			//		if (baseParamChunks.length >0) {
			//			timeRangePrompt=baseParamChunks[1];
			//		}
			Prefs.set("StarryNiteFeeder.parameterFileDirectory", new File(paramsPath).getParent()+File.separator);
			Prefs.set("StarryNiteFeeder.parameterFileName", new File(paramsPath).getName());
		}
		final String baseParameterFilePath = paramsPath;
		if (outputDir == "") {
			DirectoryChooser.setDefaultDirectory(Prefs.get("StarryNiteFeeder.outputPath",""));
			outputDir = IJ.getDirectory("Output directory for StarryNite?");
			Prefs.set("StarryNiteFeeder.outputPath", outputDir);
		} 
		final String outDir = outputDir+ File.separator ;
		
		if (!autoLaunch){
			WaitForUserDialog wfud = new WaitForUserDialog("StarryNite Feeder", 
					"Please position ROIs around the individual embryos to be lineaged.  \n\nThen adjust each embryo's t-slider to the last timepoint to be analyzed.  \n\nClick OK to launch analysis.");
			wfud.show();
		}
		
		for (int w=1; w<=WindowManager.getImageCount(); w++){
			ImagePlus imp = WindowManager.getImage(w);	
			LUT[] impLUTs = null;
			int greenMax = 0;
			int redMax = 0;
			if (imp.isComposite()) {
				impLUTs = (((CompositeImage)imp).getLuts());
				greenMax = (int) impLUTs[0].max;
				redMax = (int) impLUTs[1].max;
			}
			imp.killRoi();
			
			if (imp.getRoi() == null ) {
				for (String sourceDirFileName:sourceFileList){
					if (sourceDirFileName.endsWith("originalSNF.roi")){
						if (sourceDirFileName.startsWith(imp.getTitle())){
							WindowManager.setTempCurrentImage(imp);
							IJ.open(outDir+ sourceDirFileName);
							WindowManager.setTempCurrentImage(null);
						}
					}
				}				
			}
			if (imp.getRoi() == null ) {
				continue;
			}
			
			Roi theROI = imp.getRoi();
			int type = imp.getRoi().getType() ;
			boolean flipStack = false;

			if (theROI == null) {

			} else {

				String title = imp.getTitle();
				String savetitle = title.replace(":","_").replace(" ","").replace("_dummy","");
				String savePath = outDir+savetitle;

				IJ.saveAs(imp, "Selection", savePath+"originalSNF.roi");
				int[] xpoints = imp.getRoi().getPolygon().xpoints;
				int[] ypoints = imp.getRoi().getPolygon().ypoints;
				int npoints = xpoints.length;

				double angle =0;
				if (type > Roi.OVAL) {
					if (npoints == 4) {
						double angleZero = new Line(xpoints[0], ypoints[0], xpoints[1], ypoints[1]).getAngle();
						double angleTwo = new Line(xpoints[2], ypoints[2], xpoints[3], ypoints[3]).getAngle();
						
						double angleZeroPlusPi = angleZero + 180;
						double angleTwoPlusPi = angleTwo + 180;

						double angleDelta = angleZeroPlusPi%180 - angleTwoPlusPi%180;

						if ( Math.abs(angleDelta)  >80 && Math.abs(angleDelta)  <100) {
							angle = angleZero;
							flipStack = (angleZeroPlusPi - angleTwoPlusPi < 0 || angleZeroPlusPi - angleTwoPlusPi > 180);
							IJ.log("flipStack = " + flipStack + ":angle0PlusPi - angle2PlusPi = " + angleZeroPlusPi +" - "+ angleTwoPlusPi + " = " + (angleDelta));

							Roi ellipseRoi = new EllipseRoi(xpoints[0], ypoints[0], xpoints[1], ypoints[1], 
									(new Line(xpoints[2], ypoints[2], xpoints[3], ypoints[3])).getLength()
									/
									(new Line(xpoints[0], ypoints[0], xpoints[1], ypoints[1])).getLength()
									);
							imp.setRoi(ellipseRoi, false);
							theROI = ellipseRoi;
							IJ.saveAs(imp, "Selection", savePath +  "ellipseSNF.roi");

							Roi rectRoi = new Roi(ellipseRoi.getBounds());
							imp.setRoi(rectRoi, false);
							IJ.saveAs(imp, "Selection", savePath + "rectangleSNF.roi");

						}else {
							angle = new Line(xpoints[0], ypoints[0], xpoints[2], ypoints[2]).getAngle();
						}
					} else {
						angle = new Line(xpoints[0], ypoints[0], xpoints[npoints/2], ypoints[npoints/2]).getAngle();
					}
				}

//				angle = 180+ angle;

				int wasC = imp.getChannel();
				int wasZ = imp.getSlice();
				int wasT = imp.getFrame();

				int wavelengths = imp.getNChannels();

				Roi theRotatedROI = RoiRotator.rotate(theROI, angle);
				final String subdir = savetitle;
				new File(outDir+subdir).mkdirs();
				final String impParameterPath = outDir+subdir+File.separator+savetitle+"_SNparamsFile.txt";
				final int endPoint = imp.getFrame();
				int stackWidth=0;
				int stackHeight=0;

				for (int f = 1; f <= endPoint; f++) {
					boolean paramsWritten = false;
					if (!((new File(outDir+subdir+"/aaa_t"+f+".tif").canRead())&&(new File(outDir+subdir+"Skipped"+"/aaa_t"+f+".tif").canRead()))) {

						ImageStack stack1 = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
						ImageStack stack1skipped = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());

						ImageStack stack2 = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
						ImageStack stack2skipped = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());

						ImageStack stack3 = new ImageStack((int)theRotatedROI.getBounds().getWidth()*2, (int)theRotatedROI.getBounds().getHeight());
						ImageStack stack3skipped = new ImageStack((int)theRotatedROI.getBounds().getWidth()*2, (int)theRotatedROI.getBounds().getHeight());

						imp.getWindow().setEnabled(false);


						for (int i = 1; i <= imp.getNSlices(); i++) {
							imp.setPositionWithoutUpdate(1, i, f);

							ImageProcessor ip1 = imp.getProcessor().duplicate();

							int[] ipHis = ip1.getHistogram();
							double ipHisMode = 0.0;
							int ipHisLength = ipHis.length;
							int ipHisMaxBin = 0;
							for (int h=0; h<ipHisLength; h++) {
								if (ipHis[h] > ipHisMaxBin) {
									ipHisMaxBin = ipHis[h];
									ipHisMode = (double)h;
								}
							}
							ip1.subtract(ipHisMode * 1);

							ip1.setRoi((Roi) theROI);
							ip1.fillOutside((Roi) theROI);
							ip1 = ip1.crop();
							ImageProcessor ip1r = ip1.createProcessor((int)Math.sqrt(ip1.getWidth()*ip1.getWidth()+ip1.getHeight()*ip1.getHeight())
									, (int)Math.sqrt(ip1.getWidth()*ip1.getWidth()+ip1.getHeight()*ip1.getHeight()));
							ip1r.insert(ip1, (ip1r.getWidth()-ip1.getWidth())/2, (ip1r.getHeight()-ip1.getHeight())/2);
							ip1= ip1r;
							ip1.rotate(angle);
							ip1.setRoi((int)(ip1.getWidth()-theRotatedROI.getBounds().getWidth())/2, (int)(ip1.getHeight()-theRotatedROI.getBounds().getHeight())/2
									, (int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
							ip1 = ip1.crop();
							
							ImageProcessor ip3 = ip1.createProcessor(stack3.getWidth(), stack3.getHeight());
		
							if (!flipStack){
								stack1.addSlice(ip1);
								if (i%skipFactor == 1) {
									stack1skipped.addSlice(ip1.duplicate());
								}
								ImageProcessor ip1fh = ip1.duplicate();
								ip1fh.flipHorizontal();
								ip3.insert(ip1fh, ip3.getWidth()/2, 0);

							} else {
								ip1.flipVertical();
								stack1.addSlice(null, ip1, 0);
								if (i%skipFactor == 1) {
									stack1skipped.addSlice(null, ip1.duplicate(),0);
								}
								ImageProcessor ip1fh = ip1.duplicate();
								ip1fh.flipHorizontal();

								ip3.insert(ip1fh, ip3.getWidth()/2, 0);

							}
							
							
							if (wavelengths >= 2) {
								imp.setPositionWithoutUpdate(wavelengths, i, f);
								ImageProcessor ip2 = imp.getProcessor().duplicate();
								ipHis = ip2.getHistogram();
								ipHisMode = 0.0;
								ipHisLength = ipHis.length;
								ipHisMaxBin = 0;
								for (int h=0; h<ipHisLength; h++) {
									if (ipHis[h] > ipHisMaxBin) {
										ipHisMaxBin = ipHis[h];
										ipHisMode = (double)h;
									}
								}

								ip2.subtract(ipHisMode * 1);

								ip2.setRoi((Roi) theROI);
								ip2.fillOutside((Roi) theROI);
								ip2 = ip2.crop();
								ImageProcessor ip2r = ip2.createProcessor((int)Math.sqrt(ip2.getWidth()*ip2.getWidth()+ip2.getHeight()*ip2.getHeight())
										, (int)Math.sqrt(ip2.getWidth()*ip2.getWidth()+ip2.getHeight()*ip2.getHeight()));
								ip2r.insert(ip2, (ip2r.getWidth()-ip2.getWidth())/2, (ip2r.getHeight()-ip2.getHeight())/2);
								ip2= ip2r;
								ip2.rotate(angle);
								ip2.setRoi((int)(ip2.getWidth()-theRotatedROI.getBounds().getWidth())/2, (int)(ip2.getHeight()-theRotatedROI.getBounds().getHeight())/2
										, (int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
								ip2 = ip2.crop();

								if (!flipStack){
									stack2.addSlice(ip2);
									if (i%skipFactor == 1) {
										stack2skipped.addSlice(ip2.duplicate());
									}
									
									ImageProcessor ip2fh = ip2.duplicate();
									ip2fh.flipHorizontal();
									ip3.insert(ip2fh, 0, 0);

									stack3.addSlice(ip3);
									if (i%skipFactor == 1) {
										stack3skipped.addSlice(ip3.duplicate());
									}

								} else {
									ip2.flipVertical();
									stack2.addSlice(null, ip2, 0);
									if (i%skipFactor == 1) {
										stack2skipped.addSlice(null, ip2.duplicate(),0);
									}
									
									ImageProcessor ip2fh = ip2.duplicate();
									ip2fh.flipHorizontal();
									ip3.insert(ip2fh, 0, 0);

									stack3.addSlice(null, ip3, 0);
									if (i%skipFactor == 1) {
										stack3skipped.addSlice(null, ip3.duplicate(), 0);
									}

								}
								

							}
						}


						imp.getWindow().setEnabled(true);

						ImagePlus frameRedImp = new ImagePlus("Ch2hisSubCrop",stack2);
						ImagePlus frameRedImpSkipped = new ImagePlus("Ch2hisSubCrop",stack2skipped);

						stackWidth = frameRedImp.getWidth();
						stackHeight = frameRedImp.getHeight();
						if (!paramsWritten) {
							IJ.saveString(IJ.openAsString(baseParameterFilePath).replaceAll("(.*end_time=)\\d+(;.*)", "$1"+f+"$2")
									.replaceAll("(.*ROI=)true(;.*)", "$1false$2")
									.replaceAll("(.*ROI.min=)\\d+(;.*)", "$10$2")
									.replaceAll("(.*ROIxmax=)\\d+(;.*)", "$1"+stackWidth+"$2")
									.replaceAll("(.*ROIymax=)\\d+(;.*)", "$1"+stackHeight+"$2")
									.replaceAll("(.*)ROIpoints=\\[\\d+.*\\];(.*)", "$1"+""+"$2")
									, impParameterPath);
							paramsWritten = true;
						}

						ImagePlus frameGreenImp = new ImagePlus("Ch1hisSubCrop",stack1);
						ImagePlus frameGreenImpSkipped = new ImagePlus("Ch1hisSubCrop",stack1skipped);

						ImagePlus frameRGsplitImp = new ImagePlus("Ch12hisSubCrop",stack3);
						ImagePlus frameRGsplitImpSkipped = new ImagePlus("Ch12hisSubCrop",stack3skipped);

						
						// Red channel:

						new File(outDir+subdir).mkdirs();
						new File(outDir+subdir+"Skipped").mkdirs();

						frameRedImp.flush();
						frameRedImpSkipped.flush();
						frameGreenImp.flush();
						frameGreenImpSkipped.flush();
						frameRGsplitImp.flush();
						frameRGsplitImpSkipped.flush();

					}
				}
				imp.setPosition(wasC, wasZ, wasT);
				imp.setRoi(theROI);

				IJ.saveString(IJ.openAsString(baseParameterFilePath).replaceAll("(.*end_time=)\\d+(;.*)", "$1"+endPoint+"$2")
						.replaceAll("(.*ROI=)true(;.*)", "$1false$2")
						.replaceAll("(.*ROI.min=)\\d+(;.*)", "$10$2")
						.replaceAll("(.*ROIxmax=)\\d+(;.*)", "$1"+stackWidth+"$2")
						.replaceAll("(.*ROIymax=)\\d+(;.*)", "$1"+stackHeight+"$2")
						.replaceAll("(.*)ROIpoints=\\[\\d+.*\\];(.*)", "$1"+""+"$2")
						, impParameterPath);

				Thread linThread = new Thread(new Runnable() {
					public void run() {
						if(new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml").canRead()) {
							new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml").renameTo(
									new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_"+
											(""+new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml").lastModified()).substring(0, 10)+"_edited.xml"));
						}

						try {
							IJ.log("C:\\SNFeeder_compiled\\user\\detect_track_driver_allmatlab.exe "+impParameterPath.replace("\\\\", "\\").replace("\\", "\\\\") +(outDir+subdir+"Skipped").replace("\\\\", "\\").replace("\\", "\\\\")+"\\\\ aaa emb "+(outDir+subdir+"Skipped").replace("\\\\", "\\").replace("\\", "\\\\")+"\\\\ 0 true");

							Runtime.getRuntime().exec(new String[]{"cmd","/c","start","/min","/wait","C:\\SNFeeder_compiled\\user\\detect_track_driver_allmatlab.exe", impParameterPath.replace("\\\\", "\\").replace("\\", "\\\\"), (outDir+subdir+"Skipped").replace("\\\\", "\\").replace("\\", "\\\\")+"\\\\", "aaa", "emb", (outDir+subdir+"Skipped").replace("\\\\", "\\").replace("\\", "\\\\")+"\\\\", "0", "true"});
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						while (!(new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml")).canRead()) {
							IJ.wait(1000);
							IJ.log("awaiting "+(outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml");
						}
						
						String unskippedXmlText = IJ.openAsString((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml");
						unskippedXmlText = unskippedXmlText.replace("Skipped", "");
						unskippedXmlText = unskippedXmlText.replace("aaa_emb_edited.zip", "aaa_emb_Unskipped.zip");
						unskippedXmlText = unskippedXmlText.replace("zRes=\"1\"", "zRes=\"0.1625\"");
						unskippedXmlText = unskippedXmlText.replace("planeEnd=\"296\"", "planeEnd=\"320\"");
						IJ.saveString(unskippedXmlText, (outDir+subdir).replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml");
						
						RoiManager.respaceStarryNiteNuclei((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb.zip", 6);
						
						Process linMeasure = null;
						try {
							ProcessBuilder linMeasurePB = new ProcessBuilder(new String[]{"cmd","/c","start","/min","/wait","java", "-Xmx500m", "-cp", "acebatch2.jar", "Measure1", (outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml"});
							linMeasurePB.directory(new File("C:\\SN_Feeder_distribution_code\\"));
							linMeasure = linMeasurePB.start();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if(linMeasure!=null) {
							try {
								IJ.log("Measuring for AuxInfo "+(new Date()).getTime());
								linMeasure.waitFor();
								IJ.log("Measuring for AuxInfo Complete "+(new Date()).getTime());
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						Process linGreenExtract = null;
						try {
							ProcessBuilder linGreenExtractPB = new ProcessBuilder(new String[]{"cmd","/c","start","/min","/wait","java", "-cp", "acebatch2.jar", "SixteenBitGreenExtractor1", (outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_emb_edited.xml", ""+endPoint});
							linGreenExtractPB.directory(new File("C:\\SN_Feeder_distribution_code\\"));
							linGreenExtract = linGreenExtractPB.start();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if(linGreenExtract!=null) {
							try {
								IJ.log("Measuring Green channel "+(new Date()).getTime());
								linGreenExtract.waitFor();
								IJ.log("Measuring Green channel Complete "+(new Date()).getTime());
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}    
					}
				});
				linThread.start();
			}	
		}
	}
	
	
	//some clipped code that might demo editing within a zip.
	static void modifyTextFileInZip(String zipPath) throws IOException {
	    Path zipFilePath = Paths.get(zipPath);
	    try (FileSystem fs = FileSystems.newFileSystem(zipFilePath, null)) {
	        Path source = fs.getPath("/abc.txt");
	        Path temp = fs.getPath("/___abc___.txt");
	        if (Files.exists(temp)) {
	            throw new IOException("temp file exists, generate another name");
	        }
	        Files.move(source, temp);
	        streamCopy(temp, source);
	        Files.delete(temp);
	    }
	}

	static void streamCopy(Path src, Path dst) throws IOException {
	    try (BufferedReader br = new BufferedReader(
	            new InputStreamReader(Files.newInputStream(src)));
	         BufferedWriter bw = new BufferedWriter(
	            new OutputStreamWriter(Files.newOutputStream(dst)))) {

	        String line;
	        while ((line = br.readLine()) != null) {
	            line = line.replace("key1=value1", "key1=value2");
	            bw.write(line);
	            bw.newLine();
	        }
	    }
	}
	
}
