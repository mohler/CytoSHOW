package org.vcell.gloworm;
import ij.IJ;
import ij.Macro;
import ij.plugin.PlugIn;


public class Open_CytoSHOWMovies implements PlugIn {

	public void run(String arg) {
		// TODO Auto-generated method stub
		String mac = IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/MovieChoiceMacroQTOMM.txt");
		IJ.runMacro(mac);		

	}

}
