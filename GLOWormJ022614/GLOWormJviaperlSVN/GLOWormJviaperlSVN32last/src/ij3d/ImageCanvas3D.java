package ij3d;

import com.sun.j3d.utils.universe.SimpleUniverse;
import javax.media.j3d.J3DGraphics2D;
import javax.swing.SwingUtilities;
import javax.media.j3d.Canvas3D;
import javax.media.j3d.Background;
import javax.vecmath.Color3f;
import javax.vecmath.Point3d;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;

import ij.process.ByteProcessor;
import ij.gui.ImageCanvas2;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.plugin.Colors;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ImageCanvas3D extends Canvas3D implements KeyListener {

	private RoiImagePlus roiImagePlus;
	private ImageCanvas2 roiImageCanvas;
	private Map<Integer, Long> pressed, released;
	private Background background;
	private UIAdapter ui;
	final private ExecutorService exec = Executors.newSingleThreadExecutor();
	public int recentX, recentY;
	public Image crsrImg, noPickCrsrImg;
	public int cursorX;
	public int cursorY;
	public Hashtable<String,Roi> messageRois;



	public void flush() {
		exec.shutdown();
	}

	private class RoiImagePlus extends ImagePlus {
		public RoiImagePlus(String title, ByteProcessor ip) {
			super();
			setProcessor(title, ip);
			pressed = new HashMap<Integer, Long>();
			released = new HashMap<Integer, Long>();
		}

		public ImageCanvas2 getCanvas() {
			return roiImageCanvas;
		}
	}

	public ImageCanvas3D(int width, int height, UIAdapter uia) {
		super(SimpleUniverse.getPreferredConfiguration());
		this.ui = uia;
		setPreferredSize(new Dimension(width, height));
		ByteProcessor ip = new ByteProcessor(width, height);
		messageRois = new Hashtable<String,Roi>();
		roiImagePlus = new RoiImagePlus("RoiImage", ip);
		roiImageCanvas = new ImageCanvas2(roiImagePlus) {
			/* prevent ROI to enlarge/move on mouse click */
			public void mousePressed(MouseEvent e) {
				if(!ImageCanvas3D.this.ui.isMagnifierTool() && !ImageCanvas3D.this.ui.isPointTool())
					super.mousePressed(e);
			}
		};
		roiImageCanvas.removeKeyListener(ij.IJ.getInstance());
		roiImageCanvas.removeMouseListener(roiImageCanvas);
		roiImageCanvas.removeMouseMotionListener(roiImageCanvas);
		roiImageCanvas.setEnabled(false);
		roiImageCanvas.disablePopupMenu(false);

		background = new Background(
			new Color3f(UniverseSettings.defaultBackground));
		background.setCapability(Background.ALLOW_COLOR_WRITE);

		addListeners();
	}

	public Background getBG() { //can't use getBackground()
		return background;
	}

	public void killRoi() {
		roiImagePlus.killRoi();
		render();
	}

	void addListeners() {
		addMouseMotionListener(new MouseMotionAdapter() {
			Content recentContent ;
			String prevsortedPrefixedCursorString;

			public void mouseDragged(MouseEvent e) {
				if(ui.isRoiTool())
					exec.submit(new Runnable() { public void run() {
						postRender();
					}});
			}
			public void mouseMoved(MouseEvent e) {

				if ((( Math.abs(recentX-e.getX()))<3) && (
						Math.abs((recentY-e.getY()))<3))
					return;

				Content c = getUniverse().picker.getPickedContent(
						e.getX(), e.getY());
				
				if (c==null){  // c == null
					IJ.showStatus("");

					crsrImg = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB_PRE);

					Graphics2D g2d = (Graphics2D) crsrImg.getGraphics();

					ImageCanvas3D.this.paint(g2d);
					//								render();
					//							}
				
				
				} else if (c == recentContent ) {
					cursorX = e.getX();
					cursorY = e.getY();
					Graphics2D g2d = (Graphics2D) crsrImg.getGraphics();

					ImageCanvas3D.this.paint(g2d);
					//					render();

				} else if (c != null){
					recentX=e.getX();
					recentY=e.getY();

					recentContent = c;
					String cursorString = " ";

					IJ.showStatus(c.getName().split("( |_)=")[0]);
					cursorString = c.getName().replace("electrical", "_electrical_")
							.replace("chemical", "_chemical_")
							.replace("undefined", "_undefined_")
							.replace("certain", "")
							.replace("uncertain", "")
							.split("( |_)=")[0];


					if (IJ.isWindows())
						cursorString = c.getName().replace("electrical", "_electrical_")
						.replace("chemical", "_chemical_")
						.replace("undefined", "_undefined_")
						.replace("certain", "")
						.replace("uncertain", "")
						.split("( |_)=")[0];

					String[] cursorWords = cursorString.replaceAll(("((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))"), "").replace("_", " ").split(" ");
					if (cursorString.matches("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))")) {
						Arrays.sort(cursorWords);
						if (cursorWords.length >0) {
							cursorWords[cursorWords.length-1] = cursorWords[cursorWords.length-1] 
									+ cursorString.replaceAll(("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))"), "$2");
						}
					}
					String sortedPrefixedCursorString = cursorString.replaceAll(("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))"), "$2")+ " count=" + cursorWords.length;
					for (String word:cursorWords) {
						sortedPrefixedCursorString = sortedPrefixedCursorString + (",") + word.replaceAll(("((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))"), "");
					}
					if (!sortedPrefixedCursorString.equals(prevsortedPrefixedCursorString) &&  e.isShiftDown()) {
						IJ.log(sortedPrefixedCursorString);
						prevsortedPrefixedCursorString = sortedPrefixedCursorString;
					}
					int wordsPerRow = (cursorString.contains("chemical")||cursorString.contains("electrical")||cursorString.contains("undefined"))?1:5;
					int cursorLineCount = cursorWords.length / wordsPerRow
							+ (cursorWords.length % 3 == 0 ? 0 : 1) /* +wordsPerRow */;
					int fontSize = 18-(4*cursorLineCount/18);
					Font font = Font.decode("Arial-"+(fontSize));
					String[] cursorStringCRs = new String[cursorLineCount+2] ;
					cursorStringCRs[0] = "    ";
					int l =0;
					for (int word=0; word<cursorWords.length; word=word+1) {
						if (word >0 && (word)%wordsPerRow==0) {
							l++;
						} 
						// matching below fits both old and new formats for cphate obj names
						if (word == cursorWords.length-1 && cursorWords[word].matches("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))")) {
							if (cursorStringCRs[l] == null) {
								cursorStringCRs[l] = cursorWords[word].replaceAll("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))", "$1");
								l++;
								cursorStringCRs[l] = cursorWords[word].replaceAll("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))", "$2").replace("-", " ")
										+ " count=" + cursorWords.length;
								continue;
							} else {
								cursorStringCRs[l] = cursorStringCRs[l] + " "+ cursorWords[word].replaceAll("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))", "$1");
								l++;
								cursorStringCRs[l] = cursorWords[word].replaceAll("(.*)((-i\\d+-(c|g)\\d+-s\\d+)|(-i\\d+\\/\\d+-(c|g)\\d+\\/\\d+-s\\d+))", "$2").replace("-", " ")
										+ " count=" + cursorWords.length;
								continue;
							}
						}
						if (cursorStringCRs[l] == null) {
							cursorStringCRs[l] = cursorWords[word];
						} else
							cursorStringCRs[l] = cursorStringCRs[l] + " "+ cursorWords[word];
					}
					String longestString = "";
					for (String cscrString:cursorStringCRs) {
						if (cscrString!=null && longestString.length() < cscrString.length()) {
							longestString = cscrString;
						}
					}
					Point3d pickCenter = new Point3d();
					c.getBounds().getCenter(pickCenter);

					//create the FontRenderContext object which helps us to measure the text
					FontRenderContext frc = new FontRenderContext(null, true, true);

					//get the height and width of the text
					Font rectFont = font.deriveFont((long)(fontSize+2));
					Rectangle2D bounds = rectFont.getStringBounds(longestString, frc);
					int w = (int) bounds.getWidth();
					int ht = (int) bounds.getHeight();

					//					IJ.showStatus(c.getName().split("( |_)=")[0] + " wrapping figured");
					crsrImg = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB_PRE);
					noPickCrsrImg = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB_PRE);


					//		img.getGraphics().setColor(Colors.decode("00000000", Color.white));
					Graphics2D g2d = (Graphics2D) crsrImg.getGraphics();

					g2d.setFont(font);

					g2d.setColor(Colors.decode("#88111111",Color.gray));
					g2d.fillRect(0, 0, w, 10+ht*cursorLineCount);
					g2d.setColor(Color.YELLOW);
					g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
					g2d.drawLine(0, 0, 2, 7);
					g2d.drawLine(0, 0, 7, 2);
					g2d.drawLine(0, 0, 8, 8);
					//					g2d.drawString(cursorString, 1, img.getHeight(null)-1);
					for (int line=0; line<cursorStringCRs.length; line++) {
						if (cursorStringCRs[line]!=null)
							g2d.drawString(cursorStringCRs[line], 1, (line+1) * fontSize + 8);
					}
					if(recentX==e.getX() && recentY==e.getY() && true /*IJ.isWindows()*/){

						cursorX = e.getX();
						cursorY = e.getY();
						ImageCanvas3D.this.paint(g2d);
						//						render();
					}

				}
				
				if(ui.isRoiTool())
					exec.submit(new Runnable() { public void run() {
						postRender();
					}});
			}
		});
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if(ui.isRoiTool())
					exec.submit(new Runnable() { public void run() {
						render();
					}});
			}
			public void mouseReleased(MouseEvent e) {
				if(ui.isRoiTool())
					exec.submit(new Runnable() { public void run() {
						render();
					}});
			}
			public void mousePressed(MouseEvent e) {
				if(!ui.isRoiTool())
					roiImagePlus.killRoi();
			}
		});
		addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				exec.submit(new Runnable() { public void run() {
					ByteProcessor ip = new ByteProcessor(
									getWidth(),
									getHeight());
					roiImagePlus.setProcessor("RoiImagePlus", ip);
					render();
				}});
			}
		});
	}

	public ImageCanvas2 getRoiCanvas() {
		return roiImageCanvas;
	}

	public Roi getRoi() {
		return roiImagePlus.getRoi();
	}

	public void render() {
		stopRenderer();
		swap();
		startRenderer();
	}

	/*
	 * Needed for the isKeyDown() method. Problem:
	 * keyPressed() and keyReleased is fired periodically,
	 * dependent on the operating system preferences,
	 * even if the key is hold down.
	 */
	public void keyTyped(KeyEvent e) {}

	public synchronized void keyPressed(KeyEvent e) {
		long when = e.getWhen();
		pressed.put(e.getKeyCode(), when);
	}

	public synchronized void keyReleased(KeyEvent e) {
		long when = e.getWhen();
		released.put(e.getKeyCode(), when);
	}

	public synchronized void releaseKey(int keycode) {
		pressed.remove(keycode);
		released.remove(keycode);
	}

	public synchronized boolean isKeyDown(int keycode) {
		if(!pressed.containsKey(keycode))
			return false;
		if(!released.containsKey(keycode))
			return true;
		long p = pressed.get(keycode);
		long r = released.get(keycode);
		return p >= r || System.currentTimeMillis() - r < 100;
	}

	public void postRender() {
		J3DGraphics2D g3d = getGraphics2D();
		Roi roi = roiImagePlus.getRoi();
		if(roi != null) {
			roi.draw(g3d);
		}
		g3d.flush(true);
	}

	public Image3DUniverse getUniverse() {
		return (Image3DUniverse) ((ImageWindow3D)SwingUtilities.windowForComponent(this)).getUniverse();
	}
	
	@Override
	public void paint(Graphics g) {
	    // 1. Call the superclass's paint method FIRST. This handles the 3D rendering.
	    super.paint(g); 
	    
	    // 2. Overlay the 2D messages.
	    // Ensure the Graphics context is not null and we have messages to draw.
	    if (g == null || messageRois.isEmpty()) {
	        return;
	    }

	    // 3. Iterate through all messageRois and draw them.
	    for (Roi roi : messageRois.values()) {
	        if (roi != null) {
	            roi.drawOverlay(g); // Use drawOverlay if available, otherwise just roi.draw(g);
	        }
	    }
	}
}

