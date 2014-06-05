package org.vcell.gloworm;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.RemoteException;
import java.util.Hashtable;

import client.RemoteMQTVSHandler;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

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

		ImagePlus imp = this.imp;
	
		String pathlist = "";
		if (imp.isComposite()) {
			int displaymode = ((CompositeImage)imp).getMode();
			reconnectRemote(imp);
			
			if (e.getActionCommand() == "Slice<>Stereo") {
				primeButtons(imp);
				return;
			}
			
			else if (e.getActionCommand() == "Slice4D")
				pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + slcPath+ "|";
			else if (e.getActionCommand().contains("Stereo4DX"))
				pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + prxPath+ "|";
			else if (e.getActionCommand().contains("Stereo4DY"))
				pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + pryPath+ "|";

			MQTVSSceneLoader64 nextMsl64 = MQTVSSceneLoader64.runMQTVS_SceneLoader64(pathlist, "cycling");
			int slice = 1;
			if (viewSpecificSliceHT.get(nextMsl64.getImp().getWindow().getTitle().split(",")[0]) != null)
				slice = viewSpecificSliceHT.get(nextMsl64.getImp().getWindow().getTitle().split(",")[0]);
			nextMsl64.getImp().setPosition(imp.getChannel(), slice, imp.getFrame());
			boolean running = imp.getWindow().running;
			boolean running2 = imp.getWindow().running2;
			boolean running3 = imp.getWindow().running3;
			nextMsl64.getImp().getCanvas().setMagnification(imp.getCanvas().getMagnification());
			nextMsl64.getImp().getWindow().setSize(imp.getWindow().getSize());
			nextMsl64.getImp().getWindow().pack();
			nextMsl64.getImp().getCanvas().zoomIn(nextMsl64.getImp().getWidth(), nextMsl64.getImp().getHeight());
			nextMsl64.getImp().getCanvas().zoomOut(nextMsl64.getImp().getWidth(), nextMsl64.getImp().getHeight());
			nextMsl64.getImp().getWindow().setLocation(imp.getWindow().getLocation().x, imp.getWindow().getLocation().y);
			nextMsl64.getImp().getWindow().setVisible(true);

			nextMsl64.getImp().getWindow().sst.primeButtons(nextMsl64.getImp());;
			imp.getWindow().setVisible(false);
			if (nextMsl64.getImp().isComposite()) {
				((CompositeImage)nextMsl64.getImp()).copyLuts(imp);
				//Still need to fix replication of Min Max settings.!!
				((CompositeImage)nextMsl64.getImp()).setMode(3);
				((CompositeImage)nextMsl64.getImp()).setMode(displaymode);
				nextMsl64.getImp().updateAndRepaintWindow();

			}
			viewSpecificSliceHT.put(imp.getWindow().getTitle().split(",")[0], imp.getSlice());
			imp.close();
			if (running || running2) 
				IJ.doCommand("Start Animation [\\]");
			if (running3) 
				IJ.doCommand("Start Z Animation");
		}
	}

	public void reconnectRemote(ImagePlus rImp) {
		if (rImp.getRemoteMQTVSHandler() != null) {
			rImp.getRemoteMQTVSHandler().getRemoteIP(
					((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack())
					.getAdjustedSlice(rImp.getCurrentSlice(), 0), 100, false);
		}
	}

	public void primeButtons(ImagePlus imp2) {
		if (imp2.isComposite()) {
			reconnectRemote(imp2);
			for (String name:imp2.getRemoteMQTVSHandler().getChannelPathNames()) {
				if (name.matches(".*(_pr|_slc)..*_z.*_t.*")) {
					String[] matchedNames = {""};
					try {
						String justname = name.replace("/Volumes/GLOWORM_DATA/", "");
						String subname = justname.replaceAll("(_pr|_slc).*","").replaceAll("\\+", "_") + " " + justname.replaceAll(".*(_pr..?|_slc)J?", "").replaceAll("_x.*", "") + " " + justname.replaceAll(".*(_nmdxy)", "");
						matchedNames = imp2.getRemoteMQTVSHandler().getCompQ().getOtherViewNames(subname);
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
			}
		}
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
