package org.vcell.gloworm;
//You could start with QTVirtualStack (http://rsb.info.nih.gov/ij/plugins/download/QTVirtualStack.java), 
//change the name to something like MultiQTVirtualStack and 
//change the constructor to take an array of QTFile instead of a single one. 
//Assuming n QTFiles are passed to the constructor, MultiQTVirtualStack would need to open n QTVirtualStacks. 
//The getProcessor() method would then need to be modified to get images from the appropriate QTVirtualStack. 
//It's all pretty straightforward.


import ij.*;
import ij.gui.*;
import ij.io.FileInfo;
import ij.io.TiffDecoder;
import ij.VirtualStack;
import ij.plugin.BrowserLauncher;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.FolderOpener;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import quicktime.app.view.MoviePlayer;
import quicktime.app.view.QTImageProducer;
import quicktime.io.OpenMovieFile;
import quicktime.io.QTFile;
import quicktime.qd.QDDimension;
import quicktime.std.StdQTConstants;
import quicktime.std.clocks.TimeRecord;
import quicktime.std.movies.Movie;
import quicktime.std.movies.TimeInfo;
import quicktime.std.movies.Track;
import quicktime.QTException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.ColorModel;
import java.awt.image.PixelGrabber;
import java.io.File;
import java.io.IOException;


public class MultiQTVirtualStack extends VirtualStack {
	private MoviePlayer player;						//Why did Wayne change these from "final private"
	private QTImageProducer imageProducer;			//to "private" alone?  Is it just style? Woody vs Wayne? Or does it have any actual impact?
	QTFile[] mqtf = null;
	private int height;								
	private int width;
	private ArrayList frameLocations = new ArrayList();		
	private boolean eightBit;
	private boolean stretchToFitOverlay;
	private boolean viewOverlay;
	private boolean horizontal;  
	private boolean sideSideStereo, redCyanStereo;
	private boolean forward, vertical, grid;   // Adding the concept of grid grouping for movies. Still not implemented on 10/17/10
	private boolean firstDisplay = true;
	private boolean[] flipSingleMovieStackOrder;
	private boolean[] flipSingleMovieStackVertical;
	private boolean[] flipSingleMovieStackHorizontal;


	private boolean[] thirtySixSlices;				
	private boolean[] twentySevenSlices;				

	protected VirtualStack[] vStackArray;							// Declare this here to make it global so getProcessor can act on it.
	protected ImagePlus[] channelImpArray;
	public ImagePlus imp;
	private int[] lengths;
	private int[] widths;
	private int[] heights;
	private int[] nSlicesSingleMovie;
	private int[] relativeFrameRateSingleMovie;
	private int[] relativeZFrequencySingleMovie;
	private String[] frameRateChoices, ZFrequencyChoices;

	private int maxWidth ;
	private int maxHeight ; 
	public int maxLength ;
	public int maxSlicesSingleMovie ;
	public int maxTimesSingleMovie ;
	public int maximumRelativeFrameRate;
	public int maximumRelativeZFrequency;

	private int compositeChannel;
	private int lastChannel;  //A non-zero nonsense integer value to trigger initiation of the channel order in getProcessor.
	private int lastSlice;
	private int lastFrame;
	private String[] channelLUTName;
	private int[] channelLUTIndex;

	private int[] shiftZPosition;
	private int[] shiftTPosition;
	private double[] translateX;
	private double[] translateY;
	private double[] scaleX;
	private double[] scaleY;
	private double[] rotateAngle;
	private double sliceDepth;
	private double[] nmdxySingleMovie;
	private double[] nmdzSingleMovie;
	private double[] dzdxyRatio;
	private int minWidth;
	private int minHeight;
	private ImagePlus lineageMapImage;
	private boolean firstCall;
	private boolean secondCall;
	private boolean rotateNinetyX;
	private String sceneFileText;
	private String sceneFileName;

	public MultiQTVirtualStack(String[] args) {
		
	}
	
	public MultiQTVirtualStack(int i, int j, ColorModel rgBdefault, String string, boolean b, Color black) {
		super(i, j, rgBdefault,string, b,black);
	}

	public MultiQTVirtualStack(QTFile[] mqtf, boolean eightBit) {		//constructor for QTVirtualStack now taking an array of QtFiles.
		this.eightBit = eightBit;									//!!!Assuming n QTFiles are passed to the constructor, MultiQTVirtualStack would need to open n QTVirtualStacks.
		
		this.mqtf = mqtf;
		try {														// !!! So this idea just  has  me  opening  an Array of QTVirtual stacks
			vStackArray = new VirtualStack[mqtf.length];		
			for (int sqtf = 0; sqtf < mqtf.length ; sqtf++) {
				vStackArray[sqtf] = new QTVirtualStack(mqtf[sqtf], this.eightBit);		

				// This builds  a  giant  ArrayList  of  all frameLocations from all the QTVirtualStacks 
				// among other things, it properly enables the getSize() method to return the full length of the movie.
				this.frameLocations.addAll( ((QTVirtualStack) vStackArray[sqtf]).getFrameLocations() );		
			} 



		}
		catch (RuntimeException qte) {
			if (IJ.debugMode) IJ.log("I/O error: cannot open movie file.");
			/*  		 try { BrowserLauncher.openURL
			 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=MultiQTVirtualStack" + "Z"+
			 		qte.getMessage());
		 }
		 catch (IOException ev2) {}
			 */		 

		}
	}

