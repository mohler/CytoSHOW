package org.vcell.gloworm;

import ij.plugin.*;
import bunwarpj.*;
import ij.*;

public class bUwarpJApplyTfm_PlugIn implements PlugIn {

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
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
			bUnwarpJ_.loadElasticTransform("/Users/wmohler/Documents/N2U_skelROIs/N2U_firstRound_direct_transf_bUnwarpJ/"+num+"_direct_transf.txt", null, ""+num+".tif");
	        IJ.saveAs(impDupNum, "Tiff", "/Users/wmohler/Documents/N2U_skelROIs/n2u_bUnwarpJ_testApplyTfmFromCytoShow/"+num+"tfmApp.tif");
	        impDupNum.close();
		}
	}
}
