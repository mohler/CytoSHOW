package ij.gui;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.Colors;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ShortProcessor;

import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;


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
	JButton iconPanel;
	JButton icon2Panel;
	private BufferedImage bi;
	private BufferedImage bi2;

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
		iconPanel = new IconButton();
		icon2Panel = new IconButton();
		icon = new Icon(label);
		icon2 = new Icon(label);
		bi = new BufferedImage(Icon.WIDTH, Icon.HEIGHT, BufferedImage.TYPE_INT_ARGB);
		iconPanel.setIcon(new ImageIcon(bi));
		bi2 = new BufferedImage(Icon.WIDTH, Icon.HEIGHT, BufferedImage.TYPE_INT_ARGB);
		icon2Panel.setIcon(new ImageIcon(bi2));
		iconPanel.addMouseListener(new MouseAdapter(){
            boolean pressed;

            @Override
            public void mousePressed(MouseEvent e) {
                iconPanel.getModel().setArmed(true);
                iconPanel.getModel().setPressed(true);
                pressed = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                //if(isRighticonPanelPressed) {underlyingiconPanel.getModel().setPressed(true));
                iconPanel.getModel().setArmed(false);
                iconPanel.getModel().setPressed(false);

                if (pressed) {
                	if (SwingUtilities.isRightMouseButton(e)) {
                		if (getType() == 'c') {
                			IJ.run("Channels Tool...");
                		} else {
                			IJ.doCommand("Animation Options...");
                		}
                	}
                	else {
                    }
                }
                pressed = false;

            }

            @Override
            public void mouseExited(MouseEvent e) {
                pressed = false;
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                pressed = true;
            }                    
        });
		icon2Panel.addMouseListener(new MouseAdapter(){
            boolean pressed;

            @Override
            public void mousePressed(MouseEvent e) {
                icon2Panel.getModel().setArmed(true);
                icon2Panel.getModel().setPressed(true);
                pressed = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                //if(isRighticonPanelPressed) {underlyingiconPanel.getModel().setPressed(true));
                icon2Panel.getModel().setArmed(false);
                icon2Panel.getModel().setPressed(false);

                if (pressed) {
                	if (SwingUtilities.isRightMouseButton(e)) {
                		if (getType() == 'c') {
                			IJ.run("Channels Tool...");
                		} else {
                			IJ.doCommand("Animation Options...");
                		}
                	}
                	else {
                    }
                 }
                pressed = false;

            }

            @Override
            public void mouseExited(MouseEvent e) {
                pressed = false;
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                pressed = true;
            }                    
        });
		add(iconPanel, BorderLayout.WEST);
		add(bar, BorderLayout.CENTER);
		add(icon2Panel, BorderLayout.EAST);
		bar.addAdjustmentListener(this);
		addKeyListener(IJ.getInstance()); 
		iconEnabled = true;
		updatePlayPauseIcon();
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
		iconPanel.addKeyListener(l);
		icon2Panel.addKeyListener(l);

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
		icon.update(bi.getGraphics());
		icon2.update(bi2.getGraphics());
		iconPanel.setIcon(new ImageIcon(bi));
		icon2Panel.setIcon(new ImageIcon(bi2));
	}

	public void mouseClicked(MouseEvent e) {
//		if (SwingUtilities.isRightMouseButton(e)) {
//			if (iconPanel.contains(e.getPoint())||icon2Panel.contains(e.getPoint())){
//				 if (getType() == 'c') {
//					 IJ.run("Channels Tool...");
//				 } else {
//					 IJ.doCommand("Animation Options...");
//				 }
//			}
//			
//		}

	}

	public void mouseEntered(MouseEvent e) {
		//		IJ.runMacro("print(\"\\\\Clear\")");
		//		IJ.runMacro("print(\"\\\\Update:Movie Dimension Sliders:\\\n\'z\' and \'t\' Sliders adjust the movie position in space and time.\\\n\'c\' Slider selects which channel is being displayed or is adjusted by the Display Tool.\\\n\'r\' Slider adjusts the resolution (pixelation) of the display; low r => faster slice animation.\\\n \")");

	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseReleased(MouseEvent e) {
		StackWindow swin = this.stackWindow;
		ImagePlus imp = swin.getImagePlus();
		swin.setPositionWithoutScrollbarCheck(imp.getChannel(), imp.getSlice(), imp.getFrame());
		imp.updateStatusbarValue();
	}

	public char getType() {
		// TODO Auto-generated method stub
		return label;
	}


	class IconButton extends JButton implements ActionListener{


		public IconButton() {
			super();
			this.addActionListener(this);
			// TODO Auto-generated constructor stub
		}

		public IconButton(Action a) {
			super(a);
			this.addActionListener(this);
			// TODO Auto-generated constructor stub
		}

		public IconButton(javax.swing.Icon icon) {
			super(icon);
			this.addActionListener(this);
			// TODO Auto-generated constructor stub
		}

		public IconButton(String text, javax.swing.Icon icon) {
			super(text, icon);
			this.addActionListener(this);
			// TODO Auto-generated constructor stub
		}

		public IconButton(String text) {
			super(text);
			this.addActionListener(this);
			// TODO Auto-generated constructor stub
		}

		public void actionPerformed(ActionEvent e) {
			if (getType()!='t' && getType()!='z' && getType()!='c' || !iconEnabled) return;
			int flags = e.getModifiers();
			if ((flags&(Event.ALT_MASK|Event.META_MASK|Event.CTRL_MASK))!=0){
				if (getType() =='t' || getType() =='z') IJ.doCommand("Animation Options...");
				else if (getType() =='c') IJ.run("Channels Tool...");
			}
			else if (getType() =='t' )
				IJ.doCommand("Start Animation [\\]");
			else if (getType() == 'z' && stackWindow.getAnimationSelector().getType() == 'z')
				IJ.doCommand("Start Animation [\\]");
			else if (getType() =='z' )
				IJ.doCommand("Start Z Animation");
			else if (getType() =='c' ){
				int origChannel = stackWindow.getImagePlus().getChannel();
				if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 6 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(1);
					if (stackWindow.getImagePlus().getType() == ImagePlus.GRAY16) {
						((CompositeImage) stackWindow.getImagePlus())
							.setProcessor(new ShortProcessor(stackWindow.getImagePlus().getWidth(), stackWindow.getImagePlus().getHeight()));
					} else if (stackWindow.getImagePlus().getType() == ImagePlus.GRAY8) {
						((CompositeImage) stackWindow.getImagePlus())
							.setProcessor(new ByteProcessor(stackWindow.getImagePlus().getWidth(), stackWindow.getImagePlus().getHeight()));
					} else if (stackWindow.getImagePlus().getType() == ImagePlus.GRAY32) {
						((CompositeImage) stackWindow.getImagePlus())
							.setProcessor(new FloatProcessor(stackWindow.getImagePlus().getWidth(), stackWindow.getImagePlus().getHeight()));
					}
					boolean animationState = stackWindow.running2;
					boolean animationZState = stackWindow.running3;
					IJ.doCommand("Stop Animation");
					for (int j=1; j<=stackWindow.getImagePlus().getNChannels(); j++){
						stackWindow.setPosition(j, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame());
					}
					stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame());


					if (stackWindow.getImagePlus().isComposite()) {
						int mode = ((CompositeImage)stackWindow.getImagePlus()).getMode();
						((CompositeImage)stackWindow.getImagePlus()).setMode(1);
						((CompositeImage)stackWindow.getImagePlus()).setMode(2);
						((CompositeImage)stackWindow.getImagePlus()).setMode(3);
						((CompositeImage)stackWindow.getImagePlus()).setMode(mode);
					}
					if (animationState) IJ.doCommand("Start Animation [\\]");
					if (animationZState) IJ.doCommand("Start Z Animation");
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 1 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(2);	
//					if (stackWindow.getImagePlus().getNFrames()>1) {
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()+1);
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()-1);
//					}
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 2 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(3);
//					if (stackWindow.getImagePlus().getNFrames()>1) {
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()+1);
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()-1);
//					}
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 3 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(5);
//					if (stackWindow.getImagePlus().getNFrames()>1) {
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()+1);
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()-1);
//					}
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 5 ){
					((CompositeImage) stackWindow.getImagePlus()).setMode(6);
//					if (stackWindow.getImagePlus().getNFrames()>1) {
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()+1);
						stackWindow.setPosition(origChannel, stackWindow.getImagePlus().getSlice(), stackWindow.getImagePlus().getFrame()-1);
