package ij.plugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ImageProcessor;

public class ColorMaskSorterExtractor implements PlugIn {

    // --- INNER CLASS: The "Spoke" Definition ---
    // Represents a unique color direction (Hue/Tint) discovered in the image
    private static class Spoke {
        int id;
        double dx, dy, dz; // Normalized Unit Vector (Direction)
        double maxMagnitude; // The "furthest" point observed (Saturation limit)
        int pixelCount;

        public Spoke(int id, double dx, double dy, double dz) {
            this.id = id;
            this.dx = dx; this.dy = dy; this.dz = dz;
            this.maxMagnitude = 0;
            this.pixelCount = 0;
        }

        // Adds a pixel's observation to this spoke (updating max saturation)
        public void registerObservation(double mag) {
            if (mag > maxMagnitude) maxMagnitude = mag;
            pixelCount++;
        }
    }

    // Map of Raw RGB Color -> Assigned Spoke ID
    // (This acts as our Lookup Table so we don't recalculate vectors for every pixel)
    private HashMap<Integer, Integer> colorToSpokeMap = new HashMap<>();
    
    // The list of discovered Spokes (Chartreuse, Hunter, Blue, etc.)
    private ArrayList<Spoke> spokes = new ArrayList<>();

    ImagePlus imp, clonedImp;

    // --- CONFIGURATION ---
    // Minimum vector length to be considered "Color" (vs Gray Noise)
    // 15.0 is a safe "Forensic" threshold for 8-bit images
    private static final double NOISE_THRESHOLD = 15.0; 
    
    // How strictly vectors must align to group together (Cosine Similarity)
    // 0.95 = ~18 degrees. 0.98 = ~11 degrees. 
    // Higher = More specific buckets (Chartreuse vs Forest).
    private static final double SIMILARITY_TOLERANCE = 0.96; 

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

        // 1. Create the destination image (Masks) - Same size as source
        // We use 8-bit (Byte) because we only need 0 or 255 for masks.
        clonedImp = IJ.createImage(imp.getTitle() + "_Masks", imp.getWidth(), imp.getHeight(), imp.getImageStackSize(), 8);

