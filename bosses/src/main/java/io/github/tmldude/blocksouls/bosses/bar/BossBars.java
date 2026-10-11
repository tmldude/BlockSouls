package io.github.tmldude.blocksouls.bosses.bar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.common.Networking;

/**
 * Keeps each boss's custom bar in sync: shown to participants and to anyone within {@code barViewRange}. We don't use
 * vanilla's {@code ServerBossEvent} at all, so vanilla's bar never draws for our bosses.
 */
public final class BossBars {
	private static final Map<BossEntity, Tracker> TRACKERS = new WeakHashMap<>();

	private BossBars() {
	}

	private static final class Tracker {
		final Set<UUID> viewers = new HashSet<>();
		final Set<UUID> inFight = new HashSet<>();
		float health = -1;
		float maxHealth = -1;
		int phase = -1;
		boolean engaged;
		int lastFull;
	}

	public static void init() {
		Networking.registerClientbound(BossBarPayload.TYPE, BossBarPayload.CODEC);
	}

	/** Called by the boss every other tick. */
	public static void tick(ServerLevel level, BossEntity boss) {
		Tracker tracker = TRACKERS.computeIfAbsent(boss, b -> new Tracker());
		double range = BossEntity.framework().barViewRange;
		Set<UUID> participants = boss.participantIds();
		List<ServerPlayer> viewers = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			boolean participant = boss.isEngaged() && participants.contains(player.getUUID()) && boss.inArena(player);
			if (participant || player.distanceToSqr(boss) <= range * range) {
				viewers.add(player);
			}
		}

		boolean changed = tracker.health != boss.getHealth() || tracker.maxHealth != boss.getMaxHealth() || tracker.phase != boss.phase()
				|| tracker.engaged != boss.isEngaged();
		boolean keepAlive = boss.tickCount - tracker.lastFull >= BossEntity.framework().barKeepAliveTicks;
		Set<UUID> now = new HashSet<>();
		for (ServerPlayer player : viewers) {
			UUID id = player.getUUID();
			now.add(id);
			boolean inFight = boss.isEngaged() && participants.contains(id);
			boolean fightChanged = inFight != tracker.inFight.contains(id);
			if (changed || keepAlive || fightChanged || !tracker.viewers.contains(id)) {
				Networking.sendToPlayer(player, payload(boss, inFight));
			}
			if (inFight) {
				tracker.inFight.add(id);
			} else {
				tracker.inFight.remove(id);
			}
		}
		for (UUID gone : tracker.viewers) {
			if (!now.contains(gone)) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(gone);
				if (player != null) {
					Networking.sendToPlayer(player, BossBarPayload.remove(boss.getId()));
				}
				tracker.inFight.remove(gone);
			}
		}
		tracker.viewers.clear();
		tracker.viewers.addAll(now);
		tracker.health = boss.getHealth();
		tracker.maxHealth = boss.getMaxHealth();
		tracker.phase = boss.phase();
		tracker.engaged = boss.isEngaged();
		if (keepAlive) {
			tracker.lastFull = boss.tickCount;
		}
	}

	/** Hides the bar for everyone (death, removal). */
	public static void removeAll(ServerLevel level, BossEntity boss) {
		Tracker tracker = TRACKERS.remove(boss);
		if (tracker == null) {
			return;
		}
		for (UUID id : tracker.viewers) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			if (player != null) {
				Networking.sendToPlayer(player, BossBarPayload.remove(boss.getId()));
			}
		}
	}

	static BossBarPayload payload(BossEntity boss, boolean inFight) {
		double[] thresholds = boss.definition().thresholds();
		List<Float> notches = new ArrayList<>();
		for (int i = 1; i < thresholds.length; i++) {
			notches.add((float) thresholds[i]);
		}
		SoundEvent music = boss.definition().music();
		Optional<Identifier> musicId = music == null ? Optional.empty() : Optional.ofNullable(BuiltInRegistries.SOUND_EVENT.getKey(music));
		return new BossBarPayload(boss.getId(), false, boss.getDisplayName(), boss.getHealth(), boss.getMaxHealth(), notches, boss.phase(),
				boss.isEngaged(), inFight, musicId);
	}
}
