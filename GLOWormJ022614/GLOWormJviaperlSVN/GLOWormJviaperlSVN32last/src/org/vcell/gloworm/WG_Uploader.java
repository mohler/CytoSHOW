package org.vcell.gloworm;

import ij.IJ;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;
import ij.text.TextWindow;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;
import javax.swing.JFrame;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class WG_Uploader implements PlugIn {
	
	private ProcessBuilder jvm;
	private  Process newUploadProcess;
	
	public ProcessBuilder getJvm() {
		return jvm;
	}

	public void run(String arg) {
		if (arg == null || arg == "")
			arg = (new DirectoryChooser("Upload Folder Contents")).getDirectory();
		spawnNewUploadProcess(arg);
		
	}

	public WG_Uploader() {
		
	}
	
	public WG_Uploader(String masterPath) {
		if (masterPath == null)
			return;

		TextWindow tw = new TextWindow("WG_upload","","",400,80) {
			 public void close() {
				 if (!IJ.showMessageWithCancel("Finish this WG_upload job??", "Click Cancel to end this upload. Click OK to continue uploading.")) {
					 newUploadProcess = null;
					 System.exit(0);
				 }
			 }
		};
		tw.setVisible(true);
		tw.setExtendedState(JFrame.MAXIMIZED_BOTH);
		tw.setExtendedState(JFrame.ICONIFIED);
		ArrayList<String> iterativeDirPaths = new ArrayList<String>();
		iterativeDirPaths.add(masterPath);
		int increment = 0;
		int alSize = iterativeDirPaths.size();
		while (increment < alSize) {
			for (int i=increment;i<alSize;i++) {
				String iPath = iterativeDirPaths.get(i);
				String[] localDirFileNames = (new File(iPath)).list();
				increment++;
				for (String fileName:localDirFileNames) {
					if ((new File(iPath+fileName)).isDirectory()) {
						if (!iterativeDirPaths.contains(iPath+fileName+File.separator))
							iterativeDirPaths.add(iPath+fileName+File.separator);
					}
				}
			}
			alSize = iterativeDirPaths.size();
		}
		String uniqueClientIdentifier;
		try {
			uniqueClientIdentifier = InetAddress.getLocalHost().getHostName() +"_"+ GetNetworkAddress.GetAddress("mac");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			uniqueClientIdentifier = GetNetworkAddress.GetAddress("mac");
		}

		FTPClient ftpc = new FTPClient();
		ftpc.setBufferSize(1024000);
		try {
			ftpc.connect("155.37.253.201");
			int reply = ftpc.getReplyCode();

			if(!FTPReply.isPositiveCompletion(reply)) {
				ftpc.disconnect();
				IJ.log("FTP server refused connection.");
			} else {
//				uniqueClientIdentifier = ftpc.getLocalAddress().toString();

				ftpc.enterLocalPassiveMode();
				ftpc.login("glowormguest", "GLOWorm");
				ftpc.makeDirectory("WormguidesUploads");
				ftpc.changeWorkingDirectory("/WormguidesUploads");

				ftpc.makeDirectory("/WormguidesUploads/"+uniqueClientIdentifier);
				for (String path:iterativeDirPaths) {
					ftpc.changeWorkingDirectory("/WormguidesUploads/"+uniqueClientIdentifier);
					String[] pathChunks = path.replace(":","").split("\\"+File.separator);
					for (String chunk:pathChunks) {
						if (!chunk.equals("")) {
							ftpc.makeDirectory(chunk);
							ftpc.changeWorkingDirectory(chunk);
						}
					}
					String[] localDirFileNames = (new File(path)).list();
					String[] remoteFileNames = ftpc.listNames();
					for (String fileName:localDirFileNames) {
						boolean alreadyDone= false;
						File file = new File(path +File.separator +fileName);
						Date fd = new Date(file.lastModified());
						String dateTouchString = 20
								+ IJ.pad(fd.getYear()-100, 2) 
								+ IJ.pad(fd.getMonth()+1, 2)
								+ IJ.pad(fd.getDate(), 2)
								+ IJ.pad(fd.getHours(), 2)
								+ IJ.pad(fd.getMinutes(), 2)
								+ "."
								+ IJ.pad(fd.getSeconds(), 2);
						if (remoteFileNames != null) {
							for (String remoteFileName:remoteFileNames) {
								if (fileName.equals(remoteFileName)
										|| remoteFileName.equals(fileName+"_"+dateTouchString)) {
									alreadyDone = true;
									IJ.append((new Date()).toString()+" "+path+File.separator+fileName+/*"_"+dateTouchString+*/" already backed up.", IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+"WG_UploadLog.log");
									tw.append((new Date()).toString()+" "+path+File.separator+fileName+/*"_"+dateTouchString+*/" already backed up.");
									break;
								}
							}
						}
						if (!file.isDirectory() && !alreadyDone) {
							FileInputStream fis = new FileInputStream(path +File.separator +fileName);
							ftpc.setFileType(FTPClient.BINARY_FILE_TYPE);
							tw.append((new Date()).toString()+" "+path+File.separator+fileName+/*"_"+dateTouchString+*/" starting backup");
							ftpc.enterLocalPassiveMode();
//							ftpc.enterRemotePassiveMode();
//							ftpc.enterLocalActiveMode();
							ftpc.storeFile(fileName+"_" + dateTouchString+".tmp", fis);
							
							fis.close();
							ftpc.rename(fileName+"_" + dateTouchString+".tmp", fileName+"_" + dateTouchString);
							IJ.append((new Date()).toString()+" "+path+File.separator+fileName+/*"_"+dateTouchString+*/" newly backed up", IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+"WG_UploadLog.log");
							tw.append((new Date()).toString()+" "+path+File.separator+fileName+/*"_"+dateTouchString+*/" newly backed up");
						}
					}
					for (int c=IJ.isWindows()?0:1;c<pathChunks.length;c++) {
						ftpc.changeToParentDirectory();
					}
				}
				IJ.append("ENTIRE UPLOAD COMPLETE: "+ masterPath, IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+"WG_UploadLog.log");
				tw.append("ENTIRE UPLOAD COMPLETE: "+ masterPath);
				ftpc.logout();
			}
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
			if(ftpc.isConnected()) {
				try {
					ftpc.disconnect();
				} catch(IOException ioe) {
					// do nothing
				}
			}
		}
		newUploadProcess = null;
		 System.exit(0);
	}
	
	public String spawnNewUploadProcess(String arg) {
			String path = arg;
			jvm = null;
			newUploadProcess = null;
			int attempts =0;
			new File(IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator).mkdirs();
			String wg_jnlpSavePath = IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+"WG_Upload.jnlp";
			String wg_uploadJNLP = IJ.openUrlAsString("http://upload.cytoshow.org/WG_Upload.jnlp");
			wg_uploadJNLP = wg_uploadJNLP.replace("<argument>-upload</argument>", "<argument>-upload</argument>\n    <argument>"+path+"</argument>");
			new File(wg_jnlpSavePath).delete();
			IJ.append(wg_uploadJNLP,wg_jnlpSavePath);
			String returnString = "";
			while(attempts<1) {
				SingleInstanceService sis;
				try {
					sis = (SingleInstanceService) ServiceManager.lookup("javax.jnlp.SingleInstanceService");
					sis.removeSingleInstanceListener((SingleInstanceListener)IJ.getInstance() );				
				} catch (UnavailableServiceException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				jvm = new ProcessBuilder("javaws", "-Xnosplash", wg_jnlpSavePath);
				attempts++;
				jvm.redirectErrorStream(true);
				try {
					newUploadProcess = jvm.start();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			    IJ.wait(60000);
				try {
					sis = (SingleInstanceService) ServiceManager.lookup("javax.jnlp.SingleInstanceService");
					sis.addSingleInstanceListener((SingleInstanceListener)IJ.getInstance() );				
				} catch (UnavailableServiceException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

			} 

		    IJ.wait(2000);

			return returnString;

	}

	public Process getNewUploadProcess() {
		return newUploadProcess;
	}

}
