package org.vcell.gloworm;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.rmi.RemoteException;
import java.util.Hashtable;

import client.RemoteMQTVSHandler;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.TextRoi;
import ij.plugin.PlugIn;
import ij.plugin.frame.ContrastAdjuster;
import ij.process.LUT;

public class SliceStereoToggle implements PlugIn, ActionListener {
	static Hashtable<String, Integer>  viewSpecificSliceHT = new Hashtable<String, Integer>();
	private String slcPath;
	private String prxPath;
	private String pryPath;
	private ImagePlus imp;

	public SliceStereoToggle(ImagePlus sourceImp) {
		this.imp = sourceImp;
	}

	public void run(String arg) {
	}

	public void actionPerformed(ActionEvent e) 	{	
		if (e.getActionCommand() == "Slice<>Stereo") {
			if(imp.getWindow().modeButtonPanel.isVisible()) {
				primeButtons(imp);
				return;
			}
		}
		TextRoi.setFont("Arial", imp.getWidth()/20, Font.ITALIC);		
		TextRoi tr = new TextRoi(0, 0, "Contacting\nCytoSHOW\nserver...");
		tr.setStrokeColor(Color.gray);
		tr.setFillColor(Color.decode("#55ffff00"));

		imp.setRoi(tr);
		tr.setImage(imp);
		imp.getCanvas().paintDoubleBuffered(imp.getCanvas().getGraphics());

		boolean keepOriginal = false;
		keepOriginal = true;
		ImagePlus imp = this.imp;

		ColorModel[] cm = new ColorModel[imp.getNChannels()];
		LUT[] lut = imp.getLuts();

		String pathlist = "";
		if (imp.isComposite()) {
			int displaymode = ((CompositeImage)imp).getMode();
			reconnectRemote(imp);
			imp.killRoi();

			String name = imp.getRemoteMQTVSHandler().getChannelPathNames()[imp.getChannel()-1];
			if (name.matches(".*(_pr|_slc)..*_z.*_t.*")) {
				String[] matchedNames = {""};
				try {
					String justname = name.replace("/Volumes/GLOWORM_DATA/", "");
					String subname = justname.replaceAll("(_pr|_slc).*","").replaceAll("\\+", "_") + " " + justname.replaceAll(".*(_pr..?|_slc)J?", "").replaceAll("_x.*", "") + " " + justname.replaceAll(".*(_nmdxy)", "");
					matchedNames = imp.getRemoteMQTVSHandler().getCompQ().getOtherViewNames(subname);
				} catch (RemoteException re) {
					// TODO Auto-generated catch block
					re.printStackTrace();
				}
				for (String match:matchedNames) {
					if (match.matches(".*(_slc_).*")) {
						slcPath = match;
					}
					if (match.matches(".*(_pry?xy?_).*")) {
						prxPath = match;
					}
					if (match.matches(".*(_prx?yx?_).*")) {
						pryPath = match;
					}
				}
			}

			if (e.getActionCommand() == "Slice<>Stereo") {
				if(!imp.getWindow().modeButtonPanel.isVisible()) {
					primeButtons(imp);
					return;
				}
			}

			else if (e.getActionCommand() == "Slice4D")
				pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + slcPath+ "|";
			else if (e.getActionCommand().contains("Stereo4DX"))
				pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + prxPath+ "|";
			else if (e.getActionCommand().contains("Stereo4DY"))
				pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + pryPath+ "|";

			ImagePlus newImp = null;
			int slice = 1;
			if (!e.getActionCommand().contains("rc")) {
				MQTVSSceneLoader64 nextMsl64 = MQTVSSceneLoader64.runMQTVS_SceneLoader64(pathlist, "cycling");
				newImp = nextMsl64.getImp();
			} else {
				String path = pathlist.replace("|", "");
				RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/+" "+path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
						false, true, true, false, true, false, true, e.getActionCommand().contains("Stereo4DXrc"), false);

				newImp = rmqtvsh.getImagePlus();

				MultiChannelController mcc = newImp.getMultiChannelController();
				mcc.setChannelLUTChoice(0, 0);
				CompositeImage ci = (CompositeImage)newImp;
				ci.setPosition( 0+1, ci.getSlice(), ci.getFrame() );
				IJ.run(mcc.getChannelLUTChoice(0));
				mcc.setChannelLUTChoice(1, 4);
				ci.setPosition( 1+1, ci.getSlice(), ci.getFrame() );
				IJ.run(mcc.getChannelLUTChoice(1));
				mcc.setSliceSpinner(0, 1);			
			}

			if (viewSpecificSliceHT.get(newImp.getWindow().getTitle().split(",")[0]) != null)
				slice = viewSpecificSliceHT.get(newImp.getWindow().getTitle().split(",")[0]);
			newImp.setPosition(imp.getChannel(), slice, imp.getFrame());
			boolean running = imp.getWindow().running;
			boolean running2 = imp.getWindow().running2;
			boolean running3 = imp.getWindow().running3;
			newImp.getCanvas().setMagnification(imp.getCanvas().getMagnification());
			newImp.getWindow().setSize(imp.getWindow().getSize());
			newImp.getWindow().pack();
			newImp.getCanvas().zoomIn(newImp.getWidth(), newImp.getHeight());
			newImp.getCanvas().zoomOut(newImp.getWidth(), newImp.getHeight());
			newImp.getWindow().setLocation(imp.getWindow().getLocation().x, imp.getWindow().getLocation().y);
			newImp.getWindow().setVisible(true);
			double[] inMin = new double[newImp.getNChannels()];
			double[] inMax = new double[newImp.getNChannels()];
			if (newImp.isComposite()) {
				((CompositeImage)newImp).copyLuts(imp);
				//Still need to fix replication of Min Max settings.!!
				((CompositeImage)newImp).setMode(3);
				((CompositeImage)newImp).setMode(displaymode);
				int channel = imp.getChannel();
				for (int c=1; c<=newImp.getNChannels(); c++) {
					inMin[c-1] = imp.getDisplayRangeMin();
					inMax[c-1] = imp.getDisplayRangeMax();
					newImp.setPositionWithoutUpdate(c, newImp.getSlice(), newImp.getFrame());
					newImp.setDisplayRange(inMin[c-1], inMax[c-1]);
				}
				((CompositeImage)imp).reset();
				imp.setPosition(channel, imp.getSlice(), imp.getFrame());
			}

			newImp.getWindow().sst.actionPerformed(new ActionEvent(newImp.getWindow(), ActionEvent.ACTION_PERFORMED, "Slice<>Stereo"));
			//			imp.getWindow().setVisible(false);

			newImp.updateAndRepaintWindow();
			newImp.getWindow().toFront();

			viewSpecificSliceHT.put(imp.getWindow().getTitle().split(",")[0], imp.getSlice());
			if(!keepOriginal)
				imp.close();
			if (running || running2) 
				IJ.doCommand("Start Animation [\\]");
			if (running3) 
				IJ.doCommand("Start Z Animation");
		}
	}

	public void reconnectRemote(ImagePlus rImp) {
		if (rImp.getRemoteMQTVSHandler() != null) {
			if (rImp.getRemoteMQTVSHandler().compQ == null) {

				rImp.getRemoteMQTVSHandler().getRemoteIP(
						((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack())
						.getAdjustedSlice(rImp.getCurrentSlice(), 0), 100, false);
			}
		}
	}

	public void primeButtons(ImagePlus imp2) {

		if (slcPath == null) {
			imp2.getWindow().slice4dButton.setVisible(false);
		} else {
			imp2.getWindow().slice4dButton.setVisible(true);
		}
		if (prxPath == null) {
			imp2.getWindow().stereo4dxButton.setVisible(false);
			imp2.getWindow().stereo4dXrcButton.setVisible(false);
		} else {
			imp2.getWindow().stereo4dxButton.setVisible(true);
			imp2.getWindow().stereo4dXrcButton.setVisible(true);
		}
		if (pryPath == null) {
			imp2.getWindow().stereo4dyButton.setVisible(false);
			imp2.getWindow().stereo4dYrcButton.setVisible(false);
		}else {
			imp2.getWindow().stereo4dyButton.setVisible(true);
			imp2.getWindow().stereo4dYrcButton.setVisible(true);
		}
		imp2.updateAndRepaintWindow();
		imp2.getWindow().toggle4DModes();
		return;
	}
}
