package io.github.tmldude.blocksouls.game.player.boon.rule;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.game.player.boon.BuildConfig;
import io.github.tmldude.blocksouls.game.run.NightModifiers;
import io.github.tmldude.blocksouls.game.run.RunSystem;

/** Trinket rules. Trinkets with pure attribute/trait effects (Thick Coat, Lucky Coin, Iron Lung) need no rule. */
final class Trinkets {
	static final Identifier DAWN_CLOCK = BoonRules.id("dawn_clock");
	static final Identifier LAST_SHELL = BoonRules.id("last_shell");
	static final Identifier MAGNET_CORE = BoonRules.id("magnet_core");
	static final Identifier SIEGE_LENS = BoonRules.id("siege_lens");
	static final Identifier SOUL_JAR = BoonRules.id("soul_jar");

	private Trinkets() {
	}

	private static BuildConfig.Rules rules() {
		return BoonRules.config().rules;
	}

	static void register() {
		BoonRules.register(DAWN_CLOCK, new DawnClock());
		BoonRules.register(LAST_SHELL, new LastShell());
		BoonRules.register(MAGNET_CORE, new MagnetCore());
		BoonRules.register(SIEGE_LENS, new SiegeLens());
		BoonRules.register(SOUL_JAR, new SoulJar());
	}

	/** Prep phases are longer (the run's clock reads {@link BoonRules#prepTimeMultiplier}). */
	static final class DawnClock implements Rule {
		@Override
		public double prepTimeMultiplier(ServerPlayer owner, int level) {
			return rules().dawnClock.prepTimeMultiplier;
		}
	}

	/** Once per siege, a lethal hit leaves the owner standing at a sliver of health. */
	static final class LastShell implements Rule {
		/** Players whose shell is spent this siege. Dusk clears it. */
		private final Set<UUID> spent = new HashSet<>();

		@Override
		public boolean preventDown(ServerPlayer owner, int level) {
			if (!spent.add(owner.getUUID())) {
				return false;
			}
			owner.setHealth(Math.min(rules().lastShell.health, owner.getMaxHealth()));
			owner.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, rules().lastShell.immunityTicks, 4, false, false, true));
			owner.level().playSound(null, owner.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
			owner.sendSystemMessage(Component.translatable("message.blocksouls_game.last_shell").withStyle(ChatFormatting.GOLD));
			return true;
		}

		@Override
		public void onDusk(ServerPlayer owner, int level, int night) {
			spent.remove(owner.getUUID());
		}

		boolean isSpent(ServerPlayer owner) {
			return spent.contains(owner.getUUID());
		}

		/** Tests: a fresh siege. */
		void reset(ServerPlayer owner) {
			spent.remove(owner.getUUID());
		}
	}

	/** Dropped items fly to the owner. */
	static final class MagnetCore implements Rule {
		@Override
		public void tick(ServerPlayer owner, int level) {
			double radius = rules().magnetCore.radius;
			AABB box = owner.getBoundingBox().inflate(radius);
			List<ItemEntity> items = owner.level().getEntitiesOfClass(ItemEntity.class, box, item -> item.isAlive() && !item.hasPickUpDelay());
			Vec3 to = owner.position().add(0, 0.8, 0);
			for (ItemEntity item : items) {
				Vec3 pull = to.subtract(item.position());
				if (pull.lengthSqr() < 0.25) {
					continue;
				}
				item.setDeltaMovement(pull.normalize().scale(rules().magnetCore.speed));
				item.needsSync = true;
			}
		}
	}

	/** At dawn the owner learns which of the coming nights are Blood Moons. */
	static final class SiegeLens implements Rule {
		@Override
		public void onDawn(ServerPlayer owner, int level, int night) {
			owner.sendSystemMessage(forecast(night).withStyle(ChatFormatting.LIGHT_PURPLE));
		}

		static MutableComponent forecast(int night) {
			int ahead = Math.max(1, rules().siegeLens.nightsAhead);
			StringBuilder nights = new StringBuilder();
			for (int i = 1; i <= ahead; i++) {
				int n = night + i;
				if (!nights.isEmpty()) {
					nights.append(", ");
				}
				nights.append(n).append(NightModifiers.isBloodMoon(n, RunSystem.config()) ? " (Blood Moon)" : "");
			}
			return Component.translatable("message.blocksouls_game.siege_lens", nights.toString());
		}
	}

	/** Skipping a relic pays extra. */
	static final class SoulJar implements Rule {
		@Override
		public int skipSoulsBonus(ServerPlayer owner, int level) {
			return rules().soulJar.bonusSouls;
		}
	}
}
