package org.vcell.gloworm;

import java.awt.event.ActionEvent;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.NewPlugin;
import ij.plugin.PlugIn;
import ij.plugin.frame.Editor;
import ij.text.TextWindow;


public class Share_Suite implements PlugIn {

	public void run(String arg) {
		(new NewPlugin()).run("suite");
	}
}
