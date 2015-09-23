package org.vcell.gloworm;
import ij.*;
import ij.measure.Calibration;
import ij.process.*;
import ij.gui.*;
import ij.io.OpenDialog;
import ij.io.Opener;

import java.awt.*;

import ij.plugin.*;
import ij.plugin.frame.*;

import java.io.*;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import ij.util.Java2;

import quicktime.*;								//There is no need to import QTVirtualStack simply because it is contained in the same .jar file as this file?
import quicktime.io.*;
import quicktime.qd.*;
import quicktime.std.clocks.*;
import quicktime.std.image.*;
import quicktime.std.movies.*;
import quicktime.std.movies.media.*;
import quicktime.app.view.*; //this is where several things were moved under QTJ 6.1!!
import quicktime.std.image.GraphicsImporter;
import quicktime.std.image.GraphicsMode;
import quicktime.app.display.*;
import quicktime.app.view.MoviePlayer; 
import quicktime.util.*;
import quicktime.std.StdQTConstants;


public class MQTVS_SceneLoader implements PlugIn {

	QTFile[] mqtf = null;								
	private boolean grayscale;
	private boolean virtualStack;
	private boolean multiVStack;
	private boolean virtualHyperStack;
	private boolean stretchToFitOverlay;
	private boolean viewOverlay;
	private boolean sideSideStereo = false;
	private boolean redCyanStereo = false;
	private boolean horizontal;
	private boolean grid = false;
	private  String[] channelLUTItems =  { "Red", "Green", "Blue", "Grays","Cyan", "Magenta", "Yellow",  "Fire", "Ice", "Spectrum", "3-3-2 RGB"};

	String movieFileList = "";
	int movieCount = 0;
	String movieSliceDepthList = "";
	String movieAdjustmentFileList = "";

	File file;
	private static int displayMode = 1;
	private int cPosition = 1;
	private int zPosition = 1;
	private int tPosition = 1;
	private String pathlist;
	private String roiFileName, roiName;
	private int ZsustainROIs = 1;
	private int TsustainROIs = 1;
	private String lineageMapImagePath = "";
	private String lineageLCDFilePath = "";
	private String clFileName;


	//static method
	public static void runMQTVS_SceneLoader(String pathlist,  String options) {
		new MQTVSSceneLoader(pathlist, options);
		//return ("Pathlist, Options");
	}

	//static method
	public static void runMQTVS_SceneLoader(String options, int one) {
		new MQTVSSceneLoader(options, 1);
		//return ("Options only");
	}

	//static method
	public static void runMQTVS_SceneLoader(String pathlist) {
		new MQTVSSceneLoader(pathlist);
		//return ("Pathlist only");

	}
	/*  */


