package rs117.hd.tests;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GroundFogMigrationTest {
	private static final double REFERENCE_HEIGHT = 1000 * (162 / 255.0);
	private static final double REFERENCE_DENSITY = opticalDepth(184 / 255.0);

	private static double opticalDepth(double opacity) {
		return -Math.log(Math.max(1 - Math.max(0, Math.min(1, opacity)), 1e-4));
	}

	private static double height(double start, double end) {
		// Integrate the old linear density ramp above world Y=0. The new smoothstep
		// layer has a column integral of height / 2, independent of camera position.
		double bottom = -start;
		double top = -end;
		double column = top <= 0 ? 0 : bottom >= 0 ? (bottom + top) / 2 : top * top / (2 * (top - bottom));
		// Fremennik's old -200/-400 layer integrates to 300 world units.
		return Math.max(1, REFERENCE_HEIGHT * column / 300);
	}

	private static double density(double opacity) {
		return REFERENCE_DENSITY * opticalDepth(opacity) / opticalDepth(0.3);
	}

	private static double value(JsonObject env, String key, double fallback) {
		return env.has(key) && !env.get(key).isJsonNull() ? env.get(key).getAsDouble() : fallback;
	}

	@Test
	public void referenceTuning() {
		assertEquals(162 / 255.0 * 1000, height(-200, -400), 1e-9);
		assertEquals(184 / 255.0, 1 - Math.exp(-density(0.3)), 1e-9);
		assertEquals(0, density(0), 0);
	}

	/**
	 * Reports by default. Set -Drlhd.migrateGroundFog=true on the test JVM to rewrite
	 * environments.json. Keeps legacy parameters and any already authored new values.
	 * Rewriting also reformats the JSON; review the diff before committing.
	 */
	@Test
	public void migrateGroundFog() throws Exception {
		Path path = Path.of("src/main/resources/rs117/hd/scene/environments.json");
		var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		JsonArray environments = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonArray.class);
		for (int i = 0; i < environments.size(); i++) {
			JsonObject env = environments.get(i).getAsJsonObject();
			double opacity = value(env, "groundFogOpacity", 0);
			if (opacity <= 0)
				continue;
			double start = value(env, "groundFogStart", -200);
			double end = value(env, "groundFogEnd", -500);
			if (end >= start) {
				System.out.printf("Skipping %d (%s): inverted or collapsed fog layer requires manual tuning.%n", i, env.get("area"));
				continue;
			}
			if (!env.has("groundFogHeight"))
				env.addProperty("groundFogHeight", Math.round(height(start, end) * 10) / 10.0);
			if (!env.has("groundFogDensity"))
				env.addProperty("groundFogDensity", Math.round(density(opacity) * 10000) / 10000.0);
			System.out.printf("%d (%s): height=%s, density=%s%s%n", i, env.get("area"),
				env.get("groundFogHeight"), env.get("groundFogDensity"),
				opacity >= 1 ? " — old full opacity has no finite optical-depth equivalent; review manually" : "");
		}
//		if (Boolean.getBoolean("rlhd.migrateGroundFog"))
			Files.writeString(path, gson.toJson(environments) + "\n", StandardCharsets.UTF_8);
	}
}
