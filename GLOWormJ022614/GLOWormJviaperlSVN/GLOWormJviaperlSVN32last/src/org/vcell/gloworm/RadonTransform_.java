package org.vcell.gloworm;

import ij.*;
import ij.process.*;
import ij.io.*;
import java.io.*;
import ij.measure.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import ij.plugin.frame.*;
import javax.swing.*;



/*This plugin takes an image and performs a radon transform (by using a back projection algorithm) on it.
 * The result is a set of projection data that is displayed as an 8 bit grayscale image (sometimes called a sinogram).
 * The plugin accepts 8 or 16 bit grayscale images as input. (8 bit RGB images must be converted to 8 bit grayscale
 * before back projection - not yet functional).
 * The projection data can preferentially be re-projected using filtered back projection to produce the inverse
 * transform. The result can be output as an 8 or 16-bit image.
 *
 * @author Damien Farrell
 * version 2.0, 20 Feb 2006
 */

public class RadonTransform_ extends PlugInFrame implements ActionListener {
    
    JPanel mainpanel, settingspanel, buttonpanel;
    JTextArea infopane;
    JTextField scansfield, stepsizefield, viewsfield, filtercutofffield, zoomfield,
            outsizefield, psfield;
    JCheckBox interpbox, savedatabox, filteringbox, dostackbox;
    JComboBox filterList, outdepthlist;
    JButton okbutton, clearbutton, reconstructbutton, loadbutton, importbutton, savebutton;
    
    Font smallFont, mainFont;
    int views = 180;
    int scans = 256;
    float phi =0, stepsize=1; int ang1=0,ang2=180;
    int type, S=0, w, h, outsize = 64,outdepth = 8, stacksize=1, projstacksize=1;
    double zoom = 1, xoffset = 0, yoffset = 0, filtercutoff=1;
    boolean fast=false,interrupt=false, filtering = true, showsavedialog = false,
            reconstruct = false, cancelled = false, doentirestack, incols = true;
    String method="fbp", interp="linear", filtername="ramp";
    double projection[][]; // stores all projections for each view
    double fprojection[][]; //stores all filtered projections for each view
    double[][] bppixels;  //stores reconstructed image
    double pix[][];
    Object projectionstack[];
    ImagePlus imp;
    ImageStack inputstack;
    ByteProcessor bip;
    ShortProcessor  sip;
    byte[] bpixels; short[] spixels;
    
