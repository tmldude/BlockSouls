package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.event.CombatEvents;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.common.mob.Affixes;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;

/**
 * The AI Director (PLAN2 §3b): reads the party's stress once a second and works the wave scheduler's levers
 * (spawn rate and bursts, composition, spawn direction from the influence map, lulls and spikes) through
 * {@link DirectorHooks}. It owns the pacing budget — no stretch longer than {@code pacing.ceilingSeconds}
 * without a decision or a threat — and logs every choice to {@code /bs director log}. The day loop, siege
 * objectives and economy objects hang off it ({@link DayLoop}, {@link Objectives}, {@link Economy}, {@link Shop}).
 *
 * <p>Runs only in the expedition run shape unless {@code enabledInClassic}; otherwise every hook answers with
 * the scheduler's own default and the classic run is untouched.
 */
public final class Director {
	private static DirectorConfig config = new DirectorConfig();
	static final DirectorLog LOG = new DirectorLog(200);
	static final Pacer PACER = new Pacer();
	static InfluenceMap influence = new InfluenceMap(8);
	static PacingBudget budget = new PacingBudget(0);
	static final StressWindow WINDOW = new StressWindow(30);
	static double stress;
	static StressModel.@Nullable Reading reading;
	static Composition composition = Composition.MIXED;
	private static double anchorX;
	private static double anchorZ;
	private static long anchorSince;
	static boolean camping;
	private static long lullStartedAt = -1;
	private static long lastFlankLog = -100;
	/** The Director clock's second the run started at; log times read from here. */
	private static long runStartedAt;
	/** A siege (portal charge) the Director saw start and hasn't seen end. */
	static boolean siegeActive;

	private Director() {
	}

	public static DirectorConfig config() {
		return config;
	}

	/** Swaps the config (gametests). */
	public static void setConfig(DirectorConfig newConfig) {
		config = newConfig;
		applyConfig();
	}

