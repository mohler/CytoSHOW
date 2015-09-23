package gloworm;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.awt.Color.*;
import java.awt.*;
import java.util.*;
import ij.*;
import ij.io.*;
import ij.process.*;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.gui.*;
import ij.text.*;
import ij.io.RandomAccessStream;
import ij.measure.*;
import ij.ImagePlus.*;
import javax.swing.*;
import java.awt.datatransfer.*;

/** new in version 2.2 */
/** added more practical control panel */
/** ability to open time-series and 12-bits lsm files */
/** new unique infopanel that displays the info of the current image windows */
/** ability to close all windows without leaving the plug-in */
/** */
/** thanks for all your comments ! */
/** */
/** Comming soon : more detailed info panel */

/** The LSM Reader 2.2 plug-in for Image J */
public class LSM_Reader implements PlugIn {
    
     public GUI theGUI;
     public  static JFrame infoFrame = new JFrame("LSM Infos");

//}

/** This class implements the LSM Reader GUI */
class GUI {
    
    /** Private fields */
     public JFrame baseFrame;
     private JPanel pan;
     private GridLayout baseGridLayout = new GridLayout(5,1,10,5);
     private GUIButton butt1 = new GUIButton (" Open LSM ","Opens a 8-bit LSM image or image stack");
     private GUIButton butt2 = new GUIButton(" Close all Windows ","Closes all opened Image Windows");
     private GUIButton butt3 = new GUIButton(" Exit ","Exits the LSM Reader Plug-In");
     private GUIButton butt4 = new GUIButton(" Show Info ","Brings the info panel to front");
     //private GUIButton butt4 = new GUIButton(" Hide Info ","Hide the info panel");
     private JLabel label1 = new JLabel(" LSM Tools ", JLabel.CENTER);
     //public  JFrame infoFrame = new JFrame("LSM Infos");
    
     public String[] LSMinfoText = new String[22];
    
     private Dimension ScreenDimension = Toolkit.getDefaultToolkit().getScreenSize();
     private int ScreenX = (int) ScreenDimension.getWidth();
     private int ScreenY = (int) ScreenDimension.getHeight();
     private int baseFrameXsize = (int)(ScreenX/8);
     private int baseFrameYsize = (int)(ScreenY/5);
     private int infoFrameXsize = (int)(ScreenX/5);
     private int infoFrameYsize = (int)(1.5*ScreenY/4);
     private int baseFrameXlocation = (int)((11*ScreenX/12) - baseFrameXsize);
     private int baseFrameYlocation = (int)((11*ScreenY/12) - baseFrameYsize);
     private int infoFrameXlocation = (int)((ScreenX/12));
     private int infoFrameYlocation = (int)((9*ScreenY/10) - infoFrameYsize);
    
    
    
    
    /** Creates a new instance of GUI */
    public void GUI() {
    }
    
