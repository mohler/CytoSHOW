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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

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

	//	public FileClient(String[] args)
	public void run(String arg) {
		{     
			Container container=jFrame.getContentPane();
			container.setLayout(new FlowLayout());
			try
			{
				String remoteEngineName = arg+"/HEAD";
				IJ.log(""+remoteEngineName);
				fileInt = (Compute) Naming.lookup(remoteEngineName);
				IJ.log(fileInt.toString()+"success on connect.1");
				path = IJ.getFilePath("Upload which file?");
//				list=(new File(path)).list();
//				IJ.log(remoteEngineName+ list.toString());
//				files=new JList<String>(list);
			}
			catch(Exception e)
			{
				IJ.log("FileServer Exception:"+e.toString());
			}

//			files.setVisibleRowCount(3);
//			files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//			files.addListSelectionListener(
//					new ListSelectionListener()
//					{
//						public void valueChanged(ListSelectionEvent ev)
//						{
//							curpointer=files.getSelectedIndex();
//						}
//					});
//
//			upload=new JButton("Upload");
//			exit=new JButton("Exit");
//
//			container.add(upload);
//			container.add(exit);
//			container.add(new JScrollPane(files));
//
//			upload.addActionListener(this);
//			exit.addActionListener(this); 
//			jFrame.setTitle("WGFileUploadClient");
//			jFrame.setSize(new Dimension(250,250));
//			jFrame.setVisible(true);
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
			try
			{
//				String remoteEngineName = arg+"/HEAD";
//				Compute fileInt=(Compute)Naming.lookup(remoteEngineName);
				IJ.log(path);
				fileInt.saveUploadFile(uploadFileByteArray(path), "/"+Inet4Address.getLocalHost().getHostAddress()+"/"+path.replaceAll("[:]", "").replace("\\","/"));
				JOptionPane.showMessageDialog(null,path+" uploaded successfully");
			}
			catch(Exception e)
			{
				System.out.println("FileServer Exception:"+e.getMessage());
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


