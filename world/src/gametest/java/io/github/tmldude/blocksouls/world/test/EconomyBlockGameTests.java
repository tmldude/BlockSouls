package io.github.tmldude.blocksouls.world.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.economy.CampObjects;
import io.github.tmldude.blocksouls.common.event.EconomyEvents;
import io.github.tmldude.blocksouls.world.economy.Shopkeeper;
import io.github.tmldude.blocksouls.world.economy.WorldEconomy;

/**
 * The economy objects the world module owns (PLAN2 §3c): each block and the shopkeeper fires
 * {@link EconomyEvents#CAMP_OBJECT_USED} with its object when used, and the camp placer puts them down. The
 * game module (what using them does) isn't loaded here; its gametests cover the effects.
 */
public class EconomyBlockGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";
	private static final List<String> USED = new ArrayList<>();

	static {
		EconomyEvents.CAMP_OBJECT_USED.register((player, object, pos, sneaking) -> {
			USED.add(object + "@" + pos.toShortString() + (sneaking ? ":sneak" : ""));
			return false;
		});
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 40)
	public void economyBlocksFireTheirObjectWhenUsed(GameTestHelper helper) {
		USED.clear();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Block[] blocks = {WorldEconomy.BLOOD_ALTAR, WorldEconomy.CURSED_CHEST, WorldEconomy.CHALLENGE_PYLON, WorldEconomy.CAMP_HORN};
		CampObject[] objects = {CampObject.BLOOD_ALTAR, CampObject.CURSED_CHEST, CampObject.CHALLENGE_PYLON, CampObject.CAMP_HORN};
		for (int i = 0; i < blocks.length; i++) {
			BlockPos relative = new BlockPos(2 + i * 2, 1, 2);
			BlockPos pos = helper.absolutePos(relative);
			helper.assertTrue(CampObjects.place(level, pos, objects[i]), objects[i] + " placed by the camp placer");
			helper.assertBlockPresent(blocks[i], relative);
			BlockState state = level.getBlockState(pos);
			helper.assertTrue(state.getDestroySpeed(level, pos) < 0, objects[i] + " is unbreakable");
			helper.assertTrue(state.getPistonPushReaction() == PushReaction.IMMOVEABLE,
					objects[i] + " can't be pushed");
			state.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
			helper.assertTrue(USED.contains(objects[i] + "@" + pos.toShortString()), objects[i] + " fired its event, saw " + USED);
		}
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 40)
	public void campPlacesOneShopkeeperThatFiresTheShop(GameTestHelper helper) {
		USED.clear();
		ServerLevel level = helper.getLevel();
		BlockPos first = helper.absolutePos(new BlockPos(4, 1, 4));
		BlockPos second = helper.absolutePos(new BlockPos(10, 1, 10));
		helper.assertTrue(CampObjects.place(level, first, CampObject.SHOPKEEPER), "shopkeeper placed");
		helper.assertTrue(CampObjects.place(level, second, CampObject.SHOPKEEPER), "shopkeeper placed again");
		List<Shopkeeper> shopkeepers = level.getEntitiesOfClass(Shopkeeper.class, new AABB(first).inflate(32));
		helper.assertTrue(shopkeepers.size() == 1, "a new camp's shopkeeper replaces the old one, found " + shopkeepers.size());
		Shopkeeper shopkeeper = shopkeepers.get(0);
		helper.assertTrue(shopkeeper.blockPosition().closerThan(second, 2), "the new one stands at the new camp");
		helper.assertTrue(shopkeeper.isInvulnerable(), "the shopkeeper can't be killed (or targeted)");
		helper.assertTrue(shopkeeper.isPersistenceRequired(), "the shopkeeper never despawns");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		shopkeeper.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(USED.size() == 1 && USED.get(0).startsWith(CampObject.SHOPKEEPER + "@"), "talking to the shopkeeper fires the shop, saw " + USED);
		shopkeeper.mobInteract(player, InteractionHand.OFF_HAND);
		helper.assertTrue(USED.size() == 1, "the off hand doesn't fire it twice");
		shopkeeper.discard();
		helper.succeed();
	}
}
