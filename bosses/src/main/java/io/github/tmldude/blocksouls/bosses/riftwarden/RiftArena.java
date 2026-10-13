package io.github.tmldude.blocksouls.bosses.riftwarden;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * The final arena's trigger plate: world builds the ring and its plate at the {@code final_arena} structure slot; a
 * player stepping on the plate brings the Riftwarden out of the rift there and starts the fight. No day, no timer
 * (game's day loop skips {@code timeless} floors), so the plate is the act's only start.
 */
public final class RiftArena {
	private static final int CHECK_EVERY = 10;

	private RiftArena() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY == 0) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		for (PlacedFloor floor : Floors.all(server)) {
			FloorSlot slot = arenaSlot(floor).orElse(null);
			if (slot == null) {
				continue;
			}
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, floor.dimension()));
			if (level == null || level.players().isEmpty()) {
				continue;
			}
			ServerPlayer onPlate = onPlate(level, slot.pos());
			if (onPlate != null) {
				start(level, slot, onPlate);
			}
		}
	}

	/** The floor's final-arena slot (its position is the plate, its radius the ring's). */
	public static Optional<FloorSlot> arenaSlot(PlacedFloor floor) {
		return floor.slots(FloorSlotKind.STRUCTURE).stream().filter(s -> CommonIds.FINAL_ARENA_SLOT.equals(s.tag())).findFirst();
	}

	private static @Nullable ServerPlayer onPlate(ServerLevel level, BlockPos plate) {
		double r = Riftwarden.config().plateRadius;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double dx = player.getX() - (plate.getX() + 0.5);
			double dz = player.getZ() - (plate.getZ() + 0.5);
			double dy = player.getY() - plate.getY();
			if (dx * dx + dz * dz <= r * r && dy > -1 && dy < 2.5) {
				return player;
			}
		}
		return null;
	}

	/** The living Riftwarden of this arena, if any. */
	public static Optional<Riftwarden> find(ServerLevel level, BlockPos centre, int radius) {
		List<Riftwarden> found = level.getEntitiesOfClass(Riftwarden.class, new AABB(centre).inflate(radius + 8), Riftwarden::isAlive);
		return found.stream().findFirst();
	}

	/** Brings the boss out (or wakes the one waiting) and engages {@code player}. */
	static Riftwarden start(ServerLevel level, FloorSlot slot, ServerPlayer player) {
		Riftwarden boss = find(level, slot.pos(), slot.radius()).orElse(null);
		if (boss == null) {
			boss = spawn(level, slot);
			if (boss == null) {
				return null;
			}
			level.playSound(null, slot.pos(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 0.6f);
			for (ServerPlayer other : level.players()) {
				if (boss.inArena(other)) {
					other.sendSystemMessage(Component.translatable("boss.blocksouls_bosses.riftwarden.rises"));
				}
			}
		}
		if (!boss.isEngaged() && !boss.isReturning()) {
			boss.engage(player);
		}
		return boss;
	}

	/** Spawns the boss on the plate, home and arena set to the slot. */
	public static @Nullable Riftwarden spawn(ServerLevel level, FloorSlot slot) {
		Riftwarden boss = Riftwardens.TYPE.create(level, EntitySpawnReason.EVENT);
		if (boss == null) {
			return null;
		}
		BlockPos pos = slot.pos();
		boss.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		boss.setHome(pos);
		boss.setArenaRadius(Math.max(Riftwarden.config().arenaRadius, slot.radius() + 1));
		boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		level.addFreshEntity(boss);
		return boss;
	}
}
