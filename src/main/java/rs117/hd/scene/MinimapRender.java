package rs117.hd.scene;

import net.runelite.api.*;
import rs117.hd.HdPlugin;
import rs117.hd.HdPluginConfig;
import rs117.hd.config.MinimapType;

import javax.inject.Inject;
import rs117.hd.data.materials.Overlay;
import rs117.hd.data.materials.Underlay;
import rs117.hd.utils.HDUtils;

public class MinimapRender {

	public static SceneTileModel[] tileModels = new SceneTileModel[52];

	public static SceneTileModel createTileModel(final int shape, final int rotation, final int unused, final int tileX, final int tileY, final int posSW, final int posSE, final int northeastY, final int posNW, final int colorSW, final int colorSE, final int colorNE, final int colorNW, final int colorSW2, final int colorSE2, final int colorNE2, final int colorNW2, final int underlayRgb, final int overlayRgb) {
		return new SceneTileModelCustom(shape, rotation, unused, tileX, tileY, posSW, posSE, northeastY, posNW, colorSW, colorSE, colorNE, colorNW, colorSW2, colorSE2, colorNE2, colorNW2, underlayRgb, overlayRgb);
	}

    @Inject
    private Client client;

	@Inject
	private HdPlugin plugin;

	@Inject
    private HdPluginConfig config;

    private final int[] tmpScreenX = new int[6];
    private final int[] tmpScreenY = new int[6];

    static int blend(int var0, int var1) {
        var1 = (var0 & 127) * var1 >> 7;
        var1 = Math.max(2, var1);
        var1 = Math.min(126, var1);
        return (var0 & 0xFF80) + var1;
    }

	public void drawTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {

		if (config.minimapType() == MinimapType.SHADED) {
			drawTileShaded(tile,tx,ty,px0,py0,px1,py1);
		} else {
			client.getRasterizer().setRasterGouraudLowRes(false);
			try
			{
				SceneTilePaint paint = tile.getSceneTilePaint();
				if (paint != null)
				{
					int color = paint.getRBG();
					if (color != 0)
					{

						client.getRasterizer().fillRectangle(px0, py0, px1 - px0, py1 - py0, color);
					}
				}
				else
				{
					SceneTileModel model = tile.getSceneTileModel();
					if (model != null)
					{
						SceneTileModel stm = tileModels[(model.getShape() << 2) | model.getRotation() & 3];;
						int[] vertexX = stm.getVertexX();
						int[] vertexZ = stm.getVertexZ();

						int[] indicies1 = stm.getFaceX();
						int[] indicies2 = stm.getFaceY();
						int[] indicies3 = stm.getFaceZ();

						int w = px1 - px0;
						int h = py1 - py0;

						for (int vert = 0; vert < vertexX.length; ++vert)
						{
							tmpScreenX[vert] = px0 + ((vertexX[vert] * w) >> Perspective.LOCAL_COORD_BITS);
							tmpScreenY[vert] = py0 + (((Perspective.LOCAL_TILE_SIZE - vertexZ[vert]) * h) >> Perspective.LOCAL_COORD_BITS);
						}

						for (int face = 0; face < indicies1.length; ++face)
						{
							int idx1 = indicies1[face];
							int idx2 = indicies2[face];
							int idx3 = indicies3[face];

							boolean isUnderlay = stm.getTriangleColorA()[face] == 0;
							int color = isUnderlay
								? model.getModelUnderlay()
								: model.getModelOverlay();
							if (!isUnderlay || color != 0)
							{
								client.getRasterizer().rasterFlat(
									tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
									tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
									color);
							}
						}
					}
				}
			}
			finally
			{
				client.getRasterizer().setRasterGouraudLowRes(true);
			}
		}
	}

	public void drawTileShaded(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
		SceneTilePaint paint = tile.getSceneTilePaint();
		if (paint != null) {
			int swColor = paint.getSwColor();
			int seColor = paint.getSeColor();
			int nwColor = paint.getNwColor();
			int neColor = paint.getNeColor();

			Overlay overlay = Overlay.getOverlay(client.getScene(), tile, plugin);

			if (overlay == Overlay.NONE)
			{
				Underlay underlay = Underlay.getUnderlay(client.getScene(), tile, plugin);

				swColor = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(swColor)));
				seColor = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(seColor)));
				nwColor = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(nwColor)));
				neColor = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(neColor)));
			}


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
			return;
		}

		SceneTileModel stm = tile.getSceneTileModel();
		if (stm == null) {
			return;
		}

		int[] vertexX = stm.getVertexX();
		int[] vertexZ = stm.getVertexZ();

		int[] indicies1 = stm.getFaceX();
		int[] indicies2 = stm.getFaceY();
		int[] indicies3 = stm.getFaceZ();

		int[] color1 = stm.getTriangleColorA();
		int[] color2 = stm.getTriangleColorB();
		int[] color3 = stm.getTriangleColorC();
		int[] textures = stm.getTriangleTextureId();

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


			int c1 = color1[face];
			int c2 = color2[face];
			int c3 = color3[face];

			if (!ProceduralGenerator.isOverlayFace(tile, face)) {
				Underlay underlay = Underlay.getUnderlay(client.getScene(), tile, plugin);
				c1 = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(c1)));
				c2 = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(c2)));
				c3 = HDUtils.colorHSLToInt(underlay.modifyColor(HDUtils.colorIntToHSL(c3)));
			}

			if (textures != null && textures[face] != -1) {
				int hsl = client.getTextureProvider().getDefaultColor(textures[face]);
				client.getRasterizer().rasterGouraud(
					tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
					tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
					blend(hsl, c1), blend(hsl, c2), blend(hsl, c3));
			} else if (color1[face] != 12345678) {
				client.getRasterizer().rasterGouraud(
					tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
					tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
					c1, c2, c3);
			}
		}
	}


	private void fillGradient(int px0, int py0, int px1, int py1, int c00, int c10, int c01, int c11)
	{
		Rasterizer g3d = client.getRasterizer();
		g3d.rasterGouraud(py0, py0, py1, px0, px1, px0, c00, c10, c01);
		g3d.rasterGouraud(py0, py1, py1, px1, px0, px1, c10, c01, c11);
	}

	static {
		for (int index = 0; index < tileModels.length; ++index) {
			int shape = index >> 2;
			int rotation = index & 0x3;

			tileModels[index] = createTileModel(shape, rotation, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0);
		}
	}

}
