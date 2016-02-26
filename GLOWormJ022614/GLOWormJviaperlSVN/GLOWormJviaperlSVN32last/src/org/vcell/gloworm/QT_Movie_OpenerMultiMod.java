package org.vcell.gloworm;
import ij.*;
import ij.measure.Calibration;
import ij.plugin.*;
import ij.plugin.frame.PlugInFrame;
//import ij.plugin.frame;
import ij.gui.*;
import ij.io.*;
import ij.process.*;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

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
/**
	Opens a quicktime movie as an RGB stack, an 8-bit grayscale stack, or as a virtual stack
...or as a hyperstack in which several different movies are overlaid upon each other in separate channels.
	Requires QuickTime for Java, part of QuickTime (requires custom install on Windows).

	Changes made to allow compatibility with Java VM 1.4.1 and QTJ 6.1 under Mac OS X 10.3
	by Jeff Hardin, Dept. of Zoololgy, Univ. of Wisconsin, jdhardin@wisc.edu, 11/22/03
	
	Virtual stack support contributed by Jeffrey Woodward on 2008/11/26.
*/
public class QT_Movie_OpenerMultiMod implements PlugIn, QDConstants, StdQTConstants, MovieDrawingComplete {
	
	QTImageProducer qtip;// DEPRECATED!!		So what's the alternative in the QTJava API that's not deprecated??
	QTFile qtf = null;
	QTFile[] mqtf = null;	
	String[] path = null;
	Image javaImage = null;
	MoviePlayer moviePlayer;
	ImageStack stack;
	int i, numFrames, totalFrames, nextTime;
	Image img;		
	String pathlist;
	private boolean grayscale;
	private boolean virtualStack;
	private boolean multiVStack;
	private boolean virtualHyperStack;
	private boolean stretchToFitOverlay;
	private boolean viewOverlay;
	private boolean sideSideStereo, redCyanStereo;
 	private boolean horizontal;
	private boolean grid;
	private String sceneFileText = null;
	private String sceneFileName;
		
	public void run(String arg) {
		if (IJ.is64Bit() && IJ.isMacintosh()) {
			IJ.error("This plugin requires a 32-bit version of Java");
   		 try { BrowserLauncher.openURL
			 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=QTMovieOpenerMultiMod.run" + 
			 		"This plugin requires a 32-bit version of Java");
		 }
		 catch (IOException ev2) {}

			return;
		}
		try {
			Class qts = Class.forName("quicktime.QTSession");
		} catch (Exception e) {
			IJ.error("Requires QuickTime for Java, available as a\n"
						+"custom install with QuickTime 4.0 or later.");
	   		 try { BrowserLauncher.openURL
				 ("mailto:support@gloworm.org?" +
				 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
				 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
				 		"&body=QTMovieOpenerMultiMod.run" + 
				 		"Requires QuickTime for Java, available as a\n"
						+"custom install with QuickTime 4.0 or later.");
			 }
			 catch (IOException ev2) {}

			return;
		}
		
		Frame cmp = WindowManager.getFrame("Composite Palette");
		if (cmp != null) {
			((PlugInFrame)cmp).close();
		}




		GenericDialog gd = new GenericDialog("QT Movie Opener");	//Allows choices of Virtual and 8-bit
		gd.addCheckbox("Convert to 8-bit grayscale", true);
		gd.setInsets(0,30,10);
		gd.addMessage("Reduces memory required by factor of 4");
		gd.addCheckbox("Use Virtual Stack", true);
		gd.addCheckbox("Multiple Movies in same Virtual Stack", true);		//Added to allow for multifile virtualstack Movies.
		gd.addCheckbox("Make HyperStack", true);
		gd.addCheckbox("Stretch Compared Movies to Fit Same Frame?", true);		
		gd.addCheckbox("View in Overlay?", true);		
		gd.addCheckbox("Side-by-Side Stereo, for uncrossed eyes?", false);		
		gd.addCheckbox("Red-Cyan Stereo, for funny glasses?", false);				
		gd.addCheckbox("Horizontal Montage?", true);
		gd.addCheckbox("Grid Layout??", false);		

				
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		grayscale = gd.getNextBoolean();
		virtualStack = gd.getNextBoolean();
		multiVStack = gd.getNextBoolean();
		virtualHyperStack = gd.getNextBoolean();
		stretchToFitOverlay = gd.getNextBoolean();
		viewOverlay = gd.getNextBoolean();
		sideSideStereo  = gd.getNextBoolean();
		redCyanStereo = gd.getNextBoolean();
		horizontal = gd.getNextBoolean();
		grid = gd.getNextBoolean();
		
