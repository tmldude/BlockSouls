package io.github.tmldude.blocksouls.abilities.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.abilities.AbilityIds;
import io.github.tmldude.blocksouls.common.CommonTags;

/**
 * Ability damage: type {@code blocksouls_abilities:ability} (bypasses the hurt cooldown, no vanilla knockback — each
 * ability applies its own), with the caster as attacker so kill credit, on-kill effects and ultimate charge work.
 */
public final class AbilityDamage {
	public static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, AbilityIds.id("ability"));

	private AbilityDamage() {
	}

	public static DamageSource source(ServerPlayer caster) {
		Holder<DamageType> type = caster.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TYPE);
		return new DamageSource(type, caster, caster);
	}

	/** Hurts the target; returns the damage dealt (0 if the hit was refused). */
	public static float hurt(ServerPlayer caster, LivingEntity target, double amount) {
		ServerLevel level = caster.level();
		float before = target.getHealth();
		if (!target.hurtServer(level, source(caster), (float) Math.max(0, amount))) {
			return 0;
		}
		return Math.max(0, before - target.getHealth());
	}

	/** Hurts and knocks the target away from {@code from}. */
	public static float hurtAndPush(ServerPlayer caster, LivingEntity target, double amount, Vec3 from, double knockback) {
		float dealt = hurt(caster, target, amount);
		if (knockback > 0 && target.isAlive()) {
			Vec3 away = target.position().subtract(from);
			if (away.lengthSqr() < 1e-4) {
				away = new Vec3(0, 0, 1);
			}
			target.knockback(knockback, -away.x, -away.z, source(caster), dealt);
			target.syncVelocity = true;
		}
		return dealt;
	}

	public static boolean isElite(LivingEntity target) {
		return target.is(CommonTags.ELITES);
	}

	public static boolean isBoss(LivingEntity target) {
		return target.is(CommonTags.BOSSES);
	}
}
