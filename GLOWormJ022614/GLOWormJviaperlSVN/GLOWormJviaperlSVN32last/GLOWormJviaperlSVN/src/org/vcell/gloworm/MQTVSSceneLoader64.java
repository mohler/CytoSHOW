package org.vcell.gloworm;
import ij.*;
import ij.macro.MacroRunner;
import ij.measure.Calibration;
import ij.process.*;
import ij.gui.*;
import ij.io.FileInfo;
import ij.io.Opener;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;

import ij.plugin.*;
import ij.plugin.frame.*;

import java.io.*;
import java.util.ArrayList;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

import client.RemoteMQTVSHandler;
import ij.util.Java2;


public class MQTVSSceneLoader64 implements PlugIn {

	private boolean grayscale;
	private boolean virtualStack;
	private boolean multiVStack;
	private boolean virtualHyperStack;
	private boolean stretchToFitOverlay;
	private boolean viewOverlay;
	private boolean sideSideStereo = false;
	private boolean redCyanStereo = false;
	private boolean rcsprx = false;
	private boolean horizontal;
	private boolean grid = false;
	private  String[] channelLUTItems =  { "Red", "Green", "Blue", "Grays","Cyan", "Magenta", "Yellow",  "Fire", "Ice", "Spectrum", "3-3-2 RGB"};

	String movieFileList = "";
	int movieCount = 0;
	String movieSliceDepthList = "";
	String movieAdjustmentFileList = "";
	String pathlist;

	File file;
	private static int displayMode = 1;
	private int cPosition = 1;
	private int zPosition = 1;
	private int tPosition = 1;
	private String roiFileName, roiName;
	private int ZsustainROIs = 1;
	private int TsustainROIs = 1;
	private String lineageMapImagePath="";
	private String lineageLCDFilePath="";
	private String clFileName;
	private boolean silentlyUpdateScene;
	private ImagePlus imp;
	private boolean cycling = false;
	private int acquisitionInterval;

	/*  */	
	//constructor for calling by static method:
	MQTVSSceneLoader64(String pathlist, String options) {
		//	         showDialog();	//??????? a Generic dialog there will get the options    ????????
		this.pathlist = pathlist; //set instance variable
		if (options.contains("cycling"))
			cycling = true;
		if (options.contains("rc"))
			redCyanStereo = true;
		if (options.contains("Stereo4DXrc"))
			rcsprx = true;
		else
			Macro.setOptions(options);
		run(pathlist);
	}

	//constructor for calling by static method:
	MQTVSSceneLoader64(String options, int one) {
		Macro.setOptions(options);
		this.pathlist = null;
		run("");
	}

	//constructor for calling by static method:
	MQTVSSceneLoader64(String pathlist) {
		this.pathlist = pathlist; //set instance variable
		run(pathlist);
	}

	//static method
	public static MQTVSSceneLoader64 runMQTVS_SceneLoader64(String pathlist,  String options) {
		return new MQTVSSceneLoader64(pathlist, options);
		//return ("Pathlist, Options");
	}

	//static method
	public static MQTVSSceneLoader64 runMQTVS_SceneLoader64(String options, int one) {
		return new MQTVSSceneLoader64(options, 1);
		//return ("Options only");
	}

	//static method
	public static MQTVSSceneLoader64 runMQTVS_SceneLoader64(String pathlist) {
		return new MQTVSSceneLoader64(pathlist);
		//return ("Pathlist only");

	}
	/*  */


