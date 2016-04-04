package client;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.VirtualStack;
import ij.plugin.frame.ColorLegend;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.gui.ImageWindow;
import ij.gui.StackWindow;
import ij.gui.TextRoi;
import ij.io.FileSaver;
import ij.io.Opener;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.imageio.ImageIO;

import org.apache.commons.net.util.Base64;
import org.vcell.gloworm.MultiChannelController;
import org.vcell.gloworm.MultiQTVirtualStack;
import org.vcell.gloworm.QTVirtualStack;

import compute.Compute;
import engine.Pi;

public class RemoteMQTVSHandler {

	private  Compute comp;
	private  int[] qtDims;
	private int stkWidth, stkHeight, stkNChannels, stkNSlices, stkNFrames;
	public  String[] moviePathNames;
	private VirtualStack stack;
	public long lastGetTime=0;
	public ImagePlus imp2;

	public double jpegQuality=1;
	public int resolutionToGet=1;
	public boolean burnInComplete= false;
	private ArrayList<Thread> threadArrayList = new ArrayList<Thread>();
	private int remoteImpID = 0;
	public Compute compQ;
	private ImageWindow win2;
	private static String serverReturnString;
	private static String[] spawnStrings;
	private static String lookupString;
	private LinkedHashMap<Integer,ImageProcessor> dejaVuIPLinkedHashMap = new LinkedHashMap<Integer,ImageProcessor>();
	private File[] localMovieDirs;
	private String[] movieSlices;
	private boolean stretchToFitOverlay = false;
	private boolean eightBit = false;
	private boolean viewOverlay = true;
	private boolean horizontal = true;
	private boolean sideSideStereo = false;
	private boolean redCyanStereo = false;
	private boolean grid = false;
	private boolean silentlyUpdateScene = false;
	private Thread reloadThread;
	private int rotation;
	private boolean rcsPrX = false;


	public static void main(String args[]) {
		if (IJ.getInstance() == null)
			ImageJ.main(new String[] {""});
		new RemoteMQTVSHandler(args);
	}

	public static RemoteMQTVSHandler build(String url, String portOffset, String pathsThenSlices, 
			boolean stretchToFitOverlay, boolean viewOverlay, boolean grayscale, boolean grid, boolean horizontal, boolean sideSideStereo, boolean redCyanStereo, boolean rcsPrx, boolean silentlyUpdateScene) {
		ArrayList<String> argArrayList = new ArrayList<String>();
		argArrayList.add(url);
		argArrayList.add(portOffset);
		if (stretchToFitOverlay)
			argArrayList.add("-stretch");
		if (!viewOverlay)
			argArrayList.add("-montage");
		if (grayscale)
			argArrayList.add("-grayscale");
		if (grid)
			argArrayList.add("-grid");
		if (!horizontal)
			argArrayList.add("-vertical");
		if (sideSideStereo)
			argArrayList.add("-sidesidestereo");
		if (redCyanStereo)
			argArrayList.add("-redcyanstereo");
		if (rcsPrx) 
			argArrayList.add("-rcsprx");
		if (silentlyUpdateScene)
			argArrayList.add("-silentlyupdatescene");
		for (String path:pathsThenSlices.split(" "))
			argArrayList.add(path);
		return new RemoteMQTVSHandler(argArrayList.toArray(new String[argArrayList.size()]));
	}

