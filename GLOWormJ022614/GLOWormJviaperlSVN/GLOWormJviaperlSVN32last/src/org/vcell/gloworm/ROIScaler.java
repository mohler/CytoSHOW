package org.vcell.gloworm;

import java.awt.Color;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.Colors;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;

public class ROIScaler implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		RoiManager rm = imp.getRoiManager();
		int size = (int) IJ.getNumber("Size?", 2);
		for(Object roiKey:rm.getROIs().keySet().toArray()) {
			Roi roi = rm.getROIs().get(roiKey);
			int c = roi.getCPosition();
			int z = roi.getZPosition();
			int t = roi.getTPosition();
			String name = roi.getName();
			Color fc = roi.getFillColor();
			Color sc = roi.getStrokeColor();
			if (fc==null || fc.getRGB()==0) {
				fc = Colors.decode("88888888", Color.gray);
				sc = null;
			}
			rm.getROIsByNumbers().get(c+"_"+z+"_"+t).remove(roi);
			rm.getROIs().put(roiKey.toString(), new Roi((int)roi.getBounds().getCenterX()-size/2, (int)roi.getBounds().getCenterY()-size/2, size, size));
			roi = rm.getROIs().get(roiKey);
			roi.setName(name);
			roi.setPosition(c, z, t);
			roi.setFillColor(fc);
			roi.setStrokeColor(sc);
			rm.getROIsByNumbers().get(c+"_"+z+"_"+t).add(roi);

		}
		
	}

}
