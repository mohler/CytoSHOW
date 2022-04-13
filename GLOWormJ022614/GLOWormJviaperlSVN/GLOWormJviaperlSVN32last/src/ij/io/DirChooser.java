package ij.io;

import java.io.File;

import ij.IJ;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.*;

public class DirChooser extends Application {
	public static Stage staticPrimaryStage;
	public static String args;
	public static boolean choiceMade;

	@Override
	public void start(Stage primaryStage) throws Exception {
		choiceMade = false;
		staticPrimaryStage = primaryStage;
        Platform.setImplicitExit(false);
		javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
		
		String directory = OpenDialog.getDefaultDirectory();
		if (!new File(directory).canRead())
			directory = "";
		String title = DirChooser.args.split(":")[0];
		dc.setTitle(title);
		String defaultDir = DirChooser.args.split(":")[2];
		if (defaultDir == null)
			defaultDir = getParameters().getRaw().get(0);
		if (defaultDir!=null) {
			File f = new File(defaultDir);
			if (IJ.debugMode)
				IJ.log("DirectoryChooser,setSelectedFile: "+f);
			if (f.canRead())
				dc.setInitialDirectory(f);
			
		}
		File dir = dc.showDialog(primaryStage);
		if (dir.canRead()) {
			directory = dir.getAbsolutePath();
			if (!directory.endsWith(File.separator))
				directory += File.separator;
			OpenDialog.setDefaultDirectory(directory);
		}
		IJ.log(directory);
	    choiceMade = true;
	}
}

