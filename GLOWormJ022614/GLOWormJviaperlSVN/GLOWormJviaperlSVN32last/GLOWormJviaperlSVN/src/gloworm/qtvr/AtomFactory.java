package gloworm.qtvr;

import java.util.*;
import java.io.*;
import java.awt.image.*;
import java.awt.Image;
import java.awt.color.ColorSpace;
/**/
import java.lang.reflect.*;

public final class AtomFactory { //fix the comment later
    /*Static methods for making atoms and setting the values of their
     fields.  This class is not instantiable.
     
     Generally, if argument argX is an atom, the atom should be one
     whose type is getAtomType("argX").  The correctness of the type
     is not enforced at compile time but with
     IllegalArgumentExceptions at runtime.

     Methods with array arguments assume that the arrays have nonzero
     length (which means they throw ArrayIndexOutOfBoundsExceptions if
     that proves wrong).

     System requirements for this class (and its package in general):
     Java 1.2 (Quicktime and QTJava not required) */
    private static Atom DEFAULT_CTAB=new Atom(getAtomType("ctab"), false); //placeholder in array (since nulls get overwritten)

    private AtomFactory() {
	//enforce noninstantiability
    }

    public static int getAtomType(String atomType) {
	/*QTVR atom types, though really 32-bit integers, are often
          given as strings of 4 ASCII characters.  This method returns
          the int that corresponds to atomType's ASCII equivalent.

	  An IllegalArgumentException is thrown if atomType has length
	  other than 4 characters or contains any character without an
	  ASCII equivalent.*/
	if (atomType.length() != 4)
	    throw new IllegalArgumentException("atom type '" + atomType + "' is not 4 characters");
	if (!Util.isASCII(atomType))
	    throw new IllegalArgumentException("atom type must be an ASCII string");
	//don't bother saying what the string is, often non-ASCII --> unprintable
	byte[] atomTypeBytes=null;
	try { atomTypeBytes=atomType.getBytes("US-ASCII"); }
	catch (UnsupportedEncodingException e) {
	    throw new InternalError("ASCII encoding doesn't work");
	} //won't happen, US-ASCII is always supported
	int returnValue=0;
	for (int index=0; index < 4; index++)
	    returnValue=(returnValue << 8) | atomTypeBytes[index];
	return returnValue;
    }

    static String stringifyAtomType(int atomType) {
	//Inverse of getAtomType.
	byte[] chars=new byte[4];
	for (int mask=0xFF, index=3; index >= 0; index--) {
	    chars[index]=(byte) (atomType & mask);
	    atomType >>>= 8;
	}
	try { return new String(chars, "US-ASCII"); }
	catch (UnsupportedEncodingException e) {
	    throw new InternalError("ASCII encoding doesn't work");
	} //won't happen, US-ASCII is always supported
    }

    private static void assertAtomIsType(Atom atom, String type) {
	assertAtomIsType(atom, new String[] {type});
    }

    private static void assertAtomIsType(Atom atom, String[] types) {
	for (int index=0; index < types.length; index++)
	    if (atom.getType() == getAtomType(types[index])) return;
	//if we got here we didn't find it--complain:
	throw new IllegalArgumentException(atom + " type is " + stringifyAtomType(atom.getType()) +
					   ", should be one of " + Arrays.asList(types));
    }

    private static Atom makeAtomContainer(String type, Writable[] fields, boolean canBeWide) {
	/*Builds and returns an atom whose type, translated into
          ASCII, is the string in question.  The returned atom's
          fields (other than the size/type boilerplate at the
          beginning) are the contents of the fields array in order.
          The returned atom is made capable of holding more than 2Gb
          when written to disk iff canBeWide is true.

	  Does no argument checking--calling methods have to do that.*/
	Atom returnValue=new Atom(getAtomType(type), canBeWide);
	for (int index=0; index < fields.length; index++)
	    returnValue.addField(fields[index]);
	return returnValue;
    }

    public static Atom makeMovieAtom(Atom mvhd, Atom[] trak) {
	/*Makes and returns the atom which holds the metadata
          necessary to interpret the media samples as movies.  The
          atom will have type 'moov' and contain the movie header atom
          and track atoms given; the other optional atoms are not
          supported.*/
	//see if we have right kind of component atoms:
	assertAtomIsType(mvhd, "mvhd");
	for (int index=0; index < trak.length; index++)
	    assertAtomIsType(trak[index], "trak");
	//they check out OK, so make the atom from them:
	Atom returnValue=new Atom(getAtomType("moov"), false); //this is metadata--nice and small
	returnValue.addField(mvhd);
	for (int index=0; index < trak.length; index++)
	    returnValue.addField(trak[index]);
  	return returnValue;
    }

    public static Atom makeMovieHeaderAtom(int timeUnit, int duration, int previewStart, int previewLength, int posterTime, int nTracks) {
	/*Makes and returns the atom which holds a bunch of
          information applicable to the movie as a whole, for use as
          part of the movie's metadata.  The atom will have type
          'mvhd' and contain the argument values in the appropriate
          fields, with default values used for whatever fields aren't
          represented in the arguments.

	  This method assumes the largest ID number of any track in
	  the movie is no greater than the number of tracks.

	  ARGS
	  timeUnit: # ticks/sec where 1 tick = basic unit of time measurement in this movie
	  duration: length of movie's longest track
	  previewStart: how many ticks into the movie the part used for preview begins
	  previewLength: how many ticks the preview lasts (0 if you want no preview)
	  posterTime: how many ticks into the movie a representative still begins
	  nTracks: how many tracks this movie will ultimately have*/
	byte[] intBytes=new byte[4]; /*any int that's been converted
	to bytes for storage--Atom makes defensive copies so can reuse
	same array for all*/
	Atom returnValue=new Atom(getAtomType("mvhd"), false);
	returnValue.addField(new byte[1]); //version
	returnValue.addField(new byte[3]);//flags, should be 0
	//creation & modification time--right this second is close enough:
	Util.storeInByteArray(Util.getMacTime(), intBytes, 0);
	returnValue.addField(intBytes);
	returnValue.addField(intBytes);
	//time unit:
	Util.storeInByteArray(timeUnit, intBytes, 0);
	returnValue.addField(intBytes);
	//duration:
	Util.storeInByteArray(duration, intBytes, 0);
	returnValue.addField(intBytes);
	//suggested playback speed (use normal speed, 1):  FIXED POINT
	Util.storeInByteArray(0x00010000, intBytes, 0);
	returnValue.addField(intBytes);
	//suggested sound volume (use normal volume, 1):  FIXED POINT
	returnValue.addField(new byte[] {(byte) 0x01, (byte) 0});
	//10 reserved bytes:
	returnValue.addField(new byte[10]);
	//transform matrix:
	returnValue.addField(Util.getIdentityTransformMatrix());
	//preview start:
	Util.storeInByteArray(previewStart, intBytes, 0);
	returnValue.addField(intBytes);
	//preview length:
	Util.storeInByteArray(previewLength, intBytes, 0);
	returnValue.addField(intBytes);
	//poster time:
	Util.storeInByteArray(previewLength, intBytes, 0);
	returnValue.addField(intBytes);
	//selection time and duration--just use 0:
	Arrays.fill(intBytes, (byte) 0);
	returnValue.addField(intBytes);
	returnValue.addField(intBytes);
	//current time position in the movie--beginning:
	returnValue.addField(intBytes);
	//1-based # of next track, assuming the existing ones used #'s 1 thru nTracks:
	Util.storeInByteArray(nTracks, intBytes, 0);
	returnValue.addField(intBytes);
	return returnValue;
    }

