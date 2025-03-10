package ij.gui;
import ij.*;
import ij.measure.Calibration;
import ij.plugin.MultiFileInfoVirtualStack;
import ij.plugin.Orthogonal_Views;
import ij.plugin.frame.SyncWindows;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.ToolTipManager;

import org.vcell.gloworm.MultiChannelController;
import org.vcell.gloworm.MultiQTVirtualStack;

/** This class is an extended ImageWindow used to display image stacks. */
public class StackWindow extends ImageWindow implements Runnable, AdjustmentListener, ActionListener, MouseWheelListener {

	protected JScrollBar sliceSelector; // for backward compatibity with Image5D
	public ScrollbarWithLabel cSelector, zSelector, tSelector;
	public ArrayList<ScrollbarWithLabel> activeScrollBars = new ArrayList<ScrollbarWithLabel>();
	protected Thread thread;
	protected volatile boolean done;
	protected volatile int slice;
//	private ScrollbarWithLabel animationSelector, animationZSelector;
	boolean hyperStack;
	int nChannels=1, nSlices=1, nFrames=1;
	int c=1, z=1, t=1;
	boolean wormAtlas;
	JPanel scrollbarPanel;
	

	public StackWindow(ImagePlus imp, boolean showNow) {
		this(imp, null, showNow);
	}
    
    public StackWindow(ImagePlus imp, ImageCanvas2 ic, boolean showNow) {
		super(imp, ic);
		addScrollbars(imp);
		addMouseWheelListener(this);
		if (sliceSelector==null && this.getClass().getName().indexOf("Image5D")!=-1) {
			sliceSelector = new JScrollBar(); // prevents Image5D from crashing
		}
		//IJ.log(nChannels+" "+nSlices+" "+nFrames);
		pack();
		ic = imp.getCanvas();
		if (ic!=null) ic.setMaxBounds();
		if (showNow)
			show();
		int previousSlice = imp.getCurrentSlice();
		if (previousSlice>1 && previousSlice<=imp.getStackSize())
			imp.setSlice(previousSlice);
		else
			imp.setSlice(1);
		thread = new Thread(this, "SliceSelector");
		thread.start();
		this.pack();
		int padH = 1+this.getInsets().left
				+this.getInsets().right
				+(this.optionsPanel.isVisible()?this.optionsPanel.getWidth():0)
				+this.viewButtonPanel.getWidth();
		int padV = this.getInsets().top
				+this.getInsets().bottom
				+(this instanceof StackWindow?
						((StackWindow)this).getNScrollbars()
						*(((StackWindow)this).zSelector!=null?
								((StackWindow)this).zSelector.getHeight():
									((StackWindow)this).tSelector!=null?
											((StackWindow)this).tSelector.getHeight():
												((StackWindow)this).cSelector!=null?
														((StackWindow)this).cSelector.getHeight():0)
						:0)
						+this.overheadPanel.getHeight();
		this.setSize(ic.dstWidth+padH, ic.dstHeight+padV);

	}
	
