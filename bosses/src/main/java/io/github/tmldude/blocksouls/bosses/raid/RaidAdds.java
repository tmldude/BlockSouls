package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.field.FieldBossData;
import io.github.tmldude.blocksouls.bosses.raid.logic.RaidMath;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.run.Runs;

/**
 * Phase 2's interludes. <b>The wave:</b> the dragon takes to the air and calls its army: wave mobs (the mobs module's,
 * or zombies without it) climb out of the island's edge in a ring of violet smoke, glowing so they read from anywhere.
 * <b>The herald:</b> one of the field bosses answers its roar at the island center, weaker than in the field. While
 * either lasts the dragon hovers out of reach; clearing the wave brings it down, killing the herald crashes it down
 * stunned.
 */
final class RaidAdds {
	private static final Identifier HERALD_HEALTH = CommonIds.id(CommonIds.BOSSES, "raid_herald_health");

	private final FinalDragon dragon;
	private final Set<UUID> wave = new HashSet<>();
	private @Nullable UUID herald;
	private int waveTicks;

	RaidAdds(FinalDragon dragon) {
		this.dragon = dragon;
	}

	private static FinalRaidConfig config() {
		return FinalRaids.CONFIG;
	}

	// ---------------------------------------------------------------------------------------------------------
	// The wave

