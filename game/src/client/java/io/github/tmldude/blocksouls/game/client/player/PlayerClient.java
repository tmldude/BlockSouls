package io.github.tmldude.blocksouls.game.client.player;

import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads;
import io.github.tmldude.blocksouls.game.player.downed.RevivePayloads;

/** Client side of downed/revive, boons and trinkets: packet receivers and revive input. */
public final class PlayerClient {
	private PlayerClient() {
	}

	public static void init() {
		ClientNetworking.receive(BoonPayloads.Owned.TYPE, (payload, context) -> ClientBoons.set(payload.boons()));
		ClientNetworking.receive(BoonPayloads.BuildLine.TYPE, (payload, context) -> ClientBoons.setBuildLine(payload.tags()));
		ClientNetworking.receive(BoonPayloads.Offer.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new RelicScreen(payload)));
		ClientNetworking.receive(BoonPayloads.TrinketOffer.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new TrinketScreen(payload)));
		ClientNetworking.receive(RevivePayloads.Progress.TYPE, (payload, context) -> ClientRevive.onProgress(payload));

		ClientTickEvents.END_CLIENT_TICK.register(ReviveInput::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientBoons.reset();
			ClientRevive.reset();
			ReviveInput.reset();
		});
	}
}
