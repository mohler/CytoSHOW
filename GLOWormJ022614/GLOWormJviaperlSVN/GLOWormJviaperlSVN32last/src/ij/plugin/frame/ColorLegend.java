package ij.plugin.frame;
import ij.*;
import ij.plugin.*;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij3d.Content;
import ij3d.ContentInstant;
import ij3d.DefaultUniverse;
import ij3d.Image3DUniverse;
import ij3d.ImageWindow3D;
import ij3d.gui.Content3DManager;
import ij.gui.*;

import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListModel;
import javax.vecmath.Color3f;

import org.vcell.gloworm.MultiQTVirtualStack;

import SmartCaptureLite.r;

/** Displays the CytoSHOW ColorLegend window. */
public class ColorLegend extends PlugInFrame implements PlugIn, ItemListener, ActionListener, MouseMotionListener, WindowFocusListener, MouseListener  {

	public String[] modes = {"Display Both Checked & Unchecked", "Hide Checked", "Show Checked", "Blink Checked"};
	boolean blinkChecked = true;
	boolean showChecked;
	boolean hideChecked;
	private  String moreLabel = "More "+'\u00bb';
	private Choice choice;
	private JCheckBox[] checkbox;
	private Button moreButton;

	private int id;
	private static Point location;
	private PopupMenu pm;
	private RoiManager rm;
	private ImagePlus bbImp;
	private ImagePlus bbImpCopy;
	private static ScheduledThreadPoolExecutor blinkService;
    private ScheduledFuture schfut;
	private static ScheduledThreadPoolExecutor blinkService2;
    private ScheduledFuture schfut2;
	private Color selectColor;
	private ImageProcessor ip;
	private ImageProcessor ipCopy;
	private JCheckBox chosenCB;
	private Color mouseLocColor;
	private Hashtable<String, Color> brainbowColors;
	private Hashtable<Color, JCheckBox> checkboxHash;
	private ImagePlus bb2Imp;
	private ImageJ ij;
	private boolean popupHappened;
	private Color chosenColor;
	private Hashtable<Integer, String> selectedColorRGBs;
	public Button clearButton = new Button("Clear All CheckBoxes");
	private boolean sketchyMQTVS = true;
	private ImageProcessor lastIp;
	private boolean changedCBorChoice;
	public Hashtable<Integer, Color> droppedCellColors = new Hashtable<Integer, Color>();