    public static Atom makeTrackAtom(Atom tkhd, Atom mdia) {//optional atoms not supported
	/*Returns an atom of type 'trak' defining 1 track of the movie. */
	assertAtomIsType(tkhd, "tkhd");
	assertAtomIsType(mdia, "mdia");
	return makeAtomContainer("trak", new Writable[] {tkhd, mdia}, false);
	//doesn't have any large sub-atoms
    }

    public static Atom makeTrackHeaderAtom(boolean inPreview, boolean inPoster, int whichTrack, int duration, int width, int height) {
	/*Returns an atom of type 'tkhd' holding various info about an entire track.

	 ARGS
	 inPreview: True if the preview should include this track.
	 inPoster: True if the poster should include this track.
	 whichTrack: What # track will contain this header.  Positive, or an IllegalArgumentException is thrown.
	 (e.g. if 2 tracks already exist & you're making this header for a 3rd, put 3)
	 duration: How many ticks this track lasts.
	 width: How many pixels wide the video data on this track is.
	 height: How many pixels high the video data on this track is.*/
	if (whichTrack < 0)
	    throw new IllegalArgumentException("Track index " + whichTrack + " <= 0");
	Atom returnValue=new Atom(getAtomType("tkhd"), false);
	byte[] intBytes=new byte[4]; //any 4-byte field of the atom--can reuse, Atoms make defensive copies
	byte[] shortBytes=new byte[2];
	//version & flags, use 0 for version:
	intBytes[3]=3; //indicates track is enabled & in movie
	if (inPreview)
	    intBytes[3] |= 4;
	if (inPoster)
	    intBytes[3] |= 8;
	returnValue.addField(intBytes);
	//creation & modification time--use right now:
	Util.storeInByteArray(Util.getMacTime(), intBytes, 0);
	returnValue.addField(intBytes);
	returnValue.addField(intBytes);
	//track ID:
	Util.storeInByteArray(whichTrack, intBytes, 0);
	returnValue.addField(intBytes);
	//4 reserved bytes:
	Arrays.fill(intBytes, (byte) 0);
	returnValue.addField(intBytes);
	//duration:
	Util.storeInByteArray(duration, intBytes, 0);
	returnValue.addField(intBytes);
	//8 more reserved bytes:
	returnValue.addField(new byte[8]);
	//what layer this track is, & ID of other tracks that can replace it (0, not supported):
	Arrays.fill(intBytes, (byte) 0);
	returnValue.addField(intBytes);
	//sound volume (use normal, 1): FIXED POINT
	shortBytes[0]=1;
	returnValue.addField(shortBytes);
	//2 reserved bytes:
	Arrays.fill(shortBytes, (byte) 0);
	returnValue.addField(shortBytes);
	//transform matrix for video data:
	returnValue.addField(Util.getIdentityTransformMatrix());
	//track width: FIXED POINT
	Util.storeInByteArray(width << 16, intBytes, 0); //shift converts int to 16.16 fixed-point #
	returnValue.addField(intBytes);
	//track height: FIXED POINT
	Util.storeInByteArray(height << 16, intBytes, 0);
	returnValue.addField(intBytes);
	return returnValue;
    }

    public static Atom makeMediaAtom(Atom mdhd, Atom hdlr, Atom minf) {
	/*Returns an atom of type 'mdia' that describes but does not
          contain the actual media data for a track. */
	assertAtomIsType(mdhd, "mdhd");
	assertAtomIsType(hdlr, "hdlr");
	assertAtomIsType(minf, "minf");
	return makeAtomContainer("mdia", new Writable[] {mdhd, hdlr, minf}, false);
    }

    public static Atom makeMediaHeaderAtom (int timeScale, int duration) {
	/*Returns an atom of type 'mdhd' that gives the most basic
          information about some media data, for inclusion in the
          'mdia' atom returned by makeMediaAtom.

	  ARGS
	  timeScale: # ticks per second, where 1 tick is unit for
	  time values in this 'mdia' atom
	  duration: # ticks the media being described lasts for*/
	byte[] intBytes=new byte[4];
	Atom returnValue=new Atom(getAtomType("mdhd"), false);
	//version and flags--can/should be 0:
	returnValue.addField(intBytes);
	//creation and modification time--use this second for both:
	Util.storeInByteArray(Util.getMacTime(), intBytes, 0);
	returnValue.addField(intBytes);
	returnValue.addField(intBytes);
	//time scale:
	Util.storeInByteArray(timeScale, intBytes, 0);
	returnValue.addField(intBytes);
	//duration:
	Util.storeInByteArray(duration, intBytes, 0);
	returnValue.addField(intBytes);
	//language--use 0, English:
	returnValue.addField(new byte[2]);
	//quality--0x38 makes a good default (source: Ion Moraru, The Virtual Cell)
	returnValue.addField(new byte[] {0x38});
	return returnValue;
    }

    public static Atom makeVideoMediaInfoAtom(Atom hdlr, Atom dinf, Atom stbl) {
	/*Returns an atom of type 'minf' that describes the timing of
	  the media samples and the way they're stored, for the use of
	  whatever media handler will read them.  Video and audio
	  media info only. */
	assertAtomIsType(hdlr, "hdlr");
	assertAtomIsType(dinf, "dinf");
	assertAtomIsType(stbl, "stbl");
	return makeAtomContainer("minf",
				 new Writable[] {makeVideoMediaInfoHeaderAtom(), hdlr, dinf, stbl},
				 false);
    }

    public static Atom makeBaseMediaInfoAtom(Atom gmhd, Atom gmin) {
	/*Returns an atom of type 'minf' for describing the timing
          etc. of base media samples.*/
	assertAtomIsType(gmhd, "gmhd");
	assertAtomIsType(gmin, "gmin");
	return makeAtomContainer("minf", new Writable[] {gmhd, gmin}, false);
    }

