package gloworm;

import ij.*;
import ij.plugin.*;
import ij.process.*;
import ij.io.*;
import ij.gui.*;
import ij.text.*;
import ij.ImagePlus;
import ij.plugin.frame.*;
import ij.macro.*;
import ij.macro.Functions.*;

import java.awt.*;
import java.awt.image.ColorModel;
import java.io.*;
import java.util.*;
import java.awt.event.*;
import java.lang.reflect.*;

import javax.swing.SwingUtilities;

public class Structure_Autoselector implements PlugIn {
    /*Does what describeThisPlugin() says it does--see below.  Output
      is QuickTime movies, two per timepoint (one for cropped and
      oriented volumes, one for projections) at one frame per slice.*/

    protected Vector
	structures=new Vector(), //the user's selections
	usersOrders=new Vector(); /*User input to optionsBox.
				    Contents:
				    --0: whether options dialog was canceled (Boolean)
				    --1: desired series range start (Integer)
				    --2: desired series range end (Integer)
				    --3: auto vs. manual outline (Boolean)
				    --4: whether user wants 1st outline saved (Boolean)
				    --5: noise filtering method for raw data (Integer)
				    --6: # pixels between stack slices (Integer)
				    --7: whether user wants to interpolate projection between slices (Boolean)
				    --8: whether user wants to use *all* same outlines as last run (Boolean)
				    --9: min brightness
				    --10: max brightness
				    ...watch this space...*/
    public Choice
	selectionMethod;
    public Checkbox
	saveOutline,
	redoLastRun;
    public Button
	zoomButton;
    public TextField
	sliceSpacing;
    //widgets have to be declared public so options dialog can use them
    protected ImagePlus
	selectionImage=null; //the image the user will make selections on
    protected StackSeriesIterator
	stacks;
    protected ColorModel
	desiredColorModel;
    protected double
	desiredBrightnessMin,
	desiredBrightnessMax; //take effect after 8-bit conversion
    protected final String structureName="object";
    //what the directions will call what's being selected.  FIXME: use method instead
    protected String
	outputDir;
    protected BufferedReader
	savedDataContents=null;
    //Reader holding contents of this stack series' .data file
    private final int[]
	/*Where in saved data file various data items are, 1-based:
	  {line_number, first_token_inclusive, last_token_inclusive}
          (No need for plugins to use static constants...)*/
	FILETYPE_INT__F={1, 1, 1}, //this isn't actually used...yet
	RANGE_INTS__F={1, 2, 3},
	SAVE_OUTLINE_FLAG__F={1, 4, 4},
	PRE_OUTLINED_FLAG__F={1, 5, 5},
	AUTO_OUTLINE_FLAG__F={1, 6, 6},
	NOISE_FILTER_INT__F={1, 7, 7},
	INTERPOLATION_FLAG__F={1, 8, 8},
	SLICE_SPACING_DOUBLE__F={1, 9, 9},
	MIN_BRIGHTNESS_DOUBLE__F={1, 10, 10},
	MAX_BRIGHTNESS_DOUBLE__F={1, 11, 11};
    protected final int
	//array indices of important data:
	LINE=0,
	FIRST_TOKEN=1,
	LAST_TOKEN=2;
    private static final int
	//meanings of integer options:
	AUTO=0,
	MANUAL=1,
	NO_FILTER=0,
	GAUSSIAN_FILTER=1,
	DESPECKLE_FILTER=2,
	DUAL_FILTER=3,
	//indices of options in usersOrders:
	CANCELLED_FLAG__O=0,
	RANGE_START_INT__O=1,
	RANGE_END_INT__O=2,
	AUTO_OUTLINE_FLAG__O=3,
	SAVE_OUTLINE_FLAG__O=4,
	NOISE_FILTER_INT__O=5,
	SLICE_SPACING_DOUBLE__O=6,
	INTERPOLATION_FLAG__O=7,
	REDO_FLAG__O=8,
	MIN_BRIGHTNESS_DOUBLE__O=9,
	MAX_BRIGHTNESS_DOUBLE__O=10; //take effect before 8-bit conversion & control what data is lost
    protected static final int
	LAST_OPTION_INDEX=10; //let subclasses know the rest are free
    protected int savedDataLength=0; //length of .data file in bytes
    private Runnable eventQueueLock=new Runnable() { public void run() {} }; //for use by waitOnEventQueue()
    public void actionPerformed(ActionEvent e) {
	if (e.getSource()==zoomButton) {
	    Container parentFrame=zoomButton.getParent();
	    while (!(parentFrame instanceof Frame))
		parentFrame=parentFrame.getParent();
	    GenericDialog zoomBox=new GenericDialog("Zoom Correction", (Frame) parentFrame);
	    zoomBox.addMessage("Enter the magnification of the objective\nlens used to create these stacks.");
	    zoomBox.addNumericField("", 40, 0);
	    zoomBox.showDialog();
	    if (!zoomBox.wasCanceled()) {
		((StackSeriesIterator.ZoomCorrectable) stacks).correctZoom((int) Math.round(zoomBox.getNextNumber()));
		sliceSpacing.setText(stacks.getSliceSpacing()+"");
	    }
	}
    }
    private void activateSlice(ImagePlus imp, int whichSlice) {
//  	if (whichSlice >= 1 && whichSlice <= imp.getStackSize())
//  	    imp.setSlice(whichSlice);
//  	else {
//  	    Throwable t=new Throwable("slice index " + whichSlice + " out of range 1-" + imp.getStackSize());
//  	    t.printStackTrace();
//  	}
	synchronized(imp.getWindow()) {
	    ((StackWindow) imp.getWindow()).showSlice(whichSlice);
	}
    }
    private boolean defaultInterpolation(BufferedReader savedDataContents)
	throws IOException {
	if (savedDataContents != null)
	    return Boolean.valueOf(readSavedParameter(savedDataContents,
						      INTERPOLATION_FLAG__F[LINE],
						      INTERPOLATION_FLAG__F[FIRST_TOKEN])).booleanValue();
	else
	    return true;
    }
    private int defaultNoiseFilter(BufferedReader savedDataContents)
	throws IOException {
	if (savedDataContents != null)
	    return Integer.parseInt(readSavedParameter(savedDataContents,
						       NOISE_FILTER_INT__F[LINE],
						       NOISE_FILTER_INT__F[FIRST_TOKEN]));
	else
	    return NO_FILTER;
    }
    private boolean defaultSaveOutline(BufferedReader savedDataContents)
	throws IOException {
	if (savedDataContents != null)
	    return Boolean.valueOf(readSavedParameter(savedDataContents,
						      SAVE_OUTLINE_FLAG__F[LINE],
						      SAVE_OUTLINE_FLAG__F[FIRST_TOKEN])).booleanValue();
	else
	    return !outlineExists(savedDataContents);
	//if outline doesn't exist, default should be to save one
    }
    private int defaultSelectionMethod(BufferedReader savedDataContents) throws IOException {
	String
	    selectionMethod="";
	int
	    output;
	
	if (savedDataContents != null)
	    selectionMethod=readSavedParameter(savedDataContents,
					       AUTO_OUTLINE_FLAG__F[LINE],
					       AUTO_OUTLINE_FLAG__F[FIRST_TOKEN]);
	if (selectionMethod.equals("true"))
	    return 0;
	else
	    return 1;
    }
    private double defaultSliceSpacing(BufferedReader savedDataContents) 
	throws IOException {
	if (savedDataContents != null)
	    return Double.parseDouble(readSavedParameter(savedDataContents,
							 SLICE_SPACING_DOUBLE__F[LINE],
							 SLICE_SPACING_DOUBLE__F[FIRST_TOKEN]));
	else
	    return StackSeriesIterator.UNKNOWN_VALUE; //let's hope the user sees this is obviously wrong
    }

    private double defaultMinBrightness(BufferedReader savedDataContents) 
	throws IOException {
	if (savedDataContents != null)
	    return Double.parseDouble(readSavedParameter(savedDataContents,
	    					MIN_BRIGHTNESS_DOUBLE__F[LINE],
	    					MIN_BRIGHTNESS_DOUBLE__F[FIRST_TOKEN]));
	else
	    return 0;
    }
    private double defaultMaxBrightness(BufferedReader savedDataContents) 
	throws IOException {
	if (savedDataContents != null)
	    return Double.parseDouble(readSavedParameter(savedDataContents,
	    					MAX_BRIGHTNESS_DOUBLE__F[LINE],
	    					MAX_BRIGHTNESS_DOUBLE__F[FIRST_TOKEN]));
	else
	    return stacks.getMaxPixelValue();
    }

    
    //      protected final void waitOnEventQueue() {
//  	/*This method prevents a race condition where certain
//            operations on a displayed image cause an exception in the
//            event-dispatching thread and occasionally make a vulnerable
//            JVM implementation crash.  A thread that calls this method
//            is blocked until all pending events have been dispatched.

//            Image conversions that involve temporarily deleting slices
//            (like rotation and changing the color depth) may sometimes
//            need to be delayed in this manner to keep them from deleting
//            a slice while an event whose listener uses that slice (often
//            in repainting the image's window) is still queued.

//  	  This method or one like it should be used wherever the race
//  	  condition is known to occur: even if the successful handling
//  	  of the event is not important in itself, the exception
//  	  thrown when it fails may cause a fatal error in the native
//  	  code for event-handling on certain platforms. */
//  //  	try {
//  //  	    EventQueue.invokeAndWait(eventQueueLock);
//  //  	} catch (InterruptedException e) {
//  //  	    /**/e.printStackTrace();
//  //  	    //oops, try again:
//  //  	    waitOnEventQueue();
//  //  	} catch (InvocationTargetException e) {
//  //  	    //eventQueueLock.run() is a no-op, it can't throw an exception:
//  //  	    throw new AWTError("Event queue malfunction");
//  //  	}
//  	//do it the way invokeAndWait does it:
//  	synchronized(eventQueueNotifier) {
//  	    eq.postEvent(new InvocationEvent(this, eventQueueLock, eventQueueNotifier, true));
//  	    //does the event get posted in here?
//  	    try {
//  		eventQueueNotifier.wait();
//  	    } catch (InterruptedException e) {
//  		e.printStackTrace();
//  	    }
//  	}
//  	//may as well catch exceptions, we shouldn't get them as eventQueueLock.run() does nothing
//      }