    /** Initiates the GUI Object */
    public void init() {
        baseFrame = new JFrame();
        baseFrame.setTitle("LSM Reader 2.0");
        baseFrame.setSize(baseFrameXsize,baseFrameYsize);
        baseFrame.setResizable(false);
        baseFrame.setLocation(baseFrameXlocation,baseFrameYlocation);
        
        infoFrame.setSize(infoFrameXsize, infoFrameYsize);
        infoFrame.setLocation(infoFrameXlocation,infoFrameYlocation);
        infoFrame.setResizable(false);
        
        addExitListener(butt3, baseFrame);
        addShowHideInfolistener(butt4, baseFrame);
        addOpenListener(butt1, baseFrame);
        addCloseWinListener(butt2, baseFrame);
        
                
        pan = new JPanel();
        pan.setSize(130, 280);
        pan.setForeground(SystemColor.window);
        pan.setLayout(baseGridLayout);
        
        pan.add(label1);
        pan.add(butt1);
        pan.add(butt2);
        pan.add(butt4);
        pan.add(butt3);
        
        
        baseFrame.getContentPane().add(pan);
        
        baseFrame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    baseFrame.dispose();
                }
            }
        );
        baseFrame.setVisible(true);
        baseFrame.show();  
    }
    /** closes all frames */
    private void closeFrames()
    {
        baseFrame.dispose();
        infoFrame.dispose();
    }
    
    /** inits the info panel */
    public void initInfoFrame()
    {
        infoFrame.getContentPane().removeAll();
        String[] infolabels = new String[22];
        
        infolabels[0] = "File Name";
        infolabels[1] = "User";
        infolabels[2] = "Image Width";
        infolabels[3] = "Image Height";
        infolabels[4] = "Number of channels";
        infolabels[5] = "Z Stack size";
        infolabels[6] = "Time Stack size";
        infolabels[7] = "Scan Type";
        infolabels[8] = "Sampling mode";
        infolabels[9] = "Short notes";
        infolabels[10] = "Detailed notes";
        infolabels[11] = "Voxel X size";
        infolabels[12] = "Voxel Y size";
        infolabels[13] = "Voxel Z size";
        infolabels[14] = "Objective";
        infolabels[15] = "X zoom factor";
        infolabels[16] = "Y zoom factor";
        infolabels[17] = "Z zoom factor";
        infolabels[18] = "Plane width";
        infolabels[19] = "Plane heigth";
        infolabels[20] = "Volume depth";
        infolabels[21] = "Plane spacing";
        
        
        JPanel infopanel = new JPanel(new GridLayout(22,2,3,3));
        Font dafont = new Font(null);
        float fontsize = 11;
        dafont = dafont.deriveFont(fontsize);
        Font dafontbold = dafont.deriveFont(Font.BOLD);
        
        for (int i=0; i<22; i++)
        {
            JLabel infolab = new JLabel("  "+infolabels[i]);
            infolab.setFont(dafontbold);
            infopanel.add(infolab);
            JTextArea area = new JTextArea("  "+LSMinfoText[i]);
            area.setEditable(false);
            area.setFont(dafont);
            infopanel.add(area);
        }
        
        infoFrame.getContentPane().add(infopanel);
        infoFrame.show();
    }
    
    /** another way to init the info panel */
    public void initInfoFrame(String[] str)
    {
        
        
        infoFrame.getContentPane().removeAll();
        String[] infolabels = new String[22];
        
        infolabels[0] = "File Name";
        infolabels[1] = "User";
        infolabels[2] = "Image Width";
        infolabels[3] = "Image Height";
        infolabels[4] = "Number of channels";
        infolabels[5] = "Z Stack size";
        infolabels[6] = "Time Stack size";
        infolabels[7] = "Scan Type";
        infolabels[8] = "Sampling mode";
        infolabels[9] = "Short notes";
        infolabels[10] = "Detailed notes";
        infolabels[11] = "Voxel X size";
        infolabels[12] = "Voxel Y size";
        infolabels[13] = "Voxel Z size";
        infolabels[14] = "Objective";
        infolabels[15] = "X zoom factor";
        infolabels[16] = "Y zoom factor";
        infolabels[17] = "Z zoom factor";
        infolabels[18] = "Plane width";
        infolabels[19] = "Plane heigth";
        infolabels[20] = "Volume depth";
        infolabels[21] = "Plane spacing";
        
        
        JPanel infopanel = new JPanel(new GridLayout(22,2,3,3));
        Font dafont = new Font(null);
        float fontsize = 11;
        dafont = dafont.deriveFont(fontsize);
        Font dafontbold = dafont.deriveFont(Font.BOLD);
        
        for (int i=0; i<22; i++)
        {
            JLabel infolab = new JLabel("  "+infolabels[i]);
            infolab.setFont(dafontbold);
            infopanel.add(infolab);
            JTextArea area = new JTextArea("  "+str[i]);
            area.setEditable(false);
            area.setFont(dafont);
            infopanel.add(area);
        }
        
        infoFrame.getContentPane().add(infopanel);
        infoFrame.show();
        
    }
    
    
    /** sets the current lsm info text */ 
    public void setLSMinfoText(String[] str) {
        LSMinfoText = str;
    }
    
    
    /** Adds the dispose() function to a JButton in a JFrame */
    private void addExitListener(final JButton button, final JFrame parent) {
        button.addActionListener(new ActionListener () {
            public void actionPerformed(ActionEvent e) {
                ij.WindowManager.closeAllWindows();
                closeFrames();
            }
        });
    }
    
    /** Adds the OpenLSLM() function to a JButton in a JFrame */
    private void addOpenListener(final JButton button, final JFrame parent) {
        button.addActionListener(new ActionListener () {
            public void actionPerformed(ActionEvent e) {
                LSM_Reader_ reader = new LSM_Reader_();
                reader.OpenLSM("");
                setLSMinfoText(reader.printINFO());
                initInfoFrame();
                
                
            }
        });
    }
    
    /** Adds the "close all image windows" function to a JButton in a JFrame */
    private void addCloseWinListener(final JButton button, final JFrame parent) {
        button.addActionListener(new ActionListener () {
            public void actionPerformed(ActionEvent e) {
                ij.WindowManager.closeAllWindows();
                setLSMinfoText(new String[22]);
                initInfoFrame();
                infoFrame.dispose();
                               
            }
        });
    }
    
    
    /** Gathers all channels in a single Image Window or Expands them */
    private void addGatherExpandlistener(final JButton button, final JFrame parent) {
        button.addActionListener(new ActionListener () {
            public void actionPerformed(ActionEvent e) {
                if (button.getLabel() == " Gather ")
                {
                    button.setLabel(" Expand ");
                    button.setToolTipText("Split all the channels in separated windows");
                }
                else
                {
                    button.setLabel(" Gather ");
                    button.setToolTipText("Show all channels in the same window");
                }
            }
        });
    }
    
    
    
    
    /** Adds the dispose() function to a JButton in a JFrame */
    private void addShowHideInfolistener(final JButton button, final JFrame parent) {
        button.addActionListener(new ActionListener () {
            public void actionPerformed(ActionEvent e) {
                /*if (button.getLabel() == " Show Info ")
                {
                    button.setLabel(" Hide Info ");
                    button.setToolTipText("Hide the info panel");
                    initInfoFrame();
                    infoFrame.show();
                }
                else 
                {
                    button.setLabel(" Show Info ");
                    button.setToolTipText("Show the LSM specific informations");
                    infoFrame.dispose();
                }
                infoFrame.show();*/
                infoFrame.show();
                //infoFrame.setState(infoFrame.NORMAL);
                //infoFrame.toBack();
            }
        });
    }

    
}