    private static Atom makeVideoMediaInfoHeaderAtom() {
	/*Returns an atom of type 'vmhd' that describes color and
          graphics mode.  For use in 'minf' atoms that deal with video
          media samples.*/
	Atom returnValue=new Atom(getAtomType("vmhd"), false);
	returnValue.addField(new byte[] {0}); //version--assume 0
	//flags--set the one requiring QT version > 1.0:
	returnValue.addField(new byte[] {0, 0, 1});
	//graphics transfer mode and opcolor (set them 0 for straight copy):
	returnValue.addField(new byte[8]);
	return returnValue;
    }

    public static Atom makeDataInfoAtom(Atom[] dataReferences) {
	/*Returns an atom of type 'dinf' that holds the contents of
          the argument array (which should all be created by
          makeDataReference) in organized form.  This atom tells the
          data handler (which retrieves media data from disk or
          network) where the samples are.*/
	String[] dataRefTypes=new String[] {"alis", "rsrc", "url "};
	//not all those types are implemented yet, but make sure they pass assertion just in case
	for (int index=0; index < dataReferences.length; index++)
	    assertAtomIsType(dataReferences[index], dataRefTypes);
	Atom dref=new Atom(getAtomType("dref"), false);
	byte[] intBytes=new byte[4];
	dref.addField(intBytes); //version and flags--both can/should be 0
	Util.storeInByteArray(dataReferences.length, intBytes, 0);
	dref.addField(intBytes);
	for (int index=0; index < dataReferences.length; index++)
	    dref.addField(dataReferences[index]);
	return makeAtomContainer("dinf", new Writable[] {dref}, false);
    }

    public static Atom makeDataSelfReference() {
	/*Returns an atom-like structure (it's indistinguishable from
          an atom so we'll call it one), which tells the data handler
          to look for the samples in the same file as the movie atom
          which the return value will be part of.*/
	Atom returnValue=new Atom(getAtomType("alis"), false);
	//version and flags--version 0, flag set to indicate samples are in this file:
	returnValue.addField(new byte[1]);
	returnValue.addField(new byte[] {0, 0, 1});
	//No more info needed, this is a self-reference--source: Ion Moraru, The Virtual Cell
	return returnValue;
    }

    //sample-table-building methods:

    public static Atom makeSampleTableAtom(Atom stsd, Atom stts, Atom stsc, Atom stsz, Atom stco) {
	/*Returns an atom of type 'stbl' which translates between
          time, number in sequence and physical location of samples.*/
	assertAtomIsType(stsd, "stsd");
	assertAtomIsType(stts, "stts");
	assertAtomIsType(stsc, "stsc");
	assertAtomIsType(stsz, "stsz");
	assertAtomIsType(stco, "stco");
	return makeAtomContainer("stbl", new Writable[] {stsd, stts, stsc, stsz, stco}, false);
    }

    private static Atom makeSampleTableShell(String type, int nEntries) {
	/*Nearly all the atoms directly inside a 'stbl' atom have the
          same basic form: size, type, version byte, 3 flags bytes, #
          entries, the actual entries.  This method makes an atom with
          all but the last filled in.*/
	Atom returnValue=new Atom(getAtomType(type), false);
	byte[] intBytes=new byte[4];
	//returnValue.addField(intBytes); //version and flags--can/should be 0
	returnValue.addField(new byte[1]); //version (call it 0)
	returnValue.addField(new byte[3]); //flags (should be 0)
	Util.storeInByteArray(nEntries, intBytes, 0); //# entries
	returnValue.addField(intBytes);
	return returnValue;
    }

    public static Atom makeSampleDescriptionAtom(Atom[] entries) { //make private?
	/*Returns an atom of type 'stsd', which holds decoding
          information for individual samples.

	  ARGS
	  entries: atom-like things (currently returned only by
	  makeVideoSampleDescription) that have 1 sample's decoding
	  information.  There's a different kind for each of way too
	  many sample formats, so this method does not attempt to
	  check argument validity.*/
	//FIXME: test part common to all entries (first 4 fields) once Atom allows inspection of its fields
	Atom returnValue=makeSampleTableShell("stsd", entries.length);
	for (int index=0; index < entries.length; index++)
	    returnValue.addField(entries[index]);
	return returnValue;
    }

    public static Atom makeTimeToSampleAtom(int[] counts, int[] durations) {
	/*Returns an atom of type 'stts', which describes the
	  durations of media samples in the same unit of time used by
	  the media this atom describes.
	  
	  ARGS
	  
	  durations, counts: Arrays that together map samples to their
	  durations in chronological order, so that the first
	  counts[0] samples are each played for durations[0]
	  time-units and followed by counts[1] samples each played for
	  durations[1] time-units, etc... The groups of counts[n]
	  samples need not correspond to the chunks defined in the
	  sample-to-chunk table.

	  counts and durations must be the same length and contain
	  only positive numbers, otherwise an IllegalArgumentException
	  is thrown. */
	if (counts.length != durations.length)
	    throw new IllegalArgumentException("counts.length (" + counts.length + ") != durations.length (" + durations.length + ')');
	else for (int index=0; index < counts.length; index++) {
	    if (counts[index] <= 0)
		throw new IllegalArgumentException("counts[" + index + "] <= 0: " + counts[index]);
	    else if (durations[index] <= 0)
		throw new IllegalArgumentException("durations[" + index + "] <= 0: " + durations[index]);
	}
	byte[] entry=new byte[8]; //each entry is counts[n] followed by durations[n]
	Atom returnValue=makeSampleTableShell("stts", counts.length);
	for (int index=0; index < counts.length; index++) {
	    Util.storeInByteArray(counts[index], entry, 0);
	    Util.storeInByteArray(durations[index], entry, 4);
	    returnValue.addField(entry);
	}
	return returnValue;
  }

