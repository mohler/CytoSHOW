package ij3d.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
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
import customnode.CustomMeshNode;
import customnode.CustomMultiMesh;
import customnode.Sphere;
import customnode.WavefrontExporter;
import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.filter.*;
import ij.plugin.frame.Channels;
import ij.plugin.frame.ColorLegend;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.Recorder;
import ij.plugin.Colors;
import ij.plugin.DragAndDrop;
import ij.plugin.Orthogonal_Views;
import ij.plugin.RGBStackMerge;
import ij.plugin.Selection;
import ij.plugin.StackReverser;
import ij.util.*;
import ij.macro.*;
import ij.measure.*;
import ij3d.Content;
import ij3d.ContentInstant;
import ij3d.DefaultUniverse;
import ij3d.Image3DUniverse;
import ij3d.ImageJ3DViewer;
import ij3d.ImageWindow3D;
import ij3d.gui.Content3DManager.ModCellRenderer;
import javafx.scene.control.Cell;
import javafx.scene.control.Tooltip;

/** This plugin implements the Analyze/Tools/Content3D Manager command. */
public class Content3DManager extends PlugInFrame implements ActionListener, ItemListener, MouseListener, MouseWheelListener,
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
	private Hashtable<String, ContentInstant> contentInstants = new Hashtable<String, ContentInstant>();
	private Hashtable<String, ArrayList<ContentInstant>> contentInstantsByNumbers = new Hashtable<String, ArrayList<ContentInstant>>();
	private Hashtable<String, ArrayList<ContentInstant>> contentInstantsByRootName = new Hashtable<String, ArrayList<ContentInstant>>();

	public Hashtable<String, ArrayList<ContentInstant>> getROIsByName() {
		return getContentInstantsByRootName();
	}

	public void setROIsByName(Hashtable<String, ArrayList<ContentInstant>> contentInstantsByName) {
		this.setContentInstantsByRootName(contentInstantsByName);
	}

	private ContentInstant contentInstantCopy;
	private boolean canceled;
	private boolean macro;
	private boolean ignoreInterrupts;
	private JPopupMenu pm;
	private JButton moreButton, colorButton;
	private JCheckBox addContentInstantSpanCCheckbox = new JCheckBox("Span C", false);
	private JCheckBox addContentInstantSpanZCheckbox = new JCheckBox("Span Z", false);
	private JCheckBox addContentInstantSpanTCheckbox = new JCheckBox("Span T", false);
	private JCheckBox showAllCheckbox = new JCheckBox("Show", true);
	private JCheckBox showOwnROIsCheckbox = new JCheckBox("Only own notes", false);
	private DefaultUniverse univ;
	private ImagePlus imp;

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
	private boolean addContentInstantSpanC;
	private boolean addContentInstantSpanZ;
	private boolean addContentInstantSpanT;
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
	private ContentInstant[] originalContentInstants = null;
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
	private double contentInstantRescaleFactor = 1d;
	private double expansionDistance = 10d;
	private String lastContentInstantOpenPath;
	private int lastSelectedIndex = 0;
	private int toolTipDefaultDismissDelay;
	private int toolTipDefaultInitialDelay;
	private AceTree aceTree;
	private boolean isContentInstantScaleFactorSet;
	private JScrollPane scrollPane;
	private int shiftT;
	private int shiftC;

	public Content3DManager() {
		super("Content3D Manager");
		this.univ = (WindowManager.getCurrentWindow() instanceof ImageWindow3D)?
				((ImageWindow3D)WindowManager.getCurrentWindow()).getUniverse():null;

		// //this.setTitle(getTitle()+":"+ imp.getTitle());
		list = new JList<String>();
		list.setCellRenderer(new ModCellRenderer());
		fullList = new JList<String>();
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);
		showWindow(false);
		// WindowManager.addWindow(this);
		// thread = new Thread(this, "Content3D Manager");
		// thread.start();

	}

	public Content3DManager(boolean hideWindow) {
		super("Content3D Manager");
		this.univ = (WindowManager.getCurrentWindow() instanceof ImageWindow3D)?
			((ImageWindow3D)WindowManager.getCurrentWindow()).getUniverse():null;
		// //this.setTitle(getTitle()+":"+ imp.getTitle());
		Prefs.showAllSliceOnly = true;
		list = new JList<String>();
		list.setCellRenderer(new ModCellRenderer());
		fullList = new JList<String>();
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);
		showWindow(!hideWindow);
		// WindowManager.addWindow(this);
		// thread = new Thread(this, "Content3D Manager");
		// thread.start();

	}

	public Content3DManager(ImagePlus imp, boolean hideWindow) {
		super("Content3D Manager");
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
			((Image3DUniverse)univ).setContent3DManager(this);
			showWindow(!hideWindow);
		}
		// WindowManager.addWindow(this);
		// thread = new Thread(this, "TagManagerThread");
		// thread.start();

	}

	public Content3DManager(Image3DUniverse univ, boolean hideWindow) {
		super("Content3D Manager");
		this.univ = univ;
		Prefs.showAllSliceOnly = true;
		list = new JList<String>();
		list.setCellRenderer(new ModCellRenderer());
		fullList = new JList<String>();
		listModel = new DefaultListModel<String>();
		fullListModel = new DefaultListModel<String>();
		list.setModel(listModel);
		fullList.setModel(fullListModel);

		if (univ != null) {
			// //this.setTitle(getTitle()+":"+ imp.getTitle());
			univ.setContent3DManager(this);
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
		addContentInstantSpanCCheckbox.addItemListener(this);
		panel.add(addContentInstantSpanCCheckbox);
		addContentInstantSpanZCheckbox.addItemListener(this);
		panel.add(addContentInstantSpanZCheckbox);
		addContentInstantSpanTCheckbox.addItemListener(this);
		panel.add(addContentInstantSpanTCheckbox);
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
			ImageWindow imgWin = univ.getWindow();
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
		addPopupItem("Zap Duplicate ContentInstants");
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
			((Image3DUniverse) univ).getContent3DManager().getTextSearchField().setText("");
			e.setSource(textFilterField);
		}
		boolean wasVis = this.isVisible();

		if (e.getSource() == textFilterField) {
			this.setVisible(false);


			String thisWasTitle = this.getTitle();
			String searchString = textFilterField.getText();
			boolean isRegex = (searchString.startsWith("??"));
			boolean isLinBackTrace = (searchString.toLowerCase().startsWith("?lbt?"));
			boolean isLinTrace = (searchString.toLowerCase().startsWith("?lt?"));
			listModel.removeAllElements();
			prevSearchString = searchString;
			int count = fullListModel.getSize();
			Dimension dim = list.getSize();
			list.setSize(0, 0);
			textCountLabel.setText("?" + "/" + fullListModel.size());
			univ.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			univ.getWindow().countLabel.repaint();
			// imp.getWindow().tagsButton.setText(""+fullListModel.size());

			univ.getWindow().tagsButton.repaint();
			searching = true;
			long timeLast = 0;
			long timeNow = 0;

			for (int i = 0; i < count; i++) {
				timeNow = System.currentTimeMillis();
				if (searchString.trim().equalsIgnoreCase("") || searchString.trim().equalsIgnoreCase(".*")) {
					listModel.addElement(fullListModel.get(i));
					contentInstants.get(fullListModel.get(i)).setVisible(true);
					// IJ.log(listStrings[i]);
//					if (timeNow > timeLast + 100) {
//						timeLast = timeNow;
//						Graphics g = imp.getCanvas().getGraphics();
//						if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
//							imp.getCanvas().messageRois.remove("Loading Tags");
//
//						Roi messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
//								"   Finding tags that match:\n   " + "Reloading full set" + "..."
//										+ ((Image3DUniverse) univ).getContent3DManager().getListModel().getSize() + "");
//
//						((TextRoi) messageRoi).setCurrentFont(
//								g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width / 16)));
//						messageRoi.setStrokeColor(Color.black);
//						imp.getRoiManager().setRoiFillColor(messageRoi, Colors.decode("#99ffffdd", imp.getCanvas().getDefaultColor()));
//						imp.getCanvas().messageRois.put("Loading Tags", messageRoi);
//						imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
//					}
					continue;
				}

				if (isRegex) {
					if (((String) fullListModel.get(i)).matches(searchString.substring(2))) {
					listModel.addElement(fullListModel.get(i));
					contentInstants.get(fullListModel.get(i)).setVisible(true);
				} else {
					contentInstants.get(fullListModel.get(i)).setVisible(false);
				}				
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
						contentInstants.get(fullListModel.get(i)).setVisible(true);
					} else {
						contentInstants.get(fullListModel.get(i)).setVisible(false);
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
						contentInstants.get(fullListModel.get(i)).setVisible(true);
					} else {
						contentInstants.get(fullListModel.get(i)).setVisible(false);
					}


				} else if (((String) fullListModel.get(i)).toLowerCase().contains(searchString.toLowerCase())) {
					listModel.addElement(fullListModel.get(i));
					contentInstants.get(fullListModel.get(i)).setVisible(true);
				} else {
					contentInstants.get(fullListModel.get(i)).setVisible(false);
				}
//				if (timeNow > timeLast + 100 && !imp.getCanvas().messageRois.containsKey("Finding tags from drop")) {
//					timeLast = timeNow;
//					Graphics g = imp.getCanvas().getGraphics();
//					if (imp.getCanvas().messageRois.containsKey("Finding tags that match"))
//						imp.getCanvas().messageRois.remove("Finding tags that match");
//
//					TextRoi messageRoi = new TextRoi(imp.getCanvas().getSrcRect().x, imp.getCanvas().getSrcRect().y,
//							"   Finding tags that match:\n   \"" + searchString + "\"..."
//									+ ((Image3DUniverse) univ).getContent3DManager().getListModel().getSize() + " found.");
//
//					((TextRoi) messageRoi)
//							.setCurrentFont(g.getFont().deriveFont((float) (imp.getCanvas().getSrcRect().width / 16)));
//					messageRoi.setStrokeColor(Color.black);
//					imp.getRoiManager().setRoiFillColor(messageRoi, Colors.decode("#99ffffdd", imp.getCanvas().getDefaultColor()));
//
//					imp.getCanvas().messageRois.put("Finding tags that match", messageRoi);
//					// imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
//
//				}

			}
//			if (imp.getCanvas().messageRois.containsKey("Finding tags that match"))
//				imp.getCanvas().messageRois.remove("Finding tags that match");
//			if (imp.getCanvas().messageRois.containsKey("Loading Tags"))
//				imp.getCanvas().messageRois.remove("Loading Tags");

			searching = false;
			list.setSize(dim);
			textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
			univ.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			univ.getWindow().countLabel.repaint();

			univ.getWindow().tagsButton.repaint();

			IJ.runMacro("print(\"\\\\Update:\")");

			if (!(univ.getWindow().getTitle().matches(".*[XY]Z +\\d+.*"))) {
				ImageWindow imgWin = univ.getWindow();
				this.setVisible(wasVis);
				if (imgWin != null)
					setLocation(imgWin.getLocationOnScreen().x + imgWin.getWidth() + 5, imgWin.getLocationOnScreen().y);
			}

//			if (this.getDisplayedContentInstantsAsArray( imp.getFrame()).length < 1) {
////				int nearestTagIndex = -1;
////				int closestYet = imp.getNSlices() + imp.getNFrames();
////				ContentInstant[] contentInstants = getShownContentInstantsAsArray();
////				for (int r = 0; r < contentInstants.length; r++) {
////					if (Math.abs(contentInstants[r].getZPosition() - imp.getSlice())
////							+ Math.abs(contentInstants[r].getTimepoint() - imp.getFrame()) < closestYet) {
////						closestYet = Math.abs(contentInstants[r].getZPosition() - imp.getSlice())
////								+ Math.abs(contentInstants[r].getTimepoint() - imp.getFrame());
////						nearestTagIndex = r;
////					}
////					// IJ.log(""+closestYet);
////				}
////				if (nearestTagIndex >= 0) {
////					new ij.macro.MacroRunner("contentInstantManager('select', " + nearestTagIndex + ", " + imp.getID() + ");");
////					select(-1);
////					while (univ.getSelected().getCurrentInstant() == null)
////						IJ.wait(100);
////					imp.killContentInstant();
////				}
//			}

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
				ContentInstant contentInstant = univ.getSelected().getCurrentInstant();
				if (contentInstant == null) {
					return;
				}
				String newName = "";
				newName = contentInstant.getName();
				if (newName == "") {
					if (getSelectedContentInstantsAsArray().length > 0) {
						String selName = getSelectedContentInstantsAsArray()[0].getName();
						newName = promptForName(selName);
					} else {
						newName = promptForName(recentName);
					}
				}
				Color existingColor = null;
				Enumeration<ContentInstant> contentInstantsElements = contentInstants.elements();
				while (contentInstantsElements.hasMoreElements()) {
					ContentInstant nextContentInstant = contentInstantsElements.nextElement();
					String nextName = nextContentInstant.getName();
					if (nextName.startsWith("\"" + newName + " \"")) {
						existingColor = nextContentInstant.getColor().get();
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

				add(contentInstant, shiftKeyDown, altKeyDown, controlKeyDown);

				if (existingColor != null) {
					((Image3DUniverse) univ).getContent3DManager().setContentInstantFillColor(contentInstants.get(getListModel().get(getListModel().getSize() - 1)), existingColor);

				}

//			} else if (command.equals("Update [u]")) {
//				if (imp.getMultiChannelController() != null)
//					imp.getMultiChannelController().updateContentInstantManager();
			} else if (command.equals("Delete"))
				delete(false);
			else if (command.equals("Delete ")) {
				if (list.getSelectedIndices().length == 1)
					delete(false);
			} else if (command.equals("Color")) {
				ContentInstant[] selectedContentInstants = getSelectedContentInstantsAsArray();
				if (selectedContentInstants.length > 0) {
					int fillColorInt = Colors.decode("#00000000", Color.black).getRGB();
					if (selectedContentInstants[0].getColor().get() != null) {
						fillColorInt = selectedContentInstants[0].getColor().get().getRGB();
					}
					String hexInt = Integer.toHexString(fillColorInt);
					int l = hexInt.length();
					for (int c = 0; c < 8 - l; c++)
						hexInt = "0" + hexInt;
					String hexName = "#" + hexInt;
					Color fillColor = JColorChooser.showDialog(this.getFocusOwner(),
							"Pick a color for " + this.getSelectedContentInstantsAsArray()[0].getName() + "...",
							Colors.decode(hexName, Color.cyan));
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

					for (ContentInstant selContentInstant : getSelectedContentInstantsAsArray()) {
						String rootName = selContentInstant.getName().contains("\"")
								? "\"" + selContentInstant.getName().split("\"")[1] + "\""
								: selContentInstant.getName().split("_")[0].trim();
						// rootName = rootName.contains(" ")?rootName.split("[_\\-
						// ]")[0].trim():rootName;
						String[] rootChunks = selContentInstant.getName().split("_");
						String rootFrame = rootChunks[rootChunks.length - 1].replaceAll("[CZT]", "").split("-")[0];
						if (!rootNames_rootFrames.contains(rootName + "_" + rootFrame)) {
							rootNames_rootFrames.add(rootName + "_" + rootFrame);
							rootNames.add(rootName);
						}
					}

					ArrayList<ContentInstant> nameMatchArrayList = new ArrayList<ContentInstant>();
					// ContentInstant[] fullcontentInstants = getFullContentInstantsAsArray();

					for (int n = 0; n < rootNames.size(); n++) {
						String rootName = rootNames.get(n);
						nameMatchArrayList.addAll(getContentInstantsByRootName().get(rootName));
						// int fraa = fullcontentInstants.length;
						// for (int r=0; r < fraa; r++) {
						// String nextName = fullcontentInstants[r].getName();
						// if (nextName.startsWith(rootName)){
						// nameMatchIndexArrayList.add(r);
						// }
						// }

					}
					// int[] nameMatchIndexes = new int[nameMatchIndexArrayList.size()];
					for (ContentInstant nmContentInstant : nameMatchArrayList) {
						// nameMatchIndexes[i] = nameMatchIndexArrayList.get(i);
						// fullcontentInstants[nameMatchIndexes[i]]imp.getRoiManager().setRoiFillColor(Colors.decode(alphaCorrFillColorString,
						// fillColor));
						((Image3DUniverse) univ).getContent3DManager().setContentInstantFillColor(nmContentInstant, Colors.decode(alphaCorrFillColorString, fillColor));

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

			} else if (command.equals("Rename")) {

				ArrayList<String> rootNames_rootFrames = new ArrayList<String>();
				ArrayList<String> rootNames = new ArrayList<String>();

				ContentInstant[] selContentInstants = getSelectedContentInstantsAsArray();
				int[] selIndexes = getSelectedIndexes();
				int selectedTime = selContentInstants[0].getTimepoint();
				String selName = selContentInstants[0].getName();
				String newName = promptForName(selName.split(" ")[0].replace("\"", "").trim());
				if (newName == null)
					return;

				if (selContentInstants.length == 1) {
					if (!propagateRenamesThruLineage) {
						rename(newName, selIndexes, false);
						return;
					}
				} else if (selContentInstants.length > 1 || (selContentInstants.length == 1 && !propagateRenamesThruLineage)) {
					rename(newName, selIndexes, false);
					return;
				} else {
					return;
				}

				Color existingColor = null;
				ContentInstant[] contentInstants = getFullContentInstantsAsArray();
				int fraa = contentInstants.length;
				int r = 0;
				while (existingColor == null) {
					if (r > contentInstants.length - 1) {
						break;
					}

					String nextName = contentInstants[r].getName();
					if (nextName.startsWith("\"" + newName + " \"")) {
						existingColor = contentInstants[r].getColor().get();
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

				for (ContentInstant selContentInstant : selContentInstants) {

					String rootName = selContentInstant.getName().contains("\"") ? ("\"" + selContentInstant.getName().split("\"")[1] + "\"")
							: selContentInstant.getName().split("_")[0];

					String[] rootChunks = selContentInstant.getName().split("_");
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
					ContentInstant[] contentInstants2 = getFullContentInstantsAsArray();
					int fraaa = contentInstants2.length;
					for (int r2 = 0; r2 < fraaa; r2++) {
						String nextName = contentInstants2[r2].getName();
						if (!rootName.replace("\"", "").trim().equals("")) {
							String rootMatch = "\"" + rootName.replace("\"", "").trim()
									+ (propagateRenamesThruLineage ? "[m|n|l|r|a|p|d|v|g|h]*" : "") + " +\".*";
							if (nextName.matches(rootMatch)) {
								if (!propagateRenamesThruLineage && selContentInstants[0] == contentInstants2[r2]) {
									nameMatchIndexArrayList.add(r2);
									nameReplacementArrayList.add(nextName.replaceAll(
											"\"" + rootName.replace("\"", "").trim() + "([m|n|l|r|a|p|d|v|g|h]*) +\".*",
											newName + "$1"));
								} else if (selectedTime < contentInstants2[r2].getTimepoint() || selContentInstants[0] == contentInstants2[r2]) {
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

							((Image3DUniverse) univ).getContent3DManager().setContentInstantFillColor(contentInstants[nameMatchIndexes[i]], existingColor);

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
				
			else if (command.equals("Save..."))
				save();
			else if (command.equals("Save")) {
				select(-1);
				save();

			} else if (command.equals("Adv.")) {
				// if (imp.getMotherImp().getRoiManager().getColorLegend(e.getSource()) != null)
				// imp.getMotherImp().getRoiManager().getColorLegend(e.getSource()).setVisible(true);
				ImageWindow imgWin = univ.getWindow();
				this.setVisible(true);
				if (imgWin != null)
					setLocation(imgWin.getLocationOnScreen().x + imgWin.getWidth() + 5, imgWin.getLocationOnScreen().y);
			} else if (command.equals("Deselect"))
				select(-1);
			else if (command.equals(moreButtonLabel)) {
				Point ploc = panel.getLocation();
				Point bloc = moreButton.getLocation();
				pm.show(this, ploc.x, bloc.y);
				return;

			} else if (command.equals("Sort")) {
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
			} 
//			 else if (command.equals("Copy Selected to Other Images"))
//				copyToOtherRMs();

		}
		// IJ.log("THREAD DONE");
	}


	public void itemStateChanged(ItemEvent e) {
	}

	void add(ContentInstant contentInstant, boolean shiftKeyDown, boolean altKeyDown, boolean controlKeyDown) {
		if (controlKeyDown) {
			addContentInstantSpanC = true;
			addContentInstant(contentInstant);
			addContentInstantSpanC = false;
		} else if (altKeyDown) {
			addContentInstantSpanZ = true;
			addContentInstant(contentInstant);
			addContentInstantSpanZ = false;
		} else if (shiftKeyDown) {
			addContentInstantSpanT = true;
			addContentInstant(contentInstant);
			addContentInstantSpanT = false;
		} else
			addContentInstant(contentInstant);
	}

	/** Adds the specified ROI. */
	public void addContentInstant(ContentInstant contentInstant) {
		addContentInstant(contentInstant, false, contentInstant.getColor().get(), -1, true);
	}

	public boolean addContentInstant(boolean promptForName) {
		return addContentInstant(null, promptForName, null, -1, true);
	}

	public boolean addContentInstant(ContentInstant contentInstant, boolean promptForName, Color color, int lineWidth, boolean addToCurrentUnivPosition) {
		Image3DUniverse univ = (Image3DUniverse) this.univ;
		if (contentInstant == null) {
			if (univ == null)
				return false;
			contentInstant = univ.getSelected().getCurrentInstant();
			if (contentInstant == null) {
				error("The relevant image does not have a selection.");
				return false;
			}
		}
		if (color == null && defaultColor != null)
			color = defaultColor;
		Color fillColor = null;
		if (color != null)
			fillColor = color;
		else if (univ != null)
			fillColor = univ.getSelected().getColor().get();
		else if (contentInstant.getColor() != null)
			fillColor = contentInstant.getColor().get();
		else
			fillColor = defaultColor;
		// IJ.log(""+imp.getContentInstantFillColor());

		int n = listModel.getSize();
		if (n > 0 && !IJ.isMacro() && univ != null && univ.getWindow() != null) {
			// check for duplicate
			String label = (String) listModel.getElementAt(n - 1);
			ContentInstant contentInstant2 = (ContentInstant) contentInstants.get(label);
			if (contentInstant2 != null) {
				if (contentInstant.equals(contentInstant2)
						&& !Interpreter.isBatchMode())
					return false;
			}
		}
		String name = contentInstant.getName();
		if (textFindingField != null) // true/*name == null*/)
//			if (!(textFindingField.getText().isEmpty()  || textFindingField.getText().contains("Name..."))) {
//				contentInstant.setName(textFindingField.getText());
//				recentName = (textFindingField.getText());
//			}
			if (isStandardName(name))
				name = null;
		String label = name != null ? name : getLabel(univ, contentInstant, -1);
		if (promptForName) {
			// label = promptForName(label);
		} else if (true) {
			String altType = null;
			if (univ != null && addToCurrentUnivPosition) {
				if (contentInstant.getName() != null && contentInstant.getName().split("\"").length > 1)
					label = "\"" + contentInstant.getName().split("\"")[1].trim() + " \"" + "_" + univ.getCurrentTimepoint();
				else if (contentInstant.getName() != null)
					label = "\"" + contentInstant.getName() + " \"" + "_"
							+ univ.getCurrentTimepoint();
				else
					label = ((altType != null) ? altType :  "Object") + "_" + imp.getChannel() + "_"
							+ imp.getSlice() + "_" + univ.getCurrentTimepoint();
			} else if (contentInstant.getTimepoint() != 0) {
				if (contentInstant.getName() != null && contentInstant.getName().split("\"").length > 1)
					label = "\"" + contentInstant.getName().split("\"")[1].trim() + " \"" + "_" + contentInstant.getTimepoint();
				else if (contentInstant.getName() != null)
					label = "\"" + contentInstant.getName() + " \"" + "_" + contentInstant.getTimepoint();
				else
					label = ((altType != null) ? altType : "Object") + "_" + contentInstant.getTimepoint();
			} else {
				label = contentInstant.getName();
			}
		}
		label = getUniqueName(label);
		if (label == null)
			return false;
		listModel.addElement(label);
		fullListModel.addElement(label);
		contentInstant.setName(label);
		recentName = (label);
		if (univ != null && addToCurrentUnivPosition /*
													 * && imp.getWindow()!=null && contentInstant.getCPosition()==0 &&
													 * contentInstant.getZPosition()==0 && contentInstant.getTimepoint()==0
													 */ )
			contentInstant.timepoint = univ.getCurrentTimepoint();
		contentInstantCopy = contentInstant;

		if (fillColor != null)
			univ.getContent3DManager().setContentInstantFillColor(contentInstantCopy, fillColor, false);
		contentInstants.put(label, contentInstantCopy);
//		setUpContentInstantsByNameAndNumbers(contentInstantCopy);

		ColorLegend cl = getColorLegend();
		//
		if (contentInstantCopy != null) {
			if (cl != null) {
				Color clColor = cl.getBrainbowColors()
						.get(contentInstantCopy.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
				if (clColor != null) {
					String hexRed = Integer.toHexString(clColor.getRed());
					String hexGreen = Integer.toHexString(clColor.getGreen());
					String hexBlue = Integer.toHexString(clColor.getBlue());
					univ.getContent3DManager().setContentInstantFillColor(contentInstantCopy,
							Colors.decode("#ff" + (hexRed.length() == 1 ? "0" : "") + hexRed
									+ (hexGreen.length() == 1 ? "0" : "") + hexGreen
									+ (hexBlue.length() == 1 ? "0" : "") + hexBlue, Color.white));
				}
			}
		}

		if (univ != null && univ.getWindow() != null) {

			contentInstantCopy.timepoint = univ.getCurrentTimepoint();
			// IJ.log("addSingleContentInstant" +
			// contentInstantCopy.getCPosition()+contentInstantCopy.getZPosition()+contentInstantCopy.getTimepoint() );
		}

		// if (false)

		textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
		if (univ != null && univ.getWindow() != null) {
			univ.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			univ.getWindow().countLabel.repaint();
			// univ.getWindow().tagsButton.setText(""+fullListModel.size());

			univ.getWindow().tagsButton.repaint();
		}
		return true;
	}

	public void setUpContentInstantsByNameAndNumbers(ContentInstant contentInstant) {
		ArrayList<ContentInstant> timeContentInstants = getContentInstantsByNumbers().get(contentInstant.getTimepoint());
		if (timeContentInstants == null) {
			getContentInstantsByNumbers().put(""+contentInstant.getTimepoint(), new ArrayList<ContentInstant>());
			timeContentInstants = getContentInstantsByNumbers().get((contentInstant.getTimepoint()));
		}
		//						timeContentInstants.add(contentInstant);
		if (contentInstant.getName().contains("~")){
			timeContentInstants.add(0,contentInstant);
		}else {
			timeContentInstants.add(contentInstant);
		}

		String rootName = contentInstant.getName().contains("\"") ? "\"" + contentInstant.getName().split("\"")[1] + "\""
				: "\"" + contentInstant.getName().split("_")[0].trim() + " \"";

		ArrayList<ContentInstant> rootNameContentInstants = getContentInstantsByRootName().get(rootName);
		if (rootNameContentInstants == null) {
			getContentInstantsByRootName().put(rootName, new ArrayList<ContentInstant>());
			rootNameContentInstants = getContentInstantsByRootName().get(rootName);
		}
		rootNameContentInstants.add(contentInstant);
	}

	void recordAdd(Color color, int lineWidth) {
		if (Recorder.scriptMode())
			Recorder.recordCall("rm.addContentInstant(univ.getSelected().getCurrentInstant());");
		else if (color != null && lineWidth == 1)
			Recorder.recordString("contentInstantManager(\"Add\", \"" + getHex(color) + "\");\n");
		else if (lineWidth > 1)
			Recorder.recordString("contentInstantManager(\"Add\", \"" + getHex(color) + "\", " + lineWidth + ");\n");
		else
			Recorder.record("contentInstantManager", "Add");
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
	public void add(Image3DUniverse univ, ContentInstant contentInstant, int n) {
		if (contentInstant == null)
			return;
		String label = getLabel(univ, contentInstant, n);
		if (label == null)
			return;
		listModel.addElement(label);
		fullListModel.addElement(label);
		contentInstant.setName(label);
		recentName = (label);
		contentInstantCopy = (ContentInstant) contentInstant.cloneNode(true);
		contentInstants.put(label, contentInstantCopy);
		setUpContentInstantsByNameAndNumbers(contentInstantCopy);

		textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
		if (univ.getWindow() != null) {
			univ.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			univ.getWindow().countLabel.repaint();
			// univ.getWindow().tagsButton.setText(""+fullListModel.size());

			univ.getWindow().tagsButton.repaint();
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

	String getLabel(Image3DUniverse univ, ContentInstant contentInstant, int n) {
		String label = "";
		if (imp != null && imp.getStackSize() > 1) {
			int frame = contentInstant.getTimepoint();
			label = contentInstant.getName() +"_"+ frame;
		}
		return label;
	}

	public boolean delete(boolean replacing) {
		Image3DUniverse univ = (Image3DUniverse) this.univ;
		int count = listModel.getSize();
		int fullCount = fullListModel.getSize();
		if (count == 0)
			return error("The list is empty.");
		int indexes[] = getSelectedIndexes();
		if (indexes.length == 0 || (replacing && count > 1)) {
			String msg = "Delete all items on the list?";
			if (replacing)
				msg = "Replace items on the list?";
			canceled = false;
			if (!IJ.isMacro() && !macro) {
				YesNoCancelDialog d = new YesNoCancelDialog(this, "Content3D Manager", msg);
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
			univ.removeAllContents();
			contentInstants.clear();
			getContentInstantsByNumbers().clear();
			getContentInstantsByRootName().clear();
			listModel.removeAllElements();
			fullListModel.removeAllElements();
		} else {
			list.setModel(new DefaultListModel<String>());
			for (int i = count - 1; i >= 0; i--) {
				boolean delete = false;
				for (int j = 0; j < indexes.length; j++) {
					if (indexes[j] == i)
						delete = true;
				}
				if (delete) {
					ContentInstant contentInstant = contentInstants.get(listModel.getElementAt(i));
					if (contentInstant != null) {
						String rootName = contentInstant.getName().contains("\"") ? "\"" + contentInstant.getName().split("\"")[1] + "\""
								: contentInstant.getName().split("_")[0].trim();
						int t = contentInstant.getTimepoint();

						if (getContentInstantsByRootName().containsKey(rootName)) {
							getContentInstantsByRootName().get(rootName).remove(contentInstant);
						}

						if (getContentInstantsByNumbers().containsKey(t)) {
							getContentInstantsByNumbers().get(t).remove(contentInstant);
						}
						contentInstants.remove(contentInstant.getName());
					}
					fullListModel.removeElement(listModel.getElementAt(i));
					listModel.remove(i);
					if (univ.contains(contentInstant.getName().replace("_#0", "")))
						univ.removeContent(contentInstant.getName().replace("_#0", ""));
				}
			}
			list.setModel(listModel);
		}

		if (count > 1 && indexes.length == 1 && univ != null)
			univ.removeContent(univ.getSelected().getName());

		textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
		if (univ.getWindow() != null) {
			univ.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
			univ.getWindow().countLabel.repaint();
			// univ.getWindow().tagsButton.setText(""+fullListModel.size());

			univ.getWindow().tagsButton.repaint();
		}
		if (record())
			Recorder.record("contentInstantManager", "Delete");
		return true;
	}

	boolean update(boolean clone) {
		return clone;
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
		// ContentInstant[] selectedContentInstants = this.getSelectedContentInstantsAsArray();
		if (indexes == null)
			indexes = this.getSelectedIndexes();
		// return error("Exactly one item in the list must be selected.");

		list.setModel(new DefaultListModel<String>());

		for (int i = 0; i < indexes.length; i++) {
			String name = (String) listModel.getElementAt(indexes[i]);
			ContentInstant contentInstant = (ContentInstant) contentInstants.get(name);
			if (contentInstant == null)
				continue;
			int t = contentInstant.getTimepoint();
			if (newNames[i] == null) {
				String newestName = null;
				if (name.split("\"").length > 1) {
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
			String numbersKey = ""+t;
			contentInstantsByRootName.get(nameRoot).remove(contentInstant);
			contentInstantsByNumbers.get(numbersKey).remove(contentInstant);
			contentInstants.remove(name);
			String label = name != null ? name : getLabel((Image3DUniverse) univ, contentInstant, -1);
			 if (true) {
					label = contentInstant.getName();
			}
			label = getUniqueName(label);

			contentInstant.setName(label);
			recentName = label;
			contentInstants.put(label, contentInstant);
			setUpContentInstantsByNameAndNumbers(contentInstant);

			ColorLegend cl = getColorLegend();
			//
			if (contentInstant != null) {
				if (cl != null) {
					Color clColor = cl.getBrainbowColors()
							.get(contentInstant.getName().toLowerCase().split("_")[0].split("=")[0].replace("\"", "").trim());
					if (clColor != null) {
						String hexRed = Integer.toHexString(clColor.getRed());
						String hexGreen = Integer.toHexString(clColor.getGreen());
						String hexBlue = Integer.toHexString(clColor.getBlue());
						((Image3DUniverse) univ).getContent3DManager().setContentInstantFillColor(contentInstant,
								Colors.decode("#ff" + (hexRed.length() == 1 ? "0" : "") + hexRed
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
			;
		if (record())
			Recorder.record("contentInstantManager", "Rename", newNames[0]);
		return true;
	}

	String promptForName(String name) {
		String name2 = "";
//		if (textFindingField.getText().isEmpty()  || textFindingField.getText().contains("Name...")) {
		if (true) {
			GenericDialog gd = new GenericDialog("Content3D Manager");
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
		return setSlice;
	}

	boolean restoreWithoutUpdate(int index) {
		return false;
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


	public String getUniqueName(String name) {
		if (name.matches(".*(-\\d+)")){
			//clean up stray suffix strings in some saved contentInstants
			name = name.replaceAll("(.*?)((-\\d+)+)(-\\d+)", "$1$4");
		}
		String name2 = name + (addContentInstantSpanC ? "C" : "") + (addContentInstantSpanZ ? "Z" : "") + (addContentInstantSpanT ? "T" : "");
		String suffix = "";
		while (name2.endsWith("C") || name2.endsWith("Z") || name2.endsWith("T")) {
			suffix = (suffix.contains(name2.substring(name2.length() - 1)) ? "" : name2.substring(name2.length() - 1))
					+ suffix;
			name2 = name2.substring(0, name2.length() - 1);
		}
		ContentInstant contentInstant2 = (ContentInstant) contentInstants.get(name2 + suffix);
		int n = 1;
		while (contentInstant2 != null) {
			contentInstant2 = (ContentInstant) contentInstants.get(name2 + suffix);
			if (contentInstant2 != null) {
				int lastDash = name2.lastIndexOf("-");
				if (lastDash != -1 && name2.length() - lastDash < 5)
					name2 = name2.substring(0, lastDash);
				name2 = name2 + "-" + n;

				n++;
			}
			contentInstant2 = (ContentInstant) contentInstants.get(name2 + suffix);
		}
		return name2 + suffix;
	}

	boolean save() {
		if (listModel.getSize() == 0)
			return error("The selection list is empty.");
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		//		if (indexes.length > 1)
		//			return saveMultiple(indexes, null);
		String name = (String) listModel.getElementAt(indexes[0]);
		Macro.setOptions(null);
		SaveDialog sd = new SaveDialog("Save Selection...", name, "_etc.obj");
		String name2 = sd.getFileName();
		if (name2 == null)
			return false;
		String dir = sd.getDirectory();
		String path = dir + name;
		HashMap<String, CustomMesh> m2w = new HashMap<String, CustomMesh>();
		for (int i = 0; i < indexes.length; i++) {
			name = (String) listModel.getElementAt(indexes[i]);
			ContentInstant contentInstant = (ContentInstant) contentInstants.get(name);
			m2w.put(contentInstant.getName(),((CustomMeshNode)(contentInstant.getContentNode())).getMesh());
		}
		try {
			WavefrontExporter.save(m2w, path);
			return true;
		} catch(IOException e) {
			e.printStackTrace();
			IJ.error(e.getMessage());
			return false;
		}

	}


	void setProperties(Color color, int lineWidth, Color fillColor) {
		boolean showDialog = color == null && lineWidth == -1 && fillColor == null;
		int[] indexes = getSelectedIndexes();
		if (indexes.length == 0)
			indexes = getAllShownIndexes();
		int n = indexes.length;
		if (n == 0)
			return;
		ContentInstant cipContentInstant = null;
		String rpName = null;
		Font font = null;
		int justification = TextRoi.LEFT;
		double opacity = -1;
		String alphaPrefix = "";
		float transparency = 0f;
			if (showDialog) {
			String label = (String) listModel.getElementAt(indexes[0]);
			if (n > 1)
				label = "range: " + (indexes[0] + 1) + "-" + (indexes[n - 1] + 1);
//			else
//				cipContentInstant.setColor(fillColor != null ? new Color3f(fillColor) : new Color3f(Colors.decode("#00000000", Color.black)));
			cipContentInstant = new ContentInstant(label);
			if (n == 1) {
				fillColor = contentInstants.get(label).getColor().get();
				rpName = cipContentInstant.getName();
			}

			ContentInstantProperties ciprops = new ContentInstantProperties("Properties", cipContentInstant);
			if (!ciprops.showDialog())
				return;
			defaultLineWidth = lineWidth;
			fillColor = cipContentInstant.getColor().get();
			transparency = cipContentInstant.getTransparency();
			defaultColor = color;
		}
		ImagePlus imp = this.imp;
		if (n == listModel.getSize() && n > 1 && !IJ.isMacro()) {
			GenericDialog gd = new GenericDialog("Content3D Manager");
			gd.addMessage("Apply changes to all " + n + " selections?");
			gd.showDialog();
			if (gd.wasCanceled())
				return;
		}
		ColorLegend cl = this.getColorLegend();
		if (cl != null) {
//			ArrayList<Integer> hitIndexes = new ArrayList<Integer>();
//			ContentInstant[] targetContentInstants = getSelectedContentInstantsAsArray();
//			if (targetContentInstants.length<1)
//				targetContentInstants = getFullContentInstantsAsArray();
//			for (int i=0; i<n; i++) {
//				String label = (String) listModel.getElementAt(indexes[i]);
//				Color currentBBColor = targetContentInstants[0].getFillColor();
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
			ContentInstant contentInstant = (ContentInstant) contentInstants.get(label);
			// IJ.log("set "+color+" "+lineWidth+" "+fillColor);
			if (color != null)
				contentInstant.setColor(new Color3f(color));
			contentInstant.setTransparency(transparency);
			if (!Colors.colorToHexString(fillColor).toUpperCase().endsWith("F0F0F0"))
					contentInstant.setColor(new Color3f(fillColor));
			if (brainbowColors == null)
				brainbowColors = new Hashtable<String, Color>();
			if (fillColor != null)
				brainbowColors.put(label.split(" ")[0].replace("\"", "").toLowerCase(), new Color(fillColor.getRGB()));
		}
//		String[] newNames = new String[indexes.length];
//		for(int nn=0;nn<newNames.length;nn++) {
//			String label2 = (String) listModel.getElementAt(indexes[nn]);
//			newNames[nn]=label2;
//			String dummyString = label2.replaceAll("\".* \"_", "Dummy_");
//			String contentInstantColorChunk = dummyString.split("_")[1];
//			String newColorName = contentInstantColorChunk.startsWith("#")? 
//					label2.replace(contentInstantColorChunk, Colors.colorToHexString(fillColor)):
//						label2.replace(" \"_", " \"_"+Colors.colorToHexString(fillColor));
//			newNames[nn] = newColorName;
//		}
//		rename(newNames,  indexes, true);

		if (cipContentInstant != null && rpName != null && !cipContentInstant.getName().equals(rpName))
			rename(cipContentInstant.getName(), null, true);
		ImageCanvas ic = imp != null ? imp.getCanvas() : null;
		ContentInstant contentInstant = imp != null ? univ.getSelected().getCurrentInstant() : null;
		boolean showingAll = ic != null && ic.getShowAllROIs();
		if (contentInstant != null && (n == 1 || !showingAll)) {
			if (fillColor != null)
				contentInstant.setColor(new Color3f(fillColor));
		}
		if (imp != null)
			imp.draw();
		if (record()) {
			if (fillColor != null)
				Recorder.record("contentInstantManager", "Set Fill Color", Colors.colorToString(fillColor));
			else {
				Recorder.record("contentInstantManager", "Set Color", Colors.colorToString(color != null ? color : Color.red));
				Recorder.record("contentInstantManager", "Set Line Width", lineWidth);
			}
		}
		list.repaint();
	}


	void sort() {
		busy = true;
		// int n = contentInstants.size();
		// if (n==0) return;
		String[] labels = new String[listModel.getSize()];
		for (int i = 0; i < labels.length; i++)
			labels[i] = (String) listModel.get(i);
		String[] fullLabels = new String[fullListModel.getSize()];
		for (int i = 0; i < fullLabels.length; i++)
			fullLabels[i] = (String) fullListModel.get(i);

		int index = 0;
		// for (Enumeration en=contentInstants.keys(); en.hasMoreElements();)
		// labels[index++] = (String)en.nextElement();
		listModel.removeAllElements();
		fullListModel.removeAllElements();
		// this.setTitle( "Content3D Manager SORTING!!!") ;

		if (sortmode > 0) {
			RoiLabelByNumbersSorter.sort(labels, sortmode);
			RoiLabelByNumbersSorter.sort(fullLabels, sortmode);

		} else {
			StringSorter.sort(labels);
			StringSorter.sort(fullLabels);

		}
		int numSorted = 0;
		// Dimension dim = list.getSize();
		// list.setSize(0,0);
		for (int i = 0; i < labels.length; i++) {
			// this.setTitle( "Content3D Manager" + ((numSorted%100>50)?" SORTING!!!":"
			// Sorting...") );
			listModel.addElement(labels[i]);
			numSorted++;
		}
		// list.setSize(dim);
		for (int i = 0; i < fullLabels.length; i++) {
			fullListModel.addElement(fullLabels[i]);
		}
		// this.setTitle( "Content3D Manager" );

		if (record())
			Recorder.record("contentInstantManager", "Sort");
		busy = false;

	}

//	void options() {
//		Color c = ImageCanvas.getShowAllColor();
//		GenericDialog gd = new GenericDialog("Options");
//		// gd.addPanel(makeButtonPanel(gd), GridBagConstraints.CENTER, new Insets(5, 0,
//		// 0, 0));
//		gd.addCheckbox("Associate \"Show All\" ROIs with slices", Prefs.showAllSliceOnly);
//		gd.addCheckbox("Restore ROIs centered", restoreCentered);
//		gd.addCheckbox("Use ROI names as labels", Prefs.useNamesAsLabels);
//		gd.showDialog();
//		if (gd.wasCanceled()) {
//			if (c != ImageCanvas.getShowAllColor())
//				ImageCanvas.setShowAllColor(c);
//			return;
//		}
//		Prefs.showAllSliceOnly = gd.getNextBoolean();
//		restoreCentered = gd.getNextBoolean();
//		Prefs.useNamesAsLabels = gd.getNextBoolean();
//		ImagePlus imp = this.imp;
//		if (imp != null)
//			imp.draw();
//		if (record()) {
//			Recorder.record("contentInstantManager", "Associate", Prefs.showAllSliceOnly ? "true" : "false");
//			Recorder.record("contentInstantManager", "Centered", restoreCentered ? "true" : "false");
//			Recorder.record("contentInstantManager", "UseNames", Prefs.useNamesAsLabels ? "true" : "false");
//		}
//	}


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

	boolean error(String msg) {
		new MessageDialog(this, "Content3DManager", msg);
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
	 * Returns a reference to the Content3D Manager or null if it is not open.
	 */
	public static Content3DManager getInstance(ImagePlus queryImp) {
		if (queryImp != null) {
			for (int i = 0; i < WindowManager.getNonImageWindows().length; i++) {
				Frame frame = WindowManager.getNonImageWindows()[i];
				// IJ.log(frame.toString()+" \n"+queryImp.toString()+" \n"
				// + ((frame instanceof ContentInstantManager)?((ContentInstantManager)
				// frame).getImagePlus().toString():"None are rm\n")
				// +" "+/*((((ContentInstantManager)frame).getImagePlus() ==
				// queryImp)?"YES!":"nope"+*/"\n");
				if ((frame instanceof Content3DManager) && ((Content3DManager) frame).getImagePlus() == queryImp) {
					// IJ.log("YES");
					return (Content3DManager) frame;
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
	 * Returns a reference to the Content3D Manager window or to the macro batch mode
	 * ContentInstantManager, or null if neither exists.
	 */
	public static Content3DManager getInstance2() {
		Content3DManager rm = getInstance(WindowManager.getCurrentImage());
		return rm;
	}

	/**
	 * Returns the ROI Hashtable.
	 * 
	 * @see getCount
	 * @see getContentInstantsAsArray
	 */
	public Hashtable<String, ContentInstant> getCIs() {
		return contentInstants;
	}

	public Hashtable<String, ArrayList<ContentInstant>> getROIsByNumbers() {
		return getContentInstantsByNumbers();
	}

	/**
	 * Returns the selection list.
	 * 
	 * @see getCount
	 * @see getContentInstantsAsArray
	 */
	public JList getList() {
		return list;
	}

	/** Returns the ROI count. */
	public int getCount() {
		return listModel.getSize();
	}

	/** Returns the shown (searched) set ROIs as an array. */
	public ContentInstant[] getShownContentInstantsAsArray() {
		int n = listModel.getSize();
		ContentInstant[] array = new ContentInstant[n];
		for (int i = 0; i < n; i++) {
			String label = (String) listModel.getElementAt(i);
			array[i] = (ContentInstant) contentInstants.get(label);
		}
		return array;
	}

	/**
	 * Returns the shown (searched) set of ROIs for the specifice slice/frame as an
	 * array.
	 */
	private ContentInstant[] getDisplayedContentInstantsAsArray(int t) {
		ContentInstant[] contentInstantSetIn = this.getShownContentInstantsAsArray();
		if (contentInstantSetIn == null)
			return null;
		ArrayList<ContentInstant> matchedContentInstants = new ArrayList<ContentInstant>();
		for (int i = 0; i < contentInstantSetIn.length; i++) {
			// IJ.log(( contentInstantSetIn[i].getZPosition() +" "+ z +" "+ contentInstantSetIn[i].getTimepoint()
			// +" "+ t+"\n"));
			if (contentInstantSetIn[i].getTimepoint() > t - tSustain && contentInstantSetIn[i].getTimepoint() < t + tSustain) {
				matchedContentInstants.add(contentInstantSetIn[i]);
				// IJ.showMessage("");

			}
		}
		// IJ.log(""+matchedContentInstantIndexes.size());

		ContentInstant[] displayedContentInstants = new ContentInstant[matchedContentInstants.size()];
		for (int i = 0; i < displayedContentInstants.length; i++) {
			displayedContentInstants[i] = matchedContentInstants.get(i);
		}
		return displayedContentInstants;

	}

	/** Returns the full set of ROIs as an array. */
	public ContentInstant[] getFullContentInstantsAsArray() {
		int n = fullListModel.getSize();
		ContentInstant[] array = new ContentInstant[n];
		for (int i = 0; i < n; i++) {
			String label = (String) fullListModel.getElementAt(i);
			if (contentInstants != null)
				array[i] = (ContentInstant) contentInstants.get(label);
		}
		return array;
	}

	/** Returns the selected ROIs as an array. */
	public ContentInstant[] getSelectedContentInstantsAsArray() {
		int[] indexes = getSelectedIndexes();
		int n = indexes.length;
		ContentInstant[] array = new ContentInstant[n];
		for (int i = 0; i < n; i++) {
			String label = (String) listModel.getElementAt(indexes[i]);
			array[i] = (ContentInstant) contentInstants.get(label);
		}
		return array;
	}

	/** Returns the full set of original ROIs, cloned as an independent array. */
	public ContentInstant[] getOriginalContentInstantsAsClonedArray() {

		ContentInstant[] clonedArray;
		if (originalsCloned) {
			clonedArray = new ContentInstant[originalContentInstants.length];
			for (int i = 0; i < originalContentInstants.length; i++) {
				clonedArray[i] = (ContentInstant) originalContentInstants[i].cloneNode(true);
			}
			return (clonedArray);
		} else
			return null;
	}

	public ContentInstant[] getOriginalContentInstantsAsArray() {
		if (originalsCloned) {
			return originalContentInstants;
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
	 * Executes the Content3D Manager "Add", "Add & Draw", "Update", "Delete", "Measure",
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
			ContentInstant contentInstant = univ != null ? univ.getSelected().getCurrentInstant() : null;
			add(contentInstant, shift, alt, false);
		} 	else if (cmd.equals("delete"))
			delete(false);
		else if (cmd.equals("sort"))
			sort();

		 else if (cmd.equals("deselect") || cmd.indexOf("all") != -1) {
			if (IJ.isMacOSX())
				ignoreInterrupts = true;
			select(-1);
			IJ.wait(50);
		} else
			ok = false;
		macro = false;
		return ok;
	}

	/**
	 * Executes the Content3D Manager "Open", "Save" or "Rename" command. Returns false if
	 * <code>cmd</code> is not "Open", "Save" or "Rename", or if an error occurs.
	 */
	public boolean runCommand(String cmd, String name) {
		return false;
}

	/**
	 * Adds the current selection to the Content3D Manager, using the specified color (a 6
	 * digit hex string) and line width.
	 */
	public boolean runCommand(String cmd, String hexColor, double lineWidth) {
		ImagePlus imp = this.imp;
		ContentInstant contentInstant = imp != null ? univ.getSelected().getCurrentInstant() : null;
		if (contentInstant != null)
			;
		if (hexColor == null && lineWidth == 1.0 && (IJ.altKeyDown() && !Interpreter.isBatchMode()))
			addContentInstant(true);
		else {
			Color color = hexColor != null ? Colors.decode(hexColor, Color.cyan) : null;
			addContentInstant(null, false, color, (int) Math.round(lineWidth), true);
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
				Recorder.record("contentInstantManager", "Deselect");
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

		if (list.getSelectedIndices().length <= 1) {

//			restore(imp, index, true);
				String selectedLabel = list.getSelectedValue();
				String editedLabel = selectedLabel.replace("_#0_#0 \"_0", "").replace("\"", "");
				Content c = ((Content)((Image3DUniverse) univ).getContent(editedLabel));
				if (c.getInstants().containsValue(contentInstants.get(selectedLabel))) {
					((Image3DUniverse)univ).select(c);

				}
			
			
//			if (aceTree != null) {
//				ContentInstant cContentInstant = contentInstants.get(listModel.get(getSelectedIndexes()[0]));
//				VTreeImpl vti = aceTree.getVtree().getiVTreeImpl();
//				if (vti != null && vti.getTestCanvas() != null) {
//					for (int i = 0; i < vti.iCellLines.size(); i++) {
//						Object o = vti.iCellLines.get(i);
//						if (!(o instanceof CellLine))
//							continue;
//						CellLine cL = (CellLine) o;
//						org.rhwlab.tree.Cell c = cL.c;
//						if (c.getName()
//								.equals(listModel.get(getSelectedIndexes()[0]).replace("\"", "").split(" ")[0])) {
//							int cStart = c.getTime();
//							int cEnd = c.getEndTime();
//							int rTime = cContentInstant.getTimepoint();
//							if (cStart <= rTime && cEnd >= rTime) {
//								c.setIntTime(rTime);
//								for (MouseListener ml : vti.getTestCanvas().getMouseListeners()) {
//									if (ml != this) {
//										ml.mouseClicked(new MouseEvent(vti.getTestCanvas(), 0, 0, 0,
//												c.getIntTime() + 100, cL.y1 + 5, 1, false, MouseEvent.BUTTON1));
//									}
//								}
//							}
//							break;
//						}
//					}
//				}
//			}
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
		ContentInstant previousContentInstant = univ.getSelected().getCurrentInstant();
		if (previousContentInstant == null) {
			IJ.setKeyUp(IJ.ALL_KEYS);
			select(imp, index);
			return;
		}
		previousContentInstant = (ContentInstant) previousContentInstant.cloneNode(true);
		String label = (String) listModel.getElementAt(index);
		list.setSelectedIndices(getSelectedIndexes());
		ContentInstant contentInstant = (ContentInstant) contentInstants.get(label);
		if (contentInstant != null) {
			((Image3DUniverse) univ).select(((Content)contentInstant.getContentsContainingThisInstant().get(0)));
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

//	/** Moves all the ROIs to the specified image's overlay. */
//	public void moveContentInstantsToOverlay(ImagePlus imp) {
//		ContentInstant[] contentInstants = getShownContentInstantsAsArray();
//		int n = contentInstants.length;
//		Overlay overlay = new Overlay();
//		ImageCanvas ic = imp.getCanvas();
//		Color color = ic != null ? ic.getShowAllColor() : null;
//		for (int i = 0; i < n; i++) {
//			ContentInstant contentInstant = (ContentInstant) contentInstants[i].cloneNode(true);
//			if (!Prefs.showAllSliceOnly)
//				contentInstant.setPosition(imp, 0);
//			if (color != null && contentInstant.getStrokeColor() == null)
//				contentInstant.setStrokeColor(color);
//			if (contentInstant.getStrokeWidth() == 1)
//				contentInstant.setStrokeWidth(0);
//			overlay.add(contentInstant);
//		}
//		if (labelsCheckbox.isSelected()) {
//			overlay.drawLabels(true);
//			overlay.drawBackgrounds(true);
//			overlay.setLabelColor(Color.white);
//		}
//		imp.setOverlay(overlay);
//	}

	/** Overrides PlugInFrame.dispose(). */
	public void dispose() {
		synchronized (this) {
			done = true;
			notifyAll();
		}
		if (contentInstants != null) {
			for (ContentInstant contentInstant : contentInstants.values()) {
//				contentInstant.setImage(null);
//				contentInstant.setMotherImp(null);
			}
			contentInstants.clear();
			contentInstants = null;
		}
		if (originalContentInstants != null) {
			for (ContentInstant contentInstant : originalContentInstants) {
//				contentInstant.setImage(null);
//				contentInstant.setMotherImp(null);
			}
			originalContentInstants = null;
		}
		if (getContentInstantsByNumbers() != null) {
			for (ArrayList<ContentInstant> contentInstantAL : getContentInstantsByNumbers().values()) {
				for (ContentInstant contentInstant : contentInstantAL) {
//					contentInstant.setImage(null);
//					contentInstant.setMotherImp(null);
				}
			}
			setContentInstantsByNumbers(null);
		}
		if (getContentInstantsByRootName() != null) {
			for (ArrayList<ContentInstant> contentInstantAL : getContentInstantsByRootName().values()) {
				for (ContentInstant contentInstant : contentInstantAL) {
//					contentInstant.setImage(null);
//					contentInstant.setMotherImp(null);
				}
			}
			setContentInstantsByRootName(null);
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
//			colorLegend.setContent3DManager(null);
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
	 * getCount(). The selected ROIs are not highlighted in the Content3D Manager list and
	 * are no longer selected after the next Content3D Manager command is executed.
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
		//showAll(SHOW_ALL);
	}

	public int getZSustain() {
		return zSustain;
	}

	public void setZSustain(int sustain) {
		zSustainSpinner.setValue(sustain);
		zSustain = sustain;
		//showAll(SHOW_ALL);
	}

	public int getTSustain() {
		return tSustain;
	}

	public void setTSustain(int sustain) {
		tSustainSpinner.setValue(sustain);
		tSustain = sustain;
		//showAll(SHOW_ALL);

	}

	public void setShowAllCheckbox(boolean canvasShowAllState) {
		showAllCheckbox.setSelected(canvasShowAllState);
	}

	public ContentInstant[] getSliceSpecificContentInstantArray(int z, int t, boolean getSpanners) {
		ContentInstant[] contentInstantSetIn = this.getFullContentInstantsAsArray();
		if (contentInstantSetIn == null)
			return null;
		ArrayList<ContentInstant> matchedContentInstants = new ArrayList<ContentInstant>();
		for (int i = 0; i < contentInstantSetIn.length; i++) {
			// IJ.log(( contentInstantSetIn[i].getZPosition() +" "+ z +" "+ contentInstantSetIn[i].getTimepoint()
			// +" "+ t+"\n"));
			if (contentInstantSetIn[i].getTimepoint() == t || (contentInstantSetIn[i].getTimepoint() == 0 && getSpanners)) {
//				if (contentInstantSetIn[i].getZPosition() == z || (contentInstantSetIn[i].getZPosition() == 0
//						&& getSpanners)/*
//										 * && contentInstantSetIn[i].getTimepoint() > t - tSustain && contentInstantSetIn[i].getTimepoint() <
//										 * t + tSustain
//										 */) {
//					matchedContentInstants.add(contentInstantSetIn[i]);
//					// IJ.showMessage("");
//
//				}
			}
		}
		// IJ.log(""+matchedContentInstantIndexes.size());

		ContentInstant[] sliceSpecificFullContentInstants = new ContentInstant[matchedContentInstants.size()];
		for (int i = 0; i < sliceSpecificFullContentInstants.length; i++) {
			sliceSpecificFullContentInstants[i] = matchedContentInstants.get(i);
		}
		return sliceSpecificFullContentInstants;

	}

	public int[] getSliceSpecificIndexes(int z, int t, boolean getSpanners) {
		ContentInstant[] contentInstantSetIn = this.getFullContentInstantsAsArray();
		if (contentInstantSetIn == null)
			return null;
		ArrayList<Integer> matchedIndexes = new ArrayList<Integer>();
		for (int i = 0; i < contentInstantSetIn.length; i++) {
			// IJ.log(( contentInstantSetIn[i].getZPosition() +" "+ z +" "+ contentInstantSetIn[i].getTimepoint()
			// +" "+ t+"\n"));
			if (contentInstantSetIn[i].getTimepoint() == t || (contentInstantSetIn[i].getTimepoint() == 0 && getSpanners)) {
//				if (contentInstantSetIn[i].getZPosition() == z || (contentInstantSetIn[i].getZPosition() == 0
//						&& getSpanners)/*
//										 * && contentInstantSetIn[i].getTimepoint() > t - tSustain && contentInstantSetIn[i].getTimepoint() <
//										 * t + tSustain
//										 */) {
//					matchedIndexes.add(i);
//					// IJ.showMessage("");
//				}
			}
		}
		// IJ.log(""+matchedContentInstantIndexes.size());

		int[] sliceSpecificFullIndexes = new int[matchedIndexes.size()];
		for (int i = 0; i < sliceSpecificFullIndexes.length; i++) {
			sliceSpecificFullIndexes[i] = matchedIndexes.get(i);
		}
		return sliceSpecificFullIndexes;

	}

//	public ContentInstant getSliceSpecificContentInstant(ImagePlus impIn, int z, int t) {
//		// this.imp = imp;
//		ContentInstant combinedROI = null;
//		this.setSelectedIndexes(this.getAllShownIndexes());
//		ContentInstant[] contentInstantSubSet = this.getSelectedContentInstantsAsArray();
//		ArrayList<Integer> matchedContentInstantIndexes = new ArrayList<Integer>();
//
//		// These next two parameters are essential to tuning the size and scaling over
//		// time of the cell-specific R0Is that can be used to isolate cells and
//		// lineages. This is the best overall fit I could find for pie-I:: HIS-58 V.
//		// I will probably want to make some sliders or spinners for these in some
//		// previewable dialog.
//		// double widthDenom = 4;
//		// double timeBalancer = 2;
//
//		double widthDenom = 4.5;
//		double timeBalancer = 2;
//		ImagePlus guideImp = imp;
//		if (imp.getMotherImp() != null)
//			guideImp = imp.getMotherImp();
//		int frames = guideImp.getNFrames();
//		BigDecimal framesBD = new BigDecimal("" + (frames + timeBalancer));
//		BigDecimal widthDenomBD = new BigDecimal("" + widthDenom);
//		BigDecimal tBD = new BigDecimal(
//				"" + (imp.getMotherID() > 0 ? imp.getMotherID() : 0 + imp.getFrame() + timeBalancer));
//		BigDecimal impHeightBD = new BigDecimal("" + guideImp.getHeight());
//		BigDecimal cellDiameterBD = impHeightBD.divide(widthDenomBD, MathContext.DECIMAL32)
//				.multiply(takeRoot(3,
//						(framesBD.subtract(tBD).add(new BigDecimal("1"))).divide(tBD, MathContext.DECIMAL32),
//						new BigDecimal(".001")), MathContext.DECIMAL32);
//
//		for (int i = 0; i < contentInstantSubSet.length; i++) {
//			if (/*Math.abs(contentInstantSubSet[i].getZPosition() - z) * imp.getCalibration().pixelDepth < cellDiameterBD.intValue()
//					/ 2 &&*/ contentInstantSubSet[i].getTimepoint() > t - tSustain && contentInstantSubSet[i].getTimepoint() < t + tSustain) {
//
//				// Subtractive solution to shrinking cell sizes...
//				// double inPlaneDiameter = Math.sqrt(
//				// Math.pow(((imp.getWidth()/widthDenom)-(imp.getWidth()/widthDenom)*t*timeFactor/imp.getNFrames())/2,2)
//				// -
//				// Math.pow((contentInstantSubSet[i].getZPosition()-z)*imp.getCalibration().pixelDepth*2,2)
//				// );
//
//				// Volume fraction solution to shrinking cell sizes...
//				double inPlaneDiameter = 2 * Math.sqrt(Math.pow(cellDiameterBD.intValue() / 2, 2)
//						- Math.pow((contentInstantSubSet[i].getZPosition() - z) * imp.getCalibration().pixelDepth, 2));
//				// IJ.log(""+cellDiameterBD.intValue() +" "+ inPlaneDiameter );
//				imp.setContentInstant(new OvalContentInstant(contentInstantSubSet[i].getBounds().getCenterX() - inPlaneDiameter / 2,
//						contentInstantSubSet[i].getBounds().getCenterY() - inPlaneDiameter / 2, inPlaneDiameter, inPlaneDiameter));
//				addContentInstant(univ.getSelected().getCurrentInstant());
//				matchedContentInstantIndexes.add(this.getCount() - 1);
//			}
//		}
//
//		// Select image corners.
//		imp.setContentInstant(new Rectangle(0, 0, 1, 1));
//		addContentInstant(univ.getSelected().getCurrentInstant());
//		matchedContentInstantIndexes.add(this.getCount() - 1);
//
//		imp.setContentInstant(new Rectangle(0, imp.getHeight() - 1, 1, 1));
//		addContentInstant(univ.getSelected().getCurrentInstant());
//		matchedContentInstantIndexes.add(this.getCount() - 1);
//
//		imp.setContentInstant(new Rectangle(imp.getWidth() - 1, 0, 1, 1));
//		addContentInstant(univ.getSelected().getCurrentInstant());
//		matchedContentInstantIndexes.add(this.getCount() - 1);
//
//		imp.setContentInstant(new Rectangle(imp.getWidth() - 1, imp.getHeight() - 1, 1, 1));
//		addContentInstant(univ.getSelected().getCurrentInstant());
//		matchedContentInstantIndexes.add(this.getCount() - 1);
//
//		int[] sliceSpecificIndexes = new int[matchedContentInstantIndexes.size()];
//		for (int i = 0; i < sliceSpecificIndexes.length; i++) {
//			sliceSpecificIndexes[i] = matchedContentInstantIndexes.get(i).intValue();
//		}
//
//		setSelectedIndexes(sliceSpecificIndexes);
//
//		if (this.runCommand("combine"))
//			combinedROI = univ.getSelected().getCurrentInstant();
//
//		setSelectedIndexes(sliceSpecificIndexes);
//
//		runCommand("delete");
//		imp.killContentInstant();
//
//		return combinedROI;
//	}

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

//	private void copyToOtherRMs() {
//		ContentInstant[] copiedContentInstants = this.getSelectedContentInstantsAsArray();
//		if (copiedContentInstants == null)
//			return;
//		int[] imageIDs = WindowManager.getIDList();
//		for (int j = 0; j < imageIDs.length; j++) {
//			Content3DManager recipRM = WindowManager.getImage(imageIDs[j]).getRoiManager();
//			if (recipRM == null)
//				recipRM = new Content3DManager(WindowManager.getImage(imageIDs[j]), true);
//			if (recipRM != this) {
//				for (int i = 0; i < copiedContentInstants.length; i++) {
//
//					recipRM.addContentInstant((ContentInstant) copiedContentInstants[i].cloneNode(true));
//					recipRM.getShownContentInstantsAsArray()[recipRM.getCount() - 1].setPosition(copiedContentInstants[i].getCPosition(),
//							copiedContentInstants[i].getZPosition(), copiedContentInstants[i].getTimepoint());
//					String nameEndReader = copiedContentInstants[i].getName();
//
//					int c = copiedContentInstants[i].getCPosition();
//					int z = copiedContentInstants[i].getZPosition();
//					int t = copiedContentInstants[i].getTimepoint();
//					if (nameEndReader.split("_").length == 4) {
//						c = Integer.parseInt(nameEndReader.split("_")[1]);
//						z = Integer.parseInt(nameEndReader.split("_")[2]);
//						t = Integer.parseInt(nameEndReader.split("_")[3].split("[CZT-]")[0]);
//					}
//					while (nameEndReader.endsWith("C") || nameEndReader.endsWith("Z") || nameEndReader.endsWith("T")) {
//						if (nameEndReader.endsWith("C")) {
//							c = 0;
//							nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
//						}
//						if (nameEndReader.endsWith("Z")) {
//							z = 0;
//							nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
//						}
//						if (nameEndReader.endsWith("T")) {
//							t = 0;
//							nameEndReader = nameEndReader.substring(0, nameEndReader.length() - 1);
//						}
//					}
//					recipRM.getShownContentInstantsAsArray()[recipRM.getCount() - 1].setPosition(c, z, t);
//					recipRM.getShownContentInstantsAsArray()[recipRM.getCount() - 1].setName(nameEndReader);
//				}
//				//recipRM.showAll(Content3DManager.SHOW_ALL);
//			}
//		}
//	}

	public boolean isShowAll() {
		return showAll;
	}

//	public void sketch3D(Object source) {
//		IJ.log("" + imp.getCalibration().pixelDepth);
//		int synapseScale = imp.getWidth() / 25;
//		int modelWidth = 80;
//		if (getShownContentInstantsAsArray().length < 1 || imp.getNDimensions() > 6) {
//			busy = false;
//			return;
//		}
//		if (Channels.getInstance() != null)
//			Channels.getInstance().dispose();
//		boolean splitThem = shiftKeyDown;
//		boolean eightBit = shiftKeyDown;
//		boolean brainbow = controlKeyDown;
//		boolean fatSynapses = altKeyDown;
//		imp.getWindow().setSubTitleBkgdColor(Color.yellow);
//		int[] selectedIndexes = this.getSelectedIndexes();
//		ContentInstant[] selectedContentInstants = this.getSelectedContentInstantsAsArray();
//		int[] fullIndexes = this.getFullListIndexes();
//		ContentInstant[] fullContentInstants = this.getFullContentInstantsAsArray();
//		int[] shownIndexes = this.getAllShownIndexes();
//		ContentInstant[] shownContentInstants = this.getShownContentInstantsAsArray();
//
//		Hashtable<String, ArrayList<String>> shownContentInstantsHash = new Hashtable<String, ArrayList<String>>();
//		if (selectedIndexes.length < 1) {
//			selectedIndexes = this.getAllShownIndexes();
//			selectedContentInstants = this.getShownContentInstantsAsArray();
//		}
//
//		if (brainbow) {
//			if (source instanceof JButton
//					&& ((JButton) source).getParent().getParent().getParent() instanceof ImageWindow)
//				source = ((ImageWindow) ((JButton) source).getParent().getParent().getParent()).getImagePlus();
//			ColorLegend cl = getColorLegend(source);
//			if (cl != null) {
//				if (cl.getRoiManager() != null && cl.getRoiManager() != this) {
//					if (altKeyDown)
//						cl.getRoiManager().altKeyDown = true;
//					cl.getRoiManager().sketch3D(source);
//					cl.getRoiManager().altKeyDown = false;
//					if (altKeyDown)
//						cl = null;
//					IJ.log("Passing to original Content3D Manager...");
//					return;
//				} else if (altKeyDown) {
//					if (this.getCount() < 1) {
//						IJ.log("Manager contains no Tags");
//						return;
//					}
//
//					colorLegend.dispose();
//					WindowManager.removeWindow(colorLegend);
//					colorLegend = null;
//					cellNames = null;
//					fullCellNames = null;
//					brainbowColors = null;
//				} else {
//					if (this.getCount() < 1) {
//						IJ.log("Manager contains no Tags");
//						return;
//					}
//
//					ArrayList<String> selectedNamesStrings = new ArrayList<String>();
//					IJ.log("" + selectedNamesStrings.size());
//					for (int r = 0; r < selectedContentInstants.length; r++) {
//						if (selectedContentInstants[r].getName().split("[\"|=]").length > 1) {
//							String[] searchTextChunks = selectedContentInstants[r].getName().split("[\"|=]")[1].split(" ");
//							String searchText = "";
//							for (String chunk : searchTextChunks)
//								if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
//									searchText = searchText + " " + chunk;
//							String cellTagName = searchText.trim();
//							selectedNamesStrings.add(cellTagName);
//							// IJ.log(selectedNamesStrings.get(selectedNamesStrings.size()-1));
//						}
//					}
//					if (cellNames != null /* && this != cl.getRoiManager() */) {
//						cellNames = new ArrayList<String>();
//						for (JCheckBox cb : colorLegend.getJCheckBox()) {
//							if (((!cb.isSelected() && colorLegend.getChoice().getSelectedItem().matches("Hide.*"))
//									|| (cb.isSelected() && !colorLegend.getChoice().getSelectedItem().matches("Hide.*"))
//									|| colorLegend.getChoice().getSelectedItem().matches("Display.*"))
//									&& !cellNames.contains(cb.getName())) {
//								cellNames.add(cb.getName());
//								// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
//							}
//						}
//						if (cellNames.size() < 1 && !colorLegend.getChoice().getSelectedItem().matches("Hide.*")) {
//							for (JCheckBox cb : colorLegend.getJCheckBox()) {
//								cellNames.add(cb.getName());
//								// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
//							}
//						}
//					}
//					if (cellNames != null) {
//						String[] cellNamesArray = cellNames.toArray(new String[cellNames.size()]);
//						for (int q = cellNamesArray.length - 1; q >= 0; q--) {
//							// IJ.log(""+cellNames.get(q) + " "+ q +"?");
//							if (!selectedNamesStrings.contains(cellNames.get(q))) {
//								// IJ.log(""+cellNames.get(q) + " "+ q +"XXX");
//								cellNames.remove(q);
//							} else {
//								// IJ.log(""+cellNames.get(q) + " "+ q +"OK");
//							}
//						}
//					}
//				}
//			}
//		}
//		if (this.getCount() < 1) {
//			IJ.log("Manager contains no Tags");
//			return;
//		}
//
//		ImageProcessor drawIP;
//		if (eightBit) {
//			drawIP = new ByteProcessor(imp.getWidth(), imp.getHeight());
//		} else {
//			drawIP = new ColorProcessor(imp.getWidth(), imp.getHeight());
//		}
//		if (fullCellNames == null) {
//			fullCellNames = new ArrayList<String>();
//			mowColors = new Hashtable<String, Color>();
//			mowColors.put("", Color.white);
//			for (int r = 0; r < fullContentInstants.length; r++) {
//				if (fullContentInstants[r] != null && fullContentInstants[r].getName().matches(".*[\"|=].*")) {
//					String[] searchTextChunks = fullContentInstants[r].getName().split("[\"|=]")[1].split(" ");
//					String searchText = "";
//					for (String chunk : searchTextChunks)
//						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
//							searchText = searchText + " " + chunk;
//					if (searchText.trim().matches(".*ABar.*"))
//						isEmbryonic = true;
//					if (!fullCellNames.contains(searchText.trim())) {
//						fullCellNames.add(searchText.trim());
//						if (fullContentInstants[r].isArea() && fullContentInstants[r].getFillColor() != null)
//							mowColors.put(fullCellNames.get(fullCellNames.size() - 1),
//									new Color(fullContentInstants[r].getFillColor().getRGB()));
//						else if (fullContentInstants[r].isArea() && fullContentInstants[r].getFillColor() == null)
//							mowColors.put(fullCellNames.get(fullCellNames.size() - 1), Color.gray);
//						if (fullContentInstants[r].isLine() && fullContentInstants[r].getStrokeColor() != null)
//							mowColors.put(fullCellNames.get(fullCellNames.size() - 1),
//									new Color(fullContentInstants[r].getStrokeColor().getRGB()));
//					}
//				}
//			}
//		}
//		if (cellNames == null /* && this == cl.getRoiManager() */) {
//			cellNames = new ArrayList<String>();
//			for (int r = 0; r < selectedContentInstants.length; r++) {
//				if (selectedContentInstants[r] != null && selectedContentInstants[r].getName().matches(".*[\"|=].*")) {
//					String[] searchTextChunks = selectedContentInstants[r].getName().split("[\"|=]")[1].split(" ");
//					String searchText = "";
//					for (String chunk : searchTextChunks)
//						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
//							searchText = searchText + " " + chunk;
//					if (!cellNames.contains(searchText.trim())) {
//						cellNames.add(searchText.trim());
//						// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
//					}
//				}
//			}
//			if (cellNames == null) {
//				for (int r = 0; r < fullCellNames.size(); r++) {
//					cellNames.add("" + fullCellNames.get(r));
//					// IJ.log(""+cellNames.get(cellNames.size()-1) + " "+ cellNames.size());
//
//				}
//			}
//		}
//
//		nameLists.add(cellNames);
//
//		if (brainbowColors == null) {
//			if (colorLegend == null) {
//				brainbowColors = new Hashtable<String, Color>();
//				brainbowColors.put(fullCellNames.get(0).trim().toLowerCase(), Color.white);
//				String[] hexChoices = { "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f" };
//				for (int c3 = 0; c3 < fullCellNames.size(); c3++) {
//					String randomHexString = "";
//					boolean unique = false;
//					do {
//						randomHexString = "#" + hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
//								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
//								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
//								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
//								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))]
//								+ hexChoices[(int) Math.round(Math.random() * (hexChoices.length - 1))];
//
//						for (int i = 0; i < brainbowColors.size(); i++) {
//							if (brainbowColors.get(fullCellNames.get(i).toLowerCase()) == Colors.decode(randomHexString,
//									Color.white)
//									|| brainbowColors.get(fullCellNames.get(i).toLowerCase()).darker()
//											.darker() == Colors.decode(randomHexString, Color.white)) {
//								unique = false;
//							} else {
//								unique = true;
//							}
//						}
//					} while (!unique);
//					brainbowColors.put(fullCellNames.get(c3).toLowerCase(),
//							Colors.decode(randomHexString, Color.white));
//				}
//			} else {
//				brainbowColors = new Hashtable<String, Color>();
//				JCheckBox[] cbs = colorLegend.getJCheckBox();
//				for (int cb = 0; cb < cbs.length; cb++) {
//					brainbowColors.put(cbs[cb].getName().toLowerCase(), cbs[cb].getBackground());
//				}
//			}
//		}
//		if (colorLegend == null) {
//			colorLegend = new ColorLegend(this);
//		}
//		imp.getWindow().addWindowFocusListener(colorLegend);
//		imp.getWindow().addMouseListener(colorLegend);
//
//		int outChannels = 1;
//		if (splitThem)
//			outChannels = cellNames.size();
//		ImagePlus[] outImps = new ImagePlus[outChannels];
//
//		if (isEmbryonic) {
//			for (int j = 0; j < shownIndexes.length; j++) {
//				String[] cellNumbers = shownContentInstants[j].getName().split("_");
//				if (shownContentInstantsHash.get(cellNumbers[cellNumbers.length - 2] + "_"
//						+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", "")) == null)
//					shownContentInstantsHash.put(
//							cellNumbers[cellNumbers.length - 2] + "_"
//									+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", ""),
//							new ArrayList<String>());
//				if (!shownContentInstantsHash
//						.get(cellNumbers[cellNumbers.length - 2] + "_"
//								+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", ""))
//						.contains(shownContentInstants[j].getName())) {
//					shownContentInstantsHash
//							.get(cellNumbers[cellNumbers.length - 2] + "_"
//									+ cellNumbers[cellNumbers.length - 1].replaceAll("[CZT]", ""))
//							.add(shownContentInstants[j].getName());
//					// IJ.log(cellNumbers[cellNumbers.length-2]+"_"+cellNumbers[cellNumbers.length-1].replaceAll("[CZT]",
//					// "") +" "+j+" "+shownContentInstants[j].getName());
//				}
//
//			}
//
//			int nChannels = imp.getNChannels();
//			int nSlices = imp.getNSlices();
//			int nFrames = imp.getNFrames();
//			GenericDialog gd = new GenericDialog("Sketch3D hyperstack");
//			gd.setInsets(12, 20, 8);
//			gd.addCheckbox("Sketch3D hyperstack", false);
//			gd.addCheckbox("Save full-size sketches?", false);
//			int nRangeFields = 0;
//			if (nFrames > 1) {
//				gd.setInsets(2, 30, 3);
//				gd.addStringField("Frames (t, " + 1 + "-" + imp.getNFrames() + "):",
//						"" + imp.getFrame() + "-" + imp.getFrame());
//				nRangeFields++;
//			}
//			Vector v = gd.getStringFields();
//			JTextField[] rangeFields = new JTextField[3];
//			for (int i = 0; i < nRangeFields; i++) {
//				rangeFields[i] = (JTextField) v.elementAt(i);
//				rangeFields[i].getDocument().addDocumentListener(this);
//			}
//			hyperstackCheckbox = (JCheckBox) (gd.getCheckboxes().elementAt(0));
//			boolean fullSketchToFile = false;
//			gd.showDialog();
//
//			int firstT = imp.getFrame();
//			int lastT = imp.getFrame();
//			if (!gd.wasCanceled()) {
//				if (gd.getNextBoolean()) {
//					if (nFrames > 1) {
//						String[] range = Tools.split(gd.getNextString(), " -");
//						double t1 = Tools.parseDouble(range[0]);
//						double t2 = range.length == 2 ? Tools.parseDouble(range[1]) : Double.NaN;
//						firstT = Double.isNaN(t1) ? firstT : (int) t1;
//						lastT = Double.isNaN(t2) ? lastT : (int) t2;
//						if (firstT > lastT) {
//							firstT = lastT;
//							lastT = firstT;
//						}
//						if (firstT < 1)
//							firstT = 1;
//						if (lastT > nFrames)
//							lastT = nFrames;
//					}
//				}
//				fullSketchToFile = gd.getNextBoolean();
//			}
//
//			double fillZfactor = imp.getCalibration().pixelDepth;
//			int outNSlices = 0;
//			for (int c2 = 0; c2 < outChannels; c2++) {
//				IJ.showStatus("Processing " + (c2 + 1) + "/" + outChannels + " channels...");
//				ImageStack sketchStack = new ImageStack(modelWidth, imp.getHeight() / (imp.getWidth() / modelWidth));
//				IJ.log("" + fillZfactor);
//				IJ.log("" + (double) (sketchStack.getHeight()) / (double) (imp.getHeight()));
//				IJ.log("" + imp.getHeight());
//				IJ.log("" + sketchStack.getHeight());
//				Color frameColor = Color.WHITE;
//				outNSlices = (int) (fillZfactor * imp.getNSlices());
//				int miniStackSize = outNSlices * modelWidth / imp.getWidth();
//				for (int t = firstT; t <= lastT; t++) {
//					ImageStack bigStack = new ImageStack(imp.getWidth(), imp.getHeight());
//					for (int z = 1; z <= outNSlices; z++) {
//						if (eightBit) {
//							drawIP = new ByteProcessor(imp.getWidth(), imp.getHeight());
//						} else {
//							drawIP = new ColorProcessor(imp.getWidth(), imp.getHeight());
//						}
//						drawIP.setColor(Color.BLACK);
//						drawIP.fill();
//						bigStack.addSlice(drawIP);
//					}
//
//					for (int z = 1; z <= outNSlices; z++) {
//						ArrayList<String> theseSlcSpecContentInstantNames = new ArrayList<String>();
//						if (/* z%fillZfactor == 0 && */ this.getSliceSpecificContentInstantArray((int) (z / fillZfactor), t,
//								false) != null) {
//							for (ContentInstant thisContentInstant : this.getSliceSpecificContentInstantArray((int) (z / fillZfactor), t, false))
//								theseSlcSpecContentInstantNames.add(thisContentInstant.getName());
//						}
//						// IJ.log(""+z);
//						for (int j = 0; j < theseSlcSpecContentInstantNames.size(); j++) {
//							// IJ.log(theseSlcSpecContentInstantNames.get(j));
//							if (theseSlcSpecContentInstantNames.get(j) != null
//									&& ((ContentInstant) contentInstants.get(theseSlcSpecContentInstantNames.get(j).trim())) != null
//									/* && z%fillZfactor == 0 */
//									&& ((ContentInstant) contentInstants.get(theseSlcSpecContentInstantNames.get(j).trim()))
//											.getZPosition() == (int) (z / fillZfactor)
//									&& ((ContentInstant) contentInstants.get(theseSlcSpecContentInstantNames.get(j).trim())).getTimepoint() == t) {
//								// IJ.log(theseSlcSpecContentInstantNames.get(j));
//								String[] searchTextChunks = theseSlcSpecContentInstantNames.get(j).split("[\"|=]")[1].split(" ");
//								String searchText = "";
//								for (String chunk : searchTextChunks)
//									if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
//										searchText = searchText + " " + chunk;
//								String cellTagName = searchText.trim();
//								// IJ.log(cellTagName);
//								if ((fatSynapses && theseSlcSpecContentInstantNames.get(j).startsWith("\"syn")) || isEmbryonic) {
//									if (cellNames.contains(cellTagName)
//											&& brainbowColors.get(cellTagName.toLowerCase()) != null) {
//										// IJ.log("cell name matches");
//										int maxRadius = synapseScale / 2;
//										for (int step = -maxRadius; step < maxRadius; step++) {
//											if (z + step > 0 && z + step <= outNSlices) {
//												drawIP = bigStack.getProcessor(z + step);
//												drawIP.setColor(brainbow
//														? new Color(
//																brainbowColors.get(cellTagName.toLowerCase()).getRGB())
//														: eightBit ? Color.WHITE : mowColors.get(cellTagName));
//												double radius = Math.pow(Math.pow((maxRadius), 2) - Math.pow(step, 2),
//														0.5);
//												ContentInstant thisContentInstant = ((ContentInstant) contentInstants.get(theseSlcSpecContentInstantNames.get(j).trim()));
//												// IJ.log(""+theseSlcSpecContentInstantNames.get(j).trim()+" "+radius);
//												if (thisContentInstant != null)
//													drawIP.fill(
//															new OvalContentInstant((int) thisContentInstant.getBounds().getCenterX() - radius,
//																	(int) thisContentInstant.getBounds().getCenterY() - radius,
//																	radius * 2, radius * 2));
//
//											}
//										}
//									}
//								}
//							}
//						}
//
//						for (int j = 0; j < theseSlcSpecContentInstantNames.size(); j++) {
//							if (theseSlcSpecContentInstantNames.get(j) != null) {
//								String[] searchTextChunks = theseSlcSpecContentInstantNames.get(j).split("[\"|=]")[1].split(" ");
//								String searchText = "";
//								for (String chunk : searchTextChunks)
//									if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
//										searchText = searchText + " " + chunk;
//								String cellTagName = searchText.trim();
//								if (!(fatSynapses && theseSlcSpecContentInstantNames.get(j).startsWith("\"syn")) && !isEmbryonic
//										&& brainbowColors.get(cellTagName) != null) {
//									drawIP.setColor(
//											brainbow ? new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB())
//													: eightBit ? Color.WHITE : mowColors.get(cellTagName));
//
//									if (cellNames.contains(cellTagName)) {
//										ContentInstant thisContentInstant = ((ContentInstant) contentInstants.get(theseSlcSpecContentInstantNames.get(j).trim()));
//										if (thisContentInstant != null)
//											drawIP.fill(thisContentInstant);
//									}
//								} else {
//									// IJ.log(""+cellTagName + brainbowColors.get(cellTagName.toLowerCase()));
//
//								}
//
//							}
//						}
//					}
//					for (int s = 1; s <= bigStack.getSize(); s = s + (imp.getWidth() / modelWidth)) {
//						ImageProcessor bip = bigStack.getProcessor(s);
//						bip.setInterpolationMethod(ImageProcessor.BICUBIC);
//						sketchStack.addSlice(
//								bip.resize(modelWidth, imp.getHeight() / (imp.getWidth() / modelWidth), false));
//						// IJ.log("RESIZE "+sketchStack.getSize());
//					}
//					miniStackSize = sketchStack.getSize();
//					ImagePlus bigImp = new ImagePlus("bigStack", bigStack);
//					if (fullSketchToFile) {
//						IJ.run(bigImp, "Save", "save=" + IJ.getDirectory("home") + imp.getTitle() + "_fullSketch3D"
//								+ IJ.pad(t, 5) + ".tif");
//					}
//					bigImp.close();
//					bigImp.flush();
//					bigImp = null;
//
//				}
//
//				ImagePlus sketchImp = new ImagePlus("Sketch_"
//						+ (splitThem ? (nameLists != null ? nameLists.get(nameLists.size() - 1) : cellNames).get(c2)
//								: "Composite"),
//						sketchStack);
//
//				outImps[c2] = sketchImp;
//				outImps[c2].getCalibration().pixelDepth = 1;
//				outImps[c2].setMotherImp(imp, 1);
//				IJ.run(outImps[c2], "Stack to Hyperstack...",
//						"order=xyczt(default) channels=1 slices=" + (miniStackSize / (lastT - firstT + 1)) + " frames="
//								+ (lastT - firstT + 1) + " display=Color");
//			}
//			compImps.add(outImps[0]);
//			if (splitThem) {
//				compImps.set(compImps.size() - 1, RGBStackMerge.mergeChannels(outImps, false));
//			}
//			if (compImps.get(compImps.size() - 1) != null) {
//				compImps.get(compImps.size() - 1).show();
//				compImps.get(compImps.size() - 1).getWindow().setBackground(this.getBackground());
//				compImps.get(compImps.size() - 1).setTitle(
//						"Sketch3D #" + compImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
//				compImps.get(compImps.size() - 1).getWindow().addWindowListener(this);
//				compImps.get(compImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
//				compImps.get(compImps.size() - 1).getCanvas().addMouseListener(colorLegend);
//				compImps.get(compImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
//			}
//			if (Channels.getInstance() != null)
//				((Channels) Channels.getInstance()).close();
//			IJ.run(compImps.get(compImps.size() - 1), "3D Project...",
//					"projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
//			projYImps.add(WindowManager.getImage("Projections of Sketch3D"));
//			if (splitThem)
//				projYImps.get(projYImps.size() - 1).setDimensions(projYImps.get(projYImps.size() - 1).getNChannels(),
//						projYImps.get(projYImps.size() - 1).getNFrames(),
//						projYImps.get(projYImps.size() - 1).getNSlices());
//			projYImps.get(projYImps.size() - 1).setTitle(
//					"Sketch3D ProjDV #" + projYImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
//			projYImps.get(projYImps.size() - 1).setMotherImp(imp, 1);
//			projYImps.get(projYImps.size() - 1).getWindow().setBackground(this.getBackground());
//			projYImps.get(projYImps.size() - 1).getWindow().addWindowListener(this);
//			projYImps.get(projYImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
//			projYImps.get(projYImps.size() - 1).getCanvas().addMouseListener(colorLegend);
//			projYImps.get(projYImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
//
//			if (Channels.getInstance() != null)
//				((Channels) Channels.getInstance()).close();
//			if (Channels.getInstance() != null)
//				((Channels) Channels.getInstance()).close();
//			IJ.run(compImps.get(compImps.size() - 1), "3D Project...",
//					"projection=[Nearest Point] axis=X-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
//
//			projZImps.add(WindowManager.getImage("Projections of Sketch3D"));
//			projZImps.get(projZImps.size() - 1).setTitle(
//					"Sketch3D ProjAP #" + projZImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
//			projZImps.get(projZImps.size() - 1).setMotherImp(imp, 1);
//			projZImps.get(projZImps.size() - 1).getWindow().addWindowListener(this);
//			projZImps.get(projZImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
//			projZImps.get(projZImps.size() - 1).getCanvas().addMouseListener(colorLegend);
//			projZImps.get(projZImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
//
//			if (imp.getCanvas().droppedGeneUrls != null) {
//				IJ.setForegroundColor(255, 255, 255);
//				IJ.run(compImps.get(compImps.size() - 1), "Label...",
//						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
//								+ imp.getCanvas().droppedGeneUrls + " range=[]");
//				compImps.get(compImps.size() - 1).changes = false;
//				IJ.run(projYImps.get(projYImps.size() - 1), "Label...",
//						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
//								+ imp.getCanvas().droppedGeneUrls + " range=[]");
//				projYImps.get(projYImps.size() - 1).changes = false;
//				IJ.run(projZImps.get(projZImps.size() - 1), "Label...",
//						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
//								+ imp.getCanvas().droppedGeneUrls + " range=[]");
//				projZImps.get(projZImps.size() - 1).changes = false;
//			}
//			if (splitThem)
//				projZImps.get(projZImps.size() - 1).setDimensions(projZImps.get(projZImps.size() - 1).getNChannels(),
//						projZImps.get(projZImps.size() - 1).getNFrames(),
//						projZImps.get(projZImps.size() - 1).getNSlices());
//			projZImps.get(projZImps.size() - 1).getWindow().setBackground(this.getBackground());
//			if (brainbow) {
//
//			} else if (splitThem) {
//				IJ.run("Channels Tool...");
//			}
//
//		} else /* not isEmbryonic */ {
//			int fillZfactor = 1;
//			int outNSlices = 0;
//			for (int c2 = 0; c2 < outChannels; c2++) {
//				IJ.showStatus("Processing " + (c2 + 1) + "/" + outChannels + " channels...");
//				ImageStack sketchStack = new ImageStack(
//						(int) (imp.getWidth() * imp.getCalibration().pixelWidth / imp.getCalibration().pixelDepth),
//						(int) (imp.getHeight() * imp.getCalibration().pixelWidth / imp.getCalibration().pixelDepth));
//				fillZfactor = (isEmbryonic ? sketchStack.getHeight() : imp.getNSlices()) / imp.getNSlices();
//				Color frameColor = Color.WHITE;
//				outNSlices = (int) (isEmbryonic ? sketchStack.getHeight() * 0.9 : imp.getNSlices());
//				for (int i = 0; i < imp.getNFrames(); i++) {
//
//					for (int z = 0; z < (outNSlices); z++) {
//						drawIP.setColor(Color.BLACK);
//						drawIP.fill();
//						for (int j = 0; j < shownIndexes.length; j++) {
//							if (shownContentInstants[j] != null) {
//								String cellTagName = shownContentInstants[j].getName().split("[\"|=]")[1].trim().toLowerCase();
//								if (shownContentInstants[j].getTimepoint() == i + 1 && cellNames != null && cellNames.size() > 0
//										&& (cellNames.get(c2).equals(cellTagName) || !splitThem)) {
//									if (fatSynapses && shownContentInstants[j].getName().startsWith("\"syn")) {
//										drawIP.setColor(brainbow
//												? new Color(brainbowColors.get(cellTagName.toLowerCase()).getRGB())
//												: eightBit ? Color.WHITE : mowColors.get(cellTagName));
//
//										if (cellNames.contains(cellTagName)) {
//											double radius = synapseScale / 2;
//											drawIP.fill(
//													new OvalContentInstant((int) shownContentInstants[j].getBounds().getCenterX() - radius,
//															(int) shownContentInstants[j].getBounds().getCenterY() - radius,
//															radius * 2, radius * 2));
//											// IJ.log("drewit"+radius);
//
//										}
//									}
//								}
//							}
//						}
//						for (int j = 0; j < shownIndexes.length; j++) {
//							if (shownContentInstants[j] != null) {
//								String cellTagName = shownContentInstants[j].getName().split("[\"|=]")[1].trim();
//								if (shownContentInstants[j].getTimepoint() == i + 1
//										&& shownContentInstants[j].getZPosition() * fillZfactor == z + 1 && cellNames != null
//										&& cellNames.size() > 0 && !isEmbryonic
//										&& (cellNames.get(c2).equals(cellTagName) || !splitThem)) {
//									if (!fatSynapses || !shownContentInstants[j].getName().startsWith("\"syn")
//											&& brainbowColors.get(cellTagName.toLowerCase()) != null) {
//										drawIP.setColor(
//												brainbow && brainbowColors.get(cellTagName.toLowerCase()) != null
//														? new Color(
//																brainbowColors.get(cellTagName.toLowerCase()).getRGB())
//														: eightBit ? Color.WHITE
//																: mowColors.get(cellTagName) != null
//																		? mowColors.get(cellTagName)
//																		: Color.WHITE);
//
//										if (cellNames.contains(cellTagName))
//											drawIP.fill(shownContentInstants[j]);
//									} else {
//
//									}
//								}
//							}
//						}
//						sketchStack.addSlice(drawIP.resize(
//								(int) (imp.getWidth() * imp.getCalibration().pixelWidth
//										/ imp.getCalibration().pixelDepth),
//								(int) (imp.getHeight() * imp.getCalibration().pixelWidth
//										/ imp.getCalibration().pixelDepth)));
//					}
//				}
//
//				ImagePlus sketchImp = new ImagePlus("Sketch_"
//						+ (splitThem ? (nameLists != null ? nameLists.get(nameLists.size() - 1) : cellNames).get(c2)
//								: "Composite"),
//						sketchStack);
//				outImps[c2] = sketchImp;
//				outImps[c2].getCalibration().pixelWidth = imp.getCalibration().pixelWidth;
//				outImps[c2].getCalibration().pixelHeight = imp.getCalibration().pixelWidth;
//				outImps[c2].getCalibration().pixelDepth = imp.getCalibration().pixelWidth;
//				outImps[c2].setMotherImp(imp, 1);
//				StackReverser sr = new StackReverser();
//				sr.flipStack(outImps[c2]);
//			}
//			compImps.add(outImps[0]);
//			if (splitThem) {
//				compImps.set(compImps.size() - 1, RGBStackMerge.mergeChannels(outImps, false));
//			}
//			if (compImps.get(compImps.size() - 1) != null) {
//				compImps.get(compImps.size() - 1).show();
//				compImps.get(compImps.size() - 1).getWindow().setBackground(this.getBackground());
//				compImps.get(compImps.size() - 1).setTitle(
//						"Sketch3D #" + compImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
//				compImps.get(compImps.size() - 1).getWindow().addWindowListener(this);
//				compImps.get(compImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
//				compImps.get(compImps.size() - 1).getCanvas().addMouseListener(colorLegend);
//				compImps.get(compImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
//			}
//			if (Channels.getInstance() != null)
//				((Channels) Channels.getInstance()).close();
//			IJ.run(compImps.get(compImps.size() - 1), "3D Project...",
//					"projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
//			projYImps.add(WindowManager.getImage("Projections of Sketch3D"));
//			if (splitThem)
//				projYImps.get(projYImps.size() - 1).setDimensions(projYImps.get(projYImps.size() - 1).getNChannels(),
//						projYImps.get(projYImps.size() - 1).getNFrames(),
//						projYImps.get(projYImps.size() - 1).getNSlices());
//			projYImps.get(projYImps.size() - 1).setTitle(
//					"Sketch3D ProjDV #" + projYImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
//			projYImps.get(projYImps.size() - 1).setMotherImp(imp, 1);
//			projYImps.get(projYImps.size() - 1).getWindow().setBackground(this.getBackground());
//			projYImps.get(projYImps.size() - 1).getWindow().addWindowListener(this);
//			projYImps.get(projYImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
//			projYImps.get(projYImps.size() - 1).getCanvas().addMouseListener(colorLegend);
//			projYImps.get(projYImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
//			if (Channels.getInstance() != null)
//				((Channels) Channels.getInstance()).close();
//			ImagePlus flipDupImp = compImps.get(compImps.size() - 1).duplicate();
//			StackReverser sr = new StackReverser();
//			sr.flipStack(flipDupImp);
//			IJ.run(flipDupImp, "Reslice ...", "output=1.000 start=Left");
//			ImagePlus rsImp = IJ.getImage();
//			rsImp.setTitle("tempDupReslice");
//			rsImp.getCalibration().pixelWidth = imp.getCalibration().pixelWidth;
//			rsImp.getCalibration().pixelHeight = imp.getCalibration().pixelWidth;
//			rsImp.getCalibration().pixelDepth = imp.getCalibration().pixelWidth;
//			if (Channels.getInstance() != null)
//				((Channels) Channels.getInstance()).close();
//			IJ.run(rsImp, "3D Project...",
//					"projection=[Nearest Point] axis=Y-Axis initial=0 total=360 rotation=10 lower=1 upper=255 opacity=0 surface=0 interior=0 all");
//			projZImps.add(WindowManager.getImage("Projections of tempDupReslice"));
//			projZImps.get(projZImps.size() - 1).setTitle(
//					"Sketch3D ProjAP #" + projZImps.size() + ":" + imp.getCanvas().droppedGeneUrls.replace("\n", " "));
//			projZImps.get(projZImps.size() - 1).setMotherImp(imp, 1);
//			projZImps.get(projZImps.size() - 1).getWindow().addWindowListener(this);
//			projZImps.get(projZImps.size() - 1).getCanvas().addMouseMotionListener(colorLegend);
//			projZImps.get(projZImps.size() - 1).getCanvas().addMouseListener(colorLegend);
//			projZImps.get(projZImps.size() - 1).getWindow().addWindowFocusListener(colorLegend);
//			StackProcessor sp = new StackProcessor(projZImps.get(projZImps.size() - 1).getStack(),
//					projZImps.get(projZImps.size() - 1).getStack().getProcessor(1));
//			sp.flipHorizontal();
//			projZImps.get(projZImps.size() - 1).setStack(sp.rotateLeft());
//
//			ImageCanvas ic = projZImps.get(projZImps.size() - 1).getCanvas();
//			projZImps.get(projZImps.size() - 1).getWindow().pack();
//			int padH = 1 + projZImps.get(projZImps.size() - 1).getWindow().getInsets().left
//					+ projZImps.get(projZImps.size() - 1).getWindow().getInsets().right
//					+ (projZImps.get(projZImps.size() - 1).getWindow().optionsPanel.isVisible()
//							? projZImps.get(projZImps.size() - 1).getWindow().optionsPanel.getWidth()
//							: 0)
//					+ projZImps.get(projZImps.size() - 1).getWindow().viewButtonPanel.getWidth();
//			int padV = projZImps.get(projZImps.size() - 1).getWindow().getInsets().top
//					+ projZImps.get(projZImps.size() - 1).getWindow().getInsets().bottom
//					+ (projZImps.get(projZImps.size() - 1).getWindow() instanceof StackWindow
//							? ((StackWindow) projZImps.get(projZImps.size() - 1).getWindow()).getNScrollbars()
//									* ((StackWindow) projZImps.get(projZImps.size() - 1).getWindow()).zSelector
//											.getHeight()
//							: 0)
//					+ projZImps.get(projZImps.size() - 1).getWindow().overheadPanel.getHeight();
//			projZImps.get(projZImps.size() - 1).getWindow().setSize(ic.dstWidth + padH, ic.dstHeight + padV);
//
//			flipDupImp.flush();
//			flipDupImp = null;
//			rsImp.close();
//			rsImp.getRoiManager().dispose();
//			rsImp.setIgnoreFlush(false);
//			rsImp.flush();
//			rsImp = null;
//			IJ.wait(1000);
//			if (imp.getCanvas().droppedGeneUrls != null) {
//				IJ.setForegroundColor(255, 255, 255);
//				IJ.run(compImps.get(compImps.size() - 1), "Label...",
//						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
//								+ imp.getCanvas().droppedGeneUrls + " range=[]");
//				compImps.get(compImps.size() - 1).changes = false;
//				IJ.run(projYImps.get(projYImps.size() - 1), "Label...",
//						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
//								+ imp.getCanvas().droppedGeneUrls + " range=[]");
//				projYImps.get(projYImps.size() - 1).changes = false;
//				IJ.run(projZImps.get(projZImps.size() - 1), "Label...",
//						"dimension=Slices format=Text starting=0 interval=1 x=5 y=17 font=12 text="
//								+ imp.getCanvas().droppedGeneUrls + " range=[]");
//				projZImps.get(projZImps.size() - 1).changes = false;
//			}
//			if (splitThem)
//				projZImps.get(projZImps.size() - 1).setDimensions(projZImps.get(projZImps.size() - 1).getNChannels(),
//						projZImps.get(projZImps.size() - 1).getNFrames(),
//						projZImps.get(projZImps.size() - 1).getNSlices());
//			projZImps.get(projZImps.size() - 1).getWindow().setBackground(this.getBackground());
//			if (brainbow) {
//
//				// colorLegend.show();
//				// colorLegend.focusGained(new FocusEvent(IJ.getImage().getWindow(),
//				// FocusEvent.FOCUS_GAINED));
//
//			} else if (splitThem) {
//				IJ.run("Channels Tool...");
//			}
//		}
//	}

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
				if (source instanceof Content3DManager)
					colorLegend = ((Content3DManager) source).getColorLegend(source);

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

//	public void shiftROIsXYZTC() {
//		GenericDialog gd = new GenericDialog("Shift Tags in XYZ");
//		Panel p = new Panel();
//		gd.addPanel(p, GridBagConstraints.CENTER, new Insets(10, 0, 0, 0));
//		gd.addNumericField("X", 0, 0);
//		gd.addNumericField("Y", 0, 0);
//		gd.addNumericField("Z", 0, 0);
//		gd.addNumericField("T", 0, 0);
//		gd.addNumericField("C", 0, 0);
//		gd.showDialog();
//		if (gd.wasCanceled())
//			return;
//		shiftX = (gd.getNextNumber());
//		shiftY = (gd.getNextNumber());
//		shiftZ = ((int) gd.getNextNumber());
//		shiftT = ((int) gd.getNextNumber());
//		shiftC = ((int) gd.getNextNumber());
//		for (ContentInstant contentInstant : getShownContentInstantsAsArray()) {
//			String contentInstantWasName = contentInstant.getName();
//			contentInstant.setLocation((int) (contentInstant.getBounds().getX() + shiftX), (int) contentInstant.getBounds().getY() + shiftY);
////		for (ContentInstant contentInstant:getShownContentInstantsAsArray())
////			contentInstant.setLocation((int)contentInstant.getBounds().getX(), (int)(contentInstant.getBounds().getY() + shiftY));
////		for (ContentInstant contentInstant:getShownContentInstantsAsArray())
//			String rbnoKey = "" + contentInstant.getCPosition() + "_" + contentInstant.getZPosition() + "_" + contentInstant.getTimepoint();
//			String rbnsKey = "" + (contentInstant.getCPosition() + shiftC) + "_" + (contentInstant.getZPosition() + shiftZ) + "_"
//					+ (contentInstant.getTimepoint() + shiftT);
//			ArrayList<ContentInstant> rbnOriginal = contentInstantsByNumbers.get(rbnoKey);
//			ArrayList<ContentInstant> rbnShifted = contentInstantsByNumbers.get(rbnsKey);
//			contentInstant.setPosition(contentInstant.getCPosition(), contentInstant.getZPosition() + shiftZ, contentInstant.getTimepoint() + shiftT);
//			contentInstants.replace(contentInstant.getName(), contentInstant);
//			String contentInstantName = contentInstant.getName();
//			if (rbnOriginal != null) {
//				rbnOriginal.remove(contentInstant);
//				if (rbnShifted == null) {
//					rbnShifted = new ArrayList<ContentInstant>();
//					rbnShifted.add(contentInstant);
//					contentInstantsByNumbers.put(rbnsKey, rbnShifted);
//					IJ.log(contentInstantsByNumbers.containsKey(rbnsKey) + " " + contentInstantsByNumbers.containsValue(rbnShifted) + " "
//							+ rbnShifted.contains(contentInstant));
//				} else {
//					if (contentInstant.getName().contains("~")){
//						rbnShifted.add(0,contentInstant);
//					}else {
//						rbnShifted.add(contentInstant);
//					}
//				}
//				IJ.log(contentInstantsByNumbers.get(rbnsKey).toString());
//				String label ="";
//				if (contentInstant.getName() != null && contentInstant.getName().split("\"").length > 1){
//					label = "\"" + contentInstant.getName().split("\"")[1].trim() + " \"" + "_" +Colors.colorToHexString(contentInstant.getFillColor()) + "_" + contentInstant.getCPosition() + "_"
//							+ contentInstant.getZPosition() + "_" + contentInstant.getTimepoint();
//				}else if (contentInstant.getName() != null){
//					label = "\"" + contentInstant.getName() + " \"" + "_" +Colors.colorToHexString(contentInstant.getFillColor()) + "_" + contentInstant.getCPosition() + "_" + contentInstant.getZPosition() + "_"
//							+ contentInstant.getTimepoint();
//				}
//				label = getUniqueName(label);
//				listModel.set(listModel.indexOf(contentInstantWasName), label);
//				fullListModel.set(fullListModel.indexOf(contentInstantWasName), label);
//				contentInstant.setName(label);
//				contentInstants.remove(contentInstantWasName);
//				contentInstants.put(contentInstant.getName(), contentInstant);
//				
//			}
//		}
//		//showAll(SHOW_ALL);
//
//	}

	public void setFillTransparency(String alpha) {
		for (ContentInstant contentInstant : getShownContentInstantsAsArray()) {
			if (contentInstant.isVisible())
				contentInstant.setTransparency(1f - (Long.parseLong(alpha,16))/255);
			//showAll(SHOW_ALL);

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

//	public void realignByTags() {
//		ContentInstant[] allContentInstants = getFullContentInstantsAsArray();
//		ContentInstant[] shownContentInstants = getShownContentInstantsAsArray();
//		int slices = imp.getNFrames();
//		ArrayList<String> cellTracks = new ArrayList<String>();
//		ArrayList<Integer> cellTrackLengths = new ArrayList<Integer>();
//		ArrayList<String> shownCellTracks = new ArrayList<String>();
//		ArrayList<Integer> shownCellTrackLengths = new ArrayList<Integer>();
//		String recentContentInstantName = "";
//		String[] contentInstantNames = new String[allContentInstants.length];
//		String[] shownContentInstantNames = new String[shownContentInstants.length];
//		int count = 0;
//		for (int ns = 0; ns < contentInstantNames.length; ns++) {
//			contentInstantNames[ns] = allContentInstants[ns].getName();
//			contentInstantNames[ns] = contentInstantNames[ns].split("_")[0]
//					+ (contentInstantNames[ns].split("-").length > 1 ? "-" + contentInstantNames[ns].split("-")[1].split("C")[0] : "") + "C";
//		}
//		for (int ns = 0; ns < shownContentInstantNames.length; ns++) {
//			shownContentInstantNames[ns] = shownContentInstants[ns].getName();
//			shownContentInstantNames[ns] = shownContentInstantNames[ns].split("_")[0]
//					+ (shownContentInstantNames[ns].split("-").length > 1 ? "-" + shownContentInstantNames[ns].split("-")[1].split("C")[0]
//							: "")
//					+ "C";
//		}
//		Arrays.sort(contentInstantNames);
//		Arrays.sort(shownContentInstantNames);
//		for (int r = 0; r <= shownContentInstantNames.length; r++) {
//			String currentContentInstantName = r < shownContentInstantNames.length ? shownContentInstantNames[r] : "";
//			count++;
//			// IJ.log(currentContentInstantName+" "+recentContentInstantName + "
//			// "+(currentContentInstantName.equals(recentContentInstantName)));
//			if (!currentContentInstantName.equals(recentContentInstantName) && recentContentInstantName != "") {
//				shownCellTracks.add(recentContentInstantName);
//				shownCellTrackLengths.add(count);
//				count = 0;
//			}
//			recentContentInstantName = currentContentInstantName;
//		}
//		for (int r = 0; r <= contentInstantNames.length; r++) {
//			String currentContentInstantName = r < contentInstantNames.length ? contentInstantNames[r] : "";
//			count++;
//			// IJ.log(currentContentInstantName+" "+recentContentInstantName + "
//			// "+(currentContentInstantName.equals(recentContentInstantName)));
//			if (!currentContentInstantName.equals(recentContentInstantName) && recentContentInstantName != "") {
//				cellTracks.add(recentContentInstantName);
//				cellTrackLengths.add(count);
//				count = 0;
//			}
//			recentContentInstantName = currentContentInstantName;
//		}
//		int[] zStepShiftX = new int[shownCellTracks.size()];
//		int[] zStepShiftY = new int[shownCellTracks.size()];
//
//		int[] contentInstantCenterX1 = new int[shownCellTracks.size()];
//		int[] contentInstantCenterY1 = new int[shownCellTracks.size()];
//		int[] contentInstantCenterX2 = new int[shownCellTracks.size()];
//		int[] contentInstantCenterY2 = new int[shownCellTracks.size()];
//		int ch = 3;
//
//		for (int s = 1; s <= slices; s++) {
//			int sn = s + 1;
//			for (int n = 0; n < shownCellTracks.size(); n++) {
//				for (ch = 1; ch <= imp.getNChannels(); ch++) {
//
//					String shownCellTrackName = shownCellTracks.get(n);
//					if (true) {
//						String contentInstantName = shownCellTrackName.replace(" \"", " \"_" + ch + "_1_" + s);
//						String contentInstantNextSliceName = shownCellTrackName.replace(" \"", " \"_" + ch + "_1_" + sn);
//						ContentInstant contentInstant = (ContentInstant) contentInstants.get(contentInstantName);
//						ContentInstant contentInstantNextSlice = (ContentInstant) contentInstants.get(contentInstantNextSliceName);
//						if (contentInstant != null) {
//							contentInstantCenterX1[n] = (int) contentInstant.getBounds().getCenterX();
//							contentInstantCenterY1[n] = (int) contentInstant.getBounds().getCenterY();
//						}
//						if (contentInstantNextSlice != null) {
//							contentInstantCenterX2[n] = (int) contentInstantNextSlice.getBounds().getCenterX();
//							contentInstantCenterY2[n] = (int) contentInstantNextSlice.getBounds().getCenterY();
//							zStepShiftX[n] = contentInstantCenterX1[n] - contentInstantCenterX2[n];
//							zStepShiftY[n] = contentInstantCenterY1[n] - contentInstantCenterY2[n];
//						}
//					}
//				}
//			}
//			IJ.log(s + " " + sn);
//			Arrays.sort(zStepShiftX);
//			int meanZStepShiftX = 0;
//			int sumZStepShiftX = 0;
//			int shiftXcount = 0;
//			int maxZStepShiftX = 0;
//			;
//			for (int zshiftX : zStepShiftX) {
//				if (Math.abs(zshiftX) < 300 && Math.abs(zshiftX) > 0) {
//					if (Math.abs(maxZStepShiftX) < Math.abs(zshiftX))
//						maxZStepShiftX = zshiftX;
//					shiftXcount++;
//					sumZStepShiftX = sumZStepShiftX + zshiftX;
//				}
//			}
//			meanZStepShiftX = sumZStepShiftX / shiftXcount;
//			Arrays.sort(zStepShiftY);
//			int meanZStepShiftY = 0;
//			int sumZStepShiftY = 0;
//			int shiftYcount = 0;
//			int maxZStepShiftY = 0;
//			;
//			for (int zshiftY : zStepShiftY) {
//				if (Math.abs(zshiftY) < 300 && Math.abs(zshiftY) > 0) {
//					if (Math.abs(maxZStepShiftY) < Math.abs(zshiftY))
//						maxZStepShiftY = zshiftY;
//					shiftYcount++;
//					sumZStepShiftY = sumZStepShiftY + zshiftY;
//				}
//			}
//			meanZStepShiftY = sumZStepShiftY / shiftYcount;
//			IJ.log("Shift " + sn + " = " + meanZStepShiftX + "," + meanZStepShiftY);
//			for (int n = 0; n < cellTracks.size(); n++) {
//				for (ch = 1; ch <= imp.getNChannels(); ch++) {
//
//					String cellTrackName = cellTracks.get(n);
//					String contentInstantName = cellTrackName.replace(" \"", " \"_" + ch + "_1_" + s);
//					String contentInstantNextSliceName = cellTrackName.replace(" \"", " \"_" + ch + "_1_" + sn);
//
//					ContentInstant contentInstant = (ContentInstant) contentInstants.get(contentInstantName);
//					ContentInstant contentInstantNextSlice = (ContentInstant) contentInstants.get(contentInstantNextSliceName);
//					if (contentInstant != null && contentInstantNextSlice != null) {
//						contentInstantNextSlice.setLocation(
//								(int) (contentInstantNextSlice.getBounds().getCenterX() - (contentInstantNextSlice.getBounds().getWidth() / 2)
//										+ meanZStepShiftX),
//								(int) (contentInstantNextSlice.getBounds().getCenterY()
//										- (contentInstantNextSlice.getBounds().getHeight() / 2) + meanZStepShiftY));
//						imp.updateAndRepaintWindow();
//					}
//				}
//
//			}
//
//		}
//	}

//	public void realignByParameters() {
//		ContentInstant[] allContentInstants = getFullContentInstantsAsArray();
//		String paramString = IJ.openAsString("");
//		String[] paramLines = paramString.split("\n");
//		for (String paramLine : paramLines) {
//			if (paramLine.matches("\\d*\\=\\>.*")) {
//				// IJ.log(paramLine);
//				int slice = Integer.parseInt(paramLine.split("\\=")[0]);
//				double tx = Double.parseDouble(paramLine.split("[\\>,]")[1].split(" ")[0]);
//				double ty = Double.parseDouble(paramLine.split("[\\>,]")[1].split(" ")[1]);
//				double thetaDeg = Double.parseDouble(paramLine.split("[\\=]")[2]);
//				double theta = Math.PI * thetaDeg / 180;
//				double anchorx = imp.getWidth() / 2;
//				double anchory = imp.getHeight() / 2;
//				for (ContentInstant contentInstant : allContentInstants) {
//					if (slice == contentInstant.getTimepoint()) {
//						double x1 = contentInstant.getBounds().getCenter(center);;
//						double y1 = contentInstant.getBounds().getCenterY();
//						double x2 = 0;
//						double y2 = 0;
//						double[] preAffinePoints = { x1, y1 };
//						double[] postAffinePoints = { x2, y2 };
//						AffineTransform at = new AffineTransform();
//						at.setToTranslation(tx, ty);
//						at.transform(preAffinePoints, 0, preAffinePoints, 0, 1);
//						at.setToRotation(-theta, anchorx, anchory);
//						at.transform(preAffinePoints, 0, postAffinePoints, 0, 1);
//						contentInstant.setLocation((int) (postAffinePoints[0] - contentInstant.getBounds().getWidth() / 2),
//								(int) (postAffinePoints[1] - contentInstant.getBounds().getHeight() / 2));
//					}
//				}
//			}
//		}
//	}

	public void valueChanged(ListSelectionEvent e) {
		int index = 0;
		if (listModel.getSize() == 0)
			return;
		if (list.getSelectedIndices().length == 0)
			return;
		index = list.getSelectedIndices()[0];
		if (index < 0)
			index = 0;
		if (univ != null) {
			if (list.getSelectedIndices().length <= 1) {
//				restore(imp, index, true);
				String selectedLabel = list.getSelectedValue();
				String editedLabel = selectedLabel.replace("_#0_#0 \"_0", "").replace("\"", "");
				Content c = ((Content)((Image3DUniverse) univ).getContent(editedLabel));
				if (c.getInstants().containsValue(contentInstants.get(selectedLabel))) {
					((Image3DUniverse)univ).select(c);

				}
			}
			if (record()) {
				if (Recorder.scriptMode())
					Recorder.recordCall("rm.select(imp, " + index + ");");
				else
					Recorder.record("contentInstantManager", "Select", index);
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

//	private void openCsv(String path) { // for Elegance output
//		boolean wasVis = this.isVisible();
//		this.setVisible(false);
//		busy = true;
//		// showAll(SHOW_ALL);
//		String s = IJ.openAsString(path);
//		String[] objectLines = s.split("\n");
//		int fullCount = objectLines.length;
//
//		if ((path.contains("object") || path.contains("innerjoin")) && s.contains("OBJ_Name")) {
//			int[] sliceNumbers = new int[fullCount];
//			for (int f = 0; f < fullCount; f++) {
//				if (objectLines[f].contains("N2UNR"))
//					sliceNumbers[f] = Integer.parseInt(objectLines[f].substring(objectLines[f].indexOf("N2UNR") + 5,
//							objectLines[f].indexOf("N2UNR") + 8));
//				if (objectLines[f].contains("JSHJSH"))
//					sliceNumbers[f] = Integer.parseInt(objectLines[f].substring(objectLines[f].indexOf("JSHJSH") + 6,
//							objectLines[f].indexOf("JSHJSH") + 9));
//				IJ.log("" + sliceNumbers[f]);
//			}
//			// IJ.log(s);
//
//			long count = 0;
//
//			Hashtable<String, String> objectHash = new Hashtable<String, String>();
////			String centerZtestString = objectLines[1].split(",")[4].replace("\"", "");
////			String centerZroot = null;
////			for (int i = 0; i < centerZtestString.length(); i++) {
////				if (objectLines[2].split(",")[4].replace("\"", "").contains(centerZtestString.substring(0, i))
////						&& !centerZtestString.substring(i - 1 >= 0 ? i - 1 : 0, centerZtestString.length() - 1)
////								.matches("\\d*")) {
////					centerZroot = centerZtestString.substring(0, i);
////				}
////			}
//
//			long nContentInstants = 0;
//
//			for (int i = 1; i < objectLines.length; i++) {
//				String objectLine = objectLines[i];
//				objectHash.put(objectLine.split(",")[0].replace("\"", ""), objectLine);
//			}
//			double shrinkFactor = 1 / IJ.getNumber("XY dimension should be reduced in scale by what factor?", 1);
//
//			imp.getWindow().setVisible(false);
//
//			Hashtable<String, ArrayList<String>> synapsePairFrequencyHashtable = new Hashtable<String, ArrayList<String>>();
//			Hashtable<String, ArrayList<String>> synapseNameTallyHashtable = new Hashtable<String, ArrayList<String>>();
//
//			for (int obj = 1; obj < objectLines.length; obj++) {
//				String sObj = objectLines[obj];
//				String[] objChunks = sObj.replace(",,,",",NULL,NULL,").replace(",NULL,NULL,", ",\"NULL\",\"NULL\",").replace(",,",",NULL,").replace(",NULL,", ",\"NULL\",").split(",\"");
//				String objType = objChunks[6].replace("\"", "");
//				String imageNumber = objChunks[4].replace("\"", "");
//				int zSustain = Integer.parseInt(objChunks[21].replace(","," ").replace("\"", "").replace("zS", "").trim());
//				String presynName = objChunks[17].replace("\"", "");
//				String[] postsynNames = objChunks[18].replace("\"", "").split("[,&]");
//
//				String contentInstantNameStart = "\"" + presynName + objType
//						+ Arrays.deepToString(postsynNames).replace("[", "").replace("]", "").replace("_", "").replace(", ", "&");
//				contentInstantNameStart = contentInstantNameStart.replaceAll("(\\[|\\])", "");
//				if (synapseNameTallyHashtable.get(contentInstantNameStart) == null) {
//					synapseNameTallyHashtable.put(contentInstantNameStart, new ArrayList<String>());
//				}
//				Character incChar = 'A';
//				incChar--;
//				for (int c = 0; c <= synapseNameTallyHashtable.get(contentInstantNameStart).size(); c++) {
//					incChar++;
//				}
//				synapseNameTallyHashtable.get(contentInstantNameStart).add(contentInstantNameStart + "~" + incChar + " \"");
//				Color contentInstantColor = objType.contains("chemical") ? Color.white : Color.yellow;
//				if (contentInstantNameStart.contains("uncertain"))
//					contentInstantColor = objType.contains("chemical") ? Color.pink : Color.orange;
//				int centerX = (int) (Integer.parseInt(sObj.split(",")[1].replace("\"", "")) / shrinkFactor);
//				int centerY = (int) (Integer.parseInt(sObj.split(",")[2].replace("\"", "")) / shrinkFactor);
//				String centerZtestString = sObj.split(",")[4].replace("\"", "");
//				String centerZroot = null;
//				for (int i = 0; i < centerZtestString.length(); i++) {
//					if (sObj.split(",")[4].replace("\"", "").contains(centerZtestString.substring(0, i))
//							&& !centerZtestString.substring(i - 1 >= 0 ? i - 1 : 0, centerZtestString.length() - 1)
//									.matches("\\d*")) {
//						centerZroot = centerZtestString.substring(0, i);
//					}
//				}
//				int frontZ = Integer.parseInt(sObj.split(",")[4].replace("\"", "").replace(centerZroot, ("")));
//
//				//// SPECIAL CASE ONLY FOR honoring JSH image GAPS AT Z56 z162-166
//				if (imageNumber.startsWith("JSHJSH")) {
//					if (frontZ > 55) {
//						frontZ++;
//					}
//					if (frontZ > 162) {
//						frontZ++;
//						frontZ++;
//						frontZ++;
//						frontZ++;
//					}
//				}
//				if (imageNumber.startsWith("N2UNR")) {
//					IJ.wait(0);
//				}
//				if (imageNumber.startsWith("N2UVC")) {
//					frontZ = frontZ + 182;
//				}
//				if (imageNumber.startsWith("N2UDC")||imageNumber.startsWith("N2ULEFT")||imageNumber.startsWith("N2URIGHT")) {
//					continue;
//				}
//				int adjustmentZ = 0;
////				
//	zSustain =1;		//choice to eliminate stupid depth traces (highly inaccurate in several ways.	
////				
//				for (int susStep = 0; susStep < zSustain; susStep++) {
//					int plotZ = frontZ + susStep - adjustmentZ;
//					if (plotZ < 1 || plotZ > imp.getNSlices()) {
//						continue;
//					}
//
//					if (contentInstantNameStart.contains("|cell")) {
//					} else {
//
//						int contentInstantDiameter = (int) (25/shrinkFactor);
//						ContentInstant oContentInstant = new OvalContentInstant(centerX - contentInstantDiameter / 2, centerY - contentInstantDiameter / 2, contentInstantDiameter,
//								contentInstantDiameter);
//						// ContentInstant oContentInstant= new ContentInstant(centerX-contentInstantDiameter/2, centerY-contentInstantDiameter/2, contentInstantDiameter,
//						// contentInstantDiameter);
//						oContentInstant.setName(contentInstantNameStart + "~" + incChar + " \"");
//						imp.getRoiManager().setRoiFillColor(oContentInstant, contentInstantColor);
//						oContentInstant.setPosition(1, plotZ, 1);
////						imp.setPosition(1, plotZ, 1);
////						this.addContentInstant(oContentInstant);
//						this.addContentInstant(oContentInstant, false, oContentInstant.getFillColor(), -1, false);
//						for (String postsynName : postsynNames) {
//							IJ.log(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
//							if (synapsePairFrequencyHashtable
//									.get(presynName + "," + postsynName + "," + objType) == null) {
//								synapsePairFrequencyHashtable.put(presynName + "," + postsynName + "," + objType,
//										new ArrayList<String>());
//							}
//							synapsePairFrequencyHashtable.get(presynName + "," + postsynName + "," + objType)
//									.add(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
//						}
//					}
//				}
//			count++;
//			IJ.showStatus("" + count + "/" + fullCount + " Tags loaded for " + imp.getTitle());
//			}
//			updateShowAll();
//			// this.imp.setTitle(impTitle);
//			imp.getWindow().setVisible(true);
//			this.setVisible(wasVis);
//			busy = false;
//			for (Object key : synapsePairFrequencyHashtable.keySet()) {
//				IJ.log(((String) key) + "=" + synapsePairFrequencyHashtable.get((String) key).size());
//			}
//		} else if (path.contains("synapse_key")) {
//
//			int[] sliceNumbers = new int[fullCount];
//			String[] pres = new String[fullCount];
//			String[][] posts = new String[fullCount][];
//			String[] segments = new String[fullCount];
//			String[] xs = new String[fullCount];
//			String[] ys = new String[fullCount];
//			String[] zs = new String[fullCount];
//
//			for (int f = 0; f < fullCount; f++) {
//				if (objectLines[f].startsWith("Presynaptic"))
//					continue;
//				if (objectLines[f].contains("\"")) {
//					sliceNumbers[f] = Integer.parseInt(objectLines[f].split("\"")[2].split(",")[4]);
//					pres[f] = objectLines[f].split(",")[0];
//					posts[f] = objectLines[f].split("\"")[1].split(",");
//					segments[f] = objectLines[f].split("\"")[2].split(",")[1];
//					xs[f] = objectLines[f].split("\"")[2].split(",")[2];
//					ys[f] = objectLines[f].split("\"")[2].split(",")[3];
//					zs[f] = objectLines[f].split("\"")[2].split(",")[4];
//				} else {
//
//					sliceNumbers[f] = Integer.parseInt(objectLines[f].split(",")[5]);
//					pres[f] = objectLines[f].split(",")[0];
//					posts[f] = new String[] { objectLines[f].split(",")[1] };
//					segments[f] = objectLines[f].split(",")[2];
//					xs[f] = objectLines[f].split(",")[3];
//					ys[f] = objectLines[f].split(",")[4];
//					zs[f] = objectLines[f].split(",")[5];
//				}
//				IJ.log("" + sliceNumbers[f]);
//			}
//			// IJ.log(s);
//
//			long count = 0;
//
//			Hashtable<String, String> objectHash = new Hashtable<String, String>();
////			String centerZtestString = objectLines[1].split(",")[4].replace("\"", "");
////			String centerZroot = null;
////			for (int i=0; i<centerZtestString.length(); i++){
////				if (objectLines[2].split(",")[4].replace("\"", "")
////						.contains(centerZtestString.substring(0,i))
////						&& !centerZtestString.substring(i-1>=0?i-1:0,centerZtestString.length()-1).matches("\\d*")) {
////					centerZroot = centerZtestString.substring(0,i);
////				}
////			}
//
//			long nContentInstants = 0;
//
//			for (int i = 0; i < objectLines.length; i++) {
//				String objectLine = objectLines[i];
//				if (pres[i] != null)
//					objectHash.put(pres[i], objectLine);
//			}
//			double shrinkFactor = 1 / IJ.getNumber("XY dimension should be reduced in scale by what factor?", 1);
//			shrinkFactor = shrinkFactor * imp.getCalibration().pixelWidth;
//
//			imp.getWindow().setVisible(false);
//
//			Hashtable<String, ArrayList<String>> synapsePairFrequencyHashtable = new Hashtable<String, ArrayList<String>>();
//			Hashtable<String, ArrayList<String>> synapseNameTallyHashtable = new Hashtable<String, ArrayList<String>>();
//
//			for (int obj = 0; obj < objectLines.length; obj++) {
//				if (objectLines[obj].startsWith("Presynaptic"))
//					continue;
//				count++;
//				IJ.showStatus("" + count + "/" + fullCount + " Tags loaded for " + imp.getTitle());
//				String sObj = objectLines[obj];
//				String objType = "undefined";
//				String imageNumber = zs[obj];
//				int zSustain = 1;
//				String presynName = pres[obj];
//				String[] postsynNames = posts[obj];
//
//				String contentInstantNameStart = "\"" + presynName + objType
//						+ Arrays.deepToString(postsynNames).replace("[", "").replace("]", "").replace(", ", "&");
//				contentInstantNameStart = contentInstantNameStart.replaceAll("(\\[|\\])", "");
//				if (synapseNameTallyHashtable.get(contentInstantNameStart) == null) {
//					synapseNameTallyHashtable.put(contentInstantNameStart, new ArrayList<String>());
//				}
//				Character incChar = 'A';
//				incChar--;
//				for (int c = 0; c <= synapseNameTallyHashtable.get(contentInstantNameStart).size(); c++) {
//					incChar++;
//				}
//				synapseNameTallyHashtable.get(contentInstantNameStart).add(contentInstantNameStart + "~" + incChar + " \"");
//				Color contentInstantColor = objType.contains("chemical") ? Color.white : Color.yellow;
//				if (contentInstantNameStart.contains("uncertain"))
//					contentInstantColor = objType.contains("chemical") ? Color.pink : Color.orange;
//				int centerX = (int) (Integer.parseInt(xs[obj]) / shrinkFactor);
//				int centerY = (int) (Integer.parseInt(ys[obj]) / shrinkFactor);
//				int frontZ = Integer.parseInt(zs[obj]);
//
//				//// SPECIAL CASE ONLY FOR honoring JSH image GAPS AT Z56 z162-166
//				if (imageNumber.startsWith("JSHJSH")) {
//					if (frontZ > 55) {
//						frontZ++;
//					}
//					if (frontZ > 162) {
//						frontZ++;
//						frontZ++;
//						frontZ++;
//						frontZ++;
//					}
//				}
//
//				int adjustmentZ = 0;
//				for (int susStep = 0; susStep < zSustain; susStep++) {
//					int plotZ = frontZ + susStep - adjustmentZ;
//					if (plotZ < 1 || plotZ > imp.getNSlices()) {
//						continue;
//					}
//
//					if (contentInstantNameStart.contains("|cell")) {
//					} else {
//
//						int contentInstantDiameter = (int) (25/shrinkFactor);
//						ContentInstant oContentInstant = new OvalContentInstant(centerX - contentInstantDiameter / 2, centerY - contentInstantDiameter / 2, contentInstantDiameter,
//								contentInstantDiameter);
//
//						oContentInstant.setName(contentInstantNameStart + "~" + incChar + " \"");
//						imp.getRoiManager().setRoiFillColor(oContentInstant, contentInstantColor);
//						oContentInstant.setPosition(1, plotZ, 1);
////						imp.setPosition(1, plotZ, 1);
////						this.addContentInstant(oContentInstant);
//						this.addContentInstant(oContentInstant, false, oContentInstant.getFillColor(), -1, false);
//						for (String postsynName : postsynNames) {
//							IJ.log(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
//							if (synapsePairFrequencyHashtable
//									.get(presynName + "," + postsynName + "," + objType) == null) {
//								synapsePairFrequencyHashtable.put(presynName + "," + postsynName + "," + objType,
//										new ArrayList<String>());
//							}
//							synapsePairFrequencyHashtable.get(presynName + "," + postsynName + "," + objType)
//									.add(presynName + "," + postsynName + "," + plotZ + "," + 1 + "," + objType);
//						}
//					}
//				}
//			}
//			updateShowAll();
//			// this.imp.setTitle(impTitle);
//			imp.getWindow().setVisible(true);
//			this.setVisible(wasVis);
//			busy = false;
//			for (Object key : synapsePairFrequencyHashtable.keySet()) {
//				IJ.log(((String) key) + "=" + synapsePairFrequencyHashtable.get((String) key).size());
//			}
//
//		} else {
//			this.setVisible(true);
//			return;
//		}
//	}

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

	public void zapDuplicateContentInstants() {
		ContentInstant[] ra = this.getFullContentInstantsAsArray();
		for (int r = 0; r < ra.length; r++) {
			ContentInstant contentInstant = ra[r];
			for (int s = ra.length - 1; s > r; s--) {
				ContentInstant contentInstant2 = ra[s];
				if (contentInstant.getName() != (contentInstant2).getName()) {
					boolean duplicated = contentInstant.equals(contentInstant2);
					if (duplicated) {
						IJ.log("zap " + contentInstant2.getName());
						contentInstants.remove(listModel.getElementAt(s));
						fullListModel.removeElement(listModel.getElementAt(s));
						listModel.remove(s);
						textCountLabel.setText("" + listModel.size() + "/" + fullListModel.size());
						if (imp.getWindow() != null) {
							imp.getWindow().countLabel.setText("" + listModel.size() + "/" + fullListModel.size() + "");
							imp.getWindow().countLabel.repaint();
							// imp.getWindow().tagsButton.setText(""+fullListModel.size());

							imp.getWindow().tagsButton.repaint();
						}
						ra = this.getFullContentInstantsAsArray();
						s--;
						r--;
					}
				}
			}
		}
	}

// ALT VERSION OF ZAP DUPS THAT DOES NOT SEEM TO WORK RIGHT BUT IS FASTER....
//	ContentInstant[] ra = this.getFullContentInstantsAsArray();
//	ArrayList<Integer> indexesToDelete = new ArrayList<Integer>();
//	for (int r=0;r<ra.length;r++) {
//		ContentInstant contentInstant= ra[r];
//		if (!indexesToDelete.contains(r)){
//			for (int s=ra.length-1;s>r;s--) {
//				ContentInstant contentInstant2= ra[s];
//				if (contentInstant.getName() != (contentInstant2).getName()){
//					boolean duplicated = contentInstant.equals(contentInstant2);
//					if (duplicated) {
//						//						IJ.log("zap "+ contentInstant2.getName());
//						indexesToDelete.add(s);
//
//						//						contentInstants.remove(listModel.getElementAt(s));
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
//						//						ra = this.getFullContentInstantsAsArray();
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

//	public void importStarryNiteNuclei(String zipPath) {
//		File zipFile = new File(zipPath);
//		if (!zipFile.canRead()) {
//
//			zipPath = IJ.getFilePath("Select the zip file with <nuclei> files");
//			zipFile = new File(zipPath);
//		}
//		boolean useGivenNames = zipPath.endsWith("_ATAutoCorrected.zip") || zipPath.toLowerCase().endsWith("_atac.zip");
//		String str = openZipNucleiAsString(zipPath);
//		String[] lines = str.split("\n");
//		// imp.hide();
//		// this.setVisible(false);
//		int frame = 1;
//		Hashtable<String, String[]> prevHash = new Hashtable<String, String[]>();
//		Hashtable<String, String[]> nextHash = new Hashtable<String, String[]>();
//
//		ArrayList<String> newNames = new ArrayList<String>();
//
//		list.setModel(new DefaultListModel<String>());
//
//		for (int i = 0; i < lines.length; i++) {
//			String nextLine = lines[i];
//			if (nextLine.length() == 0) {
//				continue;
//			}
//			if (nextLine.startsWith("parameters/")) {
//				continue;
//			}
//			String[] cellData = nextLine.split(", *");
//			if (cellData.length == 1) {
//				if (cellData[0].equals("nuclei/"))
//					continue;
//				frame = Integer.parseInt(cellData[0].trim().replace("nuclei/t", "").replace("-nuclei", ""));
//				prevHash = nextHash;
//				nextHash = new Hashtable<String, String[]>();
//			} else {
//				// imp.setPositionWithoutUpdate(imp.getChannel(), imp.getSlice(), frame);
//				if (Integer.parseInt(cellData[1].trim()) != 0) {
//					nextHash.put(cellData[0], new String[] { cellData[2], cellData[3], cellData[4], cellData[9] });
//					// imp.setPositionWithoutUpdate(imp.getChannel(),
//					// (int)Double.parseDouble(cellData[7].trim()), frame);
//
//					ContentInstant newOval = new OvalContentInstant(
//							Integer.parseInt(cellData[5].trim()) - Integer.parseInt(cellData[8].trim()) / 2,
//							Integer.parseInt(cellData[6].trim()) - Integer.parseInt(cellData[8].trim()) / 2,
//							Integer.parseInt(cellData[8].trim()), Integer.parseInt(cellData[8].trim()));
//					newOval.setImage(imp);
//					newOval.setPosition(imp.getChannel(), (int) Double.parseDouble(cellData[7].trim()), frame);
//					addContentInstant(newOval, false, Color.white, 1, false);
//					String currID = cellData[0];
//					String prevCell = cellData[2];
//					String[] prevCellThings = null;
//					String prevCellDesc1 = "";
//					String prevCellDesc2 = "";
//
//					if (prevHash != null && !useGivenNames) {
//						if (prevHash.get(cellData[2]) != null) {
//							prevCellThings = prevHash.get(cellData[2]);
//							if (prevCellThings[1].equalsIgnoreCase(cellData[0])) {
//								prevCellDesc1 = prevCellThings[1];
//								prevCellDesc2 = prevCellThings[2];
//								if (!prevCellThings[2].equalsIgnoreCase("-1")) {
//									newNames.add(prevCellThings[3] + "g");
////									rename(prevCellThings[3]+"g", new int[] {this.getCount()-1}, false);
//									nextHash.put(cellData[0], new String[] { cellData[2], cellData[3], cellData[4],
//											prevCellThings[3] + "g" });
//
//								} else {
//									newNames.add(prevCellThings[3]);
////									rename(prevCellThings[3], new int[] {this.getCount()-1}, false);
//									nextHash.put(cellData[0],
//											new String[] { cellData[2], cellData[3], cellData[4], prevCellThings[3] });
//
//								}
//							} else if (prevCellThings[2].equalsIgnoreCase(cellData[0])) {
//								newNames.add(prevCellThings[3] + "h");
////								rename(prevCellThings[3]+"h", new int[] {this.getCount()-1}, false);
//								nextHash.put(cellData[0], new String[] { cellData[2], cellData[3], cellData[4],
//										prevCellThings[3] + "h" });
//							}
//						} else {
//							newNames.add(cellData[9]);
////							rename(cellData[9], new int[] {this.getCount()-1}, false);
//						}
//					} else {
//						newNames.add(cellData[9]);
////						rename(cellData[9], new int[] {this.getCount()-1}, false);
//					}
//
//					IJ.showStatus("importing nucleus " + getCount());
//				}
//			}
//		}
//
//		String[] newNamesArray = newNames.toArray(new String[newNames.size()]);
//		int[] nameIndexes = new int[newNames.size()];
//		for (int n = 0; n < nameIndexes.length; n++) {
//			nameIndexes[n] = n;
//		}
//		list.setModel(listModel);
//		rename(newNamesArray, nameIndexes, false);
//
//		if (this.getCount() > 0) {
//			select(-1);
//			saveMultiple(getAllShownIndexes(), zipFile.getParent() + File.separator + "NucleiContentInstantSet.zip");
//		}
//
////		if (!(zipPath.contains("Unskipped.zip"))){
////			respaceStarryNiteNuclei(zipPath, 6);
////		}
//
//	}

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

//	public void exportROIsAsZippedStarryNiteNuclei(String zipPath) {
//
//		File zipFile = new File(zipPath);
//		String configPath = zipPath.replace("Unskipped.zip", "edited.xml");
//		sortmode = 3;
//		if (listModel.size() > 0 && listModel.get(0).split("_").length == 5) {
//			if (sortmode > 0)
//				sortmode++;
//		}
//		this.sort();
//
//		ContentInstant[] contentInstants = this.getSelectedContentInstantsAsArray();
//		if (contentInstants.length == 0) {
//			contentInstants = this.getFullContentInstantsAsArray();
//		}
//
//		int maxT = 0;
//		int padLength = 0;
//		for (ContentInstant contentInstant : contentInstants) {
//			if (maxT < contentInstant.getTimepoint()) {
//				maxT = contentInstant.getTimepoint();
//				padLength = ("" + maxT).length();
//			}
//		}
//		String nucString = "parameters/\n";
//
//		int frame = 1;
//
//		Hashtable<String, Integer> NameTimeToTnumberHashtable = new Hashtable<String, Integer>();
//
//		for (int t = 1; t <= maxT; t++) {
//			int hitCountA = 0;
//			for (ContentInstant contentInstant : contentInstants) {
//				if (contentInstant.getTimepoint() == t && !contentInstant.getName().contains("polar")) {
//					hitCountA++;
//					NameTimeToTnumberHashtable.put(contentInstant.getName().split("\"")[1].trim() + "_" + t, hitCountA);
//				}
//			}
//		}
//
//		for (int t = 1; t <= maxT; t++) {
//			nucString = nucString + "\nnuclei/t" + IJ.pad(t, padLength) + "-nuclei\n";
//			int hitCountB = 0;
//			for (ContentInstant contentInstant : contentInstants) {
//				String contentInstantNameTrim = contentInstant.getName().split("\"")[1].trim();
//				if (contentInstant.getTimepoint() == t && !contentInstant.getName().contains("polar") && !contentInstant.getName().contains("Oval")
//						&& !contentInstant.getName().contains("\"\"") && !contentInstantNameTrim.equals("")) {
//					hitCountB++;
//					boolean alreadyNextHit = false;
//					int prevHit = -1;
//					int nextHit = -1;
//					int otherNextHit = -1;
//					if (NameTimeToTnumberHashtable.get(contentInstantNameTrim + "_" + (t - 1)) != null) {
//						prevHit = NameTimeToTnumberHashtable.get(contentInstantNameTrim + "_" + (t - 1));
//					}
//					if (NameTimeToTnumberHashtable
//							.get(contentInstantNameTrim.substring(0, contentInstantNameTrim.length() - 1) + "_" + (t - 1)) != null) {
//						prevHit = NameTimeToTnumberHashtable
//								.get(contentInstantNameTrim.substring(0, contentInstantNameTrim.length() - 1) + "_" + (t - 1));
//					}
//
//					if (contentInstantNameTrim.equals("E")) {
//						if (NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1));
//						}
//					}
//					if (contentInstantNameTrim.equals("MS")) {
//						if (NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("EMS" + "_" + (t - 1));
//						}
//					}
//
//					if (contentInstantNameTrim.equals("P3")) {
//						if (NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1));
//						}
//					}
//					if (contentInstantNameTrim.equals("C")) {
//						if (NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("P2" + "_" + (t - 1));
//						}
//					}
//
//					if (contentInstantNameTrim.equals("P4")) {
//						if (NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1));
//						}
//					}
//					if (contentInstantNameTrim.equals("D")) {
//						if (NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("P3" + "_" + (t - 1));
//						}
//					}
//					if (contentInstantNameTrim.equals("Z2")) {
//						if (NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1));
//						}
//					}
//					if (contentInstantNameTrim.equals("Z3")) {
//						if (NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1)) != null) {
//							prevHit = NameTimeToTnumberHashtable.get("P4" + "_" + (t - 1));
//						}
//					}
//
//					if (NameTimeToTnumberHashtable.get(contentInstantNameTrim + "_" + (t + 1)) != null) {
//						nextHit = NameTimeToTnumberHashtable.get(contentInstantNameTrim + "_" + (t + 1));
//					}
//
//					if (contentInstantNameTrim.equals("EMS")) {
//						if (NameTimeToTnumberHashtable.get("E" + "_" + (t + 1)) != null) {
//							nextHit = NameTimeToTnumberHashtable.get("E" + "_" + (t + 1));
//						}
//						if (NameTimeToTnumberHashtable.get("MS" + "_" + (t + 1)) != null) {
//							otherNextHit = NameTimeToTnumberHashtable.get("MS" + "_" + (t + 1));
//						}
//					}
//
//					if (contentInstantNameTrim.equals("P2")) {
//						if (NameTimeToTnumberHashtable.get("P3" + "_" + (t + 1)) != null) {
//							nextHit = NameTimeToTnumberHashtable.get("P3" + "_" + (t + 1));
//						}
//						if (NameTimeToTnumberHashtable.get("C" + "_" + (t + 1)) != null) {
//							otherNextHit = NameTimeToTnumberHashtable.get("C" + "_" + (t + 1));
//						}
//					}
//
//					if (contentInstantNameTrim.equals("P3")) {
//						if (NameTimeToTnumberHashtable.get("P4" + "_" + (t + 1)) != null) {
//							nextHit = NameTimeToTnumberHashtable.get("P4" + "_" + (t + 1));
//						}
//						if (NameTimeToTnumberHashtable.get("D" + "_" + (t + 1)) != null) {
//							otherNextHit = NameTimeToTnumberHashtable.get("D" + "_" + (t + 1));
//						}
//					}
//					if (contentInstantNameTrim.equals("P4")) {
//						if (NameTimeToTnumberHashtable.get("Z2" + "_" + (t + 1)) != null) {
//							nextHit = NameTimeToTnumberHashtable.get("Z2" + "_" + (t + 1));
//						}
//						if (NameTimeToTnumberHashtable.get("Z3" + "_" + (t + 1)) != null) {
//							otherNextHit = NameTimeToTnumberHashtable.get("Z3" + "_" + (t + 1));
//						}
//					}
//
//					for (String suffix : new String[] { "a", "p", "m", "n", "l", "r", "g", "h" }) {
//						if (!alreadyNextHit
//								&& NameTimeToTnumberHashtable.get(contentInstantNameTrim + suffix + "_" + (t + 1)) != null) {
//							nextHit = NameTimeToTnumberHashtable.get(contentInstantNameTrim + suffix + "_" + (t + 1));
//							alreadyNextHit = true;
//						}
//						if (alreadyNextHit
//								&& NameTimeToTnumberHashtable.get(contentInstantNameTrim + suffix + "_" + (t + 1)) != null) {
//							otherNextHit = NameTimeToTnumberHashtable.get(contentInstantNameTrim + suffix + "_" + (t + 1));
//						}
//					}
//					String cellString = "" + hitCountB + ",  1,  " + prevHit + ",  " + nextHit + ",  " + otherNextHit
//							+ ",  " + (int) contentInstant.getBounds().getCenterX() + ",  " + (int) contentInstant.getBounds().getCenterY()
//							+ ",  " + contentInstant.getZPosition() + ",  " + contentInstant.getBounds().width + ",  " + contentInstantNameTrim
//							+ ",  0,  0, 0, 0, , 0, 0, 0, 0, 0,";
//					nucString = nucString + cellString + "\n";
//				}
//
//			}
//		}
//		nucString = nucString + "\nnuclei/t" + "end" + "-nuclei\n";
//		File oldZipFile = new File(zipPath);
//		if (oldZipFile.canRead()) {
//			oldZipFile.renameTo(new File(zipPath.replace("Unskipped", "Unskipped" + oldZipFile.lastModified())));
//		}
//		IJ.log(nucString);
//		saveZipNuclei(nucString, zipPath);
//		File pngFile = new File(zipPath.replace(".zip", "Lineage.png"));
//		String[] paramStrings = new String[] { "P0", "" + maxT, "-500", "5000", "10", "2", "0" };
////		if (aceTree != null){
////			aceTree.exit();
////		}
//		if (imp.getStack() instanceof ImageStack) {
//			aceTree = AceTree.getAceTree(imp, zipPath);
//		} else if (new File(configPath).canRead()) {
//			aceTree = AceTree.getAceTree(configPath);
//		}
//		if (aceTree != null) {
//			VTreeImpl vti = aceTree.getVtree().getiVTreeImpl();
//			vti.printTree(paramStrings, true, true, pngFile.getName(), pngFile.getParent());
//			vti.showTree(paramStrings, true, true);
//			vti.getTestCanvas().addMouseListener(this);
//		}
//
//		aceTree.saveNuclei(new File(zipPath.replace(".zip", "_ATAutoCorrected.zip")));
//
//	}

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

	public double getContentInstantRescaleFactor() {
		return contentInstantRescaleFactor;
	}

	public void setContentInstantRescaleFactor(double contentInstantRescaleFactor) {
		this.contentInstantRescaleFactor = contentInstantRescaleFactor;
		isContentInstantScaleFactorSet = true;

	}

//	public void mapNearNeighborContacts() {
//		ContentInstant[] selContentInstants = this.getSelectedContentInstantsAsArray();
//		expansionDistance = IJ.getNumber("Distance for contact partner search", expansionDistance);
//		ArrayList<String> cellsAlreadyMapped = new ArrayList<String>();
//		for (ContentInstant contentInstant : selContentInstants) {
//			if (cellsAlreadyMapped.contains(contentInstant.getName().split("\"")[1])) {
//				continue;
//			}
//			for (ContentInstant queryContentInstant : this.getROIsByName().get("\"" + contentInstant.getName().split("\"")[1] + "\"")) {
//				if (!queryContentInstant.getName().split("\"")[1].equalsIgnoreCase(contentInstant.getName().split("\"")[1])) {
//					continue;
//				}
//				int cPos = queryContentInstant.getCPosition();
//				int zPos = queryContentInstant.getZPosition();
//				int tPos = queryContentInstant.getTimepoint();
//				ContentInstant dupContentInstant = (ContentInstant) queryContentInstant.cloneNode(true);
//
//				ContentInstant[] sameSliceContentInstants = this.getROIsByNumbers().get("" + cPos + "_" + zPos + "_" + tPos)
//						.toArray(new ContentInstant[1]);
//				for (ContentInstant testContentInstant : sameSliceContentInstants) {
//					if (zPos != testContentInstant.getZPosition()
//							|| queryContentInstant.getName().split("\"")[1].equalsIgnoreCase(testContentInstant.getName().split("\"")[1])
//							|| testContentInstant.getName().split("\"")[1].trim().contains("by")) {
//						continue;
//					}
//					Color testColor = testContentInstant.getFillColor();
//					String andName = "" + queryContentInstant.getName().split("\"")[1].trim() + "by"
//							+ testContentInstant.getName().split("\"")[1].trim();
//
//					ContentInstant scaledContentInstant = null;
//
//					scaledContentInstant = ContentInstantEnlarger.enlarge(testContentInstant, expansionDistance);
//
//					ContentInstant andContentInstant = (new ShapeContentInstant(scaledContentInstant).and(new ShapeContentInstant(dupContentInstant)));
//					if (andContentInstant != null && andContentInstant.getBounds().getWidth() > 0) {
//
//						andContentInstant.setName(andName);
//
//						andContentInstant.setPosition(cPos, zPos, tPos);
//						imp.getRoiManager().setRoiFillColor(andContentInstant, testColor);
//						this.addContentInstant(andContentInstant, false, testColor, -1, false);
//					}
//				}
//			}
//			cellsAlreadyMapped.add(contentInstant.getName().split("\"")[1]);
//		}
//	}

//	public void colorTagsByGroupInteractionRules() {
//
//		String xlsPath = IJ.getFilePath("Table of Cell Groups?");
//		String[] tableRows = IJ.openAsString(xlsPath).split("\n");
//		String rowSplitter = "\t";
//		if (xlsPath.endsWith(".csv")) {
//			rowSplitter = ",";
//		}
//		String[][] table2Darray = new String[tableRows[0].split(rowSplitter).length][tableRows.length];
//		for (int row = 0; row < tableRows.length; row++) {
//			String[] rowChunks = tableRows[row].split(rowSplitter);
//			for (int col = 0; col < rowChunks.length; col++) {
//				table2Darray[col][row] = rowChunks[col];
//			}
//		}
//
//		int header = 0;
//		int leftMargin = 0;
//		if (table2Darray[0][0].equalsIgnoreCase("Group")) {
//			header++;
//			leftMargin++;
//			if (table2Darray[0][1].equalsIgnoreCase("Color")) {
//				header++;
//			}
//		}
//
//		String[][] allBundleArrays = new String[tableRows[0].split(rowSplitter).length - leftMargin][tableRows.length
//				- header];
//		String[] allBundleNames = new String[tableRows[0].split(rowSplitter).length - leftMargin];
//		String[] allBundleColorStrings = new String[tableRows[0].split(rowSplitter).length - leftMargin];
//		for (int c = 0; c < tableRows[0].split(rowSplitter).length - leftMargin; c++) {
//			for (int r = 0; r < tableRows.length - header; r++) {
//				if (table2Darray[c + leftMargin][r + header] != null
//						&& table2Darray[c + leftMargin][r + header].length() > 0) {
//					allBundleArrays[c][r] = table2Darray[c + leftMargin][r + header];
//				}
//			}
//			if (header > 0)
//				allBundleNames[c] = table2Darray[c + leftMargin][0];
//			if (header == 2)
//				allBundleColorStrings[c] = table2Darray[c + leftMargin][1];
//		}
//		String[][] specificBundleArrays = allBundleArrays;
//
//		for (int i = 0; i < specificBundleArrays.length; i++) {
//			String[] currentBundleArray = specificBundleArrays[i];
//			if (currentBundleArray == null)
//				continue;
//			Content3DManager specificBundleRM = new Content3DManager(null, true);
//			ArrayList<ArrayList<String>> synapseByBundles = new ArrayList<ArrayList<String>>();
//			// Add a labeling first entry to each color pairing set...
//			for (int j = 0; j < allBundleArrays.length; j++) {
//				synapseByBundles.add(new ArrayList<String>());
//				synapseByBundles.get(synapseByBundles.size() - 1)
//						.add("" + allBundleColorStrings[i] + "-" + allBundleColorStrings[j] + ">>");
//			}
//			ArrayList<ContentInstant> newContentInstants = new ArrayList<ContentInstant>();
//			for (String synapseContentInstantName : contentInstants.keySet()) {
//				if (synapseContentInstantName == null)
//					continue;
//				boolean presynInBundle = false;
//				boolean postsynOutsideOfBundle = true;
//				String synapseContentInstantNameCleaned = synapseContentInstantName.replace("\"", "").replace("[", "").replace("]", "");
//				String rootName = synapseContentInstantName.contains("\"") ? synapseContentInstantName.split("\"")[1].trim() : "";
//				rootName = rootName.contains(" ") ? rootName.split("[_\\- ]")[0].trim() : rootName;
//				String[] postSynapticCells = synapseContentInstantNameCleaned.split("_")[0]
//						.replaceAll(".*(electrical|chemical)(.*)", "$2").split("\\&");
//				if (postSynapticCells[0].contains("by")) {
//					postSynapticCells[0] = postSynapticCells[0].split("by")[1].trim();
//				}
//				for (String currentBundleNeuron : currentBundleArray) {
//					if (currentBundleNeuron == null)
//						continue;
//					if (synapseContentInstantNameCleaned.startsWith(currentBundleNeuron)) {
//						presynInBundle = true;
//						boolean noHitInBundle = true;
//						for (String postSC : postSynapticCells) {
//							for (String nextBundleNeuron : currentBundleArray) {
//								if (postSC.equals(nextBundleNeuron)) {
//									noHitInBundle = false;
//								}
//							}
//						}
//						postsynOutsideOfBundle = postsynOutsideOfBundle && noHitInBundle;
//					}
//				}
//				if (presynInBundle) {
//					if (false) { // !postsynOutsideOfBundle){
//						imp.getRoiManager().setRoiFillColor(contentInstants.get(synapseContentInstantName),
//								Colors.getColor(allBundleColorStrings[i], Color.DARK_GRAY));
//						for (int ba = 0; ba < allBundleArrays.length; ba++) {
//							if (allBundleArrays[ba] == currentBundleArray) {
//								if (!synapseByBundles.get(ba).contains(rootName)) {
//									synapseByBundles.get(ba).add(rootName);
//								}
//							}
//						}
//					} else {
//						for (int ba = 0; ba < allBundleArrays.length; ba++) {
//							String[] targetBundleArray = allBundleArrays[ba];
//							if (targetBundleArray == null)
//								continue;
//							for (String targetBundleNeuron : targetBundleArray) {
//								if (targetBundleNeuron == null)
//									continue;
//								int[][] psps = new int[][] { { -1, -1 }, { +1, +1 }, { -1, +1 }, { +1, -1 }, };
//								int psc = 0;
//								int shift = postSynapticCells.length > 1 ? 5 : 0;
//								for (String postSC : postSynapticCells) {
//
//									if (postSC == null)
//										continue;
//									if (postSC.equals(targetBundleNeuron)/* && postsynOutsideOfBundle */) {
//										ContentInstant newContentInstant = ((ContentInstant) contentInstants.get(synapseContentInstantName).cloneNode(true));
//										newContentInstant.setLocation(
//												contentInstants.get(synapseContentInstantName).getBounds().getCenter(new Point3f()).x + psps[psc][0] * shift,
//												contentInstants.get(synapseContentInstantName).getBounds().getCenter().y + psps[psc][1] * shift);
//										imp.getRoiManager().setRoiFillColor(newContentInstant,
//												Colors.getColor(allBundleColorStrings[ba], Color.DARK_GRAY));
//										newContentInstants.add(newContentInstant);
//										if (!synapseByBundles.get(ba).contains(rootName)) {
//											synapseByBundles.get(ba).add(rootName);
//										}
//									}
//									psc++;
//								}
//							}
//
//						}
//					}
//
//					if (!Image3DUniverse.universes.isEmpty()) {
//						for (Image3DUniverse univ : Image3DUniverse.universes) {
//							if (univ.getContent(rootName) != null) {
//								univ.getContent(rootName)
//										.setColor(new Color3f(contentInstants.get(synapseContentInstantName).getFillColor()));
//								univ.getContent(rootName).setTransparency(0.0f);
//								;
//							}
//						}
//					}
//				} else {
////					Color contentInstantColor= synapseContentInstantName.contains("chemical")?Color.white:Color.yellow;
////					if (synapseContentInstantName.contains("uncertain")){
////						contentInstantColor= synapseContentInstantName.contains("chemical")?Color.pink:Color.orange;
////					}
////					contentInstants.get(synapseContentInstantName)imp.getRoiManager().setRoiFillColor(contentInstantColor);
//					if (!Image3DUniverse.universes.isEmpty()) {
//						for (Image3DUniverse univ : Image3DUniverse.universes) {
//							if (univ.getContent(rootName) != null) {
//								univ.getContent(rootName)
//										.setColor(new Color3f(contentInstants.get(synapseContentInstantName).getFillColor()));
//								univ.getContent(rootName).setTransparency(0.9f);
//								;
//							}
//						}
//					}
//				}
//			}
//			for (ArrayList<String> synapsePartners : synapseByBundles) {
//				for (String synapseName : synapsePartners) {
//					IJ.log(synapseName);
//				}
//				IJ.log(synapsePartners.get(0) + ": " + (synapsePartners.size() - 1) + " synapses\n\n");
//			}
//			for (ContentInstant nextContentInstant : newContentInstants) {
//				specificBundleRM.addContentInstant(nextContentInstant);
//			}
//			specificBundleRM.setSelectedIndexes(specificBundleRM.getFullListIndexes());
//			specificBundleRM.saveMultiple(specificBundleRM.getSelectedIndexes(),
//					xlsPath + "_" + allBundleNames[i] + "_ContentInstantSet.zip");
//			IJ.wait(1);
//		}
//	}

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

//	void scanForRedundantContemporaneousNames() {
//		ContentInstant[] fullROIs = getFullContentInstantsAsArray();
//		int maxT = 0;
//		int minT = Integer.MAX_VALUE;
//		int maxZ = 0;
//		int minZ = Integer.MAX_VALUE;
//		int maxC = 0;
//		int minC = Integer.MAX_VALUE;
//		for (ContentInstant contentInstant : fullROIs) {
//			if (maxT < contentInstant.getTimepoint()) {
//				maxT = contentInstant.getTimepoint();
//			}
//			if (minT > contentInstant.getTimepoint()) {
//				minT = contentInstant.getTimepoint();
//			}
//			if (maxZ < contentInstant.getZPosition()) {
//				maxZ = contentInstant.getZPosition();
//			}
//			if (minZ > contentInstant.getZPosition()) {
//				minZ = contentInstant.getZPosition();
//			}
//			if (maxC < contentInstant.getZPosition()) {
//				maxC = contentInstant.getZPosition();
//			}
//			if (minC > contentInstant.getCPosition()) {
//				minC = contentInstant.getCPosition();
//			}
//		}
//		ArrayList<ContentInstant> redundantContempROIs = new ArrayList<ContentInstant>();
//		ArrayList<ContentInstant> unresolvedNameROIs = new ArrayList<ContentInstant>();
//		for (int t = minT; t <= maxT; t++) {
//			ArrayList<ContentInstant> contempROIs = new ArrayList<ContentInstant>();
//			for (int z = minZ; z <= maxZ; z++) {
//				for (int c = minC; c <= maxC; c++) {
//					if (getROIsByNumbers().get("" + c + "_" + z + "_" + t) != null) {
//						contempROIs.addAll(getROIsByNumbers().get("" + c + "_" + z + "_" + t));
//					}
//				}
//			}
//			IJ.log("t" + IJ.pad(t, 4) + "        " + contempROIs.size());
//			for (ContentInstant qContentInstant : contempROIs) {
//				if (qContentInstant.getName().matches(".*(m|n|g|h).*")) {
//					unresolvedNameROIs.add(qContentInstant);
//				}
//				for (ContentInstant uContentInstant : contempROIs) {
//					if ((qContentInstant != uContentInstant)) {
//						String qStart = qContentInstant.getName().split(" ")[0];
//						String uStart = uContentInstant.getName().split(" ")[0];
//						if ((qContentInstant.getName().startsWith(uStart)) || uContentInstant.getName().startsWith(qStart)) {
//							if (!redundantContempROIs.contains(qContentInstant))
//								redundantContempROIs.add(qContentInstant);
//							if (!redundantContempROIs.contains(uContentInstant))
//								redundantContempROIs.add(uContentInstant);
//						}
//					}
//				}
//			}
//		}
//		for (ContentInstant undoneContentInstant : unresolvedNameROIs) {
//			imp.getRoiManager().setRoiFillColor(undoneContentInstant, Colors.decode(("#33FF8800"), Color.orange));
//		}
//		for (ContentInstant badContentInstant : redundantContempROIs) {
//			imp.getRoiManager().setRoiFillColor(badContentInstant, Colors.decode("#33FFFF00", Color.YELLOW));
//		}
//		list.repaint();
//	}

	public class ModCellRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			Color bg = Color.gray;
			if (contentInstants != null && contentInstants.get(value) != null) {
				bg = contentInstants.get(value).getColor().get();
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

	public Hashtable<String, ArrayList<ContentInstant>> getContentInstantsByNumbers() {
		return contentInstantsByNumbers;
	}

	public void setContentInstantsByNumbers(Hashtable<String, ArrayList<ContentInstant>> contentInstantsByNumbers) {
		this.contentInstantsByNumbers = contentInstantsByNumbers;
	}

	public Hashtable<String, ArrayList<ContentInstant>> getContentInstantsByRootName() {
		return contentInstantsByRootName;
	}

	public void setContentInstantsByRootName(Hashtable<String, ArrayList<ContentInstant>> contentInstantsByRootName) {
		this.contentInstantsByRootName = contentInstantsByRootName;
	}

	public void setContentInstantFillColor(ContentInstant contentInstant, Color color) {
		setContentInstantFillColor(contentInstant, color, true);
	}

	public void setContentInstantFillColor(ContentInstant contentInstant, Color color, boolean reNameInListNow) {
		contentInstant.setColor(new Color3f(color));
		if (contentInstant.getName() != null && contentInstant.getName().contains(" \"_")) {
			String contentInstantColorChunk = contentInstant.getName().split("_")[1];
			String newColorName = contentInstantColorChunk.startsWith("#")
					? contentInstant.getName().replace(contentInstantColorChunk, Colors.colorToHexString(color))
					: contentInstant.getName().replace(" \"_", Colors.colorToHexString(color));

			if (reNameInListNow && listModel.indexOf(contentInstant.getName()) != -1)
				rename(newColorName, new int[] { listModel.indexOf(contentInstant.getName()) }, false);
		}
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
	}

}
