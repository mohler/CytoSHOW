package ij;
import ij.process.*;
import ij.io.*;
import ij.gui.ImageCanvas2;
import ij.util.Tools;

import java.io.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;

/** This class represents an array of disk-resident images. */
public class VirtualStack extends ImageStack {
	private static final int INITIAL_SIZE = 100;
	private String path;
//	protected int nSlices;
	protected String[] names;
	protected String[] labels;
	private int bitDepth;
	private boolean emptyImage;
	Color fillColor = Color.black;
	private boolean burnIn = false;
	private ImagePlus lineageMapImage;
	public String dimOrder;
	protected  int[] dXA = new int[]{0};
	protected  int[] dXB = new int[]{0};
	protected  int[] dYA = new int[]{0};
	protected  int[] dYB = new int[]{0};
	protected  int[] dZA = new int[]{0};
	protected  int[] dZB = new int[]{0};



	/** Default constructor. */
	public VirtualStack() { 
		names = new String[INITIAL_SIZE];
		labels = new String[INITIAL_SIZE];
	}

	/** Creates a new, virtual stack via super call from BFVirtualStack. */
	public VirtualStack(int width, int height, ColorModel cm, String path) {
		this(width, height, null, path, false, Color.black);
	}


	/** Creates a new, empty virtual stack. */
	public VirtualStack(int width, int height, ColorModel cm, String path, boolean emptyImage, Color fillColor) {
		super(width, height, cm);
		dimOrder = "xyczt";
		this.path = path;
		this.emptyImage = emptyImage;
		if (fillColor != null)
			this.fillColor = fillColor;
		names = new String[INITIAL_SIZE];
		labels = new String[INITIAL_SIZE];
		//IJ.log("VirtualStack: "+path);
	}

	 /** Adds an image to the end of the stack. */
	public void addSlice(String name) {
		if (name==null) 
			throw new IllegalArgumentException("'name' is null!");
		nImageSlices++;
	   //IJ.log("addSlice: "+nSlices+"	"+name);
	   if (nImageSlices==names.length) {
			String[] tmp = new String[nImageSlices*2];
			System.arraycopy(names, 0, tmp, 0, nImageSlices);
			names = tmp;
			tmp = new String[nImageSlices*2];
			System.arraycopy(labels, 0, tmp, 0, nImageSlices);
			labels = tmp;
		}
		names[nImageSlices-1] = name;
	}

   /** Does nothing. */
	public void addSlice(String sliceLabel, Object pixels) {
	}

	/** Does nothing.. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
	}
	
	/** Does noting. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
	}

	/** Deletes the specified slice, were 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if (n<1 || n>nImageSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
			if (nImageSlices<1)
				return;
			for (int i=n; i<nImageSlices; i++)
				names[i-1] = names[i];
			names[nImageSlices-1] = null;
			nImageSlices--;
		}
	
	/** Deletes the last slice in the stack. */
	public void deleteLastSlice() {
		if (nImageSlices>0)
			deleteSlice(nImageSlices);
	}
	   
   /** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	public Object getPixels(int n) {
		ImageProcessor ip = getProcessor(n);
		if (ip!=null)
			return ip.getPixels();
		else
			return null;
	}		
	
	 /** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	public void setPixels(Object pixels, int n) {
	}

   /** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		ImageProcessor ip = null;
		//IJ.log("getProcessor: "+n+"  "+names[n-1]+"  "+bitDepth);
		if (!emptyImage) {
			Opener opener = new Opener();
			opener.setSilentMode(true);
			IJ.redirectErrorMessages(true);
			ImagePlus imp = opener.openImage(path, names[n-1]);
			IJ.redirectErrorMessages(false);
			ip = null;
			int depthThisImage = 0;
			if (imp!=null) {
				int w = imp.getWidth();
				int h = imp.getHeight();
				int type = imp.getType();
				ColorModel cm = imp.getProcessor().getColorModel();
				labels[n-1] = (String)imp.getProperty("Info");
				depthThisImage = imp.getBitDepth();
				ip = imp.getProcessor();
				ip.setOverlay(imp.getOverlay());
			} else {
				File f = new File(path, names[n-1]);
				String msg = f.exists()?"Error opening ":"File not found: ";
				ip = new ByteProcessor(getWidth(), getHeight());
				ip.invert();
				int size = getHeight()/20;
				if (size<9) size=9;
				Font font = new Font("Helvetica", Font.PLAIN, size);
				ip.setFont(font);
				ip.setAntialiasedText(true);
				ip.setColor(0);
				ip.drawString(msg+names[n-1], size, size*2);
				depthThisImage = 8;
			}
			if (depthThisImage!=bitDepth) {
				switch (bitDepth) {
				case 8: ip=ip.convertToByte(true); break;
				case 16: ip=ip.convertToShort(true); break;
				case 24:  ip=ip.convertToRGB(); break;
				case 32: ip=ip.convertToFloat(); break;
				}
			}
			if (ip.getWidth()!=getWidth() || ip.getHeight()!=getHeight()) {
				ImageProcessor ip2 = ip.createProcessor(getWidth(), getHeight());
				ip2.insert(ip, 0, 0);
				ip = ip2;
			}
		}else{
			ip = new ByteProcessor(getWidth(), getHeight());
			ip.setColor(fillColor);
			ip.fill();
		}
		ip.setInterpolationMethod(ImageProcessor.BICUBIC);

		ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));
		if (edges) {
			ip.findEdges();
//			ip.doAffineTransform(new AffineTransform(1,0,0,1,0,0));
		}
		if (logScale) {
			ip.log();
		}
		if (sqrtScale) {
			ip.sqrt();
		}
		return ip;
	 }
 
	/** Currently not implemented */
	public int saveChanges(int n) {
		return -1;
	}

