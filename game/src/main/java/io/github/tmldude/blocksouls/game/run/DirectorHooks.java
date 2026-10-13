package io.github.tmldude.blocksouls.game.run;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/**
 * The seam between the wave scheduler (this package) and the AI Director ({@code game/run/director}). The
 * scheduler asks the installed {@link Spawner} how many mobs to spawn this tick, from which direction, of which
 * kind, and how long a break lasts; with nothing installed (or the Director off) every answer is the scheduler's
 * own default, so the classic run shape is untouched. The static methods are the Director's read/write view of
 * the run state, which stays package-private here.
 */
public final class DirectorHooks {
	/** The Director's levers on the wave scheduler. Every default returns the scheduler's own behaviour. */
	public interface Spawner {
		/** How many wave mobs may spawn this tick ({@code fallback} = the scheduler's fixed trickle). */
		default int spawnsThisTick(MinecraftServer server, int fallback) {
			return fallback;
		}

		/** The direction (radians; x = cos, z = sin) to spawn around {@code anchor}; NaN = random. */
		default double spawnAngle(ServerLevel level, ServerPlayer anchor, RandomSource random) {
			return Double.NaN;
		}

		/** Mob ids to prefer for the next spawn (flankers, brutes); empty = the normal roster mix. */
		default List<Identifier> preferMobs(RandomSource random) {
			return List.of();
		}

		/** Chance (0–1) a spawn that may be an elite is forced to an unlocked elite (solo: fewer, scarier). */
		default double eliteBias(MinecraftServer server) {
			return 0;
		}

		/** A wave mob was spawned and tracked. */
		default void onSpawned(MinecraftServer server, Mob mob) {
		}

		/** Seconds of break before the next chained wave ({@code fallback} = config). */
		default int breakSeconds(MinecraftServer server, int fallback) {
			return fallback;
		}

		/** Extra loot tier steps for wave drops (the horn's buff). */
		default int tierShift(MinecraftServer server) {
			return 0;
		}
	}

	private static final Spawner NONE = new Spawner() {
	};
	private static Spawner spawner = NONE;

	private DirectorHooks() {
	}

	public static void install(@Nullable Spawner director) {
		spawner = director == null ? NONE : director;
	}

	static Spawner spawner() {
		return spawner;
	}

	/** Safe wrapper: a Director bug must never stop the waves. */
	static int spawnsThisTick(MinecraftServer server, int fallback) {
		try {
			return Math.max(0, spawner.spawnsThisTick(server, fallback));
		} catch (RuntimeException e) {
			BlockSoulsGame.LOGGER.error("Director spawn budget failed", e);
			return fallback;
		}
	}

	static double spawnAngle(ServerLevel level, ServerPlayer anchor, RandomSource random) {
		try {
			return spawner.spawnAngle(level, anchor, random);
		} catch (RuntimeException e) {
			BlockSoulsGame.LOGGER.error("Director spawn angle failed", e);
			return Double.NaN;
		}
	}

	/**
	 * The Director's pick for this spawn, or null for the normal roster pick: an unlocked elite (the elite bias),
	 * else one of its preferred mobs (composition) if the roster has it unlocked.
	 */
	static @Nullable WaveMobSpec preferredSpec(MinecraftServer server, RunData data, FloorRoster roster, RandomSource random,
			boolean allowElite) {
		List<Identifier> ids;
		double eliteBias;
		try {
			ids = spawner.preferMobs(random);
			eliteBias = spawner.eliteBias(server);
		} catch (RuntimeException e) {
			BlockSoulsGame.LOGGER.error("Director composition failed", e);
			return null;
		}
		List<WaveMobSpec> eligible = roster.eligible(data.wave);
		if (allowElite && eliteBias > 0 && random.nextDouble() < eliteBias) {
			List<WaveMobSpec> elites = eligible.stream().filter(spec -> spec.elite() && spec.weight() > 0).toList();
			if (!elites.isEmpty()) {
				return elites.get(random.nextInt(elites.size()));
			}
		}
		if (ids.isEmpty()) {
			return null;
		}
		List<WaveMobSpec> unlocked = eligible.stream()
				.filter(spec -> ids.contains(spec.id()) && (allowElite || !spec.elite()) && spec.weight() > 0)
				.toList();
		return unlocked.isEmpty() ? null : unlocked.get(random.nextInt(unlocked.size()));
	}

	static void onSpawned(MinecraftServer server, Mob mob) {
		try {
			spawner.onSpawned(server, mob);
		} catch (RuntimeException e) {
			BlockSoulsGame.LOGGER.error("Director spawn listener failed", e);
		}
	}

	static int breakSeconds(MinecraftServer server, int fallback) {
		try {
			return Math.max(0, spawner.breakSeconds(server, fallback));
		} catch (RuntimeException e) {
			BlockSoulsGame.LOGGER.error("Director break length failed", e);
			return fallback;
		}
	}

	static int tierShift(MinecraftServer server) {
		try {
			return Math.max(0, spawner.tierShift(server));
		} catch (RuntimeException e) {
			BlockSoulsGame.LOGGER.error("Director tier shift failed", e);
			return 0;
		}
	}

	// ---------------------------------------------------------------- the Director's view of the run

	public static boolean expedition() {
		return ActMachine.expedition();
	}

	public static boolean activeRun(MinecraftServer server) {
		return RunData.get(server).isActiveRun();
	}

	public static RunPhase phase(MinecraftServer server) {
		return RunData.get(server).phase;
	}