	public void run(final String arg) {
		pathlist = arg;
		if (pathlist == "") return;

		Frame cmp = WindowManager.getFrame("Composite Palette");
		if (cmp != null) {
			((PlugInFrame)cmp).close();
		}

		IJ.log("");
		Dimension screenDimension = IJ.getScreenSize();
		if (WindowManager.getFrame("Too Much Info ;) Window") != null){
			WindowManager.getFrame("Too Much Info ;) Window").setBounds(30,screenDimension.height-250,850,250);
			WindowManager.getFrame("Too Much Info ;) Window").toFront();
		}

		pathlist = pathlist.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
		if (pathlist.contains("/Volumes/GLOWORM_DATA"))
			pathlist = pathlist.replace('\\', '/');
		pathlist = pathlist.replaceAll("(URL=)http.*MOVIE=", ""); //Windows drop string
		pathlist = pathlist.replace("localhost", "");
		IJ.log(pathlist);

		File loadFile = new File(pathlist) ;
		try {
			file = loadFile;
			if ( file == null) {
				IJ.log("null pathlist file");
			}else {
				if ( !file.getPath().toLowerCase().contains("scene.scn") ) {
					IJ.log(" pathlist not scene.scn file");
					movieFileList = movieFileList + pathlist	+ "|";
					movieSliceDepthList = movieSliceDepthList +36+ "|";
				} else {

					//if (IJ.debugMode) IJ.log(file.getPath() );
					BufferedReader in = null;
					PrintWriter out = null;
					if (pathlist.startsWith("/Volumes/GLOWORM_DATA/")
								|| pathlist.contains("\\GLOWORM_DATA\\")) {	

						File cacheFile = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+pathlist);
						if (!(new File(cacheFile.getParent())).canWrite()) {
							//Crappy loop seems necessary for windows...							
							while (!(new File(cacheFile.getParent())).mkdirs()) { 
								while (!(new File(new File(cacheFile.getParent()).getParent())).mkdirs()) {
									while (!(new File(new File(new File(cacheFile.getParent()).getParent()).getParent())).mkdirs()) {
										while (!(new File(new File(new File(new File(cacheFile.getParent()).getParent()).getParent()).getParent()).mkdirs())) {

										}
									}
								}
							}
						}
						if (cacheFile.canRead()) {
							in = new BufferedReader(new FileReader(cacheFile));
						}else {
							in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
									RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], pathlist.replaceAll("%2B", "\\+").replaceAll("%25", "%").replace("|", "")))));
//							out = new PrintWriter(
//									new BufferedWriter(
//											new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+pathlist) ), true);
						}
					} else {
						in = new BufferedReader(new FileReader(file));
//						out = new PrintWriter(
//								new BufferedWriter(
//										new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+pathlist) ), true);
					}
					String line = "";
					//if (IJ.debugMode) IJ.log(line);
					silentlyUpdateScene = false;
					while (!line.contains("End of parameter list")) {
						line = in.readLine();
//						if (out!=null)
//							out.println(line);
						//if (IJ.debugMode) IJ.log(line);
						String[] lineSegments = line.split(" \\= ");

						if (lineSegments.length == 3) {
							if (lineSegments[0].toLowerCase().endsWith(".mov") 
									|| lineSegments[0].toLowerCase().endsWith(".avi")
									|| lineSegments[0].toLowerCase().endsWith(".tif")
									|| new File(lineSegments[0]).isDirectory() ) {
								movieCount = movieCount /*+ (redCyanStereo?2:1)*/;
								movieFileList = movieFileList + lineSegments[0]	+ "|"
													+(redCyanStereo?(lineSegments[0] + "|"):"");
								//if (IJ.debugMode) IJ.log(movieFileList);
								movieSliceDepthList = movieSliceDepthList + lineSegments[1] + "|"
										+(redCyanStereo?(lineSegments[1] + "|"):"");
								movieAdjustmentFileList = movieAdjustmentFileList + lineSegments[2] + "|"
										+(redCyanStereo?(lineSegments[2] + "|"):"");
							}
						}

						if (lineSegments[0].contains("ROIfile") ) {
							roiFileName = lineSegments[1];
							//if (IJ.debugMode) IJ.log("roiFileName = " + roiFileName);
						}

						if (lineSegments[0].contains("ColorLegendFile") ) {
							clFileName = lineSegments[1];
							//if (IJ.debugMode) IJ.log("roiFileName = " + roiFileName);
						}

						if (lineSegments[0].contains("Selection") ) {
							roiName = lineSegments[1];
							//IJ.log(roiName);
							//if (IJ.debugMode) IJ.log("roiName = " + roiName);
						}

						if (lineSegments[0].contains("Convert8bit") ) {
							if (lineSegments[1].contains("true") ) {
								grayscale = true;
							} else {
								grayscale = false;

							}
						}	


						if (lineSegments[0].contains("VirtualStack") ) {
							if (lineSegments[1].contains("true") ) {
								virtualStack = true;
							} else {
								virtualStack = false;
							}
						}	


						if (lineSegments[0].contains("MultipleMovies") ) {
							if (lineSegments[1].contains("true") ) {
								multiVStack = true;
							} else {
								multiVStack = false;
							}
						}	


						if (lineSegments[0].contains("HyperStack") ) {
							if (lineSegments[1].contains("true") ) {
								virtualHyperStack = true;
							} else {
								virtualHyperStack = false;
							}
						}	


						if (lineSegments[0].contains("StretchToFit") ) {
							if (lineSegments[1].contains("true") ) {
								stretchToFitOverlay = true;
							} else {
								stretchToFitOverlay = false;
							}
						}	


						if (lineSegments[0].contains("ViewInOverlay") ) {
							if (lineSegments[1].contains("true") ) {
								viewOverlay = true;
							} else {
								viewOverlay = false;
							}
						}	


						if (lineSegments[0].contains("HorizontalMontage") ) {
							if (lineSegments[1].contains("true") ) {
								horizontal = true;
							} else {
								horizontal = false;
							}
						}	

						if (lineSegments[0].contains("SideSideStereo") ) {
							if (lineSegments[1].contains("true") ) {
								sideSideStereo = true;
							} else {
								sideSideStereo = false;
							}
						}	

						if (lineSegments[0].contains("RedCyanStereo") ) {
							if (lineSegments[1].contains("true") || redCyanStereo == true) {
								redCyanStereo = true;
							} else {
								redCyanStereo = false;
							}
						}	

						if (lineSegments[0].contains("GridLayOut") ) {
							if (lineSegments[1].contains("true") ) {
								grid = true;
							} else {
								grid = false;
							}
						}	


						if (lineSegments[0].contains("DisplayMode") ) {
							if (Integer.parseInt(lineSegments[1]) > 1 ) {
								displayMode = (Integer.parseInt(lineSegments[1]));
//								if (displayMode == 3)
//									displayMode = 1;
							} else {
								displayMode = 1;
							}
							//if (IJ.debugMode) IJ.log( "displayMode =" + displayMode );

						}	
						if (lineSegments[0].contains("Cposition") ) {
							if (Integer.parseInt(lineSegments[1]) > 1 ) {
								cPosition = Integer.parseInt(lineSegments[1]);
							} else {
								cPosition = 1;
							}
						}	
						if (lineSegments[0].contains("Zposition") ) {
							if (Integer.parseInt(lineSegments[1]) > 1 ) {
								zPosition = Integer.parseInt(lineSegments[1]);
							} else {
								zPosition = 1;
							}
						}	
						if (lineSegments[0].contains("Tposition") ) {
							if (Integer.parseInt(lineSegments[1]) > 1 ) {
								tPosition = Integer.parseInt(lineSegments[1]);
							} else {
								tPosition = 1;
							}
						}	

						if (lineSegments[0].contains("ZsustainROIs") ) {
							if (Integer.parseInt(lineSegments[1]) > 1 ) {
								ZsustainROIs = Integer.parseInt(lineSegments[1]);
							} else {
								ZsustainROIs = 1;
							}
						}	
						if (lineSegments[0].contains("TsustainROIs") ) {
							if (Integer.parseInt(lineSegments[1]) > 1 ) {
								TsustainROIs = Integer.parseInt(lineSegments[1]);
							} else {
								TsustainROIs = 1;
							}
						}	
						if (lineSegments[0].contains("LineageMapImage") ) {
							lineageMapImagePath = lineSegments[1];	 
						}	
						if (lineSegments[0].contains("LineageLCDFile") ) {
							lineageLCDFilePath = lineSegments[1];	    						 
						}	
						if (line.contains("AcquisitionComplete = false") ) {
							silentlyUpdateScene = true;
						}	
						if (line.contains("AcquisitionInterval") ) {
						    acquisitionInterval = Integer.parseInt(lineSegments[1]);
						}	

					}
					in.close();
