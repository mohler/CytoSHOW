package ij.io;
import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.frame.*;
import ij.plugin.DICOM;
import ij.plugin.AVI_Reader;
import ij.plugin.DragAndDrop;
import ij.plugin.GIF_Reader;
import ij.plugin.SimpleCommands;
import ij.plugin.TextFileReader;
import ij.text.TextWindow;
import ij.util.Java2;
import ij.measure.ResultsTable;
import ij.macro.Interpreter;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.zip.*;
import java.util.Locale;

import javax.swing.*;
import javax.swing.filechooser.*;

import java.awt.event.KeyEvent;

import javax.imageio.ImageIO;

import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.QTMovieOpenerMultiMod;

import client.RemoteMQTVSHandler;

/** Opens tiff (and tiff stacks), dicom, fits, pgm, jpeg, bmp or
	gif images, and look-up tables, using a file open dialog or a path.
	Calls HandleExtraFileTypes plugin if the file type is unrecognised. */
public class Opener {

	public static final int UNKNOWN=0,TIFF=1,DICOM=2,FITS=3,PGM=4,JPEG=5,
			GIF=6,LUT=7,BMP=8,ZIP=9,JAVA_OR_TEXT=10,ROI=11,TEXT=12,PNG=13,
			TIFF_AND_DICOM=14,CUSTOM=15, AVI=16, OJJ=17, TABLE=18, MOV=19, SUITE=20; // don't forget to also update 'types'
	public static final String[] types = {"unknown","tif","dcm","fits","pgm",
		"jpg","gif","lut","bmp","zip","java/txt","roi","txt","png","t&d","custom","ojj","table", "mov", "ste"};
	private static String defaultDirectory = null;
	private static int fileType;
	private boolean error;
	private boolean isRGB48;
	private boolean silentMode;
	private String omDirectory;
	private File[] omFiles;
	private static boolean openUsingPlugins;
	private static boolean bioformats;

	static {
		Hashtable commands = Menus.getCommands();
		bioformats = commands!=null && commands.get("Bio-Formats Importer")!=null;
	}

	public Opener() {
	}

	/** Displays a file open dialog box and then opens the tiff, dicom, 
		fits, pgm, jpeg, bmp, gif, lut, roi, or text file selected by 
		the user. Displays an error message if the selected file is not
		in one of the supported formats. This is the method that
		ImageJ's File/Open command uses to open files. */
	public void open() {
		OpenDialog od = new OpenDialog("Open", "");
		String directory = od.getDirectory();
		String name = od.getFileName();
		if (name!=null) {
			String path = directory+name;
			error = false;
			open(path);
			if (!error) Menus.addOpenRecentItem(path);
		}
	}

	/** Displays a JFileChooser and then opens the tiff, dicom, 
		fits, pgm, jpeg, bmp, gif, lut, roi, or text files selected by 
		the user. Displays error messages if one or more of the selected 
		files is not in one of the supported formats. This is the method
		that ImageJ's File/Open command uses to open files if
		"Open/Save Using JFileChooser" is checked in EditOptions/Misc. */
	public void openMultiple() {
		Java2.setSystemLookAndFeel();
		// run JFileChooser in a separate thread to avoid possible thread deadlocks
		try {
			EventQueue.invokeAndWait(new Runnable() {
				public void run() {
					JFileChooser fc = new JFileChooser();
					fc.setFileHidingEnabled(false);
					fc.setMultiSelectionEnabled(true);
					File dir = null;
					String sdir = OpenDialog.getDefaultDirectory();
					if (sdir!=null)
						dir = new File(sdir);
					if (dir!=null)
						fc.setCurrentDirectory(dir);
					int returnVal = fc.showOpenDialog(IJ.getInstance());
					if (returnVal!=JFileChooser.APPROVE_OPTION)
						return;
					omFiles = fc.getSelectedFiles();
					if (omFiles.length==0) { // getSelectedFiles does not work on some JVMs
						omFiles = new File[1];
						omFiles[0] = fc.getSelectedFile();
					}
					omDirectory = fc.getCurrentDirectory().getPath()+File.separator;
				}
			});
		} catch (Exception e) {}
		if (omDirectory==null) return;
		OpenDialog.setDefaultDirectory(omDirectory);
		for (int i=0; i<omFiles.length; i++) {
			String path = omDirectory + omFiles[i].getName();
			open(path);
			if (i==0 && Recorder.record)
				Recorder.recordPath("open", path);
			if (i==0 && !error)
				Menus.addOpenRecentItem(path);
		}
	}

