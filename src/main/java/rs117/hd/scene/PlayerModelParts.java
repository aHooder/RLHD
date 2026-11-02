package rs117.hd.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Renderable;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.KitLoader;
import net.runelite.cache.definitions.loaders.SequenceLoader;
import net.runelite.cache.definitions.loaders.SpotAnimLoader;
import rs117.hd.HdPlugin;
import rs117.hd.scene.materials.Material;
import rs117.hd.scene.model_overrides.ModelOverride;
import rs117.hd.scene.model_overrides.UvType;

/** Experimental face provenance inferred from the historical player assembly order. */
@Slf4j
@Singleton
public class PlayerModelParts {
	@Inject
	private Client client;
	@Inject
	private HdPlugin plugin;

	private final Map<Integer, Integer> faceCounts = new HashMap<>();
	private int[] equipment;
	private int animation;
	private int gender;
	private Player player;
	private ActorSpotAnim[] spotanims;
	private int[] spotanimIds;

	// Only this immutable result is read by render workers. Cache loading stays on the client thread.
	private volatile Result result;

	private static class Result {
		final Player player;
		final ModelOverride override;
		final ActorSpotAnim[] spotanims;
		final ModelOverride[] spotanimOverrides;

		Result(Player player, ModelOverride override, ActorSpotAnim[] spotanims, ModelOverride[] spotanimOverrides) {
			this.player = player;
			this.override = override;
			this.spotanims = spotanims;
			this.spotanimOverrides = spotanimOverrides;
		}
	}

