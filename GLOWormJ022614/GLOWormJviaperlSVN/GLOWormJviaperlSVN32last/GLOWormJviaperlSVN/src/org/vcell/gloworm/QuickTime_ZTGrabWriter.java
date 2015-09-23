package org.vcell.gloworm;
import ij.*;
import ij.plugin.PlugIn;
import ij.process.*;
import ij.io.*;
import ij.gui.*;
import ij.text.TextWindow;
import ij.util.Tools;
import ij.measure.Calibration;

import java.awt.*;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;


import quicktime.*;
import quicktime.io.*;
import quicktime.qd.*;
import quicktime.std.*;
import quicktime.std.movies.*;
import quicktime.std.movies.media.*;
import quicktime.std.image.*;
import quicktime.util.*;

import java.io.*;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

// This plugin uses QuickTime for Java to save the current stack as a QuickTime movie.
// It is based on the VideoSampleBuilder example from chapter 8 of "QuickTime for Java: 
// A Developer's Notebook" by Chris Adamson (www.oreilly.com/catalog/quicktimejvaadn/).
public class QuickTime_ZTGrabWriter implements PlugIn, StdQTConstants, TextListener {
	static final int TIME_SCALE = 600;
	String[] codecs = {"Cinepak", "Animation", "H.263", "Sorenson", "Sorenson 3", "MPEG-4"};
	int[] codecTypes = {kCinepakCodecType, kAnimationCodecType, kH263CodecType, kSorensonCodecType, 0x53565133, 0x6d703476};
	static String codec = "MPEG-4";
	String[] qualityStrings = {"Low", "Normal", "High", "Maximum"};
	int[] qualityConstants = {codecLowQuality, codecNormalQuality, codecHighQuality, codecMaxQuality};
	int keyFrameRate = 15;
	private TextField[] rangeFields;
	private int firstC;
	private int lastC;
	private int firstZ;
	private int lastZ;
	private int firstT;
	private int lastT;
	private boolean includeTags;
	private boolean scaleToDisplay;
	static String quality = "Maximum";

	public void run(String arg) {
		if (IJ.is64Bit() && IJ.isMacintosh()) {
			IJ.error("This plugin requires a 32-bit version of Java");
			return;
		}
		ImagePlus imp = IJ.getImage();
		if (imp==null) return;
		if (imp.getStackSize()==1) {
			IJ.showMessage("QuickTime Writer", "This plugin requires a stack");
			return;
		}
		
		

		Calibration cal = imp.getCalibration();
		double fps = 10.0;
		if (cal.frameInterval!=0.0)
			fps = 1.0/cal.frameInterval;
		int decimalPlaces = (int) fps == fps?0:3;
		
		IJ.run("Stop Animation");
		
		if (showHSDialog(imp) == null) return;
		
		GenericDialog gd = new GenericDialog("QuickTime Options");
		gd.addCheckbox("Include Tags", true);
		gd.addCheckbox("Scale to display zoom", true);
		gd.addChoice("Compression:", codecs, codec);
		gd.addChoice("Quality:", qualityStrings, quality);
		gd.addNumericField("Frame Rate:", fps, decimalPlaces, 4, "fps");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		includeTags = gd.getNextBoolean();
		scaleToDisplay = gd.getNextBoolean();
		codec = gd.getNextChoice();
		quality = gd.getNextChoice();
		int codecType = kSorensonCodecType;
		for (int i=0; i<codecs.length; i++) {
			if (codec.equals(codecs[i]))
				codecType = codecTypes[i];
		}
		int codecQuality = codecNormalQuality;
		for (int i=0; i<qualityStrings.length; i++) {
			if (quality.equals(qualityStrings[i]))
				codecQuality = qualityConstants[i];
		}
		switch (codecQuality) {
			case codecLowQuality: keyFrameRate=30; break;
			case codecNormalQuality: keyFrameRate=15; break;
			case codecHighQuality: keyFrameRate=7; break;
			case codecMaxQuality: keyFrameRate=1; break;
		}
		fps = gd.getNextNumber();
		if (fps<0.0016666667) fps = 0.0016666667; // 10 minutes/frame
		if (fps>100.0) fps = 100.0;
		int rate = (int)(TIME_SCALE/fps);
		cal.frameInterval = 1.0/fps;

		SaveDialog sd = new SaveDialog("Save as QuickTime...", imp.getTitle(), ".mov");
		String name = sd.getFileName();
		if (name==null) return;
/*
		if (name.length()>32) {
			IJ.error("QuickTime Writer", "File name cannot be longer than 32 characters");
			return;
		}
*/
		String dir = sd.getDirectory();
		String path = dir+name;

		long start = System.currentTimeMillis();
		try {
			QTSession.open();
			//getCodecSettings(null);
			writeMovie(imp, path, codecType, codecQuality, rate);
		} catch (Exception e) {
			IJ.showProgress(1.0);
			printStackTrace(e);
		} finally {
			QTSession.close();
		}

		File f = new java.io.File(path);
		double fsize = f.length();
		int bitsPerPixel = imp.getBitDepth();
		if (bitsPerPixel ==24) bitsPerPixel = 32;
		int bytesPerPixel = bitsPerPixel/8;
		int isize = imp.getWidth()*imp.getHeight()*imp.getStackSize()*bytesPerPixel;
		IJ.showStatus(IJ.d2s((System.currentTimeMillis()-start)/1000.0, 1)+ " seconds, "+IJ.d2s(isize/fsize,0)+":1 compression");
	}

