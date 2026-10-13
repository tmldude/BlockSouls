package io.github.tmldude.blocksouls.game.client.hud;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.downed.LastStandPayloads;

/**
 * The downed indicator for v2: under the downed overlay's line, the Last Stand crawl timer and kill tally while a
 * solo stand is on, and the "keep shooting" hint while downed shooting is on. Mechanics only.
 */
public final class LastStandHud {
	private static final int BAR = 0xFFE0485C;
	private static final int TEXT = 0xFFFFD0D0;

	private static LastStandPayloads.@Nullable State state;

	private LastStandHud() {
	}

	/** The latest Last Stand packet; {@code ticksLeft < 0} clears it. */
	public static void onState(LastStandPayloads.State payload) {
		state = payload.ticksLeft() < 0 ? null : payload;
	}

	public static void reset() {
		state = null;
	}

	public static LastStandPayloads.@Nullable State state() {
		return state;
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !GameHud.config().showDownedOverlay || !PlayerStatus.isDowned(minecraft.player)) {
			state = state == null || PlayerStatus.isDowned(minecraft.player) ? state : null;
			return;
		}
		int width = graphics.guiWidth();
		int y = graphics.guiHeight() / 2 + 42;
		if (state != null) {
			int seconds = (state.ticksLeft() + 19) / 20;
			graphics.centeredText(minecraft.font, Component.translatable("hud.blocksouls_game.last_stand", state.kills(), state.killsNeeded(), seconds),
					width / 2, y, TEXT);
			GameHud.bar(graphics, width / 2 - 60, y + 12, 120, 4, (float) state.ticksLeft() / Math.max(1, state.totalTicks()), BAR);
		} else if (PlayerStatus.canShoot(minecraft.player)) {
			graphics.centeredText(minecraft.font, Component.translatable("hud.blocksouls_game.downed_shooting"), width / 2, y, TEXT);
		}
	}
}
