package io.github.tmldude.blocksouls.movement.client.jump;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.jump.JumpController;

/**
 * Drives {@link JumpController} for the local player. Player movement is client-authoritative in
 * vanilla, so the jump feel lives entirely on the client.
 */
public final class JumpHandler {
	private static MovementConfig.Jump config;
	private static JumpController controller;

	private JumpHandler() {
	}

	public static void register(MovementConfig.Jump jumpConfig) {
		config = jumpConfig;
		controller = new JumpController(jumpConfig);
		ClientTickEvents.END_CLIENT_TICK.register(JumpHandler::tick);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || !config.enabled) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		JumpController.Result result = controller.tick(new JumpController.Input(
				player.onGround(),
				client.options.keyJump.isDown(),
				velocity.y,
				player.getAttributeValue(Attributes.GRAVITY),
				isEligible(player)));

		if (result.velocityY() != velocity.y) {
			player.setDeltaMovement(velocity.x, result.velocityY(), velocity.z);
		}
		if (result.jump()) {
			player.jumpFromGround();
		}
	}

	/** Called after every local-player jump launch (see {@code LivingEntityMixin}). */
	public static void onJump(LocalPlayer player) {
		if (controller == null || !config.enabled || !isEligible(player)) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, velocity.y * controller.onJump(), velocity.z);
	}

	private static boolean isEligible(LocalPlayer player) {
		return !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isInLiquid()
				&& !player.onClimbable()
				&& !player.isPassenger()
				&& !player.isSpectator()
				&& !player.hasEffect(MobEffects.SLOW_FALLING)
				&& !player.hasEffect(MobEffects.LEVITATION);
	}
}
