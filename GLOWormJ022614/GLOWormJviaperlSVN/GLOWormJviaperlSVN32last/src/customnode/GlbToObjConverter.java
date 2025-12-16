package customnode;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ij.IJ;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GlbToObjConverter {

    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public Map<String, CustomMesh> convert(File inputFile, File decoderExe) throws Exception {
        if (!decoderExe.exists() || !decoderExe.canExecute()) {
            throw new FileNotFoundException("Draco decoder binary not found/executable: " + decoderExe);
        }

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFile))) {
            bis.mark(4);
            byte[] header = new byte[4];
            int read = bis.read(header);
            bis.reset(); 

            boolean isBinary = (read == 4 && header[0] == 'g' && header[1] == 'l' && header[2] == 'T' && header[3] == 'F');

            JsonObject rootJson;
            Map<Integer, byte[]> bufferMap = new HashMap<>();

            if (isBinary) {
                IJ.log("Parsing as Binary GLB...");
                rootJson = parseBinaryGLB(bis, bufferMap);
            } else {
                IJ.log("Parsing as Text GLTF...");
                rootJson = parseTextGLTF(bis, bufferMap);
            }

            MaterialData matData = generateMtlData(rootJson);

            return loadMeshesParallel(rootJson, bufferMap, decoderExe, matData, inputFile);
        }
    }

    private Map<String, CustomMesh> loadMeshesParallel(JsonObject gltf, Map<Integer, byte[]> bufferMap, File decoderExe, MaterialData matData, File originalFile) throws Exception {
        Map<String, CustomMesh> collectedMeshes = Collections.synchronizedMap(new LinkedHashMap<>());
        
        if (!gltf.has("meshes")) return collectedMeshes;

        JsonArray meshes = gltf.getAsJsonArray("meshes");
        JsonArray bufferViews = gltf.getAsJsonArray("bufferViews");
        JsonArray accessors = gltf.has("accessors") ? gltf.getAsJsonArray("accessors") : new JsonArray(); // Needed for Standard Path

        int threads = 4;
        int cores = Runtime.getRuntime().availableProcessors();
        threads = cores/4;
        IJ.log("Streaming " + meshes.size() + " meshes using " + threads + " threads...");
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < meshes.size(); i++) {
            final int meshIndex = i;
            tasks.add(() -> {
                try {
                    JsonObject mesh = meshes.get(meshIndex).getAsJsonObject();
                    String rawName = mesh.has("name") ? mesh.get("name").getAsString() : "mesh-" + meshIndex;
                    String cleanName = cleanName(rawName);

                    JsonArray primitives = mesh.getAsJsonArray("primitives");
                    for (int p = 0; p < primitives.size(); p++) {
                    	JsonObject prim = primitives.get(p).getAsJsonObject();

                    	int matIdx = prim.has("material") ? prim.get("material").getAsInt() : -1;
                        String matName = matData.indexToName.getOrDefault(matIdx, "default");
                        
                        // --- BRANCH 1: DRACO COMPRESSED ---
                    	if (prim.has("extensions") && prim.getAsJsonObject("extensions").has("KHR_draco_mesh_compression")) {
                    		JsonObject draco = prim.getAsJsonObject("extensions").getAsJsonObject("KHR_draco_mesh_compression");

                    		int bufferViewIndex = draco.get("bufferView").getAsInt();
                    		JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();
                    		int bufferIndex = bufferView.has("buffer") ? bufferView.get("buffer").getAsInt() : 0;
                    		byte[] binData = bufferMap.get(bufferIndex);

                    		if (binData == null) continue;

                    		int offset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
                    		int len = bufferView.get("byteLength").getAsInt();

                    		File tempDrc = File.createTempFile("draco_in_" + meshIndex + "_", ".drc");
                    		try (FileOutputStream fos = new FileOutputStream(tempDrc)) {
                    			fos.write(binData, offset, len);
                    		}

                    		InputStream objDataStream = null;
                    		File tempObjCleanup = null;
                    		Path pipeCleanup = null;
                    		Process processToWait = null;

                    		try {
                    			if (IS_WINDOWS) {
                    				File tempObj = File.createTempFile("draco_out_" + meshIndex + "_", ".obj");
                    				tempObjCleanup = tempObj;
                    				ProcessBuilder pb = new ProcessBuilder(decoderExe.getAbsolutePath(), "-i", tempDrc.getAbsolutePath(), "-o", tempObj.getAbsolutePath());
                    				pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    				if (pb.start().waitFor() != 0) throw new IOException("Decoder failed");
                    				objDataStream = new FileInputStream(tempObj);
                    			} else {
                    				String pipeName = "pipe_" + UUID.randomUUID() + ".obj";
                    				Path pipePath = Paths.get("/tmp", pipeName);
                    				pipeCleanup = pipePath;
                    				new ProcessBuilder("mkfifo", pipePath.toString()).start().waitFor();
                    				ProcessBuilder pb = new ProcessBuilder(decoderExe.getAbsolutePath(), "-i", tempDrc.getAbsolutePath(), "-o", pipePath.toString());
                    				pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    				processToWait = pb.start();
                    				objDataStream = new FileInputStream(pipePath.toFile());
                    			}

                    			if (objDataStream != null) {
                    				String objHeader = "g " + cleanName + "\nmtllib virtual_materials.mtl\nusemtl " + matName + "\n";
                    				InputStream headerStream = new ByteArrayInputStream(objHeader.getBytes(StandardCharsets.UTF_8));
                    				InputStream finalObjStream = new SequenceInputStream(headerStream, objDataStream);
                    				InputStream mtlStream = new ByteArrayInputStream(matData.mtlContent.getBytes(StandardCharsets.UTF_8));

                    				WavefrontLoader loader = new WavefrontLoader();
//                    				String safeProxyName = originalFile.getName() + ".obj";
                    				String safeProxyName = originalFile.getAbsolutePath() + ".obj";
                    				Map<String, CustomMesh> loaded = loader.loadObjs(safeProxyName, new InputStream[]{ finalObjStream, mtlStream }, false);
                    				if (loaded != null) collectedMeshes.putAll(loaded);
                    			}
                    			if (processToWait != null) processToWait.waitFor();

                    		} finally {
                    			if (objDataStream != null) try { objDataStream.close(); } catch (IOException e) {}
                    			tempDrc.delete();
                    			if (tempObjCleanup != null) tempObjCleanup.delete();
                    			if (pipeCleanup != null) {
                    				if (processToWait != null && processToWait.isAlive()) processToWait.destroy();
                    				Files.deleteIfExists(pipeCleanup);
                    			}
                    		}
                    	} 
                    	// --- BRANCH 2: STANDARD GLTF (Uncompressed) ---
                    	else {
                    	    JsonObject attributes = prim.getAsJsonObject("attributes");
                            if (attributes.has("POSITION")) {
                                int posAccIdx = attributes.get("POSITION").getAsInt();
                                float[] positions = readAccessorFloatVec3(accessors, bufferViews, bufferMap, posAccIdx);
                                
                                float[] normals = null;
                                if (attributes.has("NORMAL")) {
                                    normals = readAccessorFloatVec3(accessors, bufferViews, bufferMap, attributes.get("NORMAL").getAsInt());
                                }
                                
                                int[] indices = null;
                                if (prim.has("indices")) {
                                    indices = readAccessorIndices(accessors, bufferViews, bufferMap, prim.get("indices").getAsInt());
                                }
                                
                                // Build OBJ in memory
                                StringBuilder objSb = new StringBuilder();
                                objSb.append("g ").append(cleanName).append("\n");
                                objSb.append("mtllib virtual_materials.mtl\n");
                                objSb.append("usemtl ").append(matName).append("\n");
                                
                                for(int k=0; k<positions.length; k+=3) {
                                    objSb.append("v ").append(positions[k]).append(" ").append(positions[k+1]).append(" ").append(positions[k+2]).append("\n");
                                }
                                if (normals != null) {
                                    for(int k=0; k<normals.length; k+=3) {
                                        objSb.append("vn ").append(normals[k]).append(" ").append(normals[k+1]).append(" ").append(normals[k+2]).append("\n");
                                    }
                                }
                                if (indices != null) {
                                    for(int k=0; k<indices.length; k+=3) {
                                        int i1 = indices[k]+1; int i2 = indices[k+1]+1; int i3 = indices[k+2]+1;
                                        if (normals != null) {
                                            objSb.append("f ").append(i1).append("//").append(i1).append(" ")
                                                 .append(i2).append("//").append(i2).append(" ")
                                                 .append(i3).append("//").append(i3).append("\n");
                                        } else {
                                            objSb.append("f ").append(i1).append(" ").append(i2).append(" ").append(i3).append("\n");
                                        }
                                    }
                                } else {
                                    // No indices? Assume linear topology (rare in GLTF but possible)
                                    int count = positions.length / 3;
                                    for(int k=0; k<count; k+=3) {
                                        objSb.append("f ").append(k+1).append(" ").append(k+2).append(" ").append(k+3).append("\n");
                                    }
                                }
                                
                                InputStream objStream = new ByteArrayInputStream(objSb.toString().getBytes(StandardCharsets.UTF_8));
                                InputStream mtlStream = new ByteArrayInputStream(matData.mtlContent.getBytes(StandardCharsets.UTF_8));
                                WavefrontLoader loader = new WavefrontLoader();
                                Map<String, CustomMesh> loaded = loader.loadObjs(originalFile.getName() + ".obj", new InputStream[]{ objStream, mtlStream }, false);
                                if (loaded != null) collectedMeshes.putAll(loaded);
                            }
                    	}
                    }
                } catch (Exception e) {
                    IJ.log("Error loading mesh " + meshIndex + ": " + e.getMessage());
                    e.printStackTrace();
                }
                return null;
            });
        }

        executor.invokeAll(tasks);
        executor.shutdown();
        
        return collectedMeshes;
    }

    // --- ACCESSOR READERS FOR STANDARD GLTF ---

    private float[] readAccessorFloatVec3(JsonArray accessors, JsonArray bufferViews, Map<Integer, byte[]> bufferMap, int accIdx) {
        JsonObject acc = accessors.get(accIdx).getAsJsonObject();
        int count = acc.get("count").getAsInt();
        int bvIdx = acc.get("bufferView").getAsInt();
        int byteOffset = acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0;
        
        JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        int bufIdx = bv.has("buffer") ? bv.get("buffer").getAsInt() : 0;
        byte[] data = bufferMap.get(bufIdx);
        
        // GLTF uses Little Endian by default
        float[] res = new float[count * 3];
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int start = bvOffset + byteOffset;
        
        for(int i=0; i<count*3; i++) {
            res[i] = bb.getFloat(start + (i*4));
        }
        return res;
    }

    private int[] readAccessorIndices(JsonArray accessors, JsonArray bufferViews, Map<Integer, byte[]> bufferMap, int accIdx) {
        JsonObject acc = accessors.get(accIdx).getAsJsonObject();
        int count = acc.get("count").getAsInt();
        int compType = acc.get("componentType").getAsInt(); // 5121=UB, 5123=US, 5125=UI
        int bvIdx = acc.get("bufferView").getAsInt();
        int byteOffset = acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0;
        
        JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        int bufIdx = bv.has("buffer") ? bv.get("buffer").getAsInt() : 0;
        byte[] data = bufferMap.get(bufIdx);
        
        int[] res = new int[count];
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int start = bvOffset + byteOffset;
        
        for(int i=0; i<count; i++) {
            if (compType == 5123) { // UNSIGNED_SHORT
                res[i] = bb.getShort(start + (i*2)) & 0xFFFF;
            } else if (compType == 5125) { // UNSIGNED_INT
                res[i] = bb.getInt(start + (i*4));
            } else if (compType == 5121) { // UNSIGNED_BYTE
                res[i] = bb.get(start + i) & 0xFF;
            }
        }
        return res;
    }

    // --- EXISTING HELPERS ---
    private static class MaterialData {
        Map<Integer, String> indexToName = new HashMap<>();
        String mtlContent = "";
    }
    private MaterialData generateMtlData(JsonObject gltf) {
        MaterialData data = new MaterialData();
        StringBuilder sb = new StringBuilder();
        if (gltf.has("materials")) {
            JsonArray mats = gltf.getAsJsonArray("materials");
            for (int i = 0; i < mats.size(); i++) {
                JsonObject m = mats.get(i).getAsJsonObject();
                String name = cleanName(m.has("name") ? m.get("name").getAsString() : "mat-" + i);
                data.indexToName.put(i, name);
                sb.append("\nnewmtl ").append(name).append("\n");
                float[] c = {1f, 1f, 1f, 1f};
                if (m.has("pbrMetallicRoughness")) {
                    JsonObject pbr = m.getAsJsonObject("pbrMetallicRoughness");
                    if (pbr.has("baseColorFactor")) {
                        JsonArray ja = pbr.getAsJsonArray("baseColorFactor");
                        for(int k=0; k<4; k++) c[k] = ja.get(k).getAsFloat();
                    }
                }
                sb.append(String.format(Locale.US, "Kd %.4f %.4f %.4f%n", c[0], c[1], c[2]));
                sb.append(String.format(Locale.US, "d %.4f%n", c[3]));
                sb.append("illum 2\n");
            }
        } else {
            sb.append("newmtl default\nKd 0.8 0.8 0.8\nd 1.0\nillum 2\n");
            data.indexToName.put(-1, "default");
        }
        data.mtlContent = sb.toString();
        return data;
    }
    
    private JsonObject parseBinaryGLB(BufferedInputStream bis, Map<Integer, byte[]> bufferMap) throws IOException {
        DataInputStream dis = new DataInputStream(bis);
        readUnsignedIntLE(dis); readUnsignedIntLE(dis); readUnsignedIntLE(dis);
        long jsonLen = readUnsignedIntLE(dis);
        if (readUnsignedIntLE(dis) != CHUNK_JSON) throw new IOException("GLB Error: First chunk must be JSON.");
        byte[] jsonBytes = new byte[(int) jsonLen];
        dis.readFully(jsonBytes);
        JsonObject gltf = new Gson().fromJson(new String(jsonBytes, StandardCharsets.UTF_8), JsonObject.class);
        try {
            long binLen = readUnsignedIntLE(dis);
            if (readUnsignedIntLE(dis) == CHUNK_BIN) {
                byte[] binData = new byte[(int) binLen];
                dis.readFully(binData);
                bufferMap.put(0, binData);
            }
        } catch (EOFException e) {} 
        return gltf;
    }
    
    private JsonObject parseTextGLTF(BufferedInputStream bis, Map<Integer, byte[]> bufferMap) throws IOException {
        Scanner s = new Scanner(bis, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        String jsonStr = s.hasNext() ? s.next() : "";
        JsonObject gltf = new Gson().fromJson(jsonStr, JsonObject.class);
        if (gltf.has("buffers")) {
            JsonArray buffers = gltf.getAsJsonArray("buffers");
            for (int i = 0; i < buffers.size(); i++) {
                JsonObject buffer = buffers.get(i).getAsJsonObject();
                if (buffer.has("uri")) {
                    String uri = buffer.get("uri").getAsString();
                    if (uri.startsWith("data:")) {
                        String base64Payload = uri.substring(uri.indexOf(",") + 1);
                        bufferMap.put(i, Base64.getDecoder().decode(base64Payload));
                    }
                }
            }
        }
        return gltf;
    }
    private long readUnsignedIntLE(DataInputStream dis) throws IOException {
        byte[] b = new byte[4]; dis.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }
    private String cleanName(String s) {
        s= (s == null ? "unnamed" : s.replaceAll("[^a-zA-Z0-9_-]", "_"));
        if (!s.startsWith("mat_")) {
        	s = s.replaceAll("(.*)_.*", "$1");
        }
        return s;
    }
}