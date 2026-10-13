package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.act.Act;
import io.github.tmldude.blocksouls.common.boss.BossSpawns;
import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.economy.CampObjects;
import io.github.tmldude.blocksouls.common.event.ActEvents;
import io.github.tmldude.blocksouls.common.event.DirectorEvents;
import io.github.tmldude.blocksouls.common.event.FloorEvents;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;
import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;
import io.github.tmldude.blocksouls.game.player.soul.Souls;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;
import io.github.tmldude.blocksouls.game.run.RunIds;

/**
 * The expedition day (PLAN2 §3a): an 8-minute timer per act that ticks only while someone is online, then the
 * siege. While it runs the Director keeps the day alive: day mobs, a loot win every 30–60 s, the act's random
 * event and mini-boss, scouts when it goes quiet. The camp horn ends it early for a buff.
 */
public final class DayLoop {
	/** An ambush marker waiting for a player to walk in. */
	record Ambush(String dimension, BlockPos pos) {
	}

	static final List<Ambush> AMBUSHES = new ArrayList<>();
	private static long lastDayMobs;
	private static long nextLootAt = -1;

	private DayLoop() {
	}

	static void init() {
		ActEvents.ACT_ENTERED.register(DayLoop::onActEntered);
		ActEvents.PORTAL_CHARGE_STARTED.register(DayLoop::onSiegeStarted);
		ActEvents.PORTAL_CHARGE_COMPLETED.register(DayLoop::onSiegeCompleted);
		FloorEvents.NODE_POPPED.register((player, floor, pos, node) -> lootWin(player.level().getServer(), "node popped"));
		FloorEvents.CRATE_OPENED.register((player, floor, pos, loot) -> lootWin(player.level().getServer(), "crate opened"));
	}

	private static DirectorConfig config() {
		return Director.config();
	}

	static void reset() {
		AMBUSHES.clear();
		lastDayMobs = 0;
		nextLootAt = -1;
	}

	// ---------------------------------------------------------------- the act's day

	private static void onActEntered(MinecraftServer server, Act act) {
		if (!Director.active(server)) {
			return;
		}
		DirectorData data = DirectorData.get(server);
		ServerLevel level = Places.levelOf(server, act);
		if (data.campAct != act.ordinal()) {
			placeCamp(server, level, act);
		}
		if (act.ordinal() > data.dayAct) {
			startDay(server, act.ordinal());
		}
		if (!data.eventActs.contains(act.ordinal())) {
			data.eventActs.add(act.ordinal());
			data.setDirty();
			placeEvent(server, level, act.ordinal(), null);
		}
		if (act.ordinal() >= config().events.miniBossFromAct && !data.miniBossActs.contains(act.ordinal())) {
			data.miniBossActs.add(act.ordinal());
			data.setDirty();
			placeMiniBoss(server, level, act.ordinal());
		}
	}

	/** Day length in seconds for an act: the act's override or the default, times the trinkets' prep multiplier. */
	static int daySeconds(MinecraftServer server, int act) {
		DirectorConfig.Day day = config().day;
		double minutes = act >= 1 && act <= day.minutesByAct.size() ? day.minutesByAct.get(act - 1) : day.minutes;
		return (int) Math.max(1, Math.round(minutes * 60 * BoonRules.prepTimeMultiplier(server)));
	}

	/** Starts the act's day timer. */
	public static void startDay(MinecraftServer server, int act) {
		DirectorData data = DirectorData.get(server);
		int seconds = daySeconds(server, act);
		data.dayAct = act;
		data.dayTicks = seconds * 20;
		data.dayTicksLeft = seconds * 20;
		data.setDirty();
		long now = Director.now(server);
		lastDayMobs = now;
		nextLootAt = now + lootDelay(server.overworld().getRandom());
		Director.log(server, DirectorLog.Kind.DAY, "Day " + act + " started: " + seconds + "s until the siege");
		Director.beat(server, "day started");
		DirectorEvents.DAY_STARTED.invoker().onDayStarted(server, seconds);
	}

	private static void onSiegeStarted(MinecraftServer server, Act act) {
		if (!Director.active(server)) {
			return;
		}
		DirectorData data = DirectorData.get(server);
		if (data.dayRunning()) {
			Director.log(server, DirectorLog.Kind.DAY, "Day ended with " + data.daySecondsLeft() + "s left: siege");
		}
		data.dayTicksLeft = 0;
		data.setDirty();
		Director.siegeActive = true;
		Director.beat(server, "siege started");
		RandomSource random = server.overworld().getRandom();
		for (int i = 0; i < data.pendingBumps; i++) {
			String added = DirectorHooks.bumpModifier(server, random);
			Director.log(server, DirectorLog.Kind.ECONOMY, added == null ? "Cursed chest bump: already Blood Moon" : "Cursed chest bump: +" + added);
		}
		data.pendingBumps = 0;
		data.setDirty();
		Economy.onSiegeStarted(server);
		Objectives.onSiegeStarted(server);
	}

