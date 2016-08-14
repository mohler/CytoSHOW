package org.vcell.gloworm;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class FlattenerOfTags implements PlugIn {

	public void run(String arg) {
				ImagePlus imp = IJ.getImage();
				ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
				for(int z=1;z<=imp.getStackSize();z++) {
					imp.setSlice(z);
					IJ.wait(20);
					ImageProcessor ip = imp.flatten().getProcessor();
					stack.addSlice(ip);
				}
				ImagePlus imp2 = new ImagePlus(imp.getTitle()+"_flat", stack);
				imp.setDimensions(imp.getNChannels(), imp.getNSlices(),imp.getNFrames());
				imp2.show();
	}

}
