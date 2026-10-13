package io.github.tmldude.blocksouls.world.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.event.FloorEvents;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.floor.FloorBlocks;
import io.github.tmldude.blocksouls.world.floor.FloorPortalBlock;
import io.github.tmldude.blocksouls.world.floor.NodeBlock;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Walks a placed floor (PLAN2 §1): opens a Surface floor in the floors dimension and checks every slot was built
 * (camp, unlit portal, nodes, chests, the cave), that a node pops after its hits and the portal lights. Uses a
 * high act ordinal so it never collides with a real run's floors.
 */
public class FloorGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";
	private static final List<String> EVENTS = new ArrayList<>();

	static {
		FloorEvents.NODE_POPPED.register((player, floor, pos, node) -> EVENTS.add("pop:" + node));
		FloorEvents.EXIT_PORTAL_LIT.register((server, floor) -> EVENTS.add("lit:" + floor.actOrdinal()));
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void surfaceFloorFillsEverySlotAndNodesPop(GameTestHelper helper) {
		EVENTS.clear();
		MinecraftServer server = helper.getLevel().getServer();
		int act = 900;
		Optional<PlacedFloor> opened = Floors.open(server, new FloorRequest(act, BlockSoulsWorld.id("surface"), 1, 1234L, 1));
		helper.assertTrue(opened.isPresent(), "a Surface floor opened");
		PlacedFloor floor = opened.get();
		ServerLevel level = server.getLevel(WorldFloors.DIMENSION);
		helper.assertTrue(level != null, "the floors dimension is loaded");
		helper.assertTrue(Floors.open(server, new FloorRequest(act, BlockSoulsWorld.id("surface"), 1, 1234L, 1)).get().equals(floor),
				"opening the same act again returns the placed floor");

		for (FloorSlotKind kind : FloorSlotKind.values()) {
			helper.assertTrue(!floor.slots(kind).isEmpty(), "the floor has a " + kind + " slot");
		}
		helper.assertTrue(floor.slots().getFirst().kind() == FloorSlotKind.CAMP, "the camp comes first");
		helper.assertTrue(!level.getBlockState(floor.camp().below()).isAir(), "the camp stands on ground");
		helper.assertTrue(level.getBlockState(floor.camp().below(48)).isAir(), "the void is under the island");

		FloorSlot portal = floor.slots(FloorSlotKind.EXIT_PORTAL).getFirst();
		BlockState portalState = level.getBlockState(portal.pos());
		helper.assertTrue(portalState.is(FloorBlocks.FLOOR_PORTAL) && !portalState.getValue(FloorPortalBlock.LIT), "the portal starts unlit");

		for (FloorSlot crate : floor.slots(FloorSlotKind.CRATE)) {
			helper.assertTrue(level.getBlockState(crate.pos()).is(Blocks.CHEST), "crate slot " + crate.pos().toShortString() + " is a chest");
		}
		helper.assertTrue(floor.slots(FloorSlotKind.CRATE).size() >= 4, "loose, structure, cave and secret chests");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FloorSlot node = floor.slots(FloorSlotKind.NODE).getFirst();
		BlockState nodeState = level.getBlockState(node.pos());
		helper.assertTrue(nodeState.getBlock() instanceof NodeBlock, "node slot holds a node, found " + nodeState);
		NodeBlock block = (NodeBlock) nodeState.getBlock();
		int hits = WorldFloors.config().nodeHits;
		for (int i = 1; i < hits; i++) {
			helper.assertTrue(!block.hit(level, node.pos(), player), "hit " + i + " doesn't pop the node");
		}
		helper.assertTrue(block.hit(level, node.pos(), player), "the last hit pops the node");
		helper.assertTrue(level.getBlockState(node.pos()).isAir(), "a popped node is gone");
		helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(node.pos()).inflate(3)).isEmpty(), "the node burst its loot");
		helper.assertTrue(EVENTS.stream().anyMatch(e -> e.startsWith("pop:")), "NODE_POPPED fired, saw " + EVENTS);

		WorldFloors.lightPortal(server, floor);
		helper.assertTrue(level.getBlockState(portal.pos()).getValue(FloorPortalBlock.LIT), "the charged portal is lit");
		helper.assertTrue(EVENTS.contains("lit:" + act), "EXIT_PORTAL_LIT fired, saw " + EVENTS);
		helper.succeed();
	}
}
