package customnode;

import ij.IJ;
import ij.util.NativeTools;
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
        if (!glbFile.exists() || !(glbFile.getName().toLowerCase().endsWith(".glb") || glbFile.getName().toLowerCase().endsWith(".gltf"))) 
        	return;

        // Ensure we have a universe
        final Image3DUniverse finalUniv = (univ == null) ? new Image3DUniverse() : univ;
        if (univ == null) {
             IJ.getInstance().getDragAndDrop().setDropUniverse(finalUniv);
             finalUniv.show(false);
        }

        IJ.log("Streaming GLTF/GLB file for import...");

     // --- REMOVED: new Thread(() -> { ... }).start(); --- 
        // We now run on the thread provided by ContentImportWorker
        // old thinking->        // Run in a background thread to keep the UI responsive while decoding
//        new Thread(() -> {
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
//        }).start();
    }

    private static File extractDecoder() throws IOException {
        // NativeTools handles the path lookup, extraction, and executable permissions.
        // We just return the File it gives us.
        return NativeTools.getBundledBinary("draco_decoder");
    }


}