package io.github.tmldude.blocksouls.bosses.client.splash;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import io.github.tmldude.blocksouls.bosses.presentation.BossSplashPayload;
import io.github.tmldude.blocksouls.bosses.registry.BossSounds;
import io.github.tmldude.blocksouls.common.client.ClientNetworking;

/**
 * The name-splash: the boss's name large across the middle of the screen as the fog wall rises, fading in over
 * 0.2 s and out over the last 0.4 s, with a thin rule under it and the sting.
 */
public final class BossSplashHud {
	private static final int NAME = 0xF0E6D2;
	private static final int RULE = 0xB3261E;
	private static @Nullable Component name;
	private static long startMs;
	private static long lengthMs;

	private BossSplashHud() {
	}

	public static void init() {
		ClientNetworking.receive(BossSplashPayload.TYPE, (payload, context) -> show(payload));
	}

	static void show(BossSplashPayload payload) {
		name = payload.name();
		startMs = Util.getMillis();
		lengthMs = payload.ticks() * 50L;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BossSounds.STING, 1.0f, 1.0f));
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Component shown = name;
		Minecraft minecraft = Minecraft.getInstance();
		if (shown == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		long since = Util.getMillis() - startMs;
		if (since >= lengthMs) {
			name = null;
			return;
		}
		float alpha = Math.min(1f, since / 200f) * Math.min(1f, (lengthMs - since) / 400f);
		int a = Mth.clamp(Math.round(alpha * 255), 4, 255) << 24;
		Font font = minecraft.font;
		float scale = 2.5f + 0.15f * (since / (float) lengthMs);
		int width = font.width(shown);
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 3;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(scale, scale);
		graphics.text(font, shown, -width / 2, -font.lineHeight / 2, a | NAME, true);
		graphics.pose().popMatrix();
		int half = Math.round(width * scale * 0.6f * Math.min(1f, since / 300f));
		int ry = cy + Math.round(font.lineHeight * scale / 2f) + 3;
		graphics.fill(cx - half, ry, cx + half, ry + 1, a | RULE);
	}
}
