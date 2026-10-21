package rs117.hd.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIString;
import org.lwjgl.system.MemoryStack;
import rs117.hd.HdPlugin;
import rs117.hd.scene.ModelOverrideManager;
import rs117.hd.scene.MaterialManager;
import rs117.hd.utils.Props;
import rs117.hd.utils.ResourcePath;

import static java.awt.event.InputEvent.*;
import static org.lwjgl.assimp.Assimp.*;

/** Imports editable UV0 and materials, using immutable UV1 corner IDs for custom models. */
@Slf4j
public class ModelUvImporter implements KeyListener, AutoCloseable {
	private static final Keybind BAKE = new Keybind(KeyEvent.VK_ENTER, CTRL_DOWN_MASK | ALT_DOWN_MASK);
	// Consume whole strings and comments so embedded property names cannot match.
	private static final Pattern JSON_TOKEN = Pattern.compile(
		"\"(?:\\\\.|[^\"\\\\])*\"|//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|[{}\\[\\],:]|true|false|null|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");
	private final HdPlugin plugin;
	private final ModelOverrideManager overrides;
	private final KeyManager keys;
	private final ClientThread clientThread;
	private ResourcePath source;
	private boolean pressed;
	private boolean closed;

	@Inject
	public ModelUvImporter(HdPlugin plugin, ModelOverrideManager overrides, KeyManager keys, ClientThread clientThread) {
		this.plugin = plugin;
		this.overrides = overrides;
		this.keys = keys;
		this.clientThread = clientThread;
		if (Props.DEVELOPMENT) {
			overrides.setDevelopmentJsonProcessor((path, json) -> {
				source = path;
				resolve(json);
			});
			keys.registerKeyListener(this);
		}
	}

	@Override
	public void close() {
		closed = true;
		keys.unregisterKeyListener(this);
		if (Props.DEVELOPMENT)
			overrides.setDevelopmentJsonProcessor(null);
	}

	private int resolve(JsonElement json) {
		int count = 0;
		if (json == null || json.isJsonNull())
			return count;
		if (json.isJsonArray()) {
			for (var child : json.getAsJsonArray())
				count += resolve(child);
		} else if (json.isJsonObject()) {
			var object = json.getAsJsonObject();
			var custom = object.get("custom");
			if (custom != null && custom.isJsonPrimitive() && custom.getAsJsonPrimitive().isString()) {
				var path = Path.of("model-exports").resolve(custom.getAsString()).normalize();
				var imported = loadCustom(path, object.has("baseMaterial") ? object.get("baseMaterial").getAsString() : "NONE");
				object.add("custom", imported.get("custom"));
				object.add("baseMaterial", imported.get("baseMaterial"));
				log.info("Loaded custom model override '{}' from {}", object.get("description"), path);
				count++;
			}
			var uvs = object.get("uvs");
			if (uvs != null && uvs.isJsonPrimitive() && uvs.getAsJsonPrimitive().isString()) {
				var path = Path.of("model-exports").resolve(uvs.getAsString()).normalize();
				object.add("uvs", loadUvs(path));
				var description = object.get("description");
				log.info("Loaded UVs for model override '{}' from {}",
					description == null || description.isJsonNull() ? "UNKNOWN" : description.getAsString(), path);
				count++;
			}
			for (var entry : object.entrySet())
				if (!entry.getKey().equals("uvs"))
					count += resolve(entry.getValue());
		}
		return count;
	}

