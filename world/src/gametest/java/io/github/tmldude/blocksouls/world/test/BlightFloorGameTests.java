package io.github.tmldude.blocksouls.world.test;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Floor 5, the Blighted Surface (v2-floor5-blight): the blight pool opens a blight template for floor 5 with every slot
 * kind, the floor carries its {@code blight} tag, and the island is painted with the blight biome (fog, sky, grass).
 */
public class BlightFloorGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void blightFloorOpensWithItsLookAndTags(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			Optional<PlacedFloor> opened = Floors.open(server, new FloorRequest(905, BlockSoulsWorld.id("blight"), 5, 4321L, 1));
			helper.assertTrue(opened.isPresent(), "a Blighted Surface floor opened");
			PlacedFloor floor = opened.get();
			helper.assertTrue(floor.template().getPath().startsWith("blight_"), "a blight template, got " + floor.template());
			helper.assertTrue(floor.hasTag("blight") && floor.hasTag("elite_affixes") && floor.hasTag("floor_5"), "blight + floor-5 tags: " + floor.tags());
			for (FloorSlotKind kind : FloorSlotKind.values()) {
				helper.assertTrue(!floor.slots(kind).isEmpty(), "the floor has a " + kind + " slot");
			}
			ResourceKey<net.minecraft.world.level.biome.Biome> blight = ResourceKey.create(Registries.BIOME, BlockSoulsWorld.id("blight"));
			helper.assertTrue(level.getBiome(floor.camp()).is(blight), "the camp sits in the blight biome");
			helper.assertTrue(level.getBiome(floor.camp().offset(floor.radius() - 2, 0, 0)).is(blight), "so does the island's edge");
			helper.assertTrue(!level.getBlockState(floor.camp().below()).isAir(), "the camp stands on ground");
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}
}
