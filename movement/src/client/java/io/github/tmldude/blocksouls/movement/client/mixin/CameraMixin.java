package io.github.tmldude.blocksouls.movement.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import io.github.tmldude.blocksouls.movement.client.roll.RollHandler;

/** Lowers the view to crouch height during a dodge roll; the camera already eases toward the target eye height. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
	private float blocksouls$rollEyeHeight(Entity entity, Operation<Float> original) {
		float eyeHeight = original.call(entity);
		return entity instanceof LocalPlayer player ? RollHandler.eyeHeight(player, eyeHeight) : eyeHeight;
	}
}
