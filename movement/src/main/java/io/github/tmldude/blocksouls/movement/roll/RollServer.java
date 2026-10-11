package io.github.tmldude.blocksouls.movement.roll;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;

/** Server side of the dodge roll: validates rolls, spends stamina, runs the roll timer and i-frames. */
public final class RollServer {
	/** Ticks since the player's current roll started; absent when not rolling. */
	private static AttachmentType<Integer> rollTick;
	private static MovementConfig config;

	private RollServer() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		rollTick = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "roll_tick"));

		Networking.registerServerbound(RollPayload.TYPE, RollPayload.CODEC, RollServer::onRoll);
		ServerTickEvents.END_SERVER_TICK.register(RollServer::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RollServer::allowDamage);
	}

	private static void onRoll(RollPayload payload, ServerPlayNetworking.Context context) {
		ServerPlayer player = context.player();
		AttachmentTarget target = (AttachmentTarget) player;
		if (!config.roll.enabled || !player.isAlive() || player.isSpectator() || !player.onGround() || target.hasAttached(rollTick)) {
			return;
		}

		StaminaState stamina = StaminaAttachment.get(player);
		if (!stamina.canAfford(config.roll.staminaCost)) {
			return;
		}

		StaminaAttachment.set(player, stamina.spend(config.roll.staminaCost, config.stamina));
		target.setAttached(rollTick, 0);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			StaminaState stamina = StaminaAttachment.get(player);
			StaminaState next = stamina.tick(config.stamina);
			if (next != stamina) {
				StaminaAttachment.set(player, next);
			}

			AttachmentTarget target = (AttachmentTarget) player;
			Integer tick = target.getAttached(rollTick);
			if (tick != null) {
				target.setAttached(rollTick, Roll.isActive(tick + 1, config.roll) ? tick + 1 : null);
			}
		}
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}

		Integer tick = ((AttachmentTarget) player).getAttached(rollTick);
		return tick == null || !Roll.isInvulnerable(tick, config.roll);
	}
}