	public RemoteMQTVSHandler(String args[]) {

		boolean firstRun = true;
		IJ.log("while loop " + silentlyUpdateScene);
		firstRun = false;
		comp = null;
		compQ = null;
		try {
			String remoteEngineName = args[0]+"/HEAD";
			int skip = 2;
			for (int a=skip;a<args.length-1;a++) {
				if (args[a].startsWith("-")) 
					skip++;
				if (args[a].toLowerCase() == "-stretch")
					stretchToFitOverlay = true;
				if (args[a].toLowerCase() == "-montage")
					viewOverlay = false;
				if (args[a].toLowerCase() == "-grayscale")
					eightBit = true;
				if (args[a].toLowerCase() == "-grid")
					grid = true;
				if (args[a].toLowerCase() == "-vertical")
					horizontal = false;
				if (args[a].toLowerCase() == "-sidesidestereo")
					sideSideStereo = true;
				if (args[a].toLowerCase() == "-redcyanstereo")
					redCyanStereo = true;
				if (args[a].toLowerCase() == "-silentlyupdatescene")
					silentlyUpdateScene  = true;
				if (args[a].toLowerCase() == "-rcsprx")
					rcsPrX  = true;
			}
			if (args.length-skip == 1) {
				moviePathNames = new String[]{args[skip]};
				movieSlices = new String[]{""+1};
			} else {
				moviePathNames = new String[(args.length-skip)/2];
				movieSlices = new String[(args.length-skip)/2];
				for (int a=skip;a<args.length-1;a=a+2) {
					moviePathNames[(a-skip)/2]=args[a];
					movieSlices[(a-skip)/2]=args[a+1];
				}
			}
			IJ.log(remoteEngineName);
			IJ.log("contacting CytoSHOW server...");
			comp = (Compute) Naming.lookup(remoteEngineName);
			IJ.log(comp.toString()+"7");
			while (spawnStrings == null || serverReturnString.contains("Exception")) {
				spawnStrings = new String[] {""+System.currentTimeMillis(),
						"blank", 
						args[0].replaceAll("/", "").split(":")[0], ""+(Integer.parseInt(args[0].split(":")[1])+Integer.parseInt(args[1]))};
				serverReturnString =comp.spawnNewServer(spawnStrings[0],spawnStrings[1],spawnStrings[2],spawnStrings[3]);
				lookupString = "//"+serverReturnString.replaceAll("//","").replaceAll(".*>(.*)", "$1");
			}

			compQ = (Compute) Naming.lookup(lookupString);
		}catch (RemoteException e2) {
			if (comp==null) {
				IJ.log(lookupString + "failure to connect.1");
				return;
			}
			try {
				serverReturnString =comp.spawnNewServer(spawnStrings[0],spawnStrings[1],spawnStrings[2],spawnStrings[3]);
				lookupString = "//"+serverReturnString.replaceAll("//","").replaceAll(".*>(.*)", "$1");
			} catch (RemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				compQ = (Compute) Naming.lookup(lookupString);
			} catch (MalformedURLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (RemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (NotBoundException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			e2.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NumberFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 

		if (moviePathNames.length>0) {
			try {
				remoteImpID = compQ.setUpMovie(moviePathNames, movieSlices, Integer.parseInt(args[0].split(":")[1])+Integer.parseInt(args[1]), rcsPrX);
			} catch (NumberFormatException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (RemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		if (remoteImpID == 0) {
			return;
		}
		localMovieDirs = new File[moviePathNames.length];

		for (int m=0;m<moviePathNames.length;m++) {
			localMovieDirs[m] = new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+moviePathNames[m]+(rcsPrX?"rcsPrX":""));
			if (!localMovieDirs[m].canWrite()) {
				if (localMovieDirs[m].mkdirs()) {
					localMovieDirs[m].setReadable(true, false);
					localMovieDirs[m].setWritable(true, false);
					localMovieDirs[m].setExecutable(true, false);
				}
			}
		}
		try {
			qtDims = compQ.getQTDimensions(remoteImpID);
		} catch (RemoteException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		stkWidth = qtDims[0]; 
		stkHeight = qtDims[1]; 
		stkNChannels = qtDims[2];
		stkNSlices = qtDims[3];
		stkNFrames = qtDims[4];
		imp2 = new ImagePlus();
		stack = new RemoteMQTVirtualStack(stkWidth, stkHeight,
				ColorModel.getRGBdefault(), "", false, Color.black);
		((RemoteMQTVirtualStack)stack).setStretchToFitOverlay(stretchToFitOverlay);


		if ((moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")
				|| moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/RGB_"))) {
			stack.setBurnIn(false);
			for (int i=1; i<=stkNSlices*stkNFrames; i++) {
				((VirtualStack)stack).addSlice("");
			}
		} else {
			stack.setBurnIn(false);
			for (int i=1; i<=stkNChannels*stkNSlices*stkNFrames; i++) {
				((VirtualStack)stack).addSlice("");
			}
		}

		//            imp.show();
		//			imp2.setStack("fromServer:"+args[0]+""+remoteImpID+":"+serverReturnString, ((VirtualStack)stack));
		imp2.setStack(moviePathNames.length +"-movie Scene - "+ moviePathNames[0].replaceAll(".*/", "") +" etc, "+imp2.getID(), ((VirtualStack)stack));

		imp2.getCalibration().pixelDepth = ((RemoteMQTVirtualStack)stack).sliceDepth;
		imp2.getCalibration().pixelWidth = 1;
		imp2.getCalibration().pixelHeight = 1;

		//			win2 = null;
		if (!(moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")
				|| moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/RGB_"))) {
			imp2.setDimensions(stkNChannels, stkNSlices, stkNFrames);
			imp2.setOpenAsHyperStack(true);
			CompositeImage ci2 = null;
			if (eightBit  /*!(moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/DUP")
					|| moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/Projectionsof"))*/) {
				ci2 = new CompositeImage(imp2,CompositeImage.COMPOSITE);
			}
			if (true ) {
				win2 = new StackWindow(ci2==null?imp2:ci2, false) {


					@Override
					public void windowIconified(WindowEvent we) {
						try {
							compQ.resetServer();
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

					@Override
					public void windowClosing(WindowEvent we) {
						IJ.run(imp, "Stop Animation", "");

						TextRoi.setFont("Arial", win2.getImagePlus().getWidth()/20, Font.ITALIC);		
						TextRoi tr = new TextRoi(0, 0, "Contacting\nCytoSHOW\nserver...");
						tr.setStrokeColor(Color.gray);
						tr.setFillColor(Color.decode("#55ffff00"));

						win2.getImagePlus().setRoi(tr);
						tr.setImage(win2.getImagePlus());
						win2.getImagePlus().getCanvas().paintDoubleBuffered(win2.getImagePlus().getCanvas().getGraphics());

						String compQClosed = "failed";
						dejaVuIPLinkedHashMap.clear();	
						try {
							compQClosed =  (compQ.closeRemoteImp(remoteImpID));
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							compQClosed =  "failed";
						}
						if (compQClosed == "VMkilled")
							spawnStrings =null;
						super.windowClosing(we);
					}

					@Override
					public boolean close() {
						IJ.run(imp, "Stop Animation", "");

						TextRoi.setFont("Arial", win2.getImagePlus().getWidth()/20, Font.ITALIC);		
						TextRoi tr = new TextRoi(0, 0, "Contacting\nCytoSHOW\nserver...");
						tr.setStrokeColor(Color.gray);
						tr.setFillColor(Color.decode("#55ffff00"));

						win2.getImagePlus().setRoi(tr);
						tr.setImage(win2.getImagePlus());
						win2.getImagePlus().getCanvas().paintDoubleBuffered(win2.getImagePlus().getCanvas().getGraphics());

						String compQClosed = "failed";
						dejaVuIPLinkedHashMap.clear();	
						try {
							compQClosed =  (compQ.closeRemoteImp(remoteImpID));
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
							compQClosed =  "failed";
						}
						if (compQClosed == "IMPflushed")
							try {
								Naming.unbind(lookupString);
							} catch (RemoteException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (MalformedURLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							} catch (NotBoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						if (compQClosed == "VMkilled")
							spawnStrings =null;						
						return super.close();
						
						
					}
					
					
					@Override
					public void setVisible(final boolean visible) {
					  // let's handle visibility...
					  if (!visible || !isVisible()) { // have to check this condition simply because super.setVisible(true) invokes toFront if frame was already visible
					      super.setVisible(visible);
					  }
					  // ...and bring frame to the front.. in a strange and weird way
					  if (visible) {
					      int state = super.getExtendedState();
					      state &= Frame.ICONIFIED;
					      super.setExtendedState(state);
					      super.setAlwaysOnTop(true);
					      super.toFront();
					      super.requestFocus();
					  }
					}

					@Override
					public void toFront() {
					  setVisible(true);
					}

					@Override
					public void windowActivated(WindowEvent we) {
						super.windowActivated(we);
					    super.setAlwaysOnTop(false);
					}
				};
			} else {
				win2.updateImage(ci2);
				
			}

			if (ci2!=null)
				ci2.setWindow(win2);
			else
				imp2.setWindow(win2);
			if (stkNChannels == 1 && ci2!=null)
				IJ.run("Grays");

		} else {
			//        	imp2.show();
			imp2.setDimensions(stkNSlices, stkNFrames, 1);
			imp2.setOpenAsHyperStack(true);
			win2 = new StackWindow(imp2, true) {


				@Override
				public void windowIconified(WindowEvent we) {
					try {
						compQ.resetServer();
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				@Override
				public void windowClosing(WindowEvent we) {
					IJ.run(imp, "Stop Animation", "");

					TextRoi.setFont("Arial", win2.getImagePlus().getWidth()/20, Font.ITALIC);		
					TextRoi tr = new TextRoi(0, 0, "Contacting\nCytoSHOW\nserver...");
					tr.setStrokeColor(Color.gray);
					tr.setFillColor(Color.decode("#55ffff00"));

					win2.getImagePlus().setRoi(tr);
					tr.setImage(win2.getImagePlus());
					win2.getImagePlus().getCanvas().paintDoubleBuffered(win2.getImagePlus().getCanvas().getGraphics());

					String compQClosed = "failed";
					dejaVuIPLinkedHashMap.clear();	
					try {
						compQClosed =  (compQ.closeRemoteImp(remoteImpID));
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						compQClosed = "failed";
					}
					if (compQClosed == "VMkilled")
						spawnStrings =null;
					super.windowClosing(we);
				}

				@Override
				public boolean close() {
					IJ.run(imp, "Stop Animation", "");

					TextRoi.setFont("Arial", win2.getImagePlus().getWidth()/20, Font.ITALIC);		
					TextRoi tr = new TextRoi(0, 0, "Contacting\nCytoSHOW\nserver...");
					tr.setStrokeColor(Color.gray);
					tr.setFillColor(Color.decode("#55ffff00"));

					win2.getImagePlus().setRoi(tr);
					tr.setImage(win2.getImagePlus());
					win2.getImagePlus().getCanvas().paintDoubleBuffered(win2.getImagePlus().getCanvas().getGraphics());

					String compQClosed = "failed";
					dejaVuIPLinkedHashMap.clear();	
					try {
						compQClosed =  (compQ.closeRemoteImp(remoteImpID));
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						compQClosed = "failed";
					}
					if (compQClosed == "VMkilled")
						spawnStrings =null;
					return super.close();
				}
			};

			imp2.setWindow(win2);
		}

		win2.getImagePlus().setRemoteHandler(this);

		if 	(win2 != null ){

			MultiChannelController mcc = win2.getImagePlus().getMultiChannelController();
			if (mcc == null) {
				mcc = new MultiChannelController(this.getImagePlus());
				imp2.setMultiChannelController(mcc);
				//					mcc.setVisible(true);
			}
			//									else
			//					mcc.setVisible(true);

			RoiManager rm = win2.getImagePlus().getRoiManager();
			if (rm == null) {
				win2.getImagePlus().setRoiManager(new RoiManager(win2.getImagePlus(), true));
				rm = win2.getImagePlus().getRoiManager();
			}
			rm.runCommand("associate", "true");
		}

	}    


	public Compute getCompQ() {
		return compQ;
	}

	public void setCompQ(Compute compQ) {
		this.compQ = compQ;
	}

	public ImageProcessor getRemoteIP(final int qtSlice, final double jpegQuality, boolean burnIn) {

		Object qtbaosba=null;
		try {
			if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
				int channel = 1+ ((qtSlice-1) % stkNSlices);
				int slice = 1+ ((qtSlice-1) / (stkNSlices));
				
				qtbaosba = compQ.getQTPixels(remoteImpID, resolutionToGet, channel, slice, jpegQuality);


			} else {
				int channel = 1+ ((qtSlice-1) % stkNChannels);
				int slice = 1+ (((qtSlice-1)/stkNChannels) % stkNSlices);
				int frame = 1+ ((qtSlice-1) / (stkNChannels*stkNSlices));
				qtbaosba = compQ.getQTPixels(remoteImpID, channel, slice, frame, jpegQuality);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
			String[] args = new String[] {IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1]};
			String remoteEngineName = args[0]+"/HEAD";
			//			IJ.log(remoteEngineName);
			IJ.log("contacting CytoSHOW server...");
			TextRoi.setFont("Arial", win2.getImagePlus().getWidth()/20, Font.ITALIC);		
			TextRoi tr = new TextRoi(0, 0, "Contacting\nCytoSHOW\nserver...");
			tr.setStrokeColor(Color.gray);
			tr.setFillColor(Color.decode("#55ffff00"));

			win2.getImagePlus().setRoi(tr);
			tr.setImage(win2.getImagePlus());
			win2.getImagePlus().getCanvas().paintDoubleBuffered(win2.getImagePlus().getCanvas().getGraphics());

			try {
				comp = (Compute) Naming.lookup(remoteEngineName);
				compQ = (Compute) Naming.lookup(lookupString);
			} catch (MalformedURLException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			} catch (RemoteException e2) {
				if (comp==null) {
					IJ.log(lookupString + "failure to connect.2");
					if (win2 != null)
						win2.getImagePlus().killRoi();
					return null;
				}
				try {
					serverReturnString =comp.spawnNewServer(spawnStrings[0],spawnStrings[1],spawnStrings[2],spawnStrings[3]);
					lookupString = "//"+serverReturnString.replaceAll("//","").replaceAll(".*>(.*)", "$1");
				} catch (RemoteException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				try {
					compQ = (Compute) Naming.lookup(lookupString);
				} catch (MalformedURLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (RemoteException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (NotBoundException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				e2.printStackTrace();
			} catch (NotBoundException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}


			if (moviePathNames.length>0) {
				try {
					remoteImpID = compQ.setUpMovie(moviePathNames, movieSlices, Integer.parseInt(args[0].split(":")[1])+Integer.parseInt(args[1]), rcsPrX);
				} catch (NumberFormatException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (RemoteException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}

			return getRemoteIP( qtSlice,jpegQuality,burnIn);
		}
		//		System.out.println(qtbaosba.toString()+" "+qtSlice+" "+jpegQuality);

		if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
			IJ.log("contacting CytoSHOW server...");
			if (win2!=null) {
				TextRoi.setFont("Arial", win2.getImagePlus().getWidth() / 20,
						Font.ITALIC);
				TextRoi tr = new TextRoi(0, 0,
						"Contacting\nCytoSHOW\nserver...");
				tr.setStrokeColor(Color.gray);
				tr.setFillColor(Color.decode("#55ffff00"));
				win2.getImagePlus().setRoi(tr);
				tr.setImage(win2.getImagePlus());
				win2.getImagePlus()
						.getCanvas()
						.paintDoubleBuffered(
								win2.getImagePlus().getCanvas().getGraphics());
			}
		}

		ByteArrayInputStream bais = new ByteArrayInputStream((byte[]) qtbaosba);
		BufferedImage bi=null;
		try {
			bi = ImageIO.read(bais);
		} catch (IOException e) {
			e.printStackTrace();
		}
		ImageProcessor ip = new ColorProcessor(bi);
		if (!(moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW_")
				|| moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/RGB_"))) {
			ip = ip.convertToByte(false);
			eightBit = true;
		}

		if (burnIn) {
			if ((StackWindow)imp2.getWindow()!=null){
				if (jpegQuality < 100 ) { 
					burnInComplete = false;

					IJ.wait(jpegQuality==1?0:0);  //funky little setting...
					if (((RemoteMQTVirtualStack)stack).selectedSlice == ((((StackWindow)imp2.getWindow()).zSelector.getValue()-1) * stkNSlices)
							+ (((StackWindow)imp2.getWindow()).cSelector!=null
							?((StackWindow)imp2.getWindow()).cSelector.getValue()
									:1)){
						Thread thread = new Thread(new Runnable() {
							public void run() {
								RemoteMQTVSHandler.this.jpegQuality=10*jpegQuality;
								imp2.setProcessor(stack.getProcessor(((((StackWindow)imp2.getWindow()).zSelector.getValue()-1) * stkNSlices)
										+ (((StackWindow)imp2.getWindow()).cSelector!=null
										?((StackWindow)imp2.getWindow()).cSelector.getValue()
												:1)));
							}
						});
						thread.start();
						threadArrayList.add(thread);
					} else {//SLIDER STILL MOVING
						RemoteMQTVSHandler.this.resolutionToGet = 1;
						RemoteMQTVSHandler.this.jpegQuality=1;
						burnInComplete = false;
						for (Thread nextThread:threadArrayList)
							nextThread.interrupt();
					}
				} else if ((moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW"))
						&& !burnInComplete) {
					RemoteMQTVSHandler.this.jpegQuality=100;

					IJ.wait(0);  //funky little setting...
					if ( (((RemoteMQTVirtualStack)stack).selectedSlice == ((((StackWindow)imp2.getWindow()).zSelector.getValue()-1) * stkNSlices)
							+ (((StackWindow)imp2.getWindow()).cSelector!=null
							?((StackWindow)imp2.getWindow()).cSelector.getValue()
									:1)) 
									&& RemoteMQTVSHandler.this.resolutionToGet < stkNChannels) {
						if (RemoteMQTVSHandler.this.resolutionToGet == stkNChannels-1)
							burnInComplete = true;
						Thread thread = new Thread(new Runnable() {
							public void run() {
								RemoteMQTVSHandler.this.resolutionToGet++;
								ImageProcessor tip = stack.getProcessor(((((StackWindow)imp2.getWindow()).zSelector.getValue()-1) * stkNSlices)
										+ (((StackWindow)imp2.getWindow()).cSelector!=null
										?((StackWindow)imp2.getWindow()).cSelector.getValue()
												:1));
								imp2.setProcessor(tip);
							}
						});
						thread.start();
						threadArrayList.add(thread);
					} else { //SLIDER STILL MOVING
						RemoteMQTVSHandler.this.resolutionToGet = 1;
						RemoteMQTVSHandler.this.jpegQuality=1;
						burnInComplete = false;
						for (Thread nextThread:threadArrayList)
							nextThread.interrupt();
					}

				} else { //NOT EM, or allDone, or not burning in
					RemoteMQTVSHandler.this.resolutionToGet = 1;
					RemoteMQTVSHandler.this.jpegQuality=1;
					burnInComplete = false;
					for (Thread nextThread:threadArrayList)
						nextThread.interrupt();
				}
			} else { //window not yet shown
				if (jpegQuality < 100) { 
					new Thread(new Runnable() {
						public void run() {
							RemoteMQTVSHandler.this.jpegQuality=10*jpegQuality;
							imp2.setProcessor(stack.getProcessor(qtSlice));
						}
					}).start();
				}
			}
		}
		//		return ip.resize(stkWidth,stkHeight,false);
		if (win2 != null)
			win2.getImagePlus().killRoi();
		return ip;
		
	}	


	public ImagePlus getImagePlus() {
		if (win2 == null)
			return null;
		else
			return win2.getImagePlus();
	}

	public static byte[] getFileInputByteArray(String url, String portOffset, String pathlist) {
		ArrayList<String> argArrayList = new ArrayList<String>();
		argArrayList.add(url);
		argArrayList.add(portOffset);
		//		for (String path:pathlist.split(" "))
		//			argArrayList.add(path);
		RemoteMQTVSHandler rmqtvsh = new RemoteMQTVSHandler(argArrayList.toArray(new String[argArrayList.size()]));
		try {			
			if (rmqtvsh.comp==null) {
				IJ.log(lookupString + "failure to connect.3");
				return null;
			}
			IJ.log(rmqtvsh.comp.toString() + "failure to connect.4");
			return rmqtvsh.compQ.getFileInputByteArray(pathlist);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			if (rmqtvsh.comp==null) {
				IJ.log(lookupString + "failure to connect.5");
				return null;
			}
			IJ.log(rmqtvsh.comp.toString() + "failure to connect.6");
		}
		return null;
	}

	public String[] getChannelPathNames() {
		// TODO Auto-generated method stub
		if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
			String[] names = new String[stkNSlices];
			for (int n=0; n<names.length;n++){
				names[n] = this.moviePathNames[moviePathNames.length-1];
			}
			return names;
		} else
			return this.moviePathNames;
	}

	public String[] getMovieSlicesStrings() {
		return movieSlices;
	}

	public boolean isStretchToFitOverlay() {
		return stretchToFitOverlay;
	}

	public void setStretchToFitOverlay(boolean stretchToFitOverlay) {
		this.stretchToFitOverlay = stretchToFitOverlay;
	}

	public boolean isEightBit() {
		return eightBit;
	}

	public void setEightBit(boolean eightBit) {
		this.eightBit = eightBit;
	}

	public boolean isViewOverlay() {
		return viewOverlay;
	}

	public void setViewOverlay(boolean viewOverlay) {
		this.viewOverlay = viewOverlay;
	}

	public boolean isSideSideStereo() {
		return sideSideStereo;
	}

	public void setSideSideStereo(boolean sideSideStereo) {
		this.sideSideStereo = sideSideStereo;
	}

	public boolean isRedCyanStereo() {
		return redCyanStereo;
	}

	public void setRedCyanStereo(boolean redCyanStereo) {
		this.redCyanStereo = redCyanStereo;
	}

	public boolean isGrid() {
		return grid;
	}

	public void setGrid(boolean grid) {
		this.grid = grid;
	}

	public boolean isHorizontal() {
		return horizontal;
	}

	public void setHorizontal(boolean horizontal) {
		this.horizontal = horizontal;
	}


	//	public void refreshIP() {
	//		if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
	//			resolutionToGet = 3;
	//			jpegQuality = 1;
	//			burnInComplete = false;
	//			getImagePlus().setProcessor(stack.getProcessor(getImagePlus().getCurrentSlice()));
	//		}
	//	}






	public ArrayList<Thread> getThreadArrayList() {
		return threadArrayList;
	}

	public void setThreadArrayList(ArrayList<Thread> threadArrayList) {
		this.threadArrayList = threadArrayList;
	}


	public class RemoteMQTVirtualStack extends VirtualStack {
		private double[] translateX = new double[stkNChannels];
		private double[] translateY = new double[stkNChannels];
		private int[] nSlicesSingleMovie = new int[stkNChannels];
		private ImagePlus lineageMapImage;
		private int[] relativeZFrequencySingleMovie = new int[stkNChannels];
		private int[] relativeFrameRateSingleMovie = new int[stkNChannels];
		private double[] rotateAngle = new double[stkNChannels];
		private int[] shiftZPosition = new int[stkNChannels];
		private int[] shiftTPosition = new int[stkNChannels];
		private boolean[] flipSingleMovieStackVertical = new boolean[stkNChannels];
		private boolean[] flipSingleMovieStackHorizontal = new boolean[stkNChannels];
		private boolean[] flipSingleMovieStackOrder = new boolean[stkNChannels];
		private double[] scaleX = new double[stkNChannels];
		private double[] scaleY = new double[stkNChannels];
		private int[] channelLUTIndex = new int[stkNChannels];
		private int selectedSlice;
		public int maximumRelativeZFrequency =1;
		public int maximumRelativeFrameRate =1;
		private int finalWidth;
		private int finalHeight;
		private double gridAcross;
		private double gridDown;
		private double[] nmdxySingleMovie;
		private double[] nmdzSingleMovie;
		private double[] dzdxyRatio;
		private double sliceDepth;
		private String[] channelLUTName = new String[stkNChannels];
		private String sceneFileText;
		private String sceneFileName;;

		private RemoteMQTVirtualStack(int width, int height, ColorModel cm,
				String path, boolean emptyImage, Color fillColor) {
			super(width, height, cm, path, emptyImage, fillColor);
			Arrays.fill(relativeFrameRateSingleMovie, 1);
			Arrays.fill(relativeZFrequencySingleMovie, 1);
			nmdxySingleMovie = new double[moviePathNames.length];
			nmdzSingleMovie = new double[moviePathNames.length];
			dzdxyRatio = new double[moviePathNames.length];
			for (int m=0; m<moviePathNames.length;m++) {
				nmdxySingleMovie[m] =1;
				nmdzSingleMovie[m] =1;
				String[] nameChunks = moviePathNames[m].split("_");
				for (String nameChunk: nameChunks) {
					if (nameChunk.matches("z\\d+.*") ) {
						if (IJ.debugMode) IJ.log("found z setting in name = " + nameChunk );
						movieSlices[m] = nameChunk.substring(1);
						if (IJ.debugMode) IJ.log("" + movieSlices[m]);
					}
					else if (nameChunk.startsWith("nmdxy") ) {
						if (IJ.debugMode) IJ.log(""+ Double.parseDouble( nameChunk.substring(5) ));
						nmdxySingleMovie[m] = Double.parseDouble( nameChunk.substring(5) );
					}
					if (nameChunk.startsWith("nmdz") ) {
						if (IJ.debugMode) IJ.log(""+ Double.parseDouble( nameChunk.substring(4) ));
						nmdzSingleMovie[m] = Double.parseDouble( nameChunk.substring(4) );
					}
				}
				dzdxyRatio[m] = nmdzSingleMovie[m]/nmdxySingleMovie[m];
				if (sliceDepth == 0)
					sliceDepth = dzdxyRatio[m];
				else
					sliceDepth = Math.min(sliceDepth, dzdxyRatio[m]);
			}
		}

		@Override
		public ImageProcessor getProcessor( int slice) {
			this.selectedSlice = slice;
			ImageProcessor ip = null;
			if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
				if (this.flipSingleMovieStackOrder[0])
					slice = RemoteMQTVSHandler.this.getImagePlus().getNSlices()-(slice-1);
			} else {
				if (this.flipSingleMovieStackOrder[(slice-1)%stkNChannels]) {
					int base = slice-((slice-1)%(stkNChannels*stkNSlices));
					int nZ = RemoteMQTVSHandler.this.getImagePlus().getNSlices();
					int sliceLocal = (slice-1)%(stkNChannels*stkNSlices);
					int cLocal = sliceLocal%stkNChannels;
					int zLocal = (sliceLocal-cLocal)/stkNChannels;
					int flipZLocal = nZ-1-zLocal;
					int sliceFlip = base + flipZLocal*stkNChannels + cLocal;
					slice = sliceFlip;
				}
			}

			int channelBaseZero = (slice-1)%stkNChannels;

			int adjustedSlice = getAdjustedSlice(slice, channelBaseZero);

			if ((adjustedSlice )%(stkNChannels * stkNSlices) != slice%(stkNChannels * stkNSlices)+(shiftZPosition[channelBaseZero]  * stkNChannels))  {
				//				IJ.log(""+ adjustedSlice +" "+ channelBaseZero +" "+shiftZPosition[channelBaseZero] +" "+stkNChannels +" "+stkNSlices);
				if (shiftZPosition[channelBaseZero] > 0 ) {
					adjustedSlice = adjustedSlice - (stkNChannels * stkNSlices);
					if  (adjustedSlice > nSlices && adjustedSlice < nSlices + (shiftZPosition[channelBaseZero]  * stkNChannels))
						adjustedSlice = adjustedSlice - (stkNChannels * stkNSlices);
				}
				else if (shiftZPosition[channelBaseZero] < 0 ) {
					adjustedSlice = adjustedSlice + (stkNChannels * stkNSlices);
					if  (adjustedSlice <0 && adjustedSlice > (shiftZPosition[channelBaseZero]  * stkNChannels))
						adjustedSlice = adjustedSlice + (stkNChannels * stkNSlices);
				}
			}
			if (adjustedSlice <0 ) {
				if (!eightBit)
					ip = new ColorProcessor(finalWidth, finalHeight);
				else 
					ip = new ByteProcessor(finalWidth, finalHeight);

				ip.setColor(Color.black);
				ip.fill();

			} else {
				if (dejaVuIPLinkedHashMap.get(adjustedSlice) != null) {
					ip = dejaVuIPLinkedHashMap.get(adjustedSlice).duplicate();
				} else if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
					ImageWindow win = null;
					if (RemoteMQTVSHandler.this.getImagePlus() != null)
						win = RemoteMQTVSHandler.this.getImagePlus().getWindow();
					boolean burnIn = false;
					if (win != null) 
						burnIn = this.isBurnIn() && !win.running && !win.running2 && !win.running3;
					if ((new File(/*(IJ.isWindows()?"\\\\?\\":"")+*/localMovieDirs[stkNChannels-1]+"/"+adjustedSlice+".tif")).canRead()) {
						ip = (new Opener()).openImage(/*(IJ.isWindows()?"\\\\?\\":"")+*/localMovieDirs[stkNChannels-1]+"/"+adjustedSlice+".tif").getProcessor();
						dejaVuIPLinkedHashMap.put(adjustedSlice, ip.duplicate());
					}else if (this.isBurnIn()) {
						boolean cacheIt = false;
						if (burnIn) {
							cacheIt =  (resolutionToGet == stkNChannels);								
							ip = getRemoteIP(adjustedSlice, jpegQuality, true);
						} else {
							resolutionToGet = 1;
							ip = getRemoteIP(adjustedSlice, 1, false);							
						}
						if (cacheIt) {
							dejaVuIPLinkedHashMap.put(adjustedSlice, ip.duplicate());
							if ((new File(IJ.getDirectory("home"))).getUsableSpace() > 20000000L) 
								(new FileSaver(new ImagePlus("",ip))).saveAsTiff(/*(IJ.isWindows()?"\\\\?\\":"")+*/localMovieDirs[stkNChannels-1]+"/"+adjustedSlice+".tif");
						}
					} else {
						RemoteMQTVSHandler.this.resolutionToGet = stkNChannels;
						ip = getRemoteIP(adjustedSlice, 100, false);
						dejaVuIPLinkedHashMap.put(adjustedSlice, ip.duplicate());
						if ((new File(IJ.getDirectory("home"))).getUsableSpace() > 20000000L) 
							(new FileSaver(new ImagePlus("",ip))).saveAsTiff(/*(IJ.isWindows()?"\\\\?\\":"")+*/localMovieDirs[stkNChannels-1]+"/"+adjustedSlice+".tif");
					}

				}else if ((new File(/*(IJ.isWindows()?"\\\\?\\":"")+*/localMovieDirs[(adjustedSlice-1)%stkNChannels]+"/"+((int)Math.floor((adjustedSlice-1)/stkNChannels)+1)+".tif")).canRead()) {
					ip = (new Opener()).openImage(/*(IJ.isWindows()?"\\\\?\\":"")+*/localMovieDirs[(adjustedSlice-1)%stkNChannels]+"/"+((int)Math.floor((adjustedSlice-1)/stkNChannels)+1)+".tif").getProcessor();
					dejaVuIPLinkedHashMap.put(adjustedSlice, ip.duplicate());
				}else {
					ip = getRemoteIP(adjustedSlice, 100, false);
					dejaVuIPLinkedHashMap.put(adjustedSlice, ip.duplicate());
					if ((new File(IJ.getDirectory("home"))).getUsableSpace() > 20000000L) 
						(new FileSaver(new ImagePlus("",ip))).saveAsTiff(/*(IJ.isWindows()?"\\\\?\\":"")+*/localMovieDirs[(adjustedSlice-1)%stkNChannels]+"/"+((int)Math.floor((adjustedSlice-1)/stkNChannels)+1)+".tif");
				}


				if (IJ.currentMemory() > (IJ.maxMemory()/2L)) {
					Iterator<Integer> it = dejaVuIPLinkedHashMap.keySet().iterator();
					if (it != null && it.hasNext()) {
						ImageProcessor killIp = dejaVuIPLinkedHashMap.get(it.next());
						killIp = null;
						it.remove();
					}
				}
				IJ.showStatus(IJ.freeMemory() + " " + (new File(IJ.getDirectory("home"))).getTotalSpace()/((long)(1024*1024))
						+ "MB " + (new File(IJ.getDirectory("home"))).getFreeSpace()/((long)(1024*1024))
						+ "MB " + (new File(IJ.getDirectory("home"))).getUsableSpace()/((long)(1024*1024))
						+ "MB ");

				if (stretchToFitOverlay) {
					ip = ip.resize(stkWidth, stkHeight, false);
				}



				if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
					if (flipSingleMovieStackVertical[0])
						ip.flipVertical();
					if (flipSingleMovieStackHorizontal[0])
						ip.flipHorizontal();
					ip.setBackgroundValue(0);
					ip.rotate(rotateAngle[0]);
					ip.scale((1+(scaleX[0]/100)), 
							(1+(scaleY[0]/100)));
					ip.translate(translateX[0], -translateY[0]);
				} else {
					if (flipSingleMovieStackVertical[(adjustedSlice-1)%stkNChannels])
						ip.flipVertical();
					if (flipSingleMovieStackHorizontal[(adjustedSlice-1)%stkNChannels])
						ip.flipHorizontal();
					ip.setBackgroundValue(0);
					ip.rotate(rotateAngle[(adjustedSlice-1)%stkNChannels]);
					ip.scale((1+(scaleX[(adjustedSlice-1)%stkNChannels]/100)), 
							(1+(scaleY[(adjustedSlice-1)%stkNChannels]/100)));
					ip.translate(translateX[(adjustedSlice-1)%stkNChannels], -translateY[(adjustedSlice-1)%stkNChannels]);
				}
			}

			if (viewOverlay) {
				finalWidth = stkWidth;
				finalHeight = stkHeight;

			} else if (sideSideStereo){
				int numPanels = stkNChannels;
				if (grid) {
					gridAcross = numPanels<8?2:4;
					gridDown = numPanels/gridAcross;
				}
				if (horizontal || !horizontal) {
					if (grid) {
						finalWidth = ((int)gridAcross) * stkWidth;
						finalHeight = ((int)gridDown) * stkHeight;
					} else {
						finalWidth = stkWidth * 2;
						finalHeight = stkHeight*numPanels/2;
					}
					//				} else {
					//					if (grid) {
					//						finalHeight = ((int)gridAcross) * stkWidth;
					//						finalWidth = ((int)gridDown) * stkHeight;
					//					} else {
					//						finalWidth = stkWidth;
					//						finalHeight = stkHeight * stkNChannels;
					//					}
				}

			} else {
				int numPanels = stkNChannels;
				if (redCyanStereo)
					numPanels = stkNChannels/2;

				if (grid) {
					if ( numPanels == 3 ) numPanels = 4;
					if ( numPanels == 5 ) numPanels = 6;
					if ( numPanels == 7 ) numPanels = 9;
					if ( numPanels == 8 ) numPanels = 9;
					if ( numPanels == 10 ) numPanels = 12;
					if ( numPanels == 11 ) numPanels = 12;
					if ( numPanels == 13 ) numPanels = 16;
					if ( numPanels == 14 ) numPanels = 16;
					if ( numPanels == 15 ) numPanels = 16;
					if ( numPanels == 17 ) numPanels = 20;
					if ( numPanels == 18 ) numPanels = 20;
					if ( numPanels == 19 ) numPanels = 20;
					gridAcross = Math.ceil(Math.sqrt(numPanels));
					gridDown = numPanels/gridAcross;
				}
				if (horizontal) {
					if (grid) {
						finalWidth = ((int)gridAcross) * stkWidth;
						finalHeight = ((int)gridDown) * stkHeight;
					} else {
						finalWidth = stkWidth * numPanels;
						finalHeight = stkHeight;
					}
				} else {
					if (grid) {
						finalHeight = ((int)gridAcross) * stkWidth;
						finalWidth = ((int)gridDown) * stkHeight;
					} else {
						finalWidth = stkWidth;
						finalHeight = stkHeight * numPanels;
					}
				}
			}

			ImageProcessor finalIP = null;
			

			
			if (ip instanceof ColorProcessor)
				finalIP = new ColorProcessor(finalWidth, finalHeight);
			else 
				finalIP = new ByteProcessor(finalWidth, finalHeight);

			finalIP.setColor(Color.black);
			finalIP.fill();
			int pane = viewOverlay?0:(slice-1)%stkNChannels;
			if (redCyanStereo)
				pane = ((int)Math.floor(pane/2));
			int insertX = 0;
			int insertY = 0;
			if (!viewOverlay) {
				if (grid || sideSideStereo) {
					insertX = pane*stkWidth%finalWidth;
					insertY = ((int)Math.floor(pane*stkWidth/finalWidth))*stkHeight;
				} else {
					if (horizontal) {
						insertX = pane*stkWidth;
						insertY = 0;
					} else {
						insertX = 0;
						insertY = pane*stkWidth;
					}
				}
			}

			finalIP.insert(ip, insertX, insertY);

			return finalIP;
		}

		public int getAdjustedSlice(int slice, int channelBaseZero) {
			int adjustedSlice = (int)Math.floor((int)Math.floor((int)Math.floor(((slice-1)
					/(stkNChannels))
					/(stkNSlices))
					* relativeFrameRateSingleMovie[channelBaseZero])/this.maximumRelativeFrameRate )
					* (stkNChannels*stkNSlices)

					+ (int)Math.floor(((int)Math.floor(((slice-1) 
							%(stkNChannels*stkNSlices))
							/(stkNChannels))
							* relativeZFrequencySingleMovie[channelBaseZero])/this.maximumRelativeZFrequency )
							* (stkNChannels)

							+ channelBaseZero + 1

							+ (shiftTPosition[channelBaseZero] * stkNChannels * stkNSlices)

							+ (shiftZPosition[channelBaseZero] * stkNChannels);
			return adjustedSlice;
		}

		@Override
		public int getSize() {
			if (!(moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW"))) {
				return stkNChannels*stkNSlices*stkNFrames;
			} else {
				return stkNSlices*stkNFrames;
			}
		}

		public int getShiftSingleMovieZPosition(int channel) {
			return shiftZPosition[channel];
		}

		public void setShiftSingleMovieZPosition(int channelBaseZero, int shiftSingleMovieZPosition) {
			this.shiftZPosition[channelBaseZero] = shiftSingleMovieZPosition;
		}

		public int getShiftSingleMovieTPosition(int channel) {
			return shiftTPosition[channel];
		}

		public void setShiftSingleMovieTPosition(int channelBaseZero, int shiftSingleMovieTPosition) {
			this.shiftTPosition[channelBaseZero] = shiftSingleMovieTPosition;
		}

		public void flipSingleMovieVertical(int channelBaseZero) {
			flipSingleMovieStackVertical[channelBaseZero] = !flipSingleMovieStackVertical[channelBaseZero];		
		}

		public boolean getFlipSingleMovieStackVertical(int channelBaseZero) {
			return flipSingleMovieStackVertical[channelBaseZero];
		}

		public void flipSingleMovieHorizontal(int channelBaseZero) {
			flipSingleMovieStackHorizontal[channelBaseZero] = !flipSingleMovieStackHorizontal[channelBaseZero];		
		}

		public boolean getFlipSingleMovieStackHorizontal(int channelBaseZero) {
			return flipSingleMovieStackHorizontal[channelBaseZero];
		}

		public void flipSingleMovieZaxis(int channelBaseZero) {		
			flipSingleMovieStackOrder[channelBaseZero] = !flipSingleMovieStackOrder[channelBaseZero];		
		}

		public boolean getFlipSingleMovieStackOrder(int channelBaseZero) {
			return flipSingleMovieStackOrder[channelBaseZero];
		}

		public void setSingleMovieScale(int channelBaseZero, double scaleX, double scaleY) {
			this.scaleX[channelBaseZero] = scaleX;
			this.scaleY[channelBaseZero] = scaleY;
		}

		public double getSingleMovieScaleX(int channel) {
			return scaleX[channel];
		}

		public double getSingleMovieScaleY(int channel) {
			return scaleY[channel];
		}

		public void  setSingleMovieRotationAngle(int channelBaseZero, double rotateAngle) {
			this.rotateAngle[channelBaseZero] = rotateAngle;
		}

		public double getSingleMovieRotateAngle(int channel) {
			return rotateAngle[channel];
		}

		public void  setSingleMovieTranslate(int channelBaseZero, double translateX, double translateY) {
			this.translateX[channelBaseZero] = translateX;
			this.translateY[channelBaseZero] = translateY;
		}

		public double getSingleMovieTranslateX(int channel) {
			return translateX[channel];
		}

		public double getSingleMovieTranslateY(int channel) {
			return translateY[channel];
		}

		public Boolean getIsEightBit() {
			return eightBit;
		}

		public Boolean getIsVirtualStack() {
			return true;
		}

		public Boolean getIsMultipleMovies() {
			return true;
		}

		public Boolean getIsHyperStack() {
			return true;
		}

		public Boolean isStretchToFitOverlay() {
			return stretchToFitOverlay;
		}

		public void setStretchToFitOverlay(boolean stretchToFitOverlay) {
			RemoteMQTVSHandler.this.stretchToFitOverlay = stretchToFitOverlay;
		}

		public Boolean getIsViewInOverlay() {
			return viewOverlay;
		}

		public Boolean getIsHorizontalMontage() {
			return horizontal;
		}

		public Boolean getIsSideSideStereo() {
			return sideSideStereo;
		}

		public Boolean getIsRedCyanStereo() {
			return redCyanStereo;
		}

		public Boolean getIsGrid() {
			return grid;
		}

		public boolean isEightBit() {
			return eightBit;
		}

		public void setEightBit(boolean eightBit) {
			RemoteMQTVSHandler.this.eightBit = eightBit;
		}

		public boolean isViewOverlay() {
			return viewOverlay;
		}

		public void setViewOverlay(boolean viewOverlay) {
			RemoteMQTVSHandler.this.viewOverlay = viewOverlay;
		}

		public boolean isSideSideStereo() {
			return sideSideStereo;
		}

		public void setSideSideStereo(boolean sideSideStereo) {
			RemoteMQTVSHandler.this.sideSideStereo = sideSideStereo;
		}

		public boolean isRedCyanStereo() {
			return redCyanStereo;
		}

		public void setRedCyanStereo(boolean redCyanStereo) {
			RemoteMQTVSHandler.this.redCyanStereo = redCyanStereo;
		}

		public int[] getNSlicesSingleMovie() {
			return nSlicesSingleMovie;
		}

		public void setNSlicesSingleMovie(int[] slicesSingleMovie) {
			nSlicesSingleMovie = slicesSingleMovie;
		}

		public ImagePlus getLineageMapImage() {
			return lineageMapImage;
		}

		public void setLineageMapImage(ImagePlus lineageMapImage) {
			this.lineageMapImage = lineageMapImage;

		}

		public void setRelativeZFrequencySingleMovie(
				int[] relativeZFrequencySingleMovie) {
			this.relativeZFrequencySingleMovie = relativeZFrequencySingleMovie;
		}

		public int[] getRelativeZFrequencySingleMovie() {
			return relativeZFrequencySingleMovie;
		}

		public void setRelativeFrameRateSingleMovie(
				int[] relativeFrameRateSingleMovie) {
			this.relativeFrameRateSingleMovie = relativeFrameRateSingleMovie;
		}

		public int[] getRelativeFrameRateSingleMovie() {
			return relativeFrameRateSingleMovie;
		}

		public void setChannelLUTName(int channel, String string) {
			this.channelLUTName[channel]= string;	}

		public void setChannelLUTIndex(int channel, int channelLUTIndex) {
			this.channelLUTIndex[channel] = channelLUTIndex;
		}

		public int getChannelLUTIndex(int j) {
			// TODO Auto-generated method stub
			return this.channelLUTIndex [j];
		}

		public int getRelativeZFrequency(int j) {
			// TODO Auto-generated method stub
			return relativeZFrequencySingleMovie[j];
		}

		public int getRelativeFrameRate(int j) {
			// TODO Auto-generated method stub
			return relativeFrameRateSingleMovie[j];
		}

		public void setRelativeFrameRate(int channelBaseZero, int relativeFrameRate) {

			this.getRelativeFrameRateSingleMovie()[channelBaseZero] = relativeFrameRate;
			this.maximumRelativeFrameRate = 0;   
			for (int c=0; c<getRelativeFrameRateSingleMovie().length; c++) {
				if (this.getRelativeFrameRateSingleMovie()[c] > this.maximumRelativeFrameRate) {
					this.maximumRelativeFrameRate = this.getRelativeFrameRateSingleMovie()[c];   // new maximum
				}
			}
		}

		public void setRelativeZFrequency(int channelBaseZero, int relativeZFrequency) {

			this.getRelativeZFrequencySingleMovie()[channelBaseZero] = relativeZFrequency;
			this.maximumRelativeZFrequency = this.getRelativeZFrequencySingleMovie()[0];   // start with the first value
			for (int i=1; i<getRelativeZFrequencySingleMovie().length; i++) {
				if (this.getRelativeZFrequencySingleMovie()[i] > this.maximumRelativeZFrequency) {
					this.maximumRelativeZFrequency = this.getRelativeZFrequencySingleMovie()[i];   // new maximum
				}
			}
		}

		public double getScaleX(int channelBaseZero) {
			return scaleX[channelBaseZero];
		}

		public void setScaleX(int channelBaseZero, double scaleX) {
			this.scaleX[channelBaseZero] = scaleX;
		}

		public double getScaleY(int channelBaseZero) {
			return scaleY[channelBaseZero];
		}

		public void setScaleY(int channelBaseZero, double scaleY) {
			this.scaleY[channelBaseZero] = scaleY;
		}

		public void setTranslateY(int i, double translateY) {
			this.translateY[i] = translateY;
		}

		public void setTranslateX(int i, double translateX) {
			this.translateX[i] = translateX;
		}

		public void setGrid(boolean grid) {
			RemoteMQTVSHandler.this.grid = grid;
		}

		public void setHorizontal(boolean horizontal) {
			RemoteMQTVSHandler.this.horizontal = horizontal;
		}

		public void setSceneFileText(String sceneFileText) {
			this.sceneFileText = sceneFileText;
		}

		public void setSceneFileName(String sceneFileName) {
			this.sceneFileName = sceneFileName;
		}

		public String getSceneFileText() {
			return sceneFileText;
		}

		public String getSceneFileName() {
			return sceneFileName;
		}

	}


	public boolean isReady() {
		if (moviePathNames[0].substring(moviePathNames[0].lastIndexOf("/")).startsWith("/SW")) {
			for(Thread t:threadArrayList) {
				if(t.isAlive())
					return false;
			}
			((StackWindow)imp2.getWindow()).zSelector.setValue(1+(((RemoteMQTVirtualStack)stack).selectedSlice 
					- (((StackWindow)imp2.getWindow()).cSelector!=null
					?((StackWindow)imp2.getWindow()).cSelector.getValue()
							:1))/stkNSlices) ;
			imp2.updateAndRepaintWindow();
		}
		return true;
	}

	public Thread getReloadThread() {
		return reloadThread;
	}

	public void setReloadThread(Thread reloadThread) {
		this.reloadThread = reloadThread;
	}

	public void setRotation(int i) {

		rotation = i;
	}

}

