package ij;

import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.awt.image.*;

import ij.gui.*;
import ij.process.*;
import ij.io.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.*;
import ij.text.*;
import ij.macro.Interpreter;
import ij.io.Opener;
import ij.util.*;

import javax.jnlp.ServiceManager;
import javax.jnlp.SingleInstanceListener;
import javax.jnlp.SingleInstanceService;
import javax.jnlp.UnavailableServiceException;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.MQTVSSceneLoader64;
import org.vcell.gloworm.WG_Uploader;

import client.RemoteMQTVSHandler;


/**

091310 CODE MODIFIED TO BROWSE SLICES WITH UP/DOWN AND FRAMES WITH LEFT/RIGHT KEYS


This frame is the main ImageJ class.
<p>
ImageJ is a work of the United States Government. It is in the public domain 
and open source. There is no copyright. You are free to do anything you want
with this source but I like to get credit for my work and I would like you to 
offer your changes to me so I can possibly add them to the "official" version.

<pre>
The following command line options are recognized by ImageJ:

  "file-name"
     Opens a file
     Example 1: blobs.tif
     Example 2: /Users/wayne/images/blobs.tif
     Example3: e81*.tif

  -ijpath path
     Specifies the path to the directory containing the plugins directory
     Example: -ijpath /Applications/ImageJ

  -port<n>
     Specifies the port ImageJ uses to determine if another instance is running
     Example 1: -port1 (use default port address + 1)
     Example 2: -port2 (use default port address + 2)
     Example 3: -port0 (don't check for another instance)

  -macro path [arg]
     Runs a macro or script, passing it an optional argument,
     which can be retieved using getArgument()
     Example 1: -macro analyze.ijm
     Example 2: -macro analyze /Users/wayne/images/stack1

  -batch path [arg]
    Runs a macro or script in batch (no GUI) mode, passing it an optional argument.
    ImageJ exits when the macro finishes.

  -eval "macro code"
     Evaluates macro code
     Example 1: -eval "print('Hello, world');"
     Example 2: -eval "return getVersion();"

  -run command
     Runs an ImageJ menu command
     Example: -run "About ImageJ..."
     
  -debug
     Runs ImageJ in debug mode
</pre>
@author Wayne Rasband (wsr@nih.gov)
*/
public class ImageJ extends Frame implements ActionListener, 
	MouseListener, KeyListener, WindowListener, ItemListener, Runnable, SingleInstanceListener, PopupMenuListener {

	/** Plugins should call IJ.getVersion() or IJ.getFullVersion() to get the version string. */
	public static final String VERSION = "1.49i3";
	public static final String BUILD = ""; 
	public static Color backgroundColor = new Color(220,220,220); //224,226,235
	/** SansSerif, 12-point, plain font. */
	public static final Font SansSerif12 = new Font("SansSerif", Font.PLAIN, 12);
	/** Address of socket where Image accepts commands */
	public static final int DEFAULT_PORT = 57294;
	
	/** Run as normal application. */
	public static final int STANDALONE=0;
	
	/** Run embedded in another application. */
	public static final int EMBEDDED=1;
	
	/** Run embedded and invisible in another application. */
	public static final int NO_SHOW=2;

	private static final String IJ_X="ij.x",IJ_Y="ij.y";
	private static int port = DEFAULT_PORT;
	private static String[] arguments;
	
	public Toolbar toolbar;
	private JPanel statusBar;
	private ProgressBar progressBar;
	private JLabel statusLine;
	private boolean firstTime = true;
	private java.applet.Applet applet; // null if not running as an applet
	private Vector classes = new Vector();
	private boolean exitWhenQuitting;
	private boolean quitting;
	private long keyPressedTime, actionPerformedTime;
	private String lastKeyCommand;
	private boolean embedded;
	private boolean windowClosed;
	private static String commandName;
		
	boolean hotkey;
	private ArrayList<JPopupMenu> openPopupsArrayList;
	
	/** Creates a new ImageJ frame that runs as an application. */
	public ImageJ() {
		this(null, STANDALONE);
	}
	
	/** Creates a new ImageJ frame that runs as an application in the specified mode. */
	public ImageJ(int mode) {
		this(null, mode);
	}

	/** Creates a new ImageJ frame that runs as an applet. */
	public ImageJ(java.applet.Applet applet) {
		this(applet, STANDALONE);
	}

	/** If 'applet' is not null, creates a new ImageJ frame that runs as an applet.
		If  'mode' is ImageJ.EMBEDDED and 'applet is null, creates an embedded 
		(non-standalone) version of ImageJ. */
	public ImageJ(java.applet.Applet applet, int mode) {
		super("CytoSHOW "/*+ UIManager.getLookAndFeel().getDescription()*/);
	    try {
	        SingleInstanceService singleInstanceService =
	            (SingleInstanceService)ServiceManager.
	                lookup("javax.jnlp.SingleInstanceService");
	        // add the listener to this application!
	        singleInstanceService.addSingleInstanceListener(
	            (SingleInstanceListener)this );
	    } catch(UnavailableServiceException use) {
	        use.printStackTrace();
//	        System.exit(-1);
	    }
	    
	    openPopupsArrayList = new ArrayList<JPopupMenu>();
	    
		embedded = applet==null && (mode==EMBEDDED||mode==NO_SHOW);
		this.applet = applet;
		String err1 = Prefs.load(this, applet);
		if (IJ.isLinux()) {
			backgroundColor = new Color(240,240,240);
			setBackground(backgroundColor);
		}
		Menus m = new Menus(this, applet);
		String err2 = m.addMenuBar();
		m.installPopupMenu(this);
		setLayout(new GridLayout(2, 1));
		
		// Tool bar
		toolbar = new Toolbar();
		toolbar.addKeyListener(this);
		add(toolbar);

		// Status bar
		statusBar = new JPanel();
		statusBar.setLayout(new BorderLayout());
		statusBar.setForeground(Color.black);
		statusBar.setBackground(backgroundColor);
		statusLine = new JLabel();
		statusLine.setFont(SansSerif12);
		statusLine.addKeyListener(this);
		statusLine.addMouseListener(this);
		statusBar.add("Center", statusLine);
		progressBar = new ProgressBar(120, 20);
		progressBar.addKeyListener(this);
		progressBar.addMouseListener(this);
		statusBar.add("East", progressBar);
		statusBar.setSize(toolbar.getPreferredSize());
		((JComponent) statusLine).setToolTipText("<html>Left-Clicking icons selects from a variety of tools for measurement and/or tagging of the movies or images.<br>Right-clicking allows choice of even more tools.<br>Double-clicking allows you to set tool-specific options.<br>Dragging and dropping file icons or web links onto this toolbar will launch them in CytoSHOW.</html>");		
		ToolTipManager.sharedInstance().setDismissDelay(60000);
		add(statusBar);

		IJ.init(this, applet);
 		addKeyListener(this);
 		addWindowListener(this);
		setFocusTraversalKeysEnabled(false);
 		
		Point loc = getPreferredLocation();
		Dimension tbSize = toolbar.getPreferredSize();
		int ijWidth = tbSize.width+10;
		int ijHeight = 100;
		setCursor(Cursor.getDefaultCursor()); // work-around for JDK 1.1.8 bug
		if (mode!=NO_SHOW) {
			if (IJ.isWindows()) try {setIcon();} catch(Exception e) {}
			setBounds(loc.x, loc.y, ijWidth, ijHeight); // needed for pack to work
			setLocation(loc.x, loc.y);
			pack();
			setResizable(!(IJ.isMacintosh() || IJ.isWindows())); // make resizable on Linux
			show();
		}
		if (err1!=null)
			IJ.error(err1);
		if (err2!=null) {
			IJ.error(err2);
			IJ.runPlugIn("ij.plugin.ClassChecker", "");
		}
		m.installStartupMacroSet();
		
		if (!IJ.is64Bit()) {

			if (IJ.showMessageWithCancel("Hmmmm...your Java is 32-bit", "Are you hoping to run CytoSHOW with all functions available?\n"

					+ "Then you need 64-bit Java!\n"
					+ "Shall we clear this up now (OK) or continue in limited32-bit mode (Cancel)?\n\n"

					)) {
				IJ.runPlugIn("ij.plugin.BrowserLauncher", "http://www.java.com/en/download/manual.jsp");
				System.exit(0);
			}
		}


		if (IJ.isMacintosh()&&applet==null) { 
			Object qh = null; 
			qh = IJ.runPlugIn("MacAdapter", ""); 
			if (qh==null) 
				IJ.runPlugIn("QuitHandler", ""); 
		} 
		if (applet==null)
			IJ.runPlugIn("ij.plugin.DragAndDrop", "");
		String str = m.getMacroCount()==1?" macro":" macros";
		IJ.showStatus(version()+ m.getPluginCount() + " commands; " + m.getMacroCount() + str+" jnlp"+ (Menus.jnlp?"+":"-"));
		//if (applet==null && !embedded && Prefs.runSocketListener)
		//	new SocketListener();
		configureProxy();
		loadCursors();
 	}
 	
 	private void loadCursors() {
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		String path = Prefs.getHomeDir()+File.separator+"images/crosshair-cursor.gif";
		File f = new File(path);
		if (!f.exists())
			return;
		//Image image = toolkit.getImage(path);
		ImageIcon icon = new ImageIcon(path);
		Image image = icon.getImage();
		if (image==null)
			return;
		int width = icon.getIconWidth();
		int height = icon.getIconHeight();
		Point hotSpot = new Point(width/2, height/2);
		Cursor crosshairCursor = toolkit.createCustomCursor(image, hotSpot, "crosshair-cursor.gif");
		ImageCanvas.setCursor(crosshairCursor, 0);
		//IJ.log(width+" "+height+" "+toolkit.getBestCursorSize(width,height));
	}
    	
	void configureProxy() {
		if (Prefs.useSystemProxies) {
			try {
				System.setProperty("java.net.useSystemProxies", "true");
			} catch(Exception e) {}
		} else {
			String server = Prefs.get("proxy.server", null);
			if (server==null||server.equals(""))
				return;
			int port = (int)Prefs.get("proxy.port", 0);
			if (port==0) return;
			Properties props = System.getProperties();
			props.put("proxySet", "true");
			props.put("http.proxyHost", server);
			props.put("http.proxyPort", ""+port);
		}
		//new ProxySettings().logProperties();
	}
	
    void setIcon() throws Exception {
		URL url = this.getClass().getResource("/microscope.gif");
		if (url==null) return;
		Image img = createImage((ImageProducer)url.getContent());
		if (img!=null) setIconImage(img);
	}
	
	public Point getPreferredLocation() {
		if (!IJ.isJava14()) return new Point(0, 0);
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		Rectangle maxBounds = ge.getMaximumWindowBounds();
		int ijX = Prefs.getInt(IJ_X,-99);
		int ijY = Prefs.getInt(IJ_Y,-99);
		if (ijX>=0 && ijY>0 && ijX<(maxBounds.x+maxBounds.width-75))
			return new Point(ijX, ijY);
		Dimension tbsize = toolbar.getPreferredSize();
		int ijWidth = tbsize.width+10;
		double percent = maxBounds.width>832?0.8:0.9;
		ijX = (int)(percent*(maxBounds.width-ijWidth));
		if (ijX<10) ijX = 10;
		return new Point(ijX, maxBounds.y);
	}
	
	void showStatus(String s) {
        statusLine.setText(s);
	}

	public ProgressBar getProgressBar() {
        return progressBar;
	}

	public JPanel getStatusBar() {
        return statusBar;
	}

    /** Starts executing a menu command in a separate thread. */
    void doCommand(String name) {
		new Executer(name, null, null);
    }
        
	public void runFilterPlugIn(Object theFilter, String cmd, String arg) {
		new PlugInFilterRunner(theFilter, cmd, arg);
	}
        
	public Object runUserPlugIn(String commandName, String className, String arg, boolean createNewLoader) {
		return IJ.runUserPlugIn(commandName, className, arg, createNewLoader);	
	} 
	
	/** Return the current list of modifier keys. */
	public static String modifiers(int flags) { //?? needs to be moved
		String s = " [ ";
		if (flags == 0) return "";
		if ((flags & Event.SHIFT_MASK) != 0) s += "Shift ";
		if ((flags & Event.CTRL_MASK) != 0) s += "Control ";
		if ((flags & Event.META_MASK) != 0) s += "Meta ";
		if ((flags & Event.ALT_MASK) != 0) s += "Alt ";
		s += "] ";
		return s;
	}

	/** Handle menu events. */
	public void actionPerformed(ActionEvent e) {
		if ((e.getSource() instanceof JMenuItem)) {
			JMenuItem item = (JMenuItem)e.getSource();
			String cmd = e.getActionCommand();
			ImagePlus imp = null;
			if (item.getParent() instanceof JPopupMenu 
					|| item.getParent() instanceof JMenu
					|| item.getParent() instanceof JPanel) {
				if (item.getParent() instanceof JPopupMenu) {
					item.getParent().setVisible(false);
//					if (item.getParent().getParent().getParent() instanceof JPanel) {
//						Component[] ppcs = item.getParent().getParent().getParent().getComponents();
//						for (Component ppc:ppcs) {
//							if (ppc instanceof JMenu) {
//								((JMenu)ppc).getComponentPopupMenu().setVisible(false);
//							}
//						}
//					}
				}
				final Object invoker = Menus.getPopupMenu().getInvoker();
				if (item.getParent() instanceof JPopupMenu 
						&& item == item.getParent().getComponent(0)
							&& cmd.contains("^--------------------^")) {
//					IJ.showMessage(cmd);
					TearoffJFrame tearoff = new TearoffJFrame() {
						@Override
					    protected void processWindowEvent(WindowEvent e) {
					        super.processWindowEvent(e);
					        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
								WindowManager.removeWindow(this);
					        }							
						}
						
						
						@Override
						public void windowActivated(WindowEvent e) {
							ImageJ ij = IJ.getInstance();
							if (IJ.isMacintosh() && ij!=null && !ij.quitting()) {
								IJ.wait(10); // may be needed for Java 1.4 on OS X
								setMenuBar(Menus.getMenuBar());
							}
						}

					};
					
					ScrollPane fsp = new ScrollPane();
					GridBagLayout gridbag = new GridBagLayout();
					GridBagConstraints c = new GridBagConstraints();
					gridbag.setConstraints(fsp, c);
					JPanel fspp = new JPanel();
					fsp.add(fspp, c);
					fspp.setLayout(gridbag);
					tearoff.add(fsp);
				    fspp.setBackground(Color.white);

				    int y = 0;
					int x = 0;
					c.gridx = 0;
					c.gridy = y++;
					c.gridwidth = 1;
					c.fill = GridBagConstraints.BOTH;
					c.anchor = GridBagConstraints.CENTER;
					c.insets = new Insets(1, 1, 1, 1);

					Dimension parentSize = item.getParent().getSize();
					if (item.getParent() instanceof JPopupMenu) {
						if (((JPopupMenu)item.getParent()).getInvoker() instanceof JMenu)
							tearoff.setTitle(((JMenu)((JPopupMenu)item.getParent()).getInvoker()).getText());
						else if (item.getParent() == Menus.getPopupMenu()) {
							int pick = (item.getParent().getComponent(1) instanceof JMenuItem)?1:2;
							tearoff.setTitle(((JMenuItem)item.getParent()
									.getComponent(pick)).getText()
									.replace(" \":","\"").replaceAll("(.*) synch.*", "$1"));
						}
					}
					for (Component comp:item.getParent().getComponents()) {
						if (comp != item.getParent().getComponent(0)) {
							Component[] subcomps = null;
							
							if (comp instanceof JMenuItem  && !(comp instanceof JMenu)) {
								JMenuItem compCopy = new JMenuItem(((JMenuItem)comp).getText());
								compCopy.setIcon(((JMenuItem)comp).getIcon());
								compCopy.setToolTipText(((JMenuItem)comp).getToolTipText());
								compCopy.setActionCommand(((JMenuItem)comp).getActionCommand());
								compCopy.addActionListener(IJ.getInstance());
								fspp.add(compCopy, c);
								c.gridy++;
							} else if (comp instanceof JPanel) {
								JPanel compCopy = new JPanel();
								JButton compCopyButton = new JButton(((JButton)((JPanel)comp).getComponent(0)).getIcon());
								compCopy.add(compCopyButton);
								fspp.add(compCopy, c);
								c.gridy++;
							} else if (comp instanceof JMenu) {
								JPopupMenu jsub = ((JMenu) comp).getPopupMenu();
								if (jsub.getComponentCount() == 0)
									jsub = ((JComponent) comp).getComponentPopupMenu();
								
								subcomps = jsub.getComponents();
								JMenu compCopy = new JMenu(((JMenu)comp).getText());
								compCopy.setIcon(((JMenu)comp).getIcon());
								fspp.add(compCopy, c);
								c.gridy++;
								JPopupMenu jpm = new JPopupMenu() ;
								jpm.setInvoker((Component) invoker);
								jpm.addPopupMenuListener(IJ.getInstance());
								for (Component jmi:subcomps) {									
									Component[] subcomps2 = null;
									
									if (jmi instanceof JMenuItem  && !(jmi instanceof JMenu)) {
										JMenuItem jmiCopy = new JMenuItem(((JMenuItem)jmi).getText());
										jmiCopy.setIcon(((JMenuItem)jmi).getIcon());
										jmiCopy.setToolTipText(((JMenuItem)jmi).getToolTipText());
										jmiCopy.setActionCommand(((JMenuItem)jmi).getActionCommand());
										jmiCopy.addActionListener(IJ.getInstance());
										jpm.add(jmiCopy);
									} else if (jmi instanceof JMenu) {
										JPopupMenu jsub2 = ((JMenu) jmi).getPopupMenu();
										if (jsub2.getComponentCount() == 0)
											jsub2 = ((JComponent) jmi).getComponentPopupMenu();
										
										subcomps2 = jsub2.getComponents();
										JMenu jmiCopy = new JMenu(((JMenu)jmi).getText());
										jmiCopy.setIcon(((JMenu)jmi).getIcon());
										jpm.add(jmiCopy);
										JPopupMenu jpm2 = new JPopupMenu() ;
										jpm2.setInvoker((Component) invoker);
										jpm2.addPopupMenuListener(IJ.getInstance());
										for (Component jmi2:subcomps2) {
											if (jmi2 instanceof JMenuItem) {
												JMenuItem jmi2Copy = new JMenuItem(((JMenuItem)jmi2).getText());
												jmi2Copy.setIcon(((JMenuItem)jmi2).getIcon());
												jmi2Copy.setToolTipText(((JMenuItem)jmi2).getToolTipText());
												jmi2Copy.setActionCommand(((JMenuItem)jmi2).getActionCommand());
												jmi2Copy.addActionListener(IJ.getInstance());
												jpm2.add(jmi2Copy);
											}
										}
										jpm2.add(new JMenuItem(""));
										((JMenu) jmiCopy).setComponentPopupMenu(jpm2);	
									}
								}
								jpm.add(new JMenuItem(""));
								((JMenu) compCopy).setComponentPopupMenu(jpm);	
							}
						}
					}
					fspp.add(new JMenuItem(""), c);
					c.gridy++;
					tearoff.pack();
					tearoff.setLocation(MouseInfo.getPointerInfo().getLocation().x-100,MouseInfo.getPointerInfo().getLocation().y-10);
//					tearoff.setSize(300,fspp.getComponentCount()*23);
					tearoff.setSize(parentSize.width+50, parentSize.height+15);
					tearoff.setVisible(true);
					WindowManager.addWindow(tearoff);
					return;
				}
				if (invoker instanceof ImageCanvas) {
					imp = ((ImageCanvas)invoker).getImage();
					imp.getWindow().setAlwaysOnTop(false);
				}
				else if (invoker instanceof ColorLegend) {
					RoiManager rm = ((ColorLegend)invoker).getRoiManager();
					for (int i=0; i< rm.getCompImps().size(); i++){
						if (rm.getCompImps().get(i) != null) {
							imp = rm.getCompImps().get(i);
							i = rm.getCompImps().size();
						}
					}
				}
				else if (invoker instanceof MenuItem) {
					IJ.showMessage(invoker.toString());
					Object grandparent = ((MenuItem)invoker).getParent();
					if (grandparent instanceof ImageCanvas)
						imp = ((ImageCanvas)grandparent).getImage();
					else if (invoker instanceof ColorLegend) {
						RoiManager rm = ((ColorLegend)invoker).getRoiManager();
						for (int i=0; i< rm.getCompImps().size(); i++){
							if (rm.getCompImps().get(i) != null) {
								imp = rm.getCompImps().get(i);
								i = rm.getCompImps().size();
							}
						}
					}
					else if (grandparent instanceof MenuItem) {
						IJ.showMessage(grandparent.toString());
						Object ggparent = ((MenuItem)grandparent).getParent();
						if (ggparent instanceof ImageCanvas)
							imp = ((ImageCanvas)ggparent).getImage();
						else if (invoker instanceof ColorLegend) {
							RoiManager rm = ((ColorLegend)invoker).getRoiManager();
							for (int i=0; i< rm.getCompImps().size(); i++){
								if (rm.getCompImps().get(i) != null) {
									imp = rm.getCompImps().get(i);
									i = rm.getCompImps().size();
								}
							}
						}
						else if (ggparent instanceof MenuItem) {
							IJ.showMessage(ggparent.toString());
							Object gggparent = ((MenuItem)ggparent).getParent();
							if (gggparent instanceof ImageCanvas)
								imp = ((ImageCanvas)gggparent).getImage();
						}
					}
				}
			} 
			int flags = e.getModifiers();
			//IJ.log(""+KeyEvent.getKeyModifiersText(flags));
			hotkey = false;
			actionPerformedTime = System.currentTimeMillis();
			long ellapsedTime = actionPerformedTime-keyPressedTime;
			if (cmd!=null && (ellapsedTime>=200L||!cmd.equals(lastKeyCommand))) {
				if ((flags & Event.ALT_MASK)!=0)
					IJ.setKeyDown(KeyEvent.VK_ALT);
				if ((flags & Event.SHIFT_MASK)!=0)
					IJ.setKeyDown(KeyEvent.VK_SHIFT);
				new Executer(cmd, imp, e);
			}
			lastKeyCommand = null;
			if (IJ.debugMode) IJ.log("actionPerformed: time="+ellapsedTime+", "+e);
		} else if ((e.getSource() instanceof MenuItem)) {
			MenuItem item = (MenuItem)e.getSource();
			String cmd = e.getActionCommand();
			if (item.getParent()==Menus.openRecentMenu) {
				new RecentOpener(cmd); // open image in separate thread
				return;
			}
			cmd = e.getActionCommand();
			ImagePlus imp = WindowManager.getCurrentImage();
			new Executer(cmd, imp, e);
		} else {
			String cmd = e.getActionCommand();
			ImagePlus imp = IJ.getImage();
			new Executer(cmd, imp, e);

			imp.getCanvas().requestFocus();
		}
	}

	/** Handles CheckboxMenuItem state changes. */
	public void itemStateChanged(ItemEvent e) {
		MenuItem item = (MenuItem)e.getSource();
		MenuComponent parent = (MenuComponent)item.getParent();
		String cmd = e.getItem().toString();
		if ((Menu)parent==Menus.window)
			WindowManager.activateWindow(cmd, item);
		else
			doCommand(cmd);
	}

	public void mousePressed(MouseEvent e) {
		Undo.reset();
		if (!Prefs.noClickToGC)
			System.gc();
		IJ.showStatus(version()+IJ.freeMemory());
		if (IJ.debugMode)
			IJ.log("Windows: "+WindowManager.getWindowCount());
	}
	
	public String getInfo() {
		return version()+System.getProperty("os.name")+" "+System.getProperty("os.version")+"; "+IJ.freeMemory();
	}

	private String version() {
		return "ImageJ "+VERSION+BUILD + "; "+"Java "+System.getProperty("java.version")+(IJ.is64Bit()?" [64-bit]; ":" [32-bit]; ");
	}
	
	public void mouseReleased(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {
//		IJ.runMacro("print(\"\\\\Clear\")");
//		IJ.runMacro("print(\"\\\\Update:CytoSHOW Toolbar:\\\nLeft-Clicking icons selects from a variety of tools for measurement and/or Tag of the movies or images.\\\nRight-clicking allows choice of even more tools.\\\nDouble-clicking allows you to set tool-specific options.\\\nDragging and dropping file icons or web links for additional image, movies, or scenes \\\nonto this toolbar will launch them in this copy of CytoSHOW.\\\n \")");

	}

 	public void keyPressed(KeyEvent e) {
 		//if (e.isConsumed()) return;
		int keyCode = e.getKeyCode();
		IJ.setKeyDown(keyCode);
		hotkey = false;
//		if (keyCode==KeyEvent.VK_CONTROL || keyCode==KeyEvent.VK_SHIFT)
//			return;
		char keyChar = e.getKeyChar();
		int flags = e.getModifiers();
		if (IJ.debugMode) IJ.log("keyPressed: code=" + keyCode + " (" + KeyEvent.getKeyText(keyCode)
			+ "), char=\"" + keyChar + "\" (" + (int)keyChar + "), flags="
			+ KeyEvent.getKeyModifiersText(flags));
		boolean shift = (flags & KeyEvent.SHIFT_MASK) != 0;
		boolean control = (flags & KeyEvent.CTRL_MASK) != 0;
		boolean alt = (flags & KeyEvent.ALT_MASK) != 0;
		boolean meta = (flags & KeyEvent.META_MASK) != 0;
		String cmd = null;
		ImagePlus imp = WindowManager.getCurrentImage();
		boolean isStack = (imp!=null) && (imp.getStackSize()>1);
		
		if (imp!=null && !control && ((keyChar>=32 && keyChar<=255) || keyChar=='\b' || keyChar=='\n' ||  keyCode==KeyEvent.VK_ESCAPE)) {
			Roi roi = imp.getRoi();
			if (roi != null && roi instanceof Roi) {
				if ((flags & KeyEvent.META_MASK)!=0 && IJ.isMacOSX()) return;
				if (keyCode==KeyEvent.VK_ESCAPE) {
					imp.killRoi();
					if (imp.getRoiManager() != null)
						imp.getRoiManager().select(imp, -1);
					return;
				}
				if (alt)
					switch (keyChar) {
						case 'u': case 'm': keyChar = IJ.micronSymbol; break;
						case 'A': keyChar = IJ.angstromSymbol; break;
						default:
					}
				if (roi instanceof TextRoi)
					((TextRoi)roi).addChar(keyChar);
				return;
			} else {
				if (imp.getMotherImp()!=imp ){
					if (imp.getMotherImp().getRoiManager().getColorLegend()!=null){
						if (keyCode==KeyEvent.VK_ESCAPE) {
							for (Checkbox cb:imp.getMotherImp().getRoiManager().getColorLegend().getCheckbox()){
								if (cb.getState()) {
									cb.setState(false);
									imp.getMotherImp().getRoiManager().getColorLegend().itemStateChanged
										(new ItemEvent(cb, ItemEvent.ITEM_STATE_CHANGED, cb, ItemEvent.SELECTED));
								}
							}
//													imp.getMotherImp().getRoiManager().getColorLegend().getChoice().select("Hide Checked");
//													imp.getMotherImp().getRoiManager().getColorLegend()
//														.itemStateChanged(new ItemEvent(imp.getMotherImp().getRoiManager().getColorLegend().getChoice(),
//																0,0,0));
						}
					}
				}
			}
		}

		// Handle one character macro shortcuts
		if (!control && !meta) {
			Hashtable macroShortcuts = Menus.getMacroShortcuts();
			if (macroShortcuts.size()>0) {
				if (shift)
					cmd = (String)macroShortcuts.get(new Integer(keyCode+200));
				else
					cmd = (String)macroShortcuts.get(new Integer(keyCode));
				if (cmd!=null) {
					//MacroInstaller.runMacroCommand(cmd);
					MacroInstaller.runMacroShortcut(cmd);
					return;
				}
			}
		}

		if ((!Prefs.requireControlKey || control || meta) && keyChar!='+') {
			Hashtable shortcuts = Menus.getShortcuts();
			if (shift)
				cmd = (String)shortcuts.get(new Integer(keyCode+200));
			else
				cmd = (String)shortcuts.get(new Integer(keyCode));
		}
		
		if (cmd==null) {
			switch (keyChar) {
				case '<': case ',': cmd="Previous Slice [<]"; break;
				case '>': case '.': case ';': cmd="Next Slice [>]"; break;
				case '+': case '=': cmd="In [+]"; break;
				case '-': cmd="Out [-]"; break;
				case '*': cmd="View 100%"; break;				
				case '/': cmd="Orthogonal Views[/]"; break;
				default:
			}
		}

		if (cmd==null) {
			switch(keyCode) {
				case KeyEvent.VK_TAB: WindowManager.putBehind(); return;
				case KeyEvent.VK_BACK_SPACE: // delete
					if (deleteOverlayRoi(imp))
						return;
					cmd="Clear";
					hotkey=true;
					break; 
				//case KeyEvent.VK_BACK_SLASH: cmd=IJ.altKeyDown()?"Animation Options...":"Start Animation"; break;
				case KeyEvent.VK_EQUALS: cmd="In [+]"; break;
				case KeyEvent.VK_MINUS: cmd="Out [-]"; break;
				case KeyEvent.VK_SLASH: case 0xbf: cmd="Orthogonal Views[/]"; break;
				case KeyEvent.VK_COMMA: case 0xbc: cmd="Previous Slice [<]"; break;
				case KeyEvent.VK_PERIOD: case 0xbe: cmd="Next Slice [>]"; break;
				
				case KeyEvent.VK_LEFT: case KeyEvent.VK_RIGHT: case KeyEvent.VK_UP: case KeyEvent.VK_DOWN: // arrow keys
				case KeyEvent.VK_NUMPAD6: case KeyEvent.VK_6: case KeyEvent.VK_KP_RIGHT:
				case KeyEvent.VK_NUMPAD4: case KeyEvent.VK_4: case KeyEvent.VK_KP_LEFT:
				case KeyEvent.VK_NUMPAD8: case KeyEvent.VK_8: case KeyEvent.VK_KP_UP:
				case KeyEvent.VK_NUMPAD2: case KeyEvent.VK_2: case KeyEvent.VK_KP_DOWN: 
				case KeyEvent.VK_NUMPAD7: case KeyEvent.VK_7: case KeyEvent.VK_HOME:
				case KeyEvent.VK_NUMPAD9: case KeyEvent.VK_9: case KeyEvent.VK_PAGE_DOWN:
				case KeyEvent.VK_NUMPAD1: case KeyEvent.VK_1: case KeyEvent.VK_END:
				case KeyEvent.VK_NUMPAD3: case KeyEvent.VK_3: case KeyEvent.VK_PAGE_UP:
				case KeyEvent.VK_NUMPAD5: case KeyEvent.VK_5: /*case KeyEvent.VK_PAGE_UP:*/
				case KeyEvent.VK_NUMPAD0: case KeyEvent.VK_0: /*case KeyEvent.VK_PAGE_UP:*/
				
								
					
					if (imp==null) return;
					Roi roi = imp.getRoi();
					if (IJ.shiftKeyDown()&&imp==Orthogonal_Views.getImage())
						return;
					boolean stackKey = imp.getStackSize()>1 /*&& (roi==null||IJ.shiftKeyDown())*/;
					boolean zoomKey = /*roi==null ||*/ IJ.shiftKeyDown() || IJ.controlKeyDown();
					if (stackKey && (keyCode==KeyEvent.VK_RIGHT || keyCode==KeyEvent.VK_NUMPAD6
								|| keyCode==KeyEvent.VK_6  || keyCode==KeyEvent.VK_KP_RIGHT ))
							cmd="Next Slice [>]";
					else if (stackKey && (keyCode==KeyEvent.VK_LEFT || keyCode==KeyEvent.VK_NUMPAD4
								|| keyCode==KeyEvent.VK_4 || keyCode==KeyEvent.VK_KP_LEFT ))
							cmd="Previous Slice [<]";
					else if (stackKey && (keyCode==KeyEvent.VK_DOWN || keyCode==KeyEvent.VK_NUMPAD2
								|| keyCode==KeyEvent.VK_2 || keyCode==KeyEvent.VK_KP_DOWN )) {
							IJ.setKeyDown(KeyEvent.VK_CONTROL);
							cmd="Next Slice [>]";
							//IJ.setKeyUp(KeyEvent.VK_CONTROL);
					}
					else if (stackKey && (keyCode==KeyEvent.VK_UP || keyCode==KeyEvent.VK_NUMPAD8
								|| keyCode==KeyEvent.VK_8 || keyCode==KeyEvent.VK_KP_UP ) ){
							IJ.setKeyDown(KeyEvent.VK_CONTROL);
							cmd="Previous Slice [<]";
							//IJ.setKeyUp(KeyEvent.VK_CONTROL);
					}
					else if (stackKey && (keyCode==KeyEvent.VK_HOME || keyCode==KeyEvent.VK_NUMPAD7|| keyCode==KeyEvent.VK_7) ){
						IJ.setKeyDown(KeyEvent.VK_ALT);
						cmd="Previous Slice [<]";
						//IJ.setKeyUp(KeyEvent.VK_ALT);
					}
					else if (stackKey && (keyCode==KeyEvent.VK_PAGE_UP || keyCode==KeyEvent.VK_NUMPAD9|| keyCode==KeyEvent.VK_9) ){
						IJ.setKeyDown(KeyEvent.VK_ALT);
						cmd="Next Slice [>]";
						//IJ.setKeyUp(KeyEvent.VK_ALT);
					}
					else if (stackKey && (keyCode==KeyEvent.VK_END || keyCode==KeyEvent.VK_NUMPAD1|| keyCode==KeyEvent.VK_1) ){
						IJ.setKeyDown(KeyEvent.VK_ALT);
						IJ.setKeyDown(KeyEvent.VK_SPACE);
						
						cmd="Previous Slice [<]";
						//IJ.setKeyUp(KeyEvent.VK_ALT);
					}
					else if (stackKey && (keyCode==KeyEvent.VK_PAGE_DOWN || keyCode==KeyEvent.VK_NUMPAD3|| keyCode==KeyEvent.VK_3) ){
						IJ.setKeyDown(KeyEvent.VK_ALT);
						IJ.setKeyDown(KeyEvent.VK_SPACE);

						cmd="Next Slice [>]";
						//IJ.setKeyUp(KeyEvent.VK_ALT);
					}
					else if (stackKey && (/*keyCode==KeyEvent.VK_PAGE_DOWN || */keyCode==KeyEvent.VK_NUMPAD5|| keyCode==KeyEvent.VK_5) ){
						if (((flags&Event.ALT_MASK)!=0))
							cmd = "Animation Options...";
						else
							cmd = "Start Animation [\\]";

					}
					else if (stackKey && (/*keyCode==KeyEvent.VK_PAGE_DOWN || */keyCode==KeyEvent.VK_NUMPAD0|| keyCode==KeyEvent.VK_0) ){

						cmd="Start Z Animation";
						
					}
	
							
//					else if (roi!=null) {
//						if ((flags & KeyEvent.ALT_MASK) != 0)
//							roi.nudgeCorner(keyCode);
//						else
//							roi.nudge(keyCode);
//						return;
//					}
					break;
				case KeyEvent.VK_ESCAPE:
					abortPluginOrMacro(imp);
					return;
				case KeyEvent.VK_ENTER: WindowManager.toFront(this); return;
				default: break;
			}
		}
		
		if (cmd!=null && !cmd.equals("")) {
			if (cmd.equals("Fill")||cmd.equals("Draw"))
				hotkey = true;
			if (cmd.charAt(0)==MacroInstaller.commandPrefix)
				MacroInstaller.runMacroShortcut(cmd);
			else {
				if (cmd.contains("Animation") ||  (cmd.contains("Slice [")/* && (!IJ.getImage().getWindow().running2 && !IJ.getImage().getWindow().running3)*/))
					doCommand(cmd);  //runs cmd in new thread
				else
					IJ.run(cmd);
				keyPressedTime = System.currentTimeMillis();
				lastKeyCommand = cmd;
			}
		}
	}
	
	private boolean deleteOverlayRoi(ImagePlus imp) {
		Overlay overlay = imp!=null?imp.getOverlay():null;
		if (overlay==null)
			return false;
		Roi roi = imp.getRoi();
		for (int i=0; i<overlay.size(); i++) {
			Roi roi2 = overlay.get(i);
			if (roi2==roi) {
				overlay.remove(i);
				imp.deleteRoi();
				return true;
			}
		}
		return false;
	}
	
	private boolean ignoreArrowKeys(ImagePlus imp) {
		Frame frame = WindowManager.getFrontWindow();
		String title = frame.getTitle();
		if (title!=null && title.equals("Tag Manager"))
			return true;
		// Control Panel?
		if (frame!=null && frame instanceof javax.swing.JFrame)
			return true;
		ImageWindow win = imp.getWindow();
		// LOCI Data Browser window?
		if (imp.getStackSize()>1 && win!=null && win.getClass().getName().startsWith("loci"))
			return true;
		return false;
	}
	
	public void keyTyped(KeyEvent e) {
		char keyChar = e.getKeyChar();
		int flags = e.getModifiers();
		if (IJ.debugMode) IJ.log("keyTyped: char=\"" + keyChar + "\" (" + (int)keyChar 
			+ "), flags= "+Integer.toHexString(flags)+ " ("+KeyEvent.getKeyModifiersText(flags)+")");
		if (keyChar=='\\' || keyChar==171 || keyChar==223) {
			if (((flags&Event.ALT_MASK)!=0))
				doCommand("Animation Options...");
			else
				doCommand("Start Animation [\\]");
		}
	}

	public void keyReleased(KeyEvent e) {
		IJ.setKeyUp(e.getKeyCode());
	}
		
	void abortPluginOrMacro(ImagePlus imp) {
		if (imp!=null) {
			ImageWindow win = imp.getWindow();
			if (win!=null) {
				win.running = false;
				win.running2 = false;
			}
		}
		Macro.abort();
		Interpreter.abort();
		if (Interpreter.getInstance()!=null) IJ.beep();
	}

	public void windowClosing(WindowEvent e) {
		//doCommand("Quit");
		quit();
		windowClosed = true;
	}

	public void windowActivated(WindowEvent e) {
		if (IJ.isMacintosh() && !quitting) {
			IJ.wait(10); // may be needed for Java 1.4 on OS X
			setMenuBar(Menus.getMenuBar());
		}
	}
	
	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
	
	/** Adds the specified class to a Vector to keep it from being
		garbage collected, causing static fields to be reset. */
	public void register(Class c) {
		if (!classes.contains(c))
			classes.addElement(c);
	}

	/** Called by ImageJ when the user selects Quit. */
	public void quit() {
		Thread thread = new Thread(this, "Quit");
		thread.setPriority(Thread.NORM_PRIORITY);
		thread.start();
		IJ.wait(10);
	}
	
	/** Returns true if ImageJ is exiting. */
	public boolean quitting() {
		return quitting;
	}
	
	/** Called once when ImageJ quits. */
	public void savePreferences(Properties prefs) {
		Point loc = getLocation();
		prefs.put(IJ_X, Integer.toString(loc.x));
		prefs.put(IJ_Y, Integer.toString(loc.y));
		//prefs.put(IJ_WIDTH, Integer.toString(size.width));
		//prefs.put(IJ_HEIGHT, Integer.toString(size.height));
	}

	public static void main(String args[]) {
		if (System.getProperty("java.version").substring(0,3).compareTo("1.5")<0) {
			javax.swing.JOptionPane.showMessageDialog(null,"ImageJ "+VERSION+" requires Java 1.5 or later.");
			System.exit(0);
		}
		//IJ.debugMode = true;
		boolean noGUI = false;
		boolean remote = false;

		int mode = STANDALONE;
		arguments = args;
		//System.setProperty("file.encoding", "UTF-8");
		int nArgs = args!=null?args.length:0;
		boolean commandLine = false;
		ArrayList<String> rmiArgsArrayList = new ArrayList<String>();
		String concat = "";
		for (int i=0; i<nArgs; i++) {
			String arg = args[i];
			if (arg==null) continue;
			//IJ.log(i+"  "+arg);
			if (args[i].startsWith("-")) {
				if (args[i].startsWith("-batch")) {
					noGUI = true;
				}else if (args[i].startsWith("-upload")) {
					noGUI = true;
					if (i+1<nArgs) {
						new WG_Uploader(args[i+1]);
						args[i+1] = null;
					} else
						new WG_Uploader((new DirectoryChooser("Upload Folder Contents")).getDirectory().replace("\\", "\\\\"));
				} else if (args[i].startsWith("-debug"))
					IJ.debugMode = true;
				else if (args[i].startsWith("-ijpath") && i+1<nArgs) {
					Prefs.setHomeDir(args[i+1]);
					commandLine = true;
					args[i+1] = null;
				} else if (args[i].startsWith("-port")) {
					int delta = (int)Tools.parseDouble(args[i].substring(5, args[i].length()), 0.0);
					commandLine = true;
					if (delta==0)
						mode = EMBEDDED;
					else if (delta>0 && DEFAULT_PORT+delta<65536)
						port = DEFAULT_PORT+delta;
				}
			} 
		}
 
		// If existing ImageJ instance, pass arguments to it and quit.
  		boolean passArgs = mode==STANDALONE && !noGUI;
		if (IJ.isMacOSX() && !commandLine) passArgs = false;
		if (passArgs && isRunning(args)) 
  			return;
 		ImageJ ij = IJ.getInstance();    	
		if (!noGUI && (ij==null || (ij!=null && !ij.isShowing()))) {
			ij = new ImageJ(null, mode);
			ij.exitWhenQuitting = true;
		}
		int macros = 0;
		for (int i=0; i<nArgs; i++) {
			String arg = args[i];
			if (arg==null) continue;
			if (arg.startsWith("-")) {
				if ((arg.startsWith("-macro") || arg.startsWith("-batch")) && i+1<nArgs) {
					String arg2 = i+2<nArgs?args[i+2]:null;
					Prefs.commandLineMacro = true;
					if (noGUI && args[i+1].endsWith(".js"))
						Interpreter.batchMode = true;
					IJ.runMacroFile(args[i+1], arg2);
					break;
				} else if (arg.startsWith("-eval") && i+1<nArgs) {
					String rtn = IJ.runMacro(args[i+1]);
					if (rtn!=null)
						System.out.print(rtn);
					args[i+1] = null;
				} else if (arg.startsWith("-run") && i+1<nArgs) {
					IJ.run(args[i+1]);
					args[i+1] = null;
				}else if (args[i].startsWith("-remote")) {
					remote = true;
					if (IJ.rmiURL == null || !IJ.rmiURL.matches("\\/\\/\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?:\\d\\d?\\d?\\d? \\d\\d?\\d?\\d?")) {
						IJ.rmiURL = "//155.37.253.202:8084 1";
					}
					if (args.length > i+2) {
						if (args[i+1].matches("\\/\\/\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?:\\d\\d?\\d?\\d?")
								&& args[i+2].matches("\\d\\d?\\d?\\d?")) {
							IJ.rmiURL = args[i+1]+" "+args[i+2];
							args[i+1] = null;				
							args[i+2] = null;
						}
					}
					rmiArgsArrayList.add(IJ.rmiURL.split(" ")[0]);
					rmiArgsArrayList.add(IJ.rmiURL.split(" ")[1]);
					if (args.length > i+1) {
						for (int j = i+1;  j < args.length; j++) {
							if (args[j] != null && !args[j].startsWith("-")) {
								rmiArgsArrayList.add(""+args[j]);
								args[j] = null;
							} else if (args[j] != null && args[j].startsWith("-")) {
								break;
							}
						}
					}
					Iterator<String> argIt = rmiArgsArrayList.iterator();
					argIt.next();
					argIt.next();
					while (argIt.hasNext()) {
						concat = concat+argIt.next();
						if (argIt.hasNext())
							concat = concat+"|";
					}
				}

			} else if (macros==0 && (arg.endsWith(".ijm") || arg.endsWith(".txt"))) {
				IJ.runMacroFile(arg);
				macros++;
			} else if (arg.length()>0 && arg.indexOf("ij.ImageJ")==-1) {
				File file = new File(arg);
				IJ.open(file.getAbsolutePath());
			}
		}
		if (remote) {
			if (concat.contains("NOMOVIE") || concat.contains("APPONLY") || concat.contains("BASIC"))
				return;
			if (!concat.contains("scene.scn") && !concat.contains("suite.ste")) {
				RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], concat.replace("|"," "), 
						false, true, true, false, true, false, false, false, false);
					if (rmqtvsh == null)
						return;
					ImagePlus imp = rmqtvsh.getImagePlus();
					imp.getWindow().setVisible(true);
			} else {
				for (String scene:concat.split("\\|")) {
					if (concat.contains("scene.scn")){
						if (IJ.is64Bit())
							MQTVSSceneLoader64.runMQTVS_SceneLoader64(scene).getImp().getWindow().setVisible(true);
						else
							MQTVSSceneLoader64.runMQTVS_SceneLoader64(scene).getImp().getWindow().setVisible(true);	
					} else {
						Opener opener = new Opener();
						opener.open(scene);
					}
				}
			}
		}
		if (IJ.debugMode && IJ.getInstance()==null)
			new JavaProperties().run("");
		if (noGUI) System.exit(0);
	}

	// Is there another instance of ImageJ? If so, send it the arguments and quit.
	static boolean isRunning(String args[]) {
		return OtherInstance.sendArguments(args);
	}

	/** Returns the port that ImageJ checks on startup to see if another instance is running.
	* @see ij.OtherInstance
	*/
	public static int getPort() {
		return port;
	}
	
	/** Returns the command line arguments passed to ImageJ. */
	public static String[] getArgs() {
		return arguments;
	}

	/** ImageJ calls System.exit() when qutting when 'exitWhenQuitting' is true.*/
	public void exitWhenQuitting(boolean ewq) {
		exitWhenQuitting = ewq;
	}
	
	/** Quit using a separate thread, hopefully avoiding thread deadlocks. */
	public void run() {
		quitting = true;
		boolean changes = false;
		int[] wList = WindowManager.getIDList();
		if (wList!=null) {
			for (int i=0; i<wList.length; i++) {
				ImagePlus imp = WindowManager.getImage(wList[i]);
				if (imp!=null && imp.changes==true) {
					changes = true;
					break;
				}
			}
		}
		Frame[] frames = WindowManager.getNonImageWindows();
		if (frames!=null) {
			for (int i=0; i<frames.length; i++) {
				if (frames[i]!=null && (frames[i] instanceof Editor)) {
					if (((Editor)frames[i]).fileChanged()) {
						changes = true;
						break;
					}
				}
			}
		}
		if (windowClosed && !changes && Menus.window.getItemCount()>Menus.WINDOW_MENU_ITEMS && !(IJ.macroRunning()&&WindowManager.getImageCount()==0)) {
			GenericDialog gd = new GenericDialog("CytoSHOW", this);
			gd.addMessage("Are you sure you want to quit CytoSHOW?");
			gd.showDialog();
			quitting = !gd.wasCanceled();
			windowClosed = false;
		}
		if (!quitting)
			return;
		if (!WindowManager.closeAllWindows()) {
			quitting = false;
			return;
		}
		//IJ.log("savePreferences");
		if (applet==null) {
			saveWindowLocations();
			Prefs.savePreferences();
		}
		IJ.cleanup();
		//setVisible(false);
		//IJ.log("dispose");
		dispose();
		if (exitWhenQuitting)
			System.exit(0);
	}
	
	void saveWindowLocations() {
		Frame frame = WindowManager.getFrame("Display");
		if (frame!=null)
			Prefs.saveLocation(ContrastAdjuster.LOC_KEY, frame.getLocation());
		frame = WindowManager.getFrame("Threshold");
		if (frame!=null)
			Prefs.saveLocation(ThresholdAdjuster.LOC_KEY, frame.getLocation());
		frame = WindowManager.getFrame("Results");
		if (frame!=null) {
			Prefs.saveLocation(TextWindow.LOC_KEY, frame.getLocation());
			Dimension d = frame.getSize();
			Prefs.set(TextWindow.WIDTH_KEY, d.width);
			Prefs.set(TextWindow.HEIGHT_KEY, d.height);
		}
	}

	/** Specified by the SingleInstanceListener interface
    @param args The command line parameters used for this invocation */
	public void newActivation(String[] args) {
		int nArgs = args!=null?args.length:0;

		boolean remote = false;
		ArrayList<String> rmiArgsArrayList = new ArrayList<String>();
		String concat = "";
		StringBuffer sb = new StringBuffer();
		for (int ii=0; ii<args.length; ii++) {
			sb.append("'" + args[ii] + "' ");
		}
		String message = "CytoSHOW is already running! \nTo open another movie or scene from a web link, \nplease drag and drop the link \n" +
//				sb.toString().replaceAll(".*(\"http:.*\").*", "$1") +
				"onto the bottom of the CytoSHOW toolbar";
		// this usually serves to alert the user the app.
		// wants attention.  On Win. it will flash the
		// apps. icon in the task bar.
		for (int ii=0; ii<args.length; ii++) {
			if (args[ii]!=null && args[ii].startsWith("-eval") && ii+1<nArgs) {
				IJ.runMacro(args[ii+1]);
				args[ii+1] = null;
				break;
			} else if (args[ii]!=null && args[ii].startsWith("-remote")) {
				remote = true;
				if (IJ.rmiURL == null || !IJ.rmiURL.matches("\\/\\/\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?:\\d\\d?\\d?\\d? \\d\\d?\\d?\\d?")) {
					IJ.rmiURL = "//155.37.253.202:8084 1";
				}
				if (args.length > ii+2) {
					if (args[ii+1].matches("\\/\\/\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?\\.\\d\\d?\\d?:\\d\\d?\\d?\\d?")
							&& args[ii+2].matches("\\d\\d?\\d?\\d?")) {
						IJ.rmiURL = args[ii+1]+" "+args[ii+2];
						args[ii+1] = null;				
						args[ii+2] = null;
					}
				}
				rmiArgsArrayList.add(IJ.rmiURL.split(" ")[0]);
				rmiArgsArrayList.add(IJ.rmiURL.split(" ")[1]);
				if (args.length > ii+1) {
					for (int j = ii+1;  j < args.length; j++) {
						if (args[j] != null && !args[j].startsWith("-")) {
							rmiArgsArrayList.add(""+args[j]);
							args[j] = null;
						} else if (args[j] != null && args[j].startsWith("-")) {
							break;
						}
					}
				}
				Iterator<String> argIt = rmiArgsArrayList.iterator();
				argIt.next();
				argIt.next();
				while (argIt.hasNext()) {
					concat = concat+argIt.next();
					if (argIt.hasNext())
						concat = concat+"|";
				}
				IJ.log(concat);
			}
		}
//		IJ.showMessage("CytoSHOW is already running!" + concat, message);
		if (remote) {
			if (!concat.contains("scene.scn") && !concat.contains("suite.ste")) {
				RemoteMQTVSHandler rmqtvsh = RemoteMQTVSHandler.build(IJ.rmiURL.split(" ")[0], IJ.rmiURL.split(" ")[1], concat.replace("|"," "), 
						false, true, true, false, true, false, false, false, false);
					ImagePlus imp = rmqtvsh.getImagePlus();
					imp.getWindow().setVisible(true);
			} else {
				for (String scene:concat.split("\\|")) {
					if (concat.contains("scene.scn")){
						if (IJ.is64Bit())
							MQTVSSceneLoader64.runMQTVS_SceneLoader64(scene).getImp().getWindow().setVisible(true);
						else
							MQTVSSceneLoader64.runMQTVS_SceneLoader64(scene).getImp().getWindow().setVisible(true);	
					} else {
						Opener opener = new Opener();
						opener.open(scene);
					}
				}
			}
		}
	}

	public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
		openPopupsArrayList.add((JPopupMenu)e.getSource());
//		for(JPopupMenu openPopup:openPopupsArrayList) {
//			if (!openPopup.equals(Menus.getPopupMenu())
//					&&!openPopup.isAncestorOf(Menus.getPopupMenu())
//					&& !openPopup.isAncestorOf(((JPopupMenu)e.getSource())) 
//					&& openPopup != (JPopupMenu)e.getSource()){
//				if (!(((JPopupMenu)e.getSource()).getInvoker() instanceof ImageCanvas)) {
//				openPopup.setVisible(false);
//				openPopupsArrayList.remove(openPopup);
//					
//				}
//			}
//		}
	}

	public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
		openPopupsArrayList.remove((JPopupMenu)e.getSource());
		
	}

	public void popupMenuCanceled(PopupMenuEvent e) {
		openPopupsArrayList.remove((JPopupMenu)e.getSource());
		
	}
	
	public static String getCommandName() {
		return commandName!=null?commandName:"null";
	}
	
	public static void setCommandName(String name) {
		commandName = name;
	}
	
}