    public void describeThisPlugin() {
	IJ.showMessage("Confused?...",
		       "    Structure Autoselector is a tool for isolating multiple objects shown in a series\n" +
		       "of 3D stacks.  For each selected object in a slice of one stack, this plugin makes\n" +
		       "a separate series of stacks showing the object alone and in a standard orientation.\n" +
		       "    You can select each object manually or, if your stacks show many objects of similar\n" +
		       "shape, save your first selection and have SA make all others based on your original one.\n" +
		       "In either case, you will need to draw the object's 2 visible axes yourself--this helps SA\n" +
		       "orient its output and handle scale/position differences between the objects it must select.\n" +
		       "    NOTE! Requires objects to keep the same shape and position from one stack to another.");
    }
    private boolean doSelecting(boolean redo, boolean autoOutline) {
	/*Sets up window for user to make selections or adjust LUT in,
          while giving appropriate directions.  Also causes selections
          and LUT settings to be taken and stored in the appropriate
          variables.  Returns true if user signals to abort execution. */
	Frame directions=(redo?
			  makeTextWindow("Directions", "Set the brightness, contrast and color table as you like " + 
					 "without clicking Apply.  When finished, press Space to continue or Alt-Space to abort.", 60):
			  makeDirectionsWindow(autoOutline));
	//set up windows:
	Dimension screenSize=Toolkit.getDefaultToolkit().getScreenSize();
	selectionImage=getSelectionImage(); //no-op unless a subclass overrode it
	ImageWindow selectionWindow=selectionImage.getWindow();
	boolean exit=false;
	//center selection and directions windows side-to-side:
	while (directions.getWidth() + selectionWindow.getWidth() > screenSize.width) {
	    selectionWindow.getCanvas().zoomOut(0, 0);
	    selectionWindow.pack();
	}
	directions.setLocation((screenSize.width - selectionWindow.getWidth() - directions.getWidth())/2,
			       (screenSize.height - directions.getHeight())/2);
	selectionWindow.setLocation(directions.getX() + directions.getWidth(),
				    (screenSize.height - selectionWindow.getHeight())/2);
	directions.show();
	WindowManager.addWindow(directions);
	WindowManager.setWindow(selectionWindow);
	if (redo) { //get selections from the saved file and only let the user change the LUT:
	    try {
		restoreSavedStructures(savedDataContents);
	    } catch (Exception e) {
		IJ.showMessage(e + "\nwhile restoring last run's selections.\n" +
			       "Sorry, I'll have to exit now.");
		e.printStackTrace();
		return true;
	    }
	    //now wait for user to set LUT:
	    while (true) {
		selectionImage.killRoi(); //make attempts to select fail visibly, in case user only chose "redo" by accident
		if (IJ.spaceBarDown()) {
		    if (IJ.altKeyDown()) {
			exit=true;
			IJ.setKeyUp(KeyEvent.VK_ALT); //release of key wouldn't register otherwise
		    }
		    IJ.setKeyUp(KeyEvent.VK_SPACE);
		    break;
		}
	    }
	} else {
		// aici
		// imageProcessor - does it have setMin and setMax? for the fine tuning part
//		IJ.run("Brightness/Contrast...");
//		selectionImage.getProcessor().setMinAndMax(35,138);
//		ContrastAdjuster.update();
		
		exit=takeSelections(autoOutline);
	}
	directions.dispose();
	WindowManager.removeWindow(directions);
	
	// store here the values for fine tuning of brightness / contrast
	desiredBrightnessMin=selectionImage.getProcessor().getMin();
	desiredBrightnessMax=selectionImage.getProcessor().getMax();
	desiredColorModel=selectionImage.getStack().getColorModel();
	return exit;
    }
    private void drawOutlines(BufferedReader savedDataContents) 
	throws IOException {
	/*Makes every Structure in structures assume the saved outline.*/
	Structure
	    prototype;
	int[]
	    axes=new int[8],
	    outlineXs, outlineYs;
	StringTokenizer
	    constructorArguments;
	int
	    nPointsInOutline;
		
		//reading saved structure...
	constructorArguments=new StringTokenizer(readSavedSection(savedDataContents, 2));
	//2nd line of file has outline data
	for (int index=0; index < 8; index++)
	    axes[index]=Integer.parseInt(constructorArguments.nextToken());
	nPointsInOutline=Integer.parseInt(constructorArguments.nextToken());
	outlineXs=new int[nPointsInOutline];
	outlineYs=new int[nPointsInOutline];
	for (int index=0; index < nPointsInOutline; index++) {
	    outlineXs[index]=Integer.parseInt(constructorArguments.nextToken());
	    outlineYs[index]=Integer.parseInt(constructorArguments.nextToken());
	}
	prototype=new Structure(axes, outlineXs, outlineYs);
	//...and making other structures assume its outline.
	for (int index=0; index < structures.size(); index++) {
	    Structure currentStructure=(Structure) structures.elementAt(index);
	    currentStructure.takeOutlineOf(prototype);
	    selectionImage.setRoi(currentStructure.makeRoiFromOutline());
	    selectionImage.unlock();
	    IJ.run("Draw"); //this line + previous make new outline visible
	    selectionImage.killRoi();
	}
    }
    private Line getAxisSafely(boolean checkForExit) {
	/*Detects & returns a line selection, for use as an axis, and
	   correctly ignores any number of failed attempts to make the
	   selection (user clicks but doesn't drag far enough to
	   register as a line).  Argument should only be true when
	   calling this method to produce the first ROI in a
	   structure--i.e. for a structure's first axis when
	   autoOutline is true.*/
	Line axis;
	Roi uncastAxis; //store what will become axis before we know if casting to line will throw exception
	boolean lineNotDrawn;
	do { //let user draw while loop runs
	    lineNotDrawn=false;
	    if ((checkForExit) && (IJ.spaceBarDown())) {
		IJ.setKeyUp(KeyEvent.VK_SPACE);
		/*workaround for IJ bug/feature--if not called,
                  spaceBarDown would return true every call after the
                  first true one */
		return null;
	    }
	    if ((uncastAxis=selectionImage.getRoi())==null) {
		lineNotDrawn=true;
		continue;
	    }
	    if (!(uncastAxis instanceof Line)) { //user screwed up and switched tools
		Toolbar.getInstance().setTool(Toolbar.LINE);
		selectionImage.killRoi();
		lineNotDrawn=true;
	    }
	} while (lineNotDrawn || (axis=(Line) uncastAxis).getState()==Roi.CONSTRUCTING ||
		 (axis.x1==axis.x2 && axis.y1==axis.y2)); //user "drew" a line w/0 length
	IJ.run("Draw");
	selectionImage.killRoi();
	return axis;
    }
    protected void getExtendedStructureInfo() {
	/*Called right after the user makes each selection, this
          method does nothing in SA--but subclasses can override it
          to get additional information they need about the
          selection.*/
    }
    //delete the following variables:
    //EventQueue eq=new TransparentEventQueue();
    //Object eventQueueNotifier=new Object();

    protected final Class getMyClass() { //FIXME: there are better ways to do this, try and delete it
	/*Returns the class in which the method that directly calls
	  this one is defined.  Where the calling method overrides
	  another, the subclass with the overriding method is
	  returned; where the calling method is overridden, the
	  superclass with the overridden method is returned.  Credit
	  goes to http://java2.5341.com/msg/55334.html for the
	  technique.

	  Don't rely on a nonlocal variable to store the return value
	  of this method--Java's rules of inheritance will let the
	  value be accessed in places where it's incorrect.*/
	class ContextTracer extends SecurityManager {
	    public Class[] getClassContext() {
		return super.getClassContext();
	    }
	}; /*only object that can do a stack trace & return actual
             trace elements in Java <1.4*/
	return new ContextTracer().getClassContext()[2];
	/*Element 0 is ContextTracer.class because of the call to
          getClassContext(), element 1 is Structure_Autoselector.class
          from the call to getMyClass().  The class of the calling
          method must then be element 2.*/
    }
    private BufferedReader getSavedDataFrom(File savedDataFile) throws Exception {
	if (savedDataFile.exists() && 
	    (savedDataLength=(int) savedDataFile.length()) > 0) {
	    /*cast guaranteed safe until someone finds excuse to save
	      >2Gb settings in savedDataFile*/
	    return new BufferedReader(new FileReader(savedDataFile), savedDataLength+1);
	    /*make sure buffer can hold entire file (it should be small)*/
	}
	else
	    return null;
    }
    protected ImagePlus getSelectionImage() {
	/*The return value of this method becomes the image in which
          the user is asked to make selections.  This implementation
          returns the stack the user opened when the plugin started;
          override to use a different image.  A grayscale image should
          be converted to 8-bit so that the user can adjust its
          brightness and color table in a way that will carry over to
          the output properly. */
	return selectionImage;
    }
    public void itemStateChanged(ItemEvent e) {
	/*options dialog gets passed this method, and runs it*/
	if (e.getSource() == redoLastRun) {
	    if (!((Checkbox) e.getSource()).getState()) {
		if (selectionMethod != null) {
		    selectionMethod.setEnabled(true);
		    saveOutline.setState(selectionMethod.getSelectedIndex()==MANUAL);
		} else
		    saveOutline.setState(true);
		saveOutline.setEnabled(true);
		//default setting
	    } else {
		//don't offer options to do w/making new selections
		if (selectionMethod != null) 
		    selectionMethod.setEnabled(false);
		saveOutline.setEnabled(false);
		saveOutline.setState(false);
	    }
	    return;
	} else if (selectionMethod != null && selectionMethod.getSelectedIndex()==AUTO) {
	    saveOutline.setEnabled(false);
	    saveOutline.setState(false);
	    //this is needed for correct option-taking...
	} else {
	    saveOutline.setEnabled(true);
	    saveOutline.setState(true);
	    //...and this is just default.
	}
    }
//      private void grayscaleTo8Bits(ImagePlus imp) {
//  	/*Makes imp be 8-bit.  If imp is 32-bit color, throws an
//            IllegalArgumentException (because while it's possible to
//            convert 32-bit color to 8-bit there's no reason to do so in
//            this plugin).  If imp is indexed color or 8-bit grayscale
//            already, returns it unchanged.

//  	  If imp is a stack, the conversion will be done so that the
//  	  dimmest pixel in the entire stack is 0 and the brightest is
//  	  255.*/
//  	int currentSlice;
//  	IJ.showStatus("Converting to 8 bits...");
//  	if (imp.getType()==ImagePlus.COLOR_RGB)
//  	    throw new IllegalArgumentException("ImagePlus \"" + imp.getTitle() + "\" isn't grayscale");
//  	else if (imp.getType()==ImagePlus.GRAY16 || imp.getType()==ImagePlus.GRAY32) {
//  	    synchronized(imp) {
//  		double stackMin=0, stackMax=0;
//  		currentSlice=imp.getCurrentSlice();
//  		imp.setStack(null, imp.getStack()); //workaround for ImagePlus bug where imp.getStack() can be defensive copy
//  		for (int sliceIndex=1; sliceIndex < imp.getStackSize(); sliceIndex++) {
//  		    Object pixels=imp.getStack().getPixels(sliceIndex);
//  		    for (int pixelIndex=0; pixelIndex < Array.getLength(pixels); pixelIndex++) {
//  			double currentPixel=(imp.getType()==ImagePlus.GRAY16? ((short[]) pixels)[pixelIndex] & 0x0000FFFF:
//  					     ((float[]) pixels)[pixelIndex]);
//  			    //make sure short pixel values come out unsigned
//  			if (sliceIndex==1 && pixelIndex < 1000 && ((short[]) pixels)[pixelIndex] < 0)
//  			    System.out.println("pixel value " + ((short[]) pixels)[pixelIndex] + " corrected to " + (((short[]) pixels)[pixelIndex] & 0x0000FFFF));
//  			if (pixelIndex==0)
//  			    stackMin=stackMax=currentPixel;
//  			else {
//  			    stackMin=Math.min(stackMin, currentPixel);
//  			    stackMax=Math.max(stackMax, currentPixel);
//  			}
//  		    }
//  		}
//  		//make converter use the max and min brightnesses just found for 255 and 0:
//  		imp.getProcessor().setMinAndMax(stackMin, stackMax);
//  		imp.getStack().update(imp.getProcessor());
	    
//  		//do the actual conversion:
//  		//  	    ImageStack newStack=new ImageStack(imp.getWidth(), imp.getHeight());
//  		new StackConverter(imp).convertToGray8();
//  		//  	    for (int index=1; index <= imp.getStackSize(); index++)
//  		//  		newStack.addSlice("", new TypeConverter(imp.getStack().getProcessor(index), true).convertToByte().getPixels());
//  		//  	    (returnValue=new ImagePlus(imp.getTitle(), newStack)).setSlice(currentSlice);
//  	    }
//  	}
//  	IJ.showStatus("");
//      }

