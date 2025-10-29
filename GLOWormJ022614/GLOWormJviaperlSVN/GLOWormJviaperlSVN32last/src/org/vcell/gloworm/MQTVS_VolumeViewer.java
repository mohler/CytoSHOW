package org.vcell.gloworm;
import java.awt.Color;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue; // ADDED for thread-safe queue
import java.util.concurrent.Future;             // ADDED for asynchronous task management

import javax.swing.SwingUtilities; // Already present, but good to ensure
import javax.vecmath.Color3f;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.Colors;
import ij.plugin.PlugIn;
import ij.plugin.SubHyperstackMaker;
import ij.plugin.frame.RoiManager;
import ij3d.Content;
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
		runVolumeViewer(imp, cellName, null, false, new Image3DUniverse(), null, "");
	}
	
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, Image3DUniverse univ) {
		runVolumeViewer(imp, cellName, assignedColorString, false, univ, null, "");
	}
	
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, Image3DUniverse univ, String outDir) {
		runVolumeViewer(imp, cellName, assignedColorString, false, univ, outDir, "");
	}
	public void runVolumeViewer(ImagePlus imp, String cellName, String assignedColorString, 
			boolean saveSingly, Image3DUniverse univ, String outDir, String scaleShiftString) {
		boolean singleSave = IJ.shiftKeyDown() || saveSingly;
		if (univ == null){
			if (this.univ == null){	
				this.univ = new Image3DUniverse();
			}
			univ = this.univ;
		}
		final Image3DUniverse finalUniv = univ;
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

				// 1. Initialize and start the dispatcher thread for parallel loading 🚀
				ResultDispatcher dispatcher = new ResultDispatcher(finalUniv);
				dispatcher.start();
				
				// The entire loop now runs NON-BLOCKING on the main plugin thread
				for (int ch=duper.getFirstC(); ch<=duper.getLastC(); ch++) {
					imp.setRoi(impRoi);
					// NOTE: impD is used below, so it must be final or effectively final if used in lambdas, 
					// but since it's passed to the dispatcher, we are safe.
					ImagePlus impD = SubHyperstackMaker.makeSubhyperstack(impDup, ""+ch, "1-"+impDup.getNSlices(), "1-"+impDup.getNFrames());
					impD.setTitle(impD.getTitle()+"_"+ch);
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

					Hashtable<String, Content> contents = finalUniv.getContentsHT();
					
					// ASYNCHRONOUS CALL: Start the heavy content creation in a background thread
					Future<Content> futureContent = finalUniv.addContentLater(impD, new Color3f(channelColor), objectName, threshold, new boolean[]{true, true, true}, binFactor, Content.SURFACE);

					// SUBMIT TO DISPATCHER: Pass all necessary variables to the dispatcher thread
					dispatcher.addTask(futureContent, channelColor, singleSave, objectName, outDir, impD);
					
					// --- CRITICAL FIX: The old if (singleSave) block is REMOVED from here. ---
					// The logic has been safely moved inside the ResultDispatcher to prevent race conditions.
					
					// --- CLEANUP LOGIC ---
					IJ.setTool(ij.gui.Toolbar.HAND);
					if (impD != imp){
						// Set changes to false so close() doesn't prompt the user to save
						impD.changes = false; 
						if (impD.getWindow() != null)
							impD.getWindow().close();
					}

				} // End of channel loop
				
				// Final cleanup after all content is added
				if (impDup != imp){
					impDup.changes = false;
					if (impDup.getWindow() != null)
						impDup.getWindow().close();
				}
				ImageJ3DViewer.select(null);
				IJ.getInstance().toFront();
			} else {  
				// Original else block (empty in the provided code)
			}

			if (!imp.getTitle().startsWith("SVV")) {
				
		        // Ensure the dialog creation is executed on the Event Dispatch Thread (EDT)
				javax.swing.SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						try {
							if (imp.getWindow()!=null) {
								imp.getWindow().setVisible(true);
								imp.getWindow().setAlwaysOnTop(false);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
				
			}
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
        // FIX 1: Add null check for impDup to prevent NullPointerException on cleanup
		if (univ!=null && univ.getWindow()!=null)
			univ.getWindow().removeWindowListener(this);
            
        if (impDup != null) {
            if (impDup.getWindow() != null)
                 impDup.getWindow().dispose();
            impDup.flush();
        }
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

	// -------------------------------------------------------------------------
		// NEW ASYNCHRONOUS DISPATCHER FOR PARALLEL CONTENT LOADING
		// -------------------------------------------------------------------------

		/**
		 * A dedicated thread to poll for completed content tasks and safely dispatch 
		 * the UI updates (select, lock, color) to the EDT.
		 */
		private class ResultDispatcher extends Thread {
		    
		    private class TaskContext {
		        final Future<Content> future;
		        final Color color;
		        final boolean singleSave;
		        final String objectName;
		        final String outDir;
		        final ImagePlus impD;

		        TaskContext(Future<Content> f, Color c, boolean sS, String oN, String oD, ImagePlus iD) { 
		            this.future = f; this.color = c; 
		            this.singleSave = sS; 
		            this.objectName = oN; 
		            this.outDir = oD;
		            this.impD = iD;
		        }
		    }
		    
		    private final ConcurrentLinkedQueue<TaskContext> taskQueue;
		    private final Image3DUniverse univ;

		    public ResultDispatcher(Image3DUniverse univ) {
		        this.taskQueue = new ConcurrentLinkedQueue<>();
		        this.univ = univ;
		        this.setDaemon(true); 
		    }

		    public void addTask(Future<Content> future, Color color, boolean singleSave, String objectName, String outDir, ImagePlus impD) {
		        taskQueue.add(new TaskContext(future, color, singleSave, objectName, outDir, impD));
		    }

		    @Override
		    public void run() {
		    	System.out.println("ResultDispatcher thread is active..."); // <--- ADD THIS LINE
		            while (!taskQueue.isEmpty() || (univ.getWindow() != null && univ.getWindow().isShowing())) {
		            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

		            TaskContext context = taskQueue.peek();

		            if (context != null && context.future.isDone()) {
		                taskQueue.remove(); 

		                try {
		                    final Content loadedContent = context.future.get();
		                    final Color finalChannelColor = context.color;

		                    if (context.singleSave) {
		                        // === 1. SINGLE SAVE LOGIC (Runs on ResultDispatcher Thread) ===
		                        Hashtable<String, Content> newestContent = new Hashtable<String, Content>();
		                        newestContent.put(""+context.objectName, loadedContent); 
		                        
		                        String finalOutDir = context.outDir == null ? IJ.getDirectory("home") : context.outDir;
		                        String fileName = context.impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_") 
		                                        + "_" + context.objectName.replaceAll(":","").replaceAll("(/|\\s+)","") 
		                                        + "_" + 0 + ".obj";
		                                        
		                        File outputFile = new File(finalOutDir + File.separator + fileName);
		                                        
		                        MeshExporter.saveAsWaveFront(newestContent.values(), 
		                                outputFile, 
		                                univ.getStartTime(), univ.getEndTime(), true);

	                        	// ADDED: Check if the file was created successfully and log the result
		                        if (outputFile.exists() && outputFile.length() > 0) {
		                            System.out.println("FACTORY MODE SUCCESS: Saved content '"+context.objectName+"' to: " + outputFile.getAbsolutePath());
		                        } else {
		                            System.err.println("FACTORY MODE FAILURE: Failed to save content '"+context.objectName+"'. File not created or empty at: " + outputFile.getAbsolutePath());
		                            System.err.println("Target Directory Exists? " + new File(finalOutDir).exists());
		                        }

		                        // === 2. REMOVAL UI ACTION (Dispatch to EDT) ===
		                        SwingUtilities.invokeLater(() -> {
		                            univ.removeContent(loadedContent.getName(), true);
		                        });

		                    } else {
		                        // === 3. EXPLORATORY MODE LOCK LOGIC (Dispatch to EDT) ===
		                        SwingUtilities.invokeLater(() -> {
		                            if (loadedContent != null) {
		                                // Select and apply color
		                                univ.select(loadedContent, true);
		                                
		                                try {
		                                    float r = Integer.parseInt(""+finalChannelColor.getRed()) / 256f;
		                                    float g = Integer.parseInt(""+finalChannelColor.getGreen()) / 256f;
		                                    float b = Integer.parseInt(""+finalChannelColor.getBlue()) / 256f;
		                                    loadedContent.setColor(new Color3f(r, g, b));
		                                } catch(NumberFormatException e) {
		                                    loadedContent.setColor(null);
		                                }
		                                
		                                // Lock the content
		                                loadedContent.setLocked(true); 
		                            }
		                        });
		                    }
		                } catch (Exception e) {
		                    System.err.println("Error processing content result or save: " + e.getMessage());
		                }
		            }
		        }
		    }
		}
}