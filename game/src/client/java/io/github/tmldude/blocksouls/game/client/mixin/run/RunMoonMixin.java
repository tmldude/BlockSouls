package io.github.tmldude.blocksouls.game.client.mixin.run;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.renderer.SkyRenderer;

import io.github.tmldude.blocksouls.game.client.run.RunSkyEffects;

/** Blood Moon nights get a red moon. */
@Mixin(SkyRenderer.class)
public abstract class RunMoonMixin {
	@ModifyArg(method = "renderMoon", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform(Lorg/joml/Matrix4f;Lorg/joml/Vector4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
			index = 1)
	private Vector4f blocksouls$redMoon(Vector4f color) {
		return RunSkyEffects.moonColor(color);
	}
}
