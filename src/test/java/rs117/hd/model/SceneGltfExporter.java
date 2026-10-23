package rs117.hd.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.opengl.uniforms.UBOGlobal;
import rs117.hd.opengl.uniforms.UniformBuffer;
import rs117.hd.scene.materials.Material;
import rs117.hd.scene.water_types.WaterType;

import static rs117.hd.utils.MathUtils.*;

/** Adds scene lighting and water properties unsupported by the bundled Assimp glTF exporter. */
@Slf4j
final class SceneGltfExporter {
	private static final String LIGHTS = "KHR_lights_punctual";

	static void embed(Path path, UBOGlobal ubo, Map<WaterType, Material> waterMaterials) throws IOException {
		boolean binary = path.toString().endsWith(".glb");
		if (!binary && !path.toString().endsWith(".gltf")) {
			log.warn("Scene lighting export is only supported for GLB and glTF");
			return;
		}
		// Only rewrite the JSON chunk; stream the potentially large binary payload unchanged.
		Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "scene-lighting-", ".tmp");
		try {
			try (var input = new RandomAccessFile(path.toFile(), "r");
				 var output = new RandomAccessFile(temporary.toFile(), "rw")) {
				int jsonLength;
				if (binary) {
					if (Integer.reverseBytes(input.readInt()) != 0x46546C67 || Integer.reverseBytes(input.readInt()) != 2)
						throw new IOException("Expected a GLB 2.0 file");
					input.readInt(); // Original total length.
					jsonLength = Integer.reverseBytes(input.readInt());
					if (Integer.reverseBytes(input.readInt()) != 0x4E4F534A)
						throw new IOException("Expected the GLB JSON chunk first");
				} else {
					jsonLength = Math.toIntExact(input.length());
				}
				if (jsonLength < 0 || jsonLength > input.length() - input.getFilePointer())
					throw new IOException("Invalid glTF JSON length");
				byte[] jsonBytes = new byte[jsonLength];
				input.readFully(jsonBytes);
				var document = new JsonParser().parse(new String(jsonBytes, StandardCharsets.UTF_8)).getAsJsonObject();
				addLighting(document, ubo);
				addWaterMaterials(document, waterMaterials);
				byte[] encoded = document.toString().getBytes(StandardCharsets.UTF_8);
				int padding = binary ? (4 - encoded.length % 4) % 4 : 0;
				if (binary) {
					long length = 20L + encoded.length + padding + input.length() - input.getFilePointer();
					if (length > 0xFFFFFFFFL)
						throw new IOException("GLB exceeds its 4 GiB size limit");
					output.write(ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
						.putInt(0x46546C67).putInt(2).putInt((int) length)
						.putInt(encoded.length + padding).putInt(0x4E4F534A).array());
				}
				output.write(encoded);
				for (int i = 0; i < padding; i++)
					output.write(' ');
				byte[] buffer = new byte[1024 * 1024];
				for (int count; (count = input.read(buffer)) != -1;)
					output.write(buffer, 0, count);
			}
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void addWaterMaterials(JsonObject document, Map<WaterType, Material> waterMaterials) {
		if (waterMaterials.isEmpty())
			return;
		for (var element : document.getAsJsonArray("materials")) {
			var material = element.getAsJsonObject();
			if (!material.has("name"))
				continue;
			for (var entry : waterMaterials.entrySet()) {
				if (!entry.getValue().name.equals(material.get("name").getAsString()))
					continue;
				var water = entry.getKey();
				float[] color = (float[]) ModelExporter.waterProperty(water, "surfaceColor");
				float opacity = clamp((float) ModelExporter.waterProperty(water, "baseOpacity"), 0, 1);
				var pbr = new JsonObject();
				pbr.add("baseColorFactor", array(color[0], color[1], color[2], opacity));
				pbr.addProperty("metallicFactor", 0);
				pbr.addProperty("roughnessFactor", .1);
				material.add("pbrMetallicRoughness", pbr);
				material.addProperty("alphaMode", "BLEND");
				material.remove("alphaCutoff");
				material.addProperty("doubleSided", true);
				// Keep Assimp's normalTexture, using the map directly as a conventional tangent-space normal.
				var ior = new JsonObject();
				ior.addProperty("ior", 1.333);
				var extensions = new JsonObject();
				extensions.add("KHR_materials_ior", ior);
				material.add("extensions", extensions);
				break;
			}
		}
		document.getAsJsonArray("extensionsUsed").add("KHR_materials_ior");
	}

	private static void addLighting(JsonObject document, UBOGlobal ubo) {
		float[] ambient = read(ubo, ubo.ambientColor, 3);
		multiply(ambient, ambient, read(ubo, ubo.ambientStrength, 1)[0]);
		float[] directional = read(ubo, ubo.lightColor, 3);
		multiply(directional, directional, read(ubo, ubo.lightStrength, 1)[0]);
		float[] towardLight = read(ubo, ubo.lightDir, 3);
		// Match the model export's root coordinate conversion, without scaling the direction.
		towardLight[0] = -towardLight[0];
		towardLight[1] = -towardLight[1];
		if (length(towardLight) == 0)
			towardLight[1] = 1;
		normalize(towardLight, towardLight);

		var scene = document.getAsJsonArray("scenes").get(document.has("scene") ? document.get("scene").getAsInt() : 0).getAsJsonObject();
		var metadata = new JsonObject();
		metadata.add("ambientLinearRgb", array(ambient));
		metadata.add("directionalLinearRgb", array(directional));
		metadata.add("directionToLight", array(towardLight));
		metadata.addProperty("description", "Resolved 117 HD lighting, including exposure. Ambient RGB is intended for a uniform World background. Directional intensity is approximate, not calibrated lux.");
		var extras = scene.has("extras") ? scene.getAsJsonObject("extras") : new JsonObject();
		extras.add("117HD_lighting", metadata);
		scene.add("extras", extras);

		float intensity = max(directional);
		var light = new JsonObject();
		light.addProperty("name", "Scene directional light (sun or moon)");
		light.addProperty("type", "directional");
		light.add("color", array(intensity > 0 ? divide(directional, intensity) : new float[] { 1, 1, 1 }));
		// Our diffuse lighting omits Lambert's 1/pi; compensate for a standard glTF diffuse BRDF.
		light.addProperty("intensity", intensity * PI);
		var lights = new JsonArray();
		lights.add(light);
		var extension = new JsonObject();
		extension.add("lights", lights);
		var extensions = document.has("extensions") ? document.getAsJsonObject("extensions") : new JsonObject();
		extensions.add(LIGHTS, extension);
		document.add("extensions", extensions);
		var used = document.has("extensionsUsed") ? document.getAsJsonArray("extensionsUsed") : new JsonArray();
		used.add(LIGHTS);
		document.add("extensionsUsed", used);

		// glTF directional lights emit along local -Z, so local +Z points toward the source.
		float[] up = abs(towardLight[1]) < .99f ? new float[] { 0, 1, 0 } : new float[] { 1, 0, 0 };
		float[] right = cross(up, towardLight);
		normalize(right, right);
		up = cross(towardLight, right);
		var node = new JsonObject();
		node.addProperty("name", "Scene lighting");
		node.add("matrix", array(right[0], right[1], right[2], 0, up[0], up[1], up[2], 0,
			towardLight[0], towardLight[1], towardLight[2], 0, 0, 0, 0, 1));
		var reference = new JsonObject();
		reference.addProperty("light", 0);
		var nodeExtensions = new JsonObject();
		nodeExtensions.add(LIGHTS, reference);
		node.add("extensions", nodeExtensions);
		node.add("extras", extras.deepCopy());
		var nodes = document.getAsJsonArray("nodes");
		scene.getAsJsonArray("nodes").add(nodes.size());
		nodes.add(node);
	}

	private static JsonArray array(float... values) {
		var array = new JsonArray();
		for (float value : values)
			array.add(value);
		return array;
	}

	// Read the last uploaded CPU snapshot without adding development-only getters to the renderer.
	private static float[] read(UBOGlobal ubo, UniformBuffer.Property property, int count) {
		try {
			var dataField = UniformBuffer.class.getDeclaredField("dataFloat");
			var offsetField = UniformBuffer.Property.class.getDeclaredField("offset");
			dataField.setAccessible(true);
			offsetField.setAccessible(true);
			var data = (FloatBuffer) dataField.get(ubo);
			int offset = offsetField.getInt(property);
			float[] values = new float[count];
			for (int i = 0; i < count; i++)
				values[i] = data.get(offset + i);
			return values;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to read scene lighting", ex);
		}
	}
}
