package ij;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;

import ij.plugin.*;
import ij.plugin.frame.*;

public class FixMTLnamesInObjs implements PlugIn {

	public void run(String arg) {
		String topDirPath = IJ.getDirectory("Fix Objs recursively in which folder?");
		ArrayList<Path> paths = new ArrayList<Path>();
		try {
			Files.find(Paths.get(topDirPath), Integer.MAX_VALUE,
					(filePath, fileAttr) -> fileAttr.isRegularFile())
				.forEach(paths::add);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (Path path:paths) {
			if (path.toFile().getAbsolutePath().toLowerCase().endsWith(".obj")) {
				IJ.saveString(IJ.openAsString(path.toFile().getAbsolutePath())
						.replaceAll("(mtllib )(.*\\\\)*(.*.mtl)","$1$3"),path.toFile().getAbsolutePath());
				IJ.log(path.toFile().getAbsolutePath());
//				Runtime.getRuntime().gc();
			}
		}
	}

}
