package ij.plugin;

import javax.swing.ImageIcon;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;

public class SqRtScaleToggler implements PlugIn{

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		imp.getStack().setSqRtScale(! imp.getStack().isSqRtScale()) ;
		if (imp.getStack().isSqRtScale()) {
			imp.getWindow().sqrtButton.setToolTipText("Turn Off Square Root Contrast Effect");
			imp.getWindow().sqrtButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/SqrtScaleActive.png")));
		} else {
			imp.getWindow().sqrtButton.setToolTipText("Square Root Display Scaling");
			imp.getWindow().sqrtButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/SqrtScale.png")));

		}
		imp.setPosition(imp.getChannel(), imp.getSlice()+1, imp.getFrame());
		imp.setPosition(imp.getChannel(), imp.getSlice()-1, imp.getFrame());
	}

}
