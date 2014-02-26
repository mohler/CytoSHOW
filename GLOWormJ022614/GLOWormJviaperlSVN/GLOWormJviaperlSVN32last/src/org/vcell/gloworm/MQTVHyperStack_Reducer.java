package org.vcell.gloworm;
//HyperStackReducer  MODIFIED FROM ORIGINAL VERSION!!!  041009 Bill Mohler

import ij.plugin.*;
import ij.*;
import ij.gui.*;
import ij.process.*;
//import ij.measure.Calibration;
import java.awt.*;
//import java.util.Vector;
import java.lang.Thread;

/** Implements the Image/HyperStacks/Reduce Dimensionality command. */
public class MQTVHyperStack_Reducer extends Thread implements PlugIn, DialogListener {
    ImagePlus imp;
    int channels1, slices1, frames1;
    int channels2, slices2, frames2;
	int mode;
    double imageSize;
    static boolean keep = true;
    
    /** Default constructor */
    public MQTVHyperStack_Reducer() {
    }

    /** Constructs a MQTVHyperStack_Reducer using the specified source image. */
    public MQTVHyperStack_Reducer(ImagePlus imp) {
        this.imp = imp;
    }

    public void run(String arg) {
        imp = IJ.getImage();
        if (!(imp.isHyperStack() || imp.isComposite())) {
            IJ.error("Reducer", "HyperStack required");
            return;
        }
        int width = imp.getWidth();
        int height = imp.getHeight();
        imageSize = width*height*imp.getBytesPerPixel()/(1024.0*1024.0);
        channels1 = channels2 = imp.getNChannels();
        slices1 = slices2 = imp.getNSlices();
        frames1 = frames2 = imp.getNFrames();
        int c1 = imp.getChannel();
        int z1 = imp.getSlice();
        int t1 = imp.getFrame();
		
        if (channels1>1 && imp.isComposite()) {
			mode = ((CompositeImage)imp).getMode();
			((CompositeImage)imp).setMode(3);			//sets display mode to grayscale, which makes the MQTVHyperStack_Reducer macro work well... we'll see if it helps at all here.
		}
		
        if (!showDialog())
            return;
        String title2 = keep?WindowManager.getUniqueName(imp.getTitle()):imp.getTitle();
        int bitDepth = imp.getBitDepth();
        int size = channels2*slices2*frames2;
        ImagePlus imp2 = null;
        if (keep) {
            imp2 = IJ.createImage(title2, bitDepth+"-bit", width, height, size);
            if (imp2==null) return;
        } else {
            ImageStack stack2 = new ImageStack(width, height, size); // create empty stack
            stack2.setPixels(imp.getProcessor().getPixels(), 1); // can't create ImagePlus will null 1st image
            imp2 = new ImagePlus(title2, stack2);
            stack2.setPixels(null, 1);
        }
        imp2.setDimensions(channels2, slices2, frames2);
        reduce(imp2);
        imp2.setOpenAsHyperStack(true);
        if (channels2>1 && imp.isComposite()) {
            imp2 = new CompositeImage(imp2, 0);
            ((CompositeImage)imp2).copyLuts(imp);
        }
        imp2.show();
        if (!keep) {
            imp.changes = false;
            imp.close();
        }
    }

