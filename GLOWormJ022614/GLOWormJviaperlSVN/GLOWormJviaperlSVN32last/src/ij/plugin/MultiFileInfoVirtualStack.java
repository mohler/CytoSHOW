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
	ArrayList<FileInfoVirtualStack> fivStacks;
	FileInfo[] infoArray;
	ArrayList<FileInfo[]> infoCollectorArrayList;
	ArrayList<String> touchedFiles;
	int nImages;
	private String dir;
	private int channelDirectories;
	private String keyString = "";
	private String dimOrder;
	private double min;
	private double max;
	private int largestDirectoryLength;
	private File largestDirectoryFile;
	private String[] cumulativeTiffFileArray;
	private FileInfo[] dummyInfoArray;
	private int largestDirectoryTiffCount;
	private String infoDir;
	private int  cDim;
	private int zDim;
	public int tDim;
	private int  vDim=1;
	public int stackNumber;
	public int sliceNumber;
	private boolean isViewB;
	private boolean monitoringDecon;

	/* Default constructor. */
	public MultiFileInfoVirtualStack() {}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo array. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray) {
		infoArray = fiArray;
	}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo 
	array and displays it if 'show' is true. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray, boolean show) {
		infoArray = fiArray;
	}
	
	public MultiFileInfoVirtualStack(String dirOrOMETiff, String string, boolean show) {
		this(dirOrOMETiff, "xyczt", string, 0, 0, 0, 1, -1, false, show);
	}

	public MultiFileInfoVirtualStack(String dirOrOMETiff, String string, boolean isViewB, boolean show) {
		this(dirOrOMETiff, "xyczt", string, 0, 0, 0, 1, -1, isViewB, show);
	}

	public MultiFileInfoVirtualStack(String arg, String sliceOrder, String keyString, int cDim, int zDim, int tDim, int vDim, int pos, boolean isViewB, boolean show) {
		this.keyString = keyString;
		this.isViewB = isViewB;
		this.cDim = cDim;
		this.zDim = zDim;
		this.tDim = tDim;
		this.vDim = vDim;
		this.dimOrder = sliceOrder;
		fivStacks = new ArrayList<FileInfoVirtualStack>();
		
		infoCollectorArrayList =new ArrayList<FileInfo[]>();;
		touchedFiles = new ArrayList<String>();

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
					cumulativeSubFileArrayList.add(dir+fileName);
					tiffCount++;				
				}
			}
		}
		if (tiffCount == 0) {
			ArrayList<ArrayList<String>> channelArraryLists = new ArrayList<ArrayList<String>>();
			for (String fileName:dirFileList) {
				File subFile = new File(dir+fileName);
				if (fileName.contains("DS_Store"))
					;
				else if (keyString == "" || (subFile.getName().matches(".*"+keyString+".*") && !subFile.getName().startsWith("Proj_"))){
					channelDirectories++;
					String[] subFileList = subFile.list();
					channelArraryLists.add(new ArrayList<String>());
					IJ.log(fileName +"???  "+keyString );
					for (String subFileListElement:subFileList) {
						if (!cumulativeSubFileArrayList.contains(dir+fileName+File.separator+subFileListElement)) {
							if ((pos ==-1 ||subFileListElement.toLowerCase().contains("_pos"+pos+"."))
									&&  subFileListElement.toLowerCase().endsWith("tif")) {
								channelArraryLists.get(channelDirectories-1).add(dir+fileName+File.separator+subFileListElement);
								cumulativeSubFileArrayList.add(dir+fileName+File.separator+subFileListElement);
							}
						}
					}
				}
			}
			int lowestSpan = Integer.MAX_VALUE;
			for (ArrayList<String> al:channelArraryLists) {
				if (lowestSpan > al.size()){
					lowestSpan = al.size();
				}
			}
			for (ArrayList<String> al:channelArraryLists) {
				for (int zap=al.size();zap>=lowestSpan;zap--) {
					if (al.size()>zap) {
						cumulativeSubFileArrayList.remove(al.get(zap-1));
					}
				}
			}

			
		}
		if (cumulativeSubFileArrayList.size() != 0) {

			cumulativeTiffFileArray = new String[cumulativeSubFileArrayList.size()];
			int highT = 0;
			for (int s=0; s<cumulativeTiffFileArray.length; s++) {
				cumulativeTiffFileArray[s] = (String) cumulativeSubFileArrayList.get(s);
				String[] subFilePathChunks = cumulativeTiffFileArray[s].split(File.separator.replace("\\", "\\\\"));
				String subFileName = subFilePathChunks[subFilePathChunks.length-1];
				if (subFileName.matches(".*_t\\d+.*\\.tif")) {
					int tValue = Integer.parseInt(subFileName.replaceAll(".*_t(\\d+).*\\.tif", "$1"));
					if (tValue > highT)
						highT = tValue;
				}
				if (subFileName.matches("proj._\\d+_\\d+.tif")) {
					int tValue = Integer.parseInt(subFileName.replaceAll("proj._\\d+_(\\d+).tif", "$1"));
					if (tValue > highT)
						highT = tValue;
				}
				if (highT > 0)
					dimOrder = "xyztc";
			}
			cumulativeTiffFileArray = StringSorter.sortNumerically(cumulativeTiffFileArray);

			if (cumulativeTiffFileArray.length >0){ 
				for (String cumulativeTiffFileArrayElement:cumulativeTiffFileArray)
					bigSubFileArrayList.add(cumulativeTiffFileArrayElement);
			} else { 

				for (String fileName:dirFileList) {
					File subFile = new File(dir+fileName);
					boolean noKeyString = keyString == "";
					boolean subFileIsDir = subFile.isDirectory();
					if (fileName.contains("DS_Store")) {
						IJ.log(".");

					} else if ((noKeyString || subFile.getName().matches(".*"+keyString+".*")) && !subFileIsDir) {
						IJ.log(".");

					} else if (noKeyString || (subFile.getName().matches(".*"+keyString+".*") && !subFile.getName().startsWith("Proj_"))){
						String[] subFileList = subFile.list();
						subFileList = StringSorter.sortNumerically(subFileList);
						ArrayList<String> subFileTiffArrayList = new ArrayList<String>();


						for (String subFilePath:subFileList)
							subFileTiffArrayList.add(dir+fileName+File.separator+subFilePath);


						bigSubFileArrayList.addAll(subFileTiffArrayList);
					}
				}
			}

			monitoringDecon = keyString.toLowerCase().contains("deconvolution") 
								|| keyString.toLowerCase().contains("color");
				
			String[] goDirFileList = {""};

			if (allDirectories) {
				//			dimOrder = "xyztc";
				dir = "";

				goDirFileList = new String[bigSubFileArrayList.size()];
				for (int s=0; s<goDirFileList.length; s++) {
					goDirFileList [s] = (String) bigSubFileArrayList.get(s);
				}

			} else {
				//			dimOrder = "xyczt";
				dir = "";
				channelDirectories = 1;
				largestDirectoryTiffCount = tiffCount;
				goDirFileList = cumulativeTiffFileArray;
			}
			if (dir.length() > 0 && !dir.endsWith(File.separator))
				dir = dir + File.separator;


			if (goDirFileList != null) {
				for (String fileName:goDirFileList){
					if ((new File(fileName)).exists()) {

						TiffDecoder td = new TiffDecoder(dir, fileName);
						if (IJ.debugMode) td.enableDebugging();
						IJ.showStatus("Decoding TIFF header...");
						try {dummyInfoArray = td.getTiffInfo(0);}
						catch (IOException e) {
							String msg = e.getMessage();
							if (msg==null||msg.equals("")) msg = ""+e;
							IJ.error("TiffDecoder", msg);
							return;
						}
						//					int prevOffset = 0;
						//					for (FileInfo fi:dummyInfoArray) {
						//						IJ.log(" "+fi.offset+" "+(fi.offset-prevOffset)+" "+fi.longOffset+" "+fi.gapBetweenImages);
						//						prevOffset = fi.offset;
						//					}
						if (dummyInfoArray==null || dummyInfoArray.length==0) {
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
						if (dummyInfoArray == null) {
							TiffDecoder td = new TiffDecoder(dir, fileName);
							if (IJ.debugMode) td.enableDebugging();
							IJ.showStatus("Decoding TIFF header...");
							try {infoCollectorArrayList.add(td.getTiffInfo(0));}
							catch (IOException e) {
								String msg = e.getMessage();
								if (msg==null||msg.equals("")) msg = ""+e;
								IJ.error("TiffDecoder", msg);
								return;
							}
						} else {
							TiffDecoder td = new TiffDecoder(dir, fileName);
							if (IJ.debugMode) td.enableDebugging();
							IJ.showStatus("Decoding  TIFF image headers..."+fileName);
							//						long[] tiOffsetsArray = new long[dummyInfoArray.length];
							//						try {
							//							tiOffsetsArray = td.getTiffImageOffsets(0);
							//						} catch (IOException e) {
							//							// TODO Auto-generated catch block
							//							e.printStackTrace();
							//						}
							infoCollectorArrayList.add(new FileInfo[dummyInfoArray.length]);
							for (int si=0; si<infoCollectorArrayList.get(infoCollectorArrayList.size()-1).length; si++) {
								infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[si] = (FileInfo) dummyInfoArray[si].clone();
								infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[si].fileName = fileName;
								//								infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[si].longOffset = tiOffsetsArray[si];
								//								infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[si].offset = (int)tiOffsetsArray[si];
							}

						}
						if (infoCollectorArrayList==null || infoCollectorArrayList.size()==0) {
							continue;
						}
						fivStacks.add(new FileInfoVirtualStack());
						fivStacks.get(fivStacks.size()-1).infoArray = infoCollectorArrayList.get(infoCollectorArrayList.size()-1);
						fivStacks.get(fivStacks.size()-1).setupStack();
					} else if (fileName.matches(".*channel.*-frame.* missing")) {
						fivStacks.add(new FileInfoVirtualStack(new FileInfo(), false));
						for (FileInfo sliceInfo:fivStacks.get(fivStacks.size()-1).infoArray)
							sliceInfo.fileName = fileName;
						fivStacks.get(fivStacks.size()-1).setupStack();
					}
				}
				if (fivStacks.size() > 0) {
					ArrayList<FileInfo> infoArrayList = new ArrayList<FileInfo>();
					for (FileInfo[] fia:infoCollectorArrayList) {
						for (FileInfo fi:fia) {
							infoArrayList.add(fi);
						}
					}
					infoArray = new FileInfo[infoArrayList.size()];
					for (int f=0;f<infoArray.length;f++) {
						infoArray[f] = (FileInfo) infoArrayList.get(f);
					}
					open(show);
				}
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
		try {
			fi = td.getTiffInfo(0);
		}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return;
		}
		if (infoArray==null || infoArray.length==0) {
			return;
		}
		if (IJ.debugMode)
			IJ.log(infoArray[0].debugInfo);
		fivStacks.add(new FileInfoVirtualStack());
		fivStacks.get(fivStacks.size()-1).infoArray = fi;
		nImages = fivStacks.size() * fivStacks.get(0).nImages*(dimOrder == "xySplitCzt"?2:1);
	}
	
	public void run(String arg) {
		new MultiFileInfoVirtualStack(arg, "", true);
	}
	
	void open(boolean show) {
		if (cumulativeTiffFileArray.length >0 && cumulativeTiffFileArray[0].contains("MMStack_"))  {
			nImages = 0;
			for (FileInfoVirtualStack mmStack:fivStacks) {
				nImages = nImages + mmStack.getSize()*(dimOrder == "xySplitCzt"?2:1);
			}
			if (cDim == 0 || zDim == 0 || tDim == 0) {
				GenericDialog gd = new GenericDialog("Dimensions of HyperStacks");
				gd.addNumericField("Channels (c):", 2, 0);
				gd.addNumericField("Slices (z):", 50, 0);
				gd.addNumericField("Frames (t):", nImages/(50*2*2), 0);
				gd.showDialog();
				if (gd.wasCanceled()) return;
				cDim = (int) gd.getNextNumber();
				zDim = (int) gd.getNextNumber();
				tDim = (int) gd.getNextNumber();
				nImages = cDim*zDim*tDim;
			} else {
				this.tDim =nImages/(this.cDim*this.zDim);
			}
		} else if (monitoringDecon){
			zDim = fivStacks.get(0).nImages;
			nImages = fivStacks.size() * zDim*(dimOrder == "xySplitCzt"?2:1);

			int internalChannels = ((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]) != null
					?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]), "channels"))
							:1);		
			int channels = channelDirectories * internalChannels;
			cDim = channels;
			zDim = fivStacks.get(0).nImages/(cDim/channelDirectories);
			tDim = fivStacks.size()/cDim;
		} else {
			zDim = fivStacks.get(0).nImages;
			nImages = /*channelDirectories**/ fivStacks.size() * zDim*(dimOrder == "xySplitCzt"?2:1);

			int internalChannels = ((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]) != null
					?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]), "channels"))
							:1);		
			int channels = channelDirectories * internalChannels;
			cDim = channels;
			zDim = fivStacks.get(0).nImages/(cDim/channelDirectories);
			tDim = fivStacks.size()/(cDim/internalChannels);
		}
		
		String[] dirChunks = dir.split("\\"+File.separator);
		ImagePlus fivImpZero = fivStacks.get(0).open(false);
		ImagePlus imp = new ImagePlus(
				dirChunks[dirChunks.length-1]+"_"+
				fivImpZero.getTitle().replaceAll("\\d+\\.", "\\."), this);
		fivImpZero.flush();
		imp.setOpenAsHyperStack(true);			
		int cztDims = cDim*zDim*tDim;
		int impSize = imp.getStackSize()*vDim;
		if (cztDims!= impSize) {
			if (cztDims > impSize) {
				for (int a=imp.getStackSize();a<cDim*zDim*tDim;a++) {
					if (imp.getStack().isVirtual())
						((VirtualStack)imp.getStack()).addSlice("blank slice");
					else
						imp.getStack().addSlice(imp.getProcessor().createProcessor(imp.getWidth(), imp.getHeight()));
				}
			} else if (cztDims < impSize) {
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

	/** Deletes the specified image, where 1<=n<=nImages. */
	public void deleteSlice(int n) {

		if (n<1 || n>nImages) {
			IJ.runMacro("waitForUser(\""+n+"\");");
		}
		int stackNumber = 0;
		int sliceNumber = 1;
		int total=0;
		while (n > total && stackNumber<fivStacks.size()) {
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
		where 1<=n<=nImages. Returns null if the stack is empty.
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
//		while (n > total) {
//			total = total + fivStacks.get(stackNumber).getSize()*(dimOrder == "xySplitCzt"?2:1)/vDim;
//			stackNumber++;
//		}
//		stackNumber--;
//
//		n = n + stackNumber*fivStacks.get(stackNumber).getSize()*(dimOrder == "xySplitCzt"?2:1)/vDim;
//		
//			sliceNumber = (n-1) % (fivStacks.get(stackNumber).getSize()*(dimOrder == "xySplitCzt"?2:1)/vDim);
//
//			if (stackNumber>=0 && sliceNumber>=0) {
//				if (!touchedFiles.contains(fivStacks.get(stackNumber).infoArray[sliceNumber].fileName)) {
//					TiffDecoder td = new TiffDecoder(dir, fivStacks.get(stackNumber).infoArray[sliceNumber].fileName);
//					if (IJ.debugMode) td.enableDebugging();
//					IJ.showStatus("Decoding TIFF header...");
//					try {infoCollectorArrayList.set(stackNumber, td.getTiffInfo(0));}
//					catch (IOException e) {
//						String msg = e.getMessage();
//						if (msg==null||msg.equals("")) msg = ""+e;
//						IJ.error("TiffDecoder", msg);
//					}
//					fivStacks.get(stackNumber).infoArray = infoCollectorArrayList.get(stackNumber);
//					ImagePlus fivImpSN = fivStacks.get(stackNumber).open(false);
//					touchedFiles.add(fivStacks.get(stackNumber).infoArray[sliceNumber].fileName);
//				}
//			} 
//
			//		IJ.log(""+n+" "+z+" "+t);
		ImageProcessor ip = null;
		if (dimOrder == "xyczt") {
//			ip = fivStacks.get(stackNumber).getProcessor(sliceNumber+1+(isViewB?fivStacks.get(stackNumber).getSize()/vDim:0));
			ImagePlus imp = null;		
			if (n<=nImages ) {
				int nCorr = (n-1) + (zDim*vDim)*((n-1)/(zDim*cDim/vDim)) + (isViewB?zDim*cDim/vDim:0);
				IJ.log(n + "=>" + nCorr);
				infoArray[nCorr].nImages = 1; // why is this needed?
				FileOpener fo = new FileOpener(infoArray[nCorr]);
				imp = fo.open(false);
			}
			if (imp!=null) {
				ip = imp.getProcessor();
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
//					default: return getProcessor(1).createProcessor(w, h);
					default: return new ShortProcessor(w, h);
				}
			}
		}
		if (dimOrder == "xySplitCzt") {
			int dX = -11;
			int dY = 7;
			ImagePlus imp = null;		
			if (n<=nImages ) {
				int nCorr = ((n-1)/2)+    (zDim)*((n-1)/(zDim*vDim))     +(isViewB?(zDim):0);
				IJ.log(n + "=>" + nCorr);
				infoArray[nCorr].nImages = 1; // why is this needed?
				FileOpener fo = new FileOpener(infoArray[nCorr]);
				imp = fo.open(false);
			}
			if (imp!=null) {
				ip = imp.getProcessor();
				ip.setInterpolationMethod(ImageProcessor.BICUBIC);
				if (this.getOwnerImps() != null && this.getOwnerImps().size() > 0 && this.getOwnerImps().get(0) != null) {
					ip.translate(skewXperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2), skewYperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2));
				} else {
					ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));
				}
				ip.setRoi(1280-((1-n%2)*(1024+dX)), 0+((1-n%2)*(0+dY)), 512, 512-dY);
				ip=ip.crop();

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
//					default: return getProcessor(1).createProcessor(w, h);
					default: return new ShortProcessor(w, h);
				}
			}
		}
		if (dimOrder == "xyzct")
			ip = fivStacks.get(stackNumber).getProcessor(sliceNumber/cDim + ((sliceNumber%cDim)*fivStacks.get(stackNumber).getSize()/(vDim))
																		+(isViewB?fivStacks.get(stackNumber).getSize()/(cDim*vDim):0));
		if (dimOrder == "xyztc")
			ip = fivStacks.get(stackNumber).getProcessor(sliceNumber);
		
		if (ip instanceof FloatProcessor) {
//			ip = ip.convertToShort(false);
		}
		int[] ipHis = ip.getHistogram();
  		double ipHisMode = 0.0;
  		int ipHisLength = ipHis.length;
  		int ipHisMaxBin = 0;
  		for (int h=0; h<ipHisLength; h++) {
  			if (ipHis[h] > ipHisMaxBin) {
  				ipHisMaxBin = ipHis[h];
  				ipHisMode = (double)h;
  			}
  		}
  		ImageProcessor ip2 = ip.duplicate();
  		ip2.setValue(ipHisMode);
  		ip2.fill();
  		

		ip.setInterpolationMethod(ImageProcessor.BICUBIC);
		if (this.getOwnerImps() != null && this.getOwnerImps().size() > 0 && this.getOwnerImps().get(0) != null) {
			ip.translate(skewXperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2), skewYperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2));
			ip2.copyBits(ip, 0, 0, Blitter.COPY_ZERO_TRANSPARENT);
			ip = ip2;
		} else {
			ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));
		}
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
		if (infoArray[0].sliceLabels==null || infoArray[0].sliceLabels.length!=nImages) {
			if (n<1 || n>nImages) {
				IJ.runMacro("waitForUser(\""+n+"\");");
				return fivStacks.get(0).infoArray[0].fileName;
//				throw new IllegalArgumentException("Argument out of range: "+n);
			}
			int z = n % fivStacks.get(0).nImages;
			int t = (int) Math.floor(n/fivStacks.get(0).nImages);
			if (z==0) {
				z = fivStacks.get(0).nImages;
				t=t-1;
			}
//			IJ.log(""+n+" "+z+" "+t);
			return fivStacks.get(0).infoArray[0].fileName + " slice "+ sliceNumber;
		}
		else
			return infoArray[0].sliceLabels[n-1];
	}

	public int getWidth() {
		return infoArray[0].width;
	}
	
	public int getHeight() {
		return infoArray[0].height;
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
