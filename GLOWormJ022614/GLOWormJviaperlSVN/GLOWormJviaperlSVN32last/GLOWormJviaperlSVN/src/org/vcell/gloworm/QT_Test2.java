package org.vcell.gloworm;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import quicktime.QTException;
import quicktime.QTSession;
import quicktime.util.QTBuild;

public class QT_Test2 {

	public static void run(String arg) {

		if (!IJ.is64Bit() ){
			IJ.log("Testing QuickTime installation:");
			boolean success = false;
			boolean hasQT = false;
			try {
				Class.forName("quicktime.QTSession");
				hasQT = true;
			}
			catch (ClassNotFoundException cnfe) {
				IJ.log("QuickTime not found.");
				GenericDialog gd = new GenericDialog("QuickTime not found");
				gd.addHelp("http://www.apple.com/quicktime/download/");
				gd.addMessage("Your computer does not have QuickTime for Java installed.  \nSome features of CytoSHOW can benefit from QuickTime.  \nIf you wish to fix this:\n\n" +
						"1. Please click 'Install QuickTime' below to begin QuickTime installation. \n" +
						"\t\t If you already believe you have QuickTime installed, please click \n" +
						"\t\t this link anyway, and Unistall and Reinstall QuickTime before \n" +
						"\t\t trying to run CytoSHOW.\n\n" +
						"2. Quit CytoSHOW and relaunch to view and analyze QuickTime movies.");
				gd.centerDialog(true);
				gd.setCancelLabel("Continue without QuickTime");
				gd.setOKLabel("Quit CytoSHOW");
				gd.setHelpLabel("Install QuickTime");
				gd.showDialog();
				if (gd.wasOKed()) IJ.run("Quit");
				return;
			}catch(NoClassDefFoundError e) {			
				IJ.log("QuickTime not found.");
				GenericDialog gd = new GenericDialog("QuickTime not found");
				gd.addHelp("http://www.apple.com/quicktime/download/");
				gd.addMessage("Your computer does not have QuickTime for Java installed.  \nSome features of CytoSHOW can benefit from QuickTime.  \nIf you wish to fix this:\n\n" +
						"1. Please click 'Install QuickTime' below to begin QuickTime installation. \n" +
						"\t\t If you already believe you have QuickTime installed, please click \n" +
						"\t\t this link anyway, and Unistall and Reinstall QuickTime before \n" +
						"\t\t trying to run CytoSHOW.\n\n" +
						"2. Quit CytoSHOW and relaunch to view and analyze QuickTime movies.");
				gd.centerDialog(true);
				gd.setCancelLabel("Continue without QuickTime");
				gd.setOKLabel("Quit CytoSHOW");
				gd.setHelpLabel("Install QuickTime");
				gd.showDialog();
				if (gd.wasOKed()) IJ.run("Quit");
				return;

			}
			if (!hasQT ) {
				IJ.log("QuickTime not found.");
				GenericDialog gd = new GenericDialog("QuickTime not found");
				gd.addHelp("http://www.apple.com/quicktime/download/");
				gd.addMessage("Your computer does not have QuickTime for Java installed.  \nSome features of CytoSHOW can benefit from QuickTime.  \nIf you wish to fix this:\n\n" +
						"1. Please click 'Install QuickTime' below to begin QuickTime installation. \n" +
						"\t\t If you already believe you have QuickTime installed, please click \n" +
						"\t\t this link anyway, and Unistall and Reinstall QuickTime before \n" +
						"\t\t trying to run CytoSHOW.\n\n" +
						"2. Quit CytoSHOW and relaunch to view and analyze QuickTime movies.");
				gd.centerDialog(true);
				gd.setCancelLabel("Continue without QuickTime");
				gd.setOKLabel("Quit CytoSHOW");
				gd.setHelpLabel("Install QuickTime");
				gd.showDialog();
				if (gd.wasOKed()) IJ.run("Quit");
				return;
			}

			IJ.log("QuickTime for Java is installed.");
		}
	}
}
