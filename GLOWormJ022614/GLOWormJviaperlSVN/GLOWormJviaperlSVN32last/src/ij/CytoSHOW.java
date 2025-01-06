package ij;
import java.net.URL;
import javax.jnlp.*;


public class CytoSHOW {

public static BasicService bs;
public static URL codebaseURL;

	public static void main(String[] args) {
		
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
