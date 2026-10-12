package io.github.tmldude.blocksouls.abilities.kit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.abilities.AbilitiesConfig;
import io.github.tmldude.blocksouls.abilities.AbilityServer;
import io.github.tmldude.blocksouls.abilities.effect.AbilityDamage;
import io.github.tmldude.blocksouls.abilities.effect.Effects;
import io.github.tmldude.blocksouls.abilities.effect.Targets;
import io.github.tmldude.blocksouls.abilities.sound.AbilitySounds;

/** The ultimate slot: three run-defining buttons. */
public final class Ultimates {
	private Ultimates() {
	}

	/** A storm overhead for a few seconds: bolts keep striking random hostiles around you. */
	public static boolean stormBarrage(CastContext ctx) {
		AbilitiesConfig.Ultimate.StormBarrage cfg = ctx.config().ultimate.stormBarrage;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		ctx.sound(player.position(), AbilitySounds.STORM, 1.5F, 0.8F);
		Effects.zone(cfg.durationTicks, cfg.strikeEveryTicks, tick -> {
			if (!player.isAlive()) {
				return;
			}
			Vec3 at = player.position();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 6, at.z, 6, cfg.radius * 0.5, 1.0, cfg.radius * 0.5, 0.1);
			List<LivingEntity> hostiles = Targets.hostiles(level, at, cfg.radius, player);
			if (hostiles.isEmpty()) {
				return;
			}
			LivingEntity target = hostiles.get(level.getRandom().nextInt(hostiles.size()));
			Vec3 c = target.getBoundingBox().getCenter();
			AbilityDamage.hurtAndPush(player, target, cfg.damage * ctx.power(), c.add(0, -1, 0), 0.4);
			for (double y = c.y + 8; y > c.y - 0.5; y -= 0.5) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, y, c.z, 2, 0.1, 0.1, 0.1, 0.02);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.3);
			ctx.sound(c, AbilitySounds.STORM, 1.0F, 1.1F + level.getRandom().nextFloat() * 0.3F);
		});
		return true;
	}

	/** A few seconds of being untouchable and fast, swinging at everything in reach on a timer. */
	public static boolean meleeFrenzy(CastContext ctx) {
		AbilitiesConfig.Ultimate.MeleeFrenzy cfg = ctx.config().ultimate.meleeFrenzy;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		AbilityServer.setInvulnerable(player, cfg.durationTicks);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, cfg.durationTicks, cfg.speedAmplifier, false, true));
		ctx.sound(player.position(), AbilitySounds.FRENZY, 1.3F, 1.0F);
		Effects.zone(cfg.durationTicks, cfg.strikeEveryTicks, tick -> {
			if (!player.isAlive()) {
				return;
			}
			Vec3 at = player.position().add(0, 1, 0);
			Vec3 look = player.getLookAngle();
			boolean hit = false;
			for (LivingEntity target : Targets.hostiles(level, at, cfg.reach, player)) {
				Vec3 to = target.getBoundingBox().getCenter().subtract(at);
				if (to.lengthSqr() > 0.5 && to.normalize().dot(look) < -0.2) {
					continue; // behind you
				}
				AbilityDamage.hurtAndPush(player, target, cfg.damage * ctx.power(), player.position(), cfg.knockback);
				hit = true;
			}
			if (hit) {
				Vec3 swing = at.add(look.scale(1.2));
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, swing.x, swing.y, swing.z, 1, 0, 0, 0, 0);
				ctx.sound(at, AbilitySounds.FRENZY, 0.8F, 1.4F + level.getRandom().nextFloat() * 0.3F);
			}
		});
		return true;
	}

	/** Everyone in the party gets an absorption shield that holds for a while, then fades. */
	public static boolean partyOvershield(CastContext ctx) {
		AbilitiesConfig.Ultimate.PartyOvershield cfg = ctx.config().ultimate.partyOvershield;
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		float amount = (float) (cfg.absorption * ctx.power() * ctx.healScale());
		List<ServerPlayer> shielded = new ArrayList<>(Targets.allies(level, player.position(), cfg.radius));
		for (ServerPlayer ally : shielded) {
			ally.setAbsorptionAmount(Math.max(ally.getAbsorptionAmount(), amount));
			level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + 1, ally.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
			ctx.sound(ally.position(), AbilitySounds.SHIELD, 1.0F, 1.0F);
		}
		Effects.after(cfg.durationTicks, () -> {
			for (ServerPlayer ally : shielded) {
				if (ally.isAlive()) {
					ally.setAbsorptionAmount(Math.max(0, ally.getAbsorptionAmount() - amount));
				}
			}
		});
		return true;
	}
}
