package ij.io;
import ij.gui.*;
import ij.IJ;
import ij.ImagePlus;
import ij.process.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.awt.*;

/*	ImageJ/NIH Image 64 byte ROI outline header
	2 byte numbers are big-endian signed shorts
	
	0-3		"Iout"
	4-5		version (>=217)
	6-7		roi type
	8-9		top
	10-11	left
	12-13	bottom
	14-15	right
	16-17	NCoordinates
	18-33	x1,y1,x2,y2 (straight line)
	34-35	stroke width (v1.43i or later)
	36-39   ShapeRoi size (type must be 1 if this value>0)
	40-43   stroke color (v1.43i or later)
	44-47   fill color (v1.43i or later)
	48-49   subtype (v1.43k or later)
	50-51   options (v1.43k or later)
	52-52   arrow style or aspect ratio (v1.43p or later)
	53-53   arrow head size (v1.43p or later)
	54-55   rounded rect arc size (v1.43p or later)
	56-59   position
	60-63   header2 offset
	64-       x-coordinates (short), followed by y-coordinates
*/

/** Decodes an ImageJ, NIH Image or Scion Image ROI file. */
public class RoiDecoder {
	// offsets
	public static final int VERSION_OFFSET = 4;
	public static final int TYPE = 6;
	public static final int TOP = 8;
	public static final int LEFT = 10;
	public static final int BOTTOM = 12;
	public static final int RIGHT = 14;
	public static final int N_COORDINATES = 16;
	public static final int X1 = 18;
	public static final int Y1 = 22;
	public static final int X2 = 26;
	public static final int Y2 = 30;
	public static final int XD = 18;
	public static final int YD = 22;
	public static final int WIDTHD = 26;
	public static final int HEIGHTD = 30;
	public static final int STROKE_WIDTH = 34;
	public static final int SHAPE_ROI_SIZE = 36;
	public static final int STROKE_COLOR = 40;
	public static final int FILL_COLOR = 44;
	public static final int SUBTYPE = 48;
	public static final int OPTIONS = 50;
	public static final int ARROW_STYLE = 52;
	public static final int ELLIPSE_ASPECT_RATIO = 52;
	public static final int ARROW_HEAD_SIZE = 53;
	public static final int ROUNDED_RECT_ARC_SIZE = 54;
	public static final int POSITION = 56;
	public static final int HEADER2_OFFSET = 60;
	public static final int COORDINATES = 64;
	// header2 offsets
	public static final int C_POSITION = 4;
	public static final int Z_POSITION = 8;
	public static final int T_POSITION = 12;
	public static final int NAME_OFFSET = 16;
	public static final int NAME_LENGTH = 20;
	public static final int OVERLAY_LABEL_COLOR = 24;
	public static final int OVERLAY_FONT_SIZE = 28; //short
	public static final int AVAILABLE_BYTE1 = 30;  //byte
	public static final int IMAGE_OPACITY = 31;  //byte
	public static final int IMAGE_SIZE = 32;  //int
	public static final int FLOAT_STROKE_WIDTH = 36;  //float
		
	// subtypes
	public static final int TEXT = 1;
	public static final int ARROW = 2;
	public static final int ELLIPSE = 3;
	public static final int IMAGE = 4;
	
	// options
	public static final int SPLINE_FIT = 1;
	public static final int DOUBLE_HEADED = 2;
	public static final int OUTLINE = 4;
	public static final int OVERLAY_LABELS = 8;
	public static final int OVERLAY_NAMES = 16;
	public static final int OVERLAY_BACKGROUNDS = 32;
	public static final int OVERLAY_BOLD = 64;
	public static final int SUB_PIXEL_RESOLUTION = 128;
	public static final int DRAW_OFFSET = 256;
	
	// types
	private final int polygon=0, rect=1, oval=2, line=3, freeline=4, polyline=5, noRoi=6,
		freehand=7, traced=8, angle=9, point=10;
	
	private byte[] data;
	private String path;
	private InputStream is;
	private String name;
	private int size;
	private double scaleFactor = 1d;

	/** Constructs an RoiDecoder using a file path. */
	public RoiDecoder(String path) {
		this.path = path;
	}

	/** Constructs an RoiDecoder using a file path, while specifying a scaleFactor. */
	public RoiDecoder(double scaleFactor, String path) {
		this.scaleFactor = scaleFactor;
		this.path = path;
	}

