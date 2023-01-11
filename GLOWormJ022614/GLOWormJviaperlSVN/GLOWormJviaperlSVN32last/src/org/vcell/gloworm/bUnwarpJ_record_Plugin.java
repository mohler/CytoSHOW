package org.vcell.gloworm;

import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

import bunwarpj.bUnwarpJ_;
import ij.plugin.*;

public class bUnwarpJ_record_Plugin implements PlugIn {

    public void run(String arg) {
    	
       ImagePlus imp = IJ.getImage();
       boolean applyTfm = IJ.getString("Do what? Build or Apply?", "Build").equals("Apply");
       if (!applyTfm) {
       while(true){
	        int lastNum = imp.getCurrentSlice();
	        IJ.run(imp, "Next Slice [>]", "");
	        int num = imp.getCurrentSlice();
	        if (num == lastNum)
	        	return;
	        ImagePlus impDupNum = new ImagePlus("",imp.getProcessor());
	        impDupNum.setTitle(""+num);
	        impDupNum.setCalibration(imp.getCalibration());
	        impDupNum.show();
	        ImagePlus impRegNumLessOne = WindowManager.getImage(""+(num-1)+"r.tif");
	        IJ.run(impDupNum, "bUnwarpJ", "source_image="+ impDupNum.getTitle()+" target_image="+ impRegNumLessOne.getTitle()+" registration=Mono image_subsample_factor=0 initial_deformation=[Very Coarse] final_deformation=Fine divergence_weight=0 curl_weight=0 landmark_weight=0 image_weight=1 consistency_weight=10 stop_threshold=0.01 verbose save_transformations save_direct_transformation=/Users/wmohler/Documents/N2U_skelROIs/n2u_bUnwarpJ_testFromCytoShow/"+num+"_direct_transf.txt");
	        ImagePlus regStack = WindowManager.getImage("Registered Source Image");
	        ImagePlus impRegNum = new ImagePlus(""+num+"r.tif",regStack.getProcessor());
	        impRegNum.setCalibration(imp.getCalibration());
	        impRegNum.show();
	        IJ.saveAs(impRegNum, "Tiff", "/Users/wmohler/Documents/N2U_skelROIs/n2u_bUnwarpJ_testFromCytoShow/"+num+"r.tif");
	        impDupNum.close();
	        impRegNumLessOne.close();
	        regStack.close();
        }
       } else {
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
       }
    }

}