/** This class overrides the standard JButton Class to fit the GUI needs */
class GUIButton extends JButton {
       
    public GUIButton(String buttonText, String tooltipText) {
        Font dafont = new Font(null);
        float fontsize = 11;
        dafont = dafont.deriveFont(fontsize);
        dafont = dafont.deriveFont(Font.BOLD);
        this.setFont(dafont);
        this.setLabel(buttonText);
        this.setForeground(SystemColor.windowText);
        this.setToolTipText(tooltipText);
    }
}

/** this class defines a focus listener for an image to refresh the info panel with the image correct info*/
class MyFocusListener extends WindowAdapter {
    
    private String[] inf;
    String str ;
    ImagePlus current_imp;
    
    public MyFocusListener(String[] s, ImagePlus imp){
        inf = new String[22];
        current_imp = imp;
        inf = s;
        str ="";
        for (int i=0; i<22; i++) str += inf[i] + "\n";
        
    }
    
    public void windowActivated(WindowEvent e) {
        // The component gains the focus
        initInfoFrame(inf);
        infoFrame.dispose();
        //infoFrame.setState(infoFrame.ICONIFIED);
        //infoFrame.toBack();
        
        
    }
    public void windowLostFocus(WindowEvent e){
        // The component lost the focus
        
    }
    
    /** the same initInfoFrame() method as above but redefined here for the listener's needs */
    public void initInfoFrame(String[] str)
    {
        infoFrame.getContentPane().removeAll();
        String[] infolabels = new String[22];
        
        infolabels[0] = "File Name";
        infolabels[1] = "User";
        infolabels[2] = "Image Width";
        infolabels[3] = "Image Height";
        infolabels[4] = "Number of channels";
        infolabels[5] = "Z Stack size";
        infolabels[6] = "Time Stack size";
        infolabels[7] = "Scan Type";
        infolabels[8] = "Sampling mode";
        infolabels[9] = "Short notes";
        infolabels[10] = "Detailed notes";
        infolabels[11] = "Voxel X size";
        infolabels[12] = "Voxel Y size";
        infolabels[13] = "Voxel Z size";
        infolabels[14] = "Objective";
        infolabels[15] = "X zoom factor";
        infolabels[16] = "Y zoom factor";
        infolabels[17] = "Z zoom factor";
        infolabels[18] = "Plane width";
        infolabels[19] = "Plane heigth";
        infolabels[20] = "Volume depth";
        infolabels[21] = "Plane spacing";
        
        
        JPanel infopanel = new JPanel(new GridLayout(22,2,3,3));
        Font dafont = new Font(null);
        float fontsize = 11;
        dafont = dafont.deriveFont(fontsize);
        Font dafontbold = dafont.deriveFont(Font.BOLD);
        
        for (int i=0; i<22; i++)
        {
            JLabel infolab = new JLabel("  "+infolabels[i]);
            infolab.setFont(dafontbold);
            infopanel.add(infolab);
            JTextArea area = new JTextArea("  "+str[i]);
            area.setEditable(false);
            area.setFont(dafont);
            infopanel.add(area);
        }
        
        infoFrame.getContentPane().add(infopanel);
        //infoFrame.show();
        
        
    }
}

/***********************/
class LSM_Reader_ {
    
    /* header values */
    
     public String FILENAME = "";
     public String DIRECTORY = "";
     public File LSM;
    