	/** Constructs an RoiDecoder using a byte array. */
	public RoiDecoder(byte[] bytes, String name) {
		this(1d, bytes, name);
	}

	
	/** Constructs an RoiDecoder using a byte array, while specifying a scaleFactor. */
	public RoiDecoder(double scaleFactor, byte[] bytes, String name) {
		this.scaleFactor = scaleFactor;
		is = new ByteArrayInputStream(bytes);	
		this.name = name;
		this.size = bytes.length;
	}

	/** Returns the ROI. */
	public Roi getRoi() throws IOException {
		if (path!=null) {
			File f = new File(path);
			size = (int)f.length();
			if (!path.endsWith(".roi") && size>5242880)
				throw new IOException("This is not an ROI or file size>5MB)");
			name = f.getName();
			is = new FileInputStream(path);
		}
		data = new byte[size];

		int total = 0;
		while (total<size)
			total += is.read(data, total, size-total);
		is.close();
		if (getByte(0)==0)
			return null;
		if (getByte(0)!=73 || getByte(1)!=111)  //"Iout"
			throw new IOException("This is not an ImageJ ROI");
		int version = getShort(VERSION_OFFSET);
		int type = getByte(TYPE);
		int subtype = getShort(SUBTYPE);
		int top= getShort(TOP);
		int left = getShort(LEFT);
		int bottom = getShort(BOTTOM);
		int right = getShort(RIGHT);
		int width = right-left;
		int height = bottom-top;
		int n = getShort(N_COORDINATES);
		int options = getShort(OPTIONS);
		int position = getInt(POSITION);
		int hdr2Offset = getInt(HEADER2_OFFSET);
		int channel=0, slice=0, frame=0;
		int overlayLabelColor=0;
		int overlayFontSize=0;
		int imageOpacity=0;
		int imageSize=0;
		boolean subPixelResolution = (options&SUB_PIXEL_RESOLUTION)!=0 &&  version>=222;
		boolean drawOffset = subPixelResolution && (options&DRAW_OFFSET)!=0;
		
		boolean subPixelRect = version>=223 && subPixelResolution && (type==rect||type==oval);
		double xd=0.0, yd=0.0, widthd=0.0, heightd=0.0;
		if (subPixelRect) {
			xd = getFloat(XD);
			yd = getFloat(YD);
			widthd = getFloat(WIDTHD);
			heightd = getFloat(HEIGHTD);
		}
		
		if (hdr2Offset>0 && hdr2Offset+IMAGE_SIZE+4<=size) {
			channel = getInt(hdr2Offset+C_POSITION);
			slice = getInt(hdr2Offset+Z_POSITION);
			frame = getInt(hdr2Offset+T_POSITION);
			overlayLabelColor = getInt(hdr2Offset+OVERLAY_LABEL_COLOR);
			overlayFontSize = getShort(hdr2Offset+OVERLAY_FONT_SIZE);
			imageOpacity = getByte(hdr2Offset+IMAGE_OPACITY);
			imageSize = getInt(hdr2Offset+IMAGE_SIZE);
		}
		
		if (name!=null && name.endsWith(".roi"))
			name = name.substring(0, name.length()-4);
//		if (!name.contains("\""))
//			name = "\""+name.replace("_#", " \"_#");
		int shapeRoiSize = getInt(SHAPE_ROI_SIZE);
		boolean isComposite = shapeRoiSize>0;
		
		Roi roi = null;
		if (isComposite) {
			roi = getShapeRoi();
			Roi[] rois = ((ShapeRoi) roi).getRois();
			Roi[] scaledRois = new Roi[rois.length];
			for (int r=0;r<rois.length;r++) {
				Polygon poly = rois[r].getPolygon();

				for (int p=0;p<poly.npoints;p++) {
					poly.xpoints[p] = (int)(poly.xpoints[p]*scaleFactor);
					poly.ypoints[p] = (int)(poly.ypoints[p]*scaleFactor);
				}
				scaledRois[r] = new ShapeRoi(poly);
				scaledRois[r].copyAttributes(rois[r]);
			}
			ShapeRoi scaledRoi = null;
			for (Roi scaledPart:scaledRois) {
				if (scaledRoi == null) {
					scaledRoi = new ShapeRoi(scaledPart);
				} else {
					//after long time floundering with dead code here, realized all I need is to OR the several component rois together.
					scaledRoi.or(new ShapeRoi(scaledPart));
				}
			}
			if (version>=218)
				getStrokeWidthAndColor(roi, hdr2Offset);
			
			scaledRoi.copyAttributes(roi);
			scaledRoi.setPosition(roi.getImage(), position);
			if (channel>0 || slice>0 || frame>0)
				scaledRoi.setPosition(channel, slice, frame);
			decodeOverlayOptions(scaledRoi, version, options, overlayLabelColor, overlayFontSize);
			return scaledRoi;
		}

		switch (type) {
			case rect:
				if (version>=218 && subtype==TEXT) {
					if (subPixelRect)
						roi = new Roi(xd, yd, widthd, heightd);
					else
						roi = new Roi(left, top, width, height);
					roi = getTextRoi(roi);
					break;
				}
				if (subPixelRect)
					roi = new Roi(xd*scaleFactor, yd*scaleFactor, widthd*scaleFactor, heightd*scaleFactor);
				else
					roi = new Roi(left*scaleFactor, top*scaleFactor, width*scaleFactor, height*scaleFactor);
				int arcSize = getShort(ROUNDED_RECT_ARC_SIZE);
				if (arcSize>0)
					roi.setCornerDiameter((int) (arcSize*scaleFactor));
				break;
			case oval:
				if (subPixelRect)
					roi = new OvalRoi(xd*scaleFactor, yd*scaleFactor, widthd*scaleFactor, heightd*scaleFactor);
				else
					roi = new OvalRoi(left*scaleFactor, top*scaleFactor, width*scaleFactor, height*scaleFactor);
				break;
			case line:
				double x1 = getFloat(X1);		
				double y1 = getFloat(Y1);		
				double x2 = getFloat(X2);		
				double y2 = getFloat(Y2);
				if (subtype==ARROW) {
					roi = new Arrow(x1*scaleFactor, y1*scaleFactor, x2*scaleFactor, y2*scaleFactor);		
					((Arrow)roi).setDoubleHeaded((options&DOUBLE_HEADED)!=0);
					((Arrow)roi).setOutline((options&OUTLINE)!=0);
					int style = getByte(ARROW_STYLE);
					if (style>=Arrow.FILLED && style<=Arrow.HEADLESS)
						((Arrow)roi).setStyle(style);
					int headSize = getByte(ARROW_HEAD_SIZE);
					if (headSize>=0 && style<=30)
						((Arrow)roi).setHeadSize(headSize*scaleFactor);
				} else {
					roi = new Line(x1*scaleFactor, y1*scaleFactor, x2*scaleFactor, y2*scaleFactor);
					roi.setDrawOffset(drawOffset);
				}
				//IJ.write("line roi: "+x1+" "+y1+" "+x2+" "+y2);
				break;
			case polygon: case freehand: case traced: case polyline: case freeline: case angle: case point:
					//IJ.log("type: "+type);
					//IJ.log("n: "+n);
					//IJ.log("rect: "+left+","+top+" "+width+" "+height);
					if (n==0) break;
					int[] x = new int[n];
					int[] y = new int[n];
					float[] xf = null;
					float[] yf = null;
					int base1 = COORDINATES;
					int base2 = base1+2*n;
					int xtmp, ytmp;
					for (int i=0; i<n; i++) {
						xtmp = getShort(base1+i*2);
						if (xtmp<0) xtmp = 0;
						ytmp = getShort(base2+i*2);
						if (ytmp<0) ytmp = 0;
						x[i] = (int) ((left+xtmp)*scaleFactor);
						y[i] = (int) ((top+ytmp)*scaleFactor);
						//IJ.write(i+" "+getShort(base1+i*2)+" "+getShort(base2+i*2));
					}
					if (subPixelResolution) {
						xf = new float[n];
						yf = new float[n];
						base1 = COORDINATES+4*n;
						base2 = base1+4*n;
						for (int i=0; i<n; i++) {
							xf[i] = (float) (getFloat(base1+i*4)*scaleFactor);
							yf[i] = (float) (getFloat(base2+i*4)*scaleFactor);
						}
					}
					if (type==point) {
						if (subPixelResolution){
							roi = new PointRoi(xf, yf, n);
						}else{
							roi = new PointRoi(x, y, n);
						}
						break;
					}
					int roiType;
					if (type==polygon)
						roiType = Roi.POLYGON;
					else if (type==freehand) {
						roiType = Roi.FREEROI;
						if (subtype==ELLIPSE) {
							double ex1 = getFloat(X1);		
							double ey1 = getFloat(Y1);		
							double ex2 = getFloat(X2);		
							double ey2 = getFloat(Y2);
							double aspectRatio = getFloat(ELLIPSE_ASPECT_RATIO);
							roi = new EllipseRoi(ex1*scaleFactor,ey1*scaleFactor,ex2*scaleFactor,ey2*scaleFactor,aspectRatio);
							break;
						}
					} else if (type==traced)
						roiType = Roi.TRACED_ROI;
					else if (type==polyline)
						roiType = Roi.POLYLINE;
					else if (type==freeline)
						roiType = Roi.FREELINE;
					else if (type==angle)
						roiType = Roi.ANGLE;
					else
						roiType = Roi.FREEROI;
					if (subPixelResolution) {
						roi = new PolygonRoi(xf, yf, n, roiType);
						roi.setDrawOffset(drawOffset);
					} else
						roi = new PolygonRoi(x, y, n, roiType);
					break;
			default:
				throw new IOException("Unrecognized ROI type: "+type);
		}
		if (roi == null)
			return null;
		
		roi.setName(getRoiName());
		
		// read stroke width, stroke color and fill color (1.43i or later)
		if (version>=218) {
			getStrokeWidthAndColor(roi, hdr2Offset);
			boolean splineFit = (options&SPLINE_FIT)!=0;
			if (splineFit && roi instanceof PolygonRoi)
				((PolygonRoi)roi).fitSpline();
		}
		
		if (version>=221 && subtype==IMAGE)
			roi = getImageRoi(roi, imageOpacity, imageSize);

		if (roi.getImage() != null)
			roi.setPosition(roi.getImage(), position);
		if (channel>0 || slice>0 || frame>0)
			roi.setPosition(channel, slice, frame);
		decodeOverlayOptions(roi, version, options, overlayLabelColor, overlayFontSize);
		return roi;
	}
	
