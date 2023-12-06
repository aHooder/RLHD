package rs117.hd.scene;

import java.util.Arrays;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.RuneLite;
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

	private final boolean minimapGroundBlending = false;

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
					Tile tile = scene.getExtendedTiles()[z][x][y];
					if (tile == null) continue;
					processTilePaint(scene, sceneContext, tile, z, x, y, classicLighting);
					processTileModel(scene, sceneContext, tile, z, x, y, classicLighting);
				}
			}
		}

	}

	private void processTilePaint(Scene scene, SceneContext sceneContext, Tile tile, int z, int x, int y, boolean classicLighting) {
		SceneTilePaint paint = tile.getSceneTilePaint();
		if (paint == null) {
			return;
		}

		// Initialize color and material arrays
		int[] colors = { paint.getSwColor(), paint.getSeColor(), paint.getNwColor(), paint.getNeColor() };
		Material[] materials = new Material[4];

		Arrays.fill(materials, Material.NONE);

		int tileTexture = paint.getTexture();
		int[] vertexKeys = ProceduralGenerator.tileVertexKeys(scene, tile);

		final Point tilePoint = tile.getSceneLocation();
		final int tileX = tilePoint.getX();
		final int tileY = tilePoint.getY();
		final int tileZ = tile.getRenderLevel();
		int baseX = scene.getBaseX();
		int baseY = scene.getBaseY();

		WaterType waterType = proceduralGenerator.tileWaterType(scene, tile, paint);
		boolean hiddenTile = paint.getNwColor() == 12345678;
		int hiddenRBG = ColorUtils.srgbToPackedHsl(ColorUtils.unpackARGB(paint.getRBG()));
		Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
		Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);

		if (waterType == WaterType.NONE) {
			for (int i = 0; i < 4; i++) {
				materials[i] = Material.fromVanillaTexture(tileTexture);
				if (minimapGroundBlending && !proceduralGenerator.useDefaultColor(scene, tile) && paint.getTexture() == -1) {
					if (plugin.configGroundTextures) {
						materials[i] = sceneContext.vertexTerrainTexture.getOrDefault(vertexKeys[i], materials[i]);
					}
				} else {
					GroundMaterial groundMaterial = null;
					if (overlay != Overlay.NONE) {
						groundMaterial = overlay.groundMaterial;
						colors[i] = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(colors[i]), classicLighting));
					} else {
						if (underlay != Underlay.NONE) {
							groundMaterial = underlay.groundMaterial;
							colors[i] = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(colors[i]), classicLighting));
						}
					}

					if (plugin.configGroundTextures && groundMaterial != null) {
						materials[i] = groundMaterial.getRandomMaterial(tileZ, baseX + tileX + (i % 2), baseY + tileY + (i / 2));
					}
				}
			}
		} else {
			Arrays.fill(colors, ColorUtils.srgbToPackedHsl(waterType.surfaceColor));
			Arrays.fill(materials, Material.WATER_FLAT);
		}

		float[][] textureColors = new float[4][];
		int[] texturePackedColors = new int[4];
		for (int i = 0; i < 4; i++) {
			if (hiddenTile) {
				colors[i] = hiddenRBG;
			}

			textureColors[i] = textureManager.getTextureAverageColor(materials[i]);
			float[] colorRGB = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(colors[i]));
			texturePackedColors[i] = multiplyAndPackColors(textureColors[i], colorRGB);

			if (hiddenTile) {
				texturePackedColors[i] = hiddenRBG;
			} else {
				texturePackedColors[i] =
					texturePackedColors[0] & 0xFF80 | texturePackedColors[i] & 0x7F; // Ensure hue and saturation consistency
			}
		}

		// Update sceneContext with color information
		System.arraycopy(colors, 0, sceneContext.minimapTilePaintColors[z][x][y], 0, colors.length);
		System.arraycopy(texturePackedColors, 0, sceneContext.minimapTilePaintColors[z][x][y], colors.length, texturePackedColors.length);
	}

	private void processTileModel(Scene scene, SceneContext sceneContext, Tile tile, int z, int x, int y, boolean classicLighting) {
		SceneTileModel model = tile.getSceneTileModel();
		if (model == null) {
			return;
		}

		final int[] faceColorA = model.getTriangleColorA();
		final int[] faceColorB = model.getTriangleColorB();
		final int[] faceColorC = model.getTriangleColorC();
		final int[] faceTextures = model.getTriangleTextureId();

		Point tilePoint = tile.getSceneLocation();
		final int tileX = tilePoint.getX();
		final int tileY = tilePoint.getY();
		final int tileZ = tile.getRenderLevel();
		int baseX = scene.getBaseX();
		int baseY = scene.getBaseY();

		Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
		Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);

		final int faceCount = model.getFaceX().length;
		for (int face = 0; face < faceCount; ++face) {
			int[] colors = { faceColorA[face], faceColorB[face], faceColorC[face] };
			Material[] materials = new Material[3];
			Arrays.fill(materials, Material.NONE);

			if (faceTextures != null) {
				int textureIndex = faceTextures[face];
				Arrays.fill(materials, Material.fromVanillaTexture(textureIndex));
			}

			int[][] localVertices = ProceduralGenerator.faceLocalVertices(tile, face);
			int[] vertexKeys = ProceduralGenerator.faceVertexKeys(tile, face);
			WaterType waterType = proceduralGenerator.faceWaterType(scene, tile, face, model);

			if (waterType == WaterType.NONE) {
				if (minimapGroundBlending &&
					!(ProceduralGenerator.isOverlayFace(tile, face) && proceduralGenerator.useDefaultColor(scene, tile)) &&
					materials[0] == Material.NONE) {
					for (int i = 0; i < vertexKeys.length; i++) {
						colors[i] = sceneContext.vertexTerrainColor.getOrDefault(vertexKeys[i], colors[i]);
						if (plugin.configGroundTextures) {
							materials[i] = sceneContext.vertexTerrainTexture.getOrDefault(vertexKeys[i], materials[i]);
						}
					}
				} else {
					GroundMaterial groundMaterial = null;
					if (ProceduralGenerator.isOverlayFace(tile, face)) {
						if (overlay != Overlay.NONE) {
							groundMaterial = overlay.groundMaterial;
						}
						for (int i = 0; i < colors.length; i++) {
							colors[i] = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(colors[i])));
						}
					} else {
						if (underlay != Underlay.NONE) {
							groundMaterial = underlay.groundMaterial;
						}
						for (int i = 0; i < colors.length; i++) {
							colors[i] = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(colors[i])));
						}
					}
					if (plugin.configGroundTextures && groundMaterial != null) {
						for (int i = 0; i < localVertices.length; i++) {
							materials[i] = groundMaterial.getRandomMaterial(
								tileZ,
								baseX + tileX + (int) Math.floor((float) localVertices[i][0] / LOCAL_TILE_SIZE),
								baseY + tileY + (int) Math.floor((float) localVertices[i][1] / LOCAL_TILE_SIZE)
							);
						}
					}
				}
			} else {
				Arrays.fill(colors, ColorUtils.srgbToPackedHsl(waterType.surfaceColor));
				Arrays.fill(materials, Material.WATER_FLAT);
			}

			float[][] linearColors = new float[colors.length][];
			for (int i = 0; i < colors.length; i++) {
				linearColors[i] = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(colors[i]));
			}


			// Store the processed colors directly
			System.arraycopy(colors, 0, sceneContext.minimapTileModelColors[z][x][y][face], 0, colors.length);

			// Process materials and store them
			int firstMaterialColor = 0;
			for (int i = 0; i < materials.length; i++) {
				float[] materialColor = textureManager.getTextureAverageColor(materials[i]);
				int packedColor = multiplyAndPackColors(materialColor, linearColors[i]);

				if (i == 0) {
					firstMaterialColor = packedColor;
					sceneContext.minimapTileModelColors[z][x][y][face][3 + i] = packedColor;
				} else {
					// Applying the blending operation separately
					int blendedColor = firstMaterialColor & 0xFF80 | packedColor & 0x7F;
					sceneContext.minimapTileModelColors[z][x][y][face][3 + i] = blendedColor;
				}
			}
		}
	}

	public int multiplyAndPackColors(float[] color1, float[] color2) {
		// Ensure each color array has exactly 3 elements (R, G, B)
		if (color1.length != 3 || color2.length != 3) {
			throw new IllegalArgumentException("Each color array must have exactly 3 elements.");
		}

		float red = color1[0] * color2[0];
		float green = color1[1] * color2[1];
		float blue = color1[2] * color2[2];

		return ColorUtils.srgbToPackedHsl(ColorUtils.linearToSrgb(new float[] { red, green, blue }));
	}

	public void drawTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
		frameTimer.begin(Timer.MINIMAP_DRAW);
		try {
			if (config.minimapType() == MinimapStyle.DEFAULT || config.minimapType() == MinimapStyle.HD2008) {
				drawMinimapShaded(tile, tx, ty, px0, py0, px1, py1, config.minimapType() == MinimapStyle.HD2008);
			} else {
				drawMinimapOSRS(tile, tx, ty, px0, py0, px1, py1);
			}
		} catch (Exception e) {
			log.info("Minimap Crashed Defaulting to no minimap");
			client.setMinimapTileDrawer(null);
		}

		frameTimer.end(Timer.MINIMAP_DRAW);
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

	private void drawMinimapShaded(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1, boolean classicLighting) {
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

			if (!classicLighting) {
				swColor = environmentalLighting(swColor);
				seColor = environmentalLighting(seColor);
				nwColor = environmentalLighting(nwColor);
				neColor = environmentalLighting(neColor);
				swTexture = environmentalLighting(swTexture);
				seTexture = environmentalLighting(seTexture);
				nwTexture = environmentalLighting(nwTexture);
				neTexture = environmentalLighting(neTexture);

				seColor = swColor & 0xFF80 | seColor & 0x7F;
				nwColor = swColor & 0xFF80 | nwColor & 0x7F;
				neColor = swColor & 0xFF80 | neColor & 0x7F;

				seTexture = swTexture & 0xFF80 | seTexture & 0x7F;
				nwTexture = swTexture & 0xFF80 | nwTexture & 0x7F;
				neTexture = swTexture & 0xFF80 | neTexture & 0x7F;
			}


			boolean hasTexture = nwTexture != 0;

			fillGradient(
				px0,
				py0,
				px1,
				py1,
				hasTexture ? nwTexture : nwColor,
				hasTexture ? neTexture : neColor,
				hasTexture ? swTexture : swColor,
				hasTexture ? seTexture : seColor
			);


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

				if (!classicLighting) {
					c1 = environmentalLighting(c1);
					c2 = environmentalLighting(c2);
					c3 = environmentalLighting(c3);
					mc1 = environmentalLighting(mc1);
					mc2 = environmentalLighting(mc2);
					mc3 = environmentalLighting(mc3);

					c2 = c1 & 0xFF80 | c2 & 0x7F;
					c3 = c1 & 0xFF80 | c3 & 0x7F;

					mc2 = mc1 & 0xFF80 | mc2 & 0x7F;
					mc3 = mc1 & 0xFF80 | mc3 & 0x7F;

				}


				if ((textures != null && textures[face] != -1)) {
					client.getRasterizer().rasterGouraud(
						tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
						tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
						mc1, mc2, mc3
					);
				} else if (color1[face] != 12345678) {
					client.getRasterizer().rasterGouraud(
						tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
						tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
						mc1 == 0 ? c1 : mc1, mc2 == 0 ? c2 : mc2, mc3 == 0 ? c3 : mc3
					);
				}

			}
		}
	}

	private void fillGradient(int px0, int py0, int px1, int py1, int c00, int c10, int c01, int c11) {
		Rasterizer g3d = client.getRasterizer();
		g3d.rasterGouraud(py0, py0, py1, px0, px1, px0, c00, c10, c01);
		g3d.rasterGouraud(py0, py1, py1, px1, px0, px1, c10, c01, c11);
	}
}
