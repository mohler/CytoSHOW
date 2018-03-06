package ij.gui;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Menus;
import ij.Prefs;
import ij.VirtualStack;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.plugin.Colors;
import ij.plugin.MultiFileInfoVirtualStack;
import ij.plugin.frame.Channels;
import ij.plugin.frame.RoiManager;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.ScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.vcell.gloworm.MultiChannelController;
import org.vcell.gloworm.MultiQTVirtualStack;
import org.vcell.gloworm.SliceStereoToggle;

import com.sun.xml.internal.ws.util.StringUtils;

/** A frame for displaying images. */
public class ImageWindow extends JFrame implements FocusListener, WindowListener, WindowStateListener, MouseWheelListener {

	public static final int MIN_WIDTH = 128;
	public static final int MIN_HEIGHT = 32;
	
	protected ImagePlus imp;
	protected ImageJ ij;
	protected ImageCanvas ic;
	private double initialMagnification = 1;
	private int newWidth, newHeight;
	protected boolean closed;
	private boolean newCanvas;
	private boolean unzoomWhenMinimizing = true;
	Rectangle maxWindowBounds; // largest possible window on this screen
	Rectangle maxBounds; // Size of this window after it is maximized
	long setMaxBoundsTime;

	private static final int XINC = 8;
	private static final int YINC = 12;
	private static final int TEXT_GAP = 10;
	private static int xbase = -1;
	private static int ybase;
	private static int xloc;
	private static int yloc;
	private static int count;
	private static boolean centerOnScreen;
	private static Point nextLocation;
	
    protected int textGap = centerOnScreen?0:TEXT_GAP;

	/** This variable is set false if the user presses the escape key or closes the window. */
	public boolean running = false;
	
	/** This variable is set false if the user clicks in this
		window, presses the escape key, or closes the window. */
	public boolean running2 = false;
	public boolean running3 = false;
	private int origICtop;
	private Color subTitleBkgdColor = (Color.white);
	public Panel tagButtonPanel;
	public Panel viewButtonPanel;
	public Panel modeButtonPanel;
	public JButton fullSetButton;
	public Panel optionsPanel;
	public JButton hideShowButton;
	public JButton sketch3DButton;
	public Label countLabel;
	public JPanel overheadPanel;
	private Toolbar toolbar;
	public Toolbar getToolbar() {
		return toolbar;
	}

	public void setToolbar(Toolbar toolbar) {
		this.toolbar = toolbar;
	}

	public JButton dupButton;
	public JButton modeButton;
	public JButton stereo4dxButton;
	public JButton stereo4dyButton;
	public JButton stereo4dXrcButton;
	public JButton slice4dButton;
	public JButton stereo4dYrcButton;
	public SliceStereoToggle sst;
	public JButton sketchVVButton;
	private JButton flattenTagsButton;
	public JButton tagsButton;

	
	public ImageWindow(String title) {
		super(title);
	}

    public ImageWindow(ImagePlus imp) {
    	this(imp, null);
   }
    
    public ImageWindow(ImagePlus imp, ImageCanvas ic) {
		super(imp.getTitle());
		BorderLayout bl = new BorderLayout();

		if (false/*Prefs.blackCanvas && getClass().getName().equals("ij.gui.ImageWindow")*/) {
			setForeground(Color.white);
			setBackground(Color.black);
		} else {
        	setForeground(Color.black);
        	if (IJ.isLinux())
        		setBackground(ImageJ.backgroundColor);
        	else
        		setBackground(Color.white);
        }
		subTitleBkgdColor = Color.white;
		boolean openAsHyperStack = imp.getOpenAsHyperStack();
		ij = IJ.getInstance();
		this.imp = imp;
		if (ic==null) {
			ic=new ImageCanvas(imp); 
			newCanvas=true;
		}
		this.ic = ic;
		this.setVisible(true);
		pack();
		this.setLayout(bl);

		ImageWindow previousWindow = imp.getWindow();
		

		add(ic, BorderLayout.CENTER);
		pack();
//		show();
		addToolBarPanel();
		pack();
//		show();

		addCommandButtons(imp);
		pack();
//		show();
		
		addFocusListener(this);
		addWindowListener(this);
 		addWindowStateListener(this);
 		addKeyListener(ij);
		setFocusTraversalKeysEnabled(false);
		if (!(this instanceof StackWindow))
			addMouseWheelListener(this);
		setResizable(true);
		if (!(this instanceof HistogramWindow&&IJ.isMacro()&&Interpreter.isBatchMode())) {
			WindowManager.addWindow(this);
			imp.setWindow(this);
		}
		if (previousWindow!=null) {
			if (newCanvas)
				setLocationAndSize(false);
			else
				ic.update(previousWindow.getCanvas());
			Point loc = previousWindow.getLocation();
			setLocation(loc.x, loc.y);
			if (!(this instanceof StackWindow)) {
				pack();
				show();
			}
			if (ic.getMagnification()!=0.0)
				imp.setTitle(imp.getTitle());
			boolean unlocked = imp.lockSilently();
			boolean changes = imp.changes;
			imp.changes = false;
			previousWindow.close();
			imp.changes = changes;
			if (unlocked)
				imp.unlock();
			if (this.imp!=null)
				this.imp.setOpenAsHyperStack(openAsHyperStack);
			WindowManager.setCurrentWindow(this);
		} else {
			setLocationAndSize(false);
			if (ij!=null && !IJ.isMacintosh()) {
				Image img = ij.getIconImage();
				if (img!=null) 
					try {setIconImage(img);} catch (Exception e) {}
			}
			if (centerOnScreen) {
				GUI.center(this);
				centerOnScreen = false;
			} else if (nextLocation!=null) {
				setLocation(nextLocation);
				nextLocation = null;
			}
			if (Interpreter.isBatchMode() || (IJ.getInstance()==null&&this instanceof HistogramWindow)) {
				WindowManager.setTempCurrentImage(imp);
				Interpreter.addBatchModeImage(imp);
			} else
				if (!(this instanceof StackWindow)) {
					pack();
					show();
				}
		}
		origICtop = ic.getY();

//	    addComponentListener(new ComponentAdapter() {
//	      public void componentResized(ComponentEvent e) {
//	    	  System.out.println(e.getSource().toString());
//	    	  ImageCanvas ic = ImageWindow.this.ic;
//	    	  double mag = ic.getMagnification();
//	    	  ic.setSourceRect(new Rectangle(ic.getX(), ic.getY(), 
//	    			  ((int)(ic.getWidth()/mag)),
//	    			  ((int)(ic.getHeight()/mag))));
//	      }
//	    });
	    
     }

