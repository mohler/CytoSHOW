package ij.plugin;
import java.awt.Frame;
import java.awt.event.KeyEvent;

import SmartCaptureLite.Composite_Adjuster;
import ij.*;
import ij.gui.*;
import ij.plugin.frame.ContrastAdjuster;
import ij.plugin.frame.PlugInFrame;
import ij.process.*;
import ij.measure.Calibration;


/** 091310 CODE MODIFIED TO BROWSE SLICES WITH UP/DOWN AND FRAMES WITH LEFT/RIGHT KEYS */



/** This plugin animates stacks. */
public class Animator implements PlugIn {

	private static double animationRate = 10;
	private static int firstFrame=0, lastFrame=0;
	private static int firstSlice=0, lastSlice=0;
	private ImagePlus imp;
	private StackWindow swin;
	private int slice;
	private int nSlices;	
	private boolean wasTAnimating;

	/** Set 'arg' to "set" to display a dialog that allows the user to specify the
		animation speed. Set it to "start" to start animating the current stack.
		Set it to "stop" to stop animation. Set it to "next" or "previous"
		to stop any animation and display the next or previous frame. 
	*/
	public void run(String arg) {
		Frame[] niw = WindowManager.getNonImageWindows();		
		for (int m=0; m < niw.length; m++) {
			if (niw[m].getTitle().contains("Composite Adjuster")) {
				niw[m].toFront();
				IJ.showMessage("Close Composite Adjuster when animating", 
						"Please close Composite Adjuster window and \n toggle the Channel selector before starting animation.");				
				return;
			}
		}

		imp = WindowManager.getCurrentImage();
		if (imp==null)
			{IJ.noImage(); return;}
    	nSlices = imp.getStackSize();
		if (nSlices<2)
			{IJ.error("Animator...", "Stack required."); return;}
		ImageWindow win = imp.getWindow();
		if (win==null || !(win instanceof StackWindow)) {
			if (arg.equals("next"))
				imp.setSlice(imp.getCurrentSlice()+1);
			else if (arg.equals("previous"))
				imp.setSlice(imp.getCurrentSlice()-1);
			if (win!=null) imp.updateStatusbarValue();
			return;
		}
		swin = (StackWindow)win;
		ImageStack stack = imp.getStack();
		slice = imp.getCurrentSlice();
		IJ.register(Animator.class);
		
		if (arg.equals("options")) {
			doOptions();
			return;
		}
			
/***** DO I WANT THIS STOPPING TO HAPPEN IN GLOWORMJ? *************/
		
/*		if (swin.getAnimate()) // "stop", "next" and "previous" all stop animation
			stopAnimation();

		if (arg.equals("stop")) {
			return;
		}
*/
			
		if (arg.equals("start")) {
			if (swin.getAnimationSelector() != null)
				startAnimation();
			else if (swin.getAnimationZSelector() != null)
				startZAnimation();
			return;
		}
		
		if (arg.equals("startZ")) {
			if (swin.getAnimationZSelector() != null)
				startZAnimation();
			return;
		}


/***********THIS IS CODE THAT I MODIFIED BEFORE WAYNE MADE THE CHANGE JUST ABOVE.  DO I NEED IT NOW?*****/
		if (arg.equals("next")) {
			while (imp.getRemoteMQTVSHandler() != null 
					&& !imp.getRemoteMQTVSHandler().isReady()) {
				IJ.wait(100);
			}
			boolean a = (swin.getAnimate()); 
			if (a) 
				stopAnimation();
			nextSlice();

			if (a) 
				startAnimation();
			return;

		}

		if (arg.equals("thisSlice")) {
			while (imp.getRemoteMQTVSHandler() != null 
					&& !imp.getRemoteMQTVSHandler().isReady()) {
				IJ.wait(100);
			}
			boolean a = (swin.getAnimate()); 
			if (a) 
				stopAnimation();
			
			thisSlice();

			if (a) 
				startAnimation();
			return;

		}

		if (arg.equals("previous")) {
			while (imp.getRemoteMQTVSHandler() != null 
					&& !imp.getRemoteMQTVSHandler().isReady()) {
				IJ.wait(100);
			}
			boolean a = (swin.getAnimate()); 
			if (a) 
				stopAnimation();
			previousSlice();

			if (a) 
				startAnimation();
			return;

		}
		
		
		if (arg.equals("stop")) { // "stop" stops animation
			stopAnimation();
			stopZAnimation();
			return;
		}
			
		
		if (arg.equals("set")) {
			setSlice();
			return;
		}
	}

