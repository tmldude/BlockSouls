package io.github.tmldude.blocksouls.game.player.boon.rule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.player.Stamina;
import io.github.tmldude.blocksouls.game.player.boon.BuildConfig;

/** The keystone rules: rule-changers keyed on a trigger tag. Numbers come from {@link BuildConfig.Rules}. */
final class Keystones {
	static final Identifier POWDER_BURN = BoonRules.id("powder_burn");
	static final Identifier RED_LEDGER = BoonRules.id("red_ledger");
	static final Identifier EMBER_STEP = BoonRules.id("ember_step");
	static final Identifier DEADEYE_WIND = BoonRules.id("deadeye_wind");
	static final Identifier STORM_CHORUS = BoonRules.id("storm_chorus");
	static final Identifier BLIGHT_TITHE = BoonRules.id("blight_tithe");
	static final Identifier UNDERTOW = BoonRules.id("undertow");

	private Keystones() {
	}

	private static BuildConfig.Rules rules() {
		return BoonRules.config().rules;
	}

	static void register() {
		BoonRules.register(POWDER_BURN, new PowderBurn());
		BoonRules.register(RED_LEDGER, new RedLedger());
		BoonRules.register(EMBER_STEP, new EmberStep());
		BoonRules.register(DEADEYE_WIND, new DeadeyeWind());
		BoonRules.register(STORM_CHORUS, new StormChorus());
		BoonRules.register(BLIGHT_TITHE, new BlightTithe());
		BoonRules.register(UNDERTOW, new Undertow());
	}

	/** Reloading releases a nova worth a share of the last few bullets' damage. */
	static final class PowderBurn implements Rule {
		private final Map<UUID, Deque<Float>> recent = new HashMap<>();

		@Override
		public void onHit(ServerPlayer owner, int level, LivingEntity target, float damage, boolean precision, boolean killed) {
			Deque<Float> bullets = recent.computeIfAbsent(owner.getUUID(), id -> new ArrayDeque<>());
			bullets.addLast(damage);
			while (bullets.size() > Math.max(1, rules().powderBurn.bullets)) {
				bullets.removeFirst();
			}
		}

		@Override
		public void onReloaded(ServerPlayer owner, int level, int roundsLoaded) {
			Deque<Float> bullets = recent.remove(owner.getUUID());
			if (bullets == null || bullets.isEmpty()) {
				return;
			}
			float total = 0;
			for (float bullet : bullets) {
				total += bullet;
			}
			float nova = (float) (total * rules().powderBurn.damageShare);
			if (nova <= 0) {
				return;
			}
			ServerLevel level0 = owner.level();
			for (Mob mob : RuleSupport.hostilesNear(owner, rules().powderBurn.radius)) {
				mob.hurtServer(level0, owner.damageSources().playerAttack(owner), nova);
			}
			Vec3 pos = owner.position();
			level0.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 6, 1.0, 0.5, 1.0, 0.0);
			level0.playSound(null, owner.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		}

		@Override
		public void onCleared(ServerPlayer player) {
			recent.remove(player.getUUID());
		}

		/** Damage remembered for the next reload (tests). */
		float remembered(ServerPlayer owner) {
			Deque<Float> bullets = recent.get(owner.getUUID());
			float total = 0;
			if (bullets != null) {
				for (float bullet : bullets) {
					total += bullet;
				}
			}
			return total;
		}
	}

	/** No natural regen; kills overheal as absorption. */
	static final class RedLedger implements Rule {
		@Override
		public boolean blocksNaturalRegen(ServerPlayer owner, int level) {
			return true;
		}

		@Override
		public void onHit(ServerPlayer owner, int level, LivingEntity target, float damage, boolean precision, boolean killed) {
			if (killed) {
				float max = (float) rules().redLedger.overhealMax;
				owner.setAbsorptionAmount(Math.min(max, owner.getAbsorptionAmount() + (float) rules().redLedger.absorptionPerKill));
			}
		}
	}

	/** The roll leaves a fire trail that burns and counts as a hit. */
	static final class EmberStep implements Rule {
		private final Map<UUID, Integer> trails = new HashMap<>();
		private final List<long[]> placedFire = new ArrayList<>();

		@Override
		public void onRolled(ServerPlayer owner, int level) {
			trails.put(owner.getUUID(), Math.max(1, rules().emberStep.trailTicks));
		}

