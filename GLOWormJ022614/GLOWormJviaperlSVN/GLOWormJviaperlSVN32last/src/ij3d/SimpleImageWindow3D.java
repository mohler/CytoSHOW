package ij3d;

import com.sun.j3d.utils.universe.SimpleUniverse;

import javax.media.j3d.View;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.Menus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.ImageCanvas2;
import ij.gui.Toolbar;
import ij.plugin.DragAndDrop;
import ij.plugin.frame.Channels;
import ij.process.ColorProcessor;
import ij.macro.Interpreter;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;

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
import java.awt.event.KeyListener;
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

public class SimpleImageWindow3D extends ImageWindow3D  {

//	private DefaultUniverse universe;
//	ImageCanvas3D canvas3D;
//	private Label status = new Label("");
//	private boolean noOffScreen = false;
//	private ErrorListener error_listener;
//	private ImagePlus imp;
//	private ImageCanvas2 ic;
//	private Panel overheadPanel;
//	private Toolbar toolbar;
//	private Image3DMenubar menubar;
//	protected ImageJ ij;
//	private Panel tagButtonPanel;
//	private Panel viewButtonPanel;
//	private Panel modeButtonPanel;
//	private Label countLabel;
//	private JButton fullSetButton;
//	private JButton hideShowButton;
//	private JButton sketch3DButton;
//	private JButton sketchVVButton;
//	private JButton dupButton;
//	private JButton modeButton;
//	private JButton slice4dButton;
//	private JButton stereo4dxButton;
//	private JButton stereo4dyButton;
//	private JButton stereo4dXrcButton;
//	private SliceStereoToggle sst;
//	private JButton stereo4dYrcButton;
//	private Panel optionsPanel;


	public SimpleImageWindow3D(String title, DefaultUniverse universe) {
		super(title, universe);
		if (IJ.isMacOSX())
			this.setResizable(false);
		overheadPanel.setVisible(false);
//		this.toolbar.setVisible(false);
//		this.menubar.setVisible(false);
		viewButtonPanel.setVisible(false);

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
//				close();
				System.exit(0);
			}
		});

	}


}

