package customnode;

import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.io.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.lwjgl.util.meshoptimizer.MeshOptimizer;
import org.lwjgl.system.MemoryUtil;

import javax.vecmath.Color4f;
import javax.vecmath.Point3f;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class GltfMeshImporter {

    public static class ImportedMesh {
        public List<Point3f> vertices;
        public Color4f color;
        public String name;

        public ImportedMesh(List<Point3f> v, Color4f c, String n) {
            this.vertices = v;
            this.color = c;
            this.name = n;
        }
    }

    public static List<ImportedMesh> loadMeshes(File glbFile) throws IOException {
        List<ImportedMesh> allGeometries = new ArrayList<ImportedMesh>();

        // 1. Parse the Raw JSON Structure (Using Jackson)
        // We do this to access Materials and Extensions directly, bypassing JglTF API limits.
        JsonNode root = readJsonChunk(glbFile);
        if (root == null) throw new IOException("Could not parse GLB JSON chunk.");

        // 2. Load the Binary Data Buffers (Using JglTF)
        // JglTF is still the best way to handle the heavy binary blobs.
        GltfModelReader reader = new GltfModelReader();
        GltfModel gltfModel = reader.read(glbFile.toURI());
        
        List<AccessorModel> accessorModels = gltfModel.getAccessorModels();
        List<MeshModel> meshModels = gltfModel.getMeshModels();

        // 3. Iterate Meshes (Parallel Loop: JglTF Model + Raw JSON)
        // We assume JglTF parses the array in order, so index 'i' matches in both.
        JsonNode jsonMeshes = root.get("meshes");
        
        for (int i = 0; i < meshModels.size(); i++) {
            MeshModel meshModel = meshModels.get(i);
            JsonNode jsonMesh = (jsonMeshes != null) ? jsonMeshes.get(i) : null;
            
            List<MeshPrimitiveModel> primitives = meshModel.getMeshPrimitiveModels();
            
            for (int j = 0; j < primitives.size(); j++) {
                MeshPrimitiveModel primitive = primitives.get(j);
                JsonNode jsonPrimitive = (jsonMesh != null && jsonMesh.has("primitives")) 
                        ? jsonMesh.get("primitives").get(j) : null;
                
                AccessorModel positionAccessor = primitive.getAttributes().get("POSITION");
                
                if (positionAccessor != null) {
                    // --- Decode Geometry ---
                    // Use the JglTF Accessor to find the raw data
                    // Use the Raw JSON Root to find the Meshopt metadata
                    int accessorIndex = accessorModels.indexOf(positionAccessor);
                    List<Point3f> vertices = decodePositions(positionAccessor, accessorIndex, root);
                    
                    if (!vertices.isEmpty()) {
                        // --- Extract Color (From Raw JSON) ---
                        Color4f color = extractColor(jsonPrimitive, root);
                        
                        allGeometries.add(new ImportedMesh(vertices, color, meshModel.getName()));
                    }
                }
            }
        }
        return allGeometries;
    }

    private static Color4f extractColor(JsonNode jsonPrimitive, JsonNode root) {
        Color4f c = new Color4f(1f, 1f, 1f, 1f); // Default White
        
        if (jsonPrimitive != null && jsonPrimitive.has("material")) {
            int matIndex = jsonPrimitive.get("material").asInt();
            JsonNode materials = root.get("materials");
            
            if (materials != null && materials.has(matIndex)) {
                JsonNode mat = materials.get(matIndex);
                // Look for PBR base color
                if (mat.has("pbrMetallicRoughness")) {
                    JsonNode pbr = mat.get("pbrMetallicRoughness");
                    if (pbr.has("baseColorFactor")) {
                        JsonNode colorArray = pbr.get("baseColorFactor");
                        if (colorArray.isArray() && colorArray.size() >= 3) {
                            c.x = (float) colorArray.get(0).asDouble();
                            c.y = (float) colorArray.get(1).asDouble();
                            c.z = (float) colorArray.get(2).asDouble();
                            if (colorArray.size() > 3) {
                                c.w = (float) colorArray.get(3).asDouble();
                            }
                        }
                    }
                }
            }
        }
        return c;
    }

    private static List<Point3f> decodePositions(AccessorModel accessor, int accessorIndex, JsonNode root) {
        List<Point3f> points = new ArrayList<Point3f>();
        BufferViewModel bufferView = accessor.getBufferViewModel();
        ByteBuffer rawData = bufferView.getBufferViewData(); 
        
        // Find extension metadata for this accessor
        // Map: Accessor -> BufferView -> Extension
        JsonNode jsonAccessor = root.get("accessors").get(accessorIndex);
        
        if (jsonAccessor != null && jsonAccessor.has("bufferView")) {
            int bvIndex = jsonAccessor.get("bufferView").asInt();
            JsonNode jsonBufferView = root.get("bufferViews").get(bvIndex);
            
            if (jsonBufferView != null && jsonBufferView.has("extensions") 
                    && jsonBufferView.get("extensions").has("EXT_meshopt_compression")) {
                
                // --- MESHOPT DECODE ---
                try {
                    JsonNode ext = jsonBufferView.get("extensions").get("EXT_meshopt_compression");
                    
                    int count = ext.get("count").asInt();
                    int byteStride = ext.get("byteStride").asInt();
                    String mode = ext.get("mode").asText();
                    
                    int outputSize = count * byteStride;
                    ByteBuffer decodedBuffer = MemoryUtil.memAlloc(outputSize);
                    ByteBuffer inputBuffer = MemoryUtil.memAlloc(rawData.remaining());
                    inputBuffer.put(rawData);
                    inputBuffer.flip();

                    int result = -1;
                    if ("ATTRIBUTES".equals(mode)) {
                        result = MeshOptimizer.meshopt_decodeVertexBuffer(decodedBuffer, count, byteStride, inputBuffer);
                    } else if ("TRIANGLES".equals(mode)) {
                        result = MeshOptimizer.meshopt_decodeIndexBuffer(decodedBuffer, count, 2, inputBuffer);
                    } else if ("INDICES".equals(mode)) {
                        result = MeshOptimizer.meshopt_decodeIndexSequence(decodedBuffer, count, 2, inputBuffer);
                    }

                    if (result == 0) {
                        FloatBuffer fb = decodedBuffer.asFloatBuffer();
                        for (int i = 0; i < count; i++) {
                            points.add(new Point3f(fb.get(), fb.get(), fb.get()));
                        }
                    }

                    MemoryUtil.memFree(decodedBuffer);
                    MemoryUtil.memFree(inputBuffer);
                    return points;

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        // --- STANDARD FALLBACK ---
        AccessorData accessorData = accessor.getAccessorData();
        if (accessorData instanceof AccessorFloatData) {
            AccessorFloatData floatData = (AccessorFloatData) accessorData;
            for (int i = 0; i < floatData.getNumElements(); i++) {
                points.add(new Point3f(floatData.get(i, 0), floatData.get(i, 1), floatData.get(i, 2)));
            }
        }

        return points;
    }

    private static JsonNode readJsonChunk(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            long skipped = fis.skip(12); // Skip Header
            if (skipped != 12) return null;

            byte[] chunkHeader = new byte[8];
            if (fis.read(chunkHeader) != 8) return null;
            
            ByteBuffer wrap = ByteBuffer.wrap(chunkHeader).order(ByteOrder.LITTLE_ENDIAN);
            int chunkLength = wrap.getInt();
            int chunkType = wrap.getInt();

            if (chunkType == 0x4E4F534A) { // JSON
                byte[] jsonBytes = new byte[chunkLength];
                fis.read(jsonBytes);
                return new ObjectMapper().readTree(jsonBytes);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}