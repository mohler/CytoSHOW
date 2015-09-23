package org.vcell.gloworm;

import ij.*;
import ij.process.*;
import ij.gui.*;

import java.awt.*;

import ij.plugin.*;
import ij.plugin.frame.*;

public class Save_JPEGsnapshot implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		ImageWindow win = imp.getWindow();

		win.toFront();
		IJ.wait(500);
		Point loc = win.getLocation();
		ImageCanvas ic = win.getCanvas();
		Rectangle boundsForCapture = ic.getBounds();
		loc.x += boundsForCapture.x;
		loc.y += boundsForCapture.y;
		Rectangle r = new Rectangle(loc.x, loc.y, boundsForCapture.width, boundsForCapture.height);
		Robot robot;
		try {
			robot = new Robot();
			Image img = robot.createScreenCapture(r);
			ImagePlus imp2 = null;
			if (img!=null) {
				String title2 = WindowManager.getUniqueName(imp.getTitle());
				imp2 = new ImagePlus(title2, img);
			}
			imp2.show();
			WindowManager.setWindow(imp2.getWindow());
			IJ.run("Jpeg...", null);
//			imp2.getWindow().close();
		} catch (AWTException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
