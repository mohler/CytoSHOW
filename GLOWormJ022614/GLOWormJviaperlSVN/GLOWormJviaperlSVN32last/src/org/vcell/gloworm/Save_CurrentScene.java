package org.vcell.gloworm;

import java.awt.event.ActionEvent;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;


public class Save_CurrentScene implements PlugIn {

	public void run(String arg) {
		// TODO Auto-generated method stub
		 //MovieAlignFrame_MOD mafMOD = new MovieAlignFrame_MOD();
		ImagePlus imp = IJ.getImage();
		MultiChannelController mcc = null;
		if (imp!=null)
			 mcc = imp.getMultiChannelController();
		if (mcc!=null) {
			mcc.setSharing(false);
			mcc.actionPerformed(new ActionEvent(mcc, 0, "Save Scene") );
		}
		else if (mcc == null) {
			imp.setMultiChannelController(new MultiChannelController(imp));
			mcc = imp.getMultiChannelController();
			if (mcc!=null && imp.getImageStack() instanceof MultiQTVirtualStack) {
				mcc.setSharing(false);
				mcc.actionPerformed(new ActionEvent(mcc, 0, "Save Scene") );
			}
		}
	
	}
}
