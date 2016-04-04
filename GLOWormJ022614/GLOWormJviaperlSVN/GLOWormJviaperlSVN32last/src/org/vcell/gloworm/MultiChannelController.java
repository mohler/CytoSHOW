package org.vcell.gloworm;
/** Copied from code for Channels.java. Modified to allow time-shifting and Z-shifting spinners and positioners for XY overlay shifting of individual channels. **/


import ij.plugin.frame.*;
import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.io.SaveDialog;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.SocketException;
import java.util.Date;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SpinnerListModel;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileSystemView;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import ij.text.TextWindow;
import ij.util.Java2;
import quicktime.QTException;
import quicktime.QTSession;
import quicktime.io.QTFile;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import client.RemoteMQTVSHandler;
import client.RemoteMQTVSHandler.RemoteMQTVirtualStack;


/** Displays the ImageJ Channels window. */
/**
 * @author mohler
 *
 */
public class MultiChannelController extends PlugInFrame implements PlugIn, ItemListener, MouseListener, ActionListener /*, AdjustmentListener*/, ChangeListener {
	private boolean firstBuild;
	private static String[] modes = {"Composite", "Color", "Gray"};
	private static String[] menuItems = {"Save Scene", "Share Scene", "-", "Make Composite", "Convert to RGB", "Split Channels", "Merge Channels...", "Edit LUT..."};

	public  String[] channelLUTItems =  { "Red", "Green", "Blue", "Grays","Cyan", "Magenta", "Yellow",  "Fire", "Ice", "Spectrum", "3-3-2 RGB"};

	private static String moreLabel = "More..."+'\u00bb';
	private static String channelLUTLabel = "LUT..."+'\u00bb';	
	private static String saveLabel = "Save for Ch";
	private static String loadLabel = "Load for Ch";

	//private String[] title = {"Red", "Green", "Blue"};
	private Choice choice;
	private String[] saveName;
	private Choice[] channelLUTChoice;


	private Checkbox[] visibility, flipVCB,flipHCB, flipZCB;

	private JSpinner[] channelLUTSpinner ;
	private JSpinner[] sliceSpinner ;
	private JSpinner[] frameSpinner ;
	private JSpinner[] deltaZSpinner ;
	private JSpinner[] deltaTSpinner ;
	private JSpinner[] scaleXSpinner ;
	private JSpinner[] scaleYSpinner ;
	private JSpinner[] scaleZSpinner;
	private JSpinner[] rotateAngleSpinner ;
	private JSpinner[] translateXSpinner ;
	private JSpinner[] translateYSpinner ;
	private JTextField[] dropFramesField;


	private Button moreButton;
	private Button[] saveButton;
	private Button[] loadButton;

	private static Frame instance;
	private ImagePlus lastImage;
	private int id;
	private static Point location;
	private PopupMenu pm;
	GridBagLayout gridbag;
	GridBagConstraints c;
	private Label[] label;
	private int previousShiftZ[], previousShiftT[];
	Panel channelPanel[], slicePanel, framePanel, deltaZPanel, deltaTPanel, 
	scaleXPanel, scaleYPanel, scaleZPanel, rotateAnglePanel, translateXPanel, translateYPanel;

	Label minSliceLabel, maxSliceLabel, minFrameLabel, maxFrameLabel, 
	mindeltaZLabel, maxdeltaZLabel, deltaZLabel, mindeltaTLabel, maxdeltaTLabel, zLabel, tLabel, deltaTLabel, 
	minscaleXLabel, maxscaleXLabel, scaleXLabel, minscaleYLabel, maxscaleYLabel, scaleYLabel, scaleZLabel,
	minrotateAngleLabel, maxrotateAngleLabel, rotateAngleLabel, 
	mintranslateXLabel, maxtranslateXLabel, translateXLabel, mintranslateYLabel, maxtranslateYLabel, translateYLabel, dropFramesLabel;
	Font monoFont = new Font("Monospaced", Font.PLAIN, 12);
	private ImagePlus imp = null;
	private boolean notComposite;
	private boolean openingNewMovie;
	private File deNovoMovieFile;
	private boolean sharing;
	private JTextField[] channelNameField;
	private int nCheckBoxes;
	public boolean doingFirstSetup;
	private Button showButton;
	private TextWindow tw;
	private File saveFile;
	private boolean nonMovieMCC;
	private Panel dropFramesPanel;


	public boolean isSharing() {
		return sharing;
	}

	public void setSharing(boolean sharing) {
		this.sharing = sharing;
	}

	public MultiChannelController() {
		this(WindowManager.getCurrentImage());
	}

