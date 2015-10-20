package ij3d;

import ij.IJ;

import ij3d.pointlist.PointListDialog;
import ij.ImagePlus;

import ij.io.FileInfo;
import ij.io.OpenDialog;
import ij.io.SaveDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import vib.PointList;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.View;

import javax.vecmath.Vector3f;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;

import java.util.TreeMap;
import java.util.HashMap;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Content extends BranchGroup implements UniverseListener, ContentConstants, AxisConstants {

	private HashMap<Integer, Integer> timepointToSwitchIndex;
	private TreeMap<Integer, ContentInstant> contentInstants;
	private int currentTimePoint;
	private Switch contentSwitch;
	private boolean showAllTimepoints = false;
	private final String name;
	private boolean showPointList = false;

	private final boolean swapTimelapseData;

	public Content(String name) {
		this(name, 0);
	}

	public Content(String name, int tp) {
		this.name = name;
		this.swapTimelapseData = false;
		setCapability(BranchGroup.ALLOW_DETACH);
		setCapability(BranchGroup.ENABLE_PICK_REPORTING);
		setTimepointToSwitchIndex(new HashMap<Integer, Integer>());
		contentInstants = new TreeMap<Integer, ContentInstant>();
		ContentInstant ci = new ContentInstant(name + "_#" + tp);
		ci.timepoint = tp;
		contentInstants.put(tp, ci);
		getTimepointToSwitchIndex().put(tp, 0);
		setContentSwitch(new Switch());
		getContentSwitch().setCapability(Switch.ALLOW_SWITCH_WRITE);
		getContentSwitch().setCapability(Switch.ALLOW_CHILDREN_WRITE);
		getContentSwitch().setCapability(Switch.ALLOW_CHILDREN_EXTEND);
		getContentSwitch().addChild(ci);
		addChild(getContentSwitch());
	}

	public Content(String name, TreeMap<Integer, ContentInstant> contents) {
		this(name, contents, false);
	}

	public Content(String name, TreeMap<Integer, ContentInstant> instants, boolean swapTimelapseData) {
		this.name = name;
		this.swapTimelapseData = swapTimelapseData;
		setCapability(BranchGroup.ALLOW_DETACH);
		setCapability(BranchGroup.ENABLE_PICK_REPORTING);
		this.contentInstants = instants;
		setTimepointToSwitchIndex(new HashMap<Integer, Integer>());
		setContentSwitch(new Switch());
		getContentSwitch().setCapability(Switch.ALLOW_SWITCH_WRITE);
		getContentSwitch().setCapability(Switch.ALLOW_CHILDREN_WRITE);
		getContentSwitch().setCapability(Switch.ALLOW_CHILDREN_EXTEND);
		for(int i : instants.keySet()) {
			ContentInstant c = instants.get(i);
			c.timepoint = i;
			getTimepointToSwitchIndex().put(i, getContentSwitch().numChildren());
			getContentSwitch().addChild(c);
		}
		addChild(getContentSwitch());
	}

	// replace if timepoint is already present
	public void addInstant(ContentInstant ci) {
		int timepoint = ci.timepoint;
//		ci.detach();
		contentInstants.put(timepoint, ci);
		if(!contentInstants.containsKey(timepoint)) {
			getTimepointToSwitchIndex().put(timepoint, getContentSwitch().numChildren());
			getContentSwitch().addChild(ci);
		} else {
			int switchIdx = getTimepointToSwitchIndex().get(timepoint);
			getContentSwitch().setChild(ci, switchIdx);
		}
	}

	public void removeInstant(int timepoint) {
		if(!contentInstants.containsKey(timepoint))
			return;
		int sIdx = getTimepointToSwitchIndex().get(timepoint);
		getContentSwitch().removeChild(sIdx);
		contentInstants.remove(timepoint);
		getTimepointToSwitchIndex().remove(timepoint);
		// update the following switch indices.
		for(int i = sIdx; i < getContentSwitch().numChildren(); i++) {
			ContentInstant ci = (ContentInstant)getContentSwitch().getChild(i);
			int tp = ci.getTimepoint();
			getTimepointToSwitchIndex().put(tp, i);
		}
	}

	public ContentInstant getCurrentInstant() {
		return contentInstants.get(currentTimePoint);
	}

	public ContentInstant getInstant(int i) {
		return contentInstants.get(i);
	}

	public TreeMap<Integer, ContentInstant> getInstants() {
		return contentInstants;
	}

	public void showTimepoint(int tp) {
		showTimepoint(tp, false);
	}

	public void showTimepoint(int tp, boolean force) {
		if(tp == currentTimePoint && !force)
			return;
		ContentInstant old = getCurrentInstant();
		if(old != null && !showAllTimepoints) {
			if(swapTimelapseData)
				old.swapDisplayedData();
			if (!showAllTimepoints) {
				ContentInstant next = contentInstants.get(tp);
				if (next != null)
					next.showPointList(showPointList);
			}
			getCurrentInstant().showPointList(false);
		}
		currentTimePoint = tp;
		if(showAllTimepoints)
			return;
		ContentInstant next = getCurrentInstant();
		if(next != null && swapTimelapseData)
				next.restoreDisplayedData();

		Integer idx = getTimepointToSwitchIndex().get(tp);
		if(idx == null)
			getContentSwitch().setWhichChild(Switch.CHILD_NONE);
		else
			getContentSwitch().setWhichChild(idx);
	}

	public void setShowAllTimepoints(boolean b) {
		this.showAllTimepoints = b;
		if(b) {
			getContentSwitch().setWhichChild(Switch.CHILD_ALL);
			return;
		}
		Integer idx = getTimepointToSwitchIndex().get(currentTimePoint);
		if(idx == null)
			getContentSwitch().setWhichChild(Switch.CHILD_NONE);
		else
			getContentSwitch().setWhichChild(idx);
	}

	public boolean getShowAllTimepoints() {
		return showAllTimepoints;
	}

	public int getNumberOfInstants() {
		return contentInstants.size();
	}

	public boolean isVisibleAt(int tp) {
		return contentInstants.containsKey(tp);
	}

	public int getStartTime() {
		return contentInstants.firstKey();
	}

	public int getEndTime() {
		return contentInstants.lastKey();
	}


	// ==========================================================
	// From here begins the 'Content Instant interface', i.e.
	// methods which are delegated to the individual
	// ContentInstants.
	//
	public void displayAs(int type) {
		for(ContentInstant c : contentInstants.values())
			c.displayAs(type);
	}

	public static int getDefaultThreshold(ImagePlus imp, int type) {
		return ContentInstant.getDefaultThreshold(imp, type);
	}

	public static int getDefaultResamplingFactor(ImagePlus imp, int type) {
		return ContentInstant.getDefaultResamplingFactor(imp, type);
	}

	public void display(ContentNode node) {
		for(ContentInstant c : contentInstants.values())
			c.display(node);
	}

	public ImagePlus exportTransformed() {
		return getCurrentInstant().exportTransformed();
	}

	/* ************************************************************
	 * setters - visibility flags
	 *
	 * ***********************************************************/

	public void setVisible(boolean b) {
		for(ContentInstant c : contentInstants.values())
			c.setVisible(b);
	}

	public void showBoundingBox(boolean b) {
		for(ContentInstant c : contentInstants.values())
			c.showBoundingBox(b);
	}


	public void showCoordinateSystem(boolean b) {
		for(ContentInstant c : contentInstants.values())
			c.showCoordinateSystem(b);
	}

	public void setSelected(boolean selected) {
		// TODO really all?
		for(ContentInstant c : contentInstants.values())
			c.setSelected(selected);
	}

	/* ************************************************************
	 * point list
	 *
	 * ***********************************************************/
	public void setPointListDialog(PointListDialog p) {
		for(ContentInstant c : contentInstants.values())
			c.setPointListDialog(p);
	}

	public void showPointList(boolean b) {
		getCurrentInstant().showPointList(b);
		this.showPointList = b;
	}

	protected final static Pattern startFramePattern =
		Pattern.compile("(?s)(?m).*?^(# frame:? (\\d+)\n).*");

	public void loadPointList() {
		String dir = null, fileName = null;
		ImagePlus image = contentInstants.firstEntry().getValue().image;
		if (image != null) {
			FileInfo fi = image.getFileInfo();
			dir = fi.directory;
			fileName = fi.fileName + ".points";
		}
		OpenDialog od = new OpenDialog("Open points annotation file", dir, fileName);
		if (od.getFileName() == null)
			return;

		File file = new File(od.getDirectory(), od.getFileName());
		try {
			String fileContents = readFile(new FileInputStream(file));
			Matcher matcher = startFramePattern.matcher(fileContents);
			if (matcher.matches()) {
				// empty point lists
				for (Integer frame : contentInstants.keySet())
					contentInstants.get(frame).setPointList(new PointList());
				while (matcher.matches()) {
					int frame = Integer.parseInt(matcher.group(2));
					fileContents = fileContents.substring(matcher.end(1));
					matcher = startFramePattern.matcher(fileContents);
					ContentInstant ci = contentInstants.get(frame);
					if (ci == null)
						continue;
					String pointsForFrame = matcher.matches() ?
						fileContents.substring(0, matcher.start(1)) : fileContents;
					PointList points = PointList.parseString(pointsForFrame);
					if (points != null)
						ci.setPointList(points);
				}
			}
			else {
				// fall back to old-style one-per-frame point lists
				PointList points = PointList.parseString(fileContents);
				if (points != null)
					getCurrentInstant().setPointList(points);
			}
			showPointList(true);
		}
		catch (IOException e) {
			IJ.error("Could not read point list from " + file);
		}
	}

	String readFile(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		for (;;) {
			int count = in.read(buffer);
			if (count < 0)
				break;
			out.write(buffer, 0, count);
		}
		in.close();
		out.close();
		return out.toString("UTF-8");
	}

	public void savePointList() {
		String dir = OpenDialog.getDefaultDirectory();
		String fileName = getName();
		ImagePlus image = contentInstants.firstEntry().getValue().image;
		if (image != null) {
			FileInfo fi = image.getFileInfo();
			dir = fi.directory;
			fileName = fi.fileName;
		}
		SaveDialog sd = new SaveDialog("Save points annotation file as...",
			dir, fileName, ".points");
		if (sd.getFileName() == null)
			return;

		File file = new File(sd.getDirectory(), sd.getFileName());
		if (file.exists() && !IJ.showMessageWithCancel("File exists", "Overwrite " + file + "?"))
			return;
		try {
			PrintStream out = new PrintStream(file);
			for (Integer frame : contentInstants.keySet()) {
				ContentInstant ci = contentInstants.get(frame);
				if (ci.getPointList().size() != 0) {
					out.println("# frame " + frame);
					ci.savePointList(out);
				}
			}
			out.close();
		}
		catch (IOException e) {
			IJ.error("Could not save points to " + file);
		}
	}

	/**
	 * @deprecated
	 * @param p
	 */
	public void addPointListPoint(Point3d p) {
		getCurrentInstant().addPointListPoint(p);
	}

	/**
	 * @deprecated
	 * @param i
	 * @param pos
	 */
	public void setListPointPos(int i, Point3d pos) {
		getCurrentInstant().setListPointPos(i, pos);
	}

	public float getLandmarkPointSize() {
		return getCurrentInstant().getLandmarkPointSize();
	}

	public void setLandmarkPointSize(float r) {
		for(ContentInstant c : contentInstants.values())
			c.setLandmarkPointSize(r);
	}

	public Color3f getLandmarkColor() {
		return getCurrentInstant().getLandmarkColor();
	}

	public void setLandmarkColor(Color3f color) {
		for(ContentInstant c : contentInstants.values())
			c.setLandmarkColor(color);
	}

	public PointList getPointList() {
		return getCurrentInstant().getPointList();
	}

	/**
	 * @deprecated
	 * @param i
	 */
	public void deletePointListPoint(int i) {
		getCurrentInstant().deletePointListPoint(i);
	}

	/* ************************************************************
	 * setters - transform
	 *
	 **************************************************************/
	public void toggleLock() {
		for(ContentInstant c : contentInstants.values())
			c.toggleLock();
	}

	public void setLocked(boolean b) {
		for(ContentInstant c : contentInstants.values())
			c.setLocked(b);
	}

	public void applyTransform(double[] matrix) {
		applyTransform(new Transform3D(matrix));
	}

	public void applyTransform(Transform3D transform) {
		for(ContentInstant c : contentInstants.values())
			c.applyTransform(transform);
	}

	public void applyRotation(int axis, double degree) {
		Transform3D t = new Transform3D();
		switch(axis) {
			case X_AXIS: t.rotX(deg2rad(degree)); break;
			case Y_AXIS: t.rotY(deg2rad(degree)); break;
			case Z_AXIS: t.rotZ(deg2rad(degree)); break;
		}
		applyTransform(t);
	}

	public void applyTranslation(float dx, float dy, float dz) {
		Transform3D t = new Transform3D();
		t.setTranslation(new Vector3f(dx, dy, dz));
		applyTransform(t);
	}

	public void setTransform(double[] matrix) {
		setTransform(new Transform3D(matrix));
	}

	public void setTransform(Transform3D transform) {
		for(ContentInstant c : contentInstants.values())
			c.setTransform(transform);
	}

	public void setRotation(int axis, double degree) {
		Transform3D t = new Transform3D();
		switch(axis) {
			case X_AXIS: t.rotX(deg2rad(degree)); break;
			case Y_AXIS: t.rotY(deg2rad(degree)); break;
			case Z_AXIS: t.rotZ(deg2rad(degree)); break;
		}
		setTransform(t);
	}

	public void setTranslation(float dx, float dy, float dz) {
		Transform3D t = new Transform3D();
		t.setTranslation(new Vector3f(dx, dy, dz));
		setTransform(t);
	}

	private double deg2rad(double deg) {
		return deg * Math.PI / 180.0;
	}

	/* ************************************************************
	 * setters - attributes
	 *
	 * ***********************************************************/

	public void setChannels(boolean[] channels) {
		for(ContentInstant c : contentInstants.values())
			c.setChannels(channels);
	}

	public void setLUT(int[] r, int[] g, int[] b, int[] a) {
		for(ContentInstant c : contentInstants.values())
			c.setLUT(r, g, b, a);
	}

	public void setThreshold(int th) {
		for(ContentInstant c : contentInstants.values())
			c.setThreshold(th);
	}

	public void setShaded(boolean b) {
		for(ContentInstant c : contentInstants.values())
			c.setShaded(b);
	}

	public void setSaturatedVolumeRendering(boolean b) {
		for(ContentInstant c : contentInstants.values())
			c.setSaturatedVolumeRendering(b);
	}

	public void applySurfaceColors(ImagePlus img) {
		for(ContentInstant c : contentInstants.values())
			c.applySurfaceColors(img);
	}

	public void setColor(Color3f color) {
		for(ContentInstant c : contentInstants.values())
			c.setColor(color);
	}

	public synchronized void setTransparency(float transparency) {
		for(ContentInstant c : contentInstants.values())
			c.setTransparency(transparency);
	}

	/* ************************************************************
	 * UniverseListener interface
	 *
	 *************************************************************/
	public void transformationStarted(View view) {}
	public void contentAdded(Content c) {}
	public void contentRemoved(Content c) {
		for(ContentInstant co : contentInstants.values()) {
			co.contentRemoved(c);
		}
	}
	public void canvasResized() {}
	public void contentSelected(Content c) {}
	public void contentChanged(Content c) {}

	public void universeClosed() {
		for(ContentInstant c : contentInstants.values()) {
			c.universeClosed();
		}
	}

	public void transformationUpdated(View view) {
		eyePtChanged(view);
	}

	public void transformationFinished(View view) {
		eyePtChanged(view);
		// apply same transformation to all other time points
		// in case this content was transformed
		ContentInstant curr = getCurrentInstant();
		if(curr == null || !curr.selected)
			return;
		Transform3D t = new Transform3D();
		Transform3D r = new Transform3D();
		curr.getLocalTranslate(t);
		curr.getLocalRotate(r);

		for(ContentInstant c : contentInstants.values()) {
			if(c == getCurrentInstant())
				continue;
			c.getLocalRotate().setTransform(r);
			c.getLocalTranslate().setTransform(t);
			c.transformationFinished(view);
		}
	}

	public void eyePtChanged(View view) {
		for(ContentInstant c : contentInstants.values())
			c.eyePtChanged(view);
	}

	/* *************************************************************
	 * getters
	 *
	 **************************************************************/
	@Override
	public String getName() {
		return name;
	}

	public int getType() {
		return getCurrentInstant().getType();
	}

	public ContentNode getContentNode() {
		return getCurrentInstant().getContentNode();
	}

	public void getMin(Point3d min) {
		min.set(Double.MAX_VALUE,
			Double.MAX_VALUE,
			Double.MAX_VALUE);
		Point3d tmp = new Point3d();
		for(ContentInstant c : contentInstants.values()) {
			c.getContentNode().getMin(tmp);
			if(tmp.x < min.x) min.x = tmp.x;
			if(tmp.y < min.y) min.y = tmp.y;
			if(tmp.z < min.z) min.z = tmp.z;
		}
	}

	public void getMax(Point3d max) {
		max.set(Double.MIN_VALUE,
			Double.MIN_VALUE,
			Double.MIN_VALUE);
		Point3d tmp = new Point3d();
		for(ContentInstant c : contentInstants.values()) {
			c.getContentNode().getMax(tmp);
			if(tmp.x > max.x) max.x = tmp.x;
			if(tmp.y > max.y) max.y = tmp.y;
			if(tmp.z > max.z) max.z = tmp.z;
		}
	}

	public ImagePlus getImage() {
		return getCurrentInstant().getImage();
	}

	public boolean[] getChannels() {
		return getCurrentInstant().getChannels();
	}

	public void getRedLUT(int[] l) {
		getCurrentInstant().getRedLUT(l);
	}

	public void getGreenLUT(int[] l) {
		getCurrentInstant().getGreenLUT(l);
	}

	public void getBlueLUT(int[] l) {
		getCurrentInstant().getBlueLUT(l);
	}

	public void getAlphaLUT(int[] l) {
		getCurrentInstant().getAlphaLUT(l);
	}

	public Color3f getColor() {
		return getCurrentInstant().getColor();
	}

	public boolean isShaded() {
		return getCurrentInstant().isShaded();
	}

	public boolean isSaturatedVolumeRendering() {
		return getCurrentInstant().isSaturatedVolumeRendering();
	}

	public int getThreshold() {
		return getCurrentInstant().getThreshold();
	}

	public float getTransparency() {
		return getCurrentInstant().getTransparency();
	}

	public int getResamplingFactor() {
		return getCurrentInstant().getResamplingFactor();
	}

	public TransformGroup getLocalRotate() {
		return getCurrentInstant().getLocalRotate();
	}

	public TransformGroup getLocalTranslate() {
		return getCurrentInstant().getLocalTranslate();
	}

	public void getLocalRotate(Transform3D t) {
		getCurrentInstant().getLocalRotate(t);
	}

	public void getLocalTranslate(Transform3D t) {
		getCurrentInstant().getLocalTranslate(t);
	}

	public boolean isLocked() {
		return getCurrentInstant().isLocked();
	}

	public boolean isVisible() {
		return getCurrentInstant().isVisible();
	}

	public boolean hasCoord() {
		return getCurrentInstant().hasCoord();
	}

	public boolean hasBoundingBox() {
		return getCurrentInstant().hasBoundingBox();
	}

	public boolean isPLVisible() {
		return getCurrentInstant().isPLVisible();
	}

	public HashMap<Integer, Integer> getTimepointToSwitchIndex() {
		return timepointToSwitchIndex;
	}

	public void setTimepointToSwitchIndex(HashMap<Integer, Integer> timepointToSwitchIndex) {
		this.timepointToSwitchIndex = timepointToSwitchIndex;
	}

	public Switch getContentSwitch() {
		return contentSwitch;
	}

	public void setContentSwitch(Switch contentSwitch) {
		this.contentSwitch = contentSwitch;
	}
}

