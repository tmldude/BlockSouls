package io.github.tmldude.blocksouls.game.run.director;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import io.github.tmldude.blocksouls.common.player.WeaponHooks;
import io.github.tmldude.blocksouls.common.synergy.BuildHooks;
import io.github.tmldude.blocksouls.game.player.soul.Souls;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;

/**
 * The shopkeeper's trade board (PLAN2 §3c): this floor's offers on the top row, each one's price below it as
 * "where to get it" icons (a scrap node, trees, mobs), and the fixed services (curse purge, perk reroll) plus
 * the shopkeeper's remark about the threat on the bottom row. Click a product to buy it.
 */
public final class Shop {
	static final int ROWS = 6;
	static final int PRICE_ROWS = 3;
	static final int PURGE_SLOT = 45;
	static final int REROLL_SLOT = 47;
	static final int REMARK_SLOT = 53;

	private Shop() {
	}

	static void init() {
	}

	private static DirectorConfig.Shop config() {
		return Director.config().shop;
	}

	/** Opens the board for {@code player}. Null on success. */
	static @Nullable String open(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		List<DirectorConfig.Offer> board = DirectorRules.stock(config().pool, Math.max(1, DirectorHooks.actOrdinal(server)), DirectorData.get(server).seed, config().slots);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Board(id, inventory, board, remark(server)),
				Component.translatable("container.blocksouls_game.shop")));
		return null;
	}

	/** The shopkeeper's line about how the Director reads the party. */
	static Component remark(MinecraftServer server) {
		String key = switch (Director.mood()) {
			case LULL -> "lull";
			case SPIKE -> "spike";
			default -> Director.stress() > 0.6 ? "battered" : Director.stress() < 0.25 ? "calm" : "steady";
		};
		return Component.translatable("message.blocksouls_game.shop.remark." + key);
	}

	static @Nullable Item item(@Nullable String id) {
		Identifier parsed = id == null ? null : Identifier.tryParse(id);
		return parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).filter(item -> item != Items.AIR).orElse(null);
	}

	static ItemStack productIcon(DirectorConfig.Offer offer) {
		ItemStack stack;
		if (offer.loot != null && !offer.loot.isBlank()) {
			stack = new ItemStack(Items.CHEST);
			stack.set(DataComponents.CUSTOM_NAME, Component.translatable("container.blocksouls_game.shop.loot", offer.loot));
		} else {
			Item product = item(offer.item);
			stack = product == null ? ItemStack.EMPTY : new ItemStack(product, Math.max(1, Math.min(offer.count, 99)));
		}
		if (offer.rare && !stack.isEmpty()) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	/** A price as where to get it: the source's icon, the count, the item and the source in the name. */
	static ItemStack priceIcon(DirectorConfig.Price price) {
		Item paid = item(price.item);
		if (paid == null) {
			return ItemStack.EMPTY;
		}
		DirectorConfig.Source source = config().sources.get(price.item);
		Item icon = source == null ? null : item(source.icon);
		ItemStack stack = new ItemStack(icon != null ? icon : paid, Math.max(1, Math.min(price.count, 99)));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("container.blocksouls_game.shop.price", price.count, new ItemStack(paid).getHoverName()));
		if (source != null && source.label != null) {
			stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("container.blocksouls_game.shop.source", source.label))));
		}
		return stack;
	}

	static int has(ServerPlayer player, Item item) {
		return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), true, 0, player.inventoryMenu.getCraftSlots());
	}

	/** Buys {@code offer}: every price must be in the inventory; then it's taken and the product given. Null on success. */
	static @Nullable String buy(ServerPlayer player, DirectorConfig.Offer offer) {
		for (DirectorConfig.Price price : offer.prices) {
			Item item = item(price.item);
			if (item == null || has(player, item) < price.count) {
				return "Not enough " + (item == null ? price.item : new ItemStack(item).getHoverName().getString());
			}
		}
		for (DirectorConfig.Price price : offer.prices) {
			Item item = item(price.item);
			player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), false, price.count, player.inventoryMenu.getCraftSlots());
		}
		ServerLevel level = (ServerLevel) player.level();
		if (offer.loot != null && !offer.loot.isBlank()) {
			Identifier table = Identifier.tryParse(offer.loot);
			if (table != null) {
				Economy.rollLoot(level, ResourceKey.create(Registries.LOOT_TABLE, table), player.blockPosition(), player);
			}
		} else {
			ItemStack product = new ItemStack(item(offer.item), offer.count);
			if (!player.addItem(product) && !product.isEmpty()) {
				player.spawnAtLocation(level, product);
			}
		}
		Director.log(level.getServer(), DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " bought " + (offer.loot.isBlank() ? offer.count + "x " + offer.item
				: "a roll of " + offer.loot));
		Director.beat(level.getServer(), "purchase");
		return null;
	}

	static @Nullable String purge(ServerPlayer player) {
		int cost = config().cursePurgeSouls;
		if (BuildHooks.curses(player).isEmpty()) {
			return "You carry no curse";
		}
		if (Souls.count(player) < cost) {
			return "A purge costs " + cost + " souls";
		}
		Souls.take(player, cost);
		BuildHooks.purgeCurse(player);
		Director.log(player.level().getServer(), DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " purged a curse");
		return null;
	}

	static @Nullable String reroll(ServerPlayer player) {
		int cost = config().perkRerollSouls;
		if (Souls.count(player) < cost) {
			return "A reroll costs " + cost + " souls";
		}
		Souls.take(player, cost);
		if (!WeaponHooks.rerollPerks(player)) {
			Souls.give(player, cost);
			return "Hold a gun with perks to reroll";
		}
		Director.log(player.level().getServer(), DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " rerolled a gun's perks");
		return null;
	}

	private static ItemStack service(Item icon, String key, int souls) {
		ItemStack stack = new ItemStack(icon);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("container.blocksouls_game.shop." + key, souls));
		return stack;
	}

	/** The board: a 9×6 chest view whose slots are buttons. */
	static final class Board extends ChestMenu {
		private final List<DirectorConfig.Offer> offers;
		private final SimpleContainer view;

		Board(int containerId, Inventory inventory, List<DirectorConfig.Offer> offers, Component remark) {
			this(containerId, inventory, offers, remark, new SimpleContainer(9 * ROWS));
		}

		private Board(int containerId, Inventory inventory, List<DirectorConfig.Offer> offers, Component remark, SimpleContainer view) {
			super(MenuType.GENERIC_9x6, containerId, inventory, view, ROWS);
			this.offers = offers;
			this.view = view;
			for (int column = 0; column < offers.size(); column++) {
				DirectorConfig.Offer offer = offers.get(column);
				view.setItem(column, productIcon(offer));
				for (int row = 0; row < Math.min(PRICE_ROWS, offer.prices.size()); row++) {
					view.setItem(9 * (row + 1) + column, priceIcon(offer.prices.get(row)));
				}
			}
			view.setItem(PURGE_SLOT, service(Items.MILK_BUCKET, "purge", config().cursePurgeSouls));
			view.setItem(REROLL_SLOT, service(Items.ANVIL, "reroll", config().perkRerollSouls));
			ItemStack remarkStack = new ItemStack(Items.PAPER);
			remarkStack.set(DataComponents.CUSTOM_NAME, remark);
			view.setItem(REMARK_SLOT, remarkStack);
		}

		@Override
		public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
			if (slotIndex < 0 || slotIndex >= view.getContainerSize()) {
				if (input != ContainerInput.QUICK_MOVE) {
					super.clicked(slotIndex, buttonNum, input, player);
				}
				return;
			}
			if (!(player instanceof ServerPlayer serverPlayer) || input != ContainerInput.PICKUP) {
				sendAllDataToRemote();
				return;
			}
			String error = null;
			if (slotIndex < offers.size()) {
				error = buy(serverPlayer, offers.get(slotIndex));
			} else if (slotIndex == PURGE_SLOT) {
				error = purge(serverPlayer);
			} else if (slotIndex == REROLL_SLOT) {
				error = reroll(serverPlayer);
			}
			if (error != null) {
				serverPlayer.sendSystemMessage(Component.literal(error));
			}
			sendAllDataToRemote();
		}

		@Override
		public ItemStack quickMoveStack(Player player, int slotIndex) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean canDragTo(Slot slot) {
			return slot.container != view;
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}
	}
}