//					if (out!=null)
//						out.close();
				}

				//Special case of selecting a cell in an already opened movie from a url click
				if (WindowManager.getIDList() != null) {
					for (int q=0; q<WindowManager.getIDList().length; q++) {
						if (file.getPath().contains("MQTVS") 
								&& WindowManager.getImage(WindowManager.getIDList()[q])
								.getTitle().contains(file.getPath().substring(file.getPath().indexOf("MQTVS"), file.getPath().indexOf("MQTVS")+15))) {
							if (roiName != null) {
								int n = WindowManager.getImage(WindowManager.getIDList()[q]).getRoiManager().getListModel().getSize();
								for (int i=0; i<n; i++) {
									//IJ.log(rm.getList().getItems()[i]);
									if (((String) WindowManager.getImage(WindowManager.getIDList()[q]).getRoiManager().getListModel().get(i)).matches(roiName+"[CZT]*")) {
										new ij.macro.MacroRunner("roiManager('select', "+i+", "+WindowManager.getIDList()[q]+");");
										return;
									}
								}
							}
						}
					}
				}


				if ( !IJ.isWindows() || pathlist.contains("/Volumes/GLOWORM_DATA/")) {
					movieFileList = movieFileList.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
					movieFileList = movieFileList.replace('\\', '/');
					//if (IJ.debugMode) IJ.log(movieFileList + "WinToMac");			
					movieAdjustmentFileList = movieAdjustmentFileList.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
					movieAdjustmentFileList = movieAdjustmentFileList.replace('\\', '/');

					lineageMapImagePath = lineageMapImagePath.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
					lineageMapImagePath = lineageMapImagePath.replace('\\', '/');

					lineageLCDFilePath = lineageLCDFilePath.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
					lineageLCDFilePath = lineageLCDFilePath.replace('\\', '/');

				} else {
					movieFileList = movieFileList.replaceAll("/Volumes/GLOWORM_DATA", "Q:" );
					movieFileList = movieFileList.replace('/', '\\');
					//if (IJ.debugMode) IJ.log(movieFileList	+ "MacToWin");
					movieAdjustmentFileList = movieAdjustmentFileList.replaceAll("/Volumes/GLOWORM_DATA", "Q:" );
					movieAdjustmentFileList = movieAdjustmentFileList.replace('/', '\\');

					lineageMapImagePath = lineageMapImagePath.replaceAll("/Volumes/GLOWORM_DATA", "Q:" );
					lineageMapImagePath = lineageMapImagePath.replace('/', '\\');

					lineageLCDFilePath = lineageLCDFilePath.replaceAll("/Volumes/GLOWORM_DATA", "Q:" );
					lineageLCDFilePath = lineageLCDFilePath.replace('/', '\\');

				}

				String[] paths = movieFileList.split("\\|");				// Makes String[] array of all of the file paths to movies using | as the separator to split with.
				String[] movieSliceDepths =  movieSliceDepthList.split("\\|");
				if ( (paths[0] != null) && (movieSliceDepths[0] != null) ) {
					int[] movieSliceDepthValues = new int[movieSliceDepths.length];
					for ( int i=0; i < movieSliceDepthValues.length; i++) {
						movieSliceDepthValues[i] = Integer.valueOf(movieSliceDepths[i]).intValue();
					}
					String[] movieAdjustmentFiles = movieAdjustmentFileList.split("\\|"); 
					setImp(null);

					String pathConcat="";
					for (int p=0;p<paths.length;p++)
						pathConcat = pathConcat +" " +paths[p]+" " +movieSliceDepthValues[p];

					RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], pathConcat.trim(), 
												stretchToFitOverlay, viewOverlay, grayscale, grid, horizontal, sideSideStereo, redCyanStereo, rcsprx, silentlyUpdateScene);
