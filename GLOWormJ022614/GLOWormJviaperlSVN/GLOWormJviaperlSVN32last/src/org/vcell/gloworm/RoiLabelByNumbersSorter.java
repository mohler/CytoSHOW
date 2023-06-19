package org.vcell.gloworm;

import java.awt.Color;
import java.util.Arrays;

import ij.IJ;
import ij.plugin.Colors;

/** A simple QuickSort for String arrays. */
public class RoiLabelByNumbersSorter {
	
	/** Sorts the array. 
	 * @param splitDelimiter */
	
	public static String[] sort(String[] labels, Color[] colors, int mode) {
		if (true/*!alreadySorted(list)*/)
			labels = sort(labels, colors, "_", mode);
		return labels;
	}
	
	public static String[] sort(String[] labels, Color[] colors, String splitDelimiter, int mode) {
		if (true/*!alreadySorted(list)*/)
			labels = sort(labels, colors, splitDelimiter, 0, labels.length - 1, mode);
		return labels;
	}
	
	static String[] sort(String[] labels, Color[] colors, String splitDelimiter, int from, int to, int mode) {
//		if (mode == 0) {
//			Arrays.sort(labels);
//			return;
//		}
		int listLength = labels.length;
		int maxDigits = 30;		
		int lDCChunksLength = 0;
		boolean flipOrder = mode<0;
		int sortmode = Math.abs(mode);
		
		for (int i=0; i<listLength; i++) {
			int len =0;
			String labelDollared = labels[i].replace("\'", "$");
			String[] labelDollaredChunks = labelDollared.split(splitDelimiter);
			lDCChunksLength = labelDollaredChunks.length;
			String[] nums = new String[lDCChunksLength];
			if (sortmode == (int)Integer.MAX_VALUE) {
				if (colors!=null && colors.length==labels.length) {
					nums = new String[lDCChunksLength+1];
					nums[lDCChunksLength] = Colors.colorToHexString(colors[i]);
					if (nums[lDCChunksLength].matches("\\#..00ffff"))
						nums[lDCChunksLength] = "#00000000";
				}
			} else {
				nums[lDCChunksLength-1] =  labelDollaredChunks[sortmode <lDCChunksLength? sortmode:lDCChunksLength-1];
			}
			int count = 0;
			for (int ldc=lDCChunksLength-1; ldc>=0; ldc--) {
				if (sortmode!= ldc) {
					nums[count] = labelDollaredChunks[ldc];
					count++;
				}
			}
			
			for (String num:nums) {
				if (num == null)
					continue;
				String newNum = "";
				if (!newNum.equals(nums[0])) 
					//REPLACEALL("[ICS]","") NEEDED FOR CLEAN DISPLAY OF DC-CPHATE PLOTS...NEED TO KEEP IT...WITHOUT CONFLICT WITH COLOR CODES...
					if (num.startsWith("#")) {
						newNum = num/*.replaceAll("[ics]","")*/.replace("\"", "").replace(" ", "").toLowerCase().replaceAll("^(.*)-\\d+$", "$1").split("_")[0];
					} else {
						newNum = num.replaceAll("[ics]","").replace("\"", "").replace(" ", "").toLowerCase().replaceAll("^(.*)-\\d+$", "$1").split("_")[0];
					}
				else
					newNum = num.replace("\"", "").replace(" ", "").toLowerCase().replaceAll("^(.*)-\\d+$", "$1");
				
				if (newNum.length()==0) newNum = "aaaaaa";
				if (sortmode == 0 && newNum.equals(nums[0])) {
					
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
			String[] labelsCopy = Arrays.copyOf(labels, labels.length);
			if (flipOrder) {
				for (int i=listLength-1; i>=0; i--) {
					labelsCopy[listLength-1-i] = labels[i];
				}
				labels = labelsCopy;
			}
			for (int i=0; i<listLength; i++) {
				labels[i] = labels[i].replaceAll("(.*)(\".*\".*)","$2");
			}
		} else {  //why on earth use this pig of a sorter instead of Arrays.sort()?

//			ij.util.StringSorter.sort(labels);
		}
		return labels;
	}
			
}


  

