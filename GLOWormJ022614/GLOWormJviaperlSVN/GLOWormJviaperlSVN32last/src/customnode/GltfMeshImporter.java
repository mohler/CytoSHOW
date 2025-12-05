package customnode;

import ij.IJ;
import ij3d.Image3DUniverse;
import ij3d.Content;
import ij3d.ContentCreator;
import ij3d.ContentInstant;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;

public class GltfMeshImporter {

    /**
     * MAIN ENTRY POINT
     */
    public static void loadAndShowGltfMeshes(File glbFile, Image3DUniverse univ) {
        if (!glbFile.exists() || !(glbFile.getName().toLowerCase().endsWith(".glb") || glbFile.getName().toLowerCase().endsWith(".gltf"))) return;

        // 1. Prepare Output Directory
        String safeName = glbFile.getName().replace(".", "_") + "_parts";
        File outputDir = new File(glbFile.getParentFile(), safeName);
        if (!outputDir.exists() && !outputDir.mkdir()) {
            IJ.error("Could not create output dir: " + outputDir);
            return;
        }

        IJ.showStatus("Processing Draco GLB...");

        try {
            // 2. Extract Decoder from correct resource folder
            File decoder = extractDecoder();

            // 3. Convert (Generates _0.obj files)
            new GlbToObjConverter().convert(glbFile, outputDir, decoder);

            // 5. Load Results
            loadMeshes(outputDir, IJ.getInstance().getDragAndDrop().getDropUniverse());
            
            IJ.showStatus("Import Complete.");

        } catch (Exception e) {
            IJ.handleException(e);
        }
    }

    private static void loadMeshes(File dir, Image3DUniverse univ) {
        String[] objPaths = dir.list((d, n) -> n.toLowerCase().endsWith(".obj"));
        if (objPaths == null || objPaths.length < 1) return;
        for (int c=0; c<objPaths.length; c++) {
        	objPaths[c] = dir+File.separator+objPaths[c];
        }
        	
        if (univ == null) {
        	univ = new Image3DUniverse();
        	IJ.getInstance().getDragAndDrop().setDropUniverse(univ);
        }
        
        try {
        	univ.addContentLater(objPaths, null, false);
        } catch (Exception e) {
        	IJ.log("Error loading: " + dir.getAbsolutePath());
        }

        long waitCount=0;
        while (univ.getContents().size() < objPaths.length && waitCount<100) {
        	try {
				Thread.sleep(100);
				waitCount++;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        if (univ.getWindow() == null) {
        	univ.show(false);
        	univ.resetView();
        }
    }

    private static File extractDecoder() throws IOException {
        // Determine path based on OS/Arch matches your Project Structure
        String resourcePath = getPlatformSpecificBinaryPath("draco_decoder");
        
        InputStream in = GltfMeshImporter.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new FileNotFoundException("Decoder not found at: " + resourcePath + 
                "\nMake sure the binary is in the correct bin.os.arch package.");
        }

        String ext = resourcePath.endsWith(".exe") ? ".exe" : "";
        File temp = File.createTempFile("draco", ext);
        temp.deleteOnExit();
        
        Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        if (!temp.getName().endsWith(".exe")) {
            temp.setExecutable(true);
        }
        
        return temp;
    }

    /**
     * Maps the current OS/Arch to your specific Eclipse package structure.
     * Structure:
     * - /bin/mac/intel/
     * - /bin/mac/arm64/
     * - /bin/win/
     * - /bin/linux/
     */
    private static String getPlatformSpecificBinaryPath(String baseName) {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        String pathPrefix = "/bin"; // Root of the package structure

        if (os.contains("mac")) {
            pathPrefix += "/mac";
            // Check for Apple Silicon (M1/M2/M3)
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                pathPrefix += "/arm64";
            } else {
                pathPrefix += "/intel";
            }
        } else if (os.contains("win")) {
            pathPrefix += "/win";
            baseName += ".exe";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            pathPrefix += "/linux";
        } else {
            // Fallback or Unknown
            IJ.log("Unknown OS: " + os + ". Trying default path.");
        }

        return pathPrefix + "/" + baseName;
    }
}