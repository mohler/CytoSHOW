package ij.plugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor; // Swapped Short for Byte
import ij.process.ImageProcessor;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.ParticleAnalyzer;

public class ColorMaskSorterExtractor implements PlugIn {

    // Storage for the Vector "DNA" for each unique raw color 'c'
    // MERGED: Kept your structure (ArrayList<float[]>)
    HashMap<Integer, ArrayList<float[]>> colorRatioMap = new HashMap<Integer, ArrayList<float[]>>();

    // Storage for the final grouped colors (Clusters)
    // Each inner ArrayList contains the 'c' values belonging to that group
    ArrayList<ArrayList<Integer>> colorGroups = new ArrayList<>();
    
    // The reference vector (Signature) for each group
    ArrayList<float[]> groupSignatures = new ArrayList<>();
    
    ImagePlus imp, clonedImp;

    public ColorMaskSorterExtractor() {
    }

    @Override
    public void run(String arg) {
        imp = IJ.getImage();
        if (imp == null) {
            IJ.noImage();
            return;
        }

        if (imp.getType() != ImagePlus.COLOR_RGB) {
            IJ.error("This plugin requires an RGB image.");
            return;
        }

        // 1. Create the destination image (Masks)
        clonedImp = IJ.createImage(imp.getTitle() + "_Masks", imp.getWidth()+2, imp.getHeight()+2, imp.getImageStackSize(), 8);

        // Clear ROI Manager to start fresh using CytoSHOW API
        RoiManager rm = imp.getRoiManager();
        if (rm == null) rm = new RoiManager(); // Fallback if null, though CytoSHOW usually provides it
        
        // 2. Execute the Logic safely
        extractAndGroup(imp, clonedImp);
    }

    public void extractAndGroup(ImagePlus imp, ImagePlus clonedImp) {
        
        // Sensitivity threshold for Grey Detection 
        final int GREY_THRESHOLD = 2; 

        if (imp.getStack() != null) {
            for (int z = 1; z <= imp.getStackSize(); z++) {
                imp.setPosition(z);
                
                // Get the processor for the CURRENT slice safely
                ImageProcessor ip = imp.getProcessor();
                int[] pixels = ((int[]) ip.getPixels());

                for (int p = 0; p < pixels.length; p++) {
                    int c = pixels[p];
                    
                    if (colorRatioMap.containsKey(c)) {
                        continue;
                    }

                    int pixRed = (c >> 16) & 0xff;
                    int pixGreen = (c >> 8) & 0xff;
                    int pixBlue = c & 0xff;

                    int diffRG = Math.abs(pixRed - pixGreen);
                    int diffGB = Math.abs(pixGreen - pixBlue);
                    int diffBR = Math.abs(pixBlue - pixRed);

                    if (diffRG < GREY_THRESHOLD && diffGB < GREY_THRESHOLD && diffBR < GREY_THRESHOLD) {
                        // no action, grayscale pixel
                    } else {
                        // --- VECTOR CALCULATION ---
                        float total = pixRed + pixGreen + pixBlue;

                        if (total > 0) {
                            float normR = pixRed / total;
                            float normG = pixGreen / total;
                            float normB = pixBlue / total;

                            if (colorRatioMap.containsKey(c)) {
                                colorRatioMap.get(c).add(new float[]{normR, normG, normB, p/ip.getWidth(), p%ip.getWidth(), z});
                            } else {
                                ArrayList<float[]> nal = new ArrayList<float[]>();
                                nal.add(new float[]{normR, normG, normB, p/ip.getWidth(), p%ip.getWidth(), z});
                                colorRatioMap.put(c, nal);
                            }
                        }
                    }
                }
            }
        }
        
        IJ.log("Extraction Complete. Found " + colorRatioMap.size() + " unique non-grey color values.");
        
        // 3. Group the vectors
        groupVectors();

        // 4. Generate ROIs (Brute Force Loop)
        generateROIs(imp, clonedImp);
    }

