package rs117.hd.scene.lights;

import net.runelite.api.*;
import net.runelite.api.coords.*;
import rs117.hd.utils.HDUtils;

public class Light
{
	public final float randomOffset = HDUtils.rand.nextFloat();
	public final LightDefinition def;

	public int radius;
	public float strength;
	/**
	 * Linear color space RGBA in the range [0, 1]
	 */
	public float[] color;
	public float animation = 0.5f;
	public float duration;
	public float fadeInDuration;
	public float fadeOutDuration;
	public float spawnDelay;
	public float despawnDelay;

	public boolean visible;
	public boolean parentExists;
	public boolean withinViewingDistance = true;
	public boolean markedForRemoval;
	public boolean persistent;
	public boolean replayable;

	public final boolean animationSpecific;
	public final boolean dynamicLifetime;

	public float elapsedTime;
	public float enteredViewingDistanceAt = -1;
	public float lifetime = -1;

	public WorldPoint worldPoint;
	public boolean belowFloor;
	public boolean aboveFloor;
	public int plane;
	public int prevPlane = -1;
	public int prevTileX = -1;
	public int prevTileY = -1;
	public Alignment alignment;
	public int[] origin = new int[3];
	public int[] offset = new int[3];
	public int[] pos = new int[3];
	public int orientation;
	public int distanceSquared;

	public Actor actor;
	public Projectile projectile;
	public TileObject tileObject;
	public GraphicsObject graphicsObject;
	public int spotAnimId = -1;
	public int preOrientation;
	public int[] projectileRefCounter;
	public long hash;

	public int sizeX = 1;
	public int sizeY = 1;

	public Light(LightDefinition def) {
		this.def = def;
		System.arraycopy(def.offset, 0, offset, 0, 3);
		duration = Math.max(0, def.duration) / 1000f;
		fadeInDuration = Math.max(0, def.fadeInDuration) / 1000f;
		fadeOutDuration = Math.max(0, def.fadeOutDuration) / 1000f;
		spawnDelay = Math.max(0, def.spawnDelay) / 1000f;
		despawnDelay = Math.max(0, def.despawnDelay) / 1000f;
		color = def.color;
		radius = def.radius;
		strength = def.strength;
		alignment = def.alignment;
		plane = def.plane;
		if (def.type == LightType.PULSE)
			animation = (float) Math.random();

		// Old way of setting a fixed lifetime
		if (def.fixedDespawnTime)
			lifetime = spawnDelay + despawnDelay;

		if (lifetime == -1) {
			dynamicLifetime = true;
			// If the despawn is dynamic, ensure there's enough time for the light to fade out
			despawnDelay = Math.max(despawnDelay, fadeOutDuration);
		} else {
			dynamicLifetime = false;
		}

		animationSpecific = !def.animationIds.isEmpty();
		if (animationSpecific) {
			persistent = replayable = true;
			// Initially hide the light
			if (dynamicLifetime) {
				lifetime = 0;
			} else {
				elapsedTime = lifetime;
			}
		}
	}
}
