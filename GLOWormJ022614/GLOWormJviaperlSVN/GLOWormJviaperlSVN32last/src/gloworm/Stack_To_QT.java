package gloworm;

import ij.*;
import ij.plugin.filter.*;
import ij.gui.*;
import gloworm.qtvr.*;
import java.awt.*;
import ij.process.ImageProcessor;
import ij.io.SaveDialog;
import java.io.*;
import java.awt.image.*;

public class Stack_To_QT implements PlugInFilter {
    /*Saves the current stack as a QuickTime movie, interactively
      (through IJ.run) or in batch mode.  Assumes all slices in the
      current stack have same width, height and type. */
    private ImagePlus target;
    private boolean stackIsWide; //is this stack > 2Gb?
    //private static final int TICK_RATE=10; //ticks/sec.  Arbitrary.
    private static final int TICK_RATIO=10, // # ticks/frame.  Arbitrary.
	WIDE_STACK_MARGIN=10240, //how close to 2Gb the stack can get before we use wide offsets
	TEMPORAL_LOSSLESS=1023, //temporal-compression quality in sample description
	SPATIAL_LOSSLESS=1024, //spatial-compression quality in sample description
	STD_RESOLUTION=72 << 16; //72 pixels/inch (cribbed from existing QT file) in 16.16 fixed-point

