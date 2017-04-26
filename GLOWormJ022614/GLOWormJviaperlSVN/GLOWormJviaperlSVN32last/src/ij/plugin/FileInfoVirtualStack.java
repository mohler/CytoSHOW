package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;

import java.awt.*;
import java.io.*;
import java.util.Properties;

/** This plugin opens a multi-page TIFF file as a virtual stack. It
	implements the File/Import/TIFF Virtual Stack command. */
public class FileInfoVirtualStack extends VirtualStack implements PlugIn {
	FileInfo[] infoArray;
	
	public FileInfo[] getInfo() {
		return infoArray;
	}

	int nImages;
	
	/* Default constructor. */
	public FileInfoVirtualStack() {}

	/* Constructs a FileInfoVirtualStack from a FileInfo object. */
	public FileInfoVirtualStack(FileInfo fi) {
		infoArray = new FileInfo[1];
		infoArray[0] = fi;
		open(true);
	}

	/* Constructs a FileInfoVirtualStack from a FileInfo 
		object and displays it if 'show' is true. */
	public FileInfoVirtualStack(FileInfo fi, boolean show) {
		infoArray = new FileInfo[1];
		infoArray[0] = fi;
		open(show);
	}

	/* Constructs a FileInfoVirtualStack from an array of FileInfo 
	objects and displays it if 'show' is true. */
	public FileInfoVirtualStack(FileInfo[] fi, boolean show) {
		infoArray = fi;
		open(show);
	}
	
	public  ImageStack createStack(String path, boolean show) {
		TiffDecoder td = new TiffDecoder((new File(path)).getParent()+File.separator, (new File(path)).getName());
		if (IJ.debugMode) td.enableDebugging();
		IJ.showStatus("Decoding TIFF header...");
		try {infoArray = td.getTiffInfo(0);}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return null;
		}
		
