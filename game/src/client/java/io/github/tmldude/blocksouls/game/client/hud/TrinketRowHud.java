package io.github.tmldude.blocksouls.game.client.hud;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import io.github.tmldude.blocksouls.game.client.player.ClientBoons;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.BoonView;
import io.github.tmldude.blocksouls.game.player.boon.BoonRowLayout;

/** Trinket slots right of the hotbar: three framed icons, empty slots drawn dim. */
final class TrinketRowHud {
	private static final int GAP = 4;
	private static final int EMPTY = 0x60000000;
	private static final int EMPTY_OUTLINE = 0x60808080;

	private TrinketRowHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !PlayerSystem.BUILD.hud.showTrinkets || minecraft.player.isSpectator()) {
			return;
		}
		List<BoonView> trinkets = ClientBoons.trinkets();
		int slots = Math.max(PlayerSystem.BUILD.trinkets.slots, trinkets.size());
		// Right of the hotbar, bottom-aligned with it.
		int left = graphics.guiWidth() / 2 + 91 + GAP;
		int y = graphics.guiHeight() - 2 - BoonRowLayout.SLOT;
		for (int i = 0; i < slots; i++) {
			int x = left + i * BoonRowLayout.STEP;
			if (i < trinkets.size()) {
				BoonView trinket = trinkets.get(i);
				int color = BoonStyle.color(trinket);
				graphics.fill(x, y, x + BoonRowLayout.SLOT, y + BoonRowLayout.SLOT, 0x90000000);
				graphics.outline(x, y, BoonRowLayout.SLOT, BoonRowLayout.SLOT, color);
				BoonStyle.drawIcon(graphics, trinket.icon(), x + 1, y + 1, 16);
			} else {
				graphics.fill(x, y, x + BoonRowLayout.SLOT, y + BoonRowLayout.SLOT, EMPTY);
				graphics.outline(x, y, BoonRowLayout.SLOT, BoonRowLayout.SLOT, EMPTY_OUTLINE);
			}
		}
	}
}
