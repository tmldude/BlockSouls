package io.github.tmldude.blocksouls.game.client.hud;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.game.client.player.ClientBoons;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.TagView;

/**
 * The build line: the player's top synergy tags ("Fire · Ricochet") as icon + name in the top-left corner, so a
 * build is legible at a glance. Hidden until the build has a tag.
 */
final class BuildLineHud {
	private static final int LEFT = 4;
	private static final int TOP = 4;
	private static final int ICON = 12;
	private static final int TEXT = 0xFFF4F7FA;
	private static final int SEPARATOR = 0xFF9AA3AD;

	private BuildLineHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !PlayerSystem.BUILD.hud.showBuildLine || minecraft.player.isSpectator()) {
			return;
		}
		List<TagView> tags = ClientBoons.buildLine();
		if (tags.isEmpty()) {
			return;
		}
		int x = LEFT;
		for (int i = 0; i < tags.size(); i++) {
			TagView tag = tags.get(i);
			if (i > 0) {
				graphics.text(minecraft.font, "·", x, TOP + 2, SEPARATOR);
				x += minecraft.font.width("·") + 4;
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(tag.tag()), x, TOP, ICON, ICON);
			x += ICON + 2;
			Component name = name(tag.tag());
			graphics.text(minecraft.font, name, x, TOP + 2, TEXT);
			x += minecraft.font.width(name);
			String stacks = "×" + tag.stacks();
			graphics.text(minecraft.font, stacks, x + 2, TOP + 2, SEPARATOR);
			x += minecraft.font.width(stacks) + 8;
		}
	}

	/** {@code assets/blocksouls_game/textures/gui/sprites/synergy/<tag path>.png}. */
	static Identifier sprite(Identifier tag) {
		return Identifier.fromNamespaceAndPath("blocksouls_game", "synergy/" + tag.getPath());
	}

	static Component name(Identifier tag) {
		return Component.translatable("synergy." + tag.getNamespace() + "." + tag.getPath());
	}
}
