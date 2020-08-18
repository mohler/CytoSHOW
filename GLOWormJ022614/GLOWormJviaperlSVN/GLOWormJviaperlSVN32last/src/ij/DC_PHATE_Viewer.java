package ij;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.vcell.gloworm.MQTVSSceneLoader64;

import ij3d.Image3DUniverse;

public class DC_PHATE_Viewer {

	public static void main(String[] args) {
		//			ImageJ3DViewer.main(args);

		Image3DUniverse jshUniv = new Image3DUniverse();
		jshUniv.show();
		InputStream jshobjIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/jsh_dccphate_2017b_0000.obj");
		InputStream jshmtlIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/jsh_dccphate_2017b_0000.mtl");			
		InputStream jshviewIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/jsh_fig1bColorAndOrirentation.view");
		Path jshobjPath=null;
		Path jshmtlPath=null;
		Path jshviewPath = null;
		try {
			jshobjPath = Files.createFile((Paths.get(IJ.getDirectory("temp")+"jsh_dccphate_2017b_0000.obj")));
			Files.deleteIfExists(jshobjPath);
			Files.copy(jshobjIn, jshobjPath, StandardCopyOption.REPLACE_EXISTING);
			jshmtlPath = Files.createFile((Paths.get(IJ.getDirectory("temp")+"jsh_dccphate_2017b_0000.mtl")));
			Files.deleteIfExists(jshmtlPath);
			Files.copy(jshmtlIn, jshmtlPath, StandardCopyOption.REPLACE_EXISTING);				
			jshviewPath = Files.createFile((Paths.get(IJ.getDirectory("temp")+"jsh_fig1bColorAndOrirentation.view")));
			Files.deleteIfExists(jshviewPath);
			Files.copy(jshviewIn, jshviewPath, StandardCopyOption.REPLACE_EXISTING);

			jshUniv.addContentLater(jshobjPath.toFile().getPath()); 
			jshUniv.loadView(jshviewPath.toFile().getPath());

			Files.deleteIfExists(jshobjPath);
			Files.deleteIfExists(jshmtlPath);
			Files.deleteIfExists(jshviewPath);


		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}



		Image3DUniverse n2uUniv = new Image3DUniverse();
		n2uUniv.show();
		InputStream n2uobjIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/n2u_dccphate_2017b_flipX_0000_0000.obj");
		InputStream n2umtlIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/n2u_dccphate_2017b_flipX_0000_0000.mtl");			
		InputStream n2uviewIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/n2u_flipX_fig1BColorOrientation.view");
		Path n2uobjPath=null;
		Path n2umtlPath=null;
		Path n2uviewPath = null;
		try {
			n2uobjPath = Files.createFile((Paths.get(IJ.getDirectory("temp")+"n2u_dccphate_2017b_flipX_0000_0000.obj")) );
			Files.deleteIfExists(n2uobjPath);
			Files.copy(n2uobjIn, n2uobjPath, StandardCopyOption.REPLACE_EXISTING);
			n2umtlPath = Files.createFile((Paths.get(IJ.getDirectory("temp")+"n2u_dccphate_2017b_flipX_0000_0000.mtl")));
			Files.deleteIfExists(n2umtlPath);
			Files.copy(n2umtlIn, n2umtlPath, StandardCopyOption.REPLACE_EXISTING);				
			n2uviewPath = Files.createFile((Paths.get(IJ.getDirectory("temp")+"n2u_flipX_fig1BColorOrientation.view")));
			Files.deleteIfExists(n2uviewPath);
			Files.copy(n2uviewIn, n2uviewPath, StandardCopyOption.REPLACE_EXISTING);

			n2uUniv.addContentLater(n2uobjPath.toFile().getPath()); 
			n2uUniv.loadView(n2uviewPath.toFile().getPath());

			Files.deleteIfExists(n2uobjPath);
			Files.deleteIfExists(n2umtlPath);
			Files.deleteIfExists(n2uviewPath);


		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		jshUniv.sync(true);
		n2uUniv.sync(true);
		IJ.run("Tile");
	}


}


