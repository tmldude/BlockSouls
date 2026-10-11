package io.github.tmldude.blocksouls.movement.roll;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;

import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;

/**
 * {@code FREE_ROLL_ON_KILL}: rolls cost no stamina for a while after the player kills a mob. The server stores
 * the game time it ends, synced to the owner so the client doesn't refuse a free roll for lack of stamina.
 */
public final class FreeRoll {
	private static AttachmentType<Long> until;
	private static MovementConfig config;

	private FreeRoll() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		until = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "free_roll_until"),
				builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register(FreeRoll::afterKill);
	}

	public static boolean isActive(Player player) {
		Long end = ((AttachmentTarget) player).getAttached(until);
		return end != null && player.level().getGameTime() < end;
	}

	private static void afterKill(ServerLevel level, Entity killer, LivingEntity killed, DamageSource source) {
		if (killer instanceof ServerPlayer player && killed instanceof Mob && PlayerTraits.has(player, PlayerTraits.FREE_ROLL_ON_KILL)) {
			((AttachmentTarget) player).setAttached(until, level.getGameTime() + config.traits.freeRollTicks);
		}
	}
}
