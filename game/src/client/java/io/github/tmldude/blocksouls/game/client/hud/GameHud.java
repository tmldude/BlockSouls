package io.github.tmldude.blocksouls.game.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;

/** Owned by the "game-players" session: wave/phase, between-wave timer, boons, downed and revive HUD. */
public final class GameHud {
	private GameHud() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, id("downed"), DownedHud::extract);
		// Not attached to the crosshair: its render condition would hide these in third person.
		HudElementRegistry.addLast(id("teammate_markers"), TeammateMarkers::extract);
		HudElementRegistry.addLast(id("revive_bar"), DownedHud::extractReviveBar);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id("boons"), BoonRowHud::extract);
		HudElementRegistry.attachElementBefore(VanillaHudElements.BOSS_BAR, id("run_info"), RunInfoHud::extract);
	}

	static PlayersConfig.Hud config() {
		return PlayerSystem.CONFIG.hud;
	}

	/** F1, no player, or a menu that hides the HUD. */
	static boolean hidden(Minecraft minecraft) {
		return minecraft.player == null || minecraft.level == null || minecraft.gui.hud.isHidden();
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, path);
	}

	static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "textures/gui/hud/" + name + ".png");
	}

	static void blitIcon(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int size) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, 16, 16, 16, 16);
	}

	/** A thin progress bar with a dark frame. */
	static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float progress, int color) {
		graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xB0000000);
		graphics.fill(x, y, x + Math.round(width * Math.clamp(progress, 0.0F, 1.0F)), y + height, color);
	}
}
