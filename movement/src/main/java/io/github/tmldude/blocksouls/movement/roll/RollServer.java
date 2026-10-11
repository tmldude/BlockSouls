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

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

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
		FreeRoll.register(movementConfig);

		Networking.registerServerbound(RollPayload.TYPE, RollPayload.CODEC, (payload, context) -> tryStart(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(RollServer::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(RollServer::allowDamage);
	}

	/** Starts a roll if the player may roll now; returns whether it started. */
	public static boolean tryStart(ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		Integer tick = target.getAttached(rollTick);
		// A roll's i-frames can outlast its movement (ROLL_IFRAMES); a new roll may start once the movement ends.
		if (!config.roll.enabled || !player.isAlive() || player.isSpectator() || !player.onGround() || PlayerStatus.isDowned(player)
				|| (tick != null && Roll.isActive(tick, config.roll))) {
			return false;
		}

		boolean free = FreeRoll.isActive(player);
		StaminaState stamina = StaminaAttachment.get(player);
		if (!free && !stamina.canAfford(config.roll.staminaCost)) {
			return false;
		}

		if (!free) {
			StaminaAttachment.set(player, stamina.spend(config.roll.staminaCost, config.stamina));
		}
		target.setAttached(rollTick, 0);
		return true;
	}

	/** Starts a roll with no checks or stamina cost (debug commands and tests). */
	public static void forceStart(ServerPlayer player) {
		((AttachmentTarget) player).setAttached(rollTick, 0);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			StaminaState stamina = StaminaAttachment.get(player);
			StaminaState next = stamina.tick(config.stamina, MovementTraits.staminaRegenMultiplier(player));
			if (next != stamina) {
				StaminaAttachment.set(player, next);
			}

			AttachmentTarget target = (AttachmentTarget) player;
			Integer tick = target.getAttached(rollTick);
			if (tick != null) {
				boolean tracked = tick + 1 < Roll.trackedTicks(config.roll, MovementTraits.rollIframeEnd(player));
				target.setAttached(rollTick, tracked ? tick + 1 : null);
			}
		}
	}

	/** Whether the player is in a roll's i-frames right now. */
	public static boolean isInvulnerable(ServerPlayer player) {
		Integer tick = ((AttachmentTarget) player).getAttached(rollTick);
		return tick != null && Roll.isInvulnerable(tick, config.roll, MovementTraits.rollIframeEnd(player));
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		return !isInvulnerable(player);
	}
}
