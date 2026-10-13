package io.github.tmldude.blocksouls.mobs.affix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.mob.Affix;
import io.github.tmldude.blocksouls.common.mob.Affixes;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.ai.Shockwave;
import io.github.tmldude.blocksouls.mobs.logic.AffixRoll;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/**
 * The real {@link Affixes} provider: nameplate, extra health and the one modifier each affix adds. Every number is in
 * {@code mobs.json} under {@code affixes}. Telegraphs match the effect: the Volatile ring is drawn at the blast radius,
 * the Burning trail only burns where its flames are.
 */
public final class MobAffixes implements Affixes.Provider {
	private static final Identifier HEALTH_MODIFIER = Identifier.fromNamespaceAndPath(BlockSoulsMobs.MOD_ID, "affix_health");
	private static final double TRAIL_REACH = 0.8;
	private static final List<String> NAMES = java.util.Arrays.stream(Affix.values()).map(Affix::getSerializedName).toList();

	/** Affixed entities loaded on this server (weak: unloaded or dead ones fall out). */
	private static final Set<LivingEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());
	private static final List<TrailFlame> TRAIL = new ArrayList<>();
	private static final List<Fuse> FUSES = new ArrayList<>();

	private MobAffixes() {
	}

	public static void init() {
		Affixes.setProvider(new MobAffixes());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && Affixes.of(living).isPresent()) {
				TRACKED.add(living);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MobAffixes::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(MobAffixes::afterDeath);
		ServerTickEvents.END_LEVEL_TICK.register(MobAffixes::tick);
	}

	private static MobsConfig.AffixConfig config() {
		return BlockSoulsMobs.CONFIG.affixes;
	}

	@Override
	public boolean apply(LivingEntity entity, Affix affix) {
		Affixes.attach(entity, affix);
		AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(HEALTH_MODIFIER);
			double extra = config().healthMultiplier - 1;
			if (extra != 0) {
				health.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, extra, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
			entity.setHealth(entity.getMaxHealth());
		}
		entity.setCustomName(name(entity, affix));
		entity.setCustomNameVisible(config().showName);
		TRACKED.add(entity);
		return true;
	}

	/** "Burning Brute": the affix prefix plus the entity type's name. */
	public static Component name(LivingEntity entity, Affix affix) {
		return Component.translatable("affix.blocksouls_mobs.name", Component.translatable(affix.translationKey()), entity.getType().getDescription());
	}

	@Override
	public Optional<Affix> roll(RandomSource random, int floor, Set<String> floorTags) {
		MobsConfig mobs = BlockSoulsMobs.CONFIG;
		return AffixRoll.roll(random.nextDouble(), random.nextDouble(), floor, floorTags.contains(WaveMobs.ELITE_AFFIXES),
				mobs.affixesInClassic, NAMES, mobs.affixes).map(Affix::byName);
	}

	/**
	 * Share of incoming damage a Frosted entity takes from this source (1 for anything else). Called from the
	 * damage mixin.
	 */
	public static float damageFactor(LivingEntity entity, DamageSource source) {
		if (Affixes.of(entity).orElse(null) != Affix.FROSTED) {
			return 1;
		}
		MobsConfig.AffixConfig.Frosted frosted = config().frosted;
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE)
				|| source.typeHolder().unwrapKey().map(key -> frosted.projectileDamageTypes.contains(key.identifier().toString())).orElse(false);
		return projectile ? (float) frosted.projectileDamageFactor : 1;
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (blocked || damageTaken <= 0 || !(source.getEntity() instanceof LivingEntity attacker) || victim.is(CommonTags.WAVE_MOBS)) {
			return;
		}
		Affix affix = Affixes.of(attacker).orElse(null);
		if (affix == Affix.BURNING) {
			victim.igniteForTicks(config().burning.igniteSeconds * 20);
		} else if (affix == Affix.FROSTED) {
			MobsConfig.AffixConfig.Frosted frosted = config().frosted;
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, frosted.slowSeconds * 20, frosted.slowAmplifier), attacker);
			if (victim.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5), victim.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
			}
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		TRACKED.remove(entity);
		if (Affixes.of(entity).orElse(null) == Affix.VOLATILE && entity.level() instanceof ServerLevel level) {
			FUSES.add(new Fuse(level, entity, entity.position(), Math.max(1, config().volatileAffix.fuseTicks)));
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), MobSounds.AFFIX_VOLATILE_FUSE, SoundSource.HOSTILE, 1.5f, 1f);
		}
	}

	private static void tick(ServerLevel level) {
		MobsConfig.AffixConfig cfg = config();
		long time = level.getGameTime();
		for (LivingEntity entity : new ArrayList<>(TRACKED)) {
			if (entity.isRemoved() || !entity.isAlive()) {
				TRACKED.remove(entity);
				continue;
			}
			if (entity.level() != level) {
				continue;
			}
			Affix affix = Affixes.of(entity).orElse(null);
			if (affix == Affix.BURNING) {
				int every = Math.max(1, cfg.burning.trailEveryTicks);
				if (entity.onGround() && (time + entity.getId()) % every == 0) {
					TRAIL.add(new TrailFlame(level, entity.position(), time + cfg.burning.trailLifeTicks));
				}
			} else if (affix == Affix.MAGNETIC) {
				magnetize(level, entity, cfg.magnetic, time);
			} else if (affix == null) {
				TRACKED.remove(entity);
			}
		}
		tickTrail(level, time, cfg.burning);
		tickFuses(level, cfg.volatileAffix);
	}

	private static void tickTrail(ServerLevel level, long time, MobsConfig.AffixConfig.Burning burning) {
		for (Iterator<TrailFlame> it = TRAIL.iterator(); it.hasNext();) {
			TrailFlame flame = it.next();
			if (flame.level != level) {
				continue;
			}
			if (time >= flame.expiresAt) {
				it.remove();
				continue;
			}
			Vec3 pos = flame.pos;
			if (time % 3 == 0) {
				level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.1, pos.z, 2, 0.2, 0.05, 0.2, 0.005);
			}
			AABB box = new AABB(pos, pos).inflate(TRAIL_REACH, 0.5, TRAIL_REACH).move(0, 0.5, 0);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
					e -> e.isAlive() && !e.is(CommonTags.WAVE_MOBS) && !e.isSpectator() && !e.isOnFire())) {
				entity.igniteForTicks(Math.max(20, burning.igniteSeconds * 10));
			}
		}
	}

	private static void tickFuses(ServerLevel level, MobsConfig.AffixConfig.Volatile volatileCfg) {
		for (Iterator<Fuse> it = FUSES.iterator(); it.hasNext();) {
			Fuse fuse = it.next();
			if (fuse.level != level) {
				continue;
			}
			fuse.ticksLeft--;
			if (fuse.ticksLeft % 4 == 0) {
				ring(level, fuse.pos, volatileCfg.radius);
			}
			if (fuse.ticksLeft <= 0) {
				it.remove();
				Shockwave.hit(level, fuse.source, fuse.pos, volatileCfg.radius, volatileCfg.damage, volatileCfg.knockback);
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, fuse.pos.x, fuse.pos.y + 0.5, fuse.pos.z, 1, 0, 0, 0, 0);
				level.playSound(null, fuse.pos.x, fuse.pos.y, fuse.pos.z, MobSounds.BLOATER_BURST, SoundSource.HOSTILE, 2f, 0.8f);
			}
		}
	}

	/** The blast's exact edge, so the telegraph never lies about where it reaches. */
	private static void ring(ServerLevel level, Vec3 center, double radius) {
		int points = Math.max(16, (int) (radius * 10));
		for (int i = 0; i < points; i++) {
			double angle = i * Math.PI * 2 / points;
			level.sendParticles(ParticleTypes.FLAME, center.x + Math.cos(angle) * radius, center.y + 0.15, center.z + Math.sin(angle) * radius,
					1, 0, 0, 0, 0);
		}
	}

	private static void magnetize(ServerLevel level, LivingEntity magnet, MobsConfig.AffixConfig.Magnetic magnetic, long time) {
		Vec3 center = magnet.position().add(0, magnet.getBbHeight() / 2, 0);
		AABB box = magnet.getBoundingBox().inflate(magnetic.radius);
		double radiusSq = magnetic.radius * magnetic.radius;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.distanceToSqr(center) <= radiusSq)) {
			Vec3 toward = center.subtract(item.position());
			if (toward.lengthSqr() > 0.25) {
				item.setDeltaMovement(item.getDeltaMovement().add(toward.normalize().scale(magnetic.itemPull)));
				item.syncVelocity = true;
			}
		}
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box,
				p -> p.getOwner() instanceof Player && p.distanceToSqr(center) <= radiusSq)) {
			deflect(projectile, center, magnetic.projectileDeflect);
		}
		if (time % 10 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 4, magnet.getBbWidth(), magnet.getBbHeight() / 2, magnet.getBbWidth(), 0.05);
		}
	}

	/** Pushes a projectile sideways, away from the magnet, so it bends around it. */
	static void deflect(Entity projectile, Vec3 center, double strength) {
		Vec3 velocity = projectile.getDeltaMovement();
		Vec3 away = projectile.position().subtract(center);
		// Only the part of "away" across the flight path: bends the shot without speeding it up or stopping it.
		Vec3 across = velocity.lengthSqr() < 1.0E-6 ? away : away.subtract(velocity.scale(away.dot(velocity) / velocity.lengthSqr()));
		if (across.lengthSqr() < 1.0E-6) {
			across = new Vec3(-velocity.z, 0, velocity.x);
		}
		if (across.lengthSqr() < 1.0E-6) {
			return;
		}
		projectile.setDeltaMovement(velocity.add(across.normalize().scale(strength)));
		projectile.syncVelocity = true;
	}

	/** Gametest hook: pending Volatile fuses (all levels). */
	public static int pendingFuses() {
		return FUSES.size();
	}

	private record TrailFlame(ServerLevel level, Vec3 pos, long expiresAt) {
	}

	private static final class Fuse {
		final ServerLevel level;
		final LivingEntity source;
		final Vec3 pos;
		int ticksLeft;

		Fuse(ServerLevel level, LivingEntity source, Vec3 pos, int ticksLeft) {
			this.level = level;
			this.source = source;
			this.pos = pos;
			this.ticksLeft = ticksLeft;
		}
	}
}