    public static Atom makeVideoSampleDescription(String sampleType, short width, short height,
						  int hRes, int vRes,
						  short colorDepth,
						  int spaceQuality, int timeQuality,
						  short frameCount,
						  short dataRefIndex,
						  String compressorName,
						  Atom colorTable) {
	/*Returns a sample description that can be an element of
          makeSampleDescriptionAtom's argument.  The descripion
          applies to a video sample.

	  ARGS
	  sampleType: depends what kind of video sample.  Raw is "raw ", MPEG is "mpeg" etc.
	  width, height: dimensions of sample in pixels.
	  hRes, vRes: pixels per inch in horizontal & vertical directions respectively.
	  colorDepth: # that describes bits/pixel and whether the image is color (see QuickTime spec for allowed values).
	  spaceQuality: 0 to 1023, higher numbers mean less loss in compressing this sample spatially.
	  timeQuality: 0 to 1024, higher numbers mean less loss in compressing this sample temporally.
	  frameCount: # frames/sample (positive numbers only)
	  dataRefIndex: what # data reference (in 'dref' atom) refers to this sample, 1-based.  Not checked for validity.
	  compressorName: ASCII String describing the compressor used.
	  colorTable: An atom of type ctab.  If null, Quicktime's default color table will be used.  Ignored for grayscale values of colorDepth.

	  Values that don't fit these constraints (e.g colorDepth not
	  a value the Quicktime spec allows, compressorName with
	  nonASCII characters, etc.) throw an
	  IllegalArgumentException.
	
	Partly adapted from code by Ion Moraru, The Virtual Cell. */
	//check args:
	if (!Util.isASCII(sampleType))
	    throw new IllegalArgumentException("sampleType must be ASCII");
	switch(colorDepth) {
	case 1: case 2: case 4: case 8: case 16: case 24: case 32: case 34: case 36: case 40: break;
	default: throw new IllegalArgumentException("colorDepth " + colorDepth + " not supported");
	}
	if (spaceQuality < 0 || spaceQuality > 1023)
	    throw new IllegalArgumentException("spaceQuality " + spaceQuality + " out of 0-1023 range");
	if (timeQuality < 0 || timeQuality > 1024)
	    throw new IllegalArgumentException("timeQuality " + timeQuality + " out of 0-1024 range");
	if (frameCount <= 0)
	    throw new IllegalArgumentException("frameCount " + frameCount + " <= 0");
	if (!Util.isASCII(compressorName)) //then we probably can't print it--just say that:
	    throw new IllegalArgumentException("compressorName has non-ASCII characters");
	//args OK, now build atom:
	Atom returnValue=new Atom(getAtomType(sampleType), false);
	byte[] intBytes=new byte[4], shortBytes=new byte[2];
	returnValue.addField(new byte[6]); //reserved
	//data ref index:
	Util.storeInByteArray(dataRefIndex, shortBytes, 0);
	returnValue.addField(shortBytes);
	//version and revision level (both can/should be 0):
	Arrays.fill(shortBytes, (byte) 0);
	returnValue.addField(shortBytes);
	returnValue.addField(shortBytes);
	//vendor (use default, Apple):
	Util.storeInByteArray(getAtomType("appl"), intBytes, 0);
	returnValue.addField(intBytes);
	//temporal, spatial quality:
	Util.storeInByteArray(timeQuality, intBytes, 0);
	returnValue.addField(intBytes);
	Util.storeInByteArray(spaceQuality, intBytes, 0);
	returnValue.addField(intBytes);
	//width, height:
	Util.storeInByteArray(width, shortBytes, 0);
	returnValue.addField(shortBytes);
	Util.storeInByteArray(height, shortBytes, 0);
	returnValue.addField(shortBytes);
	//horizontal, vertical resolution:
	Util.storeInByteArray(hRes, intBytes, 0);
	returnValue.addField(intBytes);
	Util.storeInByteArray(vRes, intBytes, 0);
	returnValue.addField(intBytes);
	//data size (must be 0 for some reason):
	Arrays.fill(intBytes, (byte) 0);
	returnValue.addField(intBytes);
	//frame count:
	Util.storeInByteArray(frameCount, shortBytes, 0);
	returnValue.addField(shortBytes);
	//compressor name (32-byte Pascal string with \0 for unused characters):
	returnValue.addField(new NativeString(compressorName, NativeString.PASCAL, 32));
	//color depth: 
	Util.storeInByteArray(colorDepth, shortBytes, 0);
	returnValue.addField(shortBytes);
	//color table ID (and if colorTable is non-null, its content):
	if (colorTable==null || colorTable==DEFAULT_CTAB || colorDepth > 32 /*grayscale*/) {
	    Util.storeInByteArray((short) -1, shortBytes, 0); //use default
	    returnValue.addField(shortBytes);
	}
	else {
	    assertAtomIsType(colorTable, "ctab");
	    Arrays.fill(shortBytes, (byte) 0);
	    returnValue.addField(shortBytes);
	    returnValue.addField(colorTable);
	}
	return returnValue;
    }

    public static Atom makeSampleToChunkAtom(int[] chunkGroupStarts, int[] chunkLengths, int[] sampleDescrIDs) {
	/*Returns an atom of type 'stsc', which describes how samples
          that are consecutive (physically and in playing order) are
          grouped into chunks for the sake of description by some
          other atoms.

	  ARGS (arrays where 1st element for 1st group of chunks, 2nd
	  element for 2nd etc.  A chunk group is consecutive chunks
	  w/same # samples each & same descr. in sample description
	  atom):

	  chunkGroupStarts: what # chunk is the first in this chunk
	  group

	  chunkLengths: # of samples for every chunk in a chunk group
	  (1st element for 1st group, 2nd for 2nd etc.)
	
	  sampleDescrIDs: what # entry in the sample description atom
	  ('stsd') applies to this chunk group

	  Throws an IllegalArgumentException if any of the following
	  conditions is not met: all three arguments must be equal in
	  length, they must contain only positive elements, and
	  chunkGroupStarts must have its elements in ascending order
	  starting with 1.*/
	if (chunkGroupStarts[0] != 1)
	    throw new IllegalArgumentException("chunkGroupStarts[0] is " + chunkGroupStarts[0] + " not 1");
	if ((chunkGroupStarts.length != chunkLengths.length) || (chunkLengths.length != sampleDescrIDs.length))
	    throw new IllegalArgumentException("arg lengths " + chunkGroupStarts.length +
					       ", " + chunkLengths.length + " and " + sampleDescrIDs.length +
					       " not equal");
	/*check arg signs & chunkGroupStarts order now, it's cheaper than throwing an exception midway thru
          building atom: */
	for (int index=0, lastCGS=0; index < chunkGroupStarts.length; index++) {
	    if (chunkGroupStarts[index] <= 0)
		throw new IllegalArgumentException("chunkGroupStarts[" + index + "] (" + chunkGroupStarts[index] + ") <= 0");
	    if (chunkLengths[index] <= 0)
		throw new IllegalArgumentException("chunkLengths[" + index + "] (" + chunkLengths[index] + ") <= 0");
	    if (sampleDescrIDs[index] <= 0)
		throw new IllegalArgumentException("sampleDescrIDs[" + index + "] (" + sampleDescrIDs[index] + ") <= 0");
	    if (lastCGS >= chunkGroupStarts[index])
		throw new IllegalArgumentException("chunkGroupStarts[" + index + "] breaks ascending order");
	}
	//args OK so make the atom:
	byte[] field=new byte[12]; //field n = concat'd byte arrays of each arg's element n
	Atom returnValue=makeSampleTableShell("stsc", chunkLengths.length);
	for (int index=0; index < chunkLengths.length; index++) {
		Util.storeInByteArray(chunkGroupStarts[index], field, 0);
		Util.storeInByteArray(chunkLengths[index], field, 4);
		Util.storeInByteArray(sampleDescrIDs[index], field, 8);
		returnValue.addField(field);
	}
	return returnValue;
    }