//					rmqtvsh.getImagePlus().show();
					setImp(rmqtvsh.getImagePlus());

					getImp().setPosition(cPosition, zPosition, tPosition);
					if(!cycling)
						getImp().getWindow().setVisible(true);
					
					Thread reloadThread = getImp().getRemoteMQTVSHandler().getReloadThread();
					if (reloadThread == null) {
						reloadThread = (new Thread(new Runnable(){
							public void run() {
								IJ.wait(acquisitionInterval);
								if (getImp().getWindow()!=null) {
									MQTVSSceneLoader64 nextMsl64 = MQTVSSceneLoader64.runMQTVS_SceneLoader64(pathlist, "cycling");
									nextMsl64.getImp().setPosition(getImp().getChannel(), getImp().getSlice(), getImp().getFrame());
									boolean running = getImp().getWindow().running;
									boolean running2 = getImp().getWindow().running2;
									boolean running3 = getImp().getWindow().running3;
									nextMsl64.getImp().getWindow().setLocation(getImp().getWindow().getLocation().x, getImp().getWindow().getLocation().y);
									nextMsl64.getImp().getCanvas().setMagnification(getImp().getCanvas().getMagnification());
									nextMsl64.getImp().getWindow().setSize(getImp().getWindow().getSize());
									nextMsl64.getImp().getWindow().setVisible(true);
									if (nextMsl64.getImp().isComposite()) {
										((CompositeImage)nextMsl64.getImp()).copyLuts(getImp());
										((CompositeImage)nextMsl64.getImp()).setMode(3);
										((CompositeImage)nextMsl64.getImp()).setMode(displayMode);
									}
									getImp().close();
									if (running || running2) 
										IJ.doCommand("Start Animation [\\]");
									if (running3) 
										IJ.doCommand("Start Z Animation");
								}
							}
						}
								));
						if(silentlyUpdateScene){
							reloadThread.start();
						}
					}

					MultiChannelController mcc = getImp().getMultiChannelController();
					if (mcc==null) {
						mcc = new MultiChannelController(getImp());
						getImp().setMultiChannelController(mcc);
					}
					
					double lastMin = 0;
					double lastMax = 255;
					//ARE THERE STILL CASES WHERE WE NEED TO ADJUST TAGS ON STARTUP?
					getImp().getMultiChannelController().doingFirstSetup = true;
					for ( int j=0 ; j < movieAdjustmentFiles.length ; j++) {

						//if (IJ.debugMode) IJ.log("Loading Adjustments for Channel " + (j+1) + " " + ((MultiQTVirtualStack) vstack).getVirtualStack(j).getMovieFile().toString() );

						try {
							File adjFile;
							if (pathlist.contains("/Volumes/GLOWORM_DATA") ) {
								adjFile = new File("/Volumes/GLOWORM_DATA/"  + movieAdjustmentFiles[j]  );
							} else if (!IJ.isWindows() ) {
								//if (IJ.debugMode) IJ.log("Macintosh or Linux");
								adjFile = new File( "" + file.getParent() +  System.getProperty("file.separator") +movieAdjustmentFiles[j]  );
							} else {
								//if (IJ.debugMode) IJ.log("Windows");
								//if (IJ.debugMode) IJ.log( "" + file.getParent() +  "\\" +adjustmentFileName[j]  );
								adjFile = new File( "" + file.getParent() +  "\\" +movieAdjustmentFiles[j]  );
							}	

							if (adjFile != null) {
								if (IJ.debugMode) IJ.log(adjFile.getPath() );
								BufferedReader in = null;
								PrintWriter out = null;
								String adjFilePath = adjFile.getPath();
								adjFilePath=adjFilePath.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
								adjFilePath=adjFilePath.replace("\\Volumes\\GLOWORM_DATA\\", "/Volumes/GLOWORM_DATA/");
								if (adjFilePath.startsWith("/Volumes/GLOWORM_DATA/")
										|| adjFilePath.contains("\\GLOWORM_DATA\\")) {
									File cacheFile = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+adjFilePath);
									if (!(new File(cacheFile.getParent())).canWrite()) {
										//Crappy loop seems necessary for windows...							
										while (!(new File(cacheFile.getParent())).mkdirs()) { 
											while (!(new File(new File(cacheFile.getParent()).getParent())).mkdirs()) {
												while (!(new File(new File(new File(cacheFile.getParent()).getParent()).getParent())).mkdirs()) {
													while (!(new File(new File(new File(new File(cacheFile.getParent()).getParent()).getParent()).getParent()).mkdirs())) {

													}
												}
											}
										}
									}
									if (cacheFile.canRead()) {
										in = new BufferedReader(new FileReader(cacheFile));
									}else {
										in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
												RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], adjFilePath))));
										out = new PrintWriter(
												new BufferedWriter(
														new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+adjFilePath) ), true);
									}
								} else {
									in = new BufferedReader(new FileReader(adjFilePath));
									out = new PrintWriter(
											new BufferedWriter(
													new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+adjFilePath) ), true);
								}
								String line = "";
								//if (IJ.debugMode) IJ.log(line);

								double displayMin = 0;
								double displayMax = 255;
								double scaleX = 0;
								double scaleY = 0;
								double scaleZ = 1;
								double shiftX = 0;
								double shiftY = 0;								

								String[] lineSegments;

								if (line != null) {
									while (line != null && !line.contains("End of parameter list")) {
										line = in.readLine();
										if (out!=null)
											out.println(line);

										//if (IJ.debugMode) IJ.log(line);

										if (line != null) {
											lineSegments = line.split(" = ");
											if (lineSegments[0].contains("LUT")) {
												IJ.run(getImp(), "Stop Animation", "");
												getImp().setPosition(j + 1, getImp().getSlice(), getImp().getFrame());

												IJ.run(getImp(), lineSegments[1], "");

												for (int i = 0; i < channelLUTItems.length; i++) {
													if (channelLUTItems[i].contains(lineSegments[1])) {
														mcc.setChannelLUTChoice(j, i);
													}
												}

											}
											boolean rangeMod = false;
											if (lineSegments != null
													&& lineSegments[0]
															.contains("DisplayRangeMin")) {
												IJ.run(getImp(), "Stop Animation", "");
												displayMin = Double
														.parseDouble(lineSegments[1]);
												lastMin=displayMin;
												rangeMod=true;
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("DisplayRangeMax")) {
												IJ.run(getImp(), "Stop Animation", "");
												displayMax = Double
														.parseDouble(lineSegments[1]);
												lastMax=displayMax;
												rangeMod=true;
											}
											if (rangeMod && getImp().isComposite()) {
												((CompositeImage) getImp()).setPosition(j+1, getImp().getSlice(), getImp().getFrame());
												getImp().setDisplayRange(displayMin, displayMax);
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("FlipVertical")) {
												if (lineSegments[1].contains("true")) {
													if (getImp().getRemoteMQTVSHandler()!=null) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack())
//														.flipSingleMovieVertical(j);
//														imp.getRoiManager().setRmNeedsUpdate(true);
														mcc.setFlipVCB(j, true);
														if (IJ.isWindows()) {
															mcc.setDoingFirstSetup(false);
															mcc.itemStateChanged(new ItemEvent(mcc.getFlipVCB(j), ItemEvent.ITEM_STATE_CHANGED, mcc.getFlipVCB(j), ItemEvent.SELECTED));
															mcc.setDoingFirstSetup(true);
														}
													}
												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("FlipHorizontal")) {
												if (lineSegments[1].contains("true")) {
													if (getImp().getRemoteMQTVSHandler()!=null) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack())
//														.flipSingleMovieHorizontal(j);
////														imp.getRoiManager().setRmNeedsUpdate(true);
														mcc.setFlipHCB(j, true);
														if (IJ.isWindows()) {
															mcc.setDoingFirstSetup(false);
															mcc.itemStateChanged(new ItemEvent(mcc.getFlipVCB(j), ItemEvent.ITEM_STATE_CHANGED, mcc.getFlipVCB(j), ItemEvent.SELECTED));
															mcc.setDoingFirstSetup(true);
														}
													}
												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("FlipZaxis")) {
												if (lineSegments[1].contains("true")) {
													if (getImp().getRemoteMQTVSHandler()!=null) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack())
//														.flipSingleMovieZaxis(j);
//														imp.getRoiManager().setRmNeedsUpdate(true);
														mcc.setFlipZCB(j, true);
														if (IJ.isWindows()) {
															mcc.setDoingFirstSetup(false);
															mcc.itemStateChanged(new ItemEvent(mcc.getFlipVCB(j), ItemEvent.ITEM_STATE_CHANGED, mcc.getFlipVCB(j), ItemEvent.SELECTED));
															mcc.setDoingFirstSetup(true);
														}
													}
												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("ShiftZ")) {
												int shiftZ = Integer
														.parseInt(lineSegments[1]);
												int zShiftNet = shiftZ;
												boolean forward = false;
												if (zShiftNet!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setShiftSingleMovieZPosition(j, zShiftNet);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														zShiftNet = shiftZ - ((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack()).getShiftSingleMovieZPosition(j);
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setShiftSingleMovieZPosition(j, shiftZ);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setSliceSpinner(j, shiftZ);
												} else {

												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("ShiftT")) {
												int shiftT = Integer
														.parseInt(lineSegments[1]);
												int tShiftNet = shiftT;
												boolean forward = false;
												if (tShiftNet!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setShiftSingleMovieZPosition(j, tShiftNet);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														tShiftNet = shiftT - ((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack()).getShiftSingleMovieTPosition(j);
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setShiftSingleMovieTPosition(j, shiftT);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setFrameSpinner(j, shiftT);
												} else {

												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("deltaZ")) {
												int deltaZ = Integer
														.parseInt(lineSegments[1]);
												if (deltaZ!=1) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setRelativeZFrequency(j, deltaZ);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setRelativeZFrequency(j, deltaZ);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setDeltaZSpinner(j, deltaZ);
												} else {
													//if (IJ.debugMode) IJ.log("no deltaZ in Z loaded");
												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("deltaT")) {
												int deltaT = Integer
														.parseInt(lineSegments[1]);
												if (deltaT!=1) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setRelativeFrameRate(j, deltaT);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setRelativeFrameRate(j, deltaT);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setDeltaTSpinner(j, deltaT);
												} else {
													//if (IJ.debugMode) IJ.log("no deltaT in T loaded");
												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("ScaleX")) {
												scaleX = Double
														.parseDouble(lineSegments[1]);
												if (scaleX!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setSingleMovieScale(j, scaleX, scaleY);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setScaleX(j, scaleX);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setScaleXSpinner(j, scaleX);
												} else {
													//if (IJ.debugMode) IJ.log("no ScaleX loaded");
												}
											}
											if (lineSegments != null
													&& lineSegments[0]
															.contains("ScaleY")) {
												scaleY = Double
														.parseDouble(lineSegments[1]);
												if (scaleY!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setSingleMovieScale(j, scaleX, scaleY);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setScaleX(j, scaleY);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setScaleYSpinner(j, scaleY);
												} else {
													//if (IJ.debugMode) IJ.log("no ScaleY loaded");
												}
											}

											if (lineSegments != null
													&& lineSegments[0]
															.contains("ScaleZ")) {
												scaleZ = Double
														.parseDouble(lineSegments[1]);
												if (scaleZ!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setSingleMovieScale(j, scaleX, scaleY);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setScaleX(j, scaleY);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setScaleZSpinner(j, scaleZ);
													getImp().getCalibration().pixelDepth = scaleZ;
												} else {
													//if (IJ.debugMode) IJ.log("no ScaleY loaded");
												}
											}

											if (lineSegments != null
													&& lineSegments[0]
															.contains("RotationAngle")) {
												double rotAngle = Double
														.parseDouble(lineSegments[1]);
												if (rotAngle!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setSingleMovieRotationAngle(j, rotAngle);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setSingleMovieRotationAngle(j, rotAngle);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setRotateAngleSpinner(j, rotAngle);
												} else {
												}
											}
											if (lineSegments != null && lineSegments[0].contains("ShiftX") ) {
												shiftX = Double.parseDouble(lineSegments[1]);
												if (shiftX!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setSingleMovieTranslate(j, shiftX, shiftY);
//													} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setTranslateX(j, shiftX);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setTranslateXSpinner(j, shiftX);

													//if (IJ.debugMode) IJ.log("YES ShiftX loaded");										
												} else {
													//if (IJ.debugMode) IJ.log("no shift in X loaded");
												}
											}	
											if (lineSegments != null && lineSegments[0].contains("ShiftY") ) {
												shiftY = Double.parseDouble(lineSegments[1]);
												if (shiftY!=0) {
//													if (imp.getStack() instanceof MultiQTVirtualStack) {
//														((MultiQTVirtualStack) imp.getStack()).setSingleMovieTranslate(j, shiftX, shiftY);
//													}else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
//														((RemoteMQTVSHandler.RemoteMQTVirtualStack) imp.getStack()).setTranslateY(j, shiftY);
//													}
//													imp.getRoiManager().setRmNeedsUpdate(true);
													mcc.setTranslateYSpinner(j, shiftY);

													//if (IJ.debugMode) IJ.log("YES ShiftY loaded");
												} else {
													//if (IJ.debugMode) IJ.log("no shift in Y loaded");
												}
											}	
										}
									}
								}	
								else 
									lineSegments = null;

								in.close();
								if (out!=null)
									out.close();
							}	
						}
						catch (IOException ev)
						{
							IJ.log("I/O Error: Cannot read from specified directory/file.  MQTVSSceneLoader.openAsVirtualStack" + ev.toString());
							//					try { BrowserLauncher.openURL
							//						("mailto:support@gloworm.org?" +
							//								"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
							//								"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
							//								"&body=MQTVSSceneLoader.openAsVirtualStack" + ev.toString());
							//					}
							//					catch (IOException ev2) {}
							//					//System.exit(0);
						}

					}
					getImp().getMultiChannelController().doingFirstSetup = false;

