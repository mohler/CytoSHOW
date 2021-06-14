package org.vcell.gloworm;

import java.io.File;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class ObjEditor implements PlugIn {

	@Override
	public void run(String arg) {
		GenericDialog gd = new GenericDialog("Specify scaling and translations for objs");
		gd.addNumericField("scaleX", 1.0, 0, 0, "");
		gd.addNumericField("scaleY", 1.0, 0, 0, "");
		gd.addNumericField("scaleZ", 1.0, 0, 0, "");
		gd.addNumericField("shiftX", 0.0, 0, 0, "");
		gd.addNumericField("shiftY", 0.0, 0, 0, "");
		gd.addNumericField("shiftZ", 0.0, 0, 0, "");
		gd.showDialog();
		double scaleX = gd.getNextNumber();
		double scaleY = gd.getNextNumber();
		double scaleZ = gd.getNextNumber();
		double shiftX = gd.getNextNumber();
		double shiftY = gd.getNextNumber();
		double shiftZ = gd.getNextNumber();
		
		String objDir = IJ.getDirectory("which folder of objs to edit?");
		String outDir = IJ.getDirectory("Which folder to save edited objs?");
		String[] objDirList = new File(objDir).list();
		for (String objName:objDirList) {
			if (objName.toLowerCase().endsWith(".obj")) {
				String[] objLines = IJ.openAsString(objDir+File.separator+objName).split("\n");
				File outFile = new File(outDir+objName);
				if (outFile.exists())
					outFile.delete();
				for (String objLine:objLines) {
					if (objLine.startsWith("v ")) {
						String[] objVertChunks = objLine.split(" ");
						Double objVertX = Double.parseDouble(objVertChunks[1]);
						Double objVertY = Double.parseDouble(objVertChunks[2]);
						Double objVertZ = Double.parseDouble(objVertChunks[3]);
						String objNewLine = "v " + ((objVertX*scaleX)+shiftX) + " " + ((objVertY*scaleY)+shiftY) + " " + ((objVertZ*scaleZ)+shiftZ);
						IJ.append(objNewLine, outDir+objName);
					} else {
						IJ.append(objLine, outDir+objName);
					}
				}
				//IJ.saveString(objOutPutString, outDir+objName);
			}
		}

	}

}
