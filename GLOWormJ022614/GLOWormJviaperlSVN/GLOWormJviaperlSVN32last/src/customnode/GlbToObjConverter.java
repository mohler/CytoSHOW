package customnode;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GlbToObjConverter {

    private static final String MTL_FILENAME = "extracted_colors.mtl";
    private static final int CHUNK_JSON = 0x4E4F534A;
    private static final int CHUNK_BIN = 0x004E4942;

    public void convert(File glbFile, File outputDir, File decoderExe) throws Exception {
        if (!decoderExe.exists() || !decoderExe.canExecute()) {
            throw new FileNotFoundException("Draco decoder binary not found/executable: " + decoderExe);
        }

        try (FileInputStream fis = new FileInputStream(glbFile);
             DataInputStream dis = new DataInputStream(new BufferedInputStream(fis))) {

            // 1. Header
            long magic = readUnsignedIntLE(dis);
            if (magic != 0x46546C67L) throw new IOException("Not a GLB file.");
            readUnsignedIntLE(dis); // version
            readUnsignedIntLE(dis); // length

            // 2. JSON
            long jsonLen = readUnsignedIntLE(dis);
            if (readUnsignedIntLE(dis) != CHUNK_JSON) throw new IOException("Bad Chunk 0");
            byte[] jsonBytes = new byte[(int) jsonLen];
            dis.readFully(jsonBytes);
            JsonObject gltf = new Gson().fromJson(new String(jsonBytes, StandardCharsets.UTF_8), JsonObject.class);

            // 3. BIN
            long binLen = readUnsignedIntLE(dis);
            if (readUnsignedIntLE(dis) != CHUNK_BIN) throw new IOException("Bad Chunk 1");
            byte[] binData = new byte[(int) binLen];
            dis.readFully(binData);

            // 4. Generate Materials
            Map<Integer, String> materialMap = generateMtlFile(gltf, outputDir);

            // 5. Process Meshes
            processMeshes(gltf, binData, outputDir, decoderExe, materialMap);
        }
    }

    private void processMeshes(JsonObject gltf, byte[] binData, File outputDir, File decoderExe, Map<Integer, String> matMap) throws Exception {
        if (!gltf.has("meshes")) return;

        JsonArray meshes = gltf.getAsJsonArray("meshes");
        JsonArray bufferViews = gltf.getAsJsonArray("bufferViews");

        for (int i = 0; i < meshes.size(); i++) {
            JsonObject mesh = meshes.get(i).getAsJsonObject();
            String rawName = mesh.has("name") ? mesh.get("name").getAsString() : "mesh-" + i;
            String cleanName = cleanName(rawName);

            JsonArray primitives = mesh.getAsJsonArray("primitives");
            for (int p = 0; p < primitives.size(); p++) {
                JsonObject prim = primitives.get(p).getAsJsonObject();

                if (prim.has("extensions") && prim.getAsJsonObject("extensions").has("KHR_draco_mesh_compression")) {
                    JsonObject draco = prim.getAsJsonObject("extensions").getAsJsonObject("KHR_draco_mesh_compression");
                    JsonObject bufferView = bufferViews.get(draco.get("bufferView").getAsInt()).getAsJsonObject();
                    
                    int offset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
                    int len = bufferView.get("byteLength").getAsInt();

                    // Temp Files
                    File tempDrc = File.createTempFile("draco", ".drc");
                    File tempObj = File.createTempFile("decoded", ".obj");

                    try (FileOutputStream fos = new FileOutputStream(tempDrc)) {
                        fos.write(binData, offset, len);
                    }

                    // Decode
                    runDecoder(decoderExe, tempDrc, tempObj);

                    // Reconstruct
                    String suffix = (primitives.size() > 1) ? "_p" + p : "";
                    
                    // FIX: Append "_0" to force Timepoint 0 in ImageJ 3D Viewer
                    File finalObj = new File(outputDir, cleanName + suffix + "_0.obj");

                    int matIdx = prim.has("material") ? prim.get("material").getAsInt() : -1;
                    writeFinalObj(finalObj, tempObj, cleanName, matMap.getOrDefault(matIdx, "default"));

                    // Clean
                    tempDrc.delete();
                    tempObj.delete();
                }
            }
        }
    }

    private void writeFinalObj(File finalFile, File tempObj, String name, String mat) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(finalFile));
             BufferedReader br = new BufferedReader(new FileReader(tempObj))) {
            pw.println("mtllib " + MTL_FILENAME);
            pw.println("g " + name);
            pw.println("usemtl " + mat);
            String line;
            while ((line = br.readLine()) != null) pw.println(line);
        }
    }

    private Map<Integer, String> generateMtlFile(JsonObject gltf, File dir) throws IOException {
        Map<Integer, String> map = new HashMap<>();
        if (!gltf.has("materials")) return map;
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(new File(dir, MTL_FILENAME)))) {
            JsonArray mats = gltf.getAsJsonArray("materials");
            for (int i = 0; i < mats.size(); i++) {
                JsonObject m = mats.get(i).getAsJsonObject();
                String name = cleanName(m.has("name") ? m.get("name").getAsString() : "mat-" + i);
                map.put(i, name);
                
                pw.println("\nnewmtl " + name);
                float[] c = {1f, 1f, 1f, 1f};
                if (m.has("pbrMetallicRoughness")) {
                    JsonObject pbr = m.getAsJsonObject("pbrMetallicRoughness");
                    if (pbr.has("baseColorFactor")) {
                        JsonArray ja = pbr.getAsJsonArray("baseColorFactor");
                        for(int k=0; k<4; k++) c[k] = ja.get(k).getAsFloat();
                    }
                }
                pw.printf("Kd %.4f %.4f %.4f%n", c[0], c[1], c[2]); // Diffuse
                pw.printf("d %.4f%n", c[3]); // Alpha
                pw.println("illum 2");
            }
        }
        return map;
    }

    private void runDecoder(File exe, File in, File out) throws Exception {
        Process p = new ProcessBuilder(exe.getAbsolutePath(), "-i", in.getAbsolutePath(), "-o", out.getAbsolutePath())
                .redirectErrorStream(true).start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            while (br.readLine() != null) {} // Consume output
        }
        if (p.waitFor() != 0) throw new IOException("Decoder failed");
    }

    private long readUnsignedIntLE(DataInputStream dis) throws IOException {
        byte[] b = new byte[4]; dis.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    private String cleanName(String s) {
        return s == null ? "unnamed" : s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}