package org.vcell.gloworm;

import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Stack;

import bunwarpj.*;
import ij.plugin.*;

public class bUnwarpJ_record_Plugin implements PlugIn {

	public void run(String arg) {

		ImagePlus imp = IJ.getImage();
		ImagePlus targetStack = WindowManager.getImage("N2U_uncropped_bUnwarpJ_Apply2NDtfm.tif");
		String mode = "";
		while (!(mode.equals("Build") || mode.equals("Apply") || mode.equals("RemapRois")  || mode.equals("AlignStk") || mode.equals("AlignSlc"))){
			mode = IJ.getString("Do what? Build or Apply or AlignStk  or AlignSlc or RemapRois?", "Build");
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
				bUnwarpJ_.loadElasticTransform("/Users/wmohler/Documents/N2U_skelROIs/n2u_bUnwarpJ_2ndRound_testFromCytoShow/"+num+"_direct_transf.txt", "target", ""+num+".tif");
				IJ.saveAs(impDupNum, "Tiff", "/Users/wmohler/Documents/N2U_skelROIs/N2U_uncropped_bUnwarpJ_Apply2NDtfm/"+num+"tfmApp.tif");
				impDupNum.close();
			}
		} else if (mode.equals("RemapRois")) {
			//THE ORDER OF TARGET AND SOURCE SEEMS TO WORK OPPOSITE OF WHAT I WOULD HAVE EXPECTED.  BUT THIS ALL WORKS.
			int endZ = imp.getNSlices();
			ImagePlus targetImp = WindowManager.getImage("target.tif");
			endZ = targetImp.getNSlices();
			int startZ = targetImp.getSlice();
			for (int z=startZ;z<=endZ;z++) {
				imp.setPosition(imp.getChannel(), z, imp.getFrame());
				targetImp.setPosition(imp.getChannel(), z, imp.getFrame());
				imp.killRoi();
				targetImp.killRoi();
				Roi[] sourceRois = imp.getRoiManager().getSliceSpecificRoiArray(z, imp.getFrame(), false);
				for (Roi sroi:sourceRois) {
					if (sroi instanceof PointRoi) {
						imp.setRoi(sroi);
						break;
					}
				}
				Roi[] targetRois = targetImp.getRoiManager().getSliceSpecificRoiArray(z, imp.getFrame(), false);
				for (Roi troi:targetRois) {
					if (troi instanceof PointRoi) {
						targetImp.setRoi(troi);
						break;
					}
				}
				
				if (imp.getRoi() == null)
					targetImp.killRoi();
				if (targetImp.getRoi() == null)
					imp.killRoi();
				
				//this method works with landmarks selected OR without landmarks if nothing selected
				//landmarks do still affect initial Affine fit, even if landmarkWeight parameter is set to 0.
				//With 20 scattered landmark points and 
				//landmarkWeight set to 1 and imageWeight set to 1, it succeeds in some seriously funky fixes to build an excellent overall transform!!!
				Transformation tmxn = bUnwarpJ_.computeTransformationBatch(targetImp, imp, null, null, new Param(1, 0, 0, 2, 0.1, 0.1, 1, 1, 10, 0.01));
				targetImp.setPosition(imp.getChannel(), z, imp.getFrame());

				Roi[] sliceRois = imp.getRoiManager().getSliceSpecificRoiArray(imp.getSlice(), imp.getFrame(), false);
				for (Roi roi:sliceRois) {
					double[] xyF = new double[2];
					tmxn.transform((double) roi.getBounds().getCenterX(), (double) roi.getBounds().getCenterY(), xyF, true);
					Roi newRoi = (Roi)roi.clone();
					if (roi instanceof TextRoi) {
						newRoi.setLocation(xyF[0]-roi.getBounds().width/2, xyF[1]-roi.getBounds().height/2);
					} else if (roi instanceof ShapeRoi) {
						Roi[] rois = ((ShapeRoi) roi).getRois();
						Roi[] scaledRois = new Roi[rois.length];
						for (int r=0;r<rois.length;r++) {
							Polygon poly = rois[r].getPolygon();

							for (int p=0;p<poly.npoints;p++) {
								tmxn.transform(poly.xpoints[p], poly.ypoints[p], xyF, true);
								poly.xpoints[p] = (int) xyF[0];
								poly.ypoints[p] = (int) xyF[1];
							}
							scaledRois[r] = new ShapeRoi(poly);
							scaledRois[r].copyAttributes(rois[r]);
						}
						ShapeRoi scaledRoi = null;
						for (Roi scaledPart:scaledRois) {
							if (scaledRoi == null) {
								scaledRoi = new ShapeRoi(scaledPart);
							} else {
								// can't really do anything with additional saved Rois, can I?
							}
						}

						scaledRoi.copyAttributes(roi);
						newRoi = scaledRoi;

					} else {
						Polygon poly = roi.getPolygon();
						for (int p=0; p< poly.npoints; p++) {
							tmxn.transform(poly.xpoints[p], poly.ypoints[p], xyF, true);
							poly.xpoints[p] = (int) xyF[0];
							poly.ypoints[p] = (int) xyF[1];
						}
						ShapeRoi newShapeRoi = new ShapeRoi(poly);
						newShapeRoi.copyAttributes(newRoi);
						newRoi = newShapeRoi;
					}
					targetImp.setPosition(imp.getChannel(), z, imp.getFrame());
					targetImp.getRoiManager().addRoi(newRoi, false, roi.getStrokeColor(), roi.getFillColor(), 0, true);
				}
				
				IJ.log(" ");
				IJ.log("*******************************");
				IJ.log("Slice "+(z)+" Tags warp fit complete.  "+  DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now()));  
				IJ.log("*******************************");
				IJ.log(" ");

				
			}

