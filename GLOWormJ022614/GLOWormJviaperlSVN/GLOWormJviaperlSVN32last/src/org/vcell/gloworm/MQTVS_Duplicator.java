package org.vcell.gloworm;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.Date;
import java.util.Vector;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.FileInfo;
import ij.util.Tools;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.MultiFileInfoVirtualStack;
import ij.plugin.PlugIn;
import ij.plugin.filter.Filler;
import ij.plugin.frame.Channels;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;

/** This plugin implements the Image/Duplicate command.
<pre>
   // test script
   img1 = IJ.getImage();
   img2 = new Duplicator().run(img1);
   //img2 = new Duplicator().run(img1,1,10);
   img2.show();
</pre>
 */
public class MQTVS_Duplicator implements PlugIn, TextListener {
	private static boolean duplicateStack;
	private boolean duplicateSubstack;
	private int first, last;
	private Checkbox checkbox;
	private TextField rangeField;
	private TextField[] rangeFields;
	private int firstC = 1;
	private int lastC = 1;
	private int firstZ = 1;
	private int lastZ = 1;
	private int firstT = 1;
	private int lastT = 1;
	private boolean sliceSpecificROIs;
	private int stepT = 1;
	private int finalFrames;
	private int finalT;
	private boolean copyMergedImage;
	private String dupTitle;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		imp.getWindow().dupButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/download_button_animatedSmall.gif")));
		copyMergedImage = (imp.isComposite() && ((CompositeImage)imp).getCompositeMode() >= CompositeImage.RATIO12);
		int stackSize = imp.getStackSize();
		String title = imp.getTitle();
		String newTitle = WindowManager.getUniqueName((imp.isSketch3D()?"Sketch3D_":"")+"DUP_"+title.replaceAll("Sketch3D_*", ""));
		if (!IJ.altKeyDown()||stackSize>1) {
			if (imp.isHyperStack() || imp.getWindow() instanceof StackWindow) {
				duplicateHyperstack(imp, newTitle);
				return;
			} else
				newTitle = showDialog(imp, "Duplicate...", "Title: ", newTitle);
		}
		if (newTitle==null) {
			imp.getWindow().dupButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/download_button_animatedStill.png")));
			return;
		}
		ImagePlus imp2;
		Roi roi = imp.getRoi();
		imp.getCanvas().setVisible(false);

