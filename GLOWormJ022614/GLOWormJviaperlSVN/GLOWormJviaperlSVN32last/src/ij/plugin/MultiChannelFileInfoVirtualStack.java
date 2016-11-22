package ij.plugin;

import ij.*;
import ij.measure.Calibration;
import ij.process.*;
import ij.util.StringSorter;
import ij.gui.*;
import ij.io.*;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

public class MultiChannelFileInfoVirtualStack extends VirtualStack implements PlugIn {
	ArrayList<FileInfoVirtualStack> fivStacks = new ArrayList<FileInfoVirtualStack>();
	FileInfo[] info;
	int nImages;
	int nSlices;
	private String dir;

	public String getDir() {
		return dir;
	}

	/* Default constructor. */
	public MultiChannelFileInfoVirtualStack() {}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo array. */
	public MultiChannelFileInfoVirtualStack(FileInfo[] fiArray) {
		info = fiArray;
	}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo 
	array and displays it if 'show' is true. */
	public MultiChannelFileInfoVirtualStack(FileInfo[] fiArray, boolean show) {
		info = fiArray;
	}
	
	public MultiChannelFileInfoVirtualStack(String arg) {
		File argFile = new File(arg);
		dir = "";
		if (!argFile.exists() && !argFile.isDirectory())
			dir = IJ.getDirectory("Select Directory of TIFFs");
		else
			dir = arg;
		if (dir==null) return;
		argFile = new File(dir);
		String[] fileList = argFile.list();
		fileList = StringSorter.sortNumerically(fileList);
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
			nImages = fivStacks.size() * fivStacks.get(0).nImages;
		}
	}

	private double getDouble(Properties props, String key) {
		Double n = getNumber(props, key);
		return n!=null?n.doubleValue():0.0;
	}

	public ArrayList<FileInfoVirtualStack> getFivStacks() {
		return fivStacks;
	}

	public void setFivStacks(ArrayList<FileInfoVirtualStack> fivStacks) {
		this.fivStacks = fivStacks;
	}

	public void addFileInfo(String path) {
		TiffDecoder td = new TiffDecoder("", path);
		if (IJ.debugMode) td.enableDebugging();
		IJ.showStatus("Decoding TIFF header...");
		FileInfo[] fi = null;
		try {fi = td.getTiffInfo();}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return;
		}
		if (info==null || info.length==0) {
			return;
		}
		if (IJ.debugMode)
			IJ.log(info[0].debugInfo);
		fivStacks.add(new FileInfoVirtualStack());
		fivStacks.get(fivStacks.size()-1).info = fi;
		nImages = fivStacks.size() * fivStacks.get(0).nImages;
		Properties props = (new FileOpener(info[0])).decodeDescriptionString(info[0]);
		nSlices = (int)getDouble(props,"slices");;
	}
	
	public void run(String arg) {
		File argFile = new File(arg);
		dir = "";
		if (!argFile.exists() && !argFile.isDirectory())
			dir = IJ.getDirectory("Select Directory of TIFFs");
		else
			dir = arg;
		if (dir==null) return;
		argFile = new File(dir);
		String[] fileList = argFile.list();
		fileList = StringSorter.sortNumerically(fileList);
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
			Properties props = (new FileOpener(info[0])).decodeDescriptionString(info[0]);
			nSlices = (int)getDouble(props,"slices");;
		}
		open(true);
	}
	
	void open(boolean show) {
		nImages = fivStacks.size() * fivStacks.get(0).nImages;
		ImagePlus imp = new ImagePlus(fivStacks.get(0).open(false).getTitle().replaceAll("\\d+\\.", "\\."), this);
		imp.setOpenAsHyperStack(true);
		imp.setDimensions(fivStacks.size(), nSlices, fivStacks.get(0).nImages/nSlices);
		if (imp.getOriginalFileInfo() == null) {
			setUpFileInfo(imp);
		}
		imp = new CompositeImage(imp);
		imp.show();
	}

	public void setUpFileInfo(ImagePlus imp) {
		imp.setFileInfo(new FileInfo());
		FileInfo fi = imp.getOriginalFileInfo();
		fi.width = width;
		fi.height = height;
		fi.nImages = this.getSize();
		fi.directory = dir;
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
		int c = (n-1) % (fivStacks.size());
		ImageProcessor ip = fivStacks.get(c).getProcessor((int)Math.floor(((double)n-1)/fivStacks.size()));
		ip.setInterpolationMethod(ImageProcessor.BICUBIC);
		if (this.getOwnerImps() != null && this.getOwnerImps().size() > 0 && this.getOwnerImps().get(0) != null) {
			ip.translate(skewXperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2), skewYperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2));
		} else {
			ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));
		}
		return ip;
	 }
 
	 /** Returns the number of images in this stack. */
	public int getSize() {
		return nImages;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (info[0].sliceLabels==null || info[0].sliceLabels.length!=nImages) {
			if (n<1 || n>nImages) {
				IJ.runMacro("waitForUser(\""+n+"\");");
				return fivStacks.get(0).info[0].fileName;
//				throw new IllegalArgumentException("Argument out of range: "+n);
			}
			int c = (n-1) % (fivStacks.size());
			return fivStacks.get(c).info[0].fileName + " slice "+ (1+((int)Math.floor(((double)n-1)/fivStacks.size())));
		}
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