	void stopAnimation() {
		if (swin.getAnimationSelector() != null) 
			swin.getAnimationSelector().repaint();
		swin.setAnimate(false);
		IJ.wait(500+(int)(1000.0/animationRate));
		swin.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
		imp.updateStatusbarValue();
		imp.unlock(); 
		if (swin.getAnimationSelector() != null) 
			swin.getAnimationSelector().updatePlayPauseIcon();

	}

	void startAnimation() {
		int first=firstFrame, last=lastFrame;
		if (first<1 || first>nSlices || last<1 || last>nSlices)
			{first=1; last=nSlices;}
		if (swin.getAnimate())
			{stopAnimation(); return;}
		imp.unlock(); // so users can adjust brightness/contrast/threshold
		swin.setAnimate(true);
		long time, nextTime=System.currentTimeMillis();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		int sliceIncrement = 1;
		Calibration cal = imp.getCalibration();
		if (cal.fps!=0.0)
			animationRate = cal.fps;
		if (animationRate<0.1)
			animationRate = 1.0;
		int frames = imp.getNFrames();
		int slices = imp.getNSlices();
		
		//CASE OF A SPECIFIED T DIMENSION
		if (imp.isDisplayedHyperStack() && frames>1) {
			int frame = imp.getFrame();
			first = 1;
			last = frames;
			while (swin.getAnimate()) {
				if (swin.getZAnimate()) {
					frame = imp.getFrame();
					continue;
				}
				time = System.currentTimeMillis();
				if (time<nextTime)
					IJ.wait((int)(nextTime-time));
				else
					Thread.yield();
				nextTime += (long)(1000.0/animationRate);
				frame += sliceIncrement;
				if (frame<first) {
					frame = first+1;
					sliceIncrement = 1;
				}
				if (frame>last) {
					if (cal.loop) {
						frame = last-1;
						sliceIncrement = -1;
					} else {
						frame = first;
						sliceIncrement = 1;
					}
				}
				imp.setPosition(imp.getChannel(), imp.getSlice(), frame);
				swin.getAnimationSelector().updatePlayPauseIcon();
				swin.getAnimationZSelector().updatePlayPauseIcon();

			}
			return;
		}

		//CASE WITHOUT A SPECIFIED T DIMENSION, BUT WITH A SPECIFIED Z DIMENSION
		if (imp.isDisplayedHyperStack() && slices>1) {
			slice = imp.getSlice();
			first = 1;
			last = slices;
			while (swin.getAnimate()) {
				if (swin.getZAnimate()) {
					slice = imp.getFrame();
					continue;
				}
				time = System.currentTimeMillis();
				if (time<nextTime)
					IJ.wait((int)(nextTime-time));
				else
					Thread.yield();
				nextTime += (long)(1000.0/animationRate);
				slice += sliceIncrement;
				if (slice<first) {
					slice = first+1;
					sliceIncrement = 1;
				}
				if (slice>last) {
					if (cal.loop) {
						slice = last-1;
						sliceIncrement = -1;
					} else {
						slice = first;
						sliceIncrement = 1;
					}
				}
				imp.setPosition(imp.getChannel(), slice, imp.getFrame());
				swin.getAnimationSelector().updatePlayPauseIcon();
				swin.getAnimationZSelector().updatePlayPauseIcon();

			}
			return;
		}
		
		//CASE WITHOUT A SPECIFIED T OR Z DIMENSION, NOT A HYPERSTACK
		long startTime=System.currentTimeMillis();
		int count = 0;
		double fps = 0.0;
		while (swin.getAnimate()) {
			time = System.currentTimeMillis();
			count++;
			if (time>startTime+1000L) {
				startTime=System.currentTimeMillis();
				fps=count;
				count=0;
			}
			IJ.showStatus((int)(fps+0.5) + " fps");
			if (time<nextTime)
				IJ.wait((int)(nextTime-time));
			else
				Thread.yield();
			nextTime += (long)(1000.0/animationRate);
			slice += sliceIncrement;
			if (slice<first) {
				slice = first+1;
				sliceIncrement = 1;
			}
			if (slice>last) {
				if (cal.loop) {
					slice = last-1;
					sliceIncrement = -1;
				} else {
					slice = first;
					sliceIncrement = 1;
				}
			}
			swin.showSlice(slice);
			swin.getAnimationSelector().updatePlayPauseIcon();
			swin.getAnimationZSelector().updatePlayPauseIcon();

		}
		
	}