    public void groupVectors() {
        double groupingTolerance = 0.25; 
        
        IJ.log("Grouping vectors with tolerance: " + groupingTolerance + "...");

        for (Map.Entry<Integer, ArrayList<float[]>> entry : colorRatioMap.entrySet()) {
            Integer c = entry.getKey();
            
            for (float[] currentVec: entry.getValue()) {
                boolean matched = false;

                for (int i = 0; i < groupSignatures.size(); i++) {
                    float[] refVec = groupSignatures.get(i);

                    if (isVectorSimilar(currentVec, refVec, groupingTolerance)) {
                        colorGroups.get(i).add(c);
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    ArrayList<Integer> newGroup = new ArrayList<>();
                    newGroup.add(c);
                    colorGroups.add(newGroup);
                    groupSignatures.add(currentVec);
                }
            }
        }

        IJ.log("Grouping Complete. Created " + colorGroups.size() + " distinct color masks.");
    }

 // --- UPDATED METHOD: Temporary Full Stack Approach ---
    public void generateROIs(ImagePlus imp, ImagePlus clonedImp) {
        IJ.log("Generating ROIs (Full Stack Construction)...");
        
        clonedImp.show();

        // 1. Get the CytoSHOW ROI Manager
        RoiManager clonedRM = clonedImp.getRoiManager();
        if (clonedRM == null) {
        	clonedRM = new RoiManager(); 
            IJ.log("Note: Created new RoiManager (clonedRM.getRoiManager() was null)");
        }
        clonedRM.setVisible(true);


             // 2. Iterate through each identified GROUP
        for (int i = 0; i < colorGroups.size(); i++) {
            ArrayList<Integer> groupColors = colorGroups.get(i);
            

            boolean hasData = false;

            // B. Plot pixels from Memory into the Stack
            for (Integer c : groupColors) {
                ArrayList<float[]> pixelDataList = colorRatioMap.get(c);
                if (pixelDataList == null) continue;

                for (float[] data : pixelDataList) {
                    // [NormR, NormG, NormB, Y(p/w), X(p%w), Z]
                    int y = (int) data[3];
                    int x = (int) data[4];
                    int z = (int) data[5];

                    // Z is 1-based in ImageJ, data might be 1-based from extraction?
                    // Let's verify: In extractAndGroup, loop is 'for (int z = 1; ...)'
                    // So data[5] is 1-based. Correct.
                    
                    ImageProcessor ip = clonedImp.getStack().getProcessor(z);
                    ip.set(x, y, 255);
                    hasData = true;
                }
            }

            if (!hasData) continue;

            // C. Run Particle Analyzer on the Stack
            // Note: processStack=true (implied by running on a stack usually, but we config PA)
            
            // Set Threshold globally for the stack
            clonedImp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
            
            int preCount = clonedRM.getCount();
            
            ParticleAnalyzer pa = new ParticleAnalyzer(
                ParticleAnalyzer.ADD_TO_MANAGER, 
                0, null, 100, Double.POSITIVE_INFINITY, 0.0, 1.0
            );
            
            // This runs on the current slice by default. To run on all, we need to loop 
            // OR use the ParticleAnalyzer "Process Stack" logic. 
            // The safest, explicit way that guarantees ROIs get correct Z is to loop the stack manually here.
            
            for (int z = 1; z <= clonedImp.getStackSize(); z++) {
                clonedImp.setPosition(z);
                // We must set the threshold on the CURRENT processor for PA to see it
                clonedImp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
                
                if (pa.analyze(clonedImp)) {
                    // analyze returns false if user cancels, true otherwise
                }
            }
            
            int postCount = clonedRM.getCount();
            if (postCount > preCount) {
                IJ.log("Group " + i + ": Generated " + (postCount - preCount) + " ROIs.");
            }
            
        }

        IJ.log("ROI Generation Complete. Total ROIs: " + clonedRM.getCount());
    } 
    
    private boolean isVectorSimilar(float[] v1, float[] v2, double tol) {
        double distSq = Math.pow(v1[0] - v2[0], 2) +
                        Math.pow(v1[1] - v2[1], 2) +
                        Math.pow(v1[2] - v2[2], 2);

        return distSq < (tol * tol);
    }
}