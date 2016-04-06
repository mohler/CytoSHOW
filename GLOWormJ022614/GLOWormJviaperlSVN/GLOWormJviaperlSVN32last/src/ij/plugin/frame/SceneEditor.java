package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.SocketException;
import java.util.*;
import java.awt.datatransfer.*;																																																																																													

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneLayout;
import javax.swing.event.DocumentListener;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.vcell.gloworm.MultiQTVirtualStack;

import client.RemoteMQTVSHandler;
import ij.*;
import ij.gui.*;
import ij.util.Tools;
import ij.text.*;
import ij.macro.*;
import ij.plugin.MacroInstaller;
import ij.plugin.NewPlugin;
import ij.io.SaveDialog;

/** This is a simple TextArea based editor for editing and compiling plugins. */
public class SceneEditor extends Editor {

	/** ImportPackage statements added in front of scripts. Contains no 
	newlines so that lines numbers in error messages are not changed. */
	public static String JavaScriptIncludes =
			"importPackage(Packages.ij);"+
					"importPackage(Packages.ij.gui);"+
					"importPackage(Packages.ij.process);"+
					"importPackage(Packages.ij.measure);"+
					"importPackage(Packages.ij.util);"+
					"importPackage(Packages.ij.plugin);"+
					"importPackage(Packages.ij.io);"+
					"importPackage(Packages.ij.plugin.filter);"+
					"importPackage(Packages.ij.plugin.frame);"+
					"importPackage(java.lang);"+
					"importPackage(java.awt);"+
					"importPackage(java.awt.image);"+
					"importPackage(java.awt.geom);"+
					"importPackage(java.util);"+
					"importPackage(java.io);"+
					"function print(s) {IJ.log(s);};";

	public static String JS_NOT_FOUND = 
			"JavaScript.jar was not found in the plugins\nfolder. It can be downloaded from:\n \n"+IJ.URL+"/download/tools/JavaScript.jar";
	public static final int MAX_SIZE=28000, XINC=10, YINC=18;
	public static final int MONOSPACED=1, MENU_BAR=2;
	public static final int MACROS_MENU_ITEMS = 8;
	static final String FONT_SIZE = "editor.font.size";
	static final String FONT_MONO= "editor.font.mono";
	static final String CASE_SENSITIVE= "editor.case-sensitive";
	static final String DEFAULT_DIR= "editor.dir";
	private DocumentListener dl;
	private JScrollPane sp;
	private JTextArea ta;
	private String path;
	private boolean changes;
	private static String searchString = "";
	private static boolean caseSensitive = Prefs.get(CASE_SENSITIVE, true);
	private static int lineNumber = 1;
	private static int xoffset, yoffset;
	private static int nWindows;
	private Menu fileMenu, editMenu;
	private Properties p = new Properties();
	private int[] macroStarts;
	private String[] macroNames;
	private MenuBar mb;
	private Menu macrosMenu;
	private int nMacros;
	private Program pgm;
	private int eventCount;
	private String shortcutsInUse;
	private int inUseCount;
	private MacroInstaller installer;
	private static String defaultDir = Prefs.get(DEFAULT_DIR, null);;
	private boolean dontShowWindow;
	private int[] sizes = {9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 36, 48, 60, 72};
	private int fontSize = (int)Prefs.get(FONT_SIZE, 6); // defaults to 16-point
	private CheckboxMenuItem monospaced;
	private static boolean wholeWords;
	private boolean isMacroWindow;
	private int debugStart, debugEnd;
	private static TextWindow debugWindow;
	private boolean step;
	private int previousLine;
	private static SceneEditor instance;
	private int runToLine;
	private boolean fixedLineEndings;

	private JPanel panel;

	public SceneEditor() {
		this(16, 60, 0, MENU_BAR);
	}

	public SceneEditor(int rows, int columns, int fontSize, int options) {
		super();
		setTitle("SceneEditor");
		JButton shareButton = new JButton("Share Scene");
		shareButton.addActionListener(this);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		add(shareButton);
//		JButton suiteButton = new JButton("Share Suite");
//		suiteButton.addActionListener(this);
//		add(suiteButton);
		pack();
		setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		super.actionPerformed(e);
		String what = e.getActionCommand();
		int flags = e.getModifiers();
		boolean altKeyDown = (flags & Event.ALT_MASK)!=0;
		if (IJ.debugMode) IJ.log("actionPerformed: "+e);

		Date currentDate = new Date();
		long msec = currentDate.getTime();			    
		long sec = msec/1000;


		if ("Share Scene".equals(what)) {
			save();
			if (!super.path.endsWith("_scene.scn")) {
				super.path = super.path + ("_"+sec+"_scene.scn");
				save();
			}
			File saveFile = new File(super.path) ;

			FTPClient ftpc = new FTPClient();
			ftpc.setBufferSize(1048576);
			try {
				ftpc.connect("155.37.255.65");
				int reply = ftpc.getReplyCode();

				if(!FTPReply.isPositiveCompletion(reply)) {
					ftpc.disconnect();
					IJ.log("FTP server refused connection.");
				} else {
					ftpc.login("glowormguest", "GLOWorm");
					String[] saveDirFileNames = (new File(saveFile.getParent())).list();
					for (String fileName:saveDirFileNames) {
						if (fileName.matches(".*"+sec+".*")) {
							FileInputStream fis = new FileInputStream(saveFile.getParent() +File.separator +fileName);
							ftpc.setFileType(FTPClient.BINARY_FILE_TYPE);
							ftpc.enterLocalPassiveMode();
							ftpc.storeFile(fileName+".tmp", fis);
							fis.close();
							ftpc.rename(fileName+".tmp", fileName);
						}
					}
					ftpc.logout();
				}
			} catch (SocketException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} finally {
				if(ftpc.isConnected()) {
					try {
						ftpc.disconnect();
					} catch(IOException ioe) {
						// do nothing
					}
				}
			}

			IJ.wait(10000);

			GenericDialog lgd = new GenericDialog("Shared CytoSHOW Scene");
			lgd.addMessage("This scene can be accessed at the web address below.\n" +
					"Please copy, save, and share this link.\n");
			String link = "http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE="
					+saveFile.getName();
			lgd.addStringField("", link, link.length());
			lgd.pack();
			lgd.showDialog();
		}
		if ("Share Suite".equals(what)) {
			IJ.run("Share_Suite");
		}		
	}

}
