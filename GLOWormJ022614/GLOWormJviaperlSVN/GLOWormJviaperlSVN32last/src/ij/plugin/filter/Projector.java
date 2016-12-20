package ij.plugin.filter;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.ChannelSplitter;
import ij.plugin.Concatenator;
import ij.plugin.frame.ContrastAdjuster;
import ij.plugin.Duplicator;
import ij.plugin.HyperStackConverter;
import ij.plugin.MultiFileInfoVirtualStack;
import ij.plugin.ZProjector;
import ij.measure.Calibration;
import ij.plugin.RGBStackMerge;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.util.Tools;
import ij.macro.Interpreter;

import java.awt.*;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.awt.image.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;

import org.vcell.gloworm.MQTVS_Duplicator;
import org.vcell.gloworm.MultiChannelController;
import org.vcell.gloworm.MultiQTVirtualStack;

/**
This plugin creates a sequence of projections of a rotating volume (stack of slices) onto a plane using
nearest-point (surface), brightest-point, or mean-value projection or a weighted combination of nearest-
point projection with either of the other two methods (partial opacity).  The user may choose to rotate the
volume about any of the three orthogonal axes (x, y, or z), make portions of the volume transparent (using
thresholding), or add a greater degree of visual realism by employing depth cues. Based on Pascal code
contributed by Michael Castle of the  University of Michigan Mental Health Research Institute.
 */ 

public class Projector implements PlugInFilter, TextListener {

	static final int xAxis=0, yAxis=1, zAxis=2;
	static final int nearestPoint=0, brightestPoint=1, meanValue=2;
	static final int BIGPOWEROF2 = 8192;

	String[] axisList = {"X-Axis", "Y-Axis", "Z-Axis"};
	String[] methodList = {"Nearest Point", "Brightest Point", "Mean Value"};

	private static int axisOfRotation = yAxis;
	private static int projectionMethod = brightestPoint;

	private double sliceInterval = 5.0; // pixels
	private static int initAngle = 0;
	private static int totalAngle = 360;
	private static int angleInc = 10;
	private static int opacity = 0;
	private static int depthCueSurf = 100;
	private static int depthCueInt = 100;
	private static boolean interpolate = true;
	private static boolean debugMode;
	private int transparencyLower = 1;
	private int transparencyUpper = 255;	
	ImagePlus imp;
	ImageStack stack;
	ImageStack stack2;
	int width, height, imageWidth;
	int left, right, top, bottom;
	byte[] projArray, opaArray, brightCueArray;
	short[] zBuffer, cueZBuffer, countBuffer;
	int[] sumBuffer;
	boolean isRGB;
	String label = "";
	boolean done;
	boolean batchMode = Interpreter.isBatchMode();
	boolean isHyperstack;
	private boolean duplicateStack;
	private int firstC;
	private int lastC;
	private int firstZ;
	private int lastZ;
	private int firstT;
	private int lastT;
	private Checkbox checkbox;
	private Roi roi;
	private boolean sliceSpecificROIs;
	private Roi manualRoi;
	private ImagePlus[] projImpD;
	private RoiManager rmProj;
	private Roi[] roiArray;
	private boolean outlineObjects;
	private double progressScale;
	private double progressBase;
	private int loopC;
	private int loopT;
	private int stepT = 1;
	private boolean is16bit;
	private boolean is32bit;
	private String dupTitle;

	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		if (false/*!(imp.getStack() instanceof MultiQTVirtualStack) && imp.getType() == ImagePlus.COLOR_RGB*/) {
			IJ.runPlugIn("ij.plugin.Projector", arg);
			return DONE;
		}
		roi = imp.getRoi();	
		imp.saveRoi();
		manualRoi  = null;
		if (roi!=null && roi.isArea() && !(roi instanceof TextRoi)) 
			manualRoi = roi;

		
		if (imp!=null && imp.isHyperStack()) {
			//IJ.error("3D Project", "Hyperstacks are currently not supported. Convert to\nRGB using Image>Type>RGB Color and try again.");
			//return DONE; 
		}
		return DOES_16+DOES_8G+DOES_RGB+STACK_REQUIRED+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		if (imp != null) {
			if (imp.getStack() instanceof MultiQTVirtualStack) {
				String firstMovieName ="";
				if (((MultiQTVirtualStack)imp.getStack()).getVirtualStack(0)!=null)
					firstMovieName = ((MultiQTVirtualStack)imp.getStack()).getVirtualStack(0).getMovieName();
				if ( firstMovieName.toLowerCase().contains("gp") || firstMovieName.toLowerCase().contains("yp") 
						|| firstMovieName.toLowerCase().contains("prx") || firstMovieName.toLowerCase().contains("pry") ) {
					if ( !IJ.showMessageWithCancel("Use Stereo4D data for 3D Project input?", 
							"Are you certain you want to run 3D Projection " +
							"\nto dissect a region from a Stereo4D movie? \n(it's already 3D-projected!)" +
							"\n \nUsually, one wants to use Slice4D data for input to 3D Project.") ) {
						return;
					}
				}
			}
		}

		
		if (ip.isInvertedLut()) {
			if (!IJ.showMessageWithCancel("3D Project", ZProjector.lutMessage))
				return; 
		}

		if (!showHSDialog(imp))
			return;

		if (!showDialog())
			return;
		WindowManager.setCurrentWindow(imp.getWindow());
//		((ContrastAdjuster)ContrastAdjuster.getInstance()).toBack();
		isRGB = imp.getType()==ImagePlus.COLOR_RGB;
		is16bit = imp.getType()==ImagePlus.GRAY16;
		is32bit = imp.getType()==ImagePlus.GRAY32;
		isHyperstack = imp.isHyperStack();

		LUT[] lut = imp.getLuts();

		int stackMode = 0;


		double originalSliceInterval = sliceInterval;
		ImagePlus buildImp = null;
		projImpD = new ImagePlus[lastC-firstC+1];
		int finalChannels = 0;
		int finalSlices = 0;
		int finalFrames = 0;
		
		double inMin = imp.getDisplayRangeMin();
		double inMax = imp.getDisplayRangeMax();
		LUT[] luts = imp.getLuts();
		ColorModel cm = imp.getProcessor().getColorModel();
		int inChannel = imp.getChannel();
		int inSlice = imp.getSlice();
		int inFrame = imp.getFrame();

		if ( imp.isComposite())
			stackMode = ((CompositeImage)imp).getCompositeMode();
		if ( imp.isComposite() && ((CompositeImage)imp).getCompositeMode() < CompositeImage.RATIO12) {
			stackMode = ((CompositeImage)imp).getMode();
			((CompositeImage)imp).setMode(CompositeImage.GRAYSCALE);

		}
		if (imp.isComposite() && ((CompositeImage)imp).getCompositeMode() >= CompositeImage.RATIO12)
			lastC = firstC;


		imp.getWindow().setVisible(false);
		
		Frame rm = imp.getRoiManager();
		boolean rmVis =false; 
		if (rm != null) {
			rmVis = rm.isVisible();
			rm.setVisible(false);
		}
		Frame mcc = imp.getMultiChannelController();
		boolean mccVis = false;
		if (mcc != null) {
			mccVis = mcc.isVisible();
			mcc.setVisible(false);
		}
		
		ImageWindow liw = null;
		if (imp.getImageStack() instanceof MultiQTVirtualStack && ((MultiQTVirtualStack)imp.getImageStack()).getLineageMapImage() != null)
			 liw = ((MultiQTVirtualStack)imp.getImageStack()).getLineageMapImage().getWindow();
		boolean liwVis = false;
		if (liw != null) {	
			liwVis = liw.isVisible();
			liw.setVisible(false);
		}