    /* array with LSM specific infos */
     public String[] infos = new String[22]; 
    /*CZ-private TAG Entries*/
     public long TIF_NEWSUBFILETYPE = 0;
     public long TIF_IMAGEWIDTH = 0;
     public long TIF_IMAGELENGTH = 0;
     public long LENGTH1 = 0;
     public long LENGTH2 = 0;
     public long TIF_BITSPERSAMPLE_CHANNEL1 = 0;
     public long TIF_BITSPERSAMPLE_CHANNEL2 = 0;
     public long TIF_BITSPERSAMPLE_CHANNEL3 = 0;
     public long TIF_COMPRESSION = 0;
     public long TIF_PHOTOMETRICINTERPRETATION = 0;
     public long TIF_STRIPOFFSETS = 0;
     public long TIF_STRIPOFFSETS1 = 0;
     public long TIF_STRIPOFFSETS2 = 0;
     public long TIF_STRIPOFFSETS3 = 0;
     public long TIF_SAMPLESPERPIXEL = 0;
     public long TIF_STRIPBYTECOUNTS = 0;
     public long TIF_STRIPBYTECOUNTS1 = 0;
     public long TIF_STRIPBYTECOUNTS2 = 0;
     public long TIF_STRIPBYTECOUNTS3 = 0;
     public long TIF_CZ_LSMINFO = 0;
     public long DIMENSION_X = 0;
     public long DIMENSION_Y = 0;
     public long DIMENSION_Z = 0;
     public long NUMBER_OF_CHANNELS = 0;
     public long THUMB_X = 0;
     public long THUMB_Y = 0;
     public double VOXELSIZE_X = 0;
     public double VOXELSIZE_Y = 0;
     public double VOXELSIZE_Z = 0;
     public long DATATYPE = 1;
     public long SCANTYPE = 0;
     public long STACKSIZE = 0;
     public long TIMESTACKSIZE = 0;
     public long OFFSET_SCANINFO = 0;
     public long OFFSET_CHANNELSCOLORS = 0;
     public long OFFSET_CHANNELDATATYPES = 0;
    /* infos gathered in the CZ_LSMINFO structure */
     public String DetailedNotes = "";
     public String ShortNotes = "";
     public String Objective = "";
     public String Sampling_Mode = "";
     public String User = "NA";
     public String[] Channel = new String[32];
     public long SUBTYPE = 0;
     public long SUBSIZE = 0;
     public long ENTRY = 0;
     public double PLANE_SPACING = 0;
     public double PLANE_WIDTH = 0;
     public double PLANE_HEIGHT = 0;
     public double VOLUME_DEPTH = 0;
     public double VOXEL_X = 0;
     public double VOXEL_Y = 0;
     public double VOXEL_Z = 0;
     public double ZOOM_X = 0;
     public double ZOOM_Y = 0;
     public double ZOOM_Z = 0;
    
    
    public void LSM_Reader_() {        
    }
    
