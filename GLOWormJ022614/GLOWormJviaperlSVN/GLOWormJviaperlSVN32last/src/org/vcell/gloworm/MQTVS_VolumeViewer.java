package org.vcell.gloworm;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.ColorModel;
import java.io.File;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import javax.vecmath.Color3f;
import javax.vecmath.Point3f;

import customnode.CustomMesh;
import customnode.CustomMeshNode;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.CanvasResizer;
import ij.plugin.ChannelSplitter;
import ij.plugin.Colors;
import ij.plugin.PlugIn;
import ij.plugin.Scaler;
import ij.plugin.Slicer;
import ij.plugin.StackReverser;
import ij.plugin.SubHyperstackMaker;
import ij.plugin.filter.Projector;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij3d.ColorTable;
import ij3d.Content;
import ij3d.ContentNode;
import ij3d.Image3DUniverse;
import ij3d.ImageJ3DViewer;
import isosurface.MeshExporter;


public class MQTVS_VolumeViewer  implements PlugIn, WindowListener {

	public static ImageJ3DViewer ij3dv;
	private Image3DUniverse univ;
	private ImagePlus impDup;

	public void run(String arg) {
		String cellName = arg;
		ImagePlus imp = IJ.getImage();
		runVolumeViewer(imp, cellName, null, false, new Image3DUniverse(), null, null);
	}
	
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, Image3DUniverse univ) {
		runVolumeViewer(imp, cellName, assignedColorString, false, univ, null, null);
	}
	
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, Image3DUniverse univ, String outDir) {
		runVolumeViewer(imp, cellName, assignedColorString, false, univ, outDir, null);
	}
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, boolean saveSingly, Image3DUniverse univ, String outDir, String scaleShiftString) {
		boolean singleSave = IJ.shiftKeyDown() || saveSingly;
		if (univ == null){
			if (this.univ == null){	
				this.univ = new Image3DUniverse();
			}
			univ = this.univ;
		}
		if (!saveSingly && univ.getWindow() == null){
			univ.show(false);
			univ.getWindow().addWindowListener(this);
			WindowManager.removeWindow(univ.getWindow());
			
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
			
			impDup = null;
			if (imp.getRoiManager().getSelectedRoisAsArray().length == 0){  //??????WHY??????
//				duperString = duper.showHSDialog(imp, imp.getTitle()+"_DUP");
				impDup = duper.duplicateHyperstack(imp, imp.getTitle()+"_DUP", false);
				if (impDup !=null)
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
					if (imp.getTitle().startsWith("SVV")) {
						binFactor = 1;
						scaleFactor  = 0.1;
						if (!(imp.getMotherImp().getTitle().contains("SW_") || imp.getMotherImp().getTitle().contains("RGB_")))
							scaleFactor  = 1;
						threshold = 1;
					}
					if (impD.getBitDepth()!=8){

						IJ.run(impD, "8-bit", "");
					}
					String objectName = cellName;
					if (objectName =="")
						objectName = impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_");

					Hashtable<String, Content> contents = univ.getContentsHT();
					
					
					univ.addContent(impD, new Color3f(channelColor), objectName, threshold, new boolean[]{true, true, true}, binFactor, Content.SURFACE);
					univ.select(univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)), true);
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
						MeshExporter.saveAsWaveFront(newestContent.values(), new File(((outDir==null?IJ.getDirectory("home"):outDir)+File.separator+impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_")+"_"+objectName.replaceAll(":","").replaceAll("(/|\\s+)","")+"_"+ch+"_"+0+".obj")), univ.getStartTime(), univ.getEndTime(), true);
						univ.select(univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)), true);
						univ.getSelected().setLocked(false);
						univ.removeContent(univ.getSelected().getName(), true);
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
						impD = null;
					}

				}
				if (impDup != imp && impDup != null){
				    impDup.changes = false;
				    if (impDup.getWindow() != null) impDup.getWindow().close();
				    impDup.flush(); // <--- FORCE FLUSH
				    impDup = null;  // <--- KILL REFERENCE
				}
				ImageJ3DViewer.select(null);
				IJ.getInstance().toFront();
			} else {  
				for (int tpt = (singleSave?duper.getFirstT():0); tpt<=(singleSave?duper.getLastT():0); tpt = tpt+(singleSave?duper.getStepT():1)) {
					for (int ch=duper.getFirstC(); ch<=duper.getLastC(); ch++) {
						imp.setRoi(impRoi);
						ImagePlus impD = imp;
						if (!imp.getTitle().startsWith("SVV")){
							impD = duper.run(imp, ch, ch, duper.getFirstZ(), duper.getLastZ(), singleSave?tpt:duper.getFirstT(), singleSave?tpt:duper.getLastT(), singleSave?1:duper.getStepT(), false, msec);
//							impD.show();
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
						if (imp.getTitle().startsWith("SVV")) {
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

//						IJ.save(impD, "/Users/wmohler/Documents/testingStuff/"+ objectName +"impD.tif");
						String[] scaleShifts = scaleShiftString.split("\\|");
						boolean rotate90Y = (scaleShifts.length>6) && !scaleShifts[6].equals("0");
//  THIS NEXT BLOCK DOES AN reslice and then flips the reslice stack to give proper 90°-Y rotation relative to impD						
						if (rotate90Y) {
							Slicer sketchImpSlicer = new Slicer();
							ImagePlus flipLeftSketchImp = sketchImpSlicer.reslice(impD);
							(new StackReverser()).flipStack(flipLeftSketchImp);
							flipLeftSketchImp.setTitle(impD.getTitle()+"flip");
////							IJ.save(flipLeftSketchImp, "/Users/wmohler/Documents/testingStuff/"+ objectName +"flipLeftSketchImp.tif");
//							IJ.log("drawn... " +impD.toString());
//							IJ.log("flipped... " +flipLeftSketchImp.toString());
							impD= flipLeftSketchImp;
						}
//
						impD.setMotherImp(imp, imp.getID());
						univ.addContent(impD, new Color3f(channelColor), objectName, threshold, new boolean[]{true, true, true}, binFactor, Content.SURFACE);
						univ.select(univ.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)), true);

						// -------------------------------------------------------
						// 1. Wait for the Content to actually exist (Async Add)
						// -------------------------------------------------------
						long startTime = System.currentTimeMillis();
						while (univ.getContent(objectName) == null) {
						    IJ.wait(10);
						    if (System.currentTimeMillis() - startTime > 5000) { // 5s timeout
						        IJ.log("Error: Content '" + objectName + "' timed out during creation.");
						        break; 
						    }
						}

						// -------------------------------------------------------
						// 2. Select it and Poll the Universe until valid
						// -------------------------------------------------------
						// Initial attempt
						univ.select(univ.getContent(objectName), true);

						// Poll the UNIVERSE, not a local variable
						while (univ.getSelected() == null) {
						    // Retry selection in case the first one missed the timing window
						    if (univ.getContent(objectName) != null) {
						        univ.select(univ.getContent(objectName), true);
						    }
						    IJ.wait(10);
						    
						    // Safety hatch to prevent infinite spinning
						    if (System.currentTimeMillis() - startTime > 10000) {
						        IJ.log("Error: Failed to select '" + objectName + "' (Timeout)");
						        break; 
						    }
						}

						// -------------------------------------------------------
						// 3. Now safe to assign
						// -------------------------------------------------------
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
						while (univ.getSelected()==null){
							IJ.wait(100);
						}
						univ.getSelected().setLocked(true);
						
//						univ.show(false);
						
						if (singleSave) {
							Hashtable<String, Content> newestContent = new Hashtable<String, Content>();
							newestContent.put(""+objectName, univ.getContent((""+objectName)));
							MeshExporter.saveAsWaveFront(newestContent.values(), 
															new File(((outDir==null?IJ.getDirectory("home"):outDir)+File.separator
																	+impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_")+"_"
																	+"_"+ch+"_"+tpt+".obj")), 
															univ.getStartTime(), 
															univ.getEndTime(),
															scaleShiftString, true);
						}
						for (Object content:contents.values()){
							if (((Content)content).getName() != objectName){
								((Content)content).setVisible(false);
								univ.removeContent(objectName, true);
							}
						}

						IJ.setTool(ij.gui.Toolbar.HAND);
						if (impD != imp && impD!=null && impD.getWindow()!=null){
							impD.changes = false;
							impD.getWindow().close();
							impD.flush();
							impD = null;
						}
					}
				}
			}

			if (!imp.getTitle().startsWith("SVV")) {
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

	
	public void windowOpened(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	
	public void windowClosing(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	
	public void windowClosed(WindowEvent e) {
		univ.getWindow().removeWindowListener(this);
		impDup.getWindow().dispose();
		impDup.flush();
		//I don't think this is really cleaning up memory...
	}

	
	public void windowIconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	
	public void windowDeiconified(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	
	public void windowActivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	
	public void windowDeactivated(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}
	
//	public void dispose() {
//	    // 1. Flush the massive duplicate image
//	    if (impDup != null) {
//	        impDup.changes = false;
//	        impDup.close();
//	        impDup.flush(); 
//	        impDup = null;
//	    }
//	    
//	    // 2. Detach the Universe
//	    if (univ != null) {
//	        // The RoiManager calls univ.cleanup(), so we just null the ref here
//	        univ = null;
//	    }
//	    
//	    // 3. Clear static if necessary (though we are avoiding statics now)
//	    ij3dv = null;
//	}

	public void dispose() {
	    // 1. Dismantle the 3D Content (Your existing logic)
	    if (univ != null) {
	        java.util.Collection allContent = univ.getContents();
	        if (allContent != null) {
	            java.util.ArrayList<Content> contentList = new java.util.ArrayList<Content>(allContent);
	            for (Content c : contentList) {
	                if (c.getInstants() != null) {
	                    for (ij3d.ContentInstant instant : c.getInstants().values()) {
	                        ij3d.ContentNode node = instant.getContentNode();
	                        if (node instanceof customnode.CustomMeshNode) {
	                            customnode.CustomMesh mesh = ((customnode.CustomMeshNode)node).getMesh();
	                            if (mesh != null) {
	                                java.util.List<javax.vecmath.Point3f> vertices = mesh.getMesh();
	                                if (vertices != null) vertices.clear();
	                            }
	                        } else if (node instanceof customnode.CustomMultiMesh) {
	                            customnode.CustomMultiMesh multi = (customnode.CustomMultiMesh) node;
	                            for (int i = 0; i < multi.size(); i++) {
	                                customnode.CustomMesh subMesh = multi.getMesh(i);
	                                if (subMesh != null) {
	                                    java.util.List<javax.vecmath.Point3f> vertices = subMesh.getMesh();
	                                    if (vertices != null) vertices.clear();
	                                }
	                            }
	                        }
	                    }
	                }
	                univ.removeContent(c.getName(), false);
	            }
	        }

	        // 2. NUCLEAR OPTION: Kill the Canvas Peer AND FLUSH BUFFERS
	        try {
	            javax.media.j3d.Canvas3D c3d = univ.getCanvas();
	            if (c3d != null) {
	                c3d.stopRenderer(); 
	                c3d.removeNotify(); // Kill Native Peer
	                
	                // --- THE MISSING PIECE ---
	                if (c3d instanceof ij3d.ImageCanvas3D) {
	                    ((ij3d.ImageCanvas3D)c3d).flush(); // Free the buffers!
	                }
	                // -------------------------
	            }
	        } catch (Exception e) {
	            e.printStackTrace(); 
	        }

	        univ.cleanup();
	        univ = null;
	    }

	    // 3. Flush images (Your existing logic)
	    if (impDup != null) {
	        impDup.changes = false;
	        impDup.close();
	        impDup.flush(); 
	        impDup = null;
	    }
	    
	    ij3dv = null;
	}	
}
