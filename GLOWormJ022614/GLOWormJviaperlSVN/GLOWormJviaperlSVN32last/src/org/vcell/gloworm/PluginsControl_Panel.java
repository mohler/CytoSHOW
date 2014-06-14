package org.vcell.gloworm;
import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

import ij.plugin.*;
import ij.plugin.frame.*;
import java.awt.event.*;
import java.util.Hashtable;

public class PluginsControl_Panel extends PlugInFrame implements PlugIn, MouseListener, ActionListener  {
	GridBagLayout gridbag;
	GridBagConstraints c;
	private static Frame instance;
	public  String[] channelLUTItems =  { "Red", "Green", "Blue", "Grays","Cyan", "Magenta", "Yellow",  "Fire", "Ice", "Spectrum", "3-3-2 RGB"};
	private GridBagLayout lutGridbag;
	private GridBagConstraints lutConstraints;


	public PluginsControl_Panel() {
		super("CytoSHOW Functions");
		addMouseListener(this);
		if (instance!=null) {
			instance.toFront();
			return;
		}

		WindowManager.addWindow(this);
		instance = this;
		gridbag = new GridBagLayout();
		c = new GridBagConstraints();
		setLayout(gridbag);
		int y = 0;
		int x = 0;
		c.gridx = 0;
		c.gridy = y++;

		c.fill = GridBagConstraints.BOTH;
		c.anchor = GridBagConstraints.CENTER;
		int margin = 32;
		if (IJ.isVista())
			margin = 40;
		else if (IJ.isMacOSX())
			margin = 18;
		c.insets = new Insets(0, margin, 0, margin);

		//		IJ.log ( Menus.getCommands().toString() ) ;
		//		String[] pluginsArray = Menus.getPlugins();
		String[] pluginsArray = {/*"Open CytoSHOW Movie(s)...",
								"Open Other QuickTime Movie(s)...", 
								"Open a Saved Scene...", */
				"Adjust Display Contrast...", 
				"Multi-Channel Controller...", 
				"Tag Manager...",
				"Synchronize Windows",
				"Show Map of Keypad Controls",
				"Duplicate Region/Load to RAM...",
				"3D Project Selected Region...",
				"Orthogonal Views[/]",
				//"Interactive Reslice",
				"Volume Viewer",
				"Save Current Scene...", 
				"Save JPEG snapshot...",
//				"Save XYT movie of this scene...", 
				"Save movie of this scene...",
		};

		Button[] b = new Button[pluginsArray.length];

		for ( int i=0 ; i<pluginsArray.length ; i++) {
			c.gridwidth = 4;

			if ( (i+1) % 25 == 0) {
				c.gridx = x++;
				y  = 0;
			}
			//			if (i==0) {
			//				c.gridy = y++;
			//		        c.insets = new Insets(0, 0, 0, 0);
			//				add(new Label("Input Movies/Scenes:"), c);
			//		        c.insets = new Insets(0, margin, 0, margin);
			//				c.gridy = y++;
			//			}
			if (i==0) {
				c.gridy = y++;
				c.insets = new Insets(0, 0, 0, 0);
				add(new Label("Scene Settings:"), c);
				c.insets = new Insets(0, margin, 0, margin);
				c.gridy = y++;
			}
			if (i==5) {
				c.gridy = y++;
				c.insets = new Insets(0, 0, 0, 0);
				add(new Label("Digital Dissection:"), c);
				c.insets = new Insets(0, margin, 0, margin);
				c.gridy = y++;
			}
			if (i==9) {
				c.gridy = y++;
				c.insets = new Insets(0, 0, 0, 0);
				add(new Label("Save Movies/Scenes:"), c);
				c.insets = new Insets(0, margin, 0, margin);
				c.gridy = y++;
			}

			c.gridy = y++;

			b[i] = new Button(pluginsArray[i].replace("_", " "));
			c.gridwidth =4;
			add(b[i], c);

			b[i].addActionListener(this);
			b[i].addMouseListener(this);


		}	

		c.fill = GridBagConstraints.NONE;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(0, 0, 0, 0);
		c.gridwidth = 4;
		c.gridy=y++;
		add(new Label("Color/Pseudocolor:"), c);

		c.gridwidth=1; 
		c.gridy=y++;

		for ( int i=0 ; i<channelLUTItems.length ; i++) {
			b[i] = new Button(channelLUTItems[i].replace("_", " "));
			c.gridx = i%4;
			c.weightx = 1.0;
			if(i%4==0) {
				c.gridy = y++;
				c.weightx = 0.0;
			}

			c.fill = GridBagConstraints.BOTH;
			c.anchor = GridBagConstraints.CENTER;
			c.gridwidth=1; 

			add(b[i], c);

			b[i].addActionListener(this);
			b[i].addMouseListener(this);

		}	


		addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts
		setResizable(false);
		pack();
		show();


	}


	public void actionPerformed(ActionEvent e) {

		String command = e.getActionCommand();
		if (command==null) return;
//		if (command == "Show Map of Keypad Controls") {
//			IJ.run("URL...", "url=http://fsbill.cam.uchc.edu/gloworm/Xwords/CytoSHOWKeypad.jpg");
//			return;
//		}
		ImagePlus adjimp = WindowManager.getCurrentImage();

		if (adjimp==null ) return;
		int impChIndex= adjimp.getChannel();
		ImageStack adjstack = adjimp.getStack();
		Frame[] niw = WindowManager.getNonImageWindows();

		if (adjstack instanceof MultiQTVirtualStack) {
			IJ.run("Stop Animation");
			for (int j = 0; j < channelLUTItems.length; j++) {
				if (channelLUTItems[j].contains(command)) {
					((MultiQTVirtualStack) adjstack).setChannelLUTIndex(
							impChIndex - 1, j);
					for (int k = 0; k < niw.length; k++) {
						if (niw[k] instanceof MultiChannelController) {
							((MultiChannelController) niw[k])
							.setChannelLUTChoice(impChIndex - 1, j);
						}

					}

				}
			}
			IJ.doCommand(command);


		} else {
			IJ.doCommand(command);
		}
	}


	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub

	}


	public void mouseEntered(MouseEvent e) {
//		IJ.runMacro("print(\"\\\\Clear\")");
//		IJ.runMacro("print(\"\\\\Update:CytoSHOW Functions:\\\nProvides quick access to CytoSHOW-specific functions not included in the standard menu commands of ImageJ.\\\n \")");

	}


	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}


	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub

	}


	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub

	}

}
