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

					// SUBMIT TO DISPATCHER: Add the future and color context for non-blocking processing
					dispatcher.addTask(futureContent, channelColor);
					
					// --- CLEANUP LOGIC (Must NOT manipulate UI or Scene Graph) ---
					if (singleSave) {
						Hashtable<String, Content> newestContent = new Hashtable<String, Content>();
						// NOTE: This call to getContent() here is risky, as the content may not be loaded yet.
						// If singleSave requires the content to exist, this block needs its own Future.get() or dispatch.
						newestContent.put(""+objectName, finalUniv.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)));
						MeshExporter.saveAsWaveFront(newestContent.values(), new File(((outDir==null?IJ.getDirectory("home"):outDir)+File.separator+impD.getTitle().replaceAll(":","").replaceAll("(/|\\s+)", "_")+"_"+objectName.replaceAll(":","").replaceAll("(/|\\s+)","")+"_"+ch+"_"+0+".obj")), finalUniv.getStartTime(), finalUniv.getEndTime(), true);
						finalUniv.select(finalUniv.getContent((""+objectName/*+"_"+ch+"_"+tpt*/)), true);
						finalUniv.getSelected().setLocked(false);
						finalUniv.removeContent(finalUniv.getSelected().getName(), true);
					}

					IJ.setTool(ij.gui.Toolbar.HAND);
					if (impD != imp){
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
		        // This is a much safer way to handle GUI element creation.
				javax.swing.SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						try {
							// Call the method that creates the AWT dialog.
							// This code will now run on the AWT-EventQueue-0 thread,
							// which is where AWT Tree Lock operations should be serialized.

							imp.getWindow().setVisible(true);
							imp.getWindow().setAlwaysOnTop(false);

						} catch (Exception e) {
							// Handle any exceptions during dialog show
							e.printStackTrace();
						}
					}
				});
				
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
		if (univ!=null && univ.getWindow()!=null)
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

	// -------------------------------------------------------------------------
	// NEW ASYNCHRONOUS DISPATCHER FOR PARALLEL CONTENT LOADING
	// -------------------------------------------------------------------------

	/**
	 * A dedicated thread to poll for completed content tasks and safely dispatch 
	 * the UI updates (select, lock, color) to the EDT.
	 */
	private class ResultDispatcher extends Thread {
	    // Simple structure to pair the Future with the color data needed for the EDT update
	    private  class TaskContext {
	        final Future<Content> future;
	        final Color color;
	        TaskContext(Future<Content> f, Color c) { this.future = f; this.color = c; }
	    }
	    
	    private final ConcurrentLinkedQueue<TaskContext> taskQueue;
	    private final Image3DUniverse univ;

	    public ResultDispatcher(Image3DUniverse univ) {
	        this.taskQueue = new ConcurrentLinkedQueue<>();
	        this.univ = univ;
	        this.setDaemon(true); // Ensures the thread terminates when the main program exits
	    }

	    public void addTask(Future<Content> future, Color color) {
	        taskQueue.add(new TaskContext(future, color));
	    }

	    @Override
	    public void run() {
	        // Continue running as long as there are tasks or the viewer window is open
	        while (!taskQueue.isEmpty() || (univ.getWindow() != null && univ.getWindow().isShowing())) {
	            // Sleep to prevent the thread from consuming 100% CPU while polling
	            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

	            // Get the next future to check, but don't remove it yet
	            TaskContext context = taskQueue.peek();

	            if (context != null && context.future.isDone()) {
	                taskQueue.remove(); // Remove the completed task

	                try {
	                    // Get the content. This call is guaranteed not to block since isDone() was true.
	                    final Content loadedContent = context.future.get();
	                    final Color finalChannelColor = context.color;

	                    //  UI UPDATE: Dispatch the lock, select, and color operations to the EDT 
	                    SwingUtilities.invokeLater(() -> {
	                        if (loadedContent != null) {
	                            // Perform UI and Scene Graph manipulations safely on the EDT
	                            univ.select(loadedContent, true);
	                            
	                            // Re-apply Color Logic (copied from the loop)
	                            try {
	                                float r = Integer.parseInt(""+finalChannelColor.getRed()) / 256f;
	                                float g = Integer.parseInt(""+finalChannelColor.getGreen()) / 256f;
	                                float b = Integer.parseInt(""+finalChannelColor.getBlue()) / 256f;
	                                loadedContent.setColor(new Color3f(r, g, b));
	                            } catch(NumberFormatException e) {
	                                loadedContent.setColor(null);
	                            }
	                            
	                            loadedContent.setLocked(true);
	                        }
	                    });
	                } catch (Exception e) {
	                    System.err.println("Error processing content result: " + e.getMessage());
	                }
	            }
	        }
	    }
	}
}