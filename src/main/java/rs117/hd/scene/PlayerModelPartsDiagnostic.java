package rs117.hd.scene;

import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import rs117.hd.HdPlugin;
import rs117.hd.scene.materials.Material;
import rs117.hd.scene.model_overrides.ModelOverride;
import rs117.hd.scene.model_overrides.UvType;

/** Visualizes inferred ranges; this does not validate them against client assembly metadata. */
@Slf4j
@Singleton
public class PlayerModelPartsDiagnostic {
	@Inject
	private Client client;
	@Inject
	private HdPlugin plugin;
	@Inject
	private PlayerModelParts modelParts;

	private PlayerModelParts.Result previous;
	// Completed color overrides are published together for render workers.
	private volatile Snapshot snapshot;

	@RequiredArgsConstructor
	private static class Snapshot {
		final Player player;
		final ModelOverride override;
		final ActorSpotAnim[] spotanims;
		final ModelOverride[] spotanimOverrides;
	}

	public ModelOverride getOverride(Renderable renderable, ModelOverride fallback) {
		Snapshot current = snapshot;
		if (current != null) {
			if (renderable == current.player)
				return current.override;
			for (int i = 0; i < current.spotanims.length; i++)
				if (renderable == current.spotanims[i])
					return current.spotanimOverrides[i];
		}
		return fallback;
	}

	public void update(boolean enabled) {
		assert client.isClientThread();
		if (!enabled) {
			snapshot = null;
			previous = null;
			// Definition caches are shared with equipment overrides; do not clear them here.
			return;
		}

		Player local = client.getLocalPlayer();
		try {
			var resolved = modelParts.resolve(local);
			if (resolved == previous && (resolved == null || !resolved.changed))
				return;
			previous = resolved;
			if (resolved == null) {
				snapshot = null;
				return;
			}

			ModelOverride gray = color(0, 0);
			ModelOverride[] effects = new ModelOverride[resolved.spotanims.length];
			Arrays.fill(effects, gray);
			ModelOverride override = color(0, 0);
			if (resolved.parts != null) {
				override.faceOverrides = new ModelOverride[resolved.faceCount];
				var ranges = new StringBuilder();
				if (log.isDebugEnabled() && resolved.animation != -1) {
					byte[] data = client.getIndexConfig().loadData(12, resolved.animation);
					if (data != null) {
						var raw = PlayerModelDefinitions.readHands(data);
						var sequence = client.loadAnimation(resolved.animation);
						ranges.append(" rightHandItem=").append(sequence.getRightHandItem())
							.append(" leftHandItem=").append(sequence.getLeftHandItem())
							.append(" rawRight=").append(raw[1]).append(" rawLeft=").append(raw[0]);
					}
				}
				for (int i = 0; i < resolved.partCount; i++) {
					var part = resolved.parts[i];
					// Stable by slot/model index, or effect ID, rather than the current face offset.
					int hue = part.slot >= 0 ? (part.slot * 23 + part.index * 11) & 63 : (part.spotanimId * 23 + 7) & 63;
					ModelOverride tint = color(hue, 7);
					Arrays.fill(override.faceOverrides, part.startFace, part.startFace + part.faceCount, tint);
					if (part.spotanim != null)
						effects[part.index] = tint;
					ranges.append(" slot=").append(part.slot).append(" spotanim=").append(part.spotanimId)
						.append(" model=").append(part.modelId).append(" hue=").append(hue)
						.append(" faces=[").append(part.startFace).append(',').append(part.startFace + part.faceCount).append(')');
				}
				log.debug("Player model parts: animation={}, gender={}, predicted base faces={}, with effects={};{}",
					resolved.animation, resolved.gender, resolved.baseFaceCount, resolved.faceCount, ranges);
			}
			snapshot = new Snapshot(local, override, resolved.spotanims, effects);
		} catch (RuntimeException ex) {
			// The resolver retains an incomplete result until inputs change, avoiding repeated errors.
			snapshot = local == null ? null : new Snapshot(local, color(0, 0), new ActorSpotAnim[0], new ModelOverride[0]);
			log.warn("Unable to infer player model parts", ex);
		}
	}

	private ModelOverride color(int hue, int saturation) {
		ModelOverride tint = new ModelOverride()
			.material(Material.UNLIT).textureMaterial(Material.UNLIT).forceMaterialChanges(true)
			.uvType(UvType.GEOMETRY).undoVanillaShading(false)
			.setHue(hue).setSaturation(saturation).setLightness(64);
		tint.normalize(plugin);
		return tint;
	}
}
