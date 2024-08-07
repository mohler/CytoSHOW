package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.frame.RoiManager;
import ij.process.*;

import java.awt.*;
import java.awt.List;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;

import javax.swing.JFrame;

import org.vcell.gloworm.MultiQTVirtualStack;
 
/**
 * 
* @author Dimiter Prodanov
* 		  IMEC
* 
* @acknowledgments Many thanks to Jerome Mutterer for the code contributions and testing.
* 				   Thanks to Wayne Rasband for the code that properly handles the image magnification.
* 		
* @version 		1.2 28 April 2009
* 					- added support for arrow keys
* 					- fixed a bug in the cross position calculation
* 					- added FocusListener behavior
* 					- added support for magnification factors
* 				1.1.6 31 March 2009
* 					- added AdjustmentListener behavior thanks to Jerome Mutterer
* 					- improved pane visualization
* 					- added window rearrangement behavior. Initial code suggested by Jerome Mutterer
* 					- bug fixes by Wayne Raspband
* 				1.1 24 March 2009
* 					- improved projection image resizing
* 					- added ImageListener behaviors
* 					- added check-ups
* 					- improved pane updating
* 				1.0.5 23 March 2009
* 					- fixed pane updating issue
* 				1.0 21 March 2009
* 
* @contents This plugin projects dynamically orthogonal XZ and YZ views of a stack. 
* The output images are calibrated, which allows measurements to be performed more easily. 
*/