    public RadonTransform_() {
        super("Radon Transform Plugin");
        setLayout(new BorderLayout());
        mainFont = new Font("SansSerif", Font.PLAIN, 11);
        smallFont = new Font("SansSerif", Font.BOLD, 10);
        UIManager.put("ComboBox.font", mainFont);
        infopane = new JTextArea("Radon Transform"+"\n", 8, 20);
        infopane.setBackground(SystemColor.control);
        
        imp = IJ.getImage(); type = imp.getType();
        ImageProcessor currentip = imp.getProcessor();
        getImagepixels(currentip);
        if (w!=h){ IJ.showMessage("Error", "Image dimensions should be equal"); return;  }
        outsize = w;
        
        if (type == ImagePlus.GRAY8) {infopane.append("Input Image is 8 bit gray"); }
        if (type == ImagePlus.GRAY16) { infopane.append("Input Image is 16 bit gray"); }
        if (type == ImagePlus.COLOR_256) { infopane.append("Input Image is 8 bit color"); }
        if (type == ImagePlus.COLOR_RGB) { infopane.append("Input Image is RGB color"); }
        infopane.append("\n"+"Size: "+w+"x"+h+"\n");
        infopane.setLineWrap(true);
        infopane.setWrapStyleWord(true);
        infopane.setFont(new Font("Arial", Font.BOLD, 11));
        JScrollPane scrollingResult = new JScrollPane(infopane);
        scrollingResult.setPreferredSize(new Dimension(200, 190));
        if (isPow2(w) == true){
            scans = w;
        } else {
            //closest power of 2 rounded up
            int power = (int)((Math.log(w) / Math.log(2)))+1;
            scans = (int) Math.pow(2, power);
        }
        
        mainpanel = new JPanel();
        mainpanel.setLayout(new BorderLayout());
        settingspanel = new JPanel();
        settingspanel.setBackground(SystemColor.control);
        mainpanel.setBackground(SystemColor.control);
        mainpanel.setMaximumSize(new Dimension(200,180));
        
        settingspanel.setLayout(new GridLayout(8, 2, 1, 1));
        settingspanel.setPreferredSize(new Dimension(180,200));
        settingspanel.setBorder(BorderFactory.createTitledBorder("settings"));
        settingspanel.add(new JLabel("Scans:"));
        scansfield = new JTextField(Integer.toString(scans));
        scansfield.setToolTipText("Press Enter to input new value");
        scansfield.addActionListener(this);
        scansfield.setColumns(3);
        settingspanel.add(scansfield);
        
        settingspanel.add(new JLabel("Angular Increment:"));
        stepsizefield = new JTextField(Float.toString(stepsize));
        stepsizefield.setToolTipText("Press Enter to input new value");
        stepsizefield.addActionListener(this);
        stepsizefield.setColumns(3);
        settingspanel.add(stepsizefield);
        
        settingspanel.add(new JLabel("Views:"));
        viewsfield = new JTextField(Float.toString(views));
        viewsfield.setColumns(3); viewsfield.setEditable(false);
        viewsfield.setBackground(SystemColor.control);
        settingspanel.add(viewsfield);
        
        settingspanel.add(new JLabel("Filter"));
        String[] filters = { "ramp", "shepplogan", "hann", "hamming", "cosine","blackman"};
        filterList = new JComboBox(filters);
        filterList.setSelectedIndex(0);
        filterList.addActionListener(this);
        filterList.setToolTipText("Back Projection Filter");
        settingspanel.add(filterList);
        
        settingspanel.add(new JLabel("Filter Cutoff:"));
        filtercutofffield = new JTextField(Double.toString(filtercutoff));
        filtercutofffield.setToolTipText("Press Enter to input new value");
        filtercutofffield.addActionListener(this);
        filtercutofffield.setColumns(3);
        settingspanel.add(filtercutofffield);
        
        settingspanel.add(new JLabel("Zoom:"));
        zoomfield = new JTextField(Double.toString(zoom));
        zoomfield.setToolTipText("Press Enter to input new value");
        zoomfield.addActionListener(this);
        zoomfield.setColumns(3);
        settingspanel.add(zoomfield);
        
        settingspanel.add(new JLabel("Output Img Size:"));
        outsizefield = new JTextField(Integer.toString(outsize));
        outsizefield.setToolTipText("Press Enter to input new value");
        outsizefield.addActionListener(this);
        outsizefield.setColumns(3);
        settingspanel.add(outsizefield);
        
        settingspanel.add(new JLabel("Output Depth"));
        String[] outdepths = { "8", "16"};
        outdepthlist = new JComboBox(outdepths);
        outdepthlist.setSelectedIndex(0);
        outdepthlist.addActionListener(this);
        outdepthlist.setToolTipText("Output image depth");
        settingspanel.add(outdepthlist);
        
        buttonpanel = new JPanel();
        buttonpanel.setBackground(SystemColor.control);
        buttonpanel.setLayout(new GridLayout(6, 1, 1, 1));
        buttonpanel.setPreferredSize(new Dimension(180,120));
        //buttonpanel.setBorder(BorderFactory.createTitledBorder("settings"));
        
        okbutton = new JButton("Calculate");
        okbutton.setToolTipText("Reproject Image");
        okbutton.addActionListener(this);
        buttonpanel.add(okbutton);
        loadbutton = new JButton("Load Data");
        loadbutton.setToolTipText("Load Projection Data from text file");
        loadbutton.addActionListener(this);
        buttonpanel.add(loadbutton);
        importbutton = new JButton("Import Data");
        importbutton.setToolTipText("Import Projection Data from a file");
        importbutton.addActionListener(this);
        buttonpanel.add(importbutton);
        savebutton = new JButton("Save Data");
        savebutton.setToolTipText("Save Projection Data to text file ");
        savebutton.addActionListener(this);
        buttonpanel.add(savebutton);
        reconstructbutton = new JButton("Reconstruct");
        reconstructbutton.setToolTipText("Back Project");
        reconstructbutton.addActionListener(this);
        buttonpanel.add(reconstructbutton);
        clearbutton = new JButton("Clear/Stop");
        clearbutton.setToolTipText("Stop current process");
        clearbutton.addActionListener(this);
        buttonpanel.add(clearbutton);
        
        mainpanel.add(buttonpanel, BorderLayout.CENTER);
        mainpanel.add(settingspanel, BorderLayout.NORTH);
        
        JPanel checkbuttpanel = new JPanel();
        checkbuttpanel.setBackground(SystemColor.control);
        checkbuttpanel.setBorder(BorderFactory.createTitledBorder("Other Options"));
        checkbuttpanel.setPreferredSize(new Dimension(100,90));
        checkbuttpanel.setMaximumSize(new Dimension(100,90));
        checkbuttpanel.setLayout(new GridLayout(4,1,0,0));
        
        interpbox = new JCheckBox("Linear Interpolation");
        interpbox.setContentAreaFilled(false);
        interpbox.addActionListener(this);
        interpbox.setSelected(true);
        interpbox.setToolTipText("Use Linear Interpolation");
        checkbuttpanel.add(interpbox);
        
        filteringbox = new JCheckBox("Use Filtering");
        filteringbox.setContentAreaFilled(false);
        filteringbox.addActionListener(this);
        filteringbox.setSelected(true);
        filteringbox.setToolTipText("Apply Filter");
        checkbuttpanel.add(filteringbox);
        
        dostackbox = new JCheckBox("Do Entire Stack");
        dostackbox.setContentAreaFilled(false);
        dostackbox.addActionListener(this);
        dostackbox.setSelected(false);
        dostackbox.setToolTipText("Back Project all images in the current stack.");
        checkbuttpanel.add(dostackbox);
        
        JPanel subpanel = new JPanel();
        //subpanel.setLayout(new GridLayout(1,2,0,0));
        subpanel.setLayout(new BoxLayout(subpanel, BoxLayout.X_AXIS));
        subpanel.setBackground(SystemColor.control);
        JLabel projlabel1 = new JLabel("Stack size:");
        projlabel1.setFont(smallFont);
        subpanel.add(projlabel1);
        psfield = new JTextField(Integer.toString(projstacksize));
        psfield.setColumns(3); psfield.setEditable(false);
        psfield.setBackground(SystemColor.control);
        psfield.setFont(smallFont);
        psfield.addActionListener(this);
        subpanel.add(psfield);
        checkbuttpanel.add(subpanel);
        
        mainpanel.add(checkbuttpanel, BorderLayout.SOUTH);
        
        add(scrollingResult, BorderLayout.WEST);
        add(mainpanel, BorderLayout.CENTER);
        
        pack();
        show();
        
        /*if (cancelled == true){
            close();
            return;
        } */
        
    }
    
