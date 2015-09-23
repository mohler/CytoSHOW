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
public abstract class Atoms implements AtomConstants {

	protected int size;
	protected long size64;
		
/**
 * This method was created in VisualAge.
 * @param out java.io.DataOutputStream
 */
public abstract boolean writeData(DataOutputStream out);
}