		ArrayList<Roi> bigRoiAList = new ArrayList<Roi>();
		int finalT = lastT;
		long tempTime = (new Date()).getTime();
//		File tempDir = new File(IJ.getDirectory("home") +"Proj_"+imp.getTitle().replaceAll("[,. ;:]","") + tempTime);
		String saveRootDir = "";
		String saveRootPrefix = "";
		if (IJ.getDirectory("image") != null){
			saveRootDir = (new File(IJ.getDirectory("image"))).getParent()/*)*/ ;
			saveRootPrefix = (new File(IJ.getDirectory("image"))).getName()+"_";
		}else {
			saveRootDir = IJ.getDirectory("temp");
		}
		boolean correctSaveRoot = false;
		for (File saveSibFile: (new File(saveRootDir)).listFiles()) {
			if (saveSibFile.isDirectory() || (!saveSibFile.getName().toLowerCase().endsWith(".tif")
												&& !saveSibFile.getName().toLowerCase().contains("ds_store"))) {
				correctSaveRoot = true;
			}
		}
		if (!correctSaveRoot) {
			saveRootDir = (new File(saveRootDir)).getParent();
		}
		File tempDir = new File(saveRootDir + File.separator + saveRootPrefix +"Proj_"+imp.getTitle().replaceAll("[,. ;:]","").replace(File.separator, "_") + tempTime);
		if (!tempDir.mkdir()) {
			IJ.error("3D Projection failed", "Unable to save projected stacks to" + tempDir.getPath());
			return;
		}

		for (loopT = firstT; loopT < lastT +1; loopT=loopT+stepT) { 
			long memoryFree = Runtime.getRuntime().freeMemory();
			long memoryTotal = Runtime.getRuntime().totalMemory();
			long memoryUsed = memoryTotal-memoryFree;
			long memoryMax = Runtime.getRuntime().maxMemory();
			if ((double)memoryUsed/(double)memoryMax > 0.9) {
				finalT = loopT - stepT;
				finalFrames = finalFrames-1;
				loopT = lastT +1;
			} else {

				ImagePlus projImpDC = new ImagePlus();
				ImageStack stackC = null;
				for (loopC = firstC; loopC < lastC +1; loopC++) {


					sliceInterval = originalSliceInterval;
					imp.setRoi(manualRoi);
					ImagePlus impD = null;

					if ( imp.getNFrames() >= 1 || (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE) ) {
						impD = (new MQTVS_Duplicator()).run(imp, loopC, loopC, firstZ, lastZ, loopT, loopT, 1, sliceSpecificROIs, tempTime);
					}
					impD.setCalibration(imp.getCalibration());
//					impD.show();
					
					if (manualRoi != null && sliceSpecificROIs) {
						impD.setRoi(manualRoi);
						WindowManager.setTempCurrentImage(impD);
						IJ.run("Clear Outside", "stack");
						IJ.run("Crop");
						WindowManager.setTempCurrentImage(null);
					}else{  //Not clear why this is needed, but it gives right final product...
						WindowManager.setTempCurrentImage(impD);
						WindowManager.setTempCurrentImage(null);
					}

					ImagePlus impDZ = impD.duplicate();
					if (impDZ.getBitDepth() > 8 && !isRGB) {
						impDZ.setPosition(1, (1+lastZ+1-firstZ)/2, 1);
						impDZ.getProcessor().setMinAndMax(0.0, 50.0);
						IJ.run(impDZ,"8-bit","");
						if (impDZ.isComposite()   )
							((CompositeImage)impDZ).setMode(CompositeImage.GRAYSCALE);
					}
//					impDZ.show();
					if (interpolate && sliceInterval>1.0) {
						if (firstZ != lastZ)
							impDZ = zScale(impDZ);


						if (impDZ==null) return;
						sliceInterval = 1.0;
					}

					impD.flush();

					//Code below here taken from MQTVS_Duplicator.duplicateHyperstack

					if (impDZ==null) return;
//					impDZ.show();
//									impDZ.show();
//									impDZ.getWindow().setVisible(false);



					int dupX = 0;
					int dupY = 0;
					int dupW = imp.getWidth();
					int dupH = imp.getHeight();

					if (manualRoi!=null){
						dupX = manualRoi.getBounds().x;
						dupY = manualRoi.getBounds().y;
						dupW = manualRoi.getBounds().width;
						dupH = manualRoi.getBounds().height;
					}

//					impDZ.show();
					RoiManager rm2 = impDZ.getRoiManager();
//					rm2.setVisible(true);

					for (int z = 1; z <= imp.getNSlices() ; z++ ) {

						//						IJ.log(" "+firstZ+" "+z+" "+lastZ+" "+" "+firstT+" "+t+" "+lastT+" ");
						Roi[] sliceSpecficRoiArray = imp.getRoiManager().getSliceSpecificRoiArray(z, loopT, false);
						for (int r = 0; r < sliceSpecficRoiArray.length; r++) {
							if (manualRoi == null)
								manualRoi = new Roi(dupX,dupX,dupW,dupH);
							if( manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getCenterX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getCenterY())
									|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinY())
									|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxY())
									|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxY())
									|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinY())
									){
								Roi nextRoi = (Roi) sliceSpecficRoiArray[r].clone();
								if (nextRoi!=null) {
									nextRoi.setLocation((int) ((Roi) sliceSpecficRoiArray[r]).getBounds().getX()-dupX, (int) ((Roi) sliceSpecficRoiArray[r]).getBounds().getY()-dupY);
									int adjustZ = ((int) ((z-firstZ)*(sliceInterval)+1));
									impDZ.setPosition(1, adjustZ, 1);
									rm2.addRoi(nextRoi);
									impDZ.setRoi(nextRoi);
									//							IJ.run(impDZ, "Draw", "slice");
								}
							}
						}
					}

					rm2.setZSustain(imp.getRoiManager().getZSustain());
					rm2.setTSustain(imp.getRoiManager().getTSustain());
					rm2.showAll(RoiManager.SHOW_ALL);

					//				impDZ.getWindow().setVisible(true);
					//				IJ.runMacro("waitForUser;");

					impDZ.setRoi(0, 0, impDZ.getWidth()-1, impDZ.getHeight()-1);

					//Code above here taken from MQTVS_Duplicator.duplicateHyperstTack

					if (isRGB)
						projImpD[loopC-firstC] = doRGBProjections(impDZ);
					else{
						IJ.runMacro("print(\"\\\\Update:***Making projected view(s) of time-point "+loopT+", channel "+loopC+"...***\")");
						if (firstZ == lastZ)
							projImpD[loopC-firstC] = impDZ;
						else 
							projImpD[loopC-firstC] = doProjections(impDZ);
						IJ.runMacro("print(\"\\\\Update:   Making projected view(s) of time-point "+loopT+", channel "+loopC+"...   \")");
					}

					impDZ.flush();

					IJ.runMacro("print(\"\\\\Update:\\\n \")");

					if ( loopC == firstC && loopT == firstT)  {
//						tempDir.mkdir();
						finalSlices = projImpD[loopC-firstC].getStackSize();
					}
//					projImpD[loopC-firstC].show();
					
					Roi[] roisArray = projImpD[loopC-firstC].getRoiManager().getShownRoisAsArray();
					for (int i=0; i<roisArray.length; i++) {
						Roi nextRoi = (Roi) roisArray[i].clone();
						nextRoi.setPosition(loopC-firstC+1, nextRoi.getZPosition(), ((loopT-firstT)/stepT)+1);
						bigRoiAList.add(((Roi)nextRoi.clone()));
					}
					projImpD[loopC-firstC].getRoiManager().dispose();
					WindowManager.removeWindow(projImpD[loopC-firstC].getRoiManager());
					if (stackC == null)
						stackC = new ImageStack(projImpD[loopC-firstC].getWidth(), projImpD[loopC-firstC].getHeight());

					for (int s=1;s<=projImpD[loopC-firstC].getStackSize();s++) {
						stackC.addSlice(""+s, projImpD[loopC-firstC].getStack().getProcessor(s), s*(loopC-firstC+1)-1);
					}
				}
				projImpDC.setStack(stackC, lastC-firstC+1, stackC.getSize()/(lastC-firstC+1), 1);
				
				IJ.save(projImpDC, tempDir + File.separator + "proj_"+loopT+"_"+loopC+".tif");
				if (!isRGB) 
					projImpDC= new CompositeImage(projImpDC);
				for (loopC = firstC; loopC < lastC +1; loopC++) {
					projImpD[loopC-firstC].flush();
				}
				MultiFileInfoVirtualStack nextStack = new MultiFileInfoVirtualStack(tempDir.getPath()+ File.separator , "",0,0,0, 1, false, false);
				if (buildImp == null)
					buildImp = new ImagePlus();
				buildImp.setOpenAsHyperStack(true);
				buildImp.setStack(nextStack, 1+lastC-firstC, finalSlices, 1+loopT-firstT);
				buildImp.setPosition(1, finalSlices, 1+loopT-firstT);

				finalFrames++;
			}
		}		
		
		
		imp.getProcessor().setColorModel(cm);
		imp.setPosition(inChannel, inSlice, inFrame);
		int origChannel = imp.getChannel();
		if ( imp.isComposite()) {
			((CompositeImage)imp).setMode(stackMode);

		}

		if (imp instanceof CompositeImage && ((CompositeImage) imp).getMode() ==1 ) {
			for (int j = 1; j <= imp.getNChannels(); j++) {
				imp.setPosition(j, imp.getSlice(), imp.getFrame());
			}
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame());
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() + 1);
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() - 1);
		}

		imp.setRoi(manualRoi);
		imp.getWindow().setVisible(true);


		if (rm != null && rmVis) 
			rm.setVisible(true);
		if (mcc != null && mccVis) 
			mcc.setVisible(true);
		if (liw != null && liwVis) 
			liw.setVisible(true);


		finalChannels = lastC - firstC+1;

		finalFrames = (buildImp.getStackSize()/(finalChannels*finalSlices));
		

