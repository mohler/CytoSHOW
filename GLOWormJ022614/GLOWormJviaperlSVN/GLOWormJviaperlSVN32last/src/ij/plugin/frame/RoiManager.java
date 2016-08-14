package ij.plugin.frame;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.awt.List;
import java.util.zip.*;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.vcell.gloworm.MQTVS_VolumeViewer;
import org.vcell.gloworm.MultiQTVirtualStack;
import org.vcell.gloworm.RoiLabelByNumbersSorter;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.filter.*;
import ij.plugin.Colors;
import ij.plugin.DragAndDrop;
import ij.plugin.Orthogonal_Views;
import ij.plugin.RGBStackMerge;
import ij.plugin.StackReverser;
import ij.util.*;
import ij.macro.*;
import ij.measure.*;
import ij3d.ImageJ3DViewer;

/** This plugin implements the Analyze/Tools/Tag Manager command. */
public class RoiManager extends PlugInFrame implements ActionListener, ItemListener, MouseListener, MouseWheelListener, KeyListener, ChangeListener, Runnable, ListSelectionListener, WindowListener, TextListener {
	public static final String LOC_KEY = "manager.loc";
	private static final int BUTTONS = 15;
	private static final int DRAW=0, FILL=1, LABEL=2;
	public static final int SHOW_ALL=0, SHOW_NONE=1, LABELS=2, NO_LABELS=3;
	private static final int MENU=0, COMMAND=1;
	private static final int SHOW_OWN = 4;
	private static int rows = 15;
	private static int lastNonShiftClick = -1;
	private static boolean allowMultipleSelections = true; 
	private static String moreButtonLabel = "More "+'\u00bb';
	private static String updateButtonLabel = "Update [u]";
	private Panel panel;
	private static Frame instance;
	private static int colorIndex = 4;
	private JList<String> list, fullList;
	private Hashtable<String, Roi> rois = new Hashtable<String, Roi>();
	private Roi roiCopy;
	private boolean canceled;
	private boolean macro;
	private boolean ignoreInterrupts;
	private PopupMenu pm;
	private Button moreButton, colorButton;
	private Checkbox addRoiSpanCCheckbox = new Checkbox("Span C", false);
	private Checkbox addRoiSpanZCheckbox = new Checkbox("Span Z", false);
	private Checkbox addRoiSpanTCheckbox = new Checkbox("Span T", false);	
	private Checkbox showAllCheckbox = new Checkbox("Show", true);
	private Checkbox showOwnROIsCheckbox = new Checkbox("Only own notes", false);
	private ImagePlus imp = null;

	private Checkbox labelsCheckbox = new Checkbox("Number Labels", false);
	private JSpinner zSustainSpinner ;
	private JSpinner tSustainSpinner ;
	private int  zSustain =1;
	private int  tSustain =1;
	private TextField textSearchField =new TextField("Find...");
	private TextField textNamingField =new TextField("Name...");
	public Label textCountLabel =new Label("", Label.CENTER);

	private static boolean measureAll = true;
	private static boolean onePerSlice = true;
	private static boolean restoreCentered;
	private int prevID;
	private boolean noUpdateMode;
	private int defaultLineWidth = 1;
	private Color defaultColor;
	private boolean firstTime = true;
	private boolean addRoiSpanC;
	private boolean addRoiSpanZ;
	private boolean addRoiSpanT;
	private String prevSearchString = "";
	private boolean shiftKeyDown;
	private boolean altKeyDown;
	private boolean controlKeyDown;
	private int sortmode;
	private int[] selectedIndexes;	
	private String title;
	protected volatile boolean done;
	private ActionEvent actionEvent;
	private Thread thread;
	private boolean showAll;
	private Roi[] originalRois = null;
	private boolean originalsCloned = false;
	private Hashtable<String,Color> brainbowColors, mowColors;
	private ArrayList<ArrayList<String>> nameLists = new ArrayList<ArrayList<String>>();
	private ArrayList<String> cellNames, fullCellNames;
	private ArrayList<ImagePlus> projYImps = new ArrayList<ImagePlus>();
	private ArrayList<ImagePlus> projZImps = new ArrayList<ImagePlus>();
	private ArrayList<ImagePlus> compImps = new ArrayList<ImagePlus>();
	private ColorLegend colorLegend;
	private double shiftY =10;
	private double shiftX =10;
	private DefaultListModel<String> listModel, fullListModel;
	private boolean rmNeedsUpdate=false;
	private Button updateButton;
	private boolean searching;
	private boolean busy;
	private Checkbox hyperstackCheckbox;
	private boolean isEmbryonic = false;



