package gloworm.qtvr;
import java.util.*;
import java.io.*;

public class Atom extends Writable {
    /*Quicktime files' basic data structure.  Consists of various
      fields (either wrapped primitives or other atoms) in fixed
      order, the first 2 fields being size (in bytes) and type (a
      32-bit int).  What the other fields are and how they're
      arranged is up to the particular atom type.

      Note that this is a "classic" Quicktime atom, not the more
      modern "QT Atom" which has fields to explicitly describe
      its place in a tree structure.

      Atoms larger than 2 Gb are supported without the use of the
      "wide" atom as placeholder. */

    private int type; //nonfinal because of "blank final variable" compiler bug
    private Vector fields=new Vector();
    //amt. space taken up by mandatory numeric fields, as spec defines:
    public static final int MIN_SIZE=8; //4 bytes type + 4 bytes size
    public static final int WIDE_MIN_SIZE=MIN_SIZE+8; 
    /*extra 8-byte size field because usual 4-byte field too small &
      used just to mark this as a wide atom */

    public Atom(int type, boolean canBeWide) {
	super(canBeWide);
	this.type=type;
    }

    public long getSize() {
	long returnValue=(this.canBeWide()? WIDE_MIN_SIZE: MIN_SIZE);
	for (int index=0; index < fields.size(); index++)
	    returnValue += ((Field) fields.elementAt(index)).getSize();
	return returnValue;
    }

    public int getType() {
	return type;
    }

    protected final long getOffsetOf(Writable w) {
	/*Returns how many bytes into this atom's writable form the
          field w (not another atom equal to w!) directly appears, or
          throws an IllegalArgumentException if w is not a field of
          this Atom.  ("Field of" is not transitive for the purposes
          of this method.)

	  This method is only protected because the method it
	  overrides is too; in general, calling this method from a
	  subclass will throw the exception because of the defensive
	  copying within fields. */
	long startOffset=(this.canBeWide()? WIDE_MIN_SIZE: MIN_SIZE),
	    offset=startOffset;
	//see if w is a field of this:
	int wIndex=-1;
	for (int index=0; index < fields.size() && wIndex < 0; index++)
	    if (((Field) fields.elementAt(index)).contents==w)
		wIndex=index;
	if (wIndex < 0) throw new IllegalArgumentException(w + " is not a field of " + this);
	//now do the actual offset-calculating:
	for (int index=0; index < wIndex; index++)
	    offset += ((Field) fields.elementAt(index)).getSize();
	return offset;
    }

    private class Field {
	private Object contents;
	private final int type; //what sort of data this field holds
	private static final int OFFSET=0, WRITABLE=1, RAW_BYTES=2;

	Field(Offset o) {
	    contents=o; //it's OK, they're immutable
	    this.type=OFFSET;
	}

	Field(Writable w) {
	    Writable clone=(Writable) w.clone();
	    clone.setContainer(Atom.this);
	    contents=clone;
	    this.type=WRITABLE;
	}

	Field(byte[] b) {
	    contents=b.clone();
	    this.type=RAW_BYTES;
	}

	long getSize() {
	    /*Returns the number of bytes this Field's contents will
	      take up when its containing atom is written into a file.
	      If this field contains a mutable Writable, changes to
	      the Writable will render previously-returned values
	      obsolete.*/
	    switch(type) {
	    case OFFSET: return ((Offset) contents).pointsIntoLargeFile()? 8: 4;
	    case WRITABLE: return ((Writable) contents).getSize();
	    case RAW_BYTES: return ((byte[]) contents).length;
	    default: throw new Error("Unknown field type: " + contents); //shouldn't
	    }
	}

	InputStream getWritableContents() {
	    /*Returns the representation of this Field as it will
	      appear when its containing atom is written to file.  If
	      this field contains a mutable Writable, the part of the
	      return value corresponding to it will be affected
	      according to that Writable's policy for
	      getWritableForm().*/
	    synchronized(contents) {
		switch(type) {
		case WRITABLE: return ((Writable) contents).getWritableForm();
		case RAW_BYTES: return new ByteArrayInputStream((byte[]) contents);
		case OFFSET:
		    byte[] returnValue;
		    if (((Offset) contents).pointsIntoLargeFile()) {
			returnValue=new byte[8];
			Util.storeInByteArray(((Offset) contents).getOffset(),
					 returnValue, 0);
		    } else {
			returnValue=new byte[4];
			Util.storeInByteArray((int) ((Offset) contents).getOffset(),
					 returnValue, 0);
		    }
		    return new ByteArrayInputStream(returnValue);
		default: throw new Error("No way to write field " + contents); //shouldn't
		}
	    }
	}
    }

