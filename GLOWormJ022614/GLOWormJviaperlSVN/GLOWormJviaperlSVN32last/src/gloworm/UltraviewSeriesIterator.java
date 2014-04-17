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

class UltraviewSeriesIterator extends SliceSeriesIterator {
    /*Opens series of Ultraview stacks.*/
    private final int stackWidth, stackHeight;
    private final FileInfo importRules;    

    UltraviewSeriesIterator(ImagePlus exampleSlice) {
	super(exampleSlice);
	setMaxPixelValue(4095); //12-bit, opened as 16
	FileInfo fi=exampleSlice.getOriginalFileInfo();
	//put description of an Ultraview file in importRules--based on ij.io.ImportDialog's source:
	importRules=new FileInfo();
	importRules.directory=fi.directory;
	stackWidth=importRules.width=fi.width;
	stackHeight=importRules.height=fi.height;
	importRules.intelByteOrder=true;
	importRules.offset=6; //header 2 bytes each for magic #, width & height
	importRules.fileType=FileInfo.GRAY16_UNSIGNED;
	importRules.whiteIsZero=false;
	importRules.fileFormat=FileInfo.RAW;
    }
    protected int getStackNumberRadix() {
	return 16; //but slice # radix is the default, 10
    }
    protected String makeFileName(int whichStack, int whichSlice) {
	return getNameBase() + stringifyToNDigits(whichSlice, digitsInSliceNumbers, getSliceNumberRadix())
	    + '.' + stringifyToNDigits(whichStack, digitsInStackNumbers);
    }
    protected ImagePlus openSlice(int whichStack, int whichSlice) {
	importRules.fileName=makeFileName(whichStack, whichSlice);
	ImagePlus returnValue=new FileOpener(importRules).open(false);
	//Ultraview files store image data bottom-first.  Fix that:
	returnValue.getProcessor().flipVertical();
	return returnValue;
    }
    public int reportStackType() {
	return ULTRAVIEW;
    }
    protected String sliceNumberString(String fileName) {
	//it's the last group of numbers before the extension:
	int lastIndex=fileName.lastIndexOf("."),
	    firstIndex=lastIndex-1;
	while (isDigitInRadix(fileName.charAt(firstIndex), getSliceNumberRadix()))
	    firstIndex--;
	return fileName.substring(firstIndex+1, lastIndex);
    }
    protected String stackNumberString(String fileName) {
	//it's the extension:
	return fileName.substring(fileName.indexOf('.')+1, fileName.length());
    }
}