	public MultiChannelController(ImagePlus mcImp) {

		super("Multi-Channel Controller: "+ (mcImp != null?mcImp.getTitle():"") );


		this.imp = mcImp;
		if (imp == null)
			this.imp = WindowManager.getCurrentImage();
		if (imp == null) {
			this.dispose();
			return;
		}
		if (imp.getMultiChannelController() != null) {
			imp.getMultiChannelController().setVisible(true);
			imp.getMultiChannelController().setSize(175*imp.getNChannels(), 250);
			imp.getMultiChannelController().toFront();
			this.dispose();
			return;

		} else if (!(imp.getImageStack() instanceof MultiQTVirtualStack) && !(imp.getImageStack() instanceof RemoteMQTVirtualStack) && deNovoMovieFile==null) {
			GenericDialog gds = new GenericDialog("Convert to CytoSHOW Format?"); 
			gds.addMessage("This type of Image Stack does not work directly"
					+ "\nwith a MultiChannelController"
					+ "\nor with CytoSHOW's instant Scene sharing."
					+ "\nWould you like to create a version that is "
					+ "\ncompatible with a shared CytoSHOW Scene?");
			//			gds.addRadioButtonGroup("", new String[]{"Save Scene","Share Scene"}, 1, 2, "Save Scene");
			gds.addChoice("", new String[]{"Save Scene","Share Scene"},"Share Scene");
			gds.showDialog();
			if (gds.wasOKed()) {
				sharing  = gds.getNextChoice().equals("Share Scene");
				nonMovieMCC = true;
				this.actionPerformed(new ActionEvent(this, 0, "Save Scene") );
			} 
//			this.dispose();
//			return;

		}

		if (imp.getNDimensions() < 3) {
			for (int i=0; i<WindowManager.getIDList().length;i++) {
				if (WindowManager.getImage(WindowManager.getIDList()[i]).getStack() instanceof MultiQTVirtualStack &&
						((MultiQTVirtualStack) WindowManager.getImage(WindowManager.getIDList()[i]).getStack()).getLineageMapImage() == imp){
					imp = WindowManager.getImage(WindowManager.getIDList()[i]);
				}
			}
		}

		addMouseListener(this);

		CompositeImage ci = getCompositeImage();
		lastImage = imp;
		if ( ci == null || !ci.isComposite()) {
			//			if (IJ.debugMode) IJ.log("Not Composite");
			//			return;
			notComposite = true;
		}


		//		WindowManager.addWindow(this);
		instance = this;
		ScrollPane fsp = new ScrollPane();
		gridbag = new GridBagLayout();
		c = new GridBagConstraints();
		gridbag.setConstraints(fsp, c);
		JPanel fspp = new JPanel(gridbag);
		fsp.add(fspp, c);
		fspp.setLayout(gridbag);
		this.add(fsp);

		((JComponent) fspp).setToolTipText("<html>Adjust space/time synch-ing of <br>multiple movie channels <br>in the same window.</html>");		
		fspp.setBackground(Color.white);

		int y = 0;
		int x = 0;
		c.gridx = 0;
		c.gridy = y++;
		c.gridwidth = 1;

		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.CENTER;
		int margin = 32;
		if (IJ.isVista())
			margin = 40;
		else if (IJ.isMacOSX())
			margin = 18;
		c.insets = new Insets(10, margin, 10, margin);
		showButton = new  Button("Show Scene Details");
		showButton.addActionListener(this);
		fspp.add(showButton, c);
		c.gridy = y++;
		if (imp.getNChannels() >1 && imp.isComposite()) {
			choice = new Choice();
			for (int i = 0; i < modes.length; i++)
				choice.addItem(modes[i]);
			choice.select(0);
			choice.addItemListener(this);
			fspp.add(choice, c);
		}
		nCheckBoxes = imp!=null?imp.getNChannels():1;

		pack();
//		setVisible(true);
		
		
		if (!nonMovieMCC) {
			if (IJ.debugMode) IJ.log("" + nCheckBoxes);
			if (nCheckBoxes>CompositeImage.MAX_CHANNELS)
				nCheckBoxes = CompositeImage.MAX_CHANNELS;
			saveName = new String[nCheckBoxes];
			visibility = new Checkbox[nCheckBoxes];
			channelLUTChoice = new Choice[nCheckBoxes];
			channelLUTSpinner = new JSpinner[nCheckBoxes];
			flipVCB = new Checkbox[nCheckBoxes];
			flipHCB = new Checkbox[nCheckBoxes];
			flipZCB = new Checkbox[nCheckBoxes];
			sliceSpinner = new JSpinner[nCheckBoxes];
			frameSpinner = new JSpinner[nCheckBoxes];
			deltaZSpinner = new JSpinner[nCheckBoxes];
			deltaTSpinner = new JSpinner[nCheckBoxes];
			scaleXSpinner = new JSpinner[nCheckBoxes];
			scaleYSpinner = new JSpinner[nCheckBoxes];
			scaleZSpinner = new JSpinner[nCheckBoxes];
			rotateAngleSpinner = new JSpinner[nCheckBoxes];
			translateXSpinner = new JSpinner[nCheckBoxes];
			translateYSpinner = new JSpinner[nCheckBoxes];
			dropFramesField = new JTextField[nCheckBoxes];

			saveButton = new Button[nCheckBoxes];
			loadButton = new Button[nCheckBoxes];


			label = new Label[nCheckBoxes];
			previousShiftZ = new int[nCheckBoxes];
			previousShiftT = new int[nCheckBoxes];
			channelNameField = new JTextField[nCheckBoxes];

			for (int i=0; i<nCheckBoxes; i++) {

				/*********/
				{
					x= i;
					c.gridx = x;
					y = 1;
					c.gridy = y;
				}
				/*********/

				zLabel = new Label("Shift Z", Label.CENTER);
				tLabel = new Label("Shift T", Label.CENTER);
				deltaZLabel = new Label("Rel. dZ", Label.CENTER);
				deltaTLabel = new Label("Rel. dT", Label.CENTER);
				scaleXLabel = new Label("Scale X", Label.CENTER);
				scaleYLabel = new Label("Scale Y", Label.CENTER);
				scaleZLabel = new Label("Scale Z", Label.CENTER);
				rotateAngleLabel = new Label("Rotate", Label.CENTER);
				translateXLabel = new Label("Shift X", Label.CENTER);
				translateYLabel = new Label("Shift Y", Label.CENTER);
				dropFramesLabel = new Label("Drop Frames", Label.CENTER);

				previousShiftZ[i] = 0;  
				previousShiftT[i] = 0;

				//			ImagePlus imp = WindowManager.getCurrentImage();
				if (imp != null) {
					ImageStack stack = imp.getStack();		


					if (stack instanceof MultiQTVirtualStack && 
							((MultiQTVirtualStack) imp.getMotherImp().getStack()).getVirtualStack(i) != null) {
						saveName[i] = (stack instanceof MultiQTVirtualStack)?
								((MultiQTVirtualStack) stack).getVirtualStack(i).getMovieFile().getName():
									((QTVirtualStack) stack).getMovieFile().getName();
					} else 	if (imp.getRemoteMQTVSHandler() != null) {
						saveName[i] = imp.getRemoteMQTVSHandler().getChannelPathNames()[i].replaceAll(".*/", "");
					}


					if (IJ.debugMode) IJ.log(saveName[i]);
					ScrollPane mnsp = new ScrollPane();
					c.gridy = y++;
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(mnsp, c);
					String fitName = "";
					//				if (saveName[i].matches("(.*(slc|prx|pry)).*") )
					//					saveName[i].replaceAll("(.*(slc|prx|pry)).*", "$1");
					channelNameField[i] = new JTextField(saveName[i]);
					channelNameField[i].setFocusable(true);
					channelNameField[i].setEditable(false);

					//				movieNamePanel.add("Center", channelNameField[i]);
					mnsp.setPreferredSize(new Dimension(25,50));
					mnsp.add(channelNameField[i]);
					fspp.add(mnsp);
					c.gridy = y++;


					visibility[i] = new Checkbox("Channel "+(i+1), true);
					//				c.insets = new Insets(0, 25, i<nCheckBoxes?0:10, 5);		//Is this line giving the weird spacing?
					c.gridy = y++;
					if (imp.getNChannels() >1) fspp.add(visibility[i], c);		
					c.gridy = y++;

					channelLUTChoice[i] = new Choice();
					for (int k=0; k<channelLUTItems.length; k++)
						channelLUTChoice[i].addItem(channelLUTItems[k]);
					channelLUTChoice[i].select(3);
					channelLUTChoice[i].addItemListener(this);
					if ( imp.isComposite() ) fspp.add(channelLUTChoice[i], c);


					flipVCB[i] = new Checkbox("FlipV", false);
					flipHCB[i] = new Checkbox("FlipH", false);
					flipZCB[i] = new Checkbox("FlipZ", false);
					c.gridy = y++;

					fspp.add(flipVCB[i] , c);		
					c.gridy = y++;
					fspp.add(flipHCB[i] , c);		
					c.gridy = y++;
					fspp.add(flipZCB[i] , c);		
					c.gridy = y++;
					c.gridy = y++;

					slicePanel = new Panel();
					c.gridy = y++;
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(slicePanel, c);
					slicePanel.setLayout(new BorderLayout());
					slicePanel.add("Center", zLabel);
					fspp.add(slicePanel);

					sliceSpinner[i]= new JSpinner(new SpinnerNumberModel(0, 0 - ( (imp).getNSlices() ) , ( (imp).getNSlices() ), 1));
					sliceSpinner[i].setToolTipText("Shift Channel "+ (i+1) +"  display along Z axis by # slices or rotation increments");
					c.gridy = y++;
					fspp.add(sliceSpinner[i], c);

					deltaZPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(deltaZPanel, c);
					deltaZPanel.setLayout(new BorderLayout());
					deltaZPanel.add("Center", deltaZLabel);
					fspp.add(deltaZPanel);

					deltaZSpinner[i]= new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
					deltaZSpinner[i].setToolTipText("Adjust Channel "+ (i+1) +" 's Z-axis spacing of slices or rotation increments relative to other Channel(s)");
					c.gridy = y++;
					fspp.add(deltaZSpinner[i], c);
					c.gridy = y++;


					framePanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(framePanel, c);
					framePanel.setLayout(new BorderLayout());
					framePanel.add("Center", tLabel);
					fspp.add(framePanel);

					frameSpinner[i]= new JSpinner(new SpinnerNumberModel(0, 0-(imp).getNFrames(), (imp).getNFrames(), 1));
					frameSpinner[i].setToolTipText("Adjust Channel "+ (i+1) +" display along T axis by # time increments");			
					c.gridy = y++;
					fspp.add(frameSpinner[i], c);
					c.gridy = y++;

					deltaTPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(deltaTPanel, c);
					deltaTPanel.setLayout(new BorderLayout());
					deltaTPanel.add("Center", deltaTLabel);
					fspp.add(deltaTPanel);

					deltaTSpinner[i]= new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
					deltaTSpinner[i].setToolTipText("Adjust Channel "+ (i+1) +" 's T-axis frame rate relative to other Channel(s)");			
					c.gridy = y++;
					fspp.add(deltaTSpinner[i], c);
					c.gridy = y++;

					scaleXPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(scaleXPanel, c);
					scaleXPanel.setLayout(new BorderLayout());
					scaleXPanel.add("Center", scaleXLabel);
					fspp.add(scaleXPanel);

					scaleXSpinner[i]= new JSpinner(new SpinnerNumberModel(0, -100, 500, 1));
					scaleXSpinner[i].setToolTipText("Adjust Channel "+ (i+1) +" 's X-axis scaling/magnification by # percent");			
					c.gridy = y++;
					fspp.add(scaleXSpinner[i], c);
					c.gridy = y++;


					scaleYPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(scaleYPanel, c);
					scaleYPanel.setLayout(new BorderLayout());
					scaleYPanel.add("Center", scaleYLabel);
					fspp.add(scaleYPanel);

					scaleYSpinner[i]= new JSpinner(new SpinnerNumberModel(0, -100, 500, 1));
					scaleYSpinner[i].setToolTipText("Adjust Channel "+ (i+1) +" 's Y-axis scaling/magnification by # percent");			
					c.gridy = y++;
					fspp.add(scaleYSpinner[i], c);
					c.gridy = y++;


					scaleZPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(scaleZPanel, c);
					scaleZPanel.setLayout(new BorderLayout());
					scaleZPanel.add("Center", scaleZLabel);
					fspp.add(scaleZPanel);

					scaleZSpinner[i]= new JSpinner(new SpinnerNumberModel(0, -100, 500, 0.1));
					scaleZSpinner[i].setToolTipText("Adjust Channel "+ (i+1) +" 's Z-axis scaling/magnification by # percent");			
					c.gridy = y++;
					fspp.add(scaleZSpinner[i], c);
					c.gridy = y++;


					rotateAnglePanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(rotateAnglePanel, c);
					rotateAnglePanel.setLayout(new BorderLayout());
					rotateAnglePanel.add("Center", rotateAngleLabel);
					fspp.add(rotateAnglePanel);

					rotateAngleSpinner[i]= new JSpinner(new SpinnerNumberModel(0, -360, 360, 1));
					rotateAngleSpinner[i].setToolTipText("Rotate Channel "+ (i+1) +" display by # degrees in XY plane");			
					c.gridy = y++;
					fspp.add(rotateAngleSpinner[i], c);
					c.gridy = y++;


					translateXPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(translateXPanel, c);
					translateXPanel.setLayout(new BorderLayout());
					translateXPanel.add("Center", translateXLabel);
					fspp.add(translateXPanel);

					translateXSpinner[i]= new JSpinner(new SpinnerNumberModel(0, -1000, 1000, 1));
					translateXSpinner[i].setToolTipText("Move Channel "+ (i+1) +" display # pixels along X axis");			
					c.gridy = y++;
					fspp.add(translateXSpinner[i], c);
					c.gridy = y++;


					translateYPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(translateYPanel, c);
					translateYPanel.setLayout(new BorderLayout());
					translateYPanel.add("Center", translateYLabel);
					fspp.add(translateYPanel);

					translateYSpinner[i]= new JSpinner(new SpinnerNumberModel(0, -1000, 1000, 1));
					translateYSpinner[i].setToolTipText("Move Channel "+ (i+1) +" display # pixels along Y axis");			
					c.gridy = y++;
					fspp.add(translateYSpinner[i], c);
					c.gridy = y++;


					dropFramesPanel = new Panel();
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(dropFramesPanel, c);
					dropFramesPanel.setLayout(new BorderLayout());
					dropFramesPanel.add("Center", dropFramesLabel);
					fspp.add(dropFramesPanel);

					ScrollPane dfsp = new ScrollPane();
					c.gridy = y++;
					c.gridy = y++;
					c.insets = new Insets(0, 10, 0, 10);
					gridbag.setConstraints(dfsp, c);
					String dropFramesString = "";
					dropFramesField[i] = new JTextField(dropFramesString);
					dropFramesField[i].setToolTipText("Ignore Channel "+ (i+1) +"specific timepoints...");			
					dropFramesField[i].setFocusable(true);
					dropFramesField[i].setEditable(true);

					//				movieNamePanel.add("Center", channelNameField[i]);
					dfsp.setPreferredSize(new Dimension(25,50));
					dfsp.add(dropFramesField[i]);
					fspp.add(dfsp);
					c.gridy = y++;

					
					saveButton[i] = new Button(saveLabel + (i+1));
					fspp.add(saveButton[i], c);
					c.gridy = y++;

					loadButton[i] = new Button(loadLabel + (i+1));
					fspp.add(loadButton[i], c);
					c.gridy = y++;



					if (imp.getNChannels() >1) visibility[i].addItemListener(this);
					flipVCB[i].addItemListener(this);
					flipHCB[i].addItemListener(this);
					flipZCB[i].addItemListener(this);

					sliceSpinner[i].addChangeListener(this);
					frameSpinner[i].addChangeListener(this);
					deltaZSpinner[i].addChangeListener(this);
					deltaTSpinner[i].addChangeListener(this);
					scaleXSpinner[i].addChangeListener(this);
					scaleYSpinner[i].addChangeListener(this);
					scaleZSpinner[i].addChangeListener(this);
					rotateAngleSpinner[i].addChangeListener(this);
					translateXSpinner[i].addChangeListener(this);
					translateYSpinner[i].addChangeListener(this);
					saveButton[i].addActionListener(this);
					loadButton[i].addActionListener(this);

				}
			}
		}
		addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts
		for (Component comp:fspp.getComponents())
			comp.setMinimumSize(null);
		this.setResizable(true);
		pack();
		firstBuild = true;
		setOpeningNewMovie(true);
		update();
		setOpeningNewMovie(false);
		if (location==null) {
			GUI.center(this);
			location = getLocation();
		} else
			setLocation(location);
		if (imp!=null) 
			imp.setMultiChannelController(this);
//		this.setVisible(true);

	}

