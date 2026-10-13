package io.github.tmldude.blocksouls.world.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.event.DirectorEvents;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.WorldItems;
import io.github.tmldude.blocksouls.world.floor.NodeBlock;
import io.github.tmldude.blocksouls.world.floor.VaultBlocks;
import io.github.tmldude.blocksouls.world.floor.VaultBoss;
import io.github.tmldude.blocksouls.world.floor.VaultColossus;
import io.github.tmldude.blocksouls.world.floor.VaultEvents;
import io.github.tmldude.blocksouls.world.floor.VaultListener;
import io.github.tmldude.blocksouls.world.floor.VaultLockBlock;
import io.github.tmldude.blocksouls.world.floor.VaultWallBlock;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Walks floor 4, The Vault: opens a vault floor in the test level and checks the camp is roofed in, a node, a deep iron
 * wall node and a cave moss patch pop, plain walls hold, the hollow wall bursts open on its secret (waking the
 * mini-boss in the chamber up the hidden stair), a mob closet releases its swarm, three keys open the vault room, and
 * the Vault's two Director events build.
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
		FloorSlot node = floor.slots(FloorSlotKind.NODE).stream().filter(slot -> !slot.tag().equals("blocksouls_world:deep_iron_node")
				&& !slot.tag().equals("blocksouls_world:cave_moss_patch")).findFirst().orElseThrow();
		BlockState nodeState = level.getBlockState(node.pos());
		helper.assertTrue(nodeState.getBlock() instanceof NodeBlock, "node slot holds a node, found " + nodeState);
		NodeBlock block = (NodeBlock) nodeState.getBlock();
		for (int i = 0; i < 8 && !block.hit(level, node.pos(), player); i++) {
			// hit until it pops
		}
		helper.assertTrue(level.getBlockState(node.pos()).isAir(), "a popped node is gone");
		helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(node.pos()).inflate(3)).isEmpty(), "the node burst its loot");

		FloorSlot deepIron = floor.slots(FloorSlotKind.NODE).stream().filter(slot -> slot.tag().equals("blocksouls_world:deep_iron_node")).findFirst()
				.orElse(null);
		helper.assertTrue(deepIron != null && level.getBlockState(deepIron.pos()).is(VaultBlocks.DEEP_IRON_NODE), "deep iron is set into a wall");
		for (int i = 0; i < 8 && !VaultBlocks.DEEP_IRON_NODE.hit(level, deepIron.pos(), player); i++) {
			// hit until it pops
		}
		helper.assertTrue(level.getBlockState(deepIron.pos()).isAir(), "the deep iron node popped");
		helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(deepIron.pos()).inflate(3), e -> e.getItem().is(WorldItems.DEEP_IRON)
				|| e.getItem().is(WorldItems.SCRAP)).isEmpty(), "the deep iron node burst its loot");

		List<FloorSlot> herbs = floor.slots(FloorSlotKind.NODE).stream().filter(slot -> slot.tag().equals("blocksouls_world:cave_moss_patch")).toList();
		helper.assertTrue(herbs.size() >= 2 && herbs.size() <= 3, "2–3 cave moss patches, saw " + herbs.size());
		FloorSlot herb = herbs.getFirst();
		helper.assertTrue(level.getBlockState(herb.pos()).is(VaultBlocks.CAVE_MOSS_PATCH), "a cave moss patch grows at " + herb.pos().toShortString());
		helper.assertTrue(VaultBlocks.CAVE_MOSS_PATCH.hit(level, herb.pos(), player), "cave moss pops in one hit");
		helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(herb.pos()).inflate(3), e -> e.getItem().is(WorldItems.CAVE_MOSS)).isEmpty(),
				"the cave moss dropped");

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

		// --- PR 3: the secret's mini-boss, the vault room's lock, the Vault's events ---
		AABB whole = new AABB(floor.camp()).inflate(floor.radius() + 2, 40, floor.radius() + 2);
		List<Mob> secret = level.getEntitiesOfClass(Mob.class, whole, mob -> mob.entityTags().contains(VaultBoss.SECRET));
		helper.assertTrue(secret.size() == 1, "the hollow wall woke one mini-boss in the chamber, saw " + secret.size());
		Mob boss = secret.getFirst();
		helper.assertTrue(boss instanceof VaultListener || boss instanceof VaultColossus, "the secret's mini-boss is the Listener or the Colossus");
		helper.assertTrue(boss.getY() > floor.groundY() + 9, "it waits in the chamber over every ceiling, at y " + boss.getY());
		helper.assertTrue(!level.getBlockState(boss.blockPosition().below()).is(Blocks.REINFORCED_DEEPSLATE), "its seal is gone");
		boolean stair = false;
		for (BlockPos at : BlockPos.betweenClosed(boss.blockPosition().offset(-12, -2, -12), boss.blockPosition().offset(12, -1, 12))) {
			stair |= level.getBlockState(at).isAir() && level.getBlockState(at.below()).isAir() && level.getBlockState(at.below(4)).isAir();
		}
		helper.assertTrue(stair, "the hidden stair's shaft opens into the chamber floor");
		boss.kill(level);

		helper.assertTrue(floor.slots(FloorSlotKind.CRATE).stream().filter(slot -> slot.tag().equals("blocksouls_world:chests/vault_key")).count() == 3,
				"three crates hold the vault keys");
		BlockPos room = floor.slots(FloorSlotKind.STRUCTURE).getFirst().pos();
		BlockPos lock = null;
		for (BlockPos at : BlockPos.betweenClosed(room.offset(-2, 0, -2), room.offset(2, 0, 2))) {
			if (level.getBlockState(at).is(VaultBlocks.VAULT_LOCK)) {
				lock = at.immutable();
			}
		}
		helper.assertTrue(lock != null && level.getBlockState(lock.above()).is(VaultBlocks.VAULT_LOCK), "the vault room's door is a two-high lock");
		helper.assertTrue(VaultLockBlock.insertKey(level, lock, player) == 1 && VaultLockBlock.insertKey(level, lock.above(), player) == 2,
				"each key counts for the whole door");
		helper.assertTrue(level.getBlockState(lock).is(VaultBlocks.VAULT_LOCK), "two keys don't open it");
		VaultLockBlock.insertKey(level, lock, player);
		helper.assertTrue(level.getBlockState(lock).isAir() && level.getBlockState(lock.above()).isAir(), "the third key opens the vault");
		boolean treasure = false;
		for (BlockPos at : BlockPos.betweenClosed(room.offset(-1, 0, -1), room.offset(1, 0, 1))) {
			treasure |= level.getBlockState(at).is(Blocks.CHEST);
		}
		helper.assertTrue(treasure, "the treasure chest waits inside");

		BlockPos prison = floor.slots(FloorSlotKind.EVENT).getFirst().pos();
		helper.assertTrue(DirectorEvents.PLACE_EVENT.invoker().place(level, VaultEvents.VAULT_PRISON, prison, floor), "the world places the vault prison");
		helper.assertTrue(level.getBlockState(prison.offset(2, 1, 0)).is(Blocks.IRON_BARS) && level.getBlockState(prison.above(3)).is(Blocks.IRON_BARS),
				"the prison is a roofed cage");
		helper.assertTrue(!level.getEntitiesOfClass(IronGolem.class, new AABB(prison).inflate(2), IronGolem::isPlayerCreated).isEmpty(),
				"a golem ally waits in the cage");
		BlockPos hall = floor.slots(FloorSlotKind.MINI_BOSS).getFirst().pos();
		helper.assertTrue(DirectorEvents.PLACE_EVENT.invoker().place(level, VaultEvents.TRAPPED_HALL, hall, floor), "the world places the trapped hall");
		helper.assertTrue(level.getBlockState(hall.below()).is(VaultBlocks.VAULT_FLAME_TRAP), "the hall floor is flame grates");
		helper.assertTrue(level.getBlockState(hall.offset(0, 0, VaultEvents.HALL_ROWS / 2 + 1)).is(Blocks.CHEST), "a chest waits past the grates");
		// --- end PR 3 ---
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
