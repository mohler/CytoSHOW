package ccam.worm.qt;

import java.awt.Component;
import java.io.File;

import ccam.worm.qt.QTVRWriter.MOVIE_TYPE;
import ij.IJ;

public class QTVRUtil {

	protected static File rootDir;
	/**
	 * @param args
	 */
	public static void main(String args) {
		try {
			// make some startup window as parent component if you wish for testing
			if (args.length() > 0) {
				rootDir = new File(args);
				makeQTVRFile(rootDir, null);
				IJ.log("check1 " + args);
			} else {
				throw new Exception("Bad argument list - need rootDir");
			}
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
	}
	public static void makeQTVRFile (File rtDir, Component requester) {
		rootDir = rtDir;
		QTVRWriter qtw = new QTVRWriter(requester);
		qtw.writeWormQTVR(QTVRWriter.MOVIE_TYPE.QTVR,rootDir);

		IJ.log("check2 " + rootDir);
	}
}