    public static Atom makeChunkOffsetAtom(Offset[] offsets) {
	/*Returns an atom of type 'stco' (or 'co64' if offsets'
	  elements point into a file >2Gb) that holds the contents of
	  offsets.

	  ARGS
	  offsets: The offsets into their Quicktime file of the
	  sample-data chunks in order.  If they don't all agree on
	  whether the file can be >2Gb, an IllegalArgumentException is
	  thrown.*/
	boolean lastOffsetIsWide=offsets[0].pointsIntoLargeFile();
	for (int index=1; index < offsets.length; index++) {
	    boolean thisOffsetIsWide=offsets[index].pointsIntoLargeFile();
	    if (thisOffsetIsWide != lastOffsetIsWide)
		throw new IllegalArgumentException("inconsistent offset widths (first: " + index + ')');
	}
	Atom returnValue=makeSampleTableShell(lastOffsetIsWide? "co64": "stco", offsets.length);
	for (int index=0; index < offsets.length; index++)
	    returnValue.addField(offsets[index]);
	return returnValue;
    }

    public static Atom makeSampleSizeAtom(int[] sampleSizes) {
	/*Returns an atom of type 'stsz' containing, in order, the
          sizes of the samples in a media.  The argument's elements
          are those sizes--positive numbers only or else it throws an
          IllegalArgumentException.

	  Algorithm from code by Ion Moraru, The Virtual Cell.*/
	boolean commonSize=true;
	for (int index=0; index < sampleSizes.length; index++) {
	    /*check arg correctness (and equality while we're at it,
              we might get to use 1 entry for all samples): */
	    if (sampleSizes[index] <= 0)
		throw new IllegalArgumentException("sampleSizes[" + index + "] "
						   + sampleSizes[index] + "<= 0");
	    if (index != 0 && commonSize)
		commonSize=(sampleSizes[index]==sampleSizes[index-1]);
	}
	Atom returnValue=new Atom(getAtomType("stsz"), false);
	//can't use makeSampleTableShell(), we need a field for common size before #-of-entries field
	returnValue.addField(new byte[1]); //version--can be 0
	returnValue.addField(new byte[3]); //flags--must be 0
	byte[] intBytes=new byte[4];
	/*common size of all samples (if they don't all have their
          size in common, show that with 0): */
	Util.storeInByteArray((commonSize? sampleSizes[0]: 0), intBytes, 0);
	returnValue.addField(intBytes);
	//# samples in this media:
	Util.storeInByteArray(sampleSizes.length, intBytes, 0);
	returnValue.addField(intBytes);
	if (!commonSize) //need to give the sizes individually:
	    for (int index=0; index < sampleSizes.length; index++) {
		Util.storeInByteArray(sampleSizes[index], intBytes, 0);
		returnValue.addField(intBytes);
	    }
	return returnValue;
    }


    //end sample-table methods

    private static Atom makeHandlerReferenceAtom(String type, String subtype) {
	/*Returns an atom of type 'hdlr' that describes the handler
	  necessary to open the kind of data described by subtype.

	  ARGS
	  type: data handler vs. media handler
	  subtype: broad category of data to handle (URL, video, etc.)

	  Throws an IllegalArgumentException if it doesn't recognize
	  type or subtype.*/
	String typeName, subtypeName; //used in component name
	if (type.equals("dhlr")) {
	    typeName="Data";
	    if (subtype.equals("alis"))
		subtypeName="Alias"; //source: Ion Moraru, The Virtual Cell
	    else if (subtype.equals("rsrc"))
		subtypeName="Resource"; //source: Google + educated guessing
	    else if (subtype.equals("url "))
		subtypeName="URL"; //source: ditto
	    else throw new IllegalArgumentException("data handler subtype " + subtype + " not recognized");
	}
	else if (type.equals("mhlr")) {
	    typeName="Media";
	    if (subtype.equals("vide"))
		subtypeName="Video";
	    else if (subtype.equals("soun"))
		subtypeName="Sound";
	    else throw new IllegalArgumentException("media handler subtype " + subtype + " not recognized");
	}
	else throw new IllegalArgumentException("handler type " + type + " not recognized");
	byte[] intBytes=new byte[4];
	Atom returnValue=new Atom(getAtomType("hdlr"), false);
	returnValue.addField(new byte[1]); //version
	returnValue.addField(new byte[3]); //flags
	//handler type:
	Util.storeInByteArray(getAtomType(type), intBytes, 0);
	returnValue.addField(intBytes);
	//handler subtype:
	Util.storeInByteArray(getAtomType(subtype), intBytes, 0);
	returnValue.addField(intBytes);
	//handler manufacturer (must be 0 for some reason):
	Arrays.fill(intBytes, (byte) 0);
	returnValue.addField(intBytes);
	//more flags and flags mask (also 0):
	returnValue.addField(intBytes);
	returnValue.addField(intBytes);
	//name of handler:
	returnValue.addField(new NativeString("Apple " + subtypeName + ' ' + typeName + " Handler",
					      NativeString.PASCAL));
	return returnValue;
    }

    public static Atom makeMediaHandlerReferenceAtom(String subtype) {
	/*Returns an atom of type 'hdlr' that describes what media
	  handler should interpret the media data once the data
	  handler reads it.

	  ARGS subtype: what medium ('vide' for video, 'soun' for
	  sound--anything else throws an IllegalArgumentException) */
	return makeHandlerReferenceAtom("mhlr", subtype);
    }

    public static Atom makeDataHandlerReferenceAtom(String subtype) {
	/*Returns an atom of type 'hdlr' that describes what data
          handler should read the media data from wherever it's
          stored.

	  ARGS subtype: what is used to physically locate the media
	  ('alis' for Mac alias, 'rsrc' for Mac resource, 'url ' for
	  URL--anything else throws an IllegalArgumentException) */
	return makeHandlerReferenceAtom("dhlr", subtype);
    }