	static JsonObject loadCustom(Path path, String baseMaterial) {
		var scene = aiImportFile(path.toString(), 0);
		if (scene == null)
			throw new IllegalArgumentException("Unable to import " + path + ": " + aiGetErrorString());
		try (var stack = MemoryStack.stackPush()) {
			int faceCount = 0;
			for (int m = 0; m < scene.mNumMeshes(); m++)
				faceCount = Math.addExact(faceCount, AIMesh.create(scene.mMeshes().get(m)).mNumFaces());
			if (faceCount == 0)
				throw new IllegalArgumentException("No triangles in " + path);
			float[] coordinates = new float[Math.multiplyExact(faceCount, 6)];
			boolean[] seen = new boolean[faceCount * 3];
			String[] faceMaterials = new String[faceCount];
			var counts = new LinkedHashMap<String, Integer>();
			var name = AIString.calloc(stack);
			for (int m = 0; m < scene.mNumMeshes(); m++) {
				var mesh = AIMesh.create(scene.mMeshes().get(m));
				var uvs = mesh.mTextureCoords(0);
				var ids = mesh.mTextureCoords(1);
				if (uvs == null || ids == null || mesh.mNumUVComponents(0) < 2 || mesh.mNumUVComponents(1) < 2)
					throw new IllegalArgumentException("Missing UV0 or original corner IDs in UV1; re-export the model: " + path);
				if (aiGetMaterialString(AIMaterial.create(scene.mMaterials().get(mesh.mMaterialIndex())), AI_MATKEY_NAME, 0, 0, name) != aiReturn_SUCCESS)
					throw new IllegalArgumentException("Missing material name in " + path);
				String material = name.dataString();
				// Blender appends numeric suffixes when a material name already exists.
				if (!MaterialManager.MATERIAL_MAP.containsKey(material))
					material = material.replaceFirst("\\.[0-9]+$", "");
				if (!MaterialManager.MATERIAL_MAP.containsKey(material))
					throw new IllegalArgumentException("Unknown material '" + name.dataString() + "' in " + path);
				float[] scale = (float[]) ModelExporter.materialProperty(MaterialManager.MATERIAL_MAP.get(material), "textureScale");
				for (int f = 0; f < mesh.mNumFaces(); f++) {
					var face = mesh.mFaces().get(f);
					if (face.mNumIndices() != 3)
						throw new IllegalArgumentException("Keep the original triangles when editing " + path);
					int originalFace = -1;
					for (int c = 0; c < 3; c++) {
						int vertex = face.mIndices().get(c);
						var id = ids.get(vertex);
						int corner = (int) id.x();
						if (id.x() != corner || corner < 0 || corner >= seen.length || id.y() != seen.length || seen[corner])
							throw new IllegalArgumentException("Missing, modified or duplicate original corner IDs in " + path);
						if (originalFace != -1 && originalFace != corner / 3)
							throw new IllegalArgumentException("Triangle topology changed in " + path);
						originalFace = corner / 3;
						seen[corner] = true;
						var uv = uvs.get(vertex);
						if (!Float.isFinite(uv.x()) || !Float.isFinite(uv.y()))
							throw new IllegalArgumentException("Non-finite UV in " + path);
						// Undo the image-orientation flip and material scale applied by ModelExporter.
						coordinates[corner * 2] = (.5f - uv.x()) * scale[0] + .5f;
						coordinates[corner * 2 + 1] = (.5f - uv.y()) * scale[1] + .5f;
					}
					faceMaterials[originalFace] = material;
					counts.merge(material, 1, Integer::sum);
				}
			}
			int largest = counts.getOrDefault(baseMaterial, 0);
			for (var entry : counts.entrySet()) {
				if (entry.getValue() > largest) {
					baseMaterial = entry.getKey();
					largest = entry.getValue();
				}
			}
			var custom = new JsonObject();
			var uvs = new JsonArray();
			for (float coordinate : coordinates)
				uvs.add(coordinate);
			custom.add("uvs", uvs);
			var materials = new JsonArray();
			for (String material : counts.keySet()) {
				if (material.equals(baseMaterial))
					continue;
				var faces = new JsonArray();
				for (int first = 0; first < faceCount; first++) {
					if (!material.equals(faceMaterials[first]))
						continue;
					int last = first;
					while (last + 1 < faceCount && material.equals(faceMaterials[last + 1]))
						last++;
					String range = first + "-" + last;
					int separateLength = -2;
					for (int f = first; f <= last; f++)
						separateLength += Integer.toString(f).length() + 2;
					if (range.length() + 2 < separateLength) {
						faces.add(range);
					} else {
						for (int f = first; f <= last; f++)
							faces.add(f);
					}
					first = last;
				}
				var override = new JsonObject();
				override.addProperty("material", material);
				override.add("faces", faces);
				materials.add(override);
			}
			if (materials.size() > 0)
				custom.add("materials", materials);
			var result = new JsonObject();
			result.addProperty("baseMaterial", baseMaterial);
			result.add("custom", custom);
			return result;
		} finally {
			aiReleaseImport(scene);
		}
	}