			//Code below is nice interaction confirmation that tmxn works!!
			//    	   while(!IJ.shiftKeyDown()){
			//    		   double[] xyF = new double[2];
			//    			
			////    			String inputString = IJ.getString("x and y coords", "100,100");
			//    		   Point cLoc = imp.getCanvas().getCursorLoc();
			//    		   while (imp.getCanvas().getCursorLoc().equals(cLoc)) {
			//    			   IJ.wait(10);    			   
			//    		   }
			////    			String[] inputNumS = new String[]{""+cLoc.x,""+cLoc.y};
			//    			tmxn.transform((double) cLoc.x, (double) cLoc.y, xyF, false);
			////    			IJ.log(""+xyF[0] + ","+xyF[1]);
			//    			WindowManager.getImage("target.tif").killRoi();
			//    			WindowManager.getImage("target.tif").setRoi((int)xyF[0]-25,(int)xyF[1]-25,50,50);
			//    	   }
			//       }
		} else if (mode.equals("AlignStk") ||mode.equals("AlignSlc")) {
			//THE ORDER OF TARGET AND SOURCE SEEMS TO WORK OPPOSITE OF WHAT I WOULD HAVE EXPECTED.  BUT THIS ALL WORKS.
			if (imp.getTitle() == "target.tif")
				return;
			int endZ = imp.getNSlices();
			ImagePlus targetImp = WindowManager.getImage("target.tif");
			int startZ = targetImp.getNSlices();
			if (mode.equals("AlignSlc")) endZ = startZ;
			for (int z=startZ;z<=endZ;z++) {
				imp.setPosition(imp.getChannel(), z, imp.getFrame());
				targetImp.setPosition(imp.getChannel(), z, imp.getFrame());
				imp.killRoi();
				targetImp.killRoi();
				Roi[] sourceRois = imp.getRoiManager().getSliceSpecificRoiArray(z, imp.getFrame(), false);
				for (Roi sroi:sourceRois) {
					if (sroi instanceof PointRoi) {
						imp.setRoi(sroi);
						break;
					}
				}
				Roi[] targetRois = targetImp.getRoiManager().getSliceSpecificRoiArray(z, imp.getFrame(), false);
				for (Roi troi:targetRois) {
					if (troi instanceof PointRoi) {
						targetImp.setRoi(troi);
						break;
					}
				}
				
				if (imp.getRoi() == null)
					targetImp.killRoi();
				if (targetImp.getRoi() == null)
					imp.killRoi();
				
				//this method works with landmarks selected OR without landmarks if nothing selected
				//landmarks do still affect initial Affine fit, even if landmarkWeight parameter is set to 0.
				//With 20 scattered landmark points and 
				//landmarkWeight set to 1 and imageWeight set to 1, it succeeds in some seriously funky fixes to build an excellent overall transform!!!
				
				//This tfm warps next image in source stack to fit the last slice in the growing target stack:
				Transformation warp = bUnwarpJ_.computeTransformationBatch(targetImp, imp, null, null, new Param(1, 0, 0, 2, 0.1, 0.1, 1, 1, 10, 0.01));
				
				//This reverse tfm is used to remap the source multipoint roi to fit the new warped last target slice:
//				Transformation revWarp = bUnwarpJ_.computeTransformationBatch(imp, targetImp, null, null, new Param(1, 0, 0, 2, 0.1, 0.1, 1, 1, 10, 0.01));

				targetImp.setPosition(imp.getChannel(), z, imp.getFrame());
				
				//This direct unidirectional case seems to work!
			     if (true) {
			        	// Do unidirectional registration
//			        	warp.doUnidirectionalRegistration();
//			        	revWarp.doUnidirectionalRegistration();
			        	
			    		ImagePlus sourceOut = warp.getDirectResults();
//			    		sourceOut.show();

//			    		targetImp.getStack().deleteSlice(z+1);
			    		targetImp.getStack().addSlice("resultSlice "+z+0+1, sourceOut.getStack().getProcessor(1).duplicate());
			    		targetImp.setWindow(new StackWindow(targetImp,false)); 
			    		targetImp.setTitle("target.tif");

			    		sourceOut.close();
			        } else {

			        }

					Roi[] sliceRois = imp.getRoiManager().getSliceSpecificRoiArray(imp.getSlice(), imp.getFrame(), false);
					for (Roi roi:sliceRois) {
						double[] xyF = new double[2];
						warp.transform((double) roi.getBounds().getCenterX(), (double) roi.getBounds().getCenterY(), xyF, true);
						Roi newRoi = (Roi)roi.clone();
						if (roi instanceof TextRoi) {
							newRoi.setLocation(xyF[0]-roi.getBounds().width/2, xyF[1]-roi.getBounds().height/2);
						} else if (roi instanceof ShapeRoi) {
							Roi[] rois = ((ShapeRoi) roi).getRois();
							Roi[] scaledRois = new Roi[rois.length];
							for (int r=0;r<rois.length;r++) {
								Polygon poly = rois[r].getPolygon();

								for (int p=0;p<poly.npoints;p++) {
									warp.transform(poly.xpoints[p], poly.ypoints[p], xyF, true);
									poly.xpoints[p] = (int) xyF[0];
									poly.ypoints[p] = (int) xyF[1];
								}
								scaledRois[r] = new ShapeRoi(poly);
								scaledRois[r].copyAttributes(rois[r]);
							}
							ShapeRoi scaledRoi = null;
							for (Roi scaledPart:scaledRois) {
								if (scaledRoi == null) {
									scaledRoi = new ShapeRoi(scaledPart);
								} else {
									// can't really do anything with additional saved Rois, can I?
								}
							}

							scaledRoi.copyAttributes(roi);
							newRoi = scaledRoi;

						} else {
							Polygon poly = roi.getPolygon();
							for (int p=0; p< poly.npoints; p++) {
								warp.transform(poly.xpoints[p], poly.ypoints[p], xyF, true);
								poly.xpoints[p] = (int) xyF[0];
								poly.ypoints[p] = (int) xyF[1];
							}
							PointRoi newPointRoi = new PointRoi(poly);
							newPointRoi.copyAttributes(newRoi);
							newRoi = newPointRoi;
						}
						targetImp.setPosition(imp.getChannel(), z+1, imp.getFrame());
						targetImp.getRoiManager().addRoi(newRoi, false, roi.getStrokeColor(), roi.getFillColor(), 0, true);
						targetImp.getRoiManager().setShowAllCheckbox(true);
					}
					IJ.log(" ");
					IJ.log("*******************************");
					IJ.log("Slice "+(z+1)+" image warp fit complete.  "+  DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(LocalDateTime.now()));  
					IJ.log("*******************************");
					IJ.log(" ");

			}

			//Code below is nice interaction confirmation that tmxn works!!
			//    	   while(!IJ.shiftKeyDown()){
			//    		   double[] xyF = new double[2];
			//    			
			////    			String inputString = IJ.getString("x and y coords", "100,100");
			//    		   Point cLoc = imp.getCanvas().getCursorLoc();
			//    		   while (imp.getCanvas().getCursorLoc().equals(cLoc)) {
			//    			   IJ.wait(10);    			   
			//    		   }
			////    			String[] inputNumS = new String[]{""+cLoc.x,""+cLoc.y};
			//    			tmxn.transform((double) cLoc.x, (double) cLoc.y, xyF, false);
			////    			IJ.log(""+xyF[0] + ","+xyF[1]);
			//    			WindowManager.getImage("target.tif").killRoi();
			//    			WindowManager.getImage("target.tif").setRoi((int)xyF[0]-25,(int)xyF[1]-25,50,50);
			//    	   }
			//       }
		}

	}
	

}