    public static Offset store1VideoFrameToMdat(Atom mdat, Image img, short[] bitDepths, int[] sizes, Atom[] colorTables, boolean wideFile) {
	/*Adds img as 1 frame of raw RGB video to an atom of type
          'mdat', returning an Offset to the frame.  If wideFile is
          true, the returned Offset will support file sizes larger
          than 2Gb.  The frame's bit depth (expressed in QuickTime's
          bit-depth numbering system), size, and color table (if
          necessary for the bit depth used) are stored in the array
          arguments which are passed for that purpose, at the index of
          the first zero or null element.  (An
          ArrayIndexOutOfBoundsException is thrown if all the elements
          are full.)

	  This method tries to optimize the frame's encoding in mdat:
	  the minimum bit depth that can hold a pixel in img's
	  ColorModel will be used, up to a maximum of 32 bits total
	  and 8 bits per channel (larger pixels and channels will be
	  scaled down).  Color and alpha will only be used if actual
	  pixels from img use them; they can be omitted even if the
	  ColorModel includes them.

	  This method throws an IllegalArgumentException if mdat is
	  not type 'mdat', if mdat's size is odd before the frame is
	  added (because that breaks the proper alignment of pixel
	  rows in the atom), or if the image's color model must
	  represent a pixel with more than one array element.  If no
	  exception is thrown this method is guaranteed to leave the
	  atom with even size.*/
	assertAtomIsType(mdat, "mdat");
	if (mdat.getSize()%2 != 0) //the new sample would be misaligned if added:
	    throw new IllegalStateException(mdat + " must end at a word boundary");
	//get the raw pixel data:
	PixelGrabber pixelGrabber=new PixelGrabber(img, 0, 0, -1, -1, false);
	//we don't know width & height, so -1 for width/height means grab whole thing
	boolean pixelsGrabbed, useAlpha, grayscale;
	try {
	    pixelsGrabbed=pixelGrabber.grabPixels(); //wait until they're grabbed
	} catch (InterruptedException e) {
	    pixelsGrabbed=false;
	}
	if (!pixelsGrabbed)
	    return null;
	int width=pixelGrabber.getWidth(), height=pixelGrabber.getHeight(),
	    bitDepth=pixelGrabber.getColorModel().getPixelSize();
	Object pixels=pixelGrabber.getPixels();
	ColorModel cm=pixelGrabber.getColorModel();
	useAlpha=false; grayscale=true; //until counterexample pixel found, below
    	byte[] rawPixels= //new byte[width*height*4]
	    makeRGBA(pixels, cm);
	final int RED=0, GREEN=1*width*height, BLUE=2*width*height, ALPHA=3*width*height;
	//get absolute RGB values:
	{ boolean neverColor=cm.getColorSpace().getType()==ColorSpace.TYPE_GRAY,
	      neverAlpha=!cm.hasAlpha();
	    for (int index=0; index < width*height; index++) { //find best way to represent pixels in mdat:
		useAlpha=!neverAlpha && (useAlpha || rawPixels[ALPHA+index] != -1); //really 255, opaque
		grayscale=neverColor || (grayscale && !useAlpha //QT can't store an alpha channel with grayscale
					 && (rawPixels[RED+index]==rawPixels[GREEN+index])
					 && (rawPixels[GREEN+index]==rawPixels[BLUE+index]));
		if (index==width*height-1 && grayscale)
		    //then bitdepth may include many channels when only 1 is needed.  Fix that:
		    bitDepth=cm.getComponentSize(0);
	    }
	}
	pixelGrabber=null; img=null;
	//get bitdepth and size in memory of output:
	if (useAlpha) bitDepth=32; //only one that allows alpha
	else if (bitDepth==32) bitDepth=24; //don't use alpha channel
	else { //round nonstandard bit depths up to next (or down to biggest) legal one:
	    int[] okBitDepths;
	    if (grayscale) okBitDepths=new int[] {1, 2, 4, 8};
	    else okBitDepths=new int[] {1, 2, 4, 8, 16, 24, 32};
	    for (int index=okBitDepths.length-1; index >= 0; index--)
		if (okBitDepths[index] <  bitDepth) {
		    bitDepth=okBitDepths[index+1];
		    break;
		}
	}
	//now we have 2 representations of the pixel data, gc the extra one:
	if (!grayscale && bitDepth <= 8) //we only need the colortable & pixels-as-indices:
	    rawPixels=null;
	else { //we only need the derived RGBA values:
	    cm=null;
	    pixels=null;
	}
	int scanlineSize=(width*bitDepth + (16 - ((width*bitDepth)%16)) % 16)/8;
	//bytes/scanline including extra to align next scanline at word boundary
	System.gc();
	//pack pixels into byte array:
  	final byte[] encodedPixels=new byte[scanlineSize*height];
  	if (grayscale | bitDepth <= 8) { //encoded same way, give or take color table elsewhere
	    int pixelsPerByte=8/(bitDepth > 8? 8: bitDepth);
	    for (int vIndex=0; vIndex < height; vIndex++) { //iterate over rows of raw pixels
		int pixelXCoord=0;
		for (int bIndex=0; pixelXCoord < width; bIndex++) {
		    /*Iterate over encoded bytes of raw pixels in this
                      scanline.  Don't check bIndex, pixelXCoord will
                      max out first (then we can stop writing this
                      scanline--the rest is garbage-values for
                      padding).*/
		    byte encodedPixel=0;
		    for (int pIndex=0; pIndex < pixelsPerByte; pIndex++) {
			/*Iterate over pixels in encoded byte, which
                          just get packed together (source: some
                          Quicktime movies + a hex-editor). */
			byte[] bytePixels=null; int[] intPixels=null;
			if (pixels instanceof int[])
			    intPixels=(int[]) pixels;
			else
			    bytePixels=(byte[]) pixels;
			encodedPixel <<= bitDepth;
			if (pixelXCoord < width) {
			    encodedPixel |=
				(grayscale?
				 //use an arbitrary color's channel (inverted because QT unlike the AWT says 0 is brightest)
				 invertChannel(unscaleChannel(rawPixels[RED + vIndex*width + pixelXCoord], bitDepth), bitDepth):
				 //use a color table index verbatim (only found as element of pixels):
				 
				  (bytePixels==null?
				   intPixels[vIndex*width + pixelXCoord]:
				   bytePixels[vIndex*width + pixelXCoord]));
			}
			pixelXCoord++;
		    }
		    encodedPixels[vIndex*scanlineSize + bIndex]=encodedPixel;
		}
	    }
	}
  	else { //store RGB channels:
  	    switch(bitDepth) {
	    case 16:
		for (int vIndex=0; vIndex < height; vIndex++)
		    for (int hIndex=0; hIndex < width; hIndex++) {
			int rawIndex=vIndex*width + hIndex,
			    encIndex=vIndex*scanlineSize + hIndex*2;
			Util.storeInByteArray(pack16BitPixel(rawPixels[RED+rawIndex],
							     rawPixels[GREEN+rawIndex],
							     rawPixels[BLUE+rawIndex]),
					      encodedPixels, encIndex);
		    } break;
	    case 24: case 32:
		byte[] channels=new byte[bitDepth/8];
		for (int vIndex=0; vIndex < height; vIndex++) {
		    int encIndex=vIndex*scanlineSize;
		    for (int hIndex=0; hIndex < width; hIndex++) {
			int rawIndex=vIndex*width + hIndex;
			channels[0]=rawPixels[RED+rawIndex];
			channels[1]=rawPixels[GREEN+rawIndex];
			channels[2]=rawPixels[BLUE+rawIndex];
			if (bitDepth==32)
			    channels[3]=rawPixels[ALPHA+rawIndex];
			for (int cIndex=0; cIndex < channels.length; cIndex++)
			    encodedPixels[encIndex++]=channels[cIndex];
		    }
		} break;
	    default: throw new IllegalArgumentException("Unsupported bit depth " + bitDepth);
	    }
  	}
	//"return" info about the frame to array args:
	{
	    int index;
	    for (index=0; index < bitDepths.length && bitDepths[index] != 0; index++);
	    if (index==bitDepths.length) throw new ArrayIndexOutOfBoundsException("bitDepths array is full");
	    else bitDepths[index]=(short) (!grayscale || bitDepth==1? //frames of bit depth 1 stored same way in grayscale & color--only the color table differs
				   bitDepth: bitDepth+32); //extra 32 marks frame as grayscale
	    for (index=0; index < sizes.length && sizes[index] != 0; index++);
	    if (index==sizes.length) throw new ArrayIndexOutOfBoundsException("sizes array is full");
	    else sizes[index]=scanlineSize*height;
	    for (index=0; index < colorTables.length && colorTables[index] != null; index++);
	    if (index==colorTables.length) throw new ArrayIndexOutOfBoundsException("color tables array is full");
	    else {
		if (!(grayscale && bitDepth > 1) && bitDepth <= 8) //we're using indexed color:
		    colorTables[index]=makeColorTableAtom(cm, bitDepth);
		else //the default color table is OK:
		    colorTables[index]=DEFAULT_CTAB;
		//FIXME: provide way to use a default or built-in colortable even with indexed color
	    }
	}
  	return mdat.addFieldAndGetOffset(
					 new Writable(false) { //1 frame < 2Gb
						 public InputStream getWritableForm() {
						     return new ByteArrayInputStream(encodedPixels);
						 }
						 public long getSize() {
						     return encodedPixels.length;
						 }
					     }, //need a Writable to wrap the pixel array so Offset can point to it
					 wideFile);
    }

