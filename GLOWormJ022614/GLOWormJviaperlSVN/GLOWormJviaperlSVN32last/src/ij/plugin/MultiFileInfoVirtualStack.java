package ij.plugin;

import ij.*;
import ij.measure.Calibration;
import ij.process.*;
import ij.util.StringSorter;
import ij.gui.*;
import ij.io.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.regex.Pattern;

import org.vcell.gloworm.GetNetworkAddress;
import org.vcell.gloworm.QTVirtualStack;

public class MultiFileInfoVirtualStack extends VirtualStack implements PlugIn {
	ArrayList<FileInfoVirtualStack> fivStacks;
	FileInfo[] infoArray;
	public ArrayList<FileInfo[]> infoCollectorArrayList;
	int nImages;
	private String dir;
	private int channelDirectories;
	private String keyString = "";
//	private String dimOrder;     //already present in fields for VirtualStack;
	private double min;  
	private double max;
	private int largestDirectoryLength;
	private File largestDirectoryFile;
	private String[] cumulativeTiffFileArray;
	private FileInfo[] dummyInfoArray;
	private int largestDirectoryTiffCount;
	public String infoDir;
	private int  cDim, zDim;
	public int tDim;
	private int  vDim=1;
	public int stackNumber;
	public int sliceNumber;
	private boolean isViewB;
	private boolean monitoringDecon;
	private  int dXA;
	private  int dXB;
	private  int dYA;
	private  int dYB;
	private  int dZA;
	private  int dZB;
	private boolean reverseChannelOrder;
	private boolean rawdispimdata;
	private boolean initiatorRunning;
	private int pos;
	private boolean newRealInfo;