	/** Opens and displays a tiff, dicom, fits, pgm, jpeg, bmp, gif, lut, 
		roi, or text file. Displays an error message if the specified file
		is not in one of the supported formats. */
	public void open(String path) {
		boolean isURL = path.indexOf("://")>0;
		if (isURL && isText(path)) {
			openTextURL(path);
			return;
		}
		if (path.endsWith(".jar") || path.endsWith(".class")) {
			(new PluginInstaller()).install(path);
			return;
		}
		boolean fullPath = path.startsWith("/") || path.startsWith("\\") || path.indexOf(":\\")==1 || isURL;
		if (!fullPath) {
			//			String defaultDir = OpenDialog.getDefaultDirectory();
			String defaultDir = "/Volumes/GLOWORM_DATA";
			if (defaultDir!=null)
				path = defaultDir + path;
			else
				path = (new File(path)).getAbsolutePath();
		}
		if (!silentMode) IJ.showStatus("Opening: " + path);
		long start = System.currentTimeMillis();
		ImagePlus imp = openImage(path);
		if (imp==null && isURL)
			return;
		if (imp!=null) {
			WindowManager.checkForDuplicateName = true;
			if (isRGB48)
				openRGB48(imp);
			else
				imp.show(IJ.d2s((System.currentTimeMillis()-start)/1000.0,3)+" seconds");
		} else {
			fileType = getFileType(path);
			IJ.log("FT"+fileType);
			switch (fileType) {
			case LUT:
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.LutLoader", path);
				if (imp.getWidth()!=0)
					imp.show();
				break;
			case ROI:
				IJ.runPlugIn("ij.plugin.RoiReader", path);
				break;
			case JAVA_OR_TEXT: case TEXT: case SUITE:
				if (IJ.altKeyDown()) { // open in TextWindow if alt key down
					new TextWindow(path,400,450);
					IJ.setKeyUp(KeyEvent.VK_ALT);
					break;
				}
				if (IJ.controlKeyDown()) { // open as list of input files if control key down

					File file = new File(path);
					int maxSize = 250000;
					long size = file.length();
					if (size>=28000) {
						String osName = System.getProperty("os.name");
						if (osName.equals("Windows 95") || osName.equals("Windows 98") || osName.equals("Windows Me"))
							maxSize = 60000;
					}
					if (size<maxSize) {
						Editor ed = (Editor)IJ.runPlugIn("ij.plugin.frame.Editor", "");
						if (ed!=null) ed.open(getDir(path), getName(path));
					} else
						new TextWindow(path,400,450);
					break;
				}


				Iterator iterator = null;
				ArrayList list = new ArrayList();
				BufferedReader br = null;
				try {
					br = new BufferedReader(new FileReader(path));
				} catch (FileNotFoundException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				String tmp;
				try {
					while (null != (tmp = br.readLine())) {
						if (IJ.debugMode) IJ.log(tmp+" is the input string from drop");
						try {
							tmp = java.net.URLDecoder.decode(tmp.replaceAll("\\+","%2b"), "UTF-8");
						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						if (tmp.startsWith("file://")) tmp = tmp.substring(7);
						if (IJ.debugMode) IJ.log("  content: "+tmp);
						if (tmp.startsWith("http://")) {
							list.add(tmp);
						} else if ( !tmp.contains(File.separator)  
								&& (tmp.toLowerCase().endsWith(".mov") 
										|| tmp.toLowerCase().endsWith(".avi") 
										|| tmp.toLowerCase().endsWith("scene.scn")
										|| tmp.toLowerCase().endsWith("suite.ste"))){
							if (IJ.debugMode) IJ.log(" stringinput");
							list.add(tmp);
						} else {
							if (IJ.debugMode) IJ.log(" fileinput");
							list.add(new File(tmp));
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				iterator = list.iterator();
				String pathList = "";
				while(iterator.hasNext()) {
					Object obj = iterator.next();
					if (obj!=null && (obj instanceof String)) {
						if ( ((String)obj).startsWith("http://fsbill.cam.uchc.edu/")){
							String[] fn = ((String)obj).split("/") ;
							String fileName = fn[fn.length-1];	
							String viewName= null;
							if (fileName.contains("MOVIE=") ) {
								String[] fn2 = fileName.split("=|&");
								if (fn2.length < 3 /*&& !fn2[fn2.length - 1].contains("scene.scn")*/) 
									fileName = fn2[fn2.length-1];
								else if  (fn2.length >= 3 && 
										(fn2[fn2.length - 3].contains(".mov") || fn2[fn2.length - 3].contains(".avi"))){
									fileName = fn2[fn2.length-3];
									if (fn2[fn2.length-1].contains("scene.scn"))
										viewName = fn2[fn2.length-1];
								}
							}
							String textPath = fileName;
							if (IJ.isMacOSX() || IJ.isLinux() )
								textPath = "/Volumes/GLOWORM_DATA/" + fileName;
							if(IJ.isWindows())
								textPath = "Q:\\" + fileName;							
							if (IJ.debugMode) IJ.log(textPath + " is the result of split and concatenate to build a path");
							if ((textPath.toLowerCase().endsWith(".mov") || textPath.toLowerCase().endsWith(".avi")) && viewName ==null  ) {
								pathList = pathList + textPath + (iterator.hasNext()? "|": "");
							} else if (textPath.toLowerCase().endsWith(".mov!") || textPath.toLowerCase().endsWith(".avi!")){
								String pathListSingle = textPath;
								//pathListSingle.replace(".mov!", ".mov");
								new QTMovieOpenerMultiMod(pathListSingle.substring(0, pathListSingle.length() - 1));

							} else if ((textPath.toLowerCase().endsWith(".mov") || textPath.toLowerCase().endsWith(".avi")) 
									&& viewName != null 
									&& viewName.contains("RedCyan")  ) {
								if (IJ.debugMode) IJ.log("Found viewname =" + viewName);
								String pathListStereo = textPath;
								new QTMovieOpenerMultiMod(pathListStereo, true, false);

							}else if (textPath.toLowerCase().endsWith("scene.scn") || 
									textPath.toLowerCase().endsWith("suite.ste"))
								if (IJ.isMacOSX() || IJ.isLinux() ) 
									textPath = textPath.replace("Q:\\", "/Volumes/GLOWORM_DATA/");
							if(IJ.isWindows())
								textPath = textPath.replace("/Volumes/GLOWORM_DATA/", "Q:\\");
							if (IJ.is64Bit())
								MQTVSSceneLoader64.runMQTVS_SceneLoader64(textPath);
							else
								MQTVSSceneLoader64.runMQTVS_SceneLoader64(textPath);


						} else if ( ((String)obj).toLowerCase().endsWith(".mov") || ((String)obj).toLowerCase().endsWith(".avi") ){
							String textPath = "";
							if (IJ.isMacOSX() || IJ.isLinux() ) 
								textPath = "/Volumes/GLOWORM_DATA/" + ((String)obj);
							if(IJ.isWindows())
								textPath = "Q:\\" + ((String)obj);
							if (IJ.debugMode) IJ.log(textPath + " is the path sent to QTMOMM");
							pathList = pathList + textPath + "|";
						} else if ( ((String)obj).toLowerCase().endsWith("scene.scn") || 
								((String)obj).toLowerCase().endsWith("suite.ste")){
							String textPath = "";
							if (IJ.isMacOSX() || IJ.isLinux() ) 
								textPath = "/Volumes/GLOWORM_DATA/" + ((String)obj);
							if(IJ.isWindows())
								textPath = "Q:\\" + ((String)obj);
							if (IJ.debugMode) IJ.log(textPath + " is the path sent to QTMOMM");
							if (IJ.is64Bit())
								MQTVSSceneLoader64.runMQTVS_SceneLoader64(textPath);
							else
								MQTVSSceneLoader64.runMQTVS_SceneLoader64(textPath);

						}else {
							openURL((String)obj);
						}	
					}
					else if (obj!=null && ( ((File)obj).getPath().toLowerCase().endsWith(".mov") 
							||((File)obj).getPath().toLowerCase().endsWith(".avi")) ) 
						pathList = pathList + ((File)obj).getPath() + "|";
					else if (obj!=null && ((File)obj).getPath().toLowerCase().endsWith("scene.scn"))
						if (IJ.is64Bit())
							MQTVSSceneLoader64.runMQTVS_SceneLoader64(((File)obj).getPath() );
						else
							MQTVSSceneLoader64.runMQTVS_SceneLoader64(((File)obj).getPath() );
					else;
				}
				if (pathList != "") {
					if (IJ.debugMode) IJ.log( pathList);
					QTMovieOpenerMultiMod.runQT_Movie_OpenerMultiMod(pathList);

				} else
					new TextWindow(path,400,450);
				break;


			case OJJ:  // ObjectJ project
				IJ.runPlugIn("ObjectJ_", path);
				break;
			case TABLE:  // ImageJ Results table
				openResultsTable(path);
				break;
			case UNKNOWN:
				if (path.toLowerCase().endsWith("suite.ste")) {
					IJ.log("ASuiteFile!");
					if (!(new File(path).canRead())){ 
						path = path.replace("Q:\\", "/Volumes/GLOWORM_DATA/");
					}
					String[] suiteTextLines = null;
					if ((new File(path)).canRead()) {
						suiteTextLines = IJ.openAsString(path).split("\n");
					} else if (path.startsWith("/Volumes/GLOWORM_DATA/")){
						BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
								RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], (path)))));
						if (in != null) {
							StringBuilder stringBuilder = new StringBuilder();
							String ls = "\n";
							String line = "";
							while (line != null) {
								try {
									line = in.readLine();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								if (line != null)
									stringBuilder.append(line.replace("http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE=", "/Volumes/GLOWORM_DATA/")).append(ls);
							}
							suiteTextLines = stringBuilder.toString().split("\n");
						}

					} else {
						BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
								RemoteMQTVSHandler.getFileInputByteArray(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], ("/Volumes/GLOWORM_DATA/"+path)))));
						if (in != null) {
							StringBuilder stringBuilder = new StringBuilder();
							String ls = "\n";
							String line = "";
							while (line != null) {
								try {
									line = in.readLine().replace("http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE=", "/Volumes/GLOWORM_DATA/");;
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								if (line != null)
									stringBuilder.append(line.replace("http://fsbill.cam.uchc.edu/cgi-bin/gloworm.pl?MOVIE=", "/Volumes/GLOWORM_DATA/")).append(ls);
							}
							suiteTextLines = stringBuilder.toString().split("\n");
						}

					}
					if (suiteTextLines.length>0 ) {
						for (String stl:suiteTextLines)
							IJ.log(stl);
						// purges any old dropped stuff?
						while (IJ.getInstance().getDragAndDrop().getIterator()!=null && IJ.getInstance().getDragAndDrop().getIterator().hasNext())
							IJ.wait(100);
						
						
						IJ.getInstance().getDragAndDrop().setIterator(Arrays.asList(suiteTextLines).iterator());
						if (IJ.getInstance().getDragAndDrop().getIterator()!=null) {
							Thread thread = new Thread(IJ.getInstance().getDragAndDrop(), "DrawAndSubDrop");
							thread.setPriority(Math.max(thread.getPriority()-1, Thread.MIN_PRIORITY));
							thread.start();
						}
					}
				} else {
					String msg =
							"File is not in a supported format, a reader\n"+
									"plugin is not available, or it was not found.";
					if (path!=null) {
						if (path.length()>64)
							path = (new File(path)).getName();
						if (path.length()<=64) {
							if (IJ.redirectingErrorMessages())
								msg += " \n   "+path;
							else
								msg += " \n	 \n"+path;
						}
					}
					if (openUsingPlugins)
						msg += "\n \nNOTE: The \"OpenUsingPlugins\" option is set.";
					IJ.wait(IJ.isMacro()?500:100); // work around for OS X thread deadlock problem
					IJ.error("Opener", msg);
					error = true;
					break;
				}
			}
		}
	}

	private boolean isText(String path) {
		if (path.endsWith(".txt") || path.endsWith(".ijm") || path.endsWith(".java")
				|| path.endsWith(".js") || path.endsWith(".html") || path.endsWith(".htm")
				|| path.endsWith("/"))
			return true;
		int lastSlash = path.lastIndexOf("/");
		if (lastSlash==-1) lastSlash = 0;
		int lastDot = path.lastIndexOf(".");
		if (lastDot==-1 || lastDot<lastSlash || (path.length()-lastDot)>6)
			return true;  // no extension
		else
			return false;
	}

	/** Opens the specified file and adds it to the File/Open Recent menu.
		Returns true if the file was opened successfully.  */
	public	boolean openAndAddToRecent(String path) {
		open(path);
		if (!error)
			Menus.addOpenRecentItem(path);
		return error;
	}

	/** Attempts to open the specified file as a tiff, bmp, dicom, fits,
		pgm, gif or jpeg image. Returns an ImagePlus object if successful.
		Modified by Gregory Jefferis to call HandleExtraFileTypes plugin if 
		the file type is unrecognised. */
	public ImagePlus openImage(String directory, String name) {
		ImagePlus imp;
		FileOpener.setSilentMode(silentMode);
		if (directory.length()>0 && !(directory.endsWith("/")||directory.endsWith("\\")))
			directory += Prefs.separator;
		String path = directory+name;
		fileType = getFileType(path);
		if (IJ.debugMode)
			IJ.log("openImage: \""+types[fileType]+"\", "+path);
		switch (fileType) {
		case TIFF:
			imp = openTiff(directory, name);
			return imp;
		case DICOM:
			imp = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", path);
			if (imp.getWidth()!=0) return imp; else return null;
		case TIFF_AND_DICOM:
			// "hybrid" files created by GE-Senographe 2000 D */
			imp = openTiff(directory,name);
			ImagePlus imp2 = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", path);
			if (imp!=null && imp2!=null)	 {		
				imp.setProperty("Info",imp2.getProperty("Info"));
				imp.setCalibration(imp2.getCalibration());
			}
			if (imp==null) imp=imp2;
			return imp;
		case FITS:
			imp = (ImagePlus)IJ.runPlugIn("ij.plugin.FITS_Reader", path);
			if (imp.getWidth()!=0) return imp; else return null;
		case PGM:
			imp = (ImagePlus)IJ.runPlugIn("ij.plugin.PGM_Reader", path);
			if (imp.getWidth()!=0) {
				if (imp.getStackSize()==3 && imp.getBitDepth()==16)
					imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
				return imp;
			} else
				return null;
		case JPEG: case GIF:
			imp = openJpegOrGif(directory, name);
			if (imp!=null&&imp.getWidth()!=0) return imp; else return null;
		case PNG: 
			imp = openUsingImageIO(directory+name);
			if (imp!=null&&imp.getWidth()!=0) return imp; else return null;
		case BMP:
			imp = (ImagePlus)IJ.runPlugIn("ij.plugin.BMP_Reader", path);
			if (imp.getWidth()!=0) return imp; else return null;
		case ZIP:
			return openZip(path);
			/*			case AVI:
				AVI_Reader reader = (AVI_Reader)IJ.runPlugIn("ij.plugin.AVI_Reader", path);
				return reader.getImagePlus();
			 */	
		case UNKNOWN: case TEXT:
			// Call HandleExtraFileTypes plugin to see if it can handle unknown format
			int[] wrap = new int[] {fileType};
			imp = openWithHandleExtraFileTypes(path, wrap);
			if (imp!=null && imp.getNChannels()>1)
				imp = new CompositeImage(imp, CompositeImage.COLOR);
			fileType = wrap[0];
			return imp;
		default:
			return null;
		}
	}

	/** Attempts to open the specified file as a tiff, bmp, dicom, fits,
		pgm, gif or jpeg. Displays a file open dialog if 'path' is null or
		an empty string. Returns an ImagePlus object if successful. */
	public ImagePlus openImage(String path) {
		if (path==null || path.equals(""))
			path = getPath();
		if (path==null) return null;
		ImagePlus img = null;
		if (path.indexOf("://")>0)
			img = openURL(path);
		else
			img = openImage(getDir(path), getName(path));
		return img;
	}

	/** Open the nth image of the specified tiff stack. */
	public ImagePlus openImage(String path, int n) {
		if (path==null || path.equals(""))
			path = getPath();
		if (path==null) return null;
		int type = getFileType(path);
		if (type!=TIFF)
			throw new IllegalArgumentException("TIFF file require");
		return openTiff(path, n);
	}

	String getPath() {
		OpenDialog od = new OpenDialog("Open", "");
		String dir = od.getDirectory();
		String name = od.getFileName();
		if (name==null)
			return null;
		else
			return dir+name;
	}

	/** Attempts to open the specified url as a tiff, zip compressed tiff, 
		dicom, gif or jpeg. Tiff file names must end in ".tif", ZIP file names 
		must end in ".zip" and dicom file names must end in ".dcm". Returns an 
		ImagePlus object if successful. */
	public ImagePlus openURL(String url) {
		try {
			String name = "";
			int index = url.lastIndexOf('/');
			if (index==-1)
				index = url.lastIndexOf('\\');
			if (index>0)
				name = url.substring(index+1);
			else
				throw new MalformedURLException("Invalid URL: "+url);
			if (url.indexOf(" ")!=-1)
				url = url.replaceAll(" ", "%20");
			URL u = new URL(url);
			IJ.showStatus(""+url);
			String lurl = url.toLowerCase(Locale.US);
			ImagePlus imp = null;
			if (lurl.endsWith(".tif"))
				imp = openTiff(u.openStream(), name);
			else if (lurl.endsWith(".zip"))
				imp = openZipUsingUrl(u);
			else if (lurl.endsWith(".jpg") || lurl.endsWith(".gif"))
				imp = openJpegOrGifUsingURL(name, u);
			else if (lurl.endsWith(".dcm") || lurl.endsWith(".ima")) {
				imp = (ImagePlus)IJ.runPlugIn("ij.plugin.DICOM", url);
				if (imp!=null && imp.getWidth()==0) imp = null;
			} else if (lurl.endsWith(".png"))
				imp = openPngUsingURL(name, u);
			else {
				URLConnection uc = u.openConnection();
				String type = uc.getContentType();
				if (type!=null && (type.equals("image/jpeg")||type.equals("image/gif")))
					imp = openJpegOrGifUsingURL(name, u);
				else if (type!=null && type.equals("image/png"))
					imp = openPngUsingURL(name, u);
				else
					imp = openWithHandleExtraFileTypes(url, new int[]{0});
			}
			IJ.showStatus("");
			return imp;
		} catch (Exception e) {
			String msg = e.getMessage();
			if (msg==null || msg.equals(""))
				msg = "" + e;	
			IJ.log("Open URL\n"+msg + "\n" + url);
			return null;
		} 
	}

	/** Used by open() and IJ.open() to open text URLs. */
	void openTextURL(String url) {
		if (url.endsWith(".pdf")||url.endsWith(".zip"))
			return;
		String text = IJ.openUrlAsString(url);
		String name = url.substring(7);
		int index = name.lastIndexOf("/");
		int len = name.length();
		if (index==len-1)
			name = name.substring(0, len-1);
		else if (index!=-1 && index<len-1)
			name = name.substring(index+1);
		name = name.replaceAll("%20", " ");
		Editor ed = new Editor();
		ed.setSize(600, 300);
		ed.create(name, text);
		IJ.showStatus("");
	}


	public ImagePlus openWithHandleExtraFileTypes(String path, int[] fileType) {
		ImagePlus imp = null;
		if (path.endsWith(".db")) {
			// skip hidden Thumbs.db files on Windows
			fileType[0] = CUSTOM;
			return null;
		}
		imp = (ImagePlus)IJ.runPlugIn("HandleExtraFileTypes", path);
		if (imp==null) return null;
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi==null) {
			fi = new FileInfo();
			fi.width = imp.getWidth();
			fi.height = imp.getHeight();
			fi.directory = getDir(path);
			fi.fileName = getName(path);
			imp.setFileInfo(fi);
		}
		if (imp.getWidth()>0 && imp.getHeight()>0) {
			fileType[0] = CUSTOM;
			return imp;
		} else {
			if (imp.getWidth()==-1)
				fileType[0] = CUSTOM; // plugin opened image so don't display error
			return null;
		}
	}

	/** Opens the ZIP compressed TIFF or DICOM at the specified URL. */
	ImagePlus openZipUsingUrl(URL url) throws IOException {
		URLConnection uc = url.openConnection();
		InputStream in = uc.getInputStream();
		ZipInputStream zis = new ZipInputStream(in);
		ZipEntry entry = zis.getNextEntry();
		if (entry==null) return null;
		String name = entry.getName();
		if (!(name.endsWith(".tif")||name.endsWith(".dcm")))
			throw new IOException("This ZIP archive does not appear to contain a .tif or .dcm file\n"+name);
		if (name.endsWith(".dcm"))
			return openDicomStack(zis, entry);
		else
			return openTiff(zis, name);
	}

	ImagePlus openDicomStack(ZipInputStream zis, ZipEntry entry) throws IOException {
		ImagePlus imp = null;
		int count = 0;
		ImageStack stack = null;
		while (true) {
			if (count>0) entry = zis.getNextEntry();
			if (entry==null) break;
			String name = entry.getName();
			ImagePlus imp2 = null;
			if (name.endsWith(".dcm")) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[4096];
				int len, byteCount=0, progress=0;
				while (true) {
					len = zis.read(buf);
					if (len<0) break;
					out.write(buf, 0, len);
					byteCount += len;
					//IJ.showProgress((double)(byteCount%fileSize)/fileSize);
				}
				byte[] bytes = out.toByteArray();
				out.close();
				DICOM dcm = new DICOM(new ByteArrayInputStream(bytes));
				dcm.run(name);
				imp2 = dcm;
			}
			zis.closeEntry();
			if (imp2==null) continue;
			count++;
			String label = imp2.getTitle();
			String info = (String)imp2.getProperty("Info");
			if (info!=null) label += "\n" + info;
			if (count==1) {
				imp = imp2;
				imp.getStack().setSliceLabel(label, 1);
			} else {
				stack = imp.getStack();
				stack.addSlice(label, imp2.getProcessor());
				imp.setStack(stack);
			}
		}
		zis.close();
		IJ.showProgress(1.0);
		if (count==0)
			throw new IOException("This ZIP archive does not appear to contain any .dcm files");
		return imp;
	}

	ImagePlus openJpegOrGifUsingURL(String title, URL url) {
		ImagePlus imp = null;
		if (url==null) return null;
		if (url.getFile().toLowerCase().endsWith(".gif")) {
			URLConnection uc;
			String tempPath = IJ.getDirectory("temp");
			try {
				ReadableByteChannel rbc = java.nio.channels.Channels.newChannel(url.openStream());

				FileOutputStream fos = new FileOutputStream(tempPath+title+(!title.toLowerCase().endsWith(".gif")?".gif":""));
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}				

			imp = new GIF_Reader(tempPath+title+(!title.toLowerCase().endsWith(".gif")?".gif":""));
			return imp;
		} else {
			Image img = Toolkit.getDefaultToolkit().createImage(url);
			if (img!=null) {
				imp = new ImagePlus(title, img);
				return imp;
			} else
				return null;
		}
	}

	ImagePlus openPngUsingURL(String title, URL url) {
		if (url==null) return null;
		Image img = null;
		try {
			img = ImageIO.read(url);
		} catch (IOException e) {
			IJ.log(""+e);
		}
		if (img!=null) {
			ImagePlus imp = new ImagePlus(title, img);
			return imp;
		} else
			return null;
	}

	ImagePlus openJpegOrGif(String dir, String name) {
		ImagePlus imp = null;
		if (name.toLowerCase().endsWith(".gif")) {
			imp = new GIF_Reader(dir+name);
		} else {
			Image img = Toolkit.getDefaultToolkit().createImage(dir+name);
			if (img!=null) {
				try {
					imp = new ImagePlus(name, img);
				} catch (IllegalStateException e) {
					IJ.error("Opener", e.getMessage()+"\n(Note: IJ cannot open CMYK JPEGs)\n \n"+dir+name);
					return null; // error loading image
				}				
				if (imp.getType()==ImagePlus.COLOR_RGB)
					convertGrayJpegTo8Bits(imp);
				FileInfo fi = new FileInfo();
				fi.fileFormat = fi.GIF_OR_JPG;
				fi.fileName = name;
				fi.directory = dir;
				imp.setFileInfo(fi);
			}
		}
		return imp;
	}

	ImagePlus openUsingImageIO(String path) {
		ImagePlus imp = null;
		BufferedImage img = null;
		File f = new File(path);
		try {
			img = ImageIO.read(f);
		} catch (Exception e) {
			IJ.error("Open Using ImageIO", ""+e);
		} 
		if (img==null) return null;
		if (img.getColorModel().hasAlpha()) {
			int width = img.getWidth();
			int height = img.getHeight();
			BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics g = bi.getGraphics();
			g.setColor(Color.white);
			g.fillRect(0,0,width,height);
			g.drawImage(img, 0, 0, null);
			img = bi;
		}
		imp = new ImagePlus(f.getName(), img);
		FileInfo fi = new FileInfo();
		fi.fileFormat = fi.IMAGEIO;
		fi.fileName = f.getName();
		fi.directory = f.getParent()+File.separator;
		imp.setFileInfo(fi);
		return imp;
	}

	/** If the specified image is grayscale, convert it to 8-bits. */
	public static void convertGrayJpegTo8Bits(ImagePlus imp) {
		ImageProcessor ip = imp.getProcessor();
		if (ip.isGrayscale()) {
			IJ.showStatus("Converting to 8-bit grayscale");
			new ImageConverter(imp).convertToGray8();
		}
	}

	/** Are all the images in this file the same size and type? */
	boolean allSameSizeAndType(FileInfo[] info) {
		boolean sameSizeAndType = true;
		boolean contiguous = true;
		long startingOffset = info[0].getOffset();
		int size = info[0].width*info[0].height*info[0].getBytesPerPixel();
		for (int i=1; i<info.length; i++) {
			sameSizeAndType &= info[i].fileType==info[0].fileType
					&& info[i].width==info[0].width
					&& info[i].height==info[0].height;
			contiguous &= info[i].getOffset()==startingOffset+i*size;
		}
		if (contiguous &&  info[0].fileType!=FileInfo.RGB48)
			info[0].nImages = info.length;
		//if (IJ.debugMode) {
		//	IJ.log("sameSizeAndType: " + sameSizeAndType);
		//	IJ.log("contiguous: " + contiguous);
		//}
		return sameSizeAndType;
	}

	/** Attemps to open a tiff file as a stack. Returns 
		an ImagePlus object if successful. */
	public ImagePlus openTiffStack(FileInfo[] info) {
		if (info.length>1 && !allSameSizeAndType(info))
			return null;
		FileInfo fi = info[0];
		if (fi.nImages>1)
			return new FileOpener(fi).open(false); // open contiguous images as stack
		else {
			ColorModel cm = createColorModel(fi);
			ImageStack stack = new ImageStack(fi.width, fi.height, cm);
			Object pixels = null;
			long skip = fi.getOffset();
			int imageSize = fi.width*fi.height*fi.getBytesPerPixel();
			if (info[0].fileType==FileInfo.GRAY12_UNSIGNED) {
				imageSize = (int)(fi.width*fi.height*1.5);
				if ((imageSize&1)==1) imageSize++; // add 1 if odd
			} if (info[0].fileType==FileInfo.BITMAP) {
				int scan=(int)Math.ceil(fi.width/8.0);
				imageSize = scan*fi.height;
			}
			long loc = 0L;
			int nChannels = 1;
			try {
				InputStream is = createInputStream(fi);
				ImageReader reader = new ImageReader(fi);
				IJ.resetEscape();
				for (int i=0; i<info.length; i++) {
					nChannels = 1;
					Object[] channels = null;
					if (!silentMode)
						IJ.showStatus("Reading: " + (i+1) + "/" + info.length);
					if (IJ.escapePressed()) {
						IJ.beep();
						IJ.showProgress(1.0);
						return null;
					}
					if (info[i].compression>=FileInfo.LZW) {
						fi.stripOffsets = info[i].stripOffsets;
						fi.stripLengths = info[i].stripLengths;
					}
					if (info[i].samplesPerPixel>1 && !(info[i].getBytesPerPixel()==3||info[i].getBytesPerPixel()==6)) {
						nChannels = fi.samplesPerPixel;
						channels = new Object[nChannels];
						for (int c=0; c<nChannels; c++) {
							pixels = reader.readPixels(is, c==0?skip:0L);
							channels[c] = pixels;
						}
					} else 
						pixels = reader.readPixels(is, skip);
					if (pixels==null && channels==null) break;
					loc += imageSize*nChannels+skip;
					if (i<(info.length-1)) {
						skip = info[i+1].getOffset()-loc;
						if (info[i+1].compression>=FileInfo.LZW) skip = 0;
						if (skip<0L) {
							IJ.error("Opener", "Unexpected image offset");
							break;
						}
					}
					if (fi.fileType==FileInfo.RGB48) {
						Object[] pixels2 = (Object[])pixels;
						stack.addSlice(null, pixels2[0]);					
						stack.addSlice(null, pixels2[1]);					
						stack.addSlice(null, pixels2[2]);
						isRGB48 = true;					
					} else if (nChannels>1) {
						for (int c=0; c<nChannels; c++) {
							if (channels[c]!=null)
								stack.addSlice(null, channels[c]);
						}
					} else
						stack.addSlice(null, pixels);
					IJ.showProgress(i, info.length);
				}
				is.close();
			}
			catch (Exception e) {
				IJ.handleException(e);
			}
			catch(OutOfMemoryError e) {
				IJ.outOfMemory(fi.fileName);
				stack.deleteLastSlice();
				stack.deleteLastSlice();
			}
			IJ.showProgress(1.0);
			if (stack.getSize()==0)
				return null;
			if (fi.fileType==FileInfo.GRAY16_UNSIGNED||fi.fileType==FileInfo.GRAY12_UNSIGNED
					||fi.fileType==FileInfo.GRAY32_FLOAT||fi.fileType==FileInfo.RGB48) {
				ImageProcessor ip = stack.getProcessor(1);
				ip.resetMinAndMax();
				stack.update(ip);
			}
			//if (fi.whiteIsZero)
			//	new StackProcessor(stack, stack.getProcessor(1)).invert();
			ImagePlus imp = new ImagePlus(fi.fileName, stack);
			new FileOpener(fi).setCalibration(imp);
			imp.setFileInfo(fi);
			int stackSize = stack.getSize();
			if (nChannels>1 && (stackSize%nChannels)==0) {
				imp.setDimensions(nChannels, stackSize/nChannels, 1);
				imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
				imp.setOpenAsHyperStack(true);
			}
			IJ.showProgress(1.0);
			return imp;
		}
	}

	/** Attempts to open the specified file as a tiff.
		Returns an ImagePlus object if successful. */
	public ImagePlus openTiff(String directory, String name) {
		TiffDecoder td = new TiffDecoder(directory, name);
		if (IJ.debugMode) td.enableDebugging();
		FileInfo[] info=null;
		try {info = td.getTiffInfo(0);}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return null;
		}
		if (info==null)
			return null;
		return openTiff2(info);
	}

	/** Opens the nth image of the specified TIFF stack. */
	public ImagePlus openTiff(String path, int n) {
		TiffDecoder td = new TiffDecoder(getDir(path), getName(path));
		if (IJ.debugMode) td.enableDebugging();
		FileInfo[] info=null;
		try {info = td.getTiffInfo(0);}
		catch (IOException e) {
			String msg = e.getMessage();
			if (msg==null||msg.equals("")) msg = ""+e;
			IJ.error("TiffDecoder", msg);
			return null;
		}
		if (info==null) return null;
		FileInfo fi = info[0];
		if (info.length==1 && fi.nImages>1) {
			if (n<1 || n>fi.nImages)
				throw new IllegalArgumentException("N out of 1-"+fi.nImages+" range");
			long size = fi.width*fi.height*fi.getBytesPerPixel();
			fi.longOffset = fi.getOffset() + (n-1)*(size+fi.gapBetweenImages);
			fi.offset = 0;
			fi.nImages = 1;
		} else {
			if (n<1 || n>info.length)
				throw new IllegalArgumentException("N out of 1-"+info.length+" range");
			fi.longOffset = info[n-1].getOffset();
			fi.offset = 0;
			fi.stripOffsets = info[n-1].stripOffsets; 
			fi.stripLengths = info[n-1].stripLengths; 
		}
		FileOpener fo = new FileOpener(fi);
		return fo.open(false);
	}

	/** Attempts to open the specified inputStream as a
		TIFF, returning an ImagePlus object if successful. */
	public ImagePlus openTiff(InputStream in, String name) {
		FileInfo[] info = null;
		try {
			TiffDecoder td = new TiffDecoder(in, name);
			if (IJ.debugMode) td.enableDebugging();
			info = td.getTiffInfo(0);
		} catch (FileNotFoundException e) {
			IJ.error("TiffDecoder", "File not found: "+e.getMessage());
			return null;
		} catch (Exception e) {
			IJ.error("TiffDecoder", ""+e);
			return null;
		}
		return openTiff2(info);
	}

	/** Opens a single TIFF or DICOM contained in a ZIP archive,
		or a ZIPed collection of ".roi" files created by the Tag Manager. */	
	public ImagePlus openZip(String path) {
		ImagePlus imp = null;
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(path));
			if (zis==null) return null;
			ZipEntry entry = zis.getNextEntry();
			if (entry==null) return null;
			String name = entry.getName();
			if (name.endsWith(".roi")) {
				zis.close();
				if (!silentMode)
					if (IJ.isMacro() && Interpreter.isBatchMode() && imp.getRoiManager()==null)
						IJ.log("Use roiManager(\"Open\", path) instead of open(path)\nto open ROI sets in batch mode macros.");
					else
						IJ.runMacro("roiManager(\"Open\", getArgument());", path);
				return null;
			}
			if (name.endsWith(".tif")) {
				imp = openTiff(zis, name);
			} else if (name.endsWith(".dcm")) {
				DICOM dcm = new DICOM(zis);
				dcm.run(name);
				imp = dcm;
			} else {
				zis.close();
				IJ.error("This ZIP archive does not appear to contain a \nTIFF (\".tif\") or DICOM (\".dcm\") file, or ROIs (\".roi\").");
				return null;
			}
		} catch (Exception e) {
			IJ.error("ZipDecoder", ""+e);
			return null;
		}
		File f = new File(path);
		FileInfo fi = imp.getOriginalFileInfo();
		fi.fileFormat = FileInfo.ZIP_ARCHIVE;
		fi.fileName = f.getName();
		fi.directory = f.getParent()+File.separator;
		return imp;
	}

	/** Deserialize a byte array that was serialized using the FileSaver.serialize(). */
	public ImagePlus deserialize(byte[] bytes) {
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		TiffDecoder decoder = new TiffDecoder(stream, "Untitled");
		if (IJ.debugMode)
			decoder.enableDebugging();
		FileInfo[] info = null;
		try {
			info = decoder.getTiffInfo(0);
		} catch (IOException e) {
			return null;
		}
		FileOpener opener = new FileOpener(info[0]);
		ImagePlus imp = opener.open(false);
		if (imp==null)
			return null;
		imp.setTitle(info[0].fileName);
		imp = makeComposite(imp, info[0]);
		return imp;
	}

	private ImagePlus makeComposite(ImagePlus imp, FileInfo fi) {
		int c = imp.getNChannels();
		boolean composite = c>1 && fi.description!=null && fi.description.indexOf("mode=")!=-1;
		if (c>1 && (imp.getOpenAsHyperStack()||composite) && !imp.isComposite() && imp.getType()!=ImagePlus.COLOR_RGB) {
			int mode = CompositeImage.COLOR;
			if (fi.description!=null) {
				if (fi.description.indexOf("mode=composite")!=-1)
					mode = CompositeImage.COMPOSITE;
				else if (fi.description.indexOf("mode=gray")!=-1)
					mode = CompositeImage.GRAYSCALE;
			}
			imp = new CompositeImage(imp, mode);
		}
		return imp;
	}


	public String getName(String path) {
		int i = path.lastIndexOf('/');
		if (i==-1)
			i = path.lastIndexOf('\\');
		if (i>0)
			return path.substring(i+1);
		else
			return path;
	}

	public String getDir(String path) {
		int i = path.lastIndexOf('/');
		if (i==-1)
			i = path.lastIndexOf('\\');
		if (i>0)
			return path.substring(0, i+1);
		else
			return "";
	}

	ImagePlus openTiff2(FileInfo[] info) {
		if (info==null)
			return null;
		ImagePlus imp = null;
		if (IJ.debugMode) // dump tiff tags
			IJ.log(info[0].debugInfo);
		if (info.length>1) { // try to open as stack
			imp = openTiffStack(info);
			if (imp!=null)
				return imp;
		}
		FileOpener fo = new FileOpener(info[0]);
		imp = fo.open(false);
		if (imp==null) return null;
		int[] offsets = info[0].stripOffsets;
		if (offsets!=null&&offsets.length>1 && offsets[offsets.length-1]<offsets[0])
			ij.IJ.run(imp, "Flip Vertically", "stack");
		int c = imp.getNChannels();
		boolean composite = c>1 && info[0].description!=null && info[0].description.indexOf("mode=")!=-1;
//		if (c>1 && (imp.getOpenAsHyperStack()||composite) && !imp.isComposite() && imp.getType()!=ImagePlus.COLOR_RGB) {
//			int mode = CompositeImage.COLOR;
//			if (info[0].description!=null) {
//				if (info[0].description.indexOf("mode=composite")!=-1)
//					mode = CompositeImage.COMPOSITE;
//				else if (info[0].description.indexOf("mode=gray")!=-1)
//					mode = CompositeImage.GRAYSCALE;
//			}
//			imp = new CompositeImage(imp, mode);
//		}
		if (c>1 && info[0].description!=null) {
			int mode = CompositeImage.COMPOSITE;
			if (info[0].description.indexOf("mode=color")!=-1)
				mode = CompositeImage.COLOR;
			else if (info[0].description.indexOf("mode=gray")!=-1)
				mode = CompositeImage.GRAYSCALE;
			imp = new CompositeImage(imp, mode);
		}
		return imp;
	}

	// Eager to add this method to open QTVS and MQTVS by drag & drop of one or more mov files!
	//...actually, I think I've already added that functionality elsewhere.
	public ImagePlus openQuickTime(String path) {
		ImagePlus imp = null;
		return imp;
	}



	/** Attempts to open the specified ROI, returning null if unsuccessful. */
	public Roi openRoi(String path) {
		Roi roi = null;
		RoiDecoder rd = new RoiDecoder(path);
		try {roi = rd.getRoi();}
		catch (IOException e) {
			IJ.error("RoiDecoder", e.getMessage());
			return null;
		}
		return roi;
	}

	/** Opens a tab or comma delimited text file in the Results window. */
	public static void openResultsTable(String path) {
		try {
			ResultsTable rt = ResultsTable.open(path);
			if (rt!=null) rt.show("Results");
		} catch(IOException e) {
			IJ.error("Open Results", e.getMessage());
		}
	}

	public static String getFileFormat(String path) {
		if (!((new File(path)).exists()))
			return("not found");
		else
			return Opener.types[(new Opener()).getFileType(path)];
	}

	/**
	Attempts to determine the image file type by looking for
	'magic numbers' and the file name extension.
	 */
	public int getFileType(String path) {
		if (openUsingPlugins && !path.endsWith(".txt") &&  !path.endsWith(".java"))
			return UNKNOWN;
		File file = new File(path);
		String name = file.getName();
		InputStream is;
		byte[] buf = new byte[132];
		try {
			is = new FileInputStream(file);
			is.read(buf, 0, 132);
			is.close();
		} catch (IOException e) {
			return UNKNOWN;
		}

		int b0=buf[0]&255, b1=buf[1]&255, b2=buf[2]&255, b3=buf[3]&255;
		//if (IJ.debugMode) IJ.log("getFileType: "+ name+" "+b0+" "+b1+" "+b2+" "+b3);

		// Combined TIFF and DICOM created by GE Senographe scanners
		if (buf[128]==68 && buf[129]==73 && buf[130]==67 && buf[131]==77
				&& ((b0==73 && b1==73)||(b0==77 && b1==77)))
			return TIFF_AND_DICOM;

		// Big-endian TIFF ("MM")
		if (name.endsWith(".lsm"))
			return UNKNOWN; // The LSM	Reader plugin opens these files
		if (b0==73 && b1==73 && b2==42 && b3==0 && !(bioformats&&name.endsWith(".flex")))
			return TIFF;

		// Little-endian TIFF ("II")
		if (b0==77 && b1==77 && b2==0 && b3==42)
			return TIFF;

		// JPEG
		if (b0==255 && b1==216 && b2==255)
			return JPEG;

		// GIF ("GIF8")
		if (b0==71 && b1==73 && b2==70 && b3==56)
			return GIF;

		name = name.toLowerCase(Locale.US);

		// DICOM ("DICM" at offset 128)
		if (buf[128]==68 && buf[129]==73 && buf[130]==67 && buf[131]==77 || name.endsWith(".dcm")) {
			return DICOM;
		}

		// ACR/NEMA with first tag = 00002,00xx or 00008,00xx
		if ((b0==8||b0==2) && b1==0 && b3==0 && !name.endsWith(".spe") && !name.equals("fid"))	
			return DICOM;

		// PGM ("P1", "P4", "P2", "P5", "P3" or "P6")
		if (b0==80&&(b1==49||b1==52||b1==50||b1==53||b1==51||b1==54)&&(b2==10||b2==13||b2==32||b2==9))
			return PGM;

		// Lookup table
		if (name.endsWith(".lut"))
			return LUT;

		// PNG
		if (b0==137 && b1==80 && b2==78 && b3==71)
			return PNG;

		// ZIP containing a TIFF
		if (name.endsWith(".zip"))
			return ZIP;

		// FITS ("SIMP")
		if ((b0==83 && b1==73 && b2==77 && b3==80) || name.endsWith(".fts.gz") || name.endsWith(".fits.gz"))
			return FITS;

		// Java source file, text file or macro
		if (name.endsWith(".java") || name.endsWith(".txt") || name.endsWith(".ijm") || name.endsWith(".js") || name.endsWith(".html"))
			return JAVA_OR_TEXT;

		// ImageJ, NIH Image, Scion Image for Windows ROI
		if (b0==73 && b1==111) // "Iout"
			return ROI;

		// ObjectJ project
		if ((b0=='o' && b1=='j' && b2=='j' && b3==0) || name.endsWith(".ojj") )
			return OJJ;

		// Results table (tab-delimited or comma-separated tabular text)
		if (name.endsWith(".xls") || name.endsWith(".csv")) 
			return TABLE;

		// Text file
		boolean isText = true;
		for (int i=0; i<10; i++) {
			int c = buf[i]&255;
			if ((c<32&&c!=9&&c!=10&&c!=13) || c>126) {
				isText = false;
				break;
			}
		}
		if (isText)
			return TEXT;

		// BMP ("BM")
		if ((b0==66 && b1==77)||name.endsWith(".dib"))
			return BMP;

		// AVI
		if (name.endsWith(".avi")){
			//return AVI;
			if (IJ.debugMode) IJ.log(getPath() + " is the name.");
			return MOV;
		}

		// QuickTime movie
		if (name.endsWith(".mov")){
			if (IJ.debugMode) IJ.log(getPath() + " is the name.");
			return MOV;
		}
		return UNKNOWN;
	}

	/** Returns an IndexColorModel for the image specified by this FileInfo. */
	ColorModel createColorModel(FileInfo fi) {
		if (fi.fileType==FileInfo.COLOR8 && fi.lutSize>0)
			return new IndexColorModel(8, fi.lutSize, fi.reds, fi.greens, fi.blues);
		else
			return LookUpTable.createGrayscaleColorModel(fi.whiteIsZero);
	}

	/** Returns an InputStream for the image described by this FileInfo. */
	InputStream createInputStream(FileInfo fi) throws IOException, MalformedURLException {
		if (fi.inputStream!=null)
			return fi.inputStream;
		else if (fi.url!=null && !fi.url.equals(""))
			return new URL(fi.url+fi.fileName).openStream();
		else {
			File f = new File(fi.directory + fi.fileName);
			if (f==null || f.isDirectory())
				return null;
			else {
				InputStream is = new FileInputStream(f);
				if (fi.compression>=FileInfo.LZW)
					is = new RandomAccessStream(is);
				return is;
			}
		}
	}

	void openRGB48(ImagePlus imp) {
		isRGB48 = false;
		int stackSize = imp.getStackSize();
		imp.setDimensions(3, stackSize/3, 1);
		imp = new CompositeImage(imp, CompositeImage.COMPOSITE);
		imp.show();
	}

	/** The "Opening: path" status message is not displayed in silent mode. */
	public void setSilentMode(boolean mode) {
		silentMode = mode;
	}

	/** Open all images using HandleExtraFileTypes. Set from
		a macro using setOption("openUsingPlugins", true). */
	public static void setOpenUsingPlugins(boolean b) {
		openUsingPlugins = b;
	}

	/** Returns the state of the openUsingPlugins flag. */
	public static boolean getOpenUsingPlugins() {
		return openUsingPlugins;
	}

}