	public MultiQTVirtualStack(QTFile[] mqtf, ArrayList<String> tifPaths, ArrayList<String> dirPaths, int[] movieSlices, boolean eightBit, ImagePlus imp, 
			boolean stretchToFitOverlay, boolean viewOverlay, 
			boolean sideSideStereo, boolean redCyanStereo, boolean horizontal, boolean grid, boolean rotateNinetyX, String sceneFileName, String sceneFileText) throws IOException {		//constructor for QTVirtualStack now taking an array of QtFiles.
		this.eightBit = eightBit;									//!!!Assuming n QTFiles are passed to the constructor, MultiQTVirtualStack would need to open n QTVirtualStacks.
		this.rotateNinetyX = rotateNinetyX;
		this.mqtf = mqtf;
		this.stretchToFitOverlay = stretchToFitOverlay;
		this.viewOverlay = viewOverlay;
		this.horizontal = horizontal;
		this.sideSideStereo = sideSideStereo;
		this.redCyanStereo = redCyanStereo;
		this.grid = grid;
		if (this.sideSideStereo && this.redCyanStereo) 
			this.redCyanStereo = false;
		if (this.sideSideStereo) {
			this.grid = true;
			if (this.mqtf.length <=4) 
				this.horizontal = true;
			else 
				if (viewOverlay) this.horizontal = true;
				else this.horizontal = false;


		}
		this.imp = ((ImagePlus) imp);
		this.sceneFileText = sceneFileText;
		this.sceneFileName = sceneFileName;

		try {														// !!! So this idea just  has  me  opening  an Array of QTVirtual stacks
			vStackArray = new VirtualStack[this.mqtf.length + tifPaths.size() + dirPaths.size()];		
			channelImpArray = new ImagePlus[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			widths = new int[this.mqtf.length + tifPaths.size() + dirPaths.size()];		
			lengths = new int[this.mqtf.length + tifPaths.size() + dirPaths.size()];		
			heights = new int[this.mqtf.length + tifPaths.size() + dirPaths.size()];	
			flipSingleMovieStackOrder = new boolean[this.mqtf.length + tifPaths.size() + dirPaths.size()] ;	
			flipSingleMovieStackVertical = new boolean[this.mqtf.length + tifPaths.size() + dirPaths.size()] ;
			flipSingleMovieStackHorizontal = new boolean[this.mqtf.length + tifPaths.size() + dirPaths.size()] ;

			maxWidth = 0;
			maxHeight = 0;
			minWidth = 1000000;
			minHeight = 1000000;
			maxLength = 0;
			nSlicesSingleMovie = new int[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			setRelativeFrameRateSingleMovie(new int[this.mqtf.length + tifPaths.size() + dirPaths.size()]);
			thirtySixSlices = new boolean[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			twentySevenSlices = new boolean[this.mqtf.length + tifPaths.size() + dirPaths.size()];

			channelLUTName = new String[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			channelLUTIndex = new int[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			shiftZPosition = new int[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			shiftTPosition = new int[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			translateX = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			translateY = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			scaleX = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			scaleY = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			rotateAngle = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			nmdxySingleMovie = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			nmdzSingleMovie = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			dzdxyRatio = new double[this.mqtf.length + tifPaths.size() + dirPaths.size()];
			


			setRelativeZFrequencySingleMovie(new int[this.mqtf.length + tifPaths.size() + dirPaths.size()]);
			boolean[] metaDataNaming = new boolean[this.mqtf.length + tifPaths.size() + dirPaths.size()];

			GenericDialog gd = new GenericDialog("Convert to HyperStack: " );

			for (int sqtf = 0; sqtf < (this.mqtf.length + tifPaths.size() + dirPaths.size()) ; sqtf++) {
				nSlicesSingleMovie[sqtf] = 1;
				if (sqtf < this.mqtf.length){
					try {  
						OpenMovieFile qtMovieFile = OpenMovieFile.asRead(this.mqtf[sqtf]);
						Movie movie  = Movie.fromFile(qtMovieFile);
						Track visualTrack = movie.getIndTrackType (1,
								StdQTConstants.visualMediaCharacteristic,
								StdQTConstants.movieTrackCharacteristic);
						QDDimension d = visualTrack.getSize();
						lengths[sqtf] = visualTrack.getMedia().getSampleCount();
						if (lengths[sqtf] > maxLength) maxLength = lengths[sqtf];
						if (this.mqtf[sqtf].getName().matches(".*_pry?xy?_.*") && rotateNinetyX) 
							widths[sqtf]  = d.getHeight();
						else 
							widths[sqtf]  = d.getWidth();
						if (widths[sqtf] > maxWidth) maxWidth = widths[sqtf];
						if (widths[sqtf] < minWidth) minWidth = widths[sqtf];
						if (this.mqtf[sqtf].getName().matches(".*_pry?xy?_.*") && rotateNinetyX) 
							heights[sqtf]  = d.getWidth();
						else 
							heights[sqtf]  = d.getHeight();
						if (heights[sqtf] > maxHeight) maxHeight = heights[sqtf];
						if (heights[sqtf] < minHeight) minHeight = heights[sqtf];
						getRelativeFrameRateSingleMovie()[sqtf]  = 1;
						getRelativeZFrequencySingleMovie()[sqtf]  = 1;

						String movieFileName = this.mqtf[sqtf].getName();
						String[] nameChunks = this.mqtf[sqtf].getName().split("_");

						metaDataNaming[sqtf] = (movieFileName.contains("_au") 
								&& movieFileName.contains("_date") 
								&& movieFileName.contains("_imgsys"));

						/*Code here establishes ZT dimensions for  each  movie */

						nSlicesSingleMovie[sqtf] = movieSlices[sqtf];

						if (!metaDataNaming[sqtf]) {
							ArrayList possibleSlices = new ArrayList();
							for (int slc = 1; slc < lengths[sqtf] +1; slc++) {
								if ( lengths[sqtf] % slc == 0) possibleSlices.add(new Integer(slc));

							}
							String[] ps = new String[possibleSlices.size()];
							for ( int p = 0; p < possibleSlices.size(); p++) {
								ps[p] = ((Integer)possibleSlices.get(p)).toString();

							} 
							gd.addMessage("Parameters for \n" + this.mqtf[sqtf] + " ?");
							gd.addChoice("Slices Ch" + (sqtf+1) +" (z):", ps,  (Arrays.binarySearch(ps, "36") != -1) ? "36" :  ps[0] );
							thirtySixSlices[sqtf] = false;
							if (Arrays.binarySearch(ps, "36") != -1) thirtySixSlices[sqtf] = true;
							twentySevenSlices[sqtf] = false;
							if (Arrays.binarySearch(ps, "27") != -1) twentySevenSlices[sqtf] = true;
							nmdxySingleMovie[sqtf] = 1;
							nmdzSingleMovie[sqtf] = 1;
						} else {
							if (IJ.debugMode) IJ.log("got into metaDataNaming");
							for (String nameChunk: nameChunks) {
								if (nameChunk.startsWith("z") ) {
									if (IJ.debugMode) IJ.log("found z setting in name = " + nameChunk );
									nSlicesSingleMovie[sqtf] = Integer.parseInt( nameChunk.substring(1) );
									if (IJ.debugMode) IJ.log("" + nSlicesSingleMovie[sqtf]);
									gd.addMessage("Parameters for \n" + nameChunks[0] + nameChunks[1] + ":");
									gd.addMessage("Slices Ch" + (sqtf+1) +" (z):" + nSlicesSingleMovie[sqtf]);
								}
								else if (nameChunk.startsWith("nmdxy") ) {
									if (IJ.debugMode) IJ.log(""+ Double.parseDouble( nameChunk.substring(5) ));
									nmdxySingleMovie[sqtf] = Double.parseDouble( nameChunk.substring(5) );
								}
								if (nameChunk.startsWith("nmdz") ) {
									if (IJ.debugMode) IJ.log(""+ Double.parseDouble( nameChunk.substring(4) ));
									nmdzSingleMovie[sqtf] = Integer.parseInt( nameChunk.substring(4) );
								}
							}
						} 

						dzdxyRatio[sqtf] = nmdzSingleMovie[sqtf]/nmdxySingleMovie[sqtf];
						sliceDepth = Math.max(sliceDepth, dzdxyRatio[sqtf]);

					}			
					catch (QTException qte) {
						if (IJ.debugMode) IJ.log("I/O error: cannot open movie file.");
						/*				   		 try { BrowserLauncher.openURL
							 ("mailto:support@gloworm.org?" +
							 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
							 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
							 		"&body=MultiQTVirtualStack" + "A"+
							 		qte.getMessage());
						 }
						 catch (IOException ev2) {}
						 */						 

					}
				} else if (sqtf - this.mqtf.length < tifPaths.size()) {  //case for tiffs
					int tifNum  = sqtf - this.mqtf.length;
					vStackArray[sqtf] = ((VirtualStack)new FileInfoVirtualStack(
											new TiffDecoder("", tifPaths.get(tifNum)).getTiffInfo()[0], false));
				} else if (sqtf - this.mqtf.length - tifPaths.size() < dirPaths.size()) {  //case for dirs
					int dirNum  = sqtf - this.mqtf.length - tifPaths.size();
					vStackArray[sqtf] = ((VirtualStack)FolderOpener.open(tifPaths.get(dirNum)).getStack());
				}
			}

			if ( (this.mqtf.length != 1  || ( !thirtySixSlices[0]  && !twentySevenSlices[0] )) && !(redCyanStereo) ) {	/************* added 052710 **************** THIS IS A VERY UGLY HACK, AND WILL OCCASIONALLY MISDISPLAY MOVIES WHOSE SLICE COUNT DOESN'T ACTUALLY =36 *****************/
				if (IJ.debugMode) IJ.log("" + this.mqtf.length + thirtySixSlices[0] + twentySevenSlices[0] + "A"  + this.mqtf[0].getName() );
				for (int sqtf = 0; sqtf < this.mqtf.length ; sqtf++) {					
					if (!metaDataNaming[sqtf] && nSlicesSingleMovie[sqtf] == 0) { 
						gd.showDialog(); 
						break;
					}
				}

				if (gd.wasCanceled()) return;


				for (int sqtf = 0; sqtf < this.mqtf.length ; sqtf++) {
					if (!metaDataNaming[sqtf]) {
						String selection = gd.getNextChoice();					
						if (nSlicesSingleMovie[sqtf]== 0) nSlicesSingleMovie[sqtf] = Integer.valueOf(selection).intValue();
					}
					if (nSlicesSingleMovie[sqtf] > maxSlicesSingleMovie) maxSlicesSingleMovie = nSlicesSingleMovie[sqtf];

					if ( lengths[sqtf]/nSlicesSingleMovie[sqtf] > maxTimesSingleMovie ) maxTimesSingleMovie = lengths[sqtf]/nSlicesSingleMovie[sqtf] ;

					if (this.mqtf.length > 1) if (IJ.debugMode) IJ.log("Channel" + (sqtf+1) + " w" + widths[sqtf] + " h" + heights[sqtf] + 
							" z" + nSlicesSingleMovie[sqtf]  + " t" + lengths[sqtf]/nSlicesSingleMovie[sqtf]  +
							" L" + lengths[sqtf]  + " " + this.mqtf[sqtf]); 

				}

			} else if ( (this.mqtf.length == 1) && thirtySixSlices[0] && ( this.mqtf[0].getName().contains("prx") ||  this.mqtf[0].getName().contains("pry") 
					||  this.mqtf[0].getName().toLowerCase().contains("gp")  ||  this.mqtf[0].getName().toLowerCase().contains("yp") )  ) {				/******** added 090810 ******************* THIS IS A VERY UGLY HACK, AND WILL OCCASIONALLY MISDISPLAY MOVIES WHOSE SLICE COUNT DOESN'T ACTUALLY =36 *****************/
				if (IJ.debugMode) IJ.log("" + this.mqtf.length + thirtySixSlices[0] + twentySevenSlices[0]+"B" + this.mqtf[0].getName() );
				nSlicesSingleMovie[0] = 36;
				maxSlicesSingleMovie = 36;
				maxTimesSingleMovie = lengths[0]/nSlicesSingleMovie[0] ;
			} else if (this.mqtf.length == 2 && thirtySixSlices[0] && redCyanStereo && viewOverlay) {
				if (IJ.debugMode) IJ.log("should now open dragdropredcyanstereo");
				nSlicesSingleMovie[0] = 36;
				nSlicesSingleMovie[1] = 36;
				maxSlicesSingleMovie = 36;
				maxTimesSingleMovie = lengths[0]/nSlicesSingleMovie[0] ;
				if (IJ.debugMode) IJ.log("finished block");
				//
				//			} else if ( (this.mqtf.length == 1) && twentySevenSlices[0] && ( this.mqtf[0].getName().contains("slc") )  ) {				/******** added 090810 ******************* THIS IS A VERY UGLY HACK, AND WILL OCCASIONALLY MISDISPLAY MOVIES WHOSE SLICE COUNT DOESN'T ACTUALLY =36 *****************/
				//				if (IJ.debugMode) IJ.log("" + this.mqtf.length + thirtySixSlices[0] + twentySevenSlices[0]+"C"  + this.mqtf[0].getName() );
				//				nSlicesSingleMovie[0] = 27;
				//				maxSlicesSingleMovie = 27;
				//				maxTimesSingleMovie = lengths[0]/nSlicesSingleMovie[0] ;
				//
			} else {				/******** added 052710 ******************* THIS IS A VERY UGLY HACK, AND WILL OCCASIONALLY MISDISPLAY MOVIES WHOSE SLICE COUNT DOESN'T ACTUALLY =36 or 27 *****************/
				/*****WORKING 101810*********************TRYING TO REVISE THIS FINAL ELSE STATEMENT TO ASK THE USER FOR THE CORRECT SLICE NUMBER!!!!!*/
				for (int sqtf = 0; sqtf < this.mqtf.length ; sqtf++) {					
					if (!metaDataNaming[sqtf] && nSlicesSingleMovie[sqtf] == 0) { 
						gd.showDialog(); 
						break;
					}
				}

				if (gd.wasCanceled()) return;


				for (int sqtf = 0; sqtf < this.mqtf.length ; sqtf++) {
					if (!metaDataNaming[sqtf]) {
						String selection = gd.getNextChoice();					
						if (nSlicesSingleMovie[sqtf]== 0) 
							nSlicesSingleMovie[sqtf] = Integer.valueOf(selection).intValue();
					}
					if (nSlicesSingleMovie[sqtf] > maxSlicesSingleMovie) 
						maxSlicesSingleMovie = nSlicesSingleMovie[sqtf];

					if ( lengths[sqtf]/nSlicesSingleMovie[sqtf] > maxTimesSingleMovie ) 
						maxTimesSingleMovie = lengths[sqtf]/nSlicesSingleMovie[sqtf] ;

				}
			}




			/*******/				
			maximumRelativeZFrequency = getRelativeZFrequencySingleMovie()[0];   // start with the first value
			for (int i=1; i<getRelativeZFrequencySingleMovie().length; i++) {
				if (getRelativeZFrequencySingleMovie()[i] > maximumRelativeZFrequency) {
					maximumRelativeZFrequency = getRelativeZFrequencySingleMovie()[i];   // new maximum
				}
			}

			maximumRelativeFrameRate = getRelativeFrameRateSingleMovie()[0];   // start with the first value
			for (int i=1; i<getRelativeFrameRateSingleMovie().length; i++) {
				if (getRelativeFrameRateSingleMovie()[i] > maximumRelativeFrameRate) {
					maximumRelativeFrameRate = getRelativeFrameRateSingleMovie()[i];   // new maximum
				}
			}
			/*******/


			for (int sqtf = 0; sqtf < this.mqtf.length ; sqtf++) {
				try {  

					if (viewOverlay && !sideSideStereo) {
						//if (IJ.debugMode) IJ.log("ViewOverlay");
						vStackArray[sqtf] = new QTVirtualStack(this.imp, this.mqtf[sqtf], this.eightBit, maxWidth, maxHeight, maxLength, 
								maxSlicesSingleMovie, nSlicesSingleMovie[sqtf], maxTimesSingleMovie, 
								this.stretchToFitOverlay, 1, 0, this.horizontal, this.grid);
					}else if (this.redCyanStereo) {
						//if (IJ.debugMode) IJ.log("RCStereo");
						vStackArray[sqtf] = new QTVirtualStack(this.imp, this.mqtf[sqtf], this.eightBit, maxWidth, maxHeight, maxLength, 
								maxSlicesSingleMovie, nSlicesSingleMovie[sqtf], maxTimesSingleMovie, 
								this.stretchToFitOverlay, this.mqtf.length/2, Math.round(sqtf/2), this.horizontal, this.grid);
					}else if (this.sideSideStereo  && !viewOverlay) {
						//if (IJ.debugMode) IJ.log("SxSStereo");
						vStackArray[sqtf] = new QTVirtualStack(this.imp, this.mqtf[sqtf], this.eightBit, maxWidth, maxHeight, maxLength, 
								maxSlicesSingleMovie, nSlicesSingleMovie[sqtf], maxTimesSingleMovie, 
								this.stretchToFitOverlay, this.mqtf.length, sqtf, this.horizontal, this.grid);
					}else if (sideSideStereo && viewOverlay) {
						//if (IJ.debugMode) IJ.log("SxSStereo");
						vStackArray[sqtf] = new QTVirtualStack(this.imp, this.mqtf[sqtf], this.eightBit, maxWidth, maxHeight, maxLength, 
								maxSlicesSingleMovie, nSlicesSingleMovie[sqtf], maxTimesSingleMovie, 
								this.stretchToFitOverlay, 2, (sqtf%2), this.horizontal, this.grid);

					}else{
						//if (IJ.debugMode) IJ.log("Otherwise...");
						vStackArray[sqtf] = new QTVirtualStack(this.imp, this.mqtf[sqtf], this.eightBit, maxWidth, maxHeight, maxLength, 
								maxSlicesSingleMovie, nSlicesSingleMovie[sqtf], maxTimesSingleMovie, 
								this.stretchToFitOverlay, this.mqtf.length, sqtf, this.horizontal, this.grid);
					}

					// This builds  a  giant  ArrayList  of  all frameLocations from all the QTVirtualStacks 
					// among other things, it properly enables the getSize() method to return the full length of the movie.
					this.frameLocations.addAll( ((QTVirtualStack) vStackArray[sqtf]).getFrameLocations() );		
					this.flipSingleMovieStackOrder[sqtf] = false;
//					channelImpArray[sqtf] = new ImagePlus("test", ((QTVirtualStack) vStackArray[sqtf]));
//					channelImpArray[sqtf].setOpenAsHyperStack(true);
//					channelImpArray[sqtf].setDimensions(1, maxTimesSingleMovie, maxSlicesSingleMovie);
//					channelImpArray[sqtf].show();
//					channelImpArray[sqtf].getWindow().setVisible(false);
				}
				catch (RuntimeException qte) {
					IJ.error("error", qte.toString()+"\n"+qte.getMessage());
					if (IJ.debugMode) IJ.log("I/O error: cannot open movie file.");
					/*					 try { BrowserLauncher.openURL
						 ("mailto:support@gloworm.org?" +
						 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
						 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
						 		"&body=MultiQTVirtualStack " + "B"+
						 		qte.getMessage());
					 }
					 catch (IOException ev2) {}
					 */

				}
			} 
			if (this.mqtf.length > 1) if (IJ.debugMode) IJ.log("Overlay   " + " w" + maxWidth + " h" + maxHeight + " z" +  maxSlicesSingleMovie + 
					" t" + maxTimesSingleMovie + " L" + maxLength  + " " + "Window Summary for All Movies");			
			/*************************************/		
			this.imp = new ImagePlus((this.mqtf.length +" : see Multi-Channel Controller for details"), this);
			/****	this.imp.show();				*/
			//if (IJ.debugMode) IJ.log( this.imp.toString() + this.imp.getNChannels());				  
			/*************************************/

		}
		catch (RuntimeException qte) {
			if (IJ.debugMode) IJ.log("I/O error: cannot open movie file.");
			/*		 try { BrowserLauncher.openURL
			 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=MultiQTVirtualStack" + "C"+
			 		qte.getMessage());
		 }
		 catch (IOException ev2) {}
			 */		 

		}

	}


	//these methods are all inherited from VirtualStack (and likely from Stack).

	public void setSceneFileText(String sceneFileText) {
		this.sceneFileText = sceneFileText;
	}

	public void setSceneFileName(String sceneFileName) {
		this.sceneFileName = sceneFileName;
	}

	public int getMaxWidth() {
		return maxWidth;
	}

	public void setMaxWidth(int maxWidth) {
		this.maxWidth = maxWidth;
	}

	public int getMaxHeight() {
		return maxHeight;
	}

	public void setMaxHeight(int maxHeight) {
		this.maxHeight = maxHeight;
	}

	public double getSliceDepth() {
		return sliceDepth;
	}

	public void setSliceDepth(double sliceDepth) {
		this.sliceDepth = sliceDepth;
	}

	public String getChannelLUTName(int channel) {
		return channelLUTName[channel];
	}

	public void setChannelLUTName(int channel, String channelLUTName) {
		this.channelLUTName[channel] = channelLUTName;
	}

	public int getChannelLUTIndex(int channel) {
		if (channelLUTIndex.length != 1)
			return channelLUTIndex[channel];
		else if (channelLUTIndex[0] == 0)
//			return 3;
			return channelLUTIndex[channel];
		else
//			return 3;
			return channelLUTIndex[channel];

	}

	public void setChannelLUTIndex(int channel, int channelLUTIndex) {
		this.channelLUTIndex[channel] = channelLUTIndex;
	}


	public int getHeight() {			//calculated above to fit all movies into window
		return this.maxHeight;
	}


	public int getWidth() {			//calculated above to fit all movies into window
		return this.maxWidth;
	}


	public int getSize() {			//assembled above to count all frames in all movies
		return this.frameLocations.size();
	}


	public String getSliceLabel(int slice) {
		slice--;  // ImageJ slices are 1-based rather than zero based.
		return "";
	}


	public int getRelativeZFrequency(int channelBaseZero) {

		return this.getRelativeZFrequencySingleMovie()[channelBaseZero];

	}

	public void setRelativeZFrequency(int channelBaseZero, int relativeZFrequency) {

		this.getRelativeZFrequencySingleMovie()[channelBaseZero] = relativeZFrequency;
		this.maximumRelativeZFrequency = this.getRelativeZFrequencySingleMovie()[0];   // start with the first value
		for (int i=1; i<getRelativeZFrequencySingleMovie().length; i++) {
			if (this.getRelativeZFrequencySingleMovie()[i] > this.maximumRelativeZFrequency) {
				this.maximumRelativeZFrequency = this.getRelativeZFrequencySingleMovie()[i];   // new maximum
			}
		}
	}

	public int getRelativeFrameRate(int channelBaseZero) {
		return this.getRelativeFrameRateSingleMovie()[channelBaseZero];
	}

	public void setRelativeFrameRate(int channelBaseZero, int relativeFrameRate) {

		this.getRelativeFrameRateSingleMovie()[channelBaseZero] = relativeFrameRate;
		this.maximumRelativeFrameRate = 0;   
		for (int c=0; c<imp.getNChannels(); c++) {
			if (this.getRelativeFrameRateSingleMovie()[c] > this.maximumRelativeFrameRate) {
				this.maximumRelativeFrameRate = this.getRelativeFrameRateSingleMovie()[c];   // new maximum
			}
		}
	}


	public QTVirtualStack getVirtualStack(int number){
		if (vStackArray == null)
			return null;
		return (QTVirtualStack)vStackArray[number];
	}

	public int getChannelNSlices(int number) {
		return nSlicesSingleMovie[number];
	}


	public ImageProcessor getProcessor(int slice) {
//		if (true) {
//			ImageProcessor iptest = ((QTVirtualStack) vStackArray[0]).getProcessor(slice);
//			return iptest;
//		}
		//if(stack.length == 1) return ((QTVirtualStack) stack[0]).getProcessor(slice);
		
		if (this.imp == null) compositeChannel = 0;

		if (this.imp != null) {				//It is actually null the first time getProcessor is called, so this condition will be needed!!



			int c = this.imp.getChannel();
			int z = this.imp.getSlice();
			int trueZ =  this.imp.getSlice();
			int t = this.imp.getFrame();
			int nChannels = this.imp.getNChannels();
			int nSlices = this.imp.getNSlices();
			int nFrames = this.imp.getNFrames();

			if ( (this.imp instanceof CompositeImage) && (((CompositeImage) this.imp).getMode() ==1) && this.imp.getNChannels()>1 ) {  /*  Note that these adjustments must not be made if there is only one channel, hence the final boolean test if the if statement*/
				if (lastChannel != c ) {  /** To deal with the troublesome Case of Switching channel-selector focus in a composite image. 
										Seems to send one call to getProcessor.**/
					compositeChannel = -1;
					firstCall = true;
				}

				if (lastSlice!=z || lastFrame!=t){
					compositeChannel= -1;
					firstCall = false;
					secondCall = false;
				}
				if (secondCall){
					compositeChannel= -1;
					firstCall = false;
					secondCall = false;
				}
//				IJ.log(""+compositeChannel+" "+lastChannel+" "+lastSlice+" "+lastFrame);
				if (compositeChannel>0 && flipSingleMovieStackOrder[compositeChannel-1]) {	
					z = nSlices - (z - 1);
				}

				slice = ((compositeChannel <1) ? 1 : (compositeChannel-1) )*nSlices*nFrames 
				+ (z-1)*((compositeChannel <1) ? 1 : this.getRelativeZFrequencySingleMovie()[compositeChannel-1])/this.maximumRelativeZFrequency*nFrames 
				+ t*((compositeChannel <1) ? 1 : this.getRelativeFrameRateSingleMovie()[compositeChannel-1])/this.maximumRelativeFrameRate;

//				if (compositeChannel >0)
//					channelImpArray[compositeChannel-1].setPosition(1, t, z);
				
				if (lastChannel == c /* && lastSlice == z && lastFrame == t */) 
					compositeChannel++ ; 
				if ( (compositeChannel == nChannels+1) ) {
					compositeChannel = -1;
					//if (IJ.debugMode) IJ.log("-----------------fc");
					secondCall = false;
					firstCall = false;
				}
				lastChannel = c; lastSlice = trueZ; lastFrame = t;

			} else {
//				IJ.log("thinks it's NOT composite");

				if ((flipSingleMovieStackOrder[c-1])) {	/* Allows for a channel to be flipped in Z order of slices*/
					z = nSlices - (z - 1);
				}

				slice = (c-1)*nSlices*nFrames 
				+ (z-1)*((c == 0) ? 1 : this.getRelativeZFrequencySingleMovie()[c-1])/this.maximumRelativeZFrequency*nFrames 
				+ t*((c == 0) ? 1 : this.getRelativeFrameRateSingleMovie()[c-1])/this.maximumRelativeFrameRate;	
			}		  
		}

		if ( slice < 1 || slice > this.frameLocations.size() ) {             //slice is decremented by one in QTVirtualStack, so I don't do it here.;
			//		if (IJ.debugMode) IJ.log(""+ slice);	
			slice = 1;								//corrects for a value out of range.  Might be more elegant...?Is it even needed?
			//if (IJ.debugMode) IJ.log( "mqtvs_gp B " + slice);				
		}
		try {															

			ImageProcessor ip = new ColorProcessor(maxWidth,maxHeight);
			ip.setColor(Color.black);
			ip.fill();

			for (int sqtvs = 0 ; sqtvs <  vStackArray.length; sqtvs++ ) {	
//				IJ.log("looping");
				if (slice > ((QTVirtualStack) vStackArray[sqtvs]).getSize()) {

					slice -= ((QTVirtualStack) vStackArray[sqtvs]).getSize();

					continue;
				} else {
					//if (IJ.debugMode) IJ.log( "mqtvs_gp C " + slice);				

					if (this.imp != null)
						/*if (IJ.debugMode) IJ.log( "slicefixed = "+ slice + "; stack Index = "+ this.imp.getStackIndex(imp.getChannel(), imp.getSlice(), imp.getFrame()) + " Channel " + (sqtvs +1) ) */;
//					ip = channelImpArray[sqtvs].getProcessor();
					ImageProcessor channelIP  = ((QTVirtualStack) vStackArray[sqtvs]).getProcessor(slice);
					if (stretchToFitOverlay) {
						channelIP = channelIP.resize(maxWidth, maxHeight, false);
					}
					ip.insert((this.mqtf[sqtvs].getName().matches(".*_pry?xy?_.*") && rotateNinetyX)?channelIP.rotateRight():channelIP,0,0);
					 
					break;
				}		
			}



			return ip;
		}
		catch(Exception e) {
			if (IJ.debugMode) IJ.log("I/O error: cannot open movie file.");
			/* 		 try { BrowserLauncher.openURL
			 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=MultiQTVirtualStack" + "D"+
			 		e.getMessage());
		 }
		 catch (IOException ev2) {}
			 */		 

			throw new RuntimeException(e);

		}
	}
	
	public ImageProcessor getUnmodifiedProcessor(int slice) {
//		if (true) {
//			ImageProcessor iptest = ((QTVirtualStack) vStackArray[0]).getProcessor(slice);
//			return iptest;
//		}
		//if(stack.length == 1) return ((QTVirtualStack) stack[0]).getProcessor(slice);
		
		if (this.imp == null) compositeChannel = 0;

		if (this.imp != null) {				//It is actually null the first time getProcessor is called, so this condition will be needed!!



			int c = this.imp.getChannel();
			int z = this.imp.getSlice();
			int trueZ =  this.imp.getSlice();
			int t = this.imp.getFrame();
			int nChannels = this.imp.getNChannels();
			int nSlices = this.imp.getNSlices();
			int nFrames = this.imp.getNFrames();

			if ( (this.imp instanceof CompositeImage) && (((CompositeImage) this.imp).getMode() ==1) && this.imp.getNChannels()>1 ) {  /*  Note that these adjustments must not be made if there is only one channel, hence the final boolean test if the if statement*/
				if (lastChannel != c ) {  /** To deal with the troublesome Case of Switching channel-selector focus in a composite image. 
										Seems to send one call to getProcessor.**/
					compositeChannel = -1;
					firstCall = true;
				}

				if (lastSlice!=z || lastFrame!=t){
					compositeChannel= -1;
					firstCall = false;
					secondCall = false;
				}
				if (secondCall){
					compositeChannel= -1;
					firstCall = false;
					secondCall = false;
				}
//				IJ.log(""+compositeChannel+" "+lastChannel+" "+lastSlice+" "+lastFrame);
				if (compositeChannel>0 && flipSingleMovieStackOrder[compositeChannel-1]) {	
					z = nSlices - (z - 1);
				}

				slice = ((compositeChannel <1) ? 1 : (compositeChannel-1) )*nSlices*nFrames 
				+ (z-1)*((compositeChannel <1) ? 1 : this.getRelativeZFrequencySingleMovie()[compositeChannel-1])/this.maximumRelativeZFrequency*nFrames 
				+ t*((compositeChannel <1) ? 1 : this.getRelativeFrameRateSingleMovie()[compositeChannel-1])/this.maximumRelativeFrameRate;

//				if (compositeChannel >0)
//					channelImpArray[compositeChannel-1].setPosition(1, t, z);
				
				if (lastChannel == c /* && lastSlice == z && lastFrame == t */) 
					compositeChannel++ ; 
				if ( (compositeChannel == nChannels+1) ) {
					compositeChannel = -1;
					//if (IJ.debugMode) IJ.log("-----------------fc");
					secondCall = false;
					firstCall = false;
				}
				lastChannel = c; lastSlice = trueZ; lastFrame = t;

			} else {
//				IJ.log("thinks it's NOT composite");

				if ((flipSingleMovieStackOrder[c-1])) {	/* Allows for a channel to be flipped in Z order of slices*/
					z = nSlices - (z - 1);
				}

				slice = (c-1)*nSlices*nFrames 
				+ (z-1)*((c == 0) ? 1 : this.getRelativeZFrequencySingleMovie()[c-1])/this.maximumRelativeZFrequency*nFrames 
				+ t*((c == 0) ? 1 : this.getRelativeFrameRateSingleMovie()[c-1])/this.maximumRelativeFrameRate;	
			}		  
		}

		if ( slice < 1 || slice > this.frameLocations.size() ) {             //slice is decremented by one in QTVirtualStack, so I don't do it here.;
			//		if (IJ.debugMode) IJ.log(""+ slice);	
			slice = 1;								//corrects for a value out of range.  Might be more elegant...?Is it even needed?
			//if (IJ.debugMode) IJ.log( "mqtvs_gp B " + slice);				
		}
		try {															

			ImageProcessor channelIP = null;
			
			for (int sqtvs = 0 ; sqtvs <  vStackArray.length; sqtvs++ ) {	
//				IJ.log("looping");
				if (slice > ((QTVirtualStack) vStackArray[sqtvs]).getSize()) {

					slice -= ((QTVirtualStack) vStackArray[sqtvs]).getSize();

					continue;
				} else {
					//if (IJ.debugMode) IJ.log( "mqtvs_gp C " + slice);				

					if (this.imp != null)
						/*if (IJ.debugMode) IJ.log( "slicefixed = "+ slice + "; stack Index = "+ this.imp.getStackIndex(imp.getChannel(), imp.getSlice(), imp.getFrame()) + " Channel " + (sqtvs +1) ) */;
//					ip = channelImpArray[sqtvs].getProcessor();
					channelIP  = ((QTVirtualStack) vStackArray[sqtvs]).getProcessor(slice);
					if (stretchToFitOverlay) {
						channelIP = channelIP.resize(maxWidth, maxHeight, false);
					}
					if (this.mqtf[sqtvs].getName().matches(".*_pry?xy?_.*") && rotateNinetyX)
						channelIP = channelIP.rotateRight();

					break;
				}		
			}

			return channelIP;
		}
		catch(Exception e) {
			if (IJ.debugMode) IJ.log("I/O error: cannot open movie file.");
			/* 		 try { BrowserLauncher.openURL
			 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=MultiQTVirtualStack" + "D"+
			 		e.getMessage());
		 }
		 catch (IOException ev2) {}
			 */		 

			throw new RuntimeException(e);

		}
	}


	/* I don't know if this works in the VirtualHyperStack mode... */
	public void deleteSlice(int slice) {
		slice--;  // ImageJ slices are 1-based rather than zero based.
		frameLocations.remove(slice);
		for ( VirtualStack sqtvs : vStackArray ) {	
			//if (IJ.debugMode) IJ.log("got into the for loop");
			if (slice > ((QTVirtualStack) sqtvs).getSize()) {
				slice -= ((QTVirtualStack) sqtvs).getSize();
				continue;
			} else {
				((QTVirtualStack) sqtvs).deleteSlice(slice +1);			//slice was already decremented above, will be again in QTVirtualStack.deleteSlice, so reset to original value.		
				break;
			}

		}

	}

	public void deleteMovie(int slice) {
		// An idea waiting for its time....
	}

	/*These methods allow viewer to shift the Z or T positions of a given channel to better synchronize movies.*/
	public void adjustSingleMovieZ(int channel, int shiftSingleMovieZPosition) {
		((QTVirtualStack) vStackArray[channel]).shiftMovieZ(shiftSingleMovieZPosition);
		//if (IJ.debugMode) IJ.log("got to MQTVS.ASMZ");

	}

	public void adjustSingleMovieT(int channel, int shiftSingleMovieTPosition) {
		((QTVirtualStack) vStackArray[channel]).shiftMovieT(shiftSingleMovieTPosition);		
		//if (IJ.debugMode) IJ.log("got to MQTVS.ASMT");

	}

	public int getShiftSingleMovieZPosition(int channel) {
		return shiftZPosition[channel];
	}

	public void setShiftSingleMovieZPosition(int channelBaseZero, int shiftSingleMovieZPosition) {
		this.shiftZPosition[channelBaseZero] = this.shiftZPosition[channelBaseZero] + shiftSingleMovieZPosition;
		this.adjustSingleMovieZ(channelBaseZero, shiftSingleMovieZPosition);
	}

	public int getShiftSingleMovieTPosition(int channel) {
		return shiftTPosition[channel];
	}

	public void setShiftSingleMovieTPosition(int channelBaseZero, int shiftSingleMovieTPosition) {
		this.shiftTPosition[channelBaseZero] = this.shiftTPosition[channelBaseZero] + shiftSingleMovieTPosition;
		this.adjustSingleMovieT(channelBaseZero, shiftSingleMovieTPosition);
	}

	public void flipSingleMovieVertical(int channelBaseZero) {
		//if (IJ.debugMode) IJ.log("got to MQTVS.fSMV");
		((QTVirtualStack) vStackArray[channelBaseZero]).flipMovieVertical();
		flipSingleMovieStackVertical[channelBaseZero] = !flipSingleMovieStackVertical[channelBaseZero];		
	}

	public boolean getFlipSingleMovieStackVertical(int channelBaseZero) {
		return flipSingleMovieStackVertical[channelBaseZero];
	}

	public void flipSingleMovieHorizontal(int channelBaseZero) {
		//if (IJ.debugMode) IJ.log("got to MQTVS.fSMH");
		((QTVirtualStack) vStackArray[channelBaseZero]).flipMovieHorizontal();		
		flipSingleMovieStackHorizontal[channelBaseZero] = !flipSingleMovieStackHorizontal[channelBaseZero];		
	}

	public boolean getFlipSingleMovieStackHorizontal(int channelBaseZero) {
		return flipSingleMovieStackHorizontal[channelBaseZero];
	}

	public void flipSingleMovieZaxis(int channelBaseZero) {		
		flipSingleMovieStackOrder[channelBaseZero] = !flipSingleMovieStackOrder[channelBaseZero];		

	}

	public boolean getFlipSingleMovieStackOrder(int channelBaseZero) {
		return flipSingleMovieStackOrder[channelBaseZero];
	}

	public void setSingleMovieScale(int channelBaseZero, double scaleX, double scaleY) {
		((QTVirtualStack) vStackArray[channelBaseZero]).setScale(  scaleX,  scaleY);
		this.scaleX[channelBaseZero] = scaleX;
		this.scaleY[channelBaseZero] = scaleY;
		
	} 


	public double getSingleMovieScaleX(int channel) {
		return scaleX[channel];
	}

	public double getSingleMovieScaleY(int channel) {
		return scaleY[channel];
	}

	public void  setSingleMovieRotationAngle(int channelBaseZero, double rotateAngle) {
		((QTVirtualStack) vStackArray[channelBaseZero]).setRotationAngle(  rotateAngle);
		this.rotateAngle[channelBaseZero] = rotateAngle;
		
	} 

	public double getSingleMovieRotateAngle(int channel) {
		return rotateAngle[channel];
	}

	public void  setSingleMovieTranslate(int channelBaseZero, double translateX, double translateY) {
		((QTVirtualStack) vStackArray[channelBaseZero]).setTranslate(translateX,  translateY);
		this.translateX[channelBaseZero] = translateX;
		this.translateY[channelBaseZero] = translateY;
		
	}


	public double getSingleMovieTranslateX(int channel) {
		return translateX[channel];
	}

	public double getSingleMovieTranslateY(int channel) {
		return translateY[channel];
	}

	public Boolean getIsEightBit() {
		return eightBit;
	}

	public Boolean getIsVirtualStack() {
		return true;
	}

	public Boolean getIsMultipleMovies() {
		return true;
	}

	public Boolean getIsHyperStack() {
		return true;
	}

	public Boolean getIsStretchToFit() {
		return stretchToFitOverlay;
	}

	public Boolean getIsViewInOverlay() {
		return viewOverlay;
	}

	public Boolean getIsHorizontalMontage() {
		return horizontal;
	}

	public Boolean getIsSideSideStereo() {
		return sideSideStereo;
	}

	public Boolean getIsRedCyanStereo() {
		return redCyanStereo;
	}

	public Boolean getIsGrid() {
		return grid;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public boolean isEightBit() {
		return eightBit;
	}

	public void setEightBit(boolean eightBit) {
		this.eightBit = eightBit;
	}

	public boolean isViewOverlay() {
		return viewOverlay;
	}

	public void setViewOverlay(boolean viewOverlay) {
		this.viewOverlay = viewOverlay;
	}

	public boolean isSideSideStereo() {
		return sideSideStereo;
	}

	public void setSideSideStereo(boolean sideSideStereo) {
		this.sideSideStereo = sideSideStereo;
	}

	public boolean isRedCyanStereo() {
		return redCyanStereo;
	}

	public void setRedCyanStereo(boolean redCyanStereo) {
		this.redCyanStereo = redCyanStereo;
	}

	public int[] getNSlicesSingleMovie() {
		return nSlicesSingleMovie;
	}

	public void setNSlicesSingleMovie(int[] slicesSingleMovie) {
		nSlicesSingleMovie = slicesSingleMovie;
	}

	public void setRelativeZFrequencySingleMovie(
			int[] relativeZFrequencySingleMovie) {
		this.relativeZFrequencySingleMovie = relativeZFrequencySingleMovie;
	}

	public int[] getRelativeZFrequencySingleMovie() {
		return relativeZFrequencySingleMovie;
	}

	public void setRelativeFrameRateSingleMovie(
			int[] relativeFrameRateSingleMovie) {
		this.relativeFrameRateSingleMovie = relativeFrameRateSingleMovie;
	}

	public int[] getRelativeFrameRateSingleMovie() {
		return relativeFrameRateSingleMovie;
	}

	public String getSceneFileText() {
		return sceneFileText;
	}

	public String getSceneFileName() {
		return sceneFileName;
	}

}

