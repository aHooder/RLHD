package rs117.hd.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import rs117.hd.utils.collections.Int2IntHashMap;

/** Experimental face provenance inferred from the historical player assembly order. */
@Singleton
public class PlayerModelParts {
	// Animation hand getters expose raw empty equipment (0) minus the historical item offset.
	private static final int EMPTY_HAND = -512;

	@Inject
	private Client client;

	private final Int2IntHashMap equipmentFaceCounts = new Int2IntHashMap();

	private final Map<Integer, Integer> faceCounts = new HashMap<>();
	private int[] equipment;
	private int animation;
	private int gender;
	private Player player;
	private ActorSpotAnim[] spotanims;
	private int[] spotanimIds;

	private final Result result = new Result();
	private Part[] ranges = new Part[64];

	/** Reused diagnostic ranges, valid until the next resolve. No client geometry is retained. */
	public static class Part {
		public int slot; // -1 for spotanims
		public int index; // Source-model index within the equipment slot
		public int modelId; // -1 for spotanims, whose model ID is not exposed by the API
		public ActorSpotAnim spotanim;
		public int spotanimId;
		public int startFace;
		public int faceCount;
	}

	public static class Result {
		public Player player;
		public int animation;
		public int gender;
		public ActorSpotAnim[] spotanims;
		/** Null until every required definition/model is available. Treat this array as read-only. */
		public Part[] parts;
		public int partCount;
		public boolean changed;
		public int baseFaceCount;
		public int faceCount;
	}

	public void clear() {
		result.parts = null;
		result.player = null;
		result.spotanims = null;
		for (Part part : ranges)
			if (part != null)
				part.spotanim = null;
		player = null;
		equipment = null;
		spotanims = null;
		spotanimIds = null;
		faceCounts.clear();
		equipmentFaceCounts.clear();
	}

	/** Resolve on the client thread. Reuses the last result while the assembly inputs remain unchanged. */
	public Result resolve(Player local) {
		assert client.isClientThread();
		result.changed = false;
		PlayerComposition composition = local == null ? null : local.getPlayerComposition();
		if (composition == null || composition.getTransformedNpcId() != -1) {
			clear();
			return null;
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
		if (player == local &&
			gender == composition.getGender() &&
			animation == action &&
			Arrays.equals(equipment, worn) &&
			Arrays.equals(spotanims, effects) &&
			Arrays.equals(spotanimIds, effectIds))
			return result;

		player = local;
		gender = composition.getGender();
		animation = action;
		equipment = worn.clone();
		spotanims = effects;
		spotanimIds = effectIds;
		result.player = local;
		result.animation = action;
		result.gender = gender;
		result.spotanims = effects;
		result.parts = null;
		result.partCount = 0;
		result.baseFaceCount = result.faceCount = 0;
		result.changed = true;

		int[] effective = new int[worn.length];
		fillEquipment(effective, local);

		int totalFaces = 0;
		for (int slot = 0; slot < effective.length; slot++) {
			int value = effective[slot];
			int[] models;
			if (value >= PlayerComposition.ITEM_OFFSET) {
				int id = value - PlayerComposition.ITEM_OFFSET;
				byte[] data = client.getIndexConfig().loadData(10, id);
				if (data == null) {
					equipment = null; // Retry when the cache data arrives.
					return result;
				}
				models = PlayerModelDefinitions.readWornModels(data, gender == 1);
			} else if (value >= PlayerComposition.KIT_OFFSET) {
				int id = value - PlayerComposition.KIT_OFFSET;
				byte[] data = client.getIndexConfig().loadData(3, id);
				if (data == null) {
					equipment = null;
					return result;
				}
				models = PlayerModelDefinitions.readKitModels(data);
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
						return result;
					}
					count = model.getFaceCount();
					faceCounts.put(id, count);
				}
				addPart(slot, part, id, null, -1, totalFaces, count);
				totalFaces += count;
			}
		}
		int baseFaces = totalFaces;
		// Assumes combined effects follow the actor's iteration order; not guaranteed by the public API.
		for (int i = 0; i < effects.length; i++) {
			int id = effectIds[i];
			// Resolve before rendering on the client thread: getModel may reuse shared animation buffers.
			// Only retain the count, never the returned model.
			var model = effects[i].getModel();
			if (model == null) {
				equipment = null;
				return result;
			}
			int count = model.getFaceCount();
			addPart(-1, i, -1, effects[i], id, totalFaces, count);
			totalFaces += count;
		}
		result.parts = ranges;
		result.baseFaceCount = baseFaces;
		result.faceCount = totalFaces;
		return result;
	}

	private void addPart(int slot, int index, int modelId, ActorSpotAnim spotanim, int spotanimId, int startFace, int faceCount) {
		if (result.partCount == ranges.length)
			ranges = Arrays.copyOf(ranges, ranges.length * 2);
		Part part = ranges[result.partCount];
		if (part == null)
			ranges[result.partCount] = part = new Part();
		result.partCount++;
		part.slot = slot;
		part.index = index;
		part.modelId = modelId;
		part.spotanim = spotanim;
		part.spotanimId = spotanimId;
		part.startFace = startFace;
		part.faceCount = faceCount;
	}

	/** Writes encoded equipment/kit IDs in assembly order, including action-animation substitutions. */
	public void fillEquipment(int[] out, Player player) {
		assert client.isClientThread();
		int[] worn = player.getPlayerComposition().getEquipmentIds();
		System.arraycopy(worn, 0, out, 0, worn.length);
		int action = player.getAnimation();
		if (action != -1) {
			var sequence = client.loadAnimation(action);
			out[3] = handEquipment(sequence.getRightHandItem(), out[3]);
			out[5] = handEquipment(sequence.getLeftHandItem(), out[5]);
		}
	}

	/** Counts one slot without constructing per-source-model ranges. -1 means assets are still loading. */
	public int getEquipmentFaceCount(int equipment, int gender) {
		assert client.isClientThread();
		if (equipment < PlayerComposition.KIT_OFFSET)
			return 0;
		int key = equipment * 2 + gender;
		int cached = equipmentFaceCounts.getOrDefault(key, -1);
		if (cached >= 0)
			return cached;
		boolean item = equipment >= PlayerComposition.ITEM_OFFSET;
		int id = equipment - (item ? PlayerComposition.ITEM_OFFSET : PlayerComposition.KIT_OFFSET);
		byte[] data = client.getIndexConfig().loadData(item ? 10 : 3, id);
		if (data == null)
			return -1;
		int[] models = item ? PlayerModelDefinitions.readWornModels(data, gender == 1) : PlayerModelDefinitions.readKitModels(data);
		int total = 0;
		if (models != null) {
			for (int modelId : models) {
				if (modelId == -1)
					break;
				Integer count = faceCounts.get(modelId);
				if (count == null) {
					var model = client.loadModelData(modelId);
					if (model == null)
						return -1;
					count = model.getFaceCount();
					faceCounts.put(modelId, count);
				}
				total += count;
			}
		}
		equipmentFaceCounts.put(key, total);
		return total;
	}

	private static int handEquipment(int item, int equipped) {
		if (item == -1)
			return equipped;
		if (item == EMPTY_HAND)
			return 0;
		if (item < 0)
			throw new IllegalArgumentException("Unknown animation hand item " + item);
		return item + PlayerComposition.ITEM_OFFSET;
	}
}
