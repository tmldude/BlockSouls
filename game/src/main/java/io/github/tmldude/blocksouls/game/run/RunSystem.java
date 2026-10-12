package io.github.tmldude.blocksouls.game.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/**
 * Owned by the "game-loop" session (docs/sessions/game-loop.md): run lifecycle, day/night, waves, wave drops,
 * starter kit, sleep and dimension rules, difficulty, endless mode. Installs the {@code Runs} provider.
 */
public final class RunSystem {
	/** Written by {@code /bs run new}; the Docker entrypoint wipes the world when it sees it. */
	public static final String NEW_RUN_MARKER = "blocksouls-new-run";

	private static RunConfig config = new RunConfig();

	private RunSystem() {
	}

	public static RunConfig config() {
		return config;
	}

	public static void init() {
		config = Config.load(RunConfig.FILE, RunConfig.class, RunConfig::new);
		if (RunConfig.migrate(config)) {
			Config.save(RunConfig.FILE, config);
		}
		Runs.setProvider(server -> RunData.get(server).state());
		Networking.registerClientbound(RunSyncPayload.TYPE, RunSyncPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(RunSystem::onServerStarted);
		ServerTickEvents.END_SERVER_TICK.register(RunSystem::tick);
		ServerPlayerEvents.JOIN.register(RunSystem::onJoin);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				WaveManager.onDeath(entity, source, RunData.get(level.getServer()));
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (level == level.getServer().overworld()) {
				WaveManager.onEntityLoad(entity, level, RunData.get(level.getServer()));
			}
		});
		BossEvents.KILLED.register((level, bossId, boss, players) -> onBossKilled(level.getServer(), bossId.toString()));
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
			if (player.level() instanceof ServerLevel level && !canSleep(RunData.get(level.getServer()).phase)) {
				return Player.BedSleepingProblem.NOT_SAFE;
			}
			return null;
		});
		RunCommands.register();
	}

	/** No sleeping while a wave runs or during a break; fine once the night's waves are over. */
	public static boolean canSleep(RunPhase phase) {
		return phase != RunPhase.WAVE && phase != RunPhase.WAVE_BREAK;
	}

	public static RunState state(MinecraftServer server) {
		return RunData.get(server).state();
	}

	// ---------------------------------------------------------------- lifecycle

	/** Starts a fresh run (resets any previous one). Online players get the full start kit. */
	public static void startRun(MinecraftServer server, @Nullable String difficultyId) {
		RunData data = RunData.get(server);
		if (data.isActiveRun()) {
			WaveManager.discardAll(server, data);
		}
		data.reset();
		data.difficulty = difficulty(difficultyId);
		data.runSeed = server.overworld().getRandom().nextLong();
		for (WaveMobSpec spec : WaveMobs.all()) {
			String id = spec.id().toString();
			data.rosterOffsets.put(id, RosterShuffle.offset(id, data.runSeed, Math.max(0, config.rosterShuffle)));
		}
		data.startGameTime = server.overworld().getGameTime();
		data.phase = RunPhase.DAY;
		// A run started at night begins its first night right away.
		data.wasNight = false;
		data.setDirty();
		applyClockRate(server, data);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				player.setGameMode(GameType.SURVIVAL);
			}
			StarterKit.giveFullKit(player, data);
		}
		BlockSoulsGame.LOGGER.info("Run started ({})", data.difficulty.id());
		RunEvents.RUN_STARTED.invoker().onChange(server, data.state());
		RunSync.markDirty();
	}

	/**
	 * Ends the active run: phase becomes {@code OVER}, waves stop, {@code RunEvents.RUN_OVER} fires and every player
	 * sees the run summary. Called by the player system when everyone is down at once. No-op if no run is active.
	 */
	public static void endRun(MinecraftServer server) {
		RunData data = RunData.get(server);
		if (!data.isActiveRun()) {
			return;
		}
		boolean midWave = data.phase == RunPhase.WAVE;
		WaveManager.discardAll(server, data);
		data.phase = RunPhase.OVER;
		data.endGameTime = server.overworld().getGameTime();
		data.setDirty();
		int survived = RunSummary.wavesSurvived(data, midWave);
		RunEvents.RUN_OVER.invoker().onChange(server, data.state());
		RunSummary.show(server, data, survived);
		RunSummary.appendLog(server, data, survived);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setGameMode(GameType.SPECTATOR);
		}
		BlockSoulsGame.LOGGER.info("Run over after wave {} (night {})", data.wave, data.night);
		RunSync.markDirty();
	}

	/** Switches to endless mode (once). */
	public static void startEndless(MinecraftServer server) {
		RunData data = RunData.get(server);
		if (!data.isActiveRun() || data.endless) {
			return;
		}
		data.endless = true;
		// Tonight's budget grows too if waves are still going.
		data.nightBudget += config.endlessExtraWavesPerNight;
		data.setDirty();
		RunEvents.ENDLESS_STARTED.invoker().onChange(server, data.state());
		RunAnnouncer.endless(server);
		RunSync.markDirty();
	}

	private static void onBossKilled(MinecraftServer server, String bossId) {
		RunData data = RunData.get(server);
		if (!data.isActiveRun()) {
			return;
		}
		data.bossesKilled.add(bossId);
		data.setDirty();
		if (bossId.equals(CommonIds.FINAL_BOSS.toString())) {
			startEndless(server);
		}
	}

	/** The difficulty preset with this id; unknown or null ids use the config default (or normal). */
	public static Difficulty difficulty(@Nullable String id) {
		String key = id == null || id.isBlank() ? config.defaultDifficulty : id;
		RunConfig.DifficultyPreset preset = config.difficulties.get(key);
		if (preset == null) {
			key = config.defaultDifficulty;
			preset = config.difficulties.get(key);
		}
		if (preset == null) {
			return Difficulty.NORMAL;
		}
		return new Difficulty(key, preset.waveSize, preset.mobHealth, preset.mobDamage);
	}

	// ---------------------------------------------------------------- joining

	private static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		RunData data = RunData.get(server);
		if (data.phase == RunPhase.NOT_STARTED && config.autoStart) {
			startRun(server, consumeNextRunDifficulty());
		} else if (data.isActiveRun()) {
			StarterKit.onJoin(player, data);
		} else if (data.phase == RunPhase.OVER) {
			player.setGameMode(GameType.SPECTATOR);
		}
		RunSync.sendTo(player, server, data);
	}

	// ---------------------------------------------------------------- ticking

	private static void onServerStarted(MinecraftServer server) {
		RunData data = RunData.get(server);
		applyClockRate(server, data);
		RunSync.markDirty();
	}

	private static void tick(MinecraftServer server) {
		RunData data = RunData.get(server);
		if (!data.isActiveRun()) {
			releaseClock(server, data);
			RunSync.tick(server, data);
			return;
		}
		boolean anyoneOnline = server.getPlayerList().getPlayerCount() > 0;
		pauseClockWhenEmpty(server, data, anyoneOnline);
		if (server.overworld().getGameTime() % 100 == 0) {
			applyClockRate(server, data);
		}

		long dayTime = server.overworld().getOverworldClockTime();
		boolean night = WaveMath.isNight(dayTime, config);
		int timeOfDay = WaveMath.timeOfDay(dayTime);
		if (!night && !data.duskCuePlayed && timeOfDay >= config.duskCueTime && timeOfDay < config.duskTime) {
			data.duskCuePlayed = true;
			data.setDirty();
			RunAnnouncer.duskCue(server);
		}
		if (night && !data.wasNight) {
			data.wasNight = true;
			data.setDirty();
			if (anyoneOnline) {
				onDusk(server, data);
			} else {
				// Nights with nobody online don't count.
				data.phase = RunPhase.NIGHT_QUIET;
			}
		} else if (!night && data.wasNight) {
			data.wasNight = false;
			data.setDirty();
			onDawn(server, data);
		}

		WaveManager.tick(server, data);
		RunSync.tick(server, data);
	}

	static void onDusk(MinecraftServer server, RunData data) {
		data.night++;
		data.nightCounted = true;
		data.duskCuePlayed = false;
		data.nightModifiers.clear();
		data.nightModifiers.addAll(NightModifiers.roll(data.night, data.runSeed, config));
		data.nightBudget = WaveMath.wavesForNight(data.night, data.endless, config);
		data.wavesThisNight = 0;
		data.phase = RunPhase.NIGHT_QUIET;
		data.setDirty();
		RunEvents.DUSK.invoker().onNight(server, data.night);

		if (WaveManager.overworldPlayers(server).isEmpty()) {
			// Hiding in another dimension isn't an escape: the counter advances anyway (no mobs, no loot).
			for (int i = 0; i < data.nightBudget; i++) {
				data.wave++;
				data.wavesThisNight++;
				RunEvents.WAVE_STARTED.invoker().onWaveStarted(server, data.wave);
				RunEvents.WAVE_ENDED.invoker().onWaveEnded(server, data.wave, false);
				StarterKit.onWaveEnded(server, data, data.wave);
			}
			data.setDirty();
		} else if (data.nightBudget > 0) {
			WaveManager.startWave(server, data);
		}
		RunSync.markDirty();
	}

	static void onDawn(MinecraftServer server, RunData data) {
		if (data.phase == RunPhase.WAVE) {
			WaveManager.endWave(server, data, false);
		}
		data.phase = RunPhase.DAY;
		data.nightModifiers.clear();
		data.wavesThisNight = 0;
		data.nightBudget = 0;
		data.setDirty();
		if (data.nightCounted) {
			data.nightCounted = false;
			RunEvents.DAWN.invoker().onNight(server, data.night);
		}
		RunSync.markDirty();
	}

	// ---------------------------------------------------------------- the clock

	static Optional<Holder<WorldClock>> overworldClock(MinecraftServer server) {
		return server.overworld().dimensionType().defaultClock();
	}

	/** Day-time ticks per game tick the overworld clock runs at right now. */
	public static float clockRate(MinecraftServer server) {
		return overworldClock(server).map(clock -> server.clockManager().getInstance(clock).rate()).orElse(1.0F);
	}

	/** The run's day length: vanilla's clock has a rate with a fractional accumulator, so the whole cycle speeds up evenly. */
	private static void applyClockRate(MinecraftServer server, RunData data) {
		if (data.phase == RunPhase.NOT_STARTED) {
			return;
		}
		float rate = WaveMath.clockRate(config.dayLengthMinutes);
		overworldClock(server).ifPresent(clock -> {
			if (Math.abs(server.clockManager().getInstance(clock).rate() - rate) > 1.0E-4F) {
				server.clockManager().setRate(clock, rate);
			}
		});
	}

	/** Time only moves while someone is online (dedicated servers keep ticking for a minute after the last player leaves). */
	private static void pauseClockWhenEmpty(MinecraftServer server, RunData data, boolean anyoneOnline) {
		if (!anyoneOnline && !data.clockPausedByRun) {
			overworldClock(server).ifPresent(clock -> {
				if (!server.clockManager().getInstance(clock).isPaused()) {
					server.clockManager().setPaused(clock, true);
					data.clockPausedByRun = true;
					data.setDirty();
				}
			});
		} else if (anyoneOnline) {
			releaseClock(server, data);
		}
	}

	private static void releaseClock(MinecraftServer server, RunData data) {
		if (data.clockPausedByRun) {
			overworldClock(server).ifPresent(clock -> server.clockManager().setPaused(clock, false));
			data.clockPausedByRun = false;
			data.setDirty();
		}
	}

	/** Moves the overworld clock forward to the next {@code timeOfDay}. */
	static void skipTo(MinecraftServer server, int timeOfDay) {
		overworldClock(server).ifPresent(clock -> {
			long total = server.clockManager().getInstance(clock).totalTicks();
			server.clockManager().setTotalTicks(clock, total + WaveMath.ticksUntil(total, timeOfDay));
		});
	}

	// ---------------------------------------------------------------- new run

	static boolean inDocker() {
		return Files.exists(Path.of("/etc/blocksouls.env"));
	}

	/**
	 * {@code /bs run new}: remember the next run's difficulty, drop the marker and stop the server (Docker wipes the world
	 * and restarts). Outside Docker only logs. Returns whether the server is stopping.
	 */
	static boolean requestNewRun(MinecraftServer server, @Nullable String difficultyId) {
		String next = difficultyId != null ? difficulty(difficultyId).id() : RunData.get(server).difficulty.id();
		config.nextRunDifficulty = next;
		Config.save(RunConfig.FILE, config);
		if (!inDocker()) {
			BlockSoulsGame.LOGGER.warn("/bs run new outside Docker: stop the server and delete the world by hand; the next run starts on {}", next);
			return false;
		}
		Path marker = server.getServerDirectory().resolve(NEW_RUN_MARKER);
		try {
			Files.writeString(marker, next, StandardCharsets.UTF_8);
		} catch (IOException e) {
			BlockSoulsGame.LOGGER.error("Failed to write {}", marker, e);
			return false;
		}
		server.halt(false);
		return true;
	}

	/** The difficulty {@code /bs run new} asked for (cleared once used). */
	private static @Nullable String consumeNextRunDifficulty() {
		String next = config.nextRunDifficulty;
		if (next == null || next.isBlank()) {
			return null;
		}
		config.nextRunDifficulty = "";
		Config.save(RunConfig.FILE, config);
		return next;
	}

	static void playTo(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(sound, SoundSource.AMBIENT, player.getX(), player.getY(), player.getZ(), volume, pitch,
				player.getRandom().nextLong()));
	}

	static Holder<SoundEvent> duskSound() {
		return SoundEvents.AMBIENT_CAVE;
	}
}
