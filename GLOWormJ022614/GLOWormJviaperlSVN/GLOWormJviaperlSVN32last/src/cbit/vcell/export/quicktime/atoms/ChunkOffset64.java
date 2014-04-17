package cbit.vcell.export.quicktime.atoms;

/*©
 * (C) Copyright University of Connecticut Health Center 2001.
 * All rights reserved.
©*/
import cbit.vcell.export.quicktime.*;
import java.io.*;
import java.util.*;
/**
 * This type was created in VisualAge.
 */
public class ChunkOffset64 extends LeafAtom {

	public static final String type = "co64";
	protected int numberOfEntries;
	protected long[] chunkOffsets;

/**
 * This method was created in VisualAge.
 * @param durations int[]
 */
public ChunkOffset64(long[] offsets) {
	chunkOffsets = offsets;
	numberOfEntries = chunkOffsets.length;
	size = 16 + 8 * numberOfEntries;
}
/**
 * writeData method comment.
 */
public boolean writeData(DataOutputStream out) {
	try {
		out.writeInt(size);
		out.writeBytes(type);
		out.writeByte(version);
		out.write(flags);
		out.writeInt(numberOfEntries);
		for (int i=0;i<chunkOffsets.length;i++) out.writeLong(chunkOffsets[i]);
		return true;
	} catch (IOException e) {
		System.out.println("Unable to write: " + e.getMessage());
		e.printStackTrace();
		return false;
	}
}
}
