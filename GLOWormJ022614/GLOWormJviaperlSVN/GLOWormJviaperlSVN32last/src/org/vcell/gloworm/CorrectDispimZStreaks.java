package org.vcell.gloworm;

import java.awt.Color;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.filter.MaximumFinder;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class CorrectDispimZStreaks implements PlugIn {
	ImagePlus imp;
	ImageProcessor gaussianDiffIP;
	private MaximumFinder mf;

	public void run(String arg) {
		imp = IJ.getImage();
		ImagePlus gaussianDiffImp = (new ImagePlus(ImageWindow.class.getResource("images/z-x_Mask_ver_-32bkg_x255over408_15x33rect.tif").getPath()));
		gaussianDiffImp.getProcessor().setMinAndMax(0, 255);
		IJ.run(gaussianDiffImp, imp.getBitDepth()+"-bit", "");
		gaussianDiffIP = gaussianDiffImp.getProcessor();
		for (int s=1;s<=imp.getStackSize();s++) {
			imp.setSlice(s);
			ArrayList<String> maxCum = new ArrayList<String>();
			ImageProcessor targetIP = imp.getProcessor().duplicate();
			for (int t=10;t>=1;t--) {
				mf = new MaximumFinder();
				Polygon maxPoly = mf.getMaxima(targetIP, t, false);

				int[] maxXs = new int[maxPoly.npoints];
				int[] maxYs = new int[maxPoly.npoints];

				maxXs = maxPoly.xpoints;
				maxYs = maxPoly.ypoints;

				for (int n=0; n<maxXs.length; n++) {
					if (!maxCum.contains(maxXs[n]+","+maxYs[n])) {
						maxCum.add(maxXs[n]+","+maxYs[n]);
						ImageProcessor modIP = gaussianDiffIP.duplicate();
						modIP.multiply(((double)imp.getProcessor().getPixel(maxXs[n], maxYs[n]))/256);
						imp.getProcessor().copyBits(modIP, maxXs[n]-gaussianDiffIP.getWidth()/2, maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						targetIP.copyBits(modIP, maxXs[n]-gaussianDiffIP.getWidth()/2, maxYs[n]-gaussianDiffIP.getHeight()/2, Blitter.DIFFERENCE);
						Color fgc = Toolbar.getForegroundColor();
						Toolbar.setForegroundColor(Color.BLACK);
						targetIP.fillOval(maxXs[n], maxYs[n], 5, 3);
						Toolbar.setForegroundColor(fgc);

					}
				}
			}
		}
		imp.updateAndDraw();
	}

}
