package gloworm.qtvr;

final class Util {
    /*Static methods useful to this package in general.  Noninstantiable.*/

    private static final int EPOCH_DIF=2082844800;
    /*# seconds between the start of time for real OS's [1970] and Macs [1904].
      Source: Ion Moraru, The Virtual Cell (and many others)*/

    private static byte[] matrix; //identity transform matrix

    private Util() {
	/*enforce noninstantiability*/
    }

    private static void storeInByteArrayImpl(long n, byte[] b, int i, int nWidth) {
	/*StoreInByteArray methods delegate to this one.  nWidth is #
          bytes in n before cast to long*/
	if (i > b.length - nWidth)
	    throw new ArrayIndexOutOfBoundsException("Can't fit " + nWidth + " bytes at " + i +
						     " in byte[" + b.length + ']');
	for (int index=nWidth-1, mask=0xFF; index >= 0; index--) {
	    b[index+i]=(byte) (n & mask);
	    n >>= 8;
	} //array populated from low to high byte, so leading 0's in n (from cast) avoided
    }

    static void storeInByteArray(int n, byte[] b, int i) {
	/*Writes n in big-endian order into b, putting the high byte
          at i.  Overwrites any array elements already there.  Throws
          an ArrayIndexOutOfBoundsException if there are not enough
          elements from i to the end of b to hold n.*/
	storeInByteArrayImpl(n, b, i, 4);
    }

    static void storeInByteArray(long n, byte[] b, int i) {
	/*Writes n in big-endian order into b, putting the high byte
          at i.  Overwrites any array elements already there.  Throws
          an ArrayIndexOutOfBoundsException if there are not enough
          elements from i to the end of b to hold n.*/
	storeInByteArrayImpl(n, b, i, 8);
    }

    static void storeInByteArray(short n, byte[] b, int i) {
	/*Writes n in big-endian order into b, putting the high byte
          at i.  Overwrites any array elements already there.  Throws
          an ArrayIndexOutOfBoundsException if there are not enough
          elements from i to the end of b to hold n.*/
	storeInByteArrayImpl(n, b, i, 2);
    }

    static int getMacTime() {
	/*Returns the number of seconds since Jan. 1, 1904 in local time.  Macs and
          Quicktime represent timestamps this way.*/
	return (int) ((System.currentTimeMillis() + java.util.TimeZone.getDefault().getRawOffset()) / 1000 + EPOCH_DIF);
	/*Milliseconds since '70 UTC, --> milliseconds since '70
          local time, --> seconds (truncating fraction), + # seconds
          between '04 & '70.*/
    }

    static byte[] getIdentityTransformMatrix() {
	/*Quicktime transforms a movie's video data with a matrix
          stored in the movie's metadata before displaying.  This
          method returns the transform matrix that does nothing to the
          video data, in the right format for storage.*/
	if (matrix==null)  { //lazily initialize:
	    byte[] returnValue=new byte[36];
	    /*That's 9 ints for the rows of a 3x3 matrix concatenated
              together.  Bottom row is fixed point (2 bits).(30 bits),
              other rows fixed point (16 bits).(16 bits).  This is the
              identity matrix so we want 1's along the diagonals:*/
	    Util.storeInByteArray(0x00010000, returnValue, 0);
	    Util.storeInByteArray(0x00010000, returnValue, 16);
	    Util.storeInByteArray(0x40000000, returnValue, 32);
	    return (matrix=returnValue);
	} else return matrix;   
    }

    public static boolean isASCII(String theString) {
	/*Returns true if all theString's characters have ASCII equivalents.*/
	for (int index=0; index < theString.length(); index++)
	    if (theString.charAt(index) > 127 || theString.charAt(index) < 0)
		//source: def of ASCII in unicode glossary
		return false;
	return true;
    }

}
