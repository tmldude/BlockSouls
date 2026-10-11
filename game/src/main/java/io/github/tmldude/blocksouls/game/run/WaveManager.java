package io.github.tmldude.blocksouls.game.run;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;

import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/** Spawns, scales, tracks and ends waves (server thread). */
public final class WaveManager {
	private static final Identifier HEALTH_MODIFIER = CommonIds.id(RunIds.NAMESPACE, "wave_health");
	private static final Identifier DAMAGE_MODIFIER = CommonIds.id(RunIds.NAMESPACE, "wave_damage");
	private static final Identifier FOLLOW_MODIFIER = CommonIds.id(RunIds.NAMESPACE, "wave_follow_range");
	private static final Identifier SPRINTER_MODIFIER = CommonIds.id(RunIds.NAMESPACE, "sprinter");
	private static final int SPAWNS_PER_TICK = 3;
	private static final int SPAWN_ATTEMPTS = 16;

	/** Not saved: per-mob ticks spent far from every player (or unloaded). */
	private static final Map<UUID, Integer> STRAY_TICKS = new HashMap<>();
	/** Not saved: ordinary wave mobs burning at dawn → game time when they're removed. */
	private static final Map<UUID, Long> BURNING = new HashMap<>();
	private static int fewLeftTicks;

	private WaveManager() {
	}

	private static RunConfig config() {
		return RunSystem.config();
	}

	// ---------------------------------------------------------------- wave lifecycle

	/** Starts the next wave right now (phase WAVE). */
	static void startWave(MinecraftServer server, RunData data) {
		data.wave++;
		data.wavesThisNight++;
		data.nightBudget = Math.max(data.nightBudget, data.wavesThisNight);
		data.clearWave();
		STRAY_TICKS.clear();
		fewLeftTicks = 0;
		int players = overworldPlayers(server).size();
		data.waveTotal = WaveMath.waveCount(data.wave, players, data.difficulty.waveSize(),
				NightModifiers.countFactor(data.nightModifiers, config()), config());
		data.phase = RunPhase.WAVE;
		data.setDirty();
		RunEvents.WAVE_STARTED.invoker().onWaveStarted(server, data.wave);
		announceWave(server, data.wave);
		RunSync.markDirty();
	}

