package io.github.tmldude.blocksouls.movement.web;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
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

/**
 * Server side of the web swing: validates shots, spends stamina, and tracks each swing for fall damage, the
 * floating kick and drift. The swing's anchor is synced to every client so they can draw the rope.
 */
public final class WebServer {
	/** The current anchor, synced to all clients for the rope; absent when not swinging. */
	private static AttachmentType<Vec3> anchor;
	/** Server-side rope length, mirroring the client's reel-in. */
	private static AttachmentType<Double> ropeLength;
	/** Game time of the last release, for the no-fall-damage grace window. */
	private static AttachmentType<Long> releasedAt;
	private static MovementConfig config;

	private WebServer() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		anchor = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "web_anchor"),
				builder -> builder.syncWith(Vec3.STREAM_CODEC, AttachmentSyncPredicate.all()));
		ropeLength = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "web_rope_length"));
		releasedAt = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "web_released_at"));

		Networking.registerServerbound(WebPayloads.Attach.TYPE, WebPayloads.Attach.CODEC, (payload, context) -> {
			if (!tryAttach(context.player(), payload.anchor(), payload.block())) {
				Networking.sendToPlayer(context.player(), WebPayloads.Drop.INSTANCE);
			}
		});
		Networking.registerServerbound(WebPayloads.Detach.TYPE, WebPayloads.Detach.CODEC, (payload, context) -> detach(context.player(), false));
		Networking.registerClientbound(WebPayloads.Drop.TYPE, WebPayloads.Drop.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(WebServer::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(WebServer::allowDamage);
	}

	/** The player's web anchor, on either side; null when not swinging. */
	public static @Nullable Vec3 anchor(Player player) {
		return ((AttachmentTarget) player).getAttached(anchor);
	}

	public static boolean isSwinging(Player player) {
		return anchor(player) != null;
	}

	/** Where the rope meets the player. */
	public static Vec3 ropePoint(Player player) {
		return player.position().add(0, Pendulum.ROPE_ATTACH_HEIGHT, 0);
	}

	/** Validates a client's shot and starts the swing; returns whether it was accepted. */
	public static boolean tryAttach(ServerPlayer player, Vec3 target, BlockPos block) {
		if (!config.web.enabled || !canSwing(player) || isSwinging(player)) {
			return false;
		}

		StaminaState stamina = StaminaAttachment.get(player);
		if (!stamina.canAfford(config.web.staminaCost) || !isValidAnchor(player, target, block)) {
			return false;
		}

		StaminaAttachment.set(player, stamina.spend(config.web.staminaCost, config.stamina));
		attach(player, target, Pendulum.initialLength(ropePoint(player).distanceTo(target), config.web));
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.COBWEB_BREAK, SoundSource.PLAYERS, 0.8F, 1.8F);
		return true;
	}

	/** Starts a swing with no checks (tests and debugging). */
	public static void attach(ServerPlayer player, Vec3 target, double length) {
		AttachmentTarget attachments = (AttachmentTarget) player;
		attachments.setAttached(anchor, target);
		attachments.setAttached(ropeLength, length);
		player.resetFallDistance();
	}

	/** Ends the swing (if any) and starts the no-fall-damage window. */
	public static void detach(ServerPlayer player, boolean tellClient) {
		AttachmentTarget attachments = (AttachmentTarget) player;
		if (attachments.getAttached(anchor) == null) {
			return;
		}
		attachments.removeAttached(anchor);
		attachments.removeAttached(ropeLength);
		attachments.setAttached(releasedAt, player.level().getGameTime());
		if (tellClient) {
			Networking.sendToPlayer(player, WebPayloads.Drop.INSTANCE);
		}
	}

	/** Whether this player may start or keep a swing: alive, not riding or downed, and holding the {@code SWING} verb. */
	public static boolean canSwing(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isPassenger() && !PlayerStatus.isDowned(player)
				&& MovementTraits.canSwing(player);
	}

	/** In reach (with some tolerance for latency), in line of sight, and on a solid block. */
	private static boolean isValidAnchor(ServerPlayer player, Vec3 target, BlockPos block) {
		Vec3 eye = player.getEyePosition();
		double reach = MovementTraits.webRange(player) * config.web.serverRangeTolerance;
		if (eye.distanceToSqr(target) > reach * reach) {
			return false;
		}

		ServerLevel level = player.level();
		VoxelShape shape = level.getBlockState(block).getCollisionShape(level, block);
		if (shape.isEmpty() || !shape.bounds().move(block).inflate(0.01).contains(target)) {
			return false;
		}

		// Aim slightly past the anchor: the ray must stop on (or right next to) the anchor's block.
		Vec3 past = target.add(target.subtract(eye).normalize().scale(0.1));
		BlockHitResult hit = level.clip(new ClipContext(eye, past, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(block) || hit.getLocation().distanceToSqr(target) < 1.0;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AttachmentTarget attachments = (AttachmentTarget) player;
			Vec3 target = attachments.getAttached(anchor);
			if (target == null) {
				continue;
			}

			// Mirror the client's rope: sneak reels in; walking on the ground pays it out.
			double length = attachments.getAttachedOrElse(ropeLength, config.web.minLength);
			double distance = ropePoint(player).distanceTo(target);
			if (player.getLastClientInput().shift()) {
				length = Math.max(config.web.minLength, length - config.web.reelSpeed);
				attachments.setAttached(ropeLength, length);
			} else if (player.onGround() && distance > length) {
				length = distance;
				attachments.setAttached(ropeLength, length);
			}

			if (!canSwing(player) || Pendulum.isTooFar(distance, length, config.web)) {
				detach(player, true);
				continue;
			}

			// A swinging player can hang in open air far longer than vanilla's 80-tick floating kick allows.
			player.connection.resetFlyingTicks();
			player.resetFallDistance();
		}
	}

	/** No fall damage while swinging or shortly after letting go. */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL)) {
			return true;
		}
		if (isSwinging(player)) {
			return false;
		}
		Long released = ((AttachmentTarget) player).getAttached(releasedAt);
		return released == null || player.level().getGameTime() - released > config.web.noFallTicksAfterRelease;
	}
}
