package ij.plugin;

import javax.swing.ImageIcon;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;

public class SharpBlurToggler implements PlugIn{

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		imp.getStack().setSharpBlur(! imp.getStack().isSharpBlur()) ;
		if (imp.getStack().isSharpBlur()) {
			imp.getWindow().sharpBlurButton.setToolTipText("Turn Off SharpBlur Effect");
			imp.getWindow().sharpBlurButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/SharpBlurActive.png")));
		} else {
			imp.getWindow().sharpBlurButton.setToolTipText("Apply SharpBlur Effect");
			imp.getWindow().sharpBlurButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/SharpBlur.png")));

		}

		imp.setPosition(imp.getChannel(), imp.getSlice()+1, imp.getFrame());
		imp.setPosition(imp.getChannel(), imp.getSlice()-1, imp.getFrame());
	}

}