	/** The threat counter (the wave number). */
	public static int threat(MinecraftServer server) {
		return RunData.get(server).wave;
	}

	/** Raises the threat counter without running a wave (the overclock horn). */
	public static void addThreat(MinecraftServer server, int amount) {
		RunData data = RunData.get(server);
		if (amount <= 0 || !data.isActiveRun()) {
			return;
		}
		data.wave += amount;
		data.setDirty();
		RunSync.markDirty();
	}

	public static int actOrdinal(MinecraftServer server) {
		return RunData.get(server).actOrdinal;
	}

	/** Whether a portal charge (the siege) is running. */
	public static boolean charging(MinecraftServer server) {
		return RunData.get(server).chargingAct > 0;
	}

	/** Seconds left on the running charge, or 0. */
	public static int chargeSecondsLeft(MinecraftServer server) {
		RunData data = RunData.get(server);
		if (data.chargingAct <= 0) {
			return 0;
		}
		return (int) Math.max(0, (data.chargeEndsAt - server.overworld().getGameTime() + 19) / 20);
	}

	/** Starts the act's portal charge (the siege). Null on success, else why not. */
	public static @Nullable String startCharge(MinecraftServer server) {
		return ActMachine.startCharge(server, RunData.get(server));
	}

	/** The level the party's act is in. */
	public static ServerLevel level(MinecraftServer server) {
		return ActMachine.currentLevel(server);
	}

	/** Players in the act's level, alive and not spectating. */
	public static List<ServerPlayer> activePlayers(MinecraftServer server) {
		return WaveManager.activePlayers(server);
	}

	/** The current wave's tracked mobs (a copy). */
	public static Set<UUID> waveMobs(MinecraftServer server) {
		return Set.copyOf(RunData.get(server).waveMobs);
	}

	/** Mobs still alive or still to spawn in the current wave. */
	public static int mobsLeft(MinecraftServer server) {
		return WaveManager.mobsLeft(RunData.get(server));
	}

	/** Ticks left on a wave break, or 0 outside one. */
	public static long breakTicksLeft(MinecraftServer server) {
		RunData data = RunData.get(server);
		return data.phase == RunPhase.WAVE_BREAK ? Math.max(0, data.breakEndsAt - server.overworld().getGameTime()) : 0;
	}

	/** Shortens the running wave break to at most {@code seconds} (intermission tightening). */
	public static boolean tightenBreak(MinecraftServer server, int seconds) {
		RunData data = RunData.get(server);
		if (data.phase != RunPhase.WAVE_BREAK) {
			return false;
		}
		long target = server.overworld().getGameTime() + seconds * 20L;
		if (target >= data.breakEndsAt) {
			return false;
		}
		data.breakEndsAt = target;
		data.setDirty();
		RunSync.markDirty();
		return true;
	}

	/** Tonight's / this siege's modifiers (ids). */
	public static Set<String> modifiers(MinecraftServer server) {
		return Set.copyOf(RunData.get(server).nightModifiers);
	}

	/**
	 * Raises the running modifier set one tier (the cursed chest's price): none → one modifier from the pool,
	 * a modifier → Blood Moon. Returns the modifier added, or null when already at Blood Moon.
	 */
	public static @Nullable String bumpModifier(MinecraftServer server, RandomSource random) {
		RunData data = RunData.get(server);
		if (data.nightModifiers.contains(RunIds.BLOOD_MOON_ID)) {
			return null;
		}
		List<String> pool = RunSystem.config().modifierPool.stream().filter(id -> !data.nightModifiers.contains(id)).toList();
		String added = data.nightModifiers.isEmpty() && !pool.isEmpty() ? pool.get(random.nextInt(pool.size())) : RunIds.BLOOD_MOON_ID;
		if (added.equals(RunIds.BLOOD_MOON_ID)) {
			data.nightModifiers.clear();
		}
		data.nightModifiers.add(added);
		data.setDirty();
		RunSync.markDirty();
		return added;
	}

	/** The context a wave-mob factory gets right now. */
	public static WaveContext waveContext(MinecraftServer server, boolean breaksBlocks) {
		RunData data = RunData.get(server);
		return new WaveContext(data.wave, data.difficulty, WaveManager.modifierIds(data), breaksBlocks);
	}

	/** A spawn spot 24–40 blocks from {@code anchor}, honouring the Director's spawn angle. */
	public static @Nullable BlockPos findSpawnPos(ServerLevel level, ServerPlayer anchor, List<ServerPlayer> players, RandomSource random) {
		return WaveManager.findSpawnPos(level, anchor, players, random);
	}

	/**
	 * Spawns one mob outside any wave (a day mob, a scout, an ambush, an event's guard) at {@code pos}: the roster
	 * spec {@code preferred} if unlocked, else a weighted roster pick; scaled like a wave mob and tracked as a
	 * hunter, so it hunts the nearest player, drops wave loot and is cleaned up with the run.
	 */
	public static @Nullable Mob spawnHunter(MinecraftServer server, ServerLevel level, BlockPos pos, @Nullable Identifier preferred, boolean elite,
			RandomSource random) {
		return WaveManager.spawnHunter(server, RunData.get(server), level, pos, preferred, elite, random);
	}

	/** Hunters (out-of-wave mobs) still alive. */
	public static int hunters(MinecraftServer server) {
		return RunData.get(server).hunters.size();
	}

	/** Every wave mob and hunter is removed (gametests). */
	public static void discardAll(MinecraftServer server) {
		WaveManager.discardAll(server, RunData.get(server));
	}
}
