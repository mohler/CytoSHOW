package org.vcell.gloworm;

import ij.IJ;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class WG_Uploader implements PlugIn {
	
	public void run(String arg) {
		new WG_Uploader((new DirectoryChooser("Upload Folder Contents")).getDirectory());
	}

	public WG_Uploader() {
		
	}
	
	public WG_Uploader(String masterPath) {
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
					String[] pathChunks = path.split(""+File.separator);
					for (String chunk:pathChunks) {
						ftpc.makeDirectory(chunk);
						ftpc.changeWorkingDirectory(chunk);
					}
					String[] localDirFileNames = (new File(path)).list();
					String[] remoteFileNames = ftpc.listNames();
					for (String fileName:localDirFileNames) {
						boolean alreadyDone= false;
						if (remoteFileNames != null) {
							for (String remoteFileName:remoteFileNames) {
								if (fileName.equals(remoteFileName)) {
									alreadyDone = true;
									break;
								}
							}
						}
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
						if (!file.isDirectory() && !alreadyDone) {
							FileInputStream fis = new FileInputStream(path +File.separator +fileName);
							ftpc.setFileType(FTPClient.BINARY_FILE_TYPE);
							ftpc.enterLocalPassiveMode();
							ftpc.storeFile(fileName+"_" + dateTouchString+".tmp", fis);
							
							fis.close();
							ftpc.rename(fileName+"_" + dateTouchString+".tmp", fileName+"_" + dateTouchString);
						}
					}
					for (int c=1;c<pathChunks.length;c++) {
						ftpc.changeToParentDirectory();
					}
				}
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
	}

}
