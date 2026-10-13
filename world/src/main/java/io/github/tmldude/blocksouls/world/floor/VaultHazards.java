package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * Floor 4's (The Vault) hazard: walls burst after a few hits, and a plug collapses whole. A closet door releases a
 * swarm (scaled for the party nearby) plus a brute; a hollow wall opens the way to the secret.
 */
public final class VaultHazards {
	private static VaultConfig config = new VaultConfig();

	private VaultHazards() {
	}

	public static VaultConfig config() {
		return config;
	}

	public static void init() {
		config = Config.load(VaultConfig.FILE, VaultConfig.class, VaultConfig::new);
		VaultBlocks.init();
		for (VaultWallBlock.Content content : VaultWallBlock.Content.values()) {
			if (content != VaultWallBlock.Content.NONE) {
				FloorHazards.register(content.getSerializedName(), (player, floor) -> trigger(player, content));
			}
		}
	}

	/**
	 * {@code /bs floor hazard <closet|hollow|shortcut>}: bursts the nearest vault wall holding that, within 48 blocks; a
	 * closet with none near releases one at the player.
	 */
	static String trigger(ServerPlayer player, VaultWallBlock.Content content) {
		ServerLevel level = player.level();
		BlockPos nearest = null;
		for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-48, -6, -48), player.blockPosition().offset(48, 10, 48))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof VaultWallBlock && state.getValue(VaultWallBlock.CONTENT) == content
					&& (nearest == null || pos.distSqr(player.blockPosition()) < nearest.distSqr(player.blockPosition()))) {
				nearest = pos.immutable();
			}
		}
		if (nearest == null) {
			if (content == VaultWallBlock.Content.CLOSET) {
				return "No closet near: released " + releaseCloset(level, player.blockPosition(), player) + " mobs at you";
			}
			return "No " + content.getSerializedName() + " wall within 48 blocks";
		}
		VaultWallBlock wall = (VaultWallBlock) level.getBlockState(nearest).getBlock();
		for (int i = 0; i < 8 && !wall.hit(level, nearest, player); i++) {
			// hit until it bursts
		}
		return "Burst the " + content.getSerializedName() + " wall at " + nearest.toShortString();
	}

	/** Bursts a wall: removes it, collapses the rest of its plug and runs what the plug hid. */
	static void burst(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		VaultWallBlock.Content content = state.getValue(VaultWallBlock.CONTENT);
		level.destroyBlock(pos, false, player);
		if (content == VaultWallBlock.Content.NONE) {
			return;
		}
		BlockPos floor = collapse(level, pos, content, player);
		switch (content) {
			case CLOSET -> releaseCloset(level, floor, player);
			case HOLLOW -> level.playSound(null, pos, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.BLOCKS, 0.5F, 0.6F);
			case SHORTCUT -> level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.7F, 0.6F);
			default -> {
			}
		}
	}

	/** Breaks every wall of the same plug touching {@code start}; returns the plug's lowest open position. */
	private static BlockPos collapse(ServerLevel level, BlockPos start, VaultWallBlock.Content content, ServerPlayer player) {
		BlockPos lowest = start;
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		int broken = 0;
		while (!queue.isEmpty() && broken < Math.max(1, config.plugCollapseLimit)) {
			BlockPos at = queue.poll();
			if (at.getY() < lowest.getY()) {
				lowest = at;
			}
			for (Direction direction : Direction.values()) {
				BlockPos next = at.relative(direction);
				if (seen.add(next)) {
					BlockState state = level.getBlockState(next);
					if (state.getBlock() instanceof VaultWallBlock && state.getValue(VaultWallBlock.CONTENT) == content) {
						level.destroyBlock(next, false, player);
						broken++;
						queue.add(next);
					}
				}
			}
		}
		return lowest;
	}

	/** Releases a closet's swarm (scaled for the players near it) and its brutes at a position, angry at {@code target}. */
	public static int releaseCloset(ServerLevel level, BlockPos at, ServerPlayer target) {
		int reach = Math.max(1, config.closetPartyReach);
		int players = (int) level.players().stream().filter(p -> !p.isSpectator() && p.blockPosition().closerThan(at, reach)).count();
		int swarm = config.swarm(PartyScaling.directorBurst(Math.clamp(players, 1, 8)));
		int spawned = spawn(level, config.closetSwarmMob, swarm, at, target) + spawn(level, config.closetBruteMob, config.closetBrutes, at, target);
		level.playSound(null, at, SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
		return spawned;
	}

	private static int spawn(ServerLevel level, String id, int count, BlockPos at, ServerPlayer target) {
		Identifier parsed = Identifier.tryParse(id);
		Optional<EntityType<?>> type = parsed == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed);
		int spawned = 0;
		for (int i = 0; type.isPresent() && i < count; i++) {
			Entity entity = type.get().spawn(level, at, EntitySpawnReason.TRIGGERED);
			if (entity instanceof Mob mob) {
				mob.setTarget(target);
			}
			if (entity != null) {
				spawned++;
			}
		}
		return spawned;
	}
}