    void getImagepixels(ImageProcessor ip){
        //imp = IJ.getImage();
        //type = imp.getType();
        stacksize = imp.getStackSize();
        
        if (imp.getType() == ImagePlus.GRAY8){
            //bip= (ByteProcessor) imp.getProcessor();
            bip= (ByteProcessor) ip;
            bpixels =(byte[]) bip.getPixels();
            w= bip.getWidth(); h = bip.getHeight();
        } else if (imp.getType() == ImagePlus.GRAY16){
            //sip = (ShortProcessor) imp.getProcessor();
            sip = (ShortProcessor) ip;
            spixels= (short[]) sip.getPixels();
            w = sip.getWidth(); h = sip.getHeight();
        } else if (imp.getType() == ImagePlus.COLOR_256){
            IJ.showMessage("Info", "Image will be converted to greyscale.");
            ImageConverter conv = new ImageConverter(imp);
            conv.setDoScaling(true);
            conv.convertToGray8();
            bip= (ByteProcessor) imp.getProcessor();
            bpixels =(byte[]) bip.getPixels();
            w= bip.getWidth(); h = bip.getHeight();
        } else if (imp.getType() == ImagePlus.COLOR_RGB){
            IJ.showMessage("Info", "Image will be converted to greyscale.");
            //new ImageConverter(imp).convertToGray8();
            //ImageProcessor ip = imp.getProcessor();
            ImageProcessor cp =new ColorProcessor(imp.getImage());
            imp.setProcessor(null, cp.convertToByte(true));
            bip= (ByteProcessor) imp.getProcessor();
            bpixels =(byte[]) bip.getPixels();
            w= bip.getWidth(); h = bip.getHeight();
        }
        
        
        //get pixel values from current image
        pix = new double[w][h];
        if (type == ImagePlus.GRAY8){
            for (int x = 0; x < pix[0].length; x++ ) {
                for (int y = 0; y < pix.length; y++ ) {
                    pix[x][y] = bpixels[y + x * pix.length] &0xFF;
                }
            }
        } else if (type == ImagePlus.GRAY16){
            for (int x = 0; x < pix[0].length; x++ ) {
                for (int y = 0; y < pix.length; y++ ) {
                    pix[x][y] = spixels[y + x * pix.length] &0xFFFF;
                }
            }
        }
    }
    
    void execute(){
        final SwingWorker worker;
        IJ.showStatus("Working...");
        infopane.append("Generating projection data"+"\n");
        
        if (doentirestack == true && stacksize>1){
            infopane.append("Back projecting all images in stack "+"\n");
            infopane.append("stack size=  "+stacksize+"\n");
            //runs back projection parts in their own thread, so that gui can still be used
            worker = new SwingWorker() {
                public Object construct() {
                    ImageStack inputstack = imp.getImageStack();
                    ImageStack sinogramstack = new ImageStack(views,scans);
                    projectionstack = new Object[stacksize];
                    for (int st=1;st<=stacksize;st++) {
                        infopane.append("image "+st+"\n");
                        ImageProcessor currentip = inputstack.getProcessor(st);
                        getImagepixels(currentip);
                        projection = ForwardProject(pix);
                        projectionstack[st-1] = projection;  //array of proj arrays
                        //createSinogram();
                        ImageProcessor sino = createSinogram(false);
                        sinogramstack.addSlice(Integer.toString(st),sino);
                        projstacksize = st;
                        if (cancelled == true) break;
                    }
                    ImagePlus sinoimages = new ImagePlus("Proj Stack", sinogramstack);
                    sinoimages.show();
                    return projection;
                }
                //Runs on the event-dispatching thread.
                public void finished() {
                    psfield.setText(Integer.toString(projstacksize));
                    infopane.append("Finished stack "+"\n");
                    IJ.showStatus("Done");
                    cancelled = false;
                }
            };
            
        } else {
            
            worker = new SwingWorker() {
                public Object construct() {
                    imp = IJ.getImage(); type = imp.getType();
                    ImageProcessor currentip = imp.getProcessor();
                    getImagepixels(currentip);
                    projection = ForwardProject(pix);
                    return projection;
                }
                //Runs on the event-dispatching thread.
                public void finished() {
                    createSinogram(true);
                    IJ.write("Done");
                    IJ.showStatus("Done");
                }
            };
        }
        
        worker.start();
        
        if (showsavedialog == true){
            SaveDialog sd = new SaveDialog("Save Proj Data","projdata",".txt");
            String path = sd.getFileName();
            String dir = sd.getDirectory();
            saveProjectionsFile(dir+path);
        }
        cancelled = false;
    }
    
    ImageProcessor createSinogram( boolean showimage){
        ImageProcessor resp = new ByteProcessor(views,scans);
        ImagePlus result = new ImagePlus("Sinogram", resp);
        byte[] barray= new byte[views*scans];
        
        for (int i = 0; i < views; i++ ) {
            for (int j = 0; j < scans; j++ ) {
                barray[i + j * views] = (byte) projection[i][j];
            }
        }
        resp.setPixels(barray);
        if (showimage == true)  result.show();
        return resp;
    }
    
    
    ImagePlus createImage(double[][] bppixels){
        ImagePlus bpimg;
        ImageProcessor bpimp;
        if (outdepth == 8){
            byte[] bparray= new byte[outsize*outsize];
            bpimp = new ByteProcessor(outsize, outsize);
            bpimg = new ImagePlus("Reconstruction",bpimp);
            for (int i = 0; i < outsize; i++ ) {
                for (int j = 0; j < outsize; j++ ) {
                    bparray[j + i * outsize] = (byte) bppixels[i][j];
                }
            }
            bpimp.setPixels(bparray);
            //bpimg.show();
        } else{ //else if (outdepth == 16){
            short[] bparray = new short[outsize*outsize];
            bpimp = new ShortProcessor(outsize, outsize);
            bpimg = new ImagePlus("Reconstruction",bpimp);
            for (int i = 0; i < outsize; i++ ) {
                for (int j = 0; j < outsize; j++ ) {
                    bparray[j + i * outsize] = (short) bppixels[i][j];
                }
            }
            bpimp.setPixels(bparray);
            Calibration cal  =  imp.getCalibration();
            bpimg.setCalibration(cal);
            //bpimg.show();
            IJ.doCommand("Window/Level...");
        }
        return bpimg;
    }
    
    
    //backproject
    void reconstruct(){
        final SwingWorker worker;
        infopane.append("Performing back projection.. ");
        bppixels = new double[outsize][outsize];
        if (doentirestack == true && projstacksize>1){
            infopane.append("Reconstructing all data in stack "+"\n");
            worker = new SwingWorker() {
                public Object construct() {
                    ImageStack outputstack = new ImageStack(outsize,outsize);
                    for (int p=0; p<projstacksize;p++) {
                        infopane.append("projdata "+(p+1)+"\n");
                        projection = (double[][]) projectionstack[p];
                        //if (projection == null) break;
                        bppixels = BackProject(projection, outsize);
                        ImagePlus tempimg = createImage(bppixels);
                        ImageProcessor tempip = tempimg.getProcessor();
                        outputstack.addSlice(Integer.toString(p),tempip);
                        if (cancelled == true) break;
                    }
                    ImagePlus outputimages = new ImagePlus("Reconstructed Stack", outputstack);
                    outputimages.show();
                    return bppixels;
                }
                public void finished() {
                    infopane.append("Finished stack "+"\n");
                    IJ.showStatus("Done");
                    cancelled = false;
                }
            };
            
        } else{
            worker = new SwingWorker() {
                public Object construct() {
                    bppixels = BackProject(projection, outsize);
                    return bppixels;
                }
                //Runs on the event-dispatching thread.
                public void finished() {
                    ImagePlus newimg = createImage(bppixels);
                    newimg.show();
                    IJ.showStatus("Done");
                }
            };
        }
        worker.start();
        
    }
    
