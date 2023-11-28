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
					if (tile == null)
						continue;

					var paint = tile.getSceneTilePaint();
					if (paint != null) {
						int swColor = paint.getSwColor();
						int seColor = paint.getSeColor();
						int nwColor = paint.getNwColor();
						int neColor = paint.getNeColor();

						Material swMaterial = Material.NONE;
						Material seMaterial = Material.NONE;
						Material neMaterial = Material.NONE;
						Material nwMaterial = Material.NONE;

						int tileTexture = paint.getTexture();
						int[] vertexKeys = ProceduralGenerator.tileVertexKeys(scene, tile);
						int swVertexKey = vertexKeys[0];
						int seVertexKey = vertexKeys[1];
						int nwVertexKey = vertexKeys[2];
						int neVertexKey = vertexKeys[3];

						final Point tilePoint = tile.getSceneLocation();
						final int tileX = tilePoint.getX();
						final int tileY = tilePoint.getY();
						final int tileZ = tile.getRenderLevel();

						int baseX = scene.getBaseX();
						int baseY = scene.getBaseY();

						WaterType waterType = proceduralGenerator.tileWaterType(scene, tile, paint);
						if (waterType == WaterType.NONE) {
							swMaterial = Material.fromVanillaTexture(tileTexture);
							seMaterial = Material.fromVanillaTexture(tileTexture);
							neMaterial = Material.fromVanillaTexture(tileTexture);
							nwMaterial = Material.fromVanillaTexture(tileTexture);

							if (plugin.configGroundBlending && !proceduralGenerator.useDefaultColor(scene, tile)
								&& paint.getTexture() == -1) {// get the vertices' colors and textures from hashmaps
								if (plugin.configGroundTextures) {
									swMaterial = sceneContext.vertexTerrainTexture.getOrDefault(swVertexKey, swMaterial);
									seMaterial = sceneContext.vertexTerrainTexture.getOrDefault(seVertexKey, seMaterial);
									neMaterial = sceneContext.vertexTerrainTexture.getOrDefault(neVertexKey, neMaterial);
									nwMaterial = sceneContext.vertexTerrainTexture.getOrDefault(nwVertexKey, nwMaterial);
								}
							} else {
								GroundMaterial groundMaterial = null;
								Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
								if (overlay != Overlay.NONE) {
									groundMaterial = overlay.groundMaterial;
									swColor = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(swColor), classicLighting));
									seColor = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(seColor), classicLighting));
									nwColor = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(nwColor), classicLighting));
									neColor = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(neColor), classicLighting));
								} else {
									Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);
									if (underlay != Underlay.NONE) {
										groundMaterial = underlay.groundMaterial;
										swColor = HDUtils.colorHSLToInt(underlay.modifyColor(
											HDUtils.colorIntToHSL(swColor),
											classicLighting
										));
										seColor = HDUtils.colorHSLToInt(underlay.modifyColor(
											HDUtils.colorIntToHSL(seColor),
											classicLighting
										));
										nwColor = HDUtils.colorHSLToInt(underlay.modifyColor(
											HDUtils.colorIntToHSL(nwColor),
											classicLighting
										));
										neColor = HDUtils.colorHSLToInt(underlay.modifyColor(
											HDUtils.colorIntToHSL(neColor),
											classicLighting
										));
									}
								}

								if (plugin.configGroundTextures && groundMaterial != null) {
									swMaterial = groundMaterial.getRandomMaterial(tileZ, baseX + tileX, baseY + tileY);
									seMaterial = groundMaterial.getRandomMaterial(tileZ, baseX + tileX + 1, baseY + tileY);
									nwMaterial = groundMaterial.getRandomMaterial(tileZ, baseX + tileX, baseY + tileY + 1);
									neMaterial = groundMaterial.getRandomMaterial(tileZ, baseX + tileX + 1, baseY + tileY + 1);
								}
							}
						} else {
							// set colors for the shoreline to create a foam effect in the water shader

							swColor = seColor = nwColor = neColor = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
							swMaterial = seMaterial = nwMaterial = neMaterial = Material.WATER_FLAT;
							if (sceneContext.vertexIsWater.containsKey(swVertexKey) && sceneContext.vertexIsLand.containsKey(swVertexKey)) {
								swColor = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
							}
							if (sceneContext.vertexIsWater.containsKey(seVertexKey) && sceneContext.vertexIsLand.containsKey(seVertexKey)) {
								seColor = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
							}
							if (sceneContext.vertexIsWater.containsKey(nwVertexKey) && sceneContext.vertexIsLand.containsKey(nwVertexKey)) {
								nwColor = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
							}
							if (sceneContext.vertexIsWater.containsKey(neVertexKey) && sceneContext.vertexIsLand.containsKey(neVertexKey)) {
								neColor = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
							}
						}

						if (paint.getNwColor() == 12345678) {
							int packedCol = ColorUtils.srgbToPackedHsl(ColorUtils.unpackARGB(paint.getRBG()));
							swColor = packedCol;
							seColor = packedCol;
							nwColor = packedCol;
							neColor = packedCol;
						}

						if (!classicLighting) {
							swColor = environmentalLighting(swColor);
							seColor = environmentalLighting(seColor);
							nwColor = environmentalLighting(nwColor);
							neColor = environmentalLighting(neColor);
						}

						// Ensure hue and saturation are identical
						seColor = swColor & 0xFF80 | seColor & 0x7F;
						nwColor = swColor & 0xFF80 | nwColor & 0x7F;
						neColor = swColor & 0xFF80 | neColor & 0x7F;

						float[] swColorRBG = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(swColor));
						float[] seColorRBG = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(seColor));
						float[] nwColorRBG = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(nwColor));
						float[] neColorRBG = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(neColor));

						float[] swTextureColor = textureManager.getTextureAverageColor(swMaterial);
						float[] seTextureColor = textureManager.getTextureAverageColor(seMaterial);
						float[] nwTextureColor = textureManager.getTextureAverageColor(nwMaterial);
						float[] neTextureColor = textureManager.getTextureAverageColor(neMaterial);

						int swTexture = multiplyAndPackColors(swTextureColor, swColorRBG);
						int mseColor = multiplyAndPackColors(seTextureColor, seColorRBG);
						int mnwColor = multiplyAndPackColors(nwTextureColor, nwColorRBG);
						int mneColor = multiplyAndPackColors(neTextureColor, neColorRBG);

						if (paint.getNwColor() == 12345678) {
							int packedCol = ColorUtils.srgbToPackedHsl(ColorUtils.unpackARGB(paint.getRBG()));
							swTexture = packedCol;
							mseColor = packedCol;
							mnwColor = packedCol;
							mneColor = packedCol;
						}

						mseColor = swTexture & 0xFF80 | mseColor & 0x7F;
						mnwColor = swTexture & 0xFF80 | mnwColor & 0x7F;
						mneColor = swTexture & 0xFF80 | mneColor & 0x7F;


						sceneContext.minimapTilePaintColors[z][x][y][0] = swColor;
						sceneContext.minimapTilePaintColors[z][x][y][1] = seColor;
						sceneContext.minimapTilePaintColors[z][x][y][2] = nwColor;
						sceneContext.minimapTilePaintColors[z][x][y][3] = neColor;

						sceneContext.minimapTilePaintColorsTextures[z][x][y][0] = swTexture;
						sceneContext.minimapTilePaintColorsTextures[z][x][y][1] = mseColor;
						sceneContext.minimapTilePaintColorsTextures[z][x][y][2] = mnwColor;
						sceneContext.minimapTilePaintColorsTextures[z][x][y][3] = mneColor;


					}

					var model = tile.getSceneTileModel();
					if (model != null) {
						final int[] faceColorA = model.getTriangleColorA();
						final int[] faceColorB = model.getTriangleColorB();
						final int[] faceColorC = model.getTriangleColorC();

						final Point tilePoint = tile.getSceneLocation();
						final int tileX = tilePoint.getX();
						final int tileY = tilePoint.getY();
						final int tileZ = tile.getRenderLevel();


						int baseX = scene.getBaseX();
						int baseY = scene.getBaseY();


						final int[] faceTextures = model.getTriangleTextureId();

						final int faceCount = model.getFaceX().length;

						for (int face = 0; face < faceCount; ++face) {
							int colorA = faceColorA[face];
							int colorB = faceColorB[face];
							int colorC = faceColorC[face];

							int textureIndex = -1;
							Material materialA = Material.NONE;
							Material materialB = Material.NONE;
							Material materialC = Material.NONE;
							int[][] localVertices = ProceduralGenerator.faceLocalVertices(tile, face);
							int[] vertexKeys = ProceduralGenerator.faceVertexKeys(tile, face);
							int vertexKeyA = vertexKeys[0];
							int vertexKeyB = vertexKeys[1];
							int vertexKeyC = vertexKeys[2];

							WaterType waterType = WaterType.NONE;

							waterType = proceduralGenerator.faceWaterType(scene, tile, face, model);

							if (waterType == WaterType.NONE) {
								if (faceTextures != null) {
									textureIndex = faceTextures[face];
									materialA = Material.fromVanillaTexture(textureIndex);
									materialB = Material.fromVanillaTexture(textureIndex);
									materialC = Material.fromVanillaTexture(textureIndex);
								}

								if (plugin.configGroundBlending &&
									!(ProceduralGenerator.isOverlayFace(tile, face) && proceduralGenerator.useDefaultColor(scene, tile)) &&
									materialA == Material.NONE
								) {
									// get the vertices' colors and textures from hashmaps

									colorA = sceneContext.vertexTerrainColor.getOrDefault(vertexKeyA, colorA);
									colorB = sceneContext.vertexTerrainColor.getOrDefault(vertexKeyB, colorB);
									colorC = sceneContext.vertexTerrainColor.getOrDefault(vertexKeyC, colorC);

									if (plugin.configGroundTextures) {
										materialA = sceneContext.vertexTerrainTexture.getOrDefault(vertexKeyA, materialA);
										materialB = sceneContext.vertexTerrainTexture.getOrDefault(vertexKeyB, materialB);
										materialC = sceneContext.vertexTerrainTexture.getOrDefault(vertexKeyC, materialC);
									}
								} else {
									GroundMaterial groundMaterial = null;

									if (ProceduralGenerator.isOverlayFace(tile, face)) {
										Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
										if (overlay != Overlay.NONE)
											groundMaterial = overlay.groundMaterial;

										colorA = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(colorA)));
										colorB = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(colorB)));
										colorC = HDUtils.colorHSLToInt(overlay.modifyColor(HDUtils.colorIntToHSL(colorC)));
									} else {
										Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);
										if (underlay != Underlay.NONE)
											groundMaterial = underlay.groundMaterial;

										colorA = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(colorA)));
										colorB = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(colorB)));
										colorC = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(colorC)));
									}

									if (plugin.configGroundTextures && groundMaterial != null) {
										materialA = groundMaterial.getRandomMaterial(
											tileZ,
											baseX + tileX + (int) Math.floor((float) localVertices[0][0] / LOCAL_TILE_SIZE),
											baseY + tileY + (int) Math.floor((float) localVertices[0][1] / LOCAL_TILE_SIZE)
										);
										materialB = groundMaterial.getRandomMaterial(
											tileZ,
											baseX + tileX + (int) Math.floor((float) localVertices[1][0] / LOCAL_TILE_SIZE),
											baseY + tileY + (int) Math.floor((float) localVertices[1][1] / LOCAL_TILE_SIZE)
										);
										materialC = groundMaterial.getRandomMaterial(
											tileZ,
											baseX + tileX + (int) Math.floor((float) localVertices[2][0] / LOCAL_TILE_SIZE),
											baseY + tileY + (int) Math.floor((float) localVertices[2][1] / LOCAL_TILE_SIZE)
										);
									}
								}
							} else {
								// set colors for the shoreline to create a foam effect in the water shader
								colorA = colorB = colorC = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
								materialA = materialB = materialC = Material.WATER_FLAT;
								if (sceneContext.vertexIsWater.containsKey(vertexKeyA)
									&& sceneContext.vertexIsLand.containsKey(vertexKeyA)) {
									colorA = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
								}
								if (sceneContext.vertexIsWater.containsKey(vertexKeyB)
									&& sceneContext.vertexIsLand.containsKey(vertexKeyB)) {
									colorB = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
								}
								if (sceneContext.vertexIsWater.containsKey(vertexKeyC)
									&& sceneContext.vertexIsLand.containsKey(vertexKeyC)) {
									colorC = ColorUtils.srgbToPackedHsl(waterType.surfaceColor);
								}


							}

							if (!classicLighting) {
								colorA = environmentalLighting(colorA);
								colorB = environmentalLighting(colorB);
								colorC = environmentalLighting(colorC);
							}

							colorB = colorA & 0xFF80 | colorB & 0x7F;
							colorC = colorA & 0xFF80 | colorC & 0x7F;

							float[] colorAA = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(colorA));
							float[] colorBB = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(colorB));
							float[] colorCC = ColorUtils.srgbToLinear(ColorUtils.packedHslToSrgb(colorC));

							sceneContext.minimapTileModelColors[z][x][y][face][0] = colorA;
							sceneContext.minimapTileModelColors[z][x][y][face][1] = colorB;
							sceneContext.minimapTileModelColors[z][x][y][face][2] = colorC;

							float[] materialAColor = textureManager.getTextureAverageColor(materialA);
							float[] materialBColor = textureManager.getTextureAverageColor(materialB);
							float[] materialColor = textureManager.getTextureAverageColor(materialC);

							int materialAA = multiplyAndPackColors(materialAColor, colorAA);
							int materialBB = multiplyAndPackColors(materialBColor, colorBB);
							int materialCC = multiplyAndPackColors(materialColor, colorCC);

							materialBB = materialAA & 0xFF80 | materialBB & 0x7F;
							materialCC = materialAA & 0xFF80 | materialCC & 0x7F;

							sceneContext.minimapTileModelColorsTextures[z][x][y][face][0] = materialAA;
							sceneContext.minimapTileModelColorsTextures[z][x][y][face][1] = materialBB;
							sceneContext.minimapTileModelColorsTextures[z][x][y][face][2] = materialCC;

						}
					}
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

	private static int blend(int baseHsl, int brightnessFactorHsl) {
		int lightness = (baseHsl & 0x7F) * (brightnessFactorHsl & 0x7F) >> 7;
		return (baseHsl & ~0x7F) | clamp(lightness, 0, 0x7F);
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

			int swTexture = sceneContext.minimapTilePaintColorsTextures[plane][tileExX][tileExY][0];
			int seTexture = sceneContext.minimapTilePaintColorsTextures[plane][tileExX][tileExY][1];
			int nwTexture = sceneContext.minimapTilePaintColorsTextures[plane][tileExX][tileExY][2];
			int neTexture = sceneContext.minimapTilePaintColorsTextures[plane][tileExX][tileExY][3];

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


				int mc1 = sceneContext.minimapTileModelColorsTextures[plane][tileExX][tileExY][face][0];
				int mc2 = sceneContext.minimapTileModelColorsTextures[plane][tileExX][tileExY][face][1];
				int mc3 = sceneContext.minimapTileModelColorsTextures[plane][tileExX][tileExY][face][2];

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
						c1, c2, c3
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
