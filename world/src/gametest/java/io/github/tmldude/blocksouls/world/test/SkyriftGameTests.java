package io.github.tmldude.blocksouls.world.test;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.floor.FloorHazards;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Floor 3, Skyrift: opens a Skyrift floor (in the test level, like {@link FloorGameTests}) and checks its look and
 * hazard: the open edge (no glass wall), siege points up on floating shards, the painted biome, the rim-wind gust and
 * the fall back to camp.
 */
public class SkyriftGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";

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
