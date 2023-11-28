package rs117.hd.scene;

import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import rs117.hd.HdPlugin;
import rs117.hd.HdPluginConfig;
import rs117.hd.config.MinimapStyle;
import rs117.hd.data.WaterType;
import rs117.hd.data.materials.GroundMaterial;
import rs117.hd.data.materials.Material;
import rs117.hd.data.materials.Overlay;
import rs117.hd.data.materials.Underlay;
import rs117.hd.overlays.FrameTimer;
import rs117.hd.overlays.Timer;
import rs117.hd.utils.ColorUtils;
import rs117.hd.utils.HDUtils;

import static net.runelite.api.Constants.*;
import static net.runelite.api.Perspective.*;
import static rs117.hd.scene.SceneUploader.SCENE_OFFSET;
import static rs117.hd.utils.HDUtils.clamp;

@Slf4j
public class MinimapRenderer {
	public static SceneTileModel[] tileModels = new SceneTileModel[52];

	static {
		for (int index = 0; index < tileModels.length; ++index) {
			int shape = index >> 2;
			int rotation = index & 0x3;

			tileModels[index] = new SceneTileModelCustom(shape, rotation, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0);
		}
	}

	@Inject
	private Client client;

	@Inject
	private TextureManager textureManager;

	@Inject
	private HdPlugin plugin;

	@Inject
	private HdPluginConfig config;

	@Inject
	private EnvironmentManager environmentManager;

	@Inject
	private ProceduralGenerator proceduralGenerator;

	private final int[] tmpScreenX = new int[6];
	private final int[] tmpScreenY = new int[6];

	@Inject
	private FrameTimer frameTimer;

	public void prepareScene(SceneContext sceneContext) {
		final Scene scene = sceneContext.scene;

		boolean classicLighting = config.minimapType() == MinimapStyle.HD2008;

		for (int z = 0; z < MAX_Z; ++z) {
			for (int x = 0; x < EXTENDED_SCENE_SIZE; ++x) {
				for (int y = 0; y < EXTENDED_SCENE_SIZE; ++y) {
					Tile tile = sceneContext.scene.getExtendedTiles()[z][x][y];
					if (tile == null) return;

					processTilePaint(sceneContext, scene, tile, x, y, z, classicLighting);
					processTileModel(sceneContext, scene, tile, x, y, z, classicLighting);
				}
			}
		}
	}


