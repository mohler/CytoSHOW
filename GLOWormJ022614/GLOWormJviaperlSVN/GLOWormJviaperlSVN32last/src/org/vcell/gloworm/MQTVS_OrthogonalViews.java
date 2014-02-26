package org.vcell.gloworm;
import java.awt.image.ColorModel;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.Orthogonal_Views;
import ij.plugin.PlugIn;


public class MQTVS_OrthogonalViews  implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp != null) {
			if (imp.getStack() instanceof MultiQTVirtualStack) {
				String firstMovieName = ((MultiQTVirtualStack)imp.getStack()).getVirtualStack(0).getMovieName();
				if ( firstMovieName.toLowerCase().contains("gp") || firstMovieName.toLowerCase().contains("yp") 
						|| firstMovieName.toLowerCase().contains("prx") || firstMovieName.toLowerCase().contains("pry") ) {
					if ( !IJ.showMessageWithCancel("Use Stereo4D data for Orthogonal Slices?", 
							"Are you certain you want to run Orthogonal Slices " +
							"\nto dissect a region from a Stereo4D movie? " +
							"\n(it's actually not a volume of slices when it's in Stereo4D format...)" +
							"\n " +
							"\nUsually, one wants to use Slice4D data for input to Orthogonal Slices.") ) {
						return;
					}
				}
			}
		}

		IJ.run("Orthogonal Views");
		

	}
}