	public void addToolBarPanel() {
		overheadPanel = new JPanel();
		overheadPanel.setLayout(new GridLayout(1, 1));
		
		toolbar = new Toolbar();
		Toolbar.setInstance(toolbar);
		toolbar.installBuiltinTool("LUT Menu");
		toolbar.installBuiltinTool("Stacks Menu");
		toolbar.installBuiltinTool("Developer Menu");

		toolbar.addKeyListener(ij);
//		if (ij != null)
//			toolbar.addMouseListener(toolbar);
		overheadPanel.add(toolbar);
		overheadPanel.setSize(overheadPanel.getWidth(), overheadPanel.getHeight()*2);
//		overheadScrollPane.add(overheadPanel);

		this.add(overheadPanel, BorderLayout.NORTH);
//		Toolbar.setInstance(ij.toolbar);
	}

	public void addCommandButtons(ImagePlus imp) throws HeadlessException {
		GridBagLayout fspgridbag = new GridBagLayout();
		GridBagConstraints fspc = new GridBagConstraints();
		GridBagLayout viewgridbag = new GridBagLayout();
		GridBagConstraints vspc = new GridBagConstraints();

		tagButtonPanel = new Panel(fspgridbag);
		viewButtonPanel = new Panel(viewgridbag);
		modeButtonPanel = new Panel(viewgridbag);
		fspgridbag.setConstraints(tagButtonPanel, fspc);
		fspgridbag.setConstraints(viewButtonPanel, vspc);
		fspgridbag.setConstraints(modeButtonPanel, vspc);
		
		tagButtonPanel.setLayout(fspgridbag);
		viewButtonPanel.setLayout(viewgridbag);
		modeButtonPanel.setLayout(viewgridbag);
		Font buttonPanelFont = new Font(Font.SANS_SERIF, Font.PLAIN, IJ.isMacOSX()?7:9);
		int y = 0;
		fspc.gridx = 0;
		fspc.gridy = 0;
		fspc.gridwidth = 1;
		fspc.fill = GridBagConstraints.HORIZONTAL;
		fspc.weightx = 1.0;
		fspc.weighty = 0.1;
		fspc.gridy = y++;
		JLabel tagging = new JLabel();
		tagging.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Tags.png")));
		tagging.setToolTipText("These buttons help you add and edit tags in the scene...");
		tagButtonPanel.add(tagging, fspc);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton addButton = new JButton();
		addButton.setActionCommand("Add\n(ctrl-t)");
		addButton.setName("Add\n(ctrl-t)");
		addButton.setToolTipText("Add Selection as a Tag");
		addButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/plusicon.png")));
		addButton.setFont(buttonPanelFont);
		tagButtonPanel.add(addButton, fspc);
//		viewButtonPanel.add(addButton, fspc);
		addButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		JButton paintButton = new JButton();
		paintButton.setActionCommand("Color");
		paintButton.setName("Color");
		paintButton.setToolTipText("Re-Color Selected Tag");
		paintButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/DIYPaintbrush.png")));
		paintButton.setFont(buttonPanelFont);
		tagButtonPanel.add(paintButton, fspc);
//		viewButtonPanel.add(renameButton, fspc);
		paintButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		JButton renameButton = new JButton();
		renameButton.setActionCommand("Rename");
		renameButton.setName("Rename");
		renameButton.setToolTipText("Rename Selected Tag");
		renameButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/editIcon.png")));
		renameButton.setFont(buttonPanelFont);
		tagButtonPanel.add(renameButton, fspc);
//		viewButtonPanel.add(renameButton, fspc);
		renameButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		JButton deleteButton = new JButton();
		deleteButton.setActionCommand("Delete ");
		deleteButton.setName("Delete ");
		deleteButton.setToolTipText("Delete Selected Tag");
		deleteButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/menu_eraser.png")));
		deleteButton.setFont(buttonPanelFont);
		tagButtonPanel.add(deleteButton, fspc);
//		viewButtonPanel.add(deleteButton, fspc);
		deleteButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		JButton saveButton = new JButton();
		saveButton.setActionCommand("Save");
		saveButton.setName("Save");
		saveButton.setToolTipText("Save the Tag Set");
		saveButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/SaveIcon.png")));
		saveButton.setFont(buttonPanelFont);
		tagButtonPanel.add(saveButton, fspc);
//		viewButtonPanel.add(saveButton, fspc);
		saveButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		JButton advancedButton = new JButton();
		advancedButton.setActionCommand("Adv.");
		advancedButton.setName("Adv.");
		advancedButton.setToolTipText("Advanced Controls for Tags");
		advancedButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/gearIcon.png")));
		advancedButton.setFont(buttonPanelFont);
		tagButtonPanel.add(advancedButton, fspc);
//		viewButtonPanel.add(advancedButton, fspc);
		advancedButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weightx = 1.0;
		fspc.weighty = 0.1;
		countLabel = new Label(imp.getRoiManager().textCountLabel.getText(),Label.CENTER);
		countLabel.setFont(buttonPanelFont);
		tagButtonPanel.add(countLabel, fspc);
//		viewButtonPanel.add(countLabel, fspc);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fullSetButton = new JButton();
		fullSetButton.setActionCommand("Full\nSet");
		fullSetButton.setName("Full\nSet");
		fullSetButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/refreshIcon.png")));
		fullSetButton.setToolTipText("Refresh Tag Set");
		fullSetButton.setFont(buttonPanelFont);
		tagButtonPanel.add(fullSetButton, fspc);
//		viewButtonPanel.add(fullSetButton, fspc);
		fullSetButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		hideShowButton = new JButton();
		hideShowButton.setActionCommand("Hide");
		hideShowButton.setName("Hide");
		hideShowButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/showIcon.png")));
		hideShowButton.setToolTipText("Showing Tags...click to Hide Tags");
		hideShowButton.setFont(buttonPanelFont);
		tagButtonPanel.add(hideShowButton, fspc);
//		viewButtonPanel.add(hideShowButton, fspc);
		hideShowButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		flattenTagsButton = new JButton();
		flattenTagsButton.setActionCommand("Flatten");
		flattenTagsButton.setName("Flatten");
		flattenTagsButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/flatten.png")));
		flattenTagsButton.setToolTipText("Flatten Tags into Image");
		flattenTagsButton.setFont(buttonPanelFont);
		tagButtonPanel.add(flattenTagsButton, fspc);
//		viewButtonPanel.add(flattenTagsButton, fspc);
		flattenTagsButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		sketch3DButton = new JButton();
		sketch3DButton.setActionCommand("Sketch\n3D");
		sketch3DButton.setName("Sketch\n3D");
		sketch3DButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/3D_47973.gif")));
		sketch3DButton.setToolTipText("Sketch3D");
		sketch3DButton.setFont(buttonPanelFont);
		tagButtonPanel.add(sketch3DButton, fspc);
//		viewButtonPanel.add(sketch3DButton, fspc);
		sketch3DButton.addActionListener(imp.getRoiManager());
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		sketchVVButton = new JButton();
		sketchVVButton.setActionCommand("Sketch\nVV");
		sketchVVButton.setName("Sketch\nVV");
		sketchVVButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/VV_57282.gif")));
		sketchVVButton.setToolTipText("SketchVV");
		sketchVVButton.setFont(buttonPanelFont);
		tagButtonPanel.add(sketchVVButton, fspc);
//		viewButtonPanel.add(sketch3DButton, fspc);
		sketchVVButton.addActionListener(imp.getRoiManager());
		
//		
		y = 0;
		vspc.gridx = 0;
		vspc.gridy = 0;
		vspc.gridwidth = 1;
		vspc.fill = GridBagConstraints.HORIZONTAL;
		vspc.weightx = 1.0;
		vspc.weighty = 0.1;
		vspc.gridy = y++;
		JLabel viewing = new JLabel();
		viewing.setIcon(new ImageIcon(ImageWindow.class.getResource("images/TVviewSmall.png")));
		viewing.setToolTipText("These buttons help you adjust your view of the scene...");
//		viewButtonPanel.add(viewing, vspc);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		
		if(imp.getStack() instanceof VirtualStack) {
			JButton edgeButton = new JButton();
			edgeButton.setActionCommand("Edges");
			edgeButton.setName("Edges");
			edgeButton.setToolTipText("Find Edges of Image Features");
			edgeButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Edges.png")));
			edgeButton.addActionListener(ij);
			viewButtonPanel.add(edgeButton, fspc);
			fspc.gridy = y++;
			fspc.weighty = 0.5;
			fspc.fill = GridBagConstraints.BOTH;
		}
		
		JButton displayButton = new JButton();
		displayButton.setActionCommand("Adjust Display Contrast...");
		displayButton.setName("Adjust Display Contrast...");
		displayButton.setToolTipText("Adjust Image Contrast and Colors");
		displayButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/displayIcon.png")));
		displayButton.setFont(buttonPanelFont);
		viewButtonPanel.add(displayButton, fspc);
		displayButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton photoButton = new JButton();
		photoButton.setActionCommand("Save JPEG snapshot...");
		photoButton.setName("Save JPEG snapshot...");
		photoButton.setToolTipText("Save JPEG snapshot...");
		photoButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/photoIcon32.png")));
		photoButton.setFont(buttonPanelFont);
		viewButtonPanel.add(photoButton, fspc);
		photoButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton videoButton = new JButton();
		videoButton.setActionCommand("AVI... ");
		videoButton.setName("AVI... ");
		videoButton.setToolTipText("Save movie of this scene...");
		videoButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/video_icon32.png")));
		videoButton.setFont(buttonPanelFont);
		viewButtonPanel.add(videoButton, fspc);
		videoButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton shareButton = new JButton();
		shareButton.setActionCommand("Share Current Scene...");
		shareButton.setName("Share Current Scene...");
		shareButton.setToolTipText("Share Current Scene...");
		shareButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/sharethisIcon.png")));
		shareButton.setFont(buttonPanelFont);
		viewButtonPanel.add(shareButton, fspc);
		shareButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		dupButton = new JButton();
		dupButton.setActionCommand("Duplicate Region/Load to RAM...");
		dupButton.setName("Duplicate Region/Load to RAM...");
		dupButton.setToolTipText("Download Data from the Selected Region...");
		dupButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/download_button_animatedStill.png")));
		dupButton.setFont(buttonPanelFont);
		viewButtonPanel.add(dupButton, fspc);
		dupButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton zMipButton = new JButton();
		zMipButton.setActionCommand("Z Project...");
		zMipButton.setName("Z Project...");
		zMipButton.setToolTipText("Maximum Intensity Projection of Z-Stack ...");
		zMipButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/zMip.png")));
		zMipButton.setFont(buttonPanelFont);
		viewButtonPanel.add(zMipButton, fspc);
		zMipButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton prjButton = new JButton();
		prjButton.setActionCommand("3D Project Selected Region...");
		prjButton.setName("3D Project Selected Region...");
		prjButton.setToolTipText("3D View of the Selected Region ...");
		prjButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/3DiconNew32.png")));
		prjButton.setFont(buttonPanelFont);
		viewButtonPanel.add(prjButton, fspc);
		prjButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton vvButton = new JButton();
		vvButton.setActionCommand("Volume Viewer");
		vvButton.setName("Volume Viewer");
		vvButton.setToolTipText("Volume Viewer of the Selected Region ...");
		vvButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/VV_58191.gif")));
		vvButton.setFont(buttonPanelFont);
		viewButtonPanel.add(vvButton, fspc);
		vvButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton orthoViewButton = new JButton();
		orthoViewButton.setActionCommand("Orthogonal Views[/]");
		orthoViewButton.setName("Orthogonal Views[/]");
		orthoViewButton.setToolTipText("View Orthogonal Slices through the Image Volume");
		orthoViewButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/orthoIconSmall.png")));
		orthoViewButton.setFont(buttonPanelFont);
		viewButtonPanel.add(orthoViewButton, fspc);
		orthoViewButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton advancedViewButton = new JButton();
		advancedViewButton.setActionCommand("Multi-Channel Controller...");
		advancedViewButton.setName("Multi-Channel Controller...");
		advancedViewButton.setToolTipText("Advanced Multi-Channel Scene Controls");
		advancedViewButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/gearFireIcon.png")));
		advancedViewButton.setFont(buttonPanelFont);
		viewButtonPanel.add(advancedViewButton, fspc);
		advancedViewButton.addActionListener(ij);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		modeButton = new JButton();
		modeButton.setActionCommand("Slice<>Stereo");
		modeButton.setName("Slice<>Stereo");
		modeButton.setToolTipText("Choose among Slice-4D or Stereo-4D viewing modes");
		modeButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/4DMode.png")));
		modeButton.setFont(buttonPanelFont);
		viewButtonPanel.add(modeButton, fspc);
		sst = new SliceStereoToggle(imp);
		modeButton.addActionListener(sst);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		tagsButton = new JButton();
		tagsButton.setActionCommand("Show/Hide Tagging Tools");
		tagsButton.setName("Show/Hide Tagging Tools");
		tagsButton.setToolTipText("Show Tag-Editing Tools");
		tagsButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/TagsThin.png")));
		tagsButton.setHorizontalTextPosition(SwingConstants.CENTER);
		tagsButton.setVerticalTextPosition(SwingConstants.CENTER);
		tagsButton.setFont(buttonPanelFont);
		viewButtonPanel.add(tagsButton, fspc);
		tagsButton.addActionListener(ij);
		
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		slice4dButton = new JButton();
		slice4dButton.setActionCommand("Slice4D");
		slice4dButton.setName("Slice4D");
		slice4dButton.setToolTipText("Switch to Slice-4D viewing");
		slice4dButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Slice4D.png")));
		slice4dButton.setFont(buttonPanelFont);
		modeButtonPanel.add(slice4dButton, fspc);
		slice4dButton.addActionListener(sst);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		stereo4dxButton = new JButton();
		stereo4dxButton.setActionCommand("Stereo4DX");
		stereo4dxButton.setName("Stereo4DX");
		stereo4dxButton.setToolTipText("Switch to Stereo-4D, spinning around X-axis");
		stereo4dxButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Stereo4DX.png")));
		stereo4dxButton.setFont(buttonPanelFont);
		modeButtonPanel.add(stereo4dxButton, fspc);
		stereo4dxButton.addActionListener(sst);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		stereo4dyButton = new JButton();
		stereo4dyButton.setActionCommand("Stereo4DY");
		stereo4dyButton.setName("Stereo4DY");
		stereo4dyButton.setToolTipText("Switch to Stereo-4D, spinning around Y-axis");
		stereo4dyButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Stereo4DY.png")));
		stereo4dyButton.setFont(buttonPanelFont);
		modeButtonPanel.add(stereo4dyButton, fspc);
		stereo4dyButton.addActionListener(sst);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		stereo4dXrcButton = new JButton();
		stereo4dXrcButton.setActionCommand("Stereo4DXrc");
		stereo4dXrcButton.setName("Stereo4DXrc");
		stereo4dXrcButton.setToolTipText("Switch to Red/Cyan Stereo-4D spinning, around X-axis");
		stereo4dXrcButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Stereo4DXrc.png")));
		stereo4dXrcButton.setFont(buttonPanelFont);
		modeButtonPanel.add(stereo4dXrcButton, fspc);
		stereo4dXrcButton.addActionListener(sst);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		stereo4dYrcButton = new JButton();
		stereo4dYrcButton.setActionCommand("Stereo4DYrc");
		stereo4dYrcButton.setName("Stereo4DYrc");
		stereo4dYrcButton.setToolTipText("Switch to Red/Cyan Stereo-4D, spinning around Y-axis");
		stereo4dYrcButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/Stereo4DYrc.png")));
		stereo4dYrcButton.setFont(buttonPanelFont);
		modeButtonPanel.add(stereo4dYrcButton, fspc);
		stereo4dYrcButton.addActionListener(sst);

		BorderLayout optbl = new BorderLayout();
		optionsPanel = new Panel();
		optionsPanel.setLayout(optbl);
		optionsPanel.add(tagButtonPanel, BorderLayout.EAST);
		optionsPanel.add(modeButtonPanel, BorderLayout.WEST);

		add(optionsPanel, BorderLayout.EAST);
		add(viewButtonPanel, BorderLayout.WEST);
		tagButtonPanel.setVisible(false);
		modeButtonPanel.setVisible(false);
		optionsPanel.setVisible(true);
		viewButtonPanel.setVisible(true);

//		
	}
    
