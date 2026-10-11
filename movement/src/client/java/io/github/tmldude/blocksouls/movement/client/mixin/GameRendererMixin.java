package io.github.tmldude.blocksouls.movement.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import io.github.tmldude.blocksouls.movement.client.roll.RollHandler;

/** Tilts the camera during a dodge roll, next to vanilla's hurt tilt (applied to the world and the held item). */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("RETURN"))
	private void blocksouls$rollTilt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getCameraEntity() != client.player) {
			return;
		}
		float tilt = RollHandler.cameraTilt(client.player, cameraState.cameraEntityPartialTicks);
		if (tilt != 0) {
			poseStack.rotateDegrees(Axis.ZP, tilt);
		}
	}
}