    public void actionPerformed(ActionEvent e) {
    	cancelled = false;
        if (e.getSource() == clearbutton){
            infopane.setText(""); 
            cancelled = true;
        }
        if (e.getSource() == okbutton){
            imp = IJ.getImage(); type = imp.getType();
            ImageProcessor currentip = imp.getProcessor();
            getImagepixels(currentip);
            if (stacksize>1){
                infopane.append("Input is a stack with "+stacksize+" images"+"\n");
            }
            execute();
        }
        if (e.getSource() == loadbutton){
            FileDialog fd = new FileDialog(this);
            fd.setVisible(true);
            if (fd.getFile() == null) return;
            String path = fd.getDirectory() + fd.getFile();
            loadProjectionsFile(path);
            scansfield.setText(Integer.toString(scans));
            stepsizefield.setText(Float.toString(stepsize));
            viewsfield.setText(Integer.toString(views));
            if  (projstacksize > 1){
                ImageStack sinogramstack = new ImageStack(views,scans);
                for (int p=0;p<projstacksize;p++) {
                    projection = (double[][]) projectionstack[p];
                    ImageProcessor sino = createSinogram(false);
                    sinogramstack.addSlice(Integer.toString(p),sino);
                }
                ImagePlus sinoimages = new ImagePlus("Proj Stack", sinogramstack);
                sinoimages.show();
                psfield.setText(Integer.toString(projstacksize));
            } else {
                createSinogram(true);
            }
        }
        
        if (e.getSource() == importbutton){
            FileDialog fd = new FileDialog(this);
            fd.setVisible(true);
            if (fd.getFile() == null) return;
            String path = fd.getDirectory() + fd.getFile();
            ij.io.Opener op = new Opener();
            op.open(path); 
            imp = IJ.getImage();  
            int ss = imp.getStackSize();
            infopane.append("Stacksize= "+ss+"\n"); 
            imp.hide();
            importProjections(imp, ss);
            scansfield.setText(Integer.toString(scans));
            stepsizefield.setText(Float.toString(stepsize));
            viewsfield.setText(Integer.toString(views));
            if (ss == 1)  createSinogram(true); //if not a stack
            psfield.setText(Integer.toString(projstacksize));
        }
        
        if (e.getSource() == savebutton){
            SaveDialog sd = new SaveDialog("Save Proj Data","projdata",".txt");
            String path = sd.getFileName();
            String dir = sd.getDirectory();
            saveProjectionsFile(dir+path);
        }
        
        if (e.getSource() == reconstructbutton){
            if (projection == null) infopane.append("No projection data."+"\n"); else   reconstruct();
        }
        if (e.getSource() == scansfield){
            scans =Integer.parseInt(scansfield.getText().trim());
            infopane.append("if no. scans changed, must recalculate data"+"\n");
        }
        if (e.getSource() == stepsizefield){
            stepsize = Float.parseFloat(stepsizefield.getText().trim());
            double tempval = ((Math.abs(ang2)-ang1)/stepsize);
            if (180%stepsize != 0) {
                infopane.append("Value should produce integer number of angles!"+"\n"); stepsize=1;
            } else {
                views = (int) tempval;
                viewsfield.setText(Integer.toString(views));
                infopane.append("views= "+views+"\n"+"recalculate data"+"\n");
            }
        }
        if (e.getSource() == filterList){
            filtername = (String) filterList.getSelectedItem();
            IJ.write(filtername);
        }
        if (e.getSource() == filtercutofffield){
            filtercutoff = Double.parseDouble(filtercutofffield.getText().trim());
            infopane.append("filter cutoff= "+filtercutoff+"\n");
        }
        if (e.getSource() == zoomfield){
            zoom = Double.parseDouble(zoomfield.getText().trim());
            infopane.append("zoom= "+zoom+"\n");
        }
        if (e.getSource() == outsizefield){
            outsize = Integer.parseInt(outsizefield.getText().trim());
        }
        if (e.getSource() == outdepthlist){
            //outdepth =  (int) ((Integer)outdepthlist.getSelectedItem()).intValue();
            String d = (String)outdepthlist.getSelectedItem();
            outdepth = Integer.parseInt(d);
            IJ.write(Integer.toString(outdepth));
        }
        
        if (e.getSource() == interpbox){
            if (interpbox.isSelected() == true){
                interp = "linear"; } else {
                interp = "nearest";
                }
        }
        if (e.getSource() == filteringbox){
            if ( filteringbox.isSelected() == true){
                filtering = true; } else {
                filtering = false;
                }
        }
        if (e.getSource() == dostackbox){
            if ( dostackbox.isSelected() == true){
                doentirestack  = true;  //projstacksize = projectionstack.length;
            } else {
                doentirestack = false; //projstacksize = 1;
            }
        }
        
        
        
    }
    
    /**initialises the 2D arrays to hold the projection data*/
    public void initialiseprojection(){
        projection = new double[views][scans];
        fprojection = new double[views][scans];
    }
    