	private void setLocationAndSize(boolean updating) {
		int width = imp.getWidth();
		int height = imp.getHeight();
		Rectangle maxWindow = getMaxWindow(0,0);
		//if (maxWindow.x==maxWindow.width)  // work around for Linux bug
		//	maxWindow = new Rectangle(0, maxWindow.y, maxWindow.width, maxWindow.height);
		if (WindowManager.getWindowCount()<=1)
			xbase = -1;
		if (width>maxWindow.width/2 && xbase>maxWindow.x+5+XINC*6)
			xbase = -1;
		if (xbase==-1) {
			count = 0;
			xbase = maxWindow.x + 5;
			if (width*2<=maxWindow.width)
				xbase = maxWindow.x+maxWindow.width/2-width/2;
			ybase = maxWindow.y;
			xloc = xbase;
			yloc = ybase;
		}
		int x = xloc;
		int y = yloc;
		xloc += XINC;
		yloc += YINC;
		count++;
		if (count%6==0) {
			xloc = xbase;
			yloc = ybase;
		}

		int sliderHeight = (this instanceof StackWindow)?20:0;
		int screenHeight = maxWindow.y+maxWindow.height-sliderHeight;
		double mag = 1;
		while (xbase+XINC*4+width*mag>maxWindow.x+maxWindow.width || ybase+height*mag>=screenHeight) {
			//IJ.log(mag+"  "+xbase+"  "+width*mag+"  "+maxWindow.width);
			double mag2 = ImageCanvas.getLowerZoomLevel(mag);
			if (mag2==mag) break;
			mag = mag2;
		}
		
		if (mag<1.0) {
			initialMagnification = mag;
			ic.setDrawingSize((int)(width*mag), (int)(height*mag));
		}
		ic.setMagnification(mag);
		if (y+height*mag>screenHeight)
			y = ybase;
        if (!updating) setLocation(x, y);
		if (Prefs.open100Percent && ic.getMagnification()<1.0) {
			while(ic.getMagnification()<1.0)
				ic.zoomIn(0, 0);
			setSize(Math.min(width, maxWindow.width-x), Math.min(height, screenHeight-y));
			validate();
		} else 
			pack();
	}
				
