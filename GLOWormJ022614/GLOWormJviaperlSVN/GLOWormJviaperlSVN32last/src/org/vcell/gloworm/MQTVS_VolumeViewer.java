package org.vcell.gloworm;
import java.awt.Color;
import java.awt.Frame;
import java.awt.image.ColorModel;
import java.io.File;

import com.apple.laf.AquaButtonBorder.Toolbar;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.Scaler;
import ij.plugin.filter.Projector;
import ij.plugin.frame.RoiManager;
import ij3d.ColorTable;
import ij3d.ImageJ3DViewer;


public class MQTVS_VolumeViewer  implements PlugIn {

	public static ImageJ3DViewer ij3dv;

	public void run(String arg) {
		boolean singleSave = !IJ.shiftKeyDown();
		String cellName = arg;
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp != null) {
			if (imp.getStack() instanceof MultiQTVirtualStack) {
				String firstMovieName = ((MultiQTVirtualStack)imp.getStack()).getVirtualStack(0).getMovieName();
				if ( firstMovieName.toLowerCase().contains("gp") || firstMovieName.toLowerCase().contains("yp") 
						|| firstMovieName.toLowerCase().contains("prx") || firstMovieName.toLowerCase().contains("pry") ) {
					if ( !IJ.showMessageWithCancel("Use Stereo4D data for Volume Viewer?", 
							"Are you certain you want to run Volume Viewer " +
							"\nto dissect a region from a Stereo4D movie? " +
							"\n(it's actually not a volume of slices when it's in Stereo4D format...)" +
							"\n " +
							"\nUsually, one wants to use Slice4D data for input to Volume Viewer.") ) {
						return;
					}
				}
			}
		} else {
//			IJ.runPlugIn("ImageJ_3D_Viewer", "");
			ImageJ3DViewer ij3dv = new ImageJ3DViewer();
			ij3dv.run(".");
			
			
			return;
		}
		
		MQTVS_Duplicator duper = new MQTVS_Duplicator();
		if (ij3dv==null) {
			ij3dv = new ImageJ3DViewer();
		}
		Roi impRoi = imp.getRoi();
		if (true /*(imp.getStack().isVirtual() && imp.getNFrames() > 1) || imp.getRoi() != null*/) {
			
			imp.getWindow().setVisible(false);
			RoiManager rm = imp.getRoiManager();
			boolean rmWasVis = false;
			if (rm != null) {
				rmWasVis = rm.isVisible();
				rm.setVisible(false);
			}
			MultiChannelController mcc = imp.getMultiChannelController();
			boolean mccWasVis = false;
			if (mcc != null) {
				mccWasVis = mcc.isVisible();
				mcc.setVisible(false);
			}
			
			String duperString = ""; 
			if (!imp.getTitle().startsWith("SketchVolumeViewer"))
				duperString = duper.showHSDialog(imp, imp.getTitle()+"_DUP");

			for (int tpt = (singleSave?duper.getFirstT():0); tpt<=(singleSave?duper.getLastT():0); tpt = tpt+(singleSave?duper.getStepT():1)) {
				for (int ch=duper.getFirstC(); ch<=duper.getLastC(); ch++) {
					imp.setRoi(impRoi);
					ImagePlus impD = imp;
					if (!imp.getTitle().startsWith("SketchVolumeViewer"))
						impD = duper.run(imp, ch, ch, duper.getFirstZ(), duper.getLastZ(), singleSave?tpt:duper.getFirstT(), singleSave?tpt:duper.getLastT(), singleSave?1:duper.getStepT(), false);
					impD.show();
					impD.setTitle(imp.getShortTitle()+"_DUP_"+ch+"_"+tpt);
					impD.changes = false;
					Color channelColor = imp instanceof CompositeImage?((CompositeImage)imp).getChannelColor(ch-1):Color.white;
					if (channelColor == Color.black)
						channelColor = Color.white;
					if (cellName != "" && imp.getMotherImp().getRoiManager().getColorLegend() != null)
						channelColor = imp.getMotherImp().getRoiManager().getColorLegend().getBrainbowColors().get(cellName.split(" =")[0].split(" \\|")[0].toLowerCase());
					if (channelColor == null)
						channelColor = Color.white;
					int binFactor = 2;
					double scaleFactor  = 1.0;
					int threshold = 90;
					if (imp.getTitle().startsWith("SketchVolumeViewer")) {
						binFactor = 1;
						scaleFactor  = 0.1;
						if (!imp.getMotherImp().getTitle().contains("SW_"))
							scaleFactor  = 1;
						threshold = 1;
					}
					IJ.run(impD, "Scale...", "x="+(scaleFactor)+" y="+(scaleFactor)+" z=1.0 interpolation=Bicubic average process create" );
					ImagePlus impDS = IJ.getImage();
					impD = impDS;
					IJ.run(impD, "8-bit", "");
					String objectName = cellName;
					if (objectName =="")
						objectName = impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_");
					try {
						ImageJ3DViewer.add(impD.getTitle(), ColorTable.colorNames[ch+2], ""+objectName+"_"+ch+"_"+tpt, ""+threshold, "true", "true", "true", ""+binFactor, "2");
					} catch (NullPointerException npe) {
						ij3dv.run(".");
						ImageJ3DViewer.add(impD.getTitle(), ColorTable.colorNames[ch+2], ""+objectName+"_"+ch+"_"+tpt, ""+threshold, "true", "true", "true", ""+binFactor, "2");
					}
					ImageJ3DViewer.select(""+objectName+"_"+ch+"_"+tpt);
					ImageJ3DViewer.setColor(""+channelColor.getRed(), ""+channelColor.getGreen(), ""+channelColor.getBlue());
					ImageJ3DViewer.lock();
					ImageJ3DViewer.exportContent("wavefront", (IJ.getDirectory("home")+File.separator+impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_")+"_"+objectName.replaceAll(":","").replaceAll("(/|\\s+)","")+"_"+ch+"_"+tpt+".obj"));
					if (singleSave) {
						ImageJ3DViewer.select(""+objectName+"_"+ch+"_"+tpt);
						ImageJ3DViewer.unlock();
						ImageJ3DViewer.delete();
					}

					ImageJ3DViewer.select(null);
					IJ.getInstance().toFront();
					IJ.setTool(ij.gui.Toolbar.HAND);
					impD.changes = false;
					impD.getWindow().close();
					impD.flush();
				}
			}
			
			if (!imp.getTitle().startsWith("SketchVolumeViewer")) {
				imp.getWindow().setVisible(true);
				imp.getWindow().setAlwaysOnTop(false);
			}
			ImageJ3DViewer.select(null);
			IJ.getInstance().toFront();
			IJ.setTool(ij.gui.Toolbar.HAND);
			if (rm != null) rm.setVisible(rmWasVis);
			if (mcc != null) mcc.setVisible(mccWasVis);
		}

	}
}