    /** Creates a set of projections from the pixel values in a greyscale image
     * This projection data can then be reconstructed using fbp or iterative methods.
     * The forward projection can be done using nearest neighbour,or linear
     * interpolation  */
    
    public double[][] ForwardProject(double[][] pix){
        int i;
        i=0;
        
        double[][] proj = new double[views][scans];
        double pos, val, Aleft, Aright;
        int x, y, Xcenter, Ycenter, Ileft, Iright;
        double[] sintab = new double[views];
        double[] costab = new double[views];
        
        S=0;
        int inputimgsize = pix[0].length;
        //int min = getMin(pix);
        //int max = getMax(pix);
        
        //zero all values in projections array
        blank(proj,0);
        
        for (phi=ang1;phi<ang2;phi=phi+stepsize){
            sintab[i] = Math.sin((double) phi * Math.PI / 180 - Math.PI/2);
            costab[i] = Math.cos((double) phi * Math.PI / 180 - Math.PI/2);
            i++;
        }
        
        // Project each pixel in the image
        Xcenter = inputimgsize / 2;
        Ycenter = inputimgsize / 2;
        i=0;
        //if no. scans is greater than the image width, then scale will be <1
        
        double scale = inputimgsize*1.42/scans;
        IJ.write("Generating projection data from image pixels.. ");
        
        int N=0; val = 0;
        double weight = 0;
        double sang = Math.sqrt(2)/2;
        interrupt = false;
        double progr=0;
        
        for (phi=ang1;phi<ang2;phi=phi+stepsize){
            if (interrupt) break;
            double a = -costab[i]/sintab[i] ;
            double aa = 1/a;
            if (Math.abs(sintab[i]) > sang){
                for (S=0;S<scans;S++){
                    N = S - scans/2; //System.out.print("N="+N+" ");
                    double b = (N - costab[i] - sintab[i]) / sintab[i];
                    b =  b * scale;
                    //IJ.write("b="+b+" ");
                    
                    for (x = -Xcenter; x < Xcenter; x++){
                        if (fast == true){
                            //just use nearest neighbour interpolation
                            y = (int) Math.round(a*x + b);
                            
                            if (y >= -Xcenter && y < Xcenter )
                                val += pix[(x+Xcenter)][(y+Ycenter)];
                            
                        } else {
                            //linear interpolation
                            y = (int) Math.round(a*x + b);
                            weight = Math.abs((a*x + b) - Math.ceil(a*x + b));
                            
                            if (y >= -Xcenter && y+1 < Xcenter )
                                val += (1-weight) * pix[(x+Xcenter)][(y+Ycenter)]
                                        + weight * pix[(x+Xcenter)][(y+Ycenter+1)];
                            
                        }
                    } proj[i][S] = val/Math.abs(sintab[i]); val=0;
                    
                }
            }
            if (Math.abs(sintab[i]) <= sang){
                for (S=0;S<scans;S++){
                    N = S - scans/2;
                    double bb = (N - costab[i] - sintab[i]) / costab[i];
                    bb = bb * scale;
                    //IJ.write("bb="+bb+" ");
                    for (y = -Ycenter; y < Ycenter; y++) {
                        if (fast ==true){
                            x = (int) Math.round(aa*y + bb);
                            if (x >= -Xcenter && x < Xcenter )
                                val += pix[x+Xcenter][y+Ycenter];
                        } else {
                            x = (int) Math.round(aa*y + bb);
                            weight = Math.abs((aa*y + bb) - Math.ceil(aa*y + bb));
                            
                            if (x >= -Xcenter && x+1 < Xcenter )
                                val += (1-weight) * pix[(x+Xcenter)][(y+Ycenter)]
                                        + weight * pix[(x+Xcenter+1)][(y+Ycenter)];
                            
                        }
                    } proj[i][S] = val/Math.abs(costab[i]); val=0;
                    
                }
                
            } i++;
            progr += .0055*(180/views); IJ.showProgress(progr); //update progress bar
        }
        i=0;
        normalize2DArray(proj,0,255);
        progr=0;
        return proj;
    }
    
    /**Performs back projection and returns a 2D array of double pixel values that is used
     * to create the summation image*/
    