	private static void onSiegeCompleted(MinecraftServer server, Act act) {
		if (!Director.active(server) || !Director.siegeActive) {
			return;
		}
		Director.siegeActive = false;
		Objectives.onSiegeEnded(server, true);
		Economy.onSiegeEnded(server, true);
		DirectorData data = DirectorData.get(server);
		if (data.siegeRelics > 0) {
			BoonTier tier = BoonTier.byId(config().horn.overclockRelicTier);
			for (ServerPlayer player : DirectorHooks.activePlayers(server)) {
				for (int i = 0; i < data.siegeRelics; i++) {
					RelicOffers.dropRelic(player, tier);
				}
			}
			Director.log(server, DirectorLog.Kind.ECONOMY, "Overclock paid: " + data.siegeRelics + " relic(s) each");
		}
		data.siegeRelics = 0;
		data.hornTierShift = 0;
		data.setDirty();
	}

	// ---------------------------------------------------------------- once a second

	static void second(MinecraftServer server, long now) {
		DirectorData data = DirectorData.get(server);
		List<ServerPlayer> players = DirectorHooks.activePlayers(server);
		if (!data.dayRunning() || DirectorHooks.charging(server)) {
			return;
		}
		if (!server.getPlayerList().getPlayers().isEmpty()) {
			data.dayTicksLeft = Math.max(0, data.dayTicksLeft - 20);
			data.setDirty();
		}
		if (data.dayTicksLeft == 0) {
			Director.log(server, DirectorLog.Kind.DAY, "Day over: the siege begins");
			if (config().day.siegeAtDayEnd) {
				String error = DirectorHooks.startCharge(server);
				if (error != null) {
					Director.log(server, DirectorLog.Kind.DAY, "Siege couldn't start: " + error);
				}
			}
			return;
		}
		if (DirectorHooks.phase(server) == RunPhase.WAVE || players.isEmpty()) {
			return;
		}
		dayMobs(server, players, now);
		lootCadence(server, players, now);
		springAmbushes(server, players);
	}

	private static void dayMobs(MinecraftServer server, List<ServerPlayer> players, long now) {
		DirectorConfig.DayMobs config = config().dayMobs;
		if (now - lastDayMobs < config.intervalSeconds) {
			return;
		}
		lastDayMobs = now;
		int cap = (int) Math.round(config.maxAlive * PartyScaling.directorBurst(players.size()));
		int alive = DirectorHooks.hunters(server);
		if (alive >= cap) {
			return;
		}
		int size = Math.min(cap - alive, (int) Math.max(1, Math.round(config.pack * PartyScaling.directorBurst(players.size()))));
		int sent = Director.spawnPack(server, players, size, null, config.eliteChance);
		if (sent > 0) {
			Director.log(server, DirectorLog.Kind.DAY, "Day mobs: " + sent + " (" + (alive + sent) + "/" + cap + " out)");
			Director.beat(server, "day mobs");
		}
	}

	// ---------------------------------------------------------------- loot cadence

	private static long lootDelay(RandomSource random) {
		DirectorConfig.Loot loot = config().loot;
		int min = Math.max(1, loot.minSeconds);
		return min + random.nextInt(Math.max(1, loot.maxSeconds - min + 1));
	}

	/** A real loot win (an elite, a node, a crate): the cadence deadline restarts. */
	static void lootWin(MinecraftServer server, String reason) {
		if (!Director.active(server)) {
			return;
		}
		nextLootAt = Director.now(server) + lootDelay(server.overworld().getRandom());
	}

	static void onKill(MinecraftServer server, Entity target) {
		if (target.entityTags().contains(RunIds.ELITE_TAG)) {
			lootWin(server, "elite kill");
		} else if (nextLootAt >= 0) {
			nextLootAt = Math.max(nextLootAt, Director.now(server) + config().loot.killWinSeconds);
		}
	}

