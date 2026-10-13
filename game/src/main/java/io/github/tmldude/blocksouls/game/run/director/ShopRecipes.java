package io.github.tmldude.blocksouls.game.run.director;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.run.DirectorHooks;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.Board;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.EnchantSpec;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.Entry;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.FloorView;
import io.github.tmldude.blocksouls.game.run.director.ShopBoardRules.Placement;

/**
 * The forge and brew boards' recipes: every BlockSouls recipe (crafting, smelting, smithing transform) from the
 * server's recipe manager, placed on a board by the {@code blocksouls_board/*} data (docs/decisions/v2-boards.md),
 * plus the data's "where to get it" sources, enchant specs and gear tier chains. Reloaded with {@code /reload}.
 */
public final class ShopRecipes {
	/** An entry file: how one recipe shows. */
	record EntryFile(@Nullable Board board, Placement placement, boolean hidden) {
	}

	/** Everything the {@code blocksouls_board} folders hold. */
	record Data(Map<String, EntryFile> entries, Map<String, DirectorConfig.Source> sources, List<EnchantSpec> enchants,
			List<List<String>> chains) {
		static final Data EMPTY = new Data(Map.of(), Map.of(), List.of(), List.of());
	}

	/** A board entry and the recipe it crafts. */
	record Built(Entry entry, RecipeHolder<?> holder, ItemStack icon) {
	}

	private static volatile Data data = Data.EMPTY;
	private static @Nullable RecipeManager builtFor;
	private static @Nullable Data builtFrom;
	private static List<Built> built = List.of();

	private ShopRecipes() {
	}