		if (duplicateSubstack && (first>1||last<stackSize))
			imp2 = run(imp, 1,1, first, last, 1, 1, 1, false, 0);
		else if (duplicateStack || imp.getStackSize()==1)
			imp2 = run(imp, 1,1, 1, imp.getNSlices(), 1, 1, 1, false, 0);
		else
			imp2 = duplicateImage(imp);
		imp2.setTitle(newTitle);
		imp2.show();
		if (roi!=null && roi.isArea() && roi.getType()!=Roi.RECTANGLE) {
			imp2.restoreRoi();
			if (imp.getRoi() != null) {
				IJ.run("Colors...", "foreground=white background=black selection=yellow");
				IJ.run(imp2, "Clear Outside", "stack");
			}
		}
		imp.getWindow().dupButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/download_button_animatedStill.png")));
		imp.getCanvas().setVisible(true);
		IJ.saveAs(imp2, "Tiff", "");
	}

	/** Returns a copy of the image, stack or hyperstack contained in the specified ImagePlus. */
	public ImagePlus run(ImagePlus imp) {
		copyMergedImage = (imp.isComposite() && ((CompositeImage)imp).getCompositeMode() >= CompositeImage.RATIO12);
		if (Recorder.record) Recorder.recordCall("impD = new MQTVS_Duplicator().run(imp);");
		if (imp.getStackSize()==1) {
			ImagePlus imp2 = duplicateImage(imp);
			if (imp.getRoiManager().getColorLegend() != null)
				imp2.getRoiManager().setColorLegend(imp.getRoiManager().getColorLegend().clone(imp2));
			return imp2;
		}
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		if (roi!=null && roi.isArea())
			rect = roi.getBounds();
		int width = rect!=null?rect.width:imp.getWidth();
		int height = rect!=null?rect.height:imp.getHeight();
		ImageStack stack = imp.getStack();
		ImageStack stack2 = new ImageStack(width, height, imp.getProcessor().getColorModel());
		for (int i=1; i<=stack.getSize(); i++) {
			ImageProcessor ip2 = stack.getProcessor(i);
			ip2.setRoi(rect);
			ip2 = ip2.crop();
			stack2.addSlice(stack.getSliceLabel(i), ip2);
		}
		ImagePlus imp2 = new ImagePlus(imp.isSketch3D()?"Sketch3D_":""+"DUP_"+imp.getTitle().replaceAll("Sketch3D_*", ""), stack2);
		int[] dim = imp.getDimensions();
		imp2.setDimensions(dim[2], dim[3], dim[4]);
		if (imp.isComposite()) {
			imp2 = new CompositeImage(imp2, 0);
			((CompositeImage)imp2).copyLuts(imp);
		}
		if (imp.isHyperStack())
			imp2.setOpenAsHyperStack(true);
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay()) {
			overlay = overlay.duplicate();
			if (rect!=null)
				overlay.translate(-rect.x, -rect.y);
			imp2.setOverlay(overlay);
		}
		if (imp.getRoiManager().getColorLegend() != null)
			imp2.getRoiManager().setColorLegend(imp.getRoiManager().getColorLegend().clone(imp2));
		return imp2;
	}

	ImagePlus duplicateImage(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		ImageProcessor ip2 = ip.crop();
		ImagePlus imp2 = imp.createImagePlus();
		imp2.setProcessor(imp.isSketch3D()?"Sketch3D_":""+"DUP_"+imp.getTitle().replaceAll("Sketch3D_*", ""), ip2);
		String info = (String)imp.getProperty("Info");
		if (info!=null)
			imp2.setProperty("Info", info);
		if (imp.getStackSize()>1) {
			ImageStack stack = imp.getStack();
			String label = stack.getSliceLabel(imp.getCurrentSlice());
			if (label!=null && label.indexOf('\n')>0)
				imp2.setProperty("Info", label);
			if (imp.isComposite()) {
				LUT lut = ((CompositeImage)imp).getChannelLut();
				imp2.getProcessor().setColorModel(lut);
			}
		}
		Overlay overlay = imp.getOverlay();
		if (overlay!=null && !imp.getHideOverlay()) {
			overlay = overlay.duplicate();
			Rectangle r = ip.getRoi();
			if (r.x>0 || r.y>0)
				overlay.translate(-r.x, -r.y);
			imp2.setOverlay(overlay);
		}
		if (imp.getRoiManager().getColorLegend() != null)
			imp2.getRoiManager().setColorLegend(imp.getRoiManager().getColorLegend().clone(imp2));
		return imp2;
	}


	/** Returns a new hyperstack containing a possibly reduced version of the input image. 
	 * @param stepT */
	public ImagePlus run(ImagePlus imp, int firstC, int lastC, int firstZ, int lastZ, int firstT, int lastT, int stepT, boolean sliceSpecificROIs, long sec) {
		boolean singleStack = firstT==lastT;
		copyMergedImage = (imp.isComposite() && ((CompositeImage)imp).getCompositeMode() >= CompositeImage.RATIO12);
		RoiManager rm =  imp.getRoiManager();
		Rectangle rect = null;
		Roi roi = imp.getRoi();
		//Roi manualRoi = roi;

		Channels channelsTool = Channels.getInstance();
		if (channelsTool!=null) ((Channels) channelsTool).close();

		double[] inMin = new double[imp.getNChannels()];
		double[] inMax = new double[imp.getNChannels()];
		ColorModel[] cm = new ColorModel[imp.getNChannels()];
		LUT[] lut = imp.getLuts();

		if (roi!=null && (roi.isArea() || roi instanceof TextRoi ) && !sliceSpecificROIs){

			rect = roi.getBounds();
		}
		int width = rect!=null?rect.width:imp.getWidth();
		int height = rect!=null?rect.height:imp.getHeight();
		ImageStack stack = imp.getStack();
		boolean wasBurnIn = false;
		if (stack.isVirtual()) {
			wasBurnIn = ((VirtualStack)stack).isBurnIn();
			((VirtualStack)stack).setBurnIn(false);
		}
		ImageStack stack2 = null;
		int compositeMode = 0; 

		if ( imp.isComposite()) {
			compositeMode = ((CompositeImage)imp).getMode();
			if (!copyMergedImage)
				((CompositeImage)imp).setMode(3);

		}
		finalFrames = 0;

		finalT = lastT;
		if (sec == 0) {
			Date currentDate = new Date();
			long msec = currentDate.getTime();	
			sec = msec/1000;
		}
		String tempPath = "";

		String saveRootDir = (IJ.getDirectory("image") != null? 
				((new File(IJ.getDirectory("image"))).isDirectory()?
						IJ.getDirectory("image"):
							(new File(IJ.getDirectory("image"))).getParent()): 
								IJ.getDirectory("temp"));
		boolean correctSaveRoot = false;
//		for (File saveSibFile: (new File(saveRootDir)).listFiles()) {
//			if (saveSibFile.isDirectory() || (!saveSibFile.getName().toLowerCase().endsWith(".tif")
//												&& !saveSibFile.getName().toLowerCase().contains("ds_store"))) {
//				correctSaveRoot = true;
//			}
//		}
		if (!correctSaveRoot) {
			saveRootDir = (new File(saveRootDir)).getParent();
		}

		if (!singleStack) {
			tempPath = saveRootDir+File.separator+"DUP_"+imp.getTitle().replace(".tif","").replaceAll("[,. ;:]","")+sec;
			new File(tempPath).mkdirs();
		}
//		String stackPath = "";
		ImagePlus impT = null;
		
		for (int t=firstT; t<=lastT; t=t+stepT) {
			ImageStack stackT = new ImageStack(width, height);
			long memoryFree = Runtime.getRuntime().freeMemory();
			long memoryTotal = Runtime.getRuntime().totalMemory();
			long memoryUsed = memoryTotal-memoryFree;
			long memoryMax = Runtime.getRuntime().maxMemory();
			if ((double)memoryUsed/(double)memoryMax > 0.9) {
				finalT = t - stepT;
				finalFrames = finalFrames-1;
				t = lastT +1;
			} else {

				if (copyMergedImage)
					lastC=firstC;
				for (int z=firstZ; z<=lastZ; z++) {
					for (int c=firstC; c<=lastC; c++) {
						IJ.runMacro("print(\"\\\\Update:***Duplicating selected region(s) of time-point "+t+", channel "+c+", slice "+z+"...*** \")");

						imp.setPosition(c, z, t);			// THIS LINE CHANGED FROM ORIGINAL PLUGIN.  THIS CHANGE MAKES IT COMPATIBLE WITH MQTVS					/*********/         //ip = imp.getProcessor();						// ALSO SEEMS TO WORK FINE WITH non-virtual HyperStacks

						inMin[c-1] = imp.getChannelProcessor().getMin();
						inMax[c-1] = imp.getChannelProcessor().getMax();
						cm[c-1] = imp.getChannelProcessor().getColorModel();

						if (sliceSpecificROIs && rm != null) roi = rm.getSliceSpecificRoi(imp, z, t);
						imp.setRoi(roi);

						int n1 = imp.getStackIndex(c, z, t);
						if (imp.isComposite())
							((CompositeImage)imp).updateImage();
						ImageProcessor ip = imp.getProcessor();
						ip.setRoi(roi);
						String label = stack.getSliceLabel(n1);

						if (roi!=null && (roi.getType() != Roi.RECTANGLE || roi.getBounds().width != imp.getWidth() || roi.getBounds().height != imp.getHeight() ))
							ip.fillOutside(roi);
						if (!sliceSpecificROIs) ip = ip.crop();

						stackT.addSlice(label, ip);
						ip = null;
						IJ.runMacro("print(\"\\\\Update:   Duplicating selected region(s) of time-point "+t+", channel "+c+", slice "+z+"...    \")");

					}
				}
				finalFrames++;
				impT = new ImagePlus(dupTitle+"_"+ t +".tif", stackT);
				
				impT.setOpenAsHyperStack(true);

				impT.setDimensions(copyMergedImage?1:lastC-firstC+1, lastZ-firstZ+1, 1);
				if (  ((StackWindow)imp.getWindow()).isWormAtlas() 
						&& imp.getStack() instanceof MultiQTVirtualStack){
					//			IJ.log("Copy from WA STACK");
					impT.setDimensions(lastC-firstC+1, finalFrames, 1 );

				}

//				stackPath = IJ.getDirectory("home")+imp.getTitle().replace(".tif","").replaceAll("[,. ;:]","")+sec+File.separator+impT.getTitle().replaceAll("[, ;:]","");
				if (!singleStack) {
					IJ.saveAs(impT, "Tiff", tempPath+File.separator+impT.getTitle().replaceAll("[, ;:]",""));
					impT.close();
					impT.flush();
				}

			}
			
		}
		imp.killRoi();
		if (wasBurnIn)
			((VirtualStack)stack).setBurnIn(true);
		//		imp.getWindow().setVisible(true);


		if (imp.isComposite()) {
			for (int i=1; i<=imp.getNChannels(); i++) {
				((CompositeImage)imp).setChannelLut(lut[i-1], i);
				((CompositeImage)imp).setPosition(i, 1, 1);
				((CompositeImage)imp).getChannelProcessor().setMinAndMax(inMin[i-1], inMax[i-1]);
				((CompositeImage)imp).setMode(compositeMode);
			}
		}
		if (!singleStack) {
			stack2 = new MultiFileInfoVirtualStack(tempPath,"",false);
		} else {
			stack2 = impT.getStack();
		}
		ImagePlus imp2 = new ImagePlus(imp.isSketch3D()?"Sketch3D_":""+"DUP_"+imp.getTitle().replaceAll("Sketch3D_*", ""), stack2);
		imp2.setOpenAsHyperStack(true);

		imp2.setDimensions(copyMergedImage?1:lastC-firstC+1, lastZ-firstZ+1, finalFrames);
		if (  ((StackWindow)imp.getWindow()).isWormAtlas() 
				&& imp.getStack() instanceof MultiQTVirtualStack){
			//			IJ.log("Copy from WA STACK");
			imp2.setDimensions(lastC-firstC+1, finalFrames, 1 );

		}

		//		IJ.log(""+imp2.getOpenAsHyperStack());

		if (imp.isComposite()) {
			//        	IJ.log("Yes CI");
			//			int mode = ((CompositeImage)imp).getMode();
			if (lastC>=firstC && !copyMergedImage) {
				imp2 = new CompositeImage(imp2, 3);
				//				int i2 = 1;
				for (int i=1; i<=imp2.getNChannels(); i++) {
					//					lut[i] = ((CompositeImage)imp).getChannelLut(i);
					((CompositeImage)imp2).setChannelLut(lut[i-1], i);
					((CompositeImage)imp2).setPosition(i, 1, 1);
					((CompositeImage)imp2).getChannelProcessor().setMinAndMax(inMin[i-1], inMax[i-1]);

				}
				((CompositeImage)imp2).setMode(compositeMode);
			} else if (firstC==lastC) {
				//				LUT lut = imp.getProcessor().getLut();
				if (cm[0] instanceof IndexColorModel) imp2.getProcessor().setColorModel(cm[0]);
				imp2.getProcessor().setColorModel(lut[0]);
				imp2.setDisplayRange(inMin[0], inMax[0]);
			}
		} else {
			//        	IJ.log("Not CI");
			imp2.getProcessor().setColorModel(cm[0]);
			//    		if (lut[0] != null) imp2.getProcessor().setColorModesl(lut[0]);
			imp2.setDisplayRange(inMin[0], inMax[0]);
		}

		imp2.setOpenAsHyperStack(true);
		imp2.killRoi();
		//		IJ.log(""+imp2.getOpenAsHyperStack());

		if (imp.getRoiManager().getColorLegend() != null)
			imp2.getRoiManager().setColorLegend(imp.getRoiManager().getColorLegend().clone(imp2));
		imp2.setCalibration(imp.getCalibration());
		if (Recorder.record)
			Recorder.recordCall("impD = new MQTVS_Duplicator().run(imp, "+firstC+", "+lastC+", "+firstZ+", "+lastZ+", "+firstT+", "+lastT+", "+sliceSpecificROIs+");");
		return imp2;
	}





	String showDialog(ImagePlus imp, String title, String prompt, String defaultString) {
		int stackSize = imp.getStackSize();
		duplicateSubstack = stackSize>1 && (stackSize==imp.getNSlices()||stackSize==imp.getNFrames());
		GenericDialog gd = new GenericDialog(title);
		gd.addStringField(prompt, defaultString, duplicateSubstack?15:20);
		if (stackSize>1) {
			String msg = duplicateSubstack?"Duplicate stack":"Duplicate entire stack";
			gd.addCheckbox(msg, duplicateStack||imp.isComposite());
			if (duplicateSubstack) {
				gd.setInsets(2, 30, 3);
				gd.addStringField("Range:", "1-"+stackSize);
				Vector v = gd.getStringFields();
				rangeField = (TextField)v.elementAt(1);
				rangeField.addTextListener(this);
				checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
			}
		} else
			duplicateStack = false;
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		title = gd.getNextString();
		if (stackSize>1) {
			duplicateStack = gd.getNextBoolean();
			if (duplicateStack && duplicateSubstack) {
				String[] range = Tools.split(gd.getNextString(), " -");
				double d1 = Tools.parseDouble(range[0]);
				double d2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
				first = Double.isNaN(d1)?1:(int)d1;
				last = Double.isNaN(d2)?stackSize:(int)d2;
				if (first<1) first = 1;
				if (last>stackSize) last = stackSize;
				if (first>last) {first=1; last=stackSize;}
			} else {
				first = 1;
				last = stackSize;
			}
		}
		return title;
	}




	public ImagePlus duplicateHyperstack(ImagePlus imp, String newTitle) {
		dupTitle = showHSDialog(imp, newTitle);
		if (dupTitle==null)
			return null;
		imp.getCanvas().setVisible(false);
		ImagePlus imp2 = null;
		Roi roi = imp.getRoi();
		Roi manualRoi = null;
		if (roi!=null && roi.isArea()) 
			manualRoi = roi;

		double inMin = imp.getDisplayRangeMin();
		double inMax = imp.getDisplayRangeMax();
		ColorModel cm = imp.getProcessor().getColorModel();
		int inChannel = imp.getChannel();
		int inSlice = imp.getSlice();
		int inFrame = imp.getFrame();

		if (!duplicateStack) {
			int nChannels = imp.getNChannels();
			if (nChannels>1 && imp.isComposite() && ((CompositeImage)imp).getMode()==CompositeImage.COMPOSITE) {
				setFirstC(1);
				setLastC(nChannels);
			} else
				setFirstC(setLastC(imp.getChannel()));
			setFirstZ(setLastZ(imp.getSlice()));
			setFirstT(setLastT(imp.getFrame()));
		}

		//		imp.getWindow().setVisible(false);
		RoiManager rm = imp.getRoiManager();
		boolean rmVis =false; 
		if (rm != null) {
			rmVis = rm.isVisible();
			rm.setVisible(false);
		}
		MultiChannelController mcc = imp.getMultiChannelController();
		boolean mccVis = false;
		if (mcc != null) {
			mccVis = mcc.isVisible();
			mcc.setVisible(false);
		}

		imp2 = run(imp, getFirstC(), getLastC(), getFirstZ(), getLastZ(), getFirstT(), getLastT(), getStepT(), sliceSpecificROIs, 0);

		imp.getProcessor().setColorModel(cm);
		imp.setDisplayRange(inMin, inMax);
		imp.setPosition(inChannel, inSlice, inFrame);
		int origChannel = imp.getChannel();
		if (imp instanceof CompositeImage && ((CompositeImage) imp).getMode() ==1 ) {
			for (int j = 1; j <= imp.getNChannels(); j++) {
				imp.setPosition(j, imp.getSlice(),
						imp.getFrame());
			}
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame());
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() + 1);
			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() - 1);
		}
		imp.getWindow().setVisible(true);
		imp.setRoi(roi);
		if (rm != null && rmVis) 
			rm.setVisible(true);
		if (mcc != null && mccVis) 
			mcc.setVisible(true);

		if (imp2==null) return null;
		imp2.setTitle(newTitle);

		if (manualRoi != null && sliceSpecificROIs) {
			imp2.setRoi(manualRoi);
			WindowManager.setTempCurrentImage(imp2);
			IJ.run("Clear Outside", "stack");
			IJ.run("Crop");
			WindowManager.setTempCurrentImage(null);
		}

		imp2.getProcessor().setColorModel(cm);
		imp2.setDisplayRange(inMin, inMax);
		imp2.setDimensions(getLastC()-getFirstC()+1, getLastZ()-getFirstZ()+1, finalFrames);
		imp2.setPosition(inChannel-getFirstC()+1, inSlice-getFirstZ() +1, inFrame-getFirstT()+1);
		//		IJ.log(""+imp2.getOpenAsHyperStack());

		imp2.show();
		if (imp2.getWindow() != null) {
			imp2.getWindow().setVisible(false);

			if (imp != null && imp2 != null) {
				imp2.getWindow().setBackground(imp.getWindow().getBackground());
				imp2.getWindow().setSubTitleBkgdColor(imp.getWindow().getBackground());
			}

			if ( imp2.getWindow() instanceof StackWindow &&  ((StackWindow)imp2.getWindow()).getNScrollbars()<2) {
				if (((StackWindow)imp2.getWindow()).getAnimationZSelector() != null) 
					((StackWindow)imp2.getWindow()).getAnimationZSelector().setValue(inSlice-getFirstZ() +1);
			}
		}

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

		RoiManager rm2 = imp2.getRoiManager();
		rm2.setVisible(false);

		for (int t = getFirstT(); t <= finalT ; t = t+getStepT()) {
			for (int z = getFirstZ(); z <= getLastZ() ; z++) {

				//				IJ.log(" "+firstZ+" "+z+" "+lastZ+" "+" "+firstT+" "+t+" "+lastT+" ");
				Roi[] sliceSpecficRoiArray = imp.getRoiManager().getSliceSpecificRoiArray(z, t, true);
				for (int r = 0; r < sliceSpecficRoiArray.length; r++) {
					if (manualRoi == null)
						manualRoi = new Roi(dupX,dupY,dupW,dupH);
					if( manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getCenterX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getCenterY())
							|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinY())
							|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxY())
							|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxY())
							|| manualRoi.contains((int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMaxX(), (int)((Roi) sliceSpecficRoiArray[r]).getBounds().getMinY())
							){
						Roi nextRoi = (Roi) sliceSpecficRoiArray[r].clone();
						nextRoi.setLocation((int) (sliceSpecficRoiArray[r].getBounds().getX()-dupX), (int) ( sliceSpecficRoiArray[r]).getBounds().getY()-dupY);
						imp2.setPosition(1, z-getFirstZ()+1, ((t-getFirstT())/getStepT())+1 );
						rm2.addRoi(nextRoi);
					}
				}
			}
		}
		rm2.setZSustain(imp.getRoiManager().getZSustain());
		rm2.setTSustain(imp.getRoiManager().getTSustain());
		rm2.showAll(RoiManager.SHOW_ALL);
		//		imp2.setRoiManager(rm2);
		if (imp.getMotherImp() != null && !imp.getMotherImp().isSketch3D())
			imp2.setMotherImp(imp.getMotherImp(), getFirstT());
		else if (!imp.isSketch3D())
			imp2.setMotherImp(imp, getFirstT());
		else
			imp2.setMotherImp(imp2, getFirstT());

		imp2.getProcessor().setColorModel(cm);
		imp2.setDisplayRange(inMin, inMax);
		imp2.setDimensions(getLastC()-getFirstC()+1, getLastZ()-getFirstZ()+1, finalFrames);
		imp2.setPosition(inChannel-getFirstC()+1, inSlice-getFirstZ() +1, inFrame-getFirstT()+1);

		if (imp2.getWindow() != null) {
			imp2.getWindow().setVisible(true);
		}
		imp.getWindow().dupButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/download_button_animatedStill.png")));
		imp.getCanvas().setVisible(true);
		//		IJ.saveAs(imp2, "Tiff", "");
		return imp2;

	}




	String showHSDialog(ImagePlus imp, String newTitle) {
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		GenericDialog gd = new GenericDialog("Duplicate");
		gd.addStringField("Title:", newTitle, 15);
		gd.setInsets(12, 20, 8);
		if (imp.isHyperStack()) gd.addCheckbox("Duplicate hyperstack", true);
		else gd.addCheckbox("Duplicate stack", true);
		boolean amReady = false;
		if (imp.getRoiManager() != null){
			gd.addCheckbox("Crop via Tag Manager tags", false);
			amReady = true;
		}
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
		rangeFields = new TextField[3];
		for (int i=0; i<nRangeFields; i++) {
			rangeFields[i] = (TextField)v.elementAt(i+1);
			rangeFields[i].addTextListener(this);
		}
		checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
		gd.showDialog();
		if (gd.wasCanceled())
			return null;
		String title = gd.getNextString();
		duplicateStack = gd.getNextBoolean();
		if (amReady) 
			sliceSpecificROIs = gd.getNextBoolean();

		if (nChannels>1) {
			String channelRangeString = gd.getNextString();
//			copyMergedImage = channelRangeString.contains("merge");
			String[] range = Tools.split(channelRangeString, " -");
//			channelRangeString = channelRangeString.replace("merge","").trim();
			double c1 = Tools.parseDouble(range[0]);
			double c2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			setFirstC(Double.isNaN(c1)?1:(int)c1);
			setLastC(Double.isNaN(c2)?getFirstC():(int)c2);
			if (getFirstC()<1) setFirstC(1);
			if (getLastC()>nChannels) setLastC(nChannels);
			if (getFirstC()>getLastC()) {setFirstC(1); setLastC(nChannels);}
		} else
			setFirstC(setLastC(1));
		if (nSlices>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double z1 = Tools.parseDouble(range[0]);
			double z2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			setFirstZ(Double.isNaN(z1)?1:(int)z1);
			setLastZ(Double.isNaN(z2)?getFirstZ():(int)z2);
			if (getFirstZ()<1) setFirstZ(1);
			if (getLastZ()>nSlices) setLastZ(nSlices);
			if (getFirstZ()>getLastZ()) {setFirstZ(1); setLastZ(nSlices);}
		} else
			setFirstZ(setLastZ(1));
		if (nFrames>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double t1 = Tools.parseDouble(range[0]);
			double t2 = range.length>=2?Tools.parseDouble(range[1]):Double.NaN;
			double t3 = range.length==3?Tools.parseDouble(range[2]):Double.NaN;
			setFirstT(Double.isNaN(t1)?1:(int)t1);
			setLastT(Double.isNaN(t2)?getFirstT():(int)t2);
			setStepT(Double.isNaN(t3)?1:(int)t3);
			if (getFirstT()<1) setFirstT(1);
			if (getLastT()>nFrames) setLastT(nFrames);
			if (getFirstT()>getLastT()) {setFirstT(1); setLastT(nFrames);}
		} else
			setFirstT(setLastT(1));
		return title;
	}

	public void textValueChanged(TextEvent e) {
		checkbox.setState(true);
	}

	public int getFirstC() {
		return firstC;
	}

	public void setFirstC(int firstC) {
		this.firstC = firstC;
	}

	public int getLastC() {
		return lastC;
	}

	public int setLastC(int lastC) {
		this.lastC = lastC;
		return lastC;
	}

	public int getFirstZ() {
		return firstZ;
	}

	public void setFirstZ(int firstZ) {
		this.firstZ = firstZ;
	}

	public int getLastZ() {
		return lastZ;
	}

	public int setLastZ(int lastZ) {
		this.lastZ = lastZ;
		return lastZ;
	}

	public int getFirstT() {
		return firstT;
	}

	public void setFirstT(int firstT) {
		this.firstT = firstT;
	}

	public int getLastT() {
		return lastT;
	}

	public int setLastT(int lastT) {
		this.lastT = lastT;
		return lastT;
	}

	public int getStepT() {
		return stepT;
	}

	public void setStepT(int stepT) {
		this.stepT = stepT;
	}



}
