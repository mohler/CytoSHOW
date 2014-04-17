package gloworm;

import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.awt.image.ImageObserver;
import ij.process.*;

public abstract class StackSeriesIterator implements Enumeration { //not really an Iterator for historical reasons
    /*Opens (and returns from its nextElement method) one ImagePlus at
      a time, in order, from a numbered series of image files (all the
      same type) containing stacks.  Does any filename manipulation,
      file type detection and calls to file-opening methods necessary
      to make this happen.  Also provides various info about the
      stacks which, for some types of image file, can be derived from
      data stored in that file.*/

    //FIXME: API needs total cleanup, and service-provider framework needs installed for dealing w/subclasses.  Also add open as 8-bit method

    public static final int UNKNOWN_VALUE=-1;
    private int
	iterationStart=-1, //needs lazily initialized
	iterationEnd=-1,
	/*first and last stacks user wants processed--compare with
		seriesRange*/
	currentStackNumber=-1; //this stack will be returned by nextElement()
    private double minPixel=0, maxPixel;
    private boolean make8Bit;
    protected int digitsInStackNumbers;
	/*A minimum.  If greater than the number of digits in
	  currentStackNumber, the filename must contain leading zeroes
	  to make up the difference. Nonfinal as an implementation
	  side effect and set by constructor to its correct
	  value--DON'T CHANGE IT.*/
    protected double
	sliceSpacing=UNKNOWN_VALUE,
	zVoxelInMicrons=UNKNOWN_VALUE;
    private String
	nameBase,
	stacksPath,
	extension;
    private int[] seriesRange; //first and last stacks existing in series
    protected final ImagePlus exampleStack;
    //Arg to constructor.  Various range-checking methods need it here for reference.
		
    public static final int 
	//stack types:
	STANDARD=0, //stack types that ImageJ can open with included or no plugin
	OLYMPUS_MULTI_TIFF=1,
	ZEISS_LSM=2,
	BIORAD_PIC=3,
	ULTRAVIEW=4;
    
    protected static interface ZoomCorrectable {
	/*Some image file formats store measurements that will be
          incorrect if the user changed the zoom factor without the
          acquisition software knowing.  This interface declares a
          method for correcting a StackSeriesIterator's internal
          representation of these measurements.*/
	public void correctZoom(int zoomFactor) throws RuntimeException;
	//if zoom data in a file that should have some is corrupt
    }
    //FIXME: uncomment the following. public abstract boolean testStack(File f); //or arg could be 
	