    private Atom describeSample(int width, int height, short bitDepth, Atom colorTable) {
	/*Returns a sample description atom for img as it will be
          represented in the QuickTime file.  Assumes this movie has 1
          chunk and 1 data reference. */
	return AtomFactory.makeSampleDescriptionAtom(new Atom[] {AtomFactory.makeVideoSampleDescription("raw ", (short) width, (short) height,
													STD_RESOLUTION, STD_RESOLUTION, //resolution horiz & vert
													bitDepth,
													TEMPORAL_LOSSLESS, SPATIAL_LOSSLESS, //compression quality
													(short) 1, //frame per sample
													(short) 1, //data ref index (1-based), same OK for all
													"None", //no compressor
													colorTable)});
    }
    public void run(ImageProcessor ip) {
	/*Writes the file at the location and (approximate) frame rate
          the user directs through dialogs.  The given frame rate is
          only approximate because movies must be a whole number of
          seconds long as a consequence of the file format; the actual
          frame rate will be adjusted to conform to that. */
	SaveDialog sd;
	do {
	    int extIndex=target.getTitle().lastIndexOf('.');
	    sd=new SaveDialog("Save to .zip or .mov file: ",
			      target.getTitle().substring(0, (extIndex < 0? target.getTitle().length(): extIndex)),
			      ".mov");
	} while (sd.getFileName() != null && !(sd.getFileName().endsWith(".mov")) && !(sd.getFileName().endsWith(".zip")));
	if (sd.getFileName()==null) {
	    IJ.showStatus("Stack to QT: canceled!");
	    return;
	}
	int frameRate=-1;
	do {
	    GenericDialog frameRateBox=new GenericDialog("Output Frame Rate");
	    frameRateBox.addNumericField("Frames per second: ", 5, 0);
	    frameRateBox.showDialog();
	    frameRate=(int) Math.round(frameRateBox.getNextNumber());
	    if (frameRateBox.invalidNumber())
		frameRate=0;
	} while (!(frameRate > 0));
        String outFile=sd.getDirectory()+ sd.getFileName();
	try {
	    runBatch(target, outFile, frameRate);
	} catch (IOException e) {
	    IJ.error("Can't write " + outFile + ": " + e.getMessage());
	}
    }
    public void runBatch(ImagePlus target, String outFile, int frameRate) throws IOException {
	/*Saves the movie to outFile without user interaction.

	  ARGS
	  imp: what stack to use
	  outFile: what file to save to.
	  frameRate: how many frames per second to show (approximately).
	  
	  Throws an IllegalArgumentException if outFile's extension is
	  not ".zip" or ".mov"; if frameRate is non-positive; or if
	  the stacks' slices are not uniform in width, height and
	  type. Also throws IOException or SecurityException if
	  writing to outFile fails. */
	if (!(frameRate > 0))
	    throw new IllegalArgumentException("Frame rate " + frameRate + " is not positive");
	if (!(outFile.endsWith(".zip") || outFile.endsWith(".mov")))
	    throw new IllegalArgumentException(outFile + " does not end in .zip or .mov");
	//it would happen anyway, so do it before expensive encoding to QT format
	Class sliceType1=target.getStack().getProcessor(1).getClass();
	for (int index=2; index <= target.getStackSize(); index++) {
	    Class sliceType2=target.getStack().getProcessor(index).getClass();
	    if (sliceType1 != sliceType2)
		throw new IllegalArgumentException(sliceType2.getName() + " in " +sliceType1.getName() + " stack");
	}
	/*If we got here the stack's state is consistent: type
          checking was explicit, getProcessor() would have failed if
          the slices differed in dimensions, and 1 color model for
          whole stack is a given (if one is set--if not we set it
          later).  Now make the atom: */
	//int duration=target.getStackSize() * frameRate * TICK_RATE;
	int duration=target.getStackSize() * TICK_RATIO;
	if (target.getStack().getColorModel()==null) //the individual slices are using their own.  Standardize on one of them:
	    target.getStack().setColorModel(target.getStack().getProcessor(1).getColorModel());
	stackIsWide=(((long) target.getStackSize())
		     * target.getWidth() * target.getHeight()
		     * target.getStack().getColorModel().getPixelSize() / 8
		     + WIDE_STACK_MARGIN
		     > Integer.MAX_VALUE);
	Atom
	    mdat=new Atom(AtomFactory.getAtomType("mdat"), stackIsWide),
	    stbl=sample(target.getStack(), mdat),
	    minf=AtomFactory.makeVideoMediaInfoAtom(AtomFactory.makeDataHandlerReferenceAtom("alis"), //ref to data in same file = alias
						    AtomFactory.makeDataInfoAtom(new Atom[] {AtomFactory.makeDataSelfReference()}),
						    stbl),
	    mdia=AtomFactory.makeMediaAtom(AtomFactory.makeMediaHeaderAtom(frameRate*TICK_RATIO, duration),
					   AtomFactory.makeMediaHandlerReferenceAtom("vide"),
					   minf),
	    tkhd=AtomFactory.makeTrackHeaderAtom(false, false, //no preview or poster for this track to be in
						 1, //this is the 1st track
						 duration, target.getWidth(), target.getHeight()),
	    trak=AtomFactory.makeTrackAtom(tkhd, mdia),
	    mvhd=AtomFactory.makeMovieHeaderAtom(frameRate*TICK_RATIO, duration,
						 0, 0, 0, //no poster or preview
						 1), //how many tracks there are
	    moov=AtomFactory.makeMovieAtom(mvhd, new Atom[] {trak});
	QTFile theFile=new QTFile(stackIsWide);
	theFile.addAtom(moov);
	theFile.addAtom(mdat);
	IJ.showStatus("Writing to file...");
	theFile.writeToFile(new File(outFile));
	IJ.showStatus(outFile + " written");
    }
    private Atom sample(ImageStack stack, Atom mdat) {
	/*Stores the frames of the stack as samples in mdat and
          returns a sample-table atom (type stbl) describing what was
          stored.  Throws an IllegalArgumentException if mdat isn't a
          media-data atom.*/
	Atom sampleDescription=null, chunkOffset=null;
	int[] sampleSizes=new int[stack.getSize()];
	short[] bitDepths=new short[sampleSizes.length];
	Atom[] colorTables=new Atom[bitDepths.length];
	//ArrayList sampleDescriptions=new ArrayList(), sampleSizes=new ArrayList(), sampleToChunkEntries=new ArrayList();
	for (int index=1; index <= sampleSizes.length; index++) { //encode samples and get their sample-table entries
	    IJ.showProgress(((double) index) / sampleSizes.length);
	    IJ.showStatus("Encoding " + index + '/' + sampleSizes.length);
	    ImageProcessor ip=stack.getProcessor(index);
	    Image frame=ip.createImage();
	    Offset frameOffset=AtomFactory.store1VideoFrameToMdat(mdat, frame, bitDepths, sampleSizes, colorTables, stackIsWide);
	    if (index==1) { //get the atoms, 1 should suffice for whole chunk:
		sampleDescription=describeSample(ip.getWidth(), ip.getHeight(), bitDepths[0], colorTables[0]);
		chunkOffset=AtomFactory.makeChunkOffsetAtom(new Offset[] {frameOffset});
	    }
	    //calculate sample size as (1 pixel-row length + filler so it ends at word boundary) * number of rows:
	    PixelGrabber pg=new PixelGrabber(frame, 0, 0, 0, 0, false);
	    try {
		pg.grabPixels();
	    } catch (InterruptedException e) {
		return null;
	    }
//  	    sampleSizes[index]=pg.getColorModel().getPixelSize() * stack.getWidth(); //bits per raw scanline
//  	    sampleSizes[index] += (16 - (sampleSizes[index] % 16)) % 16; //bits per padded scanline
//  	    sampleSizes[index]=sampleSizes[index]/8 * stack.getHeight(); //bytes per whole frame
	}
	Atom sampleToChunk=AtomFactory.makeSampleToChunkAtom(new int[] {1}, //the chunk group has to start w/the 1st chunk
						 new int[] {stack.getSize()}, //the lone chunk holds all frames
						 new int[] {1}), //there's only 1 sample description & this chunk group uses it
	    sampleSize=AtomFactory.makeSampleSizeAtom(sampleSizes),
	    timeToSample=AtomFactory.makeTimeToSampleAtom(new int[] {stack.getSize()}, new int[] {TICK_RATIO});
	return AtomFactory.makeSampleTableAtom(sampleDescription, timeToSample, sampleToChunk, sampleSize, chunkOffset);
    }
    public int setup(String args, ImagePlus target) {
	this.target=target;
	return DOES_ALL;
    }
}
