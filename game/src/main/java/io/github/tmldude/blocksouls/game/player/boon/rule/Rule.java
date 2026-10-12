package io.github.tmldude.blocksouls.game.player.boon.rule;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Behavior a boon, curse or trinket references with {@code {"type": "rule", "rule": "<id>"}}. Every hook gets the
 * owner and the level the definition is owned at (curses and trinkets: 1). Hooks are only called while the
 * definition is owned; {@link BoonRules} fans the game's events out to them.
 */
public interface Rule {
	/** One of the owner's shots hit ({@code CombatEvents.HIT}, fired by guns). */
	default void onHit(ServerPlayer owner, int level, LivingEntity target, float damage, boolean precision, boolean killed) {
	}

	/** The owner's gun finished reloading ({@code CombatEvents.RELOADED}). */
	default void onReloaded(ServerPlayer owner, int level, int roundsLoaded) {
	}

	/** The owner started a dodge roll ({@code MovementEvents.ROLLED}). */
	default void onRolled(ServerPlayer owner, int level) {
	}

	/** Every server tick while owned. */
	default void tick(ServerPlayer owner, int level) {
	}

	/** Someone was revived; called for every online owner, who decides whether it concerns them. */
	default void onRevived(ServerPlayer owner, int level, ServerPlayer revived, @Nullable ServerPlayer reviver) {
	}

	/**
	 * The owner took a hit that would down them. Return true to keep them standing (the rule restores health);
	 * runs before the downed system sees the death.
	 */
	default boolean preventDown(ServerPlayer owner, int level) {
		return false;
	}

	/** The owner is about to go down (no rule prevented it); boons and curses are still owned at this point. */
	default void onGoingDown(ServerPlayer owner, int level) {
	}

	/** Multiplier on healing the owner receives (curses below 1). */
	default float healMultiplier(ServerPlayer owner, int level) {
		return 1.0F;
	}

	/** Whether the owner's natural (food) regeneration is switched off. */
	default boolean blocksNaturalRegen(ServerPlayer owner, int level) {
		return false;
	}

	/** Extra souls when the owner declines a relic. */
	default int skipSoulsBonus(ServerPlayer owner, int level) {
		return 0;
	}

	/** Multiplier the run's day phase may apply while the owner is online (the party's max applies). */
	default double prepTimeMultiplier(ServerPlayer owner, int level) {
		return 1.0;
	}

	default void onDusk(ServerPlayer owner, int level, int night) {
	}

	default void onDawn(ServerPlayer owner, int level, int night) {
	}

	/** Change the traits the owner's build computes before they're applied (e.g. share a partner's). */
	default void adjustTraits(ServerPlayer owner, int level, Map<Identifier, Integer> traits) {
	}

	/** The owner lost their boons and curses (went down, run start); drop per-player state that belonged to them. */
	default void onCleared(ServerPlayer player) {
	}
}
