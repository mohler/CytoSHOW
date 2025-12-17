package customnode;

import javax.vecmath.Point3f;
import ij.IJ;
import ij3d.Image3DUniverse;

import javax.vecmath.Color4f;
import javax.vecmath.Color3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.BufferedReader;

public class WavefrontLoader {

    private LinkedHashMap<String, CustomMesh> meshes;

    public WavefrontLoader() {}

    private BufferedReader in;
    private String line;

    // attributes of the currently read mesh
    private ArrayList<Point3f> vertices = new ArrayList<Point3f>();
    private ArrayList<Point3f> indices = new ArrayList<Point3f>();
    private String name = null;
    private Color4f material = null;
    private int type = -1;
    private String objfile = null;
    public boolean allLinesParsed;
    public int finalMeshCount;
    private String objFileName;
    private String objFileParentName;
    private String objFileGrandParentName;
    private String objFileGreatGrandParentName;

    public static LinkedHashMap<String, CustomMesh> load(String objfile, InputStream[] objmtlStreams, boolean flipXcoords) throws IOException {
        WavefrontLoader wl = new WavefrontLoader();
        try {
            wl.parse(objfile, objmtlStreams, flipXcoords);
        } catch(RuntimeException e) {
            System.out.println("error reading " + wl.name);
            throw e;
        }
        return wl.meshes;
    }

    public LinkedHashMap<String, CustomMesh> loadObjs(String objfile, InputStream[] objmtlStreams, boolean flipXcoords) throws IOException {
        try {
            parse(objfile, objmtlStreams, flipXcoords);
        } catch(RuntimeException e) {
            System.out.println("error reading " + name);
            throw e;
        }
        return meshes;
    }
    
