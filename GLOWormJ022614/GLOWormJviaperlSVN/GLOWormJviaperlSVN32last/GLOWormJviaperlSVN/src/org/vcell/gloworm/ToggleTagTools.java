package org.vcell.gloworm;

import ij.IJ;
import ij.plugin.PlugIn;

public class ToggleTagTools implements PlugIn {

	public void run(String arg) {
		IJ.getImage().getWindow().toggleTagTools();
	}

}