	static JsonArray loadUvs(Path path) {
		if (!Files.isRegularFile(path))
			throw new IllegalArgumentException("UV model does not exist: " + path);
		// Do not triangulate, optimize, or join vertices: face order is the override's contract.
		var scene = aiImportFile(path.toString(), 0);
		if (scene == null)
			throw new IllegalArgumentException("Unable to import " + path + ": " + aiGetErrorString());
		try {
			var result = new JsonArray();
			for (int m = 0; m < scene.mNumMeshes(); m++) {
				var mesh = AIMesh.create(scene.mMeshes().get(m));
				var uvs = mesh.mTextureCoords(0);
				if (uvs == null || mesh.mNumUVComponents(0) < 2)
					throw new IllegalArgumentException("Missing 2D UV channel in mesh " + m + " of " + path);
				var faces = mesh.mFaces();
				for (int f = 0; f < mesh.mNumFaces(); f++) {
					var face = faces.get(f);
					if (face.mNumIndices() != 3)
						throw new IllegalArgumentException("UV model must contain only triangles: " + path);
					for (int c = 0; c < 3; c++) {
						var uv = uvs.get(face.mIndices().get(c));
						if (!Float.isFinite(uv.x()) || !Float.isFinite(uv.y()))
							throw new IllegalArgumentException("Non-finite UV in " + path);
						result.add(uv.x());
						result.add(uv.y());
					}
				}
			}
			if (result.size() == 0)
				throw new IllegalArgumentException("No UV triangles in " + path);
			return result;
		} finally {
			aiReleaseImport(scene);
		}
	}