	/** No win in a while: a cache turns up near a player. */
	private static void lootCadence(MinecraftServer server, List<ServerPlayer> players, long now) {
		if (!config().loot.enabled || nextLootAt < 0 || now < nextLootAt) {
			return;
		}
		RandomSource random = server.overworld().getRandom();
		ServerPlayer player = players.get(random.nextInt(players.size()));
		ServerLevel level = (ServerLevel) player.level();
		BlockPos pos = Places.ring(level, player.blockPosition(), 10, random);
		nextLootAt = now + lootDelay(random);
		if (pos == null) {
			return;
		}
		placeCache(level, pos, CommonIds.WAVE_DROPS_COMMON);
		Director.log(server, DirectorLog.Kind.LOOT, "Loot cache near " + player.getScoreboardName() + " at " + pos.toShortString());
		Director.beat(server, "loot cache");
	}

	static void placeCache(ServerLevel level, BlockPos pos, ResourceKey<LootTable> lootTable) {
		level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
			barrel.setLootTable(lootTable, level.getRandom().nextLong());
		}
	}

	// ---------------------------------------------------------------- the horn

	/**
	 * The camp horn: ends the day now for a buff that grows with the time skipped (souls, better siege drops, a
	 * relic past half the day). Overclocked (sneak-use) it also raises the threat and owes a relic per player
	 * when the siege completes. Null on success, else why not.
	 */
	public static @Nullable String blowHorn(ServerPlayer player, boolean overclock) {
		MinecraftServer server = player.level().getServer();
		DirectorData data = DirectorData.get(server);
		if (!data.dayRunning() || DirectorHooks.charging(server)) {
			return "The horn only sounds during the day";
		}
		int skipped = data.daySecondsLeft();
		HornMath.Buff buff = HornMath.buff(skipped, data.daySeconds(), config().horn);
		data.dayTicksLeft = 0;
		data.hornTierShift = buff.tierShift();
		List<ServerPlayer> players = DirectorHooks.activePlayers(server);
		for (ServerPlayer each : players) {
			Souls.give(each, buff.souls());
			if (buff.freeRelic()) {
				RelicOffers.dropRelic(each, BoonTier.byId(config().horn.boonTier));
			}
			each.sendSystemMessage(Component.translatable("message.blocksouls_game.director.horn" + (overclock ? "_overclock" : ""),
					player.getDisplayName(), skipped, buff.souls()));
		}
		if (overclock) {
			DirectorHooks.addThreat(server, config().horn.overclockThreat);
			data.siegeRelics += config().horn.overclockRelics;
		}
		data.setDirty();
		Director.log(server, DirectorLog.Kind.DAY, String.format("%s blew the %shorn: %ds skipped (%.0f%%) -> %d souls, tier +%d%s",
				player.getScoreboardName(), overclock ? "overclock " : "", skipped, buff.skippedShare() * 100, buff.souls(), buff.tierShift(),
				buff.freeRelic() ? ", free relic" : ""));
		DirectorEvents.HORN_BLOWN.invoker().onHornBlown(player, skipped, overclock);
		String error = DirectorHooks.startCharge(server);
		if (error != null) {
			Director.log(server, DirectorLog.Kind.DAY, "Siege couldn't start: " + error);
		}
		return null;
	}

	// ---------------------------------------------------------------- camp, event, mini-boss

	private static void placeCamp(MinecraftServer server, ServerLevel level, Act act) {
		DirectorData data = DirectorData.get(server);
		Optional<PlacedFloor> floor = Floors.forAct(server, act.ordinal());
		BlockPos camp;
		if (floor.isPresent()) {
			camp = floor.get().camp();
		} else if (config().camp.placeWithoutFloor && !DirectorHooks.activePlayers(server).isEmpty()) {
			camp = DirectorHooks.activePlayers(server).get(0).blockPosition();
		} else {
			return;
		}
		data.campDimension = level.dimension().identifier().toString();
		data.campPos = camp.asLong();
		data.campAct = act.ordinal();
		data.setDirty();
		RandomSource random = level.getRandom();
		DirectorConfig.Camp config = config().camp;
		boolean horn = place(level, camp.offset(config.hornOffsetX, 0, config.hornOffsetZ), CampObject.CAMP_HORN, random);
		boolean shop = place(level, camp.offset(config.shopkeeperOffsetX, 0, config.shopkeeperOffsetZ), CampObject.SHOPKEEPER, random);
		Director.log(server, DirectorLog.Kind.DAY, "Camp at " + camp.toShortString() + (floor.isPresent() ? " (floor)" : "")
				+ (horn ? ", horn" : "") + (shop ? ", shopkeeper" : ""));
	}

	private static boolean place(ServerLevel level, BlockPos near, CampObject object, RandomSource random) {
		BlockPos pos = Places.surfaceNear(level, near, 3, random);
		return pos != null && CampObjects.place(level, pos, object);
	}

	/** Picks (or uses {@code forced}) the act's random event and places it. Returns the event id, or null. */
	public static @Nullable String placeEvent(MinecraftServer server, ServerLevel level, int act, @Nullable String forced) {
		RandomSource random = level.getRandom();
		String id = forced != null ? forced : DirectorRules.pickEvent(config().events.pool, act, random.nextDouble());
		if (id == null) {
			return null;
		}
		BlockPos pos = eventSpot(server, level, act, FloorSlotKind.EVENT, config().events.distanceFromCamp, random);
		if (pos == null) {
			Director.log(server, DirectorLog.Kind.EVENT, "No spot for event " + id);
			return null;
		}
		boolean placed = switch (id) {
			case "blood_altar" -> CampObjects.place(level, pos, CampObject.BLOOD_ALTAR);
			case "cursed_chest" -> CampObjects.place(level, pos, CampObject.CURSED_CHEST);
			case "challenge_pylon" -> CampObjects.place(level, pos, CampObject.CHALLENGE_PYLON);
			case "wandering_trader" -> EntityTypes.WANDERING_TRADER.spawn(level, pos, EntitySpawnReason.EVENT) != null;
			case "stranded_ally" -> {
				IronGolem golem = EntityTypes.IRON_GOLEM.spawn(level, pos, EntitySpawnReason.EVENT);
				if (golem != null) {
					golem.setPlayerCreated(true);
				}
				yield golem != null;
			}
			case "ambush" -> {
				AMBUSHES.add(new Ambush(level.dimension().identifier().toString(), pos));
				yield true;
			}
			default -> false;
		};
		Director.log(server, DirectorLog.Kind.EVENT, "Event " + id + (placed ? " at " + pos.toShortString() : " couldn't be placed"));
		return placed ? id : null;
	}

	private static @Nullable BlockPos eventSpot(MinecraftServer server, ServerLevel level, int act, FloorSlotKind kind, int distance,
			RandomSource random) {
		Optional<PlacedFloor> floor = Floors.forAct(server, act);
		if (floor.isPresent()) {
			List<FloorSlot> slots = floor.get().slots(kind);
			if (!slots.isEmpty()) {
				FloorSlot slot = slots.get(random.nextInt(slots.size()));
				BlockPos pos = Places.surfaceNear(level, slot.pos(), Math.max(1, slot.radius()), random);
				if (pos != null) {
					return pos;
				}
			}
		}
		return Places.ring(level, Places.anchor(server, level), distance, random);
	}

	private static void springAmbushes(MinecraftServer server, List<ServerPlayer> players) {
		double radius = config().events.ambushRadius * config().events.ambushRadius;
		AMBUSHES.removeIf(ambush -> {
			for (ServerPlayer player : players) {
				if (player.level().dimension().identifier().toString().equals(ambush.dimension())
						&& Places.distanceSq(ambush.pos(), player.getX(), player.getZ()) <= radius) {
					int size = (int) Math.max(1, Math.round(config().events.ambushPack * PartyScaling.directorBurst(players.size())));
					int sent = Director.spawnPack(server, players, size - 1, ambush.pos(), 0);
					sent += Director.spawnPack(server, players, 1, ambush.pos(), 1);
					Director.log(server, DirectorLog.Kind.EVENT, "Ambush sprung by " + player.getScoreboardName() + ": " + sent + " mobs");
					Director.beat(server, "ambush");
					return true;
				}
			}
			return false;
		});
	}

	/** The mini-boss for {@code act}: the roster rotated by the run seed, skipping bosses that aren't installed. */
	static @Nullable Identifier miniBossFor(List<String> roster, int act, int fromAct, long seed) {
		List<Identifier> known = roster.stream().map(Identifier::tryParse).filter(id -> id != null && BossSpawns.exists(id)).toList();
		if (known.isEmpty() || act < fromAct) {
			return null;
		}
		return known.get(Math.floorMod(act - fromAct + seed, known.size()));
	}

	private static void placeMiniBoss(MinecraftServer server, ServerLevel level, int act) {
		DirectorData data = DirectorData.get(server);
		Identifier boss = miniBossFor(config().events.miniBosses, act, config().events.miniBossFromAct, data.seed);
		if (boss == null) {
			Director.log(server, DirectorLog.Kind.EVENT, "No mini-boss for act " + act + " (none installed)");
			return;
		}
		BlockPos pos = eventSpot(server, level, act, FloorSlotKind.MINI_BOSS, config().events.miniBossDistance, level.getRandom());
		Entity spawned = pos == null ? null : BossSpawns.spawn(level, boss, pos);
		Director.log(server, DirectorLog.Kind.EVENT, "Mini-boss " + boss + (spawned != null ? " at " + pos.toShortString() : " couldn't be placed"));
	}
}
