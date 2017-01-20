package ij.plugin.frame;
import ij.*;
import ij.plugin.*;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.gui.*;

import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.vcell.gloworm.MultiQTVirtualStack;

import SmartCaptureLite.r;

/** Displays the CytoSHOW ColorLegend window. */
public class ColorLegend extends PlugInFrame implements PlugIn, ItemListener, ActionListener, MouseMotionListener, WindowFocusListener, MouseListener  {

	String[] modes = {"Display Both Checked & Unchecked", "Hide Checked", "Show Checked", "Blink Checked"};
	boolean blinkChecked = true;
	boolean showChecked;
	boolean hideChecked;
	private  String moreLabel = "More "+'\u00bb';
	private Choice choice;
	private Checkbox[] checkbox;
	private Button moreButton;

	private int id;
	private static Point location;
	private PopupMenu pm;
	private RoiManager rm;
	private ImagePlus bbImp;
	private ImagePlus bbImpCopy;
	private ScheduledThreadPoolExecutor blinkService = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture schfut;
	private ScheduledThreadPoolExecutor blinkService2 = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture schfut2;
	private Color selectColor;
	private ImageProcessor ip;
	private ImageProcessor ipCopy;
	private Checkbox chosenCB;
	private Color mouseLocColor;
	private Hashtable<String, Color> brainbowColors;
	private Hashtable<Color, Checkbox> checkboxHash;
	private ImagePlus bb2Imp;
	private ImageJ ij;
	private boolean popupHappened;
	private Color chosenColor;
	private Hashtable<Integer, String> selectedColorRGBs;
	Button clearButton = new Button("Clear All CheckBoxes");
	private boolean sketchyMQTVS = true;
	private ImageProcessor lastIp;
	private boolean changedCBorChoice;
	public Hashtable<Integer, Color> droppedCellColors = new Hashtable<Integer, Color>();

