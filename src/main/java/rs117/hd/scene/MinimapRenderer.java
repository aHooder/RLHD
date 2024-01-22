package rs117.hd.scene;

import javax.inject.Inject;
import net.runelite.api.*;
import rs117.hd.HdPlugin;
import rs117.hd.HdPluginConfig;
import rs117.hd.config.MinimapStyle;
import rs117.hd.data.WaterType;
import rs117.hd.data.materials.Overlay;
import rs117.hd.data.materials.Underlay;
import rs117.hd.overlays.FrameTimer;
import rs117.hd.overlays.Timer;
import rs117.hd.utils.ColorUtils;

import static net.runelite.api.Constants.*;
import static rs117.hd.scene.SceneUploader.SCENE_OFFSET;
import static rs117.hd.utils.HDUtils.clamp;

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

						Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
						WaterType waterType = proceduralGenerator.tileWaterType(scene, tile, paint);
						if (waterType == WaterType.NONE) {
							if (overlay != Overlay.NONE) {
								swColor = overlay.modifyColor(swColor);
								seColor = overlay.modifyColor(seColor);
								nwColor = overlay.modifyColor(nwColor);
								neColor = overlay.modifyColor(neColor);
							} else {
								Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);
								swColor = underlay.modifyColor(swColor);
								seColor = underlay.modifyColor(seColor);
								nwColor = underlay.modifyColor(nwColor);
								neColor = underlay.modifyColor(neColor);
							}
						} else {
							swColor = seColor = nwColor = neColor = 127;
						}

						if (!classicLighting) {
							swColor = environmentalLighting(swColor);
							seColor = environmentalLighting(seColor);
							nwColor = environmentalLighting(nwColor);
							neColor = environmentalLighting(neColor);

							// Ensure hue and saturation are identical
							seColor = swColor & 0xFF80 | seColor & 0x7F;
							nwColor = swColor & 0xFF80 | nwColor & 0x7F;
							neColor = swColor & 0xFF80 | neColor & 0x7F;
						}

						sceneContext.minimapTilePaintColors[z][x][y][0] = swColor;
						sceneContext.minimapTilePaintColors[z][x][y][1] = seColor;
						sceneContext.minimapTilePaintColors[z][x][y][2] = nwColor;
						sceneContext.minimapTilePaintColors[z][x][y][3] = neColor;
					}

					var model = tile.getSceneTileModel();
					if (model != null) {
						final int[] faceColorA = model.getTriangleColorA();
						final int[] faceColorB = model.getTriangleColorB();
						final int[] faceColorC = model.getTriangleColorC();

						final int faceCount = model.getFaceX().length;

						for (int face = 0; face < faceCount; ++face) {
							int colorA = faceColorA[face];
							int colorB = faceColorB[face];
							int colorC = faceColorC[face];

							var waterType = proceduralGenerator.faceWaterType(scene, tile, face, model);
							if (waterType == WaterType.NONE) {
								if (ProceduralGenerator.isOverlayFace(tile, face)) {
									Overlay overlay = Overlay.getOverlay(scene, tile, plugin);
									colorA = overlay.modifyColor(colorA);
									colorB = overlay.modifyColor(colorB);
									colorC = overlay.modifyColor(colorC);
								} else {
									Underlay underlay = Underlay.getUnderlay(scene, tile, plugin);
									colorA = underlay.modifyColor(colorA);
									colorB = underlay.modifyColor(colorB);
									colorC = underlay.modifyColor(colorC);
								}
							} else {
								// set colors for the shoreline to create a foam effect in the water shader
								colorA = colorB = colorC = 127;
							}

							if (!classicLighting) {
								colorA = environmentalLighting(colorA);
								colorB = environmentalLighting(colorB);
								colorC = environmentalLighting(colorC);

								// Ensure hue and saturation are identical
								colorB = colorA & 0xFF80 | colorB & 0x7F;
								colorC = colorA & 0xFF80 | colorC & 0x7F;
							}

							sceneContext.minimapTileModelColors[z][x][y][face][0] = colorA;
							sceneContext.minimapTileModelColors[z][x][y][face][1] = colorB;
							sceneContext.minimapTileModelColors[z][x][y][face][2] = colorC;
						}
					}
				}
			}
		}
	}

	private static int blend(int baseHsl, int brightnessFactorHsl) {
		int lightness = (baseHsl & 0x7F) * (brightnessFactorHsl & 0x7F) >> 7;
		return (baseHsl & ~0x7F) | clamp(lightness, 0, 0x7F);
	}

	public void drawTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
		frameTimer.begin(Timer.MINIMAP_DRAW);
		if (config.minimapType() == MinimapStyle.DEFAULT || config.minimapType() == MinimapStyle.HD2008) {
			drawMinimapShaded(tile, tx, ty, px0, py0, px1, py1);
		} else {
			drawMinimapOSRS(tile, tx, ty, px0, py0, px1, py1);
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

			int tex = paint.getTexture();
			if (tex == -1) {
				if (paint.getNwColor() != 12345678) {
					fillGradient(px0, py0, px1, py1, nwColor, neColor, swColor, seColor);
				} else {
					client.getRasterizer().fillRectangle(px0, py0, px1 - px0, py1 - py0, paint.getRBG());
				}
			} else {
				int hsl = client.getTextureProvider().getDefaultColor(tex);
				fillGradient(px0, py0, px1, py1, blend(hsl, nwColor), blend(hsl, neColor), blend(hsl, swColor), blend(hsl, seColor));
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

				if (textures != null && textures[face] != -1) {
					int hsl = client.getTextureProvider().getDefaultColor(textures[face]);
					client.getRasterizer().rasterGouraud(
						tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
						tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
						blend(hsl, c1), blend(hsl, c2), blend(hsl, c3)
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
