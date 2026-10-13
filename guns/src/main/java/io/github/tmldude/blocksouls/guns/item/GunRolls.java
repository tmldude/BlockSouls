package io.github.tmldude.blocksouls.guns.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.logic.Perk;
import io.github.tmldude.blocksouls.guns.logic.Rolls;

/** Reads and rolls a gun stack's rarity and perks. */
public final class GunRolls {
	private GunRolls() {
	}

	/** Loot tier for {@code roll_perks}; {@code null} means crafted odds. */
	public static double[] odds(String tier) {
		GunsConfig.Rarity config = BlockSoulsGuns.CONFIG.rarity;
		if (tier == null) {
			return config.craftedOdds;
		}
		return switch (tier) {
			case "rare" -> config.rareTierOdds;
			case "legendary" -> config.legendaryTierOdds;
			default -> config.commonTierOdds;
		};
	}

	public static boolean canRoll(ItemStack stack) {
		return stack.getItem() instanceof GunItem gun && gun.stats().perks();
	}

	/**
	 * Rolls rarity and perks onto a gun that can have them; other stacks are left alone. Exotic guns ignore the odds:
	 * they are always {@link GunRarity#EXOTIC} with its perk count.
	 */
	public static void roll(ItemStack stack, double[] odds, RandomSource random) {
		if (!canRoll(stack)) {
			return;
		}
		Random rng = new Random(random.nextLong());
		GunRarity rarity = stack.getItem() instanceof ExoticGunItem ? GunRarity.EXOTIC : Rolls.rarity(odds, rng);
		set(stack, rarity, Rolls.perks(List.of(Perk.values()), rarity.perkCount(), rng));
	}

	public static void set(ItemStack stack, GunRarity rarity, List<Perk> perks) {
		List<Identifier> ids = new ArrayList<>();
		for (Perk perk : perks) {
			ids.add(id(perk));
		}
		stack.set(GunComponents.RARITY, rarity);
		stack.set(GunComponents.PERKS, List.copyOf(ids));
		stack.set(DataComponents.RARITY, switch (rarity) {
			case COMMON -> Rarity.COMMON;
			case RARE -> Rarity.RARE;
			case LEGENDARY, EXOTIC -> Rarity.EPIC;
		});
	}

	public static boolean isRolled(ItemStack stack) {
		return stack.has(GunComponents.RARITY);
	}

	public static GunRarity rarity(ItemStack stack) {
		return stack.getOrDefault(GunComponents.RARITY, GunRarity.COMMON);
	}

	public static List<Perk> perks(ItemStack stack) {
		List<Perk> perks = new ArrayList<>();
		for (Identifier id : stack.getOrDefault(GunComponents.PERKS, List.<Identifier>of())) {
			if (BlockSoulsGuns.MOD_ID.equals(id.getNamespace())) {
				Perk.byId(id.getPath()).ifPresent(perks::add);
			}
		}
		return perks;
	}

	public static boolean hasPerk(ItemStack stack, Perk perk) {
		return stack.getOrDefault(GunComponents.PERKS, List.<Identifier>of()).contains(id(perk));
	}

	public static double damageMult(GunRarity rarity) {
		GunsConfig.Rarity config = BlockSoulsGuns.CONFIG.rarity;
		return switch (rarity) {
			case COMMON -> config.commonDamageMult;
			case RARE -> config.rareDamageMult;
			case LEGENDARY -> config.legendaryDamageMult;
			case EXOTIC -> config.exoticDamageMult;
		};
	}

	public static Identifier id(Perk perk) {
		return Identifier.fromNamespaceAndPath(BlockSoulsGuns.MOD_ID, perk.id());
	}
}
