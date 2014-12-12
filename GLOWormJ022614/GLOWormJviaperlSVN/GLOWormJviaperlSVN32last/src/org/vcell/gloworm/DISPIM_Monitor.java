package org.vcell.gloworm;

import java.awt.Button;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.macro.MacroRunner;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.plugin.FFT;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.PlugIn;
import ij.plugin.filter.Analyzer;
import ij.plugin.frame.SyncWindows;

public class DISPIM_Monitor implements PlugIn {

	public void run(String arg) {
		String[] args = arg.split("\\|");
		IJ.log(arg);
		int minA =0;
		int maxA = 255;
		String channelsA = "11";
		String modeA="composite";
		double vWidth = 0.163;
		double vHeight = 0.163;
		double vDepthRaw = 1.000;
		double vDepthDecon = 0.163;
		String vUnit = "micron";
		boolean doDecon=true;

		String dir = "";
		dir = args[0];
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
		ImagePlus impA = null;
		ImagePlus impB = null;
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
		
		int wavelengths;
		int zSlices;
		if(args.length>2){
			wavelengths = Integer.parseInt(args[1]);
			zSlices = Integer.parseInt(args[2]);
		} else {
			GenericDialog gd = new GenericDialog("Data Set Parameters?");
			gd.addNumericField("Wavelengths", 2, 0);
			gd.addNumericField("Z Slices/Stack", 49, 0);
			gd.showDialog();;
			wavelengths = (int) gd.getNextNumber();
			zSlices = (int) gd.getNextNumber();
		}
		
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
						n = Integer.parseInt(subsring.split(" ")[0]);
						IJ.log(subsring);
						IJ.log(prefix);
					} catch(NumberFormatException ne) {
						n = Double.NaN;
					} catch(StringIndexOutOfBoundsException se) {
						n = Double.NaN;
					}
				}
				if(prefix.toLowerCase().startsWith("t")
						|| prefix.toLowerCase().startsWith("f"))
					prefix = "aaaaa"+prefix;
				int numer = Integer.parseInt(subsring.split(" ")[0]);
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
				if (newTifListA.length != newTifListB.length || newTifListA.length < wavelengths*zSlices)
					skipIt = true;
				if(!skipIt) {
					Arrays.sort(newTifListA);		
					for (int f=0;f<newTifListA.length;f++) {
						while(!(new File(dir+"Big5DFileListA.txt")).exists())
							IJ.wait(100);
						if (!newTifListA[f].endsWith(".roi") && !newTifListA[f].endsWith(".DS_Store")
								&& big5DFileListAString.indexOf( nextPathA + File.separator+newTifListA[f])<0)
							IJ.append(nextPathA + File.separator +newTifListA[f], dir+"Big5DFileListA.txt");
					}
					Arrays.sort(newTifListB);		
					for (int f=0;f<newTifListB.length;f++) {
						while(!(new File(dir+"Big5DFileListB.txt")).exists())
							IJ.wait(100);
						if (!newTifListB[f].endsWith(".roi") && !newTifListB[f].endsWith(".DS_Store")
								&& big5DFileListBString.indexOf( nextPathB + File.separator+newTifListB[f])<0)
							IJ.append(nextPathB + File.separator +newTifListB[f], dir+"Big5DFileListB.txt");
					}
				}
			}

		}


		IJ.log(""+WindowManager.getImageCount());

		if ((new File(dir+"Big5DFileListA.txt" )).length() >0) {
			//		    IJ.run("Stack From List...", "open="+dir+"Big5DFileListA.txt use");
			impA = new ImagePlus();
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
			cal.pixelDepth = vDepthRaw;
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
			impB = new ImagePlus();
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
			cal.pixelDepth = vDepthRaw;
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
		
		if (doDecon) {

			String[] dirChunks = dir.split("\\\\");
			String dirName = dirChunks[dirChunks.length-1];
			WindowManager.setTempCurrentImage(impA);
			if (!((new File(dir+ dirName +"A_crop.roi")).canRead())) {
				IJ.makeRectangle(0,0,325,425);
			} else {
				IJ.open(dir+ dirName +"A_crop.roi");
			}
			WindowManager.setTempCurrentImage(impB);
			if (!((new File(dir+ dirName +"B_crop.roi")).canRead())) {
				IJ.makeRectangle(0,0,325,425);
			} else {
				IJ.open(dir+ dirName +"B_crop.roi");
			}
			WindowManager.setTempCurrentImage(null);
			
			IJ.runMacro("waitForUser(\"Select the regions containing the embryo\");");
			IJ.saveAs(impA, "Selection", dir+dirName +"A_crop.roi");
			IJ.saveAs(impB, "Selection", dir+dirName +"B_crop.roi");

			
			String frameName = ((ListVirtualStack)impA.getStack()).getSliceLabel(impA.getCurrentSlice()).split("_")[0];
			
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed\");");
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed\");");
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch1_processed\\\\"+frameName+"\");");
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMA_Ch2_processed\\\\"+frameName+"\");");
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed\");");
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed\");");
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch1_processed\\\\"+frameName+"\");");
			IJ.runMacro("File.makeDirectory(\""+dir.replace("\\", "\\\\")+"SPIMB_Ch2_processed\\\\"+frameName+"\");");

			
			String deconString = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.1625\", \"res_y double 0.1625\", \"res_z double 1.0\", \"mtxFileDirectory string "+dir.replace("\\", "\\\\")+"SPIMB_processed\", \"spimAFileDir string "+dir.replace("\\", "\\\\")+"SPIMB_processed\", \"spimBFileDir string "+dir.replace("\\", "\\\\")+"SPIMA_processed\", \"baseImage string SPIMA\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconvDirString string "+dir.replace("\\", "\\\\")+"Deconvolution\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\")";
			IJ.saveString(deconString, dir+"GenerateFusion.sct");

			(new File(dir+"GenerateFusion.bat")).delete();
			IJ.runMacro(""
					+ "		    f = File.open(\""+dir.replace("\\", "\\\\")+"GenerateFusion.bat\");\n" + 
					"		    batStringD = \"@echo off\";\n" + 
					"		    print(f,batStringD);\n" + 
					"		    batStringC = \"C\\:\";\n" + 
					"		    print(f,batStringC);\n" + 
					"		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n" + 
					"		    print(f,batStringA);\n" + 
					"		    batStringB = \"start /b mipav -s \\\""+dir.replace("\\", "\\\\")+"GenerateFusion.sct\\\" -hide\";\n" + 
					"		    print(f,batStringB);\n" + 
					"		    File.close(f);	    \n" + 
					"");
		    new MacroRunner("exec(\"cmd\", \"/c\", \""+dir.replace("\\", "\\\\")+"GenerateFusion.bat\")");
		}		


		while (true) {
			boolean focus = false;
			listA = new File(""+dir+"SPIMA").list();
			listB = new File(""+dir+"SPIMB").list();
			big5DFileListAString = IJ.openAsString(dir+"Big5DFileListA.txt");	    
			big5DFileListBString = IJ.openAsString(dir+"Big5DFileListB.txt");

			while (fileListA.length == listA.length || fileListB.length == listB.length ) {
				if (IJ.escapePressed())
					if (!IJ.showMessageWithCancel("Cancel diSPIM Monitor Updates?"
							, "Monitoring of "+ dir+ " paused by Escape.\nClick OK to resume."))
						return;
					else
						IJ.resetEscape();
				listA = new File(""+dir+"SPIMA").list();
				listB = new File(""+dir+"SPIMB").list();
				IJ.wait(5000);
			}	
			//
			//			if (isOpen("Display Channels")) {
			//				selectWindow("Display Channels");
			//				run("Close");
			//			}
			//
			fileListA = new File(""+dir+"SPIMA").list();
			fileListB = new File(""+dir+"SPIMB").list();

			long modDateA = 0;
			String recentestA = "";
			for (int a=0;a<fileListA.length;a++) {
				if(!fileListA[a].endsWith(".roi") && !fileListA[a].endsWith(".DS_Store") ){
					if (modDateA < (new File(dir +"SPIMA" + File.separator + fileListA[a])).lastModified()) {
						modDateA = (new File(dir +"SPIMA" + File.separator + fileListA[a])).lastModified();
						recentestA = dir + "SPIMA" + File.separator + fileListA[a];
					}
				}
			}
			IJ.log(recentestA +"\n"+ modDateA);
			if ((new File(recentestA)).isDirectory()) {
				String[] newTifList = {""};
				while (newTifList.length < wavelengths*zSlices)
					newTifList = (new File(recentestA)).list();
				Arrays.sort(newTifList);		
				for (int f=0;f<newTifList.length;f++) {
					while(!(new File(dir+"Big5DFileListA.txt").exists()))
						IJ.wait(100);
					if (!newTifList[f].endsWith(".roi") && !newTifList[f].endsWith(".DS_Store")
							&& big5DFileListAString.indexOf(recentestA+newTifList[f])<0)
						IJ.append(recentestA + File.separator + newTifList[f], dir+"Big5DFileListA.txt");
				}
			}

			fileListA = new File(""+dir+"SPIMA").list();
			fileListB = new File(""+dir+"SPIMB").list();

			long modDateB = 0;
			String recentestB = "";
			String recentestBname = "";
			for (int a=0;a<fileListB.length;a++) {
				if(!fileListB[a].endsWith(".roi") && !fileListB[a].endsWith(".DS_Store") ){
					if (modDateB < (new File(dir +"SPIMB" + File.separator + fileListB[a])).lastModified()) {
						modDateB = (new File(dir +"SPIMB" + File.separator + fileListB[a])).lastModified();
						recentestB = dir + "SPIMB" + File.separator + fileListB[a];
						recentestBname = fileListB[a];
					}
				}
			}
			IJ.log(recentestB +"\n"+ modDateB);
			if(recentestBname.toLowerCase().startsWith("f"))
				focus = true;
			if ((new File(recentestB)).isDirectory()) {
				String[] newTifList = {""};
				while (newTifList.length < wavelengths*zSlices)
					newTifList = (new File(recentestB)).list();
				Arrays.sort(newTifList);		
				for (int f=0;f<newTifList.length;f++) {
					while(!(new File(dir+"Big5DFileListB.txt").exists()))
						IJ.wait(100);
					if (!newTifList[f].endsWith(".roi") && !newTifList[f].endsWith(".DS_Store")
							&& big5DFileListBString.indexOf(recentestA+newTifList[f])<0)
						IJ.append(recentestB + File.separator + newTifList[f], dir+"Big5DFileListB.txt");
				}
			}
			boolean wasSynched = false;
			ArrayList<ImagePlus> synchedImpsArrayList = new ArrayList<ImagePlus>();
			if (SyncWindows.getInstance()!=null) {
				int v=0;
				while (SyncWindows.getInstance().getImageFromVector(v) != null) {
					wasSynched = true;
					synchedImpsArrayList.add(SyncWindows.getInstance().getImageFromVector(v));
					v++;
				}
				SyncWindows.getInstance().close();
			}
			int cA = impA.getChannel();
			int zA = impA.getSlice();
			int tA = impA.getFrame();
			ListVirtualStack stackA = new ListVirtualStack(dir+"Big5DFileListA.txt");
			int stkNSlicesA = stackA.getSize();
			impA.setStack(stackA, wavelengths, zSlices, stkNSlicesA/(wavelengths*zSlices));
			impA.setPosition(cA, zA, tA==impA.getNFrames()-1?impA.getNFrames():tA);
			impA.setWindow(WindowManager.getCurrentWindow());


			int cB = impB.getChannel();
			int zB = impB.getSlice();
			int tB = impB.getFrame();
			ListVirtualStack stackB = new ListVirtualStack(dir+"Big5DFileListB.txt");
			int stkNSlicesB = stackB.getSize();
			impB.setStack(stackB, wavelengths, zSlices, stkNSlicesB/(wavelengths*zSlices));
			impB.setPosition(cB, zB, tB==impB.getNFrames()-1?impB.getNFrames():tB);
			impB.setWindow(WindowManager.getCurrentWindow());

			if (wasSynched) {
				SyncWindows sw = new SyncWindows();
				for (ImagePlus impS:synchedImpsArrayList) {
					sw.addImp(impS);
				}
			}
			
			
			
			if (focus) {
				//SAD THAT I HAVE TO FAKE THIS, BUT NOT WORKING IN MY ATTEMPTS AT JAVA-ONLY...
				String fftMacroString = 
						"		    dir = \"" +dir.replace("\\", "\\\\")+"\";\n" + 
						"			autoFPath = dir+\"AutoFocusCommaSpace.txt\";" +
						"		    print(nImages);\n" + 
						"		    File.delete(autoFPath);\n" + 
						"			autoFocusString = \"\";\n" + 
						"			for (i=1;i<=nImages;i++){\n" + 
						"				print(nImages+\" \"+i);\n" + 
						"				\n" + 
						"				setBatchMode(true);\n" + 
						"				selectImage(i);\n" + 
						"		\n" + 
						"				source = getTitle();\n" + 
						"				Stack.getDimensions(width, height, channels, zDepth, frames);\n" + 
						"				Stack.getPosition(channel, slice, frame);\n" + 
						"				for (z=0; z<zDepth; z++) { \n" + 
						"					Stack.setSlice(z+1);\n" + 
						"					run(\"FFT, no auto-scaling\");\n" + 
						"					if (z==0) {\n" + 
						"						rename(\"FFTstack\");	\n" + 
						"					} else {\n" + 
						"						run(\"Select All\");\n" + 
						"						run(\"Copy\");\n" + 
						"						close();\n" + 
						"						selectWindow(\"FFTstack\");\n" + 
						"						run(\"Add Slice\");\n" + 
						"						if (z>0)\n" + 
						"							Stack.setSlice(z+2);\n" + 
						"						run(\"Select All\");\n" + 
						"						run(\"Paste\");\n" + 
						"					}\n" + 
						"					selectWindow(source);\n" + 
						"				}\n" + 
						"				selectWindow(\"FFTstack\");\n" + 
						"				makeOval(250, 250, 13, 13);\n" + 
						"				run(\"Clear\", \"stack\");\n" + 
						"				makeOval(220, 220, 73, 73);\n" + 
						"				run(\"Clear Outside\", \"stack\");\n" + 
						"				run(\"Plot Z-axis Profile\");\n" + 
						"				close();\n" + 
						"				selectWindow(\"FFTstack\");\n" + 
						"				close();\n" + 
						"				\n" + 
						"				sliceAvgs = newArray(zDepth);\n" + 
						"				List.clear;\n" + 
						"				for (z=1; z<zDepth; z++) { \n" + 
						"					sliceAvgs[z] = getResult(\"Mean\", z);\n" + 
						"					//print(sliceAvgs[z] );\n" + 
						"					List.set(sliceAvgs[z] , z);\n" + 
						"				}\n" + 
						"				\n" + 
						"				Array.sort(sliceAvgs);\n" + 
						"				print(source+\": Best focus in slice \"+List.get(sliceAvgs[zDepth-1]));\n" + 
						"				autoFocusString = autoFocusString + List.get(sliceAvgs[zDepth-1])+\", \";\n" + 
						"				selectWindow(\"Results\");\n" + 
						"				run(\"Close\");\n" + 
						"				setBatchMode(false);\n" + 
						"				selectWindow(source);\n" + 
						"				Stack.setPosition(channel, slice, frame);\n" + 
						"				updateDisplay();\n" + 
						"			}\n" + 
						"			File.saveString(autoFocusString, autoFPath);			\n" + 
						"";
				IJ.runMacro(fftMacroString);
			}		    
		    
			
			if (doDecon) {
				String deconString = "nibib.spim.PlugInDialogGenerateFusion(\"reg_one boolean false\", \"reg_all boolean true\", \"no_reg_2D boolean false\", \"reg_2D_one boolean false\", \"reg_2D_all boolean false\", \"rotate_begin list_float -10.0,-10.0,-10.0\", \"rotate_end list_float 10.0,10.0,10.0\", \"coarse_rate list_float 3.0,3.0,3.0\", \"fine_rate list_float 0.5,0.5,0.5\", \"save_arithmetic boolean false\", \"show_arithmetic boolean false\", \"save_geometric boolean false\", \"show_geometric boolean false\", \"do_interImages boolean false\", \"save_prefusion boolean false\", \"do_show_pre_fusion boolean false\", \"do_threshold boolean false\", \"save_max_proj boolean false\", \"show_max_proj boolean false\", \"x_max_box_selected boolean false\", \"y_max_box_selected boolean false\", \"z_max_box_selected boolean false\", \"do_smart_movement boolean false\", \"threshold_intensity double 10.0\", \"res_x double 0.1625\", \"res_y double 0.1625\", \"res_z double 1.0\", \"mtxFileDirectory string "+dir.replace("\\", "\\\\")+"SPIMB_processed\", \"spimAFileDir string "+dir.replace("\\", "\\\\")+"SPIMB_processed\", \"spimBFileDir string "+dir.replace("\\", "\\\\")+"SPIMA_processed\", \"baseImage string SPIMA\", \"base_rotation int -1\", \"transform_rotation int 5\", \"concurrent_num int 1\", \"mode_num int 0\", \"save_type string Tiff\", \"do_deconv boolean true\", \"deconvDirString string "+dir.replace("\\", "\\\\")+"Deconvolution\\\", \"deconv_show_results boolean false\", \"deconvolution_method int 1\", \"deconv_iterations int 10\", \"deconv_sigmaA list_float 3.5,3.5,9.6\", \"deconv_sigmaB list_float 9.6,3.5,3.5\", \"use_deconv_sigma_conversion_factor boolean true\", \"x_move int 0\", \"y_move int 0\", \"z_move int 0\")";
				IJ.saveString(deconString, dir+"GenerateFusion.sct");

				(new File(dir+"GenerateFusion.bat")).delete();
				IJ.runMacro(""
						+ "		    f = File.open(\""+dir.replace("\\", "\\\\")+"GenerateFusion.bat\");\n" + 
						"		    batStringD = \"@echo off\";\n" + 
						"		    print(f,batStringD);\n" + 
						"		    batStringC = \"C\\:\";\n" + 
						"		    print(f,batStringC);\n" + 
						"		    batStringA = \"cd C:\\\\Program Files\\\\mipav\";\n" + 
						"		    print(f,batStringA);\n" + 
						"		    batStringB = \"start /b mipav -s \\\""+dir.replace("\\", "\\\\")+"GenerateFusion.sct\\\" -hide\";\n" + 
						"		    print(f,batStringB);\n" + 
						"		    File.close(f);	    \n" + 
						"");
			    new MacroRunner("exec(\"cmd\", \"/c\", \""+dir.replace("\\", "\\\\")+"GenerateFusion.bat\")");
			}		
		}
	}
}
