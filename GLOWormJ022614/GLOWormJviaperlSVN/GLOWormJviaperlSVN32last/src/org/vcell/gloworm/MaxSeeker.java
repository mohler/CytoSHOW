package org.vcell.gloworm;

import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;

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
		ImageStack newStack = new ImageStack(imp.getWidth(), imp.getHeight());
		RoiManager rm = imp.getRoiManager();
		DefaultListModel<String> lm = rm.getListModel();

		for(int t=1;t<=imp.getNFrames();t++) {
			for (int z=1;z<=imp.getNSlices();z++) {
//				FFT fft = new FFT();
				imp.setPositionWithoutUpdate(1, z, t);
//				FHT fht = fft.newFHT(imp.getProcessor());
//
//				OvalRoi innerCircle = new OvalRoi(52, 52, 23, 23);
//				OvalRoi outerCircle = new OvalRoi(38, 38, 52, 52);
//				ImagePlus impFFT = fft.doForwardTransform(fht);
//				//			impFFT.show();
//
//				impFFT.setRoi(innerCircle);
//				FFT invFFT = new FFT();
//
//				ImageProcessor ipFFT = impFFT.getProcessor();
//				ipFFT.fill(innerCircle);
//				ipFFT.fillOutside(outerCircle);
//
//				Object obj = impFFT.getProperty("FHT");
//				FHT invFHT = (obj instanceof FHT)?(FHT)obj:null;
//
//				ImagePlus impClean = invFFT.doInverseTransform(invFHT);
				IJ.run(imp, "Find Maxima...", "noise=66 output=[Point Selection] exclude");
				//			impClean.getRoi().setPosition(1, 1, p);
				if (imp.getRoi()!=null) {
					rm.addRoi(imp.getRoi());
					rm.getROIs().get(lm.getElementAt(lm.getSize()-1)).setPosition(1, z, t);
					if(rm.getROIsByNumbers().get("1_"+z+"_"+t) == null)
						rm.getROIsByNumbers().put("1_"+z+"_"+t, new ArrayList<Roi>());
					rm.getROIsByNumbers().get("1_"+z+"_"+t).add(rm.getROIs().get(lm.getElementAt(lm.getSize()-1)));
				}
				
//				String name = rm.getROIs().get(lm.getElementAt(lm.getSize()-1)).getName();
//				String newName = rm.getUniqueName(name.replaceAll("(.*)_(\\d*)_(\\d*)_(\\d*)(.*)", "$1_"+1+"_"+z+"_"+t));
//				if (!newName.equals(name)) {
//					rm.getROIs().put(newName, (Roi) rm.getROIs().get(lm.getElementAt(lm.getSize()-1)));
//					lm.setElementAt(newName, lm.getSize()-1);
//					rm.getROIs().remove(name);
//					rm.getROIs().get(newName).setName(newName);
//				}

//				impClean.flush();
			}
//			for (int z=1;z<=imp.getNSlices()-3;z++) {
//				ArrayList<Roi> sliceH = rm.getROIsByNumbers().get("1_"+z+"_"+t);
//				ArrayList<Roi> sliceI = rm.getROIsByNumbers().get("1_"+(z+1)+"_"+t);
////				ArrayList<Roi> sliceJ = rm.getROIsByNumbers().get("1_"+(z+2)+"_"+t);
////				ArrayList<Roi> sliceK = rm.getROIsByNumbers().get("1_"+(z+2)+"_"+t);
////				ArrayList<Point> doubleStacked = new ArrayList<Point>();
//				ArrayList<Point> tripleStacked = new ArrayList<Point>();
//				ArrayList<Point> alreadyConfirmed = new ArrayList<Point>();
//				if (sliceI!=null && sliceH!=null) {
//					Roi iROI = sliceI.get(0);
//					Polygon iROIpoly = iROI.getPolygon();
//					Roi hROI = sliceH.get(0);
//					Polygon hROIpoly = hROI.getPolygon();
//					for (int pi=0;pi<iROIpoly.npoints;pi++) {
//						OvalRoi iOval = new OvalRoi(iROIpoly.xpoints[pi]-2, iROIpoly.ypoints[pi]-2,4,4);
//						for (int ph=0;ph<hROIpoly.npoints;ph++) {
//							if ((new ShapeRoi(iOval)).contains(hROIpoly.xpoints[ph], hROIpoly.ypoints[ph])) {
//								tripleStacked.add(new Point(hROIpoly.xpoints[ph], hROIpoly.ypoints[ph]));
//							}
//						}
//					}
//				}
//			
////				for (Point doubleHit2:doubleStacked) {
////					if (!alreadyConfirmed.contains(doubleHit2)) {
////						OvalRoi iOval = new OvalRoi(doubleHit2.x-1, doubleHit2.y-1,3,3);
////						if (sliceJ!=null) {
////							Roi jROI=sliceJ.get(0) ;
////							Polygon jROIpoly = jROI.getPolygon();
////							for (int pj=0;pj<jROIpoly.npoints;pj++) {
////								if ((new ShapeRoi(iOval)).contains(jROIpoly.xpoints[pj], jROIpoly.ypoints[pj])) {
////									tripleStacked.add(new Point(jROIpoly.xpoints[pj], jROIpoly.ypoints[pj]));
////								}
////							}
////						}
////					}
////				}
//				
//				for (Point tripleHit3:tripleStacked) {
////					if (!alreadyConfirmed.contains(tripleHit3)) {
//						OvalRoi iOval = new OvalRoi(tripleHit3.x-2, tripleHit3.y-2,4,4);
////						if (sliceK!=null) {
////							Roi kROI=sliceK.get(0) ;
////							Polygon kROIpoly = kROI.getPolygon();
////							for (int pk=0;pk<kROIpoly.npoints;pk++) {
////								if (new ShapeRoi(iOval).contains(kROIpoly.xpoints[pk], kROIpoly.ypoints[pk])) {
//									imp.setPositionWithoutUpdate(1, z+1, t);
//									rm.addRoi(iOval);
//									rm.getROIs().get(lm.getElementAt(lm.getSize()-1)).setPosition(1, z+1, t);
//									rm.getROIsByNumbers().get("1_"+z+"_"+t).add(rm.getROIs().get(lm.getElementAt(lm.getSize()-1)));
//									alreadyConfirmed.add(tripleHit3);
////								}
////							}
////						}
////					}
//				}
//
//			}
		}
	}
}
