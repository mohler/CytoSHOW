package gloworm.qtvr;

import java.io.*;

final class NativeString extends Writable {
    /*String of characters whose writable form is an ASCII string as
      represented in some language which compiles to native code.
      Right now only C-style and Pascal-style NativeStrings are
      supported.  The string's contents and the language of its
      writable form are immutable.*/
    public static final Object C=new Object(), PASCAL=new Object(); //avoid confusing things with 2 int args in 1 constructor
    private final String string;
    private final int writableSize;
    private final Object language;

    NativeString(String string) {
	/*Creates a C-style NativeString.  Throws an
          IllegalArgumentException if string has any non-ASCII
          characters.*/
	this(string, C);
    }

    NativeString(String string, Object language) {
	/*Creates a NativeString in the style of whatever language the
          2nd argument specifies.  The second argument should be one
          of the public objects this class provides for that purpose.
          The default language is C if the argument is null or
          otherwise doesn't specify one of the supported languages.

	  Throws an IllegalArgumentException if string has any
	  non-ASCII characters or is otherwise illegal in the
	  specified language--for example, if it violates the
	  255-character maximum for a Pascal string.*/
	this(string, language, string.length()+1);
	//all supported languages need extra byte for some kind of marker
    }

    NativeString(String string, Object language, int forcedSize) {
	/*Returns a NativeString with a physical length of forcedSize
          bytes in writable form, with zeroes for any bytes past the
          actual string's end.  By the standards of its language, the
          NativeString will be considered to have only string.length()
          characters.

	  This method throws an IllegalArgumentException if
	  NativeString(string, language) would throw one, or if string
	  is shorter than forcedSize. */
	super(false); //nature of String keeps this from being > 2Gb in writable form
	if (forcedSize < string.length())
	    throw new IllegalArgumentException("forcedSize (" + forcedSize + ") < string length (" + string.length() + ')');
	if (language==PASCAL && string.length() > 255)
	    throw new IllegalArgumentException('"'+string+"\" can't be a Pascal string, length > 255");
	//Pascal strings have max length 255 because the (unsigned) length goes in the 1st byte
	for (int index=0; index < string.length(); index++) //check if string is all ASCII
	    if (string.charAt(index) > 127)
		throw new IllegalArgumentException('"'+string+"\".charAt("+index+") is not ASCII");
	writableSize=forcedSize;
	this.string=string;
	this.language=(language==C || language==PASCAL? language: C);
	//because C strings have the least weird constraints & are most common
    }

    public final String toString() {
	/*Returns the actual text this NativeString represents,
          independent of its language or writable form. */
	return string;
    }

    final Object language() {
	return language;
    }

    public final boolean equals(Object o) {
	/*Returns true if o is a NativeString whose writable form is
          the same as the writable form of this, byte for byte. */
	return (o instanceof NativeString &&
		((NativeString) o).writableSize==writableSize &&
		((NativeString) o).language==this.language &&
		o.toString().equals(this.string));
    }

    //don't bother overriding clone, no mutable members to protect

    byte[] translate() {
	/*Returns this NativeString as a byte array in the language of its writable form.*/
		byte[] translated=new byte[(int) getSize()], ascii;
	try {
	    ascii=string.getBytes("US-ASCII");
	}
	catch (UnsupportedEncodingException e) {
	    //can't happen, US-ASCII encoding is always supported
	    throw new InternalError("ASCII encoding doesn't work.");
	}
	if (language==C)
	    System.arraycopy(ascii, 0, translated, 0, ascii.length);
	//extra element(s) left on end for null
	else if (language==PASCAL) {
	    try {
		System.arraycopy(ascii, 0, translated, 1, ascii.length);
	    } catch (ArrayIndexOutOfBoundsException e) {
		throw new ArrayIndexOutOfBoundsException("ascii.length="+ascii.length + " translated.length="+translated.length);
	    }
	    //now fill in length at start, we already know it fits:
	    translated[0]=(byte) string.length();  //OK if it's negative, it'll be read as unsigned
	}
	//either way, the right # of \0's are already on the end:
	return translated;
    }

    public long getSize() {
	return writableSize;
    }

    public final InputStream getWritableForm() {
	//a byte array would make more sense but Writable wants an InputStream
	return new java.io.ByteArrayInputStream(translate());
    }
}
