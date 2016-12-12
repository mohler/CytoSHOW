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

import org.vcell.gloworm.QTVirtualStack;

public class MultiFileInfoVirtualStack extends VirtualStack implements PlugIn {
	ArrayList<FileInfoVirtualStack> fivStacks = new ArrayList<FileInfoVirtualStack>();
	FileInfo[] info;
	ArrayList<FileInfo[]> infoCollector =new ArrayList<FileInfo[]>();;

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
	private String infoDir;
	private int  cDim, zDim, tDim;
	private int  vDim=1;
	public int stackNumber;
	public int sliceNumber;
	private boolean isViewB;

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
	
	public MultiFileInfoVirtualStack(String dirOrOMETiff, String string, boolean show) {
		this(dirOrOMETiff, string, 0, 0, 0, 1, false, show);
	}

	public MultiFileInfoVirtualStack(String dirOrOMETiff, String string, boolean isViewB, boolean show) {
		this(dirOrOMETiff, string, 0, 0, 0, 1, isViewB, show);
	}

	public MultiFileInfoVirtualStack(String arg, String keyString, int cDim, int zDim, int tDim, int vDim, boolean isViewB, boolean show) {
		this.keyString = keyString;
		this.isViewB = isViewB;
		this.cDim = cDim;
		this.zDim = zDim;
		this.tDim = tDim;
		this.vDim = vDim;
		File argFile = new File(arg);
		dir = "";
		if (!argFile.exists() || !argFile.isDirectory()) {
			dir = IJ.getDirectory("Select Directory of TIFFs");
			keyString = IJ.getString("Subdirectory Name Key String?", "Deconvolution");
		}
		else
			dir = arg;
		if (dir==null) return;
		if (dir.length() > 0 && !dir.endsWith(File.separator))
			dir = dir + File.separator;
		infoDir = dir;
		argFile = new File(dir);
		String[] dirFileList = argFile.list();
		dirFileList = StringSorter.sortNumerically(dirFileList);

		boolean allDirectories = true;
//		String[] bigSubFileList = null;
		ArrayList<String> bigSubFileArrayList = new ArrayList<String>();
		ArrayList<String> cumulativeSubFileArrayList = new ArrayList<String>();
		
		largestDirectoryLength = 0;
		int tiffCount = 0;
		for (String fileName:dirFileList) {
			File subFile = new File(dir+fileName);
			if (fileName.contains("DS_Store"))
				;
			else if ((keyString == "" || subFile.getName().matches(".*"+keyString+".*")) && !subFile.isDirectory()) {
				allDirectories = false;
				if (subFile.getName().toLowerCase().endsWith("tif")) {
					cumulativeSubFileArrayList.add(subFile.getName());
					tiffCount++;				
				}
			}
		}
		if (tiffCount == 0) {
			for (String fileName:dirFileList) {
				File subFile = new File(dir+fileName);
				if (fileName.contains("DS_Store"))
					;
				else if (keyString == "" || (subFile.getName().matches(".*"+keyString+".*") && !subFile.getName().startsWith("Proj_"))){
					channelDirectories++;
					String[] subFileList = subFile.list();
					for (String subFileListElement:subFileList)
						if (!cumulativeSubFileArrayList.contains(dir+fileName+File.separator+subFileListElement))
							if (subFileListElement.toLowerCase().endsWith("tif"))
								cumulativeSubFileArrayList.add(dir+fileName+File.separator+subFileListElement);
				}
			}
		}
		cumulativeTiffFileList = new String[cumulativeSubFileArrayList.size()];
		for (int s=0; s<cumulativeTiffFileList.length; s++) {
			cumulativeTiffFileList [s] = (String) cumulativeSubFileArrayList.get(s);
		}
		cumulativeTiffFileList = StringSorter.sortNumerically(cumulativeTiffFileList);
		for (String fileName:dirFileList) {
			File subFile = new File(dir+fileName);
			boolean noKeyString = keyString == "";
			boolean subFileDir = subFile.isDirectory();
			if (fileName.contains("DS_Store")) {
				IJ.log(".");
				
			} else if ((noKeyString || subFile.getName().matches(".*"+keyString+".*")) && !subFileDir) {
				IJ.log(".");
			
			} else if (noKeyString || (subFile.getName().matches(".*"+keyString+".*") && !subFile.getName().startsWith("Proj_"))){
				String[] subFileList = subFile.list();
				subFileList = StringSorter.sortNumerically(subFileList);
				ArrayList<String> subFileTiffArrayList = new ArrayList<String>();

				if(keyString.toLowerCase().startsWith("decon")) {

					int stuffCount = 0;
					int junkCount = 0;
					for (int q=0; q<cumulativeTiffFileList.length; q++)  {
						String cumTiffListElement = cumulativeTiffFileList[q];
						if (cumTiffListElement.toLowerCase().endsWith(".tif")) {
							if (q-stuffCount+junkCount< subFileList.length 
									&& !subFileList[q-stuffCount+junkCount].matches(cumTiffListElement)) {
								if (subFileList[q-stuffCount+junkCount].toLowerCase().endsWith(".tif")) {
									subFileTiffArrayList.add("channel-frame missing");
									stuffCount++;
								} else {
									junkCount++;
									q--;
								}
							} else if (q-stuffCount+junkCount>= subFileList.length) {

								subFileTiffArrayList.add("channel-frame missing");
								stuffCount++;
							} else {
								subFileTiffArrayList.add(dir+fileName+File.separator+cumTiffListElement);
							}

						} 
					}
					while (subFileTiffArrayList.remove("junk"));
				} else {
					for (String subFilePath:subFileList)
						subFileTiffArrayList.add(dir+fileName+File.separator+subFilePath);
				}

				bigSubFileArrayList.addAll(subFileTiffArrayList);
			}
		}

		
		String[] goDirFileList = {""};

		if (allDirectories) {
			dimOrder = "xyztc";
			dir = "";

			goDirFileList = new String[bigSubFileArrayList.size()];
			for (int s=0; s<goDirFileList.length; s++) {
				goDirFileList [s] = (String) bigSubFileArrayList.get(s);
			}

		} else {
			dimOrder = "xyczt";
			channelDirectories = 1;
			largestDirectoryTiffCount = tiffCount;
			goDirFileList = cumulativeTiffFileList;
		}
		if (dir.length() > 0 && !dir.endsWith(File.separator))
			dir = dir + File.separator;
		

		if (goDirFileList != null) {
			for (String fileName:goDirFileList){
				if ((new File(dir + fileName)).exists()) {

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
			for (String fileName:goDirFileList){
				if ((new File(dir + fileName)).canRead() && fileName.toLowerCase().endsWith(".tif")) {
					TiffDecoder td = new TiffDecoder(dir, fileName);
					if (IJ.debugMode) td.enableDebugging();
					IJ.showStatus("Decoding TIFF header...");
					try {infoCollector.add(td.getTiffInfo());}
					catch (IOException e) {
						String msg = e.getMessage();
						if (msg==null||msg.equals("")) msg = ""+e;
						IJ.error("TiffDecoder", msg);
						return;
					}
					if (infoCollector==null || infoCollector.size()==0) {
						continue;
					}
					if (IJ.debugMode)
						IJ.log(info[0].debugInfo);
					fivStacks.add(new FileInfoVirtualStack());
					fivStacks.get(fivStacks.size()-1).info = infoCollector.get(infoCollector.size()-1);
					fivStacks.get(fivStacks.size()-1).open(false);
//					if (fivStacks.get(fivStacks.size()-1).nImages == 1 && 1 < fivStacks.get(0).nImages)
//						fivStacks.get(fivStacks.size()-1).nImages = fivStacks.get(0).nImages;
				} else if (fileName == "channel-frame missing") {
					fivStacks.add(new FileInfoVirtualStack(new FileInfo(), false));
					fivStacks.get(fivStacks.size()-1).info = dummyInfo;
					for (FileInfo dummyInfo:fivStacks.get(fivStacks.size()-1).info)
						dummyInfo.fileName = "channel-frame missing";
					fivStacks.get(fivStacks.size()-1).open(false);
				}
			}
			if (fivStacks.size() > 0) {
				ArrayList<FileInfo> infoArrayList = new ArrayList<FileInfo>();
				for (FileInfo[] fia:infoCollector) {
					for (FileInfo fi:fia) {
						infoArrayList.add(fi);
					}
				}
				info = new FileInfo[infoArrayList.size()];
				for (int f=0;f<info.length;f++) {;
					info[f] = (FileInfo) infoArrayList.get(f);
				}
				open(show);
			}
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
		new MultiFileInfoVirtualStack(arg, "", true);
	}
	
	void open(boolean show) {
		if (cumulativeTiffFileList[0].contains("MMStack_"))  {
			nImages = 0;
			for (FileInfoVirtualStack mmStack:fivStacks) {
				nImages = nImages + mmStack.getSize();
			}
			if (cDim == 0 || tDim == 0 || tDim == 0) {
				GenericDialog gd = new GenericDialog("Dimensions of HyperStacks");
				gd.addNumericField("Channels (c):", 2, 0);
				gd.addNumericField("Slices (z):", 50, 0);
				gd.addNumericField("Frames (t):", nImages/(50*2*2), 0);
				gd.showDialog();
				if (gd.wasCanceled()) return;
				cDim = (int) gd.getNextNumber();
				zDim = (int) gd.getNextNumber();
				tDim = (int) gd.getNextNumber();
			} else {
//				cDim=1;
//				zDim=1;
//				tDim=1;
			}
		} else {
			zDim = fivStacks.get(0).nImages;
			nImages = channelDirectories* cumulativeTiffFileList.length * zDim;

			int internalChannels = ((new FileOpener(fivStacks.get(0).info[0])).decodeDescriptionString(fivStacks.get(0).info[0]) != null
					?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).info[0])).decodeDescriptionString(fivStacks.get(0).info[0]), "channels"))
							:1);		
			int channels = channelDirectories * internalChannels;
			cDim = channels;
			zDim = fivStacks.get(0).nImages/(cDim/channelDirectories);
			tDim = cumulativeTiffFileList.length;
		}
		
		String[] dirChunks = dir.split("\\"+File.separator);
		ImagePlus imp = new ImagePlus(
				dirChunks[dirChunks.length-1]+"_"+
				fivStacks.get(0).open(false).getTitle().replaceAll("\\d+\\.", "\\."), this);
		imp.setOpenAsHyperStack(true);				
		if (cDim*zDim*tDim != imp.getStackSize()) {
			if (cDim*zDim*tDim > imp.getStackSize()) {
				for (int a=imp.getStackSize();a<cDim*zDim*tDim;a++) {
					if (imp.getStack().isVirtual())
						((VirtualStack)imp.getStack()).addSlice("blank slice");
					else
						imp.getStack().addSlice(imp.getProcessor().createProcessor(imp.getWidth(), imp.getHeight()));
				}
			} else if (cDim*zDim*tDim < imp.getStackSize()) {
				for (int a=imp.getStackSize();a>cDim*zDim*tDim;a--) {
					imp.getStack().deleteSlice(a);
				}
			}else {
				IJ.error("HyperStack Converter", "channels x slices x frames <> stack size");
				return;
			}
		}

		imp.setDimensions(cDim, zDim, tDim);
		if (imp.getOriginalFileInfo() == null) {
			setUpFileInfo(imp);
		}
		if(imp.getType()!=ImagePlus.COLOR_RGB) {
				imp = new CompositeImage(imp);
		
				while (!imp.isComposite()) {
					IJ.wait(100);
				}
				((CompositeImage)imp).setMode(CompositeImage.COMPOSITE);
		}
		if (show)
			imp.show();
	}

	public void setUpFileInfo(ImagePlus imp) {
		imp.setFileInfo(new FileInfo());
		FileInfo fi = imp.getOriginalFileInfo();
		fi.width = width;
		fi.height = height;
		fi.nImages = this.getSize();
		fi.directory = infoDir;
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
//		if (n<1 || n>nImages)
//			throw new IllegalArgumentException("Argument out of range: "+n);
//		if (nImages<1) return;
//		for (int i=n; i<nImages; i++)
//			info[i-1] = info[i];
//		info[nImages-1] = null;
//		nImages--;
		if (n<1 || n>nImages) {
			IJ.runMacro("waitForUser(\""+n+"\");");
		}
		int stackNumber = 0;
		int sliceNumber = 1;
		int total=0;
		while (n > total) {
			total = total + fivStacks.get(stackNumber).getSize();
			stackNumber++;
		}
		stackNumber--;
		total = total - fivStacks.get(stackNumber).getSize();

		sliceNumber = n - total;
		
		fivStacks.get(stackNumber).deleteSlice(sliceNumber);
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
		
		stackNumber = 0;
		sliceNumber = 1;
		int total=0;
		while (n > total) {
			total = total + fivStacks.get(stackNumber).getSize()/vDim;
			stackNumber++;
		}
		stackNumber--;

		n = n + stackNumber*fivStacks.get(stackNumber).getSize()/vDim;
		
		sliceNumber = (n-1) % (fivStacks.get(stackNumber).getSize()/vDim) + 1;
		
//		IJ.log(""+n+" "+z+" "+t);
		ImageProcessor ip = fivStacks.get(stackNumber).getProcessor(sliceNumber+(isViewB?fivStacks.get(stackNumber).getSize()/vDim:0));
		ip.setInterpolationMethod(ImageProcessor.BICUBIC);
		if (this.getOwnerImps() != null && this.getOwnerImps().size() > 0 && this.getOwnerImps().get(0) != null) {
			ip.translate(skewXperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2), skewYperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2));
		} else {
			ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));
		}
//		ip.setMinAndMax(min, max);
		return ip;
	 }
 
	 /** Returns the number of images in this stack. */
	public int getSize() {
		return nImages/vDim;
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
			return fivStacks.get(stackNumber).info[0].fileName + " slice "+ sliceNumber;
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

	public FileInfoVirtualStack getVirtualStack(int number){
		if (fivStacks == null)
			return null;
		return ((FileInfoVirtualStack)fivStacks.get(number));
	}

 
}
