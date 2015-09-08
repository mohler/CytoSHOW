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
import ij.plugin.frame.RoiManager;
import ij3d.ColorTable;
import ij3d.ImageJ3DViewer;


public class MQTVS_VolumeViewer  implements PlugIn {

	public void run(String arg) {
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
		ImageJ3DViewer ij3dv = new ImageJ3DViewer();
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
			ij3dv.run(".");

			for (int ch=duper.getFirstC(); ch<=duper.getLastC(); ch++) {
				imp.setRoi(impRoi);
				ImagePlus impD = imp;
				if (!imp.getTitle().startsWith("SketchVolumeViewer"))
					impD = duper.run(imp, ch, ch, duper.getFirstZ(), duper.getLastZ(), duper.getFirstT(), duper.getLastT(), duper.getStepT(), false);
				impD.show();
				impD.setTitle(imp.getTitle()+"_DUP_"+ch);
				Color channelColor = imp instanceof CompositeImage?((CompositeImage)imp).getChannelColor(ch-1):Color.white;
				if (imp.getMotherImp().getRoiManager().getColorLegend() != null)
					channelColor = imp.getMotherImp().getRoiManager().getColorLegend().getBrainbowColors().getOrDefault(cellName.split(" =")[0].toLowerCase(), channelColor);
				ImageJ3DViewer.add(impD.getTitle(), ColorTable.colorNames[ch+2], ""+cellName+"_IJ3DV_"+ch, "90", "true", "true", "true", "2", "2");
				ImageJ3DViewer.select(""+cellName+"_IJ3DV_"+ch);
				ImageJ3DViewer.setColor(""+channelColor.getRed(), ""+channelColor.getGreen(), ""+channelColor.getBlue());
				ImageJ3DViewer.exportContent("wavefront", IJ.getDirectory("home")+File.separator+impD.getTitle()+"_"+cellName+"_IJ3DV_"+ch+".obj");
				
				ImageJ3DViewer.select(null);
				IJ.getInstance().toFront();
				IJ.setTool(ij.gui.Toolbar.HAND);
			}
			
			imp.getWindow().setVisible(true);
			imp.getWindow().setAlwaysOnTop(false);
			ImageJ3DViewer.select(null);
			IJ.getInstance().toFront();
			IJ.setTool(ij.gui.Toolbar.HAND);
			if (rm != null) rm.setVisible(rmWasVis);
			if (mcc != null) mcc.setVisible(mccWasVis);
		}

	}
}
