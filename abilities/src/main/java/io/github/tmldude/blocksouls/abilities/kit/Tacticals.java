package io.github.tmldude.blocksouls.abilities.kit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.abilities.AbilitiesConfig;
import io.github.tmldude.blocksouls.abilities.effect.AbilityDamage;
import io.github.tmldude.blocksouls.abilities.effect.Effects;
import io.github.tmldude.blocksouls.abilities.effect.Targets;
import io.github.tmldude.blocksouls.abilities.sound.AbilitySounds;

/** The tactical slot: frag, web bomb, fire wall, healing dart. */
public final class Tacticals {
	private Tacticals() {
	}

	/** A lobbed grenade: arcs, lands, and blasts hostiles in a radius (damage falls off to the edge; no block damage). */
	public static boolean frag(CastContext ctx) {
		AbilitiesConfig.Tactical.Frag cfg = ctx.config().tactical.frag;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		ctx.sound(ctx.eye(), AbilitySounds.THROW, 0.8F, 1.0F);
		Effects.lob(level, ctx.eye().add(ctx.look().scale(0.5)), ctx.look().scale(cfg.speed).add(0, 0.1, 0), cfg.gravity, cfg.fuseTicks,
				ParticleTypes.SMOKE, at -> {
					double radius = cfg.radius;
					for (LivingEntity target : Targets.hostiles(level, at, radius, player)) {
						double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(at));
						double share = 1.0 - (1.0 - cfg.edgeShare) * Math.min(1.0, distance / radius);
						AbilityDamage.hurtAndPush(player, target, cfg.damage * share * ctx.power(), at, cfg.knockback);
					}
					level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
					level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 12, 0.6, 0.4, 0.6, 0.02);
					ctx.sound(at, AbilitySounds.FRAG, 1.2F, 0.95F + level.getRandom().nextFloat() * 0.1F);
				});
		return true;
	}

	/** A lobbed web charge: where it lands, hostiles are slowed and dragged to the centre for a while. */
	public static boolean webBomb(CastContext ctx) {
		AbilitiesConfig.Tactical.WebBomb cfg = ctx.config().tactical.webBomb;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		ctx.sound(ctx.eye(), AbilitySounds.THROW, 0.8F, 1.3F);
		Effects.lob(level, ctx.eye().add(ctx.look().scale(0.5)), ctx.look().scale(cfg.speed).add(0, 0.1, 0), cfg.gravity, cfg.fuseTicks,
				ParticleTypes.ITEM_COBWEB, at -> {
					ctx.sound(at, AbilitySounds.WEB, 1.0F, 1.0F);
					Effects.zone(cfg.durationTicks, cfg.pulseTicks, tick -> {
						for (LivingEntity target : Targets.hostiles(level, at, cfg.radius, player)) {
							target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cfg.pulseTicks + 10, cfg.slownessAmplifier, true, false), player);
							Vec3 pull = at.subtract(target.position());
							if (pull.lengthSqr() > 0.25) {
								Vec3 velocity = target.getDeltaMovement().scale(0.5).add(pull.normalize().scale(cfg.pullStrength));
								Targets.launch(target, new Vec3(velocity.x, Math.min(velocity.y, 0.05), velocity.z));
							}
						}
						level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y + 0.3, at.z, 6, cfg.radius * 0.5, 0.3, cfg.radius * 0.5, 0.0);
					});
				});
		return true;
	}

	/** A line of fire across your facing, a couple of blocks ahead: hostiles crossing it burn and take pulsed damage. */
	public static boolean fireWall(CastContext ctx) {
		AbilitiesConfig.Tactical.FireWall cfg = ctx.config().tactical.fireWall;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 look = ctx.look();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1e-4) {
			flat = new Vec3(0, 0, 1);
		}
		flat = flat.normalize();
		Vec3 centre = ctx.feet().add(flat.scale(cfg.distanceAhead));
		Vec3 along = new Vec3(-flat.z, 0, flat.x);
		double half = cfg.length / 2;
		Vec3 a = centre.subtract(along.scale(half));
		Vec3 b = centre.add(along.scale(half));
		ctx.sound(centre, AbilitySounds.FIRE, 1.0F, 1.0F);
		Effects.zone(cfg.durationTicks, cfg.pulseTicks, tick -> {
			for (LivingEntity target : Targets.hostiles(level, centre, half + 1.5, player)) {
				if (distanceToSegment(target.position(), a, b) <= cfg.thickness) {
					target.igniteForTicks(cfg.fireSeconds * 20);
					AbilityDamage.hurt(player, target, cfg.damagePerPulse * ctx.power());
				}
			}
			for (double d = 0; d <= cfg.length; d += 0.5) {
				Vec3 p = a.add(along.scale(d));
				level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.2, p.z, 2, 0.15, 0.4, 0.15, 0.01);
			}
		});
		return true;
	}

	/** A dart that heals the first ally it hits; with no ally in the line it patches you up for a share. */
	public static boolean healingDart(CastContext ctx) {
		AbilitiesConfig.Tactical.HealingDart cfg = ctx.config().tactical.healingDart;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		double amount = cfg.heal * ctx.power() * ctx.healScale();
		ServerPlayer ally = Targets.allyRay(level, player, ctx.eye(), ctx.look(), cfg.range, 0.5);
		ServerPlayer healed = ally != null ? ally : player;
		float heal = (float) (ally != null ? amount : amount * cfg.selfShare);
		healed.heal(heal);
		Vec3 at = healed.position().add(0, healed.getBbHeight() * 0.6, 0);
		level.sendParticles(ParticleTypes.HEART, at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0.0);
		if (ally != null) {
			Vec3 from = ctx.eye();
			Vec3 to = ally.getEyePosition();
			Vec3 step = to.subtract(from).normalize().scale(0.8);
			for (Vec3 p = from.add(step); p.distanceToSqr(to) > 1; p = p.add(step)) {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
		}
		ctx.sound(at, AbilitySounds.HEAL, 0.9F, ally != null ? 1.0F : 0.8F);
		return true;
	}

	static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double t = ab.lengthSqr() < 1e-9 ? 0 : Math.clamp(p.subtract(a).dot(ab) / ab.lengthSqr(), 0.0, 1.0);
		Vec3 closest = a.add(ab.scale(t));
		return Math.sqrt((p.x - closest.x) * (p.x - closest.x) + (p.z - closest.z) * (p.z - closest.z) + Math.max(0, Math.abs(p.y - closest.y) - 1.0));
	}
}