        // 2. Execute the Logic
        extractAndGroup(imp, clonedImp);
    }

    public void extractAndGroup(ImagePlus imp, ImagePlus clonedImp) {
        IJ.log("--- Starting Forensic Vector Analysis ---");
        
        // PHASE 1: DISCOVERY
        // Scan the image to find unique "Spokes" (Vectors)
        discoverSpokes(imp);
        
        // PHASE 2: GENERATION
        // Loop through each discovered Spoke, create a mask, and generate ROIs
        generateROIs(imp, clonedImp);
    }

    /**
     * Phase 1: Iterates the image to find all unique color vectors.
     * Clusters them into "Spokes" based on SIMILARITY_TOLERANCE.
     */
    private void discoverSpokes(ImagePlus imp) {
        spokes.clear();
        colorToSpokeMap.clear();

        IJ.log("Phase 1: Discovering Color Vectors...");
        
        int width = imp.getWidth();
        int height = imp.getHeight();
        int stackSize = imp.getStackSize();

        // 1. Iterate over the entire stack
        for (int z = 1; z <= stackSize; z++) {
            imp.setPosition(z);
            ImageProcessor ip = imp.getProcessor();
            int[] pixels = (int[]) ip.getPixels();

            for (int p = 0; p < pixels.length; p++) {
                int c = pixels[p];
                
                // If we've already classified this color, skip math
                if (colorToSpokeMap.containsKey(c)) continue;

                // --- VECTOR EXTRACTION ---
                int r = (c >> 16) & 0xff;
                int g = (c >> 8) & 0xff;
                int b = c & 0xff;

                // Calculate Gray Baseline (The Origin)
                double grayAvg = (r + g + b) / 3.0;
                
                // Calculate Deviation Vector (The "Chroma")
                double vr = r - grayAvg;
                double vg = g - grayAvg;
                double vb = b - grayAvg;

                // Calculate Magnitude (Euclidean Length)
                double magnitude = Math.sqrt(vr*vr + vg*vg + vb*vb);

                // --- GREY NOISE FILTER ---
                if (magnitude < NOISE_THRESHOLD) {
                    colorToSpokeMap.put(c, -1); // -1 = Noise/Background
                    continue;
                }

                // Normalize to Unit Vector
                double nx = vr / magnitude;
                double ny = vg / magnitude;
                double nz = vb / magnitude;

                // --- CLUSTERING (The "Hub & Spoke" Logic) ---
                int matchedSpokeID = -1;
                double bestScore = -1.0;

                // Check against existing spokes
                for (Spoke s : spokes) {
                    // Dot Product: Cosine Similarity
                    double score = (nx * s.dx) + (ny * s.dy) + (nz * s.dz);
                    
                    if (score > bestScore) {
                        bestScore = score;
                        if (score >= SIMILARITY_TOLERANCE) {
                            matchedSpokeID = s.id;
                        }
                    }
                }

                if (matchedSpokeID != -1) {
                    // Found a match! Assign this color to that Spoke
                    colorToSpokeMap.put(c, matchedSpokeID);
                    spokes.get(matchedSpokeID).registerObservation(magnitude);
                } else {
                    // New Vector Discovered! Create a new Spoke
                    int newID = spokes.size();
                    Spoke newSpoke = new Spoke(newID, nx, ny, nz);
                    newSpoke.registerObservation(magnitude);
                    spokes.add(newSpoke);
                    colorToSpokeMap.put(c, newID);
                }
            }
        }
        
        IJ.log("Discovery Complete. Found " + spokes.size() + " distinct Color Spokes.");
        for(Spoke s : spokes) {
            IJ.log(String.format("Spoke %d: MaxSat=%.1f, Pixels=%d, Dir=[%.2f, %.2f, %.2f]", 
                   s.id, s.maxMagnitude, s.pixelCount, s.dx, s.dy, s.dz));
        }
    }

    /**
     * Phase 2: For each discovered Spoke, we sweep the image ONE time,
     * projecting the pixels onto the mask, and running Particle Analyzer.
     */
    public void generateROIs(ImagePlus imp, ImagePlus clonedImp) {
        IJ.log("Phase 2: Generating ROIs...");

        // FIX 1: DO NOT SHOW THE IMAGE YET!
        // clonedImp.show(); <--- DELETE THIS. Keeping it hidden prevents AWT interference.
        
        RoiManager rm = clonedImp.getRoiManager();
        if (rm == null) rm = new RoiManager();
        clonedImp.setRoiManager(rm);
        
        // FIX 2: Hide the Manager too. It speeds up adding ROIs by 10x.
        rm.setVisible(false); 

        int width = imp.getWidth();
        int height = imp.getHeight();
        int stackSize = imp.getStackSize();

        // Iterate through each Spoke (Group)
        for (Spoke s : spokes) {
            
            // A. CLEAR the Mask Image (Reset to Black)
            for (int z = 1; z <= stackSize; z++) {
                clonedImp.setSlice(z);
                ImageProcessor ipMask = clonedImp.getProcessor();
                ipMask.setColor(0);
                ipMask.fill(); 
            }

            // B. PAINT the Mask for Current Spoke
            boolean hasData = false;
            
            for (int z = 1; z <= stackSize; z++) {
                imp.setPosition(z);
                clonedImp.setPosition(z); 

                int[] srcPixels = (int[]) imp.getProcessor().getPixels();
                byte[] maskPixels = (byte[]) clonedImp.getProcessor().getPixels();

                for (int i = 0; i < srcPixels.length; i++) {
                    Integer c = srcPixels[i];
                    Integer spokeID = colorToSpokeMap.get(c);

                    if (spokeID != null && spokeID == s.id) {
                        maskPixels[i] = (byte) 255; 
                        hasData = true;
                    }
                }
            }

            if (!hasData) continue;

            // FIX 3: REMOVE LOGGING INSIDE THE LOOP
            // The deadlock was triggered by IJ.log() fighting with the UI. 
            // We silence this to prevent the "Dining Philosophers" crash.
            // IJ.log("Analyzing Spoke " + s.id + "..."); <--- COMMENT OUT
            
            int preCount = rm.getCount();
            
            ParticleAnalyzer pa = new ParticleAnalyzer(
                ParticleAnalyzer.ADD_TO_MANAGER, 
                0, null, 100, Double.POSITIVE_INFINITY, 0.0, 1.0
            );

            for (int z = 1; z <= stackSize; z++) {
                clonedImp.setPosition(z);
                clonedImp.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE);
                pa.analyze(clonedImp);
            }
            
            int postCount = rm.getCount();
            // Optional: Only log if significant to keep the console quiet
            if (postCount - preCount > 0) {
                 // IJ.log("Spoke " + s.id + ": " + (postCount - preCount) + " ROIs.");
            }
        }

        // FIX 4: NOW we show everything safely
        clonedImp.show(); 
        rm.setVisible(true);
        IJ.log("Analysis Complete. Total ROIs: " + rm.getCount());
    }
}