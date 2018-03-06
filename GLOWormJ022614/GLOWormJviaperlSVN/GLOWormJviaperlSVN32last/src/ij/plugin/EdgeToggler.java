package ij.plugin;

import ij.IJ;
import ij.ImagePlus;

public class EdgeToggler implements PlugIn{

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		imp.getStack().setEdges(! imp.getStack().isEdges()) ;
		imp.setPosition(imp.getChannel(), imp.getSlice()+1, imp.getFrame());
		imp.setPosition(imp.getChannel(), imp.getSlice()-1, imp.getFrame());
	}

}