	 /** Returns the number of slices in this stack. */
	public int getSize() {
		return nImageSlices;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		String label = labels[n-1];
		if (label==null)
			return names[n-1];
		else if (label.length()<=60)
			return label;
		else
			return names[n-1]+"\n"+label;
	}
	
	/** Returns null. */
	public Object[] getImageArray() {
		return null;
	}

   /** Does nothing. */
	public void setSliceLabel(String label, int n) {
	}

	/** Always return true. */
	public boolean isVirtual() {
		return true;
	}

   /** Does nothing. */
	public void trim() {
	}
	
	/** Returns the path to the directory containing the images. */
	public String getDirectory() {
		return path;
	}
		
	/** Returns the file name of the specified slice, were 1<=n<=nslices. */
	public String getFileName(int n) {
		return names[n-1];
	}
	
	/** Sets the bit depth (8, 16, 24 or 32). */
	public void setBitDepth(int bitDepth) {
		this.bitDepth = bitDepth;
	}

	/** Returns the bit depth (8, 16, 24 or 32), or 0 if the bit depth is not known. */
	public int getBitDepth() {
		return bitDepth;
	}
	
	public ImageStack sortDicom(String[] strings, String[] info, int maxDigits) {
		int n = getSize();
		String[] names2 = new String[n];
		for (int i=0; i<n; i++)
			names2[i] = names[i];
		for (int i=0; i<n; i++) {
			int slice = (int)Tools.parseDouble(strings[i].substring(strings[i].length()-maxDigits), 0.0);
			if (slice==0) return null;
			names[i] = names2[slice-1];
			labels[i] = info[slice-1];
		}
		return this;
	}

	public boolean isBurnIn() {
		return burnIn;
	}

	public void setBurnIn(boolean burnIn) {
		this.burnIn = burnIn;
	}

	public ImagePlus getLineageMapImage() {
		return lineageMapImage;
	}
	
	public void setLineageMapImage(ImagePlus lineageMapImage) {
		this.lineageMapImage = lineageMapImage;
		
	}

	public String getStackOrder() {
		return dimOrder;
	}

	public void setStackOrder(String dimOrder) {
		if (dimOrder.toLowerCase().startsWith("xy"))
			this.dimOrder = dimOrder;
	}


	public  int[] getdXA() {
		return dXA;
	}

	public  void setdXA(int[] dXA) {
		this.dXA = dXA;
	}

	public  int[] getdXB() {
		return dXB;
	}

	public  void setdXB(int[] dXB) {
		this.dXB = dXB;
	}

	public  int[] getdYA() {
		return dYA;
	}

	public  void setdYA(int[] dYA) {
		this.dYA = dYA;
	}

	public  int[] getdYB() {
		return dYB;
	}

	public  void setdYB(int[] dYB) {
		this.dYB = dYB;
	}

	public int[] getdZA() {
		return dZA;
	}

	public void setdZA(int[] dZA) {
		this.dZA = dZA;
	}

	public int[] getdZB() {
		return dZB;
	}

	public void setdZB(int[] dZB) {
		this.dZB = dZB;
	}


} 

