package rs117.hd.model;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import rs117.hd.HdPlugin;
import rs117.hd.renderer.legacy.LegacySceneContext;
import rs117.hd.renderer.legacy.LegacySceneUploader;
import rs117.hd.renderer.zone.SceneManager;
import rs117.hd.scene.ProceduralGenerator;
import rs117.hd.scene.TileOverrideManager;
import rs117.hd.scene.tile_overrides.TileOverride;
import rs117.hd.utils.Mat4;

/** Reuses terrain generation without updating the live tiles' buffer offsets or uploading objects. */
final class SceneTerrainExporter {
	@Inject
	private Client client;
	@Inject
	private HdPlugin plugin;
	@Inject
	private SceneManager sceneManager;
	@Inject
	private ProceduralGenerator proceduralGenerator;
	@Inject
	private TileOverrideManager tileOverrideManager;
	@Inject
	private LegacySceneUploader uploader;

	LegacySceneContext build(Map<WorldView, List<Tile>> tiles) {
		var current = plugin.getSceneContext();
		var output = new LegacySceneContext(client, current.scene, current.expandedMapLoadingChunks, null);
		boolean complete = false;
		try {
			// Keep these development-only entry points out of the production renderer's public API.
			Method paintUpload = LegacySceneUploader.class.getDeclaredMethod("upload",
				LegacySceneContext.class, Tile.class, int[].class, TileOverride.class, SceneTilePaint.class);
			Method modelUpload = LegacySceneUploader.class.getDeclaredMethod("upload",
				LegacySceneContext.class, Tile.class, int[].class, SceneTileModel.class);
			paintUpload.setAccessible(true);
			modelUpload.setAccessible(true);
			for (var entry : tiles.entrySet()) {
				var view = entry.getKey();
				var context = new LegacySceneContext(client, view.getScene(), current.expandedMapLoadingChunks, null);
				try {
					context.isInHouse = current.isInHouse;
					context.isInChambersOfXeric = current.isInChambersOfXeric;
					if (!view.isTopLevel()) {
						context.sceneOffset = 0;
						context.sizeX = view.getSizeX();
						context.sizeZ = view.getSizeY();
					}
					proceduralGenerator.generateSceneData(context, null);
					float[] projection = view.isTopLevel() ? null : sceneManager.getContext(view).projection;
					// Cofactor matrix transforms normals, including nonuniformly scaled worldviews.
					float[] normalMatrix = projection == null ? null : normalMatrix(projection);
					float[] position = new float[3];
					var worldTiles = entry.getValue();
					for (int t = 0; t < worldTiles.size(); t++) {
						var tile = worldTiles.get(t);
						context.stagingBufferVertices.clear();
						context.stagingBufferNormals.clear();
						context.stagingBufferUvs.clear();
						int[] worldPos = context.localToWorld(tile.getLocalLocation(), tile.getPlane());
						var override = tileOverrideManager.getOverride(context, tile, worldPos);
						if (tile.getSceneTilePaint() != null || override.forced)
							paintUpload.invoke(uploader, context, tile, worldPos, override, tile.getSceneTilePaint());
						if (tile.getSceneTileModel() != null)
							modelUpload.invoke(uploader, context, tile, worldPos, tile.getSceneTileModel());

						int size = context.stagingBufferVertices.position();
						output.stagingBufferVertices.ensureCapacity(size);
						output.stagingBufferNormals.ensureCapacity(size);
						output.stagingBufferUvs.ensureCapacity(size);
						var vertices = context.stagingBufferVertices.getBuffer();
						var normals = context.stagingBufferNormals.getBuffer();
						var uvs = context.stagingBufferUvs.getBuffer();
						int x = tile.getSceneLocation().getX() * 128;
						int z = tile.getSceneLocation().getY() * 128;
						for (int i = 0; i < size; i += 4) {
							position[0] = Float.intBitsToFloat(vertices.get(i)) + x;
							position[1] = Float.intBitsToFloat(vertices.get(i + 1));
							position[2] = Float.intBitsToFloat(vertices.get(i + 2)) + z;
							if (projection != null)
								Mat4.transformVecAffine(position, projection, position);
							output.stagingBufferVertices.put(position[0], position[1], position[2], vertices.get(i + 3));
							position[0] = normals.get(i);
							position[1] = normals.get(i + 1);
							position[2] = normals.get(i + 2);
							if (normalMatrix != null)
								Mat4.transformVecAffine(position, normalMatrix, position);
							output.stagingBufferNormals.put(position[0], position[1], position[2], normals.get(i + 3));
							output.stagingBufferUvs.put(uvs.get(i), uvs.get(i + 1), uvs.get(i + 2), uvs.get(i + 3));
						}
					}
				} finally {
					context.destroy();
					context.staticUnorderedModelBuffer.destroy();
				}
			}
			complete = true;
			return output;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to generate exported terrain", ex);
		} finally {
			if (!complete) {
				output.destroy();
				output.staticUnorderedModelBuffer.destroy();
			}
		}
	}

	private static float[] normalMatrix(float[] m) {
		float[] result = Mat4.identity();
		for (int c = 0; c < 3; c++) {
			int a = (c + 1) % 3 * 4, b = (c + 2) % 3 * 4;
			result[c * 4] = m[a + 1] * m[b + 2] - m[a + 2] * m[b + 1];
			result[c * 4 + 1] = m[a + 2] * m[b] - m[a] * m[b + 2];
			result[c * 4 + 2] = m[a] * m[b + 1] - m[a + 1] * m[b];
		}
		float determinant = m[0] * result[0] + m[1] * result[1] + m[2] * result[2];
		if (determinant < 0)
			for (int i = 0; i < 12; i++)
				result[i] = -result[i];
		return result;
	}
}
