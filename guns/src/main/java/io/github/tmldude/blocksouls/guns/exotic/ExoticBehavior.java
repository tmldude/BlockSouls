package io.github.tmldude.blocksouls.guns.exotic;

import java.util.List;
import java.util.Set;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.guns.shoot.Hitscan;

/**
 * An exotic gun's rule-breaking behavior. {@code GunServer} calls these around the normal shot; every method has a
 * no-op default so a behavior only overrides what it bends. The gun's JSON stats stay the only numbers per gun;
 * exotic-specific numbers live in {@code GunsConfig.Exotics}.
 */
public interface ExoticBehavior {
	/** Synergy tags this exotic adds to the shooter's build while equipped. */
	Set<Identifier> tags();

	/** Whether the gun may fire this tick (a venting Kindling Engine can't). Called before ammo is spent. */
	default boolean canFire(ServerPlayer player, ItemStack stack, long now) {
		return true;
	}

	/** The fire interval in ticks for this shot (ramping fire rate). */
	default double fireInterval(ServerPlayer player, ItemStack stack, long now, double base) {
		return base;
	}

	/**
	 * A bullet hit a living target and damage was applied.
	 *
	 * @param magAfter rounds left in the mag after this shot
	 */
	default void onHit(ServerPlayer player, ItemStack stack, LivingEntity target, double damage, boolean precision, boolean killed, int magAfter, long now) {
	}

	/** Every pellet of the shot has been traced and applied; {@code traces} are the bullet paths. */
	default void onShot(ServerPlayer player, ItemStack stack, List<Hitscan.Trace> traces, int magAfter, long now) {
	}
}
