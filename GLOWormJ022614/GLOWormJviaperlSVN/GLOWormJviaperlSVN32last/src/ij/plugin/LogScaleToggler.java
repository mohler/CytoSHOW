package ij.plugin;

import ij.IJ;
import ij.ImagePlus;

public class LogScaleToggler implements PlugIn{

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		imp.getStack().setLogScale(! imp.getStack().isLogScale()) ;
		imp.setPosition(imp.getChannel(), imp.getSlice()+1, imp.getFrame());
		imp.setPosition(imp.getChannel(), imp.getSlice()-1, imp.getFrame());
	}

}