	public ColorLegend(ImagePlus imp, String clStr) {
		super("Color Legend");
		this.setVisible(false);
		ij = IJ.getInstance();
		bbImp = imp;
		this.rm = bbImp.getRoiManager();
		rm.setColorLegend(this);
		if (bbImp.getCanvas() != null) {
			bbImp.getCanvas().addMouseMotionListener(this);
			bbImp.getCanvas().addMouseListener(this);
		}
		if (bbImp.getStack() instanceof MultiQTVirtualStack) {
			for (int i=0;i<bbImp.getNChannels();i++) {
				if ( ((MultiQTVirtualStack) bbImp.getStack()).getVirtualStack(i).getMovieName()
						.startsWith("Sketch3D")) {
					sketchyMQTVS = true;
				}
			}
		}
//		WindowManager.addWindow(this);
		ScrollPane fsp = new ScrollPane();
		GridBagLayout fspgridbag = new GridBagLayout();
		GridBagConstraints fspc = new GridBagConstraints();
		fspgridbag.setConstraints(fsp, fspc);
		Panel fspp = new Panel(fspgridbag);
		fsp.add(fspp,fspc);

		fspp.setLayout(fspgridbag);
		
		int y = 0;
		fspc.gridx = 0;
		fspc.gridy = y++;
		fspc.gridwidth = 1;
		fspc.fill = GridBagConstraints.BOTH;
		fspc.anchor = GridBagConstraints.CENTER;
		int margin = 32;
		if (IJ.isVista())
			margin = 40;
		else if (IJ.isMacOSX())
			margin = 18;
		fspc.insets = new Insets(0, 0, 0, 0);
		brainbowColors = new Hashtable<String,Color>();

		checkboxHash = new Hashtable<Color,Checkbox>();
		int count=0;
		int panelWidth =0;
		int panelHeight =0;
		String[] clLines = clStr.split("\n");
		checkbox = new Checkbox[clLines.length];
		for (int i=0; i<clLines.length; i++) {		
//			IJ.log(clLines[i]);
			checkbox[i] = new Checkbox();
			((Checkbox)checkbox[i]).setSize(150, 10);
			checkbox[i].setLabel(clLines[i].split(",")[0].length()<20?clLines[i].split(",")[0]:clLines[i].split(",")[0].substring(0, 20) + "...");
			checkbox[i].setName(clLines[i].split(",")[0]);			
			checkbox[i].setBackground(Colors.decode(clLines[i].split(",")[2], Color.white));
			brainbowColors.put(checkbox[i].getName().toLowerCase(), Colors.decode(clLines[i].split(",")[2], Color.white));	
				if (clLines[i].split(",")[0] != clLines[i].split(",")[1])
					brainbowColors.put(clLines[i].split(",")[1].toLowerCase(), Colors.decode(clLines[i].split(",")[2], Color.white));	

			checkboxHash.put(Colors.decode(clLines[i].split(",")[2], Color.white), checkbox[i]);
			checkbox[i].setFont(Menus.getFont().deriveFont(8));
//			IJ.log("WHATUP?");

			if (!clLines[i].split(",")[0].contains("NOTE:")) {
				fspp.add(checkbox[i],fspc);
				if (count==0) {
					panelWidth = panelWidth + checkbox[i].getWidth();
					count++;
					panelHeight = panelHeight+checkbox[i].getHeight();
					fspc.gridx++;
				} else if (count>=1 && fspc.gridx < 4){
					if (count < 4){
						panelWidth = panelWidth + checkbox[i].getWidth();
						fspc.gridx++;
//						count=0;
					}
				} else {
					fspc.gridy++;
					fspc.gridx=0;
				}
				checkbox[i].addItemListener(this);
				checkbox[i].addMouseListener(this);
			}
		}
		
		fspc.gridx =0;
		fspc.gridy= clLines.length/4+1;		
		choice = new Choice();
		for (int i=0; i<modes.length; i++)
			choice.addItem(modes[i]);
		choice.select(3);
		choice.addItemListener(this);
//		if (sketchyMQTVS)
//			choice.setEnabled(false);
		update();
		addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts

		setLayout(new BorderLayout());
		add(choice, BorderLayout.PAGE_START);
		Panel p = new Panel();
		p.setLayout(new BorderLayout());
		p.add(clearButton, BorderLayout.NORTH);
		clearButton.addActionListener(this);
		p.add(fsp, BorderLayout.CENTER);
		add(p, BorderLayout.CENTER);

//		hideChecked = true;
		
		this.setResizable(true);
		this.pack();
		this.setSize(fspp.getWidth()+30, fspp.getHeight()+30+choice.getHeight());
		if (location==null) {
			GUI.center(this);
			location = getLocation();
		} else
			setLocation(location);
//		this.setVisible(true);
		
	}
	
	
	public ColorLegend(RoiManager rm) {
		super("Color Legend");
		this.setVisible(false);
		ij = IJ.getInstance();
		this.rm = rm;
		bbImp = WindowManager.getCurrentImage();
		bbImp.getCanvas().addMouseMotionListener(this);
		bbImp.getCanvas().addMouseListener(this);
		if (bbImp.getStack() instanceof MultiQTVirtualStack) {
			for (int i=0;i<bbImp.getNChannels();i++) {
				if ( ((MultiQTVirtualStack) bbImp.getStack()).getVirtualStack(i).getMovieName()
						.startsWith("Sketch3D")) {
					sketchyMQTVS = true;
				}
			}
		}
//		WindowManager.addWindow(this);
		ScrollPane fsp = new ScrollPane();
		GridBagLayout fspgridbag = new GridBagLayout();
		GridBagConstraints fspc = new GridBagConstraints();
		fspgridbag.setConstraints(fsp, fspc);
		Panel fspp = new Panel(fspgridbag);
		fsp.add(fspp,fspc);

		fspp.setLayout(fspgridbag);
		
		int y = 0;
		fspc.gridx = 0;
		fspc.gridy = y++;
		fspc.gridwidth = 1;
		fspc.fill = GridBagConstraints.BOTH;
		fspc.anchor = GridBagConstraints.CENTER;
		int margin = 32;
		if (IJ.isVista())
			margin = 40;
		else if (IJ.isMacOSX())
			margin = 18;
		fspc.insets = new Insets(0, 0, 0, 0);

		CompositeImage ci = getImage();
		int nCheckBoxes = ci!=null?ci.getNChannels():3;
		if (nCheckBoxes>CompositeImage.MAX_CHANNELS)
			nCheckBoxes = CompositeImage.MAX_CHANNELS;//		checkbox = new Checkbox[nCheckBoxes];
		ArrayList<String> fullCellNames = new ArrayList<String>(); 
		if (rm.getFullCellNames()!=null) {
			for (int i =0;i<rm.getFullCellNames().size();i++)
				fullCellNames.add(""+rm.getFullCellNames().get(i)+"");
		} else {
			Roi[] fullRois = rm.getFullRoisAsArray();
			for (int r =0;r<fullRois.length;r++) {
					String[] searchTextChunks = fullRois[r].getName().split("[\"|=]")[1].split(" ");
					String searchText = "";
					for (String chunk:searchTextChunks)
						if (!(chunk.matches("-?\\d+") || chunk.matches("\\++")))
							searchText = searchText + " " + chunk;
					if ( !fullCellNames.contains(searchText.trim())) {
						fullCellNames.add(searchText.trim());				
					}
			}
		}
		if (rm.getColorLegend() != null)
			brainbowColors = rm.getColorLegend().getBrainbowColors();
		else {
			rm.setColorLegend(this);
			brainbowColors = this.getBrainbowColors();
		}
		checkbox = new Checkbox[fullCellNames.size()];
		checkboxHash = new Hashtable<Color,Checkbox>();
		int count=0;
		int panelWidth =0;
		int panelHeight =0;
		for (int i=0; i<fullCellNames.size(); i++) {
			checkbox[i] = new Checkbox();
			((Checkbox)checkbox[i]).setSize(150, 10);
			checkbox[i].setLabel(fullCellNames.get(i).length()<20?fullCellNames.get(i):fullCellNames.get(i).substring(0, 20) + "...");
			checkbox[i].setName(fullCellNames.get(i));			
			checkbox[i].setBackground(brainbowColors.get(fullCellNames.get(i).toLowerCase()));
			if (!fullCellNames.get(i).contains("NOTE:")) {
				checkboxHash.put(brainbowColors.get(fullCellNames.get(i).toLowerCase()), checkbox[i]);
				checkbox[i].setFont(Menus.getFont().deriveFont(8));
			         
				fspp.add(checkbox[i],fspc);
				if (count==0) {
					panelWidth = panelWidth + checkbox[i].getWidth();
					count++;
					panelHeight = panelHeight+checkbox[i].getHeight();
					fspc.gridx++;
				} else if (count>=1 && fspc.gridx < 4){
					if (count < 4){
						panelWidth = panelWidth + checkbox[i].getWidth();
						fspc.gridx++;
//						count=0;
					}
				} else {
					fspc.gridy++;
					fspc.gridx=0;
				}
				checkbox[i].addItemListener(this);
				checkbox[i].addMouseListener(this);
			}
		}
		
		fspc.gridx =0;
		fspc.gridy= fullCellNames.size()/4+1;		
		choice = new Choice();
		for (int i=0; i<modes.length; i++)
			choice.addItem(modes[i]);
		choice.select(3);
		choice.addItemListener(this);
//		if (sketchyMQTVS)
//			choice.setEnabled(false);

		update();
		addKeyListener(IJ.getInstance());  // ImageJ handles keyboard shortcuts

		setLayout(new BorderLayout());
		add(choice, BorderLayout.PAGE_START);
		Panel p = new Panel();
		p.setLayout(new BorderLayout());
		p.add(clearButton, BorderLayout.NORTH);
		clearButton.addActionListener(this);
		p.add(fsp, BorderLayout.CENTER);
		add(p, BorderLayout.CENTER);
//		hideChecked = true;
		
		this.setResizable(true);
		this.pack();
		this.setSize(fspp.getWidth()+30, fspp.getHeight()+30+choice.getHeight());
		if (location==null) {
			GUI.center(this);
			location = getLocation();
		} else
			setLocation(location);
		this.setVisible(true);
		show();
	}
	