	public RoiManager() {
		super("Tag Manager");
		this.imp = WindowManager.getCurrentImage();
		if (imp.getNDimensions() < 3) {
			for (int i=0; i<WindowManager.getIDList().length;i++) {
				if (WindowManager.getImage(WindowManager.getIDList()[i]).getStack() instanceof MultiQTVirtualStack &&
						((MultiQTVirtualStack) WindowManager.getImage(WindowManager.getIDList()[i]).getStack()).getLineageMapImage() == imp){
					imp = WindowManager.getImage(WindowManager.getIDList()[i]);
				}
			}
		}
		if (imp.getMotherImp() != null ) {
			ColorLegend cl= imp.getMotherImp().getRoiManager().getColorLegend();
			if (cl != null) {
				if (cl.isPopupHappened()){
					imp = imp.getMotherImp();
					cl.setPopupHappened(false);
				}

			}
		}
		if (imp.getRoiManager()!=null) {
			imp.getRoiManager().setVisible(true);
			imp.getRoiManager().toFront();
			return;
		}
		//		this.setTitle(getTitle()+":"+ imp.getTitle());
		list = new JList<String>();
		fullList = new JList<String>();		
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);
		imp.setRoiManager(this);
		showWindow(false);
		//		WindowManager.addWindow(this);
		thread = new Thread(this, "Tag Manager");
		thread.start();

	}

	public RoiManager(boolean hideWindow) {
		super("Tag Manager");
		this.imp = WindowManager.getCurrentImage();
		//		this.setTitle(getTitle()+":"+ imp.getTitle());
		Prefs.showAllSliceOnly = true;
		list = new JList<String>();
		fullList = new JList<String>();
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);

		imp.setRoiManager(this);
		showWindow(!hideWindow);
		//		WindowManager.addWindow(this);
		thread = new Thread(this, "Tag Manager");
		thread.start();


	}

	public RoiManager(ImagePlus imp, boolean hideWindow) {
		super("Tag Manager");
		this.imp = imp;
		Prefs.showAllSliceOnly = true;
		list = new JList<String>();
		fullList = new JList<String>();	
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);

		if (imp != null){
			//			this.setTitle(getTitle()+":"+ imp.getTitle());
			imp.setRoiManager(this);
		}
		showWindow(!hideWindow);
		//		WindowManager.addWindow(this);
		thread = new Thread(this, "Tag Manager");
		thread.start();


	}

	public void showWindow(boolean visOn) {
		ImageJ ij = IJ.getInstance();
		this.removeAll();
		addKeyListener(ij);
		addMouseListener(this);
		addMouseWheelListener(this);
		//		WindowManager.addWindow(this);
		//setLayout(new FlowLayout(FlowLayout.CENTER,5,5));
		setLayout(new BorderLayout());
		list.setPrototypeCellValue("012345678901234567890123456789");		
		list.addListSelectionListener(this);
		list.addKeyListener(ij);
		list.addMouseListener(this);
		list.addMouseWheelListener(this);
		((JComponent) list).setToolTipText("<html>Left-Clicking a list item <br>highlights that tag <br>in the movie window. <br>Buttons and other widgets <br>modify the content of the list <br>and the display of tags <br>in the movie window</html>");		

		if (IJ.isLinux()) list.setBackground(Color.white);
		JScrollPane scrollPane = new JScrollPane(list);
		add("Center", scrollPane);
		panel = new Panel();
		int nButtons = BUTTONS;
		panel.setLayout(new GridLayout(nButtons+1, 1, 5, 0));
		addButton("Add\n(ctrl-t)");
		addRoiSpanCCheckbox.addItemListener(this);
		panel.add(addRoiSpanCCheckbox);
		addRoiSpanZCheckbox.addItemListener(this);
		panel.add(addRoiSpanZCheckbox);
		addRoiSpanTCheckbox.addItemListener(this);
		panel.add(addRoiSpanTCheckbox);
		addButton("Update [u]");
		addButton("Delete");
		addButton("Rename");
		panel.add(textNamingField);

		addButton("Sort");
		addButton("Deselect");
		addButton("Properties...");
		//		addButton("Flatten [F]");
		addButton(moreButtonLabel);
		//		showOwnROIsCheckbox.addItemListener(this);
		//		panel.add(showOwnROIsCheckbox);
		showAllCheckbox.addItemListener(this);
		panel.add(showAllCheckbox);
		zSustainSpinner =  new JSpinner(
				new SpinnerNumberModel(zSustain<=getImage().getNSlices()?zSustain:getImage().getNSlices(), 1, getImage().getNSlices(), 1));
		zSustainSpinner.setToolTipText("Adjust Z-depth to sustain display of Tags");
		zSustainSpinner.addChangeListener(this);
		panel.add(zSustainSpinner);
		tSustainSpinner =  new JSpinner(
				new SpinnerNumberModel(tSustain<=getImage().getNFrames()?tSustain:getImage().getNFrames(), 1, getImage().getNFrames(), 1));
		tSustainSpinner.setToolTipText("Adjust T-length to sustain display of Tags");
		tSustainSpinner.addChangeListener(this);
		panel.add(tSustainSpinner);
		panel.add(textCountLabel);
		textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
		textCountLabel.setFont(Font.decode("Arial-9"));
		//		labelsCheckbox.addItemListener(this);
		//		panel.add(labelsCheckbox);
		//		panel.add( textSearchField );		
		add("South", textSearchField);
		textSearchField.addKeyListener(this);
		textSearchField.addActionListener(this);

		add("East", panel);		
		addPopupMenu();
		pack();
		Dimension size = getSize();
		if (size.width>270)
			setSize(size.width-40, size.height);
		//		list.remove(0);
		Point loc = Prefs.getLocation(LOC_KEY);
		if (loc!=null)
			setLocation(loc);
		else
			GUI.center(this);
		this.setVisible(visOn);
		if (true) {

			list.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		}

	}

	void addButton(String label) {
		Button b = new Button(label);
		b.addActionListener(this);
		b.addKeyListener(IJ.getInstance());
		b.addMouseListener(this);
		if (label.equals(moreButtonLabel)) moreButton = b;
		if (label.equals(updateButtonLabel)) updateButton = b;
		panel.add(b);
	}

	void addPopupMenu() {
		pm=new PopupMenu();
		//addPopupItem("Select All");
		addPopupItem("Open...");
		addPopupItem("Save...");
		addPopupItem("Flatten [F]");
		addPopupItem("Fill");
		addPopupItem("Draw");
		addPopupItem("AND");
		addPopupItem("OR (Combine)");
		addPopupItem("XOR");
		addPopupItem("Split");
		addPopupItem("Add Particles");
		addPopupItem("Measure");
		addPopupItem("Multi Measure");
		addPopupItem("Specify...");
		addPopupItem("Remove Slice Info");
		addPopupItem("Help");
		addPopupItem("\"Show All\" Color...");
		addPopupItem("Options...");
		addPopupItem("Get ROIs this Slice");
		addPopupItem("Copy Selected to Other Images");
		addPopupItem("Sketch3D MoW colors");
		addPopupItem("Sketch3D Brainbow colors");
		addPopupItem("Sketch3D Split Cell Channels");
		addPopupItem("Define Connectors");
		addPopupItem("Zap Duplicate Rois");
		addPopupItem("Shift Tags in XY");
		addPopupItem("Set Fill Transparency");
		addPopupItem("Realign by Tags");
		addPopupItem("Realign by Parameters");
		addPopupItem("Auto-advance when tagging");
		add(pm);
	}

	void addPopupItem(String s) {
		MenuItem mi=new MenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	public synchronized void actionPerformed(ActionEvent e) {
		//				IJ.log(e.toString()+1);
		this.actionEvent = e;
		notify();
	}

	// Separate thread that does the potentially time-consuming processing 
	public void run() {
		//		IJ.log(actionEvent!=null?actionEvent.toString()+2:"null");
		while (!done) {
			//			IJ.log(actionEvent!=null?actionEvent.toString()+2:"null");
			synchronized(this) {
				try {wait();}
				catch(InterruptedException eIE) {}
			}
			if (done) return;
			doAction(actionEvent);
		}
	}

	public void doAction(ActionEvent e) {
		//				IJ.log(e.toString()+3);
		if (e.getActionCommand().equals("Full\nSet")) {
			imp.getRoiManager().getTextSearchField().setText("");
			e.setSource(textSearchField); 
		}
		boolean wasVis = this.isVisible();

		if (e.getSource() == textSearchField) {
			this.setVisible(false);
			showAll(SHOW_NONE);

			String thisWasTitle = this.getTitle();
			String searchString = textSearchField.getText();
			boolean isRegex = (searchString.startsWith("??"));
			listModel.clear();
			prevSearchString = searchString;
			//			String[] listStrings = fullList.getItems();
			String impTitle = this.imp.getTitle();
			int numAdded = 0;
			int count = fullListModel.getSize();
			Dimension dim = list.getSize();
			list.setSize(0,0);
			textCountLabel.setText("?" +"/"+ fullListModel.size());
			imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
			imp.getWindow().countLabel.repaint();			
			searching = true;
			long timeLast = 0;
			long timeNow = 0;

			for (int i = 0; i < count; i++) {			
				timeNow = System.currentTimeMillis();
				this.setTitle(  "Tag Manager" + ((i%100>50)?" SEARCHING!!!":" Searching...") );
				if (i%100>50){
					//					IJ.runMacro("print(\"\\\\Update:***Tag Manager is still searching tags...***\")");
					this.imp.setTitle("***"+ impTitle);
				}else{
					//					IJ.runMacro("print(\"\\\\Update:   Tag Manager is still searching tags...   \")");
					this.imp.setTitle("   "+ impTitle);
				}
				if (searchString.trim().equalsIgnoreCase("") || searchString.trim().equalsIgnoreCase(".*")) {
					listModel.addElement(fullListModel.get(i));
					//IJ.log(listStrings[i]);
					numAdded++;
					if (timeNow > timeLast + 100) {
						timeLast = timeNow;
						Graphics g = imp.getCanvas().getGraphics();
						if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
							imp.getCanvas().messageRois.remove("Loading Tags");

						Roi messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
								"   Finding tags that match:\n   "+ "Reloading full set"  + "..." 
										+ imp.getRoiManager().getListModel().getSize() + "");

						((TextRoi) messageRoi).setCurrentFont(g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width/16)));
						messageRoi.setStrokeColor(Color.black);
						messageRoi.setFillColor(Colors.decode("#99ffffdd",
								imp.getCanvas().getDefaultColor()));
						imp.getCanvas().messageRois.put("Loading Tags", messageRoi);
						imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
					}
					continue;
				}

				if (isRegex && ((String) fullListModel.get(i)).toLowerCase().matches(searchString.substring(2).toLowerCase() ) ){
					listModel.addElement(fullListModel.get(i));
					numAdded++;
				}
				if (!isRegex && ((String) fullListModel.get(i)).toLowerCase().contains(
						searchString.toLowerCase())) {
					listModel.addElement(fullListModel.get(i));
					numAdded++;
				}
				if (timeNow > timeLast + 100 && !imp.getCanvas().messageRois.containsKey("Finding tags from drop")) {
					timeLast = timeNow;
					Graphics g = imp.getCanvas().getGraphics();
					if (imp.getCanvas().messageRois.containsKey("Finding tags that match"))
						imp.getCanvas().messageRois.remove("Finding tags that match");

					Roi messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
							"   Finding tags that match:\n   \""+ searchString  +"\"..." 
									+ imp.getRoiManager().getListModel().getSize() + " found.");

					((TextRoi) messageRoi).setCurrentFont(g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width/16)));
					messageRoi.setStrokeColor(Color.black);
					messageRoi.setFillColor(Colors.decode("#99ffffdd",
							imp.getCanvas().getDefaultColor()));

					imp.getCanvas().messageRois.put("Finding tags that match", messageRoi);
					imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());

				}

			}
			if (imp.getCanvas().messageRois.containsKey("Finding tags that match"))
				imp.getCanvas().messageRois.remove("Finding tags that match");
			if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
				imp.getCanvas().messageRois.remove("Loading Tags");

			searching = false;
			list.setSize(dim);
			textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
			imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
			imp.getWindow().countLabel.repaint();			
			//			this.setTitle(  thisWasTitle  );
			this.imp.setTitle(impTitle);

			IJ.runMacro("print(\"\\\\Update:\")");

			//		sort();
			if (!(imp.getWindow().getTitle().matches(".*[XY]Z +\\d+.*")))
				this.showWindow(wasVis);
			showAll(SHOW_ALL);
			if (this.getDisplayedRoisAsArray(imp.getSlice(), imp.getFrame()).length < 1){
				int nearestTagIndex = -1;
				int closestYet = imp.getNSlices() + imp.getNFrames();
				Roi[] rois = getShownRoisAsArray();
				for (int r=0; r<rois.length; r++) {
					if (Math.abs(rois[r].getZPosition()-imp.getSlice())
							+Math.abs(rois[r].getTPosition()-imp.getFrame())
							< closestYet) {
						closestYet = Math.abs(rois[r].getZPosition()-imp.getSlice())
								+Math.abs(rois[r].getTPosition()-imp.getFrame());
						nearestTagIndex = r;
					}
					//					IJ.log(""+closestYet);
				}
				if (nearestTagIndex >=0) {
					new ij.macro.MacroRunner("roiManager('select', "+nearestTagIndex+", "+imp.getID()+");");
					select(-1);
					while (imp.getRoi() == null) 
						IJ.wait(100);
					imp.killRoi();
				}
			}

		} else {

			int modifiers = e.getModifiers();
			altKeyDown = (modifiers&ActionEvent.ALT_MASK)!=0 || IJ.altKeyDown();
			shiftKeyDown = (modifiers&ActionEvent.SHIFT_MASK)!=0 || IJ.shiftKeyDown();
			controlKeyDown = (modifiers&ActionEvent.CTRL_MASK)!=0 || IJ.controlKeyDown();
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
			IJ.setKeyUp(KeyEvent.VK_CONTROL);
			String label = e.getActionCommand();
			if (label==null) {
				return;
			}
			String command = label;
			if (command.equals("Add\n(ctrl-t)"))
				add(shiftKeyDown, altKeyDown, controlKeyDown);
			else if (command.equals("Update [u]")) {
				if (imp.getMultiChannelController()!=null)
					imp.getMultiChannelController().updateRoiManager();
			}
			else if (command.equals("Delete"))
				delete(false);
			else if (command.equals("Delete ")) {
				if (list.getSelectedIndices().length == 1)
					delete(false);
			}
			else if (command.equals("Color")) {
				Roi[] selectedRois = getSelectedRoisAsArray();
				if (selectedRois.length>0) {
					int fillColorInt = Colors.decode("#00000000", Color.black).getRGB();
					if (selectedRois[0].getFillColor() != null) {
						fillColorInt = selectedRois[0].getFillColor().getRGB();
					}
					String hexInt = Integer.toHexString(fillColorInt);
					int l = hexInt.length();
					for (int c=0;c<8-l;c++)
						hexInt = "0"+hexInt;
					String hexName = "#"+hexInt;
					Color fillColor = JColorChooser.showDialog(this.getFocusOwner(), "Pick a color for "+ this.getSelectedRoisAsArray()[0].getName()+"...", Colors.decode(hexName, Color.cyan));
					String alphaCorrFillColorString =  Colors.colorToHexString(fillColor).replaceAll("#", hexName.substring(0, 3));
					fillColor = Colors.decode(alphaCorrFillColorString, fillColor);

					ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
					ArrayList<String> rootNames = new ArrayList<String>();


					for (Roi selRoi:getSelectedRoisAsArray()) {
						String rootName = selRoi.getName().contains("\"")?selRoi.getName().split("\"")[1].trim():"";
						rootName = rootName.contains(" ")?rootName.split("[_\\- ]")[0].trim():rootName;
						String[] rootChunks = selRoi.getName().split("_");
						String rootFrame = rootChunks[rootChunks.length-1].replaceAll("[CZT]", "").split("-")[0];
						if (!rootNames_rootFrames.contains(rootName+"_"+rootFrame)) {
							rootNames_rootFrames.add(rootName+"_"+rootFrame);
							rootNames.add(rootName);				
						}
					}
					
					ArrayList<Integer> nameMatchIndexArrayList = new ArrayList<Integer>();

					for (int n=0; n<rootNames.size(); n++) {
						String rootName = rootNames.get(n);
						Roi[] rois = getFullRoisAsArray();
						int fraa = rois.length;
						for (int r=0; r < fraa; r++) {
							String nextName = rois[r].getName();
							if (nextName.startsWith("\""+rootName.split("_")[0])){
								nameMatchIndexArrayList.add(r);
							}
						}

					}
					int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
					for (int i=0; i < nameMatchIndexes.length; i++) {
						nameMatchIndexes[i] = nameMatchIndexArrayList.get(i);
//						this.select(nameMatchIndexes[i]);
//
//						if (runCommand("set fill color", alphaCorrFillColorString)) {
//						}
					}	
					this.setSelectedIndexes(nameMatchIndexes);

					if (runCommand("set fill color", alphaCorrFillColorString)) {
					}

				}
				this.close();
				this.showWindow(wasVis);

			}
			else if (command.equals("Rename")) {
				if (rename(null, null, true)) {
					this.close();
					this.showWindow(wasVis);
				}
			}
			else if (command.equals("Properties..."))
				setProperties(null, -1, null);
			else if (command.equals("Flatten [F]"))
				flatten();
			else if (command.equals("Measure"))
				measure(MENU);
			else if (command.equals("Open..."))
				open(null);
			else if (command.equals("Save..."))
				save();
			else if (command.equals("Save")) {
				select(-1);
				save();
			}
			else if (command.equals("Adv.")) {
//				if (imp.getMotherImp().getRoiManager().getColorLegend(e.getSource()) != null)
//					imp.getMotherImp().getRoiManager().getColorLegend(e.getSource()).setVisible(true);
				this.showWindow(true);
			}
			else if (command.equals("Show")) {
				showAll(SHOW_ALL);
				imp.getWindow().hideShowButton.setActionCommand("Hide");				
				imp.getWindow().hideShowButton.setToolTipText("Showing Tags...click to Hide Tags");
				imp.getWindow().hideShowButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/showIcon.png")));
			}
			else if (command.equals("Hide")) {
				showAll(SHOW_NONE);
				imp.getWindow().hideShowButton.setActionCommand("Show");				
				imp.getWindow().hideShowButton.setToolTipText("Hiding Tags...click to Show Tags");
				imp.getWindow().hideShowButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/hideIcon.png")));
			}
			else if (command.equals("Fill"))
				drawOrFill(FILL);
			else if (command.equals("Draw"))
				drawOrFill(DRAW);
			else if (command.equals("Deselect"))
				select(-1);
			else if (command.equals(moreButtonLabel)) {
				Point ploc = panel.getLocation();
				Point bloc = moreButton.getLocation();
				pm.show(this, ploc.x, bloc.y);
			} else if (command.equals("OR (Combine)"))
				combine();
			else if (command.equals("Split"))
				split();
			else if (command.equals("AND"))
				and();
			else if (command.equals("XOR"))
				xor();
			else if (command.equals("Add Particles"))
				addParticles();
			else if (command.equals("Multi Measure"))
				multiMeasure();
			else if (command.equals("Sort")) {
				sortmode = 0;
				if (controlKeyDown) sortmode = 1;
				if (altKeyDown) sortmode = 2;
				if (shiftKeyDown) sortmode = 3;
				sort();
			}
			else if (command.equals("Specify..."))
				specify();
			else if (command.equals("Remove Slice Info"))
				removeSliceInfo();
			else if (command.equals("Help"))
				help();
			else if (command.equals("Options..."))
				options();
			else if (command.equals("\"Show All\" Color..."))
				setShowAllColor();
			else if (command.equals("Get ROIs this Slice")){
				ImagePlus imp = this.imp;

				getSliceSpecificRoi(imp, imp.getSlice(),imp.getFrame());
			} 
			else if (command.equals("Copy Selected to Other Images"))
				copyToOtherRMs();
			else if (command.equals("Sketch3D MoW colors"))				
				sketch3D(e.getSource());
			else if (command.equals("Color Legend"))				
				getColorLegend(e.getSource()).setVisible(true);
			else if (command.equals("Sketch3D Brainbow colors")) {
				controlKeyDown = true;
				busy = true;
				sketch3D(e.getSource());
				busy = false;
			}
			else if (command.equals("Sketch\n3D") && !imp.isSketch3D()) {
				controlKeyDown = true;
				busy = true;
				imp.getWindow().sketch3DButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/3D_42578.gif")));
				sketch3D(e.getSource());
//				sketchVolumeViewer(e.getSource());
				busy = false;
				imp.getWindow().sketch3DButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/3D_47973.gif")));
			}
			else if (command.equals("Sketch\n3D") && imp.isSketch3D() && getColorLegend()!=null) {
				PopupMenu pm2 = new PopupMenu();
				for (String modeString:getColorLegend().modes) {
					MenuItem mi2 = new MenuItem(modeString.replace("Both Checked & Unchecked", "All").replace("Checked", "Chosen"));
					mi2.addActionListener(this);
					pm2.add(mi2);
				}
				MenuItem mi2 = new MenuItem("Clear Choices");
				mi2.addActionListener(this);
				pm2.add(mi2);
				pm2.getItem(getColorLegend().getChoice().getSelectedIndex())
				.setLabel("Ã "+getColorLegend().getChoice().getSelectedItem()
						.replace("Both Checked & Unchecked", "All").replace("Checked", "Chosen"));
				imp.getWindow().sketch3DButton.add(pm2);
				pm2.show(imp.getWindow().sketch3DButton, 1, imp.getWindow().sketch3DButton.getHeight());
			}
			else if (command.equals("Sketch\nVV")) {
				controlKeyDown = true;
				busy = true;
				imp.getWindow().sketchVVButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/VV_55184.gif")));
				sketchVolumeViewer(e.getSource());
				busy = false;
				imp.getWindow().sketchVVButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/VV_57282.gif")));
			}
			else if (e.getSource() instanceof MenuItem && getColorLegend()!=null) {
				if (command != null) {
					if (command == "Clear Choices") {
						getColorLegend().actionPerformed(new ActionEvent(getColorLegend().clearButton, ActionEvent.ACTION_PERFORMED, getColorLegend().clearButton.getActionCommand()));
					} else {
						getColorLegend().getChoice().select(command.replace("Ã ", "").replace("Chosen", "Checked").replace("All", "Both Checked & Unchecked"));
						getColorLegend().itemStateChanged(new ItemEvent(getColorLegend().getChoice(), ItemEvent.ITEM_STATE_CHANGED, getColorLegend().getChoice(), ItemEvent.SELECTED));
					}
				}
			}
			else if (command.equals("Sketch3D Split Cell Channels")) {
				shiftKeyDown = true;
				busy = true;
				sketch3D(e.getSource());
				busy = false;
			}
			else if (command.equals("Define Connectors")) {
				defineConnectors();
			}
			else if (command.equals("Zap Duplicate Rois")) {
				zapDuplicateRois();
			}
			else if (command.equals("Shift Tags in XY")) {
				shiftROIsXY();
			}
			else if (command.equals("Set Fill Transparency")) {
				setFillTransparency("99");
			}
			else if (command.equals("Realign by Tags")) {
				realignByTags();
			}
			else if (command.equals("Realign by Parameters")) {
				realignByParameters();
			}
			else if (command.equals("Auto-advance when tagging")) {
				new ij.macro.MacroRunner(IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/WormAtlasLabelerMacro.txt"));
			}

			this.imp.getCanvas().requestFocus();
		}
		//		IJ.log("DONE");
		//		done = true;
	}

	private void sketchVolumeViewer(Object source) { 
		IJ.setForegroundColor(255, 255, 255);
		IJ.setBackgroundColor(0, 0, 0);
		if (getSelectedRoisAsArray().length<1)
			return;
		ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
		ArrayList<String> rootNames = new ArrayList<String>();
		String roiColorString = Colors.colorToHexString(this.getSelectedRoisAsArray()[0].getFillColor());
		roiColorString = roiColorString.substring(roiColorString.length()-6);
		String assignedColorString = "#ff" + roiColorString;

		for (Roi selRoi:getSelectedRoisAsArray()) {
			String rootName = selRoi.getName().contains("\"")?selRoi.getName().split("\"")[1].trim():"";
			rootName = rootName.contains(" ")?rootName.split("[_\\- ]")[0].trim():rootName;
			String[] rootChunks = selRoi.getName().split("_");
			String rootFrame = rootChunks[rootChunks.length-1].replaceAll("[CZT]", "").split("-")[0];
			if (!rootNames_rootFrames.contains(rootName+"_"+rootFrame)) {
				rootNames_rootFrames.add(rootName+"_"+rootFrame);
				rootNames.add(rootName);				
			}
		}

		MQTVS_VolumeViewer vv = new MQTVS_VolumeViewer(); 
		for (int n=0; n<rootNames_rootFrames.size(); n++) {
			ImagePlus sketchImp = NewImage.createImage("SketchVolumeViewer_"+rootNames_rootFrames.get(0),imp.getWidth(), imp.getHeight(), imp.getNSlices()*imp.getNFrames(), 8, NewImage.FILL_BLACK, false);
			sketchImp.setDimensions(1, imp.getNSlices(), imp.getNFrames());
			sketchImp.setCalibration(imp.getCalibration());
			String rootName = rootNames.get(n);
			sketchImp.setTitle("SketchVolumeViewer_"+rootName);
			IJ.run(sketchImp, "Select All","");
			IJ.run(sketchImp, "Clear","stack");
			if (!sketchImp.isVisible()) {
				sketchImp.show();
				sketchImp.setRoiManager(new RoiManager(false));
				sketchImp.getRoiManager().select(-1);
				IJ.wait(50);
				if (sketchImp.getRoiManager().getCount() >0)
					sketchImp.getRoiManager().runCommand("Delete");
			} else {
				sketchImp.getRoiManager().select(-1);
				IJ.wait(50);
				sketchImp.getRoiManager().runCommand("Delete");
			}
			select(-1);
			IJ.wait(50);
			ArrayList<Integer> nameMatchIndexArrayList = new ArrayList<Integer>();
			int fraa = this.getFullRoisAsArray().length;
			Roi[] rois = getFullRoisAsArray();
			for (int r=0; r < fraa; r++) {
				String nextName = rois[r].getName();
				if (nextName.startsWith("\""+rootName.split("_")[0])
						/*&&
						rootName.endsWith(nextName.split("_")[nextName.split("_").length-1].replaceAll("[CZT]", "").split("-")[0])*/
						){
					nameMatchIndexArrayList.add(r);
				}
			}
			int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
			for (int i=0; i < nameMatchIndexes.length; i++) {
				nameMatchIndexes[i] = nameMatchIndexArrayList.get(i);
				Roi nextRoi = ((Roi)getFullRoisAsArray()[nameMatchIndexArrayList.get(i)]);
				String[] nextChunks = nextRoi.getName().split("_");
				sketchImp.getWindow().setVisible(true);
				int nextSlice = Integer.parseInt(nextChunks[nextChunks.length-2]);
				int nextFrame = Integer.parseInt(nextChunks[nextChunks.length-1].replaceAll("[CZT]", "").split("-")[0]);
				sketchImp.setPosition(1, nextSlice, nextFrame);
				sketchImp.getRoiManager().addRoi((nextRoi));
			}		
			sketchImp.getRoiManager().select(-1);
			sketchImp.getRoiManager().drawOrFill(FILL);
			sketchImp.setMotherImp(imp, 0);
			sketchImp.getRoiManager().setSelectedIndexes(sketchImp.getRoiManager().getFullListIndexes());
										 
			vv.runVolumeViewer(sketchImp, rootName, assignedColorString);

			sketchImp.changes = false;
			sketchImp.close();
			sketchImp.flush();
			ImageJ3DViewer.select(null);
		}
	}

	public void itemStateChanged(ItemEvent e) {
		Object source = e.getSource();
		if (source==addRoiSpanCCheckbox) {
			if (addRoiSpanCCheckbox.getState())
				addRoiSpanC = true;
			else 
				addRoiSpanC = false;
			return;
		}
		if (source==addRoiSpanZCheckbox) {
			if (addRoiSpanZCheckbox.getState())
				addRoiSpanZ = true;
			else 
				addRoiSpanZ = false;
			return;
		}
		if (source==addRoiSpanTCheckbox) {
			if (addRoiSpanTCheckbox.getState())
				addRoiSpanT = true;
			else 
				addRoiSpanT = false;
			return;
		}
		//		if (source==showOwnROIsCheckbox) {
		//			showAll(showOwnROIsCheckbox.getState()?SHOW_OWN:(showAllCheckbox.getState()?SHOW_ALL:SHOW_NONE) );
		//			firstTime = false;
		//			return;
		//		}
		if (source==showAllCheckbox) {
			showAll(showAllCheckbox.getState()?SHOW_ALL:SHOW_NONE);
			firstTime = false;
			return;
		}
		if (source==labelsCheckbox) {
			if (firstTime)
				showAllCheckbox.setState(true);
			boolean editState = labelsCheckbox.getState();
			boolean showAllState = showAllCheckbox.getState();
			if (!showAllState && !editState)
				showAll(SHOW_NONE);
			else {
				showAll(editState?LABELS:NO_LABELS);
				if (editState) showAllCheckbox.setState(true);
			}
			firstTime = false;
			return;
		}
		if (e.getStateChange()==ItemEvent.SELECTED && !ignoreInterrupts) {
			int index = 0;
			//IJ.log("item="+e.getItem()+" shift="+IJ.shiftKeyDown()+" ctrl="+IJ. controlKeyDown());
			try {index = Integer.parseInt(e.getItem().toString());}
			catch (NumberFormatException ex) {}
			if (index<0) index = 0;
			if (!IJ.isMacintosh()) {      //handle shift-click, ctrl-click (on Mac, OS takes care of this)
				if (!IJ.shiftKeyDown()) lastNonShiftClick = index;
				if (!IJ.shiftKeyDown() && !IJ.controlKeyDown()) {  //simple click, deselect everything else
					list.clearSelection();
					//    				int[] indexes = getSelectedIndexes();
					//    				for (int i=0; i<indexes.length; i++) {
					//    					if (indexes[i]!=index)
					//    						list.deselect(indexes[i]);
					//    				}
				} else if (IJ.shiftKeyDown() && lastNonShiftClick>=0 && lastNonShiftClick<listModel.getSize()) {
					int firstIndex = Math.min(index, lastNonShiftClick);
					int lastIndex = Math.max(index, lastNonShiftClick);
					list.clearSelection();
					//                    int[] indexes = getSelectedIndexes();
					//                    for (int i=0; i<indexes.length; i++)
					//                    	if (indexes[i]<firstIndex || indexes[i]>lastIndex)
					//                    		list.deselect(indexes[i]);      //deselect everything else
					for (int i=firstIndex; i<=lastIndex; i++)
						list.setSelectedIndex(i);                     //select range
				}
			}
			if (WindowManager.getCurrentImage()!=null) {
				//				restore(getImage(), index, true);
				if(list.getSelectedIndices().length <=1) {
					//					IJ.log("list.getSelectedIndexes <=1");
					restore(getImage(), index, true);
				}
				if (record()) {
					if (Recorder.scriptMode())
						Recorder.recordCall("rm.select(imp, "+index+");");
					else
						Recorder.record("roiManager", "Select", index);
				}
			}
		}
	}

	void add(boolean shiftKeyDown, boolean altKeyDown, boolean controlKeyDown) {
		if (controlKeyDown) {
			addRoiSpanC = true;
			addRoi(false);
			addRoiSpanC = false;
		}
		else if (altKeyDown) {
			addRoiSpanZ = true;
			addRoi(false);
			addRoiSpanZ = false;
		}
		else if (shiftKeyDown) {
			addRoiSpanT = true;
			addRoi(false);
			addRoiSpanT = false;
		} else
			addRoi(false);
	}

	/** Adds the specified ROI. */
	public void addRoi(Roi roi) {
		addRoi(roi, false, null, -1);
	}

	boolean addRoi(boolean promptForName) {
		return addRoi(null, promptForName, null, -1);
	}

	boolean addRoi(Roi roi, boolean promptForName, Color color, int lineWidth) {
		ImagePlus imp = this.imp;
		if (roi==null) {
			if (imp==null)
				return false;
			roi = imp.getRoi();
			if (roi==null) {
				error("The relevant image does not have a selection.");
				return false;
			}
		}
		if (color==null && roi.getStrokeColor()!=null)
			color = roi.getStrokeColor();
		else if (color==null && defaultColor!=null)
			color = defaultColor;
		Color fillColor = imp.getRoiFillColor();
		//		IJ.log(""+imp.getRoiFillColor());
		if (lineWidth<0) {
			int sw = (int)roi.getStrokeWidth();
			lineWidth = sw>1?sw:defaultLineWidth;
		}
		if (lineWidth>100) lineWidth = 1;
		int n = listModel.getSize();
		if (n>0 && !IJ.isMacro() && imp!=null) {
			// check for duplicate
			String label = (String) listModel.getElementAt(n-1);
			Roi roi2 = (Roi)rois.get(label);
			if (roi2!=null) {
				int slice2 = getSliceNumber(roi2, label);
				if (roi.equals(roi2) && (slice2==-1||slice2==imp.getCurrentSlice()) && imp.getID()==prevID && !Interpreter.isBatchMode())
					return false;
			}
		}
		prevID = imp!=null?imp.getID():0;
		String name = roi.getName();
		if (true/*name == null*/)
			if (!(textNamingField.getText().isEmpty()  || textNamingField.getText().contains("Name..."))) {
				roi.setName(textNamingField.getText());
			}
		if (isStandardName(name))
			name = null;
		String label = name!=null?name:getLabel(imp, roi, -1);
		if (promptForName)
			label = promptForName(label);
		else if (roi instanceof TextRoi)
			if (imp != null) 
				label = (((TextRoi)roi).getText().indexOf("\n")>0?("\""+((TextRoi)roi).getText().replace("\n"," ")+"\""):"Blank") +"_"+ imp.getChannel() +"_"+ imp.getSlice() +"_"+imp.getFrame();
			else 
				label = roi.getName();
		else if (true){
			String altType = null;
			if (roi instanceof EllipseRoi) altType = "Ellipse";
			if (roi instanceof Arrow) altType = "Arrow";
			if (imp != null) 
				if (roi.getName() != null && roi.getName().split("\"").length>1)
					label = "\""+roi.getName().split("\"")[1].trim()+" \"" +"_"+ imp.getChannel() +"_"+ imp.getSlice() +"_"+imp.getFrame();
				else if (roi.getName() != null)
					label = "\""+roi.getName()+" \"" +"_"+ imp.getChannel() +"_"+ imp.getSlice() +"_"+imp.getFrame();
				else
					label = ((altType != null)?altType:roi.getTypeAsString() ) +"_"+ imp.getChannel() +"_"+ imp.getSlice() +"_"+imp.getFrame();
			else 
				label = roi.getName();
		}
		label = getUniqueName(label);
		if (label==null) return false;
		listModel.addElement(label);
		fullListModel.addElement(label);
		roi.setName(label);
		if (imp != null)
			roi.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
		roiCopy = (Roi)roi.clone();

		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			if (cal.xOrigin!=0.0 || cal.yOrigin!=0.0) {
				Rectangle r = roiCopy.getBounds();
				roiCopy.setLocation(r.x-(int)cal.xOrigin, r.y-(int)cal.yOrigin);
			}
		}
		if (lineWidth>1)
			roiCopy.setStrokeWidth(lineWidth);
		if (color!=null)
			roiCopy.setStrokeColor(color);
		if (fillColor!=null)
			roiCopy.setFillColor(fillColor);
		rois.put(label, roiCopy);
		
		
		ColorLegend cl = getColorLegend();
		//
		if (roiCopy!=null) { 
			if (cl != null) {
				Color clColor = cl.getBrainbowColors()
									.get(roiCopy.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
				if (clColor !=null) {
					String hexRed = Integer.toHexString(clColor.getRed());
					String hexGreen = Integer.toHexString(clColor.getGreen());
					String hexBlue = Integer.toHexString(clColor.getBlue());
					roiCopy.setFillColor(Colors.decode("#88"+(hexRed.length()==1?"0":"")+hexRed
														+(hexGreen.length()==1?"0":"")+hexGreen
														+(hexBlue.length()==1?"0":"")+hexBlue
													, Color.white));
				}
			}
		} 
	

		if (!Orthogonal_Views.isOrthoViewsImage(imp)) {
			if (imp.getWindow() instanceof StackWindow && ((StackWindow)imp.getWindow()).isWormAtlas()) {
				for (Roi existingRoi:getFullRoisAsArray()){
					if (imp.getSlice() == existingRoi.getZPosition() && imp.getFrame() == existingRoi.getTPosition()) {
						if (roiCopy.contains((int)existingRoi.getBounds().getCenterX(), (int)existingRoi.getBounds().getCenterY())) {
							if (existingRoi instanceof TextRoi && !(roiCopy instanceof TextRoi) && roiCopy.isArea()) {
								label = (true/*((TextRoi)existingRoi).getText().matches(".*")*/?(""+((TextRoi)existingRoi).getText().replace("\n"," ")+"| Area"):"Blank");
								rename(label, new int[]{listModel.size()-1}, true);
								roiCopy.setFillColor(existingRoi.getFillColor());
								roiCopy.setStrokeColor(null);
							}
						}
					}
				}
			} else {
				for (Roi existingRoi:getFullRoisAsArray()){
					if (imp.getSlice() == existingRoi.getZPosition() && imp.getFrame() == existingRoi.getTPosition()) {
						if (roiCopy.contains((int)existingRoi.getBounds().x, (int)existingRoi.getBounds().y)) {
							if (existingRoi instanceof TextRoi && !(roiCopy instanceof TextRoi) && roiCopy.isArea()) {
								label = (true/*((TextRoi)existingRoi).getText().matches(".*")*/?(""+((TextRoi)existingRoi).getText().replace("\n"," ")+"| Area"):"Blank");
								rename(label, new int[]{listModel.size()-1}, true);
								roiCopy.setFillColor(existingRoi.getFillColor());
								roiCopy.setStrokeColor(null);
							}
						}
					}
				}
			}
		}


		if (imp != null) {
			int c = imp.getChannel();
			int z = imp.getSlice();
			int t = imp.getFrame();
			if (addRoiSpanC) {
				c = 0;
			}
			if (addRoiSpanZ) {
				z = 0;
			}
			if (addRoiSpanT) {
				t = 0;
			}
			roiCopy.setPosition(c, z, t);
			//IJ.log("addSingleRoi" + roiCopy.getCPosition()+roiCopy.getZPosition()+roiCopy.getTPosition() );
		}
		//		if (false)
		updateShowAll();
		if (record())
			recordAdd(defaultColor, defaultLineWidth);
		textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
		if (imp.getWindow()!=null) {
			imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
			imp.getWindow().countLabel.repaint();
		}
		return true;
	}

	void recordAdd(Color color, int lineWidth) {
		if (Recorder.scriptMode())
			Recorder.recordCall("rm.addRoi(imp.getRoi());");
		else if (color!=null && lineWidth==1)
			Recorder.recordString("roiManager(\"Add\", \""+getHex(color)+"\");\n");
		else if (lineWidth>1)
			Recorder.recordString("roiManager(\"Add\", \""+getHex(color)+"\", "+lineWidth+");\n");
		else
			Recorder.record("roiManager", "Add");
	}

	String getHex(Color color) {
		if (color==null) color = ImageCanvas.getShowAllColor();
		String hex = Integer.toHexString(color.getRGB());
		if (hex.length()==8) hex = hex.substring(2);
		return hex;
	}

	/** Adds the specified ROI to the list. The third argument ('n') will 
		be used to form the first part of the ROI label if it is >= 0. */
	public void add(ImagePlus imp, Roi roi, int n) {
		if (roi==null) return;
		String label = getLabel(imp, roi, n);
		if (label==null) return;
		listModel.addElement(label);
		fullListModel.addElement(label);
		roi.setName(label);
		roiCopy = (Roi)roi.clone();
		if (imp!=null) {
			Calibration cal = imp.getCalibration();
			if (cal.xOrigin!=0.0 || cal.yOrigin!=0.0) {
				Rectangle r = roiCopy.getBounds();
				roiCopy.setLocation(r.x-(int)cal.xOrigin, r.y-(int)cal.yOrigin);
			}
		}
		rois.put(label, roiCopy);
		textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
		imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
		imp.getWindow().countLabel.repaint();			
	}

	boolean isStandardName(String name) {
		if (name==null) return false;
		boolean isStandard = false;
		int len = name.length();
		if (len>=14 && name.charAt(4)=='-' && name.charAt(9)=='-' )
			isStandard = true;
		else if (len>=17 && name.charAt(5)=='-' && name.charAt(11)=='-' )
			isStandard = true;
		else if (len>=9 && name.charAt(4)=='-')
			isStandard = true;
		else if (len>=11 && name.charAt(5)=='-')
			isStandard = true;
		return isStandard;
	}

	String getLabel(ImagePlus imp, Roi roi, int n) {
		Rectangle r = roi.getBounds();
		int xc = r.x + r.width/2;
		int yc = r.y + r.height/2;
		if (n>=0)
		{xc = yc; yc=n;}
		if (xc<0) xc = 0;
		if (yc<0) yc = 0;
		int digits = 4;
		String xs = "" + xc;
		if (xs.length()>digits) digits = xs.length();
		String ys = "" + yc;
		if (ys.length()>digits) digits = ys.length();
		if (digits==4 && imp!=null && imp.getStackSize()>=10000) digits = 5;
		xs = "000000" + xc;
		ys = "000000" + yc;
		String label = ys.substring(ys.length()-digits) + "-" + xs.substring(xs.length()-digits);
		if (imp!=null && imp.getStackSize()>1) {
			int channel = roi.getCPosition();
			int slice = roi.getZPosition();
			int frame = roi.getTPosition();
			////			if (channel==0)
			//				channel = imp.getChannel();
			////			if (slice==0)
			//				slice = imp.getSlice();
			////			if (frame==0)
			//				frame = imp.getFrame();
			String zs = "000000" + slice;
			label = zs.substring(zs.length()-digits) + "-" + label;
			roi.setPosition(channel, slice, frame);
		}
		return label;
	}

	void addAndDraw(boolean altKeyDown) {
		if (altKeyDown) {
			if (!addRoi(true)) return;
		} else if (!addRoi(false))
			return;
		ImagePlus imp = this.imp;

		if (imp!=null) {
			Undo.setup(Undo.COMPOUND_FILTER, imp);
			IJ.run(imp, "Draw", "slice");
			Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		}
		if (record()) Recorder.record("roiManager", "Add & Draw");
	}

	boolean delete(boolean replacing) {
		int count = listModel.getSize();
		int fullCount = fullListModel.getSize();
		if (count==0)
			return error("The list is empty.");
		int index[] = getSelectedIndexes();
		if (index.length==0 || (replacing&&count>1)) {
			String msg = "Delete all items on the list?";
			if (replacing)
				msg = "Replace items on the list?";
			canceled = false;
			if (!IJ.isMacro() && !macro) {
				YesNoCancelDialog d = new YesNoCancelDialog(this, "Tag Manager", msg);
				if (d.cancelPressed())
				{canceled = true; return false;}
				if (!d.yesPressed()) return false;
			}
			index = getAllShownIndexes();
		}
		if (fullCount==index.length && !replacing) {
			rois.clear();
			listModel.removeAllElements();
			fullListModel.removeAllElements();
		} else {
			for (int i=count-1; i>=0; i--) {
				boolean delete = false;
				for (int j=0; j<index.length; j++) {
					if (index[j]==i)
						delete = true;
				}
				if (delete) {
					rois.remove(listModel.getElementAt(i));
					fullListModel.removeElement(listModel.getElementAt(i));
					listModel.remove(i);
				}
			}
		}
		ImagePlus imp = this.imp;

		if (count>1 && index.length==1 && imp!=null)
			imp.deleteRoi();
		updateShowAll();
		textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
		imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
		imp.getWindow().countLabel.repaint();			
		if (record()) Recorder.record("roiManager", "Delete");
		return true;
	}

	boolean update(boolean clone) {
		ImagePlus imp = this.imp;

		if (imp==null) return false;
		ImageCanvas ic = imp.getCanvas();
		boolean showingAll = ic!=null &&  ic.getShowAllROIs();
		Roi roi = imp.getRoi();
		roi.setFillColor(Roi.getDefaultFillColor());
		if (roi==null) {
			error("The active image does not have a selection.");
			return false;
		}
		int index = list.getSelectedIndex();
		if (index<0 && !showingAll)
			return error("Exactly one item in the list must be selected.");
		if (index>=0) {
			String label = (String) listModel.getElementAt(index);

			if( roi instanceof TextRoi )
				label = (((TextRoi)roi).getText().indexOf("\n")>0?("\""+((TextRoi)roi).getText().replace("\n"," ")+"\""):"Blank") +"_"+ imp.getChannel() +"_"+ imp.getSlice() +"_"+imp.getFrame();
			else if (true){
				String altType = null;
				if (roi instanceof EllipseRoi) altType = "Ellipse";
				if (roi instanceof Arrow) altType = "Arrow";
				label = ((altType != null)?altType:roi.getTypeAsString() ) +"_"+ imp.getChannel() +"_"+ imp.getSlice() +"_"+imp.getFrame();
			}
			label = getUniqueName(label);
			if (label==null) return false;
			rename(label, null, true);

			roi.setName(label);
			//			roi.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
			//			roiCopy = (Roi)roi.clone();

			int c = imp.getChannel();
			int z = imp.getSlice();
			int t = imp.getFrame();
			if (addRoiSpanC) {
				c = 0;
			}
			if (addRoiSpanZ) {
				z = 0;
			}
			if (addRoiSpanT) {
				t = 0;
			}
			//			roiCopy.setPosition(c,z,t);
			roi.setPosition(c,z,t);
			rois.remove(label);
			if (clone) {
				Roi roi2 = (Roi)roi.clone();
				int position = roi.getPosition();
				if (imp.getStackSize()>1)
					roi2.setPosition(c,z,t);
				rois.put(label, roi2);
				//				IJ.log("cloning");
			} else
				rois.put(label, roi);


			updateShowAll();
		}
		if (record()) Recorder.record("roiManager", "Update");
		if (showingAll) imp.draw();
		return true;
	}

	boolean rename(String name2,  int[] indexes, boolean updateCanvas) {
		//		int index = list.getSelectedIndex();
		//		Roi[] selectedRois = this.getSelectedRoisAsArray();
		if (indexes == null)
			indexes = this.getSelectedIndexes();
		//			return error("Exactly one item in the list must be selected.");
		for (int i=0; i<indexes.length; i++) {
			String name = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi)rois.get(name);
			if (roi == null)
				continue;
			int c= roi.getCPosition()>0?roi.getCPosition(): imp.getChannel();
			int z= roi.getZPosition()>0?roi.getZPosition(): imp.getSlice();
			int t= roi.getTPosition()>0?roi.getTPosition(): imp.getFrame();
			if (name2==null) {
				if (roi instanceof TextRoi)
					name2 = promptForName(((TextRoi)roi).getText().replace("\n","|"));
				else if (name.split("\"").length > 1)
					name2 = promptForName(name.split("\"")[1]);
				else
					name2 = promptForName(name);
			}
			if (name2==null) return false;
			rois.remove(name);
			String label = name!=null?name:getLabel(imp, roi, -1);
			if (roi instanceof TextRoi)
				if (imp != null) {
					((TextRoi)roi).setText(name2);
					label = (((TextRoi)roi).getText().indexOf("\n")>0?("\""+((TextRoi)roi).getText().replace("\n"," ")+"\""):"Blank") +"_"+ c +"_"+ z +"_"+ t;
				} else 
					label = roi.getName();
			else if (true){
				String altType = null;
				if (roi instanceof EllipseRoi) altType = "Ellipse";
				if (roi instanceof Arrow) altType = "Arrow";
				if (imp != null) 
					label = ("\""+name2+" \"")  +"_"+ c +"_"+ z +"_"+ t;
				else 
					label = roi.getName();
			}
			label = getUniqueName(label);

			roi.setName(label);
			rois.put(label, roi);
			
			ColorLegend cl = getColorLegend();
			//
			if (roi!=null) { 
				if (cl != null) {
					Color clColor = cl.getBrainbowColors()
										.get(roi.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
					if (clColor !=null) {
						String hexRed = Integer.toHexString(clColor.getRed());
						String hexGreen = Integer.toHexString(clColor.getGreen());
						String hexBlue = Integer.toHexString(clColor.getBlue());
						roi.setFillColor(Colors.decode("#88"+(hexRed.length()==1?"0":"")+hexRed
															+(hexGreen.length()==1?"0":"")+hexGreen
															+(hexBlue.length()==1?"0":"")+hexBlue
														, Color.white));
					}
				}
			} 
		
			listModel.setElementAt(label, indexes[i]);
			//		fullList.replaceItem(name2, index);
			if (!listModel.equals(fullListModel)) {
				for (int i1=0; i1<fullListModel.getSize(); i1++) {
					if (fullListModel.getElementAt(i1).equals(name)) {
						fullListModel.setElementAt(label, i1);
					}
				}		
			}

			//			list.setSelectedIndex(indexes[i]);
		}
		if (updateCanvas)
			updateShowAll();
		if (record())
			Recorder.record("roiManager", "Rename", name2);
		return true;
	}

	String promptForName(String name) {
		String name2 = "";		
		if (textNamingField.getText().isEmpty()  || textNamingField.getText().contains("Name...")) {
			GenericDialog gd = new GenericDialog("Tag Manager");
			gd.addStringField("Rename As:", name.endsWith("|")?name.substring(0, name.length()-1):name, 20);
			gd.showDialog();
			if (gd.wasCanceled())
				return null;
			name2 = gd.getNextString();
			//		name2 = getUniqueName(name2);
		} else {
			name = textNamingField.getText();
			name2 = name.endsWith("|")?name.substring(0, name.length()-1):name;
		}
		return name2;
		
	}

	boolean restore(ImagePlus imp, int index, boolean setSlice) {
		String label = (String) listModel.getElementAt(index);
		Roi roi = (Roi)rois.get(label);
		if (imp==null || roi==null)
			return false;
		if (imp.getWindow().running || imp.getWindow().running2 || imp.getWindow().running3) {
			IJ.run("Stop Animation");
		}			
		if (setSlice) {
			int n = getSliceNumber(roi, label);
			// resets n the the proper CZT position of the current image based on n's CZT postion in the motherImp of this ROI
			if (roi.getMotherImp() !=null) {
				if (imp.isHyperStack()||imp.isComposite() || imp.getWindow() instanceof StackWindow) {
					int c =roi.getCPosition();
					int z = imp.getSlice();
					int t = imp.getFrame();
					if (roi.getName().split("_").length>3) {
						if (roi.getName().split("_")[3].contains("C")){
							c=0;
							//						IJ.log("C");
						}
						if(c==0) c = imp.getChannel();
						if(z==0) z = imp.getSlice();
						z =roi.getZPosition();  //IJ.log(""+z);
						if (roi.getName().split("_")[3].contains("Z"))
							z=0;
						t =roi.getTPosition();
						if (roi.getName().split("_")[3].contains("T"))
							t=0;
					}
					imp.setPosition(c, z, t );
				}
			}else if (n>=1 && n<=imp.getStackSize()) {
				if (imp.isHyperStack()||imp.isComposite())
					imp.setPosition(n);
				else
					imp.setSlice(n);
			}
		}
		Roi roi2 = (Roi)roi.clone();
		Calibration cal = imp.getCalibration();
		Rectangle r = roi2.getBounds();
		if (cal.xOrigin!=0.0 || cal.yOrigin!=0.0)
			roi2.setLocation(r.x+(int)cal.xOrigin, r.y+(int)cal.yOrigin);
		int width= imp.getWidth(), height=imp.getHeight();
		if (restoreCentered) {
			ImageCanvas ic = imp.getCanvas();
			if (ic!=null) {
				Rectangle r1 = ic.getSrcRect();
				Rectangle r2 = roi2.getBounds();
				roi2.setLocation(r1.x+r1.width/2-r2.width/2, r1.y+r1.height/2-r2.height/2);
			}
		}
		boolean oob = false;
		if (r.x>=width) {
			r.x = width-10;
			oob = true;
		}
		if ((r.x+r.width)<=0) {
			r.x = 10;
			oob = true;
		}
		if (r.y>=height) {
			r.y = height-10;
			oob = true;
		}
		if ((r.y+r.height)<=0) {
			r.y = 10;
			oob = true;
		}
		if (oob) roi2.setLocation(r.x, r.y);
		if (noUpdateMode) {
			imp.setRoi(roi2, false);
			noUpdateMode = false;
		} else
			imp.setRoi(roi2, true);
		return true;
	}

	boolean restoreWithoutUpdate(int index) {
		noUpdateMode = true;
		return restore(getImage(), index, false);
	}

	/** Returns the slice number associated with the specified name,
		or -1 if the name does not include a slice number. */
	public int getSliceNumber(String label) {
		int slice = -1;
		if (label.length()>=14 && label.charAt(4)=='-' && label.charAt(9)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,4),-1);
		else if (label.length()>=17 && label.charAt(5)=='-' && label.charAt(11)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,5),-1);
		else if (label.length()>=20 && label.charAt(6)=='-' && label.charAt(13)=='-')
			slice = (int)Tools.parseDouble(label.substring(0,6),-1);
		return slice;
	}

	/** Returns the slice number associated with the specified ROI or name,
		or -1 if the ROI or name does not include a slice number. */
	int getSliceNumber(Roi roi, String label) {
		int slice = roi!=null?roi.getPosition():-1;
		if (slice==0)
			slice=-1;
		if (slice==-1)
			slice = getSliceNumber(label);
		return slice;
	}

	void open(String path) {
		Macro.setOptions(null);
		String name = null;
		if (path==null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Selection(s)...", "");
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name==null)
				return;
			path = directory + name;
		}
		if (Recorder.record && !Recorder.scriptMode())
			Recorder.record("roiManager", "Open", path);
		if (path.endsWith(".zip")) {
			String origTitle = this.title;
			this.setTitle("Tag Manager LOADING!!!");
			openZip(path);
			this.setTitle(origTitle);
			textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
			imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
			imp.getWindow().countLabel.repaint();			
			return;			
		}
		if (path.endsWith(".xml")) {
			String origTitle = this.title;
			this.setTitle("Tag Manager LOADING!!!");
			openXml(path);
			this.setTitle(origTitle);
			textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
			imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
			imp.getWindow().countLabel.repaint();			
			return;			
		}
		if (path.endsWith(".csv")) {
			String origTitle = this.title;
			this.setTitle("Tag Manager LOADING!!!");
			openCsv(path);
			this.setTitle(origTitle);
			textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
			imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
			imp.getWindow().countLabel.repaint();			
			return;			
		}

		Opener o = new Opener();
		if (name==null) name = o.getName(path);
		Roi roi = o.openRoi(path);
		if (roi!=null) {
			roi.setImage(imp);
			if (name.endsWith(".roi"))
				name = name.substring(0, name.length()-4);
			name = getUniqueName(name);
			listModel.addElement(name);
			fullListModel.addElement(name);
			rois.put(name, roi);
		}		
		showAll(SHOW_ALL);
		updateShowAll();
	}

	private void openXml(String path) {
		busy = true;
		boolean wasVis = this.isVisible();
		this.setVisible(false);
		//		showAll(SHOW_ALL);
		String s = IJ.openAsString(path);
		//		IJ.log(s);
		String impTitle = this.imp.getTitle();

		String[] sLayers = s.split("<t2_layer oid=\"");
		String[] sCells = s.split("<t2_area_list");
		String[] sConnectors = s.split("<t2_connector");

		Hashtable<String, String> cellAreaHash = new Hashtable<String,String>();
		Hashtable<String, String> sConnLayerHash = new Hashtable<String,String>();

		String fillColor;
		long count =0;
		long nRois =0;
		for (int cell=1; cell<sCells.length; cell++) {
			//			counter++;
			//			if (counter<=1) continue;
			String sCell = sCells[cell];
			int offsetX = Integer.parseInt(sCell.split("(;fill:|;\")").length>1 && sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(").length>1
					?(sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(")[1]).split("[,\\.]")[8]:"0");
			int offsetY = Integer.parseInt(sCell.split("(;fill:|;\")").length>1 && sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(").length>1
					?(sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(")[1]).split("[,\\.]")[10]:"0");

			fillColor = (sCell.split("(;fill:|;\")").length>1?(sCell.split("(;fill:|;\")")[1].startsWith("#")?sCell.split("(;fill:|;\")")[1]:""):"");
			IJ.log(fillColor+" "+offsetX+" "+offsetY);
			String[] sCellAreas = sCell.split("<t2_area");
			int maxReps =0;
			int reps =0;
			for (String sCellArea:sCellAreas){
				String slicePosition = (sCellArea.split("\"").length>1?sCellArea.split("\"")[1]:"");
				for(int k=1; k< sCellArea.split("t2_path d=\"").length; k++) {
					reps =k;
					IJ.log(fillColor+"_"+slicePosition+"_"+k+" "+offsetX+" "+offsetY);
					cellAreaHash.put(fillColor+"_"+slicePosition+"_"+k, sCellArea.split("t2_path d=\"").length>1?sCellArea.split("t2_path d=\"")[k].split("\"")[0]:"");
				}
				maxReps = maxReps<reps?reps:maxReps;
			}

			for (int sl=0; sl< sLayers.length; sl++){				
				count++;
				this.setTitle(  "Tag Manager" + ((nRois%100>50)?" LOADING!!!":" Loading...") );
				if (nRois%100>50){
					IJ.runMacro("print(\"\\\\Update:***Tag Manager is still loading tags...***"+count+"\");");
					this.imp.setTitle("***"+ impTitle);
				} 
				else {
					IJ.runMacro("print(\"\\\\Update:   Tag Manager is still loading tags...   "+count+"\");");
					this.imp.setTitle("   "+ impTitle);
				} 

				String sLayer=sLayers[sl];
				int sliceNumber = 0;
				IJ.log(cellAreaHash.get(fillColor+"_"+sLayer.split("\"")[0]));
				String areaString = null;
				for (int rep=1; rep<maxReps; rep++) {
					areaString = cellAreaHash.get(fillColor+"_"+sLayer.split("\"")[0]+"_"+rep);
					if (areaString != null) {
						sliceNumber = Integer.parseInt(sLayer.split("file_path=")[1].split("\"")[1].replaceAll(".*_", "").replaceAll(".tiff*", ""));
						sConnLayerHash.put(sLayer.split("\"")[0], ""+sliceNumber);
						String[] coordStrings = areaString.replaceAll("M", "").split(" L ");
						int[] xCoords = new int[coordStrings.length];
						int[] yCoords = new int[coordStrings.length];
						for (int i=0; i<coordStrings.length; i++){
							if (true){
								xCoords[i] = Integer.parseInt(coordStrings[i].trim().split(" ")[0])+offsetX;

								yCoords[i] = Integer.parseInt(coordStrings[i].trim().split(" ")[1])+offsetY;
							}
						}
						Roi pRoi = new PolygonRoi(xCoords,yCoords,yCoords.length,Roi.FREEROI);
						pRoi.setImage(imp);
						listModel.addElement(fillColor); 
						fullListModel.addElement(fillColor);
						rois.put(fillColor, pRoi); 
						nRois++;

						pRoi.setFillColor(Colors.decode(fillColor.replace("#", "#33"), defaultColor));
						pRoi.setPosition(1,1,sliceNumber);
						//						list.setSelectedIndex(this.getCount()-1);
						this.rename(fillColor, new int[] {this.getCount()-1}, false);
					}
				}
			}
		}

		for (int sc=1; sc<sConnectors.length; sc++){
			count++;
			this.setTitle(  "Tag Manager" + ((nRois%100>50)?" LOADING!!!":" Loading...") );
			if (nRois%100>50){
				IJ.runMacro("print(\"\\\\Update:***Tag Manager is still loading tags...***"+count+"\");");
				this.imp.setTitle("***"+ impTitle);
			} 
			else {
				IJ.runMacro("print(\"\\\\Update:   Tag Manager is still loading tags...   "+count+"\");");
				this.imp.setTitle("   "+ impTitle);
			} 

			String sConnector = sConnectors[sc];
			String connLayer = sConnector.split("lid=\"").length>1?(sConnector.split("lid=\"")[1].split("\"")[0]):"";
			String connOffsetX = sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[4].split("\\.")[0]:"";
			String connOffsetY = sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[5].split("\\.")[0]:"";
			String coordsXY = sConnector.split("<t2_node x=").length>2?
					sConnector.split("<t2_node x=")[1].split("\"")[1]+"_"+
					sConnector.split("<t2_node x=")[1].split("\"")[3]+"_"+
					sConnector.split("<t2_node x=")[2].split("\"")[1]+"_"+
					sConnector.split("<t2_node x=")[2].split("\"")[3]:"";
					//			String connLayer = sConnector.split("<t2_node x=")[2].split("\"")[5];
					String connStroke = sConnector.split("stroke:")[1].split(";")[0];

					String[] connCoords = coordsXY.split("[_]");
					if (connCoords.length==4) {
						double x1 = Double.parseDouble(connCoords[0].split("\\.")[0])/* + Integer.parseInt(connOffsetX)*/;
						double y1 = Double.parseDouble(connCoords[1].split("\\.")[0])/* + Integer.parseInt(connOffsetY)*/;
						double x2 = Double.parseDouble(connCoords[2].split("\\.")[0])/* + Integer.parseInt(connOffsetX)*/;
						double y2 = Double.parseDouble(connCoords[3].split("\\.")[0])/* + Integer.parseInt(connOffsetY)*/;
						int sliceNumber = Integer.parseInt(sConnLayerHash.get(connLayer));
						//						imp.setPositionWithoutUpdate(1, 1, sliceNumber);
						double[] preAffinePoints = {x1,y1,x2,y2};
						double[] postAffinePoints = {0,0,0,0};
						AffineTransform at = new AffineTransform(	(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[0]:"")),
								(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[1]:"")),
								(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[2]:"")),
								(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[3]:"")),
								(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[4]:"")),
								(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length>1? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[5]:"")));
						at.transform(preAffinePoints, 0, postAffinePoints, 0, 2);
						Roi aRoi = new Arrow(postAffinePoints[0],
								postAffinePoints[1],
								postAffinePoints[2],
								postAffinePoints[3]);
						aRoi.setImage(imp);

						listModel.addElement(connStroke); 
						fullListModel.addElement(connStroke);
						rois.put(connStroke, aRoi); 
						nRois++;

						aRoi.setStrokeColor(Colors.decode(connStroke, defaultColor));
						aRoi.setPosition(1,1,sliceNumber);

						//						list.setSelectedIndex(this.getCount()-1);
						this.rename(connStroke, new int[] {this.getCount()-1}, false);
					}
		}
		updateShowAll();
		this.imp.setTitle(impTitle);
		this.setVisible(wasVis);
		busy = false;
	}

	void defineConnectors() {
		for (int i=0;i<getFullRoisAsArray().length; i++){
			Roi roi = getFullRoisAsArray()[i];

			if (roi instanceof Arrow) {
				//				 float[] arrowPoints = ((Arrow) roi).getPoints();
				int tPosition = roi.getTPosition();
				String[] endNames = {"NoName","NoName"};
				for  (Roi roi2:getFullRoisAsArray()){
					if (endNames[0]=="NoName" && tPosition == roi2.getTPosition() 
							&& roi2.isArea()  && (new ShapeRoi(roi2).contains((int)((Line)roi).x1d, (int)((Line)roi).y1d))){ 
						endNames[0] = roi2.getName().split("[|\"]")[1].trim();
					}
					if (endNames[1]=="NoName" && tPosition == roi2.getTPosition() && roi2.isArea()  
							&& (new ShapeRoi(roi2).contains((int)((Line)roi).x2d, (int)((Line)roi).y2d))){
						endNames[1] = roi2.getName().split("[|\"]")[1].trim();
					}
				}					 
				int[] array = {i};
				//				 this.setSelectedIndexes(array);
				rename("synapse:"+endNames[0]+">"+endNames[1], array, false);
			}
		}
		updateShowAll();
	}

	// Modified on 2005/11/15 by Ulrik Stervbo to only read .roi files and to not empty the current list
	void openZip(String path) { 
		busy = true;
		boolean wasVis = this.isVisible();
		this.setVisible(false);
		showAll(SHOW_NONE);
		showAllCheckbox.setState(false);

		ZipInputStream in = null; 
		ByteArrayOutputStream out; 
		Roi messageRoi;
		long nRois = 0; 
		try { 
			if (!path.startsWith("/Volumes/GLOWORM_DATA/"))
				in = new ZipInputStream(new FileInputStream(path)); 
			else if ((new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path)).exists())
				in = new ZipInputStream(new FileInputStream(new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path))); 
			else {
				if (imp.getRemoteMQTVSHandler().getCompQ().getFileInputByteArray(path) != null)
					in = new ZipInputStream(new ByteArrayInputStream(imp.getRemoteMQTVSHandler().getCompQ().getFileInputByteArray(path)));
			}
			byte[] buf = new byte[1024]; 
			int len; 
			if (in == null)
				return;
			ZipEntry entry = in.getNextEntry(); 
			IJ.log("");
			String impTitle = this.imp.getTitle();
			long count =0;
			// fill up the list here
			//			this.removeAll();
			long timeLast = 0;
			long timeNow = 0;


			while (entry!=null) { 
				timeNow = System.currentTimeMillis();
				if (timeNow > timeLast + 100) {
					timeLast = timeNow;
					Graphics g = imp.getCanvas().getGraphics();
					if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
						imp.getCanvas().messageRois.remove("Loading Tags");

					messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
							"   Loading Tags:\n   " + "   ..."+count+ " features tagged\n"   );

					((TextRoi) messageRoi).setCurrentFont(g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width/16)));
					messageRoi.setStrokeColor(Color.black);
					messageRoi.setFillColor(Colors.decode("#99ffffdd",
							imp.getCanvas().getDefaultColor()));

					imp.getCanvas().messageRois.put("Loading Tags", messageRoi);
					imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());

				}
				this.setTitle(  "Tag Manager" + ((nRois%100>50)?" LOADING!!!":" Loading...") );
				if (nRois%100>50){
					//					IJ.runMacro("print(\"\\\\Update:***Tag Manager is still loading tags...***"+count+"\");");
					this.imp.setTitle("***"+ impTitle);
				} 
				else {
					//					IJ.runMacro("print(\"\\\\Update:   Tag Manager is still loading tags...   "+count+"\");");
					this.imp.setTitle("   "+ impTitle);
				} 
				count++;

				String name = entry.getName(); 
				if (name.endsWith(".roi")) { 
					out = new ByteArrayOutputStream(); 
					while ((len = in.read(buf)) > 0) 
						out.write(buf, 0, len); 
					out.close(); 
					byte[] bytes = out.toByteArray(); 
					RoiDecoder rd = new RoiDecoder(bytes, name); 
					Roi roi = rd.getRoi(); 
					//
					ColorLegend cl = getColorLegend();
					//
					if (roi!=null) { 
						if (cl != null) {
							Color clColor = cl.getBrainbowColors()
												.get(roi.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
							if (clColor !=null) {
								String hexRed = Integer.toHexString(clColor.getRed());
								String hexGreen = Integer.toHexString(clColor.getGreen());
								String hexBlue = Integer.toHexString(clColor.getBlue());
								roi.setFillColor(Colors.decode("#88"+(hexRed.length()==1?"0":"")+hexRed
																	+(hexGreen.length()==1?"0":"")+hexGreen
																	+(hexBlue.length()==1?"0":"")+hexBlue
																, Color.white));
							}
						}
						roi.setImage(imp);
						name = name.substring(0, name.length()-4);
						name = getUniqueName(name); 
						if (roi instanceof TextRoi) {
							name = (((TextRoi)roi).getText().indexOf("\n")>0?
									("\""+((TextRoi)roi).getText().replace("\n"," ")+"\""):"Blank") 
									+"_"+ name.split("_")[1] +"_"+ name.split("_")[2] +"_"+name.split("_")[3];

						}
						listModel.addElement(name); 
						fullListModel.addElement(name);
						rois.put(name, roi); 
						((Roi) rois.get(name)).setName(name);  //weird but necessary, and logically so

						nRois++;
						String nameEndReader = name;
						ImagePlus imp = this.imp;

						int c = roi.getCPosition();
						int z = roi.getZPosition();
						int t = roi.getTPosition();
						if (name.split("_").length == 4) {
							c = Integer.parseInt(name.split("_")[1]);
							z = Integer.parseInt(name.split("_")[2]);
							t = Integer.parseInt(name.split("_")[3].split("[CZT-]")[0]);
						}
						while (nameEndReader.endsWith("C") || nameEndReader.endsWith("Z") || nameEndReader.endsWith("T") ) {
							if (nameEndReader.endsWith("C") ){
								c = 0;
								nameEndReader = nameEndReader.substring(0, nameEndReader.length()-1);
							}
							if (nameEndReader.endsWith("Z") ){
								z = 0;
								nameEndReader = nameEndReader.substring(0, nameEndReader.length()-1);
							}
							if (nameEndReader.endsWith("T") ){
								t = 0;
								nameEndReader = nameEndReader.substring(0, nameEndReader.length()-1);
							}
						}
						roi.setPosition(c, z, t);
					} 
				} 
				entry = in.getNextEntry(); 
			} 
			in.close(); 
			this.imp.setTitle(impTitle);
			if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
				imp.getCanvas().messageRois.remove("Loading Tags");
		} catch (IOException e) {error(e.toString());} 
		if (in == null)
			return;
		if(nRois==0)
			error("This ZIP archive does not appear to contain \".roi\" files");

		Roi[] roiArray = getFullRoisAsArray();
		int n = roiArray.length;
		Roi[] clonedArray = new Roi[n];
		for (int i=0; i<n; i++) {
			if (roiArray[i] instanceof TextRoi)
				clonedArray[i] = (TextRoi) roiArray[i].clone();
			else
				clonedArray[i] = (Roi) roiArray[i].clone();

			clonedArray[i].setPosition(roiArray[i].getCPosition(), roiArray[i].getZPosition(), roiArray[i].getTPosition());
		}
		originalRois = clonedArray;
		originalsCloned = true;
		if (imp.getMultiChannelController()!=null)
			imp.getMultiChannelController().updateRoiManager();
		this.setVisible(wasVis);
		showAll(SHOW_ALL);
		updateShowAll();
		showAllCheckbox.setState(true);

		imp.getCanvas().messageRois.remove("Loading Tags");
		imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
		messageRoi = null;
		busy = false;
		if (path.startsWith("/Volumes/GLOWORM_DATA/") && !(new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path)).exists()) {
			saveMultiple(this.getFullListIndexes(), IJ.getDirectory("home")+"CytoSHOWCacheFiles"+path);
		}
	} 


	String getUniqueName(String name) {
		String name2 = name + (addRoiSpanC?"C":"") + (addRoiSpanZ?"Z":"") + (addRoiSpanT?"T":"");
		String suffix ="";
		while (name2.endsWith("C") || name2.endsWith("Z") || name2.endsWith("T")) {
			suffix = (suffix.contains(name2.substring(name2.length()-1) )?"":name2.substring(name2.length()-1) ) + suffix;
			name2 = name2.substring(0, name2.length()-1);
		}
		Roi roi2 = (Roi)rois.get(name2 + suffix);
		int n = 1;
		while (roi2!=null) {
			roi2 = (Roi)rois.get(name2+suffix);
			if (roi2!=null) {
				int lastDash = name2.lastIndexOf("-");
				if (lastDash!=-1 && name2.length()-lastDash<5)
					name2 = name2.substring(0, lastDash);
				name2 = name2+"-"+n;

				n++;
			}
			roi2 = (Roi)rois.get(name2 + suffix);
		}
		return name2 + suffix;
	}

	boolean save() {
		if (listModel.getSize()==0)
			return error("The selection list is empty.");
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		if (indexes.length>1)
			return saveMultiple(indexes, null);
		String name = (String) listModel.getElementAt(indexes[0]);
		Macro.setOptions(null);
		SaveDialog sd = new SaveDialog("Save Selection...", imp.getShortTitle()+"_"+name, ".roi");
		String name2 = sd.getFileName();
		if (name2 == null)
			return false;
		String dir = sd.getDirectory();
		Roi roi = (Roi)rois.get(name);
		rois.remove(name);
		if (!name2.endsWith(".roi")) name2 = name2+".roi";
		String newName = name2.substring(0, name2.length()-4);
		rois.put(newName, roi);
		roi.setName(newName);
		listModel.setElementAt(newName, indexes[0]);
		fullListModel.setElementAt(newName, indexes[0]);
		RoiEncoder re = new RoiEncoder(dir+name2);
		try {
			re.write(roi);
		} catch (IOException e) {
			IJ.error("Tag Manager", e.getMessage());
		}
		return true;
	}

	boolean saveMultiple(int[] indexes, String path) {
		Macro.setOptions(null);
		if (path==null) {
			SaveDialog sd = new SaveDialog("Save ROIs...", 
					(imp.getShortTitle().contains("_scene")?
							imp.getShortTitle().substring(0,imp.getShortTitle().indexOf("_scene")):
								imp.getShortTitle() ) +"_"+"ROIs", ".zip");
			String name = sd.getFileName();
			if (name == null)
				return false;
			if (!(name.endsWith(".zip") || name.endsWith(".ZIP")))
				name = name + ".zip";
			String dir = sd.getDirectory();
			path = dir+name;
		}
		try {
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(path)));
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
			RoiEncoder re = new RoiEncoder(out);
			for (int i=0; i<indexes.length; i++) {
				String label = (String) listModel.getElementAt(indexes[i]);
				Roi roi = (Roi)rois.get(label);
				if (!label.endsWith(".roi")) label += ".roi";
				zos.putNextEntry(new ZipEntry(label));
				re.write(roi);
				out.flush();
			}
			out.close();
		}
		catch (IOException e) {
			error(""+e);
			return false;
		}
		if (record()) Recorder.record("roiManager", "Save", path);
		return true;
	}

	boolean measure(int mode) {
		ImagePlus imp = this.imp;

		if (imp==null)
			return false;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		if (indexes.length==0) return false;
		boolean allSliceOne = true;
		for (int i=0; i<indexes.length; i++) {
			String label = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi)rois.get(label);
			if (getSliceNumber(roi,label)>1) allSliceOne = false;
		}
		int measurements = Analyzer.getMeasurements();
		if (imp.getStackSize()>1)
			Analyzer.setMeasurements(measurements|Measurements.SLICE);
		int currentSlice = imp.getCurrentSlice();
		for (int i=0; i<indexes.length; i++) {
			if (restore(getImage(), indexes[i], !allSliceOne))
				IJ.run("Measure");
			else
				break;
		}
		imp.setSlice(currentSlice);
		Analyzer.setMeasurements(measurements);
		if (indexes.length>1)
			IJ.run("Select None");
		if (record()) Recorder.record("roiManager", "Measure");
		return true;
	}	

	/*
	void showIndexes(int[] indexes) {
		for (int i=0; i<indexes.length; i++) {
			String label = list.getItem(indexes[i]);
			Roi roi = (Roi)rois.get(label);
			IJ.log(i+" "+roi.getName());
		}
	}
	 */

	/* This method performs measurements for several ROI's in a stack
		and arranges the results with one line per slice.  By constast, the 
		measure() method produces several lines per slice.  The results 
		from multiMeasure() may be easier to import into a spreadsheet 
		program for plotting or additional analysis. Based on the multi() 
		method in Bob Dougherty's Multi_Measure plugin
		(http://www.optinav.com/Multi-Measure.htm).
	 */
	boolean multiMeasure() {
		ImagePlus imp = this.imp;

		if (imp==null) return false;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		if (indexes.length==0) return false;
		int measurements = Analyzer.getMeasurements();

		int nSlices = imp.getStackSize();
		if (IJ.isMacro()) {
			if (nSlices>1) measureAll = true;
			onePerSlice = true;
		} else {
			GenericDialog gd = new GenericDialog("Multi Measure");
			if (nSlices>1)
				gd.addCheckbox("Measure All "+nSlices+" Slices", measureAll);
			gd.addCheckbox("One Row Per Slice", onePerSlice);
			int columns = getColumnCount(imp, measurements)*indexes.length;
			String str = nSlices==1?"this option":"both options";
			gd.setInsets(10, 25, 0);
			gd.addMessage(
					"Enabling "+str+" will result\n"+
							"in a table with "+columns+" columns."
					);
			gd.showDialog();
			if (gd.wasCanceled()) return false;
			if (nSlices>1)
				measureAll = gd.getNextBoolean();
			onePerSlice = gd.getNextBoolean();
		}
		if (!measureAll) nSlices = 1;
		int currentSlice = imp.getCurrentSlice();

		if (!onePerSlice) {
			int measurements2 = nSlices>1?measurements|Measurements.SLICE:measurements;
			ResultsTable rt = new ResultsTable();
			Analyzer analyzer = new Analyzer(imp, measurements2, rt);
			for (int slice=1; slice<=nSlices; slice++) {
				if (nSlices>1) imp.setSliceWithoutUpdate(slice);
				for (int i=0; i<indexes.length; i++) {
					if (restoreWithoutUpdate(indexes[i]))
						analyzer.measure();
					else
						break;
				}
			}
			rt.show("Results");
			if (nSlices>1) imp.setSlice(currentSlice);
			return true;
		}

		Analyzer aSys = new Analyzer(imp); //System Analyzer
		ResultsTable rtSys = Analyzer.getResultsTable();
		ResultsTable rtMulti = new ResultsTable();
		Analyzer aMulti = new Analyzer(imp, measurements, rtMulti); //Private Analyzer

		for (int slice=1; slice<=nSlices; slice++) {
			int sliceUse = slice;
			if(nSlices == 1)sliceUse = currentSlice;
			imp.setSliceWithoutUpdate(sliceUse);
			rtMulti.incrementCounter();
			int roiIndex = 0;
			for (int i=0; i<indexes.length; i++) {
				if (restoreWithoutUpdate(indexes[i])) {
					roiIndex++;
					aSys.measure();
					for (int j=0; j<=rtSys.getLastColumn(); j++){
						float[] col = rtSys.getColumn(j);
						String head = rtSys.getColumnHeading(j);
						String suffix = ""+roiIndex;
						Roi roi = imp.getRoi();
						if (roi!=null) {
							String name = roi.getName();
							if (name!=null && name.length()>0 && (name.length()<9||!Character.isDigit(name.charAt(0))))
								suffix = "("+name+")";
						}
						if (head!=null && col!=null && !head.equals("Slice"))
							rtMulti.addValue(head+suffix,rtSys.getValue(j,rtSys.getCounter()-1));
					}
				} else
					break;
			}
			//aMulti.displayResults();
			//aMulti.updateHeadings();
		}
		rtMulti.show("Results");

		imp.setSlice(currentSlice);
		if (indexes.length>1)
			IJ.run("Select None");
		if (record()) Recorder.record("roiManager", "Multi Measure");
		return true;
	}

	int getColumnCount(ImagePlus imp, int measurements) {
		ImageStatistics stats = imp.getStatistics(measurements);
		ResultsTable rt = new ResultsTable();
		Analyzer analyzer = new Analyzer(imp, measurements, rt);
		analyzer.saveResults(stats, null);
		int count = 0;
		for (int i=0; i<=rt.getLastColumn(); i++) {
			float[] col = rt.getColumn(i);
			String head = rt.getColumnHeading(i);
			if (head!=null && col!=null)
				count++;
		}
		return count;
	}

	void multiPlot() {
		ImagePlus imp = this.imp;

		if (imp==null) return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0) indexes = getAllShownIndexes();
		int n = indexes.length;
		if (n==0) return;
		Color[] colors = {Color.blue, Color.green, Color.magenta, Color.red, Color.cyan, Color.yellow};
		if (n>colors.length) {
			colors = new Color[n];
			double c = 0;
			double inc =150.0/n;
			for (int i=0; i<n; i++) {
				colors[i] = new Color((int)c, (int)c, (int)c);
				c += inc;
			}
		}
		int currentSlice = imp.getCurrentSlice();
		double[][] x = new double[n][];
		double[][] y = new double[n][];
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		double fixedMin = ProfilePlot.getFixedMin();
		double fixedMax = ProfilePlot.getFixedMax();	
		boolean freeYScale = fixedMin==0.0 && fixedMax==0.0;
		if (!freeYScale) {
			minY = fixedMin;
			maxY = fixedMax;
		}
		int maxX = 0;
		Calibration cal = imp.getCalibration();
		double xinc = cal.pixelWidth;
		for (int i=0; i<indexes.length; i++) {
			if (!restore(getImage(), indexes[i], true)) break;
			Roi roi = imp.getRoi();
			if (roi==null) break;
			if (roi.isArea() && roi.getType()!=Roi.RECTANGLE)
				IJ.run(imp, "Area to Line", "");
			ProfilePlot pp = new ProfilePlot(imp, IJ.altKeyDown());
			y[i] = pp.getProfile();
			if (y[i]==null) break;
			if (y[i].length>maxX) maxX = y[i].length;
			if (freeYScale) {
				double[] a = Tools.getMinMax(y[i]);
				if (a[0]<minY) minY=a[0];
				if (a[1]>maxY) maxY = a[1];
			}
			double[] xx = new double[y[i].length];
			for (int j=0; j<xx.length; j++)
				xx[j] = j*xinc;
			x[i] = xx;
		}
		String xlabel = "Distance ("+cal.getUnits()+")";
		Plot plot = new Plot("Profiles",xlabel, "Value", x[0], y[0]);
		plot.setLimits(0, maxX*xinc, minY, maxY);
		for (int i=1; i<indexes.length; i++) {
			plot.setColor(colors[i]);
			if (x[i]!=null)
				plot.addPoints(x[i], y[i], Plot.LINE);
		}
		plot.setColor(colors[0]);
		if (x[0]!=null)
			plot.show();
		imp.setSlice(currentSlice);
		if (indexes.length>1)
			IJ.run("Select None");
		if (record()) Recorder.record("roiManager", "Multi Plot");
	}	

	boolean drawOrFill(int mode) {
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		ImagePlus imp = this.imp;
		imp.deleteRoi();
		ImageProcessor ip = imp.getProcessor();
		ip.setColor(Toolbar.getForegroundColor());
		ip.snapshot();
		Undo.setup(Undo.FILTER, imp);
		Filler filler = mode==LABEL?new Filler():null;
		int slice = imp.getCurrentSlice();
		for (int i=0; i<indexes.length; i++) {
			String name = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi)rois.get(name);
			int type = roi.getType();
			if (roi==null) continue;
			if (mode==FILL&&(type==Roi.POLYLINE||type==Roi.FREELINE||type==Roi.ANGLE))
				mode = DRAW;
			int slice2 = getSliceNumber(roi, name);
			if (slice2>=1 && slice2<=imp.getStackSize()) {
				imp.setSlice(slice2);
				ip = imp.getProcessor();
				ip.setColor(Toolbar.getForegroundColor());
				if (slice2!=slice) Undo.reset();
			}
			switch (mode) {
			case DRAW: roi.drawPixels(ip); break;
			case FILL: ip.fill(roi); break;
			case LABEL:
				roi.drawPixels(ip);
				filler.drawLabel(imp, ip, i+1, roi.getBounds());
				break;
			}
		}
		ImageCanvas ic = imp.getCanvas();
		if (ic!=null) ic.setShowAllROIs(false);
		imp.updateAndDraw();
		return true;
	}

	void setProperties(Color color, int lineWidth, Color fillColor) {
		boolean showDialog = color==null && lineWidth==-1 && fillColor==null;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		int n = indexes.length;
		if (n==0) return;
		Roi rpRoi = null;
		String rpName = null;
		Font font = null;
		int justification = TextRoi.LEFT;
		double opacity = -1;
		if (showDialog) {
			String label = (String) listModel.getElementAt(indexes[0]);
			rpRoi = (Roi)rois.get(label);
			if (n==1) {
				fillColor =  rpRoi.getFillColor();
				rpName = rpRoi.getName();
			}
			if (rpRoi.getStrokeColor()==null)
				rpRoi.setStrokeColor(ImageCanvas.getShowAllColor());
			rpRoi = (Roi) rpRoi.clone();
			if (n>1)
				rpRoi.setName("range: "+(indexes[0]+1)+"-"+(indexes[n-1]+1));
			rpRoi.setFillColor(fillColor!=null?fillColor:Colors.decode("#00000000", Color.black));
			RoiProperties rp = new RoiProperties("Properties", rpRoi);
			if (!rp.showDialog())
				return;
			lineWidth =  (int)rpRoi.getStrokeWidth();
			defaultLineWidth = lineWidth;
			color =  rpRoi.getStrokeColor();
			fillColor =  rpRoi.getFillColor();
			defaultColor = color;
			if (rpRoi instanceof TextRoi) {
				font = ((TextRoi)rpRoi).getCurrentFont();
				justification = ((TextRoi)rpRoi).getJustification();
			}
			if (rpRoi instanceof ImageRoi)
				opacity = ((ImageRoi)rpRoi).getOpacity();
		}
		ImagePlus imp = this.imp;
		if (n==listModel.getSize() && n>1 && !IJ.isMacro()) {
			GenericDialog gd = new GenericDialog("Tag Manager");
			gd.addMessage("Apply changes to all "+n+" selections?");
			gd.showDialog();
			if (gd.wasCanceled()) return;
		}
		ColorLegend cl = this.getColorLegend();
		if (cl!=null) {
			ArrayList<Integer> hitIndexes = new ArrayList<Integer>();
			for (int i=0; i<n; i++) {
				String label = (String) listModel.getElementAt(indexes[i]);
				Color currentBBColor = this.getSelectedRoisAsArray()[0].getFillColor();
				String hexAlpha = Integer.toHexString(fillColor.getAlpha());
				String hexRed = Integer.toHexString(fillColor.getRed());
				String hexGreen = Integer.toHexString(fillColor.getGreen());
				String hexBlue = Integer.toHexString(fillColor.getBlue());
				String fillRGBstring = "#"+(hexAlpha.length()==1?"0":"")+hexAlpha
												+(hexRed.length()==1?"0":"")+hexRed
												+(hexGreen.length()==1?"0":"")+hexGreen
												+(hexBlue.length()==1?"0":"")+hexBlue; 
				int fillRGB = fillColor.getRGB();
				int currentRGB = currentBBColor!=null?currentBBColor.getRGB():0;
				if (fillRGB == currentRGB)
					return;
				
				while (cl.getBrainbowColors().contains(new Color(fillColor.getRGB())) && fillColor.getRGB() != currentRGB) {
					if (fillColor.getBlue()<255) {
						hexBlue = Integer.toHexString(fillColor.getBlue()+1);
					} else if (fillColor.getGreen()<255) {
						hexGreen = Integer.toHexString(fillColor.getGreen()+1);
					} else if (fillColor.getRed()<255) {
						hexRed = Integer.toHexString(fillColor.getRed()+1);
					} 
					fillRGBstring = "#"+(hexAlpha.length()==1?"0":"")+hexAlpha
							+(hexRed.length()==1?"0":"")+hexRed
							+(hexGreen.length()==1?"0":"")+hexGreen
							+(hexBlue.length()==1?"0":"")+hexBlue; 
					fillColor = Colors.decode(fillRGBstring, fillColor);
				}
				cl.getBrainbowColors().put(label.split(" =")[0].replace("\"","").toLowerCase(), new Color(fillColor.getRGB()));				
				for (Checkbox cbC:cl.getCheckbox()) {
					if (cbC.getName().equals(label.split(" =")[0].replace("\"",""))){
						cbC.setBackground(new Color(fillColor.getRGB()));
					}
				}
				for (int l=0; l<listModel.size(); l++) {
					if (((String) listModel.getElementAt(l)).startsWith(label.split(" =")[0])) {
						hitIndexes.add(l);
					}
				}
			}
			indexes = new int[hitIndexes.size()];
			n=indexes.length;
			for (int h=0;h<indexes.length;h++) {
				indexes[h] = ((int)hitIndexes.get(h));
			}
		}
		for (int i=0; i<n; i++) {
			String label = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi)rois.get(label);
			//IJ.log("set "+color+"  "+lineWidth+"  "+fillColor);
			if (color!=null) 
				roi.setStrokeColor(color);
			if (lineWidth>=0) 
				roi.setStrokeWidth(lineWidth);
			roi.setFillColor(fillColor);
			if (brainbowColors == null)
				brainbowColors = new Hashtable<String, Color>();
			if (fillColor!=null)
				brainbowColors.put(label.split(" =")[0].replace("\"","").toLowerCase(), new Color(fillColor.getRGB()));
			if (roi!=null && (roi instanceof TextRoi)) {
				roi.setImage(imp);
				if (font!=null)
					((TextRoi)roi).setCurrentFont(font);
				((TextRoi)roi).setJustification(justification);
				roi.setImage(null);
			}
			if (roi!=null && (roi instanceof ImageRoi) && opacity!=-1)
				((ImageRoi)roi).setOpacity(opacity);
		}
		if (rpRoi!=null && rpName!=null && !rpRoi.getName().equals(rpName))
			rename(rpRoi.getName(), null, true);
		ImageCanvas ic = imp!=null?imp.getCanvas():null;
		Roi roi = imp!=null?imp.getRoi():null;
		boolean showingAll = ic!=null &&  ic.getShowAllROIs();
		if (roi!=null && (n==1||!showingAll)) {
			if (lineWidth>=0) roi.setStrokeWidth(lineWidth);
			if (color!=null) roi.setStrokeColor(color);
			if (fillColor!=null) roi.setFillColor(fillColor);
			if (roi!=null && (roi instanceof TextRoi)) {
				((TextRoi)roi).setCurrentFont(font);
				((TextRoi)roi).setJustification(justification);
			} else {
				IJ.log("nontext match: "+roi.getName());
			}
			if (roi!=null && (roi instanceof ImageRoi) && opacity!=-1)
				((ImageRoi)roi).setOpacity(opacity);
		}
		if (lineWidth>1 && !showingAll && roi==null) {
			showAll(SHOW_ALL);
			showingAll = true;
		}
		if (imp!=null) imp.draw();
		if (record()) {
			if (fillColor!=null)
				Recorder.record("roiManager", "Set Fill Color", Colors.colorToString(fillColor));
			else {
				Recorder.record("roiManager", "Set Color", Colors.colorToString(color!=null?color:Color.red));
				Recorder.record("roiManager", "Set Line Width", lineWidth);
			}
		}
	}

	void flatten() {
		ImagePlus imp = this.imp;
		if (imp==null)
		{IJ.noImage(); return;}
		ImageCanvas ic = imp.getCanvas();
		if (!ic.getShowAllROIs() && ic.getDisplayList()==null && imp.getRoi()==null)
			error("Image does not have an overlay or ROI");
		else
			IJ.doCommand("Flatten"); // run Image>Flatten in separate thread
	}

	public boolean getDrawLabels() {
		return labelsCheckbox.getState();
	}

	void combine() {
		ImagePlus imp = this.imp;
		if (imp==null) return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		int nPointRois = 0;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(listModel.getElementAt(indexes[i]));
			if (roi.getType()==Roi.POINT)
				nPointRois++;
			else
				break;
		}
		if (nPointRois==indexes.length)
			combinePoints(imp, indexes);
		else
			combineRois(imp, indexes);
		if (record()) Recorder.record("roiManager", "Combine");
	}

	void combineRois(ImagePlus imp, int[] indexes) {
		ShapeRoi s1=null, s2=null;
		ImageProcessor ip = null;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(listModel.getElementAt(indexes[i]));
			if (!roi.isArea() && !(roi instanceof Arrow)) {
				if (ip==null)
					ip = new ByteProcessor(imp.getWidth(), imp.getHeight());
				roi = convertLineToPolygon(roi, ip);
			}
			if (roi instanceof Arrow) {
				roi = ((Arrow) roi).getShapeRoi();
			}
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi;
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi;
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.or(s2);
			}
		}
		if (s1!=null)
			imp.setRoi(s1);
	}

	Roi convertLineToPolygon(Roi lRoi, ImageProcessor ip) {
		if (lRoi==null) return null;
		Roi pRoi = new PolygonRoi(((Line)lRoi).getPolygon(), Roi.POLYGON);
		return pRoi;
		//		ip.resetRoi();
		//		ip.setColor(0);
		//		ip.fill();
		//		ip.setColor(255);
		//		if (roi.getType()==Roi.LINE && roi.getStrokeWidth()>1)
		//			ip.fillPolygon(roi.getPolygon());
		//		else
		//			roi.drawPixels(ip);
		//		//new ImagePlus("ip", ip.duplicate()).show();
		//		ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		//		ThresholdToSelection tts = new ThresholdToSelection();
		//		return tts.convert(ip);
	}

	void combinePoints(ImagePlus imp, int[] indexes) {
		int n = indexes.length;
		Polygon[] p = new Polygon[n];
		int points = 0;
		for (int i=0; i<n; i++) {
			Roi roi = (Roi)rois.get(listModel.getElementAt(indexes[i]));
			p[i] = roi.getPolygon();
			points += p[i].npoints;
		}
		if (points==0) return;
		int[] xpoints = new int[points];
		int[] ypoints = new int[points];
		int index = 0;
		for (int i=0; i<p.length; i++) {
			for (int j=0; j<p[i].npoints; j++) {
				xpoints[index] = p[i].xpoints[j];
				ypoints[index] = p[i].ypoints[j];
				index++;
			}	
		}
		imp.setRoi(new PointRoi(xpoints, ypoints, xpoints.length));
	}

	void and() {
		ImagePlus imp = this.imp;
		if (imp==null) return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		ShapeRoi s1=null, s2=null;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(listModel.getElementAt(indexes[i]));
			if (!roi.isArea()) continue;
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.and(s2);
			}
		}
		if (s1!=null) imp.setRoi(s1);
		if (record()) Recorder.record("roiManager", "AND");
	}

	void xor() {
		ImagePlus imp = this.imp;
		if (imp==null) return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length==1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		ShapeRoi s1=null, s2=null;
		for (int i=0; i<indexes.length; i++) {
			Roi roi = (Roi)rois.get(listModel.getElementAt(indexes[i]));
			if (!roi.isArea()) continue;
			if (s1==null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi)roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1==null) return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi)roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2==null) continue;
				s1.xor(s2);
			}
		}
		if (s1!=null) imp.setRoi(s1);
		if (record()) Recorder.record("roiManager", "XOR");
	}

	void addParticles() {
		String err = IJ.runMacroFile("ij.jar:AddParticles", null);
		if (err!=null && err.length()>0)
			error(err);
	}

	void sort() {
		busy = true;
		//		int n = rois.size();
		//		if (n==0) return;
		String[] labels = new String[listModel.getSize()];
		for (int i=0; i<labels.length;i++)
			labels[i]= (String) listModel.get(i);
		String[] fullLabels = new String[fullListModel.getSize()];
		for (int i=0; i<fullLabels.length;i++)
			fullLabels[i] = (String) fullListModel.get(i);

		int index = 0;
		//		for (Enumeration en=rois.keys(); en.hasMoreElements();)
		//			labels[index++] = (String)en.nextElement();
		listModel.removeAllElements();
		fullListModel.removeAllElements();				
		this.setTitle(  "Tag Manager SORTING!!!") ;

		if (sortmode > 0) {
			RoiLabelByNumbersSorter.sort(labels, sortmode);	
			RoiLabelByNumbersSorter.sort(fullLabels, sortmode);	

		} else {
			StringSorter.sort(labels);
			StringSorter.sort(fullLabels);

		}
		int numSorted =0;
		//		Dimension dim = list.getSize();
		//		list.setSize(0,0);
		for (int i=0; i<labels.length; i++) {
			this.setTitle(  "Tag Manager" + ((numSorted%100>50)?" SORTING!!!":" Sorting...") );
			listModel.addElement(labels[i]);
			numSorted++;
		}
		//		list.setSize(dim);
		for (int i=0; i<fullLabels.length; i++) {
			fullListModel.addElement(fullLabels[i]);
		}
		this.setTitle(  "Tag Manager" );

		if (record()) Recorder.record("roiManager", "Sort");
		busy = false;

	}

	void specify() {
		try {IJ.run("Specify...");}
		catch (Exception e) {return;}
		runCommand("add");
	}

	void removeSliceInfo() {
		int[] indexes = getSelectedIndexes();
		if (indexes.length==0)
			indexes = getAllShownIndexes();
		for (int i=0; i<indexes.length; i++) {
			int index = indexes[i];
			String name = (String) listModel.getElementAt(index);
			int n = getSliceNumber(name);
			if (n==-1) continue;
			String name2 = name.substring(5, name.length());
			name2 = getUniqueName(name2);
			Roi roi = (Roi)rois.get(name);
			rois.remove(name);
			roi.setName(name2);
			roi.setPosition(0,0,0);
			rois.put(name2, roi);
			listModel.setElementAt(name2, index);
			fullListModel.setElementAt(name2, index);
		}
	}

	void help() {
		String macro = "run('URL...', 'url="+IJ.URL+"/docs/menus/analyze.html#manager');";
		new MacroRunner(macro);
	}

	void options() {
		Color c = ImageCanvas.getShowAllColor();
		GenericDialog gd = new GenericDialog("Options");
//		gd.addPanel(makeButtonPanel(gd), GridBagConstraints.CENTER, new Insets(5, 0, 0, 0));
		gd.addCheckbox("Associate \"Show All\" ROIs with slices", Prefs.showAllSliceOnly);
		gd.addCheckbox("Restore ROIs centered", restoreCentered);
		gd.addCheckbox("Use ROI names as labels", Prefs.useNamesAsLabels);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (c!=ImageCanvas.getShowAllColor())
				ImageCanvas.setShowAllColor(c);
			return;
		}
		Prefs.showAllSliceOnly = gd.getNextBoolean();
		restoreCentered = gd.getNextBoolean();
		Prefs.useNamesAsLabels = gd.getNextBoolean();
		ImagePlus imp = this.imp;
		if (imp!=null) imp.draw();
		if (record()) {
			Recorder.record("roiManager", "Associate", Prefs.showAllSliceOnly?"true":"false");
			Recorder.record("roiManager", "Centered", restoreCentered?"true":"false");
			Recorder.record("roiManager", "UseNames", Prefs.useNamesAsLabels?"true":"false");
		}
	}

	Panel makeButtonPanel(GenericDialog gd) {
		Panel panel = new Panel();
		//buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		colorButton = new Button("\"Show All\" Color...");
		colorButton.addActionListener(this);
		panel.add(colorButton);
		return panel;
	}

	void setShowAllColor() {
		ColorChooser cc = new ColorChooser("\"Show All\" Color", ImageCanvas.getShowAllColor(),  false);
		ImageCanvas.setShowAllColor(cc.getColor());
	}

	void split() {
		ImagePlus imp = this.imp;
		if (imp==null) return;
		Roi roi = imp.getRoi();
		if (roi==null || roi.getType()!=Roi.COMPOSITE) {
			error("Image with composite selection required");
			return;
		}
		boolean record = Recorder.record;
		Recorder.record = false;
		Roi[] rois = ((ShapeRoi)roi).getRois();
		for (int i=0; i<rois.length; i++) {
			imp.setRoi(rois[i]);
			addRoi(false);
		}
		Recorder.record = record;
		if (record()) Recorder.record("roiManager", "Split");
	}

	public void showAll(int mode) {
		ImagePlus imp = this.imp;
		if (imp==null){
			error("Linked image is not open."); 
			return;
		}
		ImageCanvas ic = imp.getCanvas();
		if (ic==null) return;
		showAll = mode==SHOW_ALL;
		boolean showOwn = mode==SHOW_OWN;
		//		if (showAll) {
		//			list.removeAll();
		//			for (String item:fullList.getItems() ) {
		//				list.add(item);
		//			}
		//		}
		if (mode==LABELS) {
			showAll = true;
			if (record())
				Recorder.record("roiManager", "Show All with labels");
		} else if (mode==NO_LABELS) {
			showAll = true;
			if (record())
				Recorder.record("roiManager", "Show All without labels");
		}
		//		if (showOwn) {
		//			showAll = showAllCheckbox.getState();
		//			List tempList = list;
		//			tempList.removeAll();
		//			for (String item:list.getItems() ) {
		//				tempList.add(item);
		//			}
		//			String[] listStrings = tempList.getItems();
		//			for (int i=0; i<listStrings.length; i++) {
		//				Roi roi = (Roi)rois.get(tempList.getItem(i));
		//				if (roi.getMotherImp() == imp) {
		//					tempList.deselect(i);				
		//				} else {
		//					tempList.select(i);
		//				}
		//			}
		//			String[] killList = tempList.getSelectedItems();
		//			int killCount = killList.length;
		//			while (killCount >0) {
		//				tempList.remove(killList[killCount-1]);
		//				killCount--;
		//			}
		//			list = tempList;
		//		}

		if (showAll) imp.deleteRoi();
		ic.setShowAllROIs(showAll);
		ic.setShowOwnROIs(showOwn);
		if (record())
			Recorder.record("roiManager", showAll?"Show All":"Show None");
		imp.draw();
	}

	void updateShowAll() {
		ImagePlus imp = this.imp;
		if (imp==null) return;
		ImageCanvas ic = imp.getCanvas();
		if (ic!=null && ic.getShowAllROIs())
			imp.draw();
	}

	int[] getAllShownIndexes() {
		int count = listModel.getSize();
		int[] indexes = new int[count];
		for (int i=0; i<count; i++)
			indexes[i] = i;
		return indexes;
	}

	int[] getFullListIndexes() {
		int count = fullListModel.getSize();
		int[] indexes = new int[count];
		for (int i=0; i<count; i++)
			indexes[i] = i;
		return indexes;
	}


	ImagePlus getImage() {
		ImagePlus imp = this.imp;
		if (imp==null) {
			error("This Manager's image is not open.");
			return null;
		} else
			return imp;
	}

	boolean error(String msg) {
		new MessageDialog(this, "Tag Manager", msg);
		Macro.abort();
		return false;
	}

	public void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		if (e.getID()==WindowEvent.WINDOW_CLOSING) {
			instance = null;	
		}
		if (!IJ.isMacro())
			ignoreInterrupts = false;
	}

	/** Returns a reference to the Tag Manager
		or null if it is not open. */
	public static RoiManager getInstance(ImagePlus queryImp) {
		if (queryImp != null ){
			for (int i=0;i<WindowManager.getNonImageWindows().length;i++){
				Frame frame = WindowManager.getNonImageWindows()[i];
				//				IJ.log(frame.toString()+" \n"+queryImp.toString()+" \n"
				//						+ ((frame instanceof RoiManager)?((RoiManager) frame).getImagePlus().toString():"None are rm\n")
				//						+" "+/*((((RoiManager)frame).getImagePlus() == queryImp)?"YES!":"nope"+*/"\n");
				if ((frame instanceof RoiManager) && ((RoiManager)frame).getImagePlus() == queryImp) {
					//					IJ.log("YES");
					return (RoiManager)frame;
				} else {
					//					IJ.log("NO");
					return null;
				}
			}
			return null;
		}
		else return null;
	}

	/** Returns a reference to the Tag Manager window or to the
		macro batch mode RoiManager, or null if neither exists. */
	public static RoiManager getInstance2() {
		RoiManager rm = getInstance(WindowManager.getCurrentImage());
		if (rm==null && IJ.isMacro())
			rm = Interpreter.getBatchModeRoiManager();
		return rm;
	}



	/**	Returns the ROI Hashtable.
		@see getCount
		@see getRoisAsArray
	 */
	public Hashtable<String, Roi> getROIs() {
		return rois;
	}

	/** Returns the selection list.
		@see getCount
		@see getRoisAsArray
	 */
	public JList getList() {
		return list;
	}

	/** Returns the ROI count. */
	public int getCount() {
		return listModel.getSize();
	}

	/** Returns the shown (searched) set ROIs as an array. */
	public Roi[] getShownRoisAsArray() {
		int n = listModel.getSize();
		Roi[] array = new Roi[n];
		for (int i=0; i<n; i++) {
			String label = (String) listModel.getElementAt(i);
			array[i] = (Roi)rois.get(label);
		}
		return array;
	}

	/** Returns the shown (searched) set of ROIs for the specifice slice/frame as an array. */
	private Roi[] getDisplayedRoisAsArray(int z, int t) {
		Roi[] roiSetIn = this.getShownRoisAsArray();
		if (roiSetIn == null)
			return null;
		ArrayList<Roi> matchedRois = new ArrayList<Roi>();
		for (int i=0; i < roiSetIn.length; i++) {
			//			IJ.log(( roiSetIn[i].getZPosition() +" "+ z  +" "+ roiSetIn[i].getTPosition() +" "+ t+"\n"));
			if ( roiSetIn[i].getZPosition() > z- zSustain && roiSetIn[i].getZPosition() < z + zSustain && roiSetIn[i].getTPosition() > t - tSustain &&
					roiSetIn[i].getTPosition() < t + tSustain ) {
				matchedRois.add(roiSetIn[i]);
				//				IJ.showMessage("");

			}
		}
		//		IJ.log(""+matchedRoiIndexes.size());

		Roi[] displayedRois = new Roi[matchedRois.size()];
		for (int i=0; i < displayedRois.length; i++)
		{
			displayedRois[i] = matchedRois.get(i);
		}		
		return displayedRois;

	}



	/** Returns the full set of ROIs as an array. */
	public Roi[] getFullRoisAsArray() {
		int n = fullListModel.getSize();
		Roi[] array = new Roi[n];
		for (int i=0; i<n; i++) {
			String label = (String) fullListModel.getElementAt(i);
			if (rois != null)
				array[i] = (Roi)rois.get(label);
		}
		return array;
	}

	/** Returns the selected ROIs as an array. */
	public Roi[] getSelectedRoisAsArray() {
		int[] indexes = getSelectedIndexes();
		int n = indexes.length;
		Roi[] array = new Roi[n];
		for (int i=0; i<n; i++) {
			String label = (String) listModel.getElementAt(indexes[i]);
			array[i] = (Roi)rois.get(label);
		}
		return array;
	}

	/** Returns the full set of original ROIs, cloned as an independent array. */
	public Roi[] getOriginalRoisAsClonedArray() {

		Roi[] clonedArray;
		if (originalsCloned) {
			clonedArray = new Roi[originalRois.length];
			for (int i = 0; i < originalRois.length; i++) {
				clonedArray[i] = (Roi) originalRois[i].clone();
			}
			return(clonedArray);
		} else
			return null;
	}

	public Roi[] getOriginalRoisAsArray() {
		if (originalsCloned) {
			return originalRois;
		} else
			return null;
	}


	/** Returns the name of the ROI with the specified index,
		or null if the index is out of range. */
	public String getName(int index) {
		if (index>=0 && index<listModel.getSize())
			return  (String) listModel.getElementAt(index);
		else
			return null;
	}

	/** Returns the name of the ROI with the specified index.
		Can be called from a macro using
		<pre>call("ij.plugin.frame.RoiManager.getName", index)</pre>
		Returns "null" if the Tag Manager is not open or index is
		out of range.
	 */
	public static String getName(String index, String impIDstring) {
		int i = (int)Tools.parseDouble(index, -1);
		int impID = (int)Tools.parseDouble(impIDstring, -1);
		RoiManager instance = WindowManager.getImage(impID).getRoiManager();
		if (instance!=null && i>=0 && i<instance.listModel.getSize())
			return  (String) instance.listModel.getElementAt(i);
		else
			return "null";
	}

	/** Executes the Tag Manager "Add", "Add & Draw", "Update", "Delete", "Measure", "Draw",
		"Show All", Show None", "Fill", "Deselect", "Select All", "Combine", "AND", "XOR", "Split",
		"Sort" or "Multi Measure" command.  Returns false if <code>cmd</code>
		is not one of these strings. */
	public boolean runCommand(String cmd) {
		cmd = cmd.toLowerCase();
		macro = true;
		boolean ok = true;
		if (cmd.equals("add")) {
			boolean shift = IJ.shiftKeyDown();
			boolean alt = IJ.altKeyDown();
			if (Interpreter.isBatchMode()) {
				shift = false;
				alt = false;
			}
			ImagePlus imp = this.imp;
			Roi roi = imp!=null?imp.getRoi():null;
			if (roi!=null) roi.setPosition(imp, 0);
			add(shift, alt, false);
		} else if (cmd.equals("add & draw"))
			addAndDraw(false);
		else if (cmd.equals("update"))
			update(false);
		else if (cmd.equals("update2"))
			update(false);
		else if (cmd.equals("delete"))
			delete(false);
		else if (cmd.equals("measure"))
			measure(COMMAND);
		else if (cmd.equals("draw"))
			drawOrFill(DRAW);
		else if (cmd.equals("fill"))
			drawOrFill(FILL);
		else if (cmd.equals("label"))
			drawOrFill(LABEL);
		else if (cmd.equals("and"))
			and();
		else if (cmd.equals("or") || cmd.equals("combine"))
			combine();
		else if (cmd.equals("xor"))
			xor();
		else if (cmd.equals("split"))
			split();
		else if (cmd.equals("sort"))
			sort();
		else if (cmd.equals("multi measure"))
			multiMeasure();
		else if (cmd.equals("multi plot"))
			multiPlot();
		else if (cmd.equals("show all")) {
			if (WindowManager.getCurrentImage()!=null) {
				showAll(SHOW_ALL);
				showAllCheckbox.setState(true);
			}
		} else if (cmd.equals("show none")) {
			if (WindowManager.getCurrentImage()!=null) {
				showAll(SHOW_NONE);
				showAllCheckbox.setState(false);
			}
		} else if (cmd.equals("show all with labels")) {
			labelsCheckbox.setState(true);
			showAll(LABELS);
			if (Interpreter.isBatchMode()) IJ.wait(250);
		} else if (cmd.equals("show all without labels")) {
			labelsCheckbox.setState(false);
			showAll(NO_LABELS);
			if (Interpreter.isBatchMode()) IJ.wait(250);
		} else if (cmd.equals("deselect")||cmd.indexOf("all")!=-1) {
			if (IJ.isMacOSX()) ignoreInterrupts = true;
			select(-1);
			IJ.wait(50);
		} else if (cmd.equals("reset")) {
			if (IJ.isMacOSX() && IJ.isMacro())
				ignoreInterrupts = true;
			listModel.removeAllElements();
			fullListModel.removeAllElements();
			rois.clear();
			updateShowAll();
		} else if (cmd.equals("debug")) {
			//IJ.log("Debug: "+debugCount);
			//for (int i=0; i<debugCount; i++)
			//	IJ.log(debug[i]);
		} else if (cmd.equals("enable interrupts")) {
			ignoreInterrupts = false;
		} else
			ok = false;
		macro = false;
		return ok;
	}

	/** Executes the Tag Manager "Open", "Save" or "Rename" command. Returns false if 
	<code>cmd</code> is not "Open", "Save" or "Rename", or if an error occurs. */
	public boolean runCommand(String cmd, String name) {
		cmd = cmd.toLowerCase();
		macro = true;
		if (cmd.equals("open")) {
			open(name);
			macro = false;
			return true;
		} else if (cmd.equals("save")) {
			if (!name.endsWith(".zip") && !name.equals(""))
				return error("Name must end with '.zip'");
			if (listModel.getSize()==0)
				return error("The selection list is empty.");
			int[] indexes = getAllShownIndexes();
			boolean ok = false;
			if (name.equals(""))
				ok = saveMultiple(indexes, null);
			else
				ok = saveMultiple(indexes, name);
			macro = false;
			return ok;
		} else if (cmd.equals("rename")) {
			rename(name, null, true);
			macro = false;
			return true;
		} else if (cmd.equals("set color")) {
			Color color = Colors.decode(name, Color.cyan);
			setProperties(color, -1, null);
			macro = false;
			return true;
		} else if (cmd.equals("set fill color")) {
			Color fillColor = null;
			if (name.matches("#........")) {
				fillColor = Colors.decode(name, Color.cyan);
			} else {
				fillColor = JColorChooser.showDialog(this.getFocusOwner(), "Pick a color for "+ this.getSelectedRoisAsArray()[0].getName()+"...", Colors.decode("#"+name, Color.cyan));
				if (name.length() == 8) {
					String alphaCorrFillColorString =  Colors.colorToHexString(fillColor).replaceAll("#", "#"+name.substring(0, 2));
					fillColor = Colors.decode(alphaCorrFillColorString, fillColor);
				}
			}
			setProperties(null, -1, fillColor);
			macro = false;
			return true;
		} else if (cmd.equals("set line width")) {
			int lineWidth = (int)Tools.parseDouble(name, 0);
			if (lineWidth>=0)
				setProperties(null, lineWidth, null);
			macro = false;
			return true;
		} else if (cmd.equals("associate")) {
			Prefs.showAllSliceOnly = name.equals("true")?true:false;
			macro = false;
			return true;
		} else if (cmd.equals("centered")) {
			restoreCentered = name.equals("true")?true:false;
			macro = false;
			return true;
		} else if (cmd.equals("usenames")) {
			Prefs.useNamesAsLabels = name.equals("true")?true:false;
			macro = false;
			if (labelsCheckbox.getState()) {
				ImagePlus imp = this.imp;
				if (imp!=null) imp.draw();
			}
			return true;
		}
		return false;
	}

	/** Adds the current selection to the Tag Manager, using the
		specified color (a 6 digit hex string) and line width. */
	public boolean runCommand(String cmd, String hexColor, double lineWidth) {
		ImagePlus imp = this.imp;
		Roi roi = imp!=null?imp.getRoi():null;
		if (roi!=null) roi.setPosition(imp, 0);
		if (hexColor==null && lineWidth==1.0 && (IJ.altKeyDown()&&!Interpreter.isBatchMode()))
			addRoi(true);
		else {
			Color color = hexColor!=null?Colors.decode(hexColor, Color.cyan):null;
			addRoi(null, false, color, (int)Math.round(lineWidth));
		}
		return true;	
	}

	/** Assigns the ROI at the specified index to the current image. */
	public void select(int index) {
		select(null, index);
	}

	/** Assigns the ROI at the specified index to 'imp'. */
	public void select(ImagePlus imp, int index) {
		if (IJ.shiftKeyDown()) {
			select(index, true, false);
		}
		selectedIndexes = null;
		int n = listModel.getSize();
		int[] selecteds = list.getSelectedIndices();
		if (index<0) {
			list.clearSelection();
			if (record()) Recorder.record("roiManager", "Deselect");
			return;
		}
		if (index>=n) return;			
		if (IJ.shiftKeyDown()) {
			list.addSelectionInterval(index, index);
		} else {
			list.clearSelection();
			list.setSelectedIndex(index);
		}
		list.ensureIndexIsVisible(index);

		if (imp==null) imp=getImage();
		if(list.getSelectedIndices().length <=1) {

			restore(imp, index, true);
		}

	}

	public void select(int index, boolean shiftKeyDown, boolean altKeyDown) {
		if (!(shiftKeyDown||altKeyDown))
			select(index);
		ImagePlus imp = this.imp;
		if (imp==null) return;
		Roi previousRoi = imp.getRoi();
		if (previousRoi==null){
			IJ.setKeyUp(IJ.ALL_KEYS);
			select(imp, index); 	
			return;
		}
		Roi.previousRoi = (Roi)previousRoi.clone();
		String label = (String) listModel.getElementAt(index);
		list.setSelectedIndices(getSelectedIndexes());
		Roi roi = (Roi)rois.get(label);
		if (roi!=null) {
			roi.setImage(imp);
			roi.update(shiftKeyDown, altKeyDown);
		}
	}

	public void setEditMode(ImagePlus imp, boolean editMode) {
		ImageCanvas ic = imp.getCanvas();
		boolean showAll = false;
		if (ic!=null) {
			showAll = ic.getShowAllROIs() | editMode;
			ic.setShowAllROIs(showAll);
			imp.draw();
		}
		showAllCheckbox.setState(showAll);
		labelsCheckbox.setState(editMode);
	}


	/** Overrides PlugInFrame.close(). */
	public void close() {
		super.setVisible(false);
		//    	super.close();
		//    	instance = null;
		Prefs.saveLocation(LOC_KEY, getLocation());
	}

	/** Moves all the ROIs to the specified image's overlay. */
	public void moveRoisToOverlay(ImagePlus imp) {
		Roi[] rois = getShownRoisAsArray();
		int n = rois.length;
		Overlay overlay = new Overlay();
		ImageCanvas ic = imp.getCanvas();
		Color color = ic!=null?ic.getShowAllColor():null;
		for (int i=0; i<n; i++) {
			Roi roi = (Roi)rois[i].clone();
			if (!Prefs.showAllSliceOnly)
				roi.setPosition(imp, 0);
			if (color!=null && roi.getStrokeColor()==null)
				roi.setStrokeColor(color);
			if (roi.getStrokeWidth()==1)
				roi.setStrokeWidth(0);
			overlay.add(roi);
		}
		if (labelsCheckbox.getState()) {
			overlay.drawLabels(true);
			overlay.drawBackgrounds(true);
			overlay.setLabelColor(Color.white);
		}
		imp.setOverlay(overlay);
	}

	/** Overrides PlugInFrame.dispose(). */
	public void dispose() {
		synchronized(this) {
			done = true;
			notifyAll();
		}
		if (rois != null) {
			rois.clear();
			rois=null;
		}
		if (originalRois != null) {
			originalRois = null;
		}
		list = null;
		listModel = null;
		fullList = null;
		fullListModel = null;

		if (this.colorLegend != null){
			colorLegend.dispose();
			colorLegend.setRoiManager(null);
			WindowManager.removeWindow(colorLegend);
		}
		if (list != null)
			list.removeKeyListener(IJ.getInstance());
		this.removeKeyListener(IJ.getInstance());
		//    	thread.interrupt();
		//    	thread = null;
		//		this.imp.getWindow().removeWindowListener(this);
		this.imp = null;
		WindowManager.removeWindow(this);
		super.dispose();
	}

	public void mousePressed (MouseEvent e) {
		int x=e.getX(), y=e.getY();
		if (e.isPopupTrigger() || e.isMetaDown())
			pm.show(e.getComponent(),x,y);
	}

	public void mouseWheelMoved(MouseWheelEvent event) {
		synchronized(this) {
			int index = list.getSelectedIndex();
			int rot = event.getWheelRotation();
			if (rot<-1) rot = -1;
			if (rot>1) rot = 1;
			index += rot;
			if (index<0) index = 0;
			if (index>=listModel.getSize()) index = listModel.getSize();
			//IJ.log(index+"  "+rot);
			//			select(index);
			if (IJ.isWindows())
				list.requestFocusInWindow();
		}
	}

	/** Temporarily selects multiple ROIs, where 'indexes' is an array of integers, 
		each greater than or equal to 0 and less than the value returned by getCount().
		The selected ROIs are not highlighted in the Tag Manager list and are no 
		longer selected after the next Tag Manager command is executed.
	 */
	public void setSelectedIndexes(int[] indexes) {
		int count = getCount();
		if (count==0) return;
		for (int i=0; i<indexes.length; i++) {
			if (indexes[i]<0) indexes[i]=0;
			if (indexes[i]>=count) indexes[i]=count-1;
		}
		selectedIndexes = indexes;
	}

	private int[] getSelectedIndexes() {
		if (selectedIndexes!=null) {
			int[] indexes = selectedIndexes;
			selectedIndexes = null;
			return indexes;
		} else
			return list.getSelectedIndices();
	}

	private boolean record() {
		return Recorder.record && !IJ.isMacro();
	}

	public void mouseReleased (MouseEvent e) {}
	public void mouseClicked (MouseEvent e) {}
	public void mouseEntered (MouseEvent e) {
//		IJ.runMacro("print(\"\\\\Clear\")");
//		IJ.runMacro("print(\"\\\\Update:Tag Manager:\\\nLeft-Clicking a list item highlights the Tag tag in the movie window.\\\nButtons and other widgets modify the content of the list \\\nand the display of tags in the movie window.\\\n \")");

	}
	public void mouseExited (MouseEvent e) {}

	public void keyPressed(KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

	public void keyReleased(KeyEvent arg0) {
		// TODO Auto-generated method stub

	}
	public void keyTyped(KeyEvent e) {

	}

	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == zSustainSpinner){
			zSustain = Integer.parseInt(zSustainSpinner.getValue().toString() );
		}
		if (e.getSource() == tSustainSpinner){
			tSustain = Integer.parseInt(tSustainSpinner.getValue().toString() );
		}
		showAll(SHOW_ALL);
	}

	public int getZSustain() {
		return zSustain;
	}

	public void setZSustain(int sustain) {
		zSustainSpinner.setValue(sustain);
		zSustain = sustain;
		showAll(SHOW_ALL);
	}

	public int getTSustain() {
		return tSustain;
	}

	public void  setTSustain(int sustain) {
		tSustainSpinner.setValue(sustain);
		tSustain = sustain;
		showAll(SHOW_ALL);

	}

	public void setShowAllCheckbox(boolean canvasShowAllState) {
		showAllCheckbox.setState(canvasShowAllState);
	}

	public Roi[] getSliceSpecificRoiArray(int z, int t, boolean getSpanners) {
		Roi[] roiSetIn = this.getFullRoisAsArray();
		if (roiSetIn == null)
			return null;
		ArrayList<Roi> matchedRois = new ArrayList<Roi>();
		for (int i=0; i < roiSetIn.length; i++) {
			//			IJ.log(( roiSetIn[i].getZPosition() +" "+ z  +" "+ roiSetIn[i].getTPosition() +" "+ t+"\n"));
			if (roiSetIn[i].getTPosition() == t || (roiSetIn[i].getTPosition() == 0 && getSpanners)) {
				if ( roiSetIn[i].getZPosition() == z || (roiSetIn[i].getZPosition() == 0 && getSpanners)/*  && roiSetIn[i].getTPosition() > t - tSustain &&
					roiSetIn[i].getTPosition() < t + tSustain */) {
					matchedRois.add(roiSetIn[i]);
					//				IJ.showMessage("");

				}
			}
		}
		//		IJ.log(""+matchedRoiIndexes.size());

		Roi[] sliceSpecificFullRois = new Roi[matchedRois.size()];
		for (int i=0; i < sliceSpecificFullRois.length; i++)
		{
			sliceSpecificFullRois[i] = matchedRois.get(i);
		}		
		return sliceSpecificFullRois;

	}

	public int[] getSliceSpecificIndexes(int z, int t, boolean getSpanners) {
		Roi[] roiSetIn = this.getFullRoisAsArray();
		if (roiSetIn == null)
			return null;
		ArrayList<Integer> matchedIndexes = new ArrayList<Integer>();
		for (int i=0; i < roiSetIn.length; i++) {
			//			IJ.log(( roiSetIn[i].getZPosition() +" "+ z  +" "+ roiSetIn[i].getTPosition() +" "+ t+"\n"));
			if (roiSetIn[i].getTPosition() == t || (roiSetIn[i].getTPosition() == 0 && getSpanners)) {
				if ( roiSetIn[i].getZPosition() == z || (roiSetIn[i].getZPosition() == 0 && getSpanners)/*  && roiSetIn[i].getTPosition() > t - tSustain &&
					roiSetIn[i].getTPosition() < t + tSustain */) {
				matchedIndexes.add(i);
				//				IJ.showMessage("");
				}
			}
		}
		//		IJ.log(""+matchedRoiIndexes.size());

		int[] sliceSpecificFullIndexes = new int[matchedIndexes.size()];
		for (int i=0; i < sliceSpecificFullIndexes.length; i++)
		{
			sliceSpecificFullIndexes[i] = matchedIndexes.get(i);
		}		
		return sliceSpecificFullIndexes;

	}
	public Roi getSliceSpecificRoi(ImagePlus impIn, int z, int t) {
		//		this.imp = imp;
		Roi combinedROI = null;
		this.setSelectedIndexes(this.getAllShownIndexes());
		Roi[] roiSubSet = this.getSelectedRoisAsArray();
		ArrayList<Integer> matchedRoiIndexes = new ArrayList<Integer>();

		// These next two parameters are essential to tuning the size and scaling over time of the cell-specific R0Is that can be used to isolate cells and lineages. This is the best overall fit I could find for pie-I:: HIS-58 V.
		// I will probably want to make some sliders or spinners for these in some previewable dialog.		
		//		double widthDenom = 4;
		//		double timeBalancer = 2;

		double widthDenom = 4.5;
		double timeBalancer = 2;
		ImagePlus guideImp = imp;
		if (imp.getMotherImp() != null) 
			guideImp = imp.getMotherImp();
		int frames = guideImp.getNFrames();
		BigDecimal framesBD = new BigDecimal("" + (frames+timeBalancer));
		BigDecimal widthDenomBD = new BigDecimal("" + widthDenom);
		BigDecimal tBD = new BigDecimal("" + (imp.getMotherFrame()>0?imp.getMotherFrame():0 + imp.getFrame()+timeBalancer));	
		BigDecimal impHeightBD = new BigDecimal(""+guideImp.getHeight());		
		BigDecimal cellDiameterBD = impHeightBD.divide(widthDenomBD, MathContext.DECIMAL32).multiply(takeRoot(3, (framesBD.subtract(tBD).add(new BigDecimal("1"))).divide(tBD, MathContext.DECIMAL32), new BigDecimal(".001")), MathContext.DECIMAL32) ;


		for (int i=0; i < roiSubSet.length; i++) {
			if (Math.abs(roiSubSet[i].getZPosition()-z)*imp.getCalibration().pixelDepth < cellDiameterBD.intValue()/2  &&
					roiSubSet[i].getTPosition() > t - tSustain &&
					roiSubSet[i].getTPosition() < t + tSustain ) {

				//				Subtractive solution to shrinking cell sizes...
				//				double inPlaneDiameter = Math.sqrt( Math.pow(((imp.getWidth()/widthDenom)-(imp.getWidth()/widthDenom)*t*timeFactor/imp.getNFrames())/2,2) - Math.pow((roiSubSet[i].getZPosition()-z)*imp.getCalibration().pixelDepth*2,2) );

				//				Volume fraction solution to shrinking cell sizes...
				double inPlaneDiameter = 2 * Math.sqrt( Math.pow(cellDiameterBD.intValue()/2,2) - Math.pow((roiSubSet[i].getZPosition()-z)*imp.getCalibration().pixelDepth,2) );
				//				IJ.log(""+cellDiameterBD.intValue() +" "+ inPlaneDiameter  );
				imp.setRoi(new OvalRoi(roiSubSet[i].getBounds().getCenterX() - inPlaneDiameter/2,
						roiSubSet[i].getBounds().getCenterY() - inPlaneDiameter/2, 
						inPlaneDiameter, 
						inPlaneDiameter) );
				addRoi(imp.getRoi());
				matchedRoiIndexes.add(this.getCount()-1);
			}
		}

		//Select image corners.
		imp.setRoi(new Rectangle(0,0,1,1));				
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount()-1);

		imp.setRoi(new Rectangle(0,imp.getHeight()-1,1,1));				
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount()-1);

		imp.setRoi(new Rectangle(imp.getWidth()-1,0,1,1));				
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount()-1);

		imp.setRoi(new Rectangle(imp.getWidth()-1,imp.getHeight()-1,1,1));				
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount()-1);

		int[] sliceSpecificIndexes = new int[matchedRoiIndexes.size()];
		for (int i=0; i < sliceSpecificIndexes.length; i++)
		{
			sliceSpecificIndexes[i] = matchedRoiIndexes.get(i).intValue();
		}

		setSelectedIndexes(sliceSpecificIndexes);

		if (this.runCommand("combine"))
			combinedROI = imp.getRoi();

		setSelectedIndexes(sliceSpecificIndexes);

		runCommand("delete");
		imp.killRoi();

		return combinedROI;
	}

	public void setImagePlus(ImagePlus imp) {
		this.imp = imp;
	}

	public ImagePlus getImagePlus() {
		return imp;
	}

	@Override
	public void setTitle(String title) {
		this.title = title;		
		super.setTitle(title);
	}



	public static BigDecimal takeRoot(int root, BigDecimal n, BigDecimal
			maxError) {
		int MAXITER = 5000;

		// Specify a math context with 40 digits of precision.
		MathContext mc = new MathContext(40);

		// Specify the starting value in the search for the cube root.
		BigDecimal x;
		x=new BigDecimal("1",mc);

		BigDecimal prevX = null;

		BigDecimal rootBD = new BigDecimal(root,mc);
		// Search for the cube root via the Newton-Raphson loop. Output
		//		each successive iteration's value.
		for(int i=0; i < MAXITER; ++i) {
			x = x.subtract(x.pow(root,mc)
					.subtract(n,mc)
					.divide(rootBD.multiply(x.pow(root-1,mc),mc),mc),mc);
			if(prevX!=null && prevX.subtract(x).abs().compareTo(maxError) <
					0)
				break;
			prevX = x;
		}

		return x;
	}

	private void copyToOtherRMs() {
		Roi[] copiedRois = this.getSelectedRoisAsArray();
		if (copiedRois == null)
			return;
		int[] imageIDs = WindowManager.getIDList();
		for (int j=0; j<imageIDs.length; j++){
			RoiManager recipRM = WindowManager.getImage(imageIDs[j]).getRoiManager();
			if (recipRM == null)
				recipRM = new RoiManager(WindowManager.getImage(imageIDs[j]), true);
			if (recipRM!=this){
				for (int i=0; i<copiedRois.length;i++) {

					recipRM.addRoi((Roi) copiedRois[i].clone());
					recipRM.getShownRoisAsArray()[recipRM.getCount()-1].setPosition(copiedRois[i].getCPosition(), copiedRois[i].getZPosition(), copiedRois[i].getTPosition());
					String nameEndReader = copiedRois[i].getName();

					int c = copiedRois[i].getCPosition();
					int z = copiedRois[i].getZPosition();
					int t = copiedRois[i].getTPosition();
					if (nameEndReader.split("_").length == 4) {
						c = Integer.parseInt(nameEndReader.split("_")[1]);
						z = Integer.parseInt(nameEndReader.split("_")[2]);
						t = Integer.parseInt(nameEndReader.split("_")[3].split("[CZT-]")[0]);
					}
					while (nameEndReader.endsWith("C") || nameEndReader.endsWith("Z") || nameEndReader.endsWith("T") ) {
						if (nameEndReader.endsWith("C") ){
							c = 0;
							nameEndReader = nameEndReader.substring(0, nameEndReader.length()-1);
						}
						if (nameEndReader.endsWith("Z") ){
							z = 0;
							nameEndReader = nameEndReader.substring(0, nameEndReader.length()-1);
						}
						if (nameEndReader.endsWith("T") ){
							t = 0;
							nameEndReader = nameEndReader.substring(0, nameEndReader.length()-1);
						}
					}
					recipRM.getShownRoisAsArray()[recipRM.getCount()-1].setPosition(c, z, t);
					recipRM.getShownRoisAsArray()[recipRM.getCount()-1].setName(nameEndReader);
				}
				recipRM.showAll(RoiManager.SHOW_ALL);
			}
		}
	}

	public boolean isShowAll() {
		return showAll;
	}

	public void sketch3D(Object source) {
		IJ.log(""+imp.getCalibration().pixelDepth);
		int synapseScale = imp.getWidth()/25;
		int modelWidth = 80;
		if (getShownRoisAsArray().length < 1 || imp.getNDimensions() > 6) {
			busy=false;
			return;
		}
		if (Channels.getInstance()!=null) Channels.getInstance().dispose();
		boolean splitThem = shiftKeyDown;
		boolean eightBit = shiftKeyDown;
		boolean brainbow = controlKeyDown;
		boolean fatSynapses = altKeyDown;
		imp.getWindow().setSubTitleBkgdColor(Color.yellow);
		int[] selectedIndexes = this.getSelectedIndexes();
		Roi[] selectedRois = this.getSelectedRoisAsArray();
		int[] fullIndexes = this.getFullListIndexes();
		Roi[] fullRois = this.getFullRoisAsArray();
		int[] shownIndexes = this.getAllShownIndexes();
		Roi[] shownRois = this.getShownRoisAsArray();

		Hashtable<String,ArrayList<String>> shownRoisHash = new Hashtable<String,ArrayList<String>>();
		if (selectedIndexes.length <1) {
			selectedIndexes = this.getAllShownIndexes();
			selectedRois = this.getShownRoisAsArray();
		}

		if (brainbow) {
			if(source instanceof JButton && ((JButton) source).getParent().getParent().getParent() instanceof ImageWindow) 
				source = ((ImageWindow)((JButton) source).getParent().getParent().getParent()).getImagePlus();
			ColorLegend cl = getColorLegend(source);
			if (cl != null){
				if (cl.getRoiManager() != null && cl.getRoiManager() != this) {
					if (altKeyDown)
						cl.getRoiManager().altKeyDown = true;
					cl.getRoiManager().sketch3D(source);
					cl.getRoiManager().altKeyDown = false;
					if (altKeyDown) 
						cl = null;
					IJ.log("Passing to original Tag Manager...");
					return;
				} else if (altKeyDown) {
					if (this.getCount() <1) {
						IJ.log("Manager contains no Tags");
						return;
					}

					colorLegend.dispose();
					WindowManager.removeWindow(colorLegend);
					colorLegend = null;
					cellNames = null;
					fullCellNames = null;
					brainbowColors = null;
				} else {
					if (this.getCount() <1) {
						IJ.log("Manager contains no Tags");
						return;
					}

					ArrayList<String> selectedNamesStrings = new ArrayList<String>();
					IJ.log(""+selectedNamesStrings.size());
					for (int r=0; r<selectedRois.length; r++){
						if (selectedRois[r].getName().split("[\"|=]").length>1){
							String[] searchTextChunks = selectedRois[r].getName().split("[\"|=]")[1].split(" ");
							String searchText = "";
							for (String chunk:searchTextChunks)
								if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
									searchText = searchText + " " + chunk;
							String cellTagName = searchText.trim();
							selectedNamesStrings.add(cellTagName);
							//						IJ.log(selectedNamesStrings.get(selectedNamesStrings.size()-1));
						}
					}
					if (cellNames != null /*&& this != cl.getRoiManager()*/) {
						cellNames = new ArrayList<String>();
						for (Checkbox cb:colorLegend.getCheckbox()) {
							if (((!cb.getState() && colorLegend.getChoice().getSelectedItem().matches("Hide.*"))
									||(cb.getState() && !colorLegend.getChoice().getSelectedItem().matches("Hide.*")) 
									|| colorLegend.getChoice().getSelectedItem().matches("Display.*") )
									&& !cellNames.contains(cb.getName())) {
								cellNames.add(cb.getName());
								//								IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
							}				
						}
						if (cellNames.size()<1 && !colorLegend.getChoice().getSelectedItem().matches("Hide.*")) {
							for (Checkbox cb:colorLegend.getCheckbox()) {
								cellNames.add(cb.getName());
								//								IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
							}
						}
					}
					if (cellNames != null ) {
						String[] cellNamesArray = cellNames.toArray(new String[cellNames.size()]);
						for (int q=cellNamesArray.length-1; q>=0; q--){
//							IJ.log(""+cellNames.get(q) + " "+ q +"?");
							if (!selectedNamesStrings.contains(cellNames.get(q))) {
//								IJ.log(""+cellNames.get(q) + " "+ q +"XXX");
								cellNames.remove(q);
							} else {
//								IJ.log(""+cellNames.get(q) + " "+ q +"OK");
							}
						}
					}				
				}
			}
		}
		if (this.getCount() <1) {
			IJ.log("Manager contains no Tags");
			return;
		}

		ImageProcessor drawIP;
		if (eightBit) {
			drawIP = new ByteProcessor(imp.getWidth(), imp.getHeight());
		} else {
			drawIP = new ColorProcessor(imp.getWidth(), imp.getHeight());
		}
		if (fullCellNames == null) {
			fullCellNames = new ArrayList<String>();
			mowColors = new Hashtable<String,Color>();
			mowColors.put("", Color.white);
			for (int r=0; r<fullRois.length; r++){
				if (fullRois[r] != null && fullRois[r].getName().matches(".*[\"|=].*")) {
					String[] searchTextChunks = fullRois[r].getName().split("[\"|=]")[1].split(" ");
					String searchText = "";
					for (String chunk:searchTextChunks)
						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
							searchText = searchText + " " + chunk;
					if (searchText.trim().matches(".*ABar.*"))
						isEmbryonic = true;
					if ( !fullCellNames.contains(searchText.trim())) {
						fullCellNames.add(searchText.trim());				
						if (fullRois[r].isArea() && fullRois[r].getFillColor() != null)
							mowColors.put(fullCellNames.get(fullCellNames.size()-1), new Color(fullRois[r].getFillColor().getRGB()));
						else if (fullRois[r].isArea() && fullRois[r].getFillColor() == null)
							mowColors.put(fullCellNames.get(fullCellNames.size()-1), Color.gray);
						if (fullRois[r].isLine() && fullRois[r].getStrokeColor() != null)
							mowColors.put(fullCellNames.get(fullCellNames.size()-1), new Color(fullRois[r].getStrokeColor().getRGB()));
					}
				}
			}
		}
		if (cellNames == null /*&& this == cl.getRoiManager()*/) {
			cellNames = new ArrayList<String>();
			for (int r=0; r<selectedRois.length; r++){
				if (selectedRois[r] != null && selectedRois[r].getName().matches(".*[\"|=].*")) {
					String[] searchTextChunks = selectedRois[r].getName().split("[\"|=]")[1].split(" ");
					String searchText = "";
					for (String chunk:searchTextChunks)
						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
							searchText = searchText + " " + chunk;
					if ( !cellNames.contains(searchText.trim())) {
						cellNames.add(searchText.trim());
//						IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
					}
				}
			}
			if (cellNames == null) {
				for (int r=0; r<fullCellNames.size(); r++){
					cellNames.add(""+fullCellNames.get(r));			
//					IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());

				}
			}
		}

		nameLists.add(cellNames);

		if (brainbowColors == null) {
			if (colorLegend ==null) {
				brainbowColors = new Hashtable<String,Color>();
				brainbowColors.put(fullCellNames.get(0).trim().toLowerCase(), Color.white);
				String[] hexChoices = { "3","4","5","6", "7", "8","9","a",
						"b","c", "d","e", "f" };
				for (int c3=0; c3<fullCellNames.size(); c3++) {
					String randomHexString = "";
					boolean unique = false;
					do {
						randomHexString = "#"
								+ hexChoices[(int) Math.round(Math.random()
										* (hexChoices.length - 1))]
												+ hexChoices[(int) Math.round(Math.random()
														* (hexChoices.length - 1))]
																+ hexChoices[(int) Math.round(Math.random()
																		* (hexChoices.length - 1))]
																				+ hexChoices[(int) Math.round(Math.random()
																						* (hexChoices.length - 1))]
																								+ hexChoices[(int) Math.round(Math.random()
																										* (hexChoices.length - 1))]
																												+ hexChoices[(int) Math.round(Math.random()
																														* (hexChoices.length - 1))];

						for (int i = 0; i < brainbowColors.size(); i++) {
							if (brainbowColors.get(fullCellNames.get(i).toLowerCase()) == Colors.decode(randomHexString, Color.white)
									|| brainbowColors.get(fullCellNames.get(i).toLowerCase()).darker().darker() == Colors.decode(randomHexString, Color.white)) {
								unique = false;
							} else {
								unique = true;
							}
						}
					} while (!unique);
					brainbowColors.put(fullCellNames.get(c3).toLowerCase(), Colors.decode(randomHexString, Color.white));
				}
			}else{
				brainbowColors = new Hashtable<String,Color>();
				Checkbox[] cbs = colorLegend.getCheckbox();
				for (int cb =0; cb < cbs.length; cb++) {
					brainbowColors.put(cbs[cb].getName().toLowerCase(), cbs[cb].getBackground());			
				}
			}
		}
		if (colorLegend == null) {
			colorLegend = new ColorLegend(this);
		}	
		imp.getWindow().addWindowFocusListener(colorLegend);
		imp.getWindow().addMouseListener(colorLegend);

		int outChannels = 1;
		if (splitThem)
			outChannels = cellNames.size();
		ImagePlus[] outImps = new ImagePlus[outChannels];

		if (isEmbryonic)  {
			for(int j=0;j<shownIndexes.length;j++){
				String[] cellNumbers = shownRois[j].getName().split("_");
				if (shownRoisHash.get(cellNumbers[cellNumbers.length-2]+"_"+cellNumbers[cellNumbers.length-1].replaceAll("[CZT]", "")) == null) 
					shownRoisHash.put(cellNumbers[cellNumbers.length-2]+"_"+cellNumbers[cellNumbers.length-1].replaceAll("[CZT]", ""), new ArrayList<String>());
				if (!shownRoisHash.get(cellNumbers[cellNumbers.length-2]+"_"+cellNumbers[cellNumbers.length-1].replaceAll("[CZT]", "")).contains(shownRois[j].getName())) { 
					shownRoisHash.get(cellNumbers[cellNumbers.length-2]+"_"+cellNumbers[cellNumbers.length-1].replaceAll("[CZT]", "")).add(shownRois[j].getName());
//					IJ.log(cellNumbers[cellNumbers.length-2]+"_"+cellNumbers[cellNumbers.length-1].replaceAll("[CZT]", "") +" "+j+" "+shownRois[j].getName());
				}

			}

			int nChannels = imp.getNChannels();
			int nSlices = imp.getNSlices();
			int nFrames = imp.getNFrames();
			GenericDialog gd = new GenericDialog("Sketch3D hyperstack");
			gd.setInsets(12, 20, 8);
			gd.addCheckbox("Sketch3D hyperstack", false);
			gd.addCheckbox("Save full-size sketches?", false);
			int nRangeFields = 0;
			if (nFrames>1) {
				gd.setInsets(2, 30, 3);
				gd.addStringField("Frames (t, "+1+"-"+imp.getNFrames()+"):", ""+ imp.getFrame() +"-"+ imp.getFrame());
				nRangeFields++;
			}
			Vector v = gd.getStringFields();
			TextField[] rangeFields = new TextField[3];
			for (int i=0; i<nRangeFields; i++) {
				rangeFields[i] = (TextField)v.elementAt(i);
				rangeFields[i].addTextListener(this);
			}
			hyperstackCheckbox = (Checkbox)(gd.getCheckboxes().elementAt(0));
			boolean fullSketchToFile = false;
			gd.showDialog();

			int firstT = imp.getFrame();
			int lastT = imp.getFrame();
			if (!gd.wasCanceled()){
				if (gd.getNextBoolean()) {
					if (nFrames>1) {
						String[] range = Tools.split(gd.getNextString(), " -");
						double t1 = Tools.parseDouble(range[0]);
						double t2 = range.length==2?Tools.parseDouble(range[1]):Double.NaN;
						firstT= Double.isNaN(t1)?firstT:(int)t1;
						lastT = Double.isNaN(t2)?lastT:(int)t2;
						if (firstT>lastT) {firstT=lastT; lastT=firstT;}
						if (firstT<1) firstT = 1;
						if (lastT>nFrames) lastT = nFrames;
					} 
				}
				fullSketchToFile = gd.getNextBoolean();
			}

			double fillZfactor = imp.getCalibration().pixelDepth;
			int outNSlices = 0;
			for (int c2=0; c2<outChannels; c2++) {
				IJ.showStatus("Processing "+(c2+1)+"/"+outChannels+" channels...");
				ImageStack sketchStack = new ImageStack(modelWidth, imp.getHeight()/(imp.getWidth()/modelWidth));
				IJ.log(""+fillZfactor);
				IJ.log(""+(double)(sketchStack.getHeight())/(double)(imp.getHeight()));
				IJ.log(""+imp.getHeight());
				IJ.log(""+sketchStack.getHeight());
				Color frameColor = Color.WHITE;
				outNSlices = (int) (fillZfactor*imp.getNSlices());
				int miniStackSize = outNSlices*modelWidth/imp.getWidth();
				for (int t=firstT;t<=lastT;t++){
					ImageStack bigStack = new ImageStack(imp.getWidth(), imp.getHeight());
					for (int z=1; z<=outNSlices; z++) {
						if (eightBit) {
							drawIP = new ByteProcessor(imp.getWidth(), imp.getHeight());
						} else {
							drawIP = new ColorProcessor(imp.getWidth(), imp.getHeight());
						}
						drawIP.setColor(Color.BLACK);
						drawIP.fill();
						bigStack.addSlice(drawIP);
					}

					for (int z=1; z<=outNSlices; z++) {
						ArrayList<String> theseSlcSpecRoiNames = new ArrayList<String>();
						if (/*z%fillZfactor == 0 &&*/ this.getSliceSpecificRoiArray((int)(z/fillZfactor), t, false) != null) {
							for (Roi thisRoi:this.getSliceSpecificRoiArray((int)(z/fillZfactor), t, false))
								theseSlcSpecRoiNames.add(thisRoi.getName());
						}
//						IJ.log(""+z);
						for(int j=0;j<theseSlcSpecRoiNames.size();j++){
//							IJ.log(theseSlcSpecRoiNames.get(j));
							if (theseSlcSpecRoiNames.get(j) != null 
									&& ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim()))!=null
									/*&& z%fillZfactor == 0 */
									&& ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim())).getZPosition() == (int)(z/fillZfactor)
									&& ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim())).getTPosition() == t) {
//								IJ.log(theseSlcSpecRoiNames.get(j));
								String[] searchTextChunks = theseSlcSpecRoiNames.get(j).split("[\"|=]")[1].split(" ");
								String searchText = "";
								for (String chunk:searchTextChunks)
									if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
										searchText = searchText + " " + chunk;
								String cellTagName = searchText.trim();
//								IJ.log(cellTagName);
								if ((fatSynapses && theseSlcSpecRoiNames.get(j).startsWith("\"syn")) || isEmbryonic ){
									if (cellNames.contains(cellTagName) && brainbowColors.get(cellTagName.toLowerCase())!=null){
//										IJ.log("cell name matches");
										int maxRadius = synapseScale/2;
										for (int step= -maxRadius;step< maxRadius;step++) {
											if (z+step>0 && z+step<= outNSlices ) {
												drawIP = bigStack.getProcessor(z+step);
												drawIP.setColor(brainbow?new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB()):eightBit?Color.WHITE: mowColors.get(cellTagName));
												double radius = Math.pow( Math.pow((maxRadius),2) - Math.pow(step,2), 0.5 );
												Roi thisRoi = ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim()));
//												IJ.log(""+theseSlcSpecRoiNames.get(j).trim()+" "+radius);
												if (thisRoi != null)
													drawIP.fill(new OvalRoi((int)thisRoi.getBounds().getCenterX()-radius,
															(int)thisRoi.getBounds().getCenterY()-radius, 
															radius*2, 
															radius*2));

											}
										}
									}
								}
							}
						}

						for (int j=0;j<theseSlcSpecRoiNames.size();j++){
							if (theseSlcSpecRoiNames.get(j) != null) {					
								String[] searchTextChunks = theseSlcSpecRoiNames.get(j).split("[\"|=]")[1].split(" ");
								String searchText = "";
								for (String chunk:searchTextChunks)
									if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
										searchText = searchText + " " + chunk;
								String cellTagName = searchText.trim();
								if (!(fatSynapses && theseSlcSpecRoiNames.get(j).startsWith("\"syn")) && !isEmbryonic && brainbowColors.get(cellTagName)!= null){
									drawIP.setColor(brainbow?new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB()):eightBit?Color.WHITE: mowColors.get(cellTagName));

									if (cellNames.contains(cellTagName)) {
										Roi thisRoi = ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim()));
										if (thisRoi != null)
											drawIP.fill(thisRoi);
									}
								} else {
//									IJ.log(""+cellTagName + brainbowColors.get(cellTagName.toLowerCase()));

								}

							}
						}
					}
					for (int s=1;s<=bigStack.getSize();s=s+(imp.getWidth()/modelWidth)) {
						ImageProcessor bip = bigStack.getProcessor(s);
						bip.setInterpolationMethod(ImageProcessor.BICUBIC);
						sketchStack.addSlice(bip.resize(modelWidth, imp.getHeight()/(imp.getWidth()/modelWidth), false));
//						IJ.log("RESIZE "+sketchStack.getSize());
					}
					miniStackSize = sketchStack.getSize();
					ImagePlus bigImp = new ImagePlus("bigStack", bigStack);
					if (fullSketchToFile) {
						IJ.run(bigImp, "Save", "save="+IJ.getDirectory("home")+imp.getTitle()+"_fullSketch3D"+IJ.pad(t, 5)+".tif");
					}
					bigImp.close();
					bigImp.flush();

				}

				ImagePlus sketchImp = new ImagePlus("Sketch_"+(splitThem?(nameLists!=null?nameLists.get(nameLists.size()-1):cellNames).get(c2):"Composite"), sketchStack);

				outImps[c2]=sketchImp;
				outImps[c2].getCalibration().pixelDepth = 1;
				outImps[c2].setMotherImp(imp, 1);
				IJ.run(outImps[c2], "Stack to Hyperstack...", "order=xyczt(default) channels=1 slices="+(miniStackSize/(lastT-firstT+1))+" frames="+(lastT-firstT+1)+" display=Color");		
			}
			compImps.add(outImps[0]);
			if (splitThem) {
				compImps.set(compImps.size()-1,RGBStackMerge.mergeChannels(outImps, false));
			}
			if (compImps.get(compImps.size()-1)!=null) {
				compImps.get(compImps.size()-1).show();
				compImps.get(compImps.size()-1).getWindow().setBackground(this.getBackground());
				compImps.get(compImps.size()-1).setTitle("Sketch3D #"+compImps.size()+":"+imp.getCanvas().droppedGeneUrls.replace("\n", " "));
				compImps.get(compImps.size()-1).getWindow().addWindowListener(this);
				compImps.get(compImps.size()-1).getCanvas().addMouseMotionListener(colorLegend);
				compImps.get(compImps.size()-1).getCanvas().addMouseListener(colorLegend);
				compImps.get(compImps.size()-1).getWindow().addWindowFocusListener(colorLegend);
			}
			if (Channels.getInstance()!=null) ((Channels)Channels.getInstance()).close();
			IJ.run(compImps.get(compImps.size()-1), "3D Project...", "projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
			projYImps.add(WindowManager.getImage("Projections of Sketch3D"));
			if (splitThem)
				projYImps.get(projYImps.size()-1).setDimensions(projYImps.get(projYImps.size()-1).getNChannels(), projYImps.get(projYImps.size()-1).getNFrames(), projYImps.get(projYImps.size()-1).getNSlices());
			projYImps.get(projYImps.size()-1).setTitle("Sketch3D ProjDV #"+projYImps.size()+":"+imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projYImps.get(projYImps.size()-1).setMotherImp(imp, 1);
			projYImps.get(projYImps.size()-1).getWindow().setBackground(this.getBackground());
			projYImps.get(projYImps.size()-1).getWindow().addWindowListener(this);
			projYImps.get(projYImps.size()-1).getCanvas().addMouseMotionListener(colorLegend);
			projYImps.get(projYImps.size()-1).getCanvas().addMouseListener(colorLegend);
			projYImps.get(projYImps.size()-1).getWindow().addWindowFocusListener(colorLegend);

			if (Channels.getInstance()!=null) ((Channels)Channels.getInstance()).close();
			if (Channels.getInstance()!=null) ((Channels)Channels.getInstance()).close();
			IJ.run(compImps.get(compImps.size()-1), "3D Project...", "projection=[Nearest Point] axis=X-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");

			projZImps.add(WindowManager.getImage("Projections of Sketch3D"));
			projZImps.get(projZImps.size()-1).setTitle("Sketch3D ProjAP #"+projZImps.size()+":"+imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projZImps.get(projZImps.size()-1).setMotherImp(imp, 1);
			projZImps.get(projZImps.size()-1).getWindow().addWindowListener(this);
			projZImps.get(projZImps.size()-1).getCanvas().addMouseMotionListener(colorLegend);
			projZImps.get(projZImps.size()-1).getCanvas().addMouseListener(colorLegend);
			projZImps.get(projZImps.size()-1).getWindow().addWindowFocusListener(colorLegend);

			if (imp.getCanvas().droppedGeneUrls != null) {
				IJ.setForegroundColor(255, 255, 255);
				IJ.run(compImps.get(compImps.size()-1), "Label...", "dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
						+imp.getCanvas().droppedGeneUrls+" range=[]");
				compImps.get(compImps.size()-1).changes = false;
				IJ.run(projYImps.get(projYImps.size()-1), "Label...", "dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
						+imp.getCanvas().droppedGeneUrls+" range=[]");
				projYImps.get(projYImps.size()-1).changes = false;
				IJ.run(projZImps.get(projZImps.size()-1), "Label...", "dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
						+imp.getCanvas().droppedGeneUrls+" range=[]");
				projZImps.get(projZImps.size()-1).changes = false;
			}
			if (splitThem)
				projZImps.get(projZImps.size()-1).setDimensions(projZImps.get(projZImps.size()-1).getNChannels(), projZImps.get(projZImps.size()-1).getNFrames(), projZImps.get(projZImps.size()-1).getNSlices());
			projZImps.get(projZImps.size()-1).getWindow().setBackground(this.getBackground());
			if (brainbow){



			}else 
				if (splitThem){
					IJ.run("Channels Tool...");
				}

		} else /*not isEmbryonic*/{
			int fillZfactor = 1;
			int outNSlices = 0;
			for (int c2=0; c2<outChannels; c2++) {
				IJ.showStatus("Processing "+(c2+1)+"/"+outChannels+" channels...");
				ImageStack sketchStack = new ImageStack(200, imp.getHeight()/(imp.getWidth()/200));
				fillZfactor = (isEmbryonic?sketchStack.getHeight():imp.getNSlices())/imp.getNSlices();
				Color frameColor = Color.WHITE;
				outNSlices = (int) (isEmbryonic?sketchStack.getHeight()*0.9:imp.getNSlices());
				for (int i=0;i<imp.getNFrames();i++){

					for (int z=0; z<(outNSlices); z++) {
						drawIP.setColor(Color.BLACK);
						drawIP.fill();
						for(int j=0;j<shownIndexes.length;j++){
							if (shownRois[j] != null) {
								String cellTagName = shownRois[j].getName().split("[\"|=]")[1].trim().toLowerCase();
								if (shownRois[j].getTPosition() == i+1 
										&& cellNames!=null && cellNames.size()>0 && (cellNames.get(c2).equals(cellTagName) || !splitThem)){
									if (fatSynapses && shownRois[j].getName().startsWith("\"syn") ){
										drawIP.setColor(brainbow?new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB()):eightBit?Color.WHITE: mowColors.get(cellTagName));

										if (cellNames.contains(cellTagName)){
											double radius = synapseScale/2;
											drawIP.fill(new OvalRoi((int)shownRois[j].getBounds().getCenterX()-radius,
													(int)shownRois[j].getBounds().getCenterY()-radius, 
													radius*2, 
													radius*2));
											//											IJ.log("drewit"+radius);

										}
									}
								}
							}
						}
						for(int j=0;j<shownIndexes.length;j++){
							if (shownRois[j] != null) {					
								String cellTagName = shownRois[j].getName().split("[\"|=]")[1].trim();
								if (shownRois[j].getTPosition() == i+1  && shownRois[j].getZPosition()*fillZfactor == z+1
										&& cellNames!=null && cellNames.size()>0 && !isEmbryonic && (cellNames.get(c2).equals(cellTagName) || !splitThem)){
									if (!fatSynapses || !shownRois[j].getName().startsWith("\"syn") && brainbowColors.get(cellTagName.toLowerCase())!= null){
										drawIP.setColor(brainbow && brainbowColors.get(cellTagName.toLowerCase())!=null?new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB()):eightBit?Color.WHITE: mowColors.get(cellTagName)!=null?mowColors.get(cellTagName):Color.WHITE);

										if (cellNames.contains(cellTagName))
											drawIP.fill(shownRois[j]);
									} else {

									}
								}
							}
						}
						sketchStack.addSlice(drawIP
								.resize(200, imp.getHeight()/(imp.getWidth()/200), false));
					}
				}

				ImagePlus sketchImp = new ImagePlus("Sketch_"+(splitThem?(nameLists!=null?nameLists.get(nameLists.size()-1):cellNames).get(c2):"Composite"), sketchStack);
				outImps[c2]=sketchImp;
				outImps[c2].getCalibration().pixelDepth = imp.getCalibration().pixelDepth/10>1?imp.getCalibration().pixelDepth/10:1;
				outImps[c2].setMotherImp(imp, 1);
				StackReverser sr = new StackReverser();
				sr.flipStack(outImps[c2]);
			}
			compImps.add(outImps[0]);
			if (splitThem) {
				compImps.set(compImps.size()-1,RGBStackMerge.mergeChannels(outImps, false));
			}
			if (compImps.get(compImps.size()-1)!=null) {
				compImps.get(compImps.size()-1).show();
				compImps.get(compImps.size()-1).getWindow().setBackground(this.getBackground());
				compImps.get(compImps.size()-1).setTitle("Sketch3D #"+compImps.size()+":"+imp.getCanvas().droppedGeneUrls.replace("\n", " "));
				compImps.get(compImps.size()-1).getWindow().addWindowListener(this);
				compImps.get(compImps.size()-1).getCanvas().addMouseMotionListener(colorLegend);
				compImps.get(compImps.size()-1).getCanvas().addMouseListener(colorLegend);
				compImps.get(compImps.size()-1).getWindow().addWindowFocusListener(colorLegend);
			}
			if (Channels.getInstance()!=null) ((Channels)Channels.getInstance()).close();
			IJ.run(compImps.get(compImps.size()-1), "3D Project...", "projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
			projYImps.add(WindowManager.getImage("Projections of Sketch3D"));
			if (splitThem)
				projYImps.get(projYImps.size()-1).setDimensions(projYImps.get(projYImps.size()-1).getNChannels(), projYImps.get(projYImps.size()-1).getNFrames(), projYImps.get(projYImps.size()-1).getNSlices());
			projYImps.get(projYImps.size()-1).setTitle("Sketch3D ProjDV #"+projYImps.size()+":"+imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projYImps.get(projYImps.size()-1).setMotherImp(imp, 1);
			projYImps.get(projYImps.size()-1).getWindow().setBackground(this.getBackground());
			projYImps.get(projYImps.size()-1).getWindow().addWindowListener(this);
			projYImps.get(projYImps.size()-1).getCanvas().addMouseMotionListener(colorLegend);
			projYImps.get(projYImps.size()-1).getCanvas().addMouseListener(colorLegend);
			projYImps.get(projYImps.size()-1).getWindow().addWindowFocusListener(colorLegend);
			if (Channels.getInstance()!=null) ((Channels)Channels.getInstance()).close();
			ImagePlus flipDupImp = compImps.get(compImps.size()-1).duplicate();
			StackReverser sr = new StackReverser();
			sr.flipStack(flipDupImp);
			IJ.run(flipDupImp,"Reslice ...", "output=1.000 start=Left");
			ImagePlus rsImp = IJ.getImage();
			rsImp.setTitle("tempDupReslice");
			if (Channels.getInstance()!=null) ((Channels)Channels.getInstance()).close();
			IJ.run(rsImp, "3D Project...", "projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
			projZImps.add(WindowManager.getImage("Projections of tempDupReslice"));
			projZImps.get(projZImps.size()-1).setTitle("Sketch3D ProjAP #"+projZImps.size()+":"+imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projZImps.get(projZImps.size()-1).setMotherImp(imp, 1);
			projZImps.get(projZImps.size()-1).getWindow().addWindowListener(this);
			projZImps.get(projZImps.size()-1).getCanvas().addMouseMotionListener(colorLegend);
			projZImps.get(projZImps.size()-1).getCanvas().addMouseListener(colorLegend);
			projZImps.get(projZImps.size()-1).getWindow().addWindowFocusListener(colorLegend);
			StackProcessor sp= 
					new StackProcessor(projZImps.get(projZImps.size()-1).getStack(), projZImps.get(projZImps.size()-1).getStack().getProcessor(1));
			sp.flipHorizontal();
			projZImps.get(projZImps.size()-1).setStack(sp.rotateLeft());	

			ImageCanvas ic = projZImps.get(projZImps.size()-1).getCanvas();
			projZImps.get(projZImps.size()-1).getWindow().pack();
			int padH = 1+projZImps.get(projZImps.size()-1).getWindow().getInsets().left
					+projZImps.get(projZImps.size()-1).getWindow().getInsets().right
					+(projZImps.get(projZImps.size()-1).getWindow().optionsPanel.isVisible()?projZImps.get(projZImps.size()-1).getWindow().optionsPanel.getWidth():0)
					+projZImps.get(projZImps.size()-1).getWindow().viewButtonPanel.getWidth();
			int padV = projZImps.get(projZImps.size()-1).getWindow().getInsets().top
					+projZImps.get(projZImps.size()-1).getWindow().getInsets().bottom
					+(projZImps.get(projZImps.size()-1).getWindow() instanceof StackWindow?
							((StackWindow)projZImps.get(projZImps.size()-1).getWindow()).getNScrollbars()
							*((StackWindow)projZImps.get(projZImps.size()-1).getWindow()).zSelector.getHeight()
							:0)
							+projZImps.get(projZImps.size()-1).getWindow().overheadPanel.getHeight();
			projZImps.get(projZImps.size()-1).getWindow().setSize(ic.dstWidth+padH, ic.dstHeight+padV);

			flipDupImp.flush();
			rsImp.close();
			rsImp.getRoiManager().dispose();
			rsImp.setIgnoreFlush(false);
			rsImp.flush();
			IJ.wait(1000);
			if (imp.getCanvas().droppedGeneUrls != null) {
				IJ.setForegroundColor(255, 255, 255);
				IJ.run(compImps.get(compImps.size()-1), "Label...", "dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
						+imp.getCanvas().droppedGeneUrls+" range=[]");
				compImps.get(compImps.size()-1).changes = false;
				IJ.run(projYImps.get(projYImps.size()-1), "Label...", "dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
						+imp.getCanvas().droppedGeneUrls+" range=[]");
				projYImps.get(projYImps.size()-1).changes = false;
				IJ.run(projZImps.get(projZImps.size()-1), "Label...", "dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
						+imp.getCanvas().droppedGeneUrls+" range=[]");
				projZImps.get(projZImps.size()-1).changes = false;
			}
			if (splitThem)
				projZImps.get(projZImps.size()-1).setDimensions(projZImps.get(projZImps.size()-1).getNChannels(), projZImps.get(projZImps.size()-1).getNFrames(), projZImps.get(projZImps.size()-1).getNSlices());
			projZImps.get(projZImps.size()-1).getWindow().setBackground(this.getBackground());
			if (brainbow){


				//				colorLegend.show();
				//				colorLegend.focusGained(new FocusEvent(IJ.getImage().getWindow(), FocusEvent.FOCUS_GAINED));

			}else if (splitThem){
				IJ.run("Channels Tool...");
			}
		}
	}

	public Hashtable<String, Color> getBrainbowColors() {
		return brainbowColors;
	}

	public ArrayList<String> getCellNames() {
		return cellNames;
	}

	public ArrayList<ImagePlus> getCompImps() {
		return compImps;
	}

	public void setCompImps(ArrayList<ImagePlus> compImps) {
		this.compImps = compImps;
	}

	public ArrayList<ImagePlus> getProjZImps() {
		return projZImps;
	}

	public void setProjZImps(ArrayList<ImagePlus> projZImps) {
		this.projZImps = projZImps;
	}

	public ArrayList<ImagePlus> getProjYImps() {
		return projYImps;
	}

	public void setProjYImps(ArrayList<ImagePlus> projYImps) {
		this.projYImps = projYImps;
	}

	public ColorLegend getColorLegend() {
		if (colorLegend != null)
			return colorLegend;
		else if (imp!=null && imp.getMotherImp() != imp) {
			colorLegend = imp.getMotherImp().getRoiManager().getColorLegend();
			return colorLegend;
		} else 
			return null;
	}

	public ColorLegend getColorLegend(Object source) {

		if (this.colorLegend != null) {
			if (this.getImagePlus().equals(source) || this.getCompImps().contains(source)
					|| this.getProjYImps().contains(source)
					|| this.getProjZImps().contains(source)) {
			}else{
				if (source instanceof ImagePlus)
					return ((ImagePlus)source).getRoiManager().getColorLegend(source);
				if (source instanceof RoiManager)
					return ((RoiManager)source).getColorLegend(source);
				return colorLegend;
			}
		} else if (this.colorLegend == null) {
			return imp.getMotherImp().getRoiManager().getColorLegend();
		}
		//		colorLegend.setVisible(true);
		return colorLegend;
	}

	public void setColorLegend(ColorLegend colorLegend) {
		this.colorLegend = colorLegend;
	}

	public void setCellNames(ArrayList<String> cellNames) {
		this.cellNames = cellNames;
	}

	public ArrayList<ArrayList<String>> getNameLists() {
		return nameLists;
	}

	public void setNameLists(ArrayList<ArrayList<String>> nameLists) {
		this.nameLists = nameLists;
	}

	public void shiftROIsXY() {
		GenericDialog gd = new GenericDialog("Shift Tags in XY");
		Panel p = new Panel();
		gd.addPanel(p, GridBagConstraints.CENTER, new Insets(10, 0, 0, 0));
		gd.addNumericField("X", 0, 0);
		gd.addNumericField("Y", 0, 0);
		gd.showDialog();
		if (gd.wasCanceled()) return;
		shiftX = (gd.getNextNumber());
		shiftY = (gd.getNextNumber());
		for (Roi roi:getShownRoisAsArray())
			roi.setLocation((int)(roi.getBounds().getX() + shiftX), (int)roi.getBounds().getY());
		for (Roi roi:getShownRoisAsArray())
			roi.setLocation((int)roi.getBounds().getX(), (int)(roi.getBounds().getY() + shiftY));

		showAll(SHOW_ALL);

	}

	public void setFillTransparency(String alpha) {
		for (Roi roi:getShownRoisAsArray()){
			if (roi.isArea())
				roi.setFillColor(Colors.decode("#"+alpha+Integer.toHexString(roi.getFillColor().getRGB())
						.substring(2),null));
			if (roi.isLine())
				roi.setStrokeColor(Colors.decode("#"+alpha+Integer.toHexString(roi.getStrokeColor().getRGB())
						.substring(2),null));
			showAll(SHOW_ALL);

		}
	}

	public ArrayList<String> getFullCellNames() {
		return fullCellNames;
	}

	public Hashtable<String, Color> getMowColors() {
		return mowColors;
	}

	public void setMowColors(Hashtable<String, Color> mowColors) {
		this.mowColors = mowColors;
	}

	public void realignByTags() {
		Roi[] allRois = getFullRoisAsArray();
		Roi[] shownRois = getShownRoisAsArray();
		int slices = imp.getNFrames();
		ArrayList<String> cellTracks = new ArrayList<String>();
		ArrayList<Integer> cellTrackLengths = new ArrayList<Integer>();
		ArrayList<String> shownCellTracks = new ArrayList<String>();
		ArrayList<Integer> shownCellTrackLengths = new ArrayList<Integer>();
		String recentRoiName = "";
		String[] roiNames = new String[allRois.length];
		String[] shownRoiNames = new String[shownRois.length];
		int count =0;
		for (int ns=0; ns< roiNames.length; ns++) {
			roiNames[ns] = allRois[ns].getName();
			roiNames[ns] = roiNames[ns].split("_")[0] 
					+ (roiNames[ns].split("-").length>1?
							"-"+roiNames[ns].split("-")[1].split("C")[0]:"")+"C";
		}
		for (int ns=0; ns< shownRoiNames.length; ns++) {
			shownRoiNames[ns] = shownRois[ns].getName();
			shownRoiNames[ns] = shownRoiNames[ns].split("_")[0] 
					+ (shownRoiNames[ns].split("-").length>1?
							"-"+shownRoiNames[ns].split("-")[1].split("C")[0]:"")+"C";
		}
		Arrays.sort(roiNames);
		Arrays.sort(shownRoiNames);
		for (int r= 0; r<= shownRoiNames.length; r++){
			String currentRoiName = r<shownRoiNames.length?shownRoiNames[r]:"";
			count++;
			//			IJ.log(currentRoiName+" "+recentRoiName + " "+(currentRoiName.equals(recentRoiName))); 
			if (!currentRoiName.equals(recentRoiName) && recentRoiName!="") {
				shownCellTracks.add(recentRoiName);
				shownCellTrackLengths.add(count);
				count =0;
			}
			recentRoiName = currentRoiName;
		}
		for (int r= 0; r<= roiNames.length; r++){
			String currentRoiName = r<roiNames.length?roiNames[r]:"";
			count++;
			//			IJ.log(currentRoiName+" "+recentRoiName + " "+(currentRoiName.equals(recentRoiName))); 
			if (!currentRoiName.equals(recentRoiName) && recentRoiName!="") {
				cellTracks.add(recentRoiName);
				cellTrackLengths.add(count);
				count =0;
			}
			recentRoiName = currentRoiName;
		}
		int[] zStepShiftX = new int[shownCellTracks.size()];
		int[] zStepShiftY = new int[shownCellTracks.size()];

		int[] roiCenterX1 = new int[shownCellTracks.size()];
		int[] roiCenterY1 = new int[shownCellTracks.size()];
		int[] roiCenterX2 = new int[shownCellTracks.size()];
		int[] roiCenterY2 = new int[shownCellTracks.size()];
		int ch=3;

		for (int s =1; s <= slices; s++) {
			int sn = s+1;
			for(int n=0; n<shownCellTracks.size();n++) {
				for (ch = 1; ch<=imp.getNChannels(); ch++) {

					String shownCellTrackName = shownCellTracks.get(n);
					if (true ){
						String roiName = shownCellTrackName.replace(" \"", " \"_"+ch+"_1_"+s);
						String roiNextSliceName = shownCellTrackName.replace(" \"", " \"_"+ch+"_1_"+sn);
						Roi roi = (Roi) rois.get(roiName);
						Roi roiNextSlice = (Roi) rois.get(roiNextSliceName);
						if (roi != null ) {
							roiCenterX1[n] = (int) roi.getBounds().getCenterX();
							roiCenterY1[n] = (int) roi.getBounds().getCenterY();
						}
						if (roiNextSlice != null ) {
							roiCenterX2[n] = (int) roiNextSlice.getBounds().getCenterX();
							roiCenterY2[n] = (int) roiNextSlice.getBounds().getCenterY();
							zStepShiftX[n] = roiCenterX1[n] - roiCenterX2[n];
							zStepShiftY[n] = roiCenterY1[n] - roiCenterY2[n];
						}				
					}
				}
			}
			IJ.log(s+" "+sn);
			Arrays.sort(zStepShiftX);
			int meanZStepShiftX = 0;
			int sumZStepShiftX = 0;
			int shiftXcount =0;
			int maxZStepShiftX =0;;
			for (int zshiftX:zStepShiftX) {
				if (Math.abs(zshiftX) < 300 && Math.abs(zshiftX) >0){
					if (Math.abs(maxZStepShiftX) < Math.abs(zshiftX))
						maxZStepShiftX = zshiftX;
					shiftXcount++;
					sumZStepShiftX = sumZStepShiftX + zshiftX;
				}
			}
			meanZStepShiftX = sumZStepShiftX/shiftXcount;
			Arrays.sort(zStepShiftY);
			int meanZStepShiftY = 0;
			int sumZStepShiftY = 0;
			int shiftYcount =0;
			int maxZStepShiftY =0;;
			for (int zshiftY:zStepShiftY) {
				if (Math.abs(zshiftY) < 300 && Math.abs(zshiftY) >0){
					if (Math.abs(maxZStepShiftY) < Math.abs(zshiftY))
						maxZStepShiftY = zshiftY;
					shiftYcount++;
					sumZStepShiftY = sumZStepShiftY + zshiftY;
				}
			}
			meanZStepShiftY = sumZStepShiftY/shiftYcount;
			IJ.log("Shift "+sn+" = "+meanZStepShiftX+","+meanZStepShiftY);
			for(int n=0; n<cellTracks.size();n++) {
				for (ch = 1; ch<=imp.getNChannels(); ch++) {

					String cellTrackName = cellTracks.get(n);
					String roiName = cellTrackName.replace(" \"", " \"_"+ch+"_1_"+s);
					String roiNextSliceName = cellTrackName.replace(" \"", " \"_"+ch+"_1_"+sn);

					Roi roi = (Roi) rois.get(roiName);
					Roi roiNextSlice = (Roi) rois.get(roiNextSliceName);
					if (roi != null && roiNextSlice != null) {
						roiNextSlice.setLocation((int)(roiNextSlice.getBounds().getCenterX()-(roiNextSlice.getBounds().getWidth()/2)+meanZStepShiftX)
								, (int)(roiNextSlice.getBounds().getCenterY()-(roiNextSlice.getBounds().getHeight()/2)+meanZStepShiftY));
						imp.updateAndRepaintWindow();
					}
				}

			}


		}
	}

	public void realignByParameters() {
		Roi[] allRois = getFullRoisAsArray();
		String paramString = IJ.openAsString("");
		String[] paramLines = paramString.split("\n");
		for (String paramLine:paramLines){
			if (paramLine.matches("\\d*\\=\\>.*")){
				//				IJ.log(paramLine);
				int slice = Integer.parseInt(paramLine.split("\\=")[0]);
				double tx= Double.parseDouble(paramLine.split("[\\>,]")[1].split(" ")[0]);
				double ty= Double.parseDouble(paramLine.split("[\\>,]")[1].split(" ")[1]);
				double thetaDeg = Double.parseDouble(paramLine.split("[\\=]")[2]);
				double theta = Math.PI*thetaDeg/180;
				double anchorx = imp.getWidth()/2;
				double anchory = imp.getHeight()/2;
				for (Roi roi:allRois) {
					if (slice == roi.getTPosition()){
						double x1 = roi.getBounds().getCenterX();
						double y1 = roi.getBounds().getCenterY();
						double x2=0;
						double y2=0;
						double[] preAffinePoints = {x1,y1};
						double[] postAffinePoints = {x2,y2};
						AffineTransform at = new AffineTransform();
						at.setToTranslation(tx, ty);
						at.transform(preAffinePoints, 0, preAffinePoints, 0, 1);
						at.setToRotation(-theta, anchorx, anchory);
						at.transform(preAffinePoints, 0, postAffinePoints, 0, 1);
						roi.setLocation((int)(postAffinePoints[0]-roi.getBounds().getWidth()/2),
								(int)(postAffinePoints[1]-roi.getBounds().getHeight()/2));
					}
				}
			}
		}
	}

	public void valueChanged(ListSelectionEvent e) {
		int index = 0;
		if (listModel.getSize()==0)
			return;
		if (list.getSelectedIndices().length==0)
			return;
		index = list.getSelectedIndices()[0];
		if (index<0) index = 0;
		if (imp!=null) {
			if(list.getSelectedIndices().length <=1) {
				restore(imp, index, true);
			}
			if (record()) {
				if (Recorder.scriptMode())
					Recorder.recordCall("rm.select(imp, "+index+");");
				else
					Recorder.record("roiManager", "Select", index);
			}
		}

	}

	public DefaultListModel<String> getListModel() {
		return listModel;
	}

	public void setListModel(DefaultListModel<String> listModel) {
		this.listModel = listModel;
	}

	public DefaultListModel<String> getFullListModel() {
		return fullListModel;
	}

	public void setFullListModel(DefaultListModel<String> fullListModel) {
		this.fullListModel = fullListModel;
	}

	public boolean isRmNeedsUpdate() {
		return rmNeedsUpdate;
	}

	public void setRmNeedsUpdate(boolean rmNeedsUpdate) {
		this.rmNeedsUpdate = rmNeedsUpdate;
		if (rmNeedsUpdate)
			updateButton.setBackground(Color.pink);
		else
			updateButton.setBackground(Color.gray);

	}

	private void openCsv(String path) {
		boolean wasVis = this.isVisible();
		this.setVisible(false);
		busy = true;
		//		showAll(SHOW_ALL);
		String s = IJ.openAsString(path);
		String[] objectLines = s.split("\n");
		int fullCount = objectLines.length;

		String s2 = null, name2=null, path2=null;
		ArrayList<Integer> missingZs = new ArrayList<Integer>();
		if (path.contains("object") && s.contains("OBJ_Name")) {
			int[] sliceNumbers = new int[fullCount];
			for (int f=0; f < fullCount; f++) {
				if(objectLines[f].contains("N2UNR"))
					sliceNumbers[f] = Integer.parseInt(objectLines[f].substring(objectLines[f].indexOf("N2UNR")+5, objectLines[f].indexOf("N2UNR")+8));
				IJ.log(""+sliceNumbers[f]);
			}
			Arrays.sort(sliceNumbers);
			for (int e=0; e < sliceNumbers[fullCount-1]; e++) {
				boolean gotit = false;
				for (int f=0; f < fullCount; f++) {
					if (e == sliceNumbers[f]){
						gotit= true;
					}
				}
				if (!gotit) {
					IJ.log("missing" + e);
					missingZs.add(e);
				}
			}




			OpenDialog od = new OpenDialog("Open Contin file...", "");
			String directory = od.getDirectory();
			name2 = od.getFileName();
			if (name2==null)
				return;
			path2 = directory + name2;
			s2  = IJ.openAsString(path2);
		} else {
			this.setVisible(true);
			return;
		}
		//		IJ.log(s);
		String impTitle = this.imp.getTitle();

		long count = 0;
		String[] continLines = s2.split("\n");

		Hashtable<String, String> continHash = new Hashtable<String,String>();
		Hashtable<String, String> objectHash = new Hashtable<String,String>();
		String centerZtestString = objectLines[1].split(",")[4].replace("\"", "");
		String centerZroot = null;
		for (int i=0; i<centerZtestString.length(); i++){
			if (objectLines[2].split(",")[4].replace("\"", "")
					.contains(centerZtestString.substring(0,i))
					&& !centerZtestString.substring(i-1>=0?i-1:0,centerZtestString.length()-1).matches("\\d*")) {
				centerZroot = centerZtestString.substring(0,i);
			}
		}


		long nRois =0;

		for (int i=1; i<continLines.length; i++){
			String continLine=continLines[i];
			continHash.put(continLine.split(",")[1].replace("\"", ""), continLine);
		}

		for (int i=1; i<objectLines.length; i++){
			String objectLine=objectLines[i];
			objectHash.put(objectLine.split(",")[0].replace("\"", ""), objectLine);
		}
		double shrinkFactor = 1/IJ.getNumber("XY dimension should be reduced in scale by what factor?", 1);

		imp.getWindow().setVisible(false);

		for (int obj=1; obj<objectLines.length; obj++) {
			count++;
			IJ.showStatus(""+count+"/"+fullCount+" Tags loaded for "+ imp.getTitle());
			String sObj = objectLines[obj];
			String roiName="";
			String roiColorName="";
			String objType = sObj.split(",")[6].replace("\"", "");

			int centerX = (int)(Integer.parseInt(sObj.split(",")[1].replace("\"", ""))/shrinkFactor) ;
			int centerY = (int)(Integer.parseInt(sObj.split(",")[2].replace("\"", ""))/shrinkFactor);
			int centerZ = Integer.parseInt(sObj.split(",")[4].replace("\"", "")
					.replace(centerZroot, ("")));
			//SPECIAL FIX FOR N2UNR, but now generalized
			int adjustmentZ =0;
			for (int mz = 0; mz<missingZs.size(); mz++){
				int missingZ = missingZs.get(mz);
				if (missingZ<centerZ)
					adjustmentZ = missingZs.indexOf(missingZ);
			}
			centerZ = centerZ-adjustmentZ-1;

			int objNumber =Integer.parseInt(sObj.split(",")[0].replace("\"", ""));
			int conNumber = Integer.parseInt(sObj.split(",")[5].replace("\"", ""));
			//				fillColor = (sCell.split("(;fill:|;\")").length>1?(sCell.split("(;fill:|;\")")[1].startsWith("#")?sCell.split("(;fill:|;\")")[1]:""):"");

			String continLine = continHash.get(""+conNumber);
			if (continLine != null){
				roiName = continLine.split(",")[2].replace("\"", "") + "|=" +continLine.split(",")[3].replace("\"", "") + "|" + objType;
				roiColorName = continLine.split(",")[3].split("[\"-]")[1];
			} else {
				roiName = "unmatchedObject" + "|=" +sObj.split(",")[0].replace("\"", "") + "|" + objType;;
			}

			IJ.log(""+objNumber+" "+roiName+" "+roiColorName+" "+centerX+" "+centerY +" "+ centerZ);

			Color fillColor = Color.black;
			if (roiColorName.contains("blue"))
				fillColor = Color.blue;
			if (roiColorName.contains("magenta"))
				fillColor = Color.magenta;
			if (roiColorName.contains("green"))
				fillColor = Color.green;
			if (roiColorName.contains("orange"))
				fillColor = Color.orange;
			if (roiColorName.contains("pink"))
				fillColor = Color.pink;
			if (roiColorName.contains("yellow"))
				fillColor = Color.yellow;
			if (roiColorName.contains("cyan"))
				fillColor = Color.cyan;
			if (roiColorName.contains("red"))
				fillColor = Color.red;
			String redCode = Integer.toHexString(fillColor.getRed());
			redCode = redCode.length() == 2 ? "" + redCode : "0"
					+ redCode;
			String greenCode = Integer
					.toHexString(fillColor.getGreen());
			greenCode = greenCode.length() == 2 ? "" + greenCode : "0"
					+ greenCode;
			String blueCode = Integer.toHexString(fillColor.getBlue());
			blueCode = blueCode.length() == 2 ? "" + blueCode : "0"
					+ blueCode;
			Color fillColorNew = Colors.decode("#33" + redCode
					+ greenCode + blueCode, defaultColor);
			Color strokeColorNew = Colors.decode("#55" + redCode
					+ greenCode + blueCode, defaultColor);

			if (roiName.contains("|cell")) {
				Roi tRoi = new TextRoi(centerX, centerY, roiName);
				tRoi.setImage(imp);

				//				IJ.log(""+fillColorNew.getAlpha());
				listModel.addElement(roiName);
				fullListModel.addElement(roiName);
				rois.put(roiName, tRoi);
				nRois++;
				tRoi.setFillColor(fillColorNew);
				//					imp.setRoi(tRoi, true);
				((TextRoi)tRoi).updateBounds(imp.getCanvas().getGraphics());
				tRoi.setLocation((int)(centerX - tRoi.getBounds().getWidth()/4), 
						(int)(centerY - tRoi.getBounds().getHeight()/1.2));
				tRoi.setPosition(1, 1, centerZ);
				this.rename(roiName, new int[] { this.getCount() - 1 },
						false);
				//					IJ.log(""+tRoi.getFillColor().getAlpha() +tRoi.getFillColor().toString());
			} else {
				String fromObj = sObj.replace("\",NULL,\"", "\",\"NULL\",\"").replace("\",,\"", "\",\"NULL\",\"").split("\",\"")[7];
				IJ.log(fromObj);
				String[] toObjs = sObj.replace("\",NULL,\"", "\",\"NULL\",\"").replace("\",,\"", "\",\"NULL\",\"").split("\",\"")[8].split(",");
				IJ.log(toObjs.toString()+" "+toObjs.length);
				int[] indexes = new int[toObjs.length*2];
				String fromName = "";
				String toName = "";
				for (int i=0; i<toObjs.length; i++) {
					String toObj = toObjs[i];
					IJ.log(toObj);
					String fromObjectLine = objectHash.get(fromObj);
					int fromX = centerX;
					int fromY = centerY;
					if (fromObjectLine != null) {
						int synFromConNumber = Integer.parseInt(fromObjectLine.split(",")[5].replace("\"", ""));
						String synFromConLine = continHash.get(""+ synFromConNumber);
						if (fromObjectLine != null && synFromConLine != null) {
							fromX = (int)(Integer.parseInt(fromObjectLine.split(",")[1].replace("\"", ""))/shrinkFactor);
							fromY = (int)(Integer.parseInt(fromObjectLine.split(",")[2].replace("\"", ""))/shrinkFactor);
							if (fromName.equals(""))
								fromName = fromName + synFromConLine.split(",")[2].replace("\"", "");
						}
					}
					String toObjectLine = objectHash.get(toObj);
					int toX = centerX;
					int toY = centerY;
					if (fromObjectLine != null && toObjectLine != null) {
						int synFromConNumber = Integer.parseInt(fromObjectLine.split(",")[5].replace("\"", ""));
						int synToConNumber = Integer.parseInt(toObjectLine.split(",")[5].replace("\"", ""));
						String synToConLine = continHash.get(""+ synToConNumber);
						if (toObjectLine != null && synToConLine != null) {
							toX = (int)(Integer.parseInt(toObjectLine.split(",")[1].replace("\"", ""))/shrinkFactor);
							toY = (int)(Integer.parseInt(toObjectLine.split(",")[2].replace("\"", ""))/shrinkFactor);
							toName = toName+(toName.equals("")?"":",")+ synToConLine.split(",")[2].replace("\"", "");
						}
					}

					Roi lRoi = new Line(fromX, fromY, centerX, centerY);
					lRoi.setStrokeColor(strokeColorNew);
					lRoi.setStrokeWidth(2);
					lRoi.setImage(imp);
					listModel.addElement(roiName);
					fullListModel.addElement(roiName);
					rois.put(roiName, lRoi);
					nRois++;
					lRoi.setPosition(1, 1, centerZ);
					indexes[2*i] = listModel.getSize() - 1;
					this.rename(roiName, new int[] { listModel.getSize() - 1 },
							false);

					Roi aRoi = new Arrow(centerX, centerY, toX, toY);
					aRoi.setStrokeColor(strokeColorNew);
					aRoi.setImage(imp);
					listModel.addElement(roiName);
					fullListModel.addElement(roiName);
					rois.put(roiName, aRoi);
					nRois++;
					aRoi.setPosition(1, 1, centerZ);
					indexes[(2*i)+1] = listModel.getSize() - 1;
					this.rename(roiName, new int[] { listModel.getSize() - 1 },
							false);
				}
				//					IJ.runMacro("waitForUser;");
				this.combineRois(imp, indexes);

				if (imp.getRoi() != null){
					listModel.addElement(roiName);
					fullListModel.addElement(roiName);
					rois.put(roiName, imp.getRoi());
					nRois++;
				}
				list.clearSelection();
				list.setSelectedIndices(indexes);
				this.delete(false);
				this.rename("synapse:"+(fromName==""?"unknownCell":fromName)+">"+(toName==""?"unknownCell":toName)+"|="+roiName, new int[] { listModel.getSize() - 1 },
						false);
				Roi cRoi = (Roi)rois.get(listModel.elementAt(listModel.getSize() - 1));
				if (cRoi!=null) {
					cRoi.setPosition(1, 1, centerZ);
					cRoi.setFillColor(strokeColorNew);
				}
			}
		}
		updateShowAll();
		this.imp.setTitle(impTitle);
		imp.getWindow().setVisible(true);
		this.setVisible(wasVis);
		busy = false;
	}

	public void windowClosed(WindowEvent e) {
		for (int i=0;i<compImps.size();i++) {
			if (compImps.get(i) == e.getSource()) {
				compImps.get(i).getWindow().removeWindowFocusListener(colorLegend);
				compImps.get(i).getCanvas().removeMouseMotionListener(colorLegend);
				compImps.get(i).getCanvas().removeMouseListener(colorLegend);
				compImps.remove(i);
			}
		}
		for (int i=0;i<projYImps.size();i++) {
			if (projYImps.get(i) == e.getSource()) {
				projYImps.get(i).getWindow().removeWindowFocusListener(colorLegend);
				projYImps.get(i).getCanvas().removeMouseMotionListener(colorLegend);
				projYImps.get(i).getCanvas().removeMouseListener(colorLegend);
				projYImps.remove(i);
			}
		}
		for (int i=0;i<projZImps.size();i++) {
			if (projZImps.get(i) == e.getSource()) {
				projZImps.get(i).getWindow().removeWindowFocusListener(colorLegend);
				projZImps.get(i).getCanvas().removeMouseMotionListener(colorLegend);
				projZImps.get(i).getCanvas().removeMouseListener(colorLegend);
				projZImps.remove(i);
			}
		}
	}

	public TextField getTextSearchField() {
		return textSearchField;
	}

	public boolean isSearching() {
		// TODO Auto-generated method stub
		return searching;
	}

	public void setSearching(boolean b) {
		// TODO Auto-generated method stub
		searching = b;
	}

	public boolean isControlKeyDown() {
		return controlKeyDown;
	}

	public void setControlKeyDown(boolean controlKeyDown) {
		this.controlKeyDown = controlKeyDown;
	}

	public boolean isBusy() {
		// TODO Auto-generated method stub
		return busy;
	}

	public void setBusy(boolean busy) {
		this.busy = busy;
	}

	public void textValueChanged(TextEvent e) {
		hyperstackCheckbox.setState(true);
	}

	public void zapDuplicateRois() {
		Roi[] ra = this.getFullRoisAsArray();
		for (int r=0;r<ra.length;r++) {
			Roi roi= ra[r];
			for (int s=ra.length;s>r;s--) {
				Roi roi2= ra[s];
				if (roi.getName() != (roi2).getName()){
					boolean duplicated = roi.equals(roi2);
					if (duplicated) {
						IJ.log("zap "+ roi2.getName());
						rois.remove(listModel.getElementAt(s));
						fullListModel.removeElement(listModel.getElementAt(s));
						listModel.remove(s);
						textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
						imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
						imp.getWindow().countLabel.repaint();			
						ra = this.getFullRoisAsArray();
					}
				}
			}
		}
	}
}