	static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "shop_boards"),
				new SimpleReloadListener<Data>() {
					@Override
					protected Data prepare(SharedState state) {
						Map<String, EntryFile> entries = new LinkedHashMap<>();
						Map<String, DirectorConfig.Source> sources = new LinkedHashMap<>();
						List<EnchantSpec> enchants = new ArrayList<>();
						List<List<String>> chains = new ArrayList<>();
						read(state, CommonIds.BOARD_ENTRIES, (id, json) -> {
							String recipe = string(json, "recipe", null);
							if (recipe == null || Identifier.tryParse(recipe) == null) {
								throw new IllegalArgumentException("needs a \"recipe\" id");
							}
							String boardName = string(json, "board", null);
							Board board = Board.parse(boardName);
							if (boardName != null && board == null) {
								throw new IllegalArgumentException("board must be \"forge\" or \"brew\", was " + boardName);
							}
							if (entries.put(recipe, new EntryFile(board, placement(json), json.has("hidden") && json.get("hidden").getAsBoolean())) != null) {
								BlockSoulsGame.LOGGER.warn("Board entry {} replaces an earlier entry for {}", id, recipe);
							}
						});
						read(state, CommonIds.BOARD_SOURCES, (id, json) -> {
							String item = string(json, "item", null);
							if (item == null) {
								throw new IllegalArgumentException("needs an \"item\"");
							}
							sources.put(item, new DirectorConfig.Source(string(json, "icon", item), string(json, "label", null)));
						});
						read(state, CommonIds.BOARD_ENCHANTS, (id, json) -> {
							String metal = string(json, "metal", null);
							if (metal == null) {
								throw new IllegalArgumentException("needs a \"metal\"");
							}
							enchants.add(new EnchantSpec(id.toString(), placement(json), metal, integer(json, "metalCount", 1), integer(json, "souls", 0),
									strings(json, "enchantments")));
						});
						read(state, CommonIds.BOARD_UPGRADES, (id, json) -> {
							List<String> chain = strings(json, "chain");
							if (chain.size() < 2) {
								throw new IllegalArgumentException("a chain needs 2+ items");
							}
							chains.add(chain);
						});
						return new Data(entries, sources, enchants, chains);
					}

					@Override
					protected void apply(Data prepared, SharedState state) {
						data = prepared;
						BlockSoulsGame.LOGGER.info("Loaded shop boards: {} entries, {} sources, {} enchant specs, {} tier chains",
								prepared.entries().size(), prepared.sources().size(), prepared.enchants().size(), prepared.chains().size());
					}
				});
	}

	private interface FileReader {
		void read(Identifier id, JsonObject json);
	}

	/** Reads every file of one folder in namespace-then-path order; a bad file is logged and skipped. */
	private static void read(SimpleReloadListener.SharedState state, String folder, FileReader reader) {
		FileToIdConverter files = FileToIdConverter.json(folder);
		List<Map.Entry<Identifier, Resource>> found = new ArrayList<>(files.listMatchingResources(state.resourceManager()).entrySet());
		found.sort(Map.Entry.comparingByKey());
		for (Map.Entry<Identifier, Resource> file : found) {
			Identifier id = files.fileToId(file.getKey());
			try (Reader in = file.getValue().openAsReader()) {
				reader.read(id, JsonParser.parseReader(in).getAsJsonObject());
			} catch (IOException | RuntimeException e) {
				BlockSoulsGame.LOGGER.error("Skipping {} {}: {}", folder, id, e.getMessage());
			}
		}
	}

	private static Placement placement(JsonObject json) {
		return new Placement(integer(json, "minFloor", 1), integer(json, "maxFloor", 99), new HashSet<>(strings(json, "floorTags")));
	}

	private static @Nullable String string(JsonObject json, String key, @Nullable String fallback) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? fallback : value.getAsString();
	}

	private static int integer(JsonObject json, String key, int fallback) {
		JsonElement value = json.get(key);
		return value == null || value.isJsonNull() ? fallback : value.getAsInt();
	}

	private static List<String> strings(JsonObject json, String key) {
		List<String> out = new ArrayList<>();
		JsonElement value = json.get(key);
		if (value instanceof JsonArray array) {
			array.forEach(element -> out.add(element.getAsString()));
		}
		return out;
	}

	/** Replaces the data (gametests). */
	static void setData(Data newData) {
		data = newData;
	}

	static Data data() {
		return data;
	}

	/** The config's sources with the data's merged over them. */
	static Map<String, DirectorConfig.Source> sources() {
		Map<String, DirectorConfig.Source> merged = new LinkedHashMap<>(Director.config().shop.sources);
		merged.putAll(data.sources());
		return merged;
	}

	/** Whether a recipe id is a BlockSouls recipe (the ones the boards execute and floors runs hide from the grids). */
	public static boolean modRecipe(Identifier id) {
		return id.getNamespace().startsWith("blocksouls_");
	}

	/** Every board entry, rebuilt when the recipes or the board data change. */
	static synchronized List<Built> all(MinecraftServer server) {
		RecipeManager recipes = server.getRecipeManager();
		Data current = data;
		if (recipes != builtFor || current != builtFrom) {
			built = build(recipes, current);
			builtFor = recipes;
			builtFrom = current;
		}
		return built;
	}

	static @Nullable Built find(MinecraftServer server, String recipe) {
		for (Built entry : all(server)) {
			if (entry.entry().recipe().equals(recipe)) {
				return entry;
			}
		}
		return null;
	}

	private static List<Built> build(RecipeManager recipes, Data current) {
		List<Built> out = new ArrayList<>();
		List<RecipeHolder<?>> holders = new ArrayList<>(recipes.getRecipes());
		holders.sort((a, b) -> a.id().identifier().compareTo(b.id().identifier()));
		for (RecipeHolder<?> holder : holders) {
			Identifier id = holder.id().identifier();
			if (!modRecipe(id)) {
				continue;
			}
			try {
				Built entry = build(holder, current.entries().get(id.toString()));
				if (entry != null) {
					out.add(entry);
				}
			} catch (RuntimeException e) {
				BlockSoulsGame.LOGGER.warn("Recipe {} can't go on a board: {}", id, e.toString());
			}
		}
		return List.copyOf(out);
	}

	private static @Nullable Built build(RecipeHolder<?> holder, @Nullable EntryFile file) {
		Recipe<?> recipe = holder.value();
		List<Ingredient> ingredients;
		ItemStack result;
		if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
			CraftingRecipe crafting = (CraftingRecipe) recipe;
			if (crafting.placementInfo().isImpossibleToPlace()) {
				return null;
			}
			ingredients = crafting.placementInfo().ingredients();
			result = crafting.assemble(CraftingInput.EMPTY);
		} else if (recipe instanceof SmeltingRecipe smelting) {
			ingredients = List.of(smelting.input());
			result = smelting.assemble(new SingleRecipeInput(ItemStack.EMPTY));
		} else if (recipe instanceof SmithingTransformRecipe smithing) {
			ingredients = new ArrayList<>();
			smithing.templateIngredient().ifPresent(ingredients::add);
			ingredients.add(smithing.baseIngredient());
			smithing.additionIngredient().ifPresent(ingredients::add);
			ItemStack base = smithing.baseIngredient().items().findFirst().map(item -> new ItemStack(item.value())).orElse(ItemStack.EMPTY);
			result = smithing.assemble(new SmithingRecipeInput(ItemStack.EMPTY, base, ItemStack.EMPTY));
		} else {
			return null;
		}
		if (result.isEmpty()) {
			return null;
		}
		List<List<String>> units = new ArrayList<>();
		for (Ingredient ingredient : ingredients) {
			List<String> options = ingredient.items().map(ShopRecipes::id).toList();
			if (options.isEmpty()) {
				return null;
			}
			units.add(options);
		}
		boolean consumable = result.has(DataComponents.FOOD) || result.has(DataComponents.CONSUMABLE) || result.is(CommonTags.AMMO);
		Board board = ShopBoardRules.classify(file == null ? null : file.board(), result.is(CommonTags.BREW_BOARD), result.is(CommonTags.FORGE_BOARD),
				consumable);
		Entry entry = new Entry(holder.id().identifier().toString(), board, id(result.typeHolder()), result.getCount(), units,
				file == null ? Placement.EVERYWHERE : file.placement(), file != null && file.hidden());
		return new Built(entry, holder, result);
	}

	static String id(Holder<Item> item) {
		return BuiltInRegistries.ITEM.getKey(item.value()).toString();
	}

	static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** The floor {@code player} stands on, or the act ordinal (at least 1) with no tags off a floor. */
	static FloorView view(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Optional<PlacedFloor> floor = Floors.at(server, player.level().dimension().identifier(), player.blockPosition());
		return floor.map(placed -> new FloorView(placed.floorNumber(), placed.tags()))
				.orElseGet(() -> new FloorView(Math.max(1, DirectorHooks.actOrdinal(server)), Set.of()));
	}

	/** The main inventory (hotbar + 27, no armor or offhand) as item id → count. */
	static Map<String, Integer> inventory(ServerPlayer player) {
		Map<String, Integer> out = new HashMap<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty()) {
				out.merge(id(stack.getItem()), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** Takes one {@code item} from the main inventory and returns it (a single stack), or empty. */
	static ItemStack takeOne(ServerPlayer player, String item, @Nullable ItemStack skip) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && stack != skip && id(stack.getItem()).equals(item)) {
				ItemStack taken = stack.split(1);
				inventory.setChanged();
				return taken;
			}
		}
		return ItemStack.EMPTY;
	}

	/** "2× Gunmetal Ingot (Gunmetal nodes)" for what's still missing. */
	static String describeMissing(Map<List<String>, Integer> missing) {
		Map<String, DirectorConfig.Source> sources = sources();
		List<String> parts = new ArrayList<>();
		missing.forEach((options, count) -> {
			String first = options.getFirst();
			Item item = Shop.item(first);
			String name = item == null ? first : new ItemStack(item).getHoverName().getString();
			if (options.size() > 1) {
				name += " (or similar)";
			}
			DirectorConfig.Source source = sources.get(first);
			parts.add(count + "x " + name + (source != null && source.label != null ? " from " + source.label : ""));
		});
		return String.join(", ", parts);
	}

	/** Crafts {@code built} for {@code player} from their inventory. Null on success, else what's wrong. */
	static @Nullable String craft(ServerPlayer player, Built built) {
		Entry entry = built.entry();
		Map<String, Integer> inventory = inventory(player);
		List<String> picked = ShopBoardRules.match(entry.units(), inventory);
		if (picked == null) {
			return "Missing " + describeMissing(ShopBoardRules.missing(entry.kinds(), inventory));
		}
		ItemStack product;
		if (built.holder().value() instanceof SmithingTransformRecipe smithing) {
			List<ItemStack> taken = new ArrayList<>();
			for (String item : picked) {
				taken.add(takeOne(player, item, null));
			}
			int at = 0;
			ItemStack template = smithing.templateIngredient().isPresent() ? taken.get(at++) : ItemStack.EMPTY;
			ItemStack base = taken.get(at++);
			ItemStack addition = smithing.additionIngredient().isPresent() ? taken.get(at) : ItemStack.EMPTY;
			product = smithing.assemble(new SmithingRecipeInput(template, base, addition));
		} else {
			for (String item : picked) {
				takeOne(player, item, null);
			}
			product = built.icon().copy();
		}
		int count = product.getCount(); // give() empties the stack into the inventory
		give(player, product);
		ServerLevel level = (ServerLevel) player.level();
		Director.log(level.getServer(), DirectorLog.Kind.ECONOMY, player.getScoreboardName() + " crafted " + count + "x " + entry.result()
				+ " at the " + entry.board().id() + " board");
		Director.beat(level.getServer(), "craft");
		return null;
	}

	static void give(ServerPlayer player, ItemStack stack) {
		if (!player.addItem(stack) && !stack.isEmpty()) {
			player.spawnAtLocation((ServerLevel) player.level(), stack);
		}
	}

	/** Every ingredient of {@code built}, one option each (the first), as stacks: {@code /bs shop give-ingredients}. */
	static List<ItemStack> ingredients(Built built) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (ShopBoardRules.Kind kind : built.entry().kinds()) {
			counts.merge(kind.options().getFirst(), kind.count(), Integer::sum);
		}
		List<ItemStack> out = new ArrayList<>();
		counts.forEach((item, count) -> {
			Item resolved = Shop.item(item);
			if (resolved != null) {
				out.add(new ItemStack(resolved, count));
			}
		});
		return out;
	}
}