	/** Ends the current wave. {@code cleared} false = dawn or an admin ended it. */
	static void endWave(MinecraftServer server, RunData data, boolean cleared) {
		if (data.phase != RunPhase.WAVE) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!cleared) {
			for (UUID id : List.copyOf(data.waveMobs)) {
				Entity entity = level.getEntity(id);
				if (entity instanceof Mob mob && mob.isAlive() && isElite(mob)) {
					data.hunters.add(id);
				} else if (entity != null && entity.isAlive()) {
					burn(level, entity);
				}
			}
		}
		int wave = data.wave;
		UUID killer = data.lastKiller;
		data.clearWave();
		data.phase = WaveMath.isNight(level.getOverworldClockTime(), config()) ? RunPhase.NIGHT_QUIET : RunPhase.DAY;
		data.setDirty();
		RunEvents.WAVE_ENDED.invoker().onWaveEnded(server, wave, cleared);
		if (cleared) {
			placeRewardChest(server, killer);
		}
		StarterKit.onWaveEnded(server, data, wave);
		RunSync.markDirty();
	}

	/** Called when a wave is cleared: chain the next wave after the break if the night allows. */
	private static void onCleared(MinecraftServer server, RunData data) {
		endWave(server, data, true);
		if (data.phase != RunPhase.NIGHT_QUIET) {
			return;
		}
		if (canStartAnotherWave(server, data, config().waveBreakSeconds)) {
			data.phase = RunPhase.WAVE_BREAK;
			data.breakEndsAt = server.overworld().getGameTime() + config().waveBreakSeconds * 20L;
			data.setDirty();
			RunSync.markDirty();
		}
	}

	static boolean canStartAnotherWave(MinecraftServer server, RunData data, int delaySeconds) {
		if (data.wavesThisNight >= data.nightBudget) {
			return false;
		}
		double nightLeft = WaveMath.secondsUntil(server.overworld().getOverworldClockTime(), config().dawnTime, RunSystem.clockRate(server));
		return nightLeft - delaySeconds >= config().minNightLeftSeconds;
	}

	/** Per-tick work while a run is active. */
	static void tick(MinecraftServer server, RunData data) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();

		if (data.phase == RunPhase.WAVE_BREAK && now >= data.breakEndsAt) {
			if (canStartAnotherWave(server, data, 0)) {
				startWave(server, data);
			} else {
				data.phase = RunPhase.NIGHT_QUIET;
				data.setDirty();
				RunSync.markDirty();
			}
		}

		if (data.phase == RunPhase.WAVE) {
			spawnTrickle(server, data);
			if (now % 20 == 0) {
				checkWaveMobs(server, data);
			}
			if (data.waveSpawned >= data.waveTotal && data.waveMobs.isEmpty()) {
				onCleared(server, data);
			}
		}

		if (now % 20 == 0) {
			tickHunters(server, data);
		}
		tickBurning(level, now);
	}

	// ---------------------------------------------------------------- spawning

	private static void spawnTrickle(MinecraftServer server, RunData data) {
		List<ServerPlayer> players = overworldPlayers(server);
		if (players.isEmpty()) {
			return;
		}
		for (int i = 0; i < SPAWNS_PER_TICK && data.waveSpawned < data.waveTotal && data.waveMobs.size() < PartyScaling.aliveCap(players.size()); i++) {
			// A failed spawn still counts, so a wave can't stall on bad terrain; it retries next tick otherwise.
			if (!spawnOne(server, data, players)) {
				return;
			}
		}
	}

	/** Spawns one wave mob near a random overworld player. False if no spot was found this tick. */
	static boolean spawnOne(MinecraftServer server, RunData data, List<ServerPlayer> players) {
		ServerLevel level = server.overworld();
		RandomSource random = level.getRandom();
		ServerPlayer anchor = players.get(random.nextInt(players.size()));
		BlockPos pos = findSpawnPos(level, anchor, players, random);
		if (pos == null) {
			return false;
		}

		boolean breaks = random.nextDouble() < WaveMath.breakerShare(data.wave, config());
		WaveContext context = new WaveContext(data.wave, data.difficulty, modifierIds(data), breaks);
		boolean allowElite = data.waveElites < WaveMath.maxElites(data.wave, players.size(), config());
		WaveMobSpec spec = pickSpec(data, random, allowElite);
		Mob mob = null;
		if (spec != null) {
			try {
				mob = spec.factory().create(level, pos, context);
			} catch (RuntimeException e) {
				BlockSoulsGame.LOGGER.error("Wave mob factory {} failed", spec.id(), e);
			}
		}
		if (mob == null) {
			mob = vanillaZombie(level, pos);
		}
		data.waveSpawned++;
		data.setDirty();
		if (mob == null) {
			return true;
		}

		mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
		boolean elite = (spec != null && spec.elite()) || isElite(mob);
		setUpWaveMob(mob, data, elite);
		// Track before adding: ENTITY_LOAD fires on add and discards untracked wave mobs.
		data.waveMobs.add(mob.getUUID());
		if (!level.addFreshEntity(mob)) {
			data.waveMobs.remove(mob.getUUID());
			return true;
		}
		if (elite) {
			data.waveElites++;
		}
		retarget(level, mob, players);
		RunSync.markDirty();
		return true;
	}

	private static @Nullable Mob vanillaZombie(ServerLevel level, BlockPos pos) {
		Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		if (zombie != null) {
			zombie.snapTo(pos, 0, 0);
			zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		}
		return zombie;
	}

	/** Weighted pick among this run's eligible specs (roster shuffled per run). Null = vanilla fallback. */
	static @Nullable WaveMobSpec pickSpec(RunData data, RandomSource random, boolean allowElite) {
		int spread = Math.max(0, config().rosterShuffle);
		List<WaveMobSpec> candidates = new ArrayList<>();
		for (WaveMobSpec spec : WaveMobs.eligible(data.wave + spread)) {
			int offset = data.rosterOffsets.computeIfAbsent(spec.id().toString(), id -> RosterShuffle.offset(id, data.runSeed, spread));
			if (RosterShuffle.shiftedMinWave(spec.minWave(), offset) <= data.wave && (allowElite || !spec.elite()) && spec.weight() > 0) {
				candidates.add(spec);
			}
		}
		if (candidates.isEmpty()) {
			// The shuffle never leaves a wave with nothing to spawn: fall back to the unshuffled roster.
			for (WaveMobSpec spec : WaveMobs.eligible(data.wave)) {
				if ((allowElite || !spec.elite()) && spec.weight() > 0) {
					candidates.add(spec);
				}
			}
		}
		int total = candidates.stream().mapToInt(WaveMobSpec::weight).sum();
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (WaveMobSpec spec : candidates) {
			roll -= spec.weight();
			if (roll < 0) {
				return spec;
			}
		}
		return candidates.getLast();
	}

	private static void setUpWaveMob(Mob mob, RunData data, boolean elite) {
		RunConfig config = config();
		double health = WaveMath.healthMultiplier(data.wave, data.difficulty.mobHealth(), config);
		double damage = WaveMath.damageMultiplier(data.wave, data.difficulty.mobDamage(), config);
		modifier(mob, Attributes.MAX_HEALTH, HEALTH_MODIFIER, health - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER, damage - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null && follow.getBaseValue() < config.followRange) {
			modifier(mob, Attributes.FOLLOW_RANGE, FOLLOW_MODIFIER, config.followRange - follow.getBaseValue(), AttributeModifier.Operation.ADD_VALUE);
		}
		if (data.nightModifiers.contains(RunIds.SPRINTER_ID)) {
			modifier(mob, Attributes.MOVEMENT_SPEED, SPRINTER_MODIFIER, config.sprinterSpeedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
		mob.setHealth(mob.getMaxHealth());
		mob.setPersistenceRequired();
		mob.addTag(RunIds.WAVE_MOB_TAG);
		if (elite) {
			mob.addTag(RunIds.ELITE_TAG);
		}
	}

	private static void modifier(Mob mob, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && amount != 0) {
			instance.removeModifier(id);
			instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/**
	 * A standable spot 24–40 blocks from {@code anchor}, not within 12 blocks of any player, preferring dark spots. Tries
	 * the anchor's own height first (caves, bases) and then the surface.
	 */
	static @Nullable BlockPos findSpawnPos(ServerLevel level, ServerPlayer anchor, List<ServerPlayer> players, RandomSource random) {
		RunConfig config = config();
		BlockPos best = null;
		int bestLight = Integer.MAX_VALUE;
		for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = config.spawnMinDistance + random.nextDouble() * Math.max(0, config.spawnMaxDistance - config.spawnMinDistance);
			int x = Mth.floor(anchor.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(anchor.getZ() + Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, anchor.getBlockY(), z))) {
				continue;
			}
			BlockPos pos = standableNear(level, x, anchor.getBlockY(), z);
			if (pos == null) {
				int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos top = new BlockPos(x, surface, z);
				pos = isStandable(level, top) ? top : null;
			}
			if (pos == null || tooCloseToPlayers(pos, players, config.minPlayerDistance)) {
				continue;
			}
			int light = level.getMaxLocalRawBrightness(pos);
			if (light < bestLight) {
				best = pos;
				bestLight = light;
				if (light <= 7) {
					break;
				}
			}
		}
		return best;
	}

	private static @Nullable BlockPos standableNear(ServerLevel level, int x, int y, int z) {
		for (int dy = 6; dy >= -8; dy--) {
			BlockPos pos = new BlockPos(x, y + dy, z);
			if (level.isInsideBuildHeight(pos.getY()) && isStandable(level, pos)) {
				return pos;
			}
		}
		return null;
	}

	/** Solid floor, two free non-fluid blocks for the body. */
	static boolean isStandable(ServerLevel level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState floor = level.getBlockState(below);
		if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.getFluidState().isEmpty()) {
			return false;
		}
		for (BlockPos body : new BlockPos[] {pos, pos.above()}) {
			BlockState state = level.getBlockState(body);
			if (!state.getCollisionShape(level, body).isEmpty() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static boolean tooCloseToPlayers(BlockPos pos, List<ServerPlayer> players, double minDistance) {
		double min = minDistance * minDistance;
		for (ServerPlayer player : players) {
			if (player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < min) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- tracking

	/** Once a second: drop dead mobs, retarget, respawn stragglers, light up the last few. */
	private static void checkWaveMobs(MinecraftServer server, RunData data) {
		ServerLevel level = server.overworld();
		List<ServerPlayer> players = overworldPlayers(server);
		RunConfig config = config();
		double strayDistance = config.strayDistance * config.strayDistance;
		List<Mob> alive = new ArrayList<>();

		for (Iterator<UUID> it = data.waveMobs.iterator(); it.hasNext(); ) {
			UUID id = it.next();
			Entity entity = level.getEntity(id);
			if (entity != null && !entity.isAlive()) {
				it.remove();
				STRAY_TICKS.remove(id);
				data.setDirty();
				continue;
			}
			boolean stray = entity == null || players.isEmpty() || players.stream().allMatch(p -> p.distanceToSqr(entity) > strayDistance);
			int ticks = stray ? STRAY_TICKS.merge(id, 20, Integer::sum) : 0;
			if (!stray) {
				STRAY_TICKS.remove(id);
			}
			if (stray && ticks >= config.straySeconds * 20 && !players.isEmpty()) {
				// Discard and let the trickle spawn a replacement near a player.
				if (entity != null) {
					entity.discard();
				}
				it.remove();
				STRAY_TICKS.remove(id);
				data.waveSpawned = Math.max(0, data.waveSpawned - 1);
				if (entity instanceof Mob mob && isElite(mob)) {
					data.waveElites = Math.max(0, data.waveElites - 1);
				}
				data.setDirty();
				continue;
			}
			if (entity instanceof Mob mob) {
				alive.add(mob);
				retarget(level, mob, players);
			}
		}

		int left = data.waveTotal - data.waveSpawned + data.waveMobs.size();
		fewLeftTicks = left <= config.glowRemaining ? fewLeftTicks + 20 : 0;
		if (fewLeftTicks >= config.glowSeconds * 20) {
			for (Mob mob : alive) {
				mob.setGlowingTag(true);
			}
		}
		RunSync.markDirty();
	}

	/** Elites that outlived their wave keep hunting until killed. */
	private static void tickHunters(MinecraftServer server, RunData data) {
		if (data.hunters.isEmpty()) {
			return;
		}
		ServerLevel level = server.overworld();
		List<ServerPlayer> players = overworldPlayers(server);
		for (Iterator<UUID> it = data.hunters.iterator(); it.hasNext(); ) {
			Entity entity = level.getEntity(it.next());
			if (entity != null && !entity.isAlive()) {
				it.remove();
				data.setDirty();
			} else if (entity instanceof Mob mob) {
				retarget(level, mob, players);
			}
		}
	}

	private static void retarget(ServerLevel level, Mob mob, List<ServerPlayer> players) {
		LivingEntity current = mob.getTarget();
		if (current instanceof ServerPlayer && current.isAlive() && !current.isSpectator()) {
			return;
		}
		players.stream()
				.filter(p -> !p.isSpectator() && !p.isCreative() && p.isAlive())
				.min(Comparator.comparingDouble(p -> p.distanceToSqr(mob)))
				.ifPresent(mob::setTarget);
	}

	private static void burn(ServerLevel level, Entity entity) {
		entity.igniteForTicks(config().dawnBurnSeconds * 20 + 20);
		BURNING.put(entity.getUUID(), level.getGameTime() + config().dawnBurnSeconds * 20L);
	}

	private static void tickBurning(ServerLevel level, long now) {
		if (BURNING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Long>> it = BURNING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> entry = it.next();
			if (now < entry.getValue()) {
				continue;
			}
			Entity entity = level.getEntity(entry.getKey());
			if (entity != null) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + 1, entity.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
				entity.discard();
			}
			it.remove();
		}
	}

	/** Removes every wave mob (current wave, burning ones and hunting elites). */
	static void discardAll(MinecraftServer server, RunData data) {
		ServerLevel level = server.overworld();
		for (UUID id : List.copyOf(data.waveMobs)) {
			discard(level, id);
		}
		for (UUID id : List.copyOf(data.hunters)) {
			discard(level, id);
		}
		for (UUID id : List.copyOf(BURNING.keySet())) {
			discard(level, id);
		}
		data.hunters.clear();
		BURNING.clear();
		STRAY_TICKS.clear();
		data.clearWave();
	}

	private static void discard(ServerLevel level, UUID id) {
		Entity entity = level.getEntity(id);
		if (entity != null) {
			entity.discard();
		}
	}

	/** Kills the current wave's mobs (counted as cleared once all are dead). */
	static int killWave(MinecraftServer server, RunData data) {
		ServerLevel level = server.overworld();
		int killed = 0;
		for (UUID id : List.copyOf(data.waveMobs)) {
			if (level.getEntity(id) instanceof LivingEntity living && living.isAlive()) {
				living.kill(level);
				killed++;
			}
		}
		data.waveMobs.clear();
		data.waveSpawned = data.waveTotal;
		data.setDirty();
		return killed;
	}

	/** Wave mobs (or leftovers from an earlier wave) that load in when they shouldn't exist are removed. */
	static void onEntityLoad(Entity entity, ServerLevel level, RunData data) {
		if (!(entity instanceof Mob) || !((EntityLoadData) entity).isLoadedFromDisk() || !entity.entityTags().contains(RunIds.WAVE_MOB_TAG)) {
			return;
		}
		UUID id = entity.getUUID();
		if (data.waveMobs.contains(id) || data.hunters.contains(id) || BURNING.containsKey(id)) {
			return;
		}
		if (entity.entityTags().contains(RunIds.ELITE_TAG) && data.phase != RunPhase.OVER && data.phase != RunPhase.NOT_STARTED) {
			data.hunters.add(id);
			data.setDirty();
			return;
		}
		entity.discard();
	}

	// ---------------------------------------------------------------- drops

	static void onDeath(LivingEntity entity, DamageSource source, RunData data) {
		if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		UUID id = mob.getUUID();
		boolean inWave = data.waveMobs.remove(id);
		boolean hunter = data.hunters.remove(id);
		if (!inWave && !hunter) {
			return;
		}
		STRAY_TICKS.remove(id);
		data.setDirty();
		RunSync.markDirty();

		ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
				: mob.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
		if (killer == null) {
			return;
		}
		if (inWave) {
			data.lastKiller = killer.getUUID();
		}
		boolean elite = isElite(mob);
		RandomSource random = level.getRandom();
		if (!elite && random.nextDouble() >= config().dropChance) {
			return;
		}
		int tier = WaveMath.pickTier(data.wave, random.nextDouble(), NightModifiers.tierShift(data.nightModifiers), elite, config());
		mob.dropFromLootTable(level, source, true, dropTable(tier));
	}

	static ResourceKey<LootTable> dropTable(int tier) {
		return switch (tier) {
			case WaveMath.LEGENDARY -> CommonIds.WAVE_DROPS_LEGENDARY;
			case WaveMath.RARE -> CommonIds.WAVE_DROPS_RARE;
			default -> CommonIds.WAVE_DROPS_COMMON;
		};
	}

	/** A reward chest on a free spot near the player who got the last kill (never replaces blocks). */
	static @Nullable BlockPos placeRewardChest(MinecraftServer server, @Nullable UUID killerId) {
		List<ServerPlayer> players = overworldPlayers(server);
		ServerPlayer near = killerId == null ? null : server.getPlayerList().getPlayer(killerId);
		if (near == null || near.level() != server.overworld()) {
			near = players.isEmpty() ? null : players.getFirst();
		}
		if (near == null) {
			return null;
		}
		ServerLevel level = server.overworld();
		BlockPos origin = near.blockPosition();
		BlockPos spot = null;
		search:
		for (int r = 1; r <= 4; r++) {
			for (int dy = 0; dy <= 2; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						for (int sign : new int[] {0, -1, 1}) {
							BlockPos pos = origin.offset(dx, sign * dy, dz);
							if ((Math.abs(dx) == r || Math.abs(dz) == r) && isFreeChestSpot(level, pos)) {
								spot = pos;
								break search;
							}
						}
					}
				}
			}
		}
		if (spot == null) {
			return null;
		}
		level.setBlockAndUpdate(spot, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(spot) instanceof ChestBlockEntity chest) {
			chest.setLootTable(CommonIds.WAVE_CLEAR_REWARD);
			chest.setLootTableSeed(level.getRandom().nextLong());
		}
		level.playSound(null, spot, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.1);
		level.sendParticles(ParticleTypes.END_ROD, spot.getX() + 0.5, spot.getY() + 1.2, spot.getZ() + 0.5, 12, 0.2, 0.6, 0.2, 0.05);
		return spot;
	}

	private static boolean isFreeChestSpot(ServerLevel level, BlockPos pos) {
		if (!level.isInsideBuildHeight(pos.getY()) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	// ---------------------------------------------------------------- helpers

	static boolean isElite(Mob mob) {
		return mob.entityTags().contains(RunIds.ELITE_TAG) || mob.getType().builtInRegistryHolder().is(CommonTags.ELITES);
	}

	/** Non-spectator players in the overworld (creative counts: they're present, mobs just ignore them). */
	static List<ServerPlayer> overworldPlayers(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		return server.getPlayerList().getPlayers().stream()
				.filter(p -> p.level() == overworld && !p.isSpectator() && p.isAlive())
				.toList();
	}

	static Set<Identifier> modifierIds(RunData data) {
		return data.nightModifiers.stream().map(Identifier::tryParse).filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
	}

	/** Mobs still alive or still to spawn in the current wave. */
	static int mobsLeft(RunData data) {
		return Math.max(0, data.waveTotal - data.waveSpawned) + data.waveMobs.size();
	}

	private static void announceWave(MinecraftServer server, int wave) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 10));
			player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(Integer.toString(wave))));
			if (player.level() == server.overworld()) {
				Holder<net.minecraft.sounds.SoundEvent> horn = SoundEvents.RAID_HORN;
				player.connection.send(new ClientboundSoundPacket(horn, SoundSource.HOSTILE, player.getX(), player.getY(), player.getZ(), 0.7F, 0.9F,
						player.getRandom().nextLong()));
			}
		}
	}
}
