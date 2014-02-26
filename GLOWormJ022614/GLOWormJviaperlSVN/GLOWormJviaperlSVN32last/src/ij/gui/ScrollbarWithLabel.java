package ij.gui;
import ij.CompositeImage;
import ij.IJ;
import ij.plugin.Colors;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.awt.image.BufferedImage;


/** This class, based on Joachim Walter's Image5D package, adds "c", "z" labels 
	 and play-pause icons (T) to the stack and hyperstacks dimension sliders.
 * @author Joachim Walter
 */
public class ScrollbarWithLabel extends Panel implements Adjustable, MouseListener, AdjustmentListener {
	Scrollbar bar;
	private Icon icon;
	private Icon icon2;
	private StackWindow stackWindow;
	transient AdjustmentListener adjustmentListener;
	public char label;
	private boolean iconEnabled;
	
	public ScrollbarWithLabel() {
	}

	public ScrollbarWithLabel(StackWindow stackWindow, int value, int visible, int minimum, int maximum, char label) {
		super(new BorderLayout(2, 0));
		addMouseListener(this);
		this.stackWindow = stackWindow;
		this.label = label;
		bar = new Scrollbar(Scrollbar.HORIZONTAL, value, visible, minimum, maximum);
		bar.addMouseListener(this);
		setBarCursor(label);
		icon = new Icon(label);
		icon2 = new Icon(label);
		add(icon, BorderLayout.WEST);
		add(bar, BorderLayout.CENTER);
		add(icon2, BorderLayout.EAST);
		bar.addAdjustmentListener(this);
		addKeyListener(IJ.getInstance()); 
		iconEnabled = true;
	}

	private void setBarCursor(char label) {
		Toolkit tk = Toolkit.getDefaultToolkit();
		String cursorString = label+"="+bar.getValue();
		Font font = Font.decode("Arial-Outline-18");
        
        //create the FontRenderContext object which helps us to measure the text
        FontRenderContext frc = new FontRenderContext(null, true, true);
         
        //get the height and width of the text
        Rectangle2D bounds = font.getStringBounds(cursorString, frc);
        int w = (int) bounds.getWidth();
        int h = (int) bounds.getHeight();
		Image img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
		
//		img.getGraphics().setColor(Colors.decode("00000000", Color.white));
        Graphics2D g = (Graphics2D) img.getGraphics();
        
        g.setFont(font);
        
//        g.setColor(Colors.decode("66ffffff",Color.white));
//        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.drawLine(0, 0, 2, 7);
        g.drawLine(0, 0, 7, 2);
        g.drawLine(0, 0, 8, 8);
		g.drawString(cursorString, 1, img.getHeight(null)-1);
		bar.setCursor(tk.createCustomCursor(img,new Point(0,0),"scrollCursor"));
	}
	
	/* (non-Javadoc)
	 * @see java.awt.Component#getPreferredSize()
	 */
	public Dimension getPreferredSize() {
		Dimension dim = new Dimension(0,0);
		int width = bar.getPreferredSize().width;
		Dimension minSize = getMinimumSize();
		if (width<minSize.width) width = minSize.width;		
		int height = bar.getPreferredSize().height;
		dim = new Dimension(width, height);
		return dim;
	}
	
	public Dimension getMinimumSize() {
		return new Dimension(80, 15);
	}
	
	/* Adds KeyListener also to all sub-components.
	 */
	public synchronized void addKeyListener(KeyListener l) {
		super.addKeyListener(l);
		bar.addKeyListener(l);
	}

	/* Removes KeyListener also from all sub-components.
	 */
	public synchronized void removeKeyListener(KeyListener l) {
		super.removeKeyListener(l);
		bar.removeKeyListener(l);
	}

