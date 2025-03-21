package ij3d;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Arrow;
import ij.gui.Line;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.process.ImageProcessor;
import ij3d.pointlist.PointListDialog;
import ij3d.pointlist.PointListPanel;
import ij3d.pointlist.PointListShape;
import ij3d.shapes.BoundingBox;
import ij3d.shapes.CoordinateSystem;
import isosurface.MeshGroup;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.OrderedGroup;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.View;
import javax.vecmath.Color3f;
import javax.vecmath.Matrix3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import orthoslice.MultiOrthoGroup;
import orthoslice.OrthoGroup;
import surfaceplot.SurfacePlotGroup;
import vib.FastMatrix;
import vib.InterpolatedImage;
import vib.PointList;
import voltex.VoltexGroup;
import customnode.CustomMesh;
import customnode.CustomMeshNode;

public class ContentInstant extends BranchGroup implements UniverseListener, ContentConstants {

	// time point for this ContentInstant
	public int timepoint = 0;

	// attributes
	private final String name;
	protected Color3f color = null;
	protected ImagePlus image;
	protected boolean[] channels = new boolean[] {true, true, true};
	protected int[] rLUT = createDefaultLUT();
	protected int[] gLUT = createDefaultLUT();
	protected int[] bLUT = createDefaultLUT();
	protected int[] aLUT = createDefaultLUT();
	protected float transparency = 0f;
	protected int resamplingF = 1;
	protected int threshold = 0;
	protected boolean shaded = true;
	protected int type = VOLUME;

	// visibility flags
	private boolean locked = false;
	private boolean visible = true;
	private boolean bbVisible = false;
	private boolean coordVisible = UniverseSettings.
					showLocalCoordinateSystemsByDefault;
	private boolean showPL = false;
	protected boolean selected = false;

	// entries
	private ContentNode contentNode = null;

	// point list
	private PointListShape plShape   = null;
	private PointListDialog plDialog = null;
	private PointListPanel plPanel   = null;
	private PointList points;

	// scene graph entries
	private OrderedGroup ordered;

	protected TransformGroup localRotate;
	protected TransformGroup localTranslate;
	private TreeMap<Integer, Content> contentsContainingThisInstant;


	public TreeMap<Integer, Content> getContentsContainingThisInstant() {
		return contentsContainingThisInstant;
	}

	public void setContentsContainingThisInstant(TreeMap<Integer, Content> contentsContainingThisInstant) {
		this.contentsContainingThisInstant = contentsContainingThisInstant;
	}

	private boolean available = true;

//    private static ScheduledThreadPoolExecutor blinkService;
//	private ScheduledFuture schfut;

	public ContentInstant(String name) {
		// create BranchGroup for this image
		this.name = name;
		this.contentsContainingThisInstant = new TreeMap<Integer,Content>();
		setCapability(BranchGroup.ALLOW_DETACH);
		setCapability(BranchGroup.ENABLE_PICK_REPORTING);

//		if (blinkService ==null){
//			blinkService = new ScheduledThreadPoolExecutor(1);
//		}
		
		// create transformation for pickeing
		localTranslate = new TransformGroup();
		localTranslate.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		localTranslate.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		addChild(localTranslate);
		localRotate = new TransformGroup();
		localRotate.setCapability(TransformGroup.ALLOW_TRANSFORM_READ);
		localRotate.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
		localTranslate.addChild(localRotate);

		ordered = new OrderedGroup();
		for(int i = 0; i < 5; i++) {
			Switch s = new Switch();
			s.setCapability(Switch.ALLOW_SWITCH_WRITE);
			s.setCapability(Switch.ALLOW_SWITCH_READ);
			s.setCapability(Switch.ALLOW_CHILDREN_WRITE);
			s.setCapability(Switch.ALLOW_CHILDREN_EXTEND);
			ordered.addChild(s);
		}

		localRotate.addChild(ordered);

		// create the point list
		points = new PointList();
		plShape = new PointListShape(points);
		plShape.setPickable(true);
		plPanel = new PointListPanel(name, points);
	}

	public void displayAs(int type) {
		if(image == null)
			return;
		// create content node and add it to the switch
		switch(type) {
			case VOLUME: contentNode = new VoltexGroup(this); break;
			case ORTHO: contentNode = new OrthoGroup(this); break;
			case SURFACE: contentNode = new MeshGroup(this); break;
			case SURFACE_PLOT2D: contentNode =
				new SurfacePlotGroup(this); break;
			case MULTIORTHO: contentNode = new MultiOrthoGroup(this); break;
			default: throw new IllegalArgumentException(
					"Specified type is neither VOLUME, ORTHO," +
					"SURFACE or SURFACEPLOT2D");
		}
		display(contentNode);
		// update type
		this.type = type;
	}

