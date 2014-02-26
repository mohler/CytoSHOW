package ij.plugin;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class MultiFileInfoVirtualStack extends VirtualStack implements PlugIn {
	ArrayList<FileInfoVirtualStack> fivStacks = new ArrayList<FileInfoVirtualStack>();
	FileInfo[] info;
	int nImages;

	/* Default constructor. */
	public MultiFileInfoVirtualStack() {}

	/* Constructs a FileInfoVirtualStack from a FileInfo object. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray) {
		info = fiArray;
	}

	/* Constructs a FileInfoVirtualStack from a FileInfo 
	object and displays it if 'show' is true. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray, boolean show) {
		info = fiArray;
	}

	public void run(String arg) {
		File argFile = new File(arg);
		String dir = "";
		if (!argFile.exists() && !argFile.isDirectory())
			dir = IJ.getDirectory("Select Directory of TIFFs");
		else
			dir = arg;
		if (dir==null) return;
		argFile = new File(dir);
		String[] fileList = argFile.list();
		Arrays.sort(fileList);
		for (String file:fileList){
			TiffDecoder td = new TiffDecoder(dir, file);
			if (IJ.debugMode) td.enableDebugging();
			IJ.showStatus("Decoding TIFF header...");
			try {info = td.getTiffInfo();}
			catch (IOException e) {
				String msg = e.getMessage();
				if (msg==null||msg.equals("")) msg = ""+e;
				IJ.error("TiffDecoder", msg);
				return;
			}
			if (info==null || info.length==0) {
				continue;
			}
			if (IJ.debugMode)
				IJ.log(info[0].debugInfo);
			fivStacks.add(new FileInfoVirtualStack());
			fivStacks.get(fivStacks.size()-1).info = info;
			fivStacks.get(fivStacks.size()-1).open(false);
		}
		open(true);
	}
	
	void open(boolean show) {
		nImages = fivStacks.size() * fivStacks.get(0).nImages;
		ImagePlus imp = new ImagePlus(fivStacks.get(0).open(false).getTitle().replaceAll("\\d+\\.", "\\."), this);
		imp.setOpenAsHyperStack(true);
		imp.setDimensions(1, fivStacks.get(0).nImages, fivStacks.size());
		imp.show();
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
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (nImages<1) return;
		for (int i=n; i<nImages; i++)
			info[i-1] = info[i];
		info[nImages-1] = null;
		nImages--;
	}
	
	/** Returns an ImageProcessor for the specified image,
		were 1<=n<=nImages. Returns null if the stack is empty.
	*/
	public ImageProcessor getProcessor(int n) {
		if (n<1 || n>nImages) {
			IJ.runMacro("waitForUser(\""+n+"\");");
			return fivStacks.get(0).getProcessor(1);
//			throw new IllegalArgumentException("Argument out of range: "+n);
		}
		int z = n % fivStacks.get(0).nImages;
		int t = (int) Math.floor(n/fivStacks.get(0).nImages);
		if (z==0) {
			z = fivStacks.get(0).nImages;
			t=t-1;
		}
//		IJ.log(""+n+" "+z+" "+t);
		return fivStacks.get(t).getProcessor(z);
	 }
 
	 /** Returns the number of images in this stack. */
	public int getSize() {
		return nImages;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (info[0].sliceLabels==null || info[0].sliceLabels.length!=nImages)
			return null;
		else
			return info[0].sliceLabels[n-1];
	}

	public int getWidth() {
		return info[0].width;
	}
	
	public int getHeight() {
		return info[0].height;
	}
 
}
