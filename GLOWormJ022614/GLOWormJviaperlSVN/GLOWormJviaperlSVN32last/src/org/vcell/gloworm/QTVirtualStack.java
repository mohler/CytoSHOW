package org.vcell.gloworm;

import ij.*;
import ij.gui.StackWindow;
import ij.plugin.BrowserLauncher;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import quicktime.app.view.MoviePlayer;
import quicktime.app.view.QTImageProducer;
import quicktime.io.OpenMovieFile;
import quicktime.io.QTFile;
import quicktime.qd.QDDimension;
import quicktime.std.StdQTConstants;
import quicktime.std.movies.Movie;
import quicktime.std.movies.TimeInfo;
import quicktime.std.movies.Track;
import quicktime.QTException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.PixelGrabber;
import java.io.IOException;


public class QTVirtualStack extends VirtualStack {
  private MoviePlayer player;
  private QTImageProducer imageProducer;
  private int height;
  private int width;
  private int length;
  private int maxHeight;
  private int maxWidth;
  private int maxLength;
  private int nSlicesSingleMovie;
  private int timecourseLengthCurrent; 
  private int maxSlicesSingleMovie;
  private int maxTimesSingleMovie;
  private ArrayList frameLocations = new ArrayList();
  private boolean eightBit;
  private boolean stretchToFitOverlay;
  private boolean horizontal, grid;
  private boolean forward, flipVertical, flipHorizontal;
  private QTFile qtf;
  private double scaleX, scaleY, rotateAngle, translateX, translateY ;
  private QDDimension d;
  
  private int numPanels;
  private int panelNumber;
  private double gridAcross;
  private double gridDown;
private int grabY;
private int grabX;



  public QTVirtualStack(QTFile qtf, boolean eightBit) {
    this.eightBit = eightBit;
	this.qtf = qtf;
	flipVertical = false; 
	flipHorizontal = false;


    try {
      OpenMovieFile qtMovieFile = OpenMovieFile.asRead(qtf);
      Movie movie = Movie.fromFile(qtMovieFile);
      Track visualTrack = movie.getIndTrackType (1,
						 StdQTConstants.visualMediaCharacteristic,
						 StdQTConstants.movieTrackCharacteristic);
	  d = visualTrack.getSize();
      int nFrames = visualTrack.getMedia().getSampleCount();

      this.width  = d.getWidth();
      this.height = d.getHeight();
      this.maxWidth  = d.getWidth();
      this.maxHeight = d.getHeight();
	  this.numPanels = 1;
	  
      this.player = new MoviePlayer(movie);
      this.imageProducer = new QTImageProducer(this.player, new Dimension(this.width, this.height));

      {
		int location = player.getTime();
		for (int frame = 0 ; frame < nFrames ; frame++) {
		  this.frameLocations.add(new Integer(location));
		  TimeInfo ti = visualTrack.getNextInterestingTime(StdQTConstants.nextTimeMediaSample,
								   location,
								   1);
		  location = ti.time;
		}
      }
    }
    catch (QTException qte) {
 /*     	 try { BrowserLauncher.openURL
			 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=QTVirtualStack" + "A"+
			 		qte.getMessage());
		 }
		 catch (IOException ev2) {}
*/
    	throw new RuntimeException(qte);

    }
  }
  
  
  
