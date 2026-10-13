package io.github.tmldude.blocksouls.world.test;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.floor.FloorPlanner;
import io.github.tmldude.blocksouls.world.floor.FloorTemplate;
import io.github.tmldude.blocksouls.world.floor.FloorTemplateCatalog;
import io.github.tmldude.blocksouls.world.floor.InfernalHazard;
import io.github.tmldude.blocksouls.world.floor.LavaChannels;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Floor 2, Infernal (v2-floor2-infernal): opens an Infernal floor in the test level and checks the lava channels, both
 * kinds of bridge, the island's biome, the lava rise (low bridges sink, the stone arch stands) and that lava sends a
 * player back to the camp unless they drank fire resistance. Act 901 keeps clear of real runs and FloorGameTests.
 */
public class InfernalGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void infernalFloorHasLavaBridgesAndRises(GameTestHelper helper) {
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
		Optional<PlacedFloor> opened = Floors.open(server, new FloorRequest(act, BlockSoulsWorld.id("infernal"), 2, 4321L, 1));
		helper.assertTrue(opened.isPresent(), "an Infernal floor opened");
		PlacedFloor floor = opened.get();
		helper.assertTrue(floor.tags().contains(InfernalHazard.TAG), "the floor is tagged infernal, tags " + floor.tags());
		FloorTemplate template = FloorTemplateCatalog.get(floor.template().toString());
		helper.assertTrue(template != null && template.channels != null, "the template has lava channels");
		LavaChannels.Plan channels = FloorPlanner.channels(template, floor.radius(), floor.seed());
		int lava = InfernalHazard.lavaY(floor);
		BlockPos camp = floor.camp();

		helper.assertTrue(level.getBiome(camp).is(ResourceKey.create(Registries.BIOME, BlockSoulsWorld.id("infernal"))),
				"the island wears the Infernal biome");

		// A channel column away from every bridge holds lava, one block of it.
		BlockPos lavaCell = null;
		for (int degrees = 0; degrees < 360 && lavaCell == null; degrees += 5) {
			double angle = Math.toRadians(degrees);
			int x = (int) Math.round(Math.cos(angle) * channels.ringRadius());
			int z = (int) Math.round(Math.sin(angle) * channels.ringRadius());
			if (channels.inChannel(x, z) && channels.bridgeAt(x, z) == null) {
				lavaCell = camp.offset(x, 0, z).atY(lava);
			}
		}
		helper.assertTrue(lavaCell != null, "found an open stretch of the ring channel");
		helper.assertTrue(level.getBlockState(lavaCell).is(Blocks.LAVA), "the channel holds lava at " + lavaCell.toShortString());
		helper.assertTrue(level.getBlockState(lavaCell.above()).isAir(), "air over the lava before the rise");

		// One low (planks, over the lava) and one high (stone, level with the ground) bridge cell.
		BlockPos low = null;
		BlockPos high = null;
		for (LavaChannels.Bridge bridge : channels.bridges()) {
			int x = (int) Math.round(bridge.x());
			int z = (int) Math.round(bridge.z());
			if (!channels.inChannel(x, z)) {
				continue;
			}
			if (bridge.high() && high == null) {
				high = camp.offset(x, 0, z).atY(floor.groundY());
			} else if (!bridge.high() && low == null) {
				low = camp.offset(x, 0, z).atY(lava + 1);
			}
		}
		helper.assertTrue(low != null && high != null, "the floor has low and high bridges");
		helper.assertTrue(level.getBlockState(low).is(Blocks.CRIMSON_PLANKS), "a low bridge deck at " + low.toShortString());
		helper.assertTrue(level.getBlockState(high).is(Blocks.POLISHED_BLACKSTONE_BRICKS), "the stone arch at " + high.toShortString());

		// Lava sends you back to the camp; fire resistance lets you wade.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.teleportTo(lavaCell.getX() + 0.5, lavaCell.getY(), lavaCell.getZ() + 0.5);
		helper.assertTrue(InfernalHazard.checkLava(player), "lava sends the player back");
		helper.assertTrue(player.blockPosition().distManhattan(camp) <= 4, "back at the camp, at " + player.blockPosition().toShortString());
		player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
		player.teleportTo(lavaCell.getX() + 0.5, lavaCell.getY(), lavaCell.getZ() + 0.5);
		helper.assertTrue(!InfernalHazard.checkLava(player), "fire resistance wades the lava");

		// The rise: low decks sink, the arch stands, the lava is a block higher.
		helper.assertTrue(InfernalHazard.schedule(server, floor), "the siege clock starts on an Infernal floor");
		helper.assertTrue(InfernalHazard.pending(act), "the rise is pending");
		helper.assertTrue(InfernalHazard.rise(server, floor) > 0, "the lava rose");
		helper.assertTrue(level.getBlockState(low).is(Blocks.LAVA), "the low bridge sank");
		helper.assertTrue(level.getBlockState(high).is(Blocks.POLISHED_BLACKSTONE_BRICKS), "the arch survives the rise");
		helper.assertTrue(level.getBlockState(lavaCell.above()).is(Blocks.LAVA), "the lava is a block higher");
		helper.assertTrue(!level.getBlockState(camp.below()).isAir() && !level.getBlockState(camp).is(Blocks.LAVA), "the camp stays dry");
	}
}