    StackSeriesIterator(ImagePlus exampleStack) {
	//throws NumberFormatException to preserve class invariants--see below
	if (getStackNumberRadix() < Character.MIN_RADIX || getStackNumberRadix() > Character.MAX_RADIX)
	    throw new NumberFormatException("Stack number radix " + getStackNumberRadix() + " out of bounds");
	//throw it now when it's obvious, don't wait until stackname <--> stack# conversion methods do it
	this.exampleStack=exampleStack;
	//otherwise subclass constructor needs to open the stack itself
	analyzeFileInfo(exampleStack); //FIXME: delete method, move contents into constructor
    }
    private void analyzeFileInfo(ImagePlus exampleStack) {
	/*Gets file info from exampleStack.  Does most of the constructor's work.*/
	FileInfo exampleInfo=exampleStack.getOriginalFileInfo();
	String fileName=exampleInfo.fileName;
	setMaxPixelValue(getDefaultMaxPixelValue()); //now that we have exampleStack
	stacksPath=exampleInfo.directory;
	nameBase=nameBaseOfSeries(fileName);
	extension=fileName.substring(fileName.lastIndexOf("."), fileName.length());
	/*It would be appropriate to initialize seriesRange next but
          that involves overrideable methods in ways that constructor
          chaining screws up.  Do it lazily instead.*/
    }
    protected synchronized final void assertStackIsIterated(int n) {
	//throws exception if this iterator doesn't open stack #n
	if (n < iterationStart || n > iterationEnd)
	    throw new IllegalArgumentException(this + " does not iterate over a stack #" + n);
    }
    public final boolean canCorrectZoom() {
	return this instanceof ZoomCorrectable;
    }
    private double getDefaultMaxPixelValue() { //FIXME: either make final or remove call from constructor.
	/*Returns the brightest possible value for a pixel (if
	  grayscale or indexed color) as fixed by the bit depth of the
	  image stacks.  The brightest pixel is assumed to be
	  Float.MAX_VALUE for a floating-point stack.

	  This implementation assumes that an image file has the same
	  bit depth as the ImagePlus created by opening it.
	  Subclasses that open image files at other than their true
	  bit depth should override this method. */
	switch (exampleStack.getType()) {
	case ImagePlus.COLOR_256: case ImagePlus.GRAY8: case ImagePlus.COLOR_RGB: return 255;
	case ImagePlus.GRAY16: return 65535;
	default: return Float.MAX_VALUE;
	}
    }
    public final String getDirectory() {
	/*Returns the full path of the directory containing the stacks
          in this series.*/
	return stacksPath;
    }
    public synchronized final int getIterationEnd() {
	/*Returns number of last stack that user wants processed (as
          opposed to the last stack that exists, which the seriesRange
          method returns).

	  If no iteration range is set already, defaults to
	  getSeriesRange()[1].*/

	if (iterationEnd < 0) {//unset, use default
	    seriesRange=getSeriesRange();
	    setIterationLimits(seriesRange[0], seriesRange[1]);
	}
	return iterationEnd;
    }
    public synchronized final int getIterationStart() {
	/*Returns number of first stack that user wants processed (as
          opposed to the first stack that exists, which the
          seriesRange methods return).

	  If no iteration range is set already, defaults to
	  getSeriesRange()[0].

	  I have no idea why this is done in 2 int methods instead of
	  1 int[2] method.*/
	if (iterationStart < 0) { //unset, make default
	    seriesRange=getSeriesRange();
	    setIterationLimits(seriesRange[0], seriesRange[1]);
	}
	return iterationStart;
    }
    public synchronized final double getMaxPixelValue() {
	/*Returns the brightest value a pixel in this series (or a
          non-alpha channel of one) may have,
          regardless of the image format's capabilities.

	  The default value is the brightest pixel possible for the
	  type of ImagePlus returned by openStack().  If an image
	  format does not allow a pixel that bright, the subclass
	  dealing with that format should set the true maximum in
	  every constructor with setMaxPixelValue(). */
	return maxPixel;
    }
    public synchronized final double getMinPixelValue() {
	/*Returns the dimmest value a pixel in this series may have,
          regardless of the image format's capabilities.

	  The default value is 0.  If an image format does not allow a
	  pixel that dim, the subclass dealing with that format should
	  set the true minimum in every constructor with
	  setMinPixelValue(). */
	return minPixel;
    }
    public final String getNameBase() {
	/*Returns the common element in filenames of all stacks in
          this series.*/
	return nameBase;
    }
    public double getNSecondsBetweenStacks(int n1, int n2) {
	/*Returns elapsed time between creations of stack numbered n1
          and stack numbered n2.  This implementation just subtracts
          the times they were last modified; override if your file
          format offers some other way to get this information. */
	File stack1, stack2;
	//assertStackIsIterated(n1);
	//assertStackIsIterated(n2);
	//too restrictive--using e.g. stack #n1 as a reference stack doesn't mean we want to iterate it
	stack1=new File(stacksPath, makeFileName(n1));
	stack2=new File(stacksPath, makeFileName(n2));
	long modTime1=stack1.lastModified(), modTime2=stack2.lastModified();
	if (modTime1==0) //hope this file wasn't actually created at midnight, Jan. 1, 1970:
	    throw new IllegalArgumentException(stack1 + " missing or unreadable");
	else if (modTime2==0)
	    throw new IllegalArgumentException(stack2 + " missing or unreadable");
	else return (modTime2 - modTime1)/1000;
    }
    public synchronized final int[] getSeriesRange() {
	/*Returns array having first stack # existing in series at
	  index 0, last stack # existing in series at index 1.  Also
	  sets digitsInStackNumbers to correct value as a side
	  effect.*/
	if (seriesRange != null)
	    return seriesRange;
	/*else lazily initialize (by process involving overrideable
	  methods in ways constructor shouldn't):*/
	String fileName=exampleStack.getOriginalFileInfo().fileName,
	    pilotStackNumber=stackNumberString(fileName);
	int[]
	    seriesRange=new int[2];
	    
	File
	    fileToCheck;
	boolean
	    fileNumbersHaveLeadingZeroes=pilotStackNumber.charAt(0)=='0';
	//value always errs on the side of false

	digitsInStackNumbers=pilotStackNumber.length();
	seriesRange[0]=seriesRange[1]=Integer.parseInt(pilotStackNumber, getStackNumberRadix());
	if (seriesRange[0] > 0)  {
	    do {
		if (seriesRange[0] < 0) break; //the file can't exist so don't bother checking
		fileToCheck=new File(stacksPath, makeFileName(--seriesRange[0]));
		if (!fileNumbersHaveLeadingZeroes && isWholePowerOf(seriesRange[0] + 1, getStackNumberRadix())) {
		    /*then seriesRange[0]'s loss of a digit in last
		      decrement causes ambiguity: if file # seriesRange[0]
		      exists does it have a leading zero in place of that
		      digit?*/
		    if (fileToCheck.exists()) {
			//now we know: this series *does* use leading zeroes
			fileNumbersHaveLeadingZeroes=true;
			continue; //don't bother rechecking at bottom of loop
		    }
		    else {
			//we may be looking for e.g. file09 when we should be looking for file9
			digitsInStackNumbers--;
			fileToCheck=new File(stacksPath, makeFileName(seriesRange[0]));
		    }
		}
		if (!fileToCheck.exists())
		    break;
	    } while (true);
	    //it's spaghetti code, but it does save redundant disk accesses to check file existence
	    seriesRange[0]++;
	}
	//loop exits leaving seriesRange[0] 1 less than last valid filenumber
	do
	    fileToCheck=new File(stacksPath, makeFileName(++seriesRange[1]));
	while (fileToCheck.exists());
	seriesRange[1]--;
	/*loop exits leaving seriesRange[1] 1 more than last valid
	  filenumber*/
	return seriesRange;
    }
    public final double getSliceSpacing() {
	return sliceSpacing;
    }
    protected int getStackNumberRadix() {
	/*Returns the radix of the stacks' numbers as they appear in
          the filenames.  This default implementation returns 10
          because most types of stack series are numbered in decimal.
          If yours isn't, override to return a compile-time constant
          between Character.MAX_RADIX and Character.MIN_RADIX.
          (Returning a non-constant value should be unnecessary, and
          may cause errors when the constructor calls this method.) */
	return 10;
    } //should be a final static variable except needs to be overrideable
    public final double getZVoxelInMicrons() {
	return zVoxelInMicrons;
    }
    public synchronized final boolean hasMoreElements() {
	/*If the iteration range is unset, it's assumed that no
          elements have been iterated yet, so true is returned.*/
	return (whichStack() <= iterationEnd); //initialize range to default
    }
    private static boolean hasNumericExtension(String whichFile, int radix) {
	/*Returns true if the file's extension consists of all numbers.*/
	boolean returnValue=true;
	for (int index=whichFile.indexOf('.')+1; returnValue && index < whichFile.length(); index++)
	    returnValue=(Character.digit(whichFile.charAt(index), radix) != -1);
	return returnValue;
    }
    protected final boolean isDigitInRadix(char c, int radix) {
	/*Returns true if c is a valid digit for numbers represented
          in the given radix.  If radix > 10, valid digits may be
          uppercase or lowercase.*/
	return Character.digit(c, radix) >= 0;
    }
//      private boolean is10ToPositiveInteger(int n) {
//  	/*Returns true iff log(n) base 10 is a positive integer.*/
//  	if ((n > 0) && (n % 10 == 0))
//  	    return (n==10 || is10ToPositiveInteger(n/10));
//  	/*the stack is in no danger from the recursion--# calls is about 10 for largest int*/
//  	else
//  	    return false;
//      }

