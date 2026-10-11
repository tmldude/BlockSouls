package io.github.tmldude.blocksouls.game.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.client.player.ClientRevive;

/** Your own downed screen (red vignette, washed-out color, one mechanics line) and the revive bars. */
final class DownedHud {
	private static final int VIGNETTE = 0x8A0000;
	private static final int BAR = 0xFF5CE07A;

	private DownedHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !GameHud.config().showDownedOverlay || !PlayerStatus.isDowned(minecraft.player)) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// Washed-out, dim world.
		graphics.fill(0, 0, width, height, 0x50202020);

		// Red vignette: vertical gradients top and bottom, stepped strips left and right.
		int band = height / 3;
		graphics.fillGradient(0, 0, width, band, 0xB0000000 | VIGNETTE, VIGNETTE);
		graphics.fillGradient(0, height - band, width, height, VIGNETTE, 0xB0000000 | VIGNETTE);
		int side = width / 5;
		for (int i = 0; i < side; i += 2) {
			int alpha = (int) (0xB0 * (1.0F - (float) i / side));
			int color = alpha << 24 | VIGNETTE;
			graphics.fill(i, 0, i + 2, height, color);
			graphics.fill(width - i - 2, 0, width - i, height, color);
		}

		int y = height / 2 + 28;
		graphics.centeredText(minecraft.font, Component.translatable("hud.blocksouls_game.downed"), width / 2, y, 0xFFFF6B6B);

		ClientRevive.State revive = ClientRevive.beingRevived();
		if (revive != null && GameHud.config().showReviveBar) {
			Entity reviver = minecraft.level.getEntity(revive.other());
			Component label = reviver == null
					? Component.translatable("hud.blocksouls_game.being_revived_anon")
					: Component.translatable("hud.blocksouls_game.being_revived", reviver.getDisplayName());
			graphics.centeredText(minecraft.font, label, width / 2, y + 14, 0xFFFFFFFF);
			GameHud.bar(graphics, width / 2 - 60, y + 26, 120, 4, revive.progress(), BAR);
		}
	}

	/** Under the crosshair while you're reviving someone. */
	static void extractReviveBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !GameHud.config().showReviveBar) {
			return;
		}
		ClientRevive.State revive = ClientRevive.reviving();
		if (revive == null) {
			return;
		}

		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 12;
		graphics.centeredText(minecraft.font, Component.translatable("hud.blocksouls_game.reviving"), x, y, 0xFFFFFFFF);
		GameHud.bar(graphics, x - 40, y + 11, 80, 3, revive.progress(), BAR);
	}
}