	void decodeOverlayOptions(Roi roi, int version, int options, int color, int fontSize) {
		Overlay proto = new Overlay();
		proto.drawLabels((options&OVERLAY_LABELS)!=0);
		proto.drawNames((options&OVERLAY_NAMES)!=0);
		proto.drawBackgrounds((options&OVERLAY_BACKGROUNDS)!=0);
		if (version>=220)
			proto.setLabelColor(new Color(color));
		boolean bold = (options&OVERLAY_BOLD)!=0;
		if (fontSize>0 || bold) {
			proto.setLabelFont(new Font("SansSerif", bold?Font.BOLD:Font.PLAIN, fontSize));
		}
		roi.setPrototypeOverlay(proto);
	}

	void getStrokeWidthAndColor(Roi roi, int hdr2Offset) {
		double strokeWidth = getShort(STROKE_WIDTH);
		if (hdr2Offset>0) {
			double strokeWidthD = getFloat(hdr2Offset+FLOAT_STROKE_WIDTH);
			if (strokeWidthD>0.0)
				strokeWidth = strokeWidthD;
		}
		if (strokeWidth>0.0)
			roi.setStrokeWidth(strokeWidth);
		int strokeColor = getInt(STROKE_COLOR);
		if (strokeColor!=0) {
			int alpha = (strokeColor>>24)&0xff;
			roi.setStrokeColor(new Color(strokeColor, alpha!=255));
		}
		int fillColor = getInt(FILL_COLOR);
		if (fillColor!=0) {
			int alpha = (fillColor>>24)&0xff;
			roi.setFillColor(new Color(fillColor, alpha!=255));
		}
	}

