package org.vcell.gloworm;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.PlugIn;

public class StarryNiteFeeder implements PlugIn {

	public void run(String arg) {
		String outDir = IJ.getDirectory("Output directory for input into StarryNite?");
		//print(outDir);

		for (int w=1; w<=WindowManager.getImageCount(); w++){
			ImagePlus imp = WindowManager.getImage(w);	
			
			while (imp.getRoi() == null) {
				w++;
				if (w>WindowManager.getImageCount()){
					return;
				}
				imp = WindowManager.getImage(w);
			}
			Roi theROI = imp.getRoi();
			int type = imp.getRoi().getType() ;
			
			String title = imp.getTitle();
			String savetitle = title.replace(":","_");
			IJ.save(imp, outDir+savetitle+".roi");
			int[] xpoints = imp.getRoi().getPolygon().xpoints;
			int[] ypoints = imp.getRoi().getPolygon().ypoints;
			
			double angle =0;
			if (type > 0) {
				angle = imp.getRoi().getFeretValues()[1];
			} else {
				angle = imp.getRoi().getBounds().getHeight()>imp.getRoi().getBounds().getWidth()?90:0;
			}
			
			if (ypoints[0]>ypoints[1]){
				angle = 180+ angle;
			}

			int wasC = imp.getChannel();
			int wasZ = imp.getSlice();
			int wasT = imp.getFrame();
			
			for (int f = 1; f <= imp.getNFrames(); f++) {

				imp.setRoi(theROI);
				ImagePlus frameRedImp = (new MQTVS_Duplicator()).run(imp, imp.getNChannels(), imp.getNChannels(), 1, imp.getNSlices(), f, f, 1, false, 0);
				imp.setRoi(theROI);
				ImagePlus frameGreenImp = (new MQTVS_Duplicator()).run(imp, 1, 1, 1, imp.getNSlices(), f, f, 1, false, 0);

				// Red channel:
				IJ.run(frameRedImp,"Rotate... ", "angle="+angle+" grid=0 interpolation=Bicubic enlarge");
				
				String subdir = savetitle;
				new File(outDir+subdir).mkdirs();
				
				// Do we need to save a stack?
				IJ.save(frameRedImp, outDir+subdir+"/aaa"+f+".tif");

				
				// Green channel:
				IJ.run(frameGreenImp,"Rotate... ", "angle="+angle+" grid=0 interpolation=Bicubic enlarge");
				
				frameGreenImp.getProcessor().setMinAndMax(0, 5000);
				
				IJ.run(frameGreenImp,"8-bit","");
				
				new File(outDir+subdir+"/image/tifr/").mkdirs();

				
				String command1 = "format=TIFF start=1 name=aaa-t";
				command1 += ""+IJ.pad(f,3)+" digits=0 ";
				command1 += "save=";
				
				String command2 = "["+outDir+subdir+"/image/tifr]";
				//print(command1+command2);
				IJ.run(frameGreenImp, "StarryNite Image Sequence... ", command1+command2);


				frameRedImp.flush();
				frameGreenImp.flush();

			}
			imp.setPosition(wasC, wasZ, wasT);
			imp.setRoi(theROI);
		}	
	}

		
	




}
