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
import javax.swing.JScrollBar;
import javax.swing.ToolTipManager;

import org.vcell.gloworm.MultiQTVirtualStack;

/** This class is an extended ImageWindow used to display image stacks. */
public class StackWindow extends ImageWindow implements Runnable, AdjustmentListener, ActionListener, MouseWheelListener {

	protected Scrollbar sliceSelector; // for backward compatibity with Image5D
	public ScrollbarWithLabel cSelector, zSelector, tSelector;
	public ArrayList<ScrollbarWithLabel> activeScrollBars = new ArrayList<ScrollbarWithLabel>();
	protected Thread thread;
	protected volatile boolean done;
	protected volatile int slice;
	private ScrollbarWithLabel animationSelector, animationZSelector;
	boolean hyperStack;
	int nChannels=1, nSlices=1, nFrames=1;
	int c=1, z=1, t=1;
	boolean wormAtlas;
	Panel scrollbarPanel;
	

	public StackWindow(ImagePlus imp, boolean showNow) {
		this(imp, null, showNow);
	}
    
    public StackWindow(ImagePlus imp, ImageCanvas ic, boolean showNow) {
		super(imp, ic);
		addScrollbars(imp);
		addMouseWheelListener(this);
		if (sliceSelector==null && this.getClass().getName().indexOf("Image5D")!=-1) {
			sliceSelector = new Scrollbar(); // prevents Image5D from crashing
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
		thread = new Thread(this, "zSelector");
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
		hyperStack = imp.getOpenAsHyperStack();
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

		scrollbarPanel = new Panel();
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
			if (ij!=null) cSelector.addKeyListener(ij);
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

			if (label=='t') animationSelector = zSelector;
			if (label=='z') {animationZSelector = zSelector; setZAnimate(false); }
			sbgc.gridy = y++; 
			scrollbarPanel.add(zSelector, sbgc);
			if (ij!=null) zSelector.addKeyListener(ij);
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
			animationSelector = tSelector = new ScrollbarWithLabel(this, 1, 1, 1, nFrames+1, 't');
			((JComponent) tSelector.iconPanel).setToolTipText("<html>Left-Clicking this Frame selector's icon <br>plays/pauses animation through the T dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
			((JComponent) tSelector.icon2Panel).setToolTipText("<html>Left-Clicking this Frame selector's icon <br>plays/pauses animation through the T dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
			if (wormAtlas) {
				animationSelector = tSelector = new ScrollbarWithLabel(this, 1, 1, 1, nFrames+1, 'z');
				((JComponent) tSelector.iconPanel).setToolTipText("<html>Left-Clicking this Slice selector's icon <br>plays/pauses animation through the Z dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
				((JComponent) tSelector.icon2Panel).setToolTipText("<html>Left-Clicking this Slice selector's icon <br>plays/pauses animation through the Z dimension.<br>Right-clicking or control-clicking <br>activates the Animation Options Tool.</html>");		
			}
			sbgc.gridy = y++; 
			scrollbarPanel.add(tSelector, sbgc);
			if (ij!=null) tSelector.addKeyListener(ij);
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
				t = tSelector.getValue();
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
		if (!super.close())
			return false;
		synchronized(this) {
			done = true;
			notify();
		}
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
				if (imp!=null || s!=imp.getCurrentSlice() && !Orthogonal_Views.isOrthoViewsImage(imp)) {
					imp.setSlice(s);
				}
			}
		}
	}
	
	public String createSubtitle() {
		String subtitle = super.createSubtitle();
		if (!hyperStack) return subtitle;
    	String s="";
    	int[] dim = imp.getDimensions(false);
    	int channels=dim[2], slices=dim[3], frames=dim[4];
		if (channels>=1) {
			String channelLabel ="";
			if( this.getImagePlus().getStack() instanceof MultiQTVirtualStack && ((MultiQTVirtualStack) imp.getMotherImp().getStack()).getVirtualStack(0) != null)
				channelLabel = " [" + ((MultiQTVirtualStack)this.getImagePlus().getStack()).getVirtualStack(imp.getChannel()-1).getMovieName() + "]";
			else if (this.getImagePlus().getRemoteMQTVSHandler()!=null)
				channelLabel = " [" + this.getImagePlus().getRemoteMQTVSHandler().getChannelPathNames()[imp.getChannel()-1].replaceAll(".*/", "").replaceAll("(.*(slc|prx|pry)).*", "$1") + "]";
			else if (this.getImagePlus().getStack() instanceof MultiFileInfoVirtualStack) {
				MultiFileInfoVirtualStack mfivs = ((MultiFileInfoVirtualStack)this.getImagePlus().getStack());
				channelLabel = " ["+ mfivs.getVirtualStack(mfivs.stackNumber) + "]";
			}
			s += "c:"+imp.getChannel()+"/"+channels + channelLabel ;
			if (slices>1||frames>1) s += " ";
		}
		if (slices>1) {
			s += "z:"+imp.getSlice()+"/"+slices;
			if (frames>1) s += " ";
		}
		if (frames>1)
			s += "t:"+imp.getFrame()+"/"+frames;
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
    	return s + subtitle;
    }
    
    public boolean isHyperStack() {
    	return hyperStack;
    }
    
    public void setPosition(int channel, int slice, int frame) {
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
			tSelector.setValue(frame);
			SyncWindows.setT(this, frame);
		}
    	updatePosition();
		if (this.slice>0) {
			int s = this.slice;
			this.slice = 0;
//			if (s!=imp.getCurrentSlice())
				imp.setSlice(s);
		}
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
    	if (running2!=b && animationSelector!=null)
    		animationSelector.updatePlayPauseIcon();
		running2 = b;
    }
    
    public void setZAnimate(boolean b) {
    	if (running3!=b && animationZSelector!=null)
    		animationZSelector.updatePlayPauseIcon();
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
    	remove(scrollbarPanel);
    	if (cSelector!=null) {
    		remove(cSelector);
			cSelector.removeAdjustmentListener(this);
    		cSelector = null;
    	}
    	if (zSelector!=null) {
    		remove(zSelector);
			zSelector.removeAdjustmentListener(this);
    		zSelector = null;
    	}
    	if (tSelector!=null) {
    		remove(tSelector);
			tSelector.removeAdjustmentListener(this);
    		tSelector = null;
    	}
    }

	public ScrollbarWithLabel getAnimationSelector() {
		if (animationSelector != null) 
			return animationSelector;
		else
			return tSelector != null? tSelector:(animationZSelector != null?animationZSelector:(zSelector != null? zSelector:null));
	}

	public ScrollbarWithLabel getAnimationZSelector() {
		if (animationZSelector != null) 
			return animationZSelector;
		else
			return zSelector != null? zSelector:(animationSelector != null?animationSelector:(tSelector != null? tSelector:null));
	}

	public void setWormAtlas(boolean wormAtlas) {
		this.wormAtlas = wormAtlas;
	}

	public boolean isWormAtlas() {
		return wormAtlas;
	}

}