	public static void init() {
		config = Config.load(DirectorConfig.FILE, DirectorConfig.class, DirectorConfig::new);
		if (DirectorConfig.migrate(config)) {
			Config.save(DirectorConfig.FILE, config);
		}
		applyConfig();
		ConfigEvents.RELOADED.register((name, reloaded) -> {
			if (reloaded != config) {
				return;
			}
			if (DirectorConfig.migrate(config)) {
				Config.save(DirectorConfig.FILE, config);
			}
			applyConfig();
		});

		DirectorHooks.install(new Levers());
		ServerTickEvents.END_SERVER_TICK.register(Director::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0 && active(player.level().getServer())) {
				WINDOW.addDamage(taken);
				beat(player.level().getServer(), "player hurt");
			}
		});
		PlayerRunEvents.DOWNED.register(player -> {
			if (active(player.level().getServer())) {
				WINDOW.addDown();
				log(player.level().getServer(), DirectorLog.Kind.STRESS, player.getScoreboardName() + " went down");
			}
		});
		CombatEvents.HIT.register((shooter, target, damage, precision, killed) -> {
			MinecraftServer server = shooter.level().getServer();
			if (!active(server)) {
				return;
			}
			WINDOW.addDealt(damage);
			if (killed) {
				addKillPressure(server, target);
				beat(server, "kill");
				DayLoop.onKill(server, target);
			}
		});
		RunEvents.RUN_STARTED.register((server, state) -> onRunStarted(server));
		RunEvents.RUN_OVER.register((server, state) -> onRunOver(server));
		RunEvents.WAVE_STARTED.register((server, wave) -> {
			if (active(server)) {
				beat(server, "wave " + wave);
			}
		});

		DayLoop.init();
		FloorHazards.init();
		Objectives.init();
		Economy.init();
		Shop.init();
		DirectorSync.init();
		DirectorCommands.register();
	}

	private static void applyConfig() {
		LOG.setCapacity(Math.max(10, config.logSize));
		if (influence.sectors() != Math.max(2, config.influence.sectors)) {
			influence = new InfluenceMap(Math.max(2, config.influence.sectors));
		}
		WINDOW.resize(config.stress.windowSeconds);
	}

	/** Whether the Director drives this run: on, a run is active, and expedition (or classic opted in). */
	public static boolean active(MinecraftServer server) {
		return config.enabled && (DirectorHooks.expedition() || config.enabledInClassic) && DirectorHooks.activeRun(server);
	}

	/** The Director's clock: seconds of overworld game time. */
	static long now(MinecraftServer server) {
		return server.overworld().getGameTime() / 20;
	}

	static void log(MinecraftServer server, DirectorLog.Kind kind, String text) {
		LOG.add(Math.max(0, now(server) - runStartedAt), kind, text);
		if (kind == DirectorLog.Kind.VIOLATION) {
			BlockSoulsGame.LOGGER.info("[director] {}", text);
		}
	}

	/** A decision or threat happened: the pacing budget restarts. */
	static void beat(MinecraftServer server, String reason) {
		budget.beat(now(server), reason);
	}

	public static double stress() {
		return stress;
	}

	public static Pacer.Mood mood() {
		return PACER.mood();
	}

	public static DirectorLog log() {
		return LOG;
	}

	public static InfluenceMap influence() {
		return influence;
	}

	public static Composition composition() {
		return composition;
	}

	/** Resets every live reading (run start, gametests). */
	public static void resetLive(MinecraftServer server) {
		long now = now(server);
		PACER.reset();
		influence.clear();
		WINDOW.clear();
		budget = new PacingBudget(now);
		stress = 0;
		reading = null;
		composition = Composition.MIXED;
		camping = false;
		anchorSince = now;
		lullStartedAt = -1;
		siegeActive = false;
		FloorHazards.reset();
	}

	private static void onRunStarted(MinecraftServer server) {
		resetLive(server);
		runStartedAt = now(server);
		DayLoop.reset();
		LOG.clear();
		DirectorData.get(server).reset(server.overworld().getRandom().nextLong());
		Economy.onRunStarted(server);
		if (active(server)) {
			log(server, DirectorLog.Kind.DAY, "Run started; Director on (" + (DirectorHooks.expedition() ? "expedition" : "classic") + ")");
		}
	}

	private static void onRunOver(MinecraftServer server) {
		Objectives.cancel(server, "run over");
		Economy.onRunOver(server);
		DirectorData data = DirectorData.get(server);
		data.dayTicksLeft = 0;
		data.setDirty();
		siegeActive = false;
		DirectorSync.markDirty();
	}

	// ---------------------------------------------------------------- the loop

	private static void tick(MinecraftServer server) {
		if (!active(server)) {
			DirectorSync.flush(server);
			return;
		}
		if (server.overworld().getGameTime() % 20 == 0) {
			second(server);
		}
	}

	/** Once a second while active. Public for gametests that step the Director by hand. */
	public static void second(MinecraftServer server) {
		long now = now(server);
		List<ServerPlayer> players = DirectorHooks.activePlayers(server);
		int partySize = Math.max(1, players.size());

		sampleStress(server, players, partySize);
		trackInfluence(players);
		trackCamping(server, players, now);

		RunPhase phase = DirectorHooks.phase(server);
		if (phase == RunPhase.WAVE) {
			Pacer.Change change = PACER.update(now, stress, partySize, config.levers, config.pacing);
			onMoodChange(server, change, now);
			holdLullCeiling(server, now);
		} else if (PACER.mood() == Pacer.Mood.LULL) {
			PACER.endLull(now, 0);
			lullStartedAt = -1;
		}
		if (threatNearby(server, players)) {
			beat(server, "threat");
		}
		if (phase == RunPhase.WAVE_BREAK) {
			tightenBreak(server, now);
		}

		DayLoop.second(server, now);
		Objectives.second(server, now);
		Economy.second(server, now);
		FloorHazards.second(server, now, players);
		if (siegeActive && !DirectorHooks.charging(server)) {
			// The charge ended without completing (the party left the act): everything riding on it is lost.
			Economy.onSiegeEnded(server, false);
			Objectives.cancel(server, "siege abandoned");
			siegeActive = false;
		}

		enforceBudget(server, now, phase, players, partySize);
		if (now % 10 == 0 && reading != null) {
			log(server, DirectorLog.Kind.STRESS, String.format("stress %.2f (dmg %.2f downs %.2f ammo %.2f dps %.2f power %.2f) mood %s %s",
					stress, reading.damage(), reading.downs(), reading.ammo(), reading.dps(), reading.power(), PACER.mood(), composition));
		}
		WINDOW.roll();
		DirectorSync.markDirty();
		DirectorSync.flush(server);
	}

	private static void sampleStress(MinecraftServer server, List<ServerPlayer> players, int partySize) {
		int ammo = 0;
		boolean ammoKnown = false;
		int boons = 0;
		Item primary = item(CommonIds.PRIMARY_AMMO);
		Item special = item(CommonIds.SPECIAL_AMMO);
		for (ServerPlayer player : players) {
			if (primary != null || special != null) {
				ammoKnown = true;
				var inventory = player.getInventory();
				for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
					ItemStack stack = inventory.getItem(slot);
					if ((primary != null && stack.is(primary)) || (special != null && stack.is(special))) {
						ammo += stack.getCount();
					}
				}
			}
			boons += PlayerBoons.owned(player).values().stream().mapToInt(Integer::intValue).sum();
		}
		if (!ammoKnown) {
			// No guns module: ammo reads as comfortable.
			ammo = config.stress.ammoComfortPerPlayer * partySize;
		}
		StressModel.Inputs inputs = new StressModel.Inputs(partySize, WINDOW.damage(), WINDOW.downs(), ammo, WINDOW.dpsRecent(), WINDOW.dpsEarlier(),
				boons, DirectorHooks.threat(server));
		reading = StressModel.score(inputs, config.stress);
		stress = StressModel.smooth(stress, reading.stress(), config.stress.smoothing);
	}

	private static @Nullable Item item(Identifier id) {
		return BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).orElse(null);
	}

	/** Player pressure: where the party is looking (and so shooting) heats those sectors. */
	private static void trackInfluence(List<ServerPlayer> players) {
		influence.decay(config.influence.decayPerSecond);
		for (ServerPlayer player : players) {
			influence.add(InfluenceMap.yawToAngle(player.getYRot()), config.influence.lookPressure, config.influence.spread);
		}
	}

	private static void addKillPressure(MinecraftServer server, Entity target) {
		List<ServerPlayer> players = DirectorHooks.activePlayers(server);
		if (players.isEmpty()) {
			return;
		}
		double[] centre = centroid(players);
		influence.addDirection(target.getX() - centre[0], target.getZ() - centre[1], config.influence.killPressure, config.influence.spread);
	}

	static double[] centroid(List<ServerPlayer> players) {
		double x = 0;
		double z = 0;
		for (ServerPlayer player : players) {
			x += player.getX();
			z += player.getZ();
		}
		int n = Math.max(1, players.size());
		return new double[] {x / n, z / n};
	}

	private static void trackCamping(MinecraftServer server, List<ServerPlayer> players, long now) {
		if (players.isEmpty()) {
			return;
		}
		double[] centre = centroid(players);
		double dx = centre[0] - anchorX;
		double dz = centre[1] - anchorZ;
		if (dx * dx + dz * dz > config.levers.campingRadius * config.levers.campingRadius) {
			anchorX = centre[0];
			anchorZ = centre[1];
			anchorSince = now;
		}
		camping = now - anchorSince >= config.levers.campingSeconds;
		Composition next = Composition.choose(camping, reading == null ? 0 : reading.damage());
		if (next != composition) {
			log(server, DirectorLog.Kind.COMPOSITION, composition + " -> " + next + (camping ? " (party holding a spot " + (now - anchorSince) + "s)" : ""));
			composition = next;
			beat(server, "composition " + next);
		}
	}

	private static void onMoodChange(MinecraftServer server, Pacer.@Nullable Change change, long now) {
		if (change == null) {
			return;
		}
		if (change.to() == Pacer.Mood.LULL) {
			lullStartedAt = now;
		} else if (change.from() == Pacer.Mood.LULL) {
			lullStartedAt = -1;
		}
		String detail = change.to() == Pacer.Mood.LULL ? " for " + change.seconds() + "s" : "";
		log(server, DirectorLog.Kind.MOOD, String.format("%s -> %s%s (stress %.2f)", change.from(), change.to(), detail, change.stress()));
		beat(server, "mood " + change.to());
	}

	/** Whatever asked for it, a lull never outlasts the lull ceiling. */
	private static void holdLullCeiling(MinecraftServer server, long now) {
		if (PACER.mood() != Pacer.Mood.LULL || lullStartedAt < 0) {
			return;
		}
		if (now - lullStartedAt > config.pacing.lullCeilingSeconds) {
			PACER.endLull(now, config.levers.moodCooldownSeconds);
			log(server, DirectorLog.Kind.VIOLATION, "Lull ran " + (now - lullStartedAt) + "s > ceiling " + config.pacing.lullCeilingSeconds + "s: ended");
			lullStartedAt = -1;
			beat(server, "lull ceiling");
		}
	}

	/** Starts a lull now (scripted stress in gametests, {@code /bs director lull}). */
	public static void forceLull(MinecraftServer server, int seconds) {
		long now = now(server);
		onMoodChange(server, PACER.lull(now, stress, seconds, config.pacing.lullCeilingSeconds), now);
	}

	/** Sets the smoothed stress directly (scripted stress in gametests). */
	public static void setStress(double value) {
		stress = StressModel.clamp(value);
	}

	private static boolean threatNearby(MinecraftServer server, List<ServerPlayer> players) {
		if (players.isEmpty()) {
			return false;
		}
		ServerLevel level = DirectorHooks.level(server);
		double radius = config.pacing.threatRadius * config.pacing.threatRadius;
		for (UUID id : DirectorHooks.waveMobs(server)) {
			Entity entity = level.getEntity(id);
			if (entity != null && entity.isAlive()) {
				for (ServerPlayer player : players) {
					if (player.distanceToSqr(entity) <= radius) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Intermission tightening: a running break is cut so the budget isn't blown. */
	private static void tightenBreak(MinecraftServer server, long now) {
		int left = (int) ((DirectorHooks.breakTicksLeft(server) + 19) / 20);
		int allowed = PacingBudget.tightenBreak(budget.quietSeconds(now), left, config.pacing.ceilingSeconds, config.pacing.tightBreakSeconds);
		if (allowed < left && DirectorHooks.tightenBreak(server, allowed)) {
			log(server, DirectorLog.Kind.PACING, "Break tightened " + left + "s -> " + allowed + "s (quiet " + budget.quietSeconds(now) + "s)");
		}
	}

	private static void enforceBudget(MinecraftServer server, long now, RunPhase phase, List<ServerPlayer> players, int partySize) {
		if (!budget.spent(now, config.pacing.ceilingSeconds)) {
			return;
		}
		String quiet = budget.quietSeconds(now) + "s without a decision or threat (last: " + budget.lastReason() + ")";
		String action;
		if (phase == RunPhase.WAVE) {
			if (PACER.mood() == Pacer.Mood.LULL) {
				PACER.endLull(now, 0);
				lullStartedAt = -1;
			}
			onMoodChange(server, PACER.spike(now, stress, partySize, config.levers), now);
			action = "spike";
		} else if (phase == RunPhase.WAVE_BREAK) {
			DirectorHooks.tightenBreak(server, config.pacing.tightBreakSeconds);
			action = "break cut to " + config.pacing.tightBreakSeconds + "s";
		} else if (!players.isEmpty()) {
			int sent = scoutPack(server, players);
			action = "scout pack of " + sent;
		} else {
			action = "nobody to pressure";
		}
		log(server, DirectorLog.Kind.VIOLATION, quiet + " -> " + action);
		beat(server, "budget: " + action);
	}

	/** A small pack sent at the party during a long quiet day (quiet-day pressure). */
	static int scoutPack(MinecraftServer server, List<ServerPlayer> players) {
		int size = (int) Math.max(1, Math.round(config.pacing.scoutPack * PartyScaling.directorBurst(players.size())));
		return spawnPack(server, players, size, null, PartyScaling.directorEliteShare(players.size()));
	}

	/**
	 * Spawns {@code size} hunters around a random player (from the Director's aimed direction); each is an
	 * elite with {@code eliteChance}. Returns how many spawned.
	 */
	static int spawnPack(MinecraftServer server, List<ServerPlayer> players, int size, @Nullable BlockPos near, double eliteChance) {
		if (players.isEmpty()) {
			return 0;
		}
		ServerLevel level = DirectorHooks.level(server);
		RandomSource random = level.getRandom();
		ServerPlayer anchor = players.get(random.nextInt(players.size()));
		List<Mob> spawned = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			BlockPos pos = near != null ? Places.surfaceNear(level, near, 4, random) : DirectorHooks.findSpawnPos(level, anchor, players, random);
			if (pos == null) {
				continue;
			}
			boolean elite = random.nextDouble() < eliteChance;
			Mob mob = DirectorHooks.spawnHunter(server, level, pos, preferredFor(random), elite, random);
			if (mob != null) {
				affixAndRole(server, level, mob, elite, random);
				spawned.add(mob);
			}
		}
		return spawned.size();
	}

	/**
	 * v2 mobs hook: a Director elite rolls an affix for its floor (unless its factory already gave it one), and in a
	 * flanking composition every hunter is tagged to circle to the party's blind side.
	 */
	private static void affixAndRole(MinecraftServer server, ServerLevel level, Mob mob, boolean elite, RandomSource random) {
		if (elite && Affixes.of(mob).isEmpty()) {
			PlacedFloor floor = Floors.at(server, level.dimension().identifier(), mob.blockPosition()).orElse(null);
			// Floor 0 = Classic (affixes only with mobs.json affixesInClassic).
			int number = floor != null ? floor.floorNumber() : Acts.all(server).size() <= 1 ? 0 : Math.max(1, Acts.current(server).ordinal());
			Affixes.roll(random, number, floor != null ? floor.tags() : java.util.Set.of()).ifPresent(affix -> Affixes.apply(mob, affix));
		}
		if (composition == Composition.FLANKERS) {
			mob.addTag(WaveMobs.FLANKER_TAG);
		}
	}

	private static @Nullable Identifier preferredFor(RandomSource random) {
		List<Identifier> ids = preferMobs(random);
		return ids.isEmpty() ? null : ids.get(random.nextInt(ids.size()));
	}

	static List<Identifier> preferMobs(RandomSource random) {
		List<String> pool = switch (composition) {
			case FLANKERS -> config.levers.flankerMobs;
			case BRUTES -> config.levers.bruteMobs;
			case MIXED -> List.of();
		};
		if (pool.isEmpty() || random.nextDouble() >= composition.share(config.levers)) {
			return List.of();
		}
		return pool.stream().map(Identifier::tryParse).filter(java.util.Objects::nonNull).toList();
	}

	// ---------------------------------------------------------------- the scheduler's levers

	/** What the Director answers the wave scheduler. */
	private static final class Levers implements DirectorHooks.Spawner {
		@Override
		public int spawnsThisTick(MinecraftServer server, int fallback) {
			if (!active(server)) {
				return fallback;
			}
			PACER.tick();
			int burst = (int) Math.ceil(Pacer.burstSize(Math.max(1, DirectorHooks.activePlayers(server).size()), config.levers)
					* Math.max(1, config.levers.spikeBurstMultiplier));
			return (int) Math.min(burst, Math.floor(PACER.tokens()));
		}

		@Override
		public double spawnAngle(ServerLevel level, ServerPlayer anchor, RandomSource random) {
			MinecraftServer server = level.getServer();
			if (!active(server) || !influence.hasPressure()) {
				return Double.NaN;
			}
			if (composition == Composition.FLANKERS && random.nextDouble() < config.levers.flankShare) {
				int sector = influence.flank();
				long now = now(server);
				if (now - lastFlankLog >= 5) {
					lastFlankLog = now;
					log(server, DirectorLog.Kind.FLANK, "Flanking: spawning in sector " + sector + " (party faces " + hottest() + ")");
				}
				return influence.centreOf(sector);
			}
			return influence.centreOf(influence.pick(random.nextDouble(), config.influence.sharpness));
		}

		@Override
		public List<Identifier> preferMobs(RandomSource random) {
			return Director.preferMobs(random);
		}

		@Override
		public double eliteBias(MinecraftServer server) {
			if (!active(server)) {
				return 0;
			}
			// The party curve's share beyond the roster's own elites: high solo, gone at a full party.
			return Math.max(0, PartyScaling.directorEliteShare(DirectorHooks.activePlayers(server).size()) - 0.1);
		}

		@Override
		public void onSpawned(MinecraftServer server, Mob mob) {
			if (active(server)) {
				PACER.take(1);
			}
		}

		@Override
		public int breakSeconds(MinecraftServer server, int fallback) {
			if (!active(server)) {
				return fallback;
			}
			long now = now(server);
			int allowed = PacingBudget.tightenBreak(budget.quietSeconds(now), fallback, config.pacing.ceilingSeconds, config.pacing.tightBreakSeconds);
			if (allowed < fallback) {
				log(server, DirectorLog.Kind.PACING, "Intermission " + fallback + "s -> " + allowed + "s");
			}
			return allowed;
		}

		@Override
		public int tierShift(MinecraftServer server) {
			return active(server) && DirectorHooks.charging(server) ? DirectorData.get(server).hornTierShift : 0;
		}
	}

	private static int hottest() {
		int best = 0;
		for (int i = 1; i < influence.sectors(); i++) {
			if (influence.pressure(i) > influence.pressure(best)) {
				best = i;
			}
		}
		return best;
	}
}