    private void parse(String objfile, InputStream[] objmtlStreams, boolean flipXcoords) throws IOException {
        meshes = new LinkedHashMap<String, CustomMesh>();
        
        File f = new File(objfile);
        String fileName = f.getName();

        // --- GLB CHECK ---
        if (fileName.toLowerCase().endsWith(".glb") || fileName.toLowerCase().endsWith(".gltf")) {
            try {
                IJ.log("Importing GLB: " + fileName);
                GltfMeshImporter.loadAndShowGltfMeshes(f.getAbsoluteFile(), IJ.getInstance().getDragAndDrop().getDropUniverse());
                allLinesParsed = true;
            } catch (Exception e) {
                IJ.log("GLB Import Failed: " + e.getMessage());
                e.printStackTrace();
                allLinesParsed = true; 
            }
            return; 
        }

        // --- PREPARE LOGIC ---    Cuteness here involving original path with a faked obj name, 
        //						        so data read from obj-formatted stream from the Gltf.decoder logic branch, but "...FilesToUse" switch can reformat vertices:
        objFileName = fileName;
        objFileParentName = "";
        objFileGrandParentName = "";
        objFileGreatGrandParentName = "";
        
        if (f.getParentFile()!=null 
                && f.getParentFile().getParentFile()!=null 
                && f.getParentFile().getParentFile().getParentFile()!=null 
                && f.getParentFile().exists()
                && f.getParentFile().getParentFile().exists() 
                && f.getParentFile().getParentFile().getParentFile().exists()){
            objFileParentName = f.getParentFile().getName();
            objFileGrandParentName = f.getParentFile().getParentFile().getName();
            objFileGreatGrandParentName = f.getParentFile().getParentFile().getParentFile().getName();
        }

        this.objfile = objfile;
        final File fileRef = f; // Capture for inner class

        // Define the heavy lifting as a Runnable
        Runnable parserLogic = new Runnable() {
            @Override
            public void run() {
                try {
                    // Stream Setup
                    if (objmtlStreams == null || objmtlStreams[0] == null) {
                        try {
                            in = new BufferedReader(new FileReader(fileRef));
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                            return;
                        }
                    } else {
                        in = new BufferedReader(new InputStreamReader(objmtlStreams[0]));
                    }

                    HashMap<String, Color4f> materials = null;
                    boolean noVlines = true;
                    boolean noFlines = true;

                    while((line = in.readLine()) != null) {
                        if(line.startsWith("mtllib")) {
                            String mtlName = line.split("\\s+")[1].trim();
                            materials = readMaterials(fileRef, mtlName, objmtlStreams);
                        } else if(line.startsWith("g ")) {
                            if(name != null) {
                                CustomMesh cm = createCustomMesh();
                                if(cm != null) meshes.put(name, cm);
                                indices = new ArrayList<Point3f>();
                                material = null;
                            }
                            name = line.split("\\s+")[1].trim();
                        } else if(line.startsWith("usemtl ")) {
                            if(materials != null) material = materials.get(line.split("\\s+")[1]);
                        } else if(line.startsWith("v ")) {
                            readVertex(flipXcoords);
                            noVlines = false;
                        } else if(line.startsWith("f ")) {
                            readFace();
                            noFlines = false;
                        } else if(line.startsWith("l ")) readFace();
                          else if(line.startsWith("p ")) readFace();
                        
                        // Only sleep if running async to yield to UI
                        if (objmtlStreams == null) IJ.wait(0); 
                    }
                    
                    if (objmtlStreams == null || objmtlStreams[0] == null) {
                        in.close();
                    }

                    if (noVlines || noFlines) {
                        meshes = null;
                        name = null;
                        allLinesParsed = true;
                        return;
                    }

                    if(name != null && indices.size() > 0) {
                        CustomMesh cm = createCustomMesh();
                        cm.setFlippedXCoords(flipXcoords);
                        if(cm != null) meshes.put(name, cm);
                        indices = new ArrayList<Point3f>();
                        material = null;
                    }
                    allLinesParsed = true;
                    finalMeshCount = meshes != null ? meshes.size() : 0;

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        // --- EXECUTION STRATEGY ---
        // HEURISTIC: If streams are provided, it's the GLB Converter -> RUN SYNC.
        // If streams are NULL, it's a standard File Drag-Drop -> RUN ASYNC (Thread).
        
        if (objmtlStreams != null && objmtlStreams.length > 0 && objmtlStreams[0] != null) {
            // SYNC MODE (For GLB Pipes)
            parserLogic.run();
        } else {
            // ASYNC MODE (For Legacy OBJ Drag & Drop)
            Thread compoundObjOpeningThread = new Thread(parserLogic);
            compoundObjOpeningThread.start();
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

	private void readVertex(boolean specialDrop) {
		String[] sp = line.split("\\s+");
		int flipXCoef = specialDrop?-1:1;
		if (( objFileGrandParentName.contains("AllNineBrains_FilesToUse") || objFileGreatGrandParentName.contains("AllNineBrains_FilesToUse"))) {
//			flipXCoef = 1;
			//		FOR JSH
			if (objFileName.contains("JSH") ||   objFileParentName.contains("JSH") || objFileGrandParentName.contains("JSH")) {
				float x = 20803.52f;
				float y = 12784.513f;
				float z = -4789.9414f;
				float w = 50000;
				vertices.add(new Point3f(
						w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-14701),
						w*(Float.parseFloat(sp[2]) - y)/(x-14701),
						w*(Float.parseFloat(sp[3]) -z)/(x-14701)));
			}
			else if (objFileName.contains("MeiAdult_Reslice") ||   objFileParentName.contains("MeiAdult_Reslice") || objFileGrandParentName.contains("MeiAdult_Reslice")) {
				float x = 10800.0f;
				float y = 10816.0f;
				float z =  -8844.971f;
				float w = 50000;
				vertices.add(new Point3f(
						-w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+4736),
						w*(Float.parseFloat(sp[2]) - y)/(x-4736),
						w*(Float.parseFloat(sp[3]) -z)/(x-4736)));
			}
			else if (objFileName.contains("MeiAdult") || objFileName.contains("EM_Stack") || objFileParentName.contains("MeiAdult") || objFileGrandParentName.contains("MeiAdult")
					 ||objFileParentName.contains("EM_Stack") || objFileGrandParentName.contains("EM_Stack")) {
				float x = 10800.0f;
				float y = 10816.0f;
				float z =  -8844.971f;
				float w = 50000;
				vertices.add(new Point3f(
						(float)(Math.cos(-0.227)*(w*(Float.parseFloat(sp[3]) -z)/(x-4736)) -  Math.sin(-0.227)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+4736))),
						w*(Float.parseFloat(sp[2]) - y)/(x-4736),
						(float)(Math.sin(-0.227)*(w*(Float.parseFloat(sp[3]) -z)/(x-4736)) +  Math.cos(-0.227)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+4736)))));
			}
			else if (objFileName.contains("L3") || objFileParentName.contains("L3") || objFileGrandParentName.contains("L3")) {
				float x = 13793f;
				float y = 13302f;
				float z = -4242f;
				float w = 50000;
				vertices.add(new Point3f(
						w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(-x+8829),
						w*(Float.parseFloat(sp[2]) - y)/(x-8829),
						w*(Float.parseFloat(sp[3]) -z)/(x-8829)));
			}
			else if (objFileName.contains("L2_2") || objFileParentName.contains("L2_2") || objFileGrandParentName.contains("L2_2")) {
				float x = 8002f;
				float y = 5648f;
				float z = -4090f;
				float w = 50000;
				vertices.add(new Point3f(
						-w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-2804),
						w*(Float.parseFloat(sp[2]) - y)/(x-2804),
						w*(Float.parseFloat(sp[3]) -z)/(x-2804)));
			}
			else if (objFileName.contains("L1_3") || objFileParentName.contains("L1_3") || objFileGrandParentName.contains("L1_3")) {
				float x = 8316f;
				float y = 6042f;
				float z = -2470f;
				float w = 50000;
				vertices.add(new Point3f(
						-w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-4412),
						w*(Float.parseFloat(sp[2]) - y)/(x-4412),
						w*(Float.parseFloat(sp[3]) -z)/(x-4412)));
			}
			else if (objFileName.contains("L1_2") || objFileParentName.contains("L1_2") || objFileGrandParentName.contains("L1_2")) {
				float x = 12564f;
				float y = 7782f;
				float z =  -3865f;
				float w = 50000;
				vertices.add(new Point3f(
						-(float)(Math.cos(-0.495)*(w*(Float.parseFloat(sp[3]) -z)/(x-8788)) - Math.sin(-0.495)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-8788))),
						w*(Float.parseFloat(sp[2]) - y)/(x-8788),
						-(float)(Math.sin(-0.495)*(w*(Float.parseFloat(sp[3]) -z)/(x-8788)) + Math.cos(-0.495)*(w*(flipXCoef*(Float.parseFloat(sp[1]) - x))/(x-8788)))));
			}
			else if (objFileName.contains("L1_4") || objFileParentName.contains("L1_4") || objFileGrandParentName.contains("L1_4")) {		
				float x = 8402f;
				float y = 12372f;
				float z =  -2725f;
				float w = 50000;
				vertices.add(new Point3f(
						w*(Float.parseFloat(sp[1]) - x)/(x-4464),
						(float)(Math.cos(-0.558)*(w*(Float.parseFloat(sp[2]) - y)/(x-4464)) - Math.sin(-0.558)*(w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-4464))),
						(float)(Math.sin(-0.558)*(w*(Float.parseFloat(sp[2]) - y)/(x-4464)) + Math.cos(-0.558)*(w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-4464)))));
			}
			else if (objFileName.contains("L1_5") || objFileParentName.contains("L1_5") || objFileGrandParentName.contains("L1_5")) {		
				float x = 7990f;
				float y = 6999f;
				float z =  -3317f;
				float w = 50000;
				vertices.add(new Point3f(
						-(float)(Math.cos(-2.30)*(w*(Float.parseFloat(sp[1]) - x)/(x-3973)) - Math.sin(-2.30)*((w*(Float.parseFloat(sp[2]) - y)/(x-3973)))),
						(float)(Math.sin(-2.30)*(w*(Float.parseFloat(sp[1]) - x)/(x-3973)) + Math.cos(-2.30)*((w*(Float.parseFloat(sp[2]) - y)/(x-3973)))),
						w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-3973)));
			}
			else if (objFileName.contains("N2U") || objFileParentName.contains("N2U") || objFileGrandParentName.contains("N2U")) {
				float x = 19953f;
				float y = 15255f;
				float z =  -6105f;
				float w = 50000;
				vertices.add(new Point3f(
						(float)(Math.cos(-0.384)*(w*(Float.parseFloat(sp[1]) - x)/(x-9936)) - (float)(Math.sin(-0.384)*(w*(Float.parseFloat(sp[2]) - y)/(x-9936)))),
						(float)(Math.sin(-0.384)*(w*(Float.parseFloat(sp[1]) - x)/(x-9936)) + (float)(Math.cos(-0.384)*(w*(Float.parseFloat(sp[2]) - y)/(x-9936)))),
						w*(flipXCoef*(Float.parseFloat(sp[3]) - z))/(x-9936)));
			}
			else {
				vertices.add(new Point3f(
						flipXCoef*(Float.parseFloat(sp[1])),
						Float.parseFloat(sp[2]),
						Float.parseFloat(sp[3])));
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
			return readMaterials(mtlFileName, objmtlStreams[objmtlStreams.length>1?1:0]);
			
		File mtlFile = new File(objfile.getParentFile(), mtlFileName);
		if(mtlFile.exists())
			return readMaterials(mtlFile);

		mtlFile = new File(mtlFileName);
		if(mtlFile.exists())
			return readMaterials(mtlFile);
		return null;
	}

	public static HashMap<String, Color4f> readMaterials(File f)
							throws IOException {
		return readMaterials(f.getAbsolutePath(), null);
	}

	public static HashMap<String, Color4f> readMaterials(String file, InputStream mtlStream)
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