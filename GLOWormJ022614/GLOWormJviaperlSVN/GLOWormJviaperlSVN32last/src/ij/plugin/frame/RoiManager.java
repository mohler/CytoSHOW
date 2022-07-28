package ij.plugin.frame;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.PrimitiveIterator.OfDouble;
import java.awt.List;
import java.util.zip.*;

import javax.swing.AbstractButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.ToolTipManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicListUI;
import javax.vecmath.Color3f;
import javax.vecmath.Point3f;

import org.rhwlab.acetree.AceTree;
import org.rhwlab.acetree.AceTreeNoUI;
import org.rhwlab.tree.VTreeImpl;
import org.rhwlab.tree.VTreeImpl.CellLine;
import org.rhwlab.tree.VTreeImpl.TestCanvas;
import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.MQTVS_VolumeViewer;
import org.vcell.gloworm.MultiQTVirtualStack;
import org.vcell.gloworm.RoiLabelByNumbersSorter;

import com.jogamp.openal.sound3d.Listener;
import com.sun.corba.se.spi.activation._ActivatorImplBase;
import com.sun.xml.internal.ws.developer.UsesJAXBContext;

import client.RemoteMQTVSHandler.RemoteMQTVirtualStack;
import customnode.CustomMesh;
import customnode.CustomMultiMesh;
import customnode.Sphere;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager.ModCellRenderer;
import ij.plugin.Colors;
import ij.plugin.DragAndDrop;
import ij.plugin.Orthogonal_Views;
import ij.plugin.RGBStackMerge;
import ij.plugin.RoiEnlarger;
import ij.plugin.Selection;
import ij.plugin.Slicer;
import ij.plugin.StackReverser;
import ij.plugin.Zoom;
import ij.util.*;
import ij.macro.*;
import ij.measure.*;
import ij3d.Content;
import ij3d.Image3DUniverse;
import ij3d.ImageJ3DViewer;
import javafx.scene.control.Cell;
import javafx.scene.control.Tooltip;

/** This plugin implements the Analyze/Tools/Tag Manager command. */
public class RoiManager extends PlugInFrame implements ActionListener, ItemListener, MouseListener, MouseWheelListener,
		KeyListener, ChangeListener, ListSelectionListener, WindowListener, DocumentListener {

	public static final String LOC_KEY = "manager.loc";
	private static final int BUTTONS = 15;
	private static final int DRAW = 0, FILL = 1, LABEL = 2;
	public static final int SHOW_ALL = 0, SHOW_NONE = 1, LABELS = 2, NO_LABELS = 3;
	private static final int MENU = 0, COMMAND = 1;
	private static final int SHOW_OWN = 4;
	private static int rows = 15;
	private static int lastNonShiftClick = -1;
	private static boolean allowMultipleSelections = true;
	private static String moreButtonLabel = "More " + '\u00bb';
	private static String updateButtonLabel = "Update [u]";
	private JPanel panel;
	private static Frame instance;
	private static int colorIndex = 4;
	private JList<String> list, fullList;
	private Hashtable<String, Roi> rois = new Hashtable<String, Roi>();
	private Hashtable<String, ArrayList<Roi>> roisByNumbers = new Hashtable<String, ArrayList<Roi>>();
	private Hashtable<String, ArrayList<Roi>> roisByRootName = new Hashtable<String, ArrayList<Roi>>();

	public Hashtable<String, ArrayList<Roi>> getROIsByName() {
		return getRoisByRootName();
	}

	public void setROIsByName(Hashtable<String, ArrayList<Roi>> roisByName) {
		this.setRoisByRootName(roisByName);
	}

	private Roi roiCopy;
	private boolean canceled;
	private boolean macro;
	private boolean ignoreInterrupts;
	private JPopupMenu pm;
	private JButton moreButton, colorButton;
	private JCheckBox addRoiSpanCCheckbox = new JCheckBox("Span C", false);
	private JCheckBox addRoiSpanZCheckbox = new JCheckBox("Span Z", false);
	private JCheckBox addRoiSpanTCheckbox = new JCheckBox("Span T", false);
	private JCheckBox showAllCheckbox = new JCheckBox("Show", true);
	private JCheckBox showOwnROIsCheckbox = new JCheckBox("Only own notes", false);
	private ImagePlus imp = null;

	private JCheckBox labelsCheckbox = new JCheckBox("Number", false);
	private JSpinner zSustainSpinner;
	private JSpinner tSustainSpinner;
	private int zSustain = 1;
	private int tSustain = 1;
	private JTextField textFilterField;
	private JTextField textFindingField;
	public JLabel textCountLabel = new JLabel("", JLabel.CENTER);

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
//	private Thread thread;
	private boolean showAll;
	private Roi[] originalRois = null;
	private boolean originalsCloned = false;
	private Hashtable<String, Color> brainbowColors, mowColors;
	private ArrayList<ArrayList<String>> nameLists = new ArrayList<ArrayList<String>>();
	private ArrayList<String> cellNames, fullCellNames;
	private ArrayList<ImagePlus> projYImps = new ArrayList<ImagePlus>();
	private ArrayList<ImagePlus> projZImps = new ArrayList<ImagePlus>();
	private ArrayList<ImagePlus> compImps = new ArrayList<ImagePlus>();
	private ColorLegend colorLegend;
	private double shiftY = 10;
	private double shiftX = 10;
	private int shiftZ;
	private DefaultListModel<String> listModel, fullListModel;
	private boolean rmNeedsUpdate = false;
	private JButton updateButton;
	private boolean searching;
	private boolean busy;
	private JCheckBox hyperstackCheckbox;
	private boolean isEmbryonic = false;
	private String recentName = "";
	private boolean propagateRenamesThruLineage = false;
	private double roiRescaleFactor = 1d;
	private double expansionDistance = 10d;
	private String lastRoiOpenPath;
	private int lastSelectedIndex = 0;
	private int toolTipDefaultDismissDelay;
	private int toolTipDefaultInitialDelay;
	private AceTree aceTree;
	private boolean isRoiScaleFactorSet;
	private JScrollPane scrollPane;
	private int shiftT;
	private int shiftC;

	public RoiManager() {
		super("Tag Manager");
		this.imp = WindowManager.getCurrentImage();
		if (imp.getNDimensions() < 3) {
			for (int i = 0; i < WindowManager.getIDList().length; i++) {
				if (WindowManager.getImage(WindowManager.getIDList()[i]).getStack() instanceof MultiQTVirtualStack
						&& ((MultiQTVirtualStack) WindowManager.getImage(WindowManager.getIDList()[i]).getStack())
								.getLineageMapImage() == imp) {
					imp = WindowManager.getImage(WindowManager.getIDList()[i]);
				}
			}
		}
		if (imp.getMotherImp() != null) {
			ColorLegend cl = imp.getMotherImp().getRoiManager().getColorLegend();
			if (cl != null) {
				if (cl.isPopupHappened()) {
					imp = imp.getMotherImp();
					cl.setPopupHappened(false);
				}

			}
		}
//		if (imp.getRoiManager()!=null) {
//			imp.getRoiManager().setVisible(true);
//			imp.getRoiManager().toFront();
//			return;
//		}
		// //this.setTitle(getTitle()+":"+ imp.getTitle());
		list = new JList<String>();
		list.setCellRenderer(new ModCellRenderer());
		list.addMouseListener(this);
		fullList = new JList<String>();
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);
		imp.setRoiManager(this);
		showWindow(false);
		// WindowManager.addWindow(this);
		// thread = new Thread(this, "Tag Manager");
		// thread.start();

	}

	public RoiManager(boolean hideWindow) {
		super("Tag Manager");
		this.imp = WindowManager.getCurrentImage();
		// //this.setTitle(getTitle()+":"+ imp.getTitle());
		Prefs.showAllSliceOnly = true;
		list = new JList<String>();
		list.setCellRenderer(new ModCellRenderer());
		fullList = new JList<String>();
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);

		imp.setRoiManager(this);
		showWindow(!hideWindow);
		// WindowManager.addWindow(this);
		// thread = new Thread(this, "Tag Manager");
		// thread.start();

	}

	public RoiManager(ImagePlus imp, boolean hideWindow) {
		super("Tag Manager");
		this.imp = imp;
		Prefs.showAllSliceOnly = true;
		list = new JList<String>();
		list.setCellRenderer(new ModCellRenderer());
		fullList = new JList<String>();
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);

		if (imp != null) {
			// //this.setTitle(getTitle()+":"+ imp.getTitle());
			imp.setRoiManager(this);
			showWindow(!hideWindow);
		}
		// WindowManager.addWindow(this);
		// thread = new Thread(this, "TagManagerThread");
		// thread.start();

	}

	public void showWindow(boolean visOn) {
		ImageJ ij = IJ.getInstance();

		list.removeListSelectionListener(this);
		list.removeKeyListener(ij);
		list.removeMouseListener(this);
		list.removeMouseWheelListener(this);
		String lastSearch = "Filter...";
		if (textFilterField != null)
			lastSearch = textFilterField.getText();
		String lastFind = "Find...";
		if (textFindingField != null)
			lastFind = textFindingField.getText();

//		this.removeAll();
		addKeyListener(ij);
		addMouseListener(this);
		addMouseWheelListener(this);
		// WindowManager.addWindow(this);
		// setLayout(new FlowLayout(FlowLayout.CENTER,5,5));
		JPanel bigPanel = new JPanel();
		BorderLayout layout = new BorderLayout();
		bigPanel.setLayout(layout);
		list.setPrototypeCellValue("012345678901234567890123456789");
		list.addListSelectionListener(this);
		list.addKeyListener(ij);
		list.addMouseListener(this);
		list.addMouseWheelListener(this);
		((JComponent) list).setToolTipText(
				"<html>Left-Clicking a list item <br>highlights that tag <br>in the movie window. <br>Buttons and other widgets <br>modify the content of the list <br>and the display of tags <br>in the movie window</html>");

		if (IJ.isLinux())
			list.setBackground(Color.white);
		scrollPane = new JScrollPane(list);
		bigPanel.add(scrollPane, BorderLayout.CENTER);
		panel = new JPanel();
		int nButtons = BUTTONS;
		panel.setLayout(new GridLayout(nButtons + 2, 1, 5, 0));
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

		textFindingField = new HintTextField("Find Tag...");
		textFindingField.getDocument().addDocumentListener(this);
		panel.add(textFindingField);

		addButton("Sort");
		addButton("Deselect");
		addButton("Properties...");
		// addButton("Flatten [F]");
		addButton(moreButtonLabel);
		// showOwnROIsCheckbox.addItemListener(this);
		// panel.add(showOwnROIsCheckbox);
		showAllCheckbox.addItemListener(this);
		panel.add(showAllCheckbox);

		labelsCheckbox.addItemListener(this);
		panel.add(labelsCheckbox);
		zSustainSpinner = new JSpinner(
				new SpinnerNumberModel(zSustain <= getImage().getNSlices() ? zSustain : getImage().getNSlices(), 1,
						getImage().getNSlices(), 1));
		zSustainSpinner.setToolTipText("Adjust Z-depth to sustain display of Tags");
		zSustainSpinner.addChangeListener(this);
		panel.add(zSustainSpinner);
		tSustainSpinner = new JSpinner(
				new SpinnerNumberModel(tSustain <= getImage().getNFrames() ? tSustain : getImage().getNFrames(), 1,
						getImage().getNFrames(), 1));
		tSustainSpinner.setToolTipText("Adjust T-length to sustain display of Tags");
		tSustainSpinner.addChangeListener(this);
		panel.add(tSustainSpinner);
		panel.add(textCountLabel);
		textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
		textCountLabel.setFont(Font.decode("Arial-9"));

		textFilterField = new HintTextField("Filter Tag List...");
		bigPanel.add(textFilterField, BorderLayout.SOUTH);
		textFilterField.addKeyListener(this);
		textFilterField.addActionListener(this);

		bigPanel.add(panel, BorderLayout.EAST);
		addPopupMenu();
//		Dimension size = getSize();
//		if (size.width>270)
//			setSize(size.width-40, size.height);
		// list.remove(0);
		Point loc = Prefs.getLocation(LOC_KEY);
//		if (loc!=null)
//			setLocation(loc);
//		else {
//			GUI.center(this);
//		}
		if (true) {

			list.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

		}

		this.add(bigPanel);
		this.setResizable(true);
		pack();
		if (visOn) {
			ImageWindow imgWin = imp.getWindow();
			this.setVisible(true);
			if (imgWin != null)
				setLocation(imgWin.getLocationOnScreen().x + imgWin.getWidth() + 5, imgWin.getLocationOnScreen().y);
		}

	}

	void addButton(String label) {
		JButton b = new JButton(label);
		b.addActionListener(this);
		b.addKeyListener(IJ.getInstance());
		b.addMouseListener(this);
		if (label.equals(moreButtonLabel))
			moreButton = b;
		if (label.equals(updateButtonLabel))
			updateButton = b;
		panel.add(b);
	}

	void addPopupMenu() {
		pm = new JPopupMenu();
		// addPopupItem("Select All");
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
		addPopupItem("Shift Tags in XYZTC");
		addPopupItem("Set Fill Transparency");
		addPopupItem("Realign by Tags");
		addPopupItem("Realign by Parameters");
		addPopupItem("Auto-advance when tagging");
		addPopupItem("StarryNiteImporter");
		addPopupItem("Resolve a-p l-r sisters");
		addPopupItem("UpdateAceTreeLineage");
		addPopupItem("StarryNiteExporter");
		addPopupItem("Map Neighbors");
		addPopupItem("Color Tags by Group Interaction Rules");
		addPopupItem("Color Objs by Group Interaction Rules");
		addPopupItem("Substitute synapse type objs");
		addPopupItem("Substitute synapse all icospheres");
		addPopupItem("Plot synapses to coords");
//		addPopupItem("Plot phate spheres to coords");
//		addPopupItem("Plot MK c-phate icospheres to coords");
		addPopupItem("Plot AG c-phate icospheres to coords");
		addPopupItem("fixcrappynames");
		addPopupItem("HiLite lineage name conflicts");
		addPopupItem("MeasureROIvolSA");
		addPopupItem("synapseStatsFromROIs");
		addPopupItem("flipRoiHorizontalWithImage");
		addPopupItem("flipRoiVerticalWithImage");
		add(pm);
	}

	void addPopupItem(String s) {
		JMenuItem mi = new JMenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	public synchronized void actionPerformed(ActionEvent e) {

		final ActionEvent fe = e;

		Thread actThread = new Thread(new Runnable() {

			public void run() {
				doAction(fe);
			}

		});
		actThread.start();
	}

	public void doAction(ActionEvent e) {
		// IJ.log(e.toString()+3);

		if (e == null)
			return;
		if (e.getActionCommand().equals("Full\nSet")) {
			imp.getRoiManager().getTextSearchField().setText("");
			e.setSource(textFilterField);
		}
		boolean wasVis = this.isVisible();

		if (e.getSource() == textFilterField) {
//			this.setVisible(false);
			showAll(SHOW_NONE);

			String thisWasTitle = this.getTitle();
			String searchString = textFilterField.getText();
			boolean isRegex = (searchString.startsWith("??"));
			boolean isLinBackTrace = (searchString.toLowerCase().startsWith("?lbt?"));
			boolean isLinTrace = (searchString.toLowerCase().startsWith("?lt?"));
			listModel.removeAllElements();
			prevSearchString = searchString;
			// String[] listStrings = fullList.getItems();
			String impTitle = this.imp.getTitle();
			int count = fullListModel.getSize();
			Dimension dim = list.getSize();
			list.setSize(0, 0);
			textCountLabel.setText("?" + "/" + fullListModel.size());
			imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			imp.getWindow().countLabel.repaint();
			// imp.getWindow().tagsButton.setText(""+fullListModel.size());

			imp.getWindow().tagsButton.repaint();
			searching = true;
			long timeLast = 0;
			long timeNow = 0;

			for (int i = 0; i < count; i++) {
				timeNow = System.currentTimeMillis();
				if (searchString.trim().equalsIgnoreCase("") || searchString.trim().equalsIgnoreCase(".*")) {
					listModel.addElement(fullListModel.get(i));
					// IJ.log(listStrings[i]);
					if (timeNow > timeLast + 100) {
						timeLast = timeNow;
						Graphics g = imp.getCanvas().getGraphics();
						if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
							imp.getCanvas().messageRois.remove("Loading Tags");

						Roi messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
								"   Finding tags that match:\n   " + "Reloading full set" + "..."
										+ imp.getRoiManager().getListModel().getSize() + "");

						((TextRoi) messageRoi).setCurrentFont(
								g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width / 16)));
						messageRoi.setStrokeColor(Color.black);
						this.setRoiFillColor(messageRoi, Colors.decode("#99ffffdd", imp.getCanvas().getDefaultColor()));
						imp.getCanvas().messageRois.put("Loading Tags", messageRoi);
						imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
					}
					continue;
				}

				if (isRegex && ((String) fullListModel.get(i)).matches(searchString.substring(2))) {
					listModel.addElement(fullListModel.get(i));
				} else if (isLinBackTrace) { // Uses complex regex lookahead query to anticipate various terminations of
												// ancestor names
					String matchString = "^\"" + searchString.charAt(5);
					for (int c = 6; c < searchString.length(); c++) {
						matchString = matchString + "(((?=" + searchString.charAt(c) + ")" + searchString.charAt(c)
								+ ")|((?= )))";
					}
					matchString = matchString + " \".*$";
					if (((String) fullListModel.get(i)).matches(matchString)) {
						listModel.addElement(fullListModel.get(i));
					}

				} else if (isLinTrace) {// Uses complex regex lookahead query to anticipate various terminations of
										// ancestor names
					String matchString = "^\"" + searchString.charAt(4);
					for (int c = 5; c < searchString.length(); c++) {
						matchString = matchString + "(((?=" + searchString.charAt(c) + ")" + searchString.charAt(c)
								+ ")|((?= )))";
					}
					matchString = matchString + ".* \".*$";
					if (((String) fullListModel.get(i)).matches(matchString)) {
						listModel.addElement(fullListModel.get(i));
					}

				} else if (((String) fullListModel.get(i)).toLowerCase().contains(searchString.toLowerCase())) {
					listModel.addElement(fullListModel.get(i));
				}
				if (timeNow > timeLast + 100 && !imp.getCanvas().messageRois.containsKey("Finding tags from drop")) {
					timeLast = timeNow;
					Graphics g = imp.getCanvas().getGraphics();
					if (imp.getCanvas().messageRois.containsKey("Finding tags that match"))
						imp.getCanvas().messageRois.remove("Finding tags that match");

					Roi messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
							"   Finding tags that match:\n   \"" + searchString + "\"..."
									+ imp.getRoiManager().getListModel().getSize() + " found.");

					((TextRoi) messageRoi)
							.setCurrentFont(g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width / 16)));
					messageRoi.setStrokeColor(Color.black);
					this.setRoiFillColor(messageRoi, Colors.decode("#99ffffdd", imp.getCanvas().getDefaultColor()));

					imp.getCanvas().messageRois.put("Finding tags that match", messageRoi);
					// imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());

				}

			}
			if (imp.getCanvas().messageRois.containsKey("Finding tags that match"))
				imp.getCanvas().messageRois.remove("Finding tags that match");
			if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
				imp.getCanvas().messageRois.remove("Loading Tags");

			searching = false;
			list.setSize(dim);
			textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
			imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			imp.getWindow().countLabel.repaint();

			imp.getWindow().tagsButton.repaint();

			IJ.runMacro("print(\"\\\\Update:\")");

			if (!(imp.getWindow().getTitle().matches(".*[XY]Z +\\d+.*"))) {
				ImageWindow imgWin = imp.getWindow();
				this.setVisible(wasVis);
				if (imgWin != null)
					setLocation(imgWin.getLocationOnScreen().x + imgWin.getWidth() + 5, imgWin.getLocationOnScreen().y);
			}
			showAll(SHOW_ALL);
			if (this.getDisplayedRoisAsArray(imp.getSlice(), imp.getFrame()).length < 1) {
				int nearestTagIndex = -1;
				int closestYet = imp.getNSlices() + imp.getNFrames();
				Roi[] rois = getShownRoisAsArray();
				for (int r = 0; r < rois.length; r++) {
					if (Math.abs(rois[r].getZPosition() - imp.getSlice())
							+ Math.abs(rois[r].getTPosition() - imp.getFrame()) < closestYet) {
						closestYet = Math.abs(rois[r].getZPosition() - imp.getSlice())
								+ Math.abs(rois[r].getTPosition() - imp.getFrame());
						nearestTagIndex = r;
					}
					// IJ.log(""+closestYet);
				}
				if (nearestTagIndex >= 0) {
					new ij.macro.MacroRunner("roiManager('select', " + nearestTagIndex + ", " + imp.getID() + ");");
					select(-1);
					while (imp.getRoi() == null)
						IJ.wait(100);
					imp.killRoi();
				}
			}

		} else {

			int modifiers = e.getModifiers();
			altKeyDown = (modifiers & ActionEvent.ALT_MASK) != 0 || IJ.altKeyDown();
			shiftKeyDown = (modifiers & ActionEvent.SHIFT_MASK) != 0 || IJ.shiftKeyDown();
			controlKeyDown = (modifiers & ActionEvent.CTRL_MASK) != 0 || IJ.controlKeyDown();
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SHIFT);
			IJ.setKeyUp(KeyEvent.VK_CONTROL);
			String label = e.getActionCommand();
			if (label == null) {
				return;
			}
			String command = label;
			if (command.equals("Add\n(ctrl-t)")) {
				Roi roi = imp.getRoi();
				if (roi == null) {
					return;
				}
				String newName = "";
				newName = roi.getName();
				if (newName == "") {
					if (getSelectedRoisAsArray().length > 0) {
						String selName = getSelectedRoisAsArray()[0].getName();
						newName = promptForName(selName);
					} else {
						newName = promptForName(recentName);
					}
				}
				Color existingColor = null;
				Enumeration<Roi> roisElements = rois.elements();
				while (roisElements.hasMoreElements()) {
					Roi nextRoi = roisElements.nextElement();
					String nextName = nextRoi.getName();
					if (nextName.startsWith("\"" + newName + " \"")) {
						existingColor = nextRoi.getFillColor();
						break;
					}
				}

				String existinghexName = "";
				if (existingColor != null) {
					int existingColorInt = existingColor.getRGB();
					String existinghexInt = Integer.toHexString(existingColorInt);
					int l = existinghexInt.length();
					for (int c = 0; c < 8 - l; c++)
						existinghexInt = "0" + existinghexInt;
					existinghexName = "#" + existinghexInt;
				}

				add(roi, shiftKeyDown, altKeyDown, controlKeyDown);

				if (existingColor != null) {
					this.setRoiFillColor(rois.get(getListModel().get(getListModel().getSize() - 1)), existingColor);
					imp.getRoiManager().select(imp, getListModel().getSize() - 1);
				}

				updateShowAll();
			} else if (command.equals("Update [u]")) {
				if (imp.getMultiChannelController() != null)
					imp.getMultiChannelController().updateRoiManager();
			} else if (command.equals("Delete"))
				delete(false);
			else if (command.equals("Delete ")) {
				if (list.getSelectedIndices().length == 1)
					delete(false);
			} else if (command.equals("Color")) {
				Roi[] selectedRois = getSelectedRoisAsArray();
				if (selectedRois.length > 0) {
					int fillColorInt = Colors.decode("#00000000", Color.black).getRGB();
					if (selectedRois[0].getFillColor() != null) {
						fillColorInt = selectedRois[0].getFillColor().getRGB();
					}
					String hexInt = Integer.toHexString(fillColorInt);
					int l = hexInt.length();
					for (int c = 0; c < 8 - l; c++)
						hexInt = "0" + hexInt;
					String hexName = "#" + hexInt;
//					Color fillColor = JColorChooser.showDialog(this.getFocusOwner(),
//							"Pick a color for " + this.getSelectedRoisAsArray()[0].getName() + "...",
//							Colors.decode(hexName, Color.cyan));
					ColorChooser cc = new ColorChooser("Pick a color for " + this.getSelectedRoisAsArray()[0].getName() + "...",
							Colors.decode(hexName, Color.cyan), false, imp.getWindow());
					Color fillColor = cc.getColor();

					String whackchacha = hexName.substring(0, 3);
					if (whackchacha.equals("#00"))
						whackchacha = "#88";
					String colorPickerString = Colors.colorToHexString(fillColor);
					String alphaCorrFillColorString = colorPickerString;
					while (!alphaCorrFillColorString.matches("#........"))
						if (alphaCorrFillColorString.matches("#......"))
							alphaCorrFillColorString = alphaCorrFillColorString.replaceAll("#", whackchacha);

					fillColor = Colors.decode(alphaCorrFillColorString, fillColor);

					ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
					ArrayList<String> rootNames = new ArrayList<String>();

					for (Roi selRoi : getSelectedRoisAsArray()) {
						String rootName = selRoi.getName().contains("\"")
								? "\"" + selRoi.getName().split("\"")[1] + "\""
								: selRoi.getName().split("_")[0].trim();
						// rootName = rootName.contains(" ")?rootName.split("[_\\-
						// ]")[0].trim():rootName;
						String[] rootChunks = selRoi.getName().split("_");
						String rootFrame = rootChunks[rootChunks.length - 1].replaceAll("[CZT]", "").split("-")[0];
						if (!rootNames_rootFrames.contains(rootName + "_" + rootFrame)) {
							rootNames_rootFrames.add(rootName + "_" + rootFrame);
							rootNames.add(rootName);
						}
					}

					ArrayList<Roi> nameMatchArrayList = new ArrayList<Roi>();
					// Roi[] fullrois = getFullRoisAsArray();

					for (int n = 0; n < rootNames.size(); n++) {
						String rootName = rootNames.get(n);
						nameMatchArrayList.addAll(getRoisByRootName().get(rootName));
						// int fraa = fullrois.length;
						// for (int r=0; r < fraa; r++) {
						// String nextName = fullrois[r].getName();
						// if (nextName.startsWith(rootName)){
						// nameMatchIndexArrayList.add(r);
						// }
						// }

					}
					// int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
					for (Roi nmRoi : nameMatchArrayList) {
						// nameMatchIndexes[i] = nameMatchIndexArrayList.get(i);
						// fullrois[nameMatchIndexes[i]]this.setRoiFillColor(Colors.decode(alphaCorrFillColorString,
						// fillColor));
						this.setRoiFillColor(nmRoi, Colors.decode(alphaCorrFillColorString, fillColor));

					}
					// this.setSelectedIndexes(nameMatchIndexes);
					//
					// if (runCommand("set fill color", alphaCorrFillColorString)) {
					// }

				}
				this.close();
				ImageWindow imgWin = imp.getWindow();
				this.setVisible(true);
				if (imgWin != null)
					setLocation(imgWin.getLocationOnScreen().x + imgWin.getWidth() + 5, imgWin.getLocationOnScreen().y);
				updateShowAll();

			} else if (command.equals("Rename")) {

				ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
				ArrayList<String> rootNames = new ArrayList<String>();

				Roi[] selRois = getSelectedRoisAsArray();
				int[] selIndexes = getSelectedIndexes();
				if (selRois.length <1) 
					return;
				int selectedTime = selRois[0].getTPosition();
				String selName = selRois[0].getName();
				String newName = promptForName(selName.split(" ")[0].replace("\"", "").trim());
				if (newName == null)
					return;

				if (selRois.length == 1) {
					if (!propagateRenamesThruLineage) {
						rename(newName, selIndexes, false);
						return;
					}
				} else if (selRois.length > 1 || (selRois.length == 1 && !propagateRenamesThruLineage)) {
					rename(newName, selIndexes, false);
					return;
				} else {
					return;
				}

				Color existingColor = null;
				Roi[] rois = getFullRoisAsArray();
				int fraa = rois.length;
				int r = 0;
				while (existingColor == null) {
					if (r > rois.length - 1) {
						break;
					}

					String nextName = rois[r].getName();
					if (nextName.startsWith("\"" + newName + " \"")) {
						existingColor = rois[r].getFillColor();
					}
					r++;
				}
				String existinghexName = "";
				if (existingColor != null) {
					int existingColorInt = existingColor.getRGB();
					String existinghexInt = Integer.toHexString(existingColorInt);
					int l = existinghexInt.length();
					for (int c = 0; c < 8 - l; c++)
						existinghexInt = "0" + existinghexInt;
					existinghexName = "#" + existinghexInt;
				}

				for (Roi selRoi : selRois) {

					String rootName = selRoi.getName().contains("\"") ? ("\"" + selRoi.getName().split("\"")[1] + "\"")
							: selRoi.getName().split("_")[0];

					String[] rootChunks = selRoi.getName().split("_");
					String rootFrame = rootChunks[rootChunks.length - 1].replaceAll("[CZT]", "").split("-")[0];
					if (!rootNames_rootFrames.contains(rootName + "_" + rootFrame)) {
						rootNames_rootFrames.add(rootName + "_" + rootFrame);
						rootNames.add(rootName);
					}
				}
				ArrayList<Integer> nameMatchIndexArrayList = new ArrayList<Integer>();
				ArrayList<String> nameReplacementArrayList = new ArrayList<String>();

				for (int n = 0; n < rootNames.size(); n++) {
					String rootName = rootNames.get(n);
					Roi[] rois2 = getFullRoisAsArray();
					int fraaa = rois2.length;
					for (int r2 = 0; r2 < fraaa; r2++) {
						String nextName = rois2[r2].getName();
						if (!rootName.replace("\"", "").trim().equals("")) {
							String rootMatch = "\"" + rootName.replace("\"", "").trim()
									+ (propagateRenamesThruLineage ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
							if (nextName.matches(rootMatch)) {
								if (!propagateRenamesThruLineage && selRois[0] == rois2[r2]) {
									nameMatchIndexArrayList.add(r2);
									nameReplacementArrayList.add(nextName.replaceAll(
											"\"" + rootName.replace("\"", "").trim() + "([m|n|l|r|a|p|d|v|g|h]*) +\".*",
											newName + "$1"));
								} else if (selectedTime < rois2[r2].getTPosition() || selRois[0] == rois2[r2]) {
									nameMatchIndexArrayList.add(r2);
									nameReplacementArrayList.add(nextName.replaceAll(
											"\"" + rootName.replace("\"", "").trim() + "([m|n|l|r|a|p|d|v|g|h]*) +\".*",
											newName + "$1"));
								}
							}
						} else {
							if (nextName.matches("(\"?)" + rootName.replace("\"", "") + "(\"?).*")) {
								nameMatchIndexArrayList.add(r2);
								nameReplacementArrayList.add(nextName
										.replaceAll("(\"?)" + rootName.replace("\"", "") + "(\"?)(.*)", newName));
							}
						}
					}
				}
				int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
				String[] newNames = new String[nameMatchIndexArrayList.size()];
				for (int n = 0; n < nameMatchIndexes.length; n++) {
					nameMatchIndexes[n] = nameMatchIndexArrayList.get(n);
					newNames[n] = nameReplacementArrayList.get(n);
				}
				if (rename(newNames, nameMatchIndexes, false)) {
					if (existinghexName != "") {
						for (int i = 0; i < nameMatchIndexes.length; i++) {

							this.setRoiFillColor(rois[nameMatchIndexes[i]], existingColor);

						}
					}

					this.close();
					ImageWindow imgWin = imp.getWindow();
					this.setVisible(wasVis);
					if (imgWin != null)
						setLocation(imgWin.getLocationOnScreen().x + imgWin.getWidth() + 5,
								imgWin.getLocationOnScreen().y);
				}

			}

			else if (command.equals("Properties..."))
				setProperties(null, -1, null);
			else if (command.equals("Flatten"))
				IJ.run("Flatten_Tags");
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
			} else if (command.equals("Adv.")) {
				// if (imp.getMotherImp().getRoiManager().getColorLegend(e.getSource()) != null)
				// imp.getMotherImp().getRoiManager().getColorLegend(e.getSource()).setVisible(true);
				ImageWindow imgWin = imp.getWindow();
				this.setVisible(true);
				if (imgWin != null)
					setLocation(imgWin.getLocationOnScreen().x + imgWin.getWidth() + 5, imgWin.getLocationOnScreen().y);
			} else if (command.equals("Show")) {
				showAll(SHOW_ALL);
				imp.getWindow().hideShowButton.setActionCommand("Hide");
				imp.getWindow().hideShowButton.setToolTipText("Showing Tags...click to Hide Tags");
				imp.getWindow().hideShowButton
						.setIcon(new ImageIcon(ImageWindow.class.getResource("images/showIcon.png")));
			} else if (command.equals("Hide")) {
				showAll(SHOW_NONE);
				imp.getWindow().hideShowButton.setActionCommand("Show");
				imp.getWindow().hideShowButton.setToolTipText("Hiding Tags...click to Show Tags");
				imp.getWindow().hideShowButton
						.setIcon(new ImageIcon(ImageWindow.class.getResource("images/hideIcon.png")));
			} else if (command.equals("Fill"))
				drawOrFill(FILL);
			else if (command.equals("Draw"))
				drawOrFill(DRAW);
			else if (command.equals("Deselect"))
				select(-1);
			else if (command.equals(moreButtonLabel)) {
				Point ploc = panel.getLocation();
				Point bloc = moreButton.getLocation();
				pm.show(this, ploc.x, bloc.y);
				return;
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
				if (listModel.size() > 0 /* && listModel.get(0).split("_").length == 5 */) {
					int chunksLength = listModel.get(0).split("_").length;
					if (controlKeyDown)
						sortmode = chunksLength - 1;
					if (altKeyDown)
						sortmode = chunksLength - 3;
					if (shiftKeyDown)
						sortmode = chunksLength - 2;
//					if (sortmode>1)
//						sortmode++;
				}
				sort();
			} else if (command.equals("Specify..."))
				specify();
			else if (command.equals("Remove Slice Info"))
				removeSliceInfo();
			else if (command.equals("Help"))
				help();
			else if (command.equals("Options..."))
				options();
			else if (command.equals("\"Show All\" Color..."))
				setShowAllColor();
			else if (command.equals("Get ROIs this Slice")) {
				ImagePlus imp = this.imp;

				getSliceSpecificRoi(imp, imp.getSlice(), imp.getFrame());
			} else if (command.equals("Copy Selected to Other Images"))
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
			} else if (command.equals("Sketch\n3D") && !imp.isSketch3D()) {
				controlKeyDown = true;
				busy = true;
				imp.getWindow().sketch3DButton
						.setIcon(new ImageIcon(ImageWindow.class.getResource("images/3D_42578.gif")));
				sketch3D(e.getSource());
				// sketchVolumeViewer(e.getSource());
				busy = false;
				imp.getWindow().sketch3DButton
						.setIcon(new ImageIcon(ImageWindow.class.getResource("images/3D_47973.gif")));
			} else if (command.equals("Sketch\n3D") && imp.isSketch3D() && getColorLegend() != null) {
				JPopupMenu pm2 = new JPopupMenu();
				for (String modeString : getColorLegend().modes) {
					JMenuItem mi2 = new JMenuItem(
							modeString.replace("Both Checked & Unchecked", "All").replace("Checked", "Chosen"));
					mi2.addActionListener(this);
					pm2.add(mi2);
				}
				JMenuItem mi2 = new JMenuItem("Clear Choices");
				mi2.addActionListener(this);
				pm2.add(mi2);
				((JMenuItem) pm2.getComponent(getColorLegend().getChoice().getSelectedIndex()))
						.setLabel("� " + getColorLegend().getChoice().getSelectedItem()
								.replace("Both Checked & Unchecked", "All").replace("Checked", "Chosen"));
				imp.getWindow().sketch3DButton.add(pm2);
				pm2.show(imp.getWindow().sketch3DButton, 1, imp.getWindow().sketch3DButton.getHeight());
			} else if (command.equals("Sketch\nVV")) {
				controlKeyDown = true;
				busy = true;
				imp.getWindow().sketchVVButton
						.setIcon(new ImageIcon(ImageWindow.class.getResource("images/VV_55184.gif")));
				if (shiftKeyDown) {
					sketchVolumeSlicer(e.getSource());
				} else {
					sketchVolumeViewer(e.getSource());
				}
				busy = false;
				imp.getWindow().sketchVVButton
						.setIcon(new ImageIcon(ImageWindow.class.getResource("images/VV_57282.gif")));
			} else if (e.getSource() instanceof MenuItem
					&& ((MenuItem) e.getSource()).getParent() instanceof ColorLegend && getColorLegend() != null) {
				if (command != null) {
					if (command == "Clear Choices") {
						getColorLegend().actionPerformed(new ActionEvent(getColorLegend().clearButton,
								ActionEvent.ACTION_PERFORMED, getColorLegend().clearButton.getActionCommand()));
					} else {
						getColorLegend().getChoice().select(command.replace("� ", "").replace("Chosen", "Checked")
								.replace("All", "Both Checked & Unchecked"));
						getColorLegend().itemStateChanged(new ItemEvent(getColorLegend().getChoice(),
								ItemEvent.ITEM_STATE_CHANGED, getColorLegend().getChoice(), ItemEvent.SELECTED));
					}
				}
			} else if (command.equals("Sketch3D Split Cell Channels")) {
				shiftKeyDown = true;
				busy = true;
				sketch3D(e.getSource());
				busy = false;
			} else if (command.equals("Define Connectors")) {
				defineConnectors();
			} else if (command.equals("Zap Duplicate Rois")) {
				zapDuplicateRois();
			} else if (command.equals("Shift Tags in XYZTC")) {
				shiftROIsXYZTC();
			} else if (command.equals("Set Fill Transparency")) {
				setFillTransparency("33");
			} else if (command.equals("Realign by Tags")) {
				realignByTags();
			} else if (command.equals("Realign by Parameters")) {
				realignByParameters();
			} else if (command.equals("Auto-advance when tagging")) {
				new ij.macro.MacroRunner(
						IJ.openUrlAsString("http://fsbill.cam.uchc.edu/gloworm/Xwords/WormAtlasLabelerMacro.txt"));
			} else if (command.equals("StarryNiteImporter")) {
				importStarryNiteNuclei("");
			} else if (command.equals("Resolve a-p l-r sisters")) {
				resolveMNGHsisters();
			} else if (command.equals("UpdateAceTreeLineage")) {
				String impDir = "";
				FileInfo fileInfo = imp.getOriginalFileInfo();
				if (fileInfo != null) {
					impDir = fileInfo.directory;
				} else {
					if (imp.getStack() instanceof RemoteMQTVirtualStack) {
						impDir = IJ.getDirectory("home")
								+ ((RemoteMQTVirtualStack) imp.getStack()).getSceneFileName().replace(".scn", "")
								+ "CyShAceTreeExchg";
						new File(impDir).mkdirs();
						IJ.saveString(((RemoteMQTVirtualStack) imp.getStack()).getSceneFileText(),
								impDir + File.separator + ((RemoteMQTVirtualStack) imp.getStack()).getSceneFileName());
					}
				}
				this.exportROIsAsZippedStarryNiteNuclei(impDir + File.separator + "aaa_emb_Unskipped.zip");
			} else if (command.equals("StarryNiteExporter")) {
				this.exportROIsAsZippedStarryNiteNuclei(IJ.getFilePath("Save zipped SN nuclei"));
			} else if (command.equals("Map Neighbors")) {
				mapNearNeighborContacts();
			} else if (command.equals("Color Tags by Group Interaction Rules")) {
				colorTagsByGroupInteractionRules();
			} else if (command.equals("Color Objs by Group Interaction Rules")) {
				colorObjsByGroupInteractionRules();
			} else if (command.equals("Substitute synapse type objs")) {
				swapSynapseObjTypes(false);
			} else if (command.equals("Substitute synapse all icospheres")) {
				swapSynapseObjTypes(true);
			} else if (command.equals("Plot synapses to coords")) {
				plotSynapseObjsToCoords();
			} else if (command.equals("Plot phate spheres to coords")) {
				plotPhateObjsToCoordsSpheres();
			} else if (command.equals("Plot MK c-phate icospheres to coords")) {
				plotManikFmtPhateObjsToCoordsIcospheres();
			} else if (command.equals("Plot AG c-phate icospheres to coords")) {
				plotAlexFmtPhateObjsToCoordsIcospheres();
			} else if (command.equals("fixcrappynames")) {
				fixdamnRedundantNames();
			} else if (command.equals("HiLite lineage name conflicts")) {
				scanForRedundantContemporaneousNames();
			} else if (command.equals("MeasureROvolSA") || command.equals("MeasureROIvolSA")) {
				this.sketchVolumeMeasurer(null);
			} else if (command.equals("synapseStatsFromROIs")) {
				this.synapseStatsFromRois();
			} else if (command.equals("flipRoiHorizontalWithImage")) {
				for (Roi roi:getSelectedRoisAsArray()) {
					roi = this.flipRoiWithImage(roi, false);
					rois.replace(roi.getName(), roi);
					IJ.log("flipH "+roi.getName());
				}
			} else if (command.equals("flipRoiVerticalWithImage")) {
				for (Roi roi:getSelectedRoisAsArray()) {
					roi = this.flipRoiWithImage(roi, true);
					rois.replace(roi.getName(), roi);
					IJ.log("flipV "+roi.getName());
				}
			}

			this.imp.getCanvas().requestFocus();
		}
		// IJ.log("THREAD DONE");
	}

	private void resolveMNGHsisters() {
		Roi[] fullROIs = getFullRoisAsArray();
		int maxT = 0;
		int maxLength = 0;
		for (Roi roi : fullROIs) {
			if (maxT < roi.getTPosition()) {
				maxT = roi.getTPosition();
			}
			if (maxLength < roi.getName().length()) {
				maxLength = roi.getName().length();
			}
		}
		ArrayList<String> propCandidateNames_rootFrames = new ArrayList<String>();
		ArrayList<String> propCandidateNames = new ArrayList<String>();
		ArrayList<Integer> nameMatchIndexArrayList = new ArrayList<Integer>();
		ArrayList<String> nameReplacementArrayList = new ArrayList<String>();

		for (int l = 0; l < maxLength; l++) {
			String[] roiNameKeys = getRoisByRootName().keySet()
					.toArray(new String[getRoisByRootName().keySet().size()]);
			Arrays.sort(roiNameKeys);
			String[] roiFlippedKeys = new String[roiNameKeys.length];
			for (int k = 0; k < roiNameKeys.length; k++) {
				roiFlippedKeys[roiNameKeys.length - 1 - k] = roiNameKeys[k];
			}
			roiNameKeys = roiFlippedKeys;

			nameMatchIndexArrayList = new ArrayList<Integer>();
			nameReplacementArrayList = new ArrayList<String>();
			for (String rootName : roiNameKeys) {
//				if (rootName == "\" \"" || rootName.startsWith("\"Nuc")) 
//					continue;
				ArrayList<Roi> theseRois = getRoisByRootName().get(rootName);
				if (theseRois == null || theseRois.size() == 0)
					continue;
				String thisName = rootName;
				String thatName = "";

				int maxThisT = 0;
				int minThisT = Integer.MAX_VALUE;
				for (Roi roi : theseRois) {
					if (maxThisT < roi.getTPosition()) {
						maxThisT = roi.getTPosition();
					}
					if (minThisT > roi.getTPosition()) {
						minThisT = roi.getTPosition();
					}
				}
				ArrayList<Roi> theseTSortedRois = new ArrayList<Roi>();
				for (int t = minThisT; t <= maxThisT; t++) {
					for (Roi roi : theseRois) {
						if (roi.getTPosition() == t) {
							theseTSortedRois.add(roi);
						}
					}
				}
				theseRois = theseTSortedRois;
				if (theseRois == null || theseRois.size() == 0)
					continue;
				Roi thisRoi = theseRois.get(0);

				int thisX = (int) thisRoi.getBounds().getCenterX();
				int thisZ = thisRoi.getZPosition();
				int thisT = thisRoi.getTPosition();
				if (thisName.endsWith("m \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "n \"";
				} else if (thisName.endsWith("n \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "m \"";
				} else if (thisName.endsWith("g \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "h \"";
				} else if (thisName.endsWith("h \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "g \"";
				} else {
					continue;
				}
				ArrayList<Roi> thoseRois = getRoisByRootName().get(thatName);
				if (thoseRois == null) {
					continue;
				}

				int maxThatT = 0;
				int minThatT = Integer.MAX_VALUE;
				for (Roi roi : thoseRois) {
					if (maxThatT < roi.getTPosition()) {
						maxThatT = roi.getTPosition();
					}
					if (minThatT > roi.getTPosition()) {
						minThatT = roi.getTPosition();
					}
				}
				ArrayList<Roi> thoseTSortedRois = new ArrayList<Roi>();
				for (int t = minThatT; t <= maxThatT; t++) {
					for (Roi roi : thoseRois) {
						if (roi.getTPosition() == t) {
							thoseTSortedRois.add(roi);
						}
					}
				}
				thoseRois = thoseTSortedRois;
				if (thoseRois == null || thoseRois.size() == 0)
					continue;
				Roi thatRoi = thoseRois.get(0);
				int thatX = (int) thatRoi.getBounds().getCenterX();
				int thatZ = thatRoi.getZPosition();
				int thatT = thatRoi.getTPosition();
				if (thatT != thisT)
					continue;
				ArrayList<Integer> thisIndexesArrayList = new ArrayList<Integer>();
				ArrayList<Integer> thatIndexesArrayList = new ArrayList<Integer>();
				ArrayList<Roi> theseAlreadyHit = new ArrayList<Roi>();
				ArrayList<Roi> thoseAlreadyHit = new ArrayList<Roi>();
				int thisHitCount = 0;
				int thatHitCount = 0;
				if (thisName.startsWith("\"ABan ") || thisName.startsWith("\"ABam "))
					IJ.wait(1);
				for (int r = 0; r < fullROIs.length; r++) {
					if (fullROIs[r] != null) {
						if (theseRois.contains(fullROIs[r])) {
							if (!theseAlreadyHit.contains(fullROIs[r])) {
								thisIndexesArrayList.add(r);
								theseAlreadyHit.add(fullROIs[r]);
								thisHitCount++;
							} else
								IJ.wait(1);
						} else if (thoseRois.contains(fullROIs[r])) {
							if (!thoseAlreadyHit.contains(fullROIs[r])) {
								thatIndexesArrayList.add(r);
								thoseAlreadyHit.add(fullROIs[r]);
								thatHitCount++;
							} else
								IJ.wait(1);
						}
					}
				}
				int[] thisIndexesArray = new int[thisIndexesArrayList.size()];
				for (int t = 0; t < thisIndexesArray.length; t++) {
					thisIndexesArray[t] = thisIndexesArrayList.get(t);
				}
				int[] thatIndexesArray = new int[thatIndexesArrayList.size()];
				for (int t = 0; t < thatIndexesArray.length; t++) {
					thatIndexesArray[t] = thatIndexesArrayList.get(t);
				}

				if (thisX < thatX) {
					IJ.log(thisRoi.getName().replace(thisName, thisName.substring(0, thisName.length() - 3) + "a")
							+ "  "
							+ thatRoi.getName().replace(thatName, thatName.substring(0, thatName.length() - 3) + "p"));
				} else {
					IJ.log(thisRoi.getName().replace(thisName, thisName.substring(0, thisName.length() - 3) + "p")
							+ "  "
							+ thatRoi.getName().replace(thatName, thatName.substring(0, thatName.length() - 3) + "a"));
				}
				String thisTargetString = thisName.replace("\"", "").split(" ")[0].trim();
				String thatTargetString = thatName.replace("\"", "").split(" ")[0].trim();
				String thisSwapString = "";
				String thatSwapString = "";

				if (thisTargetString.matches("EMS(a|p|m|n|g|h|l|r)")) {
					if (thisX < thatX) {
						thisSwapString = thisTargetString.replace(thisTargetString, "MS");
						thatSwapString = thatTargetString.replace(thatTargetString, "E");
					} else {
						thisSwapString = thisTargetString.replace(thisTargetString, "E");
						thatSwapString = thatTargetString.replace(thatTargetString, "MS");
					}
				} else {
					if (thisTargetString.matches("Enn") || thisTargetString.matches("Enm")) {
						IJ.wait(1);
					}
					if (thisX < thatX) {
						thisSwapString = thisTargetString.replace(thisTargetString,
								thisTargetString.substring(0, thisTargetString.length() - 1) + "a");
						thatSwapString = thatTargetString.replace(thatTargetString,
								thatTargetString.substring(0, thatTargetString.length() - 1) + "p");
					} else {
						thisSwapString = thisTargetString.replace(thisTargetString,
								thisTargetString.substring(0, thisTargetString.length() - 1) + "p");
						thatSwapString = thatTargetString.replace(thatTargetString,
								thatTargetString.substring(0, thatTargetString.length() - 1) + "a");
					}
				}

				boolean propRenameLin = true;
				String propCandidateName = thisTargetString;
				Roi[] rois2 = getFullRoisAsArray();
				int fraaa = rois2.length;
				for (int r2 = 0; r2 < fraaa; r2++) {
					String nextName = rois2[r2].getName();
					if (!propCandidateName.replace("\"", "").trim().equals("")) {
						String rootMatch = "\"" + propCandidateName.replace("\"", "").trim()
								+ (propRenameLin ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
						if (nextName.matches(rootMatch)) {
							if (!propRenameLin) {

							} else if (thisT <= rois2[r2].getTPosition()/* || selRois[0] == rois2[r2] */) {
								nameMatchIndexArrayList.add(r2);
								String nameReplacement = nextName.replaceAll("\""
										+ propCandidateName.replace("\"", "").trim() + "([m|n|l|r|a|p|d|v|g|h]*) +\".*",
										thisSwapString + "$1");
								nameReplacementArrayList.add(nameReplacement);
							}
						}
					} else {
						if (nextName.matches("(\"?)" + propCandidateName.replace("\"", "") + "(\"?).*")) {
							nameMatchIndexArrayList.add(r2);
							nameReplacementArrayList.add(nextName.replaceAll(
									"(\"?)" + propCandidateName.replace("\"", "") + "(\"?)(.*)", thisSwapString));
						}
					}
				}

				propCandidateName = thatTargetString;
				rois2 = getFullRoisAsArray();
				fraaa = rois2.length;
				for (int r2 = 0; r2 < fraaa; r2++) {
					String nextName = rois2[r2].getName();
					if (!propCandidateName.replace("\"", "").trim().equals("")) {
						String rootMatch = "\"" + propCandidateName.replace("\"", "").trim()
								+ (propRenameLin ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
						if (nextName.matches(rootMatch)) {
							if (!propRenameLin) {

							} else if (thisT <= rois2[r2].getTPosition()/* || selRois[0] == rois2[r2] */) {
								nameMatchIndexArrayList.add(r2);
								String nameReplacement = nextName.replaceAll("\""
										+ propCandidateName.replace("\"", "").trim() + "([m|n|l|r|a|p|d|v|g|h]*) +\".*",
										thatSwapString + "$1");
								nameReplacementArrayList.add(nameReplacement);
							}
						}
					} else {
						if (nextName.matches("(\"?)" + propCandidateName.replace("\"", "") + "(\"?).*")) {
							nameMatchIndexArrayList.add(r2);
							nameReplacementArrayList.add(nextName.replaceAll(
									"(\"?)" + propCandidateName.replace("\"", "") + "(\"?)(.*)", thatSwapString));
						}
					}
				}

			}
			int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
			String[] newNames = new String[nameMatchIndexArrayList.size()];
			for (int n = 0; n < nameMatchIndexes.length; n++) {
				nameMatchIndexes[n] = nameMatchIndexArrayList.get(n);
				newNames[n] = nameReplacementArrayList.get(n);
			}
			if (rename(newNames, nameMatchIndexes, false)) {
			}
		}

		for (int l = 0; l < maxLength; l++) {
			nameMatchIndexArrayList = new ArrayList<Integer>();
			nameReplacementArrayList = new ArrayList<String>();
			for (int pIteration = 2; pIteration <= 4; pIteration++) {
				String[] roiNameKeysTwo = getRoisByRootName().keySet()
						.toArray(new String[getRoisByRootName().keySet().size()]);
				Arrays.sort(roiNameKeysTwo);

				for (String rootName : roiNameKeysTwo) {
					ArrayList<Roi> theseRois = getRoisByRootName().get(rootName);

					String thisName = rootName;
					String thatName = "";
					if (theseRois == null || theseRois.size() == 0)
						continue;
					Roi thisRoi = theseRois.get(0);
					int thisX = (int) thisRoi.getBounds().getCenterX();
					int thisZ = thisRoi.getZPosition();
					int thisT = thisRoi.getTPosition();
					if (!thisName.matches("\"P" + pIteration + ". \"")) {
						continue;
					}
					if (thisName.endsWith("m \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "n \"";
					} else if (thisName.endsWith("n \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "m \"";
					} else if (thisName.endsWith("a \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "p \"";
					} else if (thisName.endsWith("p \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "a \"";
					} else if (thisName.endsWith("l \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "r \"";
					} else if (thisName.endsWith("r \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "l \"";
					} else if (thisName.endsWith("g \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "h \"";
					} else if (thisName.endsWith("h \"")) {
						thatName = thisName.substring(0, thisName.length() - 3) + "g \"";
					} else {
						continue;
					}
					ArrayList<Roi> thoseRois = getRoisByRootName().get(thatName);
					if (thoseRois == null || thoseRois.size() == 0)
						continue;
					Roi thatRoi = thoseRois.get(0);

					int thatX = (int) thatRoi.getBounds().getCenterX();
					int thatZ = thatRoi.getZPosition();
					int thatT = thatRoi.getTPosition();
					if (thatT != thisT)
						continue;
					ArrayList<Integer> thisIndexesArrayList = new ArrayList<Integer>();
					ArrayList<Integer> thatIndexesArrayList = new ArrayList<Integer>();
					ArrayList<Roi> theseAlreadyHit = new ArrayList<Roi>();
					ArrayList<Roi> thoseAlreadyHit = new ArrayList<Roi>();
					int thisHitCount = 0;
					int thatHitCount = 0;
					for (int r = 0; r < fullROIs.length; r++) {
						if (fullROIs[r] != null) {
							if (theseRois.contains(fullROIs[r])) {
								if (!theseAlreadyHit.contains(fullROIs[r])) {
									thisIndexesArrayList.add(r);
									theseAlreadyHit.add(fullROIs[r]);
									thisHitCount++;
								} else
									IJ.wait(1);
							} else if (thoseRois.contains(fullROIs[r])) {
								if (!thoseAlreadyHit.contains(fullROIs[r])) {
									thatIndexesArrayList.add(r);
									thoseAlreadyHit.add(fullROIs[r]);
									thatHitCount++;
								} else
									IJ.wait(1);
							}
						}
					}
					int[] thisIndexesArray = new int[thisIndexesArrayList.size()];
					for (int t = 0; t < thisIndexesArray.length; t++) {
						thisIndexesArray[t] = thisIndexesArrayList.get(t);
					}
					int[] thatIndexesArray = new int[thatIndexesArrayList.size()];
					for (int t = 0; t < thatIndexesArray.length; t++) {
						thatIndexesArray[t] = thatIndexesArrayList.get(t);
					}

					String thisTargetString = thisName.replace("\"", "").split(" ")[0].trim();
					String thatTargetString = thatName.replace("\"", "").split(" ")[0].trim();
					String thisSwapString = "";
					String thatSwapString = "";

					String momName = thisTargetString.substring(0, thisTargetString.length() - 1);
					if (momName.matches("(P2|P3|P4)")) {
						// if (momName.equals("P4"))
						// IJ.wait(1);
						if (thisX < thatX) {
							thisSwapString = thisTargetString.replace(thisTargetString,
									momName.equals("P2") ? "C" : (momName.equals("P3") ? "D" : "Z2"));
							thatSwapString = thatTargetString.replace(thatTargetString,
									momName.equals("P2") ? "P3" : (momName.equals("P3") ? "P4" : "Z3"));
						} else {
							thisSwapString = thisTargetString.replace(thisTargetString,
									momName.equals("P2") ? "P3" : (momName.equals("P3") ? "P4" : "Z3"));
							thatSwapString = thatTargetString.replace(thatTargetString,
									momName.equals("P2") ? "C" : (momName.equals("P3") ? "D" : "Z2"));
						}
						IJ.log(momName + " " + thisTargetString + "->" + thisSwapString + " " + thatTargetString + "->"
								+ thatSwapString);
						// rename(thisSwapString, thisIndexesArray,false);
						// rename(thatSwapString, thatIndexesArray,false);
					} else if (thisTargetString.endsWith("n \"") || thisTargetString.endsWith("m \"")
							|| thisTargetString.endsWith("g \"") || thisTargetString.endsWith("h \"")) {
						if (thisTargetString.matches("Enn") || thisTargetString.matches("Enm")) {
							IJ.wait(1);
						}
						if (thisX < thatX) {
							thisSwapString = thisTargetString.replace(thisTargetString,
									thisTargetString.substring(0, thisTargetString.length() - 1) + "a");
							thatSwapString = thatTargetString.replace(thatTargetString,
									thatTargetString.substring(0, thatTargetString.length() - 1) + "p");
						} else {
							thisSwapString = thisTargetString.replace(thisTargetString,
									thisTargetString.substring(0, thisTargetString.length() - 1) + "p");
							thatSwapString = thatTargetString.replace(thatTargetString,
									thatTargetString.substring(0, thatTargetString.length() - 1) + "a");
						}
					}

					boolean propRenameLin = true;
					String propCandidateName = thisTargetString;
					Roi[] rois2 = getFullRoisAsArray();
					int fraaa = rois2.length;
					for (int r2 = 0; r2 < fraaa; r2++) {
						String nextName = rois2[r2].getName();
						if (!propCandidateName.replace("\"", "").trim().equals("")) {
							String rootMatch = "\"" + propCandidateName.replace("\"", "").trim()
									+ (propRenameLin ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
							if (nextName.matches(rootMatch)) {
								if (!propRenameLin) {

								} else if (thisT <= rois2[r2].getTPosition()/* || selRois[0] == rois2[r2] */) {
									nameMatchIndexArrayList.add(r2);
									String nameReplacement = nextName
											.replaceAll("\"" + propCandidateName.replace("\"", "").trim()
													+ "([m|n|l|r|a|p|d|v|g|h]*) +\".*", thisSwapString + "$1");
									nameReplacementArrayList.add(nameReplacement);
								}
							}
						} else {
							if (nextName.matches("(\"?)" + propCandidateName.replace("\"", "") + "(\"?).*")) {
								nameMatchIndexArrayList.add(r2);
								nameReplacementArrayList.add(nextName.replaceAll(
										"(\"?)" + propCandidateName.replace("\"", "") + "(\"?)(.*)", thisSwapString));
							}
						}
					}

					propCandidateName = thatTargetString;
					rois2 = getFullRoisAsArray();
					fraaa = rois2.length;
					for (int r2 = 0; r2 < fraaa; r2++) {
						String nextName = rois2[r2].getName();
						if (!propCandidateName.replace("\"", "").trim().equals("")) {
							String rootMatch = "\"" + propCandidateName.replace("\"", "").trim()
									+ (propRenameLin ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
							if (nextName.matches(rootMatch)) {
								if (!propRenameLin) {

								} else if (thisT <= rois2[r2].getTPosition()/* || selRois[0] == rois2[r2] */) {
									nameMatchIndexArrayList.add(r2);
									String nameReplacement = nextName
											.replaceAll("\"" + propCandidateName.replace("\"", "").trim()
													+ "([m|n|l|r|a|p|d|v|g|h]*) +\".*", thatSwapString + "$1");
									nameReplacementArrayList.add(nameReplacement);
								}
							}
						} else {
							if (nextName.matches("(\"?)" + propCandidateName.replace("\"", "") + "(\"?).*")) {
								nameMatchIndexArrayList.add(r2);
								nameReplacementArrayList.add(nextName.replaceAll(
										"(\"?)" + propCandidateName.replace("\"", "") + "(\"?)(.*)", thatSwapString));
							}
						}
					}
					int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
					String[] newNames = new String[nameMatchIndexArrayList.size()];
					for (int n = 0; n < nameMatchIndexes.length; n++) {
						nameMatchIndexes[n] = nameMatchIndexArrayList.get(n);
						newNames[n] = nameReplacementArrayList.get(n);
					}
					if (rename(newNames, nameMatchIndexes, false)) {
					}

				}
			}
		}

		for (int l = 0; l < 1; l++) {
			String[] roiNameKeysThree = getRoisByRootName().keySet()
					.toArray(new String[getRoisByRootName().keySet().size()]);
			Arrays.sort(roiNameKeysThree);
			String[] roiFlippedKeysThree = new String[roiNameKeysThree.length];
			for (int k = 0; k < roiNameKeysThree.length; k++) {
				roiFlippedKeysThree[roiNameKeysThree.length - 1 - k] = roiNameKeysThree[k];
			}
			roiNameKeysThree = roiFlippedKeysThree;
			nameMatchIndexArrayList = new ArrayList<Integer>();
			nameReplacementArrayList = new ArrayList<String>();

			for (String rootName : roiNameKeysThree) {
				ArrayList<Roi> theseRois = getRoisByRootName().get(rootName);

				String thisName = rootName;
				String thatName = "";
				if (theseRois == null || theseRois.size() == 0)
					continue;
				Roi thisRoi = theseRois.get(0);
				int thisX = (int) thisRoi.getBounds().getCenterX();
				int thisZ = thisRoi.getZPosition();
				int thisT = thisRoi.getTPosition();
				// if (thisName.startsWith("\"E"))
				// IJ.wait(1);
				if (!thisName.matches("\"E.(m|n|a|p|g|h|l|r) \"") && !thisName.matches("\"AB.(m|n|a|p|g|h|l|r) \"")) {
					continue;
				}
				if (thisName.startsWith("\"ABap"))
					IJ.wait(1);
				if (thisName.endsWith("m \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "n \"";
				} else if (thisName.endsWith("n \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "m \"";
				} else if (thisName.endsWith("a \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "p \"";
				} else if (thisName.endsWith("p \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "a \"";
				} else if (thisName.endsWith("l \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "r \"";
				} else if (thisName.endsWith("r \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "l \"";
				} else if (thisName.endsWith("g \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "h \"";
				} else if (thisName.endsWith("h \"")) {
					thatName = thisName.substring(0, thisName.length() - 3) + "g \"";
				} else {
					continue;
				}
				ArrayList<Roi> thoseRois = getRoisByRootName().get(thatName);
				if (thoseRois == null) {
					continue;
				}
				if (thoseRois == null || thoseRois.size() == 0)
					continue;
				Roi thatRoi = thoseRois.get(0);

				int thatX = (int) thatRoi.getBounds().getCenterX();
				int thatZ = thatRoi.getZPosition();
				int thatT = thatRoi.getTPosition();
				if (thatT != thisT)
					continue;
				ArrayList<Integer> thisIndexesArrayList = new ArrayList<Integer>();
				ArrayList<Integer> thatIndexesArrayList = new ArrayList<Integer>();
				ArrayList<Roi> theseAlreadyHit = new ArrayList<Roi>();
				ArrayList<Roi> thoseAlreadyHit = new ArrayList<Roi>();
				int thisHitCount = 0;
				int thatHitCount = 0;
				for (int r = 0; r < fullROIs.length; r++) {
					if (fullROIs[r] != null) {
						if (theseRois.contains(fullROIs[r])) {
							if (!theseAlreadyHit.contains(fullROIs[r])) {
								thisIndexesArrayList.add(r);
								theseAlreadyHit.add(fullROIs[r]);
								thisHitCount++;
							} else
								IJ.wait(1);
						} else if (thoseRois.contains(fullROIs[r])) {
							if (!thoseAlreadyHit.contains(fullROIs[r])) {
								thatIndexesArrayList.add(r);
								thoseAlreadyHit.add(fullROIs[r]);
								thatHitCount++;
							} else
								IJ.wait(1);
						}
					}
				}
				int[] thisIndexesArray = new int[thisIndexesArrayList.size()];
				for (int t = 0; t < thisIndexesArray.length; t++) {
					thisIndexesArray[t] = thisIndexesArrayList.get(t);
				}
				int[] thatIndexesArray = new int[thatIndexesArrayList.size()];
				for (int t = 0; t < thatIndexesArray.length; t++) {
					thatIndexesArray[t] = thatIndexesArrayList.get(t);
				}

				String thisTargetString = thisName.replace("\"", "").split(" ")[0].trim();
				String thatTargetString = thatName.replace("\"", "").split(" ")[0].trim();
				String thisSwapString = "";
				String thatSwapString = "";

				if (thisTargetString.matches("(E.(m|n|a|p|g|h|l|r))|(AB.(m|n|a|p|g|h|l|r))")) {
					if (thisName.startsWith("\"ABap"))
						IJ.wait(1);
					if (thisZ < thatZ) {
						thisSwapString = thisTargetString.replace(thisTargetString,
								thisTargetString.substring(0, thisTargetString.length() - 1) + "l");
						thatSwapString = thatTargetString.replace(thatTargetString,
								thatTargetString.substring(0, thatTargetString.length() - 1) + "r");
					} else {
						thisSwapString = thisTargetString.replace(thisTargetString,
								thisTargetString.substring(0, thisTargetString.length() - 1) + "r");
						thatSwapString = thatTargetString.replace(thatTargetString,
								thatTargetString.substring(0, thatTargetString.length() - 1) + "l");
					}
					IJ.log(thisTargetString + "->" + thisSwapString + " " + thatTargetString + "->" + thatSwapString);
				}

				boolean propRenameLin = true;
				String propCandidateName = thisTargetString;
				Roi[] rois2 = getFullRoisAsArray();
				int fraaa = rois2.length;
				for (int r2 = 0; r2 < fraaa; r2++) {
					String nextName = rois2[r2].getName();
					if (!propCandidateName.replace("\"", "").trim().equals("")) {
						String rootMatch = "\"" + propCandidateName.replace("\"", "").trim()
								+ (propRenameLin ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
						if (nextName.matches(rootMatch)) {
							if (!propRenameLin) {

							} else if (thisT <= rois2[r2].getTPosition()/* || selRois[0] == rois2[r2] */) {
								nameMatchIndexArrayList.add(r2);
								String nameReplacement = nextName.replaceAll("\""
										+ propCandidateName.replace("\"", "").trim() + "([m|n|l|r|a|p|d|v|g|h]*) +\".*",
										thisSwapString + "$1");
								nameReplacementArrayList.add(nameReplacement);
							}
						}
					} else {
						if (nextName.matches("(\"?)" + propCandidateName.replace("\"", "") + "(\"?).*")) {
							nameMatchIndexArrayList.add(r2);
							nameReplacementArrayList.add(nextName.replaceAll(
									"(\"?)" + propCandidateName.replace("\"", "") + "(\"?)(.*)", thisSwapString));
						}
					}
				}

				propCandidateName = thisTargetString;
				rois2 = getFullRoisAsArray();
				fraaa = rois2.length;
				for (int r2 = 0; r2 < fraaa; r2++) {
					String nextName = rois2[r2].getName();
					if (!propCandidateName.replace("\"", "").trim().equals("")) {
						String rootMatch = "\"" + propCandidateName.replace("\"", "").trim()
								+ (propRenameLin ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
						if (nextName.matches(rootMatch)) {
							if (!propRenameLin) {

							} else if (thisT <= rois2[r2].getTPosition()/* || selRois[0] == rois2[r2] */) {
								nameMatchIndexArrayList.add(r2);
								String nameReplacement = nextName.replaceAll("\""
										+ propCandidateName.replace("\"", "").trim() + "([m|n|l|r|a|p|d|v|g|h]*) +\".*",
										thisSwapString + "$1");
								nameReplacementArrayList.add(nameReplacement);
							}
						}
					} else {
						if (nextName.matches("(\"?)" + propCandidateName.replace("\"", "") + "(\"?).*")) {
							nameMatchIndexArrayList.add(r2);
							nameReplacementArrayList.add(nextName.replaceAll(
									"(\"?)" + propCandidateName.replace("\"", "") + "(\"?)(.*)", thisSwapString));
						}
					}
				}

			}
			int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
			String[] newNames = new String[nameMatchIndexArrayList.size()];
			for (int n = 0; n < nameMatchIndexes.length; n++) {
				nameMatchIndexes[n] = nameMatchIndexArrayList.get(n);
				newNames[n] = nameReplacementArrayList.get(n);
			}
			if (rename(newNames, nameMatchIndexes, false)) {
			}
		}
		IJ.log("Sibling pair resolution complete");
	}

	private void sketchVolumeViewer(Object source) {
		boolean singleSave = IJ.shiftKeyDown();
		double scaleFactor = IJ.getNumber("Downscale for faster rendering?", 1.0d);
		double zPadFactor = 3;
		IJ.setForegroundColor(255, 255, 255);
		IJ.setBackgroundColor(0, 0, 0);
		if (getSelectedRoisAsArray().length < 1)
			return;
		ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
		ArrayList<String> rootNames = new ArrayList<String>();
		String roiColorString = Colors.colorToHexString(this.getSelectedRoisAsArray()[0].getFillColor());
//		roiColorString = roiColorString.substring(3 /*roiColorString.length()-6*/);
		String assignedColorString = roiColorString;

		String outDir = IJ.getDirectory("Choose Location to Save Output OBJ/MTL Files...");
		outDir = outDir + (lastRoiOpenPath != null
				? (new File(lastRoiOpenPath).getName().replace(".zip", "") + "_SVV_SingleOBJs")
				: "SVV_RenderedROIs_SingleOBJs");
		new File(outDir).mkdirs();

		for (Roi selRoi : getSelectedRoisAsArray()) {
			String rootName = selRoi.getName().contains("\"") ? selRoi.getName().split("\"")[1].trim() : "";
			rootName = rootName.contains(" ") ? rootName.split("[_\\- ]")[0].trim() : rootName;
			String[] rootChunks = selRoi.getName().split("_");
			String rootFrame = rootChunks[rootChunks.length - 1].replaceAll("[CZT]", "").split("-")[0];
			if (!rootNames_rootFrames.contains(rootName + "_" + rootFrame)) {
				rootNames_rootFrames.add(rootName + "_" + rootFrame);
				rootNames.add(rootName);
			}
		}

		MQTVS_VolumeViewer vv = new MQTVS_VolumeViewer();
		for (int n = 0; n < rootNames_rootFrames.size(); n++) {
			ImagePlus sketchImp = null;

			String rootName = rootNames.get(n);

			select(-1);
			IJ.wait(50);
			ArrayList<Integer> nameMatchIndexArrayList = new ArrayList<Integer>();
			Roi[] rois = getFullRoisAsArray();
			int fraa = rois.length;
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxY = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int r = 0; r < fraa; r++) {
				String nextName = rois[r].getName();
				if (nextName.startsWith("\"" + rootName/* .split("_")[0] */)
				/*
				 * && rootName.endsWith(nextName.split("_")[nextName.split("_").length-1].
				 * replaceAll("[CZT]", "").split("-")[0])
				 */
				) {
					nameMatchIndexArrayList.add(r);
					minX = minX > rois[r].getBounds().x ? rois[r].getBounds().x : minX;
					minY = minY > rois[r].getBounds().y ? rois[r].getBounds().y : minY;
					minZ = minZ > rois[r].getZPosition() - 1 ? rois[r].getZPosition() - 1 : minZ; // minZ = 0 for full
																									// stack...
					maxX = maxX < rois[r].getBounds().x + rois[r].getBounds().width
							? rois[r].getBounds().x + rois[r].getBounds().width
							: maxX;
					maxY = maxY < rois[r].getBounds().y + rois[r].getBounds().height
							? rois[r].getBounds().y + rois[r].getBounds().height
							: maxY;
					maxZ = maxZ < rois[r].getZPosition() ? rois[r].getZPosition() : maxZ;

				}
			}
			IJ.log(rootName + " " + minX + " " + minY + " " + minZ + " " + maxX + " " + maxY + " " + maxZ);
//			sketchImp = NewImage.createImage("SVV_"+rootNames_rootFrames.get(0),(int)(imp.getWidth()*scaleFactor), (int)(imp.getHeight()*scaleFactor), (int)(imp.getNSlices()*imp.getNFrames()*zPadFactor), 8, NewImage.FILL_BLACK, false);
			sketchImp = NewImage.createImage("SVV_" + rootNames_rootFrames.get(0),
					(int) ((maxX - minX) * scaleFactor) + 20, (int) ((maxY - minY) * scaleFactor) + 20,
					(int) ((maxZ - minZ) * zPadFactor) + 2, 8, NewImage.FILL_BLACK, false);
			sketchImp.setDimensions(1, (int) ((maxZ - minZ) * zPadFactor) + 2, imp.getNFrames());
			sketchImp.setMotherImp(imp, imp.getID());
			sketchImp.setCalibration(imp.getCalibration());
			sketchImp.getCalibration().pixelWidth = imp.getCalibration().pixelWidth / scaleFactor;
			sketchImp.getCalibration().pixelHeight = imp.getCalibration().pixelHeight / scaleFactor;
			sketchImp.getCalibration().pixelDepth = imp.getCalibration().pixelDepth / zPadFactor;

			sketchImp.setTitle("SVV_" + rootName);
//			sketchImp.show();
			sketchImp.getRoiManager().select(-1);
			IJ.wait(50);
			if (sketchImp.getRoiManager().getCount() > 0) {
				sketchImp.getRoiManager().delete(false);
			}
			int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
			Roi nextRoi = ((Roi) rois[0]);
			for (int i = 0; i < nameMatchIndexes.length; i++) {
				nameMatchIndexes[i] = nameMatchIndexArrayList.get(i);
				nextRoi = ((Roi) rois[nameMatchIndexArrayList.get(i)]);
				String[] nextChunks = nextRoi.getName().split("_");
				int nextSlice = ((Integer.parseInt(nextChunks[nextChunks.length - 2]) - minZ) * (int) zPadFactor);
				int nextFrame = Integer
						.parseInt(nextChunks[nextChunks.length - 1].replaceAll("[CZT]", "").split("-")[0]);
				nextRoi.setLocation(nextRoi.getBounds().x - minX + 10, nextRoi.getBounds().y - minY + 10);
				Roi scaledRoi = null;
				try {

					if (!nextRoi.isArea()) {
						ImageProcessor ip2 = new ByteProcessor(imp.getWidth(), imp.getHeight());
						ip2.setColor(255);
						if (nextRoi.getType() == Roi.LINE) {
							if (!(nextRoi.getStrokeWidth() > 1)) {
								nextRoi.setStrokeWidth(2d);
							}
							ip2.fillPolygon(nextRoi.getPolygon());
						} else {
							(nextRoi).drawPixels(ip2);
						}
						ip2.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
						ThresholdToSelection tts = new ThresholdToSelection();
						Roi roi2 = tts.convert(ip2);
						Selection.transferProperties(nextRoi, roi2);
						nextRoi = roi2;

					}

					if (nextRoi instanceof TextRoi) {
						scaledRoi = (Roi) nextRoi.clone();
					} else {
						scaledRoi = new RoiDecoder(scaleFactor, RoiEncoder.saveAsByteArray(nextRoi), nextRoi.getName())
								.getRoi();
					}
					nextRoi.setLocation(nextRoi.getBounds().x + minX - 10, nextRoi.getBounds().y + minY - 10);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				for (int zp = 0; zp < (int) zPadFactor; zp++) {
//					sketchImp.setPosition(1, nextSlice-zp, nextFrame);  ***
					scaledRoi.setPosition(1, nextSlice - zp, nextFrame);
					sketchImp.getRoiManager().addRoi(scaledRoi, false, scaledRoi.getStrokeColor(), scaledRoi.getFillColor(), -1, false);
				}
			}
			sketchImp.getRoiManager().select(-1);
			boolean filled = false;
			filled = sketchImp.getRoiManager().drawOrFill(nextRoi instanceof TextRoi ? DRAW : FILL);

			while (!filled) {
				IJ.wait(100);
			}
			sketchImp.setMotherImp(imp, imp.getID());
			sketchImp.getRoiManager().setSelectedIndexes(sketchImp.getRoiManager().getFullListIndexes());
			roiColorString = Colors.colorToHexString(nextRoi.getFillColor());
//			roiColorString = roiColorString.substring(3 /*roiColorString.length()-6*/);
			assignedColorString = roiColorString;
			(new StackReverser()).flipStack(sketchImp);
			sketchImp.setMotherImp(imp, imp.getID());
//			sketchImp.show();
			String scaleShiftString = "" + 1.0 + "|" + 1.0 + "|" + 1.0 + "|"
					+ (minX - 10) * sketchImp.getCalibration().pixelWidth * scaleFactor + "|"
					+ (minY - 10) * sketchImp.getCalibration().pixelHeight * scaleFactor + "|"
					/* maxZ b\c stackflip */ + (-maxZ - 1) * sketchImp.getCalibration().pixelDepth * zPadFactor;
			vv.runVolumeViewer(sketchImp, rootName, assignedColorString, true, null, outDir, scaleShiftString);
//			new ObjEditor().run("1.0|1.0|1.0|"+(minX-10)*sketchImp.getCalibration().pixelWidth*scaleFactor+"|"
//											  +(minY-10)*sketchImp.getCalibration().pixelHeight*scaleFactor+"|"
//											  +(-maxZ-1)*sketchImp.getCalibration().pixelDepth*zPadFactor+"|"
//											  +outDir+File.separator+"SVV_"+rootName+"_"+rootName+"_1_1_0000.obj"+"|"
//											  +outDir+File.separator);
			IJ.log("" + 1.0 + "|" + 1.0 + "|" + 1.0 + "|" + (minX - 10) * sketchImp.getCalibration().pixelWidth + "|"
					+ (minY - 10) * sketchImp.getCalibration().pixelHeight + "|"
					/* maxZ b\c stackflip */ + (-maxZ - 1) * sketchImp.getCalibration().pixelDepth * zPadFactor + "|"
					+ outDir + File.separator + "SVV_" + rootName + "_" + rootName + "_1_1_0000.obj" + "|" + outDir
					+ File.separator);
			sketchImp.changes = false;
			sketchImp.close();
			sketchImp.flush();
			sketchImp = null;
			ImageJ3DViewer.select(null);

//			THIS GARBAGECOLLECTOR CALL IS ABSOLUTELY REQUIRED FOR BIG DATA!!!!
			System.gc();
		}
		Image3DUniverse univ = vv.getUniv();

		Hashtable<String, Content> contents = univ.getContentsHT();

		for (Object content : contents.values()) {
			((Content) content).setLocked(true);
			((Content) content).setVisible(true);
		}

	}

	private void sketchVolumeMeasurer(Object source) {
		boolean singleSave = IJ.shiftKeyDown();
		double scaleFactor = 1.0;
		double zPadFactor = 1;
		IJ.setForegroundColor(255, 255, 255);
		IJ.setBackgroundColor(0, 0, 0);
		if (getSelectedRoisAsArray().length < 1)
			return;
		ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
		ArrayList<String> rootNames = new ArrayList<String>();
		String roiColorString = Colors.colorToHexString(this.getSelectedRoisAsArray()[0].getFillColor());
//		roiColorString = roiColorString.substring(3 /*roiColorString.length()-6*/);
		String assignedColorString = roiColorString;

		for (Roi selRoi : getSelectedRoisAsArray()) {
			String rootName = selRoi.getName().contains("\"") ? selRoi.getName().split("\"")[1].trim() : "";
			rootName = rootName.contains(" ") ? rootName.split("[_\\- ]")[0].trim() : rootName;
			String[] rootChunks = selRoi.getName().split("_");
			String rootFrame = rootChunks[rootChunks.length - 1].replaceAll("[CZT]", "").split("-")[0];
			if (!rootNames_rootFrames.contains(rootName + "_" + rootFrame)) {
				rootNames_rootFrames.add(rootName + "_" + rootFrame);
				rootNames.add(rootName);
			}
		}
		ImageProcessor dummyIP = new ByteProcessor(imp.getWidth(), imp.getHeight());
		
		Roi[] rois = getSelectedRoisAsArray();  //just changed this 07062022
		
		for (int n = 0; n < rootNames_rootFrames.size(); n++) {
			String rootName = rootNames.get(n);

			select(-1);
			IJ.wait(50);
			ArrayList<Double> nameMatchROIareaArrayList = new ArrayList<Double>();
			ArrayList<Double> nameMatchROIperimArrayList = new ArrayList<Double>();
			double sumAreas = 0;
			double sumPerims = 0;
			int sraa = rois.length;
			for (int r = 0; r < sraa; r++) {
				
				String nextName = rois[r].getName();
				if (nextName.startsWith("\"" + rootName + " \"" /* .split("_")[0] */)){
					if (true/*rois[r] instanceof ShapeRoi*/) {
						ShapeRoi shroi = new ShapeRoi(rois[r]);
						shroi.setImage(rois[r].getImage());

						dummyIP.setRoi(shroi);
						nameMatchROIareaArrayList.add(new ByteStatistics(dummyIP, ImageStatistics.AREA, imp.getCalibration()).area);
						sumAreas += nameMatchROIareaArrayList.get(nameMatchROIareaArrayList.size() -1);
						double perim = shroi.getLength();
						nameMatchROIperimArrayList.add(perim);
						sumPerims += nameMatchROIperimArrayList.get(nameMatchROIperimArrayList.size() -1);
					}
				}
			}
			double totalVolume = sumAreas * imp.getCalibration().pixelDepth;
			double totalSA = sumPerims * imp.getCalibration().pixelDepth;
			IJ.log(""+rootName+","+String.format("%.2f",totalVolume)+","+String.format("%.2f",totalSA));
		}
	}

	private void sketchVolumeSlicer(Object source) {
		boolean singleSave = IJ.shiftKeyDown();
		double scaleFactor = 1.0;
		double zPadFactor = 1;
		IJ.setForegroundColor(255, 255, 255);
		IJ.setBackgroundColor(0, 0, 0);
		if (getSelectedRoisAsArray().length < 1)
			return;
		ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
		ArrayList<String> rootNames = new ArrayList<String>();
		String roiColorString = Colors.colorToHexString(this.getSelectedRoisAsArray()[0].getFillColor());
//		roiColorString = roiColorString.substring(3 /*roiColorString.length()-6*/);
		String assignedColorString = roiColorString;

		for (Roi selRoi : getSelectedRoisAsArray()) {
			String rootName = selRoi.getName().contains("\"") ? selRoi.getName().split("\"")[1].trim() : "";
			rootName = rootName.contains(" ") ? rootName.split("[_\\- ]")[0].trim() : rootName;
			String[] rootChunks = selRoi.getName().split("_");
			String rootFrame = rootChunks[rootChunks.length - 1].replaceAll("[CZT]", "").split("-")[0];
			if (!rootNames_rootFrames.contains(rootName + "_" + rootFrame)) {
				rootNames_rootFrames.add(rootName + "_" + rootFrame);
				rootNames.add(rootName);
			}
		}

		ImagePlus impBuildTagSet = NewImage.createImage("SVV_" + rootNames_rootFrames.get(0)+"_virtualResliceStackForROIs",
				(int)(imp.getNSlices()*imp.getCalibration().pixelDepth/imp.getCalibration().pixelWidth), imp.getHeight(),
				imp.getWidth(), 8, NewImage.FILL_BLACK, true);

		String roiSaveDir = IJ.getDirectory("Save ROIs here...")+File.separator+"RoisSingly"+File.separator;
		new File(roiSaveDir).mkdirs();
		ArrayList<String> namesAlreadySaved = new ArrayList<String>();
		
		for (int n = 0; n < rootNames_rootFrames.size(); n++) {
			ImagePlus sketchImp = null;

			String rootName = rootNames.get(n);

			select(-1);
			IJ.wait(50);
			ArrayList<Integer> nameMatchIndexArrayList = new ArrayList<Integer>();
			Roi[] rois = getFullRoisAsArray();
			int fraa = rois.length;
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxY = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (int r = 0; r < fraa; r++) {
				String nextName = rois[r].getName();
				if (nextName.startsWith("\"" + rootName/* .split("_")[0] */)
				/*
				 * && rootName.endsWith(nextName.split("_")[nextName.split("_").length-1].
				 * replaceAll("[CZT]", "").split("-")[0])
				 */
				) {
					nameMatchIndexArrayList.add(r);
					minX = minX > rois[r].getBounds().x ? rois[r].getBounds().x : minX;
					minY = minY > rois[r].getBounds().y ? rois[r].getBounds().y : minY;
					minZ = minZ > rois[r].getZPosition() - 1 ? rois[r].getZPosition() - 1 : minZ; // minZ = 0 for full
																									// stack...
					maxX = maxX < rois[r].getBounds().x + rois[r].getBounds().width
							? rois[r].getBounds().x + rois[r].getBounds().width
							: maxX;
					maxY = maxY < rois[r].getBounds().y + rois[r].getBounds().height
							? rois[r].getBounds().y + rois[r].getBounds().height
							: maxY;
					maxZ = maxZ < rois[r].getZPosition() ? rois[r].getZPosition() : maxZ;

				}
			}
			IJ.log(rootName + " " + minX + " " + minY + " " + minZ + " " + maxX + " " + maxY + " " + maxZ);
//			sketchImp = NewImage.createImage("SVV_"+rootNames_rootFrames.get(0),(int)(imp.getWidth()*scaleFactor), (int)(imp.getHeight()*scaleFactor), (int)(imp.getNSlices()*imp.getNFrames()*zPadFactor), 8, NewImage.FILL_BLACK, false);
			sketchImp = NewImage.createImage("SVV_" + rootNames_rootFrames.get(0),
					(int) ((maxX - minX) * scaleFactor) + 20, (int) ((maxY - minY) * scaleFactor) + 20,
					(int) ((maxZ - minZ) * zPadFactor) + 2, 8, NewImage.FILL_BLACK, false);
			sketchImp.setDimensions(1, (int) ((maxZ - minZ) * zPadFactor) + 2, imp.getNFrames());
			sketchImp.setMotherImp(imp, imp.getID());
			sketchImp.setCalibration(imp.getCalibration());
			sketchImp.getCalibration().pixelWidth = imp.getCalibration().pixelWidth / scaleFactor;
			sketchImp.getCalibration().pixelHeight = imp.getCalibration().pixelHeight / scaleFactor;
			sketchImp.getCalibration().pixelDepth = imp.getCalibration().pixelDepth / zPadFactor;

			sketchImp.setTitle("SVV_" + rootName);
			sketchImp.show();
			sketchImp.getRoiManager().select(-1);
			IJ.wait(50);
			if (sketchImp.getRoiManager().getCount() > 0) {
				sketchImp.getRoiManager().delete(false);
			}
			int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
			Roi nextRoi = ((Roi) rois[0]);
			for (int i = 0; i < nameMatchIndexes.length; i++) {
				nameMatchIndexes[i] = nameMatchIndexArrayList.get(i);
				nextRoi = ((Roi) rois[nameMatchIndexArrayList.get(i)]);
				String[] nextChunks = nextRoi.getName().split("_");
				int nextSlice = ((Integer.parseInt(nextChunks[nextChunks.length - 2]) - minZ) * (int) zPadFactor);
				int nextFrame = Integer
						.parseInt(nextChunks[nextChunks.length - 1].replaceAll("[CZT]", "").split("-")[0]);
				nextRoi.setLocation(nextRoi.getBounds().x - minX + 10, nextRoi.getBounds().y - minY + 10);
				Roi scaledRoi = null;
				try {

					if (!nextRoi.isArea()) {
						ImageProcessor ip2 = new ByteProcessor(imp.getWidth(), imp.getHeight());
						ip2.setColor(255);
						if (nextRoi.getType() == Roi.LINE) {
							if (!(nextRoi.getStrokeWidth() > 1)) {
								nextRoi.setStrokeWidth(2d);
							}
							ip2.fillPolygon(nextRoi.getPolygon());
						} else {
							(nextRoi).drawPixels(ip2);
						}
						ip2.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
						ThresholdToSelection tts = new ThresholdToSelection();
						Roi roi2 = tts.convert(ip2);
						Selection.transferProperties(nextRoi, roi2);
						nextRoi = roi2;

					}

					if (nextRoi instanceof TextRoi) {
						scaledRoi = (Roi) nextRoi.clone();
					} else {
						scaledRoi = new RoiDecoder(scaleFactor, RoiEncoder.saveAsByteArray(nextRoi), nextRoi.getName())
								.getRoi();
					}
					nextRoi.setLocation(nextRoi.getBounds().x + minX - 10, nextRoi.getBounds().y + minY - 10);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				for (int zp = 0; zp < (int) zPadFactor; zp++) {
//					sketchImp.setPosition(1, nextSlice-zp, nextFrame);  ***
					scaledRoi.setPosition(1, nextSlice - zp, nextFrame);
					sketchImp.getRoiManager().addRoi(scaledRoi, false, scaledRoi.getStrokeColor(), scaledRoi.getFillColor(), -1, false);
				}
			}
			sketchImp.getRoiManager().select(-1);
			boolean filled = false;
			filled = sketchImp.getRoiManager().drawOrFill(nextRoi instanceof TextRoi ? DRAW : FILL);

			while (!filled) {
				IJ.wait(100);
			}
			sketchImp.setMotherImp(imp, imp.getID());
			sketchImp.getRoiManager().setSelectedIndexes(sketchImp.getRoiManager().getFullListIndexes());
			roiColorString = Colors.colorToHexString(nextRoi.getFillColor());
//			roiColorString = roiColorString.substring(3 /*roiColorString.length()-6*/);
			assignedColorString = roiColorString;
			(new StackReverser()).flipStack(sketchImp);
			sketchImp.setMotherImp(imp, imp.getID());

			String scaleShiftString = "" + 1.0 + "|" + 1.0 + "|" + 1.0 + "|"
					+ (minX - 10) * sketchImp.getCalibration().pixelWidth * scaleFactor + "|"
					+ (minY - 10) * sketchImp.getCalibration().pixelHeight * scaleFactor + "|"
					/* maxZ b\c stackflip */ + (-maxZ - 1) * sketchImp.getCalibration().pixelDepth * zPadFactor;
			
			sketchImp.setDimensions(1, (int) ((maxZ - minZ) * zPadFactor) + 2, imp.getNFrames());
			
			impBuildTagSet.show();
			Slicer.setStartAt("Left");
			Slicer.setRotate(true);
			int y1 = 0; 
			int y2 = sketchImp.getHeight()-1;
			for (int x=0; x<sketchImp.getWidth(); x++) {
				sketchImp.setRoi(new Line(x,y1,x,y2));
				Slicer sketchSlice = new Slicer();
				sketchSlice.setOutputSlices(1);
				ImagePlus resliceSketchImp = sketchSlice.reslice(sketchImp);
				resliceSketchImp.getProcessor().flipHorizontal();
//				resliceSketchImp.show();
				IJ.setThreshold(resliceSketchImp,2,255);
				ParticleAnalyzer pa = new ParticleAnalyzer(ParticleAnalyzer.ADD_TO_MANAGER,0,null,10,Double.MAX_VALUE,0,1);
				pa.analyze(resliceSketchImp);
				for (Roi nextNewRoi:resliceSketchImp.getRoiManager().getFullRoisAsArray()) {
					impBuildTagSet.setPosition(1, x + minX-(10+1), 1);
					nextNewRoi.setName(rootName);
					nextNewRoi.setLocation(nextNewRoi.getBounds().x+ minZ*sketchImp.getCalibration().pixelDepth/sketchImp.getCalibration().pixelWidth
											, nextNewRoi.getBounds().y + minY - 10);
					impBuildTagSet.getRoiManager().addRoi(nextNewRoi, false, ((Roi) rois[nameMatchIndexArrayList.get(0)]).getStrokeColor(), ((Roi) rois[nameMatchIndexArrayList.get(0)]).getFillColor(), 1, true);
					String nextNewRoiNameFromListModel = impBuildTagSet.getRoiManager().getListModel()
															.get(impBuildTagSet.getRoiManager().getListModel().size()-1);
					Roi nextNewRoiFromListModel = impBuildTagSet.getRoiManager().getROIs().get(nextNewRoiNameFromListModel);
					String label = nextNewRoiNameFromListModel;
					// Substituting updated color, Z, T, C info to saved name:

////NEEDed TO CHANGE THIS NEXT LINE TO TAKE ADVANTAGE OF GETUNIQUENAME HAVING BEEN CALLED ALREADY DURING .ADDROI 3 LINES AGO!!!!
//					String labelNew = label.replaceAll("(\".* \"_)(.*)", "$1" + Colors.colorToHexString(nextNewRoiFromListModel.getFillColor())
//							+ "_" + nextNewRoiFromListModel.getCPosition() + "_" + nextNewRoiFromListModel.getZPosition() + "_" + nextNewRoiFromListModel.getTPosition())
//							.replace(" ", "").replace("\"", "");
					String labelNew = label.replaceAll("(\".* \"_)(.*)", "$1" + Colors.colorToHexString(nextNewRoiFromListModel.getFillColor())
					+ "_$2" )
					.replace(" ", "").replace("\"", "");

					if (!labelNew.endsWith(".roi"))
						labelNew += ".roi";
					int hitCount = 0;
					while (namesAlreadySaved.contains(labelNew)) {
						hitCount++;
						labelNew = labelNew.replaceAll("(.*)(-[0-9]+)?(.roi)", "$1-" + hitCount + "$3");
					}
					namesAlreadySaved.add(labelNew);
					RoiEncoder re = new RoiEncoder(roiSaveDir + labelNew);
					try {
						re.write(nextNewRoiFromListModel);
					} catch (IOException e) {
						IJ.error("Tag Manager", e.getMessage());
					}
					
				}
				resliceSketchImp.close();
				resliceSketchImp.flush();

			}
			sketchImp.changes = false;
			sketchImp.close();
			sketchImp.flush();
			sketchImp = null;
//			ImageJ3DViewer.select(null);

//			THIS GARBAGECOLLECTOR CALL IS ABSOLUTELY REQUIRED FOR BIG DATA!!!!
			System.gc();
		}
//		Image3DUniverse univ = vv.getUniv();

//		Hashtable<String, Content> contents = univ.getContentsHT();

//		for (Object content : contents.values()) {
//			((Content) content).setLocked(true);
//			((Content) content).setVisible(true);
//		}

	}

	
	public void itemStateChanged(ItemEvent e) {
		Object source = e.getSource();
		if (source == addRoiSpanCCheckbox) {
			if (addRoiSpanCCheckbox.isSelected())
				addRoiSpanC = true;
			else
				addRoiSpanC = false;
			return;
		}
		if (source == addRoiSpanZCheckbox) {
			if (addRoiSpanZCheckbox.isSelected())
				addRoiSpanZ = true;
			else
				addRoiSpanZ = false;
			return;
		}
		if (source == addRoiSpanTCheckbox) {
			if (addRoiSpanTCheckbox.isSelected())
				addRoiSpanT = true;
			else
				addRoiSpanT = false;
			return;
		}
		// if (source==showOwnROIsCheckbox) {
		// showAll(showOwnROIsCheckbox.isSelected()?SHOW_OWN:(showAllCheckbox.isSelected()?SHOW_ALL:SHOW_NONE)
		// );
		// firstTime = false;
		// return;
		// }
		if (source == showAllCheckbox) {
			showAll(showAllCheckbox.isSelected() ? SHOW_ALL : SHOW_NONE);
			firstTime = false;
			return;
		}
		if (source == labelsCheckbox) {
			if (firstTime)
				showAllCheckbox.setSelected(true);
			boolean editState = labelsCheckbox.isSelected();
			boolean showAllState = showAllCheckbox.isSelected();
			if (!showAllState && !editState)
				showAll(SHOW_NONE);
			else {
				showAll(editState ? LABELS : NO_LABELS);
				if (editState)
					showAllCheckbox.setSelected(true);
			}
			firstTime = false;
			return;
		}
		if (e.getStateChange() == ItemEvent.SELECTED && !ignoreInterrupts) {
			int index = 0;
			// IJ.log("item="+e.getItem()+" shift="+IJ.shiftKeyDown()+" ctrl="+IJ.
			// controlKeyDown());
			try {
				index = Integer.parseInt(e.getItem().toString());
			} catch (NumberFormatException ex) {
			}
			if (index < 0)
				index = 0;
			if (!IJ.isMacintosh()) { // handle shift-click, ctrl-click (on Mac, OS takes care of this)
				if (!IJ.shiftKeyDown())
					lastNonShiftClick = index;
				if (!IJ.shiftKeyDown() && !IJ.controlKeyDown()) { // simple click, deselect everything else
					list.clearSelection();
					// int[] indexes = getSelectedIndexes();
					// for (int i=0; i<indexes.length; i++) {
					// if (indexes[i]!=index)
					// list.deselect(indexes[i]);
					// }
				} else if (IJ.shiftKeyDown() && lastNonShiftClick >= 0 && lastNonShiftClick < listModel.getSize()) {
					int firstIndex = Math.min(index, lastNonShiftClick);
					int lastIndex = Math.max(index, lastNonShiftClick);
					list.clearSelection();
					// int[] indexes = getSelectedIndexes();
					// for (int i=0; i<indexes.length; i++)
					// if (indexes[i]<firstIndex || indexes[i]>lastIndex)
					// list.deselect(indexes[i]); //deselect everything else
					for (int i = firstIndex; i <= lastIndex; i++)
						list.setSelectedIndex(i); // select range
				}
			}
			if (WindowManager.getCurrentImage() != null) {
				// restore(getImage(), index, true);
				if (list.getSelectedIndices().length <= 1) {
					// IJ.log("list.getSelectedIndexes <=1");
					restore(getImage(), index, true);
				}
				if (record()) {
					if (Recorder.scriptMode())
						Recorder.recordCall("rm.select(imp, " + index + ");");
					else
						Recorder.record("roiManager", "Select", index);
				}
			}
			list.revalidate();
			list.repaint();
		}
		this.revalidate();
		this.repaint();
	}

	void add(Roi roi, boolean shiftKeyDown, boolean altKeyDown, boolean controlKeyDown) {
		add(roi, shiftKeyDown, altKeyDown, controlKeyDown, false);
	}
	
	public void add(Roi roi, boolean shiftKeyDown, boolean altKeyDown, boolean controlKeyDown, boolean nextSlice) {
		if (controlKeyDown) {
			addRoiSpanC = true;
			addRoi(roi);
			addRoiSpanC = false;
		} else if (altKeyDown) {
			addRoiSpanZ = true;
			addRoi(roi);
			addRoiSpanZ = false;
		} else if (shiftKeyDown) {
			addRoiSpanT = true;
			addRoi(roi);
			addRoiSpanT = false;
		} else
			addRoi(roi);
	}

	/** Adds the specified ROI. */
	public void addRoi(Roi roi) {
		if (roi != null && roi.getFillColor() == null && imp!=null)  //  WHY THIS CHECK?
			roi.setFillColor(imp.getRoiFillColor());
		addRoi(roi, false, roi.getStrokeColor(), roi.getFillColor(), -1, true);
	}

	public boolean addRoi(boolean promptForName) {
		return addRoi(null, promptForName, null, null, -1, true);
	}

	public boolean addRoi(Roi roi, boolean promptForName, Color strokeColor, Color fillColor, int lineWidth, boolean addToCurrentImpPosition) {
		ImagePlus imp = this.imp;
		if (roi == null) {
			if (imp == null)
				return false;
			roi = imp.getRoi();
			if (roi == null) {
				error("The relevant image does not have a selection.");
				return false;
			}
		}
		if (strokeColor == null) { 
			if (roi.getStrokeColor() != null)
				strokeColor = roi.getStrokeColor();
			else if (strokeColor == null && imp!=null && imp.getRoiStrokeColor() != null)
				strokeColor = imp.getRoiStrokeColor();
		}

		if (fillColor == null) 
			if (imp != null)
				fillColor = imp.getRoiFillColor();
			if (fillColor == null) 
				fillColor = roi.getFillColor();
			if (fillColor == null) 
				fillColor = defaultColor;
		
		// IJ.log(""+imp.getRoiFillColor());
		if (lineWidth < 0) {
			int sw = (int) roi.getStrokeWidth();
			lineWidth = sw > 1 ? sw : defaultLineWidth;
		}
		if (lineWidth > 100)
			lineWidth = 1;
		int n = listModel.getSize();
		if (n > 0 && !IJ.isMacro() && imp != null && imp.getWindow() != null) {
			// check for duplicate
			String label = (String) listModel.getElementAt(n - 1);
			Roi roi2 = (Roi) rois.get(label);
			if (roi2 != null) {
				int slice2 = getSliceNumber(roi2, label);
				if (roi.equals(roi2) && (slice2 == -1 || slice2 == imp.getCurrentSlice()) && imp.getID() == prevID
						&& !Interpreter.isBatchMode())
					return false;
			}
		}
		prevID = imp != null ? imp.getID() : 0;
		String name = roi.getName();
		if (textFindingField != null) // true/*name == null*/)
//			if (!(textFindingField.getText().isEmpty()  || textFindingField.getText().contains("Name..."))) {
//				roi.setName(textFindingField.getText());
//				recentName = (textFindingField.getText());
//			}
			if (isStandardName(name))
				name = null;
		String label = name != null ? name : getLabel(imp, roi, -1);
		if (promptForName) {
			 label = promptForName(label);
		} else if (roi instanceof TextRoi)
			if (imp != null)
				label = (((TextRoi) roi).getText().indexOf("\n") > 0
						? ("\"" + ((TextRoi) roi).getText().replace("\n", " ") + "\"")
						: "Blank") + "_" + imp.getChannel() + "_" + imp.getSlice() + "_" + imp.getFrame();
			else
				label = roi.getName();
		if (true) {
			String colorString = Colors.colorToHexString(roi.getFillColor());
			String altType = null;
			if (roi instanceof EllipseRoi)
				altType = "Ellipse";
			if (roi instanceof Arrow)
				altType = "Arrow";
			if (imp != null && addToCurrentImpPosition) {
				if (roi.getName() != null && roi.getName().split("\"").length > 1)
					label = "\"" + roi.getName().split("\"")[1].trim() + " \"" + "_" + colorString + "_" + imp.getChannel() + "_"
							+ imp.getSlice() + "_" + imp.getFrame();
				else if (roi.getName() != null)
					label = "\"" + roi.getName() + " \"" + "_" + colorString + "_" + imp.getChannel() + "_" + imp.getSlice() + "_"
							+ imp.getFrame();
				else
					label = "\"" + ((altType != null) ? altType : roi.getTypeAsString()) + " \"" + "_" + colorString + "_" + imp.getChannel() + "_"
							+ imp.getSlice() + "_" + imp.getFrame();
			} else if (roi.getPosition() != 0) {
				if (roi.getName() != null && roi.getName().split("\"").length > 1)
					label = "\"" + roi.getName().split("\"")[1].trim() + " \"" + "_" + colorString + "_" + roi.getCPosition() + "_"
							+ roi.getZPosition() + "_" + roi.getTPosition();
				else if (roi.getName() != null)
					label = "\"" + roi.getName() + " \"" + "_" + colorString + "_" + roi.getCPosition() + "_" + roi.getZPosition() + "_"
							+ roi.getTPosition();
				else
					label = "\"" + ((altType != null) ? altType : roi.getTypeAsString())  + " \"" + "_" + colorString + "_" + roi.getCPosition() + "_"
							+ roi.getZPosition() + "_" + roi.getTPosition();
			} else {
				label = roi.getName();
			}
		}
		label = getUniqueName(label);
		if (label == null)
			return false;
		listModel.addElement(label);
		fullListModel.addElement(label);
		roi.setName(label);
		recentName = (label);
		if (imp != null && addToCurrentImpPosition /*
													 * && imp.getWindow()!=null && roi.getCPosition()==0 &&
													 * roi.getZPosition()==0 && roi.getTPosition()==0
													 */ )
			roi.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
		roiCopy = (Roi) roi.clone();

		if (imp != null) {
			Calibration cal = imp.getCalibration();
			if (cal.xOrigin != 0.0 || cal.yOrigin != 0.0) {
				Rectangle r = roiCopy.getBounds();
				roiCopy.setLocation(r.x - (int) cal.xOrigin, r.y - (int) cal.yOrigin);
			}
		}
		if (lineWidth > 1)
			roiCopy.setStrokeWidth(lineWidth);
		if (strokeColor != null)
			roiCopy.setStrokeColor(strokeColor);
		if (fillColor != null)
			this.setRoiFillColor(roiCopy, fillColor, false);
		rois.put(label, roiCopy);
		setUpRoisByNameAndNumbers(roiCopy);

		ColorLegend cl = getColorLegend();
		//
		if (roiCopy != null) {
			if (cl != null && cl.getBrainbowColors() != null) {
				Color clColor = cl.getBrainbowColors()
						.get(roiCopy.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
				if (clColor != null) {
					String hexRed = Integer.toHexString(clColor.getRed());
					String hexGreen = Integer.toHexString(clColor.getGreen());
					String hexBlue = Integer.toHexString(clColor.getBlue());
					String hexAlpha = Colors.colorToHexString(roiCopy.getFillColor()).substring(0, 3);
					this.setRoiFillColor(roiCopy,
							Colors.decode(hexAlpha + (hexRed.length() == 1 ? "0" : "") + hexRed
									+ (hexGreen.length() == 1 ? "0" : "") + hexGreen
									+ (hexBlue.length() == 1 ? "0" : "") + hexBlue, Color.white));
				}
			}
		}

		if (!Orthogonal_Views.isOrthoViewsImage(imp)) {
			if (imp != null && imp.getWindow() != null) {
				Roi[] roisFromSpans = new Roi[1];
				roisFromSpans = (getRoisByNumbers().get(
						(addRoiSpanC ? 0 : roiCopy.getCPosition()) + "_" + (addRoiSpanZ ? 0 : roiCopy.getZPosition())
								+ "_" + (addRoiSpanT ? 0 : roiCopy.getTPosition()))).toArray(roisFromSpans);
				if (imp.getWindow() instanceof StackWindow && ((StackWindow) imp.getWindow()).isWormAtlas()) {
					for (Roi existingRoi : roisFromSpans) {
						// if (imp.getSlice() == existingRoi.getZPosition() && imp.getFrame() ==
						// existingRoi.getTPosition()) {
						if (roiCopy.contains((int) existingRoi.getBounds().getCenterX(),
								(int) existingRoi.getBounds().getCenterY())) {
							if (existingRoi instanceof TextRoi && !(roiCopy instanceof TextRoi) && roiCopy.isArea()) {
								label = (true
										/* ((TextRoi)existingRoi).getText().matches(".*") */ ? (""
												+ ((TextRoi) existingRoi).getText().replace("\n", " ") + "| Area")
										: "Blank");
								rename(label, new int[] { listModel.size() - 1 }, true);
								this.setRoiFillColor(roiCopy, existingRoi.getFillColor());
								roiCopy.setStrokeColor(null);
							}
							// }
						}
					}
				} else {
					for (Roi existingRoi : roisFromSpans) {
						// if (imp.getSlice() == existingRoi.getZPosition() && imp.getFrame() ==
						// existingRoi.getTPosition()) {
						if (roiCopy.contains((int) existingRoi.getBounds().getCenterX(),
								(int) existingRoi.getBounds().getCenterY())) {
							if (existingRoi instanceof TextRoi && !(roiCopy instanceof TextRoi) && roiCopy.isArea()) {
								label = (true
										/* ((TextRoi)existingRoi).getText().matches(".*") */ ? (""
												+ ((TextRoi) existingRoi).getText().replace("\n", " ") + "| Area")
										: "Blank");
								rename(label, new int[] { listModel.size() - 1 }, true);
								this.setRoiFillColor(roiCopy, existingRoi.getFillColor());
								roiCopy.setStrokeColor(null);
							}
							// }
						}
					}
				}
			}
		}

		if (imp != null && imp.getWindow() != null && roiCopy.getPosition() == 0) {
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
			// IJ.log("addSingleRoi" +
			// roiCopy.getCPosition()+roiCopy.getZPosition()+roiCopy.getTPosition() );
		}

		// if (false)
		updateShowAll();
		if (record())
			recordAdd(defaultColor, defaultLineWidth);
		textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
		if (imp != null && imp.getWindow() != null) {
			imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			imp.getWindow().countLabel.repaint();
			// imp.getWindow().tagsButton.setText(""+fullListModel.size());

			imp.getWindow().tagsButton.repaint();
		}
		return true;
	}

	public void setUpRoisByNameAndNumbers(Roi roi) {
		ArrayList<Roi> sliceRois = getRoisByNumbers().get((addRoiSpanC ? 0 : roi.getCPosition()) + "_"
				+ (addRoiSpanZ ? 0 : roi.getZPosition()) + "_" + (addRoiSpanT ? 0 : roi.getTPosition()));
		if (sliceRois == null) {
			getRoisByNumbers().put((addRoiSpanC ? 0 : roi.getCPosition()) + "_" + (addRoiSpanZ ? 0 : roi.getZPosition())
					+ "_" + (addRoiSpanT ? 0 : roi.getTPosition()), new ArrayList<Roi>());
			sliceRois = getRoisByNumbers().get((addRoiSpanC ? 0 : roi.getCPosition()) + "_"
					+ (addRoiSpanZ ? 0 : roi.getZPosition()) + "_" + (addRoiSpanT ? 0 : roi.getTPosition()));
		}
		//						sliceRois.add(roi);
		if (roi.getName().contains("~")){
			sliceRois.add(0,roi);
		}else {
			sliceRois.add(roi);
		}

		String rootName = roi.getName().contains("\"") ? "\"" + roi.getName().split("\"")[1] + "\""
				: "\"" + roi.getName().split("_")[0].trim() + " \"";

		ArrayList<Roi> rootNameRois = getRoisByRootName().get(rootName);
		if (rootNameRois == null) {
			getRoisByRootName().put(rootName, new ArrayList<Roi>());
			rootNameRois = getRoisByRootName().get(rootName);
		}
		rootNameRois.add(roi);
	}

	void recordAdd(Color color, int lineWidth) {
		if (Recorder.scriptMode())
			Recorder.recordCall("rm.addRoi(imp.getRoi());");
		else if (color != null && lineWidth == 1)
			Recorder.recordString("roiManager(\"Add\", \"" + getHex(color) + "\");\n");
		else if (lineWidth > 1)
			Recorder.recordString("roiManager(\"Add\", \"" + getHex(color) + "\", " + lineWidth + ");\n");
		else
			Recorder.record("roiManager", "Add");
	}

	String getHex(Color color) {
		if (color == null)
			color = ImageCanvas.getShowAllColor();
		String hex = Integer.toHexString(color.getRGB());
		if (hex.length() == 8)
			hex = hex.substring(2);
		return hex;
	}

	/**
	 * Adds the specified ROI to the list. The third argument ('n') will be used to
	 * form the first part of the ROI label if it is >= 0.
	 */
	public void add(ImagePlus imp, Roi roi, int n) {
		if (roi == null)
			return;
		String label = getLabel(imp, roi, n);
		if (label == null)
			return;
		listModel.addElement(label);
		fullListModel.addElement(label);
		roi.setName(label);
		recentName = (label);
		roiCopy = (Roi) roi.clone();
		if (imp != null) {
			Calibration cal = imp.getCalibration();
			if (cal.xOrigin != 0.0 || cal.yOrigin != 0.0) {
				Rectangle r = roiCopy.getBounds();
				roiCopy.setLocation(r.x - (int) cal.xOrigin, r.y - (int) cal.yOrigin);
			}
		}
		rois.put(label, roiCopy);
		setUpRoisByNameAndNumbers(roiCopy);

		textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
		if (imp.getWindow() != null) {
			imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			imp.getWindow().countLabel.repaint();
			// imp.getWindow().tagsButton.setText(""+fullListModel.size());

			imp.getWindow().tagsButton.repaint();
		}
	}

	boolean isStandardName(String name) {
		if (name == null)
			return false;
		boolean isStandard = false;
		int len = name.length();
		if (len >= 14 && name.charAt(4) == '-' && name.charAt(9) == '-')
			isStandard = true;
		else if (len >= 17 && name.charAt(5) == '-' && name.charAt(11) == '-')
			isStandard = true;
		else if (len >= 9 && name.charAt(4) == '-')
			isStandard = true;
		else if (len >= 11 && name.charAt(5) == '-')
			isStandard = true;
		return isStandard;
	}

	String getLabel(ImagePlus imp, Roi roi, int n) {
		Rectangle r = roi.getBounds();
		int xc = r.x + r.width / 2;
		int yc = r.y + r.height / 2;
		if (n >= 0) {
			xc = yc;
			yc = n;
		}
		if (xc < 0)
			xc = 0;
		if (yc < 0)
			yc = 0;
		int digits = 4;
		String xs = "" + xc;
		if (xs.length() > digits)
			digits = xs.length();
		String ys = "" + yc;
		if (ys.length() > digits)
			digits = ys.length();
		if (digits == 4 && imp != null && imp.getStackSize() >= 10000)
			digits = 5;
		xs = "000000" + xc;
		ys = "000000" + yc;
		String label = ys.substring(ys.length() - digits) + "-" + xs.substring(xs.length() - digits);
		if (imp != null && imp.getStackSize() > 1) {
			int channel = roi.getCPosition();
			int slice = roi.getZPosition();
			int frame = roi.getTPosition();
			//// if (channel==0)
			// channel = imp.getChannel();
			//// if (slice==0)
			// slice = imp.getSlice();
			//// if (frame==0)
			// frame = imp.getFrame();
			String zs = "000000" + slice;
			label = zs.substring(zs.length() - digits) + "-" + label;
			roi.setPosition(channel, slice, frame);
		}
		return label;
	}

	void addAndDraw(boolean altKeyDown) {
		if (altKeyDown) {
			if (!addRoi(true))
				return;
		} else if (!addRoi(false))
			return;
		ImagePlus imp = this.imp;

		if (imp != null) {
			Undo.setup(Undo.COMPOUND_FILTER, imp);
			IJ.run(imp, "Draw", "slice");
			Undo.setup(Undo.COMPOUND_FILTER_DONE, imp);
		}
		if (record())
			Recorder.record("roiManager", "Add & Draw");
	}

	public boolean delete(boolean replacing) {
		int count = listModel.getSize();
		int fullCount = fullListModel.getSize();
		if (count == 0)
			return error("The list is empty.");
		int[] indexes = getSelectedIndexes();
//		Roi[] selRois = getSelectedRoisAsArray();
		if (indexes.length == 0 || (replacing && count > 1)) {
			String msg = "Delete all items on the list?";
			if (replacing)
				msg = "Replace items on the list?";
			canceled = false;
			if (!IJ.isMacro() && !macro) {
				YesNoCancelDialog d = new YesNoCancelDialog(this, "Tag Manager", msg);
				if (d.cancelPressed()) {
					canceled = true;
					return false;
				}
				if (!d.yesPressed())
					return false;
			}
			indexes = getAllShownIndexes();
		}
		if (fullCount == indexes.length && !replacing) {
			rois.clear();
			getRoisByNumbers().clear();
			getRoisByRootName().clear();
			listModel.removeAllElements();
			fullListModel.removeAllElements();
		} else {
						
			list.setModel(new DefaultListModel<String>());
			Arrays.sort(indexes);
			for (int r = indexes.length - 1; r >= 0; r--) {
				fullListModel.removeElement(listModel.get(indexes[r]));
				listModel.remove(indexes[r]);
			}		
			list.setModel(listModel);
		}
		ImagePlus imp = this.imp;

		if (count > 1 && indexes.length == 1 && imp != null)
			imp.deleteRoi();
		updateShowAll();
		textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
		if (imp.getWindow() != null) {
			imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			imp.getWindow().countLabel.repaint();
			// imp.getWindow().tagsButton.setText(""+fullListModel.size());

			imp.getWindow().tagsButton.repaint();
		}
		if (record())
			Recorder.record("roiManager", "Delete");
		return true;
	}

	boolean update(boolean clone) {
		ImagePlus imp = this.imp;

		if (imp == null)
			return false;
		ImageCanvas ic = imp.getCanvas();
		boolean showingAll = ic != null && ic.getShowAllROIs();
		Roi roi = imp.getRoi();
		this.setRoiFillColor(roi, Roi.getDefaultFillColor());
		if (roi == null) {
			error("The active image does not have a selection.");
			return false;
		}
		int index = list.getSelectedIndex();
		if (index < 0 && !showingAll)
			return error("Exactly one item in the list must be selected.");
		if (index >= 0) {
			String label = (String) listModel.getElementAt(index);

			if (roi instanceof TextRoi)
				label = (((TextRoi) roi).getText().indexOf("\n") > 0
						? ("\"" + ((TextRoi) roi).getText().replace("\n", " ") + "\"")
						: "Blank") + "_" + imp.getChannel() + "_" + imp.getSlice() + "_" + imp.getFrame();
			else if (true) {
				String altType = null;
				if (roi instanceof EllipseRoi)
					altType = "Ellipse";
				if (roi instanceof Arrow)
					altType = "Arrow";
				label = ((altType != null) ? altType : roi.getTypeAsString()) + "_" + imp.getChannel() + "_"
						+ imp.getSlice() + "_" + imp.getFrame();
			}
			label = getUniqueName(label);
			if (label == null)
				return false;
			rename(label, null, true);

			roi.setName(label);
			recentName = label;
			// roi.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
			// roiCopy = (Roi)roi.clone();

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
			// roiCopy.setPosition(c,z,t);
			roi.setPosition(c, z, t);
			rois.remove(label);
			if (clone) {
				Roi roi2 = (Roi) roi.clone();
				int position = roi.getPosition();
				if (imp.getStackSize() > 1)
					roi2.setPosition(c, z, t);
				rois.put(label, roi2);
				setUpRoisByNameAndNumbers(roi2);

				// IJ.log("cloning");
			} else {
				rois.put(label, roi);
				setUpRoisByNameAndNumbers(roi);

			}

			updateShowAll();
		}
		if (record())
			Recorder.record("roiManager", "Update");
		if (showingAll)
			imp.draw();
		return true;
	}

	private void rename(String label2, int[] indexes, boolean updateCanvas) {
		String[] newNames = new String[indexes.length];
		for (int n = 0; n < newNames.length; n++) {
			newNames[n] = label2;
		}
		rename(newNames, indexes, false);

	}

	boolean rename(String[] newNames, int[] indexes, boolean updateCanvas) {
		// int index = list.getSelectedIndex();
		// Roi[] selectedRois = this.getSelectedRoisAsArray();
		if (indexes == null)
			indexes = this.getSelectedIndexes();
		// return error("Exactly one item in the list must be selected.");

		list.setModel(new DefaultListModel<String>());

		for (int i = 0; i < indexes.length; i++) {
			String name = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi) rois.get(name);
			if (roi == null)
				continue;
			int c = roi.getCPosition() > 0 ? roi.getCPosition() : imp.getChannel();
			int z = roi.getZPosition() > 0 ? roi.getZPosition() : imp.getSlice();
			int t = roi.getTPosition() > 0 ? roi.getTPosition() : imp.getFrame();
			if (newNames[i] == null) {
				String newestName = null;
				if (roi instanceof TextRoi) {
					newestName = promptForName(((TextRoi) roi).getText().replace("\n", "|"));
					if (newestName != null)
						newNames[i] = newestName;
				} else if (name.split("\"").length > 1) {
					newestName = promptForName(name.split("\"")[1]).trim();
					if (newestName != null)
						newNames[i] = newestName;
				} else {
					newestName = promptForName(name);
					if (newestName != null)
						newNames[i] = newestName;
				}
			}
			if (newNames[i] == null)
				return false;
			String nameRoot = name.contains("\"") ? "\"" + name.split("\"")[1] + "\""
					: "\"" + name.split("_")[0].trim() + " \"";
//			String numbersKey = name.replaceAll(".*(_.*_.*_.*)(\\-*.*)", "$1")
//					.replaceFirst("_", "")
//					.replaceAll("(.*)C", "$1");
//			String numbersKey = "0_"+z+"_"+t;
//			if (name.endsWith("C"))
//				c =0;
			String numbersKey = c + "_" + z + "_" + t;
			roisByRootName.get(nameRoot).remove(roi);
			if (roisByNumbers.get(numbersKey) != null) {
				roisByNumbers.get(numbersKey).remove(roi);
			} else if (roisByNumbers.get(numbersKey.replaceAll("\\d_(.*)", "0_$1")) != null) {
				roisByNumbers.get(numbersKey.replaceAll("\\d_(.*)", "0_$1")).remove(roi);
			}
			rois.remove(name);
			String label = name != null ? name : getLabel(imp, roi, -1);
			if (roi instanceof TextRoi) {
				if (imp != null) {
					((TextRoi) roi).setText(newNames[i]);
					label = (((TextRoi) roi).getText().indexOf("\n") > 0
							? ("\"" + ((TextRoi) roi).getText().replace("\n", " ") + "\"")
							: "Blank") + "_" + c + "_" + z + "_" + t;
				} else {
					label = roi.getName();
				}
			} else if (true) {
				String altType = null;
				if (roi instanceof EllipseRoi)
					altType = "Ellipse";
				if (roi instanceof Arrow)
					altType = "Arrow";
				if (imp != null) {
					if (newNames[i].startsWith("\"")) {
						label = newNames[i];
					} else {
						label = ("\"" + newNames[i] + " \"") + "_" + Colors.colorToHexString(roi.getFillColor()) + "_"
								+ c + "_" + z + "_" + t;
					}
				} else
					label = roi.getName();
			}
			label = getUniqueName(label);

			roi.setName(label);
			recentName = label;
			rois.put(label, roi);
			setUpRoisByNameAndNumbers(roi);

			ColorLegend cl = getColorLegend();
			//
			if (roi != null) {
				if (cl != null) {
					Color clColor = cl.getBrainbowColors()
							.get(roi.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
					if (clColor != null && !clColor.equals(roi.getFillColor())) {
						String hexRed = Integer.toHexString(clColor.getRed());
						String hexGreen = Integer.toHexString(clColor.getGreen());
						String hexBlue = Integer.toHexString(clColor.getBlue());
						String hexAlpha = Colors.colorToHexString(roi.getFillColor()).substring(0, 3);
						this.setRoiFillColor(roi,
								Colors.decode(hexAlpha + (hexRed.length() == 1 ? "0" : "") + hexRed
										+ (hexGreen.length() == 1 ? "0" : "") + hexGreen
										+ (hexBlue.length() == 1 ? "0" : "") + hexBlue, Color.white));
					}
				}
			}

			listModel.setElementAt(label, indexes[i]);
			fullListModel.setElementAt(label, fullListModel.indexOf(name));
		}
		list.setModel(listModel);
		if (updateCanvas)
			updateShowAll();
		if (record())
			Recorder.record("roiManager", "Rename", newNames[0]);
		return true;
	}

	String promptForName(String name) {
		String name2 = "";
//		if (textFindingField.getText().isEmpty()  || textFindingField.getText().contains("Name...")) {
		if (true) {
			GenericDialog gd = new GenericDialog("Tag Manager");
			gd.addStringField("Rename As:", name.endsWith("|") ? name.substring(0, name.length() - 1) : name, 20);
			gd.addCheckbox("Propagate Lineage Renaming", propagateRenamesThruLineage);
			gd.showDialog();
			if (gd.wasCanceled())
				return null;
			name2 = gd.getNextString();
			propagateRenamesThruLineage = gd.getNextBoolean();
			// name2 = getUniqueName(name2);
		} else {
			name = textFindingField.getText();
			name2 = name.endsWith("|") ? name.substring(0, name.length() - 1) : name;
		}
		return name2;

	}

	boolean restore(ImagePlus imp, int index, boolean setSlice) {
		String label = (String) listModel.getElementAt(index);
		Roi roi = (Roi) rois.get(label);
		if (imp == null || roi == null)
			return false;
		if (imp.getWindow() != null) {
			if (imp.getWindow().running || imp.getWindow().running2 || imp.getWindow().running3) {
				IJ.run("Stop Animation");
			}
		}
		if (setSlice) {
			int n = getSliceNumber(roi, label);
			// resets n the the proper CZT position of the current image based on n's CZT
			// postion in the motherImp of this ROI
			if (roi.getMotherImp() != null) {
				if (imp.getWindow() != null) {
					if (imp.isHyperStack() || imp.isComposite() || imp.getWindow() instanceof StackWindow) {
						int c = roi.getCPosition();
						int z = roi.getZPosition();
						int t = roi.getTPosition();
						if (c == 0)
							c = imp.getChannel();
						if (z == 0)
							z = imp.getSlice();
						if (t == 0)
							t = imp.getFrame();

						imp.setPosition(c, z, t);
					}
				}
			} else if (n >= 1 && n <= imp.getStackSize()) {
				if (imp.isHyperStack() || imp.isComposite())
					imp.setPosition(n);
				else
					imp.setSlice(n);
			}
		}
		Roi roi2 = (Roi) roi.clone();
		roi2.copyAttributes(roi);
		Calibration cal = imp.getCalibration();
		Rectangle r = roi2.getBounds();
		if (cal.xOrigin != 0.0 || cal.yOrigin != 0.0)
			roi2.setLocation(r.x + (int) cal.xOrigin, r.y + (int) cal.yOrigin);
		int width = imp.getWidth(), height = imp.getHeight();
		if (restoreCentered) {
			if (imp.getCanvas() != null) {
				ImageCanvas ic = imp.getCanvas();
				if (ic != null) {
					Rectangle r1 = ic.getSrcRect();
					Rectangle r2 = roi2.getBounds();
					roi2.setLocation(r1.x + r1.width / 2 - r2.width / 2, r1.y + r1.height / 2 - r2.height / 2);
				}
			}
		}
		boolean oob = false;
		if (r.x >= width) {
			r.x = width - 10;
			oob = true;
		}
		if ((r.x + r.width) <= 0) {
			r.x = 10;
			oob = true;
		}
		if (r.y >= height) {
			r.y = height - 10;
			oob = true;
		}
		if ((r.y + r.height) <= 0) {
			r.y = 10;
			oob = true;
		}
		if (oob)
			roi2.setLocation(r.x, r.y);
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

	/**
	 * Returns the slice number associated with the specified name, or -1 if the
	 * name does not include a slice number.
	 */
	public int getSliceNumber(String label) {
		int slice = -1;
		if (label.length() >= 14 && label.charAt(4) == '-' && label.charAt(9) == '-')
			slice = (int) Tools.parseDouble(label.substring(0, 4), -1);
		else if (label.length() >= 17 && label.charAt(5) == '-' && label.charAt(11) == '-')
			slice = (int) Tools.parseDouble(label.substring(0, 5), -1);
		else if (label.length() >= 20 && label.charAt(6) == '-' && label.charAt(13) == '-')
			slice = (int) Tools.parseDouble(label.substring(0, 6), -1);
		return slice;
	}

	/**
	 * Returns the slice number associated with the specified ROI or name, or -1 if
	 * the ROI or name does not include a slice number.
	 */
	int getSliceNumber(Roi roi, String label) {
		int slice = roi != null ? roi.getPosition() : -1;
		if (slice == 0)
			slice = -1;
		if (slice == -1)
			slice = getSliceNumber(label);
		return slice;
	}

	public void open(String path) {
		Macro.setOptions(null);
		String name = null;
		if (path == null || path.equals("")) {
			OpenDialog od = new OpenDialog("Open Selection(s)...", "");
			String directory = od.getDirectory();
			name = od.getFileName();
			if (name == null)
				return;
			path = directory + name;
		}
		lastRoiOpenPath = path;
		if (Recorder.record && !Recorder.scriptMode())
			Recorder.record("roiManager", "Open", path);
		if (path.endsWith(".zip")) {
			if (!isRoiScaleFactorSet) {
				roiRescaleFactor = IJ.getNumber("Rescale incoming tags from "+new File(path).getName()+" to fit resized image?", roiRescaleFactor);
			} else {
				isRoiScaleFactorSet = false;
			}
			String origTitle = this.title;
			// this.setTitle("Tag Manager LOADING!!!");
			openZip(path);
			// this.setTitle(origTitle);
			textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
			if (imp.getWindow() != null) {
				imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
				imp.getWindow().countLabel.repaint();
				// imp.getWindow().tagsButton.setText(""+fullListModel.size());

				imp.getWindow().tagsButton.repaint();
			}
			return;
		}
		if (path.endsWith(".xml")) {
			String origTitle = this.title;
			// this.setTitle("Tag Manager LOADING!!!");
			openXml(path);
			// this.setTitle(origTitle);
			textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
			if (imp.getWindow() != null) {
				imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
				imp.getWindow().countLabel.repaint();
				// imp.getWindow().tagsButton.setText(""+fullListModel.size());

				imp.getWindow().tagsButton.repaint();
			}
			return;
		}
		if (path.endsWith(".csv")) {
			String origTitle = this.title;
			// this.setTitle("Tag Manager LOADING!!!");
			openCsv(path);
			// this.setTitle(origTitle);
			textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
			if (imp.getWindow() != null) {
				imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
				imp.getWindow().countLabel.repaint();
				//// imp.getWindow().tagsButton.setText(""+fullListModel.size());

				// imp.getWindow().tagsButton.repaint();
			}
			return;
		}

		Opener o = new Opener();
		if (name == null)
			name = o.getName(path);
		Roi roi = o.openRoi(path);
		if (roi != null) {
			roi.setImage(imp);
			if (name.endsWith(".roi"))
				name = name.substring(0, name.length() - 4);
			name = getUniqueName(name);
			listModel.addElement(name);
			fullListModel.addElement(name);
			roi.setName(name);
			rois.put(name, roi);
			int c = 0;
			int z = 0;
			int t = 0;

			if (imp != null && imp.getWindow() != null && roi.getPosition() == 0) {
				c = imp.getChannel();
				z = imp.getSlice();
				t = imp.getFrame();
				roi.setPosition(c, z, t);
				// IJ.log("addSingleRoi" +
				// roiCopy.getCPosition()+roiCopy.getZPosition()+roiCopy.getTPosition() );
			} else {
				c = roi.getCPosition();
				z = roi.getZPosition();
				t = roi.getTPosition();
			}
			if (roi.getName() != null && roi.getName().split("_").length > 3) {
				if (roi.getName().split("_")[3].contains("C"))
					c = 0;
				if (roi.getName().split("_")[3].contains("Z"))
					z = 0;
				if (roi.getName().split("_")[3].contains("T"))
					t = 0;
			}
			if (addRoiSpanC) {
				c = 0;
			}
			if (addRoiSpanZ) {
				z = 0;
			}
			if (addRoiSpanT) {
				t = 0;
			}
			roi.setPosition(c, z, t);
			setUpRoisByNameAndNumbers(roi);
		}
		showAll(SHOW_ALL);
		updateShowAll();
	}

	private void openXml(String path) { // for TrakEM2 output
		busy = true;
		boolean wasVis = this.isVisible();
		this.setVisible(false);
		// showAll(SHOW_ALL);
		String[] sibFileNames = new File(path).getParentFile().list();
		String meta = "";
		Hashtable<String, String> metaNamingHash = new Hashtable<String, String>();
		for (String sibName : sibFileNames) {
			if (sibName.endsWith("VAST_segmentation_metadata.txt")) {
				meta = IJ.openAsString(new File(path).getParent() + File.separator + sibName);
				break;
			}
		}
		if (meta != "") {
			String[] metaLines = meta.split("\n");
			for (String metaLine : metaLines) {
				if (metaLine.matches("(\\d+ )(.*\")(([A-Z]|\\d)+(-([A-Z]|\\d)*)? )(.*\")")) {
					String keyNumbStr = metaLine.replaceAll("(\\d+ )(.*\")(([A-Z]|\\d)*+(-([A-Z]|\\d)*)? )(.*\")", "$1")
							.trim();
					String cellNameStr = metaLine.replaceAll("(\\d+ )(.*\")(([A-Z]|\\d)+(-([A-Z]|\\d)*)? )(.*\")", "$3")
							.trim();
					metaNamingHash.put("Label " + keyNumbStr, cellNameStr);
				}
			}
		}
		String s = IJ.openAsString(path);
		// IJ.log(s);
		String impTitle = this.imp.getTitle();

		String[] sLayers = s.split("<t2_layer oid=\"");
		String[] sCells = s.split("<t2_area_list");
		String[] sConnectors = s.split("<t2_connector");

		Hashtable<String, String> cellAreaHash = new Hashtable<String, String>();
		Hashtable<String, String> sConnLayerHash = new Hashtable<String, String>();

		String fillColor;
		String cellName;
		long count = 0;
		long nRois = 0;
		String universalCLURL = MQTVSSceneLoader64.class.getResource("docs/fullUniversal_ColorLegend.lgd").toString();
		String clStr = IJ.openUrlAsString(universalCLURL);
		setColorLegend(new ColorLegend(this.imp, clStr));
		ArrayList<Integer> sliceValues = new ArrayList<Integer>();

		for (int sl = 1; sl < sLayers.length; sl++) {
			String sLayer = sLayers[sl];
			if (!sLayer.contains("file_path="))
				continue;
			String filePath = sLayer.split("file_path=")[1].split("\"")[1];
			String[] pathChunks = filePath.split("/");
			String nameChunk = pathChunks[pathChunks.length - 1];
			String fvalue = "";
			if (nameChunk.contains("-#slice=")) {
				fvalue = nameChunk.replaceAll("(.*#slice=)(\\d+)", "$2");
			} else {
				fvalue = nameChunk.replaceAll("(.*_s?)(\\d+)(.png|.tiff?)", "$2");
				fvalue = fvalue.replaceAll("(\\D*)(\\d+)(.png|.tiff?)", "$2");
			}
			sliceValues.add(Integer.parseInt(fvalue)
					- ((sLayer.split("file_path=")[1].split("\"")[1]).contains("VC_") ? 10000000 : 0));

		}
		int maxBaseSlice = 0;
		for (int sv : sliceValues) {
			if (maxBaseSlice < sv) {
				maxBaseSlice = sv;
			}
		}

		for (int cell = 1; cell < sCells.length; cell++) {
			// counter++;
			// if (counter<=1) continue;
			String sCell = sCells[cell];
			int offsetX = Integer.parseInt(sCell.split("(;fill:|;\")").length > 1
					&& sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(").length > 1
							? (sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(")[1]).split("[,\\.]")[8]
							: "0");
			int offsetY = Integer.parseInt(sCell.split("(;fill:|;\")").length > 1
					&& sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(").length > 1
							? (sCell.split("(;fill:|;\")")[0].split("transform=\"matrix\\(")[1]).split("[,\\.]")[10]
							: "0");

			String cellLabel = (sCell.split("title=\"")[1].split("\"").length > 1
					? sCell.split("title=\"")[1].split("\"")[0]
					: "");
			if (metaNamingHash.get(cellLabel) != null) {
				cellName = metaNamingHash.get(cellLabel).trim();
			} else {
				cellName = cellLabel;
				cellName = cellName.replaceAll("(.*)(_\\d+)", "$1");
				cellName = cellName.replace("BWM_", "BWM-");
			}
			fillColor = (sCell.split("(;fill:|;\")").length > 1
					? (sCell.split("(;fill:|;\")")[1].startsWith("#") ? sCell.split("(;fill:|;\")")[1] : "")
					: "");
			getColorLegend().getBrainbowColors().put(cellName.toLowerCase(), Colors.decode(fillColor, Color.white));
			IJ.log(cellName + " " + fillColor + " " + offsetX + " " + offsetY);
			String[] sCellAreas = sCell.split("<t2_area");
			int maxReps = 0;
			int reps = 0;
			for (String sCellArea : sCellAreas) {
				String slicePosition = (sCellArea.split("\"").length > 1 ? sCellArea.split("\"")[1] : "");
				for (int k = 1; k < sCellArea.split("t2_path d=\"").length; k++) {
					reps = k;
//					IJ.log(fillColor+"_"+slicePosition+"_"+k+" "+offsetX+" "+offsetY);
					cellAreaHash.put(cellName + "_" + fillColor + "_" + slicePosition + "_" + k,
							sCellArea.split("t2_path d=\"").length > 1
									? sCellArea.split("t2_path d=\"")[k].split("\"")[0]
									: "");
				}
				maxReps = maxReps < reps ? reps : maxReps;
			}

			for (int sl = 0; sl < sLayers.length; sl++) {
				count++;
				// this.setTitle( "Tag Manager" + ((nRois%100>50)?" LOADING!!!":" Loading...")
				// );
				if (nRois % 100 > 50) {
					IJ.runMacro("print(\"\\\\Update:***Tag Manager is still loading tags...***" + count + "\");");
					// this.imp.setTitle("***"+ impTitle);
				} else {
					IJ.runMacro("print(\"\\\\Update:   Tag Manager is still loading tags...   " + count + "\");");
					// this.imp.setTitle(" "+ impTitle);
				}

				String sLayer = sLayers[sl];
				if (!sLayer.contains("file_path="))
					continue;
				int sliceNumber = 0;
				IJ.log(cellAreaHash.get(cellName + "_" + fillColor + "_" + sLayer.split("\"")[0]));
				String areaString = null;

				for (int rep = 1; rep <= maxReps; rep++) {
					areaString = cellAreaHash.get(cellName + "_" + fillColor + "_" + sLayer.split("\"")[0] + "_" + rep);
					if (areaString != null) {
						String filePath = sLayer.split("file_path=")[1].split("\"")[1];
						String[] pathChunks = filePath.split("/");
						String nameChunk = pathChunks[pathChunks.length - 1];
						String fvalue = "";
						if (nameChunk.contains("-#slice=")) {
							fvalue = nameChunk.replaceAll("(.*#slice=)(\\d+)", "$2");
						} else {
							fvalue = nameChunk.replaceAll("(.*_s?)(\\d+)(.png|.tiff?)", "$2");
							fvalue = fvalue.replaceAll("(\\D*)(\\d+)(.png|.tiff?)", "$2");
						}
						sliceNumber = Integer.parseInt(fvalue)
								+ ((sLayer.split("file_path=")[1].split("\"")[1]).contains("VC_") ? maxBaseSlice + 1
										: 0);
						sConnLayerHash.put(sLayer.split("\"")[0], "" + sliceNumber);
						String[] coordStrings = areaString.replaceAll("M", "").split(" L ");
						int[] xCoords = new int[coordStrings.length];
						int[] yCoords = new int[coordStrings.length];
						for (int i = 0; i < coordStrings.length; i++) {
							if (true) {
								xCoords[i] = Integer.parseInt(coordStrings[i].trim().split(" ")[0]) + offsetX;

								yCoords[i] = Integer.parseInt(coordStrings[i].trim().split(" ")[1]) + offsetY;
							}
						}
						Roi pRoi = new PolygonRoi(xCoords, yCoords, yCoords.length, Roi.FREEROI);
						pRoi.setImage(imp);
						pRoi.setName(cellName);
						listModel.addElement(cellName);
						fullListModel.addElement(cellName);
						rois.put(cellName, pRoi);
						pRoi.setFillColor(Colors.decode(fillColor.replace("#", "#33"), defaultColor));
						pRoi.setPosition(1, sliceNumber, 1);
						setUpRoisByNameAndNumbers(pRoi);
						nRois++;

						// list.setSelectedIndex(this.getCount()-1);
						this.rename(cellName, new int[] { this.getCount() - 1 }, false);
					}
				}
			}
		}

		for (int sc = 1; sc < sConnectors.length; sc++) {
			count++;
			// this.setTitle( "Tag Manager" + ((nRois%100>50)?" LOADING!!!":" Loading...")
			// );
			if (nRois % 100 > 50) {
				IJ.runMacro("print(\"\\\\Update:***Tag Manager is still loading tags...***" + count + "\");");
				// this.imp.setTitle("***"+ impTitle);
			} else {
				IJ.runMacro("print(\"\\\\Update:   Tag Manager is still loading tags...   " + count + "\");");
				// this.imp.setTitle(" "+ impTitle);
			}

			String sConnector = sConnectors[sc];
			String connLayer = sConnector.split("lid=\"").length > 1 ? (sConnector.split("lid=\"")[1].split("\"")[0])
					: "";
			String connOffsetX = sConnector.split("transform=\"matrix\\(").length > 1
					? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[4].split("\\.")[0]
					: "";
			String connOffsetY = sConnector.split("transform=\"matrix\\(").length > 1
					? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[5].split("\\.")[0]
					: "";
			String coordsXY = sConnector.split("<t2_node x=").length > 2
					? sConnector.split("<t2_node x=")[1].split("\"")[1] + "_"
							+ sConnector.split("<t2_node x=")[1].split("\"")[3] + "_"
							+ sConnector.split("<t2_node x=")[2].split("\"")[1] + "_"
							+ sConnector.split("<t2_node x=")[2].split("\"")[3]
					: "";
			// String connLayer = sConnector.split("<t2_node x=")[2].split("\"")[5];
			String connStroke = sConnector.split("stroke:")[1].split(";")[0];

			String[] connCoords = coordsXY.split("[_]");
			if (connCoords.length == 4) {
				double x1 = Double.parseDouble(connCoords[0].split("\\.")[0])/* + Integer.parseInt(connOffsetX) */;
				double y1 = Double.parseDouble(connCoords[1].split("\\.")[0])/* + Integer.parseInt(connOffsetY) */;
				double x2 = Double.parseDouble(connCoords[2].split("\\.")[0])/* + Integer.parseInt(connOffsetX) */;
				double y2 = Double.parseDouble(connCoords[3].split("\\.")[0])/* + Integer.parseInt(connOffsetY) */;
				int sliceNumber = Integer.parseInt(sConnLayerHash.get(connLayer));

				double[] preAffinePoints = { x1, y1, x2, y2 };
				double[] postAffinePoints = { 0, 0, 0, 0 };
				AffineTransform at = new AffineTransform(
						(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length > 1
								? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[0]
								: "")),
						(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length > 1
								? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[1]
								: "")),
						(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length > 1
								? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[2]
								: "")),
						(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length > 1
								? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[3]
								: "")),
						(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length > 1
								? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[4]
								: "")),
						(Double.parseDouble(sConnector.split("transform=\"matrix\\(").length > 1
								? sConnector.split("transform=\"matrix\\(")[1].split("[,\\)]")[5]
								: "")));
				at.transform(preAffinePoints, 0, postAffinePoints, 0, 2);
				Roi aRoi = new Arrow(postAffinePoints[0], postAffinePoints[1], postAffinePoints[2],
						postAffinePoints[3]);
				aRoi.setImage(imp);

				listModel.addElement(connStroke);
				fullListModel.addElement(connStroke);
				rois.put(connStroke, aRoi);
				setUpRoisByNameAndNumbers(aRoi);
				nRois++;

				aRoi.setStrokeColor(Colors.decode(connStroke, defaultColor));
				aRoi.setPosition(1, 1, sliceNumber);

				// list.setSelectedIndex(this.getCount()-1);
				this.rename(connStroke, new int[] { this.getCount() - 1 }, false);
			}
		}
		updateShowAll();
		// this.imp.setTitle(impTitle);
		this.setVisible(wasVis);
		colorLegend = new ColorLegend(this);
		busy = false;
	}

	void defineConnectors() {
		for (int i = 0; i < getFullRoisAsArray().length; i++) {
			Roi roi = getFullRoisAsArray()[i];

			if (roi instanceof Arrow) {
				// float[] arrowPoints = ((Arrow) roi).getPoints();
				int tPosition = roi.getTPosition();
				String[] endNames = { "NoName", "NoName" };
				for (Roi roi2 : getFullRoisAsArray()) {
					if (endNames[0] == "NoName" && tPosition == roi2.getTPosition() && roi2.isArea()
							&& (new ShapeRoi(roi2).contains((int) ((Line) roi).x1d, (int) ((Line) roi).y1d))) {
						endNames[0] = roi2.getName().split("[|\"]")[1].trim();
					}
					if (endNames[1] == "NoName" && tPosition == roi2.getTPosition() && roi2.isArea()
							&& (new ShapeRoi(roi2).contains((int) ((Line) roi).x2d, (int) ((Line) roi).y2d))) {
						endNames[1] = roi2.getName().split("[|\"]")[1].trim();
					}
				}
				int[] array = { i };
				// this.setSelectedIndexes(array);
				rename("synapse:" + endNames[0] + ">" + endNames[1], array, false);
			}
		}
		updateShowAll();
	}

	// Modified on 2005/11/15 by Ulrik Stervbo to only read .roi files and to not
	// empty the current list
	void openZip(String path) {
		busy = true;
		boolean wasVis = this.isVisible();
		this.setVisible(false);
		showAll(SHOW_NONE);
		showAllCheckbox.setSelected(false);

		ZipInputStream in = null;
		ByteArrayOutputStream out;
		Roi messageRoi;
		long nRois = 0;
		try {
			if (!path.startsWith("/Volumes/GLOWORM_DATA/"))
				in = new ZipInputStream(new FileInputStream(path));
			else if ((new File(IJ.getDirectory("home") + "CytoSHOWCacheFiles" + path)).exists())
				in = new ZipInputStream(
						new FileInputStream(new File(IJ.getDirectory("home") + "CytoSHOWCacheFiles" + path)));
			else {
				if (imp.getRemoteMQTVSHandler().getCompQ().getFileInputByteArray(path) != null)
					in = new ZipInputStream(new ByteArrayInputStream(
							imp.getRemoteMQTVSHandler().getCompQ().getFileInputByteArray(path)));
			}
			byte[] buf = new byte[1024];
			int len;
			if (in == null)
				return;
			ZipEntry entry = in.getNextEntry();
			IJ.log("");
			String impTitle = this.imp.getTitle();
			long count = 0;
			// fill up the list here
			// this.removeAll();
			long timeLast = 0;
			long timeNow = 0;

			while (entry != null) {
				timeNow = System.currentTimeMillis();
				if (timeNow > timeLast + 100) {
					timeLast = timeNow;
					if (imp.getCanvas() != null) {
						Graphics g = imp.getCanvas().getGraphics();
						if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
							imp.getCanvas().messageRois.remove("Loading Tags");

						messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
								"   Loading Tags:\n   " + "   ..." + count + " features tagged\n");

						((TextRoi) messageRoi).setCurrentFont(
								g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width / 16)));
						messageRoi.setStrokeColor(Color.black);
						this.setRoiFillColor(messageRoi, Colors.decode("#99ffffdd", imp.getCanvas().getDefaultColor()));

						imp.getCanvas().messageRois.put("Loading Tags", messageRoi);
						imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
					}
				}
				// this.setTitle( "Tag Manager" + ((nRois%100>50)?" LOADING!!!":" Loading...")
				// );
				if (nRois % 100 > 50) {
					// IJ.runMacro("print(\"\\\\Update:***Tag Manager is still loading
					// tags...***"+count+"\");");
					// this.imp.setTitle("***"+ impTitle);
				} else {
					// IJ.runMacro("print(\"\\\\Update: Tag Manager is still loading tags...
					// "+count+"\");");
					// this.imp.setTitle(" "+ impTitle);
				}
				count++;

				String name = entry.getName();
				if (name.endsWith(".roi")) {
					out = new ByteArrayOutputStream();
					while ((len = in.read(buf)) > 0)
						out.write(buf, 0, len);
					out.close();
					byte[] bytes = out.toByteArray();
					RoiDecoder rd = new RoiDecoder(roiRescaleFactor, bytes, name);
					Roi roi = rd.getRoi();
					String savedName = roi.getName();
					if (savedName != "" && savedName != name){
						name = savedName;
					}
					if (roi instanceof ShapeRoi) {
//						IJ.wait(1);
					}

					//
					ColorLegend cl = getColorLegend();
					//

					if (roi != null) {
//						name = name.substring(0, name.length() - 4);

						int c = roi.getCPosition();
						int z = roi.getZPosition();
						int t = roi.getTPosition();
						Color roiColor = roi.getFillColor();

						if (name.split("_").length == 4) {
							c = Integer.parseInt(name.split("_")[1]);
							z = Integer.parseInt(name.split("_")[2]);
							t = Integer.parseInt(name.split("_")[3].split("[CZT-]")[0]);
						}

						if (name.split("_").length == 5) {
							roiColor = Colors.decode(name.split("_")[1], roiColor);
							c = Integer.parseInt(name.split("_")[2]);
							z = Integer.parseInt(name.split("_")[3]);
							t = Integer.parseInt(name.split("_")[4].split("[CZT-]")[0]);
						}

						int oldZ = z;

////		  SPECIAL CASE ONLY FOR honoring JSH image GAPS AT Z56 z162-166				
//						if (z>55){
//							z++;
//						}
//						if (z>162){
//							z++;
//							z++;
//							z++;
//							z++;							
//						}

						roi.setPosition(c, z, t);
						roi.setFillColor(roiColor); // only place in this class where this.setRoiFillColor(Roi, Color)
													// should not be used.

						if (imp.getNSlices() == 1)
							roi.setPosition(c, 1, t);
						if (!name.contains(" \"_#"))
							name = name.replace(" \"_", " \"_"+Colors.colorToHexString(roiColor)+"_");
						name = name.replace("_" + oldZ + "_", "_" + z + "_");

						if (cl != null) {
							Color clColor = cl.getBrainbowColors().get(
									roi.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
							if (clColor != null) {
								String hexRed = Integer.toHexString(clColor.getRed());
								String hexGreen = Integer.toHexString(clColor.getGreen());
								String hexBlue = Integer.toHexString(clColor.getBlue());
								String hexAlpha = Colors.colorToHexString(roiColor).substring(0, 3);
								this.setRoiFillColor(roi,
										Colors.decode(hexAlpha + (hexRed.length() == 1 ? "0" : "") + hexRed
												+ (hexGreen.length() == 1 ? "0" : "") + hexGreen
												+ (hexBlue.length() == 1 ? "0" : "") + hexBlue, Color.white));
								roiColor = roi.getFillColor();
							}
						}
						if (name.split("_").length == 4) {
							name = name.replace(" \"_", " \"_" + Colors.colorToHexString(roiColor) + "_");
						} else if (name.split("_").length == 5) {
							name = name.replace(name.split("_")[1], Colors.colorToHexString(roiColor));
						}
						roi.setImage(imp);

						if (roi instanceof TextRoi) {
							name = (((TextRoi) roi).getText().indexOf("\n") > 0
									? ("\"" + ((TextRoi) roi).getText().replace("\n", " ") + "\"")
									: "Blank") + "_" + Colors.colorToHexString(roiColor) + "_" + name.split("_")[1]
									+ "_" + name.split("_")[2] + "_" + name.split("_")[3];

						}
						name = getUniqueName(name);
						
						listModel.addElement(name);
						fullListModel.addElement(name);
						rois.put(name, roi);
						((Roi) rois.get(name)).setName(name); // weird but necessary, and logically so
						String nameEndReader = name;
						while (nameEndReader.endsWith("C") || nameEndReader.endsWith("Z")
								|| nameEndReader.endsWith("T")) {
							if (nameEndReader.endsWith("C")) {
								c = 0;
								nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
							}
							if (nameEndReader.endsWith("Z")) {
								z = 0;
								nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
							}
							if (nameEndReader.endsWith("T")) {
								t = 0;
								nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
							}
						}
						roi.setPosition(c, z, t);
						String rbnKey = roi.getCPosition() + "_" + (imp.getNSlices() == 1 ? 1 : roi.getZPosition())
								+ "_" + roi.getTPosition();
						ArrayList<Roi> sliceRois = getRoisByNumbers().get(rbnKey);
						if (sliceRois == null) {
							getRoisByNumbers().put(rbnKey, new ArrayList<Roi>());
							sliceRois = getRoisByNumbers().get(rbnKey);
						}
//						sliceRois.add(roi);
						if (roi.getName().contains("~")){
							sliceRois.add(0,roi);
						}else {
							sliceRois.add(roi);
						}
						String rootName = roi.getName().contains("\"") ? "\"" + roi.getName().split("\"")[1] + "\""
								: roi.getName().split("_")[0].trim();

						ArrayList<Roi> rootNameRois = getRoisByRootName().get(rootName);
						if (rootNameRois == null) {
							getRoisByRootName().put(rootName, new ArrayList<Roi>());
							rootNameRois = getRoisByRootName().get(rootName);
						}
						rootNameRois.add(roi);

						nRois++;
						ImagePlus imp = this.imp;

					}
				}
				entry = in.getNextEntry();
			}
			in.close();
			// //this.imp.setTitle(impTitle);
			// if(imp.getCanvas()!=null) {
			// if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
			// imp.getCanvas().messageRois.remove("Loading Tags");
			// }
		} catch (IOException e) {
			error(e.toString());
		}
		if (in == null)
			return;
		if (nRois == 0)
			error("This ZIP archive does not appear to contain \".roi\" files");

		Roi[] roiArray = getFullRoisAsArray();
		int n = roiArray.length;

//		Roi[] clonedArray = new Roi[n];
//		for (int i=0; i<n; i++) {
//			if (roiArray[i] instanceof TextRoi)
//				clonedArray[i] = (TextRoi) roiArray[i].clone();
//			else
//				clonedArray[i] = (Roi) roiArray[i].clone();
//
//			clonedArray[i].setPosition(roiArray[i].getCPosition(), roiArray[i].getZPosition(), roiArray[i].getTPosition());
//		}
//		originalRois = clonedArray;
//		originalsCloned = true;

		if (imp.getMultiChannelController() != null)
			imp.getMultiChannelController().updateRoiManager();
		this.setVisible(wasVis);
		showAll(SHOW_ALL);
		updateShowAll();
		showAllCheckbox.setSelected(true);
		if (imp.getCanvas() != null) {
			imp.getCanvas().messageRois.remove("Loading Tags");
			imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
		}
		messageRoi = null;
		busy = false;
		if (path.startsWith("/Volumes/GLOWORM_DATA/")
				&& !(new File(IJ.getDirectory("home") + "CytoSHOWCacheFiles" + path)).exists()) {
			saveMultiple(this.getFullListIndexes(), IJ.getDirectory("home") + "CytoSHOWCacheFiles" + path);
		}
		System.gc();
	}

	public String getUniqueName(String name) {
		if (name.matches(".*(-\\d+)")){
			//clean up stray suffix strings in some saved rois
			name = name.replaceAll("(.*?)((-\\d+)+)(-\\d+)", "$1$4");
		}
		String name2 = name + (addRoiSpanC ? "C" : "") + (addRoiSpanZ ? "Z" : "") + (addRoiSpanT ? "T" : "");
		String suffix = "";
		while (name2.endsWith("C") || name2.endsWith("Z") || name2.endsWith("T")) {
			suffix = (suffix.contains(name2.substring(name2.length() - 1)) ? "" : name2.substring(name2.length() - 1))
					+ suffix;
			name2 = name2.substring(0, name2.length() - 1);
		}
		Roi roi2 = (Roi) rois.get(name2 + suffix);
		int n = 1;
		while (roi2 != null) {
			roi2 = (Roi) rois.get(name2 + suffix);
			if (roi2 != null) {
				int lastDash = name2.lastIndexOf("-");
				if (lastDash != -1 && name2.length() - lastDash < 5)
					name2 = name2.substring(0, lastDash);
				name2 = name2 + "-" + n;

				n++;
			}
			roi2 = (Roi) rois.get(name2 + suffix);
		}
		return name2 + suffix;
	}

	boolean save() {
		if (listModel.getSize() == 0)
			return error("The selection list is empty.");
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		if (indexes.length > 1)
			return saveMultiple(indexes, null);
		String name = (String) listModel.getElementAt(indexes[0]);
		Macro.setOptions(null);
		SaveDialog sd = new SaveDialog("Save Selection...", imp.getShortTitle() + "_" + name, ".roi");
		String name2 = sd.getFileName();
		if (name2 == null)
			return false;
		String dir = sd.getDirectory();
		Roi roi = (Roi) rois.get(name);
		rois.remove(name);
		if (!name2.endsWith(".roi"))
			name2 = name2 + ".roi";
		String newName = name2.substring(0, name2.length() - 4);
		rois.put(newName, roi);
		ArrayList<Roi> sliceRois = getRoisByNumbers()
				.get(roi.getCPosition() + "_" + roi.getZPosition() + "_" + roi.getTPosition());
		if (sliceRois == null) {
			getRoisByNumbers().put(roi.getCPosition() + "_" + roi.getZPosition() + "_" + roi.getTPosition(),
					new ArrayList<Roi>());
			sliceRois = getRoisByNumbers()
					.get(roi.getCPosition() + "_" + roi.getZPosition() + "_" + roi.getTPosition());
		}
		sliceRois.add(roi);
		String rootName = newName.contains("\"") ? "\"" + roi.getName().split("\"")[1] + "\""
				: roi.getName().split("_")[0].trim();

		ArrayList<Roi> rootNameRois = getRoisByRootName().get(rootName);
		if (rootNameRois == null) {
			getRoisByRootName().put(rootName, new ArrayList<Roi>());
			rootNameRois = getRoisByRootName().get(rootName);
		}
		rootNameRois.add(roi);

		roi.setName(newName);
		recentName = newName;
		listModel.setElementAt(newName, indexes[0]);
		fullListModel.setElementAt(newName, indexes[0]);
		RoiEncoder re = new RoiEncoder(dir + name2);
		try {
			re.write(roi);
		} catch (IOException e) {
			IJ.error("Tag Manager", e.getMessage());
		}
		return true;
	}

	boolean saveMultiple(int[] indexes, String path) {
		Macro.setOptions(null);
		if (path == null) {
			SaveDialog sd = new SaveDialog("Save ROIs...",
					(imp.getShortTitle().contains("_scene")
							? imp.getShortTitle().substring(0, imp.getShortTitle().indexOf("_scene"))
							: imp.getShortTitle()) + "_" + "ROIs",
					".zip");
			String name = sd.getFileName();
			if (name == null)
				return false;
			if (!(name.endsWith(".zip") || name.endsWith(".ZIP")))
				name = name + ".zip";
			String dir = sd.getDirectory();
			path = dir + name;
		}
		try {
			ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(new File(path))));
			zos.setLevel(0);
			RoiEncoder re = new RoiEncoder(zos);
			ArrayList<String> namesAlreadySaved = new ArrayList<String>();
			for (int i = 0; i < indexes.length; i++) {
				String label = (String) listModel.getElementAt(indexes[i]);
				Roi roi = (Roi) rois.get(label);
				// Substituting updated color, Z, T, C info to saved name:
				String newColorString = Colors.colorToHexString(roi.getFillColor());
				String labelNew = label.replaceAll("(\".* \"_)(.*)", "$1" + newColorString
						+ "_" + roi.getCPosition() + "_" + roi.getZPosition() + "_" + roi.getTPosition());
				roi.setName(labelNew);
				if (!labelNew.endsWith(".roi"))
					labelNew += ".roi";
				int hitCount = 0;
				while (namesAlreadySaved.contains(labelNew)) {
					hitCount++;
					labelNew = labelNew.replaceAll("(.*)(-[0-9]+)?(.roi)", "$1-" + hitCount + "$3");
				}
				namesAlreadySaved.add(labelNew);
				zos.putNextEntry(new ZipEntry(labelNew));
				re.write(roi);
				zos.closeEntry();
				IJ.showStatus(""+i+ " tags written to .zip file");
			}
			zos.close();
			IJ.showStatus("Zip file complete.");

		} catch (IOException e) {
			error("" + e);
			return false;
		}
		if (record())
			Recorder.record("roiManager", "Save", path);
		return true;
	}

	boolean measure(int mode) {
		ImagePlus imp = this.imp;

		if (imp == null)
			return false;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		if (indexes.length == 0)
			return false;
		boolean allSliceOne = true;
		for (int i = 0; i < indexes.length; i++) {
			String label = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi) rois.get(label);
			if (getSliceNumber(roi, label) > 1)
				allSliceOne = false;
		}
		int measurements = Analyzer.getMeasurements();
		if (imp.getStackSize() > 1)
			Analyzer.setMeasurements(measurements | Measurements.SLICE);
		int currentSlice = imp.getCurrentSlice();
		for (int i = 0; i < indexes.length; i++) {
			if (restore(getImage(), indexes[i], !allSliceOne))
				IJ.run("Measure");
			else
				break;
		}
		imp.setSlice(currentSlice);
		Analyzer.setMeasurements(measurements);
		if (indexes.length > 1)
			IJ.run("Select None");
		if (record())
			Recorder.record("roiManager", "Measure");
		return true;
	}

	/*
	 * void showIndexes(int[] indexes) { for (int i=0; i<indexes.length; i++) {
	 * String label = list.getItem(indexes[i]); Roi roi = (Roi)rois.get(label);
	 * IJ.log(i+" "+roi.getName()); } }
	 */

	/*
	 * This method performs measurements for several ROI's in a stack and arranges
	 * the results with one line per slice. By constast, the measure() method
	 * produces several lines per slice. The results from multiMeasure() may be
	 * easier to import into a spreadsheet program for plotting or additional
	 * analysis. Based on the multi() method in Bob Dougherty's Multi_Measure plugin
	 * (http://www.optinav.com/Multi-Measure.htm).
	 */
	boolean multiMeasure() {
		ImagePlus imp = this.imp;

		if (imp == null)
			return false;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		if (indexes.length == 0)
			return false;
		int measurements = Analyzer.getMeasurements();

		int nSlices = imp.getStackSize();
		if (IJ.isMacro()) {
			if (nSlices > 1)
				measureAll = true;
			onePerSlice = true;
		} else {
			GenericDialog gd = new GenericDialog("Multi Measure");
			if (nSlices > 1)
				gd.addCheckbox("Measure All " + nSlices + " Slices", measureAll);
			gd.addCheckbox("One Row Per Slice", onePerSlice);
			int columns = getColumnCount(imp, measurements) * indexes.length;
			String str = nSlices == 1 ? "this option" : "both options";
			gd.setInsets(10, 25, 0);
			gd.addMessage("Enabling " + str + " will result\n" + "in a table with " + columns + " columns.");
			gd.showDialog();
			if (gd.wasCanceled())
				return false;
			if (nSlices > 1)
				measureAll = gd.getNextBoolean();
			onePerSlice = gd.getNextBoolean();
		}
		if (!measureAll)
			nSlices = 1;
		int currentSlice = imp.getCurrentSlice();

		if (!onePerSlice) {
			int measurements2 = nSlices > 1 ? measurements | Measurements.SLICE : measurements;
			ResultsTable rt = new ResultsTable();
			Analyzer analyzer = new Analyzer(imp, measurements2, rt);
			for (int slice = 1; slice <= nSlices; slice++) {
				if (nSlices > 1)
					imp.setSliceWithoutUpdate(slice);
				for (int i = 0; i < indexes.length; i++) {
					if (restoreWithoutUpdate(indexes[i]))
						analyzer.measure();
					else
						break;
				}
			}
			rt.show("Results");
			if (nSlices > 1)
				imp.setSlice(currentSlice);
			return true;
		}

		Analyzer aSys = new Analyzer(imp); // System Analyzer
		ResultsTable rtSys = Analyzer.getResultsTable();
		ResultsTable rtMulti = new ResultsTable();
		Analyzer aMulti = new Analyzer(imp, measurements, rtMulti); // Private Analyzer

		for (int slice = 1; slice <= nSlices; slice++) {
			int sliceUse = slice;
			if (nSlices == 1)
				sliceUse = currentSlice;
			imp.setSliceWithoutUpdate(sliceUse);
			rtMulti.incrementCounter();
			int roiIndex = 0;
			for (int i = 0; i < indexes.length; i++) {
				if (restoreWithoutUpdate(indexes[i])) {
					roiIndex++;
					aSys.measure();
					for (int j = 0; j <= rtSys.getLastColumn(); j++) {
						float[] col = rtSys.getColumn(j);
						String head = rtSys.getColumnHeading(j);
						String suffix = "" + roiIndex;
						Roi roi = imp.getRoi();
						if (roi != null) {
							String name = roi.getName();
							if (name != null && name.length() > 0
									&& (name.length() < 9 || !Character.isDigit(name.charAt(0))))
								suffix = "(" + name + ")";
						}
						if (head != null && col != null && !head.equals("Slice"))
							rtMulti.addValue(head + suffix, rtSys.getValue(j, rtSys.getCounter() - 1));
					}
				} else
					break;
			}
			// aMulti.displayResults();
			// aMulti.updateHeadings();
		}
		rtMulti.show("Results");

		imp.setSlice(currentSlice);
		if (indexes.length > 1)
			IJ.run("Select None");
		if (record())
			Recorder.record("roiManager", "Multi Measure");
		return true;
	}

	int getColumnCount(ImagePlus imp, int measurements) {
		ImageStatistics stats = imp.getStatistics(measurements);
		ResultsTable rt = new ResultsTable();
		Analyzer analyzer = new Analyzer(imp, measurements, rt);
		analyzer.saveResults(stats, null);
		int count = 0;
		for (int i = 0; i <= rt.getLastColumn(); i++) {
			float[] col = rt.getColumn(i);
			String head = rt.getColumnHeading(i);
			if (head != null && col != null)
				count++;
		}
		return count;
	}

	void multiPlot() {
		ImagePlus imp = this.imp;

		if (imp == null)
			return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		int n = indexes.length;
		if (n == 0)
			return;
		Color[] colors = { Color.blue, Color.green, Color.magenta, Color.red, Color.cyan, Color.yellow };
		if (n > colors.length) {
			colors = new Color[n];
			double c = 0;
			double inc = 150.0 / n;
			for (int i = 0; i < n; i++) {
				colors[i] = new Color((int) c, (int) c, (int) c);
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
		boolean freeYScale = fixedMin == 0.0 && fixedMax == 0.0;
		if (!freeYScale) {
			minY = fixedMin;
			maxY = fixedMax;
		}
		int maxX = 0;
		Calibration cal = imp.getCalibration();
		double xinc = cal.pixelWidth;
		for (int i = 0; i < indexes.length; i++) {
			if (!restore(getImage(), indexes[i], true))
				break;
			Roi roi = imp.getRoi();
			if (roi == null)
				break;
			if (roi.isArea() && roi.getType() != Roi.RECTANGLE)
				IJ.run(imp, "Area to Line", "");
			ProfilePlot pp = new ProfilePlot(imp, IJ.altKeyDown());
			y[i] = pp.getProfile();
			if (y[i] == null)
				break;
			if (y[i].length > maxX)
				maxX = y[i].length;
			if (freeYScale) {
				double[] a = Tools.getMinMax(y[i]);
				if (a[0] < minY)
					minY = a[0];
				if (a[1] > maxY)
					maxY = a[1];
			}
			double[] xx = new double[y[i].length];
			for (int j = 0; j < xx.length; j++)
				xx[j] = j * xinc;
			x[i] = xx;
		}
		String xlabel = "Distance (" + cal.getUnits() + ")";
		Plot plot = new Plot("Profiles", xlabel, "Value", x[0], y[0]);
		plot.setLimits(0, maxX * xinc, minY, maxY);
		for (int i = 1; i < indexes.length; i++) {
			plot.setColor(colors[i]);
			if (x[i] != null)
				plot.addPoints(x[i], y[i], Plot.LINE);
		}
		plot.setColor(colors[0]);
		if (x[0] != null)
			plot.show();
		imp.setSlice(currentSlice);
		if (indexes.length > 1)
			IJ.run("Select None");
		if (record())
			Recorder.record("roiManager", "Multi Plot");
	}

	boolean drawOrFill(int mode) {
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		ImagePlus imp = this.imp;
		imp.deleteRoi();
		ImageProcessor ip = null;
		Filler filler = mode == LABEL ? new Filler() : null;
		int slice = imp.getCurrentSlice();
		for (int i = 0; i < indexes.length; i++) {
			String name = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi) rois.get(name);
			int type = roi.getType();
			if (roi == null)
				continue;
			if (mode == FILL && (type == Roi.POLYLINE || type == Roi.FREELINE || type == Roi.ANGLE))
				mode = DRAW;
			if (roi.getZPosition() != 0) {
				imp.setPosition(roi.getCPosition(), roi.getZPosition(), roi.getTPosition());
				ip = imp.getProcessor();
				ip.setColor(Toolbar.getForegroundColor());
			}
			switch (mode) {
			case DRAW:
				roi.drawPixels(ip);
				break;
			case FILL:
				ip.fill(roi);
				break;
			case LABEL:
				roi.drawPixels(ip);
				filler.drawLabel(imp, ip, i + 1, roi.getBounds());
				break;
			}
		}
		ImageCanvas ic = imp.getCanvas();
		if (ic != null)
			ic.setShowAllROIs(false);
		imp.updateAndDraw();
		return true;
	}

	void setProperties(Color color, int lineWidth, Color fillColor) {
		boolean showDialog = color == null && lineWidth == -1 && fillColor == null;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		int n = indexes.length;
		if (n == 0)
			return;
		Roi rpRoi = null;
		String rpName = null;
		Font font = null;
		int justification = TextRoi.LEFT;
		double opacity = -1;
		String alphaPrefix = "";
		if (showDialog) {
			String label = (String) listModel.getElementAt(indexes[0]);
			rpRoi = (Roi) rois.get(label);
			if (n == 1) {
				fillColor = rpRoi.getFillColor();
				rpName = rpRoi.getName();
			}
			if (rpRoi.getStrokeColor() == null)
				rpRoi.setStrokeColor(ImageCanvas.getShowAllColor());
			rpRoi = (Roi) rpRoi.clone();
			if (n > 1)
				rpRoi.setName("range: " + (indexes[0] + 1) + "-" + (indexes[n - 1] + 1));
			else
				this.setRoiFillColor(rpRoi, fillColor != null ? fillColor : Colors.decode("#00000000", Color.black));

			RoiProperties rp = new RoiProperties("Properties", rpRoi);
			if (!rp.showDialog())
				return;
			lineWidth = (int) rpRoi.getStrokeWidth();
			defaultLineWidth = lineWidth;
			color = rpRoi.getStrokeColor();
			fillColor = rpRoi.getFillColor();
			if (Colors.colorToHexString(fillColor).endsWith("f0f0f0")) {
				alphaPrefix = Colors.colorToHexString(fillColor).substring(0, 3);
			}
//			defaultColor = color;
			if (rpRoi instanceof TextRoi) {
				font = ((TextRoi) rpRoi).getCurrentFont();
				justification = ((TextRoi) rpRoi).getJustification();
			}
			if (rpRoi instanceof ImageRoi)
				opacity = ((ImageRoi) rpRoi).getOpacity();
		}
		ImagePlus imp = this.imp;
		if (n == listModel.getSize() && n > 1 && !IJ.isMacro()) {
			GenericDialog gd = new GenericDialog("Tag Manager");
			gd.addMessage("Apply changes to all " + n + " selections?");
			gd.showDialog();
			if (gd.wasCanceled())
				return;
		}
		ColorLegend cl = this.getColorLegend();
		if (cl != null) {
//			ArrayList<Integer> hitIndexes = new ArrayList<Integer>();
//			Roi[] targetRois = getSelectedRoisAsArray();
//			if (targetRois.length<1)
//				targetRois = getFullRoisAsArray();
//			for (int i=0; i<n; i++) {
//				String label = (String) listModel.getElementAt(indexes[i]);
//				Color currentBBColor = targetRois[0].getFillColor();
//				String hexAlpha = Integer.toHexString(fillColor.getAlpha());
//				String hexRed = Integer.toHexString(fillColor.getRed());
//				String hexGreen = Integer.toHexString(fillColor.getGreen());
//				String hexBlue = Integer.toHexString(fillColor.getBlue());
//				String fillRGBstring = "#"+(hexAlpha.length()==1?"0":"")+hexAlpha
//						+(hexRed.length()==1?"0":"")+hexRed
//						+(hexGreen.length()==1?"0":"")+hexGreen
//						+(hexBlue.length()==1?"0":"")+hexBlue; 
//				int fillRGB = fillColor.getRGB();
//				int currentRGB = currentBBColor!=null?currentBBColor.getRGB():0;
//				if (fillRGB == currentRGB)
//					return;
//
//				while (cl.getBrainbowColors().contains(new Color(fillColor.getRGB())) && fillColor.getRGB() != currentRGB) {
//					if (fillColor.getBlue()<255) {
//						hexBlue = Integer.toHexString(fillColor.getBlue()+1);
//					} else if (fillColor.getGreen()<255) {
//						hexGreen = Integer.toHexString(fillColor.getGreen()+1);
//					} else if (fillColor.getRed()<255) {
//						hexRed = Integer.toHexString(fillColor.getRed()+1);
//					} 
//					fillRGBstring = "#"+(hexAlpha.length()==1?"0":"")+hexAlpha
//							+(hexRed.length()==1?"0":"")+hexRed
//							+(hexGreen.length()==1?"0":"")+hexGreen
//							+(hexBlue.length()==1?"0":"")+hexBlue; 
//					fillColor = Colors.decode(fillRGBstring, fillColor);
//				}
//				cl.getBrainbowColors().put(label.split(" ")[0].replace("\"","").toLowerCase(), new Color(fillColor.getRGB()));				
//				for (Checkbox cbC:cl.getCheckbox()) {
//					String cbcName=cbC.getName();
//					if (cbcName.equals(label.split(" ")[0].replace("\"",""))){
//						cbC.setBackground(new Color(fillColor.getRGB()));
//					}
//				}
//				for (int l=0; l<listModel.size(); l++) {
//					if (((String) listModel.getElementAt(l)).startsWith(label.split(" =")[0])) {
//						hitIndexes.add(l);
//					}
//				}
//			}
//			indexes = new int[hitIndexes.size()];
//			n=indexes.length;
//			for (int h=0;h<indexes.length;h++) {
//				indexes[h] = ((int)hitIndexes.get(h));
//			}
		}
		for (int i = 0; i < n; i++) {
			String label = (String) listModel.getElementAt(indexes[i]);
			Roi roi = (Roi) rois.get(label);
			// IJ.log("set "+color+" "+lineWidth+" "+fillColor);
			if (color != null)
				roi.setStrokeColor(color);
			if (lineWidth >= 0)
				roi.setStrokeWidth(lineWidth);
			if (alphaPrefix != "") {
				String iRoiColorStr = Colors.colorToHexString(roi.getFillColor());
				roi.setFillColor(Colors.decode(
						alphaPrefix + iRoiColorStr.substring(iRoiColorStr.length() - 6, iRoiColorStr.length()),
						fillColor));
			} else
				roi.setFillColor(fillColor);
			if (brainbowColors == null)
				brainbowColors = new Hashtable<String, Color>();
			if (fillColor != null)
				brainbowColors.put(label.split(" ")[0].replace("\"", "").toLowerCase(), new Color(fillColor.getRGB()));
			if (roi != null && (roi instanceof TextRoi)) {
				roi.setImage(imp);
				if (font != null)
					((TextRoi) roi).setCurrentFont(font);
				((TextRoi) roi).setJustification(justification);
				roi.setImage(null);
			}
			if (roi != null && (roi instanceof ImageRoi) && opacity != -1)
				((ImageRoi) roi).setOpacity(opacity);
			
			String newLabel ="";
			String roiWasName = roi.getName();
			if (roi.getName() != null && roi.getName().split("\"").length > 1){
				newLabel = "\"" + roi.getName().split("\"")[1].trim() + " \"" + "_" +Colors.colorToHexString(roi.getFillColor()) + "_" + roi.getCPosition() + "_"
						+ roi.getZPosition() + "_" + roi.getTPosition();
			}else if (roi.getName() != null){
				newLabel = "\"" + roi.getName() + " \"" + "_" +Colors.colorToHexString(roi.getFillColor()) + "_" + roi.getCPosition() + "_" + roi.getZPosition() + "_"
						+ roi.getTPosition();
			}

			newLabel = getUniqueName(newLabel);
			getListModel().set(getListModel().indexOf(roiWasName), newLabel);
			getFullListModel().set(getFullListModel().indexOf(roiWasName), newLabel);
			roi.setName(newLabel);
			getROIs().put(roi.getName(), roi);
			getROIs().remove(roiWasName);

			
			IJ.showStatus(""+i+"/"+n+" tags modified.");
		}
//		String[] newNames = new String[indexes.length];
//		for(int nn=0;nn<newNames.length;nn++) {
//			String label2 = (String) listModel.getElementAt(indexes[nn]);
//			newNames[nn]=label2;
//			String dummyString = label2.replaceAll("\".* \"_", "Dummy_");
//			String roiColorChunk = dummyString.split("_")[1];
//			String newColorName = roiColorChunk.startsWith("#")? 
//					label2.replace(roiColorChunk, Colors.colorToHexString(fillColor)):
//						label2.replace(" \"_", " \"_"+Colors.colorToHexString(fillColor));
//			newNames[nn] = newColorName;
//		}
//		rename(newNames,  indexes, true);

		if (rpRoi != null && rpName != null && !rpRoi.getName().equals(rpName))
			rename(rpRoi.getName(), null, true);
		ImageCanvas ic = imp != null ? imp.getCanvas() : null;
		Roi roi = imp != null ? imp.getRoi() : null;
		boolean showingAll = ic != null && ic.getShowAllROIs();
		if (roi != null && (n == 1 || !showingAll)) {
			if (lineWidth >= 0)
				roi.setStrokeWidth(lineWidth);
			if (color != null)
				roi.setStrokeColor(color);
			if (fillColor != null)
				this.setRoiFillColor(roi, fillColor);
			if (roi != null && (roi instanceof TextRoi)) {
				((TextRoi) roi).setCurrentFont(font);
				((TextRoi) roi).setJustification(justification);
			} else {
				IJ.log("nontext match: " + roi.getName());
			}
			if (roi != null && (roi instanceof ImageRoi) && opacity != -1)
				((ImageRoi) roi).setOpacity(opacity);
		}
		if (lineWidth > 1 && !showingAll && roi == null) {
			showAll(SHOW_ALL);
			showingAll = true;
		}
		if (imp != null)
			imp.draw();
		if (record()) {
			if (fillColor != null)
				Recorder.record("roiManager", "Set Fill Color", Colors.colorToString(fillColor));
			else {
				Recorder.record("roiManager", "Set Color", Colors.colorToString(color != null ? color : Color.red));
				Recorder.record("roiManager", "Set Line Width", lineWidth);
			}
		}
		list.repaint();
	}

	void flatten() {
		ImagePlus imp = this.imp;
		if (imp == null) {
			IJ.noImage();
			return;
		}
		ImageCanvas ic = imp.getCanvas();
		if (!ic.getShowAllROIs() && ic.getDisplayList() == null && imp.getRoi() == null)
			error("Image does not have an overlay or ROI");
		else
			IJ.doCommand("Flatten"); // run Image>Flatten in separate thread
	}

	public boolean getDrawLabels() {
		return labelsCheckbox.isSelected();
	}

	void combine() {
		ImagePlus imp = this.imp;
		if (imp == null)
			return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		int nPointRois = 0;
		for (int i = 0; i < indexes.length; i++) {
			Roi roi = (Roi) rois.get(listModel.getElementAt(indexes[i]));
			if (roi.getType() == Roi.POINT)
				nPointRois++;
			else
				break;
		}
		if (nPointRois == indexes.length)
			combinePoints(imp, indexes);
		else
			combineRois(imp, indexes);
		if (record())
			Recorder.record("roiManager", "Combine");
	}

	void combineRois(ImagePlus imp, int[] indexes) {
		ShapeRoi s1 = null, s2 = null;
		ImageProcessor ip = null;
		for (int i = 0; i < indexes.length; i++) {
			Roi roi = (Roi) rois.get(listModel.getElementAt(indexes[i]));
			if (!roi.isArea() && !(roi instanceof Arrow)) {
				if (ip == null)
					ip = new ByteProcessor(imp.getWidth(), imp.getHeight());
				roi = convertLineToPolygon(roi, ip);
			}
			if (roi instanceof Arrow) {
				roi = ((Arrow) roi).getShapeRoi();
			}
			if (s1 == null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi) roi;
				else
					s1 = new ShapeRoi(roi);
				if (s1 == null)
					return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi) roi;
				else
					s2 = new ShapeRoi(roi);
				if (s2 == null)
					continue;
				s1.or(s2);
			}
		}
		if (s1 != null)
			imp.setRoi(s1);
	}

	Roi convertLineToPolygon(Roi lRoi, ImageProcessor ip) {
		if (lRoi == null)
			return null;
		Roi pRoi = new PolygonRoi(((Line) lRoi).getPolygon(), Roi.POLYGON);
		return pRoi;
		// ip.resetRoi();
		// ip.setColor(0);
		// ip.fill();
		// ip.setColor(255);
		// if (roi.getType()==Roi.LINE && roi.getStrokeWidth()>1)
		// ip.fillPolygon(roi.getPolygon());
		// else
		// roi.drawPixels(ip);
		// //new ImagePlus("ip", ip.duplicate()).show();
		// ip.setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
		// ThresholdToSelection tts = new ThresholdToSelection();
		// return tts.convert(ip);
	}

	void combinePoints(ImagePlus imp, int[] indexes) {
		int n = indexes.length;
		Polygon[] p = new Polygon[n];
		int points = 0;
		for (int i = 0; i < n; i++) {
			Roi roi = (Roi) rois.get(listModel.getElementAt(indexes[i]));
			p[i] = roi.getPolygon();
			points += p[i].npoints;
		}
		if (points == 0)
			return;
		int[] xpoints = new int[points];
		int[] ypoints = new int[points];
		int index = 0;
		for (int i = 0; i < p.length; i++) {
			for (int j = 0; j < p[i].npoints; j++) {
				xpoints[index] = p[i].xpoints[j];
				ypoints[index] = p[i].ypoints[j];
				index++;
			}
		}
		imp.setRoi(new PointRoi(xpoints, ypoints, xpoints.length));
	}

	void and() {
		ImagePlus imp = this.imp;
		if (imp == null)
			return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		ShapeRoi s1 = null, s2 = null;
		for (int i = 0; i < indexes.length; i++) {
			Roi roi = (Roi) rois.get(listModel.getElementAt(indexes[i]));
			if (!roi.isArea())
				continue;
			if (s1 == null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi) roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1 == null)
					return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi) roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2 == null)
					continue;
				s1.and(s2);
			}
		}
		if (s1 != null)
			imp.setRoi(s1);
		if (record())
			Recorder.record("roiManager", "AND");
	}

	void xor() {
		ImagePlus imp = this.imp;
		if (imp == null)
			return;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 1) {
			error("More than one item must be selected, or none");
			return;
		}
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		ShapeRoi s1 = null, s2 = null;
		for (int i = 0; i < indexes.length; i++) {
			Roi roi = (Roi) rois.get(listModel.getElementAt(indexes[i]));
			if (!roi.isArea())
				continue;
			if (s1 == null) {
				if (roi instanceof ShapeRoi)
					s1 = (ShapeRoi) roi.clone();
				else
					s1 = new ShapeRoi(roi);
				if (s1 == null)
					return;
			} else {
				if (roi instanceof ShapeRoi)
					s2 = (ShapeRoi) roi.clone();
				else
					s2 = new ShapeRoi(roi);
				if (s2 == null)
					continue;
				s1.xor(s2);
			}
		}
		if (s1 != null)
			imp.setRoi(s1);
		if (record())
			Recorder.record("roiManager", "XOR");
	}

	void addParticles() {
		String err = IJ.runMacroFile("ij.jar:AddParticles", null);
		if (err != null && err.length() > 0)
			error(err);
	}

	void sort() {
		busy = true;
		// int n = rois.size();
		// if (n==0) return;
		String[] labels = new String[listModel.getSize()];
		for (int i = 0; i < labels.length; i++)
			labels[i] = (String) listModel.get(i);
		String[] fullLabels = new String[fullListModel.getSize()];
		for (int i = 0; i < fullLabels.length; i++)
			fullLabels[i] = (String) fullListModel.get(i);

		int index = 0;
		// for (Enumeration en=rois.keys(); en.hasMoreElements();)
		// labels[index++] = (String)en.nextElement();
		listModel.removeAllElements();
		fullListModel.removeAllElements();
		// this.setTitle( "Tag Manager SORTING!!!") ;

		if (sortmode > -1) {
			RoiLabelByNumbersSorter.sort(labels, sortmode);
			RoiLabelByNumbersSorter.sort(fullLabels, sortmode);

		} else {   //why on earth use this pig of a sorter instead of Arrays.sort()?
			StringSorter.sort(labels);
			StringSorter.sort(fullLabels);

		}
		int numSorted = 0;
		// Dimension dim = list.getSize();
		// list.setSize(0,0);
		for (int i = 0; i < labels.length; i++) {
			// this.setTitle( "Tag Manager" + ((numSorted%100>50)?" SORTING!!!":"
			// Sorting...") );
			listModel.addElement(labels[i]);
			numSorted++;
		}
		// list.setSize(dim);
		for (int i = 0; i < fullLabels.length; i++) {
			fullListModel.addElement(fullLabels[i]);
		}
		// this.setTitle( "Tag Manager" );

		if (record())
			Recorder.record("roiManager", "Sort");
		busy = false;

	}

	void specify() {
		try {
			IJ.run("Specify...");
		} catch (Exception e) {
			return;
		}
		runCommand("add");
	}

	void removeSliceInfo() {
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		for (int i = 0; i < indexes.length; i++) {
			int index = indexes[i];
			String name = (String) listModel.getElementAt(index);
			int n = getSliceNumber(name);
			if (n == -1)
				continue;
			String name2 = name.substring(5, name.length());
			name2 = getUniqueName(name2);
			Roi roi = (Roi) rois.get(name);
			rois.remove(name);
			roi.setName(name2);
			recentName = name2;
			roi.setPosition(0, 0, 0);
			rois.put(name2, roi);
			setUpRoisByNameAndNumbers(roi);
		}
	}

	void help() {
		String macro = "run('URL...', 'url=" + IJ.URL + "/docs/menus/analyze.html#manager');";
		new MacroRunner(macro);
	}

	void options() {
		Color c = ImageCanvas.getShowAllColor();
		GenericDialog gd = new GenericDialog("Options");
		// gd.addPanel(makeButtonPanel(gd), GridBagConstraints.CENTER, new Insets(5, 0,
		// 0, 0));
		gd.addCheckbox("Associate \"Show All\" ROIs with slices", Prefs.showAllSliceOnly);
		gd.addCheckbox("Restore ROIs centered", restoreCentered);
		gd.addCheckbox("Use ROI names as labels", Prefs.useNamesAsLabels);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (c != ImageCanvas.getShowAllColor())
				ImageCanvas.setShowAllColor(c);
			return;
		}
		Prefs.showAllSliceOnly = gd.getNextBoolean();
		restoreCentered = gd.getNextBoolean();
		Prefs.useNamesAsLabels = gd.getNextBoolean();
		ImagePlus imp = this.imp;
		if (imp != null)
			imp.draw();
		if (record()) {
			Recorder.record("roiManager", "Associate", Prefs.showAllSliceOnly ? "true" : "false");
			Recorder.record("roiManager", "Centered", restoreCentered ? "true" : "false");
			Recorder.record("roiManager", "UseNames", Prefs.useNamesAsLabels ? "true" : "false");
		}
	}

	Panel makeButtonPanel(GenericDialog gd) {
		Panel panel = new Panel();
		// buttons.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
		colorButton = new JButton("\"Show All\" Color...");
		colorButton.addActionListener(this);
		panel.add(colorButton);
		return panel;
	}

	void setShowAllColor() {
		ColorChooser cc = new ColorChooser("\"Show All\" Color", ImageCanvas.getShowAllColor(), false);
		ImageCanvas.setShowAllColor(cc.getColor());
	}

	void split() {
		ImagePlus imp = this.imp;
		if (imp == null)
			return;
		Roi roi = imp.getRoi();
		if (roi == null || roi.getType() != Roi.COMPOSITE) {
			error("Image with composite selection required");
			return;
		}
		boolean record = Recorder.record;
		Recorder.record = false;
		Roi[] rois = ((ShapeRoi) roi).getRois();
		for (int i = 0; i < rois.length; i++) {
			imp.setRoi(rois[i]);
			addRoi(false);
		}
		Recorder.record = record;
		if (record())
			Recorder.record("roiManager", "Split");
	}

	public void showAll(int mode) {
		ImagePlus imp = this.imp;
		if (imp == null) {
			error("Linked image is not open.");
			return;
		}
		ImageCanvas ic = imp.getCanvas();
		if (ic == null)
			return;
		showAll = mode == SHOW_ALL;
		boolean showOwn = mode == SHOW_OWN;
		// if (showAll) {
		// list.removeAll();
		// for (String item:fullList.getItems() ) {
		// list.add(item);
		// }
		// }
		if (mode == LABELS) {
			showAll = true;
			if (record())
				Recorder.record("roiManager", "Show All with labels");
		} else if (mode == NO_LABELS) {
			showAll = true;
			if (record())
				Recorder.record("roiManager", "Show All without labels");
		}
		// if (showOwn) {
		// showAll = showAllCheckbox.isSelected();
		// List tempList = list;
		// tempList.removeAll();
		// for (String item:list.getItems() ) {
		// tempList.add(item);
		// }
		// String[] listStrings = tempList.getItems();
		// for (int i=0; i<listStrings.length; i++) {
		// Roi roi = (Roi)rois.get(tempList.getItem(i));
		// if (roi.getMotherImp() == imp) {
		// tempList.deselect(i);
		// } else {
		// tempList.select(i);
		// }
		// }
		// String[] killList = tempList.getSelectedItems();
		// int killCount = killList.length;
		// while (killCount >0) {
		// tempList.remove(killList[killCount-1]);
		// killCount--;
		// }
		// list = tempList;
		// }

		if (showAll)
			imp.deleteRoi();
		ic.setShowAllROIs(showAll);
		ic.setShowOwnROIs(showOwn);
		if (record())
			Recorder.record("roiManager", showAll ? "Show All" : "Show None");
		imp.draw();
	}

	void updateShowAll() {
		ImagePlus imp = this.imp;
		if (imp == null)
			return;
		ImageCanvas ic = imp.getCanvas();
		if (ic != null && ic.getShowAllROIs())
			imp.draw();
	}

	int[] getAllShownIndexes() {
		int count = listModel.getSize();
		int[] indexes = new int[count];
		for (int i = 0; i < count; i++)
			indexes[i] = i;
		return indexes;
	}

	int[] getFullListIndexes() {
		int count = fullListModel.getSize();
		int[] indexes = new int[count];
		for (int i = 0; i < count; i++)
			indexes[i] = i;
		return indexes;
	}

	ImagePlus getImage() {
		ImagePlus imp = this.imp;
		if (imp == null) {
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
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			instance = null;
		}
		if (!IJ.isMacro())
			ignoreInterrupts = false;
	}

	/**
	 * Returns a reference to the Tag Manager or null if it is not open.
	 */
	public static RoiManager getInstance(ImagePlus queryImp) {
		if (queryImp != null) {
			for (int i = 0; i < WindowManager.getNonImageWindows().length; i++) {
				Frame frame = WindowManager.getNonImageWindows()[i];
				// IJ.log(frame.toString()+" \n"+queryImp.toString()+" \n"
				// + ((frame instanceof RoiManager)?((RoiManager)
				// frame).getImagePlus().toString():"None are rm\n")
				// +" "+/*((((RoiManager)frame).getImagePlus() ==
				// queryImp)?"YES!":"nope"+*/"\n");
				if ((frame instanceof RoiManager) && ((RoiManager) frame).getImagePlus() == queryImp) {
					// IJ.log("YES");
					return (RoiManager) frame;
				} else {
					// IJ.log("NO");
					return null;
				}
			}
			return null;
		} else
			return null;
	}

	/**
	 * Returns a reference to the Tag Manager window or to the macro batch mode
	 * RoiManager, or null if neither exists.
	 */
	public static RoiManager getInstance2() {
		RoiManager rm = getInstance(WindowManager.getCurrentImage());
		if (rm == null && IJ.isMacro())
			rm = Interpreter.getBatchModeRoiManager();
		return rm;
	}

	/**
	 * Returns the ROI Hashtable.
	 * 
	 * @see getCount
	 * @see getRoisAsArray
	 */
	public Hashtable<String, Roi> getROIs() {
		return rois;
	}

	public Hashtable<String, ArrayList<Roi>> getROIsByNumbers() {
		return getRoisByNumbers();
	}

	/**
	 * Returns the selection list.
	 * 
	 * @see getCount
	 * @see getRoisAsArray
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
		for (int i = 0; i < n; i++) {
			String label = (String) listModel.getElementAt(i);
			array[i] = (Roi) rois.get(label);
		}
		return array;
	}

	/**
	 * Returns the shown (searched) set of ROIs for the specifice slice/frame as an
	 * array.
	 */
	private Roi[] getDisplayedRoisAsArray(int z, int t) {
		Roi[] roiSetIn = this.getShownRoisAsArray();
		if (roiSetIn == null)
			return null;
		ArrayList<Roi> matchedRois = new ArrayList<Roi>();
		for (int i = 0; i < roiSetIn.length; i++) {
			// IJ.log(( roiSetIn[i].getZPosition() +" "+ z +" "+ roiSetIn[i].getTPosition()
			// +" "+ t+"\n"));
			if (roiSetIn[i].getZPosition() > z - zSustain && roiSetIn[i].getZPosition() < z + zSustain
					&& roiSetIn[i].getTPosition() > t - tSustain && roiSetIn[i].getTPosition() < t + tSustain) {
				matchedRois.add(roiSetIn[i]);
				// IJ.showMessage("");

			}
		}
		// IJ.log(""+matchedRoiIndexes.size());

		Roi[] displayedRois = new Roi[matchedRois.size()];
		for (int i = 0; i < displayedRois.length; i++) {
			displayedRois[i] = matchedRois.get(i);
		}
		return displayedRois;

	}

	/** Returns the full set of ROIs as an array. */
	public Roi[] getFullRoisAsArray() {
		int n = fullListModel.getSize();
		Roi[] array = new Roi[n];
		for (int i = 0; i < n; i++) {
			String label = (String) fullListModel.getElementAt(i);
			if (rois != null)
				array[i] = (Roi) rois.get(label);
		}
		return array;
	}

	/** Returns the selected ROIs as an array. */
	public Roi[] getSelectedRoisAsArray() {
		int[] indexes = getSelectedIndexes();
		int n = indexes.length;
		Roi[] array = new Roi[n];
		for (int i = 0; i < n; i++) {
			String label = (String) listModel.getElementAt(indexes[i]);
			array[i] = (Roi) rois.get(label);
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
			return (clonedArray);
		} else
			return null;
	}

	public Roi[] getOriginalRoisAsArray() {
		if (originalsCloned) {
			return originalRois;
		} else
			return null;
	}

	/**
	 * Returns the name of the ROI with the specified index, or null if the index is
	 * out of range.
	 */
	public String getName(int index) {
		if (index >= 0 && index < listModel.getSize())
			return (String) listModel.getElementAt(index);
		else
			return null;
	}

	/**
	 * Returns the name of the ROI with the specified index. Can be called from a
	 * macro using
	 * 
	 * <pre>
	 * call("ij.plugin.frame.RoiManager.getName", index)
	 * </pre>
	 * 
	 * Returns "null" if the Tag Manager is not open or index is out of range.
	 */
	public static String getName(String index, String impIDstring) {
		int i = (int) Tools.parseDouble(index, -1);
		int impID = (int) Tools.parseDouble(impIDstring, -1);
		RoiManager instance = WindowManager.getImage(impID).getRoiManager();
		if (instance != null && i >= 0 && i < instance.listModel.getSize())
			return (String) instance.listModel.getElementAt(i);
		else
			return "null";
	}

	/**
	 * Executes the Tag Manager "Add", "Add & Draw", "Update", "Delete", "Measure",
	 * "Draw", "Show All", Show None", "Fill", "Deselect", "Select All", "Combine",
	 * "AND", "XOR", "Split", "Sort" or "Multi Measure" command. Returns false if
	 * <code>cmd</code> is not one of these strings.
	 */
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
			Roi roi = imp != null ? imp.getRoi() : null;
			if (roi != null)
				roi.setPosition(imp, 0);
			add(roi, shift, alt, false);
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
			if (WindowManager.getCurrentImage() != null) {
				showAll(SHOW_ALL);
				showAllCheckbox.setSelected(true);
			}
		} else if (cmd.equals("show none")) {
			if (WindowManager.getCurrentImage() != null) {
				showAll(SHOW_NONE);
				showAllCheckbox.setSelected(false);
			}
		} else if (cmd.equals("show all with labels")) {
			labelsCheckbox.setSelected(true);
			showAll(LABELS);
			if (Interpreter.isBatchMode())
				IJ.wait(250);
		} else if (cmd.equals("show all without labels")) {
			labelsCheckbox.setSelected(false);
			showAll(NO_LABELS);
			if (Interpreter.isBatchMode())
				IJ.wait(250);
		} else if (cmd.equals("deselect") || cmd.indexOf("all") != -1) {
			if (IJ.isMacOSX())
				ignoreInterrupts = true;
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
			// IJ.log("Debug: "+debugCount);
			// for (int i=0; i<debugCount; i++)
			// IJ.log(debug[i]);
		} else if (cmd.equals("enable interrupts")) {
			ignoreInterrupts = false;
		} else
			ok = false;
		macro = false;
		return ok;
	}

	/**
	 * Executes the Tag Manager "Open", "Save" or "Rename" command. Returns false if
	 * <code>cmd</code> is not "Open", "Save" or "Rename", or if an error occurs.
	 */
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
			if (listModel.getSize() == 0)
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
				fillColor = JColorChooser.showDialog(this.getFocusOwner(),
						"Pick a color for " + this.getSelectedRoisAsArray()[0].getName() + "...",
						Colors.decode("#" + name, Color.cyan));
				if (name.length() == 8) {
					String alphaCorrFillColorString = Colors.colorToHexString(fillColor).replaceAll("#",
							"#" + name.substring(0, 2));
					fillColor = Colors.decode(alphaCorrFillColorString, fillColor);
				}
			}
			setProperties(null, -1, fillColor);
			macro = false;
			return true;
		} else if (cmd.equals("set line width")) {
			int lineWidth = (int) Tools.parseDouble(name, 0);
			if (lineWidth >= 0)
				setProperties(null, lineWidth, null);
			macro = false;
			return true;
		} else if (cmd.equals("associate")) {
			Prefs.showAllSliceOnly = name.equals("true") ? true : false;
			macro = false;
			return true;
		} else if (cmd.equals("centered")) {
			restoreCentered = name.equals("true") ? true : false;
			macro = false;
			return true;
		} else if (cmd.equals("usenames")) {
			Prefs.useNamesAsLabels = name.equals("true") ? true : false;
			macro = false;
			if (labelsCheckbox.isSelected()) {
				ImagePlus imp = this.imp;
				if (imp != null)
					imp.draw();
			}
			return true;
		}
		return false;
	}

	/**
	 * Adds the current selection to the Tag Manager, using the specified color (a 6
	 * digit hex string) and line width.
	 */
	public boolean runCommand(String cmd, String hexColor, double lineWidth) {
		ImagePlus imp = this.imp;
		Roi roi = imp != null ? imp.getRoi() : null;
		if (roi != null)
			roi.setPosition(imp, 0);
		if (hexColor == null && lineWidth == 1.0 && (IJ.altKeyDown() && !Interpreter.isBatchMode()))
			addRoi(true);
		else {
			Color color = hexColor != null ? Colors.decode(hexColor, Color.cyan) : null;
			addRoi(null, false, color, color, (int) Math.round(lineWidth), true);
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
		if (index < 0) {
			list.clearSelection();
			if (record())
				Recorder.record("roiManager", "Deselect");
			return;
		}
		if (index >= n)
			return;
		if (IJ.shiftKeyDown()) {
			list.addSelectionInterval(index, index);
		} else {
			list.clearSelection();
			list.setSelectedIndex(index);
		}
		list.ensureIndexIsVisible(index);

		if (imp == null)
			imp = getImage();
		if (list.getSelectedIndices().length <= 1) {

			restore(imp, index, true);
			if (aceTree != null) {
				Roi cRoi = rois.get(listModel.get(getSelectedIndexes()[0]));
				VTreeImpl vti = aceTree.getVtree().getiVTreeImpl();
				if (vti != null && vti.getTestCanvas() != null) {
					for (int i = 0; i < vti.iCellLines.size(); i++) {
						Object o = vti.iCellLines.get(i);
						if (!(o instanceof CellLine))
							continue;
						CellLine cL = (CellLine) o;
						org.rhwlab.tree.Cell c = cL.c;
						if (c.getName()
								.equals(listModel.get(getSelectedIndexes()[0]).replace("\"", "").split(" ")[0])) {
							int cStart = c.getTime();
							int cEnd = c.getEndTime();
							int rTime = cRoi.getTPosition();
							if (cStart <= rTime && cEnd >= rTime) {
								c.setIntTime(rTime);
								for (MouseListener ml : vti.getTestCanvas().getMouseListeners()) {
									if (ml != this) {
										ml.mouseClicked(new MouseEvent(vti.getTestCanvas(), 0, 0, 0,
												c.getIntTime() + 100, cL.y1 + 5, 1, false, MouseEvent.BUTTON1));
									}
								}
							}
							break;
						}
					}
				}
			}
		}
		if (this.isVisible()) {
			list.revalidate();
		}
		lastSelectedIndex = index;
	}

	public void select(int index, boolean shiftKeyDown, boolean altKeyDown) {
		if (!(shiftKeyDown || altKeyDown))
			select(index);
		ImagePlus imp = this.imp;
		if (imp == null)
			return;
		Roi previousRoi = imp.getRoi();
		if (previousRoi == null) {
			IJ.setKeyUp(IJ.ALL_KEYS);
			select(imp, index);
			return;
		}
		Roi.previousRoi = (Roi) previousRoi.clone();
		String label = (String) listModel.getElementAt(index);
		list.setSelectedIndices(getSelectedIndexes());
		Roi roi = (Roi) rois.get(label);
		if (roi != null) {
			roi.setImage(imp);
			roi.update(shiftKeyDown, altKeyDown);
		}
	}

	public void setEditMode(ImagePlus imp, boolean editMode) {
		ImageCanvas ic = imp.getCanvas();
		boolean showAll = false;
		if (ic != null) {
			showAll = ic.getShowAllROIs() | editMode;
			ic.setShowAllROIs(showAll);
			imp.draw();
		}
		showAllCheckbox.setSelected(showAll);
		labelsCheckbox.setSelected(editMode);
	}

	/** Overrides PlugInFrame.close(). */
	public void close() {
		super.setVisible(false);
		// super.close();
		// instance = null;
		Prefs.saveLocation(LOC_KEY, getLocation());
	}

	/** Moves all the ROIs to the specified image's overlay. */
	public void moveRoisToOverlay(ImagePlus imp) {
		Roi[] rois = getShownRoisAsArray();
		int n = rois.length;
		Overlay overlay = new Overlay();
		ImageCanvas ic = imp.getCanvas();
		Color color = ic != null ? ic.getShowAllColor() : null;
		for (int i = 0; i < n; i++) {
			Roi roi = (Roi) rois[i].clone();
			if (!Prefs.showAllSliceOnly)
				roi.setPosition(imp, 0);
			if (color != null && roi.getStrokeColor() == null)
				roi.setStrokeColor(color);
			if (roi.getStrokeWidth() == 1)
				roi.setStrokeWidth(0);
			overlay.add(roi);
		}
		if (labelsCheckbox.isSelected()) {
			overlay.drawLabels(true);
			overlay.drawBackgrounds(true);
			overlay.setLabelColor(Color.white);
		}
		imp.setOverlay(overlay);
	}

	/** Overrides PlugInFrame.dispose(). */
	public void dispose() {
		synchronized (this) {
			done = true;
			notifyAll();
		}
		if (rois != null) {
			for (Roi roi : rois.values()) {
				roi.setImage(null);
				roi.setMotherImp(null);
			}
			rois.clear();
			rois = null;
		}
		if (originalRois != null) {
			for (Roi roi : originalRois) {
				roi.setImage(null);
				roi.setMotherImp(null);
			}
			originalRois = null;
		}
		if (getRoisByNumbers() != null) {
			for (ArrayList<Roi> roiAL : getRoisByNumbers().values()) {
				for (Roi roi : roiAL) {
					roi.setImage(null);
					roi.setMotherImp(null);
				}
			}
			setRoisByNumbers(null);
		}
		if (getRoisByRootName() != null) {
			for (ArrayList<Roi> roiAL : getRoisByRootName().values()) {
				for (Roi roi : roiAL) {
					roi.setImage(null);
					roi.setMotherImp(null);
				}
			}
			setRoisByRootName(null);
		}
		if (list != null) {
			list.removeKeyListener(IJ.getInstance());
			while (list.getMouseWheelListeners().length > 0) {
				list.removeMouseWheelListener(list.getMouseWheelListeners()[0]);
			}
		}
		if (textFindingField != null) {
			textFindingField.getDocument().removeDocumentListener(this);
		}
		if (aceTree != null && aceTree.getVtree() != null && aceTree.getVtree().getiVTreeImpl() != null
				&& aceTree.getVtree().getiVTreeImpl().getTestCanvas() != null) {
			aceTree.getVtree().getiVTreeImpl().getTestCanvas().removeMouseListener(this);
			aceTree.exit();
		}

		list = null;
		listModel = null;
		fullList = null;
		fullListModel = null;

		if (this.colorLegend != null) {
			if (imp != null) {
				if (imp.getWindow() != null) {
					imp.getWindow().removeFocusListener(colorLegend);
					imp.getWindow().removeWindowListener(colorLegend);
				}
			}
			colorLegend.setRoiManager(null);
			colorLegend.dispose();
			WindowManager.removeWindow(colorLegend);
		}
		this.removeKeyListener(IJ.getInstance());
		this.removeFocusListener(this);
		this.removeWindowListener(this);
		this.removeMouseListener(this);
		this.removeMouseWheelListener(this);
		while (this.getComponentCount() > 0 && this.getComponents()[0] != null) {
			Component comp = this.getComponents()[0];
			if (comp instanceof Panel) {
				while (((Panel) comp).getComponentCount() > 0 && ((Panel) comp).getComponents()[0] != null) {
					Component pComp = ((Panel) comp).getComponents()[0];
					if (pComp instanceof JButton) {
						while (((Button) pComp).getActionListeners().length > 0) {
							((Button) pComp).removeActionListener(((Button) pComp).getActionListeners()[0]);
						}
						while (((Button) pComp).getMouseListeners().length > 0) {
							((Button) pComp).removeMouseListener(((Button) pComp).getMouseListeners()[0]);
						}
					}
					if (pComp instanceof JTextField) {
						while (((JTextField) pComp).getActionListeners().length > 0
								&& ((JTextField) pComp).getActionListeners()[0] != null) {
							((JTextField) pComp).removeActionListener(((JTextField) pComp).getActionListeners()[0]);
						}
					}
					if (pComp instanceof Choice) {
						while (((Choice) pComp).getItemListeners().length > 0) {
							((Choice) pComp).removeItemListener(((Choice) pComp).getItemListeners()[0]);
						}
					}
					if (pComp instanceof JCheckBox) {
						while (((Checkbox) pComp).getItemListeners().length > 0) {
							((Checkbox) pComp).removeItemListener(((Checkbox) pComp).getItemListeners()[0]);
						}
					}
					((Panel) comp).remove(pComp);
				}
			}
			if (comp instanceof JButton) {
				while (((Button) comp).getActionListeners().length > 0) {
					((Button) comp).removeActionListener(((Button) comp).getActionListeners()[0]);
				}
				while (((Button) comp).getMouseListeners().length > 0) {
					((Button) comp).removeMouseListener(((Button) comp).getMouseListeners()[0]);
				}
			}
			if (comp instanceof JTextField) {
				while (((JTextField) comp).getActionListeners().length > 0) {
					((JTextField) comp).removeActionListener(((JTextField) comp).getActionListeners()[0]);
				}
			}
			if (comp instanceof Choice) {
				while (((Choice) comp).getItemListeners().length > 0) {
					((Choice) comp).removeItemListener(((Choice) comp).getItemListeners()[0]);
				}
			}
			if (comp instanceof JCheckBox) {
				while (((Checkbox) comp).getItemListeners().length > 0) {
					((Checkbox) comp).removeItemListener(((Checkbox) comp).getItemListeners()[0]);
				}
			}

			while ((comp).getKeyListeners().length > 0) {
				(comp).removeKeyListener((comp).getKeyListeners()[0]);
			}
			this.remove(comp);
		}
		if (pm != null) {
			while (pm.getComponentCount() > 0) {
				if (pm.getComponent(0) instanceof JMenuItem) {
					while ((((JMenuItem) pm.getComponent(0))).getActionListeners().length > 0) {
						((JMenuItem) pm.getComponent(0))
								.removeActionListener((((JMenuItem) pm.getComponent(0)).getActionListeners()[0]));
					}
					pm.remove(0);
				}
			}
			this.remove(pm);
		}

		// thread.interrupt();
		// thread = null;
		// this.imp.getWindow().removeWindowListener(this);
		this.imp = null;
		WindowManager.removeWindow(this);
		super.dispose();
	}

	public void mousePressed(MouseEvent e) {
		int x = e.getX(), y = e.getY();
		if (e.isPopupTrigger() || e.isMetaDown())
			pm.show(e.getComponent(), x, y);
	}

	public void mouseWheelMoved(MouseWheelEvent event) {
		if (event.getSource() == list) {
			ToolTipManager.sharedInstance().setDismissDelay(0);
			ToolTipManager.sharedInstance().setInitialDelay(Integer.MAX_VALUE);
		}

		synchronized (this) {
			int index = list.getSelectedIndex();
			if (index < 0)
				index = lastSelectedIndex;
			int rot = event.getWheelRotation();
			if (rot < -1)
				rot = -1;
			if (rot > 1)
				rot = 1;
			index += rot;
			if (index >= listModel.getSize())
				index = listModel.getSize();
//			IJ.log(index+"  "+rot+"  "+event.getSource());
			select(index);
			event.consume();
			if (IJ.isWindows())
				list.requestFocusInWindow();
		}
	}

	/**
	 * Temporarily selects multiple ROIs, where 'indexes' is an array of integers,
	 * each greater than or equal to 0 and less than the value returned by
	 * getCount(). The selected ROIs are not highlighted in the Tag Manager list and
	 * are no longer selected after the next Tag Manager command is executed.
	 */
	public void setSelectedIndexes(int[] indexes) {
		int count = getCount();
		if (count == 0)
			return;
		for (int i = 0; i < indexes.length; i++) {
			if (indexes[i] < 0)
				indexes[i] = 0;
			if (indexes[i] >= count)
				indexes[i] = count - 1;
		}
		selectedIndexes = indexes;
	}

	private int[] getSelectedIndexes() {
		if (selectedIndexes != null) {
			int[] indexes = selectedIndexes;
			selectedIndexes = null;
			return indexes;
		} else
			return list.getSelectedIndices();
	}

	private boolean record() {
		return Recorder.record && !IJ.isMacro();
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseClicked(MouseEvent e) {
		if (e.getSource() instanceof TestCanvas) {
			org.rhwlab.tree.Cell cell = ((TestCanvas) e.getSource()).findIt(e.getX(), e.getY());
			if (cell == null)
				return;
			int cellIndex = -1;
			for (int i = 0; i < listModel.getSize(); i++) {
				String tagName = "\"" + cell.getName() + " \"_.+_.+_" + cell.getIntTime() + ".*";
				if (listModel.get(i).matches(tagName)) {
					cellIndex = i;
				}
			}
			this.select(cellIndex);
		} 

		if (e.getSource() instanceof JList) {
			JList clickedList = (JList)e.getSource();
			if (e.getClickCount() > 1) {
				new Zoom().run("to");
			}
		}
	}

	public void mouseEntered(MouseEvent e) {
		if (e.getSource() == list) {
			toolTipDefaultDismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
			toolTipDefaultInitialDelay = ToolTipManager.sharedInstance().getInitialDelay();

		}
	}

	public void mouseExited(MouseEvent e) {
		if (e.getSource() == list) {
			ToolTipManager.sharedInstance().setDismissDelay(toolTipDefaultDismissDelay);
			ToolTipManager.sharedInstance().setInitialDelay(toolTipDefaultInitialDelay);
		}
	}

	public void keyPressed(KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

	public void keyReleased(KeyEvent arg0) {
		// TODO Auto-generated method stub

	}

	public void keyTyped(KeyEvent e) {

	}

	public void stateChanged(ChangeEvent e) {
		if (e.getSource() == zSustainSpinner) {
			zSustain = Integer.parseInt(zSustainSpinner.getValue().toString());
		}
		if (e.getSource() == tSustainSpinner) {
			tSustain = Integer.parseInt(tSustainSpinner.getValue().toString());
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

	public void setTSustain(int sustain) {
		tSustainSpinner.setValue(sustain);
		tSustain = sustain;
		showAll(SHOW_ALL);

	}

	public void setShowAllCheckbox(boolean canvasShowAllState) {
		showAllCheckbox.setSelected(canvasShowAllState);
	}

	public Roi[] getSliceSpecificRoiArray(int z, int t, boolean getSpanners) {
		Roi[] roiSetIn = this.getFullRoisAsArray();
		if (roiSetIn == null)
			return null;
		ArrayList<Roi> matchedRois = new ArrayList<Roi>();
		for (int i = 0; i < roiSetIn.length; i++) {
			// IJ.log(( roiSetIn[i].getZPosition() +" "+ z +" "+ roiSetIn[i].getTPosition()
			// +" "+ t+"\n"));
			if (roiSetIn[i].getTPosition() == t || (roiSetIn[i].getTPosition() == 0 && getSpanners)) {
				if (roiSetIn[i].getZPosition() == z || (roiSetIn[i].getZPosition() == 0
						&& getSpanners)/*
										 * && roiSetIn[i].getTPosition() > t - tSustain && roiSetIn[i].getTPosition() <
										 * t + tSustain
										 */) {
					matchedRois.add(roiSetIn[i]);
					// IJ.showMessage("");

				}
			}
		}
		// IJ.log(""+matchedRoiIndexes.size());

		Roi[] sliceSpecificFullRois = new Roi[matchedRois.size()];
		for (int i = 0; i < sliceSpecificFullRois.length; i++) {
			sliceSpecificFullRois[i] = matchedRois.get(i);
		}
		return sliceSpecificFullRois;

	}

	public int[] getSliceSpecificIndexes(int z, int t, boolean getSpanners) {
		Roi[] roiSetIn = this.getFullRoisAsArray();
		if (roiSetIn == null)
			return null;
		ArrayList<Integer> matchedIndexes = new ArrayList<Integer>();
		for (int i = 0; i < roiSetIn.length; i++) {
			// IJ.log(( roiSetIn[i].getZPosition() +" "+ z +" "+ roiSetIn[i].getTPosition()
			// +" "+ t+"\n"));
			if (roiSetIn[i].getTPosition() == t || (roiSetIn[i].getTPosition() == 0 && getSpanners)) {
				if (roiSetIn[i].getZPosition() == z || (roiSetIn[i].getZPosition() == 0
						&& getSpanners)/*
										 * && roiSetIn[i].getTPosition() > t - tSustain && roiSetIn[i].getTPosition() <
										 * t + tSustain
										 */) {
					matchedIndexes.add(i);
					// IJ.showMessage("");
				}
			}
		}
		// IJ.log(""+matchedRoiIndexes.size());

		int[] sliceSpecificFullIndexes = new int[matchedIndexes.size()];
		for (int i = 0; i < sliceSpecificFullIndexes.length; i++) {
			sliceSpecificFullIndexes[i] = matchedIndexes.get(i);
		}
		return sliceSpecificFullIndexes;

	}

	public Roi getSliceSpecificRoi(ImagePlus impIn, int z, int t) {
		// this.imp = imp;
		Roi combinedROI = null;
		this.setSelectedIndexes(this.getAllShownIndexes());
		Roi[] roiSubSet = this.getSelectedRoisAsArray();
		ArrayList<Integer> matchedRoiIndexes = new ArrayList<Integer>();

		// These next two parameters are essential to tuning the size and scaling over
		// time of the cell-specific R0Is that can be used to isolate cells and
		// lineages. This is the best overall fit I could find for pie-I:: HIS-58 V.
		// I will probably want to make some sliders or spinners for these in some
		// previewable dialog.
		// double widthDenom = 4;
		// double timeBalancer = 2;

		double widthDenom = 4.5;
		double timeBalancer = 2;
		ImagePlus guideImp = imp;
		if (imp.getMotherImp() != null)
			guideImp = imp.getMotherImp();
		int frames = guideImp.getNFrames();
		BigDecimal framesBD = new BigDecimal("" + (frames + timeBalancer));
		BigDecimal widthDenomBD = new BigDecimal("" + widthDenom);
		BigDecimal tBD = new BigDecimal(
				"" + (imp.getMotherID() > 0 ? imp.getMotherID() : 0 + imp.getFrame() + timeBalancer));
		BigDecimal impHeightBD = new BigDecimal("" + guideImp.getHeight());
		BigDecimal cellDiameterBD = impHeightBD.divide(widthDenomBD, MathContext.DECIMAL32)
				.multiply(takeRoot(3,
						(framesBD.subtract(tBD).add(new BigDecimal("1"))).divide(tBD, MathContext.DECIMAL32),
						new BigDecimal(".001")), MathContext.DECIMAL32);

		for (int i = 0; i < roiSubSet.length; i++) {
			if (Math.abs(roiSubSet[i].getZPosition() - z) * imp.getCalibration().pixelDepth < cellDiameterBD.intValue()
					/ 2 && roiSubSet[i].getTPosition() > t - tSustain && roiSubSet[i].getTPosition() < t + tSustain) {

				// Subtractive solution to shrinking cell sizes...
				// double inPlaneDiameter = Math.sqrt(
				// Math.pow(((imp.getWidth()/widthDenom)-(imp.getWidth()/widthDenom)*t*timeFactor/imp.getNFrames())/2,2)
				// -
				// Math.pow((roiSubSet[i].getZPosition()-z)*imp.getCalibration().pixelDepth*2,2)
				// );

				// Volume fraction solution to shrinking cell sizes...
				double inPlaneDiameter = 2 * Math.sqrt(Math.pow(cellDiameterBD.intValue() / 2, 2)
						- Math.pow((roiSubSet[i].getZPosition() - z) * imp.getCalibration().pixelDepth, 2));
				// IJ.log(""+cellDiameterBD.intValue() +" "+ inPlaneDiameter );
				imp.setRoi(new OvalRoi(roiSubSet[i].getBounds().getCenterX() - inPlaneDiameter / 2,
						roiSubSet[i].getBounds().getCenterY() - inPlaneDiameter / 2, inPlaneDiameter, inPlaneDiameter));
				addRoi(imp.getRoi());
				matchedRoiIndexes.add(this.getCount() - 1);
			}
		}

		// Select image corners.
		imp.setRoi(new Rectangle(0, 0, 1, 1));
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount() - 1);

		imp.setRoi(new Rectangle(0, imp.getHeight() - 1, 1, 1));
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount() - 1);

		imp.setRoi(new Rectangle(imp.getWidth() - 1, 0, 1, 1));
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount() - 1);

		imp.setRoi(new Rectangle(imp.getWidth() - 1, imp.getHeight() - 1, 1, 1));
		addRoi(imp.getRoi());
		matchedRoiIndexes.add(this.getCount() - 1);

		int[] sliceSpecificIndexes = new int[matchedRoiIndexes.size()];
		for (int i = 0; i < sliceSpecificIndexes.length; i++) {
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

	public static BigDecimal takeRoot(int root, BigDecimal n, BigDecimal maxError) {
		int MAXITER = 5000;

		// Specify a math context with 40 digits of precision.
		MathContext mc = new MathContext(40);

		// Specify the starting value in the search for the cube root.
		BigDecimal x;
		x = new BigDecimal("1", mc);

		BigDecimal prevX = null;

		BigDecimal rootBD = new BigDecimal(root, mc);
		// Search for the cube root via the Newton-Raphson loop. Output
		// each successive iteration's value.
		for (int i = 0; i < MAXITER; ++i) {
			x = x.subtract(x.pow(root, mc).subtract(n, mc).divide(rootBD.multiply(x.pow(root - 1, mc), mc), mc), mc);
			if (prevX != null && prevX.subtract(x).abs().compareTo(maxError) < 0)
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
		for (int j = 0; j < imageIDs.length; j++) {
			RoiManager recipRM = WindowManager.getImage(imageIDs[j]).getRoiManager();
			if (recipRM == null)
				recipRM = new RoiManager(WindowManager.getImage(imageIDs[j]), true);
			if (recipRM != this) {
				for (int i = 0; i < copiedRois.length; i++) {

					recipRM.addRoi((Roi) copiedRois[i].clone());
					recipRM.getShownRoisAsArray()[recipRM.getCount() - 1].setPosition(copiedRois[i].getCPosition(),
							copiedRois[i].getZPosition(), copiedRois[i].getTPosition());
					String nameEndReader = copiedRois[i].getName();

					int c = copiedRois[i].getCPosition();
					int z = copiedRois[i].getZPosition();
					int t = copiedRois[i].getTPosition();
					if (nameEndReader.split("_").length == 4) {
						c = Integer.parseInt(nameEndReader.split("_")[1]);
						z = Integer.parseInt(nameEndReader.split("_")[2]);
						t = Integer.parseInt(nameEndReader.split("_")[3].split("[CZT-]")[0]);
					}
					while (nameEndReader.endsWith("C") || nameEndReader.endsWith("Z") || nameEndReader.endsWith("T")) {
						if (nameEndReader.endsWith("C")) {
							c = 0;
							nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
						}
						if (nameEndReader.endsWith("Z")) {
							z = 0;
							nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
						}
						if (nameEndReader.endsWith("T")) {
							t = 0;
							nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
						}
					}
					recipRM.getShownRoisAsArray()[recipRM.getCount() - 1].setPosition(c, z, t);
					recipRM.getShownRoisAsArray()[recipRM.getCount() - 1].setName(nameEndReader);
				}
				recipRM.showAll(RoiManager.SHOW_ALL);
			}
		}
	}

	public boolean isShowAll() {
		return showAll;
	}

	public void sketch3D(Object source) {
		IJ.log("" + imp.getCalibration().pixelDepth);
		int synapseScale = imp.getWidth() / 25;
		int modelWidth = 80;
		if (getShownRoisAsArray().length < 1 || imp.getNDimensions() > 6) {
			busy = false;
			return;
		}
		if (Channels.getInstance() != null)
			Channels.getInstance().dispose();
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

		Hashtable<String, ArrayList<String>> shownRoisHash = new Hashtable<String, ArrayList<String>>();
		if (selectedIndexes.length < 1) {
			selectedIndexes = this.getAllShownIndexes();
			selectedRois = this.getShownRoisAsArray();
		}

		if (brainbow) {
			if (source instanceof JButton
					&& ((JButton) source).getParent().getParent().getParent() instanceof ImageWindow)
				source = ((ImageWindow) ((JButton) source).getParent().getParent().getParent()).getImagePlus();
			ColorLegend cl = getColorLegend(source);
			if (cl != null) {
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
					if (this.getCount() < 1) {
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
					if (this.getCount() < 1) {
						IJ.log("Manager contains no Tags");
						return;
					}

					ArrayList<String> selectedNamesStrings = new ArrayList<String>();
					IJ.log("" + selectedNamesStrings.size());
					for (int r = 0; r < selectedRois.length; r++) {
						if (selectedRois[r].getName().split("[\"|=]").length > 1) {
							String[] searchTextChunks = selectedRois[r].getName().split("[\"|=]")[1].split(" ");
							String searchText = "";
							for (String chunk : searchTextChunks)
								if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
									searchText = searchText + " " + chunk;
							String cellTagName = searchText.trim();
							selectedNamesStrings.add(cellTagName);
							// IJ.log(selectedNamesStrings.get(selectedNamesStrings.size()-1));
						}
					}
					if (cellNames != null /* && this != cl.getRoiManager() */) {
						cellNames = new ArrayList<String>();
						for (JCheckBox cb : colorLegend.getJCheckBox()) {
							if (((!cb.isSelected() && colorLegend.getChoice().getSelectedItem().matches("Hide.*"))
									|| (cb.isSelected() && !colorLegend.getChoice().getSelectedItem().matches("Hide.*"))
									|| colorLegend.getChoice().getSelectedItem().matches("Display.*"))
									&& !cellNames.contains(cb.getName())) {
								cellNames.add(cb.getName());
								// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
							}
						}
						if (cellNames.size() < 1 && !colorLegend.getChoice().getSelectedItem().matches("Hide.*")) {
							for (JCheckBox cb : colorLegend.getJCheckBox()) {
								cellNames.add(cb.getName());
								// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
							}
						}
					}
					if (cellNames != null) {
						String[] cellNamesArray = cellNames.toArray(new String[cellNames.size()]);
						for (int q = cellNamesArray.length - 1; q >= 0; q--) {
							// IJ.log(""+cellNames.get(q) + " "+ q +"?");
							if (!selectedNamesStrings.contains(cellNames.get(q))) {
								// IJ.log(""+cellNames.get(q) + " "+ q +"XXX");
								cellNames.remove(q);
							} else {
								// IJ.log(""+cellNames.get(q) + " "+ q +"OK");
							}
						}
					}
				}
			}
		}
		if (this.getCount() < 1) {
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
			mowColors = new Hashtable<String, Color>();
			mowColors.put("", Color.white);
			for (int r = 0; r < fullRois.length; r++) {
				if (fullRois[r] != null && fullRois[r].getName().matches(".*[\"|=].*")) {
					String[] searchTextChunks = fullRois[r].getName().split("[\"|=]")[1].split(" ");
					String searchText = "";
					for (String chunk : searchTextChunks)
						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
							searchText = searchText + " " + chunk;
					if (searchText.trim().matches(".*ABar.*"))
						isEmbryonic = true;
					if (!fullCellNames.contains(searchText.trim())) {
						fullCellNames.add(searchText.trim());
						if (fullRois[r].isArea() && fullRois[r].getFillColor() != null)
							mowColors.put(fullCellNames.get(fullCellNames.size() - 1),
									new Color(fullRois[r].getFillColor().getRGB()));
						else if (fullRois[r].isArea() && fullRois[r].getFillColor() == null)
							mowColors.put(fullCellNames.get(fullCellNames.size() - 1), Color.gray);
						if (fullRois[r].isLine() && fullRois[r].getStrokeColor() != null)
							mowColors.put(fullCellNames.get(fullCellNames.size() - 1),
									new Color(fullRois[r].getStrokeColor().getRGB()));
					}
				}
			}
		}
		if (cellNames == null /* && this == cl.getRoiManager() */) {
			cellNames = new ArrayList<String>();
			for (int r = 0; r < selectedRois.length; r++) {
				if (selectedRois[r] != null && selectedRois[r].getName().matches(".*[\"|=].*")) {
					String[] searchTextChunks = selectedRois[r].getName().split("[\"|=]")[1].split(" ");
					String searchText = "";
					for (String chunk : searchTextChunks)
						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
							searchText = searchText + " " + chunk;
					if (!cellNames.contains(searchText.trim())) {
						cellNames.add(searchText.trim());
						// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
					}
				}
			}
			if (cellNames == null) {
				for (int r = 0; r < fullCellNames.size(); r++) {
					cellNames.add("" + fullCellNames.get(r));
					// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());

				}
			}
		}

		nameLists.add(cellNames);

		if (brainbowColors == null) {
			if (colorLegend == null) {
				brainbowColors = new Hashtable<String, Color>();
				brainbowColors.put(fullCellNames.get(0).trim().toLowerCase(), Color.white);
				String[] hexChoices = { "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };
				for (int c3 = 0; c3 < fullCellNames.size(); c3++) {
					String randomHexString = "";
					boolean unique = false;
					do {
						randomHexString = "#" + hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))];

						for (int i = 0; i < brainbowColors.size(); i++) {
							if (brainbowColors.get(fullCellNames.get(i).toLowerCase()) == Colors.decode(randomHexString,
									Color.white)
									|| brainbowColors.get(fullCellNames.get(i).toLowerCase()).darker()
											.darker() == Colors.decode(randomHexString, Color.white)) {
								unique = false;
							} else {
								unique = true;
							}
						}
					} while (!unique);
					brainbowColors.put(fullCellNames.get(c3).toLowerCase(),
							Colors.decode(randomHexString, Color.white));
				}
			} else {
				brainbowColors = new Hashtable<String, Color>();
				JCheckBox[] cbs = colorLegend.getJCheckBox();
				for (int cb = 0; cb < cbs.length; cb++) {
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

		if (isEmbryonic) {
			for (int j = 0; j < shownIndexes.length; j++) {
				String[] cellNumbers = shownRois[j].getName().split("_");
				if (shownRoisHash.get(cellNumbers[cellNumbers.length - 2] + "_"
						+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", "")) == null)
					shownRoisHash.put(
							cellNumbers[cellNumbers.length - 2] + "_"
									+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", ""),
							new ArrayList<String>());
				if (!shownRoisHash
						.get(cellNumbers[cellNumbers.length - 2] + "_"
								+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", ""))
						.contains(shownRois[j].getName())) {
					shownRoisHash
							.get(cellNumbers[cellNumbers.length - 2] + "_"
									+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", ""))
							.add(shownRois[j].getName());
					// IJ.log(cellNumbers[cellNumbers.length-2]+"_"+cellNumbers[cellNumbers.length-1].replaceAll("[CZT]",
					// "") +" "+j+" "+shownRois[j].getName());
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
			if (nFrames > 1) {
				gd.setInsets(2, 30, 3);
				gd.addStringField("Frames (t, " + 1 + "-" + imp.getNFrames() + "):",
						"" + imp.getFrame() + "-" + imp.getFrame());
				nRangeFields++;
			}
			Vector v = gd.getStringFields();
			JTextField[] rangeFields = new JTextField[3];
			for (int i = 0; i < nRangeFields; i++) {
				rangeFields[i] = (JTextField) v.elementAt(i);
				rangeFields[i].getDocument().addDocumentListener(this);
			}
			hyperstackCheckbox = (JCheckBox) (gd.getCheckboxes().elementAt(0));
			boolean fullSketchToFile = false;
			gd.showDialog();

			int firstT = imp.getFrame();
			int lastT = imp.getFrame();
			if (!gd.wasCanceled()) {
				if (gd.getNextBoolean()) {
					if (nFrames > 1) {
						String[] range = Tools.split(gd.getNextString(), " -");
						double t1 = Tools.parseDouble(range[0]);
						double t2 = range.length == 2 ? Tools.parseDouble(range[1]) : Double.NaN;
						firstT = Double.isNaN(t1) ? firstT : (int) t1;
						lastT = Double.isNaN(t2) ? lastT : (int) t2;
						if (firstT > lastT) {
							firstT = lastT;
							lastT = firstT;
						}
						if (firstT < 1)
							firstT = 1;
						if (lastT > nFrames)
							lastT = nFrames;
					}
				}
				fullSketchToFile = gd.getNextBoolean();
			}

			double fillZfactor = imp.getCalibration().pixelDepth;
			int outNSlices = 0;
			for (int c2 = 0; c2 < outChannels; c2++) {
				IJ.showStatus("Processing " + (c2 + 1) + "/" + outChannels + " channels...");
				ImageStack sketchStack = new ImageStack(modelWidth, imp.getHeight() / (imp.getWidth() / modelWidth));
				IJ.log("" + fillZfactor);
				IJ.log("" + (double) (sketchStack.getHeight()) / (double) (imp.getHeight()));
				IJ.log("" + imp.getHeight());
				IJ.log("" + sketchStack.getHeight());
				Color frameColor = Color.WHITE;
				outNSlices = (int) (fillZfactor * imp.getNSlices());
				int miniStackSize = outNSlices * modelWidth / imp.getWidth();
				for (int t = firstT; t <= lastT; t++) {
					ImageStack bigStack = new ImageStack(imp.getWidth(), imp.getHeight());
					for (int z = 1; z <= outNSlices; z++) {
						if (eightBit) {
							drawIP = new ByteProcessor(imp.getWidth(), imp.getHeight());
						} else {
							drawIP = new ColorProcessor(imp.getWidth(), imp.getHeight());
						}
						drawIP.setColor(Color.BLACK);
						drawIP.fill();
						bigStack.addSlice(drawIP);
					}

					for (int z = 1; z <= outNSlices; z++) {
						ArrayList<String> theseSlcSpecRoiNames = new ArrayList<String>();
						if (/* z%fillZfactor == 0 && */ this.getSliceSpecificRoiArray((int) (z / fillZfactor), t,
								false) != null) {
							for (Roi thisRoi : this.getSliceSpecificRoiArray((int) (z / fillZfactor), t, false))
								theseSlcSpecRoiNames.add(thisRoi.getName());
						}
						// IJ.log(""+z);
						for (int j = 0; j < theseSlcSpecRoiNames.size(); j++) {
							// IJ.log(theseSlcSpecRoiNames.get(j));
							if (theseSlcSpecRoiNames.get(j) != null
									&& ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim())) != null
									/* && z%fillZfactor == 0 */
									&& ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim()))
											.getZPosition() == (int) (z / fillZfactor)
									&& ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim())).getTPosition() == t) {
								// IJ.log(theseSlcSpecRoiNames.get(j));
								String[] searchTextChunks = theseSlcSpecRoiNames.get(j).split("[\"|=]")[1].split(" ");
								String searchText = "";
								for (String chunk : searchTextChunks)
									if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
										searchText = searchText + " " + chunk;
								String cellTagName = searchText.trim();
								// IJ.log(cellTagName);
								if ((fatSynapses && theseSlcSpecRoiNames.get(j).startsWith("\"syn")) || isEmbryonic) {
									if (cellNames.contains(cellTagName)
											&& brainbowColors.get(cellTagName.toLowerCase()) != null) {
										// IJ.log("cell name matches");
										int maxRadius = synapseScale / 2;
										for (int step = -maxRadius; step < maxRadius; step++) {
											if (z + step > 0 && z + step <= outNSlices) {
												drawIP = bigStack.getProcessor(z + step);
												drawIP.setColor(brainbow
														? new Color(
																brainbowColors.get(cellTagName.toLowerCase()).getRGB())
														: eightBit ? Color.WHITE : mowColors.get(cellTagName));
												double radius = Math.pow(Math.pow((maxRadius), 2) - Math.pow(step, 2),
														0.5);
												Roi thisRoi = ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim()));
												// IJ.log(""+theseSlcSpecRoiNames.get(j).trim()+" "+radius);
												if (thisRoi != null)
													drawIP.fill(
															new OvalRoi((int) thisRoi.getBounds().getCenterX() - radius,
																	(int) thisRoi.getBounds().getCenterY() - radius,
																	radius * 2, radius * 2));

											}
										}
									}
								}
							}
						}

						for (int j = 0; j < theseSlcSpecRoiNames.size(); j++) {
							if (theseSlcSpecRoiNames.get(j) != null) {
								String[] searchTextChunks = theseSlcSpecRoiNames.get(j).split("[\"|=]")[1].split(" ");
								String searchText = "";
								for (String chunk : searchTextChunks)
									if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
										searchText = searchText + " " + chunk;
								String cellTagName = searchText.trim();
								if (!(fatSynapses && theseSlcSpecRoiNames.get(j).startsWith("\"syn")) && !isEmbryonic
										&& brainbowColors.get(cellTagName) != null) {
									drawIP.setColor(
											brainbow ? new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB())
													: eightBit ? Color.WHITE : mowColors.get(cellTagName));

									if (cellNames.contains(cellTagName)) {
										Roi thisRoi = ((Roi) rois.get(theseSlcSpecRoiNames.get(j).trim()));
										if (thisRoi != null)
											drawIP.fill(thisRoi);
									}
								} else {
									// IJ.log(""+cellTagName + brainbowColors.get(cellTagName.toLowerCase()));

								}

							}
						}
					}
					for (int s = 1; s <= bigStack.getSize(); s = s + (imp.getWidth() / modelWidth)) {
						ImageProcessor bip = bigStack.getProcessor(s);
						bip.setInterpolationMethod(ImageProcessor.BICUBIC);
						sketchStack.addSlice(
								bip.resize(modelWidth, imp.getHeight() / (imp.getWidth() / modelWidth), false));
						// IJ.log("RESIZE "+sketchStack.getSize());
					}
					miniStackSize = sketchStack.getSize();
					ImagePlus bigImp = new ImagePlus("bigStack", bigStack);
					if (fullSketchToFile) {
						IJ.run(bigImp, "Save", "save=" + IJ.getDirectory("home") + imp.getTitle() + "_fullSketch3D"
								+ IJ.pad(t, 5) + ".tif");
					}
					bigImp.close();
					bigImp.flush();
					bigImp = null;

				}

				ImagePlus sketchImp = new ImagePlus("Sketch_"
						+ (splitThem ? (nameLists != null ? nameLists.get(nameLists.size() - 1) : cellNames).get(c2)
								: "Composite"),
						sketchStack);

				outImps[c2] = sketchImp;
				outImps[c2].getCalibration().pixelDepth = 1;
				outImps[c2].setMotherImp(imp, 1);
				IJ.run(outImps[c2], "Stack to Hyperstack...",
						"order=xyczt(default) channels=1 slices=" + (miniStackSize / (lastT - firstT + 1)) + " frames="
								+ (lastT - firstT + 1) + " display=Color");
			}
			compImps.add(outImps[0]);
			if (splitThem) {
				compImps.set(compImps.size() - 1, RGBStackMerge.mergeChannels(outImps, false));
			}
			if (compImps.get(compImps.size() - 1) != null) {
				compImps.get(compImps.size() - 1).show();
				compImps.get(compImps.size() - 1).getWindow().setBackground(this.getBackground());
				compImps.get(compImps.size() - 1).setTitle(
						"Sketch3D #" + compImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
				compImps.get(compImps.size() - 1).getWindow().addWindowListener(this);
				compImps.get(compImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
				compImps.get(compImps.size() - 1).getCanvas().addMouseListener(colorLegend);
				compImps.get(compImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
			}
			if (Channels.getInstance() != null)
				((Channels) Channels.getInstance()).close();
			IJ.run(compImps.get(compImps.size() - 1), "3D Project...",
					"projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
			projYImps.add(WindowManager.getImage("Projections of Sketch3D"));
			if (splitThem)
				projYImps.get(projYImps.size() - 1).setDimensions(projYImps.get(projYImps.size() - 1).getNChannels(),
						projYImps.get(projYImps.size() - 1).getNFrames(),
						projYImps.get(projYImps.size() - 1).getNSlices());
			projYImps.get(projYImps.size() - 1).setTitle(
					"Sketch3D ProjDV #" + projYImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projYImps.get(projYImps.size() - 1).setMotherImp(imp, 1);
			projYImps.get(projYImps.size() - 1).getWindow().setBackground(this.getBackground());
			projYImps.get(projYImps.size() - 1).getWindow().addWindowListener(this);
			projYImps.get(projYImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
			projYImps.get(projYImps.size() - 1).getCanvas().addMouseListener(colorLegend);
			projYImps.get(projYImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);

			if (Channels.getInstance() != null)
				((Channels) Channels.getInstance()).close();
			if (Channels.getInstance() != null)
				((Channels) Channels.getInstance()).close();
			IJ.run(compImps.get(compImps.size() - 1), "3D Project...",
					"projection=[Nearest Point] axis=X-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");

			projZImps.add(WindowManager.getImage("Projections of Sketch3D"));
			projZImps.get(projZImps.size() - 1).setTitle(
					"Sketch3D ProjAP #" + projZImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projZImps.get(projZImps.size() - 1).setMotherImp(imp, 1);
			projZImps.get(projZImps.size() - 1).getWindow().addWindowListener(this);
			projZImps.get(projZImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
			projZImps.get(projZImps.size() - 1).getCanvas().addMouseListener(colorLegend);
			projZImps.get(projZImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);

			if (imp.getCanvas().droppedGeneUrls != null) {
				IJ.setForegroundColor(255, 255, 255);
				IJ.run(compImps.get(compImps.size() - 1), "Label...",
						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
								+ imp.getCanvas().droppedGeneUrls + " range=[]");
				compImps.get(compImps.size() - 1).changes = false;
				IJ.run(projYImps.get(projYImps.size() - 1), "Label...",
						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
								+ imp.getCanvas().droppedGeneUrls + " range=[]");
				projYImps.get(projYImps.size() - 1).changes = false;
				IJ.run(projZImps.get(projZImps.size() - 1), "Label...",
						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
								+ imp.getCanvas().droppedGeneUrls + " range=[]");
				projZImps.get(projZImps.size() - 1).changes = false;
			}
			if (splitThem)
				projZImps.get(projZImps.size() - 1).setDimensions(projZImps.get(projZImps.size() - 1).getNChannels(),
						projZImps.get(projZImps.size() - 1).getNFrames(),
						projZImps.get(projZImps.size() - 1).getNSlices());
			projZImps.get(projZImps.size() - 1).getWindow().setBackground(this.getBackground());
			if (brainbow) {

			} else if (splitThem) {
				IJ.run("Channels Tool...");
			}

		} else /* not isEmbryonic */ {
			int fillZfactor = 1;
			int outNSlices = 0;
			for (int c2 = 0; c2 < outChannels; c2++) {
				IJ.showStatus("Processing " + (c2 + 1) + "/" + outChannels + " channels...");
				ImageStack sketchStack = new ImageStack(
						(int) (imp.getWidth() * imp.getCalibration().pixelWidth / imp.getCalibration().pixelDepth),
						(int) (imp.getHeight() * imp.getCalibration().pixelWidth / imp.getCalibration().pixelDepth));
				fillZfactor = (isEmbryonic ? sketchStack.getHeight() : imp.getNSlices()) / imp.getNSlices();
				Color frameColor = Color.WHITE;
				outNSlices = (int) (isEmbryonic ? sketchStack.getHeight() * 0.9 : imp.getNSlices());
				for (int i = 0; i < imp.getNFrames(); i++) {

					for (int z = 0; z < (outNSlices); z++) {
						drawIP.setColor(Color.BLACK);
						drawIP.fill();
						for (int j = 0; j < shownIndexes.length; j++) {
							if (shownRois[j] != null) {
								String cellTagName = shownRois[j].getName().split("[\"|=]")[1].trim().toLowerCase();
								if (shownRois[j].getTPosition() == i + 1 && cellNames != null && cellNames.size() > 0
										&& (cellNames.get(c2).equals(cellTagName) || !splitThem)) {
									if (fatSynapses && shownRois[j].getName().startsWith("\"syn")) {
										drawIP.setColor(brainbow
												? new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB())
												: eightBit ? Color.WHITE : mowColors.get(cellTagName));

										if (cellNames.contains(cellTagName)) {
											double radius = synapseScale / 2;
											drawIP.fill(
													new OvalRoi((int) shownRois[j].getBounds().getCenterX() - radius,
															(int) shownRois[j].getBounds().getCenterY() - radius,
															radius * 2, radius * 2));
											// IJ.log("drewit"+radius);

										}
									}
								}
							}
						}
						for (int j = 0; j < shownIndexes.length; j++) {
							if (shownRois[j] != null) {
								String cellTagName = shownRois[j].getName().split("[\"|=]")[1].trim();
								if (shownRois[j].getTPosition() == i + 1
										&& shownRois[j].getZPosition() * fillZfactor == z + 1 && cellNames != null
										&& cellNames.size() > 0 && !isEmbryonic
										&& (cellNames.get(c2).equals(cellTagName) || !splitThem)) {
									if (!fatSynapses || !shownRois[j].getName().startsWith("\"syn")
											&& brainbowColors.get(cellTagName.toLowerCase()) != null) {
										drawIP.setColor(
												brainbow && brainbowColors.get(cellTagName.toLowerCase()) != null
														? new Color(
																brainbowColors.get(cellTagName.toLowerCase()).getRGB())
														: eightBit ? Color.WHITE
																: mowColors.get(cellTagName) != null
																		? mowColors.get(cellTagName)
																		: Color.WHITE);

										if (cellNames.contains(cellTagName))
											drawIP.fill(shownRois[j]);
									} else {

									}
								}
							}
						}
						sketchStack.addSlice(drawIP.resize(
								(int) (imp.getWidth() * imp.getCalibration().pixelWidth
										/ imp.getCalibration().pixelDepth),
								(int) (imp.getHeight() * imp.getCalibration().pixelWidth
										/ imp.getCalibration().pixelDepth)));
					}
				}

				ImagePlus sketchImp = new ImagePlus("Sketch_"
						+ (splitThem ? (nameLists != null ? nameLists.get(nameLists.size() - 1) : cellNames).get(c2)
								: "Composite"),
						sketchStack);
				outImps[c2] = sketchImp;
				outImps[c2].getCalibration().pixelWidth = imp.getCalibration().pixelWidth;
				outImps[c2].getCalibration().pixelHeight = imp.getCalibration().pixelWidth;
				outImps[c2].getCalibration().pixelDepth = imp.getCalibration().pixelWidth;
				outImps[c2].setMotherImp(imp, 1);
				StackReverser sr = new StackReverser();
				sr.flipStack(outImps[c2]);
			}
			compImps.add(outImps[0]);
			if (splitThem) {
				compImps.set(compImps.size() - 1, RGBStackMerge.mergeChannels(outImps, false));
			}
			if (compImps.get(compImps.size() - 1) != null) {
				compImps.get(compImps.size() - 1).show();
				compImps.get(compImps.size() - 1).getWindow().setBackground(this.getBackground());
				compImps.get(compImps.size() - 1).setTitle(
						"Sketch3D #" + compImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
				compImps.get(compImps.size() - 1).getWindow().addWindowListener(this);
				compImps.get(compImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
				compImps.get(compImps.size() - 1).getCanvas().addMouseListener(colorLegend);
				compImps.get(compImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
			}
			if (Channels.getInstance() != null)
				((Channels) Channels.getInstance()).close();
			IJ.run(compImps.get(compImps.size() - 1), "3D Project...",
					"projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
			projYImps.add(WindowManager.getImage("Projections of Sketch3D"));
			if (splitThem)
				projYImps.get(projYImps.size() - 1).setDimensions(projYImps.get(projYImps.size() - 1).getNChannels(),
						projYImps.get(projYImps.size() - 1).getNFrames(),
						projYImps.get(projYImps.size() - 1).getNSlices());
			projYImps.get(projYImps.size() - 1).setTitle(
					"Sketch3D ProjDV #" + projYImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projYImps.get(projYImps.size() - 1).setMotherImp(imp, 1);
			projYImps.get(projYImps.size() - 1).getWindow().setBackground(this.getBackground());
			projYImps.get(projYImps.size() - 1).getWindow().addWindowListener(this);
			projYImps.get(projYImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
			projYImps.get(projYImps.size() - 1).getCanvas().addMouseListener(colorLegend);
			projYImps.get(projYImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
			if (Channels.getInstance() != null)
				((Channels) Channels.getInstance()).close();
			ImagePlus flipDupImp = compImps.get(compImps.size() - 1).duplicate();
			StackReverser sr = new StackReverser();
			sr.flipStack(flipDupImp);
			IJ.run(flipDupImp, "Reslice ...", "output=1.000 start=Left");
			ImagePlus rsImp = IJ.getImage();
			rsImp.setTitle("tempDupReslice");
			rsImp.getCalibration().pixelWidth = imp.getCalibration().pixelWidth;
			rsImp.getCalibration().pixelHeight = imp.getCalibration().pixelWidth;
			rsImp.getCalibration().pixelDepth = imp.getCalibration().pixelWidth;
			if (Channels.getInstance() != null)
				((Channels) Channels.getInstance()).close();
			IJ.run(rsImp, "3D Project...",
					"projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
			projZImps.add(WindowManager.getImage("Projections of tempDupReslice"));
			projZImps.get(projZImps.size() - 1).setTitle(
					"Sketch3D ProjAP #" + projZImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
			projZImps.get(projZImps.size() - 1).setMotherImp(imp, 1);
			projZImps.get(projZImps.size() - 1).getWindow().addWindowListener(this);
			projZImps.get(projZImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
			projZImps.get(projZImps.size() - 1).getCanvas().addMouseListener(colorLegend);
			projZImps.get(projZImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
			StackProcessor sp = new StackProcessor(projZImps.get(projZImps.size() - 1).getStack(),
					projZImps.get(projZImps.size() - 1).getStack().getProcessor(1));
			sp.flipHorizontal();
			projZImps.get(projZImps.size() - 1).setStack(sp.rotateLeft());

			ImageCanvas ic = projZImps.get(projZImps.size() - 1).getCanvas();
			projZImps.get(projZImps.size() - 1).getWindow().pack();
			int padH = 1 + projZImps.get(projZImps.size() - 1).getWindow().getInsets().left
					+ projZImps.get(projZImps.size() - 1).getWindow().getInsets().right
					+ (projZImps.get(projZImps.size() - 1).getWindow().optionsPanel.isVisible()
							? projZImps.get(projZImps.size() - 1).getWindow().optionsPanel.getWidth()
							: 0)
					+ projZImps.get(projZImps.size() - 1).getWindow().viewButtonPanel.getWidth();
			int padV = projZImps.get(projZImps.size() - 1).getWindow().getInsets().top
					+ projZImps.get(projZImps.size() - 1).getWindow().getInsets().bottom
					+ (projZImps.get(projZImps.size() - 1).getWindow() instanceof StackWindow
							? ((StackWindow) projZImps.get(projZImps.size() - 1).getWindow()).getNScrollbars()
									* ((StackWindow) projZImps.get(projZImps.size() - 1).getWindow()).zSelector
											.getHeight()
							: 0)
					+ projZImps.get(projZImps.size() - 1).getWindow().overheadPanel.getHeight();
			projZImps.get(projZImps.size() - 1).getWindow().setSize(ic.dstWidth + padH, ic.dstHeight + padV);

			flipDupImp.flush();
			flipDupImp = null;
			rsImp.close();
			rsImp.getRoiManager().dispose();
			rsImp.setIgnoreFlush(false);
			rsImp.flush();
			rsImp = null;
			IJ.wait(1000);
			if (imp.getCanvas().droppedGeneUrls != null) {
				IJ.setForegroundColor(255, 255, 255);
				IJ.run(compImps.get(compImps.size() - 1), "Label...",
						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
								+ imp.getCanvas().droppedGeneUrls + " range=[]");
				compImps.get(compImps.size() - 1).changes = false;
				IJ.run(projYImps.get(projYImps.size() - 1), "Label...",
						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
								+ imp.getCanvas().droppedGeneUrls + " range=[]");
				projYImps.get(projYImps.size() - 1).changes = false;
				IJ.run(projZImps.get(projZImps.size() - 1), "Label...",
						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
								+ imp.getCanvas().droppedGeneUrls + " range=[]");
				projZImps.get(projZImps.size() - 1).changes = false;
			}
			if (splitThem)
				projZImps.get(projZImps.size() - 1).setDimensions(projZImps.get(projZImps.size() - 1).getNChannels(),
						projZImps.get(projZImps.size() - 1).getNFrames(),
						projZImps.get(projZImps.size() - 1).getNSlices());
			projZImps.get(projZImps.size() - 1).getWindow().setBackground(this.getBackground());
			if (brainbow) {

				// colorLegend.show();
				// colorLegend.focusGained(new FocusEvent(IJ.getImage().getWindow(),
				// FocusEvent.FOCUS_GAINED));

			} else if (splitThem) {
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
		else if (imp != null && imp.getMotherImp() != imp) {
			colorLegend = imp.getMotherImp().getRoiManager().getColorLegend();
			return colorLegend;
		} else {
//			String universalCLURL = MQTVSSceneLoader64.class.("docs/fullUniversal_ColorLegend.lgd").toString();
//			String clStr = IJ.openUrlAsString(universalCLURL);
//			colorLegend = new ColorLegend(this.imp, clStr);
//
//			return colorLegend;
			return null;
		}
	}

	public ColorLegend getColorLegend(Object source) {

		if (this.colorLegend != null) {
			if (this.getImagePlus().equals(source) || this.getCompImps().contains(source)
					|| this.getProjYImps().contains(source) || this.getProjZImps().contains(source)) {
			} else {
				if (source instanceof ImagePlus)
					colorLegend = ((ImagePlus) source).getRoiManager().getColorLegend(source);
				if (source instanceof RoiManager)
					colorLegend = ((RoiManager) source).getColorLegend(source);

			}
		} else if (this.colorLegend == null) {
			if (imp != null && imp.getMotherImp() != null && imp.getMotherImp().getRoiManager() != null
					&& imp.getMotherImp().getRoiManager().getColorLegend() != null) {
				colorLegend = imp.getMotherImp().getRoiManager().getColorLegend();
			} else {
				colorLegend = new ColorLegend(this);
			}
		}
		// colorLegend.setVisible(true);
		return colorLegend;
	}

	public void setColorLegend(ColorLegend cl) {
		this.colorLegend = cl;
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

	public void shiftROIsXYZTC() {
		GenericDialog gd = new GenericDialog("Shift Tags in XYZ");
		Panel p = new Panel();
		gd.addPanel(p, GridBagConstraints.CENTER, new Insets(10, 0, 0, 0));
		gd.addNumericField("X", 0, 0);
		gd.addNumericField("Y", 0, 0);
		gd.addNumericField("Z", 0, 0);
		gd.addNumericField("T", 0, 0);
		gd.addNumericField("C", 0, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		shiftX = (gd.getNextNumber());
		shiftY = (gd.getNextNumber());
		shiftZ = ((int) gd.getNextNumber());
		shiftT = ((int) gd.getNextNumber());
		shiftC = ((int) gd.getNextNumber());
		for (Roi roi : getSelectedRoisAsArray()) {
			String roiWasName = roi.getName();
			roi.setLocation((int) (roi.getBounds().getX() + shiftX), (int) roi.getBounds().getY() + shiftY);
//		for (Roi roi:getShownRoisAsArray())
//			roi.setLocation((int)roi.getBounds().getX(), (int)(roi.getBounds().getY() + shiftY));
//		for (Roi roi:getShownRoisAsArray())
			String rbnoKey = "" + roi.getCPosition() + "_" + roi.getZPosition() + "_" + roi.getTPosition();
			String rbnsKey = "" + (roi.getCPosition() + shiftC) + "_" + (roi.getZPosition() + shiftZ) + "_"
					+ (roi.getTPosition() + shiftT);
			ArrayList<Roi> rbnOriginal = roisByNumbers.get(rbnoKey);
			ArrayList<Roi> rbnShifted = roisByNumbers.get(rbnsKey);
			roi.setPosition(roi.getCPosition(), roi.getZPosition() + shiftZ, roi.getTPosition() + shiftT);
			rois.replace(roi.getName(), roi);
			String roiName = roi.getName();
			if (rbnOriginal != null) {
				rbnOriginal.remove(roi);
				if (rbnShifted == null) {
					rbnShifted = new ArrayList<Roi>();
					rbnShifted.add(roi);
					roisByNumbers.put(rbnsKey, rbnShifted);
					IJ.log(roisByNumbers.containsKey(rbnsKey) + " " + roisByNumbers.containsValue(rbnShifted) + " "
							+ rbnShifted.contains(roi));
				} else {
					if (roi.getName().contains("~")){
						rbnShifted.add(0,roi);
					}else {
						rbnShifted.add(roi);
					}
				}
				IJ.log(roisByNumbers.get(rbnsKey).toString());
				String label ="";
				if (roi.getName() != null && roi.getName().split("\"").length > 1){
					label = "\"" + roi.getName().split("\"")[1].trim() + " \"" + "_" +Colors.colorToHexString(roi.getFillColor()) + "_" + roi.getCPosition() + "_"
							+ roi.getZPosition() + "_" + roi.getTPosition();
				}else if (roi.getName() != null){
					label = "\"" + roi.getName() + " \"" + "_" +Colors.colorToHexString(roi.getFillColor()) + "_" + roi.getCPosition() + "_" + roi.getZPosition() + "_"
							+ roi.getTPosition();
				}
				
				label = getUniqueName(label);
				listModel.set(listModel.indexOf(roiWasName), label);
				fullListModel.set(fullListModel.indexOf(roiWasName), label);
				roi.setName(label);
				rois.remove(roiWasName);
				rois.put(roi.getName(), roi);
				
			}
		}
		showAll(SHOW_ALL);

	}

	public void setFillTransparency(String alpha) {
		for (Roi roi : getShownRoisAsArray()) {
			if (roi.isArea())
				this.setRoiFillColor(roi, Colors
						.decode("#" + alpha + Integer.toHexString(roi.getFillColor().getRGB()).substring(2), null));
			if (roi.isLine())
				roi.setStrokeColor(Colors
						.decode("#" + alpha + Integer.toHexString(roi.getStrokeColor().getRGB()).substring(2), null));
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
		int count = 0;
		for (int ns = 0; ns < roiNames.length; ns++) {
			roiNames[ns] = allRois[ns].getName();
			roiNames[ns] = roiNames[ns].split("_")[0]
					+ (roiNames[ns].split("-").length > 1 ? "-" + roiNames[ns].split("-")[1].split("C")[0] : "") + "C";
		}
		for (int ns = 0; ns < shownRoiNames.length; ns++) {
			shownRoiNames[ns] = shownRois[ns].getName();
			shownRoiNames[ns] = shownRoiNames[ns].split("_")[0]
					+ (shownRoiNames[ns].split("-").length > 1 ? "-" + shownRoiNames[ns].split("-")[1].split("C")[0]
							: "")
					+ "C";
		}
		Arrays.sort(roiNames);
		Arrays.sort(shownRoiNames);
		for (int r = 0; r <= shownRoiNames.length; r++) {
			String currentRoiName = r < shownRoiNames.length ? shownRoiNames[r] : "";
			count++;
			// IJ.log(currentRoiName+" "+recentRoiName + "
			// "+(currentRoiName.equals(recentRoiName)));
			if (!currentRoiName.equals(recentRoiName) && recentRoiName != "") {
				shownCellTracks.add(recentRoiName);
				shownCellTrackLengths.add(count);
				count = 0;
			}
			recentRoiName = currentRoiName;
		}
		for (int r = 0; r <= roiNames.length; r++) {
			String currentRoiName = r < roiNames.length ? roiNames[r] : "";
			count++;
			// IJ.log(currentRoiName+" "+recentRoiName + "
			// "+(currentRoiName.equals(recentRoiName)));
			if (!currentRoiName.equals(recentRoiName) && recentRoiName != "") {
				cellTracks.add(recentRoiName);
				cellTrackLengths.add(count);
				count = 0;
			}
			recentRoiName = currentRoiName;
		}
		int[] zStepShiftX = new int[shownCellTracks.size()];
		int[] zStepShiftY = new int[shownCellTracks.size()];

		int[] roiCenterX1 = new int[shownCellTracks.size()];
		int[] roiCenterY1 = new int[shownCellTracks.size()];
		int[] roiCenterX2 = new int[shownCellTracks.size()];
		int[] roiCenterY2 = new int[shownCellTracks.size()];
		int ch = 3;

		for (int s = 1; s <= slices; s++) {
			int sn = s + 1;
			for (int n = 0; n < shownCellTracks.size(); n++) {
				for (ch = 1; ch <= imp.getNChannels(); ch++) {

					String shownCellTrackName = shownCellTracks.get(n);
					if (true) {
						String roiName = shownCellTrackName.replace(" \"", " \"_" + ch + "_1_" + s);
						String roiNextSliceName = shownCellTrackName.replace(" \"", " \"_" + ch + "_1_" + sn);
						Roi roi = (Roi) rois.get(roiName);
						Roi roiNextSlice = (Roi) rois.get(roiNextSliceName);
						if (roi != null) {
							roiCenterX1[n] = (int) roi.getBounds().getCenterX();
							roiCenterY1[n] = (int) roi.getBounds().getCenterY();
						}
						if (roiNextSlice != null) {
							roiCenterX2[n] = (int) roiNextSlice.getBounds().getCenterX();
							roiCenterY2[n] = (int) roiNextSlice.getBounds().getCenterY();
							zStepShiftX[n] = roiCenterX1[n] - roiCenterX2[n];
							zStepShiftY[n] = roiCenterY1[n] - roiCenterY2[n];
						}
					}
				}
			}
			IJ.log(s + " " + sn);
			Arrays.sort(zStepShiftX);
			int meanZStepShiftX = 0;
			int sumZStepShiftX = 0;
			int shiftXcount = 0;
			int maxZStepShiftX = 0;
			;
			for (int zshiftX : zStepShiftX) {
				if (Math.abs(zshiftX) < 300 && Math.abs(zshiftX) > 0) {
					if (Math.abs(maxZStepShiftX) < Math.abs(zshiftX))
						maxZStepShiftX = zshiftX;
					shiftXcount++;
					sumZStepShiftX = sumZStepShiftX + zshiftX;
				}
			}
			meanZStepShiftX = sumZStepShiftX / shiftXcount;
			Arrays.sort(zStepShiftY);
			int meanZStepShiftY = 0;
			int sumZStepShiftY = 0;
			int shiftYcount = 0;
			int maxZStepShiftY = 0;
			;
			for (int zshiftY : zStepShiftY) {
				if (Math.abs(zshiftY) < 300 && Math.abs(zshiftY) > 0) {
					if (Math.abs(maxZStepShiftY) < Math.abs(zshiftY))
						maxZStepShiftY = zshiftY;
					shiftYcount++;
					sumZStepShiftY = sumZStepShiftY + zshiftY;
				}
			}
			meanZStepShiftY = sumZStepShiftY / shiftYcount;
			IJ.log("Shift " + sn + " = " + meanZStepShiftX + "," + meanZStepShiftY);
			for (int n = 0; n < cellTracks.size(); n++) {
				for (ch = 1; ch <= imp.getNChannels(); ch++) {

					String cellTrackName = cellTracks.get(n);
					String roiName = cellTrackName.replace(" \"", " \"_" + ch + "_1_" + s);
					String roiNextSliceName = cellTrackName.replace(" \"", " \"_" + ch + "_1_" + sn);

					Roi roi = (Roi) rois.get(roiName);
					Roi roiNextSlice = (Roi) rois.get(roiNextSliceName);
					if (roi != null && roiNextSlice != null) {
						roiNextSlice.setLocation(
								(int) (roiNextSlice.getBounds().getCenterX() - (roiNextSlice.getBounds().getWidth() / 2)
										+ meanZStepShiftX),
								(int) (roiNextSlice.getBounds().getCenterY()
										- (roiNextSlice.getBounds().getHeight() / 2) + meanZStepShiftY));
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
		for (String paramLine : paramLines) {
			if (paramLine.matches("\\d*\\=\\>.*")) {
				// IJ.log(paramLine);
				int slice = Integer.parseInt(paramLine.split("\\=")[0]);
				double tx = Double.parseDouble(paramLine.split("[\\>,]")[1].split(" ")[0]);
				double ty = Double.parseDouble(paramLine.split("[\\>,]")[1].split(" ")[1]);
				double thetaDeg = Double.parseDouble(paramLine.split("[\\=]")[2]);
				double theta = Math.PI * thetaDeg / 180;
				double anchorx = imp.getWidth() / 2;
				double anchory = imp.getHeight() / 2;
				for (Roi roi : allRois) {
					if (slice == roi.getTPosition()) {
						double x1 = roi.getBounds().getCenterX();
						double y1 = roi.getBounds().getCenterY();
						double x2 = 0;
						double y2 = 0;
						double[] preAffinePoints = { x1, y1 };
						double[] postAffinePoints = { x2, y2 };
						AffineTransform at = new AffineTransform();
						at.setToTranslation(tx, ty);
						at.transform(preAffinePoints, 0, preAffinePoints, 0, 1);
						at.setToRotation(-theta, anchorx, anchory);
						at.transform(preAffinePoints, 0, postAffinePoints, 0, 1);
						roi.setLocation((int) (postAffinePoints[0] - roi.getBounds().getWidth() / 2),
								(int) (postAffinePoints[1] - roi.getBounds().getHeight() / 2));
					}
				}
			}
		}
	}

	public void valueChanged(ListSelectionEvent e) {
		int index = 0;
		if (listModel.getSize() == 0)
			return;
		if (list.getSelectedIndices().length == 0)
			return;
		index = list.getSelectedIndices()[0];
		if (index < 0)
			index = 0;
		if (imp != null) {
			if (list.getSelectedIndices().length <= 1) {
				restore(imp, index, true);
			}
			if (record()) {
				if (Recorder.scriptMode())
					Recorder.recordCall("rm.select(imp, " + index + ");");
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

	private void openCsv(String path) { // for Elegance output
		boolean wasVis = this.isVisible();
		this.setVisible(false);
		busy = true;
		// showAll(SHOW_ALL);
		String s = IJ.openAsString(path);
		String[] objectLines = s.split("\n");
		int fullCount = objectLines.length;

		if ((path.contains("jsh_object") || path.contains("n2u_innerjoin")) 
//				&& s.contains("OBJ_Name")
				) {  // Emmons Lab data
			int[] sliceNumbers = new int[fullCount];
			for (int f = 0; f < fullCount; f++) {
				if (objectLines[f].contains("N2UNR"))
					sliceNumbers[f] = Integer.parseInt(objectLines[f].substring(objectLines[f].indexOf("N2UNR") + 5,
							objectLines[f].indexOf("N2UNR") + 8));
				if (objectLines[f].contains("JSHJSH"))
					sliceNumbers[f] = Integer.parseInt(objectLines[f].substring(objectLines[f].indexOf("JSHJSH") + 6,
							objectLines[f].indexOf("JSHJSH") + 9));
				IJ.log("" + sliceNumbers[f]);
			}
			// IJ.log(s);

			long count = 0;

			Hashtable<String, String> objectHash = new Hashtable<String, String>();
//			String centerZtestString = objectLines[1].split(",")[4].replace("\"", "");
//			String centerZroot = null;
//			for (int i = 0; i < centerZtestString.length(); i++) {
//				if (objectLines[2].split(",")[4].replace("\"", "").contains(centerZtestString.substring(0, i))
//						&& !centerZtestString.substring(i - 1 >= 0 ? i - 1 : 0, centerZtestString.length() - 1)
//								.matches("\\d*")) {
//					centerZroot = centerZtestString.substring(0, i);
//				}
//			}

			long nRois = 0;

			for (int i = 1; i < objectLines.length; i++) {
				String objectLine = objectLines[i];
				objectHash.put(objectLine.split(",")[0].replace("\"", ""), objectLine);
			}
			double shrinkFactor = 1 / IJ.getNumber("XY dimension should be reduced in scale by what factor?", 1);

			imp.getWindow().setVisible(false);

			Hashtable<String, ArrayList<String>> synapsePairFrequencyHashtable = new Hashtable<String, ArrayList<String>>();
			Hashtable<String, ArrayList<String>> synapseNameTallyHashtable = new Hashtable<String, ArrayList<String>>();

			for (int obj = 1; obj < objectLines.length; obj++) {
				String sObj = objectLines[obj].replace("[", "").replace("]", "");
				String[] objChunks = sObj.replace(",,,",",NULL,NULL,").replace(",NULL,NULL,", ",\"NULL\",\"NULL\",").replace(",,",",NULL,").replace(",NULL,", ",\"NULL\",").split(",\"");
				String objType = objChunks[6].replace("\"", "");
				String imageNumber = objChunks[4].replace("\"", "");
				int zSustain = Integer.parseInt(objChunks[21].replace(","," ").replace("\"", "").replace("zS", "").trim());
				String presynName = objChunks[17].replace("\"", "");
				String[] postsynNames = objChunks[18].replace("\"", "").split("[,&]");

				String roiNameStart = "\"" + presynName + objType
						+ Arrays.deepToString(postsynNames).replace("[", "").replace("]", "").replace("_", "").replace(", ", "&");
				roiNameStart = roiNameStart.replaceAll("(\\[|\\])", "");
				if (synapseNameTallyHashtable.get(roiNameStart) == null) {
					synapseNameTallyHashtable.put(roiNameStart, new ArrayList<String>());
				}
				Character incChar = 'A'-1;
				Character prefixChar = 'A'-1;
				String incString = "";
				for (int c = 0; c <= synapseNameTallyHashtable.get(roiNameStart).size(); c++) {
					incChar++;
					if (incChar > 'Z') { 
						prefixChar++;
						incChar = 'A';
						if (prefixChar > 'A')
							IJ.wait(10);
					}
					incString = ""+(prefixChar > ((char)('A'-1))?prefixChar:"")+""+incChar;
				}
				synapseNameTallyHashtable.get(roiNameStart).add(roiNameStart + "~" + incString + " \"");
				Color roiColor = objType.contains("chemical") ? Color.white : Color.yellow;
				if (roiNameStart.contains("uncertain"))
					roiColor = objType.contains("chemical") ? Color.pink : Color.orange;
				int centerX = (int) (Integer.parseInt(sObj.split(",")[1].replace("\"", "")) / shrinkFactor);
				int centerY = (int) (Integer.parseInt(sObj.split(",")[2].replace("\"", "")) / shrinkFactor);
				String centerZtestString = sObj.split(",")[4].replace("\"", "");
				String centerZroot = null;
				for (int i = 0; i < centerZtestString.length(); i++) {
					if (sObj.split(",")[4].replace("\"", "").contains(centerZtestString.substring(0, i))
							&& !centerZtestString.substring(i - 1 >= 0 ? i - 1 : 0, centerZtestString.length() - 1)
									.matches("\\d*")) {
						centerZroot = centerZtestString.substring(0, i);
					}
				}
				int frontZ = Integer.parseInt(sObj.split(",")[4].replace("\"", "").replace(centerZroot, ("")));

				//// SPECIAL CASE ONLY FOR honoring JSH image GAPS AT Z56 z162-166
				if (imageNumber.startsWith("JSHJSH")) {
					if (frontZ > 55) {
						frontZ++;
					}
					if (frontZ > 162) {
						frontZ++;
						frontZ++;
						frontZ++;
						frontZ++;
					}
				}
				if (imageNumber.startsWith("N2UNR")) {
					IJ.wait(0);
				}
				if (imageNumber.startsWith("N2UVC")) {
					frontZ = frontZ + 182;
				}
				if (imageNumber.startsWith("N2UDC")||imageNumber.startsWith("N2ULEFT")||imageNumber.startsWith("N2URIGHT")) {
					continue;
				}
				int adjustmentZ = 0;
////				
//	zSustain =1;		//choice to eliminate stupid depth traces (highly inaccurate in several ways.	
////				
				for (int susStep = 0; susStep < zSustain; susStep++) {
					int plotZ = frontZ + susStep - adjustmentZ;
					if (plotZ < 1 || plotZ > imp.getNSlices()) {
						continue;
					}

					if (roiNameStart.contains("|cell")) {
					} else {

						int roiDiameter = (int) (25/shrinkFactor);
						Roi oRoi = new OvalRoi(centerX - roiDiameter / 2, centerY - roiDiameter / 2, roiDiameter,
								roiDiameter);
						// Roi oRoi= new Roi(centerX-roiDiameter/2, centerY-roiDiameter/2, roiDiameter,
						// roiDiameter);
						oRoi.setName(roiNameStart + "~" + incString + " \"");
						this.setRoiFillColor(oRoi, roiColor);
						oRoi.setPosition(1, plotZ, 1);
//						imp.setPosition(1, plotZ, 1);
//						this.addRoi(oRoi);
						this.addRoi(oRoi, false, oRoi.getStrokeColor(), oRoi.getFillColor(), -1, false);
						for (String postsynName : postsynNames) {
							IJ.log(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
							if (synapsePairFrequencyHashtable
									.get(presynName + "," + postsynName + "," + objType) == null) {
								synapsePairFrequencyHashtable.put(presynName + "," + postsynName + "," + objType,
										new ArrayList<String>());
							}
							synapsePairFrequencyHashtable.get(presynName + "," + postsynName + "," + objType)
									.add(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
						}
					}
				}
			count++;
			IJ.showStatus("" + count + "/" + fullCount + " Tags loaded for " + imp.getTitle());
			}
			updateShowAll();
			// this.imp.setTitle(impTitle);
			imp.getWindow().setVisible(true);
			this.setVisible(wasVis);
			busy = false;
			for (Object key : synapsePairFrequencyHashtable.keySet()) {
				IJ.log(((String) key) + "=" + synapsePairFrequencyHashtable.get((String) key).size());
			}
		} else if (path.contains("synapse_key")) {  // Mei lab synapse data

			int[] sliceNumbers = new int[fullCount];
			String[] pres = new String[fullCount];
			String[][] posts = new String[fullCount][];
			String[] segments = new String[fullCount];
			String[] xs = new String[fullCount];
			String[] ys = new String[fullCount];
			String[] zs = new String[fullCount];

			for (int f = 0; f < fullCount; f++) {
				if (objectLines[f].startsWith("Presynaptic"))
					continue;
				if (objectLines[f].contains("\"")) {  //this and below case true for mei lab synapses, polyadic and monadic respectively
					sliceNumbers[f] = Integer.parseInt(objectLines[f].split("\"")[2].split(",")[4]);
					pres[f] = objectLines[f].split(",")[0];
					posts[f] = objectLines[f].split("\"")[1].split(",");
					segments[f] = objectLines[f].split("\"")[2].split(",")[1];
					xs[f] = objectLines[f].split("\"")[2].split(",")[2];
					ys[f] = objectLines[f].split("\"")[2].split(",")[3];
					zs[f] = objectLines[f].split("\"")[2].split(",")[4];
				} else {

					sliceNumbers[f] = Integer.parseInt(objectLines[f].split(",")[5]);
					pres[f] = objectLines[f].split(",")[0];
					posts[f] = new String[] { objectLines[f].split(",")[1] };
					segments[f] = objectLines[f].split(",")[2];
					xs[f] = objectLines[f].split(",")[3];
					ys[f] = objectLines[f].split(",")[4];
					zs[f] = objectLines[f].split(",")[5];
				}
				IJ.log("" + sliceNumbers[f]);
			}
			// IJ.log(s);

			long count = 0;

			Hashtable<String, String> objectHash = new Hashtable<String, String>();
//			String centerZtestString = objectLines[1].split(",")[4].replace("\"", "");
//			String centerZroot = null;
//			for (int i=0; i<centerZtestString.length(); i++){
//				if (objectLines[2].split(",")[4].replace("\"", "")
//						.contains(centerZtestString.substring(0,i))
//						&& !centerZtestString.substring(i-1>=0?i-1:0,centerZtestString.length()-1).matches("\\d*")) {
//					centerZroot = centerZtestString.substring(0,i);
//				}
//			}

			long nRois = 0;

			for (int i = 0; i < objectLines.length; i++) {
				String objectLine = objectLines[i];
				if (pres[i] != null)
					objectHash.put(pres[i], objectLine);
			}
			double shrinkFactor = 1 / IJ.getNumber("XY dimension should be reduced in scale by what factor?", 1);
			shrinkFactor = shrinkFactor * imp.getCalibration().pixelWidth;

			imp.getWindow().setVisible(false);

			Hashtable<String, ArrayList<String>> synapsePairFrequencyHashtable = new Hashtable<String, ArrayList<String>>();
			Hashtable<String, ArrayList<String>> synapseNameTallyHashtable = new Hashtable<String, ArrayList<String>>();

			for (int obj = 0; obj < objectLines.length; obj++) {
				if (objectLines[obj].startsWith("Presynaptic"))
					continue;
				count++;
				IJ.showStatus("" + count + "/" + fullCount + " Tags loaded for " + imp.getTitle());
				String sObj = objectLines[obj];
				String objType = "undefined";
				String imageNumber = zs[obj];
				int zSustain = 1;
				String presynName = pres[obj];
				String[] postsynNames = posts[obj];

				String roiNameStart = "\"" + presynName + objType
						+ Arrays.deepToString(postsynNames).replace("[", "").replace("]", "").replace(", ", "&");
				roiNameStart = roiNameStart.replaceAll("(\\[|\\])", "");
				if (synapseNameTallyHashtable.get(roiNameStart) == null) {
					synapseNameTallyHashtable.put(roiNameStart, new ArrayList<String>());
				}
				Character incChar = 'A'-1;
				Character prefixChar = 'A'-1;
				String incString = "";
				for (int c = 0; c <= synapseNameTallyHashtable.get(roiNameStart).size(); c++) {
					incChar++;
					if (incChar > 'Z') { 
						prefixChar++;
						incChar = 'A';
						if (prefixChar > 'A')
							IJ.wait(10);
					}
					incString = ""+(prefixChar > ((char)('A'-1))?prefixChar:"")+""+incChar;
				}
				synapseNameTallyHashtable.get(roiNameStart).add(roiNameStart + "~" + incString + " \"");

				Color roiColor = objType.contains("chemical") ? Color.white : Color.yellow;
				if (roiNameStart.contains("uncertain"))
					roiColor = objType.contains("chemical") ? Color.pink : Color.orange;
				int centerX = (int) (Integer.parseInt(xs[obj]) / shrinkFactor);
				int centerY = (int) (Integer.parseInt(ys[obj]) / shrinkFactor);
				int frontZ = Integer.parseInt(zs[obj]);

				//// SPECIAL CASE ONLY FOR honoring JSH image GAPS AT Z56 z162-166
				if (imageNumber.startsWith("JSHJSH")) {
					if (frontZ > 55) {
						frontZ++;
					}
					if (frontZ > 162) {
						frontZ++;
						frontZ++;
						frontZ++;
						frontZ++;
					}
				}

				int adjustmentZ = 0;
				for (int susStep = 0; susStep < zSustain; susStep++) {
					int plotZ = frontZ + susStep - adjustmentZ;
					if (plotZ < 1 || plotZ > imp.getNSlices()) {
						continue;
					}

					if (roiNameStart.contains("|cell")) {
					} else {

						int roiDiameter = (int) (25/shrinkFactor);
						Roi oRoi = new OvalRoi(centerX - roiDiameter / 2, centerY - roiDiameter / 2, roiDiameter,
								roiDiameter);

						oRoi.setName(roiNameStart + "~" + incString + " \"");
						this.setRoiFillColor(oRoi, roiColor);
						oRoi.setPosition(1, plotZ, 1);
//						imp.setPosition(1, plotZ, 1);
//						this.addRoi(oRoi);
						this.addRoi(oRoi, false, oRoi.getStrokeColor(), oRoi.getFillColor(), -1, false);
						for (String postsynName : postsynNames) {
							IJ.log(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
							if (synapsePairFrequencyHashtable
									.get(presynName + "," + postsynName + "," + objType) == null) {
								synapsePairFrequencyHashtable.put(presynName + "," + postsynName + "," + objType,
										new ArrayList<String>());
							}
							synapsePairFrequencyHashtable.get(presynName + "," + postsynName + "," + objType)
									.add(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
						}
					}
				}
			}
			updateShowAll();
			// this.imp.setTitle(impTitle);
			imp.getWindow().setVisible(true);
			this.setVisible(wasVis);
			busy = false;
			for (Object key : synapsePairFrequencyHashtable.keySet()) {
				IJ.log(((String) key) + "=" + synapsePairFrequencyHashtable.get((String) key).size());
			}

		} else {
			this.setVisible(true);
			return;
		}
	}
	
	public void synapseStatsFromRois() {
		Roi cropRoi = imp.getRoi();
		if (cropRoi == null)
			cropRoi = new Roi(0,0,imp.getWidth(),imp.getHeight());
		Hashtable<String, ArrayList> synapsePairFrequencyHashtable = new Hashtable<String, ArrayList>();			
		for (Roi roi:getShownRoisAsArray()) {
			if (!cropRoi.contains((int)roi.getBounds().getCenterX(), (int)roi.getBounds().getCenterY())) {
				continue;
			}
			String name = roi.getName();
			String[] nameChunks = name.split("(\"|electric|chemical|undefined|\\~)");
//			IJ.log(Arrays.toString(nameChunks));
			String presynName = nameChunks[1];
			String[] postsynNames = nameChunks[2].split("\\&");
			String plotZ = ""+roi.getZPosition();
			String objType = name.split("\""+presynName+"|"+postsynNames[0])[1];
			
			for (String postsynName : postsynNames) {
				IJ.log(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);

				if (synapsePairFrequencyHashtable
						.get(presynName + "," + postsynName + "," + objType) == null) {
					synapsePairFrequencyHashtable.put(presynName + "," + postsynName + "," + objType,
							new ArrayList<String>());
				}
				synapsePairFrequencyHashtable.get(presynName + "," + postsynName + "," + objType)
				.add(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
			}
		}
		updateShowAll();
		Object[] keysArray = synapsePairFrequencyHashtable.keySet().toArray();
		Arrays.sort(keysArray);
		IJ.log(" ");
		IJ.log(" ");
		IJ.log(" ");
		IJ.log("pre,post,type,synapses");
		for (Object key : keysArray) {
			String outLine = (((String) key) + "," + synapsePairFrequencyHashtable.get((String) key).size());
			IJ.log(outLine);
		}

	}

	public void windowClosed(WindowEvent e) {
		for (int i = 0; i < compImps.size(); i++) {
			if (compImps.get(i) == e.getSource()) {
				compImps.get(i).getWindow().removeWindowFocusListener(colorLegend);
				compImps.get(i).getCanvas().removeMouseMotionListener(colorLegend);
				compImps.get(i).getCanvas().removeMouseListener(colorLegend);
				compImps.remove(i);
			}
		}
		for (int i = 0; i < projYImps.size(); i++) {
			if (projYImps.get(i) == e.getSource()) {
				projYImps.get(i).getWindow().removeWindowFocusListener(colorLegend);
				projYImps.get(i).getCanvas().removeMouseMotionListener(colorLegend);
				projYImps.get(i).getCanvas().removeMouseListener(colorLegend);
				projYImps.remove(i);
			}
		}
		for (int i = 0; i < projZImps.size(); i++) {
			if (projZImps.get(i) == e.getSource()) {
				projZImps.get(i).getWindow().removeWindowFocusListener(colorLegend);
				projZImps.get(i).getCanvas().removeMouseMotionListener(colorLegend);
				projZImps.get(i).getCanvas().removeMouseListener(colorLegend);
				projZImps.remove(i);
			}
		}
	}

	public JTextField getTextSearchField() {
		return textFilterField;
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
//		hyperstackCheckbox.setSelected(true);   //what the hell is this?
		if (e.getSource() == textFindingField) {
			String searchString = textFindingField.getText().toLowerCase();
			for (int i = 0; i < listModel.getSize(); i++) {
				String hitCandidate = listModel.get(i).toLowerCase();
				if (hitCandidate.contains(searchString)) {
					list.ensureIndexIsVisible(i);
					break;
				}
			}
		}
	}

	public void zapDuplicateRois() {
		Roi[] ra = this.getFullRoisAsArray();
		for (int r = 0; r < ra.length; r++) {
			Roi roi = ra[r];
			for (int s = ra.length - 1; s > r; s--) {
				Roi roi2 = ra[s];
				if (roi.getName() != (roi2).getName()) {
					boolean duplicated = roi.equals(roi2);
					if (duplicated) {
						IJ.log("zap " + roi2.getName());
						rois.remove(listModel.getElementAt(s));
						fullListModel.removeElement(listModel.getElementAt(s));
						listModel.remove(s);
						textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
						if (imp.getWindow() != null) {
							imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
							imp.getWindow().countLabel.repaint();
							// imp.getWindow().tagsButton.setText(""+fullListModel.size());

							imp.getWindow().tagsButton.repaint();
						}
						ra = this.getFullRoisAsArray();
						s--;
						r--;
					}
				}
			}
		}
	}

// ALT VERSION OF ZAP DUPS THAT DOES NOT SEEM TO WORK RIGHT BUT IS FASTER....
//	Roi[] ra = this.getFullRoisAsArray();
//	ArrayList<Integer> indexesToDelete = new ArrayList<Integer>();
//	for (int r=0;r<ra.length;r++) {
//		Roi roi= ra[r];
//		if (!indexesToDelete.contains(r)){
//			for (int s=ra.length-1;s>r;s--) {
//				Roi roi2= ra[s];
//				if (roi.getName() != (roi2).getName()){
//					boolean duplicated = roi.equals(roi2);
//					if (duplicated) {
//						//						IJ.log("zap "+ roi2.getName());
//						indexesToDelete.add(s);
//
//						//						rois.remove(listModel.getElementAt(s));
//						//						fullListModel.removeElement(listModel.getElementAt(s));
//						//						listModel.remove(s);
//						//						textCountLabel.setText(""+ listModel.size() +"/"+ fullListModel.size());
//						//						if (imp.getWindow()!=null) {
//						//							imp.getWindow().countLabel.setText(""+ listModel.size() +"/"+ fullListModel.size() +"");
//						//							imp.getWindow().countLabel.repaint();			
//						//							//imp.getWindow().tagsButton.setText(""+fullListModel.size());
//						//
//						//							imp.getWindow().tagsButton.repaint();
//						//						}
//						//						ra = this.getFullRoisAsArray();
//						//						s--;
//						//						r--;
//					}
//				}
//			}
//		}
//	}
//	int[] itd = new int[indexesToDelete.size()];
//	for (int i=0; i<itd.length; i++){
//		itd[i] = indexesToDelete.get(i);
//	}
//	setSelectedIndexes(itd);
//	delete(false);
//

	public void importStarryNiteNuclei(String zipPath) {
		File zipFile = new File(zipPath);
		if (!zipFile.canRead()) {

			zipPath = IJ.getFilePath("Select the zip file with <nuclei> files");
			zipFile = new File(zipPath);
		}
		boolean useGivenNames = zipPath.endsWith("_ATAutoCorrected.zip") || zipPath.toLowerCase().endsWith("_atac.zip");
		String str = openZipNucleiAsString(zipPath);
		String[] lines = str.split("\n");
		// imp.hide();
		// this.setVisible(false);
		int frame = 1;
		Hashtable<String, String[]> prevHash = new Hashtable<String, String[]>();
		Hashtable<String, String[]> nextHash = new Hashtable<String, String[]>();

		ArrayList<String> newNames = new ArrayList<String>();

		list.setModel(new DefaultListModel<String>());

		for (int i = 0; i < lines.length; i++) {
			String nextLine = lines[i];
			if (nextLine.length() == 0) {
				continue;
			}
			if (nextLine.startsWith("parameters/")) {
				continue;
			}
			String[] cellData = nextLine.split(", *");
			if (cellData.length == 1) {
				if (cellData[0].equals("nuclei/"))
					continue;
				frame = Integer.parseInt(cellData[0].trim().replace("nuclei/t", "").replace("-nuclei", ""));
				prevHash = nextHash;
				nextHash = new Hashtable<String, String[]>();
			} else {
				// imp.setPositionWithoutUpdate(imp.getChannel(), imp.getSlice(), frame);
				if (Integer.parseInt(cellData[1].trim()) != 0) {
					nextHash.put(cellData[0], new String[] { cellData[2], cellData[3], cellData[4], cellData[9] });
					// imp.setPositionWithoutUpdate(imp.getChannel(),
					// (int)Double.parseDouble(cellData[7].trim()), frame);

					Roi newOval = new OvalRoi(
							Integer.parseInt(cellData[5].trim()) - Integer.parseInt(cellData[8].trim()) / 2,
							Integer.parseInt(cellData[6].trim()) - Integer.parseInt(cellData[8].trim()) / 2,
							Integer.parseInt(cellData[8].trim()), Integer.parseInt(cellData[8].trim()));
					newOval.setImage(imp);
					newOval.setPosition(imp.getChannel(), (int) Double.parseDouble(cellData[7].trim()), frame);

					String currID = cellData[0];
					String prevCell = cellData[2];
					String[] prevCellThings = null;
					String prevCellDesc1 = "";
					String prevCellDesc2 = "";

					if (prevHash != null && (!useGivenNames || cellData[9].startsWith("Nuc"))) {
						if (prevHash.get(cellData[2]) != null) {
							prevCellThings = prevHash.get(cellData[2]);
							if (prevCellThings[1].equalsIgnoreCase(cellData[0])) {
								prevCellDesc1 = prevCellThings[1];
								prevCellDesc2 = prevCellThings[2];
								if (!prevCellThings[2].equalsIgnoreCase("-1")) {
									newNames.add(prevCellThings[3] + "g");
									newOval.setName(prevCellThings[3]+"g");
									nextHash.put(cellData[0], new String[] { cellData[2], cellData[3], cellData[4],
											prevCellThings[3] + "g" });

								} else {
									newNames.add(prevCellThings[3]);
									newOval.setName(prevCellThings[3]);
									nextHash.put(cellData[0],
											new String[] { cellData[2], cellData[3], cellData[4], prevCellThings[3] });

								}
							} else if (prevCellThings[2].equalsIgnoreCase(cellData[0])) {
								newNames.add(prevCellThings[3] + "h");
								newOval.setName(prevCellThings[3]+"h");
								nextHash.put(cellData[0], new String[] { cellData[2], cellData[3], cellData[4],
										prevCellThings[3] + "h" });
							}
						} else {
							newNames.add(cellData[9]);
							newOval.setName(cellData[9]);
						}
					} else {
						newNames.add(cellData[9]);
						newOval.setName(cellData[9]);
					}
					addRoi(newOval, false, null, Colors.decode("#44ff0000", Color.red), 1, false);

					IJ.showStatus("importing nucleus " + getCount());
				}
			}
		}

		String[] newNamesArray = newNames.toArray(new String[newNames.size()]);
		int[] nameIndexes = new int[newNames.size()];
		for (int n = 0; n < nameIndexes.length; n++) {
			nameIndexes[n] = n;
		}
		list.setModel(listModel);
//		rename(newNamesArray, nameIndexes, false);

		if (this.getCount() > 0) {
			select(-1);
			saveMultiple(getAllShownIndexes(), zipFile.getParent() + File.separator + "NucleiRoiSet.zip");
		}

//		if (!(zipPath.contains("Unskipped.zip"))){
//			respaceStarryNiteNuclei(zipPath, 6);
//		}

	}

	public static void respaceStarryNiteNuclei(String zipPath, int skipFactor) {
		File zipFile = new File(zipPath);
		if (!zipFile.canRead()) {

			zipPath = IJ.getFilePath("Select the zip file with <nuclei> files");
			zipFile = new File(zipPath);
		}
		String str = openZipNucleiAsString(zipPath);
		String outStr = "";
		String[] lines = str.split("\n");
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < lines.length; i++) {
			String nextLine = lines[i];
			if (nextLine.length() == 0) {
//				continue;
			}
			if (nextLine.startsWith("parameters/")) {
//				continue;
			}
			String[] cellData = nextLine.split(", *");
			if (cellData.length == 1) {
//				continue;
			} else {
				if (cellData[9] != " ") {
					int slicePos = (int) Double.parseDouble(cellData[7].trim());
					lines[i] = nextLine.replace(cellData[7].trim(), "" + (1 + ((slicePos - 1) * skipFactor)));
				}
			}
//			outStr = outStr +"\n"+ lines[i];
			sb.append("\n" + lines[i]);
			outStr = sb.toString();
		}
		saveZipNuclei(outStr, zipPath.replace("Skipped", "").replace(".zip", "_Unskipped.zip"));
	}

//	public void respaceStarryNiteNucleiWithArrayList(String zipPath, int skipFactor) {
//		File zipFile = new File(zipPath);
//		if (!zipFile.canRead()) {
//
//			zipPath = IJ.getFilePath("Select the zip file with <nuclei> files");
//			zipFile = new File(zipPath);
//		}
//		ArrayList<Object> timepoints= openZipNucleiAsObjects(zipPath);
//
//		for (int i = 0; i < timepoints.size(); i++) {
//			Object nextTime = timepoints.get(i);
//			String nextTimeString = (String)nextTime;
//			String[] nextTimeLines = nextTimeString.split("\n");
//			for (int j = 0; j < nextTimeLines.length; j++) {
//				String nextLine = nextTimeLines[j];
//				if (nextLine.length()==0) {
//					//				continue;
//				}
//				if (nextLine.startsWith("parameters/")) {
//					//				continue;
//				}
//				String[] cellData = nextLine.split(", *");
//				if (cellData.length ==1){
//					//				continue;
//				} else {
//					if (cellData[9] !=" ") {
//						int slicePos = (int)Double.parseDouble(cellData[7].trim());
//						nextTimeLines[j] = nextLine.replace(cellData[7].trim(), ""+(1+((slicePos-1)*skipFactor)));
//					}
//				}
//				nextTimeString = nextTimeString.replace(nextLine, nextTimeLines[j]);
//			}
//			timepoints.set(i, nextTimeString);
//		}
//		saveZipNucleiFromArrayList(timepoints, zipPath.replace(".zip", "Unskipped.zip"));
//	}

//	ArrayList<Object> openZipNucleiAsObjects(String path) { 
//
//		ZipInputStream in = null; 
//		ByteArrayOutputStream out;
//		String str = "";
//
//		try { 
//			in = new ZipInputStream(new FileInputStream(path)); 
//
//			byte[] buf = new byte[1024*1024]; 
//			int len; 
//			ZipEntry entry = in.getNextEntry(); 
//			IJ.log("");
//
//			String string ="";
//
//			while (entry!=null) { 
//
//				String name = entry.getName(); 
//				IJ.log(name);
//				if (name.endsWith("-nuclei")) { 
//					out = new ByteArrayOutputStream(); 
//					while ((len = in.read(buf)) > 0) 
//						out.write(buf, 0, len); 
//					out.close(); 
//					string = out.toString();
//				} 
//				str = str+"\n"+name+"\n"+string;
//				entry = in.getNextEntry(); 
//			} 
//			in.close(); 
//		} catch (IOException e) {error(e.toString());} 
//		return str;
//	} 

	public static String openZipNucleiAsString(String path) {

		ZipInputStream in = null;
		ByteArrayOutputStream out;
		String str = "";
		double endImportTime = IJ.getNumber("Import up until which timepoint?", 1000);
		try {
			in = new ZipInputStream(new FileInputStream(path));

			byte[] buf = new byte[1024 * 1024];
			int len;
			ZipEntry entry = in.getNextEntry();
			IJ.log("");

			String string = "";

			while (entry != null) {

				String name = entry.getName();
				IJ.log(name);
				if (name.endsWith("-nuclei")) {
					if (name.contains("t"+(IJ.pad((int)(endImportTime + 1),3)+"-nuclei")))
						break;
					out = new ByteArrayOutputStream();
					while ((len = in.read(buf)) > 0)
						out.write(buf, 0, len);
					out.close();
					string = out.toString();
				}
				str = str + "\n" + name + "\n" + string;
				entry = in.getNextEntry();
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return str;
	}

//	boolean saveZipNucleiFromArrayList(ArrayList<Object> timepoints, String path) { 
//		try {
//			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(path)));
//			String nucleiFrame = "";
//			String nucleiLabel = "";			
//			for (int i=0; i<timepoints.size(); i++) {
//				String[] nucLines = ((String)timepoints.get(i)).split("\n");
//				if (nucLines[i].contains("nuclei/t")) {
//					zos.putNextEntry(new ZipEntry(nucleiLabel));
//					byte[] data = nucleiFrame.getBytes();
//					zos.write(data, 0, data.length);					
//					zos.closeEntry();
//
//					nucleiLabel = nucLines[i];
//					nucleiFrame = "";
//				} else {
//					nucleiFrame = nucleiFrame + nucLines[i] + "\n" ;
//				}
//			}
//			zos.close();
//		}
//		catch (IOException e) {
//			error(""+e);
//			return false;
//		}
//		return true;
//	} 

	public void exportROIsAsZippedStarryNiteNuclei(String zipPath) {

		File zipFile = new File(zipPath);
		String configPath = zipPath.replace("Unskipped.zip", "edited.xml");
		sortmode = 3;
		if (listModel.size() > 0 && listModel.get(0).split("_").length == 5) {
			if (sortmode > 0)
				sortmode++;
		}
		this.sort();

		Roi[] rois = this.getSelectedRoisAsArray();
		if (rois.length == 0) {
			rois = this.getFullRoisAsArray();
		}

		int maxT = 0;
		int padLength = 0;
		for (Roi roi : rois) {
			if (maxT < roi.getTPosition()) {
				maxT = roi.getTPosition();
				padLength = ("" + maxT).length();
			}
		}
		String nucString = "parameters/\n";

		int frame = 1;

		Hashtable<String, Integer> NameTimeToTnumberHashtable = new Hashtable<String, Integer>();

		for (int t = 1; t <= maxT; t++) {
			int hitCountA = 0;
			for (Roi roi : rois) {
				if (roi.getTPosition() == t && !roi.getName().contains("polar")) {
					hitCountA++;
					NameTimeToTnumberHashtable.put(roi.getName().split("\"")[1].trim() + "_" + t, hitCountA);
				}
			}
		}

		for (int t = 1; t <= maxT; t++) {
			nucString = nucString + "\nnuclei/t" + IJ.pad(t, padLength) + "-nuclei\n";
			int hitCountB = 0;
			for (Roi roi : rois) {
				String roiNameTrim = roi.getName().split("\"")[1].trim();
				if (roi.getTPosition() == t && !roi.getName().contains("polar") && !roi.getName().contains("Oval")
						&& !roi.getName().contains("\"\"") && !roiNameTrim.equals("")) {
					hitCountB++;
					boolean alreadyNextHit = false;
					int prevHit = -1;
					int nextHit = -1;
					int otherNextHit = -1;
					if (NameTimeToTnumberHashtable.get(roiNameTrim + "_" + (t - 1)) != null) {
						prevHit = NameTimeToTnumberHashtable.get(roiNameTrim + "_" + (t - 1));
					}
					if (NameTimeToTnumberHashtable
							.get(roiNameTrim.substring(0, roiNameTrim.length() - 1) + "_" + (t - 1)) != null) {
						prevHit = NameTimeToTnumberHashtable
								.get(roiNameTrim.substring(0, roiNameTrim.length() - 1) + "_" + (t - 1));
					}

					if (roiNameTrim.equals("E")) {
						if (NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1));
						}
					}
					if (roiNameTrim.equals("MS")) {
						if (NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1));
						}
					}

					if (roiNameTrim.equals("P3")) {
						if (NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1));
						}
					}
					if (roiNameTrim.equals("C")) {
						if (NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1));
						}
					}

					if (roiNameTrim.equals("P4")) {
						if (NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1));
						}
					}
					if (roiNameTrim.equals("D")) {
						if (NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1));
						}
					}
					if (roiNameTrim.equals("Z2")) {
						if (NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1));
						}
					}
					if (roiNameTrim.equals("Z3")) {
						if (NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1)) != null) {
							prevHit = NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1));
						}
					}

					if (NameTimeToTnumberHashtable.get(roiNameTrim + "_" + (t + 1)) != null) {
						nextHit = NameTimeToTnumberHashtable.get(roiNameTrim + "_" + (t + 1));
					}

					if (roiNameTrim.equals("EMS")) {
						if (NameTimeToTnumberHashtable.get("E" + "_" + (t + 1)) != null) {
							nextHit = NameTimeToTnumberHashtable.get("E" + "_" + (t + 1));
						}
						if (NameTimeToTnumberHashtable.get("MS" + "_" + (t + 1)) != null) {
							otherNextHit = NameTimeToTnumberHashtable.get("MS" + "_" + (t + 1));
						}
					}

					if (roiNameTrim.equals("P2")) {
						if (NameTimeToTnumberHashtable.get("P3" + "_" + (t + 1)) != null) {
							nextHit = NameTimeToTnumberHashtable.get("P3" + "_" + (t + 1));
						}
						if (NameTimeToTnumberHashtable.get("C" + "_" + (t + 1)) != null) {
							otherNextHit = NameTimeToTnumberHashtable.get("C" + "_" + (t + 1));
						}
					}

					if (roiNameTrim.equals("P3")) {
						if (NameTimeToTnumberHashtable.get("P4" + "_" + (t + 1)) != null) {
							nextHit = NameTimeToTnumberHashtable.get("P4" + "_" + (t + 1));
						}
						if (NameTimeToTnumberHashtable.get("D" + "_" + (t + 1)) != null) {
							otherNextHit = NameTimeToTnumberHashtable.get("D" + "_" + (t + 1));
						}
					}
					if (roiNameTrim.equals("P4")) {
						if (NameTimeToTnumberHashtable.get("Z2" + "_" + (t + 1)) != null) {
							nextHit = NameTimeToTnumberHashtable.get("Z2" + "_" + (t + 1));
						}
						if (NameTimeToTnumberHashtable.get("Z3" + "_" + (t + 1)) != null) {
							otherNextHit = NameTimeToTnumberHashtable.get("Z3" + "_" + (t + 1));
						}
					}

					for (String suffix : new String[] { "a", "p", "m", "n", "l", "r", "g", "h" }) {
						if (!alreadyNextHit
								&& NameTimeToTnumberHashtable.get(roiNameTrim + suffix + "_" + (t + 1)) != null) {
							nextHit = NameTimeToTnumberHashtable.get(roiNameTrim + suffix + "_" + (t + 1));
							alreadyNextHit = true;
						}
						if (alreadyNextHit
								&& NameTimeToTnumberHashtable.get(roiNameTrim + suffix + "_" + (t + 1)) != null) {
							otherNextHit = NameTimeToTnumberHashtable.get(roiNameTrim + suffix + "_" + (t + 1));
						}
					}
					String cellString = "" + hitCountB + ",  1,  " + prevHit + ",  " + nextHit + ",  " + otherNextHit
							+ ",  " + (int) roi.getBounds().getCenterX() + ",  " + (int) roi.getBounds().getCenterY()
							+ ",  " + roi.getZPosition() + ",  " + roi.getBounds().width + ",  " + roiNameTrim
							+ ",  0,  0, 0, 0, , 0, 0, 0, 0, 0,";
					nucString = nucString + cellString + "\n";
				}

			}
		}
		nucString = nucString + "\nnuclei/t" + "end" + "-nuclei\n";
		File oldZipFile = new File(zipPath);
		if (oldZipFile.canRead()) {
			oldZipFile.renameTo(new File(zipPath.replace("Unskipped", "Unskipped" + oldZipFile.lastModified())));
		}
		IJ.log(nucString);
		saveZipNuclei(nucString, zipPath);
		File pngFile = new File(zipPath.replace(".zip", "Lineage.png"));
		String[] paramStrings = new String[] { "P0", "" + maxT, "-500", "5000", "10", "2", "0" };
//		if (aceTree != null){
//			aceTree.exit();
//		}
		if (imp.getStack() instanceof ImageStack) {
			aceTree = AceTree.getAceTree(imp, zipPath);
		} else if (new File(configPath).canRead()) {
			aceTree = AceTree.getAceTree(configPath);
		}
		if (aceTree != null) {
			VTreeImpl vti = aceTree.getVtree().getiVTreeImpl();
			vti.printTree(paramStrings, true, true, pngFile.getName(), pngFile.getParent());
			vti.showTree(paramStrings, true, true);
			vti.getTestCanvas().addMouseListener(this);
		}

		aceTree.saveNuclei(new File(zipPath.replace(".zip", "_ATAutoCorrected.zip")));

	}

	static boolean saveZipNuclei(String nucString, String path) {
		String[] nucLines = nucString.split("\n");
		try {
			ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(path)));
			BufferedOutputStream bos = new BufferedOutputStream(zos);
			String nucleiFrame = "";
			String nucleiLabel = "";
			// kind of stupid looping, but works if catch last i...
			for (int i = 0; i < nucLines.length; i++) {
				if (nucLines[i].contains("nuclei/t")) {
					if (nucleiLabel != "") {
						zos.putNextEntry(new ZipEntry(nucleiLabel));
						byte[] data = nucleiFrame.getBytes();
						bos.write(data, 0, data.length);
						bos.flush();
					}
					nucleiLabel = nucLines[i];
					nucleiFrame = "";
				} else {
					nucleiFrame = nucleiFrame + nucLines[i] + "\n";
				}
			}
			zos.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public double getRoiRescaleFactor() {
		return roiRescaleFactor;
	}

	public void setRoiRescaleFactor(double roiRescaleFactor) {
		this.roiRescaleFactor = roiRescaleFactor;
		isRoiScaleFactorSet = true;

	}

	public void mapNearNeighborContacts() {
		Roi[] selRois = this.getSelectedRoisAsArray();
		expansionDistance = IJ.getNumber("Distance (pixels) for contact partner search", expansionDistance);
		ArrayList<String> cellsAlreadyMapped = new ArrayList<String>();
		for (Roi roi : selRois) {
			if (cellsAlreadyMapped.contains(roi.getName().split("\"")[1])) {
				continue;
			}
			for (Roi queryRoi : this.getROIsByName().get("\"" + roi.getName().split("\"")[1] + "\"")) {
				if (!queryRoi.getName().split("\"")[1].equalsIgnoreCase(roi.getName().split("\"")[1])) {
					continue;
				}
				int cPos = queryRoi.getCPosition();
				int zPos = queryRoi.getZPosition();
				int tPos = queryRoi.getTPosition();
				Roi dupRoi = (Roi) queryRoi.clone();

				Roi[] sameSliceRois = this.getROIsByNumbers().get("" + cPos + "_" + zPos + "_" + tPos)
						.toArray(new Roi[1]);
				for (Roi testRoi : sameSliceRois) {
					if (zPos != testRoi.getZPosition()
							|| queryRoi.getName().split("\"")[1].equalsIgnoreCase(testRoi.getName().split("\"")[1])
							|| testRoi.getName().split("\"")[1].trim().contains("by")) {
						continue;
					}
					Color testColor = testRoi.getFillColor();
					String andName = "" + queryRoi.getName().split("\"")[1].trim() + "by"
							+ testRoi.getName().split("\"")[1].trim();

					Roi scaledRoi = null;

					scaledRoi = RoiEnlarger.enlarge(testRoi, expansionDistance);

					Roi andRoi = (new ShapeRoi(scaledRoi).and(new ShapeRoi(dupRoi)));
					if (andRoi != null && andRoi.getBounds().getWidth() > 0) {

						andRoi.setName(andName);

						andRoi.setPosition(cPos, zPos, tPos);
						this.setRoiFillColor(andRoi, testColor);
						this.addRoi(andRoi, false, null, testColor, -1, false);
					}
				}
			}
			cellsAlreadyMapped.add(roi.getName().split("\"")[1]);
		}
	}

	public void colorTagsByGroupInteractionRules() {

		String xlsPath = IJ.getFilePath("Table of Cell Groups?");
		String[] tableRows = IJ.openAsString(xlsPath).split("\n");
		String rowSplitter = "\t";
		if (xlsPath.endsWith(".csv")) {
			rowSplitter = ",";
		}
		String[][] table2Darray = new String[tableRows[0].split(rowSplitter).length][tableRows.length];
		for (int row = 0; row < tableRows.length; row++) {
			String[] rowChunks = tableRows[row].split(rowSplitter);
			for (int col = 0; col < rowChunks.length; col++) {
				table2Darray[col][row] = rowChunks[col];
			}
		}

		int header = 0;
		int leftMargin = 0;
		if (table2Darray[0][0].equalsIgnoreCase("Group")) {
			header++;
			leftMargin++;
			if (table2Darray[0][1].equalsIgnoreCase("Color")) {
				header++;
			}
		}

		String[][] allBundleArrays = new String[tableRows[0].split(rowSplitter).length - leftMargin][tableRows.length
				- header];
		String[] allBundleNames = new String[tableRows[0].split(rowSplitter).length - leftMargin];
		String[] allBundleColorStrings = new String[tableRows[0].split(rowSplitter).length - leftMargin];
		for (int c = 0; c < tableRows[0].split(rowSplitter).length - leftMargin; c++) {
			for (int r = 0; r < tableRows.length - header; r++) {
				if (table2Darray[c + leftMargin][r + header] != null
						&& table2Darray[c + leftMargin][r + header].length() > 0) {
					allBundleArrays[c][r] = table2Darray[c + leftMargin][r + header];
				}
			}
			if (header > 0)
				allBundleNames[c] = table2Darray[c + leftMargin][0];
			if (header == 2)
				allBundleColorStrings[c] = table2Darray[c + leftMargin][1];
		}
		String[][] specificBundleArrays = allBundleArrays;

		for (int i = 0; i < specificBundleArrays.length; i++) {
			String[] currentBundleArray = specificBundleArrays[i];
			if (currentBundleArray == null)
				continue;
			RoiManager specificBundleRM = new RoiManager(null, true);
			ArrayList<ArrayList<String>> synapseByBundles = new ArrayList<ArrayList<String>>();
			// Add a labeling first entry to each color pairing set...
			for (int j = 0; j < allBundleArrays.length; j++) {
				synapseByBundles.add(new ArrayList<String>());
				synapseByBundles.get(synapseByBundles.size() - 1)
						.add("" + allBundleColorStrings[i] + "-" + allBundleColorStrings[j] + ">>");
			}
			ArrayList<Roi> newRois = new ArrayList<Roi>();
			for (String synapseRoiName : rois.keySet()) {
				if (synapseRoiName == null)
					continue;
				boolean presynInBundle = false;
				boolean postsynOutsideOfBundle = true;
				String synapseRoiNameCleaned = synapseRoiName.replace("\"", "").replace("[", "").replace("]", "");
				String rootName = synapseRoiName.contains("\"") ? synapseRoiName.split("\"")[1].trim() : "";
				rootName = rootName.contains(" ") ? rootName.split("[_\\- ]")[0].trim() : rootName;
				String[] postSynapticCells = synapseRoiNameCleaned.split("_")[0]
						.replaceAll(".*(electrical|chemical)(.*)", "$2").split("\\&");
				if (postSynapticCells[0].contains("by")) {
					postSynapticCells[0] = postSynapticCells[0].split("by")[1].trim();
				}
				for (String currentBundleNeuron : currentBundleArray) {
					if (currentBundleNeuron == null)
						continue;
					if (synapseRoiNameCleaned.startsWith(currentBundleNeuron)) {
						presynInBundle = true;
						boolean noHitInBundle = true;
						for (String postSC : postSynapticCells) {
							for (String nextBundleNeuron : currentBundleArray) {
								if (postSC.equals(nextBundleNeuron)) {
									noHitInBundle = false;
								}
							}
						}
						postsynOutsideOfBundle = postsynOutsideOfBundle && noHitInBundle;
					}
				}
				if (presynInBundle) {
					if (false) { // !postsynOutsideOfBundle){
						this.setRoiFillColor(rois.get(synapseRoiName),
								Colors.getColor(allBundleColorStrings[i], Color.DARK_GRAY));
						for (int ba = 0; ba < allBundleArrays.length; ba++) {
							if (allBundleArrays[ba] == currentBundleArray) {
								if (!synapseByBundles.get(ba).contains(rootName)) {
									synapseByBundles.get(ba).add(rootName);
								}
							}
						}
					} else {
						for (int ba = 0; ba < allBundleArrays.length; ba++) {
							String[] targetBundleArray = allBundleArrays[ba];
							if (targetBundleArray == null)
								continue;
							for (String targetBundleNeuron : targetBundleArray) {
								if (targetBundleNeuron == null)
									continue;
								int[][] psps = new int[][] { { -1, -1 }, { +1, +1 }, { -1, +1 }, { +1, -1 }, };
								int psc = 0;
								int shift = postSynapticCells.length > 1 ? 5 : 0;
								for (String postSC : postSynapticCells) {

									if (postSC == null)
										continue;
									if (postSC.equals(targetBundleNeuron)/* && postsynOutsideOfBundle */) {
										Roi newRoi = ((Roi) rois.get(synapseRoiName).clone());
										newRoi.setLocation(
												rois.get(synapseRoiName).getBounds().x + psps[psc][0] * shift,
												rois.get(synapseRoiName).getBounds().y + psps[psc][1] * shift);
										this.setRoiFillColor(newRoi,
												Colors.getColor(allBundleColorStrings[ba], Color.DARK_GRAY));
										newRois.add(newRoi);
										if (!synapseByBundles.get(ba).contains(rootName)) {
											synapseByBundles.get(ba).add(rootName);
										}
									}
									psc++;
								}
							}

						}
					}

					if (!Image3DUniverse.universes.isEmpty()) {
						for (Image3DUniverse univ : Image3DUniverse.universes) {
							if (univ.getContent(rootName) != null) {
								univ.getContent(rootName)
										.setColor(new Color3f(rois.get(synapseRoiName).getFillColor()));
								univ.getContent(rootName).setTransparency(0.0f);
								;
							}
						}
					}
				} else {
//					Color roiColor= synapseRoiName.contains("chemical")?Color.white:Color.yellow;
//					if (synapseRoiName.contains("uncertain")){
//						roiColor= synapseRoiName.contains("chemical")?Color.pink:Color.orange;
//					}
//					rois.get(synapseRoiName)this.setRoiFillColor(roiColor);
					if (!Image3DUniverse.universes.isEmpty()) {
						for (Image3DUniverse univ : Image3DUniverse.universes) {
							if (univ.getContent(rootName) != null) {
								univ.getContent(rootName)
										.setColor(new Color3f(rois.get(synapseRoiName).getFillColor()));
								univ.getContent(rootName).setTransparency(0.9f);
								;
							}
						}
					}
				}
			}
			for (ArrayList<String> synapsePartners : synapseByBundles) {
				for (String synapseName : synapsePartners) {
					IJ.log(synapseName);
				}
				IJ.log(synapsePartners.get(0) + ": " + (synapsePartners.size() - 1) + " synapses\n\n");
			}
			for (Roi nextRoi : newRois) {
				specificBundleRM.addRoi(nextRoi);
			}
			specificBundleRM.setSelectedIndexes(specificBundleRM.getFullListIndexes());
			specificBundleRM.saveMultiple(specificBundleRM.getSelectedIndexes(),
					xlsPath + "_" + allBundleNames[i] + "_RoiSet.zip");
			IJ.wait(1);
		}
	}

	public void colorObjsByGroupInteractionRules() {

		String xlsPath = IJ.getFilePath("Table of Cell Groups?");
		String objDirPath = IJ.getDirectory("Folder of Objs to Sort and Color?");
		String newCellOutDirPath = objDirPath.substring(0, objDirPath.lastIndexOf(File.separator)) + "_asCells";
		String newGroupOutDirPath = objDirPath.substring(0, objDirPath.lastIndexOf(File.separator)) + "_asGroups";

		String[] tableRows = IJ.openAsString(xlsPath).split("\n");
		String rowSplitter = "\t";
		if (xlsPath.endsWith(".csv")) {
			rowSplitter = ",";
		}
		String[][] table2Darray = new String[tableRows[0].split(rowSplitter).length][tableRows.length];
		for (int row = 0; row < tableRows.length; row++) {
			String[] rowChunks = tableRows[row].split(rowSplitter);
			for (int col = 0; col < rowChunks.length; col++) {
				table2Darray[col][row] = rowChunks[col];
			}
		}

		int header = 0;
		int leftMargin = 0;
		if (table2Darray[0][0].equalsIgnoreCase("Group")) {
			header++;
			leftMargin++;
			if (table2Darray[0][1].equalsIgnoreCase("Color")) {
				header++;
			}
		}

		String[][] allGroupArrays = new String[tableRows[0].split(rowSplitter).length - leftMargin][tableRows.length
				- header];
		String[] allGroupNames = new String[tableRows[0].split(rowSplitter).length - leftMargin];
		String[] allGroupColorStrings = new String[tableRows[0].split(rowSplitter).length - leftMargin];
		for (int c = 0; c < tableRows[0].split(rowSplitter).length - leftMargin; c++) {
			for (int r = 0; r < tableRows.length - header; r++) {
				if (table2Darray[c + leftMargin][r + header] != null
						&& table2Darray[c + leftMargin][r + header].length() > 0) {
					allGroupArrays[c][r] = table2Darray[c + leftMargin][r + header];
				}
			}
			if (header > 0)
				allGroupNames[c] = table2Darray[c + leftMargin][0];
			if (header == 2)
				allGroupColorStrings[c] = table2Darray[c + leftMargin][1];
		}
		String[][] specificGroupArrays = allGroupArrays;

		ArrayList<String> allCellNamesFromTable = new ArrayList<String>();
		for (String[] sga : specificGroupArrays) {
			for (String cell : sga) {
				allCellNamesFromTable.add(cell);
			}
		}

		for (int i = 0; i < specificGroupArrays.length; i++) {
			String[] currentGroupArray = specificGroupArrays[i];
			if (currentGroupArray == null)
				continue;
			String specificGroupOutputDirPath = newGroupOutDirPath + File.separator + allGroupNames[i] + "_"
					+ allGroupColorStrings[i];
			new File(specificGroupOutputDirPath).mkdirs();
			String universalMtlColorURL = MQTVSSceneLoader64.class
					.getResource("docs/UniversalColorPallet_firstVersion.mtl").toString();
			IJ.saveString(IJ.openUrlAsString(universalMtlColorURL),
					specificGroupOutputDirPath + File.separator + "UniversalColorPallet_firstVersion.mtl");
			ArrayList<ArrayList<String>> synapseSummaryByGroups = new ArrayList<ArrayList<String>>();
			ArrayList<ArrayList<String>> synapseDetailByGroups = new ArrayList<ArrayList<String>>();
			// Add a labeling first entry to each color pairing set...
			for (int j = 0; j < allGroupArrays.length; j++) {
				synapseSummaryByGroups.add(new ArrayList<String>());
				synapseSummaryByGroups.get(synapseSummaryByGroups.size() - 1)
						.add("" + allGroupColorStrings[i] + "-" + allGroupColorStrings[j] + ">>");
				synapseDetailByGroups.add(new ArrayList<String>());
				synapseDetailByGroups.get(synapseDetailByGroups.size() - 1)
						.add("" + allGroupColorStrings[i] + "-" + allGroupColorStrings[j] + ">>>>");
			}
			ArrayList<String> newObjNamesforGroupPatches = new ArrayList<String>();
			ArrayList<String> oldObjNamesforGroupPatches = new ArrayList<String>();
			ArrayList<String> newPreSynObjNamesforCellPatches = new ArrayList<String>();
			ArrayList<String> newPostSynObjNamesforCellPatches = new ArrayList<String>();
			ArrayList<String> oldObjNamesforCellPatches = new ArrayList<String>();
			ArrayList<int[]> xyShiftsForPolyadicGroupSynapses = new ArrayList<int[]>();
			ArrayList<int[]> xyShiftsForPolyadicCellSynapses = new ArrayList<int[]>();
			ArrayList<String> preSynCellNameDirs = new ArrayList<String>();
			ArrayList<String> postSynCellNameDirs = new ArrayList<String>();
			String[] objDirList = new File(objDirPath).list();
			for (String synapseObjFileName : objDirList) {
				if (synapseObjFileName.contains("electrical"))
					IJ.wait(1);
				if (synapseObjFileName == null || !synapseObjFileName.toLowerCase().endsWith(".obj"))
					continue;
				boolean presynInGroup = false;
				boolean rootIsPostSyn = false;
				boolean postsynOutsideOfGroup = true;
				String synapseObjNameCleaned = synapseObjFileName.replace("\"", "").replace("[", "").replace("]", "");

				String rootName = synapseObjFileName.contains("\"") ? synapseObjFileName.split("\"")[1].trim()
						: synapseObjNameCleaned.replaceAll("(.*_zs\\d+_)(.*)(electrical|chemical)(.*)", "$2");
				rootName = rootName.contains(" ") ? rootName.split("[_\\- ]")[0].trim() : rootName.trim();
				String[] postSynapticCellArray = synapseObjNameCleaned.split("_")[1]
						.replaceAll(".*(electrical|chemical)(.*)", "$2").split("\\&");
				if (postSynapticCellArray[0].contains("by")) {
					rootName = postSynapticCellArray[0];
					postSynapticCellArray[0] = postSynapticCellArray[0].split("by")[1].trim();
				}
				ArrayList<String> postSynapticCellsTransBundle = new ArrayList<String>();
				for (String s : postSynapticCellArray) {
					postSynapticCellsTransBundle.add(s);
				}
				for (String currentGroupNeuron : currentGroupArray) {
					if (currentGroupNeuron == null)
						continue;
					if (!allCellNamesFromTable.contains(currentGroupNeuron))
						continue;
					if (rootName.equals(currentGroupNeuron)) {
						if (!preSynCellNameDirs.contains(currentGroupNeuron + "_PreSyn")) {
							preSynCellNameDirs.add(currentGroupNeuron + "_PreSyn");
							new File(newCellOutDirPath + File.separator + currentGroupNeuron + "_PreSyn").mkdirs();
							IJ.saveString(IJ.openUrlAsString(universalMtlColorURL),
									newCellOutDirPath + File.separator + currentGroupNeuron + "_PreSyn" + File.separator
											+ "UniversalColorPallet_firstVersion.mtl");
						}
						presynInGroup = true;
						boolean noHitInGroup = true;
						for (String postSC : postSynapticCellArray) {
							if (!allCellNamesFromTable.contains(postSC))
								continue;

							if (!postSynCellNameDirs.contains(postSC + "_PostSyn")) {
								postSynCellNameDirs.add(postSC + "_PostSyn");
								new File(newCellOutDirPath + File.separator + postSC + "_PostSyn").mkdirs();
								IJ.saveString(IJ.openUrlAsString(universalMtlColorURL),
										newCellOutDirPath + File.separator + postSC + "_PostSyn" + File.separator
												+ "UniversalColorPallet_firstVersion.mtl");
							}

							for (String nextGroupNeuron : currentGroupArray) {
								if (postSC.equals(nextGroupNeuron)) {
//									noHitInGroup = false;
									postSynapticCellsTransBundle.remove(nextGroupNeuron);
								}
							}
						}
//						postsynOutsideOfGroup = postsynOutsideOfGroup && noHitInGroup;
					} else {
						for (String postSC : postSynapticCellsTransBundle) {
							if (postSC.equals(currentGroupNeuron)) {
								rootIsPostSyn = true;
							}
						}
					}
				}

				if (presynInGroup) {
					for (int ba = 0; ba < allGroupArrays.length; ba++) {
						String[] targetGroupArray = allGroupArrays[ba];
						if (targetGroupArray == null)
							continue;
						for (String targetGroupNeuron : targetGroupArray) {
							if (targetGroupNeuron == null)
								continue;
							int[][] psps = new int[][] { { -1, -1 }, { +1, +1 }, { -1, +1 }, { +1, -1 } };
							int psc = 0;
							int shift = postSynapticCellsTransBundle.size() > 1 ? 50 : 0;
							for (String postSC : postSynapticCellArray) {

								if (postSC == null)
									continue;
								if (postSC.equals(targetGroupNeuron)) {
									if (synapseObjFileName.contains("electrical"))
										IJ.wait(1);
									String newPreSynObjFileName = synapseObjFileName.endsWith("postSyn.obj") ? ""
											: synapseObjFileName;
									newPreSynObjFileName = newPreSynObjFileName.replace(".obj",
											"_" + allGroupColorStrings[ba] + "-" + psc + ".obj");
									String newPostSynObjFileName = synapseObjFileName.endsWith("preSyn.obj") ? ""
											: synapseObjFileName;
									;
									newPostSynObjFileName = newPostSynObjFileName.replace(".obj",
											"_" + allGroupColorStrings[i] + "-" + psc + ".obj");
									if (postSynapticCellsTransBundle.contains(postSC)) {
										newObjNamesforGroupPatches.add(newPreSynObjFileName);
										oldObjNamesforGroupPatches.add(synapseObjFileName);
										xyShiftsForPolyadicGroupSynapses
												.add(new int[] { psps[psc][0] * shift, psps[psc][1] * shift });

									}

									newPreSynObjNamesforCellPatches.add(newPreSynObjFileName);

									newPostSynObjNamesforCellPatches.add(newPostSynObjFileName);

									oldObjNamesforCellPatches.add(synapseObjFileName);
									xyShiftsForPolyadicCellSynapses
											.add(new int[] { psps[psc][0] * shift, psps[psc][1] * shift });

									if (!synapseSummaryByGroups.get(ba).contains(rootName + "->" + postSC)) {
										synapseSummaryByGroups.get(ba).add(rootName + "->" + postSC);
									}
									if (!synapseDetailByGroups.get(ba)
											.contains(synapseObjFileName + "=" + newPreSynObjFileName)) {
										synapseDetailByGroups.get(ba)
												.add(synapseObjFileName + "=" + newPreSynObjFileName);
									}
								}
								psc++;
							}

						}
					}

				} else if (rootIsPostSyn && !presynInGroup) {
					for (int ba = 0; ba < allGroupArrays.length; ba++) {
						String[] targetGroupArray = allGroupArrays[ba];
						if (targetGroupArray == null)
							continue;
						for (String targetGroupNeuron : targetGroupArray) {
							if (targetGroupNeuron == null)
								continue;
							int psc = 0;
							if (rootName.equals(targetGroupNeuron)) {
								String newPostSynObjFileName = synapseObjFileName.endsWith("preSyn.obj") ? ""
										: synapseObjFileName;
								;
								newPostSynObjFileName = newPostSynObjFileName.replace(".obj",
										"_" + allGroupColorStrings[ba] + "-" + psc + ".obj");
								newObjNamesforGroupPatches.add(newPostSynObjFileName);
								oldObjNamesforGroupPatches.add(synapseObjFileName);
								xyShiftsForPolyadicGroupSynapses.add(new int[] { 0, 0 });
							}
						}
					}
				} else {
					IJ.wait(1);
				}

			}
			for (ArrayList<String> synapseSPartners : synapseSummaryByGroups) {
				for (String synapseSName : synapseSPartners) {
					IJ.log(synapseSName);
				}
				IJ.log(synapseSPartners.get(0) + ": " + (synapseSPartners.size() - 1) + " Cell Pairings\n\n");
			}
			for (ArrayList<String> synapseDPartners : synapseDetailByGroups) {
				for (String synapseDName : synapseDPartners) {
					IJ.log(synapseDName);
				}
				IJ.log(synapseDPartners.get(0) + ": " + (synapseDPartners.size() - 1) + " Synapse Objs\n\n");
			}
			for (String nextObjName : newObjNamesforGroupPatches) {
				if (nextObjName.toLowerCase().endsWith(".obj")) {
					String oldObjName = oldObjNamesforGroupPatches.get(newObjNamesforGroupPatches.indexOf(nextObjName));
					String[] newObjNameChunks = nextObjName.split("_");
					String newObjColorName = newObjNameChunks[newObjNameChunks.length - 1].split("-")[0].toUpperCase();
					String newObjBodyText = IJ.openAsString(objDirPath + File.separator + oldObjName)
							.replaceAll("(.*mtllib ).*(\n.*)", "$1" + "UniversalColorPallet_firstVersion.mtl" + "$2")
							.replaceAll("(.*\ng )(.*)(\n.*)",
									"$1" + "$2_"
											+ newObjNameChunks[newObjNameChunks.length - 1].toLowerCase()
													.replace(".obj", "")
											+ "$3")
							.replaceAll("(.*usemtl mat_).*(\n.*)", "$1" + newObjColorName + "$2");
					String[] newObjBodyTextVertexLines = newObjBodyText.split("(\nv |\nusemtl)");
					String rebuildObjBodyText = newObjBodyTextVertexLines[0];
					for (int v = 1; v < newObjBodyTextVertexLines.length - 1; v++) {
						String[] vertexLineChunks = ("\nv " + newObjBodyTextVertexLines[v]).split(" ");
						String shiftedObjBodyTextVertexLine = "\nv "
								+ (Double.parseDouble(vertexLineChunks[1]) + xyShiftsForPolyadicGroupSynapses
										.get(newObjNamesforGroupPatches.indexOf(nextObjName))[0])
								+ " "
								+ (Double.parseDouble(vertexLineChunks[2]) + xyShiftsForPolyadicGroupSynapses
										.get(newObjNamesforGroupPatches.indexOf(nextObjName))[1])
								+ " " + vertexLineChunks[3];
						rebuildObjBodyText = rebuildObjBodyText + shiftedObjBodyTextVertexLine;
					}
					rebuildObjBodyText = rebuildObjBodyText + "\nusemtl"
							+ newObjBodyTextVertexLines[newObjBodyTextVertexLines.length - 1];
					IJ.saveString(rebuildObjBodyText, specificGroupOutputDirPath + File.separator + nextObjName);
				}
			}
			for (String nextObjName : newPreSynObjNamesforCellPatches) {
				if (nextObjName.toLowerCase().endsWith(".obj")) {
					String oldObjName = oldObjNamesforCellPatches
							.get(newPreSynObjNamesforCellPatches.indexOf(nextObjName));
					String[] newObjNameChunks = nextObjName.split("_");
					String newObjColorName = newObjNameChunks[newObjNameChunks.length - 1].split("-")[0].toUpperCase();
					String newObjBodyText = IJ.openAsString(objDirPath + File.separator + oldObjName)
							.replaceAll("(.*mtllib ).*(\n.*)", "$1" + "UniversalColorPallet_firstVersion.mtl" + "$2")
							.replaceAll("(.*\ng )(.*)(\n.*)",
									"$1" + "$2_"
											+ newObjNameChunks[newObjNameChunks.length - 1].toLowerCase()
													.replace(".obj", "")
											+ "$3")
							.replaceAll("(.*usemtl mat_).*(\n.*)", "$1" + newObjColorName + "$2");
					String[] newObjBodyTextVertexLines = newObjBodyText.split("(\nv |\nusemtl)");
					String rebuildObjBodyText = newObjBodyTextVertexLines[0];
					for (int v = 1; v < newObjBodyTextVertexLines.length - 1; v++) {
						String[] vertexLineChunks = ("\nv " + newObjBodyTextVertexLines[v]).split(" ");
						String shiftedObjBodyTextVertexLine = "\nv "
								+ (Double.parseDouble(vertexLineChunks[1]) + xyShiftsForPolyadicCellSynapses
										.get(newPreSynObjNamesforCellPatches.indexOf(nextObjName))[0])
								+ " "
								+ (Double.parseDouble(vertexLineChunks[2]) + xyShiftsForPolyadicCellSynapses
										.get(newPreSynObjNamesforCellPatches.indexOf(nextObjName))[1])
								+ " " + vertexLineChunks[3];
						rebuildObjBodyText = rebuildObjBodyText + shiftedObjBodyTextVertexLine;
					}
					rebuildObjBodyText = rebuildObjBodyText + "\nusemtl"
							+ newObjBodyTextVertexLines[newObjBodyTextVertexLines.length - 1];
					for (String currentNeuron : preSynCellNameDirs) {
						if (nextObjName.contains("_" + currentNeuron.replaceAll("(_PreSyn|_PostSyn)", ""))) {
							IJ.saveString(rebuildObjBodyText,
									newCellOutDirPath + File.separator + currentNeuron + File.separator + nextObjName);
						}
					}
				}
			}
			for (String nextObjName : newPostSynObjNamesforCellPatches) {
				if (nextObjName.toLowerCase().endsWith(".obj")) {
					String oldObjName = oldObjNamesforCellPatches
							.get(newPostSynObjNamesforCellPatches.indexOf(nextObjName));
					String[] newObjNameChunks = nextObjName.split("_");
					String newObjColorName = newObjNameChunks[newObjNameChunks.length - 1].split("-")[0].toUpperCase();
					String newObjBodyText = IJ.openAsString(objDirPath + File.separator + oldObjName)
							.replaceAll("(.*mtllib ).*(\n.*)", "$1" + "UniversalColorPallet_firstVersion.mtl" + "$2")
							.replaceAll("(.*\ng )(.*)(\n.*)",
									"$1" + "$2_"
											+ newObjNameChunks[newObjNameChunks.length - 1].toLowerCase()
													.replace(".obj", "")
											+ "$3")
							.replaceAll("(.*usemtl mat_).*(\n.*)", "$1" + newObjColorName + "$2");
					String[] newObjBodyTextVertexLines = newObjBodyText.split("(\nv |\nusemtl)");
					String rebuildObjBodyText = newObjBodyTextVertexLines[0];
					for (int v = 1; v < newObjBodyTextVertexLines.length - 1; v++) {
						String[] vertexLineChunks = ("\nv " + newObjBodyTextVertexLines[v]).split(" ");
						String shiftedObjBodyTextVertexLine = "\nv "
								+ (Double.parseDouble(vertexLineChunks[1]) + xyShiftsForPolyadicCellSynapses
										.get(newPostSynObjNamesforCellPatches.indexOf(nextObjName))[0])
								+ " "
								+ (Double.parseDouble(vertexLineChunks[2]) + xyShiftsForPolyadicCellSynapses
										.get(newPostSynObjNamesforCellPatches.indexOf(nextObjName))[1])
								+ " " + vertexLineChunks[3];
						rebuildObjBodyText = rebuildObjBodyText + shiftedObjBodyTextVertexLine;
					}
					rebuildObjBodyText = rebuildObjBodyText + "\nusemtl"
							+ newObjBodyTextVertexLines[newObjBodyTextVertexLines.length - 1];
					for (String currentNeuron : postSynCellNameDirs) {
						boolean breakOut = false;
						String[] nextObjNameChunks = nextObjName.split("chemical|electrical|\\&|_(un)?certain");
						for (String nextChunk : nextObjNameChunks) {
							if (!nextChunk.equals("_" + currentNeuron.replaceAll("(_PreSyn|_PostSyn)", ""))
									&& nextChunk.equals(currentNeuron.replaceAll("(_PreSyn|_PostSyn)", ""))) {
								String[] currentNeuronDirList = new File(
										newCellOutDirPath + File.separator + currentNeuron).list();
								boolean currentSynapseNew = true;
								if (currentNeuronDirList != null) {
									for (String fileName : currentNeuronDirList) {
										String nextObjStart = nextObjName.replaceAll("(_zs\\d+).*", "$1");
										if (fileName.startsWith(nextObjStart)) {
											currentSynapseNew = false;
										}
									}
								}
								if (currentSynapseNew) {
									IJ.saveString(rebuildObjBodyText, newCellOutDirPath + File.separator + currentNeuron
											+ File.separator + nextObjName);
									breakOut = true;
								}
							}
						}
						if (breakOut)
							break; // only one hit allowed per postsyn name...
					}
				}
			}
			IJ.wait(1);
		}
	}

	public void swapSynapseObjTypes(boolean allBalls) {

		String electricalObj = "";
		String postSynObj = "";
		String preSynObj = "";
//		String electricalObj =  IJ.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/SVV_newZap25_newZap25_960_0000.obj").toString());
//		String preSynObj = IJ.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/SVV_newCircle19_newCircle19_326_0000.obj").toString());
		if (!allBalls) {
			electricalObj = IJ.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/3dZap_0000.obj").toString());
		} else {
			electricalObj = IJ
					.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.obj").toString());
		}
		if (!allBalls) {
			preSynObj = IJ.openUrlAsString(
					MQTVSSceneLoader64.class.getResource("docs/SVV_newDiamond25_newDiamond25_386_0000.obj").toString());
		} else {
			preSynObj = IJ
					.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.obj").toString());
		}
		postSynObj = IJ.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.obj").toString());
		IJ.log(preSynObj + postSynObj + electricalObj);
		String[] preSynSections = preSynObj.split("(\ng |\ns )");
		String[] postSynSections = postSynObj.split("(\ng |\ns )");
		String[] electricalSections = electricalObj.split("(\ng |\ns )");
		IJ.log("" + preSynSections.length + postSynSections.length + electricalSections.length);
		String[] electricalVertices = electricalSections[1].split("\n");
		String[] electricalFacets = electricalSections[2].split("\n");
		String[] postSynVertices = postSynSections[1].split("\n");
		String[] postSynFacets = postSynSections[2].split("\n");
		String[] preSynVertices = preSynSections[1].split("\n");
		String[] preSynFacets = preSynSections[2].split("\n");
		ArrayList<Double> electricalVXs = new ArrayList<Double>();
		ArrayList<Double> electricalVYs = new ArrayList<Double>();
		ArrayList<Double> electricalVZs = new ArrayList<Double>();
		ArrayList<Double> postSynVXs = new ArrayList<Double>();
		ArrayList<Double> postSynVYs = new ArrayList<Double>();
		ArrayList<Double> postSynVZs = new ArrayList<Double>();
		ArrayList<Double> preSynVXs = new ArrayList<Double>();
		ArrayList<Double> preSynVYs = new ArrayList<Double>();
		ArrayList<Double> preSynVZs = new ArrayList<Double>();
		ArrayList<Double> electricalFXs = new ArrayList<Double>();
		ArrayList<Double> electricalFYs = new ArrayList<Double>();
		ArrayList<Double> electricalFZs = new ArrayList<Double>();
		ArrayList<Double> postSynFXs = new ArrayList<Double>();
		ArrayList<Double> postSynFYs = new ArrayList<Double>();
		ArrayList<Double> postSynFZs = new ArrayList<Double>();
		ArrayList<Double> preSynFXs = new ArrayList<Double>();
		ArrayList<Double> preSynFYs = new ArrayList<Double>();
		ArrayList<Double> preSynFZs = new ArrayList<Double>();
		for (int x = 0; x < electricalVertices.length; x++) {
			if (electricalVertices[x].startsWith("v ")) {
				electricalVXs.add(Double.parseDouble(electricalVertices[x].split(" ")[1]));
				electricalVYs.add(Double.parseDouble(electricalVertices[x].split(" ")[2]));
				electricalVZs.add(Double.parseDouble(electricalVertices[x].split(" ")[3])*1.4);
			}
		}
		for (int x = 0; x < electricalFacets.length; x++) {
			if (electricalFacets[x].startsWith("f ")) {
				electricalFXs.add(Double.parseDouble(electricalFacets[x].split(" ")[1]));
				electricalFYs.add(Double.parseDouble(electricalFacets[x].split(" ")[2]));
				electricalFZs.add(Double.parseDouble(electricalFacets[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < postSynVertices.length; x++) {
			if (postSynVertices[x].startsWith("v ")) {
				postSynVXs.add(Double.parseDouble(postSynVertices[x].split(" ")[1]));
				postSynVYs.add(Double.parseDouble(postSynVertices[x].split(" ")[2]));
				postSynVZs.add(Double.parseDouble(postSynVertices[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < postSynFacets.length; x++) {
			if (postSynFacets[x].startsWith("f ")) {
				postSynFXs.add(Double.parseDouble(postSynFacets[x].split(" ")[1]));
				postSynFYs.add(Double.parseDouble(postSynFacets[x].split(" ")[2]));
				postSynFZs.add(Double.parseDouble(postSynFacets[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < preSynVertices.length; x++) {
			if (preSynVertices[x].startsWith("v ")) {
				preSynVXs.add(Double.parseDouble(preSynVertices[x].split(" ")[1]));
				preSynVYs.add(Double.parseDouble(preSynVertices[x].split(" ")[2]));
				preSynVZs.add(Double.parseDouble(preSynVertices[x].split(" ")[3]) * 2.0);
			}
		}
		for (int x = 0; x < preSynFacets.length; x++) {
			if (preSynFacets[x].startsWith("f ")) {
				preSynFXs.add(Double.parseDouble(preSynFacets[x].split(" ")[1]));
				preSynFYs.add(Double.parseDouble(preSynFacets[x].split(" ")[2]));
				preSynFZs.add(Double.parseDouble(preSynFacets[x].split(" ")[3]));
			}
		}

		Object[] evxs = (electricalVXs.toArray());
		Arrays.sort(evxs);
		double evxMedian = (double) evxs[evxs.length / 2];
		Object[] postvxs = (postSynVXs.toArray());
		Arrays.sort(postvxs);
		double postvxMedian = (double) postvxs[postvxs.length / 2];
		Object[] prevxs = (preSynVXs.toArray());
		Arrays.sort(prevxs);
		double prevxMedian = (double) prevxs[prevxs.length / 2];
		Object[] evys = (electricalVYs.toArray());
		Arrays.sort(evys);
		double evyMedian = (double) evys[evys.length / 2];
		Object[] postvys = (postSynVYs.toArray());
		Arrays.sort(postvys);
		double postvyMedian = (double) postvys[postvys.length / 2];
		Object[] prevys = (preSynVYs.toArray());
		Arrays.sort(prevys);
		double prevyMedian = (double) prevys[prevys.length / 2];
		Object[] evzs = (electricalVZs.toArray());
		Arrays.sort(evzs);
		double evzMedian = (double) evzs[evzs.length / 2];
		double evzMin = (double) evzs[0];
		double evzMax = (double) evzs[evzs.length - 1];
		double evzMean = (evzMax + evzMin) / 2;
		Object[] postvzs = (postSynVZs.toArray());
		Arrays.sort(postvzs);
		double postvzMedian = (double) postvzs[postvzs.length / 2];
		double postvzMin = (double) postvzs[0];
		double postvzMax = (double) postvzs[postvzs.length - 1];
		double postvzMean = (postvzMax + postvzMin) / 2;
		Object[] prevzs = (preSynVZs.toArray());
		Arrays.sort(prevzs);
		double prevzMedian = (double) prevzs[prevzs.length / 2];
		double prevzMin = (double) prevzs[0];
		double prevzMax = (double) prevzs[prevzs.length - 1];
		double prevzMean = (prevzMax + prevzMin) / 2;
		Object[] efxs = (electricalFXs.toArray());
		Arrays.sort(efxs);
		double efxMedian = (double) efxs[efxs.length / 2];
		Object[] postfxs = (postSynFXs.toArray());
		Arrays.sort(postfxs);
		double postfxMedian = (double) postfxs[postfxs.length / 2];
		Object[] prefxs = (preSynFXs.toArray());
		Arrays.sort(prefxs);
		double prefxMedian = (double) prefxs[prefxs.length / 2];
		Object[] efys = (electricalFYs.toArray());
		Arrays.sort(efys);
		double efyMedian = (double) efys[efys.length / 2];
		Object[] postfys = (postSynFYs.toArray());
		Arrays.sort(postfys);
		double postfyMedian = (double) postfys[postfys.length / 2];
		Object[] prefys = (preSynFYs.toArray());
		Arrays.sort(prefys);
		double prefyMedian = (double) prefys[prefys.length / 2];
		Object[] efzs = (electricalFZs.toArray());
		Arrays.sort(efzs);
		double efzMedian = (double) efzs[efzs.length / 2];
		Object[] postfzs = (postSynFZs.toArray());
		Arrays.sort(postfzs);
		double postfzMedian = (double) postfzs[postfzs.length / 2];
		Object[] prefzs = (preSynFZs.toArray());
		Arrays.sort(prefzs);
		double prefzMedian = (double) prefzs[prefzs.length / 2];

		IJ.wait(1);

		String inputDirPath = IJ.getDirectory("select directory of single OBJs");
		String[] inputList = new File(inputDirPath).list();
		for (String inputName : inputList) {
			String inputPath = inputDirPath + inputName;
			if (!inputPath.toLowerCase().endsWith(".obj"))
				continue;
			String inputObj = IJ.openAsString(inputPath);
			String[] inputSections = inputObj.split("(\ng |\ns )");
			String[] inputVertices = inputSections[1].split("\n");
			String[] inputFacets = inputSections[2].split("\n");
			ArrayList<Double> inputVXs = new ArrayList<Double>();
			ArrayList<Double> inputVYs = new ArrayList<Double>();
			ArrayList<Double> inputVZs = new ArrayList<Double>();
			ArrayList<Double> inputFXs = new ArrayList<Double>();
			ArrayList<Double> inputFYs = new ArrayList<Double>();
			ArrayList<Double> inputFZs = new ArrayList<Double>();
			for (int x = 0; x < inputVertices.length; x++) {
				if (inputVertices[x].startsWith("v ")) {
					inputVXs.add(Double.parseDouble(inputVertices[x].split(" ")[1]));
					inputVYs.add(Double.parseDouble(inputVertices[x].split(" ")[2]));
					inputVZs.add(Double.parseDouble(inputVertices[x].split(" ")[3]));
				}
			}
			for (int x = 0; x < inputFacets.length; x++) {
				if (inputFacets[x].startsWith("f ")) {
					inputFXs.add(Double.parseDouble(inputFacets[x].split(" ")[1]));
					inputFYs.add(Double.parseDouble(inputFacets[x].split(" ")[2]));
					inputFZs.add(Double.parseDouble(inputFacets[x].split(" ")[3]));
				}
			}
			if (inputVXs.size() == 0)
				continue;
			Object[] ivxs = (inputVXs.toArray());
			Arrays.sort(ivxs);
			double ivxMedian = (double) ivxs[ivxs.length / 2];
			Object[] ivys = (inputVYs.toArray());
			Arrays.sort(ivys);
			double ivyMedian = (double) ivys[ivys.length / 2];
			Object[] ivzs = (inputVZs.toArray());
			Arrays.sort(ivzs);
			double ivzMedian = (double) ivzs[ivzs.length / 2];
			double ivzMin = (double) ivzs[0];
			double ivzMax = (double) ivzs[ivzs.length - 1];
			double ivzMean = (ivzMax + ivzMin) / 2;
			Object[] ifxs = (inputFXs.toArray());
			Arrays.sort(ifxs);
			double ifxMedian = (double) ifxs[ifxs.length / 2];
			Object[] ifys = (inputFYs.toArray());
			Arrays.sort(ifys);
			double ifyMedian = (double) ifys[ifys.length / 2];
			Object[] ifzs = (inputFZs.toArray());
			Arrays.sort(ifzs);
			double ifzMedian = (double) ifzs[ifzs.length / 2];

			File inputFile = new File(inputPath);
			String outputTag = "";
			String[] postSynNames = inputFile.getName().split("&");
			for (int psn =0; psn<postSynNames.length; psn++){
				outputTag = inputFile.getName().contains("electrical") ? "gapJxn" : (psn+1)+"postSyn";
				String outputPath = inputPath.replace(".obj", outputTag + ".obj");
				String outputObj = "";
				String[] outputSections = outputTag == "gapJxn" ? electricalSections : postSynSections;
				String[] outputVertices = outputTag == "gapJxn" ? electricalVertices : postSynVertices;
				String[] outputFacets = outputTag == "gapJxn" ? electricalFacets : postSynFacets;
				ArrayList<Double> outputVXs = outputTag == "gapJxn" ? electricalVXs : postSynVXs;
				ArrayList<Double> outputVYs = outputTag == "gapJxn" ? electricalVYs : postSynVYs;
				ArrayList<Double> outputVZs = outputTag == "gapJxn" ? electricalVZs : postSynVZs;
				ArrayList<Double> outputFXs = outputTag == "gapJxn" ? electricalFXs : postSynFXs;
				ArrayList<Double> outputFYs = outputTag == "gapJxn" ? electricalFYs : postSynFYs;
				ArrayList<Double> outputFZs = outputTag == "gapJxn" ? electricalFZs : postSynFZs;

				double stepShiftZ = (postvzMax - postvzMin)*0.25;
				double offsetVX = ivxMedian - (outputTag == "gapJxn" ? evxMedian : postvxMedian);
				double offsetVY = ivyMedian - (outputTag == "gapJxn" ? evyMedian : postvyMedian);
				double stepIncrement = stepShiftZ*((double)psn) -stepShiftZ*(((double)postSynNames.length-1)/2);
				double offsetVZ = stepIncrement + ivzMin - (outputTag == "gapJxn" ? (evzMean - evzMin) : (postvzMean - postvzMin));
				//			double zScale = (ivzMax-ivzMin)/((outputTag=="gapJxn"?evzMax:postvzMax)-(outputTag=="gapJxn"?evzMin:postvzMin));
				outputObj = outputObj + inputSections[0] + "\ng " + inputVertices[0]
						+ (outputTag == "gapJxn" ? "" : "_post"+(psn+1)) + "\n";
				for (int i = 0; i < outputVXs.size(); i++) {
					outputObj = outputObj + "v " + (outputVXs.get(i) + offsetVX) + " " + (outputVYs.get(i) + offsetVY) + " "
							+ (outputVZs.get(i) + offsetVZ) + "\n";
					//				outputObj = outputObj + "v " +(outputVXs.get(i)+offsetVX) 
					//						  + " " +(outputVYs.get(i)+offsetVY) 
					//						  + " " +(outputVZs.get(i)+offsetVZ) + "\n";
				}
				outputObj = outputObj + inputVertices[inputVertices.length - 1] + "\n";

				outputObj = outputObj + "\ns " + outputFacets[0] + "\n";
				for (int i = 0; i < outputFXs.size(); i++) {
					outputObj = outputObj + "f " + outputFXs.get(i).intValue() + " " + outputFYs.get(i).intValue() + " "
							+ outputFZs.get(i).intValue() + "\n";
				}
				outputObj = outputObj + outputFacets[outputFacets.length - 1] + "\n";
				IJ.saveString(outputObj, outputPath);
}
			if (outputTag != "gapJxn") {
				String outputBTag = inputFile.getName().contains("electrical") ? "gapJxn" : "preSyn";
				String outputBPath = inputPath.replace(".obj", outputBTag + ".obj");
				String outputBObj = "";
				String[] outputBSections = outputBTag == "gapJxn" ? electricalSections : preSynSections;
				String[] outputBVertices = outputBTag == "gapJxn" ? electricalVertices : preSynVertices;
				String[] outputBFacets = outputBTag == "gapJxn" ? electricalFacets : preSynFacets;
				ArrayList<Double> outputBVXs = outputBTag == "gapJxn" ? electricalVXs : preSynVXs;
				ArrayList<Double> outputBVYs = outputBTag == "gapJxn" ? electricalVYs : preSynVYs;
				ArrayList<Double> outputBVZs = outputBTag == "gapJxn" ? electricalVZs : preSynVZs;
				ArrayList<Double> outputBFXs = outputBTag == "gapJxn" ? electricalFXs : preSynFXs;
				ArrayList<Double> outputBFYs = outputBTag == "gapJxn" ? electricalFYs : preSynFYs;
				ArrayList<Double> outputBFZs = outputBTag == "gapJxn" ? electricalFZs : preSynFZs;


				double offsetBVX = ivxMedian - (outputBTag == "gapJxn" ? evxMedian : prevxMedian);
				double offsetBVY = ivyMedian - (outputBTag == "gapJxn" ? evyMedian : prevyMedian);
				double offsetBVZ = ivzMin - (outputTag == "gapJxn" ? (evzMean - evzMin) : (prevzMean));
//				double zScaleB = (ivzMax-ivzMin)/((outputBTag=="gapJxn"?evzMax:prevzMax)-(outputBTag=="gapJxn"?evzMin:prevzMin));
				outputBObj = outputBObj + inputSections[0] + "\ng " + inputVertices[0]
						+ (outputBTag == "gapJxn" ? "" : "_pre") + "\n";
				for (int i = 0; i < outputBVXs.size(); i++) {
					outputBObj = outputBObj + "v " + (outputBVXs.get(i) + offsetBVX) + " "
							+ (outputBVYs.get(i) + offsetBVY) + " " + (outputBVZs.get(i) + offsetBVZ) + "\n";
				}
				outputBObj = outputBObj + inputVertices[inputVertices.length - 1] + "\n";

				outputBObj = outputBObj + "\ns " + outputBFacets[0] + "\n";
				for (int i = 0; i < outputBFXs.size(); i++) {
					outputBObj = outputBObj + "f " + outputBFXs.get(i).intValue() + " " + outputBFYs.get(i).intValue()
							+ " " + outputBFZs.get(i).intValue() + "\n";
				}
				outputBObj = outputBObj + outputBFacets[outputBFacets.length - 1] + "\n";
				IJ.saveString(outputBObj, outputBPath);

			}
		}
	}

	public void plotSynapseObjsToCoords() {

		String electricalObj = IJ.openUrlAsString(
				MQTVSSceneLoader64.class.getResource("docs/SVV_newZap25_newZap25_960_0000.obj").toString());
		String postSynObj = IJ.openUrlAsString(
				MQTVSSceneLoader64.class.getResource("docs/SVV_newDiamond25_newDiamond25_386_0000.obj").toString());
//		String preSynObj = IJ.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/SVV_newCircle19_newCircle19_326_0000.obj").toString());
		String preSynObj = IJ
				.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.obj").toString());

//		postSynObj = electricalObj;

		IJ.log(preSynObj + postSynObj + electricalObj);
		String[] preSynSections = preSynObj.split("(\ng |\ns )");
		String[] postSynSections = postSynObj.split("(\ng |\ns )");
		String[] electricalSections = electricalObj.split("(\ng |\ns )");
		IJ.log("" + preSynSections.length + postSynSections.length + electricalSections.length);
		String[] electricalVertices = electricalSections[1].split("\n");
		String[] electricalFacets = electricalSections[2].split("\n");
		String[] postSynVertices = postSynSections[1].split("\n");
		String[] postSynFacets = postSynSections[2].split("\n");
		String[] preSynVertices = preSynSections[1].split("\n");
		String[] preSynFacets = preSynSections[2].split("\n");
		ArrayList<Double> electricalVXs = new ArrayList<Double>();
		ArrayList<Double> electricalVYs = new ArrayList<Double>();
		ArrayList<Double> electricalVZs = new ArrayList<Double>();
		ArrayList<Double> postSynVXs = new ArrayList<Double>();
		ArrayList<Double> postSynVYs = new ArrayList<Double>();
		ArrayList<Double> postSynVZs = new ArrayList<Double>();
		ArrayList<Double> preSynVXs = new ArrayList<Double>();
		ArrayList<Double> preSynVYs = new ArrayList<Double>();
		ArrayList<Double> preSynVZs = new ArrayList<Double>();
		ArrayList<Double> electricalFXs = new ArrayList<Double>();
		ArrayList<Double> electricalFYs = new ArrayList<Double>();
		ArrayList<Double> electricalFZs = new ArrayList<Double>();
		ArrayList<Double> postSynFXs = new ArrayList<Double>();
		ArrayList<Double> postSynFYs = new ArrayList<Double>();
		ArrayList<Double> postSynFZs = new ArrayList<Double>();
		ArrayList<Double> preSynFXs = new ArrayList<Double>();
		ArrayList<Double> preSynFYs = new ArrayList<Double>();
		ArrayList<Double> preSynFZs = new ArrayList<Double>();
		for (int x = 0; x < electricalVertices.length; x++) {
			if (electricalVertices[x].startsWith("v ")) {
				electricalVXs.add(Double.parseDouble(electricalVertices[x].split(" ")[1]));
				electricalVYs.add(Double.parseDouble(electricalVertices[x].split(" ")[2]));
				electricalVZs.add(Double.parseDouble(electricalVertices[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < electricalFacets.length; x++) {
			if (electricalFacets[x].startsWith("f ")) {
				electricalFXs.add(Double.parseDouble(electricalFacets[x].split(" ")[1]));
				electricalFYs.add(Double.parseDouble(electricalFacets[x].split(" ")[2]));
				electricalFZs.add(Double.parseDouble(electricalFacets[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < postSynVertices.length; x++) {
			if (postSynVertices[x].startsWith("v ")) {
				postSynVXs.add(Double.parseDouble(postSynVertices[x].split(" ")[1]));
				postSynVYs.add(Double.parseDouble(postSynVertices[x].split(" ")[2]));
				postSynVZs.add(Double.parseDouble(postSynVertices[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < postSynFacets.length; x++) {
			if (postSynFacets[x].startsWith("f ")) {
				postSynFXs.add(Double.parseDouble(postSynFacets[x].split(" ")[1]));
				postSynFYs.add(Double.parseDouble(postSynFacets[x].split(" ")[2]));
				postSynFZs.add(Double.parseDouble(postSynFacets[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < preSynVertices.length; x++) {
			if (preSynVertices[x].startsWith("v ")) {
				preSynVXs.add(Double.parseDouble(preSynVertices[x].split(" ")[1]));
				preSynVYs.add(Double.parseDouble(preSynVertices[x].split(" ")[2]));
				preSynVZs.add(Double.parseDouble(preSynVertices[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < preSynFacets.length; x++) {
			if (preSynFacets[x].startsWith("f ")) {
				preSynFXs.add(Double.parseDouble(preSynFacets[x].split(" ")[1]));
				preSynFYs.add(Double.parseDouble(preSynFacets[x].split(" ")[2]));
				preSynFZs.add(Double.parseDouble(preSynFacets[x].split(" ")[3]));
			}
		}

		Object[] evxs = (electricalVXs.toArray());
		Arrays.sort(evxs);
		double evxMedian = (double) evxs[evxs.length / 2];
		Object[] postvxs = (postSynVXs.toArray());
		Arrays.sort(postvxs);
		double postvxMedian = (double) postvxs[postvxs.length / 2];
		Object[] prevxs = (preSynVXs.toArray());
		Arrays.sort(prevxs);
		double prevxMedian = (double) prevxs[prevxs.length / 2];
		Object[] evys = (electricalVYs.toArray());
		Arrays.sort(evys);
		double evyMedian = (double) evys[evys.length / 2];
		Object[] postvys = (postSynVYs.toArray());
		Arrays.sort(postvys);
		double postvyMedian = (double) postvys[postvys.length / 2];
		Object[] prevys = (preSynVYs.toArray());
		Arrays.sort(prevys);
		double prevyMedian = (double) prevys[prevys.length / 2];
		Object[] evzs = (electricalVZs.toArray());
		Arrays.sort(evzs);
		double evzMedian = (double) evzs[evzs.length / 2];
		double evzMin = (double) evzs[0];
		double evzMax = (double) evzs[evzs.length - 1];
		Object[] postvzs = (postSynVZs.toArray());
		Arrays.sort(postvzs);
		double postvzMedian = (double) postvzs[postvzs.length / 2];
		double postvzMin = (double) postvzs[0];
		double postvzMax = (double) postvzs[postvzs.length - 1];
		Object[] prevzs = (preSynVZs.toArray());
		Arrays.sort(prevzs);
		double prevzMedian = (double) prevzs[prevzs.length / 2];
		double prevzMin = (double) prevzs[0];
		double prevzMax = (double) prevzs[prevzs.length - 1];
		Object[] efxs = (electricalFXs.toArray());
		Arrays.sort(efxs);
		double efxMedian = (double) efxs[efxs.length / 2];
		Object[] postfxs = (postSynFXs.toArray());
		Arrays.sort(postfxs);
		double postfxMedian = (double) postfxs[postfxs.length / 2];
		Object[] prefxs = (preSynFXs.toArray());
		Arrays.sort(prefxs);
		double prefxMedian = (double) prefxs[prefxs.length / 2];
		Object[] efys = (electricalFYs.toArray());
		Arrays.sort(efys);
		double efyMedian = (double) efys[efys.length / 2];
		Object[] postfys = (postSynFYs.toArray());
		Arrays.sort(postfys);
		double postfyMedian = (double) postfys[postfys.length / 2];
		Object[] prefys = (preSynFYs.toArray());
		Arrays.sort(prefys);
		double prefyMedian = (double) prefys[prefys.length / 2];
		Object[] efzs = (electricalFZs.toArray());
		Arrays.sort(efzs);
		double efzMedian = (double) efzs[efzs.length / 2];
		Object[] postfzs = (postSynFZs.toArray());
		Arrays.sort(postfzs);
		double postfzMedian = (double) postfzs[postfzs.length / 2];
		Object[] prefzs = (preSynFZs.toArray());
		Arrays.sort(prefzs);
		double prefzMedian = (double) prefzs[prefzs.length / 2];

		IJ.wait(1);
		String inputPath = IJ.getFilePath("Select csv file with PHATE data");
		String mtlPath = IJ.getFilePath("Select mtl file with color rules");
		File inputFile = new File(inputPath);
		File mtlFile = new File(mtlPath);
		String outputDir = inputFile.getParent() + File.separator + inputFile.getName().replace(".csv", "")
				+ File.separator;
		new File(outputDir).mkdirs();
		IJ.saveString(IJ.openAsString(mtlPath), outputDir + mtlFile.getName());
		String inputPhateData = IJ.openAsString(inputPath);
		String[] inputPhateList = inputPhateData.split("\n");
		for (String phateLine : inputPhateList) {
			if (phateLine.startsWith("serialNumber"))
				continue;
			String[] phateLineChunks = phateLine.split(",");

			String outputTag = phateLineChunks[5];
			String outputPath = outputDir + inputFile.getName() + "_" + outputTag + "plot.obj";
			String[] outputSections = postSynSections;
			String[] outputVertices = postSynVertices;
			String[] outputFacets = postSynFacets;
			ArrayList<Double> outputVXs = postSynVXs;
			ArrayList<Double> outputVYs = postSynVYs;
			ArrayList<Double> outputVZs = postSynVZs;
			ArrayList<Double> outputFXs = postSynFXs;
			ArrayList<Double> outputFYs = postSynFYs;
			ArrayList<Double> outputFZs = postSynFZs;

			double offsetVX = Double.parseDouble(phateLineChunks[1]) * 1000 - (postvxMedian);
			double offsetVY = Double.parseDouble(phateLineChunks[2]) * 1000 - (postvyMedian);
			double offsetVZ = Double.parseDouble(phateLineChunks[3]) * 1000 - (postvzMedian);
			double zScale = 1;
			String outputObj = "";
			outputObj = outputObj + "# OBJ File\nmtllib " + mtlFile.getName() + "\ng " + phateLineChunks[5] + "\n";
			for (int i = 0; i < outputVXs.size(); i++) {
				outputObj = outputObj + "v " + (outputVXs.get(i) + offsetVX) + " " + (outputVYs.get(i) + offsetVY) + " "
						+ (((outputVZs.get(i)) * zScale) + offsetVZ) + "\n";
			}
			outputObj = outputObj + "usemtl mat_" + phateLineChunks[4];

			outputObj = outputObj + "\ns " + outputFacets[0] + "\n";
			for (int i = 0; i < outputFXs.size(); i++) {
				outputObj = outputObj + "f " + outputFXs.get(i).intValue() + " " + outputFYs.get(i).intValue() + " "
						+ outputFZs.get(i).intValue() + "\n";
			}
			outputObj = outputObj + outputFacets[outputFacets.length - 1] + "\n";
			IJ.saveString(outputObj, outputPath);

		}

	}

	public void plotPhateObjsToCoordsSpheres() {

		IJ.wait(1);
		String inputPath = IJ.getFilePath("Select csv file with PHATE data");
		String condensationSNpath = IJ.getFilePath("Select csv file with condensation cluster data");
//		String mtlPath = IJ.getFilePath("Select mtl file with color rules");
		File inputFile = new File(inputPath);
		File conSNFile = new File(condensationSNpath);
//		File mtlFile = new File(mtlPath);
		String outputDir = inputFile.getParent() + File.separator + inputFile.getName().replace(".csv", "")
				+ File.separator;
		new File(outputDir).mkdirs();
//		IJ.saveString(IJ.openAsString(mtlPath), outputDir+mtlFile.getName());
		String inputPhateData = IJ.openAsString(inputPath);
		String[] inputPhateList = inputPhateData.split("\n");
		String conSNData = IJ.openAsString(condensationSNpath);
		String[] conSNList = conSNData.split("\n");
		ImageJ3DViewer ij3dv = new ImageJ3DViewer();
		ij3dv.run(".");
		Image3DUniverse univ = Image3DUniverse.universes.get(0);
		String[] cellHeaders = null;
		int previousMaxSN = 0;
		int nextMaxSN = 0;
		Hashtable<Integer, String> serialRosters = new Hashtable<Integer, String>();
		for (int iteration = 0; iteration < conSNList.length; iteration++) {
			String[] csnChunks = conSNList[iteration].split(",");
			if (iteration == 0) {
				cellHeaders = csnChunks;
			}
			for (int cell = 0; cell < cellHeaders.length; cell++) {
				int sn = 0;
				if (iteration == 0) {
					sn = previousMaxSN + cell;
				} else {
					sn = previousMaxSN + Integer.parseInt(csnChunks[cell]);
				}
				IJ.log(cellHeaders[cell] + " " + iteration + " " + csnChunks[cell] + " " + sn);
				if (serialRosters.get(sn) == null) {
					serialRosters.put(sn, cellHeaders[cell]);
				} else {
					serialRosters.put(sn, serialRosters.get(sn) + "_" + cellHeaders[cell]);
				}
				if (sn > nextMaxSN) {
					nextMaxSN = sn;
				}
			}
			previousMaxSN = nextMaxSN;
		}

		for (String phateLine : inputPhateList) {
			if (phateLine.startsWith("serialNumber"))
				continue;
			String[] phateLineChunks = phateLine.split(",");
			String serial = phateLineChunks[0];
			String outputTag = serialRosters.get(Integer.parseInt(serial)) + "-" + serial;
			float offsetVX = (float) (Double.parseDouble(phateLineChunks[1]) * 1000 - 50000);
			float offsetVY = (float) (Double.parseDouble(phateLineChunks[2]) * 1000 - 50000);
			float offsetVZ = (float) (Double.parseDouble(phateLineChunks[3]) * 1000 - 50000);
			Sphere sph800 = new Sphere(new Point3f(offsetVX, offsetVY, offsetVZ), 800, 50, 50);

			String colorString = phateLineChunks[4];
			float cycle = Float.parseFloat(phateLineChunks[9]);

			cycle = 25f; // hack to give full colors
			if (inputPath.toLowerCase().contains("n2u")) {
				if (colorString.equals("1"))
					sph800.setColor(new Color3f(1f, 1 - cycle / 25f, 1f));
				if (colorString.equals("2"))
					sph800.setColor(new Color3f(1 - cycle / 25f, 1 - cycle / 25f, 1f));
				if (colorString.equals("3"))
					sph800.setColor(new Color3f(1f, 1 - cycle / 25f, 1 - cycle / 25f));
				if (colorString.equals("4"))
					sph800.setColor(new Color3f(1 - cycle / 25f, 1f, 1 - cycle / 25f));
				if (colorString.equals("5"))
					sph800.setColor(new Color3f(1.5f - cycle / 25f, 1.5f - cycle / 25f, 1.5f - cycle / 25f));
				if (colorString.equals("6"))
					sph800.setColor(new Color3f(1.5f - cycle / 25f, 1.5f - cycle / 25f, 1.5f - cycle / 25f));
			}
			if (inputPath.toLowerCase().contains("jsh")) {
				if (colorString.equals("1"))
					sph800.setColor(new Color3f(1 - cycle / 25f, 1 - cycle / 25f, 1f));
				if (colorString.equals("2"))
					sph800.setColor(new Color3f(1 - cycle / 25f, 1 - cycle / 25f, 1f));
				if (colorString.equals("3"))
					sph800.setColor(new Color3f(1f, 1 - cycle / 25f, 1f));
				if (colorString.equals("4"))
					sph800.setColor(new Color3f(1f, 1 - cycle / 25f, 1 - cycle / 25f));
				if (colorString.equals("5"))
					sph800.setColor(new Color3f(1 - cycle / 25f, 1f, 1 - cycle / 25f));
				if (colorString.equals("6"))
					sph800.setColor(new Color3f(1.5f - cycle / 25f, 1.5f - cycle / 25f, 1.5f - cycle / 25f));
			}

			sph800.setName(outputTag);
			Object[] existingContents = univ.getContents().toArray();
			boolean condensed = false;

			for (int s = 0; s < existingContents.length; s++) {
				Content existingContent = (Content) existingContents[s];
				String ecName = existingContent.getName();
				String currSN = "-" + serial;
				if (ecName.endsWith(currSN)) {
//					existingContent.setName(phateLineChunks[5]+"_"+ecName);
					condensed = true;
					s = existingContents.length;
				}
			}

			if (!condensed)
				IJ.log(outputTag + "\n");
			univ.addCustomMesh(sph800, outputTag).setLocked(true);

		}
//		univ.addCustomMesh(bigmesh,"multi");

	}

	public void plotOriginalPhateObjsToCoordsIcospheres() {

		IJ.wait(1);
		String icosphereObj = IJ
				.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.obj").toString());
		File mtlFile = new File(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.mtl").toString());

		IJ.log(icosphereObj);
		String[] icosphereSections = icosphereObj.split("(\ng |\ns )");
		IJ.log("" + icosphereSections.length);
		String[] icosphereVertices = icosphereSections[1].split("\n");
		String[] icosphereFacets = icosphereSections[2].split("\n");
		ArrayList<Double> icosphereVXs = new ArrayList<Double>();
		ArrayList<Double> icosphereVYs = new ArrayList<Double>();
		ArrayList<Double> icosphereVZs = new ArrayList<Double>();
		ArrayList<Double> icosphereFXs = new ArrayList<Double>();
		ArrayList<Double> icosphereFYs = new ArrayList<Double>();
		ArrayList<Double> icosphereFZs = new ArrayList<Double>();

		for (int x = 0; x < icosphereVertices.length; x++) {
			if (icosphereVertices[x].startsWith("v ")) {
				icosphereVXs.add(Double.parseDouble(icosphereVertices[x].split(" ")[1]));
				icosphereVYs.add(Double.parseDouble(icosphereVertices[x].split(" ")[2]));
				icosphereVZs.add(Double.parseDouble(icosphereVertices[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < icosphereFacets.length; x++) {
			if (icosphereFacets[x].startsWith("f ")) {
				icosphereFXs.add(Double.parseDouble(icosphereFacets[x].split(" ")[1]));
				icosphereFYs.add(Double.parseDouble(icosphereFacets[x].split(" ")[2]));
				icosphereFZs.add(Double.parseDouble(icosphereFacets[x].split(" ")[3]));
			}
		}

		Object[] icospherevxs = (icosphereVXs.toArray());
		Arrays.sort(icospherevxs);
		double icospherevxMedian = (double) icospherevxs[icospherevxs.length / 2];
		Object[] icospherevys = (icosphereVYs.toArray());
		Arrays.sort(icospherevys);
		double icospherevyMedian = (double) icospherevys[icospherevys.length / 2];
		Object[] icospherevzs = (icosphereVZs.toArray());
		Arrays.sort(icospherevzs);
		double icospherevzMedian = (double) icospherevzs[icospherevzs.length / 2];
		double icospherevzMin = (double) icospherevzs[0];
		double icospherevzMax = (double) icospherevzs[icospherevzs.length - 1];
		Object[] icospherefxs = (icosphereFXs.toArray());
		Arrays.sort(icospherefxs);
		double icospherefxMedian = (double) icospherefxs[icospherefxs.length / 2];
		Object[] icospherefys = (icosphereFYs.toArray());
		Arrays.sort(icospherefys);
		double icospherefyMedian = (double) icospherefys[icospherefys.length / 2];
		Object[] icospherefzs = (icosphereFZs.toArray());
		Arrays.sort(icospherefzs);
		double icospherefzMedian = (double) icospherefzs[icospherefzs.length / 2];

		String inputPath = IJ.getFilePath("Select csv file with PHATE data");
		String condensationSNpath = IJ.getFilePath("Select csv file with condensation cluster data");
//		String mtlPath = IJ.getFilePath("Select mtl file with color rules");
		File inputFile = new File(inputPath);
		File conSNFile = new File(condensationSNpath);
//		File mtlFile = new File(mtlPath);
		String outputDir = inputFile.getParent() + File.separator + inputFile.getName().replace(".csv", "")
				+ File.separator;
		new File(outputDir).mkdirs();
//		IJ.saveString(IJ.openAsString(mtlPath), outputDir+mtlFile.getName());
		String inputPhateData = IJ.openAsString(inputPath);
		String[] inputPhateList = inputPhateData.split("\n");
		String conSNData = IJ.openAsString(condensationSNpath);
		String[] conSNList = conSNData.split("\n");
		String[] cellHeaders = null;
		int previousMaxSN = 0;
		int nextMaxSN = 0;
		int iterationOfSix = 0;
		Hashtable<Integer, String> serialToRosterStringHashtable = new Hashtable<Integer, String>();
		Hashtable<Integer, ArrayList<String>> serialToRosterArrayHashtable = new Hashtable<Integer, ArrayList<String>>();

		Hashtable<String, Integer[][]> nameToRanksAndSNsHashtable = new Hashtable<String, Integer[][]>();

		for (int iteration = 0; iteration < conSNList.length; iteration++) {
			String[] csnChunks = conSNList[iteration].split(",");
			if (iteration == 0) {
				cellHeaders = csnChunks;
			} else {
				int maxGroupNum = 0;
				for (int cell = 0; cell < cellHeaders.length; cell++) {
					if (nameToRanksAndSNsHashtable.get(cellHeaders[cell]) == null) {
						nameToRanksAndSNsHashtable.put(cellHeaders[cell], new Integer[2][conSNList.length]);
					}
					int sn = 0;
					if (iteration == 0) {
						sn = previousMaxSN + cell;
					} else {
						sn = previousMaxSN + Integer.parseInt(csnChunks[cell]);
					}

					nameToRanksAndSNsHashtable.get(cellHeaders[cell])[0][iteration] = Integer.parseInt(csnChunks[cell]);
					nameToRanksAndSNsHashtable.get(cellHeaders[cell])[1][iteration] = sn;

					IJ.log(cellHeaders[cell] + " " + iteration + " " + csnChunks[cell] + " " + sn);
					if (serialToRosterStringHashtable.get(sn) == null) {
						serialToRosterStringHashtable.put(sn, cellHeaders[cell]);
						serialToRosterArrayHashtable.put(sn, new ArrayList<String>());
						serialToRosterArrayHashtable.get(sn).add(cellHeaders[cell]);
					} else {
						serialToRosterStringHashtable.put(sn,
								serialToRosterStringHashtable.get(sn) + "_" + cellHeaders[cell]);
						serialToRosterArrayHashtable.get(sn).add(cellHeaders[cell]);
					}
					if (sn > nextMaxSN) {
						nextMaxSN = sn;
					}
					if (iteration > 0 && Integer.parseInt(csnChunks[cell]) > maxGroupNum) {
						maxGroupNum = Integer.parseInt(csnChunks[cell]);
					}
				}
				if (maxGroupNum == 6) {
					iterationOfSix = iteration;
				}
				previousMaxSN = nextMaxSN;
			}
		}
		String cluster_assignments_rebuiltAGtoMKfmt = "";
		for (int iter = 0; iter < conSNList.length; iter++) {
			if (iter > 0) {

//				Manually in Excel:
//
//
//					Paste copy of original Alex line 12 or greater in a place separate from rest of sheet.  Bring tally cells along.
//					Paste copy of original Alex line 1  (columns with 1-178) below it.   Bring tally cells along.
//
//					Read upper row from left, looking for interruptions to the continuous upward number flow.  
//
//					At each deviant cell, record the substituted value and scan (usually backward) until you find another instance of the same number.
//					Read below the second instance and record the "normal" position that it corresponds to.  Return to the original deviation spot, and enter that number into the cell below the deviation.
//
//					Repeat the process, moving to the right.  Pay special attention to omit the correctly ordered cells from substitution:
//					After correct 13 followed by eg two deviations, find 14.  Do not substitute for these original in sequence instances of each number.  For any earlier or later deviant entries of 14, do the swap process.
//
//					Need to enact this with arraylists...  Actually, arrays should work!!!  Just need to create row arrays.

				int[] rowOneArray = new int[cellHeaders.length];
				int[] rowNArray = new int[cellHeaders.length];
				for (int colIndex = 0; colIndex < cellHeaders.length; colIndex++) {
					rowOneArray[colIndex] = nameToRanksAndSNsHashtable.get(cellHeaders[colIndex])[0][1];
					rowNArray[colIndex] = nameToRanksAndSNsHashtable.get(cellHeaders[colIndex])[0][iter];
				}

				int targetValue = -1;
				int deviantIndex = -1;
				int deviantValue = -1;
				ArrayList<Integer> deviantValuesProcessed = new ArrayList<Integer>();
				for (int countingIndex = 0; countingIndex < cellHeaders.length - 1; countingIndex++) {
					if (targetValue < 0) {
						targetValue = rowNArray[countingIndex] + 1;
					}
					if (targetValue > 177) {
						IJ.wait(1);
					}
					if (rowNArray[countingIndex + 1] != targetValue) {
						deviantValue = rowNArray[countingIndex + 1];
						deviantIndex = countingIndex + 1;
					} else {
						targetValue = -1; // push on to next target
						continue;
					}
					if (!deviantValuesProcessed.contains(deviantValue)) {
						deviantValuesProcessed.add(deviantValue);
						ArrayList<Integer> deviantHitIndexes = new ArrayList<Integer>();
						for (int scanningIndex = 0; scanningIndex < cellHeaders.length; scanningIndex++) {
							if (rowNArray[scanningIndex] == deviantValue) {
								deviantHitIndexes.add(scanningIndex);
							}
						}
						int minDevHitIndex = cellHeaders.length;
						for (int dhi : deviantHitIndexes) {
							if (dhi < minDevHitIndex) {
								minDevHitIndex = dhi;
							}
						}
						for (int fixingIndex : deviantHitIndexes) {
							rowOneArray[fixingIndex] = minDevHitIndex + 1;
						}
					} else {
						deviantIndex = -1; // push on to next deviant
						deviantValue = -1;
						continue;
					}

				}
				IJ.wait(1);

				for (int colIndex = 0; colIndex < cellHeaders.length; colIndex++) {
					cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt
							+ (colIndex > 0 ? "," : "") + rowOneArray[colIndex];
				}
				cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt + "\n";

			} else {
				for (int colIndex = 0; colIndex < cellHeaders.length; colIndex++) {
					cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt
							+ (colIndex > 0 ? "," : "") + cellHeaders[colIndex];
				}
				cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt + "\n";
			}
		}
		IJ.saveString(cluster_assignments_rebuiltAGtoMKfmt,
				condensationSNpath.replace(".csv", "") + "_rebuiltAGtoMKfmt.csv");

		Hashtable<String, Integer> clusterColorTable = new Hashtable<String, Integer>();

		for (int cell = 0; cell < cellHeaders.length; cell++) {
			String[] csnChunks = conSNList[iterationOfSix].split(",");
			clusterColorTable.put(cellHeaders[cell], Integer.parseInt(csnChunks[cell]));
		}

		for (String phateLine : inputPhateList) {
			if (phateLine.startsWith("serialNumber"))
				continue;
			String[] phateLineChunks = phateLine.split(",");
			String serial = phateLineChunks[0];

//			String outputTag = phateLineChunks[5]+"-"+serial;
			String outputTag = serialToRosterStringHashtable.get(Integer.parseInt(serial)) + "-" + serial;

			String outputPath = outputDir + inputFile.getName() + "-" + serial + "-" + outputTag.split("_")[0] + ".obj";
			String[] outputSections = icosphereSections;
			String[] outputVertices = icosphereVertices;
			String[] outputFacets = icosphereFacets;
			ArrayList<Double> outputVXs = icosphereVXs;
			ArrayList<Double> outputVYs = icosphereVYs;
			ArrayList<Double> outputVZs = icosphereVZs;
			ArrayList<Double> outputFXs = icosphereFXs;
			ArrayList<Double> outputFYs = icosphereFYs;
			ArrayList<Double> outputFZs = icosphereFZs;

			double offsetVX = Double.parseDouble(phateLineChunks[1]) * 1000 - (icospherevxMedian);
			double offsetVY = Double.parseDouble(phateLineChunks[2]) * 1000 - (icospherevyMedian);
			double offsetVZ = Double.parseDouble(phateLineChunks[3]) * 1000 - (icospherevzMedian);
			double zScale = 1;
			String outputObj = "";
			outputObj = outputObj + "# OBJ File\nmtllib " + mtlFile.getName() + "\ng " + outputTag + "\n";
			for (int i = 0; i < outputVXs.size(); i++) {
				outputObj = outputObj + "v " + (outputVXs.get(i) + offsetVX) + " " + (outputVYs.get(i) + offsetVY) + " "
						+ (((outputVZs.get(i)) * zScale) + offsetVZ) + "\n";
			}
//			outputObj = outputObj + "usemtl mat_"+ phateLineChunks[4] ;
			String leadCellName = "";
			if (outputTag.split("_")[0].contains("-")) {
				leadCellName = outputTag.split("-")[0];
			} else {
				leadCellName = outputTag.split("_")[0];
			}
			outputObj = outputObj + "usemtl mat_" + clusterColorTable.get(leadCellName);

			outputObj = outputObj + "\ns " + outputFacets[0] + "\n";
			for (int i = 0; i < outputFXs.size(); i++) {
				outputObj = outputObj + "f " + outputFXs.get(i).intValue() + " " + outputFYs.get(i).intValue() + " "
						+ outputFZs.get(i).intValue() + "\n";
			}
			outputObj = outputObj + outputFacets[outputFacets.length - 1] + "\n";
			IJ.saveString(outputObj, outputPath);

		}
//		univ.addCustomMesh(bigmesh,"multi");

	}

	public void plotAlexFmtPhateObjsToCoordsIcospheres() {

		IJ.wait(1);
		String icosphereObj = IJ
				.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.obj").toString());
		File mtlFile = new File(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.mtl").toString());

		IJ.log(icosphereObj);
		String[] icosphereSections = icosphereObj.split("(\ng |\ns )");
		IJ.log("" + icosphereSections.length);
		String[] icosphereVertices = icosphereSections[1].split("\n");
		String[] icosphereFacets = icosphereSections[2].split("\n");
		ArrayList<Double> icosphereVXs = new ArrayList<Double>();
		ArrayList<Double> icosphereVYs = new ArrayList<Double>();
		ArrayList<Double> icosphereVZs = new ArrayList<Double>();
		ArrayList<Double> icosphereFXs = new ArrayList<Double>();
		ArrayList<Double> icosphereFYs = new ArrayList<Double>();
		ArrayList<Double> icosphereFZs = new ArrayList<Double>();

		for (int x = 0; x < icosphereVertices.length; x++) {
			if (icosphereVertices[x].startsWith("v ")) {
				icosphereVXs.add(Double.parseDouble(icosphereVertices[x].split(" ")[1]));
				icosphereVYs.add(Double.parseDouble(icosphereVertices[x].split(" ")[2]));
				icosphereVZs.add(Double.parseDouble(icosphereVertices[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < icosphereFacets.length; x++) {
			if (icosphereFacets[x].startsWith("f ")) {
				icosphereFXs.add(Double.parseDouble(icosphereFacets[x].split(" ")[1]));
				icosphereFYs.add(Double.parseDouble(icosphereFacets[x].split(" ")[2]));
				icosphereFZs.add(Double.parseDouble(icosphereFacets[x].split(" ")[3]));
			}
		}

		Object[] icospherevxs = (icosphereVXs.toArray());
		Arrays.sort(icospherevxs);
		double icospherevxMedian = (double) icospherevxs[icospherevxs.length / 2];
		Object[] icospherevys = (icosphereVYs.toArray());
		Arrays.sort(icospherevys);
		double icospherevyMedian = (double) icospherevys[icospherevys.length / 2];
		Object[] icospherevzs = (icosphereVZs.toArray());
		Arrays.sort(icospherevzs);
		double icospherevzMedian = (double) icospherevzs[icospherevzs.length / 2];
		double icospherevzMin = (double) icospherevzs[0];
		double icospherevzMax = (double) icospherevzs[icospherevzs.length - 1];
		Object[] icospherefxs = (icosphereFXs.toArray());
		Arrays.sort(icospherefxs);
		double icospherefxMedian = (double) icospherefxs[icospherefxs.length / 2];
		Object[] icospherefys = (icosphereFYs.toArray());
		Arrays.sort(icospherefys);
		double icospherefyMedian = (double) icospherefys[icospherefys.length / 2];
		Object[] icospherefzs = (icosphereFZs.toArray());
		Arrays.sort(icospherefzs);
		double icospherefzMedian = (double) icospherefzs[icospherefzs.length / 2];

		IJ.log("Select csv file with PHATE coordinates data");
		String inputPhateCoordinatesPath = IJ.getFilePath("Select csv file with PHATE coordinates data");
		IJ.log("Select csv file with condensation cluster assignment data");
		String clusterAssignmentsPath = IJ.getFilePath("Select csv file with condensation cluster assignment data");
		IJ.log("!!Select csv file with condensation cluster assignment data to compare!!");
		String clusterAssignmentsCOMPPath = IJ
				.getFilePath("!!Select csv file with condensation cluster assignment data to compare!!");
		IJ.log("Select Mei Lab metadata for naming");
		String cellNamesFromMeiLabPath = IJ.getFilePath("Select Mei Lab metadata for naming");
		IJ.log("Select mtl file with color rules");
		String mtlPath = IJ.getFilePath("Select mtl file with color rules");
		File inputFile = new File(inputPhateCoordinatesPath);
		File clustAsnFile = new File(clusterAssignmentsPath);
		mtlFile = new File(mtlPath);
		String outputDir = inputFile.getParent() + File.separator + inputFile.getName().replace(".csv", "")
				+ File.separator;
		new File(outputDir).mkdirs();
		IJ.saveString(IJ.openAsString(mtlPath), outputDir + mtlFile.getName());
		String inputPhateCoordinatesData = IJ.openAsString(inputPhateCoordinatesPath);
		String[] inputPhateCoordinatesRows = inputPhateCoordinatesData.split("\n");
		String clusterAsgnData = IJ.openAsString(clusterAssignmentsPath);
		String[] clusterAsgnRows = clusterAsgnData.split("\n");
		String clusterAsgnCOMPData = IJ.openAsString(clusterAssignmentsCOMPPath);
		String[] clusterAsgnCOMPRows = clusterAsgnCOMPData.split("\n");
		String[] cellHeadersInitial = null;
		String[] cellHeadersDecoded = null;
		String cellNamesFromMeiLabData;
		String[] cellNamesFromMeiLabRows = null;
		String[] cellNames = null;
		if (cellNamesFromMeiLabPath != null) {
			cellNamesFromMeiLabData = IJ.openAsString(cellNamesFromMeiLabPath);
			cellNamesFromMeiLabRows = cellNamesFromMeiLabData.split("\n");
			cellNames = null;
		}

		int previousMaxSN = -1;
		int nextMaxSN = 0;
		int iterationOfSix = 0;
		int iterationOfFour = 0;
		int iterationOfNine = 0;
		int iterationOfChoice = 0;
		Hashtable<Integer, String> serialToRosterStringHashtable = new Hashtable<Integer, String>();
		Hashtable<Integer, ArrayList<String>> serialToRosterArrayHashtable = new Hashtable<Integer, ArrayList<String>>();

		Hashtable<String, Integer[][]> nameToClustersAndSNsHashtable = new Hashtable<String, Integer[][]>();
		Hashtable<String, Integer[][]> nameToCOMPClustersAndSNsHashtable = new Hashtable<String, Integer[][]>();

		Hashtable<Integer, Double[]> snToCoordsHashtable = new Hashtable<Integer, Double[]>();

		Hashtable<String, String> meiSNtoNameHashtable = new Hashtable<String, String>();

		Hashtable<String, Double[]> name_iterationToCoordsHashtable = new Hashtable<String, Double[]>();

		for (String phateCoordsRow : inputPhateCoordinatesRows) {
			if (phateCoordsRow.startsWith("serialNumber") || phateCoordsRow.startsWith(",0,1,2"))
				continue;
			String[] phateCoordsRowChunks = phateCoordsRow.split(",");
			String serial = phateCoordsRowChunks[0];
			double x = Double.parseDouble(phateCoordsRowChunks[1]);
			double y = Double.parseDouble(phateCoordsRowChunks[2]);
			double z = Double.parseDouble(phateCoordsRowChunks[3]);
			Double[] coords = { x, y, z };
			snToCoordsHashtable.put(Integer.parseInt(serial), coords);
		}

		if (cellNamesFromMeiLabPath != null) {
			for (String cellNamesFromMeiLabRow : cellNamesFromMeiLabRows) {

				if (cellNamesFromMeiLabRow.startsWith("%") || cellNamesFromMeiLabRow.equals("")
						|| cellNamesFromMeiLabRow.startsWith("0")) {
					continue;
				}
				String[] cellNamesFromMeiLabRowChunks = cellNamesFromMeiLabRow.split(" ");
				String serialNumber = cellNamesFromMeiLabRowChunks[0];
				String name = cellNamesFromMeiLabRowChunks[40];

				meiSNtoNameHashtable.put(serialNumber, name);
			}
		}

		int groupNumOfChoice = 0;
		groupNumOfChoice = (int) IJ.getNumber("Number of expected clusters?", 1);

		for (int iteration = 0; iteration < clusterAsgnRows.length; iteration++) {
			String[] clusterAsgnChunks = clusterAsgnRows[iteration].split(",");
			if (iteration == 0) {
				cellHeadersInitial = clusterAsgnChunks;
				cellHeadersDecoded = Arrays.copyOf(cellHeadersInitial, cellHeadersInitial.length);

			} else {

				int maxGroupNum = 0;
				for (int cell = 0; cell < cellHeadersInitial.length; cell++) {
					String cellName = cellHeadersInitial[cell].split("_")[0];
					if (cellNamesFromMeiLabPath != null) {
						cellName = meiSNtoNameHashtable.get(cellName.replace("Label ", "")).replace("\"", "")
								.replace("-", "");
						cellHeadersDecoded[cell] = cellHeadersInitial[cell]
								.replace(cellHeadersInitial[cell].split("_")[0], cellName);
					}
					if (nameToClustersAndSNsHashtable.get(cellName) == null) {
						nameToClustersAndSNsHashtable.put(cellName, new Integer[2][clusterAsgnRows.length]);
						int ipcrl = inputPhateCoordinatesRows.length;
					}
					int sn = 0;
					if (iteration == 0) {
						sn = previousMaxSN + cell;
					} else {
						sn = previousMaxSN + Integer.parseInt(clusterAsgnChunks[cell]);
					}

					nameToClustersAndSNsHashtable.get(cellName)[0][iteration] = Integer
							.parseInt(clusterAsgnChunks[cell]);
					nameToClustersAndSNsHashtable.get(cellName)[1][iteration] = sn;
					name_iterationToCoordsHashtable.put(cellName + "_" + iteration, snToCoordsHashtable.get(sn));

					IJ.log(cellName + " " + iteration + " " + clusterAsgnChunks[cell] + " " + sn);
					if (serialToRosterStringHashtable.get(sn) == null) {
						serialToRosterStringHashtable.put(sn, cellName);
						serialToRosterArrayHashtable.put(sn, new ArrayList<String>());
						serialToRosterArrayHashtable.get(sn).add(cellName);
					} else {
						serialToRosterStringHashtable.put(sn, serialToRosterStringHashtable.get(sn) + "_" + cellName);
						serialToRosterArrayHashtable.get(sn).add(cellName);
					}
					if (sn > nextMaxSN) {
						nextMaxSN = sn;
					}
					if (iteration > 0 && Integer.parseInt(clusterAsgnChunks[cell]) > maxGroupNum) {
						maxGroupNum = Integer.parseInt(clusterAsgnChunks[cell]);
					}
				}
				previousMaxSN = nextMaxSN;
			}
		}

		for (int iteration = 0; iteration < clusterAsgnCOMPRows.length; iteration++) {
			String[] clusterAsgnCOMPChunks = clusterAsgnCOMPRows[iteration].split(",");
			if (iteration == 0) {
				cellHeadersInitial = clusterAsgnCOMPChunks;
			} else {
				int maxGroupNum = 0;
				for (int cell = 0; cell < cellHeadersDecoded.length; cell++) {
					if (nameToCOMPClustersAndSNsHashtable.get(cellHeadersDecoded[cell].split("_")[0]) == null) {
						nameToCOMPClustersAndSNsHashtable.put(cellHeadersDecoded[cell].split("_")[0],
								new Integer[2][clusterAsgnRows.length]);
						int ipcrl = inputPhateCoordinatesRows.length;
					}
					int sn = 0;
					if (iteration == 0) {
						sn = previousMaxSN + cell;
					} else {
						sn = previousMaxSN + Integer.parseInt(clusterAsgnCOMPChunks[cell]);
					}

					if (iteration < nameToCOMPClustersAndSNsHashtable
							.get(cellHeadersDecoded[cell].split("_")[0])[0].length) {
						nameToCOMPClustersAndSNsHashtable
								.get(cellHeadersDecoded[cell].split("_")[0])[0][iteration] = Integer
										.parseInt(clusterAsgnCOMPChunks[cell]);
						nameToCOMPClustersAndSNsHashtable
								.get(cellHeadersDecoded[cell].split("_")[0])[1][iteration] = sn;
					}

					if (iteration > 0 && Integer.parseInt(clusterAsgnCOMPChunks[cell]) > maxGroupNum) {
						maxGroupNum = Integer.parseInt(clusterAsgnCOMPChunks[cell]);
					}

				}
				if (maxGroupNum >= groupNumOfChoice) {
					iterationOfChoice = iteration;
				}

				if (maxGroupNum == 9) {
					iterationOfNine = iteration;
				}
				if (maxGroupNum == 6) {
					iterationOfSix = iteration;
				}
				if (maxGroupNum == 4) {
					iterationOfFour = iteration;
				}

			}
		}

		Hashtable<String, Integer> nameKeyclusterTable = new Hashtable<String, Integer>();

// WHY ARE ALL THE FAILOVER DEFAULTS HERE = 5?  WAS THAT JUST A RANDOM CHOICE??
		if (false) {
			if (inputPhateCoordinatesPath.toLowerCase().contains("n2u")) {
				for (int cell = 0; cell < cellHeadersDecoded.length; cell++) {
					nameKeyclusterTable.put(cellHeadersDecoded[cell].split("_")[0],
							nameToCOMPClustersAndSNsHashtable
									.get(cellHeadersDecoded[cell].split("_")[0])[0][iterationOfSix != 0 ? iterationOfSix
											: 5]);
				}
			} else if (inputPhateCoordinatesPath.toLowerCase().contains("jsh")) {
				for (int cell = 0; cell < cellHeadersDecoded.length; cell++) {
					nameKeyclusterTable.put(cellHeadersDecoded[cell].split("_")[0],
							nameToCOMPClustersAndSNsHashtable.get(
									cellHeadersDecoded[cell].split("_")[0])[0][iterationOfFour != 0 ? iterationOfFour
											: iterationOfSix != 0 ? iterationOfSix : 5]);
				}
			} else {
				for (int cell = 0; cell < cellHeadersDecoded.length; cell++) {
					nameKeyclusterTable.put(cellHeadersDecoded[cell].split("_")[0],
							nameToCOMPClustersAndSNsHashtable.get(
									cellHeadersDecoded[cell].split("_")[0])[0][iterationOfNine != 0 ? iterationOfNine
											: iterationOfFour != 0 ? iterationOfFour
													: iterationOfSix != 0 ? iterationOfSix : 5]);
				}
			}
		} else {
			/**/
			for (int cell = 0; cell < cellHeadersDecoded.length; cell++) {
				nameKeyclusterTable.put(cellHeadersDecoded[cell].split("_")[0], nameToCOMPClustersAndSNsHashtable
						.get(cellHeadersDecoded[cell].split("_")[0])[0][iterationOfChoice]);
			}
			/**/
		}

		for (String phateCoordsRow : inputPhateCoordinatesRows) {
			if (phateCoordsRow.startsWith("serialNumber") || phateCoordsRow.startsWith(",0,1,2"))
				continue;
			String[] phateCoordsRowChunks = phateCoordsRow.split(",");
			String serial = phateCoordsRowChunks[0];

//			String outputTag = phateLineChunks[5]+"-"+serial;
			String outputTag = serialToRosterStringHashtable.get(Integer.parseInt(serial));
			Integer[] clusters = null;
			Integer[] SNs = null;
			if (cellNamesFromMeiLabPath == null) {
				clusters = nameToClustersAndSNsHashtable.get(outputTag.split("[_-]")[0])[0];
				SNs = nameToClustersAndSNsHashtable.get(outputTag.split("[_-]")[0])[1];
			} else if (cellNamesFromMeiLabPath != null) {
				clusters = nameToClustersAndSNsHashtable.get(outputTag.split("[_-]")[0])[0];
				SNs = nameToClustersAndSNsHashtable.get(outputTag.split("[_-]")[0])[1];
			}
			int itr = -1;
			int cluster = -1;
			for (int s = 1; s < SNs.length; s++) {
				if (SNs[s] == Integer.parseInt(serial)) {
					itr = s;
					cluster = clusters[s];
				}
			}
			outputTag = outputTag + "-i" + itr + "-c" + cluster + "-s" + serial;

			String outputPath = outputDir + inputFile.getName() + ".i" + itr + ".c" + cluster + ".s" + serial + "."
					+ outputTag.split("[_-]")[0] + ".obj";

			String[] outputSections = icosphereSections;
			String[] outputVertices = icosphereVertices;
			String[] outputFacets = icosphereFacets;
			ArrayList<Double> outputVXs = icosphereVXs;
			ArrayList<Double> outputVYs = icosphereVYs;
			ArrayList<Double> outputVZs = icosphereVZs;
			ArrayList<Double> outputFXs = icosphereFXs;
			ArrayList<Double> outputFYs = icosphereFYs;
			ArrayList<Double> outputFZs = icosphereFZs;

			double offsetVX = snToCoordsHashtable.get(Integer.parseInt(serial))[0] * 400000 - (icospherevxMedian);
			double offsetVY = snToCoordsHashtable.get(Integer.parseInt(serial))[1] * 400000 - (icospherevyMedian);
			double offsetVZ = snToCoordsHashtable.get(Integer.parseInt(serial))[2] * 400000 - (icospherevzMedian);

			double zScale = 1;
			String outputObj = "";
			outputObj = outputObj + "# OBJ File\nmtllib " + mtlFile.getName() + "\ng " + outputTag + "\n";
			for (int i = 0; i < outputVXs.size(); i++) {
				outputObj = outputObj + "v " + (outputVXs.get(i) + offsetVX) + " " + (outputVYs.get(i) + offsetVY) + " "
						+ (((outputVZs.get(i)) * zScale) + offsetVZ) + "\n";
			}

			String leadCellName = "";
			if (outputTag.split("_")[0].contains("-")) {
				leadCellName = outputTag.split("-")[0];
			} else {
				leadCellName = outputTag.split("_")[0];
			}
			outputObj = outputObj + "usemtl mat_" + nameKeyclusterTable.get(leadCellName);

			outputObj = outputObj + "\ns " + outputFacets[0] + "\n";
			for (int i = 0; i < outputFXs.size(); i++) {
				outputObj = outputObj + "f " + outputFXs.get(i).intValue() + " " + outputFYs.get(i).intValue() + " "
						+ outputFZs.get(i).intValue() + "\n";
			}
			outputObj = outputObj + outputFacets[outputFacets.length - 1] + "\n";
			IJ.saveString(outputObj, outputPath);

		}
//		univ.addCustomMesh(bigmesh,"multi");

	}

	public void plotManikFmtPhateObjsToCoordsIcospheres() {

		IJ.wait(1);
		String icosphereObj = IJ
				.openUrlAsString(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.obj").toString());
		File mtlFile = new File(MQTVSSceneLoader64.class.getResource("docs/icosphereOut_0000.mtl").toString());

		IJ.log(icosphereObj);
		String[] icosphereSections = icosphereObj.split("(\ng |\ns )");
		IJ.log("" + icosphereSections.length);
		String[] icosphereVertices = icosphereSections[1].split("\n");
		String[] icosphereFacets = icosphereSections[2].split("\n");
		ArrayList<Double> icosphereVXs = new ArrayList<Double>();
		ArrayList<Double> icosphereVYs = new ArrayList<Double>();
		ArrayList<Double> icosphereVZs = new ArrayList<Double>();
		ArrayList<Double> icosphereFXs = new ArrayList<Double>();
		ArrayList<Double> icosphereFYs = new ArrayList<Double>();
		ArrayList<Double> icosphereFZs = new ArrayList<Double>();

		for (int x = 0; x < icosphereVertices.length; x++) {
			if (icosphereVertices[x].startsWith("v ")) {
				icosphereVXs.add(Double.parseDouble(icosphereVertices[x].split(" ")[1]));
				icosphereVYs.add(Double.parseDouble(icosphereVertices[x].split(" ")[2]));
				icosphereVZs.add(Double.parseDouble(icosphereVertices[x].split(" ")[3]));
			}
		}
		for (int x = 0; x < icosphereFacets.length; x++) {
			if (icosphereFacets[x].startsWith("f ")) {
				icosphereFXs.add(Double.parseDouble(icosphereFacets[x].split(" ")[1]));
				icosphereFYs.add(Double.parseDouble(icosphereFacets[x].split(" ")[2]));
				icosphereFZs.add(Double.parseDouble(icosphereFacets[x].split(" ")[3]));
			}
		}

		Object[] icospherevxs = (icosphereVXs.toArray());
		Arrays.sort(icospherevxs);
		double icospherevxMedian = (double) icospherevxs[icospherevxs.length / 2];
		Object[] icospherevys = (icosphereVYs.toArray());
		Arrays.sort(icospherevys);
		double icospherevyMedian = (double) icospherevys[icospherevys.length / 2];
		Object[] icospherevzs = (icosphereVZs.toArray());
		Arrays.sort(icospherevzs);
		double icospherevzMedian = (double) icospherevzs[icospherevzs.length / 2];
		double icospherevzMin = (double) icospherevzs[0];
		double icospherevzMax = (double) icospherevzs[icospherevzs.length - 1];
		Object[] icospherefxs = (icosphereFXs.toArray());
		Arrays.sort(icospherefxs);
		double icospherefxMedian = (double) icospherefxs[icospherefxs.length / 2];
		Object[] icospherefys = (icosphereFYs.toArray());
		Arrays.sort(icospherefys);
		double icospherefyMedian = (double) icospherefys[icospherefys.length / 2];
		Object[] icospherefzs = (icosphereFZs.toArray());
		Arrays.sort(icospherefzs);
		double icospherefzMedian = (double) icospherefzs[icospherefzs.length / 2];

		String inputPath = IJ.getFilePath("Select csv file with PHATE data");
		String condensationSNpath = IJ.getFilePath("Select csv file with condensation cluster data");
		String mtlPath = IJ.getFilePath("Select mtl file with color rules");
		File inputFile = new File(inputPath);
		File conSNFile = new File(condensationSNpath);
		mtlFile = new File(mtlPath);
		String outputDir = inputFile.getParent() + File.separator + inputFile.getName().replace(".csv", "")
				+ File.separator;
		new File(outputDir).mkdirs();
		IJ.saveString(IJ.openAsString(mtlPath), outputDir + mtlFile.getName());
		String inputPhateData = IJ.openAsString(inputPath);
		String[] inputPhateList = inputPhateData.split("\n");
		String conSNData = IJ.openAsString(condensationSNpath);
		String[] conSNList = conSNData.split("\n");
		String[] cellHeaders = null;
		int previousMaxSN = -1;
		int nextMaxSN = 0;
		int iterationOfSix = 0;
		int iterationOfFour = 0;
		Hashtable<Integer, String> serialToRosterStringHashtable = new Hashtable<Integer, String>();
		Hashtable<Integer, ArrayList<String>> serialToRosterArrayHashtable = new Hashtable<Integer, ArrayList<String>>();

		Hashtable<String, Integer[][]> nameToRanksAndSNsHashtable = new Hashtable<String, Integer[][]>();

		for (int iteration = 0; iteration < conSNList.length; iteration++) {
			String[] csnChunks = conSNList[iteration].split(",");
			String[] clusterNumberings = csnChunks;
			int[] clusterNumbers = new int[clusterNumberings.length];
			if (iteration == 0) {
				cellHeaders = csnChunks;
			} else {
				int maxGroupNum = 0;
////			NEW WAY FOR CSV DIRECTLY CONVERTED ALEX -> MANIK FORMAT....
				for (int s = 0; s < clusterNumberings.length; s++) {
					clusterNumbers[s] = Integer.parseInt(clusterNumberings[s]);
				}
				ArrayList<Integer> clusterNumbersListToCrunch = new ArrayList<Integer>();
				for (int q = 0; q < clusterNumbers.length; q++) {
					if (!clusterNumbersListToCrunch.contains(clusterNumbers[q])) {
						clusterNumbersListToCrunch.add(clusterNumbers[q]);
					}
				}
				Hashtable<Integer, Integer> clusterCrunchHT = new Hashtable<Integer, Integer>();
				for (int c = 0; c < clusterNumbersListToCrunch.size(); c++) {
					clusterCrunchHT.put(clusterNumbersListToCrunch.get(c), c + 1);
				}
////
				for (int cell = 0; cell < cellHeaders.length; cell++) {
					if (nameToRanksAndSNsHashtable.get(cellHeaders[cell]) == null) {
						nameToRanksAndSNsHashtable.put(cellHeaders[cell], new Integer[2][conSNList.length]);
					}

////					OLD WAY FOR CSV DIRECTLY FROM ALEX....
//					int sn =0;
//					if (iteration==0){
//						sn = previousMaxSN+cell;
//					}else{
//						sn = previousMaxSN+Integer.parseInt(csnChunks[cell]);
//					}
////

////				NEW WAY FOR CSV DIRECTLY CONVERTED ALEX -> MANIK FORMAT....					
					int sn = 0;
					if (iteration == 0) {
						sn = previousMaxSN + cell;
					} else {
						sn = previousMaxSN + clusterCrunchHT.get(Integer.parseInt(csnChunks[cell]));
					}
////
					nameToRanksAndSNsHashtable.get(cellHeaders[cell])[0][iteration] = Integer.parseInt(csnChunks[cell]);
					nameToRanksAndSNsHashtable.get(cellHeaders[cell])[1][iteration] = sn;

					IJ.log(cellHeaders[cell] + " " + iteration + " " + csnChunks[cell] + " " + sn);
					if (serialToRosterStringHashtable.get(sn) == null) {
						serialToRosterStringHashtable.put(sn, cellHeaders[cell]);
						serialToRosterArrayHashtable.put(sn, new ArrayList<String>());
						serialToRosterArrayHashtable.get(sn).add(cellHeaders[cell]);
					} else {
						serialToRosterStringHashtable.put(sn,
								serialToRosterStringHashtable.get(sn) + "_" + cellHeaders[cell]);
						serialToRosterArrayHashtable.get(sn).add(cellHeaders[cell]);
					}
					if (sn > nextMaxSN) {
						nextMaxSN = sn;
					}
					if (iteration > 0 && clusterCrunchHT.get(Integer.parseInt(csnChunks[cell])) > maxGroupNum) {
						maxGroupNum = clusterCrunchHT.get(Integer.parseInt(csnChunks[cell]));
					}
				}
				if (maxGroupNum == 6) {
					iterationOfSix = iteration;
				}
				if (maxGroupNum == 4) {
					iterationOfFour = iteration;
				}

				previousMaxSN = nextMaxSN;
			}
		}

//		String cluster_assignments_rebuiltAGtoMKfmt ="";
//		for (int iter=0; iter<conSNList.length; iter++) {
//			if (iter>0) {							
//				
////				Manually in Excel:
////
////
////					Paste copy of original Alex line 12 or greater in a place separate from rest of sheet.  Bring tally cells along.
////					Paste copy of original Alex line 1  (columns with 1-178) below it.   Bring tally cells along.
////
////					Read upper row from left, looking for interruptions to the continuous upward number flow.  
////
////					At each deviant cell, record the substituted value and scan (usually backward) until you find another instance of the same number.
////					Read below the second instance and record the "normal" position that it corresponds to.  Return to the original deviation spot, and enter that number into the cell below the deviation.
////
////					Repeat the process, moving to the right.  Pay special attention to omit the correctly ordered cells from substitution:
////					After correct 13 followed by eg two deviations, find 14.  Do not substitute for these original in sequence instances of each number.  For any earlier or later deviant entries of 14, do the swap process.
////
////					Need to enact this with arraylists...  Actually, arrays should work!!!  Just need to create row arrays.
//
//				int[] rowOneArray = new int[cellHeaders.length];
//				int[] rowNArray  = new int[cellHeaders.length];
//				for (int colIndex=0; colIndex < cellHeaders.length; colIndex++) {
//					rowOneArray[colIndex] = nameToRanksAndSNsHashtable.get(cellHeaders[colIndex])[0][1];
//					rowNArray[colIndex] = nameToRanksAndSNsHashtable.get(cellHeaders[colIndex])[0][iter];
//				}				
//				
//				int targetValue = -1;
//				int deviantIndex = -1;
//				int deviantValue = -1;
//				ArrayList<Integer> deviantValuesProcessed = new ArrayList<Integer>();
//				for (int countingIndex=0; countingIndex < cellHeaders.length-1; countingIndex++) {
//					if (targetValue<0) {
//						targetValue = rowNArray[countingIndex]+1;
//					}
//					if (targetValue>177) {
//						IJ.wait(1);
//					}
//					if (rowNArray[countingIndex+1] != targetValue) {
//						deviantValue = rowNArray[countingIndex+1];
//						deviantIndex = countingIndex+1;
//					} else {
//						targetValue = -1;  //push on to next target
//						continue;
//					}
//					if (!deviantValuesProcessed.contains(deviantValue)) {
//						deviantValuesProcessed.add(deviantValue);
//						ArrayList<Integer> deviantHitIndexes = new ArrayList<Integer>();
//						for (int scanningIndex=0; scanningIndex < cellHeaders.length; scanningIndex++) {
//							if (rowNArray[scanningIndex] == deviantValue) {
//								deviantHitIndexes.add(scanningIndex);
//							}
//						}
//						int minDevHitIndex = cellHeaders.length;
//						for (int dhi:deviantHitIndexes) {
//							if (dhi < minDevHitIndex)				{
//								minDevHitIndex = dhi;
//							}
//						}
//						for(int fixingIndex:deviantHitIndexes) {
//							rowOneArray[fixingIndex] = minDevHitIndex+1;
//						}
//					} else {
//						deviantIndex = -1;  //push on to next deviant
//						deviantValue = -1;
//						continue;
//					}
//
//					
//				}
//				IJ.wait(1);
//
//				for (int colIndex=0; colIndex < cellHeaders.length; colIndex++) {
//					cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt +(colIndex>0?",":"")+  rowOneArray[colIndex];
//				}
//				cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt + "\n";
//
//			} else {
//				for (int colIndex=0; colIndex < cellHeaders.length; colIndex++) {
//					cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt +(colIndex>0?",":"") +cellHeaders[colIndex];
//				}
//				cluster_assignments_rebuiltAGtoMKfmt = cluster_assignments_rebuiltAGtoMKfmt + "\n";
//			}
//		}
//		IJ.saveString(cluster_assignments_rebuiltAGtoMKfmt, condensationSNpath.replace(".csv", "")+"_rebuiltAGtoMKfmt.csv");

		Hashtable<String, Integer> clusterColorTable = new Hashtable<String, Integer>();

		if (inputPath.toLowerCase().contains("n2u")) {
			String[] csnSixChunks = conSNList[iterationOfSix].split(",");
			for (int cell = 0; cell < cellHeaders.length; cell++) {
				String[] clusterNumberings = csnSixChunks;
				int[] clusterNumbers = new int[clusterNumberings.length];

				for (int s = 0; s < clusterNumberings.length; s++) {
					clusterNumbers[s] = Integer.parseInt(clusterNumberings[s]);
				}
				ArrayList<Integer> clusterNumbersListToCrunch = new ArrayList<Integer>();
				for (int q = 0; q < clusterNumbers.length; q++) {
					if (!clusterNumbersListToCrunch.contains(clusterNumbers[q])) {
						clusterNumbersListToCrunch.add(clusterNumbers[q]);
					}
				}
				Hashtable<Integer, Integer> clusterCrunchHT = new Hashtable<Integer, Integer>();
				for (int c = 0; c < clusterNumbersListToCrunch.size(); c++) {
					clusterCrunchHT.put(clusterNumbersListToCrunch.get(c), c + 1);
				}

				clusterColorTable.put(cellHeaders[cell], clusterCrunchHT.get(Integer.parseInt(csnSixChunks[cell])));
			}
		} else if (inputPath.toLowerCase().contains("jsh")) {
			String[] csnFourChunks = conSNList[iterationOfSix].split(",");
			for (int cell = 0; cell < cellHeaders.length; cell++) {
				String[] clusterNumberings = csnFourChunks;
				int[] clusterNumbers = new int[clusterNumberings.length];

				for (int s = 0; s < clusterNumberings.length; s++) {
					clusterNumbers[s] = Integer.parseInt(clusterNumberings[s]);
				}
				ArrayList<Integer> clusterNumbersListToCrunch = new ArrayList<Integer>();
				for (int q = 0; q < clusterNumbers.length; q++) {
					if (!clusterNumbersListToCrunch.contains(clusterNumbers[q])) {
						clusterNumbersListToCrunch.add(clusterNumbers[q]);
					}
				}
				Hashtable<Integer, Integer> clusterCrunchHT = new Hashtable<Integer, Integer>();
				for (int c = 0; c < clusterNumbersListToCrunch.size(); c++) {
					clusterCrunchHT.put(clusterNumbersListToCrunch.get(c), c + 1);
				}

				clusterColorTable.put(cellHeaders[cell], clusterCrunchHT.get(Integer.parseInt(csnFourChunks[cell])));
			}
		}

		for (String phateLine : inputPhateList) {
			if (phateLine.startsWith("serialNumber") || phateLine.startsWith(",0,1,2"))
				continue;
			String[] phateLineChunks = phateLine.split(",");
			String serial = phateLineChunks[0];

//			String outputTag = phateLineChunks[5]+"-"+serial;
			String outputTag = serialToRosterStringHashtable.get(Integer.parseInt(serial));
			Integer[] groups = nameToRanksAndSNsHashtable.get(outputTag.split("[_-]")[0])[0];
			Integer[] SNs = nameToRanksAndSNsHashtable.get(outputTag.split("[_-]")[0])[1];
			int itr = -1;
			int grp = -1;
			for (int s = 1; s < SNs.length; s++) {
				if (SNs[s] == Integer.parseInt(serial)) {
					itr = s;
					grp = groups[s];
				}
			}
			outputTag = outputTag + "-i" + itr + "-g" + grp + "-s" + serial;

			String outputPath = outputDir + inputFile.getName() + ".i" + itr + ".g" + grp + ".s" + serial + "."
					+ outputTag.split("[_-]")[0] + ".obj";
			String[] outputSections = icosphereSections;
			String[] outputVertices = icosphereVertices;
			String[] outputFacets = icosphereFacets;
			ArrayList<Double> outputVXs = icosphereVXs;
			ArrayList<Double> outputVYs = icosphereVYs;
			ArrayList<Double> outputVZs = icosphereVZs;
			ArrayList<Double> outputFXs = icosphereFXs;
			ArrayList<Double> outputFYs = icosphereFYs;
			ArrayList<Double> outputFZs = icosphereFZs;

			double offsetVX = Double.parseDouble(phateLineChunks[1]) * 500000 - (icospherevxMedian);
			double offsetVY = Double.parseDouble(phateLineChunks[2]) * 500000 - (icospherevyMedian);
			double offsetVZ = Double.parseDouble(phateLineChunks[3]) * 500000 - (icospherevzMedian);
			double zScale = 1;
			String outputObj = "";
			outputObj = outputObj + "# OBJ File\nmtllib " + mtlFile.getName() + "\ng " + outputTag + "\n";
			for (int i = 0; i < outputVXs.size(); i++) {
				outputObj = outputObj + "v " + (outputVXs.get(i) + offsetVX) + " " + (outputVYs.get(i) + offsetVY) + " "
						+ (((outputVZs.get(i)) * zScale) + offsetVZ) + "\n";
			}
//			outputObj = outputObj + "usemtl mat_"+ phateLineChunks[4] ;
			String leadCellName = "";
			if (outputTag.split("_")[0].contains("-")) {
				leadCellName = outputTag.split("-")[0];
			} else {
				leadCellName = outputTag.split("_")[0];
			}
			outputObj = outputObj + "usemtl mat_" + clusterColorTable.get(leadCellName);

			outputObj = outputObj + "\ns " + outputFacets[0] + "\n";
			for (int i = 0; i < outputFXs.size(); i++) {
				outputObj = outputObj + "f " + outputFXs.get(i).intValue() + " " + outputFYs.get(i).intValue() + " "
						+ outputFZs.get(i).intValue() + "\n";
			}
			outputObj = outputObj + outputFacets[outputFacets.length - 1] + "\n";
			IJ.saveString(outputObj, outputPath);

		}
//		univ.addCustomMesh(bigmesh,"multi");

	}

	void fixdamnJSHs() {
		String dir = IJ.getDirectory("");
		for (String name : new File(dir).list()) {
			IJ.saveString(IJ.openAsString(dir + name).replaceAll(",(\\d,)", ",JSH00$1")
					.replaceAll(",(\\d\\d,)", ",JSH0$1").replaceAll(",(\\d\\d\\d,)", ",JSH$1"), dir + name);
		}
	}

	void fixdamnRedundantNames() {
//		String input = 	IJ.getFilePath("SpazzyFile?");
		String inputDir = IJ.getDirectory("SpazzyFileFolder?") + File.separator;
		File inputDirFile = new File(inputDir);
		String[] inputDirFileList = inputDirFile.list();
		for (String input : inputDirFileList) {
			File inputFile = new File(input);
			// String inputDir = inputFile.getParent() +"/";
			String inputName = inputFile.getName();
			String inputRoot = inputName.replaceAll("(.*)(\\....)", "$1");

			IJ.log(input);
			IJ.log(inputRoot);

			String lrss = longestStutteredSubstring(inputRoot);
			IJ.log(lrss);
			String inputRootFixed = inputRoot.replace(lrss + lrss, lrss).replace("SketchVolumeViewer_", "");
			IJ.log(inputRootFixed);
			IJ.log(inputDir + inputRootFixed);
			IJ.saveString(IJ.openAsString(inputDir + input).replace(inputRoot, inputRootFixed),
					inputDir + inputRootFixed + ".obj");
		}
	}

	void scanForRedundantContemporaneousNames() {
		Roi[] fullROIs = getFullRoisAsArray();
		int maxT = 0;
		int minT = Integer.MAX_VALUE;
		int maxZ = 0;
		int minZ = Integer.MAX_VALUE;
		int maxC = 0;
		int minC = Integer.MAX_VALUE;
		for (Roi roi : fullROIs) {
			if (maxT < roi.getTPosition()) {
				maxT = roi.getTPosition();
			}
			if (minT > roi.getTPosition()) {
				minT = roi.getTPosition();
			}
			if (maxZ < roi.getZPosition()) {
				maxZ = roi.getZPosition();
			}
			if (minZ > roi.getZPosition()) {
				minZ = roi.getZPosition();
			}
			if (maxC < roi.getZPosition()) {
				maxC = roi.getZPosition();
			}
			if (minC > roi.getCPosition()) {
				minC = roi.getCPosition();
			}
		}
		ArrayList<Roi> redundantContempROIs = new ArrayList<Roi>();
		ArrayList<Roi> unresolvedNameROIs = new ArrayList<Roi>();
		for (int t = minT; t <= maxT; t++) {
			ArrayList<Roi> contempROIs = new ArrayList<Roi>();
			for (int z = minZ; z <= maxZ; z++) {
				for (int c = minC; c <= maxC; c++) {
					if (getROIsByNumbers().get("" + c + "_" + z + "_" + t) != null) {
						contempROIs.addAll(getROIsByNumbers().get("" + c + "_" + z + "_" + t));
					}
				}
			}
			IJ.log("t" + IJ.pad(t, 4) + "        " + contempROIs.size());
			for (Roi qRoi : contempROIs) {
				if (qRoi.getName().matches(".*(m|n|g|h).*")) {
					unresolvedNameROIs.add(qRoi);
				}
				for (Roi uRoi : contempROIs) {
					if ((qRoi != uRoi)) {
						String qStart = qRoi.getName().split(" ")[0];
						String uStart = uRoi.getName().split(" ")[0];
						if ((qRoi.getName().startsWith(uStart)) || uRoi.getName().startsWith(qStart)) {
							if (!redundantContempROIs.contains(qRoi))
								redundantContempROIs.add(qRoi);
							if (!redundantContempROIs.contains(uRoi))
								redundantContempROIs.add(uRoi);
						}
					}
				}
			}
		}
		for (Roi undoneRoi : unresolvedNameROIs) {
			this.setRoiFillColor(undoneRoi, Colors.decode(("#33FF8800"), Color.orange));
		}
		for (Roi badRoi : redundantContempROIs) {
			this.setRoiFillColor(badRoi, Colors.decode("#33FFFF00", Color.YELLOW));
		}
		list.repaint();
	}

	public class ModCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			Color bg = Color.gray;
			if (rois != null && rois.get(value) != null) {
				bg = rois.get(value).getFillColor();
			}
			setBackground(isSelected ? Color.black : bg);
			setForeground(isSelected ? Color.white : Color.BLACK);
			setOpaque(true); // otherwise, it's transparent
			return this;
		}

	}

	String longestRepeatedSubstring(String str) {
		int n = str.length();
		int LCSRe[][] = new int[n + 1][n + 1];

		String res = ""; // To store result
		int res_length = 0; // To store length of result

		// building table in bottom-up manner
		int i, index = 0;
		for (i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				// (j-i) > LCSRe[i-1][j-1] to remove
				// overlapping
				if (str.charAt(i - 1) == str.charAt(j - 1) && LCSRe[i - 1][j - 1] < (j - i)) {
					LCSRe[i][j] = LCSRe[i - 1][j - 1] + 1;

					// updating maximum length of the
					// substring and updating the finishing
					// index of the suffix
					if (LCSRe[i][j] > res_length) {
						res_length = LCSRe[i][j];
						index = Math.max(i, index);
					}
				} else {
					LCSRe[i][j] = 0;
				}
			}
		}

		// If we have non-empty result, then insert all
		// characters from first character to last
		// character of String
		if (res_length > 0) {
			for (i = index - res_length + 1; i <= index; i++) {
				res += str.charAt(i - 1);
			}
		}

		return res;
	}

	String longestStutteredSubstring(String str) {
		int n = str.length();
		int LCSRe[][] = new int[n + 1][n + 1];

		String res = ""; // To store result
		int res_length = 0; // To store length of result

		// building table in bottom-up manner
		int i, index = 0;
		for (i = 1; i <= n; i++) {
			for (int j = i + 1; j <= n; j++) {
				// (j-i) > LCSRe[i-1][j-1] to remove
				// overlapping
				if (str.charAt(i - 1) == str.charAt(j - 1) && LCSRe[i - 1][j - 1] < (j - i)) {
					LCSRe[i][j] = LCSRe[i - 1][j - 1] + 1;

					// updating maximum length of the
					// substring and updating the finishing
					// index of the suffix
					if (LCSRe[i][j] > res_length) {
						res_length = LCSRe[i][j];
						index = Math.max(i, index);
					}
				} else {
					LCSRe[i][j] = 0;
				}
			}
		}

		// If we have non-empty result, then insert all
		// characters from first character to last
		// character of String
		if (res_length > 0) {
			for (i = index - res_length + 1; i <= index; i++) {
				res += str.charAt(i - 1);
			}
		}

		if (str.contains(res + res)) {

			return res;

		} else {
			return "";
		}

	}

	public Hashtable<String, ArrayList<Roi>> getRoisByNumbers() {
		return roisByNumbers;
	}

	public void setRoisByNumbers(Hashtable<String, ArrayList<Roi>> roisByNumbers) {
		this.roisByNumbers = roisByNumbers;
	}

	public Hashtable<String, ArrayList<Roi>> getRoisByRootName() {
		return roisByRootName;
	}

	public void setRoisByRootName(Hashtable<String, ArrayList<Roi>> roisByRootName) {
		this.roisByRootName = roisByRootName;
	}

	public void setRoiFillColor(Roi roi, Color color) {
		setRoiFillColor(roi, color, true);
	}

	public void setRoiFillColor(Roi roi, Color color, boolean reNameInListNow) {
		roi.setFillColor(color);
		if (roi.getName() != null && roi.getName().contains(" \"_")) {
			String roiColorChunk = roi.getName().split("_")[1];
			String newColorName = roiColorChunk.startsWith("#")
					? roi.getName().replace(roiColorChunk, Colors.colorToHexString(color))
					: roi.getName().replace(" \"_", Colors.colorToHexString(color));

			if (reNameInListNow && listModel.indexOf(roi.getName()) != -1)
				if (!newColorName.equals(roi.getName()))
					rename(newColorName, new int[] { listModel.indexOf(roi.getName()) }, false);
		}
	}

	public Roi flipRoiWithImage(Roi roi, boolean vertical) {
		ShapeRoi sr = new ShapeRoi(roi);
		AffineTransform at = AffineTransform.getScaleInstance(vertical?1:-1, vertical?-1:1);
		at.translate(roi.getBounds().x + (vertical?0:-imp.getWidth()),roi.getBounds().y + (vertical?-imp.getHeight():0));
		Shape flippedShape = at.createTransformedShape(sr.getShape());
		ShapeRoi flippedRoi = new ShapeRoi(flippedShape);

		flippedRoi.copyAttributes(roi);
		flippedRoi.setPosition(roi.getCPosition(), roi.getZPosition(), roi.getTPosition());
		return flippedRoi;
	}
	
	
	
	@Override
	public void changedUpdate(DocumentEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertUpdate(DocumentEvent e) {
		if (e.getDocument() == textFindingField.getDocument()) {
			String searchString = textFindingField.getText().toLowerCase();
			for (int i = 0; i < listModel.getSize(); i++) {
				String hitCandidate = listModel.get(i).toLowerCase();
				if (hitCandidate.contains(searchString)) {
					list.ensureIndexIsVisible(i);
					break;
				}
			}
		}

	}

	@Override
	public void removeUpdate(DocumentEvent e) {
		if (e.getDocument() == textFindingField.getDocument()) {
			String searchString = textFindingField.getText().toLowerCase();
			for (int i = 0; i < listModel.getSize(); i++) {
				String hitCandidate = listModel.get(i).toLowerCase();
				if (hitCandidate.contains(searchString)) {
					list.ensureIndexIsVisible(i);
					break;
				}
			}
		}

	}

	class HintTextField extends JTextField implements FocusListener {

		private final String hint;
		private boolean showingHint;

		public HintTextField(final String hint) {
			super(hint);
			this.hint = hint;
			this.showingHint = true;
			super.addFocusListener(this);
		}

		@Override
		public void focusGained(FocusEvent e) {
			if (this.getText().isEmpty()) {
				super.setText("");
				showingHint = false;
			}
		}

		@Override
		public void focusLost(FocusEvent e) {
			if (this.getText().isEmpty()) {
				super.setText(hint);
				showingHint = true;
			}
		}

		@Override
		public String getText() {
			return showingHint ? "" : super.getText();
		}
		

		@Override		
		public void setText(String textToSet) {
			showingHint = false;
			super.setText(textToSet);
		}

	}

}