    private static byte unscaleChannel(byte channel, int bitDepth) {
	/*Returns the value of channel, a pixel's component originally
	  bitDepth wide, as it was before getting scaled to 0-255.
	  bitDepth must be in the range 1-8 inclusive. */
	if (bitDepth < 1 || bitDepth > 8) //sanity check
	    throw new IllegalArgumentException("bit depth " + bitDepth + " > 8");
	else return (byte) (channel >> (8-bitDepth));
    }

    private static byte invertChannel(byte channel, int bitDepth) {
	/*Where channel is a pixel's component scaled to be bitDepth
          wide, returns the difference between channel and the maximum
          value for that component.  bitDepth must be in the range 1-8
          inclusive; if bitDepth implies a maximum value smaller than
          channel, the smallest bit depth that fits channel is used
          instead. */
	if (bitDepth < 1 || bitDepth > 8) //sanity check
	    throw new IllegalArgumentException("bit depth " + bitDepth + " > 8");
	//fix too-small bitDepth:
	while ((2 << bitDepth) <= (channel & 0xFF)) bitDepth++;
	return (byte) (((2 << bitDepth) -1) - (channel & 0xFF));
    }
    
    private static short pack16BitPixel(byte r, byte g, byte b) {
	/*Returns the pixel with the given RGB values, represented
	  in the 16-bit form required by an mdat atom. That form
	  is high byte 0, remainder 5 bytes each for RG&B in that
	  order. */
	return (short) (((unscaleChannel(r, 5) & 0xFF) << 10) |
			((unscaleChannel(g, 5) & 0xFF) << 5) |
			(unscaleChannel(g, 5) & 0xFF));
    }
    
    private static Atom makeColorTableAtom(ColorModel cm, int bitDepth) {
	/*Returns an atom of type 'ctab' with the same
	  integer-to-RGB mappings as cm for every integer bitDepth
	  bits long.  If bitDepth is greater than 16, throws an
	  IllegalArgumentException. */
	//FIXME: find way to encode RGB-vector to scalar (and make sure doesn't happen to IndexColorModel)
	if (bitDepth > 16)
	    throw new IllegalArgumentException("Bit depth " + bitDepth + " > 16-bit max");
	Atom returnValue=new Atom(getAtomType("ctab"), false);
	returnValue.addField(new byte[4]); //color table seed (spec requires 0 value)
	byte[] shortBytes=new byte[2];
	Util.storeInByteArray((short) 0x8000, shortBytes, 0); //flags (value mandated by spec)
	returnValue.addField(shortBytes);
	Util.storeInByteArray((short) ((1 << bitDepth) - 1), shortBytes, 0); //index of last color in table
	returnValue.addField(shortBytes);
	//build color table:
	int[] indices=new int[1 << bitDepth];
	for (int index=0; index < indices.length; index++) //take all possible color-table indices...
	    indices[index]=index;
	byte[] rawColors=makeRGBA(indices, cm), //...and get corresponding colors.
	    encodedColor=new byte[4];
	final int RED=0, GREEN=1*indices.length, BLUE=2*indices.length;
	for (int index=0; index < indices.length; index++) {
	    //encodedColor[0] must stay blank
	    encodedColor[1]=rawColors[RED+index];
	    encodedColor[2]=rawColors[GREEN+index];
	    encodedColor[3]=rawColors[BLUE+index];
	    returnValue.addField(encodedColor);
	}
	return returnValue;
    }
    
