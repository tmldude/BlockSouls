package io.github.tmldude.blocksouls.bosses.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;
import io.github.tmldude.blocksouls.bosses.BossesConfig;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.run.Runs;

/**
 * The field-boss spawner (PLAN.md D2b). Each boss kind registers a {@link FieldBossRule}; the spawner checks them
 * every {@code scanIntervalTicks} during a run and enforces the shared rules:
 * <ul>
 * <li>never while the run is {@code NOT_STARTED} or {@code OVER};</li>
 * <li>a boss killed this run never comes back (tracked in {@link FieldBossData});</li>
 * <li>at most one roaming field boss at a time, one of each kind, each kind with a cooldown after it leaves;</li>
 * <li>never within {@code minDistanceFromSpawn} of the world spawn;</li>
 * <li>announced through the world: a distant cue toward it, a lightning flash and a smoke column over it.</li>
 * </ul>
 */
public final class FieldBosses {
	private static final Map<Identifier, FieldBossRule> RULES = new LinkedHashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();

	private FieldBosses() {
	}

	/** Registers the spawn rule for boss {@code id} (a {@code CommonIds} boss id). Call from your mod initializer. */
	public static void register(Identifier id, FieldBossRule rule) {
		if (RULES.putIfAbsent(id, rule) != null) {
			throw new IllegalArgumentException("Field boss rule already registered: " + id);
		}
	}

	public static Map<Identifier, FieldBossRule> rules() {
		return Collections.unmodifiableMap(RULES);
	}

