package org.vcell.gloworm;

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
import ij.plugin.FFT;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.FHT;
import ij.process.ImageProcessor;

public class MaxSeeker implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		imp.getWindow().setVisible(false);
		ImageStack newStack = new ImageStack(imp.getWidth(), imp.getHeight());
		RoiManager rm = imp.getRoiManager();
		DefaultListModel<String> lm = rm.getListModel();

		for(int t=24073;t<=imp.getNFrames();t++) {
//		for(int t=1;t<=10;t++) {
			for (int z=1;z<=imp.getNSlices();z++) {
				imp.setPositionWithoutUpdate(1, z, t);

				IJ.run(imp, "Find Maxima...", "noise=45 output=[Point Selection] exclude");
				//			impClean.getRoi().setPosition(1, 1, p);
				if (imp.getRoi()!=null) {
					rm.addRoi(imp.getRoi());
					rm.getROIs().get(lm.getElementAt(lm.getSize()-1)).setPosition(1, z, t);
					if(rm.getROIsByNumbers().get("1_"+z+"_"+t) == null)
						rm.getROIsByNumbers().put("1_"+z+"_"+t, new ArrayList<Roi>());
					rm.getROIsByNumbers().get("1_"+z+"_"+t).add(rm.getROIs().get(lm.getElementAt(lm.getSize()-1)));
				}
				

//				impClean.flush();
			}
			Hashtable<String, ArrayList<Integer>> doubleStacked = new Hashtable<String, ArrayList<Integer>>();
			for (int z=1;z<=imp.getNSlices()-1;z++) {
				ArrayList<Roi> sliceH = rm.getROIsByNumbers().get("1_"+z+"_"+t);
				ArrayList<Roi> sliceI = rm.getROIsByNumbers().get("1_"+(z+1)+"_"+t);

				if (sliceI!=null && sliceH!=null) {
					Roi iROI = sliceI.get(0);
					Polygon iROIpoly = iROI.getPolygon();
					Roi hROI = sliceH.get(0);
					Polygon hROIpoly = hROI.getPolygon();
					for (int pi=0;pi<iROIpoly.npoints;pi++) {
						OvalRoi iOval = new OvalRoi(iROIpoly.xpoints[pi]-2, iROIpoly.ypoints[pi]-4,9,9);
						for (int ph=0;ph<hROIpoly.npoints;ph++) {
							if ((new ShapeRoi(iOval)).contains(hROIpoly.xpoints[ph], hROIpoly.ypoints[ph])) {
								if (doubleStacked.get(""+hROIpoly.xpoints[ph]+"_"+hROIpoly.ypoints[ph]+"_"+z) == null) {
									doubleStacked.put(""+iROIpoly.xpoints[pi]+"_"+iROIpoly.ypoints[pi]+"_"+(z+1), new ArrayList<Integer>());
								} else {
									doubleStacked.put(""+iROIpoly.xpoints[pi]+"_"+iROIpoly.ypoints[pi]+"_"+(z+1), (ArrayList<Integer>) doubleStacked.get(""+hROIpoly.xpoints[ph]+"_"+hROIpoly.ypoints[ph]+"_"+z).clone());
									doubleStacked.remove(""+hROIpoly.xpoints[ph]+"_"+hROIpoly.ypoints[ph]+"_"+z);
								}
								doubleStacked.get(""+iROIpoly.xpoints[pi]+"_"+iROIpoly.ypoints[pi]+"_"+(z+1)).add(z+1);
							} 
						}
					}
				}
			}
			int rs = rm.getCount();
			for(int r =rs-1;r>=0;r--) {
				if (rm.getListModel().get(r).startsWith("Point")) {
					rm.select(r);
					rm.delete(false);
				
//					rm.getROIs().remove(rm.getListModel().get(r));
//					rm.getListModel().remove(r);
					
				}
			}

			for (String doubleHit2:doubleStacked.keySet()) {
				int zSpan = doubleStacked.get(doubleHit2).size();
				int x = Integer.parseInt(doubleHit2.split("_")[0]);
				int y = Integer.parseInt(doubleHit2.split("_")[1]);
				int z = Integer.parseInt(doubleHit2.split("_")[2]);
				int zSet = (z)-zSpan/2;
				OvalRoi iOval = new OvalRoi(x-2, y-2, 5, 5);
				imp.setPositionWithoutUpdate(1, zSet, t);
				rm.addRoi(iOval);
				rm.getROIs().get(lm.getElementAt(lm.getSize()-1)).setPosition(1, zSet, t);
//				rm.getROIsByNumbers().get("1_"+zSet+"_"+t).add(rm.getROIs().get(lm.getElementAt(lm.getSize()-1)));
				
//				String name = rm.getROIs().get(lm.getElementAt(lm.getSize()-1)).getName();
//				String newName = rm.getUniqueName(name.replaceAll("(.*)_(\\d*)_(\\d*)_(\\d*)(.*)", "$1_"+1+"_"+z+"_"+t));
//				if (!newName.equals(name)) {
//					rm.getROIs().put(newName, (Roi) rm.getROIs().get(lm.getElementAt(lm.getSize()-1)));
//					lm.setElementAt(newName, lm.getSize()-1);
//					rm.getROIs().remove(name);
//					rm.getROIs().get(newName).setName(newName);
//				}

			}

		}
		imp.getWindow().setVisible(true);
	}
}
