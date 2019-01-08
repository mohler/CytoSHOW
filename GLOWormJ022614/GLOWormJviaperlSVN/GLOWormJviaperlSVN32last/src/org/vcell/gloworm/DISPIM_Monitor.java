package org.vcell.gloworm;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.image.ColorModel;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.sun.scenario.effect.impl.prism.ps.PPSInvertMaskPeer;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.VirtualStack;
import ij.WindowManager;
import ij.gui.EllipseRoi;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.SelectKeyChannelDialog;
import ij.gui.StackWindow;
import ij.gui.YesNoCancelDialog;
import ij.io.FileInfo;
import ij.io.FileOpener;
import ij.io.OpenDialog;
import ij.io.TiffDecoder;
import ij.macro.MacroRunner;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.FFT;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.FolderOpener;
import ij.plugin.MultiFileInfoVirtualStack;
import ij.plugin.PlugIn;
import ij.plugin.RoiRotator;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.Projector;
import ij.plugin.filter.Projector16bit;
import ij.plugin.frame.SyncWindows;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.LUT;
import ij.process.ShortProcessor;

public class DISPIM_Monitor implements PlugIn, ActionListener, ChangeListener, ImageListener {

	//Info from proximal tiff header in MMomeTIFF vvvvvvv
	static int All_images_dependently = 1;
	static int All_images_independently = 2;
	static int One_image_only = 3;	
	static int No_registration = 4;
	boolean inputTmx;
	
	private double diSPIM_MM_LaserExposure_ms;
	private String diSPIM_MM_MVRotations;
	private String diSPIM_MM_SPIMtype;
	private String diSPIM_MM_UUID;
	//	 private int diSPIM_MM_SPIMAcqSettings;
	private String diSPIM_MM_spimMode;
	private boolean diSPIM_MM_isStageScanning;
	private boolean diSPIM_MM_useTimepoints;
	private int diSPIM_MM_numTimepoints;
	private double diSPIM_MM_timepointInterval;
	private boolean diSPIM_MM_useMultiPositions;
	private boolean diSPIM_MM_useChannels;
	private String diSPIM_MM_channelMode;
	private int diSPIM_MM_numChannels;
	//	 private int diSPIM_MM_channels;
	private ArrayList<Boolean> diSPIM_MM_useChannel_ArrayList;
	private ArrayList<String> diSPIM_MM_group_ArrayList;
	private ArrayList<String> diSPIM_MM_config_ArrayList;
	private String diSPIM_MM_channelGroup;
	private boolean diSPIM_MM_useAutofocus;
	private int diSPIM_MM_numSides;
	private boolean diSPIM_MM_firstSideIsA;
	private double diSPIM_MM_delayBeforeSide;
	private int diSPIM_MM_numSlices;
	private double diSPIM_MM_stepSizeUm;
	private boolean diSPIM_MM_minimizeSlicePeriod;
	private double diSPIM_MM_desiredSlicePeriod;
	private double diSPIM_MM_desiredLightExposure;
	private boolean diSPIM_MM_centerAtCurrentZ;
	//	 private int diSPIM_MM_sliceTiming;
	private double diSPIM_MM_scanDelay;
	private int diSPIM_MM_scanNum;
	private double diSPIM_MM_scanPeriod;
	private double diSPIM_MM_laserDelay;
	private double diSPIM_MM_laserDuration;
	private double diSPIM_MM_cameraDelay;
	private double diSPIM_MM_cameraDuration;
	private double diSPIM_MM_cameraExposure;
	private double diSPIM_MM_sliceDuration;
	private boolean diSPIM_MM_valid;
	private String diSPIM_MM_cameraMode;
	private boolean diSPIM_MM_useHardwareTimepoints;
	private boolean diSPIM_MM_useSeparateTimepoints;
	private String diSPIM_MM_Position_X;
	private String diSPIM_MM_Position_Y;
	private String diSPIM_MM_Date;
	private int diSPIM_MM_MetadataVersion;
	private int diSPIM_MM_Width;
	private double diSPIM_MM_PixelAspect;
	private String[] diSPIM_MM_ChNames;
	private int diSPIM_MM_Height;
	private String diSPIM_MM_SlicePeriod_ms;
	private int diSPIM_MM_GridColumn;
	private double diSPIM_MM_PixelSize_um;
	private int diSPIM_MM_Frames;
	private String diSPIM_MM_Source;
	private int diSPIM_MM_Channels;
	private String diSPIM_MM_AcqusitionName;
	private int diSPIM_MM_NumberOfSides;
	private String diSPIM_MM_SPIMmode;
	private int[] diSPIM_MM_ChColors;
	private int diSPIM_MM_Slices;
	private String diSPIM_MM_UserName;
	private int diSPIM_MM_Depth;
	private String diSPIM_MM_PixelType;
	private String diSPIM_MM_Time;
	private String diSPIM_MM_FirstSide;
	private double diSPIM_MM_zStep_um;
	private boolean diSPIM_MM_SlicesFirst;
	private int[] diSPIM_MM_ChContrastMin;
	private String diSPIM_MM_StartTime;
	private String diSPIM_MM_MVRotationAxis;
	private String diSPIM_MM_MicroManagerVersion;
	private int diSPIM_MM_IJType;
	private int diSPIM_MM_GridRow;
	private String diSPIM_MM_VolumeDuration;
	private int diSPIM_MM_NumComponents;
	private String diSPIM_MM_Position_SPIM_Head;
	private int diSPIM_MM_BitDepth;
	private String diSPIM_MM_ComputerName;
	private String diSPIM_MM_CameraMode;
	private boolean diSPIM_MM_TimeFirst;
	private int[] diSPIM_MM_ChContrastMax;
	private int diSPIM_MM_Positions;
	//Info from proximal tiff header in MMomeTIFF ^^^^^^


	private boolean doMipavDecon;
	private boolean doGPUdecon;
	private int keyChannel;
	private int slaveChannel;
	private int oldLength;
	private int cDim=1;
	private int zDim=1;
	private int tDim=1;
	private int vDim=1;
	private int pDim=1;
	private String[] diSPIM_MM_ChColorStrings;
	private String[] diSPIM_MM_ChContrastMinStrings;
	private String[] diSPIM_MM_ChContrastMaxStrings;
	private String dimOrder;
	private WG_Uploader wgUploadJob;
	private boolean autoUpload;
	private boolean uploadPending;
	private boolean uploadRunning;
	private int iterations;
	private Process regDeconProcess;
	private boolean doSerialRegPriming;
	private double sliceTresholdVsModeA;
	private double sliceTresholdVsModeB;
	private long tempTime = -1;
	private MultiFileInfoVirtualStack deconmfivs;
	private MultiFileInfoVirtualStack dfProjXmfivs;
	private MultiFileInfoVirtualStack dfProjYmfivs;
	private boolean autodepth;
	private JButton[][] dispimToolsButton;
	private JButton[][] dispimPreviewButton;
	private JButton[][] fuseButton;
	private JButton[][] splitButton;
	private Checkbox[][] edgeBox;

	private String dirOrOMETiff;
	String timecode = "" + -1;

	String[] fileListA = { "" };
	String[] fileListB = { "" };
	// fileListB = newArray("");
	String[] fileRanksA = { "" };
	String[] fileRanksB = { "" };
	String[] fileNumsA = { "" };
	String[] fileNumsB = { "" };
	String[] fileSortA = { "" };
	String[] fileSortB = { "" };
	String[] newTifListA = { "" };
	String[] newTifListB = { "" };
	String[] listA = { "" };
	String[] listB = { "" };
	String[] deconFileList1 = { "" };
	String[] deconFileList2 = { "" };
	String[] deconList1 = { "" };
	String[] deconList2 = { "" };
	String big5DFileListAString = ("");
	String big5DFileListBString = ("");

	ImagePlus[] impAs;
	ImagePlus[] impBs;
	ImagePlus[] impDF1s;
	ImagePlus[] impDF2s;
	ImagePlus[] impPrxs;
	ImagePlus[] impPrys;

	CompositeImage[] ciDFs;
	CompositeImage[] ciPrxs;
	CompositeImage[] ciPrys;

	Projector16bit[] prjXs = new Projector16bit[1];
	Projector16bit[] prjYs = new Projector16bit[1];

	Roi[] rectRoiAs  = new Roi[1];
	Roi[] rectRoiBs  = new Roi[1];
	int[] wasFrameA = new int[1];
	int[] wasFrameB = new int[1];
	int[] wasSliceA = new int[1];
	int[] wasSliceB = new int[1];
	int[] wasChannelA = new int[1];
	int[] wasChannelB = new int[1];
	int[] zFirstA =new int[1];
	int[] zLastA =new int[1];
	int[] zFirstB =new int[1];
	int[] zLastB =new int[1];
	String[] lastMatrix = new String[1];
	boolean[] doProcessing= new boolean[1];
	boolean[] wasEdgesA= new boolean[1]; 
	boolean[] wasEdgesB= new boolean[1]; 
	private String savePath;
	private String tempDir;
	int wavelengths = 1;
	int outputWavelengths = 1;
	int zSlices = 1;
	private boolean monitoring;
	private String rerunArg;

	LUT[] lutA = new LUT[4];
	LUT[] lutB = new LUT[4];
	double[] minA = {0,0,0,0};
	double[] maxA = {255,255,255,255};
	double[] minB = {0,0,0,0};
	double[] maxB = {255,255,255,255};
	String channelsA = "11";
	int modeA = CompositeImage.COMPOSITE;
	int modeB = CompositeImage.COMPOSITE;
	double vWidth = 0.1625;
	double vHeight = 0.1625;
	double vDepthRaw = 1.000;
	double vDepthDecon = 0.1625;
	String vUnit = "micron";
	double cropScaleFactor = 1d;
	double cropWidthDefault = 325*cropScaleFactor;
	double cropHeightDefault = 425*cropScaleFactor;
	double[] cropWidthA = new double[1];
	double[] cropHeightA = new double[1];
	double[] cropWidthB = new double[1];
	double[] cropHeightB = new double[1];

	boolean stackDualViewTimePoints = false;
	boolean singleImageTiffs = false;
	boolean omeTiffs = false;
	boolean stackLabviewTimePoints = false;
	boolean stageScan = false;
	private String prxPath;
	private String pryPath;
	private String keyString;
	private boolean splitChannels;
	private String dirOrOMETiffFinal;
	private int abRelOriValue;
	private File dc1File;
	private File dc2File;
	private File tmxFile;
	private ImageProcessor ipA2;
	private ImageProcessor ipA1;
	private ImageStack stackA1;
	private ImageStack stackA2;
	private ImageProcessor ipA1r;
	private ImageProcessor ipA2r;
	private ImagePlus impXA1;
	private ImagePlus impXA2;
	private ImageStack stackB1;
	private ImageStack stackB2;
	private ImageProcessor ipB1;
	private ImageProcessor ipB1r;
	private ImageProcessor ipB2;
	private ImagePlus impXB2;
	private ImagePlus impXB1;
	private ImageProcessor ipB2r;
	private Roi impRoiA;
	private Polygon pA;
	private Polygon pAR;
	private Roi impRoiB;
	private Polygon pB;
	private Polygon pBR;
	private File pryColor1File;
	private File pryColor2File;
	private File prxColor1File;
	private File prxColor2File;
	private File outputTmxFile;
	private File outputDCFile;
	private File saveFile;
	private MultiFileInfoVirtualStack[] stackDFs;
	private MultiFileInfoVirtualStack[] stackPrxs;
	private MultiFileInfoVirtualStack[] stackPrys;
	private MultiFileInfoVirtualStack[] stackAs;
	private MultiFileInfoVirtualStack[] stackBs;
	private String saveName;
	private JSpinner[][] xSpinner;
	private JSpinner[][] ySpinner;
	private JSpinner[][] zSpinner;
	private Panel[][] spinnerPanel;
	private String dirConcat;
	private String diSPIM_MM_channelOrder = "GR";
	private int[] posIntArray;
	private Roi[] origRoiAs;
	private Roi[] origRoiBs;
	private Roi[] ellipseRoiAs;
	private Roi[] ellipseRoiBs;
	private boolean lineageDecons;
	private String paramsPath;
	private int depthSkipFactor;
	private boolean haveAxesRoiA;
	private boolean haveAxesRoiDF;
	private boolean haveAxesRoiB;
	private String maxXPath;
	private String maxYPath;
	private String maxZPath;
	private File maxXColor1File;
	private File maxYColor1File;
	private File maxZColor1File;
	private File maxXColor2File;
	private File maxYColor2File;
	private File maxZColor2File;
//	private MultiFileInfoVirtualStack dfMaxXmfivs;
//	private MultiFileInfoVirtualStack dfMaxYmfivs;
//	private MultiFileInfoVirtualStack dfMaxZmfivs;
//	private ImagePlus[] impMaxXs;
//	private CompositeImage[] ciMaxXs;
//	private ImageWindow maxXwin;
//	private ImagePlus[] impMaxYs;
//	private CompositeImage[] ciMaxYs;
//	private ImageWindow maxYwin;
//	private ImagePlus[] impMaxZs;
//	private CompositeImage[] ciMaxZs;
//	private ImageWindow maxZwin;
	private boolean orientBeforeLineage;
	private double[] anglesA;
	private double[] anglesB;
	private double[] longAxesA;
	private double[] longAxesB;
	private double[][] rotXYZs;
	private boolean snfCycleComplete = false;
	private String keyView;
	private boolean doPrimeFromDefaultRegTMX;
	private boolean doForceDefaultRegTMX;

	public Process getRegDeconProcess() {
		return regDeconProcess;
	}

	public void setRegDeconProcess(Process regDeconProcess) {
		this.regDeconProcess = regDeconProcess;
	}

	public boolean isDoDecon() {
		return doGPUdecon;
	}

	public void setDoDecon(boolean doDecon) {
		this.doGPUdecon = doDecon;
	}

	public void runDecon() {
		if (!doMipavDecon && !doGPUdecon) {
			IJ.setKeyDown(KeyEvent.VK_ESCAPE);
		}
	}

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	public void run(final String arg) {
		ImagePlus.addImageListener(this);
		String[] args = arg.split("\\|");
		IJ.log(arg);
		dirOrOMETiff = "";
		dirOrOMETiff = args[0];
		IJ.log(dirOrOMETiff);
		//		keyString = "";
		boolean fullRun = !(arg.contains("rerunWithDecon"));
		

		String uniqueClientIdentifier;
		try {
			uniqueClientIdentifier = InetAddress.getLocalHost().getHostName() +"_"+ GetNetworkAddress.GetAddress("mac");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			uniqueClientIdentifier = GetNetworkAddress.GetAddress("mac");
		}

		if (fullRun) {
			OpenDialog.setDefaultDirectory(Prefs.get("diSPIMmonitor.input", dirOrOMETiff));

			while (!(new File(dirOrOMETiff)).isDirectory()
					&& !dirOrOMETiff.endsWith(".tif")) {
				if (arg.contains("newMM")) {
					dirOrOMETiff = IJ
							.getDirectory("Select a timepoint directory with MM diSPIM raw data");
					keyString = (new File(dirOrOMETiff)).getName().split("_")[0];
					IJ.log(dirOrOMETiff);
					String[] dirChunks = dirOrOMETiff.split("\\|");
					for (String s:dirChunks){
						IJ.log(s);
					}
					dirOrOMETiff = dirChunks[0];
					if (dirChunks.length>1){
						String[] posStringArray = Arrays.copyOfRange(dirChunks, 1, dirChunks.length-1);
						posIntArray = new int[posStringArray.length];
						for(int p=0;p<posIntArray.length;p++){
							try{
								posIntArray[p] = Integer.parseInt(posStringArray[p].trim());
							} catch (NumberFormatException nfe){
								posIntArray[p]=-1;
							}
						}
					}
					IJ.log(dirOrOMETiff);

					dirOrOMETiff = (new File(dirOrOMETiff)).getParent()+File.separator;
					stackDualViewTimePoints = true;
					singleImageTiffs = false;
					omeTiffs = true;
					stackLabviewTimePoints = false;
					stageScan = false;
				} else if (arg.contains("sstMM")) {
					;
					dirOrOMETiff = IJ
							.getFilePath("Select a file with WG MM diSPIM raw data");
					stackDualViewTimePoints = false;
					singleImageTiffs = true;
					omeTiffs = false;
					stackLabviewTimePoints = false;
					stageScan = false;
				} else if (arg.contains("megaTiffMM")) {
					;
					dirOrOMETiff = IJ
							.getFilePath("Select a file with old megaTiff MM diSPIM raw data");
					stackDualViewTimePoints = false;
					singleImageTiffs = false;
					omeTiffs = true;
					stackLabviewTimePoints = false;
					stageScan = false;
				} else if (arg.contains("stageScanMM")) {
					dirOrOMETiff = IJ
							.getDirectory("Select a timepoint directory with stage-scanned MM diSPIM raw data");
					keyString = (new File(dirOrOMETiff)).getName().split("_")[0];
					dirOrOMETiff = (new File(dirOrOMETiff)).getParent()+File.separator;
					stackDualViewTimePoints = true;
					singleImageTiffs = false;
					omeTiffs = true;
					stackLabviewTimePoints = false;
					stageScan = true;
				} else if (arg.contains("stageScansstMM")) {
					dirOrOMETiff = IJ
							.getFilePath("Select a file with stage-scanned WG MM diSPIM raw data");
					stackDualViewTimePoints = false;
					singleImageTiffs = true;
					omeTiffs = false;
					stackLabviewTimePoints = false;
					stageScan = true;
				} else if (arg.contains("scanStageLabView")) {
					dirOrOMETiff = IJ
							.getDirectory("Select master directory with stage-scanned LabView diSPIM raw data");
					stackDualViewTimePoints = false;
					singleImageTiffs = false;
					omeTiffs = false;
					stackLabviewTimePoints = true;
					stageScan = true;
				}else if (arg.contains("currentStacks")) {
					dirOrOMETiff = IJ
							.getDirectory("image");
					stackDualViewTimePoints = false;
					singleImageTiffs = false;
					omeTiffs = false;
					stackLabviewTimePoints = false;
					stageScan = false;
				} else {
					dirOrOMETiff = IJ
							.getDirectory("Select master directory with LabView diSPIM raw data");
					stackDualViewTimePoints = false;
					singleImageTiffs = false;
					omeTiffs = false;
					stackLabviewTimePoints = true;
					stageScan = false;
				}
			}
			

			Prefs.set("diSPIMmonitor.input", dirOrOMETiff);
			String dirOrOMETiffDirectory = dirOrOMETiff;
			if (!new File(dirOrOMETiff).isDirectory())
				dirOrOMETiffDirectory = new File(dirOrOMETiff).getParent();
			dirOrOMETiffFinal = dirOrOMETiffDirectory;

			File dirOrOMETiffFile = new File(dirOrOMETiff);
			if (dirOrOMETiffFile.isDirectory())
				savePath = dirOrOMETiff;
			else
				savePath = dirOrOMETiffFile.getParentFile().getParent()
				+ File.separator + dirOrOMETiffFile.getParentFile().getName()
				+ "_" + dirOrOMETiffFile.getName().split("_")[0] + "_"+ File.separator;

			String[] savePathChunks = savePath.split(Pattern.quote(File.separator));
			saveName = savePathChunks[savePathChunks.length-1];
			//			OpenDialog.setDefaultDirectory(Prefs.get("diSPIMmonitor.output", savePath));
			//			savePath = IJ.getDirectory("Select Output Folder")+ File.separator + saveName+"_Output"+ File.separator;
			//			new File(savePath).mkdirs();
			//			Prefs.set("diSPIMmonitor.output", new File(savePath).getParent());
			tempDir = IJ.getDirectory("temp");


			String[] dirOrOMETiffChunks = dirOrOMETiff
					.split(IJ.isWindows() ? "\\\\" : "/");

			if ((new File(dirOrOMETiff)).isDirectory() && !omeTiffs) {
				IJ.saveString("", dirOrOMETiff + "Big5DFileListA.txt");
				while (!(new File(dirOrOMETiff + "Big5DFileListA.txt")).exists())
					IJ.wait(100);
				IJ.saveString("", dirOrOMETiff + "Big5DFileListB.txt");
				while (!(new File(dirOrOMETiff + "Big5DFileListB.txt")).exists())
					IJ.wait(100);
				IJ.saveString("", dirOrOMETiff + "BigMAXFileListA.txt");
				while (!(new File(dirOrOMETiff + "BigMAXFileListA.txt")).exists())
					IJ.wait(100);
				IJ.saveString("", dirOrOMETiff + "BigMAXFileListB.txt");
				while (!(new File(dirOrOMETiff + "BigMAXFileListB.txt")).exists())
					IJ.wait(100);
			}

			if (args.length > 2) {
				wavelengths = Integer.parseInt(args[1]);
				zSlices = Integer.parseInt(args[2]);
			} else if (!omeTiffs || !dirOrOMETiffFile.isDirectory()) {
				//				GenericDialog gd = new GenericDialog("Data Set Parameters?");
				//				gd.addNumericField("Wavelengths", 2, 0);
				//				gd.addNumericField("Z Slices/Stack", 50, 0);
				//				gd.showDialog();
				//				;
				//				wavelengths = (int) gd.getNextNumber();
				//				zSlices = (int) gd.getNextNumber();
			}
			dirOrOMETiffFile = new File(dirOrOMETiff);
			if (arg.contains("currentStacks") && WindowManager.getImageCount()==2){
				pDim=1;
				impAs = new ImagePlus[pDim];
				impBs = new ImagePlus[pDim];
				origRoiAs = new Roi[pDim];
				origRoiBs = new Roi[pDim];
				ellipseRoiAs = new Roi[pDim];
				ellipseRoiBs = new Roi[pDim];
				rectRoiAs = new Roi[pDim];
				rectRoiBs = new Roi[pDim];
				doProcessing= new boolean[pDim];
				impDF1s  = new ImagePlus[pDim];
				impDF2s  = new ImagePlus[pDim];
				impPrxs = new ImagePlus[pDim];
				impPrys = new ImagePlus[pDim];

				ciDFs  = new CompositeImage[pDim];
				ciPrxs  = new CompositeImage[pDim];
				ciPrys  = new CompositeImage[pDim];

				prjXs = new Projector16bit[pDim];
				prjYs = new Projector16bit[pDim];

				wasFrameA = new int[pDim];
				wasFrameB = new int[pDim];
				wasSliceA = new int[pDim];
				wasSliceB = new int[pDim];
				wasChannelA = new int[pDim];
				wasChannelB = new int[pDim];
				zFirstA = new int[pDim];
				zLastA = new int[pDim];
				zFirstB = new int[pDim];
				zLastB = new int[pDim];
				lastMatrix = new String[pDim];
				cropWidthA = new double[pDim];
				cropHeightA = new double[pDim];
				cropWidthB = new double[pDim];
				cropHeightB = new double[pDim];			
				anglesA = new double[pDim];					
				anglesB = new double[pDim];					
				longAxesA = new double[pDim];					
				longAxesB = new double[pDim];					
				rotXYZs = new double[pDim][3];
				
				dispimToolsButton = new JButton[pDim][2];
				dispimPreviewButton = new JButton[pDim][2];
				fuseButton = new JButton[pDim][2];
				splitButton = new JButton[pDim][2];
				spinnerPanel = new Panel[pDim][2];
				xSpinner = new JSpinner[pDim][2];
				ySpinner = new JSpinner[pDim][2];
				zSpinner = new JSpinner[pDim][2];

				wasEdgesA = new boolean[pDim];
				wasEdgesB = new boolean[pDim];

				stackAs = new MultiFileInfoVirtualStack[pDim];
				stackBs = new MultiFileInfoVirtualStack[pDim];

				impAs[0] = WindowManager.getImage(1);
				impBs[0] = WindowManager.getImage(2);
				
				stackAs[0] = (MultiFileInfoVirtualStack) impAs[0].getImageStack();
				if (stackAs[0].getWidth()==2048 && stackAs[0].getHeight()==512){
					int wasC = impAs[0].getNChannels();
					int wasZ = impAs[0].getNSlices();
					int wasT = impAs[0].getNFrames();
					stackAs[0].setDimOrder("xySplitCzt");
					impAs[0].setStack(stackAs[0],2*wasC, wasZ, wasT);
					wavelengths = impAs[0].getNChannels();
					cDim = impAs[0].getNChannels();
					zDim = impAs[0].getNSlices();
					tDim = impAs[0].getNFrames();
					vDim = 2;
					impAs[0].getCalibration().setUnit("micron");
					impAs[0].getCalibration().pixelWidth = 0.1625;
					impAs[0].getCalibration().pixelHeight = 0.1625;
					impAs[0].getCalibration().pixelDepth = 1;
				}
				stackBs[0] = (MultiFileInfoVirtualStack) impBs[0].getImageStack();
				if (stackBs[0].getWidth()==2048 && stackBs[0].getHeight()==512){
					int wasC = impBs[0].getNChannels();
					int wasZ = impBs[0].getNSlices();
					int wasT = impBs[0].getNFrames();
					stackBs[0].setDimOrder("xySplitCzt");
					impBs[0].setStack(stackBs[0],2*wasC, wasZ, wasT);
					impBs[0].getCalibration().setUnit("micron");
					impBs[0].getCalibration().pixelWidth = 0.1625;
					impBs[0].getCalibration().pixelHeight = 0.1625;
					impBs[0].getCalibration().pixelDepth = 1;
				}

				
			}else if (dirOrOMETiffFile.isDirectory()) {
				if (omeTiffs) {
					fileListA = new File("" + dirOrOMETiff).list();

					if (keyString =="")
						keyString = dirOrOMETiffFile.list()[dirOrOMETiffFile.list().length/2].split("_")[0];

					//Reading in diSPIM header from MM tiffs vvvvvv

					readInMMdiSPIMheader(dirOrOMETiffFile);

					//Reading in diSPIM header from MM tiffs ^^^^^^

					if (dimOrder == null || dimOrder == "")
						dimOrder = (diSPIM_MM_channelMode!=null && diSPIM_MM_channelMode.contains("VOLUME")?"xyzct":"xyczt");

					if (!dimOrder.toLowerCase().matches(".*split.*c.*"))
						wavelengths = cDim/vDim; 
					else
						wavelengths = cDim;
					vWidth = diSPIM_MM_PixelSize_um;
					vHeight = diSPIM_MM_PixelSize_um;
					vDepthRaw = diSPIM_MM_zStep_um;

					impAs = new ImagePlus[pDim];
					impBs = new ImagePlus[pDim];
					origRoiAs = new Roi[pDim];
					origRoiBs = new Roi[pDim];
					ellipseRoiAs = new Roi[pDim];
					ellipseRoiBs = new Roi[pDim];
					rectRoiAs = new Roi[pDim];
					rectRoiBs = new Roi[pDim];
					doProcessing= new boolean[pDim];
					impDF1s  = new ImagePlus[pDim];
					impDF2s  = new ImagePlus[pDim];
					impPrxs = new ImagePlus[pDim];
					impPrys = new ImagePlus[pDim];

					ciDFs  = new CompositeImage[pDim];
					ciPrxs  = new CompositeImage[pDim];
					ciPrys  = new CompositeImage[pDim];
//					impMaxXs = new ImagePlus[pDim];
//					ciMaxXs  = new CompositeImage[pDim];
//					impMaxYs = new ImagePlus[pDim];
//					ciMaxYs  = new CompositeImage[pDim];
//					impMaxZs = new ImagePlus[pDim];
//					ciMaxZs  = new CompositeImage[pDim];

					prjXs = new Projector16bit[pDim];
					prjYs = new Projector16bit[pDim];

					wasFrameA = new int[pDim];
					wasFrameB = new int[pDim];
					wasSliceA = new int[pDim];
					wasSliceB = new int[pDim];
					wasChannelA = new int[pDim];
					wasChannelB = new int[pDim];
					zFirstA = new int[pDim];
					zLastA = new int[pDim];
					zFirstB = new int[pDim];
					zLastB = new int[pDim];
					lastMatrix = new String[pDim];
					cropWidthA = new double[pDim];
					cropHeightA = new double[pDim];
					cropWidthB = new double[pDim];
					cropHeightB = new double[pDim];			
					anglesA = new double[pDim];					
					anglesB = new double[pDim];					
					longAxesA = new double[pDim];					
					longAxesB = new double[pDim];					
					rotXYZs = new double[pDim][3];
					
					dispimToolsButton = new JButton[pDim][2];
					dispimPreviewButton = new JButton[pDim][2];
					fuseButton = new JButton[pDim][2];
					splitButton = new JButton[pDim][2];
					spinnerPanel = new Panel[pDim][2];
					xSpinner = new JSpinner[pDim][2];
					ySpinner = new JSpinner[pDim][2];
					zSpinner = new JSpinner[pDim][2];

					wasEdgesA = new boolean[pDim];
					wasEdgesB = new boolean[pDim];

					stackAs = new MultiFileInfoVirtualStack[pDim];
					stackBs = new MultiFileInfoVirtualStack[pDim];
					
					File argFile = new File(dirOrOMETiff);
					String argFileName = argFile.getName();
					String titleRoot = (argFile.getName());

					ArrayList<String> argPeerDirArrayList = new ArrayList<String>();
					String[] peerDirList = new File(argFile.getParent()).list();
					for (String peerDirName:peerDirList) {
						String peerDir = argFile.getParent()+File.separator+peerDirName+File.separator;
						if (!peerDirName.equals(argFile.getName())){
							if ((peerDirName.startsWith(argFileName) ||  peerDirName.endsWith(argFileName)) ) {

								argPeerDirArrayList.add(dirOrOMETiff.replace("\\", "\\\\"));
								argPeerDirArrayList.add(peerDir.replace("\\", "\\\\"));
								titleRoot = (argFileName);

								break;
							} else if ((argFileName.startsWith(peerDirName)) || argFileName.endsWith(peerDirName)) {
								argPeerDirArrayList.add(peerDir.replace("\\", "\\\\"));
								argPeerDirArrayList.add(dirOrOMETiff.replace("\\", "\\\\"));
								titleRoot = (peerDirName);

								break;
							}
						}
					}
					if (argPeerDirArrayList.size()==0) {
						argPeerDirArrayList.add(dirOrOMETiff);
					}
					
					dirConcat = "";
					for (String argPeer:argPeerDirArrayList) {
						dirConcat = dirConcat + argPeer +"|";
					}
					String dirOrOMETiff0 = dirConcat.split("\\|")[0];
					IJ.log(dirConcat);
					
					for (int pos=0; pos<pDim; pos++) {
						boolean go = true;
						if (posIntArray!=null){
							go = false;
							for (int posInt:posIntArray){
								if (posInt == pos){
									go=true;
								}
							}
						}
						if (!go){
							continue;
						}
						impAs[pos] = new ImagePlus();
						impAs[pos].setTitle(titleRoot.replaceAll("(.*)Pos\\d+_\\d+.ome.tif", "$1.ome.tif") + "_Pos" +pos + ": SPIMA");
						impBs[pos] = new ImagePlus();
						impBs[pos].setTitle(titleRoot.replaceAll("(.*)Pos\\d+_\\d+.ome.tif", "$1.ome.tif") + "_Pos" +pos + ": SPIMB");

						impAs[pos].setFileInfo(new FileInfo());
						impAs[pos].getOriginalFileInfo().fileName = dirOrOMETiff0;
						impAs[pos].getOriginalFileInfo().directory = dirOrOMETiff0;

						impBs[pos].setFileInfo(new FileInfo());
						impBs[pos].getOriginalFileInfo().fileName = dirOrOMETiff0;
						impBs[pos].getOriginalFileInfo().directory = dirOrOMETiff0;

						tDim = Math.max(diSPIM_MM_Frames, diSPIM_MM_numTimepoints);
						if (cDim == 4 && wavelengths == 4 && splitChannels == true && dimOrder == "xySplitCzt") {
							cDim = 2;
							wavelengths = 2;
							splitChannels = true;
							dimOrder = "xySplitSequentialCzt";
						} 
						final int fPos = pos;

						stackAs[pos] = new MultiFileInfoVirtualStack(
								dirConcat, dimOrder, keyString, cDim*(diSPIM_MM_Channels/vDim>1 && diSPIM_MM_channelOrder == "RG"?-1:1), zDim, tDim, vDim, pos,
								false, false, true, false){
							
							@Override
							public void initiateStack(int stkNum, int slcNum){
								super.initiateStack( stkNum,  slcNum);
								if (stackBs[fPos]!=null){
									stackBs[fPos].infoCollectorArrayList.set(stkNum, Arrays.copyOf(((FileInfo[])infoCollectorArrayList.get(stkNum)),((FileInfo[])infoCollectorArrayList.get(stkNum)).length));
									stackBs[fPos].getFivStacks().get(stkNum).infoArray = Arrays.copyOf(((FileInfo[])infoCollectorArrayList.get(stkNum)),((FileInfo[])infoCollectorArrayList.get(stkNum)).length);
								}
							}
						};
						stackBs[pos] = new MultiFileInfoVirtualStack(
								dirConcat, dimOrder, keyString, cDim*(diSPIM_MM_Channels/vDim>1 && diSPIM_MM_channelOrder == "RG"?-1:1), zDim, tDim, vDim, pos,
								true, false, true, false){
							
							@Override
							public void initiateStack(int stkNum, int slcNum){
								super.initiateStack( stkNum,  slcNum);
								if (stackAs[fPos]!=null){
									stackAs[fPos].infoCollectorArrayList.set(stkNum, Arrays.copyOf(((FileInfo[])infoCollectorArrayList.get(stkNum)),((FileInfo[])infoCollectorArrayList.get(stkNum)).length));
									stackAs[fPos].getFivStacks().get(stkNum).infoArray = Arrays.copyOf(((FileInfo[])infoCollectorArrayList.get(stkNum)),((FileInfo[])infoCollectorArrayList.get(stkNum)).length);
								}
							}

						};

						if (stackAs[pos].getSize() == 0) {
							impAs[pos].flush();
							impAs[pos]=null;
							continue;
						}
						if (stackBs[pos].getSize() == 0) {
							impBs[pos].flush();
							impBs[pos]=null;
							continue;
						}
						tDim = ((MultiFileInfoVirtualStack) stackAs[pos]).tDim;
						impAs[pos].setStack(stackAs[pos]);
						Calibration calA = impAs[pos].getCalibration();
						calA.pixelWidth = vWidth;
						calA.pixelHeight = vHeight;
						calA.pixelDepth = vDepthRaw;
						calA.setUnit(vUnit);

						int sizeA = stackAs[pos].getSize();
						if (stageScan)
							stackAs[pos].setSkewXperZ(
									calA.pixelDepth / calA.pixelWidth);
						int shouldBeSizeA = (cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim) ) * zDim * (sizeA/((cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim))*zDim));

						for (int d=1;d<=sizeA-shouldBeSizeA;d++){
							stackAs[pos].deleteSlice(1);
						}

						IJ.log(""+ sizeA+" "+shouldBeSizeA+" "+stackAs[pos].getSize());
						
						impAs[pos].setOpenAsHyperStack(true);
						impAs[pos].setDimensions(cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim), zDim, sizeA/((cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim))*zDim));

						impAs[pos] = new CompositeImage(impAs[pos]);
//						{
//							@Override
//							public synchronized void flush(){
//								ObjectOutputStream oos;
//								try {
//									oos = new ObjectOutputStream(new FileOutputStream(new RandomAccessFile(stackAs[fPos].infoDir+"touchedFileFIs"+fPos+"A.inf","rw").getFD()));
//									oos.writeObject(((MultiFileInfoVirtualStack) getImageStack()).infoCollectorArrayList);
//									oos.close();
//								} catch (IOException e) {
//									// TODO Auto-generated catch block
//									e.printStackTrace();
//								}
//								super.flush();
//							}
//						};
						while (!impAs[pos].isComposite()) {
							IJ.wait(100);
						}
						((CompositeImage)impAs[pos]).setMode(CompositeImage.COMPOSITE);


						impBs[pos].setStack(stackBs[pos]);
						Calibration calB = impBs[pos].getCalibration();
						calB.pixelWidth = vWidth;
						calB.pixelHeight = vHeight;
						calB.pixelDepth = vDepthRaw;
						calB.setUnit(vUnit);
						
						int sizeB = stackBs[pos].getSize();

						int shouldBeSizeB = (cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim) ) * zDim * (sizeB/((cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim))*zDim));

						for (int d=1;d<=sizeB-shouldBeSizeB;d++){
							stackBs[pos].deleteSlice(1);
						}

						IJ.log(""+ sizeB+" "+shouldBeSizeB+" "+stackBs[pos].getSize());
						

						if (stageScan)
							stackBs[pos].setSkewXperZ(
									-calB.pixelDepth / calB.pixelWidth);
						impBs[pos].setOpenAsHyperStack(true);
						impBs[pos].setDimensions(cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim), zDim, sizeB/((cDim/(dimOrder.matches("xySplit.*Czt")?1:vDim))*zDim));

						impBs[pos] = new CompositeImage(impBs[pos]);