	private void processTilePaint(SceneContext sceneContext, Scene scene, Tile tile, int x, int y, int z, boolean classicLighting) {
		final SceneTilePaint paint = tile.getSceneTilePaint();
		if (paint == null) {
			return; // No paint to process
		}

		int[] vertexKeys = ProceduralGenerator.tileVertexKeys(scene, tile);
		Point tilePoint = tile.getSceneLocation();
		int baseX = scene.getBaseX();
		int baseY = scene.getBaseY();
		int tileZ = tile.getRenderLevel();
		int tileTexture = paint.getTexture();
		WaterType waterType = proceduralGenerator.tileWaterType(scene, tile, paint);

		// Colors and Materials initialization
		int[] colors = { paint.getSwColor(), paint.getSeColor(), paint.getNwColor(), paint.getNeColor() };
		Material[] materials = new Material[4];

		int[] finalColors = new int[8]; // Store the results

		Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
		Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);
		GroundMaterial groundMaterial = (overlay != Overlay.NONE) ? overlay.groundMaterial : underlay.groundMaterial;
		Material vanillaTexture = Material.fromVanillaTexture(tileTexture);
		int waterColor = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);

		int swVertexKey = vertexKeys[0];
		int seVertexKey = vertexKeys[1];
		int nwVertexKey = vertexKeys[2];
		int neVertexKey = vertexKeys[3];
		for (int i = 0; i < 4; i++) {
			// Material and Color processing
			materials[i] = vanillaTexture;

			//Disable Ground Blending in minimap as it causes issues
			//if (plugin.configGroundBlending && !proceduralGenerator.useDefaultColor(scene, tile) && paint.getTexture() == -1) {
			//materials[i] = sceneContext.vertexTerrainTexture.getOrDefault(vertexKeys[i], materials[i]);
			//}


			if (waterType != WaterType.NONE) {
				Arrays.fill(colors, ColorUtils.srgbToPackedHsl(waterType.surfaceColor));
				if (sceneContext.vertexIsWater.containsKey(swVertexKey) && sceneContext.vertexIsLand.containsKey(swVertexKey))
					colors[0] = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
				if (sceneContext.vertexIsWater.containsKey(seVertexKey) && sceneContext.vertexIsLand.containsKey(seVertexKey))
					colors[1] = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
				if (sceneContext.vertexIsWater.containsKey(nwVertexKey) && sceneContext.vertexIsLand.containsKey(nwVertexKey))
					colors[2] = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
				if (sceneContext.vertexIsWater.containsKey(neVertexKey) && sceneContext.vertexIsLand.containsKey(neVertexKey))
					colors[3] = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
			} else {
				if (overlay != Overlay.NONE || underlay != Underlay.NONE) {
					colors[i] = HDUtils.colorHSLToInt((overlay != Overlay.NONE) ?
						overlay.modifyColor(HDUtils.colorIntToHSL(colors[i]), classicLighting) :
						underlay.modifyColor(HDUtils.colorIntToHSL(colors[i]), classicLighting));

					if (plugin.configGroundTextures && groundMaterial != null) {
						int materialX = baseX + tilePoint.getX() + (i % 2);
						int materialY = baseY + tilePoint.getY() + (i / 2);
						materials[i] = groundMaterial.getRandomMaterial(tileZ, materialX, materialY);
					}
				}
			}

			// Combine texture color with vertex color
			float[] textureColor = textureManager.getTextureAverageColor(materials[i]);
			float[] vertexColor = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(colors[i]));
			finalColors[i + 4] = multiplyAndPackColors(textureColor, vertexColor);
			finalColors[i] = colors[i];
			// Environmental lighting
			if (!classicLighting) {
				finalColors[i] = environmentalLighting(colors[i]);
			}
		}

		// Ensure hue and saturation are identical

		for (int i = 1; i < 4; i++) {
			finalColors[i] = finalColors[0] & 0xFF80 | finalColors[i] & 0x7F;
			finalColors[i + 4] = finalColors[4] & 0xFF80 | finalColors[i + 4] & 0x7F;
		}


		System.arraycopy(finalColors, 0, sceneContext.minimapTilePaintColors[z][x][y], 0, finalColors.length);

	}

	private void processTileModel(SceneContext sceneContext, Scene scene, Tile tile, int x, int y, int z, boolean classicLighting) {
		final SceneTileModel model = tile.getSceneTileModel();
		if (model == null) {
			return; // No model to process
		}

		final int baseX = scene.getBaseX();
		final int baseY = scene.getBaseY();
		final int[] faceTextures = model.getTriangleTextureId();
		final int faceCount = model.getFaceX().length;
		final Point tilePoint = tile.getSceneLocation();
		final int tileX = tilePoint.getX();
		final int tileY = tilePoint.getY();
		final int tileZ = tile.getRenderLevel();

		// Handle overlay and underlay
		Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
		Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);
		GroundMaterial groundMaterial = overlay != Overlay.NONE ? overlay.groundMaterial : underlay.groundMaterial;

		int[] vertexKeys = ProceduralGenerator.tileVertexKeys(scene, tile);
		int vertexKeyA = vertexKeys[0];
		int vertexKeyB = vertexKeys[1];
		int vertexKeyC = vertexKeys[2];

		for (int face = 0; face < faceCount; ++face) {
			int[] colors = { model.getTriangleColorA()[face], model.getTriangleColorB()[face], model.getTriangleColorC()[face] };
			Material[] materials = new Material[3];
			Arrays.fill(materials, Material.NONE);

			WaterType waterType = proceduralGenerator.faceWaterType(scene, tile, face, model);

			if (waterType != WaterType.NONE) {
				Arrays.fill(colors, ColorUtils.srgbToPackedHsl(waterType.surfaceColor));
				if (sceneContext.vertexIsWater.containsKey(vertexKeyA) && sceneContext.vertexIsLand.containsKey(vertexKeyA))
					colors[0] = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
				if (sceneContext.vertexIsWater.containsKey(vertexKeyB) && sceneContext.vertexIsLand.containsKey(vertexKeyB))
					colors[1] = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
				if (sceneContext.vertexIsWater.containsKey(vertexKeyC) && sceneContext.vertexIsLand.containsKey(vertexKeyC))
					colors[2] = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);

			} else {
				int textureIndex = faceTextures != null ? faceTextures[face] : -1;
				if (textureIndex != -1) {
					Material textureMaterial = Material.fromVanillaTexture(textureIndex);
					Arrays.fill(materials, textureMaterial);
				} else {
					boolean isOverlay = ProceduralGenerator.isOverlayFace(tile, face);
					if (isOverlay && overlay != Overlay.NONE) {
						groundMaterial = overlay.groundMaterial;
						for (int i = 0; i < colors.length; i++) {
							colors[i] = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(colors[i])));
						}
					} else if (!isOverlay && underlay != Underlay.NONE) {
						groundMaterial = underlay.groundMaterial;
						for (int i = 0; i < colors.length; i++) {
							colors[i] = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(colors[i])));
						}
					}

					if (plugin.configGroundTextures && groundMaterial != null) {
						int[][] localVertices = ProceduralGenerator.faceLocalVertices(tile, face);
						for (int i = 0; i < materials.length; i++) {
							materials[i] = groundMaterial.getRandomMaterial(
								tileZ,
								baseX + tileX + (int) Math.floor((float) localVertices[i][0] / LOCAL_TILE_SIZE),
								baseY + tileY + (int) Math.floor((float) localVertices[i][1] / LOCAL_TILE_SIZE)
							);
						}
					}
				}
			}

			// Apply environmental lighting and adjust colors
			if (!classicLighting) {
				for (int i = 0; i < colors.length; i++) {
					colors[i] = environmentalLighting(colors[i]);
				}
				// Ensure hue and saturation are identical
				int baseColor = colors[0] & 0xFF80;
				for (int i = 1; i < colors.length; i++) {
					colors[i] = baseColor | colors[i] & 0x7F;
				}
			}

			// Convert colors to linear space and combine with materials
			for (int i = 0; i < colors.length; i++) {
				float[] tileColor = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(colors[i]));
				float[] materialColor = textureManager.getTextureAverageColor(materials[i]);
				int packedColor = multiplyAndPackColors(materialColor, tileColor);

				packedColor = packedColor & 0xFF80 | sceneContext.minimapTileModelColors[z][x][y][face][0] & 0x7F;

				sceneContext.minimapTileModelColors[z][x][y][face][i + 3] = packedColor;
			}

			// Store the original colors
			System.arraycopy(colors, 0, sceneContext.minimapTileModelColors[z][x][y][face], 0, colors.length);
		}
	}


	public void drawTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
		frameTimer.begin(Timer.MINIMAP_DRAW);
		try {
			if (config.minimapType() == MinimapStyle.DEFAULT || config.minimapType() == MinimapStyle.HD2008) {
				drawMinimapShaded(tile, tx, ty, px0, py0, px1, py1);
			} else {
				drawMinimapOSRS(tile, tx, ty, px0, py0, px1, py1);
			}
		} catch (Exception e) {
			log.info("Minimap Crashed Defaulting to no minimap");
			client.setMinimapTileDrawer(null);
		}

		frameTimer.end(Timer.MINIMAP_DRAW);
	}

	private void drawMinimapOSRS(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
		client.getRasterizer().setRasterGouraudLowRes(false);
		try {
			SceneTilePaint paint = tile.getSceneTilePaint();
			if (paint != null) {
				int color = paint.getRBG();
				if (color != 0) {
					client.getRasterizer().fillRectangle(px0, py0, px1 - px0, py1 - py0, color);
				}
			} else {
				SceneTileModel model = tile.getSceneTileModel();
				if (model != null) {
					SceneTileModel stm = tileModels[(model.getShape() << 2) | model.getRotation() & 3];

					int[] vertexX = stm.getVertexX();
					int[] vertexZ = stm.getVertexZ();

					int[] indicies1 = stm.getFaceX();
					int[] indicies2 = stm.getFaceY();
					int[] indicies3 = stm.getFaceZ();

					int w = px1 - px0;
					int h = py1 - py0;

					for (int vert = 0; vert < vertexX.length; ++vert) {
						tmpScreenX[vert] = px0 + ((vertexX[vert] * w) >> Perspective.LOCAL_COORD_BITS);
						tmpScreenY[vert] = py0 + (((Perspective.LOCAL_TILE_SIZE - vertexZ[vert]) * h) >> Perspective.LOCAL_COORD_BITS);
					}

					for (int face = 0; face < indicies1.length; ++face) {
						int idx1 = indicies1[face];
						int idx2 = indicies2[face];
						int idx3 = indicies3[face];

						boolean isUnderlay = stm.getTriangleColorA()[face] == 0;
						int color = isUnderlay
							? model.getModelUnderlay()
							: model.getModelOverlay();
						if (!isUnderlay || color != 0) {
							client.getRasterizer().rasterFlat(
								tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
								tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
								color
							);
						}
					}
				}
			}
		} finally {
			client.getRasterizer().setRasterGouraudLowRes(true);
		}
	}

	private void drawMinimapShaded(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
		var sceneContext = plugin.getSceneContext();
		if (sceneContext == null)
			return;

		int plane = tile.getPlane();
		int tileExX = tile.getSceneLocation().getX() + SCENE_OFFSET;
		int tileExY = tile.getSceneLocation().getY() + SCENE_OFFSET;

		var paint = tile.getSceneTilePaint();
		if (paint != null) {
			int swColor = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][0];
			int seColor = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][1];
			int nwColor = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][2];
			int neColor = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][3];

			int swTexture = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][4];
			int seTexture = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][5];
			int nwTexture = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][6];
			int neTexture = sceneContext.minimapTilePaintColors[plane][tileExX][tileExY][7];
			
			if (paint.getTexture() == -1) {
				if (paint.getNeColor() != 12345678) {
					fillGradient(px0, py0, px1, py1, nwColor, neColor, swColor, seColor);
				} else {
					client.getRasterizer().fillRectangle(px0, py0, px1 - px0, py1 - py0, paint.getRBG());
				}
			} else {
				fillGradient(px0, py0, px1, py1, nwTexture, neTexture, swTexture, seTexture);
			}



		}

		var model = tile.getSceneTileModel();
		if (model != null) {
			int[] vertexX = model.getVertexX();
			int[] vertexZ = model.getVertexZ();

			int[] indicies1 = model.getFaceX();
			int[] indicies2 = model.getFaceY();
			int[] indicies3 = model.getFaceZ();

			int[] color1 = model.getTriangleColorA();
			int[] textures = model.getTriangleTextureId();

			int localX = tx << Perspective.LOCAL_COORD_BITS;
			int localY = ty << Perspective.LOCAL_COORD_BITS;

			int w = px1 - px0;
			int h = py1 - py0;

			for (int vert = 0; vert < vertexX.length; ++vert) {
				tmpScreenX[vert] = px0 + (((vertexX[vert] - localX) * w) >> Perspective.LOCAL_COORD_BITS);
				tmpScreenY[vert] = py0 + (((Perspective.LOCAL_TILE_SIZE - (vertexZ[vert] - localY)) * h) >> Perspective.LOCAL_COORD_BITS);
			}

			for (int face = 0; face < indicies1.length; ++face) {
				int idx1 = indicies1[face];
				int idx2 = indicies2[face];
				int idx3 = indicies3[face];

				int c1 = sceneContext.minimapTileModelColors[plane][tileExX][tileExY][face][0];
				int c2 = sceneContext.minimapTileModelColors[plane][tileExX][tileExY][face][1];
				int c3 = sceneContext.minimapTileModelColors[plane][tileExX][tileExY][face][2];


				int mc1 = sceneContext.minimapTileModelColors[plane][tileExX][tileExY][face][3];
				int mc2 = sceneContext.minimapTileModelColors[plane][tileExX][tileExY][face][4];
				int mc3 = sceneContext.minimapTileModelColors[plane][tileExX][tileExY][face][5];


				if (textures != null && textures[face] != -1) {
					client.getRasterizer().rasterGouraud(
						tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
						tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
						mc1, mc2, mc3
					);
				} else if (color1[face] != 12345678) {
					client.getRasterizer().rasterGouraud(
						tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
						tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
						c1, c2, c3
					);
				}


			}
		}
	}

	public void applyEnvironmentalLighting(SceneContext sceneContext) {

	}

	private void fillGradient(int px0, int py0, int px1, int py1, int c00, int c10, int c01, int c11) {
		Rasterizer g3d = client.getRasterizer();
		g3d.rasterGouraud(py0, py0, py1, px0, px1, px0, c00, c10, c01);
		g3d.rasterGouraud(py0, py1, py1, px1, px0, px1, c10, c01, c11);
	}

	public int multiplyAndPackColors(float[] textureColor, float[] tileColor) {
		// Ensure each color array has exactly 3 elements (R, G, B)
		if (textureColor.length != 3 || tileColor.length != 3) {
			throw new IllegalArgumentException("Each color array must have exactly 3 elements.");
		}

		float red = textureColor[0] * tileColor[0];
		float green = textureColor[1] * tileColor[1];
		float blue = textureColor[2] * tileColor[2];

		return ColorUtils.srgbToPackedHsl(ColorUtils.linearToSrgb(new float[] { red, green, blue }));
	}

	private int environmentalLighting(int packedHsl) {
		var rgb = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(packedHsl));
		for (int i = 0; i < 3; i++) {
			rgb[i] *=
				environmentManager.currentAmbientColor[i] * environmentManager.currentAmbientStrength +
				environmentManager.currentDirectionalColor[i] * environmentManager.currentDirectionalStrength;
			rgb[i] = clamp(rgb[i], 0, 1);
		}
		return ColorUtils.srgbToPackedHsl(ColorUtils.linearToSrgb(rgb));
	}

}
