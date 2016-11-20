package client;

import ij.IJ;
import ij.plugin.PlugIn;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.vcell.gloworm.GetNetworkAddress;

import compute.Compute;

public class WGFileUploadClient implements PlugIn, ActionListener {

	JFrame jFrame = new JFrame();
	public JFrame getjFrame() {
		return jFrame;
	}
	public void setjFrame(JFrame jFrame) {
		this.jFrame = jFrame;
	}

	JList<String> files;
	JButton upload,exit;
	String list[];
	int curpointer=0;
	public String arg;
	private String path;
	private Compute fileInt;
	private ArrayList<String> subDirectories = new ArrayList<String>();
	static int chunkSizeSpec = 4096*4096;

	//	public FileClient(String[] args)
	public void run(String arg) {
		{     
			Container container=jFrame.getContentPane();
			container.setLayout(new FlowLayout());
			chunkSizeSpec = (int) IJ.getNumber("Bytes per chunk?", chunkSizeSpec);
			try
			{
				String remoteEngineName = arg+"/HEAD";
				IJ.log(""+remoteEngineName);
				fileInt = (Compute) Naming.lookup(remoteEngineName);
				IJ.log(fileInt.toString()+"success on connect.1");
				path = IJ.getDirectory("Upload which folder?");
				if (!path.endsWith(File.separator))
					path = path+File.separator;
				list=(new File(path)).list();
				IJ.log(remoteEngineName+ list.toString());
				files=new JList<String>(list);
			}
			catch(Exception e)
			{
				IJ.log("FileServer Exception:"+e.toString());
			}

			actionPerformed(new ActionEvent(this, 0, "Upload"));
		}

	}
	public void actionPerformed(ActionEvent ae)
	{
		String eventlabel=ae.getActionCommand();
		if(eventlabel.equals("Exit"))
		{
			jFrame.setVisible(false);
			System.exit(0);
		}
		else if(eventlabel.equals("Upload"))
		{
			if ((new File(path)).isDirectory()){
				for (int f=0;f<list.length;f++) {
					String filePath = path+list[f];
					File nextFile = new File(filePath);
					long nextFileLength = nextFile.length();
					if (nextFile.isDirectory()) {
						subDirectories.add(filePath);
					} else {
						Date fd = new Date(nextFile.lastModified());
						String dateTouchString = 20
								+ IJ.pad(fd.getYear()-100, 2) 
								+ IJ.pad(fd.getMonth()+1, 2)
								+ IJ.pad(fd.getDate(), 2)
								+ IJ.pad(fd.getHours(), 2)
								+ IJ.pad(fd.getMinutes(), 2)
								+ "."
								+ IJ.pad(fd.getSeconds(), 2);

						try
						{
//							String uniqueClientIdentifier = GetNetworkAddress.GetAddress("ip");
							String uniqueClientIdentifier = GetNetworkAddress.GetAddress("mac");
//							String uniqueClientIdentifier = Inet4Address.getLocalHost().getHostAddress();
							IJ.log(filePath+" starting upload, "+nextFileLength+" bytes...");
							long timeStart = (new Date()).getTime();
							int iteration=0;
							boolean looping = true;
							
							while(looping ) {
								//fileInt.saveUploadFile(uploadFileByteArray(filePath), "/"+uniqueClientIdentifier+"/"+filePath.replaceAll("[:]", "").replace("\\","/")+"_" + dateTouchString);
								long sizeOnServerSoFar = fileInt.saveUploadFile(uploadFileChunkByteArray(filePath, chunkSizeSpec, iteration), "/"+uniqueClientIdentifier+"/"+filePath.replaceAll("[:]", "").replace("\\","/")+"_" + dateTouchString+".tmp");
//								IJ.log(""+sizeOnServerSoFar);
								iteration++;
								long copiedSoFar = ((long)chunkSizeSpec)*((long)iteration);
								looping = copiedSoFar < nextFileLength;
							}
							IJ.log(filePath+" uploaded successfully ("+nextFile.length()+" bytes, "+((new Date()).getTime()-timeStart)+" msec)");
							fileInt.renameUploadFile("/"+uniqueClientIdentifier+"/"+filePath.replaceAll("[:]", "").replace("\\","/")+"_" + dateTouchString+".tmp",
									"/"+uniqueClientIdentifier+"/"+filePath.replaceAll("[:]", "").replace("\\","/")+"_" + dateTouchString);
						}
						catch(Exception e)
						{
							IJ.log("FileServer Exception:"+e.initCause(e).toString());
						}
					}
				}
				for (String subdir:subDirectories) {
					IJ.run("TestRMIFTUpload", "upload=["+subdir+"]");				
				}
				
			}
		}
}
	
	public byte[] uploadFileByteArray(String path) {
		File file=new File(path);
		//Defines buffer in which the file will be read
		byte[] buffer=new byte[(int)file.length()];
		BufferedInputStream inputFileStream = null;
		try {
			inputFileStream = new BufferedInputStream( new FileInputStream(path));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//Reads the file into buffer
		try {
			inputFileStream.read(buffer,0,buffer.length);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			inputFileStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return(buffer);

	}
	
	public byte[] uploadFileChunkByteArray(String filePath, int chunkSize, int iteration) {
		try
		{
			File file=new File(filePath);
			long fileLength = file.length();
			//Defines buffer in which the file will be read
			long nextChunkSize =  (((long)chunkSize) < fileLength-(((long)chunkSize)*((long)iteration))?((long)chunkSize):fileLength%((long)chunkSize));
			byte[] buffer=new byte[(int) nextChunkSize];
			BufferedInputStream inputFileStream=new BufferedInputStream( new FileInputStream(filePath));
			//Reads the file into buffer
			inputFileStream.skip(((long)chunkSize)*((long)iteration));
			inputFileStream.read(buffer,0,(int) nextChunkSize);
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
}


