package io.github.tmldude.blocksouls.abilities.kit;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.abilities.AbilitiesConfig;
import io.github.tmldude.blocksouls.abilities.effect.AbilityDamage;
import io.github.tmldude.blocksouls.abilities.effect.Effects;
import io.github.tmldude.blocksouls.abilities.effect.Targets;
import io.github.tmldude.blocksouls.abilities.sound.AbilitySounds;

/** The signature slot: one per origin. */
public final class Signatures {
	private Signatures() {
	}

	/** Drifter: slam straight down; on landing, hostiles around you take damage and fly up. On the ground it's instant. */
	public static boolean groundPound(CastContext ctx) {
		AbilitiesConfig.Signature.GroundPound cfg = ctx.config().signature.groundPound;
		ServerPlayer player = ctx.player();
		if (player.onGround()) {
			slam(ctx, cfg);
			return true;
		}
		Targets.launch(player, new Vec3(0, -cfg.slamSpeed, 0));
		player.fallDistance = 0;
		Effects.add(new Effects.Ticking() {
			int ticks;

			@Override
			public boolean tick() {
				if (!player.isAlive()) {
					return false;
				}
				player.fallDistance = 0;
				if (player.onGround() || ++ticks >= cfg.maxFallTicks) {
					slam(ctx, cfg);
					return false;
				}
				return true;
			}
		});
		return true;
	}

