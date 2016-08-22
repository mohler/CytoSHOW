package org.vcell.gloworm;

import java.awt.Checkbox;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.io.File;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.MultiFileInfoVirtualStack;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.util.Tools;

public class FlattenerOfTags implements PlugIn, TextListener {

	private Checkbox checkbox;
	private int stepT;
	private int lastT;
	private int firstT;
	private int lastZ;
	private int firstZ;
	private int firstC;
	private int lastC;

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		IJ.run("View 100%");
		int nChannels = imp.getNChannels();
		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		GenericDialog gd = new GenericDialog("Flatten Tags into Image");
		gd.setInsets(12, 20, 8);
		if (imp.isHyperStack()) gd.addCheckbox("Duplicate hyperstack", true);
		else gd.addCheckbox("Duplicate stack", true);
		int nRangeFields = 0;
		if (nChannels>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Channels (c):", "1-"+nChannels);
			nRangeFields++;
		}
		if (nSlices>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Slices (z):", "1-"+nSlices);
			nRangeFields++;
		}
		if (nFrames>1) {
			gd.setInsets(2, 30, 3);
			gd.addStringField("Frames (t):", ""+ imp.getFrame() +"-"+ imp.getFrame());
			nRangeFields++;
		}
		Vector v = gd.getStringFields();
		TextField[] rangeFields = new TextField[3];
		for (int i=0; i<nRangeFields; i++) {
			rangeFields[i] = (TextField)v.elementAt(i);
			rangeFields[i].addTextListener(this);
		}
		checkbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		boolean duplicateStack = gd.getNextBoolean();

		if (nChannels>1) {
			String channelRangeString = gd.getNextString();
			//					copyMergedImage = channelRangeString.contains("merge");
			String[] range = Tools.split(channelRangeString, " -");
			//					channelRangeString = channelRangeString.replace("merge","").trim();
			double c1 = Tools.parseDouble(range[0]);
			double c2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			setFirstC(Double.isNaN(c1)?1:(int)c1);
			setLastC(Double.isNaN(c2)?getFirstC():(int)c2);
			if (getFirstC()<1) setFirstC(1);
			if (getLastC()>nChannels) setLastC(nChannels);
			if (getFirstC()>getLastC()) {setFirstC(1); setLastC(nChannels);}
		} else
			setFirstC(setLastC(1));
		if (nSlices>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double z1 = Tools.parseDouble(range[0]);
			double z2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
			setFirstZ(Double.isNaN(z1)?1:(int)z1);
			setLastZ(Double.isNaN(z2)?getFirstZ():(int)z2);
			if (getFirstZ()<1) setFirstZ(1);
			if (getLastZ()>nSlices) setLastZ(nSlices);
			if (getFirstZ()>getLastZ()) {setFirstZ(1); setLastZ(nSlices);}
		} else
			setFirstZ(setLastZ(1));
		if (nFrames>1) {
			String[] range = Tools.split(gd.getNextString(), " -");
			double t1 = Tools.parseDouble(range[0]);
			double t2 = range.length>=2?Tools.parseDouble(range[1]):Double.NaN;
			double t3 = range.length==3?Tools.parseDouble(range[2]):Double.NaN;
			setFirstT(Double.isNaN(t1)?1:(int)t1);
			setLastT(Double.isNaN(t2)?getFirstT():(int)t2);
			setStepT(Double.isNaN(t3)?1:(int)t3);
			if (getFirstT()<1) setFirstT(1);
			if (getLastT()>nFrames) setLastT(nFrames);
			if (getFirstT()>getLastT()) {setFirstT(1); setLastT(nFrames);}
		} else
			setFirstT(setLastT(1));

		String dir = IJ.getDirectory("home")+"CytoSHOWCacheFiles" +File.separator+ imp.getTitle()+"_flat";
		new File(dir).mkdir();
		for (int t=firstT; t<=lastT; t++) {
//			if (new File(dir+File.separator+imp.getTitle()+"_flat_"+IJ.pad(t,6)+".tif").exists())
//				continue;
			ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
			for(int z=firstZ;z<=lastZ;z++) {
				imp.setPositionWithoutUpdate(imp.getChannel(),z,t);						
				IJ.wait(20);
				ImageProcessor ip = imp.flatten().getProcessor();
				stack.addSlice(ip);
				ip = null; 
			}
			ImagePlus imp2 = new ImagePlus(imp.getTitle()+"_flat_"+IJ.pad(t,6), stack);
			IJ.saveAs(imp2,"Tiff", dir+File.separator+imp2.getTitle()+".tif");
			imp2.close();
			stack = null;
			imp2.flush();
		}
		ImagePlus imp3 = new ImagePlus(imp.getTitle()+"_flat", new MultiFileInfoVirtualStack(dir, "", true));
	}

	public void textValueChanged(TextEvent e) {
		checkbox.setState(true);
	}

	public int getFirstC() {
		return firstC;
	}

	public void setFirstC(int firstC) {
		this.firstC = firstC;
	}

	public int getLastC() {
		return lastC;
	}

	public int setLastC(int lastC) {
		this.lastC = lastC;
		return lastC;
	}

	public int getFirstZ() {
		return firstZ;
	}

	public void setFirstZ(int firstZ) {
		this.firstZ = firstZ;
	}

	public int getLastZ() {
		return lastZ;
	}

	public int setLastZ(int lastZ) {
		this.lastZ = lastZ;
		return lastZ;
	}

	public int getFirstT() {
		return firstT;
	}

	public void setFirstT(int firstT) {
		this.firstT = firstT;
	}

	public int getLastT() {
		return lastT;
	}

	public int setLastT(int lastT) {
		this.lastT = lastT;
		return lastT;
	}

	public int getStepT() {
		return stepT;
	}

	public void setStepT(int stepT) {
		this.stepT = stepT;
	}

}
