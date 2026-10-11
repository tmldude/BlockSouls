package io.github.tmldude.blocksouls.movement.jump;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.BlockSoulsMovement;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;

/** Counts air jumps per player (bounded by the trait) and shows the puff to everyone else. */
public final class AirJumpServer {
	/** Air jumps used since last on the ground. */
	private static AttachmentType<Integer> used;

	private AirJumpServer() {
	}

	public static void register() {
		used = AttachmentRegistry.create(Identifier.fromNamespaceAndPath(BlockSoulsMovement.MOD_ID, "air_jumps_used"));
		Networking.registerServerbound(AirJumpPayload.TYPE, AirJumpPayload.CODEC, (payload, context) -> onAirJump(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(AirJumpServer::tick);
	}

	private static void onAirJump(ServerPlayer player) {
		AttachmentTarget target = (AttachmentTarget) player;
		int count = target.getAttachedOrElse(used, 0);
		if (player.onGround() || PlayerStatus.isDowned(player) || count >= MovementTraits.airJumps(player)) {
			return;
		}
		target.setAttached(used, count + 1);

		// The jumper sees their own puff right away, client-side.
		ServerLevel level = player.level();
		for (ServerPlayer other : level.players()) {
			if (other != player) {
				level.sendParticles(other, ParticleTypes.POOF, false, false, player.getX(), player.getY(), player.getZ(), 6, 0.25, 0.05, 0.25, 0.02);
			}
		}
		level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.onGround()) {
				((AttachmentTarget) player).removeAttached(used);
			}
		}
	}
}
