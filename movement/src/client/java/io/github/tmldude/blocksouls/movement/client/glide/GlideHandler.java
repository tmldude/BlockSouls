package io.github.tmldude.blocksouls.movement.client.glide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.glide.Glide;
import io.github.tmldude.blocksouls.movement.glide.Updrafts;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

/**
 * Updraft riding for the local player: while gliding on the wings through an updraft, lift is added to the
 * (client-authoritative) velocity each tick. Starting and stopping the glide is vanilla's fall-flying, enabled
 * by the server's custom-elytra hook.
 */
public final class GlideHandler {
	private static MovementConfig.Glide config;

	private GlideHandler() {
	}

	public static void register(MovementConfig.Glide glideConfig) {
		config = glideConfig;
		ClientTickEvents.END_CLIENT_TICK.register(GlideHandler::tick);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || !player.isFallFlying() || !MovementTraits.canGlide(player)) {
			return;
		}
		boolean inUpdraft = Updrafts.isInUpdraft(player, config);
		double lift = Glide.lift(true, inUpdraft, config);
		if (lift > 0) {
			Vec3 velocity = player.getDeltaMovement();
			player.setDeltaMovement(velocity.x, velocity.y + lift, velocity.z);
			if (player.tickCount % 3 == 0) {
				player.level().addParticle(ParticleTypes.CLOUD, player.getX() + (player.getRandom().nextDouble() - 0.5) * 1.5,
						player.getY() - 0.5, player.getZ() + (player.getRandom().nextDouble() - 0.5) * 1.5, 0, 0.08, 0);
			}
		}
	}
}
