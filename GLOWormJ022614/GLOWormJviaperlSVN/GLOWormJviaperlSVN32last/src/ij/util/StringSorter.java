package ij.util;

import ij.IJ;

import java.util.Arrays;

/** A simple QuickSort for String arrays. */
public class StringSorter {
	
	/** Sorts the array. */
	public static void sort(String[] a) {
		if (!alreadySorted(a))
			sort(a, 0, a.length - 1);
	}
	
	static void sort(String[] a, int from, int to) {
		int i = from, j = to;
		String center = a[ (from + to) / 2 ];
		do {
			while ( i < to && restructure(center).compareTo( restructure(a[i]) ) > 0 ) i++;
			while ( j > from && restructure(center).compareTo( restructure(a[j]) ) < 0 ) j--;
			if (i < j) {String temp = a[i]; a[i] = a[j]; a[j] = temp; }
			if (i <= j) { i++; j--; }
		} while(i <= j);
		if (from < j) sort(a, from, j);
		if (i < to) sort(a,  i, to);
	}
		
	private static String restructure(String string) {
		String[] stringSplit = string.split("_");
		String stringRestructured;
		if (stringSplit.length == 4)
			stringRestructured = stringSplit[0].split(" ")[0] + "_" + padNumbers(stringSplit[3]) + "_" + padNumbers(stringSplit[2]) + "_" + padNumbers(stringSplit[1]);
		else
			stringRestructured = string;

		return stringRestructured;
	}
	
	private static String padNumbers(String string) {
		int maxDigits = 15;
		String num = "";
		int len = string.length();
		for (int j=0; j<len; j++) {
			Character ch = string.charAt(j);
			if (ch>=48 && ch<=57) num += ch;
		}
		if (num.length()==0) num = "aaaaaa";
		num = "000000000000000" + num; // prepend maxDigits leading zeroes
		num = num.substring(num.length()-maxDigits);
		return num;
	}
	

	static boolean alreadySorted(String[] a) {
		for ( int i=1; i<a.length; i++ ) {
			if (a[i].compareTo(a[i-1]) < 0 )
			return false;
		}
		return true;
	}
	
	/** Sorts file names containing numerical components.
	* @author Norbert Vischer
	*/
	public static String[] sortNumerically(String[] list) {
		int n = list.length;
		String[] paddedList = getPaddedNames(list);
		String[] sortedList = new String[n];
		int[] indexes = Tools.rank(paddedList);
		for (int i = 0; i < n; i++)
			sortedList[i] = list[indexes[i]];
		return sortedList;
	}

	
	//THIS MAY NOT BE WORKING ON WINDOWS??
	public static String[] sortNumericallyViaRegex(String[] list) {
		int n = list.length;
		int stringMaxLength = 0; 
		int stringMinLength = Integer.MAX_VALUE; 
		String[][] nameChunks = new String[n][];
		String[] nameRoot = new String[n];
		String[] nameEnd = new String[n];
		String[] nameIncr = new String[n];
		for(int i=0;i<n;i++) {
			if(list[i].length() > stringMaxLength) {
				stringMaxLength = list[i].length();
			}
			if(list[i].length() < stringMinLength) {
				stringMinLength = list[i].length();
			}
		}
		for(int i=0;i<n;i++) {
			if(list[i].length() <= stringMaxLength) {
				nameChunks[i] = list[i].split("\\d+");
				if(nameChunks.length > 1) {
					nameEnd[i] = nameChunks[i][nameChunks[i].length-1];
					nameRoot[i] = list[i].replaceAll("(.*\\D)\\d+"+nameEnd[i], "$1");
					nameIncr[i] = list[i].replaceAll(".*\\D(\\d+)"+nameEnd[i], "$1");
					list[i] = nameRoot[i] + IJ.pad(Integer.parseInt(nameIncr[i]), stringMaxLength - stringMinLength +1)
								+nameEnd[i];
				}
			}
		}
		Arrays.sort(list);		
		for(int i=0;i<n;i++) {
			//IJ.log(list[i]);
			if(list[i]!=null) {
				nameChunks[i] = list[i].split("\\d+");
				if(nameChunks.length > 1) {
					nameEnd[i] = nameChunks[i][nameChunks[i].length-1];
					nameRoot[i] = list[i].replaceAll("(.*\\D)\\d+"+nameEnd[i], "$1");
					//IJ.log(nameRoot[i]);
					//IJ.log(list[i]);
					list[i] = list[i].replaceAll(nameRoot[i]+"[0]+([0-9])", nameRoot[i]+"$1");
					//IJ.log(list[i]+"  !");
				}
			}
		}
		return list;
	}

	// Pads individual numeric string components with zeroes for correct sorting
	private static String[] getPaddedNames(String[] names) {
		int nNames = names.length;
		String[] paddedNames = new String[nNames];
		int maxLen = 0;
		for (int jj = 0; jj < nNames; jj++) {
			if (names[jj].length() > maxLen) {
				maxLen = names[jj].length();
			}
		}
		int maxNums = maxLen / 2 + 1;//calc array sizes
		int[][] numberStarts = new int[names.length][maxNums];
		int[][] numberLengths = new int[names.length][maxNums];
		int[] maxDigits = new int[maxNums];

		//a) record position and digit count of 1st, 2nd, .. n-th number in string
		for (int jj = 0; jj < names.length; jj++) {
			String name = names[jj];
			boolean inNumber = false;
			int nNumbers = 0;
			int nDigits = 0;
			for (int pos = 0; pos < name.length(); pos++) {
				boolean isDigit = name.charAt(pos) >= '0' && name.charAt(pos) <= '9';
				if (isDigit) {
					nDigits++;
					if (!inNumber) {
						numberStarts[jj][nNumbers] = pos;
						inNumber = true;
					}
				}
				if (inNumber && (!isDigit || (pos == name.length() - 1))) {
					inNumber = false;
					if (maxDigits[nNumbers] < nDigits) {
						maxDigits[nNumbers] = nDigits;
					}
					numberLengths[jj][nNumbers] = nDigits;
					nNumbers++;
					nDigits = 0;
				}
			}
		}
		
		//b) perform padding
		for (int jj = 0; jj < names.length; jj++) {
			String name = names[jj];
			int numIndex = 0;
			StringBuilder destName = new StringBuilder();
			for (int srcPtr = 0; srcPtr < name.length(); srcPtr++) {
				if (srcPtr == numberStarts[jj][numIndex]) {
					int numLen = numberLengths[jj][numIndex];
					if (numLen > 0) {
						for (int pad = 0; pad < (maxDigits[numIndex] - numLen); pad++) {
							destName.append('0');
						}
					}
					numIndex++;
				}
				destName.append(name.charAt(srcPtr));
			}
			paddedNames[jj] = destName.toString();
		}
		return paddedNames;
	}

}