	private static int[] createDefaultLUT() {
		int[] lut = new int[256];
		for(int i = 0; i < lut.length; i++)
			lut[i] = i;
		return lut;
	}

	public static int getDefaultThreshold(ImagePlus imp, int type) {
		if(type != SURFACE)
			return 0;
		ImageStack stack = imp.getStack();
		int d = imp.getStackSize();
		// compute stack histogram
		int[] h = stack.getProcessor(1).getHistogram();
		for(int z = 1; z < d; z++) {
			int[] tmp = stack.getProcessor(z+1).getHistogram();
			for(int i = 0; i < h.length; i++)
				h[i] += tmp[i];

		}
		return imp.getProcessor().getAutoThreshold(h);
	}

	public static int getDefaultResamplingFactor(ImagePlus imp, int type) {
		int w = imp.getWidth(), h = imp.getHeight();
		int d = imp.getStackSize();
		int max = Math.max(w, Math.max(h, d));
		switch(type) {
			case SURFACE: return (int)Math.ceil(max / 128f);
			case VOLUME:  return (int)Math.ceil(max / 256f);
			case ORTHO:   return (int)Math.ceil(max / 256f);
			case SURFACE_PLOT2D: return (int)Math.ceil(max / 128f);
		}
		return 1;
	}

	public void display(ContentNode node) {
		// remove everything if possible
		for(@SuppressWarnings("rawtypes")
		Enumeration e = ordered.getAllChildren(); e.hasMoreElements(); ) {
			Switch s = (Switch)e.nextElement();
			s.removeAllChildren();
		}

		// create content node and add it to the switch
		contentNode = node;
		((Switch)ordered.getChild(CO)).addChild(contentNode);

		// create the bounding box and add it to the switch
		Point3d min = new Point3d(); contentNode.getMin(min);
		Point3d max = new Point3d(); contentNode.getMax(max);
		BoundingBox bb = new BoundingBox(min, max);
		bb.setPickable(false);
		((Switch)ordered.getChild(BS)).addChild(bb);
		bb = new BoundingBox(min, max, new Color3f(0, 1, 0));
		bb.setPickable(false);
		((Switch)ordered.getChild(BB)).addChild(bb);

		// create coordinate system and add it to the switch
		float cl = (float)Math.abs(max.x - min.x) / 5f;
		CoordinateSystem cs = new CoordinateSystem(
						cl, new Color3f(0, 1, 0));
		cs.setPickable(false);
		((Switch)ordered.getChild(CS)).addChild(cs);

		// create point list and add it to the switch
		((Switch)ordered.getChild(PL)).addChild(plShape);

		// adjust the landmark point size properly
		plShape.setRadius((float)min.distance(max) / 100f);

		// initialize child mask of the switch
		setSwitch(BS, selected);
		setSwitch(CS, coordVisible);
		setSwitch(CO, visible);
		setSwitch(PL, showPL);

		// update type
		this.type = CUSTOM;
	}

	private void setSwitch(int which, boolean on) {
		((Switch)ordered.getChild(which)).setWhichChild(on ? Switch.CHILD_ALL : Switch.CHILD_NONE);
	}

	public ImagePlus exportTransformed() {
		ImagePlus orig = getImage();
		if(orig == null)
			throw new RuntimeException("No greyscale image exists for "
				+ getName());

		Transform3D t1 = new Transform3D();
		getLocalTranslate().getTransform(t1);
		Transform3D t2 = new Transform3D();
		getLocalRotate().getTransform(t2);
		t1.mul(t2);
		FastMatrix fc = FastMatrix.fromCalibration(orig);
		FastMatrix fm = fc.inverse().times(IJ3dExecuter.toFastMatrix(t1).inverse()).
			times(fc);
		InterpolatedImage in = new InterpolatedImage(orig);
		InterpolatedImage out = in.cloneDimensionsOnly();
		int w = orig.getWidth(), h = orig.getHeight();
		int d = orig.getStackSize();

		for (int k = 0; k < d; k++) {
			ImageProcessor ip = out.getImage().getStack().
						getProcessor(k + 1);
			for (int j = 0; j < h; j++) {
				for(int i = 0; i < w; i++) {
					fm.apply(i, j, k);
					ip.set(i, j, (int)in.interpol.get(
							fm.x, fm.y, fm.z));
				}
				IJ.showProgress(k + 1, d);
			}
		}
		out.getImage().setTitle(orig.getTitle() + "_transformed");
		out.getImage().getProcessor().setColorModel(
			orig.getProcessor().getColorModel());
		return out.getImage();
	}

