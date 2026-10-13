package io.github.tmldude.blocksouls.game.run.director;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import io.github.tmldude.blocksouls.game.player.soul.Souls;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.EnchantSpec;

/**
 * The forge board's services besides the perk reroll (docs/decisions/v2-boards.md): enchant (the floor's metal
 * and souls → one random enchantment from the floor's list on the held gear) and upgrade (two of a piece → the
 * next tier of its chain, keeping the held piece's enchantments).
 */
final class ShopForge {
	private ShopForge() {
	}

	static @Nullable EnchantSpec spec(ServerPlayer player) {
		return ShopBoardRules.enchantSpec(ShopRecipes.data().enchants(), ShopRecipes.view(player));
	}

	/** The enchantments of {@code spec} that can go on {@code held} now (a new compatible one, or one below max). */
	static List<Holder<Enchantment>> fitting(ServerPlayer player, EnchantSpec spec, ItemStack held) {
		Registry<Enchantment> registry = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemEnchantments current = held.getEnchantments();
		List<Holder<Enchantment>> out = new ArrayList<>();
		for (String id : spec.enchantments()) {
			Identifier parsed = Identifier.tryParse(id);
			if (parsed == null) {
				continue;
			}
			registry.get(parsed).ifPresent(holder -> {
				if (!holder.value().canEnchant(held)) {
					return;
				}
				int level = current.getLevel(holder);
				boolean fits = level > 0 ? ShopBoardRules.enchantLevel(level, holder.value().getMaxLevel()) > 0
						: EnchantmentHelper.isEnchantmentCompatible(current.keySet(), holder);
				if (fits) {
					out.add(holder);
				}
			});
		}
		return out;
	}

	/** Enchants the held item. Null on success. */
	static @Nullable String enchant(ServerPlayer player) {
		EnchantSpec spec = spec(player);
		if (spec == null) {
			return "The forge has no enchantments on this floor";
		}
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			return "Hold the gear to enchant";
		}
		List<Holder<Enchantment>> fits = fitting(player, spec, held);
		if (fits.isEmpty()) {
			return "No enchantment of this floor fits that";
		}
		Item metal = Shop.item(spec.metal());
		if (metal == null) {
			return "The forge's metal isn't in this world";
		}
		String unpaid = unpaid(player, metal, spec.metalCount(), spec.souls());
		if (unpaid != null) {
			return "An enchantment costs " + unpaid;
		}
		pay(player, metal, spec.metalCount(), spec.souls());
		Holder<Enchantment> picked = fits.get(player.getRandom().nextInt(fits.size()));
		int level = ShopBoardRules.enchantLevel(held.getEnchantments().getLevel(picked), picked.value().getMaxLevel());
		EnchantmentHelper.updateEnchantments(held, enchantments -> enchantments.set(picked, level));
		Director.log(player.level().getServer(), DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " enchanted " + ShopRecipes.id(held.getItem())
				+ " with " + picked.getRegisteredName() + " " + level);
		return null;
	}

	/** Upgrades the held piece with a second one from the inventory. Null on success. */
	static @Nullable String upgrade(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			return "Hold the piece to upgrade";
		}
		String id = ShopRecipes.id(held.getItem());
		Item next = Shop.item(ShopBoardRules.nextTier(ShopRecipes.data().chains(), id));
		if (next == null) {
			return "That piece has no next tier";
		}
		if (Shop.has(player, held.getItem()) < 2) {
			return "Carry a second " + held.getHoverName().getString() + " to upgrade";
		}
		DirectorConfig.Shop config = Director.config().shop;
		EnchantSpec spec = spec(player);
		Item metal = spec == null ? null : Shop.item(spec.metal());
		int metalCount = metal == null ? 0 : config.upgradeMetalCount;
		String unpaid = unpaid(player, metal, metalCount, config.upgradeSouls);
		if (unpaid != null) {
			return "An upgrade costs " + unpaid;
		}
		pay(player, metal, metalCount, config.upgradeSouls);
		ShopRecipes.takeOne(player, id, held);
		ItemStack upgraded = new ItemStack(next);
		EnchantmentHelper.setEnchantments(upgraded, held.getEnchantments());
		player.setItemInHand(InteractionHand.MAIN_HAND, upgraded);
		Director.log(player.level().getServer(), DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " upgraded " + id + " to " + ShopRecipes.id(next));
		return null;
	}

	/** "3x Gunmetal Ingot and 8 souls" when the player can't pay, else null. */
	private static @Nullable String unpaid(ServerPlayer player, @Nullable Item metal, int metalCount, int souls) {
		boolean shortOf = (metal != null && metalCount > 0 && Shop.has(player, metal) < metalCount) || Souls.count(player) < souls;
		if (!shortOf) {
			return null;
		}
		String metalPart = metal == null || metalCount <= 0 ? "" : metalCount + "x " + new ItemStack(metal).getHoverName().getString() + " and ";
		return metalPart + souls + " souls";
	}

	private static void pay(ServerPlayer player, @Nullable Item metal, int metalCount, int souls) {
		if (metal != null && metalCount > 0) {
			player.getInventory().clearOrCountMatchingItems(stack -> stack.is(metal), false, metalCount, player.inventoryMenu.getCraftSlots());
		}
		if (souls > 0) {
			Souls.take(player, souls);
		}
	}
}
