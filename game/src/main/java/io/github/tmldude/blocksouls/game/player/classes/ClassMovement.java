package io.github.tmldude.blocksouls.game.player.classes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/**
 * The class signature movements that live game-side ({@link ClassRules}): the Warden's <b>slam landing</b> (a fall of
 * {@code slamMinFall}+ blocks knocks back and hurts enemies around the landing and costs no health) and the Arcanist's
 * <b>hover</b> (holding jump while falling floats the player, up to {@code hoverTicks} per airborne stint). Both are
 * server-side: the slam reads the server's fall distance, the hover refreshes a short Slow Falling the client
 * predicts on its own, so it stays smooth without pushing motion packets every tick.
 */
public final class ClassMovement {
	/** Fall distance at the end of the last tick, per player (the slam fires on the tick it drops back to 0). */
	private static final Map<UUID, Double> LAST_FALL = new HashMap<>();
	/** Hover ticks spent this airborne stint. */
	private static final Map<UUID, Integer> HOVER_USED = new HashMap<>();

	private ClassMovement() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
		// A slam landing is the fall damage's replacement: the fall distance is still set while the damage is dealt.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && forgivesFall(player, source)));
		ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> {
			LAST_FALL.remove(listener.player.getUUID());
			HOVER_USED.remove(listener.player.getUUID());
		});
	}

	private static boolean forgivesFall(ServerPlayer player, DamageSource source) {
		if (!source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		ClassRules rules = PlayerClasses.rules(player);
		return rules.slams() && player.fallDistance >= rules.slamMinFall();
	}

	static void tick(ServerPlayer player) {
		ClassRules rules = PlayerClasses.rules(player);
		UUID id = player.getUUID();
		if (!rules.slams() && !rules.hovers()) {
			LAST_FALL.remove(id);
			HOVER_USED.remove(id);
			return;
		}
		double last = LAST_FALL.getOrDefault(id, 0.0);
		boolean grounded = player.onGround() || player.isInWater() || player.onClimbable();
		if (rules.slams() && player.onGround() && last >= rules.slamMinFall() && !PlayerStatus.isDowned(player)) {
			slam(player, rules);
		}
		LAST_FALL.put(id, grounded ? 0.0 : player.fallDistance);

		if (rules.hovers()) {
			hover(player, rules, grounded);
		}
	}

	private static void slam(ServerPlayer player, ClassRules rules) {
		ServerLevel level = player.level();
		double radius = rules.slamRadius();
		AABB area = player.getBoundingBox().inflate(radius, 1.0, radius);
		DamageSource source = level.damageSources().playerAttack(player);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e instanceof Enemy && e.isAlive() && e.distanceToSqr(player) <= radius * radius)) {
			if (rules.slamDamage() > 0) {
				target.hurtServer(level, source, (float) rules.slamDamage());
			}
			if (rules.slamKnockback() > 0) {
				double dx = target.getX() - player.getX();
				double dz = target.getZ() - player.getZ();
				// knockback() pushes away from the given direction's opposite, so pass player-minus-target.
				target.knockback(rules.slamKnockback(), -dx, -dz, source, 0.0F);
			}
		}
		BlockState below = level.getBlockState(player.getOnPos());
		if (!below.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), player.getX(), player.getY() + 0.1, player.getZ(),
					40, radius / 3.0, 0.1, radius / 3.0, 0.15);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 0.9F, 0.9F);
	}

	private static void hover(ServerPlayer player, ClassRules rules, boolean grounded) {
		UUID id = player.getUUID();
		if (grounded || player.isFallFlying() || player.getAbilities().flying) {
			HOVER_USED.remove(id);
			return;
		}
		int used = HOVER_USED.getOrDefault(id, 0);
		boolean falling = player.getDeltaMovement().y < 0 || player.fallDistance > 0;
		if (used >= rules.hoverTicks() || !falling || !player.getLastClientInput().jump() || PlayerStatus.isDowned(player)) {
			return;
		}
		HOVER_USED.put(id, used + 1);
		int refresh = Math.max(1, PlayerClasses.config().hoverRefreshTicks);
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, refresh, 0, false, false, false));
		player.resetFallDistance();
		if (used % 4 == 0) {
			player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(), 2, 0.2, 0.0, 0.2, 0.01);
		}
	}

	/** Hover ticks this player has left in the current airborne stint (tests, HUD). */
	public static int hoverLeft(ServerPlayer player) {
		return Math.max(0, PlayerClasses.rules(player).hoverTicks() - HOVER_USED.getOrDefault(player.getUUID(), 0));
	}
}
