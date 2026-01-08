package ij;
import java.net.URL;
import javax.jnlp.*;

import ij.io.CacheGovernor;
import ij.io.HoardManager;


public class CytoSHOW {

public static BasicService bs;
public static URL codebaseURL;

	public static void main(String[] args) {
		// --- WINDOWS FIXES START ---
        // Must be called before any GUI components are created!
        String osName = System.getProperty("os.name", "generic").toLowerCase();
        if (osName.contains("win")) {
            
            // Disable the Direct3D pipeline (prevents pipeline fighting with OpenGL)
            // This is the most likely fix for the "strobe" you are seeing now.
            System.setProperty("sun.java2d.d3d", "false");
        }
        // --- WINDOWS FIXES END ---
        
		// Option A: Run it in a background thread so UI loads instantly
        new Thread(() -> {
            HoardManager.performSanityCheck();
            // Optionally run the Governor here too, just to be sure
            CacheGovernor.govern(); 
        }).start();

        // Continue with UI initialization...
        
		try {
		    bs = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
		    codebaseURL = bs.getCodeBase();
		    // ... use the codebaseURL to determine the JNLP file
		} catch (UnavailableServiceException e) {
		    // Handle the case where the BasicService is not available
		}
		
		ImageJ.main(args);
	}

}
