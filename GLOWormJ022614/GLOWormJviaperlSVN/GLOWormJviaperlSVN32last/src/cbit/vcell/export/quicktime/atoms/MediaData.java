package cbit.vcell.export.quicktime.atoms;

/*©
 * (C) Copyright University of Connecticut Health Center 2001.
 * All rights reserved.
©*/
import cbit.vcell.export.quicktime.*;
import java.io.*;
/**
 * This type was created in VisualAge.
 */
public class MediaData extends Atoms {

	public static final String type = "mdat";
	protected MediaChunk[] mediaChunks;
	
/**
 * This method was created in VisualAge.
 * @param dReference cbit.vcell.export.quicktime.DataReference
 */
public MediaData(MediaChunk[] chunks) {
	mediaChunks = chunks;
	size = 16;
	for (int i=0;i<mediaChunks.length;i++) size += mediaChunks[i].getSize();
}
/**
 * writeData method comment.
 */
public boolean writeData(DataOutputStream out) {
	try {
		int offset = 16;
		int fakesize =1;
		out.writeInt(fakesize);
		out.writeBytes(type);
		out.writeLong(size);
		for (int i=0;i<mediaChunks.length;i++) {
			out.write(mediaChunks[i].getDataBytes());
			mediaChunks[i].setOffset(offset);
			offset += mediaChunks[i].getSize();
		}
		return true;
	} catch (IOException e) {
		System.out.println("Unable to write: " + e.getMessage());
		e.printStackTrace();
		return false;
	}
}
/**
 * writeData method comment.
 */
public void writeData(File file, boolean isDataFile) throws IOException {
	if (!file.exists() || !isDataFile) {
		// new file; just write everything out
		DataOutputStream dout = new DataOutputStream(new FileOutputStream(file));
		writeData(dout);
		dout.close();
	} else {
		// file has mdat atom containing some or all of the chunks
		RandomAccessFile fw = new RandomAccessFile(file, "rw");
		long length = file.length();
		// append chunks that don't have their data bytes already in the file
		fw.seek(length);
		for (int i=0;i<mediaChunks.length;i++) {
			if (!mediaChunks[i].isDataInFile(file)) {
				mediaChunks[i].setOffset(length);
				fw.write(mediaChunks[i].getDataBytes());
				length += mediaChunks[i].getSize();
			}
		}
		// update the media data atom header
		//size = (int)length;
		fw.seek(0);
		fw.writeInt(1);
		fw.seek(8);
		fw.writeLong(length);
		fw.close();
	}
}
}
