package ij;
import java.awt.*;
import java.awt.image.*;
import java.util.ArrayList;

import ij.process.*;

/**
This class represents an expandable array of images.
@see ImagePlus
*/
public class ImageStack {
	private static final int BYTE=0, SHORT=1, FLOAT=2, RGB=3, UNKNOWN=-1;
	static final int INITIAL_SIZE = 25;
	protected static final String outOfRange = "Argument out of range: ";
	private int type = UNKNOWN;
	public int nImageSlices = 0;
	private Object[] stack;
	protected String[] label;
	protected int width;
	protected int height;
	protected double skewXperZ = 0f;
	protected double skewYperZ = 0f;
	private Rectangle roi;
	private ColorModel cm;
	private double min=Double.MAX_VALUE;
	private double max;
	private float[] cTable;
	protected boolean edges;
	protected boolean logScale;
	protected boolean sqrtScale;
	protected boolean sharpBlur;
	protected boolean flipH;
	protected boolean flipV;
	protected boolean flipZ;
	protected boolean rotateRight;
	protected boolean rotateLeft;
	protected boolean rcstereo;
	
	public boolean isRcstereo() {
		return rcstereo;
	}

	public void setRcstereo(boolean rcstereo) {
		this.rcstereo = rcstereo;
	}
	
	/** Default constructor. */
	public ImageStack() { }

	/** Creates a new, empty image stack. */
	public ImageStack(int width, int height) {
		this(width, height, null);
	}
	
	/** Creates a new, empty image stack with a capacity of 'size'.  All
	     'size' slices and labels of this image stack are initially null.*/
	public ImageStack(int width, int height, int size) {
		this.width = width;
		this.height = height;
		stack = new Object[size];
		label = new String[size];
		nImageSlices = size;
	}

	/** Creates a new, empty image stack using the specified color model. */
	public ImageStack(int width, int height, ColorModel cm) {
		this.width = width;
		this.height = height;
		this.cm = cm;
		stack = new Object[INITIAL_SIZE];
		label = new String[INITIAL_SIZE];
		nImageSlices = 0;
	}

	/** Adds an image in the forma of a pixel array to the end of the stack. */
	public void addSlice(String sliceLabel, Object pixels) {
		if (pixels==null) 
			throw new IllegalArgumentException("'pixels' is null!");
		if (!pixels.getClass().isArray()) 
			throw new IllegalArgumentException("'pixels' is not an array");
		int size = stack.length;
		nImageSlices++;
		if (nImageSlices>=size) {
			Object[] tmp1 = new Object[size*2];
			System.arraycopy(stack, 0, tmp1, 0, size);
			stack = tmp1;
			String[] tmp2 = new String[size*2];
			System.arraycopy(label, 0, tmp2, 0, size);
			label = tmp2;
		}
		stack[nImageSlices-1] = pixels;
		this.label[nImageSlices-1] = sliceLabel;
		if (type==UNKNOWN) {
			if (pixels instanceof byte[])
				type = BYTE;
			else if (pixels instanceof short[])
				type = SHORT;
			else if (pixels instanceof float[])
				type = FLOAT;
			else if (pixels instanceof int[])
				type = RGB;
		}
	}
	
	/**
	* @deprecated
	* Short images are always unsigned.
	*/
	public void addUnsignedShortSlice(String sliceLabel, Object pixels) {
		addSlice(sliceLabel, pixels);
	}
	
	/** Adds the image in 'ip' to the end of the stack. */
	public void addSlice(ImageProcessor ip) {
		addSlice(null, ip);
	}

	/** Adds the image in 'ip' to the end of the stack, setting
		the string 'sliceLabel' as the slice metadata. */
	public void addSlice(String sliceLabel, ImageProcessor ip) {
		if (ip.getWidth()!=width || ip.getHeight()!=height)
			throw new IllegalArgumentException("Dimensions do not match: "+width+"x"+height+" "+ip.getWidth()+"x"+ip.getHeight());
		if (nImageSlices==0) {
			cm = ip.getColorModel();
			min = ip.getMin();
			max = ip.getMax();
		}
		addSlice(sliceLabel, ip.getPixels());
	}
	
