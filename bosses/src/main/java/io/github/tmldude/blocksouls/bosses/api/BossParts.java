package io.github.tmldude.blocksouls.bosses.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Lookup for {@link BossPart}s, so they behave like vanilla's dragon parts: mixins add them to
 * {@code Level.getEntities(Entity, AABB, Predicate)} (crosshair picking, bullets, sweeps) and resolve their ids
 * ({@code ServerLevel.getEntityOrPart}, {@code ClientLevel.getEntity}) so attacking one works. Bosses with parts
 * are tracked per level as they load and unload.
 */
public final class BossParts {
	private static final Map<Level, Set<BossEntity>> BOSSES = new ConcurrentHashMap<>();

	private BossParts() {
	}

	public static void track(BossEntity boss) {
		if (boss.parts().length > 0) {
			BOSSES.computeIfAbsent(boss.level(), level -> ConcurrentHashMap.newKeySet()).add(boss);
		}
	}

	public static void untrack(BossEntity boss) {
		Set<BossEntity> bosses = BOSSES.get(boss.level());
		if (bosses != null) {
			bosses.remove(boss);
			if (bosses.isEmpty()) {
				BOSSES.remove(boss.level());
			}
		}
	}

	public static void forget(Level level) {
		BOSSES.remove(level);
	}

	/** Adds the parts in {@code box} that pass {@code selector} (called from the level mixin). */
	public static void collect(Level level, @Nullable Entity except, AABB box, Predicate<? super Entity> selector, List<Entity> output) {
		Set<BossEntity> bosses = BOSSES.get(level);
		if (bosses == null) {
			return;
		}
		for (BossEntity boss : bosses) {
			if (boss == except || boss.isRemoved()) {
				continue;
			}
			for (BossPart part : boss.parts()) {
				if (part != except && selector.test(part) && box.intersects(part.getBoundingBox())) {
					output.add(part);
				}
			}
		}
	}

	public static @Nullable Entity byId(Level level, int id) {
		Set<BossEntity> bosses = BOSSES.get(level);
		if (bosses == null) {
			return null;
		}
		for (BossEntity boss : bosses) {
			for (BossPart part : boss.parts()) {
				if (part.getId() == id) {
					return part;
				}
			}
		}
		return null;
	}
}
