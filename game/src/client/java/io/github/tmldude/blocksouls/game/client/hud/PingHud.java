package io.github.tmldude.blocksouls.game.client.hud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.game.player.ping.PingPayloads;

/**
 * Pings on the HUD: a marker at the pinged spot (pinned to the screen edge when off-screen, like the downed
 * teammate markers) with the pinger's name and distance, red when a downed player pinged their own position.
 */
public final class PingHud {
	private static final int PING = 0xFFF2C845;
	private static final int DOWNED_PING = 0xFFFF6B6B;

	private record Ping(int player, Vec3 pos, boolean downed, long expiresAt) {
	}

	private static final List<Ping> PINGS = new ArrayList<>();

	private PingHud() {
	}

	public static void onPinged(PingPayloads.Pinged payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		PINGS.removeIf(ping -> ping.player() == payload.player());
		PINGS.add(new Ping(payload.player(), payload.pos(), payload.downed(), now + payload.ticks()));
	}

	public static void reset() {
		PINGS.clear();
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || PINGS.isEmpty()) {
			return;
		}
		long now = minecraft.level.getGameTime();
		Camera camera = minecraft.gameRenderer.mainCamera();
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		for (Iterator<Ping> it = PINGS.iterator(); it.hasNext();) {
			Ping ping = it.next();
			if (now >= ping.expiresAt()) {
				it.remove();
				continue;
			}
			int[] screen = TeammateMarkers.project(camera, ping.pos(), width, height);
			int x = screen[0];
			int y = screen[1];
			int color = ping.downed() ? DOWNED_PING : PING;
			// A diamond that pulses with the remaining time.
			int size = 4 + (int) ((ping.expiresAt() - now) % 20 < 10 ? 1 : 0);
			graphics.fill(x - size, y - 1, x + size, y + 1, color);
			graphics.fill(x - 1, y - size, x + 1, y + size, color);
			Entity pinger = minecraft.level.getEntity(ping.player());
			Component name = pinger == null ? Component.literal("?") : pinger.getDisplayName();
			double distance = minecraft.player.getPosition(partial).distanceTo(ping.pos());
			graphics.centeredText(minecraft.font, name, x, y - 16, color);
			graphics.centeredText(minecraft.font, Math.round(distance) + "m", x, y + 7, color);
		}
	}
}