	/** Adds the image in 'ip' to the stack following slice 'n'. Adds
		the slice to the beginning of the stack if 'n' is zero. */
	public void addSlice(String sliceLabel, ImageProcessor ip, int n) {
		if (n<0 || n>nImageSlices)
			throw new IllegalArgumentException(outOfRange+n);
		addSlice(sliceLabel, ip);
		Object tempSlice = stack[nImageSlices-1];
		String tempLabel = label[nImageSlices-1];
		int first = n>0?n:1;
		for (int i=nImageSlices-1; i>=first; i--) {
			stack[i] = stack[i-1];
			label[i] = label[i-1];
		}
		stack[n] = tempSlice;
		label[n] = tempLabel;
	}
	
	/** Deletes the specified slice, were 1<=n<=nslices. */
	public void deleteSlice(int n) {
		if (n<1 || n>nImageSlices)
			throw new IllegalArgumentException(outOfRange+n);
		if (nImageSlices<1)
			return;
		for (int i=n; i<nImageSlices; i++) {
			stack[i-1] = stack[i];
			label[i-1] = label[i];
		}
		stack[nImageSlices-1] = null;
		label[nImageSlices-1] = null;
		nImageSlices--;
	}
	
	/** Deletes the last slice in the stack. */
	public void deleteLastSlice() {
		if (nImageSlices>0)
			try {
				deleteSlice(nImageSlices);
			} catch (IllegalArgumentException iae) {
				deleteSlice(nImageSlices);
			}
	}
	
    public int getWidth() {
    	return width;
    }

    public int getHeight() {
    	return height;
    }
    
	public void setRoi(Rectangle roi) {
		this.roi = roi;
	}
	
	public Rectangle getRoi() {
		if (roi==null)
			return new Rectangle(0, 0, width, height);
		else
			return(roi);
	}
	
	/** Updates this stack so its attributes, such as min, max,
		calibration table and color model, are the same as 'ip'. */
	public void update(ImageProcessor ip) {
		if (ip!=null) {
			min = ip.getMin();
			max = ip.getMax();
			cTable = ip.getCalibrationTable();
			cm = ip.getColorModel();
		}
	}
	
	/** Returns the pixel array for the specified slice, were 1<=n<=nslices. */
	public Object getPixels(int n) {
		if (n<1 || n>nImageSlices)
			throw new IllegalArgumentException(outOfRange+n);
		return stack[n-1];
	}
	
	/** Assigns a pixel array to the specified slice,
		were 1<=n<=nslices. */
	public void setPixels(Object pixels, int n) {
		if (n<1 || n>nImageSlices)
			throw new IllegalArgumentException(outOfRange+n);
		stack[n-1] = pixels;
	}
	
	/** Returns the stack as an array of 1D pixel arrays. Note
		that the size of the returned array may be greater than
		the number of slices currently in the stack, with
		unused elements set to null. */
	public Object[] getImageArray() {
		return stack;
	}
	
	/** Returns the number of slices in this stack. */
	public int getSize() {
		return nImageSlices;
	}

	/** Returns the slice labels as an array of Strings. Note
		that the size of the returned array may be greater than
		the number of slices currently in the stack. Returns null
		if the stack is empty or the label of the first slice is null.  */
	public String[] getSliceLabels() {
		if (nImageSlices==0)
			return null;
		else
			return label;
	}
	
	/** Returns the label of the specified slice, were 1<=n<=nslices.
		Returns null if the slice does not have a label. For DICOM
		and FITS stacks, labels may contain header information. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nImageSlices)
			throw new IllegalArgumentException(outOfRange+n);
		return label[n-1];
	}
	
	/** Returns a shortened version (up to the first 60 characters or first newline and 
		suffix removed) of the label of the specified slice.
		Returns null if the slice does not have a label. */
	public String getShortSliceLabel(int n) {
		String shortLabel = getSliceLabel(n);
		if (shortLabel==null) return null;
    	int newline = shortLabel.indexOf('\n');
    	if (newline==0) return null;
    	if (newline>0)
    		shortLabel = shortLabel.substring(0, newline);
    	int len = shortLabel.length();
		if (len>4 && shortLabel.charAt(len-4)=='.' && !Character.isDigit(shortLabel.charAt(len-1)))
			shortLabel = shortLabel.substring(0,len-4);
		if (shortLabel.length()>60)
			shortLabel = shortLabel.substring(0, 60);
		return shortLabel;
	}

	/** Sets the label of the specified slice, were 1<=n<=nslices. */
	public void setSliceLabel(String label, int n) {
		if (n<1 || n>nImageSlices)
			throw new IllegalArgumentException(outOfRange+n);
		this.label[n-1] = label;
	}
	
