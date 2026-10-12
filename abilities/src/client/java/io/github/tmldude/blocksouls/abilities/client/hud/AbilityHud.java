package io.github.tmldude.blocksouls.abilities.client.hud;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.tmldude.blocksouls.abilities.AbilitiesConfig;
import io.github.tmldude.blocksouls.abilities.BlockSoulsAbilities;
import io.github.tmldude.blocksouls.abilities.logic.CooldownMath;
import io.github.tmldude.blocksouls.common.ability.Abilities;
import io.github.tmldude.blocksouls.common.ability.AbilityDefinition;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;
import io.github.tmldude.blocksouls.common.ability.AbilityState;
import io.github.tmldude.blocksouls.common.ability.ChargeModel;

/**
 * Bottom-left: three framed ability icons (tactical, signature, ultimate) with a cooldown sweep that drains from the
 * top, a seconds label while cooling, and the ultimate charge bar above the row. Nothing else draws in that corner
 * (guns' ammo is bottom-right, boons sit left of the hotbar, stamina right of it).
 */
public final class AbilityHud {
	public static final int SLOT = 20;
	public static final int GAP = 4;
	private static final int ICON = 16;
	private static final int DENIED_TICKS = 6;
	private static final int READY_FLASH_TICKS = 20;

	private static final int FRAME_TACTICAL = 0xFF7FB2FF;
	private static final int FRAME_SIGNATURE = 0xFFFFB84A;
	private static final int FRAME_ULTIMATE = 0xFFE36BFF;
	private static final int FRAME_COOLING = 0xFF5A5F66;
	private static final int DENIED = 0xFFFF4040;
	private static final int SWEEP = 0xB0101014;
	private static final int CHARGE = 0xFFE36BFF;
	private static final int CHARGE_READY = 0xFFFFF0FF;

	private static final Map<AbilitySlot, Integer> DENIED_TICKS_LEFT = new EnumMap<>(AbilitySlot.class);
	private static int readyFlash;

	private AbilityHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath(BlockSoulsAbilities.MOD_ID, "abilities"),
				AbilityHud::extract);
	}

	public static void onDenied(AbilitySlot slot) {
		DENIED_TICKS_LEFT.put(slot, DENIED_TICKS);
	}

	public static void onUltimateReady() {
		readyFlash = READY_FLASH_TICKS;
	}

	public static void tick() {
		DENIED_TICKS_LEFT.replaceAll((slot, ticks) -> Math.max(0, ticks - 1));
		if (readyFlash > 0) {
			readyFlash--;
		}
	}

	static Identifier sprite(Identifier ability) {
		return Identifier.fromNamespaceAndPath(ability.getNamespace(), "ability/" + ability.getPath());
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		AbilitiesConfig.Hud config = BlockSoulsAbilities.CONFIG.hud;
		if (player == null || minecraft.level == null || !config.enabled || minecraft.gui.hud.isHidden() || player.isSpectator()) {
			return;
		}

		AbilityState state = Abilities.state(player);
		long now = Abilities.now(player);
		int left = config.marginX;
		int bottom = graphics.guiHeight() - config.marginY;
		int top = bottom - SLOT;
		Font font = minecraft.font;

		AbilitySlot[] slots = AbilitySlot.values();
		for (int i = 0; i < slots.length; i++) {
			AbilitySlot slot = slots[i];
			int x = left + i * (SLOT + GAP);
			AbilityDefinition definition = Abilities.definition(player, slot);
			drawSlot(graphics, font, x, top, slot, definition, state, now);
		}

		// Ultimate charge bar above the row, as wide as the row.
		int width = slots.length * SLOT + (slots.length - 1) * GAP;
		float charge = (float) state.ultimateCharge();
		int barTop = top - 6;
		graphics.fill(left - 1, barTop - 1, left + width + 1, barTop + 3, 0xB0000000);
		int color = state.ultimateReady() ? ((readyFlash / 4) % 2 == 0 ? CHARGE_READY : CHARGE) : CHARGE;
		graphics.fill(left, barTop, left + Math.round(width * charge), barTop + 2, color);
	}

	private static void drawSlot(GuiGraphicsExtractor graphics, Font font, int x, int y, AbilitySlot slot, @Nullable AbilityDefinition definition,
			AbilityState state, long now) {
		boolean ready = definition != null && Abilities.isReady(Minecraft.getInstance().player, slot);
		int frame = definition == null ? FRAME_COOLING : ready ? switch (slot) {
			case TACTICAL -> FRAME_TACTICAL;
			case SIGNATURE -> FRAME_SIGNATURE;
			case ULTIMATE -> FRAME_ULTIMATE;
		} : FRAME_COOLING;
		if (DENIED_TICKS_LEFT.getOrDefault(slot, 0) > 0) {
			frame = DENIED;
		}
		graphics.fill(x, y, x + SLOT, y + SLOT, 0x90000000);
		graphics.outline(x, y, SLOT, SLOT, frame);
		if (definition == null) {
			return;
		}

		int iconX = x + (SLOT - ICON) / 2;
		int iconY = y + (SLOT - ICON) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(definition.id()), iconX, iconY, ICON, ICON);

		if (definition.charge() == ChargeModel.KILL_CHARGE) {
			// The charge bar above shows progress; dim the icon until it's full.
			if (!state.ultimateReady()) {
				float missing = 1.0F - (float) state.ultimateCharge();
				graphics.fill(iconX, iconY, iconX + ICON, iconY + Math.round(ICON * missing), SWEEP);
			}
			return;
		}

		AbilityState.Cooldown cooldown = state.cooldowns().get(slot);
		if (cooldown != null && !cooldown.ready(now)) {
			float progress = CooldownMath.progress(cooldown.startedAt(), cooldown.readyAt(), now);
			int sweep = Math.round(ICON * (1.0F - progress));
			graphics.fill(iconX, iconY, iconX + ICON, iconY + sweep, SWEEP);
			String seconds = Integer.toString(CooldownMath.secondsLeft(cooldown.readyAt(), now));
			graphics.text(font, seconds, x + (SLOT - font.width(seconds)) / 2, y + (SLOT - 8) / 2, 0xFFFFFFFF, true);
		}
	}
}