	/* ************************************************************
	 * swapping
	 *
	 * ***********************************************************/
	public void clearOriginalData() {
		if (image != null)
			image.close();
		image = null;
	}

	public void swapDisplayedData() {
		if(!available)
			return;
		contentNode.swapDisplayedData(getDisplayedDataSwapfile(), getName());
		available = false;
	}

	public void restoreDisplayedData() {
		System.out.println("restoreDisplayedData " + getName());
		if(available) {
			System.out.println("not restoring because it is not swapped");
			return;
		}
		contentNode.restoreDisplayedData(getDisplayedDataSwapfile(), getName());
		available = true;
	}

	public void clearDisplayedData() {
		if(!available) return;
		contentNode.clearDisplayedData();
		available = false;
	}

	public boolean isAvailable() {
		return available;
	}

	private String displayedDataSwapfile = null;
	private String originalDataSwapfile = null;

//	private boolean blinkOn;

	private Color3f trueColor;

	private String getOriginalDataSwapfile() {
		if(originalDataSwapfile != null)
			return originalDataSwapfile;
		File tmp = new File(System.getProperty("java.io.tmpdir"), "3D_Viewer");
		if(!tmp.exists())
			tmp.mkdirs();
		tmp = new File(tmp, "original");
		if(!tmp.exists())
			tmp.mkdirs();
		originalDataSwapfile = new File(tmp, getName()).
			getAbsolutePath();
		return originalDataSwapfile;
	}

	private String getDisplayedDataSwapfile() {
		if(displayedDataSwapfile != null)
			return displayedDataSwapfile;
		File tmp = new File(System.getProperty("java.io.tmpdir"), "3D_Viewer");
		if(!tmp.exists())
			tmp.mkdirs();
		tmp = new File(tmp, "displayed");
		if(!tmp.exists())
			tmp.mkdirs();
		displayedDataSwapfile = new File(tmp, getName()).
			getAbsolutePath();
		return displayedDataSwapfile;
	}


	/* ************************************************************
	 * setters - visibility flags
	 *
	 * ***********************************************************/

	public void setVisible(boolean b) {
		visible = b;
		setSwitch(CO, b);
		setSwitch(CS, b & coordVisible);
// 		whichChild.set(BB, b && bbVisible);
		// only if hiding, hide the point list
		if(!b) {
			showPointList(false);
		}
	}

	public void showBoundingBox(boolean b) {
		bbVisible = b;
		setSwitch(BB, b);
	}


	public void showCoordinateSystem(boolean b) {
		coordVisible = b;
		setSwitch(CS, b);
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
//		blinkOn=true;
//		if (this.trueColor == null)
//			this.trueColor = getColor();
//		final Color3f realColor = this.trueColor;
//		if (schfut != null && !selected) {
//			schfut.cancel(true);
//			schfut = null;
//			setColor(this.trueColor);
//		} else {
//			schfut = blinkService.scheduleAtFixedRate(new Runnable()
//			{
//				public void run()
//				{
//
//					if (blinkOn){
//						setTempColor(ContentInstant.this.trueColor);
//						blinkOn = false;
//					} else {
//						setTempColor(new Color3f(ContentInstant.this.trueColor.x*0.7f,
//												ContentInstant.this.trueColor.y*0.7f,
//												ContentInstant.this.trueColor.z*0.7f));
//						blinkOn =true;
//					}
//				}
//			}, 0, 500, TimeUnit.MILLISECONDS);
//		}
	}

	/* ************************************************************
	 * point list
	 *
	 * ***********************************************************/

	public void setPointListDialog(PointListDialog p) {
		this.plDialog = p;
	}

	public void showPointList(boolean b) {
		if(plShape == null)
			return;

		setSwitch(PL, b);
		showPL = b;
		if(b && plDialog != null)
			plDialog.addPointList(getName(), plPanel);
		else if(!b && plDialog != null)
			plDialog.removePointList(plPanel);
	}

	public void loadPointList() {
		PointList points = PointList.load(image);
		if (points != null)
			setPointList(points);
	}

	public void setPointList(PointList points) {
		this.points = points;
		plPanel.setPointList(points);
		plShape.setPointList(points);
	}

	public void savePointList() {
		String dir = OpenDialog.getDefaultDirectory();
		String n = this.getName();
		if(image != null) {
			FileInfo fi = image.getFileInfo();
			dir = fi.directory;
			n = fi.fileName;
		}
		points.save(dir, n);
	}

	public void savePointList(PrintStream out) throws IOException {
		points.save(out, false);
	}