//		IJ.run( buildImp, 
//				"Stack to Hyperstack...", "order=xyzct channels=" + finalChannels + " slices=" + finalSlices + " frames=" + finalFrames + " display=Composite");
				
		buildImp.setDimensions(finalChannels, finalSlices, finalFrames);

		if ( imp.isComposite() ) {
			CompositeImage buildImp2 = new CompositeImage(buildImp, 0);
			((CompositeImage)buildImp2).copyLuts(imp);
			//buildImp2.show();
			buildImp = buildImp2;
//			((CompositeImage)buildImp).setMode(	(imp.isComposite() && ((CompositeImage)imp).getCompositeMode() >= CompositeImage.RATIO12)?CompositeImage.GRAYSCALE:stackMode);

		}

		if (buildImp==null) return;
		buildImp.setTitle((imp.isSketch3D()?"Sketch3D_":"")+"Projections of "+imp.getTitle().replaceAll("Sketch3D_*", ""));
		
		buildImp.getProcessor().setColorModel(cm);
		if (!buildImp.isComposite()) {
			if (!is16bit) {
				buildImp.setDisplayRange(inMin, inMax);
			}else {
				buildImp.setDisplayRange(0, 255);
			}
		} else {
			if (!is16bit) {
				((CompositeImage)buildImp).setLuts(luts);
			} else {
				((CompositeImage)buildImp).resetDisplayRanges();
			}
		}
		buildImp.setDimensions(finalChannels, finalSlices, finalFrames);

		buildImp.setPosition(inChannel-firstC+1, 1, inFrame-firstT+1);
		if (imp.getMultiChannelController() !=null && imp.isComposite() && ((CompositeImage)imp).getMode() != CompositeImage.GRAYSCALE) {
			IJ.run(buildImp,imp.getMultiChannelController().getChannelLUTChoice(inChannel-firstC),"");
		}
		
		if (imp.getRoiManager().getColorLegend() != null)
			buildImp.getRoiManager().setColorLegend(imp.getRoiManager().getColorLegend().clone(buildImp));
		buildImp.show();

		if (imp != null & buildImp != null){
			buildImp.getWindow().setBackground(imp.getWindow().getBackground());
			buildImp.getWindow().setSubTitleBkgdColor(imp.getWindow().getBackground());
		}
		buildImp.getWindow().setVisible(false);
		
		IJ.runMacro("print(\"\\\\Update:   Arranging Tags...   \")");
		
		if (WindowManager.getImage("Concatenated Stacks") != null)  {
			ImagePlus csImp = WindowManager.getImage("Concatenated Stacks");
			TextRoi.setFont("Arial", csImp.getWidth()/10, Font.ITALIC);		
			TextRoi tr = new TextRoi(0, 0, "Arranging \nTags...\n");
			tr.setStrokeColor(Color.gray);
			tr.setFillColor(Color.decode("#00000000"));

			csImp.setRoi(tr);
			tr.setImage(csImp);
			csImp.getCanvas().paintDoubleBuffered(csImp.getCanvas().getGraphics());
		}
		
		RoiManager bigRM = buildImp.getRoiManager();
		bigRM.setVisible(false);
		for (int r=0; r<bigRoiAList.size(); r++) {
			int c = bigRoiAList.get(r).getCPosition();
			int z = bigRoiAList.get(r).getZPosition();
			int t = bigRoiAList.get(r).getTPosition();
			buildImp.setPosition(c, z, t); 
			bigRM.addRoi(((Roi)bigRoiAList.get(r).clone()));
		}
		bigRM.setZSustain(1);
		bigRM.setTSustain(imp.getRoiManager().getTSustain());
		bigRM.showAll(RoiManager.SHOW_ALL);
		buildImp.setPosition(inChannel-firstC+1, 1, inFrame-firstT+1);