    /*The method run describes what the plugin actually does */
    public void OpenLSM (String arg)
    {
        /* Gets the filename and the directory via ImageJ methods */
        OpenDialog od = new OpenDialog("Open LSM image ... ", arg);
        FILENAME = od.getFileName();
        
        
        /* Clean exit of the program if no file selected */
        if (FILENAME == null) 
        {
            IJ.error("no file selected");
            return;
        }
        DIRECTORY = od.getDirectory();
        if (DIRECTORY == null) 
        {
            IJ.error("no file selected");
            return;
        }
        LSM = new File(DIRECTORY, FILENAME);
        
        /* Shows the "about" dialog if "about" is passed as argument */ 
        if (arg.equals("about")) 
        {
            showAbout();
        }
        else
            
        /* Tests if the selected file is a valid LSM 510 file */
        {
        boolean test = isLSMfile();
        if (test) 
        {
            
        /* Searches for the number of tags in the first image directory */    
            long iterator1 = HowManyTAGs(8);
        /* Analyses each tag found */
            for (int k=0 ; k<iterator1 ; k++)
            {
                byte[] TAG1 = readTAG(10+12*k);
                analyseTAG(TAG1);
            }
         /* Searches for the offsets to pixel data*/
            getSTRIPOFFSETS(TIF_STRIPOFFSETS);
         /* Searches for the number of bytes used for a single pixel in each channel*/
            getSTRIPBYTECOUNTS(TIF_STRIPBYTECOUNTS);
         /* Searches for infos located in the CZ-private TAG */
            getCZ_LSMINFO(TIF_CZ_LSMINFO);
         /* Gets the LSM specific information located in the CZ-LSMinfo specific structure */
            getSCANINFO(OFFSET_SCANINFO);
         
          /* Opens an Image Window for each channel */
            int daOffset = (DATATYPE == 2)?((int) (TIF_IMAGEWIDTH*TIF_IMAGELENGTH*2)):((int) (TIF_IMAGEWIDTH*TIF_IMAGELENGTH));
            for (int j = 0; j<(int)(NUMBER_OF_CHANNELS); j++)
            {
                ImagePlus imp = open(DIRECTORY, FILENAME, daOffset*j);
                imp.setTitle(FILENAME + " Channel : " + Channel[j]);
                /* Shows the image if it exists */
                if (imp != null ) 
                {
			imp.show();
                        imp.getWindow().addWindowListener(new MyFocusListener(this.printINFO(), imp));
                        
                        
                        
                }
                else
                {
			IJ.showMessage("Open LSM...", "Failed.");
		}
                
            }
            
          
        }
        /* Clean exit if selected file is not a valid LSM file       */
        /* NB : Here the term "valid" is given for any file matching */
        /* the identifier regardless of the file version, even if    */
        /* this plug-in is written for LSM 510 version 2.8 only.     */
        else 
        {
            IJ.error(" Selected image is not a valid LSM file ");
            return;
        }
        }
    }
    
       
///////////// isLSMfile method definition //////////////////////////////////////
public  boolean isLSMfile () 
{
    boolean identifier = false;
    long ID = 0;
    try
    {
        /* An ImageJ RandomAccessStream is used here as in the whole program */
        /* to increase the execution speed using a buffered stream           */
        RandomAccessFile file = new RandomAccessFile(LSM, "r");
        RandomAccessStream stream = new RandomAccessStream(file);
        stream.seek(2);//offset to the identifier
        /* Reads the identifier */
        ID = swap(stream.readShort());
        if (ID == 42) identifier = true;
    }
    catch (IOException isLSMfile_IOex)
    {
        isLSMfile_IOex.printStackTrace();
    }
    return identifier;
}
////////////// readTAG method definition ///////////////////////////////////////
 public byte[] readTAG (long position) 
{
    /* As many treatments must be done here , all the bytes are stocked in */
    /* an array.                                                           */
    byte[] DIRENTRY = new byte[12];
    try
    {
        RandomAccessFile LSMFile = new RandomAccessFile(LSM, "r");
        LSMFile.seek(position);
        for (int i=0; i<12; i++)
        {
            DIRENTRY[i] = LSMFile.readByte();
        }
        LSMFile.close();
    }
    catch(IOException readTAG_exception)
    {
        readTAG_exception.printStackTrace();
    }
    return DIRENTRY;
}

//////////// analyseTAG method definition //////////////////////////////////////
 public void analyseTAG(byte[] DIRENTRY)
{
    /* This method works on 12 bytes arrays because java natively works on        */
    /* Big-Endian data but here the whole file is in Intel Byte Order which means */
    /* Little-Endian byte order. Thus, the byte swapping code trick on the array. */
    int TAGTYPE = 0;
    long LENGTH = 0;
    int MASK = 0x00ff;
    long MASK2 = 0x000000ff;
            
    TAGTYPE = ((DIRENTRY[1] & MASK) << 8) | ((DIRENTRY[0] & MASK ) <<0);
    LENGTH = ((DIRENTRY[7] & MASK2) << 24) | ((DIRENTRY[6] & MASK2) << 16) | ((DIRENTRY[5] & MASK2) << 8) | (DIRENTRY[4] & MASK2);
            
    switch (TAGTYPE)
    {
    case 254:
        TIF_NEWSUBFILETYPE = ((DIRENTRY[11] & MASK2) << 24) | ((DIRENTRY[10] & MASK2) << 16) | ((DIRENTRY[9] & MASK2) << 8) | (DIRENTRY[8] & MASK2);
        break;
    case 256:
        TIF_IMAGEWIDTH = ((DIRENTRY[11] & MASK2) << 24) | ((DIRENTRY[10] & MASK2) << 16) | ((DIRENTRY[9] & MASK2) << 8) | (DIRENTRY[8] & MASK2);
        break;
    case 257:
        TIF_IMAGELENGTH = ((DIRENTRY[11] & MASK2) << 24) | ((DIRENTRY[10] & MASK2) << 16) | ((DIRENTRY[9] & MASK2) << 8) | (DIRENTRY[8] & MASK2);
        break;
    case 258:
        LENGTH1 = ((DIRENTRY[7] & MASK2) << 24) | ((DIRENTRY[6] & MASK2) << 16) | ((DIRENTRY[5] & MASK2) << 8) | (DIRENTRY[4] & MASK2);
        TIF_BITSPERSAMPLE_CHANNEL1 = ((DIRENTRY[8] & MASK2) << 0);
        TIF_BITSPERSAMPLE_CHANNEL2 = ((DIRENTRY[9] & MASK2) << 0);
        TIF_BITSPERSAMPLE_CHANNEL3 = ((DIRENTRY[10] & MASK2) << 0);
        break;
    case 259:
        TIF_COMPRESSION = ((DIRENTRY[8] & MASK2) << 0);
        break;
    case 262:
        TIF_PHOTOMETRICINTERPRETATION = ((DIRENTRY[8] & MASK2) << 0);
        break;
    case 273:
        LENGTH2 = ((DIRENTRY[7] & MASK2) << 24) | ((DIRENTRY[6] & MASK2) << 16) | ((DIRENTRY[5] & MASK2) << 8) | (DIRENTRY[4] & MASK2);
        TIF_STRIPOFFSETS = ((DIRENTRY[11] & MASK2) << 24) | ((DIRENTRY[10] & MASK2) << 16) | ((DIRENTRY[9] & MASK2) << 8) | (DIRENTRY[8] & MASK2);
        break;
    case 277:
        TIF_SAMPLESPERPIXEL = ((DIRENTRY[8] & MASK2) << 0);
        break;
    case 279:
        TIF_STRIPBYTECOUNTS = ((DIRENTRY[11] & MASK2) << 24) | ((DIRENTRY[10] & MASK2) << 16) | ((DIRENTRY[9] & MASK2) << 8) | (DIRENTRY[8] & MASK2);
        break;
    case 34412:
        TIF_CZ_LSMINFO = ((DIRENTRY[11] & MASK2) << 24) | ((DIRENTRY[10] & MASK2) << 16) | ((DIRENTRY[9] & MASK2) << 8) | (DIRENTRY[8] & MASK2);
        break;
    default:
        break;
    }
}
        
/////////////// printINFO method definition ////////////////////////////////////
 public String[] printINFO()
{
    /* This method simply converts numbers into strings using ImageJ d2s()    */
    /* method and creates an ImageJ textwindow to display properly the infos. */
    
    String stacksize = IJ.d2s(DIMENSION_Z, 0);
    String width = IJ.d2s(TIF_IMAGEWIDTH, 0);
    String height = IJ.d2s(TIF_IMAGELENGTH, 0);
    String channels = IJ.d2s(NUMBER_OF_CHANNELS, 0);
    String scantype = "";
    int scan = (int) SCANTYPE;
    switch (scan)
    {
        case 0:
            scantype = "Normal X-Y-Z scan";
            break;
        case 1:
            scantype = "Z scan";
            break;
        case 2:
            scantype = "Line scan";
            break;
        case 3:
            scantype = "Time series X-Y";
            break;
        case 4:
            scantype = "Time series X-Z";
            break;
        case 5:
            scantype = "Time series - Means of ROIs";
            break;
        default : 
            scantype = "UNKNOWN !";
            break;
    }
    
    String voxelsize_x = IJ.d2s(VOXELSIZE_X*1000000, 2) + " µm";
    String voxelsize_y = IJ.d2s(VOXELSIZE_Y*1000000, 2) + " µm";
    String voxelsize_z = IJ.d2s(VOXELSIZE_Z*1000000, 2) + " µm";
    String timestacksize = IJ.d2s(TIMESTACKSIZE, 0);
    String plane_spacing = IJ.d2s(PLANE_SPACING, 2) + " µm";
    String plane_width = IJ.d2s(PLANE_WIDTH, 2) + " µm";
    String plane_height = IJ.d2s(PLANE_HEIGHT, 2) + " µm";
    String volume_depth = IJ.d2s(VOLUME_DEPTH, 2) + " µm";
    String channel_names = "";
    
    
    infos[0] = FILENAME ;
    infos[1] = User;
    infos[2] = width;
    infos[3] = height;
    infos[4] = channels;
    infos[5] = stacksize;
    infos[6] = timestacksize; 
    infos[7] = scantype;
    infos[8] = Sampling_Mode;
    infos[9] = ShortNotes;
    infos[10] = DetailedNotes;
    infos[11] = voxelsize_x;
    infos[12] = voxelsize_y;
    infos[13] = voxelsize_z;
    infos[14] = Objective;
    infos[15] = IJ.d2s(ZOOM_X, 2);
    infos[16] = IJ.d2s(ZOOM_Y, 2);
    infos[17] = IJ.d2s(ZOOM_Z, 2);
    infos[18] = plane_width;
    infos[19] = plane_height;
    infos[20] = volume_depth;
    infos[21] = plane_spacing;
   
    return infos;
          
    
}

//////////////////////// getSTRIPOFFSETS method definition /////////////////////
 public void getSTRIPOFFSETS(long position)
{
    try
    {
        RandomAccessFile file = new RandomAccessFile(LSM, "r");
        RandomAccessStream stream = new RandomAccessStream(file);
        stream.seek((int)position);
        TIF_STRIPOFFSETS1 = swap(stream.readInt());
        TIF_STRIPOFFSETS2 = swap(stream.readInt());
        TIF_STRIPOFFSETS3 = swap(stream.readInt());
        stream.close();
        file.close();
    }
    catch(IOException getSTRIPOFFESTS_exception)
    {
        getSTRIPOFFESTS_exception.printStackTrace();
    }
}

//////////////////////// getSTRIPBYTECOUNTS method definition //////////////////
 public void getSTRIPBYTECOUNTS(long position)
{
    try
    {
        RandomAccessFile file = new RandomAccessFile(LSM, "r");
        RandomAccessStream stream = new RandomAccessStream(file);
        stream.seek((int)position);
        TIF_STRIPBYTECOUNTS1 = swap(stream.readInt());
        TIF_STRIPBYTECOUNTS2 = swap(stream.readInt());
        TIF_STRIPBYTECOUNTS3 = swap(stream.readInt());
        stream.close();
        file.close();
    }
    catch(IOException getSTRIPBYTECOUNTS_exception)
    {
        getSTRIPBYTECOUNTS_exception.printStackTrace();
    }
}

////////////////////// HowManyTAGS method definition ///////////////////////////
 public long HowManyTAGs(long position)
{
    long TAGs = 0;
    try
    {
        RandomAccessFile file = new RandomAccessFile(LSM, "r");
        RandomAccessStream stream = new RandomAccessStream(file);
        stream.seek((int)position);
        TAGs = swap(stream.readShort());
        stream.close();
        file.close();
    }
    catch(IOException HowManyTAGs_exception)
    {
        HowManyTAGs_exception.printStackTrace();
    }
    return TAGs;
}

///////////////////////////////////////////////////////////////////////////////
 public void getCZ_LSMINFO(long position)
{
    try
    {
        RandomAccessFile file = new RandomAccessFile(LSM, "r");
        RandomAccessStream stream = new RandomAccessStream(file);
        if (position == 0) return;
        stream.seek((int)position+8);
        
        DIMENSION_X = swap(stream.readInt());
        DIMENSION_Y = swap(stream.readInt());
        DIMENSION_Z = swap(stream.readInt());
        NUMBER_OF_CHANNELS = swap(stream.readInt());
        TIMESTACKSIZE = swap(stream.readInt());
        DATATYPE = swap(stream.readInt());
        
        stream.seek((int)position+88);
        SCANTYPE = swap(stream.readShort());
        
        stream.seek((int)position + 40);
        VOXELSIZE_X = swap(stream.readDouble());
        stream.seek((int)position + 48);
        VOXELSIZE_Y = swap(stream.readDouble());
        stream.seek((int)position + 56);
        VOXELSIZE_Z = swap(stream.readDouble());
        
        stream.seek((int)position + 108);
        OFFSET_CHANNELSCOLORS = swap(stream.readInt());
        stream.seek((int)position + 120);
        OFFSET_CHANNELDATATYPES = swap(stream.readInt());
        
        stream.seek((int)position+124);
        OFFSET_SCANINFO = swap(stream.readInt());
        
        stream.close();
        file.close();
    }
    catch(IOException getCZ_LSMINFO_exception)
    {
        getCZ_LSMINFO_exception.printStackTrace();
    }
}

////////////////////////////////////////////////////////////////////////////////
 public void getSCANINFO(long position)
{
    try
        {
            
            RandomAccessFile file = new RandomAccessFile(LSM, "r");
            RandomAccessStream stream = new RandomAccessStream(file);
            
            /* Gets the correct channel names */
            stream.seek((int) OFFSET_CHANNELSCOLORS);
            int BlockSize = swap(stream.readInt());
            stream.seek((int) OFFSET_CHANNELSCOLORS+16);
            int NamesOffset = swap(stream.readInt());
            file.seek(NamesOffset + (int) OFFSET_CHANNELSCOLORS);
            int Namesize = BlockSize - NamesOffset;
            String AllNames = file.readLine();
            AllNames = AllNames.substring(0, Namesize);
            int k = 0;
            int begindex = 4;
            int endindex = 5;
            for (int j = 0; j<NUMBER_OF_CHANNELS; j++)
            {
            endindex = AllNames.indexOf(00, begindex);
            Channel[j] = AllNames.substring(begindex, endindex);
            begindex = endindex+5;
            }
            //////////////////////
            /* Seeks for entries in the scaninfo structure until the end of the */ 
            /* file as the end of the structure is not known yet.               */
            long temp = file.length() - ((int)position);
            if (temp>0) 
            {    
                long END = file.length() - ((int)position);
                for (int i = 0; i<(int) END; i+= ((int)SUBSIZE+12) )
                {
                stream.seek((int)position + i);    
                ENTRY = swap(stream.readInt());
                SUBTYPE = swap(stream.readInt());
                SUBSIZE = swap(stream.readInt());
            
                    if (ENTRY == 0x000000001000001e)
                    {
                        PLANE_SPACING = swap(stream.readDouble());
                    }

                    if (ENTRY == 0x000000001000001f)
                    {
                        PLANE_WIDTH = swap(stream.readDouble());
                    }

                    if (ENTRY == 0x0000000010000020)
                    {
                        PLANE_HEIGHT = swap(stream.readDouble());
                    }

                    if (ENTRY == 0x0000000010000021)
                    {
                        VOLUME_DEPTH  = swap(stream.readDouble());
                    }

                    if (ENTRY == 0x0000000010000002)
                    {
                        ShortNotes = file.readLine();
                         if (SUBSIZE<ShortNotes.length()) 
                         {
                            ShortNotes = ShortNotes.substring(0,(int)SUBSIZE);
                         }
                         else ShortNotes = "NC, bugged file";

                    }
                    if (ENTRY == 0x0000000010000003)
                    {
                        DetailedNotes = file.readLine();
                        if (SUBSIZE<DetailedNotes.length()) 
                         {
                            DetailedNotes = DetailedNotes.substring(0,(int)SUBSIZE);
                         }
                         else DetailedNotes = "NC, bugged file";
                    }

                    if (ENTRY == 0x0000000010000004)
                    {
                        Objective = file.readLine();
                        if (SUBSIZE<Objective.length()) 
                         {
                            Objective = Objective.substring(0,(((int)SUBSIZE-2)>0)?((int)SUBSIZE-2):((int)SUBSIZE));
                         }
                         else Objective = "NC, bugged file";

                    }

                    if (ENTRY == 0x0000000010000016)
                    {
                        ZOOM_X = swap(stream.readDouble());
                    }

                    if (ENTRY == 0x0000000010000017)
                    {
                        ZOOM_Y = swap(stream.readDouble());
                    }

                    if (ENTRY == 0x0000000010000018)
                    {
                        ZOOM_Z = swap(stream.readDouble());
                    }

                    if (ENTRY == 0x0000000010000047)
                    {
                        User = file.readLine();
                        if (SUBSIZE<User.length()) 
                         {
                            User = User.substring(0,(((int)SUBSIZE-2)>0)?((int)SUBSIZE-2):((int)SUBSIZE));
                         }
                         else User = "NC, bugged file";
                    }

                    if (ENTRY == 0x0000000040000003)
                    {
                        int SAMPLING_MODE = swap(stream.readInt());
                        switch (SAMPLING_MODE)
                        {
                            case 0: Sampling_Mode = "Sample - No Average";
                                    break;

                            case 1: Sampling_Mode = "Line Average ";
                                    break;

                            case 2: Sampling_Mode = "Frame Average ";
                                    break;

                            case 3: Sampling_Mode = "Integration Mode ";
                                    break;
                            default : Sampling_Mode = "Not Found";
                        }
                    }

                    if (ENTRY == 0x0000000040000005)
                    {
                        int SAMPLING_NUMBER = swap(stream.readInt());
                        Sampling_Mode += IJ.d2s(SAMPLING_NUMBER, 0) +" x";
                    }
                }
            }
            else 
            {
                ShortNotes = "NC, bugged file";
                DetailedNotes = "NC, bugged file";
            }
            stream.close();
            file.close();
        }
        catch(IOException getSCANINFO_exception)
        {
            getSCANINFO_exception.printStackTrace();
        }
        
        
}
////////////////////////////////////////////////////////////////////////////////
////////// ImagePlus.open() method re-defintion ////////////////////////////////

/* This method simply sets up the fileinfo class which is used by imageJ when */
/* opening an image.                                                           */
public  ImagePlus open(String directory, String file, int offset) 
{
    File f = new File(directory, file);
    try
    {
        FileInputStream in = new FileInputStream(f);
        FileInfo fi = new FileInfo();
        fi.directory = directory;
        fi.fileFormat = fi.TIFF;
        fi.fileName = file ;
        int datatype = (int) DATATYPE;
        switch (datatype)
        {
            case 1:
                fi.fileType = fi.GRAY8;
                break;
            case 2:
                fi.fileType = fi.GRAY16_UNSIGNED;
                break;
            case 5:
                fi.fileType = fi.GRAY32_FLOAT;
                break;
            default : 
                fi.fileType = fi.GRAY8;
                break;
        }
        fi.gapBetweenImages = (int) ((TIF_IMAGEWIDTH*TIF_IMAGELENGTH)*(NUMBER_OF_CHANNELS-1));
        fi.height = (int) TIF_IMAGELENGTH;
        fi.intelByteOrder = true;
        int scantype = (int)(SCANTYPE);
        switch (scantype)
        {
            case 3:
                fi.nImages = (int) TIMESTACKSIZE;
                break;
            case 4:
                fi.nImages = (int) TIMESTACKSIZE;
                break;
            default:
                fi.nImages = (int) DIMENSION_Z;
                break;
        }
        fi.offset = (LENGTH2==1)?((int)TIF_STRIPOFFSETS + offset):((int)TIF_STRIPOFFSETS1 + offset);
        fi.width = (int) TIF_IMAGEWIDTH;
        /*The voxel sizes are given in meters. Yet they do not exceeds the µm */
        /*so the values are multiplied by 1 million to fit the µm unit.       */
        fi.pixelDepth = VOXELSIZE_Z*1000000;
        fi.pixelHeight = VOXELSIZE_Y*1000000;
        fi.pixelWidth = VOXELSIZE_X*1000000;
        fi.unit = "µm";
        fi.valueUnit ="µm";
        FileOpener fo = new FileOpener(fi);
        ImagePlus imp = fo.open(false);
        IJ.showStatus("");
        return imp;
    }
    catch (IOException open) 
    {
        IJ.error("An error occured while reading the file.\n \n" + open);
        IJ.showStatus("");
        return null;
    }
}
    

/////////////// bit swapping methods ///////////////////////////////////////////    
/* Those methods were taken from Java JRE 1.4 as ImageJ works only with 1.3 */
/* which doesn't have them.                                                 */