	/* 
	 * Methods of the Adjustable interface
	 */
	public synchronized void addAdjustmentListener(AdjustmentListener l) {
		if (l == null) {
			return;
		}
		adjustmentListener = AWTEventMulticaster.add(adjustmentListener, l);
	}
	public int getBlockIncrement() {
		return bar.getBlockIncrement();
	}
	public int getMaximum() {
		return bar.getMaximum();
	}
	public int getMinimum() {
		return bar.getMinimum();
	}
	public int getOrientation() {
		return bar.getOrientation();
	}
	public int getUnitIncrement() {
		return bar.getUnitIncrement();
	}
	public int getValue() {
		return bar.getValue();
	}
	public int getVisibleAmount() {
		return bar.getVisibleAmount();
	}
	public synchronized void removeAdjustmentListener(AdjustmentListener l) {
		if (l == null) {
			return;
		}
		adjustmentListener = AWTEventMulticaster.remove(adjustmentListener, l);
	}
	public void setBlockIncrement(int b) {
		bar.setBlockIncrement(b);		 
	}
	public void setMaximum(int max) {
		bar.setMaximum(max);		
	}
	public void setMinimum(int min) {
		bar.setMinimum(min);		
	}
	public void setUnitIncrement(int u) {
		bar.setUnitIncrement(u);		
	}
	public void setValue(int v) {
		bar.setValue(v);		
		setBarCursor(label);
	}
	public void setVisibleAmount(int v) {
		bar.setVisibleAmount(v);		
	}

	public void setFocusable(boolean focusable) {
		super.setFocusable(focusable);
		bar.setFocusable(focusable);
	}
		
