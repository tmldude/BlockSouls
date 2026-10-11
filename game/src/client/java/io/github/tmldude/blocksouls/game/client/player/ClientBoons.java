package io.github.tmldude.blocksouls.game.client.player;

import java.util.List;

import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.BoonView;

/** The local player's boons as the server last sent them (combo parts already left out). */
public final class ClientBoons {
	private static List<BoonView> owned = List.of();

	private ClientBoons() {
	}

	public static List<BoonView> get() {
		return owned;
	}

	static void set(List<BoonView> boons) {
		owned = List.copyOf(boons);
	}
}
