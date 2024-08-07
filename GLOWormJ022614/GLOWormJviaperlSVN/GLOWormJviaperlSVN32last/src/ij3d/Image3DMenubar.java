package ij3d;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.media.j3d.View;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

import com.sun.j3d.utils.universe.Viewer;


@SuppressWarnings("serial")
public class Image3DMenubar extends JMenuBar implements ActionListener,
					 		ItemListener,
							UniverseListener, MouseListener {

	private Image3DUniverse univ;
	private IJ3dExecuter iJ3dExecuter;

	private JMenuItem open;
	private JMenuItem addContentFromImage;
	private JMenuItem saveView;
	private JMenuItem loadView;
	private JMenuItem saveSession;
	private JMenuItem loadSession;
	private JMenuItem importObj;
	private JMenuItem importStl;
	private JMenuItem color;
	private JMenuItem bgColor;
	private JCheckBoxMenuItem fullscreen;
	private JMenuItem channels;
	private JMenuItem luts;
	private JMenuItem transparency;
	private JMenuItem threshold;
	private JMenuItem delete;
	private JMenuItem properties;
	private JMenuItem resetView;
	private JMenuItem snapshot;
	private JMenuItem record360;
	private JMenuItem startRecord;
	private JMenuItem stopRecord;
	private JMenuItem startAnimation;
	private JMenuItem stopAnimation;
	private JMenuItem animationOptions;
	private JMenuItem light;
	private JMenuItem viewPreferences;
	private JMenuItem shortcuts;
	private JMenuItem close;
	private JMenuItem setTransform;
	private JMenuItem resetTransform;
	private JMenuItem applyTransform;
	private JMenuItem saveTransform;
	private JMenuItem exportTransformed;
	private JMenuItem exportObj;
	private JMenuItem exportObjs;
	private JMenuItem exportDXF;
	private JMenuItem exportAsciiSTL;
	private JMenuItem exportBinarySTL;
	private JMenuItem exportU3D;
	private JMenuItem scalebar;
	private JMenuItem displayAsVolume;
	private JMenuItem displayAsOrtho;
	private JMenuItem displayAsMultiOrtho;
	private JMenuItem displayAsSurface;
	private JMenuItem displayAsSurfacePlot;
	private JMenuItem centerSelected;
	private JMenuItem centerOrigin;
	private JMenuItem centerUniverse;
	private JCheckBoxMenuItem sync;
	private JMenuItem fitViewToUniverse;
	private JMenuItem fitViewToContent;
	private JMenuItem regist;
	private JMenuItem pl_load;
	private JMenuItem pl_save;
	private JMenuItem pl_size;
	private JMenuItem pl_color;
	private JCheckBoxMenuItem pl_show;
	private JMenuItem j3dproperties;
	private JCheckBoxMenuItem coordinateSystem;
	private JCheckBoxMenuItem boundingBox;
	private JCheckBoxMenuItem allCoordinateSystems;
	private JCheckBoxMenuItem lock;
	private JCheckBoxMenuItem show;
	private JMenuItem viewposXY, viewposXZ, viewposYZ, viewnegXY, viewnegXZ, viewnegYZ;
	private JMenuItem sphere, box, cone, tube;

	private JMenu transformMenu;
	private JMenu landmarksMenu;
	private JMenu editMenu;
	private JMenu selectMenu;
	private JMenu viewMenu;
	private JMenu fileMenu;
	private JMenu helpMenu;
	private JMenu addMenu;
	private JMenuItem newViewer;
	private JMenuItem saveCLView;
	private JMenuItem renameViewer;

	public Image3DMenubar(Image3DUniverse univ) {
		super();
		this.univ = univ;
		this.iJ3dExecuter = univ.getExecuter();

		univ.addUniverseListener(this);

		fileMenu = createFileMenu();
		this.add(fileMenu);

		editMenu = createEditMenu();
		this.add(editMenu);

		viewMenu = createViewMenu();
		this.add(viewMenu);

		addMenu = createAddMenu();
		this.add(addMenu);

		landmarksMenu = createLandmarkMenu();
		this.add(landmarksMenu);

		helpMenu = createHelpMenu();
		this.add(helpMenu);

		contentSelected(null, true);
	}

	public JMenu createFileMenu() {
		JMenu file = new JMenu("File");

		newViewer = new JMenuItem("New Viewer");
		newViewer.addActionListener(this);
		file.add(newViewer);
		
		renameViewer = new JMenuItem("Rename Viewer");
		renameViewer.addActionListener(this);
		file.add(renameViewer);
		
		open = new JMenuItem("Open...");
		open.addActionListener(this);
		file.add(open);

		JMenu importt = new JMenu("Import surfaces");

		importObj = new JMenuItem("WaveFront");
		importObj.addActionListener(this);
		importt.add(importObj);

		importStl = new JMenuItem("STL");
		importStl.addActionListener(this);
		importt.add(importStl);


		file.add(importt);

		JMenu subMenu = new JMenu("Export surfaces");
		file.add(subMenu);
		exportObj = new JMenuItem("Compound WaveFront File");
		exportObj.addActionListener(this);
		subMenu.add(exportObj);

		exportObjs = new JMenuItem("Individual WaveFront Files");
		exportObjs.addActionListener(this);
		subMenu.add(exportObjs);

		exportDXF = new JMenuItem("DXF");
		exportDXF.addActionListener(this);
		subMenu.add(exportDXF);

		exportAsciiSTL = new JMenuItem("STL (ASCII)");
		exportAsciiSTL.addActionListener(this);
		subMenu.add(exportAsciiSTL);

		exportBinarySTL = new JMenuItem("STL (binary)");
		exportBinarySTL.addActionListener(this);
		subMenu.add(exportBinarySTL);

		exportU3D = new JMenuItem("U3D");
		exportU3D.addActionListener(this);
		subMenu.add(exportU3D);

		file.addSeparator();

		saveCLView = new JMenuItem("Save ColorLegend");
		saveCLView.addActionListener(this);
		file.add(saveCLView);

		file.addSeparator();

		saveView = new JMenuItem("Save View");
		saveView.addActionListener(this);
		file.add(saveView);

		loadView = new JMenuItem("Load View");
		loadView.addActionListener(this);
		file.add(loadView);

		file.addSeparator();

		saveSession = new JMenuItem("Save Session");
		saveSession.addActionListener(this);
		file.add(saveSession);

		loadSession = new JMenuItem("Load Session");
		loadSession.addActionListener(this);
		file.add(loadSession);

		file.addSeparator();

		close = new JMenuItem("Quit");
		close.addActionListener(this);
		file.add(close);

		return file;
	}

	public JMenu createAddMenu() {
		JMenu add = new JMenu("Add");
		addContentFromImage = new JMenuItem("From image");
		addContentFromImage.addActionListener(this);
		add.add(addContentFromImage);

		add.addSeparator();

		sphere = new JMenuItem("Sphere");
		sphere.addActionListener(this);
		add.add(sphere);

		box = new JMenuItem("Box");
		box.addActionListener(this);
		add.add(box);

		cone = new JMenuItem("Cone");
		cone.addActionListener(this);
		add.add(cone);

		tube = new JMenuItem("Tube");
		tube.addActionListener(this);
		add.add(tube);

		return add;
	}

	public JMenu createEditMenu() {
		JMenu edit = new JMenu("Edit");

		edit.add(createDisplayAsSubMenu());
		// TODO:
		selectMenu = createSelectMenu();
		edit.add(selectMenu);

		edit.addSeparator();

		luts = new JMenuItem("Transfer function");
		luts.addActionListener(this);
		edit.add(luts);

		channels = new JMenuItem("Change channels");
		channels.addActionListener(this);
		edit.add(channels);

		color = new JMenuItem("Change color");
		color.addActionListener(this);
		edit.add(color);

		transparency = new JMenuItem("Change transparency");
		transparency.addActionListener(this);
		edit.add(transparency);

		threshold = new JMenuItem("Adjust threshold");
		threshold.addActionListener(this);
		edit.add(threshold);

		edit.addSeparator();

		show = new JCheckBoxMenuItem("Show content");
		show.setState(true);
		show.addItemListener(this);
		edit.add(show);

		coordinateSystem = new JCheckBoxMenuItem(
					"Show coordinate system", true);
		coordinateSystem.addItemListener(this);
		edit.add(coordinateSystem);

		boundingBox = new JCheckBoxMenuItem(
					"Show bounding box", false);
		boundingBox.addItemListener(this);
		edit.add(boundingBox);

		allCoordinateSystems = new JCheckBoxMenuItem(
				"Show all coordinate systems", true);
		allCoordinateSystems.addItemListener(this);
		edit.add(allCoordinateSystems);

		edit.addSeparator();

		delete = new JMenuItem("Delete");
		delete.setEnabled(false);
		delete.addActionListener(this);
		edit.add(delete);

		edit.addSeparator();

		properties = new JMenuItem("Object Properties");
		properties.addActionListener(this);
		edit.add(properties);

		edit.addSeparator();


		transformMenu = createTransformMenu();
		edit.add(transformMenu);

		edit.addSeparator();

		shortcuts = new JMenuItem("Keyboard shortcuts");
		shortcuts.addActionListener(this);
		edit.add(shortcuts);

		viewPreferences = new JMenuItem("View Preferences");
		viewPreferences.addActionListener(this);
		edit.add(viewPreferences);

		return edit;
	}

	private JMenu createLandmarkMenu() {
		JMenu pl = new JMenu("Landmarks");

		pl_load = new JMenuItem("Load Point List");
		pl_load.addActionListener(this);
		pl.add(pl_load);

		pl_save = new JMenuItem("Save Point List");
		pl_save.addActionListener(this);
		pl.add(pl_save);

		pl_show = new JCheckBoxMenuItem("Show Point List");
		pl_show.addItemListener(this);
		pl.add(pl_show);

		pl.addSeparator();

		pl_size = new JMenuItem("Point size");
		pl_size.addActionListener(this);
		pl.add(pl_size);

		pl_color = new JMenuItem("Point color");
		pl_color.addActionListener(this);
		pl.add(pl_color);

		pl.addSeparator();

		regist = new JMenuItem("Register");
		regist.addActionListener(this);
		pl.add(regist);

		return pl;
	}

	public JMenu createSelectMenu() {
		JMenu selectM = new JMenu("Select");
		final JCheckBoxMenuItem none = new JCheckBoxMenuItem("None");
		none.addItemListener(new ItemListener() {
			
			public void itemStateChanged(ItemEvent e) {
				if(e.getStateChange() == ItemEvent.SELECTED)
					iJ3dExecuter.select(null);
			}
		});
		selectM.add(none);

		return selectM;
	}

	public JMenu createTransformMenu() {
		JMenu transform = new JMenu("Transformation");

		lock = new JCheckBoxMenuItem("Lock");
		lock.addItemListener(this);
		transform.add(lock);

		setTransform = new JMenuItem("Set Transform");
		setTransform.addActionListener(this);
		transform.add(setTransform);

		resetTransform = new JMenuItem("Reset Transform");
		resetTransform.addActionListener(this);
		transform.add(resetTransform);

		applyTransform = new JMenuItem("Apply Transform");
		applyTransform.addActionListener(this);
		transform.add(applyTransform);

		saveTransform = new JMenuItem("Save Transform");
		saveTransform.addActionListener(this);
		transform.add(saveTransform);

		transform.addSeparator();

		exportTransformed= new JMenuItem("Export transformed image");
		exportTransformed.addActionListener(this);
		transform.add(exportTransformed);

		return transform;
	}

	public JMenu createViewMenu() {
		JMenu view = new JMenu("View");

		resetView = new JMenuItem("Reset view");
		resetView.addActionListener(this);
		view.add(resetView);

		// center submenu
		JMenu menu = new JMenu("Center");
		centerSelected = new JMenuItem("Selected content");
		centerSelected.addActionListener(this);
		menu.add(centerSelected);

		centerOrigin = new JMenuItem("Origin");
		centerOrigin.addActionListener(this);
		menu.add(centerOrigin);

		centerUniverse = new JMenuItem("Universe");
		centerUniverse.addActionListener(this);
		menu.add(centerUniverse);
		view.add(menu);

		// fit view submenu
		menu = new JMenu("Fit view to");
		fitViewToUniverse = new JMenuItem("Universe");
		fitViewToUniverse.addActionListener(this);
		menu.add(fitViewToUniverse);

		fitViewToContent = new JMenuItem("Selected content");
		fitViewToContent.addActionListener(this);
		menu.add(fitViewToContent);
		view.add(menu);

		menu = new JMenu("Set view");
		viewposXY = new JMenuItem("+ XY"); viewposXY.addActionListener(this); menu.add(viewposXY);
		viewposXZ = new JMenuItem("+ XZ"); viewposXZ.addActionListener(this); menu.add(viewposXZ);
		viewposYZ = new JMenuItem("+ YZ"); viewposYZ.addActionListener(this); menu.add(viewposYZ);
		viewnegXY = new JMenuItem("- XY"); viewnegXY.addActionListener(this); menu.add(viewnegXY);
		viewnegXZ = new JMenuItem("- XZ"); viewnegXZ.addActionListener(this); menu.add(viewnegXZ);
		viewnegYZ = new JMenuItem("- YZ"); viewnegYZ.addActionListener(this); menu.add(viewnegYZ);
		view.add(menu);

		view.addSeparator();

		snapshot = new JMenuItem("Take snapshot");
		snapshot.addActionListener(this);
		view.add(snapshot);

		view.addSeparator();

		record360 = new JMenuItem("Record 360 deg rotation");
		record360.addActionListener(this);
		view.add(record360);

		startRecord = new JMenuItem("Start freehand recording");
		startRecord.addActionListener(this);
		view.add(startRecord);

		stopRecord = new JMenuItem("Stop freehand recording");
		stopRecord.addActionListener(this);
		view.add(stopRecord);

		view.addSeparator();

		startAnimation = new JMenuItem("Start animation");
		startAnimation.addActionListener(this);
		view.add(startAnimation);

		stopAnimation = new JMenuItem("Stop animation");
		stopAnimation.addActionListener(this);
		view.add(stopAnimation);

		animationOptions = new JMenuItem("Change animation options");
		animationOptions.addActionListener(this);
		view.add(animationOptions);

		view.addSeparator();

		sync = new JCheckBoxMenuItem("Sync view");
		sync.addItemListener(this);
		view.add(sync);

		view.addSeparator();

		scalebar = new JMenuItem("Edit Scalebar");
		scalebar.addActionListener(this);
		view.add(scalebar);

		view.addSeparator();

		light = new JMenuItem("Adjust light");
		light.addActionListener(this);
		view.add(light);

		bgColor = new JMenuItem("Change background color");
		bgColor.addActionListener(this);
		view.add(bgColor);

		fullscreen = new JCheckBoxMenuItem("Fullscreen");
		fullscreen.setState(univ.isFullScreen());
		fullscreen.addItemListener(this);
		view.add(fullscreen);

		return view;
	}

	public JMenu createHelpMenu() {
		JMenu help = new JMenu("Help");
		j3dproperties = new JMenuItem("Java 3D Properties");
		j3dproperties.addActionListener(this);
		help.add(j3dproperties);
		return help;
	}

	public JMenu createAttributesSubMenu() {
		JMenu attributes = new JMenu("Attributes");




		return attributes;
	}

	public JMenu createDisplayAsSubMenu() {
		JMenu display = new JMenu("Display as");

		displayAsVolume = new JMenuItem("Volume");
		displayAsVolume.addActionListener(this);
		display.add(displayAsVolume);

		displayAsOrtho = new JMenuItem("Orthoslice");
		displayAsOrtho.addActionListener(this);
		display.add(displayAsOrtho);

		displayAsMultiOrtho = new JMenuItem("Multi-orthoslice");
		displayAsMultiOrtho.addActionListener(this);
		display.add(displayAsMultiOrtho);

		displayAsSurface = new JMenuItem("Surface");
		displayAsSurface.addActionListener(this);
		display.add(displayAsSurface);

		displayAsSurfacePlot = new JMenuItem("Surface Plot 2D");
		displayAsSurfacePlot.addActionListener(this);
		display.add(displayAsSurfacePlot);

		return display;
	}

	
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();

		if(src == color)
			iJ3dExecuter.changeColor(getSelected());
		else if (src == renameViewer) {
			String newTitle = IJ.getString("Rename Viewer...", univ.getTitle());
			if (!newTitle.contentEquals(""))
					univ.setTitle(newTitle);
		}else if (src == bgColor)
			iJ3dExecuter.changeBackgroundColor();
		else if(src == scalebar)
			iJ3dExecuter.editScalebar();
		else if(src == luts)
			iJ3dExecuter.adjustLUTs(getSelected());
		else if(src == channels)
			iJ3dExecuter.changeChannels(getSelected());
		else if(src == transparency)
			iJ3dExecuter.changeTransparency(getSelected());
		else if(src == open)
			iJ3dExecuter.addContentFromFile();
		else if(src == addContentFromImage)
			iJ3dExecuter.addContentFromImage(null);
		else if(src == regist)
			iJ3dExecuter.register();
		else if(src == delete)
			iJ3dExecuter.delete(getSelected());
		else if(src == resetView)
			iJ3dExecuter.resetView();
		else if(src == centerSelected)
			iJ3dExecuter.centerSelected(getSelected());
		else if(src == centerOrigin)
			iJ3dExecuter.centerOrigin();
		else if(src == centerUniverse)
			iJ3dExecuter.centerUniverse();
		else if(src == fitViewToUniverse)
			iJ3dExecuter.fitViewToUniverse();
		else if(src == fitViewToContent)
			iJ3dExecuter.fitViewToContent(getSelected());
		else if(src == snapshot || e.getActionCommand().toLowerCase().contains("snapshot"))
			iJ3dExecuter.snapshot();
		else if(src == record360)
			iJ3dExecuter.record360();
		else if(src == startRecord)
			iJ3dExecuter.startFreehandRecording();
		else if(src == stopRecord)
			iJ3dExecuter.stopFreehandRecording();
		else if(src == startAnimation)
			iJ3dExecuter.startAnimation();
		else if(src == stopAnimation)
			iJ3dExecuter.stopAnimation();
		else if(src == animationOptions)
			iJ3dExecuter.changeAnimationOptions();
		else if(src == threshold || e.getActionCommand().toLowerCase().contains("threshold/color"))
			iJ3dExecuter.changeThreshold(getSelected());
		else if(src == displayAsVolume) {
			iJ3dExecuter.displayAs(getSelected(), Content.VOLUME);
			updateMenus();
		} else if(src == displayAsOrtho) {
			iJ3dExecuter.displayAs(getSelected(), Content.ORTHO);
			updateMenus();
		} else if(src == displayAsMultiOrtho) {
			iJ3dExecuter.displayAs(getSelected(), Content.MULTIORTHO);
			updateMenus();
		} else if(src == displayAsSurface) {
			iJ3dExecuter.displayAs(getSelected(), Content.SURFACE);
			updateMenus();
		} else if(src == displayAsSurfacePlot) {
			iJ3dExecuter.displayAs(
				getSelected(), Content.SURFACE_PLOT2D);
			updateMenus();
		} else if(src == close)
			iJ3dExecuter.close();
		else if(src == resetTransform)
			iJ3dExecuter.resetTransform(getSelected());
		else if(src == setTransform)
			iJ3dExecuter.setTransform(getSelected());
		else if(src == properties)
			iJ3dExecuter.contentProperties(getSelected());
		else if(src == applyTransform)
			iJ3dExecuter.applyTransform(getSelected());
		else if(src == saveTransform)
			iJ3dExecuter.saveTransform(getSelected());
		else if(src == exportTransformed)
			iJ3dExecuter.exportTransformed(getSelected());
		else if (src == pl_load)
			iJ3dExecuter.loadPointList(getSelected());
		else if (src == pl_save)
			iJ3dExecuter.savePointList(getSelected());
		else if (src == pl_size)
			iJ3dExecuter.changePointSize(getSelected());
		else if (src == pl_color)
			iJ3dExecuter.changePointColor(getSelected());
		else if (src == saveView)
			iJ3dExecuter.saveView();
		else if (src == loadView)
			iJ3dExecuter.loadView();
		else if (src == saveSession)
			iJ3dExecuter.saveSession();
		else if (src == loadSession)
			iJ3dExecuter.loadSession();
		else if (src == importObj)
			iJ3dExecuter.importWaveFront();
		else if (src == importStl)
			iJ3dExecuter.importSTL();
		else if (src == exportDXF)
			iJ3dExecuter.saveAsDXF();
		else if (src == exportObj)
			iJ3dExecuter.saveAsWaveFront(true);
		else if (src == exportObjs)
			iJ3dExecuter.saveAsWaveFront(false);
		else if (src == saveCLView)
			iJ3dExecuter.saveColorLegend(null);
		else if (src == exportU3D)
			iJ3dExecuter.saveAsU3D();
		else if (src == exportAsciiSTL)
			iJ3dExecuter.saveAsAsciiSTL();
		else if (src == exportBinarySTL)
			iJ3dExecuter.saveAsBinarySTL();
		else if (src == light)
			iJ3dExecuter.adjustLight();
		else if (src == viewPreferences)
			iJ3dExecuter.viewPreferences();
		else if (src == shortcuts)
			iJ3dExecuter.editShortcuts();
		else if(src == j3dproperties)
			iJ3dExecuter.j3dproperties();
		else if (viewposXY == src)
			iJ3dExecuter.execute(new Runnable() { 
			public void run() { univ.rotateToPositiveXY(); }});
		else if (viewposXZ == src)
			iJ3dExecuter.execute(new Runnable() { 
			public void run() { univ.rotateToPositiveXZ(); }});
		else if (viewposYZ == src)
			iJ3dExecuter.execute(new Runnable() { 
			public void run() { univ.rotateToPositiveYZ(); }});
		else if (viewnegXY == src)
			iJ3dExecuter.execute(new Runnable() { 
			public void run() { univ.rotateToNegativeXY(); }});
		else if (viewnegXZ == src)
			iJ3dExecuter.execute(new Runnable() { 
			public void run() { univ.rotateToNegativeXZ(); }});
		else if (viewnegYZ == src)
			iJ3dExecuter.execute(new Runnable() { 
			public void run() { univ.rotateToNegativeYZ(); }});
		else if(src == sphere) {
			iJ3dExecuter.addSphere();
		} else if(src == box) {
			iJ3dExecuter.addBox();
		} else if(src == cone) {
			iJ3dExecuter.addCone();
		} else if(src == tube) {
			iJ3dExecuter.addTube();
		}else if (e.getActionCommand().toLowerCase().contains("synched 3d controls")) {
			//sync(true) now done by mouseclicked...
		}else if (e.getActionCommand().toLowerCase().contains("solo 3d controls")) {
			//sync(false) now done by mouseclicked...
		}else if (e.getActionCommand().toLowerCase().contains("auto adjust while adding")) {
			((JButton)e.getSource()).setActionCommand("No adjust while adding");
			((JButton)e.getSource()).setName("No adjust while adding");
			((JButton)e.getSource()).setToolTipText("No adjust while adding");
			((JButton)e.getSource()).setIcon(new ImageIcon(ImageWindow.class.getResource("images/NOautoButton32.png")));
			((Image3DUniverse)(((ImageWindow3D)this.getParent().getParent().getParent().getParent().getParent()).getUniverse())).setAutoAdjustView(true);
		}else if (e.getActionCommand().toLowerCase().contains("no adjust while adding")) {
			((JButton)e.getSource()).setActionCommand("Auto adjust while adding");
			((JButton)e.getSource()).setName("Auto adjust while adding");
			((JButton)e.getSource()).setToolTipText("Auto adjust while adding");
			((JButton)e.getSource()).setIcon(new ImageIcon(ImageWindow.class.getResource("images/autoButton32.png")));
			((Image3DUniverse)(((ImageWindow3D)this.getParent().getParent().getParent().getParent().getParent()).getUniverse())).setAutoAdjustView(false);
		} else if(src == newViewer) {
			  new Image3DUniverse().show(false);
		}



	}

	
	public void itemStateChanged(ItemEvent e) {
		Object src = e.getSource();
		Content c = getSelected();
		if(src == coordinateSystem)
			iJ3dExecuter.showCoordinateSystem(
				c, coordinateSystem.getState());
		else if (src == allCoordinateSystems)
			iJ3dExecuter.showAllCoordinateSystems(
				allCoordinateSystems.getState());
		else if(src == boundingBox)
			iJ3dExecuter.showBoundingBox(
				c, boundingBox.getState());
		else if(src == show)
			iJ3dExecuter.showContent(c, show.getState());
		else if(src == lock)
			iJ3dExecuter.setLocked(c, lock.getState());
		else if (src == pl_show)
			iJ3dExecuter.showPointList(c, pl_show.getState());
		else if (src == sync)
			iJ3dExecuter.sync(sync.getState());
		else if (src == fullscreen)
			iJ3dExecuter.setFullScreen(fullscreen.getState());
	}

	private Content getSelected() {
		if (univ.getContent3DManager() != null
				&& univ.getContent3DManager().getSelectedContentInstantsAsArray() != null 
				&& univ.getContent3DManager().getSelectedContentInstantsAsArray().length > 1)
			return null;
		Content c = univ.getSelected();
		if(c != null)
			return c;
		if(univ.getContents().size() == 1)
			return (Content)univ.contents().next();
		return null;
	}







	// Universe Listener interface
	
	public void transformationStarted(View view) {}
	
	public void transformationFinished(View view) {}
	
	public void canvasResized() {}
	
	public void transformationUpdated(View view) {}
	
	public void contentChanged(Content c, boolean updateNow) {}
	
	public void universeClosed() {}

	
	public void contentAdded(Content c, boolean updateNow) {
		updateMenus();
		if(c == null)
			return;
		final String name = c.getName();
		final JCheckBoxMenuItem item = new JCheckBoxMenuItem(name);
		item.addItemListener(new ItemListener() {
			
			public void itemStateChanged(ItemEvent e) {
//				item.setSelected(!item.isSelected());
				if(e.getStateChange() == ItemEvent.SELECTED)
					iJ3dExecuter.select(name);
//				else
//					iJ3dExecuter.select(null);
			}
		});
		selectMenu.add(item);
	}

	
	public void contentRemoved(Content c, boolean updateNow) {
		updateMenus();
		if(c == null)
			return;
		for(int i = 0; i < selectMenu.getItemCount(); i++) {
			JMenuItem item = selectMenu.getItem(i);
			if(item.getText().equals(c.getName())) {
				selectMenu.remove(i);
				return;
			}
		}
	}


	
	public void contentSelected(Content c, boolean updateNow) {
		updateMenus();
	}

	public void updateMenus() {
		SwingUtilities.invokeLater(new Runnable() {
			
			public void run() {
				doUpdateMenus();
			}
		});
	}

	private void doUpdateMenus() {

		fullscreen.setState(univ.isFullScreen());

		Content c = getSelected();

		delete.setEnabled(c != null);
		centerSelected.setEnabled(c != null);

		displayAsVolume.setEnabled(c != null);
		displayAsSurface.setEnabled(c != null);
		displayAsSurfacePlot.setEnabled(c != null);
		displayAsOrtho.setEnabled(c != null);
		properties.setEnabled(c != null);

		color.setEnabled(c != null);
		transparency.setEnabled(c != null);
		threshold.setEnabled(c != null);
		channels.setEnabled(c != null);

		show.setEnabled(c != null);
		coordinateSystem.setEnabled(c != null);

		pl_load.setEnabled(c != null);
		pl_save.setEnabled(c != null);
		pl_show.setEnabled(c != null);
		pl_size.setEnabled(c != null);
		pl_color.setEnabled(c != null);

		lock.setEnabled(c != null);
		setTransform.setEnabled(c != null);
		applyTransform.setEnabled(c != null);
		resetTransform.setEnabled(c != null);
		saveTransform.setEnabled(c != null);
		exportTransformed.setEnabled(c != null);

		// update select menu
		Content sel = univ.getSelected();
		for(int i = 0; i < selectMenu.getItemCount(); i++) {
			JMenuItem item = selectMenu.getItem(i);
			((JCheckBoxMenuItem)item).setState(sel != null &&
				sel.getName().equals(item.getText()));
		}


		if(c == null)
			return;

		int t = c.getType();

		coordinateSystem.setState(c.hasCoord());
		lock.setState(c.isLocked());
		show.setState(c.isVisible());
		pl_show.setState(c.isPLVisible());

		ImagePlus i = c.getImage();
		displayAsVolume.setEnabled(t != Content.VOLUME && i != null);
		displayAsOrtho.setEnabled(t != Content.ORTHO && i != null);
		displayAsSurface.setEnabled(t != Content.SURFACE && i != null);
		displayAsSurfacePlot.setEnabled(
				t != Content.SURFACE_PLOT2D && i != null);
		displayAsMultiOrtho.setEnabled(t != Content.MULTIORTHO && i != null);
	}

	@Override
	public void mouseClicked(MouseEvent e) {

	}

	@Override
	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.isShiftDown()) {
			univ.setSynchNewState(true);
		} else {
			univ.setSynchNewState(false);
		}
		if (((JButton)e.getSource()).getName() == "Solo 3D controls") {
			univ.sync(false);
			((JButton)e.getSource()).setActionCommand("Synched 3D controls");
			((JButton)e.getSource()).setName("Synched 3D controls");
			((JButton)e.getSource()).setToolTipText("Synched 3D controls");
			((JButton)e.getSource()).setIcon(new ImageIcon(ImageWindow.class.getResource("images/Syncbutton32.png")));
		} else if (((JButton)e.getSource()).getName() == "Synched 3D controls") {
			univ.sync(true);
			((JButton)e.getSource()).setActionCommand("Solo 3D controls");
			((JButton)e.getSource()).setName("Solo 3D controls");
			((JButton)e.getSource()).setToolTipText("Solo 3D controls");
			((JButton)e.getSource()).setIcon(new ImageIcon(ImageWindow.class.getResource("images/Solobutton32.png")));
		}
		
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}
}