	public ModelOverride getOverride(Renderable renderable, ModelOverride fallback) {
		Result current = result;
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
		Player local = client.getLocalPlayer();
		PlayerComposition composition = local == null ? null : local.getPlayerComposition();
		if (!enabled || composition == null || composition.getTransformedNpcId() != -1) {
			result = null;
			player = null;
			equipment = null;
			spotanims = null;
			spotanimIds = null;
			faceCounts.clear();
			return;
		}

		int[] worn = composition.getEquipmentIds();
		int action = local.getAnimation();
		var active = new ArrayList<ActorSpotAnim>();
		for (ActorSpotAnim spotanim : local.getSpotAnims())
			if (spotanim.getFrame() >= 0 && spotanim.getStartCycle() <= client.getGameCycle())
				active.add(spotanim);
		ActorSpotAnim[] effects = active.toArray(new ActorSpotAnim[0]);
		int[] effectIds = new int[effects.length];
		for (int i = 0; i < effects.length; i++)
			effectIds[i] = effects[i].getId();
		if (player == local && gender == composition.getGender() && animation == action && Arrays.equals(equipment, worn)
			&& Arrays.equals(spotanims, effects) && Arrays.equals(spotanimIds, effectIds))
			return;

		player = local;
		gender = composition.getGender();
		animation = action;
		equipment = worn.clone();
		spotanims = effects;
		spotanimIds = effectIds;
		ModelOverride gray = color(0, 0);
		ModelOverride[] effectOverrides = new ModelOverride[effects.length];
		Arrays.fill(effectOverrides, gray);
		result = new Result(local, gray, effects, effectOverrides.clone());

		try {
			int[] effective = worn.clone();
			var ranges = new StringBuilder();
			if (action != -1) {
				byte[] data = client.getIndexConfig().loadData(12, action);
				if (data == null) {
					equipment = null;
					return;
				}
				var raw = new SequenceLoader().load(action, data);
				var sequence = client.loadAnimation(action);
				int right = sequence.getRightHandItem();
				int left = sequence.getLeftHandItem();
				// Raw sequence fields distinguish no substitution (-1) from an empty hand (0).
				effective[3] = handEquipment(raw.rightHandItem, right, effective[3]);
				effective[5] = handEquipment(raw.leftHandItem, left, effective[5]);
				ranges.append(" rightHand=").append(right).append(" rawRight=").append(raw.rightHandItem)
					.append(" leftHand=").append(left).append(" rawLeft=").append(raw.leftHandItem);
			}

			var faces = new ArrayList<ModelOverride>();
			for (int slot = 0; slot < effective.length; slot++) {
				int value = effective[slot];
				int[] models;
				if (value >= PlayerComposition.ITEM_OFFSET) {
					int id = value - PlayerComposition.ITEM_OFFSET;
					byte[] data = client.getIndexConfig().loadData(10, id);
					if (data == null) {
						equipment = null; // Retry when the cache data arrives.
						return;
					}
					var item = new ItemLoader().load(id, data);
					models = gender == 1 ? new int[] { item.femaleModel0, item.femaleModel1, item.femaleModel2 } :
						new int[] { item.maleModel0, item.maleModel1, item.maleModel2 };
				} else if (value >= PlayerComposition.KIT_OFFSET) {
					int id = value - PlayerComposition.KIT_OFFSET;
					byte[] data = client.getIndexConfig().loadData(3, id);
					if (data == null) {
						equipment = null;
						return;
					}
					models = new KitLoader().load(id, data).models;
				} else {
					continue;
				}
				if (models == null)
					continue;
				for (int part = 0; part < models.length; part++) {
					int id = models[part];
					if (id == -1)
						break; // Worn secondary/tertiary models require the preceding models.
					Integer count = faceCounts.get(id);
					if (count == null) {
						var model = client.loadModelData(id);
						if (model == null) {
							equipment = null;
							return;
						}
						count = model.getFaceCount();
						faceCounts.put(id, count);
					}
					// Stable by slot and source-model index, so equipment changes don't recolor other slots.
					int hue = (slot * 23 + part * 11) & 63;
					ModelOverride tint = color(hue, 7);
					ranges.append(" slot=").append(slot).append(" model=").append(id)
						.append(" hue=").append(hue).append(" faces=[").append(faces.size())
						.append(',').append(faces.size() + count).append(')');
					for (int face = 0; face < count; face++)
						faces.add(tint);
				}
			}
			int baseFaces = faces.size();
			// Test the hypothesis that combined effects follow the actor's iteration order.
			// Separately rendered effects receive the same tint without relying on this assumption.
			for (int i = 0; i < effects.length; i++) {
				int id = effectIds[i];
				byte[] data = client.getIndexConfig().loadData(13, id);
				if (data == null) {
					equipment = null;
					return;
				}
				int modelId = new SpotAnimLoader().load(id, data).modelId;
				Integer count = faceCounts.get(modelId);
				if (count == null) {
					var model = client.loadModelData(modelId);
					if (model == null) {
						equipment = null;
						return;
					}
					count = model.getFaceCount();
					faceCounts.put(modelId, count);
				}
				int hue = (id * 23 + 7) & 63;
				ModelOverride tint = effectOverrides[i] = color(hue, 7);
				ranges.append(" spotanim=").append(id).append(" model=").append(modelId)
					.append(" hue=").append(hue).append(" combinedFaces=[").append(faces.size())
					.append(',').append(faces.size() + count).append(')');
				for (int face = 0; face < count; face++)
					faces.add(tint);
			}
			// Publish only after all ranges have been resolved; never mutate a worker's snapshot.
			ModelOverride override = color(0, 0);
			override.faceOverrides = faces.toArray(new ModelOverride[0]);
			result = new Result(local, override, effects, effectOverrides);
			log.debug("Player model parts: animation={}, gender={}, predicted base faces={}, with effects={};{}",
				action, gender, baseFaces, faces.size(), ranges);
		} catch (RuntimeException ex) {
			// Keep gray until the appearance changes, rather than throwing/logging every frame.
			log.warn("Unable to infer player model parts for animation {}", action, ex);
		}
	}

	private static int handEquipment(int raw, int item, int equipped) {
		if (raw < 0)
			return equipped;
		if (raw == 0)
			return 0;
		if (item < 0)
			throw new IllegalArgumentException("Sequence specifies hand equipment " + raw + " but API returned " + item);
		return item + PlayerComposition.ITEM_OFFSET;
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
