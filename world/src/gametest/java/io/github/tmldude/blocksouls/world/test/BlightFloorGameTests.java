package io.github.tmldude.blocksouls.world.test;

import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.floor.FloorBlocks;
import io.github.tmldude.blocksouls.world.floor.HerbBlock;
import io.github.tmldude.blocksouls.world.floor.NodeBlock;
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

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void blightCrystalAndGhostcapPop(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			PlacedFloor floor = Floors.open(server, new FloorRequest(906, BlockSoulsWorld.id("blight"), 5, 98L, 1)).orElseThrow();
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			FloorSlot crystal = slot(floor, CommonIds.BLIGHT_CRYSTAL_NODE.toString());
			FloorSlot herb = slot(floor, CommonIds.GHOSTCAP.toString());
			helper.assertTrue(crystal != null && herb != null, "the floor has a blight crystal node and a ghostcap");
			helper.assertTrue(floor.slots(FloorSlotKind.NODE).stream().filter(s -> s.tag().equals(CommonIds.GHOSTCAP.toString())).count() == 2,
					"a solo floor grows 2 ghostcaps");

			BlockState state = level.getBlockState(crystal.pos());
			helper.assertTrue(state.is(FloorBlocks.BLIGHT_CRYSTAL_NODE), "the crystal slot holds the node, found " + state);
			NodeBlock node = (NodeBlock) state.getBlock();
			boolean popped = false;
			for (int i = 0; i < 8 && !popped; i++) {
				popped = node.hit(level, crystal.pos(), player);
			}
			helper.assertTrue(popped && level.getBlockState(crystal.pos()).isAir(), "the crystal node pops");
			helper.assertTrue(!level.getEntitiesOfClass(ItemEntity.class, new AABB(crystal.pos()).inflate(3)).isEmpty(), "and bursts its loot");

			state = level.getBlockState(herb.pos());
			helper.assertTrue(state.getBlock() instanceof HerbBlock, "the herb slot holds a ghostcap, found " + state);
			helper.assertTrue(((HerbBlock) state.getBlock()).hit(level, herb.pos(), player), "a ghostcap pops in one hit");
			helper.assertTrue(level.getBlockState(herb.pos()).isAir(), "and is gone");
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(herb.pos()).inflate(2)).stream()
					.anyMatch(e -> BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).equals(CommonIds.GHOSTCAP)), "it drops a ghostcap");
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}

	private static FloorSlot slot(PlacedFloor floor, String tag) {
		return floor.slots(FloorSlotKind.NODE).stream().filter(s -> s.tag().equals(tag)).findFirst().orElse(null);
	}

	/** Slice 3: the nest (outpost + legendary chest + cursed chest beside it) and the grave (soft soil over an exotic chest). */
	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void nestAndGraveAreBuilt(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			PlacedFloor floor = Floors.open(server, new FloorRequest(907, BlockSoulsWorld.id("blight"), 5, 77L, 1)).orElseThrow();
			FloorSlot nest = floor.slots(FloorSlotKind.STRUCTURE).getFirst();
			helper.assertTrue(nest.tag().equals("nest"), "the structure is the nest, got " + nest.tag());
			boolean nestChest = false;
			boolean cursed = false;
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(nest.pos().offset(-12, -4, -12), nest.pos().offset(12, 8, 12))) {
				if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
						&& chest.getLootTable() != null && chest.getLootTable().identifier().getPath().equals("chests/blight_nest")) {
					nestChest = true;
				}
				cursed |= level.getBlockState(pos).is(io.github.tmldude.blocksouls.world.economy.WorldEconomy.CURSED_CHEST);
			}
			helper.assertTrue(nestChest, "the nest holds a blight_nest chest");
			helper.assertTrue(cursed, "a cursed chest sits beside it");

			FloorSlot grave = floor.slots(FloorSlotKind.SECRET).getFirst();
			helper.assertTrue(grave.tag().equals("grave"), "the secret is the grave, got " + grave.tag());
			helper.assertTrue(level.getBlockState(grave.pos().below()).is(net.minecraft.world.level.block.Blocks.SOUL_SOIL), "soft soil over the grave");
			net.minecraft.core.BlockPos chestPos = grave.pos().below(3); // FloorBuilder.graveChest
			helper.assertTrue(level.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
					&& chest.getLootTable() != null && chest.getLootTable().identifier().getPath().equals("chests/blight_grave"),
					"the grave chest holds the exotic table, found " + level.getBlockState(chestPos));
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}
}
