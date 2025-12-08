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

        // FIX 1: Hard cap threads at 4 to prevent RAM explosion from OBJ text
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

                    	if (prim.has("extensions") && prim.getAsJsonObject("extensions").has("KHR_draco_mesh_compression")) {
                    		JsonObject draco = prim.getAsJsonObject("extensions").getAsJsonObject("KHR_draco_mesh_compression");

                    		int bufferViewIndex = draco.get("bufferView").getAsInt();
                    		JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();
                    		int bufferIndex = bufferView.has("buffer") ? bufferView.get("buffer").getAsInt() : 0;
                    		byte[] binData = bufferMap.get(bufferIndex);

                    		if (binData == null) continue;

                    		int offset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
                    		int len = bufferView.get("byteLength").getAsInt();
                    		int matIdx = prim.has("material") ? prim.get("material").getAsInt() : -1;
                    		String matName = matData.indexToName.getOrDefault(matIdx, "default");

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
                    				// Keep existing Windows Temp File Logic
                    				File tempObj = File.createTempFile("draco_out_" + meshIndex + "_", ".obj");
                    				tempObjCleanup = tempObj;
                    				ProcessBuilder pb = new ProcessBuilder(decoderExe.getAbsolutePath(), "-i", tempDrc.getAbsolutePath(), "-o", tempObj.getAbsolutePath());
                    				pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    				if (pb.start().waitFor() != 0) throw new IOException("Decoder failed");

                    				// Open stream but DO NOT close it in a try-with-resources block
                    				objDataStream = new FileInputStream(tempObj);
                    			} else {
                    				// Keep existing Unix Named Pipe Logic
                    				String pipeName = "pipe_" + UUID.randomUUID() + ".obj";
                    				Path pipePath = Paths.get("/tmp", pipeName);
                    				pipeCleanup = pipePath;
                    				new ProcessBuilder("mkfifo", pipePath.toString()).start().waitFor();

                    				ProcessBuilder pb = new ProcessBuilder(decoderExe.getAbsolutePath(), "-i", tempDrc.getAbsolutePath(), "-o", pipePath.toString());
                    				pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    				processToWait = pb.start();

                    				// Open stream but DO NOT close it in a try-with-resources block
                    				objDataStream = new FileInputStream(pipePath.toFile());
                    			}

                    			if (objDataStream != null) {
                    				String objHeader = "g " + cleanName + "\nmtllib virtual_materials.mtl\nusemtl " + matName + "\n";
                    				InputStream headerStream = new ByteArrayInputStream(objHeader.getBytes(StandardCharsets.UTF_8));

                    				// FIX 2: Chain streams and pass to loader without closing 'objDataStream'
                    				InputStream finalObjStream = new SequenceInputStream(headerStream, objDataStream);
                    				InputStream mtlStream = new ByteArrayInputStream(matData.mtlContent.getBytes(StandardCharsets.UTF_8));

                    				WavefrontLoader loader = new WavefrontLoader();
                    				String safeProxyName = originalFile.getName() + ".obj";

                    				// Loader reads until EOF
                    				Map<String, CustomMesh> loaded = loader.loadObjs(safeProxyName, new InputStream[]{ finalObjStream, mtlStream }, false);

                    				if (loaded != null) collectedMeshes.putAll(loaded);
                    			}

                    			if (processToWait != null) processToWait.waitFor();

                    		} finally {
                    			// 1. Close the stream explicitly (Safe now!)
                    			if (objDataStream != null) {
                    				try { 
                    					objDataStream.close(); 
                    				} catch (IOException e) {
                    					// Ignore close errors
                    				}
                    			}

                    			// 2. Clean up temp files
                    			tempDrc.delete();
                    			if (tempObjCleanup != null) tempObjCleanup.delete();

                    			// 3. Clean up pipes (Unix only)
                    			if (pipeCleanup != null) {
                    				if (processToWait != null && processToWait.isAlive()) {
                    					processToWait.destroy();
                    				}
                    				Files.deleteIfExists(pipeCleanup);
                    			}
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

    // --- Helpers (MaterialData, Parsers) same as before ---
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
        return s == null ? "unnamed" : s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}