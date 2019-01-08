package org.vcell.gloworm;

import ij.IJ;
import ij.plugin.PlugIn;

public class testparamsedits implements PlugIn {

	@Override
	public void run(String arg) {
		Double intensityCue = 2298.0;
		IJ.log(IJ.openAsString(null)
				.replaceAll("(.*ROI=)true(;.*)", "$1false$2")
				.replaceAll("(.*ROI.min=)\\d+(;.*)", "$10$2")
				.replaceAll("(.*)ROIpoints=\\[\\d+.*\\];(.*)", "$1"+""+"$2")
				.replaceAll("(.*parameters.intensitythreshold=\\[.*\\]\\.\\/)(\\d+)(\\ .*;.*)", "$1\\($2\\*"+3000/intensityCue+"\\)$3")
				);

	}

}
