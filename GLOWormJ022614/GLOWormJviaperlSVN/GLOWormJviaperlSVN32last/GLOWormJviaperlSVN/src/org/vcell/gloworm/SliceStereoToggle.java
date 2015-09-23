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

import javax.swing.SwingWorker;

public class SliceStereoToggle implements PlugIn, ActionListener {
	static Hashtable<String, Integer>  viewSpecificSliceHT = new Hashtable<String, Integer>();
	private String slcPath;
	private String prxPath;
	private String pryPath;
	private ImagePlus imp;
	protected ImagePlus newImp;
	protected Integer slice;
	protected boolean keepOriginal;
	protected int displaymode;
	private ActionEvent ae;
	private String pathlist;

	public SliceStereoToggle(ImagePlus sourceImp) {
		this.imp = sourceImp;
	}

	public void run(String arg) {
	}

	public void actionPerformed(ActionEvent e) 	{
		this.ae = e;
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
		imp.getCanvas().paint(imp.getCanvas().getGraphics());

		keepOriginal = false;
		keepOriginal = true;
		ImagePlus imp = this.imp;

		ColorModel[] cm = new ColorModel[imp.getNChannels()];
		LUT[] lut = imp.getLuts();

		pathlist = "";
		if (imp.isComposite()) {
			displaymode = ((CompositeImage)imp).getMode();
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

			slice = 1;
			SwingWorker<ImagePlus, ImagePlus> worker = new SwingWorker<ImagePlus,ImagePlus>() {

				@Override
				protected ImagePlus doInBackground() throws Exception {
					if (!SliceStereoToggle.this.ae.getActionCommand().contains("rc")) {
						MQTVSSceneLoader64 nextMsl64 = MQTVSSceneLoader64.runMQTVS_SceneLoader64(pathlist, "cycling");
						return nextMsl64.getImp();
					} else if (pathlist.contains("scene.scn")) {
						MQTVSSceneLoader64 nextMsl64 = MQTVSSceneLoader64.runMQTVS_SceneLoader64(pathlist, "cycling "+SliceStereoToggle.this.ae.getActionCommand());
						return nextMsl64.getImp();
					} else {
						String path = pathlist.replace("|", "");
						RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/+" "+path+" "+path.replaceAll(".*_z(\\d+)_t.*", "$1")/*+"36"*/, 
								false, true, true, false, true, false, true, ae.getActionCommand().contains("Stereo4DXrc"), false);
						return rmqtvsh.getImagePlus();
					}
				}

				@Override
				protected void done() {
					try {

						SliceStereoToggle.this.newImp = super.get();
						SliceStereoToggle.this.newImp.show();
						SliceStereoToggle.this.newImp.getWindow().toFront();

						if (SliceStereoToggle.this.ae.getActionCommand().contains("rc")) {
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

						if (viewSpecificSliceHT.get(SliceStereoToggle.this.newImp.getWindow().getTitle().split(",")[0]) != null)
							SliceStereoToggle.this.slice = viewSpecificSliceHT.get(SliceStereoToggle.this.newImp.getWindow().getTitle().split(",")[0]);
						SliceStereoToggle.this.newImp.setPosition(SliceStereoToggle.this.imp.getChannel(), slice, SliceStereoToggle.this.imp.getFrame());
						boolean running = SliceStereoToggle.this.imp.getWindow().running;
						boolean running2 = SliceStereoToggle.this.imp.getWindow().running2;
						boolean running3 = SliceStereoToggle.this.imp.getWindow().running3;
						SliceStereoToggle.this.newImp.getCanvas().setMagnification(SliceStereoToggle.this.imp.getCanvas().getMagnification());
						SliceStereoToggle.this.newImp.getWindow().setSize(SliceStereoToggle.this.imp.getWindow().getSize());
						SliceStereoToggle.this.newImp.getWindow().pack();
						SliceStereoToggle.this.newImp.getCanvas().zoomIn(newImp.getWidth(), newImp.getHeight());
						SliceStereoToggle.this.newImp.getCanvas().zoomOut(newImp.getWidth(), newImp.getHeight());
						SliceStereoToggle.this.newImp.getWindow().setLocation(SliceStereoToggle.this.imp.getWindow().getLocation().x, SliceStereoToggle.this.imp.getWindow().getLocation().y);
						//newImp.getWindow().setVisible(true);
						double[] inMin = new double[SliceStereoToggle.this.newImp.getNChannels()];
						double[] inMax = new double[SliceStereoToggle.this.newImp.getNChannels()];
						if (SliceStereoToggle.this.newImp.isComposite()) {
							((CompositeImage)newImp).copyLuts(SliceStereoToggle.this.imp);
							//Still need to fix replication of Min Max settings.!!
							((CompositeImage)SliceStereoToggle.this.newImp).setMode(3);
							((CompositeImage)SliceStereoToggle.this.newImp).setMode(SliceStereoToggle.this.displaymode);
							int channel = SliceStereoToggle.this.imp.getChannel();
							for (int c=1; c<=SliceStereoToggle.this.newImp.getNChannels(); c++) {
								inMin[c-1] = SliceStereoToggle.this.imp.getDisplayRangeMin();
								inMax[c-1] = SliceStereoToggle.this.imp.getDisplayRangeMax();
								SliceStereoToggle.this.newImp.setPositionWithoutUpdate(c, SliceStereoToggle.this.newImp.getSlice(), SliceStereoToggle.this.newImp.getFrame());
								SliceStereoToggle.this.newImp.setDisplayRange(inMin[c-1], inMax[c-1]);
							}
							((CompositeImage)SliceStereoToggle.this.imp).reset();
							SliceStereoToggle.this.imp.setPosition(channel, SliceStereoToggle.this.imp.getSlice(), SliceStereoToggle.this.imp.getFrame());
						}

						SliceStereoToggle.this.newImp.getWindow().sst.actionPerformed(new ActionEvent(newImp.getWindow(), ActionEvent.ACTION_PERFORMED, "Slice<>Stereo"));
						//			imp.getWindow().setVisible(false);

						SliceStereoToggle.this.newImp.updateAndRepaintWindow();

						SliceStereoToggle.this.newImp.getWindow().toFront();

						viewSpecificSliceHT.put(SliceStereoToggle.this.imp.getWindow().getTitle().split(",")[0], SliceStereoToggle.this.imp.getSlice());
						if(!SliceStereoToggle.this.keepOriginal)
							SliceStereoToggle.this.imp.close();
						if (running || running2) 
							IJ.doCommand("Start Animation [\\]");
						if (running3) 
							IJ.doCommand("Start Z Animation");
						//IJ.log("done");

					} catch (Throwable t) {
						t.printStackTrace();
					}
				}

			};
			worker.execute();
		}
	}

	public void reconnectRemote(ImagePlus rImp) {
		if (rImp.getRemoteMQTVSHandler() != null) {
			//			if (rImp.getRemoteMQTVSHandler().compQ == null) {
			if (true ) {
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

	class AnswerWorker extends SwingWorker<Integer, Integer>
	{

		@Override
		protected Integer doInBackground() throws Exception {
			// TODO Auto-generated method stub
			return null;
		}
	}
}
