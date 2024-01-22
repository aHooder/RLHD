package rs117.hd.scene;

import lombok.Getter;
import net.runelite.api.*;

@Getter
public class SceneTileModelCustom implements SceneTileModel {

	public static final int[][] SHAPE_POINTS = {
		{1, 3, 5, 7},
		{1, 3, 5, 7},
		{1, 3, 5, 7},
		{1, 3, 5, 7, 6},
		{1, 3, 5, 7, 6},
		{1, 3, 5, 7, 6},
		{1, 3, 5, 7, 6},
		{1, 3, 5, 7, 2, 6},
		{1, 3, 5, 7, 2, 8},
		{1, 3, 5, 7, 2, 8},
		{1, 3, 5, 7, 11, 12},
		{1, 3, 5, 7, 11, 12},
		{1, 3, 5, 7, 13, 14},
	};

	public static final int[][] SHAPE_PATHS = {
		{0, 1, 2, 3, 0, 0, 1, 3},
		{1, 1, 2, 3, 1, 0, 1, 3},
		{0, 1, 2, 3, 1, 0, 1, 3},
		{0, 0, 1, 2, 0, 0, 2, 4, 1, 0, 4, 3},
		{0, 0, 1, 4, 0, 0, 4, 3, 1, 1, 2, 4},
		{0, 0, 4, 3, 1, 0, 1, 2, 1, 0, 2, 4},
		{0, 1, 2, 4, 1, 0, 1, 4, 1, 0, 4, 3},
		{0, 4, 1, 2, 0, 4, 2, 5, 1, 0, 4, 5, 1, 0, 5, 3},
		{0, 4, 1, 2, 0, 4, 2, 3, 0, 4, 3, 5, 1, 0, 4, 5},
		{0, 0, 4, 5, 1, 4, 1, 2, 1, 4, 2, 3, 1, 4, 3, 5},
		{0, 0, 1, 5, 0, 1, 4, 5, 0, 1, 2, 4, 1, 0, 5, 3, 1, 5, 4, 3, 1, 4, 2, 3},
		{1, 0, 1, 5, 1, 1, 4, 5, 1, 1, 2, 4, 0, 0, 5, 3, 0, 5, 4, 3, 0, 4, 2, 3},
		{1, 0, 5, 4, 1, 0, 1, 5, 0, 0, 4, 3, 0, 4, 5, 3, 0, 5, 2, 3, 0, 1, 2, 5},
	};

	int[] vertexY;
	int[] triangleColorA;
	int[] faceY;
	int[] faceX;
	int[] faceZ;
	int[] vertexX;
	int[] vertexZ;

