package io.github.tmldude.blocksouls.mobs.client;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.mobs.client.model.AshHoundModel;
import io.github.tmldude.blocksouls.mobs.client.model.BloaterModel;
import io.github.tmldude.blocksouls.mobs.client.model.BruteAnimations;
import io.github.tmldude.blocksouls.mobs.client.model.BruteModel;
import io.github.tmldude.blocksouls.mobs.client.model.CrawlerModel;
import io.github.tmldude.blocksouls.mobs.client.model.InfectedPiglinModel;
import io.github.tmldude.blocksouls.mobs.client.model.ScreamerModel;
import io.github.tmldude.blocksouls.mobs.client.model.ShamblerModel;
import io.github.tmldude.blocksouls.mobs.client.render.BlockSoulsMobRenderer;
import io.github.tmldude.blocksouls.mobs.client.render.InfectedPiglinRenderer;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;

public class BlockSoulsMobsClient implements ClientModInitializer {
	public static final ModelLayerLocation SHAMBLER = layer("shambler");
	public static final ModelLayerLocation CRAWLER = layer("crawler");
	public static final ModelLayerLocation SCREAMER = layer("screamer");
	public static final ModelLayerLocation BLOATER = layer("bloater");
	public static final ModelLayerLocation BRUTE = layer("brute");
	public static final ModelLayerLocation MUTANT_BRUTE = layer("mutant_brute");
	public static final ModelLayerLocation INFECTED_PIGLIN = layer("infected_piglin");
	public static final ModelLayerLocation ASH_HOUND = layer("ash_hound");

	private static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(CommonIds.id(CommonIds.MOBS, name), "main");
	}

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(SHAMBLER, ShamblerModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(CRAWLER, CrawlerModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(SCREAMER, ScreamerModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(BLOATER, BloaterModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(BRUTE, BruteModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(MUTANT_BRUTE, BruteModel::createMutantLayer);
		ModelLayerRegistry.registerModelLayer(INFECTED_PIGLIN, InfectedPiglinModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(ASH_HOUND, AshHoundModel::createBodyLayer);

		EntityRendererRegistry.register(MobEntities.SHAMBLER, ctx ->
				new BlockSoulsMobRenderer<>(ctx, new ShamblerModel(ctx.bakeLayer(SHAMBLER)), 0.5f, "shambler", 1f, false));
		EntityRendererRegistry.register(MobEntities.CRAWLER, ctx ->
				new BlockSoulsMobRenderer<>(ctx, new CrawlerModel(ctx.bakeLayer(CRAWLER)), 0.6f, "crawler", 1f, false));
		EntityRendererRegistry.register(MobEntities.SCREAMER, ctx ->
				new BlockSoulsMobRenderer<>(ctx, new ScreamerModel(ctx.bakeLayer(SCREAMER)), 0.4f, "screamer", 1f, false));
		EntityRendererRegistry.register(MobEntities.BLOATER, ctx ->
				new BlockSoulsMobRenderer<>(ctx, new BloaterModel(ctx.bakeLayer(BLOATER)), 0.9f, "bloater", 1f, true));
		EntityRendererRegistry.register(MobEntities.BRUTE, ctx ->
				new BlockSoulsMobRenderer<>(ctx, new BruteAnimations.BruteSlam(ctx.bakeLayer(BRUTE)), 1.0f, "brute", 1.15f, false));
		EntityRendererRegistry.register(MobEntities.MUTANT_BRUTE, ctx ->
				new BlockSoulsMobRenderer<>(ctx, new BruteAnimations.Mutant(ctx.bakeLayer(MUTANT_BRUTE)), 1.2f, "mutant_brute", 1.35f, true));
		EntityRendererRegistry.register(MobEntities.INFECTED_PIGLIN, ctx -> new InfectedPiglinRenderer(ctx, INFECTED_PIGLIN));
		EntityRendererRegistry.register(MobEntities.ASH_HOUND, ctx ->
				new BlockSoulsMobRenderer<>(ctx, new AshHoundModel(ctx.bakeLayer(ASH_HOUND)), 0.5f, "ash_hound", 1f, true));
		EntityRendererRegistry.register(MobEntities.DEBRIS, ctx -> new ThrownItemRenderer<>(ctx, 1.5f, false));
	}
}