    public synchronized Object clone() {
	/*Returns an Atom whose writable form is identical in content
          to this one's.*/
	Atom returnValue=(Atom) super.clone();
	returnValue.fields=new Vector();
	for (int index=0; index < fields.size(); index++) {
	    //returnValue.fields.addElement(((Field) fields.elementAt(index)).defensiveCopy());
	    //bad move!  The new Field will be returnValue's but have this for its enclosing instance...
	    Object field=((Field) fields.elementAt(index)).contents;
	    if (field instanceof byte[])
		returnValue.addField((byte[]) ((byte[]) field).clone());
	    else if (field instanceof Offset)
		returnValue.addField((Offset) field); //safe, Offsets are immutable
	    else if (field instanceof Writable) {
		field=((Writable) field).clone();
		((Writable) field).setContainer(returnValue);
		returnValue.addField((Writable) field);
	    }
	    else throw new Error("No way to write field " + field); //shouldn't
	}
	return returnValue;
    }
    
    public synchronized void addField(Writable w) {
	/*Stores a defensive copy of w as the next field in this
          atom.*/
	fields.addElement(new Field(w)); //the constructor does the cloning
    }

    public synchronized Offset addFieldAndGetOffset(Writable w, boolean wideContainer) {
	/*Stores a defensive copy of w as the next field in this atom
          and returns an Offset describing its location in this Atom's
          movie file.  wideContainer should be true if this atom will
          be stored in a file that might exceed 2Gb. */
	Field f=new Field(w);
	fields.addElement(f);
	return new Offset((Writable) f.contents, wideContainer);
    }

    public synchronized void addField(byte[] b) {
	/*Stores a copy of b as the next field in this atom.*/
	fields.addElement(new Field(b));
    }

    public synchronized void addField(Offset o) {
	/*Stores o as the next field in this atom.*/
	fields.addElement(new Field((Offset) o));
    }

    public final synchronized InputStream getWritableForm() {
	/*Returns a representation of this Atom that can be written
          directly to file.  The contents of the InputStream returned
          by a particular call to this method are fixed at the time
          this method is called; adding fields will not affect the
          contents of an InputStream this method has previously
          returned.*/
	byte[] header;
	int sizeField;
	if (this.canBeWide()) {
	    header=new byte[WIDE_MIN_SIZE];
	    sizeField=1; //indicates real size is the 8 bytes after type
	    Util.storeInByteArray(getSize(), header, 8);
	} else {
	    header=new byte[MIN_SIZE];
	    sizeField=(int) getSize();
	}
	Util.storeInByteArray(sizeField, header, 0);
	Util.storeInByteArray(type, header, 4);
	/*Now collect the parts of the writable form, in a way that
          keeps returnValue safe from further changes to this atom:*/
	Vector writableParts=new Vector(fields.size()+1);
	writableParts.addElement(new ByteArrayInputStream(header));
	for (int index=0; index < fields.size(); index++)
	    writableParts.addElement(((Field) fields.elementAt(index)).getWritableContents());
	//assemble the whole mess into a single InputStream & return it:
	return new SequenceInputStream(writableParts.elements());
    }

    public String toString() {
	/*Type & hash code for prettier error messages.  Full details
          come from dumpContents(). */
	return AtomFactory.stringifyAtomType(type) + " Atom #" + System.identityHashCode(this);
    }

    public void dumpContents() {
	/*Recursively lists the atom's contents depth-first in
          human-readable form on System.out, for ease of
          debugging. Each field is labeled by level of nesting, with
          fields of this being labeled 1: and their fields being
          labeled 2: ... */
	dumpContentsImpl(1);
    }

    private void dumpContentsImpl(int level) {
	//level = level of nesting
	synchronized(fields) {
	    for (int index=0; index < fields.size(); index++) {
		Field theField=(Field) fields.elementAt(index);
		System.out.print(level + ": ");
		switch (theField.type) {
		case Field.OFFSET:
		    System.out.println("Offset to " +
				       ((Offset) theField.contents).targetString());
		    break;
		case Field.RAW_BYTES:
		    byte[] theBytes=(byte[]) theField.contents;
		    String[] hexStrings=new String[theBytes.length];
		    for (int i=0; i < theBytes.length; i++) {
			int theByte=theBytes[i] & 0xFF;
			hexStrings[i]=(theByte < 0x10? "0x0": "0x") + Integer.toHexString(theByte);
		    }
		    System.out.println(Arrays.asList(hexStrings));
		    break;
		case Field.WRITABLE:
		    System.out.println(theField.contents);
		    if (theField.contents instanceof Atom)
			((Atom) theField.contents).dumpContentsImpl(level+1);
		}
	    }
	}
    }
}
