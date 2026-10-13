package io.github.tmldude.blocksouls.bosses.presentation;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.common.Networking;

/**
 * The name-splash (PLAN2 §3h): as the fog wall rises, everyone in the fight and in bar range sees the boss's name for
 * {@code presentation.splashTicks} and hears the sting. Once per fight: {@link #send} is called from the boss's engage,
 * and {@link #sent} counts splashes for tests.
 */
public final class BossSplash {
	private static final Map<BossEntity, Integer> SENT = new WeakHashMap<>();

	private BossSplash() {
	}

	public static void init() {
		Networking.registerClientbound(BossSplashPayload.TYPE, BossSplashPayload.CODEC);
	}

	public static void send(ServerLevel level, BossEntity boss) {
		SENT.merge(boss, 1, Integer::sum);
		int ticks = BossEntity.framework().presentation.splashTicks;
		if (ticks <= 0) {
			return;
		}
		double range = BossEntity.framework().barViewRange;
		BossSplashPayload payload = new BossSplashPayload(boss.getId(), boss.getDisplayName(), ticks);
		for (ServerPlayer player : level.players()) {
			if (boss.inFight(player) || player.distanceTo(boss) <= range) {
				Networking.sendToPlayer(player, payload);
			}
		}
	}

	/** Splashes sent for {@code boss} since it was created. */
	public static int sent(BossEntity boss) {
		return SENT.getOrDefault(boss, 0);
	}
}
