package ij.plugin;

import javax.swing.ImageIcon;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;

public class LogScaleToggler implements PlugIn{

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		imp.getStack().setLogScale(! imp.getStack().isLogScale()) ;
		if (imp.getStack().isLogScale()) {
			imp.getWindow().logButton.setToolTipText("Turn Off Log Contrast Effect");
			imp.getWindow().logButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/LogScaleActive.png")));
		} else {
			imp.getWindow().logButton.setToolTipText("Logarithmic Display Scaling");
			imp.getWindow().logButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/LogScale.png")));

		}

		imp.setPosition(imp.getChannel(), imp.getSlice()+1, imp.getFrame());
		imp.setPosition(imp.getChannel(), imp.getSlice()-1, imp.getFrame());
	}

}
