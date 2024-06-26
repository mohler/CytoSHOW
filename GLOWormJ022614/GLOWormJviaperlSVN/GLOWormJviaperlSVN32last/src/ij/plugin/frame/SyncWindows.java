package ij.plugin.frame;
import ij.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


/** This class "synchronizes" mouse input in multiple windows. Once
	several windows are synchronized, mouse events in any one of the
	synchronized windows are propagated to the others.

	Note, the notion of synchronization use by the SyncWindows class
	here (i.e. multiple windows that all get the same mouse input) is
	somewhat different than the use of the synchronize keyword in the
	Java language. (In Java, synchronize has to do w/ critical section
	access by multiple threads.)
<p>
	Optionally passes on change of z-slice of a stack to other stacks;

	Optionally translates positions to different windows via offscreen
	coordinates, i.e. correctly translates coordinates to windows with a
	different zoom;

	Updates the list of windows by click of a button;

@author Patrick Kelly <phkelly@ucsd.edu>;
Improved GUI, support of image coordinates and z-slices by Joachim Walter <correspondence@walter-witzenhausen.de>

*/
public class SyncWindows extends PlugInFrame implements
	ActionListener, MouseMotionListener, MouseListener, DisplayChangeListener,
	ItemListener, ImageListener, ListSelectionListener, MouseWheelListener {

	/** Indices of synchronized image windows are maintained in this Vector. */
	protected Vector vwins = null;

	/* Manage mouse information.
	   The mouse coordinates x and y are only changed by the methods of the
	   MousMotionListener Interface. They are used by the MouseListener methods.
	   This way, the coordinates that were valid before a MouseListener event
	   (e.g. a Zoom) happened can be accessed. */
	protected int oldX, oldY;
	protected int x=0;
	protected int y=0;

	/** List of currently displayed windows retrieved from ImageJ
	   window manager. */
	protected JList wList;

	/** Panel for GUI */
	protected JPanel panel;

	/** Checkboxes for user control. */
	protected JCheckBox cCursor, cSlice, cChannel, cFrame, cCoords, cScaling;

	/** Buttons for user control. */
	protected JButton bSyncAll, bUnsyncAll;

	/** Hashtable to map list ids to image window ids. */
	protected Vector vListMap;
	
	/** reference to current instance of ImageJ (to avoid repeated IJ.getInstance() s) */
	protected final ImageJ ijInstance;

	/*	Variables to store display values of current window.
	 *	Translation by screenX/Y() and offScreenX/Y() does not work,
	 *	because current window receives events (e.g. zooming) before this plugin.
	 */
	private double currentMag = 1;
	private Rectangle currentSrcRect = new Rectangle(0,0,400,400);
	
	// Control size of cursor box and clipping region. These could be
	// changed to tune performance.
	static final int RSZ = 16;
	static final int SZ = RSZ/2;
	static final int SCALE = 3;
	
	private static SyncWindows instance;
	private static Point location;
	
	//--------------------------------------------------
	/** Create window sync frame. Frame is shown via call to show() or
		by invoking run method.	 */
	public SyncWindows() {
		this("Synchronize Windows");
	}

	public SyncWindows(String s) {
		super(s);
		ijInstance = IJ.getInstance();
		if (instance!=null) {
			WindowManager.toFront(instance);
			return;
		}
		instance = this;
		panel = controlPanel();
		add(panel);
		pack();
		setResizable(false);
		IJ.register(this.getClass());
		if (location==null)
			location = getLocation();
		else
			setLocation(location);
		updateWindowList();
		WindowManager.addWindow(this);
		ImagePlus.addImageListener(this);
		show();
	}
	
	public static void setC(ImageWindow source, int channel) {
		SyncWindows syncWindows = instance;
		if (syncWindows==null || !syncWindows.synced(source))
			return;
		DisplayChangeEvent event=new DisplayChangeEvent(source, DisplayChangeEvent.CHANNEL, channel);
		syncWindows.displayChanged(event);
	}

	public static void setZ(ImageWindow source, int slice) {
		SyncWindows syncWindows = instance;
		if (syncWindows==null || !syncWindows.synced(source))
			return;
		DisplayChangeEvent event=new DisplayChangeEvent(source, DisplayChangeEvent.Z, slice);
		syncWindows.displayChanged(event);
	}

	public static void setT(ImageWindow source, int frame) {
		SyncWindows syncWindows = instance;
		if (syncWindows==null || !syncWindows.synced(source))
			return;
		DisplayChangeEvent event=new DisplayChangeEvent(source, DisplayChangeEvent.T, frame);
		syncWindows.displayChanged(event);
	}
	
	private boolean synced(ImageWindow source) {
		if (source==null || vwins==null)
			return false;
		ImagePlus imp = source.getImagePlus();
		if (imp==null)
			return false;
		return vwins.contains(new Integer(imp.getID()));
	}

	// --------------------------------------------------
	/**
	* Method to pass on changes of the z-slice of a stack.
	*/
	public void displayChanged(DisplayChangeEvent e) {
		if (vwins == null) return;

		Object source = e.getSource();
		int type = e.getType();
		int value = e.getValue();

		ImagePlus imp;
		ImageWindow iw;

		// Current imagewindow
		ImageWindow iwc = WindowManager.getCurrentImage().getWindow();

		// pass on only if event comes from current window
		if (!iwc.equals(source)) return;

		// Change channel in other synchronized hyperstacks.
		if (cChannel.isSelected() && type==DisplayChangeEvent.CHANNEL) {
			for (int n=0; n<vwins.size();++n) {
				imp = getImageFromVector(n);
				if (imp != null) {
					iw = imp.getWindow();
					if( !iw.equals(source)) {
						if (iw instanceof StackWindow)
							((StackWindow)iw).setPosition(value, imp.getSlice(), imp.getFrame());
					}
				}
			}	
		}		 
		
		// Change slices in other synchronized windows.
		if (cSlice.isSelected() && type==DisplayChangeEvent.Z) {
			for(int n=0; n<vwins.size();++n) {
				imp = getImageFromVector(n);
				if (imp != null) {
					iw = imp.getWindow();
	
					int stacksize = imp.getStackSize();
					if( !iw.equals(source) && (iw instanceof StackWindow) ) {
						((StackWindow)iw).setPosition(imp.getChannel(), value, imp.getFrame());
					}
				}
			}
		}

		// Change frame in other synchronized Image5Ds.
		if (cFrame.isSelected() && type==DisplayChangeEvent.T) {
			for(int n=0; n<vwins.size();++n) {
				imp = getImageFromVector(n);
				if (imp != null) {
					iw = imp.getWindow();
					if (!iw.equals(source)) {
						if (iw instanceof StackWindow)
							((StackWindow)iw).setPosition(imp.getChannel(), imp.getSlice(), value);
					}
				}
			}   
		}

		// Store srcRect, Magnification and others of current ImageCanvas
		ImageCanvas2 icc = iwc.getCanvas();
		storeCanvasState(icc);
	}


	// --------------------------------------------------
	//
	// MouseMotionListener interface methods.
	//

	// --------------------------------------------------
	/** Draws the "synchronize" cursor in each of the synchronized
	 windows.  */
	public void mouseMoved(MouseEvent e) {
		if (!cCursor.isSelected()) return;
		if (vwins == null) return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;
		Point p;
		Point oldp;
		Rectangle rect;

		oldX = x; oldY = y;
		x = e.getX();
		y = e.getY();

		p = new Point(x, y);
		rect = boundingRect(x,y,oldX,oldY);

		// get ImageCanvas2 that received event
		ImageCanvas2 icc = (ImageCanvas2) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getParent().getParent().getParent().getParent();

		// Draw new cursor box in each synchronized window.
		// and pass on mouse moved event
		for(int n=0; n<vwins.size();++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);	   
			if (imp != null) {				
				iw = imp.getWindow();
				ic = iw.getCanvas();			  
				if (cCoords.isSelected() && iw != iwc) {
					p = getMatchingCoords(ic, icc, x, y);
					oldp = getMatchingCoords(ic, icc, oldX, oldY);
					rect = boundingRect(p.x, p.y, oldp.x, oldp.y);
				} else {
					p.x = x;
					p.y = y;
					rect = boundingRect(x,y,oldX,oldY);
				}

				// For PolygonRoi the cursor would overwrite the indicator lines.
				Roi roi = imp.getRoi();
				if (! (roi != null && roi instanceof PolygonRoi && roi.getState() == Roi.CONSTRUCTING) ) {
					drawSyncCursor(ic,rect, p.x, p.y);
				}

				if(iw != iwc)
					ic.mouseMoved(adaptEvent(e, ic, p));	   
			}		   
		}
		// Display correct values in ImageJ statusbar
		iwc.getImagePlus().mouseMoved(icc.offScreenX(x), icc.offScreenY(y));

		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/** Propagate mouse dragged events to all synchronized windows.	 */
	public void mouseDragged(MouseEvent e) {
		if (!cCursor.isSelected()) return;
		if (vwins == null) return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;
		Point p;
		Point oldp;
		Rectangle rect;

		oldX = x; oldY = y;
		x = e.getX();
		y = e.getY();

		p = new Point(x, y);
		rect = boundingRect(x,y,oldX,oldY);

		// get ImageCanvas2 that received event
		ImageCanvas2 icc = (ImageCanvas2) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getParent().getParent().getParent().getParent();

		// Draw new cursor box in each synchronized window.
		// and pass on mouse dragged event
		for(int n=0; n<vwins.size();++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();

				if (cCoords.isSelected() && iw != iwc) {
					p = getMatchingCoords(ic, icc, x, y);
					oldp = getMatchingCoords(ic, icc, oldX, oldY);
					rect = boundingRect(p.x, p.y, oldp.x, oldp.y);
				} else {
					p = new Point(x, y);
					rect = boundingRect(x,y,oldX,oldY);
				}

				// For PolygonRoi the cursor would overwrite the indicator lines.
				Roi roi = imp.getRoi();
				if (! (roi != null && roi instanceof PolygonRoi && roi.getState() == Roi.CONSTRUCTING) )
					drawSyncCursor(ic,rect, p.x, p.y);

				if(iw != iwc)
					ic.mouseDragged(adaptEvent(e, ic, p));
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}


	// --------------------------------------------------
	//
	// MouseListener interface
	//

	// --------------------------------------------------
	/** Propagate mouse clicked events to all synchronized windows. */
	public void mouseClicked(MouseEvent e) {
		if (!cCursor.isSelected()) return;
		if (vwins == null) return;
		// prevent popups popping up in all windows on right mouseclick
		if (Toolbar.getToolId()!= Toolbar.MAGNIFIER && 
			(e.isPopupTrigger() || (e.getModifiers() & MouseEvent.META_MASK)!=0)) return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;
		Point p;

		p = new Point(x,y);

		// get ImageCanvas2 that received event
		ImageCanvas2 icc = (ImageCanvas2) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getParent().getParent().getParent().getParent();

		for(int n=0; n<vwins.size();++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				if(iw != iwc) {
					ic = iw.getCanvas();
					if (cCoords.isSelected()) {
						p = getMatchingCoords(ic, icc, x, y);
					}
					ic.mouseClicked(adaptEvent(e, ic, p));
				}
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/** Propagate mouse entered events to all synchronized windows. */
	public void mouseEntered(MouseEvent e) {	
		if (!cCursor.isSelected()) return;
		if (vwins == null) return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;
		Point p;

		p = new Point(x,y);

		// get ImageCanvas2 that received event
		ImageCanvas2 icc = (ImageCanvas2) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getParent().getParent().getParent().getParent();

		for(int n=0; n<vwins.size();++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {		
				iw = imp.getWindow();
				if(iw != iwc) {
					ic = iw.getCanvas();

					if (cCoords.isSelected()) {
						p = getMatchingCoords(ic, icc, x, y);
					}
					ic.mouseEntered(adaptEvent(e, ic, p));
				}				
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}


	// --------------------------------------------------
	/** Propagate mouse exited events to all synchronized windows. */
	public void mouseExited(MouseEvent e) {
		if (!cCursor.isSelected()) return;
		if (vwins == null) return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;
		Point p;
		Rectangle rect;

		p = new Point(x,y);
		rect = boundingRect(x,y,x,y);

		// get ImageCanvas2 that received event
		ImageCanvas2 icc = (ImageCanvas2) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getParent().getParent().getParent().getParent();

		for(int n=0; n<vwins.size();++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {				
				iw = imp.getWindow();
				ic = iw.getCanvas();
				
				if (cCoords.isSelected() && iw != iwc) {
					p = getMatchingCoords(ic, icc, x, y);
					rect = boundingRect(p.x, p.y, p.x, p.y);
				} else {
					p.x = x;
					p.y = y;
					rect = boundingRect(x,y,x,y);
				}

				// Repaint to get rid of cursor.
				Graphics g = ic.getGraphics();
				try {
					g.setClip(rect.x,rect.y,rect.width,rect.height);
					ic.paint(g);
				} finally {
					// free up graphics resources
					g.dispose();
				}

				if(iw != iwc)
					ic.mouseExited(adaptEvent(e, ic, p));					 
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}


	// --------------------------------------------------
	/** Propagate mouse pressed events to all synchronized windows. */
	public void mousePressed(MouseEvent e) {
		if (!cCursor.isSelected()) return;
		if (vwins == null) return;
		// prevent popups popping up in all windows on right mouseclick
		if (Toolbar.getToolId()!= Toolbar.MAGNIFIER && 
			(e.isPopupTrigger() || (e.getModifiers() & MouseEvent.META_MASK)!=0)) return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;
		Point p;

		p = new Point(x,y);

		// Current window already received mouse event.
		// get ImageCanvas2 that received event
		ImageCanvas2 icc = (ImageCanvas2) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getParent().getParent().getParent().getParent();

		for(int n=0; n<vwins.size();++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();
				// Repaint to get rid of sync indicator.
				ic.paint(ic.getGraphics());
				if(iw != iwc) {
					ic = iw.getCanvas();
					if (cCoords.isSelected()) {
						p = getMatchingCoords(ic, icc, x, y);
					}
					ic.mousePressed(adaptEvent(e, ic, p));
				}
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}

	// --------------------------------------------------
	/** Propagate mouse released events to all synchronized
		windows. */
	public void mouseReleased(MouseEvent e) {
		if (!cCursor.isSelected()) return;
		if (vwins == null) return;
		// prevent popups popping up in all windows on right mouseclick
		if (Toolbar.getToolId()!= Toolbar.MAGNIFIER && 
			(e.isPopupTrigger() || (e.getModifiers() & MouseEvent.META_MASK)!=0)) return;
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;

		int xloc = e.getX();
		int yloc = e.getY();
		Point p = new Point(xloc, yloc);
		Rectangle rect = boundingRect(xloc, yloc, xloc, yloc);


		// get ImageCanvas2 that received event
		ImageCanvas2 icc = (ImageCanvas2) e.getSource();
		ImageWindow iwc = (ImageWindow) icc.getParent().getParent().getParent().getParent();

		for(int n=0; n<vwins.size();++n) {
			// to keep ImageJ from freezing when a mouse event is processed on exit
			if (ijInstance.quitting()) {
				return;
			}
			imp = getImageFromVector(n);
			if (imp != null) {
				iw = imp.getWindow();
				ic = iw.getCanvas();

				if (cCoords.isSelected()) {
					p = getMatchingCoords(ic, icc, xloc, yloc);
					rect = boundingRect(p.x, p.y, p.x, p.y);
				}

				// Redraw to make sure sync cursor is drawn.
				// For PolygonRoi the cursor would overwrite the indicator lines.
				Roi roi = imp.getRoi();
				if (! (roi != null && roi instanceof PolygonRoi && roi.getState() == Roi.CONSTRUCTING) )
					drawSyncCursor(ic,rect, p.x, p.y);
				if(iw != iwc)
					ic.mouseReleased(adaptEvent(e, ic, p));
			}
		}
		// Store srcRect, Magnification and others of current ImageCanvas
		storeCanvasState(icc);
	}


	// --------------------------------------------------
	/** Implementation of ActionListener interface. */
	public void actionPerformed(ActionEvent e) {
		// Determine which button was pressed.
		Object source = e.getSource();
		if (source instanceof JButton) {
			JButton bpressed = (JButton)source;

			 if(bpressed.getLabel() == "Synchronize All") {
				if (wList == null) return;
				// Select all items on list.
				Vector v = new Vector();
				Integer I;
				for(int i=0; i<wList.getModel().getSize();++i) {
					wList.setSelectedIndex(i);
					I = (Integer)vListMap.elementAt(i);
					v.addElement(I);
				}
				addWindows(v);
			}
			else if(bpressed==bUnsyncAll) {
				removeAllWindows();
			}
		} else if (wList != null && source == wList) {
			// Doubleclick on entry in wList
			addSelections();
		}
	}

	// --------------------------------------------------
	/** Item Listener method
	 */
	public void itemStateChanged(ItemEvent e) {
	// safest way to get matching of selected windows in list and in plugin:
	// deselect all windows in plugin and then select all in list
	// A List often does not do what you expect.

		if (wList != null && e.getSource() == wList) {
			if(vwins != null) {
			// unsynchronize all windows and remove from window list
				Integer I;
				for(int n = 0; n<vwins.size();++n) {
					I = (Integer)vwins.elementAt(n);
					removeWindow(I);
				}
				vwins.removeAllElements();
			}
			addSelections();
		}
		
		if (cCoords != null && e.getSource() == cCoords) {
			if (cScaling != null && e.getStateChange() == ItemEvent.DESELECTED) 
					cScaling.setSelected(false);
		}	
		
		if (cScaling != null && e.getSource() == cScaling) {
			if (cCoords != null && e.getStateChange() == ItemEvent.SELECTED) 
					cCoords.setSelected(true);
		}
	}


	// --------------------------------------------------
	/** Override parent windowClosing method to clean up synchronized
	resources on exit. */
	public void windowClosing(WindowEvent e) {
		if(e.getSource() == this) {
			removeAllWindows();
			ImagePlus.removeImageListener(this);
			close();	
		}
	}
	
	/** Implementation of ImageListener interface: update window list, if image is opened or closed */
	public void imageOpened(ImagePlus imp) {
		updateWindowList();
	}

	/** Implementation of ImageListener interface: update window list, if image is opened or closed */
	public void imageClosed(ImagePlus imp) {
		updateWindowList();
	}

	public void imageUpdated(ImagePlus imp) {
	}

	// --------------------------------------------------
	/** Build window list display and button controls.
	 *	Create Hashtable that connects list entries to window IDs.*/
	protected JPanel controlPanel() {

		JPanel p = new JPanel();
		BorderLayout layout = new BorderLayout();
		layout.setVgap(3);
		p.setLayout(layout);
		p.add(buildWindowList(), BorderLayout.NORTH,0);
		p.add(buildControlPanel(), BorderLayout.CENTER,1);

		return p;
	}

	// --------------------------------------------------
	/** Builds list of open ImageWindows */
	protected Component buildWindowList() {
		ImagePlus img;
		ImageWindow iw;

		// get IDList from WindowManager
		int[] imageIDs = WindowManager.getIDList();		  
		
		if(imageIDs != null) {
			int size;
			if (imageIDs.length < 10) {
				size = imageIDs.length;
			} else {
				size = 10;
			} 
			
			// Initialize window list and vector that maps list entries to window IDs.
			DefaultListModel<String> listModel = new DefaultListModel<String>();
			wList = new JList<String>(listModel);
			vListMap = new Vector();

			// Add Windows to list, select windows, that previously were selected
			for(int n=0; n<imageIDs.length;++n) {
				vListMap.addElement(new Integer(imageIDs[n]));
				listModel.addElement(WindowManager.getImage(imageIDs[n]).getTitle());
				if ( vwins != null && vwins.contains(new Integer(imageIDs[n])) ) {
					wList.setSelectedIndex(n);
				}
			}
			// clean vector of selected images (vwins) from images that have been closed,
			if (vwins != null && vwins.size() != 0) {
				for (int n=0; n<vwins.size(); ++n) {
					if(! vListMap.contains(vwins.elementAt(n))) {
						vwins.removeElementAt(n);
						n -= 1;
					}
				}
			}

			wList.setPrototypeCellValue("012345678901234567890123456789");		
			wList.addListSelectionListener(this);
			wList.addKeyListener(IJ.getInstance());
			wList.addMouseListener(this);
			wList.addMouseWheelListener(this);
			return (Component)wList;
		}
		else {
			Label label = new Label("No windows to select.");
			wList = null;
			vListMap = null;
			vwins = null;
			return (Component)label;
		}
	}

	// --------------------------------------------------
	/** Builds panel containing control buttons. */
	protected Panel buildControlPanel() {
		GridLayout layout = new GridLayout(4,2);
		layout.setVgap(2);
		layout.setHgap(2);
		Panel p = new Panel(layout);

		// Checkbox: synchronize cursor
		cCursor = new JCheckBox("Sync Cursor", true);
		p.add(cCursor);

		// Checkbox: propagate slice
		cSlice = new JCheckBox("Sync z-Slices",true);
		p.add(cSlice);

//		TODO: Give functionality to Synchronize Channels and Synchronize t-Frames checkboxes.
		
		// Checkbox: synchronize channels (for hyperstacks)
		cChannel = new JCheckBox("Sync Channels", true);
		p.add(cChannel);
		
		// Checkbox: synchronize time-frames (for hyperstacks)
		cFrame = new JCheckBox("Sync t-Frames", true);
		p.add(cFrame);
		
		// Checkbox: image coordinates
		cCoords = new JCheckBox("Image Coordinates", true);
		cCoords.addItemListener(this);
		p.add(cCoords);
 
		// Checkbox: image scaling (take pixel scale and offset into account)
		cScaling = new JCheckBox("Image Scaling", false);
		cScaling.addItemListener(this);
		p.add(cScaling);
		
		

		// Synchronize all windows.
		bSyncAll = new JButton("Synchronize All");
		bSyncAll.addActionListener(this);
		p.add(bSyncAll);

		// Unsynchronize all windows.
		bUnsyncAll = new JButton("Unsynchronize All");
		bUnsyncAll.addActionListener(this);
		p.add(bUnsyncAll);

		return p;
	}

	// --------------------------------------------------
	/** Compute bounding rectangle given current and old cursor
	locations. This is used to determine what part of image to
	redraw. */
	protected Rectangle boundingRect(int x, int y,
				   int oldX, int oldY) {
		int dx = Math.abs(oldX - x)/2;
		int dy = Math.abs(oldY - y)/2;

		int xOffset = dx + SCALE * SZ;
		int yOffset = dy + SCALE * SZ;

		int xCenter = (x + oldX)/2;
		int yCenter = (y + oldY)/2;

		int xOrg = Math.max(xCenter - xOffset,0);
		int yOrg = Math.max(yCenter - yOffset,0);

		int w = 2 * xOffset;
		int h = 2 * yOffset;

		return new Rectangle(xOrg, yOrg, w, h);
	}

	/* Update the List of Windows in the GUI. Used by the "Update" button. */
	protected void updateWindowList() {	  
		// Don't build a new window list, while the old one is removed.
		// When an StackWindow is replaced by an OpenStackWindow, updateWindowList
		// is called again and the other components in the panel are removed, also.
		Component newWindowList = buildWindowList();
		panel.remove(0);
		panel.add(newWindowList,BorderLayout.NORTH,0);
		pack();
	}
	

	// --------------------------------------------------
	private void addSelections() {
		if(wList == null) return; // nothing to select

		int[] listIndexes = wList.getSelectedIndices();
		Integer I;

		Vector v = new Vector();
		for(int n=0; n<listIndexes.length;++n) {
			I = (Integer)vListMap.elementAt(listIndexes[n]);
			v.addElement(I);
		}

		addWindows(v);
	}

	// --------------------------------------------------
	/** Adds "this" object as mouse listener and mouse motion listener
	to each of the windows in input array.	*/
	private void addWindows(Vector v) {
		Integer I;
		ImagePlus imp;
		ImageWindow iw;

		//	Handle initial case of no windows.
		if(vwins == null && v.size() > 0)
			vwins = new Vector();

		// Add all windows in vector to synchronized window list.
		for(int n=0; n<v.size();++n) {
			I = (Integer)v.elementAt(n);

			// Make sure input window is not already on list.
			if(!vwins.contains(I)) {
				imp = WindowManager.getImage(I.intValue());
				if (imp != null) {
					iw = imp.getWindow();
					iw.getCanvas().addMouseMotionListener(this);
					iw.getCanvas().addMouseListener(this);
					vwins.addElement(I);
				}
			}
		}
	}

	// --------------------------------------------------
	/** Adds "this" object as mouse listener and mouse motion listener
	to each of the window for imp.	*/
	public void addImp(ImagePlus imp) {
		Integer I;
		ImageWindow iw;

		//	Handle initial case of no windows.
		if(vwins == null && imp!=null)
			vwins = new Vector();

		// Add imp to synchronized window list.
		// Make sure input window is not already on list.
		if(imp!=null && !vwins.contains(imp.getID())) {
			iw = imp.getWindow();
			iw.getCanvas().addMouseMotionListener(this);
			iw.getCanvas().addMouseListener(this);
			vwins.addElement(imp.getID());

		}

	}


	// --------------------------------------------------
	private void removeAllWindows() {
		if(vwins != null) {
			Integer I;
			for(int n = 0; n<vwins.size();++n) {
				I = (Integer)vwins.elementAt(n);
				removeWindow(I);
			}
			// Remove all windows from window list.
			vwins.removeAllElements();
		}

		// Deselect all elements on list (if present).
		if (wList == null) return;
		wList.clearSelection();
	}


	// --------------------------------------------------
	/** Remove "this" object as mouse listener and mouse motion
	*	listener from the window with ID I.
	*/
	private void removeWindow(Integer I) {
		ImagePlus imp;
		ImageWindow iw;
		ImageCanvas2 ic;

		imp = WindowManager.getImage(I.intValue());
		if (imp != null) {
			iw = imp.getWindow();
			if (iw != null) {	
				ic = iw.getCanvas();
				if (ic != null) {
						ic.removeMouseListener(this);
						ic.removeMouseMotionListener(this);
						// Repaint to get rid of sync indicator.
						ic.paint(ic.getGraphics()); 
				}		
			}
		}
	}

	// --------------------------------------------------
	/** Draw cursor that indicates windows are synchronized. */
	private void drawSyncCursor(ImageCanvas2 ic, Rectangle rect,
				int x, int y) {
		int xpSZ = x+SZ;
		int xmSZ = x-SZ;
		int ypSZ = y+SZ;
		int ymSZ = y-SZ;
		int xp2 = x+2;
		int xm2 = x-2;
		int yp2 = y+2;
		int ym2 = y-2;
		Graphics g = ic.getGraphics();

		try {
			g.setClip(rect.x,rect.y,rect.width,rect.height);
			ic.paint(g);
			g.setColor(Color.red);
//			  g.drawRect(x-SZ,y-SZ,RSZ,RSZ);
			g.drawLine(xmSZ, ymSZ, xm2, ym2);
			g.drawLine(xpSZ, ypSZ, xp2, yp2);
			g.drawLine(xpSZ, ymSZ, xp2, ym2);
			g.drawLine(xmSZ, ypSZ, xm2, yp2);
		}
		finally {
			// free up graphics resources
			g.dispose();
		}
	}

	/** Store srcRect and Magnification of the currently active ImageCanvas2 ic */
	private void storeCanvasState(ImageCanvas2 ic) {
		currentMag = ic.getMagnification();
		currentSrcRect = new Rectangle(ic.getSrcRect());
	}
	
	// --------------------------------------------------
	/** Get ImagePlus from Windows-Vector vwins. */
	public ImagePlus getImageFromVector(int n) {
		if (vwins == null || n<0 || vwins.size() < n+1) return null;

		ImagePlus imp;
		imp = WindowManager.getImage(((Integer)vwins.elementAt(n)).intValue());
		return imp;
	}	 
	
	/** Get the title of image n from Windows-Vector vwins. If the image ends with
	 *	.tif, the extension is removed. */
	public String getImageTitleFromVector(int n) {
		if (vwins == null || n<0 || vwins.size() < n+1) return "";

		ImagePlus imp;
		imp = WindowManager.getImage(((Integer)vwins.elementAt(n)).intValue());
		String title = imp.getTitle();
		if (title.length()>=4 && (title.substring(title.length()-4)).equalsIgnoreCase(".tif")) {
			title = title.substring(0, title.length()-4);
		} else if (title.length()>=5 && (title.substring(title.length()-5)).equalsIgnoreCase(".tiff")) {
			title = title.substring(0, title.length()-5);
		}		 
		return title;
	}

/** Get index of "image" in vector of synchronized windows, if image is in vector.
 * Else return -1. 
 */	   
	public int getIndexOfImage(ImagePlus image) {
		int index = -1;
		ImagePlus imp;
		if (vwins == null || vwins.size() == 0) 
			return index;
			
		for (int n=0; n<vwins.size(); n++){
			imp = WindowManager.getImage(((Integer)vwins.elementAt(n)).intValue());
			if (imp == image) {
				index = n;
				break;
			}
		}
		return index;
	}

	// --------------------------------------------------
	/** Get Screen Coordinates for ImageCanvas2 ic matching
	 *	the OffScreen Coordinates of the current ImageCanvas.
	 *	(srcRect and magnification stored after each received event.)
	 *	Input: The target ImageCanvas, the current ImageCanvas, 
	 *	x-ScreenCoordinate for current Canvas, y-ScreenCoordinate for current Canvas
	 *	If the "ImageScaling" checkbox is selected, Scaling and Offset 
	 *	of the images are taken into account. */
	protected Point getMatchingCoords(ImageCanvas2 ic, ImageCanvas2 icc, int x, int y) {

		double xOffScreen = currentSrcRect.x + (x/currentMag);
		double yOffScreen = currentSrcRect.y + (y/currentMag);

		if (cScaling.isSelected()) {
			Calibration cal = ((ImageWindow)ic.getParent().getParent().getParent().getParent()).getImagePlus().getCalibration();
			Calibration curCal = ((ImageWindow)icc.getParent().getParent().getParent().getParent()).getImagePlus().getCalibration();

			xOffScreen = ((xOffScreen-curCal.xOrigin)*curCal.pixelWidth)/cal.pixelWidth+cal.xOrigin;
			yOffScreen = ((yOffScreen-curCal.yOrigin)*curCal.pixelHeight)/cal.pixelHeight+cal.yOrigin;
		}			 

		int xnew = ic.screenXD(xOffScreen);
		int ynew = ic.screenYD(yOffScreen);

		return new Point(xnew, ynew);
	}

	// --------------------------------------------------
	/** Makes a new mouse event from MouseEvent e with the Canvas c
	 *	as source and the coordinates of Point p as X and Y.*/
	private MouseEvent adaptEvent(MouseEvent e, JComponent c, Point p) {
		return new MouseEvent(c, e.getID(), e.getWhen(), e.getModifiers(),
		   p.x, p.y, e.getClickCount(), e.isPopupTrigger());

	}
	    
	public Insets getInsets() {
		Insets i = super.getInsets();
		return new Insets(i.top+10, i.left+10, i.bottom+10, i.right+10);
	}

	public void close() {
		super.close();
		instance = null;
		location = getLocation();
	}
	
	public static SyncWindows getInstance() {
		return instance;
	}

	@Override
	public void valueChanged(ListSelectionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent arg0) {
		// TODO Auto-generated method stub
		
	}

}	// SyncWindows_

/** The Listener interface for receiving DisplayChange events.
 *	The listener can be registered to an Object issuing DisplayChange events
 *	by its addDisplayChangeListener method.
 *	So far only OpenStackWindow used by SyncWindows is such an Object.
 *	*/
interface DisplayChangeListener extends java.util.EventListener {

	public void displayChanged(DisplayChangeEvent e);
}


/* -------------------------------------------------------------------------
/*
/* CLASS DisplayChangeEvent
/*
/* ------------------------------------------------------------------------- */

/** To be raised when a property of the image display has been changed */
class DisplayChangeEvent extends EventObject {

/** Type of change in display:
 *	Coordinate X, Y, Z, the Zoom, time, color channel.
 *	So far there is no need for properties other than Z.
 */
	public static final int X = 1;
	public static final int Y = 2;
	public static final int Z = 3;
	public static final int ZOOM = 4;
	public static final int T = 5;
	public static final int CHANNEL = 6;

	private int type;
	private int value;

	public DisplayChangeEvent(Object source, int type, int value) {
		super(source);
		this.type = type;
		this.value = value;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

}


/* -------------------------------------------------------------------------
/*
/* CLASS IJEventMulticaster
/*
/* ------------------------------------------------------------------------- */

/**
 * Multicaster for events special to ImageJ
 * <p>
 * Example how to implement an object, which fires DisplayChangeEvents using the
 * IJEventMulticaster:
 *
 * <pre><code>
 * public mySpecialWindow extends StackWindow {
 *
 *		DisplayChangeListener dclistener = null;
 *
 *		public synchronized void addDisplayChangeListener(DisplayChangeListener l) {
 *			dclistener = IJEventMulticaster.add(dclistener, l);
 *		}
 *
 *		public synchronized void removeDisplayChangeListener(DisplayChangeListener l) {
 *			dclistener = IJEventMulticaster.remove(dclistener, l);
 *		}
 *
 *		public void myEventFiringMethod(arguments) {
 *			... code ...
 *			if (dclistener != null) {
 *				DisplayChangeEvent dcEvent = new DisplayChangeEvent(this, DisplayChangeEvent.Z, zSlice);
 *				dclistener.displayChanged(dcEvent);
 *			}
 *			... code ...
 *		}
 *
 *		... other methods ...
 * }
 * </code></pre>
 *
 * To put in a new event-listener (by changing this class or extending it):
 * <p>
 * - Add the listener to the "implements" list.
 * <p>
 * - Add the methods of this listener to pass on the events (like displayChanged).
 * <p>
 * - Add the methods "add" and "remove" with the corresponding listener type.
 * <p>
 *
 * @author: code take from Sun's AWTEventMulticaster by J. Walter 2002-03-07
 */

class IJEventMulticaster extends AWTEventMulticaster implements DisplayChangeListener {

	IJEventMulticaster(EventListener a, EventListener b) {
		super(a,b);
	}

	/**
	 * Handles the DisplayChange event by invoking the
	 * displayChanged methods on listener-a and listener-b.
	 * @param e the DisplayChange event
	 */

	public void displayChanged(DisplayChangeEvent e) {
		((DisplayChangeListener)a).displayChanged(e);
		((DisplayChangeListener)b).displayChanged(e);
	}
	/**
	 * Adds DisplayChange-listener-a with DisplayChange-listener-b and
	 * returns the resulting multicast listener.
	 * @param a DisplayChange-listener-a
	 * @param b DisplayChange-listener-b
	 */
	public static DisplayChangeListener add(DisplayChangeListener a, DisplayChangeListener b) {
		return (DisplayChangeListener)addInternal(a, b);
	}
	/**
	 * Removes the old DisplayChange-listener from DisplayChange-listener-l and
	 * returns the resulting multicast listener.
	 * @param l DisplayChange-listener-l
	 * @param oldl the DisplayChange-listener being removed
	 */
	public static DisplayChangeListener remove(DisplayChangeListener l, DisplayChangeListener oldl) {
		return (DisplayChangeListener)removeInternal(l, oldl);
	}
}



















