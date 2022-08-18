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
//		if (mode == 0) {
//			Arrays.sort(labels);
//			return;
//		}
		int listLength = labels.length;
		int maxDigits = 30;		
		int lDCChunksLength = 0;

		for (int i=0; i<listLength; i++) {
			int len =0;
			String labelDollared = labels[i].replace("\'", "$");
			String[] labelDollaredChunks = labelDollared.split("_");
			lDCChunksLength = labelDollaredChunks.length;
			String[] nums = new String[lDCChunksLength];
			nums[lDCChunksLength-1] =  labelDollaredChunks[mode];
			int count = 0;
			for (int ldc=lDCChunksLength-1; ldc>=0; ldc--) {
				if (mode!= ldc) {
					nums[count] = labelDollaredChunks[ldc];
					count++;
				}
			}
			
			for (String num:nums) {

				String newNum = num.replace("\"", "").replace(" ", "").toLowerCase().replaceAll("^(.*)-\\d+$", "$1");
				
				if (newNum.length()==0) newNum = "aaaaaa";
				newNum = "00000000000000000000000000000000000" + newNum; // prepend maxDigits leading zeroes
				newNum = newNum.substring(newNum.length()-maxDigits);//trim back to maxDigits total in newNum
				labels[i] = newNum + labels[i];
			}
			labels[i] = labels[i].replaceAll("(0*)(([A-Z]|[a-z]|\\#)+)(.*)", "$2$4");    
		}
		if (labels!=null) {
			Arrays.sort(labels);
			for (int i=0; i<listLength; i++) {
				labels[i] = labels[i].replaceAll("(.*)(\".*\".*)","$2");
			}
		} else {  //why on earth use this pig of a sorter instead of Arrays.sort()?
//			ij.util.StringSorter.sort(labels);
		}
	}
			
}


  