	Rectangle getMaxWindow(int xloc, int yloc) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle bounds = ge.getMaximumWindowBounds();
		//bounds.x=960; bounds.y=0; bounds.width=960; bounds.height=1200;
		if (IJ.debugMode) IJ.log("getMaxWindow: "+bounds+"  "+xloc+","+yloc);
		if (xloc>bounds.x+bounds.width || yloc>bounds.y+bounds.height) {
			Rectangle bounds2 = getSecondaryMonitorBounds(ge, xloc, yloc);
			if (bounds2!=null) return bounds2;
		}
		Dimension ijSize = ij!=null?ij.getSize():new Dimension(0,0);
		if (bounds.height>600) {
			bounds.y += ijSize.height;
			bounds.height -= ijSize.height;
		}
		return bounds;
	}
	
	private Rectangle getSecondaryMonitorBounds(GraphicsEnvironment ge, int xloc, int yloc) {
		//IJ.log("getSecondaryMonitorBounds "+wb);
		GraphicsDevice[] gs = ge.getScreenDevices();
		for (int j=0; j<gs.length; j++) {
			GraphicsDevice gd = gs[j];
			GraphicsConfiguration[] gc = gd.getConfigurations();
			for (int i=0; i<gc.length; i++) {
				Rectangle bounds = gc[i].getBounds();
				//IJ.log(j+" "+i+" "+bounds+"  "+bounds.contains(wb.x, wb.y));
				if (bounds!=null && bounds.contains(xloc, yloc))
					return bounds;
			}
		}		
		return null;
	}
	
	public double getInitialMagnification() {
		return initialMagnification;
	}
	
	/** Override Container getInsets() to make room for some text above the image. */
	public Insets getInsets() {
		Insets insets = super.getInsets();
		double mag = ic.getMagnification();
		int extraWidth = (int)((MIN_WIDTH - imp.getWidth()*mag)/2.0);
		if (extraWidth<0) extraWidth = 0;
		extraWidth = 5;
		int extraHeight = (int)((MIN_HEIGHT - imp.getHeight()*mag)/2.0);
		if (extraHeight<0) extraHeight = 0;
		extraHeight = 5;
		insets = new Insets(insets.top+textGap+extraHeight, insets.left+extraWidth, insets.bottom+extraHeight, insets.right+extraWidth);
		return insets;
	}

    /** Draws the subtitle. */
    public void drawInfo(Graphics g) {
        if (textGap!=0) {
			Insets insets = super.getInsets();
				
			g.setColor(subTitleBkgdColor);
			g.fillRect(insets.left, insets.top, (int) g.getFontMetrics().getStringBounds(createSubtitle(), g).getWidth()+10, this.origICtop);
			if (imp.isComposite()) {
				CompositeImage ci = (CompositeImage)imp;
				if (ci.getMode()==CompositeImage.COMPOSITE)
					g.setColor(ci.getChannelColor().darker());
				else
					g.setColor(Color.black);
			} else {
				g.setColor(Color.black);
			}
			String subTitle = createSubtitle();
			g.drawString(subTitle, insets.left+5, insets.top+TEXT_GAP);
		}
    }
    
    /** Creates the subtitle. */
    public String createSubtitle() {
    	String s="";
    	int nSlices = imp.getStackSize();
    	if (nSlices>1) {
    		ImageStack stack = imp.getStack();
    		int currentSlice = imp.getCurrentSlice();
    		s += currentSlice+"/"+nSlices;
    		String label = stack.getSliceLabel(currentSlice);
    		if (label!=null && label.length()>0) {
    			if (imp.isHyperStack()) label = label.replace(';', ' ');
    			s += " (" + label + ")";
    		}
			if ((this instanceof StackWindow) && (running2 || running3)) {
				return s;
			}
    		s += "; ";
		} else {
			String label = (String)imp.getProperty("Label");
			if (label!=null) {
				int newline = label.indexOf('\n');
				if (newline>0)
					label = label.substring(0, newline);
				int len = label.length();
				if (len>4 && label.charAt(len-4)=='.' && !Character.isDigit(label.charAt(len-1)))
					label = label.substring(0,len-4);
//				if (label.length()>60)
//					label = label.substring(0, 60);
				s = label + "; ";
			}
		}
    	int type = imp.getType();
    	Calibration cal = imp.getCalibration();
    	if (cal.scaled()) {
    		s += IJ.d2s(imp.getWidth()*cal.pixelWidth,2) + "x" + IJ.d2s(imp.getHeight()*cal.pixelHeight,2)
 			+ " " + cal.getUnits() + " (" + imp.getWidth() + "x" + imp.getHeight() + "); ";
    	} else
    		s += imp.getWidth() + "x" + imp.getHeight() + " pixels; ";
		double size = ((double)imp.getWidth()*imp.getHeight()*imp.getStackSize())/1024.0;
    	switch (type) {
	    	case ImagePlus.GRAY8:
	    	case ImagePlus.COLOR_256:
	    		s += "8-bit";
	    		break;
	    	case ImagePlus.GRAY16:
	    		s += "16-bit";
				size *= 2.0;
	    		break;
	    	case ImagePlus.GRAY32:
	    		s += "32-bit";
				size *= 4.0;
	    		break;
	    	case ImagePlus.COLOR_RGB:
	    		s += "RGB";
				size *= 4.0;
	    		break;
    	}
    	if (imp.isInvertedLut())
    		s += " (inverting LUT)";
   		String s2=null, s3=null;
    	if (size<1024.0)
    		{s2=IJ.d2s(size,0); s3="K";}
    	else if (size<10000.0)
     		{s2=IJ.d2s(size/1024.0,1); s3="MB";}
    	else if (size<1048576.0)
    		{s2=IJ.d2s(Math.round(size/1024.0),0); s3="MB";}
	   	else
    		{s2=IJ.d2s(size/1048576.0,1); s3="GB";}
    	if (s2.endsWith(".0")) s2 = s2.substring(0, s2.length()-2);
     	return s+"; "+s2+s3;
    }

    public void paint(Graphics g) {
		//if (IJ.debugMode) IJ.log("wPaint: " + imp.getTitle());
		drawInfo(g);
		Rectangle r = ic.getBounds();
		int extraWidth = MIN_WIDTH - r.width;
		int extraHeight = MIN_HEIGHT - r.height;
//		if (extraWidth<=0 && extraHeight<=0 && !Prefs.noBorder && !IJ.isLinux())
//			g.drawRect(r.x-1, r.y-1, r.width+1, r.height+1);
    }
    
	/** Removes this window from the window list and disposes of it.
		Returns false if the user cancels the "save changes" dialog. */
	public boolean close() {
		boolean isRunning = running || running2 || running3;
		running = running2 = running3 = false;
		boolean virtual = imp.getStackSize()>1 && imp.getStack().isVirtual();
		if (isRunning) IJ.wait(500);
		if (ij==null || IJ.getApplet()!=null || Interpreter.isBatchMode() || IJ.macroRunning() || virtual)
			imp.changes = false;
		if (imp.changes) {
			String msg;
			String name = imp.getTitle();
			if (name.length()>22)
				msg = "Save changes to\n" + "\"" + name + "\"?";
			else
				msg = "Save changes to \"" + name + "\"?";
			YesNoCancelDialog d = new YesNoCancelDialog(this, "ImageJ", msg);
			if (d.cancelPressed())
				return false;
			else if (d.yesPressed()) {
				FileSaver fs = new FileSaver(imp);
				if (!fs.save()) return false;
			}
		}
		closed = true;
		if (WindowManager.getWindowCount()==0)
			{xloc = 0; yloc = 0;}
		WindowManager.removeWindow(this);
		//setVisible(false);
		if (ij!=null && ij.quitting())  // this may help avoid thread deadlocks
			return true;
		dispose();
		if (ic != null)
			this.remove(ic);
		if (imp!=null)
			imp.flush();
		imp = null;
		return true;
	}
	
	public ImagePlus getImagePlus() {
		return imp;
	}

	public void setImage(ImagePlus imp2) {
		ImageCanvas ic = getCanvas();
		if (ic==null || imp2==null)
			return;
		imp = imp2;
		imp.setWindow(this);
		ic.updateImage(imp);
		ic.setImageUpdated();
		ic.repaint();
		repaint();
	}
	
	public void updateImage(ImagePlus imp) {
//        if (imp!=this.imp)
//            throw new IllegalArgumentException("imp!=this.imp");
		this.imp = imp;
        ic.updateImage(imp);
        setLocationAndSize(true);
        if (this instanceof StackWindow) {
        	StackWindow sw = (StackWindow)this;
        	int stackSize = imp.getStackSize();
        	int nScrollbars = sw.getNScrollbars();
        	if (stackSize==1 && nScrollbars>0)
        		sw.removeScrollbars();
        	else if (stackSize>1 && nScrollbars==0)
        		sw.addScrollbars(imp);
        }
        pack();
		repaint();
		maxBounds = getMaximumBounds();
		setMaximizedBounds(maxBounds);
		setMaxBoundsTime = System.currentTimeMillis();
	}

	public ImageCanvas getCanvas() {
		return ic;
	}
	

	static ImagePlus getClipboard() {
		return ImagePlus.getClipboard();
	}
	
	public Rectangle getMaximumBounds() {
		double width = imp.getWidth();
		double height = imp.getHeight();
		double iAspectRatio = width/height;
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle maxWindow = ge.getMaximumWindowBounds();
		maxWindowBounds = maxWindow;
		if (iAspectRatio/((double)maxWindow.width/maxWindow.height)>0.75) {
			maxWindow.y += 22;  // uncover ImageJ menu bar
			maxWindow.height -= 22;
		}
		Dimension extraSize = getExtraSize();
		double maxWidth = maxWindow.width-extraSize.width;
		double maxHeight = maxWindow.height-extraSize.height;
		double mAspectRatio = maxWidth/maxHeight;
		int wWidth, wHeight;
		double mag;
		if (iAspectRatio>=mAspectRatio) {
			mag = maxWidth/width;
			wWidth = maxWindow.width;
			wHeight = (int)(height*mag+extraSize.height);
		} else {
			mag = maxHeight/height;
			wHeight = maxWindow.height;
			wWidth = (int)(width*mag+extraSize.width);
		}
		int xloc = (int)(maxWidth-wWidth)/2;
		if (xloc<0) xloc = 0;
		return new Rectangle(xloc, maxWindow.y, wWidth, wHeight);
	}
	
	Dimension getExtraSize() {
		Insets insets = getInsets();
		int extraWidth = insets.left+insets.right + 10;
		int extraHeight = insets.top+insets.bottom + 10;
		if (extraHeight==20) extraHeight = 42;
		int members = getComponentCount();
		//if (IJ.debugMode) IJ.log("getExtraSize: "+members+" "+insets);
		for (int i=1; i<members; i++) {
		    Component m = getComponent(i);
		    Dimension d = m.getPreferredSize();
			extraHeight += d.height + 5;
			if (IJ.debugMode) IJ.log(i+"  "+d.height+" "+extraHeight);
		}
		return new Dimension(extraWidth, extraHeight);
	}

	public Component add(Component comp) {
		comp = super.add(comp);
		maxBounds = getMaximumBounds();
		//if (!IJ.isLinux()) {
			setMaximizedBounds(maxBounds);
			setMaxBoundsTime = System.currentTimeMillis();
		//}
		return comp;
	}
	
	//public void setMaximizedBounds(Rectangle r) {
	//	super.setMaximizedBounds(r);
	//	IJ.log("setMaximizedBounds: "+r+" "+getMaximizedBounds());
	//	if (getMaximizedBounds().x==0)
	//		throw new IllegalArgumentException("");
	//}
	
	public void maximize() {
		if (maxBounds==null)
			return;
		int width = imp.getWidth();
		int height = imp.getHeight();
		double aspectRatio = (double)width/height;
		Dimension extraSize = getExtraSize();
		int extraHeight = extraSize.height;
		double mag = (double)(maxBounds.height-extraHeight)/height;
		if (IJ.debugMode) IJ.log("maximize: "+mag+" "+ic.getMagnification()+" "+maxBounds);
		setSize(getMaximizedBounds().width, getMaximizedBounds().height);
		if (mag>ic.getMagnification() || aspectRatio<0.5 || aspectRatio>2.0) {
			ic.setMagnification2(mag);
			ic.setSrcRect(new Rectangle(0, 0, width, height));
			ic.setDrawingSize((int)(width*mag), (int)(height*mag));
			validate();
			unzoomWhenMinimizing = true;
		} else
			unzoomWhenMinimizing = false;
	}
	
	public void minimize() {
		if (unzoomWhenMinimizing)
			ic.unzoom();
		unzoomWhenMinimizing = true;
	}

	/** Has this window been closed? */
	public boolean isClosed() {
		return closed;
	}
	
	public void focusGained(FocusEvent e) {
		if (!Interpreter.isBatchMode() && ij!=null && !ij.quitting() && imp!=null) {
			if (IJ.debugMode) IJ.log("focusGained: "+imp);
			WindowManager.setCurrentWindow(this);
		}
	}

	public void windowActivated(WindowEvent e) {
		if (IJ.debugMode) IJ.log("windowActivated: "+imp.getTitle());
		ImageJ ij = IJ.getInstance();
		boolean quitting = ij!=null && ij.quitting();
		if (IJ.isMacintosh() && ij!=null && !quitting) {
			IJ.wait(10); // may be needed for Java 1.4 on OS X
			setMenuBar(Menus.getMenuBar());
		}
		if (imp==null) return;
		imp.setActivated(); // notify ImagePlus that image has been activated
		if (!closed && !quitting && !Interpreter.isBatchMode())
			WindowManager.setCurrentWindow(this);
		Channels channels = Channels.getInstance();
		if (channels!=null && imp.isComposite()) {
			((Channels)channels).update();
			WindowManager.getCurrentWindow().toFront();
		}
		toolbar.setTool2(toolbar.localCurrent);

	}
	
	public void windowClosing(WindowEvent e) {
		//IJ.log("windowClosing: "+imp.getTitle()+" "+closed);
		if (closed)
			return;
		if (ij!=null) {
			WindowManager.setCurrentWindow(this);
			IJ.doCommand("Close");
		} else {
			//setVisible(false);
			dispose();
			WindowManager.removeWindow(this);
		}
	}
	
	public void windowStateChanged(WindowEvent e) {
		int oldState = e.getOldState();
		int newState = e.getNewState();
		//IJ.log("WSC: "+getBounds()+" "+oldState+" "+newState);
		if ((oldState & Frame.MAXIMIZED_BOTH) == 0 && (newState & Frame.MAXIMIZED_BOTH) != 0)
			maximize();
		else if ((oldState & Frame.MAXIMIZED_BOTH) != 0 && (newState & Frame.MAXIMIZED_BOTH) == 0)
			minimize();
	}

	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void focusLost(FocusEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}	
	public void windowOpened(WindowEvent e) {}
	
	public void mouseWheelMoved(MouseWheelEvent event) {
		int rotation = event.getWheelRotation();
		int width = imp.getWidth();
		int height = imp.getHeight();
		Rectangle srcRect = ic.getSrcRect();
		int xstart = srcRect.x;
		int ystart = srcRect.y;
		if (IJ.spaceBarDown() || srcRect.height==height) {
			srcRect.x += rotation*Math.max(width/200, 1);
			if (srcRect.x<0) srcRect.x = 0;
			if (srcRect.x+srcRect.width>width) srcRect.x = width-srcRect.width;
		} else {
			srcRect.y += rotation*Math.max(height/200, 1);
			if (srcRect.y<0) srcRect.y = 0;
			if (srcRect.y+srcRect.height>height) srcRect.y = height-srcRect.height;
		}
		if (srcRect.x!=xstart || srcRect.y!=ystart)
			ic.repaint();
	}

	/** Copies the current ROI to the clipboard. The entire
	    image is copied if there is no ROI. */
	public void copy(boolean cut) {
		imp.copy(cut);
    }
                

	public void paste() {
		imp.paste();
    }
                
    /** This method is called by ImageCanvas.mouseMoved(MouseEvent). 
    	@see ij.gui.ImageCanvas#mouseMoved
    */
    public void mouseMoved(int x, int y) {
    	imp.mouseMoved(x, y);
    }
    
    public String toString() {
    	return imp!=null?imp.getTitle():"";
    }
    
    /** Causes the next image to be opened to be centered on the screen
    	and displayed without informational text above the image. */
    public static void centerNextImage() {
    	centerOnScreen = true;
    }
    
    /** Causes the next image to be displayed at the specified location. */
    public static void setNextLocation(Point loc) {
    	nextLocation = loc;
    }

    /** Causes the next image to be displayed at the specified location. */
    public static void setNextLocation(int x, int y) {
    	nextLocation = new Point(x, y);
    }

    /** Moves and resizes this window. Changes the 
    	 magnification so the image fills the window. */
    public void setLocationAndSize(int x, int y, int width, int height) {
		setBounds(x, y, width, height);
		getCanvas().fitToWindow();
		pack();
	}

	public Color getSubTitleBkgdColor() {
		return subTitleBkgdColor;
	}

	public void setSubTitleBkgdColor(Color subTitleBkgdColor) {
		this.subTitleBkgdColor = subTitleBkgdColor;
	}
	
	/** Overrides the setBounds() method in Component so
		we can find out when the window is resized. */
	//public void setBounds(int x, int y, int width, int height)	{
	//	super.setBounds(x, y, width, height);
	//	ic.resizeSourceRect(width, height);
	//}
	
	public void toggleTagTools() {
		tagButtonPanel.setVisible(!tagButtonPanel.isVisible());
		this.pack();
		int padH = 1+getInsets().left
				+getInsets().right
				+(optionsPanel.isVisible()?optionsPanel.getWidth():0)
				+viewButtonPanel.getWidth();
		int padV = getInsets().top
				+getInsets().bottom
				+((this instanceof StackWindow && ((StackWindow)this).getNScrollbars() >0)?
						((StackWindow)this).getNScrollbars()
						*((StackWindow)this).activeScrollBars.get(0).getHeight()
						:0)
						+overheadPanel.getHeight();
		imp.getWindow().setSize(ic.dstWidth+padH, ic.dstHeight+padV);
	}
	
	public void toggle4DModes() {
		modeButtonPanel.setVisible(!modeButtonPanel.isVisible());
		this.pack();
		int padH = 1+getInsets().left
				+getInsets().right
				+(optionsPanel.isVisible()?optionsPanel.getWidth():0)
				+viewButtonPanel.getWidth();
		int padV = getInsets().top
				+getInsets().bottom
				+(this instanceof StackWindow?
						((StackWindow)this).getNScrollbars()
						*((StackWindow)this).zSelector.getHeight()
						:0)
						+overheadPanel.getHeight();
		imp.getWindow().setSize(ic.dstWidth+padH, ic.dstHeight+padV);
	}

	public void itemStateChanged(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			imp.getStack().setEdges(true);
			imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame()+1);
			imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame()-1);
		}else {
			imp.getStack().setEdges(false);
			imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame()+1);
			imp.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame()-1);
		}

	}

	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand() == "Edges") {
			imp.getStack().setEdges(!imp.getStack().isEdges());
		}
	}



	
} //class ImageWindow