		if ( pathlist == null) pathlist = "START";


		String directory = "START";
		String name = "START";
		
		if (pathlist == "START") {
			do {  
				boolean firstRun = (name == "START") ;
				OpenDialog od = new OpenDialog("Open QuickTime...", arg);		//I moved OpenDialog after GenericDialog to allow for looping through selection of multiple movies.
				directory = od.getDirectory();
				name = od.getFileName();
				if (firstRun  && name ==null) return;
				if ( firstRun ) {
					pathlist = directory + name;
				} else if (name != null){
					pathlist = pathlist+"|"+directory+name;	/** Why did I use this string approach? Why not just an arraylist? **/
				}		// Will allow me to build concatenated string for all movies needed. Can split it later.
				//if (IJ.debugMode) IJ.log(pathlist +"  "+ multiVStack);

			} while ((name != null) && (multiVStack));				//Loops if multiVstack is true and as long as user continues to select more movie files.
		}
		
		
		if (pathlist==null) return;								// True if no Movie files opened.
		//if (IJ.debugMode) IJ.log(pathlist);
		String[] listPath = pathlist.split("\\|");				// Makes String[] array of all of the file paths to movies using | as the separator to split with later.

		if (sideSideStereo) {
			redCyanStereo = false;
			//viewOverlay = false;
			//horizontal = true;
			//grid = true;
		}
		if (redCyanStereo) {
			sideSideStereo = false;
		}
		
		if (sideSideStereo || redCyanStereo) {
			path = new String[listPath.length*2];
			for (int lp=0; lp<listPath.length; lp++) {
				path[lp*2] = listPath[lp];
				//if (IJ.debugMode) IJ.log(path[lp]);
				path[(lp*2)+1] = listPath[lp]; /**Two copies of each movie for stereo**/
				//if (IJ.debugMode) IJ.log(path[lp+1]);
			}
		} else {
			path = new String[listPath.length];
			for (int lp=0; lp<listPath.length; lp++) {
				path[lp] = listPath[lp];
				//if (IJ.debugMode) IJ.log(path[lp]);
			}
		}

		//if (IJ.debugMode) IJ.log(path[0]);
					