	/**
	 * @deprecated
	 * @param p
	 */
	@Deprecated
	public void addPointListPoint(Point3d p) {
		points.add(p.x, p.y, p.z);
		if(plDialog != null)
			plDialog.update();
	}

	/**
	 * @deprecated
	 * @param i
	 * @param pos
	 */
	@Deprecated
	public void setListPointPos(int i, Point3d pos) {
		points.placePoint(points.get(i), pos.x, pos.y, pos.z);
	}

	public float getLandmarkPointSize() {
		return plShape.getRadius();
	}

	public void setLandmarkPointSize(float r) {
		plShape.setRadius(r);
	}

	public Color3f getLandmarkColor() {
		return plShape.getColor();
	}

	public void setLandmarkColor(Color3f color) {
		plShape.setColor(color);
	}

	public PointList getPointList() {
		return points;
	}

	/**
	 * @deprecated
	 * @param i
	 */
	@Deprecated
	public void deletePointListPoint(int i) {
		points.remove(i);
		if(plDialog != null)
			plDialog.update();
	}

	/* ************************************************************
	 * setters - transform
	 *
	 **************************************************************/
	public void toggleLock() {
		locked = !locked;
	}

	public void setLocked(boolean b) {
		locked = b;
	}

	public void applyTransform(double[] matrix) {
		applyTransform(new Transform3D(matrix));
	}

	public void applyTransform(Transform3D transform) {
		Transform3D t1 = new Transform3D();
		localTranslate.getTransform(t1);
		Transform3D t2 = new Transform3D();
		localRotate.getTransform(t2);
		t1.mul(t2);

		t1.mul(transform, t1);
		setTransform(t1);
	}

	public void setTransform(double[] matrix) {
		if(contentNode == null)
			return;
		setTransform(new Transform3D(matrix));
	}

	public void setTransform(Transform3D transform) {
		if(contentNode == null)
			return;
		Transform3D t = new Transform3D();
		Point3d c = new Point3d(); contentNode.getCenter(c);

		Matrix3f m = new Matrix3f();
		transform.getRotationScale(m);
		t.setRotationScale(m);
		// One might thing a rotation matrix has no translational
		// component, however, if the rotation is composed of
		// translation - rotation - backtranslation, it has indeed.
		Vector3d v = new Vector3d();
		v.x = -m.m00*c.x - m.m01*c.y - m.m02*c.z + c.x;
		v.y = -m.m10*c.x - m.m11*c.y - m.m12*c.z + c.y;
		v.z = -m.m20*c.x - m.m21*c.y - m.m22*c.z + c.z;
		t.setTranslation(v);
		localRotate.setTransform(t);

		Vector3d v2 = new Vector3d();
		transform.get(v2);
		v2.sub(v);
		t.set(v2);
		localTranslate.setTransform(t);
	}

	/* ************************************************************
	 * setters - attributes
	 *
	 * ***********************************************************/

	public void setLUT(int[] rLUT, int[] gLUT, int[] bLUT, int[] aLUT) {
		this.rLUT = rLUT;
		this.gLUT = gLUT;
		this.bLUT = bLUT;
		this.aLUT = aLUT;
		if(contentNode != null)
			contentNode.lutUpdated(rLUT, gLUT, bLUT, aLUT);
	}

	public void setChannels(boolean[] channels) {
		boolean channelsChanged = channels[0] != this.channels[0] ||
				channels[1] != this.channels[1] ||
				channels[2] != this.channels[2];
		if(!channelsChanged)
			return;
		this.channels = channels;
		if(contentNode != null)
			contentNode.channelsUpdated(channels);
	}

	public void setThreshold(int th) {
		if(th != threshold) {
			this.threshold = th;
			if(contentNode != null)
				contentNode.thresholdUpdated(threshold);
		}
	}

	public void setShaded(boolean b) {
		if(b != shaded) {
			this.shaded = b;
			if(contentNode != null)
				contentNode.shadeUpdated(shaded);
		}
	}

	public boolean isShaded() {
		return shaded;
	}

	public void setSaturatedVolumeRendering(boolean b) {
		if(contentNode != null && type == VOLUME) {
			((VoltexGroup)contentNode)
				.getRenderer()
				.getVolume()
				.setSaturatedVolumeRendering(b);
		}
	}

	public boolean isSaturatedVolumeRendering() {
		return contentNode != null &&
			type == VOLUME &&
			((VoltexGroup)contentNode)
				.getRenderer()
				.getVolume()
				.isSaturatedVolumeRendering();
	}

