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
	static final int ENCHANT_SLOT = 46;
	static final int UPGRADE_SLOT = 47;
	static final int PREV_SLOT = 48;
	static final int TRADE_TAB_SLOT = 49;
	static final int FORGE_TAB_SLOT = 50;
	static final int BREW_TAB_SLOT = 51;
	static final int NEXT_SLOT = 52;
	static final int REMARK_SLOT = 53;
	/** Forge/brew entries: two per row, the result in column 0 or 5 and up to three ingredient kinds after it. */
	static final int ENTRY_SECOND_COLUMN = 5;
	static final int ENTRY_INGREDIENTS = 3;

	/** The board's tabs. */
	public enum Tab {
		TRADE(Items.EMERALD, null), FORGE(Items.ANVIL, ShopBoardRules.Board.FORGE), BREW(Items.BREWING_STAND, ShopBoardRules.Board.BREW);

		final Item icon;
		private final ShopBoardRules.@Nullable Board board;

		Tab(Item icon, ShopBoardRules.@Nullable Board board) {
			this.icon = icon;
			this.board = board;
		}

		ShopBoardRules.@Nullable Board board() {
			return board;
		}

		String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static @Nullable Tab parse(String name) {
			for (Tab tab : values()) {
				if (tab.id().equalsIgnoreCase(name)) {
					return tab;
				}
			}
			return null;
		}
	}

	private Shop() {
	}

	static void init() {
		ShopRecipes.register();
		ShopGate.register();
		ShopCommands.register();
	}

	private static DirectorConfig.Shop config() {
		return Director.config().shop;
	}

	/** Opens the board on its trade tab for {@code player}. Null on success. */
	static @Nullable String open(ServerPlayer player) {
		return open(player, Tab.TRADE);
	}

	/** Opens the board on {@code tab} for {@code player}. Null on success. */
	public static @Nullable String open(ServerPlayer player, Tab tab) {
		MinecraftServer server = player.level().getServer();
		List<DirectorConfig.Offer> board = DirectorRules.stock(config().pool, Math.max(1, DirectorHooks.actOrdinal(server)), DirectorData.get(server).seed, config().slots);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new Board(id, inventory, server, board, remark(server), tab),
				Component.translatable("container.blocksouls_game.shop")));
		return null;
	}

	/** {@code player}'s floor's entries of {@code board}, in recipe id order. */
	static List<ShopRecipes.Built> visible(ServerPlayer player, ShopBoardRules.Board board) {
		List<ShopRecipes.Built> all = ShopRecipes.all(player.level().getServer());
		java.util.Map<ShopBoardRules.Entry, ShopRecipes.Built> byEntry = new java.util.IdentityHashMap<>();
		all.forEach(built -> byEntry.put(built.entry(), built));
		return ShopBoardRules.visible(all.stream().map(ShopRecipes.Built::entry).toList(), board, ShopRecipes.view(player)).stream().map(byEntry::get)
				.toList();
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
		return sourceIcon(price.item, price.count, false);
	}

	/**
	 * An ingredient as where to get it (config + data sources): the source's icon, the count, the item (and "or
	 * similar" for a tag ingredient) in the name, the source's label in the lore.
	 */
	static ItemStack sourceIcon(String itemId, int count, boolean alternatives) {
		Item paid = item(itemId);
		if (paid == null) {
			return ItemStack.EMPTY;
		}
		DirectorConfig.Source source = ShopRecipes.sources().get(itemId);
		Item icon = source == null ? null : item(source.icon);
		ItemStack stack = new ItemStack(icon != null ? icon : paid, Math.max(1, Math.min(count, 99)));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable(alternatives ? "container.blocksouls_game.shop.price_any" : "container.blocksouls_game.shop.price",
				count, new ItemStack(paid).getHoverName()));
		if (source != null && source.label != null) {
			stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("container.blocksouls_game.shop.source", source.label))));
		}
		return stack;
	}

	/** A forge/brew entry's result: the item, with every ingredient listed in the lore and "click to craft". */
	static ItemStack resultIcon(ShopRecipes.Built built) {
		ItemStack stack = built.icon().copy();
		List<Component> lore = new java.util.ArrayList<>();
		for (ShopBoardRules.Kind kind : built.entry().kinds()) {
			Item item = item(kind.options().getFirst());
			Component name = item == null ? Component.literal(kind.options().getFirst()) : new ItemStack(item).getHoverName();
			lore.add(Component.translatable(kind.options().size() > 1 ? "container.blocksouls_game.shop.price_any" : "container.blocksouls_game.shop.price",
					kind.count(), name));
		}
		lore.add(Component.translatable("container.blocksouls_game.shop.craft"));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static ItemStack named(Item icon, String key) {
		ItemStack stack = new ItemStack(icon);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("container.blocksouls_game.shop." + key));
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

	/** The board: a 9×6 chest view whose slots are buttons, with the trade / forge / brew tabs on the bottom row. */
	static final class Board extends ChestMenu {
		private final List<DirectorConfig.Offer> offers;
		private final Component remark;
		private final SimpleContainer view;
		private Tab tab;
		private int page;
		private List<ShopRecipes.Built> entries = List.of();

		Board(int containerId, Inventory inventory, MinecraftServer server, List<DirectorConfig.Offer> offers, Component remark, Tab tab) {
			this(containerId, inventory, server, offers, remark, tab, new SimpleContainer(9 * ROWS));
		}

		private Board(int containerId, Inventory inventory, MinecraftServer server, List<DirectorConfig.Offer> offers, Component remark, Tab tab,
				SimpleContainer view) {
			super(MenuType.GENERIC_9x6, containerId, inventory, view, ROWS);
			this.offers = offers;
			this.remark = remark;
			this.view = view;
			show(inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null, tab);
		}

		Tab tab() {
			return tab;
		}

		List<ShopRecipes.Built> entries() {
			return entries;
		}

		/** Switches to {@code newTab}'s first page and redraws. */
		void show(@Nullable ServerPlayer player, Tab newTab) {
			tab = newTab;
			page = 0;
			ShopBoardRules.Board board = newTab.board();
			entries = board == null || player == null ? List.of() : visible(player, board);
			draw();
		}

		private void draw() {
			view.clearContent();
			if (tab == Tab.TRADE) {
				for (int column = 0; column < offers.size(); column++) {
					DirectorConfig.Offer offer = offers.get(column);
					view.setItem(column, productIcon(offer));
					for (int row = 0; row < Math.min(PRICE_ROWS, offer.prices.size()); row++) {
						view.setItem(9 * (row + 1) + column, priceIcon(offer.prices.get(row)));
					}
				}
				view.setItem(PURGE_SLOT, service(Items.MILK_BUCKET, "purge", config().cursePurgeSouls));
				view.setItem(REROLL_SLOT, service(Items.ANVIL, "reroll", config().perkRerollSouls));
			} else {
				for (int i = 0; i < ShopBoardRules.PER_PAGE; i++) {
					int index = page * ShopBoardRules.PER_PAGE + i;
					if (index >= entries.size()) {
						break;
					}
					int first = 9 * (i / 2) + (i % 2 == 0 ? 0 : ENTRY_SECOND_COLUMN);
					ShopRecipes.Built built = entries.get(index);
					view.setItem(first, resultIcon(built));
					List<ShopBoardRules.Kind> kinds = built.entry().kinds();
					for (int k = 0; k < Math.min(ENTRY_INGREDIENTS, kinds.size()); k++) {
						ShopBoardRules.Kind kind = kinds.get(k);
						view.setItem(first + 1 + k, sourceIcon(kind.options().getFirst(), kind.count(), kind.options().size() > 1));
					}
				}
				if (tab == Tab.FORGE) {
					view.setItem(PURGE_SLOT, service(Items.ANVIL, "reroll", config().perkRerollSouls));
					view.setItem(ENCHANT_SLOT, service(Items.ENCHANTING_TABLE, "enchant", 0));
					view.setItem(UPGRADE_SLOT, service(Items.SMITHING_TABLE, "upgrade", config().upgradeSouls));
				}
				if (page > 0) {
					view.setItem(PREV_SLOT, named(Items.ARROW, "prev"));
				}
				if (page + 1 < ShopBoardRules.pages(entries.size())) {
					view.setItem(NEXT_SLOT, named(Items.ARROW, "next"));
				}
			}
			view.setItem(TRADE_TAB_SLOT, tabIcon(Tab.TRADE));
			view.setItem(FORGE_TAB_SLOT, tabIcon(Tab.FORGE));
			view.setItem(BREW_TAB_SLOT, tabIcon(Tab.BREW));
			ItemStack remarkStack = new ItemStack(Items.PAPER);
			remarkStack.set(DataComponents.CUSTOM_NAME, remark);
			view.setItem(REMARK_SLOT, remarkStack);
		}

		private ItemStack tabIcon(Tab icon) {
			ItemStack stack = named(icon.icon, "tab." + icon.id());
			if (icon == tab) {
				stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			return stack;
		}

		/** The entry whose result sits in {@code slot} on this page, or null. */
		ShopRecipes.@Nullable Built entryAt(int slot) {
			if (tab == Tab.TRADE || slot >= PURGE_SLOT) {
				return null;
			}
			int column = slot % 9;
			int i;
			if (column == 0) {
				i = (slot / 9) * 2;
			} else if (column == ENTRY_SECOND_COLUMN) {
				i = (slot / 9) * 2 + 1;
			} else {
				return null;
			}
			int index = page * ShopBoardRules.PER_PAGE + i;
			return index < entries.size() ? entries.get(index) : null;
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
			switch (slotIndex) {
				case TRADE_TAB_SLOT -> show(serverPlayer, Tab.TRADE);
				case FORGE_TAB_SLOT -> show(serverPlayer, Tab.FORGE);
				case BREW_TAB_SLOT -> show(serverPlayer, Tab.BREW);
				case PREV_SLOT -> turn(-1);
				case NEXT_SLOT -> turn(1);
				default -> error = act(serverPlayer, slotIndex);
			}
			if (error != null) {
				serverPlayer.sendSystemMessage(Component.literal(error));
			}
			sendAllDataToRemote();
		}

		private void turn(int by) {
			if (tab != Tab.TRADE) {
				page = Math.max(0, Math.min(ShopBoardRules.pages(entries.size()) - 1, page + by));
				draw();
			}
		}

		private @Nullable String act(ServerPlayer player, int slot) {
			if (tab == Tab.TRADE) {
				if (slot < offers.size()) {
					return buy(player, offers.get(slot));
				}
				return slot == PURGE_SLOT ? purge(player) : slot == REROLL_SLOT ? reroll(player) : null;
			}
			if (tab == Tab.FORGE) {
				switch (slot) {
					case PURGE_SLOT:
						return reroll(player);
					case ENCHANT_SLOT:
						return ShopForge.enchant(player);
					case UPGRADE_SLOT:
						return ShopForge.upgrade(player);
					default:
						break;
				}
			}
			ShopRecipes.Built built = entryAt(slot);
			return built == null ? null : ShopRecipes.craft(player, built);
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
