package io.github.tmldude.blocksouls.game.player.classes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.boon.Boon;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.BoonEffect;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;

/**
 * Applies and removes what a class does to a player. Classes are a maintained layer: boons clear and rebuild
 * traits on their own refresh, going down clears infinite effects, milk clears everything, so {@link #apply} is
 * idempotent and runs again after each of those (and on a timer) to put the class's share back.
 */
final class ClassEffects {
	static final Identifier BOSS_HEARTS = Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "class/boss_hearts");

	private ClassEffects() {
	}

	static void apply(ServerPlayer player, PlayerClass playerClass, ClassState state, ClassesConfig config) {
		var effects = playerClass.effects();
		Map<Holder<MobEffect>, Integer> permanent = new HashMap<>();
		Map<Holder<MobEffect>, Integer> conditional = new HashMap<>();
		Map<Identifier, Integer> traits = new HashMap<>();

		for (int i = 0; i < effects.size(); i++) {
			switch (effects.get(i)) {
				case ClassEffect.Effect e -> effect(e.effect()).ifPresent(holder -> {
					if (e.when() == ClassEffect.When.ALWAYS) {
						permanent.merge(holder, e.amplifier(), Math::max);
					} else if (holds(player, e.when())) {
						conditional.merge(holder, e.amplifier(), Math::max);
					}
				});
				case ClassEffect.Attribute a -> {
					AttributeInstance attribute = attribute(player, a.attribute());
					AttributeModifier.Operation operation = operation(a.operation());
					if (attribute != null && operation != null) {
						attribute.addOrReplacePermanentModifier(new AttributeModifier(modifierId(playerClass, i), a.amount(), operation));
					}
				}
				case ClassEffect.Trait t -> traits.merge(Identifier.parse(t.trait()), t.level(), Integer::sum);
			}
		}

		permanent.forEach((holder, amplifier) -> {
			MobEffectInstance current = player.getEffect(holder);
			if (current == null || !current.isInfiniteDuration() || current.getAmplifier() < amplifier) {
				player.addEffect(new MobEffectInstance(holder, MobEffectInstance.INFINITE_DURATION, amplifier, true, false, true));
			}
		});
		int hold = Math.max(1, config.reapplyIntervalTicks) + Math.max(0, config.conditionalEffectGraceTicks);
		conditional.forEach((holder, amplifier) -> {
			MobEffectInstance current = player.getEffect(holder);
			if (current == null || current.getAmplifier() < amplifier || (!current.isInfiniteDuration() && current.getDuration() < hold)) {
				player.addEffect(new MobEffectInstance(holder, hold, amplifier, true, false, true));
			}
		});

		// Traits: the class's levels sit on top of the boons' (which rebuild the whole map on their refresh).
		if (!traits.isEmpty()) {
			Map<Identifier, Integer> boonLevels = boonTraitLevels(player);
			traits.forEach((trait, level) -> {
				int wanted = boonLevels.getOrDefault(trait, 0) + level;
				if (PlayerTraits.level(player, trait) != wanted) {
					PlayerTraits.set(player, trait, wanted);
				}
			});
		}

		// Bloodbound: +hearts per boss kill (capped).
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			int hearts = playerClass.rules().heartsPerBossKill() * Math.min(state.bossKills(), Math.max(0, config.bossKillHeartsCap));
			if (hearts > 0) {
				health.addOrReplacePermanentModifier(new AttributeModifier(BOSS_HEARTS, hearts * 2.0, AttributeModifier.Operation.ADD_VALUE));
			} else {
				health.removeModifier(BOSS_HEARTS);
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	static void remove(ServerPlayer player, PlayerClass playerClass) {
		var effects = playerClass.effects();
		for (int i = 0; i < effects.size(); i++) {
			switch (effects.get(i)) {
				case ClassEffect.Effect e -> effect(e.effect()).ifPresent(holder -> {
					MobEffectInstance current = player.getEffect(holder);
					if (current != null && (current.isInfiniteDuration() || e.when() != ClassEffect.When.ALWAYS)) {
						player.removeEffect(holder);
					}
				});
				case ClassEffect.Attribute a -> {
					AttributeInstance attribute = attribute(player, a.attribute());
					if (attribute != null) {
						attribute.removeModifier(modifierId(playerClass, i));
					}
				}
				case ClassEffect.Trait t -> {
					Identifier trait = Identifier.parse(t.trait());
					PlayerTraits.set(player, trait, boonTraitLevels(player).getOrDefault(trait, 0));
				}
			}
		}
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(BOSS_HEARTS);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static boolean holds(ServerPlayer player, ClassEffect.When when) {
		return switch (when) {
			case ALWAYS -> true;
			case IN_WATER -> player.isInWater();
			case IN_RAIN -> player.isInWaterOrRain() && !player.isInWater();
			case IN_LAVA -> player.isInLava();
			case ON_FIRE -> player.isOnFire();
		};
	}

	/** Trait levels the player's boons grant (the boons module's own rule: trait level × boon level, summed). */
	static Map<Identifier, Integer> boonTraitLevels(ServerPlayer player) {
		Map<Identifier, Integer> levels = new HashMap<>();
		PlayerBoons.owned(player).forEach((id, level) -> {
			Boon boon = BoonCatalog.get(id);
			if (boon == null) {
				return;
			}
			for (BoonEffect effect : boon.effects()) {
				if (effect instanceof BoonEffect.Trait t) {
					levels.merge(Identifier.parse(t.trait()), t.level() * level, Integer::sum);
				}
			}
		});
		return levels;
	}

	static Identifier modifierId(PlayerClass playerClass, int index) {
		Identifier id = Identifier.parse(playerClass.id());
		return Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "class/" + id.getNamespace() + "/" + id.getPath() + "/" + index);
	}

	private static Optional<Holder.Reference<MobEffect>> effect(String id) {
		return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id));
	}

	@SuppressWarnings("unchecked")
	private static AttributeInstance attribute(ServerPlayer player, String id) {
		return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id))
				.map(holder -> player.getAttribute((Holder<Attribute>) holder))
				.orElse(null);
	}

	private static AttributeModifier.Operation operation(String name) {
		for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
			if (operation.getSerializedName().equals(name)) {
				return operation;
			}
		}
		return null;
	}
}