//						{
//							@Override
//							public synchronized void flush(){
//								ObjectOutputStream oos;
//								try {
//									oos = new ObjectOutputStream(new FileOutputStream(new RandomAccessFile(stackBs[fPos].infoDir+"touchedFileFIs"+fPos+"B.inf","rw").getFD()));
//									oos.writeObject(((MultiFileInfoVirtualStack) getImageStack()).infoCollectorArrayList);
//									oos.close();
//								} catch (IOException e) {
//									// TODO Auto-generated catch block
//									e.printStackTrace();
//								}
//								super.flush();
//							}
//						};
						while (!impBs[pos].isComposite()) {
							IJ.wait(100);
						}
						((CompositeImage)impBs[pos]).setMode(CompositeImage.COMPOSITE);

						impAs[pos].setPosition(1, zDim/2, impAs[pos].getNFrames()-1);
						impBs[pos].setPosition(1, zDim/2, impBs[pos].getNFrames()-1);
						wasFrameA[pos] = impAs[pos].getFrame();
						wasFrameB[pos] = impBs[pos].getFrame();
						wasSliceA[pos] = impAs[pos].getSlice();
						wasSliceB[pos] = impBs[pos].getSlice();
						wasChannelA[pos] = impAs[pos].getChannel();
						wasChannelB[pos] = impBs[pos].getChannel();
						wasEdgesA[pos] = impAs[pos].getStack().isEdges();
						wasEdgesB[pos] = impBs[pos].getStack().isEdges();

						impAs[pos].show();
						impBs[pos].show();
					}
				} else {
					fileListA = new File("" + dirOrOMETiff + "SPIMA").list();
					fileListB = new File("" + dirOrOMETiff + "SPIMB").list();
					fileRanksA = Arrays.copyOf(fileListA, fileListA.length);
					fileRanksB = Arrays.copyOf(fileListB, fileListB.length);
					fileNumsA = Arrays.copyOf(fileListA, fileListA.length);
					fileNumsB = Arrays.copyOf(fileListB, fileListB.length);
					fileSortA = Arrays.copyOf(fileListA, fileListA.length);
					fileSortB = Arrays.copyOf(fileListB, fileListB.length);

					for (int a = 0; a < fileListA.length; a++) {
						if (!fileListA[a].endsWith(".roi")
								&& !fileListA[a].endsWith(".DS_Store")) {
							String sring = fileListA[a].replace("/", "");
							String subsring = sring;
							String prefix = "";
							double n = Double.NaN;
							try {
								n = Integer.parseInt(subsring);
							} catch (NumberFormatException e) {
								n = Double.NaN;
							}
							while (Double.isNaN(n)) {
								try {
									prefix = prefix + subsring.substring(0, 1);
									subsring = subsring.substring(1);
									n = Integer.parseInt(subsring.split(" ")[0]);
									IJ.log(subsring);
									IJ.log(prefix);
								} catch (NumberFormatException ne) {
									n = Double.NaN;
								} catch (StringIndexOutOfBoundsException se) {
									n = Double.NaN;
								}
							}
							if (prefix.toLowerCase().startsWith("t")
									|| prefix.toLowerCase().startsWith("f"))
								prefix = "aaaaa" + prefix;
							int numer = Integer.parseInt(subsring.split(" ")[0]);
							IJ.log(subsring + " " + numer);
							fileNumsA[a] = prefix + IJ.pad(numer, 6) + "|" + sring;
							fileNumsB[a] = prefix + IJ.pad(numer, 6) + "|" + sring;
						} else {
							fileNumsA[a] = "";
							fileNumsB[a] = "";
						}

					}
					Arrays.sort(fileNumsA);
					Arrays.sort(fileNumsB);

					for (int r = 0; r < fileNumsA.length; r++) {
						String[] splt = fileNumsA[r].split("\\|");
						if (splt.length > 1)
							fileSortA[r] = splt[1];
						else
							fileSortA[r] = "";
						IJ.log(r + " " + " " + fileNumsA[r] + " " + fileSortA[r]);
						splt = fileNumsB[r].split("\\|");
						if (splt.length > 1)
							fileSortB[r] = splt[1];
						else
							fileSortB[r] = "";

					}

					for (int d = 0; d < fileSortA.length; d++) {
						boolean skipIt = false;
						String nextPathA = dirOrOMETiff + "SPIMA" + File.separator
								+ fileSortA[d];
						String nextPathB = dirOrOMETiff + "SPIMB" + File.separator
								+ fileSortB[d];
						IJ.log(nextPathA);
						IJ.log(nextPathB);
						if ((new File(nextPathA)).isDirectory()
								&& (new File(nextPathB)).isDirectory()) {
							newTifListA = (new File(nextPathA)).list();
							newTifListB = (new File(nextPathB)).list();
							if (newTifListA.length != newTifListB.length
									|| newTifListA.length < wavelengths * zSlices)
								skipIt = true;
							if (!skipIt) {
								Arrays.sort(newTifListA);
								for (int f = 0; f < newTifListA.length; f++) {
									while (!(new File(dirOrOMETiff
											+ "Big5DFileListA.txt")).exists())
										IJ.wait(100);
									if (!newTifListA[f].endsWith(".roi")
											&& !newTifListA[f]
													.endsWith(".DS_Store")
													&& big5DFileListAString
													.indexOf(nextPathA
															+ File.separator
															+ newTifListA[f]) < 0)
										IJ.append(nextPathA + File.separator
												+ newTifListA[f], dirOrOMETiff
												+ "Big5DFileListA.txt");
								}
								Arrays.sort(newTifListB);
								for (int f = 0; f < newTifListB.length; f++) {
									while (!(new File(dirOrOMETiff
											+ "Big5DFileListB.txt")).exists())
										IJ.wait(100);
									if (!newTifListB[f].endsWith(".roi")
											&& !newTifListB[f]
													.endsWith(".DS_Store")
													&& big5DFileListBString
													.indexOf(nextPathB
															+ File.separator
															+ newTifListB[f]) < 0)
										IJ.append(nextPathB + File.separator
												+ newTifListB[f], dirOrOMETiff
												+ "Big5DFileListB.txt");
								}
							}
						}

					}

					IJ.log("" + WindowManager.getImageCount());

					if ((new File(dirOrOMETiff + "Big5DFileListA.txt")).length() > 0) {
						// IJ.run("Stack From List...",
						// "open="+dir+"Big5DFileListA.txt use");
						//					impA = impAs[0];
						impAs[0].setStack(new ListVirtualStack(dirOrOMETiff
								+ "Big5DFileListA.txt"));

						int stkNSlices = impAs[0].getNSlices();

						impAs[0].setTitle("SPIMA: " + dirOrOMETiff);

						impAs[0].setDimensions(wavelengths, zSlices, stkNSlices
								/ (wavelengths * zSlices));
						IJ.log(wavelengths + " " + zSlices + " " + stkNSlices
								/ (wavelengths * zSlices));
						if (wavelengths > 1) {
							impAs[0] = new CompositeImage(impAs[0]);
							while (!impAs[0].isComposite()) {
								IJ.wait(100);
							}
						}
						Calibration cal = impAs[0].getCalibration();
						cal.pixelWidth = vWidth;
						cal.pixelHeight = vHeight;
						cal.pixelDepth = vDepthRaw;
						cal.setUnit(vUnit);
						if (stageScan)
							impAs[0].getStack().setSkewXperZ(
									-cal.pixelDepth / cal.pixelWidth);

						impAs[0].setPosition(wavelengths, zSlices / 2, stkNSlices
								/ (wavelengths * zSlices));

						impAs[0].setPosition(1, zSlices / 2, stkNSlices
								/ (wavelengths * zSlices));

						if (impAs[0].isComposite())
							((CompositeImage) impAs[0])
							.setMode(CompositeImage.COMPOSITE);
						impAs[0].show();

					}

					if ((new File(dirOrOMETiff + "Big5DFileListB.txt")).length() > 0) {
						// IJ.run("Stack From List...",
						// "open="+dir+"Big5DFileListB.txt use");
						//					impB = impBs[0];
						impBs[0].setStack(new ListVirtualStack(dirOrOMETiff
								+ "Big5DFileListB.txt"));
						int stkNSlices = impBs[0].getNSlices();

						impBs[0].setTitle("SPIMB: " + dirOrOMETiff);

						impBs[0].setDimensions(wavelengths, zSlices, stkNSlices
								/ (wavelengths * zSlices));
						IJ.log(wavelengths + " " + zSlices + " " + stkNSlices
								/ (wavelengths * zSlices));
						if (wavelengths > 1) {
							impBs[0] = new CompositeImage(impBs[0]);
							while (!impBs[0].isComposite()) {
								IJ.wait(100);
							}
						}
						Calibration cal = impBs[0].getCalibration();
						cal.pixelWidth = vWidth;
						cal.pixelHeight = vHeight;
						cal.pixelDepth = vDepthRaw;
						cal.setUnit(vUnit);
						if (stageScan)
							impBs[0].getStack().setSkewXperZ(
									-cal.pixelDepth / cal.pixelWidth);

						impBs[0].setPosition(wavelengths, zSlices / 2, stkNSlices
								/ (wavelengths * zSlices));

						impBs[0].setPosition(1, zSlices / 2, stkNSlices
								/ (wavelengths * zSlices));

						if (impBs[0].isComposite())
							((CompositeImage) impBs[0])
							.setMode(CompositeImage.COMPOSITE);
						impBs[0].show();

					}
				}
			} else if (this.dirOrOMETiff.endsWith(".ome.tif")) {
				readInMMdiSPIMheader(dirOrOMETiffFile.getParentFile());

				TiffDecoder tdA = new TiffDecoder("",dirOrOMETiff);
				TiffDecoder tdB = new TiffDecoder("",dirOrOMETiff);

				String mmPath = (new File(dirOrOMETiff)).getParent();

				if (dimOrder == null || dimOrder == "")
					dimOrder = ((diSPIM_MM_channelMode!=null && diSPIM_MM_channelMode.contains("VOLUME"))?"xyzct":"xyczt");

				if (!dimOrder.toLowerCase().matches(".*split.*c.*"))
					wavelengths = cDim/vDim; 
				else
					wavelengths = cDim;
				vWidth = diSPIM_MM_PixelSize_um;
				vHeight = diSPIM_MM_PixelSize_um;
				vDepthRaw = diSPIM_MM_zStep_um;

				impAs = new ImagePlus[pDim];
				impBs = new ImagePlus[pDim];
				origRoiAs = new Roi[pDim];
				origRoiBs = new Roi[pDim];
				ellipseRoiAs = new Roi[pDim];
				ellipseRoiBs = new Roi[pDim];
				rectRoiAs = new Roi[pDim];
				rectRoiBs = new Roi[pDim];
				doProcessing= new boolean[pDim];
				impDF1s  = new ImagePlus[pDim];
				impDF2s  = new ImagePlus[pDim];
				impPrxs = new ImagePlus[pDim];
				impPrys = new ImagePlus[pDim];

				ciDFs  = new CompositeImage[pDim];
				ciPrxs  = new CompositeImage[pDim];
				ciPrys  = new CompositeImage[pDim];
//				impMaxXs = new ImagePlus[pDim];
//				ciMaxXs  = new CompositeImage[pDim];
//				impMaxYs = new ImagePlus[pDim];
//				ciMaxYs  = new CompositeImage[pDim];
//				impMaxZs = new ImagePlus[pDim];
//				ciMaxZs  = new CompositeImage[pDim];

				prjXs = new Projector16bit[pDim];
				prjYs = new Projector16bit[pDim];

				wasFrameA = new int[pDim];
				wasFrameB = new int[pDim];
				wasSliceA = new int[pDim];
				wasSliceB = new int[pDim];
				wasChannelA = new int[pDim];
				wasChannelB = new int[pDim];
				zFirstA = new int[pDim];
				zLastA = new int[pDim];
				zFirstB = new int[pDim];
				zLastB = new int[pDim];
				lastMatrix = new String[pDim];
				cropWidthA = new double[pDim];
				cropHeightA = new double[pDim];
				cropWidthB = new double[pDim];
				cropHeightB = new double[pDim];			
				anglesA = new double[pDim];					
				anglesB = new double[pDim];					
				longAxesA = new double[pDim];					
				longAxesB = new double[pDim];					
				rotXYZs = new double[pDim][3];
				
				dispimToolsButton = new JButton[pDim][2];
				dispimPreviewButton = new JButton[pDim][2];
				fuseButton = new JButton[pDim][2];
				splitButton = new JButton[pDim][2];
				spinnerPanel = new Panel[pDim][2];
				xSpinner = new JSpinner[pDim][2];
				ySpinner = new JSpinner[pDim][2];
				zSpinner = new JSpinner[pDim][2];

				wasEdgesA = new boolean[pDim];
				wasEdgesB = new boolean[pDim];

				stackAs = new MultiFileInfoVirtualStack[pDim];
				stackBs = new MultiFileInfoVirtualStack[pDim];

				for (int pos=0; pos<pDim; pos++) {
					boolean go = true;
					if (posIntArray!=null){
						go = false;
						for (int posInt:posIntArray){
							if (posInt == pos){
								go=true;
							}
						}
					}
					if (!go){
						continue;
					}

					String[] mmList = (new File(mmPath)).list();
					boolean noFilesForPos = true;
					for (String mmListFileName:mmList) {
						if(mmListFileName.toLowerCase().matches(".*mmstack_pos"+pos+".*.ome.tif")) {
							noFilesForPos = false;
						}
					}
					if (noFilesForPos) {
						continue;
					}
					impAs[pos] = new ImagePlus();
					impAs[pos].setTitle(dirOrOMETiffFile.getName().replaceAll("(.*)Pos\\d+_\\d+.ome.tif", "$1.ome.tif")  + "_Pos" +pos + ": SPIMA");
					impBs[pos] = new ImagePlus();
					impBs[pos].setTitle(dirOrOMETiffFile.getName().replaceAll("(.*)Pos\\d+_\\d+.ome.tif", "$1.ome.tif")  + "_Pos" +pos + ": SPIMB");

					impAs[pos].setFileInfo(new FileInfo());
					impAs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
					impAs[pos].getOriginalFileInfo().directory = dirOrOMETiff;

					impBs[pos].setFileInfo(new FileInfo());
					impBs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
					impBs[pos].getOriginalFileInfo().directory = dirOrOMETiff;

					impAs[pos].setStack(new MultiFileInfoVirtualStack(mmPath, dimOrder, "MMStack_Pos"+pos, cDim, zDim, tDim, vDim, pos, false, false, true, true));

					int stackSizeA = impAs[pos].getImageStackSize();
//					int nChannels = cDim/(dimOrder=="xySplitCzt"?1:vDim);
					int nChannelsA = ((MultiFileInfoVirtualStack)impAs[pos].getStack()).cDim;
					int nSlicesA = ((MultiFileInfoVirtualStack)impAs[pos].getStack()).zDim;
					int nFramesA = ((MultiFileInfoVirtualStack)impAs[pos].getStack()).tDim;
					//					dirOrOMETiff = ((MultiFileInfoVirtualStack)impAs[pos].getStack()).getFivStacks().get(0).getInfo()[pos].directory +
					//							File.separator +
					//							((MultiFileInfoVirtualStack)impAs[pos].getStack()).getFivStacks().get(0).getInfo()[pos].fileName;


					if (nChannelsA*nSlicesA*nFramesA!=stackSizeA) {
						if (nChannelsA*nSlicesA*nFramesA>stackSizeA) {
							for (int a=stackSizeA;a<nChannelsA*nSlicesA*nFramesA;a++) {
								if (impAs[pos].getStack().isVirtual())
									((VirtualStack)impAs[pos].getStack()).addSlice("blank slice");
								else
									impAs[pos].getStack().addSlice(impAs[pos].getProcessor().createProcessor(impAs[pos].getWidth(), impAs[pos].getHeight()));
							}
						} else if (nChannelsA*nSlicesA*nFramesA<stackSizeA) {
							for (int a=nChannelsA*nSlicesA*nFramesA;a<stackSizeA;a++) {
								((MultiFileInfoVirtualStack)impAs[pos].getStack()).deleteSlice(1);
								stackSizeA--;
							}
						}else {
							IJ.error("HyperStack Converter", "channels x slices x frames <> stack size");
							return;
						}
					}


					impAs[pos].setStack(impAs[pos].getImageStack());
					impAs[pos].setOpenAsHyperStack(true);
					impAs[pos].setDimensions(nChannelsA, nSlicesA, nFramesA);

					if (nChannelsA > 1){

						impAs[pos] = new CompositeImage(impAs[pos]);
						while (!impAs[pos].isComposite()) {
							IJ.wait(100);
						}
					}
					if (impAs[pos].isComposite())
						((CompositeImage)impAs[pos]).setMode(CompositeImage.COMPOSITE);

					Calibration cal = impAs[pos].getCalibration();
					cal.pixelWidth = vWidth;
					cal.pixelHeight = vHeight;
					cal.pixelDepth = vDepthRaw;
					cal.setUnit(vUnit);

					impAs[pos].setPosition(wavelengths, nSlicesA, nFramesA);	

					impAs[pos].setPosition(1, nSlicesA/2, nFramesA/2);	

					impAs[pos].setFileInfo(new FileInfo());
					impAs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
					impAs[pos].getOriginalFileInfo().directory = dirOrOMETiff;
					impAs[pos].show();


					impBs[pos].setStack(new MultiFileInfoVirtualStack(mmPath, dimOrder, "MMStack_Pos"+pos, cDim, zDim, tDim, vDim, pos, true, false, true, true));

					int stackSizeB = impBs[pos].getImageStackSize();
					int nChannelsB = ((MultiFileInfoVirtualStack)impBs[pos].getStack()).cDim;
					int nSlicesB = ((MultiFileInfoVirtualStack)impBs[pos].getStack()).zDim;
					int nFramesB = ((MultiFileInfoVirtualStack)impBs[pos].getStack()).tDim;
					//					dirOrOMETiff = ((MultiFileInfoVirtualStack)impBs[pos].getStack()).getFivStacks().get(0).getInfo()[pos].directory +
					//							File.separator +
					//							((MultiFileInfoVirtualStack)impBs[pos].getStack()).getFivStacks().get(0).getInfo()[pos].fileName;


					if (nChannelsB*nSlicesB*nFramesB!=stackSizeB) {
						if (nChannelsB*nSlicesB*nFramesB>stackSizeB) {
							for (int a=stackSizeB;a<nChannelsB*nSlicesB*nFramesB;a++) {
								if (impBs[pos].getStack().isVirtual())
									((VirtualStack)impBs[pos].getStack()).addSlice("blank slice");
								else
									impBs[pos].getStack().addSlice(impBs[pos].getProcessor().createProcessor(impBs[pos].getWidth(), impBs[pos].getHeight()));
							}
						} else if (nChannelsB*nSlicesB*nFramesB<stackSizeB) {
							for (int a=nChannelsB*nSlicesB*nFramesB;a<stackSizeB;a++) {
								((MultiFileInfoVirtualStack)impBs[pos].getStack()).deleteSlice(1);
								stackSizeB--;
							}
						}else {
							IJ.error("HyperStack Converter", "channels x slices x frames <> stack size");
							return;
						}
					}


					impBs[pos].setStack(impBs[pos].getImageStack());
					impBs[pos].setOpenAsHyperStack(true);
					impBs[pos].setDimensions(nChannelsB, nSlicesB, nFramesB);

					if (nChannelsB > 1){

						impBs[pos] = new CompositeImage(impBs[pos]);
						while (!impBs[pos].isComposite()) {
							IJ.wait(100);
						}
					}
					if (impBs[pos].isComposite())
						((CompositeImage)impBs[pos]).setMode(CompositeImage.COMPOSITE);

					cal = impBs[pos].getCalibration();
					cal.pixelWidth = vWidth;
					cal.pixelHeight = vHeight;
					cal.pixelDepth = vDepthRaw;
					cal.setUnit(vUnit);

					impBs[pos].setPosition(wavelengths, nSlicesA, nFramesA);	

					impBs[pos].setPosition(1, nSlicesA/2, nFramesA/2);	

					impBs[pos].setFileInfo(new FileInfo());
					impBs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
					impBs[pos].getOriginalFileInfo().directory = dirOrOMETiff;
					impBs[pos].show();
				}
			} else if (dirOrOMETiff.matches(".*_\\d{9}_\\d{3}_.*.tif")) {
				listB = new File(dirOrOMETiff).getParentFile().list();
				int newLength = 0;
				for (String newFileListItem : listB)
					if (newFileListItem.endsWith(".tif"))
						newLength++;

				wavelengths = 2;  //OLD DEFAULT VALUES OFTEN USED.  NEED TO TREAT SMARTLY SOON
				zSlices = 50;
				while (Math.floor(newLength / (wavelengths * 2 * zSlices)) == 0) {

					IJ.wait(10);
					listB = new File(dirOrOMETiff).getParentFile().list();
					newLength = 0;
					for (String newFileListItem : listB)
						if (newFileListItem.endsWith(".tif"))
							newLength++;
				}
				IJ.run("Image Sequence...",
						"open=["
								+ dirOrOMETiff
								+ "] number="
								+ newLength
								+ " starting=1 increment=1 scale=100 file=Cam2 or=[] sort use");
				IJ.run("Stack to Hyperstack...",
						"order=xyczt(default) channels="
								+ wavelengths
								+ " slices="
								+ zSlices
								+ " frames="
								+ (Math.floor(newLength
										/ (wavelengths * 2 * zSlices)))
										+ " display=Composite");
				// IJ.getImage().setTitle("SPIMA: "+IJ.getImage().getTitle());
				/*impA = */ impAs[0] = WindowManager.getCurrentImage();
				Calibration calA = impAs[0].getCalibration();
				calA.pixelWidth = vWidth;
				calA.pixelHeight = vHeight;
				calA.pixelDepth = vDepthRaw;
				calA.setUnit(vUnit);
				if (stageScan)
					impAs[0].getStack()
					.setSkewXperZ(-calA.pixelDepth / calA.pixelWidth);
				impAs[0].setTitle("SPIMA: " + impAs[0].getTitle());

				IJ.run("Image Sequence...",
						"open=["
								+ dirOrOMETiff
								+ "] number="
								+ newLength
								+ " starting=1 increment=1 scale=100 file=Cam1 or=[] sort use");
				IJ.run("Stack to Hyperstack...",
						"order=xyczt(default) channels="
								+ wavelengths
								+ " slices="
								+ zSlices
								+ " frames="
								+ (Math.floor(newLength
										/ (wavelengths * 2 * zSlices)))
										+ " display=Composite");
				// IJ.getImage().setTitle("SPIMB: "+IJ.getImage().getTitle());
				/*impB = */ impBs[0] = WindowManager.getCurrentImage();
				Calibration calB = impBs[0].getCalibration();
				calB.pixelWidth = vWidth;
				calB.pixelHeight = vHeight;
				calB.pixelDepth = vDepthRaw;
				calB.setUnit(vUnit);
				if (stageScan)
					impBs[0].getStack().setSkewXperZ(calB.pixelDepth / calB.pixelWidth);
				impBs[0].setTitle("SPIMB: " + impBs[0].getTitle());

				oldLength = newLength;
				dispimToolsButton = new JButton[pDim][2];
				dispimPreviewButton = new JButton[pDim][2];
				fuseButton = new JButton[pDim][2];
				splitButton = new JButton[pDim][2];
				spinnerPanel = new Panel[pDim][2];
				xSpinner = new JSpinner[pDim][2];
				ySpinner = new JSpinner[pDim][2];
				zSpinner = new JSpinner[pDim][2];

			}

			IJ.run("Tile");
			IJ.log("" + WindowManager.getImageCount());

			//NEED TO FIGURE OUT FOR DECON LAUNNCH FROM NEW BUTTON ON WINDOW!
			autoUpload = IJ.showMessageWithCancel("AutoUpload Data to WormGUIDES Server?", "Click to have CytoSHOW backup these data to the WormGUIDES server.");

			if (autoUpload){
				Thread uploadThread = new Thread(new Runnable() {
					public void run() {
						if (wgUploadJob == null) 
							wgUploadJob = new WG_Uploader();
						if (wgUploadJob.getNewUploadProcess() != null)
							uploadRunning = wgUploadJob.getNewUploadProcess().isAlive();
						if(!uploadRunning)	{
							wgUploadJob = new WG_Uploader();
							wgUploadJob.run(dirConcat);
						}
					}
				});
				uploadThread.start();
			}
		} else {

			SelectKeyChannelDialog d = new SelectKeyChannelDialog(
					IJ.getInstance(),
					"Deconvolve/Fuse diSPIM Raw Data Volumes ",
					"New raw data will be deconvolved/fused \nas soon as they are captured.");
			// d.setVisible(true);
			if (d.cancelPressed()) {
				doMipavDecon = false;
				doGPUdecon = false;
			} else if (d.yesPressed()) {
				doMipavDecon = d.getRegDeconMethod() == "mipav CPU method";
				doGPUdecon = d.getRegDeconMethod() == "MinGuo GPU method";
				doSerialRegPriming = d.getMatPrimMethod() == "Prime registration with previous matrix";
				doPrimeFromDefaultRegTMX = d.getMatPrimMethod() == "Prime registration with default matrix";
				doForceDefaultRegTMX = d.getMatPrimMethod() == "Force registration with default matrix";
				if (doGPUdecon) {
					iterations = d.getIterations();
					sliceTresholdVsModeA = d.getSubFractA();
					sliceTresholdVsModeB = d.getSubFractB();

				}
				keyChannel = d.getKeyChannel();
				keyView = d.getRegDeconView();
				abRelOriValue = d.getAbRelOriValue();
				autodepth = d.isAutodepth();
				lineageDecons = d.isLineage();
				orientBeforeLineage = d.isOrientationPreview();
				slaveChannel = keyChannel == 1 ? 2 : 1;
				OpenDialog.setDefaultDirectory(Prefs.get("diSPIMmonitor.output", savePath));
				String savePathParent = IJ.getDirectory("Select where to make Output Folder");
				String prefix = "Decon"+(orientBeforeLineage?"Preview":"")+"_";
				if (savePathParent.contains(dirOrOMETiff)) { 
					String shortName = (new File(dirOrOMETiff)).getName().replaceAll("_", "").replaceAll("&", "AND");
					savePath = (new File(dirOrOMETiff)).getParent() + File.separator+prefix+shortName.substring(0, shortName.length()<90?shortName.length():85).replaceAll("_", "").replaceAll("&", "AND")+"..._Output"+ File.separator;
				} else {
					savePath = savePathParent + File.separator +prefix+ (new File(dirOrOMETiff)).getName().replaceAll("_", "").replaceAll("&", "AND") +"..._Output"+ File.separator;
				}
				
				new File(savePath).mkdirs();
				Prefs.set("diSPIMmonitor.output", new File(savePath).getParent());

				if (lineageDecons) {
					OpenDialog.setDefaultDirectory(Prefs.get("StarryNiteFeeder.parameterFileDirectory",""));
					OpenDialog.setLastName(Prefs.get("StarryNiteFeeder.parameterFileName",""));
					paramsPath = new OpenDialog("Parameter file for StarryNite?", OpenDialog.getDefaultDirectory(), OpenDialog.getLastName()).getPath();
					Prefs.set("StarryNiteFeeder.parameterFileDirectory", new File(paramsPath).getParent()+File.separator);
					Prefs.set("StarryNiteFeeder.parameterFileName", new File(paramsPath).getName());
				}

			} else {
				doMipavDecon = false;
				doGPUdecon = false;
			}
		}

		if (doMipavDecon || doGPUdecon) {
			outputWavelengths = wavelengths >1?2:1;
			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				} 

				doProcessing[pos] = true;


				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				} 

				origRoiAs[pos] = impAs[pos].getRoi();
				origRoiBs[pos] = impBs[pos].getRoi();
				String[] savePathList = new File(savePath).list();
				
				while (doProcessing[pos] && (rectRoiAs[pos] == null || rectRoiBs[pos] == null)) {
					origRoiAs[pos] = impAs[pos].getRoi();
					origRoiBs[pos] = impBs[pos].getRoi();

					WindowManager.setTempCurrentImage(impAs[pos]);
					if (origRoiAs[pos] == null) {
						
						origRoiAs[pos] = new Roi(0, 0, cropWidthDefault, cropHeightDefault);								 

						String matchString="blah";
						for (String mightBe:savePathList) {
							if (mightBe.matches(".*Pos" + pos + "A_(\\d+-\\d+)*original.roi"))
								matchString=mightBe;
						}
						if (!((new File(savePath +  matchString)).canRead())) {
							for (String mightBe:savePathList) {
								if (mightBe.matches(".*Pos" + pos + "A_(\\d+-\\d+)*crop.roi"))
									matchString=mightBe;
							}
						} 
						if ((new File(savePath +  matchString)).canRead()) {
							IJ.open(savePath +  matchString);
							origRoiAs[pos] = impAs[pos].getRoi();
							if (matchString.matches(".*Pos" + pos + "A_(\\d+-\\d+)original.roi")) {
								String matchZSpan = matchString.replaceAll(".*Pos" + pos + "A_(\\d+-\\d+)original.roi", "$1");
								String[] matchEnds = matchZSpan.split("-");
								zFirstA[pos] = Integer.parseInt(matchEnds[0]);
								zLastA[pos] = Integer.parseInt(matchEnds[1]);
							}
							cropWidthA[pos] = origRoiAs[pos].getBounds().width;
							cropHeightA[pos] = origRoiAs[pos].getBounds().height;
						} else{
							impAs[pos].setRoi(origRoiAs[pos]);
						}
							
					}
					
					rectRoiAs[pos] = new Roi(origRoiAs[pos].getBounds());

					WindowManager.setTempCurrentImage(impBs[pos]);
					if (origRoiBs[pos] == null) {
						
						origRoiBs[pos] = new Roi(0, 0, cropWidthDefault, cropHeightDefault);								 

						String matchString="blah";
						for (String mightBe:savePathList) {
							if (mightBe.matches(".*Pos" + pos + "B_(\\d+-\\d+)*original.roi"))
								matchString=mightBe;
						}
						if (!((new File(savePath +  matchString)).canRead())) {
							for (String mightBe:savePathList) {
								if (mightBe.matches(".*Pos" + pos + "B_(\\d+-\\d+)*crop.roi"))
									matchString=mightBe;
							}
						} 
						if ((new File(savePath +  matchString)).canRead()) {
							IJ.open(savePath +  matchString);
							origRoiBs[pos] = impBs[pos].getRoi();
							if (matchString.matches(".*Pos" + pos + "B_(\\d+-\\d+)original.roi")) {
								String matchZSpan = matchString.replaceAll(".*Pos" + pos + "B_(\\d+-\\d+)original.roi", "$1");
								String[] matchEnds = matchZSpan.split("-");
								zFirstB[pos] = Integer.parseInt(matchEnds[0]);
								zLastB[pos] = Integer.parseInt(matchEnds[1]);
							}
							cropWidthB[pos] = origRoiBs[pos].getBounds().width;
							cropHeightB[pos] = origRoiBs[pos].getBounds().height;
						} else{
							impBs[pos].setRoi(origRoiBs[pos]);
						}
							
					}
					
					rectRoiBs[pos] = new Roi(origRoiBs[pos].getBounds());

					WindowManager.setTempCurrentImage(null);
				}
			}

			IJ.runMacro("waitForUser(\"Select the regions containing the specimens"
					+ "\\\n for deconvolution/fusion processing."
					// + "\\\nAlso, set the minimum Brightness limit."
					+
					"\\\nWhen you are then ready, click OK here to commence processing."
					+ "\");");

			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				} 


				origRoiAs[pos] = impAs[pos].getRoi();
				origRoiBs[pos] = impBs[pos].getRoi();

				if (origRoiAs[pos] == null) {

				} else if (	(origRoiAs[pos].getType() != Roi.RECTANGLE
								/*&& origRoiAs[pos].getBounds().height > cropHeightA[pos]*/)
								
					|| (origRoiAs[pos].getType() == Roi.RECTANGLE 
								/*&& origRoiAs[pos].getBounds().getHeight() > cropHeightA[pos]*/)
								
					|| (origRoiAs[pos].getType() == Roi.RECTANGLE 
								/*&& origRoiAs[pos].getBounds().getWidth() > cropWidthA[pos]*/)) {
											
					IJ.saveAs(impAs[pos], "Selection", savePath +  "Pos" + pos + "A_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"original.roi");

					int[] xApoints = origRoiAs[pos].getPolygon().xpoints;
					int[] yApoints = origRoiAs[pos].getPolygon().ypoints;
					int npoints = xApoints.length;

					double angle =0;
					if (origRoiAs[pos].getType() > Roi.OVAL) {

						if (npoints == 4) {
							double angleZero = new Line(xApoints[0], yApoints[0], xApoints[1], yApoints[1]).getAngle();
							longAxesA[pos] = new Line(xApoints[0], yApoints[0], xApoints[1], yApoints[1]).getLength();
							double angleTwo = new Line(xApoints[2], yApoints[2], xApoints[3], yApoints[3]).getAngle();
							double angleZeroPlusPi = angleZero + 180;
							double angleTwoPlusPi = angleTwo + 180;

							double angleDelta = angleZeroPlusPi%180 - angleTwoPlusPi%180;

							if ( Math.abs(angleDelta)  >80 && Math.abs(angleDelta)  <100) {
								haveAxesRoiA = true;
								angle = angleZero;
								anglesA[pos] = angle;
								
								ellipseRoiAs[pos] = new EllipseRoi(xApoints[0], yApoints[0], xApoints[1], yApoints[1], 
										(new Line(xApoints[2], yApoints[2], xApoints[3], yApoints[3])).getLength()
										/
										(new Line(xApoints[0], yApoints[0], xApoints[1], yApoints[1])).getLength()
												);
								impAs[pos].setRoi(ellipseRoiAs[pos], false);
								IJ.saveAs(impAs[pos], "Selection", savePath +  "Pos" + pos + "A_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"ellipse.roi");

								rectRoiAs[pos] = new Roi(ellipseRoiAs[pos].getBounds());
								impAs[pos].setRoi(rectRoiAs[pos], false);
								IJ.saveAs(impAs[pos], "Selection", savePath +  "Pos" + pos + "A_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"rectangle.roi");

								
							}else {
								rectRoiAs[pos] = new Roi(origRoiAs[pos].getBounds());
								impAs[pos].setRoi(rectRoiAs[pos], false);
								IJ.saveAs(impAs[pos], "Selection", savePath +  "Pos" + pos + "A_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"rectangle.roi");
								angle = new Line(xApoints[0], yApoints[0], xApoints[2], yApoints[2]).getAngle();
								longAxesA[pos] = new Line(xApoints[0], yApoints[0], xApoints[2], yApoints[2]).getLength();
							}
						} else {
							rectRoiAs[pos] = new Roi(origRoiAs[pos].getBounds());
							impAs[pos].setRoi(rectRoiAs[pos], false);
							IJ.saveAs(impAs[pos], "Selection", savePath +  "Pos" + pos + "A_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"rectangle.roi");
							angle = new Line(xApoints[0], yApoints[0], xApoints[npoints/2], yApoints[npoints/2]).getAngle();
							longAxesA[pos] = new Line(xApoints[0], yApoints[0], xApoints[npoints/2], yApoints[npoints/2]).getLength();
						}
					} else {
						rectRoiAs[pos] = new Roi(origRoiAs[pos].getBounds());
						impAs[pos].setRoi(rectRoiAs[pos], false);
						IJ.saveAs(impAs[pos], "Selection", savePath +  "Pos" + pos + "A_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"rectangle.roi");
						angle = origRoiAs[pos].getBounds().getHeight() > origRoiAs[pos].getBounds().getWidth()?90:0;
						longAxesA[pos] = origRoiAs[pos].getBounds().getHeight() > origRoiAs[pos].getBounds().getWidth()?origRoiAs[pos].getBounds().getHeight():origRoiAs[pos].getBounds().getWidth();
					}

					angle = 180+ angle;

				}
				impAs[pos].setRoi(origRoiAs[pos]);

				
				if (origRoiBs[pos] == null) {

				} else if (	(origRoiBs[pos].getType() != Roi.RECTANGLE
								/*&& origRoiBs[pos].getBounds().height > cropHeightB[pos]*/)
								
					|| (origRoiBs[pos].getType() == Roi.RECTANGLE 
								/*&& origRoiBs[pos].getBounds().getHeight() > cropHeightB[pos]*/)
								
					|| (origRoiBs[pos].getType() == Roi.RECTANGLE 
								/*&& origRoiBs[pos].getBounds().getWidth() > cropWidthB[pos]*/)) {
					
					IJ.saveAs(impBs[pos], "Selection", savePath +  "Pos" + pos + "B_"+(autodepth?(zFirstB[pos]+"-"+zLastB[pos]):"")+"original.roi");

					int[] xBpoints = origRoiBs[pos].getPolygon().xpoints;
					int[] yBpoints = origRoiBs[pos].getPolygon().ypoints;
					int npoints = xBpoints.length;
					
					if (lineageDecons){
						Roi snfRoi = (Roi) origRoiBs[pos].clone();
						snfRoi.setLocation(0,0);
						impBs[pos].setRoi(snfRoi);
						IJ.saveAs(impBs[pos], "Selection", savePath + "Decon-Fuse_"
								+ impBs[pos].getTitle().split(":")[0] +"originalSNF.roi");

					}
					
					double angle =0;
					if (origRoiBs[pos].getType() > Roi.OVAL) {

						if (npoints == 4) {
							double angleZero = new Line(xBpoints[0], yBpoints[0], xBpoints[1], yBpoints[1]).getAngle();
							longAxesB[pos] = new Line(xBpoints[0], yBpoints[0], xBpoints[1], yBpoints[1]).getLength();
							double angleTwo = new Line(xBpoints[2], yBpoints[2], xBpoints[3], yBpoints[3]).getAngle();
							double angleZeroPlusPi = angleZero + 180;
							double angleTwoPlusPi = angleTwo + 180;

							double angleDelta = angleZeroPlusPi%180 - angleTwoPlusPi%180;

							if ( Math.abs(angleDelta)  >80 && Math.abs(angleDelta)  <100) {
								haveAxesRoiB = true;
								angle = angleZero;
								anglesB[pos] = angle;
								
								ellipseRoiBs[pos] = new EllipseRoi(xBpoints[0], yBpoints[0], xBpoints[1], yBpoints[1], 
										(new Line(xBpoints[2], yBpoints[2], xBpoints[3], yBpoints[3])).getLength()
										/
										(new Line(xBpoints[0], yBpoints[0], xBpoints[1], yBpoints[1])).getLength()
												);
								impBs[pos].setRoi(ellipseRoiBs[pos], false);
								IJ.saveAs(impBs[pos], "Selection", savePath +  "Pos" + pos + "B_"+(autodepth?(zFirstB[pos]+"-"+zLastB[pos]):"")+"ellipse.roi");

								rectRoiBs[pos] = new Roi(ellipseRoiBs[pos].getBounds());
								impBs[pos].setRoi(rectRoiBs[pos], false);
								IJ.saveAs(impBs[pos], "Selection", savePath +  "Pos" + pos + "B_"+(autodepth?(zFirstB[pos]+"-"+zLastB[pos]):"")+"rectangle.roi");

								
							}else {
								rectRoiBs[pos] = new Roi(origRoiBs[pos].getBounds());
								impBs[pos].setRoi(rectRoiBs[pos], false);
								IJ.saveAs(impBs[pos], "Selection", savePath +  "Pos" + pos + "B_"+(autodepth?(zFirstB[pos]+"-"+zLastB[pos]):"")+"rectangle.roi");
								angle = new Line(xBpoints[0], yBpoints[0], xBpoints[2], yBpoints[2]).getAngle();
								longAxesB[pos] = new Line(xBpoints[0], yBpoints[0], xBpoints[2], yBpoints[2]).getLength();
							}
						} else {
							rectRoiBs[pos] = new Roi(origRoiBs[pos].getBounds());
							impBs[pos].setRoi(rectRoiBs[pos], false);
							IJ.saveAs(impBs[pos], "Selection", savePath +  "Pos" + pos + "B_"+(autodepth?(zFirstB[pos]+"-"+zLastB[pos]):"")+"rectangle.roi");
							angle = new Line(xBpoints[0], yBpoints[0], xBpoints[npoints/2], yBpoints[npoints/2]).getAngle();
							longAxesB[pos] = new Line(xBpoints[0], yBpoints[0], xBpoints[npoints/2], yBpoints[npoints/2]).getLength();
						}
					} else {
						rectRoiBs[pos] = new Roi(origRoiBs[pos].getBounds());
						impBs[pos].setRoi(rectRoiBs[pos], false);
						IJ.saveAs(impBs[pos], "Selection", savePath +  "Pos" + pos + "B_"+(autodepth?(zFirstB[pos]+"-"+zLastB[pos]):"")+"rectangle.roi");
						angle = origRoiBs[pos].getBounds().getHeight() > origRoiBs[pos].getBounds().getWidth()?90:0;
						longAxesB[pos] = origRoiBs[pos].getBounds().getHeight() > origRoiBs[pos].getBounds().getWidth()?origRoiBs[pos].getBounds().getHeight():origRoiBs[pos].getBounds().getWidth();
						
					}

					angle = 180+ angle;

					
				}
				impBs[pos].setRoi(origRoiBs[pos]);
							
				
				if (doProcessing[pos] && !(rectRoiAs[pos] == null || rectRoiBs[pos] == null)) {
					
					impAs[pos].setRoi(origRoiAs[pos]);
					impBs[pos].setRoi(origRoiBs[pos]);
					wasFrameA[pos] = impAs[pos].getFrame();
					wasFrameB[pos] = impBs[pos].getFrame();
					wasSliceA[pos] = impAs[pos].getSlice();
					wasSliceB[pos] = impBs[pos].getSlice();
					wasChannelA[pos] = impAs[pos].getChannel();
					wasChannelB[pos] = impBs[pos].getChannel();
					wasEdgesA[pos] = impAs[pos].getStack().isEdges();
					wasEdgesB[pos] = impBs[pos].getStack().isEdges();

					cropWidthA[pos] = rectRoiAs[pos].getBounds().width;
					cropHeightA[pos] = rectRoiAs[pos].getBounds().height;

					cropWidthB[pos] = rectRoiBs[pos].getBounds().width;
					cropHeightB[pos] = rectRoiBs[pos].getBounds().height;


					zFirstA[pos] = 1;
					zLastA[pos] = impAs[pos].getNSlices();
					zFirstB[pos] = 1;
					zLastB[pos] = impBs[pos].getNSlices();

					if (autodepth) {
						zFirstA[pos] = 0;
						zLastA[pos] = 0;
						zFirstB[pos] = 0;
						zLastB[pos] = 0;

						for (int zTest = 1; zTest<= impAs[pos].getNSlices(); zTest++) {
							Roi impRoi = (Roi) rectRoiAs[pos].clone();
							Polygon pA = new Polygon(impRoi.getPolygon().xpoints,
									impRoi.getPolygon().ypoints,
									impRoi.getPolygon().npoints);
							double fMax = impRoi.getBounds().width > impRoi.getBounds().height ? impRoi.getBounds().width: impRoi.getBounds().height;
							double angle = impRoi.getBounds().width > impRoi.getBounds().height ? 90 : 0;
							if (impRoi.getType() != Roi.RECTANGLE) {
								double[] fVals = impRoi.getFeretValues();
								fMax = fVals[0];
								angle = fVals[1];
							}
							Polygon pAR = pA;

							impAs[pos].setPositionWithoutUpdate(1, zTest, impAs[pos].getFrame());
							ImageProcessor ipTest = impAs[pos].getProcessor().duplicate();
							int[] ipHisTest = ipTest.getHistogram();
							double ipHisMode = 0.0;
							int ipHisLength = ipHisTest.length;
							int ipHisMaxBin = 0;
							for (int h=0; h<ipHisLength; h++) {
								if (ipHisTest[h] > ipHisMaxBin) {
									ipHisMaxBin = ipHisTest[h];
									ipHisMode = (double)h;
								}
							}
							ipTest.setRoi((Roi) rectRoiAs[pos]);
							ipTest = ipTest.crop();
							double testmean = ipTest.getStatistics().mean;
							if (testmean > ipHisMode*sliceTresholdVsModeA) {
								if (zFirstA[pos] == 0) {
									zFirstA[pos] = zTest;
									zLastA[pos] = zTest;
								}else {
									zLastA[pos] = zTest;
								}
							}

							if (wavelengths >= 2) {
								impAs[pos].setPositionWithoutUpdate(wavelengths, zTest, impAs[pos].getFrame());
								ipTest = impAs[pos].getProcessor().duplicate();
								ipHisTest = ipTest.getHistogram();
								ipHisMode = 0.0;
								ipHisLength = ipHisTest.length;
								ipHisMaxBin = 0;
								for (int h=0; h<ipHisLength; h++) {
									if (ipHisTest[h] > ipHisMaxBin) {
										ipHisMaxBin = ipHisTest[h];
										ipHisMode = (double)h;
									}
								}
								ipTest.setRoi((Roi) rectRoiAs[pos]);
								ipTest = ipTest.crop();
								if (ipTest.getStatistics().mean > ipHisMode*sliceTresholdVsModeA) {
									if (zFirstA[pos] == 0) {
										zFirstA[pos] = zTest;
										zLastA[pos] = zTest;
									}else {
										zLastA[pos] = zTest;
									}
								}

							}
						}

						for (int zTest = 1; zTest<= impBs[pos].getNSlices(); zTest++) {
							Roi impRoi = (Roi) rectRoiBs[pos].clone();
							Polygon pB = new Polygon(impRoi.getPolygon().xpoints,
									impRoi.getPolygon().ypoints,
									impRoi.getPolygon().npoints);
							double fMax = impRoi.getBounds().width > impRoi.getBounds().height ? impRoi.getBounds().width: impRoi.getBounds().height;
							double angle = impRoi.getBounds().width > impRoi.getBounds().height ? 90 : 0;
							if (impRoi.getType() != Roi.RECTANGLE) {
								double[] fVals = impRoi.getFeretValues();
								fMax = fVals[0];
								angle = fVals[1];
							}
							Polygon pBR = pB;

							impBs[pos].setPositionWithoutUpdate(1, zTest, impBs[pos].getFrame());
							ImageProcessor ipTest = impBs[pos].getProcessor().duplicate();
							int[] ipHisTest = ipTest.getHistogram();
							double ipHisMode = 0.0;
							int ipHisLength = ipHisTest.length;
							int ipHisMaxBin = 0;
							for (int h=0; h<ipHisLength; h++) {
								if (ipHisTest[h] > ipHisMaxBin) {
									ipHisMaxBin = ipHisTest[h];
									ipHisMode = (double)h;
								}
							}
							ipTest.setRoi((Roi) rectRoiBs[pos]);
							ipTest = ipTest.crop();
							if (ipTest.getStatistics().mean > ipHisMode*sliceTresholdVsModeB) {
								if (zFirstB[pos] == 0) {
									zFirstB[pos] = zTest;
									zLastB[pos] = zTest;
								}else {
									zLastB[pos] = zTest;
								}
							}

							if (wavelengths >= 2) {
								impBs[pos].setPositionWithoutUpdate(wavelengths, zTest, impBs[pos].getFrame());
								ipTest = impBs[pos].getProcessor().duplicate();
								ipHisTest = ipTest.getHistogram();
								ipHisMode = 0.0;
								ipHisLength = ipHisTest.length;
								ipHisMaxBin = 0;
								for (int h=0; h<ipHisLength; h++) {
									if (ipHisTest[h] > ipHisMaxBin) {
										ipHisMaxBin = ipHisTest[h];
										ipHisMode = (double)h;
									}
								}
								ipTest.setRoi((Roi) rectRoiBs[pos]);
								ipTest = ipTest.crop();

								if (ipTest.getStatistics().mean > ipHisMode*sliceTresholdVsModeB) {
									if (zFirstB[pos] == 0) {
										zFirstB[pos] = zTest;
										zLastB[pos] = zTest;
									}else {
										zLastB[pos] = zTest;
									}
								}

							}
						}

					}

					IJ.saveAs(impAs[pos], "Selection", savePath +  "Pos" + pos + "A_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"original.roi");
					IJ.saveAs(impBs[pos], "Selection", savePath +  "Pos" + pos + "B_"+(autodepth?(zFirstA[pos]+"-"+zLastA[pos]):"")+"original.roi");

					impAs[pos].setPosition(wasChannelA[pos], wasSliceA[pos], wasFrameA[pos]);
					impAs[pos].updateAndDraw();
					impBs[pos].setPosition(wasChannelB[pos], wasSliceB[pos], wasFrameB[pos]);
					impBs[pos].updateAndDraw();
				}
			}
			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				} 

				if(doProcessing[pos]) {
					//					if(fuseButton[pos][0] != null)
					//						fuseButton[pos][0].setVisible(false);
					//					if(fuseButton[pos][1] != null)
					//						fuseButton[pos][1].setVisible(false);
					//					impAs[pos].getWindow().pack();
					//					impBs[pos].getWindow().pack();
				}
			}
			IJ.run("Tile");

		} else {	//if not deconning immediately)
			Panel[][] diSPIMPanel = new Panel[pDim][2];
			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				} 
				Dimension sizeWinA = impAs[pos].getWindow().getSize();
				Dimension sizeWinB = impBs[pos].getWindow().getSize();
				if(diSPIMPanel[pos][0] == null) {
					diSPIMPanel[pos][0] = new Panel(new BorderLayout());
					
				}
				if(diSPIMPanel[pos][1] == null) {
					diSPIMPanel[pos][1] = new Panel(new BorderLayout());					
				}

				if(dispimToolsButton[pos][0] == null) {
					dispimToolsButton[pos][0] = new JButton("diSPIM");
					dispimToolsButton[pos][0].addActionListener(this);
					diSPIMPanel[pos][0].add(BorderLayout.WEST, dispimToolsButton[pos][0]);
					if ( ((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdXA() == 0 &&
							((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdYA() == 0 &&
							((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdZA() == 0) {
						
						dispimToolsButton[pos][0].setBackground(Color.orange);
					}
					dispimToolsButton[pos][0].setVisible(true);
				}else {
					dispimToolsButton[pos][0].setVisible(true);
				}

				if(dispimToolsButton[pos][1] == null) {
					dispimToolsButton[pos][1] = new JButton("diSPIM");
					dispimToolsButton[pos][1].addActionListener(this);
					diSPIMPanel[pos][1].add(BorderLayout.WEST, dispimToolsButton[pos][1]);
					if ( ((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdXA() == 0 &&
							((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdYA() == 0 &&
							((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdZA() == 0) {
						
						dispimToolsButton[pos][1].setBackground(Color.orange);
					}
					dispimToolsButton[pos][1].setVisible(true);
				}else {
					dispimToolsButton[pos][1].setVisible(true);
				}

				if(fuseButton[pos][0] == null) {
					fuseButton[pos][0] = new JButton("Fuse");
					fuseButton[pos][0].addActionListener(this);
					diSPIMPanel[pos][0].add(BorderLayout.NORTH, fuseButton[pos][0]);
					fuseButton[pos][0].setVisible(false);
				}else {
					fuseButton[pos][0].setVisible(false);
				}
				if(fuseButton[pos][1] == null) {
					fuseButton[pos][1] = new JButton("Fuse");
					fuseButton[pos][1].addActionListener(this);
					diSPIMPanel[pos][1].add(BorderLayout.NORTH, fuseButton[pos][1]);
					fuseButton[pos][1].setVisible(false);
				}else {
					fuseButton[pos][1].setVisible(false);
				}
				
				spinnerPanel[pos][0] = new Panel();
				spinnerPanel[pos][0].setLayout(new BorderLayout());
				spinnerPanel[pos][1] = new Panel();
				spinnerPanel[pos][1].setLayout(new BorderLayout());

				if ( splitChannels = true ) {

					if(splitButton[pos][0] == null) {
						splitButton[pos][0] = new JButton("CCM");
						splitButton[pos][0].setToolTipText("Switch between viewing mode and channel calibration mode");
						splitButton[pos][0].addActionListener(this);
						diSPIMPanel[pos][0].add(BorderLayout.SOUTH, splitButton[pos][0]);
						splitButton[pos][0].setVisible(false);
					}else {
						splitButton[pos][0].setVisible(false);
					}
					if(splitButton[pos][1] == null) {
						splitButton[pos][1] = new JButton("CCM");
						splitButton[pos][0].setToolTipText("Switch between viewing mode and channel calibration mode");
						splitButton[pos][1].addActionListener(this);
						diSPIMPanel[pos][1].add(BorderLayout.SOUTH, splitButton[pos][1]);
						splitButton[pos][1].setVisible(false);
					}else {
						splitButton[pos][1].setVisible(false);
					}
					
					if(xSpinner[pos][0] == null) {
						xSpinner[pos][0] = new JSpinner(new SpinnerNumberModel(((MultiFileInfoVirtualStack)impAs[pos].getStack()).getdXA(), -impAs[pos].getWidth(), impAs[pos].getWidth(), 1));
						xSpinner[pos][0].setToolTipText("Adjust Channel X Alignment ViewA");
						xSpinner[pos][0].addChangeListener(this);
						spinnerPanel[pos][0].add(BorderLayout.NORTH, xSpinner[pos][0]);
						xSpinner[pos][0].setVisible(false);
					}else {
						xSpinner[pos][0].setVisible(false);
					}
					if(ySpinner[pos][0] == null) {
						ySpinner[pos][0] = new JSpinner(new SpinnerNumberModel(((MultiFileInfoVirtualStack)impAs[pos].getStack()).getdYA(), -impAs[pos].getHeight(), impAs[pos].getHeight(), 1));
						ySpinner[pos][0].setToolTipText("Adjust Channel Y Alignment ViewA");
						ySpinner[pos][0].addChangeListener(this);
						spinnerPanel[pos][0].add(BorderLayout.CENTER, ySpinner[pos][0]);
						ySpinner[pos][0].setVisible(false);
					}else {
						ySpinner[pos][0].setVisible(false);
					}

					if(xSpinner[pos][1] == null) {
						xSpinner[pos][1] = new JSpinner(new SpinnerNumberModel(((MultiFileInfoVirtualStack)impBs[pos].getStack()).getdXB(), -impBs[pos].getWidth(), impBs[pos].getWidth(), 1));
						xSpinner[pos][1].setToolTipText("Adjust Channel X Alignment ViewB");
						xSpinner[pos][1].addChangeListener(this);
						spinnerPanel[pos][1].add(BorderLayout.NORTH, xSpinner[pos][1]);
						xSpinner[pos][1].setVisible(false);
					}else {
						xSpinner[pos][1].setVisible(false);
					}
					if(ySpinner[pos][1] == null) {
						ySpinner[pos][1] = new JSpinner(new SpinnerNumberModel(((MultiFileInfoVirtualStack)impBs[pos].getStack()).getdYB(), -impBs[pos].getHeight(), impBs[pos].getHeight(), 1));
						ySpinner[pos][1].setToolTipText("Adjust Channel Y Alignment ViewB");
						ySpinner[pos][1].addChangeListener(this);
						spinnerPanel[pos][1].add(BorderLayout.CENTER, ySpinner[pos][1]);
						ySpinner[pos][1].setVisible(false);
					}else {
						ySpinner[pos][1].setVisible(false);
					}
				}
				
				if(zSpinner[pos][0] == null) {
					zSpinner[pos][0] = new JSpinner(new SpinnerNumberModel(((MultiFileInfoVirtualStack)impAs[pos].getStack()).getdZA(), -impAs[pos].getNSlices(), impAs[pos].getNSlices(), 1));
					zSpinner[pos][0].setToolTipText("Adjust Channel Z Alignment ViewA");
					zSpinner[pos][0].addChangeListener(this);
					spinnerPanel[pos][0].add(BorderLayout.SOUTH, zSpinner[pos][0]);
					zSpinner[pos][0].setVisible(false);
				}else {
					zSpinner[pos][0].setVisible(false);
				}
				diSPIMPanel[pos][0].add(BorderLayout.CENTER, spinnerPanel[pos][0]);
				
				if(zSpinner[pos][1] == null) {
					zSpinner[pos][1] = new JSpinner(new SpinnerNumberModel(((MultiFileInfoVirtualStack)impAs[pos].getStack()).getdZB(), -impBs[pos].getNSlices(), impBs[pos].getNSlices(), 1));
					zSpinner[pos][1].setToolTipText("Adjust Channel Z Alignment ViewB");
					zSpinner[pos][1].addChangeListener(this);
					spinnerPanel[pos][1].add(BorderLayout.SOUTH, zSpinner[pos][1]);
					zSpinner[pos][1].setVisible(false);
				}else {
					zSpinner[pos][1].setVisible(false);
				}
				diSPIMPanel[pos][1].add(BorderLayout.CENTER, spinnerPanel[pos][1]);

				
				diSPIMPanel[pos][0].setVisible(true);
				impAs[pos].getWindow().viewButtonPanel.add(diSPIMPanel[pos][0]);
				impAs[pos].getWindow().viewButtonPanel.validate();
				diSPIMPanel[pos][1].setVisible(true);
				impBs[pos].getWindow().viewButtonPanel.add(diSPIMPanel[pos][1]);
				impBs[pos].getWindow().viewButtonPanel.validate();

				impAs[pos].getWindow().pack();
//				impAs[pos].getWindow().setSize(sizeWinA);
				impBs[pos].getWindow().pack();
//				impBs[pos].getWindow().setSize(sizeWinB);
				
				
			}
			IJ.run("Tile");
		}

		if (doMipavDecon || doGPUdecon) {
			stackDFs = new MultiFileInfoVirtualStack[pDim];
			stackPrxs = new MultiFileInfoVirtualStack[pDim];
			stackPrys = new MultiFileInfoVirtualStack[pDim];

			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				} 

				
				
				if (doProcessing[pos]) {
					double pwA = impAs[pos].getCalibration().pixelWidth;
					double phA = impAs[pos].getCalibration().pixelHeight;
					double pdA = impAs[pos].getCalibration().pixelDepth;
					double pwB = impBs[pos].getCalibration().pixelWidth;
					double phB = impBs[pos].getCalibration().pixelHeight;
					double pdB = impBs[pos].getCalibration().pixelDepth;

					stackDFs[pos] = null;					

					if (impAs[pos].hasNullStack())
						continue;
					if (impBs[pos].hasNullStack())
						continue;

					depthSkipFactor = ((int)(pdB/phB)) ;

					origRoiAs[pos] = impAs[pos].getRoi();
					origRoiBs[pos] = impBs[pos].getRoi();
					wasFrameA[pos] = impAs[pos].getFrame();
					wasFrameB[pos] = impBs[pos].getFrame();
					wasSliceA[pos] = impAs[pos].getSlice();
					wasSliceB[pos] = impBs[pos].getSlice();
					wasChannelA[pos] = impAs[pos].getChannel();
					wasChannelB[pos] = impBs[pos].getChannel();
					wasEdgesA[pos] = impAs[pos].getStack().isEdges();
					wasEdgesB[pos] = impBs[pos].getStack().isEdges();

					if ((new File(savePath)).canRead()) {
						// SETUP OF WINDOWS SHOWING PRE-EXISTING DECON OUTPUTS
						stackDFs[pos] = null;					
						stackPrxs[pos] = null;					
						stackPrys[pos] = null;					
						if (true) {
							prxPath = "";
							pryPath = "";
							if (doGPUdecon) {
								if (new File(savePath
										+ File.separator + "RegDecon" + File.separator + "Pos" + pos).canRead() ) {
									stackDFs[pos] = new MultiFileInfoVirtualStack(savePath
											+ File.separator + "RegDecon" + File.separator + "Pos" + pos,  "Deconvolution", false,
											false);
									prxPath = savePath + File.separator + "ProjX_"+("Decon-Fuse_"
											+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
									pryPath = savePath + File.separator + "ProjY_"+("Decon-Fuse_"
											+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
									IJ.log(prxPath);
									IJ.log(pryPath);
									if (new File(prxPath).canRead() && new File(pryPath).canRead()) {
										if (wavelengths ==1) {
											stackPrxs[pos] = new MultiFileInfoVirtualStack(prxPath,"Color", false,
													false);					
											stackPrys[pos] = new MultiFileInfoVirtualStack(pryPath, "Color", false,
													false);		
										}
										if (wavelengths >= 2) {
											stackPrxs[pos] = new MultiFileInfoVirtualStack(prxPath, "Color", false,
													false);					
											stackPrys[pos] = new MultiFileInfoVirtualStack(pryPath,  "Color", false,
													false);		
										}
									} 
								}
							} else {
								stackDFs[pos] = new MultiFileInfoVirtualStack(
										((new File(dirOrOMETiff)).isDirectory() ? dirOrOMETiff
												: (new File(dirOrOMETiff)).getParent())
												+ File.separator, "Deconvolution",
												false);
							}
							if (stackDFs[pos]!=null && stackDFs[pos].getSize() > 0) {
								ImageWindow win = null;
								impDF1s[pos] = new ImagePlus();
								impDF1s[pos].getRoiManager().setImagePlus(null);
								impDF1s[pos].getRoiManager().dispose();
								impDF1s[pos].setRoiManager(null);
								impDF1s[pos].setStack((orientBeforeLineage?"Preview_":"") + "Decon-Fuse_"
										+ impAs[pos].getTitle().split(":")[0], stackDFs[pos]);
								impDF1s[pos].setFileInfo(new FileInfo());
								impDF1s[pos].getCalibration().setUnit(impAs[pos].getCalibration().getUnit());
								impDF1s[pos].getCalibration().pixelWidth = pwA;
								impDF1s[pos].getCalibration().pixelHeight = phA;
								impDF1s[pos].getCalibration().pixelDepth = pwA;

								impDF1s[pos].getOriginalFileInfo().directory = dirOrOMETiff;
								int stkNSlicesDF = impDF1s[pos].getStackSize();
								int zSlicesDF1 = stackDFs[pos].getFivStacks().get(0)
										.getSize();
								impDF1s[pos].setOpenAsHyperStack(true);
								IJ.log("outputWavelengths "+outputWavelengths+".  zSlicesDF1 "+zSlicesDF1+".  stkNSlicesDF "+stkNSlicesDF);
								impDF1s[pos].setStack(impDF1s[pos].getStack(), outputWavelengths,
										zSlicesDF1, stkNSlicesDF
										/ (outputWavelengths * zSlicesDF1));
								if (ciDFs[pos] != null) {
									win = ciDFs[pos].getWindow();
								} else {
									win = null;
								}
								ciDFs[pos] = new CompositeImage(impDF1s[pos]);
								ciDFs[pos].setPosition(1, ciDFs[pos].getNSlices()/2, ciDFs[pos].getNFrames());

								if (wavelengths > 1)
									ciDFs[pos].setMode(CompositeImage.COMPOSITE);
								else
									ciDFs[pos].setMode(CompositeImage.GRAYSCALE);
								if (win==null) {
									ciDFs[pos].show();
									WindowManager.group(impBs[pos], ciDFs[pos]);
									ciDFs[pos].setPosition(1, ciDFs[pos].getNSlices()/2, ciDFs[pos].getNFrames());
								} else {
									int oldW = win.getWidth();
									int oldH = win.getHeight();
									int oldC = win.getImagePlus().getChannel();
									int oldZ = win.getImagePlus().getSlice();
									int oldT = win.getImagePlus().getFrame();
									double oldMag = win.getCanvas().getMagnification();
									double oldMin = win.getImagePlus()
											.getDisplayRangeMin();
									double oldMax = win.getImagePlus()
											.getDisplayRangeMax();
									boolean oldEdges = win.getImagePlus().getStack().isEdges();

									ciDFs[pos].setWindow(win);
									win.updateImage(ciDFs[pos]);
									win.getImagePlus().getStack().setEdges(oldEdges);
									win.setSize(oldW, oldH);
									((StackWindow) win).addScrollbars(ciDFs[pos]);
									win.getCanvas().setMagnification(oldMag);								
									win.getImagePlus().updateAndDraw();
									win.getImagePlus().setPosition(oldC, oldZ, oldT);
									win.getImagePlus().setDisplayRange(oldMin, oldMax);
									win.setSize(win.getSize().width,
											win.getSize().height);

								}

								if (stackPrxs[pos]!=null && stackPrys[pos]!=null) {
									ImageWindow prjXwin = null;
									ImageWindow prjYwin = null;

									impPrxs[pos] = new ImagePlus();
									impPrxs[pos].getRoiManager().setImagePlus(null);
									impPrxs[pos].getRoiManager().dispose();
									impPrxs[pos].setRoiManager(null);
									impPrxs[pos].setStack((orientBeforeLineage?"Preview_":"") + "3DProjX_Decon-Fuse_"
											+ impAs[pos].getTitle().split(":")[0], stackPrxs[pos]);
									impPrxs[pos].setFileInfo(new FileInfo());
									impPrxs[pos].getCalibration().setUnit(impDF1s[pos].getCalibration().getUnit());
									impPrxs[pos].getCalibration().pixelWidth = impDF1s[pos].getCalibration().pixelWidth;
									impPrxs[pos].getCalibration().pixelHeight = impDF1s[pos].getCalibration().pixelHeight;
									impPrxs[pos].getCalibration().pixelDepth = impDF1s[pos].getCalibration().pixelWidth;

									impPrxs[pos].getOriginalFileInfo().directory = prxPath + File.separator;
									int stkNSlicesPrx = impPrxs[pos].getStackSize();
									int zSlicesPrx = stackPrxs[pos].getFivStacks().get(0)
											.getSize();
									impPrxs[pos].setOpenAsHyperStack(true);
									impPrxs[pos].setStack(impPrxs[pos].getStack(), outputWavelengths,
											zSlicesPrx, stkNSlicesPrx
											/ (outputWavelengths * zSlicesPrx));
									if (ciPrxs[pos] != null) {
										prjXwin = ciPrxs[pos].getWindow();
									}
									ciPrxs[pos] = new CompositeImage(impPrxs[pos]);
									ciPrxs[pos].setPosition(1, 1, ciPrxs[pos].getNFrames());

									impPrys[pos] = new ImagePlus();
									impPrys[pos].getRoiManager().setImagePlus(null);
									impPrys[pos].getRoiManager().dispose();
									impPrys[pos].setRoiManager(null);
									impPrys[pos].setStack((orientBeforeLineage?"Preview_":"") + "3DProjY_Decon-Fuse_"
											+ impAs[pos].getTitle().split(":")[0], stackPrys[pos]);
									impPrys[pos].setFileInfo(new FileInfo());
									impPrys[pos].getCalibration().setUnit(impDF1s[pos].getCalibration().getUnit());
									impPrys[pos].getCalibration().pixelWidth = impDF1s[pos].getCalibration().pixelWidth;
									impPrys[pos].getCalibration().pixelHeight = impDF1s[pos].getCalibration().pixelHeight;
									impPrys[pos].getCalibration().pixelDepth = impDF1s[pos].getCalibration().pixelWidth;

									impPrys[pos].getOriginalFileInfo().directory = pryPath + File.separator;
									int stkNSlicesPry = impPrys[pos].getStackSize();
									int zSlicesPry = stackPrys[pos].getFivStacks().get(0)
											.getSize();
									impPrys[pos].setOpenAsHyperStack(true);
									impPrys[pos].setStack(impPrys[pos].getStack(), outputWavelengths,
											zSlicesPry, stkNSlicesPry
											/ (outputWavelengths * zSlicesPry));
									if (ciPrys[pos] != null) {
										prjYwin = ciPrys[pos].getWindow();
									}
									ciPrys[pos] = new CompositeImage(impPrys[pos]);
									ciPrys[pos].setPosition(1, 1, ciPrys[pos].getNFrames());

									if (wavelengths > 1)
										ciPrxs[pos].setMode(CompositeImage.COMPOSITE);
									else
										ciPrxs[pos].setMode(CompositeImage.GRAYSCALE);
									if (prjXwin==null) {
										ciPrxs[pos].show();
										WindowManager.group(ciDFs[pos], ciPrxs[pos]);
										ciPrxs[pos].setPosition(1, 1, ciPrxs[pos].getNFrames());
									} else {
										int oldW = prjXwin.getWidth();
										int oldH = prjXwin.getHeight();
										int oldC = prjXwin.getImagePlus().getChannel();
										int oldZ = prjXwin.getImagePlus().getSlice();
										int oldT = prjXwin.getImagePlus().getFrame();
										double oldMag = prjXwin.getCanvas().getMagnification();
										double oldMin = prjXwin.getImagePlus()
												.getDisplayRangeMin();
										double oldMax = prjXwin.getImagePlus()
												.getDisplayRangeMax();
										((CompositeImage)ciPrxs[pos]).copyLuts(prjXwin.getImagePlus());
										boolean oldEdges = prjXwin.getImagePlus().getStack().isEdges();

										ciPrxs[pos].setWindow(prjXwin);
										prjXwin.updateImage(ciPrxs[pos]);
										prjXwin.getImagePlus().getStack().setEdges(oldEdges);
										prjXwin.setSize(oldW, oldH);
										((StackWindow) prjXwin).addScrollbars(ciPrxs[pos]);
										prjXwin.getCanvas().setMagnification(oldMag);
										prjXwin.getImagePlus().updateAndDraw();
										prjXwin.getImagePlus().setPosition(oldC, oldZ, oldT);
										prjXwin.getImagePlus().setDisplayRange(oldMin, oldMax);
										prjXwin.setSize(prjXwin.getSize().width,
												prjXwin.getSize().height);

									}

									if (wavelengths > 1)
										ciPrys[pos].setMode(CompositeImage.COMPOSITE);
									else
										ciPrys[pos].setMode(CompositeImage.GRAYSCALE);
									if (prjYwin==null) {
										ciPrys[pos].show();
										WindowManager.group(ciPrxs[pos], ciPrys[pos]);
										ciPrys[pos].setPosition(1, 1, ciPrys[pos].getNFrames());
									} else {
										int oldW = prjYwin.getWidth();
										int oldH = prjYwin.getHeight();
										int oldC = prjYwin.getImagePlus().getChannel();
										int oldZ = prjYwin.getImagePlus().getSlice();
										int oldT = prjYwin.getImagePlus().getFrame();
										double oldMag = prjXwin.getCanvas().getMagnification();
										double oldMin = prjYwin.getImagePlus()
												.getDisplayRangeMin();
										double oldMax = prjYwin.getImagePlus()
												.getDisplayRangeMax();
										((CompositeImage)ciPrys[pos]).copyLuts(prjYwin.getImagePlus());
										boolean oldEdges = prjYwin.getImagePlus().getStack().isEdges();

										ciPrys[pos].setWindow(prjYwin);
										prjYwin.updateImage(ciPrys[pos]);
										prjYwin.getImagePlus().getStack().setEdges(oldEdges);
										prjYwin.setSize(oldW, oldH);
										((StackWindow) prjYwin).addScrollbars(ciPrys[pos]);
										prjYwin.getCanvas().setMagnification(oldMag);								
										prjYwin.getImagePlus().updateAndDraw();
										prjYwin.getImagePlus().setPosition(oldC, oldZ, oldT);
										prjYwin.getImagePlus().setDisplayRange(oldMin, oldMax);
										prjYwin.setSize(prjYwin.getSize().width,
												prjYwin.getSize().height);

									}
									if (orientBeforeLineage) {
										Panel[] diSPIMPreviewPanel = new Panel[2];
										Dimension sizeWinA = ciPrys[pos]
												.getWindow().getSize();
										if (diSPIMPreviewPanel[0] == null) {
											diSPIMPreviewPanel[0] = new Panel(
													new BorderLayout());
										}
										if (diSPIMPreviewPanel[1] == null) {
											diSPIMPreviewPanel[1] = new Panel(
													new BorderLayout());
										}
										if (dispimPreviewButton[pos][0] == null) {
											dispimPreviewButton[pos][0] = new JButton(
													"diSPIM Preview");
											dispimPreviewButton[pos][0]
													.addActionListener(DISPIM_Monitor.this);
											diSPIMPreviewPanel[0]
													.add(BorderLayout.WEST,
															dispimPreviewButton[pos][0]);
											dispimPreviewButton[pos][0]
													.setBackground(Color.orange);

											dispimPreviewButton[pos][0]
													.setVisible(true);
										} else {
											dispimPreviewButton[pos][0]
													.setVisible(true);
										}
										if (dispimPreviewButton[pos][1] == null) {
											dispimPreviewButton[pos][1] = new JButton(
													"diSPIM Preview");
											dispimPreviewButton[pos][1]
													.addActionListener(DISPIM_Monitor.this);
											diSPIMPreviewPanel[1]
													.add(BorderLayout.WEST,
															dispimPreviewButton[pos][1]);
											dispimPreviewButton[pos][1]
													.setBackground(Color.orange);

											dispimPreviewButton[pos][1]
													.setVisible(true);
										} else {
											dispimPreviewButton[pos][1]
													.setVisible(true);
										}
										diSPIMPreviewPanel[0].setVisible(true);
										ciPrxs[pos].getWindow().viewButtonPanel
												.add(diSPIMPreviewPanel[0]);
										ciPrxs[pos].getWindow().viewButtonPanel
												.validate();
										diSPIMPreviewPanel[1].setVisible(true);
										ciPrys[pos].getWindow().viewButtonPanel
												.add(diSPIMPreviewPanel[1]);
										ciPrys[pos].getWindow().viewButtonPanel
												.validate();
										ciPrxs[pos].getWindow().pack();
										//									impAs[pos].getWindow().setSize(sizeWinA);
										ciPrys[pos].getWindow().pack();
										//									impBs[pos].getWindow().setSize(sizeWinB);
									}
									

								}
							}
						}
					}
				}

			}  //END SETUP OF WINDOW SHOWING PRE-EXISTING DECON OUTPUTS
			IJ.run("Tile");
		}


		if (doGPUdecon) {  
			processFilesByMinGuoDeconvolution();
		} //END PROCESSING ALREADY SAVED RAW DATA 

		if (doMipavDecon) {

			processFilesByMipavDeconvolution();
		}
		
		if (lineageDecons){
			StarryNiteFeeder snf = new StarryNiteFeeder();
			String deconPath = savePath;
			IJ.log(""+deconPath +"|"+ paramsPath +"|"+ deconPath +"|"+ depthSkipFactor);
			snf.run(""+deconPath +"|"+ paramsPath +"|"+ deconPath +"|"+ depthSkipFactor);
		}

		

//		IJ.run("Tile");

		monitoring = true;

		while (monitoring) {
			boolean focus = false;
			if (arg.contains("currentStacks")){

			} else if ((new File(dirOrOMETiff)).isDirectory()) {
				if (omeTiffs) {
					fileListA = new File("" + dirOrOMETiff).list();
					String[] newlist = new String[fileListA.length];
					int yescount = 0;
					for (int fle=0; fle<fileListA.length; fle++) {
						if (fileListA[fle].contains(keyString)) {
							newlist[yescount] = fileListA[fle];
							yescount++;
						}
					}
					fileListA = Arrays.copyOf(newlist, yescount);
					listA = fileListA;
					deconFileList1 = (new File(dirOrOMETiff +"_Deconvolution1")).list();
					deconList1 = deconFileList1;
					deconFileList2 = (new File(dirOrOMETiff +"_Deconvolution2")).list();
					deconList2 = deconFileList2;



					while (monitoring && (fileListA.length == listA.length)
							&& (!doMipavDecon || ((deconList1 == null && deconList2 == null) || (!(deconList1 == null
							|| deconFileList1 == null || deconList1.length != deconFileList1.length) || !(deconList2 == null
							|| deconFileList2 == null || deconList2.length != deconFileList2.length))))) {

						if (IJ.escapePressed())
							if (!IJ.showMessageWithCancel(
									"Cancel diSPIM Monitor Updates?",
									"Monitoring of "
											+ dirOrOMETiff
											+ " paused by Escape.\nClick OK to resume."))
								return;
							else
								IJ.resetEscape();
						listA = new File("" + dirOrOMETiff).list();
						String[] newlist2 = new String[listA.length];
						int yescount2 = 0;
						for (int fle=0; fle<listA.length; fle++) {
							if (listA[fle].contains(keyString)) {
								newlist2[yescount2] = listA[fle];
								yescount2++;
							}
						}
						listA = Arrays.copyOf(newlist2, yescount2);
						deconList1 = (new File(dirOrOMETiff +"_Deconvolution1"))
								.list();
						deconList2 = (new File(dirOrOMETiff +"_Deconvolution2"))
								.list();
						IJ.wait(5000);
					}

					if (!monitoring) {
						continue;
					}

					IJ.log("NEW DATA WRITTEN");
					uploadPending = true;
					IJ.wait(10000);
					fileListA = listA;
					deconFileList1 = deconList1;
					deconFileList2 = deconList2;

					long modDateA = 0;
					String recentestA = "";
					for (int a = 0; a < fileListA.length; a++) {
						if (!fileListA[a].endsWith(".roi")
								&& !fileListA[a].endsWith(".DS_Store")) {
							if (modDateA < (new File(dirOrOMETiff
									+ File.separator + fileListA[a]))
									.lastModified()) {
								modDateA = (new File(dirOrOMETiff
										+ File.separator + fileListA[a]))
										.lastModified();
								recentestA = dirOrOMETiff
										+ File.separator + fileListA[a];
							}
						}
					}
					IJ.log(recentestA + "\n" + modDateA);
					File recentestAFile = new File(recentestA);
					ArrayList<String> rafdlclean = new ArrayList<String>();
					while (rafdlclean.size() < pDim) {
						rafdlclean = new ArrayList<String>();
						String[] recentestAFileDirList = recentestAFile.list();
						for (int f = 0; f < recentestAFileDirList.length; f++) {
							if (!recentestAFileDirList[f].endsWith(".roi")
									&& !recentestAFileDirList[f].endsWith(".DS_Store")) {
								rafdlclean.add(recentestAFileDirList[f]);
							}
						}
						IJ.wait(1000);
						IJ.log(".");
						recentestAFileDirList = recentestAFile.list();
					}
					for (int nf =0; nf< pDim; nf++) {
						if (nf>0 && new File(rafdlclean.get(nf)).length() != new File(rafdlclean.get(nf)).length())
							nf--;
					}
					IJ.log(recentestA + " complete");

					MultiFileInfoVirtualStack[] stackAs = new MultiFileInfoVirtualStack[pDim];
					MultiFileInfoVirtualStack[] stackBs = new MultiFileInfoVirtualStack[pDim];

					for (int pos=0; pos<pDim; pos++) {
						boolean go = true;
						if (posIntArray!=null){
							go = false;
							for (int posInt:posIntArray){
								if (posInt == pos){
									go=true;
								}
							}
						}
						if (!go){
							continue;
						}

						doProcessing[pos] = true;

						if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
							doProcessing[pos] = false;
							continue;
						}
						if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
							doProcessing[pos] = false;
							continue;
						} 

						if (doProcessing[pos]) {

							if (impAs[pos].hasNullStack())
								continue;
							if (impBs[pos].hasNullStack())
								continue;

							ImageWindow win = null;

							win = impAs[pos].getWindow();
							double zoomA = win.getCanvas().getMagnification();
							int cA = impAs[pos].getChannel();
							int zA = impAs[pos].getSlice();
							int tA = impAs[pos].getFrame();
							boolean edgesA = impAs[pos].getStack().isEdges();
							boolean tailing = tA==impAs[pos].getNFrames();
							tDim = listA.length;

							stackAs[pos] = new MultiFileInfoVirtualStack(
									dirConcat, dimOrder, keyString, cDim*(diSPIM_MM_Channels/vDim>1 && diSPIM_MM_channelOrder == "RG"?-1:1), zDim, tDim, vDim, pos,
									false, false, true, new File(this.dirOrOMETiff).isDirectory() && this.dirOrOMETiff.endsWith(".ome.tif"));

							ImagePlus impNext = new ImagePlus(impAs[pos].getTitle(), stackAs[pos]);
							impNext.setOpenAsHyperStack(true);
							impNext.setDimensions(cDim, zDim, tDim);
							impNext = new CompositeImage(impNext);
							((CompositeImage)impNext).setMode(modeA);
							((CompositeImage)impNext).reset();
							((CompositeImage)impNext).copyLuts(impAs[pos]);
							impNext.setCalibration(impAs[pos].getCalibration());
							if (stageScan)
								stackAs[pos].setSkewXperZ(
										impNext.getCalibration().pixelDepth / impNext.getCalibration().pixelWidth);

							impAs[pos].flush();
							impAs[pos] = impNext;


							win.setImage(impAs[pos]);
							if (win instanceof StackWindow) {
								StackWindow sw = (StackWindow)win;
								int stackSize = impAs[pos].getStackSize();
								int nScrollbars = sw.getNScrollbars();
								sw.addScrollbars(impAs[pos]);
							}
							impAs[pos].getStack().setEdges(edgesA);
							impAs[pos].setPosition(cA, zA, (tailing || tA > impAs[pos].getNFrames())? impAs[pos].getNFrames() : tA);
							((CompositeImage)impAs[pos]).setMode(modeA);
							win.getCanvas().setMagnification(zoomA);
							Dimension winSize = win.getSize();
							win.pack();
							win.setSize(winSize);


							win = impBs[pos].getWindow();
							double zoomB = win.getCanvas().getMagnification();
							int cB = impBs[pos].getChannel();
							int zB = impBs[pos].getSlice();
							int tB = impBs[pos].getFrame();
							if (impBs[pos].isComposite()) {
								modeB = ((CompositeImage)impBs[pos]).getCompositeMode();
							}
							boolean edgesB = impBs[pos].getStack().isEdges();

							tailing = tB==impBs[pos].getNFrames();
							tDim = listA.length;

							stackBs[pos] = new MultiFileInfoVirtualStack(
									dirConcat, dimOrder, keyString, cDim*(diSPIM_MM_Channels/vDim>1 && diSPIM_MM_channelOrder == "RG"?-1:1), zDim, tDim, vDim, pos,
									true, false, true, new File(this.dirOrOMETiff).isDirectory() && this.dirOrOMETiff.endsWith(".ome.tif"));

							impNext = new CompositeImage(new ImagePlus(impBs[pos].getTitle(), stackBs[pos]));
							impNext.setOpenAsHyperStack(true);
							impNext.setDimensions(cDim, zDim, tDim);
							impNext = new CompositeImage(impNext);
							((CompositeImage)impNext).setMode(modeB);
							((CompositeImage)impNext).reset();
							((CompositeImage)impNext).copyLuts(impBs[pos]);
							impNext.setCalibration(impBs[pos].getCalibration());
							if (stageScan)
								stackAs[pos].setSkewXperZ(
										impNext.getCalibration().pixelDepth / impNext.getCalibration().pixelWidth);

							impBs[pos].flush();
							impBs[pos] = impNext;

							win.setImage(impBs[pos]);
							if (win instanceof StackWindow) {
								StackWindow sw = (StackWindow)win;
								int stackSize = impBs[pos].getStackSize();
								int nScrollbars = sw.getNScrollbars();
								sw.addScrollbars(impBs[pos]);
							}
							impBs[pos].getStack().setEdges(edgesB);
							impBs[pos].setPosition(cB, zB, (tailing || tB > impBs[pos].getNFrames())? impBs[pos].getNFrames() : tB);
							((CompositeImage)impBs[pos]).setMode(modeB);
							win.getCanvas().setMagnification(zoomB);
							winSize = win.getSize();
							win.pack();
							win.setSize(winSize);

						}
					}
					boolean wasSynched = false;
					ArrayList<ImagePlus> synchedImpsArrayList = new ArrayList<ImagePlus>();
					if (SyncWindows.getInstance() != null) {
						int v = 0;
						while (SyncWindows.getInstance().getImageFromVector(v) != null) {
							wasSynched = true;
							synchedImpsArrayList.add(SyncWindows.getInstance()
									.getImageFromVector(v));
							v++;
						}
						SyncWindows.getInstance().close();
					}


					if (wasSynched) {
						SyncWindows sw = new SyncWindows();
						for (ImagePlus impS : synchedImpsArrayList) {
							sw.addImp(impS);
						}
					}

				} else {

					listA = new File("" + dirOrOMETiff + "SPIMA").list();
					listB = new File("" + dirOrOMETiff + "SPIMB").list();
					big5DFileListAString = IJ.openAsString(dirOrOMETiff
							+ "Big5DFileListA.txt");
					big5DFileListBString = IJ.openAsString(dirOrOMETiff
							+ "Big5DFileListB.txt");
					deconList1 = (new File(dirOrOMETiff +"_Deconvolution1")).list();
					deconList2 = (new File(dirOrOMETiff +"_Deconvolution2")).list();

					while (monitoring && (fileListA.length == listA.length || fileListB.length == listB.length)
							&& (!doMipavDecon || ((deconList1 == null && deconList2 == null) || (!(deconList1 == null
							|| deconFileList1 == null || deconList1.length != deconFileList1.length) || !(deconList2 == null
							|| deconFileList2 == null || deconList2.length != deconFileList2.length))))) {
						if (IJ.escapePressed())
							if (!IJ.showMessageWithCancel(
									"Cancel diSPIM Monitor Updates?",
									"Monitoring of "
											+ dirOrOMETiff
											+ " paused by Escape.\nClick OK to resume."))
								return;
							else
								IJ.resetEscape();
						listA = new File("" + dirOrOMETiff + "SPIMA").list();
						listB = new File("" + dirOrOMETiff + "SPIMB").list();
						deconList1 = (new File(dirOrOMETiff +"Deconvolution1"))
								.list();
						deconList2 = (new File(dirOrOMETiff +"Deconvolution2"))
								.list();
						IJ.wait(5000);
					}
					//
					// if (isOpen("Display Channels")) {
					// selectWindow("Display Channels");
					// run("Close");
					// }
					//
					if (!monitoring) {
						continue;
					}

					IJ.log("NEW DATA WRITTEN");
					uploadPending = true;

					fileListA = new File("" + dirOrOMETiff + "SPIMA").list();
					fileListB = new File("" + dirOrOMETiff + "SPIMB").list();
					deconFileList1 = (new File(dirOrOMETiff +"_Deconvolution1"))
							.list();
					deconFileList2 = (new File(dirOrOMETiff +"_Deconvolution2"))
							.list();

					long modDateA = 0;
					String recentestA = "";
					for (int a = 0; a < fileListA.length; a++) {
						if (!fileListA[a].endsWith(".roi")
								&& !fileListA[a].endsWith(".DS_Store")) {
							if (modDateA < (new File(dirOrOMETiff + "SPIMA"
									+ File.separator + fileListA[a]))
									.lastModified()) {
								modDateA = (new File(dirOrOMETiff + "SPIMA"
										+ File.separator + fileListA[a]))
										.lastModified();
								recentestA = dirOrOMETiff + "SPIMA"
										+ File.separator + fileListA[a];
							}
						}
					}
					IJ.log(recentestA + "\n" + modDateA);
					if ((new File(recentestA)).isDirectory()) {
						String[] newTifList = { "" };
						while (newTifList.length < wavelengths * zSlices)
							newTifList = (new File(recentestA)).list();
						Arrays.sort(newTifList);
						for (int f = 0; f < newTifList.length; f++) {
							while (!(new File(dirOrOMETiff + "Big5DFileListA.txt")
							.exists()))
								IJ.wait(100);
							if (!newTifList[f].endsWith(".roi")
									&& !newTifList[f].endsWith(".DS_Store")
									&& big5DFileListAString.indexOf(recentestA
											+ newTifList[f]) < 0)
								IJ.append(recentestA + File.separator
										+ newTifList[f], dirOrOMETiff
										+ "Big5DFileListA.txt");
						}
					}

					fileListA = new File("" + dirOrOMETiff + "SPIMA").list();
					fileListB = new File("" + dirOrOMETiff + "SPIMB").list();

					long modDateB = 0;
					String recentestB = "";
					String recentestBname = "";
					for (int a = 0; a < fileListB.length; a++) {
						if (!fileListB[a].endsWith(".roi")
								&& !fileListB[a].endsWith(".DS_Store")) {
							if (modDateB < (new File(dirOrOMETiff + "SPIMB"
									+ File.separator + fileListB[a]))
									.lastModified()) {
								modDateB = (new File(dirOrOMETiff + "SPIMB"
										+ File.separator + fileListB[a]))
										.lastModified();
								recentestB = dirOrOMETiff + "SPIMB"
										+ File.separator + fileListB[a];
								recentestBname = fileListB[a];
							}
						}
					}
					IJ.log(recentestB + "\n" + modDateB);
					if (recentestBname.toLowerCase().startsWith("focus"))
						focus = true;
					if ((new File(recentestB)).isDirectory()) {
						String[] newTifList = { "" };
						while (newTifList.length < wavelengths * zSlices)
							newTifList = (new File(recentestB)).list();
						Arrays.sort(newTifList);
						for (int f = 0; f < newTifList.length; f++) {
							while (!(new File(dirOrOMETiff + "Big5DFileListB.txt")
							.exists()))
								IJ.wait(100);
							if (!newTifList[f].endsWith(".roi")
									&& !newTifList[f].endsWith(".DS_Store")
									&& big5DFileListBString.indexOf(recentestA
											+ newTifList[f]) < 0)
								IJ.append(recentestB + File.separator
										+ newTifList[f], dirOrOMETiff
										+ "Big5DFileListB.txt");
						}
					}
					boolean wasSynched = false;
					ArrayList<ImagePlus> synchedImpsArrayList = new ArrayList<ImagePlus>();
					if (SyncWindows.getInstance() != null) {
						int v = 0;
						while (SyncWindows.getInstance().getImageFromVector(v) != null) {
							wasSynched = true;
							synchedImpsArrayList.add(SyncWindows.getInstance()
									.getImageFromVector(v));
							v++;
						}
						SyncWindows.getInstance().close();
					}

					for (int pos=0; pos<pDim; pos++) {
						boolean go = true;
						if (posIntArray!=null){
							go = false;
							for (int posInt:posIntArray){
								if (posInt == pos){
									go=true;
								}
							}
						}
						if (!go){
							continue;
						}

						doProcessing[pos] = true;

						if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
							doProcessing[pos] = false;
							continue;
						}
						if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
							doProcessing[pos] = false;
							continue;
						} 

						if (doProcessing[pos]) {
							double pwA = impAs[pos].getCalibration().pixelWidth;
							double phA = impAs[pos].getCalibration().pixelHeight;
							double pdA = impAs[pos].getCalibration().pixelDepth;
							double pwB = impBs[pos].getCalibration().pixelWidth;
							double phB = impBs[pos].getCalibration().pixelHeight;
							double pdB = impBs[pos].getCalibration().pixelDepth;


							if (impAs[pos].hasNullStack())
								continue;
							if (impBs[pos].hasNullStack())
								continue;

							ImageWindow win = null;

							win = impAs[pos].getWindow();
							int cA = impAs[pos].getChannel();
							int zA = impAs[pos].getSlice();
							int tA = impAs[pos].getFrame();
							ListVirtualStack stackA = new ListVirtualStack(dirOrOMETiff
									+ "Big5DFileListA.txt");
							int stkNSlicesA = stackA.getSize();

							impAs[pos].setStack(stackA, wavelengths, zSlices, stkNSlicesA
									/ (wavelengths * zSlices));
							if (stageScan)
								impAs[pos].getStack().setSkewXperZ(
										-pdA
										/ pwA);
							impAs[pos].setPosition(cA, zA,
									tA == impAs[pos].getNFrames() - 1 ? impAs[pos].getNFrames() : tA);
							//impAs[pos].setWindow(win);
							win.setImage(impAs[pos]);


							win = impBs[pos].getWindow();
							int cB = impBs[pos].getChannel();
							int zB = impBs[pos].getSlice();
							int tB = impBs[pos].getFrame();
							ListVirtualStack stackB = new ListVirtualStack(dirOrOMETiff
									+ "Big5DFileListB.txt");
							int stkNSlicesB = stackB.getSize();

							impBs[pos].setStack(stackB, wavelengths, zSlices, stkNSlicesB
									/ (wavelengths * zSlices));
							if (stageScan)
								impBs[pos].getStack().setSkewXperZ(
										-pdB
										/ pwB);
							impBs[pos].setPosition(cB, zB,
									tB == impBs[pos].getNFrames() - 1 ? impBs[pos].getNFrames() : tB);
							//							impBs[pos].setWindow(win);
							win.setImage(impBs[pos]);

						}
					}
					if (wasSynched) {
						SyncWindows sw = new SyncWindows();
						for (ImagePlus impS : synchedImpsArrayList) {
							sw.addImp(impS);
						}
					}
				}
			} else if (dirOrOMETiff.matches(".*_\\d{9}_\\d{3}_.*.tif")) {

				int newLength = oldLength;
				while (monitoring && (oldLength == newLength
						|| newLength % (wavelengths * 2 * zSlices) != 0)) {

					IJ.wait(10);
					listB = new File(dirOrOMETiff).getParentFile().list();
					newLength = 0;
					for (String newFileListItem : listB)
						if (newFileListItem.endsWith(".tif"))
							newLength++;
				}
				if (!monitoring) {
					continue;
				}

				oldLength = newLength;
				boolean wasSynched = false;
				ArrayList<ImagePlus> synchedImpsArrayList = new ArrayList<ImagePlus>();
				if (SyncWindows.getInstance() != null) {
					int v = 0;
					while (SyncWindows.getInstance().getImageFromVector(v) != null) {
						wasSynched = true;
						synchedImpsArrayList.add(SyncWindows.getInstance()
								.getImageFromVector(v));
						v++;
					}
					SyncWindows.getInstance().close();
				}
				for (int pos=0; pos<pDim; pos++) {
					boolean go = true;
					if (posIntArray!=null){
						go = false;
						for (int posInt:posIntArray){
							if (posInt == pos){
								go=true;
							}
						}
					}
					if (!go){
						continue;
					}

					doProcessing[pos] = true;

					if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
						doProcessing[pos] = false;
						continue;
					}
					if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
						doProcessing[pos] = false;
						continue;
					} 

					if (doProcessing[pos]) {
						double pwA = impAs[pos].getCalibration().pixelWidth;
						double phA = impAs[pos].getCalibration().pixelHeight;
						double pdA = impAs[pos].getCalibration().pixelDepth;
						double pwB = impBs[pos].getCalibration().pixelWidth;
						double phB = impBs[pos].getCalibration().pixelHeight;
						double pdB = impBs[pos].getCalibration().pixelDepth;


						if (impAs[pos].hasNullStack())
							continue;
						if (impBs[pos].hasNullStack())
							continue;


						int cA = impAs[pos].getChannel();
						int zA = impAs[pos].getSlice();
						int tA = impAs[pos].getFrame();
						int cB = impBs[pos].getChannel();
						int zB = impBs[pos].getSlice();
						int tB = impBs[pos].getFrame();
						if (impAs[pos].isComposite())
							modeA = ((CompositeImage) impAs[pos]).getCompositeMode();
						if (impBs[pos].isComposite())
							modeB = ((CompositeImage) impBs[pos]).getCompositeMode();

						// IJ.run("Image Sequence...",
						// "open=["+dirOrOMETiff+"] number="+ newLength
						// +" starting=1 increment=1 scale=100 file=Cam2 or=[] sort use");
						FolderOpener foA = new FolderOpener();
						foA.openAsVirtualStack(true);
						foA.sortFileNames(true);
						foA.setFilter("Cam2");
						ImagePlus impTmpA = foA.openFolder(new File(dirOrOMETiff)
						.getParent());

						ImageWindow win = null;

						// NOT WORKING YET!!!!
						win = impAs[pos].getWindow();
						//						ColorModel cmA = impAs[pos].getProcessor().getColorModel();
						//						double dminA = impAs[pos].getProcessor().getMin();
						//						double dmaxA = impAs[pos].getProcessor().getMax();

						impAs[pos].setStack(impTmpA.getStack(), wavelengths, zSlices, impTmpA
								.getStack().getSize() / (wavelengths * zSlices));
						//						impAs[pos].getProcessor().setColorModel(cmA);
						//						impAs[pos].getProcessor().setMinAndMax(dminA, dmaxA);
						if (stageScan)
							impAs[pos].getStack().setSkewXperZ(
									-pdB
									/ pwB);

						impAs[pos].setPosition(cA, zA,
								tA == impAs[pos].getNFrames() - 1 ? impAs[pos].getNFrames() : tA);
						//impAs[pos].setWindow(win);
						win.setImage(impAs[pos]);


						// IJ.run("Image Sequence...",
						// "open=["+dirOrOMETiff+"] number="+ newLength
						// +" starting=1 increment=1 scale=100 file=Cam1 or=[] sort use");
						FolderOpener foB = new FolderOpener();
						foB.openAsVirtualStack(true);
						foB.sortFileNames(true);
						foB.setFilter("Cam1");
						ImagePlus impTmpB = foB.openFolder(new File(dirOrOMETiff)
						.getParent());

						win = impBs[pos].getWindow();
						//						ColorModel cmB = impBs[pos].getProcessor().getColorModel();
						//						double dminB = impBs[pos].getProcessor().getMin();
						//						double dmaxB = impBs[pos].getProcessor().getMax();

						impBs[pos].setStack(impTmpB.getStack(), wavelengths, zSlices, impTmpB
								.getStack().getSize() / (wavelengths * zSlices));
						//						impBs[pos].getProcessor().setColorModel(cmB);
						//						impBs[pos].getProcessor().setMinAndMax(dminB, dmaxB);
						if (stageScan)
							impBs[pos].getStack().setSkewXperZ(
									pdB
									/ pwB);

						impBs[pos].setPosition(cB, zB,
								tB == impBs[pos].getNFrames() - 1 ? impBs[pos].getNFrames() : tB);
						//impBs[pos].setWindow(win);
						win.setImage(impBs[pos]);

					}
				}
				if (wasSynched) {
					SyncWindows sw = new SyncWindows();
					for (ImagePlus impS : synchedImpsArrayList) {
						sw.addImp(impS);
					}
				}

			} else {		//megatiff files
				long fileOldMod = (new File(dirOrOMETiff)).lastModified();
				while (monitoring && fileOldMod == (new File(dirOrOMETiff)).lastModified()) {
					if (IJ.escapePressed())
						if (!IJ.showMessageWithCancel(
								"Cancel diSPIM Monitor Updates?",
								"Monitoring of "
										+ dirOrOMETiff
										+ " paused by Escape.\nClick OK to resume."))
							return;
						else
							IJ.resetEscape();
					IJ.wait(5000);
				}
				if (!monitoring) {
					continue;
				}

				IJ.log("NEW DATA WRITTEN");
				uploadPending = true;
				File dirOrOMETiffFile = new File(dirOrOMETiff);

				boolean wasSynched = false;
				ArrayList<ImagePlus> synchedImpsArrayList = new ArrayList<ImagePlus>();
				if (SyncWindows.getInstance() != null) {
					int v = 0;
					while (SyncWindows.getInstance().getImageFromVector(v) != null) {
						wasSynched = true;
						synchedImpsArrayList.add(SyncWindows.getInstance()
								.getImageFromVector(v));
						v++;
					}
					SyncWindows.getInstance().close();
				}
				for (int pos=0; pos<pDim; pos++) {
					boolean go = true;
					if (posIntArray!=null){
						go = false;
						for (int posInt:posIntArray){
							if (posInt == pos){
								go=true;
							}
						}
					}
					if (!go){
						continue;
					}

					doProcessing[pos] = true;

					if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
						doProcessing[pos] = false;
						continue;
					}
					if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
						doProcessing[pos] = false;
						continue;
					} 

					if (doProcessing[pos]) {

						if (impAs[pos].hasNullStack())
							continue;
						if (impBs[pos].hasNullStack())
							continue;


						TiffDecoder tdA = new TiffDecoder("", dirOrOMETiff);
						ImageWindow win = null;
						win = impAs[pos].getWindow();
						int cA = impAs[pos].getChannel();
						int zA = impAs[pos].getSlice();
						int tA = impAs[pos].getFrame();

						try {

							impAs[pos].setStack(new FileInfoVirtualStack(tdA.getTiffInfo(0),
									false));
							int stackSize = impAs[pos].getNSlices();
							int nChannels = wavelengths * 2;
							int nSlices = zSlices;
							int nFrames = (int) Math.floor((double) stackSize
									/ (nChannels * nSlices));

							//							impAs[pos] = new ImagePlus();
							//							impAs[pos].setTitle(dirOrOMETiffFile.getName() +"_Pos"+pos+ ": SPIMA");
							//							impBs[pos] = new ImagePlus();
							//							impBs[pos].setTitle(dirOrOMETiffFile.getName() +"_Pos"+pos+ ": SPIMB");
							//
							//							impAs[pos].setFileInfo(new FileInfo());
							//							impAs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
							//							impAs[pos].getOriginalFileInfo().directory = dirOrOMETiff;
							//
							//							impBs[pos].setFileInfo(new FileInfo());
							//							impBs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
							//							impBs[pos].getOriginalFileInfo().directory = dirOrOMETiff;

							if (nChannels * nSlices * nFrames != stackSize) {
								if (nChannels * nSlices * nFrames > stackSize) {
									for (int a = stackSize; a < nChannels * nSlices
											* nFrames; a++) {
										if (impAs[pos].getStack().isVirtual())
											((VirtualStack) impAs[pos].getStack())
											.addSlice("blank slice");
										else
											impAs[pos].getStack().addSlice(
													impAs[pos].getProcessor()
													.createProcessor(
															impAs[pos].getWidth(),
															impAs[pos].getHeight()));
									}
								} else if (nChannels * nSlices * nFrames < stackSize) {
									for (int a = nChannels * nSlices * nFrames; a < stackSize; a++) {
										impAs[pos].getStack().deleteSlice(
												nChannels * nSlices * nFrames);
									}
								} else {
									IJ.error("HyperStack Converter",
											"channels x slices x frames <> stack size");
									return;
								}
							}
							for (int t = nFrames - 1; t >= 0; t--) {
								for (int c = nChannels; c >= 1; c = c - 2) {
									for (int s = c * nSlices - 1; s >= (c - 1)
											* nSlices; s--) {
										int target = t * nChannels * nSlices + s + 1;
										impAs[pos].getStack().deleteSlice(target);
									}
								}
							}

							impAs[pos].setStack(impAs[pos].getImageStack());

							impAs[pos].setDimensions(wavelengths, nSlices, nFrames);

							if (nChannels > 1) {

								impAs[pos] = new CompositeImage(impAs[pos]);
								while (!impAs[pos].isComposite()) {
									IJ.wait(100);
									// selectWindow("SPIMB: "+dir);
								}
							}
							Calibration cal = impAs[pos].getCalibration();
							cal.pixelWidth = vWidth;
							cal.pixelHeight = vHeight;
							cal.pixelDepth = vDepthRaw;
							cal.setUnit(vUnit);
							if (stageScan)
								impAs[pos].getStack().setSkewXperZ(
										cal.pixelDepth / cal.pixelWidth);

							impAs[pos].setPosition(wavelengths, nSlices, nFrames);

							// impAs[pos].resetDisplayRange();
							impAs[pos].setPosition(1, nSlices / 2, nFrames / 2);
							// impAs[pos].resetDisplayRange();
							if (impAs[pos].isComposite())
								((CompositeImage) impAs[pos])
								.setMode(CompositeImage.COMPOSITE);
							impAs[pos].setFileInfo(new FileInfo());
							impAs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
							impAs[pos].getOriginalFileInfo().directory = dirOrOMETiff;

						} catch (IOException e) {
							e.printStackTrace();
						}

						impAs[pos].setPosition(cA, zA,
								tA == impAs[pos].getNFrames() - 1 ? impAs[pos].getNFrames() : tA);
						//impAs[pos].setWindow(win);
						win.setImage(impAs[pos]);


						TiffDecoder tdB = new TiffDecoder("", dirOrOMETiff);
						win = impBs[pos].getWindow();
						int cB = impBs[pos].getChannel();
						int zB = impBs[pos].getSlice();
						int tB = impBs[pos].getFrame();

						try {

							impBs[pos].setStack(new FileInfoVirtualStack(tdB.getTiffInfo(0),
									false));
							int stackSize = impBs[pos].getNSlices();
							int nChannels = wavelengths * 2;
							int nSlices = zSlices;
							int nFrames = (int) Math.floor((double) stackSize
									/ (nChannels * nSlices));


							if (nChannels * nSlices * nFrames != stackSize) {
								if (nChannels * nSlices * nFrames > stackSize) {
									for (int a = stackSize; a < nChannels * nSlices
											* nFrames; a++) {
										if (impBs[pos].getStack().isVirtual())
											((VirtualStack) impBs[pos].getStack())
											.addSlice("blank slice");
										else
											impBs[pos].getStack().addSlice(
													impBs[pos].getProcessor()
													.createProcessor(
															impBs[pos].getWidth(),
															impBs[pos].getHeight()));
									}
								} else if (nChannels * nSlices * nFrames < stackSize) {
									for (int a = nChannels * nSlices * nFrames; a < stackSize; a++) {
										impBs[pos].getStack().deleteSlice(
												nChannels * nSlices * nFrames);
									}
								} else {
									IJ.error("HyperStack Converter",
											"channels x slices x frames <> stack size");
									return;
								}
							}
							for (int t = nFrames - 1; t >= 0; t--) {
								for (int c = nChannels; c >= 1; c = c - 2) {
									for (int s = c * nSlices - 1; s >= (c - 1)
											* nSlices; s--) {
										int target = t * nChannels * nSlices + s + 1;
										impBs[pos].getStack().deleteSlice(target);
									}
								}
							}

							impBs[pos].setStack(impBs[pos].getImageStack());

							impBs[pos].setDimensions(wavelengths, nSlices, nFrames);

							if (nChannels > 1) {

								impBs[pos] = new CompositeImage(impBs[pos]);
								while (!impBs[pos].isComposite()) {
									IJ.wait(100);
									// selectWindow("SPIMB: "+dir);
								}
							}
							Calibration cal = impBs[pos].getCalibration();
							cal.pixelWidth = vWidth;
							cal.pixelHeight = vHeight;
							cal.pixelDepth = vDepthRaw;
							cal.setUnit(vUnit);
							if (stageScan)
								impBs[pos].getStack().setSkewXperZ(
										-cal.pixelDepth / cal.pixelWidth);

							impBs[pos].setPosition(wavelengths, nSlices, nFrames);

							// impBs[pos].resetDisplayRange();
							impBs[pos].setPosition(1, nSlices / 2, nFrames / 2);
							// impBs[pos].resetDisplayRange();
							if (impBs[pos].isComposite())
								((CompositeImage) impBs[pos])
								.setMode(CompositeImage.COMPOSITE);
							impBs[pos].setFileInfo(new FileInfo());
							impBs[pos].getOriginalFileInfo().fileName = dirOrOMETiff;
							impBs[pos].getOriginalFileInfo().directory = dirOrOMETiff;

						} catch (IOException e) {
							e.printStackTrace();
						}

						impBs[pos].setPosition(cB, zB,
								tB == impBs[pos].getNFrames() - 1 ? impBs[pos].getNFrames() : tB);
						//impBs[pos].setWindow(win);
						win.setImage(impBs[pos]);

					}
				}
				if (wasSynched) {
					SyncWindows sw = new SyncWindows();
					for (ImagePlus impS : synchedImpsArrayList) {
						sw.addImp(impS);
					}
				}
			}

			if (focus) {
				// SAD THAT I HAVE TO FAKE THIS, BUT NOT WORKING IN MY ATTEMPTS
				// AT JAVA-ONLY...
				String fftMacroString = "		    dir = \""
						+ dirOrOMETiff.replace("\\", "\\\\")
						+ "\";\n"
						+ "			autoFPath = dir+\"AutoFocusCommaSpace.txt\";"
						+ "		    print(nImages);\n"
						+ "		    File.delete(autoFPath);\n"
						+ "			autoFocusString = \"\";\n"
						+ "			for (i=1;i<=nImages;i++){\n"
						+ "				print(nImages+\" \"+i);\n"
						+ "				\n"
						+ "				setBatchMode(true);\n"
						+ "				selectImage(i);\n"
						+ "		\n"
						+ "				source = getTitle();\n"
						+ "				Stack.getDimensions(width, height, channels, zDepth, frames);\n"
						+ "				Stack.getPosition(channel, slice, frame);\n"
						+ "				Stack.setPosition(channel, slice, frames);\n"
						+ "				for (z=0; z<zDepth; z++) { \n"
						+ "					Stack.setSlice(z+1);\n"
						+ "					run(\"FFT, no auto-scaling\");\n"
						+ "					if (z==0) {\n"
						+ "						rename(\"FFTstack\");	\n"
						+ "					} else {\n"
						+ "						run(\"Select All\");\n"
						+ "						run(\"Copy\");\n"
						+ "						close();\n"
						+ "						selectWindow(\"FFTstack\");\n"
						+ "						run(\"Add Slice\");\n"
						+ "						if (z>0)\n"
						+ "							Stack.setSlice(z+2);\n"
						+ "						run(\"Select All\");\n"
						+ "						run(\"Paste\");\n"
						+ "					}\n"
						+ "					selectWindow(source);\n"
						+ "				}\n"
						+ "				Stack.setPosition(channel, slice, frame);\n"
						+ "				selectWindow(\"FFTstack\");\n"
						+ "				makeOval(250, 250, 13, 13);\n"
						+ "				run(\"Clear\", \"stack\");\n"
						+ "				makeOval(220, 220, 73, 73);\n"
						+ "				run(\"Clear Outside\", \"stack\");\n"
						+ "				run(\"Plot Z-axis Profile\");\n"
						+ "				close();\n"
						+ "				selectWindow(\"FFTstack\");\n"
						+ "				close();\n"
						+ "				\n"
						+ "				sliceAvgs = newArray(zDepth);\n"
						+ "				List.clear;\n"
						+ "				for (z=0; z<zDepth; z++) { \n"
						+ "					sliceAvgs[z] = getResult(\"Mean\", z);\n"
						+ "					//print(sliceAvgs[z] );\n"
						+ "					List.set(sliceAvgs[z] , z);\n"
						+ "				}\n"
						+ "				\n"
						+ "				Array.sort(sliceAvgs);\n"
						+ "				print(source+\": Best focus in slice \"+(parseInt(List.get(sliceAvgs[zDepth-1]))+1));\n"
						+ "				autoFocusString = autoFocusString + (parseInt(List.get(sliceAvgs[zDepth-1]))+1)+\", \";\n"
						+ "				selectWindow(\"Results\");\n"
						+ "				run(\"Close\");\n"
						+ "				setBatchMode(false);\n"
						+ "				selectWindow(source);\n"
						+ "				Stack.setPosition(channel, slice, frame);\n"
						+ "				updateDisplay();\n"
						+ "			}\n"
						+ "			File.saveString(autoFocusString, autoFPath);			\n"
						+ "";
				IJ.runMacro(fftMacroString);
			}


			String dirOrOMETiffDirectory = dirOrOMETiff;
			if (!new File(dirOrOMETiff).isDirectory())
				dirOrOMETiffDirectory = new File(dirOrOMETiff).getParent();
			final String dirOrOMETiffFinal = dirOrOMETiffDirectory;

//			if (autoUpload) {
//				Thread uploadMonitoringThread = new Thread(new Runnable() {
//					public void run() {
//						wgUploadJob = WG_Uploader.getInstance();
//						wgUploadJob.run(dirOrOMETiffFinal);
//											}
//				});
//				uploadMonitoringThread.start();
//			}


//			if (doGPUdecon) {			
//				processFilesByMinGuoDeconvolution();
//			}
//
//
//
//			if (doMipavDecon) {			
//				processFilesByMipavDeconvolution();
//			}
			//			}
		}
		if (rerunArg !="")
			run(rerunArg);
	}

	public void processFilesByMipavDeconvolution() {
		String[] frameFileNames = new String[impAs[0].getNFrames() + 1];

		for (int f = 1; f <= impAs[0].getNFrames(); f++) {
			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				doProcessing[pos] = true;

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()  || f>impAs[pos].getNFrames()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()  || f>impBs[pos].getNFrames()) {
					doProcessing[pos] = false;
					continue;
				} 

				if (doProcessing[pos]) {

					if (impAs[pos].hasNullStack())
						continue;
					if (impBs[pos].hasNullStack())
						continue;


					impAs[pos].setPositionWithoutUpdate(impAs[pos].getChannel(),
							impAs[pos].getSlice(), f);

					if (impAs[pos].getStack() instanceof ListVirtualStack)
						frameFileNames[f] = ((ListVirtualStack) impAs[pos].getStack())
						.getDirectory(impAs[pos].getCurrentSlice());
					else if (impAs[pos].getStack() instanceof FileInfoVirtualStack
							|| impAs[pos].getStack() instanceof MultiFileInfoVirtualStack)
						frameFileNames[f] = "t" + f;
					else
						frameFileNames[f] = "t" + f;
					//					timecode = "" + (new Date()).getTime();

					if (!(new File(savePath + "Pos"+pos+ "_SPIMA_Ch1_processed"
							+ File.separator + frameFileNames[f] + File.separator
							+ frameFileNames[f] + ".tif")).canRead()
							|| (wavelengths >= 2 && !(new File(savePath
									+ "Pos"+pos+ "_SPIMA_Ch2_processed" + File.separator
									+ frameFileNames[f] + File.separator
									+ frameFileNames[f] + ".tif")).canRead())
									|| !(new File(savePath + "Pos"+pos+ "_SPIMB_Ch1_processed"
											+ File.separator + frameFileNames[f]
													+ File.separator + frameFileNames[f] + ".tif"))
													.canRead()
													|| (wavelengths >= 2 && !(new File(savePath
															+ "Pos"+pos+ "_SPIMA_Ch2_processed" + File.separator
															+ frameFileNames[f] + File.separator
															+ frameFileNames[f] + ".tif")).canRead())) {
						IJ.runMacro("File.makeDirectory(\""
								+ savePath.replace("\\", "\\\\")
								+ "Pos"+pos+ "_SPIMA_Ch1_processed\");");
						IJ.runMacro("File.makeDirectory(\""
								+ savePath.replace("\\", "\\\\")
								+ "Pos"+pos+ "_SPIMA_Ch1_processed\"+File.separator+\""
								+ frameFileNames[f] + "\");");
						IJ.runMacro("File.makeDirectory(\""
								+ savePath.replace("\\", "\\\\")
								+ "Pos"+pos+ "_SPIMB_Ch1_processed\");");
						IJ.runMacro("File.makeDirectory(\""
								+ savePath.replace("\\", "\\\\")
								+ "Pos"+pos+ "_SPIMB_Ch1_processed\"+File.separator+\""
								+ frameFileNames[f] + "\");");
						IJ.runMacro("File.makeDirectory(\""
								+ savePath.replace("\\", "\\\\")
								+"Pos"+pos+ "_Deconvolution1\");");
						if (wavelengths >= 2) {
							IJ.runMacro("File.makeDirectory(\""
									+ savePath.replace("\\", "\\\\")
									+ "Pos"+pos+ "_SPIMA_Ch2_processed\");");
							IJ.runMacro("File.makeDirectory(\""
									+ savePath.replace("\\", "\\\\")
									+ "Pos"+pos+ "_SPIMA_Ch2_processed\"+File.separator+\""
									+ frameFileNames[f] + "\");");
							IJ.runMacro("File.makeDirectory(\""
									+ savePath.replace("\\", "\\\\")
									+ "Pos"+pos+ "_SPIMB_Ch2_processed\");");
							IJ.runMacro("File.makeDirectory(\""
									+ savePath.replace("\\", "\\\\")
									+ "Pos"+pos+ "_SPIMB_Ch2_processed\"+File.separator+\""
									+ frameFileNames[f] + "\");");
							IJ.runMacro("File.makeDirectory(\""
									+ savePath.replace("\\", "\\\\")
									+"Pos"+pos+ "_Deconvolution2\");");
						}

						ImageStack stackA1 = new ImageStack((int)cropWidthA[pos], (int)cropHeightA[pos]);
						ImageStack stackA2 = new ImageStack((int)cropWidthA[pos], (int)cropHeightA[pos]);
						wasEdgesA[pos] = impAs[pos].getStack().isEdges();
						impAs[pos].getWindow().setEnabled(false);
						for (int i = 1; i <= impAs[pos].getNSlices(); i++) {
							impAs[pos].setPositionWithoutUpdate(1, i, f);
							Roi impRoi = (Roi) origRoiAs[pos].clone();
							Polygon pA = new Polygon(impRoi.getPolygon().xpoints,
									impRoi.getPolygon().ypoints,
									impRoi.getPolygon().npoints);
							double fMax = impRoi.getBounds().width > impRoi
									.getBounds().height ? impRoi.getBounds().width
											: impRoi.getBounds().height;
									double angle = impRoi.getBounds().width > impRoi
											.getBounds().height ? 90 : 0;
									if (impRoi.getType() != Roi.RECTANGLE) {
										double[] fVals = impRoi.getFeretValues();
										fMax = fVals[0];
										angle = fVals[1];
									}
									Polygon pAR = pA;

									ImageProcessor ip1 = impAs[pos].getProcessor().duplicate();
									ip1.setRoi(
											Math.max((int) pAR.getBounds().x
													- ((int)cropWidthA[pos] - pAR.getBounds().width)
													/ 2, 0),
													Math.max((int) pAR.getBounds().y
															- ((int)cropHeightA[pos] - pAR.getBounds().height)
															/ 2, 0), (int)cropWidthA[pos], (int)cropHeightA[pos]);
									ip1 = ip1.crop();
									ImageProcessor ip1r = ip1.createProcessor((int)cropWidthA[pos],
											(int)cropHeightA[pos]);
									ip1r.insert(ip1, 0, 0);
									ip1 = ip1r;
									//									ip1.subtract(minLimit[0]);
									stackA1.addSlice(ip1);
									if (wavelengths >= 2) {
										impAs[pos].setPositionWithoutUpdate(wavelengths, i, f);
										ImageProcessor ip2 = impAs[pos].getProcessor()
												.duplicate();
										ip2.setRoi(
												Math.max(
														(int) pAR.getBounds().x
														- ((int)cropWidthA[pos] - pAR
																.getBounds().width)
																/ 2, 0),
																Math.max(
																		(int) pAR.getBounds().y
																		- ((int)cropHeightA[pos] - pAR
																				.getBounds().height)
																				/ 2, 0), (int)cropWidthA[pos],
																				(int)cropHeightA[pos]);
										ip2 = ip2.crop();
										ImageProcessor ip2r = ip2.createProcessor(
												(int)cropWidthA[pos], (int)cropHeightA[pos]);
										ip2r.insert(ip2, 0, 0);
										ip2 = ip2r;
										// ip2.subtract(minLimit[1]);
										stackA2.addSlice(ip2);
									}
						}
						impAs[pos].getWindow().setEnabled(true);
						ImagePlus impXA1 = new ImagePlus();
						impXA1.setStack(stackA1);
						impXA1.setCalibration(impAs[pos].getCalibration());
						IJ.saveAs(impXA1, "Tiff", savePath + "Pos"+pos+ "_SPIMA_Ch1_processed"
								+ File.separator + frameFileNames[f]
										+ File.separator + frameFileNames[f] + ".tif");
						if (wavelengths >= 2) {
							ImagePlus impXA2 = new ImagePlus();
							impXA2.setStack(stackA2);
							impXA2.setCalibration(impAs[pos].getCalibration());
							IJ.saveAs(impXA2, "Tiff", savePath
									+ "Pos"+pos+ "_SPIMA_Ch2_processed" + File.separator
									+ frameFileNames[f] + File.separator
									+ frameFileNames[f] + ".tif");
						}

						ImageStack stackB1 = new ImageStack((int)cropWidthB[pos], (int)cropHeightB[pos]);
						ImageStack stackB2 = new ImageStack((int)cropWidthB[pos], (int)cropHeightB[pos]);
						wasEdgesB[pos] = impBs[pos].getStack().isEdges();
						impBs[pos].getWindow().setEnabled(false);
						for (int i = 1; i <= impBs[pos].getNSlices(); i++) {
							impBs[pos].setPositionWithoutUpdate(1, i, f);
							Roi impRoi = (Roi) origRoiBs[pos].clone();
							Polygon pB = new Polygon(impRoi.getPolygon().xpoints,
									impRoi.getPolygon().ypoints,
									impRoi.getPolygon().npoints);
							double fMax = impRoi.getBounds().width > impRoi
									.getBounds().height ? impRoi.getBounds().width
											: impRoi.getBounds().height;
									double angle = impRoi.getBounds().width > impRoi
											.getBounds().height ? 90 : 0;
									if (impRoi.getType() != Roi.RECTANGLE) {
										double[] fVals = impRoi.getFeretValues();
										fMax = fVals[0];
										angle = fVals[1];
									}
									Polygon pBR = pB;

									ImageProcessor ip1 = impBs[pos].getProcessor().duplicate();
									ip1.setRoi(
											Math.max((int) pBR.getBounds().x
													- ((int)cropWidthB[pos] - pBR.getBounds().width)
													/ 2, 0),
													Math.max((int) pBR.getBounds().y
															- ((int)cropHeightB[pos] - pBR.getBounds().height)
															/ 2, 0), (int)cropWidthB[pos], (int)cropHeightB[pos]);
									ip1 = ip1.crop();

									ImageProcessor ip1r = ip1.createProcessor((int)cropWidthB[pos],
											(int)cropHeightB[pos]);
									ip1r.insert(ip1, 0, 0);
									ip1 = ip1r;
									// ip1.subtract(minLimit[2]);
									stackB1.addSlice(ip1);
									if (wavelengths >= 2) {
										impBs[pos].setPositionWithoutUpdate(wavelengths, i, f);
										ImageProcessor ip2 = impBs[pos].getProcessor()
												.duplicate();
										ip2.setRoi(
												Math.max(
														(int) pBR.getBounds().x
														- ((int)cropWidthB[pos] - pBR
																.getBounds().width)
																/ 2, 0),
																Math.max(
																		(int) pBR.getBounds().y
																		- ((int)cropHeightB[pos] - pBR
																				.getBounds().height)
																				/ 2, 0), (int)cropWidthB[pos],
																				(int)cropHeightB[pos]);
										ip2 = ip2.crop();
										ImageProcessor ip2r = ip2.createProcessor(
												(int)cropWidthB[pos], (int)cropHeightB[pos]);
										ip2r.insert(ip2, 0, 0);
										ip2 = ip2r;
										// ip2.subtract(minLimit[3]);
										stackB2.addSlice(ip2);
									}
						}
						impBs[pos].getWindow().setEnabled(true);
						ImagePlus impXB1 = new ImagePlus();
						impXB1.setStack(stackB1);
						impXB1.setCalibration(impBs[pos].getCalibration());
						IJ.saveAs(impXB1, "Tiff", savePath + "Pos"+pos+ "_SPIMB_Ch1_processed"
								+ File.separator + frameFileNames[f]
										+ File.separator + frameFileNames[f] + ".tif");
						if (wavelengths >= 2) {
							ImagePlus impXB2 = new ImagePlus();
							impXB2.setStack(stackB2);
							impXB2.setCalibration(impBs[pos].getCalibration());
							IJ.saveAs(impXB2, "Tiff", savePath
									+ "Pos"+pos+ "_SPIMB_Ch2_processed" + File.separator
									+ frameFileNames[f] + File.separator
									+ frameFileNames[f] + ".tif");
						}

					}


					final String[] frameFileNamesFinal = frameFileNames;

					impAs[pos].getStack().setEdges(wasEdgesA[pos]);
					impBs[pos].getStack().setEdges(wasEdgesB[pos]);
					impAs[pos].setPosition(wasChannelA[pos], wasSliceA[pos], wasFrameA[pos]);
					impBs[pos].setPosition(wasChannelB[pos], wasSliceB[pos], wasFrameB[pos]);

					final int ff = f;

					//					timecode = "" + (new Date()).getTime();
					final String ftimecode = timecode;

					if (!(new File(savePath +"Pos"+pos+ "_Deconvolution1" + File.separator
							+ "Decon_" + frameFileNames[f] + ".tif")).canRead()
							|| (wavelengths >= 2 && !(new File(savePath
									+"Pos"+pos+ "_Deconvolution2" + File.separator + "Decon_"
									+ frameFileNames[f] + ".tif")).canRead())) {
						String deconStringKey = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.182\", \"res_y double 0.182\", \"res_z double 1.0\", \"mtxFileDirectory string "
								+ savePath.replace("\\", "\\\\")
								+ "Pos"+pos+ "_SPIMB_Ch"
								+ keyChannel
								+ "_processed"
								+ File.separator.replace("\\", "\\\\")
								+ frameFileNames[f]
										+ "\", \"spimBFileDir string "
										+ savePath.replace("\\", "\\\\")
										+ "Pos"+pos+ "_SPIMA_Ch"
										+ keyChannel
										+ "_processed"
										+ File.separator.replace("\\", "\\\\")
										+ frameFileNames[f]
												+ "\", \"spimAFileDir string "
												+ savePath.replace("\\", "\\\\")
												+ "Pos"+pos+ "_SPIMB_Ch"
												+ keyChannel
												+ "_processed"
												+ File.separator.replace("\\", "\\\\")
												+ frameFileNames[f]
														+ "\", \"baseImage string "
														+ frameFileNames[f]
																//																+ "\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconv_platform int 2\", \"deconvDirString string "
																+ "\", \"base_rotation int -1\", \"transform_rotation int 4\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconv_platform int 2\", \"deconvDirString string "
																+ savePath.replace("\\", "\\\\")
																+"Pos"+pos+ "_Deconvolution"
																+ keyChannel
																+ "\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\", \"fusion_range string 1-1\")";
						IJ.wait(5000);

						new MacroRunner(
								"cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");"
										+ "cpuChunks = split(cpuPerformance,\"\\\"\");"
										+ "x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); "
										+ "while(x >30) {\n"
										+ "	wait(10000);"
										+ "	cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");"
										+ "	cpuChunks = split(cpuPerformance,\"\\\"\");"
										+ "	x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); "
										+ "}" + "print(\""
										+ frameFileNames[f]
												+ "_"
												+ keyChannel
												+ " processing...\");"
												+

								"			File.saveString(\'"
								+ deconStringKey
								+ "\', \""
								+ tempDir.replace("\\", "\\\\")
								+ "GenerateFusion1"
								+ frameFileNames[f]
										+ timecode
										+ ".sct\");"
										+

								"		    f = File.open(\""
								+ tempDir.replace("\\", "\\\\")
								+ "GenerateFusion1"
								+ frameFileNames[f]
										+ timecode
										+ ".bat\");\n"
										+ "		    batStringD = \"@echo off\";\n"
										+ "		    print(f,batStringD);\n"
										+ "		    batStringC = \"C\\:\";\n"
										+ "		    print(f,batStringC);\n"
										+ "		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n"
										+ "		    print(f,batStringA);\n"
										+ "		    batStringB = \"cmd64 /c mipav -s \\\""
										+ tempDir.replace("\\", "\\\\")
										+ "GenerateFusion1"
										+ frameFileNames[f]
												+ timecode
												+ ".sct\\\" -hide\";\n"
												+ "		    print(f,batStringB);\n"
												+ "		    print(f,\"exit\");\n"
												+ "		    File.close(f);	    \n"
												+

								"batJob = exec(\"cmd64\", \"/c\", \"start\", \"/low\", \"/min\", \"/wait\", \""
								+ tempDir.replace("\\", "\\\\")
								+ "GenerateFusion1"
								+ frameFileNames[f]
										+ timecode + ".bat\");" + "");

						final String finalConvPath = savePath
								+"Pos"+pos+ "_Deconvolution1\\Decon_" + frameFileNames[f]
										+ ".tif";
						Thread convThread = new Thread(new Runnable() {
							public void run() {
								while (!(new File(finalConvPath)).canRead()) {
									IJ.wait(10000);
								}
								IJ.wait(30000);
								new MacroRunner("print(\""
										+ frameFileNamesFinal[ff] + "_"
										+ keyChannel + " complete.\");"
										+ "delBat = File.delete(\""
										+ tempDir.replace("\\", "\\\\")
										+ "GenerateFusion1"
										+ frameFileNamesFinal[ff] + ftimecode
										+ ".bat\");" + "delSct = File.delete(\""
										+ tempDir.replace("\\", "\\\\")
										+ "GenerateFusion1"
										+ frameFileNamesFinal[ff] + ftimecode
										+ ".sct\");");

								//								ImagePlus convImp = IJ.openImage(finalConvPath);
								//								if (convImp != null) {
								//									IJ.saveAs(convImp, "TIFF", finalConvPath);
								//									convImp.close();
								//								}
							}
						});
						convThread.start();

						if (wavelengths >= 2) {
							String deconStringSlave = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.182\", \"res_y double 0.182\", \"res_z double 1.0\", \"mtxFileDirectory string "
									+ savePath.replace("\\", "\\\\")
									+ "Pos"+pos+ "_SPIMB_Ch"
									+ keyChannel
									+ "_processed"
									+ File.separator.replace("\\", "\\\\")
									+ frameFileNames[f]
											+ "\", \"spimBFileDir string "
											+ savePath.replace("\\", "\\\\")
											+ "Pos"+pos+ "_SPIMA_Ch"
											+ slaveChannel
											+ "_processed"
											+ File.separator.replace("\\", "\\\\")
											+ frameFileNames[f]
													+ "\", \"spimAFileDir string "
													+ savePath.replace("\\", "\\\\")
													+ "Pos"+pos+ "_SPIMB_Ch"
													+ slaveChannel
													+ "_processed"
													+ File.separator.replace("\\", "\\\\")
													+ frameFileNames[f]
															+ "\", \"baseImage string "
															+ frameFileNames[f]
																	//																	+ "\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconv_platform int 2\", \"deconvDirString string "
																	+ "\", \"base_rotation int -1\", \"transform_rotation int 4\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconv_platform int 2\", \"deconvDirString string "
																	+ savePath.replace("\\", "\\\\")
																	+"Pos"+pos+ "_Deconvolution"
																	+ slaveChannel
																	+ "\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\", \"fusion_range string 1-1\")";
							IJ.wait(5000);

							new MacroRunner(
									"print (\""
											+ savePath.replace("\\", "\\\\")
											+ "Pos"+pos+ "_SPIMB_Ch"
											+ keyChannel
											+ "_processed"
											+ File.separator.replace("\\", "\\\\")
											+ frameFileNames[f]
													+ File.separator.replace("\\", "\\\\")
													+ frameFileNames[f]
															+ "1_To_"
															+ frameFileNames[f]
																	+ ".mtx\");"
																	+ "while (!File.exists(\""
																	+ savePath.replace("\\", "\\\\")
																	+ "Pos"+pos+ "_SPIMB_Ch"
																	+ keyChannel
																	+ "_processed"
																	+ File.separator.replace("\\", "\\\\")
																	+ frameFileNames[f]
																			+ File.separator.replace("\\", "\\\\")
																			+ frameFileNames[f]
																					+ "1_To_"
																					+ frameFileNames[f]
																							+ ".mtx\")) {"
																							+ "wait(10000);"
																							+ "}"
																							+ "cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");"
																							+ "cpuChunks = split(cpuPerformance,\"\\\"\");"
																							+ "x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); "
																							+ "while(x >30) {\n"
																							+ "	wait(10000);"
																							+ "	cpuPerformance = exec(\"cmd64\",\"/c\",\"typeperf \\\"\\\\Processor(_Total)\\\\% Processor Time\\\" -sc 1\");"
																							+ "	cpuChunks = split(cpuPerformance,\"\\\"\");"
																							+ "	x = parseFloat(cpuChunks[lengthOf(cpuChunks)-2]); "
																							+ "}"
																							+ "print(\""
																							+ frameFileNames[f]
																									+ "_"
																									+ slaveChannel
																									+ " processing...\");"
																									+

									"			File.saveString(\'"
									+ deconStringSlave
									+ "\', \""
									+ tempDir.replace("\\", "\\\\")
									+ "GenerateFusion2"
									+ frameFileNames[f]
											+ timecode
											+ ".sct\");"
											+

									"		    f = File.open(\""
									+ tempDir.replace("\\", "\\\\")
									+ "GenerateFusion2"
									+ frameFileNames[f]
											+ timecode
											+ ".bat\");\n"
											+ "		    batStringD = \"@echo off\";\n"
											+ "		    print(f,batStringD);\n"
											+ "		    batStringC = \"C\\:\";\n"
											+ "		    print(f,batStringC);\n"
											+ "		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n"
											+ "		    print(f,batStringA);\n"
											+ "		    batStringB = \"cmd64 /c mipav -s \\\""
											+ tempDir.replace("\\", "\\\\")
											+ "GenerateFusion2" + frameFileNames[f]
													+ timecode + ".sct\\\" -hide\";\n"
													+ "		    print(f,batStringB);\n"
													+ "		    print(f,\"exit\");\n"
													+ "		    File.close(f);	    \n" +

									"batJob = exec(\"cmd64\", \"/c\", \"start\", \"/low\", \"/min\", \"/wait\", \""
									+ tempDir.replace("\\", "\\\\")
									+ "GenerateFusion2" + frameFileNames[f]
											+ timecode + ".bat\");" + "");

							final String finalConvPath2 = savePath
									+"Pos"+pos+ "_Deconvolution2\\Decon_" + frameFileNames[f]
											+ ".tif";
							Thread convThread2 = new Thread(new Runnable() {
								public void run() {
									while (!(new File(finalConvPath2)).canRead()) {
										IJ.wait(10000);
									}
									IJ.wait(30000);
									new MacroRunner("print(\""
											+ frameFileNamesFinal[ff] + "_"
											+ slaveChannel + " complete.\");"
											+ "delBat = File.delete(\""
											+ tempDir.replace("\\", "\\\\")
											+ "GenerateFusion2"
											+ frameFileNamesFinal[ff] + ftimecode
											+ ".bat\");"
											+ "delSct = File.delete(\""
											+ tempDir.replace("\\", "\\\\")
											+ "GenerateFusion2"
											+ frameFileNamesFinal[ff] + ftimecode
											+ ".sct\");");

									//									ImagePlus convImp = IJ
									//											.openImage(finalConvPath2);
									//									if (convImp != null) {
									//										IJ.saveAs(convImp, "TIFF", finalConvPath2);
									//										convImp.close();
									//									}
								}
							});
							convThread2.start();
						}
					}
					// IJ.wait(15000);
				}
			}
		}
	}

	public void processFilesByMinGuoDeconvolution() {
		//START PROCESSING ALREADY SAVED RAW DATA 
		if (!orientBeforeLineage) {

			if (new File(savePath+"fineRotations.txt").canRead()){
				String rotFileText = IJ.openAsString(savePath+"fineRotations.txt");
				String[] rotFileLines = rotFileText.split("\n");
				for (String line:rotFileLines){
					String[] rotLineChunks = line.split(",");
					if (rotLineChunks.length==4){
						rotXYZs[Integer.parseInt(rotLineChunks[0])][0] = Double.parseDouble(rotLineChunks[1]);
						rotXYZs[Integer.parseInt(rotLineChunks[0])][1] = Double.parseDouble(rotLineChunks[2]);
						rotXYZs[Integer.parseInt(rotLineChunks[0])][2] = Double.parseDouble(rotLineChunks[3]);
					}
				}
			}
		}
		if (wavelengths >=1) {
			new File("" + savePath + "CropBkgdSub").mkdirs();
			new File("" + savePath + "RegDecon" + File.separator + "TMX").mkdirs();
			if (doSerialRegPriming){
			}
		}
		if (doForceDefaultRegTMX || doSerialRegPriming){
			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}
				
				doProcessing[pos] = true;

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible() ) {
					doProcessing[pos] = false;
					continue;
				} 
				
				lastMatrix[pos] = IJ.openAsString("C:\\DataForTest\\Matrix_0.tmx");

				if (new File("" + savePath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(0, 4)+".tmx").canRead() )
					lastMatrix[pos] = IJ.openAsString("" + savePath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(0, 4)+".tmx");
				else 
					IJ.saveString(lastMatrix[pos], "" + savePath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(0, 4)+".tmx");
			}
		}
		for (int posForPathSetup=0; posForPathSetup<pDim; posForPathSetup++) {
			new File("" + savePath + "RegDecon" + File.separator + "Pos"+ posForPathSetup + File.separator +"Deconvolution1").mkdirs();
			if (wavelengths >= 2) {
				new File("" + savePath + "RegDecon" + File.separator + "Pos"+ posForPathSetup + File.separator +"Deconvolution2").mkdirs();
			}
		}

		final int[] posFrameStart = new int[pDim];
		final int[] posFrameEnd = new int[pDim];

		for (int pos=0; pos<pDim; pos++) {
			boolean go = true;
			if (posIntArray!=null){
				go = false;
				for (int posInt:posIntArray){
					if (posInt == pos){
						go=true;
					}
				}
			}
			if (!go || !doProcessing[pos]){
				continue;
			}
			
			tDim = impAs[pos].getNFrames();
			posFrameStart[pos] = impAs[pos].getFrame();
			
			posFrameEnd[pos] = impBs[pos].getFrame();
			
			
			//ADD XROT PREVIEW CODE HERE?????
			
			
		}
		final String[][] frameFileNames = new String[pDim][tDim+1];

		Thread prepStacksThread = new Thread(null, new Runnable(){
			
			public void run() {
				for (int f = (orientBeforeLineage?tDim:1); (orientBeforeLineage?(f>=1):(f<=tDim)); f=(orientBeforeLineage?(f-1):(f+1))) {
					for (int pos=0; pos<pDim; pos++) {
						boolean go = true;
						if (posIntArray!=null){
							go = false;
							for (int posInt:posIntArray){
								if (posInt == pos){
									go=true;
								}
							}
						}

						if (orientBeforeLineage && (posFrameStart[pos]!=f && posFrameEnd[pos]!=f)) {
							go = false;
						}else if (posFrameStart[pos]>f || posFrameEnd[pos]<f) {
							go = false;
						} else{
//							IJ.log("else");
						}
						if (!go){
							continue;
						}
						
						doProcessing[pos] = true;

						if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible() || f>impAs[pos].getNFrames()) {
							doProcessing[pos] = false;
							IJ.log("! no winorstackA "+f+" "+pos);
							continue;
						}
						if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible() || f>impBs[pos].getNFrames()) {
							doProcessing[pos] = false;
							IJ.log("! no winorstackB "+f+" "+pos);
							continue;
						} 
						if(go) {
							wasFrameA[pos] = impAs[pos].getFrame();
							wasFrameB[pos] = impBs[pos].getFrame();
							wasSliceA[pos] = impAs[pos].getSlice();
							wasSliceB[pos] = impBs[pos].getSlice();
							wasChannelA[pos] = impAs[pos].getChannel();
							wasChannelB[pos] = impBs[pos].getChannel();
						}
						if (!IJ.shiftKeyDown()){
							if (impPrxs[pos]!=null && impPrys[pos]!=null && impAs[pos].getNFrames() == impPrxs[pos].getNFrames() && impAs[pos].getNFrames() == impPrys[pos].getNFrames()
									&& impBs[pos].getNFrames() == impPrxs[pos].getNFrames() && impBs[pos].getNFrames() == impPrys[pos].getNFrames()) {
								doProcessing[pos] = false;
								IJ.log("no unfinishedViewss "+f+" "+pos);
								continue;
							}
						}

						int longAxisLength = (int)longAxesA[pos];
						if (doProcessing[pos]) {
							double pwA = impAs[pos].getCalibration().pixelWidth;
							double phA = impAs[pos].getCalibration().pixelHeight;
							double pdA = impAs[pos].getCalibration().pixelDepth;
							double pwB = impBs[pos].getCalibration().pixelWidth;
							double phB = impBs[pos].getCalibration().pixelHeight;
							double pdB = impBs[pos].getCalibration().pixelDepth;



							if (impAs[pos].hasNullStack() || impAs[pos].getNFrames()<f-1)
								continue;
							if (impBs[pos].hasNullStack())
								continue;

							depthSkipFactor = ((int)(pdB/phB)) ;
							
							
							if (new File(savePath + "RegDecon" + File.separator  + "Pos"+ pos + File.separator +"Deconvolution1" + File.separator + "Pos" + pos + "_Decon_t"+ IJ.pad(f, 4)+".tif").canRead() 
									&& new File(savePath + "RegDecon" + File.separator  + "Pos"+ pos + File.separator +"Deconvolution2" + File.separator + "Pos" + pos + "_Decon_t"+ IJ.pad(f, 4)+".tif").canRead()){
								continue;
							}
							if (new File(savePath + "CropBkgdSub" + File.separator  + "SPIMA"+pos+"-"+f+"-1_1.tif").canRead() &&
									new File(savePath + "CropBkgdSub" + File.separator  + "SPIMB"+pos+"-"+f+"-1_1.tif").canRead() &&
									new File(savePath + "CropBkgdSub" + File.separator  + "SPIMA"+pos+"-"+f+"-2_1.tif").canRead() &&
									new File(savePath + "CropBkgdSub" + File.separator  + "SPIMB"+pos+"-"+f+"-2_1.tif").canRead()){ 
							
								continue;
							}

							impAs[pos].setPositionWithoutUpdate(impAs[pos].getChannel(), impAs[pos].getSlice(), f);

							if (impAs[pos].getStack() instanceof ListVirtualStack)
								frameFileNames[pos][f] = ((ListVirtualStack) impAs[pos].getStack())
								.getDirectory(impAs[pos].getCurrentSlice());
							else if (impAs[pos].getStack() instanceof FileInfoVirtualStack
									|| impAs[pos].getStack() instanceof MultiFileInfoVirtualStack)
								frameFileNames[pos][f] = "t" + f;
							else
								frameFileNames[pos][f] = "t" + f;
							//							timecode = "" + (new Date()).getTime();

							stackA1 = new ImageStack(longAxisLength, longAxisLength);
							stackA2 = new ImageStack(longAxisLength, longAxisLength);
							wasEdgesA[pos] = impAs[pos].getStack().isEdges();
							impAs[pos].getWindow().setEnabled(false);

							 
							stackA1.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
							stackA2.addSlice(new ShortProcessor(longAxisLength, longAxisLength));

							double maxBkgd1 = 0.0;
							double maxBkgd2 = 0.0;
							for (int i = zFirstA[pos]; i <= zLastA[pos]; i++) {
								impAs[pos].setPositionWithoutUpdate(1, i, f);
								impRoiA = (Roi) origRoiAs[pos].clone();
								pA = new Polygon(impRoiA.getPolygon().xpoints,
										impRoiA.getPolygon().ypoints,
										impRoiA.getPolygon().npoints);
								double fMax = impRoiA.getBounds().width > impRoiA.getBounds().height ? impRoiA.getBounds().width: impRoiA.getBounds().height;
								double angle = impRoiA.getBounds().width > impRoiA.getBounds().height ? 90 : 0;
								if (impRoiA.getType() != Roi.RECTANGLE) {
									double[] fVals = impRoiA.getFeretValues();
									fMax = fVals[0];
									angle = fVals[1];
								}
								pAR = pA;

								ipA1 = impAs[pos].getProcessor().duplicate();

								int[] ipHis = ipA1.getHistogram();
								double ipHisMode = 0.0;
								int ipHisLength = ipHis.length;
								int ipHisMaxBin = 0;
								for (int h=0; h<ipHisLength; h++) {
									if (ipHis[h] > ipHisMaxBin) {
										ipHisMaxBin = ipHis[h];
										ipHisMode = (double)h;
									}
								}
								//							if (maxBkgd1 < ipHisMode )
								//								maxBkgd1 = ipHisMode;
								ipA1.subtract(ipHisMode * sliceTresholdVsModeA);
								ipA1.setRoi((Roi) rectRoiAs[pos]);
								ipA1.fillOutside((Roi) rectRoiAs[pos]);
								ipA1 = ipA1.crop();
								ipA1r = ipA1.createProcessor((int)cropWidthA[pos]+2, (int)cropHeightA[pos]+2);
								ipA1r.insert(ipA1, 1, 1);
								ipA1 = ipA1r;

//								stackA1.addSlice(ipA1);							
								stackA1.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
								stackA1.getProcessor(stackA1.getSize()).insert(ipA1, (longAxisLength/2)-(ipA1.getWidth()/2), (longAxisLength/2)-(ipA1.getHeight()/2));

								if (wavelengths >= 2) {
									impAs[pos].setPositionWithoutUpdate(wavelengths, i, f);
									ipA2 = impAs[pos].getProcessor().duplicate();
									ipHis = ipA2.getHistogram();
									ipHisMode = 0.0;
									ipHisLength = ipHis.length;
									ipHisMaxBin = 0;
									for (int h=0; h<ipHisLength; h++) {
										if (ipHis[h] > ipHisMaxBin) {
											ipHisMaxBin = ipHis[h];
											ipHisMode = (double)h;
										}
									}

									//								if (maxBkgd2 < ipHisMode )
									//									maxBkgd2 = ipHisMode;
									ipA2.subtract(ipHisMode * sliceTresholdVsModeA);
									ipA2.setRoi((Roi) rectRoiAs[pos]);
									ipA2.fillOutside((Roi) rectRoiAs[pos]);
									ipA2 = ipA2.crop();
									ipA2r = ipA2.createProcessor((int)cropWidthA[pos]+2, (int)cropHeightA[pos]+2);
									ipA2r.insert(ipA2, 1, 1);
									ipA2 = ipA2r;
//									stackA2.addSlice(ipA2);
									stackA2.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
									stackA2.getProcessor(stackA2.getSize()).insert(ipA2, (longAxisLength/2)-(ipA2.getWidth()/2), (longAxisLength/2)-(ipA2.getHeight()/2));
								}
							}

							stackA1.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
							stackA2.addSlice(new ShortProcessor(longAxisLength, longAxisLength));

							impAs[pos].getWindow().setEnabled(true);
							impXA1 = new ImagePlus();
							impXA1.setStack(stackA1);
							if (diSPIM_MM_ComputerName!=null && diSPIM_MM_ComputerName.startsWith("diSPIM-HP")) {
								//THERE IS A LONG STORY BEHIND THIS CHOICE OF TWO OPTION...SHROFF LAB WHIMS...
//								IJ.run(impXA1, "Rotate 90 Degrees Left", "stack");
							}
							impXA1.setCalibration(impAs[pos].getCalibration());
							IJ.saveAs(impXA1, "Tiff", savePath + "CropBkgdSub" + File.separator + "SPIMA"+pos+"-"+f+"-1_1.tif");
							while (!(new File(savePath + "CropBkgdSub" + File.separator + "SPIMA"+pos+"-"+f+"-1_1.tif").canRead())){
								IJ.wait(100);
							}
							if (wavelengths >= 2) {
								impXA2 = new ImagePlus();
								impXA2.setStack(stackA2);
								if (diSPIM_MM_ComputerName!=null && diSPIM_MM_ComputerName.startsWith("diSPIM-HP")) {
									//THERE IS A LONG STORY BEHIND THIS CHOICE OF TWO OPTION...SHROFF LAB WHIMS...
//									IJ.run(impXA2, "Rotate 90 Degrees Left", "stack");
								}
								impXA2.setCalibration(impAs[pos].getCalibration());
								IJ.saveAs(impXA2, "Tiff", savePath + "CropBkgdSub" + File.separator + "SPIMA"+pos+"-"+f+"-2_1.tif");
								while (!(new File(savePath + "CropBkgdSub" + File.separator + "SPIMA"+pos+"-"+f+"-2_1.tif").canRead())){
									IJ.wait(100);
								}

							}
							
							
							
							longAxisLength = (int)longAxesB[pos];

							stackB1 = new ImageStack(longAxisLength, longAxisLength);
							stackB2 = new ImageStack(longAxisLength, longAxisLength);
							wasEdgesB[pos] = impBs[pos].getStack().isEdges();
							impBs[pos].getWindow().setEnabled(false);

							stackB1.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
							stackB2.addSlice(new ShortProcessor(longAxisLength, longAxisLength));

							for (int i = zFirstB[pos]; i <= zLastB[pos]; i++) {
								impBs[pos].setPositionWithoutUpdate(1, i, f);
								impRoiB = (Roi) rectRoiBs[pos].clone();
								pB = new Polygon(impRoiB.getPolygon().xpoints,
										impRoiB.getPolygon().ypoints,
										impRoiB.getPolygon().npoints);
								double fMax = impRoiB.getBounds().width > impRoiB
										.getBounds().height ? impRoiB.getBounds().width
												: impRoiB.getBounds().height;
										double angle = impRoiB.getBounds().width > impRoiB
												.getBounds().height ? 90 : 0;
										if (impRoiB.getType() != Roi.RECTANGLE) {
											double[] fVals = impRoiB.getFeretValues();
											fMax = fVals[0];
											angle = fVals[1];
										}
										pBR = pB;

										ipB1 = impBs[pos].getProcessor().duplicate();
										int[] ipHis = ipB1.getHistogram();
										double ipHisMode = 0.0;
										int ipHisLength = ipHis.length;
										int ipHisMaxBin = 0;
										for (int h=0; h<ipHisLength; h++) {
											if (ipHis[h] > ipHisMaxBin) {
												ipHisMaxBin = ipHis[h];
												ipHisMode = (double)h;
											}
										}
										//									if (maxBkgd1 < ipHisMode )
										//										maxBkgd1 = ipHisMode;
										ipB1.subtract(ipHisMode * sliceTresholdVsModeB);
										ipB1.setRoi((Roi) rectRoiBs[pos]);
										ipB1.fillOutside((Roi) rectRoiBs[pos]);
										ipB1 = ipB1.crop();

										ipB1r = ipB1.createProcessor((int)cropWidthB[pos]+2, (int)cropHeightB[pos]+2);
										ipB1r.insert(ipB1, 1, 1);
										ipB1 = ipB1r;
										// ip1.subtract(minLimit[2]);
//										stackB1.addSlice(ipB1);

										stackB1.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
										stackB1.getProcessor(stackB1.getSize()).insert(ipB1, (longAxisLength/2)-(ipB1.getWidth()/2), (longAxisLength/2)-(ipB1.getHeight()/2));

										if (wavelengths >= 2) {
											impBs[pos].setPositionWithoutUpdate(wavelengths, i, f);
											ipB2 = impBs[pos].getProcessor().duplicate();
											ipHis = ipB2.getHistogram();
											ipHisMode = 0.0;
											ipHisLength = ipHis.length;
											ipHisMaxBin = 0;
											for (int h=0; h<ipHisLength; h++) {
												if (ipHis[h] > ipHisMaxBin) {
													ipHisMaxBin = ipHis[h];
													ipHisMode = (double)h;
												}
											}

											//										if (maxBkgd2 < ipHisMode )
											//											maxBkgd2 = ipHisMode;
											ipB2.subtract(ipHisMode * sliceTresholdVsModeB);
											ipB2.setRoi((Roi) rectRoiBs[pos]);
											ipB2.fillOutside((Roi) rectRoiBs[pos]);
											ipB2 = ipB2.crop();
											ipB2r = ipB2.createProcessor((int)cropWidthB[pos]+2, (int)cropHeightB[pos]+2);
											ipB2r.insert(ipB2, 1, 1);
											ipB2 = ipB2r;
											// ip2.subtract(minLimit[3]);
//											stackB2.addSlice(ipB2);
											stackB2.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
											stackB2.getProcessor(stackB2.getSize()).insert(ipB2, (longAxisLength/2)-(ipB2.getWidth()/2), (longAxisLength/2)-(ipB2.getHeight()/2));

										}
							}

							stackB1.addSlice(new ShortProcessor(longAxisLength, longAxisLength));
							stackB2.addSlice(new ShortProcessor(longAxisLength, longAxisLength));

							impBs[pos].getWindow().setEnabled(true);
							impXB1 = new ImagePlus();
							impXB1.setStack(stackB1);
							if (diSPIM_MM_ComputerName!=null && diSPIM_MM_ComputerName.startsWith("diSPIM-HP")) {
								//THERE IS A LONG STORY BEHIND THIS CHOICE OF TWO OPTION...SHROFF LAB WHIMS...
//								IJ.run(impXB1, "Flip Horizontally", "stack");
//								IJ.run(impXB1, "Rotate 90 Degrees Right", "stack");
							}
							impXB1.setCalibration(impBs[pos].getCalibration());
							IJ.saveAs(impXB1, "Tiff", savePath + "CropBkgdSub" + File.separator + "SPIMB"+pos+"-"+f+"-1_1.tif");
							while (!(new File(savePath + "CropBkgdSub" + File.separator + "SPIMB"+pos+"-"+f+"-1_1.tif").canRead())){
								IJ.wait(100);
							}
							if (wavelengths >= 2) {
								impXB2 = new ImagePlus();
								impXB2.setStack(stackB2);
								if (diSPIM_MM_ComputerName!=null && diSPIM_MM_ComputerName.startsWith("diSPIM-HP")) {
									//THERE IS A LONG STORY BEHIND THIS CHOICE OF TWO OPTION...SHROFF LAB WHIMS...
//									IJ.run(impXB2, "Flip Horizontally", "stack");
//									IJ.run(impXB2, "Rotate 90 Degrees Right", "stack");
								}
								impXB2.setCalibration(impBs[pos].getCalibration());
								IJ.saveAs(impXB2, "Tiff", savePath + "CropBkgdSub" + File.separator + "SPIMB"+pos+"-"+f+"-2_1.tif");
								while (!(new File(savePath + "CropBkgdSub" + File.separator + "SPIMB"+pos+"-"+f+"-2_1.tif").canRead())){
									IJ.wait(100);
								}

							}




							final String[][] frameFileNamesFinal = frameFileNames;

							impAs[pos].setPosition(wasChannelA[pos], wasSliceA[pos], wasFrameA[pos]);
							impAs[pos].updateAndDraw();
							impBs[pos].setPosition(wasChannelB[pos], wasSliceB[pos], wasFrameB[pos]);
							impBs[pos].updateAndDraw();
							impAs[pos].getStack().setEdges(wasEdgesA[pos]);
							impBs[pos].getStack().setEdges(wasEdgesB[pos]);

							//							if (f==1)
							//								IJ.wait(5000);

						}
					}
				}
			}
			
		}, "prepStacksThread");
		prepStacksThread.start();
		
		
		Thread deconFuseThread = new Thread(null, new Runnable(){

			private File output3DxFile;
			private File output3DyFile;
			private File outputMaxxFile;
			private File outputMaxyFile;
			private File outputMaxzFile;

			public void run() {		
				for (int f = (orientBeforeLineage?tDim:1); (orientBeforeLineage?(f>=1):(f<=tDim)); f=(orientBeforeLineage?(f-1):(f+1))) {

					int[] failIteration = new int[pDim];
					Arrays.fill(failIteration, 0);

					for (int pos=0; pos<pDim; pos++) {
						if (doSerialRegPriming){
							failIteration[pos] = 1;
						}
						if (doPrimeFromDefaultRegTMX){
							failIteration[pos] = 2;
						}
						if (doForceDefaultRegTMX){
							failIteration[pos] = 3;
						}
					}
					for (int pos=0; pos<pDim; pos++) {

						boolean doForceDefaultRegTMXiterated = false;
						boolean doPrimeFromDefaultRegTMXiterated = false;
						boolean doSerialRegPrimingIterated = false;
						if (failIteration[pos]==0){
							doForceDefaultRegTMXiterated = false;
							doPrimeFromDefaultRegTMXiterated = false;
							doSerialRegPrimingIterated = false;
						}
						if (failIteration[pos]==1){
							doForceDefaultRegTMXiterated = false;
							doPrimeFromDefaultRegTMXiterated = false;
							doSerialRegPrimingIterated = true;
						}
						if (failIteration[pos]==2){
							doForceDefaultRegTMXiterated = false;
							doPrimeFromDefaultRegTMXiterated = true;
							doSerialRegPrimingIterated = false;
						}
						if (failIteration[pos]==3){
							doForceDefaultRegTMXiterated = true;
							doPrimeFromDefaultRegTMXiterated = false;
							doSerialRegPrimingIterated = false;
						}
						if (failIteration[pos]==4){
							continue;
						}

						boolean go = true;
						if (posIntArray!=null){
							go = false;
							for (int posInt:posIntArray){
								if (posInt == pos){
									go=true;
								}
							}
						}

						if (orientBeforeLineage && (posFrameStart[pos]!=f && posFrameEnd[pos]!=f)) {
							go = false;
						}else if (posFrameStart[pos]>f || posFrameEnd[pos]<f) {
							go = false;
						} else{
//							IJ.log("else");
						}
						if (!go){
							continue;
						}

						doProcessing[pos] = true;

						if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible() || f>impAs[pos].getNFrames()) {
							doProcessing[pos] = false;
							IJ.log("! no winorstackA "+f+" "+pos);
							continue;
						}
						if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible() || f>impBs[pos].getNFrames()) {
							doProcessing[pos] = false;
							IJ.log("! no winorstackB "+f+" "+pos);
							continue;
						} 
						if(go) {
							wasFrameA[pos] = impAs[pos].getFrame();
							wasFrameB[pos] = impBs[pos].getFrame();
							wasSliceA[pos] = impAs[pos].getSlice();
							wasSliceB[pos] = impBs[pos].getSlice();
							wasChannelA[pos] = impAs[pos].getChannel();
							wasChannelB[pos] = impBs[pos].getChannel();
						}
						if (!IJ.shiftKeyDown()){
							if (impPrxs[pos]!=null && impPrys[pos]!=null && impAs[pos].getNFrames() == impPrxs[pos].getNFrames() && impAs[pos].getNFrames() == impPrys[pos].getNFrames()
									&& impBs[pos].getNFrames() == impPrxs[pos].getNFrames() && impBs[pos].getNFrames() == impPrys[pos].getNFrames()) {
								doProcessing[pos] = false;
								IJ.log("no unfinishedViewss "+f+" "+pos);
								continue;
							}
						}

						if (doProcessing[pos]) {
							IJ.log("failIteration[pos]" + failIteration[pos] + " pos"+pos + " t"+f);

							String saveDFPath = savePath;
							while (!(new File(saveDFPath  + "CropBkgdSub" + File.separator + "SPIMA"+pos+"-"+f+"-1_1.tif").canRead()) ||
									!(new File(saveDFPath + "CropBkgdSub" + File.separator + "SPIMA"+pos+"-"+f+"-2_1.tif").canRead()) ||
									!(new File(saveDFPath + "CropBkgdSub" + File.separator + "SPIMB"+pos+"-"+f+"-1_1.tif").canRead()) ||
									!(new File(saveDFPath + "CropBkgdSub" + File.separator + "SPIMB"+pos+"-"+f+"-2_1.tif").canRead()) ){
								IJ.wait(100);
							}
							double pwA = impAs[pos].getCalibration().pixelWidth;
							double phA = impAs[pos].getCalibration().pixelHeight;
							double pdA = impAs[pos].getCalibration().pixelDepth;
							double pwB = impBs[pos].getCalibration().pixelWidth;
							double phB = impBs[pos].getCalibration().pixelHeight;
							double pdB = impBs[pos].getCalibration().pixelDepth;



							if (impAs[pos].hasNullStack() || impAs[pos].getNFrames()<f-1)
								continue;
							if (impBs[pos].hasNullStack())
								continue;


							int fi=0;
							dc1File = new File(saveDFPath + "RegDecon" + File.separator  + "Pos"+ pos + File.separator +"Deconvolution1" + File.separator + "Pos" + pos + "_Decon_t"+ IJ.pad(f, 4)+".tif");
							dc2File = new File(saveDFPath + "RegDecon" + File.separator  + "Pos"+ pos + File.separator +"Deconvolution2" + File.separator + "Pos" + pos + "_Decon_t"+ IJ.pad(f, 4)+".tif");

							if ((wavelengths ==1 && dc1File.canRead())
									||
									(wavelengths >= 2 && dc1File.canRead()
									&& dc2File.canRead())) {
								IJ.log("already done: " + pos +" "+ impAs[pos].getChannel()+" "+ impAs[pos].getSlice()+" "+ f);
								continue;
							} else {
								IJ.log("starting " + pos +" "+ impAs[pos].getChannel()+" "+ impAs[pos].getSlice()+" "+ f);

								int type = origRoiBs[pos].getType();
								int[] xpoints = origRoiBs[pos].getPolygon().xpoints;
								int[] ypoints = origRoiBs[pos].getPolygon().ypoints;
								if (keyView=="A"){
									 type = origRoiAs[pos].getType();
									 xpoints = origRoiAs[pos].getPolygon().xpoints;
									 ypoints = origRoiAs[pos].getPolygon().ypoints;
								}

								int npoints = xpoints.length;

								double xRotAngle = rotXYZs[pos][0];
								double yRotAngle =  rotXYZs[pos][1];
								double zRotAngle =  rotXYZs[pos][2];
								if (type > Roi.OVAL) {
									if (npoints == 4) {
										double angleZero = new Line(xpoints[0], ypoints[0], xpoints[1], ypoints[1]).getAngle();
										double angleTwo = new Line(xpoints[2], ypoints[2], xpoints[3], ypoints[3]).getAngle();

										double angleZeroPlusPi = angleZero + 180;
										double angleTwoPlusPi = angleTwo + 180;

										double angleDelta = angleZeroPlusPi%180 - angleTwoPlusPi%180;

										if ( Math.abs(angleDelta)  >80 && Math.abs(angleDelta)  <100) {
											haveAxesRoiDF = true;
											zRotAngle = zRotAngle + angleZero;

										}else {
											zRotAngle = zRotAngle + new Line(xpoints[0], ypoints[0], xpoints[2], ypoints[2]).getAngle();
										}
									} else {
										zRotAngle = zRotAngle + new Line(xpoints[0], ypoints[0], xpoints[npoints/2], ypoints[npoints/2]).getAngle();
									}
								}


								for (fi=1; fi<=f; fi++) {
									tmxFile = new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(f-fi, 4)+".tmx");
									if (tmxFile.canRead() ){
										lastMatrix[pos] = IJ.openAsString("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(f-fi, 4)+".tmx");
										break;
									}
								}

								final int ff = f;

								//							timecode = "" + (new Date()).getTime();
								final String ftimecode = timecode;

								try {
									while (regDeconProcess!= null && regDeconProcess.isAlive()) {
										IJ.wait(100);
									}
									//							} catch (IOException e) {
									//								// TODO Auto-generated catch block
									//								e.printStackTrace();
								}finally {
									if (regDeconProcess!= null) {
										IJ.log("rdpExit="+regDeconProcess.exitValue());
										try {
											if (regDeconProcess.getOutputStream() != null) {
												regDeconProcess.getOutputStream().close();
											}
										} catch (final IOException ioe) {
											// ignore
										}
										try {
											if (regDeconProcess.getInputStream() != null) {
												regDeconProcess.getInputStream().close();
											}
										} catch (final IOException ioe) {
											// ignore
										}
										try {
											if (regDeconProcess.getErrorStream() != null) {
												regDeconProcess.getErrorStream().close();
											}
										} catch (final IOException ioe) {
											// ignore
										}
										regDeconProcess.destroyForcibly();
									}
								}

								String savePrxPath = saveDFPath  + "ProjX_"+("Decon-Fuse_"
										+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
								String savePryPath = saveDFPath  + "ProjY_"+("Decon-Fuse_"
										+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
								String saveMaxXPath = saveDFPath  + "MaxX_"+("Decon-Fuse_"
										+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
								String saveMaxYPath = saveDFPath  + "MaxY_"+("Decon-Fuse_"
										+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
								String saveMaxZPath = saveDFPath  + "MaxZ_"+("Decon-Fuse_"
										+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";

								//						IJ.log(savePrxPath);
								//						IJ.log(savePryPath);
								prxColor1File = new File(savePrxPath+File.separator+"Color1");
								pryColor1File = new File(savePryPath+File.separator+"Color1");
								maxXColor1File = new File(saveMaxXPath+File.separator+"Color1");
								maxYColor1File = new File(saveMaxYPath+File.separator+"Color1");
								maxZColor1File = new File(saveMaxZPath+File.separator+"Color1");
								prxColor1File.mkdirs();
								pryColor1File.mkdirs();
								maxXColor1File.mkdirs();
								maxYColor1File.mkdirs();
								maxZColor1File.mkdirs();

								if (wavelengths >= 2) {
									prxColor2File = new File(savePrxPath+File.separator+"Color2");
									pryColor2File = new File(savePryPath+File.separator+"Color2");
									maxXColor2File = new File(saveMaxXPath+File.separator+"Color2");
									maxYColor2File = new File(saveMaxYPath+File.separator+"Color2");
									maxZColor2File = new File(saveMaxZPath+File.separator+"Color2");
									prxColor2File.mkdirs();
									pryColor2File.mkdirs(); 
									maxXColor2File.mkdirs();
									maxYColor2File.mkdirs();
									maxZColor2File.mkdirs();
								}
								String threeDorientationIndex= ""+abRelOriValue;

								//							String threeDorientationIndex= "1";
								//							if (diSPIM_MM_ComputerName!=null && diSPIM_MM_ComputerName.startsWith("diSPIM-HP")) {
								//								threeDorientationIndex =  "-1";
								//							}							

								outputDCFile = new File(saveDFPath + "RegDecon" + File.separator + "Decon" + File.separator + "Decon_1.tif");
								output3DxFile = new File(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Xaxis"+ File.separator +"MP_3D_Xaxis_1.tif");
								output3DyFile = new File(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Yaxis"+ File.separator +"MP_3D_Yaxis_1.tif");
								outputMaxxFile = new File(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_XProj"+ File.separator +"MP_YZ_1.tif");
								outputMaxyFile = new File(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_YProj"+ File.separator +"MP_ZX_1.tif");
								outputMaxzFile = new File(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_ZProj"+ File.separator +"MP_XY_1.tif");

								
								if (wavelengths == 1) {
									
									if (!new File(saveDFPath + "CropBkgdSub" + File.separator  + "SPIMA"+pos+"-"+f+"-1_1.tif").canRead() ||
											!new File(saveDFPath + "CropBkgdSub" + File.separator  + "SPIMB"+pos+"-"+f+"-1_1.tif").canRead()){ 
										IJ.wait(100);
									}
									try {
										String[] cmdln =null;
										if (keyView == "B"){
											if (lineageDecons)
												cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-1_","SPIMA"+pos+"-"+f+"-1_", saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
											else
												cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-1_","SPIMA"+pos+"-"+f+"-1_", saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
										}else{
											if (lineageDecons)
												cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-1_","SPIMB"+pos+"-"+f+"-1_", saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
											else
												cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-1_","SPIMB"+pos+"-"+f+"-1_", saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
										}

										regDeconProcess = Runtime.getRuntime().exec(cmdln);
										
										while (regDeconProcess!= null && regDeconProcess.isAlive()) {
											IJ.wait(100);
										}


									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}finally {
										if (regDeconProcess!= null) {
											IJ.log("rdpExit="+regDeconProcess.exitValue());
											try {
												if (regDeconProcess.getOutputStream() != null) {
													regDeconProcess.getOutputStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											try {
												if (regDeconProcess.getInputStream() != null) {
													regDeconProcess.getInputStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											try {
												if (regDeconProcess.getErrorStream() != null) {
													regDeconProcess.getErrorStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											regDeconProcess.destroyForcibly();

										}
									}
									try {
										int waitCount = 0;
										while (!(new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}
										IJ.log(""+ pos+ "old=> " + lastMatrix[pos]);

										if(!(new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").canRead())) {
											//NEED TO INCREMENT FROM FRESH TO PRIME TO FORCE WITH LAST
											failIteration[pos]++;
											pos--;
											continue;
										} else {
											lastMatrix[pos] = IJ.openAsString("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx");
											if (Double.parseDouble(lastMatrix[pos].split("\\t")[10])< 0.8){
												IJ.log("Bah Humbug");
												failIteration[pos]++;
												pos--;
												continue;
											}else{
												IJ.log("Merry Xmas");
												if (orientBeforeLineage) {
													Files.copy(Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx"),
															Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ "0000"+".tmx"), StandardCopyOption.REPLACE_EXISTING);												
												}
											}
										}
										
										
										
										waitCount = 0;
										while (!(outputDCFile.canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}

										if(outputDCFile.canRead()) {
											Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon" + File.separator + "Decon_1.tif"),
													Paths.get(saveDFPath + "RegDecon" + File.separator  + "Pos"+ pos + File.separator +"Deconvolution1" + File.separator + "Pos" + pos + "_Decon_t"+ IJ.pad(f, 4)+".tif"), StandardCopyOption.REPLACE_EXISTING);

											//											IJ.runMacro("waitForUser;");
											if (new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").canRead()) {
												lastMatrix[pos] = IJ.openAsString("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx");
												//												IJ.saveString(lastMatrix[pos], "" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx");
												Files.move(Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx"),
														Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx"), StandardCopyOption.REPLACE_EXISTING);												IJ.log(""+ pos+ "new=> " + lastMatrix[pos]);
													
												new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").delete();
												IJ.log(""+ pos+ "new=> " + lastMatrix[pos]);
											}

										} else {
											posFrameEnd[pos]--;
										}

										int k = f;

										waitCount = 0;
										while (!(output3DxFile.canRead() && output3DyFile.canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}

										if (output3DxFile.canRead() && output3DyFile.canRead()) {
											try {
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Xaxis"+ File.separator +"MP_3D_Xaxis_1.tif"),
														Paths.get(savePrxPath+File.separator +"Color1"+ File.separator +"projX_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Yaxis"+ File.separator +"MP_3D_Yaxis_1.tif"),
														Paths.get(savePryPath+File.separator +"Color1"+ File.separator +"projY_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_XProj"+ File.separator +"MP_YZ_1.tif"),
														Paths.get(saveMaxXPath+File.separator +"Color1"+ File.separator +"maxX_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_YProj"+ File.separator +"MP_ZX_1.tif"),
														Paths.get(saveMaxYPath+File.separator +"Color1"+ File.separator +"maxY_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_ZProj"+ File.separator +"MP_XY_1.tif"),
														Paths.get(saveMaxZPath+File.separator +"Color1"+ File.separator +"maxZ_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}

										}

									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}


								}


								//	NEW TWO CALL VERSION TO REUSE FIT ON CH 2						
								if (wavelengths >= 2) {
									if (!new File(saveDFPath + "CropBkgdSub" + File.separator  + "SPIMA"+pos+"-"+f+"-1_1.tif").canRead() ||
											!new File(saveDFPath + "CropBkgdSub" + File.separator  + "SPIMB"+pos+"-"+f+"-1_1.tif").canRead() ||
											!new File(saveDFPath + "CropBkgdSub" + File.separator  + "SPIMA"+pos+"-"+f+"-2_1.tif").canRead() ||
											!new File(saveDFPath + "CropBkgdSub" + File.separator  + "SPIMB"+pos+"-"+f+"-2_1.tif").canRead()){
										IJ.wait(100);
									}
									try {
										String[] cmdln = {""};
										if (keyChannel ==1){
											if (keyView == "B"){
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-1_","SPIMA"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-1_","SPIMA"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}else{
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-1_","SPIMB"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-1_","SPIMB"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}
										}else{
											if (keyView == "B"){
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-2_","SPIMA"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-2_","SPIMA"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}else{
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-2_","SPIMB"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-2_","SPIMB"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,(doForceDefaultRegTMXiterated?"4":"1"), threeDorientationIndex,"0", ((doForceDefaultRegTMXiterated || doPrimeFromDefaultRegTMXiterated || (doSerialRegPrimingIterated  && f>1))?"1":"0"),saveDFPath + "RegDecon" + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ (doPrimeFromDefaultRegTMXiterated?"0000":IJ.pad(f-fi, 4))+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}
										}
										//								IJ.log(cmdln);
										IJ.log(Arrays.toString(cmdln));
										regDeconProcess = Runtime.getRuntime().exec(cmdln);

										while (regDeconProcess!= null && regDeconProcess.isAlive()) {
											IJ.wait(100);
										}

									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}finally {
										if (regDeconProcess!= null) {
											IJ.log("rdpExit="+regDeconProcess.exitValue());
											try {
												if (regDeconProcess.getOutputStream() != null) {
													regDeconProcess.getOutputStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											try {
												if (regDeconProcess.getInputStream() != null) {
													regDeconProcess.getInputStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											try {
												if (regDeconProcess.getErrorStream() != null) {
													regDeconProcess.getErrorStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											regDeconProcess.destroyForcibly();

										}
									}

									try {
										int waitCount = 0;
										while (!(new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}
										IJ.log(""+ pos+ "old=> " + lastMatrix[pos]);

										if(!(new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").canRead())) {
//											NEED TO INCREMENT FROM FRESH TO PRIME TO FORCE WITH LAST
											failIteration[pos]++;
											pos--;
											continue;
										} else {
											lastMatrix[pos] = IJ.openAsString("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx");
											if (lastMatrix[pos].startsWith("Error") || Double.parseDouble(lastMatrix[pos].split("\\t")[10])< 0.8){
												IJ.log("Bah Humbug");
												failIteration[pos]++;
												pos--;
												continue;
											}else{
												IJ.log("Merry Xmas");
												if (orientBeforeLineage) {
													Files.copy(Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx"),
															Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ "0000"+".tmx"), StandardCopyOption.REPLACE_EXISTING);												
												}
											}
										}
										
										
										waitCount = 0;
										while (!(outputDCFile.canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}

										if(outputDCFile.canRead()) {
											Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon" + File.separator + "Decon_1.tif"),
													Paths.get(saveDFPath + "RegDecon" + File.separator  + "Pos"+ pos + File.separator +"Deconvolution"+ keyChannel + File.separator + "Pos" + pos + "_Decon_t"+ IJ.pad(f, 4)+".tif"), StandardCopyOption.REPLACE_EXISTING);

											//											IJ.runMacro("waitForUser;");
											if (new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").canRead()) {
												lastMatrix[pos] = IJ.openAsString("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx");
//												IJ.saveString(lastMatrix[pos], "" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx");
												Files.move(Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx"),
														Paths.get("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx"), StandardCopyOption.REPLACE_EXISTING);												
												new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").delete();
												IJ.log(""+ pos+ "new=> " + lastMatrix[pos]);
											}

										} else {
											posFrameEnd[pos]--;
										}

										int k = f;

										waitCount = 0;
										while (!(output3DxFile.canRead() && output3DyFile.canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}

										if (output3DxFile.canRead() && output3DyFile.canRead()) {
											try {
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Xaxis"+ File.separator +"MP_3D_Xaxis_1.tif"),
														Paths.get(savePrxPath+File.separator +"Color"+ keyChannel+ File.separator +"projX_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Yaxis"+ File.separator +"MP_3D_Yaxis_1.tif"),
														Paths.get(savePryPath+File.separator +"Color"+ keyChannel+ File.separator +"projY_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_XProj"+ File.separator +"MP_YZ_1.tif"),
														Paths.get(saveMaxXPath+File.separator +"Color"+ keyChannel+ File.separator +"maxX_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_YProj"+ File.separator +"MP_ZX_1.tif"),
														Paths.get(saveMaxYPath+File.separator +"Color"+ keyChannel+ File.separator +"maxY_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_ZProj"+ File.separator +"MP_XY_1.tif"),
														Paths.get(saveMaxZPath+File.separator +"Color"+ keyChannel+ File.separator +"maxZ_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}

										}

									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									//NOW REG slavechannel Using new key channel tmx
									try {
										String[] cmdln = {""};
										if (keyChannel ==1){
											if (keyView == "B"){
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-2_","SPIMA"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-2_","SPIMA"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}else{
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-2_","SPIMB"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-2_","SPIMB"+pos+"-"+f+"-2_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}
										}else{
											if (keyView == "B"){
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-1_","SPIMA"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMB"+pos+"-"+f+"-1_","SPIMA"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwB, ""+phB,""+pdB, ""+pwA, ""+phA,""+pdA,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}else{
												if (lineageDecons)
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion_withRotationOptions_zyx.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-1_","SPIMB"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0",""+zRotAngle,""+yRotAngle,""+xRotAngle};
												else
													cmdln = new String[] {"cmd","/c","start","/min","/wait","C:\\spimfusion.exe", saveDFPath + "CropBkgdSub" + File.separator  , saveDFPath + "CropBkgdSub" + File.separator  , "SPIMA"+pos+"-"+f+"-1_","SPIMB"+pos+"-"+f+"-1_"  , saveDFPath + "RegDecon" + File.separator ,"1","1","1","1",""+pwA, ""+phA,""+pdA, ""+pwB, ""+phB,""+pdB,"4",threeDorientationIndex,"0", (doSerialRegPrimingIterated?"1":"1"),saveDFPath + "RegDecon"  + File.separator +"TMX" +File.separator+"RegMatrix_Pos"+pos+"_t"+ IJ.pad(f, 4)+".tmx" , "0","0.0001" , ""+iterations , "16","C:\\DataForTest\\PSFA128.tif","C:\\DataForTest\\PSFB128.tif","1","0"};
											}
										}
										//								IJ.log(cmdln);
										IJ.log(Arrays.toString(cmdln));
										regDeconProcess = Runtime.getRuntime().exec(cmdln);
										while (regDeconProcess!= null && regDeconProcess.isAlive()) {
											IJ.wait(100);
										}
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}finally {
										if (regDeconProcess!= null) {
											IJ.log("rdpExit="+regDeconProcess.exitValue());
											try {
												if (regDeconProcess.getOutputStream() != null) {
													regDeconProcess.getOutputStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											try {
												if (regDeconProcess.getInputStream() != null) {
													regDeconProcess.getInputStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											try {
												if (regDeconProcess.getErrorStream() != null) {
													regDeconProcess.getErrorStream().close();
												}
											} catch (final IOException ioe) {
												// ignore
											}
											regDeconProcess.destroyForcibly();

										}
									}
									try {
										int waitCount = 0;
										while (!(outputDCFile.canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}

										new File("" + saveDFPath + "RegDecon" + File.separator + "TMX" + File.separator + "Matrix_1.tmx").delete();

										if(outputDCFile.canRead()) {
											
											Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon" + File.separator + "Decon_1.tif"),
													Paths.get(saveDFPath + "RegDecon" + File.separator  + "Pos"+ pos + File.separator +"Deconvolution"+ slaveChannel + File.separator + "Pos" + pos + "_Decon_t"+ IJ.pad(f, 4)+".tif"), StandardCopyOption.REPLACE_EXISTING);

										}



										int k = f;

										waitCount = 0;
										while (!(output3DxFile.canRead() && output3DyFile.canRead()) && waitCount<100) {
											IJ.wait(100);
											waitCount++;
										}

										if (output3DxFile.canRead() && output3DyFile.canRead()) {
											try {
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Xaxis"+ File.separator +"MP_3D_Xaxis_1.tif"),
														Paths.get(savePrxPath+File.separator +"Color"+ slaveChannel+ File.separator +"projX_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_3D_Yaxis"+ File.separator +"MP_3D_Yaxis_1.tif"),
														Paths.get(savePryPath+File.separator +"Color"+ slaveChannel+ File.separator +"projY_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_XProj"+ File.separator +"MP_YZ_1.tif"),
														Paths.get(saveMaxXPath+File.separator +"Color"+ slaveChannel+ File.separator +"maxX_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_YProj"+ File.separator +"MP_ZX_1.tif"),
														Paths.get(saveMaxYPath+File.separator +"Color"+ slaveChannel+ File.separator +"maxY_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
												Files.move(Paths.get(saveDFPath + "RegDecon" + File.separator + "Decon"+ File.separator +"MP_ZProj"+ File.separator +"MP_XY_1.tif"),
														Paths.get(saveMaxZPath+File.separator +"Color"+ slaveChannel+ File.separator +"maxZ_"+pos+"_"+k+".tif"), StandardCopyOption.REPLACE_EXISTING);
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}

										}


									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
								IJ.log("finishing " + pos +" "+ impAs[pos].getChannel()+" "+ impAs[pos].getSlice()+" "+ f);
							}
						}
					}
				}
			}
		}, "deconFuseThread");
		deconFuseThread.start();
		
		
		
		Thread updateWindowsAndWriteSNFFilesThread = new Thread(null, new Runnable(){

			public void run() {		
				snfCycleComplete=false;
				boolean posAllDone = false;
				while (!posAllDone){	

					posAllDone=true;
					for (int pos=0; pos<pDim; pos++) {
						boolean go = true;
						if (posIntArray!=null){
							go = false;
							for (int posInt:posIntArray){
								if (posInt == pos){
									go=true;
								}
							}
						}

						//						if (posFrameStart[pos]>f || posFrameEnd[pos]<f) {
						//							go = false;
						//						}
						if (!go){
							continue;
						}

						doProcessing[pos] = true;

						if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible() || posFrameEnd[pos]>impAs[pos].getNFrames()) {
							doProcessing[pos] = false;
							continue;
						}
						if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible() || posFrameEnd[pos]>impBs[pos].getNFrames()) {
							doProcessing[pos] = false;
							continue;
						} 
						if(go) {
							wasFrameA[pos] = impAs[pos].getFrame();
							wasFrameB[pos] = impBs[pos].getFrame();
							wasSliceA[pos] = impAs[pos].getSlice();
							wasSliceB[pos] = impBs[pos].getSlice();
							wasChannelA[pos] = impAs[pos].getChannel();
							wasChannelB[pos] = impBs[pos].getChannel();
						}

						if (doProcessing[pos]) {

							double pwA = impAs[pos].getCalibration().pixelWidth;
							double phA = impAs[pos].getCalibration().pixelHeight;
							double pdA = impAs[pos].getCalibration().pixelDepth;
							double pwB = impBs[pos].getCalibration().pixelWidth;
							double phB = impBs[pos].getCalibration().pixelHeight;
							double pdB = impBs[pos].getCalibration().pixelDepth;



							if (impAs[pos].hasNullStack())
								continue;
							if (impBs[pos].hasNullStack())
								continue;


							FilenameFilter tiffNameFilter = new FilenameFilter() {
								@Override
								public boolean accept(File dir, String name) {
									return name.toLowerCase().endsWith(".tif");
								}
							};

							String openDFPath = savePath;
							saveFile = new File(openDFPath );
							if ((saveFile).canRead()) {
								// SETUP OF WINDOWS SHOWING NEW AND PRE-EXISTING DECON OUTPUTS

								stackDFs[pos] = null;
								File deconmfivsPathFile = new File(openDFPath+ File.separator + "RegDecon" + File.separator + "Pos" + pos);

								if (deconmfivsPathFile.canRead() 
										&& new File(openDFPath + File.separator + "RegDecon" + File.separator + "Pos" + pos, "Deconvolution1").list(tiffNameFilter).length>0
										){

									if (wavelengths == 2){
										while (new File(openDFPath + File.separator + "RegDecon" + File.separator + "Pos" + pos, "Deconvolution1").list(tiffNameFilter).length
												!= new File(openDFPath + File.separator + "RegDecon" + File.separator + "Pos" + pos, "Deconvolution2").list(tiffNameFilter).length){
											IJ.wait(100);
										}
									}
									stackDFs[pos] = new MultiFileInfoVirtualStack(openDFPath
												+ File.separator + "RegDecon" + File.separator + "Pos" + pos, "Deconvolution",
												false);
									
								}

								if (stackDFs[pos]!=null && stackDFs[pos].getSize() > 0 && (impDF1s[pos]==null || stackDFs[pos].getSize() > impDF1s[pos].getStack().getSize())) {
									ImageWindow win = null;
									if (impDF1s[pos] != null){
										impDF1s[pos].getRoiManager().setImagePlus(null);
										impDF1s[pos].getRoiManager().dispose();
										impDF1s[pos].setRoiManager(null);
									} else {
										impDF1s[pos] = new ImagePlus();
									}
									impDF1s[pos].setStack((orientBeforeLineage?"Preview_":"") + "Decon-Fuse_"
											+ impAs[pos].getTitle().split(":")[0], stackDFs[pos]);
									impDF1s[pos].setFileInfo(new FileInfo());
									impDF1s[pos].getCalibration().setUnit(impAs[pos].getCalibration().getUnit());
									impDF1s[pos].getCalibration().pixelWidth = pwA;
									impDF1s[pos].getCalibration().pixelHeight = phA;
									impDF1s[pos].getCalibration().pixelDepth = pwA;  //isotropic!

									impDF1s[pos].getOriginalFileInfo().directory = dirOrOMETiffFinal;
									int stkNSlicesDF = impDF1s[pos].getStackSize();
									int zSlicesDF1 = ((MultiFileInfoVirtualStack)impDF1s[pos].getStack()).getFivStacks().get(0)
											.getSize();
									impDF1s[pos].setOpenAsHyperStack(true);
									impDF1s[pos].setStack(impDF1s[pos].getStack(), outputWavelengths,
											zSlicesDF1, stkNSlicesDF
											/ (outputWavelengths * zSlicesDF1));
									if (ciDFs[pos] != null) {
										win = ciDFs[pos].getWindow();
									} else {
										win = null;
									}

									ciDFs[pos] = new CompositeImage(impDF1s[pos]);


									if (wavelengths > 1)
										ciDFs[pos].setMode(CompositeImage.COMPOSITE);
									else
										ciDFs[pos].setMode(CompositeImage.GRAYSCALE);

									if (win==null) {
										ciDFs[pos].show();
										WindowManager.group(impBs[pos], ciDFs[pos]);
										ciDFs[pos].setPosition(1, ciDFs[pos].getNSlices()/2, ciDFs[pos].getNFrames());
										win = ciDFs[pos].getWindow();
									}else {
										int oldW = win.getWidth();
										int oldH = win.getHeight();
										int oldC = win.getImagePlus().getChannel();
										int oldZ = win.getImagePlus().getSlice();
										int oldT = win.getImagePlus().getFrame();
										double oldMag = win.getCanvas().getMagnification();
										double oldMin = win.getImagePlus()
												.getDisplayRangeMin();
										double oldMax = win.getImagePlus()
												.getDisplayRangeMax();
										boolean oldEdges = win.getImagePlus().getStack().isEdges();
										ciDFs[pos].copyLuts(win.getImagePlus());

										//									win.setVisible(false);	
										win.setImage(ciDFs[pos]);
										ciDFs[pos].setWindow(win);
										win.updateImage(ciDFs[pos]);
										win.getImagePlus().getStack().setEdges(oldEdges);
										win.setSize(oldW, oldH);
										((StackWindow) win).addScrollbars(ciDFs[pos]);
										win.getCanvas().setMagnification(oldMag);
										win.getImagePlus().updateAndDraw();
										win.getImagePlus().setPosition(oldC, oldZ, oldT);
										win.getImagePlus().setDisplayRange(oldMin, oldMax);
//										win.setSize(win.getSize().width,
//												win.getSize().height);

										//									win.setVisible(true);	
									}

									String openPrxPath = openDFPath  + "ProjX_"+("Decon-Fuse_"
											+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
									String openPryPath = openDFPath  + "ProjY_"+("Decon-Fuse_"
											+ impAs[pos].getTitle().split(":")[0]).replaceAll("[,. ;:]","").replace(File.separator, "_") + "_FullVolume";
									if (new File(openPryPath).canRead() ){
										while (new File(openPryPath, "Color1").list(tiffNameFilter).length<1){
											IJ.wait(100);
										}

										if (wavelengths == 2){
											while (new File(openPrxPath, "Color1").list(tiffNameFilter).length
													!= new File(openPrxPath, "Color2").list(tiffNameFilter).length){
												IJ.wait(100);
											}
											while (new File(openPryPath, "Color1").list(tiffNameFilter).length
													!= new File(openPryPath, "Color2").list(tiffNameFilter).length){
												IJ.wait(100);
											}
										}

										stackPrxs[pos] = null;
										stackPrys[pos] = null;
										stackPrxs[pos] = new MultiFileInfoVirtualStack(openPrxPath+File.separator, "Color", false);
										stackPrys[pos] = new MultiFileInfoVirtualStack(openPryPath+File.separator, "Color", false);

									}

									if (stackPrxs[pos]!=null && stackPrxs[pos].getSize() > 0 && (impPrxs[pos]==null || stackPrxs[pos].getSize() > impPrxs[pos].getStack().getSize())) {
										ImageWindow prjXwin = null;
										if (impPrxs[pos] != null){
											impPrxs[pos].getRoiManager().setImagePlus(null);
											impPrxs[pos].getRoiManager().dispose();
											impPrxs[pos].setRoiManager(null);
										}else {
											impPrxs[pos] = new ImagePlus();
										}
										impPrxs[pos].setStack((orientBeforeLineage?"Preview_":"") + "3DProjX_Decon-Fuse_"
												+ impAs[pos].getTitle().split(":")[0], stackPrxs[pos]);
										impPrxs[pos].setFileInfo(new FileInfo());
										impPrxs[pos].getCalibration().setUnit(impDF1s[pos].getCalibration().getUnit());
										impPrxs[pos].getCalibration().pixelWidth = impDF1s[pos].getCalibration().pixelWidth;
										impPrxs[pos].getCalibration().pixelHeight = impDF1s[pos].getCalibration().pixelHeight;
										impPrxs[pos].getCalibration().pixelDepth = impDF1s[pos].getCalibration().pixelWidth;

										impPrxs[pos].getOriginalFileInfo().directory = openPrxPath+File.separator;
										int stkNSlicesPrx = impPrxs[pos].getStackSize();
										int zSlicesPrx = ((MultiFileInfoVirtualStack)impPrxs[pos].getStack()).getFivStacks().get(0)
												.getSize();
										impPrxs[pos].setOpenAsHyperStack(true);
										impPrxs[pos].setStack(impPrxs[pos].getStack(), outputWavelengths,
												zSlicesPrx, stkNSlicesPrx
												/ (outputWavelengths * zSlicesPrx));
										if (ciPrxs[pos] != null) {
											prjXwin = ciPrxs[pos].getWindow();
										} else {
											prjXwin = null;
										}

										ciPrxs[pos] = new CompositeImage(impPrxs[pos]);
										ciPrxs[pos].setPosition(1, 1, ciPrxs[pos].getNFrames());

										if (wavelengths > 1)
											ciPrxs[pos].setMode(CompositeImage.COMPOSITE);
										else
											ciPrxs[pos].setMode(CompositeImage.GRAYSCALE);

										int oldW;
										int oldH;
										int oldC;
										int oldZ;
										int oldT;
										double oldMag;
										double oldMin;
										double oldMax;
										boolean oldEdges;
										if (prjXwin==null) {
											ciPrxs[pos].show();
											WindowManager.group(ciDFs[pos], ciPrxs[pos]);
											ciPrxs[pos].setPosition(1, 1, ciPrxs[pos].getNFrames());
											prjXwin = ciPrxs[pos].getWindow();
										} else {
											oldW = prjXwin.getWidth();
											oldH = prjXwin.getHeight();
											oldC = prjXwin.getImagePlus().getChannel();
											oldZ = prjXwin.getImagePlus().getSlice();
											oldT = prjXwin.getImagePlus().getFrame();
											oldMag = prjXwin.getCanvas().getMagnification();
											oldMin = prjXwin.getImagePlus()
													.getDisplayRangeMin();
											oldMax = prjXwin.getImagePlus()
													.getDisplayRangeMax();
											oldEdges = prjXwin.getImagePlus().getStack().isEdges();
											((CompositeImage)ciPrxs[pos]).copyLuts(prjXwin.getImagePlus());

											prjXwin.setImage(ciPrxs[pos]);
											ciPrxs[pos].setWindow(prjXwin);
											prjXwin.updateImage(ciPrxs[pos]);
											prjXwin.getImagePlus().getStack().setEdges(oldEdges);
											prjXwin.setSize(oldW, oldH);
											((StackWindow) prjXwin).addScrollbars(ciPrxs[pos]);
											prjXwin.getCanvas().setMagnification(oldMag);											
											prjXwin.getImagePlus().updateAndDraw();
											prjXwin.getImagePlus().setPosition(oldC, oldZ, oldT);
											prjXwin.getImagePlus().setDisplayRange(oldMin, oldMax);
//											prjXwin.setSize(prjXwin.getSize().width,
//													prjXwin.getSize().height);


										}
									}
									
									if (stackPrys[pos]!=null && stackPrys[pos].getSize() > 0 && (impPrys[pos]==null || stackPrys[pos].getSize() > impPrys[pos].getStack().getSize())) {
										ImageWindow prjYwin = null;

										if (impPrys[pos] != null) {
											impPrys[pos].getRoiManager().setImagePlus(null);
											impPrys[pos].getRoiManager().dispose();
											impPrys[pos].setRoiManager(null);
										} else {
											impPrys[pos] = new ImagePlus();
										}
										impPrys[pos].setStack((orientBeforeLineage?"Preview_":"") + "3DProjY_Decon-Fuse_"
												+ impAs[pos].getTitle().split(":")[0], stackPrys[pos]);
										impPrys[pos].setFileInfo(new FileInfo());
										impPrys[pos].getCalibration().setUnit(impDF1s[pos].getCalibration().getUnit());
										impPrys[pos].getCalibration().pixelWidth = impDF1s[pos].getCalibration().pixelWidth;
										impPrys[pos].getCalibration().pixelHeight = impDF1s[pos].getCalibration().pixelHeight;
										impPrys[pos].getCalibration().pixelDepth = impDF1s[pos].getCalibration().pixelWidth;

										impPrys[pos].getOriginalFileInfo().directory = openPryPath+File.separator;
										int stkNSlicesPry = impPrys[pos].getStackSize();
										int zSlicesPry = ((MultiFileInfoVirtualStack)impPrys[pos].getStack()).getFivStacks().get(0)
												.getSize();
										impPrys[pos].setOpenAsHyperStack(true);
										impPrys[pos].setStack(impPrys[pos].getStack(), outputWavelengths,
												zSlicesPry, stkNSlicesPry
												/ (outputWavelengths * zSlicesPry));
										if (ciPrys[pos] != null) {
											prjYwin = ciPrys[pos].getWindow();
										} else {
											prjYwin = null;
										}

										ciPrys[pos] = new CompositeImage(impPrys[pos]);
										ciPrys[pos].setPosition(1, 1, ciPrys[pos].getNFrames());


										if (wavelengths > 1)
											ciPrys[pos].setMode(CompositeImage.COMPOSITE);
										else
											ciPrys[pos].setMode(CompositeImage.GRAYSCALE);

										int oldW;
										int oldH;
										int oldC;
										int oldZ;
										int oldT;
										double oldMag;
										double oldMin;
										double oldMax;
										boolean oldEdges;
										if (prjYwin==null) {
											ciPrys[pos].show();
											WindowManager.group(ciPrxs[pos], ciPrys[pos]);
											ciPrys[pos].setPosition(1, 1, ciPrys[pos].getNFrames());
											prjYwin = ciPrys[pos].getWindow();
										} else {
											oldW = prjYwin.getWidth();
											oldH = prjYwin.getHeight();
											oldC = prjYwin.getImagePlus().getChannel();
											oldZ = prjYwin.getImagePlus().getSlice();
											oldT = prjYwin.getImagePlus().getFrame();
											oldMag = prjYwin.getCanvas().getMagnification();
											oldMin = prjYwin.getImagePlus()
													.getDisplayRangeMin();
											oldMax = prjYwin.getImagePlus()
													.getDisplayRangeMax();
											oldEdges = prjYwin.getImagePlus().getStack().isEdges();
											((CompositeImage)ciPrys[pos]).copyLuts(prjYwin.getImagePlus());

											prjYwin.setImage(ciPrys[pos]);
											ciPrys[pos].setWindow(prjYwin);
											prjYwin.updateImage(ciPrys[pos]);
											prjYwin.getImagePlus().getStack().setEdges(oldEdges);
											prjYwin.setSize(oldW, oldH);
											((StackWindow) prjYwin).addScrollbars(ciPrys[pos]);
											prjYwin.getCanvas().setMagnification(oldMag);
											prjYwin.getImagePlus().updateAndDraw();
											prjYwin.getImagePlus().setPosition(oldC, oldZ, oldT);
											prjYwin.getImagePlus().setDisplayRange(oldMin, oldMax);
//											prjYwin.setSize(prjYwin.getSize().width,
//													prjYwin.getSize().height);
											

										}
										
										if (orientBeforeLineage) {
											Panel[] diSPIMPreviewPanel = new Panel[2];
											Dimension sizeWinA = ciPrys[pos]
													.getWindow().getSize();
											if (diSPIMPreviewPanel[0] == null) {
												diSPIMPreviewPanel[0] = new Panel(
														new BorderLayout());
											}
											if (diSPIMPreviewPanel[1] == null) {
												diSPIMPreviewPanel[1] = new Panel(
														new BorderLayout());
											}
											if (dispimPreviewButton[pos][0] == null) {
												dispimPreviewButton[pos][0] = new JButton(
														"diSPIM Preview");
												dispimPreviewButton[pos][0]
														.addActionListener(DISPIM_Monitor.this);
												diSPIMPreviewPanel[0]
														.add(BorderLayout.WEST,
																dispimPreviewButton[pos][0]);
												dispimPreviewButton[pos][0]
														.setBackground(Color.orange);

												dispimPreviewButton[pos][0]
														.setVisible(true);
											} else {
												dispimPreviewButton[pos][0]
														.setVisible(true);
											}
											if (dispimPreviewButton[pos][1] == null) {
												dispimPreviewButton[pos][1] = new JButton(
														"diSPIM Preview");
												dispimPreviewButton[pos][1]
														.addActionListener(DISPIM_Monitor.this);
												diSPIMPreviewPanel[1]
														.add(BorderLayout.WEST,
																dispimPreviewButton[pos][1]);
												dispimPreviewButton[pos][1]
														.setBackground(Color.orange);

												dispimPreviewButton[pos][1]
														.setVisible(true);
											} else {
												dispimPreviewButton[pos][1]
														.setVisible(true);
											}
											diSPIMPreviewPanel[0]
													.setVisible(true);
											ciPrxs[pos].getWindow().viewButtonPanel
													.add(diSPIMPreviewPanel[0]);
											ciPrxs[pos].getWindow().viewButtonPanel
													.validate();
											diSPIMPreviewPanel[1]
													.setVisible(true);
											ciPrys[pos].getWindow().viewButtonPanel
													.add(diSPIMPreviewPanel[1]);
											ciPrys[pos].getWindow().viewButtonPanel
													.validate();
											ciPrxs[pos].getWindow().pack();
											//											impAs[pos].getWindow().setSize(sizeWinA);
											ciPrys[pos].getWindow().pack();
											//											impBs[pos].getWindow().setSize(sizeWinB);
										}
											
											
										
//										IJ.run("Tile");
									}

									////////								
									if (lineageDecons){

										LUT[] impLUTs = null;
										int greenMax = 0;
										int redMax = 0;
										if (ciDFs[pos].isComposite()) {
											impLUTs = (((CompositeImage)ciDFs[pos]).getLuts());
											greenMax = (int) impLUTs[0].max;
											redMax = (int) impLUTs[1].max;
										}

										String outputDir = openDFPath;
										String sourceDir = openDFPath;
										String outDir = outputDir+ File.separator ;
										String[] sourceFileList = new File(sourceDir).list();
										ciDFs[pos].killRoi();

										Roi theROI = new Roi(0,0,ciDFs[pos].getWidth(),ciDFs[pos].getHeight());
										int type = Roi.RECTANGLE ;
										boolean flipStack = false;

										if (theROI == null) {

										} else {

											String title = ciDFs[pos].getTitle();
											String savetitle = title.replace(":","_").replace(" ","").replace("_dummy","");
											String openingPath = outDir+savetitle;

											int[] xpoints = theROI.getPolygon().xpoints;
											int[] ypoints = theROI.getPolygon().ypoints;
											int npoints = xpoints.length;

											double angle =0;
//											if (type > Roi.OVAL) {
//												if (npoints == 4) {
//													double angleZero = new Line(xpoints[0], ypoints[0], xpoints[1], ypoints[1]).getAngle();
//													double angleTwo = new Line(xpoints[2], ypoints[2], xpoints[3], ypoints[3]).getAngle();
//
//													double angleZeroPlusPi = angleZero + 180;
//													double angleTwoPlusPi = angleTwo + 180;
//
//													double angleDelta = angleZeroPlusPi%180 - angleTwoPlusPi%180;
//
//													if ( Math.abs(angleDelta)  >80 && Math.abs(angleDelta)  <100) {
//														haveAxesRoiDF = true;
//														angle = angleZero;
//
//														//??? is flipstack correct??  or should it use deltas?!!?						
//														flipStack = (angleZeroPlusPi - angleTwoPlusPi < 0 || angleZeroPlusPi - angleTwoPlusPi > 180);
//														IJ.log("flipStack = " + flipStack + ":angle0PlusPi - angle2PlusPi = " + angleZeroPlusPi +" - "+ angleTwoPlusPi + " = " + (angleDelta));
//
//														Roi ellipseRoi = new EllipseRoi(xpoints[0], ypoints[0], xpoints[1], ypoints[1], 
//																(new Line(xpoints[2], ypoints[2], xpoints[3], ypoints[3])).getLength()
//																/
//																(new Line(xpoints[0], ypoints[0], xpoints[1], ypoints[1])).getLength()
//																);
//														ciDFs[pos].setRoi(ellipseRoi, false);
//														theROI = ellipseRoi;
//														IJ.saveAs(ciDFs[pos], "Selection", openPath +  "ellipseSNF.roi");
//
//														Roi rectRoi = new Roi(ellipseRoi.getBounds());
//														ciDFs[pos].setRoi(rectRoi, false);
//														IJ.saveAs(ciDFs[pos], "Selection", openPath + "rectangleSNF.roi");
//
//													}else {
//														angle = new Line(xpoints[0], ypoints[0], xpoints[2], ypoints[2]).getAngle();
//													}
//												} else {
//													angle = new Line(xpoints[0], ypoints[0], xpoints[npoints/2], ypoints[npoints/2]).getAngle();
//												}
//											}


											int wasC = ciDFs[pos].getChannel();
											int wasZ = ciDFs[pos].getSlice();
											int wasT = ciDFs[pos].getFrame();

											int wavelengths = ciDFs[pos].getNChannels();

//											Roi theRotatedROI = RoiRotator.rotate(theROI, angle);
											final String subdir = savetitle;
											new File(outDir+subdir).mkdirs();
											final String impParameterPath = outDir+subdir+File.separator+savetitle+"_SNparamsFile.txt";
											final int endPoint = ciDFs[pos].getNFrames();
											int stackWidth=0;
											int stackHeight=0;

											for (int frame = 1; frame <= endPoint; frame++) {
												boolean paramsWritten = false;
												if (!((new File(outDir+subdir+"/aaa_t"+frame+".tif").canRead())&&(new File(outDir+subdir+"Skipped"+"/aaa_t"+frame+".tif").canRead()))) {

													ImageStack stack3 = new ImageStack((int)ciDFs[pos].getProcessor().getWidth()*2, (int)ciDFs[pos].getProcessor().getHeight());
													ImageStack stack3skipped = new ImageStack((int)ciDFs[pos].getProcessor().getWidth()*2, (int)ciDFs[pos].getProcessor().getHeight());

													ciDFs[pos].getWindow().setEnabled(false);


													for (int i = 1; i <= ciDFs[pos].getNSlices(); i++) {
														ciDFs[pos].setPositionWithoutUpdate(1, i, frame);

														ImageProcessor ip1 = ciDFs[pos].getProcessor().duplicate();

														int[] ipHis = ip1.getHistogram();
														double ipHisMode = 0.0;
														int ipHisLength = ipHis.length;
														int ipHisMaxBin = 0;
														for (int h=0; h<ipHisLength; h++) {
															if (ipHis[h] > ipHisMaxBin) {
																ipHisMaxBin = ipHis[h];
																ipHisMode = (double)h;
															}
														}
														ip1.subtract(ipHisMode * 1);

														ImageProcessor ip3 = ip1.createProcessor(stack3.getWidth(), stack3.getHeight());

														flipStack = false;  //NO LONGER NEED DOUBLEFLIP IF USING ZYX ROTATIONS TO CORRECT!

														if (!flipStack){
															ImageProcessor ip1fh = ip1.duplicate();
															ip1fh.flipHorizontal();
															ip3.insert(ip1fh, ip3.getWidth()/2, 0);

														} else {
															ip1.flipVertical();
															ImageProcessor ip1fh = ip1.duplicate();
															ip1fh.flipHorizontal();

															ip3.insert(ip1fh, ip3.getWidth()/2, 0);

														}


														if (wavelengths >= 2) {
															ciDFs[pos].setPositionWithoutUpdate(wavelengths, i, frame);
															ImageProcessor ip2 = ciDFs[pos].getProcessor().duplicate();
															ipHis = ip2.getHistogram();
															ipHisMode = 0.0;
															ipHisLength = ipHis.length;
															ipHisMaxBin = 0;
															for (int h=0; h<ipHisLength; h++) {
																if (ipHis[h] > ipHisMaxBin) {
																	ipHisMaxBin = ipHis[h];
																	ipHisMode = (double)h;
																}
															}

															ip2.subtract(ipHisMode * 1);

															if (!flipStack){

																ImageProcessor ip2fh = ip2.duplicate();
																ip2fh.flipHorizontal();
																ip3.insert(ip2fh, 0, 0);

																stack3.addSlice(ip3);
																if (i%depthSkipFactor == 1) {
																	stack3skipped.addSlice(ip3.duplicate());
																}

															} else {
																ip2.flipVertical();

																ImageProcessor ip2fh = ip2.duplicate();
																ip2fh.flipHorizontal();
																ip3.insert(ip2fh, 0, 0);

																stack3.addSlice(null, ip3, 0);
																if (i%depthSkipFactor == 1) {
																	stack3skipped.addSlice(null, ip3.duplicate(), 0);
																}

															}


														}
													}


													ciDFs[pos].getWindow().setEnabled(true);

													ImagePlus frameRGsplitImp = new ImagePlus("Ch12hisSubCrop",stack3);
													ImagePlus frameRGsplitImpSkipped = new ImagePlus("Ch12hisSubCrop",stack3skipped);


													stackWidth = frameRGsplitImp.getWidth()/2;
													stackHeight = frameRGsplitImp.getHeight();
													if (!paramsWritten) {
														IJ.saveString(IJ.openAsString(paramsPath).replaceAll("(.*end_time=)\\d+(;.*)", "$1"+frame+"$2")
																.replaceAll("(.*ROI=)true(;.*)", "$1false$2")
																.replaceAll("(.*ROI.min=)\\d+(;.*)", "$10$2")
																.replaceAll("(.*ROIxmax=)\\d+(;.*)", "$1"+stackWidth+"$2")
																.replaceAll("(.*ROIymax=)\\d+(;.*)", "$1"+stackHeight+"$2")
																.replaceAll("(.*)ROIpoints=\\[\\d+.*\\];(.*)", "$1"+""+"$2")
																, impParameterPath);
														paramsWritten = true;
													}


													// Red channel:

													new File(outDir+subdir).mkdirs();
													new File(outDir+subdir+"Skipped").mkdirs();


													// save a stack

													IJ.save(frameRGsplitImp, outDir+subdir+"/aaa_t"+frame+".tif");
													IJ.save(frameRGsplitImpSkipped, outDir+subdir+"Skipped"+"/aaa_t"+frame+".tif");

													frameRGsplitImp.flush();
													frameRGsplitImpSkipped.flush();

												}
											}
											ciDFs[pos].setPosition(wasC, wasZ, wasT);

											IJ.saveString(IJ.openAsString(paramsPath).replaceAll("(.*end_time=)\\d+(;.*)", "$1"+endPoint+"$2")
													.replaceAll("(.*ROI=)true(;.*)", "$1false$2")
													.replaceAll("(.*ROI.min=)\\d+(;.*)", "$10$2")
													.replaceAll("(.*ROIxmax=)\\d+(;.*)", "$1"+stackWidth+"$2")
													.replaceAll("(.*ROIymax=)\\d+(;.*)", "$1"+stackHeight+"$2")
													.replaceAll("(.*)ROIpoints=\\[\\d+.*\\];(.*)", "$1"+""+"$2")
													, impParameterPath);


										}
									}
									//////////							

								}
							}
						}
						if (doProcessing[pos] &&(ciDFs[pos]==null || (orientBeforeLineage?2:(posFrameEnd[pos]-posFrameStart[pos])) > ciDFs[pos].getNFrames())) {
							posAllDone=false;
						}
					}
				}
				//			IJ.run("Tile");

				String dirOrOMETiffDirectory = dirOrOMETiff;
				if (!new File(dirOrOMETiff).isDirectory())
					dirOrOMETiffDirectory = new File(dirOrOMETiff).getParent();
				final String dirOrOMETiffFinal = dirOrOMETiffDirectory;


				snfCycleComplete = true;

			}
		}, "updateWindowsAndWriteSNFFilesThread");
		updateWindowsAndWriteSNFFilesThread.start();
		
		
		while (snfCycleComplete){
			IJ.wait(100);
		}
		while (!snfCycleComplete){
			IJ.wait(100);
		}

		for (int pos=0; pos<pDim; pos++) {
			if (doProcessing[pos]){
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}

				if (!go){
					continue;
				}

				doProcessing[pos] = true;

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					IJ.log("! no winorstackA "+pos);
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					IJ.log("! no winorstackB "+pos);
					continue;
				} 
				
				ciDFs[pos].setPosition(1, ciDFs[pos].getNSlices()/2, posFrameEnd[pos]-posFrameStart[pos]);
				ciPrxs[pos].setPosition(1, 1, posFrameEnd[pos]-posFrameStart[pos]);
				ciPrys[pos].setPosition(1, 1, posFrameEnd[pos]-posFrameStart[pos]);

			}
		}

	}



	public void readInMMdiSPIMheader(File dirOrOMETiffFile)
			throws NumberFormatException {
		String diSPIMheader = "";
		if (dirOrOMETiffFile.isDirectory()) {
			for (String fileName:dirOrOMETiffFile.list()) {
				File nextFile = new File(dirOrOMETiffFile+File.separator+fileName);

				if(nextFile.isDirectory() && nextFile.list().length>0) {
					for (String listFile:nextFile.list()){
						if (listFile.contains("MMStack") && listFile.toLowerCase().endsWith(".ome.tif")) {
							IJ.log(nextFile.getPath()+File.separator+listFile);
							if (diSPIMheader == "")
								diSPIMheader = open_diSPIMheaderAsString(nextFile.getPath()+File.separator+listFile);
							tDim++;
							break;
						}
					}
				} else if (fileName.contains("MMStack") && fileName.toLowerCase().endsWith(".ome.tif")) {
					IJ.log(nextFile.getPath());
					if (diSPIMheader == "")
						diSPIMheader = open_diSPIMheaderAsString(nextFile.getPath());
					tDim++;
					break;
				}
			}
		} else {
			diSPIMheader = open_diSPIMheaderAsString(dirOrOMETiffFile.getAbsolutePath());
		}
		IJ.log(diSPIMheader);
		diSPIMheader = diSPIMheader.replaceAll("(.*\\$.\\#.*\\{\")(.*)", "\\{\"$2");
		diSPIMheader = diSPIMheader.substring(0, diSPIMheader.lastIndexOf("}"));
		
		IJ.log(diSPIMheader);

		String squareSetsSearchPattern=".*";
		int squareSetsCount=0;
		for(int i = 0; i < diSPIMheader.length(); i++){
			if (diSPIMheader.charAt(i)=='[') {
				squareSetsSearchPattern = squareSetsSearchPattern+"(\\[.*\\]).*";
				squareSetsCount++;
			}
		}
		IJ.log(squareSetsSearchPattern);
		IJ.log(""+squareSetsCount);
		for(int capture=2;capture<=squareSetsCount;capture++) {
			diSPIMheader = diSPIMheader
					.replace((diSPIMheader.replaceAll(squareSetsSearchPattern, "$"+capture)),
					(diSPIMheader.replaceAll(squareSetsSearchPattern, "$"+capture).replace(",",";")));
		}
		IJ.log("*************************");
		IJ.log(diSPIMheader);
		diSPIM_MM_useChannel_ArrayList = new ArrayList<Boolean>();
		diSPIM_MM_group_ArrayList = new ArrayList<String>();
		diSPIM_MM_config_ArrayList = new ArrayList<String>();

		String[] diSPIMheaderChunks = diSPIMheader
				.replace("\\\"", "\"")
				.replace(",", "\\n")
				.replace("}", "\\n}\\n")
				.replace("{", "\\n{\\n")
				//												.replace("]", "\\n]\\n")
				//												.replace("[", "\\n[\\n")
				.split("\\\\n");
		int indents = 1;
		String allVars = "";
		int diSPIM_MM_channel_use_index=0; 
		int diSPIM_MM_channel_group_index=0; 
		int diSPIM_MM_channel_config_index=0; 
		int diSPIM_MM_channel_name_index=0; 

		for(String chunk:diSPIMheaderChunks) {
			if (chunk.startsWith("}") ) {
				indents--;
			}
			for (int i=0;i<indents;i++)
				chunk = "        "+chunk;
			//			IJ.log(chunk);
			if (chunk.contains("{") ) {
				indents++;
			}

			if (chunk.trim().startsWith("\"LaserExposure_ms\":")) {
				diSPIM_MM_LaserExposure_ms= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"MVRotations\":")) {
				diSPIM_MM_MVRotations= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"SPIMtype\":")) {
				diSPIM_MM_SPIMtype= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"UUID\":")) {
				diSPIM_MM_UUID= chunk.split(":")[1].replace("\"", "").trim();
			}					 									

			if (chunk.trim().startsWith("\"spimMode\":")) {
				diSPIM_MM_spimMode= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"isStageScanning\":")) {
				diSPIM_MM_isStageScanning= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"useTimepoints\":")) {
				diSPIM_MM_useTimepoints= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"numTimepoints\":")) {
				diSPIM_MM_numTimepoints= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				tDim = diSPIM_MM_numTimepoints;
			}
			if (chunk.trim().startsWith("\"timepointInterval\":")) {
				diSPIM_MM_timepointInterval= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"useMultiPositions\":")) {
				diSPIM_MM_useMultiPositions= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"useChannels\":")) {
				diSPIM_MM_useChannels= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"channelMode\":")) {
				diSPIM_MM_channelMode= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"numChannels\":")) {
				diSPIM_MM_numChannels= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				cDim = diSPIM_MM_numChannels;
			}				

			if (chunk.trim().startsWith("\"useChannel_\":")) {
				diSPIM_MM_useChannel_ArrayList.add(chunk.trim().split(":")[1].replace("\"", "").trim().toLowerCase().contains("true"));
				diSPIM_MM_channel_use_index++;					
			}

			if (chunk.trim().startsWith("\"group_\":")) {
				diSPIM_MM_group_ArrayList.add(chunk.trim().split(":")[1].replace("\"", "").trim());
				diSPIM_MM_channel_group_index++;
			}

			if (chunk.trim().startsWith("\"config_\":")) {
				diSPIM_MM_config_ArrayList.add(chunk.trim().split(":")[1].replace("\"", "").trim());
				if (diSPIM_MM_config_ArrayList.size()==1) {
					if  (diSPIM_MM_config_ArrayList.get(0).contains("488")) {
						diSPIM_MM_channelOrder = "GR";
					} else if  (diSPIM_MM_config_ArrayList.get(0).contains("561")) {
						diSPIM_MM_channelOrder = "RG";
					} 
				}
				diSPIM_MM_channel_config_index++;
			}


			if (chunk.trim().startsWith("\"channelGroup\":")) {
				diSPIM_MM_channelGroup= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"useAutofocus\":")) {
				diSPIM_MM_useAutofocus= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"numSides\":")) {
				diSPIM_MM_numSides= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				vDim = diSPIM_MM_numSides;
			}
			if (chunk.trim().startsWith("\"firstSideIsA\":")) {
				diSPIM_MM_firstSideIsA= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"delayBeforeSide\":")) {
				diSPIM_MM_delayBeforeSide= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"numSlices\":")) {
				diSPIM_MM_numSlices= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				zDim = diSPIM_MM_numSlices;
			}
			if (chunk.trim().startsWith("\"stepSizeUm\":")) {
				diSPIM_MM_stepSizeUm= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"minimizeSlicePeriod\":")) {
				diSPIM_MM_minimizeSlicePeriod= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"desiredSlicePeriod\":")) {
				diSPIM_MM_desiredSlicePeriod= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"desiredLightExposure\":")) {
				diSPIM_MM_desiredLightExposure= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"centerAtCurrentZ\":")) {
				diSPIM_MM_centerAtCurrentZ= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}					 									

			if (chunk.trim().startsWith("\"scanDelay\":")) {
				diSPIM_MM_scanDelay= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"scanNum\":")) {
				diSPIM_MM_scanNum= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"scanPeriod\":")) {
				diSPIM_MM_scanPeriod= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"laserDelay\":")) {
				diSPIM_MM_laserDelay= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"laserDuration\":")) {
				diSPIM_MM_laserDuration= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"cameraDelay\":")) {
				diSPIM_MM_cameraDelay= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"cameraDuration\":")) {
				diSPIM_MM_cameraDuration= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"cameraExposure\":")) {
				diSPIM_MM_cameraExposure= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"sliceDuration\":")) {
				diSPIM_MM_sliceDuration= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"valid\":")) {
				diSPIM_MM_valid= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"cameraMode\":")) {
				diSPIM_MM_cameraMode= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"hardwareTimepoints\":")) {
				diSPIM_MM_useHardwareTimepoints= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"separateTimepoints\":")) {
				diSPIM_MM_useSeparateTimepoints= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}
			if (chunk.trim().startsWith("\"Position_X\":")) {
				diSPIM_MM_Position_X= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"Position_Y\":")) {
				diSPIM_MM_Position_Y= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"Date\":")) {
				diSPIM_MM_Date= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"MetadataVersion\":")) {
				diSPIM_MM_MetadataVersion= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"Width\":")) {
				diSPIM_MM_Width= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"PixelAspect\":")) {
				diSPIM_MM_PixelAspect= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
			}

			if (chunk.trim().startsWith("\"ChNames\":")) {
				diSPIM_MM_ChNames= chunk.split(":")[1].replace("[", "").replace("]", "").replace("\"", "").split(";");
				for(String s:diSPIM_MM_ChNames)
					//					IJ.log(s)
					;
			}


			if (chunk.trim().startsWith("\"Height\":")) {
				diSPIM_MM_Height= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"SlicePeriod_ms\":")) {
				diSPIM_MM_SlicePeriod_ms= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"GridColumn\":")) {
				diSPIM_MM_GridColumn= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"PixelSize_um\":")) {
				diSPIM_MM_PixelSize_um= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
				//						vWidth = diSPIM_MM_PixelSize_um;
				//						vHeight = diSPIM_MM_PixelSize_um;
			}
			if (chunk.trim().startsWith("\"Frames\":")) {
				diSPIM_MM_Frames= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				tDim = diSPIM_MM_Frames;
			}
			if (chunk.trim().startsWith("\"Source\":")) {
				diSPIM_MM_Source= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"Channels\":")) {
				diSPIM_MM_Channels= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				cDim = diSPIM_MM_Channels;
			}
			if (chunk.trim().startsWith("\"AcqusitionName\":")) {
				diSPIM_MM_AcqusitionName= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"NumberOfSides\":")) {
				diSPIM_MM_NumberOfSides= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				vDim = diSPIM_MM_NumberOfSides;
			}
			if (chunk.trim().startsWith("\"SPIMmode\":")) {
				diSPIM_MM_SPIMmode= chunk.split(":")[1].replace("\"", "").trim();
			}


			if (chunk.trim().startsWith("\"ChColors\":")) {
				diSPIM_MM_ChColorStrings= chunk.split(":")[1].replace("[", "").replace("]", "").replace("\"", "").split(";");
				diSPIM_MM_ChColors = new int[diSPIM_MM_ChColorStrings.length];
				for(int ccs=0;ccs<diSPIM_MM_ChColorStrings.length;ccs++) {
					//					IJ.log(diSPIM_MM_ChColorStrings[ccs]);
					diSPIM_MM_ChColors[ccs] = Integer.parseInt(diSPIM_MM_ChColorStrings[ccs]);
				}
			}


			if (chunk.trim().startsWith("\"Slices\":")) {
				diSPIM_MM_Slices= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
				zDim = diSPIM_MM_Slices;
			}
			if (chunk.trim().startsWith("\"UserName\":")) {
				diSPIM_MM_UserName= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"Depth\":")) {
				diSPIM_MM_Depth= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"PixelType\":")) {
				diSPIM_MM_PixelType= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"Time\":")) {
				diSPIM_MM_Time= chunk.split("\":\"")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"FirstSide\":")) {
				diSPIM_MM_FirstSide= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"z-step_um\":")) {
				diSPIM_MM_zStep_um= Double.parseDouble(chunk.split(":")[1].replace("\"", "").trim());
				//						vDepthRaw = diSPIM_MM_zStep_um;
			}
			if (chunk.trim().startsWith("\"SlicesFirst\":")) {
				diSPIM_MM_SlicesFirst= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}

			if (chunk.trim().startsWith("\"ChContrastMin\":")) {
				diSPIM_MM_ChContrastMinStrings= chunk.split(":")[1].replace("[", "").replace("]", "").replace("\"", "").split(";");
				diSPIM_MM_ChContrastMin = new int[diSPIM_MM_ChContrastMinStrings.length];
				for(int ccmin=0;ccmin<diSPIM_MM_ChContrastMinStrings.length;ccmin++) {
					//					IJ.log(diSPIM_MM_ChContrastMinStrings[ccmin]);
					diSPIM_MM_ChContrastMin[ccmin] = Integer.parseInt(diSPIM_MM_ChContrastMinStrings[ccmin]);
				}
			}


			if (chunk.trim().startsWith("\"StartTime\":")) {
				diSPIM_MM_StartTime= chunk.split("\":\"")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"MVRotationAxis\":")) {
				diSPIM_MM_MVRotationAxis= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"MicroManagerVersion\":")) {
				diSPIM_MM_MicroManagerVersion= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"IJType\":")) {
				diSPIM_MM_IJType= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"GridRow\":")) {
				diSPIM_MM_GridRow= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"VolumeDuration\":")) {
				diSPIM_MM_VolumeDuration= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"NumComponents\":")) {
				diSPIM_MM_NumComponents= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"Position_SPIM_Head\":")) {
				diSPIM_MM_Position_SPIM_Head= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"BitDepth\":")) {
				diSPIM_MM_BitDepth= Integer.parseInt(chunk.split(":")[1].replace("\"", "").trim());
			}
			if (chunk.trim().startsWith("\"ComputerName\":")) {
				diSPIM_MM_ComputerName= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"CameraMode\":")) {
				diSPIM_MM_CameraMode= chunk.split(":")[1].replace("\"", "").trim();
			}
			if (chunk.trim().startsWith("\"TimeFirst\":")) {
				diSPIM_MM_TimeFirst= chunk.split(":")[1].replace("\"", "").trim().toLowerCase().contains("true");
			}

			if (chunk.trim().startsWith("\"ChContrastMax\":")) {
				diSPIM_MM_ChContrastMaxStrings= chunk.split(":")[1].replace("[", "").replace("]", "").replace("\"", "").split(";");
				diSPIM_MM_ChContrastMax = new int[diSPIM_MM_ChContrastMaxStrings.length];
				for(int ccMax=0;ccMax<diSPIM_MM_ChContrastMaxStrings.length;ccMax++) {
					//					IJ.log(diSPIM_MM_ChContrastMaxStrings[ccMax]);
					diSPIM_MM_ChContrastMax[ccMax] = Integer.parseInt(diSPIM_MM_ChContrastMaxStrings[ccMax]);
				}
			}

			if (chunk.trim().contains("\"Positions\":")) {
				diSPIM_MM_Positions= Integer.parseInt(chunk.replaceAll("(.*\"Positions\":)(.*)", "\"Positions\":$2").split(":")[1].split("[;,]")[0].replace("\"", "").trim());
				pDim = diSPIM_MM_Positions;
			}

			//								if (chunk.trim().split(":").length>1)
			//									allVars = allVars+"\n diSPIM_MM_"+chunk.trim().split(":")[1];
		}
		//						IJ.log(allVars);

		if (diSPIM_MM_Width == 2048 ) {
			if (diSPIM_MM_channelMode==null || diSPIM_MM_channelMode.startsWith("NONE")) {
				if(diSPIM_MM_useChannels==false) {
					cDim = 2;    //using diSPIM_MM_channel_use_index value doesn' work for Shroff system (counts 4, duh)
					splitChannels = true;
					dimOrder = "xySplitCzt";
				}
			}
		}
		if (diSPIM_MM_Width == 1536 ) {
			if (diSPIM_MM_channelMode==null || diSPIM_MM_channelMode.startsWith("NONE")) {
				if(diSPIM_MM_useChannels==false) {
					cDim = 2;    //using diSPIM_MM_channel_use_index value doesn' work for Shroff system (counts 4, duh)
					splitChannels = true;
					dimOrder = "xySplitCzt";
				}
			} else {
//				if (diSPIM_MM_numChannels == 2) {
//					cDim = 2;    
//					splitChannels = true;
//					dimOrder = "xySplitSequentialCzt";
//				}
				if (diSPIM_MM_numChannels == 2) {
					cDim = 4;    
					splitChannels = true;
					dimOrder = "xySplitCzt";
				} else if (diSPIM_MM_numChannels == 1) {
					cDim = 2;    
					splitChannels = true;
					dimOrder = "xySplitCzt";
				}
			}
		}
	}

	public Polygon rotatePolygon(Polygon p1, double angle) {
		double theta = angle * Math.PI / 180;
		double xcenter = p1.getBounds().getCenterX();
		double ycenter = p1.getBounds().getCenterY();
		for (int v = 0; v < p1.xpoints.length; v++) {
			double dx = p1.xpoints[v] - xcenter;
			double dy = ycenter - p1.ypoints[v];
			double r = Math.sqrt(dx * dx + dy * dy);
			double a = Math.atan2(dy, dx);
			p1.xpoints[v] = (int) (xcenter + r * Math.cos(a + theta));
			p1.ypoints[v] = (int) (ycenter - r * Math.sin(a + theta));
		}
		return p1;
	}

	//	{"LaserExposure_ms":"2.5","MVRotations":"0_90_0_90","SPIMtype":"diSPIM","UUID":"ea4d0baa-8f54-4937-82f7-4d1e62492be5","SPIMAcqSettings":"{\n  \"spimMode\": \"PIEZO_SLICE_SCAN\",\n  \"isStageScanning\": false,\n  \"useTimepoints\": true,\n  \"numTimepoints\": 5,\n  \"timepointInterval\": 15.0,\n  \"useMultiPositions\": true,\n  \"useChannels\": true,\n  \"channelMode\": \"VOLUME_HW\",\n  \"numChannels\": 2,\n  \"channels\": [\n    {\n      \"useChannel_\": true,\n      \"group_\": \"Channel\",\n      \"config_\": \"488 nm\"\n    },\n    {\n      \"useChannel_\": true,\n      \"group_\": \"Channel\",\n      \"config_\": \"561 nm\"\n    }\n  ],\n  \"channelGroup\": \"Channel\",\n  \"useAutofocus\": false,\n  \"numSides\": 2,\n  \"firstSideIsA\": true,\n  \"delayBeforeSide\": 50.0,\n  \"numSlices\": 50,\n  \"stepSizeUm\": 1.0,\n  \"minimizeSlicePeriod\": true,\n  \"desiredSlicePeriod\": 5.5,\n  \"desiredLightExposure\": 2.5,\n  \"centerAtCurrentZ\": false,\n  \"sliceTiming\": {\n    \"scanDelay\": 4.5,\n    \"scanNum\": 1,\n    \"scanPeriod\": 3.0,\n    \"laserDelay\": 5.5,\n    \"laserDuration\": 2.5,\n    \"cameraDelay\": 2.75,\n    \"cameraDuration\": 1.0,\n    \"cameraExposure\": 5.35,\n    \"sliceDuration\": 8.0,\n    \"valid\": true\n  },\n  \"cameraMode\": \"EDGE\",\n  \"hardwareTimepoints\": false,\n  \"separateTimepoints\": true\n}

	public String open_diSPIMheaderAsString(String path) {
		IJ.log(path);
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Text File", "");
			String directory = od.getDirectory();
			String name = od.getFileName();
			if (name==null) return null;
			path = directory + name;
		}
		String str = "";
		File file = new File(path);
		if (!file.exists())
			return "Error: file not found";
		try {
			StringBuffer sb = new StringBuffer(5000);
			BufferedReader r = new BufferedReader(new FileReader(file));
			String s ="";
			while (!s.contains("\"Positions\":")) {
				//			for (int l=0;l<2;l++) {
				s=r.readLine();
				//				IJ.log(s);
				if (s==null)
					break;
				//				else
				//					IJ.log(s);
				//					sb.append(s+"\n");
			}
			r.close();
			//			str = new String(sb);
			str = s;
		}
		catch (Exception e) {
			str = "Error: "+e.getMessage();
		}
		return str;
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand() == "Fuse") {
			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				doProcessing[pos] = true;

				if (e.getSource() == dispimToolsButton[pos][0]) {
					if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
						continue;
					}
					dispimToolsButton[pos][0].setBackground(null);
					fuseButton[pos][0].setVisible(false);
					splitButton[pos][0].setVisible(false);
					xSpinner[pos][0].setVisible(false);
					ySpinner[pos][0].setVisible(false);
					zSpinner[pos][0].setVisible(false);
					impAs[pos].getWindow().viewButtonPanel.validate();

				}
				if (e.getSource() == dispimToolsButton[pos][1]) {
					if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
						continue;
					}
					dispimToolsButton[pos][1].setBackground(null);
					fuseButton[pos][1].setVisible(false);
					splitButton[pos][1].setVisible(false);
					xSpinner[pos][1].setVisible(false);
					ySpinner[pos][1].setVisible(false);
					zSpinner[pos][1].setVisible(false);
					impBs[pos].getWindow().viewButtonPanel.validate();
				}
			}
			rerunArg = (dirOrOMETiff+"|"+"rerunWithDecon"+"|"+"newMM");
			monitoring = false;
		}
		if (e.getActionCommand() == "diSPIM") {
			
			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				doProcessing[pos] = true;

				if (e.getSource() == dispimToolsButton[pos][0]) {
					if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
						continue;
					}
					boolean shown = fuseButton[pos][0].isVisible();
					dispimToolsButton[pos][0].setBackground(!shown?Color.yellow:null);
					fuseButton[pos][0].setVisible(!shown);
					splitButton[pos][0].setVisible(dimOrder.contains("Sequential") && !shown);
					xSpinner[pos][0].setVisible(!shown);
					ySpinner[pos][0].setVisible(!shown);
					zSpinner[pos][0].setVisible(!shown);
					if (shown) {
						for (int pp=0; pp<pDim; pp++) {
							if (impAs[pp]==null || impAs[pp].hasNullStack() || impAs[pp].getWindow()==null  || !impAs[pp].getWindow().isVisible()) {
								continue;
							}
							xSpinner[pp][0].setValue(((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdXA());

							ySpinner[pp][0].setValue(((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdYA());

							zSpinner[pp][0].setValue(((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdZA());

							dispimToolsButton[pp][0].setBackground(fuseButton[pp][0].isVisible()?Color.yellow:null);
							((CompositeImage)impAs[pp]).updateAndDraw();
							impAs[pp].getWindow().viewButtonPanel.validate();
							
							for (FileInfo[] nextFIarray:stackAs[pp].infoCollectorArrayList){
								for (FileInfo nextFI:nextFIarray){
									if (nextFI.channelShifts == null || nextFI.channelShifts.length<6){
										nextFI.channelShifts = new int[]{0,0,0,0,0,0,};
									}
									nextFI.channelShifts[0] = ((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdXA();
									nextFI.channelShifts[1] = ((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdYA();
									nextFI.channelShifts[2] = ((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdZA();
								}
							}
						}
						Prefs.set("diSPIMmonitor.dXA", ((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdXA());
						Prefs.set("diSPIMmonitor.dYA", ((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdYA());
						Prefs.set("diSPIMmonitor.dZA", ((MultiFileInfoVirtualStack)(impAs[pos].getStack())).getdZA());							
						
					}
					impAs[pos].getWindow().viewButtonPanel.validate();
				}
				if (e.getSource() == dispimToolsButton[pos][1]) {
					if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
						continue;
					}
					boolean shown = fuseButton[pos][1].isVisible();
					dispimToolsButton[pos][1].setBackground(!shown?Color.yellow:null);
					fuseButton[pos][1].setVisible(!shown);
					splitButton[pos][1].setVisible(dimOrder.contains("Sequential") && !shown);
					xSpinner[pos][1].setVisible(!shown);
					ySpinner[pos][1].setVisible(!shown);
					zSpinner[pos][1].setVisible(!shown);
					if (shown) {
						for (int pp=0; pp<pDim; pp++) {
							if (impBs[pp]==null || impBs[pp].hasNullStack() || impBs[pp].getWindow()==null  || !impBs[pp].getWindow().isVisible()) {
								continue;
							}
							xSpinner[pp][1].setValue(((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdXB());

							ySpinner[pp][1].setValue(((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdYB());

							zSpinner[pp][1].setValue(((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdZB());

							dispimToolsButton[pp][1].setBackground(fuseButton[pp][1].isVisible()?Color.yellow:null);
							((CompositeImage)impBs[pp]).updateAndDraw();
							impBs[pp].getWindow().viewButtonPanel.validate();

							for (FileInfo[] nextFIarray:stackAs[pp].infoCollectorArrayList){
								for (FileInfo nextFI:nextFIarray){
									if (nextFI.channelShifts == null || nextFI.channelShifts.length<6){
										nextFI.channelShifts = new int[]{0,0,0,0,0,0,};
									}
									nextFI.channelShifts[3] = ((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdXB();
									nextFI.channelShifts[4] = ((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdYB();
									nextFI.channelShifts[5] = ((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdZB();
								}
							}
						}
						Prefs.set("diSPIMmonitor.dXB", ((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdXB());
						Prefs.set("diSPIMmonitor.dYB", ((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdYB());
						Prefs.set("diSPIMmonitor.dZB", ((MultiFileInfoVirtualStack)(impBs[pos].getStack())).getdZB());
					}
					impBs[pos].getWindow().viewButtonPanel.validate();
				}
			}
		}
		
		if (e.getActionCommand() == "diSPIM Preview"){
			IJ.beep();
			IJ.log("Preview adjustment details saved to disk");
			for (int pos=0; pos<pDim; pos++) {
				double xRotRead=0;
				double yRotRead=0;
				double zRotRead=0;
				double maxReferenceIntensity = 0;
				if (ciPrxs[pos] != null && ciPrxs[pos].isVisible()){
					xRotRead = (ciPrxs[pos].getSlice()-1)*10;
					Roi xRoi = ciPrxs[pos].getRoi();
					if (xRoi != null){
						if (xRoi.getType() == Roi.LINE){
							yRotRead = 0-(((Line)xRoi).getAngle()-180);
						}
					}
					
					ImagePlus xImp = (new ImagePlus("testX",ciPrxs[pos].getProcessor()));
					maxReferenceIntensity = xImp.getStatistics().max;
					xImp.flush();
				}
				if (ciPrys[pos] != null && ciPrys[pos].isVisible()){
					if (yRotRead ==0 ){
						yRotRead = (ciPrys[pos].getSlice()-10)*10;
					}
					Roi yRoi = ciPrys[pos].getRoi();
					if (yRoi != null){
						if (yRoi.getType() == Roi.LINE){
							xRotRead = 90-((Line)yRoi).getAngle();
						}
					}
					ImagePlus yImp = (new ImagePlus("testY",ciPrys[pos].getProcessor()));
					maxReferenceIntensity = yImp.getStatistics().max;
					yImp.flush();
				}
				if (xRotRead < 0){
					xRotRead = 360+xRotRead;
				}
				if (yRotRead < 0){
					yRotRead = 360+yRotRead;
				}
				IJ.append("" + pos +","+ xRotRead +","+ yRotRead +","+ zRotRead +","+ maxReferenceIntensity, savePath+"fineRotations.txt");
			}
		}
		
		if (e.getActionCommand() == "CCM") {
			if (cDim == 4 && wavelengths == 4 && splitChannels == true && dimOrder == "xySplitCzt") {
				cDim = 2;
				wavelengths = 2;
				splitChannels = true;
				dimOrder = "xySplitSequentialCzt";
			} else if (cDim == 2 && wavelengths == 2 && splitChannels == true && dimOrder == "xySplitSequentialCzt") {
				cDim = 4;
				wavelengths = 4;
				splitChannels = true;
				dimOrder = "xySplitCzt";
			}

			MultiFileInfoVirtualStack[] stackAs = new MultiFileInfoVirtualStack[pDim];
			MultiFileInfoVirtualStack[] stackBs = new MultiFileInfoVirtualStack[pDim];

			for (int pos=0; pos<pDim; pos++) {
				boolean go = true;
				if (posIntArray!=null){
					go = false;
					for (int posInt:posIntArray){
						if (posInt == pos){
							go=true;
						}
					}
				}
				if (!go){
					continue;
				}

				
				if(e.getSource()!= splitButton[pos][0] && e.getSource()!= splitButton[pos][1]){
					continue;
				}
				
				doProcessing[pos] = true;

				if (impAs[pos]==null || impAs[pos].hasNullStack() || impAs[pos].getWindow()==null  || !impAs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				}
				if (impBs[pos]==null || impBs[pos].hasNullStack() || impBs[pos].getWindow()==null  || !impBs[pos].getWindow().isVisible()) {
					doProcessing[pos] = false;
					continue;
				} 


				if (impAs[pos].hasNullStack())
					continue;
				if (impBs[pos].hasNullStack())
					continue;

				if (doProcessing[pos]) {
					ImageWindow win = null;

					win = impAs[pos].getWindow();
					double zoomA = win.getCanvas().getMagnification();
					int cA = impAs[pos].getChannel();
					int zA = impAs[pos].getSlice();
					int tA = impAs[pos].getFrame();
					boolean tailing = tA==impAs[pos].getNFrames();
					tDim = impAs[pos].getNFrames();

					stackAs[pos] = new MultiFileInfoVirtualStack(
							dirConcat, dimOrder, keyString, cDim*(diSPIM_MM_Channels/vDim>1 && diSPIM_MM_channelOrder == "RG"?-1:1), zDim, tDim, vDim, pos,
							false, false, true, new File(this.dirOrOMETiff).isDirectory() && this.dirOrOMETiff.endsWith(".ome.tif"));

					ImagePlus impNextA = new ImagePlus(impAs[pos].getTitle(), stackAs[pos]);
					impNextA.setOpenAsHyperStack(true);
					impNextA.setDimensions(cDim, zDim, tDim);
					impNextA = new CompositeImage(impNextA);
					((CompositeImage)impNextA).setMode(modeA);
					((CompositeImage)impNextA).reset();
					((CompositeImage)impNextA).copyLuts(impAs[pos]);
					impNextA.setCalibration(impAs[pos].getCalibration());
					if (stageScan)
						stackAs[pos].setSkewXperZ(
								impNextA.getCalibration().pixelDepth / impNextA.getCalibration().pixelWidth);

					impAs[pos] = impNextA;
					
					win = null;

					win.setImage(impAs[pos]);
					impAs[pos].setWindow(win);
					win.updateImage(impAs[pos]);
					if (win instanceof StackWindow) {
						StackWindow sw = (StackWindow)win;
						int stackSize = impAs[pos].getStackSize();
						int nScrollbars = sw.getNScrollbars();
						sw.addScrollbars(impAs[pos]);
					}
					impAs[pos].setPosition(cA, zA, (tailing || tA > impAs[pos].getNFrames())? impAs[pos].getNFrames() : tA);
					((CompositeImage)impAs[pos]).setMode(modeA);
					win.getCanvas().setMagnification(zoomA);
					Dimension winSize = win.getSize();
					win.pack();
					win.setSize(winSize);


					win = impBs[pos].getWindow();
					double zoomB = win.getCanvas().getMagnification();
					int cB = impBs[pos].getChannel();
					int zB = impBs[pos].getSlice();
					int tB = impBs[pos].getFrame();
					if (impBs[pos].isComposite()) {
						modeB = ((CompositeImage)impBs[pos]).getCompositeMode();
					}

					tailing = tB==impBs[pos].getNFrames();
					tDim = impBs[pos].getNFrames();

					stackBs[pos] = new MultiFileInfoVirtualStack(
							dirConcat, dimOrder, keyString, cDim*(diSPIM_MM_Channels/vDim>1 && diSPIM_MM_channelOrder == "RG"?-1:1), zDim, tDim, vDim, pos,
							true, false, true, new File(this.dirOrOMETiff).isDirectory() && this.dirOrOMETiff.endsWith(".ome.tif"));
					
					ImagePlus impNextB = new ImagePlus(impAs[pos].getTitle(), stackAs[pos]);

					impNextB = new CompositeImage(new ImagePlus(impBs[pos].getTitle(), stackBs[pos]));
					impNextB.setOpenAsHyperStack(true);
					impNextB.setDimensions(cDim, zDim, tDim);
					impNextB = new CompositeImage(impNextB);
					((CompositeImage)impNextB).setMode(modeB);
					((CompositeImage)impNextB).reset();
					((CompositeImage)impNextB).copyLuts(impBs[pos]);
					impNextB.setCalibration(impBs[pos].getCalibration());
					if (stageScan)
						stackAs[pos].setSkewXperZ(
								impNextB.getCalibration().pixelDepth / impNextB.getCalibration().pixelWidth);

					impBs[pos] = impNextB;

					win.setImage(impBs[pos]);
					impBs[pos].setWindow(win);
					win.updateImage(impBs[pos]);
					if (win instanceof StackWindow) {
						StackWindow sw = (StackWindow)win;
						int stackSize = impBs[pos].getStackSize();
						int nScrollbars = sw.getNScrollbars();
						sw.addScrollbars(impBs[pos]);
					}
					impBs[pos].setPosition(cB, zB, (tailing || tB > impBs[pos].getNFrames())? impBs[pos].getNFrames() : tB);
					((CompositeImage)impBs[pos]).setMode(modeB);
					win.getCanvas().setMagnification(zoomB);
					winSize = win.getSize();
					win.pack();
					win.setSize(winSize);
					
					((MultiFileInfoVirtualStack)(impAs[pos].getStack())).setdXA((Integer) xSpinner[pos][0].getValue());
					((MultiFileInfoVirtualStack)(impAs[pos].getStack())).setdYA((Integer) ySpinner[pos][0].getValue());
					((MultiFileInfoVirtualStack)(impAs[pos].getStack())).setdZA((Integer) zSpinner[pos][0].getValue());
					((MultiFileInfoVirtualStack)(impBs[pos].getStack())).setdXB((Integer) xSpinner[pos][1].getValue());
					((MultiFileInfoVirtualStack)(impBs[pos].getStack())).setdYB((Integer) ySpinner[pos][1].getValue());
					((MultiFileInfoVirtualStack)(impBs[pos].getStack())).setdZB((Integer) zSpinner[pos][1].getValue());

					((CompositeImage)impAs[pos]).updateAndDraw();

					((CompositeImage)impBs[pos]).updateAndDraw();
				}
			}

		}

	}

	public void stateChanged(ChangeEvent e) {
		if (e.getSource() instanceof JSpinner) {
			for (int p=0; p<pDim; p++) {
				if ( impAs[p]!=null && impAs[p].getWindow()!=null && impAs[p].getWindow().isAncestorOf((Component)e.getSource())) {
					if(e.getSource().equals(xSpinner[p][0])) {
						((MultiFileInfoVirtualStack)(impAs[p].getStack())).setdXA((Integer) xSpinner[p][0].getValue());
						((CompositeImage)impAs[p]).updateAndDraw();
						break;
					}
					if(e.getSource().equals(ySpinner[p][0])) {
						((MultiFileInfoVirtualStack)(impAs[p].getStack())).setdYA((Integer) ySpinner[p][0].getValue());
						((CompositeImage)impAs[p]).updateAndDraw();
						break;
					}
					if(e.getSource().equals(zSpinner[p][0])) {
						((MultiFileInfoVirtualStack)(impAs[p].getStack())).setdZA((Integer) zSpinner[p][0].getValue());
						((CompositeImage)impAs[p]).updateAndDraw();
						break;
					}
				}else if ( impBs[p]!=null && impBs[p].getWindow()!=null && impBs[p].getWindow().isAncestorOf((Component)e.getSource())) {
					if(e.getSource().equals(xSpinner[p][1])) {
						((MultiFileInfoVirtualStack)(impBs[p].getStack())).setdXB((Integer) xSpinner[p][1].getValue());
						((CompositeImage)impBs[p]).updateAndDraw();
						break;
					}
					if(e.getSource().equals(ySpinner[p][1])) {
						((MultiFileInfoVirtualStack)(impBs[p].getStack())).setdYB((Integer) ySpinner[p][1].getValue());
						((CompositeImage)impBs[p]).updateAndDraw();
						break;
					}
					if(e.getSource().equals(zSpinner[p][1])) {
						((MultiFileInfoVirtualStack)(impBs[p].getStack())).setdZB((Integer) zSpinner[p][1].getValue());
						((CompositeImage)impBs[p]).updateAndDraw();
						break;
					}
				}
			}
		}
		
	}

	public void imageOpened(ImagePlus imp) {
		// TODO Auto-generated method stub
		
	}

	public void imageClosed(ImagePlus closingImp) {
		if (closingImp != null){
			closingImp.killRoi();
		}
		if (impAs!=null ) {
			for (int a=0;a<impAs.length;a++) {
				if (impAs[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);
					stackAs[a] = null;
					impAs[a] = null;
				}
			}
		}
		if (impBs!=null ) {
			for (int a=0;a<impBs.length;a++) {
				if (impBs[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);
					stackBs[a] = null;
					impBs[a] = null;
				}
			}
		}
		if (ciDFs!=null ) {
			for (int a=0;a<ciDFs.length;a++) {
				if (ciDFs[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);
					stackDFs[a] = null;
					impDF1s[a] = null;
					ciDFs[a] = null;
				}
			}
		}
		if (ciPrxs!=null ) {
			for (int a=0;a<ciPrxs.length;a++) {
				if (ciPrxs[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);
					stackPrxs[a] = null;
					impPrxs[a] = null;
					ciPrxs[a] = null;
				}
			}
		}
		if (ciPrys!=null ) {
			for (int a=0;a<ciPrys.length;a++) {
				if (ciPrys[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);
					stackPrys[a] = null;
					impPrys[a] = null;
					ciPrys[a] = null;
				}
			}
		}
		if (impPrxs!=null ) {
			for (int a=0;a<impPrxs.length;a++) {
				if (impPrxs[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);
					stackPrxs[a] = null;

					impPrxs[a] = null;
				}
			}
		}
		if (impPrys!=null ) {
			for (int a=0;a<impPrys.length;a++) {
				if (impPrys[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);

					stackPrys[a] = null;
					impPrys[a] = null;

				}
			}
		}
		if (impDF1s!=null ) {
			for (int a=0;a<impDF1s.length;a++) {
				if (impDF1s[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);
					stackDFs[a] = null;

					impDF1s[a] = null;
				}
			}
		}
		if (impDF2s!=null ) {
			for (int a=0;a<impDF2s.length;a++) {
				if (impDF2s[a] == closingImp) {
					//				ImagePlus.removeImageListener(this);

					impDF2s[a] = null;
				}
			}
		}
	}

	public void imageUpdated(ImagePlus imp) {
		// TODO Auto-generated method stub
		
	}
}
