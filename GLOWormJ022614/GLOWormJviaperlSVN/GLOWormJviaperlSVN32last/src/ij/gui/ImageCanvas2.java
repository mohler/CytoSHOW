package ij.gui;

import java.awt.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Hashtable;
import java.util.Properties;
import java.awt.image.*;
import java.io.IOException;

import ij.process.*;
import ij.measure.*;
import ij.plugin.Colors;
import ij.plugin.DragAndDrop;
import ij.plugin.WandToolOptions;
import ij.plugin.Zoom;
import ij.plugin.filter.Projector;
import ij.plugin.frame.ColorLegend;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.plugin.tool.MacroToolRunner;
import ij.plugin.tool.PlugInTool;
import ij.macro.*;
import ij.*;
import ij.util.*;
import ij3d.ImageCanvas3D;
import ij3d.ImageWindow3D;
import ij3d.Image3DUniverse;

import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.util.*;
import java.awt.geom.*;

import javax.imageio.ImageIO;
import javax.media.j3d.Canvas3D;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import org.rhwlab.acetree.AceTree;
import org.rhwlab.tree.Cell;
import org.rhwlab.tree.SulstonTree;
import org.vcell.gloworm.MultiQTVirtualStack;

/** This is a Canvas used to display images in a Window. */
public class ImageCanvas2 extends JComponent implements MouseListener, MouseMotionListener, MouseWheelListener, ComponentListener, Cloneable {

