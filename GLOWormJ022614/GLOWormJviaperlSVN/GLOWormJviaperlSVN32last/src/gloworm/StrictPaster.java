package gloworm;

import java.lang.reflect.Array;
import java.util.Arrays;
import ij.process.ImageProcessor;
import ij.gui.*;
import ij.IJ;
import java.awt.Polygon;
import java.awt.Rectangle;

public class StrictPaster {
    /*Static utility class providing a copy method and a paste method
      that, unlike ImageJ 1.29's ImageWindow.paste() method or menu
      commands that call it, always pastes exactly those pixels inside
      the ROI mask +/- 1 pixel when called during plugin execution.
      (Does not support paste modes other than straight replacement of
      target's pixels with pasted ones.)
      
      This class maintains its own clipboard rather than using
      ImageJ's.  Regions of image copied with an ImageWindow method or
      Edit menu command cannot be pasted with a StrictPaster method,
      and vice versa.

      Note: StrictPaster methods are less efficient than
      ImageWindow methods, Blitter methods, and other wheels
      reinvented here.  Use this class only in code where ImageJ's own
      pasting capabilities have already been tried and found to act
      erratically.*/

//      private static boolean strictMode=false;
    //use strictPaste() instead of ImageWindow.paste()?
    private static Object src;
    //pixel array
//      private static PolygonRoi roi;
    private static Roi roi;

    static {
	IJ.register(StrictPaster.class);
    }

    private StrictPaster() {}
    private static boolean arentCongruent(Roi roi1, Roi roi2) {
	/*Returns true if roi1 and roi2 are of different classes or
          noncongruent geometrically.  Automatically true if either
          is null.*/
	//**/IJ.write((roi1 == null) + " " + (roi2 == null));
	Rectangle rect1, rect2;
	return (((roi1 == null) || (roi2 == null)) ||
		!roi1.getClass().equals(roi2.getClass()) ||
		((roi1 instanceof PolygonRoi)?
		!(Arrays.equals(((PolygonRoi) roi1).getXCoordinates(), ((PolygonRoi) roi2).getXCoordinates()) &&
		  Arrays.equals(((PolygonRoi) roi1).getYCoordinates(), ((PolygonRoi) roi2).getYCoordinates()))
		 : //roi's shape is determined by its bounding rectangle
		!((rect1=roi1.getBoundingRect()).width == (rect2=roi2.getBoundingRect()).width &&
		  rect1.height == rect2.height)));
    }
    public static synchronized void copyFrom(ImageWindow w) {
	/*Use this before using pasteTo()--data from ImageJ's
          clipboard is not recognized.*/
	ImageProcessor ip;
	ij.ImagePlus imp;
//  	Roi roi;
	Rectangle rect;
	synchronized(imp=w.getImagePlus()) {
	    roi=imp.getRoi();
	    if (roi == null || roi.getType()==Roi.LINE)
		imp.setRoi((roi=new Roi(0, 0, imp.getWidth(), imp.getHeight())));
//  	    if (!(strictMode=(roi != null && roi instanceof PolygonRoi))) {
//  		/*rectangle ROIs seem to copy well enough by usual
//                    methods and I don't feel like supporting oval ROIs
//                    just now*/
//  		w.copy(false);
//  		return;
//  	    }
	    ip=imp.getProcessor();
	}
//  	StrictPaster.roi=(PolygonRoi) roi;
	synchronized(roi) {
	    rect=roi.getBoundingRect();
	}
	synchronized(ip) {
	    Object ipPixels=ip.getPixels();
	    src=Array.newInstance(ipPixels.getClass().getComponentType(),
				  rect.width * rect.height);
	    for (int index=0; index < rect.height; index++)
		System.arraycopy(ipPixels, (rect.y + index)*ip.getWidth() + rect.x,
				 src, index*rect.width, rect.width);
	}
    }
    public static synchronized void pasteTo(ImageWindow w) {
	/*Based on code from ImageJ version 1.29's ImageWindow.paste()
          and PolygonRoi.contains().*/
	ImageProcessor ip;
	Roi roi;
	Rectangle rect;
//  	Polygon pastingArea;
	java.awt.Shape pastingArea;
	boolean wNeedsRoi;

//  	if (!strictMode) {
//  	    w.paste();
//  	    return;
//  	}
	synchronized(w.getImagePlus()) {
	    roi=w.getImagePlus().getRoi();
	    ip=w.getImagePlus().getProcessor();
	    wNeedsRoi=arentCongruent(roi, StrictPaster.roi);
	    if (wNeedsRoi) {
		/*do what ImageWindow.paste() does--replace the
		  window's ROI with a centered ROI same shape as that
		  from the copyFrom window:*/
		roi=(Roi) StrictPaster.roi.clone();
		rect=roi.getBoundingRect();
		roi.setLocation((ip.getWidth()-rect.width)/2,
				(ip.getHeight()-rect.height)/2);
	    }
	    synchronized(roi) {
		rect=roi.getBoundingRect();
		if (roi instanceof PolygonRoi) {
		    pastingArea=new Polygon(((PolygonRoi) roi).getXCoordinates(),
					    ((PolygonRoi) roi).getYCoordinates(),
					    ((PolygonRoi) roi).getNCoordinates());
		    ((Polygon) pastingArea).translate(rect.x, rect.y);
		    //coordinates from roi were relative
		}
		else if (roi instanceof OvalRoi)
		    pastingArea=new java.awt.geom.Ellipse2D.Float((float) rect.x, (float) rect.y, (float) rect.width, (float) rect.height);
		else //it's a rectangular roi
		    pastingArea=rect;
		/*pastingArea != the ROI-masked area because the ROI's
                  mask-generating method violates the Shape contract
                  re what pixels are "inside"--this limits pasting
                  strictness to +/- 1 pixel*/
		w.getImagePlus().setRoi(roi);
	    }
	}
	synchronized(ip) {
	    Object dest=ip.getPixels();
	    for (int y=rect.y, pixelCounter=0; y < rect.y + rect.height; y++)
		for (int x=rect.x; x < rect.x + rect.width; x++, pixelCounter++)
		    if (pastingArea.contains(x, y))
			//roi.contains(x, y) gives weird results
			Array.set(dest, y*ip.getWidth() + x,
				  Array.get(src, pixelCounter));
	    //not efficient--but general
	}
	w.getImagePlus().setProcessor(null, ip);
	w.getImagePlus().updateAndDraw();
    }
}
