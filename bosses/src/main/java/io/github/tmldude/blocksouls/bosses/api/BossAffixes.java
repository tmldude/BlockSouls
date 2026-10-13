package io.github.tmldude.blocksouls.bosses.api;

import java.util.Optional;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.BossesConfig;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.mob.Affix;
import io.github.tmldude.blocksouls.common.mob.Affixes;

/**
 * Field-boss affixes (PLAN2 §3h) on the shared {@link Affixes} contract: Expedition floors from
 * {@code affixes.fromFloor} on roll one per field boss. The affix is stored with {@link Affixes#attach}; what it does
 * to a boss lives here (bosses own their combat), so a boss's affix works whether or not the mobs module is loaded.
 */
public final class BossAffixes {
	private BossAffixes() {
	}

	private static BossesConfig.AffixRules rules() {
		return BossEntity.framework().affixes;
	}

	/** The floor {@code boss} stands in (1-based), or 0 outside Expedition. */
	public static int floorOf(BossEntity boss) {
		if (!(boss.level() instanceof ServerLevel level)) {
			return 0;
		}
		return Floors.at(level.getServer(), level.dimension().identifier(), boss.blockPosition()).map(f -> f.floorNumber()).orElse(0);
	}

	/** Rolls and gives a fresh field boss its affix (Expedition only). Returns the affix, if any. */
	public static Optional<Affix> rollFor(BossEntity boss) {
		BossesConfig.AffixRules c = rules();
		int floor = floorOf(boss);
		if (!c.enabled || floor < c.fromFloor || boss.getRandom().nextDouble() >= c.chance) {
			return Optional.empty();
		}
		Optional<Affix> affix = Affixes.roll(boss.getRandom(), floor);
		if (affix.isEmpty()) {
			affix = Optional.of(pick(boss.getRandom()));
		}
		give(boss, affix.get());
		return affix;
	}

	static Affix pick(RandomSource random) {
		Affix[] all = Affix.values();
		return all[random.nextInt(all.length)];
	}

	/** Gives {@code boss} {@code affix} (or clears it for null). */
	public static void give(BossEntity boss, Affix affix) {
		Affixes.attach(boss, affix);
	}

	public static Optional<Affix> of(BossEntity boss) {
		return Affixes.of(boss);
	}

	/** The boss's name with its affix prefix ("Burning Ashwyrm"). */
	public static Component name(BossEntity boss, Component base) {
		return of(boss).<Component>map(a -> Component.translatable("affix.blocksouls_bosses.named", Component.translatable(a.translationKey()),
				base)).orElse(base);
	}

	static void tick(ServerLevel level, BossEntity boss) {
		Optional<Affix> affix = of(boss);
		if (affix.isEmpty()) {
			return;
		}
		BossesConfig.AffixRules c = rules();
		switch (affix.get()) {
			case BURNING -> {
				if (boss.tickCount % 3 == 0) {
					level.sendParticles(ParticleTypes.FLAME, boss.getX(), boss.getY() + 0.2, boss.getZ(), 3, boss.getBbWidth() / 3, 0.1,
							boss.getBbWidth() / 3, 0.01);
				}
			}
			case FROSTED -> {
				if (boss.tickCount % 5 == 0) {
					level.sendParticles(ParticleTypes.SNOWFLAKE, boss.getX(), boss.getY() + boss.getBbHeight() / 2, boss.getZ(), 3,
							boss.getBbWidth() / 2, boss.getBbHeight() / 3, boss.getBbWidth() / 2, 0.01);
				}
			}
			case VOLATILE -> {
				if (boss.tickCount % 6 == 0) {
					level.sendParticles(ParticleTypes.SMOKE, boss.getX(), boss.getY() + boss.getBbHeight() / 2, boss.getZ(), 2,
							boss.getBbWidth() / 3, boss.getBbHeight() / 3, boss.getBbWidth() / 3, 0.01);
				}
			}
			case MAGNETIC -> {
				if (c.magneticIntervalTicks > 0 && boss.tickCount % c.magneticIntervalTicks == 0) {
					pull(level, boss, c);
				}
			}
		}
	}

	/** Magnetic: tugs players' projectiles in range a little toward the boss's flank (off their line). */
	private static void pull(ServerLevel level, BossEntity boss, BossesConfig.AffixRules c) {
		AABB box = boss.getBoundingBox().inflate(c.magneticRadius);
		Vec3 centre = boss.position().add(0, boss.getBbHeight() / 2, 0);
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() instanceof ServerPlayer)) {
			Vec3 v = projectile.getDeltaMovement();
			Vec3 side = new Vec3(-v.z, 0, v.x).normalize();
			Vec3 to = centre.subtract(projectile.position());
			double sign = Math.signum(side.dot(to));
			projectile.setDeltaMovement(v.add(side.scale(-sign * c.magneticPull)));
			projectile.needsSync = true;
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 20, c.magneticRadius / 3, 1, c.magneticRadius / 3, 0.05);
	}

	static void onHit(ServerLevel level, BossEntity boss, LivingEntity victim) {
		Optional<Affix> affix = of(boss);
		if (affix.isEmpty()) {
			return;
		}
		BossesConfig.AffixRules c = rules();
		switch (affix.get()) {
			case BURNING -> victim.igniteForTicks(c.burningFireTicks);
			case FROSTED -> victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, c.frostedSlowTicks, 1), boss);
			default -> {
			}
		}
	}

	private record Fuse(ServerLevel level, Vec3 at, BossEntity boss, int when) {
	}

	private static final java.util.List<Fuse> FUSES = new java.util.ArrayList<>();

	/** Registers the fuse ticker. */
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (FUSES.isEmpty()) {
				return;
			}
			int now = server.getTickCount();
			java.util.Iterator<Fuse> it = FUSES.iterator();
			while (it.hasNext()) {
				Fuse fuse = it.next();
				if (fuse.level().getServer() != server) {
					it.remove();
				} else if (now >= fuse.when()) {
					it.remove();
					burst(fuse);
				} else if ((fuse.when() - now) % 5 == 0) {
					Telegraphs.circle(fuse.level(), ParticleTypes.FLAME, fuse.at(), rules().volatileRadius);
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> FUSES.clear());
	}

	static void onDeath(ServerLevel level, BossEntity boss) {
		if (of(boss).orElse(null) != Affix.VOLATILE) {
			return;
		}
		// A short fuse with a ring telegraph, then a burst that breaks no blocks.
		BossesConfig.AffixRules c = rules();
		Telegraphs.circle(level, ParticleTypes.FLAME, boss.position(), c.volatileRadius);
		FUSES.add(new Fuse(level, boss.position(), boss, level.getServer().getTickCount() + Math.max(20, c.volatileFuseTicks)));
	}

	private static void burst(Fuse fuse) {
		ServerLevel level = fuse.level();
		Vec3 at = fuse.at();
		BossesConfig.AffixRules c = rules();
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE,
				4.0f, 0.8f);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(c.volatileRadius))) {
			if (player.position().distanceTo(at) <= c.volatileRadius) {
				player.hurtServer(level, level.damageSources().explosion(fuse.boss(), fuse.boss()), (float) c.volatileDamage);
			}
		}
	}
}
