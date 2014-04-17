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

/*-----Subclasses-----*/

class StandardSeriesIterator extends StackSeriesIterator {
    /*Provides open method for stacks in any file format that ImageJ can
      open with an Opener.*/

    StandardSeriesIterator(ImagePlus selectionImage) {
	super(selectionImage);
    }
    protected ImagePlus openStack(int whichStack) {
	ImagePlus returnValue=new Opener().openImage(getDirectory()+makeFileName(whichStack));
	returnValue.hide(); //Opener showing image is unwanted side effect
	return returnValue;
    }
    public int reportStackType() {
	return STANDARD;
    }
}
