package org.vcell.gloworm;

import java.awt.Color;
import java.awt.Point;
import java.awt.Polygon;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
import ij.io.RoiEncoder;
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
	  try {
		  
//        Map<String, String> env = new HashMap<String, String>(); 
//        env.put("create", "true");
//        URI uri = URI.create("jar:file:"+IJ.getDirectory("home")
//				+File.separator+"CytoSHOWCacheFiles"
//				+File.separator+"TrackingOutputs"
//				+File.separator+imp.getTitle()+"trkROIs.zip");
//		FileSystem zipfs = FileSystems.newFileSystem(uri, env);
		
		for(int t=0;t<=imp.getNFrames();t++) {
//		for(int t=1;t<=1000;t++) {
			for (int z=1;z<=imp.getNSlices();z++) {
				ArrayList<Roi> ztRoiAL = rm.getROIsByNumbers().get("1_"+z+"_"+t);
				if(ztRoiAL!=null) {

					ArrayList<Roi> zt1uRoiAL = rm.getROIsByNumbers().get("1_"+z+"_"+(t+1));
					ArrayList<Roi> z1ut1uRoiAL = rm.getROIsByNumbers().get("1_"+(z+1)+"_"+(t+1));
					ArrayList<Roi> z1dt1uRoiAL = rm.getROIsByNumbers().get("1_"+(z-1)+"_"+(t+1));
					ArrayList<Roi> z2ut1uRoiAL = rm.getROIsByNumbers().get("1_"+(z+2)+"_"+(t+1));
					ArrayList<Roi> z2dt1uRoiAL = rm.getROIsByNumbers().get("1_"+(z-2)+"_"+(t+1));
					ArrayList<Roi> zt2uRoiAL = rm.getROIsByNumbers().get("1_"+z+"_"+(t+1));
					ArrayList<Roi> z1ut2uRoiAL = rm.getROIsByNumbers().get("1_"+(z+1)+"_"+(t+2));
					ArrayList<Roi> z1dt2uRoiAL = rm.getROIsByNumbers().get("1_"+(z-1)+"_"+(t+2));
					ArrayList<Roi> z2ut2uRoiAL = rm.getROIsByNumbers().get("1_"+(z+2)+"_"+(t+2));
					ArrayList<Roi> z2dt2uRoiAL = rm.getROIsByNumbers().get("1_"+(z-2)+"_"+(t+2));
					ArrayList<Roi> zAlltFwdRoiAL = new ArrayList<Roi>();
					//This stacking order has a rational basis: closest taken first.
					if (zt1uRoiAL!=null)
						zAlltFwdRoiAL.addAll(zt1uRoiAL);
					if (zt2uRoiAL!=null)
						zAlltFwdRoiAL.addAll(zt2uRoiAL);
					if (z1dt1uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z1dt1uRoiAL);
					if (z1ut1uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z1ut1uRoiAL);
					if (z1dt2uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z1dt2uRoiAL);
					if (z1ut2uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z1ut2uRoiAL);
					if (z2dt1uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z2dt1uRoiAL);
					if (z2ut1uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z2ut1uRoiAL);
					if (z2dt2uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z2dt2uRoiAL);
					if (z2ut2uRoiAL!=null)
						zAlltFwdRoiAL.addAll(z2ut2uRoiAL);
					
					for (Object r:ztRoiAL.toArray()) {
						Roi roi = (Roi)r;
						for (Roi roiTest:zAlltFwdRoiAL) {
							if ((new ShapeRoi(new OvalRoi(roi.getBounds().getCenterX()-4,roi.getBounds().getCenterY()-4,9,9))).contains((int)roiTest.getBounds().getCenterX(), (int)roiTest.getBounds().getCenterY())) {
								if (roi.getFillColor() ==null || roi.getFillColor().getRGB()==0) {
									double rand = Math.random();
									String randFix = (""+rand).replaceAll("E-\\d*", "");
									String randString = randFix.substring(randFix.length()-8);
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
									String cString = "#"+randString.replaceAll("(..)(......)","88$2");
									IJ.log(t+" "+cString);
									roi.setFillColor(Colors.decode(cString,Color.white));
								} else {
									IJ.log(""+t);
								}
								
								String roiRootName = roi.getName().split("_")[0];
								if (!(roiTest.getFillColor() == roi.getFillColor() && roiTest.getName().startsWith(roiRootName))) {
									roiTest.setFillColor(roi.getFillColor());
									for(int i=0;i<lm.size();i++) {
										String s = lm.elementAt(i);
										if (s.equals(roiTest.getName())) {
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
								break; //for roiTest
							}
						}
						if(!roi.getName().startsWith("\"")) {
							ztRoiAL.remove(roi);
						}
					}
					
					for (Roi roi:ztRoiAL) {
						String label = roi.getName();
						if (!label.endsWith(".roi")) label += ".roi";
//						Path roifile = Paths.get(IJ.getDirectory("home")
//								+File.separator+"CytoSHOWCacheFiles"
//								+File.separator+"TrackingOutputs"
//								+File.separator+"TempsForZip"
//								+File.separator+label);
//					    Path pathInZipfile = zipfs.getPath("/"+label);          
					    (new File(IJ.getDirectory("home")
								+File.separator+"CytoSHOWCacheFiles"
								+File.separator+"TrackingOutputs"
								+File.separator+"TempsForZip")).mkdirs();
					    RoiEncoder re = new RoiEncoder(IJ.getDirectory("home")
								+File.separator+"CytoSHOWCacheFiles"
								+File.separator+"TrackingOutputs"
								+File.separator+"TempsForZip"
								+File.separator+label);
					       
						re.write(roi);
//						Files.copy(roifile,pathInZipfile, StandardCopyOption.REPLACE_EXISTING );

					}
				}
				
			}
		}
//		zipfs.close();
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
	  }catch (IOException e) {
		  System.out.print(e);
	  }
	}
}