//					getImp().setPosition(1, getImp().getSlice(), getImp().getFrame()+1);
//					getImp().setPosition(1, getImp().getSlice(), getImp().getFrame()-1);
					if (getImp().isComposite()) {
//						int mode = ((CompositeImage)getImp()).getMode();
						((CompositeImage)getImp()).setMode(1);
						((CompositeImage)getImp()).setMode(2);
						((CompositeImage)getImp()).setMode(3);
						((CompositeImage)getImp()).setMode(displayMode);
						((CompositeImage)getImp()).updateAndRepaintWindow();
					}

					if(!cycling)
						getImp().getWindow().setVisible(true);
					

					/*************************************************************************************************************************************************/

					screenDimension = IJ.getScreenSize();


					Frame iwf = getImp().getWindow();
					//if (IJ.debugMode) IJ.log(iwf.toString() + " is the FrontWindow iwf");
					if (iwf != null) { 
						Dimension d = iwf.getSize() ;
						iwf.setBounds(20,100,d.width, d.height +10);
//						while (100+iwf.getSize().height > screenDimension.height-190) {
//							IJ.run(imp,"Out [-]","");
//						}
						Frame mcf = getImp().getMultiChannelController();
						if (mcf != null) {
							Dimension dmc = mcf.getSize() ;
							mcf.setBounds(screenDimension.width-(175*getImp().getNChannels()), ((d.height+120 < screenDimension.height -50)? d.height+120: screenDimension.height-50),175*getImp().getNChannels(), 180);
							//					mcf.setVisible(true);

						}
					}