		return open(show).getStack();
	}


	public void run(String arg) {
		OpenDialog  od = new OpenDialog("Open TIFF", arg);
		String name = od.getFileName();
		if (name==null) return;
		if (name.endsWith(".zip")) {
			IJ.error("Virtual Stack", "ZIP compressed stacks not supported");
			return;
		}
		String  dir = od.getDirectory();
		TiffDecoder td = new TiffDecoder(dir, name);
		if (IJ.debugMode) td.enableDebugging();
		IJ.showStatus("Decoding TIFF header...");
		try {infoArray = td.getTiffInfo(0);}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return;
		}
		if (infoArray==null || infoArray.length==0) {
			IJ.error("Virtual Stack", "This does not appear to be a TIFF stack");
			return;
		}
		if (IJ.debugMode)
			IJ.log(infoArray[0].debugInfo);
		open(true);
	}
	
	public ImagePlus open(boolean show) {
		FileInfo fi = infoArray[0];
		int n = fi.nImages;
		if (infoArray.length==1 && n>1) {
			infoArray = new FileInfo[n];
			long size = fi.width*fi.height*fi.getBytesPerPixel();
			for (int i=0; i<n; i++) {
				infoArray[i] = (FileInfo)fi.clone();
				infoArray[i].nImages = 1;
				infoArray[i].longOffset = fi.getOffset() + i*(size + fi.gapBetweenImages);
			}
		}
		nImages = infoArray.length;
		nSlices = nImages;
		names = new String[nImages+1];
		labels = new String[nImages+1];
		FileOpener fo = new FileOpener(infoArray[0] );
		ImagePlus imp = fo.open(false);
		if (nImages==1 && fi.fileType==FileInfo.RGB48) {
			if (show) imp.show();
			return imp;
		}
		Properties props = fo.decodeDescriptionString(fi);
		ImagePlus imp2 = new ImagePlus(fi.fileName, this);
		imp2.setFileInfo(fi);
		if (imp!=null && props!=null) {
			setBitDepth(imp.getBitDepth());
			imp2.setCalibration(imp.getCalibration());
			imp2.setOverlay(imp.getOverlay());
			if (fi.info!=null)
				imp2.setProperty("Info", fi.info);
			int channels = getInt(props,"channels");
			int slices = getInt(props,"slices");
			int frames = getInt(props,"frames");
			if (channels*slices*frames==nImages) {
				imp2.setDimensions(channels, slices, frames);
				if (getBoolean(props, "hyperstack"))
					imp2.setOpenAsHyperStack(true);
			}
			if (channels>1 && fi.description!=null) {
				int mode = CompositeImage.COMPOSITE;
				if (fi.description.indexOf("mode=color")!=-1)
					mode = CompositeImage.COLOR;
				else if (fi.description.indexOf("mode=gray")!=-1)
					mode = CompositeImage.GRAYSCALE;
				imp2 = new CompositeImage(imp2, mode);
			}
		}
		if (show) imp2.show();
		return imp2;
	}

	public void setupStack() {
		FileInfo fi = infoArray[0];
		int n = fi.nImages;
		if (infoArray.length==1 && n>1) {
			infoArray = new FileInfo[n];
			long size = fi.width*fi.height*fi.getBytesPerPixel();
			for (int i=0; i<n; i++) {
				infoArray[i] = (FileInfo)fi.clone();
				infoArray[i].nImages = 1;
				infoArray[i].longOffset = fi.getOffset() + i*(size + fi.gapBetweenImages);
			}
		}
		nImages = infoArray.length;
		nSlices = nImages;
		names = new String[nImages+1];
		labels = new String[nImages+1];

		
		return;
	}

	
	int getInt(Properties props, String key) {
		Double n = getNumber(props, key);
		return n!=null?(int)n.doubleValue():1;
	}

	Double getNumber(Properties props, String key) {
		String s = props.getProperty(key);
		if (s!=null) {
			try {
				return Double.valueOf(s);
			} catch (NumberFormatException e) {}
		}	
		return null;
	}

	boolean getBoolean(Properties props, String key) {
		String s = props.getProperty(key);
		return s!=null&&s.equals("true")?true:false;
	}

	/** Deletes the specified image, were 1<=n<=nImages. */
	public void deleteSlice(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (nSlices<1) return;
		for (int i=n; i<nSlices; i++)
			infoArray[i-1] = infoArray[i];
		if (nSlices-1<infoArray.length)
			infoArray[nSlices-1] = null;
//		nImages--;
		nSlices--;
	}
	
	/** Returns an ImageProcessor for the specified image,
		were 1<=n<=nImages. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		if (n<1 || n>nSlices)
			return getProcessor(1);
//			throw new IllegalArgumentException("Argument out of range: "+n);
		if (IJ.debugMode) IJ.log("FileInfoVirtualStack: "+n+", "+infoArray[n-1].getOffset());
		//if (n>1) IJ.log("  "+(info[n-1].getOffset()-info[n-2].getOffset()));
		ImagePlus imp = null;		
		if (n<=nImages ) {
			infoArray[n-1].nImages = 1; // why is this needed?
			FileOpener fo = new FileOpener(infoArray[n-1]);
			imp = fo.open(false);
		}
		if (imp!=null) {
			ImageProcessor ip = imp.getProcessor();
			ip.setInterpolationMethod(ImageProcessor.BICUBIC);
			if (this.getOwnerImps() != null && this.getOwnerImps().size() > 0 && this.getOwnerImps().get(0) != null) {
				ip.translate(skewXperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2), skewYperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2));
			} else {
				ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));
			}
			return ip;
		} else {
			int w=getWidth(), h=getHeight();
			if (n<=nImages ) 
				/*IJ.log("Read error or file not found ("+n+"): "+info[n-1].directory+info[n-1].fileName)*/;
			switch (getBitDepth()) {
				case 8: return new ByteProcessor(w, h);
				case 16: return new ShortProcessor(w, h);
				case 24: return new ColorProcessor(w, h);
				case 32: return new FloatProcessor(w, h);
//				default: return getProcessor(1).createProcessor(w, h);
				default: return new ShortProcessor(w, h);
			}
		}
	 }
 
	 /** Returns the number of images in this stack. */
	public int getSize() {
		return nSlices;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (infoArray[0].sliceLabels==null || infoArray[0].sliceLabels.length!=nImages)
			return infoArray[0].fileName + " slice "+ n;
		else
			return infoArray[0].sliceLabels[n-1];
	}

	public int getWidth() {
		return infoArray[0].width;
	}
	
	public int getHeight() {
		return infoArray[0].height;
	}
    
}
