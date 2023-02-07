package ij.plugin;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.frame.ColorLegend;
import ij3d.ColorTable;
import ij3d.DefaultUniverse;
import ij3d.Image3DUniverse;
import ij3d.ImageCanvas3D;
import ij3d.ImageJ3DViewer;
import ij3d.ImageWindow3D;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.Event;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Window;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.*;

import javax.media.j3d.Canvas3D;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.MultiChannelController;
import org.vcell.gloworm.MultiQTVirtualStack;
import org.vcell.gloworm.QTMovieOpenerMultiMod;

import client.RemoteMQTVSHandler;
import client.RemoteMTVSHandler;

/** This class opens images, roi's, luts and text files dragged and dropped on  the "ImageJ" window.
     It is based on the Draw_And_Drop plugin by Eric Kischell (keesh@ieee.org).

     10 November 2006: Albert Cardona added Linux support and an  
     option to open all images in a dragged folder as a stack.
 */

public class DragAndDrop implements PlugIn, DropTargetListener, Runnable {
	private Iterator iterator;
	private ImagePlus imp;
	private ImagePlus dropImp;
	private static boolean convertToRGB;
	private static boolean virtualStack;
	private boolean openAsVirtualStack;
	private boolean doSketch3D;
	private int nDrops =0;
	private boolean traceLineages = false;
	private boolean traceForward = false;
	private boolean traceBackward = false;
	private DropTargetDropEvent dtde;
	private boolean freshDrop;
//	private ImageJ3DViewer dropViewer;
	private Image3DUniverse dropUniverse;
	private boolean working;
	
//	private static DragAndDrop instance;
//
//	public static DragAndDrop getInstance() {
//		return instance;
//	}

	public void run(String arg) {
//		instance = this;
//		ImageJ ij = IJ.getInstance();
//		if (ij!=null) {
//			ij.setDropTarget(null);
//			new DropTarget(ij, this);
//			new DropTarget(Toolbar.getInstance(), this);
//			new DropTarget(ij.getStatusBar(), this);
//		}
	}  

	public void addDropTarget(Component c) {
		c.setDropTarget(null);
		new DropTarget(c, this);
	}

