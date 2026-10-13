package io.github.tmldude.blocksouls.world.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.event.FloorEvents;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.WorldItems;
import io.github.tmldude.blocksouls.world.floor.FloorHazards;
import io.github.tmldude.blocksouls.world.floor.HerbBlock;
import io.github.tmldude.blocksouls.world.floor.FloorBlocks;
import io.github.tmldude.blocksouls.world.floor.NodeBlock;
import io.github.tmldude.blocksouls.world.floor.SkySecret;
import io.github.tmldude.blocksouls.world.floor.SkyTemple;
import io.github.tmldude.blocksouls.world.floor.SkyriftBlock;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Floor 3, Skyrift: opens a Skyrift floor (in the test level, like {@link FloorGameTests}) and checks its look and
 * hazard: the open edge (no glass wall), siege points up on floating shards, the painted biome, the rim-wind gust and
 * the fall back to camp; then pops a skysteel node out on a shard and a cloudbloom herb; then triggers the glowing-seed
 * secret (take it from the sky temple, get hit and watch it dim home, take it again and plant it at the camp altar).
 */
public class SkyriftGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";
	private static final List<String> POPS = new ArrayList<>();

	static {
		FloorEvents.NODE_POPPED.register((player, floor, pos, node) -> POPS.add(node.getPath() + "@" + (floor == null ? "none" : floor.actOrdinal())));
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void skyriftFloorHasShardsAnOpenEdgeAndWind(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			walk(helper, level.getServer(), level, 901);
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void skysteelNodesOnShardsAndCloudbloomHerbsPop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			pops(helper, level, 931); // 93x: clear of the other floors' test acts (900-907)
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void glowingSeedDimsOnAHitAndPaysAtTheCampAltar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			seed(helper, level, 903);
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}

	private static void seed(GameTestHelper helper, ServerLevel level, int act) {
		Optional<PlacedFloor> opened = Floors.open(level.getServer(), new FloorRequest(act, BlockSoulsWorld.id("skyrift"), 3, 1357L, 1));
		helper.assertTrue(opened.isPresent(), "a Skyrift floor opened");
		PlacedFloor floor = opened.get();
		FloorSlot temple = floor.slots(FloorSlotKind.STRUCTURE).stream().filter(slot -> SkyTemple.TAG.equals(slot.tag())).findFirst()
				.orElseThrow(() -> helper.assertionException(Component.literal("the floor has a sky temple, structures " + floor.slots(FloorSlotKind.STRUCTURE))));
		FloorSlot secret = floor.slots(FloorSlotKind.SECRET).stream().filter(slot -> SkyTemple.SEED_SECRET.equals(slot.tag())).findFirst()
				.orElseThrow(() -> helper.assertionException(Component.literal("the floor has the seed secret, secrets " + floor.slots(FloorSlotKind.SECRET))));
		BlockPos pedestal = SkyTemple.pedestal(temple.pos());
		BlockPos altar = secret.pos();
		helper.assertTrue(level.getBlockState(pedestal).is(FloorBlocks.SEED_PEDESTAL) && level.getBlockState(pedestal).getValue(SkyriftBlock.ACTIVE),
				"a lit seed pedestal tops the temple at " + pedestal + ", found " + level.getBlockState(pedestal));
		helper.assertTrue(level.getBlockState(SkyTemple.chest(temple.pos())).is(Blocks.CHEST), "the temple's top chest is there");
		helper.assertTrue(level.getBlockState(altar).is(FloorBlocks.SKY_ALTAR) && !level.getBlockState(altar).getValue(SkyriftBlock.ACTIVE),
				"an unlit sky altar stands by camp at " + altar + ", found " + level.getBlockState(altar));
		helper.assertTrue(altar.closerThan(floor.camp(), 12), "the altar is near camp");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(pedestal.getX() + 0.5, pedestal.getY() + 1, pedestal.getZ() + 0.5);
		SkySecret.take(level, pedestal, level.getBlockState(pedestal), player);
		helper.assertTrue(carried(player) != null, "taking the seed puts it in the inventory");
		helper.assertTrue(!level.getBlockState(pedestal).getValue(SkyriftBlock.ACTIVE), "the pedestal goes dark");

		player.hurtServer(level, level.damageSources().genericKill(), 1.0F);
		helper.assertTrue(carried(player) == null, "a hit takes the seed");
		helper.assertTrue(level.getBlockState(pedestal).getValue(SkyriftBlock.ACTIVE), "and it dims back to its pedestal");

		SkySecret.take(level, pedestal, level.getBlockState(pedestal), player);
		ItemStack seed = carried(player);
		helper.assertTrue(seed != null, "the seed can be taken again");
		player.teleportTo(altar.getX() + 0.5, altar.getY() + 1, altar.getZ() + 0.5);
		int before = items(player);
		SkySecret.plant(level, altar, level.getBlockState(altar), player, seed);
		helper.assertTrue(level.getBlockState(altar).getValue(SkyriftBlock.ACTIVE), "the seed takes root in the altar");
		helper.assertTrue(carried(player) == null, "the seed is spent");
		helper.assertTrue(items(player) >= before || dropped(level, player.blockPosition(), null), "and the planter gets the reward");
	}

	private static @Nullable ItemStack carried(ServerPlayer player) {
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(WorldItems.GLOWING_SEED)) {
				return stack;
			}
		}
		return null;
	}

	private static int items(ServerPlayer player) {
		int count = 0;
		for (ItemStack stack : player.getInventory()) {
			count += stack.isEmpty() ? 0 : 1;
		}
		return count;
	}

	private static void pops(GameTestHelper helper, ServerLevel level, int act) {
		POPS.clear();
		Optional<PlacedFloor> opened = Floors.open(level.getServer(), new FloorRequest(act, BlockSoulsWorld.id("skyrift"), 3, 2468L, 1));
		helper.assertTrue(opened.isPresent(), "a Skyrift floor opened");
		PlacedFloor floor = opened.get();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		FloorSlot skysteel = floor.slots(FloorSlotKind.NODE).stream().filter(slot -> slot.tag().endsWith("skysteel_node")).findFirst()
				.orElseThrow(() -> helper.assertionException(Component.literal("the floor has a skysteel node, nodes " + floor.slots(FloorSlotKind.NODE))));
		helper.assertTrue(skysteel.pos().getY() > floor.groundY() + 3, "the skysteel node sits up on a shard at " + skysteel.pos());
		BlockState state = level.getBlockState(skysteel.pos());
		helper.assertTrue(state.getBlock() instanceof NodeBlock, "the skysteel slot holds a node, found " + state);
		int hits = 0;
		while (!((NodeBlock) state.getBlock()).hit(level, skysteel.pos(), player)) {
			helper.assertTrue(++hits < 32, "the skysteel node pops eventually");
		}
		helper.assertTrue(level.getBlockState(skysteel.pos()).isAir(), "a popped skysteel node is gone");
		helper.assertTrue(dropped(level, skysteel.pos(), WorldItems.SKYSTEEL) || dropped(level, skysteel.pos(), null), "the node burst its loot");
		helper.assertTrue(POPS.contains("skysteel_node@" + act), "NODE_POPPED names the skysteel node and its floor, saw " + POPS);

		FloorSlot herb = floor.slots(FloorSlotKind.NODE).stream().filter(slot -> slot.tag().endsWith("cloudbloom_plant")).findFirst()
				.orElseThrow(() -> helper.assertionException(Component.literal("the floor has a cloudbloom, nodes " + floor.slots(FloorSlotKind.NODE))));
		BlockState herbState = level.getBlockState(herb.pos());
		helper.assertTrue(herbState.getBlock() instanceof HerbBlock, "the herb slot holds a cloudbloom, found " + herbState);
		helper.assertTrue(((HerbBlock) herbState.getBlock()).hit(level, herb.pos(), player), "one hit picks a herb");
		helper.assertTrue(dropped(level, herb.pos(), WorldItems.CLOUDBLOOM), "the herb drops cloudbloom");
		helper.assertTrue(POPS.contains("cloudbloom_plant@" + act), "NODE_POPPED names the herb and its floor, saw " + POPS);
	}

	/** An item entity near {@code pos}: of {@code item}, or of anything when it is null. */
	private static boolean dropped(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item item) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream().anyMatch(entity -> item == null || entity.getItem().is(item));
	}

	private static void walk(GameTestHelper helper, MinecraftServer server, ServerLevel level, int act) {
		Optional<PlacedFloor> opened = Floors.open(server, new FloorRequest(act, BlockSoulsWorld.id("skyrift"), 3, 4321L, 1));
		helper.assertTrue(opened.isPresent(), "a Skyrift floor opened");
		PlacedFloor floor = opened.get();
		helper.assertTrue(floor.hasTag("skyrift"), "the floor is tagged skyrift, tags " + floor.tags());
		for (FloorSlotKind kind : FloorSlotKind.values()) {
			helper.assertTrue(!floor.slots(kind).isEmpty(), "the floor has a " + kind + " slot");
		}

		BlockPos camp = floor.camp();
		for (int i = 0; i < 16; i++) {
			double angle = i * Math.PI / 8;
			int x = camp.getX() + (int) Math.round(Math.cos(angle) * (floor.radius() - 0.6));
			int z = camp.getZ() + (int) Math.round(Math.sin(angle) * (floor.radius() - 0.6));
			for (int y = floor.groundY() - 6; y <= floor.groundY() + 8; y++) {
				helper.assertTrue(!level.getBlockState(new BlockPos(x, y, z)).is(Blocks.GLASS), "no glass wall at the edge");
			}
		}

		long raised = floor.slots(FloorSlotKind.SIEGE_RING).stream().filter(slot -> slot.pos().getY() > floor.groundY() + 6).count();
		helper.assertTrue(raised > 0, "some siege points sit up on shards");
		for (FloorSlot point : floor.slots(FloorSlotKind.SIEGE_RING)) {
			if (point.pos().getY() > floor.groundY() + 6) {
				helper.assertTrue(level.getBlockState(point.pos().below()).isSolid(), "a raised siege point stands on a shard at " + point.pos());
				boolean floats = false;
				for (int y = point.pos().getY() - 2; y > floor.groundY() && !floats; y--) {
					floats = level.getBlockState(point.pos().atY(y)).isAir();
				}
				helper.assertTrue(floats, "the shard at " + point.pos() + " floats");
			}
		}

		helper.assertTrue(level.getBiome(camp).is(ResourceKey.create(Registries.BIOME, BlockSoulsWorld.id("skyrift"))), "the floor wears its biome");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		double edge = floor.radius() - 3;
		player.teleportTo(camp.getX() + 0.5 + edge, floor.groundY() + 2, camp.getZ() + 0.5);
		player.setDeltaMovement(Vec3.ZERO);
		String gust = FloorHazards.get("gust").fire(player, floor);
		helper.assertTrue(player.getDeltaMovement().x > 0.05, "the gust pushes outward (" + gust + "), motion " + player.getDeltaMovement());

		player.setHealth(20.0F);
		FloorHazards.get("fall").fire(player, floor);
		helper.assertTrue(player.blockPosition().closerThan(camp, 3), "a fall sends the player back to camp, at " + player.blockPosition());
		helper.assertTrue(player.getHealth() == 20.0F - WorldFloors.config().fallPenalty, "and costs the fall penalty");
	}
}
