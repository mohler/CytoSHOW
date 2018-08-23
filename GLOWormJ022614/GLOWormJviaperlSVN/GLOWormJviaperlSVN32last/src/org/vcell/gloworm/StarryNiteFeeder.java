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
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;
import ij.process.ShortProcessor;
import ij.process.StackStatistics;

public class StarryNiteFeeder implements PlugIn {

	public void run(String arg) {
		String[] argChunks = arg.split("\\|");
		String sourceDir = "";
		String paramsPath = "";
		String outputDir = "";
		int skipFactor = 6;
		if (argChunks.length == 4) {
			sourceDir = argChunks[0];
			paramsPath = argChunks[1];
			outputDir = argChunks[2];
			skipFactor = Integer.parseInt(argChunks[3]);
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
		final String outDir = outputDir;
		
		WaitForUserDialog wfud = new WaitForUserDialog("StarryNite Feeder", 
														"Please position ROIs around the individual embryos to be lineaged.  \n\nThen adjust each embryo's t-slider to the last timepoint to be analyzed.  \n\nClick OK to launch analysis.");
		wfud.show();
		
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
			while (imp.getRoi() == null) {
				w++;
				if (w>WindowManager.getImageCount()){
					return;
				}
				imp = WindowManager.getImage(w);
			}
			Roi theROI = imp.getRoi();
			int type = imp.getRoi().getType() ;

			String title = imp.getTitle();
			String savetitle = title.replace(":","_").replace(" ","");
			new File(new File(outDir+savetitle+".roi").getParent()).mkdirs();
			IJ.save(imp, outDir+savetitle+".roi");
			int[] xpoints = imp.getRoi().getPolygon().xpoints;
			int[] ypoints = imp.getRoi().getPolygon().ypoints;
			int npoints = xpoints.length;

			double angle =0;
			if (type > Roi.OVAL) {
//				angle = imp.getRoi().getFeretValues()[1];
				angle = new Line(xpoints[0], ypoints[0], xpoints[npoints/2], ypoints[npoints/2]).getAngle();
			} else {
				angle = imp.getRoi().getBounds().getHeight()>imp.getRoi().getBounds().getWidth()?90:0;
			}

			angle = 180+ angle;

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
				if (!(new File(outDir+subdir+"/aaa"+f+".tif").canRead())) {
					
					ImageStack stack1 = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
					ImageStack stack1skipped = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
										
					ImageStack stack2 = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
					ImageStack stack2skipped = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());

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

						stack1.addSlice(ip1);
						if (i%skipFactor == 1) {
							stack1skipped.addSlice(ip1.duplicate());
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

							stack2.addSlice(ip2);
							if (i%skipFactor == 1) {
								stack2skipped.addSlice(ip2.duplicate());
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

			// Red channel:

					new File(outDir+subdir).mkdirs();
					new File(outDir+subdir+"Skipped").mkdirs();
					

					// save a stack
					IJ.save(frameRedImp, outDir+subdir+"/aaa"+f+".tif");
					IJ.save(frameRedImpSkipped, outDir+subdir+"Skipped"+"/aaa"+f+".tif");

					new File(outDir+subdir+"/image/tif16/").mkdirs();
					new File(outDir+subdir+"Skipped"+"/image/tif16/").mkdirs();


					String command16b = "format=TIFF start=1 name=aaa-t";
					command16b += ""+IJ.pad(f,3)+" digits=0 ";
					command16b += "save=";

					String command16a = "["+outDir+subdir+"/image/tif16]";
					String command16askipped = "["+outDir+subdir+"Skipped"+"/image/tif16]";
					//print(command16+command162);
					IJ.run(frameRedImp, "StarryNite Image Sequence... ", command16b+command16a);
					IJ.run(frameRedImpSkipped, "StarryNite Image Sequence... ", command16b+command16askipped);

					
					if (redMax==65535) {
						ImageStatistics stkStats = new StackStatistics(frameRedImp);
						frameRedImp.getProcessor().setMinAndMax(0,stkStats.max);
						frameRedImpSkipped.getProcessor().setMinAndMax(0,stkStats.max);
					} else if (redMax>0) {
						frameRedImp.getProcessor().setMinAndMax(0, redMax);
						frameRedImpSkipped.getProcessor().setMinAndMax(0, redMax);
					}else {
						frameRedImp.getProcessor().setMinAndMax(0, 5000);
						frameRedImpSkipped.getProcessor().setMinAndMax(0, 5000);
					}
					
					IJ.run(frameRedImp,"8-bit","");
					IJ.run(frameRedImpSkipped,"8-bit","");

					new File(outDir+subdir+"/image/tif/").mkdirs();
					new File(outDir+subdir+"Skipped"+"/image/tif/").mkdirs();


					String command1 = "format=TIFF start=1 name=aaa-t";
					command1 += ""+IJ.pad(f,3)+" digits=0 ";
					command1 += "save=";

					String command2 = "["+outDir+subdir+"/image/tif]";
					String command2skipped = "["+outDir+subdir+"Skipped"+"/image/tif]";
					//print(command1+command2);
					IJ.run(frameRedImp, "StarryNite Image Sequence... ", command1+command2);
					IJ.run(frameRedImpSkipped, "StarryNite Image Sequence... ", command1+command2skipped);

					
					
			// Green channel:
					new File(outDir+subdir+"/image/tifr16/").mkdirs();
					new File(outDir+subdir+"Skipped"+"/image/tifr16/").mkdirs();


					String command16 = "format=TIFF start=1 name=aaa-t";
					command16 += ""+IJ.pad(f,3)+" digits=0 ";
					command16 += "save=";

					String command162 = "["+outDir+subdir+"/image/tifr16]";
					String command162skipped = "["+outDir+subdir+"Skipped"+"/image/tifr16]";
					//print(command16+command162);
					IJ.run(frameGreenImp, "StarryNite Image Sequence... ", command16+command162);
					IJ.run(frameGreenImpSkipped, "StarryNite Image Sequence... ", command16+command162skipped);

					if (greenMax==65535) {
						ImageStatistics stkStats = new StackStatistics(frameGreenImp);
						frameGreenImp.getProcessor().setMinAndMax(0,stkStats.max);
						frameGreenImpSkipped.getProcessor().setMinAndMax(0,stkStats.max);
					} else if (greenMax>0) {
						frameGreenImp.getProcessor().setMinAndMax(0, greenMax);
						frameGreenImpSkipped.getProcessor().setMinAndMax(0, greenMax);
					}else {
						frameGreenImp.getProcessor().setMinAndMax(0, 5000);
						frameGreenImpSkipped.getProcessor().setMinAndMax(0, 5000);
					}
					
					IJ.run(frameGreenImp,"8-bit","");
					IJ.run(frameGreenImpSkipped,"8-bit","");

					new File(outDir+subdir+"/image/tifr/").mkdirs();
					new File(outDir+subdir+"Skipped"+"/image/tifr/").mkdirs();


					 command1 = "format=TIFF start=1 name=aaa-t";
					command1 += ""+IJ.pad(f,3)+" digits=0 ";
					command1 += "save=";

					 command2 = "["+outDir+subdir+"/image/tifr]";
					 command2skipped = "["+outDir+subdir+"Skipped"+"/image/tifr]";
					//print(command1+command2);
					IJ.run(frameGreenImp, "StarryNite Image Sequence... ", command1+command2);
					IJ.run(frameGreenImpSkipped, "StarryNite Image Sequence... ", command1+command2skipped);


					frameRedImp.flush();
					frameRedImpSkipped.flush();
					frameGreenImp.flush();
					frameGreenImpSkipped.flush();

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
					if(new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa__edited.xml").canRead()) {
						new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa__edited.xml").renameTo(
								new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa_"+
										(""+new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa__edited.xml").lastModified()).substring(0, 10)+"_edited.xml"));
					}
					
					try {
						IJ.log("matlab -nosplash -nodesktop -r ver;addpath('C:\\SN_Feeder_distribution_code\\'); detect_track_driver_allmatlab('"+impParameterPath+"','"+(outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\','aaa','','"+(outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\',0,true)");

						Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "matlab", "-nosplash", "-nodesktop", "-r", "ver;addpath('C:\\SN_Feeder_distribution_code\\'); detect_track_driver_allmatlab('"+impParameterPath+"','"+(outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\','aaa','','"+(outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\',0,true)"});
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					while (!(new File((outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa__edited.xml")).canRead()) {
						IJ.wait(1000);
					}
					Process linMeasure = null;
					try {
						ProcessBuilder linMeasurePB = new ProcessBuilder(new String[]{"cmd", "/c", "start", "java", "-Xmx500m", "-cp", "acebatch2.jar", "Measure1", (outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa__edited.xml"});
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
						ProcessBuilder linGreenExtractPB = new ProcessBuilder(new String[]{"cmd", "/c", "start", "java", "-cp", "acebatch2.jar", "SixteenBitGreenExtractor1", (outDir+subdir+"Skipped").replace("\\", "\\\\")+"\\\\aaa__edited.xml", ""+endPoint});
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
