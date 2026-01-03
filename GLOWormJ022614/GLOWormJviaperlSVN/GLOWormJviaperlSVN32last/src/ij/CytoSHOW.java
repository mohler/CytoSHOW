package ij;
import java.net.URL;
import javax.jnlp.*;

import ij.io.CacheGovernor;
import ij.io.HoardManager;


public class CytoSHOW {

public static BasicService bs;
public static URL codebaseURL;

	public static void main(String[] args) {
		
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
