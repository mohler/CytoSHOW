package org.vcell.gloworm;

import java.awt.Button;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.measure.Calibration;
import ij.plugin.FileInfoVirtualStack;
import ij.plugin.ListVirtualStack;
import ij.plugin.PlugIn;
import ij.plugin.frame.SyncWindows;

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
		    for (int a=0;a<fileListB.length;a++) {
		    	if(!fileListB[a].endsWith(".roi") && !fileListB[a].endsWith(".DS_Store") ){
		    		if (modDateB < (new File(dir +"SPIMB" + File.separator + fileListB[a])).lastModified()) {
		    			modDateB = (new File(dir +"SPIMB" + File.separator + fileListB[a])).lastModified();
		    			recentestB = dir + "SPIMB" + File.separator + fileListB[a];
		    		}
		    	}
		    }
		    IJ.log(recentestB +"\n"+ modDateB);
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
		}
	}
}
