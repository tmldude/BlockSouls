package io.github.tmldude.blocksouls.game.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.game.client.run.ClientRun;

/** Top center: phase icon, the wave number big, then the countdown or mobs left. */
final class RunInfoHud {
	private static final int TOP = 4;
	private static final int GAP = 4;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int ENDLESS = 0xFFFF6A5A;

	private RunInfoHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !GameHud.config().showRunInfo) {
			return;
		}

		ClientRun.View view = ClientRun.get();
		RunState state = view.state();
		if (state.phase() == RunPhase.NOT_STARTED) {
			return;
		}

		Font font = minecraft.font;
		String wave = String.valueOf(Math.max(0, state.wave()));
		int waveWidth = font.width(wave) * 2;

		String detail = null;
		Identifier detailIcon = null;
		if (view.secondsToNext() >= 0) {
			detail = clock(view.secondsToNext());
		} else if (state.phase() == RunPhase.WAVE && view.mobsLeft() >= 0) {
			detail = String.valueOf(view.mobsLeft());
			detailIcon = GameHud.icon("skull");
		}
		int detailWidth = detail == null ? 0 : GAP + (detailIcon == null ? 0 : 10) + font.width(detail);

		int total = 16 + GAP + waveWidth + detailWidth;
		int x = graphics.guiWidth() / 2 - total / 2;
		graphics.fill(x - 4, TOP - 2, x + total + 4, TOP + 18, 0x60000000);

		GameHud.blitIcon(graphics, GameHud.icon(phaseIcon(state.phase())), x, TOP, 16);
		x += 16 + GAP;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, TOP + 1);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.text(font, wave, 0, 0, state.endless() ? ENDLESS : WHITE);
		graphics.pose().popMatrix();
		x += waveWidth;

		if (detail != null) {
			x += GAP;
			if (detailIcon != null) {
				GameHud.blitIcon(graphics, detailIcon, x, TOP + 4, 8);
				x += 10;
			}
			graphics.text(font, detail, x, TOP + 5, WHITE);
		}
	}

	private static String phaseIcon(RunPhase phase) {
		return switch (phase) {
			case WAVE -> "wave";
			case WAVE_BREAK -> "break";
			case NIGHT_QUIET -> "moon";
			case OVER -> "skull";
			default -> "sun";
		};
	}

	static String clock(int seconds) {
		return seconds / 60 + ":" + String.format("%02d", seconds % 60);
	}
}
