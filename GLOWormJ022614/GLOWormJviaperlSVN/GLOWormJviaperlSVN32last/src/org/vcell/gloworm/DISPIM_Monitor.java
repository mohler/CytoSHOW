package org.vcell.gloworm;

import java.io.File;
import java.util.Arrays;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.ListVirtualStack;
import ij.plugin.PlugIn;

public class DISPIM_Monitor implements PlugIn {

	public void run(String arg) {

		IJ.log(arg);
	    int minA =0;
	    int maxA = 255;
	    String channelsA = "11";
	    String modeA="composite";
	    double vWidth = 0.163;
	    double vHeight = 0.163;
	    double vDepth = 1.000;
	    String vUnit = "micron";

	    String dir = "";
		dir = arg;
		IJ.log(dir);
		//waitForUser("");
		while (!(new File(dir)).isDirectory()) {
			dir = IJ.getDirectory("Select top directory for diSPIM raw data");
		}
		IJ.log(dir);
		String[] fileListA = {""};
		String[] fileListB = {""};
//		fileListB = newArray("");
		String[] fileRanksA = {""};
		String[] fileRanksB = {""};
		String[] fileNumsA = {""};
		String[] fileNumsB = {""};
		String[] fileSortA = {""};
		String[] fileSortB = {""};
		String[] newTifListA = {""};
		String[] newTifListB = {""};
		String[] listA = {""};
		String[] listB = {""};

		IJ.saveString("", dir+"Big5DFileListA.txt");
		while(!(new File(dir+"Big5DFileListA.txt")).exists())
			IJ.wait(100);
		IJ.saveString("", dir+"Big5DFileListB.txt");
		while(!(new File(dir+"Big5DFileListB.txt")).exists())
			IJ.wait(100);
		IJ.saveString("", dir+"BigMAXFileListA.txt");
		while(!(new File(dir+"BigMAXFileListA.txt")).exists())
			IJ.wait(100);
		IJ.saveString("", dir+"BigMAXFileListB.txt");
		while(!(new File(dir+"BigMAXFileListB.txt")).exists())
			IJ.wait(100);

		GenericDialog gd = new GenericDialog("Data Set Parameters?");
		gd.addNumericField("Wavelengths", 2, 0);
		gd.addNumericField("Z Slices/Stack", 49, 0);
		gd.showDialog();;
		int wavelengths = (int) gd.getNextNumber();
		int zSlices = (int) gd.getNextNumber();

		fileListA = new File(""+dir+"SPIMA").list();
		fileListB = new File(""+dir+"SPIMB").list();
		fileRanksA = Arrays.copyOf(fileListA, fileListA.length);
		fileRanksB = Arrays.copyOf(fileListB, fileListB.length);
		fileNumsA = Arrays.copyOf(fileListA, fileListA.length);
		fileNumsB = Arrays.copyOf(fileListB, fileListB.length);
		fileSortA = Arrays.copyOf(fileListA, fileListA.length);
		fileSortB = Arrays.copyOf(fileListB, fileListB.length);

	    String big5DFileListAString = ("");	    
	    String big5DFileListBString =("");

	    for (int a=0;a<fileListA.length;a++) {
		  if(!fileListA[a].endsWith(".roi") && !fileListA[a].endsWith(".DS_Store") ){
			String sring = fileListA[a].replace("/", "");
			String subsring = sring;
			String prefix = "";
			double n = Double.NaN;
			try {
				n = Integer.parseInt(subsring);
			} catch(NumberFormatException e) {
				n = Double.NaN;
			}
			while (Double.isNaN(n)) {
				try {
					prefix = prefix + subsring.substring(0,1);
					subsring = subsring.substring(1);
					n = Integer.parseInt(subsring);
					IJ.log(subsring);
					IJ.log(prefix);
				} catch(NumberFormatException e) {
					n = Double.NaN;
				}
			}
			if(prefix.toLowerCase().startsWith("t")
			 	|| prefix.toLowerCase().startsWith("f"))
				prefix = "aaaaa"+prefix;
			int numer = Integer.parseInt(subsring);
			IJ.log(subsring+" "+numer);
			fileNumsA[a] =prefix+ IJ.pad(numer,6)+"|"+sring;
			fileNumsB[a] =prefix+ IJ.pad(numer,6)+"|"+sring;
		  } else {
			  fileNumsA[a] = "";
			  fileNumsB[a] = "";
		  }

	    }
	   	Arrays.sort(fileNumsA);
	   	Arrays.sort(fileNumsB);

	   	for (int r=0;r<fileNumsA.length;r++) {
	   			String[] splt =fileNumsA[r].split("\\|");
	   			if (splt.length > 1)
	   				fileSortA[r] = splt [1];
	   			else 
	   				fileSortA[r] = "";
	   			IJ.log(r+" "+" "+fileNumsA[r]+" "+fileSortA[r]);
	   			splt =fileNumsB[r].split("\\|");
	   			if (splt.length > 1)
	   				fileSortB[r] = splt[1];
	   			else 
	   				fileSortB[r] = "";
	   		
	   	}
		   
		for (int d=0;d<fileSortA.length;d++) {
			boolean skipIt = false;
			String nextPathA =  dir + "SPIMA" + File.separator + fileSortA[d];
			String nextPathB =  dir + "SPIMB" + File.separator + fileSortB[d];
		    IJ.log(nextPathA);
		    IJ.log(nextPathB);
	 	    if ( (new File(nextPathA)).isDirectory()  && (new File(nextPathB)).isDirectory()) {
				newTifListA = (new File(nextPathA)).list();
				newTifListB = (new File(nextPathB)).list();
				if (newTifListA.length != newTifListB.length || newTifListA.length != wavelengths*zSlices)
					skipIt = true;
				if(!skipIt) {
					Arrays.sort(newTifListA);		
					for (int f=0;f<newTifListA.length;f++) {
						while(!(new File(dir+"Big5DFileListA.txt")).exists())
						IJ.wait(100);
						if (big5DFileListAString.indexOf( nextPathA + File.separator+newTifListA[f])<0)
							IJ.append(nextPathA + File.separator +newTifListA[f], dir+"Big5DFileListA.txt");
				    }
					Arrays.sort(newTifListB);		
					for (int f=0;f<newTifListB.length;f++) {
						while(!(new File(dir+"Big5DFileListB.txt")).exists())
						IJ.wait(100);
						if (big5DFileListBString.indexOf( nextPathB + File.separator+newTifListB[f])<0)
							IJ.append(nextPathB + File.separator +newTifListB[f], dir+"Big5DFileListB.txt");
				    }
			    }
		    }

		}


	    IJ.log(""+WindowManager.getImageCount());

	    if ((new File(dir+"Big5DFileListA.txt" )).length() >0) {
//		    IJ.run("Stack From List...", "open="+dir+"Big5DFileListA.txt use");
		    ImagePlus impA = new ImagePlus();
		    impA.setStack(new ListVirtualStack(dir+"Big5DFileListA.txt"));
	  	    int stkNSlices = impA.getNSlices();

		    impA.setTitle("SPIMA: "+dir);

			impA.setDimensions(wavelengths, zSlices, stkNSlices/(wavelengths*zSlices));
			IJ.log(wavelengths+" "+zSlices+" "+stkNSlices/(wavelengths*zSlices));
			if (wavelengths > 1){
//				IJ.run("Make Composite", "display=Composite");
				impA = new CompositeImage(impA);
				while (!impA.isComposite()) {
					IJ.wait(100);
					//selectWindow("SPIMA: "+dir);
				}
		  	    impA.setPosition(1, 1, 1);	
			    IJ.run("Green");
			    impA.resetDisplayRange();
		  	    impA.setPosition(2, 1, 1);	
			    IJ.run("Red");
			    impA.resetDisplayRange();
		    }
			Calibration cal = impA.getCalibration();
			cal.pixelWidth = vWidth;
			cal.pixelHeight = vHeight;
			cal.pixelDepth = vDepth;
			cal.setUnit(vUnit);

	 	    impA.setPosition(wavelengths, zSlices/2, stkNSlices/(wavelengths*zSlices));	
			
		    impA.resetDisplayRange();
	  	    impA.setPosition(1, zSlices/2, stkNSlices/(wavelengths*zSlices));	
		    impA.resetDisplayRange();
			((CompositeImage)impA).setMode(CompositeImage.COMPOSITE);
			impA.show();

		}

	    if ((new File(dir+"Big5DFileListB.txt" )).length() >0) {
//		    IJ.run("Stack From List...", "open="+dir+"Big5DFileListB.txt use");
		    ImagePlus impB = new ImagePlus();
		    impB.setStack(new ListVirtualStack(dir+"Big5DFileListB.txt"));
	  	    int stkNSlices = impB.getNSlices();

		    impB.setTitle("SPIMB: "+dir);

			impB.setDimensions(wavelengths, zSlices, stkNSlices/(wavelengths*zSlices));
			IJ.log(wavelengths+" "+zSlices+" "+stkNSlices/(wavelengths*zSlices));
			if (wavelengths > 1){
				impB = new CompositeImage(impB);
				while (!impB.isComposite()) {
					IJ.wait(100);
					//selectWindow("SPIMB: "+dir);
				}
		  	    impB.setPosition(1, 1, 1);	
			    IJ.run("Green");
			    impB.resetDisplayRange();
		  	    impB.setPosition(2, 1, 1);	
			    IJ.run("Red");
			    impB.resetDisplayRange();
		    }
			Calibration cal = impB.getCalibration();
			cal.pixelWidth = vWidth;
			cal.pixelHeight = vHeight;
			cal.pixelDepth = vDepth;
			cal.setUnit(vUnit);

	 	    impB.setPosition(wavelengths, zSlices/2, stkNSlices/(wavelengths*zSlices));	
			
		    impB.resetDisplayRange();
	  	    impB.setPosition(1, zSlices/2, stkNSlices/(wavelengths*zSlices));	
		    impB.resetDisplayRange();
			((CompositeImage)impB).setMode(CompositeImage.COMPOSITE);
			impB.show();

		}
		
		IJ.run("Tile");
	    IJ.log(""+WindowManager.getImageCount());

//		while (true) {
//			focus = false;
//		    listA = getFileList(""+dir+"SPIMA" + File.separator);
//	 	    listB = getFileList(""+dir +"SPIMB" + File.separator);
//		    big5DFileListAString = File.openAsString(dir+"Big5DFileListA.txt");	    
//		    big5DFileListBString = File.openAsString(dir+"Big5DFileListB.txt");
//
//		    while (lengthOf(fileListA) == lengthOf(listA) || lengthOf(fileListB) == lengthOf(listB) ) {
//				if (IJ.escapePressed() == "true")
//					exit("Monitoring of "+ dir+ " cancelled by Escape.");
//			 	listA = getFileList(""+dir +"SPIMA" + File.separator);
//				listB = getFileList(""+dir +"SPIMB" + File.separator);
//				wait(5000);
//		    }	
//
//			if (isOpen("Display Channels")) {
//				selectWindow("Display Channels");
//				run("Close");
//			}
//
//		    fileListA = getFileList(dir +"SPIMA" + File.separator);
//		    fileListB = getFileList(dir + "SPIMB" + File.separator);
//
//		    modDateA = 0;
//		    recentestA = "";
//		    for (a=0;a<lengthOf(fileListA);a++) {
//				if (modDateA < File.lastModified(dir +"SPIMA" + File.separator + fileListA[a])) {
//					modDateA = File.lastModified(dir + "SPIMA" + File.separator + fileListA[a]);
//					recentestA = dir + "SPIMA" + File.separator + fileListA[a];
//				}
//		    }
//		    IJ.log(recentestA +"\n"+ modDateA);
//		    if (File.isDirectory(recentestA) == 1) {
//			    newTifList = "";
//			    while (lengthOf(newTifList) != wavelengths*zSlices)
//					newTifList = getFileList(recentestA);
//				Array.sort(newTifList);		
//				for (f=0;f<lengthOf(newTifList);f++) {
//					while(!File.exists(dir+"Big5DFileListA.txt"))
//						wait(100);
//					if (indexOf(big5DFileListAString, recentestA+newTifList[f])<0)
//						File.append(recentestA+newTifList[f], dir+"Big5DFileListA.txt");
//				}
//		    }
//		    
//		    modDateB = 0;
//		    recentestB = "";
//		    recentestBname ="";
//		    for (b=0;b<lengthOf(fileListB);b++) {
//				if (modDateB < File.lastModified(dir +"SPIMB" + File.separator + fileListB[b])) {
//					modDateB= File.lastModified(dir + "SPIMB" + File.separator + fileListB[b]);
//					recentestB = dir + "SPIMB" + File.separator + fileListB[b];
//					recentestBname = fileListB[b];
//				}
//		    }
//		    IJ.log(recentestB+"\n"+ modDateB);
//			if(startsWith(toLowerCase(recentestBname), "f"))
//			 	focus = true;
//		    if (File.isDirectory(recentestB) == 1) {
//			    newTifList = "";
//			    while (lengthOf(newTifList) != wavelengths*zSlices)
//					newTifList = getFileList(recentestB);
//				Array.sort(newTifList);
//				for (f=0;f<lengthOf(newTifList);f++) {
//					while(!File.exists(dir+"Big5DFileListB.txt"))
//						wait(100);
//					if (indexOf(big5DFileListBString, recentestB+newTifList[f])<0)
//						File.append(recentestB+newTifList[f], dir+"Big5DFileListB.txt");
//				}
//		    }
//		    
//			IJ.log(nImages+" batch");
//			
//		    if (isOpen("SPIMA: "+dir)) {
//		    	selectWindow("SPIMA: "+dir);
//					if (wavelengths > 1) {
//						Stack.getActiveChannels(channelsA);
//						Stack.getDisplayMode(modeA);
//					}
//			    Stack.getPosition(channelA, sliceA, frameA);
//			  	Stack.setPosition(1, sliceA, frameA);	
//				getMinAndMax(minA1, maxA1);
//			  	if (wavelengths > 1) {
//			  		Stack.setPosition(2, sliceA, frameA);	
//					getMinAndMax(minA2, maxA2);
//				}
//			    getLocationAndSize(xA, yA, widthA, heightA);
//			    IJ.log(nImages);
//			    close();
//			    IJ.log(nImages);
//		    } else {
//				channelA=1; sliceA=1; frameA=1; 
//				xA=0; yA=0; widthA=0; heightA=0; 
//				minA1=0;minA2=0;maxA1=255;maxA2=255;
//		    }
//		    
//			setBatchMode(true);
//		    if (File.length(dir+"Big5DFileListA.txt" ) >0) {
//			    run("Stack From List...", "open="+dir+"Big5DFileListA.txt use");
//			    stkNSlices = nSlices;
//			    rename("SPIMA: "+dir);
//		    	IJ.log(nImages);
//
//				Stack.setDimensions(wavelengths, zSlices, stkNSlices/(wavelengths*zSlices));
//				if (wavelengths > 1){
//					run("Make Composite", "display=Composite");
//				    IJ.log(nImages+" HS");
//			  	    Stack.setPosition(1, sliceA, frameA);	
//				    run("Green");
//				    setMinAndMax(minA1, maxA1);	
//			  	    Stack.setPosition(2, sliceA, frameA);	
//				    run("Red");
//				    setMinAndMax(minA2, maxA2);	
//			    } else {
//					selectWindow("SPIMA: "+dir);
//				    setMinAndMax(minA1, maxA1);
//		    	}
//		  	    Stack.setPosition(channelA, sliceA, stkNSlices/(wavelengths*zSlices));
//				if (wavelengths > 1) {
//				    Stack.setDisplayMode(modeA);	    
//				    Stack.setActiveChannels(channelsA);
//				}
//
//			    setVoxelSize(vWidth, vHeight, vDepth, vUnit);    
//				updateDisplay();
//			
//				setBatchMode(false);
//				selectWindow("SPIMA: "+dir);
//				setLocation(xA,yA);
//		    }
//
//		    minB =0;
//		    maxB = 255;
//		    channelsB = "11";
//		    modeB="composite";
//		    
//			if (isOpen("SPIMB: "+dir)) {
//				selectWindow("SPIMB: "+dir);
//				if (wavelengths > 1) {
//					Stack.getActiveChannels(channels_B);
//					Stack.getDisplayMode(mode_B);
//				}
//			    Stack.getPosition(channelB, sliceB, frameB);
//			  	Stack.setPosition(1, sliceB, frameB);	
//				getMinAndMax(minB1, maxB1);
//				if (wavelengths > 1) {
//				  	Stack.setPosition(2, sliceB, frameB);	
//					getMinAndMax(minB2, maxB2);
//				}
//			    getLocationAndSize(xB, yB, widthB, heightB);
//			    IJ.log(nImages);
//			    close();
//			    IJ.log(nImages);
//		   } else {
//				channelB=1; sliceB=1; frameB=1; 
//				xB=0; yB=0; widthB=0; heightB=0; 
//				minB1=0;minB2=0;maxB1=255;maxB2=255;
//		   }
//			setBatchMode(true);
//			
//		    if (File.length(dir+"Big5DFileListB.txt" ) >0) {
//			    run("Stack From List...", "open="+dir+"Big5DFileListB.txt use");
//			    stkNSlices = nSlices;
//
//			    rename("SPIMB: "+dir);
//
//				Stack.setDimensions(wavelengths, zSlices, stkNSlices/(wavelengths*zSlices));
//				if (wavelengths > 1){
//					run("Make Composite", "display=Composite");
//			  	    Stack.setPosition(1, sliceB, frameB);	
//				    run("Green");
//				    setMinAndMax(minB1, maxB1);
//			  	    Stack.setPosition(2, sliceB, frameB);	
//				    run("Red");
//				    setMinAndMax(minB2, maxB2);
//			    } else {
//					selectWindow("SPIMB: "+dir);
//				    setMinAndMax(minB1, maxB1);
//		    	}
//		  	    Stack.setPosition(channelB, sliceB, stkNSlices/(wavelengths*zSlices));		    
//				if (wavelengths > 1) {
//				    Stack.setDisplayMode(modeA);	    
//				    Stack.setActiveChannels(channelsA);
//				}
//
//			    setVoxelSize(vWidth, vHeight, vDepth, vUnit);    
//				updateDisplay();
//
//			    setBatchMode(false);
//				selectWindow("SPIMB: "+dir);
//			    setLocation(xB,yB);
//			    updateDisplay();
//		    }
//		    
//		    deconString = 'nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.1625\", \"res_y double 0.1625\", \"res_z double 1.0\", \"mtxFileDirectory string '+dir+'SPIMB_processed\", \"spimAFileDir string '+dir+'SPIMB_processed\", \"spimBFileDir string '+dir+'SPIMA_processed\", \"baseImage string SPIMA\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconvDirString string '+dir+'Deconvolution\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\")';
//		    File.saveString(deconString, dir+"GenerateFusion.sct");
//		    
//		    File.delete(dir+"GenerateFusion.bat");
//		    f = File.open(dir+"GenerateFusion.bat");
//		    batStringA = "cd C:\\\\Program Files\\\\mipav";
//		    batStringB = "mipav -s "+replace(dir,"\\\\","\\\\\\\\")+"GenerateFusion.sct -hide";
//		    batStringC = "C:";
//		    IJ.log(f,batStringC);
//		    IJ.log(f,batStringA);
//		    IJ.log(f,batStringB);
//			File.close(f);	    
//			
//		    if (focus) {
//			    IJ.log(nImages);
//			    File.delete(dir+"AutoFocusCommaSpace.txt");
//			    //File.delete(dir+"AutoFocusNewLine.txt");
//				autoFocusString = "";
//				for (i=1;i<=nImages;i++){
//					IJ.log(nImages+" "+i);
//					
//					setBatchMode(true);
//					selectImage(i);
//			
//					source = getTitle();
//					Stack.getDimensions(width, height, channels, zDepth, frames);
//					Stack.getPosition(channel, slice, frame);
//					for (z=0; z<zDepth; z++) { 
//						Stack.setSlice(z+1);
//						run("FFT, no auto-scaling");
//						if (z==0) {
//							rename("FFTstack");	
//						} else {
//							run("Select All");
//							run("Copy");
//							close();
//							selectWindow("FFTstack");
//							run("Add Slice");
//							if (z>0)
//								Stack.setSlice(z+2);
//							run("Select All");
//							run("Paste");
//						}
//						selectWindow(source);
//					}
//					selectWindow("FFTstack");
//					makeOval(250, 250, 13, 13);
//					run("Clear", "stack");
//					makeOval(220, 220, 73, 73);
//					run("Clear Outside", "stack");
//					run("Plot Z-axis Profile");
//					close();
//					selectWindow("FFTstack");
//					close();
//					
//					sliceAvgs = newArray(zDepth);
//					List.clear;
//					for (z=1; z<zDepth; z++) { 
//						sliceAvgs[z] = getResult("Mean", z);
//						//IJ.log(sliceAvgs[z] );
//						List.set(sliceAvgs[z] , z);
//					}
//					
//					Array.sort(sliceAvgs);
//					IJ.log(source+": Best focus in slice "+List.get(sliceAvgs[zDepth-1]));
//					autoFocusString = autoFocusString + List.get(sliceAvgs[zDepth-1])+", ";
//					selectWindow("Results");
//					run("Close");
//					setBatchMode(false);
//					selectWindow(source);
//					Stack.setPosition(channel, slice, frame);
//					updateDisplay();
//				}
//				File.saveString(autoFocusString, dir+"AutoFocusCommaSpace.txt");			
//			}		    
//		}
		
	}

}
