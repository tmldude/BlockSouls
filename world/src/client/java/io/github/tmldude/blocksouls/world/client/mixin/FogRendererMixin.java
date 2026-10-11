package io.github.tmldude.blocksouls.world.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;

import io.github.tmldude.blocksouls.world.AshFog;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.WorldConfig;

/** Ash fog: in the Nether the atmospheric fog is thicker and grayer. */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void blocksouls_world$ashFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount,
			ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		WorldConfig.AshFog cfg = BlockSoulsWorld.CONFIG.ashFog;
		if (!cfg.enabled || level.dimension() != Level.NETHER || camera.getFluidInCamera() != FogType.ATMOSPHERIC) {
			return;
		}
		FogData fog = cir.getReturnValue();
		float[] range = AshFog.thicken(fog.environmentalStart, fog.environmentalEnd, cfg.fogStart, cfg.fogEnd);
		fog.environmentalStart = range[0];
		fog.environmentalEnd = range[1];
		fog.skyEnd = Math.min(fog.skyEnd, range[1]);
		fog.cloudEnd = Math.min(fog.cloudEnd, range[1]);
		float[] rgb = AshFog.blend(fog.color.x(), fog.color.y(), fog.color.z(), cfg.ashBlend);
		fog.color.set(rgb[0], rgb[1], rgb[2], fog.color.w());
	}
}