		if (virtualStack) {
			try {
				openAsVirtualStack(path, grayscale);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		//calls method to make a QTVirtualStack, either as RGB or 8-bit.Passes array "path" and boolean "grayscale".
			return;
		}


		try {											//This try catch block executes if virtualStack is false
			QTSession.open();			
			qtf = new QTFile(path[0]);
			IJ.showStatus("Opening \""+name+"\"");
			if (IJ.debugMode) IJ.write("OpenMovieFile.asRead(qtf)");
			OpenMovieFile openMovieFile = OpenMovieFile.asRead(qtf);
			IJ.showProgress(0.0);
			if (IJ.debugMode) IJ.write("Movie.fromFile");
			Movie m = Movie.fromFile (openMovieFile);
			if (IJ.debugMode) IJ.write("m.getTrackCount()");
			int numTracks = m.getTrackCount();
			if (IJ.debugMode) IJ.write("numTracks: "+numTracks);
			int trackMostLikely = 0;
			int trackNum = 0;
			while((++trackNum <= numTracks) && (trackMostLikely==0)) {
				Track imageTrack = m.getTrack(trackNum);
				QDDimension d = imageTrack.getSize();
				if (d.getWidth() > 0) trackMostLikely = trackNum; //first track with width != soundtrack
			}
			if (IJ.debugMode) IJ.write("m.getTrack: "+trackMostLikely);
			Track imageTrack = m.getTrack(trackMostLikely);
			QDDimension d = imageTrack.getSize();
			int width = d.getWidth();
			int height = d.getHeight();
			
			moviePlayer = new MoviePlayer (m); 			
			qtip = new QTImageProducer (moviePlayer, new Dimension(width,height));
			img = Toolkit.getDefaultToolkit().createImage(qtip);
			boolean needsRedrawing = qtip.isRedrawing();
			if (IJ.debugMode) IJ.write("needsRedrawing: "+needsRedrawing);
			int maxTime = m.getDuration();
			//m.setDrawingCompleteProc(movieDrawingCallWhenChanged, this);
                        
			if (IJ.debugMode) IJ.write("Counting frames");
                      
  			TimeInfo timeInfo = new TimeInfo(0, 0);
                        moviePlayer.setTime(0);
                        totalFrames = 0;
                        do {
                            totalFrames++;
                            timeInfo = imageTrack.getNextInterestingTime(nextTimeMediaSample, timeInfo.time, 1f);
                        } while (timeInfo.time > -1);
                       
			int size = (width*height*totalFrames*4)/(1024*1024);
			IJ.showStatus("Allocating "+width+"x"+height+"x"+totalFrames+" stack ("+size+"MB)");
			stack = allocateStack(width, height, totalFrames);
			if (stack==null) {
				QTSession.close();
				if (stack==null)
					IJ.outOfMemory("Movie_Opener");
				return;
			}
			numFrames = totalFrames;
			if (stack.getSize()<numFrames)
				numFrames = stack.getSize();
                        
 			if (IJ.debugMode) IJ.write("Rewinding and reading movie");
                          
                        moviePlayer.setTime(0);
                        i = 0;
  			            nextTime = 0;
                         do {
                           i++;   
                            if (needsRedrawing)
					             qtip.redraw(null);
                            qtip.updateConsumers (null);
                            ImageProcessor ip = new ColorProcessor(img);
                            if (grayscale)
                                    ip = ip.convertToByte(false);
                            stack.setPixels(ip.getPixels(), i);
                            IJ.showStatus((i) + "/" + numFrames);
                            IJ.showProgress((double)(i)/totalFrames);
                            timeInfo = imageTrack.getNextInterestingTime(nextTimeMediaSample, nextTime, 1f);
                            nextTime = timeInfo.time;
                            moviePlayer.setTime(nextTime);
                        } while (nextTime > -1);
			openMovieFile.close();
	 		QTSession.close();
	 	}
		catch(Exception e) {
			QTSession.close();
			IJ.showProgress(1.0);
			String msg = e.getMessage();
			if (msg==null) msg = ""+e;
			if (msg.equals("-108") && IJ.isMacintosh())
				msg += "\n \nTry allocating more memory \nto the ImageJ application.";
			if (!msg.equals("-128"))
				IJ.error("Open movie failed: "+ msg);
			return;
		}
		catch (NoClassDefFoundError e) {
			IJ.error("QuickTime for Java required");
			return;
		}
		
		ImagePlus newImp = new ImagePlus(name, stack);
		newImp.show();
		if (newImp.getNChannels() == 1)
			IJ.run("Grays");
	}

	ImageStack allocateStack(int width, int height, int size) {
		ImageStack stack=null;
		byte[] temp;
		try {
			stack = new ImageStack(width, height);
			int mem = 0;
			for (int i=0; i<size; i++) {
				if (grayscale)
					stack.addSlice(null, new byte[width*height]);
				else
					stack.addSlice(null, new int[width*height]);
				mem += width*height*4;
				//IJ.write((i+1)+"/"+size+" "+mem/1024);
			}
			temp = new byte[width*height*4*5+1000000];
	 	}
		catch(OutOfMemoryError e) {
			if (stack!=null) {
				Object[] arrays = stack.getImageArray();
				if (arrays!=null)
					for (int i=0; i<arrays.length; i++)
				arrays[i] = null;
			}
			stack = null;
		}
		temp = null;
		System.gc();
		System.gc();
		return stack;
	}

	public int execute (Movie m) {
		try {
			qtip.updateConsumers (null);
		} catch (QTException e) {
			return e.errorCode();
		}
		return 0;
	}
	
