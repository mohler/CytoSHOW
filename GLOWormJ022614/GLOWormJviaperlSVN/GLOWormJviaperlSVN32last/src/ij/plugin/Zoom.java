package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.measure.*;
import ij.plugin.frame.RoiManager;

import java.awt.*;

/** This plugin implements the commands in the Image/Zoom submenu. */
public class Zoom implements PlugIn{

	/** 'arg' must be "in", "out", "100%" or "orig". */
	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
		ImageCanvas2 ic = imp.getCanvas();
		if (ic==null) return;
		Point loc = ic.getCursorLoc();
		if (!ic.cursorOverImage()) {
			Rectangle srcRect = ic.getSrcRect();
			loc.x = srcRect.x + srcRect.width/2;
			loc.y = srcRect.y + srcRect.height/2;
		}
		int x = ic.screenX(loc.x);
		int y = ic.screenY(loc.y);
    	if (arg.equals("in")) {
 			ic.zoomIn(x, y);
//			if (ic.getMagnification()<=1.0) imp.repaintWindow();
    	} else if (arg.equals("out")) {
			ic.zoomOut(x, y);
//			if (ic.getMagnification()<1.0) imp.repaintWindow();
    	} else if (arg.equals("orig"))
			ic.unzoom();
    	else if (arg.equals("100%"))
    		ic.zoom100Percent();
		else if (arg.equals("to"))
			zoomToSelection(imp, ic);
		else if (arg.equals("set"))
			setZoom(imp, ic);
		else if (arg.equals("max")) {
			ImageWindow win = imp.getWindow();
			win.setBounds(win.getMaximumBounds());
			win.maximize();
		}
	}
	
	void zoomToSelection(ImagePlus imp, ImageCanvas2 ic) {
		Roi roi = imp.getRoi();
		ic.unzoom();
		if (roi==null) 
			return;
		Rectangle w = imp.getWindow().getBounds();
		Rectangle r = roi.getBounds();
		double mag = ic.getMagnification();
		int marginw = (int)((w.width - mag * imp.getWidth()));
		int marginh = (int)((w.height - mag * imp.getHeight()));
		int x = r.x+r.width/2;
		int y = r.y+r.height/2;
		mag = ic.getHigherZoomLevel(mag);
		boolean reShow = imp.getRoiManager().isShowAll();
		if (reShow)
			imp.getRoiManager().showAll(RoiManager.SHOW_NONE);
		while(r.width*mag*10<w.width - marginw && r.height*mag*2<w.height - marginh) {
			ic.zoomIn(ic.screenX(x), ic.screenY(y));
			double cmag = ic.getMagnification();
			if (cmag==32.0) break;
			mag = ic.getHigherZoomLevel(cmag);
			w = imp.getWindow().getBounds();
		}
		if (reShow)
			imp.getRoiManager().showAll(RoiManager.SHOW_ALL);	
		imp.setRoi(roi);
	}
	
	/** Based on Albert Cardona's ZoomExact plugin:
		http://albert.rierol.net/software.html */
	void setZoom(ImagePlus imp, ImageCanvas2 ic) {
		int x = imp.getWidth()/2;
		int y = imp.getHeight()/2;
		ImageWindow win = imp.getWindow();
		GenericDialog gd = new GenericDialog("Set Zoom");
		gd.addNumericField("Zoom:", ic.getMagnification() * 200, 0, 4, "%");
		gd.addNumericField("X center:", x, 0, 5, "");
		gd.addNumericField("Y center:", y, 0, 5, "");
		gd.showDialog();
		if (gd.wasCanceled()) return;
		double mag = gd.getNextNumber()/100.0;
		x = (int)gd.getNextNumber();
		y = (int)gd.getNextNumber();
		if (x<0) x=0;
		if (y<0) y=0;
		if (x>=imp.getWidth()) x=imp.getWidth()-1;
		if (y>=imp.getHeight()) y=imp.getHeight()-1;
		if (mag<=0.0) mag = 1.0;
		win.getCanvas().setMagnification(mag);
//		double w = imp.getWidth()*mag;
//		double h = imp.getHeight()*mag;
		int w = ic.getWidth();
		int h = ic.getHeight();
		Dimension screen = IJ.getScreenSize();
		if (w>screen.width-20) w = screen.width - 20;  // does it fit?
		if (h>screen.height-50) h = screen.height - 50;
		int width = (int) Math.floor((w/mag));
//		while (width*mag>w) w--;
		int height = (int) Math.floor((h/mag));
//		while (height*mag>h) h--;
		x -= width/2;
		y -= height/2;
		if (x<0) x=0;
		if (y<0) y=0;
		if(x+width > imp.getWidth()) x= imp.getWidth()-width;
		if(y+height > imp.getHeight()) y= imp.getHeight()-height;
		ic.setSourceRect(new Rectangle(x, y, width, height));
		ic.setDrawingSize((int)w, (int)h);
// 		IJ.log(""+ w+" "+width+" "+h+" "+height+" "+mag);
		win.pack();
		ic.repaint();
		ic.zoomIn(x, y);
		ic.zoomOut(x, y);
	}
	
}