    protected Frame makeDirectionsWindow(boolean autoOutline) { //FIXME: enforce column size with args to text-area constructor, not with inserting newlines
	/*Returns a window showing directions for the user on how to
          select, packed and ready to show(). */
	String
	    howToSelectAutomatically="To select each " + structureName + ", trace both of its visible axes with the line tool.\n" +
	    " Press space bar when finished selecting, and outlines will appear\n" +
	    " automatically.\n\n",
	    howToSelectManually="To select each " + structureName + ", trace its outline with the polygon tool, then both\n" +
	    " of its visible axes with the line tool (switching between tools is done\n" +
	    " automatically).  Press space bar when finished selecting.\n\n";
	Frame directions=new Frame("Directions")/*, selectionWindow*/;
	javax.swing.JTextArea directionsText=new javax.swing.JTextArea((autoOutline? howToSelectAutomatically: howToSelectManually) +
								       "You can adjust the current slice's brightness, contrast or LUT\n" +
								       "at any time (just be sure not to click 'Apply') and the output\n" +
								       "will be adjusted likewise.\n\n" +
								       "In the output, " + structureName + "s will be oriented so that:\n" +
								       "--First axis drawn is horizontal, with first endpoint to left\n" +
								       "--Second axis drawn is vertical, with first endpoint at top\n\n" +
								       "(C. elegans biologists: to put an embryo in standard orientation,\n" +
								       "draw the first axis head-to-tail and the second dorsal-to-ventral.)");
	//to do: make text of directions variable so subclasses can hide
	directionsText.setSize(directionsText.getPreferredSize());
	directionsText.setEditable(false);
	directionsText.setLineWrap(true);
	directionsText.setWrapStyleWord(true);
	directions.add(directionsText);
	directions.pack();
	return directions;
    }
    protected final String makeMacSafeFileName(String nameBase, String extension) {
	/*Concatenates nameBase and extension, but with nameBase
	  truncated if necessary so that the result is within the
	  31-character limit for filenames in OS<=9's HFS file
	  system.*/
	if (nameBase.length() + extension.length() <= 31)
	    return nameBase + extension;
	else
	    return
		nameBase.substring(0, 30-extension.length()) + extension;
    }
    private String makeMacSafeNameBase(StackSeriesIterator stacks, String fixed, String dotExt) {
	/*Returns shortNB, the common element from the filenames of
	  all images enumerated by stacks, truncated so that all
	  output-stack filenames which have the form
	  shortNB+fixed+whichStack+dotExt (where whichStack is the
	  number of an image enumerated by stacks) will be under 31
	  characters.  Necessary to avoid the following situation:

	  Output stack 1's filename:
	  makeMacSafeFileName("longDrawnOutStackName",
	  whichStack+dotExt), or "longDrawnOutStackName1.stack"

	  Output stack 3,067,895's filename:
	  makeMacSafeFileName("longDrawnOutStackName",
	  whichStack+dotExt), or "longDrawnOutStackN3067895.stack"

	  ...meaning whatever program gets the output stacks next may
	  not recognize the first stack prefixed longDrawnOutStackName
	  and the later stack prefixed longDrawnOutStackN as belonging
	  to the same numbered series.  Solution: use
	  makeMacSafeNameBase(longDrawnOutStackSeriesIterator, "",
	  .stack), which will return "longDrawnOutStackN" as the
	  proper prefix for *all* output stacks in the series.

	  NOTE: fixed is any extra string that must appear whole in
	  the output stacks' filenames.*/
	int 
	    reservedLength=fixed.length() + Integer.toString(stacks.getIterationEnd()).length()
	    + dotExt.length();
	String
	    nameBase=stacks.getNameBase();
	/*# of characters outside of shortNB that will be in filename
          of output stack having longest number*/
	return (31-reservedLength < nameBase.length())? //if so, truncate
	    nameBase.substring(0, 30-reservedLength):
	    nameBase;
    }
    private String makeNumberStringFor(int whichStack, StackSeriesIterator stacks) {
	/*Returns a string representation of whichStack, expanded with
          leading zeroes to the maximum length of a stack number in
          stacks's iteration range.*/
	return stringifyToNDecimalDigits(whichStack, Integer.toString(stacks.getIterationEnd()).length());
    }
    protected EventDrivenDialog makeOptionsDialog(File savedDataFile, StackSeriesIterator stacks)
	throws IOException {
	/*Creates dialog where user can set every option
	  possible in SA or a subclass (except initial query re
	  where images/saved data are).  Subclasses can add
	  options.*/
		
	EventDrivenDialog options=null;
	    try {
		options=new EventDrivenDialog("Options",
					      this.getClass().getMethod("actionPerformed",
									new Class[] {ActionEvent.class}), null,
					      this.getClass().getMethod("itemStateChanged",
									new Class[] {ItemEvent.class}),
					      null, null,
					      this);
	    } catch (NoSuchMethodException e) {} //it's not happening, these methods exist & are public
	int[]
	    availableSeriesRange=stacks.getSeriesRange(),
	    desiredSeriesRange=userSeriesRange(savedDataContents);
	String
	    defaultNoiseFilter;
	
	this.sliceSpacing=new TextField(Double.toString((stacks.getSliceSpacing()==StackSeriesIterator.UNKNOWN_VALUE)?
							defaultSliceSpacing(savedDataContents):
							stacks.getSliceSpacing()), 6);
	//Force user to enter the slice spacing if it isn't known already:
	if (Double.parseDouble(sliceSpacing.getText())==StackSeriesIterator.UNKNOWN_VALUE)
	    /*OK to use == on double, we're testing if sliceSpacing
              was set to hold a constant not comparing calculated
              values*/
	    sliceSpacing.setText("");
	options.setResizable(false);
	options.addMessage("Stacks #" + availableSeriesRange[0] + "-" +
			   availableSeriesRange[1] + " exist.");
	options.addNumericField("First stack to process: #", desiredSeriesRange[0], 0);
	options.addNumericField("Last stack to process: #", desiredSeriesRange[1], 0);
	options.addNumericField("Slice spacing (pixels): ", sliceSpacing);
	if (stacks.getSliceSpacing() != StackSeriesIterator.UNKNOWN_VALUE)
	    /*the file itself reported the correct spacing--user need not edit it:*/
	    sliceSpacing.setEditable(false);
	if (stacks.canCorrectZoom()) {
	    zoomButton=new Button("Correct for zoom...");
	    options.addButton(zoomButton);
	    zoomButton.addActionListener(options);
	}
	if (savedDataContents != null) { //redo should be possible
	    redoLastRun=new Checkbox("Reuse last run's selections",
					    false);
	    //it'd be annoying if true could become default
	    redoLastRun.addItemListener(options);
	    options.addCheckbox(redoLastRun);
	}
//	options.addDivider(40);
	if (outlineExists(savedDataContents)) {
	    selectionMethod=new Choice();
	    selectionMethod.add("automatically by axes");
	    selectionMethod.add("manually");
	    selectionMethod.addItemListener(options);
	    selectionMethod.select(defaultSelectionMethod(savedDataContents));
	    options.addChoice("Objects will be selected ", selectionMethod);
	}
	else
	    options.addMessage("No saved selection found!\nManual selecting forced.");
	saveOutline=new Checkbox("Save 1st selection",
					defaultSaveOutline(savedDataContents));
	if (selectionMethod != null &&
	    selectionMethod.getSelectedIndex()==AUTO)
	    saveOutline.setEnabled(false);
	options.addCheckbox(saveOutline);
	options.addDivider(40);
	
	options.addMessage("8-bit Conversion");
	options.addNumericField("Map darkest pixel to", defaultMinBrightness(savedDataContents), 2); 
	options.addNumericField("Map brightest pixel to", defaultMaxBrightness(savedDataContents), 2); 
	options.addDivider(40);
	options.addCheckbox("Interpolate projection slices", defaultInterpolation(savedDataContents));
	switch(defaultNoiseFilter(savedDataContents)) {
	case GAUSSIAN_FILTER:
	    defaultNoiseFilter="Gaussian blur";
	    break;
	case DESPECKLE_FILTER:
	    defaultNoiseFilter="Despeckle";
	    break;
	case DUAL_FILTER:
	    defaultNoiseFilter="Both";
	    break;
	default:
	    defaultNoiseFilter="None";
	}
	options.addChoice("Noise filter: " , new String[]
	    {"None", "Gaussian blur", "Despeckle", "Both"},
			  defaultNoiseFilter);
	options.addDivider(40);
	return options;
    }
    private void makeSelectionsKey(String outputDir, String nameBase) {
	/*Saves a snapshot of the stack the user made selections on,
	  with the selections numbered in the order they were made.*/
	TextRoi number;
	String numerals; //string version of number
	Structure currentStructure;
	DialogFiller dF=null; //for now
	Exception e=null;
	do {
	    try {
		dF=new DialogFiller("ZProjection", false,
				    new boolean[] {!IJ.isMacintosh(), IJ.isMacintosh()},
				    null, new Integer[] {new Integer(1)}, //max value proj (outlines show up best)
				    new String[] {"1", Integer.toString(selectionImage.getStackSize())},
				    null, null);
	    }
	    catch (Exception x) {
		if (x.getMessage().startsWith("Already")) {
		    /*some other DialogFiller exists for this box--wait
		      for it to go away*/
		    e=x;
		    IJ.wait(500);
		}
		/*otherwise it's a random thing--best to give up*/
		else return;
	    }
	} while (e != null);
	IJ.showStatus("Saving key to output series...");
	//Z-projecting screwed up the brightness (but not the color model)--fix it:
	//IJ.getImage().getProcessor().setMinAndMax(desiredBrightnessMin, desiredBrightnessMax);
	selectionImage.getProcessor().setMinAndMax(desiredBrightnessMin, desiredBrightnessMax);
	selectionImage.getStack().update(selectionImage.getProcessor());
	if (selectionImage.getProcessor() instanceof ByteProcessor)
	    ((ByteProcessor) /*IJ.getImage()*/ selectionImage.getProcessor()).applyLut();
	if (((Boolean) usersOrders.elementAt(REDO_FLAG__O)).booleanValue())
	    //old selections key should still be valid
	    return;
	for (int index=0; index<structures.size(); index++) {
	    currentStructure=(Structure) structures.elementAt(index);
	    numerals=Integer.toString(index);
	    number=new TextRoi((currentStructure.getOrigin())[0],
			       (currentStructure.getOrigin())[1],
			       selectionImage);
	    for (int index2=0; index2 < numerals.length(); index2++)
		number.addChar(numerals.charAt(index2));
	    selectionImage.setRoi(number);
	    IJ.run("Draw");
	    selectionImage.killRoi();
	}
	dF.setActive(true);
	IJ.run("Z Project..."); //no other way to do it in IJ 1.29
	dF.selfDestruct();
	//Z-projection screwed up the min and max, set them right again:
	IJ.run("Jpeg...", "path='"
	       + outputDir + makeMacSafeFileName(nameBase, "_SAkey.jpg")
	       +"'");
	IJ.getImage().getWindow().close();
    }
    protected final Frame makeTextWindow(String titleText, String windowText, final int maxLineLength) {
	/*Returns a top-level window called titleText showing
          windowText, read-only and broken up on whitespace into lines
          of maxLineLength characters or less.  The window is the
          smallest size allowing all the text to be read without
          scrolling and has no listeners registered.

	  Throws an IllegalArgumentException if maxLineLength is less than 2. */
	if (maxLineLength < 2)
	    throw new IllegalArgumentException("max line length " + maxLineLength + " < 2");
	StringTokenizer words=new StringTokenizer(windowText, " \t\n\r\f-", true); //don't lose existing formatting
	String theWord=null;
	StringBuffer filledText=new StringBuffer();
	int lineLength=0, nLines=1;
	//break windowText into lines:
	while (words.hasMoreTokens()) {
	    theWord=words.nextToken();
	    while (theWord.length() > maxLineLength) { //break it into max-length lines w/dash at end
		int breakLength=maxLineLength - lineLength - 1;
		filledText.append(theWord.substring(0, breakLength)).append('-').append('\n');
		lineLength=0;
		nLines++;
		theWord=theWord.substring(breakLength, theWord.length());
	    } //what's left can now be treated like a normal word:
	    if (lineLength + theWord.length() > maxLineLength) { //save it for next line:
		filledText.append('\n');
		nLines++;
		lineLength=0;
	    }
	    if (theWord=="\n" || theWord=="\f") nLines++;
	    //if we're here we made sure this line has room for theWord, so add it:
	    filledText.append(theWord);
	    lineLength += theWord.length();
	}
	//now make the window:
	Frame theWindow=new Frame(titleText);
	javax.swing.JTextArea textArea=new javax.swing.JTextArea(filledText.toString());
	textArea.setSize(textArea.getPreferredSize());
	textArea.setEditable(false);
	textArea.setLineWrap(true);
	textArea.setWrapStyleWord(true);
	theWindow.setResizable(false);
	theWindow.add(textArea);
	theWindow.pack();
	return theWindow;
    }
    private boolean outlineExists(BufferedReader savedDataContents) 
	throws IOException {
	/*Returns true if user has already drawn & saved a
	  prototypical outline for an object in this stack series.  See
	  comments above.*/
	if (savedDataContents != null)
	    return Boolean.valueOf(readSavedParameter(savedDataContents,
						      PRE_OUTLINED_FLAG__F[LINE],
						      PRE_OUTLINED_FLAG__F[FIRST_TOKEN])).booleanValue();
	else
	    return false;
    }
    private ImagePlus poseStructure(ImagePlus rawData, int imageType, Structure currentStructure) {
	/*From rawData, a stack on which various Structures are
          selected, creates and returns a stack showing
          currentStructure cropped out and placed in standard
          orientation.*/
	int[] centeringCorrection=currentStructure.centeringCorrection(),
	    /*amount by which cropped areas should be pasted into new
	      stack off-center so rotation will leave them centered
	      properly*/
	    dimensions=currentStructure.dimensions();
	PolygonRoi cropRoi;
	int nSlices=rawData.getStackSize(),
	    copyWindowSize=(int) Math.ceil(Math.sqrt(
						     dimensions[0] * dimensions[0] +
						     dimensions[1] * dimensions[1]));
	/*Rotating the isolated selection leaves white corners on
	  the stack-- not good for the brightest-point projection
	  to be made from it!  A window of this length on each
	  side is the smallest big enough that the final cropping
	  step always excludes these corners.  Credit goes to Bill
	  Mohler for the general idea.*/
	/*ImageWindow*/ StackWindow cropWindow, rawDataWindow=(StackWindow) rawData.getWindow();
	ImagePlus cropImp;
	ImageProcessor cropIP;
	StackProcessor cropSP;
	NewImage.open("cutout",
		      copyWindowSize, copyWindowSize,
		      nSlices, imageType, NewImage.FILL_BLACK, false);
	cropWindow=(StackWindow) WindowManager.getCurrentWindow();
	cropImp=cropWindow.getImagePlus();
//  	if (cropImp.isInvertedLut())
//  	    IJ.setForegroundColor(255, 255, 255); //black
//  	else
//  	    IJ.setForegroundColor(0, 0, 0);
	for (int sliceIndex=1;
	     sliceIndex <= nSlices;
	     sliceIndex++) {
	    WindowManager.setCurrentWindow(rawDataWindow);
//  	    rawData.setSlice(currentStructure.needsFlipped()?
//  			     nSlices + 1 - sliceIndex: //reverse slice order
//  			     sliceIndex);
  	    activateSlice(rawData, (currentStructure.needsFlipped()?
  				    nSlices + 1 - sliceIndex:
  				    sliceIndex));
	    if (sliceIndex==1)
		rawData.setRoi(currentStructure.makeRoiFromOutline());
	    StrictPaster.copyFrom(rawDataWindow);
	    WindowManager.setCurrentWindow(cropWindow);
//	    System.out.println("Slice " + sliceIndex + " out of " + cropImp.getStackSize());
	    //cropImp.setSlice(sliceIndex);
	    //cropWindow.showSlice(sliceIndex);
	    activateSlice(cropImp, sliceIndex);
	    if (sliceIndex==1) {
		Rectangle boundingRect;
		cropRoi=currentStructure.makeRoiFromOutline();
		boundingRect=cropRoi.getBoundingRect();
		cropImp.setRoi(cropRoi);
		cropRoi.setLocation((int) Math.round(.5*(copyWindowSize - boundingRect.getWidth()))
				    - centeringCorrection[0],
				    (int) Math.round(.5*(copyWindowSize - boundingRect.getHeight()))
				    - centeringCorrection[1]);
		/*direct pasted slice to where, after rotation,
		  cutting out a piece the right size from the
		  middle will get all of it*/
	    }
	    StrictPaster.pasteTo(cropWindow);
	}
	cropImp.killRoi();
	//otherwise only what's in the ROI gets rotated
	WindowManager.setCurrentWindow(cropWindow);
	IJ.run("Arbitrarily...", "stack angle="
	       + (-180/Math.PI * currentStructure.orientation())
				//rotate it backward by currentStructure.orientation() degrees
	       + " interpolate");
	/*The part of the new stack to keep is in the center; its
	      bounding rectangle is dimensions[0] x dimensions[1].
	      Crop accordingly...*/
	cropImp.killRoi();
	cropImp.setRoi(new Roi(
			       (copyWindowSize - dimensions[0])/2,
			       (copyWindowSize - dimensions[1])/2,
			       dimensions[0], dimensions[1]));
	cropIP=cropImp.getProcessor();
	cropSP=new StackProcessor(cropImp.getStack(),
				  cropIP);
	cropImp.setStack(null,
			 cropSP.resize(dimensions[0], dimensions[1]));
	//above reverse-engineered from ImageJ's Resizer class
	cropWindow=(StackWindow) cropImp.getWindow();
	//resizing got rid of old cropWindow
	if (currentStructure.needsFlipped())
	    IJ.run("Flip Vertically", "stack");
//  	cropImp.getStack().setColorModel(desiredColorModel);
//  	for (int index=1; index <= cropImp.getStackSize(); index++) {
//  	    cropImp.setSlice(index);
//  	    cropImp.getProcessor().setMinAndMax(desiredBrightnessMin, desiredBrightnessMax);
//  	}
	return cropImp;
    }
    protected void postProcessProjectedStructure(StackSeriesIterator stacks, ImagePlus projsStack, int whichStructure) {
	/*Makes final modifications to a stack showing projected views
          of an isolated Structure.  See comment on
          postProcessSectionedStructure().*/
    }
    protected void postProcessSectionedStructure(StackSeriesIterator stacks, ImagePlus sectionsStack, int whichStructure) {
	/*Makes final modifications to a cropped, oriented stack
          depicting a Structure before saving.  SA doesn't need this
          but calls it at the appropriate time for benefit of
          subclasses, which may override as needed.*/
    }
    private void processTimePoint(ImagePlus rawData, int stackNumber, String outputDir, String nameBase)
	throws Exception {
	/*For each Structure: 
 
	  --Cuts the region it covers out of rawData and places it in
	  uniform orientation.

	  --Builds a subdirectory tree, if it does not already exist,
	  for storing output in the directory chosen for output by the
	  user.  The subdirectory for the Structure overall is called
	  nameBase + " sel " + a number N that identifies the
	  Structure.  Within this directory are two subdirectories,
	  one called nameBase + " cuts " + N and the other called
	  nameBase + " projs " + N.

	  --Saves the cutout in the cutouts directory as a QuickTime
	  movie of arbitrary frame rate, one slice per
	  frame. (Filename: nameBase + _CsNtX.mov, where N identifies
	  the structure and X identifies the timepoint.)

	  --Creates a window containing two 3D projections of the
	  cutout, one around X-axis and one around Y.

	  --Saves the projections window in the projections directory
	  as a QuickTime movie of arbitrary frame rate, one view per
	  frame. (Filename: nameBase + _PsNtX.mov, see above.)

	  This method has no effect on the file rawData came from, but
	  it will overwrite any file whose name conflicts with the
	  name of a file or directory to be created.

	  NOTE: All file and directory names are kept to a
	  31-character maximum by truncating nameBase as needed.*/

	ImageWindow
	    cropWindow,
	    doubleProjWindow,
	    rawDataWindow=rawData.getWindow(); //each method can get its own
	Structure
	    currentStructure;
	int
	    imageType=((rawData.getType()==ImagePlus.COLOR_256)?
		       NewImage.GRAY8:
		       ((rawData.getType()==ImagePlus.COLOR_RGB)?
			NewImage.RGB:
			rawData.getType())),
	    //NewImage and ImagePlus have some incompatible type constants
	    noiseFilter=((Integer) usersOrders.elementAt(NOISE_FILTER_INT__O)).intValue();
	    //codecType; //what Quicktime compression codec to use
	String
	    sliceSpacing=((Double) usersOrders.elementAt(SLICE_SPACING_DOUBLE__O)).toString();
	DialogFiller
	    projectionDialogFiller;
//  	QTMovieHack
//  	    qtifier;
	Stack_To_QT
	    stackWriter=new Stack_To_QT();
	switch (noiseFilter) {
	case DUAL_FILTER:
	    IJ.run("Despeckle", "stack");
	case GAUSSIAN_FILTER:
	    IJ.run("Gaussian Blur...", "radius=2 stack");
	    break;
	case DESPECKLE_FILTER:
	    IJ.run("Despeckle", "stack");
	    break;
	}
	projectionDialogFiller=new DialogFiller("3D Projection", false, 
						(IJ.isMacintosh()?
						 new boolean[] {false, true}:
						 new boolean[] {true, false}),
						//Macs have OK & Cancel switched
						new Boolean[] {(Boolean) usersOrders.elementAt(INTERPOLATION_FLAG__O)}, //interpolate?
						new Integer[] {new Integer(1), //brightest point
							       new Integer(1)}, //Y-axis
						new String[] {sliceSpacing,
							      "0", //init angle
							      "360", //total rotation
							      "10", //rotation angle increment
							      null, null, null, "0", "0"}, //no depth cuing, it interferes w/quantitating brightness
						null, null);
	for (int structIndex=0; structIndex < structures.size(); structIndex++) {
	    //show progress thru this stack in window title:
    	System.out.println(" " + structIndex);
	    IJ.getInstance().setTitle(IJ.getInstance().getTitle().substring(0, IJ.getInstance().getTitle().lastIndexOf(':')+1)
				      + " selection " + (structIndex+1) + '/' + structures.size());
	    File structDir=new File(outputDir, makeMacSafeFileName(nameBase, "_sel_" + structIndex)),
		cDir=new File(structDir, makeMacSafeFileName(nameBase, "_cuts_" + structIndex)),
		pDir=new File(structDir, makeMacSafeFileName(nameBase, "_projs_" + structIndex));
	    String outputFile=nameBase + "_Cs" + structIndex + "t" + makeNumberStringFor(stackNumber, stacks) + ".mov";
	    String cropWindowTitle;
	    /*C signifying merely Cropped view of structure.  Checks
              for Mac-safety of filename done already--don't repeat.*/
	    while (!cDir.exists())
	    	cDir.mkdirs();
	    while (!pDir.exists())
	    	pDir.mkdirs();
//	    (cropWindow=new ImageWindow(poseStructure(rawData, imageType, (currentStructure=(Structure) structures.elementAt(structIndex))))).show();
	    
//	    Functions.doFunction(setBatchMode());
//	    Interpreter.setBatchMode(true);
//	    IJ.doCommand("setBatchMode(false)");
	    
	    cropWindow=new ImageWindow(poseStructure(rawData, imageType, (currentStructure=(Structure) structures.elementAt(structIndex))));
	    cropWindow.show();
	    cropWindowTitle=WindowManager.getCurrentWindow().getTitle();
	    
//	    (doubleProjWindow=new StackWindow(projectStack2Ways(cropWindow.getImagePlus(), imageType, projectionDialogFiller))).show();
    	System.out.println("     in");
	    doubleProjWindow=new StackWindow(projectStack2Ways(cropWindow.getImagePlus(), imageType, projectionDialogFiller));
	    doubleProjWindow.show();
    	System.out.println("     out");

	    // dan: point of this? maybe use selectWindow(name), selectImage(id)
	    // new macro function: list = getList("window.titles")... todo: try it
	    while (!WindowManager.getCurrentWindow().getTitle().equals(cropWindowTitle))
	    {
	    	String currName = WindowManager.getCurrentWindow().getTitle();
	    	WindowManager.putBehind();
	    }
	    cropWindow=WindowManager.getCurrentWindow();
	    
	    postProcessSectionedStructure(stacks, IJ.getImage(), structIndex);
//    	    codecType=quicktime.std.StdQTConstants.kAnimationCodecType;
	    //lossless at quality used here
//    	    qtifier=new QTMovieHack(cDir.toString()+File.separatorChar, outputFile, codecType,
//    				    quicktime.std.StdQTConstants.codecMaxQuality,
//    				    8, 1);
	    //make all frames keyframes, otherwise annoying white mask can show
//    	    qtifier.go();
	    stackWriter.runBatch(cropWindow.getImagePlus(), cDir.toString()+File.separatorChar+outputFile, 10); //frame rate doesn't matter so use easy round #
	    outputFile=nameBase + "_Ps" + structIndex + "t" + makeNumberStringFor(stackNumber, stacks) + ".mov";
	    
	    /*P signifying Projected view of structure*/
//    	    qtifier.setOutput(pDir.toString()+File.separatorChar, outputFile);
	    postProcessProjectedStructure(stacks, doubleProjWindow.getImagePlus(), structIndex);
	    WindowManager.setCurrentWindow(doubleProjWindow);

//    	    qtifier.go();
	    stackWriter.runBatch(doubleProjWindow.getImagePlus(), pDir.toString()+File.separatorChar+outputFile, 10);
	    //close any old images:
	    int[] imageWindows=WindowManager.getIDList();
	    for (int windowIndex=0; windowIndex < imageWindows.length; windowIndex++) {
	    	ImagePlus currentImage=WindowManager.getImage(imageWindows[windowIndex]);
//	    	System.out.println("         " + currentImage.getWindow().getTitle());
	    	if (!currentImage.getWindow().equals(rawDataWindow)) {
	    		currentImage.hide();
	    		currentImage.flush();
	    	}
	    }
	}
	projectionDialogFiller.selfDestruct();
	rawDataWindow.getImagePlus().hide();
	rawDataWindow.getImagePlus().flush();
    }
    
