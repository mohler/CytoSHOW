package ij.plugin;

import ij.*;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.RankFilters;
import ij.process.*;
import ij.util.StringSorter;
import ij.gui.*;
import ij.io.*;

import java.awt.*;
import java.awt.event.ActionEvent;
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
	public FileInfo[] infoArray;
	public ArrayList<FileInfo[]> infoCollectorArrayList;
	//	int nSlices;  //already present in fields for FileInfoVirtualStack;
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
	public int  cDim;
	public int zDim;
	public int tDim;
	public int  vDim=1;
	public int stackNumber;
	public int sliceNumber;
	private boolean isViewB;
	private boolean monitoringDecon;
	private boolean reverseChannelOrder;
	private boolean rawdispimdata;
	private boolean initiatorRunning;
	private int pos;
	private boolean newRealInfo;
	private ArrayList<FileInfo[]> savedInfoCollectorArrayList;
	private String infoLoadReport;
	private int[] corrXA;
	private int[] corrYA;
	private int[] corrZA;
	private int[] corrXB;
	private int[] corrYB;
	private int[] corrZB;
	private ImagePlus ownerImp;
	private boolean segmentLiveNeuron;
	private GenericDialog trackingDialog;
	double threshModeCoeff = 0.079;  
	double minSize = 900;
	double maxSize = 100000;
	double minCirc = 0.00;
	double maxCirc = 1.000;
	private ImageProcessor statsIP;
	private ImageProcessor bigIP;

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
		this(dirOrOMETiff, "xyczt", string, 0, 0, 0, 1, -1, false, show, false, false, null);
	}

	public MultiFileInfoVirtualStack(String dirOrOMETiff, String string, boolean isViewB, boolean show) {
		this(dirOrOMETiff, "xyczt", string, 0, 0, 0, 1, -1, isViewB, show, false, false, null);
	}

	public MultiFileInfoVirtualStack(String arg, String sliceOrder, String keyString, int cDim, int zDim, int tDim, int vDim, final int pos, boolean isViewB, boolean show, boolean rawdispimdata, boolean omeMegaTiff, ImagePlus ownerImp) {
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
		this.ownerImp = ownerImp;
		dimOrder = sliceOrder;
		fivStacks = new ArrayList<FileInfoVirtualStack>();

		String[] args = arg.split("\\|");

		File argFile = new File(args[0]);
		dir = "";
		if (!argFile.exists() || !argFile.isDirectory()) {
			dir = IJ.getDirectory("Select Directory of TIFFs");
			String defaultKey = "Deconvolution";
			if (dir.replace(File.separator, "/").matches(".*/Proj[XY]_Decon-Fuse.*/")
					|| dir.replace(File.separator, "/").matches(".*/Max[XYZ]_Decon-Fuse.*/"))
				defaultKey = "Color";
			keyString = IJ.getString("Subdirectory Name Key String?", defaultKey);
			args[0]=dir;
		} else
			dir = args[0];
		if (dir==null) return;
		if (dir.length() > 0 && !dir.endsWith(File.separator))
			dir = dir + File.separator+ File.separator;
		infoDir = dir;

		infoCollectorArrayList =new ArrayList<FileInfo[]>();
		infoLoadReport ="";


		ArrayList<String> bigSubFileArrayList = new ArrayList<String>();
		ArrayList<String> cumulativeSubFileArrayList = new ArrayList<String>();
		ArrayList<ArrayList<String>> channelArrayLists = new ArrayList<ArrayList<String>>();
		boolean allDirectories = true;
		int tiffCount = 0;

		for (String subArg:args) {
			if (subArg==null) return;
			if (subArg.length() > 0 && !subArg.endsWith(File.separator))
				subArg = subArg + File.separator+ File.separator;
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
						if (al.size()>zap && zap>0) {
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
				//	IJ.log("tif "+(s+1)+" ="+cumulativeTiffFileArray[s]);
				String[] subFilePathChunks = cumulativeTiffFileArray[s].split(Pattern.quote(File.separator));
				String subFileName = subFilePathChunks[subFilePathChunks.length-1];

				//	which format was this for, now conflicting with SN 16-bit stack output names...
				//	if (subFileName.matches(".*_t\\d+.*\\.tif")) 
				if (subFileName.matches(".*Decon_(reg_)?\\d+\\.tif")) {
					int tValue = Integer.parseInt(subFileName.replaceAll(".*_(\\d+)\\.tif", "$1"));
					if (tValue > highT)
						highT = tValue;
				}
				if (subFileName.matches(".*Decon_t\\d{4}\\.tif")) {
					int tValue = Integer.parseInt(subFileName.replaceAll(".*_t(\\d+).*\\.tif", "$1"));
					if (tValue > highT)
						highT = tValue;
				}
				if (subFileName.matches("proj._\\d+_\\d+.tif")) {
					int tValue = Integer.parseInt(subFileName.replaceAll("proj._\\d+_(\\d+).tif", "$1"));
					if (tValue > highT)
						highT = tValue;
				}
				if (subFileName.matches("SPIM.-\\d+.tif")) {
					int tValue = Integer.parseInt(subFileName.replaceAll("SPIM.-(\\d+).tif", "$1"));
					if (tValue > highT)
						highT = tValue;
				}
				if (subFileName.matches("max._\\d+_\\d+.tif")) {
					int tValue = Integer.parseInt(subFileName.replaceAll("max._\\d+_(\\d+).tif", "$1"));
					if (tValue > highT)
						highT = tValue;
				}

				if (highT > 0)
					dimOrder = "xyztc";
			}
			//	cumulativeTiffFileArray = StringSorter.sortNumericallyViaRegex(cumulativeTiffFileArray);
			//	IJ.log("CumSubFileArray.length="+cumulativeTiffFileArray.length);


			//	new Thread (new Runnable(){
			//	public void run(){getSavedExtractedFileInfos(pos);}
			//	}).start();
			//	
			//	while (infoLoadReport=="" || (infoLoadReport=="success" && (savedInfoCollectorArrayList == null || savedInfoCollectorArrayList.size()==0))) {
			////	IJ.log(infoLoadReport + (savedInfoCollectorArrayList != null?savedInfoCollectorArrayList.size():""));
			//	
			//	IJ.wait(10);
			//	}
			////	IJ.log(infoLoadReport + (savedInfoCollectorArrayList != null?savedInfoCollectorArrayList.size():""));

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
				//	dimOrder = "xyztc";
				dir = "";

				goDirFileList = new String[bigSubFileArrayList.size()];
				for (int s=0; s<goDirFileList.length; s++) {
					goDirFileList [s] = (String) bigSubFileArrayList.get(s);
				}

			} else {
				//	dimOrder = "xyczt";
				dir = "";
				channelDirectories = 1;
				largestDirectoryTiffCount = tiffCount;
				goDirFileList = cumulativeTiffFileArray;
			}
			if (dir.length() > 0 && !dir.endsWith(File.separator))
				dir = dir + File.separator;

			if (monitoringDecon)
				goDirFileList = StringSorter.sortNumericallyViaRegex(goDirFileList);

			if (goDirFileList != null) {
				long firstFileSize = (new File(dir + goDirFileList[0])).length();

				if (!omeMegaTiff){
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
								if (dummyInfoArray == null || (new File(dir + fileName)).length() < firstFileSize*0.998
										|| (new File(dir + fileName)).length() > firstFileSize*1.002) {
									TiffDecoder td = new TiffDecoder(dir, fileName);
									if (IJ.debugMode) td.enableDebugging();
									IJ.showStatus("Decoding TIFF header...");
									try {
										infoCollectorArrayList.add(td.getTiffInfo(0));
										int tiLength = infoCollectorArrayList.get(infoCollectorArrayList.size()-1).length;
										int corrLength = infoCollectorArrayList.get(0).length;
										if ( tiLength < corrLength){
											infoCollectorArrayList.set(infoCollectorArrayList.size()-1
													, Arrays.copyOf(infoCollectorArrayList.get(infoCollectorArrayList.size()-1)
															, corrLength));
											for (int l=tiLength; l<corrLength; l++){
												infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[l] = 
														infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[tiLength-1];
											}
										}
										if ( tiLength > corrLength){
											FileInfo[] tempArray = new FileInfo[corrLength];
											int diff = tiLength-corrLength;
											for (int l=0; l<corrLength; l++){
												tempArray[l] = infoCollectorArrayList.get(infoCollectorArrayList.size()-1)[l+diff];
											}
											infoCollectorArrayList.set(infoCollectorArrayList.size()-1
													, tempArray);
										}

									}catch (IOException e) {
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
								int sizeWas = fivStacks.size();
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
							int sizeWas = fivStacks.size();
							fivStacks.get(sizeWas-1).infoArray = infoCollectorArrayList.get(i);
							fivStacks.get(sizeWas-1).setupStack();
						}
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
					//	IJ.log("fi "+(f+1)+infoArray[f].directory+File.separator+infoArray[f].fileName);

				}
				if (fivStacks.get(0).getInfo()[0].fileName.matches(".*Decon(-Fuse|_reg)_.*aaa_.*")){
					this.cDim = 2;
					dimOrder = "xySplitCzt";
				}
				open(show);
			}
		}
	}

	public void getSavedExtractedFileInfos(int pos) {
		if (new File(infoDir+"touchedFileFIs"+pos+(isViewB?"B":"A")+".inf").canRead()) {

			ObjectInputStream ois;
			try {
				ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(infoDir+"touchedFileFIs"+pos+(isViewB?"B":"A")+".inf")));
				try {
					savedInfoCollectorArrayList  = (ArrayList<FileInfo[]>) ois.readObject();
					infoLoadReport = "success";

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
			if (savedInfoCollectorArrayList!=null){
				for(FileInfo[]  infoArray:savedInfoCollectorArrayList){
					for(FileInfo fi:infoArray){
						fi.directory= "";
						String[] filePathChunks =  fi.fileName.replace("\\\\", "\\").split(Pattern.quote(File.separator));
						int fpcl = filePathChunks.length;
						String Quadrachunk = filePathChunks[fpcl-3]+File.separator+
								filePathChunks[fpcl-2]+File.separator+
								filePathChunks[fpcl-1];
						//	IJ.log(Quadrachunk);
						//	IJ.log(fi.fileName);
						for(String cumTiff:cumulativeTiffFileArray){
							String[] cumPathChunks =  cumTiff.replace("\\\\", "\\").split(Pattern.quote(File.separator));
							int cpcl = cumPathChunks.length;
							String cumQuadrachunk = cumPathChunks[cpcl-3]+File.separator+
									cumPathChunks[cpcl-2]+File.separator+
									cumPathChunks[cpcl-1];

							if (cumQuadrachunk.endsWith(Quadrachunk)){
								fi.fileName=cumTiff;
							}
						}
					}
				}
				infoCollectorArrayList = savedInfoCollectorArrayList;
				for (int stkNum=0;stkNum<fivStacks.size();stkNum++){
					fivStacks.get(stkNum).infoArray = infoCollectorArrayList.get(stkNum);
				}
			}
			infoLoadReport = "failure";

		} else {
			infoLoadReport = "failure";
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
		nSlices = fivStacks.size() * fivStacks.get(0).nSlices*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);
	}

	public void run(String arg) {
		new MultiFileInfoVirtualStack(arg, "", true);
	}

	public ImagePlus open(boolean show) {
		String[] splitPath = cumulativeTiffFileArray[0].split(Pattern.quote(File.separator));
		if (splitPath[splitPath.length-1].contains("MMStack_") && (cumulativeTiffFileArray.length >0)) { 
			nSlices = 0;
			for (FileInfoVirtualStack mmStack:fivStacks) {
				nSlices = nSlices + mmStack.getSize()*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1)
						*(dimOrder.toLowerCase().matches(".*splitratioc.*")?2:1);
			}

			if (cDim == 0 || zDim == 0 || tDim == 0) {
				GenericDialog gd = new GenericDialog("Dimensions of HyperStacks");
				gd.addNumericField("Channels (c):", 2, 0);
				gd.addNumericField("Slices (z):", 50, 0);
				gd.addNumericField("Frames (t):", nSlices/(50*2*2), 0);
				gd.showDialog();
				if (gd.wasCanceled()) return null;
				cDim = (int) gd.getNextNumber();
				zDim = (int) gd.getNextNumber();
				tDim = (int) gd.getNextNumber();
				nSlices = cDim*zDim*tDim;
			} else {
				/*why like this?*/
				this.tDim =nSlices/(this.cDim*this.zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));
			}

		} else if (monitoringDecon){
			zDim = fivStacks.get(0).nSlices;
			nSlices = fivStacks.size() * zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);

			int internalChannels = ((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]) != null
					?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]), "channels"))
							:1);	
			int channels = channelDirectories * internalChannels;
			cDim = channels;
			zDim = fivStacks.get(0).nSlices/(cDim/channelDirectories);
			//	tDim = fivStacks.size()/cDim;
			this.tDim =nSlices/(this.cDim*this.zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));
		} else if (fivStacks.get(0).getInfo()[0].fileName.matches(".*Decon(-Fuse|_reg)_.*aaa_.*")){
			zDim = fivStacks.get(0).nSlices;
			cDim = 2;
			tDim = fivStacks.size();
			nSlices = cDim*zDim*tDim;
		} else {
			zDim = fivStacks.get(0).nSlices;
			nSlices = /*channelDirectories**/ fivStacks.size() * zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);

			int internalChannels = ((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]) != null
					?(fivStacks.get(0).getInt((new FileOpener(fivStacks.get(0).infoArray[0])).decodeDescriptionString(fivStacks.get(0).infoArray[0]), "channels"))
							:1);	
			int channels = channelDirectories * internalChannels;
			cDim = channels;
			zDim = fivStacks.get(0).nSlices/(cDim/channelDirectories);
			//	tDim = fivStacks.size()/(cDim/internalChannels);
			this.tDim =nSlices/(this.cDim*this.zDim*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));

		}

		dXA = new int[tDim*cDim*vDim];
		dYA = new int[tDim*cDim*vDim];
		dZA = new int[tDim*cDim*vDim];
		dXB = new int[tDim*cDim*vDim];
		dYB = new int[tDim*cDim*vDim];
		dZB = new int[tDim*cDim*vDim];
		Arrays.fill(dXA,dimOrder.toLowerCase().matches(".*splitratioc.*")?25:0);
		Arrays.fill(dYA,dimOrder.toLowerCase().matches(".*splitratioc.*")?-8:0);
		Arrays.fill(dZA,0);
		Arrays.fill(dXB,0);
		Arrays.fill(dYB,0);
		Arrays.fill(dZB,0);


		corrXA = new int[tDim*cDim*vDim];
		corrYA = new int[tDim*cDim*vDim];
		corrZA = new int[tDim*cDim*vDim];
		corrXB = new int[tDim*cDim*vDim];
		corrYB = new int[tDim*cDim*vDim];
		corrZB = new int[tDim*cDim*vDim];
		if (savedInfoCollectorArrayList == null || savedInfoCollectorArrayList.size()==0 || savedInfoCollectorArrayList.get(0)[0].channelShifts == null){
			Arrays.fill(corrXA,0);
			Arrays.fill(corrYA,0);
			Arrays.fill(corrZA,0);
			Arrays.fill(corrXB,0);
			Arrays.fill(corrYB,0);
			Arrays.fill(corrZB,0);
		} else {
			Arrays.fill(corrXA, savedInfoCollectorArrayList.get(0)[0].channelShifts[0]);
			Arrays.fill(corrYA, savedInfoCollectorArrayList.get(0)[0].channelShifts[1]);
			Arrays.fill(corrZA, savedInfoCollectorArrayList.get(0)[0].channelShifts[2]);
			Arrays.fill(corrXB, savedInfoCollectorArrayList.get(0)[0].channelShifts[3]);
			Arrays.fill(corrYB, savedInfoCollectorArrayList.get(0)[0].channelShifts[4]);
			Arrays.fill(corrZB, savedInfoCollectorArrayList.get(0)[0].channelShifts[5]);

		}

		String[] dirChunks = dir.split(Pattern.quote(File.separator));
		ImagePlus fivImpZero = fivStacks.get(0).open(false);
		ImagePlus imp = new ImagePlus(
				dirChunks[dirChunks.length-1]+"_"+
						fivImpZero.getTitle().replaceAll("\\d+\\.", "\\."), this);
		imp.setCalibration(fivImpZero.getCalibration());
		if (fivStacks.get(0).getInfo()[0].fileName.matches(".*Decon(-Fuse|_reg)_.*aaa_.*")){  //StarryNiteFeeder output
			imp.setTitle(fivStacks.get(0).getInfo()[0].fileName.replaceAll("(.*)(Decon(-Fuse|_reg)_.*)("+Pattern.quote(File.separator)+"aaa_.*)", "$2"));
		}


		File correctiveShiftsFile = new File(infoDir + "correctiveShifts.txt");
		if (correctiveShiftsFile.canRead()){
			String correctiveShiftsString = IJ.openAsString(infoDir + "correctiveShifts.txt");
			String[] correctiveShiftsLines = correctiveShiftsString.split("\n");
			int[][] shiftArrays = new int[][]{corrXA,corrYA,corrZA,corrXB,corrYB,corrZB};
			for (int line = 0; line<correctiveShiftsLines.length; line++){
				String[] correctiveShiftsLineChunks = correctiveShiftsLines[line].split(",");
				for (int m=0; m<shiftArrays[line].length; m++){
					if (m<correctiveShiftsLineChunks.length){
						if(line%3==0){
							shiftArrays[line][m] = (int)(Double.parseDouble(correctiveShiftsLineChunks[m].trim()));
						} else if(line%3==1){
							shiftArrays[line][m] = (int)(Double.parseDouble(correctiveShiftsLineChunks[m].trim()));
						} else if(line%3==2){
							shiftArrays[line][m] = (int)(Double.parseDouble(correctiveShiftsLineChunks[m].trim()));
						}

					}
				}
			}
		}


		imp.setOpenAsHyperStack(true);	
		//	int cztDims = cDim*zDim*fivStacks.size();
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
				for (int a=impSize;a>cztDims;a--) {
					imp.getStack().deleteSlice(a);
				}
			}else {
				IJ.error("HyperStack Converter", "channels x slices x frames <> stack size");
				return null;
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
		return imp;
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

	/** Deletes the specified image, where 1<=n<=nSlices. */
	public void deleteSlice(int n) {

		if (n<1 || n>nSlices) {
			throw new IllegalArgumentException(outOfRange+n);
		}
		int stackNumber = 0;


		while (stackNumber<fivStacks.size() && n > fivStacks.get(0).getSize()*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1)) {
			n = n - fivStacks.get(0).getSize()*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);
			stackNumber++;
		}
		stackNumber--;
		//	if (stackNumber >= fivStacks.size()){
		//	stackNumber = fivStacks.size()-1;
		//	}

		sliceNumber = n/(dimOrder.toLowerCase().matches(".*splitc.*")?2:1);
		if (sliceNumber <1) sliceNumber = 1;
		if (stackNumber<0) stackNumber = 0;
		if (dimOrder.toLowerCase().matches(".*splitc.*")){	//IS ALL THIS REALLY NECESSARY??? MIGHT BE FOR SPLITC
			if (sliceNumber%2==0){
				if (fivStacks.get(stackNumber).getSize()>0){
					fivStacks.get(stackNumber).deleteSlice(sliceNumber>0?sliceNumber:1);
				}
			}
		} else {
			fivStacks.get(stackNumber).deleteSlice(fivStacks.get(stackNumber).getSize());
		}

		nSlices--;
	}

	/** Returns an ImageProcessor for the specified image,
where 1<=n<=nSlices. Returns null if the stack is empty.
	 */
	public ImageProcessor getProcessor(int slice) {
		int n =slice;
		if (n<1 || n>nSlices) {
			//	IJ.runMacro("waitForUser(\""+n+"\");");
			return fivStacks.get(0).getProcessor(1);
			//	throw new IllegalArgumentException("Argument out of range: "+n);
		}


		stackNumber = 0;
		sliceNumber = 1;
		int  vSliceNumber = 0;

		if (dimOrder.toLowerCase().matches("xy.*czt")) {
			int adjN =0;
			int zc = zDim*cDim*(dimOrder.toLowerCase().matches(".*split.*c.*")?2:1);
			while (n>zc/vDim) {
				adjN = adjN + (zc);
				n = n-(zc/vDim);
			}
			n=n+adjN;
		}

		while (stackNumber < fivStacks.size() && n > fivStacks.get(stackNumber).getSize()*(dimOrder.toLowerCase().matches(".*split(ratio)?c.*")?2:1)) {
			n = n - fivStacks.get(stackNumber).getSize()*(dimOrder.toLowerCase().matches(".*split(ratio)?c.*")?2:1);
			stackNumber++;
		}
		//	if (stackNumber>=fivStacks.size()) {
		//	stackNumber= fivStacks.size()-1;
		//	}

		sliceNumber = n;


		//	sliceNumber = 1+(n) % (fivStacks.get(stackNumber).getSize()*(dimOrder.toLowerCase().matches(".*splitc.*")?2:1));


		if (reverseChannelOrder) {
			sliceNumber = sliceNumber%2==0?(sliceNumber+1):(sliceNumber-1);
		}
		int dX =0;;
		int dY =0;;
		int dZ =0;;

		if (dXA!= null && dXB!= null && dYA!= null && dYB!= null && dZA!= null && dZB!= null){
			dX = isViewB?dXB[0]:dXA[0];;
			dY = isViewB?dYB[0]:dYA[0];;
			dZ = isViewB?dZB[0]:dZA[0];;
		}

		int corrX = 0;
		int corrY = 0;
		int corrZ = 0;

		if (rawdispimdata /*&& cDim/vDim>1*/ ) {
			dZ=isViewB?dZB[0]:dZA[0];
		}

		ImageProcessor ip = null;
		if (dimOrder == "xyczt") {
			//vSliceNumber = (sliceNumber)+(isViewB?fivStacks.get(stackNumber).getSize()/vDim:0); //worked ever?///
			vSliceNumber = (sliceNumber)+(isViewB?zDim*cDim/vDim:0);  //WORKS 02012019 for Mark's old megatiff

			//ADJUSTMENTS BELOW DEAL WITH CALLING RG CHANNELS CORRECTLY
			//I DO NOT FULLY UNDERSTAND HOW OR WHY IT WORKS!!!???
			//	if (vSliceNumber%2 == 0) {
			//	vSliceNumber = vSliceNumber-1;
			//	} else {
			//	vSliceNumber = vSliceNumber-1;
			//	}

			if (vSliceNumber>fivStacks.get(stackNumber).getSize()) {
				vSliceNumber = vSliceNumber-fivStacks.get(stackNumber).getSize();
				stackNumber++;
			}

			//	if (stackNumber >= fivStacks.size()){
			//	stackNumber = fivStacks.size()-1;
			//	}
			corrX=isViewB?corrXB[((stackNumber)%tDim)]:corrXA[((stackNumber)%tDim)];
			corrY=isViewB?corrYB[((stackNumber)%tDim)]:corrYA[((stackNumber)%tDim)];
			corrZ=isViewB?corrZB[((stackNumber)%tDim)]:corrZA[((stackNumber)%tDim)];
			//IJ.log("stk="+stackNumber +"  vslc="+vSliceNumber);
			initiateStack(stackNumber, 0);
			ip = fivStacks.get(stackNumber).getProcessor(vSliceNumber+(sliceNumber%2==0?0:dZ)+corrZ);
			ip.translate(corrX, corrY);
		}
		if (dimOrder.toLowerCase().matches(".*split.*c.*")) {

			if (dimOrder.toLowerCase().matches(".*split(ratio)*c.*")){
				vSliceNumber = (n%2 + sliceNumber/2) + (isViewB?zDim*(cDim/2)*(dimOrder.toLowerCase().matches(".*splitsequentialc.*")?2:1):0);
			} else {
				vSliceNumber = (sliceNumber)+(isViewB?zDim*(cDim/2)*(dimOrder.toLowerCase().matches(".*splitsequentialc.*")?2:1):0);
			}

			//ADJUSTMENTS BELOW DEAL WITH CALLING C1 AND C4 FOR CSM MODE SWITCH TO JUST 2 MAIN RG CHANNELS
			//I DO NOT FULLY UNDERSTAND HOW OR WHY IT WORKS!!!???
			//	if (dimOrder.toLowerCase().matches(".*splitsequentialc.*")) {
			//	if (vSliceNumber%2 == 0) {
			//	vSliceNumber = vSliceNumber-1;
			//	} else {
			//	vSliceNumber = vSliceNumber-1;
			//	}
			//	}

			if (vSliceNumber>fivStacks.get(stackNumber).getSize()) {
				vSliceNumber = vSliceNumber-fivStacks.get(stackNumber).getSize();
				stackNumber++;
			}

			//	if (stackNumber >= fivStacks.size()){
			//	stackNumber = fivStacks.size()-1;
			//	}

			int frameNumber = (slice-1)/(cDim*zDim*vDim);
			corrX=isViewB?corrXB[frameNumber]:corrXA[frameNumber];
			corrY=isViewB?corrYB[frameNumber]:corrYA[frameNumber];
			corrZ=isViewB?corrZB[frameNumber]:corrZA[frameNumber];
			//	IJ.log("stk="+stackNumber +"  vslc="+vSliceNumber);
			initiateStack(stackNumber, 0);
			if (dimOrder.toLowerCase().matches(".*splitratioc.*")){
				ip = fivStacks.get(stackNumber).getProcessor(vSliceNumber);	
			} else {
				ip = fivStacks.get(stackNumber).getProcessor(vSliceNumber+(sliceNumber%(2)==0?0:dZ*2)+corrZ);
			}
			ip.setInterpolationMethod(ImageProcessor.BICUBIC);
			ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));

			boolean ratioing = false;
			if (fivStacks.get(0).getInfo()[0].fileName.matches(".*Decon(-Fuse|_reg)_.*aaa_.*")){  //StarryNiteFeeder output
				int w = ip.getWidth();
				int h = ip.getHeight();
				int xOri = 0+((1-(n+1)%2)*(w/2));
				int yOri = 0;

				ip.setRoi(xOri, yOri, w/2, h);
			}else if (ip.getWidth()==2048 && vDim ==2) { //NIBIB splitview setup(?)
				//	dX=2;
				//	dY=0;
				int xOri = 256+((1-(n+1)%2)*(1024));
				int yOri = 0+((1-(n+1)%2)*(0));
				ip.setRoi(xOri, yOri, 512, 512);
			}else if (ip.getWidth()==2048 && vDim ==1) { //Yale ratio setup(?)
				//	dX=2;
				//	dY=0;
				ratioing = true;
				int xOri = 0+(((n+1)%2)*(1024));
				int yOri = 0+(((n+1)%2)*(0));
				ip.setRoi(xOri, yOri, 1024, 2048);


			} else if (ip.getWidth()==1536) {	//Yale splitview setup

				int xOri = 0+((0+(n+1)%2)*(1024));
				int yOri = 0+((1-(n+1)%2)*(0));
				ip.setRoi(xOri, yOri, 512, 512);
			} 
			ip = ip.crop();
			if (fivStacks.get(0).getInfo()[0].fileName.matches(".*Decon(-Fuse|_reg)_.*aaa_.*")){  //StarryNiteFeeder output
				ip.flipHorizontal();
			}
			ip.translate((1-n%2)*dX, (1-n%2)*dY);
			double corediffX = corrX-corrXA[0];
			double corediffY = corrY-corrYA[0];

			if (ratioing) {
				String wormTraceMapPath = infoDir + File.separator + "ThermoTraceMap.tif";
				bigIP=null;
				if (isViewB) {
					if (bigIP == null){
						if (new File(wormTraceMapPath).canRead()){
							ImagePlus bigImp = new ImagePlus(wormTraceMapPath);
							bigIP = bigImp.getProcessor();
						}
						
						if (bigIP == null){
							bigIP = new FloatProcessor(907, 1200);
							double[] ramp = new double[bigIP.getWidth()];
							for (double i=0; i<bigIP.getWidth(); i++)
								ramp[(int)i] = (double)(18d+(i*5d/(double)bigIP.getWidth()));
							int offset;
							for (int y=0; y<bigIP.getHeight(); y++) {
								offset = y*bigIP.getWidth();
								for (int x=0; x<bigIP.getWidth(); x++)
									bigIP.putPixelValue(x,y,(ramp[x]));
							}
							for (int t=0; t<ownerImp.getNFrames(); t++){
								double drawX = corrXB[t]-corrXA[0];
								double drawY = corrYB[t]-corrYA[0];

								ImageProcessor stampIP = new FloatProcessor(5,5);
								Arrays.fill((float[])stampIP.getPixels(), ((float)(((t)-1)*0.033)));
								bigIP.insert(stampIP, ((bigIP.getWidth()-2)/2)+(int)drawX/100, ((bigIP.getHeight()-2)/2)+(int)drawY/100);

							}
						}
					}

				} else {


					if (n%2!=0){
						statsIP = ip.duplicate();
						int[] ipHis = statsIP.getHistogram();
						double ipHisMode = 0.0;
						int ipHisLength = ipHis.length;
						int ipHisMaxBin = 0;
						for (int h=0; h<ipHisLength; h++) {
							if (ipHis[h] > ipHisMaxBin) {
								ipHisMaxBin = ipHis[h];
								ipHisMode = (double)h;
							}
						}
						statsIP.subtract(ipHisMode);
						new RankFilters().rank(statsIP, 2, RankFilters.MEDIAN, 0, 0);	

					}
					if (n%2==0){
						if(segmentLiveNeuron && !isViewB){

							// pushing limits 08082019
//							threshModeCoeff = 0.079;  
//							minSize = 900;
//							maxSize = 100000;
//							minCirc = 0.00;
//							maxCirc = 1.000;

							if (trackingDialog == null){
								trackingDialog = new GenericDialog("Tracking Parameters");
								trackingDialog.addNumericField("thresholdCoeff", threshModeCoeff, 4);
								trackingDialog.addNumericField("minSize", minSize, 0);
								trackingDialog.addNumericField("maxSize", maxSize, 0);
								trackingDialog.addNumericField("minCirc", minCirc, 4);
								trackingDialog.addNumericField("maxCirc", maxCirc, 4);
								trackingDialog.centerDialog(true);
								trackingDialog.pack();
								trackingDialog.show();
								if (!trackingDialog.wasCanceled()){
									threshModeCoeff = trackingDialog.getNextNumber();
									minSize = trackingDialog.getNextNumber();
									maxSize = trackingDialog.getNextNumber();
									minCirc = trackingDialog.getNextNumber();	
									maxCirc = trackingDialog.getNextNumber();
								}
							}else {

							}

							if( ownerImp.getRoiManager().getROIsByNumbers().get(""+ownerImp.getChannel()+"_"+ownerImp.getSlice()+"_"+ownerImp.getFrame())==null
									|| ownerImp.getRoiManager().getROIsByNumbers().get(""+ownerImp.getChannel()+"_"+ownerImp.getSlice()+"_"+ownerImp.getFrame()).isEmpty()) {
								ImageProcessor maskIP = ip.duplicate();

								int[] ipHis = maskIP.getHistogram();
								double ipHisMode = 0.0;
								int ipHisLength = ipHis.length;
								int ipHisMaxBin = 0;
								for (int h=0; h<ipHisLength; h++) {
									if (ipHis[h] > ipHisMaxBin) {
										ipHisMaxBin = ipHis[h];
										ipHisMode = (double)h;
									}
								}
								maskIP.subtract(ipHisMode);
								new RankFilters().rank(maskIP, 2, RankFilters.MEDIAN, 0, 0);	
								ImageProcessor preppedIP = maskIP.duplicate();

								maskIP.threshold((int) (ipHisMode*threshModeCoeff));
								maskIP.setThreshold(255,255,ImageProcessor.NO_LUT_UPDATE);

								ImagePlus maskImp = new ImagePlus("Mask", maskIP);
								maskImp.setMotherImp(this.ownerImp, 0);

								ResultsTable resTab = new ResultsTable();
								resTab.setDelimiter(',');
								ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES
										+ ParticleAnalyzer.ADD_TO_MANAGER
										, ParticleAnalyzer.AREA+ ParticleAnalyzer.CENTER_OF_MASS
										+ ParticleAnalyzer.CIRCULARITY+ ParticleAnalyzer.MEAN
										+ ParticleAnalyzer.FERET 
										+ ParticleAnalyzer.PERIMETER 
										, resTab
										, minSize, maxSize
										, minCirc, maxCirc); 
								pa.setHideOutputImage(true);

								while (!pa.analyze(maskImp, maskIP)){
									IJ.wait(50);
								}
								IJ.wait(50);
								maskImp.flush();
								double hiRGRatio =0;
								double hiProduct = 0; 
								double hiFeret = 0; 
								double	loPARatio = 1000000;
								double hiQuadraStandard = 0;
								Roi hiRGRoi = null;
								Roi hiProductRoi=null;
								Roi hiFeretRoi=null;
								Roi loPARatioRoi=null;
								Roi hiQuadraRoi = null;

								ArrayList<Roi> roisbn = ownerImp.getRoiManager().getROIsByNumbers().get(""+ownerImp.getChannel()+"_"+ownerImp.getSlice()+"_"+ownerImp.getFrame());

								if (roisbn !=null && statsIP != null) {
									for (int r=0;r<roisbn.size();r++) {
										ImageProcessor greenIP = statsIP.duplicate();
										ImageProcessor redIP = preppedIP.duplicate();

										greenIP.setRoi(roisbn.get(r));
										redIP.setRoi(roisbn.get(r));

										ImageStatistics greenStat = greenIP.getStatistics();
										ImageStatistics redStat = redIP.getStatistics();

										double roiRGMeanRatio = redStat.mean/greenStat.mean;
										double roiStdDevProduct = greenStat.stdDev*redStat.stdDev;
										double roiFeretDiameterRatio = greenStat.major
												/greenStat.minor;
										double roiPARatio = roisbn.get(r).getLength()
												/greenStat.area;

										double roiQuadraTest = (Math.pow(roiRGMeanRatio,7))*(Math.pow(roiStdDevProduct,1))*(Math.pow(roiFeretDiameterRatio,1))/(Math.pow(roiPARatio,1));

										if (roiQuadraTest > hiQuadraStandard || hiQuadraStandard ==0) {
											hiQuadraStandard = roiQuadraTest;
											hiQuadraRoi = roisbn.get(r);
										}
										if (roiRGMeanRatio > hiRGRatio || hiRGRatio ==0) {
											hiRGRatio = roiRGMeanRatio;
											hiRGRoi = roisbn.get(r);
										}
										if (roiStdDevProduct > hiProduct || hiProduct ==0) {

											hiProduct = roiStdDevProduct;

											hiProductRoi = roisbn.get(r);

										} 
										if (roiFeretDiameterRatio > hiFeret || hiFeret==0) {

											hiFeret = roiFeretDiameterRatio;

											hiFeretRoi = roisbn.get(r);

										} 
										if (roiPARatio < loPARatio || loPARatio == 1000000) {

											loPARatio = roiPARatio;

											loPARatioRoi = roisbn.get(r);

										} 
									}
									for (Object thisRoi:roisbn.toArray()) {
										Roi roi = (Roi)thisRoi;
										if (roi != hiQuadraRoi) {
											ownerImp.getRoiManager().setSelectedIndexes(new int[]{ownerImp.getRoiManager().getListModel().indexOf(roi.getName())});
											ownerImp.getRoiManager().delete(false);
											//										IJ.log("deleted stray roi");
										} else {
										}
									}
								}
								if (hiRGRoi!=null)
									hiRGRoi.setFillColor(Colors.decode("#ff00ffff", Color.cyan));
								if (hiProductRoi!=null)
									hiProductRoi.setFillColor(Colors.decode("#ff8844ff", Color.magenta));
								if (hiFeretRoi!=null)
									hiFeretRoi.setFillColor(Colors.decode("#ffff0000", Color.red));
								if (loPARatioRoi!=null)
									loPARatioRoi.setFillColor(Colors.decode("#ffffff00", Color.yellow));
								if (hiQuadraRoi!=null) {
									hiQuadraRoi.setFillColor(Colors.decode("#ffffaa88", Color.orange));
									ImageProcessor greenIP = statsIP.duplicate();
									ImageProcessor redIP = preppedIP.duplicate();

									greenIP.setRoi(hiQuadraRoi);
									greenIP.fillOutside(hiQuadraRoi);
									greenIP.setMask(greenIP.getMask());
									//								new ImagePlus("greentest", greenIP.crop()).show();

									redIP.setRoi(hiQuadraRoi);
									redIP.fillOutside(hiQuadraRoi);
									redIP.setMask(redIP.getMask());
									//								new ImagePlus("redtest", redIP.crop()).show();

									ImageProcessor ratioIP = redIP.crop().toFloat(1, null);
									ratioIP.copyBits(greenIP.crop().toFloat(1, null), 0, 0, Blitter.DIVIDE);
									//								new ImagePlus("ratiotest",ratioIP).show();

									ImageStatistics greenStat = greenIP.getStatistics();
									ImageStatistics redStat = redIP.getStatistics();
									ImageStatistics ratioStat = ratioIP.getStatistics();

									double roiRGMeanRatio = redStat.mean/greenStat.mean;
									double ratioIPmean = ratioStat.mean;
									IJ.log(""+roiRGMeanRatio+" =? "+ratioIPmean);

									
									
									maskIP = redIP.duplicate();

									ipHis = maskIP.getHistogram();
									ipHisMode = 0.0;
									ipHisLength = ipHis.length;
									ipHisMaxBin = 0;
									for (int h=0; h<ipHisLength; h++) {
										if (ipHis[h] > ipHisMaxBin) {
											ipHisMaxBin = ipHis[h];
											ipHisMode = (double)h;
										}
									}
									maskIP.subtract(ipHisMode);
									new RankFilters().rank(maskIP, 2, RankFilters.MEDIAN, 0, 0);	
									preppedIP = maskIP.duplicate();

									maskIP.threshold((int) (redStat.max/10));
									maskIP.setThreshold(255,255,ImageProcessor.NO_LUT_UPDATE);

									maskImp = new ImagePlus("Mask", maskIP);
									maskImp.setMotherImp(this.ownerImp, 0);

									resTab = new ResultsTable();
									resTab.setDelimiter(',');
									pa = new ParticleAnalyzer(ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES
											+ ParticleAnalyzer.ADD_TO_MANAGER
											, ParticleAnalyzer.AREA+ ParticleAnalyzer.CENTER_OF_MASS
											+ ParticleAnalyzer.CIRCULARITY+ ParticleAnalyzer.MEAN
											+ ParticleAnalyzer.FERET 
											+ ParticleAnalyzer.PERIMETER 
											, resTab
											, minSize/10, minSize
											, minCirc, maxCirc); 
									pa.setHideOutputImage(true);

									while (!pa.analyze(maskImp, maskIP)){
										IJ.wait(50);
									}
									IJ.wait(50);
									maskImp.flush();

									if (roisbn!=null && roisbn.size()>1) {
										Roi headFitRoi = null;
										Roi tailFitRoi = null;
										for (int r=1; r<roisbn.size(); r++) {
											maskIP.setRoi(roisbn.get(r));
											ImageStatistics stats = maskIP.getStatistics();										
											double dx = stats.major*Math.cos(stats.angle/180.0*Math.PI)/2.0;
											double dy = - stats.major*Math.sin(stats.angle/180.0*Math.PI)/2.0;
											double x1 = stats.xCentroid - dx;
											double x2 = stats.xCentroid + dx;
											double y1 = stats.yCentroid - dy;
											double y2 = stats.yCentroid + dy;
											double aspectRatio = stats.minor/stats.major;
											if (aspectRatio < 0.3){
												ownerImp.getRoiManager().setSelectedIndexes(new int[]{ownerImp.getRoiManager().getListModel().indexOf(roisbn.get(r).getName())});
												ownerImp.getRoiManager().delete(false);
												r--;
												continue;
											}
											Roi headEllipse = new EllipseRoi(x1,y1,x2,y2,aspectRatio);
											headEllipse = RoiEnlarger.enlarge(headEllipse, (stats.minor));
											headFitRoi = (new ShapeRoi(headEllipse).and(new ShapeRoi(hiQuadraRoi)));
											headFitRoi.setPosition(ownerImp.getChannel(), ownerImp.getSlice(), ownerImp.getFrame());
											tailFitRoi = (new ShapeRoi(headFitRoi).xor(new ShapeRoi(hiQuadraRoi)));
											tailFitRoi.setPosition(ownerImp.getChannel(), ownerImp.getSlice(), ownerImp.getFrame());
										}
											if (headFitRoi !=null)
												ownerImp.getRoiManager().addRoi(headFitRoi, false, Color.blue, 1, false);
											if (tailFitRoi !=null)
												ownerImp.getRoiManager().addRoi(tailFitRoi, false, Color.red, 1, false);
									}
								}
							}
						}
					}
				}
				if (isViewB) {
					if (ownerImp!=null)
						ownerImp.setRoi(((bigIP.getWidth()-2)/2)+(int)corediffX/100, ((bigIP.getHeight()-2)/2)+(int)corediffY/100, 5, 5);
//					IJ.saveAsTiff(new ImagePlus("",bigIP), wormTraceMapPath);
					ip = bigIP;
				}

			}

		}
		if (dimOrder == "xyzct") {
			corrX=isViewB?corrXB[((stackNumber)%tDim)]:corrXA[((stackNumber)%tDim)];
			corrY=isViewB?corrYB[((stackNumber)%tDim)]:corrYA[((stackNumber)%tDim)];
			corrZ=isViewB?corrZB[((stackNumber)%tDim)]:corrZA[((stackNumber)%tDim)];

			initiateStack(stackNumber, 0);
			ip = fivStacks.get(stackNumber).getProcessor(sliceNumber/cDim + ((sliceNumber%cDim)*fivStacks.get(stackNumber).getSize()/(vDim))+(sliceNumber%2==0?0:dZ)
					+(isViewB?fivStacks.get(stackNumber).getSize()/(cDim*vDim):0)+corrZ);
			ip.translate(corrX, corrY);
		}
		if (dimOrder == "xyztc") {
			corrX=isViewB?corrXB[((stackNumber)%tDim)]:corrXA[((stackNumber)%tDim)];
			corrY=isViewB?corrYB[((stackNumber)%tDim)]:corrYA[((stackNumber)%tDim)];
			corrZ=isViewB?corrZB[((stackNumber)%tDim)]:corrZA[((stackNumber)%tDim)];
			//IJ.log(""+stackNumber+" "+corrX+" "+corrY+" "+corrZ);
			initiateStack(stackNumber, 0);
			ip = fivStacks.get(stackNumber).getProcessor(sliceNumber+(sliceNumber%2==0?0:dZ)+corrZ);
			ip.translate(corrX, corrY);
		}

		//	IJ.log(dimOrder+" "+stackNumber+" "+fivStacks.get(stackNumber).getSize()+" "+sliceNumber+" "+vSliceNumber+" ");

		if (ip instanceof FloatProcessor) {
			//	ip = ip.convertToShort(false);
		} else {
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
		}

		ip.setInterpolationMethod(ImageProcessor.BICUBIC);
		ip.translate(skewXperZ*(n-1), skewYperZ*(n-1));

		//FALSING OUT 488G/R RATIOING (06192018)
		if (false && dimOrder.toLowerCase().matches(".*splitsequentialc.*") && n%cDim == 1) {
			ImageProcessor nextIP = fivStacks.get(stackNumber).getProcessor(vSliceNumber+1);

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

		//	ip.multiply(0.25);

		//	ip.setMinAndMax(min, max);
		if (edges) {
			ip.findEdges();

			//	ip.doAffineTransform(new AffineTransform(1.075,0.25,0.2,1.075,0,0));
		}
		return ip;
	}

	public void initiateStack(int stkNum, int slcNum) {
		if (fivStacks!=null && fivStacks.size()>stkNum && fivStacks.get(stkNum).infoArray.length>slcNum) {
			fivStacks.get(stkNum).setupStack();

			String currentFileName =fivStacks.get(stkNum).infoArray[0].fileName;

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
		return nSlices/vDim;
	}

	/** Returns the label of the Nth image. */
	public String getSliceLabel(int n) {
		if (n<1 || n>nSlices)
			throw new IllegalArgumentException("Argument out of range: "+n);
		if (infoArray==null || infoArray[0].sliceLabels==null || infoArray[0].sliceLabels.length!=nSlices) {
			if (n<1 || n>nSlices) {
				//	IJ.runMacro("waitForUser(\""+n+"\");");
				return fivStacks.get(0).infoArray[0].fileName;
				//	throw new IllegalArgumentException("Argument out of range: "+n);
			}
			int z = n % fivStacks.get(0).nSlices;
			int t = (int) Math.floor(n/fivStacks.get(0).nSlices);
			if (z==0) {
				z = fivStacks.get(0).nSlices;
				t=t-1;
			}
			//	IJ.log(""+n+" "+z+" "+t);
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
		if (this.dimOrder.contains("Split") && !dimOrder.contains("Split")){
			nSlices=nSlices/2;
			cDim=cDim/2;
		}
		if (!this.dimOrder.contains("Split") && dimOrder.contains("Split")){
			nSlices=nSlices*2;
			cDim=cDim*2;
		}
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

	public ImagePlus getOwnerImp() {
		return ownerImp;
	}

	public void setOwnerImp(ImagePlus ownerImp) {
		this.ownerImp = ownerImp;
	}

	public void setSegmentLiveNeuron(boolean b) {
		this.segmentLiveNeuron = b;
	}

	public boolean getSegmentLiveNeuron() {

		return this.segmentLiveNeuron;
	}

	public GenericDialog getTrackingDialog() {
		return trackingDialog;
	}

	public void setTrackingDialog(GenericDialog trackingDialog) {
		this.trackingDialog = trackingDialog;
	}

}