	/** Returns an ImageProcessor for the specified slice,
		were 1<=n<=nslices. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		ImageProcessor ip;
		if (n<1 || n>nImageSlices)
			throw new IllegalArgumentException(outOfRange+n);
		if (nImageSlices==0)
			return null;
		if (stack[n-1]==null)
			throw new IllegalArgumentException("Pixel array is null");
		if (stack[n-1] instanceof byte[])
			ip = new ByteProcessor(width, height, null, cm);
		else if (stack[n-1] instanceof short[])
			ip = new ShortProcessor(width, height, null, cm);
		else if (stack[n-1] instanceof int[])
			ip = new ColorProcessor(width, height, null);
		else if (stack[n-1] instanceof float[])
			ip = new FloatProcessor(width, height, null, cm);		
		else
			throw new IllegalArgumentException("Unknown stack type");
		ip.setPixels(stack[n-1]);
		if (min!=Double.MAX_VALUE && ip!=null && !(ip instanceof ColorProcessor))
			ip.setMinAndMax(min, max);
		if (cTable!=null)
			ip.setCalibrationTable(cTable);
		ip.setInterpolationMethod(ImageProcessor.BICUBIC);
			
		ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));

		return ip;
	}
	
	/** Assigns a new color model to this stack. */
	public void setColorModel(ColorModel cm) {
		this.cm = cm;
	}
	
	/** Returns this stack's color model. May return null. */
	public ColorModel getColorModel() {
		return cm;
	}
	
	/** Returns true if this is a 3-slice RGB stack. */
	public boolean isRGB() {
    	if (nImageSlices==3 && (stack[0] instanceof byte[]) && getSliceLabel(1)!=null && getSliceLabel(1).equals("Red"))	
			return true;
		else
			return false;
	}
	
	/** Returns true if this is a 3-slice HSB stack. */
	public boolean isHSB() {
    	if (nImageSlices==3 && getSliceLabel(1)!=null && getSliceLabel(1).equals("Hue"))	
			return true;
		else
			return false;
	}

	/** Returns true if this is a virtual (disk resident) stack. 
		This method is overridden by the VirtualStack subclass. */
	public boolean isVirtual() {
		return false;
	}

	/** Frees memory by deleting a few slices from the end of the stack. */
	public void trim() {
		int n = (int)Math.round(Math.log(nImageSlices)+1.0);
		for (int i=0; i<n; i++) {
			deleteLastSlice();
			System.gc();
		}
	}

	public String toString() {
		String v = isVirtual()?"(V)":"";
		return ("stack["+getWidth()+"x"+getHeight()+"x"+getSize()+v+"]");
	}
	
	/** Returns, as a double, the specified voxel. */
	public final double getVoxel(int x, int y, int z) {
		if (x>=0 && x<width && y>=0 && y<height && z>=0 && z<nImageSlices) {
			switch (type) {
				case BYTE:
					byte[] bytes = (byte[])stack[z];
					return bytes[y*width+x]&0xff;
				case SHORT:
					short[] shorts = (short[])stack[z];
					return shorts[y*width+x]&0xffff;
				case FLOAT:
					float[] floats = (float[])stack[z];
					return floats[y*width+x];
				case RGB:
					int[] ints = (int[])stack[z];
					return ints[y*width+x]&0xffffffff;
				default: return 0.0;
			}
		} else
			return 0.0;
	}
		
	/* Sets the value of the specified voxel. */
	public final void setVoxel(int x, int y, int z, double value) {
		if (x>=0 && x<width && y>=0 && y<height && z>=0 && z<nImageSlices) {
			switch (type) {
				case BYTE:
					byte[] bytes = (byte[])stack[z];
					if (value>255.0)
						value = 255.0;
					else if (value<0.0)
						value = 0.0;
					bytes[y*width+x] = (byte)(value+0.5);
					break;
				case SHORT:
					short[] shorts = (short[])stack[z];
					if (value>65535.0)
						value = 65535.0;
					else if (value<0.0)
						value = 0.0;
					shorts[y*width+x] = (short)(value+0.5);
					break;
				case FLOAT:
					float[] floats = (float[])stack[z];
					floats[y*width+x] = (float)value;
					break;
				case RGB:
					int[] ints = (int[])stack[z];
					ints[y*width+x] = (int)value;
					break;
			}
		}
	}
	
