package ij.plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.OpenDialog;

import java.io.File;

import org.vcell.gloworm.ListVirtualStack;

public class ListVirtualStackOpener implements PlugIn{
	public void run(String arg) {
		OpenDialog  od = new OpenDialog("Open Image List", arg);
		String name = od.getFileName();
		if (name==null) return;
		String  dir = od.getDirectory();
		//IJ.log("ListVirtualStack: "+dir+"   "+name);
		ImageStack stack = new ListVirtualStack(dir+name);
		ImagePlus imp2 = new ImagePlus(name, stack);
		imp2.show();
	}

}
