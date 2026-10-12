package io.github.tmldude.blocksouls.game.client.player;

import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.client.Keybinds;
import io.github.tmldude.blocksouls.game.client.hud.LastStandHud;
import io.github.tmldude.blocksouls.game.client.hud.ClassSelectScreen;
import io.github.tmldude.blocksouls.game.client.hud.PingHud;
import io.github.tmldude.blocksouls.game.client.hud.RuneTreeScreen;
import io.github.tmldude.blocksouls.game.player.downed.LastStandPayloads;
import io.github.tmldude.blocksouls.game.player.classes.ClassPayloads;
import io.github.tmldude.blocksouls.game.player.ping.PingPayloads;
import io.github.tmldude.blocksouls.game.player.rune.RunePayloads;

/** Client side of the v2 player layer (classes, Last Stand, pings, the run summary): packet receivers and input. */
public final class PlayersV2Client {
	private static KeyMapping ping;

	private PlayersV2Client() {
	}

	public static void init() {
		ping = Keybinds.keyboard("ping", SDLScancode.SDL_SCANCODE_B);
		ClientNetworking.receive(ClassPayloads.Offer.TYPE, (payload, context) -> Minecraft.getInstance().gui.setScreen(new ClassSelectScreen(payload)));
		ClientNetworking.receive(LastStandPayloads.State.TYPE, (payload, context) -> LastStandHud.onState(payload));
		ClientNetworking.receive(PingPayloads.Pinged.TYPE, (payload, context) -> PingHud.onPinged(payload));
		ClientNetworking.receive(RunePayloads.Tree.TYPE, (payload, context) -> {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.gui.screen() instanceof RuneTreeScreen screen) {
				screen.update(payload);
			} else {
				minecraft.gui.setScreen(new RuneTreeScreen(payload));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ping.consumeClick()) {
				if (client.player != null) {
					ClientNetworking.sendToServer(PingPayloads.Ping.INSTANCE);
				}
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			LastStandHud.reset();
			PingHud.reset();
		});
	}
}
