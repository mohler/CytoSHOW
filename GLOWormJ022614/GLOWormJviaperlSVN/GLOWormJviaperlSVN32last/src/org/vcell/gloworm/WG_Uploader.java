package org.vcell.gloworm;

import ij.IJ;
import ij.io.DirectoryChooser;
import ij.plugin.PlugIn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class WG_Uploader implements PlugIn {
	
	public void run(String arg) {
		new WG_Uploader((new DirectoryChooser("Upload Folder Contents")).getDirectory());
	}

	public WG_Uploader() {
		
	}
	
	public WG_Uploader(String path) {

		FTPClient ftpc = new FTPClient();
		try {
			ftpc.connect("155.37.255.65");
			int reply = ftpc.getReplyCode();

			if(!FTPReply.isPositiveCompletion(reply)) {
				ftpc.disconnect();
				IJ.log("FTP server refused connection.");
			} else {
				ftpc.login("glowormguest", "GLOWorm");
				String[] pathChunks = path.split(""+File.separator);
				for (String chunk:pathChunks) {
					ftpc.makeDirectory(chunk);
					ftpc.changeWorkingDirectory(chunk);
				}
				String[] saveDirFileNames = (new File(path)).list();
				for (String fileName:saveDirFileNames) {
					if (fileName.matches(".*")) {
						FileInputStream fis = new FileInputStream(path +File.separator +fileName);
						ftpc.setFileType(FTPClient.BINARY_FILE_TYPE);
						ftpc.enterLocalPassiveMode();
						ftpc.storeFile(fileName+".tmp", fis);
						fis.close();
						ftpc.rename(fileName+".tmp", fileName);
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
