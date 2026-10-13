package io.github.tmldude.blocksouls.game.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.game.run.director.DirectorLog;
import io.github.tmldude.blocksouls.game.run.director.Shop;
import io.github.tmldude.blocksouls.game.run.director.ShopGate;

/**
 * The shopkeeper's forge and brew boards (docs/decisions/v2-boards.md), with the test recipe
 * {@code blocksouls_game_test:test_tonic} (apple + sugar → honey bottle, a consumable, so it's on the brew board).
 */
public class ShopBoardGameTests {
	private static final int NEXT_PAGE_SLOT = 52;
	private static final int MAX_PAGES = 50;

	@GameTest(environment = "blocksouls_game_test:boards_brew")
	public void brewBoardCraftsAConsumable(GameTestHelper helper) {
		ServerPlayer player = DirectorTestSupport.setUp(helper, DirectorTestSupport.quietConfig());
		player.getInventory().add(new ItemStack(Items.APPLE));
		player.getInventory().add(new ItemStack(Items.SUGAR));
		Shop.open(player, Shop.Tab.BREW);
		helper.assertTrue(player.containerMenu instanceof ChestMenu, "the brew board opened, menu " + player.containerMenu);
		int slot = findResult(player);
		helper.assertTrue(slot >= 0, "the tonic is listed on the brew board");
		player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(player.getInventory().countItem(Items.HONEY_BOTTLE) == 1, "crafted the tonic");
		helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 0 && player.getInventory().countItem(Items.SUGAR) == 0, "paid the ingredients");
		helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "nothing picked up off the board");
		player.containerMenu.clicked(slot, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(player.getInventory().countItem(Items.HONEY_BOTTLE) == 1, "can't craft without the ingredients");
		helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.ECONOMY, "crafted 1x minecraft:honey_bottle at the brew board"), "craft logged");
		player.closeContainer();
		DirectorTestSupport.tearDown(helper);
		helper.succeed();
	}

	/** The slot of the tonic's result on the open board, turning pages until it shows; -1 if it never does. */
	private static int findResult(ServerPlayer player) {
		for (int page = 0; page < MAX_PAGES; page++) {
			for (int slot = 0; slot < 45; slot++) {
				int column = slot % 9;
				if ((column == 0 || column == 5) && player.containerMenu.getSlot(slot).getItem().is(Items.HONEY_BOTTLE)) {
					return slot;
				}
			}
			if (player.containerMenu.getSlot(NEXT_PAGE_SLOT).getItem().isEmpty()) {
				return -1;
			}
			player.containerMenu.clicked(NEXT_PAGE_SLOT, 0, ContainerInput.PICKUP, player);
		}
		return -1;
	}

	@GameTest(environment = "blocksouls_game_test:boards_gate")
	public void floorsGateTakesBlockSoulsRecipesOffTheGrid(GameTestHelper helper) {
		ServerPlayer player = DirectorTestSupport.setUp(helper, DirectorTestSupport.quietConfig());
		ServerLevel level = helper.getLevel();
		CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.APPLE), new ItemStack(Items.SUGAR)));
		try {
			ShopGate.setClosed(false);
			java.util.Optional<RecipeHolder<CraftingRecipe>> open = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
			helper.assertTrue(open.isPresent() && open.get().id().identifier().toString().equals("blocksouls_game_test:test_tonic"),
					"the grid crafts the tonic outside a floors run: " + open);
			helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level, open.get()).isPresent(), "the hinted lookup too");
			ShopGate.setClosed(true);
			helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).isEmpty(), "a floors run takes it off the grid");
			helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level, open.get()).isEmpty(), "and off the hinted lookup");
			CraftingInput planks = CraftingInput.of(1, 1, List.of(new ItemStack(Items.OAK_LOG)));
			helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, planks, level).isPresent(), "vanilla recipes still craft");

			BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
			level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
			player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false));
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "the crafting table opens nothing: " + player.containerMenu);
		} finally {
			ShopGate.setClosed(false);
		}
		DirectorTestSupport.tearDown(helper);
		helper.succeed();
	}
}