	/** Experimental */
	public float[] getVoxels(int x0, int y0, int z0, int w, int h, int d, float[] voxels) {
		boolean inBounds = x0>=0 && x0+w<=width && y0>=0 && y0+h<=height && z0>=0 && z0+d<=nImageSlices;
		if (voxels==null || voxels.length!=w*h*d)
			voxels = new float[w*h*d];
		int i = 0;
		int offset;
		for (int z=z0; z<z0+d; z++) {
			for (int y=y0; y<y0+h; y++) {
				if (inBounds) {
					switch (type) {
						case BYTE:
							byte[] bytes = (byte[])stack[z];
							for (int x=x0; x<x0+w; x++)
								voxels[i++] = bytes[y*width+x]&0xff;
							break;
						case SHORT:
							short[] shorts = (short[])stack[z];
							for (int x=x0; x<x0+w; x++)
								voxels[i++] = shorts[y*width+x]&0xffff;
							break;
						case FLOAT:
							float[] floats = (float[])stack[z];
							for (int x=x0; x<x0+w; x++)
								voxels[i++] = floats[y*width+x];
							break;
						case RGB:
							int[] ints = (int[])stack[z];
							for (int x=x0; x<x0+w; x++)
								voxels[i++] = ints[y*width+x]&0xffffffff;
							break;
						default:
							for (int x=x0; x<x0+w; x++)
								voxels[i++] = 0f;
					}
				} else {
					for (int x=x0; x<x0+w; x++)
						voxels[i++] = (float)getVoxel(x, y, z);
				}
			}
		}
		return voxels;
	}

	/** Experimental */
	public float[] getVoxels(int x0, int y0, int z0, int w, int h, int d, float[] voxels, int channel) {
		if (getBitDepth()!=24)
			return getVoxels(x0, y0, z0, w, h, d, voxels);
		boolean inBounds = x0>=0 && x0+w<=width && y0>=0 && y0+h<=height && z0>=0 && z0+d<=nImageSlices;
		if (voxels==null || voxels.length!=w*h*d)
			voxels = new float[w*h*d];
		int i = 0;
		for (int z=z0; z<z0+d; z++) {
			int[] ints = (int[])stack[z];
			for (int y=y0; y<y0+h; y++) {
				for (int x=x0; x<x0+w; x++) {
					int value = inBounds?ints[y*width+x]&0xffffffff:(int)getVoxel(x, y, z);
					switch (channel) {
						case 0: value=(value&0xff0000)>>16; break;
						case 1: value=(value&0xff00)>>8; break;
						case 2: value=value&0xff;; break;
					}
					voxels[i++] = (float)value;
				}
			}
		}
		return voxels;
	}

	/** Experimental */
	public void setVoxels(int x0, int y0, int z0, int w, int h, int d, float[] voxels) {
		boolean inBounds = x0>=0 && x0+w<=width && y0>=0 && y0+h<=height && z0>=0 && z0+d<=nImageSlices;
		if (voxels==null || voxels.length!=w*h*d)
			;
		int i = 0;
		float value;
		for (int z=z0; z<z0+d; z++) {
			for (int y=y0; y<y0+h; y++) {
				if (inBounds) {
					switch (type) {
						case BYTE:
							byte[] bytes = (byte[])stack[z];
							for (int x=x0; x<x0+w; x++) {
								value = voxels[i++];
								if (value>255f)
									value = 255f;
								else if (value<0f)
									value = 0f;
								bytes[y*width+x] = (byte)(value+0.5f);
							}
							break;
						case SHORT:
							short[] shorts = (short[])stack[z];
							for (int x=x0; x<x0+w; x++) {
								value = voxels[i++];
								if (value>65535f)
									value = 65535f;
								else if (value<0f)
									value = 0f;
								shorts[y*width+x] = (short)(value+0.5f);
							}
							break;
						case FLOAT:
							float[] floats = (float[])stack[z];
							for (int x=x0; x<x0+w; x++) {
								value = voxels[i++];
								floats[y*width+x] = value;
							}
							break;
						case RGB:
							int[] ints = (int[])stack[z];
							for (int x=x0; x<x0+w; x++) {
								value = voxels[i++];
								ints[y*width+x] = (int)value;
							}
							break;
					}
				} else {
					for (int x=x0; x<x0+w; x++)
						setVoxel(x, y, z, voxels[i++]);
				}
			}
		}
	}
	