	public void setHyperBounds(int firstSlice, int lastSlice, int firstFrame, int lastFrame) {
		this.firstZ = firstSlice;
		this.lastZ = lastSlice;
		this.firstT = firstFrame;
		this.lastT = lastFrame;
	}
	
	public void writeMovie(ImagePlus imp, String path, int codecType, int codecQuality, int rate) throws QTException, IOException {
	
		ImageWindow window = imp.getWindow();
		window.toFront();
	//	IJ.wait(500);
		ImageCanvas imcan = window.getCanvas();
		Rectangle captureBnds = imcan.getBounds();

		int viewChannel = imp.getChannel();
		int viewSlice = imp.getSlice();
		int viewFrame = imp.getFrame();

		int width = captureBnds.width;
		int height = captureBnds.height;
		if (!scaleToDisplay && !includeTags) {
			width = imp.getWidth();
			height = imp.getHeight();
			if (width > 3000 || height > 3000) {
				double wtohratio = (double) width/height;
				if (wtohratio >= 1) {
					width = 3000;
					height = (int)(3000 / wtohratio);
				}
			}
		}					

		
		ImageStack stack = imp.getStack();
		int frames = imp.getNFrames();
		int slices = imp.getNSlices();
		QTFile movFile = new QTFile (new java.io.File(path));
		Movie movie = Movie.createMovieFile(movFile, kMoviePlayer, createMovieFileDeleteCurFile|createMovieFileDontCreateResFile);
		int timeScale = TIME_SCALE; // 100 units per second
		Track videoTrack = movie.addTrack (width, height, 0);
		VideoMedia videoMedia = new VideoMedia(videoTrack, timeScale);
		videoMedia.beginEdits();
		ImageDescription imgDesc2 = new ImageDescription(QDConstants.k32ARGBPixelFormat);
		imgDesc2.setWidth(width);
		imgDesc2.setHeight(height);
		QDGraphics gw = new QDGraphics(imgDesc2, 0);
		QDRect bounds = new QDRect (0, 0, width, height);
		int rawImageSize = QTImage.getMaxCompressionSize(gw, bounds, gw.getPixMap().getPixelSize(), 
			codecQuality, codecType, CodecComponent.anyCodec);
		QTHandle imageHandle = new QTHandle (rawImageSize, true);
		imageHandle.lock();
		RawEncodedImage compressedImage = RawEncodedImage.fromQTHandle(imageHandle);
		CSequence seq = new CSequence(gw, bounds, gw.getPixMap().getPixelSize(), codecType, CodecComponent.bestFidelityCodec, 
			codecQuality, codecQuality, keyFrameRate, null, 0);
		ImageDescription imgDesc = seq.getDescription();
		int[] pixels2 = null;

		window.setVisible(false);

		for (int slice=firstZ; slice<= lastZ; slice++) {
			for (int frame= firstT; frame<= lastT; frame++) {
				IJ.showProgress(frame-firstT, lastT-firstT+1);
				IJ.showStatus( (slice-firstZ)
						+"/"+(lastZ-firstZ+1) 
						+ " (" +IJ.d2s( (slice-firstZ)*100.0/(lastZ-firstZ+1),0)+"%)");

				ImagePlus imp2 = null;


				{
					imp.setPositionWithoutUpdate(imp.getChannel(), slice, frame);			
					try {

						Image img = imp.getImage();
						ColorProcessor ip = new ColorProcessor(img);
						ip.setInterpolationMethod(ImageProcessor.NONE);
						ImageProcessor ip2= ip.resize(width, height);
						int[] pixels=null;
						if (!includeTags) {
							pixels = (int[])ip2.getPixels();
						}else{
							pixels = new int[(int)(imp.getCanvas().getSrcRect().getWidth() * imp.getCanvas().getSrcRect().getHeight())];
							BufferedImage bimg = new BufferedImage((int)imp.getCanvas().getSrcRect().getWidth(), (int)imp.getCanvas().getSrcRect().getHeight(), BufferedImage.TYPE_INT_ARGB);
							imp.getCanvas().paint(bimg.getGraphics());
							pixels = ((DataBufferInt)bimg.getRaster().getDataBuffer()).getData();
						}
						//						IJ.log(pixels.toString()+" "+pixels.length);
						RawEncodedImage pixelData = gw.getPixMap().getPixelData();
						int intsPerRow = pixelData.getRowBytes()/4;
						if (pixels2==null) pixels2 = new int[intsPerRow*height];
						if (EndianOrder.isNativeLittleEndian()) {
							//EndianOrder.flipBigEndianToNative(pixels, 0, EndianDescriptor.flipAll32);
							int offset1, offset2;
							for (int y=0; y<height; y++) {
								offset1 = y*width;
								offset2 = y* intsPerRow;
								for (int x=0; x<width; x++)
									pixels2[offset2++] = EndianOrder.flipBigEndianToNative32(pixels[offset1++]);
							}
						} else {
							for (int i=0; i<height; i++)
								System.arraycopy(pixels, i*width, pixels2, i*intsPerRow, width);
						}
						pixelData.copyFromArray(0, pixels2, 0, intsPerRow*height);
						CompressedFrameInfo cfInfo = seq.compressFrame (gw, bounds, codecFlagUpdatePrevious, compressedImage);
						boolean syncSample = cfInfo.getSimilarity()==0; // see developer.apple.com/qa/qtmcc/qtmcc20.html
						videoMedia.addSample (imageHandle, 0, cfInfo.getDataSize(), rate, imgDesc, 1, syncSample?0:mediaSampleNotSync);
						if (img!=null) {
							String title = WindowManager.getUniqueName(imp.getTitle());
							imp2 = new ImagePlus(title, img);
						}

					} catch(Exception e) {}

				}
			}
		}

		imp.setPosition(viewChannel, viewSlice, viewFrame);
		window.setVisible(true);


		videoMedia.endEdits();
		videoTrack.insertMedia (0, 0, videoMedia.getDuration(), 1);
		OpenMovieFile omf = OpenMovieFile.asWrite (movFile);
		movie.addResource (omf, movieInDataForkResID, movFile.getName());
	}