    private static byte[] makeRGBA(Object pixels, ColorModel cm) {
	/*Where pixels is byte[] or int[] of length n, returns
	  an array of length 4n containing the color and alpha
	  components of pixels as defined by cm.  The returned
	  array has each channel's values in a stretch of n
	  consecutive elements; the channels appear in RGBA
	  order.
	  
	  If pixels is not byte[] or int[], an
	  IllegalArgumentException is thrown. */
	int[] intPixels=null;
	byte[] bytePixels=null, returnValue;
	int length, RED=0, GREEN=1, BLUE=2, ALPHA=3;
	if (pixels instanceof int[]) {
	    intPixels=(int[]) pixels;
	    length=intPixels.length;
	}
	else if (pixels instanceof byte[]) {
	    bytePixels=(byte[]) pixels;
	    length=bytePixels.length;
	}
	else throw new IllegalArgumentException(pixels + " is not byte[] or int[]");
	returnValue=new byte[4*length];
	boolean cmCantDoScalars=(!(cm instanceof IndexColorModel || cm instanceof DirectColorModel));
	    //it still might do scalars but no guarantee or simple way to find out
	DataBuffer db=null;  SampleModel sm=null; //used in getting array form of pixels
	if (cmCantDoScalars) {
	    /*then under current implementation, pixels is probably
              wrong.  FIXME: don't throw this exception once
              store1VideoFrameToMdat can grab multi-element pixels
              correctly. */
	    throw new IllegalArgumentException("Only 1 array element per pixel supported");
	}
	for (int index=0; index < length; index++) {
	    if (cmCantDoScalars) {
		Object arrayPixel=sm.getDataElements(index, 0, null, db);
		//FIXME: take right # elements from pixel array directly
		returnValue[RED*length + index]=(byte) cm.getRed(arrayPixel);
		returnValue[GREEN*length + index]=(byte) cm.getGreen(arrayPixel);
		returnValue[BLUE*length + index]=(byte) cm.getBlue(arrayPixel);
		returnValue[ALPHA*length + index]=(byte) cm.getAlpha(arrayPixel);
	    }
	    else {
		int pixel=(intPixels==null? 0xFF & bytePixels[index]: intPixels[index]);
		returnValue[RED*length + index]=(byte) cm.getRed(pixel);
		returnValue[GREEN*length + index]=(byte) cm.getGreen(pixel);
		returnValue[BLUE*length + index]=(byte) cm.getBlue(pixel);
		returnValue[ALPHA*length + index]=(byte) cm.getAlpha(pixel);
	    }
	}
	return returnValue;
    }

    //alternate form of PixelGrabber (for future use w/pixels not representable as int?)

    private static class PixelThief extends PixelGrabber {
	/*Does debugging-related things with grabbed pixels. */
	static Field intArrayField, byteArrayField, colorModelField;

	public PixelThief(Image img, int x, int y, int w, int h, boolean forceRGB) {
	    super(img, x, y, w, h, forceRGB);
	    boolean colorModelBuiltIn=isColorModel(); //reflection is slow, don't do it twice
	    System.out.println("Color model exists at construction: " + colorModelBuiltIn);
	    //if it does exists we want to know what it is:
	    if (colorModelBuiltIn) try {
		System.out.println("    " + colorModelField.get(this));
	    } catch (IllegalAccessException e) {
		//if we got this far, access to colorModelField should not be failing:
		throw new InternalError("You broke the reflection thingy!");
	    }
	}

	static { //reflection hack to make the pixel arrays visible
	    try {
		(byteArrayField=PixelGrabber.class.getDeclaredField("bytePixels")).setAccessible(true);
		(intArrayField=PixelGrabber.class.getDeclaredField("intPixels")).setAccessible(true);
		(colorModelField=PixelGrabber.class.getDeclaredField("imageModel")).setAccessible(true);
	    } catch (NoSuchFieldException e) {
		throw new InternalError("Nothing to see here, move along.");
	    }
	}

	public void setPixels(int srcX, int srcY, int srcW, int srcH, ColorModel model, byte[] pixels, int srcOff, int srcScan) {
	    ij.IJ.log("--- PIXELS IN ---");
	    dumpPixels(pixels);
//  	    System.out.println("PIXELS ALREADY EXIST AS:");
//  	    System.out.println("    byte: " + isBytePixelArray());
//  	    System.out.println("     int: " + isIntPixelArray());
//  	    super.setPixels(srcX, srcY, srcW, srcH, model, pixels, srcOff, srcScan);
//  	    System.out.println("Got byte-pixels in " + model.getClass().getName() + '@' + System.identityHashCode(model));
//  	    System.out.println("Now they're in " + getColorModel().getClass().getName() + '@' + System.identityHashCode(getColorModel()));
	    super.setPixels(srcX, srcY, srcW, srcH, model, pixels, srcOff, srcScan);
	    ij.IJ.log("--- PIXELS OUT ---");
	    dumpPixels(getPixels());
	}

	public void setPixels(int srcX, int srcY, int srcW, int srcH, ColorModel model, int[] pixels, int srcOff, int srcScan) {
	    ij.IJ.log("--- PIXELS IN ---");
	    dumpPixels(pixels);
//  	    System.out.println("PIXELS ALREADY EXIST AS:");
//  	    System.out.println("    byte: " + isBytePixelArray());
//  	    System.out.println("     int: " + isIntPixelArray());
//  	    super.setPixels(srcX, srcY, srcW, srcH, model, pixels, srcOff, srcScan);
//  	    System.out.println("Got int-pixels in " + model.getClass().getName() + '@' + System.identityHashCode(model));
//  	    System.out.println("Now they're in " + getColorModel().getClass().getName() + '@' + System.identityHashCode(getColorModel()));
	    super.setPixels(srcX, srcY, srcW, srcH, model, pixels, srcOff, srcScan);
	    ij.IJ.log("--- PIXELS OUT ---");
	    dumpPixels(getPixels());
	}

	private void dumpPixels(Object pixels) {
	    for (int index=0; index < Array.getLength(pixels); index++)
		ij.IJ.log(""+Array.getInt(pixels, index));
	}

	private boolean isBytePixelArray() {
	    //Returns true if this already has some grabbed pixels stored as a byte array.
	    try {
		return byteArrayField.get(this) != null;
	    } catch (IllegalAccessException e) {
		throw new InternalError("MY ARRAY!  Hands off!");
	    }
	}

	private boolean isIntPixelArray() {
	    //Returns true if this already has some grabbed pixels stored as an int array.
	    try {
		return intArrayField.get(this) != null;
	    } catch (IllegalAccessException e) {
		throw new InternalError("NOOOOOO, Kitty, it's MY array!");
	    }
	}

	private boolean isColorModel() {
	    //Returns true if this already has a color model.
	    try {
		return colorModelField.get(this) != null;
	    } catch (IllegalAccessException e) {
		throw new InternalError("You touch my ColorModel, I crash your program...");
	    }
	}

	public void setDimensions(int width, int height) {
	    System.out.println("Before dimensions set: ");
	    System.out.println("      int pixels exist: " + isIntPixelArray());
	    System.out.println("     byte pixels exist: " + isBytePixelArray());
	    System.out.println("    color model exists: " + isColorModel());
	    super.setDimensions(width, height);
	    System.out.println("Dimensions set.");
	    System.out.println("STACK TRACE:");
	    new Exception("Ignore me, I'm only here to do a stack trace.").printStackTrace();
	}

    }

}
