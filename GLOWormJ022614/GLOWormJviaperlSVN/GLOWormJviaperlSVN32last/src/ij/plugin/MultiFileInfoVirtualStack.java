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
import java.util.Collection;
import java.util.Properties;

public class MultiFileInfoVirtualStack extends VirtualStack implements PlugIn {
	ArrayList<FileInfoVirtualStack> fivStacks = new ArrayList<FileInfoVirtualStack>();
	FileInfo[] info;
	int nImages;
	private String dir;
	private int channelDirectories;
	private String keyString = "";
	private String dimOrder;
	private double min;
	private double max;
	private int largestDirectoryLength;
	private File largestDirectoryFile;
	private String[] cumulativeTiffFileList;
	private FileInfo[] dummyInfo;
	private int largestDirectoryTiffCount;

	/* Default constructor. */
	public MultiFileInfoVirtualStack() {}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo array. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray) {
		info = fiArray;
	}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo 
	array and displays it if 'show' is true. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray, boolean show) {
		info = fiArray;
	}
	
	public MultiFileInfoVirtualStack(String arg, boolean show) {
		openMIVS(arg, "", show);
	}
	
	public MultiFileInfoVirtualStack(String arg, String keyString, boolean show) {
		openMIVS(arg, keyString, show);
	}

	public void openMIVS(String arg, String keyString, boolean show) {
		this.keyString = keyString;
		File argFile = new File(arg);
		dir = "";
		if (!argFile.exists() || !argFile.isDirectory())
			dir = IJ.getDirectory("Select Directory of TIFFs");
		else
			dir = arg;
		if (dir==null) return;
		argFile = new File(dir);
		String[] dirfileList = argFile.list();
		dirfileList = StringSorter.sortNumerically(dirfileList);

		boolean allDirectories = true;
//		String[] bigSubFileList = null;
		ArrayList<String> bigSubFileArrayList = new ArrayList<String>();
		ArrayList<String> cumulativeSubFileArrayList = new ArrayList<String>();
		
		largestDirectoryLength = 0;
		int tiffCount = 0;
		for (String fileName:dirfileList) {
			File subFile = new File(dir+fileName);
			if (fileName.contains("DS_Store"))
				;
			else if ((keyString == "" || subFile.getName().matches(".*"+keyString+".*")) && !subFile.isDirectory()) {
				allDirectories = false;
				if (subFile.getName().toLowerCase().endsWith("tif"))
					tiffCount++;				
			} else if (keyString == "" || subFile.getName().matches(".*"+keyString+".*")){
				channelDirectories++;
				String[] subFileList = subFile.list();
				for (String subFileListElement:subFileList)
					if (!cumulativeSubFileArrayList.contains(subFileListElement))
						if (subFileListElement.toLowerCase().endsWith("tif"))
							cumulativeSubFileArrayList.add(subFileListElement);
			}
		}
		cumulativeTiffFileList = new String[cumulativeSubFileArrayList.size()];
		for (int s=0; s<cumulativeTiffFileList.length; s++) {
			cumulativeTiffFileList [s] = (String) cumulativeSubFileArrayList.get(s);
		}
		cumulativeTiffFileList = StringSorter.sortNumerically(cumulativeTiffFileList);
		for (String fileName:dirfileList) {
			File subFile = new File(dir+fileName);
			if (fileName.contains("DS_Store"))
				;
			else if ((keyString == "" || subFile.getName().matches(".*"+keyString+".*")) && !subFile.isDirectory()) {
				;			
			} else if (keyString == "" || subFile.getName().matches(".*"+keyString+".*")){
				String[] subFileList = subFile.list();
				subFileList = StringSorter.sortNumerically(subFileList);
				ArrayList<String> subFileTiffArrayList = new ArrayList<String>();
				int stuffCount = 0;
				int junkCount = 0;
				for (int q=0; q<cumulativeTiffFileList.length; q++)  {
					String cumTiffListElement = cumulativeTiffFileList[q];
					if (cumTiffListElement.toLowerCase().endsWith(".tif")) {
						if (q-stuffCount+junkCount< subFileList.length 
								&& !subFileList[q-stuffCount+junkCount].matches(cumTiffListElement)) {
							if (subFileList[q-stuffCount+junkCount].toLowerCase().endsWith(".tif")) {
								subFileTiffArrayList.add("stuff");
								stuffCount++;
							} else {
								junkCount++;
								q--;
							}
						} else if (q-stuffCount+junkCount>= subFileList.length) {
							subFileTiffArrayList.add("stuff");
							stuffCount++;
						} else {
							subFileTiffArrayList.add(dir+fileName+File.separator+cumTiffListElement);
						}

					} 
				}
				while (subFileTiffArrayList.remove("junk"));
				
				bigSubFileArrayList.addAll(subFileTiffArrayList);
			}
		}

		if (allDirectories) {
			dimOrder = "xyztc";
			dir = "";

			dirfileList = new String[bigSubFileArrayList.size()];
			for (int s=0; s<dirfileList.length; s++) {
				dirfileList [s] = (String) bigSubFileArrayList.get(s);
			}

		} else {
			dimOrder = "xyczt";
			channelDirectories = 1;
			largestDirectoryTiffCount = tiffCount;
		}
		if (dir.length() > 0 && !dir.endsWith(File.separator))
			dir = dir + File.separator;
		
		if (cumulativeTiffFileList != null) {
			for (String fileName:dirfileList){
				if ((new File(dir + fileName)).canRead()) {
					TiffDecoder td = new TiffDecoder(dir, fileName);
					if (IJ.debugMode) td.enableDebugging();
					IJ.showStatus("Decoding TIFF header...");
					try {dummyInfo = td.getTiffInfo();}
					catch (IOException e) {
						String msg = e.getMessage();
						if (msg==null||msg.equals("")) msg = ""+e;
						IJ.error("TiffDecoder", msg);
						return;
					}
					if (dummyInfo==null || dummyInfo.length==0) {
						continue;
					} else {
						break;
					}
				}
			}

		}	
		
		if (channelDirectories >0) {
			for (String fileName:dirfileList){
				if ((new File(dir + fileName)).canRead()) {
					TiffDecoder td = new TiffDecoder(dir, fileName);
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
				} else if (fileName == "stuff") {
					fivStacks.add(new FileInfoVirtualStack(new FileInfo(), false));
					fivStacks.get(fivStacks.size()-1).info = dummyInfo;
					for (FileInfo dummyInfo:fivStacks.get(fivStacks.size()-1).info)
						dummyInfo.fileName = "stuff";
					fivStacks.get(fivStacks.size()-1).open(false);
				}
			}
			open(show);
		}
	}

	public ArrayList<FileInfoVirtualStack> getFivStacks() {
		return fivStacks;
	}

	public void setFivStacks(ArrayList<FileInfoVirtualStack> fivStacks) {
		this.fivStacks = fivStacks;
	}

	public void addFileInfo(String path) {
		TiffDecoder td = new TiffDecoder((new File(path)).getParent(), (new File(path)).getName());
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
	}
	
	public void run(String arg) {
		new MultiFileInfoVirtualStack(arg, true);
	}
	
	void open(boolean show) {
		nImages = channelDirectories* cumulativeTiffFileList.length * fivStacks.get(0).nImages;
		
		int internalChannels = ((new FileOpener(fivStacks.get(0).info[0])).decodeDescriptionString(fivStacks.get(0).info[0]) != null
				?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).info[0])).decodeDescriptionString(fivStacks.get(0).info[0]), "channels"))
				:1);		
		int channels = channelDirectories * internalChannels;
				
		ImagePlus imp = new ImagePlus(fivStacks.get(0).open(false).getTitle().replaceAll("\\d+\\.", "\\."), this);
		imp.setOpenAsHyperStack(true);				
		if (channelDirectories*fivStacks.get(0).nImages*cumulativeTiffFileList.length!=imp.getStackSize()) {
			if (channelDirectories*fivStacks.get(0).nImages*cumulativeTiffFileList.length>imp.getStackSize()) {
				for (int a=imp.getStackSize();a<channelDirectories*fivStacks.get(0).nImages*cumulativeTiffFileList.length;a++) {
					if (imp.getStack().isVirtual())
						((VirtualStack)imp.getStack()).addSlice("stuff");
					else
						imp.getStack().addSlice(imp.getProcessor().createProcessor(imp.getWidth(), imp.getHeight()));
				}
			} else if (channelDirectories*fivStacks.get(0).nImages*cumulativeTiffFileList.length<imp.getStackSize()) {
				for (int a=channelDirectories*fivStacks.get(0).nImages*cumulativeTiffFileList.length;a<imp.getStackSize();a++) {
					imp.getStack().deleteSlice(channelDirectories*fivStacks.get(0).nImages*cumulativeTiffFileList.length);
				}
			}else {
				IJ.error("HyperStack Converter", "channels x slices x frames <> stack size");
				return;
			}
		}

		imp.setDimensions(channels, fivStacks.get(0).nImages/internalChannels, cumulativeTiffFileList.length);
		if (imp.getOriginalFileInfo() == null) {
			setUpFileInfo(imp);
		}
		imp = new CompositeImage(imp);
		while (!imp.isComposite()) {
			IJ.wait(100);
		}
		((CompositeImage)imp).setMode(CompositeImage.COMPOSITE);
		if (show)
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
		int z = n % fivStacks.get(0).nImages;
		int t = (int) Math.floor(n/fivStacks.get(0).nImages);
		if (z==0) {
			z = fivStacks.get(0).nImages;
			t=t-1;
		}
//		IJ.log(""+n+" "+z+" "+t);
		ImageProcessor ip = fivStacks.get(t).getProcessor(z);
//		ip.setMinAndMax(min, max);
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
			int z = n % fivStacks.get(0).nImages;
			int t = (int) Math.floor(n/fivStacks.get(0).nImages);
			if (z==0) {
				z = fivStacks.get(0).nImages;
				t=t-1;
			}
//			IJ.log(""+n+" "+z+" "+t);
			return fivStacks.get(t).info[0].fileName + " slice "+ z;
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
	
	public String getDimOrder() {
		// TODO Auto-generated method stub
		return dimOrder;
	}

	public void setDimOrder(String dimOrder) {
		this.dimOrder = dimOrder;
	}

	public double getMin() {
		return min;
	}

	public void setMin(double min) {
		this.min = min;
	}

	public double getMax() {
		return max;
	}

	public void setMax(double max) {
		this.max = max;
	}

	public String getDir() {
		return dir;
	}


 
}