 short swap(short x) {
	return (short)((x << 8) |
		       ((x >> 8) & 0xff));
    }

     char swap(char x) {
	return (char)((x << 8) |
		      ((x >> 8) & 0xff));
    }

     int swap(int x) {
	return (int)((swap((short)x) << 16) |
		     (swap((short)(x >> 16)) & 0xffff));
    }

    long swap(long x) {
	return (long)(((long)swap((int)(x)) << 32) |
		      ((long)swap((int)(x >> 32)) & 0xffffffffL));
    }

     float swap(float x) {
	//return Float.intBitsToFloat(swap(Float.floatToRawIntBits(x))); not accepted by mac os 9!
        return Float.intBitsToFloat(swap(Float.floatToIntBits(x)));
    }

     double swap(double x) {
	//return Double.longBitsToDouble(swap(Double.doubleToRawLongBits(x))); not accepted by mac os 9!
        return Double.longBitsToDouble(swap(Double.doubleToLongBits(x)));
    }
////////////////////////////////////////////////////////////////////////////////
/* The About message */
void showAbout()
{
    IJ.showMessage("About LSM Image Opener...",
    "Version 2.2\n"
    +"Works on images generated by LSM 510 version 2.8\n"
    +"Not tested on other versions\n"
    +"Written by Yannick KREMPP\n"
    +"For IBMP-CNRS Strasbourg, France\n"
    +"contact : yannickkrempp@wanadoo.fr");
}
///////////////////////////////////////////////////////////////////////////////


}
    public void run(String args) {
        theGUI = new GUI();
        theGUI.init();
    }
}
