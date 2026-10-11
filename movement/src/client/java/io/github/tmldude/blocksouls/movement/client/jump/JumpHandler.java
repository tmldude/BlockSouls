package io.github.tmldude.blocksouls.movement.client.jump;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.client.web.WebHandler;
import io.github.tmldude.blocksouls.movement.flight.Flight;
import io.github.tmldude.blocksouls.movement.flight.FlightPayload;
import io.github.tmldude.blocksouls.movement.jump.AirJumpPayload;
import io.github.tmldude.blocksouls.movement.jump.AirJumps;
import io.github.tmldude.blocksouls.movement.jump.JumpController;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

/**
 * Drives {@link JumpController} for the local player, plus the jump-key traits: air jumps ({@code DOUBLE_JUMP})
 * and starting flight with a double tap in the air ({@code FLIGHT}). Player movement is client-authoritative in
 * vanilla, so the jump feel lives entirely on the client.
 */
public final class JumpHandler {
	private static MovementConfig config;
	private static JumpController controller;
	private static final AirJumps airJumps = new AirJumps();
	private static final Flight.DoubleTap flightTap = new Flight.DoubleTap();
	private static boolean wasJumpDown;

	private JumpHandler() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		controller = new JumpController(movementConfig.jump);
		ClientTickEvents.END_CLIENT_TICK.register(JumpHandler::tick);
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		boolean jumpDown = client.options.keyJump.isDown();
		boolean pressed = jumpDown && !wasJumpDown;
		wasJumpDown = jumpDown;
		boolean eligible = isEligible(player);

		Vec3 velocity = player.getDeltaMovement();
		JumpController.Result result = controller.tick(new JumpController.Input(
				player.onGround(),
				jumpDown,
				velocity.y,
				player.getAttributeValue(Attributes.GRAVITY),
				config.jump.enabled && eligible));

		if (result.velocityY() != velocity.y) {
			player.setDeltaMovement(velocity.x, result.velocityY(), velocity.z);
		}
		if (result.jump()) {
			player.jumpFromGround();
		}

		airJumps.tick(player.onGround());
		boolean airborne = eligible && !player.onGround();
		// A press in mid-air, not the one that launched a jump this tick or a coyote jump.
		boolean airPress = config.jump.enabled ? result.airPress() : pressed && airborne;
		if (flightTap.tick(airPress, airborne, config.traits.flightDoubleTapTicks) && canStartFlight(player)) {
			// The server grants flight; the press must not also fire as a buffered jump on landing.
			ClientNetworking.sendToServer(FlightPayload.INSTANCE);
			controller.cancelBuffer();
		} else if (airPress && airJumps.tryUse(MovementTraits.airJumps(player))) {
			airJump(player);
			controller.cancelBuffer();
		}
	}

	/** Called after every local-player jump launch (see {@code LivingEntityMixin}). */
	public static void onJump(LocalPlayer player) {
		if (controller == null || !config.jump.enabled || !isEligible(player)) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		player.setDeltaMovement(velocity.x, velocity.y * controller.onJump(), velocity.z);
	}

	/** A vanilla-height jump in mid-air, with a puff of air under the feet. */
	private static void airJump(LocalPlayer player) {
		Vec3 velocity = player.getDeltaMovement();
		double power = player.getAttributeValue(Attributes.JUMP_STRENGTH) + player.getJumpBoostPower();
		player.setDeltaMovement(velocity.x, power, velocity.z);

		RandomSource random = player.getRandom();
		for (int i = 0; i < 6; i++) {
			player.level().addParticle(ParticleTypes.POOF, player.getX() + (random.nextDouble() - 0.5) * 0.5, player.getY(),
					player.getZ() + (random.nextDouble() - 0.5) * 0.5, 0, -0.05, 0);
		}
		ClientNetworking.sendToServer(AirJumpPayload.INSTANCE);
	}

	private static boolean canStartFlight(LocalPlayer player) {
		return MovementTraits.canFly(player) && !player.getAbilities().mayfly && StaminaAttachment.get(player).value() > 0;
	}

	private static boolean isEligible(LocalPlayer player) {
		return !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isInLiquid()
				&& !player.onClimbable()
				&& !player.isPassenger()
				&& !player.isSpectator()
				&& !player.hasEffect(MobEffects.SLOW_FALLING)
				&& !player.hasEffect(MobEffects.LEVITATION)
				&& !PlayerStatus.isDowned(player)
				&& !WebHandler.isSwinging();
	}
}