	/* Default constructor. */
	public MultiFileInfoVirtualStack() {}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo array. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray) {
		this(fiArray, true);
	}

	/* Constructs a MultiFileInfoVirtualStack from a FileInfo 
	array and displays it if 'show' is true. */
	public MultiFileInfoVirtualStack(FileInfo[] fiArray, boolean show) {
		infoArray = fiArray;
		open(show);
	}

	public MultiFileInfoVirtualStack(String dirOrOMETiff, String string, boolean show) {
		this(dirOrOMETiff, "xyczt", string, 0, 0, 0, 1, -1, false, show, false);
	}

	public MultiFileInfoVirtualStack(String dirOrOMETiff, String string, boolean isViewB, boolean show) {
		this(dirOrOMETiff, "xyczt", string, 0, 0, 0, 1, -1, isViewB, show, false);
	}

	public MultiFileInfoVirtualStack(String arg, String sliceOrder, String keyString, int cDim, int zDim, int tDim, int vDim, int pos, boolean isViewB, boolean show, boolean rawdispimdata) {
		this.rawdispimdata = rawdispimdata;
		this.keyString = keyString;
		this.isViewB = isViewB;
		if (cDim<0) {
			cDim = cDim*(-1);
			reverseChannelOrder=true;
		}
		this.cDim = cDim;
		this.zDim = zDim;
		this.tDim = tDim;
		this.vDim = vDim;
		this.pos = pos;
		dimOrder = sliceOrder;
		fivStacks = new ArrayList<FileInfoVirtualStack>();
		
		String[] args = arg.split("\\|");
		
		File argFile = new File(args[0]);
		dir = "";
		if (!argFile.exists() || !argFile.isDirectory()) {
			dir = IJ.getDirectory("Select Directory of TIFFs");
			String defaultKey = "Deconvolution";
			if (dir.replace(File.separator, "/").matches(".*/Proj[XY]_Decon-Fuse.*/"))
				defaultKey = "Color";
			keyString = IJ.getString("Subdirectory Name Key String?", defaultKey);
			args[0]=dir;
		}
		else
			dir = args[0];
		if (dir==null) return;
		if (dir.length() > 0 && !dir.endsWith(File.separator))
			dir = dir + File.separator;
		infoDir = dir;
		
		infoCollectorArrayList =new ArrayList<FileInfo[]>();

		getSavedExtractedFileInfos(pos);
	
		if (infoCollectorArrayList.size()==0 || infoCollectorArrayList.get(0)[0].channelShifts == null){
			dXA= Integer.parseInt(Prefs.get("diSPIMmonitor.dXA", "0"));
			dXB= Integer.parseInt(Prefs.get("diSPIMmonitor.dXB", "0"));
			dYA= Integer.parseInt(Prefs.get("diSPIMmonitor.dYA", "0"));
			dYB= Integer.parseInt(Prefs.get("diSPIMmonitor.dYB", "0"));
			dZA= Integer.parseInt(Prefs.get("diSPIMmonitor.dZA", "0"));
			dZB= Integer.parseInt(Prefs.get("diSPIMmonitor.dZB", "0"));
		} else {
			dXA= infoCollectorArrayList.get(0)[0].channelShifts[0];
			dYA= infoCollectorArrayList.get(0)[0].channelShifts[1];
			dZA= infoCollectorArrayList.get(0)[0].channelShifts[2];
			dXB= infoCollectorArrayList.get(0)[0].channelShifts[3];
			dYB= infoCollectorArrayList.get(0)[0].channelShifts[4];
			dZB= infoCollectorArrayList.get(0)[0].channelShifts[5];

		}

	
		ArrayList<String> bigSubFileArrayList = new ArrayList<String>();
		ArrayList<String> cumulativeSubFileArrayList = new ArrayList<String>();
		ArrayList<ArrayList<String>> channelArrayLists = new ArrayList<ArrayList<String>>();
		boolean allDirectories = true;
		int tiffCount = 0;

		for (String subArg:args) {
			if (subArg==null) return;
			if (subArg.length() > 0 && !subArg.endsWith(File.separator))
				subArg = subArg + File.separator;
			argFile = new File(subArg);

			String[] subArgFileList = argFile.list();
			subArgFileList = StringSorter.sortNumericallyViaRegex(subArgFileList);

			allDirectories = true;

			largestDirectoryLength = 0;
			tiffCount = 0;
			for (String fileName:subArgFileList) {
				File subFile = new File(subArg+fileName);
				if (fileName.contains("DS_Store"))
					;
				else if ((keyString == "" || subFile.getName().matches(".*"+keyString+".*")) && !subFile.isDirectory()) {
					if (subFile.getName().toLowerCase().endsWith("tif")) {
						allDirectories = false;
						cumulativeSubFileArrayList.add(subArg+fileName);
						tiffCount++;				
					}
				}
			}

			if (tiffCount == 0) {
				channelDirectories=0;
				channelArrayLists = new ArrayList<ArrayList<String>>();
				for (String fileName:subArgFileList) {
					File subFile = new File(subArg+fileName);
					if (fileName.contains("DS_Store"))
						;
					else if (keyString == "" || (subFile.getName().matches(".*"+keyString+".*") && !subFile.getName().startsWith("Proj_"))){
						channelDirectories++;
						String[] subFileList = subFile.list();
						channelArrayLists.add(new ArrayList<String>());
						//IJ.log(fileName +"???  "+keyString );
						for (String subFileListElement:subFileList) {
							if (!cumulativeSubFileArrayList.contains(subArg+fileName+File.separator+subFileListElement)) {
								if ((pos ==-1 ||subFileListElement.toLowerCase().contains("_pos"+pos+"."))
										&&  subFileListElement.toLowerCase().endsWith("tif")) {
									channelArrayLists.get(channelDirectories-1).add(subArg+fileName+File.separator+subFileListElement);
									cumulativeSubFileArrayList.add(subArg+fileName+File.separator+subFileListElement);
								}
							}
						}
					}
				}
				int lowestSpan = Integer.MAX_VALUE;
				for (ArrayList<String> al:channelArrayLists) {
					if (lowestSpan > al.size()){
						lowestSpan = al.size();
					}
				}
				for (ArrayList<String> al:channelArrayLists) {
					for (int zap=al.size();zap>=lowestSpan;zap--) {
						if (al.size()>zap) {
							cumulativeSubFileArrayList.remove(al.get(zap-1));
						}
					}
				}
			}
		}
		if (cumulativeSubFileArrayList.size() != 0) {
			cumulativeTiffFileArray = new String[cumulativeSubFileArrayList.size()];
			int highT = 0;
			for (int s=0; s<cumulativeTiffFileArray.length; s++) {
				cumulativeTiffFileArray[s] = (String) cumulativeSubFileArrayList.get(s);
//				IJ.log("tif "+(s+1)+" ="+cumulativeTiffFileArray[s]);
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
//			cumulativeTiffFileArray = StringSorter.sortNumericallyViaRegex(cumulativeTiffFileArray);
//			IJ.log("CumSubFileArray.length="+cumulativeTiffFileArray.length);
			
			if (cumulativeTiffFileArray.length >0){ 
				for (String cumulativeTiffFileArrayElement:cumulativeTiffFileArray)
					bigSubFileArrayList.add(cumulativeTiffFileArrayElement);
			} else { 

				for (String fileName:new File(dir).list()) {
					File subFile = new File(dir+fileName);
					boolean noKeyString = keyString == "";
					boolean subFileIsDir = subFile.isDirectory();
					if (fileName.contains("DS_Store")) {
						IJ.log(".");

					} else if ((noKeyString || subFile.getName().matches(".*"+keyString+".*")) && !subFileIsDir) {
						IJ.log(".");

					} else if (noKeyString || (subFile.getName().matches(".*"+keyString+".*") && !subFile.getName().startsWith("Proj_"))){
						String[] subFileList = subFile.list();
						subFileList = StringSorter.sortNumericallyViaRegex(subFileList);
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

			if (monitoringDecon)
				goDirFileList = StringSorter.sortNumericallyViaRegex(goDirFileList);
			String lastFileNameOfBunch = "";

			if (goDirFileList != null) {
				lastFileNameOfBunch = goDirFileList[goDirFileList.length-1];
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
				if (infoCollectorArrayList.size()==0){
					for (String fileName:goDirFileList){
						if ((new File(dir + fileName)).canRead() && fileName.toLowerCase().endsWith(".tif")) {
							if (dummyInfoArray == null || fileName==lastFileNameOfBunch) {
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
								infoCollectorArrayList.add(new FileInfo[dummyInfoArray.length]);
								for (int si=0; si<infoCollectorArrayList.get(infoCollectorArrayList.size()-1).length; si++) {
									infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[si] = (FileInfo) dummyInfoArray[si].clone();
									infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[si].fileName = fileName+"_dummy";
								}

							}
							if (infoCollectorArrayList==null || infoCollectorArrayList.size()==0) {
								continue;
							}
							fivStacks.add(new FileInfoVirtualStack());
							final int sizeWas = fivStacks.size();
							fivStacks.get(sizeWas-1).infoArray = infoCollectorArrayList.get(infoCollectorArrayList.size()-1);
							fivStacks.get(sizeWas-1).setupStack();
						} else if (fileName.matches(".*channel.*-frame.* missing")) {
							fivStacks.add(new FileInfoVirtualStack(new FileInfo(), false));
							for (FileInfo sliceInfo:fivStacks.get(fivStacks.size()-1).infoArray)
								sliceInfo.fileName = fileName;
							fivStacks.get(fivStacks.size()-1).setupStack();
						}
					}
				} else {
					for (int i = 0; i<infoCollectorArrayList.size(); i++) {
						fivStacks.add(new FileInfoVirtualStack());
						final int sizeWas = fivStacks.size();
						fivStacks.get(sizeWas-1).infoArray = infoCollectorArrayList.get(i);
						fivStacks.get(sizeWas-1).setupStack();
					}
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
					//						IJ.log("fi "+(f+1)+infoArray[f].directory+File.separator+infoArray[f].fileName);

				}

				open(show);
			}


		}
	}

	public void getSavedExtractedFileInfos(int pos) {
		if (new File(infoDir+"touchedFileFIs"+pos+".inf").canRead()) {
			ObjectInputStream ois;
			try {
				ois = new ObjectInputStream(new FileInputStream(infoDir+"touchedFileFIs"+pos+".inf"));
				try {
					infoCollectorArrayList  = (ArrayList<FileInfo[]>) ois.readObject();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}finally{
					ois.close();
				}
			} catch (FileNotFoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}finally{

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
		nImages = fivStacks.size() * fivStacks.get(0).nImages*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);
	}

	public void run(String arg) {
		new MultiFileInfoVirtualStack(arg, "", true);
	}

	void open(boolean show) {
		String[] splitPath = cumulativeTiffFileArray[0].split(Pattern.quote(File.separator));
		if (splitPath[splitPath.length-1].contains("MMStack_") && (cumulativeTiffFileArray.length >0)) { 
			nImages = 0;
			for (FileInfoVirtualStack mmStack:fivStacks) {
				nImages = nImages + mmStack.getSize()*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);
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
/*why like this?*/
				this.tDim =nImages/(this.cDim*this.zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));
			}

		} else if (monitoringDecon){
			zDim = fivStacks.get(0).nImages;
			nImages = fivStacks.size() * zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);

			int internalChannels = ((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]) != null
					?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]), "channels"))
							:1);		
			int channels = channelDirectories * internalChannels;
			cDim = channels;
			zDim = fivStacks.get(0).nImages/(cDim/channelDirectories);