//					WindowManager.getFrame("Too Much Info ;) Window").toFront();
					
					
					RoiManager rm = getImp().getRoiManager();
					String clStr;
					if (clFileName != null) {
						if (pathlist.contains("/Volumes/GLOWORM_DATA") ) {
							clStr = IJ.openAsString("/Volumes/GLOWORM_DATA/" + clFileName);
						} else {
							clStr = IJ.openAsString(file.getPath().substring(0, file.getPath().lastIndexOf("MQTVS")) + clFileName);
						}

						BufferedReader in = null;
						if (pathlist.startsWith("/Volumes/GLOWORM_DATA/")) {
							in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
									RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], ("/Volumes/GLOWORM_DATA/" + clFileName)))));
							if (in != null) {
								StringBuilder stringBuilder = new StringBuilder();
								String ls = System.getProperty("line.separator");
								String line = "";
								while (line != null) {
									line = in.readLine();
									if (line != null)
										stringBuilder.append(line).append(ls);
								}
								clStr = stringBuilder.toString();
							}
						}
					} else {
						String universalCLURL = MQTVSSceneLoader64.class.getResource("docs/fullUniversal_ColorLegend.lgd").toString();
						clStr = IJ.openUrlAsString(universalCLURL);
					}
					ColorLegend cl = new ColorLegend(getImp(), clStr);
					rm.setColorLegend(cl);

					if (roiFileName != null) {
						String roiFilePath;
						if (pathlist.contains("/Volumes/GLOWORM_DATA") ) {
							roiFilePath = "/Volumes/GLOWORM_DATA/"  + roiFileName ;
						} else {
							roiFilePath = file.getPath().substring(0, file.getPath().lastIndexOf("MQTVS")) + roiFileName; 
						}
						IJ.log(roiFilePath);
						File roiFile = new File(roiFilePath);
						if (roiFile.exists() || roiFilePath.startsWith("/Volumes/GLOWORM_DATA/")) {
							//						imp.setRoiManager(rm);
							if (rm == null) {
								getImp().setRoiManager(new RoiManager(getImp(), true));
								rm = getImp().getRoiManager();
							}

							rm.runCommand("Open", roiFilePath);
							rm.setZSustain(ZsustainROIs);
							rm.setTSustain(TsustainROIs);
							rm.runCommand("show all");
							rm.runCommand("associate", "true");

							if (roiName != null) {
								int n = rm.getListModel().getSize();
								for (int i=0; i<n; i++) {
									//IJ.log(rm.getList().getItems()[i]);
									if (((String) rm.getListModel().get(i)).matches(roiName.replace("+", "\\+")+"[CZT]*")) {
										//rm.select(i);
										// this needs to run on a separate thread, at least on OS X
										// "update2" does not clone the ROI so the "Show All"
										// outline moves as the user moves the RO.
										//IJ.log("Selection = " +roiName +" "+ i);
										new ij.macro.MacroRunner("roiManager('select', "+i+", "+getImp().getID()+");");
									}
								}
							}
							//						rm.setVisible(false);
						}
					}

					if (lineageLCDFilePath != "" && lineageMapImagePath != "") {
						ImagePlus lineageMapImage = null;

						byte[] inBytes = null;
						lineageMapImagePath = lineageMapImagePath.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
						if (lineageMapImagePath.contains("/Volumes/GLOWORM_DATA"))
							lineageMapImagePath = lineageMapImagePath.replace('\\', '/');

						if (lineageMapImagePath.startsWith("/Volumes/GLOWORM_DATA/")) {
							inBytes = RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], lineageMapImagePath);
							if (inBytes != null) {
								ByteArrayInputStream bais = new ByteArrayInputStream((byte[]) inBytes);
								BufferedImage bi=null;
								try {
									bi = ImageIO.read(bais);
								} catch (IOException e) {
									e.printStackTrace();
								}

								lineageMapImage = new ImagePlus("lineageMap",bi);
								lineageMapImage.show();
							}
						}else {
							lineageMapImage = IJ.openImage(lineageMapImagePath);
						}

						if (lineageMapImage !=null) {
							lineageMapImage.getWindow().setBackground(getImp().getWindow().getBackground());
							if (getImp()!=null) ((VirtualStack) getImp().getStack()).setLineageMapImage(lineageMapImage);
							ImagePlus lineageLinkedImp = getImp();
							String lineageMacro = IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/LineageTreeBrowseMacro.txt");
							File tempLCDFile = null;
							lineageLCDFilePath = lineageLCDFilePath.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
							if (lineageLCDFilePath.contains("/Volumes/GLOWORM_DATA"))
								lineageLCDFilePath = lineageLCDFilePath.replace('\\', '/');

							if (lineageLCDFilePath.startsWith("/Volumes/GLOWORM_DATA/")) {
								tempLCDFile = new File(IJ.getDirectory("temp") + lineageLCDFilePath.replace("/Volumes/GLOWORM_DATA/", ""));
								inBytes = RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], lineageLCDFilePath);
								if (inBytes != null) {
									FileOutputStream fileOuputStream = 
											new FileOutputStream(tempLCDFile); 
									fileOuputStream.write(inBytes);
									fileOuputStream.close();
								}
							} else {
								tempLCDFile = new File(lineageLCDFilePath);
							}

							MacroRunner lineageSelectionMacroRunner = new MacroRunner(lineageMacro, lineageMapImage.getTitle() + "|" +tempLCDFile.getPath() + "|" + lineageLinkedImp.getID());
						}							

					}
				}

			}
		}	
		catch (IOException ev)
		{
			IJ.log("I/O Error: Cannot read from specified directory/file.");
			boolean sendErrorEmail = IJ.showMessageWithCancel("**CytoSHOW error**",
					"It seems that your computer is unable to mount the shared drive \ncontaining CytoSHOW movies and scene files."+
					"\n \nTo report this problem and get help finding a solution, \nplease click OK and send the auto-generated email to CytoSHOW staff.");
			if (sendErrorEmail){
				try { BrowserLauncher.openURL

					("mailto:support@gloworm.org?" +
							"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
							"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
							"&body=I/O Error: Cannot read from specified directory/file.\nMQTVSSceneLoader.run " + ev.toString());
				}
				catch (IOException ev2) {}
			}

		}

	}

	public ImagePlus getImp() {
		return imp;
	}

	public void setImp(ImagePlus imp) {
		this.imp = imp;
	}

}