    public double[][] BackProject(double[][] proj, int size){
        int i, a;
        i=0;
        double val = 0, pos, Aleft, Aright;
        
        double[][]  bpimage = new double[size][size];
        int x, y, Xcenter, Ycenter, Ileft, Iright;
        double[] sintab = new double[views];
        double[] costab = new double[views];
        double[][] nproj;
        double progr=0;
        S=0;
        
        //filter projections before back projection
        if (filtering == true && method == "fbp"){
            
            fprojection = Filter(proj);
            
        } else{
            fprojection = proj; //no filtering
        }
        
        //create tables of sin and cos values for each angle used
        for (phi=ang1;phi<ang2;phi=phi+stepsize){
            sintab[i] = Math.sin((double) phi * Math.PI / 180);
            costab[i] = Math.cos((double) phi * Math.PI / 180);
            i++;
        }
        
        // Initialize output image to zero
        for (x=0;x<size;x++){
            for (y=0;y<size;y++){
                bpimage[x][y] = 0;
            }
        }
        
        //Back Project each pixel in the image
        Xcenter = size / 2;
        Ycenter = size / 2;
        i=0;
        double scale = zoom*size*1.42/scans;
        IJ.showStatus("Performing back projection.. ");
        int sxoffset = (int) Math.floor(xoffset*size*zoom);
        int syoffset = (int) Math.floor(yoffset*size*zoom);
        //int soffset = (int) Math.floor(offset*scale);
        interrupt = false;
        
        for (x = -Xcenter; x < Xcenter; x++){
            if (interrupt == true) break;
            for (y = -Ycenter; y < Ycenter; y++) {
                int x1 = x - sxoffset;
                int y1 = y - syoffset;
                if (Math.abs(x1) <= Xcenter+Math.abs(sxoffset) &&
                        Math.abs(y1) <= Ycenter+Math.abs(syoffset) ){
                    
                    for (phi=ang1;phi<ang2;phi=phi+stepsize){
                        pos = (x1 * sintab[i] - y1 * costab[i]);
                        //pos = (x1 * costab[i] + y1 * sintab[i]);
                        //System.out.print("pos= "+pos+" ");
                        
                        if (interp == "nearest"){
                            S = (int)Math.round(pos/scale);
                            S = S + scans/2;
                            if (S<scans && S>0)
                                val = val + fprojection[i][S];
                        }
                        //perform linear interpolation
                        else if (interp == "linear"){
                            if (pos>=0){
                                a = (int)Math.floor(pos/scale);
                                int b = a + scans/2;
                                if (b<scans-1 && b>0){
                                    val = val +  fprojection[i][b] + (fprojection[i][b+1]
                                            - fprojection[i][b])
                                            * (pos/scale-(double)a);
                                }
                            } else if (pos<0){
                                a = (int)Math.floor(pos/scale);
                                int b = a + scans/2;
                                if (b<scans-1 && b>0){
                                    val = val + fprojection[i][b] + (fprojection[i][b]
                                            - fprojection[i][b+1])
                                            * (Math.abs(pos/scale) - Math.abs(a));
                                }
                            }
                        }
                        i++;
                    }S=0;i=0;
                    
                    bpimage[x + Xcenter][y + Ycenter] = val/views;
                    //bpimage[x + Xcenter][y + Ycenter] = val*Math.PI/2*views;
                    //img = img*pi/(2*length(theta));
                    
                    val=0;
                }
            }
            progr += .0045*(180/views); IJ.showProgress(progr); //update progress bar
        }
        if (outdepth == 8) {
            normalize2DArray(bpimage,0,255);
        } else {
            normalize2DArray(bpimage,0,4095);
        }
        return bpimage;
        
    }
    
    
    /**filter the projection data before back projection. This is done here in the
     * frequency domain by multiplying the FT of the proj data with a frequency filter
     * and then retrieving the inverse FT of the result  */
    
    public double[][] Filter(double[][] proj){
        
        int i, pscans;
        double filter[], pfilter[];   //array to store filter and padded filter
        
        double[] rawdata;
        double[] idata;
        
        double[][] fproj = new double[views][scans];
        
        //length of array - no of 'scans', should be a power of 2
        //if scans is a power of 2 then just allocated twice this value for arrays and then pad
        //the projection (and filter data?) with zeroes before applying FFT
        if (isPow2(scans) == true){
            pscans = scans;   System.out.println("power of 2");
        }
        //if scans is not a power of 2, then round pscans up to nearest power and double
        else {
            int power = (int)((Math.log(scans) / Math.log(2)))+1; //closest power of 2 rounded up
            pscans = (int) Math.pow(2, power);
            System.out.println("PSCANS: "+pscans);
        }
        rawdata = new double[pscans*2];
        idata = new double[pscans*2];
        pfilter = new double[pscans*2];
        
        for (S = 0; S < pscans*2; S++) {
            idata[S] = 0;
        }
        
        // Initialize the filter
        filter = filter1(filtername, pscans*2, filtercutoff);
        
      /*for (S = 0; S<scans*2; S++) {
          pfilter[S] = filter[S];
      }
      //zero pad filter
      for (S = scans; S<pscans*2; S++) {
          pfilter[S] = 0;
      }*/
        i=0;
        
        // Filter each projection
        for (phi = ang1; phi < ang2; phi+=stepsize) {
            for (S = 0; S<scans; S++) {
                rawdata[S] = proj[i][S];
            }
            //zero pad projections
            for (S = scans; S<pscans*2; S++) {
                rawdata[S] = 0;
            }
            FFT(1, pscans*2, rawdata, idata);
            for (S = 0; S<scans*2; S++) {
                rawdata[S] *= filter[S];
            }
            //perform inverse fourier transform of filtered product
            FFT(0, pscans*2, rawdata, idata);
            for (S = 0; S<scans; S++) {
                fproj[i][S] = rawdata[S];
            }
            for (S = 0; S<pscans*2; S++) {
                idata[S] = 0;
            }
            i++;
        }
        return fproj;
    }
    
    public static double[] filter1(String filtname, int scans, double cutoff){
        int i=0;
        int Width = scans/2;
        double tau = Width*cutoff;
        double exponent;
        double[] filter = new double[scans];
        double PI = Math.PI;
        
        filter[0] = 0;
        if (filtname == "ramp"){
            for (i = 1; i <= Width; i++) {
                filter[scans - i] = filter[i] = (double) PI*i ;
            }
        } else if (filtname == "shepplogan"){
            for (i = 1; i <= Width; i++) {
                filter[scans - i] = filter[i] =  PI*i * ((Math.sin(PI*i/Width/2))/(PI*i/Width/2));
                
            }
        } else if (filtname == "hamming"){
            
            for (i = 1; i <= Width; i++) {
                if (i <= tau){
                    filter[scans - i] = filter[i] =  PI*i * (.54 + (.46 * Math.cos(PI*i/tau)));
                } else filter[scans - i] = filter[i] = 0;
            }
        } else if (filtname == "hann"){
            for (i = 1; i <= Width; i++) {
                if (i <= tau){
                    filter[scans - i] = filter[i] =  PI*i * (1 + (Math.cos(PI*i/tau)));
                } else filter[scans - i] = filter[i] = 0;
            }
        } else if (filtname == "cosine"){
            for (i = 1; i <= Width; i++) {
                if (i <= tau){
                    filter[scans - i] = filter[i] =  PI*i * (Math.cos(PI*i/tau/2));
                } else filter[scans - i] = filter[i] = 0;
            }
        } else if (filtname == "blackman"){
            for (i = 1; i <= Width; i++) {
                if (i <= tau){
                    filter[scans - i] = filter[i] = PI*i * (0.42 + (0.5 * Math.cos(PI*i/tau-1))
                    + (0.08 * Math.cos(2*PI*i/tau-1)));
                } else filter[scans - i] = filter[i] = 0;
            }
        }
        //normalizeData(filter, 1);
        setRange1DArray(filter,0,1);
        return filter;
    }
    
/*
   This computes an in-place complex-to-complex FFT
   x and y are the real and imaginary arrays of 2^m points.
   dir =  1 gives forward transform,  dir = -1 gives reverse transform
 */
    