    private static boolean isWholePowerOf(int n, int radix) {
	/*Returns whether log(n) base radix is a positive integer. */
	if ((n > 0) && (n % radix == 0))
	    return (n==radix || isWholePowerOf(n/radix, radix));
	else return false;
    }
    protected String makeFileName(int fileNumber) {
	/*Returns the full filename of a stack, given the numerical
          part of that name.  This default implementation assumes the
          file number goes between the common element in all filenames
          and the extension; override if it doesn't.*/
	return nameBase + stringifyToNDigits(fileNumber, digitsInStackNumbers) + extension;
    }
    public static StackSeriesIterator makeIteratorFor(String whichFile, boolean show) 
	throws IOException { //FIXME: leave what follows to subclasses, and invoke it from here in generalizable way
	/*Creates and returns a StackSeriesIterator of the proper type
	  to open the file whose name is given.  If show is true,
	  opens the file and shows the image in it.  An IOException is
	  thrown if whichFile does not exist or is unreadable; an
	  IllegalArgumentException is thrown if the file is not a
	  stack of known type from a series.*/
 
	StackSeriesIterator output=null;
	Opener testOpener=new Opener();
	ImagePlus selectionImage;

	ij.gui.ImageWindow.centerNextImage();
	if (!Character.isDigit(whichFile.charAt(whichFile.lastIndexOf(".")-1)))
	    /*part of filename before extension does not end in a number*/
	    throw new IllegalArgumentException("\n" + whichFile + " is not a stack from a series.");
	else if (whichFile.toLowerCase().endsWith(".lsm"))
	    output=new LsmSeriesIterator(testOpener.openImage(whichFile));
	/*testOpener can't open the stack correctly, but it will make sure
	  the constructor gets the proper FileInfo.  The constructor then opens
	  the LSM file for real.*/
	else if (whichFile.toLowerCase().endsWith(".stk")) //it's otherwise a normal TIFF
	    output=new MetamorphSeriesIterator(testOpener.openImage(whichFile));
	else if ((selectionImage=testOpener.openImage(whichFile)) != null) {
	    output=new StandardSeriesIterator(selectionImage);
	}
	else if (hasNumericExtension(whichFile, 16)) { //see if it's an Ultraview file
		File ultraviewFile;
		RandomAccessFile ultraviewRandomAccessFile=new RandomAccessFile(ultraviewFile=new File(whichFile), "r");
		int magicNumber=ultraviewRandomAccessFile.read() | (ultraviewRandomAccessFile.read() << 8);
		//little-endian
		if (magicNumber==3248) {
		    //the file is an Ultraview, open accordingly
		    ImagePlus imp=new ImagePlus();
		    //UltraviewSeriesIterator constructor doesn't need the picture
		    FileInfo fi=new FileInfo();
		    fi.width=ultraviewRandomAccessFile.read() | (ultraviewRandomAccessFile.read() << 8);
		    fi.height=ultraviewRandomAccessFile.read()| (ultraviewRandomAccessFile.read() << 8);
		    ultraviewRandomAccessFile.close();
		    fi.fileName=ultraviewFile.getName();
		    fi.directory=ultraviewFile.getParent() + File.separatorChar /*which File.getParent() strips*/;
		    imp.setFileInfo(fi);
		    output=new UltraviewSeriesIterator(imp);
		}
	}
	//...handling of other stack types...
	if (output==null)
	    throw new IllegalArgumentException(whichFile + " is not a known stack type");
	else if (show)
	    output.openStack(Integer.parseInt(output.stackNumberString(whichFile.substring(whichFile.lastIndexOf(File.separatorChar)+1,
											   whichFile.length())), 
					      output.getStackNumberRadix())).show();
	return output;
    }
    public String nameBaseOfSeries(String fileName) {
	/*Returns the common element in all filenames from a stack
	  series, given one such filename.  This default
	  implementation assumes the common element is whatever comes
	  before the final block of numbers in the filename (before
	  the extension); if it isn't, override in a way that does not
	  rely on any initialization done by the constructor.*/
	int
	    indexNumbersStartAfter;
	fileName=fileName.substring(0, fileName.lastIndexOf("."));
	for (indexNumbersStartAfter=fileName.length()-1;
	     Character.isDigit(fileName.charAt(indexNumbersStartAfter));
	     indexNumbersStartAfter--);
	/*Loop stops decrementing indexNumbersStartAfter once it's indexed last nondigit
		in Filename and thus has 1 less than the desired value.*/
	return fileName.substring(0, indexNumbersStartAfter+1);
    }
    public synchronized final Object nextElement() {
	/*Merely returns the stack--showing it is up to the method
          that wanted it. */
	ImagePlus output;
	if (!hasMoreElements())
	    throw new NoSuchElementException("No more stacks to open.");
	output=openStack(currentStackNumber++);
	output.getProcessor().setMinAndMax(getMinPixelValue(), getMaxPixelValue());
	output.getStack().update(output.getProcessor());
	if (make8Bit && (output.getType()==ImagePlus.GRAY16 || output.getType()==ImagePlus.GRAY32))
	    new StackConverter(output).convertToGray8();
	return output;
    }
    /*Do not call this method, it's only accessible so it can be
      overridden--use nextElement() instead.  If this method returns
      an ImagePlus of different bit depth than the image file it came
      from, override getDefaultMaxPixelValue().

      Calling this method should not cause the returned ImagePlus to
      show onscreen. */
    protected abstract ImagePlus openStack(int whichStack);
    public abstract int reportStackType(); //FIXME: should probably delete this
    public synchronized final void set8Bit(boolean make8Bit) {
	/*If true, grayscale stacks will be converted to 8-bit (color
          stacks will be unaffected). Default: false. */
	this.make8Bit=make8Bit;
    }
    public synchronized final void setIterationLimits(int iterationStart, int iterationEnd) {
	/*Sets the numbers of the first and last stacks to be
	  iterated, inclusive.  Not called in the constructor because
	  the user has to input the args at an arbitrary stage in
	  plugin execution.
	  Exceptions: IllegalArgumentException if iterationStart >
	  iterationEnd or either is outside series range.*/
	this.seriesRange=getSeriesRange();
	/*Force initialization of this.seriesRange.  It's wrong if
          whatever called this method didn't do it already in checking
          args before passing them--but better let it slide than
          complain.*/
	if (iterationStart > iterationEnd)
	    throw new IllegalArgumentException("Can't iterate backward: stack #" + iterationStart + " to " + iterationEnd);
	else if (iterationStart < seriesRange[0])
	    throw new IllegalArgumentException("Can't begin with stack #" + iterationStart + ", they start at " + seriesRange[0]);
	else if (iterationEnd > seriesRange[1])
	    throw new IllegalArgumentException("Can't end with stack #" + iterationStart + ", they stop at " + seriesRange[1]);
	this.iterationStart=iterationStart;
	this.iterationEnd=iterationEnd;
	currentStackNumber=iterationStart;
    }
    public synchronized final void setMaxPixelValue(double maxPixel) {
	/*Sets the brightest value a pixel in this series may have,
          regardless of the image format's capabilities. */
	this.maxPixel=maxPixel; //avoid an impossible value
    }
    public synchronized final void setMinPixelValue(double minPixel) {
	/*Sets the brightest value a pixel in this series may have. */
	this.minPixel=(minPixel < 0? 0: minPixel);
    }
    protected String stackNumberString(String fileName) {
	/*Returns the string, including leading zeroes, that
          represents the number of the stack fileName names.  This
          default implementation assumes that the string is the last
          block of digits (which may be uppercase or lowercase if the
          stack number is e.g. hexadecimal) before the extension.
          Should be OK for most series with 1 file per stack.*/
	int firstDigit,  //actually character before it
	    lastDigit;
	for (lastDigit=fileName.length()-1;
	     !isDigitInRadix(fileName.charAt(lastDigit), getStackNumberRadix());
	     lastDigit--);
	for (firstDigit=lastDigit;
	     isDigitInRadix(fileName.charAt(firstDigit), getStackNumberRadix());
	     firstDigit--);
	return fileName.substring(firstDigit+1, lastDigit+1);
	/*firstDigit is exclusive & last is inclusive, but substring
          wants the opposite for its args*/
    }
    protected final String stringifyToNDigits(int theNumber, int nDigits) {
	/*Same as other stringifyToNDigits() but the base is
          this.getStackNumberRadix().*/
	return stringifyToNDigits(theNumber, nDigits, getStackNumberRadix());
    }
    protected final String stringifyToNDigits(int theNumber, int nDigits, int radix) throws IllegalArgumentException {
	/*Returns a string representation at least nDigits long of
          theNumber in base radix.  The return value is the same as
          that of Integer.toString(theNumber, radix) except that the
          string is padded to be exactly nDigits long (if it is not at
          least that long already) by the addition of leading zeroes
          before the first digit and after any minus sign.*/
	StringBuffer returnValue=new StringBuffer(Integer.toString(theNumber, radix));
	int insertZeroesIndex=returnValue.toString().indexOf('-') + 1;
	//in case someone finds a reason to care that theNumber < 0 is represented right
	while (returnValue.length() < nDigits)
	    returnValue.insert(insertZeroesIndex, '0');
	return new String(returnValue);
    }
    public synchronized ImagePlus summarize() {
	/*Displays and returns a stack whose slices are Z-projections
	  of the series in order.  If this series is grayscale, it
	  will be converted to 8-bit to save memory.  Only stacks in
	  the iteration range are represented.*/
	int savedPlace=currentStackNumber;
	ImagePlus stack, summary;
	ImageStack summaryStack=null;
	ImageWindow zProjWindow;
	DialogFiller dF=null;
	Exception e=null;
	TextRoi stackLabel=null;
	String stackNumber=null;
//  	boolean grayscale=false;
	IJ.showStatus("Summarizing series...");
	do {
	    try {
		dF=new DialogFiller("ZProjection", false,
				    new boolean[] {!IJ.isMacintosh(), IJ.isMacintosh()},
				    null, new Integer[] {new Integer(1)}, //brightest-pixel proj
				    null, null, null);
	    }
	    catch (Exception x) {
		if (x.getMessage().startsWith("Already")) {
		    /*some other DialogFiller exists for this box--wait
		      for it to go away*/
		    e=x;
		    IJ.wait(500);
		}
		/*otherwise it's an inexplicable glitch--best to give up */
		else return null;
	    }
	} while (e != null);
	currentStackNumber=iterationStart;
	while (this.hasMoreElements()) {
	    boolean isFirstStack=(whichStack()==iterationStart);
	    (stack=(ImagePlus) this.nextElement()).show();
	    if (isFirstStack) {
//  		grayscale=!(stack.getType()==ImagePlus.COLOR_RGB && stack.getType()==ImagePlus.COLOR_256);
		dF.setTextComponentStates(new String[] {"1", Integer.toString(stack.getStackSize())});
		dF.setActive(true);
	    }
	    WindowManager.setCurrentWindow(stack.getWindow()); //just in case
	    IJ.run("Z Project...");
//  	    if (grayscale) {
//  		WindowManager.getCurrentWindow().getImagePlus().getProcessor().setMinAndMax(minPixel, maxPixel);
//  		IJ.run("8-bit");
//  	    }
	    //IJ.run("Z Project...");
	    zProjWindow=WindowManager.getCurrentWindow();
	    if (isFirstStack) {
		summaryStack=new ImageStack(zProjWindow.getImagePlus().getWidth(),
							 zProjWindow.getImagePlus().getHeight());
		stackNumber="stack #" + iterationStart;
	    }
	    else
		stackNumber=Integer.toString(currentStackNumber - 1);
	    stackLabel=new TextRoi(zProjWindow.getImagePlus().getWidth(),
				   zProjWindow.getImagePlus().getHeight(),
				   zProjWindow.getImagePlus());
	    for (int index=0; index < stackNumber.length(); index++)
		stackLabel.addChar(stackNumber.charAt(index));
	    zProjWindow.getImagePlus().setRoi(stackLabel);
	    IJ.run("Draw");
	    summaryStack.addSlice("", zProjWindow.getImagePlus().getProcessor());
	    zProjWindow.dispose();
	    stack.hide();
	    stack.flush();
	    WindowManager.removeWindow(zProjWindow);
	    System.gc();
	}
	dF.selfDestruct();
	currentStackNumber=savedPlace;
	//use absolute brightness scale:
	summary=new ImagePlus("", summaryStack);
	summaryStack.update(summary.getProcessor());
	//now show it and return it:
	new StackWindow(summary).show();
//  	new StackWindow(summary=new ImagePlus("", summaryStack)).show();
	return summary;
    }
	public String toString() {
	    return this.getClass().getName() + " of series " + stacksPath + nameBase + '*';
	}
    public synchronized final int whichStack() {
	/*Returns the absolute number of the stack that will be opened
	  next.  The absolute number is given in the stack's filename
	  and may differ from its place in the iteration order.

	  If the iteration range is not set, the number defaults to
	  getSeriesRange()[0].*/
	if (currentStackNumber < 0)
	    currentStackNumber=getIterationStart();
	return currentStackNumber;
    }
}