  public QTVirtualStack(ImagePlus mqtvsImp, QTFile qtf, boolean eightBit, int maxWidth, int maxHeight, int maxLength, 
							int maxSlicesSingleMovie, int nSlicesSingleMovie, int maxTimesSingleMovie, 
							boolean stretchToFitOverlay, int numPanels , int panelNumber
							, boolean horizontal, boolean grid) {
    this.eightBit = eightBit;
	this.qtf = qtf;
    this.stretchToFitOverlay = stretchToFitOverlay;
    this.horizontal = horizontal;
    this.grid = grid;
	
	flipVertical = false; 
	flipHorizontal = false;

    try {
      OpenMovieFile qtMovieFile = OpenMovieFile.asRead(qtf);
      Movie movie = Movie.fromFile(qtMovieFile);
      Track visualTrack = movie.getIndTrackType (1,
						 StdQTConstants.visualMediaCharacteristic,
						 StdQTConstants.movieTrackCharacteristic);
      d = visualTrack.getSize();
      int nFrames = visualTrack.getMedia().getSampleCount();
	  
/***** Changing factor below adjusts width of the window frame for the rendered movie.****/
	  if (grid) {
		  if ( numPanels == 3 ) numPanels = 4;
		  if ( numPanels == 5 ) numPanels = 6;
		  if ( numPanels == 7 ) numPanels = 9;
		  if ( numPanels == 8 ) numPanels = 9;
		  if ( numPanels == 10 ) numPanels = 12;
		  if ( numPanels == 11 ) numPanels = 12;
		  if ( numPanels == 13 ) numPanels = 16;
		  if ( numPanels == 14 ) numPanels = 16;
		  if ( numPanels == 15 ) numPanels = 16;
		  if ( numPanels == 17 ) numPanels = 20;
		  if ( numPanels == 18 ) numPanels = 20;
		  if ( numPanels == 19 ) numPanels = 20;
		  gridAcross = Math.ceil(Math.sqrt(numPanels));
		  gridDown = numPanels/gridAcross;
		  
		  if (this.horizontal) {
			  this.maxWidth = maxWidth* ((int)gridAcross);
			  this.maxHeight = maxHeight* ((int)gridDown);
		  }else{
			  this.maxHeight = maxHeight* ((int)gridAcross);
			  this.maxWidth = maxWidth* ((int)gridDown);
		  }

	  } else {
		  if (this.horizontal) {
			  this.maxWidth = maxWidth*numPanels;
			  this.maxHeight = maxHeight;
		  }else{
			  this.maxHeight = maxHeight*numPanels;
			  this.maxWidth = maxWidth;
		  }
	  }
	  
/************/
	  
	  this.maxLength = maxLength;
	  this.nSlicesSingleMovie = nSlicesSingleMovie;
	  this.maxSlicesSingleMovie = maxSlicesSingleMovie;
	  this.maxTimesSingleMovie = maxTimesSingleMovie;
	  this.numPanels = numPanels;
	  this.panelNumber = panelNumber;
	  
		this.scaleX = 1.0;
		this.scaleY = 1.0; 
		this.rotateAngle = 0; 
		this.translateX = 0.0; 
		this.translateY = 0.0;


	  if (stretchToFitOverlay) {	  
		  this.length = (maxTimesSingleMovie * maxSlicesSingleMovie);
		  this.width  = d.getWidth();
		  this.height = d.getHeight();
	  }else{
		  this.length = (maxTimesSingleMovie * maxSlicesSingleMovie);
		  this.width   = d.getWidth();
		  this.height  = d.getHeight();
	  }
	  	  
      this.player = new MoviePlayer(movie);
      this.imageProducer = new QTImageProducer(this.player, new Dimension(this.width, this.height));
																//This Dimension sets the size of the movie rendering but oddly does NOT resolve conflicts in opening movies of different sizes.

      {
		int location = player.getTime();
		for (int frame = 0 ; frame < this.length ; frame++) {		//The ArrayList for each Movie is built to the maxLength of the largest 4D movie in the group.
		  if (frame >= nFrames) {
				this.frameLocations.add(new Integer(location));		// This  should initially fill  surplus  entries with  the  final  frame  of  the Movie.
		  } else {
				this.frameLocations.add(new Integer(location));
				TimeInfo ti = visualTrack.getNextInterestingTime(StdQTConstants.nextTimeMediaSample,
									   location,
									   1);
				location = ti.time;
		  }
		}
      
	  this.timecourseLengthCurrent = (nFrames/this.nSlicesSingleMovie);
	  int timecourseLengthMax = this.maxTimesSingleMovie;
	  int timecourseLengthPad = timecourseLengthMax - timecourseLengthCurrent;
	  int stackDepthPad = maxSlicesSingleMovie - nSlicesSingleMovie;
	  


		for (int z = 0; z < nSlicesSingleMovie; z++) {
			for (int t = timecourseLengthCurrent ; t < timecourseLengthMax; t++) {
				/*Pads the end of each shorter timecourse with copies of the final frame of that slice.
					Then clips end of arraylist to Maintain correct Size.
					Might be better to pad with black frame, but I'm not sure how to generate one.*/
				this.frameLocations.add( (z* timecourseLengthMax) + t  , 
											this.frameLocations.get( (z* timecourseLengthMax) + timecourseLengthCurrent -1));
											
				this.frameLocations.remove( this.frameLocations.size() - 1 );
			}									
		}
		
		
		for (int z = nSlicesSingleMovie; z < maxSlicesSingleMovie ; z++) {
			for (int t = 0; t < timecourseLengthMax; t++) {
				/*Copies the timecourse from the final slice of each shorter movie to pad the extra slices needed for maxSlicesSingleMovie
					Might be better to pad with black frames, but I'm not sure how to generate one.*/
				this.frameLocations.set( (z* timecourseLengthMax) + t  , 
											this.frameLocations.get( ( (nSlicesSingleMovie -1) * timecourseLengthMax) + t  ) );
		
			}
		}
	  }
	  
    }
    catch (QTException qte) {
  		 try { BrowserLauncher.openURL
			 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=QTVirtualStack" + "B"+
			 		qte.getMessage());
		 }
		 catch (IOException ev2) {}

      throw new RuntimeException(qte);
    }
  }


  public int getHeight() {
    return this.height;
  }


  public int getWidth() {
    return this.width;
  }


  public int getSize() {
    return frameLocations.size();
  }


  public String getSliceLabel(int slice) {
    slice--;  // ImageJ slices are 1-based rather than zero based.
    return "";
  }

	/*A  Method  added  to  allow MultiQtVirtualStack to get the frameLocations from each QtVirtualStack
	But is this even necessary Or can I do all I need with getSize()?  */
	public ArrayList getFrameLocations() {
		return this.frameLocations;
	}


  public QTFile getMovieFile(){
	return qtf;
  }


  public ImageProcessor getProcessor(int slice) {
    slice--;  // ImageJ slices are 1-based rather than zero based.
    try {

		int prew = this.width;	
		int preh = this.height;
		int w = this.maxWidth;	
		int h = this.maxHeight;
	  
      				//!!!!!!! These dimensions determine the Size of the canvas or window. They can make it larger than the dimensions in this.width and this.height.
      				// !!!! These values Can define a container window in which ANY size of movie can be played.
												// They "feel" the Pixel Grabber and the ImageProcessor
	 
	     int[] prepixels = new int[prew * preh];
	     int[] pixels = new int[w * h];
	  
	  
	  this.player.setTime( ((Integer) frameLocations.get(Math.round(slice  ) ) ).intValue() );
	  //if (IJ.debugMode) IJ.log( "qtvs_gp  "+this.qtf+"  " + slice);
      this.imageProducer.redraw(null);
      	long tsleep = 20;
      	
      	/* At this point here, I should be able to do rotation (and possibly other transforms) on the 
      	 * incoming pixels from the ImageProducer by putting the pixel array into an initial ColorProcessor 
      	 * that gets transformed and then recapturing the transformed view into the larger Maxwidth x Maxheight pixels array. */
      	PixelGrabber pregrabber = new PixelGrabber(this.imageProducer,	0, 0, prew, preh, prepixels, 0, prew);
		pregrabber.grabPixels(0);
	    ImageProcessor preip = new ColorProcessor(prew, preh, prepixels);
		  //if (IJ.debugMode) IJ.log("past new ip in QTVS.gP " + flipVertical);
		  if (flipVertical) {
		  		//if (IJ.debugMode) IJ.log("FV in QTVS.gP " + flipVertical);
				preip.flipVertical();
		  }
		  if (flipHorizontal) {
		  		//if (IJ.debugMode) IJ.log("FH in QTVS.gP " + flipHorizontal);
				preip.flipHorizontal();
		  }
		  //ip.medianFilter();
		  preip.setInterpolationMethod(1);
		  preip.setBackgroundValue(0.0);
		  preip.translate(this.translateX, this.translateY);
		  preip.scale(this.scaleX,this.scaleY);
		  preip.rotate(this.rotateAngle);
    	
		  Image preimage = preip.createImage();
		  
      	if (this.grid){//THIS CODE ADDED 10/25/10
      		if (this.horizontal) {
      			grabX = Math.round(0-(this.panelNumber%((int)gridAcross))*w)/ ((int)gridAcross); 
      			grabY = Math.round(0-h* ((int)Math.floor((this.panelNumber)/((int)gridAcross))))/ ((int)gridDown);
      		}else{
      			grabX = Math.round(0-(this.panelNumber%((int)gridDown))*w)/ ((int)gridDown); 
      			grabY = Math.round(0-h* ((int)Math.floor((this.panelNumber)/((int)gridDown))))/ ((int)gridAcross);
      		}
      	} else {   //THIS CODE RESIMPLIFIED 10/25/10
      		if (this.horizontal) {
      			grabX = Math.round(0-(this.panelNumber)*w)/this.numPanels; 
      			grabY = 0; 
      		}else{
      			grabX = 0; 
      			grabY = Math.round(0-(this.panelNumber)*h)/this.numPanels;       			
      		}
      	}
 
      	PixelGrabber grabber = new PixelGrabber(preimage, grabX, grabY, prew, preh, prepixels, 0, prew); 
      	if (numPanels > 1) grabber = new PixelGrabber(preimage, grabX, grabY, w, h, pixels, 0, w); 
      	//if (IJ.debugMode) IJ.log("pixels not yet grabbed");
      	Thread.sleep(tsleep);
      	grabber.grabPixels(0);
      	//if (IJ.debugMode) IJ.log("pixels grabbed");

      ImageProcessor ip = new ColorProcessor(prew, preh, prepixels);			// !!! If these dimensions don't match those of PixelGrabber then the image produced is very screwy.
      if (numPanels > 1)  ip = new ColorProcessor(w, h, pixels);      
      if (eightBit)
          ip=ip.convertToByte(false);
//	  ip.setInterpolationMethod(ImageProcessor.NONE);
//	  if (stretchToFitOverlay)
//		  ip = ip.resize(w, h, false);
      //if (IJ.debugMode) IJ.log("end of getProcessor");
	  ip.setInterpolationMethod(ImageProcessor.BICUBIC);
	  if (this.getOwnerImps() != null && this.getOwnerImps().size() > 0 && this.getOwnerImps().get(0) != null) {
			ip.translate(skewXperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1), skewYperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1));
		} else {
			ip.translate(skewXperZ*(slice-1), skewYperZ*(slice-1));
		}
      return ip;
    }
    catch(Exception e) {				   		 
/*    	try { BrowserLauncher.openURL
		 ("mailto:support@gloworm.org?" +
			 		"subject=Help%20or%20Comments%20on%20CytoSHOWNotes%20or%20CytoSHOW!!" +
			 		"%20%20Please,%20send%20your%20question%20or%20bug-report%20below." +
			 		"&body=QTVirtualStack" + "C"+
			 		e.getMessage());
		 }
		 catch (IOException ev2) {}
*/
      throw new RuntimeException(e);
    }
  }










  public void deleteSlice(int slice) {
    slice--;  // ImageJ slices are 1-based rather than zero based.
    frameLocations.remove(slice);
  }

  /*These methods allow viewer to shift the Z or T positions of a given channel to better synchronize movies.*/
	public void shiftMovieT(int shiftSingleMovieTPosition) {

		//if (IJ.debugMode) IJ.log("Got to the QTVS.SMT");
//		IJ.log(""+shiftSingleMovieTPosition);
		if (shiftSingleMovieTPosition < 0) {
			for (int i = 0; i < -shiftSingleMovieTPosition; i++) {
				for (int z = 0; z < maxSlicesSingleMovie; z++) {
					this.frameLocations.add((z * maxTimesSingleMovie), this.frameLocations.get(((z + 1) * maxTimesSingleMovie) - 1));
					this.frameLocations.remove(((z + 1) * maxTimesSingleMovie));
				}
			}
		} else {
			for (int i = 0; i < shiftSingleMovieTPosition; i++) {
				for (int z = maxSlicesSingleMovie-1 ; z >= 0  ; z--) {
					this.frameLocations.add( ((z+1)* maxTimesSingleMovie) , this.frameLocations.get( (z)* maxTimesSingleMovie ));
					this.frameLocations.remove( ((z)* maxTimesSingleMovie ) );
				}
			}
		}
	}

	public void shiftMovieZ(int shiftSingleMovieZPosition) {
	
		ArrayList copyFrames = new ArrayList();
		//if (IJ.debugMode) IJ.log("Got to the QTVS.SMZ");

		if (shiftSingleMovieZPosition < 0) {
			for (int i = 0; i < -shiftSingleMovieZPosition; i++) {
				for (int t= 0; t < maxTimesSingleMovie; t++) {
					copyFrames.add( this.frameLocations.get( (maxSlicesSingleMovie-1)*(maxTimesSingleMovie) )  );
					this.frameLocations.remove( (maxSlicesSingleMovie-1)*(maxTimesSingleMovie) );
				}
				this.frameLocations.addAll(0, copyFrames);
				copyFrames.removeAll(copyFrames);
			}
		} else {
			for (int i = 0; i < shiftSingleMovieZPosition; i++) {
				for (int t= 0; t < maxTimesSingleMovie; t++) {
					copyFrames.add( this.frameLocations.get(0) );
					this.frameLocations.remove(0);
				}
				this.frameLocations.addAll(copyFrames);
				copyFrames.removeAll(copyFrames);
			}
		}
									
	}

	
	public void flipMovieVertical() {
		flipVertical = !flipVertical;		
		//if (IJ.debugMode) IJ.log("got to QTVS.fMV " + flipVertical);
	}

	public void flipMovieHorizontal() {
		flipHorizontal = !flipHorizontal;
		//if (IJ.debugMode) IJ.log("got to QTVS.fMH " + flipHorizontal);
	}


	public void setScale( double scaleX, double scaleY) {
		this.scaleX = 1+ 0.01*scaleX;
		this.scaleY = 1+ 0.01*scaleY; 
	} 
 
 
	public void  setRotationAngle( double rotateAngle) {
		this.rotateAngle = rotateAngle; 
	} 


	public void  setTranslate( double translateX, double translateY) {
		this.translateX = translateX; 
		this.translateY = -translateY;
	}
	
	public String getMovieName() {
		if ( this.getMovieFile().getName().contains("_au") 
				&& this.getMovieFile().getName().contains("_date") 
				&& this.getMovieFile().getName().contains("_imgsys") ) {
			String[] nameChunks = this.getMovieFile().getName().split("_");
			return (nameChunks[0] +"_"+ nameChunks[1] +"_"+ nameChunks[2]);
		} else {	
			return this.getMovieFile().getName();
		}
		
	}
	
	
}



