//		buildImp.setRoiManager(bigRM);
		IJ.runMacro("print(\"\\\\Update:\\\n \")");

		if (imp.getMotherImp() != null && !imp.getMotherImp().isSketch3D())
			buildImp.setMotherImp(imp.getMotherImp(), firstT);
		else if (!imp.isSketch3D())
			buildImp.setMotherImp(imp, firstT);
		else
			buildImp.setMotherImp(buildImp, firstT);

		
		while (WindowManager.getImage("Concatenated Stacks") != null) 
			WindowManager.getImage("Concatenated Stacks").close();
		for (ImagePlus ownerImp:buildImp.getImageStack().getOwnerImps()) 
			if (ownerImp.getTitle() == "Concatenated Stacks") {
				buildImp.getImageStack().removeOwnerImp(ownerImp);
				ownerImp.flush();
			}

		if (WindowManager.getImage("BuildStack")!= null) 
			WindowManager.getImage("BuildStack").close();
		
		imp.setPosition(inChannel, inSlice, inFrame);

		if (imp.isComposite() && ((CompositeImage) imp).getMode() ==1 ) {
			((CompositeImage) imp).setMode(2);
			((CompositeImage) imp).setMode(3);
			((CompositeImage) imp).setMode(1);

			for (int j = 1; j <= imp.getNChannels(); j++) {
				imp.setPosition(j, imp.getSlice(),
						imp.getFrame());
			}
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame());
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() + 1);
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() - 1);
			
		}

		buildImp.getWindow().setVisible(true);

	}

	private boolean showDialog() {
		ImageProcessor ip = imp.getProcessor();
		double lower = ip.getMinThreshold();
		if (lower!=ImageProcessor.NO_THRESHOLD) {
			transparencyLower = (int)lower;
			transparencyUpper = (int)ip.getMaxThreshold();
		}
		Calibration cal = imp.getCalibration();
		boolean hyperstack = imp.isHyperStack() && imp.getNFrames()>1;
		GenericDialog gd = new GenericDialog("3D Projection");
		gd.addChoice("Projection method:", methodList, methodList[projectionMethod]);
		gd.addChoice("Axis of rotation:", axisList, axisList[axisOfRotation]);
		//gd.addMessage("");
		gd.addNumericField("Slice spacing ("+cal.getUnits()+"):",cal.pixelDepth,2); 

		gd.addNumericField("Initial angle (0-359 degrees):", initAngle, 0);
		gd.addNumericField("Total rotation (0-359 degrees):", totalAngle, 0);
		gd.addNumericField("Rotation angle increment:", angleInc, 0);
		gd.addNumericField("Lower transparency bound:", transparencyLower, 0);
		gd.addNumericField("Upper transparency bound:", transparencyUpper, 0);
		gd.addNumericField("Opacity (0-100%):", opacity, 0);
		gd.addNumericField("Surface depth-cueing (0-100%):", 100-depthCueSurf, 0);
		gd.addNumericField("Interior depth-cueing (0-100%):", 100-depthCueInt, 0);
		gd.addCheckbox("Interpolate", interpolate);
		gd.addCheckbox("Outline/shadow (nearest point method only)", outlineObjects);
		
		//gd.addCheckbox("Debug Mode:", debugMode);

		gd.addHelp(IJ.URL+"/docs/menus/image.html#project");
		gd.showDialog();
		if (gd.wasCanceled())
			return false;;
		projectionMethod = gd.getNextChoiceIndex();
		axisOfRotation = gd.getNextChoiceIndex();
		cal.pixelDepth = gd.getNextNumber();
		if (cal.pixelWidth==0.0) cal.pixelWidth = 1.0;
		sliceInterval = cal.pixelDepth/cal.pixelWidth;
		initAngle =  (int)gd.getNextNumber();
		totalAngle =  (int)gd.getNextNumber();
		angleInc =  (int)gd.getNextNumber();
		transparencyLower =  (int)gd.getNextNumber();
		transparencyUpper =  (int)gd.getNextNumber();
		opacity =  (int)gd.getNextNumber();
		depthCueSurf =  100-(int)gd.getNextNumber();
		depthCueInt =  100-(int)gd.getNextNumber();
		interpolate =  gd.getNextBoolean();
		outlineObjects =  gd.getNextBoolean();
		return true;
    }

	public boolean showHSDialog(ImagePlus imp) {
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		GenericDialog gd = new GenericDialog("3D Project hyperstack");
		//gd.addStringField("Title:", newTitle, 15);
		gd.setInsets(12, 20, 8);
		gd.addCheckbox("3D Project hyperstack", true);
		gd.addCheckbox("Crop via Tag Manager tags", false);
		int nRangeFields = 0;
		if (nChannels>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Channels (c):", "1-"+nChannels);
			nRangeFields++;
		}
		if (nSlices>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Slices (z):", "1-"+nSlices);
			nRangeFields++;
		}
		if (nFrames>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Frames (t):", ""+ imp.getFrame() +"-"+ imp.getFrame());
			nRangeFields++;
		}
		Vector v = gd.getStringFields();
		TextField[] rangeFields = new TextField[3];
		for (int i=0; i<nRangeFields; i++) {
			rangeFields[i] = (TextField)v.elementAt(i);
			rangeFields[i].addTextListener(this);
		}
		checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
//		dupTitle = gd.getNextString();
		duplicateStack = gd.getNextBoolean();
		sliceSpecificROIs = gd.getNextBoolean();		
		if (nChannels>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double c1 = Tools.parseDouble(range[0]);
			double c2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			firstC = Double.isNaN(c1)?1:(int)c1;
			lastC = Double.isNaN(c2)?firstC:(int)c2;
			if (firstC<1) firstC = 1;
			if (lastC>nChannels) lastC = nChannels;
			if (firstC>lastC) {firstC=1; lastC=nChannels;}
		} else
			firstC = lastC = 1;
		if (nSlices>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double z1 = Tools.parseDouble(range[0]);
			double z2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			firstZ = Double.isNaN(z1)?1:(int)z1;
			lastZ = Double.isNaN(z2)?firstZ:(int)z2;
			if (firstZ<1) firstZ = 1;
			if (lastZ>nSlices) lastZ = nSlices;
			if (firstZ>lastZ) {firstZ=1; lastZ=nSlices;}
		} else
			firstZ = lastZ = 1;
		if (nFrames>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double t1 = Tools.parseDouble(range[0]);
			double t2 = range.length>=2?Tools.parseDouble(range[1]):Double.NaN;
			double t3 = range.length==3?Tools.parseDouble(range[2]):Double.NaN;
			firstT= Double.isNaN(t1)?1:(int)t1;
			lastT = Double.isNaN(t2)?firstT:(int)t2;
			stepT = Double.isNaN(t3)?1:(int)t3;
			if (firstT<1) firstT = 1;
			if (lastT>nFrames) lastT = nFrames;
			if (firstT>lastT) {firstT=1; lastT=nFrames;}
		} else
			firstT = lastT = 1;
		return true;
	}


    public  ImagePlus doRGBProjections(ImagePlus imp) {
    	boolean saveUseInvertingLut = Prefs.useInvertingLut;
    	Prefs.useInvertingLut = false;
        ImageStack[] channels = ChannelSplitter.splitRGB(imp.getStack(), true);
        ImagePlus red = new ImagePlus("Red", channels[0]);
        ImagePlus green = new ImagePlus("Green", channels[1]);
        ImagePlus blue = new ImagePlus("Blue", channels[2]);
        Calibration cal = imp.getCalibration();
        Roi roi = imp.getRoi();
        if (roi!=null)
        	{red.setRoi(roi); green.setRoi(roi); blue.setRoi(roi);}
        red.setCalibration(cal); green.setCalibration(cal); blue.setCalibration(cal);
        label = "Red: ";
        progressBase = 0.0;
        progressScale = 1.0/3.0;
        ImagePlus redProj = doProjections(red);
        red.flush();
        if (redProj==null || done) return null;
        label = "Green: ";
        progressBase = 1.0/3.0;
        ImagePlus greenProj = doProjections(green);
        green.flush();
        if (green==null || done) return null;
        label = "Blue: ";
        progressBase = 2.0/3.0;
        ImagePlus blueProj = doProjections(blue);
        blue.flush();
        if (blue==null || done) return null;
        int w = redProj.getWidth(), h = redProj.getHeight(), d = redProj.getStackSize();
        RGBStackMerge merge = new RGBStackMerge();
        ImageStack stack = merge.mergeStacks(w, h, d, redProj.getStack(), greenProj.getStack(), blueProj.getStack(), true);
        redProj.flush();
        greenProj.flush();
        blueProj.flush();
        Prefs.useInvertingLut = saveUseInvertingLut;
        ImagePlus returnImp = new ImagePlus("Projections of "+imp.getShortTitle(), stack);
        for (int s=1;s<=stack.getSize();s++) {
        	ColorProcessor cp = (ColorProcessor) stack.getProcessor(s);
    		if (projectionMethod == nearestPoint && outlineObjects) {
     			int[][] pixels = cp.getIntArray();
       			int[][] pixelsCopy = Arrays.copyOf(pixels, pixels.length);
       			for (int x = 0; x<cp.getWidth();x++) {
       				for (int y = 0; y<cp.getHeight();y++) {
       					int[] adjacentColors = new int[9];
       					int[] neighborColors = new int[9];
       					int nearReach = 3;
       					neighborColors[0] = pixelsCopy[x-nearReach>0?x-nearReach:x][y-nearReach>0?y-nearReach:y];
       					neighborColors[1] = pixelsCopy[x-nearReach>0?x-nearReach:x][y];
       					neighborColors[2] = pixelsCopy[x-nearReach>0?x-nearReach:x][y+nearReach<cp.getHeight()?y+nearReach:y];
       					neighborColors[3] = pixelsCopy[x][y-nearReach>0?y-nearReach:y];
       					neighborColors[4] = pixelsCopy[x][y];
       					neighborColors[5] = pixelsCopy[x][y+nearReach<cp.getHeight()?y+nearReach:y];
       					neighborColors[6] = pixelsCopy[x+nearReach<cp.getWidth()?x+nearReach:x][y-nearReach>0?y-nearReach:y];
       					neighborColors[7] = pixelsCopy[x+nearReach<cp.getWidth()?x+nearReach:x][y];
       					neighborColors[8] = pixelsCopy[x+nearReach<cp.getWidth()?x+nearReach:x][y+nearReach<cp.getHeight()?y+nearReach:y];

       					adjacentColors[0] = pixelsCopy[x-1>0?x-1:x][y-1>0?y-1:y];
       					adjacentColors[1] = pixelsCopy[x-1>0?x-1:x][y];
       					adjacentColors[2] = pixelsCopy[x-1>0?x-1:x][y+1<cp.getHeight()?y+1:y];
       					adjacentColors[3] = pixelsCopy[x][y-1>0?y-1:y];
       					adjacentColors[4] = pixelsCopy[x][y];
       					adjacentColors[5] = pixelsCopy[x][y+1<cp.getHeight()?y+1:y];
       					adjacentColors[6] = pixelsCopy[x+1<cp.getWidth()?x+1:x][y-1>0?y-1:y];
       					adjacentColors[7] = pixelsCopy[x+1<cp.getWidth()?x+1:x][y];
       					adjacentColors[8] = pixelsCopy[x+1<cp.getWidth()?x+1:x][y+1<cp.getHeight()?y+1:y];

       					for (int neighborColor:neighborColors) {	
   							cp.set(x, y, new Color(pixelsCopy[x][y]).getRGB());
       						if (neighborColor != pixelsCopy[x][y] ) { //finds pixel near edge? 
       							cp.set(x, y, new Color(pixelsCopy[x][y]).darker().getRGB());
       						}
       					}
       					for (int adjacentColor:adjacentColors) {				
       						if (adjacentColor != pixelsCopy[x][y] ) { //finds edge pixel
       							cp.set(x, y, new Color(pixelsCopy[x][y]).darker().darker().getRGB());
       						}
       					}
       				}
    			}
    		}

        }
        return returnImp;
    }

	public ImagePlus doProjections(ImagePlus imp) {
		int nSlices;				// number of slices in volume
		int projwidth, projheight;	//dimensions of projection image
		int xcenter, ycenter, zcenter;	//coordinates of center of volume of rotation
		int theta;				//current angle of rotation in degrees
		double thetarad;			//current angle of rotation in radians
		int sintheta, costheta;		//sine and cosine of current angle
		int offset;
		int curval, prevval, nextval, aboveval, belowval;
		int n, nProjections, angle;
		boolean minProjSize = true;

		stack = imp.getStack();
		if ((angleInc==0) && (totalAngle!=0))
			angleInc = 5;
		boolean negInc = angleInc<0;
		if (negInc) angleInc = -angleInc;
		angle = 0;
		nProjections = 0;
		if (angleInc==0)
			nProjections = 1;
		else {
			while (angle<=totalAngle) {
				nProjections++;
				angle += angleInc;
			}
		}
		if (angle>360)
			nProjections--;
		if (nProjections<=0)
			nProjections = 1;
		if (negInc) angleInc = -angleInc;

		ImageProcessor ip = imp.getProcessor();
		Rectangle r = ip.getRoi();
		left = r.x;
		top = r.y;
		right = r.x + r.width;
		bottom = r.y + r.height;
		nSlices = imp.getStackSize();
		imageWidth = imp.getWidth();
		width = right - left;
		height = bottom - top;
		xcenter = (left + right)/2;          //find center of volume of rotation
		ycenter = (top + bottom)/2;
		zcenter = (int)(nSlices*sliceInterval/2.0+0.5);

		projwidth = 0;
		projheight = 0;
		if (minProjSize && axisOfRotation!=zAxis) {
			switch (axisOfRotation) {
			case xAxis:
				projheight = (int)(Math.sqrt(nSlices*sliceInterval*nSlices*sliceInterval+height*height) + 0.5);
				projwidth = width;
				break;
			case yAxis:
				projwidth = (int)(Math.sqrt(nSlices*sliceInterval*nSlices*sliceInterval+width*width) + 0.5);
				projheight = height;
				break;
			}
		} else {
			projwidth = (int) (Math.sqrt (nSlices*sliceInterval*nSlices*sliceInterval+width*width) + 0.5);
			projheight = (int) (Math.sqrt (nSlices*sliceInterval*nSlices*sliceInterval+height*height) + 0.5);
		}
		if ((projwidth%2)==1)
			projwidth++;
		int projsize = projwidth * projheight;
//		IJ.log(projwidth+" "+projheight);
		
		if (projwidth<=0 || projheight<=0) {
			IJ.error("'projwidth' or 'projheight' <= 0");
			return null;
		}
		try {
			allocateArrays(nProjections, projwidth, projheight);
		}  catch(OutOfMemoryError e) {
			Object[] images = stack2.getImageArray();
			if (images!=null)
				for (int i=0; i<images.length; i++) images[i]=null;
			stack2 = null;
			IJ.error("Projector - Out of Memory",
					"To use less memory, use a rectanguar\n"
					+"selection,  reduce \"Total Rotation\",\n"
					+"and/or increase \"Angle Increment\"."
			);
			return null;
		}
		ImagePlus projections = new ImagePlus("Projections of "+imp.getShortTitle(), stack2);
		projImpD[loopC-firstC] = projections;
		
		roiArray = imp.getRoiManager().getShownRoisAsArray();
		
		rmProj = projections.getRoiManager();
//		rmProj.showAll(RoiManager.SHOW_ALL);
//		rmProj.setVisible(false);
		

		projections.setCalibration(imp.getCalibration());
		//projections.show();

		IJ.resetEscape();
		theta = initAngle;
		IJ.resetEscape();
		for (n=0; n<nProjections; n++) {
			IJ.showStatus(n+"/"+nProjections);
			if (!batchMode) IJ.showProgress((double)n/nProjections);
			thetarad = theta * Math.PI/180.0;
			costheta = (int)(BIGPOWEROF2*Math.cos(thetarad) + 0.5);
			sintheta = (int)(BIGPOWEROF2*Math.sin(thetarad) + 0.5);

			projArray = (byte[])stack2.getPixels(n+1);
			if (projArray==null)
				break;
			if ((projectionMethod==nearestPoint) || (opacity>0)) {
				for (int i=0; i<projsize; i++)
					zBuffer[i] = (short)32767;
			}
			if ((opacity>0) && (projectionMethod!=nearestPoint)) {
				for (int i=0; i<projsize; i++)
					opaArray[i] = (byte)0;
			}
			if ((projectionMethod==brightestPoint) && (depthCueInt<100)) {
				for (int i=0; i<projsize; i++)
					brightCueArray[i] = (byte)0;
				for (int i=0; i<projsize; i++)
					cueZBuffer[i] = (short)0;
			}
			if (projectionMethod==meanValue) {
				for (int i=0; i<projsize; i++)
					sumBuffer[i] = 0;
				for (int i=0; i<projsize; i++)
					countBuffer[i] = (short)0;
			}
			switch (axisOfRotation) {
			case xAxis:
				doOneProjectionX (n+1, nSlices, ycenter, zcenter,projwidth, projheight, costheta, sintheta);
				break;
			case yAxis:
				doOneProjectionY (n+1, nSlices, xcenter, zcenter,projwidth, projheight, costheta, sintheta);
				break;
			case zAxis:
				doOneProjectionZ (n+1, nSlices, xcenter, ycenter, zcenter, projwidth, projheight, costheta, sintheta);
				break;
			}

			if (projectionMethod==meanValue) {
				int count;
				for (int i=0; i<projsize; i++) {
					count = countBuffer[i];
					if (count!=0)
						projArray[i] = (byte)(sumBuffer[i]/count);
				}
			}
			if ((opacity>0) && (projectionMethod!=nearestPoint)) {
				for (int i=0; i<projsize; i++)
					projArray[i] = (byte)((opacity*(opaArray[i]&0xff) + (100-opacity)*(projArray[i] &0xff))/100);
			}
			if (axisOfRotation==zAxis) {
				for (int i=projwidth; i<(projsize-projwidth); i++) {
					curval = projArray[i]&0xff;
					prevval = projArray[i-1]&0xff;
					nextval = projArray[i+1]&0xff;
					aboveval = projArray[i-projwidth]&0xff;
					belowval = projArray[i+projwidth]&0xff;
					if ((curval==0)&&(prevval!=0)&&(nextval!=0)&&(aboveval!=0)&&(belowval!=0))
						projArray[i] = (byte)((prevval+nextval+aboveval+belowval)/4);
				}
			}

			theta = (theta + angleInc)%360;
			/*if (projections.getWindow()==null && IJ.getInstance()!=null && !batchMode)   // is "Projections" window still open?
				{done=true; break;}*/
			if (IJ.escapePressed())
			{done=true; break;}
			projections.setSlice(n+1);
			if (IJ.escapePressed())
			{IJ.beep(); break;}
		} //end for all projections
		if (!batchMode) IJ.showProgress(1.0);

		if (debugMode) {
			if (projArray!=null) new ImagePlus("projArray", new ByteProcessor(projwidth, projheight, projArray, null)).show();
			if (opaArray!=null) new ImagePlus("opaArray", new ByteProcessor(projwidth, projheight, opaArray, null)).show();
			if (brightCueArray!=null) new ImagePlus("brightCueArray", new ByteProcessor(projwidth, projheight, brightCueArray, null)).show();
			if (zBuffer!=null) new ImagePlus("zBuffer", new ShortProcessor(projwidth, projheight, zBuffer, null)).show();
			if (cueZBuffer!=null) new ImagePlus("cueZBuffer", new ShortProcessor(projwidth, projheight, cueZBuffer, null)).show();
			if (countBuffer!=null) new ImagePlus("countBuffer", new ShortProcessor(projwidth, projheight, countBuffer, null)).show();
			if (sumBuffer!=null) {
				float[] tmp = new float[projwidth*projheight];
				for (int i=0; i<projwidth*projheight; i++)
					tmp[i] = sumBuffer[i];
				new ImagePlus("sumBuffer", new FloatProcessor(projwidth, projheight, tmp, null)).show();
			}
		}
		//projections.show();
		return projections;

	} // doProjection()


	void allocateArrays(int nProjections, int projwidth, int projheight) {
		int projsize = projwidth*projheight;
		ColorModel cm = imp.getProcessor().getColorModel();
		if (isRGB) cm = null;
		stack2 = new ImageStack(projwidth, projheight, cm);
		projArray = new byte[projsize];
		for (int i=0; i<nProjections; i++)
			stack2.addSlice(null, new byte[projsize]);
		if ((projectionMethod==nearestPoint) || (opacity > 0))
			zBuffer = new short[projsize];		
		if ((opacity>0) && (projectionMethod!=nearestPoint))
			opaArray = new byte[projsize];
		if ((projectionMethod==brightestPoint) && (depthCueInt<100)) {
			brightCueArray = new byte[projsize];
			cueZBuffer = new short[projsize];
		}
		if (projectionMethod==meanValue) {
			sumBuffer = new int[projsize];
			countBuffer = new short[projsize];
		}
	}


	/**
	This method projects each pixel of a volume (stack of slices) onto a plane as the volume rotates about the x-axis. Integer
	arithmetic, precomputation of values, and iterative addition rather than multiplication inside a loop are used extensively
	to make the code run efficiently. Projection parameters stored in global variables determine how the projection will be performed.
	This procedure returns various buffers which are actually used by DoProjections() to find the final projected image for the volume
	of slices at the current angle.
	 * @param sintheta2 
	 */
	void doOneProjectionX (int projSlice, int nSlices, int ycenter, int zcenter, int projwidth, int projheight, int costheta, int sintheta) {
		int     thispixel;			//current pixel to be projected
		int    offset, offsetinit;		//precomputed offsets into an image buffer
		int z;					//z-coordinate of points in current slice before rotation
		int ynew, znew;			//y- and z-coordinates of current point after rotation
		int zmax, zmin;			//z-coordinates of first and last slices before rotation
		int zmaxminuszmintimes100;	//precomputed values to save time in loops
		int c100minusDepthCueInt, c100minusDepthCueSurf;
		boolean DepthCueIntLessThan100, DepthCueSurfLessThan100;
		boolean OpacityOrNearestPt, OpacityAndNotNearestPt;
		boolean MeanVal, BrightestPt;
		int ysintheta, ycostheta;
		int zsintheta, zcostheta, ysinthetainit, ycosthetainit;
		byte[] pixels;
		int projsize = projwidth * projheight;

		//find z-coordinates of first and last slices
		zmax = zcenter + projheight/2;  
		zmin = zcenter - projheight/2;
		zmaxminuszmintimes100 = 100 * (zmax-zmin);
		c100minusDepthCueInt = 100 - depthCueInt;
		c100minusDepthCueSurf = 100 - depthCueSurf;
		DepthCueIntLessThan100 = (depthCueInt < 100);
		DepthCueSurfLessThan100 = (depthCueSurf < 100);
		OpacityOrNearestPt = ((projectionMethod==nearestPoint) || (opacity>0));
		OpacityAndNotNearestPt = ((opacity>0) && (projectionMethod!=nearestPoint));
		MeanVal = (projectionMethod==meanValue);
		BrightestPt = (projectionMethod==brightestPoint);
		ycosthetainit = (top - ycenter - 1) * costheta;
		ysinthetainit = (top - ycenter - 1) * sintheta;
		offsetinit = ((projheight-bottom+top)/2) * projwidth + (projwidth - right + left)/2 - 1;
		
		for(int r=0; r < roiArray.length; r++) {
			//			for (int k=1; k<=nSlices; k++) {
			z = (int)((roiArray[r].getZPosition()-1)*(imp.getCalibration().pixelDepth/imp.getCalibration().pixelWidth)+0.5) - zcenter;
			zcostheta = z * costheta;
			zsintheta = z * sintheta;
			ycostheta = ycosthetainit;
			ysintheta = ysinthetainit;
			//				for (int j=top; j<bottom; j++) {
			ycostheta = ycostheta + (int) (costheta * roiArray[r].getBounds().getCenterY()) ;  //rotate about x-axis and find new y,z
			ysintheta = ysintheta + (int) (sintheta * roiArray[r].getBounds().getCenterY());  //x-coordinates will not change
			ynew = (ycostheta - zsintheta)/BIGPOWEROF2 + ycenter - top;
			znew = (ysintheta + zcostheta)/BIGPOWEROF2 + zcenter;

			ynew = (offsetinit/projwidth) +ynew;
			
			Roi nextRoi = (Roi) roiArray[r].clone();
			nextRoi.setLocation( (int)(roiArray[r].getBounds().getCenterX() - nextRoi.getBounds().getWidth()/2), (int) ( ynew - nextRoi.getBounds().getHeight()/2));
			projImpD[loopC-firstC].setSlice(projSlice);
			rmProj.addRoi(nextRoi);
//			imp.getRoiManager().dispose();
//			WindowManager.removeWindow(imp.getRoiManager());

		}

		
		for (int k=1; k<=nSlices; k++) {
			pixels = (byte[])stack.getPixels(k);
			z = (int)((k-1)*sliceInterval+0.5) - zcenter;
			zcostheta = z * costheta;
			zsintheta = z * sintheta;
			ycostheta = ycosthetainit;
			ysintheta = ysinthetainit;
			for (int j=top; j<bottom; j++) {
				ycostheta += costheta;  //rotate about x-axis and find new y,z
				ysintheta += sintheta;  //x-coordinates will not change
				ynew = (ycostheta - zsintheta)/BIGPOWEROF2 + ycenter - top;
				znew = (ysintheta + zcostheta)/BIGPOWEROF2 + zcenter;
				offset = offsetinit + ynew * projwidth;
				//GetLine (BoundRect.left, j, width, theLine, Info->PicBaseAddr);
				//read each pixel in current row and project it
				int lineIndex = j*imageWidth;
				for (int i=left; i<right; i++) {
					thispixel = pixels[lineIndex+i]&0xff;
					offset++;
					//if (stack2.getSize()==32 && j==32 && i==32) IJ.write("thispixel: "+thispixel+ " "+lineIndex);
					if ((offset>=projsize) || (offset<0))
						offset = 0;
					if ((thispixel <= transparencyUpper) && (thispixel >= transparencyLower)) {
						if (OpacityOrNearestPt) {
							if (znew<zBuffer[offset]) {
								zBuffer[offset] = (short)znew;
								if (OpacityAndNotNearestPt) {
									if (DepthCueSurfLessThan100)
										opaArray[offset] = (byte)(/*255 -*/ (depthCueSurf*(/*255-*/thispixel)/100 + 
												c100minusDepthCueSurf*(/*255-*/thispixel)*(zmax-znew)/zmaxminuszmintimes100));
									else
										opaArray[offset] = (byte)thispixel;
								} else {
									//p = (BYTE *)(projaddr + offset);
									if (DepthCueSurfLessThan100)
										projArray[offset] = (byte)(/*255 -*/ (depthCueSurf*(/*255-*/thispixel)/100 +
												c100minusDepthCueSurf*(/*255-*/thispixel)*(zmax-znew)/zmaxminuszmintimes100));
									else
										projArray[offset]  = (byte)thispixel;
								}
							} // if znew<zBuffer[offset]
						} //if OpacityOrNearestP
						if (MeanVal) {
							//sp = (long *)sumbufaddr;
							sumBuffer[offset] += thispixel;
							//cp = (short int *)countbufaddr;
							countBuffer[offset]++;
						} else
							if (BrightestPt) {
								if (DepthCueIntLessThan100) {
									if ((thispixel>(brightCueArray[offset]&0xff)) || (thispixel==(brightCueArray[offset]&0xff)) && (znew>cueZBuffer[offset])) {
										brightCueArray[offset] = (byte)thispixel;  //use z-buffer to ensure that if depth-cueing is on,
										cueZBuffer[offset] = (short)znew;       //the closer of two equally-bright points is displayed.
										projArray[offset] = (byte)((depthCueInt*thispixel/100 +
												c100minusDepthCueInt*thispixel*(zmax-znew)/zmaxminuszmintimes100));
									}
								} else {
									if (thispixel>(projArray[offset]&0xff))
										projArray[offset] = (byte)thispixel;
								}
							} // else BrightestPt
					} // if thispixel in range
				} //for i (all pixels in row)
			} // for j (all rows of BoundRect)
		} // for k (all slices)
	} //  doOneProjectionX()


	/** Projects each pixel of a volume (stack of slices) onto a plane as the volume rotates about the y-axis. */
	void  doOneProjectionY (int projSlice, int nSlices, int xcenter, int zcenter, int projwidth, int projheight, int costheta, int sintheta) {
		//IJ.write("DoOneProjectionY: "+xcenter+" "+zcenter+" "+(double)costheta/BIGPOWEROF2+ " "+(double)sintheta/BIGPOWEROF2);
		int thispixel;			//current pixel to be projected
		int offset, offsetinit;		//precomputed offsets into an image buffer
		int z;					//z-coordinate of points in current slice before rotation
		int xnew, znew;			//y- and z-coordinates of current point after rotation
		int zmax, zmin;			//z-coordinates of first and last slices before rotation
		int zmaxminuszmintimes100; //precomputed values to save time in loops
		int c100minusDepthCueInt, c100minusDepthCueSurf;
		boolean DepthCueIntLessThan100, DepthCueSurfLessThan100;
		boolean OpacityOrNearestPt, OpacityAndNotNearestPt;
		boolean MeanVal, BrightestPt;
		int xsintheta, xcostheta;
		int zsintheta, zcostheta, xsinthetainit, xcosthetainit;
		byte[] pixels;
		int projsize = projwidth * projheight;

		//find z-coordinates of first and last slices
		zmax = zcenter + projwidth/2;  
		zmin = zcenter - projwidth/2;
		zmaxminuszmintimes100 = 100 * (zmax-zmin);
		c100minusDepthCueInt = 100 - depthCueInt;
		c100minusDepthCueSurf = 100 - depthCueSurf;
		DepthCueIntLessThan100 = (depthCueInt < 100);
		DepthCueSurfLessThan100 = (depthCueSurf < 100);
		OpacityOrNearestPt = ((projectionMethod==nearestPoint) || (opacity>0));
		OpacityAndNotNearestPt = ((opacity>0) && (projectionMethod!=nearestPoint));
		MeanVal = (projectionMethod==meanValue);
		BrightestPt = (projectionMethod==brightestPoint);
		xcosthetainit = (left - xcenter - 1) * costheta;
		xsinthetainit = (left - xcenter - 1) * sintheta;
		
		
		for(int r=0; r < roiArray.length; r++) {
			//			for (int k=1; k<=nSlices; k++) {
			z = (int)((roiArray[r].getZPosition()-1)*(imp.getCalibration().pixelDepth/imp.getCalibration().pixelWidth)+0.5) - zcenter;
			zcostheta = z * costheta;
			zsintheta = z * sintheta;
			offsetinit = ((projheight-bottom+top)/2) * projwidth +(projwidth - right + left)/2 - projwidth;
//			IJ.log(""+offsetinit);
//			for (int j=top; j<bottom; j++) {
			xcostheta = xcosthetainit;
			xsintheta = xsinthetainit;
			offsetinit = offsetinit + (int) (projwidth * roiArray[r].getBounds().getCenterY());
//			for (int i=left; i<right; i++) 
			xcostheta = xcostheta + (int) (costheta * (roiArray[r].getBounds().getCenterX())) ;  //rotate about y-axis and find new x,z
			xsintheta = xsintheta + (int) (sintheta * (roiArray[r].getBounds().getCenterX()));  //y-coordinates will not change
			xnew = (xcostheta + zsintheta)/BIGPOWEROF2 + xcenter - left;
			znew = (zcostheta - xsintheta)/BIGPOWEROF2 + zcenter;
			offset = offsetinit + xnew;

			xnew = xnew + ((projheight-bottom+top)/2) * projwidth +(projwidth - right + left)/2;
//			ynew = (offset/projwidth) + roiArray[r].getBounds().getCenterY();
			
			Roi nextRoi = (Roi) roiArray[r].clone();
			nextRoi.setLocation( (int) (xnew - nextRoi.getBounds().getWidth()/2), (int)((roiArray[r].getBounds().getCenterY() - nextRoi.getBounds().getHeight()/2)));
			projImpD[loopC-firstC].setSlice(projSlice);
			rmProj.addRoi(((Roi)nextRoi.clone()));
//			if (imp.getRoiManager() != null){
//				imp.getRoiManager().dispose();
//				WindowManager.removeWindow(imp.getRoiManager());
//			}
		}

		
		for (int k=1; k<=nSlices; k++) {
			pixels = (byte[])stack.getPixels(k);
			z = (int)((k-1)*sliceInterval+0.5) - zcenter;
			zcostheta = z * costheta;
			zsintheta = z * sintheta;
			offsetinit = ((projheight-bottom+top)/2) * projwidth +(projwidth - right + left)/2 - projwidth;
			for (int j=top; j<bottom; j++) {
				xcostheta = xcosthetainit;
				xsintheta = xsinthetainit;
				offsetinit += projwidth;
				int lineOffset = j*imageWidth;
				//read each pixel in current row and project it
				for (int i=left; i<right; i++) {
					thispixel =pixels[lineOffset+i]&0xff;
					xcostheta += costheta;  //rotate about x-axis and find new x,z
					xsintheta += sintheta;  //y-coordinates will not change
					//if (k==1 && j==top) IJ.write(k+" "thispixel);
					if ((thispixel <= transparencyUpper) && (thispixel >= transparencyLower)) {
						xnew = (xcostheta + zsintheta)/BIGPOWEROF2 + xcenter - left;
						znew = (zcostheta - xsintheta)/BIGPOWEROF2 + zcenter;
						offset = offsetinit + xnew;
						if ((offset>=projsize) || (offset<0))
							offset = 0;
						if (OpacityOrNearestPt) {
							if (znew<zBuffer[offset]) {
								zBuffer[offset] = (short)znew;
								if (OpacityAndNotNearestPt) {
									if (DepthCueSurfLessThan100)
										opaArray[offset] = (byte)((depthCueSurf*thispixel/100 + 
												c100minusDepthCueSurf*thispixel*(zmax-znew)/zmaxminuszmintimes100));
									else
										opaArray[offset] = (byte)thispixel;
								} else {
									if (DepthCueSurfLessThan100)
										projArray[offset] = (byte)((depthCueSurf*thispixel/100 +
												c100minusDepthCueSurf*thispixel*(zmax-znew)/zmaxminuszmintimes100));
									else
										projArray[offset] = (byte)thispixel;
								}
							} // if (znew < zBuffer[offset])
						} // if (OpacityOrNearestPt)
						if (MeanVal) {
							sumBuffer[offset] += thispixel;
							countBuffer[offset]++;
						} else if (BrightestPt) {
							if (DepthCueIntLessThan100) {
								if ((thispixel>(brightCueArray[offset]&0xff)) || (thispixel==(brightCueArray[offset]&0xff)) && (znew>cueZBuffer[offset])) {
									brightCueArray[offset] = (byte)thispixel;  //use z-buffer to ensure that if depth-cueing is on,
									cueZBuffer[offset] = (short)znew;       //the closer of two equally-bright points is displayed.
									projArray[offset] = (byte)((depthCueInt*thispixel/100 +
											c100minusDepthCueInt*thispixel*(zmax-znew)/zmaxminuszmintimes100));
								}
							} else {
								if (thispixel > (projArray[offset]&0xff))
									projArray[offset] = (byte)thispixel;
							}
						} // if  BrightestPt
					} //end if thispixel in range
				} // for i (all pixels in row)
			} // for j (all rows)
		} // for k (all slices)
	} // DoOneProjectionY()


	/** Projects each pixel of a volume (stack of slices) onto a plane as the volume rotates about the z-axis. */
	void doOneProjectionZ (int projSlice, int nSlices, int xcenter, int ycenter, int zcenter, int projwidth, int projheight, int costheta, int sintheta) {
		int thispixel;        //current pixel to be projected
		int offset, offsetinit; //precomputed offsets into an image buffer
		int z;   //z-coordinate of points in current slice before rotation
		int xnew, ynew; //y- and z-coordinates of current point after rotation
		int zmax, zmin; //z-coordinates of first and last slices before rotation
		int zmaxminuszmintimes100; //precomputed values to save time in loops
		int c100minusDepthCueInt, c100minusDepthCueSurf;
		boolean DepthCueIntLessThan100, DepthCueSurfLessThan100;
		boolean OpacityOrNearestPt, OpacityAndNotNearestPt;
		boolean MeanVal, BrightestPt;
		int xsintheta, xcostheta, ysintheta, ycostheta;
		int xsinthetainit, xcosthetainit, ysinthetainit, ycosthetainit;
		byte[] pixels;
		int projsize = projwidth * projheight;

		//find z-coordinates of first and last slices
		//zmax = zcenter + projwidth/2;  
		//zmin = zcenter - projwidth/2;
		zmax = (int)((nSlices-1)*sliceInterval+0.5) - zcenter;
		zmin = -zcenter;

		zmaxminuszmintimes100 = 100 * (zmax-zmin);
		c100minusDepthCueInt = 100 - depthCueInt;
		c100minusDepthCueSurf = 100 - depthCueSurf;
		DepthCueIntLessThan100 = (depthCueInt < 100);
		DepthCueSurfLessThan100 = (depthCueSurf < 100);
		OpacityOrNearestPt = ((projectionMethod==nearestPoint) || (opacity>0));
		OpacityAndNotNearestPt = ((opacity>0) && (projectionMethod!=nearestPoint));
		MeanVal = (projectionMethod==meanValue);
		BrightestPt = (projectionMethod==brightestPoint);
		xcosthetainit = (left - xcenter - 1) * costheta;
		xsinthetainit = (left - xcenter - 1) * sintheta;
		ycosthetainit = (top - ycenter - 1) * costheta;
		ysinthetainit = (top - ycenter - 1) * sintheta;
		//float[] f = new float[projsize];
		//IJ.write("");
		//IJ.write("depthCueSurf: "+depthCueSurf);
		//IJ.write("zmax: "+zmax);
		//IJ.write("zmin: "+zmin);
		//IJ.write("zcenter: "+zcenter);
		//IJ.write("zmaxminuszmintimes100: "+zmaxminuszmintimes100);
		//IJ.write("c100minusDepthCueSurf: "+c100minusDepthCueSurf);
		offsetinit = ((projheight-bottom+top)/2) * projwidth + (projwidth - right + left)/2 - 1;
		for (int k=1; k<=nSlices; k++) {
			pixels = (byte[])stack.getPixels(k);
			z = (int)((k-1)*sliceInterval+0.5) - zcenter;
			ycostheta = ycosthetainit;
			ysintheta = ysinthetainit;
			for (int j=top; j<bottom; j++) {
				ycostheta += costheta;
				ysintheta += sintheta;
				xcostheta = xcosthetainit;
				xsintheta = xsinthetainit;
				//GetLine (BoundRect.left, j, width, theLine, Info->PicBaseAddr);
				int lineIndex = j*imageWidth;
				//read each pixel in current row and project it
				for (int i=left; i<right; i++) {
					thispixel = pixels[lineIndex+i]&0xff;
					xcostheta += costheta;  //rotate about x-axis and find new y,z
					xsintheta += sintheta;  //x-coordinates will not change
					if ((thispixel <= transparencyUpper) && (thispixel >= transparencyLower)) {
						xnew = (xcostheta - ysintheta)/BIGPOWEROF2 + xcenter - left;
						ynew = (xsintheta + ycostheta)/BIGPOWEROF2 + ycenter - top;
						offset = offsetinit + ynew * projwidth + xnew;
						if ((offset>=projsize) || (offset<0))
							offset = 0;
						if (OpacityOrNearestPt) {
							if (z<zBuffer[offset]) {
								zBuffer[offset] = (short)z;
								if (OpacityAndNotNearestPt) {
									if (DepthCueSurfLessThan100)
										opaArray[offset] = (byte)((depthCueSurf*(thispixel)/100 +  c100minusDepthCueSurf*(thispixel)*(zmax-z)/zmaxminuszmintimes100));
									else
										opaArray[offset] = (byte)thispixel;
								} else {
									if (DepthCueSurfLessThan100) {
										int v = (depthCueSurf*thispixel/100 + c100minusDepthCueSurf*thispixel*(zmax-z)/zmaxminuszmintimes100);
										//f[offset] = z;
										projArray[offset] = (byte)v;
									} else
										projArray[offset] = (byte)thispixel;
								}
							} // if z<zBuffer[offset]
						} // OpacityOrNearestPt
						if (MeanVal) {
							sumBuffer[offset] += thispixel;
							countBuffer[offset]++;
						} else if (BrightestPt) {
							if (DepthCueIntLessThan100) {
								if ((thispixel>(brightCueArray[offset]&0xff)) || (thispixel==(brightCueArray[offset]&0xff)) && (z>cueZBuffer[offset])) {
									brightCueArray[offset] = (byte)thispixel;  //use z-buffer to ensure that if depth-cueing is on,
									cueZBuffer[offset] = (short)z;       //the closer of two equally-bright points is displayed.
									projArray[offset] = (byte)((depthCueInt*(thispixel)/100 + c100minusDepthCueInt*(thispixel)*(zmax-z)/zmaxminuszmintimes100));
								}
							} else {
								//p = (BYTE *)(projaddr + offset);
								if (thispixel > (projArray[offset]&0xff))
									projArray[offset] = (byte)thispixel;
							}
						} // else BrightestPt
					} //if thispixel in range
				} //for i (all pixels in row)
			} // for j (all rows of BoundRect)
		} // for k (all slices)
		//new ImagePlus("f", new FloatProcessor(projwidth,projheight,f,null)).show();
	} // end doOneProjectionZ()

	public ImagePlus zScale(ImagePlus imp) {
		IJ.showStatus("Z Scaling...");
		ImageStack stack1 = imp.getStack();
		int depth1 = stack1.getSize();
		ImagePlus imp2 = null;
		String title = imp.getTitle();
		ImageProcessor ip = imp.getProcessor();
		ColorModel cm = ip.getColorModel();
		int width1 = imp.getWidth();
		int height1 = imp.getHeight();
		Rectangle r = ip.getRoi();
		int width2 = r.width;
		int height2 = r.height;
		int depth2 = (int)(stack1.getSize()*sliceInterval+0.5);
		imp2 = NewImage.createImage(title, width2, height2, depth2, isRGB?24:8, NewImage.FILL_BLACK, false);
		if (imp2==null || depth2!=imp2.getStackSize()) return null;
		ImageStack stack2 = imp2.getStack();
		ImageProcessor xzPlane1 = ip.createProcessor(width2, depth1);
		xzPlane1.setInterpolate(true);
		ImageProcessor xzPlane2;		
		int[] line = new int[width2];
		for (int y=0; y<height2; y++) {
			for (int z=0; z<depth1; z++) {
				if (isRGB)
					getRGBRow(stack1, r.x, r.y+y, z, width1, width2, line);
				else
					getByteRow(stack1, r.x, r.y+y, z, width1, width2, line);
				xzPlane1.putRow(0, z, line, width2);
			}
			//if (y==r.y) new ImagePlus("xzPlane", xzPlane1).show();
			xzPlane2 = xzPlane1.resize(width2, depth2);
			for (int z=0; z<depth2; z++) {
				xzPlane2.getRow(0, z, line, width2);
				if (isRGB)
					putRGBRow(stack2, y, z, width2, line);
				else
					putByteRow(stack2, y, z, width2, line);
			}
			if (!batchMode) IJ.showProgress(y, height2-1);
		}
		//imp2.show();
		//imp2.setCalibration(imp.getCalibration());
		ImageProcessor ip2 = imp2.getProcessor();
		ip2.setColorModel(cm);
		imp2.setDimensions(1, imp2.getStackSize(), 1);
		return imp2;
	}

	public void getByteRow(ImageStack stack, int x, int y, int z, int width1, int width2, int[] line) {
		byte[] pixels = (byte[])stack.getPixels(z+1);
		int j = x + y*width1;
		for (int i=0; i<width2; i++)
			line[i] = pixels[j++]&255;
	}

	public void putByteRow(ImageStack stack, int y, int z, int width, int[] line) {
		byte[] pixels = (byte[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			pixels[j++] = (byte)line[i];
	}

	public void getRGBRow(ImageStack stack, int x, int y, int z, int width1, int width2, int[] line) {
		int[] pixels = (int[])stack.getPixels(z+1);
		int j = x + y*width1;
		for (int i=0; i<width2; i++)
			line[i] = pixels[j++];
	}

	public void putRGBRow(ImageStack stack, int y, int z, int width, int[] line) {
		int[] pixels = (int[])stack.getPixels(z+1);
		int j = y*width;
		for (int i=0; i<width; i++)
			pixels[j++] = line[i];
	}

	public void textValueChanged(TextEvent e) {
		checkbox.setState(true);
	}

}