	private void bake() {
		if (closed || source == null)
			return;
		try {
			Path path = source.toPath().toAbsolutePath();
			String original = Files.readString(path);
			var replacements = new ArrayList<String>();
			String updated = replacePaths(original, replacements);
			if (replacements.isEmpty())
				return;
			Path temporary = Files.createTempFile(path.getParent(), "model-uvs-", ".json");
			try {
				Files.writeString(temporary, updated, StandardCharsets.UTF_8);
				if (!Files.readString(path).equals(original))
					throw new IOException("Overrides changed during import; refusing to overwrite them");
				Path backup = Files.createTempFile(path.getParent(), "model-overrides-", ".bak");
				Files.writeString(backup, original, StandardCharsets.UTF_8);
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				for (var replacement : replacements)
					log.info("{}: {}", path, replacement);
				log.info("Baked {} UV model paths into {}. Backup: {}", replacements.size(), path, backup);
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (Exception ex) {
			log.error("Unable to bake model UVs; overrides were not replaced", ex);
		}
	}

	private static class ObjectSpan {
		int start;
		String description = "UNKNOWN";
		String baseMaterial = "NONE";
		int baseStart, baseEnd;
		String customPath;
		int customStart, customEnd, customLine;
		String uvLocation;
	}

	private static class Replacement {
		final int start, end;
		final String text;

		Replacement(int start, int end, String text) {
			this.start = start;
			this.end = end;
			this.text = text;
		}
	}

	private String replacePaths(String original, ArrayList<String> reports) {
		// Validate JSON, but edit only the imported values, preserving all unrelated formatting.
		plugin.getGson().fromJson(original, JsonElement.class);
		var objects = new ArrayDeque<ObjectSpan>();
		var edits = new ArrayList<Replacement>();
		String newline = original.contains("\r\n") ? "\r\n" : "\n";
		var tokens = JSON_TOKEN.matcher(original);
		String previousString = null;
		String property = null;
		int line = 1;
		int scanned = 0;
		while (tokens.find()) {
			while (scanned < tokens.start())
				if (original.charAt(scanned++) == '\n')
					line++;
			String token = tokens.group();
			if (token.startsWith("/"))
				continue;
			if (token.equals("{")) {
				var object = new ObjectSpan();
				object.start = tokens.end();
				objects.push(object);
			} else if (token.equals("}")) {
				var object = objects.pop();
				if (object.uvLocation != null)
					reports.add("Replaced UV path for '" + object.description + "' " + object.uvLocation);
				if (object.customPath != null) {
					var imported = loadCustom(Path.of("model-exports").resolve(object.customPath).normalize(), object.baseMaterial);
					int lineStart = original.lastIndexOf('\n', object.customStart - 1) + 1;
					int indentEnd = lineStart;
					while (indentEnd < object.customStart && (original.charAt(indentEnd) == ' ' || original.charAt(indentEnd) == '\t'))
						indentEnd++;
					String indent = original.substring(lineStart, indentEnd);
					edits.add(new Replacement(object.customStart, object.customEnd, format(imported.get("custom"), indent, newline)));
					String base = imported.get("baseMaterial").getAsString();
					if (!base.equals(object.baseMaterial)) {
						String value = format(imported.get("baseMaterial"), "", newline);
						if (object.baseEnd > 0) {
							edits.add(new Replacement(object.baseStart, object.baseEnd, value));
						} else {
							edits.add(new Replacement(object.start, object.start, " \"baseMaterial\": " + value + ","));
						}
					}
					reports.add("Replaced custom model for '" + object.description + "' from '" + object.customPath + "' at line " + object.customLine);
				}
			} else if (token.equals(":")) {
				property = previousString;
				continue;
			} else if (token.startsWith("\"")) {
				String value = plugin.getGson().fromJson(token, String.class);
				if (!objects.isEmpty()) {
					var object = objects.peek();
					if ("description".equals(property)) {
						object.description = value;
					} else if ("baseMaterial".equals(property)) {
						object.baseMaterial = value;
						object.baseStart = tokens.start();
						object.baseEnd = tokens.end();
					} else if ("custom".equals(property)) {
						object.customPath = value;
						object.customStart = tokens.start();
						object.customEnd = tokens.end();
						object.customLine = line;
					} else if ("uvs".equals(property)) {
						var uvs = loadUvs(Path.of("model-exports").resolve(value).normalize());
						edits.add(new Replacement(tokens.start(), tokens.end(), format(uvs, "", newline)));
						object.uvLocation = "from '" + value + "' at line " + line;
					}
				}
				previousString = value;
				property = null;
				continue;
			}
			property = previousString = null;
		}
		edits.sort(Comparator.comparingInt(edit -> edit.start));
		var result = new StringBuilder();
		int copied = 0;
		for (var edit : edits) {
			result.append(original, copied, edit.start).append(edit.text);
			copied = edit.end;
		}
		return result.append(original, copied, original.length()).toString();
	}

	private String format(JsonElement json, String indent, String newline) {
		if (json.isJsonNull() || json.isJsonPrimitive())
			return json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber() ?
				new BigDecimal(json.getAsString()).stripTrailingZeros().toPlainString() : plugin.getGson().toJson(json);
		boolean inline = json.isJsonArray();
		if (inline)
			for (var value : json.getAsJsonArray())
				inline &= value.isJsonPrimitive() || value.isJsonNull();
		String childIndent = indent + "  ";
		String separator = inline ? " " : newline + childIndent;
		var result = new StringBuilder(json.isJsonArray() ? "[" : "{");
		boolean first = true;
		if (json.isJsonArray()) {
			for (var value : json.getAsJsonArray()) {
				if (!first)
					result.append(',');
				first = false;
				result.append(separator).append(format(value, childIndent, newline));
			}
		} else {
			for (var entry : json.getAsJsonObject().entrySet()) {
				if (!first)
					result.append(',');
				first = false;
				result.append(separator).append(plugin.getGson().toJson(entry.getKey())).append(": ")
					.append(format(entry.getValue(), childIndent, newline));
			}
		}
		if (!first)
			result.append(inline ? " " : newline + indent);
		return result.append(json.isJsonArray() ? "]" : "}").toString();
	}

	@Override
	public void keyPressed(KeyEvent event) {
		if (BAKE.matches(event)) {
			event.consume();
			if (!pressed) {
				pressed = true;
				clientThread.invokeLater(this::bake);
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.VK_ENTER)
			pressed = false;
	}

	@Override
	public void keyTyped(KeyEvent event) {}
}
