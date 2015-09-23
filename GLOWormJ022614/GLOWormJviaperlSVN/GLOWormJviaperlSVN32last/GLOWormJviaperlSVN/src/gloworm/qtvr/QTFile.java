package gloworm.qtvr;

import java.io.*;
import java.util.Vector;
import java.util.zip.*;

public class QTFile extends Writable {
    /*Data structure for accumulating an entire Quicktime file's worth of data, then writing it all to a file. */
    private Vector atoms=new Vector(); //only atoms go directly into Quicktime files

    public QTFile(boolean canBeWide) {
	super(canBeWide);
    }
    public void addAtom(Atom atom) {
	/*Adds atom, not a defensive copy of it, to this file.  Atoms are
          written to the file in the order they were added by this
          method.

	If atom.getContainer() is non-null, an IllegalStateException
	is thrown.  Use the clone of such atoms instead.*/
	synchronized(atom) {
	    atoms.addElement(atom);
	    atom.setContainer(this);
	}
    }
    public Object clone() {
	/*The return value holds defensive copies of all Atoms.*/
	QTFile returnValue=(QTFile) super.clone();
	returnValue.atoms=new Vector(atoms.size());
	for (int index=0; index < atoms.size(); index++)
	    returnValue.atoms.addElement(((Atom) atoms.elementAt(index)).clone());
	return returnValue;
    }
    protected long getOffsetOf(Writable w) {
	/*Returns the location where the writable form of w (not a
          clone of it!) appears directly in this QTFile's writable
          form, or throws an IllegalArgumentException if it doesn't.*/
	if (!(w instanceof Atom)) //then this can't directly contain it
	    throw new IllegalArgumentException(w + " not directly contained in " + this);
	int wIndex=-1;
	for (int index=0; index < atoms.size() && wIndex < 0; index++)
	    if (w==atoms.elementAt(index)) wIndex=index;
	if (wIndex < 0)
	    throw new IllegalArgumentException(w + " not directly contained in " + this);
	long offset=0;
	for (int index=0; index < wIndex; index++) //sum sizes of atoms before w
	    offset += ((Atom) atoms.elementAt(index)).getSize();
	return offset;
    }
    public final long getSize() {
	long returnValue=0;
	for (int index=0; index < atoms.size(); index++)
	    returnValue += ((Atom) atoms.elementAt(index)).getSize();
	return returnValue;
    }
    public final InputStream getWritableForm() {
	/*Additions to this file or changes to its component atoms do
          not affect any InputStream already returned by this method.
          Changes made to the atoms in this file while this method
          runs are guaranteed, well, atomic.  But it is not specified
          whether the changes are represented in the return value.

	  You probably don't need to call this directly--use
	  writeToFile instead. */
	synchronized(atoms) {
	    Vector writableForms=new Vector(atoms.size());
	    for (int index=0; index < atoms.size(); index++)
		writableForms.addElement(((Atom) atoms.elementAt(index)).getWritableForm());
	    return new SequenceInputStream(writableForms.elements());
	}
    }
    protected void setContainer() {
	/*Not supported, as this is meant to be written to disk rather
          than stored in another Writable.*/
	throw new UnsupportedOperationException("");
    }
    public void writeToFile(File file) throws IOException {
	/*Writes the Quicktime movie to disk as file.  If the
	  extension is ".zip" the file will be a Zip file, maximally
	  compressed, containing the Quicktime file with the same name
	  but the extension ".mov" and no associated path.

	  Throws FileNotFoundException or SecurityException if the
	  file could not be accessed for writing (in which case it was
	  not created or left unchanged).  Can also throw a more
	  general IOException if the writing-to-disk process fails
	  midway, in which case it may leave behind a
	  partially-written file.  Additionally, throws
	  IllegalArgumentException if file's extension is not ".zip"
	  or ".mov".

	  WARNING: If another file with the same name and path exists,
	  it will be silently overwritten! */
	boolean zip=file.getName().endsWith(".zip");
	if (!zip && !(file.getName().endsWith(".mov")))
	    throw new IllegalArgumentException(file.getName() + " does not end in .zip or .mov");
	InputStream fileContents=getWritableForm();
	OutputStream outFile=new FileOutputStream(file);
	byte[] buf=new byte[512]; //adapted from http://javaalmanac.com/egs/java.util.zip/CreateZip.html
	if (zip)
	    ((ZipOutputStream)
	     (outFile=new ZipOutputStream(outFile))).putNextEntry(new ZipEntry(file.getName().substring(0, file.getName().length()-3)
									       + "mov"));
	int bufCount;
	while ((bufCount=fileContents.read(buf, 0, buf.length)) > 0)
	    outFile.write(buf, 0, bufCount);
	outFile.close();
    }
}