//			tDim = fivStacks.size()/cDim;
			this.tDim =nImages/(this.cDim*this.zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));
		} else {
			zDim = fivStacks.get(0).nImages;
			nImages = /*channelDirectories**/ fivStacks.size() * zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);

			int internalChannels = ((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]) != null
					?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]), "channels"))
							:1);		
			int channels = channelDirectories * internalChannels;
			cDim = channels;
			zDim = fivStacks.get(0).nImages/(cDim/channelDirectories);
//			tDim = fivStacks.size()/(cDim/internalChannels);
			this.tDim =nImages/(this.cDim*this.zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));

		}

		String[] dirChunks = dir.split("\\"+File.separator);
		ImagePlus fivImpZero = fivStacks.get(0).open(false);
		ImagePlus imp = new ImagePlus(
				dirChunks[dirChunks.length-1]+"_"+
						fivImpZero.getTitle().replaceAll("\\d+\\.", "\\."), this);
		
		fivImpZero.flush();
		
		imp.setOpenAsHyperStack(true);			
//		int cztDims = cDim*zDim*fivStacks.size();
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
		int  vSliceNumber = 0;
		int total=0;

		if (dimOrder.toLowerCase().matches("xy.*czt")) {
			int adjN =0;
			int zxc = zDim*cDim*(dimOrder.toLowerCase().matches(".*split.*c.*")?2:1);
			while (n>zxc/vDim) {
				adjN = adjN + (zxc);
				n = n-(zxc/vDim);
			}
			n=n+adjN;
		}

		while (n > total) {
			total = total + fivStacks.get(stackNumber).getSize()*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);
			stackNumber++;
		}

		stackNumber--;

		sliceNumber = 1+(n) % (fivStacks.get(stackNumber).getSize()*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));
		
		if (dimOrder.toLowerCase().matches(".*splitc.*")) {
			sliceNumber = (sliceNumber/2);
		}
		
		if (reverseChannelOrder) {
			sliceNumber = sliceNumber%2==0?(sliceNumber+1):(sliceNumber-1);
		}
		
		int dZ = 0;
		if (rawdispimdata && cDim/vDim>1 ) {
			dZ=isViewB?dZB:dZA;
		}

		ImageProcessor ip = null;
		if (dimOrder == "xyczt") {
			vSliceNumber = (sliceNumber)+(isViewB?fivStacks.get(stackNumber).getSize()/vDim:0);
			//ADJUSTMENTS BELOW DEAL WITH CALLING RG CHANNELS CORRECTLY
			//I DO NOT FULLY UNDERSTAND HOW OR WHY IT WORKS!!!???
			if (vSliceNumber%2 == 0) {
				vSliceNumber = vSliceNumber-1;
			} else {
				vSliceNumber = vSliceNumber-1;
			}

			if (vSliceNumber>fivStacks.get(stackNumber).getSize()) {
				vSliceNumber = vSliceNumber-fivStacks.get(stackNumber).getSize();
				stackNumber++;
			}
			initiateStack(stackNumber, 0);
			ip = fivStacks.get(stackNumber).getProcessor(vSliceNumber+(sliceNumber%2==0?0:dZ));
		}
		if (dimOrder.toLowerCase().matches(".*split.*c.*")) {

			int dX = 0;
			int dY = 0;
			
			dX=isViewB?dXB:dXA;
			dY=isViewB?dYB:dYA;

			vSliceNumber = (sliceNumber)+(isViewB?zDim*(cDim/2)*(dimOrder.toLowerCase().matches(".*splitsequentialc.*")?2:1):0);


			//ADJUSTMENTS BELOW DEAL WITH CALLING C1 AND C4 FOR CSM MODE SWITCH TO JUST 2 MAIN RG CHANNELS
			//I DO NOT FULLY UNDERSTAND HOW OR WHY IT WORKS!!!???
			if (dimOrder.toLowerCase().matches(".*splitsequentialc.*")) {
				if (vSliceNumber%2 == 0) {
					vSliceNumber = vSliceNumber-1;
				} else {
					vSliceNumber = vSliceNumber-1;
				}
			}

			if (vSliceNumber>fivStacks.get(stackNumber).getSize()) {
				vSliceNumber = vSliceNumber-fivStacks.get(stackNumber).getSize();
				stackNumber++;
			}

			initiateStack(stackNumber, 0);
			ip = fivStacks.get(stackNumber).getProcessor(vSliceNumber+(sliceNumber%(2)==0?0:dZ*2));

			ip.setInterpolationMethod(ImageProcessor.BICUBIC);
			if (this.getOwnerImps() != null && this.getOwnerImps().size() > 0 && this.getOwnerImps().get(0) != null) {
				ip.translate(skewXperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2), skewYperZ*(this.getOwnerImps().get(this.getOwnerImps().size()-1).getSlice()-1-this.getOwnerImps().get(this.getOwnerImps().size()-1).getNSlices()/2));
			} else {
				ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));
			}

			if (ip.getWidth()==2048) //NIBIB splitview setup(?)
			{
				dX=2;
				dY=0;
				int xOri = 256+((1-(n+1)%2)*(1024));
				int yOri = 0+((1-(n+1)%2)*(0));
				ip.setRoi(xOri, yOri, 512, 512);
			} else if (ip.getWidth()==1536) {		//Yale splitview setup
			
				int xOri = 0+((0+(n+1)%2)*(1024));
				int yOri = 0+((1-(n+1)%2)*(0));
				ip.setRoi(xOri, yOri, 512, 512);
			}
			ip = ip.crop();
			ip.translate((1-n%2)*dX, (1-n%2)*dY);
		}
		if (dimOrder == "xyzct") {
			initiateStack(stackNumber, 0);
			ip = fivStacks.get(stackNumber).getProcessor(sliceNumber/cDim + ((sliceNumber%cDim)*fivStacks.get(stackNumber).getSize()/(vDim))+(sliceNumber%2==0?0:dZ)
					+(isViewB?fivStacks.get(stackNumber).getSize()/(cDim*vDim):0));
		}
		if (dimOrder == "xyztc") {
			initiateStack(stackNumber, 0);
			ip = fivStacks.get(stackNumber).getProcessor(sliceNumber+(sliceNumber%2==0?0:dZ));
		}

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

		if (dimOrder.toLowerCase().matches(".*splitsequentialc.*") && n%cDim == 1) {
			ImageProcessor nextIP = fivStacks.get(stackNumber).getProcessor(vSliceNumber+1);
			int dX=0;
			int dY=0;
			if (nextIP.getWidth()==1536) //Yale splitview setup
			{
				dX=isViewB?2:5;
				dY=isViewB?7:2;
				int xOri = 1024;
				int yOri = 0;
				ip.setRoi(xOri, yOri, 512, 512);
			}
			nextIP = nextIP.crop();
			nextIP.translate((1-n%2)*dX, (1-n%2)*dY);
			ImageProcessor rip1= null;
			ImageProcessor rip2= null;
			if (ip instanceof ByteProcessor && nextIP instanceof ByteProcessor) {
				rip1 = ((ByteProcessor) ip).convertToFloat();
				rip2 = ((ByteProcessor) nextIP).convertToFloat();
			} else if (ip instanceof ShortProcessor && nextIP instanceof ShortProcessor) {
				rip1 = ((ShortProcessor) ip).convertToFloat();
				rip2 = ((ShortProcessor) nextIP).convertToFloat();
			}
			if (rip1!=null && rip2!=null) {
				rip1.copyBits(rip2, 0, 0, Blitter.DIVIDE);
				rip1.setMinAndMax(0, 255);
				ip = rip1.convertToShort(true);
			}
		}

		//		ip.multiply(0.25);

		//		ip.setMinAndMax(min, max);
		if (edges) {
			ip.findEdges();
			
//			ip.doAffineTransform(new AffineTransform(1.075,0.25,0.2,1.075,0,0));
		}
		return ip;
	}

	public void initiateStack(int stkNum, int slcNum) {
		fivStacks.get(stkNum).setupStack();
		if (fivStacks!=null && fivStacks.size()>stkNum && fivStacks.get(stkNum).infoArray.length>slcNum) {

			String currentFileName =fivStacks.get(stkNum).infoArray[slcNum].fileName;
			
			if (currentFileName.endsWith("_dummy")) {
				initiatorRunning = true;
				currentFileName = currentFileName.replace("_dummy","");
				TiffDecoder td = new TiffDecoder(dir, currentFileName);
				if (IJ.debugMode) td.enableDebugging();
				IJ.showStatus("Decoding TIFF header...");
				try {infoCollectorArrayList.set(stkNum, td.getTiffInfo(0));}
				catch (IOException e) {
					String msg = e.getMessage();
					if (msg==null||msg.equals("")) msg = ""+e;
					IJ.error("TiffDecoder", msg);
				}
				newRealInfo = true;
				fivStacks.get(stkNum).infoArray = infoCollectorArrayList.get(stkNum);

				initiatorRunning = false;

			}
		}
		
	}

	/** Returns the number of images in this stack. */
	public int getSize() {
		return nImages/vDim;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nImages)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (infoArray==null || infoArray[0].sliceLabels==null || infoArray[0].sliceLabels.length!=nImages) {
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

	public  int getdXA() {
		return dXA;
	}

	public  void setdXA(int dXA) {
		this.dXA = dXA;
	}

	public  int getdXB() {
		return dXB;
	}

	public  void setdXB(int dXB) {
		this.dXB = dXB;
	}

	public  int getdYA() {
		return dYA;
	}

	public  void setdYA(int dYA) {
		this.dYA = dYA;
	}

	public  int getdYB() {
		return dYB;
	}

	public  void setdYB(int dYB) {
		this.dYB = dYB;
	}

	public int getdZA() {
		return dZA;
	}

	public void setdZA(int dZA) {
		this.dZA = dZA;
	}

	public int getdZB() {
		return dZB;
	}

	public void setdZB(int dZB) {
		this.dZB = dZB;
	}

}
