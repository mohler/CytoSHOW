package org.vcell.gloworm;

import ij.IJ;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class WG_Uploader implements PlugIn {
	
	public void run(String arg) {
		spawnNewUploadProcess((new DirectoryChooser("Upload Folder Contents")).getDirectory());
	}

	public WG_Uploader() {
		
	}
	
	public WG_Uploader(String masterPath) {
		if (masterPath == null)
			return;

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
		
		FTPClient ftpc = new FTPClient();
		try {
			ftpc.connect("155.37.255.65");
			int reply = ftpc.getReplyCode();

			if(!FTPReply.isPositiveCompletion(reply)) {
				ftpc.disconnect();
				IJ.log("FTP server refused connection.");
			} else {
				ftpc.login("glowormguest", "GLOWorm");
				ftpc.makeDirectory(ftpc.getLocalAddress().toString());
				ftpc.changeWorkingDirectory(ftpc.getLocalAddress().toString());
				for (String path:iterativeDirPaths) {
					String[] pathChunks = path.replace(":","").split("\\"+File.separator);
					String pathConcat = "";
					for (String chunk:pathChunks) {
						if (!chunk.equals("")) {
							pathConcat = pathConcat + File.separator + chunk;
							File dirFile = new File(pathConcat);
							Date dd = new Date(dirFile.lastModified());
							String dirDateTouchString = "";
// seemed like good idea, but folder moddates tricky...no go
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
									IJ.append((new Date()).toString()+" "+path+fileName+/*"_"+dateTouchString+*/" already backed up.", IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+"WG_UploadLog.log");
										
									break;
								}
							}
						}
						if (!file.isDirectory() && !alreadyDone) {
							FileInputStream fis = new FileInputStream(path +File.separator +fileName);
							ftpc.setFileType(FTPClient.BINARY_FILE_TYPE);
							ftpc.enterLocalPassiveMode();
							ftpc.storeFile(fileName+"_" + dateTouchString+".tmp", fis);
							
							fis.close();
							ftpc.rename(fileName+"_" + dateTouchString+".tmp", fileName+"_" + dateTouchString);
							IJ.append((new Date()).toString()+" "+path+fileName+/*"_"+dateTouchString+*/" newly backed up", IJ.getDirectory("home")+"CytoSHOWCacheFiles"+File.separator+"WG_UploadLog.log");
						}
					}
					for (int c=IJ.isWindows()?0:1;c<pathChunks.length;c++) {
						ftpc.changeToParentDirectory();
					}
				}
				ftpc.logout();
			}
		System.exit(0);
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
	
	}
	
	public String spawnNewUploadProcess(String arg) {
			String path = arg;
			ProcessBuilder jvm = null;
			Process newUploadProcess = null;
			int attempts =0;
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

}
