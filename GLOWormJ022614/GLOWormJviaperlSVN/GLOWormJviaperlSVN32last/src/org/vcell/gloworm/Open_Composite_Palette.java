package org.vcell.gloworm;
//import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
//import ij.gui.ImageWindow;
//import ij.gui.StackWindow;
import ij.plugin.*;
//import ij.plugin.frame.ContrastAdjuster;
//import ij.plugin.frame.PlugInFrame;
import SmartCaptureLite.Composite_Adjuster;

public class Open_Composite_Palette  implements PlugIn {
	
	Composite_Adjuster cp;
	

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
//		int origChannel = imp.getChannel();
		IJ.run("Brightness/Contrast...", "");
//		if (imp.getNChannels() <2) {
//			return;
//		}
		//IJ.log("Composite_Adjuster" );
//		int wc = WindowManager.getImageCount();
//		for (int i =1; i < wc+1; i++){
//			ImageWindow iw = WindowManager.getImage(i).getWindow();
//				WindowManager.setTempCurrentImage(iw.getImagePlus());
//				IJ.run("Stop Animation");
//				WindowManager.setTempCurrentImage(null);
//		}
//		cp = new Composite_Adjuster();
//		cp.run(null);
//
//		while (cp!=null);
//			
//		
//		if ( ((CompositeImage) imp).getMode() ==1 ) {
//			for (int j = 1; j <= imp.getNChannels(); j++) {
//				imp.setPosition(j, imp.getSlice(),
//						imp.getFrame());
//			}
//			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame());
//			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() + 1);
//			imp.setPosition(origChannel, imp.getSlice(), imp.getFrame() - 1);
//		}
		return;
	}
	
//    public void close() {
//        cp.close();
//
//    }

}


