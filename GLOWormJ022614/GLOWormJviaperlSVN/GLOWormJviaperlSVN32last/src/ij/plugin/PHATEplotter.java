package ij.plugin;

import ij.plugin.frame.RoiManager;

public class PHATEplotter implements PlugIn {

	public PHATEplotter() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void run(String arg) {
		RoiManager.plotAlexFmtPhateObjsToCoordsIcospheres();
	}

}
