package rs117.hd.utils;

import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import rs117.hd.scene.water_types.WaterType;

import static net.runelite.api.Constants.*;
import static rs117.hd.utils.MathUtils.*;

@Slf4j
public class WaterTerrainGenerator {
	public final int width;
	public final int height;
	public final float[][] heights;
	public final boolean[][] pinned;
	public final WaterType[][] waterTypes;

	public WaterTerrainGenerator(int width, int height) {
		this.width = width;
		this.height = height;
		this.heights = new float[MAX_Z][width * height];
		this.pinned = new boolean[MAX_Z][width * height];
		this.waterTypes = new WaterType[MAX_Z][width * height];
	}

	public void reset() {
		for (int plane = 0; plane < MAX_Z; plane++) {
			Arrays.fill(heights[plane], Float.NaN);
			Arrays.fill(pinned[plane], false);
			Arrays.fill(waterTypes[plane], WaterType.NONE);
		}
	}

	private int coalescePlane(int plane, int x, int y, int h) {
		int i = y * width + x;
		for (int z = 0; z <= plane; ++z)
			if (heights[z][i] == h)
				return z; // Coalesce
		return plane;
	}

	public void setVertex(int plane, int x, int y, int h, WaterType waterType) {
		plane = coalescePlane(plane, x, y, h);
		int idx = y * width + x;
		if (pinned[plane][idx])
			return;

		if (waterType == WaterType.NONE) {
			heights[plane][idx] = h;
			pinned[plane][idx] = true;
		} else {
			heights[plane][idx] = h + waterType.depth;
		}
		waterTypes[plane][idx] = waterType;
	}

	// BFS outward from all initialized cells to fill uninitialized (NaN) cells
	// with the value and type of their nearest initialized neighbour
	public void fillUninitialized() {
		int N = width * height;
		for (int plane = 0; plane < MAX_Z; plane++) {
			int[] queue = new int[N];
			int head = 0, tail = 0;

			for (int i = 0; i < N; i++) {
				if (!Float.isNaN(heights[plane][i]))
					queue[tail++] = i;
			}

			int[] dx = { -1, 1, 0, 0 };
			int[] dy = { 0, 0, -1, 1 };

			while (head < tail) {
				int i = queue[head++];
				int x = i % width;
				int y = i / width;

				for (int d = 0; d < 4; d++) {
					int nx = x + dx[d];
					int ny = y + dy[d];
					if (nx < 0 || nx >= width || ny < 0 || ny >= height)
						continue;

					int n = ny * width + nx;
					if (!Float.isNaN(heights[plane][n]))
						continue;

					heights[plane][n] = heights[plane][i];
					pinned[plane][n] = pinned[plane][i];
					waterTypes[plane][n] = waterTypes[plane][i];
					queue[tail++] = n;
				}
			}
		}
	}

	public void pinContourLines() {
		for (int plane = 0; plane < MAX_Z; plane++) {
			float[] originalHeights = copy(heights[plane]);

			// Pin the centers between adjacent cells of different water types,
			// instead of pinning the grid-aligned corners directly. Every cell
			// already has a water type (waterTypes[i]); the contour lies
			// between cells of DIFFERENT types. For each cell i and each of its
			// 4 grid-aligned neighbours k at distance 4, if waterTypes[k] !=
			// waterTypes[i], pin the midpoint (distance 2) to the average of
			// heights[i] and heights[k]. Multiple writers to the same midpoint
			// (which happens for diagonal contours) are averaged together.
			int[] offsets = { -4, 4, -width * 4, width * 4 };
			int[] midOffsets = { -2, 2, -width * 2, width * 2 };

			float[] pinSum = new float[heights[plane].length];
			int[] pinCount = new int[heights[plane].length];

			for (int x = 4; x < width - 4; ++x) {
				for (int y = 4; y < height - 4; ++y) {
					int i = y * width + x;

					for (int j = 0; j < offsets.length; ++j) {
						int k = i + offsets[j];
						if (waterTypes[plane][k] == waterTypes[plane][i])
							continue;

						int m = i + midOffsets[j];
						pinSum[m] += 0.5f * (originalHeights[i] + originalHeights[k]);
						pinCount[m]++;
					}
				}
			}

			for (int i = 0; i < heights[plane].length; ++i) {
				if (pinCount[i] > 0 && waterTypes[plane][i] != WaterType.NONE) {
					heights[plane][i] = pinSum[i] / pinCount[i];
					pinned[plane][i] = true;
				}
			}
		}
	}

	public void solve() {
		pinContourLines();
		fillUninitialized();
		for (int plane = 0; plane < MAX_Z; plane++)
			MultigridLaplace.solve(heights[plane], pinned[plane], width, height);
	}

	public int getDepth(int plane, int x, int y, int h) {
		plane = coalescePlane(plane, x, y, h);
		return round(heights[plane][y * width + x] - h);
	}
}
