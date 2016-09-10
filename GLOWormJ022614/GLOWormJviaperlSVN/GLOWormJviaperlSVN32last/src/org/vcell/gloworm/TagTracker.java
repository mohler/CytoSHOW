package org.vcell.gloworm;

import java.awt.Color;
import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.swing.DefaultListModel;
import javax.swing.ListModel;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.plugin.Colors;
import ij.plugin.FFT;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.FHT;
import ij.process.ImageProcessor;

public class TagTracker implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		imp.getWindow().setVisible(false);
		RoiManager rm = imp.getRoiManager();
		DefaultListModel<String> lm = rm.getListModel();

		for(int t=1;t<=imp.getNFrames();t++) {
//		for(int t=1;t<=1000;t++) {
			for (int z=1;z<=imp.getNSlices();z++) {
				ArrayList<Roi> ztRoiAL = rm.getROIsByNumbers().get("1_"+z+"_"+t);
				
				ArrayList<Roi> zt1uRoiAL = rm.getROIsByNumbers().get("1_"+z+"_"+(t+1));
				ArrayList<Roi> z1ut1uRoiAL = rm.getROIsByNumbers().get("1_"+(z+1)+"_"+(t+1));
				ArrayList<Roi> z1dt1uRoiAL = rm.getROIsByNumbers().get("1_"+(z-1)+"_"+(t+1));
				ArrayList<Roi> z2ut1uRoiAL = rm.getROIsByNumbers().get("1_"+(z+2)+"_"+(t+1));
				ArrayList<Roi> z2dt1uRoiAL = rm.getROIsByNumbers().get("1_"+(z-2)+"_"+(t+1));
				ArrayList<Roi> zAllt1uRoiAL = new ArrayList<Roi>();
				if (z2dt1uRoiAL!=null)
					zAllt1uRoiAL.addAll(z2dt1uRoiAL);
				if (z1dt1uRoiAL!=null)
					zAllt1uRoiAL.addAll(z1dt1uRoiAL);
				if (zt1uRoiAL!=null)
					zAllt1uRoiAL.addAll(zt1uRoiAL);
				if (z1ut1uRoiAL!=null)
					zAllt1uRoiAL.addAll(z1ut1uRoiAL);
				if (z2ut1uRoiAL!=null)
					zAllt1uRoiAL.addAll(z2ut1uRoiAL);
				if(ztRoiAL!=null) {
					for (Object r:ztRoiAL.toArray()) {
						Roi roi = (Roi)r;
						for (Roi roiTest:zAllt1uRoiAL) {
							if ((new ShapeRoi(new OvalRoi(roi.getBounds().getCenterX()-4,roi.getBounds().getCenterY()-4,9,9))).contains((int)roiTest.getBounds().getCenterX(), (int)roiTest.getBounds().getCenterY())) {
								if (roi.getFillColor() ==null || roi.getFillColor().getRGB()==0) {
									double rand = Math.random()+0.00000000000000001;
									String randString = (""+rand).substring((""+rand).length()-8);
									String randName="\""+randString+" \"";
									for(int i=0;i<lm.size();i++) {
										String s = lm.elementAt(i);
										if (s.equals(roi.getName())) {
											s = randName + (s).substring((s).indexOf("_"));
											lm.set(i, s);
											rm.getFullListModel().set(i, s);
											String roiOldName = roi.getName();
											roi.setName(s);
											rm.getROIs().put(s, roi);
											rm.getROIs().remove(roiOldName);
											if (rm.getROIsByName().get(randName)==null) {
												rm.getROIsByName().put(randName, new ArrayList<Roi>());
											}
											if (!rm.getROIsByName().get(randName).contains(roi)) {
												rm.getROIsByName().get(randName).add(roi);
											}
										}
									}
									roi.setFillColor(Colors.decode("#"+randString.replaceAll("(..)(......)","88$2"),Color.white));
								}
								roiTest.setFillColor(roi.getFillColor());
								for(int i=0;i<lm.size();i++) {
									String s = lm.elementAt(i);
									if (s.equals(roiTest.getName())) {
										String roiRootName = roi.getName().split("_")[0];
										String newS=roiRootName+ (s).substring((s).indexOf("_"));
										lm.set(i, newS);
										rm.getFullListModel().set(i, newS);
										String roiOldName = roiTest.getName();
										roiTest.setName(newS);
										rm.getROIs().put(newS, roi);
										rm.getROIs().remove(roiOldName);
										if (rm.getROIsByName().get(roiRootName)==null) {
											rm.getROIsByName().put(roiRootName, new ArrayList<Roi>());
										}
										if (!rm.getROIsByName().get(roiRootName).contains(roi)) {
											rm.getROIsByName().get(roiRootName).add(roi);
										}
									}
								}
							}
						}
						if(!roi.getName().startsWith("\"")) {
							ztRoiAL.remove(roi);
						}
					}
					
				}	
			}
		}
		for(int i=lm.size()-1;i>=0;i--) {
			String s = lm.elementAt(i);
			if (!s.startsWith("\"")) {
				lm.remove(i);
				rm.getFullListModel().remove(i);
				rm.getROIs().remove(s);
			}
		}
		rm.validate();
		rm.repaint();
		imp.getWindow().setVisible(true);
	}
}
