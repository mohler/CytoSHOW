package gloworm;

import ij.process.*;
import ij.*;
import java.util.Vector;
import java.lang.reflect.Array;

public class ImageLinearizer {
    /*Creates a line-shaped representation of an ImagePlus, in which
      each pixel is the average of all the ImagePlus's pixels that
      share a perpendicular line with it.  (If the ImagePlus holds a
      stack, the stack's brightest-point Z-projection is acted on
      instead.)  Optionally, keeps track of the pixel lines it
      creates, and returns them on demand as the rows or columns of a
      pixel array in chronological order.*/

    public static final int HORIZONTAL=1, VERTICAL=2;
    private Vector lines;
    private int maxLineLength;
    private boolean invertedColors=false; //maybe get rid of this
    //was LUT inverted in arg to most recent linearizeAndStore call?
    private Class pixelType=null;

    public ImageLinearizer() {
	maxLineLength=0;
	lines=new Vector();
    }
    private long comparePixels(Number pixel1, Number pixel2) {
	/*Return value is > 0 if the first argument's value represents
          a brighter pixel than the second argument, 0 if the two
          arguments represent equally bright pixels (within the limits
          of floating-point precision, and < 0 if the first argument
          represents a dimmer pixel than the second argument.  Assumes
          both arguments are same type.*/
	long value1, value2, bitMask=0xFFFFFFFFL;
	/*pixel values are unsigned and less than 32 bits, so all (or
          their bits taken as int) are in long's value set*/
	if (pixel1 instanceof Float) {
	    value1=Float.floatToIntBits(pixel1.floatValue());
	    value2=Float.floatToIntBits(pixel2.floatValue());
	    /*in IEEE representation, comparing two finite positive FP
              #'s and comparing their bits taken as unsigned ints -->
              same result*/
	}
	else {
	    value1=pixel1.intValue() & bitMask;
	    value2=pixel2.intValue() & bitMask;
	    //common Java trick to fake unsigned ints
	}
	return value1 - value2;
	/*this works because only the low-order halves of value1 and
          value2 store the number--no overflow*/
    }
    private int getLineLength(ImagePlus source, int lineDirection) {
	//Returns how many pixels the linearization of source will have.
	switch(Math.abs(lineDirection)) {
	case (HORIZONTAL): return source.getWidth();
	case (VERTICAL): return source.getHeight();
	default: throw new IllegalArgumentException("Argument must be +/-HORIZONTAL or +/-VERTICAL");
	}
    }
    public synchronized Object getLinesArray(int lineDirection) {
	/*Returns a pixel array containing, in chronological order,
          return values of all calls to this.linearizeAndStore().  If
          lineDirection's absolute value is HORIZONTAL, each line's
          first pixel will appear to the left and the first line of
          pixels at the top (or vice versa if lineDirection's absolute
          value is VERTICAL) in an ImageProcessor whose pixel array is
          the return value.  All lines will appear centered in the
          length direction.

	  Note that, unlike in linearize(), lineDirection must be
	  positive. */
	Object returnValue=Array.newInstance(pixelType,
					     maxLineLength*nLines());
	int pixelIndexMultiplier,
	    lineIndexMultiplier;
	/*the mth pixel in the nth line will appear in the return
	  value at index pixelIndexMultiplier*m +
	  lineIndexMultiplier*n*/
	Object paddingElement=new Byte((byte) 0);
	//black pixel that Array.set can put in any pixel array
	lineDirection=Math.abs(lineDirection);
	if (lines.isEmpty())
	    return null;
	if (lineDirection==HORIZONTAL) {
	    pixelIndexMultiplier=1;
	    lineIndexMultiplier=linesArrayWidth(lineDirection);
	}
	else { //here's where bogus value of lineDirection throws exception:
	    pixelIndexMultiplier=linesArrayWidth(lineDirection);
	    lineIndexMultiplier=1;
	}
	for (int lineIndex=0; lineIndex < nLines(); lineIndex++) {
	    Object currentLine=padArrayToSize(lines.elementAt(lineIndex), maxLineLength, paddingElement);
	    for (int pixelIndex=0; pixelIndex < maxLineLength; pixelIndex++)
		Array.set(returnValue, pixelIndex*pixelIndexMultiplier + lineIndex*lineIndexMultiplier,
			  Array.get(currentLine, pixelIndex));
	}
	return returnValue;
	    
    }
    private int getPixelBreadth(ImagePlus source, int lineDirection) {
	/*Returns how many pixels per slice are averaged to create
          each pixel of source's linearization.*/
	switch(Math.abs(lineDirection)) {
	case (HORIZONTAL): return source.getHeight();
	case (VERTICAL): return source.getWidth();
	default: throw new IllegalArgumentException("Argument must be +/-HORIZONTAL or +/-VERTICAL");
	}
    }
    private int indexDataPixelForLinePixel(ImageProcessor data, int d, int L, int lineDirection) {
	/*Returns the index in data's array where can be found the
          pixel which should be number d to get averaged into pixel
          number L of data's linearization.*/
	int direction=Math.abs(lineDirection), lineLength;
	boolean indexLinesBackward=direction != lineDirection;
	if (direction == HORIZONTAL) {
	    //d refers to height, L to width
	    lineLength=data.getWidth();
	    return data.getWidth() * d +
		((indexLinesBackward)? lineLength - L - 1: L);
	}
	else {
	    //d refers to width, L to height
	    lineLength=data.getHeight();
	    return d + data.getWidth() *
		(indexLinesBackward? lineLength - L - 1: L);
	}
    }
    public Object linearize(ImagePlus source, int lineDirection) {
	/*Returns an array of pixel values representing source, as
          explained in this class's description.  If lineDirection's
          absolute value is HORIZONTAL, the line will be calculated as
          if it ran horizontally along source (so that each pixel is
          an average of all the pixels on a vertical line or plane
          with it).  If lineDirection's absolute value is VERTICAL,
          the line will be calculated as if it ran vertically.  Using
          a negative value for lineDirection causes the elements of
          the returned array to appear in reverse order.*/
	Object returnValue=Array.newInstance(source.getProcessor().getPixels().getClass().getComponentType(),
					     getLineLength(source, lineDirection));
	//it's slow but it's general
	int pixelBreadth=getPixelBreadth(source, lineDirection),
	    lineLength=getLineLength(source, lineDirection), nSlices=source.getStackSize();
	IJ.showStatus("Averaging to line...");
	if (source.getStackSize() > 1) //use Z-proj instead
	    return linearize(maxValZProjOf(source), lineDirection);
	for (int lineIndex=0; lineIndex < lineLength; lineIndex++) {
	    double pixelSum=0; //this is also general--values will come from array of unspecified numeric type
	    for (int sliceIndex=1; sliceIndex <= nSlices; sliceIndex++) {
		source.setSlice(sliceIndex);
		Object rawPixels=source.getProcessor().getPixels();
		for (int pixelIndex=0; pixelIndex < pixelBreadth; pixelIndex++)
		    pixelSum += Array.getDouble(rawPixels, indexDataPixelForLinePixel(source.getProcessor(), pixelIndex, lineIndex, lineDirection));
	    }
	    setNumericArrayElement(returnValue, lineIndex, pixelSum/(pixelBreadth*nSlices)); //avg value
	}
	return returnValue;
    }
    public Object linearizeAndStore(ImagePlus source, int lineDirection) {
	/*Same as Linearize but also stores the return value
          internally to become part of pixel array returned by
          getLinesArray().  If the return value is of a different type
          than the pixel lines already stored, those lines are cleared
          from memory.*/
	Object returnValue=linearize(source, lineDirection);
	maxLineLength=Math.max(maxLineLength, Array.getLength(returnValue));
	if (returnValue.getClass().getComponentType() != pixelType) {
	    /*previously stored lines won't show up right next to this
              one--get rid of them*/
	    pixelType=returnValue.getClass().getComponentType();
	    lines.removeAllElements();
	}
	lines.add(returnValue);
	return returnValue;
    }
    public int linesArrayHeight(int lineDirection) {
	/*Height of image that could be made from return value of
          getLinesArray(lineDirection) if it were called now.  Next
          call to linearizeAndStore() will make returned value
          obsolete.*/
	switch(Math.abs(lineDirection)) {
	case (HORIZONTAL): return nLines();
	case (VERTICAL): return maxLineLength;
	default: throw new IllegalArgumentException("Argument must be HORIZONTAL or VERTICAL");
	}
    }
    public int linesArrayWidth(int lineDirection) {
	/*Width of image that could be made from return value of
          getLinesArray(lineDirection) if it were called now.  Next
          call to linearizeAndStore() will make returned value
          obsolete.*/
	switch(Math.abs(lineDirection)) {
	case (HORIZONTAL): return maxLineLength;
	case (VERTICAL): return nLines();
	default: throw new IllegalArgumentException("Argument must be HORIZONTAL or VERTICAL");
	}
    }
    public ImagePlus maxValZProjOf(ImagePlus source) {
	synchronized(source) {
	    /*Given a stack, returns the brightest-point Z-projection of that stack.*/
	    int length;
	    Object[] pixelArrays=source.getStack().getImageArray();
	    ImageProcessor outIP=source.getProcessor().createProcessor(source.getWidth(), source.getHeight());
	    Object inPixels=pixelArrays[0], outPixels=Array.newInstance(inPixels.getClass().getComponentType(), (length=Array.getLength(inPixels)));
	    System.arraycopy(inPixels, 0, outPixels, 0, length);
	    for (int sliceIndex = 1; sliceIndex < pixelArrays.length && (inPixels=pixelArrays[sliceIndex]) != null; sliceIndex++)
		for (int pixelIndex=0; pixelIndex < length; pixelIndex++) {
		    Number theInPixel=(Number) Array.get(inPixels, pixelIndex), theOutPixel=(Number) Array.get(outPixels, pixelIndex);
		    if (comparePixels(theInPixel, theOutPixel) > 0)
			Array.set(outPixels, pixelIndex, theInPixel);
		}
	    outIP.setPixels(outPixels);
	    return new ImagePlus("", outIP);
	}
    }
    private int nLines() {
	return lines.size();
    }
    private Object padArrayToSize(Object array, int size, Object padding) {
	/*Returns a copy of array with an equal (+/- 1) number of
          extra elements added at both ends, if necessary, to bring
          the length up to size.  Extra elements are set to the value
          of padding, unwrapped if necessary.*/
	Object returnValue;
	Class arrayType=array.getClass().getComponentType();
	int contentSize, padSize;
	if (arrayType==null)
	    throw new IllegalArgumentException("Argument must be array");
	if ((contentSize=Array.getLength(array)) >= size)
	    return array;
	returnValue=Array.newInstance(arrayType, size);
	padSize=(size - contentSize)/2;
	for (int index=0; index < padSize; index++)
	    Array.set(returnValue, index, padding);
	System.arraycopy(array, 0, returnValue, padSize, contentSize);
	for (int index=contentSize+padSize; index < size; index++)
	    Array.set(returnValue, index, padding);
	return returnValue;
    }
    private void setNumericArrayElement(Object array, int index, double numericElement) {
	/*Puts numericElement, narrowed as necessary, in array of any
          primitive numeric type.  Data mangling is at your own
          risk.*/
	Class arrayType=array.getClass().getComponentType();
	if (arrayType==null)
	    throw new IllegalArgumentException("First argument must be array object");
	if (arrayType.equals(byte.class))
	    Array.setByte(array, index, (byte) Math.round(numericElement));
	else if (arrayType.equals(short.class))
	    Array.setShort(array, index, (short) Math.round(numericElement));
	else if (arrayType.equals(int.class))
	    Array.setInt(array, index, (int) Math.round(numericElement));
	else if (arrayType.equals(long.class))
	    Array.setLong(array, index, Math.round(numericElement));
	else if (arrayType.equals(float.class))
	    Array.setFloat(array, index, (float) numericElement);
	else if (arrayType.equals(double.class))
	    Array.setDouble(array, index, numericElement);
    }
}