	public void drop(DropTargetDropEvent dtde)  {
		this.dtde = dtde;
		freshDrop = true;
		dtde.acceptDrop(DnDConstants.ACTION_COPY);
		Component dtc = dtde.getDropTargetContext().getDropTarget().getComponent();
//		dropImp = WindowManager.getCurrentImage();
		dropImp = null;
		setImp(dropImp);
		if (dtc instanceof ImageCanvas) {
			dropImp = ((ImageCanvas) dtc).getImage();
			if (dropImp == null)
				dropImp = IJ.getImage();
			setImp(dropImp);
			if (getImp().getTitle().contains("Sketch3D"))
				doSketch3D = true;
			if (getImp().getMotherImp() != null && getImp().getMotherImp() != getImp())
				setImp(getImp().getMotherImp());
		} else if (dtc instanceof ImageCanvas3D) {
			dropUniverse = ((ImageCanvas3D) dtc).getUniverse();
			dropImp = dropUniverse.getWindow().getImagePlus();
			setImp(dropImp);

		} else {
			setImp(null);
		}
		
		DataFlavor URI_LIST_FLAVOR =null;
		try {
			URI_LIST_FLAVOR = new DataFlavor( "text/uri-list;class=java.lang.String" );
		}
		catch ( ClassNotFoundException ignore ) {

		}

		DataFlavor[] flavors = null;
		try  {
			Transferable t = dtde.getTransferable();
			Transferable transferable = dtde.getTransferable();
			setIterator(null);
			flavors = t.getTransferDataFlavors();
			if (IJ.debugMode) IJ.log("DragAndDrop.drop: "+flavors.length+" flavors");
			ArrayList droppedItemsArrayList = new ArrayList();

			for (int i=0; i<flavors.length; i++) {
				if (IJ.debugMode) IJ.log("  flavor["+i+"]: "+flavors[i].getMimeType());
				boolean consumed = false;
				if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					dtde.acceptDrop(dtde.getDropAction());
					try {

						List transferData = (List) transferable.getTransferData(DataFlavor.javaFileListFlavor);
						if (transferData != null && transferData.size() > 0) {
							//							IJ.log("You dropped " + transferData.size() + " files");							
							dtde.dropComplete(true);
							if (transferData.get(0) instanceof File && ((File)transferData.get(0)).isDirectory()){
								for (int j=0; j<transferData.size();j++){
									for (File file:((File)transferData.get(j)).listFiles()){
										if (file.getName().toLowerCase().endsWith(".obj"))
											droppedItemsArrayList.add(file);
									}
								}
								setIterator(droppedItemsArrayList.iterator());
							} else{
								setIterator(((List)transferData).iterator());
							}
							consumed = true;
							break;
						}

					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				if (flavors[i].isFlavorJavaFileListType()) {
					Object data = t.getTransferData(DataFlavor.javaFileListFlavor);
					String dataString = "" + ((List)data);
//					IJ.log(dataString);
					if (!dataString.contains("[]")) {
						setIterator(((List)data).iterator());
						consumed = true;
						break;
					}
				} 
				if (!consumed && flavors[i].isFlavorTextType() || 
						( URI_LIST_FLAVOR != null && t.isDataFlavorSupported( URI_LIST_FLAVOR ))){
					Object ob = null;
					if( URI_LIST_FLAVOR != null && t.isDataFlavorSupported( URI_LIST_FLAVOR )) {
						ob =  t.getTransferData( URI_LIST_FLAVOR );
					}
					else
						ob = t.getTransferData(flavors[i]);
					if (!(ob instanceof String)) continue;
					String s = ob.toString().trim().replaceAll("\\%(\\D)", "\\%25$1");
					if (IJ.debugMode) IJ.log(s+" is the input string from drop");
					if (IJ.isLinux() && s.length()>1 && (int)s.charAt(1)==0)
						s = fixLinuxString(s);

					if (s.indexOf("href=\"")!=-1 || s.indexOf("src=\"")!=-1) {
						s = parseHTML(s);
						if (IJ.debugMode) IJ.log("  url: "+s);
						droppedItemsArrayList.add(s);
						this.setIterator(droppedItemsArrayList.iterator());
						break;
					}
					BufferedReader br = new BufferedReader(new StringReader(s));
					String tmp;
					String clStr = "";
					while (null != (tmp = br.readLine())) {
						tmp = java.net.URLDecoder.decode(tmp.replaceAll("\\+","%2b"), "UTF-8");
						if (tmp.contains("file://")) {
							//							tmp = tmp.substring(7);
							tmp = tmp.replaceAll("file://", "").replaceAll("localhost", "");
						}
						if (IJ.debugMode) IJ.log("  content: "+tmp);
						if (tmp.contains("http://")) {
							droppedItemsArrayList.add(tmp);
						} else if ( !tmp.contains(File.separator)  
								&& (tmp.toLowerCase().endsWith(".mov") 
										|| tmp.toLowerCase().endsWith(".avi") 
										|| tmp.toLowerCase().contains("scene.scn")
										|| tmp.toLowerCase().endsWith(".obj")
										|| tmp.toLowerCase().endsWith(".zip")
										|| tmp.toLowerCase().endsWith(".tif"))){
							if (IJ.debugMode) IJ.log(" stringinput");
							droppedItemsArrayList.add(tmp);
						} else if ( !tmp.contains(File.separator) 
								&& (tmp.trim().split(" ")[0].toLowerCase().matches("\\D{3,5}-\\d{1,4}")
										|| tmp.trim().split(" ")[0].toLowerCase().matches("\\D{1}\\d{1,2}\\D{1,2}\\d{1,2}\\.\\d{1,2}"))) {
							//							IJ.log("https://www.wormbase.org/db/get?name="+ tmp.trim().split(" ")[0] + ";class=gene"
							//									+ (tmp.trim().split(" ").length>1?" "+tmp.trim().split(" ")[1]:""));

//OLD FORMAT, FAILS FOR MANY GENES 12052021
//							droppedItemsArrayList.add("https://www.wormbase.org/db/get?name="+ tmp.trim().split(" ")[0] + ";class=gene"
//									+ (tmp.trim().split(" ").length>1?" "+tmp.trim().split(" ")[tmp.trim().split(" ").length-1]:""));
//NEW FORMAT, WORKS FOR ALL? GENES 12052021
						String newSearchString = "https://www.wormbase.org/search/gene/"+ tmp.trim().split(" ")[0] + "?species=c_elegans"
									+ (tmp.trim().split(" ").length>1?" "+tmp.trim().split(" ")[tmp.trim().split(" ").length-1]:"");
							droppedItemsArrayList.add(newSearchString);
	
						} else if (tmp.endsWith("ColorLegend.lgd")){
							ColorLegend cl;
							Frame[] frames = WindowManager.getImageWindows();
							if (this.dtde.getDropTargetContext().getDropTarget().getComponent() instanceof ImageCanvas3D) {
								for (Frame frame:frames){
									if (frame instanceof ImageWindow3D){
										if (this.dtde.getDropTargetContext().getDropTarget().getComponent() == ((ImageWindow3D)frame).getUniverse().getCanvas()){
											Image3DUniverse i3duniv = (Image3DUniverse)((ImageWindow3D)frame).getUniverse();
											imp = i3duniv.getWindow().getImagePlus();
										}
									}
								}
							}

							if (new File(tmp).exists()){
								cl = new ColorLegend(getImp(), IJ.openAsString(tmp));
								IJ.showStatus("");
							} else
								IJ.showStatus("badpath");
							return;
						} else if(tmp.contains(File.separator)){
							if (IJ.debugMode) IJ.log(" fileinput");
							droppedItemsArrayList.add(new File(tmp));
						} else if (tmp.trim().split(" ")[0].toLowerCase().matches(".*expr\\d*.*")){
							droppedItemsArrayList.add("https://www.wormbase.org/species/c_elegans/expr_pattern/" +tmp.trim());
						} else if (tmp.trim().equalsIgnoreCase("*") || tmp.trim().equalsIgnoreCase(".*")) {
							getImp().getRoiManager().getTextSearchField().setText("");
							getImp().getRoiManager().actionPerformed(new ActionEvent(getImp().getRoiManager().getTextSearchField(),0,"",0,0));
							return;
						} else if (tmp.trim().split(" ")[0].matches(".*\\S.*")){
							//							IJ.log("anatomyName?");
							if ((tmp.trim().split(",").length ==2 && Colors.decode(tmp.split(",")[1], null) != null) ||
								(tmp.trim().split(",").length >2 && Colors.decode(tmp.split(",")[2], null) != null)){
//								if (tmp.equals(tmp.replaceAll("([A-Za-z]+),([A-Za-z]+),(.*)", "$1,$1,$3"))) {
//										IJ.wait(1); 
										Color testColor3 = Colors.decode(tmp.split(",")[1], null);
										if (testColor3 != null) {
											tmp = tmp.replaceAll("([A-Za-z]+,)(.*)", "$1$1$2");
										}
										clStr = clStr + tmp +"\n";
										continue;
									
//								}
							}
							droppedItemsArrayList.add(tmp.trim());
						}	
					}
					if (!clStr.contentEquals("")) {
						ColorLegend cl = new ColorLegend(imp, clStr);
						return;
					}
					this.setIterator(droppedItemsArrayList.iterator());
					break;
				}
			}
			if (getIterator()!=null) {
				Thread thread = new Thread(this, "DrawAndDrop");
				thread.setPriority(Math.max(thread.getPriority()-1, Thread.MIN_PRIORITY));
				thread.start();
			}

		}
		catch(Exception e)  {
			dtde.dropComplete(false);
			return;
		}
		dtde.dropComplete(true);
		if (flavors==null || flavors.length==0) {
			if (IJ.isMacOSX())
				IJ.error("First drag and drop ignored. Please try again. You can avoid this\n"
						+"problem by dragging to the toolbar instead of the status bar.");
			else
				IJ.error("Drag and drop failed");
		}
	}

	private String fixLinuxString(String s) {
		StringBuffer sb = new StringBuffer(200);
		for (int i=0; i<s.length(); i+=2)
			sb.append(s.charAt(i));
		return new String(sb);
	}

	private String parseHTML(String s) {
		if (IJ.debugMode) IJ.log("parseHTML:\n"+s);
		int index1 = s.indexOf("src=\"");
		if (index1>=0) {
			int index2 = s.indexOf("\"", index1+5);
			if (index2>0)
				return s.substring(index1+5, index2);
		}
		index1 = s.indexOf("href=\"");
		if (index1>=0) {
			int index2 = s.indexOf("\"", index1+6);
			if (index2>0)
				return s.substring(index1+6, index2);
		}
		return s;
	}

	public void dragEnter(DropTargetDragEvent e)  {
		IJ.showStatus("<<Drag and Drop>>");
		if (IJ.debugMode) IJ.log("DragEnter: "+e.getLocation());
		e.acceptDrag(DnDConstants.ACTION_COPY);
		openAsVirtualStack = false;
	}

	public void dragOver(DropTargetDragEvent e) {
		if (IJ.debugMode) IJ.log("DragOver: "+e.getLocation());
		ImageJ ij = IJ.getInstance();
		if (ij!=null) {
			if (e.getDropTargetContext().getDropTarget().getComponent().equals(ij.getStatusBar())) {
				Point loc = e.getLocation();
				int buttonSize = Toolbar.getButtonSize();
				int width = ij.getSize().width;
				openAsVirtualStack = width-loc.x<=buttonSize && 
						(e.getDropTargetContext().getDropTarget().getComponent().equals(ij.getStatusBar()));

				if (openAsVirtualStack)
					IJ.showStatus("<<Open with Special Settings>>");
				else
					IJ.showStatus("<<Drag and Drop>>");
			} else {
				Point loc = e.getLocation();
				int buttonSize = Toolbar.getButtonSize();
				int width = e.getDropTargetContext().getDropTarget().getComponent().getSize().width;
				openAsVirtualStack = width-loc.x<=buttonSize && 
						(e.getDropTargetContext().getDropTarget().getComponent() instanceof ImageCanvas3D);
				if (openAsVirtualStack)
					IJ.showStatus("<<Open with Special Settings>> into IJ3DViewer");
				else
					IJ.showStatus("<<Drag and Drop>> into IJ3DViewer");

			}
		}
	}

	public void dragExit(DropTargetEvent e) {
		IJ.showStatus("");
	}
	public void dropActionChanged(DropTargetDragEvent e) {}

	public void run() {
		working = true;
		ImagePlus imp = this.getImp();
		nDrops ++;
		traceLineages = false;
		traceForward = false;
		traceBackward = false;
		Iterator iterator = this.getIterator();
//		IJ.log(iterator.toString());
		Roi messageRoi = new TextRoi(0,0,"");
		if (imp != null && !(imp.getWindow() instanceof ImageWindow3D)) {
			messageRoi = new TextRoi(dropImp.getCanvas().getSrcRect().x, dropImp.getCanvas().getSrcRect().y,
					"   New drop queued...");

			((TextRoi) messageRoi).setCurrentFont(dropImp.getCanvas().getGraphics()
					.getFont().deriveFont((float) (dropImp.getCanvas().getSrcRect().width/16)));
			messageRoi.setStrokeColor(Color.black);
			messageRoi.setFillColor(Colors.decode("#99ffddff",
					dropImp.getCanvas().getDefaultColor()));

			dropImp.getCanvas().messageRois.put("Pending drop "+nDrops, messageRoi);
			dropImp.getCanvas().paintDoubleBuffered(dropImp.getCanvas().getGraphics());
			while (imp.getRoiManager().isBusy()) {

				IJ.wait(1000);
				//				IJ.error("Please wait!", "Please wait for response to the previous Drag-Drop action to complete before performing a new one.");
				//				return;
			}
		}
		String pathList = "";
		ArrayList<ArrayList<String>> cellSets = new ArrayList<ArrayList<String>>();
		ArrayList<String> rawCellSet = new ArrayList<String>();

		String cellsRegex = "??\"(";
		String finalSuffix = ") .*";
//		String embSymExcludedRegex = "(e(a|p)|c(a|p)....|ab(a|p)|ab......|ab.......)";
		String embSymExcludedRegex = "(E(A|P)|C(A|P)....|AB(A|P)|AB......|AB.......)";
		//			String embSymExcludedRegex = "(?<!e(a|p)|c(a|p)....|ab(a|p)|ab......|ab.......)";
//		String symAxesRegex = "(l|r|d|v|dl|dr|vl|vr)";
		String symAxesRegex = "(L|R|D|V|DL|DR|VL|VR)";
//		String synPrefix = "synapse:.*";
		String synPrefix = ".*(undefined|chemical|electrical|by|\\&)";
		
		String synSuffix = ".*";
		String cellListString = "";
		ColorLegend impCL = null;
		if (imp!=null && imp.getRoiManager()!=null) {
			//				IJ.showStatus("OK1");
			impCL = imp.getRoiManager().getColorLegend();
		}
		if (impCL != null) {
			//				IJ.showStatus("OK2");
			impCL.droppedCellColors = new Hashtable<Integer, Color>();
		}
		boolean or = false;
		IJ.log("");
		String[] partsListLogLines2 = {""};
		if (IJ.getLog()!= null)
			 partsListLogLines2 = IJ.getLog().split("\n");

		boolean partsLoaded = false;
		ArrayList<String> newSubDropsList = new ArrayList<String>();

		while(iterator.hasNext() && this.isWorking()) {
			if (traceLineages && !partsLoaded) {
				String oldLog = IJ.getLog();

				IJ.runMacro(""
						+ "print(\"starting...\");"
						+ "string = File.openUrlAsString(\"http://fsbill.cam.uchc.edu/gloworm/Xwords/Partslist.html\");"
						+ "fates = split(string, \"\\\'?\\\'\");"
						+ "print(\"Derived Fates:\");"
						+ "for (i=0; i<lengthOf(fates); i++) {"
						+ "		if (indexOf(fates[i], \"nofollow\") > 0)"
						+ "			print(\"begets \"+ replace(fates[i], \"\\\"\", \"\") );"
						+ "}");

				partsListLogLines2 = IJ.getLog().split("\n");
				IJ.log("\\Clear");
				IJ.log(oldLog);
				partsLoaded = true;
			}
			Object nextItem = iterator.next();
			if (nextItem!=null && (nextItem instanceof String) ) {
				if ( ((String)nextItem).startsWith("http")) {
					if ( ((String)nextItem).contains("http://fsbill.cam.uchc.edu/") ||
							((String)nextItem).contains("http://fsbill.vcell.uchc.edu/")){

						//						String macro = IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/Mount_GLOWORM_DATAmacro.txt");
						//						if (macro.contains("Remount GLOWORM_DATA")){
						//							IJ.runMacro(macro);
						//						}

						String[] fn = ((String)nextItem).replaceAll("%25", "%").split("/") ;
						String fileName = fn[fn.length-1];	
						String viewName= null;
						if (fileName.contains("MOVIE=") ) {
							String[] fn2 = fileName.split("=|&");
							if (fn2.length < 3 /*&& !fn2[fn2.length - 1].contains("scene.scn")*/) 
								fileName = fn2[fn2.length-1];
							else if  (fn2.length >= 3) {
								if ((fn2[fn2.length - 3].contains(".mov") || fn2[fn2.length - 3].contains(".avi"))){
									fileName = fn2[fn2.length-3];
									if (fn2[fn2.length-1].contains("scene.scn"))
										viewName = fn2[fn2.length-1];
								} else {
									if (fileName.contains("_SPECTAG=")) {
										fileName = fileName.replaceAll(".*MOVIE=", "");
									}
								}
							}
						}
						String path = fileName;
						if (IJ.isMacOSX() || IJ.isLinux())
							path = "/Volumes/GLOWORM_DATA/" + fileName;
						if(IJ.isWindows())
							path = "Q:\\" + fileName;							
						if (IJ.debugMode) IJ.log(path + " is the result of split and concatenate to build a path");
						if ((path.toLowerCase().endsWith(".mov") || path.toLowerCase().endsWith(".avi")) && viewName ==null  ) {
							pathList = pathList + path + ( (iterator.hasNext()  || openAsVirtualStack)? "|": "");
						} else if (path.toLowerCase().endsWith(".mov!") || path.toLowerCase().endsWith(".avi!")){
							String pathListSingle = path;
							//pathListSingle.replace(".mov!", ".mov");
							//							new QTMovieOpenerMultiMod(pathListSingle.substring(0, pathListSingle.length() - 1));
							RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replaceAll("%2B", "\\+").replaceAll("%25", "%")+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
									false, true, true, false, true, false, false, false, false);
							rmqtvsh.getImagePlus().getWindow().setVisible(true);
							if (dropImp == null)
								return;
							dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
							dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							//							IJ.log(nDrops+" nDrops");
							nDrops--;
							return;

						} else if ((path.toLowerCase().endsWith(".mov") || path.toLowerCase().endsWith(".avi")) && viewName != null) {
							if (IJ.debugMode) IJ.log("Found viewname =" + viewName);
							String pathListStereo = path;
							//							new QTMovieOpenerMultiMod(pathListStereo, true, false);
							RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replaceAll("%2B", "\\+").replaceAll("%25", "%")+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/+" "+path.replaceAll("%2B", "\\+").replaceAll("%25", "%")+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
									false, true, true, false, true, false, true, false, false);

							ImagePlus stereoImp = rmqtvsh.getImagePlus();
							MultiChannelController mcc = stereoImp.getMultiChannelController();
							mcc.setChannelLUTChoice(0, 0);
							CompositeImage ci = (CompositeImage)stereoImp;
							ci.setPosition( 0+1, ci.getSlice(), ci.getFrame() );
							IJ.doCommand(mcc.getChannelLUTChoice(0) );
							mcc.setChannelLUTChoice(1, 4);
							ci.setPosition( 1+1, ci.getSlice(), ci.getFrame() );
							IJ.doCommand(mcc.getChannelLUTChoice(1) );
							mcc.setSliceSpinner(0, 1);					
							//							if (path.contains("_prx_")) {
							//								mcc.setRotateAngleSpinner(0, 90);
							//								mcc.setRotateAngleSpinner(1, 90);
							//								rmqtvsh.setRotation(90);
							//							}
							stereoImp.getWindow().setVisible(true);

							if (dropImp == null)
								return;
							dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
							dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							//							IJ.log(nDrops+" nDrops");
							nDrops--;
						} else if (path.toLowerCase().endsWith(".tif") ) {
							String pathListStereo = path;
							//							new QTMovieOpenerMultiMod(pathListStereo, true, false);
							RemoteMTVSHandler rmtvsh = RemoteMTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replaceAll("%2B", "\\+").replaceAll("%25", "%")+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
									false, true, true, false, true, false, true, false, false);

							ImagePlus stereoImp = rmtvsh.getImagePlus();
							MultiChannelController mcc = stereoImp.getMultiChannelController();
							mcc.setChannelLUTChoice(0, 0);
							CompositeImage ci = (CompositeImage)stereoImp;
							ci.setPosition( 0+1, ci.getSlice(), ci.getFrame() );
							IJ.doCommand(mcc.getChannelLUTChoice(0) );
							mcc.setChannelLUTChoice(1, 4);
							ci.setPosition( 1+1, ci.getSlice(), ci.getFrame() );
							IJ.doCommand(mcc.getChannelLUTChoice(1) );
							mcc.setSliceSpinner(0, 1);					
							//							if (path.contains("_prx_")) {
							//								mcc.setRotateAngleSpinner(0, 90);
							//								mcc.setRotateAngleSpinner(1, 90);
							//								rmqtvsh.setRotation(90);
							//							}
							stereoImp.getWindow().setVisible(true);

							if (dropImp == null)
								return;
							dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
							dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							//							IJ.log(nDrops+" nDrops");
							nDrops--;
							return;

						}else if (path.toLowerCase().contains("scene.scn")) {
							if (IJ.is64Bit())
								MQTVSSceneLoader64.runMQTVS_SceneLoader64(path);
							else
								MQTVSSceneLoader64.runMQTVS_SceneLoader64(path);
							if (dropImp == null)
								return;
							dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
							dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							//							IJ.log(nDrops+" nDrops");
							nDrops--;
							return;
						}else if (path.toLowerCase().endsWith("suite.ste")) {
							(new Opener()).open(path);

							if (dropImp == null)
								return;
							dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
							dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							//							IJ.log(nDrops+" nDrops");
							nDrops--;
							return;

						}else if (path.toLowerCase().endsWith(".obj")) {
//							Component dtc = this.dtde.getDropTargetContext().getDropTarget().getComponent();
							Window dropWin = null;
							if (dropUniverse != null)
								dropWin = dropUniverse.getWindow();
							
							if (IJ.getInstance()!=null) {
								if ( this.dtde.getDropTargetContext().getDropTarget().getComponent() == IJ.getInstance().getStatusBar()){
									if (freshDrop){
										dropUniverse = null;
										dropImp = null;
										setImp(dropImp);
									}
								}
							}

							if (dropUniverse == null) {
								dropUniverse = new Image3DUniverse();
										dropUniverse.show(false);
										dropUniverse.getWindow().setDragAndDrop(DragAndDrop.this);
										IJ.getInstance().setDragAndDrop(new DragAndDrop());
										dropUniverse.getWindow().getDragAndDrop().addDropTarget(dropUniverse.getCanvas());
								while (dropUniverse.getWindow() == null) IJ.wait (10);
								dropImp = dropUniverse.getWindow().getImagePlus();
								setImp(dropImp);
							}

							freshDrop = false;
							
							File file = new File(path) ;
							BufferedReader in = null;
							PrintWriter out = null;

							
							if (path.startsWith("/Volumes/GLOWORM_DATA/")
									|| path.contains("\\GLOWORM_DATA\\")) {	

								File cacheFile = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+path);
								if (!(new File(cacheFile.getParent())).canWrite()) {
									//Crappy loop seems necessary for windows...							
									while (!(new File(cacheFile.getParent())).mkdirs()) { 
										while (!(new File(new File(cacheFile.getParent()).getParent())).mkdirs()) {
											while (!(new File(new File(new File(cacheFile.getParent()).getParent()).getParent())).mkdirs()) {
												while (!(new File(new File(new File(new File(cacheFile.getParent()).getParent()).getParent()).getParent()).mkdirs())) {

												}
											}
										}
									}
								}
								if (cacheFile.canRead()) {
									//										in = new BufferedReader(new FileReader(cacheFile));
									nextItem = cacheFile;
								}else {
									in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
											RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replace(".obj", ".mtl").replaceAll("%2B", "\\+").replaceAll("%25", "%").replace("|", "")))));
									try {
										out = new PrintWriter(
												new BufferedWriter(
														new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path.replace(".obj", ".mtl")) ), true);
										String line = "";
										if (line != null) {
											while (line != null && !line.contains("End of parameter list")) {
												line = in.readLine();
												if (out!=null)
													out.println(line);
											}
										}
										out.close();
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									
									in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
											RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replaceAll("%2B", "\\+").replaceAll("%25", "%").replace("|", "")))));
									try {
										out = new PrintWriter(
												new BufferedWriter(
														new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path) ), true);
										String line = "";
										if (line != null) {
											while (line != null && !line.contains("End of parameter list")) {
												line = in.readLine();
												if (out!=null)
													out.println(line);
											}
										}
										out.close();
										nextItem = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path);
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							} else {

							}
							
							try {
								if (openAsVirtualStack) {
									dropUniverse.setFlipXonImport(true);
									int s = this.dtde.getDropAction();
									dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
								} else {
									int s = this.dtde.getDropAction();
									dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
								}
							} catch (Exception e) {
								dropUniverse = new Image3DUniverse();
										dropUniverse.show(false);
								if (openAsVirtualStack) {
									dropUniverse.setFlipXonImport(true);
									int s = this.dtde.getDropAction();
									dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
								} else {
									int s = this.dtde.getDropAction();
									dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
								}
							}
							//ImageJ3DViewer.lock();

						} else {
							dropImp = new Opener().openURL((String)nextItem);
							setImp(dropImp);
							dropImp.show();
							if (dropImp == null)
								return;
							dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
							dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							//							IJ.log(nDrops+" nDrops");
							nDrops--;
							return;
						}
