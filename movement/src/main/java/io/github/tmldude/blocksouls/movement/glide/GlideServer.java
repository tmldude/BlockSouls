package io.github.tmldude.blocksouls.movement.glide;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.event.PlayerMoveEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.stamina.StaminaState;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

/**
 * The glide wings are a custom elytra: {@link EntityElytraEvents#CUSTOM} lets a {@code GLIDE} player start and
 * keep vanilla's fall-flying (pose, physics, anti-cheat allowance) with nothing equipped, for as long as stamina
 * lasts. Vanilla asks the hook on both sides, so the client starts the glide on the jump press like an elytra and
 * the server confirms it; stamina drains on the server tick and the glide ends the tick it hits zero.
 */
public final class GlideServer {
	/** Marks a glide in progress so the start event fires once. */
	private static AttachmentType<Boolean> gliding;
	private static MovementConfig config;

	private GlideServer() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		gliding = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "gliding"));
		EntityElytraEvents.CUSTOM.register((entity, tickElytra) -> canGlide(entity));
		ServerTickEvents.END_SERVER_TICK.register(GlideServer::tick);
	}

	/** Whether the wings carry this entity now (the custom-elytra answer, both sides). */
	public static boolean canGlide(LivingEntity entity) {
		return entity instanceof Player player && Glide.canGlide(MovementTraits.canGlide(player), PlayerStatus.isDowned(player),
				player.isInLiquid(), StaminaAttachment.get(player).value(), config.glide);
	}

	/** Whether the player is fall-flying on the wings (not on an equipped glider). */
	public static boolean isGliding(ServerPlayer player) {
		return ((AttachmentTarget) player).hasAttached(gliding);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AttachmentTarget target = (AttachmentTarget) player;
			boolean onWings = player.isFallFlying() && MovementTraits.canGlide(player) && !hasOwnGlider(player);
			if (!onWings) {
				target.removeAttached(gliding);
				continue;
			}
			if (!target.hasAttached(gliding)) {
				target.setAttached(gliding, Boolean.TRUE);
				PlayerMoveEvents.GLIDE_STARTED.invoker().onPlayer(player);
			}
			tickGlide(player);
		}
	}

	/** One server tick of gliding on the wings: spends stamina. Public for tests. */
	public static void tickGlide(ServerPlayer player) {
		StaminaState stamina = StaminaAttachment.get(player);
		StaminaAttachment.set(player, stamina.spend(Glide.staminaPerTick(config.glide), config.stamina));
	}

	/** A worn elytra glides on its own; the wings only count where vanilla wouldn't. */
	private static boolean hasOwnGlider(ServerPlayer player) {
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.VALUES) {
			if (LivingEntity.canGlideUsing(player.getItemBySlot(slot), slot)) {
				return true;
			}
		}
		return false;
	}
}
