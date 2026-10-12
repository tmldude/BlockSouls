package io.github.tmldude.blocksouls.game.client.player;

import net.minecraft.client.Minecraft;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.game.client.hud.OriginSelectScreen;
import io.github.tmldude.blocksouls.game.player.origin.OriginPayloads;

/** Client side of the v2 player layer (origins, Last Stand, pings, the run summary): packet receivers and input. */
public final class PlayersV2Client {
	private PlayersV2Client() {
	}

	public static void init() {
		ClientNetworking.receive(OriginPayloads.Offer.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new OriginSelectScreen(payload)));
	}
}
