package ij.plugin;
import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;	
import java.util.*;
import ij.*;
import ij.io.*;
import ij.gui.*;
import ij.measure.Calibration;
import ij.process.*;
import ij.plugin.frame.Recorder;
import ij.macro.Interpreter;

/** This plugin, which saves the images in a stack as separate files for input into StarryNite, 
	implements the File/Save As/Image Sequence command. */
public class StarryNiteStackWriter implements PlugIn {

	//private static String defaultDirectory = null;
	private static String[] choices = {"BMP",  "FITS", "GIF", "JPEG", "PGM", "PNG", "Raw", "Text", "TIFF",  "ZIP"};
	private static String fileType = "TIFF";
	private static int ndigits = 4;
	private static boolean useLabels;
	private static boolean firstTime = true;
	private int startAt;
	private boolean hyperstack;
	private int[] dim;
	//private static boolean startAtZero;

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || (imp!=null && imp.getStackSize()<2)) {
			IJ.error("Stack Writer", "This command requires a stack.");
			return;
		}
		int stackSize = imp.getStackSize();
		String name = imp.getTitle();
		int dotIndex = name.lastIndexOf(".");
		if (dotIndex>=0)
			name = name.substring(0, dotIndex);
		hyperstack = imp.isHyperStack();
		LUT[] luts = null;
		int lutIndex = 0;
		int nChannels = imp.getNChannels();
		if (hyperstack) {
			dim = imp.getDimensions();
			if (imp.isComposite())
				luts = ((CompositeImage)imp).getLuts();
			if (firstTime && ndigits==4) {
				ndigits = 3;
				firstTime = false;
			}
		}
		
		GenericDialog gd = new GenericDialog("Save Image Sequence");
		gd.addChoice("Format:", choices, fileType);
		gd.addStringField("Name:", name, 12);
		if (!hyperstack)
			gd.addNumericField("Start At:", startAt, 0);
		gd.addNumericField("Digits (1-8):", ndigits, 0);
		if (!hyperstack)
			gd.addCheckbox("Use slice labels as file names", useLabels);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		fileType = gd.getNextChoice();
		name = gd.getNextString();
		if (!hyperstack)
			startAt = (int)gd.getNextNumber();
		if (startAt<0) startAt = 0;
		ndigits = (int)gd.getNextNumber();
		if (!hyperstack)
			useLabels = gd.getNextBoolean();
		else
			useLabels = false;
		int number = 0;
		if (ndigits<1) ndigits = 1;
		if (ndigits>8) ndigits = 8;
//		int maxImages = (int)Math.pow(10,ndigits);
//		if (stackSize>maxImages && !useLabels && !hyperstack) {
//			IJ.error("Stack Writer", "More than " + ndigits
//				+" digits are required to generate \nunique file names for "+stackSize+" images.");
//			return;			
//		}
		String format = fileType.toLowerCase(Locale.US);
		if (format.equals("gif") && !FileSaver.okForGif(imp))
			return;
		else if (format.equals("fits") && !FileSaver.okForFits(imp))
			return;
			
		if (format.equals("text"))
			format = "text image";
		String extension = "." + format;
		if (format.equals("tiff"))
			extension = ".tif";
		else if (format.equals("text image"))
			extension = ".txt";
			
		String title = "Save Image Sequence";
		String macroOptions = Macro.getOptions();
		String directory = null;
		if (macroOptions!=null) {
			directory = Macro.getValue(macroOptions, title, null);
			if (directory!=null) {
				File f = new File(directory);
				boolean exists = f.exists();
				if (directory.indexOf(".")==-1 && !exists) {
					// Is 'directory' a macro variable?
					if (directory.startsWith("&")) directory=directory.substring(1);
					Interpreter interp = Interpreter.getInstance();
					String directory2 = interp!=null?interp.getStringVariable(directory):null;
					if (directory2!=null) directory = directory2;
				}
				if (!f.isDirectory() && (exists||directory.lastIndexOf(".")>directory.length()-5))
					directory = f.getParent();
				if (!directory.endsWith(File.separator))
					directory += File.separator;
			}
		}
		if (directory==null) {
			if (Prefs.useFileChooser && !IJ.isMacOSX()) {
				String digits = getDigits(number);
				SaveDialog sd = new SaveDialog(title, name+digits+extension, extension);
				String name2 = sd.getFileName();
				if (name2==null)
					return;
				directory = sd.getDirectory();
			} else
				directory = IJ.getDirectory(title);
		}
		if (directory==null)
			return;
		
		boolean isOverlay = imp.getOverlay()!=null && !imp.getHideOverlay();
		if (!(format.equals("jpeg")||format.equals("png")))
			isOverlay = false;
		ImageStack stack = imp.getStack();
		ImagePlus imp2 = new ImagePlus();
		imp2.setTitle(imp.getTitle());
		Calibration cal = imp.getCalibration();
		int nSlices = stack.getSize();
		String path,label=null;
		imp.lock();
		for (int i=1; i<=nSlices; i++) {
			IJ.showStatus("writing: "+i+"/"+nSlices);
			IJ.showProgress(i, nSlices);
			ImageProcessor ip = stack.getProcessor(i);
			if (isOverlay) {
				imp.setSliceWithoutUpdate(i);
				ip = imp.flatten().getProcessor();
			} else if (luts!=null && nChannels>1 && hyperstack) {
				ip.setColorModel(luts[lutIndex++]);
				if (lutIndex>=luts.length) lutIndex = 0;
			}
			imp2.setProcessor(null, ip);
			String label2 = stack.getSliceLabel(i);
			if (label2!=null && label2.indexOf("\n")!=-1)
				imp2.setProperty("Info", label2);
			else {
				Properties props = imp2.getProperties();
				if (props!=null) props.remove("Info");
			}
			imp2.setCalibration(cal);
			String digits = getDigits(number++);
			if (useLabels) {
				label = stack.getShortSliceLabel(i);
				if (label!=null && label.equals("")) label = null;
				if (label!=null) label = label.replaceAll("/","-");
			}
			if (label==null)
				path = directory+name+digits+extension;
			else
				path = directory+label+extension;
			if (Recorder.record)
				Recorder.disablePathRecording();
			IJ.saveAs(imp2, format, path);
		}
		imp.unlock();
		if (isOverlay) imp.setSlice(1);
		IJ.showStatus("");
	}
	
	String getDigits(int n) {
		if (hyperstack) {
			int c = (n%dim[2])+1;
			int z = ((n/dim[2])%dim[3])+1;
			int t = ((n/(dim[2]*dim[3]))%dim[4])+1;
			String cs="", zs="", ts="";
			if (dim[2]>1) {
				cs = IJ.pad(c, ndigits);
				cs = "_c"+cs;
			}
			if (dim[3]>1) {
				zs = IJ.pad(z, ndigits);
				zs = "-p"+ zs;
			}
			if (dim[4]>1) {
				ts = IJ.pad(t, ndigits);
				ts = "-t"+ts;
			}
			return ts+zs+cs;
		} else {
			return "-p"+IJ.pad(n+1, ndigits);
		}
	}
	
}

