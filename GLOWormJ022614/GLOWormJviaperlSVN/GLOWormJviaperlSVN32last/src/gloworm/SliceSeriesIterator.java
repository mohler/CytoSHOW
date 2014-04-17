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

/*...extend StackSeriesIterator for your favorite image format here...*/

abstract class SliceSeriesIterator extends StackSeriesIterator {
    /*StackSeriesIterator for series where each stack is made up of
      multiple files that store one slice each.  Each stack should
      have the same number of slices in it, otherwise the odd stack
      out or (rarely) the series itself may be represented
      incompletely in the output.*/
    protected int digitsInSliceNumbers;
    //description of super.digitsInStackNumbers applies
    
    private class SubIterator extends StackSeriesIterator {
	/*Helps reconstruct a stack by iterating over the files that
          hold its component slices.  Methods dealing w/stacks here
          delegate to methods dealing w/slices in the enclosing
          instance to make implementation simpler.*/
	int whichSeries; //number of what SliceSeriesIterator.this considers a stack

	SubIterator(ImagePlus exampleStack) {
	    super(exampleStack);
	    whichSeries=Integer.parseInt(SliceSeriesIterator.this.stackNumberString(exampleStack.getOriginalFileInfo().fileName),
					 SliceSeriesIterator.this.getStackNumberRadix());
	    getSeriesRange(); //it's safe, this class is effectively final
	    SliceSeriesIterator.this.digitsInSliceNumbers=this.digitsInStackNumbers;
	    //easier to do here than in SliceSeriesIterator's constructor	
	}

	public int reportStackType() {
	    return SliceSeriesIterator.this.reportStackType();
	}

	public ImagePlus openStack(int whichStack) {
	    return SliceSeriesIterator.this.openSlice(whichSeries, whichStack);
	}

	public String stackNumberString(String fileName) {
	    return SliceSeriesIterator.this.sliceNumberString(fileName);
	}

	public String makeFileName(int stackNumber) {
	    //stackNumber=slice # (in the current stack), so:
	    return SliceSeriesIterator.this.makeFileName(whichSeries, stackNumber);
	}

	public int getStackNumberRadix() {
	    if (SliceSeriesIterator.this==null)
		/*...then a superclass constructor is calling to
                  verify the return value.  (Enclosing instances
                  aren't assigned until it returns.)  Tell it what it
                  wants, the verification was done when the future
                  SliceSeriesIterator.this was created:*/
		return 10;
	    else
		return SliceSeriesIterator.this.getSliceNumberRadix();
	}

    }

    SliceSeriesIterator(ImagePlus exampleSlice) {
	super(exampleSlice);
	if (getSliceNumberRadix() < Character.MIN_RADIX || getSliceNumberRadix() > Character.MAX_RADIX)
	    throw new NumberFormatException("Slice number radix " + getSliceNumberRadix() + " out of bounds");
	String exampleStackNumber=stackNumberString(exampleSlice.getOriginalFileInfo().fileName),
	    exampleSliceNumber=sliceNumberString(exampleSlice.getOriginalFileInfo().fileName);
	digitsInStackNumbers=exampleStackNumber.length();
	digitsInSliceNumbers=exampleSliceNumber.length();
	//they'll be set straight by getSeriesRange()--right now openStack() needs to see these values
    }
    //should not leave window open showing return value

    protected int getSliceNumberRadix() {
	/*let subclasses override it if they have to--sometimes slice
          # radix != stack # radix*/
	return super.getStackNumberRadix();
    }
    protected final String makeFileName(int stackNumber) {
	/*Don't call this method!  Use makeFileName(int, int)
	  instead--a filename in the kind of series this iterates over
	  needs to be specified by 2 numbers.  This method only exists
	  so that the unary super.makeFileName() can inspect the file
	  system for information that any slice of the stack could
	  provide.*/
	return makeFileName(stackNumber, Integer.parseInt(sliceNumberString(exampleStack.getOriginalFileInfo().fileName),
							  getSliceNumberRadix()));
	//a typical file of the series
    }
    protected abstract String makeFileName(int stackNumber, int sliceNumber);
    protected abstract ImagePlus openSlice(int whichStack, int whichSlice);
    protected final ImagePlus openStack(int whichStack) {
	ImagePlus fakeSlice=new ImagePlus(),
	    stack=null;
	FileInfo fi=new FileInfo();
	fi.directory=getDirectory();
	fi.fileName=makeFileName(whichStack, Integer.parseInt(sliceNumberString(exampleStack.getOriginalFileInfo().fileName),
							      getSliceNumberRadix()));
	fakeSlice.setFileInfo(fi);
	SubIterator slices=new SubIterator(fakeSlice);
	int nSlices=slices.getIterationEnd() - slices.getIterationStart() + 1,
	    thisSlice=1;
	while (slices.hasMoreElements()) {
	    IJ.showStatus("Building stack (" + (thisSlice++) + "/" + nSlices + ")");
	    if (slices.whichStack()==slices.getIterationStart()) {
		stack=(ImagePlus) slices.nextElement();
		stack.setStack("stack " + whichStack, stack.getStack());
		//workaround for ImagePlus bug that returns defensive copy of 1-slice stack
	    }
	    else {
		ImagePlus currentSlice=(ImagePlus) slices.nextElement();
		stack.getStack().addSlice("", currentSlice.getProcessor());
	    }
	}
	IJ.showStatus("");
	System.gc();
	return stack;
    }
    /*force override--can't assume anything about how the stack # is
      represented in the name of a 1-slice file*/
    protected abstract String sliceNumberString(String fileName);
    protected abstract String stackNumberString(String fileName);
}
