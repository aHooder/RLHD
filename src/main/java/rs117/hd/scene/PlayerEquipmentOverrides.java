package rs117.hd.scene;

import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Renderable;
import net.runelite.client.callback.ClientThread;
import rs117.hd.scene.model_overrides.ModelOverride;
import rs117.hd.utils.ModelHash;

/** Resolves only rendered players. Published overrides remain immutable for upload jobs. */
@Slf4j
@Singleton
public class PlayerEquipmentOverrides {
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ModelOverrideManager overrides;
	@Inject
	private PlayerModelParts parts;

	private final State[] players = new State[64];
	private final int[] equipment = new int[12];
	private final ModelOverride[] slots = new ModelOverride[12];

	private class State implements Runnable {
		Player player;
		final int[] worn = new int[12];
		final int[] equipment = new int[12];
		final ModelOverride[] slots = new ModelOverride[12];
		final int[] ends = new int[12];
		final int[] worldPos = new int[3];
		int animation;
		int gender;
		int lastSlot = -1;
		boolean queued;
		boolean retry;
		boolean ready;
		ModelOverride base;
		ModelOverride resolvedBase;
		int resolvedGender;
		ModelOverride combined;

		@Override
		public void run() {
			synchronized (PlayerEquipmentOverrides.this) {
				queued = false;
				if (player != null)
					resolve(this);
			}
		}
	}

	public synchronized void clear() {
		for (State state : players) {
			if (state != null) {
				state.player = null;
				state.base = state.resolvedBase = state.combined = null;
				state.ready = false;
				Arrays.fill(state.slots, null);
			}
		}
		parts.clear();
	}

	public synchronized ModelOverride getOverride(Renderable renderable, Model model, ModelOverride base, int[] worldPos) {
		if (!(renderable instanceof Player))
			return base;
		Player player = (Player) renderable;
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null || composition.getTransformedNpcId() != -1)
			return base;

		State state = null;
		int available = players.length - 1;
		for (int i = 0; i < players.length; i++) {
			if (players[i] != null && players[i].player == player) {
				state = players[i];
				break;
			}
			if ((players[i] == null || players[i].player == null) && available == players.length - 1)
				available = i;
		}
		if (state == null) {
			// Only replace the final entry on overflow, retaining the other 63 players.
			state = players[available];
			if (state == null)
				players[available] = state = new State();
			state.player = player;
			state.base = null;
			state.resolvedBase = null;
		}
		boolean changed = state.base != base || state.gender != composition.getGender() ||
			state.animation != player.getAnimation() || !Arrays.equals(state.worn, composition.getEquipmentIds()) ||
			!Arrays.equals(state.worldPos, worldPos);
		if (changed) {
			state.ready = false;
			state.base = base;
			state.gender = composition.getGender();
			state.animation = player.getAnimation();
			System.arraycopy(composition.getEquipmentIds(), 0, state.worn, 0, state.worn.length);
			System.arraycopy(worldPos, 0, state.worldPos, 0, 3);
		}
		if (changed || state.retry) {
			if (client.isClientThread()) {
				resolve(state);
			} else if (!state.queued) {
				// Loading client model data on render workers is unsafe; never block them on the client.
				state.queued = true;
				clientThread.invokeLater(state);
			}
		}
		return state.ready && state.combined != null && model.getFaceCount() >= state.ends[state.lastSlot] ? state.combined : base;
	}

	private void resolve(State state) {
		assert client.isClientThread();
		state.retry = false;
		var composition = state.player.getPlayerComposition();
		if (composition == null || composition.getTransformedNpcId() != -1)
			return;
		if (state.gender != composition.getGender() || state.animation != state.player.getAnimation() ||
			!Arrays.equals(state.worn, composition.getEquipmentIds())) {
			state.retry = true;
			return;
		}
		try {
			parts.fillEquipment(equipment, state.player);
			int last = -1;
			for (int slot = 0; slot < equipment.length; slot++) {
				ModelOverride selected = ModelOverride.NONE;
				if (equipment[slot] >= PlayerComposition.ITEM_OFFSET)
					selected = overrides.getOverride(ModelHash.packUuid(ModelHash.TYPE_EQUIPMENT,
						equipment[slot] - PlayerComposition.ITEM_OFFSET), state.worldPos);
				slots[slot] = selected.isDummy ? state.base : selected;
				if (slots[slot] != state.base)
					last = slot;
			}
			if (last == -1) {
				state.combined = null;
				return;
			}
			boolean unchanged = state.combined != null && state.resolvedBase == state.base &&
				state.resolvedGender == state.gender && state.lastSlot == last;
			for (int slot = 0; unchanged && slot <= last; slot++)
				unchanged = state.equipment[slot] == equipment[slot] && state.slots[slot] == slots[slot];
			if (unchanged) {
				state.ready = true;
				return;
			}
			int end = 0;
			for (int slot = 0; slot <= last; slot++) {
				int count = parts.getEquipmentFaceCount(equipment[slot], state.gender);
				if (count < 0) {
					state.retry = true;
					return;
				}
				state.ends[slot] = end += count;
			}
			ModelOverride combined = state.base.copy();
			int mappedFaces = combined.faceOverrides == null ? 0 : combined.faceOverrides.length;
			if (combined.faceOverrides == null || mappedFaces < end) {
				combined.faceOverrides = mappedFaces == 0 ?
					new ModelOverride[end] : Arrays.copyOf(combined.faceOverrides, end);
				Arrays.fill(combined.faceOverrides, mappedFaces, end, combined);
			}
			int start = 0;
			for (int slot = 0; slot <= last; slot++) {
				ModelOverride selected = slots[slot];
				if (selected != state.base) {
					selected = selected.copyForModelPart(start, state.ends[slot] - start);
					Arrays.fill(combined.faceOverrides, start, state.ends[slot], selected);
				}
				start = state.ends[slot];
				combined.mightHaveTransparency |= selected.mightHaveTransparency;
				combined.mightBeDoubleSided |= selected.mightBeDoubleSided;
			}
			state.combined = combined;
			state.resolvedBase = state.base;
			state.resolvedGender = state.gender;
			state.lastSlot = last;
			System.arraycopy(equipment, 0, state.equipment, 0, equipment.length);
			System.arraycopy(slots, 0, state.slots, 0, slots.length);
			state.ready = true;
		} catch (RuntimeException ex) {
			log.warn("Unable to resolve equipment overrides for player {}", state.player.getName(), ex);
		}
	}
}