	public void applySurfaceColors(ImagePlus imp) {
		if(contentNode == null)
			return;
		CustomMesh mesh = null;
		switch(type) {
			case SURFACE:
				mesh = ((MeshGroup)contentNode).getMesh();
				break;
			case CUSTOM:
				mesh = ((CustomMeshNode)contentNode).getMesh();
				break;
		}
		if(mesh == null)
			return;
		mesh.loadSurfaceColorsFromImage(imp);
	}

	public void setColor(Color3f color) {
		if ((this.color == null && color == null) ||
				(this.color != null && color != null &&
				 this.color.equals(color)))
			return;
		this.setTrueColor(color);
		this.color = color;
 		plShape.setColor(color);
		if(contentNode != null)
			contentNode.colorUpdated(this.color);
	}

	public void setTempColor(Color3f color) {
		if ((this.color == null && color == null) ||
				(this.color != null && color != null &&
				 this.color.equals(color)))
			return;
		this.color = color;
 		plShape.setColor(color);
		if(contentNode != null)
			contentNode.colorUpdated(this.color);
	}

	public synchronized void setTransparency(float transparency) {
		transparency = transparency < 0 ? 0 : transparency;
		transparency = transparency > 1 ? 1 : transparency;
		if(Math.abs(transparency - this.transparency) < 0.01)
			return;
		this.transparency = transparency;
		if(contentNode != null){
			CustomMesh mesh = null;
			switch(type) {
			case SURFACE:
				mesh = ((MeshGroup)contentNode).getMesh();
				break;
			case CUSTOM:
				mesh = ((CustomMeshNode)contentNode).getMesh();
				break;
			}
			if(mesh == null)
				return;
			mesh.setTransparency(transparency);
			contentNode.transparencyUpdated(this.transparency);
		}

	}

	/* ************************************************************
	 * UniverseListener interface
	 *
	 *************************************************************/
	
	public void transformationStarted(View view) {}
	
	public void contentAdded(Content c, boolean updateNow) {}
	
	public void contentRemoved(Content c, boolean updateNow) {
		if(plDialog != null)
			plDialog.removePointList(plPanel);
	}
	
	public void canvasResized() {}
	
	public void contentSelected(Content c, boolean updateNow) {}
	
	public void contentChanged(Content c, boolean updateNow) {}

	
	public void universeClosed() {
		if(plDialog != null)
			plDialog.removePointList(plPanel);
	}

	
	public void transformationUpdated(View view) {
		eyePtChanged(view);
	}

	
	public void transformationFinished(View view) {
		eyePtChanged(view);
	}

	public void eyePtChanged(View view) {
		if(contentNode != null)
			contentNode.eyePtChanged(view);
	}

	/* *************************************************************
	 * getters
	 *
	 **************************************************************/
	
	public String getName() {
		return name + "_#" + timepoint;
	}

	public int getTimepoint() {
		return timepoint;
	}

	public int getType() {
		return type;
	}

	public ContentNode getContentNode() {
		return contentNode;
	}

	public ImagePlus getImage() {
		return image;
	}

	public boolean[] getChannels() {
		return channels;
	}

	public void getRedLUT(int[] l) {
		System.arraycopy(rLUT, 0, l, 0, rLUT.length);
	}

	public void getGreenLUT(int[] l) {
		System.arraycopy(gLUT, 0, l, 0, gLUT.length);
	}

	public void getBlueLUT(int[] l) {
		System.arraycopy(bLUT, 0, l, 0, bLUT.length);
	}

	public void getAlphaLUT(int[] l) {
		System.arraycopy(aLUT, 0, l, 0, aLUT.length);
	}

	public Color3f getColor() {
		return color;
	}

	public int getThreshold() {
		return threshold;
	}

	public float getTransparency() {
		return transparency;
	}

	public int getResamplingFactor() {
		return resamplingF;
	}

	public TransformGroup getLocalRotate() {
		return localRotate;
	}

	public TransformGroup getLocalTranslate() {
		return localTranslate;
	}

	public void getLocalRotate(Transform3D t) {
		localRotate.getTransform(t);
	}

	public void getLocalTranslate(Transform3D t) {
		localTranslate.getTransform(t);
	}

	public boolean isLocked() {
		return locked;
	}

	public boolean isVisible() {
		return visible;
	}

	public boolean hasCoord() {
		return coordVisible;
	}

	public boolean hasBoundingBox() {
		return bbVisible;
	}

	public boolean isPLVisible() {
		return showPL;
	}

	public Color3f getTrueColor() {
		return trueColor;
	}

	public void setTrueColor(Color3f trueColor) {
		this.trueColor = trueColor;
	}
}

