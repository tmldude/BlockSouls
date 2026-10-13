package io.github.tmldude.blocksouls.common.boss;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;

/**
 * Spawn a boss by its id ({@code CommonIds.FURNACE_KNIGHT} etc.) without importing the bosses module: the
 * Director's mini-boss rotation goes through here. A boss's entity type id is its boss id, so the default
 * looks the type up in the registry and spawns it; the bosses module may install a provider that also sets
 * up its arena. Returns null when the id is unknown (bosses absent) or the spawn failed.
 *
 * <p>Threading: server thread.
 */
public final class BossSpawns {
	private static final Spawner DEFAULT = (level, bossId, pos) -> BuiltInRegistries.ENTITY_TYPE.getOptional(bossId)
			.map(type -> (Entity) type.spawn(level, pos, EntitySpawnReason.EVENT))
			.orElse(null);

	private static Spawner spawner = DEFAULT;

	private BossSpawns() {
	}

	public static @Nullable Entity spawn(ServerLevel level, Identifier bossId, BlockPos pos) {
		return spawner.spawn(level, bossId, pos);
	}

	/** True if {@code bossId} names a registered entity type (the bosses module is loaded). */
	public static boolean exists(Identifier bossId) {
		return BuiltInRegistries.ENTITY_TYPE.containsKey(bossId);
	}

	/** Called once by the bosses module, if it wants more than the default type lookup. */
	public static void setSpawner(Spawner newSpawner) {
		spawner = Objects.requireNonNull(newSpawner);
	}

	@FunctionalInterface
	public interface Spawner {
		@Nullable Entity spawn(ServerLevel level, Identifier bossId, BlockPos pos);
	}
}
