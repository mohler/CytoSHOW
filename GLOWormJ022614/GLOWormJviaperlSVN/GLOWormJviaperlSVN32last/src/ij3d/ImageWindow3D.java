package ij3d;

import com.sun.j3d.utils.universe.SimpleUniverse;

import javax.media.j3d.View;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.ImageCanvas;
import ij.gui.Toolbar;
import ij.plugin.frame.Channels;
import ij.process.ColorProcessor;
import ij.macro.Interpreter;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

import java.awt.AWTException;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.awt.Label;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowListener;
import java.awt.event.WindowStateListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.lang.reflect.Method;

import javax.media.j3d.Canvas3D;
import javax.media.j3d.GraphicsConfigTemplate3D;
import javax.media.j3d.ImageComponent2D;
import javax.media.j3d.RenderingError;
import javax.media.j3d.RenderingErrorListener;
import javax.media.j3d.Screen3D;
import javax.vecmath.Color3f;

import org.vcell.gloworm.SliceStereoToggle;

public class ImageWindow3D extends JFrame implements FocusListener, WindowListener, UniverseListener {

	private DefaultUniverse universe;
	ImageCanvas3D canvas3D;
	private Label status = new Label("");
	private boolean noOffScreen = true;
	private ErrorListener error_listener;
	private ImagePlus imp;
	private ImageCanvas ic;
	private Panel overheadPanel;
	private Toolbar toolbar;
	protected ImageJ ij;
	private Panel tagButtonPanel;
	private Panel viewButtonPanel;
	private Panel modeButtonPanel;
	private Label countLabel;
	private JButton fullSetButton;
	private JButton hideShowButton;
	private JButton sketch3DButton;
	private JButton sketchVVButton;
	private JButton dupButton;
	private JButton modeButton;
	private JButton slice4dButton;
	private JButton stereo4dxButton;
	private JButton stereo4dyButton;
	private JButton stereo4dXrcButton;
	private SliceStereoToggle sst;
	private JButton stereo4dYrcButton;
	private Panel optionsPanel;


