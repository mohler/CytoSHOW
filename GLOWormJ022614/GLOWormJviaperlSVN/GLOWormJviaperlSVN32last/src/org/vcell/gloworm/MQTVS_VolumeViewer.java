package org.vcell.gloworm;
import java.awt.Frame;
import java.awt.image.ColorModel;
import java.io.File;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij3d.ImageJ3DViewer;


public class MQTVS_VolumeViewer  implements PlugIn {

	public void run(String arg) {
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
		
		ImagePlus impD = imp;
		if ((imp.getStack().isVirtual() && imp.getNFrames() > 1) || imp.getRoi() != null) {
//			double inMin = imp.getDisplayRangeMin();
//			double inMax = imp.getDisplayRangeMax();
//			ColorModel cm = imp.getProcessor().getColorModel();
//			int inChannel = imp.getChannel();
//			int inSlice = imp.getSlice();
//			int inFrame = imp.getFrame();
			
			imp.getWindow().setVisible(false);
			Frame rm =WindowManager.getFrame("Tag Manager");
			boolean rmWasVis = false;
			if (rm != null) {
				rmWasVis = rm.isVisible();
				rm.setVisible(false);
			}
			Frame mcc = imp.getMultiChannelController();
			boolean mccWasVis = false;
			if (mcc != null) {
				mccWasVis = mcc.isVisible();
				mcc.setVisible(false);
			}
			
			MQTVS_Duplicator duper = new MQTVS_Duplicator();
			impD = duper.duplicateHyperstack(imp, imp.getTitle()+"_DUP");

			imp.getWindow().setVisible(true);
			if (rm != null) rm.setVisible(rmWasVis);
			if (mcc != null) mcc.setVisible(mccWasVis);
			
//			impD.setCalibration(imp.getCalibration());
//			impD.getProcessor().setColorModel(cm);
//			impD.setDisplayRange(inMin, inMax);
//			impD.setPosition(inChannel, inSlice, 1);

//			imp.getProcessor().setColorModel(cm);
//			imp.setDisplayRange(inMin, inMax);
//			imp.setPosition(inChannel, inSlice, inFrame);
//			int origChannel = imp.getChannel();
//			if (imp instanceof CompositeImage && ((CompositeImage) imp).getMode() ==1 ) {
//				for (int j = 1; j <= imp.getNChannels(); j++) {
//					imp.setPosition(j, imp.getSlice(),
//							imp.getFrame());
//				}
//				imp.setPosition(origChannel, imp.getSlice(), imp.getFrame());
//				imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() + 1);
//				imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() - 1);
//			}

//			impD.show();
		}

//		IJ.runPlugIn(impD, "Volume_Viewer", "");
//		IJ.runPlugIn("ImageJ_3D_Viewer", "");
		ImageJ3DViewer ij3dv = new ImageJ3DViewer();
		ij3dv.run(".");
		ImageJ3DViewer.add(impD.getTitle(), "White", impD.getTitle()+"_IJ3DV_"+imp.getChannel(), "75", "true", "true", "true", "2", "2");
		ImageJ3DViewer.select(impD.getTitle()+"_IJ3DV_"+imp.getChannel());
		ImageJ3DViewer.exportContent("wavefront", IJ.getDirectory("home")+File.separator+impD.getTitle()+"_IJ3DV_"+imp.getChannel()+".obj");
		
		if (imp.getNFrames() > 1 || imp.getRoi() != null) {
			impD.close();
			impD.flush();
		}
	}
}