	private static void slam(CastContext ctx, AbilitiesConfig.Signature.GroundPound cfg) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 at = player.position();
		for (LivingEntity target : Targets.hostiles(level, at, cfg.radius, player)) {
			AbilityDamage.hurt(player, target, cfg.damage * ctx.power());
			if (target.isAlive()) {
				Vec3 away = target.position().subtract(at);
				Vec3 flat = away.lengthSqr() < 1e-4 ? Vec3.ZERO : new Vec3(away.x, 0, away.z).normalize().scale(0.4);
				Targets.launch(target, flat.add(0, cfg.knockup, 0));
			}
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 20, cfg.radius * 0.4, 0.1, cfg.radius * 0.4, 0.05);
		ctx.sound(at, AbilitySounds.SLAM, 1.2F, 0.9F);
	}

	/** Webslinger: a web line. A hostile in the line is yanked to you; a block pulls you to it. Nothing hit: fizzle. */
	public static boolean webPull(CastContext ctx) {
		AbilitiesConfig.Signature.WebPull cfg = ctx.config().signature.webPull;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Targets.Ray ray = Targets.ray(level, player, ctx.eye(), ctx.look(), cfg.range, 0.4);
		if (ray.entity() == null && !ray.hitBlock()) {
			return false;
		}
		Vec3 from = ctx.eye();
		Vec3 step = ray.end().subtract(from).normalize().scale(0.7);
		for (Vec3 p = from.add(step); p.distanceToSqr(ray.end()) > 0.5; p = p.add(step)) {
			level.sendParticles(ParticleTypes.ITEM_COBWEB, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		if (ray.entity() != null && ray.entity().getEntity() instanceof LivingEntity target) {
			Vec3 to = player.position().add(ctx.look().scale(1.5)).subtract(target.position());
			double distance = Math.max(1, to.length());
			Targets.launch(target, to.normalize().scale(Math.min(cfg.mobPull, distance * 0.35)).add(0, 0.3, 0));
			ctx.sound(target.position(), AbilitySounds.WEB, 1.0F, 1.2F);
		} else {
			Vec3 to = ray.end().subtract(player.position());
			double distance = Math.max(1, to.length());
			Targets.launch(player, to.normalize().scale(Math.min(cfg.selfPull, distance * 0.3)).add(0, 0.25, 0));
			ctx.sound(player.position(), AbilitySounds.WEB, 1.0F, 1.4F);
		}
		return true;
	}

	/** Ashborn: a cone of fire ahead; hostiles in it burn and take damage. */
	public static boolean emberWave(CastContext ctx) {
		AbilitiesConfig.Signature.EmberWave cfg = ctx.config().signature.emberWave;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 look = ctx.look();
		for (LivingEntity target : Targets.cone(level, player, look, cfg.range, cfg.coneDegrees)) {
			target.igniteForTicks(cfg.fireSeconds * 20);
			AbilityDamage.hurtAndPush(player, target, cfg.damage * ctx.power(), player.position(), 0.5);
		}
		Vec3 eye = ctx.eye();
		for (int i = 0; i < 40; i++) {
			Vec3 dir = Targets.spread(look, cfg.coneDegrees / 2, level.getRandom());
			double d = 0.8 + level.getRandom().nextDouble() * cfg.range;
			Vec3 p = eye.add(dir.scale(d));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		ctx.sound(eye, AbilitySounds.FIRE, 1.1F, 0.9F);
		return true;
	}

	/** Tidecaller: a geyser under you launches you and nearby allies (slow fall for the landing); hostiles take a little. */
	public static boolean geyser(CastContext ctx) {
		AbilitiesConfig.Signature.Geyser cfg = ctx.config().signature.geyser;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 at = player.position();
		for (ServerPlayer ally : Targets.allies(level, at, cfg.radius)) {
			Targets.launch(ally, new Vec3(ally.getDeltaMovement().x, cfg.launch, ally.getDeltaMovement().z));
			ally.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, cfg.slowFallTicks, 0, true, false), player);
			ally.fallDistance = 0;
		}
		for (LivingEntity target : Targets.hostiles(level, at, cfg.radius, player)) {
			AbilityDamage.hurt(player, target, cfg.mobDamage * ctx.power());
			if (target.isAlive()) {
				Targets.launch(target, new Vec3(target.getDeltaMovement().x, cfg.launch * 0.6, target.getDeltaMovement().z));
			}
		}
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 60, cfg.radius * 0.4, 1.0, cfg.radius * 0.4, 0.3);
		level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, at.x, at.y, at.z, 20, 0.5, 1.5, 0.5, 0.1);
		ctx.sound(at, AbilitySounds.GEYSER, 1.1F, 1.0F);
		return true;
	}

	/** Juggernaut: a shout that turns every hostile nearby onto you, shoves them back, and hardens you for a while. */
	public static boolean tauntSlam(CastContext ctx) {
		AbilitiesConfig.Signature.TauntSlam cfg = ctx.config().signature.tauntSlam;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 at = player.position();
		for (LivingEntity target : Targets.hostiles(level, at, cfg.radius, player)) {
			if (target instanceof Mob mob) {
				mob.setTarget(player);
			}
			AbilityDamage.hurtAndPush(player, target, cfg.damage * ctx.power(), at, cfg.knockback);
		}
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, cfg.resistanceTicks, cfg.resistanceAmplifier, false, true));
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, at.x, at.y + 1.5, at.z, 8, 0.6, 0.4, 0.6, 0.0);
		ctx.sound(at, AbilitySounds.SLAM, 1.3F, 0.7F);
		return true;
	}

	/** Gunslinger: six instant shots from the hip, no ammo, each its own ray with a little spread. */
	public static boolean fanTheHammer(CastContext ctx) {
		AbilitiesConfig.Signature.FanTheHammer cfg = ctx.config().signature.fanTheHammer;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = ctx.eye();
		for (int i = 0; i < cfg.shots; i++) {
			Vec3 dir = Targets.spread(ctx.look(), cfg.spreadDegrees, level.getRandom());
			Targets.Ray ray = Targets.ray(level, player, eye, dir, cfg.range, 0.3);
			if (ray.entity() != null && ray.entity().getEntity() instanceof LivingEntity target) {
				AbilityDamage.hurtAndPush(player, target, cfg.damage * ctx.power(), eye, 0.3);
				level.sendParticles(ParticleTypes.CRIT, ray.end().x, ray.end().y, ray.end().z, 4, 0.1, 0.1, 0.1, 0.2);
			}
			Vec3 step = dir.scale(1.0);
			for (Vec3 p = eye.add(step.scale(1.5)); p.distanceToSqr(eye) < ray.end().distanceToSqr(eye); p = p.add(step)) {
				level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
		}
		Vec3 muzzle = eye.add(ctx.look());
		level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y - 0.2, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
		ctx.sound(eye, AbilitySounds.FAN, 1.2F, 1.0F);
		return true;
	}

	/** Tinkerer: a scrap sentry at your feet for a few seconds, zapping the nearest hostile in range on a timer. */
	public static boolean overclock(CastContext ctx) {
		AbilitiesConfig.Signature.Overclock cfg = ctx.config().signature.overclock;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 at = player.position();
		ctx.sound(at, AbilitySounds.ZAP, 0.8F, 0.6F);
		Effects.zone(cfg.durationTicks, cfg.zapEveryTicks, tick -> {
			Vec3 top = at.add(0, 1.0, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 3, 0.2, 0.2, 0.2, 0.05);
			List<LivingEntity> hostiles = Targets.hostiles(level, top, cfg.zapRange, player);
			LivingEntity nearest = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity target : hostiles) {
				double d = target.distanceToSqr(top);
				if (d < best) {
					best = d;
					nearest = target;
				}
			}
			if (nearest == null) {
				return;
			}
			AbilityDamage.hurt(player, nearest, cfg.zapDamage * ctx.power());
			Vec3 to = nearest.getBoundingBox().getCenter();
			Vec3 step = to.subtract(top).normalize().scale(0.5);
			for (Vec3 p = top; p.distanceToSqr(to) > 0.3 && p.distanceToSqr(top) < cfg.zapRange * cfg.zapRange; p = p.add(step)) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			ctx.sound(top, AbilitySounds.ZAP, 0.7F, 1.3F);
		});
		return true;
	}

	/** Bloodbound: drain every hostile around you; a share of the damage heals you and allies in the radius. */
	public static boolean transfusion(CastContext ctx) {
		AbilitiesConfig.Signature.Transfusion cfg = ctx.config().signature.transfusion;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 at = player.position();
		double dealt = 0;
		for (LivingEntity target : Targets.hostiles(level, at, cfg.radius, player)) {
			dealt += AbilityDamage.hurt(player, target, cfg.drainDamage * ctx.power());
			Vec3 c = target.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.1);
		}
		List<ServerPlayer> allies = Targets.allies(level, at, cfg.radius);
		if (dealt > 0 && !allies.isEmpty()) {
			float each = (float) (dealt * cfg.healShare * ctx.healScale() / allies.size());
			for (ServerPlayer ally : allies) {
				ally.heal(each);
				level.sendParticles(ParticleTypes.HEART, ally.getX(), ally.getY() + 1.2, ally.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
			}
		}
		ctx.sound(at, AbilitySounds.DRAIN, 1.0F, 1.0F);
		return true;
	}
}
