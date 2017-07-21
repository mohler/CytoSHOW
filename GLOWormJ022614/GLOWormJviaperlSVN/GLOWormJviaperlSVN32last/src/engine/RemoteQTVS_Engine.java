package engine;

import ij.IJ;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.StackWindow;
import ij.plugin.BrowserLauncher;
import ij.plugin.FileInfoVirtualStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Scanner;
import java.util.zip.ZipInputStream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.vcell.gloworm.MultiQTVirtualStack;
import org.vcell.gloworm.QTVirtualStack;

import client.RemoteMQTVSHandler;
import quicktime.io.QTFile;
import quicktime.app.view.MoviePlayer;
import quicktime.app.view.QTImageProducer;
import quicktime.io.OpenMovieFile;
import quicktime.io.QTFile;
import quicktime.qd.QDDimension;
import quicktime.std.StdQTConstants;
import quicktime.std.movies.Movie;
import quicktime.std.movies.TimeInfo;
import quicktime.std.movies.Track;
import quicktime.QTException;
import quicktime.QTSession;
import sun.misc.IOUtils;
import compute.Compute;

public class RemoteQTVS_Engine extends UnicastRemoteObject implements Compute {



	/**
	 * 
	 */
	private static final long serialVersionUID = -287720719840925938L;
	private Hashtable<Integer,ImagePlus> movieTable = new Hashtable<Integer,ImagePlus>();
	private long lullStartTime;
	private int port;
	private String sceneFileName = null;
	private String sceneFileText = null;
	private static String sprName;
	private static Compute engine;
	private static String portString;
	

	public RemoteQTVS_Engine(int port) throws RemoteException {
        super(port);
        this.port = port;
    }

