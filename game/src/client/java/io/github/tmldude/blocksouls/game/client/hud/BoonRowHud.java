package io.github.tmldude.blocksouls.game.client.hud;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import io.github.tmldude.blocksouls.game.client.player.ClientBoons;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.BoonView;
import io.github.tmldude.blocksouls.game.player.boon.BoonRowLayout;

/**
 * Owned boons as small framed icons to the left of the hotbar (past the offhand slot), growing leftward and
 * wrapping upward. Level pips sit above stackable boons.
 */
final class BoonRowHud {
	/** Keeps the row below the run info at the top of the screen. */
	private static final int TOP = 40;

	private BoonRowHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !GameHud.config().showBoons || minecraft.player.isSpectator()) {
			return;
		}

		List<BoonView> boons = ClientBoons.get();
		// Left of the offhand slot.
		int right = graphics.guiWidth() / 2 - 91 - 29 - 4;
		int[][] slots = BoonRowLayout.place(boons.size(), right, graphics.guiHeight() - 2, TOP);
		for (int i = 0; i < boons.size(); i++) {
			BoonView boon = boons.get(i);
			int x = slots[i][0];
			int y = slots[i][1];
			int color = BoonStyle.color(boon);

			graphics.fill(x, y, x + BoonRowLayout.SLOT, y + BoonRowLayout.SLOT, 0x90000000);
			graphics.outline(x, y, BoonRowLayout.SLOT, BoonRowLayout.SLOT, color);
			BoonStyle.drawIcon(graphics, boon.icon(), x + 1, y + 1, 16);

			if (boon.maxLevel() > 1) {
				for (int pip = 0; pip < boon.maxLevel(); pip++) {
					int px = x + 1 + pip * 4;
					graphics.fill(px, y - 3, px + 3, y - 1, pip < boon.level() ? color : 0x80404040);
				}
			}
		}
	}
}
