package ij.gui;

import java.awt.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Hashtable;
import java.util.Properties;
import java.awt.image.*;

import ij.process.*;
import ij.measure.*;
import ij.plugin.Colors;
import ij.plugin.DragAndDrop;
import ij.plugin.WandToolOptions;
import ij.plugin.frame.ColorLegend;
import ij.plugin.frame.Recorder;
import ij.plugin.frame.RoiManager;
import ij.plugin.tool.PlugInTool;
import ij.macro.*;
import ij.*;
import ij.util.*;

import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.util.*;
import java.awt.geom.*;

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

import org.vcell.gloworm.MultiQTVirtualStack;

/** This is a Canvas used to display images in a Window. */
public class ImageCanvas extends Canvas implements MouseListener, MouseMotionListener, Cloneable {

	public static Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);
	protected static Cursor handCursor = new Cursor(Cursor.HAND_CURSOR);
	protected static Cursor moveCursor = new Cursor(Cursor.MOVE_CURSOR);
	protected static Cursor crosshairCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);

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
	private ShapeRoi[] labelShapes;
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

	private Image offScreenImage;
	private int offScreenWidth = 0;
	private int offScreenHeight = 0;
	private boolean mouseExited = true;
	private boolean customRoi;
	private boolean drawNames;
	public Hashtable<String,Roi> messageRois;
	public String droppedGeneUrls = "";
	public boolean sketchyMQTVS;



	public ImageCanvas(ImagePlus imp) {
		this.imp = imp;
		//		sketchyMQTVS = false;
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

		ij = IJ.getInstance();
		int width = imp.getWidth();
		int height = imp.getHeight();
		imageWidth = width;
		imageHeight = height;
		srcRect = new Rectangle(0, 0, imageWidth, imageHeight);
		setDrawingSize(imageWidth, imageHeight);
		magnification = 1.0;
		messageRois = new Hashtable<String,Roi>();

		DragAndDrop dnd = DragAndDrop.getInstance();
		if (dnd!=null)
			dnd.addDropTarget(this);

		addMouseListener(this);
		addMouseMotionListener(this);
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

	/** Update this ImageCanvas to have the same zoom and scale settings as the one specified. */
	void update(ImageCanvas ic) {
		if (ic==null || ic==this || ic.imp==null)
			return;
		if (ic.imp.getWidth()!=imageWidth || ic.imp.getHeight()!=imageHeight)
			return;
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
		if (roi!=null || showAllROIs || overlay!=null) {
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
			if (showAllROIs) drawAllROIs(g);
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
			repaint();
			return;
		}
		initGraphics(g, null, showAllColor);
		Hashtable rois = rm.getROIs();
		DefaultListModel listModel = rm.getListModel();
		boolean drawLabels = rm.getDrawLabels();
		currentRoi = null;
		int n = 0;
		if (listModel != null)
			n = listModel.getSize();
		if (IJ.debugMode) IJ.log("paint: drawing "+n+" \"Show All\" ROIs");
		//		if (labelShapes==null || labelShapes.length!=n)
		setLabelShapes(new ShapeRoi[n]);
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
		for (int i=0; i<n; i++) {
			String label = null;
			Roi roi = null;
			try {
				label = (String) listModel.get(i);
				roi = (Roi)rois.get(label);
			} catch(Exception e) {
				roi = null;
			}
			if (roi==null) continue;
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
						getLabelShapes()[i] = roi instanceof Arrow?((Arrow)roi).getShapeRoi():new ShapeRoi(roi);
					}
				} else {
					int position = roi.getPosition();
					if (position==0)
						position = getSliceNumber(roi.getName());
					if (position==0 || position==currentImage)
						drawRoi(g, roi, -1);  //Why is this line here?
					drawRoi(g, roi, drawLabels?i:-1);
				}
			}
		}
		if (overlay != null) {
			for (int o=0;o<overlay.size();o++)
				drawRoi(g, overlay.get(o), -1);
		}
		((Graphics2D)g).setStroke(Roi.onePixelWide);
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
		if (!drawingList && getLabelShapes()!=null && index<getLabelShapes().length)
			getLabelShapes()[index] = new ShapeRoi(new Rectangle(x-1, y-h+2, w+1, h));
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
			Graphics offScreenGraphics = offScreenImage.getGraphics();
			setInterpolation(offScreenGraphics, Prefs.interpolateScaledImages);
			Image img = imp.getImage();
			if (img!=null)
				offScreenGraphics.drawImage(img, 0, 0, srcRectWidthMag, srcRectHeightMag,
						srcRect.x, srcRect.y, srcRect.x+srcRect.width, srcRect.y+srcRect.height, null);
			if (overlay!=null) drawOverlay(offScreenGraphics);
			if (showAllROIs) drawAllROIs(offScreenGraphics);
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
			g.drawImage(offScreenImage, 0, 0, null);
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

	/** Sets the cursor based on the current tool and cursor location. */
	public void setCursor(int sx, int sy, int ox, int oy) {
		setXMouse(ox);
		setYMouse(oy);
		mouseExited = false;
		Roi roi = imp.getRoi();
		ImageWindow win = imp.getWindow();
		if (win==null)
			return;
		if (IJ.spaceBarDown()) {
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
			repaint();
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
	private int rotation;

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
			imp.getWindow().pack();
			imp.getWindow().setSize(dstWidth+padH, dstHeight+padV);
		} else {
			adjustSourceRect(newMag, sx, sy);
			imp.getWindow().pack();
			imp.getWindow().setSize(dstWidth+padH, dstHeight+padV);
		}
		if (srcRect.width<imageWidth || srcRect.height<imageHeight)
			resetMaxBounds();
		repaint();
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
						+imp.getWindow().overheadPanel.getHeight();
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
				imp.getWindow().pack();
				imp.getWindow().setSize(newDstWidth+padH, newDstHeight+padV);
				repaint();
			} else {
				setDrawingSize(newDstWidth, newDstHeight);
				imp.getWindow().pack();
				imp.getWindow().setSize(newDstWidth+padH, newDstHeight+padV);
				repaint();
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
		repaint();
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
		repaint();
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
		repaint();
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
		if (IJ.spaceBarDown()) {
			// temporarily switch to "hand" tool of space bar down
			setupScroll(ox, oy);
			return;
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
					fakeTR.searchWormbase();
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
			if (Toolbar.getBrushSize()>0)
				new RoiBrush();
			else
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
		int n = listModel.getSize();
		if (getLabelShapes()==null || getLabelShapes().length!=n) return false;
		for (int i=0; i<n; i++) {
			if ( rois.get(listModel.get(i)) instanceof Arrow && getLabelShapes()[i]!=null && getLabelShapes()[i].contains(x, y)) {
				new ij.macro.MacroRunner("roiManager('select', "+i+", "+imp.getID()+");");
				return true;
			}
			if (getLabelShapes()[i]!=null && getLabelShapes()[i].contains(x, y)
					&& ( ((Roi) rois.get(listModel.get(i))).getFillColor()!=null
					|| rois.get(listModel.get(i)) instanceof TextRoi ) ) {
				//rm.select(i);
				// this needs to run on a separate thread, at least on OS X
				// "update2" does not clone the ROI so the "Show All"
				// outline moves as the user moves the RO.
				new ij.macro.MacroRunner("roiManager('select', "+i+", "+imp.getID()+");");
				return true;
			}
		}
		rm.select(imp, -1);
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
		this.setCursor(tk.createCustomCursor(img,new Point(0,0),"searchCursor"));


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
			Checkbox clccb = null;
			if (colorLegend!=null){
				clccb = colorLegend.getChosenCB();
				colorLegend.setChosenCB(null);
			}

			boolean brainbowSelection = colorLegend != null &&  (e.getSource() instanceof Checkbox || clccb!= null) 
					&& (imp.getTitle().contains("Sketch3D") 
							|| sketchyMQTVS);


			if (rm != null) {

				DefaultListModel listModel = rm.getListModel();
				int n = listModel.getSize();

				String cellName = "";
				String cellTag = "";
				JComponent[] standardmis = new JComponent[popup.getComponentCount()];
				for (int k = standardmis.length - 1; k >= 0; k--) {
					standardmis[k] = (JComponent) popup.getComponent(k);
					popup.remove(k);
				}
				String clickedROIstring = "";
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

				for (int r = 0; r < n; r++) {
					//					IJ.log(""+r);
					if ((!lineageMap && this.getLabelShapes()[r] != null
							&& this.getLabelShapes()[r].contains(xOS, yOS))
							|| (brainbowSelection /*&& rm.getImagePlus().getCanvas().getlabelShapes()[r].intersects(new Rectangle (xOS*10-200, yOS*10-200,400,400))*/)
							|| (lineageMap && logLines.length >0 && logLines[logLines.length-1].startsWith(">")
									&& ((String) listModel.get(r)).startsWith("\""+logLines[logLines.length-1].split("[> ,]")[1]+" " + logLines[logLines.length-1].split("[> ,]")[2])) ) {
						mi = null;
						targetTag[0] = r;
						targetTag[1] = (int) rm.getFullRoisAsArray()[r].getBounds().getCenterX();
						targetTag[2] = (int) rm.getFullRoisAsArray()[r].getBounds().getCenterY();

						if (!brainbowSelection) {
							cellTag = (String) listModel.get(r);
							cellName = ((String) listModel.get(r)).split("[\"|=]")[1].trim();
							if (cellName.split(" ").length >1 
									&& cellName.split(" ")[1].matches("-*\\d*") 
									&& (cellName.split(" ").length <3?true:cellName.split(" ")[2].matches("\\+*")) ){
								cellName = cellName.split(" ")[0];
							} 

							mi =new JMenuItem("\""+cellName + " \": synch all windows to this tag" );
							mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Synch.png")));
							clickedROIstring = listModel.get(r) + ": "
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
						if (lineageMap) r=n;
					}
				}
				for (int i = 0; i < impIDs.length; i++) {
					if (true /*WindowManager.getImage(impIDs[i]) != this.getImage()*/) {
						DefaultListModel otherListModel =WindowManager.getImage(impIDs[i]).getRoiManager().getListModel();
						Object[] roiNames = otherListModel.toArray();
						for (int j = 0; j < roiNames.length; j++) {
							if (((String) roiNames[j]).contains("\"") && ((String) roiNames[j]).split("\"")[1].split(" ")[0].toLowerCase().trim().equals(cellName.toLowerCase())
									&& !clickedROIstring
									.contains(roiNames[j]
											+ ": "
											+ WindowManager
											.getImage(
													impIDs[i])
													.getTitle())) {
								mi = new JMenuItem("also see: "
										+ ((String) roiNames[j]).split("_")[0]
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

				final ImagePlus cartoonImp = IJ.openImage("http://legacy.wormbase.org/cell/diagrams/"+cellName.toLowerCase()+".gif");
				
				if (cartoonImp!=null) {
					cartoonImp.setTitle(cellName);
					JPanel cartoonPanel = new JPanel();
					JButton cartoonButton = new JButton();
					cartoonButton.setIcon(new ImageIcon(cartoonImp.getImage()));
					cartoonPanel.add(cartoonButton);
					cartoonPanel.setBackground(Color.white);
					popup.add(cartoonPanel, 1);
				}

				
				
				String[] logLines2=null;

				if (getGenes && cellName != "" && cellName != null) {
					JMenu genePopup = new JMenu(cellName + ": Expressed Genes >", true);
					genePopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/DNAicon.png")));
					genePopup.getPopupMenu().addPopupMenuListener(ij);
					popupInfo[1] = popupInfo[1] + "Expressed Genes >\n";
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
					IJ.runMacro(""
							+ "string = File.openUrlAsString(\"http://www.wormbase.org/db/get?name="
							+ cellName
							+ ";class=Anatomy_term\");"
							+ "print(string);");							
					logLines2 = IJ.getLog().split("wname=\"associations\"");
					//					IJ.log("\\Clear");
					IJ.log(oldLog);
					//					IJ.showMessage(cellName+IJ.getLog());
					String restString = "";
					if (logLines2 != null && logLines2.length > 1 && logLines2[1].split("\"").length > 1)
						restString = logLines2[1].split("\"")[1];

					IJ.runMacro(""
							+ "string = File.openUrlAsString(\"http://www.wormbase.org"
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

					if (!rm.getImagePlus().getTitle().startsWith("Projections") && rm.getFullListModel().size()>0) {

						JMenu nearPopup = new JMenu(cellName+": Nearby cells >", true);
						nearPopup.setIcon(new ImageIcon(ImageWindow.class.getResource("images/NearIcon.png")));
						nearPopup.getPopupMenu().addPopupMenuListener(ij);
						mi =  new JMenuItem("^--------------------^");
						mi.addActionListener(ij);
						nearPopup.add(mi);

						double widthDenom = 3;
						double timeBalancer = -28;
						if (rm.getImagePlus().getTitle().contains("MQTVS_1305281558")){
							widthDenom = 3.8;
							timeBalancer = 2;
						}
						if (rm.getImagePlus().getTitle().contains("SW_")){
							widthDenom = 100;
							timeBalancer = 0;
						}
						ImagePlus guideImp = rm.getImagePlus();
						if (rm.getImagePlus().getMotherImp() != null)
							guideImp = rm.getImagePlus().getMotherImp();
						int frames = guideImp.getNFrames();

						BigDecimal framesBD = new BigDecimal(""
								+ (frames + timeBalancer));
						BigDecimal widthDenomBD = new BigDecimal("" + widthDenom);
						BigDecimal tBD = new BigDecimal(""
								+ (rm.getImagePlus().getMotherFrame() > 0 ? rm.getImagePlus().getMotherFrame()
										: 0 + rm.getFullRoisAsArray()[targetTag[0]].getTPosition() + timeBalancer));
						BigDecimal impHeightBD = new BigDecimal(""
								+ guideImp.getHeight());
						BigDecimal cellDiameterBD = impHeightBD.divide(
								new BigDecimal("10"), MathContext.DECIMAL32);
						if (guideImp.getTitle().contains("SW_")){

							cellDiameterBD = impHeightBD.divide(
									widthDenomBD, MathContext.DECIMAL32).multiply(
											takeRoot(3, (framesBD.subtract(tBD)
													.add(new BigDecimal("1"))).divide(tBD,
															MathContext.DECIMAL32), new BigDecimal(
																	".001")), MathContext.DECIMAL32);
						}else{
							cellDiameterBD = impHeightBD.divide(
									widthDenomBD, MathContext.DECIMAL32).multiply(
											takeRoot(3, (framesBD.subtract(tBD)
													.add(new BigDecimal("1"))).divide(tBD,
															MathContext.DECIMAL32), new BigDecimal(
																	".001")), MathContext.DECIMAL32);
						}
						int zSpan = (int) (cellDiameterBD.intValue() / guideImp
								.getCalibration().pixelDepth);
						if (zSpan<1)
							zSpan=1;
						for (int z = 0; z < zSpan; z++) {
							int zSlice = Integer.parseInt(((String) rm.getListModel().get(targetTag[0]))
									.split("_")[((String) rm.getListModel().get(targetTag[0])).split("_").length-2])
									- zSpan / 2 + z;
							if (zSlice < 1 || zSlice > rm.getImagePlus().getNSlices())
								continue;
							int tFrame = Integer.parseInt(((String) rm.getListModel().get(targetTag[0]))
									.split("_")[((String) rm.getListModel().get(targetTag[0])).split("_").length-1]
											.replace("C", "").replace("Z", "").replace("T", "").split("-")[0]);

							double inPlaneDiameter = 2 * Math.sqrt(Math.pow(
									cellDiameterBD.intValue() / 2, 2)
									- Math.pow((rm.getFullRoisAsArray()[targetTag[0]].getZPosition() - zSlice)
											* guideImp.getCalibration().pixelDepth, 2));

							Roi hoodRoi = new OvalRoi(targetTag[1] - (inPlaneDiameter / 2),
									targetTag[2] - (inPlaneDiameter / 2), 
									inPlaneDiameter, inPlaneDiameter);

							Roi[] nearbyROIs = rm.getSliceSpecificRoiArray(zSlice,
									tFrame, false);
							for (int h = 0; h < nearbyROIs.length; h++) {

								if (hoodRoi.contains((int) nearbyROIs[h]
										.getBounds().getCenterX(),
										(int) nearbyROIs[h].getBounds()
										.getCenterY())
										&& !clickedROIstring.contains(nearbyROIs[h].getName()
												+ ": " + rm.getImagePlus().getTitle())) {

									//									nearPopup.add(zSlice+" "+tFrame);

									mi = new JMenuItem("near "
											+ nearbyROIs[h].getName().split("_")[0]
													+ " in \""
													+ (rm.getImagePlus().getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0].length()>28?
																	(rm.getImagePlus().getTitle().replaceAll("\\d+-movie Scene - ", "").split(",")[0].substring(0,25) +"..."):
																				(rm.getImagePlus().getTitle().replaceAll("\\d-movie scene - ", "").split(",")[0]))+"\"");
									mi.setActionCommand("near "
											+ nearbyROIs[h].getName() + ": "
											+ rm.getImagePlus().getTitle());
									popupInfo[1] = popupInfo[1]+ "near "
											+ nearbyROIs[h].getName() + ": "
											+ rm.getImagePlus().getTitle()+"\n";

									mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
									mi.addActionListener(ij);
									nearPopup.add(mi);
								}
							}
						}
						relationshipsPopup.add(nearPopup);			
					}

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

							mi = new JMenuItem("");
							mi.setLabel("analogous to   " + embAnalogsChunks[1]+cellName.substring(embAnalogsChunks[0].length(), cellName.length()));
							mi.addActionListener(ij);
							analogsPopup.add(mi);
							analogName = embAnalogsChunks[1]+cellName.substring(embAnalogsChunks[0].length(), cellName.length());


							for (int o=0;o< openImageIDsWithRM.length;o++) {

								Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
								for (int j = 0; j < imgRoiNames.length; j++) {
									if (((String) imgRoiNames[j]).replace("\"","").startsWith(analogName+" ")) {
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
										mi.setActionCommand(itemString);
										mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
										popupInfo[1] = popupInfo[1]+itemString+"\n";
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
								mi = new JMenuItem("");
								mi.setLabel("analogous to   " + logLines[h].substring(logLines[h].indexOf("name=")+5, logLines[h].indexOf(" rel=")) );
								mi.addActionListener(ij);
								analogsPopup.add(mi);


								analogName = logLines[h].substring(logLines[h].indexOf("name=")+5, logLines[h].indexOf(" rel="));
								for (int o=0;o< openImageIDsWithRM.length;o++) {

									Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
									for (int j = 0; j < imgRoiNames.length; j++) {
										if (((String) imgRoiNames[j]).toUpperCase().replace("\"","").startsWith(analogName.toUpperCase()+" ")) {
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
											mi.setActionCommand(itemString);
											mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
											popupInfo[1] = popupInfo[1]+itemString+"\n";
											mi.addActionListener(ij);
											analogsPopup.add(mi);
										}
									}
								}

							}
						}
					}

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
										mi = new JMenuItem("");
										mi.setLabel("synapses chemically onto  "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ (synapseChunks[2].contains("Sp")?"polyadic":"monadic")
												+ (Integer.parseInt(synapseChunks[3])>1?" synapses":" synapse"));
										popupInfo[1] = popupInfo[1]+"synapses chemically onto  "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ (synapseChunks[2].contains("Sp")?"polyadic":"monadic")
												+ (Integer.parseInt(synapseChunks[3])>1?" synapses":" synapse")+"\n";

										mi.addActionListener(ij);
										presynapticPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").startsWith(candidate+" ")) {
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
													mi.setActionCommand(itemString);
													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+itemString+"\n";
													mi.addActionListener(ij);
													presynapticPopup.add(mi);
												}
											}
										}
									}	
									if (synapseChunks[2].startsWith("R")) {
										postsyn = true;
										mi = new JMenuItem("");
										mi.setLabel("is synapsed chemically by "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ (synapseChunks[2].contains("Rp")?"polyadic":"monadic") 
												+ (Integer.parseInt(synapseChunks[3])>1?" synapses":" synapse"));
										popupInfo[1] = popupInfo[1]+"is synapsed chemically by "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ (synapseChunks[2].contains("Rp")?"polyadic":"monadic")
												+ (Integer.parseInt(synapseChunks[3])>1?" synapses":" synapse")+"\n";

										mi.addActionListener(ij);
										postsynapticPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").startsWith(candidate+" ")) {
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
													mi.setActionCommand(itemString);
													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+itemString+"\n";

													mi.addActionListener(ij);
													postsynapticPopup.add(mi);
												}
											}
										}
									}	
									if (synapseChunks[2].startsWith("EJ")) {
										elec = true;
										mi = new JMenuItem("");
										mi.setLabel("synapses electrically to  "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ "electric" 
												+ (Integer.parseInt(synapseChunks[3])>1?" junctions":" junction"));
										mi.addActionListener(ij);
										electricPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").startsWith(candidate+" ")) {
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
													mi.setActionCommand(itemString);
													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+itemString+"\n";

													mi.addActionListener(ij);
													electricPopup.add(mi);
												}
											}
										}
									}	
									if (synapseChunks[2].startsWith("NMJ")) {
										nmj = true;
										mi = new JMenuItem("");
										mi.setLabel("neuromuscular junctions   "
												+ synapseChunks[1] + ": "
												+ synapseChunks[3]+" "
												+ "neuromuscular" 
												+ (Integer.parseInt(synapseChunks[3])>1?" junctions":" junction"));
										mi.addActionListener(ij);
										neuromuscularPopup.add(mi);
										String candidate = synapseChunks[1];

										for (int o=0;o< openImageIDsWithRM.length;o++) {

											Object[] imgRoiNames =  WindowManager.getImage(openImageIDsWithRM[o]).getRoiManager().getListModel().toArray();
											for (int j = 0; j < imgRoiNames.length; j++) {
												if (((String) imgRoiNames[j]).replace("\"","").startsWith(candidate+" ")) {
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
													mi.setActionCommand(itemString);
													mi.setIcon(new ImageIcon(ImageWindow.class.getResource("images/See.png")));
													popupInfo[1] = popupInfo[1]+itemString+"\n";

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

					imp.getWindow().toFront();
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
										if (((String) imgRoiNames[j]).replace("\"","").startsWith(candidate+" ")) {
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
												if (((String) imgRoiNames[j]).replace("\"","").startsWith(candidate+" ")) {
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
			if (brainbowSelection && e.getSource() instanceof Checkbox && !sketchyMQTVS)
				colorLegend.add(popup);
			else
				imp.getWindow().add(popup);
			if (IJ.isMacOSX()) IJ.wait(10);
			if (IJ.isLinux()) IJ.wait(10);

			popup.show((Component)e.getSource(), x, y);
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
		flags = e.getModifiers();
		//		mousePressedX = mousePressedY = -1;
		//IJ.log("mouseDragged: "+flags);
		if (flags==0)  // workaround for Mac OS 9 bug
			flags = InputEvent.BUTTON1_MASK;
		if (Toolbar.getToolId()==Toolbar.HAND || IJ.spaceBarDown())
			scroll(x, y);
		else {
			PlugInTool tool = Toolbar.getPlugInTool();
			if (tool!=null) {
				tool.mouseDragged(imp, e);
				if (e.isConsumed()) return;
			}
			IJ.setInputEvent(e);
			Roi roi = imp.getRoi();
			if (roi != null){
				//				IJ.log("NOT NULL");
				//				if (roi instanceof TextRoi)
				//					roi.state = roi.MOVING;
				roi.handleMouseDrag(x, y, flags);
			}
		}
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
			ImageCanvas ic = img.getCanvas();
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
		repaint();
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
		repaint();
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
			if (e.isConsumed()) return;
		}
		flags = e.getModifiers();
		flags &= ~InputEvent.BUTTON1_MASK; // make sure button 1 bit is not set
		flags &= ~InputEvent.BUTTON2_MASK; // make sure button 2 bit is not set
		flags &= ~InputEvent.BUTTON3_MASK; // make sure button 3 bit is not set
		Roi roi = imp.getRoi();
		if (roi != null) {
			Rectangle r = roi.getBounds();
			int type = roi.getType();
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
		if (e.getSource() == this.getImage().getWindow().modeButton) {
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
				int n = listModel.size();
				if (getLabelShapes() == null || getLabelShapes().length != n)
					return;
				String cursorString = null;
				for (int i = 0; i < n; i++) {
					if (rois.get(listModel.get(i)) instanceof Arrow
							&& getLabelShapes()[i] != null
							&& getLabelShapes()[i].contains(getXMouse(), getYMouse())) {
						cursorString = ((Roi) rois.get(listModel.get(i))).getName().split("[\"|]")[1];
						i = n;
					}
				}
				if (cursorString == null) {
					for (int i = 0; i < n; i++) {
						if (getLabelShapes()[i] != null
								&& getLabelShapes()[i].contains(getXMouse(), getYMouse())
								&& ((Roi) rois.get(listModel.get(i))).getName().split("[\"|=]").length > 1) {
							cursorString = ((Roi) rois.get(listModel.get(i))).getName().split("[\"|=]")[1];
							i = n;
						}
					}
				}
				Graphics g = getGraphics();
				if (cursorString != null) {
					if (IJ.isWindows()) {
						//				IJ.log(cursorString);
						cursorRoi = new TextRoi(getXMouse()+10/ getMagnification(), getYMouse(), cursorString);
						((TextRoi) cursorRoi).setCurrentFont(g.getFont().deriveFont((float) (16 / getMagnification())));
						try {
							paint(g);
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
				Checkbox cursorCB = imp.getMotherImp().getRoiManager().getColorLegend().getChosenCB();
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
		PlugInTool tool = Toolbar.getPlugInTool();
		if (tool!=null)
			tool.mouseClicked(imp, e);
		if (imp.getRemoteMQTVSHandler() != null) {
			IJ.run(imp, "This Slice", "");			
		}
	}

	public boolean getShowOwnROIs() {

		return showOwnROIs;
	}


	public void setShowOwnROIs(boolean showOwnROIs) {
		this.showOwnROIs = showOwnROIs;
	}

	public void setLabelShapes(ShapeRoi[] labelShapes) {
		this.labelShapes = labelShapes;
	}

	public ShapeRoi[] getLabelShapes() {
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

}