	public void addScrollbars(ImagePlus imp) {
		ImageStack s = imp.getStack();
		wormAtlas = ( (s instanceof MultiQTVirtualStack && ((MultiQTVirtualStack) imp.getMotherImp().getStack()).getVirtualStack(0) != null) && ((MultiQTVirtualStack)s).getVirtualStack(0).getMovieName().startsWith("SW"));
		int stackSize = s.getSize();
		nSlices = stackSize;
		hyperStack = true;  //imp.getOpenAsHyperStack();
		//imp.setOpenAsHyperStack(false);
		int[] dim = imp.getDimensions();
		int nDimensions = 2+(dim[2]>1?1:0)+(dim[3]>1?1:0)+(dim[4]>1?1:0);
		if (nDimensions<=3 && (dim[2]!=nSlices && dim[3]!=nSlices && dim[4]!=nSlices ) )
			hyperStack = false;
		if (hyperStack) {
			nChannels = dim[2];
			nSlices = dim[3];
			nFrames = dim[4];
		}
		//IJ.log("StackWindow: "+hyperStack+" "+nChannels+" "+nSlices+" "+nFrames);
//		if (nSlices==stackSize) hyperStack = false;
		if (nChannels*nSlices*nFrames!=stackSize) hyperStack = false;
		if (cSelector!=null||zSelector!=null||tSelector!=null)
			removeScrollbars();
		ImageJ ij = IJ.getInstance();

		scrollbarPanel = new JPanel();
		GridBagLayout sbgridbag = new GridBagLayout();
		GridBagConstraints sbgc = new GridBagConstraints();
		scrollbarPanel.setLayout(sbgridbag);
		int y = 0;
		sbgc.gridx = 0;
		sbgc.gridy = y++;
		sbgc.gridwidth = GridBagConstraints.REMAINDER;
		sbgc.fill = GridBagConstraints.BOTH;
		sbgc.weightx = 1.0;
		sbgc.weighty = 1.0;


		if (nChannels>1) {
			cSelector = new ScrollbarWithLabel(this, 1, 1, 1, nChannels+1, 'c');
			((JComponent) cSelector.iconPanel).setToolTipText("<html>Left-Clicking this Channel selector's icon <br>changes the Display Mode from <br>\'Channels Merged\' to \'Channels Color\' to \'Channels Gray\'.<br>Right-clicking or control-clicking <br>activates the Channels Tool.</html>");		
			((JComponent) cSelector.icon2Panel).setToolTipText("<html>Left-Clicking this Channel selector's icon <br>changes the Display Mode from <br>\'Channels Merged\' to \'Channels Color\' to \'Channels Gray\'.<br>Right-clicking or control-clicking <br>activates the Channels Tool.</html>");		
			if (wormAtlas) cSelector = new ScrollbarWithLabel(this, 1, 1, 1, nChannels+1, 'r');
			sbgridbag.setConstraints(cSelector, sbgc);
			sbgc.gridy = y++; 
			scrollbarPanel.add(cSelector);
			cSelector.addAdjustmentListener(this);
			cSelector.setFocusable(false); // prevents scroll bar from blinking on Windows
			cSelector.setUnitIncrement(1);
			cSelector.setBlockIncrement(1);
			activeScrollBars.add(cSelector);

		}
		if (nSlices>1) {
			char label = nChannels>1||nFrames>1?'z':'z';  
			if (stackSize==dim[2] && imp.isComposite()) label = 'c';
			if (wormAtlas) label = 'c';
			zSelector = new ScrollbarWithLabel(this, 1, 1, 1, nSlices+1, label);
			((JComponent) zSelector.iconPanel).setToolTipText("<html>Left-Clicking this Slice selector's icon <br>plays/pauses animation through the Z dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
			((JComponent) zSelector.icon2Panel).setToolTipText("<html>Left-Clicking this Slice selector's icon <br>plays/pauses animation through the Z dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		

//			if (label=='t') 
//				animationSelector = zSelector;
//			if (label=='z') {
//				animationZSelector = zSelector; 
//				setZAnimate(false); 
//			}
			sbgridbag.setConstraints(zSelector, sbgc);
			sbgc.gridy = y++; 
			scrollbarPanel.add(zSelector);
			zSelector.addAdjustmentListener(this);
			zSelector.setFocusable(false);
			int blockIncrement = nSlices/10;
			if (blockIncrement<1) blockIncrement = 1;
			zSelector.setUnitIncrement(1);
			zSelector.setBlockIncrement(blockIncrement);
			sliceSelector = zSelector.bar;
			activeScrollBars.add(zSelector);

		}
		if (nFrames>1) {
			/*animationSelector = */tSelector = new ScrollbarWithLabel(this, 1, 1, 1, nFrames+1, 't');
			((JComponent) tSelector.iconPanel).setToolTipText("<html>Left-Clicking this Frame selector's icon <br>plays/pauses animation through the T dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
			((JComponent) tSelector.icon2Panel).setToolTipText("<html>Left-Clicking this Frame selector's icon <br>plays/pauses animation through the T dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
			if (wormAtlas) {
				/*animationSelector = */tSelector = new ScrollbarWithLabel(this, 1, 1, 1, nFrames+1, 'z');
				((JComponent) tSelector.iconPanel).setToolTipText("<html>Left-Clicking this Slice selector's icon <br>plays/pauses animation through the Z dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
				((JComponent) tSelector.icon2Panel).setToolTipText("<html>Left-Clicking this Slice selector's icon <br>plays/pauses animation through the Z dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
			}
			sbgridbag.setConstraints(tSelector, sbgc);
			sbgc.gridy = y++; 
			scrollbarPanel.add(tSelector);
			tSelector.addAdjustmentListener(this);
			tSelector.setFocusable(false);
			int blockIncrement = nFrames/10;
			if (blockIncrement<1) blockIncrement = 1;
			tSelector.setUnitIncrement(1);
			tSelector.setBlockIncrement(blockIncrement);
			activeScrollBars.add(tSelector);

		}
		this.add(scrollbarPanel, BorderLayout.SOUTH);
		
	}

	public synchronized void adjustmentValueChanged(AdjustmentEvent e) {
		Frame[] niw = WindowManager.getNonImageWindows();		
		for (int m=0; m < niw.length; m++) {
			if (niw[m].getTitle().contains("Composite Adjuster")) {
				niw[m].toFront();
				IJ.showMessage("Close Composite Adjuster when changing position", 
						"Please close Composite Adjuster window and \n toggle the Channel selector before changing stack position.");				
				return;
			}
		}
		int trueFrame = t;

		if (running2 || running3) {
			boolean animationState = this.running2;
			boolean animationZState = this.running3;
			if ((e.getSource() == tSelector) || (e.getSource() == zSelector)) {
				IJ.doCommand("Stop Animation");
			} else if (e.getSource() == cSelector) {
				IJ.doCommand("Stop Animation");
				if (this.getImagePlus().isComposite()) 
					((CompositeImage) this.getImagePlus()).setC(cSelector.getValue());
				int origChannel = this.getImagePlus().getChannel();
				if (this.getImagePlus().isComposite() && ((CompositeImage) this.imp).getMode() ==1 ) {
					((CompositeImage)this.getImagePlus()).setMode(3);
					((CompositeImage)this.getImagePlus()).setMode(1);
				}
				if (animationState) IJ.doCommand("Start Animation [\\]");
				if (animationZState) IJ.doCommand("Start Z Animation");
			}
		}


		if (!running2 && !running3) {
			if (e.getSource()==cSelector) {
				c = cSelector.getValue();
				if (c==imp.getChannel()&&e.getAdjustmentType()==AdjustmentEvent.TRACK) 
					return;
			} else if (e.getSource()==zSelector) {
				z = zSelector.getValue();
				int slice = hyperStack?imp.getSlice():imp.getCurrentSlice();
				if (z==slice&&e.getAdjustmentType()==AdjustmentEvent.TRACK) return;
			} else if (e.getSource()==tSelector) {
				trueFrame = tSelector.getValue();
		    	int frame = trueFrame;
		    	MultiChannelController mcc = imp.getMultiChannelController();
		    	if (mcc!=null && mcc.getDropFramesFieldText(c-1)!=null) {
		    		String[] dropFramesChunks = mcc.getDropFramesFieldText(c-1).split(",");
		    		ArrayList<String> dropFramesChunksArrayList = new ArrayList<String>();
		    		for (String dropFramesChunk:dropFramesChunks)
		    			dropFramesChunksArrayList.add(dropFramesChunk.trim());
		    		while (dropFramesChunksArrayList.contains(""+frame)) {
		    			frame--;
		    		}
		    	}
				t = frame;
				if (t==imp.getFrame()&&e.getAdjustmentType()==AdjustmentEvent.TRACK) {
					adjustmentValueChanged(new AdjustmentEvent(e.getAdjustable(), 
							AdjustmentEvent.ADJUSTMENT_FIRST,AdjustmentEvent.UNIT_INCREMENT,t,false));
					return;
				}
			}
			updatePosition();			
			notify();

			if (e.getSource()==cSelector &&  (imp.isComposite() && ((CompositeImage) this.imp).getMode() ==1 ) ) {
				if (WindowManager.getFrame("Display") != null)
					WindowManager.getFrame("Display").toFront();
				this.toFront();
			}

		}
		if (!running && !running2 && !running3)
			syncWindows(e.getSource());
	}
	
	private void syncWindows(Object source) {
		if (SyncWindows.getInstance()==null)
			return;
		if (source==cSelector)
			SyncWindows.setC(this, cSelector.getValue());
		else if (source==zSelector)
			SyncWindows.setZ(this, zSelector.getValue());
		else if (source==tSelector)
			SyncWindows.setT(this, tSelector.getValue());
		else
			throw new RuntimeException("Unknownsource:"+source);
	}

	
	void updatePosition() {
		if (imp.getOriginalFileInfo() != null && imp.getOriginalFileInfo().fileName.toLowerCase().endsWith("_csv.ome.tif")) {
//			IJ.log("ome");
			slice =  (t-1)*nChannels*nSlices + (c-1)*nSlices + z;
		} else if (imp.getStack() instanceof MultiFileInfoVirtualStack && ((MultiFileInfoVirtualStack)imp.getStack()).getDimOrder() == "xyztc") {
			slice =   (c-1)*nSlices*nFrames + (t-1)*nSlices + z;
		} else {
//			IJ.log("not ome");
			slice = (t-1)*nChannels*nSlices + (z-1)*nChannels + c;
		}
		imp.updatePosition(c, z, t);
		subTitleField.setText(createSubtitle(false));
		subTitleField.setToolTipText(createSubtitle(true));
	}

	public void actionPerformed(ActionEvent e) {
	}

//	public void mouseWheelMoved(MouseWheelEvent event) {
//		synchronized(this) {
//			int rotation = event.getWheelRotation();
//			if (hyperStack) {
//				if (rotation>0)
//					IJ.runPlugIn("ij.plugin.Animator", "next");
//				else if (rotation<0)
//					IJ.runPlugIn("ij.plugin.Animator", "previous");
//			} else {
//				int slice = imp.getCurrentSlice() + rotation;
//				if (slice<1)
//					slice = 1;
//				else if (slice>imp.getStack().getSize())
//					slice = imp.getStack().getSize();
//				imp.setSlice(slice);
//				imp.updateStatusbarValue();
//			}
//		}
//	}

	public boolean close() {
		removeScrollbars();
		if (cSelector!=null) {
			while (cSelector.iconPanel.getMouseWheelListeners().length>0) 
				cSelector.iconPanel.removeMouseWheelListener(cSelector.iconPanel.getMouseWheelListeners()[0]);
			while (cSelector.icon2Panel.getMouseWheelListeners().length>0) 
				cSelector.icon2Panel.removeMouseWheelListener(cSelector.icon2Panel.getMouseWheelListeners()[0]);
			
			cSelector.bar.removeAdjustmentListener(cSelector);
			cSelector.bar.removeMouseListener(cSelector);
			cSelector.removeAdjustmentListener(this);
			cSelector.removeKeyListener(ij);
			while (cSelector.getMouseListeners().length>0) 
				cSelector.removeMouseListener(cSelector.getMouseListeners()[0]);
			while (cSelector.iconPanel.getMouseListeners().length>0) 
				cSelector.iconPanel.removeMouseListener(cSelector.iconPanel.getMouseListeners()[0]);
			while (cSelector.icon2Panel.getMouseListeners().length>0) 
				cSelector.icon2Panel.removeMouseListener(cSelector.icon2Panel.getMouseListeners()[0]);
			ActionListener[] csipals=cSelector.iconPanel.getActionListeners();
			while (csipals.length>0) { 
				cSelector.iconPanel.removeActionListener(csipals[0]);		
				csipals=cSelector.iconPanel.getActionListeners();
			}
			ActionListener[] csipals2=cSelector.icon2Panel.getActionListeners();;
			while (csipals2.length>0) {
				cSelector.icon2Panel.removeActionListener(csipals2[0]);	
				csipals2=cSelector.icon2Panel.getActionListeners();
			}
			
			cSelector.iconPanel.setAction(null);
			cSelector.icon2Panel.setAction(null);
			while (cSelector.getComponents().length >0){
				cSelector.remove(0);
			}
			cSelector.icon=null;
			cSelector.icon2=null;
			cSelector.iconPanel=null;
			cSelector.icon2Panel=null;
			cSelector.bar = null;
			cSelector.stackWindow = null;

			cSelector.adjustmentListener=null;
			cSelector.stackWindow = null;
			cSelector=null;
		}
		if (tSelector!=null) {
			tSelector.bar.removeAdjustmentListener(tSelector);
			tSelector.bar.removeMouseListener(tSelector);
			tSelector.removeAdjustmentListener(this);
			tSelector.removeKeyListener(ij);
			while (tSelector.getMouseListeners().length>0) 
				tSelector.removeMouseListener(tSelector.getMouseListeners()[0]);
			if (tSelector.iconPanel!=null) {
				while (tSelector.iconPanel.getMouseListeners().length>0) 
					tSelector.iconPanel.removeMouseListener(tSelector.iconPanel.getMouseListeners()[0]);
				while (tSelector.icon2Panel.getMouseListeners().length>0) 
					tSelector.icon2Panel.removeMouseListener(tSelector.icon2Panel.getMouseListeners()[0]);
				ActionListener[] tsipals=tSelector.iconPanel.getActionListeners();
				while (tsipals.length>0) {
					tSelector.iconPanel.removeActionListener(tsipals[0]);			
					tsipals=tSelector.iconPanel.getActionListeners();
				}
				ActionListener[] tsipals2=tSelector.icon2Panel.getActionListeners();;
				while (tsipals2.length>0) {
					tSelector.icon2Panel.removeActionListener(tsipals2[0]);	
					tsipals2=tSelector.icon2Panel.getActionListeners();
				}
				tSelector.iconPanel.setAction(null);
				tSelector.icon2Panel.setAction(null);
				while (tSelector.getComponents().length >0){
					tSelector.remove(0);
				}
				tSelector.icon=null;
				tSelector.icon2=null;
				tSelector.iconPanel=null;
				tSelector.icon2Panel=null;
			}
			tSelector.bar = null;
			tSelector.stackWindow = null;

			tSelector.adjustmentListener=null;
			tSelector.stackWindow = null;
			tSelector=null;
		}
		if (zSelector!=null) {
			zSelector.bar.removeAdjustmentListener(zSelector);
			zSelector.bar.removeMouseListener(zSelector);
			zSelector.removeAdjustmentListener(this);
			zSelector.removeKeyListener(ij);
			while (zSelector.getMouseListeners().length>0) 
				zSelector.removeMouseListener(zSelector.getMouseListeners()[0]);
			if (zSelector.iconPanel!=null) {
				while (zSelector.iconPanel.getMouseListeners().length>0) 
					zSelector.iconPanel.removeMouseListener(zSelector.iconPanel.getMouseListeners()[0]);
				while (zSelector.icon2Panel.getMouseListeners().length>0) 
					zSelector.icon2Panel.removeMouseListener(zSelector.icon2Panel.getMouseListeners()[0]);
				ActionListener[] zsipals=zSelector.iconPanel.getActionListeners();
				while (zsipals.length>0) {
					zSelector.iconPanel.removeActionListener(zsipals[0]);		
					zsipals=zSelector.iconPanel.getActionListeners();
				}
				ActionListener[] zasipals2=zSelector.icon2Panel.getActionListeners();
				while (zasipals2.length>0) {
					zSelector.icon2Panel.removeActionListener(zasipals2[0]);	
					zasipals2=zSelector.icon2Panel.getActionListeners();
				}
				zSelector.iconPanel.setAction(null);
				zSelector.icon2Panel.setAction(null);
				while (zSelector.getComponents().length >0){
					zSelector.remove(0);
				}
				zSelector.icon=null;
				zSelector.icon2=null;
				zSelector.iconPanel=null;
				zSelector.icon2Panel=null;
			}
			zSelector.bar = null;
			zSelector.stackWindow = null;

			zSelector.adjustmentListener=null;
			zSelector.stackWindow = null;
			zSelector=null;
		}
		if (activeScrollBars!=null) {
			int asbs = activeScrollBars.size();
			for (int asbl=0;asbl<asbs;asbl++) {
				activeScrollBars.remove(0);
			}
			activeScrollBars=null;
		}
		if (!super.close()) {
			return false;
		}
		synchronized(this) {
			done = true;
			notify();
		}
		this.removeFocusListener(this);
		this.removeMouseWheelListener(this);
		this.removeWindowListener(this);
		this.removeWindowStateListener(this);
		this.dispose();
        return true;
	}

	/** Displays the specified slice and updates the stack scrollbar. */
	public void showSlice(int index) {
		if (imp!=null && index>=1 && index<=imp.getStackSize()) {
			imp.setSlice(index);
			SyncWindows.setZ(this, index);
		}
	}
	
	/** Updates the stack scrollbar. */
	public void updateSliceSelector() {
		if (hyperStack || zSelector==null) return;
		int stackSize = imp.getStackSize();
		int max = zSelector.getMaximum();
		if (max!=(stackSize+1))
			zSelector.setMaximum(stackSize+1);
		zSelector.setValue(imp.getCurrentSlice());
	}
	
	public void run() {
		while (!done) {
			synchronized(this) {
				try {wait(10);}
				catch(InterruptedException e) {}
			}
			if (done) return;
			if (slice>0) {
				int s = slice;
				slice = 0;
				if (imp!=null || s!=imp.getCurrentSlice() ) {
					imp.setSlice(s);
				}
			}
		}
	}
	
	public String createSubtitle(boolean longPath) {
		String subtitle = super.createSubtitle(longPath);
		if (!hyperStack) return subtitle;
    	String s="";
    	int[] dim = imp.getDimensions(false);
    	int channels=dim[2], slices=dim[3], frames=dim[4];
		if (channels>=1) {
			String channelLabel ="";
//			if( this.getImagePlus().getStack() instanceof MultiQTVirtualStack && ((MultiQTVirtualStack) imp.getMotherImp().getStack()).getVirtualStack(0) != null)
//				channelLabel = " [" + ((MultiQTVirtualStack)this.getImagePlus().getStack()).getVirtualStack(imp.getChannel()-1).getMovieName() + "]";
//			else if (this.getImagePlus().getRemoteMQTVSHandler()!=null)
//				channelLabel = " [" + this.getImagePlus().getRemoteMQTVSHandler().getChannelPathNames()[imp.getChannel()-1].replaceAll(".*/", "").replaceAll("(.*(slc|prx|pry)).*", "$1") + "]";
//			else if (this.getImagePlus().getStack() instanceof MultiFileInfoVirtualStack) {
//				MultiFileInfoVirtualStack mfivs = ((MultiFileInfoVirtualStack)this.getImagePlus().getStack());
//				channelLabel = " ["+ mfivs.getVirtualStack(mfivs.stackNumber) + "]";
//			}
			s += "c"+imp.getChannel()+"/"+channels;   // + channelLabel ;
			if (slices>1||frames>1) s += "; ";
		}
		if (slices>1) {
			s += "z"+imp.getSlice()+"/"+slices;
			if (frames>1) s += "; ";
		}
		if (frames>1)
			s += "t"+imp.getFrame()+"/"+frames;
		if (running2 || running3) return s;
		int index = subtitle.indexOf(";");
		if (index!=-1) {
			int index2 = subtitle.indexOf("(");
			if (index2>=0 && index2<index && subtitle.length()>index2+4 && !subtitle.substring(index2+1, index2+4).equals("ch:")) {
				index = index2;
				s = s + " ";
			}
			subtitle = subtitle.substring(index, subtitle.length());
		} else
			subtitle = "";
    	return (s + subtitle).replace(" ", longPath?" ":"");
    }
    
    public boolean isHyperStack() {
    	return hyperStack;
    }
    
    public void setPosition(int channel, int slice, int trueFrame) {
    	int frame = trueFrame;
    	MultiChannelController mcc = imp.getMultiChannelController();
    	if (mcc!=null && mcc.getDropFramesFieldText(channel-1)!=null) {
    		String[] dropFramesChunks = mcc.getDropFramesFieldText(channel-1).split(",");
    		ArrayList<String> dropFramesChunksArrayList = new ArrayList<String>();
    		for (String dropFramesChunk:dropFramesChunks)
    			dropFramesChunksArrayList.add(dropFramesChunk.trim());
    		while (dropFramesChunksArrayList.contains(""+frame)) {
    			frame--;
    		}
    	}
    	if (cSelector!=null /*&& channel!=c*/) {
    		c = channel;
			cSelector.setValue(channel);
			SyncWindows.setC(this, channel);
		}
    	if (zSelector!=null /*&& slice!=z*/) {
    		z = slice;
			zSelector.setValue(slice);
			SyncWindows.setZ(this, slice);
		}
    	if (tSelector!=null /*&& frame!=t*/) {
    		t = frame;
			tSelector.setValue(trueFrame);
			SyncWindows.setT(this, trueFrame);
		}
    	updatePosition();
		if (this.slice>0) {
			int s = this.slice;
			this.slice = 0;
//			if (s!=imp.getCurrentSlice())
			if (imp instanceof CompositeImage)
				((CompositeImage)imp).setSlice(s);
			else
				imp.setSlice(s);
		}
		t = trueFrame;
		imp.updatePosition(c, z, t);
		subTitleField.setText(createSubtitle(false));
		subTitleField.setToolTipText(createSubtitle(true));

    }
    
    public void setPositionWithoutScrollbarCheck(int channel, int slice, int frame) {
//    	if (cSelector!=null && channel!=c) {
//    		c = channel;
//			cSelector.setValue(channel);
//			SyncWindows.setC(this, channel);
//		}
//    	if (zSelector!=null && slice!=z) {
//    		z = slice;
//			zSelector.setValue(slice);
//			SyncWindows.setZ(this, slice);
//		}
//    	if (tSelector!=null && frame!=t) {
//    		t = frame;
//			tSelector.setValue(frame);
//			SyncWindows.setT(this, frame);
//		}
    	updatePosition();
		if (this.slice>0) {
			int s = this.slice;
			this.slice = 0;
//			if (s!=imp.getCurrentSlice())
				imp.setSlice(s);
		}
    }
    
    public boolean validDimensions() {
    	int c = imp.getNChannels();
    	int z = imp.getNSlices();
    	int t = imp.getNFrames();
    	if (c!=nChannels||z!=nSlices||t!=nFrames||c*z*t!=imp.getStackSize())
    		return false;
    	else
    		return true;
    }
    
    public void setAnimate(boolean b) {
    	if (running2!=b && getAnimationSelector()!=null)
    		getAnimationSelector().updatePlayPauseIcon();
		running2 = b;
    }
    
    public void setZAnimate(boolean b) {
    	if (running3!=b && getAnimationZSelector()!=null)
    		getAnimationZSelector().updatePlayPauseIcon();
		running3 = b;
    }

    
    public boolean getAnimate() {
    	return running2;
    }
    
    public boolean getZAnimate() {
    	return running3;
    }
    
    public int getNScrollbars() {
    	int n = 0;
    	if (cSelector!=null) n++;
    	if (zSelector!=null) n++;
    	if (tSelector!=null) n++;
    	return n;
    }
    
    void removeScrollbars() {
//    	if (cSelector!=null) {
//    		scrollbarPanel.remove(cSelector);
//			cSelector.removeAdjustmentListener(this);
//    		cSelector = null;
//    	}
//    	if (zSelector!=null) {
//    		scrollbarPanel.remove(zSelector);
//			zSelector.removeAdjustmentListener(this);
//    		zSelector = null;
//    	}
//    	if (tSelector!=null) {
//    		scrollbarPanel.remove(tSelector);
//			tSelector.removeAdjustmentListener(this);
//    		tSelector = null;
//    	}
		if (cSelector!=null) {
			cSelector.bar.removeAdjustmentListener(cSelector);
			cSelector.bar.removeMouseListener(cSelector);
			cSelector.removeAdjustmentListener(this);
			cSelector.removeKeyListener(ij);
			while (cSelector.getMouseListeners().length>0) 
				cSelector.removeMouseListener(cSelector.getMouseListeners()[0]);
			while (cSelector.iconPanel.getMouseListeners().length>0) 
				cSelector.iconPanel.removeMouseListener(cSelector.iconPanel.getMouseListeners()[0]);
			while (cSelector.icon2Panel.getMouseListeners().length>0) 
				cSelector.icon2Panel.removeMouseListener(cSelector.icon2Panel.getMouseListeners()[0]);
			ActionListener[] csipals=cSelector.iconPanel.getActionListeners();
			while (csipals.length>0) { 
				cSelector.iconPanel.removeActionListener(csipals[0]);		
				csipals=cSelector.iconPanel.getActionListeners();
			}
			ActionListener[] csipals2=cSelector.icon2Panel.getActionListeners();;
			while (csipals2.length>0) {
				cSelector.icon2Panel.removeActionListener(csipals2[0]);	
				csipals2=cSelector.icon2Panel.getActionListeners();
			}
			
			cSelector.icon=null;
			cSelector.icon2=null;
			cSelector.iconPanel=null;
			cSelector.icon2Panel=null;
			cSelector.bar = null;
			cSelector.stackWindow = null;

			cSelector.adjustmentListener=null;
			cSelector.stackWindow = null;
			cSelector=null;
		}
		if (tSelector!=null) {
			tSelector.bar.removeAdjustmentListener(tSelector);
			tSelector.bar.removeMouseListener(tSelector);
			tSelector.removeAdjustmentListener(this);
			tSelector.removeKeyListener(ij);
			while (tSelector.getMouseListeners().length>0) 
				tSelector.removeMouseListener(tSelector.getMouseListeners()[0]);
			if (tSelector.iconPanel!=null) {
				while (tSelector.iconPanel.getMouseListeners().length>0) 
					tSelector.iconPanel.removeMouseListener(tSelector.iconPanel.getMouseListeners()[0]);
				while (tSelector.icon2Panel.getMouseListeners().length>0) 
					tSelector.icon2Panel.removeMouseListener(tSelector.icon2Panel.getMouseListeners()[0]);
				ActionListener[] tsipals=tSelector.iconPanel.getActionListeners();
				while (tsipals.length>0) {
					tSelector.iconPanel.removeActionListener(tsipals[0]);			
					tsipals=tSelector.iconPanel.getActionListeners();
				}
				ActionListener[] tsipals2=tSelector.icon2Panel.getActionListeners();;
				while (tsipals2.length>0) {
					tSelector.icon2Panel.removeActionListener(tsipals2[0]);	
					tsipals2=tSelector.icon2Panel.getActionListeners();
				}
				tSelector.iconPanel.setAction(null);
				tSelector.icon2Panel.setAction(null);
				tSelector.icon=null;
				tSelector.icon2=null;
				tSelector.iconPanel=null;
				tSelector.icon2Panel=null;
			}
			tSelector.bar = null;
			tSelector.stackWindow = null;

			tSelector.adjustmentListener=null;
			tSelector.stackWindow = null;
			tSelector=null;
		}
		if (zSelector!=null) {
			zSelector.bar.removeAdjustmentListener(zSelector);
			zSelector.bar.removeMouseListener(zSelector);
			zSelector.removeAdjustmentListener(this);
			zSelector.removeKeyListener(ij);
			while (zSelector.getMouseListeners().length>0) 
				zSelector.removeMouseListener(zSelector.getMouseListeners()[0]);
			if (zSelector.iconPanel!=null) {
				while (zSelector.iconPanel.getMouseListeners().length>0) 
					zSelector.iconPanel.removeMouseListener(zSelector.iconPanel.getMouseListeners()[0]);
				while (zSelector.icon2Panel.getMouseListeners().length>0) 
					zSelector.icon2Panel.removeMouseListener(zSelector.icon2Panel.getMouseListeners()[0]);
				ActionListener[] zsipals=zSelector.iconPanel.getActionListeners();
				while (zsipals.length>0) {
					zSelector.iconPanel.removeActionListener(zsipals[0]);		
					zsipals=zSelector.iconPanel.getActionListeners();
				}
				ActionListener[] zasipals2=zSelector.icon2Panel.getActionListeners();
				while (zasipals2.length>0) {
					zSelector.icon2Panel.removeActionListener(zasipals2[0]);	
					zasipals2=zSelector.icon2Panel.getActionListeners();
				}
				zSelector.iconPanel.setAction(null);
				zSelector.icon2Panel.setAction(null);
				zSelector.icon=null;
				zSelector.icon2=null;
				zSelector.iconPanel=null;
				zSelector.icon2Panel=null;
			}
			zSelector.bar = null;
			zSelector.stackWindow = null;

			zSelector.adjustmentListener=null;
			zSelector.stackWindow = null;
			zSelector=null;
		}
		if (activeScrollBars!=null) {
			int asbs = activeScrollBars.size();
			for (int asbl=0;asbl<asbs;asbl++) {
				activeScrollBars.remove(0);
			}
		}
//    	remove(scrollbarPanel);
    	scrollbarPanel = null;
    }

	public ScrollbarWithLabel getAnimationSelector() {
//		if (animationSelector != null) 
//			return animationSelector;
//		else
			return tSelector != null? tSelector:(zSelector != null? zSelector:null);
	}

	public ScrollbarWithLabel getAnimationZSelector() {
//		if (animationZSelector != null) 
//			return animationZSelector;
//		else
			return zSelector != null? zSelector:(tSelector != null? tSelector:null);
	}

	public void setWormAtlas(boolean wormAtlas) {
		this.wormAtlas = wormAtlas;
	}

	public boolean isWormAtlas() {
		return wormAtlas;
	}

}
