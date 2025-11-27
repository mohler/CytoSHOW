package customnode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lwjgl.util.meshoptimizer.MeshOptimizer;
import org.lwjgl.system.MemoryUtil;
import ij.IJ;

import javax.vecmath.Color4f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Point3f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.IntBuffer;
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
		IJ.log("DEBUG: [GltfMeshImporter] Loading " + glbFile.getName());
		List<ImportedMesh> result = new ArrayList<>();
		List<ByteBuffer> binaryBuffers = new ArrayList<>();
		JsonNode root = null;

		// --- 1. RAW BINARY PARSING ---
		try (FileInputStream fis = new FileInputStream(glbFile)) {
			byte[] header = new byte[12];
			readFully(fis, header);
			ByteBuffer h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			if (h.getInt() != 0x46546C67) throw new IOException("Invalid GLB Magic");
			h.getInt(); 
			long fileLength = h.getInt();

			long bytesRead = 12;
			while (bytesRead < fileLength) {
				byte[] ch = new byte[8];
				if (fis.read(ch) < 8) break;
				bytesRead += 8;
				ByteBuffer chb = ByteBuffer.wrap(ch).order(ByteOrder.LITTLE_ENDIAN);
				int chunkLen = chb.getInt();
				int chunkType = chb.getInt();

				byte[] payload = new byte[chunkLen];
				readFully(fis, payload);
				bytesRead += chunkLen;

				if (chunkType == 0x4E4F534A) { // JSON
					root = new ObjectMapper().readTree(payload);
				} else if (chunkType == 0x004E4942) { // BIN
					ByteBuffer bin = ByteBuffer.allocateDirect(chunkLen);
					bin.put(payload);
					bin.flip();
					binaryBuffers.add(bin);
					IJ.log("DEBUG: Binary Chunk Stored (" + chunkLen + " bytes)");
				}
			}
		} catch (Exception e) {
			throw new IOException("GLB Parse Error", e);
		}

		if (root == null || binaryBuffers.isEmpty()) return result;

		// --- 2. TRAVERSE SCENE GRAPH (For Names Only) ---
		JsonNode scenes = root.get("scenes");
		JsonNode nodes = root.get("nodes");

		if (nodes == null) return result;

		if (scenes != null && scenes.size() > 0) {
			int sceneIndex = root.has("scene") ? root.get("scene").asInt() : 0;
			JsonNode scene = scenes.get(sceneIndex);
			JsonNode sceneNodes = scene.get("nodes");

			if (sceneNodes != null) {
				// Use Identity Matrix (No Transforms)
				Matrix4f identity = new Matrix4f();
				identity.setIdentity();

				for (JsonNode nodeIdx : sceneNodes) {
					processNode(nodeIdx.asInt(), identity, root, nodes, binaryBuffers, result);
				}
			}
		} else {
			Matrix4f identity = new Matrix4f();
			identity.setIdentity();
			for (int i = 0; i < nodes.size(); i++) {
				processNode(i, identity, root, nodes, binaryBuffers, result);
			}
		}

		return result;
	}

	private static void processNode(int nodeIndex, Matrix4f parentTransform, 
			JsonNode root, JsonNode nodesArray, 
			List<ByteBuffer> buffers, List<ImportedMesh> result) {

		JsonNode node = nodesArray.get(nodeIndex);
		String name = node.has("name") ? node.get("name").asText() : "node_" + nodeIndex;

		// --- RESTORE TRANSFORM LOGIC ---
		Matrix4f localTransform = new Matrix4f();
		localTransform.setIdentity();

		if (node.has("matrix")) {
			// Explicit 4x4 Matrix
			float[] m = new float[16];
			JsonNode matNode = node.get("matrix");
			for(int i=0; i<16; i++) m[i] = (float) matNode.get(i).asDouble();
			localTransform.set(m);
			IJ.log("DEBUG: Node '" + name + "' has explicit Matrix.");
		} else {
			// TRS (Translation, Rotation, Scale)
			if (node.has("translation")) {
				JsonNode t = node.get("translation");
				Vector3f trans = new Vector3f((float)t.get(0).asDouble(), (float)t.get(1).asDouble(), (float)t.get(2).asDouble());
				Matrix4f tMat = new Matrix4f();
				tMat.set(trans);
				localTransform.mul(tMat); // T * ...
			}
			if (node.has("rotation")) {
				JsonNode r = node.get("rotation");
				Quat4f quat = new Quat4f((float)r.get(0).asDouble(), (float)r.get(1).asDouble(), (float)r.get(2).asDouble(), (float)r.get(3).asDouble());
				Matrix4f rMat = new Matrix4f();
				rMat.set(quat);
				localTransform.mul(rMat); // T * R * ...
			}
			if (node.has("scale")) {
				JsonNode s = node.get("scale");
				float sx = (float)s.get(0).asDouble();
				float sy = (float)s.get(1).asDouble();
				float sz = (float)s.get(2).asDouble();

				// Debug log to catch the Z-distortion culprit
				if (sx != 1.0f || sy != 1.0f || sz != 1.0f) {
					IJ.log("DEBUG: Node '" + name + "' has Scale: [" + sx + ", " + sy + ", " + sz + "]");
				}

				Matrix4f sMat = new Matrix4f();
				sMat.set(new float[] {
						sx, 0, 0, 0,
						0, sy, 0, 0,
						0, 0, sz, 0,
						0, 0, 0, 1
				});
				localTransform.mul(sMat); // T * R * S
			}
		}

		// Combine with Parent
		Matrix4f globalTransform = new Matrix4f(parentTransform);
		globalTransform.mul(localTransform);

		// Process Mesh
		if (node.has("mesh")) {
			int meshIndex = node.get("mesh").asInt();
			JsonNode mesh = root.get("meshes").get(meshIndex);

			// Name Fallback
			String finalName = name;
			if ((finalName.isEmpty() || finalName.startsWith("node_")) && mesh.has("name")) {
				finalName = mesh.get("name").asText();
			}
			if (finalName.isEmpty()) finalName = "mesh_" + meshIndex;

			IJ.log("DEBUG: Processing '" + finalName + "' (Mesh " + meshIndex + ")");
			processMeshGeometry(mesh, globalTransform, finalName, root, buffers, result);
		}

		// Recurse
		if (node.has("children")) {
			for (JsonNode childIdx : node.get("children")) {
				processNode(childIdx.asInt(), globalTransform, root, nodesArray, buffers, result);
			}
		}
	}
	private static void processMeshGeometry(JsonNode mesh, Matrix4f transform, String name, 
			JsonNode root, List<ByteBuffer> buffers, List<ImportedMesh> result) {
		if (mesh.has("primitives")) {
			for (JsonNode primitive : mesh.get("primitives")) {
				if (primitive.has("attributes") && primitive.get("attributes").has("POSITION")) {
					int posId = primitive.get("attributes").get("POSITION").asInt();

					// Decode Vertices
					List<Point3f> localVerts = decodeAccessorPoints(posId, root, buffers);

					// Decode Indices
					List<Point3f> triangles = new ArrayList<>();
					if (primitive.has("indices")) {
						int indId = primitive.get("indices").asInt();
						int[] indices = decodeAccessorIndices(indId, root, buffers);
						for (int idx : indices) {
							if (idx >= 0 && idx < localVerts.size()) {
								triangles.add(localVerts.get(idx));
							}
						}
					} else {
						triangles = localVerts;
					}

					if (!triangles.isEmpty()) {
						while (triangles.size() % 3 != 0) triangles.remove(triangles.size()-1);

						// Apply Transform (Which is now Identity -> No Op)
						// We construct new points just to be safe and consistent
						List<Point3f> worldVerts = new ArrayList<>(triangles.size());
						for (Point3f p : triangles) {
							// Pure Coordinate Copy
							worldVerts.add(new Point3f(p)); 
						}

						Color4f color = extractColor(primitive, root);
						result.add(new ImportedMesh(worldVerts, color, name));
						IJ.log("DEBUG: Added '" + name + "' (" + worldVerts.size() + " verts)");
					}
				}
			}
		}
	}

	// ... (decodeAccessorPoints, decodeAccessorIndices, getDecodedBuffer, extractColor, readFully are UNCHANGED) ...

	private static List<Point3f> decodeAccessorPoints(int accessorId, JsonNode root, List<ByteBuffer> buffers) {
		List<Point3f> points = new ArrayList<>();
		ByteBuffer raw = getDecodedBuffer(accessorId, root, buffers);
		if (raw != null) {
			JsonNode accessor = root.get("accessors").get(accessorId);
			int count = accessor.get("count").asInt();
			int stride = raw.capacity() / count;
			raw.order(ByteOrder.LITTLE_ENDIAN);

			if (stride >= 12) { 
				FloatBuffer fb = raw.asFloatBuffer();
				for (int i = 0; i < count; i++) points.add(new Point3f(fb.get(), fb.get(), fb.get()));
			} else if (stride >= 8) { 
				ShortBuffer sb = raw.asShortBuffer();
				float[] min = new float[3];
				float[] max = new float[3];
				if (accessor.has("min")) for(int k=0;k<3;k++) min[k] = (float) accessor.get("min").get(k).asDouble();
				if (accessor.has("max")) for(int k=0;k<3;k++) max[k] = (float) accessor.get("max").get(k).asDouble();

				float rangeX = max[0] - min[0];
				float rangeY = max[1] - min[1];
				float rangeZ = max[2] - min[2];

				for (int i = 0; i < count; i++) {
					int sx = sb.get() & 0xFFFF; int sy = sb.get() & 0xFFFF; int sz = sb.get() & 0xFFFF; sb.get();
					float px = (sx / 65535.0f) * rangeX + min[0];
					float py = (sy / 65535.0f) * rangeY + min[1];
					float pz = (sz / 65535.0f) * rangeZ + min[2];
					points.add(new Point3f(px, py, pz));
				}
			}
		}
		return points;
	}

	private static int[] decodeAccessorIndices(int accessorId, JsonNode root, List<ByteBuffer> buffers) {
		ByteBuffer raw = getDecodedBuffer(accessorId, root, buffers);
		if (raw == null) return new int[0];
		JsonNode accessor = root.get("accessors").get(accessorId);
		int count = accessor.get("count").asInt();
		int[] indices = new int[count];
		int stride = raw.capacity() / count;
		raw.order(ByteOrder.LITTLE_ENDIAN);
		if (stride == 4) { 
			IntBuffer ib = raw.asIntBuffer();
			for (int i = 0; i < count; i++) indices[i] = ib.get();
		} else if (stride == 2) { 
			ShortBuffer sb = raw.asShortBuffer();
			for (int i = 0; i < count; i++) indices[i] = sb.get() & 0xFFFF;
		} else if (stride == 1) { 
			for (int i = 0; i < count; i++) indices[i] = raw.get() & 0xFF;
		}
		return indices;
	}

	private static ByteBuffer getDecodedBuffer(int accessorId, JsonNode root, List<ByteBuffer> buffers) {
		try {
			JsonNode accessor = root.get("accessors").get(accessorId);
			int bufferViewId = accessor.get("bufferView").asInt();
			JsonNode bufferView = root.get("bufferViews").get(bufferViewId);
			int bufferIdx = bufferView.has("buffer") ? bufferView.get("buffer").asInt() : 0;
			if (bufferIdx >= buffers.size()) bufferIdx = 0; 
			ByteBuffer mainBuffer = buffers.get(bufferIdx);

			if (bufferView.has("extensions") && bufferView.get("extensions").has("EXT_meshopt_compression")) {
				JsonNode ext = bufferView.get("extensions").get("EXT_meshopt_compression");
				int bvOffset = ext.get("byteOffset").asInt();
				int bvLength = ext.get("byteLength").asInt();
				if (bvOffset + bvLength > mainBuffer.capacity()) {
					IJ.log("ERROR: Meshopt Overflow"); return null;
				}
				mainBuffer.clear(); mainBuffer.position(bvOffset); mainBuffer.limit(bvOffset + bvLength);
				ByteBuffer compressedData = mainBuffer.slice(); compressedData.order(ByteOrder.LITTLE_ENDIAN);
				int count = ext.get("count").asInt();
				int byteStride = ext.get("byteStride").asInt();
				String mode = ext.get("mode").asText();
				ByteBuffer decoded = MemoryUtil.memAlloc(count * byteStride);
				int result = -1;
				if ("ATTRIBUTES".equals(mode)) result = MeshOptimizer.meshopt_decodeVertexBuffer(decoded, count, byteStride, compressedData);
				else if ("TRIANGLES".equals(mode)) result = MeshOptimizer.meshopt_decodeIndexBuffer(decoded, count, byteStride, compressedData);
				else if ("INDICES".equals(mode)) result = MeshOptimizer.meshopt_decodeIndexSequence(decoded, count, byteStride, compressedData);

				if (result == 0) { decoded.order(ByteOrder.LITTLE_ENDIAN); return decoded; }
				MemoryUtil.memFree(decoded); return null;
			}
			int bvOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").asInt() : 0;
			int bvLength = bufferView.get("byteLength").asInt();
			if (bvOffset + bvLength > mainBuffer.capacity()) return null;
			mainBuffer.clear(); mainBuffer.position(bvOffset); mainBuffer.limit(bvOffset + bvLength);
			ByteBuffer raw = mainBuffer.slice(); raw.order(ByteOrder.LITTLE_ENDIAN);
			return raw;
		} catch (Exception e) { e.printStackTrace(); }
		return null;
	}

	private static Color4f extractColor(JsonNode jsonPrimitive, JsonNode root) {
		Color4f c = new Color4f(1f, 1f, 1f, 1f); 
		try {
			if (jsonPrimitive.has("material")) {
				int matIndex = jsonPrimitive.get("material").asInt();
				JsonNode materials = root.get("materials");
				if (materials != null && materials.has(matIndex)) {
					JsonNode mat = materials.get(matIndex);
					if (mat.has("pbrMetallicRoughness")) {
						JsonNode pbr = mat.get("pbrMetallicRoughness");
						if (pbr.has("baseColorFactor")) {
							JsonNode arr = pbr.get("baseColorFactor");
							if (arr.isArray() && arr.size() >= 3) {
								c.x = (float) arr.get(0).asDouble(); c.y = (float) arr.get(1).asDouble(); c.z = (float) arr.get(2).asDouble();
								if (arr.size() > 3) c.w = (float) arr.get(3).asDouble();
							}
						}
					}
				}
			}
		} catch (Exception e) {}
		return c;
	}

	private static void readFully(InputStream in, byte[] b) throws IOException {
		int offset = 0;
		while (offset < b.length) {
			int count = in.read(b, offset, b.length - offset);
			if (count < 0) throw new IOException("Premature end of file");
			offset += count;
		}
	}
}