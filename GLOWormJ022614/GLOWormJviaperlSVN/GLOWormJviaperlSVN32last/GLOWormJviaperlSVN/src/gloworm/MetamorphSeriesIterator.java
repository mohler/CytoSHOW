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

class MetamorphSeriesIterator extends StandardSeriesIterator {
    /*Overrides getDefaultMaxPixelValue() to return the correct value for
      12-bit images (which Metamorphs are, despite opening as 16-bit).

      Metamorphs have metadata, but this implementation ignores it. */

    MetamorphSeriesIterator(ImagePlus selectionImage) {
	super(selectionImage);
	setMaxPixelValue(4095); //because we're 12-bit but must open as 16
    }
}
