package gloworm;

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.awt.image.ImageObserver;
import ij.process.*;

class LsmSeriesIterator extends StackSeriesIterator implements StackSeriesIterator.ZoomCorrectable {
    /*Provides open method for Zeiss LSM stacks.*/
    LSM_Reader.LSM_Reader_ innerReader;
    String[] headerInfo;
    int apparentZoom; //zoom value used to derive sliceSpacing

    LsmSeriesIterator(ImagePlus selectionImage) {
	super(selectionImage);
	innerReader=new LSM_Reader().new LSM_Reader_();
	innerReader.OpenLSM(
			    selectionImage.getOriginalFileInfo().directory+
			    selectionImage.getOriginalFileInfo().fileName);
	//open the stack properly
	headerInfo=innerReader.printINFO();
  	apparentZoom=extractZoom(headerInfo[14]);
	zVoxelInMicrons=Double.parseDouble(headerInfo[13].substring(0, headerInfo[13].length()-3));
	//VOXELSIZE_Z in CZPRIVATE tag, converted to microns
	sliceSpacing=zVoxelInMicrons /
	    Double.parseDouble(headerInfo[11].substring(0, headerInfo[11].length()-3)); //VOXELSIZE_X in CZPRIVATE tag
	//VOXELSIZE_Z is slice spacing in SI units--we want pixels--so use fact VOXELSIZE_X is 1 pixel to convert
    }
    public void correctZoom(int actualZoom) {
	sliceSpacing *= (((double) actualZoom)/((double) apparentZoom));
	/*Derivation: xVoxelSize is inversely proportional to zoom,
    because a voxel is a fixed fraction of the image and increasing
    magnification makes the area covered by the image smaller.
    zVoxelSize is unaffected by zoom.  That means sliceSpacing, or
    zVoxelSize/xVoxelSize, is directly proportional to zoom.  As
    derived from the image file this value reflects apparentZoom, so
    to make it reflect actualZoom we have to divide out apparentZoom
    and multiply by actualZoom.*/
	apparentZoom=actualZoom;
    }
    private int extractZoom(String objectiveLabel) throws RuntimeException {
	/*Given the name of an objective lens, reads the zoom factor
          stated in it.*/
	int indexBeforeStart, indexAfterEnd=0;
	try {
	    do {//find the 'x' in a zoom factor written as (a number)x
		indexAfterEnd=objectiveLabel.indexOf('x', indexAfterEnd+1);
		if (indexAfterEnd < 0)
		    indexAfterEnd=objectiveLabel.indexOf('X', indexAfterEnd+1);
	    } while (!Character.isDigit(objectiveLabel.charAt(indexAfterEnd-1)));
	    indexBeforeStart=indexAfterEnd-1;
	    while (Character.isDigit(objectiveLabel.charAt(--indexBeforeStart)));
	} catch (IndexOutOfBoundsException e) {
	    throw new RuntimeException("Can't find zoom data in this file, try another.");
	    //maybe actually try that other here, in some later implementation?
	}
	return Integer.parseInt(objectiveLabel.substring(indexBeforeStart+1, indexAfterEnd));
    }
    private String getTransImageFileName(int n) {
	/*Returns the name of the image file that, if it exists, will
          contain the transmitted-light image corresponding to the nth
          stack.*/
	StringBuffer fileName=new StringBuffer(makeFileName(n));
	fileName.setCharAt(getNameBase().length()-3, '2');
	/*LSM naming system rule to derive name of transmitted-light
          image from stack name*/
	return getDirectory()+new String(fileName);
    }
    protected ImagePlus openStack(int whichStack) {
	innerReader.OpenLSM(getDirectory()+makeFileName(whichStack));
	//see LSM_Reader_ source
	ImagePlus returnValue=IJ.getImage();
	/*OpenLSM() doesn't return this ImagePlus, so use the
	  fact it's now on top to make ImageJ do it.*/
	returnValue.hide();
	return returnValue;
    }
    public int reportStackType() {
	return ZEISS_LSM;
    }
    private ImagePlus stackTransImages() {
	/*Displays and returns a stack made of the transmitted-light
          images that are sometimes taken along with LSM stacks of the
          stacks' subject.  Only images that correspond to stacks in
          iteration range are used.

	Since the presence of a stack in the iteration range does not
	  guarantee the existence of its transmitted-light
	  counterpart, any missing image is represented by a blank
	  slice in the return stack.  If all images are missing,
	  however, this method returns null.*/
	ImageStack transImageStack=null;
	ImagePlus returnValue;
	boolean someTransImagesExist=false; //for all we know yet
	int iterationStart=getIterationStart(), iterationEnd=getIterationEnd();
	ImageProcessor blank;
	Class ipType;
	String[] transImageFileNames=new String[iterationEnd - iterationStart + 1];
	int width, height;
	IJ.showStatus("Stacking transmitted-light images...");
	for (int index=iterationStart; index <= iterationEnd; index++) {
	    String fileName=getTransImageFileName(index);
	    boolean thisTransImageExists=new File(fileName).exists();
	    //list transmitted-light images that exist, with nulls to hold place of those that don't:
	    transImageFileNames[index - iterationStart] = thisTransImageExists? fileName: null;
	    someTransImagesExist |= thisTransImageExists;
	}
	if (!someTransImagesExist)
	    return null;
	for (int index=iterationStart; index <= iterationEnd; index++) {
	    ImageWindow transWindow;
	    String transImageFileName=transImageFileNames[index - iterationStart];
	    if (transImageFileName != null) {
		innerReader.OpenLSM(transImageFileName);
		transWindow=WindowManager.getCurrentWindow();
		if (index==iterationStart) //initialize output stack
		    transImageStack=new ImageStack(IJ.getImage().getWidth(), IJ.getImage().getHeight());
		transImageStack.addSlice("", transWindow.getImagePlus().getProcessor());
		transWindow.dispose();
		WindowManager.removeWindow(transWindow);
	    }
	    //else fill in the blanks after finished w/real images
	    System.gc();
	}
	ipType=transImageStack.getPixels(1).getClass(); //presumably same for other slices
	width=transImageStack.getWidth(); height=transImageStack.getHeight();
	if (ipType==byte[].class)
	    blank=new ByteProcessor(width, height);
	else if (ipType==short[].class)
	    blank=new ShortProcessor(width, height);
	else if (ipType==int[].class)
	    blank=new ColorProcessor(width, height);
	else
	    blank=new FloatProcessor(width, height);
	for (int index = 0; index < transImageFileNames.length; index++)
	    if (transImageFileNames[index]==null)
		transImageStack.addSlice("", blank, index);
	/*result: blank inserted in stack wherever null was in names
	  array (taking into account zero-based array vs. 1-based
	  stack)*/
	(returnValue=new ImagePlus("", transImageStack)).show();
	return returnValue;
    }
    public ImagePlus summarize() {
	ImagePlus summary=super.summarize();
	StackWindow summaryWindow;
	ImagePlus part2=summary, part1=stackTransImages();
	ImageStack summaryStack;
	if (part1 != null) {
	    /*combine part1 and part2 side-by-side into image:
	      part1 for better visibility, part2 to better show
	      what LUT of output will look like*/
	    Roi roi1=new Roi(0, 0, part1.getWidth(), part1.getHeight());
	    Roi roi2=new Roi(part1.getWidth(), 0, part2.getWidth(), part2.getHeight());
	    IJ.showStatus("Combining stacks...");
  	    summaryStack=new ImageStack(part1.getWidth() + part2.getWidth(), Math.max(part1.getHeight(), part2.getHeight()));
//    	    summaryStack.addSlice("", part2.getProcessor().createProcessor(summaryStack.getWidth(), summaryStack.getHeight()));
//    	    //make it displayable--otherwise attempt to make ImagePlus w/it for pasting into will fail
//  	    summary=new ImagePlus("", summaryStack);
//  	    (summaryWindow=new StackWindow(summary=new ImagePlus("", summaryStack))).show();
//  	    (summaryWindow=new StackWindow(summary=new ImagePlus("", part2.getProcessor().createProcessor(part1.getWidth() + part2.getWidth(),
//  													  Math.max(part1.getHeight(), part2.getHeight()))))).show();
//  	    summary.setStack("", summary.getStack());
	    for (int index=1; index <= part1.getStackSize(); index++) {
		ImageWindow mergingWindow=new ImageWindow(new ImagePlus("",
									part2.getProcessor().createProcessor(summaryStack.getWidth(),
													     summaryStack.getHeight())));
		IJ.showProgress(index / part1.getStackSize());
//  		summary.setSlice(index);
		part1.setSlice(index);
		part2.setSlice(index);
		StrictPaster.copyFrom(part1.getWindow());
		mergingWindow.getImagePlus().setRoi(roi1);
		StrictPaster.pasteTo(mergingWindow);
		StrictPaster.copyFrom(part2.getWindow());
		mergingWindow.getImagePlus().setRoi(roi2);
		StrictPaster.pasteTo(mergingWindow);
		summaryStack.addSlice("", mergingWindow.getImagePlus().getProcessor());
		mergingWindow.dispose();
		System.gc();
	    }
	    part1.hide();
	    part1.flush();
	    part2.hide();
	    part2.flush();
	    summary.setStack("", summaryStack);
	    new StackWindow(summary).show();
	}
	return summary;
    }
}
