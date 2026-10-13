package io.github.tmldude.blocksouls.guns.item;

import java.util.List;
import java.util.function.BiConsumer;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.guns.exotic.ExoticBehavior;
import io.github.tmldude.blocksouls.guns.exotic.Exotics;
import io.github.tmldude.blocksouls.guns.logic.Perk;

/**
 * Guns' {@code SynergyTags.Source}: the perks of the gun in the main hand, and an exotic's own tags, count toward
 * the player's build (one stack each). Works on both sides from the item's components.
 */
public final class GunSynergy {
	private GunSynergy() {
	}

	/** Which synergy tags a perk carries. */
	public static List<Identifier> tags(Perk perk) {
		return switch (perk) {
			case OUTLAW -> List.of(SynergyTags.ON_PRECISION, SynergyTags.ON_RELOAD);
			case RAMPAGE -> List.of(SynergyTags.ON_KILL);
			case EXPLOSIVE_ROUNDS -> List.of(SynergyTags.ON_PRECISION, SynergyTags.EXPLOSION);
			case VORPAL -> List.of(SynergyTags.KINETIC);
		};
	}

	public static void collect(Player player, BiConsumer<Identifier, Integer> sink) {
		ItemStack stack = player.getMainHandItem();
		GunItem gun = GunItem.of(stack);
		if (gun == null) {
			return;
		}
		for (Perk perk : GunRolls.perks(stack)) {
			for (Identifier tag : tags(perk)) {
				sink.accept(tag, 1);
			}
		}
		ExoticBehavior exotic = Exotics.of(gun.weaponId());
		if (exotic != null) {
			for (Identifier tag : exotic.tags()) {
				sink.accept(tag, 1);
			}
		}
	}
}
