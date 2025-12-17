package customnode;

import customnode.CustomMesh;
import ij.IJ;
// import ij.util.GltfPackLoader; // Appears unused, but keeping if you need it later

import java.io.File;
import java.io.Writer;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import javax.vecmath.Color4f;
import javax.vecmath.Color3f;
import javax.vecmath.Point3f;

public class WavefrontExporter {

	public static void save(
			Map<String, CustomMesh> meshes,
			String objFilePathString) throws IOException {
		
		save(meshes,objFilePathString,true);
	}
	
	public static void save(
			Map<String, CustomMesh> meshes,
			String objFilePathString, boolean oneFile) throws IOException {

		File objF = new File(objFilePathString);
		String objname = objF.getName();
		String mtlname = objname;
		if(mtlname.endsWith(".obj"))
			mtlname = mtlname.substring(0, mtlname.length() - 4);
		mtlname += ".mtl";

		OutputStreamWriter dos_obj = null,
				   dos_mtl = null;
		try {
			dos_obj = new OutputStreamWriter(
				new BufferedOutputStream(
				new FileOutputStream(objF)), "8859_1");
			dos_mtl = new OutputStreamWriter(
				new BufferedOutputStream(
				new FileOutputStream(
				new File(objF.getParent(), mtlname))),
				"8859_1");
			save(meshes, objFilePathString, mtlname, dos_obj, dos_mtl, null, oneFile);
			dos_obj.flush();
			dos_obj.flush();
			for(String n : meshes.keySet()) {
				CustomMesh m = meshes.get(n);
				m.loadedFromFile = objFilePathString;
				m.loadedFromName = n.replaceAll(" ", "_").
					replaceAll("#", "--");
				m.changed = false;
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try { if (null != dos_obj) dos_obj.close(); } catch (Exception e) {}
			try { if (null != dos_mtl) dos_mtl.close(); } catch (Exception e) {}
		}
	}

	/**
	 * Write the given collection of <code>CustomMesh</code>es;
	 * @param meshes maps a name to a <code>CustomMesh</code>. The name
	 * is used to set the group name ('g') in the obj file.
	 * @param mtlFileName name of the material file, which is used to
	 * store in the obj-file.
	 * @param objWriter <code>Writer</code> for the obj file
	 * @param mtlWriter <code>Writer</code> for the material file.
	 */
	public static void save(
			Map<String, CustomMesh> meshes,
			String objFilePathString, 
			String mtlFileName,
			Writer objWriter,
			Writer mtlWriter) throws IOException {
		save(
				meshes,
				objFilePathString, 
				mtlFileName,
				objWriter,
				mtlWriter,
				null);
	}

	public static void save(
			Map<String, CustomMesh> meshes,
			String objFilePathString, 
			String mtlFileName,
			Writer objWriter,
			Writer mtlWriter,
			String scaleShiftString) throws IOException {
		save (meshes, objFilePathString, mtlFileName, objWriter, mtlWriter, scaleShiftString, false);
	}

	
	public static void save(
				Map<String, CustomMesh> meshes,
				String objFilePathString, 
				String mtlFileName,
				Writer objWriter,
				Writer mtlWriter,
				String scaleShiftString,
				boolean oneFile) throws IOException {

		Double scaleX = 1.0, scaleY = 1.0, scaleZ = 1.0, shiftX = 0.0, shiftY = 0.0, shiftZ = 0.0, rotate90Y = 0.0;
		
		String[] scaleShiftChunks = null;
		if (scaleShiftString!= null) {
			scaleShiftChunks = scaleShiftString.split("\\|");
			double[] scalesAndShifts = new double[scaleShiftChunks.length];
			for (int a=0; a<scaleShiftChunks.length; a++) {
				scalesAndShifts[a] = Double.parseDouble(scaleShiftChunks[a]);
				switch (a) {
				case 0: scaleX = scalesAndShifts[a];
				case 1: scaleY = scalesAndShifts[a];
				case 2: scaleZ = scalesAndShifts[a];
				case 3: shiftX = scalesAndShifts[a];
				case 4: shiftY = scalesAndShifts[a];
				case 5: shiftZ = scalesAndShifts[a];
				case 6: rotate90Y = scalesAndShifts[a];
				}
			}
		}

		final HashMap<Mtl, Mtl> ht_mat = new HashMap<Mtl, Mtl>();

		// Vert indices in .obj files are global, not reset for every
		// object. Starting at '1' because vert indices start at one.
		int j = 1; 

		final StringBuffer tmp = new StringBuffer(100);

		if (oneFile) {
			objWriter.write("# OBJ File\n");
			objWriter.write("mtllib ");
			objWriter.write(mtlFileName);
			objWriter.write('\n');
		}
	
		for(String name : meshes.keySet()) {     //MADE A VERSION OF THIS THAT WILL WORK WITH AN ORDERED COLLECTION by Using LinkedHashMap for meshes
			Writer singleObjWriter =null;
			Writer singleMtlWriter =null;
			HashMap<Mtl, Mtl> single_ht_mat = null;
			String objOutPathString = "";
			if (!oneFile) {
				String singleObjPathRoot = objFilePathString.replace("_0000", "").replace(".obj", "").replace(".OBJ", "");
				new File(singleObjPathRoot).mkdirs();
				objOutPathString = singleObjPathRoot+File.separator+"SVV_"+name.replace("&", "_").replace("~", "-")+"__1_1_0000.obj";
//				objOutPathString = objFilePathString;
				File objF = new File(objOutPathString);
				String mtlOutPathString = objOutPathString.replace(".obj",".mtl");
				File mtlF = new File(mtlOutPathString);
				singleObjWriter = new OutputStreamWriter(
						new BufferedOutputStream(
								new FileOutputStream(objF)), "8859_1");
				singleMtlWriter = new OutputStreamWriter(
						new BufferedOutputStream(
								new FileOutputStream(mtlF)), "8859_1");			
				singleObjWriter.write("# OBJ File\n");
				singleObjWriter.write("mtllib ");
				mtlFileName = mtlF.getName();
				singleObjWriter.write(mtlFileName);
				singleObjWriter.write('\n');

				single_ht_mat = new HashMap<Mtl, Mtl>();
				j=1;
			} else {
				singleObjWriter = objWriter;
				single_ht_mat = ht_mat;
			}
			CustomMesh cmesh = meshes.get(name);

			final List<Point3f> vertices = cmesh.getMesh();
			// make material, and see whether it exists already
			Color3f color = cmesh.getColor();
			if (null == color) {
				// happens when independent colors
				// have been set for each vertex.
				color = CustomMesh.DEFAULT_COLOR;
			}
			Mtl mat = new Mtl(1 - cmesh.getTransparency(),
					color);
			if(single_ht_mat.containsKey(mat))
				mat = single_ht_mat.get(mat);
			else
				single_ht_mat.put(mat, mat);

			// make list of vertices
			String title = name.replaceAll(" ", "_").
					replaceAll("#", "--");
			HashMap<Point3f, Integer> ht_points =
					new HashMap<Point3f, Integer>();
			singleObjWriter.write("g ");
			singleObjWriter.write(title);
			singleObjWriter.write('\n');
			final int len = vertices.size();
			int[] index = new int[len];

			// index over index array, to make faces later
			int k = 0;
			for (Point3f p : vertices) {
				// check if point already exists
				if(ht_points.containsKey(p)) {
					index[k] = ht_points.get(p);
				} else {
					// new point
					index[k] = j;
					// record
					ht_points.put(p, j);
					// append vertex
					if (true) { //rotate90Y==0
						tmp.append('v').append(' ')
						.append(p.x*scaleX+shiftX).append(' ')
						.append(p.y*scaleY+shiftY).append(' ')
						.append(p.z*scaleZ+shiftZ).append('\n');
					} else {
						tmp.append('v').append(' ')
						.append(p.x*scaleX+shiftX).append(' ')
						.append(p.z*scaleZ+shiftZ).append(' ')
						.append(-p.y*scaleY-shiftY).append('\n');
					}
					singleObjWriter.write(tmp.toString());
					tmp.setLength(0);
					j++;
				}
				k++;
			}
			singleObjWriter.write("usemtl ");
			singleObjWriter.write(mat.name);
			singleObjWriter.write('\n');
			// print faces
			if(cmesh.getClass() == CustomTriangleMesh.class)
				writeTriangleFaces(index, singleObjWriter, name);
			else if(cmesh.getClass() == CustomQuadMesh.class)
				writeQuadFaces(index, singleObjWriter, name);
			else if(cmesh.getClass() == CustomPointMesh.class)
				writePointFaces(index, singleObjWriter, name);
			else if(cmesh.getClass() == CustomLineMesh.class) {
				CustomLineMesh clm = (CustomLineMesh)cmesh;
				switch(clm.getMode()) {
				case CustomLineMesh.PAIRWISE:
					writePairwiseLineFaces(
						index, singleObjWriter, name);
					break;
				case CustomLineMesh.CONTINUOUS:
					writeContinuousLineFaces(
						index, singleObjWriter, name);
					break;
				default: throw new IllegalArgumentException(
					"Unknown line mesh mode");
				}
			} else {
				throw new IllegalArgumentException(
					"Unknown custom mesh class: " +
					cmesh.getClass());
			}
			if(!oneFile) {
				// make mtl file
				singleMtlWriter.write("# MTL File\n");
				for(Mtl singleMat : single_ht_mat.keySet()) {
					StringBuffer sb = new StringBuffer(150);
					singleMat.fill(sb);
					singleMtlWriter.write(sb.toString());
				}
				singleMtlWriter.flush();
				singleMtlWriter.close();
				singleObjWriter.flush();
				singleObjWriter.close();
//				try {Thread.sleep(30000);}
//				catch (Exception e) {e.printStackTrace();}

				
				if (IJ.isWindows()) {
					//				String commandString = "cmd /c start /min /wait for %f in ("+objOutPathString+") do (set g=%f&& npx obj2gltf -i %f&& npx gltf-pipeline -i %g:.obj=.gltf% -o %g:.obj=.gltf% -d)";
					final String finalObjOutPathString = objOutPathString;
					Thread makeDracoGLTF = new Thread(new Runnable(){
						public void run() {
							String[] commandStringArrayA = new String[]{"cmd","/c","npx","obj2gltf","--checkTransparency","-i",finalObjOutPathString};
							Process objTOgltf = null;
							try {
								objTOgltf = Runtime.getRuntime().exec(commandStringArrayA);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
							String gltfOutPathString = finalObjOutPathString.replace(".obj", ".gltf");
							String[] commandStringArrayB = new String[]{"cmd","/c","npx","gltf-pipeline","-i",gltfOutPathString,"-o",gltfOutPathString,"--draco.compressionLevel","10","-d"};
							//				while (!new File (gltfOutPathString).canRead())
							//					IJ.wait(10);
							try {
								int waitResult = objTOgltf.waitFor();
								if (waitResult == 0) {
									try {
										Runtime.getRuntime().exec(commandStringArrayB);
									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}
								}
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					});
					makeDracoGLTF.start();
				} else if (IJ.isMacOSX()) {
                    // Logic for processing MULTIPLE files (Batch) on Mac
                    
	                String gltfOutPathString = objOutPathString.replace(".obj", ".gltf");
	                String glbOutPathString = objOutPathString.replace(".obj", ".glb");

                    // --- 1. OBJ to GLTF Command List ---
                    List<String> commandListA = Arrays.asList(
                        "/usr/local/bin/npx",      // Executable
                        "obj2gltf",                // Tool
                        "--checkTransparency",     // Arg
                        "-i",                      // Arg
                        objOutPathString,         // Input
                        "-o",                      // Arg
                        gltfOutPathString          // Output
                    );

                    // --- 2. GLTF to Draco Command List ---
                    List<String> commandListB = Arrays.asList(
                        "/usr/local/bin/npx",
                        "gltf-pipeline",
                        "-i",
                        gltfOutPathString,
                        "-o",
                        glbOutPathString,         // Overwrite existing .gltf
                        "--draco.compressionLevel",
                        "10",
                        "-d"
                    );

                    try {
                        while(!new File(objOutPathString).canRead()) Thread.sleep(10);
                        
                        // RUN STEP A
                        int resultA = runNpxCommand(commandListA, "OBJ2GLTF Conversion"); 

                        if (resultA == 0) {
                            // RUN STEP B
                            int resultB = runNpxCommand(commandListB, "Draco Compression");
                            if (resultB != 0) IJ.error("Draco compression failed with code: " + resultB);
                        } else {
                            IJ.error("OBJ to GLTF conversion failed with code: " + resultA);
                        }
                    } catch (Exception e) {
                        IJ.error("Process execution error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

			}
		}
		if(oneFile) {
			// make mtl file
			mtlWriter.write("# MTL File\n");
			for(Mtl mat : ht_mat.keySet()) {
				StringBuffer sb = new StringBuffer(150);
				mat.fill(sb);
				mtlWriter.write(sb.toString());
			}
			String outPathObj = objFilePathString;
			String outPathGltf = outPathObj.replace(".obj", ".glb");
			mtlWriter.flush();
			mtlWriter.close();
			objWriter.flush();
			objWriter.close();
//			try {Thread.sleep(30000);}
//			catch (Exception e) {e.printStackTrace();}
			
			
			if (IJ.isWindows()) {
				String[] commandStringArrayA = new String[]{"cmd","/c","npx","obj2gltf","--checkTransparency","-i",objFilePathString};
				Process objTOgltf = Runtime.getRuntime().exec(commandStringArrayA);
				String gltfOutPathString = objFilePathString.replace(".obj", ".gltf");
				String[] commandStringArrayB = new String[]{"cmd","/c","npx","gltf-pipeline","-i",gltfOutPathString,"-o",gltfOutPathString,"--draco.compressionLevel","10","-d"};
				//			while (!new File (gltfOutPathString).canRead())
				//				IJ.wait(10);
				try {
					int waitResult = objTOgltf.waitFor();
					if (waitResult == 0) {
						Runtime.getRuntime().exec(commandStringArrayB);
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else if (IJ.isMacOSX()) {
                // Logic for processing ONE file on Mac
                
                String gltfOutPathString = objFilePathString.replace(".obj", ".gltf");
                String glbOutPathString = objFilePathString.replace(".obj", ".glb");

                // --- 1. OBJ to GLTF Command List ---
                List<String> commandListA = Arrays.asList(
                    "/usr/local/bin/npx",
                    "obj2gltf",
                    "--separate",
                    "--checkTransparency",
                    "-i",
                    objFilePathString,
                    "-o",
                    gltfOutPathString,
                    "-d"
                );

                // --- 2. GLTF to Draco GLB Command List ---
                List<String> commandListB = Arrays.asList(
                    "/usr/local/bin/npx",
                    "gltf-pipeline",
                    "-i",
                    gltfOutPathString,
                    "-o",
                    glbOutPathString,          // Output as .glb
                    "--draco.compressionLevel",
                    "10",
                    "-d"
                );

                try {
                    while(!new File(objFilePathString).canRead()) Thread.sleep(10);
                    
                    // RUN STEP A
                    int resultA = runNpxCommand(commandListA, "OBJ2GLTF Conversion"); 

                    if (resultA == 0) {
                        // RUN STEP B
                        int resultB = runNpxCommand(commandListB, "Draco Compression");
                        if (resultB != 0) IJ.error("Draco compression failed with code: " + resultB);
                    } else {
                        IJ.error("OBJ to GLTF conversion failed with code: " + resultA);
                    }
                } catch (Exception e) {
                    IJ.error("Process execution error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
		}
	}
	
	
	/**
	 * Inner class to consume streams prevents the process from hanging.
	 * Redirects output to IJ.log()
	 */
	private static class StreamGobbler implements Runnable {
	    private InputStream inputStream;
	    private String prefix;

	    public StreamGobbler(InputStream inputStream, String prefix) {
	        this.inputStream = inputStream;
	        this.prefix = prefix;
	    }

	    @Override
	    public void run() {
	        // buffer the stream to read line-by-line
	        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
	            String line;
	            while ((line = reader.readLine()) != null) {
	                IJ.log(prefix + ": " + line);
	            }
	        } catch (IOException e) {
	            IJ.log("Error reading stream: " + e.getMessage());
	        }
	    }
	}

	/**
	 * Reliable execution method that handles PATH, Streams, and Waiting
	 */
	private static int runNpxCommand(List<String> commandList, String description) {
	    try {
	        ProcessBuilder pb = new ProcessBuilder(commandList);
	        
            // --- FIX: UPDATE THE PATH ---
            // Eclipse often doesn't see /usr/local/bin. We must add it explicitly.
            java.util.Map<String, String> env = pb.environment();
            String currentPath = env.getOrDefault("PATH", "");
            env.put("PATH", "/usr/local/bin:" + currentPath);
	        
	        // 2. Merge Error and Output streams (Simpler handling)
	        pb.redirectErrorStream(true);

	        IJ.log(commandList.toString());
	        IJ.log("--- Starting " + description + " ---");
	        Process p = pb.start();
            
            // --- FIX: CONSUME OUTPUT ---
            // Must run in a separate thread to prevent blocking
            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), description);
            new Thread(outputGobbler).start();

	        // 3. Wait for finish
	        int exitCode = p.waitFor();
	        IJ.log("--- " + description + " finished with Exit Code: " + exitCode + " ---");
	        
	        return exitCode;

	    } catch (Exception e) {
	        IJ.log("Failed to run " + description + ": " + e.getMessage());
	        e.printStackTrace();
	        return -1;
	    }
	}


	/**
	 * Write faces for triangle meshes.
	 */
// MY BIG CLUNKY THOROUGH VERSION...
//	static void writeTriangleFaces(int[] indices, Writer objWriter, String name)
//						throws IOException {
//		if(indices.length % 3 != 0)
//			throw new IllegalArgumentException(
//				"list of triangles not multiple of 3: " + name);
//		final StringBuffer buf = new StringBuffer(100);
//		objWriter.write("s 1\n");
//		
//		ArrayList<String> indexTrios = new ArrayList<String>();	
//		ArrayList<String> iTriosEdited = null;
//		ArrayList<String> iTriosAlreadyFlipped = new ArrayList<String>();
//		ArrayList<String> iTriosToZap =new ArrayList<String>();
//
//		for (int i = 0; i < indices.length; i += 3) {
//			indexTrios.add(buf.append(indices[i]).append(' ').append(indices[i+1]).append(' ').append(indices[i+2]).toString());
//			buf.setLength(0);
//		}
//		
//		iTriosEdited = new ArrayList<String>(indexTrios);
//		for (String iTrio:indexTrios) {
//			String[] iTrioSplit = iTrio.split(" ");
//			StringBuffer sb = new StringBuffer(200);
//			if (iTrioSplit[0].equals(iTrioSplit[1]) || iTrioSplit[0].equals(iTrioSplit[2]) || iTrioSplit[2].equals(iTrioSplit[1])) {
//				iTriosToZap.add(iTrio);
//				continue;
//			}
//			String iTloop = sb.append(iTrioSplit[0]).append(" ")
//					    							.append(iTrioSplit[2]).append(" ")
//					    							.append(iTrioSplit[1]).append(" ")
//					    							.append(iTrioSplit[0]).append(" ")
//					    							.append(iTrioSplit[2]).append(" ")
//					    							.append(iTrioSplit[1]).toString();
//			sb.setLength(0);
//			
//			boolean pre2faced = false;
//			for (String iTkey:iTriosEdited) {
//				pre2faced = iTloop.contains(iTkey);
//				if (pre2faced) {
//					iTriosAlreadyFlipped.add(iTkey);
//				}
//				continue;			
//			}
//			if (!pre2faced) {
//				iTriosEdited.add(sb.append(iTrioSplit[0]).append(" ")
//					    							.append(iTrioSplit[2]).append(" ")
//					    							.append(iTrioSplit[1]).toString());
//				sb.setLength(0);
//			}
//		}
//		iTriosEdited.removeAll(iTriosToZap);
//		for (String iTkey:iTriosEdited) {
//			buf.append("f ").append(iTkey).append("\n");
//			objWriter.write(buf.toString());
//
//			buf.setLength(0);
//		}
//		objWriter.write('\n');
//	}
	
	
// GEMINI'S SLICKED UP SPEEDY VERSION BASED ON MY BASIC LOGIC BUT IMPROVED...
	/**
	 * Write faces for triangle meshes.
	 * Optimized with Sorted Key ID to handle mirroring and uniqueness in O(N).
	 */
	static void writeTriangleFaces(int[] indices, Writer objWriter, String name)
	                    throws IOException {
	    if (indices.length % 3 != 0)
	        throw new IllegalArgumentException(
	            "list of triangles not multiple of 3: " + name);

	    objWriter.write("s 1\n");
	    
	    // StringBuilder is not synchronized, so it's faster than StringBuffer
	    final StringBuilder buf = new StringBuilder(50);
	    
	    // Set to store unique geometric IDs.
	    // Use HashSet for O(1) lookups. LinkedHashSet if you desperately need to preserve order.
	    Set<String> processedGeometry = new HashSet<String>();

	    for (int i = 0; i < indices.length; i += 3) {
	        int v1 = indices[i];
	        int v2 = indices[i+1];
	        int v3 = indices[i+2];

	        // 1. ZAP VESTIGIAL (Degenerate Faces)
	        // If any vertex indices are identical, it's a line or point. Skip it.
	        if (v1 == v2 || v2 == v3 || v3 == v1) {
	            continue; 
	        }

	        // 2. CREATE GEOMETRIC ID
	        // We sort the indices purely to generate a unique ID for this triangle,
	        // regardless of its winding order or rotation.
	        // (s1, s2, s3 will be the sorted versions of v1, v2, v3)
	        int s1 = v1, s2 = v2, s3 = v3;
	        if (s1 > s2) { int t = s1; s1 = s2; s2 = t; }
	        if (s2 > s3) { int t = s2; s2 = s3; s3 = t; }
	        if (s1 > s2) { int t = s1; s1 = s2; s2 = t; }
	        
	        // ID string e.g., "10 20 30"
	        String geomID = buf.append(s1).append(' ')
	                           .append(s2).append(' ')
	                           .append(s3).toString();
	        buf.setLength(0); // Clear buffer

	        // 3. CHECK UNIQUENESS
	        // If we have seen this geometry before (even flipped or rotated), skip it.
	        if (processedGeometry.contains(geomID)) {
	            continue;
	        }
	        processedGeometry.add(geomID);

	        // 4. WRITE MIRRORED PAIR
	        // We write the triangle EXACTLY as it appeared in the array (v1 v2 v3),
	        // and then its exact geometric mirror (v1 v3 v2).
	        
	        // Forward Face
	        buf.append("f ").append(v1).append(' ')
	           .append(v2).append(' ')
	           .append(v3).append('\n');
	        
	        // Backward Face (Swap last two to flip normal)
	        buf.append("f ").append(v1).append(' ')
	           .append(v3).append(' ')
	           .append(v2).append('\n');
	           
	        objWriter.write(buf.toString());
	        buf.setLength(0);
	    }
	    objWriter.write('\n');
	}
	
	
	
	/**
	 * Write faces for point meshes.
	 */
	static void writePointFaces(int[] indices, Writer objWriter, String name)
						throws IOException {
		final StringBuffer buf = new StringBuffer(100);
		objWriter.write("s 1\n");
		for (int i = 0; i < indices.length; i++) {
			buf.append('f').append(' ')
				.append(indices[i]).append('\n');
			objWriter.write(buf.toString());
			buf.setLength(0);
		}
		objWriter.write('\n');
	}

	/**
	 * Write faces for quad meshes.
	 */
	static void writeQuadFaces(int[] indices, Writer objWriter, String name)
						throws IOException {
		if(indices.length % 4 != 0)
			throw new IllegalArgumentException(
				"list of quads not multiple of 4: " + name);
		final StringBuffer buf = new StringBuffer(100);
		objWriter.write("s 1\n");
		for (int i = 0; i < indices.length; i += 4) {
			buf.append('f').append(' ')
				.append(indices[i]).append(' ')
				.append(indices[i+1]).append(' ')
				.append(indices[i+2]).append(' ')
				.append(indices[i+3]).append('\n');
			objWriter.write(buf.toString());
			buf.setLength(0);
		}
		objWriter.write('\n');
	}

	/**
	 * Write faces for pairwise line meshes, ie the points addressed
	 * by the indices array are arranged in pairs each specifying 
	 * one line segment.
	 */
	static void writePairwiseLineFaces(int[] indices, Writer objWriter, String name)
						throws IOException {
		if(indices.length % 2 != 0)
			throw new IllegalArgumentException(
				"list of lines not multiple of 2: " + name);
		final StringBuffer buf = new StringBuffer(100);
		objWriter.write("s 1\n");
		for (int i = 0; i < indices.length; i += 2) {
			buf.append('f').append(' ')
				.append(indices[i]).append(' ')
				.append(indices[i+1]).append('\n');
			objWriter.write(buf.toString());
			buf.setLength(0);
		}
		objWriter.write('\n');
	}

	/**
	 * Write faces for continuous line meshes, ie the points addressed
	 * by the indices array represent a continuous line.
	 */
	static void writeContinuousLineFaces(int[] indices, Writer objWriter, String name)
						throws IOException {
		final StringBuffer buf = new StringBuffer(100);
		objWriter.write("s 1\n");
		for (int i = 0; i < indices.length - 1; i++) {
			buf.append('f').append(' ')
				.append(indices[i]).append(' ')
				.append(indices[i+1]).append('\n');
			objWriter.write(buf.toString());
			buf.setLength(0);
		}
		objWriter.write('\n');
	}

	/** A Material, but avoiding name colisions. Not thread-safe. */
	static private int mat_index = 1;
	static private class Mtl {

		private Color4f col;
		String name;

		Mtl(float alpha, Color3f c) {
			this.col = new Color4f(c.x, c.y, c.z, alpha);
			name = "mat_" + mat_index;
			mat_index++;
		}

		Mtl(float alpha, float R, float G, float B) {
			this.col = new Color4f(R, G, B, alpha);
			name = "mat_" + mat_index;
			mat_index++;
		}

		public boolean equals(Object ob) {
			if (ob instanceof Mtl) {
				return this.col == ((Mtl)ob).col;
			}
			return false;
		}

		public int hashCode() {
			return col.hashCode();
		}

		void fill(StringBuffer sb) {
			sb.append("\nnewmtl ").append(name).append('\n')
			  .append("Ns 96.078431\n")
			  .append("Ka 0.0 0.0 0.0\n")
			  .append("Kd ").append(col.x).append(' ')
			  // this is INCORRECT but I'll figure out the
			  // conversion later
			  .append(col.y).append(' ').append(col.z).append('\n')
			  .append("Ks 0.5 0.5 0.5\n")
			  .append("Ni 1.0\n")
			  .append("d ").append(col.w).append('\n')
			  .append("illum 2\n\n");
		}
	}

	/** Utility method to encode text data in 8859_1. */
	static public boolean saveToFile(final File f, final String data)
							throws IOException {
		if (null == f) return false;
		OutputStreamWriter dos = new OutputStreamWriter(
			new BufferedOutputStream(
			 // encoding in Latin 1 (for macosx not to mess around
			new FileOutputStream(f), data.length()), "8859_1");
		dos.write(data, 0, data.length());
		dos.flush();
		return true;
	}
}