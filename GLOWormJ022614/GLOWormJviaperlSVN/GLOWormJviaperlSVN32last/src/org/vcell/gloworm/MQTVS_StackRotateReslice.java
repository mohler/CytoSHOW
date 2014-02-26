package org.vcell.gloworm;
import java.awt.Frame;
import java.awt.image.ColorModel;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;


public class MQTVS_StackRotateReslice  implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();


		if (imp.getNFrames() > 1 || imp.getRoi() != null) {
			double inMin = imp.getDisplayRangeMin();
			double inMax = imp.getDisplayRangeMax();
			ColorModel cm = imp.getProcessor().getColorModel();
			int inChannel = imp.getChannel();
			int inSlice = imp.getSlice();
			int inFrame = imp.getFrame();

			imp.getWindow().setVisible(false);
			Frame rm =WindowManager.getFrame("Tag Manager");
			if (rm != null) rm.setVisible(false);
			Frame mcc = imp.getMultiChannelController();
			if (mcc != null) mcc.setVisible(false);
			
			ImagePlus impD = (new MQTVS_Duplicator()).run(imp, imp.getChannel(), imp.getChannel(), 
					1, imp.getNSlices(), 
					imp.getFrame(), imp.getFrame(), 1, false);

			imp.getWindow().setVisible(true);
			if (rm != null) rm.setVisible(true);
			if (mcc != null) mcc.setVisible(true);

			impD.setCalibration(imp.getCalibration());
			impD.getProcessor().setColorModel(cm);
			impD.setDisplayRange(inMin, inMax);
			impD.setPosition(inChannel, inSlice, 1);

			imp.getProcessor().setColorModel(cm);
			imp.setDisplayRange(inMin, inMax);
			imp.setPosition(inChannel, inSlice, inFrame);


			impD.show();
		}

		IJ.runPlugIn("Stack_Rotate", "");

	}
}
