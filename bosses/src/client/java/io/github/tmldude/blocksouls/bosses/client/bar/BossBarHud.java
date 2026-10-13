package io.github.tmldude.blocksouls.bosses.client.bar;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Stage;

/**
 * Our boss bar (vanilla's never shows for our bosses): top center under the run HUD, the boss's name over a wide
 * framed bar, a pale "damage taken" chunk that drains 0.6 s after a hit, notches at the phase thresholds, and a short
 * shake + flash when a phase starts. Up to two bars stack.
 */
public final class BossBarHud {
	private static final int TOP = 26;
	private static final int BAR_HEIGHT = 6;
	private static final int SPACING = 22;
	private static final int MAX_BARS = 2;
	private static final int FRAME = 0xE0100806;
	private static final int BACK = 0xC02A1A14;
	private static final int FILL = 0xFFB3261E;
	private static final int FILL_HIGHLIGHT = 0xFFE0523A;
	private static final int TRAIL = 0xFFF2D49B;
	private static final int NOTCH = 0xFF0B0604;
	private static final int NAME = 0xFFF0E6D2;
	private static final long SHAKE_MS = 500;
	private static final int POSTURE_HEIGHT = 2;
	private static final int POSTURE_BACK = 0xA0140C08;
	private static final int POSTURE_FILL = 0xFFE8C05A;
	private static final int POSTURE_FULL = 0xFFFFF4D0;

	private BossBarHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientBossBars.frame();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || !ClientBossBars.any()) {
			return;
		}
		Font font = minecraft.font;
		List<ClientBossBars.Bar> bars = ClientBossBars.shown(MAX_BARS);
		int width = Math.min(256, (int) (graphics.guiWidth() * 0.55f));
		long now = Util.getMillis();
		for (int i = 0; i < bars.size(); i++) {
			ClientBossBars.Bar bar = bars.get(i);
			int x = graphics.guiWidth() / 2 - width / 2;
			int y = TOP + i * SPACING;
			float sinceChange = bar.phaseChangedMs == 0 ? Float.MAX_VALUE : now - bar.phaseChangedMs;
			if (sinceChange < SHAKE_MS) {
				float strength = 1 - sinceChange / SHAKE_MS;
				x += Math.round(Mth.sin(sinceChange * 0.12f) * 3 * strength);
				y += Math.round(Mth.cos(sinceChange * 0.17f) * 1.5f * strength);
			}
			draw(graphics, font, bar, x, y, width, sinceChange);
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, ClientBossBars.Bar bar, int x, int y, int width, float sinceChange) {
		graphics.text(font, bar.name, x, y, NAME, true);
		int barY = y + font.lineHeight + 1;
		graphics.fill(x - 2, barY - 2, x + width + 2, barY + BAR_HEIGHT + 2, FRAME);
		graphics.fill(x, barY, x + width, barY + BAR_HEIGHT, BACK);

		int value = Math.round(width * (float) bar.trail.value());
		int trail = Math.round(width * (float) bar.trail.trail());
		if (trail > value) {
			graphics.fill(x + value, barY, x + trail, barY + BAR_HEIGHT, TRAIL);
		}
		if (value > 0) {
			graphics.fill(x, barY, x + value, barY + BAR_HEIGHT, FILL);
			graphics.fill(x, barY, x + value, barY + 2, FILL_HIGHLIGHT);
		}
		for (float notch : bar.notches) {
			int nx = x + Math.round(width * notch);
			graphics.fill(nx - 1, barY - 2, nx + 1, barY + BAR_HEIGHT + 2, NOTCH);
		}
		drawPosture(graphics, bar, x, barY + BAR_HEIGHT + 3, width);
		if (sinceChange < SHAKE_MS) {
			int alpha = (int) (200 * (1 - sinceChange / SHAKE_MS));
			graphics.fill(x - 2, barY - 2, x + width + 2, barY + BAR_HEIGHT + 2, (alpha << 24) | 0xFFF4E0);
		}
	}

	/** The thin posture bar under the boss bar, filling from both ends toward the middle; white while broken. */
	private static void drawPosture(GuiGraphicsExtractor graphics, ClientBossBars.Bar bar, int x, int y, int width) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !(minecraft.level.getEntity(bar.entityId) instanceof BossEntity boss)) {
			return;
		}
		float posture = Mth.clamp(boss.posture(), 0, 1);
		boolean broken = boss.stage() == Stage.BROKEN;
		if (posture <= 0 && !broken) {
			return;
		}
		int w = Math.round(width * 0.6f);
		int left = x + (width - w) / 2;
		graphics.fill(left - 1, y - 1, left + w + 1, y + POSTURE_HEIGHT + 1, POSTURE_BACK);
		int half = Math.round(w / 2f * (broken ? 1 : posture));
		int color = broken && (Util.getMillis() / 120) % 2 == 0 ? POSTURE_FULL : POSTURE_FILL;
		graphics.fill(left, y, left + half, y + POSTURE_HEIGHT, color);
		graphics.fill(left + w - half, y, left + w, y + POSTURE_HEIGHT, color);
	}
}
