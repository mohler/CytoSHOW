package ij.plugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.IntStream;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.plugin.filter.ParticleAnalyzer;
import ij.process.ImageProcessor;

public class ColorMaskSorterExtractor implements PlugIn {

    // --- INNER CLASS: The "Spoke" Definition ---
    // Represents a unique color direction (Hue/Tint) discovered in the image
    private static class Spoke {
        int id;
        double dx, dy, dz; 
        double maxMagnitude; 
        int pixelCount;

        public Spoke(int id, double dx, double dy, double dz) {
            this.id = id;
            this.dx = dx; this.dy = dy; this.dz = dz;
            this.maxMagnitude = 0;
            this.pixelCount = 0;
        }

        public void registerObservation(double mag) {
            if (mag > maxMagnitude) maxMagnitude = mag;
            pixelCount++;
        }

        /**
         * Returns the "Key Color" for this spoke.
         * Logic: Projects the vector from Mid-Gray (127.5) to the RGB cube limits.
         * This preserves negative vectors (e.g. Cyan = Negative Red).
         */
        public Color getKeyColor() {
            // 1. Find the component with the largest absolute deviation from neutral
            double maxDev = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            
            // 2. Calculate scaling factor to push that component to the edge (0 or 255)
            // We start from Gray (127.5) and extend the vector.
            double scale = 127.5 / maxDev;
            
            // 3. Project and Round
            int r = (int) Math.round(127.5 + (dx * scale));
            int g = (int) Math.round(127.5 + (dy * scale));
            int b = (int) Math.round(127.5 + (dz * scale));
            
            // Clamp to be safe (0-255)
            return new Color(clamp(r), clamp(g), clamp(b));
        }
        
        private int clamp(int val) {
            return Math.max(0, Math.min(255, val));
        }
        
        public String getHexString() {
            Color c = getKeyColor();
            return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }
    }
    
    // Map of Raw RGB Color -> Assigned Spoke ID
    // Acts as our Lookup Table so we don't recalculate vectors for every pixel
    private HashMap<Integer, Integer> colorToSpokeMap = new HashMap<>();
    
    // The list of discovered Spokes (Chartreuse, Hunter, Blue, etc.)
    private ArrayList<Spoke> spokes = new ArrayList<>();

    ImagePlus imp, clonedImp;

    // --- CONFIGURATION ---
    // Minimum vector length to be considered "Color" (vs Gray Noise)
    private static final double NOISE_THRESHOLD = 15.0; 
    
    // How strictly vectors must align to group together (Cosine Similarity).
    // 0.96 allows ~16 degrees variance. 
    // If using subtle shifts (1.15x boost), this must be tight enough to separate them.
    private static final double SIMILARITY_TOLERANCE = 0.99; // Tightened to 0.98 for sensitivity

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
        // Parallel Super-Paint followed by fast threshold extraction
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
        
        int stackSize = imp.getStackSize();

