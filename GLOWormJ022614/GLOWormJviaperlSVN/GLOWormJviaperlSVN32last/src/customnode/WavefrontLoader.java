package customnode;

import javax.vecmath.Point3f;
import javax.vecmath.Color4f;
import javax.vecmath.Color3f;

import java.util.ArrayList;
import java.util.HashMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

public class WavefrontLoader {

	/**
	 * Load the specified obj file and returns the result as
	 * a hash map, mapping the object names to the corresponding
	 * <code>CustomMesh</code> objects.
	 * @param objmtlStreams 
	 */
	public static HashMap<String, CustomMesh> load(String objfile, InputStream[] objmtlStreams, boolean flipXcoords)
						throws IOException {
		WavefrontLoader wl = new WavefrontLoader();
		try {
			wl.parse(objfile, objmtlStreams, flipXcoords);
		} catch(RuntimeException e) {
			System.out.println("error reading " + wl.name);
			throw e;
		}
		return wl.meshes;
	}

	private HashMap<String, CustomMesh> meshes;

	private  WavefrontLoader() {}

	private BufferedReader in;
	private String line;

	// attributes of the currently read mesh
	private ArrayList<Point3f> vertices = new ArrayList<Point3f>();
	private ArrayList<Point3f> indices = new ArrayList<Point3f>();
	private String name = null;
	private Color4f material = null;
	private int type = -1;
	private String objfile = null;

	private void parse(String objfile, InputStream[] objmtlStreams, boolean flipXcoords) throws IOException {
		this.objfile = objfile;
		File f = null;
		if (objmtlStreams==null || objmtlStreams[0]==null) {
			f = new File(objfile);
			in = new BufferedReader(new FileReader(objfile));
		}else {
			in = new BufferedReader(new InputStreamReader(objmtlStreams[0]));
		}

		HashMap<String, Color4f> materials = null;

		meshes = new HashMap<String, CustomMesh>();

		while((line = in.readLine()) != null) {
			if(line.startsWith("mtllib")) {
				String mtlName = line.split("\\s+")[1].trim();
				materials = readMaterials(f, mtlName, objmtlStreams);
			} else if(line.startsWith("g ")) {
				if(name != null) {
					CustomMesh cm = createCustomMesh();
					if(cm != null)
						meshes.put(name, cm);
					indices = new ArrayList<Point3f>();
					material = null;
				}
				name = line.split("\\s+")[1].trim();
			} else if(line.startsWith("usemtl ")) {
				if(materials != null)
					material = materials.get(line.split("\\s+")[1]);
			} else if(line.startsWith("v ")) {
				readVertex(flipXcoords);
			} else if(line.startsWith("f ")) {
				readFace();
			} else if(line.startsWith("l ")) {
				readFace();
			} else if(line.startsWith("p ")) {
				readFace();
			}
		}
		in.close();
		if(name != null && indices.size() > 0) {
			CustomMesh cm = createCustomMesh();
			cm.setFlippedXCoords(flipXcoords);
			if(cm != null)
				meshes.put(name, cm);
			indices = new ArrayList<Point3f>();
			material = null;
		}
	}

	private CustomMesh createCustomMesh() {
		if(indices.size() == 0)
			return null;
		CustomMesh cm = null;
		switch(type) {
			case 1: cm = new CustomPointMesh(indices); break;
			case 2: cm = new CustomLineMesh(indices, CustomLineMesh.PAIRWISE); break;
			case 3: cm = new CustomTriangleMesh(indices); break;
			case 4: cm = new CustomQuadMesh(indices); break;
			default: throw new RuntimeException(
				"Unexpected number of vertices for faces");
		}
		cm.loadedFromFile = objfile;
		cm.loadedFromName = name;
		cm.changed = false;
		if(material == null)
			return cm;
		cm.setColor(new Color3f(material.x, material.y, material.z));
		cm.setTransparency(material.w);
		cm.changed = false;
		return cm;
	}

	private void readFace() {
		String[] sp = line.split("\\s+");
		type = sp.length - 1;
		for(int i = 1; i < sp.length; i++) {
			int idx = -1;
			try {
				idx = Integer.parseInt(sp[i]) - 1;
			} catch(NumberFormatException e) {
				int l = sp[i].indexOf('/');
				if(l != -1) {
					sp[i] = sp[i].substring(0, l);
					idx = Integer.parseInt(sp[i]) - 1;
				}
			}
			if(idx == -1)
				throw new RuntimeException(
					"Error parsing faces: " + name);
			indices.add(vertices.get(idx));
		}
	}

	private void readVertex(boolean flipXcoords) {
		String[] sp = line.split("\\s+");
		int flipXCoef = flipXcoords?1:1;

		vertices.add(new Point3f(
				flipXCoef*Float.parseFloat(sp[1]),
				Float.parseFloat(sp[2]),
				Float.parseFloat(sp[3])));
		
	}

	private HashMap<String, Color4f> readMaterials(
			File objfile, String mtlFileName, InputStream[] objmtlStreams) throws IOException {

		if(objmtlStreams!=null)
			return readMaterials(mtlFileName, objmtlStreams[1]);
			
		File mtlFile = new File(objfile.getParentFile(), mtlFileName);
		if(mtlFile.exists())
			return readMaterials(mtlFile);

		mtlFile = new File(mtlFileName);
		if(mtlFile.exists())
			return readMaterials(mtlFile);
		return null;
	}

	private static HashMap<String, Color4f> readMaterials(File f)
							throws IOException {
		return readMaterials(f.getAbsolutePath(), null);
	}

	private static HashMap<String, Color4f> readMaterials(String file, InputStream mtlStream)
							throws IOException {
		String name = null;
		Color4f color = null;

		HashMap<String, Color4f> materials =
				new HashMap<String, Color4f>();
		
		BufferedReader mtlInReader = null;
		if (mtlStream ==null) {
			mtlInReader = new BufferedReader(new FileReader(file));
		}else {
			mtlInReader = new BufferedReader(new InputStreamReader(mtlStream));
		}
		String line;
		while((line = mtlInReader.readLine()) != null) {
			// newmtl: if we've read one before
			// add it to the hash map
			if(line.startsWith("newmtl")) {
				if(name != null && color != null)
					materials.put(name, color);
				String[] sp = line.split("\\s+");
				name = sp[1].trim();
				color = null;
			}

			if(line.startsWith("Kd")) {
				String[] sp = line.split("\\s+");
				color = new Color4f(
					Float.parseFloat(sp[1]),
					Float.parseFloat(sp[2]),
					Float.parseFloat(sp[3]),
					1);
			}

			if(line.startsWith("d ")) {
				if(color == null)
					color = new Color4f(1, 1, 1, 1);
				String[] sp = line.split("\\s+");
				color.w = 1 - Float.parseFloat(sp[1]);
			}
		}
		mtlInReader.close();

		if(name != null && color != null)
			materials.put(name, color);

		return materials;
	}
}