	public Roi getShapeRoi() throws IOException {
		int type = getByte(TYPE);
		if (type!=rect)
			throw new IllegalArgumentException("Invalid composite ROI type");
		int top= (int) (getShort(TOP));
		int left = (int) (getShort(LEFT));
		int bottom = (int) (getShort(BOTTOM));
		int right = (int) (getShort(RIGHT));
		int width = right-left;
		int height = bottom-top;
		int n = getInt(SHAPE_ROI_SIZE);

		ShapeRoi roi = null;
		float[] shapeArray = new float[n];
		int base = COORDINATES;
		for(int i=0; i<n; i++) {
			shapeArray[i] = (float) (getFloat(base));
			base += 4;
		}
		roi = new ShapeRoi(shapeArray);
		roi.setName(getRoiName());
		return roi;
	}
	
	Roi getTextRoi(Roi roi) {
		Rectangle r = roi.getBounds();
		int hdrSize = RoiEncoder.HEADER_SIZE;
		int size = getInt(hdrSize);
		int style = getInt(hdrSize+4);
		int nameLength = getInt(hdrSize+8);
		int textLength = getInt(hdrSize+12);
		char[] name = new char[nameLength];
		char[] text = new char[textLength];
		for (int i=0; i<nameLength; i++)
			name[i] = (char)getShort(hdrSize+16+i*2);
		for (int i=0; i<textLength; i++)
			text[i] = (char)getShort(hdrSize+16+nameLength*2+i*2);
		Font font = new Font(new String(name), style, (int) (size*scaleFactor));
		Roi roi2 = null;
		if (roi.subPixelResolution()) {
			FloatPolygon fp = roi.getFloatPolygon();
			roi2 = new TextRoi(fp.xpoints[0]*scaleFactor, fp.ypoints[0]*scaleFactor, new String(text), font);
		} else
			roi2 = new TextRoi(r.x*scaleFactor, r.y*scaleFactor, new String(text), font);
		roi2.setStrokeColor(roi.getStrokeColor());
		roi2.setFillColor(roi.getFillColor());
		roi2.setName(getRoiName());
		return roi2;
	}
	