	SceneTileModelCustom(final int shape, final int rotation, final int unused, final int tileX, final int tileY, final int posSW, final int posSE, final int northeastY, final int posNW, final int colorSW, final int colorSE, final int colorNE, final int colorNW, final int colorSW2, final int colorSE2, final int colorNE2, final int colorNW2, final int underlayRgb, final int overlayRgb) {

		final int SIZE = 128;
		final int HALF_SIZE = SIZE / 2;
		final int QUARTER_SIZE = SIZE / 4;
		final int THREE_QUARTER_SIZ = SIZE * 3 / 4;
		final int[] shapeVertices = SceneTileModelCustom.SHAPE_POINTS[shape];
		final int vertexCount = shapeVertices.length;

		this.vertexX = new int[vertexCount];
		this.vertexY = new int[vertexCount];
		this.vertexZ = new int[vertexCount];
		final int[] colors = new int[vertexCount];
		final int[] elevations = new int[vertexCount];

		final int posX = tileX * SIZE;
		final int posZ = tileY * SIZE;

		int[][] vertexMappings = {
			{1, posX, posZ, posSW, colorSW, colorSW2},
			{2, posX + HALF_SIZE, posZ, (posSW + posSE) >> 1, (colorSW + colorSE) >> 1, (colorSW2 + colorSE2) >> 1},
			{3, posX + SIZE, posZ, posSE, colorSE, colorSE2},
			{4, posX + SIZE, posZ + HALF_SIZE, (posSE + northeastY) >> 1, (colorSE + colorNE) >> 1, (colorSE2 + colorNE2) >> 1},
			{5, posX + SIZE, posZ + SIZE, northeastY, colorNE, colorNE2},
			{6, posX + HALF_SIZE, posZ + SIZE, (northeastY + posNW) >> 1, (colorNE + colorNW) >> 1, (colorNE2 + colorNW2) >> 1},
			{7, posX, posZ + SIZE, posNW, colorNW, colorNW2},
			{8, posX, posZ + HALF_SIZE, (posNW + posSW) >> 1, (colorNW + colorSW) >> 1, (colorNW2 + colorSW2) >> 1},
			{9, posX + HALF_SIZE, posZ + QUARTER_SIZE, (posSW + posSE) >> 1, (colorSW + colorSE) >> 1, (colorSW2 + colorSE2) >> 1},
			{10, posX + THREE_QUARTER_SIZ, posZ + HALF_SIZE, (posSE + northeastY) >> 1, (colorSE + colorNE) >> 1, (colorSE2 + colorNE2) >> 1},
			{11, posX + HALF_SIZE, posZ + THREE_QUARTER_SIZ, (northeastY + posNW) >> 1, (colorNE + colorNW) >> 1, (colorNE2 + colorNW2) >> 1},
			{12, posX + QUARTER_SIZE, posZ + HALF_SIZE, (posNW + posSW) >> 1, (colorNW + colorSW) >> 1, (colorNW2 + colorSW2) >> 1},
			{13, posX + QUARTER_SIZE, posZ + QUARTER_SIZE, posSW, colorSW, colorSW2},
			{14, posX + THREE_QUARTER_SIZ, posZ + QUARTER_SIZE, posSE, colorSE, colorSE2},
			{15, posX + THREE_QUARTER_SIZ, posZ + THREE_QUARTER_SIZ, northeastY, colorNE, colorNE2},
			{16, posX + QUARTER_SIZE, posZ + THREE_QUARTER_SIZ, posNW, colorNW, colorNW2}
		};

		for (int i = 0; i < vertexCount; ++i) {
			int vertex = shapeVertices[i];
			if ((vertex & 0x1) == 0x0 && vertex <= 8) {
				vertex = (vertex - rotation - rotation - 1 & 0x7) + 1;
			}
			if (vertex > 8 && vertex <= 12) {
				vertex = (vertex - 9 - rotation & 0x3) + 9;
			}
			if (vertex > 12 && vertex <= 16) {
				vertex = (vertex - 13 - rotation & 0x3) + 13;
			}
			for (int[] mapping : vertexMappings) {
				if (vertex == mapping[0]) {
					this.vertexX[i] = mapping[1];
					this.vertexY[i] = mapping[3];
					this.vertexZ[i] = mapping[2];
					colors[i] = mapping[4];
					elevations[i] = mapping[5];
					break;
				}
			}
		}
		final int[] shapeFaces = SceneTileModelCustom.SHAPE_PATHS[shape];
		final int faceCount = shapeFaces .length / 4;
		this.faceX = new int[faceCount];
		this.faceY = new int[faceCount];
		this.faceZ = new int[faceCount];
		this.triangleColorA = new int[faceCount];


		int index = 0;
		for (int i = 0; i < faceCount; ++i) {
			final int type = shapeFaces[index];
			int v1 = shapeFaces[index + 1];
			int v2 = shapeFaces[index + 2];
			int v3 = shapeFaces[index + 3];
			index += 4;

			if (v1 < 4) v1 = (v1 - rotation & 3);
			if (v2 < 4) v2 = (v2 - rotation & 3);
			if (v3 < 4) v3 = (v3 - rotation & 3);

			this.faceX[i] = v1;
			this.faceY[i] = v2;
			this.faceZ[i] = v3;
			this.triangleColorA[i] = type == 0 ? colors[v1] : elevations[v1];
		}

	}

	@Override
	public int getModelUnderlay() { return 0; }

	@Override
	public int getModelOverlay() { return 0; }

	@Override
	public int getShape() { return 0; }

	@Override
	public int getRotation() { return 0; }

	@Override
	public int[] getTriangleColorB() { return new int[0]; }

	@Override
	public int[] getTriangleColorC() { return new int[0]; }

	@Override
	public int[] getTriangleTextureId() { return new int[0]; }

	@Override
	public boolean isFlat() {
		return false;
	}

	@Override
	public int getBufferOffset() { return 0; }

	@Override
	public void setBufferOffset(int i) { }

	@Override
	public int getUvBufferOffset() { return 0; }

	@Override
	public void setUvBufferOffset(int i) { }

	@Override
	public int getBufferLen() { return 0; }

	@Override
	public void setBufferLen(int i) { }
}