//					} else if (dropUniverse !=null && ((String)nextItem).contains("https://www.wormbase.org/")){
						
					} else if ((dropUniverse !=null || imp !=null) && ((String)nextItem).contains("https://www.wormbase.org/")){
						if (dropUniverse == null) {
							imp.getRoiManager().setBusy(true);
							Graphics g = dropImp.getCanvas().getGraphics();
							if (dropImp.getCanvas().messageRois.containsKey("Finding tags from drop"))
								dropImp.getCanvas().messageRois.remove("Finding tags from drop");

							messageRoi = new TextRoi(dropImp.getCanvas().getSrcRect().x, dropImp.getCanvas().getSrcRect().y,
									"   Finding tags that match\n   "+ (cellSets.size()+1)  +" pattern(s)\n   ...");

							((TextRoi) messageRoi).setCurrentFont(g.getFont().deriveFont((float) (dropImp.getCanvas().getSrcRect().width/16)));
							messageRoi.setStrokeColor(Color.black);
							messageRoi.setFillColor(Colors.decode("#99ffdddd",
									dropImp.getCanvas().getDefaultColor()));

							dropImp.getCanvas().messageRois.put("Finding tags from drop", messageRoi);
							dropImp.getCanvas().paintDoubleBuffered(dropImp.getCanvas().getGraphics());

						}
						if ( ((String)nextItem).matches(".*/anatomy_term/WBbt:\\d*") ||
								((String)nextItem).matches(".*https://www.wormbase.org/db/get\\?name=.*;class=Anatomy_term")){
							//							IJ.log("cellURL");
							String cellColorCode = (((String)nextItem).split(" ").length>1?((String)nextItem).split(" ")[((String)nextItem).split(" ").length-1].trim():"");
							String oldLog = IJ.getLog();
							IJ.log("\\Clear");
							IJ.runMacro(""
									+ "string = File.openUrlAsString(\""+((String)nextItem)+"\");"
									+ "print(string);");							
							String[] logLines2 = IJ.getLog().split("<title>WormBase:  anatomy_term Summary: ");
							IJ.log("\\Clear");
							IJ.log(oldLog);
							if (logLines2.length<2)
								return;
							String s = logLines2[1].split("  </title>")[0];
							rawCellSet.add(s.toUpperCase().trim());	

							if (cellColorCode != "" && impCL!=null) {
								if (impCL.getBrainbowColors().get(s.toLowerCase().trim())!=null) {
									if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB()) == null) {
										impCL.droppedCellColors.put(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
									} else {
										Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB());
										Color nuevo = Colors.decode(cellColorCode, Color.white);
										int newRed = old.getRed()+nuevo.getRed(); 
										int newGreen = old.getGreen()+nuevo.getGreen(); 
										int newBlue = old.getBlue()+nuevo.getBlue();
										int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
										Color mix = new Color(255*newRed/newMax, 
												255*newGreen/newMax, 
												255*newBlue/newMax);
										impCL.droppedCellColors.put(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB(), mix);
									}
								}
								//								IJ.showStatus("newdropcolor");
							}

							cellListString = cellListString + s + ",";

							if (traceLineages && traceForward) {
								for (int l = 0; l < partsListLogLines2.length; l++) {
									partsListLogLines2[l] = partsListLogLines2[l]
											.replace("</a></td><td>", ":")
											.replace("</td><td>", ";")
											.replaceAll("\\.", "");
									if (s.toUpperCase().trim().matches("EMS"))
										s = "(E|MS)";
									if (s.toUpperCase().trim().matches("P0"))
										s = "(P0|AB|EMS|E|MS|C|D)";
									if (s.toUpperCase().trim().matches("P1"))
										s = "(P0p|EMS|E|MS|C|D)";
									if (s.toUpperCase().trim().matches("P2"))
										s = "(P0pp|C|D)";
									if (s.toUpperCase().trim().matches("P3"))
										s = "(P0ppp|D)";
									if (s.toUpperCase().trim().matches("P4"))
										s = "(P0pppp)";

									if (partsListLogLines2[l].startsWith("begets ")
											&& partsListLogLines2[l].toLowerCase().matches(".*:"+s.toLowerCase().replace("*", "")+".*;.*")) {
										String as = partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "");
										rawCellSet.add(as.toUpperCase().trim());
										if (cellColorCode != "" && impCL!=null) {
											if (impCL.getBrainbowColors().get(as.toLowerCase().trim())!=null) {
												if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB()) == null) {
													impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
												} else {
													Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB());
													Color nuevo = Colors.decode(cellColorCode, Color.white);
													int newRed = old.getRed()+nuevo.getRed(); 
													int newGreen = old.getGreen()+nuevo.getGreen(); 
													int newBlue = old.getBlue()+nuevo.getBlue();
													int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
													Color mix = new Color(255*newRed/newMax, 
															255*newGreen/newMax, 
															255*newBlue/newMax);
													impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), mix);
												}
											}
											//										IJ.showStatus("newdropcolor");
										}
										cellListString = cellListString + as + ",";
									}
									if (partsListLogLines2[l].startsWith("begets ")
											&& partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "").matches(s.toLowerCase().replace("*", "")+".*")) {
										s = partsListLogLines2[l].toLowerCase().replaceAll(".*:", "").replaceAll(";.*", "");
										IJ.showStatus(s);
										//										IJ.runMacro("waitForUser;");
									}
								}
							}
							if (traceLineages) {
						for (JCheckBox cbq:impCL.getJCheckBox()) {
									if ((traceForward 
											&& (cbq.getName().toUpperCase().trim().startsWith(s.toUpperCase().trim())
													|| (s.toUpperCase().matches("P1") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
													|| (s.toUpperCase().matches("P2") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|Z."))
													|| (s.toUpperCase().matches("P3") && cbq.getName().toUpperCase().matches("P.|D.*|Z."))
													|| (s.toUpperCase().matches("P4") && cbq.getName().toUpperCase().matches("P.|Z."))
													|| (s.toUpperCase().matches("EMS") && cbq.getName().toUpperCase().matches("E.*|MS.*"))))
													||(traceBackward
															&& (s.toUpperCase().trim().startsWith(cbq.getName().toUpperCase().trim())
																	|| (cbq.getName().toUpperCase().matches("P1") && s.toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
																	|| (cbq.getName().toUpperCase().matches("P2") && s.toUpperCase().matches("P.|C.*|D.*|Z."))
																	|| (cbq.getName().toUpperCase().matches("P3") && s.toUpperCase().matches("P.|D.*|Z."))
																	|| (cbq.getName().toUpperCase().matches("P4") && s.toUpperCase().matches("P.|Z."))
																	|| (cbq.getName().toUpperCase().matches("EMS") && s.toUpperCase().matches("E.*|MS.*"))))
											) {
										rawCellSet.add(cbq.getName().toUpperCase().trim());  //to search with
										Enumeration bbcKenum = impCL.getBrainbowColors().keys();
										while (bbcKenum.hasMoreElements()) {
											String es = (String) bbcKenum.nextElement();
											//								IJ.showStatus(es);
											IJ.showStatus(cbq.getName().trim());
											if (es.toLowerCase().trim().matches(cbq.getName().toLowerCase().trim())) {
												if (cellColorCode != "" && impCL!=null) {
													if (impCL.getBrainbowColors().get(es.toLowerCase().trim())!=null) {
														if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB()) == null) {
															//												IJ.showStatus("NO OVERLAP");
															impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
														} else {
															//												IJ.showStatus("OVERLAP");
															Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB());
															Color nuevo = Colors.decode(cellColorCode, Color.white);
															int newRed = old.getRed()+nuevo.getRed(); 
															int newGreen = old.getGreen()+nuevo.getGreen(); 
															int newBlue = old.getBlue()+nuevo.getBlue();
															int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
															Color mix = new Color(255*newRed/newMax, 
																	255*newGreen/newMax, 
																	255*newBlue/newMax);
															impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), mix);
														}
													}
													//										IJ.showStatus("newdropcolor");
												}
											}
										}
									}
								}
							}
						} else if ( ((String)nextItem).matches(".*/gene/WBGene\\d*.*") 
								|| ((String)nextItem).matches(".*https://www.wormbase.org/db/get\\?name=.*;class=gene.*")
								|| ((String)nextItem).matches(".*https://www.wormbase.org/search/gene/.*\\?species.*"))  {
							String cellColorCode = (((String)nextItem).split(" ").length>1?((String)nextItem).split(" ")[((String)nextItem).split(" ").length-1].trim():"");
							String oldLog = IJ.getLog();
							IJ.log("\\Clear");
							IJ.runMacro(""
									+ "string = File.openUrlAsString(\""+((String)nextItem).split(" ")[0].trim()+"\");"
									+ "print(string);");							
							String[] logLines2 = IJ.getLog().split("wname=\"expression\"");
							IJ.log("\\Clear");
							IJ.log(oldLog);
							String restString = "";
							if (logLines2.length > 1 && logLines2[1].split("\"").length > 1)
								restString= logLines2[1].split("\"")[1];
							//							IJ.log(restString);

							IJ.log("\\Clear");
							IJ.runMacro(""
									+ "string = File.openUrlAsString(\"https://www.wormbase.org"
									+ restString
									+ "\");"

									+ "cells = split(string, \"><\");"
									+ "for (i=0; i<lengthOf(cells); i++) {"
									+ "	if (startsWith(cells[i], \"a href=\\\"/species/all/anatomy_term/\") ) "
									+ "		print(cells[i+1]);"
									+ "}");
							logLines2 = IJ.getLog().toLowerCase().split("\n");
							IJ.log("\\Clear");
							IJ.log(oldLog);
							Arrays.sort(logLines2);
							ArrayList<String> cellSet = new ArrayList<String>();
							cellSets.add(cellSet);
							for (String s:logLines2) {
								cellSet.add(s.toUpperCase().trim());
								if (cellColorCode != "" && impCL!=null) {
									if (impCL.getBrainbowColors().get(s.toLowerCase().trim())!=null) {
										if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB()) == null) {
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
										} else {
											Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB());
											Color nuevo = Colors.decode(cellColorCode, Color.white);
											int newRed = old.getRed()+nuevo.getRed(); 
											int newGreen = old.getGreen()+nuevo.getGreen(); 
											int newBlue = old.getBlue()+nuevo.getBlue();
											int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
											Color mix = new Color(255*newRed/newMax, 
													255*newGreen/newMax, 
													255*newBlue/newMax);
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB(), mix);
										}
									}
									//									IJ.showStatus("newdropcolor");
								}
								if (traceLineages && traceForward) {
									for (int l = 0; l < partsListLogLines2.length; l++) {
										partsListLogLines2[l] = partsListLogLines2[l]
												.replace("</a></td><td>", ":")
												.replace("</td><td>", ";")
												.replaceAll("\\.", "");
										if (s.toUpperCase().trim().matches("EMS"))
											s = "(E|MS)";
										if (s.toUpperCase().trim().matches("P0"))
											s = "(P0|AB|EMS|E|MS|C|D)";
										if (s.toUpperCase().trim().matches("P1"))
											s = "(P0p|EMS|E|MS|C|D)";
										if (s.toUpperCase().trim().matches("P2"))
											s = "(P0pp|C|D)";
										if (s.toUpperCase().trim().matches("P3"))
											s = "(P0ppp|D)";
										if (s.toUpperCase().trim().matches("P4"))
											s = "(P0pppp)";

										if (partsListLogLines2[l].startsWith("begets ")
												&& partsListLogLines2[l].toLowerCase().matches(".*:"+s.toLowerCase().replace("*", "")+".*;.*")) {
											String as = partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "");
											cellSet.add(as.toUpperCase().trim());
											if (cellColorCode != "" && impCL!=null) {
												if (impCL.getBrainbowColors().get(as.toLowerCase().trim())!=null) {
													if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB()) == null) {
														impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
													} else {
														Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB());
														Color nuevo = Colors.decode(cellColorCode, Color.white);
														int newRed = old.getRed()+nuevo.getRed(); 
														int newGreen = old.getGreen()+nuevo.getGreen(); 
														int newBlue = old.getBlue()+nuevo.getBlue();
														int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
														Color mix = new Color(255*newRed/newMax, 
																255*newGreen/newMax, 
																255*newBlue/newMax);
														impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), mix);
													}
												}
												//										IJ.showStatus("newdropcolor");
											}
										}
										if (partsListLogLines2[l].startsWith("begets ")
												&& partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "").matches(s.toLowerCase().replace("*", "")+".*")) {
											s = partsListLogLines2[l].toLowerCase().replaceAll(".*:", "").replaceAll(";.*", "");
											IJ.showStatus(s);
											//											IJ.runMacro("waitForUser;");
										}
									}
								}
								if (traceLineages) {
									for (JCheckBox cbq:impCL.getJCheckBox()) {
										if ((traceForward 
												&& (cbq.getName().toUpperCase().trim().startsWith(s.toUpperCase().trim())
														|| (s.toUpperCase().matches("P1") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
														|| (s.toUpperCase().matches("P2") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|Z."))
														|| (s.toUpperCase().matches("P3") && cbq.getName().toUpperCase().matches("P.|D.*|Z."))
														|| (s.toUpperCase().matches("P4") && cbq.getName().toUpperCase().matches("P.|Z."))
														|| (s.toUpperCase().matches("EMS") && cbq.getName().toUpperCase().matches("E.*|MS.*"))))
														||(traceBackward
																&& (s.toUpperCase().trim().startsWith(cbq.getName().toUpperCase().trim())
																		|| (cbq.getName().toUpperCase().matches("P1") && s.toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
																		|| (cbq.getName().toUpperCase().matches("P2") && s.toUpperCase().matches("P.|C.*|D.*|Z."))
																		|| (cbq.getName().toUpperCase().matches("P3") && s.toUpperCase().matches("P.|D.*|Z."))
																		|| (cbq.getName().toUpperCase().matches("P4") && s.toUpperCase().matches("P.|Z."))
																		|| (cbq.getName().toUpperCase().matches("EMS") && s.toUpperCase().matches("E.*|MS.*"))))
												) {
											if (!cellSet.contains(cbq.getName().toUpperCase().trim())) {
												cellSet.add(cbq.getName().toUpperCase().trim());  //to search with
												Enumeration bbcKenum = impCL.getBrainbowColors().keys();
												while (bbcKenum.hasMoreElements()) {
													String es = (String) bbcKenum.nextElement();
													//								IJ.showStatus(es);
													IJ.showStatus(cbq.getName().trim());
													if (es.toLowerCase().trim().matches(cbq.getName().toLowerCase().trim())) {
														if (cellColorCode != "" && impCL!=null) {
															if (impCL.getBrainbowColors().get(es.toLowerCase().trim())!=null) {
																if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB()) == null) {
																	//												IJ.showStatus("NO OVERLAP");
																	impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
																} else {
																	//												IJ.showStatus("OVERLAP");
																	Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB());
																	Color nuevo = Colors.decode(cellColorCode, Color.white);
																	int newRed = old.getRed()+nuevo.getRed(); 
																	int newGreen = old.getGreen()+nuevo.getGreen(); 
																	int newBlue = old.getBlue()+nuevo.getBlue();
																	int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
																	Color mix = new Color(255*newRed/newMax, 
																			255*newGreen/newMax, 
																			255*newBlue/newMax);
																	impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), mix);
																}
															}
															//										IJ.showStatus("newdropcolor");
														}
													}
												}
											}
										}
									}
								}
							}
							dropImp.getCanvas().droppedGeneUrls = (dropImp.getCanvas().droppedGeneUrls != ""?dropImp.getCanvas().droppedGeneUrls:"(Live_from_WormBase...)\nCells_expressing:\n") 
									+ ((String)nextItem).replace("https://www.wormbase.org/db/get?name=", "")
									.replace(";class=gene", "")
									.replaceAll(".*/gene/", "").split(" ")[0]+ "&";

						} else if ( ((String)nextItem).matches(".*/expr_pattern/Expr\\d*")){
							//							IJ.log("exprURL");							
							String cellColorCode = (((String)nextItem).split(" ").length>1?((String)nextItem).split(" ")[((String)nextItem).split(" ").length-1].trim():"");
							String oldLog = IJ.getLog();
							IJ.log("\\Clear");
							IJ.runMacro(""
									+ "string = File.openUrlAsString(\""+((String)nextItem)+"\");"
									+ "print(string);");							
							String[] logLines2 = IJ.getLog().split("wname=\"details\"");
							IJ.log("\\Clear");
							IJ.log(oldLog);
							String restString = logLines2[1].split("\"")[1];
							//							IJ.log(restString);

							IJ.log("\\Clear");
							IJ.runMacro(""
									+ "string = File.openUrlAsString(\"https://www.wormbase.org"
									+ restString
									+ "\");"

									+ "cells = split(string, \"><\");"
									+ "for (i=0; i<lengthOf(cells); i++) {"
									+ "	if (startsWith(cells[i], \"a href=\\\"/species/all/anatomy_term/\") ) "
									+ "		print(cells[i+1]);"
									+ "}");
							logLines2 = IJ.getLog().toLowerCase().split("\n");
							IJ.log("\\Clear");
							IJ.log(oldLog);
							Arrays.sort(logLines2);
							ArrayList<String> cellSet = new ArrayList<String>();
							cellSets.add(cellSet);
							for (String s:logLines2) {
								cellSet.add(s.toUpperCase().trim());
								if (cellColorCode != "" && impCL!=null) {
									if (impCL.getBrainbowColors().get(s.toLowerCase().trim())!=null) {
										if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB()) == null) {
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
										} else {
											Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB());
											Color nuevo = Colors.decode(cellColorCode, Color.white);
											int newRed = old.getRed()+nuevo.getRed(); 
											int newGreen = old.getGreen()+nuevo.getGreen(); 
											int newBlue = old.getBlue()+nuevo.getBlue();
											int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
											Color mix = new Color(255*newRed/newMax, 
													255*newGreen/newMax, 
													255*newBlue/newMax);
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(s.toLowerCase().trim()).getRGB(), mix);
										}
									}
									//									IJ.showStatus("newdropcolor");
								}
								if (traceLineages && traceForward) {
									for (int l = 0; l < partsListLogLines2.length; l++) {
										partsListLogLines2[l] = partsListLogLines2[l]
												.replace("</a></td><td>", ":")
												.replace("</td><td>", ";")
												.replaceAll("\\.", "");
										if (s.toUpperCase().trim().matches("EMS"))
											s = "(E|MS)";
										if (s.toUpperCase().trim().matches("P0"))
											s = "(P0|AB|EMS|E|MS|C|D)";
										if (s.toUpperCase().trim().matches("P1"))
											s = "(P0p|EMS|E|MS|C|D)";
										if (s.toUpperCase().trim().matches("P2"))
											s = "(P0pp|C|D)";
										if (s.toUpperCase().trim().matches("P3"))
											s = "(P0ppp|D)";
										if (s.toUpperCase().trim().matches("P4"))
											s = "(P0pppp)";

										if (partsListLogLines2[l].startsWith("begets ")
												&& partsListLogLines2[l].toLowerCase().matches(".*:"+s.toLowerCase().replace("*", "")+".*;.*")) {
											String as = partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "");
											cellSet.add(as.toUpperCase().trim());
											if (cellColorCode != "" && impCL!=null) {
												if (impCL.getBrainbowColors().get(as.toLowerCase().trim())!=null) {
													if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB()) == null) {
														impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
													} else {
														Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB());
														Color nuevo = Colors.decode(cellColorCode, Color.white);
														int newRed = old.getRed()+nuevo.getRed(); 
														int newGreen = old.getGreen()+nuevo.getGreen(); 
														int newBlue = old.getBlue()+nuevo.getBlue();
														int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
														Color mix = new Color(255*newRed/newMax, 
																255*newGreen/newMax, 
																255*newBlue/newMax);
														impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), mix);
													}
												}
												//										IJ.showStatus("newdropcolor");
											}
										}
										if (partsListLogLines2[l].startsWith("begets ")
												&& partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "").matches(s.toLowerCase().replace("*", "")+".*")) {
											s = partsListLogLines2[l].toLowerCase().replaceAll(".*:", "").replaceAll(";.*", "");
											IJ.showStatus(s);
											//											IJ.runMacro("waitForUser;");
										}
									}
								}
								if (traceLineages) {
									for (JCheckBox cbq:impCL.getJCheckBox()) {
										if ((traceForward 
												&& (cbq.getName().toUpperCase().trim().startsWith(s.toUpperCase().trim())
														|| (s.toUpperCase().matches("P1") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
														|| (s.toUpperCase().matches("P2") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|Z."))
														|| (s.toUpperCase().matches("P3") && cbq.getName().toUpperCase().matches("P.|D.*|Z."))
														|| (s.toUpperCase().matches("P4") && cbq.getName().toUpperCase().matches("P.|Z."))
														|| (s.toUpperCase().matches("EMS") && cbq.getName().toUpperCase().matches("E.*|MS.*"))))
														||(traceBackward
																&& (s.toUpperCase().trim().startsWith(cbq.getName().toUpperCase().trim())
																		|| (cbq.getName().toUpperCase().matches("P1") && s.toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
																		|| (cbq.getName().toUpperCase().matches("P2") && s.toUpperCase().matches("P.|C.*|D.*|Z."))
																		|| (cbq.getName().toUpperCase().matches("P3") && s.toUpperCase().matches("P.|D.*|Z."))
																		|| (cbq.getName().toUpperCase().matches("P4") && s.toUpperCase().matches("P.|Z."))
																		|| (cbq.getName().toUpperCase().matches("EMS") && s.toUpperCase().matches("E.*|MS.*"))))
												) {
											if (!cellSet.contains(cbq.getName().toUpperCase().trim())){
												cellSet.add(cbq.getName().toUpperCase().trim());  //to search with
												Enumeration bbcKenum = impCL.getBrainbowColors().keys();
												while (bbcKenum.hasMoreElements()) {
													String es = (String) bbcKenum.nextElement();
													//								IJ.showStatus(es);
													IJ.showStatus(cbq.getName().trim());
													if (es.toLowerCase().trim().matches(cbq.getName().toLowerCase().trim())) {
														if (cellColorCode != "" && impCL!=null) {
															if (impCL.getBrainbowColors().get(es.toLowerCase().trim())!=null) {
																if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB()) == null) {
																	//												IJ.showStatus("NO OVERLAP");
																	impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
																} else {
																	//												IJ.showStatus("OVERLAP");
																	Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB());
																	Color nuevo = Colors.decode(cellColorCode, Color.white);
																	int newRed = old.getRed()+nuevo.getRed(); 
																	int newGreen = old.getGreen()+nuevo.getGreen(); 
																	int newBlue = old.getBlue()+nuevo.getBlue();
																	int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
																	Color mix = new Color(255*newRed/newMax, 
																			255*newGreen/newMax, 
																			255*newBlue/newMax);
																	impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), mix);
																}
															}
															//										IJ.showStatus("newdropcolor");
														}
													}
												}
											}
										}
									}
								}
							}
							dropImp.getCanvas().droppedGeneUrls = (dropImp.getCanvas().droppedGeneUrls != ""?dropImp.getCanvas().droppedGeneUrls:"(Live_from_WormBase...)\nCells_expressing:\n") 
									+ ((String)nextItem).replaceAll(".*/expr_pattern/", "")+ "&";

						} else {
							dropImp = new Opener().openURL((String)nextItem);
							setImp(dropImp);
							dropImp.show();
							if (dropImp == null)
								return;
							dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
							dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							//							IJ.log(nDrops+" nDrops");
							nDrops--;
							return;
						}
					} else {
						imp = new Opener().openURL((String)nextItem);
						imp.show();
					}
				} else if ( ((String)nextItem).toLowerCase().endsWith(".mov") || ((String)nextItem).toLowerCase().endsWith(".avi") ){
					String path = "";
					if (!((String)nextItem).contains(File.separator) && !((String)nextItem).startsWith("/Volumes/GLOWORM_DATA/")) {
						if (IJ.isMacOSX()  || IJ.isLinux()) 
							path = "/Volumes/GLOWORM_DATA/" + ((String)nextItem);
						if(IJ.isWindows())
							path = "Q:\\" + ((String)nextItem);
					}else
						path = ((String)nextItem);
					if (IJ.debugMode) IJ.log(path + " is the path sent to QTMOMM");
					pathList = pathList + path +  ((iterator.hasNext()  || openAsVirtualStack)? "|": "");
				} else if (( ((String)nextItem).toLowerCase().contains("scene.scn"))){
					String path = "";
					if (!((String)nextItem).contains(File.separator) && !((String)nextItem).startsWith("/Volumes/GLOWORM_DATA/")) {
						if (IJ.isMacOSX() || IJ.isLinux()) {
							path = "/Volumes/GLOWORM_DATA/" + ((String)nextItem);
						}
						if(IJ.isWindows())
							path = "Q:\\" + ((String)nextItem);
					} else
						path = ((String)nextItem);
					if (IJ.debugMode) IJ.log(path + " is the path sent to QTMOMM");
					if (IJ.is64Bit())
						MQTVSSceneLoader64.runMQTVS_SceneLoader64(path);
					else
						MQTVSSceneLoader64.runMQTVS_SceneLoader64(path);
				}else if (((String)nextItem).toLowerCase().endsWith(".obj")) {
//					Component dtc = this.dtde.getDropTargetContext().getDropTarget().getComponent();
					Window dropWin = null;
					if (dropUniverse != null)
						dropWin = dropUniverse.getWindow();
					
					if (IJ.getInstance()!=null) {
						if ( this.dtde.getDropTargetContext().getDropTarget().getComponent() == IJ.getInstance().getStatusBar()){
							if (freshDrop){
								dropUniverse = null;
								dropImp = null;
								setImp(dropImp);
							}
						}
					}

					if (dropUniverse == null) {
						dropUniverse = new Image3DUniverse();
								dropUniverse.show(false);
								dropUniverse.getWindow().setDragAndDrop(DragAndDrop.this);
								IJ.getInstance().setDragAndDrop(new DragAndDrop());
								dropUniverse.getWindow().getDragAndDrop().addDropTarget(dropUniverse.getCanvas());
						while (dropUniverse.getWindow() == null) IJ.wait (10);
						dropImp = dropUniverse.getWindow().getImagePlus();
						setImp(dropImp);
					}

					freshDrop = false;
					
					String path = (String)nextItem;
					File file = new File(path) ;
					BufferedReader in = null;
					PrintWriter out = null;

					
					File cacheObj = null;
					File cacheMtl = null;
					if (path.startsWith("/Volumes/GLOWORM_DATA/")
							|| path.contains("\\GLOWORM_DATA\\")) {	

						cacheObj = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+path);
						cacheMtl = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+path.replace(".obj", ".mtl"));
						if (!(new File(cacheObj.getParent())).canWrite()) {
							//Crappy loop seems necessary for windows...							
							while (!(new File(cacheObj.getParent())).mkdirs()) { 
								while (!(new File(new File(cacheObj.getParent()).getParent())).mkdirs()) {
									while (!(new File(new File(new File(cacheObj.getParent()).getParent()).getParent())).mkdirs()) {
										while (!(new File(new File(new File(new File(cacheObj.getParent()).getParent()).getParent()).getParent()).mkdirs())) {

										}
									}
								}
							}
						}
						if (cacheObj.canRead() && cacheMtl.canRead()) {

							// skip ahead to opening obj
							
						}else {
							if (!cacheMtl.canRead()) {
								in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
										RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replace(".obj", ".mtl").replaceAll("%2B", "\\+").replaceAll("%25", "%").replace("|", "")))));
								try {
									out = new PrintWriter(
											new BufferedWriter(
													new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path.replace(".obj", ".mtl")) ), true);
									String line = "";
									if (line != null) {
										while (line != null && !line.contains("End of parameter list")) {
											line = in.readLine();
											if (out!=null)
												out.println(line);
										}
									}
									out.close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
							if (!cacheObj.canRead()) {							
								in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
										RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replaceAll("%2B", "\\+").replaceAll("%25", "%").replace("|", "")))));
								try {
									out = new PrintWriter(
											new BufferedWriter(
													new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path) ), true);
									String line = "";
									if (line != null) {
										while (line != null && !line.contains("End of parameter list")) {
											line = in.readLine();
											if (out!=null)
												out.println(line);
										}
									}
									out.close();
									cacheObj = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path);
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
					} else {

					}
					
					try {
						if (openAsVirtualStack) {
							dropUniverse.setFlipXonImport(true);
							int s = this.dtde.getDropAction();
							dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
						} else {
							int s = this.dtde.getDropAction();
							dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
						}
					} catch (Exception e) {
						dropUniverse = new Image3DUniverse();
								dropUniverse.show(false);
							while (dropUniverse.getWindow() == null) IJ.wait (10);
						if (openAsVirtualStack) {
							dropUniverse.setFlipXonImport(true);
							int s = this.dtde.getDropAction();
							dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
						} else {
							int s = this.dtde.getDropAction();
							dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
						}
					}
					//ImageJ3DViewer.lock();


				} else if (( ((String)nextItem).toLowerCase().trim().equals("or"))){
					or = true;
				} else if (( ((String)nextItem).toLowerCase().trim().contains("trace"))){
					traceLineages = true;
					traceForward = true;
					traceBackward = true;
					if (( ((String)nextItem).toLowerCase().trim().contains("back"))){
						traceForward = false;
						traceBackward = true;
					}
					if (( ((String)nextItem).toLowerCase().trim().contains("forward"))){
						traceForward = true;
						traceBackward = false;
					}
				}else {
					String cellColorCode = (((String)nextItem).split(" ").length>1?((String)nextItem).split(" ")[((String)nextItem).split(" ").length-1].trim():"");
					String s = ((String)nextItem);
					String ss = s.split(" ")[0].replaceAll("(?<!(\\.))\\*", "\\.\\*").trim();
					rawCellSet.add(ss.toUpperCase().trim());  //to search with
					//						IJ.log(ss +" ss1");
					//						IJ.runMacro("waitForUser;");
					cellListString = cellListString + s.trim().replaceAll("\\.(?!\\*)", "*").replaceAll(" .*", "").trim() + ",";  //to show
					if (cellColorCode != "" && impCL!=null) {
						Enumeration bbcKenum = impCL.getBrainbowColors().keys();
						while (bbcKenum.hasMoreElements()) {
							String es = (String) bbcKenum.nextElement();
							//								IJ.showStatus(es);
							IJ.showStatus(s.split(" ")[0].toLowerCase().replace("*",".*").trim());
							if (es.toLowerCase().trim().matches(s.split(" ")[0].toLowerCase().replace("*",".*").trim())) {
								if (cellColorCode != "" && impCL!=null) {
									if (impCL.getBrainbowColors().get(es.toLowerCase().trim())!=null) {
										if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB()) == null) {
											//												IJ.showStatus("NO OVERLAP");
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
										} else {
											//												IJ.showStatus("OVERLAP");
											Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB());
											Color nuevo = Colors.decode(cellColorCode, Color.white);
											int newRed = old.getRed()+nuevo.getRed(); 
											int newGreen = old.getGreen()+nuevo.getGreen(); 
											int newBlue = old.getBlue()+nuevo.getBlue();
											int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
											Color mix = new Color(255*newRed/newMax, 
													255*newGreen/newMax, 
													255*newBlue/newMax);
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), mix);
										}
									}
									//										IJ.showStatus("newdropcolor");
								}
							}
						}
					}
					ArrayList<String> ssHits = new ArrayList<String>();
					ssHits.add(ss);
					if (traceLineages && traceForward) {
						for (int l = 0; l < partsListLogLines2.length; l++) {
							partsListLogLines2[l] = partsListLogLines2[l]
									.replace("</a></td><td>", ":")
									.replace("</td><td>", ";")
									.replaceAll("\\.", "");
							if (ss.toUpperCase().trim().matches("EMS"))
								ss = "(E|MS)";
							if (ss.toUpperCase().trim().matches("P0"))
								ss = "(P0|AB|EMS|E|MS|C|D)";
							if (ss.toUpperCase().trim().matches("P1"))
								ss = "(P0p|EMS|E|MS|C|D)";
							if (ss.toUpperCase().trim().matches("P2"))
								ss = "(P0pp|C|D)";
							if (ss.toUpperCase().trim().matches("P3"))
								ss = "(P0ppp|D)";
							if (ss.toUpperCase().trim().matches("P4"))
								ss = "(P0pppp)";

							//									IJ.log(ss +" ss2");
							//									IJ.runMacro("waitForUser;");

							if (partsListLogLines2[l].startsWith("begets ")
									&& (partsListLogLines2[l].toLowerCase().matches(".*:"+ss.toLowerCase().replace("*", "")+".*;.*")
											||	partsListLogLines2[l].toLowerCase().matches(".*:.*;.*"+ss.toLowerCase().replaceAll("(?<!(\\.))\\*", "\\.\\*")+".*") )) {
								String as = partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "");
								//									IJ.log(as + " as");
								//									IJ.runMacro("waitForUser;");
								rawCellSet.add(as);
								if (cellColorCode != "" && impCL!=null) {
									if (impCL.getBrainbowColors().get(as.toLowerCase().trim())!=null) {
										if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB()) == null) {
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
										} else {
											Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB());
											Color nuevo = Colors.decode(cellColorCode, Color.white);
											int newRed = old.getRed()+nuevo.getRed(); 
											int newGreen = old.getGreen()+nuevo.getGreen(); 
											int newBlue = old.getBlue()+nuevo.getBlue();
											int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
											Color mix = new Color(255*newRed/newMax, 
													255*newGreen/newMax, 
													255*newBlue/newMax);
											impCL.droppedCellColors.put(impCL.getBrainbowColors().get(as.toLowerCase().trim()).getRGB(), mix);
										}
									}
									//										IJ.showStatus("newdropcolor");
								}
								//										cellListString = cellListString + as + ",";
							}
							if (partsListLogLines2[l].startsWith("begets ")
									&& (partsListLogLines2[l].toLowerCase().replaceAll(".*rel=nofollow>", "").replaceAll(":.*", "").matches(s.split(" ")[0].replaceAll("(?<!(\\.))\\*", "\\.\\*").toLowerCase().trim()+".*")
											||	partsListLogLines2[l].toLowerCase().matches(".*:.*;.*"+s.split(" ")[0].replaceAll("(?<!(\\.))\\*", "\\.\\*").toLowerCase().trim()+".*") )) {
								ss = partsListLogLines2[l].toLowerCase().replaceAll(".*:", "").replaceAll(";.*", "");
								IJ.showStatus(s.split(" ")[0].replaceAll("(?<!(\\.))\\*", "\\.\\*").trim()+" "+ss);
								ssHits.add(ss);
								//									IJ.runMacro("waitForUser;");
							}
						}
					}
					if (traceLineages) {
						for (String ns:ssHits) {
							for (JCheckBox cbq:impCL.getJCheckBox()) {
								if ((traceForward 
										&& (cbq.getName().toUpperCase().trim().startsWith(ns.toUpperCase().trim())
												|| (ns.toUpperCase().matches("P1") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
												|| (ns.toUpperCase().matches("P2") && cbq.getName().toUpperCase().matches("P.|C.*|D.*|Z."))
												|| (ns.toUpperCase().matches("P3") && cbq.getName().toUpperCase().matches("P.|D.*|Z."))
												|| (ns.toUpperCase().matches("P4") && cbq.getName().toUpperCase().matches("P.|Z."))
												|| (ns.toUpperCase().matches("EMS") && cbq.getName().toUpperCase().matches("E.*|MS.*"))))
												||(traceBackward
														&& (ns.toUpperCase().trim().startsWith(cbq.getName().toUpperCase().trim())
																|| (cbq.getName().toUpperCase().matches("P1") && ns.toUpperCase().matches("P.|C.*|D.*|E.*|MS.*|Z."))
																|| (cbq.getName().toUpperCase().matches("P2") && ns.toUpperCase().matches("P.|C.*|D.*|Z."))
																|| (cbq.getName().toUpperCase().matches("P3") && ns.toUpperCase().matches("P.|D.*|Z."))
																|| (cbq.getName().toUpperCase().matches("P4") && ns.toUpperCase().matches("P.|Z."))
																|| (cbq.getName().toUpperCase().matches("EMS") && ns.toUpperCase().matches("E.*|MS.*"))))
										) {
									rawCellSet.add(cbq.getName().toUpperCase().trim());  //to search with
									Enumeration bbcKenum = impCL.getBrainbowColors().keys();
									while (bbcKenum.hasMoreElements()) {
										String es = (String) bbcKenum.nextElement();
										//								IJ.showStatus(es);
										IJ.showStatus(cbq.getName().trim());
										if (es.toLowerCase().trim().matches(cbq.getName().toLowerCase().trim())) {
											if (cellColorCode != "" && impCL!=null) {
												if (impCL.getBrainbowColors().get(es.toLowerCase().trim())!=null) {
													if (impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB()) == null) {
														//												IJ.showStatus("NO OVERLAP");
														impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), Colors.decode(cellColorCode, Color.white));
													} else {
														//												IJ.showStatus("OVERLAP");
														Color old = impCL.droppedCellColors.get(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB());
														Color nuevo = Colors.decode(cellColorCode, Color.white);
														int newRed = old.getRed()+nuevo.getRed(); 
														int newGreen = old.getGreen()+nuevo.getGreen(); 
														int newBlue = old.getBlue()+nuevo.getBlue();
														int newMax = Math.max(newBlue, Math.max(newRed, newGreen));
														Color mix = new Color(255*newRed/newMax, 
																255*newGreen/newMax, 
																255*newBlue/newMax);
														impCL.droppedCellColors.put(impCL.getBrainbowColors().get(es.toLowerCase().trim()).getRGB(), mix);
													}
												}
												//										IJ.showStatus("newdropcolor");
											}
										}
									}
								}
							}

						}
					}
					//						IJ.runMacro("waitForUser;");
					//							IJ.showStatus("newdropcolor");
				}	
			}
			else if (nextItem!=null && ( ((File)nextItem).getPath().toLowerCase().endsWith(".mov") 
					|| ((File)nextItem).getPath().toLowerCase().endsWith(".avi"))) 
				pathList = pathList + ((File)nextItem).getPath() +  ((((File)nextItem).getPath().toLowerCase().endsWith(".mov") && (iterator.hasNext()  || openAsVirtualStack))? "|": "");
			else if (nextItem!=null && ((File)nextItem).getPath().toLowerCase().contains("scene.scn")) {
				if (IJ.is64Bit())
					MQTVSSceneLoader64.runMQTVS_SceneLoader64( ((File)nextItem).getPath() );
				else
					MQTVSSceneLoader64.runMQTVS_SceneLoader64( ((File)nextItem).getPath() );

			}else if (nextItem!=null && ((File)nextItem).getPath().toLowerCase().endsWith(".obj")) {
				if (IJ.getInstance()!=null) {
					if ( this.dtde.getDropTargetContext().getDropTarget().getComponent() == IJ.getInstance().getStatusBar()){
						if (freshDrop){
							dropUniverse = null;
							dropImp = null;
							setImp(dropImp);
						}
					}
				}

				if (dropUniverse == null) {
					dropUniverse = new Image3DUniverse();
					IJ.wait(100);  //This little pause is needed not crash on windows
					dropUniverse.show(false);
							dropUniverse.getWindow().setDragAndDrop(DragAndDrop.this);
							IJ.getInstance().setDragAndDrop(new DragAndDrop());
							dropUniverse.getWindow().getDragAndDrop().addDropTarget(dropUniverse.getCanvas());
					while (dropUniverse.getWindow() == null) IJ.wait (10);
					dropImp = dropUniverse.getWindow().getImagePlus();
					setImp(dropImp);
				}

				Window dropWin = null;
				if (dropUniverse != null)
					dropWin = dropUniverse.getWindow();
				

				freshDrop = false;
				
				String path = ((File) nextItem).getPath();
				File file = new File(path) ;
				BufferedReader in = null;
				PrintWriter out = null;

				
				if (path.startsWith("/Volumes/GLOWORM_DATA/")
						|| path.contains("\\GLOWORM_DATA\\")) {	

					File cacheFile = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+path);
					if (!(new File(cacheFile.getParent())).canWrite()) {
						//Crappy loop seems necessary for windows...							
						while (!(new File(cacheFile.getParent())).mkdirs()) { 
							while (!(new File(new File(cacheFile.getParent()).getParent())).mkdirs()) {
								while (!(new File(new File(new File(cacheFile.getParent()).getParent()).getParent())).mkdirs()) {
									while (!(new File(new File(new File(new File(cacheFile.getParent()).getParent()).getParent()).getParent()).mkdirs())) {

									}
								}
							}
						}
					}
					if (cacheFile.canRead()) {
//						try {
//							in = new BufferedReader(new FileReader(cacheFile));
							nextItem = cacheFile;
//						} catch (FileNotFoundException e) {
//							// TODO Auto-generated catch block
//							e.printStackTrace();
//						}
					}else {
						byte[] buf = RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replace(".obj", ".mtl").replaceAll("%2B", "\\+").replaceAll("%25", "%").replace("|", ""));
						
						in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf)));
						try {
							out = new PrintWriter(
									new BufferedWriter(
											new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path.replace(".obj", ".mtl")) ), true);
							String line = "";
							if (line != null) {
								while (line != null && !line.contains("End of parameter list")) {
									line = in.readLine();
									if (out!=null)
										out.println(line);
								}
							}
							out.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						
						in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
								RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path.replaceAll("%2B", "\\+").replaceAll("%25", "%").replace("|", "")))));
						try {
							out = new PrintWriter(
									new BufferedWriter(
											new FileWriter(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path) ), true);
							String line = "";
							if (line != null) {
								while (line != null && !line.contains("End of parameter list")) {
									line = in.readLine();
									if (out!=null)
										out.println(line);
								}
							}
							out.close();
							nextItem = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				} else {

				}
				
				try {
					if (openAsVirtualStack) {
						dropUniverse.setFlipXonImport(true);
						int s = this.dtde.getDropAction();
						dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);
					} else {
						int s = this.dtde.getDropAction();
//						IJ.log(""+s);
						dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);     //this call picked when dropping either a single obj file or a mixed folder with objs and others
					}
				} catch (Exception e) {
					dropUniverse = new Image3DUniverse();
							dropUniverse.show(false);
					while (dropUniverse.getWindow() == null) IJ.wait (10);
					if (openAsVirtualStack) {
						dropUniverse.setFlipXonImport(true);
						int s = this.dtde.getDropAction();
						dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);

					} else {
						dropUniverse.setFlipXonImport(false);
						int s = this.dtde.getDropAction();
						dropUniverse.addContentLater(((File)nextItem).getPath(), null, s==1||s==1073741824?true:false);

					}
				}
				//ImageJ3DViewer.lock();

			} else if (nextItem!=null && ((File)nextItem).getPath().endsWith("ColorLegend.lgd")){
				ColorLegend cl;
				if (new File(((File)nextItem).getPath()).exists()){
					Frame[] frames = WindowManager.getImageWindows();
					if (this.dtde.getDropTargetContext().getDropTarget().getComponent() instanceof ImageCanvas3D) {
						for (Frame frame:frames){
							if (frame instanceof ImageWindow3D){
								if (this.dtde.getDropTargetContext().getDropTarget().getComponent() == ((ImageWindow3D)frame).getUniverse().getCanvas()){
									Image3DUniverse i3duniv = (Image3DUniverse)((ImageWindow3D)frame).getUniverse();
									dropImp = i3duniv.getWindow().getImagePlus();
									setImp(dropImp);
								}
							}
						}
					}
					cl = new ColorLegend(imp, IJ.openAsString(((File)nextItem).getPath()));
					impCL = imp.getRoiManager().getColorLegend();
					IJ.showStatus("");
					dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);

					nDrops--;
					return;
				} else
					IJ.showStatus("badpath");
			} else if (nextItem!=null && IJ.isWindows() && ((File)nextItem).getPath().endsWith(".url")){
				IJ.log(nextItem.toString());
				BufferedReader reader=null;
				try {
					reader = new BufferedReader( new FileReader ((File)nextItem));
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String         line = null;
				StringBuilder  stringBuilder = new StringBuilder();
				String         ls = "\n";

				try {
					while( ( line = reader.readLine() ) != null ) {
						stringBuilder.append( line );
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String fs = stringBuilder.toString();
				IJ.log(fs);
				if (fs.contains("http") && fs.contains("scene.scn")) {
					MQTVSSceneLoader64.runMQTVS_SceneLoader64("/Volumes/GLOWORM_DATA/" 
							+ fs.replaceAll("(.*MOVIE=)(.*scene.scn)(.*)", "$2"));
				}

			} else if (nextItem!=null && IJ.isLinux()){
				IJ.log(nextItem.toString());
				BufferedReader reader=null;
				try {
					reader = new BufferedReader( new FileReader ((File)nextItem));
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String         line = null;
				StringBuilder  stringBuilder = new StringBuilder();
				String         ls = "\n";

				try {
					while( ( line = reader.readLine() ) != null ) {
						stringBuilder.append( line );
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String fs = stringBuilder.toString();
				IJ.log(fs);
				if (fs.contains("http") && fs.contains("scene.scn")) {
					MQTVSSceneLoader64.runMQTVS_SceneLoader64("/Volumes/GLOWORM_DATA/" 
							+ fs.replaceAll("(.*MOVIE=)(.*scene.scn)(.*)", "$2"));
				}

			} else
				openFile((File)nextItem);

		}
		if (newSubDropsList.size()>0 ) {
			while (this.getIterator()!=null && this.getIterator().hasNext())
				IJ.wait(100);
			this.setIterator(newSubDropsList.iterator());
			if (this.getIterator()!=null) {
				Thread thread = new Thread(this, "DrawAndSubDrop");
				thread.setPriority(Math.max(thread.getPriority()-1, Thread.MIN_PRIORITY));
				thread.start();
			}
			if (dropImp == null) {
				setImp(dropImp);
				return;
			}
			dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
			dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
			//				IJ.log(nDrops+" nDrops");
			nDrops--;
			return;
		}

		if (pathList != "") {
			if (IJ.debugMode) IJ.log( pathList);
			if (!pathList.contains("/Volumes/GLOWORM_DATA/")) {
				if (pathList.toLowerCase().endsWith(".avi")) {
					if (openAsVirtualStack)
						IJ.run("AVI...", "select="+pathList+" use");
					else
						IJ.run("AVI...", "select="+pathList+"");

				} else {
					new QTMovieOpenerMultiMod(pathList);
				}
			} else {
				MQTVSSceneLoader64.runMQTVS_SceneLoader64(pathList.replaceAll("%2B", "\\+").replaceAll("%25", "%"));
			}
			if (dropImp == null) {
				setImp(dropImp);
				return;
			}
			dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
			dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
			//				IJ.log(nDrops+" nDrops");
			nDrops--;
			return;
		}

		if (rawCellSet.size()!=0) {
			cellSets.add(rawCellSet);
		}	

		if (cellListString.endsWith(","))
			cellListString = cellListString.substring(0, cellListString.length()-1).toUpperCase();
		if (imp == null) 
			return;

		if (dropImp != null && dropUniverse == null) {
			if (dropImp.getCanvas().droppedGeneUrls != null && dropImp.getCanvas().droppedGeneUrls.endsWith("&"))
				dropImp.getCanvas().droppedGeneUrls = dropImp.getCanvas().droppedGeneUrls.substring(0, dropImp.getCanvas().droppedGeneUrls.length()-1);
			dropImp.getCanvas().droppedGeneUrls = (dropImp.getCanvas().droppedGeneUrls != "" ?dropImp.getCanvas().droppedGeneUrls+(cellListString.trim() != ""?"\nand_":""):"") 
					+ (cellListString.trim() != ""?"cell(s):":"")+cellListString + "\n";
		}
		for (int cs=0; cs < cellSets.size()-1; cs++) {
			if (or)
				cellSets.get(0).addAll(cellSets.get(cs));
			else
				cellSets.get(0).retainAll(cellSets.get(cs));
		}
		if (cellSets.size() > 0) {
			cellSets.get(0).addAll(cellSets.get(cellSets.size()-1));


			for (String s:cellSets.get(0)) {
				cellsRegex = cellsRegex + s + (s.matches(embSymExcludedRegex)?"":symAxesRegex+"?") 
						+"|"
						+ synPrefix 
						+ s + (s.matches(embSymExcludedRegex)?"":symAxesRegex+"?") 
						+ synSuffix 
						+"|";
			}
			if (cellsRegex.endsWith("|"))
				cellsRegex= cellsRegex.substring(0, cellsRegex.length()-1);
			cellsRegex= cellsRegex + finalSuffix;
			//				IJ.log(cellsRegex);

			if (dropImp!=null  && (!dropImp.isSketch3D() || (dropImp != imp && IJ.altKeyDown()))) {
				ColorLegend cl = imp.getRoiManager().getColorLegend();
				String hideState = ""; 
				if (cl != null) {
					hideState = cl.getChoice().getSelectedItem();
					cl.getChoice().select("Hide Checked");
					cl.itemStateChanged(new ItemEvent(cl.getChoice(), ItemEvent.ITEM_STATE_CHANGED, cl.getChoice(), ItemEvent.SELECTED));
					for (JCheckBox cb:cl.getJCheckBox()){
						cb.setSelected(false);
					}
				}
				if (dropUniverse == null) {
					imp.getRoiManager().getTextSearchField().setText(cellsRegex);
					imp.getRoiManager().setSearching(true);
					imp.getRoiManager().actionPerformed(new ActionEvent(imp.getRoiManager().getTextSearchField(),0,"",0,0));
					Graphics g = dropImp.getCanvas().getGraphics();
					long timeLast = 0;
					long timeNow = 0;

					while (imp.getRoiManager().isSearching()) {
						timeNow = System.currentTimeMillis();
						if (timeNow > timeLast + 100) {
							timeLast = timeNow;
							if (imp.getCanvas().messageRois.containsKey("Finding tags from drop"))
								imp.getCanvas().messageRois.remove("Finding tags from drop");
							{
								dropImp.getCanvas().droppedGeneUrls = (or?dropImp.getCanvas().droppedGeneUrls.replace("&", "OR"):dropImp.getCanvas().droppedGeneUrls);
								messageRoi = new TextRoi(dropImp.getCanvas().getSrcRect().x, dropImp.getCanvas().getSrcRect().y,
										"   Finding tags that match\n   "+ "these pattern(s):\n   ..." 
												+ imp.getRoiManager().getListModel().getSize() + " found."
												+ "\n" + dropImp.getCanvas().droppedGeneUrls);

								((TextRoi) messageRoi).setCurrentFont(g.getFont().deriveFont((float) (dropImp.getCanvas().getSrcRect().width/16)));
								messageRoi.setStrokeColor(Color.black);
								messageRoi.setFillColor(Colors.decode("#99ffdddd",
										dropImp.getCanvas().getDefaultColor()));

								dropImp.getCanvas().messageRois.put("Finding tags from drop", messageRoi);
								dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
							} 
						}
					} 
					dropImp.getCanvas().messageRois.remove("Finding tags from drop");
					dropImp.getCanvas().paintDoubleBuffered(g);

					if (doSketch3D) {
						imp.getRoiManager().setControlKeyDown(true);
						imp.getRoiManager().sketch3D(imp.getCanvas());
						dropImp.getCanvas().droppedGeneUrls = "";
					}
					imp.getRoiManager().setBusy(false);
					if (cl != null) {
						cl.getChoice().select(hideState);
						cl.itemStateChanged(new ItemEvent(cl.getChoice(), ItemEvent.ITEM_STATE_CHANGED, cl.getChoice(), ItemEvent.SELECTED));
					}
				}else {
					dropUniverse.getContent3DManager().getTextSearchField().setText(cellsRegex);
					dropUniverse.getContent3DManager().setSearching(true);
					dropUniverse.getContent3DManager().actionPerformed(new ActionEvent(dropUniverse.getContent3DManager().getTextSearchField(),0,"",0,0));

				}
			} else { //dropImp.isSketch3D()
				if (imp.getRoiManager().getColorLegend() !=null) {
					imp.getRoiManager().getColorLegend().checkFromCellsRegex(dropImp, cellsRegex.substring(3));

					Graphics g = dropImp.getCanvas().getGraphics();
					dropImp.getCanvas().droppedGeneUrls = "";
					dropImp.getCanvas().messageRois.remove("Finding tags from drop");
					dropImp.getCanvas().paintDoubleBuffered(g);
				}
				imp.getRoiManager().setBusy(false);
			}
		}
		if (dropImp == null) {
			setImp(dropImp);
			return;
		}
		dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
		dropImp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
		//			IJ.log(nDrops+" nDrops");
		nDrops--;
		return;
	}

	/** Open a URL. */
	private void openURL(String url) {
		if (IJ.debugMode) IJ.log("DragAndDrop.openURL: "+url);
		if (url!=null)
			IJ.open(url);
	}

	/** Open a file. If it's a directory, ask to open all images as a sequence in a stack or individually. */
	public void openFile(File f) {
		if (IJ.debugMode) IJ.log("DragAndDrop.openFile: "+f);
		try {
			if (null == f) return;
			String path = f.getPath();
			if (f.exists()) {
				if (f.isDirectory())
					openDirectory(f, path);
				else {
					if (openAsVirtualStack && (path.endsWith(".tif")||path.endsWith(".TIF")))
						(new FileInfoVirtualStack()).run(path);
					else if  (path.toLowerCase().endsWith(".mov") || path.toLowerCase().endsWith(".avi")) {
						//							new QTMovieOpenerMultiMod(path);
						RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
								false, true, true, false, true, false, false, false, false);
						rmqtvsh.getImagePlus().getWindow().setVisible(true);
						if (dropImp == null) {
							setImp(dropImp);
							return;
						}
						dropImp.getCanvas().messageRois.remove("Pending drop "+nDrops);
						dropImp.getCanvas().paintDoubleBuffered(getImp().getCanvas().getGraphics());
						//							IJ.log(nDrops+" nDrops");
						nDrops--;
						return;
					} else if (path.endsWith(".zip")||path.endsWith(".ZIP")) {
						Frame[] frames = WindowManager.getImageWindows();
						if (this.dtde.getDropTargetContext().getDropTarget().getComponent() instanceof ImageCanvas) {
							for (Frame frame:frames){
								if (frame instanceof ImageWindow){
									if (this.dtde.getDropTargetContext().getDropTarget().getComponent() == ((ImageWindow)frame).getCanvas()){
										dropImp = ((ImageWindow)frame).getImagePlus();
										setImp(dropImp);
										dropImp.getWindow().tagButtonPanel.setVisible(true);
										dropImp.getRoiManager().setVisible(true);
										dropImp.getRoiManager().open(path);
									}
								}
							}
						}

					}else {
						(new Opener()).openAndAddToRecent(path);
					}
					OpenDialog.setLastDirectory(f.getParent()+File.separator);
					OpenDialog.setLastName(f.getName());
				}
			} else {
				if (IJ.debugMode) IJ.log("File not found: " + path);
			}
		} catch (Throwable e) {
			if (!Macro.MACRO_CANCELED.equals(e.getMessage()))
				IJ.handleException(e);
		}
	}

	private void openDirectory(File f, String path) {
		if (path==null) return;
		if (!(path.endsWith(File.separator)||path.endsWith("/")))
			path += File.separator;
		String[] names = f.list();
		names = (new FolderOpener()).trimFileList(names);
		if (names==null)
			return;
		String msg = "Open all "+names.length+" images in \"" + f.getName() + "\" as a stack?";
		GenericDialog gd = new GenericDialog("Open Folder");
		gd.setInsets(10,5,0);
		gd.addMessage(msg);
		gd.setInsets(15,35,0);
		gd.addCheckbox("Convert to RGB", convertToRGB);
		gd.setInsets(0,35,0);
		gd.addCheckbox("Use Virtual Stack", virtualStack);
		gd.enableYesNoCancel();
		gd.showDialog();
		if (gd.wasCanceled()) return;
		if (gd.wasOKed()) {
			convertToRGB = gd.getNextBoolean();
			virtualStack = gd.getNextBoolean();
			String options  = " sort";
			if (convertToRGB) options += " convert_to_rgb";
			if (virtualStack) options += " use";
			IJ.run("Image Sequence...", "open=[" + path + "]"+options);
			DirectoryChooser.setDefaultDirectory(path);
		} else {
			for (int k=0; k<names.length; k++) {
				IJ.redirectErrorMessages();
				if (!names[k].startsWith("."))
					(new Opener()).open(path + names[k]);
			}
		}
		IJ.register(DragAndDrop.class);
	}

	public Iterator getIterator() {
		return iterator;
	}

	public void setIterator(Iterator iterator) {
		this.iterator = iterator;
	}

	public ImagePlus getImp() {
		return imp;
	}

	public void setImp(ImagePlus imp) {
		this.imp = imp;
	}

	public ImagePlus getDropImp() {
		return dropImp;
	}

	public void setDropImp(ImagePlus imp) {
		dropImp = imp;		
	}

	public void setUniverse(DefaultUniverse universe) {
		this.dropUniverse = (Image3DUniverse) universe;
		
	}

	public boolean isWorking() {
		return working;
	}

	public void setWorking(boolean working) {
		this.working = working;
	}
}