	/*
	 * Method of the AdjustmentListener interface.
	 */
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if (bar != null && e.getSource() == bar) {
			setBarCursor(label);
			AdjustmentEvent myE = new AdjustmentEvent(this, e.getID(), e.getAdjustmentType(), 
					e.getValue(), e.getValueIsAdjusting());
			AdjustmentListener listener = adjustmentListener;
			if (listener != null) {
				listener.adjustmentValueChanged(myE);
			}
		}
	}
		
	public void updatePlayPauseIcon() {
		icon.repaint();
		icon2.repaint();
	}
	
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseEntered(MouseEvent e) {
		IJ.runMacro("print(\"\\\\Clear\")");
		IJ.runMacro("print(\"\\\\Update:Movie Dimension Sliders:\\\n\'z\' and \'t\' Sliders adjust the movie position in space and time.\\\n\'c\' Slider selects which channel is being displayed or is adjusted by the Display Tool.\\\n\'r\' Slider adjusts the resolution (pixelation) of the display; low r => faster slice animation.\\\n \")");
		
	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	public char getType() {
		// TODO Auto-generated method stub
		return label;
	}


	
	class Icon extends Canvas implements MouseListener {
		private static final int WIDTH = 26, HEIGHT=14;
		private BasicStroke stroke = new BasicStroke(2f);
		private char type;
		private Image image;

		public Icon(char type) {
			addMouseListener(this);
			addKeyListener(IJ.getInstance()); 
			setSize(WIDTH, HEIGHT);
			this.type = type;
		}
		
		/** Overrides Component getPreferredSize(). */
		public Dimension getPreferredSize() {
			return new Dimension(WIDTH, HEIGHT);
		}
				
		public void update(Graphics g) {
			paint(g);
		}
		
		public void paint(Graphics g) {
			g.setColor(Color.white);
			if (type =='c') g.setColor(Color.black);
			g.fillRect(0, 0, WIDTH, HEIGHT);
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (type=='t' || type=='z')
				drawPlayPauseButton(g2d);
			
			drawLetter(g);
		}
		
		private void drawLetter(Graphics g) {
			g.setFont(new Font("SansSerif", Font.PLAIN, 14));
			if (type =='c') g.setFont(new Font("SansSerif", Font.PLAIN, 14));
			String string = "";
			if (type =='t'|| type =='z'|| type =='r') 
				string = String.valueOf(type);
				g.setColor(Color.black);
			if (type =='c') {
				string = String.valueOf(type);
				if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 3 ){
					string = "CG";
					g.setColor(Color.white);
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 2 ){
					string = "CC";
					g.setColor(Color.green);
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 1 ){
					string = "CM";
					g.setColor(Color.yellow);
				}
				else {
					g.setColor(Color.magenta);
				}
				
			}
			g.drawString(string, 2, 12); 
		}

		private void drawPlayPauseButton(Graphics2D g) {
			if ( (type =='t' && stackWindow.getAnimate()) 
					|| (type =='z' && stackWindow.getZAnimate())  
					|| (type =='z' && stackWindow.getAnimationSelector().getType() == 'z' && stackWindow.getAnimate()) 
					|| (type =='z' && stackWindow.getAnimationZSelector().getType() == 'z' && stackWindow.getZAnimate()) ) {
				g.setColor(Color.red);
				g.setStroke(stroke);
				g.drawLine(15, 3, 15, 11);
				g.drawLine(20, 3, 20, 11);
			} else {
				g.setColor(Color.green);
				GeneralPath path = new GeneralPath();
				path.moveTo(15f, 2f);
				path.lineTo(22f, 7f);
				path.lineTo(15f, 12f);
				path.lineTo(15f, 2f);
				g.fill(path);
			}
		}
		
		public void mousePressed(MouseEvent e) {
			if (type!='t' && type!='z' && type!='c' || !iconEnabled) return;
			int flags = e.getModifiers();
			if ((flags&(Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0){
				if (type =='t' || type =='z') IJ.doCommand("Animation Options...");
				else if (type =='c') IJ.run("Channels Tool...");
				
			}
			else if (type =='t' )
				IJ.doCommand("Start Animation [\\]");
			else if (type == 'z' && stackWindow.getAnimationSelector().getType() == 'z')
				IJ.doCommand("Start Animation [\\]");
			else if (type =='z' )
				IJ.doCommand("Start Z Animation");
			else if (type =='c' ){
				int origChannel = stackWindow.getImagePlus().getChannel();
				if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 3 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(1);
					boolean animationState = stackWindow.running2;
					boolean animationZState = stackWindow.running3;
					IJ.doCommand("Stop Animation");
					for (int j=1; j<=stackWindow.getImagePlus().getNChannels(); j++){
						stackWindow.setPosition(j, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame());
					}
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame());
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()+1);
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()-1);
					if (animationState) IJ.doCommand("Start Animation [\\]");
					if (animationZState) IJ.doCommand("Start Z Animation");
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 1 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(2);		
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()+1);
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()-1);
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 2 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(3);
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()+1);
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()-1);
				}
				stackWindow.cSelector.updatePlayPauseIcon();
			}


		}
		
		public void mouseReleased(MouseEvent e) {}
		public void mouseExited(MouseEvent e) {
//			IJ.runMacro("print(\"\\\\Clear\")");
		}
		public void mouseClicked(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {
			IJ.runMacro("print(\"\\\\Clear\")");
			if (type =='c' &&  stackWindow.getImagePlus().isComposite() )
				IJ.runMacro("print(\"\\\\Update:Channel Mode Button: \\\nLeft-Clicking this icon changes the Display Mode from \\\'Channels Merged\\\' to \\\'Channels Color\\\' to \\\'Channels Gray\\\'.\\\nRight-clicking or control-clicking activates the Channels Tool.\\\n \")");
			else if (type =='z')
				IJ.runMacro("print(\"\\\\Update:Space Animation Button: \\\nLeft-Clicking this icon plays or pauses animation through focus (or rotates the projected image).\\\nRight-clicking or control-clicking activates the Animation Options Tool.\\\n \")");
			else if (type =='t')
				IJ.runMacro("print(\"\\\\Update:Time Animation Button: \\\nLeft-Clicking this icon plays or pauses animation through time.\\\nRight-clicking or control-clicking activates the Animation Options Tool.\\\n \")");

		}
	
	} // StartStopIcon class



	public void setIconEnabled(boolean b) {
		iconEnabled = b;
	}




	
}
