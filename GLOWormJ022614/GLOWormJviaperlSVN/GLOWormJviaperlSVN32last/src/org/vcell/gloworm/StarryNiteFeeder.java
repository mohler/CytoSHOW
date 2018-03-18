package org.vcell.gloworm;

import java.awt.Polygon;
import java.io.File;
import java.io.IOException;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.RoiRotator;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

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
			String savetitle = title.replace(":","_").replace(" ","");
			new File(new File(outDir+savetitle+".roi").getParent()).mkdirs();
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
			
			int wavelengths = imp.getNChannels();
			
			Roi theRotatedROI = RoiRotator.rotate(theROI, angle);
			for (int f = 1; f <= imp.getNFrames(); f++) {

				ImageStack stack1 = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
				ImageStack stack2 = new ImageStack((int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
				imp.getWindow().setEnabled(false);


				for (int i = 1; i <= imp.getNSlices(); i++) {
					imp.setPositionWithoutUpdate(1, i, f);

					ImageProcessor ip1 = imp.getProcessor().duplicate();

					int[] ipHis = ip1.getHistogram();
					double ipHisMode = 0.0;
					int ipHisLength = ipHis.length;
					int ipHisMaxBin = 0;
					for (int h=0; h<ipHisLength; h++) {
						if (ipHis[h] > ipHisMaxBin) {
							ipHisMaxBin = ipHis[h];
							ipHisMode = (double)h;
						}
					}
					ip1.subtract(ipHisMode * 1);
					
					ip1.setRoi((Roi) theROI);
					ip1.fillOutside((Roi) theROI);
					ip1 = ip1.crop();
					ImageProcessor ip1r = ip1.createProcessor((int)Math.sqrt(ip1.getWidth()*ip1.getWidth()+ip1.getHeight()*ip1.getHeight())
															, (int)Math.sqrt(ip1.getWidth()*ip1.getWidth()+ip1.getHeight()*ip1.getHeight()));
					ip1r.insert(ip1, (ip1r.getWidth()-ip1.getWidth())/2, (ip1r.getHeight()-ip1.getHeight())/2);
					ip1= ip1r;
					ip1.rotate(angle);
					ip1.setRoi((int)(ip1.getWidth()-theRotatedROI.getBounds().getWidth())/2, (int)(ip1.getHeight()-theRotatedROI.getBounds().getHeight())/2
								, (int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
					ip1 = ip1.crop();

					stack1.addSlice(ip1);

					if (wavelengths >= 2) {
						imp.setPositionWithoutUpdate(wavelengths, i, f);
						ImageProcessor ip2 = imp.getProcessor().duplicate();
						ipHis = ip2.getHistogram();
						ipHisMode = 0.0;
						ipHisLength = ipHis.length;
						ipHisMaxBin = 0;
						for (int h=0; h<ipHisLength; h++) {
							if (ipHis[h] > ipHisMaxBin) {
								ipHisMaxBin = ipHis[h];
								ipHisMode = (double)h;
							}
						}

						ip2.subtract(ipHisMode * 1);
						
						ip2.setRoi((Roi) theROI);
						ip2.fillOutside((Roi) theROI);
						ip2 = ip2.crop();
						ImageProcessor ip2r = ip2.createProcessor((int)Math.sqrt(ip2.getWidth()*ip2.getWidth()+ip2.getHeight()*ip2.getHeight())
																, (int)Math.sqrt(ip2.getWidth()*ip2.getWidth()+ip2.getHeight()*ip2.getHeight()));
						ip2r.insert(ip2, (ip2r.getWidth()-ip2.getWidth())/2, (ip2r.getHeight()-ip2.getHeight())/2);
						ip2= ip2r;
						ip2.rotate(angle);
						ip2.setRoi((int)(ip2.getWidth()-theRotatedROI.getBounds().getWidth())/2, (int)(ip2.getHeight()-theRotatedROI.getBounds().getHeight())/2
									, (int)theRotatedROI.getBounds().getWidth(), (int)theRotatedROI.getBounds().getHeight());
						ip2 = ip2.crop();

						stack2.addSlice(ip2);
					}
				}


				imp.getWindow().setEnabled(true);

				ImagePlus frameRedImp = new ImagePlus("Ch2hisSubCrop",stack2);
				ImagePlus frameGreenImp = new ImagePlus("Ch1hisSubCrop",stack1);

				// Red channel:
				
				String subdir = savetitle;
				new File(outDir+subdir).mkdirs();
				
				// Do we need to save a stack?
				IJ.save(frameRedImp, outDir+subdir+"/aaa"+f+".tif");

				
				// Green channel:
				
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
			
			try {
				Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "F:\\Matlab-R2018a\\bin\\matlab", "-nosplash", "-nodesktop", "-r", "addpath('C:\\Users\\SPIM\\Desktop\\TestLineaging\\Bill_distributionCopy\\source_code\\distribution_code\\'); detect_track_driver_allmatlab('2015_raw_view_dispim_param_boundarypercent0_2.txt','V:\\Bill\\DCFSNout\\Continue_20180216-1651_edges_DCR6834_bbs-8_72.7F_20C_M9_0.1625um_1um_75s_488-561_P0.75-0.5_5ms_s2_Leighton-Yale_Pos4_SPIMA\\Continue_20180216-1651_Edges_Leighton-Yale_Pos4_SPIMA\\','aaa','','V:\\Bill\\DCFSNout\\Continue_20180216-1651_edges_DCR6834_bbs-8_72.7F_20C_M9_0.1625um_1um_75s_488-561_P0.75-0.5_5ms_s2_Leighton-Yale_Pos4_SPIMA\\Continue_20180216-1651_Edges_Leighton-Yale_Pos4_SPIMA\\',0,true)"});
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}	
	}

	public static Process launchMatlabSN() {
		try {
			Process p = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "F:\\Matlab-R2018a\\bin\\matlab", "-nosplash", "-nodesktop", "-r", "addpath('C:\\\\Users\\\\SPIM\\\\Desktop\\\\TestLineaging\\\\Bill_distributionCopy\\\\source_code\\\\distribution_code\\\\'); detect_track_driver_allmatlab('2015_raw_view_dispim_param_boundarypercent0_2.txt','V:\\Bill\\DCFSNout\\Continue_20180216-1651_edges_DCR6834_bbs-8_72.7F_20C_M9_0.1625um_1um_75s_488-561_P0.75-0.5_5ms_s2_Leighton-Yale_Pos4_SPIMA\\Continue_20180216-1651_Edges_Leighton-Yale_Pos4_SPIMA\\','aaa','','V:\\Bill\\DCFSNout\\Continue_20180216-1651_edges_DCR6834_bbs-8_72.7F_20C_M9_0.1625um_1um_75s_488-561_P0.75-0.5_5ms_s2_Leighton-Yale_Pos4_SPIMA\\Continue_20180216-1651_Edges_Leighton-Yale_Pos4_SPIMA\\',0,true)"});
			return p;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

		
	




}