    private ImagePlus projectStack2Ways(ImagePlus cropImp, int imageType, DialogFiller projectionDialogFiller) {
	/*Returns an ImagePlus showing 2 projected views of cropImp
          (so called because it should depict the region of interest
          cropped and oriented, as by poseStructure()): projection
          rotating around X-axis on left, projection rotating around
          Y-axis on right.*/
	ImageWindow cropWindow, xProjWindow, yProjWindow, doubleProjWindow;
	ImagePlus xProjImp, yProjImp, doubleProjImp;
	int gutterWidth, //space left between X & Y projections
	    nViews;
	Runnable runner = new Runnable() {public void run(){
		//otherwise a rare bug might try to project it next
    	System.out.print(".");
		IJ.wait(10);
	}};

	WindowManager.setCurrentWindow(cropImp.getWindow()); //in case it isn't
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		// TODO: handle exception
	}
	/*X-axis projection (=Y-axis projection flipped sideways,
	  because real X-axis projection would have weird
	  contrast--IJ bug*/
//	IJ.wait(100); //let the event be dispatched so can wait on it
//	waitOnEventQueue();
	IJ.run("Rotate 90 Degrees Right");
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	cropImp=IJ.getImage(); //rotating got rid of old one
	cropWindow=cropImp.getWindow();
	projectionDialogFiller.setActive(true);
	IJ.run("3D Project...");
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	//{int ignoreThisInt=IJ.getImage().getStackSize();}
	/*magic to prevent exception that gets thrown somehow when
          stack size is miscalculated during projection*/
	/*waitOnEventQueue();*/ /*otherwise next line screws up handling
                              of paint event generated by drawing the
                              last projected view */
	IJ.run("Rotate 90 Degrees Left");
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	xProjWindow=WindowManager.getCurrentWindow();
	nViews=(xProjImp=xProjWindow.getImagePlus()).getStackSize(); //yProjImp would give same
	xProjImp.changes=false;
	//don't bother user about saving it
//	xProjWindow.hide();
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	WindowManager.removeWindow(xProjWindow);
	WindowManager.setCurrentWindow(cropWindow);
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	IJ.run("Rotate 90 Degrees Left"); //put it back to normal for next proj
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	cropImp=IJ.getImage();
	cropWindow=cropImp.getWindow();
	//Y-axis projection:
	WindowManager.setCurrentWindow(cropWindow);
	IJ.run("3D Project...");
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	//need to waitOnEventQueue() here too?
	projectionDialogFiller.setActive(false);
	yProjWindow=WindowManager.getCurrentWindow();
	//cropImp.hide();
	//cropImp.flush();
	xProjWindow.setVisible(true);
	//WindowManager.addWindow(xProjWindow);
	xProjImp=xProjWindow.getImagePlus();
	yProjImp=yProjWindow.getImagePlus();
	gutterWidth=(int) ((xProjImp.getWidth()+yProjImp.getWidth())*.07); //looks about right
	nViews=xProjImp.getStackSize(); //yProjImp would give same
	NewImage.open("Dual projection", xProjImp.getWidth()+yProjImp.getWidth()+gutterWidth,
		      Math.max(xProjImp.getHeight(), yProjImp.getHeight()),
		      nViews, imageType, NewImage.FILL_BLACK, false);
	//what's pasted into the new image will have the right brightness, but the color model needs explicitly set:
	(doubleProjImp=(doubleProjWindow=WindowManager.getCurrentWindow()).getImagePlus()).getStack().setColorModel(desiredColorModel);
	doubleProjWindow.setVisible(false);
	for (int sliceIndex=1; sliceIndex <= nViews; sliceIndex++) {
	    WindowManager.setCurrentWindow(xProjWindow);
	    //xProjImp.setSlice(sliceIndex);
	    activateSlice(xProjImp, sliceIndex);
	    //yProjImp.setSlice(sliceIndex);
	    activateSlice(yProjImp, sliceIndex);
	    //doubleProjImp.setSlice(sliceIndex);
	    activateSlice(doubleProjImp, sliceIndex);
//  	    if (sliceIndex != 1)
//  		doubleProjImp.getStack().addSlice("", doubleProjImp.getProcessor());
	    if (sliceIndex==1) {
		xProjImp.setRoi(0, 0, xProjImp.getWidth(), xProjImp.getHeight());
		yProjImp.setRoi(0, 0, yProjImp.getWidth(), yProjImp.getHeight());
	    }
	    //xProjWindow.copy(false);
	    StrictPaster.copyFrom(xProjWindow);
	    WindowManager.setCurrentWindow(doubleProjWindow);
	    doubleProjImp.setRoi(0, (doubleProjImp.getHeight()-xProjImp.getHeight())/2,
				 //vertically centered
				 xProjImp.getWidth(), xProjImp.getHeight());
	    //make pasted selection go there
	    //doubleProjWindow.paste();
	    StrictPaster.pasteTo(doubleProjWindow);
	    WindowManager.setCurrentWindow(yProjWindow);
	    //yProjWindow.copy(false);
	    StrictPaster.copyFrom(yProjWindow);
	    WindowManager.setCurrentWindow(doubleProjWindow);
	    doubleProjImp.setRoi(xProjImp.getWidth()+gutterWidth, (doubleProjImp.getHeight()-yProjImp.getHeight())/2,
				 yProjImp.getWidth(), yProjImp.getHeight());
	    //doubleProjWindow.paste();
	    StrictPaster.pasteTo(doubleProjWindow);
//  	    doubleProjImp.getProcessor().setMinAndMax(desiredBrightnessMin, desiredBrightnessMax);
	}
	doubleProjWindow.setVisible(true);
	try {
		javax.swing.SwingUtilities.invokeAndWait(runner);		
	} catch (Exception e) {
		System.out.println("Exception: " + e.getMessage());
	}
	xProjImp.hide();
	xProjImp.flush();
	yProjImp.hide();
	yProjImp.flush();
	return doubleProjImp;
    }
    protected Vector readOptions(EventDrivenDialog options,
				 StackSeriesIterator stacks, boolean outlineExists)
	throws IOException {
	/*Collects & returns user's input to options.  Only override
          with a method that calls the original.*/
	Vector output=new Vector();
	int[]
	    desiredSeriesRange=new int[2],
	    availableSeriesRange=stacks.getSeriesRange();
	double sliceSpacing;
	boolean
	    numbersMisEntered=false,
	    redo;
	String filterString;
	int filterInt;
	double minBrightness, maxBrightness;
	do {
	    options.showDialog();
	    for (int index=0; index<=1; index++)
		desiredSeriesRange[index]=(int) Math.round(options.getNextNumber());
	    sliceSpacing=options.getNextNumber();
	    minBrightness=options.getNextNumber();
	    maxBrightness=options.getNextNumber();
	    numbersMisEntered=
		!options.wasCanceled() && //if it was the range entered doesn't matter
		(options.hasAnyInvalidNumbers() ||
		 (desiredSeriesRange[0] > desiredSeriesRange[1]) ||
		 (desiredSeriesRange[0] < availableSeriesRange[0]) ||
		 (desiredSeriesRange[1] > availableSeriesRange[1]) ||
		 sliceSpacing <= 0);
	    if (numbersMisEntered)
		IJ.beep();
	} while (numbersMisEntered);
	/*The user's other input to options is ensured valid by having
	  one of any mutually contradictory option pair grayed out.
	  Those who want to write another plugin that extends or calls
	  this one, and add extra options to the dialog box, take
	  note!*/
	output.addElement(options.wasCanceled()? Boolean.TRUE: Boolean.FALSE);
	for (int index=0; index < 2; index++)
	    output.addElement(
			      new Integer(desiredSeriesRange[index]));
	if (outlineExists &&
	    options.getNextChoice().equals("automatically by axes"))
	    output.addElement(Boolean.TRUE);
	else
	    output.addElement(Boolean.FALSE);
	if (redoLastRun != null)
	    //next checkbox is that one--don't use until later
	    options.getNextBoolean();
	output.addElement(options.getNextBoolean()? Boolean.TRUE: Boolean.FALSE);
	filterString=options.getNextChoice();
	if (filterString.equals("Gaussian blur"))
	    filterInt=GAUSSIAN_FILTER;
	else if (filterString.equals("Despeckle"))
	    filterInt=DESPECKLE_FILTER;
	else if (filterString.equals("Both"))
	    filterInt=DUAL_FILTER;
	else
	    filterInt=NO_FILTER;
	output.addElement(new Integer(filterInt));
	output.addElement(new Double(sliceSpacing));
	output.addElement(options.getNextBoolean()? Boolean.TRUE: Boolean.FALSE); //interpolate?
	redo=redoLastRun != null && redoLastRun.getState();
	output.addElement(redo?  Boolean.TRUE: Boolean.FALSE);
	if (redo)
	    output.setElementAt(Boolean.FALSE, 3);
	    /*pretend user chose manual selection so won't try to
              auto-outline*/
	output.addElement(new Double(minBrightness));
	output.addElement(new Double(maxBrightness));
	return output;
    }
    protected final String readSavedParameter(BufferedReader savedDataReader, int line, int token)
	throws IOException {
	/*Catchall method for reading saved data.  More specific
	  methods that need a particular piece of this data call it.*/
	String
	    output="";
	StringTokenizer
	    dataFromFile=new StringTokenizer(readSavedSection(savedDataReader, line));
	for (int index=1; index<=token; index++)
	    output=dataFromFile.nextToken();
	return output;
    }
    protected final String readSavedSection(BufferedReader dataFromFile, int line)
	throws IOException {
	/*For use by methods that need all saved data of a given kind
	  at once.  More efficient than repeatedly calling
	  readSavedParameter.*/
//  	BufferedReader
//  	    dataFromFile=new BufferedReader(new FileReader(savedDataFile));
	String
	    output="";
	dataFromFile.mark(savedDataLength+1);
	for (int index=1; index<=line; index++)
	    output=dataFromFile.readLine();
	dataFromFile.reset();
	//dataFromFile.close();
	return output;
    }
    protected void restoreExtendedStructuresInfo(BufferedReader savedDataContents) 
	throws IOException {
	/*This method, not used by SA, should be overridden by
          subclasses that use getExtendedStructureInfo() (along with
          saveSettings()) so that any extra info saved along with last
          run's Structures will be restored with them.*/
    }
    private void restoreSavedStructures(BufferedReader savedDataContents)
	throws IOException {
	StringTokenizer dataFromFile=new StringTokenizer(readSavedSection(savedDataContents, 3));
	while (dataFromFile.hasMoreTokens()) {
	    int nPointsInOutline;
	    int[] axes=new int[8], xCoords, yCoords;
	    for (int index=0; index < 8; index++)
		axes[index]=Integer.parseInt(dataFromFile.nextToken());
	    nPointsInOutline=Integer.parseInt(dataFromFile.nextToken());
	    xCoords=new int[nPointsInOutline];
	    yCoords=new int[nPointsInOutline];
	    for (int index=0; index < nPointsInOutline; index++) {
		xCoords[index]=Integer.parseInt(dataFromFile.nextToken());
		yCoords[index]=Integer.parseInt(dataFromFile.nextToken());
	    }
	    structures.addElement(new Structure(axes, xCoords, yCoords));
	    restoreExtendedStructuresInfo(savedDataContents);
	}
    }
    public void run(String arg) {
	String
	    inputDir,
	    //outputDir,
	    nameBase, /*common element in all stack filenames*/
	    whichFile; /*name+path of file user will open*/
	boolean
	    autoOutline,
	    saveOutline,
	    exitSignalGiven;
	int
	    stackType;
	FileDialog
	    selectionImagePicker,
	    savePathPicker;
	EventDrivenDialog
	    optionsBox;
	File
	    savedDataFile;
	ImagePlus
	    currentStack;

//  	/**/Toolkit.getDefaultToolkit().getSystemEventQueue().push(eq);

	if (arg.equals("describe")) {
	    IJ.showStatus ("SA: (c) AB Software & World Domination");
	    describeThisPlugin();
	    return;	
	}
	if (IJ.getApplet() != null) {
	    /*then SA can't function because ImageJ can't access local machine's files*/
	    IJ.error("You must be running ImageJ as an application to use Structure Autoselector.");
	    return;
	}
	//getting instructions from the user...
	IJ.showStatus ("SA: (c) AB Software & World Domination");
//  	selectionImagePicker=new FileDialog(IJ.getInstance(), "Open file from series.");
//  	selectionImagePicker.show();
//  	whichFile=selectionImagePicker.getDirectory() +
//  	    selectionImagePicker.getFile();
//  	while (selectionImagePicker.getFile()==null ||
//  	       !(new File(whichFile)).exists()) {
//  	    if (selectionImagePicker.getDirectory()==null) {
//  		//user hit Cancel
//  		IJ.showStatus("SA: Canceled!");
//  		return;
//  	    }
//  	    else {//user hit OK but chose nonexistent or no file
//  		IJ.beep();
//  		selectionImagePicker.show();
//  	    }
//  	}
	{ //ask user for a file and check if response is correct:
	    selectionImagePicker=new FileDialog(IJ.getInstance(), "Open file from series.");
	    boolean badFile=false; //user picked no file, or a nonexistent one
	    do {
		selectionImagePicker.show();
		//  selectionImagePicker.dir, selectionImagePicker.file
		whichFile=selectionImagePicker.getDirectory() + selectionImagePicker.getFile();		// path+file name
		if (badFile=(selectionImagePicker.getFile()==null || !(new File(whichFile).exists())))
		    if (selectionImagePicker.getDirectory()==null) { //user clicked cancel:
			IJ.showStatus("Canceled!");
			return;
		    } else //file is wrong even though user hit OK--signal error & try again:
			IJ.beep();
	    } while (badFile);
	}
       	try {
	    stacks=StackSeriesIterator.makeIteratorFor(whichFile, true);
	} catch (Exception e) {
	    IJ.error(e.getMessage() + "\nwhile trying to read stacks.  Sorry, I'll have to exit now.");
	    e.printStackTrace();
	    return;
	}
	//opens image window
	while ((selectionImage=WindowManager.getCurrentImage())==null);
	/*VM might otherwise try to continue before thread opening
	  image window finishes, throwing NullPointerException.*/
//  	if (selectionImage.getType()==ImagePlus.GRAY16 || selectionImage.getType()==ImagePlus.GRAY32) {
//  	    /*Stacks will need to become 8-bit before projecting.
//                Convert this one now (using absolute brightness scale)
//                so user can make 8-bit LUT for whole series in it:*/
//  	    //grayscaleTo8Bits(selectionImage);
//  	    selectionImage.getProcessor().setMinAndMax(0, stacks.getMaxPixelValue());
//  	    selectionImage.getStack().update(selectionImage.getProcessor());
//  	    new StackConverter(selectionImage).convertToGray8();
//  	}
	if (selectionImage.isInvertedLut()) {
	    IJ.setForegroundColor(0, 0, 0); //white
	    IJ.setBackgroundColor(255, 255, 255); //black
	}
	else {
	    IJ.setForegroundColor(255, 255, 255);
	    IJ.setBackgroundColor(0, 0, 0);
	}
	//hopefully --> max visibility of stuff to be drawn on output
	nameBase=stacks.nameBaseOfSeries(selectionImagePicker.getFile());
	inputDir=selectionImagePicker.getDirectory();
	/*Put all files SA produces in same directory?  Existence of series data in it means yes.*/
	savedDataFile=new File(inputDir, makeMacSafeFileName(nameBase, ".data"));
	if (savedDataFile.exists()) 
	    outputDir=inputDir;
	else { //let user choose output directory
	    savePathPicker=new FileDialog(
					  IJ.getInstance(),
					  "Choose directory for saving output.");
	    savePathPicker.setFile(makeMacSafeFileName(nameBase, ".data"));
	    //put something in filename field so Windows will let user hit OK
	    savePathPicker.show();
	    outputDir=savePathPicker.getDirectory();
	    if (outputDir==null) { //user canceled
		IJ.showStatus("SA: Canceled!");
		return;
	    }
	    savedDataFile=new File(outputDir, makeMacSafeFileName(nameBase, ".data"));
	}
	try {
	    savedDataContents=getSavedDataFrom(savedDataFile);
	    //savedDataLength initialized as side effect
	    optionsBox=makeOptionsDialog(savedDataFile, stacks);
	    // waiting for user input inside the dialog
	    usersOrders=readOptions(optionsBox, stacks, outlineExists(savedDataContents));
	}
	catch (Exception e) {
	    IJ.error(e + "\nwhile taking options.\n" +
		     "Sorry, I'll have to exit now.");
	    e.printStackTrace();
	    selectionImage.hide();
	    selectionImage.flush();
	    return;
	}
	if (((Boolean) usersOrders.elementAt(CANCELLED_FLAG__O)).booleanValue()) {
	    //user canceled optionsBox
	    selectionImage.hide();
	    selectionImage.flush();
	    IJ.showStatus("SA: Canceled!");
	    return;
	}
	//apply brightness settings to entire series:
	stacks.setMinPixelValue(((Double) usersOrders.elementAt(MIN_BRIGHTNESS_DOUBLE__O)).intValue());
	stacks.setMaxPixelValue(((Double) usersOrders.elementAt(MAX_BRIGHTNESS_DOUBLE__O)).intValue());
	stacks.set8Bit(true); //they wouldn't project otherwise
	if (selectionImage.getType()==ImagePlus.GRAY16 || selectionImage.getType()==ImagePlus.GRAY32) {
	    /*Stacks will need to become 8-bit before projecting.
              Convert this one now (using absolute brightness scale)
              so user can make 8-bit LUT for whole series in it: */
	    //grayscaleTo8Bits(selectionImage);
	    selectionImage.getProcessor().setMinAndMax(stacks.getMinPixelValue(),
						       stacks.getMaxPixelValue());
	    selectionImage.getStack().update(selectionImage.getProcessor());
	    new StackConverter(selectionImage).convertToGray8();
	}
	// finds the index of the 1st and last stack (members iterationStart, iterationEnd, seriesRange
	stacks.setIterationLimits(((Integer) usersOrders.elementAt(RANGE_START_INT__O)).intValue(),
				  ((Integer) usersOrders.elementAt(RANGE_END_INT__O)).intValue());
	autoOutline=((Boolean) usersOrders.elementAt(AUTO_OUTLINE_FLAG__O)).booleanValue();
	saveOutline=((Boolean) usersOrders.elementAt(SAVE_OUTLINE_FLAG__O)).booleanValue();
	//...making selections on open stack...
//  	if (((Boolean) usersOrders.elementAt(REDO_FLAG__O)).booleanValue()) {
//  	    //user ordered redo of last run
//  	    try {
//  		restoreSavedStructures(savedDataContents);
//  	    } catch (Exception e) {
//  		IJ.showMessage(e + "\nwhile restoring last run's selections.\n" +
//  			 "Sorry, I'll have to exit now.");
//  		e.printStackTrace();
//  		return;
//  	    }
//  	}
//  	else if (takeSelections(autoOutline)) {
	// user selects the outline and the 2 axes
	if (doSelecting(((Boolean) usersOrders.elementAt(REDO_FLAG__O)).booleanValue(), autoOutline)) {
	    selectionImage.hide();
	    selectionImage.flush();
	    IJ.showStatus("SA: Canceled!");
	    return; //user signaled to quit plugin
	}
	if (autoOutline) {
	    try {
		drawOutlines(savedDataContents);
	    }
	    catch (Exception e) {
		IJ.error(e + "\nwhile trying to read saved outline.\n" +
			 "Sorry, I'll have to exit now.");
		selectionImage.hide();
		selectionImage.flush();
		e.printStackTrace();
		return;
	    }
	}
	//saving user's settings
	try {
	    saveSettings(savedDataFile);
	}
	catch (Exception e) {
	    File settingsBackup=new File(outputDir, makeMacSafeFileName(nameBase, ".temp"));
	    if (settingsBackup.exists())
		settingsBackup.renameTo(savedDataFile);
				//restore savedDataFile from backup
	    IJ.error(e + "\nwhile trying to save your settings.\n" +
		     "Sorry, I'll have to exit now.");
	    e.printStackTrace();
	    return;
	}
	finally {
	    if (savedDataContents != null)
		try {
		    savedDataContents.close();
		}
		catch (IOException e) {
		    System.exit(e.getClass().hashCode());
		    //only remaining way to make JVM quit hogging the file
		}
	}
//  	desiredBrightnessMin=selectionImage.getProcessor().getMin();
//  	desiredBrightnessMax=selectionImage.getProcessor().getMax();
//  	desiredColorModel=selectionImage.getStack().getColorModel();
	makeSelectionsKey(outputDir, nameBase);
	//...and applying selections to the rest of the stack series.
	selectionImage.hide();
	selectionImage.flush();
	String outputNameBase=makeMacSafeNameBase(stacks, "_Cs"+structures.size()+"t", ".mov");
	/*want to ensure all stacks produced have same namebase--that
          of stack series showing last structure*/
	IJ.showMessage("Some advice before I process your stacks:",
		       "1) If you chose new LUT settings for the current stack, they will not\n" +
		       "   be visible in the output while it's under construction.\n\n\n" +
		       "2) Output files:\n" +
		       "   filename_CsNtX.mov: cropped stack, selection N at time (=stack #) X\n" +
		       "   filename_PsNtX.mov: 3D projection, selection N at time X\n\n\n" +
		       "<<< That's all--Click OK to continue >>>"); //FIXME: find less annoying way to say this

	while (stacks.hasMoreElements()) {
	    //show progress thru series in IJ window title, other methods would erase it if shown in status/progress bar:
	    IJ.getInstance().setTitle("ImageJ - stack " + (stacks.whichStack()-stacks.getIterationStart()+1) + '/'
				      + (stacks.getIterationEnd()+1 - stacks.getIterationStart())
				      +  ':'); //let processTimePoint fill in progress w/in this stack later
	    Undo.reset();
	    System.gc();
	    (currentStack=(ImagePlus) stacks.nextElement()).show();
//  	    if (currentStack.getType()==ImagePlus.GRAY16 || currentStack.getType()==ImagePlus.GRAY32) {
//  		//switch to projectable type
//  		//IJ.run("8-bit");
//  		currentStack.getProcessor().setMinAndMax(0, stacks.getMaxPixelValue());
//  		currentStack.getStack().update(currentStack.getProcessor());
//  		new StackConverter(currentStack).convertToGray8();
//  		currentStack=WindowManager.getCurrentImage();
//  	    } <--already done by stacks.nextElement()
	    //set LUT to match that defined by user:
	    currentStack.getStack().setColorModel(desiredColorModel);
	    //set brightness/contrast to match that defined by user:
	    for (int index=1; index <= currentStack.getStackSize(); index++) {
		ImageProcessor slice=currentStack.getStack().getProcessor(index);
		slice.setMinAndMax(desiredBrightnessMin, desiredBrightnessMax);
		if (slice instanceof ByteProcessor)
		    ((ByteProcessor) slice).applyLut();
		/*easiest way to make sure altered LUT survives processing--but not needed or possible w/RGB images*/
	    }
	    try {
		processTimePoint(currentStack, stacks.whichStack()-1, outputDir, outputNameBase);
	    /*whichStack() returns the next one up for
	      processing-- but processTimePoint() wants
	      the number of this one*/
	    }
	    catch (Exception e) {
		DialogFiller.endUse();
		IJ.showMessage(e + "\nwhile trying to process Stack #" + (stacks.whichStack()-1)
			       + ".\nSorry, I'll have to exit now.");
		e.printStackTrace();
		IJ.getInstance().setTitle("ImageJ"); //otherwise it'll be stuck w/the status message forever
		return;
	    }
	    if (currentStack != null) {//just in case
		currentStack.hide();
		currentStack.flush();
	    }
	}
	//set IJ window title back to normal:
	IJ.getInstance().setTitle("ImageJ");
	for (int index = 0; index < 3; index++)
	    IJ.beep();
    }
    private void saveSettings(File settingsFile)
	throws IOException {
	/*Updates settingsFile to reflect the new settings from this
          run.  Actual updated contents of settingsFile derived by
          calling updateSettingsFile, which is what subclasses should
          override.*/
	BufferedWriter
	    settingsSaver;
	StringBuffer
	    settings;
  	String
  	    output/*=""*/;
	File
	    settingsBackup=new File(outputDir,
				    settingsFile.getName().substring(0,settingsFile.getName().lastIndexOf('.'))
				    + ".temp");
	settings=new StringBuffer();
	if (savedDataContents != null) { //settingsFile exists, so:
	    settingsFile.renameTo(settingsBackup);
	    //if this method throws an exception before saving new data, old data won't be lost
	    savedDataContents.mark(savedDataLength+1);
	    String line=savedDataContents.readLine();
	    while (line != null) {
		settings.append(line).append("\n");
		line=savedDataContents.readLine();
	    }
	    savedDataContents.reset();
	}
	settingsSaver=new BufferedWriter(new FileWriter(settingsFile));
	updateSettingsFile(settings);
	output=new String(settings);
	settingsSaver.write(output, 0, output.length());
	settingsSaver.flush();
	settingsSaver.close();
	//if this line is reached, the new data was saved correctly, so no need for the backup:
	if (savedDataContents != null)
	    settingsBackup.delete();
    }
    protected final int skipSetting(StringBuffer settings, int settingsPtr, String delim) {
	/*Returns the index where the token (defined in terms of
          delim) after the one that begins at index settingsPtr will
          be.  If settings ends before the current token ends (or
          begins), this method first appends delim to settings to mark
          the end of the token.  Note that the null substring
          "between" 2 adjacent appearances of delim, or before delim
          if it appears at the beginning of settings, also counts as a
          token.

	  This method is designed so that, using last run's return
	  value as this run's settingsPtr argument, you can iterate
	  over a series of consecutive tokens one by one (creating new
	  ones at the end of settings if necessary).  If last run used
	  a different value of delim than this run, however, the
	  tokens returned by the successive runs may overlap or be
	  separated by extra characters--use caution.*/
	int nextDelimIndex=settings.toString().indexOf(delim, settingsPtr);
	if (settings.length()-1 < settingsPtr ||
	    nextDelimIndex < 0) {
	    settings.append(delim);
	    return settings.length();
	}
	else {
	    return (nextDelimIndex + delim.length());
	}
    }
    private String stringifyToNDecimalDigits(int theNumber, int nDigits) {
	/*Returns a string representation of theNumber, base 10, with
          leading zeroes as needed to expand the length to nDigits.*/
	String returnValue=Integer.toString(theNumber);
	while (returnValue.length() < nDigits)
	    returnValue="0" + returnValue;
	return returnValue;
    }
    private PolygonRoi takeOutline(boolean autoOutline) {
	/*Sets up tools for user to draw a structure's outline and
	  returns it, if one is to be drawn.  Otherwise does nothing.*/
	PolygonRoi
	    output;
	
	selectionImage.killRoi(); //if one pre-existed
	if (autoOutline)
	    return null;
	else {
	    Toolbar.getInstance().setTool(Toolbar.POLYGON);
	    while ((selectionImage.getRoi()==null) ||
		   (selectionImage.getRoi().getState()==Roi.CONSTRUCTING)) 
		if (IJ.spaceBarDown())	{
		    IJ.setKeyUp(KeyEvent.VK_SPACE);
		    return null;
		}
	    output=(PolygonRoi) selectionImage.getRoi();	
	    selectionImage.unlock(); //otherwise next line crashes plugin
	    IJ.run("Draw");
	    selectionImage.killRoi();
	    Toolbar.getInstance().setTool(Toolbar.LINE); //for drawing axes after method returns
	    return output;
	}
    }
    private boolean takeSelections(boolean autoOutline) { //FIXME: this method is nonfinal why?
	/*Sets up tools for user to select structures, and records
	  selections.  Returns true if user signals to exit this method
	  without selecting anything. */

	PolygonRoi
	    outline;
	Line
	    axis1, axis2;
//  	Dimension screenSize=Toolkit.getDefaultToolkit().getScreenSize();
//  	Frame directions=makeDirectionsWindow(autoOutline);
//  	ImageWindow selectionWindow;
//  	selectionImage=IJ.getImage();
//  	selectionWindow=selectionImage.getWindow();
//  	//center selection and directions windows side-to-side:
//  	while (directions.getWidth() + selectionWindow.getWidth() > screenSize.width) {
//  	    selectionWindow.getCanvas().zoomOut(0, 0);
//  	    selectionWindow.pack();
//  	}
//  	directions.setLocation((screenSize.width - selectionWindow.getWidth() - directions.getWidth())/2,
//  			       (screenSize.height - directions.getHeight())/2);
//  	selectionWindow.setLocation(directions.getX() + directions.getWidth(),
//  				    (screenSize.height - selectionWindow.getHeight())/2);
//  	directions.show();
//  	WindowManager.addWindow(directions);
//  	WindowManager.setWindow(selectionWindow);
  	Toolbar.getInstance().setTool(Toolbar.LINE);
	while (true) { //loop only exited by method returning
	    outline=takeOutline(autoOutline);
	    //takeOutline() handles tool switching
	    if ((outline==null) && !(autoOutline)) {
//  		directions.dispose();
//  		WindowManager.removeWindow(directions);
		return (structures.size()==0? true: false);
	    }
	    /*When autoOutline is false, takeOutline() only returns
	      null if the user signaled to exit while it was
	      running.*/
	    axis1=getAxisSafely(autoOutline);
	    if (axis1==null) { //other possible exit signal
//  		directions.dispose();
//  		WindowManager.removeWindow(directions);
		return (structures.size()==0?
			true:
			false);
	    }
	    axis2=getAxisSafely(false);
	    //Now have enough data to make the Structure, so:
	    if (autoOutline)
		structures.addElement(new Structure(
						    new int[] {axis1.x1, axis1.y1, axis1.x2, axis1.y2,
							       axis2.x1, axis2.y1, axis2.x2, axis2.y2}
						    )); //no outline data to put in the new Structure
	    else
		structures.addElement(new Structure(
						    new int[] {axis1.x1, axis1.y1, axis1.x2, axis1.y2,
							       axis2.x1, axis2.y1, axis2.x2, axis2.y2},
						    outline.getXCoordinates(), outline.getYCoordinates(), 
						    outline.getBoundingRect().x, outline.getBoundingRect().y,
						    outline.getNCoordinates()
						    ));
	    getExtendedStructureInfo();
	    /*chance for subclasses to collect whatever additional
              info they need--no args, they've got all data about the
              recent selection in variables*/
	}
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, byte newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	return updateSetting(settings, settingsPtr, new Byte(newSetting), delim);
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, char newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	if (settings.length()-1 < settingsPtr) {
	    settings.append(newSetting).append(delim);
	    return settings.length();
	}
	else {
	    settings.deleteCharAt(settingsPtr);
	    settings.insert(settingsPtr, newSetting);
	    return (settingsPtr+1+delim.length());
	    /*shortcut possible because the replacement token must be
              1 character long*/
	}
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, double newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	return updateSetting(settings, settingsPtr, new Double(newSetting), delim);
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, float newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	return updateSetting(settings, settingsPtr, new Float(newSetting), delim);
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, int newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	return updateSetting(settings, settingsPtr, new Integer(newSetting), delim);
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, long newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	return updateSetting(settings, settingsPtr, new Long(newSetting), delim);
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, Object newSetting, String delim) {
	/*Does what skipSetting does, but replaces the skipped-over
          token with newSetting.toString().  If settings ends before
          index settingsPtr, appends newSetting.toString() as well as
          the trailing delim from skipSetting() before finding the
          return value.*/
	int nextDelimIndex=settings.toString().indexOf(delim, settingsPtr);
	/*delim should be short enough that using KMP or such (-->
	  instantiating array) gains no efficiency over
	  instantiating string to call brute-force indexOf() on*/
	String stringifiedNewSetting=newSetting.toString();
	if (settings.length()-1 < settingsPtr || nextDelimIndex < 0) {
	    settings.append(stringifiedNewSetting).append(delim);
	    return settings.length();
	}
	else {
	    settings.delete(settingsPtr, nextDelimIndex);
	    settings.insert(settingsPtr, newSetting);
	    //return (nextDelimIndex + delim.length());
	    return settingsPtr + stringifiedNewSetting.length() + delim.length();
	    //index of character after end of delim following insertion
	}
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, short newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	return updateSetting(settings, settingsPtr, new Short(newSetting), delim);
    }
    protected final int updateSetting(StringBuffer settings, int settingsPtr, boolean newSetting, String delim) {
	//Does what updateSetting(StringBuffer, int, Object, String) does.
	return updateSetting(settings, settingsPtr, (newSetting?  Boolean.TRUE: Boolean.FALSE), delim);
    }
    protected int updateSettingsFile(StringBuffer settings) throws IOException {
	/*settings holds the contents of any existing settings file
	  for this series of stacks.  This method updates it with the
	  settings to be saved this time, for writing back to the file
	  by code elsewhere.  The return value is the index in
	  settings of the first character of the first line not
	  updated by this method.

	  File format: Stringified numbers and booleans separated by
	  spaces.
	 
	  --First line: General information from SA itself about this
	  stack: file type, series range (2 numbers), whether
	  filenumbers have leading zeroes, minimum number of digits in
	  filenumbers, whether user wanted an outline saved this run,
	  whether a saved outline exists at all, whether user wanted
	  auto-outlining this run, what if any noise-filtering method
	  to use

	  --Second line: [first outline drawn].toString() if
	  autoOutline selected, else blank (for auto-outlining)
	  

	  --Third line: stringified selections (for quick redo)

	  --Line(s) 4 to N: Information specific to plugins extending
	  SA

	  Any method that overrides this one should call it first, and
	  should place its data on an arbitrary block of lines
	  beginning with the subclass's name rather than a fixed line
	  number, otherwise multiple subclasses may disagree over
	  whose data is whose.*/
	boolean
	    saveOutline=((Boolean) usersOrders.elementAt(SAVE_OUTLINE_FLAG__O)).booleanValue(),
	    autoOutline=((Boolean) usersOrders.elementAt(AUTO_OUTLINE_FLAG__O)).booleanValue(),
	    interpolate=((Boolean) usersOrders.elementAt(INTERPOLATION_FLAG__O)).booleanValue();
	int
	    noiseFilter=((Integer) usersOrders.elementAt(NOISE_FILTER_INT__O)).intValue(),
	    settingsPtr=0; //index of first character in settings not yet dealt with
	double
	    sliceSpacing=((Double) usersOrders.elementAt(SLICE_SPACING_DOUBLE__O)).doubleValue(),
		minBrightness=((Double) usersOrders.elementAt(MIN_BRIGHTNESS_DOUBLE__O)).doubleValue(),
		maxBrightness=((Double) usersOrders.elementAt(MAX_BRIGHTNESS_DOUBLE__O)).doubleValue();
	settingsPtr=updateSetting(settings, settingsPtr, stacks.reportStackType(), " ");
	settingsPtr=updateSetting(settings, settingsPtr,
				   ((Integer) usersOrders.elementAt(RANGE_START_INT__O)).intValue(), " ");
	/*run() already has these as local variables, but it's easier to
	  take them from usersOrders than to pass them here as args*/
	settingsPtr=updateSetting(settings, settingsPtr,
				   ((Integer) usersOrders.elementAt(RANGE_END_INT__O)).intValue(), " ");
	settingsPtr=updateSetting(settings, settingsPtr, saveOutline, " ");
	settingsPtr=updateSetting(settings, settingsPtr,
				   saveOutline || (savedDataContents != null && outlineExists(savedDataContents)),
				   " ");
	/*If either side of the || is true, a saved outline will be
          available next run*/
	settingsPtr=updateSetting(settings, settingsPtr, autoOutline, " ");
	settingsPtr=updateSetting(settings, settingsPtr, noiseFilter, " ");
	settingsPtr=updateSetting(settings, settingsPtr, interpolate, " ");
	settingsPtr=updateSetting(settings, settingsPtr, sliceSpacing, " ");
	settingsPtr=updateSetting(settings, settingsPtr, minBrightness, " ");
	settingsPtr=updateSetting(settings, settingsPtr, maxBrightness, " ");
	settingsPtr=skipSetting(settings, settingsPtr, "\n");
	/*next token to update delimited by "\n"--make sure it doesn't
          overlap last one*/
	if (saveOutline)
	    settingsPtr=updateSetting(settings, settingsPtr, structures.elementAt(0), "\n");
	else {
	    /*keep old outline, if any, since user didn't provide a replacement*/
	    settingsPtr=skipSetting(settings, settingsPtr, "\n");
	}
	//save *all* outlines for redo:
	{
	    StringBuffer redoBuffer=new StringBuffer();
	    for (int index=0; index < structures.size(); index++) {
		redoBuffer.append(structures.elementAt(index)).append(" ");
	    }
	    settingsPtr=updateSetting(settings, settingsPtr, redoBuffer, "\n");
	}
	return settingsPtr;
    }
    private int[] userSeriesRange(BufferedReader savedDataContents)
	throws IOException {
	//which stacks user wants to process
	if (savedDataContents != null)
	    return new int[] {
		Integer.parseInt(readSavedParameter(savedDataContents,
						    RANGE_INTS__F[LINE],
						    RANGE_INTS__F[FIRST_TOKEN])),
		Integer.parseInt(readSavedParameter(savedDataContents,
						    RANGE_INTS__F[LINE],
						    RANGE_INTS__F[LAST_TOKEN])),
	    };
	else
	    return new int[] {stacks.getSeriesRange()[0],
			      stacks.getSeriesRange()[1]}; 
    }
}
