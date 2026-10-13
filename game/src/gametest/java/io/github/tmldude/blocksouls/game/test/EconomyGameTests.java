package io.github.tmldude.blocksouls.game.test;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.event.EconomyEvents;
import io.github.tmldude.blocksouls.game.player.soul.Souls;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.director.DayLoop;
import io.github.tmldude.blocksouls.game.run.director.Director;
import io.github.tmldude.blocksouls.game.run.director.DirectorConfig;
import io.github.tmldude.blocksouls.game.run.director.DirectorLog;

/**
 * The run's economy objects (PLAN2 §3c) as the world module fires them: a player uses the object at a spot.
 * The world module isn't loaded here, so a vanilla block stands in for each world block; the world module's
 * own gametests cover its blocks and the shopkeeper firing the event.
 */
public class EconomyGameTests {
	private static final BlockPos SPOT = new BlockPos(DirectorTestSupport.CENTRE + 2, 1, DirectorTestSupport.CENTRE);

	private static int stacks(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (!player.getInventory().getItem(slot).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/** Places a stand-in block at {@link #SPOT} and has {@code player} use {@code object} there. */
	private static boolean use(GameTestHelper helper, ServerPlayer player, CampObject object, @Nullable Block standIn, boolean sneaking) {
		if (standIn != null) {
			helper.setBlock(SPOT, standIn);
		}
		return EconomyEvents.CAMP_OBJECT_USED.invoker().onUsed(player, object, helper.absolutePos(SPOT), sneaking);
	}

	@GameTest(environment = "blocksouls_game_test:economy_altar")
	public void bloodAltarTakesHeartsForLegendaryLoot(GameTestHelper helper) {
		DirectorConfig director = DirectorTestSupport.quietConfig();
		director.altar.hearts = 4;
		ServerPlayer player = DirectorTestSupport.setUp(helper, director);
		int before = stacks(player);
		helper.assertTrue(use(helper, player, CampObject.BLOOD_ALTAR, Blocks.REDSTONE_BLOCK, false), "the Director handles the altar");
		helper.assertBlockPresent(Blocks.AIR, SPOT);
		helper.assertTrue(player.getMaxHealth() == 12, "4 hearts paid: max health 12, was " + player.getMaxHealth());
		helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.ECONOMY, "paid the blood altar 4 heart(s)"), "altar logged" + DirectorTestSupport.dump());
		helper.assertTrue(stacks(player) > before, "the altar's loot was given");
		DirectorTestSupport.tearDown(helper);
		helper.succeed();
	}

	@GameTest(environment = "blocksouls_game_test:economy_cursed_chest")
	public void cursedChestPaysLootAndBumpsTheNextSiege(GameTestHelper helper) {
		ServerPlayer player = DirectorTestSupport.setUp(helper, DirectorTestSupport.quietConfig());
		int before = stacks(player);
		helper.assertTrue(use(helper, player, CampObject.CURSED_CHEST, Blocks.CRYING_OBSIDIAN, false), "the Director handles the chest");
		helper.assertBlockPresent(Blocks.AIR, SPOT);
		helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.ECONOMY, "next siege +1 tier(s)"), "chest bumps the next siege"
				+ DirectorTestSupport.dump());
		helper.assertTrue(stacks(player) > before, "the chest's loot was given");
		DirectorTestSupport.tearDown(helper);
		helper.succeed();
	}

	@GameTest(environment = "blocksouls_game_test:economy_pylon", maxTicks = 400)
	public void challengePylonRewardsWhoeverHoldsTheCircle(GameTestHelper helper) {
		DirectorConfig director = DirectorTestSupport.quietConfig();
		director.pylon.seconds = 3;
		director.pylon.packEverySeconds = 1;
		director.pylon.pack = 1;
		ServerPlayer player = DirectorTestSupport.setUp(helper, director);
		helper.assertTrue(use(helper, player, CampObject.CHALLENGE_PYLON, Blocks.LODESTONE, false), "the Director handles the pylon");
		helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.ECONOMY, "lit a challenge pylon"), "pylon lit");
		helper.assertBlockPresent(Blocks.LODESTONE, SPOT);
		helper.succeedWhen(() -> {
			helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.ECONOMY, "Challenge pylon survived by 1"), "the circle was held"
					+ DirectorTestSupport.dump());
			helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.ECONOMY, "Pylon calls the horde"), "the pylon sent packs");
			helper.assertBlockPresent(Blocks.AIR, SPOT);
			DirectorTestSupport.tearDown(helper);
		});
	}

	@GameTest(environment = "blocksouls_game_test:economy_horn")
	public void campHornEndsTheDayForSoulsAndStartsTheSiege(GameTestHelper helper) {
		ServerPlayer player = DirectorTestSupport.setUp(helper, DirectorTestSupport.quietConfig());
		MinecraftServer server = helper.getLevel().getServer();
		DayLoop.startDay(server, 1);
		int souls = Souls.count(player);
		helper.assertTrue(use(helper, player, CampObject.CAMP_HORN, Blocks.BELL, false), "the Director handles the horn");
		helper.assertTrue(Souls.count(player) > souls, "an early horn pays souls: " + souls + " -> " + Souls.count(player));
		helper.assertTrue(RunDebug.charging(server), "the horn starts the siege (portal charge)" + DirectorTestSupport.dump());
		helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.DAY, "blew the horn"), "horn logged");
		use(helper, player, CampObject.CAMP_HORN, null, false);
		helper.assertTrue(Director.log().tail(500, DirectorLog.Kind.DAY).stream().filter(e -> e.text().contains("blew the horn")).count() == 1,
				"a second blow during the siege is refused");
		DirectorTestSupport.tearDown(helper);
		helper.succeed();
	}

	@GameTest(environment = "blocksouls_game_test:economy_shop")
	public void shopkeeperSellsFromTheBoard(GameTestHelper helper) {
		DirectorConfig director = DirectorTestSupport.quietConfig();
		DirectorConfig.Offer apples = new DirectorConfig.Offer();
		apples.item = "minecraft:apple";
		apples.count = 3;
		apples.prices.add(new DirectorConfig.Price("minecraft:iron_ingot", 2));
		director.shop.pool = new ArrayList<>(List.of(apples));
		ServerPlayer player = DirectorTestSupport.setUp(helper, director);
		player.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
		helper.assertTrue(use(helper, player, CampObject.SHOPKEEPER, null, false), "the Director handles the shopkeeper");
		helper.assertTrue(player.containerMenu instanceof ChestMenu, "the trade board opened, menu " + player.containerMenu);
		ItemStack product = player.containerMenu.getSlot(0).getItem();
		helper.assertTrue(product.is(Items.APPLE) && product.getCount() == 3, "the offer shows on the board: " + product);
		player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 3, "bought 3 apples");
		helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 0, "paid 2 iron");
		helper.assertTrue(player.containerMenu.getCarried().isEmpty(), "nothing picked up off the board");
		player.containerMenu.clicked(0, 0, ContainerInput.PICKUP, player);
		helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 3, "can't buy without the price");
		helper.assertTrue(DirectorTestSupport.logged(DirectorLog.Kind.ECONOMY, "bought 3x minecraft:apple"), "purchase logged");
		player.closeContainer();
		DirectorTestSupport.tearDown(helper);
		helper.succeed();
	}
}
