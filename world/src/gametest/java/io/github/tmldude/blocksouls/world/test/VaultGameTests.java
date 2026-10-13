package io.github.tmldude.blocksouls.world.test;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.floor.NodeBlock;
import io.github.tmldude.blocksouls.world.floor.VaultWallBlock;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Walks floor 4, The Vault: opens a vault floor in the test level and checks the camp is roofed in, a node pops, plain
 * walls hold, the hollow wall bursts open on its secret and a mob closet releases its swarm.
 */
public class VaultGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void vaultFloorBuildsAndItsWallsBurst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			walkVault(helper, level.getServer(), level, 904);
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}

	private static void walkVault(GameTestHelper helper, MinecraftServer server, ServerLevel level, int act) {
		Optional<PlacedFloor> opened = Floors.open(server, new FloorRequest(act, BlockSoulsWorld.id("vault"), 4, 4321L, 1));
		helper.assertTrue(opened.isPresent(), "a Vault floor opened");
		PlacedFloor floor = opened.get();
		helper.assertTrue(floor.tags().contains("vault"), "the vault floor carries its tag, saw " + floor.tags());
		helper.assertTrue(!level.getBlockState(floor.camp().below()).isAir(), "the camp stands on a floor");
		helper.assertTrue(level.getBlockState(floor.camp().above(23)).is(Blocks.BEDROCK), "the camp is roofed in by the bedrock lid");
		for (FloorSlotKind kind : new FloorSlotKind[] {FloorSlotKind.EXIT_PORTAL, FloorSlotKind.STRUCTURE, FloorSlotKind.SECRET,
				FloorSlotKind.MINI_BOSS, FloorSlotKind.EVENT, FloorSlotKind.NODE, FloorSlotKind.CRATE, FloorSlotKind.SIEGE_RING}) {
			helper.assertTrue(!floor.slots(kind).isEmpty(), "the vault has a " + kind + " slot");
		}
		for (FloorSlot crate : floor.slots(FloorSlotKind.CRATE)) {
			helper.assertTrue(level.getBlockState(crate.pos()).is(Blocks.CHEST), "crate slot " + crate.pos().toShortString() + " is a chest");
		}
		for (FloorSlot point : floor.slots(FloorSlotKind.SIEGE_RING)) {
			helper.assertTrue(level.getBlockState(point.pos()).isAir(), "siege point " + point.pos().toShortString() + " is in a corridor");
		}

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FloorSlot node = floor.slots(FloorSlotKind.NODE).getFirst();
		BlockState nodeState = level.getBlockState(node.pos());
		helper.assertTrue(nodeState.getBlock() instanceof NodeBlock, "node slot holds a node, found " + nodeState);
		NodeBlock block = (NodeBlock) nodeState.getBlock();
		for (int i = 0; i < 8 && !block.hit(level, node.pos(), player); i++) {
			// hit until it pops
		}
		helper.assertTrue(level.getBlockState(node.pos()).isAir(), "a popped node is gone");
		helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(node.pos()).inflate(3)).isEmpty(), "the node burst its loot");

		BlockPos plain = find(level, floor, VaultWallBlock.Content.NONE);
		helper.assertTrue(plain != null, "the vault has plain walls");
		VaultWallBlock wall = (VaultWallBlock) level.getBlockState(plain).getBlock();
		for (int i = 0; i < 6; i++) {
			helper.assertTrue(!wall.hit(level, plain, player), "a plain vault wall never bursts");
		}

		BlockPos hollow = find(level, floor, VaultWallBlock.Content.HOLLOW);
		helper.assertTrue(hollow != null, "the secret is sealed by a hollow wall");
		VaultWallBlock hollowWall = (VaultWallBlock) level.getBlockState(hollow).getBlock();
		boolean burst = false;
		for (int i = 0; i < 4 && !burst; i++) {
			burst = hollowWall.hit(level, hollow, player);
		}
		helper.assertTrue(burst && level.getBlockState(hollow).isAir(), "the hollow wall bursts open");
		helper.assertTrue(find(level, floor, VaultWallBlock.Content.HOLLOW) == null, "the whole hollow plug collapsed");

		BlockPos closet = find(level, floor, VaultWallBlock.Content.CLOSET);
		helper.assertTrue(closet != null, "the vault has mob closets");
		int before = level.getEntitiesOfClass(Mob.class, new AABB(closet).inflate(6)).size();
		VaultWallBlock closetWall = (VaultWallBlock) level.getBlockState(closet).getBlock();
		for (int i = 0; i < 4 && !closetWall.hit(level, closet, player); i++) {
			// hit until it bursts
		}
		helper.assertTrue(level.getEntitiesOfClass(Mob.class, new AABB(closet).inflate(6)).size() > before, "the closet released its swarm");
	}

	/** The vault wall of a content kind nearest the camp, at corridor height. */
	private static BlockPos find(ServerLevel level, PlacedFloor floor, VaultWallBlock.Content content) {
		BlockPos camp = floor.camp();
		int r = floor.radius();
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(camp.offset(-r, 0, -r), camp.offset(r, 3, r))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof VaultWallBlock && state.getValue(VaultWallBlock.CONTENT) == content
					&& (best == null || pos.distSqr(camp) < best.distSqr(camp))) {
				best = pos.immutable();
			}
		}
		return best;
	}
}
