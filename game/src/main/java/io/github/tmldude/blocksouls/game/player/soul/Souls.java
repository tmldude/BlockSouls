package io.github.tmldude.blocksouls.game.player.soul;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.common.CommonIds;

/** Souls are an item ({@code CommonIds.SOUL}) in the inventory; these helpers count, pay and take them. */
public final class Souls {
	private Souls() {
	}

	public static Item item() {
		return BuiltInRegistries.ITEM.getValue(CommonIds.SOUL);
	}

	public static int count(ServerPlayer player) {
		return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item()), true, 0, player.inventoryMenu.getCraftSlots());
	}

	/** Hands over souls; what doesn't fit drops at the player's feet. */
	public static void give(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return;
		}
		ItemStack stack = new ItemStack(item(), amount);
		if (!player.addItem(stack) && !stack.isEmpty()) {
			player.spawnAtLocation(player.level(), stack);
		}
	}

	/** Removes up to {@code amount} souls; returns how many were actually taken. */
	public static int take(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return 0;
		}
		return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item()), false, amount, player.inventoryMenu.getCraftSlots());
	}
}
