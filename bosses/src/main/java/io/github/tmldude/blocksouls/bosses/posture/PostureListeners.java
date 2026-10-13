package io.github.tmldude.blocksouls.bosses.posture;

import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.common.event.CombatEvents;
import io.github.tmldude.blocksouls.common.event.PlayerMoveEvents;

/**
 * Feeds boss posture from the shared events (PLAN2 §3h): precision hits ({@link CombatEvents#HIT}) and well-timed
 * rolls ({@link PlayerMoveEvents#ROLLED}) inside an attack's hit window.
 */
public final class PostureListeners {
	private PostureListeners() {
	}

	public static void init() {
		CombatEvents.HIT.register((shooter, target, damage, precision, killed) -> {
			if (!precision || killed) {
				return;
			}
			BossEntity boss = target instanceof BossEntity b ? b : null;
			if (boss != null && boss.isEngaged()) {
				boss.onPrecisionHit(shooter, damage);
			}
		});
		PlayerMoveEvents.ROLLED.register(PostureListeners::rolled);
	}

	private static void rolled(ServerPlayer player) {
		double reach = 64;
		for (BossEntity boss : player.level().getEntitiesOfClass(BossEntity.class, player.getBoundingBox().inflate(reach),
				b -> b.isEngaged() && b.isAlive())) {
			if (boss.inFight(player)) {
				boss.onRolled(player);
			}
		}
	}
}
