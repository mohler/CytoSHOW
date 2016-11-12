package engine;

import ij.ImagePlus;
import ij.process.ImageProcessor;

import java.awt.Image;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.rmi.Naming;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.zip.ZipInputStream;

import compute.Compute;

public class ComputeEngine extends UnicastRemoteObject implements Compute {


	public ComputeEngine(int port) throws RemoteException {
        super(port);
    }

    public static void main(String[] args) {
        Registry registry;
		try {
			registry = LocateRegistry.createRegistry(8080);
		} catch (RemoteException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		try {
            registry = LocateRegistry.getRegistry(8080);
            if (registry == null) {
            	registry = LocateRegistry.createRegistry(8080);
            }
		} catch (RemoteException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
//        if (System.getSecurityManager() != null) {
//            System.setSecurityManager(null);
//        }
        try {
            String name = "//localhost:8080/Compute";
            Compute engine = new ComputeEngine(8080);
            try {
            	Naming.lookup(name);
            } catch (UnexpectedException ue) {
            	Naming.rebind(name, engine);
            } catch (NotBoundException nbe) {
            	Naming.rebind(name, engine);
            } catch (NoSuchObjectException nbe) {
            	Naming.rebind(name, engine);
            }
            System.out.println("ComputeEngine bound");
        } catch (Exception e) {
            System.err.println("ComputeEngine exception:");
            e.printStackTrace();
        }
    }

	public BigDecimal doPi(int numdig) throws RemoteException {
		Pi newPi = new Pi(numdig);
		return newPi.execute();
	}

	public Object getQTPixels(int impID, int channel, int slice, int frame, double jpegQuality) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public int[] getQTDimensions(int impID) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public int setUpMovie(String[] names, String[] movieSlices, int port, boolean redCyanStereo) throws RemoteException {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public String closeRemoteImp(int remoteImpID) {
		return "";
	}

	public String spawnNewServer(String macID,String clientIP, String serverIP, String serverPort) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public byte[] getFileInputByteArray(String pathlist) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean resetServer() throws RemoteException {
		// TODO Auto-generated method stub
		return false;
	}

	public void restartLullClock() throws RemoteException {
		// TODO Auto-generated method stub
		
	}

	public boolean startLullClock() throws RemoteException {
		// TODO Auto-generated method stub
		return false;
	}

	public String[] getOtherViewNames(String name)  throws RemoteException{
		// TODO Auto-generated method stub
		return null;
	}

	public byte[] downloadFileByteArray(String fileName) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public String[] getFiles(String path) throws RemoteException {
		// TODO Auto-generated method stub
		return null;
	}

	public void saveUploadFile(byte[] uploadBytes, String path)
			throws RemoteException {
		// TODO Auto-generated method stub
		
	}

}
