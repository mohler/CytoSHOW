package gloworm.qtvr;

public class Offset {
    /*Function object that's permanently associated with a target
      Writable.  Calculates where in the file to be written the target
      will appear.  A method that adds data to an Writable should
      return one where the QT spec indicates the offset will be needed
      elsewhere, and take one as an argument instead of a literal
      value when storing the offset to some other Writable.*/
    private final Writable target;
    private final boolean wideContainer;

    Offset(Writable target, boolean wideContainer) {
	/*wideContainer is true if the offset could be relative to a
	  file 2Gb or larger.  It is possible to make wideContainer
	  false if the offset's target is known to be in the first 2Gb
	  of the file, but not recommended, because it makes creation
	  of the chunk offset atom and future changes to the file's
	  layout more prone to fail.

	  Throws an IllegalArgumentException if target is null. */
	if (target==null) throw new NullPointerException("target is null");
	this.target=target;
	this.wideContainer=wideContainer;
    }

    long getOffset() {
	/*Returns how many bytes into the file the target begins.
          Throws an IllegalStateException if the container does not
          contain the target. */
	getFile(); //throw the exception relatively cheaply (w/o calculating offsets)
	Writable w=target;
	long offset=0;
	while (w.getContainer() != null) {
	    offset += w.getContainer().getOffsetOf(w);
	    w=w.getContainer();
	}
	return offset;
    }

    private QTFile getFile() {
	/*Returns the QTFile that the offset points into.  Assumes the
          QTFile is the outermost Writable containing the target; if
          that Writable is not a QTFile an IllegalStateException is
          thrown.*/
	Writable w=target;
	while (w.getContainer() != null)
	    w=w.getContainer();
	if (w instanceof QTFile) return (QTFile) w;
	else throw new IllegalStateException(target + "'s position in QTFile not defined");
    }
    
    boolean pointsIntoLargeFile() {
	/*Returns true if the offset is pointing to a location in a
          file larger than 2Gb, in which case all 8 bytes of
          getOffset() are used.*/
	return wideContainer;
    }

    String targetString() {
	//For debugging.
	return target.toString();
    }

    /*FIXME: Include way to check offset width when target's position
      in file undefined.  3 ways to do this:

      1) Make the constructor take the container which the offset is
      relative to (and enforce it with a NullPointerException).  Most
      extensible for dealing w/relative offsets into an arbitrary
      container (if can evade the tendency of defensive-copying
      Writables to screw them up), but inflexible: the width may be
      known where/when the specific container instance isn't.

      2) Make the constructor take a boolean re whether the
      container's wide.  Doesn't help or hurt extensibility for
      arbitrary containers (there has to be a method that lazily sets
      the container), more flexible, and you know at compile time if
      you transposed the parameters. */
    
}

