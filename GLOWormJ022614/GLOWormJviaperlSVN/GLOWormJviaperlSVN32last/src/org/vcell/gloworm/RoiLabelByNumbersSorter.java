package org.vcell.gloworm;

import java.awt.Color;
import java.util.Arrays;

import ij.IJ;
import ij.plugin.Colors;

/** A simple QuickSort for String arrays. */
public class RoiLabelByNumbersSorter {
	
	/** Sorts the array. 
	 * @param splitDelimiter */
	
	public static void sort(String[] labels, Color[] colors, int mode) {
		if (true/*!alreadySorted(list)*/)
			sort(labels, colors, "_", mode);
	}
	
	public static void sort(String[] labels, Color[] colors, String splitDelimiter, int mode) {
		if (true/*!alreadySorted(list)*/)
			sort(labels, colors, splitDelimiter, 0, labels.length - 1, mode);
	}
	
	static void sort(String[] labels, Color[] colors, String splitDelimiter, int from, int to, int mode) {
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
			String[] labelDollaredChunks = labelDollared.split(splitDelimiter);
			lDCChunksLength = labelDollaredChunks.length;
			String[] nums = new String[lDCChunksLength];
			if (mode == (int)Integer.MAX_VALUE) {
				if (colors!=null && colors.length==labels.length) {
					nums = new String[lDCChunksLength+1];
					nums[lDCChunksLength] = Colors.colorToHexString(colors[i]);
				}
			} else {
				nums[lDCChunksLength-1] =  labelDollaredChunks[mode <lDCChunksLength? mode:lDCChunksLength-1];
			}
			int count = 0;
			for (int ldc=lDCChunksLength-1; ldc>=0; ldc--) {
				if (mode!= ldc) {
					nums[count] = labelDollaredChunks[ldc];
					count++;
				}
			}
			
			for (String num:nums) {

				String newNum = "";
				if (!newNum.equals(nums[0])) 
					//WHAT THE HELL IS REPLACEALL("[ICS]","") NEEDED FOR???
					newNum = num/*.replaceAll("[ics]","")*/.replace("\"", "").replace(" ", "").toLowerCase().replaceAll("^(.*)-\\d+$", "$1").split("_")[0];
				else
					newNum = num.replace("\"", "").replace(" ", "").toLowerCase().replaceAll("^(.*)-\\d+$", "$1");
				
				if (newNum.length()==0) newNum = "aaaaaa";
				if (mode == 0 && newNum.equals(nums[0])) {
					
				} else if(num.equals(labelDollaredChunks[0])){  //leaves text of name un-prepended, so get alpha sort order after whatever chunk is the lead sort key.
					
				}else {
					newNum = "00000000000000000000000000000000000" + newNum; // prepend maxDigits leading zeroes
					newNum = newNum.substring(newNum.length()-maxDigits);//trim back to maxDigits total in newNum
				}
				labels[i] = newNum + labels[i];
			}
			labels[i] = labels[i].replaceAll("^(0*)(([A-Z]|[a-z]|\\#)+)(.*)", "$2$4");   // ^ trims all leadingest 0s if text chunk is what is being sorted on. But leaves them if numeric chunk is placed first...
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


  