	Roi getImageRoi(Roi roi, int opacity, int size) {
		if (size<=0)
			return roi;
		Rectangle r = roi.getBounds();
		byte[] bytes = new byte[size];
		for (int i=0; i<size; i++)
			bytes[i] = (byte)getByte(COORDINATES+i);
		ImagePlus imp = new Opener().deserialize(bytes);
		ImageProcessor ip = imp.getProcessor();
		ip.scale(scaleFactor, scaleFactor);
		ImageRoi roi2 = new ImageRoi((int)(r.x*scaleFactor), (int)(r.y*scaleFactor), ip);
		roi2.setOpacity(opacity/255.0);
		return roi2;
	}

	String getRoiName() {
		String fileName = name;
		if (name == null)
			return "UnnamedRoi";
		if (name.matches("\".* \"(_.*)*_\\d+_\\d+_\\d+.*")) {
			return fileName;
		}
		int hdr2Offset = getInt(HEADER2_OFFSET);
		if (hdr2Offset==0)
			return fileName;
		int offset = getInt(hdr2Offset+NAME_OFFSET);
		int length = getInt(hdr2Offset+NAME_LENGTH);
		if (offset==0 || length==0)
			return fileName;
		if (offset+length*2>size)
			return fileName;
		char[] charName = new char[length];
		for (int i=0; i<length; i++)
			charName[i] = (char)getShort(offset+i*2);
		return new String(charName);
	}

	int getByte(int base) {
		if (data.length > base)
			return data[base]&255;
		else
			return 0;
	}

	int getShort(int base) {
		int b0 = data[base]&255;
		int b1 = data[base+1]&255;
		int n = (short)((b0<<8) + b1);
		if (n<-5000)
			n = (b0<<8) + b1; // assume n>32767 and unsigned
		return n;		
	}
	
	int getInt(int base) {
		int b0 = data[base]&255;
		int b1 = data[base+1]&255;
		int b2 = data[base+2]&255;
		int b3 = data[base+3]&255;
		return ((b0<<24) + (b1<<16) + (b2<<8) + b3);
	}

	float getFloat(int base) {
		return Float.intBitsToFloat(getInt(base));
	}
	
	/** Opens an ROI from a byte array. */
	public static Roi openFromByteArray(byte[] bytes) {
		Roi roi = null;
		try {
			RoiDecoder decoder = new RoiDecoder(bytes, null);
			roi = decoder.getRoi();
		} catch (IOException e) {
			return null;
		}
		return roi;
	}

}