		@Override
		public void tick(ServerPlayer owner, int level) {
			Integer left = trails.get(owner.getUUID());
			if (left == null) {
				return;
			}
			if (left <= 1) {
				trails.remove(owner.getUUID());
			} else {
				trails.put(owner.getUUID(), left - 1);
			}
			ServerLevel world = owner.level();
			for (Mob mob : RuleSupport.hostilesNear(owner, rules().emberStep.radius)) {
				mob.igniteForTicks(rules().emberStep.fireTicks);
				mob.hurtServer(world, owner.damageSources().playerAttack(owner), (float) rules().emberStep.damage);
			}
			BlockPos pos = owner.blockPosition();
			if (rules().emberStep.placeFire && world.isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(world, pos, Direction.UP)) {
				world.setBlock(pos, BaseFireBlock.getState(world, pos), 11);
				placedFire.add(new long[] {pos.asLong(), world.getGameTime() + rules().emberStep.fireTicks, world.dimension().identifier().hashCode()});
			}
			sweep(world);
		}

		/** Fire the trail placed goes out again after {@code fireTicks} (if nothing else burnt it first). */
		private void sweep(ServerLevel world) {
			long now = world.getGameTime();
			for (Iterator<long[]> it = placedFire.iterator(); it.hasNext();) {
				long[] entry = it.next();
				if (entry[1] > now) {
					continue;
				}
				it.remove();
				if (entry[2] == world.dimension().identifier().hashCode()) {
					BlockPos pos = BlockPos.of(entry[0]);
					if (world.getBlockState(pos).is(Blocks.FIRE)) {
						world.removeBlock(pos, false);
					}
				}
			}
		}

		@Override
		public void onCleared(ServerPlayer player) {
			trails.remove(player.getUUID());
		}

		boolean trailing(ServerPlayer owner) {
			return trails.containsKey(owner.getUUID());
		}
	}

	/** Precision hits refund stamina (movement installs the hook). */
	static final class DeadeyeWind implements Rule {
		@Override
		public void onHit(ServerPlayer owner, int level, LivingEntity target, float damage, boolean precision, boolean killed) {
			if (precision) {
				Stamina.add(owner, rules().deadeyeWind.staminaRefund * level);
			}
		}
	}

	/** Every n-th kill calls lightning on the nearest hostile. */
	static final class StormChorus implements Rule {
		private final Map<UUID, Integer> kills = new HashMap<>();

		@Override
		public void onHit(ServerPlayer owner, int level, LivingEntity target, float damage, boolean precision, boolean killed) {
			if (!killed) {
				return;
			}
			int count = kills.merge(owner.getUUID(), 1, Integer::sum);
			if (count < Math.max(1, rules().stormChorus.killsPerStrike)) {
				return;
			}
			kills.put(owner.getUUID(), 0);
			for (Mob mob : RuleSupport.hostilesNear(owner, rules().stormChorus.range)) {
				if (mob == target) {
					continue;
				}
				ServerLevel world = owner.level();
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(world, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.snapTo(mob.position());
					bolt.setCause(owner);
					world.addFreshEntity(bolt);
				}
				return;
			}
		}

		@Override
		public void onCleared(ServerPlayer player) {
			kills.remove(player.getUUID());
		}

		int kills(ServerPlayer owner) {
			return kills.getOrDefault(owner.getUUID(), 0);
		}
	}

	/** Precision hits wither; kills on withered targets heal. */
	static final class BlightTithe implements Rule {
		@Override
		public void onHit(ServerPlayer owner, int level, LivingEntity target, float damage, boolean precision, boolean killed) {
			if (killed && target.hasEffect(MobEffects.WITHER)) {
				owner.heal((float) rules().blightTithe.healOnKill);
			} else if (precision && !killed) {
				target.addEffect(new MobEffectInstance(MobEffects.WITHER, rules().blightTithe.witherTicks, rules().blightTithe.witherAmplifier), owner);
			}
		}
	}

	/** A finished reload pulls nearby hostiles in. */
	static final class Undertow implements Rule {
		@Override
		public void onReloaded(ServerPlayer owner, int level, int roundsLoaded) {
			for (Mob mob : RuleSupport.hostilesNear(owner, rules().undertow.radius)) {
				Vec3 pull = owner.position().subtract(mob.position());
				if (pull.lengthSqr() < 1.0E-4) {
					continue;
				}
				Vec3 impulse = pull.normalize().scale(rules().undertow.pullStrength);
				mob.push(impulse.x, 0.15 + impulse.y * 0.25, impulse.z);
			}
			owner.level().playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}
}
