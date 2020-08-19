package ij;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Hashtable;

import org.vcell.gloworm.MQTVSSceneLoader64;

import ij3d.Image3DUniverse;

public class DC_PHATE_Viewer {

	public static void main(String[] args) {
		//			ImageJ3DViewer.main(args);

		InputStream[] jshObjmtlStreams = new InputStream[2];

		Image3DUniverse jshUniv = new Image3DUniverse();
		jshUniv.show();
		InputStream jshobjIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/jsh_dccphate_2017b_0000.obj");
		InputStream jshmtlIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/jsh_dccphate_2017b_0000.mtl");			
		jshObjmtlStreams[0] =jshobjIn;
		jshObjmtlStreams[1] =jshmtlIn;
		InputStream jshviewIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/jsh_fig1bColorAndOrirentation.view");
		Path jshobjPath=null;
		Path jshmtlPath=null;
		Path jshviewPath = null;
		try {
			jshUniv.addContentLater("docs/jsh_dccphate_2017b_0000.obj", jshObjmtlStreams); 
			jshUniv.loadView("docs/jsh_fig1bColorAndOrirentation.view", jshviewIn);

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		InputStream[] n2uObjmtlStreams = new InputStream[2];

		Image3DUniverse n2uUniv = new Image3DUniverse();
		n2uUniv.show();
		InputStream n2uobjIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/n2u_dccphate_2017b_flipX_0000_0000.obj");
		InputStream n2umtlIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/n2u_dccphate_2017b_flipX_0000_0000.mtl");	
		n2uObjmtlStreams[0] =n2uobjIn;
		n2uObjmtlStreams[1] =n2umtlIn;
		InputStream n2uviewIn = MQTVSSceneLoader64.class.getResourceAsStream("docs/n2u_flipX_fig1BColorOrientation.view");
		Path n2uobjPath=null;
		Path n2umtlPath=null;
		Path n2uviewPath = null;
		try {
			n2uUniv.addContentLater("docs/n2u_dccphate_2017b_flipX_0000_0000.obj", n2uObjmtlStreams); 
			n2uUniv.loadView("docs/n2u_flipX_fig1BColorOrientation.view", n2uviewIn);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		jshUniv.sync(true);
		n2uUniv.sync(true);
		IJ.run("Tile");
	}


}


