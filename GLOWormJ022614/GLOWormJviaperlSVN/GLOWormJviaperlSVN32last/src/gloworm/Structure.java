package gloworm;

import ij.gui.*;
import ij.*;

public class Structure {
    /*An object the user selects while running Structure
      Autoselector--2 axes in slice plane of stack, + a polygonal
      outline (optional).  Each instance is responsible for all the
      math that will need to be done to its own coordinates.*/

    private int[]
	outlineXCoords, outlineYCoords, //absolute
	axes;
    //Contains coordinate pairs of 4 points (axes' endpoints) in order they were drawn.
    private boolean
	isBackward;
    //true if rotation in the plane can't put this Structure in standard orientation

    Structure(int[] axes) {
	/*Creates a Structure with no outline from an int[8] whose
          contents are 4 Cartesian pairs: the first and second
          endpoints of the first axis, then the first and second
          endpoints of the second axis.  If axes is not 8 elements,
          throws an IllegalArgumentException. */
	if (axes.length != 8)
	    throw new IllegalArgumentException("One or more arguments has wrong dimension");
	else {
	    this.axes=(int[]) axes.clone();
	    double testAngle=((rectToPolar(axes[6]-axes[4], axes[7]-axes[5]))[1] - orientation())
		% (2*Math.PI); //direction of 2nd axis relative to 1st
					
	    this.isBackward=((testAngle < 0?
			      testAngle + 2*Math.PI:
			      testAngle)
			     >= Math.PI);
	    //Meaning 2nd axis won't run toward -y when 1st runs along +x.
	}
    }
    Structure(int[] axes, int[] absoluteXCoords, int[] absoluteYCoords) { //last 2 args describe outline
	/*Creates a Structure with axes as in the 1-argument
          constructor, and the x and y coordinate arrays for the
          outline relative to the origin of the entire image this
          selection was made on. */
	this(axes);
	outlineXCoords=(int[]) absoluteXCoords.clone();
	outlineYCoords=(int[]) absoluteYCoords.clone();
    }
    Structure(int[] axes, int[] relativeXCoords, int[] relativeYCoords, int outlineOriginX, int outlineOriginY, int nPoints) {
	/*Creates a Structure with axes as in the 1-argument
	  constructor, and outline coordinate arrays in the form they
	  would be given by a user-drawn PolygonRoi: arbitrary in
	  length despite representing only nPoints actual points, and
	  relative to the bounding-box origin (outlineOriginX,
	  outlineOriginY).*/
	this(axes);
	int[] tempXs=new int[nPoints];
	int[] tempYs=new int[nPoints];
	//cut coordinate arrays to proper size:
	for (int index=0; index < nPoints; index++) {
	    tempXs[index]=relativeXCoords[index];
	    tempYs[index]=relativeYCoords[index];
	}
	for (int index=0; index < nPoints; index++) {
	    tempXs[index]+=outlineOriginX;
	    tempYs[index]+=outlineOriginY;
	}
	outlineXCoords=tempXs;
	outlineYCoords=tempYs;
    }
    public double[] axisLengths() {
	//Axes' lengths appear in return array in order they were drawn.
	return new double[] {
	    Math.sqrt(square(axes[2]-axes[0]) + square(axes[3]-axes[1])),
	    Math.sqrt(square(axes[6]-axes[4]) + square(axes[7]-axes[5]))
	    //distance formula
	};
    }
    public int[] centeringCorrection() {
	/*Returns the difference between the ideal and default pivot
          points for rotating this Structure around.  The default
          pivot point is the center of this Structure's bounding box
          before rotation; the ideal pivot point is the point that
          will become the center of this Structure's bounding box
          after rotation.*/
	int origCenterX=(int) Math.round(.5*findArrayMin(outlineXCoords) + 
					 .5*findArrayMax(outlineXCoords)),
	    origCenterY=(int) Math.round(.5*findArrayMin(outlineYCoords) +
					 .5*findArrayMax(outlineYCoords));
	//pivot point of rotation--remains constant in screen coords
	int[][] rotatedOutline=predictRotatedOutline();
	int[] rotatedXCoords=rotatedOutline[0],
	    rotatedYCoords=rotatedOutline[1],
	    returnValue;
	int rotatedCenterX=(int) Math.round(.5*findArrayMin(rotatedXCoords) +
					    .5*findArrayMax(rotatedXCoords)),
	    rotatedCenterY=(int) Math.round(.5*findArrayMin(rotatedYCoords) +
					    .5*findArrayMax(rotatedYCoords));
	returnValue=new int[] {rotatedCenterX - origCenterX,
			       rotatedCenterY - origCenterY};
	/*Now returnValue reflects the ideal pivot point's position
	 _after_ rotation backward (around the default pivot) puts it
	 at the center of the new bounding box.  Rotate forward again
	 to find where it started out:*/
	double[] polar=rectToPolar(returnValue[0], returnValue[1]);
	polar[1] += orientation();
	returnValue=polarToRect(polar[0], polar[1]);
	return returnValue;
    }
    public int[] dimensions() {
	/*Dimensions of the bounding box this Structure would have if rotated
	  to make its first axis horizontal.*/
	int[][] rotatedOutline=predictRotatedOutline();
	return new int[] {findArrayMax(rotatedOutline[0]) -
			  findArrayMin(rotatedOutline[0])+2,
			  findArrayMax(rotatedOutline[1]) -
			  findArrayMin(rotatedOutline[1])+2};
	//+2 so rounding error won't cut off part of Structure
    }
    private int findArrayMax(int[] array) {
	int currentMax=array[0];
	for (int index=0; index < array.length; index++)
	    if (array[index] > currentMax)
		currentMax=array[index];
	return currentMax;
    }
    private int findArrayMin(int[] array) {
	int currentMin=array[0];
	for (int index=0; index < array.length; index++)
	    if (array[index] < currentMin)
		currentMin=array[index];
	return currentMin;
    }
    public int[] getAxes() {
	return (int[]) axes.clone();
    }
    public int[] getOrigin() throws IllegalArgumentException {
	//Returns upper left corner of the axes' bounding rectangle.
	return new int[] {
	    Math.min(Math.min(axes[0], axes[2]), Math.min(axes[4], axes[6])),
	    Math.min(Math.min(axes[1], axes[3]), Math.min(axes[5], axes[7]))
	};
    }
    public int[] getOutlineXs() {
	return (int[]) outlineXCoords.clone();
    }
    public int[] getOutlineYs() {
	return (int[]) outlineYCoords.clone();
    }
    public PolygonRoi makeRoiFromOutline() {
	//So that the structure's outline contents can be copied into a new stack.
	int
	    outlineOriginX=findArrayMin(outlineXCoords),
	    outlineOriginY=findArrayMin(outlineYCoords), //find outline's bounding rectangle origin
	    pointCount=nPointsInOutline();
	int[]
	    relativeXCoords=new int[pointCount],
	    relativeYCoords=new int[pointCount];
	PolygonRoi
	    output;
	for (int index=0; index < pointCount; index++) {
	    relativeXCoords[index]=outlineXCoords[index]-outlineOriginX;
	    relativeYCoords[index]=outlineYCoords[index]-outlineOriginY;
	} //PolygonRoi constructor needs relative coordinates
		
	output=new PolygonRoi(relativeXCoords, relativeYCoords, pointCount, Roi.POLYGON);
	output.setLocation(outlineOriginX, outlineOriginY);
	return output;
    }
    public boolean needsFlipped() {
    return false;
//	return this.isBackward;		// !!!
    }
    public int nPointsInOutline() {
	if (outlineXCoords==null)
	    return 0;
	else
	    return outlineXCoords.length;
    }
    public double orientation() {
	/*Returns (in radians) the direction of a vector from the
	  first to the second endpoint of this structure's first
	  axis.*/
	int[] axisEquiv=new int[] {axes[2]-axes[0], axes[3]-axes[1]};
	//the vector translated to start at (axes[0], axes[1])

	return (rectToPolar(axisEquiv[0], axisEquiv[1]))[1] % (2*Math.PI);
	/*The angle part of the coordinate pair from rectToPolar.*/
    }
    private int[] polarToRect(double r, double theta) {
	//re why returns int[], see above
	int x, y;
	x=(int) Math.round(r * Math.cos(theta));
	y=(int) Math.round(r * Math.sin(theta));
	return new int[] {x, y};
    }
    private int[][] predictRotatedOutline() {
	/*1st subarray is screen X-coordinates this Structure;s
	  outline will have after rotation around center of its
	  current bounding box. 2nd subarray is same, Y-coordinates.*/
	int pivotX=(int) Math.round(.5*findArrayMin(outlineXCoords) + .5*findArrayMax(outlineXCoords)),
	    pivotY=(int) Math.round(.5*findArrayMin(outlineYCoords) + .5*findArrayMax(outlineYCoords));
	int[] rect=new int[2];
	int[][] returnValue=new int[2][nPointsInOutline()];
	double[] polar;
	double orientation=orientation();
	for (int index=0; index < nPointsInOutline(); index++) {
	    rect[0]=outlineXCoords[index] - pivotX; //make relative to pivot
	    rect[1]=outlineYCoords[index] - pivotY;
	    polar=rectToPolar(rect[0], rect[1]);
	    polar[1] -= orientation; //rotate backward to final orientation
	    rect=polarToRect(polar[0], polar[1]);
	    returnValue[0][index]=rect[0] + pivotX; //make absolute again
	    returnValue[1][index]=rect[1] + pivotY;
	}
	return returnValue;
    }
    private double[] rectToPolar(int x, int y) {
	/*Assumes x and y are a pixel's coordinates--that's why they're ints.*/
	double r, theta;
	r=Math.sqrt(square(x) + square(y));
	if (y==0)
	    theta=0;
	else if (x==0)
	    theta=((y > 0)?
		   Math.PI/2:
		   3*Math.PI/2);
	else
	    theta=Math.atan(((double) y)/((double) x));
	if (x < 0)
	    //pi/2 <= the REAL theta <= 3pi/2 outside Math.atan()'s range, so:
	    theta += Math.PI;
	//return new double[] {r, theta};
	return new double[] {r, (theta < 0)?
			     theta + 2*Math.PI:
			     theta};
	//make range of theta 0 to 2pi
    }
    private int square(int operand) {
	return operand * operand;
    }
    public void takeOutlineOf(Structure prototype) {
	/*Gives this structure the outline of prototype, but rotated and scaled to fit its own axes.*/
	int pointCount=prototype.nPointsInOutline();
	int[]
	    rectPoint,
	    prototypeAxes=prototype.axes,
	    prototypeXCoords=prototype.outlineXCoords,
	    prototypeYCoords=prototype.outlineYCoords;
	double[] polarPoint;
	double angle=this.orientation() - prototype.orientation(), 
	    //outline to be rotated over this angle*/
	    scale=(((this.axisLengths())[0] > (this.axisLengths())[1])?
		   (this.axisLengths())[0] / (prototype.axisLengths())[0] :
		   (this.axisLengths())[1] / (prototype.axisLengths())[1]);
				//scaling factor should be ratio of _longer_ axis to same axis of prototype
	outlineXCoords=new int[pointCount];
	outlineYCoords=new int[pointCount];
	for (int index=0; index < pointCount; index++) {
	    rectPoint=new int[]
	    {prototypeXCoords[index] - prototypeAxes[0],
	     prototypeYCoords[index] - prototypeAxes[1]};
	    /*translate from screen's to prototype's coordinate
	      system, where 1st point of 1st axis==(0, 0)*/
	    polarPoint=rectToPolar(rectPoint[0], rectPoint[1]);
	    polarPoint[0] *= scale; //scale point by scale
	    polarPoint[1] += angle; //rotate point by angle
	    rectPoint=polarToRect(polarPoint[0], polarPoint[1]);
	    rectPoint[0] += this.axes[0];
	    rectPoint[1] += this.axes[1];
	    //translate point from this Structure's to screen's coord system
	    outlineXCoords[index]=rectPoint[0];
	    outlineYCoords[index]=rectPoint[1];
	}
    }
    public String toString() {
	/*Output describes this structure completely.
	  First 8 numbers = axis coordinates
	  Next number = how many points in outline
	  Remaining numbers= coordinates of points in outline (in coordinate pairs)*/
	String output="";
	for (int index=0; index < 8; index++)
	    output += axes[index] + " ";
	output += nPointsInOutline() + " ";
	for (int index=0; index < nPointsInOutline(); index++) {
	    output += getOutlineXs()[index] + " ";
	    output += getOutlineYs()[index] + " ";
	}
	return output;
    }
}
