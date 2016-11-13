package client;

import ij.IJ;
import ij.plugin.PlugIn;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.rmi.Naming;
import java.rmi.RemoteException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import compute.Compute;

public class WGFileDownloadClient implements PlugIn, ActionListener {
	
	JFrame jFrame = new JFrame();
	public JFrame getjFrame() {
		return jFrame;
	}
	public void setjFrame(JFrame jFrame) {
		this.jFrame = jFrame;
	}

	JList<String> files;
	JButton download,exit;
	String list[];
	int curpointer=-1;
	public String arg;
	private String path;
	private Compute fileInt;

	//	public FileClient(String[] args)
	public void run(String arg) {
		{     
			curpointer=-1;
			Container container=jFrame.getContentPane();
			container.setLayout(new FlowLayout());
			try
			{
				this.arg = arg;
				String remoteEngineName = arg+"/HEAD";
				IJ.log(""+remoteEngineName);
				fileInt = (Compute) Naming.lookup(remoteEngineName);
				IJ.log(fileInt.toString()+"success on connect.1");
				path = IJ.getString("Download files from which server directory?", "/Volumes/WormGUIDESmountpoint/YaleLabE/10.84.11.36/Users/wmohler/CytoSHOWCacheFiles/Volumes/GLOWORM_DATA/SEAMJR_MAX_MAX_SPIMA-1470331696_1.avi");
				list=fileInt.getFiles(path);
				IJ.log(remoteEngineName+ list.toString());
				files=new JList<String>(list);
			}
			catch(Exception e)
			{
				IJ.log("FileServer Exception:"+e.toString());
			}

			files.setVisibleRowCount(3);
			files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			files.addListSelectionListener(
					new ListSelectionListener()
					{
						public void valueChanged(ListSelectionEvent ev)
						{
							curpointer=files.getSelectedIndex();
						}
					});

			download=new JButton("Download");
			exit=new JButton("Exit");

			container.add(download);
			container.add(exit);
			container.add(new JScrollPane(files));

			download.addActionListener(this);
			exit.addActionListener(this); 
			jFrame.setTitle("WGFileDowloadClient");
			jFrame.setSize(new Dimension(250,250));
			jFrame.setVisible(true);
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
		else if(eventlabel.equals("Download"))
		{
			int loopStart = curpointer==-1?0:curpointer;
			int loopEnd = curpointer==-1?list.length:curpointer+1;
			for (int f=(loopStart); f<(loopEnd); f++) {
				curpointer = f;
				try
				{
					String remoteEngineName = this.arg+"/HEAD";
					Compute fileInt=(Compute)Naming.lookup(remoteEngineName);
					int chunkSize = 10*1024*1024;
					File file=new File(IJ.getDirectory("home")+path+File.separator+list[curpointer]);
					file.getParentFile().mkdirs();
					BufferedOutputStream outputFile=new BufferedOutputStream(new FileOutputStream(file.getAbsolutePath()));
					boolean looping = true;
					int iteration = 0;
					while(looping) {
						//				byte[] filedata=fileInt..downloadFileChunkByteArray(path+"/"+list[curpointer]);
						byte[] filedata = fileInt.downloadFileChunkByteArray(path+"/"+list[curpointer], chunkSize, iteration);
						looping = filedata!=null && filedata.length == chunkSize ;
						if (filedata!=null)
							outputFile.write(filedata,0,filedata.length);
						iteration++;
					}
					outputFile.flush();
					outputFile.close();
				}
				catch(Exception e)
				{
					System.out.println("FileServer Exception:"+e.getMessage());
				}

			}
			JOptionPane.showMessageDialog(jFrame,list[curpointer]+"Downloaded successfully");
		}
	}
	//	public static void main(String args[])
	//	{
	//		FileClient fileClient=new FileClient(args);
	//		arg=args[0];
	//		fileClient.getjFrame().setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	//	}

	//	public void run(String arg) {
	//		FileClient fileClient=new FileClient(new String[]{arg}) ;
	//		this.arg=arg;
	//		fileClient.getjFrame().setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	//		
	//	}
}


