package ij3d.gui;
import ij.*;
import ij.gui.GenericDialog;
import ij.plugin.Colors;
import ij.io.RoiDecoder;
import ij.process.FloatPolygon;
import ij3d.ContentInstant;
import ij.measure.*;
import ij.plugin.filter.Analyzer;
import java.awt.*;
import java.util.*;

import javax.vecmath.Color3f;


 /** Displays a dialog that allows the user to specify ROI properties such as color and line width. */
public class ContentInstantProperties {
	private ContentInstant ci;
	private String title;
	private boolean showName = true;
	private boolean showListCoordinates;
	private boolean addToOverlay;
	private boolean overlayOptions;
	private boolean existingOverlay;
	private boolean setPositions;
	private boolean listCoordinates;
	private static final String[] justNames = {"Left", "Center", "Right"};

	/** Constructs a ColorChooser using the specified title and initial color. */
	//Doesn't actually seem to do so...
	public ContentInstantProperties(String title, ContentInstant ci) {
		if (ci==null)
			throw new IllegalArgumentException("ROI is null");
		this.title = title;
		showName = title.startsWith("Prop");
		showListCoordinates = showName && title.endsWith(" ");
		addToOverlay = title.equals("Add to Overlay");
		overlayOptions = title.equals("Overlay Options");
		ImagePlus imp = WindowManager.getCurrentImage();
		this.ci = ci;
	}
	
	private String decodeColor(Color color, Color defaultColor) {
		if (color==null)
			color = defaultColor;
		String str = "#"+Integer.toHexString(color.getRGB());
//		if (str.length()==9 && str.startsWith("#ff"))
//			str = "#"+str.substring(3);
		String lc = Colors.hexToColor(str);
		if (lc!=null) str = lc;
		return str;
	}
	
	/** Displays the dialog box and returns 'false' if the user cancels it. */
	public boolean showDialog() {
		Color strokeColor = null;
		Color fillColor = null;
		double strokeWidth = 1.0;
		String name= ci.getName();
		boolean isRange = name!=null && name.startsWith("range: ");
		String nameLabel = isRange?"Range:":"Name:";
		if (isRange) name = name.substring(7);
		if (name==null) name = "";
		if (ci.getColor()!=null) fillColor = ci.getColor().get();

		String fillc = fillColor!=null?"#"+Integer.toHexString(fillColor.getRGB()):"none";
		if (IJ.isMacro()) fillc = "none";
		int digits = (int)strokeWidth==strokeWidth?0:1;
		GenericDialog gd = new GenericDialog(title);
		if (showName)
			gd.addStringField(nameLabel, name, 15);
		gd.addMessage("");
		gd.addStringField("Fill color: ", fillc);

		gd.showDialog();
		if (gd.wasCanceled()) return false;
		if (showName) {
			name = gd.getNextString();
			if (!isRange) ci.setName(name.length()>0?name:null);
		}
		fillc = gd.getNextString();
		boolean applyToOverlay = false;
		boolean newOverlay = false;
		String alphaPrefix = "#ff";
		long alphaLong =0;
		if (fillc.length()==9 || fillc.length()==8) {
			alphaPrefix = fillc.substring(0,3);
			 alphaLong = (Long.parseLong(alphaPrefix.replace("#",""),16)); 
			fillColor = Colors.decode(fillc, fillColor);
		}else if (fillc.length()==7 || fillc.length()==8) {
			 alphaLong = (Long.parseLong(alphaPrefix.replace("#",""),16)); 
			fillColor = Colors.decode(fillc, fillColor);
		}else if (fillc.length()==3 || fillc.length()==2) {
			alphaPrefix = fillc;
			 alphaLong = (Long.parseLong(alphaPrefix.replace("#",""),16)); 
			fillColor = Colors.decode(fillc+"f0f0f0", fillColor);
		}
		if (fillColor!=null) {
			ci.setTransparency(1f-alphaLong/255f);
			ci.setColor(new Color3f(fillColor));
		}
		if (newOverlay) ci.setName("new-overlay");
		if (applyToOverlay) {
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp==null)
				return true;
		}
		//if (strokeWidth>1.0 && !roi.isDrawingTool())
		//	Line.setWidth(1);
		return true;
	}
		
	
	
}