        // Iterate over the entire stack
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
            IJ.log(String.format("Spoke %d: Key=%s, Pixels=%d", 
                   s.id, s.getHexString(), s.pixelCount));
        }
    }

    /**
     * Phase 2: High-Speed Single Pass Generation.
     * Uses parallel streams to create a Label Map, then extracts ROIs via thresholding.
     */
    public void generateROIs(ImagePlus imp, ImagePlus clonedImp) {
        IJ.log("Phase 2: Generating ROIs (High-Speed Single Pass)...");

        RoiManager clonedRM = clonedImp.getRoiManager();
        if (clonedRM == null) clonedRM = new RoiManager();
        clonedImp.setRoiManager(clonedRM);
        clonedRM = clonedImp.getRoiManager();
        clonedRM.setVisible(false); // Speed up

        int stackSize = imp.getStackSize();

        // --- STEP 1: PARALLEL "SUPER-PAINT" (The One Pass) ---
        // Pixel Value = (SpokeID + 1). Background = 0.
        
        IJ.log("Constructing Label Map (Parallel)...");
        
        IntStream.rangeClosed(1, stackSize).parallel().forEach(z -> {
            // Fetch processors directly from stack to be thread-safe
            ImageProcessor ipSrc = imp.getStack().getProcessor(z);
            ImageProcessor ipMask = clonedImp.getStack().getProcessor(z);
            
            int[] srcPixels = (int[]) ipSrc.getPixels();
            byte[] maskPixels = (byte[]) ipMask.getPixels();
            
            for (int i = 0; i < srcPixels.length; i++) {
                Integer c = srcPixels[i];
                Integer spokeID = colorToSpokeMap.get(c);
                
                // Note: spokeID is -1 for noise. 
                // (-1 + 1) = 0, which correctly paints Background.
                if (spokeID != null) {
                    maskPixels[i] = (byte) (spokeID + 1);
                } else {
                    maskPixels[i] = (byte) 0;
                }
            }
        });

        // --- STEP 2: FAST EXTRACTION (The Threshold Switch) ---
        
        for (Spoke s : spokes) {
            
            int targetValue = s.id + 1; // The pixel value we are hunting for
            int preCount = clonedRM.getCount();
            
            ParticleAnalyzer pa = new ParticleAnalyzer(
                ParticleAnalyzer.ADD_TO_MANAGER, 
                0, null, 100, Double.POSITIVE_INFINITY, 0.0, 1.0
            );

            // Loop slices for PA to ensure correct Z-positioning of ROIs
            for (int z = 1; z <= stackSize; z++) {
                clonedImp.setPosition(z);
                
                // THE TRICK: We isolate the spoke by thresholding ONLY its value.
                clonedImp.getProcessor().setThreshold(targetValue, targetValue, ImageProcessor.NO_LUT_UPDATE);
                pa.analyze(clonedImp);
            }
            
            int postCount = clonedRM.getCount();
            
            if (postCount > preCount) {
                // --- NAMING & COLORING ---
                Color keyColor = s.getKeyColor();
                String keyHex = s.getHexString();
                
                int[] ayes = new int [postCount-preCount];
                String[] names = new String[postCount-preCount];
                
                for (int i = preCount; i < postCount; i++) {
                    ayes[i-preCount] = i;
                    names[i-preCount] = "Area-" + keyHex; 
                }
                
                clonedRM.rename(names, ayes, false);
                clonedRM.setSelectedIndexes(ayes);
                
                for (Roi next : clonedRM.getSelectedRoisAsArray()) {
                    clonedRM.setRoiFillColor(next, keyColor);
                }
            }
        }

        clonedImp.show(); 
        clonedRM.setVisible(true);
        IJ.log("Analysis Complete. Total ROIs: " + clonedRM.getCount());
    }

    /**
     * MACRO ADAPTER: Allows this function to be called from ImageJ Macro Language.
     * Usage: call("ij.plugin.ColorMaskSorterExtractor.apply6ZoneShiftMacro", "1");
     */
    public static String apply6ZoneShiftMacro(String zoneIDStr) {
        // 1. Grab the active image (Macro style)
        ImagePlus imp = IJ.getImage();
        if (imp == null) return "Error: No image open";
        
        // 2. Grab the active ROI
        Roi roi = imp.getRoi();
        if (roi == null) return "Error: No selection";
        
        // 3. Parse the ID and run
        try {
            int zoneID = Integer.parseInt(zoneIDStr);
            apply6ZoneShift(imp, roi, zoneID); // Call the main logic
            return "Success: Applied Zone " + zoneID;
        } catch (NumberFormatException e) {
            return "Error: Zone ID must be a number (1-6)";
        }
    }
    
    /**
     * POST-HOC UTILITY: Applies a "Cyclic Crosstalk" shift to create distinguishable clones.
     * This forces a vector rotation (Hue Shift) that the Extractor can detect.
     * @param zoneID 1=Red->Magenta, 2=Green->Yellow, 3=Blue->Cyan, etc.
     */
    public static void apply6ZoneShift(ImagePlus imp, Roi zoneRoi, int zoneID) {
        
        java.awt.Rectangle bounds = zoneRoi.getBounds();
        int stackSize = imp.getStackSize();
        
        // The "Kick": 0.25 (25%) bleed is required to safely clear the 0.99 tolerance.
        // This rotates the vector by approx 14 degrees.
        double kick = 0.25; 

        // Iterate the ENTIRE Stack (Drill Down)
        for (int z = 1; z <= stackSize; z++) {
            
            // Fetch processor directly from stack (Thread-safe/Fast)
            ImageProcessor ip = imp.getStack().getProcessor(z);
            
            for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
                for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                    
                    if (!zoneRoi.contains(x, y)) continue;

                    int c = ip.getPixel(x, y);
                    int r = (c >> 16) & 0xff;
                    int g = (c >> 8) & 0xff;
                    int b = c & 0xff;

                    // Safety: Skip gray/black background to prevent "tinted boxes"
                    double avg = (r + g + b) / 3.0;
                    if (Math.abs(r - avg) < 5.0 && Math.abs(g - avg) < 5.0 && Math.abs(b - avg) < 5.0) {
                        continue;
                    }

                    int newR = r, newG = g, newB = b;

                    switch (zoneID) {
                        // --- PRIMARY ROTATIONS ---
                        case 1: // Rotates RED -> MAGENTA (Red bleeds into Blue)
                            newB = clamp(b + (int)(r * kick)); 
                            break;
                            
                        case 2: // Rotates GREEN -> YELLOW (Green bleeds into Red)
                            newR = clamp(r + (int)(g * kick)); 
                            break;
                            
                        case 3: // Rotates BLUE -> CYAN (Blue bleeds into Green)
                            newG = clamp(g + (int)(b * kick)); 
                            break;
            
                        // --- REVERSE ROTATIONS ---
                        case 4: // Rotates RED -> YELLOW (Red bleeds into Green)
                            newG = clamp(g + (int)(r * kick));
                            break;
                            
                        case 5: // Rotates GREEN -> CYAN (Green bleeds into Blue)
                            newB = clamp(b + (int)(g * kick));
                            break;
                            
                        case 6: // Rotates BLUE -> MAGENTA (Blue bleeds into Red)
                            newR = clamp(r + (int)(b * kick));
                            break;
                    }

                    ip.putPixel(x, y, (newR << 16) | (newG << 8) | newB);
                }
            }
        }
        
        imp.updateAndDraw();
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}