	public void run(String arg) {

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

		Java2.setSystemLookAndFeel();
//		JFileChooser fc = new JFileChooser();
//		File loadFile = new File("MQTVS_scene.scn") ;
//		fc.setDialogTitle( "Select a saved *_scene.scn MQTVS Scene file" );
//		fc.setSelectedFile(loadFile);
//		int dialogResult = fc.showOpenDialog(null);
		OpenDialog od = new OpenDialog("Select a saved *_scene.scn MQTVS Scene file", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return;
		String inPath = directory + name;
		if (inPath != null) {
			try {
				file = new File(inPath);
				if ( file != null) {
					if ( file.getPath().toLowerCase().contains("scene.scn") ) {

						//if (IJ.debugMode) IJ.log(file.getPath() );
						BufferedReader in = new BufferedReader(
								new FileReader(file));
						String line = in.readLine();
						//if (IJ.debugMode) IJ.log(line);
						while (!line.contains("End of parameter list")) {
							line = in.readLine();
							//if (IJ.debugMode) IJ.log(line);
							String[] lineSegments = line.split(" = ");

							if (lineSegments[0].toLowerCase().contains(".mov")  || lineSegments[0].toLowerCase().contains(".avi")) {
								movieCount = movieCount +1;
								movieFileList = movieFileList + lineSegments[0]	+ "|";
								//if (IJ.debugMode) IJ.log(movieFileList);
								movieSliceDepthList = movieSliceDepthList + lineSegments[1] + "|";
								movieAdjustmentFileList = movieAdjustmentFileList + lineSegments[2] + "|";
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
								if (lineSegments[1].contains("true") ) {
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



						}
					}
					if (movieFileList.contains("\\") ) {
						//IJ.log (movieFileList + "Win");
						//			IJ.log (movieAdjustmentFileList);

					} else if  (movieFileList.contains("/") ) {
						//IJ.log (movieFileList + "Mac");
						//			IJ.log (movieAdjustmentFileList);

					}

					if ( !IJ.isWindows() ) {
						movieFileList = movieFileList.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
						movieFileList = movieFileList.replace('\\', '/');
						//if (IJ.debugMode) IJ.log(movieFileList + "WinToMac");			
						movieAdjustmentFileList = movieAdjustmentFileList.replaceAll("Q:", "/Volumes/GLOWORM_DATA");
						movieAdjustmentFileList = movieAdjustmentFileList.replace('\\', '/');
					} else {
						movieFileList = movieFileList.replaceAll("/Volumes/GLOWORM_DATA", "Q:" );
						movieFileList = movieFileList.replace('/', '\\');
						//if (IJ.debugMode) IJ.log(movieFileList	+ "MacToWin");
						movieAdjustmentFileList = movieAdjustmentFileList.replaceAll("/Volumes/GLOWORM_DATA", "Q:" );
						movieAdjustmentFileList = movieAdjustmentFileList.replace('/', '\\');
					}

					String[] path = movieFileList.split("\\|");				// Makes String[] array of all of the file paths to movies using | as the separator to split with.
					String[] movieSliceDepth =  movieSliceDepthList.split("\\|");
					if ( (path[0] != null) && (movieSliceDepth[0] != null) ) {
						int[] movieSliceDepthValue = new int[movieSliceDepth.length];
						for ( int i=0; i < movieSliceDepthValue.length; i++) {
							movieSliceDepthValue[i] = Integer.valueOf(movieSliceDepth[i]).intValue();
						}
						String[] movieAdjustmentFile = movieAdjustmentFileList.split("\\|"); 
						ImagePlus imp = openAsVirtualStack(path, movieSliceDepthValue, movieAdjustmentFile, grayscale);		
						if (lineageLCDFilePath != "" && lineageMapImagePath != "") {

							IJ.open(lineageMapImagePath);

							ImagePlus lineageMapImage = WindowManager.getCurrentImage();
							if (lineageMapImage !=null) {
								lineageMapImage.getWindow().setBackground(imp.getWindow().getBackground());
								if (imp!=null) ((MultiQTVirtualStack) imp.getStack()).setLineageMapImage(lineageMapImage);
								ImagePlus lineageLinkedImp = imp;
								String lineageMacro = IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/LineageTreeBrowseMacro.txt");
								IJ.runMacro(lineageMacro, lineageMapImage.getTitle() + "|" + lineageLCDFilePath+ "|" + lineageLinkedImp.getID());
							}							

						}
					}
				}
			}	
			catch (IOException ev)
			{
				if (IJ.debugMode) IJ.log("I/O Error: Cannot read from specified directory/file.");
				try { BrowserLauncher.openURL
					("mailto:support@gloworm.org?" +
							"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
							"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
							"&body=MQTVS_SceneLoader.run" + ev.toString());
				}
				catch (IOException ev2) {}

			}
		}	
	}




	ImagePlus openAsVirtualStack(String[] paths, int[] movieSlices, String[] adjustmentFileNames, boolean eightBit) throws IOException {		//Called in main method once the path is known, if boolean virtualStack is true.  I made path a String array.
		ArrayList<String> qtPaths = new ArrayList<String>();
		ArrayList<String> tifPaths = new ArrayList<String>();
		ArrayList<String> dirPaths = new ArrayList<String>();
		for (String path:paths) {
			if ( (path.toLowerCase().endsWith(".mov") || path.toLowerCase().endsWith(".avi"))
					&& new File(path).exists())
				qtPaths.add(path);
			else if ( path.toLowerCase().endsWith(".tif")
					&& new File(path).exists())
				tifPaths.add(path);
			else if ( new File(path).isDirectory()
					&& new File(path).exists())
				dirPaths.add(path);
		}
		try {
			QTFile[] mqtf = new QTFile[qtPaths.size()];						// mqtf  is  now  an  array,  filled  by  the for loop through the array of paths.
			for (int p = 0; p < qtPaths.size(); p++) {
				mqtf[p] = new QTFile(qtPaths.get(p));
				//if (IJ.debugMode) IJ.log(mqtf[p].toString());

			}

			if (mqtf[0]==null && tifPaths.size() == 0 && dirPaths.size() == 0) 
				return null;
			String movieCountStr = (mqtf.length + tifPaths.size() + dirPaths.size())+ "-Movie Overlay #"+ (WindowManager.getImageCount() + 1) ;
			if (mqtf.length > 0 && !QTSession.isInitialized())
				QTSession.open();

			ImagePlus imp = null;
			VirtualStack vstack = null;

			//if (IJ.debugMode) IJ.log(" \n" + movieCountStr+ ":");
			vstack = new MultiQTVirtualStack(mqtf, tifPaths, dirPaths, movieSlices, eightBit, imp, stretchToFitOverlay, viewOverlay, sideSideStereo, redCyanStereo, horizontal, grid, false);		

			if ( mqtf.length == 1 || ((redCyanStereo || sideSideStereo) && mqtf.length == 2)) {
				((MultiQTVirtualStack) vstack).imp.setTitle( ((MultiQTVirtualStack) vstack).getVirtualStack(0).getMovieName());
			} else {
				((MultiQTVirtualStack) vstack).imp.setTitle(movieCountStr+" : see Multi-Channel Controller for details");
			}
			imp = ((MultiQTVirtualStack) vstack).imp;
			

			if (virtualHyperStack && ( ((MultiQTVirtualStack) vstack).imp != null) ) {
				imp = convertStackToHS(imp, vstack, mqtf);
			}

			if (!virtualHyperStack) imp.show();
			Calibration cal = imp.getCalibration();
			cal.pixelDepth= ((MultiQTVirtualStack) vstack).getSliceDepth();


			/************ NEED A WAY HERE TO LOAD ADJUSTMENT FILES DIRECTLY: COPIED CODE FROM MAF_MOD [or could CREATE (STATIC) METHOD IN MAF_MOD] *********************/
			if ( ((MultiQTVirtualStack) vstack).imp != null) {
				WindowManager.setCurrentWindow(imp.getWindow() ) ;

				CompositeImage ci = null;

				if ((((MultiQTVirtualStack) vstack).imp != null) && (((MultiQTVirtualStack) vstack).imp.isComposite())) {
					ci = new CompositeImage(((MultiQTVirtualStack) vstack).imp, 1);
				}



				for ( int j=0 ; j < adjustmentFileNames.length ; j++) {

					//if (IJ.debugMode) IJ.log("Loading Adjustments for Channel " + (j+1) + " " + ((MultiQTVirtualStack) vstack).getVirtualStack(j).getMovieFile().toString() );

					try {
						File adjFile;
						if (!IJ.isWindows() ) {
							//if (IJ.debugMode) IJ.log("Macintosh or Linux");
							adjFile = new File( "" + file.getParent() +  System.getProperty("file.separator") +adjustmentFileNames[j]  );
						} else {
							//if (IJ.debugMode) IJ.log("Windows");
							//if (IJ.debugMode) IJ.log( "" + file.getParent() +  "\\" +adjustmentFileName[j]  );
							adjFile = new File( "" + file.getParent() +  "\\" +adjustmentFileNames[j]  );
						}	

						if (IJ.debugMode) IJ.log(adjFile.getPath() );
						BufferedReader in = new BufferedReader(
								new FileReader(adjFile));
						String line = in.readLine();
						//if (IJ.debugMode) IJ.log(line);

						double displayMin = 0;
						double displayMax = 255;
						double scaleX = 0;
						double scaleY = 0;
						double shiftX = 0;
						double shiftY = 0;								

						String[] lineSegments;

						if (line != null) {
							while (line != null && !line.contains("End of parameter list")) {
								line = in.readLine();

								//if (IJ.debugMode) IJ.log(line);

								if (line != null) {
									lineSegments = line.split(" = ");
									if (lineSegments[0].contains("LUT")) {
										IJ.run("Stop Animation", "");
										((MultiQTVirtualStack) vstack).imp.setPosition(j + 1, imp.getSlice(), imp.getFrame());

										IJ.run(lineSegments[1]);

										for (int i = 0; i < channelLUTItems.length; i++) {
											
											if (channelLUTItems[i].contains(lineSegments[1])) {
												((MultiQTVirtualStack) vstack).setChannelLUTIndex(j, i);
//												mafMOD
//												.setChannelLUTChoice(j,
//														i);
											}
										}
										//if (IJ.debugMode) IJ.log("***Setting Channel " + imp.getChannel() + " LUT to " + lineSegments[1]);    /******** THIS READS OUT THE CORRECT CHANNEL NUMBER *******/
										//									IJ.doCommand("Start Animation [\\]");

									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("DisplayRangeMin")) {
										IJ.run("Stop Animation", "");
										displayMin = Double
										.parseDouble(lineSegments[1]);
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("DisplayRangeMax")) {
										IJ.run("Stop Animation", "");
										displayMax = Double
										.parseDouble(lineSegments[1]);
									}
									if (imp.isComposite()) {
										((CompositeImage) ((MultiQTVirtualStack) vstack).imp)
										.setPosition(
												j + 1,
												((MultiQTVirtualStack) vstack).imp
												.getSlice(),
												((MultiQTVirtualStack) vstack).imp
												.getChannel());
										((CompositeImage) ((MultiQTVirtualStack) vstack).imp)
										.setDisplayRange(displayMin,
												displayMax);
										((CompositeImage) ((MultiQTVirtualStack) vstack).imp)
										.updateChannelAndDraw();
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("FlipVertical")) {
										if (lineSegments[1].contains("true")) {
											((MultiQTVirtualStack) vstack)
											.flipSingleMovieVertical(j);
//											mafMOD.setFlipVCB(j, true);
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("FlipHorizontal")) {
										if (lineSegments[1].contains("true")) {
											((MultiQTVirtualStack) vstack)
											.flipSingleMovieHorizontal(j);
//											mafMOD.setFlipHCB(j, true);
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("FlipZaxis")) {
										if (lineSegments[1].contains("true")) {
											((MultiQTVirtualStack) vstack)
											.flipSingleMovieZaxis(j);
//											mafMOD.setFlipZCB(j, true);
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("ShiftZ")) {
										int shiftZ = Integer
										.parseInt(lineSegments[1]);
										int zShiftNet = shiftZ;
										boolean forward = false;
										if (true) {
											((MultiQTVirtualStack) vstack).setShiftSingleMovieZPosition(j, zShiftNet);
										} else {
											//if (IJ.debugMode) IJ.log("no shift in Z loaded");
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("ShiftT")) {
										int shiftT = Integer
										.parseInt(lineSegments[1]);
										int tShiftNet = shiftT;
										boolean forward = false;
										if (true) {
											((MultiQTVirtualStack) vstack).setShiftSingleMovieTPosition(j, tShiftNet);
										} else {
											//if (IJ.debugMode) IJ.log("no shift in T loaded");
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("deltaZ")) {
										int deltaZ = Integer
										.parseInt(lineSegments[1]);
										if (true) {
//											mafMOD.setDeltaZSpinner(j, deltaZ);
											((MultiQTVirtualStack) vstack).setRelativeZFrequency(j, deltaZ);

											//if (IJ.debugMode) IJ.log("YES deltaZ in Z loaded");
											/*	if (vstack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) vstack).setRelativeZFrequency(j, deltaZ);
											 */
										} else {
											//if (IJ.debugMode) IJ.log("no deltaZ in Z loaded");
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("deltaT")) {
										int deltaT = Integer
										.parseInt(lineSegments[1]);
										if (true) {
//											mafMOD.setDeltaTSpinner(j, deltaT);
											((MultiQTVirtualStack) vstack).setRelativeFrameRate(j, deltaT);
											//if (IJ.debugMode) IJ.log("YES deltaT in T loaded");
											/*	if (vstack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) vstack).setRelativeFrameRate(j, deltaT);
											 */
										} else {
											//if (IJ.debugMode) IJ.log("no deltaT in T loaded");
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("ScaleX")) {
										scaleX = Double
										.parseDouble(lineSegments[1]);
										if (true) {
//											mafMOD.setScaleXSpinner(j, ScaleX);

											//if (IJ.debugMode) IJ.log("YES ScaleX loaded");

										} else {
											//if (IJ.debugMode) IJ.log("no ScaleX loaded");
										}
									}
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("ScaleY")) {
										scaleY = Double
										.parseDouble(lineSegments[1]);
										if (true) {
//											mafMOD.setScaleYSpinner(j, ScaleY);
											((MultiQTVirtualStack) vstack).setSingleMovieScale(j, scaleX, scaleY);

											//if (IJ.debugMode) IJ.log("YES ScaleY loaded");

										} else {
											//if (IJ.debugMode) IJ.log("no ScaleY loaded");
										}
									}
									/*if (vstack instanceof MultiQTVirtualStack ) 
										((MultiQTVirtualStack) vstack).setSingleMovieScale(j, 
											ScaleX, 
											ScaleY);	 
									 */
									if (lineSegments != null
											&& lineSegments[0]
											                .contains("RotationAngle")) {
										double rotAngle = Double
										.parseDouble(lineSegments[1]);
										if (true) {
//											mafMOD.setRotateAngleSpinner(j,rotAngle);
											((MultiQTVirtualStack) vstack).setSingleMovieRotationAngle(j, rotAngle);

											//if (IJ.debugMode) IJ.log("YES RotationAngle loaded");
											/*	if (vstack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) vstack).setSingleMovieRotationAngle(j, rotAngle);
											 */
										} else {
										}
									}
									if (lineSegments != null && lineSegments[0].contains("ShiftX") ) {
										shiftX = Double.parseDouble(lineSegments[1]);
										if (true ) {
//											mafMOD.setTranslateXSpinner(j, shiftX);

											//if (IJ.debugMode) IJ.log("YES ShiftX loaded");										
										} else {
											//if (IJ.debugMode) IJ.log("no shift in X loaded");
										}
									}	
									if (lineSegments != null && lineSegments[0].contains("ShiftY") ) {
										shiftY = Double.parseDouble(lineSegments[1]);
										if (true ) {
//											mafMOD.setTranslateYSpinner(j, shiftY);											
											((MultiQTVirtualStack) vstack).setSingleMovieTranslate(j, shiftX, shiftY);

											//if (IJ.debugMode) IJ.log("YES ShiftY loaded");
										} else {
											//if (IJ.debugMode) IJ.log("no shift in Y loaded");
										}
									}	
									/*if (vstack instanceof MultiQTVirtualStack ) 
									((MultiQTVirtualStack) vstack).setSingleMovieTranslate(j, 
										shiftX, 
										-shiftY );
									 */
								}

							}	
						} else lineSegments = null;

						in.close();

					}	
					catch (IOException ev)
					{
						if (IJ.debugMode) IJ.log("I/O Error: Cannot read from specified directory/file.");
						try { BrowserLauncher.openURL
							("mailto:support@gloworm.org?" +
									"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
									"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
									"&body=MQTVSSceneLoader.openAsVirtualStack" + ev.toString());
						}
						catch (IOException ev2) {}
						//System.exit(0);
					}
					
				}

			}	


			/*************************************************************************************************************************************************/


			//			IJ.run("Brightness/Contrast...");
			Dimension screenDimension = IJ.getScreenSize();
			////			WindowManager.getFrame("Display").setLocation(screenDimension.width-(165),100);
			//			if (WindowManager.getFrame("Too Much Info ;) Window") != null)
			//				WindowManager.getFrame("Too Much Info ;) Window").setBounds(350,screenDimension.height-225,850,225);


			Frame iwf = imp.getWindow();
			//if (IJ.debugMode) IJ.log(iwf.toString() + " is the FrontWindow iwf");
			if (iwf != null) { 
				Dimension d = iwf.getSize() ;
				iwf.setBounds(20,100,d.width, d.height +10);
//				while (100+iwf.getSize().height > screenDimension.height-190) {
//					IJ.run("Out [-]");
//				}
				Frame mcf = imp.getMultiChannelController();
				if (mcf != null) {
					Dimension dmc = mcf.getSize() ;
					mcf.setBounds(screenDimension.width-dmc.width, ((d.height+120 < screenDimension.height -50)? d.height+120: screenDimension.height-50),dmc.width, dmc.height);
					//					mcf.setVisible(true);

				}
			}
//			WindowManager.getFrame("Too Much Info ;) Window").toFront();

			if 	( ((MultiQTVirtualStack) vstack).imp != null ){
				if (file != null) imp.setTitle(file.getName());
				MultiChannelController mcc = imp.getMultiChannelController();
				if (mcc == null) {
					imp.setMultiChannelController(new MultiChannelController(imp));
					mcc = imp.getMultiChannelController();
					mcc.setVisible(false);
				}else
					mcc.setVisible(false);

				if 	( ((MultiQTVirtualStack) vstack).imp.getNChannels() > 1 ) {
					((MultiQTVirtualStack) vstack).imp.setPosition(2,1,1);
					((MultiQTVirtualStack) vstack).imp.setPosition(2,1,2);
					((MultiQTVirtualStack) vstack).imp.setPosition(1,1,2);
				}

				if ( zPosition == 1 && tPosition == 1 ) {
					//IJ.doCommand("Start Animation [\\]");
				} else {
					((MultiQTVirtualStack) vstack).imp.setPosition(cPosition, zPosition - 1, tPosition);
					for (int q = 0; q < ((MultiQTVirtualStack) vstack).imp.getNChannels(); q++) {
						((MultiQTVirtualStack) vstack).imp.setPosition(((MultiQTVirtualStack) vstack).imp.getNChannels()-q, zPosition, tPosition);

					}
					((MultiQTVirtualStack) vstack).imp.setPosition(cPosition, zPosition, tPosition);

					//IJ.doCommand("Start Animation [\\]");

				}
				//				RoiManager rm = new RoiManager(imp, false);
				//				IJ.run("Tag Manager...");
				RoiManager rm = imp.getRoiManager();
				if (roiFileName != null) {
					File roiFile = new File(file.getPath().substring(0, file.getPath().indexOf("MQTVS")) + roiFileName); 
					if (roiFile.exists()) {
						//						imp.setRoiManager(rm);
						if (rm == null) {
							imp.setRoiManager(new RoiManager(imp, true));
							rm = imp.getRoiManager();
						}

						rm.runCommand("Open", roiFile.getPath());
						rm.setZSustain(ZsustainROIs);
						rm.setTSustain(TsustainROIs);
						rm.runCommand("show all");
						rm.runCommand("associate", "true");

						if (roiName != null) {
							int n = rm.getListModel().getSize();
							for (int i=0; i<n; i++) {
								//IJ.log(rm.getList().getItems()[i]);
								if (((String) rm.getListModel().get(i)).matches(roiName+"[CZT]*")) {
									//rm.select(i);
									// this needs to run on a separate thread, at least on OS X
									// "update2" does not clone the ROI so the "Show All"
									// outline moves as the user moves the RO.
									//IJ.log("Selection = " +roiName +" "+ i);
									new ij.macro.MacroRunner("roiManager('select', "+i+", "+imp.getID()+");");
								}
							}
						}
						//						rm.setVisible(false);
					}
				}
				if (clFileName != null) {
					String clStr = IJ.openAsString(file.getPath().substring(0, file.getPath().lastIndexOf("MQTVS")) + clFileName);
					ColorLegend cl = new ColorLegend(imp, clStr);
				}
				
			} else {
				IJ.error("CytoSHOW Error", "Sorry, but one or more movies specified could not be accessed.");
				try { BrowserLauncher.openURL
					("mailto:support@gloworm.org?" +
							"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
							"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
							"&body=MQTVSSceneLoader.openAsVirtualstack" + 
							"CytoSHOW Error:" + "Sorry, but one or more movies specified could not be accessed.");
				}
				catch (IOException ev2) {}

			}
			
			return imp;



		} catch(QTException qte) {
			if (IJ.debugMode) IJ.log("I/O Error: Cannot read from specified movie file.");
			IJ.error(qte.getMessage());
			try { BrowserLauncher.openURL
				("mailto:support@gloworm.org?" +
						"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
						"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
						"&body=MQTVSSceneLoader.openAsVirtualstack" + qte.getMessage());
			}
			catch (IOException ev2) {}


		}
		return null;
	}




	public static ImagePlus convertStackToHS(ImagePlus imp, VirtualStack vstack, QTFile[] mqtf) {


		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		imp.setStack(vstack, nChannels, nSlices, nFrames);
		int stackSize = imp.getImageStackSize();
		if (stackSize==1) {
			IJ.error("MultiQTVirtualStack to HyperStack", "SceneLoader error: Stack required");
			return imp;
		}
		if (imp.getBitDepth()==24) {

		}

		nChannels = mqtf.length;
		nSlices = ((MultiQTVirtualStack) vstack).maxSlicesSingleMovie;
		nFrames = ((MultiQTVirtualStack) vstack).maxTimesSingleMovie;
		//		IJ.log(nChannels+" "+nSlices+" "+nFrames+" "+stackSize);
		if (nChannels*nSlices*nFrames!=stackSize) {
			IJ.error("HyperStack Converter", "channels x slices x frames <> stack size");
			return imp;
		}

		imp.setDimensions(nChannels, nSlices, nFrames);		//This sets the entered dimensions in the ImagePlus.



		ImagePlus imp2 = imp;						
		if (nChannels>0 && imp.getBitDepth()!=24) {
			imp2 = new CompositeImage(imp, displayMode);		
			//if (IJ.debugMode) IJ.log( "displayMode =" + displayMode );
		}
		imp2.setOpenAsHyperStack(true);
		new StackWindow(imp2, true);

		if (imp!=imp2) imp.hide();
		((MultiQTVirtualStack) vstack).imp = imp2;
		imp2.show();
		//Why doesn't this calibration work?
		Calibration cal = imp2.getCalibration();
		cal.pixelDepth= ((MultiQTVirtualStack) vstack).getSliceDepth();

		if (imp.isComposite()) ((CompositeImage)imp2).setMode(displayMode);
		return imp2;

	}


}