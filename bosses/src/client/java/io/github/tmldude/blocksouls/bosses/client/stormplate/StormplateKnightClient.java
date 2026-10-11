package io.github.tmldude.blocksouls.bosses.client.stormplate;

import net.minecraft.ChatFormatting;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;
import io.github.tmldude.blocksouls.bosses.stormplate.DragonscaleArmor;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateKnights;
import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Client registration for the Stormplate Knight (model layer, renderer: gold seams and spearhead always lit, crackling
 * plate in phase 2) and the dragonscale set's mechanics tooltip.
 */
public final class StormplateKnightClient {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(CommonIds.id(CommonIds.BOSSES, "stormplate_knight"), "main");

	private StormplateKnightClient() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, StormplateKnightModel::createBodyLayer);
		EntityRendererRegistry.register(StormplateKnights.TYPE, ctx ->
				new BossRenderer<>(ctx, new StormplateKnightModel(ctx.bakeLayer(LAYER)), 1.3f, "stormplate_knight", 1.15f)
						.glow("stormplate_knight_glow", state -> true)
						.glow("stormplate_knight_storm_glow", state -> state.phase >= 1));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (DragonscaleArmor.isPiece(stack)) {
				lines.add(Component.translatable("tooltip.blocksouls_bosses.dragonscale_set").withStyle(ChatFormatting.GRAY));
				lines.add(Component.literal(" ").append(Component.translatable("tooltip.blocksouls_bosses.dragonscale_set.bonus"))
						.withStyle(ChatFormatting.BLUE));
			}
		});
	}
}
