package io.github.tmldude.blocksouls.movement.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;

/** Thin stamina bar just above the hunger row; hidden while stamina is full. */
public final class StaminaHud {
	private static final int WIDTH = 81;
	private static final int HEIGHT = 2;
	private static final int BACKGROUND = 0x99000000;
	private static final int FILL = 0xFF4CD07D;
	private static final int FILL_EMPTY = 0xFFD0574C;

	private static MovementConfig.Stamina config;

	private StaminaHud() {
	}

	public static void register(MovementConfig.Stamina staminaConfig) {
		config = staminaConfig;
		HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR,
				Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "stamina"), StaminaHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isSpectator() || player.isCreative()) {
			return;
		}

		float fraction = StaminaAttachment.get(player).fraction(config);
		if (fraction >= 1.0F) {
			return;
		}

		// Right-aligned with the hunger row, which sits 39px above the bottom and ends 91px right of center.
		int right = graphics.guiWidth() / 2 + 91;
		int left = right - WIDTH;
		int top = graphics.guiHeight() - 39 - HEIGHT - 2;

		graphics.fill(left - 1, top - 1, right + 1, top + HEIGHT + 1, BACKGROUND);
		graphics.fill(left, top, left + Math.round(WIDTH * fraction), top + HEIGHT,
				fraction < 0.25F ? FILL_EMPTY : FILL);
	}
}