    public void reduce(ImagePlus imp2) {
        int channels = imp2.getNChannels();
        int slices = imp2.getNSlices();
        int frames = imp2.getNFrames();
        int c1 = imp.getChannel();
        int z1 = imp.getSlice();
        int t1 = imp.getFrame();
//		if (IJ.debugMode) IJ.log(c1 +" "+ z1 +" "+ t1 +" "+ channels +" "+ slices +" "+ frames);
		
        int i = 1;						//start value for progress bar.
        int n = channels*slices*frames;
        ImageStack stack = imp.getStack();
        ImageStack stack2 = imp2.getStack();
		
        for (int c=1; c<=channels; c++) {
			int c2 = c;
            if (channels==1) {
				c = c1;
				 c2 = 1;
			}
			
            LUT lut = imp.isComposite()?((CompositeImage)imp).getChannelLut():null;
            imp.setPositionWithoutUpdate(c, 1, 1);
            ImageProcessor ip = imp.getProcessor();
            double min = ip.getMin();
            double max = ip.getMax();
            for (int z=1; z<=slices; z++) {
				int z2 = z;
				if (slices==1) {
					z = z1;
					 z2 = 1;
				}
                for (int t=1; t<=frames; t++) {
                    IJ.showProgress(i++, n);
						int t2 = t;
						if (frames==1) {
							t = t1;
							 t2 = 1;
						}

/*ORIGINAL*        ip = stack.getProcessor(imp.getStackIndex(c, z, t)); */

/*********/			imp.setPositionWithoutUpdate(c, z, t);			// THESE TWO LINES CHANGED FROM ORIGINAL PLUGIN.  THIS CHANGE MAKES IT COMPATIBLE WITH MQTVS
/*********/         ip = imp.getProcessor();						// ALSO SEEMS TO WORK FINE WITH non-virtual HyperStacks
					
//					if (IJ.debugMode) IJ.log(ip + " " + c+ " " + z+ " " + t);
					
					int n2 = imp2.getStackIndex(c2, z2, t2);
                    if (stack2.getPixels(n2)!=null)
                        stack2.getProcessor(n2).insert(ip, 0, 0);
                    else
                        stack2.setPixels(ip.getPixels(), n2);
                }
            }
            if (lut!=null) {
                if (imp2.isComposite())
                    ((CompositeImage)imp2).setChannelLut(lut);
                else
                    imp2.getProcessor().setColorModel(lut);
            }
            imp2.getProcessor().setMinAndMax(min, max);
        }
        if (channels1>1 && imp.isComposite()) 
				((CompositeImage)imp).setMode(mode);
        imp.setPosition(c1, z1, t1);
		
        imp2.resetStack();
        if (channels2>1 && imp2.isComposite()) 
				((CompositeImage)imp).setMode(mode);
       imp2.setPosition(1, 1, 1);
        imp2.setCalibration(imp.getCalibration());
    }

    boolean showDialog() {
        GenericDialog gd = new GenericDialog("Reduce");
            gd.setInsets(10, 20, 5);
        gd.addMessage("Create Image With:");
        gd.setInsets(0, 35, 0);
        if (channels1!=1) gd.addCheckbox(channels1+" channels", true);
        gd.setInsets(0, 35, 0);
        if (slices1!=1) gd.addCheckbox(slices1+" slices", true);
        gd.setInsets(0, 35, 0);
        if (frames1!=1) gd.addCheckbox(frames1+" frames", true);
        gd.setInsets(5, 20, 0);
        gd.addMessage(getNewDimensions()+"      ");
        gd.setInsets(15, 20, 0);
        gd.addCheckbox("Keep Source", keep);
        gd.addDialogListener(this);
        gd.showDialog();
        if (gd.wasCanceled()) return false;
        if (channels1!=1) channels2 = gd.getNextBoolean()?channels1:1;
        if (slices1!=1) slices2 = gd.getNextBoolean()?slices1:1;
        if (frames1!=1) frames2 = gd.getNextBoolean()?frames1:1;
        keep = gd.getNextBoolean();
        return true;
    }

    public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
        if (e==null) return false;
        Object source = e.getSource();
        Checkbox cb = (source instanceof Checkbox)?(Checkbox)source:null;
        if (cb==null) return true;
        String label = cb.getLabel();
        if (label.indexOf("channels")!=-1)
            channels2 = cb.getState()?channels1:1;
        if (label.indexOf("slices")!=-1)
            slices2 = cb.getState()?slices1:1;
        if (label.indexOf("frames")!=-1)
            frames2 = cb.getState()?frames1:1;
        ((Label)gd.getMessage()).setText(getNewDimensions());
        return true;
    }

    String getNewDimensions() {
        String s = channels2+"x"+slices2+"x"+frames2;
        s += " ("+(int)Math.round(imageSize*channels2*slices2*frames2)+"MB)";
        return(s);
    }

}



 
		

 