	void doOptions() {
		if (firstFrame<1 || firstFrame>nSlices || lastFrame<1 || lastFrame>nSlices)
			{firstFrame=1; lastFrame=nSlices;}
		if (imp.isDisplayedHyperStack()) {
			int frames = imp.getNFrames();
			int slices = imp.getNSlices();
			firstFrame = 1;
			if (frames>1)
				lastFrame = frames;
			else if (slices>1)
				lastFrame=slices;
		}
		boolean start = !swin.getAnimate();
		Calibration cal = imp.getCalibration();
		if (cal.fps!=0.0)
			animationRate = cal.fps;
		else if (cal.frameInterval!=0.0 && cal.getTimeUnit().equals("sec"))
			animationRate = 1.0/cal.frameInterval;
		int decimalPlaces = (int)animationRate==animationRate?0:3;
		GenericDialog gd = new GenericDialog("Animation Options");
		gd.addNumericField("Speed (0.1-1000 fps):", animationRate, decimalPlaces);
		if (!imp.isDisplayedHyperStack()) {
			gd.addNumericField("First Frame:", firstFrame, 0);
			gd.addNumericField("Last Frame:", lastFrame, 0);
		}
		gd.addCheckbox("Loop Back and Forth", cal.loop);
		gd.addCheckbox("Start Animation", start);
		gd.showDialog();
		if (gd.wasCanceled()) {
			if (firstFrame==1 && lastFrame==nSlices)
				{firstFrame=0; lastFrame=0;}
			return;
		}
		double speed = gd.getNextNumber();
		if (!imp.isDisplayedHyperStack()) {
			firstFrame = (int)gd.getNextNumber();
			lastFrame = (int)gd.getNextNumber();
		}
		if (firstFrame==1 && lastFrame==nSlices)
			{firstFrame=0; lastFrame=0;}
		cal.loop = gd.getNextBoolean();
		Calibration.setLoopBackAndForth(cal.loop);
		start = gd.getNextBoolean();
		if (speed>1000.0) speed = 1000.0;
		//if (speed<0.1) speed = 0.1;
		animationRate = speed;
		if (animationRate!=0.0)
			cal.fps = animationRate;
		if (start && !swin.getAnimate())
			startAnimation();
	}
	