	public static BossesConfig.Field config() {
		return BlockSoulsBosses.CONFIG.field;
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (config().enabled && server.getTickCount() % Math.max(1, config().scanIntervalTicks) == 0) {
				scan(server);
			}
		});
		RunEvents.RUN_STARTED.register((server, state) -> FieldBossData.get(server).resetRun());
	}

	// ---------------------------------------------------------------------------------------------------------

	private static void scan(MinecraftServer server) {
		if (config().requireActiveRun && !Runs.get(server).isActive()) {
			return;
		}
		FieldBossData data = FieldBossData.get(server);
		dropStale(server, data);
		rollDay(server, data);

		List<Map.Entry<Identifier, FieldBossRule>> rules = new ArrayList<>(RULES.entrySet());
		Collections.shuffle(rules, new java.util.Random(RANDOM.nextLong()));
		long now = server.overworld().getGameTime();
		for (Map.Entry<Identifier, FieldBossRule> entry : rules) {
			Identifier id = entry.getKey();
			FieldBossRule rule = entry.getValue();
			if (!rule.settings().enabled || data.killed.contains(id) || data.active.containsKey(id)
					|| data.cooldownUntil.getOrDefault(id, 0L) > now) {
				continue;
			}
			if (rule.roaming() && anyRoamingActive(data)) {
				continue;
			}
			if (rule.schedule() == FieldBossRule.Schedule.DAILY && !data.pendingToday.contains(id)) {
				continue;
			}
			if (trySpawn(server, data, id, rule, null)) {
				data.pendingToday.remove(id);
				data.setDirty();
				return;
			}
		}
	}

	private static void rollDay(MinecraftServer server, FieldBossData data) {
		long day = server.overworld().getOverworldClockTime() / 24000L;
		if (day == data.lastDay) {
			return;
		}
		data.lastDay = day;
		data.pendingToday.clear();
		for (Map.Entry<Identifier, FieldBossRule> entry : RULES.entrySet()) {
			FieldBossRule rule = entry.getValue();
			if (rule.schedule() == FieldBossRule.Schedule.DAILY && RANDOM.nextDouble() < rule.settings().chancePerDay) {
				data.pendingToday.add(entry.getKey());
			}
		}
		data.setDirty();
	}

	private static boolean anyRoamingActive(FieldBossData data) {
		for (Identifier id : data.active.keySet()) {
			FieldBossRule rule = RULES.get(id);
			if (rule == null || rule.roaming()) {
				return true;
			}
		}
		return false;
	}

	/** Forgets live-boss records whose entity is gone (its chunk is loaded but it isn't there). */
	private static void dropStale(MinecraftServer server, FieldBossData data) {
		data.active.entrySet().removeIf(entry -> {
			FieldBossData.Active active = entry.getValue();
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, active.dimension()));
			if (level == null) {
				return true;
			}
			if (!level.hasChunkAt(active.pos())) {
				return false;
			}
			return !(level.getEntity(active.entity()) instanceof BossEntity boss) || !boss.isAlive();
		});
	}

	/**
	 * Tries every candidate player (or only {@code near}) for a spot and spawns the boss there.
	 *
	 * @return true if a boss appeared
	 */
	private static boolean trySpawn(MinecraftServer server, FieldBossData data, Identifier id, FieldBossRule rule, @Nullable ServerPlayer near) {
		List<ServerPlayer> players = new ArrayList<>(near != null ? List.of(near) : server.getPlayerList().getPlayers());
		Collections.shuffle(players, new java.util.Random(RANDOM.nextLong()));
		for (ServerPlayer player : players) {
			if (player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			Optional<FieldBossRule.Spot> spot = rule.find(level, player, RANDOM);
			if (spot.isEmpty() || (near == null && tooCloseToSpawn(spot.get()))) {
				continue;
			}
			BossEntity boss = spawn(spot.get(), rule);
			if (boss != null) {
				data.active.put(id, new FieldBossData.Active(boss.getUUID(), spot.get().level().dimension().identifier(), boss.blockPosition()));
				data.setDirty();
				return true;
			}
		}
		return false;
	}

	private static boolean tooCloseToSpawn(FieldBossRule.Spot spot) {
		ServerLevel level = spot.level();
		if (level.dimension() != Level.OVERWORLD) {
			return false;
		}
		BlockPos spawn = level.getRespawnData().globalPos().pos();
		double min = config().minDistanceFromSpawn;
		return spawn.distSqr(spot.pos()) < min * min;
	}

	private static @Nullable BossEntity spawn(FieldBossRule.Spot spot, FieldBossRule rule) {
		ServerLevel level = spot.level();
		BossEntity boss = rule.create(level);
		BlockPos pos = spot.pos();
		boss.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, spot.yaw(), 0);
		boss.setHome(pos);
		if (spot.arenaRadius() > 0) {
			boss.setArenaRadius(spot.arenaRadius());
		}
		boss.makeFieldBoss(rule.settings().leaveAfterTicks);
		boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		if (!level.addFreshEntity(boss)) {
			return null;
		}
		announce(level, boss);
		BlockSoulsBosses.LOGGER.info("Field boss {} appeared at {} in {}", boss.definition().id(), pos, level.dimension().identifier());
		return boss;
	}

	/** Show, don't tell: a far-off cue from the boss's direction and a flash over it. No chat message. */
	private static void announce(ServerLevel level, BossEntity boss) {
		LightningBolt flash = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (flash != null) {
			flash.snapTo(boss.getX(), boss.getY(), boss.getZ());
			flash.setVisualOnly(true);
			level.addFreshEntity(flash);
		}
		double range = config().announceRange;
		for (ServerPlayer player : level.players()) {
			double distance = player.distanceTo(boss);
			if (distance > range || distance < 1) {
				continue;
			}
			// Play it 24 blocks toward the boss so it reads as "over there", whatever the real distance.
			Vec3 toward = boss.position().subtract(player.position()).normalize().scale(Math.min(24, distance));
			Vec3 at = player.position().add(toward);
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.HOSTILE,
					at.x, at.y, at.z, 3.0f, 0.6f, RANDOM.nextLong()));
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Hooks from BossEntity

	public static void onEngaged(ServerLevel level, BossEntity boss) {
	}

	/** The boss died: it won't come back this run. */
	public static void onKilled(ServerLevel level, BossEntity boss) {
		FieldBossData data = FieldBossData.get(level.getServer());
		Identifier id = boss.definition().id();
		data.killed.add(id);
		data.active.remove(id);
		data.setDirty();
	}

	/** It walked off unengaged: cooldown, then it may appear again. */
	public static void onLeft(ServerLevel level, BossEntity boss) {
		release(level, boss);
	}

	/** Discarded some other way (command): same as leaving. */
	public static void onRemoved(ServerLevel level, BossEntity boss) {
		release(level, boss);
	}

	private static void release(ServerLevel level, BossEntity boss) {
		FieldBossData data = FieldBossData.get(level.getServer());
		Identifier id = boss.definition().id();
		FieldBossData.Active active = data.active.get(id);
		if (active != null && active.entity().equals(boss.getUUID())) {
			data.active.remove(id);
			FieldBossRule rule = RULES.get(id);
			if (rule != null) {
				data.cooldownUntil.put(id, level.getServer().overworld().getGameTime() + rule.settings().cooldownTicks);
			}
			data.setDirty();
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Debug

	/**
	 * {@code /bs boss field roll}: tries each rule (or just {@code only}) near {@code player}, ignoring the run phase,
	 * the daily roll, cooldowns and the spawn-distance rule (but not "killed this run" or "already out").
	 *
	 * @return the id that spawned, or null
	 */
	public static @Nullable Identifier forceRoll(ServerPlayer player, @Nullable Identifier only) {
		MinecraftServer server = player.level().getServer();
		FieldBossData data = FieldBossData.get(server);
		dropStale(server, data);
		for (Map.Entry<Identifier, FieldBossRule> entry : RULES.entrySet()) {
			Identifier id = entry.getKey();
			if ((only != null && !only.equals(id)) || data.killed.contains(id) || data.active.containsKey(id)) {
				continue;
			}
			if (trySpawn(server, data, id, entry.getValue(), player)) {
				return id;
			}
		}
		return null;
	}
}
