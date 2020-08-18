package ij;

import java.io.IOException;

import org.vcell.gloworm.MQTVSSceneLoader64;

import ij3d.Image3DUniverse;
import ij3d.ImageJ3DViewer;

public class DC_PHATE_VIEWER {

	public static void main(String[] args) {
//			ImageJ3DViewer.main(args);
			
			Image3DUniverse jshUniv = new Image3DUniverse();
			jshUniv.show();
			String s1 = MQTVSSceneLoader64.class.getResource("docs/jsh_dccphate_2017b_0000.obj").getPath();
			jshUniv.addContentLater(s1);
			try {
				String s2 = MQTVSSceneLoader64.class.getResource("docs/jsh_fig1bColorAndOrirentation.view").getPath();
				jshUniv.loadView(s2);
			} catch (IOException e) {
				e.printStackTrace();
			}

			Image3DUniverse n2uUniv = new Image3DUniverse();
			n2uUniv.show();
			s1 = MQTVSSceneLoader64.class.getResource("docs/n2u_dccphate_2017b_flipX_0000_0000.obj").getPath();
			n2uUniv.addContentLater(s1);
			try {
				String s2 = MQTVSSceneLoader64.class.getResource("docs/n2u_flipX_fig1BColorOrientation.view").getPath();
				n2uUniv.loadView(s2);
			} catch (IOException e) {
				e.printStackTrace();
			}
			jshUniv.sync(true);
			n2uUniv.sync(true);
			IJ.run("Tile");
}

}