	public static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
	protected static Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);
	protected static Cursor crosshairCursor;	

	public static boolean usePointer = Prefs.usePointerCursor;

	protected ImagePlus imp;
	protected boolean imageUpdated;
	protected Rectangle srcRect;
	protected int imageWidth, imageHeight;
	private int xMouse; // current cursor offscreen x location 
	private int yMouse; // current cursor offscreen y location

	private boolean showCursorStatus = true;
	private int sx2, sy2;
	private boolean disablePopupMenu;
	private boolean showAllROIs;
	private boolean showOwnROIs = false;
	private static Color zoomIndicatorColor;
	private static Font smallFont, largeFont;
	private Font font;
	private Hashtable<Roi,ShapeRoi> labelShapes;
	private boolean maxBoundsReset;
	private Overlay overlay, showAllList, showAllOverlay;
	private static final int LIST_OFFSET = 100000;
	private static Color showAllColor = Prefs.getColor(Prefs.SHOW_ALL_COLOR, new Color(0, 255, 255));
	private Color defaultColor = showAllColor;
	private static Color labelColor, bgColor;
	private int resetMaxBoundsCount;
	private Roi currentRoi;
	private int mousePressedX, mousePressedY;
	private long mousePressedTime;
	private JPopupMenu popup;

	protected ImageJ ij;
	protected double magnification;
	public int dstWidth;
	public int dstHeight;

	protected int xMouseStart;
	protected int yMouseStart;
	protected int xSrcStart;
	protected int ySrcStart;
	protected int flags;

	public Image offScreenImage;
	private int offScreenWidth = 0;
	private int offScreenHeight = 0;
	private boolean mouseExited = true;
	private boolean customRoi;
	private boolean drawNames;
	public Hashtable<String,Roi> messageRois;
	public String droppedGeneUrls = "";
	public boolean sketchyMQTVS;
	private ArrayList<Roi> sliceRois;
	private RoiBrush canvasRoiBrush;



	public ImageCanvas2(ImagePlus imp) {
		this.imp = imp;
		//		sketchyMQTVS = false;
		
		if (imp.getWindow()!=null && imp.getWindow() instanceof ImageWindow3D){
			
		} else {
			if (imp.getMotherImp().getStack() instanceof MultiQTVirtualStack) {
				if (((MultiQTVirtualStack) imp.getMotherImp().getStack()).getVirtualStack(0) != null) {

					for (int i=0;i<imp.getNChannels();i++) {
						if (((MultiQTVirtualStack) imp.getMotherImp().getStack()).getVirtualStack(i) != null) {
							if ( ((MultiQTVirtualStack) imp.getMotherImp().getStack()).getVirtualStack(i).getMovieName()
									.startsWith("Sketch3D")) {
								sketchyMQTVS = true;
								//					IJ.showStatus(""+sketchyMQTVS);
							}
						}
					}
				}
			}
		}

		ij = IJ.getInstance();
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setDrawingSize(imageWidth, imageHeight);
		magnification = 1.0;
		messageRois = new Hashtable<String,Roi>();
//		try {
//			crosshairCursor =  Toolkit.getDefaultToolkit().createCustomCursor(ImageIO.read(ImageWindow.class.getResource("images/crosshairAA2_32x32.png")),new Point(16,16),"crosshairCursor");
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		Image img = new BufferedImage(32,32, BufferedImage.TYPE_INT_ARGB_PRE);

		//		img.getGraphics().setColor(Colors.decode("00000000", Color.white));
		Graphics2D g2d = (Graphics2D) img.getGraphics();

		g2d.setFont(font);

//		g2d.setColor(Colors.decode("#00111111",Color.gray));
//		g2d.fillRect(0, 0, 32,32);
		g2d.setColor(Color.WHITE);
		g2d.fillRect(15, 8, 3, 16);
		g2d.fillRect(8, 15, 16, 3);
		g2d.setColor(Color.BLACK);
		g2d.fillRect(16, 8, 1, 16);
		g2d.fillRect(8, 16, 16, 1);
		crosshairCursor = Toolkit.getDefaultToolkit().createCustomCursor(img,new Point(16,16),"crosshairCursor");

		DragAndDrop dnd = new DragAndDrop();
		if (dnd!=null)
			dnd.addDropTarget(this);

		addMouseListener(this);
		addMouseMotionListener(this);
		addMouseWheelListener (this);
		addComponentListener(this);
		addKeyListener(ij);  // ImageJ handles keyboard shortcuts
		setFocusTraversalKeysEnabled(false);
	}

	void updateImage(ImagePlus imp) {
		this.imp = imp;
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setDrawingSize(imageWidth, (int)imageHeight);
		magnification = 1.0;
	}

	/** Update this ImageCanvas2 to have the same zoom and scale settings as the one specified. */
	void update(ImageCanvas2 ic) {
		if (ic == null /* || ic==this */|| ic.imp==null)
			return;
		
//		IJ.log("a. "+ic.getBounds().width +" "+ic.getBounds().height+" "+magnification);
		
		if (ic.imp.getWidth()!=imageWidth || ic.imp.getHeight()!=imageHeight)
			return;
		
		dstWidth = ic.getBounds().width;
		dstHeight = ic.getBounds().height;
		
//		IJ.log("b. "+dstWidth +" "+dstHeight+" "+magnification);
		
		srcRect.width = (int)(dstWidth/magnification);
		srcRect.height = (int)(dstHeight/magnification);
		
		if (srcRect.width > imageWidth) {
			srcRect.width = imageWidth;
			srcRect.height = imageHeight;
		}
		
//		IJ.log("c. "+srcRect.width +" "+srcRect.height+" "+magnification);
		
		if (srcRect.width < (int) (dstWidth/magnification)){
				dstWidth = (int) (srcRect.width * magnification);
				dstHeight = (int) (srcRect.height * magnification);
		}

//		IJ.log("d. "+dstWidth +" "+dstHeight+" "+magnification);
		
		
		srcRect = new Rectangle(ic.srcRect.x, ic.srcRect.y, ic.srcRect.width, ic.srcRect.height);
		setMagnification(ic.magnification);
		setDrawingSize(ic.dstWidth, ic.dstHeight);
	}

	public void setSourceRect(Rectangle r) {
		srcRect = r;
	}

	void setSrcRect(Rectangle srcRect) {
		this.srcRect = srcRect;
	}

	public Rectangle getSrcRect() {
		return srcRect;
	}

	public void setDrawingSize(int width, int height) {
		dstWidth = width;
		dstHeight = height;
		setSize(dstWidth, dstHeight);
	}

	/** ImagePlus.updateAndDraw calls this method to force the paint()
		method to update the image from the ImageProcessor. */
	public void setImageUpdated() {
		imageUpdated = true;
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void paint(Graphics g) {
		Roi roi = imp.getRoi();
		if (this == imp.getCanvas() && (roi!=null || showAllROIs || overlay!=null)) {
			if (roi!=null) roi.updatePaste();
			if (/*!IJ.isMacOSX() &&*/ imageWidth!=0) {
				paintDoubleBuffered(g);
				return;
			}
		}
		try {
			if (imageUpdated) {
				imageUpdated = false;
				imp.updateImage();
			}
			setInterpolation(g, Prefs.interpolateScaledImages);
			Image img = imp.getImage();
			if (img!=null)
				g.drawImage(img, 0, 0, (int)(srcRect.width*magnification), (int)(srcRect.height*magnification),
						srcRect.x, srcRect.y, srcRect.x+srcRect.width, srcRect.y+srcRect.height, null);
			if (overlay!=null) drawOverlay(g);
			if (showAllROIs) 
				drawAllROIs(g);
			else {
				setSliceRois(null);
				String rbnString = ""+(imp.getChannel()>0?imp.getChannel():1)+"_"+(imp.getSlice())+"_"+imp.getFrame();
				String rbnCh0String = ""+0+"_"+imp.getSlice()+"_"+imp.getFrame();

				sliceRois = new ArrayList<Roi>();
				if(imp.getRoiManager().getROIsByNumbers().get(rbnString)!=null) {
					labelShapes.clear();
					sliceRois.addAll(imp.getRoiManager().getROIsByNumbers().get(rbnString));
				}
				if(imp.getRoiManager().getROIsByNumbers().get(rbnCh0String)!=null) {
					labelShapes.clear();
					sliceRois.addAll(imp.getRoiManager().getROIsByNumbers().get(rbnCh0String));
				}
			}
			if (roi!=null) drawRoi(roi, g);
			if (srcRect.width+10<imageWidth || srcRect.height+10<imageHeight)
				drawZoomIndicator(g);
			if (IJ.debugMode) showFrameRate(g);
		}
		catch(OutOfMemoryError e) {IJ.outOfMemory("Paint");}
	}

	private void setInterpolation(Graphics g, boolean interpolate) {
		if (magnification==1)
			return;
		else if (magnification<1.0 || interpolate) {
			Object value = RenderingHints.VALUE_RENDER_QUALITY;
			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_RENDERING, value);
		} else if (magnification>1.0) {
			Object value = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
			((Graphics2D)g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, value);
		}
	}

	private void drawRoi(Roi roi, Graphics g) {
		if (roi==currentRoi) {
			Color lineColor = roi.getStrokeColor();
			Color fillColor = roi.getFillColor();
			float lineWidth = roi.getStrokeWidth();
			roi.setStrokeColor(null);
			roi.setFillColor(null);
			boolean strokeSet = roi.getStroke()!=null;
			if (strokeSet)
				roi.setStrokeWidth(1);
			roi.draw(g);
			roi.setStrokeColor(lineColor);
			if (strokeSet)
				roi.setStrokeWidth(lineWidth);
			roi.setFillColor(fillColor);
			currentRoi = null;
		} else
			roi.draw(g);
	}

	void drawAllROIs(Graphics g) {
		RoiManager rm = this.imp.getRoiManager();
		//		if (rm!=null) IJ.log(rm.toString());
		if (rm==null) {
			rm = Interpreter.getBatchModeRoiManager();
			if (rm!=null && rm.getListModel().getSize()==0)
				rm = null;
		}
		if (rm==null) {
			showAllROIs = false;
			paintDoubleBuffered(getGraphics());
			return;
		}
		initGraphics(g, null, showAllColor);
		Hashtable<String,Roi> rois = rm.getROIs();
		DefaultListModel<String> listModel = rm.getListModel();
		boolean drawLabels = rm.getDrawLabels();
		currentRoi = null;
		int n = 0;
		if (listModel != null)
			n = listModel.getSize();
		if (IJ.debugMode) IJ.log("paint: drawing "+n+" \"Show All\" ROIs");
		//		if (labelShapes==null || labelShapes.length!=n)
		setLabelShapes(new Hashtable<Roi,ShapeRoi>());
		if (!drawLabels)
			showAllList = new Overlay();
		else
			showAllList = null;
		if (imp==null)
			return;
		int currentImage = imp.getCurrentSlice();
		int channel=0, slice=0, frame=0;
		boolean hyperstack = imp.getWindow() instanceof StackWindow;
		if ( true || hyperstack ) {
			channel = imp.getChannel();
			slice = imp.getSlice();
			frame = imp.getFrame();
		}
		drawNames = Prefs.useNamesAsLabels;
		
		sliceRois = new ArrayList<Roi>();
		for (int tSpan=imp.getFrame()-rm.getTSustain()+1;tSpan<=imp.getFrame()+rm.getTSustain()-1;tSpan++) {
			for (int zSpan=imp.getSlice()-rm.getZSustain()+1;zSpan<=imp.getSlice()+rm.getZSustain()-1;zSpan++) {
				String rbnString = ""+(imp.getChannel()>0?imp.getChannel():1)+"_"+zSpan+"_"+tSpan;
				if(rm.getROIsByNumbers().get(rbnString)!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get(rbnString));
				if(rm.getROIsByNumbers().get("0_0_0")!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get("0_0_0"));
				if(rm.getROIsByNumbers().get("0_"+zSpan+"_"+tSpan)!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get("0_"+zSpan+"_"+tSpan));
				if(rm.getROIsByNumbers().get((imp.getChannel()>0?imp.getChannel():1)+"_0"+"_"+tSpan)!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get((imp.getChannel()>0?imp.getChannel():1)+"_0"+"_"+tSpan));
				if(rm.getROIsByNumbers().get((imp.getChannel()>0?imp.getChannel():1)+"_0_0")!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get((imp.getChannel()>0?imp.getChannel():1)+"_0_0"));
				if(rm.getROIsByNumbers().get((imp.getChannel()>0?imp.getChannel():1)+"_"+zSpan+"_0")!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get((imp.getChannel()>0?imp.getChannel():1)+"_"+zSpan+"_0"));
				if(rm.getROIsByNumbers().get(("0_"+zSpan+"_0"))!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get(("0_"+zSpan+"_0")));
				if(rm.getROIsByNumbers().get("0_0"+"_"+tSpan)!=null)
					sliceRois.addAll(rm.getROIsByNumbers().get("0_0"+"_"+tSpan));
			}
		}

		if (sliceRois.size() > 0) {
//			IJ.log(""+sliceRois.size());
			Roi[] sliceRoisArray = new Roi[sliceRois.size()];
			for (int sr=0;sr<sliceRoisArray.length;sr++)
				sliceRoisArray[sr] = ((Roi)sliceRois.get(sr));
			labelShapes = new Hashtable<Roi,ShapeRoi>();
			for (int i=0; i<sliceRoisArray.length; i++) {  
				String label = null;
				Roi roi = null;
				try {
					roi = sliceRoisArray[i];
					label = roi.getName();
				} catch(Exception e) {
					roi = null;
				}
				Color roiFillColor = roi.getFillColor();
				if (roi==null 
						|| roiFillColor==null 
						|| (0 < roiFillColor.getAlpha() && roiFillColor.getAlpha() < 17) 
						|| !rm.getListModel().contains(roi.getName() )) 
					continue;
				if (showAllList!=null)
					showAllList.add(roi);
				if (i<200 && drawLabels && roi==imp.getRoi())
					currentRoi = roi;
				if (Prefs.showAllSliceOnly && (true || imp.getStackSize()>1) ) {
					if (true || hyperstack) {

						int c = roi.getCPosition();
						int z = roi.getZPosition();
						int t = roi.getTPosition();

						if (roi.getName() != null && roi.getName().split("_").length>3) {
							if (roi.getName().split("_")[3].contains("C"))
								c=0;
							if (roi.getName().split("_")[3].contains("Z"))
								z=0;
							if (roi.getName().split("_")[3].contains("T"))
								t=0;
						}
						//IJ.log(""+c+z+t);
						CompositeImage ci = null;
						if (imp.isComposite()) ci = ((CompositeImage)imp);

						//					IJ.log(Math.abs(z-slice) +" "+ rm.getZSustain() +"z");
						//					IJ.log(Math.abs(t-frame) +" "+ rm.getTSustain() +"t");
						if ((c==0||c==channel|| (ci !=null && ci.getMode() == ci.COMPOSITE)) 
								&& (z==0||z==slice||(!roi.getName().contains(" zL") && Math.abs(z-slice)<rm.getZSustain()) /*|| Math.abs(z+(roi.getMotherImp()!=null?roi.getMotherImp().getNSlices():imp.getNSlices())-slice)<rm.getZSustain()*/ ) 
								&& (t==0||t==frame||(!roi.getName().contains(" tL") && Math.abs(t-frame)<rm.getTSustain()) /*|| Math.abs(t+(roi.getMotherImp()!=null?roi.getMotherImp().getNFrames():imp.getNFrames())-frame)<rm.getTSustain()*/ ) ) {
							Color origColor = roi.getStrokeColor();
							if (origColor == null) origColor = showAllColor;
							if ((c!=channel ||  z !=slice || t != frame) 
									&& (c!=0) && !(z==0 && t==frame) && !(t==0 && z==slice) && !(z==0 && t==0)) 
								roi.setStrokeColor(origColor.darker());
							drawRoi(g, roi, drawLabels?i:-1);
							roi.setStrokeColor(origColor);
							if (roi instanceof ShapeRoi) {
								labelShapes.put(roi, (ShapeRoi)roi);
							}else{
								labelShapes.put(roi,(roi instanceof Arrow?((Arrow)roi).getShapeRoi():new ShapeRoi(roi)));
								if (labelShapes!=null && roi!= null)
									if (labelShapes.get(roi) != null) {
										labelShapes.get(roi).setName(roi.getName());
									}
							}
						}
					} else {
						int position = roi.getPosition();
						if (position==0)
							position = getSliceNumber(roi.getName());
						if (position==0 || position==currentImage)
							drawRoi(g, roi, -1);  //Why is this line here?
							;
						drawRoi(g, roi, drawLabels?i:-1);
					}
				}
			}
		}
		if (overlay != null) {
			for (int o=0;o<overlay.size();o++)
				drawRoi(g, overlay.get(o), -1);
				;
		}
		((Graphics2D)g).setStroke(Roi.onePixelWide);
	}


	public ArrayList<Roi> getSliceRois() {
		return sliceRois;
	}

	public void setSliceRois(ArrayList<Roi> sliceRois) {
		this.sliceRois = sliceRois;
		if (sliceRois == null || sliceRois.size() == 0) {
			String rbnString = ""+(imp.getChannel()>0?imp.getChannel():1)+"_"+(imp.getSlice())+"_"+imp.getFrame();
			String rbnCh0String = ""+0+"_"+imp.getSlice()+"_"+imp.getFrame();

			sliceRois = new ArrayList<Roi>();
			if(imp.getRoiManager().getROIsByNumbers().get(rbnString)!=null) {
				labelShapes = new Hashtable<Roi,ShapeRoi>();
				sliceRois.addAll(imp.getRoiManager().getROIsByNumbers().get(rbnString));
			}
			if(imp.getRoiManager().getROIsByNumbers().get(rbnCh0String)!=null) {
				labelShapes = new Hashtable<Roi,ShapeRoi>();
				sliceRois.addAll(imp.getRoiManager().getROIsByNumbers().get(rbnCh0String));
			}
		}

	}

	// MODIFIED ROUGHLY TO WORK WITH MULTICHANNEL STACKS
	public int getSliceNumber(String label) {
		int channels = imp.getNChannels();
		if (IJ.debugMode) IJ.log(label +  channels );
		int slice = -1;
		if (label.length()>=14 && label.charAt(4)=='-' && label.charAt(9)=='-'){
			slice = (int)Tools.parseDouble(label.substring(0,4),-1);
			if (imp.getStack() instanceof MultiQTVirtualStack) slice = channels * (slice-1) +1;
		} else if (label.length()>=17 && label.charAt(5)=='-' && label.charAt(11)=='-'){
			slice = (int)Tools.parseDouble(label.substring(0,5),-1);
			if (imp.getStack() instanceof MultiQTVirtualStack)  slice = channels * (slice-1) +1;
		} else if (label.length()>=20 && label.charAt(6)=='-' && label.charAt(13)=='-'){
			slice = (int)Tools.parseDouble(label.substring(0,6),-1);
			if (imp.getStack() instanceof MultiQTVirtualStack)  slice = channels * (slice-1) +1;
		}
		return slice;
	}

	void drawOverlay(Graphics g) {
		if (imp!=null && imp.getHideOverlay())
			return;
		Color labelColor = overlay.getLabelColor();
		if (labelColor==null) labelColor = Color.white;
		initGraphics(g, labelColor, Roi.getColor());
		int n = overlay.size();
		if (IJ.debugMode) IJ.log("paint: drawing "+n+" ROI display list");
		int currentImage = imp!=null?imp.getCurrentSlice():-1;
		if (imp.getStackSize()==1)
			currentImage = -1;
		int channel=0, slice=0, frame=0;
		boolean hyperstack = imp.isHyperStack();
		if (hyperstack) {
			channel = imp.getChannel();
			slice = imp.getSlice();
			frame = imp.getFrame();
		}
		drawNames = overlay.getDrawNames();
		boolean drawLabels = drawNames || overlay.getDrawLabels();
		font = overlay.getLabelFont();
		for (int i=0; i<n; i++) {
			if (overlay==null) break;
			Roi roi = overlay.get(i);
			if (roi.isActiveOverlayRoi()) {
				Color fillColor = roi.getFillColor();
				if ((fillColor!=null&&fillColor.getAlpha()!=255) || (roi instanceof ImageRoi))
					continue;
			}
			if (hyperstack && roi.getPosition()==0) {
				int c = roi.getCPosition();
				int z = roi.getZPosition();
				int t = roi.getTPosition();
				if ((c==0||c==channel) && (z==0||z==slice) && (t==0||t==frame))
					drawRoi(g, roi, drawLabels?i+LIST_OFFSET:-1);
			} else {
				int position = roi.getPosition();
				if (position==0 || position==currentImage)
					drawRoi(g, roi, drawLabels?i+LIST_OFFSET:-1);
			}
		}
		((Graphics2D)g).setStroke(Roi.onePixelWide);
		drawNames = false;
		font = null;
	}

	void initGraphics(Graphics g, Color textColor, Color defaultColor) {
		if (smallFont==null) {
			smallFont = new Font("SansSerif", Font.PLAIN, 9);
			largeFont = new Font("SansSerif", Font.PLAIN, 12);
		}
		if (textColor!=null) {
			labelColor = textColor;
			if (overlay!=null && overlay.getDrawBackgrounds())
				bgColor = new Color(255-labelColor.getRed(), 255-labelColor.getGreen(), 255-labelColor.getBlue());
			else
				bgColor = null;
		} else {
			int red = defaultColor.getRed();
			int green = defaultColor.getGreen();
			int blue = defaultColor.getBlue();
			if ((red+green+blue)/3<128)
				labelColor = Color.white;
			else
				labelColor = Color.black;
			bgColor = defaultColor;
		}
		this.setDefaultColor(defaultColor);
		g.setColor(defaultColor);
	}

	public void drawRoi(Graphics g, Roi roi, int index) {
		int type = roi.getType();
		ImagePlus imp2 = roi.getImage();
		roi.setImage(imp);
		if(roi.getImage() != null && roi.getImage().flatteningCanvas != null){
			roi.ic = roi.getImage().flatteningCanvas;
			roi.mag =1.0;
		}

		Color saveColor = roi.getStrokeColor();
		if (saveColor==null)
			roi.setStrokeColor(getDefaultColor());
		if (roi.getStroke()==null)
			((Graphics2D)g).setStroke(Roi.onePixelWide);
		if (roi instanceof TextRoi)
			((TextRoi)roi).drawText(g);
		else
			roi.drawOverlay(g);
		roi.setStrokeColor(saveColor);
		if (index>=0) {
			if (roi==currentRoi)
				g.setColor(Roi.getColor());
			else
				g.setColor(getDefaultColor());
			drawRoiLabel(g, index, roi);
		}
		if (imp2!=null)
			roi.setImage(imp2);
		else
			roi.setImage(null);
	}

	void drawRoiLabel(Graphics g, int index, Roi roi) {
		Rectangle r = roi.getBounds();
		int x = screenX(r.x);
		int y = screenY(r.y);
		double mag = getMagnification();
		int width = (int)(r.width*mag);
		int height = (int)(r.height*mag);
		int size = width>40 && height>40?12:9;
		if (font!=null) {
			g.setFont(font);
			size = font.getSize();
		} else if (size==12)
			g.setFont(largeFont);
		else
			g.setFont(smallFont);
		boolean drawingList = index >= LIST_OFFSET;
		if (drawingList) index -= LIST_OFFSET;
		String label = "" + (index+1);
		if (drawNames && roi.getName()!=null)
			label = roi.getName();
		FontMetrics metrics = g.getFontMetrics();
		int w = metrics.stringWidth(label);
		x = x + width/2 - w/2;
		y = y + height/2 + Math.max(size/2,6);
		int h = metrics.getAscent() + metrics.getDescent();
		if (bgColor!=null) {
			g.setColor(bgColor);
			g.fillRoundRect(x-1, y-h+2, w+1, h-3, 5, 5);
		}
		if (!drawingList && getLabelShapes()!=null && index<getLabelShapes().size()) {
			labelShapes.put(roi, new ShapeRoi(new Rectangle(x-1, y-h+2, w+1, h)));
			labelShapes.get(index).setName(roi.getName());
		}
		g.setColor(labelColor);
		g.drawString(label, x, y-2);
		g.setColor(getDefaultColor());
	} 

	void drawZoomIndicator(Graphics g) {
		int x1 = 10;
		int y1 = 10;
		double aspectRatio = (double)imageHeight/imageWidth;
		int w1 = 64;
		if (aspectRatio>1.0)
			w1 = (int)(w1/aspectRatio);
		int h1 = (int)(w1*aspectRatio);
		if (w1<4) w1 = 4;
		if (h1<4) h1 = 4;
		int w2 = (int)(w1*((double)srcRect.width/imageWidth));
		int h2 = (int)(h1*((double)srcRect.height/imageHeight));
		if (w2<1) w2 = 1;
		if (h2<1) h2 = 1;
		int x2 = (int)(w1*((double)srcRect.x/imageWidth));
		int y2 = (int)(h1*((double)srcRect.y/imageHeight));
		if (zoomIndicatorColor==null)
			zoomIndicatorColor = new Color(128, 128, 255);
		g.setColor(zoomIndicatorColor);
		((Graphics2D)g).setStroke(Roi.onePixelWide);
		g.drawRect(x1, y1, w1, h1);
		if (w2*h2<=200 || w2<10 || h2<10)
			g.fillRect(x1+x2, y1+y2, w2, h2);
		else
			g.drawRect(x1+x2, y1+y2, w2, h2);
	}

	// Use double buffer to reduce flicker when drawing complex ROIs.
	// Author: Erik Meijering
	public void paintDoubleBuffered(Graphics g) {
		ImageWindow win = imp.getWindow();
		final int srcRectWidthMag = (int)(srcRect.width*magnification);
		final int srcRectHeightMag = (int)(srcRect.height*magnification);
		if (offScreenImage==null || offScreenWidth!=srcRectWidthMag || offScreenHeight!=srcRectHeightMag) {
			offScreenImage = createImage(srcRectWidthMag, srcRectHeightMag);
			offScreenWidth = srcRectWidthMag;
			offScreenHeight = srcRectHeightMag;
		}
		Roi roi = imp.getRoi();
		try {
			if (imageUpdated) {
				imageUpdated = false;
				imp.updateImage();
			}
			if (offScreenImage!=null){
				Graphics offScreenGraphics = offScreenImage.getGraphics();
				setInterpolation(offScreenGraphics, Prefs.interpolateScaledImages);
				Image img = imp.getImage();
				if (img!=null)
					offScreenGraphics.drawImage(img, 0, 0, srcRectWidthMag, srcRectHeightMag,
							srcRect.x, srcRect.y, srcRect.x+srcRect.width, srcRect.y+srcRect.height, null);
				if (overlay!=null) drawOverlay(offScreenGraphics);
				if (showAllROIs) 
					drawAllROIs(offScreenGraphics);
				else {
					setSliceRois(null);
					String rbnString = ""+(imp.getChannel()>0?imp.getChannel():1)+"_"+(imp.getSlice())+"_"+imp.getFrame();
					String rbnCh0String = ""+0+"_"+imp.getSlice()+"_"+imp.getFrame();

					sliceRois = new ArrayList<Roi>();
					if(imp.getRoiManager().getROIsByNumbers().get(rbnString)!=null) {
						labelShapes.clear();
						sliceRois.addAll(imp.getRoiManager().getROIsByNumbers().get(rbnString));
					}
					if(imp.getRoiManager().getROIsByNumbers().get(rbnCh0String)!=null) {
						labelShapes.clear();
						sliceRois.addAll(imp.getRoiManager().getROIsByNumbers().get(rbnCh0String));
					}
				}
				if (roi!=null) drawRoi(roi, offScreenGraphics);
				if (srcRect.width<imageWidth ||srcRect.height<imageHeight)
					drawZoomIndicator(offScreenGraphics);
				if (IJ.debugMode) showFrameRate(offScreenGraphics);
				if (messageRois != null) {
					Enumeration<Roi> mRoiEnum = messageRois.elements();
					ArrayList<Roi> r = new ArrayList<Roi>();
					while (mRoiEnum.hasMoreElements()){
						r.add(mRoiEnum.nextElement());
					}
					int yOffset =0;
					for (int i=0; i< r.size(); i++) {
						r.get(i).setLocation(srcRect.x, srcRect.y+yOffset);
						drawRoi(offScreenGraphics, (Roi)r.get(i), -1);
						yOffset = yOffset + r.get(i).height;
					}
				}
				if (getCursorRoi() != null) {
					drawRoi(offScreenGraphics, getCursorRoi(), -1);
				}
			}
			if (g!=null){
// COMMENTING OUT NEXT LINE WAS ESSENTIAL IN ELIMINATING MOUSE-MOVED SCREEN FLICKER FOR MQTVS WINDOWS.
//  WAS IT EVER NEEDED??  WHY NO EFFECT IN OTHER WINDOWS?  WEIRD!!!  12242020
//				g.clearRect(0,0,this.getWidth(),this.getHeight());
				g.drawImage(offScreenImage, 0, 0, null);
			}
		}
		catch(OutOfMemoryError e) {IJ.outOfMemory("Paint");}
	}

	public void resetDoubleBuffer() {
		offScreenImage = null;
	}

	long firstFrame;
	int frames, fps;

	void showFrameRate(Graphics g) {
		frames++;
		if (System.currentTimeMillis()>firstFrame+1000) {
			firstFrame=System.currentTimeMillis();
			fps = frames;
			frames=0;
		}
		g.setColor(Color.white);
		g.fillRect(10, 12, 50, 15);
		g.setColor(Color.black);
		g.drawString((int)(fps+0.5) + " fps", 10, 25);
	}

	public Dimension getPreferredSize() {
		return new Dimension(dstWidth, dstHeight);
	}

	int count;
	private long mouseDownTime;
	private String[] popupInfo = {"",""};

	/*
    public Graphics getGraphics() {
     	Graphics g = super.getGraphics();
		IJ.write("getGraphics: "+count++);
		if (IJ.altKeyDown())
			throw new IllegalArgumentException("");
    	return g;
    }
	 */

	/** Returns the current cursor location in image coordinates. */
	public Point getCursorLoc() {
		return new Point(getXMouse(), getYMouse());
	}

	/** Returns 'true' if the cursor is over this image. */
	public boolean cursorOverImage() {
		return !mouseExited;
	}

	/** Returns the mouse event modifiers. */
	public int getModifiers() {
		return flags;
	}

	/** Returns the ImagePlus object that is associated with this ImageCanvas. */
	public ImagePlus getImage() {
		return imp;
	}

	public void setImage(ImagePlus imp) {
		this.imp = imp;
	}

	/** Sets the cursor based on the current tool and cursor location. */
	public void setCursor(int sx, int sy, int ox, int oy) {
		setXMouse(ox);
		setYMouse(oy);
		mouseExited = false;
		Roi roi = imp.getRoi();
		ImageWindow win = imp.getWindow();
		if (win==null)
			return;
		if (IJ.spaceBarDown() && (roi==null || !(roi.contains(ox,oy)) )) {
			setCursor(handCursor);
			return;
		}
		int id = Toolbar.getToolId();
		switch (Toolbar.getToolId()) {
		case Toolbar.MAGNIFIER:
			setCursor(moveCursor);
			break;
		case Toolbar.HAND:
			setCursor(handCursor);
			break;
		default:  //selection tool
			PlugInTool tool = Toolbar.getPlugInTool();
			boolean arrowTool = roi!=null && (roi instanceof Arrow) && tool!=null && "Arrow Tool".equals(tool.getToolName());
			if ((id==Toolbar.SPARE1 || id>=Toolbar.SPARE2) && !arrowTool) {
				if (Prefs.usePointerCursor)
					setCursor(defaultCursor);
				else
					setCursor(crosshairCursor);
			} else if (roi!=null && roi.getState()!=roi.CONSTRUCTING && roi.isHandle(sx, sy)>=0)
				setCursor(handCursor);
			else if (Prefs.usePointerCursor || (roi!=null && roi.getState()!=roi.CONSTRUCTING && roi.contains(ox, oy)))
				setCursor(defaultCursor);
			else
				setCursor(crosshairCursor);
		}
	}

	/**Converts a screen x-coordinate to an offscreen x-coordinate.*/
	public int offScreenX(int sx) {
		return srcRect.x + (int)(sx/magnification);
	}

	/**Converts a screen y-coordinate to an offscreen y-coordinate.*/
	public int offScreenY(int sy) {
		return srcRect.y + (int)(sy/magnification);
	}

	/**Converts a screen x-coordinate to a floating-point offscreen x-coordinate.*/
	public double offScreenXD(int sx) {
		return srcRect.x + sx/magnification;
	}

	/**Converts a screen y-coordinate to a floating-point offscreen y-coordinate.*/
	public double offScreenYD(int sy) {
		return srcRect.y + sy/magnification;

	}

	/**Converts an offscreen x-coordinate to a screen x-coordinate.*/
	public int screenX(int ox) {
		return  (int)((ox-srcRect.x)*magnification);
	}

	/**Converts an offscreen y-coordinate to a screen y-coordinate.*/
	public int screenY(int oy) {
		return  (int)((oy-srcRect.y)*magnification);
	}

	/**Converts a floating-point offscreen x-coordinate to a screen x-coordinate.*/
	public int screenXD(double ox) {
		return  (int)((ox-srcRect.x)*magnification);
	}

	/**Converts a floating-point offscreen x-coordinate to a screen x-coordinate.*/
	public int screenYD(double oy) {
		return  (int)((oy-srcRect.y)*magnification);
	}

	public double getMagnification() {
		return magnification;
	}

	public void setMagnification(double magnification) {
		setMagnification2(magnification);
	}

	void setMagnification2(double magnification) {
		if (magnification>32.0) magnification = 32.0;
		if (magnification<0.03125) magnification = 0.03125;
		this.magnification = magnification;
		imp.setTitle(imp.getTitle());
	}

	/** Enlarge the canvas if the user enlarges the window. */
	void resizeCanvas(int width, int height) {
		ImageWindow win = imp.getWindow();
		//IJ.log("resizeCanvas: "+srcRect+" "+imageWidth+"  "+imageHeight+" "+width+"  "+height+" "+dstWidth+"  "+dstHeight+" "+win.maxBounds);
		if (!maxBoundsReset&& (width>dstWidth||height>dstHeight)&&win!=null&&win.maxBounds!=null&&width!=win.maxBounds.width-10) {
			if (resetMaxBoundsCount!=0)
				resetMaxBounds(); // Works around problem that prevented window from being larger than maximized size
			resetMaxBoundsCount++;
		}
		if (IJ.altKeyDown())
		{fitToWindow(); return;}
		if (srcRect.width<imageWidth || srcRect.height<imageHeight) {
			if (width>imageWidth*magnification)
				width = (int)(imageWidth*magnification);
			if (height>imageHeight*magnification)
				height = (int)(imageHeight*magnification);
			setDrawingSize(width, height);
			srcRect.width = (int)(dstWidth/magnification);
			srcRect.height = (int)(dstHeight/magnification);
			if ((srcRect.x+srcRect.width)>imageWidth)
				srcRect.x = imageWidth-srcRect.width;
			if ((srcRect.y+srcRect.height)>imageHeight)
				srcRect.y = imageHeight-srcRect.height;
			paintDoubleBuffered(getGraphics());
		}
		//IJ.log("resizeCanvas2: "+srcRect+" "+dstWidth+"  "+dstHeight+" "+width+"  "+height);
	}

	public void fitToWindow() {
		ImageWindow win = imp.getWindow();
		if (win==null) return;
		Rectangle bounds = win.getBounds();
		Insets insets = win.getInsets();
		int sliderHeight = (win instanceof StackWindow)?20:0;
		double xmag = (double)(bounds.width-10)/srcRect.width;
		double ymag = (double)(bounds.height-(10+insets.top+sliderHeight))/srcRect.height;
		setMagnification(Math.min(xmag, ymag));
		int width=(int)(imageWidth*magnification);
		int height=(int)(imageHeight*magnification);
		if (width==dstWidth&&height==dstHeight) return;
		srcRect=new Rectangle(0,0,imageWidth, imageHeight);
		setDrawingSize(width, height);
		getParent().doLayout();
	}

	void setMaxBounds() {
		if (maxBoundsReset) {
			maxBoundsReset = false;
			ImageWindow win = imp.getWindow();
			if (win!=null && !IJ.isLinux() && win.maxBounds!=null) {
				win.setMaximizedBounds(win.maxBounds);
				win.setMaxBoundsTime = System.currentTimeMillis();
			}
		}
	}

	void resetMaxBounds() {
		ImageWindow win = imp.getWindow();
		if (win!=null && (System.currentTimeMillis()-win.setMaxBoundsTime)>500L) {
			win.setMaximizedBounds(win.maxWindowBounds);
			maxBoundsReset = true;
		}
	}

	private static final double[] zoomLevels = {
		1/72.0, 1/48.0, 1/32.0, 1/24.0, 1/16.0, 1/12.0, 
		1/8.0, 1/6.0, 1/4.0, 1/3.0, 1/2.0, 0.75, 1.0, 1.5,
		2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0 };
	private Roi cursorRoi;
	public Roi getCursorRoi() {
		return cursorRoi;
	}

	public void setCursorRoi(Roi cursorRoi) {
		this.cursorRoi = cursorRoi;
	}

	private int rotation;
	private Point previousP;
	private long previousT;

	public static double getLowerZoomLevel(double currentMag) {
		double newMag = zoomLevels[0];
		for (int i=0; i<zoomLevels.length; i++) {
			if (zoomLevels[i] < currentMag)
				newMag = zoomLevels[i];
			else
				break;
		}
		return newMag;
	}

	public static double getHigherZoomLevel(double currentMag) {
		double newMag = 32.0;
		for (int i=zoomLevels.length-1; i>=0; i--) {
			if (zoomLevels[i]>currentMag)
				newMag = zoomLevels[i];
			else
				break;
		}
		return newMag;
	}

	/** Zooms in by making the window bigger. If it can't
		be made bigger, then make the source rectangle 
		(srcRect) smaller and center it at (sx,sy). Note that
		sx and sy are screen coordinates. */
	public void zoomIn(int sx, int sy) {
		if (magnification>=32) return;
		double newMag = getHigherZoomLevel(magnification);
		int newWidth = (int)(imageWidth*newMag);
		int newHeight = (int)(imageHeight*newMag);
		int padH = 1+imp.getWindow().getInsets().left
				+imp.getWindow().getInsets().right
				+imp.getWindow().viewButtonPanel.getWidth()
				+(imp.getWindow().optionsPanel.isVisible()?imp.getWindow().optionsPanel.getWidth():0);
		int padV = 1+imp.getWindow().getInsets().top
				+imp.getWindow().getInsets().bottom
				+(imp.getWindow() instanceof StackWindow?
						((StackWindow)imp.getWindow()).getNScrollbars()
						*(((StackWindow)imp.getWindow()).zSelector!=null?
								((StackWindow)imp.getWindow()).zSelector.getHeight():
									((StackWindow)imp.getWindow()).tSelector!=null?
											((StackWindow)imp.getWindow()).tSelector.getHeight():
												((StackWindow)imp.getWindow()).cSelector.getHeight())
						:0)
						+imp.getWindow().overheadPanel.getHeight();
		
//		newWidth = imageWidth;
//		newHeight = imageHeight;
//		
		Dimension newSize = canEnlarge(newWidth, newHeight);
		if (sx > newWidth)
			sx= (int)(imageWidth*magnification);
		if (sy>newHeight)
			sy= (int)(imageHeight*magnification);
		if (newSize!=null) {
			setDrawingSize(newSize.width, newSize.height);
			if (newSize.width!=newWidth || newSize.height!=newHeight)
				adjustSourceRect(newMag, sx, sy);
			else {
				setMagnification(newMag);
				adjustSourceRect(newMag, sx, sy);
			}
//			imp.getWindow().pack();
			imp.getWindow().setSize(dstWidth+padH, dstHeight+padV);
		} else {
			adjustSourceRect(newMag, sx, sy);
//			imp.getWindow().pack();
			imp.getWindow().setSize(dstWidth+padH, dstHeight+padV);
		}
		if (srcRect.width<imageWidth || srcRect.height<imageHeight)
			resetMaxBounds();
		paintDoubleBuffered(getGraphics());
	}

	public void adjustSourceRect(double newMag, int x, int y) {
		//IJ.log("adjustSourceRect1: "+newMag+" "+dstWidth+"  "+dstHeight);
		int w = (int)Math.round(dstWidth/newMag);
		if (w*newMag<dstWidth) 
			w++;
		int h = (int)Math.round(dstHeight/newMag);
		if (h*newMag<dstHeight) 
			h++;
		x = offScreenX(x);
		y = offScreenY(y);
		Rectangle r = new Rectangle(x-w/2, y-h/2, w, h);
		if (r.x<0) 
			r.x = 0;
		if (r.y<0) 
			r.y = 0;
		if (r.x+w>imageWidth) 
			r.x = imageWidth-w;
		if (r.y+h>imageHeight) 
			r.y = imageHeight-h;
		srcRect = r;
		setMagnification(newMag);
		//IJ.log("adjustSourceRect2: "+srcRect+" "+dstWidth+"  "+dstHeight);
	}

	protected Dimension canEnlarge(int newWidth, int newHeight) {
		//if ((flags&Event.CTRL_MASK)!=0 || IJ.controlKeyDown()) return null;
		ImageWindow win = imp.getWindow();
		if (win==null) return null;
		Rectangle r1 = win.getBounds();
		Insets insets = win.getInsets();
		Point loc = getLocation();
		if (loc.x>insets.left+5 || loc.y>insets.top+5) {
			r1.width = newWidth+insets.left+insets.right+10;
			r1.height = newHeight+insets.top+insets.bottom+10;
			if (win instanceof StackWindow) r1.height+=20;
		} else {
			r1.width = r1.width - dstWidth + newWidth+10;
			r1.height = r1.height - dstHeight + newHeight+10;
		}
		Rectangle max = win.getMaxWindow(r1.x, r1.y);
		boolean fitsHorizontally = r1.x+r1.width<max.x+max.width;
		boolean fitsVertically = r1.y+r1.height<max.y+max.height;
		if (fitsHorizontally && fitsVertically)
			return new Dimension(newWidth, newHeight);
		else if (fitsVertically && !fitsHorizontally)
			return new Dimension(this.getWidth(), newHeight);
		else if (fitsHorizontally && !fitsVertically)
			return new Dimension(newWidth, this.getHeight());
		else if (!fitsHorizontally && !fitsVertically)
			return new Dimension(this.getWidth(),this.getHeight());
		else
			return null;
	}

	/**Zooms out by making the source rectangle (srcRect)  
		larger and centering it on (x,y). If we can't make it larger,  
		then make the window smaller.*/
	public void zoomOut(int x, int y) {
		if (magnification<=0.03125)
			return;
		double oldMag = magnification;
		double newMag = getLowerZoomLevel(magnification);
		double srcRatio = (double)srcRect.width/srcRect.height;
		double imageRatio = (double)imageWidth/imageHeight;
		double initialMag = imp.getWindow().getInitialMagnification();
		int padH = 1+imp.getWindow().getInsets().left
				+imp.getWindow().getInsets().right
				+imp.getWindow().viewButtonPanel.getWidth()
				+(imp.getWindow().optionsPanel.isVisible()?imp.getWindow().optionsPanel.getWidth():0);
		int padV = 1+imp.getWindow().getInsets().top
				+imp.getWindow().getInsets().bottom
				+(imp.getWindow() instanceof StackWindow?
						((StackWindow)imp.getWindow()).getNScrollbars()
						*(((StackWindow)imp.getWindow()).zSelector!=null?
								((StackWindow)imp.getWindow()).zSelector.getHeight():
									((StackWindow)imp.getWindow()).tSelector!=null?
											((StackWindow)imp.getWindow()).tSelector.getHeight():
												((StackWindow)imp.getWindow()).cSelector.getHeight())
						:0)
						+(imp.getWindow().overheadPanel!=null?imp.getWindow().overheadPanel.getHeight():0);
		if (Math.abs(srcRatio-imageRatio)>0.05) {
			double scale = oldMag/newMag;
			int newSrcWidth = (int)Math.round(srcRect.width*scale);
			int newSrcHeight = (int)Math.round(srcRect.height*scale);
			if (newSrcWidth>imageWidth) newSrcWidth=imageWidth;
			if (newSrcHeight>imageHeight) newSrcHeight=imageHeight;
			int newSrcX = srcRect.x - (newSrcWidth - srcRect.width)/2;
			if (newSrcX+newSrcWidth>imageWidth)
				newSrcX = imageWidth- newSrcWidth;
			int newSrcY = srcRect.y - (newSrcHeight - srcRect.height)/2;
			if (newSrcY+newSrcHeight>imageHeight)
				newSrcY = imageHeight- newSrcHeight;
			if (newSrcX<0) newSrcX = 0;
			if (newSrcY<0) newSrcY = 0;
			srcRect = new Rectangle(newSrcX, newSrcY, newSrcWidth, newSrcHeight);
			//IJ.log(newMag+" "+srcRect+" "+dstWidth+" "+dstHeight);
			int newDstWidth = (int)(srcRect.width*newMag);
			int newDstHeight = (int)(srcRect.height*newMag);
			setMagnification(newMag);
			setMaxBounds();
			//IJ.log(newDstWidth+" "+dstWidth+" "+newDstHeight+" "+dstHeight);
			if (newDstWidth<dstWidth || newDstHeight<dstHeight) {
				//IJ.log("pack");
				setDrawingSize(newDstWidth, newDstHeight);
//				imp.getWindow().pack();
				imp.getWindow().setSize(newDstWidth+padH, newDstHeight+padV);
				paintDoubleBuffered(getGraphics());
			} else {
				setDrawingSize(newDstWidth, newDstHeight);
//				imp.getWindow().pack();
				imp.getWindow().setSize(newDstWidth+padH, newDstHeight+padV);
				paintDoubleBuffered(getGraphics());
			}
			return;
		}
		if (imageWidth*newMag>dstWidth) {
			int w = (int)Math.round(dstWidth/newMag);
			if (w*newMag<dstWidth) w++;
			int h = (int)Math.round(dstHeight/newMag);
			if (h*newMag<dstHeight) h++;
			x = offScreenX(x);
			y = offScreenY(y);
			Rectangle r = new Rectangle(x-w/2, y-h/2, w, h);
			if (r.x<0) r.x = 0;
			if (r.y<0) r.y = 0;
			if (r.x+w>imageWidth) r.x = imageWidth-w;
			if (r.y+h>imageHeight) r.y = imageHeight-h;
			srcRect = r;
		} else {
			srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
			setDrawingSize((int)(imageWidth*newMag), (int)(imageHeight*newMag));
			//setDrawingSize(dstWidth/2, dstHeight/2);
			imp.getWindow().pack();
			imp.getWindow().setSize(dstWidth+padH, dstHeight+padV);
		}
		//IJ.write(newMag + " " + srcRect.x+" "+srcRect.y+" "+srcRect.width+" "+srcRect.height+" "+dstWidth + " " + dstHeight);
		setMagnification(newMag);
		//IJ.write(srcRect.x + " " + srcRect.width + " " + dstWidth);
		setMaxBounds();
		paintDoubleBuffered(getGraphics());
	}

	/** Implements the Image/Zoom/Original Scale command. */
	public void unzoom() {
		double imag = imp.getWindow().getInitialMagnification();
		int padH = 1+imp.getWindow().getInsets().left
				+imp.getWindow().getInsets().right
				+imp.getWindow().viewButtonPanel.getWidth()
				+(imp.getWindow().optionsPanel.isVisible()?imp.getWindow().optionsPanel.getWidth():0);
		int padV = 1+imp.getWindow().getInsets().top
				+imp.getWindow().getInsets().bottom
				+(imp.getWindow() instanceof StackWindow?
						((StackWindow)imp.getWindow()).getNScrollbars()
						*(((StackWindow)imp.getWindow()).zSelector!=null?
								((StackWindow)imp.getWindow()).zSelector.getHeight():
									((StackWindow)imp.getWindow()).tSelector!=null?
											((StackWindow)imp.getWindow()).tSelector.getHeight():
												((StackWindow)imp.getWindow()).cSelector.getHeight())
						:0)
						+imp.getWindow().overheadPanel.getHeight();
		if (magnification==imag)
			return;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		ImageWindow win = imp.getWindow();
		setDrawingSize((int)(imageWidth*imag), (int)(imageHeight*imag));
		setMagnification(imag);
		setMaxBounds();
		win.pack();
		imp.getWindow().setSize(dstWidth+padH, dstHeight+padV);
		setMaxBounds();
		paintDoubleBuffered(getGraphics());
	}

	/** Implements the Image/Zoom/View 100% command. */
	public void zoom100Percent() {
		int padH = 1+imp.getWindow().getInsets().left
				+imp.getWindow().getInsets().right
				+imp.getWindow().viewButtonPanel.getWidth()
				+(imp.getWindow().optionsPanel.isVisible()?imp.getWindow().optionsPanel.getWidth():0);
		int padV = 1+imp.getWindow().getInsets().top
				+imp.getWindow().getInsets().bottom
				+(imp.getWindow() instanceof StackWindow?
						((StackWindow)imp.getWindow()).getNScrollbars()
						*(((StackWindow)imp.getWindow()).zSelector!=null?
								((StackWindow)imp.getWindow()).zSelector.getHeight():
									((StackWindow)imp.getWindow()).tSelector!=null?
											((StackWindow)imp.getWindow()).tSelector.getHeight():
												((StackWindow)imp.getWindow()).cSelector.getHeight())
						:0)
						+imp.getWindow().overheadPanel.getHeight();
		if (magnification==1.0)
			return;
		double imag = imp.getWindow().getInitialMagnification();
		if (magnification!=imag)
			unzoom();
		if (magnification==1.0)
			return;
		if (magnification<1.0) {
			while (magnification<1.0)
				zoomIn(imageWidth/2, imageHeight/2);
		} else if (magnification>1.0) {
			while (magnification>1.0)
				zoomOut(imageWidth/2, imageHeight/2);
		} else
			return;
		int x=getXMouse(), y=getYMouse();
		if (mouseExited) {
			x = imageWidth/2;
			y = imageHeight/2;
		}
		int sx = screenX(x);
		int sy = screenY(y);
		adjustSourceRect(1.0, sx, sy);
		imp.getWindow().pack();
		imp.getWindow().setSize(dstWidth+padH, dstHeight+padV);
		paintDoubleBuffered(getGraphics());
	}

	protected void scroll(int sx, int sy) {
		int ox = xSrcStart + (int)(sx/magnification);  //convert to offscreen coordinates
		int oy = ySrcStart + (int)(sy/magnification);
		//IJ.log("scroll: "+ox+" "+oy+" "+xMouseStart+" "+yMouseStart);
		int newx = xSrcStart + (xMouseStart-ox);
		int newy = ySrcStart + (yMouseStart-oy);
		if (newx<0) newx = 0;
		if (newy<0) newy = 0;
		if ((newx+srcRect.width)>imageWidth) newx = imageWidth-srcRect.width;
		if ((newy+srcRect.height)>imageHeight) newy = imageHeight-srcRect.height;
		srcRect.x = newx;
		srcRect.y = newy;
		//IJ.log(sx+"  "+sy+"  "+newx+"  "+newy+"  "+srcRect);
		imp.draw();
		Thread.yield();
	}	

	Color getColor(int index){
		IndexColorModel cm = (IndexColorModel)imp.getProcessor().getColorModel();
		//IJ.write(""+index+" "+(new Color(cm.getRGB(index))));
		return new Color(cm.getRGB(index));
	}

	/** Sets the foreground drawing color (or background color if 
	'setBackground' is true) to the color of the pixel at (ox,oy). */
	public void setDrawingColor(int ox, int oy, boolean setBackground) {
		//IJ.log("setDrawingColor: "+setBackground+this);
		int type = imp.getType();
		int[] v = imp.getPixel(ox, oy);
		switch (type) {
		case ImagePlus.GRAY8: {
			if (setBackground)
				setBackgroundColor(getColor(v[0]));
			else
				setForegroundColor(getColor(v[0]));
			break;
		}
		case ImagePlus.GRAY16: case ImagePlus.GRAY32: {
			double min = imp.getProcessor().getMin();
			double max = imp.getProcessor().getMax();
			double value = (type==ImagePlus.GRAY32)?Float.intBitsToFloat(v[0]):v[0];
			int index = (int)(255.0*((value-min)/(max-min)));
			if (index<0) index = 0;
			if (index>255) index = 255;
			if (setBackground)
				setBackgroundColor(getColor(index));
			else
				setForegroundColor(getColor(index));
			break;
		}
		case ImagePlus.COLOR_RGB: case ImagePlus.COLOR_256: {
			Color c = new Color(v[0], v[1], v[2]);
			if (setBackground)
				setBackgroundColor(c);
			else
				setForegroundColor(c);
			break;
		}
		}
		Color c;
		if (setBackground)
			c = Toolbar.getBackgroundColor();
		else {
			c = Toolbar.getForegroundColor();
			imp.setColor(c);
		}
		IJ.showStatus("("+c.getRed()+", "+c.getGreen()+", "+c.getBlue()+")");
	}

	private void setForegroundColor(Color c) {
		Toolbar.setForegroundColor(c);
		if (Recorder.record)
			Recorder.record("setForegroundColor", c.getRed(), c.getGreen(), c.getBlue());
	}

	private void setBackgroundColor(Color c) {
		Toolbar.setBackgroundColor(c);
		if (Recorder.record)
			Recorder.record("setBackgroundColor", c.getRed(), c.getGreen(), c.getBlue());
	}

	public void mousePressed(MouseEvent e) {
		//if (ij==null) return;
		showCursorStatus = true;
		int toolID = Toolbar.getToolId();
		int toolAlt = Toolbar.getOvalToolType();
		ImageWindow win = imp.getWindow();

		if (e.getButton() == MouseEvent.BUTTON3 && toolID==Toolbar.OVAL && toolAlt==Toolbar.BRUSH_ROI) {
			if (imp.getRoi() !=null) {
				imp.getRoiManager().add(imp.getRoi(), false, false, false, true);
				imp.setPosition(imp.getChannel(),imp.getSlice()+1, imp.getFrame());
			}
			e.consume();
			return;
		}
		
		if (win!=null && (win.running2 || win.running3) && toolID!=Toolbar.MAGNIFIER) {
			if (win instanceof StackWindow) {
				((StackWindow)win).setAnimate(false);
				((StackWindow)win).setZAnimate(false);
			} else
				win.running2 = win.running3 = false;
			return;
		}

		int x = e.getX();
		int y = e.getY();
		flags = e.getModifiers();
		//IJ.log("Mouse pressed: " + e.isPopupTrigger() + "  " + ij.modifiers(flags));		
		//if (toolID!=Toolbar.MAGNIFIER && e.isPopupTrigger()) {
		if (toolID!=Toolbar.MAGNIFIER && (e.isPopupTrigger()||(!IJ.isMacintosh()&&(flags&Event.META_MASK)!=0))) {
			mousePressedX = ((int)(this.getXMouse()*this.getMagnification()));
			mousePressedY = ((int)(this.getYMouse()*this.getMagnification()));
			handlePopupMenu(e);
			return;
		}

		int ox = offScreenX(x);
		int oy = offScreenY(y);
		setXMouse(ox); setYMouse(oy);
		previousP = new Point(ox, oy);
		if (IJ.spaceBarDown() ) {
			// temporarily switch to "hand" tool of space bar down
			if (toolID==Toolbar.OVAL || toolAlt==Toolbar.BRUSH_ROI) {

				if (imp.getRoi()!=null) {
					if (imp.getRoi().contains(ox, oy)) {
						toolID = Toolbar.RECTANGLE;
					} else {
						setupScroll(ox, oy);
						return;
					}
				}else {
					setupScroll(ox, oy);
					return;
				}
			} else {
				setupScroll(ox, oy);
				return;
			}
		}


		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null) {
			tool.mousePressed(imp, e);
			if (e.isConsumed()) return;
		}
		boolean doubleClick =  (System.currentTimeMillis()-mouseDownTime)<=400;
		mouseDownTime = System.currentTimeMillis();
		//		if (cursorRoi != null) {
		if (this.getCursor().getType() == Cursor.CUSTOM_CURSOR) {
			if (doubleClick) {
				Roi roi = null;
				if (imp.getRoiManager().getSelectedRoisAsArray().length >0)
					roi = imp.getRoiManager().getSelectedRoisAsArray()[0];

				if (roi != null) 
					currentRoi = roi;
				//			if (doubleClick && roi instanceof TextRoi) 
				//				((TextRoi)roi).searchWormbase();
				if (roi instanceof Roi) {
					//				IJ.setKeyDown(KeyEvent.VK_ALT);
					//				IJ.setKeyDown(KeyEvent.VK_SHIFT);

					TextRoi fakeTR = new TextRoi(0,0,imp.getRoiManager().getSelectedRoisAsArray()[0]
							.getName().split("[\"|=]")[1].trim());
//					fakeTR.searchWormbase();
					
					//				IJ.setKeyUp(KeyEvent.VK_ALT);
					//				IJ.setKeyUp(KeyEvent.VK_SHIFT);

				}
			}
		}
		Roi impRoi = imp.getRoi();
		if (!(impRoi!=null && (impRoi.contains(ox, oy)||impRoi.isHandle(x, y)>=0)) && roiManagerSelect(ox, oy))
			return;
		if (customRoi && overlay!=null)
			return;

		switch (toolID) {
		case Toolbar.MAGNIFIER:
			if (IJ.shiftKeyDown())
				zoomToSelection(ox, oy);
			else if ((flags & (Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0) {
				//IJ.run("Out");
				zoomOut(x, y);
				if (getMagnification()<1.0)
					imp.repaintWindow();
			} else {
				//IJ.run("In");
				zoomIn(x, y);
				if (getMagnification()<=1.0)
					imp.repaintWindow();
			}
			break;
		case Toolbar.HAND:
			setupScroll(ox, oy);
			break;
		case Toolbar.DROPPER:
			setDrawingColor(ox, oy, IJ.altKeyDown());
			break;
		case Toolbar.WAND:
			Roi roi = imp.getRoi();
			if (roi!=null && roi.contains(ox, oy)) {
				Rectangle r = roi.getBounds();
				if (r.width==imageWidth && r.height==imageHeight)
					imp.deleteRoi();
				else if (!e.isAltDown()) {
					handleRoiMouseDown(e);
					return;
				}
			}
			if (roi!=null) {
				int handle = roi.isHandle(x, y);
				if (handle>=0) {
					roi.mouseDownInHandle(handle, x, y);
					return;
				}
			}
			setRoiModState(e, roi, -1);
			String mode = WandToolOptions.getMode();
			double tolerance = WandToolOptions.getTolerance();
			int npoints = IJ.doWand(ox, oy, tolerance, mode);
			if (Recorder.record && npoints>0) {
				if (tolerance==0.0 && mode.equals("Legacy"))
					Recorder.record("doWand", ox, oy);
				else
					Recorder.recordString("doWand("+ox+", "+oy+", "+tolerance+", \""+mode+"\");\n");
			}
			break;
		case Toolbar.OVAL:
			if (Toolbar.getBrushSize()>0) {
	 			canvasRoiBrush = new RoiBrush(imp);


			} else
				handleRoiMouseDown(e);
			break;
		case Toolbar.SPARE1: case Toolbar.SPARE2: case Toolbar.SPARE3: 
		case Toolbar.SPARE4: case Toolbar.SPARE5: case Toolbar.SPARE6:
		case Toolbar.SPARE7: case Toolbar.SPARE8: case Toolbar.SPARE9:
			if (tool!=null && "Arrow Tool".equals(tool.getToolName()))
				handleRoiMouseDown(e);
			else
				Toolbar.getInstance().runMacroTool(toolID);
			break;
		default:  //selection tool
			handleRoiMouseDown(e);
		}
	}

	boolean roiManagerSelect(int x, int y) {
		RoiManager rm = imp.getRoiManager();
		if (rm==null) return false;
		Hashtable rois = rm.getROIs();
		DefaultListModel listModel = rm.getListModel();
		if (labelShapes==null) {
			return false;
		}
		if (sliceRois == null)
			return false;
		if (sliceRois.size() > labelShapes.size()) {
			Roi[] sliceRoisArray = new Roi[sliceRois.size()];
			for (int sr=0;sr<sliceRoisArray.length;sr++) {
				sliceRoisArray[sr] = ((Roi)sliceRois.get(sr));
				Roi slcRoi = sliceRoisArray[sr];
				if (slcRoi instanceof ShapeRoi) {
					labelShapes.put(slcRoi, (ShapeRoi)slcRoi);
				}else{
					labelShapes.put(slcRoi,(slcRoi instanceof Arrow?((Arrow)slcRoi).getShapeRoi():new ShapeRoi(slcRoi)));
					if (labelShapes!=null && slcRoi!= null)
						if (labelShapes.get(slcRoi) != null) {
							labelShapes.get(slcRoi).setName(slcRoi.getName());
						}
				}

			}

		}
		Roi[] sliceRoisArray = new Roi[sliceRois.size()];
		for (int sr=0;sr<sliceRoisArray.length;sr++)
			sliceRoisArray[sr] = ((Roi)sliceRois.get(sr));
		for (int i=0; i<sliceRoisArray.length; i++) {  
			if (sliceRoisArray[i] instanceof Arrow
					&& getLabelShapes().get(sliceRoisArray[i]) != null
					&& getLabelShapes().get(sliceRoisArray[i]).contains(x, y)) {
				
				int q = listModel.indexOf(sliceRoisArray[i].getName());
//				new ij.macro.MacroRunner("roiManager('select', "+q+", "+imp.getID()+");");
				if (q <0)
					return false;
				rm.select(imp, q);
				rm.validate();
				rm.repaint();
				return true;
			}
			
			
			if (getLabelShapes().get(sliceRoisArray[i]) !=null 
					&& getLabelShapes().get(sliceRoisArray[i]).contains(x, y)
					/*&& ( getLabelShapes()[i].getFillColor()!=null
						|| getLabelShapes()[i].getStrokeColor()!=null
						|| rois.get(getLabelShapes()[i].getName()) instanceof TextRoi )*/ ) {
				//rm.select(i);
				// this needs to run on a separate thread, at least on OS X
				// "update2" does not clone the ROI so the "Show All"
				// outline moves as the user moves the RO.
				String pickedRoiName = sliceRoisArray[i].getName();
				String pickedCellName = pickedRoiName.split(" ")[0].replace("\"", "");
				int q = listModel.indexOf(pickedRoiName);
//				new ij.macro.MacroRunner("roiManager('select', "+q+", "+imp.getID()+");");
				
				if (q <0)
					return false;
				if (imp.getRoiManager().aceTree != null) {
					int t = imp.getT();
					double tCal = 1; // imp.getCalibration().frameInterval;
					double ysc = imp.getRoiManager().aceTree.iSulstonTree.iTreePanel.c.ysc;
					int[] cellXY = imp.getRoiManager().aceTree.iSulstonTree.iTreePanel.cellTreeLocation(pickedCellName, (int) (t/tCal));
					Graphics g = imp.getRoiManager().aceTree.iSulstonTree.iTreePanel.getGraphics();
//					imp.getRoiManager().aceTree.iSulstonTree.iTreePanel.repaint();
                    double ypad = 10*100/imp.getRoiManager().aceTree.iSulstonTree.getHeight();
//                    IJ.log("c.ysc= "+imp.getRoiManager().aceTree.iSulstonTree.iTreePanel.c.ysc+" ypad= "+ ypad);
                    g.setColor(Color.magenta);
//                    g.drawOval(e.getX()-10, e.getY()-10, 20, 20);
                    g.drawOval(cellXY[0]-10, (int)((((cellXY[1]) +Cell.START1 +Cell.START0)*ysc) ), 20, 20);
                   	g.setColor(Color.black);
                	g.drawString(pickedCellName, cellXY[0]+6, (int)((((cellXY[1]) +Cell.START1 +Cell.START0)*ysc) ));


				}

				rm.select(imp, q);
				rm.validate();
				rm.repaint();
				return true;
			}
		}
//		rm.select(imp, -1);
		return false;
	}

	void zoomToSelection(int x, int y) {
		IJ.setKeyUp(IJ.ALL_KEYS);
		String macro =
				"args = split(getArgument);\n"+
						"x1=parseInt(args[0]); y1=parseInt(args[1]); flags=20;\n"+
						"while (flags&20!=0) {\n"+
						"getCursorLoc(x2, y2, z, flags);\n"+
						"if (x2>=x1) x=x1; else x=x2;\n"+
						"if (y2>=y1) y=y1; else y=y2;\n"+
						"makeRectangle(x, y, abs(x2-x1), abs(y2-y1));\n"+
						"wait(10);\n"+
						"}\n"+
						"run('To Selection');\n";
		new MacroRunner(macro, x+" "+y);
	}

	protected void setupScroll(int ox, int oy) {
		xMouseStart = ox;
		yMouseStart = oy;
		xSrcStart = srcRect.x;
		ySrcStart = srcRect.y;
	}

	public void handlePopupMenu(MouseEvent e) {
		if (disablePopupMenu) return;
		//		boolean getGenes = IJ.shiftKeyDown() /*|| e.getSource() instanceof Checkbox*/;
		//		boolean getFates = IJ.altKeyDown() || IJ.controlKeyDown() /*|| e.getSource() instanceof Checkbox*/;

		Toolkit tk = Toolkit.getDefaultToolkit();
		String cursorString = "Searching! please wait...";
		if (IJ.isWindows())
			cursorString = "Wait!";
		Font font = Font.decode("Arial-Outline-18");

		//create the FontRenderContext object which helps us to measure the text
		FontRenderContext frc = new FontRenderContext(null, true, true);

		//get the height and width of the text
		Rectangle2D bounds = font.getStringBounds(cursorString, frc);
		int w = (int) bounds.getWidth();
		int ht = (int) bounds.getHeight();
		Image img = new BufferedImage(w, ht, BufferedImage.TYPE_INT_ARGB_PRE);

		//		img.getGraphics().setColor(Colors.decode("00000000", Color.white));
		Graphics2D g2d = (Graphics2D) img.getGraphics();

		g2d.setFont(font);

		g2d.setColor(Colors.decode("#66111111",Color.gray));
		g2d.fillRect(0, 0, w, ht);
		g2d.setColor(Color.YELLOW);
		g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
		g2d.drawLine(0, 0, 2, 7);
		g2d.drawLine(0, 0, 7, 2);
		g2d.drawLine(0, 0, 8, 8);
		g2d.drawString(cursorString, 1, img.getHeight(null)-1);
		Cursor searchCursor = tk.createCustomCursor(img,new Point(0,0),"searchCursor");
		if (e.getSource() instanceof Canvas)
			((Canvas)e.getSource()).setCursor(searchCursor);
		else 
			((JComponent)e.getSource()).setCursor(searchCursor);

		boolean getGenes = true /*|| e.getSource() instanceof Checkbox*/;
		boolean getFates = true /*|| e.getSource() instanceof Checkbox*/;
		boolean lineageMap = false;
		if (IJ.debugMode) IJ.log("show popup: " + (e.isPopupTrigger()?"true":"false"));
		int x = e.getX();
		int y = e.getY();
		int xOS = this.offScreenX(x);
		int yOS = this.offScreenY(y);
		Roi roi = imp.getRoi();
		if (roi!=null && (roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE || roi.getType()==Roi.ANGLE)
				&& roi.getState()==roi.CONSTRUCTING) {
			roi.handleMouseUp(x, y); // simulate double-click to finalize
			roi.handleMouseUp(x, y); // polygon or polyline selection
			return;
		}

		Menus.installPopupMenu(ij);
		popup = Menus.getPopupMenu();
		
		if (popup!=null) {
			
			popupInfo[0] = "";
			popupInfo[1] = "";

			int[] impIDs = WindowManager.getIDList();

			RoiManager rm = imp.getRoiManager();

			for (int i=0; i<impIDs.length;i++) {
				if (WindowManager.getImage(WindowManager.getIDList()[i]).getStack() instanceof MultiQTVirtualStack &&
						((MultiQTVirtualStack) WindowManager.getImage(WindowManager.getIDList()[i]).getStack()).getLineageMapImage() == imp){
					rm = WindowManager.getImage(WindowManager.getIDList()[i]).getRoiManager();
					lineageMap = true;
				}
			}
			ColorLegend colorLegend = null;
			if (imp.getMotherImp() != null) 
				colorLegend = imp.getMotherImp().getRoiManager().getColorLegend();
			JCheckBox clccb = null;
			if (colorLegend!=null){
				clccb = colorLegend.getChosenCB();
				colorLegend.setChosenCB(null);
			}

			boolean brainbowSelection = colorLegend != null &&  (e.getSource() instanceof Checkbox || clccb!= null) 
					&& (imp.getTitle().contains("Sketch3D") 
							|| sketchyMQTVS);

			boolean ij3dSelection = e.getSource() instanceof ImageCanvas3D;
			
			if (rm != null && (labelShapes != null || brainbowSelection || ij3dSelection)) {
//				Roi[] fullRoisArray = rm.getFullRoisAsArray();
				DefaultListModel listModel = rm.getListModel();
				int n;
				if (labelShapes == null)
					n=0;
				else
					n = labelShapes.size();

				String cellName = "";
				String fullTagName = "";
				String cellDescription = "";
				JComponent[] standardmis = new JComponent[popup.getComponentCount()];
				for (int k = standardmis.length - 1; k >= 0; k--) {
					standardmis[k] = (JComponent) popup.getComponent(k);
					popup.remove(k);
				}
				String clickedROIstring = "";
				IJ.log("");
				String[] logLines = IJ.getLog().split("\n");
				//if (logLines==null) logLines = new String[]{""};

				int[] targetTag = {0,0,0};
				if (this.getLabelShapes() == null) {
					setShowAllROIs(false);
					setShowAllROIs(true);
				}
				JMenuItem mi =null;
				mi =  new JMenuItem("^--------------------^");
				mi.addActionListener(ij);
				popup.add(mi);

				if (brainbowSelection){
					//					rm = colorLegend.getRoiManager();
					////					rm.getImagePlus().setPosition(rm.getImagePlus().getChannel(), rm.getImagePlus().getSlice(),imp.getSlice());
//					popup.add("brainbow selection");
					if (e.getSource() != this) {
						if ( e.getSource() instanceof Checkbox)
							cellName = ((Checkbox) e.getSource()).getName().trim();
					} else
						cellName = clccb.getName().trim();
					//					cellName = ((TextRoi)cursorRoi).getText();
					mi =  new JMenuItem("\""+cellName + " \": synch all windows to this tag" );
					mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Synch.png")));
					mi.addActionListener(ij);
					popup.add(mi);
				}
				
				if (ij3dSelection) {
					fullTagName = ((ImageWindow3D)((ImageCanvas3D)e.getSource())
							.getParent().getParent().getParent().getParent()).getUniverse().getPicker().getPickedContent(e.getX(), e.getY()).getName().split("-")[0];
					fullTagName = fullTagName.replaceAll("(.*)?_(pre|post)\\d?.*", "$1");
					if (fullTagName.matches("[A-Z]+by([A-Z]+)")){
						cellName = fullTagName.replaceAll("[A-Z]+by([A-Z]+)","$1");
					} else if (!fullTagName.contains("~")) {
						cellName = fullTagName;
					}
					clickedROIstring = fullTagName + ": "
							+ rm.getImagePlus().getTitle();				}

				if (sliceRois!=null) {
					Roi[] sliceRoisArray = new Roi[sliceRois.size()];
					for (int sr=0;sr<sliceRoisArray.length;sr++)
						sliceRoisArray[sr] = ((Roi)sliceRois.get(sr));
					for (int r=0; r<sliceRoisArray.length; r++) {  
						//					IJ.log(""+r);
						if ((!lineageMap && this.getLabelShapes().get(sliceRoisArray[r]) != null
								&& this.getLabelShapes().get(sliceRoisArray[r]).contains(xOS, yOS))
								|| (brainbowSelection /*&& rm.getImagePlus().getCanvas().getLabelShapes().get(r).intersects(new Rectangle (xOS*10-200, yOS*10-200,400,400))*/)
								|| (lineageMap && logLines.length >0 && logLines[logLines.length-1].startsWith(">")
										&& (this.getLabelShapes().get(sliceRoisArray[r]).getName()).startsWith("\""+logLines[logLines.length-1].split("[> ,]")[1]+" " + logLines[logLines.length-1].split("[> ,]")[2])) ) {
							mi = null;
							targetTag[0] = r;
							targetTag[1] = (int) this.getLabelShapes().get(sliceRoisArray[r]).getBounds().getCenterX();
							targetTag[2] = (int) this.getLabelShapes().get(sliceRoisArray[r]).getBounds().getCenterY();

							if (!brainbowSelection) {
								fullTagName = this.getLabelShapes().get(sliceRoisArray[r]).getName();
								fullTagName = fullTagName.split(" ")[0];
								cellName = fullTagName.split("[\"|=]")[1].trim();
								if (cellName.split(" ").length >1 
										&& cellName.split(" ")[1].matches("-*\\d*") 
										&& (cellName.split(" ").length <3?true:cellName.split(" ")[2].matches("\\+*")) ){
									cellName = cellName.split(" ")[0];
								} 
								if (cellName.matches("[A-Z]+by([A-Z]+)")){
									cellName = cellName.replaceAll("[A-Z]+by([A-Z]+)","$1");
								}

								mi =new JMenuItem("\""+cellName + " \": synch all windows to this tag" );
								mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Synch.png")));
								clickedROIstring = fullTagName + ": "
										+ rm.getImagePlus().getTitle();
								mi.addActionListener(ij);
								popup.add(mi);

								//							mi = new JMenuItem(listModel.get(r) + ": "
								//									+ rm.getImagePlus().getTitle());
								//							clickedROIstring = listModel.get(r) + ": "
								//									+ rm.getImagePlus().getTitle();
								//							mi.addActionListener(ij);
								//							popup.add(mi);
							}
							if (true) r=sliceRoisArray.length;
						}
					}
				}
				
				if (cellName.toLowerCase().matches("bwm-[a-z]+0\\d+")){
					cellName = cellName.toLowerCase().replaceAll("(bwm-)([a-z]+)(0)(\\d+)", "$2$4");
				}
				
				String oldLog1 = IJ.getLog();
				IJ.log(cellName);
				IJ.runMacro(""
						+ "print(\"starting...\");"
						+ "string = File.openUrlAsString(\"http://fsbill.cam.uchc.edu/gloworm/Xwords/Partslist.html\");"
						+ "fates = split(string, \"\\\'?\\\'\");"
						+ "print(\"Derived Fates:\");"
						+ "for (i=0; i<lengthOf(fates); i++) {"
						+ "		if (indexOf(fates[i], \"nofollow\") > 0)"
						+ "			print(\"descriptor \"+ replace(fates[i], \"\\\"\", \"\") );"
						+ "}");

				String[] partsListHtmlLines = IJ.getLog().split("\n");
				IJ.log("\\Clear");
				IJ.log(oldLog1);

				for (int p=0;p<partsListHtmlLines.length;p++) {
					String thing = partsListHtmlLines[p];
					thing = thing
							.replace("</a></td><td>", ":")
							.replace("</td><td>", ";")
							.replaceAll("\\.", "");

					if (thing.startsWith("descriptor ")
							&& thing.toLowerCase().contains(cellName.toLowerCase())) {
						String itemString = thing.substring(16);
						mi = new JMenuItem(itemString.contains(" rel=") ? itemString
								.substring(0, itemString.indexOf(" rel="))
								: itemString);
						itemString = (itemString.contains(" rel=") ? itemString
								.substring(0, itemString.indexOf(" rel="))
								: itemString)
								+ " "
								+ thing.substring(thing
										.indexOf("nofollow>") + 9);
						cellDescription = itemString;

					}
				}
				

				final String fCellDescription = cellDescription;
				
				for (int i = 0; i < impIDs.length; i++) {
					if (WindowManager.getImage(impIDs[i]).getWindow() instanceof ImageWindow3D) {
						DefaultListModel otherListModel = ((Image3DUniverse)((ImageWindow3D)WindowManager.getImage(impIDs[i]).getWindow()).getUniverse()).getContent3DManager().getListModel();
						Object[] objNames = otherListModel.toArray();
						for (int j = 0; j < objNames.length; j++) {
							String nextObjName = (String) objNames[j];
							nextObjName = nextObjName.replaceAll("(.*?)((_pre|_post\\d+)?)_#0_#0 \"_0", "$1");
							if ((nextObjName).contains("\"") && ("\""+(nextObjName).split("\"")[1].split(" ")[0].toLowerCase().trim()).equals(fullTagName.toLowerCase())
									&& !clickedROIstring
									.contains(nextObjName
											+ ": "
											+ WindowManager
											.getImage(
													impIDs[i])
													.getTitle())) {
								mi = new JMenuItem("also see: "
										+ (nextObjName).split("_")[0]
												+ " in \""
												+ (WindowManager.getImage(
														impIDs[i]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																(WindowManager.getImage(
																		impIDs[i]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																			(WindowManager.getImage(
																					impIDs[i]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
								mi.setActionCommand("also see: "
										+ objNames[j]
												+ ": "
												+ WindowManager.getImage(
														impIDs[i]).getTitle());
								mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/More.png")));
								mi.addActionListener(ij);
								popup.add(mi);
								j = objNames.length;
							}
						}
					} else {
						DefaultListModel otherListModel =WindowManager.getImage(impIDs[i]).getRoiManager().getListModel();
						Object[] roiNames = otherListModel.toArray();
						for (int j = 0; j < roiNames.length; j++) {
							String nextRoiName = (String) roiNames[j];
							if (nextRoiName.startsWith("\" \""))
								continue;
							nextRoiName = (nextRoiName).split("\"")[1].split(" ")[0];
							if (nextRoiName.toLowerCase().trim().equals(fullTagName.toLowerCase())
									&& !clickedROIstring
									.contains(nextRoiName
											+ ": "
											+ WindowManager
											.getImage(
													impIDs[i])
													.getTitle())) {
								mi = new JMenuItem("also see: "
										+ (nextRoiName).split("_")[0]
												+ " in \""
												+ (WindowManager.getImage(
														impIDs[i]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																(WindowManager.getImage(
																		impIDs[i]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																			(WindowManager.getImage(
																					impIDs[i]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
								mi.setActionCommand("also see: "
										+ roiNames[j]
												+ ": "
												+ WindowManager.getImage(
														impIDs[i]).getTitle());
								mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/More.png")));
								mi.addActionListener(ij);
								popup.add(mi);
								j = roiNames.length;
							}
						}
			
					}
				}
				popupInfo[0] = cellName;
				popupInfo[1] = cellName+"\n\n";

				final String finalCellName = ""+cellName;
				
				new Thread(new Runnable() {
					public void run() {
						ImagePlus cartoonImp = IJ.openImage("http://fsbill.cam.uchc.edu/gloworm/Xwords/NeuronDiagrams/"+finalCellName.toLowerCase()+".gif");
						if (cartoonImp!=null && cartoonImp.getImage()!=null) {
							cartoonImp.setTitle(finalCellName);
							JPanel cartoonPanel = new JPanel();
							JButton cartoonButton = new JButton();
							cartoonButton.setIcon(new ImageIcon(cartoonImp.getImage()));
							cartoonButton.setToolTipText(fCellDescription);
							cartoonPanel.add(cartoonButton);
							cartoonPanel.setBackground(Color.white);
							popup.add(finalCellName);
							popup.add(cartoonPanel, 1);
						}
					}
				}
				).start();
				
				

				
				
				String[] logLines2=null;
				
//				if (false) {
				if (getGenes && cellName != "" && cellName != null) {
					JMenu genePopup = new JMenu(cellName + ": Expressed Genes (live from WormBase)>", true);
					genePopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/DNAicon.png")));
					genePopup.getPopupMenu().addPopupMenuListener(ij);
					popupInfo[1] = popupInfo[1] + "Expressed Genes (live from WormBase)>\n";
					String oldLog = IJ.getLog();
					IJ.log(cellName);

					String wbCellID = "";
					if (!(cellName.matches("AB[aprldv]*")
							|| cellName.matches("C[aprldv]*")
							|| cellName.matches("D[aprldv]*")
							|| cellName.matches("E[aprldv]*")
							|| cellName.matches("MS[aprldv]*"))) {
						//						cellName = ((TextRoi)cursorRoi).getName();
						IJ.runMacro(""
								+ "print(\"starting...\");"
								+ "string = File.openUrlAsString(\"http://fsbill.cam.uchc.edu/gloworm/Xwords/Partslist.html\");"
								+ "fates = split(string, \"\\\'?\\\'\");"
								+ "print(\"Derived Fates:\");"
								+ "for (i=0; i<lengthOf(fates); i++) {"
								+ "		if (indexOf(fates[i], \"nofollow\") > 0)"
								+ "			print(\"begets \"+ replace(fates[i], \"\\\"\", \"\") );"
								+ "}");

						logLines2 = IJ.getLog().split("\n");
						IJ.log("\\Clear");
						IJ.log(oldLog);

						for (int l = 0; l < logLines2.length; l++) {
							logLines2[l] = logLines2[l]
									.replace("</a></td><td>", ":")
									.replace("</td><td>", ";")
									.replaceAll("\\.", "");
							if (logLines2[l].startsWith("begets ")
									&& logLines2[l].toLowerCase().matches(".*" + cellName.toLowerCase()+ ":.*")) {
								wbCellID = logLines2[l].substring(logLines2[l].indexOf("name=")+5, logLines2[l].indexOf(" rel="));

							}
						}
					} else {
						wbCellID = cellName;
					}
					mi =  new JMenuItem("^--------------------^");
					mi.addActionListener(ij);
					genePopup.add(mi);

					mi = new JMenuItem(cellName);
					mi.addActionListener(ij);
					genePopup.add(mi);
					//					genePopup.add(wbCellID);
					//					IJ.log("\\Clear");

//		OLD OBSOLETE WORMBASE QUERY SYNTAX...replacing on 06102021					
//					IJ.runMacro(""
//							+ "string = File.openUrlAsString(\"https://www.wormbase.org/db/get?name="
//							+ cellName
//							+ ";class=Anatomy_term\");"
//							+ "print(string);");							

					IJ.runMacro(""
							+ "string = File.openUrlAsString(\"https://wormbase.org/species/c_elegans/anatomy_term/"
							+ cellName
							+ "\");"
							+ "print(string);");							
					logLines2 = IJ.getLog().split("wname=\"associations\"");
					//					IJ.log("\\Clear");
					IJ.log(oldLog);
					//					IJ.showMessage(cellName+IJ.getLog());
					String restString = "";
					if (logLines2 != null && logLines2.length > 1 && logLines2[1].split("\"").length > 1)
						restString = logLines2[1].split("\"")[1];

					IJ.runMacro(""
							+ "string = File.openUrlAsString(\"https://www.wormbase.org"
							+ restString
							+ "\");"

							+ "genes = split(string, \"><\");"
							+ "print(\"Expressed Genes:\");"
							+ "for (i=0; i<lengthOf(genes); i++) {"
							+ "	if (startsWith(genes[i], \"span class=\\\"locus\\\"\") ) "
							+ "		print(\"expresses \"+genes[i+1]);"
							+ "}");
					//popup.add(new JMenuItem("-"));
					logLines2 = IJ.getLog().toLowerCase().split("\n");
					Arrays.sort(logLines2);

					IJ.log("\\Clear");
					IJ.log(oldLog);
					IJ.runMacro(""
							+ "string = File.openUrlAsString(\"http://www.gloworm.org/\");"
							+ "print(string);");
					String glowormHomePage = IJ.getLog();
					IJ.log("\\Clear");
					IJ.log(oldLog);
					for (int l = 0; l < logLines2.length; l++) {
						if (logLines2[l].startsWith("expresses")) {

							boolean hasCytoSHOWData = glowormHomePage.toLowerCase()
									.contains(logLines2[l].split(" ")[1] + ")")
									|| glowormHomePage.contains(logLines2[l]
											.split(" ")[1]
													+ ":");

							//					boolean hasCytoSHOWData = glowormGenePage.contains("Interactive movies are available for a fluorescence reporter of this gene.");
							boolean redundant = false;
							for(int i=0;i<genePopup.getItemCount();i++) {
								if (genePopup.getItem(i).getLabel().contentEquals(logLines2[l])) {
									redundant = true;
									break;
								}
									
							}
							if (redundant)
								continue;
							mi = new JMenuItem(logLines2[l]);
							popupInfo[1] = popupInfo[1]+ logLines2[l] + "\n";

							mi.addActionListener(ij);
							genePopup.add(mi);
							if (hasCytoSHOWData) {
								String[] glowormLogLines = glowormHomePage.split("\n");
								String matchString = "";
								for (int g=0;g<glowormLogLines.length;g++) {
									if (glowormLogLines[g].matches("<a href=.*"+logLines2[l].split(" ")[1]+".*</a>")) {
										matchString = glowormLogLines[g].substring(glowormLogLines[g].indexOf("http://"), glowormLogLines[g].indexOf("'>"));
										g = glowormLogLines.length;
									}
								}
								JMenu glowormPopup = new JMenu(" *** " + logLines2[l].split(" ")[1] + " shown in CytoSHOW:", true);
								glowormPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/CytoSHOW3icon.png")));
								glowormPopup.getPopupMenu().addPopupMenuListener(ij);
								mi =  new JMenuItem("^--------------------^");
								mi.addActionListener(ij);
								glowormPopup.add(mi);
								mi = new JMenuItem(matchString);
								popupInfo[1] = popupInfo[1]+ "  "+ matchString + "\n";
								mi.addActionListener(ij);
								glowormPopup.add(mi);
								IJ.runMacro(""
										+ "string = File.openUrlAsString(\""+matchString+"\");"
										+ "print(string);");
								String glowormGenePage = IJ.getLog();
								IJ.log("\\Clear");
								IJ.log(oldLog);

								String[] glowGeneLogLines = glowormGenePage.split("\n");
								int tagLag = 0;
								for (int gg=0; gg<glowGeneLogLines.length; gg++){
									if (glowGeneLogLines[gg].toLowerCase().contains("slice4d movies") || glowGeneLogLines[gg].toLowerCase().contains("stereo4d movies")) {
										mi = new JMenuItem(glowGeneLogLines[gg].replace("<br />", ""));
										popupInfo[1] = popupInfo[1]+ "    "+ glowGeneLogLines[gg].replace("<br />", "") + "\n";
										mi.setEnabled(false);								
										glowormPopup.add(mi);
										tagLag=0;
									} else {
										tagLag++;
									}
									if (glowGeneLogLines[gg].contains("http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE=") && tagLag<3) {
										mi = new JMenuItem("movie "+ glowGeneLogLines[gg].substring(glowGeneLogLines[gg].indexOf("\">")+2, 
												glowGeneLogLines[gg].indexOf("</a>")));
										popupInfo[1] = popupInfo[1]+ "      "+ glowGeneLogLines[gg].substring(glowGeneLogLines[gg].indexOf("\">")+2, 
												glowGeneLogLines[gg].indexOf("</a>")) +"...\n"
												+"            "+ glowGeneLogLines[gg].substring(glowGeneLogLines[gg].indexOf("http://"), 
														glowGeneLogLines[gg].indexOf("\">")) + "\n";
										mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/CytoSHOW3icon.png")));
										mi.addActionListener(ij);
										glowormPopup.add(mi);
										if (glowGeneLogLines[gg].contains("also viewable in ")){
											mi = new JMenuItem("movie "+ glowGeneLogLines[gg].substring(glowGeneLogLines[gg].indexOf("\">")+2, 
													glowGeneLogLines[gg].indexOf("</a>")) +" also viewable in RedCyan Stereo");
											popupInfo[1] = popupInfo[1] +"      "+ "--also viewable in RedCyan Stereo"  +"...\n"
													+"            "+ glowGeneLogLines[gg].substring(glowGeneLogLines[gg].indexOf("http://"), 
															glowGeneLogLines[gg].indexOf("\">")) + "&amp;VIEW=MQTVS_RedCyanStereo_scene.scn" + "\n"; 
											mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/CytoSHOW3icon.png")));
											mi.addActionListener(ij);
											glowormPopup.add(mi);

										}
										tagLag=0;
									}
								}

								genePopup.add(glowormPopup);
							}

							hasCytoSHOWData = false;
						}
					}
					IJ.log("\\Clear");
					IJ.log(oldLog);
					popup.add(genePopup);
				}

				if (getFates && cellName != "") {
					JMenu relationshipsPopup = new JMenu(cellName+": Cell Relationships >", true);
					relationshipsPopup.getPopupMenu().addPopupMenuListener(ij);
					relationshipsPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/HandshakeIcon.png")));
					popupInfo[1] = popupInfo[1]+"\nCell Relationships >\n";
					//popup.add(new JMenuItem("-"));
					mi =  new JMenuItem("^--------------------^");
					mi.addActionListener(ij);
					relationshipsPopup.add(mi);

//					if (!rm.getImagePlus().getTitle().startsWith("Projections") && rm.getFullListModel().size()>0) {
//
//						JMenu nearPopup = new JMenu(cellName+": Nearby cells >", true);
//						nearPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/NearIcon.png")));
//						nearPopup.getPopupMenu().addPopupMenuListener(ij);
//						mi =  new JMenuItem("^--------------------^");
//						mi.addActionListener(ij);
//						nearPopup.add(mi);
//						
//						ImagePlus guideImp = rm.getImagePlus();
//						if (rm.getImagePlus().getMotherImp() != null)
//							guideImp = rm.getImagePlus().getMotherImp();
//						int frames = guideImp.getNFrames();
//
//						ArrayList<Roi> sameCellRois = new ArrayList<Roi>(); 
//						ArrayList<String> sameCellTrimmedNames = new ArrayList<String>(); 
//						ArrayList<Integer> sameCellZs = new ArrayList<Integer>(); 
//						ArrayList<Integer> sameCellTs = new ArrayList<Integer>(); 
//						
//						for (Roi nextRoi:this.getImage().getRoiManager().getROIsByName().get("\""+roi.getName().split("\"")[1]+"\"")){
//							if (!nextRoi.getName().split("\"")[1].equalsIgnoreCase(roi.getName().split("\"")[1])){
//								continue;
//							}
//							sameCellRois.add(nextRoi);
//							sameCellTrimmedNames.add(nextRoi.getName());
//							sameCellZs.add(nextRoi.getZPosition());
//							sameCellTs.add(nextRoi.getTPosition());
//
//						}
//
//						int zRadius=1;
//						double inPlaneDiameter=0;
//						double maxInPlaneDiameter=0;
//						ArrayList<Roi> nearHits = new ArrayList<Roi>();
//						ArrayList<String> nearHitNames = new ArrayList<String>();
//						Hashtable<String,ArrayList<Roi>> hitNameRoisHashtable = new Hashtable<String,ArrayList<Roi>>();
//						String[] targetChunks = sliceRoisArray[targetTag[0]].getName().split("_");
//						int targetZ = Integer.parseInt(targetChunks[targetChunks.length-2]);
//						int tFrame = Integer.parseInt(targetChunks[targetChunks.length-1]
//								.replace("C", "").replace("Z", "").replace("T", "").split("-")[0]);
//						
//						double threeDZdiagonal = Math.sqrt(
//															Math.pow(
//																	Math.sqrt(Math.pow(guideImp.getWidth(), 2)
//																				+Math.pow(guideImp.getHeight(), 2)
//																			 ) 
//																, 2)
//															+
//															Math.pow(
//																	guideImp.getNSlices()
//																	*(guideImp.getCalibration().pixelDepth/guideImp.getCalibration().pixelWidth)
//																,2)
//														);
//						
//						double zxRatio = guideImp.getCalibration().pixelDepth/guideImp.getCalibration().pixelWidth;
//
//						
//						while (nearHits.size() < 10 && (zRadius< threeDZdiagonal)) {
//							for (int zSliceAdjust=0-(zRadius-1);zSliceAdjust<=0+(zRadius-1);zSliceAdjust++) {
//
//								for (int sc=0;sc<sameCellRois.size();sc++) {
//									int zSlice = sameCellRois.get(sc).getZPosition() + zSliceAdjust;
//									if (zSlice < 1 || zSlice > rm.getImagePlus().getNSlices())
//										continue;
//									if (guideImp.getTitle().contains("SW_")) {
//										zSlice = sameCellZs.get(sc);
//										inPlaneDiameter = 10 * zxRatio * zRadius;
//									}
//									if (!guideImp.getTitle().contains("SW_")) {
//										double legXYSquared = Math.pow(zRadius,2)-Math.pow((zSlice-targetZ),2);
//										inPlaneDiameter = 2 * zxRatio * Math.sqrt(legXYSquared>0?legXYSquared:0);
//									}
//									if (inPlaneDiameter>maxInPlaneDiameter)
//										maxInPlaneDiameter = inPlaneDiameter;
//									Roi hoodRoi = new OvalRoi(sameCellRois.get(sc).getBounds().getCenterX() - (inPlaneDiameter / 2),
//											sameCellRois.get(sc).getBounds().getCenterY() - (inPlaneDiameter / 2), 
//											inPlaneDiameter, inPlaneDiameter);
////									Roi[] nearbyROIs = rm.getSliceSpecificRoiArray(zSlice,
////											sameCellTs.get(sc), false);
//									Roi[] nearbyROIs = this.getImage().getRoiManager().getROIsByNumbers().get(""+1+"_"+zSlice+"_"+sameCellTs.get(sc)).toArray(new Roi[1]);
//									for (int h = 0; h < nearbyROIs.length; h++) {
//										Roi nextNearRoi = nearbyROIs[h];
//										String nnrn = nextNearRoi.getName().split("[\"|=]")[1].trim().split(" ")[0];
//										if (((OvalRoi)hoodRoi).contains((int) nextNearRoi
//												.getBounds().getCenterX(),
//												(int) nextNearRoi.getBounds()
//												.getCenterY())
//												&& !clickedROIstring.startsWith("\""+nnrn) ){
//											String hitTrimName = nextNearRoi.getName().split("[\"|=]")[1].trim().split(" ")[0];
//											if (hitNameRoisHashtable.get(hitTrimName)==null) {
//												hitNameRoisHashtable.put(hitTrimName, new ArrayList<Roi>());
//											}
//											hitNameRoisHashtable.get(hitTrimName).add(nextNearRoi);
//											boolean notGotYet = !nearHits.contains(nextNearRoi)  && !nearHitNames.contains(nextNearRoi.getName().split("[\"|=]")[1].trim().split(" ")[0]);
//											if (notGotYet && nearHits.size()<100) {
//												nearHits.add(nextNearRoi);
//												nearHitNames.add(nextNearRoi.getName().split("[\"|=]")[1].trim().split(" ")[0]);
//											}
//										}
//									}
//								}
//								if (guideImp.getTitle().contains("SW_")) 
//									zSliceAdjust = targetZ+zRadius+1;
//							}
//								zRadius = (int) (zRadius+1);
//						}
//
//						for (String hit:hitNameRoisHashtable.keySet()) {
//							ArrayList<Roi> hitRois = hitNameRoisHashtable.get(hit);
//							
//							if (imp.getTitle().contains("SW_")){
//
//							}
//							
//							String hitPlaneString ="";
//							int[] hitPlaneArray = new int[hitRois.size()];
//							
//							if(guideImp.getTitle().contains("SW_")) {
//								for (int q=0;q<hitRois.size();q++)
//									hitPlaneArray[q]=hitRois.get(q).getZPosition();
//							} else {
//								for (int q=0;q<hitRois.size();q++)
//									hitPlaneArray[q]=hitRois.get(q).getTPosition();
//							}
//							Arrays.sort(hitPlaneArray);
//							for (Object plane:hitPlaneArray)
//								if (!hitPlaneString.contains(" " +(Integer)plane+","))
//									hitPlaneString = hitPlaneString +" " +(Integer)plane+",";
//							hitPlaneString = hitPlaneString.replaceAll("(.*),", "$1 ");
//							if(guideImp.getTitle().contains("SW_")) {
//								mi = new JMenuItem("near "
//										+ hit
//										+ " within "+ (maxInPlaneDiameter/2 * imp.getCalibration().pixelWidth*1000) +"nm at slices {"+ hitPlaneString+ "} in \""
//										+ (rm.getImagePlus().getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
//												(rm.getImagePlus().getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
//													(rm.getImagePlus().getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
//							} else {
//								mi = new JMenuItem("near "
//										+ hit
//												+ " within "+ (maxInPlaneDiameter/2 * imp.getCalibration().pixelWidth)+"�m at frames {"+ hitPlaneString+ "} in \""
//												+ (rm.getImagePlus().getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
//														(rm.getImagePlus().getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
//															(rm.getImagePlus().getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
//							}
//							mi.setActionCommand("near "
//									+ hitNameRoisHashtable.get(hit).get(0).getName() + ": "
//									+ rm.getImagePlus().getTitle());
//							popupInfo[1] = popupInfo[1]+ mi.getText()+"\n";
//
//							mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
//							mi.addActionListener(ij);
//							nearPopup.add(mi);
//
//						}
//
//						relationshipsPopup.add(nearPopup);			
//					}
					popupInfo[1] = popupInfo[1] +"\n";

					String analogName ="";
					String embAnalogsMatrix = IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/EmbryonicAnalogousCells.csv");
					String[] embAnalogsRows = embAnalogsMatrix.split("\n");
					JMenu analogsPopup = new JMenu(cellName+": Analogous cells >", true);
					analogsPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Analogs.png")));
					analogsPopup.getPopupMenu().addPopupMenuListener(ij);
					mi =  new JMenuItem("^--------------------^");
					mi.addActionListener(ij);
					analogsPopup.add(mi);
					int[] openImageIDsWithRM = WindowManager.getIDList();
					for (int s=1;s< embAnalogsRows.length;s++) {
						String[] embAnalogsChunks = embAnalogsRows[s].split(",");
						if (cellName.startsWith(embAnalogsChunks[0])){
							mi = new JMenuItem(cellName);
							mi.addActionListener(ij);
							analogsPopup.add(mi);

							relationshipsPopup.add(analogsPopup);

							mi = new JMenuItem("analogous to   " + embAnalogsChunks[1]+cellName.substring(embAnalogsChunks[0].length(), cellName.length()));
							mi.addActionListener(ij);
							popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
							analogsPopup.add(mi);
							analogName = embAnalogsChunks[1]+cellName.substring(embAnalogsChunks[0].length(), cellName.length());


							for (int o=0;o< openImageIDsWithRM.length;o++) {

								Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
								for (int j = 0; j < imgRoiNames.length; j++) {
									if (((String) imgRoiNames[j]).replace("\"","").trim().startsWith(analogName+" ")) {
										String itemString = "***shown here: "
												+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
										j = imgRoiNames.length;
										mi = new JMenuItem(itemString.split("_")[0] + " in " 
												+ (WindowManager.getImage(
														openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																(WindowManager.getImage(
																		openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																			(WindowManager.getImage(
																					openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");

										
										mi.setToolTipText(itemString);
										mi.setActionCommand(itemString);

										mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
										popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
										mi.addActionListener(ij);
										analogsPopup.add(mi);
									}
								}
							}

						}
					}
					if ( cellName.matches(".*(DL|D|DR|R|VR|V|VL|L)")){
						String oldLog = IJ.getLog();
						mi = new JMenuItem(cellName);
						mi.addActionListener(ij);
						popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
						analogsPopup.add(mi);
						relationshipsPopup.add(analogsPopup);

						IJ.runMacro(""
								+ "print(\"starting...\");"
								+ "string = File.openUrlAsString(\"http://fsbill.cam.uchc.edu/gloworm/Xwords/Partslist.html\");"
								+ "fates = split(string, \"\\\'?\\\'\");"
								+ "print(\"Derived Fates:\");"
								+ "for (i=0; i<lengthOf(fates); i++) {"
								+ "		if (indexOf(fates[i], \"nofollow\") > 0)"
								+ "			print(\"analogOf \"+ replace(fates[i], \"\\\"\", \"\") );"
								+ "}");

						logLines = IJ.getLog().split("\n");
						IJ.log("\\Clear");
						IJ.log(oldLog);
						for (int h=0;h<logLines.length;h++){
							logLines[h] = logLines[h]
									.replace("</a></td><td>", ":")
									.replace("</td><td>", ";")
									.replaceAll("\\.", "");
							if (logLines[h].startsWith("analogOf ")
									&& ( cellName.matches(".*(D|R|V|L)") && (logLines[h].trim().toUpperCase().matches(".*[^A-Z]" + cellName.toUpperCase().substring(0,cellName.length()-1)+ "(DL|D|DR|R|VR|V|VL|L):.*") )
											|| ( cellName.matches(".*(DL|DR|VL|VR)") && logLines[h].trim().toUpperCase().matches(".*[^A-Z]" + cellName.toUpperCase().substring(0,cellName.length()-2)+ "(DL|D|DR|R|VR|V|VL|L):.*"))) ) {
								mi = new JMenuItem("analogous to   " + logLines[h].substring(logLines[h].indexOf("name=")+5, logLines[h].indexOf(" rel=")) );
								mi.addActionListener(ij);
								popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
								analogsPopup.add(mi);


								analogName = logLines[h].substring(logLines[h].indexOf("name=")+5, logLines[h].indexOf(" rel="));
								for (int o=0;o< openImageIDsWithRM.length;o++) {

									Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
									for (int j = 0; j < imgRoiNames.length; j++) {
										if (((String) imgRoiNames[j]).toUpperCase().replace("\"","").trim().startsWith(analogName.toUpperCase()+" ")) {
											String itemString = "***shown here: "
													+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
											j = imgRoiNames.length;
											mi = new JMenuItem(itemString.split("_")[0] + " in " 
													+ (WindowManager.getImage(
															openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																	(WindowManager.getImage(
																			openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																				(WindowManager.getImage(
																						openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
											mi.setToolTipText(itemString);
											mi.setActionCommand(itemString);

											mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
											popupInfo[1] = popupInfo[1]+mi.getText()+"\n";											mi.addActionListener(ij);
											analogsPopup.add(mi);
										}
									}
								}

							}
						}
					}
					popupInfo[1] = popupInfo[1] +"\n";

					String synapseMatrix = IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/NeuronConnect.csv");
					if (synapseMatrix.contains(cellName)){
						boolean presyn = false;
						boolean postsyn = false;
						boolean elec = false;
						boolean nmj = false;
						JMenu presynapticPopup = new JMenu(cellName+": Presynaptic >", true);
						presynapticPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Presynaptic.png")));
						presynapticPopup.getPopupMenu().addPopupMenuListener(ij);
						mi =  new JMenuItem("^--------------------^");
						mi.addActionListener(ij);
						presynapticPopup.add(mi);
						JMenu postsynapticPopup = new JMenu(cellName+": Postsynaptic >", true);
						postsynapticPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Postsynaptic.png")));
						postsynapticPopup.getPopupMenu().addPopupMenuListener(ij);
						mi =  new JMenuItem("^--------------------^");
						mi.addActionListener(ij);
						postsynapticPopup.add(mi);
						JMenu electricPopup = new JMenu(cellName+": Electrical >", true);
						electricPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Electric.png")));
						electricPopup.getPopupMenu().addPopupMenuListener(ij);
						mi =  new JMenuItem("^--------------------^");
						mi.addActionListener(ij);
						electricPopup.add(mi);
						JMenu neuromuscularPopup = new JMenu(cellName+": Neuromuscular >", true);
						neuromuscularPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Muscle.png")));
						neuromuscularPopup.getPopupMenu().addPopupMenuListener(ij);
						mi =  new JMenuItem("^--------------------^");
						mi.addActionListener(ij);
						neuromuscularPopup.add(mi);

						String[] synapseRows = synapseMatrix.split("\n");
						for (int s=1;s< synapseRows.length;s++) {
							if (synapseRows[s].contains(cellName)){				

								String[] synapseChunks = synapseRows[s].split(",");

								//								int[] openImageIDsWithRM = WindowManager.getIDList();

								if (synapseChunks[0].contains(cellName)) {
									if (synapseChunks[2].startsWith("S")) {
										presyn = true;
										mi = new JMenuItem("synapses chemically onto  "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ (synapseChunks[2].contains("Sp")?"polyadic":"monadic")
												+ (Integer.parseInt(synapseChunks[3])>1?" synapses":" synapse"));
										popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
										mi.addActionListener(ij);
										presynapticPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").trim().startsWith(candidate+" ")) {
													String itemString = "***shown here: "
															+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
													j = imgRoiNames.length;
													mi = new JMenuItem(itemString.split("_")[0] + " in " 
															+ (WindowManager.getImage(
																	openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																			(WindowManager.getImage(
																					openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																						(WindowManager.getImage(
																								openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
													mi.setToolTipText(itemString);
													mi.setActionCommand(itemString);

													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
													mi.addActionListener(ij);
													presynapticPopup.add(mi);
												}
											}
										}
									}	
									if (synapseChunks[2].startsWith("R")) {
										postsyn = true;
										mi = new JMenuItem("is synapsed chemically by "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ (synapseChunks[2].contains("Rp")?"polyadic":"monadic") 
												+ (Integer.parseInt(synapseChunks[3])>1?" synapses":" synapse"));
										popupInfo[1] = popupInfo[1]+mi.getText()+"\n";


										mi.addActionListener(ij);
										postsynapticPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").trim().startsWith(candidate+" ")) {
													String itemString = "***shown here: "
															+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
													j = imgRoiNames.length;
													mi = new JMenuItem(itemString.split("_")[0] + " in " 
															+ (WindowManager.getImage(
																	openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																			(WindowManager.getImage(
																					openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																						(WindowManager.getImage(
																								openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
													mi.setToolTipText(itemString);
													mi.setActionCommand(itemString);

													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+mi.getText()+"\n";

													mi.addActionListener(ij);
													postsynapticPopup.add(mi);
												}
											}
										}
									}	
									if (synapseChunks[2].startsWith("EJ")) {
										elec = true;
										mi = new JMenuItem("synapses electrically to  "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ "electric" 
												+ (Integer.parseInt(synapseChunks[3])>1?" junctions":" junction"));
										popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
										mi.addActionListener(ij);
										electricPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").trim().startsWith(candidate+" ")) {
													String itemString = "***shown here: "
															+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
													j = imgRoiNames.length;
													mi = new JMenuItem(itemString.split("_")[0] + " in " 
															+ (WindowManager.getImage(
																	openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																			(WindowManager.getImage(
																					openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																						(WindowManager.getImage(
																								openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
													mi.setToolTipText(itemString);
													mi.setActionCommand(itemString);

													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+mi.getText()+"\n";

													mi.addActionListener(ij);
													electricPopup.add(mi);
												}
											}
										}
									}	
									if (synapseChunks[2].startsWith("NMJ")) {
										nmj = true;
										mi = new JMenuItem("neuromuscular junctions   "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ "neuromuscular" 
												+ (Integer.parseInt(synapseChunks[3])>1?" junctions":" junction"));
										popupInfo[1] = popupInfo[1]+mi.getText()+"\n";
										mi.addActionListener(ij);
										neuromuscularPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").trim().startsWith(candidate+" ")) {
													String itemString = "***shown here: "
															+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
													j = imgRoiNames.length;
													mi = new JMenuItem(itemString.split("_")[0] + " in " 
															+ (WindowManager.getImage(
																	openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																			(WindowManager.getImage(
																					openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																						(WindowManager.getImage(
																								openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
													mi.setToolTipText(itemString);
													mi.setActionCommand(itemString);

													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+mi.getText()+"\n";

													mi.addActionListener(ij);
													neuromuscularPopup.add(mi);
												}
											}
										}
									}	

								}



							}
						}
						if (presyn) relationshipsPopup.add(presynapticPopup);			
						if (postsyn) relationshipsPopup.add(postsynapticPopup);
						if (elec) relationshipsPopup.add(electricPopup);
						if (nmj) relationshipsPopup.add(neuromuscularPopup);						
					}

					popup.add(relationshipsPopup);
				}
				popupInfo[1] = popupInfo[1] +"\n";

				if (getFates && cellName != "") {
					JMenu fatePopup = new JMenu(cellName+": Descendent Cells >", true);
					fatePopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/DescendantsIcon.png")));
					fatePopup.getPopupMenu().addPopupMenuListener(ij);
					mi =  new JMenuItem("^--------------------^");
					mi.addActionListener(ij);
					fatePopup.add(mi);
					popupInfo[1] = popupInfo[1] + "\nDescendent Cells >\n";

					String oldLog = IJ.getLog();
					IJ.log(cellName);

//					imp.getWindow().toFront();
					IJ.runMacro(""
							+ "print(\"starting...\");"
							+ "string = File.openUrlAsString(\"http://fsbill.cam.uchc.edu/gloworm/Xwords/Partslist.html\");"
							+ "fates = split(string, \"\\\'?\\\'\");"
							+ "print(\"Derived Fates:\");"
							+ "for (i=0; i<lengthOf(fates); i++) {"
							+ "		if (indexOf(fates[i], \"nofollow\") > 0)"
							+ "			print(\"begets \"+ replace(fates[i], \"\\\"\", \"\") );"
							+ "}");

					//popup.add(new JMenuItem("-"));
					logLines2 = IJ.getLog().split("\n");
					IJ.log("\\Clear");
					IJ.log(oldLog);
					mi = new JMenuItem(cellName);
					mi.addActionListener(ij);
					fatePopup.add(mi);

					for (int l = 0; l < logLines2.length; l++) {
						logLines2[l] = logLines2[l]
								.replace("</a></td><td>", ":")
								.replace("</td><td>", ";")
								.replaceAll("\\.", "");
						if (cellName.matches("EMS"))
							cellName = "(E|MS)";
						if (cellName.matches("P0"))
							cellName = "(P0|AB|EMS|E|MS|C|D)";
						if (cellName.matches("P1"))
							cellName = "(P0p|EMS|E|MS|C|D)";
						if (cellName.matches("P2"))
							cellName = "(P0pp|C|D)";
						if (cellName.matches("P3"))
							cellName = "(P0ppp|D)";
						if (cellName.matches("P4"))
							cellName = "(P0pppp)";

						if (logLines2[l].startsWith("begets ")
								&& logLines2[l].toLowerCase().contains(cellName.toLowerCase())) {
							int ensuingDivisions = 0;
							int cellNameIndex = logLines2[l].toLowerCase().indexOf(cellName.toLowerCase());
							int cellNameEndIndex = logLines2[l].substring(cellNameIndex).indexOf(";");
							if (cellNameEndIndex>0)
								ensuingDivisions = cellNameEndIndex - cellName.length();
							if (ensuingDivisions<1) {
								cellNameEndIndex = logLines2[l].substring(cellNameIndex).indexOf(" ");
								if (cellNameEndIndex>0)
									ensuingDivisions = cellNameEndIndex - cellName.length();
							}
							if (ensuingDivisions>0 
									&& logLines2[l].substring(cellNameIndex+cellName.length(), cellNameIndex+cellName.length()+ensuingDivisions)
									.matches("[apdvlr]*")) {

								for (int d=1;d<=ensuingDivisions;d++) {
									String itemString = "begets=>"+logLines2[l].substring(logLines2[l].indexOf(cellName), logLines2[l].indexOf(cellName)+cellName.length()+d)+" ->";
									mi = new JMenuItem(itemString);
									popupInfo[1] = popupInfo[1]+itemString+"\n";
									mi.addActionListener(ij);
									fatePopup.add(mi);
									int[] openImageIDsWithRM = WindowManager.getIDList();
									for (int o=0;o< openImageIDsWithRM.length;o++) {
										Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
										for (int j = 0; j < imgRoiNames.length; j++) {
											if (((String) imgRoiNames[j]).startsWith("\""+itemString
													.substring(8,itemString.indexOf(" ")+1)) ) {
												String menuString = "***shown here: "
														+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
												j = imgRoiNames.length;
												mi = new JMenuItem(menuString.split("_")[0] + " in " 
														+ (WindowManager.getImage(
														openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																(WindowManager.getImage(
																		openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																			(WindowManager.getImage(
																					openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
												mi.setActionCommand(menuString);
												mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
												popupInfo[1] = popupInfo[1]+itemString+"\n";

												mi.addActionListener(ij);
												fatePopup.add(mi);
											}
										}
									}

								}
							}

							String itemString = "begets=>"
									+ logLines2[l].substring(12, logLines2[l]
											.indexOf(";"));
							mi = new JMenuItem(itemString.contains(" rel=") ? itemString
									.substring(0, itemString.indexOf(" rel="))
									: itemString);
							itemString = (itemString.contains(" rel=") ? itemString
									.substring(0, itemString.indexOf(" rel="))
									: itemString)
									+ " "
									+ logLines2[l].substring(logLines2[l]
											.indexOf("nofollow>") + 9);

							mi.setToolTipText(itemString);
							mi.setActionCommand(itemString);

							popupInfo[1] = popupInfo[1]+itemString+"\n";
							mi.addActionListener(ij);
							fatePopup.add(mi);

							int[] openImageIDsWithRM = WindowManager.getIDList();
							for (int o=0;o< openImageIDsWithRM.length;o++) {
								Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
								for (int j = 0; j < imgRoiNames.length; j++) {
									if (((String) imgRoiNames[j]).startsWith("\""+itemString
											.substring(8,itemString.indexOf(" ")+1))
											||((String) imgRoiNames[j]).startsWith("\""+itemString
													.substring(itemString.indexOf(" ")+1,itemString.indexOf(":")))
													|| ((String) imgRoiNames[j]).contains(itemString
															.substring(itemString.indexOf(":") + 1,
																	itemString.indexOf(";")))) {
										String menuString = "***shown here: "
												+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
										j = imgRoiNames.length;
										mi = new JMenuItem(menuString.split("_")[0] + " in " 
												+ (WindowManager.getImage(
												openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
														(WindowManager.getImage(
																openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																	(WindowManager.getImage(
																			openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
										mi.setActionCommand(menuString);
										mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
										popupInfo[1] = popupInfo[1]+itemString+"\n";

										mi.addActionListener(ij);
										fatePopup.add(mi);
									}
								}
							}
						}
					}
					IJ.log("\\Clear");
					IJ.log(oldLog);
					popup.add(fatePopup);
				}
				popupInfo[1] = popupInfo[1] +"\n";

				if (getFates && cellName != "") {
					//popup.add(new JMenuItem("-"));
					JMenu ancestryPopup = new JMenu(cellName+": Ancestor Cells >", true);
					ancestryPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/AncestorIcon.png")));
					ancestryPopup.getPopupMenu().addPopupMenuListener(ij);
					mi =  new JMenuItem("^--------------------^");
					mi.addActionListener(ij);
					ancestryPopup.add(mi);
					popupInfo[1] = popupInfo[1] + "\nAncestor Cells >\n";

					String[] cellTagChunks = cellName.replace("\"", "").replace("=", "").split(" ");
					for (int c=0; c<cellTagChunks.length; c++){
						//						IJ.log(cellTagChunks[c]);
						if (cellTagChunks[c].matches("AB[aprldv]*")
								|| cellTagChunks[c].matches("C[aprldv]*")
								|| cellTagChunks[c].matches("D[aprldv]*")
								|| cellTagChunks[c].matches("E[aprldv]*")
								|| cellTagChunks[c].matches("MS[aprldv]*")){
							mi = new JMenuItem(cellName);
							mi.addActionListener(ij);
							ancestryPopup.add(mi);

							for (int a=1; a<=cellTagChunks[c].length();a++) {
								String candidate = cellTagChunks[c].substring(0, cellTagChunks[c].length()+1-a);

								mi = new JMenuItem("descended from "+candidate);
								popupInfo[1] = popupInfo[1]+"descended from "+candidate+"\n";

								mi.addActionListener(ij);
								ancestryPopup.add(mi);

								int[] openImageIDsWithRM = WindowManager.getIDList();
								for (int o=0;o< openImageIDsWithRM.length;o++) {

									Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
									for (int j = 0; j < imgRoiNames.length; j++) {
										if (((String) imgRoiNames[j]).replace("\"","").trim().startsWith(candidate+" ")) {
											String itemString = "***shown here: "
													+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
											j = imgRoiNames.length;
											mi = new JMenuItem(itemString.split("_")[0] + " in " 
													+ (WindowManager.getImage(
													openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
															(WindowManager.getImage(
																	openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																		(WindowManager.getImage(
																				openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
											mi.setToolTipText(itemString);
											mi.setActionCommand(itemString);

											mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
											popupInfo[1] = popupInfo[1] + itemString+"\n";

											mi.addActionListener(ij);
											ancestryPopup.add(mi);
										}
									}
								}
								if (candidate.toUpperCase() == candidate) {
									a=cellTagChunks[c].length();
									continue;
								}
							}
						} else {
							for (int l = 0; l < logLines2.length; l++) {
								logLines2[l] = logLines2[l]
										.replace("</a></td><td>", ":")
										.replace("</td><td>", ";")
										.replaceAll("\\.", "");
								if (logLines2[l].startsWith("begets ")
										&& logLines2[l].toLowerCase().matches(".*" + cellName.toLowerCase()+ ":.*")) {
									mi = new JMenuItem(cellName);
									mi.addActionListener(ij);
									ancestryPopup.add(mi);

									c=cellTagChunks.length;
									String[] logLineChunks = logLines2[l].split(":");
									String[] lineageNameChunks = logLineChunks[1].split(";");
									//									MenuItem miFake = new MenuItem("*"+lineageNameChunks[0]);
									//									ancestryPopup.add(miFake);

									for (int a=1; a<=lineageNameChunks[0].length();a++) {
										String candidate = lineageNameChunks[0].substring(0, lineageNameChunks[0].length()+1-a);

										mi = new JMenuItem("descended from "+candidate);
										popupInfo[1] = popupInfo[1]+"descended from "+candidate+"\n";

										mi.addActionListener(ij);
										ancestryPopup.add(mi);

										int[] openImageIDsWithRM = WindowManager.getIDList();
										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").trim().startsWith(candidate+" ")) {
													String itemString = "***shown here: "
															+ imgRoiNames[j]+"{"+ openImageIDsWithRM[o] + "|" + j + "}";
													j = imgRoiNames.length;
													mi = new JMenuItem(itemString.split("_")[0] + " in " 
															+ (WindowManager.getImage(
															openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																	(WindowManager.getImage(
																			openImageIDsWithRM[o]).getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																				(WindowManager.getImage(
																						openImageIDsWithRM[o]).getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
													mi.setToolTipText(itemString);
													mi.setActionCommand(itemString);

													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+itemString+"\n";

													mi.addActionListener(ij);
													ancestryPopup.add(mi);
												}
											}
										}

										if (candidate.toUpperCase() == candidate) {
											a=lineageNameChunks[0].length();
											continue;
										}
									}

								}
							}
						}

					}
					popup.add(ancestryPopup);
				}

				ImagePlus dataImp = brainbowSelection?rm.getImagePlus():imp.getMotherImp();
				if (getFates && (dataImp.getStack() instanceof MultiQTVirtualStack)) {
					//popup.add(new JMenuItem("-"));
					JMenu relatedDataPopup = new JMenu(cellName+": Related Data Sets >", true);
					relatedDataPopup.getPopupMenu().addPopupMenuListener(ij);
					mi =  new JMenuItem("^--------------------^");
					mi.addActionListener(ij);
					relatedDataPopup.add(mi);
					popupInfo[1] = popupInfo[1] + "\n"+dataImp.getTitle()+"\n";
					popupInfo[1] = popupInfo[1] + "\nRelated Data Sets >\n";
					relatedDataPopup.add(dataImp.getTitle());
					String oldLog = IJ.getLog();

					String[] fileList = null;
					//					String[] fileList = ((MultiQTVirtualStack)imp.getStack()).getVirtualStack(0).getMovieFile().getParentFile().list();
					IJ.runMacro(""
							+ "string = File.openUrlAsString(\"http://www.gloworm.org/\");"
							+ "print(string);");
					String glowormHomePage = IJ.getLog();
					IJ.log("\\Clear");
					IJ.log(oldLog);

					for (int m=0; m<dataImp.getNChannels(); m++) {
						String movieGeneList = "";
						String[] movieGeneNames = null;
						String glowGeneLog ="";
						if (((MultiQTVirtualStack)dataImp.getStack()).getVirtualStack(m)!=null)
							movieGeneNames = ((MultiQTVirtualStack)dataImp.getStack()).getVirtualStack(m).getMovieName()
							.toLowerCase().split("(%(.fp)?)");
						else
							movieGeneNames = new String[]{""};
						for (int v =0; v<movieGeneNames.length-1;v++) {
							movieGeneNames[v] = movieGeneNames[v].replace("+", "");
							movieGeneList= movieGeneList + (movieGeneList==""?" ":", ") + movieGeneNames[v];

							String[] glowormLogLines = glowormHomePage.toLowerCase().split("\n");
							String matchString = "";
							for (int g=0;g<glowormLogLines.length;g++) {
								if (glowormLogLines[g].contains("fusion") && glowormLogLines[g].contains(movieGeneNames[v]) && glowormLogLines[g].contains("http://") && glowormLogLines[g].contains(".html'>")  ){
									matchString = glowormLogLines[g].substring(glowormLogLines[g].indexOf("http://"), glowormLogLines[g].indexOf(".html'>")+5);
									IJ.runMacro(""
											+ "string = File.openUrlAsString(\""+matchString+"\");"
											+ "print(string);");
									String glowormGenePage = IJ.getLog();
									IJ.log("\\Clear");
									IJ.log(oldLog);

									glowGeneLog = glowGeneLog + glowormGenePage.replace("%25", "%").replace("%2B", "+");
								}
							}
						}

						relatedDataPopup.add("Data portraying "+ movieGeneList + ":");
						fileList = glowGeneLog.split("\n");

						JMenu samePopup = new JMenu(cellName+": Current specimen >", true);
						samePopup.getPopupMenu().addPopupMenuListener(ij);
						mi =  new JMenuItem("^--------------------^");
						mi.addActionListener(ij);
						samePopup.add(mi);
						JMenu diffPopup = new JMenu(cellName+": More specimens >", true);
						diffPopup.getPopupMenu().addPopupMenuListener(ij);
						mi =  new JMenuItem("^--------------------^");
						mi.addActionListener(ij);
						diffPopup.add(mi);
						JMenu[] geneExprPopups = new JMenu[movieGeneNames.length];
						for (int e1=0; e1<movieGeneNames.length-1; e1++) {
							geneExprPopups[e1] = new JMenu(movieGeneNames[e1], true);
							geneExprPopups[e1].getPopupMenu().addPopupMenuListener(ij);
							mi =  new JMenuItem("^--------------------^");
							mi.addActionListener(ij);
							geneExprPopups[e1].add(mi);
							diffPopup.add(geneExprPopups[e1]);
						}

						for (int f=0; f<fileList.length;f++) {
							if (fileList[f].contains("http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE=") 
									&& 
									(fileList[f].toLowerCase().contains( ((MultiQTVirtualStack)dataImp.getStack()).getVirtualStack(m).getMovieFile().getName()
											.toLowerCase().replaceAll("(_slc.*|_pr.*)", ""))) ) {
								mi = new JMenuItem("movie " + fileList[f].substring(fileList[f].indexOf("\">")+2, 
										fileList[f].indexOf("</a>")));
								popupInfo[1] = popupInfo[1]+"movie "+fileList[f].substring(fileList[f].indexOf("\">")+2, 
										fileList[f].indexOf("</a>")) +"...\n"
										+"            "+ fileList[f].substring(fileList[f].indexOf("http://"), 
												fileList[f].indexOf("\">")) + "\n";
								mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/CytoSHOW3icon.png")));
								mi.addActionListener(ij);
								samePopup.add(mi);
								if (fileList[f].toLowerCase().contains("also viewable in ")){
									mi = new JMenuItem("movie "+ fileList[f].substring(fileList[f].indexOf("\">")+2, 
											fileList[f].indexOf("</a>")) +" also viewable in RedCyan Stereo");
									popupInfo[1] = popupInfo[1] +"      "+ "--also viewable in RedCyan Stereo"  +"...\n"
											+"            "+ fileList[f].substring(fileList[f].indexOf("http://"), 
													fileList[f].indexOf("\">")) + "&amp;VIEW=MQTVS_RedCyanStereo_scene.scn" + "\n"; 
									mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/CytoSHOW3icon.png")));
									mi.addActionListener(ij);
									samePopup.add(mi);

								}
							}else if (fileList[f].contains("http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE=") 
									&& fileList[f].toLowerCase().contains(".mov")){
								for (int e1=0; e1<movieGeneNames.length; e1++) {
									if (fileList[f].toLowerCase().contains(movieGeneNames[e1].toLowerCase().trim())){
										mi = new JMenuItem("movie " + fileList[f].substring(fileList[f].indexOf("\">")+2, 
												fileList[f].indexOf("</a>")));
										popupInfo[1] = popupInfo[1]+"movie "+fileList[f].substring(fileList[f].indexOf("\">")+2, 
												fileList[f].indexOf("</a>")) +"...\n"
												+"            "+ fileList[f].substring(fileList[f].indexOf("http://"), 
														fileList[f].indexOf("\">")) + "\n";
										mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/CytoSHOW3icon.png")));
										mi.addActionListener(ij);
										geneExprPopups[e1].add(mi);
										if (fileList[f].toLowerCase().contains("also viewable in ")){
											mi = new JMenuItem("movie "+ fileList[f].substring(fileList[f].indexOf("\">")+2, 
													fileList[f].indexOf("</a>")) +" also viewable in RedCyan Stereo");
											popupInfo[1] = popupInfo[1] +"      "+ "--also viewable in RedCyan Stereo"  +"...\n"
													+"            "+ fileList[f].substring(fileList[f].indexOf("http://"), 
															fileList[f].indexOf("\">")) + "&amp;VIEW=MQTVS_RedCyanStereo_scene.scn" + "\n"; 
											mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/CytoSHOW3icon.png")));
											mi.addActionListener(ij);
											geneExprPopups[e1].add(mi);

										}

									}
								}
							}

						}
						relatedDataPopup.add(samePopup);
						relatedDataPopup.add(diffPopup);

					}
					popup.add(relatedDataPopup);
					//					IJ.log("xxx "+cellName);
				}



				//popup.add(new JMenuItem("-"));

				JMenu functionPopup = new JMenu("CytoSHOW Functions >", true);
				functionPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Tools.png")));
				functionPopup.getPopupMenu().addPopupMenuListener(ij);
				mi =  new JMenuItem("^--------------------^");
				mi.addActionListener(ij);
				functionPopup.add(mi);
				for (int k = 0; k < standardmis.length; k++) {
					functionPopup.add(standardmis[k]);
				}
				popup.add(functionPopup);

				if(popupInfo[0] == "")
					popupInfo[0] = dataImp.getTitle();

				mi = new JMenuItem("Save this info in text file...");
				mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Write.png")));
				mi.addActionListener(ij);
				//popup.add(new JMenuItem("-"));
				popup.add(mi);

			}
			if (brainbowSelection && e.getSource() instanceof Checkbox && !sketchyMQTVS) {
				colorLegend.add(popup);
			} else if (ij3dSelection) { 
			} else {
//				imp.getWindow().add(popup);
			}
			if (IJ.isMacOSX()) IJ.wait(10);
			if (IJ.isLinux()) IJ.wait(10);

			popup.setLightWeightPopupEnabled(false);
			popup.show((Component)e.getSource(), x, y);
			if (e.getSource() instanceof Canvas)
				((Canvas)e.getSource()).setCursor(defaultCursor);
			else 
				((JComponent)e.getSource()).setCursor(defaultCursor);


		}
	}

	public void mouseExited(MouseEvent e) {
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null) {
			tool.mouseExited(imp, e);
			if (e.isConsumed()) return;
		}
		//autoScroll(e);
		ImageWindow win = imp.getWindow();
		if (win!=null)
			setCursor(defaultCursor);
		IJ.showStatus("");
		mouseExited = true;
	}

	/*
	public void autoScroll(MouseEvent e) {
		Roi roi = imp.getRoi();
		if (roi==null || roi.getState()!=roi.CONSTRUCTING || srcRect.width>=imageWidth || srcRect.height>=imageHeight
		|| !(roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE || roi.getType()==Roi.ANGLE))
			return;
		int sx = e.getX();
		int sy = e.getY();
		xMouseStart = srcRect.x+srcRect.width/2;
		yMouseStart = srcRect.y+srcRect.height/2;
		Rectangle r = roi.getBounds();
		Dimension size = getSize();
		int deltax=0, deltay=0;
		if (sx<0)
			deltax = srcRect.width/4;
		else if (sx>size.width)
			deltax = -srcRect.width/4;
		if (sy<0)
			deltay = srcRect.height/4;
		else if (sy>size.height)
			deltay = -srcRect.height/4;
		//IJ.log("autoscroll: "+sx+" "+sy+" "+deltax+" "+deltay+" "+r);
		scroll(screenX(xMouseStart+deltax), screenY(yMouseStart+deltay));
	}
	 */

	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();
		setXMouse(offScreenX(x));
		setYMouse(offScreenY(y));
		Point p = this.getCursorLoc();
		flags = e.getModifiers();
		//		mousePressedX = mousePressedY = -1;
		//IJ.log("mouseDragged: "+flags);
		Roi roi = imp.getRoi();
		if (flags==0)  // workaround for Mac OS 9 bug
			flags = InputEvent.BUTTON1_MASK;
		if (Toolbar.getToolId()==Toolbar.HAND || IJ.spaceBarDown())
			if (roi != null ) {
				if (roi.contains(previousP.x, previousP.y)){
					Rectangle b = roi.getBounds();
					roi.handleMouseDrag(x, y, flags);
				} else {
					scroll(x, y);
				}			
			} else {
				scroll(x, y);
			}
		else {
			PlugInTool tool = Toolbar.getPlugInTool();
			if (tool!=null) {
				tool.mouseDragged(imp, e);
				if (e.isConsumed()) return;
			}
			IJ.setInputEvent(e);
			if (roi != null){
				//				IJ.log("NOT NULL");
				//				if (roi instanceof TextRoi)
				//					roi.state = roi.MOVING;
				roi.handleMouseDrag(x, y, flags);
			}

		}
		previousP = p;
		previousT = new Date().getTime();

		int theta = (int)Math.round(Math.random()*360);
		double thetarad = theta * Math.PI/180.0;
		int BIGPOWEROF2 = 8192;
		int costheta = (int)(BIGPOWEROF2*Math.cos(thetarad) + 0.5);
		int sintheta = (int)(BIGPOWEROF2*Math.sin(thetarad) + 0.5);

//		Projector.projectCursorAroundX(theta*imp.getNSlices()/360, imp.getNSlices(), (int)imp.getCanvas().getCursorLoc().y, imp.getNSlices()/2, imp.getWidth(), imp.getHeight(), costheta, sintheta);
	}

	protected void handleRoiMouseDown(MouseEvent e) {
		int sx = e.getX();
		int sy = e.getY();
		int ox = offScreenX(sx);
		int oy = offScreenY(sy);
		Roi roi = imp.getRoi();
		int handle = roi!=null?roi.isHandle(sx, sy):-1;
		boolean multiPointMode = roi!=null && (roi instanceof PointRoi) && handle==-1
				&& Toolbar.getToolId()==Toolbar.POINT && Toolbar.getMultiPointMode();
		if (multiPointMode) {
			imp.setRoi(((PointRoi)roi).addPoint(offScreenXD(sx), offScreenYD(sy)));
			return;
		}
		setRoiModState(e, roi, handle);
		if (roi!=null) {
			if (handle>=0) {
				roi.mouseDownInHandle(handle, sx, sy);
				return;
			}
			Rectangle r = roi.getBounds();
			int type = roi.getType();
			if (type==Roi.RECTANGLE && r.width==imp.getWidth() && r.height==imp.getHeight()
					&& roi.getPasteMode()==Roi.NOT_PASTING && !(roi instanceof ImageRoi)) {
				imp.deleteRoi();
				return;
			}
			if (roi.contains(ox, oy)) {
				if (roi.modState==Roi.NO_MODS)
					roi.handleMouseDown(sx, sy);
				else {
					imp.deleteRoi();
					imp.createNewRoi(sx,sy);
				}
				return;
			}
			if ((type==Roi.POLYGON || type==Roi.POLYLINE || type==Roi.ANGLE)
					&& roi.getState()==roi.CONSTRUCTING)
				return;
			int tool = Toolbar.getToolId();
			if ((tool==Toolbar.POLYGON||tool==Toolbar.POLYLINE||tool==Toolbar.ANGLE)&& !(IJ.shiftKeyDown()||IJ.altKeyDown())) {
				imp.deleteRoi();
				return;
			}
		}
		imp.createNewRoi(sx,sy);
	}

	void setRoiModState(MouseEvent e, Roi roi, int handle) {
		if (roi==null || (handle>=0 && roi.modState==Roi.NO_MODS))
			return;
		if (roi.state==Roi.CONSTRUCTING)
			return;
		int tool = Toolbar.getToolId();
		if (tool>Toolbar.FREEROI && tool!=Toolbar.WAND && tool!=Toolbar.POINT)
		{roi.modState = Roi.NO_MODS; return;}
		if (e.isShiftDown())
			roi.modState = Roi.ADD_TO_ROI;
		else if (e.isAltDown())
			roi.modState = Roi.SUBTRACT_FROM_ROI;
		else
			roi.modState = Roi.NO_MODS;
		//IJ.log("setRoiModState: "+roi.modState+" "+ roi.state);
	}

	/** Disable/enable popup menu. */
	public void disablePopupMenu(boolean status) {
		disablePopupMenu = status;
	}

	public void setShowAllList(Overlay showAllList) {
		this.showAllOverlay = showAllList;
		labelShapes = null;
	}

	//	public Overlay getShowAllList() {
	//		return showAllOverlay;
	//	}

	/** Enables/disables the Tag Manager "Show All" mode. */
	public void setShowAllROIs(boolean showAllROIs) {
		this.showAllROIs = showAllROIs;
	}

	/** Returns the state of the Tag Manager "Show All" flag. */
	public boolean getShowAllROIs() {
		return showAllROIs;
	}

	/** Return the Tag Manager "Show All" list as an overlay. */
	public Overlay getShowAllList() {
		if (!showAllROIs) return null;
		if (showAllList!=null) return showAllList;
		RoiManager rm=  imp.getRoiManager();
		if (rm==null) return null;
		Roi[] rois = rm.getShownRoisAsArray();
		if (rois.length==0) return null;
		Overlay overlay = new Overlay();
		for (int i=0; i<rois.length; i++)
			overlay.add((Roi)rois[i].clone());
		return overlay;
	}

	/** Returns the color used for "Show All" mode. */
	public static Color getShowAllColor() {
		if (showAllColor!=null && showAllColor.getRGB()==0xff80ffff)
			showAllColor = Color.cyan;
		return showAllColor;
	}

	/** Sets the color used used for the Tag Manager "Show All" mode. */
	public static void setShowAllColor(Color c) {
		if (c==null) return;
		showAllColor = c;
		labelColor = null;
		ImagePlus img = WindowManager.getCurrentImage();
		if (img!=null) {
			ImageCanvas2 ic = img.getCanvas();
			if (ic!=null && ic.getShowAllROIs()) img.draw();
		}
	}

	/** Experimental */
	public static void setCursor(Cursor cursor, int type) {
		crosshairCursor = cursor;
	}

	/** Use ImagePlus.setOverlay(ij.gui.Overlay). */
	public void setOverlay(Overlay overlay) {
		this.overlay = overlay;
		paintDoubleBuffered(getGraphics());
	}

	/** Use ImagePlus.getOverlay(). */
	public Overlay getOverlay() {
		return overlay;
	}

	/**
	 * @deprecated
	 * replaced by ImagePlus.setOverlay(ij.gui.Overlay)
	 */
	public void setDisplayList(Vector list) {
		if (list!=null) {
			Overlay list2 = new Overlay();
			list2.setVector(list);
			setOverlay(list2);
		} else
			setOverlay(null);
		if (overlay!=null)
			overlay.drawLabels(overlay.size()>0&&overlay.get(0).getStrokeColor()==null);
		else
			customRoi = false;
		paintDoubleBuffered(getGraphics());
	}

	/**
	 * @deprecated
	 * replaced by ImagePlus.setOverlay(Shape, Color, BasicStroke)
	 */
	public void setDisplayList(Shape shape, Color color, BasicStroke stroke) {
		if (shape==null)
		{setOverlay(null); return;}
		Roi roi = new ShapeRoi(shape);
		roi.setStrokeColor(color);
		roi.setStroke(stroke);
		Overlay list = new Overlay();
		list.add(roi);
		setOverlay(list);
	}

	/**
	 * @deprecated
	 * replaced by ImagePlus.setOverlay(Roi, Color, int, Color)
	 */
	public void setDisplayList(Roi roi, Color color) {
		roi.setStrokeColor(color);
		Overlay list = new Overlay();
		list.add(roi);
		setOverlay(list);
	}

	/**
	 * @deprecated
	 * replaced by ImagePlus.getOverlay()
	 */
	public Vector getDisplayList() {
		if (overlay==null) return null;
		Vector displayList = new Vector();
		for (int i=0; i<overlay.size(); i++)
			displayList.add(overlay.get(i));
		return displayList;
	}

	/** Allows plugins (e.g., Orthogonal_Views) to create a custom ROI using a display list. */
	public void setCustomRoi(boolean customRoi) {
		this.customRoi = customRoi;
	}

	public boolean getCustomRoi() {
		return customRoi;
	}

	/** Called by IJ.showStatus() to prevent status bar text from
		being overwritten until the cursor moves at least 12 pixels. */
	public void setShowCursorStatus(boolean status) {
		showCursorStatus = status;
		if (status==true)
			sx2 = sy2 = -1000;
		else {
			sx2 = screenX(getXMouse());
			sy2 = screenY(getYMouse());
		}
	}

	public void mouseReleased(MouseEvent e) {
		//		int ox = offScreenX(e.getX());
		//		int oy = offScreenY(e.getY());
		//		if (overlay!=null && ox==mousePressedX && oy==mousePressedY
		//		&& (System.currentTimeMillis()-mousePressedTime)>250L) {
		//			if (activateOverlayRoi(ox,oy))
		//				return;
		//		}
		//
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null) {
			tool.mouseReleased(imp, e);
//			if (e.isConsumed()) return;
		}
		flags = e.getModifiers();
		flags &= ~InputEvent.BUTTON1_MASK; // make sure button 1 bit is not set
		flags &= ~InputEvent.BUTTON2_MASK; // make sure button 2 bit is not set
		flags &= ~InputEvent.BUTTON3_MASK; // make sure button 3 bit is not set
		Roi roi = imp.getRoi();
		if (roi != null) {
			Rectangle r = roi.getBounds();
			int type = roi.getType();
			if (!e.isConsumed()) {

				if ((r.width==0 || r.height==0)
						&& !(type==Roi.POLYGON||type==Roi.POLYLINE||type==Roi.ANGLE||type==Roi.LINE)
						&& !(roi instanceof TextRoi)
						&& roi.getState()==roi.CONSTRUCTING
						&& type!=roi.POINT)
					imp.deleteRoi();
				else {
					roi.handleMouseUp(e.getX(), e.getY());
					if (roi.getType()==Roi.LINE && roi.getLength()==0.0)
						imp.deleteRoi();
				}
			} else {
				while(imp.blinkOn)
					IJ.wait(10);
				imp.setSchfut(null);
				imp.setRoi(imp.getRoi());
			}
		}
	}

	private boolean activateOverlayRoi(int ox, int oy) {
		int currentImage = -1;
		if (imp.getStackSize()>1)
			currentImage = imp.getCurrentSlice();
		int channel=0, slice=0, frame=0;
		boolean hyperstack = imp.isHyperStack();
		if (hyperstack) {
			channel = imp.getChannel();
			slice = imp.getSlice();
			frame = imp.getFrame();
		}
		Overlay o = overlay;
		for (int i=o.size()-1; i>=0; i--) {
			Roi roi = o.get(i);
			//IJ.log(".isAltDown: "+roi.contains(ox, oy));
			if (roi.contains(ox, oy)) {
				if (hyperstack && roi.getPosition()==0) {
					int c = roi.getCPosition();
					int z = roi.getZPosition();
					int t = roi.getTPosition();
					if (!((c==0||c==channel) && (z==0||z==slice) && (t==0||t==frame)))
						continue;
				} else {
					int position = roi.getPosition();
					if (!(position==0||position==currentImage))
						continue;
				}
				roi.setImage(null);
				imp.setRoi(roi);
				return true;
			}
		}
		return false;
	}


	public void mouseMoved(MouseEvent e) {
		//if (ij==null) return;
		if (this.getImage().getWindow().modeButton != null && e.getSource() == this.getImage().getWindow().modeButton) {
			this.getImage().getWindow().modeButtonPanel.setVisible(true);
		}
		if (e.getSource() == this) {
			int sx = e.getX();
			int sy = e.getY();
			int ox = offScreenX(sx);
			int oy = offScreenY(sy);
			flags = e.getModifiers();
			setCursor(sx, sy, ox, oy);
			//		mousePressedX = mousePressedY = -1;
			IJ.setInputEvent(e);
			PlugInTool tool = Toolbar.getPlugInTool();
			if (tool!=null) {
				tool.mouseMoved(imp, e);
				if (e.isConsumed()) return;
			}
			Roi roi = imp.getRoi();
			if (roi!=null && (roi.getType()==Roi.POLYGON || roi.getType()==Roi.POLYLINE || roi.getType()==Roi.ANGLE) 
					&& roi.getState()==Roi.CONSTRUCTING) {
				PolygonRoi pRoi = (PolygonRoi)roi;
				pRoi.setImage(this.getImage());
				pRoi.handleMouseMove(sx, sy);
			} else {
				if (ox<imageWidth && oy<imageHeight) {
					ImageWindow win = imp.getWindow();
					// Cursor must move at least 12 pixels before text
					// displayed using IJ.showStatus() is overwritten.
					if ((sx-sx2)*(sx-sx2)+(sy-sy2)*(sy-sy2)>144)
						showCursorStatus = true;
					if (win!=null&&showCursorStatus) win.mouseMoved(ox, oy);
				} else
					IJ.showStatus("");
			}

			if ( !(sketchyMQTVS || imp.getTitle().startsWith("Sketch3D") || (imp.getWindow().getTitle().matches(".*[XY]Z +\\d+ Sketch3D.*")))) {
				RoiManager rm = imp.getRoiManager();
				if (rm == null)
					return;
				Hashtable<String, Roi> rois = rm.getROIs();
				DefaultListModel<String> listModel = rm.getListModel();
				if (labelShapes == null || labelShapes.size() == 0) {
					labelShapes = new Hashtable<Roi,ShapeRoi>();
					
				}
				int n = labelShapes.size();
//				if (getLabelShapes() == null || getLabelShapes().size() != n)
//					return;
				String cursorString = null;

				if (sliceRois == null || sliceRois.size() == 0 || labelShapes.size() == 0) {
					String rbnString = ""+(imp.getChannel()>0?imp.getChannel():1)+"_"+(imp.getSlice())+"_"+imp.getFrame();
					String rbnCh0String = ""+0+"_"+imp.getSlice()+"_"+imp.getFrame();

					sliceRois = new ArrayList<Roi>();
					if(rm.getROIsByNumbers().get(rbnString)!=null) {
						labelShapes.clear();
						sliceRois.addAll(rm.getROIsByNumbers().get(rbnString));
					}
					if(rm.getROIsByNumbers().get(rbnCh0String)!=null) {
						labelShapes.clear();
						sliceRois.addAll(rm.getROIsByNumbers().get(rbnCh0String));
					}
				}
				if (sliceRois == null || sliceRois.size() == 0)
					return;
				Roi[] sliceRoisArray = new Roi[sliceRois.size()];
				for (int sr=0;sr<sliceRoisArray.length;sr++) {
					sliceRoisArray[sr] = ((Roi)sliceRois.get(sr));
					Roi slcRoi = sliceRoisArray[sr];
					if (slcRoi instanceof ShapeRoi) {
						labelShapes.put(slcRoi, (ShapeRoi)slcRoi);
					}else{
						labelShapes.put(slcRoi,(slcRoi instanceof Arrow?((Arrow)slcRoi).getShapeRoi():new ShapeRoi(slcRoi)));
						if (labelShapes!=null && slcRoi!= null)
							if (labelShapes.get(slcRoi) != null) {
								labelShapes.get(slcRoi).setName(slcRoi.getName());
							}
					}

				}
				for (int i=0; i<sliceRoisArray.length; i++) {  
					if (sliceRoisArray[i] instanceof Arrow
							&& labelShapes.get(sliceRoisArray[i]) != null
							&& labelShapes.get(sliceRoisArray[i]).contains(ox, oy)
					/* && showAllROIs */) {
						cursorString = sliceRoisArray[i].getName().split("[\"|=]")[1];
						i = sliceRoisArray.length;
					}
				}
				if (cursorString == null /* && showAllROIs */) {
					for (Roi slcRoi:sliceRoisArray) {  
						if (labelShapes.get(slcRoi) != null 
     								&& labelShapes.get(slcRoi).contains(ox, oy)
								&& slcRoi.getName().split("[\"|=]").length > 1) {
							if (imp.getRoiManager().getListModel().indexOf(slcRoi.getName()) >= 0) {
								cursorString = slcRoi.getName().split("[\"|=]")[1];
								break;
							}
						}
					}
				}
				Graphics g = getGraphics();
				if (cursorString != null) {
					if (IJ.isWindows()) {
						//				IJ.log(cursorString);
						try {
							cursorRoi = null;
							paint(g);
							cursorRoi = new TextRoi(getXMouse()+10/ getMagnification(), getYMouse(), cursorString);
							((TextRoi) cursorRoi).setCurrentFont(g.getFont().deriveFont((float) (16 / getMagnification())));
							cursorRoi.setStrokeColor(Color.black);
							cursorRoi.setFillColor(Colors.decode("#99ffffff",
									getDefaultColor()));
							cursorRoi.setLocation(((int) (getXMouse()> getSrcRect().getX()+cursorString.length()*4/ getMagnification()?(getXMouse()< getSrcRect().getMaxX()-cursorString.length()*4.5/ getMagnification()?getXMouse()- cursorString.length()*4/ getMagnification():getXMouse()- cursorString.length()*9/ getMagnification()):getXMouse()))
									, getYMouse()<getSrcRect().getMaxY()-40/getMagnification()?((int)(getYMouse()+20/ getMagnification())):((int)(getYMouse()-35/ getMagnification())));
							drawRoi(g, cursorRoi, -1);
						} finally {
						}
					} else {
						Toolkit tk = Toolkit.getDefaultToolkit();
						Font font = Font.decode("Arial-Outline-18");

						//create the FontRenderContext object which helps us to measure the text
						FontRenderContext frc = new FontRenderContext(null, false, false);

						//get the height and width of the text
						Rectangle2D bounds = font.getStringBounds(cursorString, frc);
						int w = (int) bounds.getWidth();
						int ht = (int) bounds.getHeight();
						Image img = new BufferedImage(w, ht+8, BufferedImage.TYPE_INT_ARGB_PRE);

						//				img.getGraphics().setColor(Colors.decode("00000000", Color.white));
						Graphics2D g2d = (Graphics2D) img.getGraphics();

						g2d.setFont(font);

						g2d.setColor(Colors.decode("#99ffffff",Color.gray));
						g2d.fillRect(0, 0, w, ht+8);
						g2d.setColor(Color.black);
						g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
						g2d.drawLine(0, 0, 2, 7);
						g2d.drawLine(0, 0, 7, 2);
						g2d.drawLine(0, 0, 8, 8);
						g2d.drawString(cursorString, 1, img.getHeight(null)-6);
						this.setCursor(tk.createCustomCursor(img,new Point(0,0),"labelCursor"));
					}
				} else {
					if (!IJ.isMacOSX()) {
						cursorRoi = null;
						paint(g);
					}
				}
			}else if (imp.getMotherImp().getRoiManager().getColorLegend() != null){
				//			IJ.showStatus("bling");
				Graphics g = getGraphics();
				JCheckBox cursorCB = imp.getMotherImp().getRoiManager().getColorLegend().getChosenCB();
				if (cursorCB != null) {
					String cursorString = cursorCB.getName();
					if (IJ.isWindows()) {
						cursorRoi = new TextRoi((getXMouse()-cursorCB.getWidth()/2)/ getMagnification(), getYMouse(), cursorString);
						((TextRoi) cursorRoi).setCurrentFont(g.getFont().deriveFont((float) (16 / getMagnification())));
						try {
							paint(g);
							cursorRoi.setStrokeColor(Color.black);
							//					IJ.log("#99"+Integer.toHexString(imp.getProcessor().get(xMouse,yMouse)).substring(2));
							//					cursorRoi.setFillColor(Colors.decode("#99"+Integer.toHexString(imp.getProcessor().get(xMouse,yMouse)).substring(2),
							//							getDefaultColor()));
							cursorRoi.setFillColor(Colors.decode("#99ffffff",
									getDefaultColor()));
							((TextRoi)cursorRoi).updateBounds(g);
							cursorRoi.setLocation(((int) (getXMouse()> getSrcRect().getX()+cursorString.length()*4/ getMagnification()?(getXMouse()< getSrcRect().getMaxX()-cursorString.length()*4.5/ getMagnification()?getXMouse()- cursorString.length()*4/ getMagnification():getXMouse()- cursorString.length()*9/ getMagnification()):getXMouse()))
									, getYMouse()<getSrcRect().getMaxY()-40/getMagnification()?((int)(getYMouse()+20/ getMagnification())):((int)(getYMouse()-35/ getMagnification())));
							drawRoi(g, cursorRoi, -1);
						} finally {
						}
					} else {
						Toolkit tk = Toolkit.getDefaultToolkit();
						Font font = Font.decode("Arial-Outline-18");

						//create the FontRenderContext object which helps us to measure the text
						FontRenderContext frc = new FontRenderContext(null, false, false);

						//get the height and width of the text
						Rectangle2D bounds = font.getStringBounds(cursorString, frc);
						int w = (int) bounds.getWidth();
						int ht = (int) bounds.getHeight();
						Image img = new BufferedImage(w, ht+8, BufferedImage.TYPE_INT_ARGB_PRE);

						//				img.getGraphics().setColor(Colors.decode("00000000", Color.white));
						Graphics2D g2d = (Graphics2D) img.getGraphics();

						g2d.setFont(font);

						g2d.setColor(Colors.decode("#99ffffff",Color.gray));
						g2d.fillRect(0, 0, w, ht+8);
						g2d.setColor(Color.black);
						g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
						g2d.drawLine(0, 0, 2, 7);
						g2d.drawLine(0, 0, 7, 2);
						g2d.drawLine(0, 0, 8, 8);
						g2d.drawString(cursorString, 1, img.getHeight(null)-6);
						this.setCursor(tk.createCustomCursor(img,new Point(0,0),"labelCursor"));
					}
				} else {
					if (!IJ.isMacOSX()) {
						cursorRoi = null;
						paint(g);
					}
				}
			}
		}
	}

	public void mouseEntered(MouseEvent e) {
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null)
			tool.mouseEntered(imp, e);

//		IJ.runMacro("print(\"\\\\Clear\")");
//		IJ.runMacro("print(\"\\\\Update:CytoSHOW Movie Window:\\\nLeft-Clicking on any tag will bring the linked image feature into focus.\\\nDouble-clicking will launch web-links to related information:\\\nDouble-click => WormBase.org \\\nShift-double-click => Google.com\\\nControlOption-double-click => WormAtlas.org \\\nShift-ControlOption-double-click => Textpresso C.elegans \\\nRight-Click => Shortcuts to CytoSHOW Functions, including cell-search in other open movies, \\\nexpressed genes with links to WormBase gene pages, \\\ncell fates, ancestries, and interactions with links to web resources \\\n \")");

	}

	public void mouseClicked(MouseEvent e) {

		showCursorStatus = true;
		int toolID = Toolbar.getToolId();
		int toolAlt = Toolbar.getOvalToolType();
		ImageWindow win = imp.getWindow();
		
		if (win!=null && (win.running2 || win.running3) && toolID!=Toolbar.MAGNIFIER) {
			if (win instanceof StackWindow) {
				((StackWindow)win).setAnimate(false);
				((StackWindow)win).setZAnimate(false);
			} else
				win.running2 = win.running3 = false;
			return;
		}

		int x = e.getX();
		int y = e.getY();

		int ox = offScreenX(x);
		int oy = offScreenY(y);
		setXMouse(ox); setYMouse(oy);
		PlugInTool tool = Toolbar.getPlugInTool();

		boolean doubleClick =  (e.getClickCount() >1);
		//		if (cursorRoi != null) {
		if (this.getCursor().getType() == Cursor.CUSTOM_CURSOR) {
			if (doubleClick) {
				Roi roi = imp.getRoiManager().getSelectedRoisAsArray()[0];

				if (roi != null) 
					currentRoi = roi;
				//			if (doubleClick && roi instanceof TextRoi) 
				//				((TextRoi)roi).searchWormbase();
				if (roi instanceof Roi) {
					//				IJ.setKeyDown(KeyEvent.VK_ALT);
					//				IJ.setKeyDown(KeyEvent.VK_SHIFT);

					TextRoi fakeTR = new TextRoi(0,0,imp.getRoiManager().getSelectedRoisAsArray()[0]
							.getName().split("[\"|=]")[1].trim());
//					fakeTR.searchWormbase();
					
					//				IJ.setKeyUp(KeyEvent.VK_ALT);
					//				IJ.setKeyUp(KeyEvent.VK_SHIFT);

				}
			}
		}
		Roi impRoi = imp.getRoi();
		if ( roiManagerSelect(ox, oy)) {
			if (doubleClick) {
				Roi roi = imp.getRoiManager().getSelectedRoisAsArray()[0];

				if (roi != null) 
					currentRoi = roi;
				//			if (doubleClick && roi instanceof TextRoi) 
				//				((TextRoi)roi).searchWormbase();
				if (roi instanceof Roi) {
					imp.getRoiManager().select(imp.getRoiManager().getListModel().indexOf(roi.getName()));
					new Zoom().run("to");
				}
			} else {
				Roi roi = imp.getRoiManager().getSelectedRoisAsArray()[0];
				imp.getRoiManager().select(imp.getRoiManager().getListModel().indexOf(roi.getName()));
			}
			return;
		}
		
		if (tool!=null)
			tool.mouseClicked(imp, e);
		if (imp.getRemoteMQTVSHandler() != null) {
			IJ.run(imp, "This Slice", "");			
		}
		double theta = Math.random()*360;
		double thetarad = theta * Math.PI/180.0;
		int BIGPOWEROF2 = 8192;
		int costheta = (int)(BIGPOWEROF2*Math.cos(thetarad) + 0.5);
		int sintheta = (int)(BIGPOWEROF2*Math.sin(thetarad) + 0.5);

//		Projector.projectCursorAroundX(1, imp.getNSlices(), imp.getHeight()/2, imp.getNSlices()/2, imp.getWidth(), imp.getHeight(), costheta, sintheta);
	}

	public boolean getShowOwnROIs() {

		return showOwnROIs;
	}


	public void setShowOwnROIs(boolean showOwnROIs) {
		this.showOwnROIs = showOwnROIs;
	}

	public void setLabelShapes(Hashtable<Roi,ShapeRoi> labelShapes) {
		this.labelShapes = labelShapes;
	}

	public Hashtable<Roi,ShapeRoi> getLabelShapes() {
		return labelShapes;
	}

	public static BigDecimal takeRoot(int root, BigDecimal n, BigDecimal
			maxError) {
		int MAXITER = 5000;

		// Specify a math context with 40 digits of precision.
		MathContext mc = new MathContext(40);

		// Specify the starting value in the search for the cube root.
		BigDecimal x;
		x=new BigDecimal("1",mc);

		BigDecimal prevX = null;

		BigDecimal rootBD = new BigDecimal(root,mc);
		// Search for the cube root via the Newton-Raphson loop. Output
		//		each successive iteration's value.
		for(int i=0; i < MAXITER; ++i) {
			x = x.subtract(x.pow(root,mc)
					.subtract(n,mc)
					.divide(rootBD.multiply(x.pow(root-1,mc),mc),mc),mc);
			if(prevX!=null && prevX.subtract(x).abs().compareTo(maxError) <
					0)
				break;
			prevX = x;
		}

		return x;
	}

	public String[] getPopupInfo() {
		return popupInfo;
	}

	public void setDefaultColor(Color defaultColor) {
		this.defaultColor = defaultColor;
	}

	public Color getDefaultColor() {
		return defaultColor;
	}

	public void setXMouse(int xMouse) {
		this.xMouse = xMouse;
	}

	public int getXMouse() {
		return xMouse;
	}

	public void setYMouse(int yMouse) {
		this.yMouse = yMouse;
	}

	public int getYMouse() {
		return yMouse;
	}

	public JPopupMenu getPopup() {
		return popup;
	}

	public void setPopup(JPopupMenu popup) {
		this.popup = popup;
	}

	public int getMousePressedX() {
		return mousePressedX;
	}

	public void setMousePressedX(int mousePressedX) {
		this.mousePressedX = mousePressedX;
	}

	public int getMousePressedY() {
		return mousePressedY;
	}

	public void setMousePressedY(int mousePressedY) {
		this.mousePressedY = mousePressedY;
	}

	public void setRotation(int i) {
		this.rotation = i;
	}

	
	public void mouseWheelMoved(MouseWheelEvent e) {
		int rot = e.getWheelRotation();
		int modifiers = e.getModifiers();
		boolean altKeyDown = (modifiers&ActionEvent.ALT_MASK)!=0 || IJ.altKeyDown();
		boolean shiftKeyDown = (modifiers&ActionEvent.SHIFT_MASK)!=0 || IJ.shiftKeyDown();
		boolean controlKeyDown = (modifiers&ActionEvent.CTRL_MASK)!=0 || IJ.controlKeyDown();
		IJ.setKeyUp(KeyEvent.VK_ALT);
		IJ.setKeyUp(KeyEvent.VK_SHIFT);
		IJ.setKeyUp(KeyEvent.VK_CONTROL);
		Point p = this.getCursorLoc();

		Roi roi = imp.getRoi();
		if (roi != null
				/* && imp.getRoi().contains(p.x, p.y) */ 
				&& Toolbar.getToolId() == Toolbar.OVAL 
				&& Toolbar.getOvalToolType() == Toolbar.BRUSH_ROI
				&& !IJ.spaceBarDown()) {
			Toolbar.setBrushSize(Toolbar.getBrushSize() > rot?Toolbar.getBrushSize() - rot: 1);
//			if (!roi.isArea()){
//				imp.deleteRoi();
//			} else {
//				int size = (int) roi.getBounds().width/2;
//				Toolbar.setBrushSize(size);
//			}

 			flags += InputEvent.BUTTON1_MASK;
 			canvasRoiBrush = new RoiBrush(imp);
		} else {
			if (shiftKeyDown){
				int newT = imp.getFrame()+rot;
				if (newT > imp.getNFrames()) newT = 1;
				if (newT <  1) newT = imp.getNFrames();
				imp.setPosition(imp.getChannel(), imp.getSlice(), newT);
			} else if (controlKeyDown){
				int newC = imp.getChannel()+rot;
				if (newC > imp.getNChannels()) newC = 1;
				if (newC <  1) newC = imp.getNChannels();
				imp.setPosition(newC, imp.getSlice(), imp.getFrame());
			} else {
				int newZ = imp.getSlice()+rot;
				if (newZ > imp.getNSlices()) newZ = 1;
				if (newZ <  1) newZ = imp.getNSlices();
				imp.setPosition(imp.getChannel(), newZ, imp.getFrame());
			}
		}
		e.consume();
	}

	@Override
	public void componentResized(ComponentEvent e) {
		
		update(this);
	}

	@Override
	public void componentMoved(ComponentEvent e) {
			// TODO Auto-generated method stub
		
	}

	@Override
	public void componentShown(ComponentEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void componentHidden(ComponentEvent e) {
		// TODO Auto-generated method stub
		
	}

}
