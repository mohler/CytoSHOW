package org.vcell.gloworm;
import java.awt.Color;
import java.awt.Frame;
import java.awt.image.ColorModel;
import java.io.File;
import java.util.Date;
import java.util.Hashtable;

import javax.vecmath.Color3f;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.ChannelSplitter;
import ij.plugin.Colors;
import ij.plugin.PlugIn;
import ij.plugin.Scaler;
import ij.plugin.SubHyperstackMaker;
import ij.plugin.filter.Projector;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij3d.ColorTable;
import ij3d.Content;
import ij3d.Image3DUniverse;
import ij3d.ImageJ3DViewer;
import isosurface.MeshExporter;


public class MQTVS_VolumeViewer  implements PlugIn {

	public static ImageJ3DViewer ij3dv;
	private Image3DUniverse univ;

	public void run(String arg) {
		String cellName = arg;
		ImagePlus imp = IJ.getImage();
		runVolumeViewer(imp, cellName, null, false, new Image3DUniverse());
	}
	
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, Image3DUniverse univ) {
		runVolumeViewer(imp, cellName, assignedColorString, false, univ);
	}
	
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, boolean saveSingly, Image3DUniverse univ) {
		boolean singleSave = IJ.shiftKeyDown() || saveSingly;
		if (univ == null){
			if (this.univ == null){	
				this.univ = new Image3DUniverse();
			}
			univ = this.univ;
		}
		if (univ.getWindow() == null){
			univ.show();
			univ.getWindow().setTitle(imp.getMotherImp().getTitle()+" [IJ3DV]");

			WindowManager.addWindow(univ.getWindow());
		}

		if (imp != null) {
			if (imp.getStack() instanceof MultiQTVirtualStack) {
				String firstMovieName = ((MultiQTVirtualStack)imp.getStack()).getVirtualStack(0).getMovieName();
				if ( firstMovieName.toLowerCase().contains("gp") || firstMovieName.toLowerCase().contains("yp") 
						|| firstMovieName.toLowerCase().contains("prx") || firstMovieName.toLowerCase().contains("pry") ) {
					if ( !IJ.showMessageWithCancel("Use Stereo4D data for Volume Viewer?", 
							"Are you certain you want to run Volume Viewer " +
							"\nto dissect a region from a Stereo4D movie? " +
							"\n(it's actually not a volume of slices when it's in Stereo4D format...)" +
							"\n " +
							"\nUsually, one wants to use Slice4D data for input to Volume Viewer.") ) {
						return;
					}
				}
			}
		} else {
			ImageJ3DViewer ij3dv = IJ.getIJ3DVInstance();			
			
			return;
		}
		
		MQTVS_Duplicator duper = new MQTVS_Duplicator();		

		Roi impRoi = imp.getRoi();
		if (true /*(imp.getStack().isVirtual() && imp.getNFrames() > 1) || imp.getRoi() != null*/) {
			
//			imp.getWindow().setVisible(false);
			RoiManager rm = imp.getRoiManager();
			boolean rmWasVis = false;
			if (rm != null) {
				rmWasVis = rm.isVisible();
				rm.setVisible(false);
			}
			MultiChannelController mcc = imp.getMultiChannelController();
			boolean mccWasVis = false;
			if (mcc != null) {
				mccWasVis = mcc.isVisible();
				mcc.setVisible(false);
			}
			
//			String duperString = ""; 
			ImagePlus impDup = null;
			if (imp.getRoiManager().getSelectedRoisAsArray().length == 0){  //??????WHY??????
//				duperString = duper.showHSDialog(imp, imp.getTitle()+"_DUP");
				impDup = duper.duplicateHyperstack(imp, imp.getTitle()+"_DUP", false);
				impDup.hide();
			}
			Date currentDate = new Date();
			long msec = currentDate.getTime();	
			long sec = msec/1000;

			
			if (impDup!=null){
				
				
				for (int ch=duper.getFirstC(); ch<=duper.getLastC(); ch++) {
					imp.setRoi(impRoi);
					ImagePlus impD = SubHyperstackMaker.makeSubhyperstack(impDup, ""+ch, "1-"+impDup.getNSlices(), "1-"+impDup.getNFrames());
					impD.setTitle(impD.getTitle()+"_"+ch);
//					impD.show();
					impD.setRoi(0, 0, impD.getWidth(), impD.getHeight());

					Color white = Colors.decode("#ff229900", Color.white);

					Color channelColor = assignedColorString!=null?Colors.decode(assignedColorString, null):null;
					if (channelColor == null) {
						channelColor = imp instanceof CompositeImage?((CompositeImage)imp).getChannelColor(ch-1):white;
						if (channelColor == Color.black)
							channelColor = white;
						if (cellName != "" && imp.getMotherImp().getRoiManager().getColorLegend() != null)
							channelColor = imp.getMotherImp().getRoiManager().getColorLegend().getBrainbowColors().get(cellName.split(" =")[0].split(" \\|")[0].toLowerCase());
						if (channelColor == null)
							channelColor = white;
					}
					int binFactor = 2;
					double scaleFactor  = 1.0;
					int threshold = 90;
					if (imp.getTitle().startsWith("SketchVolumeViewer")) {
						binFactor = 1;
						scaleFactor  = 0.1;
						if (!(imp.getMotherImp().getTitle().contains("SW_") || imp.getMotherImp().getTitle().contains("RGB_")))
							scaleFactor  = 1;
						threshold = 1;
					}
					//NOW HANDLE SCALING BEFORE CALL TO HIS METHOD					
					//					IJ.run(impD, "Scale...", "x="+(scaleFactor)+" y="+(scaleFactor)+" z=1.0 interpolation=Bicubic average process create" );
					//					ImagePlus impDS = IJ.getImage();
					//					impD = impDS;
					if (impD.getBitDepth()!=8){
//						ImageProcessor chip = impD.getProcessor();
//						if (impD.isComposite()){
//							chip = ((CompositeImage)impD).getProcessor(ch);
//						} else {
//							chip = impD.getProcessor();
//						}
//						double chmin = chip.getMin();
//						double chmax = chip.getMax();
//						chip.setMinAndMax(chmin, chmax);
						IJ.run(impD, "8-bit", "");
					}
					String objectName = cellName;
					if (objectName =="")
						objectName = impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_");

					Hashtable<String, Content> contents = univ.getContentsHT();
					univ.addContent(impD, new Color3f(channelColor), objectName, threshold, new boolean[]{true, true, true}, binFactor, Content.SURFACE);
					univ.select(univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)));
					Content sel = univ.getSelected();
					try {
						float r = Integer.parseInt(""+channelColor.getRed()) / 256f;
						float g = Integer.parseInt(""+channelColor.getGreen()) / 256f;
						float b = Integer.parseInt(""+channelColor.getBlue()) / 256f;
						if(univ != null && univ.getSelected() != null) {
							sel.setColor(new Color3f(r, g, b));
						}
					} catch(NumberFormatException e) {
						sel.setColor(null);
					}
					univ.getSelected().setLocked(true);
					if (singleSave) {
						Hashtable<String, Content> newestContent = new Hashtable<String, Content>();
						newestContent.put(""+objectName, univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)));
						MeshExporter.saveAsWaveFront(newestContent.values(), new File((IJ.getDirectory("home")+File.separator+impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_")+"_"+objectName.replaceAll(":","").replaceAll("(/|\\s+)","")+"_"+ch+"_"+0+".obj")), univ.getStartTime(), univ.getEndTime());
												univ.select(univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)));
												univ.getSelected().setLocked(false);
												univ.removeContent(univ.getSelected().getName());
					}
//					for (Object content:contents.values()){
//						if (((Content)content).getName() != objectName){
//							((Content)content).setVisible(false);
//						}
//					}

					IJ.setTool(ij.gui.Toolbar.HAND);
					if (impD != imp){
						impD.changes = false;
						if (impD.getWindow() != null)
							impD.getWindow().close();
						impD.flush();
					}

				}
				if (impDup != imp){
					impDup.changes = false;
					if (impDup.getWindow() != null)
						impDup.getWindow().close();
					impDup.flush();
				}
				ImageJ3DViewer.select(null);
				IJ.getInstance().toFront();
			} else {  //NOW BASICALLY DEAD LEGACY CODE...THE WAY THINGS WERE BEFORE ~4/23/2019...WORKED FOR ROI BUT NOT IMAGES
				for (int tpt = (singleSave?duper.getFirstT():0); tpt<=(singleSave?duper.getLastT():0); tpt = tpt+(singleSave?duper.getStepT():1)) {
					for (int ch=duper.getFirstC(); ch<=duper.getLastC(); ch++) {
						imp.setRoi(impRoi);
						ImagePlus impD = imp;
						if (!imp.getTitle().startsWith("SketchVolumeViewer")){
							impD = duper.run(imp, ch, ch, duper.getFirstZ(), duper.getLastZ(), singleSave?tpt:duper.getFirstT(), singleSave?tpt:duper.getLastT(), singleSave?1:duper.getStepT(), false, msec);
							impD.show();
							impD.setTitle(imp.getShortTitle()+"_DUP_"+ch+"_"+tpt);
							impD.changes = false;
						}
						Color white = Colors.decode("#ff229900", Color.white);

						Color channelColor = assignedColorString!=null?Colors.decode(assignedColorString, null):null;
						if (channelColor == null) {
							channelColor = imp instanceof CompositeImage?((CompositeImage)imp).getChannelColor(ch-1):white;
							if (channelColor == Color.black)
								channelColor = white;
							if (cellName != "" && imp.getMotherImp().getRoiManager().getColorLegend() != null)
								channelColor = imp.getMotherImp().getRoiManager().getColorLegend().getBrainbowColors().get(cellName.split(" =")[0].split(" \\|")[0].toLowerCase());
							if (channelColor == null)
								channelColor = white;
						}
						int binFactor = 2;
						double scaleFactor  = 1.0;
						int threshold = 90;
						if (imp.getTitle().startsWith("SketchVolumeViewer")) {
							binFactor = 1;
							scaleFactor  = 0.1;
							if (!(imp.getMotherImp().getTitle().contains("SW_") || imp.getMotherImp().getTitle().contains("RGB_")))
								scaleFactor  = 1;
							threshold = 1;
						}
						//NOW HANDLE SCALING BEFORE CALL TO HIS METHOD					
						//					IJ.run(impD, "Scale...", "x="+(scaleFactor)+" y="+(scaleFactor)+" z=1.0 interpolation=Bicubic average process create" );
						//					ImagePlus impDS = IJ.getImage();
						//					impD = impDS;
						if (impD.getBitDepth()!=8){
							IJ.run(impD, "8-bit", "");
						}
						String objectName = cellName;
						if (objectName =="")
							objectName = impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_");
						//					impD.show();
						Hashtable<String, Content> contents = univ.getContentsHT();
						univ.addContent(impD, new Color3f(channelColor), objectName, threshold, new boolean[]{true, true, true}, binFactor, Content.SURFACE);
						univ.select(univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)));
						Content sel = univ.getSelected();
						while (sel==null){
							IJ.wait(100);
						}
						try {
							float r = Integer.parseInt(""+channelColor.getRed()) / 256f;
							float g = Integer.parseInt(""+channelColor.getGreen()) / 256f;
							float b = Integer.parseInt(""+channelColor.getBlue()) / 256f;
							if(univ != null && univ.getSelected() != null) {
								sel.setColor(new Color3f(r, g, b));
							}
						} catch(NumberFormatException e) {
							sel.setColor(null);
						}
						while (univ.getSelected()==null){
							IJ.wait(100);
						}
						univ.getSelected().setLocked(true);
						if (singleSave) {
							Hashtable<String, Content> newestContent = new Hashtable<String, Content>();
							newestContent.put(""+objectName, univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)));
							MeshExporter.saveAsWaveFront(newestContent.values(), new File((IJ.getDirectory("home")+File.separator+impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_")+"_"+objectName.replaceAll(":","").replaceAll("(/|\\s+)","")+"_"+ch+"_"+tpt+".obj")), univ.getStartTime(), univ.getEndTime());
							//						univ.select(univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)));
							//						univ.getSelected().setLocked(false);
							//						univ.removeContent(univ.getSelected().getName());
						}
						for (Object content:contents.values()){
							if (((Content)content).getName() != objectName){
								((Content)content).setVisible(false);
							}
						}

						//					ImageJ3DViewer.select(null);
						//					IJ.getInstance().toFront();
						IJ.setTool(ij.gui.Toolbar.HAND);
						if (impD != imp){
							impD.changes = false;
							impD.getWindow().close();
							impD.flush();
						}
					}
				}
			}

			if (!imp.getTitle().startsWith("SketchVolumeViewer")) {
				imp.getWindow().setVisible(true);
				imp.getWindow().setAlwaysOnTop(false);
			}
//			ImageJ3DViewer.select(null);
//			IJ.getInstance().toFront();
			IJ.setTool(ij.gui.Toolbar.HAND);
			if (rm != null) rm.setVisible(rmWasVis);
			if (mcc != null) mcc.setVisible(mccWasVis);
		}

	}

	public Image3DUniverse getUniv() {
		return this.univ;
	}

	public void setUniv(Image3DUniverse univ) {
		this.univ = univ;
	}
}
