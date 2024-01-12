package customnode;

import javax.vecmath.Point3f;
import javax.vecmath.Color4f;
import javax.vecmath.Color3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;

public class WavefrontLoader {

	/**
	 * Load the specified obj file and returns the result as
	 * a hash map, mapping the object names to the corresponding
	 * <code>CustomMesh</code> objects.
	 * @param objmtlStreams 
	 */
	public static LinkedHashMap<String, CustomMesh> load(String objfile, InputStream[] objmtlStreams, boolean flipXcoords)
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

	private LinkedHashMap<String, CustomMesh> meshes;

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
		meshes = new LinkedHashMap<String, CustomMesh>();

		Thread compoundObjOpeningThread = new Thread (new Runnable() {

			@Override
			public void run() {
				WavefrontLoader.this.objfile = objfile;
				File f = null;
				if (objmtlStreams==null || objmtlStreams[0]==null) {
					f = new File(objfile);
					try {
						in = new BufferedReader(new FileReader(objfile));
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				}else {
					in = new BufferedReader(new InputStreamReader(objmtlStreams[0]));
				}

				HashMap<String, Color4f> materials = null;

				try{
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
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(name != null && indices.size() > 0) {
					CustomMesh cm = createCustomMesh();
					cm.setFlippedXCoords(flipXcoords);
					if(cm != null)
						meshes.put(name, cm);
					indices = new ArrayList<Point3f>();
					material = null;
				}

			}
		});
		compoundObjOpeningThread.start();
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

	private void readVertex(boolean specialDrop) {
		String[] sp = line.split("\\s+");
		int flipXCoef = specialDrop?-1:1;
		String objFileParentName = "";
		String objFileGrandParentName = "";
		String objFileGreatGrandParentName = "";
		if (new File(objfile).getParentFile()!=null 
				&& new File(objfile).getParentFile().getParentFile()!=null 
				&& new File(objfile).getParentFile().getParentFile().getParentFile()!=null 
				&& new File(objfile).getParentFile().exists()
				&& new File(objfile).getParentFile().getParentFile().exists() 
				&& new File(objfile).getParentFile().getParentFile().getParentFile().exists()){
			objFileParentName = new File(objfile).getParentFile().getName();
			objFileGrandParentName = new File(objfile).getParentFile().getParentFile().getName();
			objFileGreatGrandParentName = new File(objfile).getParentFile().getParentFile().getParentFile().getName();
		}
		if (( objFileGrandParentName.contains("AllNineBrains_FilesToUse") || objFileGreatGrandParentName.contains("AllNineBrains_FilesToUse"))
				&& !specialDrop) {
			flipXCoef = 1;
			//		FOR JSH
			if (objFileParentName.contains("JSH") || objFileGrandParentName.contains("JSH")) {
				float x = 20803.52f;
				float y = 12784.513f;
				float z = -4789.9414f;
				float w = 50000;
				vertices.add(new Point3f(
						w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-14701),
						w*(Float.parseFloat(sp[2]) - y)/(x-14701),
						w*(Float.parseFloat(sp[3]) -z)/(x-14701)));
			}
			//		//		FOR MEI ADULT
			//		if (objFileGrandParentName.contains("MeiAdult")) {
			//		float x = 10800.0f;
			//		float y = 10816.0f;
			//		float z =  -8844.971f;
			//		float w = 50000;
			//		vertices.add(new Point3f(
			//				w*(Float.parseFloat(sp[3]) -z)/(x-4736),
			//				w*(Float.parseFloat(sp[2]) - y)/(x-4736),
			//				w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+4736)));
			//		}
			//		FOR MEI ADULT rot corr xz
			else if (objFileParentName.contains("MeiAdult_Reslice") || objFileGrandParentName.contains("MeiAdult_Reslice")) {
				float x = 10800.0f;
				float y = 10816.0f;
				float z =  -8844.971f;
				float w = 50000;
				vertices.add(new Point3f(
						-w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+4736),
						w*(Float.parseFloat(sp[2]) - y)/(x-4736),
						w*(Float.parseFloat(sp[3]) -z)/(x-4736)));
			}
			else if (objFileParentName.contains("MeiAdult") || objFileGrandParentName.contains("MeiAdult")) {
				float x = 10800.0f;
				float y = 10816.0f;
				float z =  -8844.971f;
				float w = 50000;
				vertices.add(new Point3f(
						(float)(Math.cos(-0.227)*(w*(Float.parseFloat(sp[3]) -z)/(x-4736)) -  Math.sin(-0.227)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+4736))),
						w*(Float.parseFloat(sp[2]) - y)/(x-4736),
						(float)(Math.sin(-0.227)*(w*(Float.parseFloat(sp[3]) -z)/(x-4736)) +  Math.cos(-0.227)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+4736)))));
			}
			//		FOR L3
			else if (objFileParentName.contains("L3") || objFileGrandParentName.contains("L3")) {
				float x = 13793f;
				float y = 13302f;
				float z = -4242f;
				float w = 50000;
				vertices.add(new Point3f(
						w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+8829),
						w*(Float.parseFloat(sp[2]) - y)/(x-8829),
						w*(Float.parseFloat(sp[3]) -z)/(x-8829)));
			}
			//		FOR L2_2
			else if (objFileParentName.contains("L2_2") || objFileGrandParentName.contains("L2_2")) {
				float x = 8002f;
				float y = 5648f;
				float z = -4090f;
				float w = 50000;
				vertices.add(new Point3f(
						w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-2804),
						w*(Float.parseFloat(sp[2]) - y)/(x-2804),
						w*(Float.parseFloat(sp[3]) -z)/(x-2804)));
			}
			//	FOR L1_3
			else if (objFileParentName.contains("L1_3") || objFileGrandParentName.contains("L1_3")) {
				float x = 8316f;
				float y = 6042f;
				float z = -2470f;
				float w = 50000;
				vertices.add(new Point3f(
						-w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-4412),
						w*(Float.parseFloat(sp[2]) - y)/(x-4412),
						w*(Float.parseFloat(sp[3]) -z)/(x-4412)));
			}
			//	FOR L1_2
			//		if (objFileGrandParentName.contains("L1_2")) {
			//	float x = 12564f;
			//	float y = 7782f;
			//	float z =  -3865f;
			//	float w = 50000;
			//	vertices.add(new Point3f(
			//	-w*(Float.parseFloat(sp[3]) -z)/(x-8788),
			//	w*(Float.parseFloat(sp[2]) - y)/(x-8788),
			//	-w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-8788)));
			//		}

			//	FOR L1_2 fixing rot around Yaxis
			else if (objFileParentName.contains("L1_2") || objFileGrandParentName.contains("L1_2")) {
				float x = 12564f;
				float y = 7782f;
				float z =  -3865f;
				float w = 50000;
				vertices.add(new Point3f(
						-(float)(Math.cos(-0.495)*(w*(Float.parseFloat(sp[3]) -z)/(x-8788)) - Math.sin(-0.495)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-8788))),
						w*(Float.parseFloat(sp[2]) - y)/(x-8788),
						-(float)(Math.sin(-0.495)*(w*(Float.parseFloat(sp[3]) -z)/(x-8788)) + Math.cos(-0.495)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-8788)))));
			}
			//	//	FOR L1_4
			//		if (objFileGrandParentName.contains("L1_4")) {
			//			float x = 8402f;
			//		float y = 12372f;
			//		float z =  -2725f;
			//		float w = 50000;
			//		vertices.add(new Point3f(
			//				w*(Float.parseFloat(sp[1]) - x)/(x-4464),
			//				w*(Float.parseFloat(sp[2]) - y)/(x-4464),
			//				w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-4464)));
			//		}

			//		FOR L1_4 with yz rotation around x axis
			else if (objFileParentName.contains("L1_4") || objFileGrandParentName.contains("L1_4")) {		
				float x = 8402f;
				float y = 12372f;
				float z =  -2725f;
				float w = 50000;
				vertices.add(new Point3f(
						w*(Float.parseFloat(sp[1]) - x)/(x-4464),
						(float)(Math.cos(-0.558)*(w*(Float.parseFloat(sp[2]) - y)/(x-4464)) - Math.sin(-0.558)*(w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-4464))),
						(float)(Math.sin(-0.558)*(w*(Float.parseFloat(sp[2]) - y)/(x-4464)) + Math.cos(-0.558)*(w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-4464)))));
			}

			//	FOR L1_5
			else if (objFileParentName.contains("L1_5") || objFileGrandParentName.contains("L1_5")) {		
				float x = 7990f;
				float y = 6999f;
				float z =  -3317f;
				float w = 50000;
				vertices.add(new Point3f(
						-(float)(Math.cos(-2.30)*(w*(Float.parseFloat(sp[1]) - x)/(x-3973)) - Math.sin(-2.30)*((w*(Float.parseFloat(sp[2]) - y)/(x-3973)))),
						(float)(Math.sin(-2.30)*(w*(Float.parseFloat(sp[1]) - x)/(x-3973)) + Math.cos(-2.30)*((w*(Float.parseFloat(sp[2]) - y)/(x-3973)))),
						w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-3973)));
				//
			}
			//	FOR N2Uadult
			else if (objFileParentName.contains("N2U") || objFileGrandParentName.contains("N2U")) {
				float x = 19953f;
				float y = 15255f;
				float z =  -6105f;
				float w = 50000;
				vertices.add(new Point3f(
						(float)(Math.cos(-0.384)*(w*(Float.parseFloat(sp[1]) - x)/(x-9936)) - (float)(Math.sin(-0.384)*(w*(Float.parseFloat(sp[2]) - y)/(x-9936)))),
						(float)(Math.sin(-0.384)*(w*(Float.parseFloat(sp[1]) - x)/(x-9936)) + (float)(Math.cos(-0.384)*(w*(Float.parseFloat(sp[2]) - y)/(x-9936)))),
						w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-9936)));
			}
		}
		else {
			vertices.add(new Point3f(
					flipXCoef*(Float.parseFloat(sp[1])),
					Float.parseFloat(sp[2]),
					Float.parseFloat(sp[3])));
		}
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

