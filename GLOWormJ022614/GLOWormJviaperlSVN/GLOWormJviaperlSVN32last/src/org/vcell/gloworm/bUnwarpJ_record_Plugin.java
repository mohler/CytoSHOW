package org.vcell.gloworm;

import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

import bunwarpj.*;
import ij.plugin.*;

public class bUnwarpJ_record_Plugin implements PlugIn {

    public void run(String arg) {
    	
       ImagePlus imp = IJ.getImage();
       ImagePlus targetStack = WindowManager.getImage("N2U_uncropped_bUnwarpJ_Apply2NDtfm.tif");
       String mode = "";
       while (!(mode.equals("Build") || mode.equals("Apply") || mode.equals("RemapRois"))){
    		   mode = IJ.getString("Do what? Build or Apply or RemapRois?", "Build");
       }
       if (mode.equals("Build")) {
    	   while(true){
    		   int lastNum = imp.getCurrentSlice();
    		   IJ.run(imp, "Next Slice [>]", "");
    		   int num = imp.getCurrentSlice();	        
    		   if (num <= lastNum)
    			   return;
    		   targetStack.setSlice(num);
    		   ImagePlus targetDupNum = new ImagePlus("target"+num,targetStack.getProcessor());
    		   targetDupNum.setCalibration(targetStack.getCalibration());
    		   targetDupNum.show();
    		   ImagePlus impDupNum = new ImagePlus("source"+num,imp.getProcessor());
    		   //	        impDupNum.setTitle(""+num);
    		   impDupNum.setCalibration(imp.getCalibration());
    		   impDupNum.show();
    		   //	        ImagePlus impRegNumLessOne = WindowManager.getImage(""+(num-1)+"r.tif");
    		   IJ.run(impDupNum, "bUnwarpJ", "source_image="+ impDupNum.getTitle()+" target_image="+ targetDupNum.getTitle()+" registration=Mono image_subsample_factor=0 initial_deformation=[Very Coarse] final_deformation=Fine divergence_weight=0 curl_weight=0 landmark_weight=0 image_weight=1 consistency_weight=10 stop_threshold=0.01 verbose save_transformations save_direct_transformation=/Users/wmohler/Documents/N2U_skelROIs/n2u_bUnwarpJ_directFitTo2ndTfm/"+num+"_direct_transf.txt");
    		   ImagePlus regStack = WindowManager.getImage("Registered Source Image");
    		   ImagePlus impRegNum = new ImagePlus(""+num+"r.tif",regStack.getProcessor());
    		   impRegNum.setCalibration(imp.getCalibration());
    		   impRegNum.show();
    		   IJ.saveAs(impRegNum, "Tiff", "/Users/wmohler/Documents/N2U_skelROIs/n2u_bUnwarpJ_directFitTo2ndTfm/"+num+"r.tif");
    		   impDupNum.close();
    		   //	        impRegNumLessOne.close();
    		   targetDupNum.close();
    		   regStack.close();
    		   impRegNum.close();
    	   }
       } else if (mode.equals("Apply")) {
    	   while(true){
    		   int lastNum = imp.getCurrentSlice();
    		   IJ.run(imp, "Next Slice [>]", "");
    		   int num = imp.getCurrentSlice();
    		   if (num <= lastNum)
    			   return;
    		   ImagePlus impDupNum = new ImagePlus("",imp.getProcessor());
    		   impDupNum.setTitle(""+num+".tif");
    		   impDupNum.setCalibration(imp.getCalibration());
    		   impDupNum.show();
    		   bUnwarpJ_.loadElasticTransform("/Users/wmohler/Documents/N2U_skelROIs/n2u_bUnwarpJ_2ndRound_testFromCytoShow/"+num+"_direct_transf.txt", "blank", ""+num+".tif");
    		   IJ.saveAs(impDupNum, "Tiff", "/Users/wmohler/Documents/N2U_skelROIs/N2U_uncropped_bUnwarpJ_Apply2NDtfm/"+num+"tfmApp.tif");
    		   impDupNum.close();
    	   }
       } else if (mode.equals("RemapRois")) {
    	   //THE ORDER OF TARGET AND SOURCE SEEMS TO WORK OPPOSITE OF WHAT I WOULD HAVE EXPECTED.  BUT THIS ALL WORKS.
    	   Transformation tmxn = bUnwarpJ_.computeTransformationBatch(imp, WindowManager.getImage("blank.tif"), null, null, new Param(2, 0, 0, 2, 0, 0, 0, 1, 10, 0.01));
    	   while(true){
    		   double[] xyF = new double[2];
    			
//    			String inputString = IJ.getString("x and y coords", "100,100");
    		   Point cLoc = imp.getCanvas().getCursorLoc();
    		   while (imp.getCanvas().getCursorLoc().equals(cLoc)) {
    			   IJ.wait(10);    			   
    		   }
    			String[] inputNumS = new String[]{""+cLoc.x,""+cLoc.y};
    			tmxn.transform(Double.parseDouble(inputNumS[0]), Double.parseDouble(inputNumS[1]), xyF, false);
//    			IJ.log(""+xyF[0] + ","+xyF[1]);
    			WindowManager.getImage("blank.tif").killRoi();
    			WindowManager.getImage("blank.tif").setRoi((int)xyF[0]-25,(int)xyF[1]-25,50,50);
    	   }
       }
    }

}
