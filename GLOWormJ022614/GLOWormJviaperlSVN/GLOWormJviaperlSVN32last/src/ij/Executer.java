package ij;
import ij.util.Tools;
import ij.text.TextWindow;
import ij.plugin.DragAndDrop;
import ij.plugin.MacroInstaller;
import ij.plugin.frame.Editor;
import ij.plugin.frame.Recorder;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.io.OpenDialog;

import java.io.*;
import java.util.*;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.Choice;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;

import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.MultiChannelController;
import org.vcell.gloworm.QTMovieOpenerMultiMod;

import client.RemoteMQTVSHandler;


/** Runs ImageJ menu commands in a separate thread.*/
public class Executer implements Runnable {

	private static String previousCommand;
	private static CommandListener listener;
	private static Vector listeners = new Vector();
	private ImageJ ij;

	private String command;
	private Thread thread;

	/** Create an Executer to run the specified menu command
		in this thread using the active image. */
	public Executer(String cmd) {
		command = cmd;
	}

	/** Create an Executer that runs the specified menu 
		command in a separate thread using the specified image,
		or using the active image if 'imp' is null. */
	public Executer(String cmd, ImagePlus imp, ActionEvent ae) {
		boolean jumpToCytoSHOW = IJ.shiftKeyDown();
		boolean jumpToWormAtlas = IJ.controlKeyDown() || IJ.altKeyDown();
		if (cmd.startsWith("near ") || cmd.startsWith("also see: "))
			cmd = cmd.substring(cmd.indexOf("\""));
		if (cmd.startsWith("Repeat")) {
			command = previousCommand;
			IJ.setKeyUp(KeyEvent.VK_SHIFT);	
		} else if (cmd.startsWith("http://")){
			IJ.runPlugIn("ij.plugin.BrowserLauncher", cmd);
		} else if (cmd.startsWith("\"")){
			//			IJ.log(cmd);
			String[] cmdChunks = cmd.split(": ");
			IJ.log(cmdChunks[cmdChunks.length-1]);
			ImagePlus cmdImp = null;
			if (cmdChunks[cmdChunks.length-1].trim().contains("synch all windows to this tag") || jumpToCytoSHOW) {
				int[] impIDs = WindowManager.getIDList();
				for (int e=0; e<impIDs.length; e++) {
					cmdImp = WindowManager.getImage(impIDs[e]);

					Object[] targetItems = cmdImp.getRoiManager().getListModel().toArray();

					for (int i=0; i<targetItems.length; i++) {
						if ( ((String) targetItems[i]).startsWith(cmdChunks[0].split(" ")[0] +" ") ) {
							//							WindowManager.setWindow(cmdImp.getWindow());
							new ij.macro.MacroRunner("roiManager('select', "+i+", "+cmdImp.getID()+");" +
									"selectImage("+cmdImp.getID()+");" +
									"getSelectionBounds(roix, roiy, roiwidth, roiheight);" +
									"run(\"Set... \", \"zoom=\" + getZoom*100 + \" x=\" + roix+roiwidth/2 + \" y=\" + roiy+roiheight/2);" +
//									"roiManager('select', "+i+", "+cmdImp.getID()+");" +
									//									"print(\"Zoom Complete\");"+
									"");
							//							IJ.wait(200);
							i = targetItems.length;
						}
					}
				}
			} else {
				cmdImp = ((ImageWindow) WindowManager.getFrame(cmdChunks[cmdChunks.length-1])).getImagePlus();

				Object[] targetItems =  cmdImp.getRoiManager().getListModel().toArray();
				for (int i=0; i<targetItems.length; i++) {
					if (((String) targetItems[i]).startsWith(cmdChunks[0]) ) {
						new ij.macro.MacroRunner("roiManager('select', "+i+", "+cmdImp.getID()+");" +
								"getSelectionBounds(roix, roiy, roiwidth, roiheight);" +
								"run(\"Set... \", \"zoom=\" + getZoom*100 + \" x=\" + roix+roiwidth/2 + \" y=\" + roiy+roiheight/2);" +
								"");
						cmdImp.getWindow().toFront();
						i = targetItems.length;
					}
				}
			}
		}else if (cmd.startsWith("is also expressed in")){
			//			IJ.showMessage("!"+cmd+"!");
			String cellPage = ("http://www.wormbase.org/db/get?name="+cmd.split(" ")[4]+";class=Anatomy_term");
			IJ.runPlugIn("ij.plugin.BrowserLauncher", cellPage );

		}else if (cmd.startsWith("expresses")){
			String genePage = ("http://www.wormbase.org/db/get?name="+cmd.split(" ")[1]+";class=gene");
			//			String genePage = ("http://legacy.wormbase.org/db/gene/gene?name="+cmd.split(" ")[1]+";class=Gene");
			if (jumpToCytoSHOW && !jumpToWormAtlas) {
				genePage = ("http://www.gloworm.org/p/genes.html");
				IJ.runPlugIn("ij.plugin.BrowserLauncher", genePage );
			} else if(jumpToCytoSHOW && jumpToWormAtlas) {
				ij = IJ.getInstance();
				String oldLog = IJ.getLog();
				IJ.log("\\Clear");
				IJ.runMacro(""
						+ "string = File.openUrlAsString(\"http://www.wormbase.org/db/get?name="
						+ cmd.split(" ")[1]
								+ ";class=gene\");"
								+ "print(string);");							
				String[] logLines2 = IJ.getLog().split("wname=\"expression\"");
				IJ.log("\\Clear");
				IJ.log(oldLog);
				//				IJ.showMessage(cellName+IJ.getLog());
				String restString = logLines2[1].split("\"")[1];

				IJ.log("\\Clear");
				IJ.runMacro(""
						+ "string = File.openUrlAsString(\"http://www.wormbase.org"
						+ restString
						+ "\");"

						+ "cells = split(string, \"><\");"
						//						+ "print(\""+cmd.split(" ")[1]+" expressed in:\");"
						+ "for (i=0; i<lengthOf(cells); i++) {"
						+ "	if (startsWith(cells[i], \"a href=\\\"/species/c_elegans/anatomy_term/\") ) "
						+ "		print(cells[i+1]);"
						+ "}");
				//				popup.add(new MenuItem("-"));
				logLines2 = IJ.getLog().toLowerCase().split("\n");
				IJ.log("\\Clear");
				IJ.log(oldLog);
				Arrays.sort(logLines2);
				PopupMenu expressionPopup = new PopupMenu("*"+((MenuItem)ae.getSource()).getLabel());
				MenuItem mi = (MenuItem)ae.getSource();
				PopupMenu motherPopup = (PopupMenu) mi.getParent();
				int miIndex =0;
				for (int m=0; m<motherPopup.getItemCount(); m++) {
					if (motherPopup.getItem(m) == mi) {
						miIndex = m;
					}
				}
				motherPopup.remove(miIndex);
				motherPopup.insert(expressionPopup, miIndex);
				expressionPopup.add(cmd.split(" ")[1]+":");
				for (String s:logLines2) {
					if (s.trim() != "") {
						MenuItem mi2 = new MenuItem("is also expressed in "+s);
						mi2.addActionListener(ij);
						expressionPopup.add(mi2);
					}
				}
				jumpToWormAtlas = false;
				jumpToCytoSHOW = false;
				imp.getCanvas().getPopup().show(imp.getCanvas(), imp.getCanvas().getMousePressedX(), imp.getCanvas().getMousePressedY());
			} else {
				IJ.runPlugIn("ij.plugin.BrowserLauncher", genePage );
			}

		}else if (cmd.startsWith("***shown here")){
			String[] cmdChunks = cmd.split(": ");
			IJ.log(cmdChunks[cmdChunks.length-1]);
			ImagePlus cmdImp = null;
			if ( jumpToCytoSHOW) {
				int[] impIDs = WindowManager.getIDList();
				for (int e=0; e<impIDs.length; e++) {
					cmdImp = WindowManager.getImage(impIDs[e]);

					Object[] targetItems =  cmdImp.getRoiManager().getListModel().toArray();

					for (int i=0; i<targetItems.length; i++) {
						if (((String) targetItems[i]).startsWith(cmdChunks[1].split(" ")[0] +" ") ) {
							//							WindowManager.setWindow(cmdImp.getWindow());
							new ij.macro.MacroRunner("roiManager('select', "+i+", "+cmdImp.getID()+");" +
									"selectImage("+cmdImp.getID()+");" +
									"getSelectionBounds(roix, roiy, roiwidth, roiheight);" +
									"run(\"Set... \", \"zoom=\" + getZoom*100 + \" x=\" + roix+roiwidth/2 + \" y=\" + roiy+roiheight/2);" +
//									"roiManager('select', "+i+", "+cmdImp.getID()+");" +
									//									"print(\"Zoom Complete\");"+
									"");
							//							IJ.wait(200);
							i = targetItems.length;
						}
					}
				}
			} else {
				int windowID = Integer.parseInt(cmd.split("\"")[cmd.split("\"").length-1].split("[{|}]")[1]);
				WindowManager.setCurrentWindow(WindowManager.getImage(windowID).getWindow());
				WindowManager.getImage(windowID).getWindow().toFront();
				new ij.macro.MacroRunner("roiManager('select', "+cmd.split("\"")[cmd.split("\"").length-1].split("[{|}]")[2]+", "+cmd.split("\"")[cmd.split("\"").length-1].split("[{|}]")[1]+");" +
						"getSelectionBounds(roix, roiy, roiwidth, roiheight);" +
						"run(\"Set... \", \"zoom=\" + getZoom*100 + \" x=\" + roix+roiwidth/2 + \" y=\" + roiy+roiheight/2);" +
						"");
			}
		}else if (cmd.trim().startsWith("begets=>") || cmd.trim().startsWith("cellID=>")){
			//			String cellPage = "http://legacy.wormbase.org/db/ontology/anatomy?name="+cmd.trim().substring(8,cmd.trim().indexOf(" "))+";open=show_Expr_pattern";
			String cellPage = "http://www.wormbase.org/db/get?name="+cmd.trim().substring(8,cmd.trim().indexOf(" "))+";class=Anatomy_term";
			if (jumpToWormAtlas) cellPage = ("http://wormatlas.org/search_results.html?cx=016220512202578422943%3Amikvfhp2nri&cof=FORID%3A10&ie=UTF-8&q="
					+cmd.trim().substring(cmd.trim().indexOf(" ")+1, cmd.trim().indexOf(":"))+ "&siteurl=wormatlas.org%252F");
			if (jumpToCytoSHOW) cellPage = ("http://www.google.com/search?q="+ cmd.trim().substring(8).replaceAll("[:;]", " "));
			if (jumpToCytoSHOW && jumpToWormAtlas) cellPage = "http://www.textpresso.org/cgi-bin/celegans/query?textstring=\""
					+cmd.trim().substring(8,cmd.trim().indexOf(" "))+ "\"";
			IJ.runPlugIn("ij.plugin.BrowserLauncher", cellPage );

		}else if (cmd.startsWith("descended from") || cmd.startsWith("analogous to")) {
			//			IJ.log(cmd);
			//			String cellPage = "http://legacy.wormbase.org/db/ontology/anatomy?name="+cmd.trim().substring(15)+";open=show_Expr_pattern";
			String cellPage = "http://www.wormbase.org/db/get?name="+cmd.trim().substring(15)+";class=Anatomy_term";
			if (jumpToWormAtlas) cellPage = ("http://wormatlas.org/search_results.html?cx=016220512202578422943%3Amikvfhp2nri&cof=FORID%3A10&ie=UTF-8&q="
					+cmd.trim().substring(15)+ "&siteurl=wormatlas.org%252F");
			if (jumpToCytoSHOW) cellPage = ("http://www.google.com/search?q="+cmd.trim().substring(15));
			if (jumpToCytoSHOW && jumpToWormAtlas) cellPage = "http://www.textpresso.org/cgi-bin/celegans/query?textstring=\""
					+cmd.trim().substring(15)+ "\"";
			IJ.runPlugIn("ij.plugin.BrowserLauncher", cellPage );

		}else if (cmd.startsWith("synapses ") 
				|| cmd.startsWith("is synapsed ") 
				|| cmd.startsWith("neuromuscular junctions")) {
			//			IJ.log(cmd);
			//			String cellPage = "http://legacy.wormbase.org/db/ontology/anatomy?name="+cmd.trim().substring(26,cmd.indexOf(":"))+";open=show_Expr_pattern";
			String cellPage = "http://www.wormbase.org/db/get?name="+cmd.trim().substring(26,cmd.indexOf(":"))+";class=Anatomy_term";
			if (jumpToWormAtlas) cellPage = ("http://wormatlas.org/search_results.html?cx=016220512202578422943%3Amikvfhp2nri&cof=FORID%3A10&ie=UTF-8&q="
					+cmd.trim().substring(26,cmd.indexOf(":"))+ "&siteurl=wormatlas.org%252F");
			if (jumpToCytoSHOW) cellPage = ("http://www.google.com/search?q="+cmd.trim().substring(26,cmd.indexOf(":")));
			if (jumpToCytoSHOW && jumpToWormAtlas) cellPage = "http://www.textpresso.org/cgi-bin/celegans/query?textstring=\""
					+cmd.trim().substring(26,cmd.indexOf(":"))+ "\"";
			IJ.runPlugIn("ij.plugin.BrowserLauncher", cellPage );
		} else if (cmd.startsWith("movie ")) {
			String movieTitle = cmd.replace("movie ", "");
			boolean rcs =false;
			if (movieTitle != movieTitle.replace(" also viewable in RedCyan Stereo", "")) {
				movieTitle = movieTitle.replace(" also viewable in RedCyan Stereo", "");
				rcs = true;
			}

			String movieUrlOrPath = "";
			//			if(imp == null)
			//				imp = IJ.getImage();
			if (imp != null) {
				movieUrlOrPath = "" + imp.getCanvas().getPopupInfo()[1].split(movieTitle.replace("+", "\\+") + "...\n")[1]
					.split("\n")[rcs?2:0].trim();
			}else {
				return;
			}


			String fileName ="";
			String viewName= null;
			if ( movieUrlOrPath.contains("http://fsbill")){

				String[] fn = (movieUrlOrPath).replaceAll("%2B", "\\+").replaceAll("%25", "%").split("/") ;
				fileName = fn[fn.length-1];	

				viewName= null;
				if (fileName.contains("MOVIE=") ) {
					String[] fn2 = fileName.split("=|&");
					if (fn2.length < 3 /*&& !fn2[fn2.length - 1].contains("scene.scn")*/) 
						fileName = fn2[fn2.length-1];
					else if  (fn2.length >= 3 && 
							(fn2[fn2.length - 3].contains(".mov") || fn2[fn2.length - 3].contains(".avi"))){
						fileName = fn2[fn2.length-3];
						if (fn2[fn2.length-1].contains("scene.scn"))
							viewName = fn2[fn2.length-1];
					}
				}
			} else {
				fileName = movieUrlOrPath;

			}

			String path = "/Volumes/GLOWORM_DATA/" + fileName;

			if ((path.toLowerCase().endsWith(".mov") || path.toLowerCase().endsWith(".avi")) && viewName == null){
				ArrayList<String> rmiArgsArrayList = new ArrayList<String>();
				rmiArgsArrayList.add(IJ.rmiURL.split(" ")[0]);
				rmiArgsArrayList.add(IJ.rmiURL.split(" ")[1]);
				rmiArgsArrayList.add(path);

				if (rcs) {
					RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/
							+" "+path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
							false, true, true, false, true, false, true, false, false);
					imp = rmqtvsh.getImagePlus();
					imp.getWindow().setVisible(true);
				} else {
					//						RemoteMQTVSHandler.main(rmiArgsArrayList.toArray(new String[rmiArgsArrayList.size()]));
					//						build(String url, String portOffset, String pathsThenSlices, 
					//								boolean stretchToFitOverlay, boolean viewOverlay, boolean grayscale, boolean grid, boolean horizontal, boolean sideSideStereo, boolean redCyanStereo, boolean silentlyUpdateScene) {
					RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
							false, true, true, false, true, false, false, false, false);
					imp = rmqtvsh.getImagePlus();
					imp.getWindow().setVisible(true);
				}

			} else if ((path.toLowerCase().endsWith(".mov") || path.toLowerCase().endsWith(".avi")) && viewName != null) {
				ArrayList<String> rmiArgsArrayList = new ArrayList<String>();
				rmiArgsArrayList.add(IJ.rmiURL.split(" ")[0]);
				rmiArgsArrayList.add(IJ.rmiURL.split(" ")[1]);
				rmiArgsArrayList.add(path);

				RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/+" "+path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
						false, true, true, false, true, false, true, false, false);