	void openAsVirtualStack(String[] path, boolean eightBit) throws IOException {		//Called in main method once the path is known, if boolean virtualStack is true.  I made path a String array.
		try {
			mqtf = new QTFile[path.length]; // mqtf  is  now  an  array,  filled  by  the for loop through the array of paths.
			//if (IJ.debugMode) IJ.log(mqtf.length + "--" + path.length);
			for (int p = 0; p < path.length; p++) {
				//if (IJ.debugMode) IJ.log(p +"="+path[p]);
				mqtf[p] = new QTFile(path[p]);
				//if (IJ.debugMode) IJ.log(p+"="+mqtf[p].toString());
			}
			
			if (mqtf[0]==null) return;
			String movieCountStr = "";
			movieCountStr = mqtf.length + "-Movie Overlay #"+ (WindowManager.getImageCount() + 1) ;
			if (!QTSession.isInitialized())
				QTSession.open();
			
			ImagePlus imp = null;
			VirtualStack vstack = null;
			int[] movieSlices = new int[mqtf.length];			
			if (false /*mqtf.length == 1*/) {	
										//!!! Note that QTVirtualStack is a subclass of VirtualStack.
				if (virtualHyperStack) {
					vstack = new MultiQTVirtualStack(mqtf, new ArrayList<String>(), new ArrayList<String>(), movieSlices, eightBit, imp, stretchToFitOverlay, viewOverlay, sideSideStereo, redCyanStereo, horizontal, grid, false, sceneFileName, sceneFileText );	
				} else {
					vstack = new QTVirtualStack(mqtf[0], eightBit);	
				}
				imp = new ImagePlus(mqtf[0].getName(), vstack);			// If only one movie...

//*************IS THERE ANY REASON I CAN'T BUILD THIS imp WITHIN THE CONSTRUCTOR OF MultiQTVirtualStack, RATHER THAN IN THIS LINE OF THIS CODE???				

			} else {
										//!!! Note that MultiQTVirtualStack is a subclass of VirtualStack.
				//if (IJ.debugMode) IJ.log(" \n" + movieCountStr+ ":");
				vstack = new MultiQTVirtualStack(mqtf, new ArrayList<String>(), new ArrayList<String>(), movieSlices, eightBit, imp, stretchToFitOverlay, viewOverlay, sideSideStereo, redCyanStereo, horizontal, grid, false, sceneFileName, sceneFileText);		//!!!!! In MultiQTVirtualStack I need a way to have one stack built from the array of QTFiles.

/*************BUILD THE imp ABOVE WITHIN THE CONSTRUCTOR OF MultiQTVirtualStack, RATHER THAN IN THIS LINE OF THIS CODE	*/			
				if ( ((MultiQTVirtualStack) vstack).imp != null) {
					if (  mqtf.length == 1 || ((redCyanStereo || sideSideStereo) && mqtf.length == 2)) {
						((MultiQTVirtualStack) vstack).imp.setTitle( ((MultiQTVirtualStack) vstack).getVirtualStack(0).getMovieName());

					} else {
					
						((MultiQTVirtualStack) vstack).imp.setTitle(movieCountStr+" : see Multi-Channel Controller for details");
					}
					imp = ((MultiQTVirtualStack) vstack).imp;
					//if (IJ.debugMode) IJ.log( imp.toString() +  " " + imp.getNChannels());	
				}
/**********************************AT THE MOMENT, THIS AIN'T WORKING********************************/
				
			}
			if ( imp != null ) {
				if (virtualHyperStack) {
					convertStackToHS(imp, vstack);
				}

				if (!virtualHyperStack) imp.show();
				if (imp.getNChannels() == 1)
					IJ.run("Grays");
				Calibration cal = imp.getCalibration();
				cal.pixelDepth= ((MultiQTVirtualStack) vstack).getSliceDepth();

				//IJ.run("Channels Tool...");
				//IJ.run("Brightness/Contrast...");
			}


		} catch(QTException qte) {
			IJ.error(qte.getMessage());
	   		 try { BrowserLauncher.openURL
				 ("mailto:support@gloworm.org?" +
				 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
				 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
				 		"&body=QTMovieOpenerMultiMod.openAsVirtualstack" + 
				 		qte.getMessage());
			 }
			 catch (IOException ev2) {}

		}
	}


/* This method borrowed from HyperStackConverter plugin code 
	Allows a checkbox to call forth the Stack to Hyperstack Dialog.
*/
 

	static final int C=0, Z=1, T=2;
    static final int CZT=0, CTZ=1, ZCT=2, ZTC=3, TCZ=4, TZC=5;
 	static final String[] orders = {"xytzc(default)", "xyctz", "xyzct", "xyztc", "xytcz", "xyczt"};
	static int order = CZT;
	