public class Orthogonal_Views implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ActionListener, 
	ImageListener, WindowListener, AdjustmentListener, MouseWheelListener, FocusListener, CommandListener {

	private ImageWindow xyWin;
	private ImagePlus originalImp;
	private boolean rgb;
	public ImageStack imageStack;
	private boolean hyperstack;
	private int currentChannel, currentFrame; 
	private int currentMode = 10000;
	private ImageCanvas2 xyCanvas;
	private static final int H_ROI=0, H_ZOOM=1;
	private static boolean sticky=true;
	private static int xzID, yzID, xyID;
	private static Orthogonal_Views instance;
	private ImagePlus xz_image, yz_image;
	/** ImageProcessors for the xz and yz images */
	private ImageProcessor fp1, fp2;
	private double ax, ay, az;
	private boolean rotateYZ = Prefs.rotateYZ;
	private boolean flipXZ = Prefs.flipXZ;
	
	private int xyX, xyY, xyW, xyH;
	private Calibration cal=null, cal_xz=new Calibration(), cal_yz=new Calibration();
	private double magnification=1.0;
	private Color color = Roi.getColor();
	private Updater updater = new Updater();
	private double min, max;
	private Dimension screen = IJ.getScreenSize();
	private boolean syncZoom = true;
	private Point crossLoc;
	private boolean firstTime = true;
	private static int previousID, previousX, previousY;
	private Rectangle startingSrcRect;
	private Roi[] stackRois = new Roi[1];
	private ArrayList<Roi> stackRoiArrayList = new ArrayList<Roi>();
	private ImageStack originalStack;
	private ColorModel originalCM;
	private int originalMode;
	private Color defaultWinColor;
	private ImagePlus xy_image;
	 
	public void run(String arg) {
		originalImp = IJ.getImage();
		originalImp.getWindow().running = false;
		originalImp.getWindow().running2 = false;
		originalImp.getWindow().running3 = false;
		originalStack = originalImp.getStack();
		originalCM = originalStack.getColorModel();
		defaultWinColor = new Color(originalImp.getWindow().getBackground().getRGB());
		if (instance!=null) {
			instance.dispose();
			return;
		}
		if (originalImp.getStackSize()==1) {
			IJ.error("Othogonal Views", "This command requires a stack.");
			return;
		}
		hyperstack = originalImp.isHyperStack();
		if ((hyperstack||originalImp.isComposite()) && originalImp.getNSlices()<=1) {
			IJ.error("Othogonal Views", "This command requires a stack, or a hypertack with Z>1.");
			return;
		}
		yz_image = WindowManager.getImage(yzID);
		rgb = originalImp.getBitDepth()==24 || hyperstack;
		int yzBitDepth = hyperstack?24:originalImp.getBitDepth();
		if (yz_image==null || yz_image.getHeight()!=originalImp.getHeight() || yz_image.getBitDepth()!=yzBitDepth){
			yz_image = new ImagePlus();
			yz_image.setMotherImp(originalImp.getMotherImp(), originalImp.getMotherID());
//			IJ.log("SET MOTHERIMP XZ");
		}
		xz_image = WindowManager.getImage(xzID);
		//if (xz_image!=null) IJ.log(imp+"  "+xz_image+"  "+xz_image.getHeight()+"  "+imp.getHeight()+"  "+xz_image.getBitDepth()+"  "+yzBitDepth);
		if (xz_image==null || xz_image.getWidth()!=originalImp.getWidth() || xz_image.getBitDepth()!=yzBitDepth) {
			xz_image = new ImagePlus();
//			IJ.log("NEW XZ");
			xz_image.setMotherImp(originalImp.getMotherImp(), originalImp.getMotherID());
//			IJ.log("SET MOTHERIMP YZ");
		}
		xy_image = WindowManager.getImage(xyID);
		//if (xy_image!=null) IJ.log(imp+"  "+xy_image+"  "+xy_image.getHeight()+"  "+imp.getHeight()+"  "+xy_image.getBitDepth()+"  "+yzBitDepth);
		if (xy_image==null || xy_image.getWidth()!=originalImp.getWidth() || xy_image.getBitDepth()!=yzBitDepth) {
			xy_image = new ImagePlus();
//			IJ.log("NEW xy");
			xy_image.setMotherImp(originalImp.getMotherImp(), originalImp.getMotherID());
//			IJ.log("SET MOTHERIMP YZ");
		}
		instance = this;
		ImageProcessor ip = (originalImp instanceof CompositeImage && originalImp.getNChannels()>1)?
							new ColorProcessor(originalImp.getImage()):originalImp.getProcessor();
		min = ip.getMin();
		max = ip.getMax();
		cal=this.originalImp.getCalibration();
		double calx=cal.pixelWidth;
		double caly=cal.pixelHeight;
		double calz=cal.pixelDepth;
		ax=1.0;
		ay=caly/calx;
		az=calz/calx;
		imageStack = getStack();
		xyWin = xy_image.getWindow();
		xyCanvas = xyWin.getCanvas();
		addListeners(xyCanvas);
		magnification= xyCanvas.getMagnification();
		originalImp.deleteRoi();
		Rectangle r = xyCanvas.getSrcRect();
		if (originalImp.getID()==previousID)
			crossLoc = new Point(previousX, previousY);
		else
			crossLoc = new Point(r.x+r.width/2, r.y+r.height/2);
		calibrate();
		if (createProcessors(imageStack)) {
			if (ip.isColorLut() || ip.isInvertedLut()) {
				ColorModel cm = ip.getColorModel();
				fp1.setColorModel(cm);
				fp1.setMinAndMax(ip.getMin(), ip.getMax());
				fp2.setColorModel(cm);				
				fp2.setMinAndMax(ip.getMin(), ip.getMax());
			}
//			IJ.log("run");
			update();
		} else
			dispose();
	}
	
	private ImageStack getStack() {
		if (true/*imp.isHyperStack()*/) {
			boolean xy_setup =false;
			if (xy_image == null)
				xy_setup =true;
			if (xy_setup  || (originalImp.isComposite() && ((CompositeImage)originalImp).getMode() != currentMode)) {
				xy_image.flush();
				xy_image = originalImp.createImagePlus();
			}
			xy_image.setTitle(originalImp.getTitle()+" t="+originalImp.getFrame());
			if (originalImp.isComposite() && currentMode == 10000) {
				originalMode =  ((CompositeImage)originalImp).getMode();
			}
			if (originalImp.isComposite()) {
//				((CompositeImage)imp).setMode(CompositeImage.GRAYSCALE);
				currentMode = ((CompositeImage)originalImp).getMode();
			}
			originalImp.deleteRoi();
			int slices = originalImp.getNSlices();
			ImageStack stack = new ImageStack(originalImp.getWidth(), originalImp.getHeight());
			int c=originalImp.getChannel(), z=originalImp.getSlice(), t=originalImp.getFrame();
			
			stackRoiArrayList.clear();
			RoiManager rm = originalImp.getRoiManager();
			Roi[] allRois = rm.getShownRoisAsArray();

			for (int j=0; j<allRois.length; j++) {
				if ( (Math.abs(t-allRois[j].getTPosition())<rm.getTSustain() || allRois[j].getTPosition() ==0)							
						&& (allRois[j].getCPosition() == c || allRois[j].getCPosition() == 0)) {
					stackRoiArrayList.add((Roi) allRois[j].clone());
					if (t-allRois[j].getTPosition() !=0) {
						stackRoiArrayList.get(stackRoiArrayList.size()-1).setStrokeColor(stackRoiArrayList.get(stackRoiArrayList.size()-1).getStrokeColor().darker());
					}
				}
			}	

			Arrays.fill(stackRois, null);
			stackRois = (Roi[]) stackRoiArrayList.toArray(stackRois);

			stackRoiArrayList.clear();

			for (int i=1; i<=slices; i++) {
				int slicePos = (t-1)*originalImp.getNSlices() + (i-1)*originalImp.getNChannels() + c;
				if (originalImp instanceof CompositeImage && originalImp.getNChannels()>1 && currentMode < CompositeImage.GRAYSCALE) {
					originalImp.getWindow().setEnabled(false);
					originalImp.setPositionWithoutUpdate(c, i, t);
					((CompositeImage)originalImp).setMode(currentMode);
					stack.addSlice(new ColorProcessor(originalImp.getImage()));
				} else if (originalImp.getStack() instanceof VirtualStack) {
					originalImp.getWindow().setEnabled(false);
					originalImp.setPositionWithoutUpdate(c, i, t);
					stack.addSlice(originalImp.getProcessor());
				} else {
					originalImp.getWindow().setEnabled(false);
					originalImp.setPositionWithoutUpdate(c, i, t);
					stack.addSlice(originalImp.getProcessor());
				}
			}
			if (originalImp.isComposite()) {
				if (((StackWindow)originalImp.getWindow()).cSelector != null)
					((StackWindow)originalImp.getWindow()).cSelector.setIconEnabled(false);
			}
			if (originalImp.getStack().isVirtual()) {
				if (((StackWindow)originalImp.getWindow()).tSelector != null)
					((StackWindow)originalImp.getWindow()).tSelector.setEnabled(false);
			}
			if (originalImp.isComposite() && originalImp.getStack().isVirtual()) {
				if (((StackWindow)originalImp.getWindow()).zSelector != null)
					((StackWindow)originalImp.getWindow()).zSelector.setEnabled(false);
			}


			originalImp.setPosition(c, z, t);
			originalImp.getWindow().setEnabled(true);
			currentChannel = c;
			currentFrame = t;
			originalImp.deleteRoi();
			xy_image.setStack(stack);
			xy_image.show();
			while (xy_image.getWindow() == null) IJ.wait(10);
			xy_image.getWindow().setBackground(Color.yellow);
			if (xy_setup)
				xy_image.getWindow().setLocation(originalImp.getWindow().getLocation());
			xyID = xy_image.getID();
			for (Roi stackRoi:stackRois) {
				if (stackRoi != null) {
					xy_image.setPositionWithoutUpdate(1, stackRoi.getZPosition(), 1);
					xy_image.getRoiManager().addRoi(stackRoi);
				}
			}
			xy_image.getRoiManager().showAll(RoiManager.SHOW_ALL);
			xy_image.getRoiManager().setZSustain(originalImp.getRoiManager().getZSustain());
			return stack;
		} else
			return originalImp.getStack();
	}
 
	private void addListeners(ImageCanvas2 canvass) {
		xyCanvas.addMouseListener(this);
		xyCanvas.addMouseMotionListener(this);
		xyCanvas.addKeyListener(this);
		xyWin.addWindowListener (this);  
		xyWin.addMouseWheelListener(this);
		xyWin.addFocusListener(this);
//		Component[] c = win.getComponents();
//		//IJ.log(c[1].toString());
//		((ScrollbarWithLabel) c[1]).addAdjustmentListener (this);
		((StackWindow) xyWin).zSelector.addAdjustmentListener(this);
		ImagePlus.addImageListener(this);
		Executer.addCommandListener(this);

	}
	 
	private void calibrate() {
		String unit=cal.getUnit();
		double o_depth=cal.pixelDepth;
		double o_height=cal.pixelHeight;
		double o_width=cal.pixelWidth;
		cal_yz.setUnit(unit);
		if (rotateYZ) {
			cal_yz.pixelHeight=o_depth/az;
			cal_yz.pixelWidth=o_height;
		} else {
			cal_yz.pixelWidth=o_depth/az;
			cal_yz.pixelHeight=o_height;
		}
		yz_image.setCalibration(cal_yz);
		cal_xz.setUnit(unit);
		cal_xz.pixelWidth=o_width;
		cal_xz.pixelHeight=o_depth/az;
		xz_image.setCalibration(cal_xz);
	}

	private void updateMagnification(int x, int y) {
        double magnification= xyWin.getCanvas().getMagnification();
        int z = xy_image.getSlice()-1;
        ImageWindow xz_win = xz_image.getWindow();
        if (xz_win==null) return;
        ImageCanvas2 xz_ic = xz_win.getCanvas();
        double xz_mag = xz_ic.getMagnification();
        double arat = az/ax;
		int zcoord=(int)(arat*z);
		if (flipXZ) zcoord=(int)(arat*(xy_image.getNSlices()-z));
        while (xz_mag<magnification) {
        	xz_ic.zoomIn(xz_ic.screenX(x), xz_ic.screenY(zcoord));
        	xz_mag = xz_ic.getMagnification();
        }
        while (xz_mag>magnification) {
        	xz_ic.zoomOut(xz_ic.screenX(x), xz_ic.screenY(zcoord));
        	xz_mag = xz_ic.getMagnification();
        }
        ImageWindow yz_win = yz_image.getWindow();
        if (yz_win==null) return;
        ImageCanvas2 yz_ic = yz_win.getCanvas();
        double yz_mag = yz_ic.getMagnification();
		zcoord = (int)(arat*z);
        while (yz_mag<magnification) {
        	//IJ.log(magnification+"  "+yz_mag+"  "+zcoord+"  "+y+"  "+x);
        	yz_ic.zoomIn(yz_ic.screenX(zcoord), yz_ic.screenY(y));
        	yz_mag = yz_ic.getMagnification();
        }
        while (yz_mag>magnification) {
        	yz_ic.zoomOut(yz_ic.screenX(zcoord), yz_ic.screenY(y));
        	yz_mag = yz_ic.getMagnification();
        }
	}
	
	void updateViews(Point p, ImageStack iStack) {
		ImageProcessor ip = null;
		if (originalImp instanceof CompositeImage && originalImp.getNChannels()>1 && currentMode < CompositeImage.GRAYSCALE)
			ip = xy_image.getProcessor();
		else
			ip = originalImp.getProcessor();
		if (!(ip instanceof ColorProcessor)) {
			ColorModel cm = ip.getColorModel();
			iStack.setColorModel(cm);
			fp1.setColorModel(cm);
			fp2.setColorModel(cm);				
		}
		min = ip.getMin();
		max = ip.getMax();
		fp1.setMinAndMax(min, max);
		fp2.setMinAndMax(min, max);

		if (fp1==null) return;
		updateXZView(p,iStack);
		
		double arat=az/ax;
		int width2 = fp1.getWidth();
		int height2 = (int)Math.round(fp1.getHeight()*az);
		if (width2!=fp1.getWidth()||height2!=fp1.getHeight()) {
			fp1.setInterpolate(true);
			ImageProcessor sfp1=fp1.resize(width2, height2);
			if (!rgb) sfp1.setMinAndMax(min, max);
			xz_image.setProcessor("XZ "+p.y +" "+ xy_image.getTitle(), sfp1);
		} else {
			fp1.setInterpolate(true);
			ImageProcessor sfp1=fp1.resize(width2, height2);
			if (!rgb) fp1.setMinAndMax(min, max);
	    	xz_image.setProcessor("XZ "+p.y+" "+ xy_image.getTitle(), fp1);
		}
			
		if (rotateYZ)
			updateYZView(p, iStack);
		else
			updateZYView(p, iStack);
				
		width2 = (int)Math.round(fp2.getWidth()*az);
		height2 = fp2.getHeight();
		String title = "YZ ";
		if (rotateYZ) {
			width2 = fp2.getWidth();
			height2 = (int)Math.round(fp2.getHeight()*az);
			title = "ZY ";
		}
		//IJ.log("updateViews "+width2+" "+height2+" "+arat+" "+ay+" "+fp2);
		if (width2!=fp2.getWidth()||height2!=fp2.getHeight()) {
			fp2.setInterpolate(true);
			ImageProcessor sfp2=fp2.resize(width2, height2);
			if (!rgb) sfp2.setMinAndMax(min, max);
			yz_image.setProcessor(title+p.x+" "+ xy_image.getTitle(), sfp2);
		} else {
			fp1.setInterpolate(true);
			ImageProcessor sfp2=fp2.resize(width2, height2);
			if (!rgb) fp2.setMinAndMax(min, max);
			yz_image.setProcessor(title+p.x+" "+ xy_image.getTitle(), fp2);
		}
		
		calibrate();
		if (yz_image.getWindow()==null) {
			yz_image.show();
			yz_image.getWindow().setBackground(Color.cyan);
			ImageCanvas2 ic = yz_image.getCanvas();
			ic.addKeyListener(this);
			ic.addMouseListener(this);
			ic.addMouseMotionListener(this);
			ic.addMouseListener(yz_image.getMotherImp().getRoiManager().getColorLegend());
			ic.addMouseMotionListener(yz_image.getMotherImp().getRoiManager().getColorLegend());

			ic.setCustomRoi(true);
			yz_image.getWindow().addMouseWheelListener(this);
			yzID = yz_image.getID();
			yz_image.getWindow().setSubTitleBkgdColor(xy_image.getWindow().getBackground());
			yz_image.getRoiManager().showAll(RoiManager.SHOW_ALL);
	

		} else {
			ImageCanvas2 ic = yz_image.getWindow().getCanvas();
//			yz_image.getRoiManager().showAll(RoiManager.SHOW_ALL);
		}
		if (xz_image.getWindow()==null) {
			xz_image.show();
			xz_image.getWindow().setBackground(Color.magenta);
			ImageCanvas2 ic = xz_image.getCanvas();
			ic.addKeyListener(this);
			ic.addMouseListener(this);
			ic.addMouseMotionListener(this);
			ic.setCustomRoi(true);
			ic.addMouseListener(xz_image.getMotherImp().getRoiManager().getColorLegend());
			ic.addMouseMotionListener(xz_image.getMotherImp().getRoiManager().getColorLegend());
			xz_image.getWindow().addMouseWheelListener(this);
			xzID = xz_image.getID();
			xz_image.getWindow().setSubTitleBkgdColor(xy_image.getWindow().getBackground());
			xz_image.getRoiManager().showAll(RoiManager.SHOW_ALL);			

		} else {
			ImageCanvas2 ic = xz_image.getWindow().getCanvas();
//			xz_image.getRoiManager().showAll(RoiManager.SHOW_ALL);			
		}
 
	}
	
	void arrangeWindows(boolean sticky) {
		ImageWindow xyWin = xy_image.getWindow();
		if (xyWin==null) return;
		Point loc = xyWin.getLocation();
		if ((xyX!=loc.x)||(xyY!=loc.y)
				||(xyW!=xyWin.getWidth())||(xyH!=xyWin.getHeight())) {
			xyX =  loc.x;
			xyY =  loc.y;
			xyW = xyWin.getWidth();
			xyH = xyWin.getHeight();
 			ImageWindow yzWin =null;
 			long start = System.currentTimeMillis();
 			while (yzWin==null && (System.currentTimeMillis()-start)<=2500L) {
				yzWin = yz_image.getWindow();
				if (yzWin==null) IJ.wait(50);
			}
			if (yzWin!=null)
 				yzWin.setLocation(xyX+xyWin.getWidth(), xyY);
			ImageWindow xzWin =null;
 			start = System.currentTimeMillis();
 			while (xzWin==null && (System.currentTimeMillis()-start)<=2500L) {
				xzWin = xz_image.getWindow();
				if (xzWin==null) IJ.wait(50);
			}
			if (xzWin!=null)
 				xzWin.setLocation(xyX,xyY+xyWin.getHeight());
 			if (firstTime) {
 				xy_image.getWindow().toFront();
 				if (hyperstack)
 					originalImp.setPosition(originalImp.getChannel(), originalImp.getSlice(), originalImp.getFrame());
 				else
 					originalImp.setSlice(originalImp.getNSlices()/2);
 				firstTime = false;
 			}
		}
	}
	
	/**
	 * @param iStack - used to get the dimensions of the new ImageProcessors
	 * @return
	 */
	boolean createProcessors(ImageStack iStack) {
		ImageProcessor ip=iStack.getProcessor(1);
		int width= iStack.getWidth();
		int height=iStack.getHeight();
		int ds=iStack.getSize(); 
		double arat=1.0;//az/ax;
		double brat=1.0;//az/ay;
		int za=(int)(ds*arat);
		int zb=(int)(ds*brat);
		//IJ.log("za: "+za +" zb: "+zb);
		
		if (ip instanceof FloatProcessor) {
			fp1=new FloatProcessor(width,za);
			if (rotateYZ)
				fp2=new FloatProcessor(height,zb);
			else
				fp2=new FloatProcessor(zb,height);
			return true;
		}
		
		if (ip instanceof ByteProcessor) {
			fp1=new ByteProcessor(width,za);
			if (rotateYZ)
				fp2=new ByteProcessor(height,zb);
			else
				fp2=new ByteProcessor(zb,height);
			return true;
		}
		
		if (ip instanceof ShortProcessor) {
			fp1=new ShortProcessor(width,za);
			if (rotateYZ)
				fp2=new ShortProcessor(height,zb);
			else
				fp2=new ShortProcessor(zb,height);
			//IJ.log("createProcessors "+rotateYZ+"  "+height+"   "+zb+"  "+fp2);
			return true;
		}
		
		if (ip instanceof ColorProcessor) {
			fp1=new ColorProcessor(width,za);
			if (rotateYZ)
				fp2=new ColorProcessor(height,zb);
			else
				fp2=new ColorProcessor(zb,height);
			return true;
		}
		
		return false;
	}
	
	void updateXZView(Point p, ImageStack iStack) {
		xy_image.deleteRoi();
				int width= iStack.getWidth();
		int size=iStack.getSize();
		ImageProcessor ip=iStack.getProcessor(1);
		int y=p.y;
		
		//loop will set up RoiManager
		xz_image.getRoiManager().getListModel().removeAllElements();
		xz_image.getRoiManager().getROIs().clear();
		xz_image.getRoiManager().getRoisByNumbers().clear();
		xz_image.getRoiManager().getRoisByRootName().clear();
		xz_image.getRoiManager().getFullListModel().removeAllElements();

		if (xz_image.getWindow() != null && originalImp.getRoiManager().isShowAll()) {
			if ( stackRois[0] != null) {
				ArrayList<Roi> clonedRois = new ArrayList<Roi>();
				for (int r = 0; r < stackRois.length; r++) {
					Rectangle srb = stackRois[r].getBounds();
					for (int x = 0; x < width; x++) {
						if (srb.contains(x, y) && !clonedRois.contains(stackRois[r])) {
							if (stackRois[r] instanceof TextRoi || stackRois[r] instanceof OvalRoi || !(stackRois[r] instanceof ShapeRoi)) {
								Roi nextRoi = (Roi) stackRois[r].clone();
								clonedRois.add(stackRois[r]);
								nextRoi.setLocation(
										(int) (nextRoi.getBounds().x) ,
										(int) (nextRoi.getZPosition()
												* originalImp.getCalibration().pixelDepth/originalImp.getCalibration().pixelWidth 
												- srb.getHeight() / 2));
								xz_image.getRoiManager().addRoi(nextRoi);
								//							x = x+srb.width;
								//							r=stackRois.length;
							}
						}
					}
				}
			}
//			xz_image.getRoiManager().showAll(RoiManager.SHOW_ALL);
		}

		// XZ
		if (ip instanceof ShortProcessor) {
			short[] newpix=new short[width*size];
			for (int i=0; i<size; i++) { 
				Object pixels=iStack.getPixels(i+1);
				if (flipXZ)
					System.arraycopy(pixels, width*y, newpix, width*(size-i-1), width);
				else
					System.arraycopy(pixels, width*y, newpix, width*i, width);
			}
			fp1.setPixels(newpix);
			return;
		}
		
		if (ip instanceof ByteProcessor) {
			byte[] newpix=new byte[width*size];
			for (int i=0;i<size; i++) { 
				Object pixels=iStack.getPixels(i+1);
				if (flipXZ)
					System.arraycopy(pixels, width*y, newpix, width*(size-i-1), width);
				else
					System.arraycopy(pixels, width*y, newpix, width*i, width);
			}
			fp1.setPixels(newpix);
			return;
		}
		
		if (ip instanceof FloatProcessor) {
			float[] newpix=new float[width*size];
			for (int i=0; i<size; i++) { 
				Object pixels=iStack.getPixels(i+1);
				if (flipXZ)
					System.arraycopy(pixels, width*y, newpix, width*(size-i-1), width);
				else
					System.arraycopy(pixels, width*y, newpix, width*i, width);
			}
			fp1.setPixels(newpix);
			return;
		}
		
		if (ip instanceof ColorProcessor) {
			int[] newpix=new int[width*size];
			for (int i=0;i<size; i++) { 
				Object pixels=iStack.getPixels(i+1);
				if (flipXZ)
					System.arraycopy(pixels, width*y, newpix, width*(size-i-1), width);
				else
					System.arraycopy(pixels, width*y, newpix, width*i, width);
			}
			fp1.setPixels(newpix);
			return;
		}
		
	}
	
	void updateYZView(Point p, ImageStack iStack) {
		int width= iStack.getWidth();
		int height=iStack.getHeight();
		int ds=iStack.getSize();
		ImageProcessor ip=iStack.getProcessor(1);
		int x=p.x;
		
		if (ip instanceof FloatProcessor) {
			float[] newpix=new float[ds*height];
			for (int i=0;i<ds; i++) { 
				float[] pixels= (float[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int j=0;j<height;j++)
					newpix[(ds-i-1)*height + j] = pixels[x + j* width];
			}
			fp2.setPixels(newpix);
		}
		
		if (ip instanceof ByteProcessor) {
			byte[] newpix=new byte[ds*height];
			for (int i=0;i<ds; i++) { 
				byte[] pixels= (byte[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int j=0;j<height;j++)
					newpix[(ds-i-1)*height + j] = pixels[x + j* width];
			}
			fp2.setPixels(newpix);
		}
		
		if (ip instanceof ShortProcessor) {
			short[] newpix=new short[ds*height];
			for (int i=0;i<ds; i++) { 
				short[] pixels= (short[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int j=0;j<height;j++)
					newpix[(ds-i-1)*height + j] = pixels[x + j* width];
			}
			fp2.setPixels(newpix);
		}
		
		if (ip instanceof ColorProcessor) {
			int[] newpix=new int[ds*height];
			for (int i=0;i<ds; i++) { 
				int[] pixels= (int[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int j=0;j<height;j++)
					newpix[(ds-i-1)*height + j] = pixels[x + j* width];
			}
			fp2.setPixels(newpix);
		}
		if (!flipXZ) fp2.flipVertical();
		
	}
	
	void updateZYView(Point p, ImageStack iStack) {
		xy_image.deleteRoi();
		int width= iStack.getWidth();
		int height=iStack.getHeight();
		int ds=iStack.getSize();
		ImageProcessor ip=iStack.getProcessor(1);
		int x=p.x;

		//loop will set up RoiManager
		yz_image.getRoiManager().getListModel().removeAllElements();
		yz_image.getRoiManager().getROIs().clear();
		yz_image.getRoiManager().getRoisByNumbers().clear();
		yz_image.getRoiManager().getRoisByRootName().clear();
		yz_image.getRoiManager().getFullListModel().removeAllElements();
		if (yz_image.getWindow() != null  && originalImp.getRoiManager().isShowAll()) {
			if (stackRois[0] != null ) {
				ArrayList<Roi> clonedRois = new ArrayList<Roi>();
				for (int r = 0; r < stackRois.length; r++) {
					Rectangle srb = stackRois[r].getBounds();
					for (int y = 0; y < height; y++) {
						if (srb.contains(x, y) && !clonedRois.contains(stackRois[r])) {
							if (stackRois[r] instanceof TextRoi || stackRois[r] instanceof OvalRoi || !(stackRois[r] instanceof ShapeRoi)) {
								Roi nextRoi = (Roi) stackRois[r].clone();
								clonedRois.add(stackRois[r]);
								nextRoi.setLocation((int) (nextRoi.getZPosition()
										* originalImp.getCalibration().pixelDepth/originalImp.getCalibration().pixelHeight -
										srb.getWidth() / 2),
										(int) (nextRoi.getBounds().y));
								yz_image.getRoiManager().addRoi(nextRoi);
								//							y = y + srb.height;
								//							r=stackRois.length;
							}
						}
					}
				}
			}
//			yz_image.getRoiManager().showAll(RoiManager.SHOW_ALL);
		}
		
		if (ip instanceof FloatProcessor) {
			float[] newpix=new float[ds*height];
			for (int i=0;i<ds; i++) { 
				float[] pixels= (float[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int y=0;y<height;y++)
					newpix[i + y*ds] = pixels[x + y* width];
			}
			fp2.setPixels(newpix);
		}
		
		if (ip instanceof ByteProcessor) {
			byte[] newpix=new byte[ds*height];
			for (int i=0;i<ds; i++) { 
				byte[] pixels= (byte[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int y=0;y<height;y++)
					newpix[i + y*ds] = pixels[x + y* width];
			}
			fp2.setPixels(newpix);
		}
		
		if (ip instanceof ShortProcessor) {
			short[] newpix=new short[ds*height];
			for (int i=0;i<ds; i++) { 
				short[] pixels= (short[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int y=0;y<height;y++)
					newpix[i + y*ds] = pixels[x + y* width];
			}
			fp2.setPixels(newpix);
		}
		
		if (ip instanceof ColorProcessor) {
			int[] newpix=new int[ds*height];
			for (int i=0;i<ds; i++) { 
				int[] pixels= (int[]) iStack.getPixels(i+1);//toFloatPixels(pixels);
				for (int y=0;y<height;y++)
					newpix[i + y*ds] = pixels[x + y* width];
			}
			fp2.setPixels(newpix);
		}
		
	}
	 
	/** draws the crosses in the images */
	void drawCross(ImagePlus imp, Point p, Line vLine, Line hLine) {
		int width=imp.getWidth();
		int height=imp.getHeight();
		float x = p.x;
		float y = p.y;
		hLine = new Line(0f, y, width, y);
		vLine = new Line(x, 0f, x, height);	
	}
	      
	void dispose() {
		if (((StackWindow)originalImp.getWindow()).cSelector != null)
			((StackWindow)originalImp.getWindow()).cSelector.setIconEnabled(true);
		if (updater!=null) {
			updater.quit();
			updater = null;
		}
		int recentZ = originalImp.getSlice();
		if(xy_image!=null && xy_image.getWindow()!= null && !xy_image.getWindow().isClosed()) {		
			xy_image.setOverlay(null);
			recentZ = xy_image.getSlice();
			xyCanvas.removeMouseListener(this);
			xyCanvas.removeMouseMotionListener(this);
			xyCanvas.removeKeyListener(this);
			xyCanvas.setCustomRoi(false);
			xy_image.close();
		}

		if (xz_image!=null && xz_image.getWindow()!= null && !xz_image.getWindow().isClosed()) {		
			xz_image.setOverlay(null);
			xz_image.getRoiManager().dispose();
			WindowManager.removeWindow(xz_image.getRoiManager());
			ImageWindow win1 = xz_image.getWindow();
			if (win1!=null && !win1.isClosed()) {
				win1.removeMouseWheelListener(this);
				ImageCanvas2 ic = win1.getCanvas();
				if (ic!=null) {
					//IJ.log("TEST1");
					ic.removeKeyListener(this);
					ic.removeMouseListener(this);
					ic.removeMouseMotionListener(this);
					ic.setCustomRoi(false);
					win1.removeWindowListener(this);
					win1.removeFocusListener(this);
					win1.setResizable(true);
					win1.setBackground(defaultWinColor);
				}
			}
			xz_image.close();
		}
		
		
		if (yz_image!=null && yz_image.getWindow()!= null && !yz_image.getWindow().isClosed()) {		
			yz_image.setOverlay(null);
			yz_image.getRoiManager().dispose();
			WindowManager.removeWindow(yz_image.getRoiManager());
			ImageWindow win2 = yz_image.getWindow();
			if (win2!=null && !win2.isClosed()) {
				win2.removeMouseWheelListener(this);
				ImageCanvas2 ic = win2.getCanvas();
				if (ic!=null) {
					//IJ.log("TEST2");
					ic.removeKeyListener(this);
					ic.removeMouseListener(this);
					ic.removeMouseMotionListener(this);
					ic.setCustomRoi(false);
					win2.removeWindowListener(this);
					win2.removeFocusListener(this);
					win2.setResizable(true);
					win2.setBackground(defaultWinColor);
				}
			}
			yz_image.close();
		}
		ImagePlus.removeImageListener(this);
		Executer.removeCommandListener(this);
		if(xyWin!=null) {		
			xyWin.removeWindowListener(this);
			xyWin.removeFocusListener(this);
			xyWin.setResizable(true);
			xyWin.setBackground(defaultWinColor);
		}
		instance = null;
		previousID = originalImp.getID();
		previousX = crossLoc.x;
		previousY = crossLoc.y;
		if (!(originalImp instanceof CompositeImage) || originalImp.getNChannels()<=1 || currentMode != CompositeImage.COMPOSITE){
			ColorModel cm = (originalImp.getProcessor().getColorModel());
			int min = (int) originalImp.getProcessor().getMin();
			int max = (int) originalImp.getProcessor().getMax();
			originalImp.setStack(originalStack);
			originalImp.getProcessor().setColorModel(cm);
			originalImp.getProcessor().setMinAndMax(min, max);
			if (((StackWindow)originalImp.getWindow()).cSelector != null)
				((StackWindow)originalImp.getWindow()).cSelector.setEnabled(true);
			if (((StackWindow)originalImp.getWindow()).tSelector != null)
				((StackWindow)originalImp.getWindow()).tSelector.setEnabled(true);
			if (((StackWindow)originalImp.getWindow()).zSelector != null)
				((StackWindow)originalImp.getWindow()).zSelector.setEnabled(true);
		} else {
			originalImp.getStack().setColorModel(originalCM);
			originalImp.setStack(originalStack);
			((CompositeImage)originalImp).setMode(originalMode);
			if (((StackWindow)originalImp.getWindow()).cSelector != null)
				((StackWindow)originalImp.getWindow()).cSelector.setEnabled(true);
			if (((StackWindow)originalImp.getWindow()).tSelector != null)
				((StackWindow)originalImp.getWindow()).tSelector.setEnabled(true);
			if (((StackWindow)originalImp.getWindow()).zSelector != null)
				((StackWindow)originalImp.getWindow()).zSelector.setEnabled(true);
		}
//		xy_image.close();
		
// BELOW LINES COMMENTED OUT BECAUSE THEY CAUSE THREADING DEADLOCK BETWEEN SLICESELECTOR THREADS...PROBLEM MITIGATED??
//		originalImp.setPosition(originalImp.getChannel(), recentZ, originalImp.getFrame());
//		originalImp.setPosition(originalImp.getChannel(), recentZ+1, originalImp.getFrame());
//		originalImp.setPosition(originalImp.getChannel(), recentZ, originalImp.getFrame());
//		IJ.runMacro("waitForUser;");
		originalImp.updateAndDraw();
		imageStack = null;
	}
	
	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		ImageCanvas2 xyCanvas = xy_image.getCanvas();
		startingSrcRect = (Rectangle)xyCanvas.getSrcRect().clone();
		mouseDragged(e);
	}

	public void mouseDragged(MouseEvent e) {
		if (IJ.spaceBarDown())  // scrolling?
			return;
		if (e.getSource().equals(xyCanvas)) {
			crossLoc = xyCanvas.getCursorLoc();
//			IJ.log("md");
			update();

		} else if (e.getSource().equals(xz_image.getCanvas())) {
//			IJ.log("XZDRAG");
			crossLoc.x = xz_image.getCanvas().getCursorLoc().x;
			int pos = xz_image.getCanvas().getCursorLoc().y;
			int z = (int)Math.round(pos/az);
			int slice = flipXZ?originalImp.getNSlices()-z:z+1;
			if (originalImp instanceof CompositeImage && originalImp.getNChannels()>1 && currentMode < CompositeImage.GRAYSCALE) {
				xy_image.setSlice(slice);
//				imp.zeroUpdateMode = true;
//				imp.setPositionWithoutUpdate(imp.getChannel(), slice, imp.getFrame());
//				imp.zeroUpdateMode = false;
				((StackWindow)xy_image.getWindow()).zSelector.setValue(slice);

			} else if (originalImp.getStack() instanceof VirtualStack) {
				int min = (int) originalImp.getProcessor().getMin();
				int max = (int) originalImp.getProcessor().getMax();
				xy_image.setSlice(slice);
				xy_image.getProcessor().setMinAndMax(min, max);
//				imp.zeroUpdateMode = true;
//				imp.setPositionWithoutUpdate(imp.getChannel(), slice, imp.getFrame());
//				imp.zeroUpdateMode = false;
				((StackWindow)xy_image.getWindow()).zSelector.setValue(slice);

			} else {
				xy_image.setSlice(slice);
				((StackWindow)xy_image.getWindow()).zSelector.setValue(slice);

			}
		} else if (e.getSource().equals(yz_image.getCanvas())) {
//			IJ.log("YZDRAG");
			int pos;
			if (rotateYZ) {
				crossLoc.y = yz_image.getCanvas().getCursorLoc().x;
				pos = yz_image.getCanvas().getCursorLoc().y;
			} else {
				crossLoc.y = yz_image.getCanvas().getCursorLoc().y;
				pos = yz_image.getCanvas().getCursorLoc().x;
			}
			int z = (int)Math.round(pos/az);
			int slice = z+1;
			if (originalImp instanceof CompositeImage && originalImp.getNChannels()>1 && currentMode < CompositeImage.GRAYSCALE) {
				xy_image.setSlice(slice);
//				imp.zeroUpdateMode = true;
//				imp.setPositionWithoutUpdate(imp.getChannel(), slice, imp.getFrame());
//				imp.zeroUpdateMode = false;
				((StackWindow)xy_image.getWindow()).zSelector.setValue(slice);

			} else if (originalImp.getStack() instanceof VirtualStack) {
				int min = (int) originalImp.getProcessor().getMin();
				int max = (int) originalImp.getProcessor().getMax();
				xy_image.setSlice(slice);
				xy_image.getProcessor().setMinAndMax(min, max);
//				imp.zeroUpdateMode = true;
//				imp.setPositionWithoutUpdate(imp.getChannel(), slice, imp.getFrame());
//				imp.zeroUpdateMode = false;
				((StackWindow)xy_image.getWindow()).zSelector.setValue(slice);

			} else {
				xy_image.setSlice(slice);
				((StackWindow)xy_image.getWindow()).zSelector.setValue(slice);

			}
		}
		update();
	}

	public void mouseReleased(MouseEvent e) {
		ImageCanvas2 ic = xy_image.getCanvas();
		Rectangle srcRect = ic.getSrcRect();
		if (srcRect.x!=startingSrcRect.x || srcRect.y!=startingSrcRect.y) {
			// user has scrolled xy image
			int dy = srcRect.y - startingSrcRect.y;
			ImageCanvas2 yzic = yz_image.getCanvas();
			Rectangle yzSrcRect =yzic.getSrcRect();
			if (rotateYZ) {
				yzSrcRect.x += dy;
				if (yzSrcRect.x<0)
					yzSrcRect.x = 0;
				if (yzSrcRect.x>yz_image.getWidth()-yzSrcRect.width)
					yzSrcRect.y = yz_image.getWidth()-yzSrcRect.width;
			} else {
				yzSrcRect.y += dy;
				if (yzSrcRect.y<0)
					yzSrcRect.y = 0;
				if (yzSrcRect.y>yz_image.getHeight()-yzSrcRect.height)
					yzSrcRect.y = yz_image.getHeight()-yzSrcRect.height;
			}
			yzic.repaint();
			int dx = srcRect.x - startingSrcRect.x;
			ImageCanvas2 xzic = xz_image.getCanvas();
			Rectangle xzSrcRect =xzic.getSrcRect();
			xzSrcRect.x += dx;
			if (xzSrcRect.x<0)
				xzSrcRect.x = 0;
			if (xzSrcRect.x>xz_image.getWidth()-xzSrcRect.width)
				xzSrcRect.x = xz_image.getWidth()-xzSrcRect.width;
			xzic.repaint();
		}
	}
	
	/**
	 * Refresh the output windows. This is done by sending a signal 
	 * to the Updater() thread. 
	 */
	void update() {
		if (updater!=null)
			updater.doUpdate();
	}
	
	private void exec() {
		if (xyCanvas==null) return;
		int width=originalImp.getWidth();
		int height=originalImp.getHeight();
		if (hyperstack) {
			int c = originalImp.getChannel();
			int t = originalImp.getFrame();
			if (c!=currentChannel || t!=currentFrame)
				imageStack = null;
			if (originalImp.isComposite()) {
				int mode = ((CompositeImage)originalImp).getMode();
				if (mode!=currentMode)
					imageStack = null;
			}
		}
		
		if (imageStack==null)
			imageStack = getStack();
		xy_image.setStack(imageStack);
//		xy_image.setSlice(imp.getSlice());

		double arat=az/ax;
		double brat=az/ay;
		Point p=crossLoc;
		if (p.y>=height) p.y=height-1;
		if (p.x>=width) p.x=width-1;
		if (p.x<0) p.x=0;
		if (p.y<0) p.y=0;
//		IJ.log("UPDATEVIEWS");
		updateViews(p, imageStack);
		Line vLine = null;
		Line hLine = null;
		hLine = new Line(0f, p.y, width, p.y);
		vLine = new Line(p.x, 0f, p.x, height);	
		Overlay overlay = new Overlay();
		vLine.setStrokeColor(Color.cyan);
		hLine.setStrokeColor(Color.magenta);
		overlay.add(vLine);
		overlay.add(hLine);
		
		Roi yzLabel = new TextRoi(p.x+1,1,"YZ", Font.decode("Arial-"+8/xyCanvas.getMagnification()));
		yzLabel.setStrokeColor(Color.cyan);
		Roi xzLabel = new TextRoi(1,p.y,"XZ", Font.decode("Arial-"+8/xyCanvas.getMagnification()));
		xzLabel.setStrokeColor(Color.magenta);
		overlay.add(yzLabel);
		overlay.add(xzLabel);
		xy_image.setOverlay(overlay);
		xyCanvas.setCustomRoi(true);
		updateCrosses(p.x, p.y, arat, brat);
		if (syncZoom) updateMagnification(p.x, p.y);
		arrangeWindows(sticky);
	}

	private void updateCrosses(int x, int y, double arat, double brat) {
		Point p;
		int z=originalImp.getNSlices();
		int zlice=((StackWindow)xy_image.getWindow()).zSelector.getValue()-1;
		int zcoord=(int)Math.round(arat*zlice);
		if (flipXZ) zcoord = (int)Math.round(arat*(z-zlice));
		
//		ImageCanvas2 xzCanvas = xz_image.getCanvas();
		p=new Point (x, zcoord);
		Line vLine = null;
		Line hLine = null;
		hLine = new Line(0f, p.y, xz_image.getWidth(), p.y);
		vLine = new Line(p.x, 0f, p.x, xz_image.getHeight());	
		Overlay overlay = new Overlay();
		vLine.setStrokeColor(Color.cyan);
		hLine.setStrokeColor(Color.yellow);
		overlay.add(vLine);
		overlay.add(hLine);
		
		Roi yzLabel = new TextRoi(p.x+1,1,"YZ", Font.decode("Arial-"+8/xyCanvas.getMagnification()));
		yzLabel.setStrokeColor(Color.cyan);
		Roi xyLabel = new TextRoi(1,p.y,"XY", Font.decode("Arial-"+8/xyCanvas.getMagnification()));
		xyLabel.setStrokeColor(Color.yellow);
		overlay.add(yzLabel);
		overlay.add(xyLabel);
		xz_image.setOverlay(overlay);
//		xz_image.setOverlay(path, color, new BasicStroke(1));
		if (rotateYZ) {
			if (flipXZ)
				zcoord=(int)Math.round(brat*(z-zlice));
			else
				zcoord=(int)Math.round(brat*(zlice));
			p=new Point (y, zcoord);
		} else {
			zcoord=(int)Math.round(arat*zlice);
			p=new Point (zcoord, y);
		}
		hLine = new Line(0f, p.y, yz_image.getWidth(), p.y);
		vLine = new Line(p.x, 0f, p.x, yz_image.getHeight());	
		overlay = new Overlay();
		vLine.setStrokeColor(Color.yellow);
		hLine.setStrokeColor(Color.magenta);
		overlay.add(vLine);
		overlay.add(hLine);
		
		xyLabel = new TextRoi(p.x+1,1,"XY", Font.decode("Arial-"+8/xyCanvas.getMagnification()));
		xyLabel.setStrokeColor(Color.yellow);
		Roi xzLabel = new TextRoi(1,p.y,"XZ", Font.decode("Arial-"+8/xyCanvas.getMagnification()));
		xzLabel.setStrokeColor(Color.magenta);
		overlay.add(xyLabel);
		overlay.add(xzLabel);
		yz_image.setOverlay(overlay);
//		yz_image.setOverlay(path, color, new BasicStroke(1));
		IJ.showStatus(xy_image.getLocationAsString(crossLoc.x, crossLoc.y));
	}

	public void mouseMoved(MouseEvent e) {
	}

	public void keyPressed(KeyEvent e) {
		int key = e.getKeyCode();
		if (key==KeyEvent.VK_ESCAPE) {
			IJ.beep();
			dispose();
		} else if (IJ.shiftKeyDown()) {
			int width=originalImp.getWidth(), height=originalImp.getHeight();
			switch (key) {
				case KeyEvent.VK_LEFT: crossLoc.x--; if (crossLoc.x<0) crossLoc.x=0; break;
				case KeyEvent.VK_RIGHT: crossLoc.x++; if (crossLoc.x>=width) crossLoc.x=width-1; break;
				case KeyEvent.VK_UP: crossLoc.y--; if (crossLoc.y<0) crossLoc.y=0; break;
				case KeyEvent.VK_DOWN: crossLoc.y++; if (crossLoc.y>=height) crossLoc.y=height-1; break;
				default: return;
			}
//			IJ.log("kp");
			update();
		}
	}

	public void keyReleased(KeyEvent e) {
	}

	public void keyTyped(KeyEvent e) {
	}

	public void actionPerformed(ActionEvent ev) {
	}

	public void imageClosed(ImagePlus imp) {
		dispose();
	}

	public void imageOpened(ImagePlus imp) {
	}

	public void imageUpdated(ImagePlus imp) {
		if (imp==this.originalImp) {
			ImageProcessor ip = imp.getProcessor();
			min = ip.getMin();
			max = ip.getMax();
//			IJ.log("iu");
			update();
		}
	}

	public String commandExecuting(String command) {
		if (command.equals("In")||command.equals("Out")) {
			ImagePlus cimp = WindowManager.getCurrentImage();
			if (cimp==null) return command;
			if (cimp==xy_image) {
				ImageCanvas2 ic = xy_image.getCanvas();
				if (ic==null) return null;
				int x = ic.screenX(crossLoc.x);
				int y = ic.screenY(crossLoc.y);
				if (command.equals("In")) {
					ic.zoomIn(x, y);
					if (ic.getMagnification()<=1.0) xy_image.repaintWindow();
				} else {
					ic.zoomOut(x, y);
					if (ic.getMagnification()<1.0) xy_image.repaintWindow();
				}
				xyX=crossLoc.x; xyY=crossLoc.y;
//				IJ.log("cx");
				update();
				return null;
			} else if (cimp==xz_image || cimp==yz_image) {
				syncZoom = false;
				return command;
			} else
				return command;
		} else if (command.equals("Flip Vertically")&& xz_image!=null) {
			if (xz_image==WindowManager.getCurrentImage()) {
				flipXZ = !flipXZ;
//				IJ.log("cx");
				update();
				return null;
			} else
				return command;
		} else
			return command;
	}

	public void windowActivated(WindowEvent e) {
		 arrangeWindows(sticky);
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		dispose();		
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
		 arrangeWindows(sticky);
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowOpened(WindowEvent e) {
	}

	public void adjustmentValueChanged(AdjustmentEvent e) {
//		IJ.log("avc");
		update();
	}
		
	public void mouseWheelMoved(MouseWheelEvent e) {
		if (e.getSource().equals(xz_image.getWindow())) {
			crossLoc.y += e.getWheelRotation();
		} else if (e.getSource().equals(yz_image.getWindow())) {
			crossLoc.x += e.getWheelRotation();
		}
//		IJ.log("mwm");
		update();
	}

	public void focusGained(FocusEvent e) {
		ImageCanvas2 ic = xy_image.getCanvas();
		if (ic!=null) xyCanvas.requestFocus();
		arrangeWindows(sticky);
	}

	public void focusLost(FocusEvent e) {
		arrangeWindows(sticky);
	}
	
	public static ImagePlus getImage() {
		if (instance!=null)
			return instance.originalImp;
		else
			return null;
	}
	
	public static synchronized boolean isOrthoViewsImage(ImagePlus imp) {
		if (imp==null || instance==null)
			return false;
		else
			return imp==instance.originalImp || imp==instance.xz_image || imp==instance.yz_image || imp == instance.xy_image;
	}

	public static Orthogonal_Views getInstance() {
		return instance;
	}

	public int[] getCrossLoc() {
		int[] loc = new int[3];
		loc[0] = crossLoc.x;
		loc[1] = crossLoc.y;
		loc[2] = originalImp.getSlice()-1;
		return loc;
	}
	
	public void setCrossLoc(int x, int y, int z) {
		crossLoc.setLocation(x, y);
		if (hyperstack)
			originalImp.setPosition(originalImp.getChannel(), z+1, originalImp.getFrame());
		else
			originalImp.setSlice(z+1);
//		IJ.log("setCrossLoc");
		update();
	}
	
	public ImagePlus getXZImage(){
		return xz_image;
	}
	
	public ImagePlus getYZImage(){
		return yz_image;
	}

	/**
	 * This is a helper class for Othogonal_Views that delegates the
	 * repainting of the destination windows to another thread.
	 * 
	 * @author Albert Cardona
	 */
	private class Updater extends Thread {
		long request = 0;

		// Constructor autostarts thread
		Updater() {
			super("Othogonal Views Updater");
			setPriority(Thread.NORM_PRIORITY);
			start();
		}

		void doUpdate() {
			if (isInterrupted()) return;
			synchronized (this) {
				request++;
//				IJ.log(""+request);
				notify();
			}
		}

		void quit() {
			IJ.wait(10);
			interrupt();
			synchronized (this) {
				notify();
			}
		}

		public void run() {
			exec();
			while (!isInterrupted()) {
				try {
					final long r;
					synchronized (this) {
						r = request;
					}
					// Call update from this thread
					if (r>0){
//						IJ.log(""+r);
//						request--;
						exec();
					}
					synchronized (this) {
						if (r==request) {
							request = 0; // reset
							wait();
						}
						// else loop through to update again
					}
				} catch (Exception e) { }
			}
		}
		
	}  // Updater class

}