//					}
				}
				stackWindow.cSelector.updatePlayPauseIcon();
			}
		}
	}


	class Icon extends Canvas {
		private static final int WIDTH = 26, HEIGHT=14;
		private BasicStroke stroke = new BasicStroke(2f);
		private char type;
		private Image image;

		public Icon(char type) {
//			addKeyListener(IJ.getInstance()); 
			setSize(WIDTH, HEIGHT);
			this.setType(type);
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
			if (getType() =='c') g.setColor(Color.black);
			g.fillRect(0, 0, WIDTH, HEIGHT);
			Graphics2D g2d = (Graphics2D)g;
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (getType()=='t' || getType()=='z')
				drawPlayPauseButton(g2d);

			drawLetter(g);
		}

		private void drawLetter(Graphics g) {
			g.setFont(new Font("SansSerif", Font.PLAIN, 14));
			if (getType() =='c') g.setFont(new Font("SansSerif", Font.PLAIN, 14));
			String string = "";
			if (getType() =='t'|| getType() =='z'|| getType() =='r') 
				string = String.valueOf(getType());
			g.setColor(Color.black);
			if (getType() =='c') {
				string = String.valueOf(getType());
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
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 5 ){
					string = "1:2";
					g.setColor(Color.cyan);
				}
				else if ( stackWindow.getImagePlus().isComposite() && ((CompositeImage) stackWindow.getImagePlus()).getMode() == 6 ){
					string = "2:1";
					g.setColor(Color.orange);
				}
				else {
					g.setColor(Color.magenta);
				}

			}
			g.drawString(string, 2, 12); 
		}

		private void drawPlayPauseButton(Graphics2D g) {
			if ( stackWindow != null) { 
				if ((	getType() =='t' && stackWindow.getAnimate()) 
								|| (getType() =='z' && stackWindow.getZAnimate())  
								|| (getType() =='z' && stackWindow.getAnimationSelector() != null && stackWindow.getAnimationSelector().getType() == 'z' && stackWindow.getAnimate()) 
								|| (getType() =='z' && stackWindow.getAnimationZSelector() != null && stackWindow.getAnimationZSelector().getType() == 'z' && stackWindow.getZAnimate()) ) {
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
		}


		public char getType() {
			return type;
		}

		public void setType(char type) {
			this.type = type;
		}

	} // StartStopIcon class



	public void setIconEnabled(boolean b) {
		iconEnabled = b;
	}

	public Icon getIcon() {
		return icon;
	}

	public void setIcon(Icon icon) {
		this.icon = icon;
	}





}
