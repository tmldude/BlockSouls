package io.github.tmldude.blocksouls.game.client.player;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;

import io.github.tmldude.blocksouls.game.player.downed.RevivePayloads;

/** Revive progress the local player is part of, as reviver or as the one being revived. */
public final class ClientRevive {
	/** Progress goes stale if the server stops sending (e.g. the other player left). */
	private static final int STALE_TICKS = 10;

	/** @param other the entity id of the other player */
	public record State(int other, float progress, long updatedAt) {
	}

	private static @Nullable State reviving;
	private static @Nullable State beingRevived;

	private ClientRevive() {
	}

	/** Who the local player is reviving, and how far along. */
	public static @Nullable State reviving() {
		return fresh(reviving);
	}

	/** The furthest-along revive on the local player. */
	public static @Nullable State beingRevived() {
		return fresh(beingRevived);
	}

	static void onProgress(RevivePayloads.Progress payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		int self = minecraft.player.getId();
		long now = minecraft.level.getGameTime();
		boolean ended = payload.progress() < 0;

		if (payload.reviver() == self) {
			reviving = ended ? null : new State(payload.downed(), payload.progress(), now);
		}
		if (payload.downed() == self) {
			State current = fresh(beingRevived);
			if (ended) {
				if (current != null && current.other() == payload.reviver()) {
					beingRevived = null;
				}
			} else if (current == null || current.other() == payload.reviver() || payload.progress() > current.progress()) {
				beingRevived = new State(payload.reviver(), payload.progress(), now);
			}
		}
	}

	static void reset() {
		reviving = null;
		beingRevived = null;
	}

	private static @Nullable State fresh(@Nullable State state) {
		Minecraft minecraft = Minecraft.getInstance();
		if (state == null || minecraft.level == null || minecraft.level.getGameTime() - state.updatedAt() > STALE_TICKS) {
			return null;
		}
		return state;
	}
}