    public static void main(String[] args) {
    	String ipAddress = "155.37.253.202"; //Local IP address 
    	System.setProperty("java.rmi.server.hostname",ipAddress);        
    	
    	Registry registry;
        portString = args[0];
        if (portString==null)
        	portString = ipAddress+":80/HEAD";
        String[] s= portString.split("[/:]");
        int port = Integer.parseInt(s[s.length-2]);
        		
		try {
			registry = LocateRegistry.createRegistry(port);
		} catch (RemoteException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		try {
            registry = LocateRegistry.getRegistry(port);
            if (registry == null) {
            	registry = LocateRegistry.createRegistry(port);
            }
		} catch (RemoteException e1) {
			e1.printStackTrace();
		}
        try {
            sprName = "//"+portString.replaceAll("//","").replaceAll(".*>(.*)", "$1");
            engine = new RemoteQTVS_Engine(port);
            try {
            	Naming.rebind(sprName, engine);
            } catch (UnexpectedException ue) {
            	Naming.rebind(sprName, engine);
            } catch (NoSuchObjectException nbe) {
            	Naming.rebind(sprName, engine);
            }
            System.out.println("QTVSEngine bound on port "+portString);
        } catch (Exception e) {
            System.out.println("QTVSEngine exception:");
//            e.printStackTrace();
        }

    }

	public int setUpMovie(String[] names, String[] slices, int port, boolean rcsPrx)  throws RemoteException {
		if (!QTSession.isInitialized())
			try {
				QTSession.open();
			} catch (QTException e) {
				e.printStackTrace();
			}
		ImagePlus impLocal = null;
		VirtualStack vstack = null;
		String[] nameChunks = names[0].split("_");
		int maxSlicesSingleMovie = 0;
		boolean metaDataNaming = (names[0].contains("_au") 
				&& names[0].contains("_date") 
				&& names[0].contains("_imgsys"));
		if (metaDataNaming) {
			for (String nameChunk: nameChunks) {
				if (nameChunk.startsWith("z") && slices.length == 1) {
					 maxSlicesSingleMovie = Integer.parseInt( nameChunk.substring(1) );
				}
				else if (nameChunk.startsWith("nmdxy") ) {
//					if (IJ.debugMode) IJ.log(""+ Double.parseDouble( nameChunk.substring(5) ));
//					nmdxySingleMovie[sqtf] = Double.parseDouble( nameChunk.substring(5) );
				}
				if (nameChunk.startsWith("nmdz") ) {
//					if (IJ.debugMode) IJ.log(""+ Double.parseDouble( nameChunk.substring(4) ));
//					nmdzSingleMovie[sqtf] = Integer.parseInt( nameChunk.substring(4) );
				}
			}
		}
		QTFile[] mqtf = new QTFile[names.length];
		int[] movieSlices =  new int[names.length];
		for (int f=0;f<mqtf.length;f++) {
			mqtf[f] = new QTFile(names[f]);
			movieSlices[f]= Integer.parseInt(slices[f]);
		}
		try {
            if (names[0].substring(names[0].lastIndexOf("/")).startsWith("/SW")
             		|| names[0].substring(names[0].lastIndexOf("/")).startsWith("/RGB")
//               	|| names[0].substring(names[0].lastIndexOf("/")).startsWith("/DUP")
//            		|| names[0].substring(names[0].lastIndexOf("/")).startsWith("/Projectionsof")
            		) {
            	vstack = new MultiQTVirtualStack(mqtf, new ArrayList<String>(),new ArrayList<String>(),movieSlices, false, impLocal, true, true, false, false, false, false, rcsPrx, sceneFileName, sceneFileText);
            } else {
            	vstack = new MultiQTVirtualStack(mqtf, new ArrayList<String>(),new ArrayList<String>(),movieSlices, true, impLocal, false, true, false, false, false, false, rcsPrx, sceneFileName, sceneFileText);
            }
		} catch (IOException e) {
			e.printStackTrace();
		}

		impLocal = ((MultiQTVirtualStack) vstack).imp;
		if  (maxSlicesSingleMovie == 0) {
			Arrays.sort(movieSlices);
			maxSlicesSingleMovie = movieSlices[movieSlices.length-1];
			
		}
		impLocal.setDimensions(mqtf.length, maxSlicesSingleMovie, impLocal.getImageStackSize()/(maxSlicesSingleMovie*mqtf.length));
		movieTable.put(impLocal.getID(), impLocal);
        impLocal.setOpenAsHyperStack(true);

		startLullClock();
		return impLocal.getID();
	}


	public Object getQTPixels(int impID, int channel, int slice, int frame, double jpegQuality) throws RemoteException {

		restartLullClock();
		ImagePlus qtimp = null; 
		while (qtimp == null)
			qtimp = movieTable.get(impID);
		
		qtimp.setPosition(channel, slice, frame);
		BufferedImage bi = null;
		ColorProcessor cp = ((ColorProcessor) ((MultiQTVirtualStack)qtimp.getStack()).getUnmodifiedProcessor(qtimp.getChannel()).convertToRGB());
		if (jpegQuality<100)
			bi = cp.resize(qtimp.getWidth()/5, qtimp.getHeight()/5, false).getBufferedImage();
		else
			bi = cp.getBufferedImage();
				
		Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("jpeg");
		ImageWriter writer = (ImageWriter)iter.next();
		ByteArrayOutputStream baos = new ByteArrayOutputStream(qtimp.getBytesPerPixel()*qtimp.getWidth()*qtimp.getHeight());
		ImageOutputStream ios = null;
		try {
			ios = ImageIO.createImageOutputStream(baos);
		} catch (IOException e) {
			e.printStackTrace();
		}
		writer.setOutput(ios);
		
		ImageWriteParam param = writer.getDefaultWriteParam();
		param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		int quality = (int)jpegQuality;
		param.setCompressionQuality(quality/100f);
		if (quality == 100)
			param.setSourceSubsampling(1, 1, 0, 0);
		IIOImage iioImage = new IIOImage(bi, null, null);
		try {
			writer.write(null, iioImage, param);
			ios.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		writer.dispose();

		return baos.toByteArray();
				
	}

	public int[] getQTDimensions(int remoteImpID) throws RemoteException {
		return movieTable.get(remoteImpID).getDimensions();
	}
	
	public BigDecimal doPi(int numdig) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public String closeRemoteImp(int remoteImpID) {
		ImagePlus imp = movieTable.get(remoteImpID);
		movieTable.remove(remoteImpID);
		if (imp != null) {
			if (imp.getWindow()!=null) {
				imp.getWindow().close();
			}
			imp.flush();
		}
		try {
			if (movieTable.size() <1 && port != 80) {
				UnicastRemoteObject.unexportObject(this, true);
				System.exit(0);
				return "VMkilled";
			}
		} catch (NoSuchObjectException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "IMPflushed";
	}

	public String spawnNewServer(String macID, String clientIP, String serverIP, String serverPort) throws RemoteException {
		try {
			ProcessBuilder jvm = null;
			Process newServerProcess = null;
			int attempts =0;
			String returnString = "";
			String line = "";			
			while(!line.contains("QTVSEngine bound on port") && attempts<3) {
				serverPort = "" + (8085 +((int)(Math.random()*1000)));
				jvm = new ProcessBuilder("java","-d32", "-Xmx1000M", "-Xdock:name=\"CytoSHOW "+macID+":"+RemoteServer.getClientHost()+">"+serverPort+"\"", 
						"-jar", "/Users/wmohler/Documents/RemoteQTVS_jpgEngine.jar", macID+":"+RemoteServer.getClientHost()+">"+serverIP+":"+serverPort+"/QTVS");
				attempts++;
				jvm.redirectErrorStream(true);
				newServerProcess = jvm.start();
				BufferedReader br = new BufferedReader(new InputStreamReader(newServerProcess.getInputStream()));
				StringBuilder builder = new StringBuilder();
				line = br.readLine();
			} 
			if (line.contains("QTVSEngine bound on port")) {
				returnString = macID+":"+RemoteServer.getClientHost()+">"+serverIP+":"+serverPort+"/QTVS";
			} else {
				serverPort = "80";
//				jvm = new ProcessBuilder("java","-d32", "-Xmx500M", "-Xdock:name=\"CytoSHOW "+macID+":"+RemoteServer.getClientHost()+">"+serverPort+"\"", 
//						"-jar", "/Users/wmohler/Documents/RemoteQTVS_jpgEngine.jar", macID+":"+RemoteServer.getClientHost()+">"+serverIP+":"+serverPort+"/HEAD");				
				returnString = macID+":"+RemoteServer.getClientHost()+">"+serverIP+":"+serverPort+"/HEAD";
//				jvm.redirectErrorStream(true);
//				newServerProcess = jvm.start();
			}
			String processInfo = "";
		    IJ.wait(2000);

			return returnString;

		} catch (UnknownHostException e) {
			e.printStackTrace();
			return "UnknownHostException: "+macID+":"+clientIP+">"+serverIP+":"+serverPort+"/HEAD";
		} catch (ServerNotActiveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "ServerNotActiveException: "+macID+":"+clientIP+">"+serverIP+":"+serverPort+"/HEAD";
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "IOException: " + macID+":"+clientIP+">"+serverIP+":"+serverPort+"/HEAD";
		}
	}

	public byte[] getFileInputByteArray(String path) throws RemoteException {
		try {
			if (!(new File(path)).canRead())
					return null;
			InputStream input = new FileInputStream(path); 
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024*1024];

			try {
				for (int length = 0; (length = input.read(buffer)) > 0;) {
				    output.write(buffer, 0, length);
				}
				input.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			byte[] bytes = output.toByteArray(); // Pass that instead to RMI response.
			return bytes;
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public void restartLullClock() throws RemoteException {
		lullStartTime = System.currentTimeMillis();
	}
	
	public boolean startLullClock() throws RemoteException {
		lullStartTime = System.currentTimeMillis();
		(new Thread(new Runnable() {
			public void run() {
				while(lullStartTime + 300000 > System.currentTimeMillis()) {
					IJ.wait(1000);
				}
				if (RemoteQTVS_Engine.this.port == 80) {
			        // Unregister ourself
			        try {
						Naming.unbind(sprName);
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

			        // Unexport; this will also remove us from the RMI runtime
			        try {
						UnicastRemoteObject.unexportObject(RemoteQTVS_Engine.this, true);
					} catch (NoSuchObjectException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					for (ImagePlus imp:movieTable.values()) {
						if (imp.getWindow()!=null) {
							imp.getWindow().close();
						}
						imp.flush();
						imp = null;
					}
					movieTable.clear();

			        try {
			            try {
			            	Naming.rebind(sprName, engine);
			            } catch (UnexpectedException ue) {
			            	Naming.rebind(sprName, engine);
			            } catch (NoSuchObjectException nbe) {
			            	Naming.rebind(sprName, engine);
			            }
			            System.out.println("QTVSEngine bound on port "+portString);
			        } catch (Exception e) {
			            System.out.println("QTVSEngine exception:");
//			            e.printStackTrace();
			        }

				} else {
					try {
						UnicastRemoteObject.unexportObject(RemoteQTVS_Engine.this, true);
					} catch (NoSuchObjectException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.exit(0);
				}
			}
		})).start();
		return true;
	}

	public boolean resetServer() throws RemoteException {
		if (RemoteQTVS_Engine.this.port == 80) {
//			for (ImagePlus imp:movieTable.values()) {
//				if (imp.getWindow()!=null) {
//					imp.getWindow().close();
//				}
//				imp.flush();
//				imp = null;
//			}
//			movieTable.clear();
		} else {
			UnicastRemoteObject.unexportObject(this, true);
			System.exit(0);
		}
		return true;
	}

	public String[] getOtherViewNames(String subname)  throws RemoteException {
		String[] fileList = (new File("/Volumes/GLOWORM_DATA/")).list();
		ArrayList<String> matchedArrayList = new ArrayList<String>();
		for (String fileName:fileList) {
			if (fileName.matches(".*mov") || fileName.matches(".*scene.scn")) {
				String prefix = subname.split(" ")[0];
				String midfix = subname.split(" ")[1];
				String suffix = subname.split(" ")[2];
				if(fileName.replaceAll("\\+", "_").matches(prefix+".*"+midfix+".*"/*+suffix*/)) {
					matchedArrayList.add(fileName);
				}
			}
		}
		return matchedArrayList.toArray(new String[matchedArrayList.size()]);
//		return new String[]{prefix+midfix+suffix};
	}

	public byte[] downloadFileByteArray(String fileName) throws RemoteException {
		try
		{
			File file=new File(fileName);
			//Defines buffer in which the file will be read
			byte[] buffer=new byte[(int)file.length()];
			BufferedInputStream inputFileStream=new BufferedInputStream( new FileInputStream(fileName));
			//Reads the file into buffer
			inputFileStream.read(buffer,0,buffer.length);
			inputFileStream.close();
			return(buffer);
		}
		catch(Exception e)
		{
			System.out.println("FileImpl:"+e.getMessage());
			e.printStackTrace();
			return(null);                    
		}
	}

	public byte[] downloadFileChunkByteArray(String filePath, int chunkSize, int iteration) throws RemoteException {
		try
		{
			File file=new File(filePath);
			//Defines buffer in which the file will be read
			int nextChunkSize = (int) (chunkSize < file.length()-iteration*chunkSize?chunkSize:file.length()%chunkSize);
			byte[] buffer=new byte[nextChunkSize];
			BufferedInputStream inputFileStream=new BufferedInputStream( new FileInputStream(filePath));
			//Reads the file into buffer
			inputFileStream.skip(iteration*chunkSize);
			inputFileStream.read(buffer,0,nextChunkSize);
			inputFileStream.close();
			return(buffer);
		}
		catch(Exception e)
		{
			System.out.println("FileImpl:"+e.getMessage());
			e.printStackTrace();
			return(null);                    
		}
	}

	public String[] getFiles(String path) throws RemoteException {
		//Folder name in which the files should be stored
		String dirname=path;
		File serverDir=new File(dirname);
		String[] file=serverDir.list();
		return file;
	}

	public long saveUploadFile(byte[] uploadBytes, String path) {
		File file=new File("/Users/glowormguest/Public/DropBox/WormguidesUploads/"+path);
		file.getParentFile().mkdirs();
		BufferedOutputStream outputFileStream = null;
		try {
			outputFileStream = new BufferedOutputStream(new FileOutputStream(file.getAbsolutePath(), true));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outputFileStream.write(uploadBytes,0,uploadBytes.length);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outputFileStream.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			outputFileStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return file.length();
	}

	public void renameUploadFile(String tempName, String permName) throws RemoteException {
		File tempFile = new File("/Users/glowormguest/Public/DropBox/WormguidesUploads/"+tempName);
		File permFile = new File("/Users/glowormguest/Public/DropBox/WormguidesUploads/"+permName);
		tempFile.renameTo(permFile);
	}


}