    void convertStackToHS(ImagePlus imp, VirtualStack vstack) {
 
		
		int nChannels = imp.getNChannels();
        int nSlices = imp.getNSlices();
        int nFrames = imp.getNFrames();
        int stackSize = imp.getImageStackSize();
		int mode;
        if (stackSize==1) {
            IJ.error("MultiQTVirtualStack to HyperStack", "MovieOpener error: Stack required");
            return;
        }
        if (imp.getBitDepth()==24) {
			//******************These lines Must not be run, or there are errors when Using RGB Color in VirtualHyperStacks**********************//
            //new CompositeConverter().run("color");		            
			//return;
        }
        String[] modes = {"Composite", "Color", "Grayscale"};
        GenericDialog gd = new GenericDialog("Convert MultiQTVirtualStack to HyperStack");
        gd.addChoice("Order:", orders, orders[order]);

        gd.addNumericField("Channels (c):", mqtf.length, 0);
		if ( vstack instanceof MultiQTVirtualStack) {
			gd.addNumericField("Slices (z):", ((MultiQTVirtualStack) vstack).maxSlicesSingleMovie, 0);
			gd.addNumericField("Frames (t):", ((MultiQTVirtualStack) vstack).maxTimesSingleMovie, 0);
		}
		if ( vstack instanceof QTVirtualStack) {
			gd.addNumericField("Slices (z):", ((QTVirtualStack) vstack).getSize(), 0);
			gd.addNumericField("Frames (t):", 1, 0);
		}

		
		gd.addChoice("Display Mode:", modes, modes[0]);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        order = gd.getNextChoiceIndex();
        nChannels = (int) gd.getNextNumber();
        nSlices = (int) gd.getNextNumber();
        nFrames = (int) gd.getNextNumber();
        mode = gd.getNextChoiceIndex();
		//if (IJ.debugMode) IJ.log(nChannels +" "+ nSlices +" "+ nFrames +" "+ nChannels*nSlices*nFrames +" "+ stackSize);
        if (nChannels*nSlices*nFrames!=stackSize) {
            IJ.error("HyperStack Converter", "channels x slices x frames <> stack size");
            return;
        }

        imp.setDimensions(nChannels, nSlices, nFrames);		//This sets the entered dimensions in the ImagePlus.
		
		
		/*  I now need to ask my MultiQTVirtualStack to fix its components in the QTVirtualStack arraylists 
		by calling a new method in MultiQTVirtualSlack*/

        if (order!=CZT && imp.getStack().isVirtual()) {
/*
            IJ.error("HyperStack Converter", "Virtual stacks must by in XYCZT order.");
*/
        } else {
            //shuffle(imp, order);
			ImagePlus imp2 = imp;						
            if (nChannels>0 && imp.getBitDepth()!=24) {
            	if (nChannels ==1) {
            		imp2 = new CompositeImage(imp, CompositeImage.GRAYSCALE);
            	}else{
            		imp2 = new CompositeImage(imp, mode+1);		/************************ For some reason, this doesn't build the right kind of window using CompositeWindow*************************************/
            	}
            }
            imp2.setOpenAsHyperStack(true);
            new StackWindow(imp2, true);
            if (imp!=imp2) imp.hide();
			((MultiQTVirtualStack) vstack).imp = imp2;
			((MultiQTVirtualStack) vstack).imp.show();
			if (imp.getNChannels() == 1)
				IJ.run("Grays");
			//if (IJ.debugMode) IJ.log(" " +  ((MultiQTVirtualStack) vstack).imp.toString() + ((MultiQTVirtualStack) vstack).imp.getNChannels() +"from mqtvs.getP via opener" );
			//Why doesn't this calibration work?
			Calibration cal = imp2.getCalibration();
			cal.pixelDepth= ((MultiQTVirtualStack) vstack).getSliceDepth();

			
			IJ.run("Brightness/Contrast...");
			Dimension screenDimension = IJ.getScreenSize();
			WindowManager.getFrame("Display").setLocation(screenDimension.width-(165),100);
			if (WindowManager.getFrame("Too Much Info ;) Window") != null)
				WindowManager.getFrame("Too Much Info ;) Window").setBounds(0,screenDimension.height-225,850,200);
			
		
			Frame iwf = WindowManager.getCurrentWindow();
			//if (IJ.debugMode) IJ.log(iwf.toString() + " is the FrontWindow iwf");
			Dimension d = iwf.getSize() ;
			iwf.setBounds(20,100,d.width, d.height +10);
			if (imp.getNChannels() >= 1) {
				if (!redCyanStereo && !(sideSideStereo && viewOverlay) ) {
					for (int c=0; c<((MultiQTVirtualStack) vstack).imp.getNChannels(); c++) {
						if (viewOverlay) ((MultiQTVirtualStack) vstack).setChannelLUTIndex(c, c) ;
						if (!viewOverlay)  {
							((MultiQTVirtualStack) vstack).setChannelLUTIndex(c, 3);
							((MultiQTVirtualStack) vstack).imp.setPosition( c+1, imp.getSlice(), imp.getFrame() );
							IJ.run("Grays");
						}

					}	
				}
				if (redCyanStereo) {
					for (int c=0; c<((MultiQTVirtualStack) vstack).imp.getNChannels(); c = c+2) {
						((MultiQTVirtualStack) vstack).imp.setPosition( c+1, imp.getSlice(), imp.getFrame() );
						IJ.run("Red");
						((MultiQTVirtualStack) vstack).imp.setPosition( c+2, imp.getSlice(), imp.getFrame() );
						if ( c==0) IJ.run("Cyan");
						if ( c==2 ) {
							if (viewOverlay  ) IJ.run("Green");
							else  IJ.run("Cyan");
						}
						if ( c==4 ) {
							if (viewOverlay  ) IJ.run("Blue");
							else  IJ.run("Cyan");
						}
						
						((MultiQTVirtualStack) vstack).setChannelLUTIndex(c, 0) ;
						((MultiQTVirtualStack) vstack).setChannelLUTIndex(c+1, 4) ;
						if ( c==2 && viewOverlay ) ((MultiQTVirtualStack) vstack).setChannelLUTIndex(c+1, 1) ;
						if ( c==4 && viewOverlay  ) ((MultiQTVirtualStack) vstack).setChannelLUTIndex(c+1, 2) ;

						((MultiQTVirtualStack) vstack).adjustSingleMovieZ(c, 1);
					}
				}
				if (sideSideStereo)  {
					for (int c=0; c<((MultiQTVirtualStack) vstack).imp.getNChannels(); c = c+2) {
						if ( viewOverlay) {
							((MultiQTVirtualStack) vstack).setChannelLUTIndex(c, c/2) ;
							((MultiQTVirtualStack) vstack).setChannelLUTIndex(c+1, c/2) ;

							((MultiQTVirtualStack) vstack).imp.setPosition( c+1, imp.getSlice(), imp.getFrame() );
							if ( c==0 && ((MultiQTVirtualStack) vstack).imp.getNChannels() == 2) IJ.run("Grays");
							else if ( c==0 ) IJ.run("Red");
							if ( c==2 ) IJ.run("Green");
							if ( c==4 ) IJ.run("Blue");
							((MultiQTVirtualStack) vstack).imp.setPosition( c+2, imp.getSlice(), imp.getFrame() );
							if ( c==0 && ((MultiQTVirtualStack) vstack).imp.getNChannels() == 2 ) IJ.run("Grays");
							else if ( c==0 ) IJ.run("Red");
							if ( c==2 ) IJ.run("Green");
							if ( c==4 ) IJ.run("Blue");
						}
						((MultiQTVirtualStack) vstack).adjustSingleMovieZ(c, 1);
											
					}
				}
				
//				IJ.run("Multi-Channel Controller...");
				Frame mcc = imp.getMultiChannelController();
				if (mcc == null) {
					imp.setMultiChannelController(new MultiChannelController(imp));
					mcc = imp.getMultiChannelController();
				}
				if (mcc != null) {
					mcc.setVisible(false);
					Dimension dmc = mcc.getSize() ;
					mcc.setBounds(screenDimension.width-dmc.width, ((d.height+120 < screenDimension.height -50)? d.height+120: screenDimension.height-50),dmc.width, dmc.height);

				}
			}


			if 	(((MultiQTVirtualStack) vstack).imp.getNChannels() > 1 ) {
				((MultiQTVirtualStack) vstack).imp.setPosition(2,1,1);
				((MultiQTVirtualStack) vstack).imp.setPosition(2,1,2);
				((MultiQTVirtualStack) vstack).imp.setPosition(1,1,2);
			}
			
			IJ.doCommand("Start Animation [\\]");

        }
    }


}