	public ImageWindow3D(String title, DefaultUniverse universe) {
		super(title);
		BorderLayout bl = new BorderLayout();
		String j3dNoOffScreen = System.getProperty("j3d.noOffScreen");
		if (j3dNoOffScreen != null && j3dNoOffScreen.equals("true"))
			noOffScreen = true;
		ij = IJ.getInstance();
		imp = new ImagePlus();
		imp.setTitle("ImageJ 3D Viewer");
		this.universe = universe;
		this.canvas3D = (ImageCanvas3D)universe.getCanvas();
		this.setResizable(false);
		ic = this.canvas3D.getRoiCanvas();
		this.setLayout(bl);

		error_listener = new ErrorListener();
		error_listener.addTo(universe);

		
		addToolBarPanel();
//
		addCommandButtons(imp);

		add(canvas3D, BorderLayout.CENTER);

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				close();
			}
		});

		universe.addUniverseListener(this);
		addFocusListener(this);
		addWindowListener(this);
		updateImagePlus();
		WindowManager.addWindow(this);

	}

	public void addToolBarPanel() {
		overheadPanel = new Panel();
		overheadPanel.setLayout(new GridLayout(1, 1));
		
		toolbar = new Toolbar();
		toolbar.setThreeDViewer(true);

		toolbar.addKeyListener(ij);
		toolbar.addMouseListener(toolbar);
		overheadPanel.add(toolbar);

		this.add(overheadPanel, BorderLayout.NORTH);
		toolbar.setTool(Toolbar.HAND);
		toolbar.repaint();
	}
	
	public void addCommandButtons(ImagePlus imp) throws HeadlessException {
		GridBagLayout fspgridbag = new GridBagLayout();
		GridBagConstraints fspc = new GridBagConstraints();
		GridBagLayout viewgridbag = new GridBagLayout();
		GridBagConstraints vspc = new GridBagConstraints();
		Image3DMenubar menubar = ((Image3DMenubar)((Image3DUniverse)getUniverse()).getMenuBar());

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
		JButton displayButton = new JButton();
		displayButton.setActionCommand("Adjust Object Threshold/Color/Transparency...");
		displayButton.setName("Adjust Object Threshold/Color/Transparency...");
		displayButton.setToolTipText("Adjust Object Threshold/Color/Transparency...");
		displayButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/displayIcon.png")));
		displayButton.setFont(buttonPanelFont);
		viewButtonPanel.add(displayButton, fspc);
		displayButton.addActionListener(menubar);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton photoButton = new JButton();
		photoButton.setActionCommand("Save JPEG snapshot of IJ3DV...");
		photoButton.setName("Save JPEG snapshot of IJ3DV...");
		photoButton.setToolTipText("Save JPEG snapshot of IJ3DV...");
		photoButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/photoIcon32.png")));
		photoButton.setFont(buttonPanelFont);
		viewButtonPanel.add(photoButton, fspc);
		photoButton.addActionListener(menubar);
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
//		viewButtonPanel.add(dupButton, fspc);
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
//		viewButtonPanel.add(zMipButton, fspc);
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
//		viewButtonPanel.add(prjButton, fspc);
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
//		viewButtonPanel.add(vvButton, fspc);
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
//		viewButtonPanel.add(orthoViewButton, fspc);
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
//		viewButtonPanel.add(advancedViewButton, fspc);
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
//		viewButtonPanel.add(modeButton, fspc);
		sst = new SliceStereoToggle(imp);
		modeButton.addActionListener(sst);
		fspc.gridy = y++;
		fspc.weighty = 0.5;
		fspc.fill = GridBagConstraints.BOTH;
		JButton tagsButton = new JButton();
		tagsButton.setActionCommand("Show/Hide Tagging Tools");
		tagsButton.setName("Show/Hide Tagging Tools");
		tagsButton.setToolTipText("Show Tag-Editing Tools");
		tagsButton.setIcon(new ImageIcon(ImageWindow.class.getResource("images/TagsThin.png")));
		tagsButton.setFont(buttonPanelFont);
//		viewButtonPanel.add(tagsButton, fspc);
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

	public DefaultUniverse getUniverse() {
		return universe;
	}

	public ImageCanvas getCanvas() {
		return new ImageCanvas(getImagePlus());
	}

	/* off-screen rendering stuff */
	private Canvas3D offScreenCanvas3D;
	private Canvas3D getOffScreenCanvas() {
		if (offScreenCanvas3D != null)
			return offScreenCanvas3D;

		GraphicsConfigTemplate3D templ = new GraphicsConfigTemplate3D();
		templ.setDoubleBuffer(GraphicsConfigTemplate3D.UNNECESSARY);
		GraphicsConfiguration gc =
			GraphicsEnvironment.getLocalGraphicsEnvironment().
			getDefaultScreenDevice().getBestConfiguration(templ);

		offScreenCanvas3D = new Canvas3D(gc, true);
		Screen3D sOn = canvas3D.getScreen3D();
		Screen3D sOff = offScreenCanvas3D.getScreen3D();
		sOff.setSize(sOn.getSize());
		sOff.setPhysicalScreenWidth(sOn.getPhysicalScreenWidth());
		sOff.setPhysicalScreenHeight(sOn.getPhysicalScreenHeight());

		universe.getViewer().getView().addCanvas3D(offScreenCanvas3D);

		return offScreenCanvas3D;
	}

	private static ImagePlus makeDummyImagePlus() {
		ColorProcessor cp = new ColorProcessor(1, 1);
		return new ImagePlus("3D", cp);
	}

	public void updateImagePlus() {
		//this.imp = getNewImagePlus();
		imp_updater.update();
	}

	public void updateImagePlusAndWait() {
		imp_updater.updateAndWait();
	}

	void quitImageUpdater() {
		imp_updater.quit();
	}

	final ImagePlusUpdater imp_updater = new ImagePlusUpdater();

	private class ImagePlusUpdater extends Thread {
		boolean go = true;
		int update = 0;
		ImagePlusUpdater() {
			super("3D-V-IMP-updater");
			try { setDaemon(true); } catch (Exception e) { e.printStackTrace(); }
			setPriority(Thread.NORM_PRIORITY);
			start();
		}
		void update() {
			synchronized (this) {
				update++;
				notify();
			}
		}
		void updateAndWait() {
			update();
			synchronized (this) {
				while (update > 0) {
					try { wait(); } catch (InterruptedException ie) { ie.printStackTrace(); }
				}
			}
		}
		public void run() {
			while (go) {
				final int u;
				synchronized (this) {
					if (0 == update) {
						try { wait(); } catch (InterruptedException ie) { ie.printStackTrace(); }
					}
					u = update;
				}
				ImageWindow3D.this.imp = getNewImagePlus();
				synchronized (this) {
					if (u != update) continue; // try again, there was a new request
					// Else, done:
					update = 0;
					notify(); // for updateAndWait
				}
			}
		}
		void quit() {
			go = false;
			synchronized (this) {
				update = -Integer.MAX_VALUE;
				notify();
			}
		}
	}

	public ImagePlus getImagePlus() {
		if(imp == null)
			imp_updater.updateAndWait(); //updateImagePlus();
		return imp;
	}

	private int top = 0, bottom = 0, left = 0, right = 0;
	private ImagePlus getNewImagePlus() {
		if (getWidth() <= 0 || getHeight() <= 0)
			return makeDummyImagePlus();
		if (noOffScreen) {
			if (universe != null && universe.getUseToFront())
				toFront();
			Point p = canvas3D.getLocationOnScreen();
			int w = canvas3D.getWidth();
			int h = canvas3D.getHeight();
			Robot robot;
			try {
				robot = new Robot(getGraphicsConfiguration()
					.getDevice());
			} catch (AWTException e) {
				return makeDummyImagePlus();
			}
			Rectangle r = new Rectangle(p.x + left, p.y + top,
					w - left - right, h - top - bottom);
			BufferedImage bImage = robot.createScreenCapture(r);
			ColorProcessor cp = new ColorProcessor(bImage);
			ImagePlus result = new ImagePlus("3d", cp);
			result.setRoi(canvas3D.getRoi());
			return result;
		}
		BufferedImage bImage = new BufferedImage(canvas3D.getWidth(),
				canvas3D.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		ImageComponent2D buffer =
			new ImageComponent2D(ImageComponent2D.FORMAT_RGBA,
					bImage);

		try {
			getOffScreenCanvas();
			offScreenCanvas3D.setOffScreenBuffer(buffer);
			offScreenCanvas3D.renderOffScreenBuffer();
			offScreenCanvas3D.waitForOffScreenRendering();
			bImage = offScreenCanvas3D.getOffScreenBuffer()
				.getImage();
			// To release the reference of buffer inside Java 3D.
			offScreenCanvas3D.setOffScreenBuffer(null);
		} catch (Exception e) {
			noOffScreen = true;
			universe.getViewer().getView()
				.removeCanvas3D(offScreenCanvas3D);
			offScreenCanvas3D = null;
			System.err.println("Java3D error: " +
 				"Off-screen rendering not supported by this\n" +
				"setup. Falling back to screen capturing");
			return getNewImagePlus();
		}


		ColorProcessor cp = new ColorProcessor(bImage);
		ImagePlus result = new ImagePlus("3d", cp);
		result.setRoi(canvas3D.getRoi());
		return result;
	}

	public Label getStatusLabel() {
		return status;
	}

	public boolean close() {
		if (null == universe) return false;
		WindowManager.removeWindow(this);
		universe.removeUniverseListener(this);

		// Must remove the listener so this instance can be garbage
		// collected and removed from the Canvas3D, overcomming the limit
		// of 32 total Canvas3D instances.
		try {
			Method m = SimpleUniverse.class.getMethod(
					"removeRenderingErrorListener",
					new Class[]{RenderingErrorListener.class});
			if (null != m)
				m.invoke(universe, new Object[]{error_listener});
		} catch (Exception ex) {
			System.out.println(
					"Could NOT remove the RenderingErrorListener!");
			ex.printStackTrace();
		}

		if (null != universe.getWindow())
			universe.cleanup();

		imp_updater.quit();
		canvas3D.flush();
		universe = null;
		dispose();
		return true;
	}

	/*
	 * The UniverseListener interface
	 */
	public void universeClosed() {}
	public void transformationStarted(View view) {}
	public void transformationUpdated(View view) {}
	public void contentSelected(Content c) {}
	public void transformationFinished(View view) {
		updateImagePlus();
	}

	public void contentAdded(Content c){
		updateImagePlus();
	}

	public void contentRemoved(Content c){
		updateImagePlus();
	}

	public void contentChanged(Content c){
		updateImagePlus();
	}

	public void canvasResized() {
		updateImagePlus();
	}
	
//	/** Override Container getInsets() to avoid ic, imp, etc... */
//	@Override
//	public Insets getInsets() {
//		return new Insets(0,0,0,0);	
//	}

	public Toolbar getToolbar() {
		return toolbar;
	}

	public void setToolbar(Toolbar toolbar) {
		this.toolbar = toolbar;
	}

	private int lastToolID;
	private boolean closed;

	private class ErrorListener implements RenderingErrorListener {
		public void errorOccurred(RenderingError error) {
			IJ.log(error.getDetailMessage());
			error.printVerbose();
//			throw new RuntimeException(error.getDetailMessage());
		}

		/*
		 * This is a slightly ugly workaround for DefaultUniverse not
		 * having addRenderingErrorListener() in Java3D 1.5.
		 * The problem, of course, is that Java3D 1.5 just exit(1)s
		 * on error by default, _unless_ you add a listener!
		 */
		public void addTo(DefaultUniverse universe) {
			try {
				Class[] params = {
					RenderingErrorListener.class
				};
				Class c = universe.getClass();
				String name = "addRenderingErrorListener";
				Method m = c.getMethod(name, params);
				Object[] list = { this };
				m.invoke(universe, list);
			} catch (Exception e) {
				/* method not found -> Java3D 1.4 */
				System.err.println("Java3D < 1.5 detected");
				//e.printStackTrace();
			}
		}
	}

	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowClosing(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowClosed(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void windowActivated(WindowEvent e) {
		if (IJ.debugMode) IJ.log("windowActivated: "+imp.getTitle());
		ImageJ ij = IJ.getInstance();
		boolean quitting = ij!=null && ij.quitting();
		if (IJ.isMacintosh() && ij!=null && !quitting) {
			IJ.wait(10); // may be needed for Java 1.4 on OS X
			setMenuBar(Menus.getMenuBar());
		}
		toolbar.setTool(toolbar.localCurrent);
	}

	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void focusGained(FocusEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void focusLost(FocusEvent e) {
		// TODO Auto-generated method stub
		
	}
}