	public ColorLegend(ImagePlus imp, String clStr) {
		super("Color Legend");
		this.setVisible(false);
		ij = IJ.getInstance();
		bbImp = imp;
		this.rm = bbImp.getRoiManager();
		if (blinkService == null)
			blinkService = new ScheduledThreadPoolExecutor(1);
		if (blinkService2 == null)
			blinkService2 = new ScheduledThreadPoolExecutor(1);
		rm.setColorLegend(this);
		if (bbImp.getCanvas() != null) {
			bbImp.getCanvas().addMouseMotionListener(this);
			bbImp.getCanvas().addMouseListener(this);
		}
		if (bbImp.getStack() instanceof MultiQTVirtualStack) {
			for (int i=0;i<bbImp.getNChannels();i++) {
				if ( ((MultiQTVirtualStack) bbImp.getStack()).getVirtualStack(i).getMovieName()
						.startsWith("Sketch3D")) {
					sketchyMQTVS = true;
				}
			}
		}
//		WindowManager.addWindow(this);
		ScrollPane fsp = new ScrollPane();
		GridBagLayout fspgridbag = new GridBagLayout();
		GridBagConstraints fspc = new GridBagConstraints();
		fspgridbag.setConstraints(fsp, fspc);
		Panel fspp = new Panel(fspgridbag);
		fsp.add(fspp,fspc);

		fspp.setLayout(fspgridbag);
		
		int y = 0;
		fspc.gridx = 0;
		fspc.gridy = y++;
		fspc.gridwidth = 1;
		fspc.fill = GridBagConstraints.BOTH;
		fspc.anchor = GridBagConstraints.CENTER;
		int margin = 32;
		if (IJ.isVista())
			margin = 40;
		else if (IJ.isMacOSX())
			margin = 18;
		fspc.insets = new Insets(0, 0, 0, 0);
		brainbowColors = new Hashtable<String,Color>();

		checkboxHash = new Hashtable<Color,JCheckBox>();
		int count=0;
		int panelWidth =0;
		int panelHeight =0;
		if (clStr != null) {
			String[] clLines = clStr.split("\n");
			checkbox = new JCheckBox[clLines.length];
			for (int i=0; i<clLines.length; i++) {		
				//			IJ.log(clLines[i]);
				if (clLines[i].equals(""))
					continue;
				checkbox[i] = new JCheckBox();
				((JCheckBox)checkbox[i]).setSize(150, 10);
				checkbox[i].setLabel(clLines[i].split(",")[0].length()<20?clLines[i].split(",")[0]:clLines[i].split(",")[0].substring(0, 20) + "...");
				checkbox[i].setName(clLines[i].split(",")[0]);			
				if (!clLines[i].split(",")[0].equals(clLines[i].split(",")[1])) {
					if (true || clLines[i].split(",")[1].startsWith("#")) {
						if (imp.getWindow()!=null && imp.getWindow() instanceof ImageWindow) {
							brainbowColors.put(clLines[i].split(",")[0].toLowerCase().split("[_-]")[0], Colors.decode(clLines[i].split(",")[1], Color.white));
						}else {
							brainbowColors.put(clLines[i].split(",")[0].toLowerCase(), Colors.decode(clLines[i].split(",")[1], Color.white));
						}
					}
				} else if (clLines[i].length() >2){
					if (true || clLines[i].split(",")[2].startsWith("#")) {
						if (imp.getWindow()!=null && imp.getWindow() instanceof ImageWindow) {
							brainbowColors.put(clLines[i].split(",")[0].toLowerCase().split("[_-]")[0], Colors.decode(clLines[i].split(",")[2], Color.white));
						}else {
							brainbowColors.put(clLines[i].split(",")[0].toLowerCase(), Colors.decode(clLines[i].split(",")[1], Color.white));
						}
					}
//					brainbowColors.put(checkbox[i].getName().toLowerCase(), Colors.decode(clLines[i].split(",")[2], Color.white));	
//					checkbox[i].setBackground(Colors.decode(clLines[i].split(",")[2], Color.white));
//					checkboxHash.put(Colors.decode(clLines[i].split(",")[2], Color.white), checkbox[i]);
				}
				
				checkbox[i].setFont(Menus.getFont().deriveFont(8));
				//			IJ.log("WHATUP?");

				if (!clLines[i].split(",")[0].contains("NOTE:")) {
					fspp.add(checkbox[i],fspc);
					if (count==0) {
						panelWidth = panelWidth + checkbox[i].getWidth();
						count++;
						panelHeight = panelHeight+checkbox[i].getHeight();
						fspc.gridx++;
					} else if (count>=1 && fspc.gridx < 4){
						if (count < 4){
							panelWidth = panelWidth + checkbox[i].getWidth();
							fspc.gridx++;
							//						count=0;
						}
					} else {
						fspc.gridy++;
						fspc.gridx=0;
					}
					checkbox[i].addItemListener(this);
					checkbox[i].addMouseListener(this);
				}
			}

			fspc.gridx =0;
			fspc.gridy= clLines.length/4+1;		
			choice = new Choice();
			for (int i=0; i<modes.length; i++)
				choice.addItem(modes[i]);
			choice.select(3);
			choice.addItemListener(this);
			//		if (sketchyMQTVS)
			//			choice.setEnabled(false);
			update();
			addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts

			setLayout(new BorderLayout());
			add(choice, BorderLayout.PAGE_START);
			Panel p = new Panel();
			p.setLayout(new BorderLayout());
			p.add(clearButton, BorderLayout.NORTH);
			clearButton.addActionListener(this);
			p.add(fsp, BorderLayout.CENTER);
			add(p, BorderLayout.CENTER);

			//		hideChecked = true;

			this.setResizable(true);
			this.pack();
			this.setSize(fspp.getWidth()+30, fspp.getHeight()+30+choice.getHeight());
			if (location==null) {
				GUI.center(this);
				location = getLocation();
			} else
				setLocation(location);
			//		this.setVisible(true);
			if (imp.getWindow()!=null ){
				if (imp.getWindow() instanceof ImageWindow3D) {
					DefaultUniverse univ = ((ImageWindow3D)imp.getWindow()).getUniverse();
					Hashtable<String, ContentInstant> contentInstants = ((Image3DUniverse)univ).getContent3DManager().getCIs();
					ListModel lm = ((Image3DUniverse)univ).getContent3DManager().getListModel();
					for (int index =0; index < lm.getSize(); index++) {
						String hashkey = (String) lm.getElementAt(index);
						ContentInstant ci = contentInstants.get(hashkey);
						String fixedName = ci.getName().replace("_#0_#0", "").replace("BWM-", "BWM") ;


						ArrayList<String> hitTests = new ArrayList<String>();

						hitTests.add( fixedName.split("-")[0].toLowerCase());
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^(.*by)(.*)$", "$2"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^(.*in)(.*)$", "$2"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^(.*in)(.*)(@.*)$", "$2"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll(".*_(.*)[_$].*","$1"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^(.*)undefined.*(_pre)*", "$1"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^(.*)chemical.*(_pre)*", "$1"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^.*(electrical|&)(.*)$","$2"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^(.*)electrical.*$","$1"));
						hitTests.add( fixedName.split("-")[0].toLowerCase().replaceAll("^(.*)_.*", "$1")); 		
						hitTests.add( fixedName.toLowerCase());
						hitTests.add( fixedName.toLowerCase().replaceAll("^(.*by)(.*)$", "$2"));
						hitTests.add( fixedName.toLowerCase().replaceAll("^(.*in)(.*)$", "$2"));
						hitTests.add( fixedName.toLowerCase().replaceAll("^(.*in)(.*)(@.*)$", "$2"));
						hitTests.add( fixedName.toLowerCase().replaceAll(".*_(.*)[_$].*","$1"));
						hitTests.add( fixedName.toLowerCase().replaceAll("^(.*)undefined.*(_pre)*", "$1"));
						hitTests.add( fixedName.toLowerCase().replaceAll("^(.*)chemical.*(_pre)*", "$1"));
						hitTests.add( fixedName.toLowerCase().replaceAll("^.*(electrical|&)(.*)$","$2"));
						hitTests.add( fixedName.toLowerCase().replaceAll("^(.*)electrical.*$","$1"));

						int testNumber=0;
						while (testNumber < hitTests.size() 
								&& 
								brainbowColors.get(hitTests.get(testNumber))==null)
							testNumber++;
						final int finalTestNumber = testNumber;
						
						Set<Thread> threadsInJVM = Thread.getAllStackTraces().keySet();
						int nbThreads = threadsInJVM.size();
						while (nbThreads >250) {
							IJ.wait(10);
							threadsInJVM = Thread.getAllStackTraces().keySet();
							nbThreads = threadsInJVM.size();
						}

						if (testNumber<hitTests.size()) {
							new Thread(new Runnable() {
								public void run() {
									Color hitColor = brainbowColors.get(hitTests.get(finalTestNumber));
									ci.setColor(new Color3f(hitColor));
									float fAlpha = hitColor.getRGBComponents(null)[3];
									ci.setTransparency(1.0f - fAlpha);
									IJ.wait(0);
								}
							}).start();
							//						IJ.log(fixedName+" "+ci.getColor().get());

							//					!!! succeeded to FIX THIS ONE NEXT!!!!!
							if (fixedName.contains("_post")) {

								int postSynRank = Integer.parseInt(fixedName.replaceAll("^.*post(\\d*)","$1"));
								String buildHitTest = "^.*(undefined|chemical)";
								for (int psr=1;psr<=postSynRank;psr++) {
									buildHitTest = buildHitTest+ (psr<postSynRank?".*?[\\&~]":"(.*?)[\\&~]");
								}
								buildHitTest = buildHitTest+ ".*post.*$";
								final String hitTestPost = fixedName.split("-")[0].toLowerCase().replaceAll(buildHitTest, "$2");

								//THIS CAN CAUSE A TRAP IF THERE ARE MORE THAN 100 THREADS THAT DO NOT RESOLVE QUICKLY
								while (nbThreads >100) {
									IJ.wait(10);
									threadsInJVM = Thread.getAllStackTraces().keySet();
									nbThreads = threadsInJVM.size();
								}

								if(brainbowColors.get(hitTestPost)!=null) {
									if (hitTestPost.contains("bwm"))
										IJ.wait(1);
									new Thread(new Runnable() {
										public void run() {
											Color hitTestPostColor = brainbowColors.get(hitTestPost);
											ci.setColor(new Color3f(hitTestPostColor));
											float fAlpha = hitTestPostColor.getRGBComponents(null)[3];
											ci.setTransparency(1.0f - fAlpha);
											IJ.wait(0);
										}
									}).start();
									//							IJ.log(fixedName+" "+hitTestPost+" "+ColorLegend.this.getBrainbowColors().get(hitTestPost));
								}
							}
						}

						IJ.showStatus(index+"/"+lm.getSize()+" contents color-adjusted.");
					}
					JList list = ((Image3DUniverse)univ).getContent3DManager().getList();
					int listfirstposition = list.getFirstVisibleIndex();
					int listlastposition = list.getLastVisibleIndex();

					((Image3DUniverse)univ).getContent3DManager().close();
					((Image3DUniverse)univ).getContent3DManager().setVisible(true);
					list.ensureIndexIsVisible(listlastposition);
					list.ensureIndexIsVisible(listfirstposition);

					IJ.showStatus("All content color-adjusted per ColorLegend");

				} else {  //ImageWindow rather than ImageWindow3D

					Hashtable<String, Roi> contentInstants = imp.getRoiManager().getROIs();
					ListModel lm = imp.getRoiManager().getListModel();
					for (int index =0; index < lm.getSize(); index++) {
						String hashkey = (String) lm.getElementAt(index);
						Roi roi = contentInstants.get(hashkey);
						String fixedName = roi.getName().replace("_#0_#0", "").replace("BWM-", "BWM");


						ArrayList<String> hitTests = new ArrayList<String>();

						hitTests.add( fixedName.split(" =.* \"_")[0].toLowerCase().replace("\"", ""));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", ""));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^(.*by)(.*)$", "$2"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^(.*in)(.*)$", "$2"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^(.*in)(.*)(@.*)$", "$2"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll(".*_(.*)[_$].*","$1"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^(.*)undefined.*(_pre)*", "$1"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^(.*)chemical.*(_pre)*", "$1"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^.*(electrical|&)(.*)$","$2"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^(.*)electrical.*$","$1"));
						hitTests.add( fixedName.split(" \"_")[0].toLowerCase().replace("\"", "").replaceAll("^(.*)_.*", "$1")); 		
						hitTests.add( fixedName.toLowerCase().replace("\"", ""));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll("^(.*by)(.*)$", "$2"));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll("^(.*in)(.*)$", "$2"));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll("^(.*in)(.*)(@.*)$", "$2"));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll(".*_(.*)[_$].*","$1"));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll("^(.*)undefined.*(_pre)*", "$1"));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll("^(.*)chemical.*(_pre)*", "$1"));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll("^.*(electrical|&)(.*)$","$2"));
						hitTests.add( fixedName.toLowerCase().replace("\"", "").replaceAll("^(.*)electrical.*$","$1"));

						int testNumber=0;
						while (testNumber < hitTests.size() 
								&& 
								brainbowColors.get(hitTests.get(testNumber))==null)
							testNumber++;
						final int finalTestNumber = testNumber;
						
						Set<Thread> threadsInJVM = Thread.getAllStackTraces().keySet();
						int nbThreads = threadsInJVM.size();
						while (nbThreads >200) {
							IJ.wait(10);
							threadsInJVM = Thread.getAllStackTraces().keySet();
							nbThreads = threadsInJVM.size();
						}

						if (testNumber<hitTests.size()) {
							new Thread(new Runnable() {
								public void run() {
									imp.getRoiManager().setRoiFillColor(roi, brainbowColors.get(hitTests.get(finalTestNumber)), true, false);
								}
							}).start();
							//						IJ.log(fixedName+" "+ci.getColor().get());
						}

						//					!!! succeeded to FIX THIS ONE NEXT!!!!!
						if (fixedName.contains("_post")) {

							int postSynRank = Integer.parseInt(fixedName.replaceAll("^.*post(\\d*)","$1"));
							String buildHitTest = "^.*(undefined|chemical)";
							for (int psr=1;psr<=postSynRank;psr++) {
								buildHitTest = buildHitTest+ (psr<postSynRank?".*?[\\&~]":"(.*?)[\\&~]");
							}
							buildHitTest = buildHitTest+ ".*post.*$";
							final String hitTestPost = fixedName.split("-")[0].toLowerCase().replaceAll(buildHitTest, "$2");

							if(brainbowColors.get(hitTestPost)!=null) {
								new Thread(new Runnable() {
									public void run() {
										imp.getRoiManager().setRoiFillColor(roi, brainbowColors.get(hitTestPost), true, false);
									}
								}).start();
								//							IJ.log(fixedName+" "+hitTestPost+" "+ColorLegend.this.getBrainbowColors().get(hitTestPost));
							}
						}

						IJ.showStatus(index+"/"+lm.getSize()+" tags color-adjusted.");
					}
					JList list = imp.getRoiManager().getList();
					int listfirstposition = list.getFirstVisibleIndex();
					int listlastposition = list.getLastVisibleIndex();

					imp.getRoiManager().close();
					imp.getRoiManager().setVisible(true);
					list.ensureIndexIsVisible(listlastposition);
					list.ensureIndexIsVisible(listfirstposition);

					IJ.showStatus("All tags color-adjusted per ColorLegend");

				}
			}
			IJ.wait(0);
		}
	}
	
	
	public ColorLegend(RoiManager rm) {
		super("Color Legend");
		this.setVisible(false);
		ij = IJ.getInstance();
		this.rm = rm;
		if (blinkService == null)
			blinkService = new ScheduledThreadPoolExecutor(1);
		if (blinkService2 == null)
			blinkService2 = new ScheduledThreadPoolExecutor(1);
		bbImp = WindowManager.getCurrentImage();
		bbImp.getCanvas().addMouseMotionListener(this);
		bbImp.getCanvas().addMouseListener(this);
		if (bbImp.getStack() instanceof MultiQTVirtualStack) {
			for (int i=0;i<bbImp.getNChannels();i++) {
				if ( ((MultiQTVirtualStack) bbImp.getStack()).getVirtualStack(i).getMovieName()
						.startsWith("Sketch3D")) {
					sketchyMQTVS = true;
				}
			}
		}
//		WindowManager.addWindow(this);
		ScrollPane fsp = new ScrollPane();
		GridBagLayout fspgridbag = new GridBagLayout();
		GridBagConstraints fspc = new GridBagConstraints();
		fspgridbag.setConstraints(fsp, fspc);
		Panel fspp = new Panel(fspgridbag);
		fsp.add(fspp,fspc);

		fspp.setLayout(fspgridbag);
		
		int y = 0;
		fspc.gridx = 0;
		fspc.gridy = y++;
		fspc.gridwidth = 1;
		fspc.fill = GridBagConstraints.BOTH;
		fspc.anchor = GridBagConstraints.CENTER;
		int margin = 32;
		if (IJ.isVista())
			margin = 40;
		else if (IJ.isMacOSX())
			margin = 18;
		fspc.insets = new Insets(0, 0, 0, 0);

		CompositeImage ci = getImage();
		int nCheckBoxes = ci!=null?ci.getNChannels():3;
		if (nCheckBoxes>CompositeImage.MAX_CHANNELS)
			nCheckBoxes = CompositeImage.MAX_CHANNELS;//		checkbox = new JCheckBox[nCheckBoxes];
		ArrayList<String> fullCellNames = new ArrayList<String>(); 
		if (rm.getFullCellNames()!=null) {
			for (int i =0;i<rm.getFullCellNames().size();i++)
				fullCellNames.add(""+rm.getFullCellNames().get(i)+"");
		} else {
			Roi[] fullRois = rm.getFullRoisAsArray();
			for (int r =0;r<fullRois.length;r++) {
					String[] searchTextChunks = fullRois[r].getName().split("[\"|=]")[1].split(" ");
					String searchText = "";
					for (String chunk:searchTextChunks)
						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
							searchText = searchText + " " + chunk;
					if ( !fullCellNames.contains(searchText.trim())) {
						fullCellNames.add(searchText.trim());				
					}
			}
		}
		if (rm.getColorLegend() != null)
			brainbowColors = rm.getColorLegend().getBrainbowColors();
		else {
			rm.setColorLegend(this);
			brainbowColors = this.getBrainbowColors();
		}
		if (brainbowColors != null) {
			checkbox = new JCheckBox[fullCellNames.size()];
			checkboxHash = new Hashtable<Color,JCheckBox>();
			int count=0;
			int panelWidth =0;
			int panelHeight =0;
			for (int i=0; i<fullCellNames.size(); i++) {
				checkbox[i] = new JCheckBox();
				((JCheckBox)checkbox[i]).setSize(150, 10);
				checkbox[i].setLabel(fullCellNames.get(i).length()<20?fullCellNames.get(i):fullCellNames.get(i).substring(0, 20) + "...");
				checkbox[i].setName(fullCellNames.get(i));			
				checkbox[i].setBackground(brainbowColors.get(fullCellNames.get(i).toLowerCase()));
				if (!fullCellNames.get(i).contains("NOTE:")) {
					checkboxHash.put(brainbowColors.get(fullCellNames.get(i).toLowerCase()), checkbox[i]);
					checkbox[i].setFont(Menus.getFont().deriveFont(8));

					fspp.add(checkbox[i],fspc);
					if (count==0) {
						panelWidth = panelWidth + checkbox[i].getWidth();
						count++;
						panelHeight = panelHeight+checkbox[i].getHeight();
						fspc.gridx++;
					} else if (count>=1 && fspc.gridx < 4){
						if (count < 4){
							panelWidth = panelWidth + checkbox[i].getWidth();
							fspc.gridx++;
							//						count=0;
						}
					} else {
						fspc.gridy++;
						fspc.gridx=0;
					}
					checkbox[i].addItemListener(this);
					checkbox[i].addMouseListener(this);
				}
			}

			fspc.gridx =0;
			fspc.gridy= fullCellNames.size()/4+1;		
			choice = new Choice();
			for (int i=0; i<modes.length; i++)
				choice.addItem(modes[i]);
			choice.select(3);
			choice.addItemListener(this);
			//		if (sketchyMQTVS)
			//			choice.setEnabled(false);

			update();
			addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts

			setLayout(new BorderLayout());
			add(choice, BorderLayout.PAGE_START);
			Panel p = new Panel();
			p.setLayout(new BorderLayout());
			p.add(clearButton, BorderLayout.NORTH);
			clearButton.addActionListener(this);
			p.add(fsp, BorderLayout.CENTER);
			add(p, BorderLayout.CENTER);
			//		hideChecked = true;

			this.setResizable(true);
			this.pack();
			this.setSize(fspp.getWidth()+30, fspp.getHeight()+30+choice.getHeight());
			if (location==null) {
				GUI.center(this);
				location = getLocation();
			} else
				setLocation(location);
			this.setVisible(true);
			show();
		}
	}
	
	public ColorLegend(Content3DManager content3dManager) {
		super("Color Legend");
		//not sure what will get invoked here once this is called...
	}


	public void update() {
	}
	
	void addPopupItem(String s) {
		MenuItem mi=new MenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	CompositeImage getImage() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || !imp.isComposite())
			return null;
		else
			return (CompositeImage)imp;
	}

	public void itemStateChanged(ItemEvent e) {

		Object source = e.getSource();
		if (bbImp!=IJ.getImage()) {
			bbImp = IJ.getImage();
			bbImp.getCanvas().addMouseMotionListener(this);
		}
//		IJ.log(choice.getSelectedItem());		
		ipCopy = bbImp.getStack().getProcessor(bbImp.getSlice()).duplicate();
//		IJ.log(choice.getSelectedItem());
//		IJ.log(choice.getSelectedItem());
		if (source instanceof Choice) {
			
//			IJ.log(choice.getSelectedItem());
			String previousChoice = blinkChecked?"blink":(showChecked?"show":"hide");
			if (choice.getSelectedItem() == "Blink Checked") {
				blinkChecked = true;
				showChecked = false;
				hideChecked = false;
			}else if (choice.getSelectedItem() == "Show Checked") {
				blinkChecked = false;
				showChecked = true;
				hideChecked = false;
			}else if (choice.getSelectedItem() == "Hide Checked" 
						|| choice.getSelectedItem() == "Display Both Checked & Unchecked") {
				blinkChecked = false;
				showChecked = false;
				hideChecked = (choice.getSelectedItem() == "Hide Checked");
			}
			changedCBorChoice = true;
			bbImp.updateAndRepaintWindow();
		}
		if (source instanceof JCheckBox /*&& (!bbImp.equals(rm.getImagePlus()) || sketchyMQTVS)*/) {
//			IJ.showStatus("CLICK");
			changedCBorChoice = true;
//			droppedCellColors.clear();
			bbImp.updateAndRepaintWindow();
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		String command = e.getActionCommand();
		if (command==null) return;
		if (command.equals(moreLabel)) {
			Point bloc = moreButton.getLocation();
			pm.show(this, bloc.x, bloc.y);
		} else if (command.equals("Convert to RGB"))
			IJ.doCommand("Stack to RGB");
		else if (e.getSource() == clearButton) {
			droppedCellColors.clear();
			for (JCheckBox cbq:checkbox) {
				if (cbq.isSelected()) {
					cbq.setSelected(false);
				}
			}
			this.itemStateChanged(new ItemEvent(checkbox[0], ItemEvent.ITEM_STATE_CHANGED, checkbox[0], ItemEvent.SELECTED));
		} else
			IJ.doCommand(command);
	}
		
	public void close() {
		if (this.getRoiManager()==null) {
			super.close();
//			rm.setColorLegend(null);
			location = getLocation();
			dispose();
		} else
			this.setVisible(false);
	}
	
    public void dispose() {
    	while (bbImp != null || bbImpCopy != null) {
    		bbImp = null;
    		bb2Imp=null;
    		bbImpCopy = null;
    	}
    	if(checkbox!=null) {
    		for (JCheckBox cb:checkbox){
    			for (ItemListener il:cb.getItemListeners()){
    				cb.removeItemListener(il);
    			}
    			for (MouseListener ml:cb.getMouseListeners()){
    				cb.removeMouseListener(ml);
    			}

    		}
    	}
        this.removeKeyListener(IJ.getInstance());
        super.dispose();
    }


	public void mouseDragged(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseMoved(MouseEvent e) {

		Object source = e.getSource();
		bb2Imp = ((ImageCanvas2)source).getImage();
		if (rm == null || (bb2Imp == rm.getImagePlus()  && !bb2Imp.getTitle().startsWith("Sketch3D") && !sketchyMQTVS))
			return;

//		IJ.log(" " +e.getX()+" " + e.getY()+" " + ((ColorProcessor)bb2Imp.getIP()).getWidth()+" " + ((ColorProcessor)bb2Imp.getIP()).getHeight());
		
		mouseLocColor = (bb2Imp.getIP() instanceof ColorProcessor?((ColorProcessor)bb2Imp.getIP())
				.getColor(bb2Imp.getCanvas().offScreenX(e.getX()),
						bb2Imp.getCanvas().offScreenY(e.getY())):Color.white);

		if (mouseLocColor.equals(chosenColor)){
			return;
		} else if (mouseLocColor == null){
			return;
		}else if (mouseLocColor.equals(Color.black)) {
			chosenCB = null;
			chosenColor = null;
			return;
		}
//		IJ.showStatus(""+sketchyMQTVS+1);

		for (JCheckBox cbC:checkbox) {
			if (cbC.getBackground().equals(mouseLocColor)){
//				IJ.showStatus(""+sketchyMQTVS+1);
				chosenCB = cbC;
				chosenColor= mouseLocColor;
				if (schfut2 != null)
					schfut2.cancel(true);
				schfut2 = blinkService2.scheduleAtFixedRate(new Runnable()
				{
					public void run()
					{
						if (chosenCB.getBackground().equals(mouseLocColor))
							chosenCB.setBackground(mouseLocColor.darker().darker());
						else
							chosenCB.setBackground(brainbowColors.get(chosenCB.getName().toLowerCase()));
					}
				}, 0, 500, TimeUnit.MILLISECONDS);

			} else{
//				IJ.showStatus(""+sketchyMQTVS+1);
				if (bbImp != null && (bb2Imp == rm.getImagePlus()  
						&& !(bb2Imp.getTitle().startsWith("Sketch3D") || sketchyMQTVS)))
					cbC.setBackground(rm.getMowColors().get(cbC.getName()));
				else {
//					IJ.showStatus(""+sketchyMQTVS+1);
					cbC.setBackground(brainbowColors.get(cbC.getName().toLowerCase()));
				}
			}
		}	
	}

	public void windowGainedFocus(WindowEvent e) {
		if (rm == null)
			return;
		Object source = e.getSource();
		if (bbImp==null || bbImp.getWindow()!=source) {
			bbImp = IJ.getImage();
//			bbImp.getCanvas().addMouseMotionListener(this);
			bbImpCopy = bbImp.getDupImp();
			for (int i=0; i<rm.getNameLists().size(); i++){
				if (bbImp.equals(rm.getCompImps().get(i<rm.getCompImps().size()?i:rm.getCompImps().size()-1))
						|| bbImp.equals(rm.getProjYImps().get(i<rm.getProjYImps().size()?i:rm.getProjYImps().size()-1)) 
						|| bbImp.equals(rm.getProjZImps().get(i<rm.getProjZImps().size()?i:rm.getProjZImps().size()-1))) {
					for (JCheckBox cb:checkbox){
//						cb.setBackground(rm.getBrainbowColors().get(cb.getName()));
						if (rm.getNameLists().get(i).contains(cb.getName())) {
							cb.setLabel(cb.getLabel().replaceAll("[()]", ""));
							cb.setFont(cb.getFont().deriveFont(Font.PLAIN + Font.BOLD ));
						} else{
							cb.setLabel("("+cb.getLabel().replaceAll("[()]", "")+")");
							cb.setFont(cb.getFont().deriveFont(Font.PLAIN + Font.ITALIC));
						}
					}
				} 
			}
		}
		if (bbImpCopy==null && !bbImp.equals(rm.getImagePlus())) {
			bbImpCopy = new ImagePlus("bbImpCopy", bbImp.getStack().getProcessor(bbImp.getCurrentSlice()));
			bbImp.setDupImp(bbImpCopy);	
		}
		this.repaint();
		bbImp.updateAndDraw();
	}

	public void windowLostFocus(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseClicked(MouseEvent e) {
		boolean selectMore = IJ.shiftKeyDown();
		boolean selectLineage = IJ.altKeyDown();
//		IJ.showStatus("mouseclick");
		Object source = e.getSource();
		if (source instanceof JCheckBox)
			return;
		if (bbImp!=null && bbImp.getCanvas()!= (ImageCanvas2)source)
			bbImp=null;
		else {
			bbImp = ((ImageCanvas2)source).getImage();
			if (bbImp.getIP() instanceof ColorProcessor) 
				mouseLocColor = ((ColorProcessor)bbImp.getIP())
				.getColor(bbImp.getCanvas().offScreenX(e.getX()),
					bbImp.getCanvas().offScreenY(e.getY()));
			if (!selectMore && mouseLocColor!=null && !mouseLocColor.equals(Color.black)){
				for (JCheckBox cbq:checkbox) {
					if (cbq.isSelected()) {
						cbq.setSelected(false);
//						this.itemStateChanged(new ItemEvent(cbq,0,null,ItemEvent.DESELECTED));
					}
				}
			}
			JCheckBox cbC = checkboxHash.get(mouseLocColor);
			if (cbC == null)
				cbC = checkboxHash.get(mouseLocColor.darker().darker());
			if (cbC != null) {	
				chosenCB = cbC;
				chosenCB.setSelected(true);
				if (selectLineage) {
					for (JCheckBox cbq:checkbox) {
						if (cbq.getName().trim().contains(cbC.getName().trim())
								|| cbC.getName().trim().contains(cbq.getName().trim())
								|| (cbC.getName().matches("P1") && cbq.getName().matches("P.|C.*|D.*|E.*|MS.*|Z."))
								|| (cbC.getName().matches("P2") && cbq.getName().matches("P.|C.*|D.*|Z."))
								|| (cbC.getName().matches("P3") && cbq.getName().matches("P.|D.*|Z."))
								|| (cbC.getName().matches("P4") && cbq.getName().matches("P.|Z."))
								|| (cbC.getName().matches("EMS") && cbq.getName().matches("E.*|MS.*"))
								|| (cbq.getName().matches("P1") && cbC.getName().matches("P.|C.*|D.*|E.*|MS.*|Z."))
								|| (cbq.getName().matches("P2") && cbC.getName().matches("P.|C.*|D.*|Z."))
								|| (cbq.getName().matches("P3") && cbC.getName().matches("P.|D.*|Z."))
								|| (cbq.getName().matches("P4") && cbC.getName().matches("P.|Z."))
								|| (cbq.getName().matches("EMS") && cbC.getName().matches("E.*|MS.*"))
								) {
							cbq.setSelected(true);
						}
					}
				}
				this.itemStateChanged(new ItemEvent(chosenCB,ItemEvent.ITEM_STATE_CHANGED,chosenCB,ItemEvent.SELECTED));
			} else 
				chosenCB = null;
			
		}
	}

	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger() && e.getSource() instanceof JCheckBox) {
			for (int i=0; i<rm.getCompImps().size(); i++){
				if (rm.getCompImps().get(i) != null) {
					rm.getCompImps().get(i).getCanvas().handlePopupMenu(e);
					popupHappened = true;
					i = rm.getCompImps().size();
				}
			}
		}
	}

	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public RoiManager getRoiManager() {
		// TODO Auto-generated method stub
		return rm;
	}

	public JCheckBox[] getJCheckBox() {
		return checkbox;
	}

	public void setJCheckBox(JCheckBox[] checkbox) {
		this.checkbox = checkbox;
	}

	public Choice getChoice() {
		return choice;
	}

	public void setChoice(Choice choice) {
		this.choice = choice;
	}

	public JCheckBox getChosenCB() {
		return chosenCB;
	}

	public void setChosenCB(JCheckBox chosenCB) {
		this.chosenCB = chosenCB;
	}

	public boolean isPopupHappened() {
		return popupHappened;
	}

	public void setPopupHappened(boolean popupHappened) {
		this.popupHappened = popupHappened;
	}

	public void setRoiManager(RoiManager rm) {
		// TODO Auto-generated method stub
		this.rm = rm;
	}

	public void save(String path) throws IOException {
		File clFile = new File(path);
		String clString = "";
		for (JCheckBox cb:getJCheckBox()) {
			if (cb.getBackground() != null) {
				clString = clString + cb.getName().replace("_", "") +","+ cb.getLabel() +","
						+ Colors.colorToHexString(cb.getBackground()) +","
						+ cb.getBackground().getAlpha() +","
						+ cb.getBackground().getRed() +","
						+ cb.getBackground().getGreen() +","
						+ cb.getBackground().getBlue() +"\n";
			} else {
				clString = clString + cb.getName().replace("_", "") +","+ cb.getLabel() +","
						+ "#00000000" +","
						+ 0 +","
						+ 0 +","
						+ 0 +","
						+ 0 +"\n";

			}
		}
		clFile.createNewFile();

		FileWriter out = new FileWriter(clFile);
		out.write(clString);
		out.close();
	}
	
	public void saveCLfromRoiMgr(String path) throws IOException {
		if (!(bbImp.getWindow() instanceof ImageWindow3D)) {
			Roi[] selRois = bbImp.getRoiManager().getSelectedRoisAsArray();
			if (selRois.length <1)
				selRois = bbImp.getRoiManager().getShownRoisAsArray();
			ArrayList<JCheckBox> tempCheckboxes = new ArrayList<JCheckBox>();
			ArrayList<String> alreadyNamed = new ArrayList<String>();
			int i=0;
			if (brainbowColors == null) {
				brainbowColors = new Hashtable<String, Color>();
			}
			for (Roi roi:selRois) {
				if (roi == null)
					continue;
				if (alreadyNamed.contains(roi.getName().split(" \"_")[0].replace("\"","")))
					continue;
				Color roiARGBcolor = roi.getFillColor();
				if (roiARGBcolor == null) {
					roiARGBcolor = roi.getStrokeColor();
				}
				if (roiARGBcolor == null) {
					roiARGBcolor = roi.getColor();
				}
				tempCheckboxes.add(new JCheckBox(roi.getName().split(" \"_")[0].replace("\"","")));
				tempCheckboxes.get(tempCheckboxes.size()-1).setSize(150, 10);
				tempCheckboxes.get(tempCheckboxes.size()-1).setText(roi.getName().split(" \"_")[0].replace("\"",""));
				tempCheckboxes.get(tempCheckboxes.size()-1).setName(roi.getName().split(" \"_")[0].replace("\"",""));			
				tempCheckboxes.get(tempCheckboxes.size()-1).setBackground(roiARGBcolor);
				if (roiARGBcolor != null) {

					brainbowColors.put(tempCheckboxes.get(tempCheckboxes.size()-1).getName().toLowerCase(), roiARGBcolor);
					checkboxHash.put(roiARGBcolor, tempCheckboxes.get(tempCheckboxes.size()-1));
				}
				tempCheckboxes.get(tempCheckboxes.size()-1).setFont(Menus.getFont().deriveFont(8));
				//		IJ.log("WHATUP?");
				i++;
				alreadyNamed.add(roi.getName().split(" \"_")[0].replace("\"",""));
			}
			checkbox = tempCheckboxes.toArray(new JCheckBox[tempCheckboxes.size()]);
			save(path);
		}
	}

	
	public void saveCLfromIJ3D(String path) throws IOException {
		if (bbImp.getWindow() instanceof ImageWindow3D) {
			Iterator contentIterator = ((ImageWindow3D)bbImp.getWindow()).getUniverse().contents();
			int ciLength = 0;
			while (contentIterator.hasNext()) {
				ciLength++;
				Content content = (Content) contentIterator.next();
			}
			checkbox = new JCheckBox[ciLength];
			contentIterator = ((ImageWindow3D)bbImp.getWindow()).getUniverse().contents();
			int i=0;
			while (contentIterator.hasNext()) {
				Content content = (Content) contentIterator.next();
				Color contentRGBcolor = content.getColor().get();
				float contentTransp = content.getTransparency();
				Color contentARGBcolor = new Color(contentRGBcolor.getRed(),contentRGBcolor.getGreen(),contentRGBcolor.getBlue(), (int)((1f-contentTransp)*255));
				//		IJ.log(clLines[i]);
				checkbox[i] = new JCheckBox();
				((JCheckBox)checkbox[i]).setSize(150, 10);
				checkbox[i].setLabel(content.getName());
				checkbox[i].setName(content.getName());			
				checkbox[i].setBackground(contentARGBcolor);
				brainbowColors.put(checkbox[i].getName().toLowerCase(), contentARGBcolor);

				checkboxHash.put(content.getColor().get(), checkbox[i]);
				checkbox[i].setFont(Menus.getFont().deriveFont(8));
				//		IJ.log("WHATUP?");
				i++;
			}
			save(path);
		}
	}

	
	public ColorLegend clone(ImagePlus imp) {
		String clString = "";
		for (JCheckBox cb:getJCheckBox()) {
			clString = clString + cb.getName() +","+ cb.getLabel() +","
						+ Colors.colorToString(cb.getBackground()) +","
						+ cb.getBackground().getRed() +","
						+ cb.getBackground().getGreen() +","
						+ cb.getBackground().getBlue() +"\n";
		}

		ColorLegend cl2 = new ColorLegend(imp, clString);
		return cl2;
	}

	private boolean blinked;
	public int countHits;
	
	public ImageProcessor processedIP(ImageProcessor ip2) {
		ip = ip2;
		if (ip == ipCopy)
			return ip;
		changedCBorChoice = false;
		lastIp = ip;
		ipCopy = ip2.duplicate();
		selectedColorRGBs = new Hashtable<Integer,String>();
		for (JCheckBox cbOther:checkbox) {
			if (cbOther.isSelected()) {
				selectedColorRGBs.put(brainbowColors.get(cbOther.getName().toLowerCase()).getRGB(),""+brainbowColors.get(cbOther.getName().toLowerCase()).getRGB());
//				selectedColorRGBs.add(brainbowColors.get(cbOther.getName()).darker().getRGB());
//				selectedColorRGBs.add(brainbowColors.get(cbOther.getName()).darker().darker().getRGB());
			}
		}
		if (selectedColorRGBs.size()>0) {
//			IJ.log("processingip");
			if (blinkChecked) {
				//			IJ.log("blink");
				if (schfut == null || schfut.isDone()) {
//					IJ.log("newblinker");

					schfut = blinkService.scheduleAtFixedRate(new Runnable()
					{
						public void run()
						{
							for (int x=0; x<ip.getWidth(); x++){
								for (int y=0; y<ip.getHeight(); y++){
									if (selectedColorRGBs.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
										if (!blinked) {
											if(droppedCellColors.size()>0 && droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
												((ColorProcessor)ip).set(x, y, droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y)).getRGB());
											} else {
												((ColorProcessor)ip).set(x, y, Color.decode(""+((ColorProcessor)ipCopy).get(x,y)).darker().darker().darker().getRGB());
											}
										} else {
											((ColorProcessor)ip).set(x, y, ((ColorProcessor)ipCopy).get(x,y));
										}
									}
								}
							}
							blinked = !blinked;
							bbImp.getCanvas().paintDoubleBuffered(bbImp.getCanvas().getGraphics());
						}
					}, 0, 500, TimeUnit.MILLISECONDS);
				}
			} else if (hideChecked || showChecked) {
				//			IJ.log("hideorshow");
				if (schfut != null)
					schfut.cancel(true);
				for (int x=0; x<ip.getWidth(); x++){
					for (int y=0; y<ip.getHeight(); y++){
						if ((selectedColorRGBs.get(((ColorProcessor)ipCopy).get(x,y))==null && hideChecked) 
								|| (selectedColorRGBs.get(((ColorProcessor)ipCopy).get(x,y))!=null && showChecked)){
							if(droppedCellColors.size()>0 && droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
								((ColorProcessor)ip).set(x, y, droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y)).getRGB());
							} else {
								((ColorProcessor)ip).set(x, y, ((ColorProcessor)ipCopy).get(x,y));
							}
						} else
							((ColorProcessor)ip).set(x, y, Color.decode(""+((ColorProcessor)ipCopy).get(x,y)).darker().darker().darker().getRGB());
					}
				}
				//			bbImp.updateAndRepaintWindow();
			} else {
				//			IJ.log("else");
				if (schfut != null)
					schfut.cancel(true);
				for (int x=0; x<ip.getWidth(); x++){
					for (int y=0; y<ip.getHeight(); y++){
						if(droppedCellColors.size()>0 && droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
							((ColorProcessor)ip).set(x, y, droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y)).getRGB());
						} else {
							((ColorProcessor)ip).set(x, y, Color.decode(""+((ColorProcessor)ipCopy).get(x,y)).getRGB());
						}
					}
				}
				//			bbImp.updateAndRepaintWindow();
			}
		}
		return ip;
	}


	public void checkFromCellsRegex(ImagePlus dropImp, String cellsRegex) {
		bbImp = dropImp;
		countHits =0;
		for (JCheckBox cb:checkbox) {
			cb.setSelected((cb.getName().toLowerCase().trim()+" ").matches(cellsRegex.toLowerCase()));
			countHits++;
		}
//		this.itemStateChanged(new ItemEvent(checkbox[0],ItemEvent.ITEM_STATE_CHANGED,checkbox[0],ItemEvent.SELECTED));
		bbImp.updateAndDraw();
		if (rm !=null)
			rm.setSearching(false);
		return;
	}


	public Hashtable<String, Color> getBrainbowColors() {
		// TODO Auto-generated method stub
		return brainbowColors;
	}
}
