package rs117.hd.renderer;

import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

@Slf4j
@Singleton
public class ZoneRenderer implements Renderer {
	@Override
	public void loadScene(WorldView worldView, Scene scene) {
		log.debug("loadScene({}, {})", worldView, scene);
	}

	@Override
	public void swapScene(Scene scene) {
		log.debug("swapScene({})", scene);
	}

	@Override
	public void invalidateZone(Scene scene, int zoneX, int zoneZ) {
		log.debug("invalidateZone({}, zoneX={}, zoneZ={})", scene, zoneX, zoneZ);
	}

	@Override
	public void despawnWorldView(WorldView worldView) {
		log.debug("despawnWorldView({})", worldView);
	}

	@Override
	public void preSceneDraw(
		Scene scene,
		float cameraX,
		float cameraY,
		float cameraZ,
		float cameraPitch,
		float cameraYaw,
		int minLevel,
		int level,
		int maxLevel,
		Set<Integer> hideRoofIds
	) {
		log.debug(
			"preSceneDraw({}, cameraPos=[{}, {}, {}], cameraOri=[{}, {}], minLevel={}, level={}, maxLevel={}, hideRoofIds=[{}])",
			scene,
			cameraX,
			cameraY,
			cameraZ,
			cameraPitch,
			cameraYaw,
			minLevel,
			level,
			maxLevel,
			hideRoofIds.stream().map(i -> Integer.toString(i)).collect(
				Collectors.joining(", "))
		);
		if (scene.getWorldViewId() == WorldView.TOPLEVEL)
			scene.setDrawDistance(Constants.EXTENDED_SCENE_SIZE);
	}

	@Override
	public void postSceneDraw(Scene scene) {
		log.debug("postSceneDraw({})", scene);
	}

	@Override
	public void drawPass(Projection entityProjection, Scene scene, int pass) {
		log.debug("drawPass({}, {}, pass={})", entityProjection, scene, pass);
	}

	@Override
	public void drawZoneOpaque(Projection entityProjection, Scene scene, int zx, int zz) {
		log.debug("drawZoneOpaque({}, {}, zx={}, zz={})", entityProjection, scene, zx, zz);
	}

	@Override
	public void drawZoneAlpha(Projection entityProjection, Scene scene, int level, int zx, int zz) {
		log.debug("drawZoneAlpha({}, {}, zx={}, zz={})", entityProjection, scene, zx, zz);
	}

	@Override
	public void drawDynamic(
		Projection worldProjection,
		Scene scene,
		TileObject tileObject,
		Renderable r,
		Model m,
		int orient,
		int x,
		int y,
		int z
	) {
		log.debug(
			"drawDynamic({}, {}, tileObject={}, renderable={}, model={}, orientation={}, modelPos=[{}, {}, {}])",
			worldProjection, scene, tileObject, r, m, orient, x, y, z
		);
	}

	@Override
	public void drawTemp(Projection worldProjection, Scene scene, GameObject gameObject, Model m) {
		log.debug("drawTemp({}, {}, gameObject={}, model={})", worldProjection, scene, gameObject, m);
	}

	@Override
	public void draw(int overlaySrgba) {
		log.debug("draw(overlaySrgba={})", overlaySrgba);
	}
}
