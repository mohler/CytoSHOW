package customnode;

import ij.IJ;
import ij3d.Image3DUniverse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import javax.swing.SwingUtilities;

public class GltfMeshImporter {

    /**
     * MAIN ENTRY POINT
     * Decodes GLB/GLTF in memory and adds directly to the Universe.
     */
    public static void loadAndShowGltfMeshes(File glbFile, Image3DUniverse univ) {
        if (!glbFile.exists() || !(glbFile.getName().toLowerCase().endsWith(".glb") || glbFile.getName().toLowerCase().endsWith(".gltf"))) return;

        // Ensure we have a universe
        final Image3DUniverse finalUniv = (univ == null) ? new Image3DUniverse() : univ;
        if (univ == null) {
             IJ.getInstance().getDragAndDrop().setDropUniverse(finalUniv);
             finalUniv.show(false);
        }

        IJ.log("Streaming Draco GLB...");

        // Run in a background thread to keep the UI responsive while decoding
        new Thread(() -> {
            try {
                // 1. Extract Decoder
                File decoder = extractDecoder();

                // 2. Convert & Load (The Pipeline)
                // Returns Map<String, CustomMesh>
                GlbToObjConverter converter = new GlbToObjConverter();
                Map<String, CustomMesh> meshes = converter.convert(glbFile, decoder);

                // 3. Add to Universe (Back on the Event Dispatch Thread)
                if (meshes != null && !meshes.isEmpty()) {
                	// Inside GltfMeshImporter.loadAndShowGltfMeshes...

                	SwingUtilities.invokeLater(() -> {
                		for (Map.Entry<String, CustomMesh> entry : meshes.entrySet()) {
                			try {
                				String name = entry.getKey();
                				CustomMesh mesh = entry.getValue();

                				// FIX: Capture the Content object and explicitly lock it
                				// Assuming addCustomMesh returns the Content object (standard behavior)
                				ij3d.Content c = finalUniv.addCustomMesh(mesh, name, true);

                				if (c != null) {
                					c.setLocked(true); // <--- Force lock
                				}

                			} catch (Exception e) {
                				IJ.log("Failed to add mesh: " + entry.getKey());
                			}
                		}
                		// Reset view if this was a fresh import (first item only)
                		if (finalUniv.getContents().size() == meshes.size()) {
//                			finalUniv.resetView();
                		}
//                		IJ.log("Import Complete.");
                	});                
                } else {
                    IJ.log("Import Failed: No meshes found.");
                }

            } catch (Exception e) {
                IJ.handleException(e);
            }
        }).start();
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