    public static void FFT(int dir, int s, double[] x,double[] y) {
        int n, i, i1, j, k, i2, l, l1, l2;
        double c1, c2, tx, ty, t1, t2, u1, u2, z;
        int m = (int) (Math.log(s)/Math.log(2));
        double[] spectrum = new double[s];
        
        /* Calculate the number of points */
        n = 1;
        for (i=0;i<m;i++)
            n *= 2;
        
        /* Do the bit reversal */
        i2 = n >> 1;
        j = 0;
        for (i=0;i<n-1;i++) {
            if (i < j) {
                tx = x[i];
                ty = y[i];
                x[i] = x[j];
                y[i] = y[j];
                x[j] = tx;
                y[j] = ty;
            }
            k = i2;
            while (k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }
        
        /* Compute the FFT */
        c1 = -1.0;
        c2 = 0.0;
        l2 = 1;
        for (l=0;l<m;l++) {
            l1 = l2;
            l2 <<= 1;
            u1 = 1.0;
            u2 = 0.0;
            for (j=0;j<l1;j++) {
                for (i=j;i<n;i+=l2) {
                    i1 = i + l1;
                    t1 = u1 * x[i1] - u2 * y[i1];
                    t2 = u1 * y[i1] + u2 * x[i1];
                    x[i1] = x[i] - t1;
                    y[i1] = y[i] - t2;
                    x[i] += t1;
                    y[i] += t2;
                }
                z =  u1 * c1 - u2 * c2;
                u2 = u1 * c2 + u2 * c1;
                u1 = z;
            }
            c2 = Math.sqrt((1.0 - c1) / 2.0);
            if (dir == 1)
                c2 = -c2;
            c1 = Math.sqrt((1.0 + c1) / 2.0);
        }
        
        /* Scaling for forward transform */
        if (dir == 1) {
            for (i=0;i<n;i++) {
                x[i] /= n;
                y[i] /= n;
                
            }
            
        }
        
    }
    
    /**method to blank a 2D array of doubles */
    public static void blank(double data[][], double value) {
        for ( int i = 0; i < data.length; i++ ) {
            for ( int j = 0; j < data[0].length; j++ ) {
                data[i][j] = value;
            }
        }
    }
    
    private static void normalize2DArray(double data[][], double min, double max) {
        
        double datamax = getMax(data);
        IJ.write("Normalizing array; MAX:"+datamax);
        zeronegvals2DArray(data);
        double datamin = 0;
        
        for ( int i = 0; i < data.length; i++ ) {
            for ( int j = 0; j < data[0].length; j++ ) {
                data[i][j] = (double) (((data[i][j]-datamin) * (max))/datamax);
                
            }
        }
    }
    
    private static void zeronegvals2DArray(double data[][]) {
        
        double datamin = getMin(data);
        
        if (datamin < 0 ){
            for ( int i = 0; i < data.length; i++ ) {
                for ( int j = 0; j < data[0].length; j++ ) {
                    if (data[i][j] < 0 ){
                        data[i][j] = 0;
                    }
                }
            }
        }
    }
    
    /**method to find the minimum value in a 2D array of doubles */
    private static double getMin(double data[][]) {
        
        double min = data[0][0];
        for ( int i = 0; i < data.length; i++ ) {
            for ( int j = 0; j < data[0].length; j++ ) {
                if (data[i][j] < min) min = data[i][j] ;
            }//System.out.println(min);
        }
        return min;
    }
    
    /**method to find the maximum value in a 2D array of doubles */
    private static double getMax(double data[][]) {
        
        double max = data[0][0];
        for ( int i = 0; i < data.length; i++ ) {
            for ( int j = 0; j < data[0].length; j++ ) {
                if (data[i][j] > max) max = data[i][j] ;
            } //System.out.println(max);
        }
        return max;
    }
    
    public static boolean isPow2(int value) {
        return (value == (int)roundPow2(value));
    }
    
    public static int pow2(int power) {
        return (1 << power);
    }
    
    public static double roundPow2(double value) {
        double power = (double)(Math.log(value) / Math.log(2));
        int intPower = (int)Math.round(power);
        return (double)(pow2(intPower));
    }
    
    public static double[] setRange1DArray(double data[], int min, int max) {
        
        double[] result = new double [data.length];
        for ( int i = 0; i < result.length; i++ ) {
            if (data[i] > max){
                result[i] = max ;
            } else if (data[i] < min){
                result[i] = min ;
            } else { result [i] = data[i];}
        }
        return result;
    }
    
    /**Saves the projection data as a text file with each line representing
     *a set of projections for each angle*/
    
    public void saveProjectionsFile(String filename){
        
        double[][] array = new double[views][scans];
        /*if (projstacksize>1){
            array = (double[][]) projectionstack[0];
        } else{ */
        array = projection;  //just use most recently stored single set of projeciotn data
        //}
        PrintWriter writer;
        
        try{
            if (filename == null){
                writer = new PrintWriter(new FileWriter("/proj.txt"));
            } else{
                writer = new PrintWriter(new FileWriter(filename));
            }
            writer.println(projstacksize);
            writer.println(stepsize);
            writer.println(array.length);
            writer.println(array[0].length);
            for ( int p = 0; p < projstacksize; p++ ) {
                if (projstacksize>1){
                    array = (double[][]) projectionstack[p];
                }
                for ( int y = 0; y < array[0].length; y++ ) {
                    for ( int x = 0; x < array.length; x++ ) {
                        writer.print( array[x][y]+" ");
                    } writer.println();
                } //writer.println();
                
            }
            writer.close();
        } catch (IOException ioe) {
            System.out.println("I/O Exception in file writing: "+ioe); }
        
    }
    
    
    
    /**loads the projection data from the text file, each line has a projection
     * The first four values are the stacksize, stepsize, views, scans respectively  */
    
    public void loadProjectionsFile(String filename){
        double[][] array;
        float step; int s;int v; int ps=1;
        File file;
        BufferedReader reader;
        //read header
        try{
            if (filename == null){
                file = new File("/proj.txt");
                reader = new BufferedReader(new FileReader(file));
            } else{
                file = new File(filename);
                reader = new BufferedReader(new FileReader(file));
            }
            ps =  Integer.valueOf(reader.readLine()).intValue();
            step = Float.valueOf(reader.readLine()).floatValue();
            v = Integer.valueOf(reader.readLine()).intValue();
            s = Integer.valueOf(reader.readLine()).intValue();
            
            System.out.println("projstacksize="+ps);
            System.out.println("v="+v);System.out.println("s="+s);
            reader.close();
        } catch (IOException ioe) { System.out.println("I/O Exception "+ioe);
        s=0;v=0;step=0;}
        array = new double[v][s];
        projstacksize = ps;
        views = v;
        scans = s;
        stepsize = step;
        if (ps > 1) { projectionstack = new Object[ps]; }
        try{
            if (filename == null){
                file = new File("/proj.txt");
                reader = new BufferedReader(new FileReader(file));
            } else{
                file = new File(filename);
                reader = new BufferedReader(new FileReader(file));
            }
            reader.readLine();reader.readLine();reader.readLine();reader.readLine(); //skip over header
            // process the entire file, of space or comma-delimited data
            String aLine = new String();
            for ( int p = 0; p < ps; p++ ) {
                for ( int y = 0; y < array[0].length; y++ ) {
                    aLine = reader.readLine();
                    StringTokenizer st = new StringTokenizer(aLine);
                    for ( int x = 0; x < array.length; x++ ) {
                        String str = st.nextToken();
                        array[x][y] =  Double.parseDouble(str);
                    }
                } //reader.readLine();
                if (ps > 1){
                    projectionstack[p] = array;
                }
                array = new double[v][s];
            }
            reader.close();
        } catch (IOException ioe) { System.out.println("I/O Exception "+ioe); }
        initialiseprojection();
        projection = array;
    }
    
    /**imports the projection data from an image sinogram, results may be noisy */
    
    public void importProjections(ImagePlus inputimg, int ps){
        
        int s;int v; //int ps=1;
        short[] sarray; byte[] barray;
        
        CustomDialog importdialog = new CustomDialog(this, true, "Data bins in cols or rows?");
        incols = importdialog.getAnswer();

        type = imp.getType();
        projstacksize = ps;
        //infopane.append("stacksize= "+ps+"\n");
        if (ps > 1) { projectionstack = new Object[ps]; }
        
        ImageProcessor inputip  = inputimg.getProcessor();
        if (incols == true){
            v = inputip.getWidth(); 
            s = inputip.getHeight();
        } else{
            s = inputip.getWidth(); 
            v = inputip.getHeight();
        }
        
        infopane.append("Importing projection data as image "+"\n");
        views = v;
        scans = s;
        stepsize = 180/v;
        infopane.append("Scans= "+s+" "); infopane.append("Views= "+v+"\n");
        infopane.append("Stepsize= "+stepsize+"\n ");
        
        //these are defined in the case of a stack of images as input.
        ImageStack inputstack = imp.getImageStack();
        ImageStack sinogramstack = new ImageStack(views,scans);
        
        for ( int p = 0; p < ps; p++ ) {
            
            if (cancelled == true) break;
            projection = new double[v][s];
            
            //now put pixel values from image into the projection data array
            if (imp.getType() == ImagePlus.GRAY16){
                if (ps > 1){
                    infopane.append("data "+p+"\n");
                    inputip = (ShortProcessor) inputstack.getProcessor(p+1);
                } else {
                    inputip= (ShortProcessor) inputimg.getProcessor();
                }
                sarray = (short[]) inputip.getPixels();
                if (incols == true){
                    System.out.println("incols"); infopane.append("incols "+p+"\n");
                    for (int x = 0; x < projection[0].length; x++ ) {
                        for (int y = 0; y < projection.length; y++ ) {
                            projection[y][x] = sarray[y + x * projection.length] &0xFFFF;
                        }
                    }
                } else{
                    System.out.println("inrows"); infopane.append("inrows "+p+"\n");
                    for (int x = 0; x < projection[0].length; x++ ) {
                        for (int y = 0; y < projection.length; y++ ) {
                            projection[y][x] = sarray[x + y * projection[0].length] &0xFFFF;
                        } 
                    }
                }
                
            } else{
                if (ps > 1){
                    infopane.append("data "+p+"\n");
                    inputip = (ByteProcessor) inputstack.getProcessor(p+1);
                } else {
                    inputip= (ByteProcessor) inputimg.getProcessor();
                }
                barray = (byte[]) inputip.getPixels();
                if (incols == true){
                    for (int x = 0; x < projection[0].length; x++ ) {
                        for (int y = 0; y < projection.length; y++ ) {
                            projection[y][x] = barray[y + x * projection.length] &0xFF;
                        }
                    }
                } else{
                    for (int x = 0; x < projection[0].length; x++ ) {
                        for (int y = 0; y < projection.length; y++ ) {
                            projection[y][x] = barray[x + y * projection[0].length] &0xFF;
                        }
                    }
                }

            }
            if (ps > 1){
                projectionstack[p] = projection;
                ImageProcessor sino = createSinogram(false);
                sinogramstack.addSlice(Integer.toString(p+1),sino);
            }
        }
        if (ps > 1){
            ImagePlus sinoimages = new ImagePlus("Proj Stack", sinogramstack);
            sinoimages.show();
            dostackbox.setSelected(true); doentirestack = true;
        }
        
    }
    
}