	/** Calls the wave: {@link RaidMath#waveSize} mobs around the island. */
	void startWave(ServerLevel level, int players) {
		clearWave(level);
		waveTicks = 0;
		int count = RaidMath.waveSize(players, config().waveBase, config().wavePerPlayer,
				config().waveMax);
		RaidArena arena = dragon.arena();
		Vec3 center = arena.centerVec();
		double radius = arena.scaled(config().waveRadius);
		List<WaveMobSpec> specs = WaveMobs.eligible(config().waveMobTier);
		WaveContext context = new WaveContext(config().waveMobTier, Runs.get(level.getServer()).difficulty(), Set.of(), false);
		for (int i = 0; i < count; i++) {
			double angle = i * Math.PI * 2 / count + level.getRandom().nextDouble() * 0.3;
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			BlockPos pos = BlockPos.containing(x, arena.groundY(level, x, z), z);
			Mob mob = create(level, specs, pos, context);
			if (mob == null) {
				continue;
			}
			mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
			mob.setPersistenceRequired();
			// Glowing: the wave reads from across the island.
			mob.setGlowingTag(true);
			level.addFreshEntity(mob);
			ServerPlayer nearest = nearestPlayer(mob);
			if (nearest != null) {
				mob.setTarget(nearest);
			}
			wave.add(mob.getUUID());
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.1);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
		}
		level.playSound(null, center.x, center.y, center.z, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 12f, 0.7f);
	}

	private static @Nullable Mob create(ServerLevel level, List<WaveMobSpec> specs, BlockPos pos, WaveContext context) {
		if (!specs.isEmpty()) {
			int total = specs.stream().mapToInt(WaveMobSpec::weight).sum();
			int roll = level.getRandom().nextInt(Math.max(1, total));
			for (WaveMobSpec spec : specs) {
				roll -= spec.weight();
				if (roll < 0) {
					Mob mob = spec.factory().create(level, pos, context);
					if (mob != null) {
						return mob;
					}
					break;
				}
			}
		}
		// Without the mobs module: plain zombies.
		return EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
	}

	private @Nullable ServerPlayer nearestPlayer(Entity from) {
		ServerPlayer best = null;
		for (ServerPlayer player : dragon.participants()) {
			if (dragon.inFight(player) && !player.isCreative() && (best == null || player.distanceToSqr(from) < best.distanceToSqr(from))) {
				best = player;
			}
		}
		return best;
	}

	/** Wave mobs still alive. */
	int waveAlive(ServerLevel level) {
		wave.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity mob) || !mob.isAlive());
		return wave.size();
	}

	/** True once the wave is cleared (or ran out of time). */
	boolean waveOver(ServerLevel level) {
		waveTicks++;
		if (waveTicks % 40 == 0) {
			// Keep the wave on the players (they lose interest across the island).
			for (UUID id : wave) {
				if (level.getEntity(id) instanceof Mob mob && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
					ServerPlayer nearest = nearestPlayer(mob);
					if (nearest != null) {
						mob.setTarget(nearest);
					}
				}
			}
		}
		return waveAlive(level) == 0 || waveTicks >= config().waveTimeoutTicks;
	}

	void clearWave(ServerLevel level) {
		for (UUID id : wave) {
			Entity mob = level.getEntity(id);
			if (mob != null) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
				mob.discard();
			}
		}
		wave.clear();
	}

	// ---------------------------------------------------------------------------------------------------------
	// The herald

	/** Summons the herald at the island center. Returns false if no boss could be summoned (the interlude is skipped). */
	boolean startHerald(ServerLevel level) {
		clearHerald(level);
		Identifier id = pickHerald(level);
		if (id == null) {
			return false;
		}
		BossEntity boss = Bosses.type(id).map(type -> (BossEntity) type.create(level, EntitySpawnReason.EVENT)).orElse(null);
		if (boss == null) {
			return false;
		}
		RaidArena arena = dragon.arena();
		Vec3 at = arena.centerVec().add(arena.scaled(6), 0, 0);
		BlockPos pos = BlockPos.containing(at.x, arena.groundY(level, at.x, at.z), at.z);
		boss.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		boss.setHome(pos);
		// No fog wall on the island (it would lock out players coming back through the portal); the herald fights
		// across the whole raid arena.
		boss.setFogWallEnabled(false);
		boss.setArenaRadius((int) Math.max(4, arena.scaled(config().arenaRadius * 0.5)));
		AttributeInstance maxHealth = boss.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.addOrUpdateTransientModifier(new AttributeModifier(HERALD_HEALTH, config().heraldHealthMult - 1,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			boss.setHealth(boss.getMaxHealth());
		}
		level.addFreshEntity(boss);
		herald = boss.getUUID();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, boss.getX(), boss.getY() + 1.5, boss.getZ(), 120, 1.2, 1.5, 1.2, 0.3);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, boss.getX(), boss.getY() + 1, boss.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6f, 0.8f);
		// Straight into the fight with whoever is here.
		for (ServerPlayer player : dragon.participants()) {
			if (dragon.inFight(player)) {
				boss.engage(player);
				break;
			}
		}
		BlockSoulsBosses.LOGGER.info("Final raid: herald {} summoned", id);
		return true;
	}

	/** A registered boss from the pool, preferring ones not killed this run. Never the final dragon itself. */
	private static @Nullable Identifier pickHerald(ServerLevel level) {
		List<Identifier> registered = new ArrayList<>();
		for (String name : config().heraldPool) {
			Identifier id = Identifier.tryParse(name);
			if (id != null && !id.equals(CommonIds.FINAL_BOSS) && Bosses.type(id).isPresent()) {
				registered.add(id);
			}
		}
		if (registered.isEmpty()) {
			return null;
		}
		FieldBossData data = FieldBossData.get(level.getServer());
		List<Identifier> fresh = registered.stream().filter(id -> !data.killed(id)).toList();
		List<Identifier> pool = fresh.isEmpty() ? registered : fresh;
		return pool.get(level.getRandom().nextInt(pool.size()));
	}

	/** The herald, while it lives. */
	@Nullable BossEntity herald(ServerLevel level) {
		return herald != null && level.getEntity(herald) instanceof BossEntity boss && boss.isAlive() ? boss : null;
	}

	/** True once the herald is dead (or gone). */
	boolean heraldOver(ServerLevel level) {
		return herald(level) == null;
	}

	void clearHerald(ServerLevel level) {
		if (herald != null && level.getEntity(herald) instanceof BossEntity boss && boss.isAlive()) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 60, 0.8, 1.2, 0.8, 0.1);
			boss.discard();
		}
		herald = null;
	}

	void clear(ServerLevel level) {
		clearWave(level);
		clearHerald(level);
	}
}
