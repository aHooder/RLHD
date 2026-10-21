package rs117.hd.scene.model_overrides;

import com.google.gson.JsonObject;
import java.util.Arrays;
import rs117.hd.HdPlugin;
import rs117.hd.scene.materials.Material;

/** Authored per-corner UVs and per-face overrides; normalized once, indexed during upload. */
public class CustomModelOverride {
	public float[] uvs;
	private JsonObject[] materials;
	transient ModelOverride[] faceOverrides;

	void normalize(HdPlugin plugin, ModelOverride parent) {
		if (faceOverrides != null)
			return;
		if (uvs == null || uvs.length == 0 || uvs.length % 6 != 0)
			throw new IllegalArgumentException("custom.uvs must contain six coordinates per triangle");
		for (float uv : uvs)
			if (!Float.isFinite(uv))
				throw new IllegalArgumentException("Non-finite custom UV");

		// Serialize a copy before normalization, so children inherit authored values, not derived flags.
		var defaults = parent.copy();
		defaults.custom = null;
		defaults.uvs = null;
		defaults.textureMaterial = Material.NONE;
		defaults.materialOverrides = null;
		defaults.colorOverrides = null;
		defaults.clearIds();
		var gson = plugin.getGson();
		var inherited = gson.toJsonTree(defaults).getAsJsonObject();
		defaults.normalize(plugin);
		var resolved = new ModelOverride[uvs.length / 6];
		Arrays.fill(resolved, defaults);
		if (materials != null) {
			boolean[] assigned = new boolean[resolved.length];
			for (var material : materials) {
				var definition = inherited.deepCopy();
				for (var entry : material.entrySet())
					definition.add(entry.getKey(), entry.getValue());
				if (material.has("material"))
					definition.add("baseMaterial", material.get("material"));
				definition.remove("custom");
				var override = gson.fromJson(definition, ModelOverride.class);
				override.normalize(plugin);
				for (var range : material.getAsJsonArray("faces")) {
					String[] ends = range.getAsString().split("-", -1);
					int first = Integer.parseInt(ends[0]);
					int last = ends.length == 1 ? first : Integer.parseInt(ends[1]);
					if (ends.length > 2 || first < 0 || last < first || last >= resolved.length)
						throw new IllegalArgumentException("Invalid custom face range: " + range);
					for (int face = first; face <= last; face++) {
						if (assigned[face])
							throw new IllegalArgumentException("Duplicate custom face: " + face);
						assigned[face] = true;
						resolved[face] = override;
					}
				}
			}
		}
		faceOverrides = resolved;
	}
}
