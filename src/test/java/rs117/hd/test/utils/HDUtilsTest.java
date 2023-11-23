package rs117.hd.test.utils;

import org.junit.Assert;
import org.junit.Test;
import rs117.hd.utils.HDUtils;

public class HDUtilsTest {
	@Test
	public void convexHull2DTest() {
		float[][] points = {
			{ 0, 0 },
			{ 1, 1 },
			{ 2, 3 },
			{ 0, 10 },
			{ 5, 5 },
			{ 10, 10 },
			{ 10, 0 },
		};
		var hull = HDUtils.convexHull2D(points);
		Assert.assertNotNull(hull);
		Assert.assertTrue(hull.length == 4);

		Assert.assertNull(HDUtils.convexHull2D(new float[][] { { 0, 0 }, { 1, 0 } }));
	}
}
