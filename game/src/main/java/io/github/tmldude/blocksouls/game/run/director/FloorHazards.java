package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.boss.BossSpawns;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;

/**
 * Floor "danger scaling" (floor 5's Blighted Surface first): on a floor whose tags name an entry in
 * {@code floor_hazards.json}, the day's mobs come denser, field bosses walk the island by day, and zone problems
 * (a drifting storm that hurts, a spawn surge, the camp shop going dark) fire on a timer. Driven once a second by the
 * Director, so it only runs when the Director does; live-only state (a restart mid-day starts the timers over).
 */
public final class FloorHazards {
	static final String SHOP_CLOSED = "The shopkeeper has fled the blight. Back soon.";

	private static FloorHazardsConfig config = new FloorHazardsConfig();

	private static int act = -1;
	private static long nextProblemAt = -1;
	private static long nextBossAt = -1;
	private static long shopClosedUntil = -1;
	private static HazardMath.@Nullable Storm storm;
	private static long stormUntil = -1;
	private static FloorHazardsConfig.@Nullable Problem stormSpec;
	/** Roaming field bosses alive, by boss id. */
	private static final Map<String, UUID> ROAMERS = new LinkedHashMap<>();

	private FloorHazards() {
	}

	static void init() {
		config = Config.load(FloorHazardsConfig.FILE, FloorHazardsConfig.class, FloorHazardsConfig::new);
		ConfigEvents.RELOADED.register((name, reloaded) -> {
			if (reloaded instanceof FloorHazardsConfig loaded) {
				config = loaded;
			}
		});
		FloorHazardCommands.register();
	}

	public static FloorHazardsConfig config() {
		return config;
	}

	/** Swaps the config (gametests). */
	public static void setConfig(FloorHazardsConfig newConfig) {
		config = newConfig;
	}

	static void reset() {
		act = -1;
		nextProblemAt = -1;
		nextBossAt = -1;
		shopClosedUntil = -1;
		storm = null;
		stormSpec = null;
		stormUntil = -1;
		ROAMERS.clear();
	}

	// ---------------------------------------------------------------- lookups

	/** The current act's floor, if it has one. */
	static Optional<PlacedFloor> floor(MinecraftServer server) {
		return Floors.forAct(server, DirectorHooks.actOrdinal(server));
	}

	/** The hazards for a floor: the first configured tag it carries. */
	static FloorHazardsConfig.@Nullable TagHazards hazardsFor(@Nullable PlacedFloor floor) {
		if (floor == null || !config.enabled) {
			return null;
		}
		for (Map.Entry<String, FloorHazardsConfig.TagHazards> entry : config.byTag.entrySet()) {
			if (floor.hasTag(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/** The day-mob settings for the current floor: its hazard override, else the Director's own. */
	static DirectorConfig.DayMobs dayMobs(MinecraftServer server, DirectorConfig.DayMobs fallback) {
		FloorHazardsConfig.TagHazards hazards = hazardsFor(floor(server).orElse(null));
		return hazards != null && hazards.dayMobs != null ? hazards.dayMobs : fallback;
	}

	/** Whether the camp shop is closed by a dead-camp problem right now. */
	static boolean shopClosed(MinecraftServer server) {
		return shopClosedUntil > Director.now(server);
	}

	public static boolean stormActive() {
		return storm != null;
	}

	/** Bosses walking the current floor (gametests, status). */
	public static int roamers() {
		return ROAMERS.size();
	}

	// ---------------------------------------------------------------- the loop

	/** Once a second while the Director is active. */
	static void second(MinecraftServer server, long now, List<ServerPlayer> players) {
		PlacedFloor floor = floor(server).orElse(null);
		FloorHazardsConfig.TagHazards hazards = hazardsFor(floor);
		if (floor == null || hazards == null) {
			if (act != -1) {
				reset();
			}
			return;
		}
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, floor.dimension()));
		if (level == null) {
			return;
		}
		if (act != floor.actOrdinal()) {
			reset();
			act = floor.actOrdinal();
			nextProblemAt = now + hazards.firstProblemSeconds;
			nextBossAt = now + hazards.roamingFirstSeconds;
		}
		boolean day = DirectorData.get(server).dayRunning() && !DirectorHooks.charging(server);
		tickStorm(server, level, floor, players, now);
		wanderRoamers(level, floor, hazards, now);
		if (!day || players.isEmpty()) {
			return;
		}
		if (now >= nextBossAt) {
			nextBossAt = now + Math.max(1, hazards.roamingRespawnSeconds);
			spawnRoamers(server, level, floor, hazards, players);
		}
		if (now >= nextProblemAt) {
			nextProblemAt = now + Math.max(5, hazards.problemIntervalSeconds);
			FloorHazardsConfig.Problem problem = HazardMath.pick(hazards.problems, level.getRandom()::nextDouble);
			if (problem != null) {
				trigger(server, problem, players);
			}
		}
	}

	/**
	 * Fires a problem now (also {@code /bs director hazard}). Returns null, or why it couldn't (no hazard floor, no players).
	 */
	public static @Nullable String trigger(MinecraftServer server, String id) {
		FloorHazardsConfig.TagHazards hazards = hazardsFor(floor(server).orElse(null));
		if (hazards == null) {
			return "This act's floor has no hazards";
		}
		List<ServerPlayer> players = DirectorHooks.activePlayers(server);
		if (players.isEmpty()) {
			return "No players in the run";
		}
		if ("bosses".equals(id)) {
			PlacedFloor floor = floor(server).orElseThrow();
			ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, floor.dimension()));
			if (level == null) {
				return "The floor's level isn't loaded";
			}
			act = floor.actOrdinal();
			int before = ROAMERS.size();
			spawnRoamers(server, level, floor, hazards, players);
			return ROAMERS.size() > before ? null : "No field boss could be placed (none installed, or all are out)";
		}
		for (FloorHazardsConfig.Problem problem : hazards.problems) {
			if (problem.id.equals(id) || problem.type.equals(id)) {
				act = floor(server).orElseThrow().actOrdinal();
				trigger(server, problem, players);
				return null;
			}
		}
		return "Unknown hazard " + id;
	}