	/** Experimental */
	public void setVoxels(int x0, int y0, int z0, int w, int h, int d, float[] voxels, int channel) {
		if (getBitDepth()!=24) {
			setVoxels(x0, y0, z0, w, h, d, voxels);
			return;
		}
		boolean inBounds = x0>=0 && x0+w<=width && y0>=0 && y0+h<=height && z0>=0 && z0+d<=nImageSlices;
		if (voxels==null || voxels.length!=w*h*d)
			;
		int i = 0;
		for (int z=z0; z<z0+d; z++) {
			int[] ints = (int[])stack[z];
			for (int y=y0; y<y0+h; y++) {
				for (int x=x0; x<x0+w; x++) {
					int value = inBounds?ints[y*width+x]&0xffffffff:(int)getVoxel(x, y, z);
					int color = (int)voxels[i++];
					switch (channel) {
						case 0: value=(value&0xff00ffff) | ((color&0xff)<<16); break;
						case 1: value=(value&0xffff00ff) | ((color&0xff)<<8); break;
						case 2: value=(value&0xffffff00) | (color&0xff); break;
					}
					if (inBounds)
						ints[y*width+x] = value;
					else
						setVoxel(x, y, z, value);
				}
			}
		}
	}

	/** Experimental */
	public void drawSphere(double radius, int xc, int yc, int zc) {
		int diameter = (int)Math.round(radius*2);
	    double r = radius;
		int xmin=(int)(xc-r+0.5), ymin=(int)(yc-r+0.5), zmin=(int)(zc-r+0.5);
		int xmax=xmin+diameter, ymax=ymin+diameter, zmax=zmin+diameter;
		double r2 = r*r;
		r -= 0.5;
		double xoffset=xmin+r, yoffset=ymin+r, zoffset=zmin+r;
		double xx, yy, zz;
		for (int x=xmin; x<=xmax; x++) {
			for (int y=ymin; y<=ymax; y++) {
				for (int z=zmin; z<=zmax; z++) {
					xx = x-xoffset; yy = y-yoffset;  zz = z-zoffset;
					if (xx*xx+yy*yy+zz*zz<=r2)
						setVoxel(x, y, z, 255);
				}
			}
		}
	}

	
	/** Returns the bit depth (8=byte, 16=short, 24=RGB, 32=float). */
	public int getBitDepth() {
		switch (type) {
			case BYTE: return 8;
			case SHORT: return 16;
			case FLOAT: return 32;
			case RGB: return 24;
		}
		return 0;
	}

	/** Creates a new ImageStack.
	*  @param width  width in pixels
	*  @param height height in pixels
	*  @param depth number of images
	*  @param bitdepth  8, 16, 32 (float) or 24 (RGB)
	*/
	 public static ImageStack create(int width, int height, int depth, int bitdepth) {
		ImageStack stack =  IJ.createImage("", width, height, depth, bitdepth).getStack();
		if (bitdepth==16 || bitdepth==32) {
			stack.min = Double.MAX_VALUE;
			stack.max = 0.0;
		}
		return stack;
	 }
	 
	public double getSkewXperZ() {
		return skewXperZ;
	}

	public void setSkewXperZ(double skewXperZ) {
		this.skewXperZ = skewXperZ;
	}

	public double getSkewYperZ() {
		return skewYperZ;
	}

	public void setSkewYperZ(double skewYperZ) {
		this.skewYperZ = skewYperZ;
	}

	public boolean isEdges() {
		return edges;
	}

	public void setEdges(boolean edges) {
		this.edges = edges;
	}

	public boolean isLogScale() {
		return logScale;
	}

	public void setLogScale(boolean logScale) {
		this.logScale = logScale;
	}

	public boolean isSqRtScale() {
		return sqrtScale;
	}

	public void setSqRtScale(boolean sqrtScale) {
		this.sqrtScale = sqrtScale;
	}

	public boolean isSharpBlur() {
		// TODO Auto-generated method stub
		return sharpBlur;
	}
	
	public void setSharpBlur(boolean sharpBlur) {
		this.sharpBlur = sharpBlur;
	}

	public boolean isFlipH() {
		return flipH;
	}
	
	public void setFlipH(boolean flipH) {
		this.flipH = flipH;
	}

	public boolean isFlipV() {
		return flipV;
	}
	
	public void setFlipV(boolean flipV) {
		this.flipV = flipV;
	}

	public boolean isFlipZ() {
		return flipZ;
	}

	public void setFlipZ(boolean flipZ) {
		this.flipZ = flipZ;
	}

	public boolean isRotateRight() {
		return rotateRight;
	}

	public void setRotateRight(boolean rotateRight) {
		this.rotateRight = rotateRight;
	}

	public boolean isRotateLeft() {
		return rotateLeft;
	}

	public void setRotateLeft(boolean rotateLeft) {
		this.rotateLeft = rotateLeft;
	}





}