				imp = rmqtvsh.getImagePlus();
				//					MultiChannelController mcc = new MultiChannelController(imp);
				//					imp.setMultiChannelController(mcc);
				MultiChannelController mcc = imp.getMultiChannelController();
				mcc.setChannelLUTChoice(0, 0);
				CompositeImage ci = (CompositeImage)imp;
				ci.setPosition( 0+1, ci.getSlice(), ci.getFrame() );
				IJ.doCommand(mcc.getChannelLUTChoice(0) );
				mcc.setChannelLUTChoice(1, 4);
				ci.setPosition( 1+1, ci.getSlice(), ci.getFrame() );
				IJ.doCommand(mcc.getChannelLUTChoice(1) );
				mcc.setSliceSpinner(0, 1);			
//				if (path.contains("_prx_")) {
//					mcc.setRotateAngleSpinner(0, 90);
//					mcc.setRotateAngleSpinner(1, 90);
//					rmqtvsh.setRotation(90);
//				}
				//					imp.show();
				imp.getWindow().setVisible(true);


			}else if (path.toLowerCase().endsWith("scene.scn")) 
				MQTVSSceneLoader64.runMQTVS_SceneLoader64(path);

		} else if (cmd.startsWith("Save this info") ){
			//			IJ.run("Text Window ");
			Editor cellInfoTextWin = new Editor();
			cellInfoTextWin.setTitle(imp.getCanvas().getPopupInfo()[0]+" Info");
			cellInfoTextWin.getTextArea().append(imp.getCanvas().getPopupInfo()[1]);
			cellInfoTextWin.setVisible(true);
		} else if (cmd.startsWith("Sketch3D Brainbow colors") ){
			IJ.setKeyDown(KeyEvent.VK_SHIFT);
			imp.getRoiManager().actionPerformed(new ActionEvent(imp,0,"Sketch3D Brainbow colors",0,0));
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
		} else if (cmd.startsWith("Color Legend") ){
			imp.getRoiManager().actionPerformed(new ActionEvent(imp,0,"Color Legend",0,0));
		} else {
			command = cmd;
			if (!(cmd.equals("Undo")||cmd.equals("Close")))
				previousCommand = cmd;
		}
		IJ.resetEscape();
		thread = new Thread(this, cmd);
		thread.setPriority(Math.max(thread.getPriority()-2, Thread.MIN_PRIORITY));
		if (imp!=null)
			WindowManager.setTempCurrentImage(thread, imp);
		thread.start();
	}

	public void run() {
		if (command==null) return;
		if (listeners.size()>0) synchronized (listeners) {
			for (int i=0; i<listeners.size(); i++) {
				CommandListener listener = (CommandListener)listeners.elementAt(i);
				command = listener.commandExecuting(command);
				if (command==null) return;
			}
		}
		try {
			if (Recorder.record) {
				Recorder.setCommand(command);
				runCommand(command);
				Recorder.saveCommand();
			} else
				runCommand(command);
			int len = command.length();
			if (len>0 && command.charAt(len-1)!=']')
				IJ.setKeyUp(IJ.ALL_KEYS);  // set keys up except for "<", ">", "+" and "-" shortcuts
		} catch(Throwable e) {
			IJ.showStatus("");
			IJ.showProgress(1, 1);
			ImagePlus imp = WindowManager.getCurrentImage();
			if (imp!=null) imp.unlock();
			String msg = e.getMessage();
			if (e instanceof OutOfMemoryError)
				IJ.outOfMemory(command);
			else if (e instanceof RuntimeException && msg!=null && msg.equals(Macro.MACRO_CANCELED))
				; //do nothing
			else {
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				String s = caw.toString();
				if (IJ.isMacintosh()) {
					if (s.indexOf("ThreadDeath")>0)
						return;
					s = Tools.fixNewLines(s);
				}
				int w=500, h=300;
				if (s.indexOf("UnsupportedClassVersionError")!=-1) {
					if (s.indexOf("version 49.0")!=-1) {
						s = e + "\n \nThis plugin requires Java 1.5 or later.";
						w=700; h=150;
					}
					if (s.indexOf("version 50.0")!=-1) {
						s = e + "\n \nThis plugin requires Java 1.6 or later.";
						w=700; h=150;
					}
					if (s.indexOf("version 51.0")!=-1) {
						s = e + "\n \nThis plugin requires Java 1.7 or later.";
						w=700; h=150;
					}
				}
				if (IJ.getInstance()!=null)
					new TextWindow("Exception", s, w, h);
				else
					IJ.log(s);
			}
		}
	}

	void runCommand(String cmd) {
		Hashtable table = Menus.getCommands();
		String className = (String)table.get(cmd);
		if (className!=null) {
			String arg = "";
			if (className.endsWith("\")")) {
				// extract string argument (e.g. className("arg"))
				int argStart = className.lastIndexOf("(\"");
				if (argStart>0) {
					arg = className.substring(argStart+2, className.length()-2);
					className = className.substring(0, argStart);
				}
			}
			if (IJ.shiftKeyDown() && className.startsWith("ij.plugin.Macro_Runner") && !Menus.getShortcuts().contains("*"+cmd))
				IJ.open(IJ.getDirectory("plugins")+arg);
			else
				IJ.runPlugIn(cmd, className, arg);
		} else {
			// Is this command in Plugins>Macros?
			if (MacroInstaller.runMacroCommand(cmd))
				return;
			// Is this command a LUT name?
			String path = IJ.getDirectory("luts")+cmd+".lut";
			File f = new File(path);
			if (f.exists()) {
				String dir = OpenDialog.getLastDirectory();
				IJ.open(path);
				OpenDialog.setLastDirectory(dir);
			} else if (!openRecent(cmd))
				IJ.error("Unrecognized command: " + cmd);
		}
	}

	/** Opens a file from the File/Open Recent menu 
 	      and returns 'true' if successful. */
	boolean openRecent(String cmd) {
		Menu menu = Menus.openRecentMenu;
		if (menu==null) return false;
		for (int i=0; i<menu.getItemCount(); i++) {
			if (menu.getItem(i).getLabel().equals(cmd)) {
				IJ.open(cmd);
				return true;
			}
		}
		return false;
	}

	/** Returns the last command executed. Returns null
		if no command has been executed. */
	public static String getCommand() {
		return previousCommand;
	}

	/** Adds the specified command listener. */
	public static void addCommandListener(CommandListener listener) {
		listeners.addElement(listener);
	}

	/** Removes the specified command listener. */
	public static void removeCommandListener(CommandListener listener) {
		listeners.removeElement(listener);
	}

}