	public void update() {
	}
	
	void addPopupItem(String s) {
		MenuItem mi=new MenuItem(s);
		mi.addActionListener(this);
		pm.add(mi);
	}

	CompositeImage getImage() {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null || !imp.isComposite())
			return null;
		else
			return (CompositeImage)imp;
	}

	public void itemStateChanged(ItemEvent e) {

		Object source = e.getSource();
		if (bbImp!=IJ.getImage()) {
			bbImp = IJ.getImage();
			bbImp.getCanvas().addMouseMotionListener(this);
		}
//		IJ.log(choice.getSelectedItem());		
		ipCopy = bbImp.getStack().getProcessor(bbImp.getSlice()).duplicate();
//		IJ.log(choice.getSelectedItem());
//		IJ.log(choice.getSelectedItem());
		if (source instanceof Choice) {
			
//			IJ.log(choice.getSelectedItem());
			String previousChoice = blinkChecked?"blink":(showChecked?"show":"hide");
			if (choice.getSelectedItem() == "Blink Checked") {
				blinkChecked = true;
				showChecked = false;
				hideChecked = false;
			}else if (choice.getSelectedItem() == "Show Checked") {
				blinkChecked = false;
				showChecked = true;
				hideChecked = false;
			}else if (choice.getSelectedItem() == "Hide Checked" 
						|| choice.getSelectedItem() == "Display Both Checked & Unchecked") {
				blinkChecked = false;
				showChecked = false;
				hideChecked = (choice.getSelectedItem() == "Hide Checked");
			}
			changedCBorChoice = true;
			bbImp.updateAndRepaintWindow();
		}
		if (source instanceof Checkbox /*&& (!bbImp.equals(rm.getImagePlus()) || sketchyMQTVS)*/) {
//			IJ.showStatus("CLICK");
			changedCBorChoice = true;
//			droppedCellColors.clear();
			bbImp.updateAndRepaintWindow();
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		String command = e.getActionCommand();
		if (command==null) return;
		if (command.equals(moreLabel)) {
			Point bloc = moreButton.getLocation();
			pm.show(this, bloc.x, bloc.y);
		} else if (command.equals("Convert to RGB"))
			IJ.doCommand("Stack to RGB");
		else if (e.getSource() == clearButton) {
			droppedCellColors.clear();
			for (Checkbox cbq:checkbox) {
				if (cbq.getState()) {
					cbq.setState(false);
				}
			}
			this.itemStateChanged(new ItemEvent(checkbox[0], ItemEvent.ITEM_STATE_CHANGED, checkbox[0], ItemEvent.SELECTED));
		} else
			IJ.doCommand(command);
	}
		
	public void close() {
		if (this.getRoiManager()==null) {
			super.close();
//			rm.setColorLegend(null);
			location = getLocation();
			dispose();
		} else
			this.setVisible(false);
	}
	
    public void dispose() {
    	while (bbImp != null || bbImpCopy != null) {
    		bbImp = null;
    		bbImpCopy = null;
    	}
        this.removeKeyListener(IJ.getInstance());
        super.dispose();
    }


	public void mouseDragged(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseMoved(MouseEvent e) {

		Object source = e.getSource();
		bb2Imp = ((ImageCanvas)source).getImage();
		if (rm == null || (bb2Imp == rm.getImagePlus()  && !bb2Imp.getTitle().startsWith("Sketch3D") && !sketchyMQTVS))
			return;

//		IJ.log(" " +e.getX()+" " + e.getY()+" " + ((ColorProcessor)bb2Imp.getIP()).getWidth()+" " + ((ColorProcessor)bb2Imp.getIP()).getHeight());
		
		mouseLocColor = (bb2Imp.getIP() instanceof ColorProcessor?((ColorProcessor)bb2Imp.getIP())
				.getColor(bb2Imp.getCanvas().offScreenX(e.getX()),
						bb2Imp.getCanvas().offScreenY(e.getY())):Color.white);

		if (mouseLocColor.equals(chosenColor)){
			return;
		}else if (mouseLocColor.equals(Color.black)) {
			chosenCB = null;
			chosenColor = null;
			return;
		}
//		IJ.showStatus(""+sketchyMQTVS+1);

		for (Checkbox cbC:checkbox) {
			if (cbC.getBackground().equals(mouseLocColor)){
//				IJ.showStatus(""+sketchyMQTVS+1);
				chosenCB = cbC;
				chosenColor= mouseLocColor;
				if (schfut2 != null)
					schfut2.cancel(true);
				schfut2 = blinkService2.scheduleAtFixedRate(new Runnable()
				{
					public void run()
					{
						if (chosenCB.getBackground().equals(mouseLocColor))
							chosenCB.setBackground(mouseLocColor.darker().darker());
						else
							chosenCB.setBackground(brainbowColors.get(chosenCB.getName().toLowerCase()));
					}
				}, 0, 500, TimeUnit.MILLISECONDS);

			} else{
//				IJ.showStatus(""+sketchyMQTVS+1);
				if (bbImp != null && (bb2Imp == rm.getImagePlus()  
						&& !(bb2Imp.getTitle().startsWith("Sketch3D") || sketchyMQTVS)))
					cbC.setBackground(rm.getMowColors().get(cbC.getName()));
				else {
//					IJ.showStatus(""+sketchyMQTVS+1);
					cbC.setBackground(brainbowColors.get(cbC.getName().toLowerCase()));
				}
			}
		}	
	}

	public void windowGainedFocus(WindowEvent e) {
		if (rm == null)
			return;
		Object source = e.getSource();
		if (bbImp==null || bbImp.getWindow()!=source) {
			bbImp = IJ.getImage();
//			bbImp.getCanvas().addMouseMotionListener(this);
			bbImpCopy = bbImp.getDupImp();
			for (int i=0; i<rm.getNameLists().size(); i++){
				if (bbImp.equals(rm.getCompImps().get(i<rm.getCompImps().size()?i:rm.getCompImps().size()-1))
						|| bbImp.equals(rm.getProjYImps().get(i<rm.getProjYImps().size()?i:rm.getProjYImps().size()-1)) 
						|| bbImp.equals(rm.getProjZImps().get(i<rm.getProjZImps().size()?i:rm.getProjZImps().size()-1))) {
					for (Checkbox cb:checkbox){
//						cb.setBackground(rm.getBrainbowColors().get(cb.getName()));
						if (rm.getNameLists().get(i).contains(cb.getName())) {
							cb.setLabel(cb.getLabel().replaceAll("[()]", ""));
							cb.setFont(cb.getFont().deriveFont(Font.PLAIN + Font.BOLD ));
						} else{
							cb.setLabel("("+cb.getLabel().replaceAll("[()]", "")+")");
							cb.setFont(cb.getFont().deriveFont(Font.PLAIN + Font.ITALIC));
						}
					}
				} 
			}
		}
		if (bbImpCopy==null && !bbImp.equals(rm.getImagePlus())) {
			bbImpCopy = new ImagePlus("bbImpCopy", bbImp.getStack().getProcessor(bbImp.getCurrentSlice()));
			bbImp.setDupImp(bbImpCopy);	
		}
		this.repaint();
		bbImp.updateAndDraw();
	}

	public void windowLostFocus(WindowEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseClicked(MouseEvent e) {
		boolean selectMore = IJ.shiftKeyDown();
		boolean selectLineage = IJ.altKeyDown();
//		IJ.showStatus("mouseclick");
		Object source = e.getSource();
		if (source instanceof Checkbox)
			return;
		if (bbImp!=null && bbImp.getCanvas()!= (ImageCanvas)source)
			bbImp=null;
		else {
			bbImp = ((ImageCanvas)source).getImage();
			if (bbImp.getIP() instanceof ColorProcessor) 
				mouseLocColor = ((ColorProcessor)bbImp.getIP())
				.getColor(bbImp.getCanvas().offScreenX(e.getX()),
					bbImp.getCanvas().offScreenY(e.getY()));
			if (!selectMore && mouseLocColor!=null && !mouseLocColor.equals(Color.black)){
				for (Checkbox cbq:checkbox) {
					if (cbq.getState()) {
						cbq.setState(false);
//						this.itemStateChanged(new ItemEvent(cbq,0,null,ItemEvent.DESELECTED));
					}
				}
			}
			Checkbox cbC = checkboxHash.get(mouseLocColor);
			if (cbC == null)
				cbC = checkboxHash.get(mouseLocColor.darker().darker());
			if (cbC != null) {	
				chosenCB = cbC;
				chosenCB.setState(true);
				if (selectLineage) {
					for (Checkbox cbq:checkbox) {
						if (cbq.getName().trim().contains(cbC.getName().trim())
								|| cbC.getName().trim().contains(cbq.getName().trim())
								|| (cbC.getName().matches("P1") && cbq.getName().matches("P.|C.*|D.*|E.*|MS.*|Z."))
								|| (cbC.getName().matches("P2") && cbq.getName().matches("P.|C.*|D.*|Z."))
								|| (cbC.getName().matches("P3") && cbq.getName().matches("P.|D.*|Z."))
								|| (cbC.getName().matches("P4") && cbq.getName().matches("P.|Z."))
								|| (cbC.getName().matches("EMS") && cbq.getName().matches("E.*|MS.*"))
								|| (cbq.getName().matches("P1") && cbC.getName().matches("P.|C.*|D.*|E.*|MS.*|Z."))
								|| (cbq.getName().matches("P2") && cbC.getName().matches("P.|C.*|D.*|Z."))
								|| (cbq.getName().matches("P3") && cbC.getName().matches("P.|D.*|Z."))
								|| (cbq.getName().matches("P4") && cbC.getName().matches("P.|Z."))
								|| (cbq.getName().matches("EMS") && cbC.getName().matches("E.*|MS.*"))
								) {
							cbq.setState(true);
						}
					}
				}
				this.itemStateChanged(new ItemEvent(chosenCB,ItemEvent.ITEM_STATE_CHANGED,chosenCB,ItemEvent.SELECTED));
			} else 
				chosenCB = null;
			
		}
	}

	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger() && e.getSource() instanceof Checkbox) {
			for (int i=0; i<rm.getCompImps().size(); i++){
				if (rm.getCompImps().get(i) != null) {
					rm.getCompImps().get(i).getCanvas().handlePopupMenu(e);
					popupHappened = true;
					i = rm.getCompImps().size();
				}
			}
		}
	}

	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public RoiManager getRoiManager() {
		// TODO Auto-generated method stub
		return rm;
	}

	public Checkbox[] getCheckbox() {
		return checkbox;
	}

	public void setCheckbox(Checkbox[] checkbox) {
		this.checkbox = checkbox;
	}

	public Choice getChoice() {
		return choice;
	}

	public void setChoice(Choice choice) {
		this.choice = choice;
	}

	public Checkbox getChosenCB() {
		return chosenCB;
	}

	public void setChosenCB(Checkbox chosenCB) {
		this.chosenCB = chosenCB;
	}

	public boolean isPopupHappened() {
		return popupHappened;
	}

	public void setPopupHappened(boolean popupHappened) {
		this.popupHappened = popupHappened;
	}

	public void setRoiManager(RoiManager rm) {
		// TODO Auto-generated method stub
		this.rm = rm;
	}

	public void save(String path) throws IOException {
		File clFile = new File(path);
		String clString = "";
		for (Checkbox cb:getCheckbox()) {
			clString = clString + cb.getName() +","+ cb.getLabel() +","
						+ Colors.colorToString(cb.getBackground()) +","
						+ cb.getBackground().getRed() +","
						+ cb.getBackground().getGreen() +","
						+ cb.getBackground().getBlue() +"\n";
		}
		clFile.createNewFile();

		FileWriter out = new FileWriter(clFile);
		out.write(clString);
		out.close();
	}
	
	public ColorLegend clone(ImagePlus imp) {
		String clString = "";
		for (Checkbox cb:getCheckbox()) {
			clString = clString + cb.getName() +","+ cb.getLabel() +","
						+ Colors.colorToString(cb.getBackground()) +","
						+ cb.getBackground().getRed() +","
						+ cb.getBackground().getGreen() +","
						+ cb.getBackground().getBlue() +"\n";
		}

		ColorLegend cl2 = new ColorLegend(imp, clString);
		return cl2;
	}

	private boolean blinked;
	public int countHits;
	
	public ImageProcessor processedIP(ImageProcessor ip2) {
		ip = ip2;
		if (ip == ipCopy)
			return ip;
		changedCBorChoice = false;
		lastIp = ip;
		ipCopy = ip2.duplicate();
		selectedColorRGBs = new Hashtable<Integer,String>();
		for (Checkbox cbOther:checkbox) {
			if (cbOther.getState()) {
				selectedColorRGBs.put(brainbowColors.get(cbOther.getName().toLowerCase()).getRGB(),""+brainbowColors.get(cbOther.getName().toLowerCase()).getRGB());
//				selectedColorRGBs.add(brainbowColors.get(cbOther.getName()).darker().getRGB());
//				selectedColorRGBs.add(brainbowColors.get(cbOther.getName()).darker().darker().getRGB());
			}
		}
		if (selectedColorRGBs.size()>0) {
//			IJ.log("processingip");
			if (blinkChecked) {
				//			IJ.log("blink");
				if (schfut == null || schfut.isDone()) {
//					IJ.log("newblinker");

					schfut = blinkService.scheduleAtFixedRate(new Runnable()
					{
						public void run()
						{
							for (int x=0; x<ip.getWidth(); x++){
								for (int y=0; y<ip.getHeight(); y++){
									if (selectedColorRGBs.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
										if (!blinked) {
											if(droppedCellColors.size()>0 && droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
												((ColorProcessor)ip).set(x, y, droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y)).getRGB());
											} else {
												((ColorProcessor)ip).set(x, y, Color.decode(""+((ColorProcessor)ipCopy).get(x,y)).darker().darker().darker().getRGB());
											}
										} else {
											((ColorProcessor)ip).set(x, y, ((ColorProcessor)ipCopy).get(x,y));
										}
									}
								}
							}
							blinked = !blinked;
							bbImp.getCanvas().paintDoubleBuffered(bbImp.getCanvas().getGraphics());
						}
					}, 0, 500, TimeUnit.MILLISECONDS);
				}
			} else if (hideChecked || showChecked) {
				//			IJ.log("hideorshow");
				if (schfut != null)
					schfut.cancel(true);
				for (int x=0; x<ip.getWidth(); x++){
					for (int y=0; y<ip.getHeight(); y++){
						if ((selectedColorRGBs.get(((ColorProcessor)ipCopy).get(x,y))==null && hideChecked) 
								|| (selectedColorRGBs.get(((ColorProcessor)ipCopy).get(x,y))!=null && showChecked)){
							if(droppedCellColors.size()>0 && droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
								((ColorProcessor)ip).set(x, y, droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y)).getRGB());
							} else {
								((ColorProcessor)ip).set(x, y, ((ColorProcessor)ipCopy).get(x,y));
							}
						} else
							((ColorProcessor)ip).set(x, y, Color.decode(""+((ColorProcessor)ipCopy).get(x,y)).darker().darker().darker().getRGB());
					}
				}
				//			bbImp.updateAndRepaintWindow();
			} else {
				//			IJ.log("else");
				if (schfut != null)
					schfut.cancel(true);
				for (int x=0; x<ip.getWidth(); x++){
					for (int y=0; y<ip.getHeight(); y++){
						if(droppedCellColors.size()>0 && droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y))!=null) {
							((ColorProcessor)ip).set(x, y, droppedCellColors.get(((ColorProcessor)ipCopy).get(x,y)).getRGB());
						} else {
							((ColorProcessor)ip).set(x, y, Color.decode(""+((ColorProcessor)ipCopy).get(x,y)).getRGB());
						}
					}
				}
				//			bbImp.updateAndRepaintWindow();
			}
		}
		return ip;
	}


	public void checkFromCellsRegex(ImagePlus dropImp, String cellsRegex) {
		bbImp = dropImp;
		countHits =0;
		for (Checkbox cb:checkbox) {
			cb.setState((cb.getName().toLowerCase().trim()+" ").matches(cellsRegex.toLowerCase()));
			countHits++;
		}
//		this.itemStateChanged(new ItemEvent(checkbox[0],ItemEvent.ITEM_STATE_CHANGED,checkbox[0],ItemEvent.SELECTED));
		bbImp.updateAndDraw();
		if (rm !=null)
			rm.setSearching(false);
		return;
	}


	public Hashtable<String, Color> getBrainbowColors() {
		// TODO Auto-generated method stub
		return brainbowColors;
	}
}
