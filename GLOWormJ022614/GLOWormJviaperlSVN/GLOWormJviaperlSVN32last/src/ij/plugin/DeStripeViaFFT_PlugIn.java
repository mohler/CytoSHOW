package ij.plugin;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.io.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.awt.image.*;

/** This plugin implements the Help/About ImageJ command by opening
	the about.jpg in ij.jar, scaling it 400% and adding some text. */
	public class DeStripeViaFFT_PlugIn implements PlugIn {
		static final int SMALL_FONT=14, LARGE_FONT=30;

	public void run(String arg) {
		// SAD THAT I HAVE TO FAKE THIS, BUT NOT WORKING IN MY ATTEMPTS
		// AT JAVA-ONLY...
		String fftMacroString = 
				  "	setBatchMode(true);\n"
				+ "	for (i=1;i<=nImages;i++){\n"
				+ "	\n"
				+ "	selectImage(i);\n"
				+ "	\n"
				+ "	source = getTitle();\n"
				+ "	Stack.getDimensions(width, height, channels, zDepth, frames);\n"
				+ "	Stack.getPosition(channel, slice, frame);\n"
				+ "	Stack.setPosition(channel, slice, frames);\n"
				+ "	for (z=0; z<zDepth; z++) { \n"
				+ "	Stack.setSlice(z+1);\n"
				+ "	run(\"FFT, no auto-scaling\");\n"
				+ "	rename(\"FFTfwd\");	\n"
				+ "	selectWindow(source);\n"
				+ "	selectWindow(\"FFTfwd\");\n"
				+ "	makeEllipse(864, 2051, 1980, 2051, 0.04);\n"
				+ "	run(\"Clear\", \"stack\");\n"
				+ "	makeEllipse(2142, 2051, 3258, 2051, 0.04);\n"
				+ "	run(\"Clear\", \"stack\");\n"
				+ "	run(\"Inverse FFT\");\n"
				+ "	selectWindow(\"FFTfwd\");\n"
				+ "	close();\n"
				
				+ "	selectWindow(\"Inverse FFT of FFTfwd\");\n"				
				+ "	rename(\"invFFTslice\");	\n"
				+ "	selectWindow(\"invFFTslice\");\n"
				+ "	run(\"Select All\");\n"
				+ "	run(\"Copy\");\n"
				+ "	close();\n"
				+ "	\n"
				+ "	selectWindow(source);\n"
				+ "	run(\"Select All\");\n"
				+ "	run(\"Paste\");\n"
				+ "	}\n"
				+ "	\n"
				+ "	setBatchMode(false);\n"
				+ "	selectWindow(source);\n"
				+ "	Stack.setPosition(channel, slice, frame);\n"
				+ "	updateDisplay();\n"
				+ "";
		IJ.log(fftMacroString);
		IJ.runMacro(fftMacroString);
	}

	int x(String text, ImageProcessor ip, int max) {
		return ip.getWidth() - max + (max - ip.getStringWidth(text))/2 - 10;
	}

}