	void nextSlice() {
		if (!imp.lock())
			return;
		if (Orthogonal_Views.isOrthoViewsImage(imp)) {
			imp.setProcessor(Orthogonal_Views.getInstance().imageStack.getProcessor(imp.getSlice()+1));
			imp.zeroUpdateMode = true;
			imp.setPositionWithoutUpdate(imp.getChannel(), imp.getSlice()+1, imp.getFrame());
			//				imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
			imp.zeroUpdateMode = false;
			imp.updateStatusbarValue();
			imp.unlock();
			Orthogonal_Views.getInstance().update();
			return;
		}
		boolean hyperstack = imp.isDisplayedHyperStack();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		if (hyperstack && frames>1 && !((IJ.controlKeyDown()||IJ.spaceBarDown()||IJ.altKeyDown()))) {
			int t = imp.getFrame() + 1;
			if (t>frames) t = 1;
			swin.setPosition(imp.getChannel(), imp.getSlice(), t);
		} else if (hyperstack && slices>1 && !(IJ.spaceBarDown()||IJ.altKeyDown())) {
			int z = imp.getSlice() + 1;
			if (z>slices) z = 1;
			swin.setPosition(imp.getChannel(), z, imp.getFrame());
			IJ.setKeyUp(KeyEvent.VK_CONTROL);
		} else if (hyperstack && channels>1 && !IJ.spaceBarDown()) {
			int c = imp.getChannel() + 1;
			if (c>channels) c = 1;
			swin.setPosition(c, imp.getSlice(), imp.getFrame());
			IJ.setKeyUp(KeyEvent.VK_ALT);
		} else if (hyperstack) {
			int t = imp.getFrame() + frames/10;
			if (t>frames) t = 1;
			swin.setPosition(imp.getChannel(), imp.getSlice(), t);
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SPACE);
		
		} else {
			int t = imp.getCurrentSlice();
			if (IJ.spaceBarDown()&&IJ.altKeyDown()) {
				t = t + imp.getStackSize()/10;

			} else {
				t = t + 1;
			}
			if (t> imp.getStackSize()  ) t = 1;
			imp.setPosition(t);
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SPACE);
		}
		imp.updateStatusbarValue();
		imp.unlock();
	}
	
	void thisSlice() {
		if (!imp.lock())
			return;
		
//		ImageProcessor ip = imp.getProcessor();
//		if (imp.getRemoteMQTVSHandler() != null)
//			ip = imp.getStack().getProcessor(imp.getSlice());
//		ip.setMinAndMax(ip.getMin(), ip.getMax());
		swin.setPosition(imp.getChannel(), imp.getSlice(), imp.getFrame());
		imp.updateStatusbarValue();
		imp.unlock();
	}

	
	void previousSlice() {
		if (!imp.lock())
			return;
		if (Orthogonal_Views.isOrthoViewsImage(imp)) {
			imp.setProcessor(Orthogonal_Views.getInstance().imageStack.getProcessor(imp.getSlice()-1));
			imp.zeroUpdateMode = true;
			imp.setPositionWithoutUpdate(imp.getChannel(), imp.getSlice()-1, imp.getFrame());
			//				imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
			imp.zeroUpdateMode = false;
			imp.updateStatusbarValue();
			imp.unlock();
			Orthogonal_Views.getInstance().update();
			return;
		}
		boolean hyperstack = imp.isDisplayedHyperStack();
		int channels = imp.getNChannels();
		int slices = imp.getNSlices();
		int frames = imp.getNFrames();
		if (hyperstack && frames>1 && !((IJ.controlKeyDown()||IJ.spaceBarDown()||IJ.altKeyDown()))) {
			int t = imp.getFrame() - 1;
			if (t<1) t = frames;
			swin.setPosition(imp.getChannel(), imp.getSlice(), t);
		} else if (hyperstack && slices>1 && !(IJ.spaceBarDown()||IJ.altKeyDown())) {
			int z = imp.getSlice() -1;
			if (z<1) z = slices;
			swin.setPosition(imp.getChannel(), z, imp.getFrame());
			IJ.setKeyUp(KeyEvent.VK_CONTROL);
		} else if (hyperstack && channels>1 && (!IJ.spaceBarDown())) {
			int c = imp.getChannel() -1 ;
			if (c<1) c = channels;
			swin.setPosition(c, imp.getSlice(), imp.getFrame());
			IJ.setKeyUp(KeyEvent.VK_ALT);
		} else if (hyperstack) {
			int t = imp.getFrame() - frames/10;
			if (t<1) t = frames;
			swin.setPosition(imp.getChannel(), imp.getSlice(), t);
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SPACE);
 
		} else {
			int t = imp.getCurrentSlice();
			if (IJ.spaceBarDown()&&IJ.altKeyDown()) {
				t = t - imp.getStackSize()/10;

			} else {
				t = t - 1;
			}
			if (t< 1  ) t = imp.getStackSize();
			imp.setPosition(t);
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SPACE);
		}
		imp.updateStatusbarValue();
		imp.unlock();
	}

	void setSlice() {
        GenericDialog gd = new GenericDialog("Set Slice");
        gd.addNumericField("Slice Number (1-"+nSlices+"):", slice, 0);
        gd.showDialog();
        if (!gd.wasCanceled()) {
        	int n = (int)gd.getNextNumber();
        	if (imp.isDisplayedHyperStack())
        		imp.setPosition(n);
        	else
        		imp.setSlice(n);
        }
	}

	/** Returns the current animation speed in frames per second. */
	public static double getFrameRate() {
		return animationRate;
	}

	
	void stopZAnimation() {
		if (swin.getAnimationZSelector() != null) 
			swin.getAnimationZSelector().repaint();

		swin.setZAnimate(false);
		IJ.wait(500+(int)(1000.0/animationRate));
		imp.unlock(); 
		if (swin.getAnimationZSelector() != null) {
			swin.getAnimationSelector().updatePlayPauseIcon();
			swin.getAnimationZSelector().updatePlayPauseIcon();
		}

	}

	void startZAnimation() {
		int firstZ=firstSlice, lastZ=lastSlice;
		int firstT=firstFrame, lastT=lastFrame;
		if (firstZ<1 || firstZ>nSlices || lastZ<1 || lastZ>nSlices)
			{firstZ=1; lastZ=nSlices;}
		if (firstT<1 || firstT>nSlices || lastT<1 || lastT>nSlices)
			{firstT=1; lastT=nSlices;}
		
		wasTAnimating = swin.getAnimate();

		if (swin.getZAnimate()){
			if (wasTAnimating) {
				//IJ.log("wasTAnimating1 " + wasTAnimating);
				stopAnimation();  //stops T
			}
			stopZAnimation(); 
			if (wasTAnimating && !swin.getAnimate()){
				//IJ.log("wasTAnimating2 " + wasTAnimating);
				wasTAnimating = false;
				startAnimation();  //restarts T
			}
			return;
		}

		if (wasTAnimating) {
			//IJ.log("wasTAnimating3 " + wasTAnimating);
			stopAnimation();  //stops T
		}
		imp.unlock(); // so users can adjust brightness/contrast/threshold
		swin.setZAnimate(true);
		long time, nextTime=System.currentTimeMillis();
		Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
		int sliceIncrement = 1;
		int frameIncrement = 1;
		Calibration cal = imp.getCalibration();
		if (cal.fps!=0.0)
			animationRate = cal.fps;
		if (animationRate<0.1)
			animationRate = 1.0;
		int frames = imp.getNFrames();
		int slices = imp.getNSlices();
		
		if (imp.isDisplayedHyperStack() && slices>1) {
			int slice = imp.getSlice();
			int frame = imp.getFrame();
			firstZ = 1;
			lastZ = slices;
			firstT = 1;
			lastT = frames;
			while (swin.getZAnimate()) {
				time = System.currentTimeMillis();
				if (time<nextTime)
					IJ.wait((int)(nextTime-time));
				else
					Thread.yield();
				nextTime += (long)(1000.0/animationRate);
				slice += sliceIncrement;
				if (slice<firstZ) {
					slice = firstZ+1;
					sliceIncrement = 1;
				}
				if (slice>lastZ) {
					if (cal.loop) {
						slice = lastZ-1;
						sliceIncrement = -1;
					} else {
						slice = firstZ;
						sliceIncrement = 1;
					}
				}
				if (swin.getAnimate() ) {
					frame += frameIncrement;
					if (frame < firstT) {
						frame = firstT + 1;
						frameIncrement = 1;
					}
					if (frame > lastT) {
						if (cal.loop) {
							frame = lastT - 1;
							frameIncrement = -1;
						} else {
							frame = firstT;
							frameIncrement = 1;
						}
					}
				}
				if (Orthogonal_Views.isOrthoViewsImage(imp)) {
					imp.setProcessor(Orthogonal_Views.getInstance().imageStack.getProcessor(slice));
					imp.zeroUpdateMode = true;
					imp.setPositionWithoutUpdate(imp.getChannel(), slice, imp.getFrame());
					//				imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());
					imp.zeroUpdateMode = false;
					Orthogonal_Views.getInstance().update();
				} else
					imp.setPosition(imp.getChannel(), slice, frame);
				if (wasTAnimating && !swin.getAnimate()){
					//IJ.log("wasTAnimating4 " + wasTAnimating);
					wasTAnimating = false;
					swin.setAnimate(true);
				}
				//IJ.log("ZAnimating...");
				swin.getAnimationSelector().updatePlayPauseIcon();
				swin.getAnimationZSelector().updatePlayPauseIcon();

			}
			return;
		}

		//CASE WITHOUT A SPECIFIED T OR Z DIMENSION, NOT A HYPERSTACK
		long startTime=System.currentTimeMillis();
		int count = 0;
		double fps = 0.0;
		while (swin.getZAnimate()) {
			time = System.currentTimeMillis();
			count++;
			if (time>startTime+1000L) {
				startTime=System.currentTimeMillis();
				fps=count;
				count=0;
			}
			IJ.showStatus((int)(fps+0.5) + " fps");
			if (time<nextTime)
				IJ.wait((int)(nextTime-time));
			else
				Thread.yield();
			nextTime += (long)(1000.0/animationRate);
			slice += sliceIncrement;
			if (slice<firstZ) {
				slice = firstZ+1;
				sliceIncrement = 1;
			}
			if (slice>lastZ) {
				if (cal.loop) {
					slice = lastZ-1;
					sliceIncrement = -1;
				} else {
					slice = firstZ;
					sliceIncrement = 1;
				}
			}
			swin.showSlice(slice);
			swin.getAnimationSelector().updatePlayPauseIcon();
			swin.getAnimationZSelector().updatePlayPauseIcon();
		}
		
	}

	
	
}