	public void update() {

		CompositeImage ci = getCompositeImage();
		ImageStack stack =null;
		if (imp!=null)  stack = imp.getStack();		


		if (ci==null || ( !(stack instanceof MultiQTVirtualStack) 
				&& !(stack instanceof  QTVirtualStack) 
				&& imp.getRemoteMQTVSHandler()==null) ) {
			instance = null;
			location = getLocation();
			close();
			return;
			//} else if (ci == lastImage && !firstBuild) {
			//	return;
		}
		int n = visibility.length;
		int nChannels = ci.getNChannels();

		MultiChannelController mcc= this;
		if (IJ.debugMode) IJ.log(ci +"..."+lastImage);


		boolean[] active = ci.getActiveChannels();

		for (int i=0; i<mcc.visibility.length; i++) {
			while (mcc.visibility[i] == null) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (imp.getNChannels() >1) mcc.visibility[i].setState(active[i]);
		}
		int index = 0;
		if (imp.isComposite()) {
			switch (ci.getMode()) {
			case CompositeImage.COMPOSITE: index=0; break;
			case CompositeImage.COLOR: index=1; break;
			case CompositeImage.GRAYSCALE: index=2; break;
			}
		}
		IJ.wait(100);

		if (ci.getNChannels() >1 && imp.isComposite() )  
			choice.select(index);

		for (int j = 0; j < nChannels; j++) {
			while (mcc.getTranslateYSpinner(j) == null) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (imp.getStack() instanceof MultiQTVirtualStack) {
				if ( imp.isComposite()) 
					mcc.setChannelLUTChoice(j, ((MultiQTVirtualStack) stack).getChannelLUTIndex(j));

				mcc.setFlipVCB(j, ((MultiQTVirtualStack) stack).getFlipSingleMovieStackVertical(j));
				mcc.setFlipHCB(j, ((MultiQTVirtualStack) stack).getFlipSingleMovieStackHorizontal(j));
				mcc.setFlipZCB(j, ((MultiQTVirtualStack) stack).getFlipSingleMovieStackOrder(j));
				mcc.setSliceSpinner(j, ((MultiQTVirtualStack) stack).getShiftSingleMovieZPosition(j));
				mcc.setFrameSpinner(j, ((MultiQTVirtualStack) stack).getShiftSingleMovieTPosition(j));
				mcc.setDeltaZSpinner(j, ((MultiQTVirtualStack) stack).getRelativeZFrequency(j));
				mcc.setDeltaTSpinner(j, ((MultiQTVirtualStack) stack).getRelativeFrameRate(j));
				mcc.setScaleXSpinner(j, ((MultiQTVirtualStack) stack).getSingleMovieScaleX(j));
				mcc.setScaleYSpinner(j, ((MultiQTVirtualStack) stack).getSingleMovieScaleY(j));
				mcc.setRotateAngleSpinner(j, ((MultiQTVirtualStack) stack).getSingleMovieRotateAngle(j));
				mcc.setTranslateXSpinner(j, ((MultiQTVirtualStack) stack).getSingleMovieTranslateX(j));
				mcc.setTranslateYSpinner(j, ((MultiQTVirtualStack) stack).getSingleMovieTranslateY(j));

			} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
				if ( imp.isComposite()) 
					mcc.setChannelLUTChoice(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getChannelLUTIndex(j));

				mcc.setFlipVCB(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getFlipSingleMovieStackVertical(j));
				mcc.setFlipHCB(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getFlipSingleMovieStackHorizontal(j));
				mcc.setFlipZCB(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getFlipSingleMovieStackOrder(j));
				mcc.setSliceSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getShiftSingleMovieZPosition(j));
				mcc.setFrameSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getShiftSingleMovieTPosition(j));
				mcc.setDeltaZSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getRelativeZFrequency(j));
				mcc.setDeltaTSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getRelativeFrameRate(j));
				mcc.setScaleXSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getSingleMovieScaleX(j));
				mcc.setScaleYSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getSingleMovieScaleY(j));
				mcc.setRotateAngleSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getSingleMovieRotateAngle(j));
				mcc.setTranslateXSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getSingleMovieTranslateX(j));
				mcc.setTranslateYSpinner(j, ((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).getSingleMovieTranslateY(j));
			} 
		}
		if (imp != lastImage) 
			imp = lastImage;
		//WindowManager.getCurrentWindow().toFront();

	}

	void addPopupItem(String s) {
		MenuItem mi=new MenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	CompositeImage getCompositeImage() {

		if (imp==null || !imp.isComposite() ||imp.getType() == ImagePlus.COLOR_RGB) {
			return null;
		}else if (!imp.isComposite()){
			this.setBackground(imp.getWindow().getBackground());
			CompositeImage singleChannelci= new CompositeImage(imp, CompositeImage.GRAYSCALE);
			return singleChannelci;
		}
		else
			this.setBackground(imp.getWindow().getBackground());
		return (CompositeImage)imp;
	}

	public void itemStateChanged(ItemEvent e) {
		CompositeImage ci = getCompositeImage();
		ImageStack stack = imp.getStack();		

		if (imp==null) return;
		if (!imp.isComposite()) {
			int channels = imp.getNChannels();
			if (channels==1 && imp.getStackSize()<=4)
				channels = imp.getStackSize();
			if (imp.getBitDepth()==24 || (channels>1&&channels<CompositeImage.MAX_CHANNELS)) {
				//				GenericDialog gd = new GenericDialog(imp.getTitle(), this);
				//				gd.addMessage("Convert to multi-channel composite image?");
				//				gd.showDialog();
				//				if (gd.wasCanceled())
				//					/*return*/;
				//				else
				//					IJ.doCommand("Make Composite");                 
			} else {
				IJ.error("Channels", "A composite image is required (e.g., "+moreLabel+" Open HeLa Cells),\nor create one using "+moreLabel+" Make Composite.");
				return;
			}
		}
		//		if (!ci.isComposite()) return;

		Object source = e.getSource();
		if (source==choice) {
			int index = ((Choice)source).getSelectedIndex();
			switch (index) {
			case 0: ci.setMode(CompositeImage.COMPOSITE); break;
			case 1: ci.setMode(CompositeImage.COLOR); break;
			case 2: ci.setMode(CompositeImage.GRAYSCALE); break;
			}
			ci.updateAllChannelsAndDraw();
		} else if (source instanceof Choice) {
			for (int i=0; i<channelLUTChoice.length; i++) {
				Choice ch = (Choice)source;
				if (ch == channelLUTChoice[i]) {


					/***************************************************************************************/
					IJ.run("Stop Animation", "");
					ci.setPosition( i+1, ci.getSlice(), ci.getFrame() );
					IJ.doCommand(channelLUTItems[((Choice)source).getSelectedIndex()] );
					if (stack instanceof MultiQTVirtualStack) {
						((MultiQTVirtualStack) stack).setChannelLUTName(i, channelLUTItems[((Choice)source).getSelectedIndex()]);
						((MultiQTVirtualStack) stack).setChannelLUTIndex(i, ((Choice)source).getSelectedIndex() );
					}else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack){
						((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setChannelLUTName(i, channelLUTItems[((Choice)source).getSelectedIndex()]);
						((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setChannelLUTIndex(i, ((Choice)source).getSelectedIndex() );
					}else {
						IJ.run(channelLUTItems[((Choice)source).getSelectedIndex()]);
					}
					//					IJ.doCommand("Start Animation [\\]");
				}
			}
		} else if (source instanceof Checkbox) {
			//if (IJ.debugMode) IJ.log("got to visibility");
			for (int i=0; i<imp.getNChannels(); i++) {
				Checkbox cb = (Checkbox)source;
				if (cb==visibility[i]) {
					if (ci!=null && ci.getMode()==CompositeImage.COMPOSITE) {
						boolean[] active = ci.getActiveChannels();
						active[i] = cb.getState();
					} else {
						imp.setPosition(i+1, imp.getSlice(), imp.getFrame());
					}
					imp.updateAndDraw();
					return;
				} else if (cb==flipVCB[i]) {
					//if (IJ.debugMode) IJ.log("got to MAF.fVCB");
					if (flipVCB[i].getState()  || !flipVCB[i].getState()) {
						if (stack instanceof MultiQTVirtualStack) {
							((MultiQTVirtualStack) stack).flipSingleMovieVertical(i);
							if (ci!=null && ci.getProcessor(i+1)!=null) {
								ci.getProcessor(i+1).flipVertical(); 
								ci.getProcessor(i+1).flipVertical();
							}
							if (ci!=null) ci.updateAndDraw();
						}
						else if (imp.getRemoteMQTVSHandler()!=null)
							((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).flipSingleMovieVertical(i);
						/*How can this be made to live. update?*/
						imp.setProcessor(imp.getProcessor());
						imp.updateAndDraw();
						if (doingFirstSetup)  
							imp.getRoiManager().setRmNeedsUpdate(true);

					}
				} else if (cb==flipHCB[i]  ) {
					if (flipHCB[i].getState() || !flipHCB[i].getState()) {
						if (stack instanceof MultiQTVirtualStack)
							((MultiQTVirtualStack) stack).flipSingleMovieHorizontal(i);
						else if (imp.getRemoteMQTVSHandler()!=null)
							((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).flipSingleMovieHorizontal(i);
						if (ci!=null && ci.getProcessor(i+1)!=null) {
							ci.getProcessor(i+1).flipHorizontal(); 
							ci.getProcessor(i+1).flipHorizontal();
						}
						if (ci!=null) ci.updateAndDraw();
						/*How can this be made to live. update?*/
						imp.updateAndDraw();
						if (doingFirstSetup)  
							imp.getRoiManager().setRmNeedsUpdate(true);

					}

				} else if (cb==flipZCB[i]  ) {
					if (flipZCB[i].getState() || !flipZCB[i].getState()) {
						if (stack instanceof MultiQTVirtualStack)
							((MultiQTVirtualStack) stack).flipSingleMovieZaxis(i);
						else if (imp.getRemoteMQTVSHandler()!=null)
							((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).flipSingleMovieZaxis(i);
						/*How can this be made to live. update?*/
						imp.updateAndDraw();

						if (doingFirstSetup)  
							imp.getRoiManager().setRmNeedsUpdate(true);

					}
				}
				if (!openingNewMovie) {
					if ( imp != null && !((StackWindow)imp.getWindow()).getAnimate() ) {
						//if (IJ.debugMode) IJ.log( "Animation off. ") ;
						if(imp.getNFrames()>1) {
							imp.setPosition( imp.getChannel(), imp.getSlice(), imp.getFrame()+1 );
							imp.updateAndDraw();
							imp.setPosition( imp.getChannel(), imp.getSlice(), imp.getFrame()-1 );
						} else {
							imp.setPosition( imp.getChannel(), imp.getSlice()+1, imp.getFrame() );
							imp.updateAndDraw();
							imp.setPosition( imp.getChannel(), imp.getSlice()-1, imp.getFrame() );
						}
						if (imp.isComposite()) {
							int mode = ((CompositeImage)imp).getMode();
							((CompositeImage)imp).setMode(1);
							((CompositeImage)imp).setMode(2);
							((CompositeImage)imp).setMode(3);
							((CompositeImage)imp).setMode(mode);
						}
					}
				}

			}
		}
	}

	public boolean isDoingFirstSetup() {
		return doingFirstSetup;
	}

	public void setDoingFirstSetup(boolean doingFirstSetup) {
		this.doingFirstSetup = doingFirstSetup;
	}

	public void actionPerformed(ActionEvent e) {

		//		ImagePlus imp = WindowManager.getCurrentImage();
		CompositeImage ci = null;
		if(imp.isComposite())
			ci = getCompositeImage();

		ImageStack stack = (ci!=null?ci:imp).getImageStack();	

		String command = e.getActionCommand();

		if (!(stack instanceof MultiQTVirtualStack || stack instanceof RemoteMQTVirtualStack) && !command.equals("Save Scene")  && !command.equals("Share Scene")  && !command.equals("Show Scene Details")){
			IJ.showMessage("Error: Multi-Channel Controller", "This type of Image Stack does not work with a Multi-Channel Controller.");
			return;			
		}

		if (command==null) return;

		if (command.contains(saveLabel)){
			for ( int j=0 ; j < CompositeImage.MAX_CHANNELS; j++) {
				if ( command.contains(""+(j+1) ) ) {
					int saveChannelNumber = j +1;
					String saveName = (stack instanceof MultiQTVirtualStack)?((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieName():((QTVirtualStack) stack).getMovieName();
					if (IJ.debugMode) 
						IJ.log(saveName);

					IJ.run("Stop Animation", "");
					(ci!=null?ci:imp).setPosition(saveChannelNumber, (ci!=null?ci:imp).getSlice(), (ci!=null?ci:imp).getFrame() );
					/***********Next lines output a file with text **************/
					File saveFileAdj = null;
					try {				
						Date currentDate = new Date();
						long msec = currentDate.getTime();			    
						long sec = msec/1000;

						saveFileAdj = new File((stack instanceof MultiQTVirtualStack)? 
								((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieName().substring(0, 15) + "_" + (j+1) + "_" + sec
								+ ".adj":
									((QTVirtualStack) stack).getMovieName().substring(0, 15) + "_" + (j+1) + "_" + sec
									+ ".adj");
						while ( saveFileAdj == null ||  saveFileAdj.exists() ) {
							JFileChooser fc = new JFileChooser( ((stack instanceof MultiQTVirtualStack)?
									((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieFile().getPath():
										((QTVirtualStack) stack).getMovieFile().getPath()), FileSystemView.getFileSystemView());
							fc.setDialogTitle( "Save a new *.adj Channel Adjustment file" );
							fc.setSelectedFile(saveFileAdj);
							int dialogResult = fc.showSaveDialog(null);
							if (dialogResult == JFileChooser.APPROVE_OPTION) {

								saveFileAdj = fc.getSelectedFile();

							} else if (dialogResult == JFileChooser.CANCEL_OPTION) {
								saveFileAdj = null;
								break;
							}

						}


						PrintWriter out = 
								new PrintWriter(
										new BufferedWriter(
												new FileWriter(saveFileAdj) ), true);
						out.println("Saved Adjustments for movie " + ( (stack instanceof MultiQTVirtualStack)?
								((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieFile().getPath():
									((QTVirtualStack) stack).getMovieFile().getPath() ) );
						out.println("");
						if ( imp.isComposite() ) 
							out.println("LUT = " + channelLUTItems[((Choice)channelLUTChoice[j]).getSelectedIndex()] );
						if (ci!=null) out.println("DisplayRangeMin = "+ (ci).getProcessor(j+1).getMin());
						else out.println("DisplayRangeMin = "+ imp.getProcessor().getMin());
						if (ci!=null) out.println("DisplayRangeMax = "+ (ci).getProcessor(j+1).getMax());
						else out.println("DisplayRangeMax = "+ imp.getProcessor().getMax());
						out.println("FlipVertical = " + flipVCB[j].getState());
						out.println("FlipHorizontal = " + flipHCB[j].getState());
						out.println("FlipZaxis = " + flipZCB[j].getState());
						out.println("ShiftZ = " + Integer.parseInt(sliceSpinner[j].getValue().toString()));
						out.println("ShiftT = " + Integer.parseInt(frameSpinner[j].getValue().toString()));
						out.println("deltaZ = " + Integer.parseInt(deltaZSpinner[j].getValue().toString()));
						out.println("deltaT = " + Integer.parseInt(deltaTSpinner[j].getValue().toString()));
						out.println("ScaleX = " + Double.parseDouble(scaleXSpinner[j].getValue().toString() ));
						out.println("ScaleY = " + Double.parseDouble(scaleYSpinner[j].getValue().toString() ));
						out.println("RotationAngle = " + Double.parseDouble(rotateAngleSpinner[j].getValue().toString() ));
						out.println("ShiftX = " + Double.parseDouble(translateXSpinner[j].getValue().toString() ));
						out.println("ShiftY = " + Double.parseDouble(translateYSpinner[j].getValue().toString() ));
						out.println("DropFrames = ," + dropFramesField[j].getText() );
						out.println("End of parameter list");

					}
					catch (IOException ev)
					{
						try {
							Date currentDate = new Date();
							long msec = currentDate.getTime();			    
							long sec = msec/1000;


							if (IJ.debugMode) IJ.log("I/O Error: Cannot save to specified directory/file.");
							MessageDialog ioErrorDialog = new MessageDialog(this, "Save Settings Error!", "Your movie settings could not be automatically saved in the same location as the movie file\n\nPlease choose another location");
							//ioErrorDialog.show();

							Boolean newSave = true;
							if (saveFileAdj == null) {
								saveFileAdj = new File( (stack instanceof MultiQTVirtualStack)?
										((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieName().substring(0, 15) + "_" + (j+1) + "_" + sec
										+ ".adj": 
											((QTVirtualStack) stack).getMovieName().substring(0, 15) + "_" + (j+1) + "_" + sec
											+ ".adj");
								while ( saveFileAdj == null ||  saveFileAdj.exists() ) {
									JFileChooser fc = new JFileChooser( ((stack instanceof MultiQTVirtualStack)?
											((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieFile().getPath():
												((QTVirtualStack) stack).getMovieFile().getPath()), FileSystemView.getFileSystemView());
									fc.setDialogTitle( "Save a new *.adj Channel Adjustment file" );
									fc.setSelectedFile(saveFileAdj);
									int dialogResult = fc.showSaveDialog(null);
									if (dialogResult == JFileChooser.APPROVE_OPTION) {

										saveFileAdj = fc.getSelectedFile();
										newSave = false;

										if (saveFileAdj.exists())
											newSave = true;

									} else if (dialogResult == JFileChooser.CANCEL_OPTION) {
										saveFileAdj = null;
										break;
									}
								}	
							}
							PrintWriter out = 
									new PrintWriter(
											new BufferedWriter(
													new FileWriter(saveFileAdj) ), true);
							out.println("Saved Adjustments for movie " + ((stack instanceof MultiQTVirtualStack)?
									((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieFile().getPath():
										((QTVirtualStack) stack).getMovieFile().getPath()) );
							out.println("");
							if ( imp.isComposite() ) 
								out.println("LUT = " + channelLUTItems[((Choice)channelLUTChoice[j]).getSelectedIndex()] );
							if (ci!=null) out.println("DisplayRangeMin = "+ (ci).getProcessor(j+1).getMin());
							else out.println("DisplayRangeMin = "+ imp.getProcessor().getMin());
							if (ci!=null) out.println("DisplayRangeMax = "+ (ci).getProcessor(j+1).getMax());
							else out.println("DisplayRangeMax = "+ imp.getProcessor().getMax());
							out.println("FlipVertical = " + flipVCB[j].getState());
							out.println("FlipHorizontal = " + flipHCB[j].getState());
							out.println("FlipZaxis = " + flipZCB[j].getState());
							out.println("ShiftZ = " + Integer.parseInt(sliceSpinner[j].getValue().toString()));
							out.println("ShiftT = " + Integer.parseInt(frameSpinner[j].getValue().toString()));
							out.println("deltaZ = " + Integer.parseInt(deltaZSpinner[j].getValue().toString()));
							out.println("deltaT = " + Integer.parseInt(deltaTSpinner[j].getValue().toString()));
							out.println("ScaleX = " + Double.parseDouble(scaleXSpinner[j].getValue().toString() ));
							out.println("ScaleY = " + Double.parseDouble(scaleYSpinner[j].getValue().toString() ));
							out.println("RotationAngle = " + Double.parseDouble(rotateAngleSpinner[j].getValue().toString() ));
							out.println("ShiftX = " + Double.parseDouble(translateXSpinner[j].getValue().toString() ));
							out.println("ShiftY = " + Double.parseDouble(translateYSpinner[j].getValue().toString() ));
							out.println("DropFrames = ," + dropFramesField[j].getText() );
							out.println("End of parameter list");
						}
						catch (IOException ex)
						{
						}



					}
					/***********end of text output***********/
				}
			}

		} else if (command.contains(loadLabel)) {
			for ( int j=0 ; j < CompositeImage.MAX_CHANNELS; j++) {

				if ( command.contains(""+(j+1) ) ) {
					if (IJ.debugMode) IJ.log("Loading Adjustments for Channel " + (j+1) + " " + ((stack instanceof MultiQTVirtualStack)?
							((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieFile().toString():
								((QTVirtualStack) stack).getMovieFile().toString()) );
					Java2.setSystemLookAndFeel();
					JFileChooser fc = new JFileChooser( ((stack instanceof MultiQTVirtualStack)?
							((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieFile().toString():
								((QTVirtualStack) stack).getMovieFile().toString()) );
					File loadFile = new File( ((stack instanceof MultiQTVirtualStack)?
							((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieFile().toString():
								((QTVirtualStack) stack).getMovieFile().toString()) + ".adj") ;
					fc.setDialogTitle( "Select a *.mov.adj Channel Adjustment file" );
					fc.setSelectedFile(loadFile);
					int dialogResult = fc.showOpenDialog(null);
					if (dialogResult == JFileChooser.APPROVE_OPTION) {
						try {
							File file = fc.getSelectedFile();

							if ( file.getPath().contains(".adj") || file.getPath().contains(".ADJ")) {

								if (IJ.debugMode) IJ.log(file.getPath() );
								BufferedReader in = new BufferedReader(
										new FileReader(file));
								String line = in.readLine();
								if (IJ.debugMode) IJ.log(line);

								double displayMin = 0;
								double displayMax = 255;

								while (!line.contains("End of parameter list")) {
									line = in.readLine();
									if (IJ.debugMode) IJ.log(line);
									String[] lineSegments = line.split(" ");

									if (lineSegments[0].contains("LUT") ) {
										IJ.run("Stop Animation", "");
										(ci!=null?ci:imp).setPosition( j+1, (ci!=null?ci:imp).getSlice(), (ci!=null?ci:imp).getFrame() );
										IJ.doCommand( lineSegments[2] );
										for ( int k=0; k<channelLUTItems.length; k++) {
											if ( lineSegments[2].contains( channelLUTItems[k] ) )
												((Choice)channelLUTChoice[j]).select(k);	

										}

									}	

									if (lineSegments[0].contains("DisplayRangeMin") ) {
										IJ.run("Stop Animation", "");
										(ci!=null?ci:imp).setPosition( j+1, (ci!=null?ci:imp).getSlice(), (ci!=null?ci:imp).getFrame() );
										displayMin = Double.parseDouble(lineSegments [2] );
									}	
									if (lineSegments[0].contains("DisplayRangeMax") ) {
										IJ.run("Stop Animation", "");
										(ci!=null?ci:imp).setPosition( j+1, (ci!=null?ci:imp).getSlice(), (ci!=null?ci:imp).getFrame() );
										displayMax = Double.parseDouble(lineSegments [2] );
									}	
									((ci!=null?ci:imp)).setDisplayRange(displayMin, displayMax);

									if (lineSegments[0].contains("FlipVertical") ) {
										if (lineSegments[2].contains("true") ) {
											((MultiQTVirtualStack) stack).flipSingleMovieVertical(j);
											flipVCB[j].setState(true);
										} else {
											flipVCB[j].setState(false);
										}
									}	
									if (lineSegments[0].contains("FlipHorizontal") ) {
										if (lineSegments[2].contains("true") ) {
											((MultiQTVirtualStack) stack).flipSingleMovieHorizontal(j);
											flipHCB[j].setState(true);
										} else {
											flipHCB[j].setState(false);
										}
									}	
									if (lineSegments[0].contains("FlipZaxis") ) {
										if (lineSegments[2].contains("true") ) {
											((MultiQTVirtualStack) stack).flipSingleMovieZaxis(j);
											flipZCB[j].setState(true);
										} else {
											flipZCB[j].setState(false);
										}
									}	


									if (lineSegments[0].contains("ShiftZ") ) {
										int shiftZ = Integer.parseInt(lineSegments[2]);
										int zShiftNet = shiftZ - previousShiftZ[j];
										boolean forward = false;
										if (true) {									
											int zShiftNetAbs = zShiftNet;
											if (zShiftNet < 0) {
												zShiftNetAbs = -zShiftNet;
												forward = true;
											} else {
												forward = false;
											}																			
											previousShiftZ[j] = previousShiftZ[j] + zShiftNet;

											//											for(int k = 0; k < zShiftNetAbs; k++){
											if (stack instanceof MultiQTVirtualStack )   						
												((MultiQTVirtualStack) stack).adjustSingleMovieZ(j, zShiftNet);	 
											//											}
											sliceSpinner[j].setValue(shiftZ);	 
											if (IJ.debugMode) IJ.log("YES shift in Z loaded");
										} else {
											if (IJ.debugMode) IJ.log("no shift in Z loaded");
										}
									}	
									if (lineSegments[0].contains("ShiftT") ) {
										int shiftT = Integer.parseInt(lineSegments[2]);
										int tShiftNet = shiftT - previousShiftT[j];
										boolean forward = false;
										if (true) {									
											int tShiftNetAbs = tShiftNet;
											if (tShiftNet < 0) {
												tShiftNetAbs = -tShiftNet;
												forward = false;
											} else {
												forward = true;
											}																			
											previousShiftT[j] = previousShiftT[j] + tShiftNet;
											//											for(int k = 0; k < tShiftNetAbs; k++){
											if (stack instanceof MultiQTVirtualStack )   						
												((MultiQTVirtualStack) stack).adjustSingleMovieT(j, tShiftNet);	 
											//											}
											frameSpinner[j].setValue(shiftT);	 

											if (IJ.debugMode) IJ.log("YES shift in T loaded");
										} else {
											if (IJ.debugMode) IJ.log("no shift in T loaded");
										}
									}	


									if (lineSegments[0].contains("deltaZ") ) {
										int deltaZ = Integer.parseInt(lineSegments[2]);
										if (true ) {
											if (IJ.debugMode) IJ.log("YES deltaZ in Z loaded");
											if (stack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) stack).setRelativeZFrequency(j, deltaZ);
											deltaZSpinner[j].setValue(deltaZ);	 
										} else {
											if (IJ.debugMode) IJ.log("no deltaZ in Z loaded");
										}
									}	
									if (lineSegments[0].contains("deltaT") ) {
										int deltaT = Integer.parseInt(lineSegments[2]);
										if (true ) {
											if (IJ.debugMode) IJ.log("YES deltaT in T loaded");
											if (stack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) stack).setRelativeFrameRate(j, deltaT);
											deltaTSpinner[j].setValue(deltaT);	 
										} else {
											if (IJ.debugMode) IJ.log("no deltaT in T loaded");
										}
									}	
									if (lineSegments[0].contains("ScaleX") ) {
										int ScaleX = Integer.parseInt(lineSegments[2]);
										if (true ) {
											if (IJ.debugMode) IJ.log("YES ScaleX loaded");
											if (stack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) stack).setSingleMovieScale(j, 
														ScaleX, 
														Double.parseDouble(scaleYSpinner[j].getValue().toString()));	 

											scaleXSpinner[j].setValue(ScaleX);	 

										} else {
											if (IJ.debugMode) IJ.log("no ScaleX loaded");
										}
									}	
									if (lineSegments[0].contains("ScaleY") ) {
										int ScaleY = Integer.parseInt(lineSegments[2]);
										if (true ) {
											if (IJ.debugMode) IJ.log("YES ScaleY loaded");
											if (stack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) stack).setSingleMovieScale(j, 
														Double.parseDouble(scaleXSpinner[j].getValue().toString()), 
														ScaleY);	 

											scaleYSpinner[j].setValue(ScaleY);	 

										} else {
											if (IJ.debugMode) IJ.log("no ScaleY loaded");
										}
									}	
									if (lineSegments[0].contains("RotationAngle") ) {
										int rotAngle = Integer.parseInt(lineSegments[2]);
										if (true ) {
											if (IJ.debugMode) IJ.log("YES RotationAngle loaded");
											if (stack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) stack).setSingleMovieRotationAngle(j, rotAngle);
											rotateAngleSpinner[j].setValue(rotAngle);	 
										} else {
										}
									}	
									if (lineSegments[0].contains("ShiftX") ) {
										int shiftX = Integer.parseInt(lineSegments[2]);
										if (true ) {
											if (IJ.debugMode) IJ.log("YES ShiftX loaded");
											if (stack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) stack).setSingleMovieTranslate(j, 
														shiftX, 
														(Double.parseDouble(translateYSpinner[j].getValue().toString())) );

											translateXSpinner[j].setValue(shiftX);	 

										} else {
											if (IJ.debugMode) IJ.log("no shift in X loaded");
										}
									}	
									if (lineSegments[0].contains("ShiftY") ) {
										int shiftY = Integer.parseInt(lineSegments[2]);
										if (true ) {
											if (IJ.debugMode) IJ.log("YES ShiftY loaded");
											if (stack instanceof MultiQTVirtualStack ) 
												((MultiQTVirtualStack) stack).setSingleMovieTranslate(j, 
														(Double.parseDouble(translateXSpinner[j].getValue().toString())), 
														shiftY );

											translateYSpinner[j].setValue(shiftY);	 

										} else {
											if (IJ.debugMode) IJ.log("no shift in Y loaded");
										}
									}	

									if (lineSegments[0].contains("DropFrames") ) {
										String dropFramesString = lineSegments[2];
										if (true ) {
											if (IJ.debugMode) IJ.log("YES dropped frames  loaded");

											dropFramesField[j].setText(dropFramesString) ;

										} else {
											if (IJ.debugMode) IJ.log("no dropped frames loaded");
										}
									}	
								}	
								in.close();
							}
						}	
						catch (IOException ev)
						{
							if (IJ.debugMode) IJ.log("I/O Error: Cannot read from specified directory/file.");
							//System.exit(0);
						}

					}	

				}
			}
		} else if (command.equals(moreLabel)) {
			Point bloc = moreButton.getLocation();
			pm.show(this, bloc.x, bloc.y);
		} else if (command.equals("Convert to RGB")) {
			IJ.doCommand("Stack to RGB");

/********************************/

		} else if (command.equals("Save Scene") || command.equals("Show Scene Details")) {
			Date currentDate = new Date();
			long msec = currentDate.getTime();			    
			long sec = msec/1000;

			if (IJ.debugMode) IJ.log("Saving Scene portraying " + ci.getNChannels() + " movies /n");

			String name = (imp.getTitle().length()>28?imp.getTitle().substring(0, 25):imp.getTitle().replace(".tif", ""))+sec;
			name = name.replace(" ","");
			String dir = IJ.getDirectory("home");
			String path = dir+name.replaceAll("[\\?\\\\/:;]", "_");
			if(!(imp.getImageStack() instanceof MultiQTVirtualStack)
					&& !(imp.getImageStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack)
					&& deNovoMovieFile==null) {
				imp.setMultiChannelController(this);
//				SaveDialog sd = new SaveDialog("Save Movie for CytoSHOW Scene as...", imp.getTitle().length()>28?imp.getTitle().substring(0, 25):imp.getTitle().replace(".tif", ""), ".avi");
//				String name = sd.getFileName().replace(" ","");
				name = name +"_1.avi";
				name = name.replace(" ","");
				if (imp.getType()==ImagePlus.COLOR_RGB)
					name = "RGB_"+name;
				if (name==null) return;
				/*
			if (name.length()>32) {
				IJ.error("QuickTime Writer", "File name cannot be longer than 32 characters");
				return;
			}
				 */
				dir = IJ.getDirectory("home");
				path = dir+name.replaceAll("[\\?\\\\/:;]", "_");
				Roi roi = imp.getRoi();
				if (roi != null)
					imp.killRoi();
				int mode = CompositeImage.GRAYSCALE;
				double[] mins = new double[imp.getNChannels()+1]; 
				double[] maxs = new double[imp.getNChannels()+1]; 

				if (imp.getNChannels()>1) {
					int[] position = {imp.getChannel(), imp.getSlice(), imp.getFrame()};
					if (imp.isComposite()) {
						mode = ((CompositeImage)imp).getMode();
						((CompositeImage)imp).setMode(CompositeImage.GRAYSCALE);
					}
					imp.getCanvas().unzoom();
					for (int c=1; c<mins.length; c++) {
						imp.setPosition(c, position[1], position[2]);
						mins[c] = imp.getChannelProcessor().getMin();
						maxs[c] = imp.getChannelProcessor().getMin();
						imp.getChannelProcessor().setMinAndMax(0, 255);
					
						IJ.run(imp, "AVI... ", "compression=PNG frame=10"+" channels="+c+"-"+c+" slices=1-"+imp.getNSlices()
								+" frames=1-"+imp.getNFrames()+" save=["+path.replace("_1.avi", "_"+c+".avi")+"]");				
						deNovoMovieFile = new java.io.File(path);

						imp.getChannelProcessor().setMinAndMax(mins[c], maxs[c]);
					}
					imp.setPosition(position[0], position[1], position[2]);
					if (imp.isComposite()) 
						((CompositeImage)imp).setMode(mode);

				}  else {
					imp.getCanvas().unzoom();
					IJ.run(imp, "AVI... ", "compression=PNG frame=10"+" channels=1-"+imp.getNChannels()+" slices=1-"+imp.getNSlices()
							+" frames=1-"+imp.getNFrames()+" save=["+path+"]");				
					deNovoMovieFile = new java.io.File(path);
				}
				if (roi != null)
					imp.setRoi(roi);
			}

			/***********Next lines output a file with text **************/
			if (saveFile == null || !command.equals("Show Scene Details")) {
				saveFile = new File( path+"_scene.scn") ;
				try {

					//				while ( saveFile == null || !( saveFile.getPath().toLowerCase().contains("scene.scn")) || !saveFile.exists() ) {
					//					JFileChooser fc = new JFileChooser();
					//					fc.setDialogTitle( "Save a new *_scene.scn MQTVS Scene file" );
					//					fc.setSelectedFile(saveFile);
					//					int dialogResult = fc.showSaveDialog(null);
					//					if (dialogResult == JFileChooser.APPROVE_OPTION) {
					//
					//						saveFile = fc.getSelectedFile();

					saveFile.createNewFile();

					//					} else if (dialogResult == JFileChooser.CANCEL_OPTION) {
					//						saveFile = null;
					//						break;
					//					}
					//
					//				}

					PrintWriter out = 
							new PrintWriter(
									new BufferedWriter(
											new FileWriter(saveFile) ), true);
					out.println("Saved Scene for movies:"); 
					for ( int m=0 ; m < (/*deNovoMovieFile!=null?1:*/(ci!=null?ci:imp).getNChannels()); m++) {
						int saveChannelNumber = m;									
						if (stack instanceof MultiQTVirtualStack)
							out.println( (deNovoMovieFile!=null?(sharing?"/Volumes/GLOWORM_DATA/" + deNovoMovieFile.getName().replaceAll("_\\d+.avi", "_"+(m+1)+".avi"):deNovoMovieFile.getPath().replaceAll("_\\d+.avi", "_"+(m+1)+".avi")):((MultiQTVirtualStack) stack).getVirtualStack(m).getMovieFile().getPath()) 
									+ " = " + (deNovoMovieFile!=null?imp.getNSlices():((MultiQTVirtualStack) stack).getChannelNSlices(m) )
									+ " = " + (deNovoMovieFile!=null?
											(deNovoMovieFile.getName().length()>12?deNovoMovieFile.getName().substring(0, 12):deNovoMovieFile.getName()) + "_" + (m+1) + "_" + sec + ".adj":
												(((MultiQTVirtualStack) stack).getVirtualStack(m).getMovieName().length()>12?((MultiQTVirtualStack) stack).getVirtualStack(m).getMovieName().substring(0, 12):((MultiQTVirtualStack) stack).getVirtualStack(m).getMovieName()) + "_" + (m+1) + "_" + sec + ".adj" ));

						else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack)
							out.println( (deNovoMovieFile!=null?(sharing?"/Volumes/GLOWORM_DATA/" + deNovoMovieFile.getName().replaceAll("_\\d+.avi", "_"+(m+1)+".avi"):deNovoMovieFile.getPath().replaceAll("_\\d+.avi", "_"+(m+1)+".avi")):imp.getRemoteMQTVSHandler().getChannelPathNames()[m]) 
									+ " = " + (deNovoMovieFile!=null?imp.getNSlices():imp.getRemoteMQTVSHandler().getMovieSlicesStrings()[m] )
									+ " = " + (deNovoMovieFile!=null?
											(deNovoMovieFile.getName().length()>12?deNovoMovieFile.getName().substring(0, 12):deNovoMovieFile.getName())
											+ "_" + (m+1) + "_" + sec + ".adj":
												imp.getRemoteMQTVSHandler().getChannelPathNames()[m].replaceAll(".*/","").length()>12?
														imp.getRemoteMQTVSHandler().getChannelPathNames()[m].replaceAll(".*/","").substring(0, 12)
														:imp.getRemoteMQTVSHandler().getChannelPathNames()[m].replaceAll(".*/","") )
														+ "_" + (m+1) + "_" + sec + ".adj" );
						else 
							out.println( (deNovoMovieFile!=null?(sharing?"/Volumes/GLOWORM_DATA/" + deNovoMovieFile.getName().replaceAll("_\\d+.avi", "_"+(m+1)+".avi"):deNovoMovieFile.getPath().replaceAll("_\\d+.avi", "_"+(m+1)+".avi")):"error") 
									+ " = " + (deNovoMovieFile!=null?(!(deNovoMovieFile.getName().startsWith("SW_")|| deNovoMovieFile.getName().startsWith("RGB_"))?imp.getNSlices():1):"error" )
									+ " = " + (deNovoMovieFile!=null?
											(deNovoMovieFile.getName().length()>12?deNovoMovieFile.getName().substring(0, 12):deNovoMovieFile.getName())
											+ "_" + (m+1) + "_" + sec + ".adj":
												"error" + "_" + (m+1) + "_" + sec + ".adj" ));

					}
					out.println("");
					RoiManager rm =  imp.getRoiManager();
					if ( rm != null ) {
						if (rm.getCount()>0){
							out.println("ROIfile = " + /*saveFile.getParent() +File.separator +*/ "MQTVS_"+ sec +"_ROIs.zip");
							if (rm.getList().getSelectedValue() != null){
								out.println("Selection = " + rm.getList().getSelectedValue());
							}
						}
						ColorLegend cl = rm.getColorLegend();
						if ( cl != null) {
							out.println("ColorLegendFile = " + /*saveFile.getParent() +File.separator +*/ "MQTVS_"+ sec +"_ColorLegend.lgd");
						}
					}
					out.println("");
					out.println("Convert8bit = " + ((deNovoMovieFile!=null && !deNovoMovieFile.getName().startsWith("SW_") && !deNovoMovieFile.getName().startsWith("RGB_"))?true:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsEightBit():(imp.getRemoteMQTVSHandler()!=null?imp.getRemoteMQTVSHandler().isEightBit():false) ) ) );
					out.println("VirtualStack = " + (deNovoMovieFile!=null?true:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsVirtualStack():true) ));
					out.println("MultipleMovies = " + (deNovoMovieFile!=null?(imp.getNChannels()>1):(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsMultipleMovies():true) ));
					out.println("HyperStack = " + (deNovoMovieFile!=null?true:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsHyperStack():true) ));
					out.println("StretchToFit = " + (deNovoMovieFile!=null?true:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsStretchToFit():(imp.getRemoteMQTVSHandler()!=null?imp.getRemoteMQTVSHandler().isStretchToFitOverlay():false) ) ) );
					out.println("ViewInOverlay = " + (deNovoMovieFile!=null?true:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsViewInOverlay():(imp.getRemoteMQTVSHandler()!=null?imp.getRemoteMQTVSHandler().isViewOverlay():false) ) ) );
					out.println("HorizontalMontage = " + (deNovoMovieFile!=null?false:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsHorizontalMontage():(imp.getRemoteMQTVSHandler()!=null?imp.getRemoteMQTVSHandler().isHorizontal():false) ) ) );
					out.println("SideSideStereo = " + (deNovoMovieFile!=null?false:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsSideSideStereo():(imp.getRemoteMQTVSHandler()!=null?imp.getRemoteMQTVSHandler().isSideSideStereo():false) ) ) );
					out.println("RedCyanStereo = " + (deNovoMovieFile!=null?false:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsRedCyanStereo():(imp.getRemoteMQTVSHandler()!=null?imp.getRemoteMQTVSHandler().isRedCyanStereo():false) ) ) );
					out.println("GridLayOut = " + (deNovoMovieFile!=null?false:(stack instanceof MultiQTVirtualStack?((MultiQTVirtualStack) stack).getIsGrid():(imp.getRemoteMQTVSHandler()!=null?imp.getRemoteMQTVSHandler().isGrid():false) ) ) );

					if (ci!=null) out.println("DisplayMode = " + ci.getMode() + " = " + ci.getModeAsString()	 );	
					else out.println("DisplayMode = " + 1	 );
					out.println("Cposition = " + (deNovoMovieFile!=null?1:(ci!=null?ci:imp).getChannel())	 );			
					out.println("Zposition = " + (ci!=null?ci:imp).getSlice()	 );			
					out.println("Tposition = " + (ci!=null?ci:imp).getFrame()	 );			
					if (rm !=null) {
						out.println("ZsustainROIs = " + (rm ==null?1:rm.getZSustain()) );
						out.println("TsustainROIs = " + (rm ==null?1:rm.getTSustain()) );
					}
					out.println("End of parameter list");
					//if (IJ.debugMode) IJ.log(""+ imp.getNChannels());

					out.close();

					if (imp!=null) {
						for (int j = 0; j < imp.getNChannels(); j++) {
							try {
								//if (IJ.debugMode) IJ.log("debug a");
								File saveFile1 = new File(saveFile.getParent()
										+ File.separator
										+ (deNovoMovieFile!=null?
												(deNovoMovieFile.getName().length()>12?deNovoMovieFile.getName().substring(0, 12):deNovoMovieFile.getName()) + "_" + (j+1) + "_" + sec + ".adj"
												:( stack instanceof MultiQTVirtualStack
														?(((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieName().length()>12
																?((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieName().substring(0, 12)
																		:((MultiQTVirtualStack) stack).getVirtualStack(j).getMovieName()) 
																		:(imp.getRemoteMQTVSHandler().getChannelPathNames()[j].replaceAll(".*/","").length()>12
																				?imp.getRemoteMQTVSHandler().getChannelPathNames()[j].replaceAll(".*/","").substring(0, 12)
																						:imp.getRemoteMQTVSHandler().getChannelPathNames()[j].replaceAll(".*/","") ))
																						+ "_" + (j+1) + "_" + sec + ".adj" ));
								PrintWriter out1 = new PrintWriter(
										new BufferedWriter(
												new FileWriter(saveFile1)), true);
								if (deNovoMovieFile!=null)
									out1.println("Saved Adjustments for movie "
											+ deNovoMovieFile.getPath());
								else
									out1.println("Saved Adjustments for movie "
											+ (stack instanceof MultiQTVirtualStack
													?((MultiQTVirtualStack) stack)
															.getVirtualStack(j).getMovieFile()
															.getPath()
															:imp.getRemoteMQTVSHandler().getChannelPathNames()[j]) );
								out1.println("");
								if ( imp.isComposite() ) 
									out1
									.println((deNovoMovieFile!=null 
												&& imp.getMotherImp().getMultiChannelController()!=null
												&& imp.getMotherImp().getMultiChannelController().channelLUTChoice!=null?
													"LUT = "
													+ (j==0?"Green":"Red")
															 :channelLUTChoice!=null?
																	 "LUT = "
																	 + channelLUTItems[((Choice) channelLUTChoice[j])
																	                   .getSelectedIndex()]:
																	                	   channelLUTItems[j]) 
											);

								if (ci!=null) {
									if (ci.getMode() == 1) {

										out1.println("DisplayRangeMin = "
												+ ((deNovoMovieFile!=null && !(deNovoMovieFile.getName().startsWith("SW_")|| deNovoMovieFile.getName().startsWith("RGB_")))?0:ci.getProcessor(j + 1).getMin()));
										out1.println("DisplayRangeMax = "
												+ ((deNovoMovieFile!=null && !(deNovoMovieFile.getName().startsWith("SW_")|| deNovoMovieFile.getName().startsWith("RGB_")))?255:ci.getProcessor(j + 1).getMax()));
									} else {
										ci.setPosition(j + 1, ci.getSlice(), ci
												.getFrame());
										out1.println("DisplayRangeMin = "
												+ ((deNovoMovieFile!=null && !(deNovoMovieFile.getName().startsWith("SW_")|| deNovoMovieFile.getName().startsWith("RGB_")))?0:ci.getProcessor().getMin()));
										out1.println("DisplayRangeMax = "
												+ ((deNovoMovieFile!=null && !(deNovoMovieFile.getName().startsWith("SW_")|| deNovoMovieFile.getName().startsWith("RGB_")))?255:ci.getProcessor().getMax()));

									}
								}
								out1.println("FlipVertical = "
										+ (deNovoMovieFile!=null?false:flipVCB[j].getState()));
								out1.println("FlipHorizontal = "
										+ (deNovoMovieFile!=null?false:flipHCB[j].getState()));
								out1.println("FlipZaxis = "
										+ (deNovoMovieFile!=null?false:flipZCB[j].getState()));
								out1.println("ShiftZ = "
										+ (deNovoMovieFile!=null?0:Integer.parseInt(sliceSpinner[j]
												.getValue().toString())));
								out1.println("ShiftT = "
										+ (deNovoMovieFile!=null?0:Integer.parseInt(frameSpinner[j]
												.getValue().toString())));
								out1.println("deltaZ = "
										+ (deNovoMovieFile!=null?1:Integer.parseInt(deltaZSpinner[j]
												.getValue().toString())));
								out1.println("deltaT = "
										+ (deNovoMovieFile!=null?1:Integer.parseInt(deltaTSpinner[j]
												.getValue().toString())));
								out1.println("ScaleX = "
										+ (deNovoMovieFile!=null?0.0:Double.parseDouble(scaleXSpinner[j]
												.getValue().toString())));
								out1.println("ScaleY = "
										+ (deNovoMovieFile!=null?0.0:Double.parseDouble(scaleYSpinner[j]
												.getValue().toString())));
								out1.println("RotationAngle = "
										+ (deNovoMovieFile!=null?0.0:Double.parseDouble(rotateAngleSpinner[j]
												.getValue().toString())));
								out1.println("ShiftX = "
										+ (deNovoMovieFile!=null?0.0:Double.parseDouble(translateXSpinner[j]
												.getValue().toString())));
								out1.println("ShiftY = "
										+ (deNovoMovieFile!=null?0.0:Double.parseDouble(translateYSpinner[j]
												.getValue().toString())));
								out1.println("DropFrames = ," + (deNovoMovieFile!=null?"":dropFramesField[j].getText() ));
								out1.println("End of parameter list");

								out1.close();

							} catch (IOException ev) {
								//if (IJ.debugMode) IJ.log("debug c");

							}
						}
					}
					if ( rm != null) {
						if (rm.getCount()>0){
							((RoiManager)rm).runCommand("Save", saveFile.getParent() +File.separator +"MQTVS_"+ sec +"_ROIs.zip");
						}
						ColorLegend cl = rm.getColorLegend();
						if ( cl != null) {
							cl.save(saveFile.getParent() +File.separator + "MQTVS_"+ sec +"_ColorLegend.lgd");
						}
					}
				}
				catch (IOException ev)
				{
				}
			}
			if (command.equals("Show Scene Details")) {
				if (imp.getStack() instanceof MultiQTVirtualStack) {
					tw = new TextWindow(((MultiQTVirtualStack)imp.getStack()).getSceneFileName(),"", "Shared as: \nhttp://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE="+((MultiQTVirtualStack)imp.getStack()).getSceneFileName()+"\n"+((MultiQTVirtualStack)imp.getStack()).getSceneFileText(), 300, 300);
				} else if (imp.getStack() instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
					tw = new TextWindow(((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack()).getSceneFileName(),"", "Shared as: \nhttp://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE="+((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack()).getSceneFileName()+"\n"+((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack()).getSceneFileText(), 300, 300);
				} else {
					tw = new TextWindow(saveFile.getName(), "", "Shared as: \nhttp://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE="+saveFile.getName()+"\n"+IJ.openAsString(saveFile.getPath()), 300, 300);
				}
			}

			if (sharing) {
				FTPClient ftpc = new FTPClient();
				try {
					ftpc.connect("155.37.255.65");
					int reply = ftpc.getReplyCode();

					if(!FTPReply.isPositiveCompletion(reply)) {
						ftpc.disconnect();
						IJ.log("FTP server refused connection.");
					} else {
						ftpc.login("glowormguest", "GLOWorm");
						String[] saveDirFileNames = (new File(saveFile.getParent())).list();
						for (String fileName:saveDirFileNames) {
							if (fileName.matches(".*"+sec+".*")) {
								FileInputStream fis = new FileInputStream(saveFile.getParent() +File.separator +fileName);
								ftpc.setFileType(FTPClient.BINARY_FILE_TYPE);
								ftpc.enterLocalPassiveMode();
								ftpc.storeFile(fileName+".tmp", fis);
								fis.close();
								ftpc.rename(fileName+".tmp", fileName);
							}
						}
//						if (deNovoMovieFile !=null) {
//							FileInputStream fis = new FileInputStream(deNovoMovieFile.getPath());
//							ftpc.setFileType(FTPClient.BINARY_FILE_TYPE);
//							ftpc.enterLocalPassiveMode();
//							ftpc.storeFile(deNovoMovieFile.getName()+".tmp", fis);
//							fis.close();
//							ftpc.rename(deNovoMovieFile.getName()+".tmp", deNovoMovieFile.getName());
//						}
						ftpc.logout();
					}
				} catch (SocketException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} finally {
					if(ftpc.isConnected()) {
						try {
							ftpc.disconnect();
						} catch(IOException ioe) {
							// do nothing
						}
					}
				}

				IJ.wait(10000);

				GenericDialog lgd = new GenericDialog("Shared CytoSHOW Scene");
				lgd.addMessage("This scene can be accessed at the web address below.\n" +
						"Please copy, save, and share this link.\n");
				String link = "http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE="
						+saveFile.getName();
				lgd.addStringField("", link, link.length());
				lgd.pack();
				lgd.showDialog();

				sharing = false;
			}
//			if (deNovoMovieFile!=null) {
//				MQTVSSceneLoader.runMQTVS_SceneLoader(saveFile.getPath());
//			}

		} else {
			IJ.doCommand(command);
		}
	}

	public static Frame getInstance() {
		return instance;
	}

	public void close() {
		super.setVisible(false);

		location = getLocation();
	}

	public void dispose() {
		super.dispose();
		this.removeKeyListener(IJ.getInstance());
		location = getLocation();
	}

	public void windowClosing(WindowEvent e) {
		close();
	}


	public synchronized void stateChanged(ChangeEvent e) {

		ImageStack stack = imp.getStack();		

		boolean forward = false;
		if (imp==null) return;
		for (int i=0; i< imp.getNChannels(); i++){ 
			if (e.getSource()==sliceSpinner[i]) {
				forward = true;
				String zShiftLive = sliceSpinner[i].getValue().toString();
				int zShiftNet = 0;  
				if (stack instanceof MultiQTVirtualStack )  { 					
					zShiftNet = Integer.parseInt(zShiftLive) - ((MultiQTVirtualStack)stack).getShiftSingleMovieZPosition(i);
					((MultiQTVirtualStack)stack).setShiftSingleMovieZPosition(i, zShiftNet);
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
					zShiftNet = Integer.parseInt(zShiftLive) - ((RemoteMQTVSHandler.RemoteMQTVirtualStack)stack).getShiftSingleMovieZPosition(i);
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setShiftSingleMovieZPosition(i, Integer.parseInt(zShiftLive));
				}
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==frameSpinner[i]) {
				forward = true;
				String tShiftLive = frameSpinner[i].getValue().toString();
				int tShiftNet = 0;  
				if (stack instanceof MultiQTVirtualStack )  { 					
					tShiftNet = Integer.parseInt(tShiftLive) - ((MultiQTVirtualStack)stack).getShiftSingleMovieZPosition(i);
					((MultiQTVirtualStack)stack).setShiftSingleMovieZPosition(i, tShiftNet);
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack) {
					tShiftNet = Integer.parseInt(tShiftLive) - ((RemoteMQTVSHandler.RemoteMQTVirtualStack)stack).getShiftSingleMovieZPosition(i);
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setShiftSingleMovieTPosition(i, Integer.parseInt(tShiftLive));
				}
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==deltaZSpinner[i]) {
				if (stack instanceof MultiQTVirtualStack ) {
					((MultiQTVirtualStack) stack).setRelativeZFrequency(i, Integer.parseInt(deltaZSpinner[i].getValue().toString()));	 
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack ) {
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setRelativeZFrequency(i, Integer.parseInt(deltaZSpinner[i].getValue().toString()));	 
				} 
				//if (IJ.debugMode) IJ.log( ((MultiQTVirtualStack) stack).getVirtualStack(i).getMovieFile().toString() + " deltaZ =" + Integer.parseInt(deltaZSpinner[i].getValue().toString() ) );
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==deltaTSpinner[i]) {
				if (stack instanceof MultiQTVirtualStack ) {
					((MultiQTVirtualStack) stack).setRelativeFrameRate(i, Integer.parseInt(deltaTSpinner[i].getValue().toString()));	
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack ) {
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setRelativeFrameRate(i, Integer.parseInt(deltaTSpinner[i].getValue().toString()));	 
				} 
				//if (IJ.debugMode) IJ.log( ((MultiQTVirtualStack) stack).getVirtualStack(i).getMovieFile().toString() + " deltaT =" + Integer.parseInt(deltaTSpinner[i].getValue().toString() ) ); 
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==scaleXSpinner[i]) {
				if (stack instanceof MultiQTVirtualStack ) {
					((MultiQTVirtualStack) stack).setSingleMovieScale(i, ((MultiQTVirtualStack)stack).getSingleMovieScaleX(i) , Double.parseDouble(scaleYSpinner[i].getValue().toString()));	
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack ) {
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setScaleX(i, Double.parseDouble(scaleXSpinner[i].getValue().toString()));	 
				} 
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==scaleYSpinner[i]) {
				if (stack instanceof MultiQTVirtualStack ) {
					((MultiQTVirtualStack) stack).setSingleMovieScale(i, ((MultiQTVirtualStack)stack).getSingleMovieScaleY(i) , Double.parseDouble(scaleYSpinner[i].getValue().toString()));	
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack ) {
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setScaleY(i, Double.parseDouble(scaleYSpinner[i].getValue().toString()));	 
				} 
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==scaleZSpinner[i]) {
				imp.getCalibration().pixelDepth = (Double) scaleZSpinner[i].getValue();
			}

			if (e.getSource()==rotateAngleSpinner[i]) {
				if (stack instanceof MultiQTVirtualStack ) {
					((MultiQTVirtualStack) stack).setSingleMovieRotationAngle(i, Double.parseDouble(rotateAngleSpinner[i].getValue().toString()) );
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack ) {
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setSingleMovieRotationAngle(i, Double.parseDouble(rotateAngleSpinner[i].getValue().toString()) );	 
				} 
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==translateXSpinner[i]) {
				if (stack instanceof MultiQTVirtualStack ) {
					((MultiQTVirtualStack) stack).setSingleMovieTranslate(i, Double.parseDouble(translateXSpinner[i].getValue().toString()), ( ((MultiQTVirtualStack)stack).getSingleMovieTranslateY(i) ) );
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack ) {
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setTranslateX(i, Double.parseDouble(translateXSpinner[i].getValue().toString()) );	 
				} 
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}

			if (e.getSource()==translateYSpinner[i]) {
				if (stack instanceof MultiQTVirtualStack ) {
					((MultiQTVirtualStack) stack).setSingleMovieTranslate(i, ((MultiQTVirtualStack)stack).getSingleMovieTranslateX(i), (Double.parseDouble(translateYSpinner[i].getValue().toString())) );
				} else if (stack instanceof RemoteMQTVSHandler.RemoteMQTVirtualStack ) {
					((RemoteMQTVSHandler.RemoteMQTVirtualStack) stack).setTranslateY(i, Double.parseDouble(translateYSpinner[i].getValue().toString()) );	 
				} 
				/*How can this be made to live. update?*/
				imp.updateAndDraw();
				if (doingFirstSetup)  
					imp.getRoiManager().setRmNeedsUpdate(true);
			}
			if (!openingNewMovie) {
				if ( imp != null && !((StackWindow)imp.getWindow()).getAnimate() ) {
					//if (IJ.debugMode) IJ.log( "Animation off. ") ;
//					if(imp.getNFrames()>1) {
//						imp.setPosition( imp.getChannel(), imp.getSlice(), imp.getFrame()+1 );
//						imp.updateAndDraw();
//						imp.setPosition( imp.getChannel(), imp.getSlice(), imp.getFrame()-1 );
//					} else {
//						imp.setPosition( imp.getChannel(), imp.getSlice()+1, imp.getFrame() );
//						imp.updateAndDraw();
//						imp.setPosition( imp.getChannel(), imp.getSlice()-1, imp.getFrame() );
//					}
					if (imp.isComposite()) {
						int mode = ((CompositeImage)imp).getMode();
						((CompositeImage)imp).setMode(1);
						((CompositeImage)imp).setMode(2);
						((CompositeImage)imp).setMode(3);
						((CompositeImage)imp).setMode(mode);
					}
				}
			}
		}
	}

	public void updateRoiManager() {
		if (!imp.getRoiManager().isRmNeedsUpdate())
			return;
		imp.getRoiManager().setRmNeedsUpdate(false);
		RoiManager rm = imp.getRoiManager();
		if (rm != null){
			StackWindow swin = (StackWindow) imp.getWindow();
			WindowManager.setTempCurrentImage(imp);
			IJ.run("Stop Animation");
			Roi[] rois = rm.getFullRoisAsArray();
			Roi[] resetRois = rm.getOriginalRoisAsArray();
			int rmc = rm.getCount();
			int numAdded = 0;
			String impTitle = imp.getTitle();
			String rmTitle = rm.getTitle();
			double centerX = imp.getWidth()/2;
			double centerY = imp.getHeight()/2;

			for (int c=0;c<imp.getNChannels();c++) {
				double angleRadians = Double.parseDouble(rotateAngleSpinner[c].getValue().toString())/(180.0/Math.PI);
				double ca = Math.cos(angleRadians);
				double sa = Math.sin(angleRadians);
				double tmp1 = centerY*sa-centerX*ca;
				double tmp2 = -centerX*sa-centerY*ca;
				double tmp3, tmp4, xs, ys;
				int  ixs, iys;

				for (int i=0;i<rmc;i++) {
					if (rois.length>0 && rois[i] != null && (rois[i].getCPosition() == c+1 /*|| (Integer.parseInt(rois[i].getName().split("_")[1]) == c+1)*/)) {
						// Update FlipZ ***
						if (flipZCB[c].getState()) {
							rois[i].setPosition(resetRois[i].getCPosition(),imp.getNSlices()-resetRois[i].getZPosition()+1,resetRois[i].getTPosition());
						} else {
							rois[i].setPosition(resetRois[i].getCPosition(),resetRois[i].getZPosition(),resetRois[i].getTPosition());
						}
						// Update FlipV ***
						if (flipVCB[c].getState()){
							rois[i].setLocation((int)resetRois[i].getBounds().getX(),
									imp.getHeight()-(int)resetRois[i].getBounds().getMinY()-(int)rois[i].getBounds().getHeight());
						} else {
							rois[i].setLocation((int)resetRois[i].getBounds().getX(),
									(int)resetRois[i].getBounds().getY());
						}
						// Update FlipH
						if (flipHCB[c].getState()) {
							rois[i].setLocation(imp.getWidth()-(int)rois[i].getBounds().getMinX()-(int)rois[i].getBounds().getWidth(),
									(int)rois[i].getBounds().getY());
						} else {
							rois[i].setLocation((int)rois[i].getBounds().getX(),
									(int)rois[i].getBounds().getY());
						}
						// Update Translate XY
						rois[i].setLocation((int)(rois[i].getBounds().getCenterX()-(int)rois[i].getBounds().getWidth()/2 + Double.parseDouble(translateXSpinner[c].getValue().toString())), (int)(rois[i].getBounds().getCenterY()-(int)rois[i].getBounds().getHeight()/2 - Double.parseDouble(translateYSpinner[c].getValue().toString())));
						// Update Scales
						rois[i].setLocation((int)(((rois[i].getBounds().getCenterX()-(int)rois[i].getBounds().getWidth()/2) * (1+ (Double.parseDouble(scaleXSpinner[c].getValue().toString()) * 0.01) ) ) - imp.getWidth() *(1+ (Double.parseDouble(scaleXSpinner[c].getValue().toString()) * 0.01) )/2 + imp.getWidth()/2 ), 
								(int)(((rois[i].getBounds().getCenterY()-(int)rois[i].getBounds().getHeight()/2) * (1+ (Double.parseDouble(scaleYSpinner[c].getValue().toString()) * 0.01) ) ) - imp.getHeight() *(1+ (Double.parseDouble(scaleYSpinner[c].getValue().toString()) * 0.01) )/2 + imp.getHeight()/2 ) );
						// Update Angles
						tmp3 = tmp1 - rois[i].getBounds().getCenterY()*sa + centerX;
						tmp4 = tmp2 + rois[i].getBounds().getCenterY()*ca + centerY;
						xs = rois[i].getBounds().getCenterX()*ca + tmp3;
						ys = rois[i].getBounds().getCenterX()*sa + tmp4;
						ixs = (int)(xs+0.5);
						iys = (int)(ys+0.5);
						if (ixs>=imp.getWidth()) ixs = imp.getWidth() - 1;
						if (iys>=imp.getHeight()) iys = imp.getHeight() -1;
						rois[i].setLocation((int)(ixs-rois[i].getBounds().getWidth()/2 ), (int)(iys-rois[i].getBounds().getHeight()/2 ) );
						// Update Zshifts
						int newZposition = (int) (rois[i].getZPosition() - Double.parseDouble(sliceSpinner[c].getValue().toString()));
						if (newZposition < 1) {
							newZposition = imp.getNSlices() + newZposition;
						}
						while (newZposition > imp.getNSlices()) {
							newZposition = newZposition - imp.getNSlices();
						}	
						rois[i].setPosition(rois[i].getCPosition(), newZposition ,rois[i].getTPosition());
						// Update Zfrequencies
						if (imp.getStack() instanceof RemoteMQTVirtualStack)
							newZposition = (int) (rois[i].getZPosition() 
								/ ((double)((RemoteMQTVirtualStack)imp.getStack()).getRelativeZFrequencySingleMovie()[c]
										/ (double)((RemoteMQTVirtualStack)imp.getStack()).maximumRelativeZFrequency) );
						else if (imp.getStack() instanceof MultiQTVirtualStack) 
							newZposition = (int) (rois[i].getZPosition() 
								/ ((double)((MultiQTVirtualStack)imp.getStack()).getRelativeZFrequencySingleMovie()[c]
										/ (double)((MultiQTVirtualStack)imp.getStack()).maximumRelativeZFrequency) );
						rois[i].setPosition(rois[i].getCPosition(), newZposition, rois[i].getTPosition());
						// Update Tshifts
						int newTposition = (int) (rois[i].getTPosition() - Double.parseDouble(frameSpinner[c].getValue().toString()));
						if (newTposition < 1) {
							newTposition = imp.getNFrames() + newTposition;
						}
						while (newTposition > imp.getNFrames()) {
							newTposition = newTposition - imp.getNFrames();
						}
						rois[i].setPosition(rois[i].getCPosition(), rois[i].getZPosition(), newTposition);
						// Update Tfrequencies
						if (imp.getStack() instanceof RemoteMQTVirtualStack)
							newTposition = (int) (rois[i].getTPosition() 
									/ ((double)((RemoteMQTVirtualStack)imp.getStack()).getRelativeFrameRateSingleMovie()[c]
											/ (double)((RemoteMQTVirtualStack)imp.getStack()).maximumRelativeFrameRate) );
						else if (imp.getStack() instanceof MultiQTVirtualStack) 
							newTposition = (int) (rois[i].getTPosition() 
								/ ((double)((MultiQTVirtualStack)imp.getStack()).getRelativeFrameRateSingleMovie()[c]
										/ (double)((MultiQTVirtualStack)imp.getStack()).maximumRelativeFrameRate) );
						rois[i].setPosition(c+1, rois[i].getZPosition(), newTposition );
					}
					numAdded++;

					rm.setTitle(  "Tag Manager" + ((numAdded%100>50)?" REFITTING!!!":" Refitting...") );
					if (numAdded%100>50){
						IJ.runMacro("print(\"\\\\Update:***Tag Manager is still refitting tags...***\")");
						imp.setTitle("***"+ impTitle);
					}else{
						IJ.runMacro("print(\"\\\\Update:   Tag Manager is still refitting tags...   \")");
						imp.setTitle("   "+ impTitle);
					}

				}
			}
			IJ.runMacro("print(\"\\\\Update:\")");
			imp.setTitle(impTitle);
			rm.setTitle(rmTitle);
			while (swin.running || swin.running2 || swin.running3)
				IJ.wait(100);
			//			swin.running = animating;
			//			swin.running2 = tAnimating;
			//			swin.running3 = zAnimating;

			//			if (zAnimating) {
			//				WindowManager.setTempCurrentImage(imp);
			//				IJ.run("Start Z Animation");
			//			}
			//			if (tAnimating) {
			//				WindowManager.setTempCurrentImage(imp);
			//				IJ.run("Start Animation [\\]");
			//			}
		}		

	}

	public String getChannelLUTItem(int index) {
		return channelLUTItems[index];
	}

	public String getChannelLUTChoice(int index) {
		return channelLUTChoice[index].getSelectedItem();
	}
	
	public void setChannelLUTChoice(int index,int choice) {
		if ( imp.isComposite() ) 
			this.channelLUTChoice[index].select(choice) ;
	}

	public Checkbox getVisibility(int index) {
		return visibility[index];
	}

	public void setVisibility(int index, boolean visibility) {
		this.visibility[index].setState(visibility);
	}

	public Checkbox getFlipVCB(int index) {
		return flipVCB[index];
	}

	public void setFlipVCB(int index, boolean flipVCB) {
		this.flipVCB[index].setState(flipVCB) ;
	}

	public Checkbox getFlipHCB(int index) {
		return flipHCB[index];
	}

	public void setFlipHCB(int index, boolean flipHCB) {
		this.flipHCB[index].setState(flipHCB) ;
	}

	public Checkbox getFlipZCB(int index) {
		return flipZCB[index];
	}

	public void setFlipZCB(int index, boolean flipZCB) {
		this.flipZCB[index].setState(flipZCB) ;
	}

	public JSpinner getSliceSpinner(int index) {
		return sliceSpinner[index];
	}

	public void setSliceSpinner(int index, int shiftT) {
		this.sliceSpinner[index].setValue(shiftT);
	}

	public JSpinner getFrameSpinner(int index) {
		return frameSpinner[index];
	}

	public void setFrameSpinner(int index, int shiftT) {
		this.frameSpinner[index].setValue(shiftT);
	}

	public JSpinner getDeltaZSpinner(int index) {
		return deltaZSpinner[index];
	}

	public void setDeltaZSpinner(int index, int shiftT) {
		this.deltaZSpinner[index].setValue(shiftT);
	}

	public JSpinner getDeltaTSpinner(int index) {
		return deltaTSpinner[index];
	}

	public void setDeltaTSpinner(int index, int shiftT) {
		this.deltaTSpinner[index].setValue(shiftT);
	}

	public JSpinner getScaleXSpinner(int index) {
		return scaleXSpinner[index];
	}

	public void setScaleXSpinner(int index, double scaleX) {
		this.scaleXSpinner[index].setValue(scaleX);
	}

	public JSpinner getScaleYSpinner(int index) {
		return scaleYSpinner[index];
	}

	public void setScaleYSpinner(int index, double scaleY) {
		this.scaleYSpinner[index].setValue(scaleY);
	}

	public JSpinner getScaleZSpinner(int index) {
		return scaleZSpinner[index];
	}

	public void setScaleZSpinner(int index, double scaleZ) {
		this.scaleZSpinner[index].setValue(scaleZ);
	}

	public JSpinner getRotateAngleSpinner(int index) {
		return rotateAngleSpinner[index];
	}

	public void setRotateAngleSpinner(int index, double rotAngle) {
		this.rotateAngleSpinner[index].setValue(rotAngle);
	}

	public JSpinner getTranslateXSpinner(int index) {
		return translateXSpinner[index];
	}

	public void setTranslateXSpinner(int index, double shiftX) {
		this.translateXSpinner[index].setValue(shiftX);
	}

	public JSpinner getTranslateYSpinner(int index) {
		return translateYSpinner[index];
	}

	public void setTranslateYSpinner(int index, double shiftY) {
		this.translateYSpinner[index].setValue(shiftY);
	}

	public int getPreviousShiftZ(int index) {
		return previousShiftZ[index];
	}

	public void setPreviousShiftZ(int index, int previousShiftZ) {
		this.previousShiftZ[index] = previousShiftZ;
	}

	public int getPreviousShiftT(int index) {
		return previousShiftT[index];
	}

	public void setPreviousShiftT(int index, int previousShiftT) {
		this.previousShiftT[index] = previousShiftT;
	}

	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseEntered(MouseEvent e) {
//		IJ.runMacro("print(\"\\\\Clear\")");
//		IJ.runMacro("print(\"\\\\Update:Multi-Channel Controller:\\\nProvides adjustments for space/time synchronization of \\\nmultiple channels displayed in the same movie window. \\\n \")");

	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public boolean isOpeningNewMovie() {
		return openingNewMovie;
	}

	public void setOpeningNewMovie(boolean openingNewMovie) {
		this.openingNewMovie = openingNewMovie;
	}

	public JTextField getMovieNameField(int index) {
		return channelNameField[index];
	}

	public void setMovieNameField(int index, JTextField movieNameField) {
		this.channelNameField[index] = movieNameField;
	}

	public String getDropFramesFieldText(int index) {
		if (dropFramesField == null || index >= dropFramesField.length)
			return null;
		return dropFramesField[index].getText();
	}

	public void setDropFramesFieldText(String text, int index) {
		this.dropFramesField[index].setText(text);
	}


}

