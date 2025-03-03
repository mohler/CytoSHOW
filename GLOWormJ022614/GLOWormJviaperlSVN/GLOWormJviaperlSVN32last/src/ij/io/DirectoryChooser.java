package ij.io;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.Recorder;
import ij.util.Java2;
import java.awt.*;
import java.io.*;
import javax.swing.*;
import javax.swing.filechooser.*;

/** This class displays a dialog box that allows the user can select a directory. */ 
 public class DirectoryChooser {
 	private String directory;
 	private String title;
 
 	/** Display a dialog using the specified title. */
 	public DirectoryChooser(String title) {
 		this.title = title;
		if (IJ.isMacOSX())
			getDirectoryUsingFileDialog(title);
 		else {
			String macroOptions = Macro.getOptions();
			if (macroOptions!=null)
				directory = Macro.getValue(macroOptions, title, null);
			if (directory==null) {
 				if (EventQueue.isDispatchThread())
 					getDirectoryUsingJFileChooserOnThisThread(title);
 				else
 					getDirectoryUsingJFileChooser(title);
 			}
 		}
 	}
 	
	// runs JFileChooser on event dispatch thread to avoid possible thread deadlocks
 	void getDirectoryUsingJFileChooser(final String title) {
		Java2.setSystemLookAndFeel();
		try {
			EventQueue.invokeAndWait(new Runnable() {
				public void run() {
					JFileChooser chooser = new JFileChooser();
					chooser.setFileHidingEnabled(false);
					chooser.setDialogTitle(title);
					chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					String defaultDir = OpenDialog.getDefaultDirectory();
					if (defaultDir!=null) {
						File f = new File(defaultDir);
						if (IJ.debugMode)
							IJ.log("DirectoryChooser,setSelectedFile: "+f);
						chooser.setSelectedFile(f);
					}
					chooser.setApproveButtonText("Select");
					if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
						File file = chooser.getSelectedFile();
						directory = file.getAbsolutePath();
						if (!directory.endsWith(File.separator))
							directory += File.separator;
						OpenDialog.setDefaultDirectory(directory);
					}
				}
			});
		} catch (Exception e) {}
	}
 
	// Choose a directory using JFileChooser on the current thread
 	void getDirectoryUsingJFileChooserOnThisThread(final String title) {
		Java2.setSystemLookAndFeel();
		try {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileHidingEnabled(false);
			chooser.setDialogTitle(title);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			String defaultDir = OpenDialog.getDefaultDirectory();
			if (defaultDir!=null) {
				File f = new File(defaultDir);
				if (IJ.debugMode)
					IJ.log("DirectoryChooser,setSelectedFile: "+f);
				chooser.setSelectedFile(f);
			}
			chooser.setApproveButtonText("Select");
			if (chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {
				File file = chooser.getSelectedFile();
				directory = file.getAbsolutePath();
				if (!directory.endsWith(File.separator))
					directory += File.separator;
				OpenDialog.setDefaultDirectory(directory);
			}
		} catch (Exception e) {}
	}

 	// On Mac OS X, we can select directories using the native file open dialog
 	void getDirectoryUsingFileDialog(String title) {
 		boolean saveUseJFC = Prefs.useJFileChooser;
 		Prefs.useJFileChooser = false;
		System.setProperty("apple.awt.fileDialogForDirectories", "true");
		String dir=null, name=null;
		String defaultDir = OpenDialog.getDefaultDirectory();
		if (defaultDir!=null) {
			File f = new File(defaultDir);
			dir = f.getParent();
			name = f.getName();
		}
		if (IJ.debugMode)
			IJ.log("DirectoryChooser: dir=\""+dir+"\",  file=\""+name+"\"");
		OpenDialog od = new OpenDialog(title, dir, name);
		if (od.getDirectory()==null)
			directory = null;
		else
			directory = od.getDirectory() + od.getFileName() + "/";
		if (directory!=null)
			OpenDialog.setDefaultDirectory(directory);
		System.setProperty("apple.awt.fileDialogForDirectories", "false");
 		Prefs.useJFileChooser = saveUseJFC;
	}

 	/** Returns the directory selected by the user. */
 	public String getDirectory() {
		if (IJ.debugMode)
			IJ.log("DirectoryChooser.getDirectory: "+directory);
		if (Recorder.record && !IJ.isMacOSX())
			Recorder.recordPath(title, directory);
 		return directory;
 	}
 	
    /** Sets the default directory presented in the dialog. */
    public static void setDefaultDirectory(String dir) {
    	if (dir==null || (new File(dir)).isDirectory())
			OpenDialog.setDefaultDirectory(dir);
    }

	//private void setSystemLookAndFeel() {
	//	try {
	//		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
	//	} catch(Throwable t) {}
	//}

}
