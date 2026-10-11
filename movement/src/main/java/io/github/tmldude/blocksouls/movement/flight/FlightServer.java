package io.github.tmldude.blocksouls.movement.flight;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;
import io.github.tmldude.blocksouls.movement.trait.TraitMath;

/**
 * {@code FLIGHT}: the server grants creative-style flight (at a slower speed) only while it's active and takes it
 * back the moment it ends. The marker is saved with the player so a flight interrupted by a logout or crash is still
 * revoked on the next join, since vanilla saves {@code mayfly} with the player.
 */
public final class FlightServer {
	private static AttachmentType<Boolean> active;
	private static MovementConfig config;

	private FlightServer() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		active = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "flight_active"),
				builder -> builder.persistent(Codec.BOOL));

		Networking.registerServerbound(FlightPayload.TYPE, FlightPayload.CODEC, (payload, context) -> tryStart(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(FlightServer::tick);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			if (isFlying(listener.player)) {
				stop(listener.player);
			}
		});
	}

	public static boolean isFlying(ServerPlayer player) {
		return ((AttachmentTarget) player).hasAttached(active);
	}

	public static boolean tryStart(ServerPlayer player) {
		if (isFlying(player) || !MovementTraits.canFly(player) || PlayerStatus.isDowned(player) || player.onGround()
				|| hasOwnFlight(player) || player.isPassenger() || player.isFallFlying() || !player.isAlive()
				|| StaminaAttachment.get(player).value() <= 0) {
			return false;
		}

		((AttachmentTarget) player).setAttached(active, true);
		Abilities abilities = player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
		abilities.setFlyingSpeed(TraitMath.flyingSpeed(config.traits));
		player.onUpdateAbilities();
		return true;
	}

	/** Ends the flight and puts the player's abilities back to their game mode's. */
	public static void stop(ServerPlayer player) {
		((AttachmentTarget) player).removeAttached(active);
		Abilities abilities = player.getAbilities();
		if (!hasOwnFlight(player)) {
			player.gameMode().updatePlayerAbilities(abilities);
		}
		abilities.setFlyingSpeed((float) TraitMath.CREATIVE_FLYING_SPEED);
		player.onUpdateAbilities();
	}

	/** Creative and spectator players fly anyway; flight never touches their abilities. */
	private static boolean hasOwnFlight(ServerPlayer player) {
		return player.isCreative() || player.isSpectator();
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isFlying(player)) {
				continue;
			}

			StaminaState stamina = StaminaAttachment.get(player);
			if (hasOwnFlight(player) || Flight.shouldEnd(player.onGround(), stamina.value(), MovementTraits.canFly(player),
					PlayerStatus.isDowned(player), player.getAbilities().flying)) {
				stop(player);
				continue;
			}

			StaminaAttachment.set(player, stamina.spend(TraitMath.flightStaminaPerTick(config.traits), config.stamina));
			player.connection.resetFlyingTicks();
		}
	}
}