	void printStackTrace(Exception e) {
		CharArrayWriter caw = new CharArrayWriter();
		PrintWriter pw = new PrintWriter(caw);
		e.printStackTrace(pw);
		String s = caw.toString();
		new TextWindow("Exception", s, 500, 300);
	}
	
	String showHSDialog(ImagePlus imp) {
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		GenericDialog gd = new GenericDialog("Select Ranges");
		//gd.addStringField("Title:", imp, 15);
		gd.setInsets(12, 20, 8);
//		gd.addCheckbox("Duplicate hyperstack", duplicateStack);
		int nRangeFields = 0;
/*		if (nChannels>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Channels (c):", "1-"+nChannels);
			nRangeFields++;
		}
*/
		if (nSlices>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Slices (z):", "1-"+nSlices);
			nRangeFields++;
		}
		if (nFrames>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Frames (t):", ""+ imp.getFrame() +"-"+ imp.getFrame());
			nRangeFields++;
		}
		Vector v = gd.getStringFields();
		rangeFields = new TextField[nRangeFields];
		for (int i=0; i<nRangeFields; i++) {
			rangeFields[i] = (TextField)v.elementAt(i);
			rangeFields[i].addTextListener(this);
		}
		//checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		//String title = gd.getNextString();
		//duplicateStack = gd.getNextBoolean();
/*		if (nChannels>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double c1 = Tools.parseDouble(range[0]);
			double c2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			firstC = Double.isNaN(c1)?1:(int)c1;
			lastC = Double.isNaN(c2)?firstC:(int)c2;
			if (firstC<1) firstC = 1;
			if (lastC>nChannels) lastC = nChannels;
			if (firstC>lastC) {firstC=1; lastC=nChannels;}
		} else
			firstC = lastC = 1;
*/
		if (nSlices>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double z1 = Tools.parseDouble(range[0]);
			double z2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			firstZ = Double.isNaN(z1)?1:(int)z1;
			lastZ = Double.isNaN(z2)?firstZ:(int)z2;
			if (firstZ<1) firstZ = 1;
			if (lastZ>nSlices) lastZ = nSlices;
			if (firstZ>lastZ) {firstZ=1; lastZ=nSlices;}
		} else
			firstZ = lastZ = 1;
		if (nFrames>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double t1 = Tools.parseDouble(range[0]);
			double t2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			firstT= Double.isNaN(t1)?1:(int)t1;
			lastT = Double.isNaN(t2)?firstT:(int)t2;
			if (firstT<1) firstT = 1;
			if (lastT>nFrames) lastT = nFrames;
			if (firstT>lastT) {firstT=1; lastT=nFrames;}
		} else
			firstT = lastT = 1;
		return ("OK");
	}

	public void textValueChanged(TextEvent e) {
		//checkbox.setState(true);
	}


}
