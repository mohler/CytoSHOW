package ij.io;

import java.io.File;
import java.util.List;

import ij.IJ;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.*;

public class FileChooser extends Application {
	public static Stage staticPrimaryStage;
	public static String args;
	public static boolean choiceMade;

	@Override
	public void start(Stage primaryStage) throws Exception {
		choiceMade = false;
		staticPrimaryStage = primaryStage;
        Platform.setImplicitExit(false);
		javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
		
		String directory = OpenDialog.getDefaultDirectory();
		if (!new File(directory).canRead())
			directory = "";
		String title = FileChooser.args.split(":")[0];
		fc.setTitle(title);
		String defaultDir = FileChooser.args.split(":")[2];
		if (defaultDir == null)
			defaultDir = getParameters().getRaw().get(0);
		if (defaultDir!=null) {
			File f = new File(defaultDir);
			if (IJ.debugMode)
				IJ.log("DirectoryChooser,setSelectedFile: "+f);
			if (f.canRead())
				fc.setInitialDirectory(f);
			
		}
		List<File> files = fc.showOpenMultipleDialog(primaryStage);
		IJ.log("*****");
		if (files == null) {
			return;
		}
		for (File file:files) {
			if (file.canRead()) {
				IJ.log(file.getAbsolutePath());

				directory = file.getParentFile().getAbsolutePath();
				if (!directory.endsWith(File.separator))
					directory += File.separator;
				OpenDialog.setDefaultDirectory(directory);
			}
		}

	    choiceMade = true;
	}
}