	/** Problem ids plus {@code bosses} for the current floor's entry (command suggestions); every entry's when none. */
	public static List<String> triggers() {
		List<String> ids = new ArrayList<>();
		for (FloorHazardsConfig.TagHazards hazards : config.byTag.values()) {
			hazards.problems.forEach(problem -> {
				if (!ids.contains(problem.id)) {
					ids.add(problem.id);
				}
			});
		}
		ids.add("bosses");
		return ids;
	}

	private static void trigger(MinecraftServer server, FloorHazardsConfig.Problem problem, List<ServerPlayer> players) {
		long now = Director.now(server);
		PlacedFloor floor = floor(server).orElse(null);
		if (floor == null) {
			return;
		}
		switch (problem.type) {
			case "storm" -> {
				ServerPlayer target = players.get(server.overworld().getRandom().nextInt(players.size()));
				double radius = HazardMath.stormRadius(problem.radius, players.size());
				// It rolls in from the island's edge on the far side of a player, toward them.
				double angle = Math.atan2(target.getZ() - floor.camp().getZ(), target.getX() - floor.camp().getX()) + Math.PI;
				double start = floor.radius() * 0.8;
				storm = new HazardMath.Storm(Math.cos(angle) * start, Math.sin(angle) * start, radius, problem.speed, floor.radius());
				storm.aim(target.getX() - floor.camp().getX(), target.getZ() - floor.camp().getZ());
				stormSpec = problem;
				stormUntil = now + Math.max(1, problem.seconds);
				announce(players, problem, ChatFormatting.DARK_PURPLE);
			}
			case "surge" -> {
				int sent = Director.spawnPack(server, players, HazardMath.surgePack(problem.pack, players.size()), null, problem.eliteChance);
				announce(players, problem, ChatFormatting.RED);
				Director.log(server, DirectorLog.Kind.EVENT, "Hazard " + problem.id + ": " + sent + " spawned");
				return;
			}
			case "dead_camp" -> {
				shopClosedUntil = now + Math.max(1, problem.seconds);
				announce(players, problem, ChatFormatting.GRAY);
			}
			default -> {
				Director.log(server, DirectorLog.Kind.EVENT, "Hazard " + problem.id + " has unknown type " + problem.type);
				return;
			}
		}
		Director.log(server, DirectorLog.Kind.EVENT, "Hazard " + problem.id + " for " + problem.seconds + " s");
	}

