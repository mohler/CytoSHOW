package gloworm.qtvr;

abstract class Writable implements Cloneable {
    /*Extended by data structures which can be written into a
      Quicktime file.  Mutable implementations should carefully
      specify how changing the internal state of an instance will
      affect the nature and correctness of what the Writable methods
      of that instance may have previously returned.  In particular,
      unless there is a good reason to do otherwise, a mutable
      instance of Writable should simply become immutable (at least
      with respect to parts of its state that affect its writable
      form) the first time a Writable method is called.

      Accessor methods which affect or are affected by any part of
      internal state reflected in the writable form should all be
      declared synchronized.

      Note that all Writables which contain other Writables should
      override getOffset and ensure that their internal structure is
      compatible with the correct operation of this method (see its
      comment).  They should also take measures to ensure they can't
      contain themselves, otherwise their writable forms will be
      infinitely long.*/

    private boolean canBeWide;
    private Writable container; 

    public abstract java.io.InputStream getWritableForm(); //what's read from return value gets written to file
    public abstract long getSize(); //returns length in bytes of the writable form
    public Object clone() {
	/*This implementation returns a Writable that's a shallow copy
          of this except that getContainer() returns null.  Override
          it to return a copy that's deep with respect to all mutable
          members, in which all member Writables have had setContainer
          called with the copy as an argument.*/
	Writable clone;
	try {
	    clone=(Writable) super.clone();
	} catch (CloneNotSupportedException e) {
	    //can't happen, this instanceof Cloneable
	    throw new InternalError("Cloneable isn't.");
	}
	clone.container=null;
	return clone;
    }

    Writable(boolean canBeWide) { //add diskSwappable arg later
	/*canBeWide is true if this Writable's size is expected to
          exceed 2Gb.  If diskSwappable is true, this Writable will
          make an ongoing effort to minimize its size in memory by
          storing components of itself on disk.  It should be true for
          Writables which contain media data or deeply-nested data,
          and will be assumed true for Writables which canBeWide.*/
	this.canBeWide=canBeWide;
    }

    boolean canBeWide() {
	return canBeWide;
    }

    final Writable getContainer() {
	/*Returns the Writable w, if any, that will explicitly output
          this's Writable form as part of its own (not just as part of
          some other Writable field of w); otherwise returns null.*/
	return container;
    }

    final void setContainer(Writable container) {
	/*If getContainer() is already non-null, throws an
          IllegalStateException.  If this is a QTFile, throws an
          UnsupportedOperationException.*/
	if (this.container==null) {
	    if (this instanceof QTFile)
		throw new UnsupportedOperationException("QTFile can't have a container");
	    else
		this.container=container;
	}
	else throw new IllegalStateException("container already set");
	/*not enforced at compile time by declaring container final
          because it would 1) force caller to have container at
          instantiation and 2) screw up clone*/
    }

    protected long getOffsetOf(Writable x) {
	/*If this directly contains x, this method should return the
	  number of bytes preceeding x's writable form in the writable
	  form of this.  If this does not directly contain x, this
	  method should return a negative value.  (Writable u directly
	  contains Writable w iff w.getContainer()==u.)

	  For this method to work, x must not be directly contained in
	  any Writable more than once (clones of x are OK).

          This default implementation just throws an
          UnsupportedOperationException; override for any Writable
          class W that can store other Writables even if W has no
          methods that provide Offsets.*/
	throw new UnsupportedOperationException("offsets not supported");
	//FIXME: make Atom.Field.getWritableContents() complain if its Offset contents give the negative value
    }

}
