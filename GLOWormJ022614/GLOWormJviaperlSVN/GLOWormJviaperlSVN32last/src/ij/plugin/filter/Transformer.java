package ij.plugin.filter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;

import java.awt.*;
import java.awt.image.*;

/** Implements the Flip and Rotate commands in the Image/Transform submenu. */
public class Transformer implements PlugInFilter {
	
	ImagePlus imp;
	String arg;

	public int setup(String arg, ImagePlus imp) {
		this.arg = arg;
		this.imp = imp;
//		if (arg.equals("fliph") || arg.equals("flipv"))
//			return IJ.setupDialog(imp, DOES_ALL+NO_UNDO);
//		else
			return DOES_ALL+NO_UNDO+NO_CHANGES;
	}

	public void run(ImageProcessor ip) {
		if (arg.equals("fliph")) {
			if (imp.getStack() instanceof VirtualStack) {
				imp.getStack().setFlipH(!imp.getStack().isFlipH());
				imp.updateAndRepaintWindow();
			} else {
				ip.flipHorizontal();
			}
			return;
		}
		if (arg.equals("flipv")) {
			if (imp.getStack() instanceof VirtualStack) {
				imp.getStack().setFlipV(!imp.getStack().isFlipV());
				imp.updateAndRepaintWindow();
			} else {
				ip.flipVertical();
			}
			return;
		}
		if (arg.equals("flipz")) {
			if (imp.getStack() instanceof VirtualStack) {
				imp.getStack().setFlipZ(!imp.getStack().isFlipZ());
				imp.updateAndRepaintWindow();
			} else {
				IJ.run(imp, "StackReverser", "");
			}
			return;
		}
		if (arg.equals("right") || arg.equals("left")) {
			if (imp.getStack() instanceof VirtualStack) {
				if (arg.equals("right")) {
					imp.getStack().setRotateRight(!imp.getStack().isRotateRight());
				}
				if (arg.equals("left")) {
					imp.getStack().setRotateLeft(!imp.getStack().isRotateLeft());
				}
	//????????????
				imp = new ImagePlus(imp.getTitle(), imp.getProcessor().rotateLeft());
				imp.show();
	//??????????????
				imp.updateAndRepaintWindow();
			} else {
				StackProcessor sp = new StackProcessor(imp.getStack(), ip);
				ImageStack s2 = null;
				if (arg.equals("right"))
					s2 = sp.rotateRight();
				else
					s2 = sp.rotateLeft();
				Calibration cal = imp.getCalibration();
				imp.setStack(null, s2);
				double pixelWidth = cal.pixelWidth;
				cal.pixelWidth = cal.pixelHeight;
				cal.pixelHeight = pixelWidth;
				return;
			}
		}
	}
	
}
