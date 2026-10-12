package io.github.tmldude.blocksouls.game.player.boon.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/** Shared lookups for rules. */
final class RuleSupport {
	private RuleSupport() {
	}

	/** Living hostiles (mobs that are enemies) within {@code radius} of the player, alive, nearest first. */
	static List<Mob> hostilesNear(ServerPlayer player, double radius) {
		ServerLevel level = player.level();
		AABB box = player.getBoundingBox().inflate(radius);
		List<Mob> mobs = new ArrayList<>(level.getEntitiesOfClass(Mob.class, box,
				mob -> mob instanceof Enemy && mob.isAlive() && mob.distanceToSqr(player) <= radius * radius));
		mobs.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
		return mobs;
	}

	/** Other standing players within {@code range}, nearest first. */
	static List<ServerPlayer> alliesNear(ServerPlayer player, double range) {
		List<ServerPlayer> allies = new ArrayList<>();
		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other != player && other.level() == player.level() && !PlayerStatus.isDowned(other) && !other.isSpectator()
					&& other.distanceToSqr(player) <= range * range) {
				allies.add(other);
			}
		}
		allies.sort(Comparator.comparingDouble(other -> other.distanceToSqr(player)));
		return allies;
	}

	static @Nullable ServerPlayer nearestAlly(ServerPlayer player, double range) {
		List<ServerPlayer> allies = alliesNear(player, range);
		return allies.isEmpty() ? null : allies.getFirst();
	}

	static boolean isHostile(LivingEntity entity) {
		return entity instanceof Enemy && entity.isAlive();
	}
}