	private static void announce(List<ServerPlayer> players, FloorHazardsConfig.Problem problem, ChatFormatting colour) {
		Component text = Component.translatable("message.blocksouls_game.hazard." + problem.id).withStyle(colour);
		for (ServerPlayer player : players) {
			player.sendSystemMessage(text);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1, 0.6f);
		}
	}

	// ---------------------------------------------------------------- the storm

	private static void tickStorm(MinecraftServer server, ServerLevel level, PlacedFloor floor, List<ServerPlayer> players, long now) {
		HazardMath.Storm current = storm;
		if (current == null || stormSpec == null) {
			return;
		}
		if (now >= stormUntil) {
			storm = null;
			stormSpec = null;
			for (ServerPlayer player : players) {
				player.sendSystemMessage(Component.translatable("message.blocksouls_game.hazard.storm_over").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		BlockPos camp = floor.camp();
		if (current.step(1) && !players.isEmpty()) {
			// Arrived: drift on toward a random player.
			ServerPlayer next = players.get(level.getRandom().nextInt(players.size()));
			current.aim(next.getX() - camp.getX(), next.getZ() - camp.getZ());
		}
		double cx = camp.getX() + current.x;
		double cz = camp.getZ() + current.z;
		RandomSource random = level.getRandom();
		int points = (int) Math.max(12, current.radius * 3);
		for (int i = 0; i < points; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double r = current.radius * Math.sqrt(random.nextDouble());
			double x = cx + Math.cos(angle) * r;
			double z = cz + Math.sin(angle) * r;
			double y = floor.groundY() + 1.5 + random.nextDouble() * 4;
			level.sendParticles(i % 3 == 0 ? ParticleTypes.WITCH : ParticleTypes.SQUID_INK, x, y, z, 1, 0.2, 0.4, 0.2, 0.01);
		}
		for (ServerPlayer player : players) {
			if (player.level() == level && current.inside(player.getX() - camp.getX(), player.getZ() - camp.getZ()) && !player.isSpectator()) {
				player.hurtServer(level, level.damageSources().magic(), stormSpec.damage);
			}
		}
		level.playSound(null, cx, floor.groundY() + 2, cz, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 0.4f, 0.5f);
	}

	// ---------------------------------------------------------------- roaming field bosses

	private static void spawnRoamers(MinecraftServer server, ServerLevel level, PlacedFloor floor, FloorHazardsConfig.TagHazards hazards,
			List<ServerPlayer> players) {
		pruneRoamers(level);
		RandomSource random = level.getRandom();
		for (String raw : hazards.roamingBosses) {
			if (ROAMERS.size() >= hazards.roamingBossesAlive) {
				return;
			}
			Identifier id = Identifier.tryParse(raw);
			if (id == null || ROAMERS.containsKey(raw) || !BossSpawns.exists(id)) {
				continue;
			}
			BlockPos pos = roamSpot(level, floor, hazards, players, random);
			Entity boss = pos == null ? null : BossSpawns.spawn(level, id, pos);
			if (boss != null) {
				ROAMERS.put(raw, boss.getUUID());
				for (ServerPlayer player : players) {
					player.sendSystemMessage(Component.translatable("message.blocksouls_game.hazard.field_boss", boss.getDisplayName())
							.withStyle(ChatFormatting.DARK_RED));
				}
			}
			Director.log(server, DirectorLog.Kind.EVENT, "Field boss " + raw + (boss != null ? " at " + pos.toShortString() : " couldn't be placed"));
		}
	}

	private static @Nullable BlockPos roamSpot(ServerLevel level, PlacedFloor floor, FloorHazardsConfig.TagHazards hazards, List<ServerPlayer> players,
			RandomSource random) {
		double min = hazards.roamingMinDistance;
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.min(floor.radius() * 0.85, min + random.nextDouble() * Math.max(1, floor.radius() * 0.85 - min));
			int x = floor.camp().getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = floor.camp().getZ() + (int) Math.round(Math.sin(angle) * distance);
			boolean clear = true;
			for (ServerPlayer player : players) {
				if (player.level() == level && player.distanceToSqr(x, player.getY(), z) < min * min) {
					clear = false;
					break;
				}
			}
			BlockPos pos = clear ? Places.surface(level, x, z) : null;
			if (pos != null) {
				return pos;
			}
		}
		return null;
	}

	private static void pruneRoamers(ServerLevel level) {
		Iterator<Map.Entry<String, UUID>> it = ROAMERS.entrySet().iterator();
		while (it.hasNext()) {
			Entity entity = level.getEntity(it.next().getValue());
			if (entity == null || !entity.isAlive()) {
				it.remove();
			}
		}
	}

	/** Every 5 s a field boss with nothing to fight walks a leg somewhere else on the island. */
	private static void wanderRoamers(ServerLevel level, PlacedFloor floor, FloorHazardsConfig.TagHazards hazards, long now) {
		if (ROAMERS.isEmpty() || now % 5 != 0) {
			return;
		}
		pruneRoamers(level);
		RandomSource random = level.getRandom();
		for (UUID uuid : ROAMERS.values()) {
			if (!(level.getEntity(uuid) instanceof Mob mob) || mob.getTarget() != null || !mob.getNavigation().isDone()) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2;
			double x = mob.getX() + Math.cos(angle) * hazards.roamingWander;
			double z = mob.getZ() + Math.sin(angle) * hazards.roamingWander;
			double dx = x - floor.camp().getX();
			double dz = z - floor.camp().getZ();
			double limit = floor.radius() * 0.85;
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > limit) {
				x = floor.camp().getX() + dx / d * limit;
				z = floor.camp().getZ() + dz / d * limit;
			}
			BlockPos to = Places.surface(level, (int) Math.round(x), (int) Math.round(z));
			if (to != null) {
				mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, hazards.roamingSpeed);
			}
		}
	}
}
