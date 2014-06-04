package org.vcell.gloworm;

import java.rmi.RemoteException;
import java.util.Hashtable;

import client.RemoteMQTVSHandler;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

public class SliceStereoToggle implements PlugIn {
	static Hashtable<String, Integer>  viewSpecificSliceHT = new Hashtable<String, Integer>();
	
	public SliceStereoToggle() {
		// TODO Auto-generated constructor stub
	}

	public void run(String arg) {
		ImagePlus imp = IJ.getImage();
		if (imp.getWindow()!=null) {
			if (imp.getRemoteMQTVSHandler() != null)
				imp.getRemoteMQTVSHandler().getRemoteIP(
						((RemoteMQTVSHandler.RemoteMQTVirtualStack)imp.getStack())
							.getAdjustedSlice(imp.getCurrentSlice(), 0), 100, false);
			String pathlist = "";
			if (imp.isComposite()) {
				int displaymode = ((CompositeImage)imp).getMode();
				for (String name:imp.getRemoteMQTVSHandler().getChannelPathNames()) {
					if (name.matches(".*(_pr|_slc)..*_z.*_t.*")) {
						String[] matchedNames = {""};
						try {
							String justname = name.replace("/Volumes/GLOWORM_DATA/", "");
							String subname = justname.replaceAll("(_pr|_slc).*","").replaceAll("\\+", "_") + " " + justname.replaceAll(".*(_pr..?|_slc)J?", "").replaceAll("_x.*", "") + " " + justname.replaceAll(".*(_nmdxy)", "");
							matchedNames = imp.getRemoteMQTVSHandler().getCompQ().getOtherViewNames(subname);
						} catch (RemoteException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						boolean takeNext = false;
						for (String match:matchedNames) {
							if (takeNext && !match.matches(".*(_pr..?|_slc)J.*") && pathlist == "") {
								pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + match + "|";
								takeNext = false;
							} else if (name.contains(match))
								takeNext = true;
						}
						if (pathlist == "")
							pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + matchedNames[0] + "|";
//						pathlist = pathlist + "/Volumes/GLOWORM_DATA/" + matchedNames[(int) Math.floor(Math.random()*(matchedNames.length-1))] + "|";
					}
				}
				if (pathlist == "")
					return;
				MQTVSSceneLoader64 nextMsl64 = MQTVSSceneLoader64.runMQTVS_SceneLoader64(pathlist, "cycling");
				int slice = 1;
				if (viewSpecificSliceHT.get(nextMsl64.getImp().getWindow().getTitle().split(",")[0]) != null)
					slice = viewSpecificSliceHT.get(nextMsl64.getImp().getWindow().getTitle().split(",")[0]);
				nextMsl64.getImp().setPosition(imp.getChannel(), slice, imp.getFrame());
				boolean running = imp.getWindow().running;
				boolean running2 = imp.getWindow().running2;
				boolean running3 = imp.getWindow().running3;
				nextMsl64.getImp().getWindow().setLocation(imp.getWindow().getLocation().x, imp.getWindow().getLocation().y);
				nextMsl64.getImp().getCanvas().setMagnification(imp.getCanvas().getMagnification());
				nextMsl64.getImp().getWindow().setSize(imp.getWindow().getSize());
				nextMsl64.getImp().getWindow().setVisible(true);
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
	}

}
