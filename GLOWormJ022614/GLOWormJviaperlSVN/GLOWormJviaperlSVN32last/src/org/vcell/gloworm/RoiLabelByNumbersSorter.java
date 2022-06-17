package org.vcell.gloworm;

import java.util.Arrays;

import ij.IJ;

/** A simple QuickSort for String arrays. */
public class RoiLabelByNumbersSorter {
	
	/** Sorts the array. */
	public static void sort(String[] labels, int mode) {
		if (true/*!alreadySorted(list)*/)
			sort(labels, 0, labels.length - 1, mode);
	}
	
	static void sort(String[] labels, int from, int to, int mode) {
		if (mode == 0) {
			Arrays.sort(labels);
			return;
		}
		int listLength = labels.length;
		boolean allSameLength = true;
		int len0 = labels[0].length();
		for (int i=0; i<listLength; i++) {
			if (labels[i].length()!=len0) {
				allSameLength = false;
				break;
			}
		}
		if (allSameLength)
			{ij.util.StringSorter.sort(labels); return;}
		int maxDigits = 15;		
		String[] labels2 = null;	
		char ch;	
		for (int i=0; i<listLength; i++) {
			//Replace needed here before split to handle the quote-quote issue without exceptions 
			int len =0;
			if (labels[i].contains("Nuc")) {
				IJ.wait(0);
			}
			String labelDollared = labels[i].replace("\'", "$");
			String[] labelDollaredChunks = labelDollared.split("_");
			int lDCChunksLength = labelDollaredChunks.length;
			try {
				len = labelDollaredChunks[/* lDCChunksLength - 5 + */ mode].split("-")[0].length();}
			catch (java.lang.ArrayIndexOutOfBoundsException e) {continue;}
			String num = "";
			for (int j=0; j<len; j++) {
				//Replace needed here before split to handle the quote-quote issue without exceptions 
				ch = labelDollaredChunks[/* lDCChunksLength - 5 */ + mode].split("-")[0].charAt(j);
				if (ch>=48 && ch<=57) num += ch;
			}
			if (num.length()==0) num = "aaaaaa";
			num = "000000000000000" + num; // prepend maxDigits leading zeroes
			num = num.substring(num.length()-maxDigits);
			labels[i] = num + labels[i];
		}
		if (labels!=null) {
			Arrays.sort(labels);
			for (int i=0; i<listLength; i++) {
				labels[i] = labels[i].substring(maxDigits);
			}
		} else {  //why on earth use this pig of a sorter instead of Arrays.sort()?
			ij.util.StringSorter.sort(labels);
		}
	}
			
}


  

