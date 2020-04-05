package ij.plugin;

import javax.swing.ImageIcon;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;

public class EdgeToggler implements PlugIn{

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		imp.getStack().setEdges(! imp.getStack().isEdges()) ;
		if (imp.getStack().isEdges()) {
			imp.getWindow().edgeButton.setToolTipText("Turn Off Edges Effect");
			imp.getWindow().edgeButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/EdgesActive.png")));
		} else {
			imp.getWindow().edgeButton.setToolTipText("Find Edges of Image Features");
			imp.getWindow().edgeButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Edges.png")));

		}

		imp.setPosition(imp.getChannel(), imp.getSlice()+1, imp.getFrame());
		imp.setPosition(imp.getChannel(), imp.getSlice()-1, imp.getFrame());
	}

}
