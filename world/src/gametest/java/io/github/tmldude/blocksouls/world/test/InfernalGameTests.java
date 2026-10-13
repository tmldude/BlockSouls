package io.github.tmldude.blocksouls.world.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.InfernalContent;
import io.github.tmldude.blocksouls.world.floor.FloorBlocks;
import io.github.tmldude.blocksouls.world.floor.FloorPlanner;
import io.github.tmldude.blocksouls.world.floor.FloorTemplate;
import io.github.tmldude.blocksouls.world.floor.FloorTemplateCatalog;
import io.github.tmldude.blocksouls.world.floor.InfernalEvents;
import io.github.tmldude.blocksouls.world.floor.InfernalFortress;
import io.github.tmldude.blocksouls.world.floor.InfernalHazard;
import io.github.tmldude.blocksouls.world.floor.LavaChannels;
import io.github.tmldude.blocksouls.world.floor.NodeBlock;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Floor 2, Infernal (v2-floor2-infernal): opens an Infernal floor in the test level and checks the lava channels, both
 * kinds of bridge, the island's biome, the lava rise (low bridges sink, the stone arch stands) and that lava sends a
 * player back to the camp unless they drank the Ashcap Tonic. Slice 2: pops an ashcap and a cinder ore node, and drinks
 * both brews. Slice 3: douses the fortress's soulfire door with a water bucket and finds the vault chest behind it, and
 * places the stranded trader event. Act 910 (Infernal owns 910-919) keeps clear of real runs and the other floors' test
 * acts (900-907, 93x).
 */
public class InfernalGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 200)
	public void infernalFloorHasLavaBridgesAndRises(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldFloors.useLevelForTests(level.dimension());
		try {
			walk(helper, level.getServer(), level, 910);
		} finally {
			WorldFloors.useLevelForTests(null);
		}
		helper.succeed();
	}

	private static boolean dropped(ServerLevel level, BlockPos pos, Item item) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream().anyMatch(drop -> drop.getItem().is(item));
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

		// Slice 2: the ashcaps pop in one hit, a cinder ore node in nodeHits, each into its loot.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<FloorSlot> herbs = floor.slots(FloorSlotKind.NODE).stream().filter(slot -> slot.tag().equals("blocksouls_world:ashcap")).toList();
		helper.assertTrue(herbs.size() >= 2 && herbs.size() <= 3, "two or three ashcaps, found " + herbs.size());
		BlockPos herb = herbs.getFirst().pos();
		helper.assertTrue(level.getBlockState(herb).is(FloorBlocks.ASHCAP), "an ashcap grows at " + herb.toShortString());
		helper.assertTrue(((NodeBlock) FloorBlocks.ASHCAP).hit(level, herb, player), "one hit pops the ashcap");
		helper.assertTrue(level.getBlockState(herb).isAir(), "the popped ashcap is gone");
		helper.assertTrue(dropped(level, herb, InfernalContent.ASHCAP), "the ashcap dropped ashcap");
		BlockPos ore = floor.slots(FloorSlotKind.NODE).stream().filter(slot -> slot.tag().equals("blocksouls_world:cinder_ore"))
				.map(FloorSlot::pos).findFirst().orElse(null);
		if (ore == null) { // this seed rolled no cinder ore: stand one up by the camp
			ore = camp.above(4);
			level.setBlock(ore, FloorBlocks.CINDER_ORE.defaultBlockState(), Block.UPDATE_ALL);
		}
		helper.assertTrue(level.getBlockState(ore).is(FloorBlocks.CINDER_ORE), "cinder ore at " + ore.toShortString());
		for (int i = 1; i < WorldFloors.config().nodeHits; i++) {
			helper.assertTrue(!((NodeBlock) FloorBlocks.CINDER_ORE).hit(level, ore, player), "hit " + i + " doesn't pop the cinder ore");
		}
		helper.assertTrue(((NodeBlock) FloorBlocks.CINDER_ORE).hit(level, ore, player), "the last hit pops the cinder ore");
		helper.assertTrue(dropped(level, ore, InfernalContent.CINDER), "the cinder ore burst cinder");

		// Slice 3: the fortress's soulfire door goes out under a water bucket and opens the vault; the water pocket is a spring.
		FloorSlot pocket = floor.slots(FloorSlotKind.SECRET).stream().filter(slot -> slot.tag().equals(InfernalFortress.WATER_POCKET))
				.findFirst().orElse(null);
		helper.assertTrue(pocket != null && level.getBlockState(pocket.pos().below()).is(Blocks.WATER), "the water pocket holds water");
		FloorSlot door = floor.slots(FloorSlotKind.SECRET).stream().filter(slot -> slot.tag().equals(InfernalFortress.SOULFIRE_DOOR))
				.findFirst().orElse(null);
		helper.assertTrue(door != null, "the fortress recorded its soulfire door");
		BlockPos seal = null;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(door.pos().relative(direction)).is(FloorBlocks.SOULFIRE_SEAL)) {
				seal = door.pos().relative(direction);
			}
		}
		helper.assertTrue(seal != null, "a seal block beside the door slot at " + door.pos().toShortString());
		helper.assertTrue(level.getBlockState(seal.above()).is(FloorBlocks.SOULFIRE_SEAL), "the door is two high");
		helper.assertTrue(floor.slots(FloorSlotKind.CRATE).stream().anyMatch(slot -> slot.tag().equals(InfernalFortress.BASTION_LOOT.identifier().toString())),
				"the fortress hall has its bastion chest");
		ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
		player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
		InteractionResult doused = level.getBlockState(seal).useItemOn(bucket, level, player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(seal), Direction.UP, seal, false));
		helper.assertTrue(doused.consumesAction(), "the water bucket works on the seal");
		helper.assertTrue(level.getBlockState(seal).isAir() && level.getBlockState(seal.above()).isAir(), "the whole door went out");
		BlockPos vault = seal;
		helper.assertTrue(floor.slots(FloorSlotKind.CRATE).stream().anyMatch(slot -> slot.tag().equals(InfernalFortress.VAULT_LOOT.identifier().toString())
				&& slot.pos().distManhattan(vault) <= 6 && level.getBlockState(slot.pos()).is(Blocks.CHEST)), "the vault chest waits behind the door");

		// Slice 3: the stranded trader stands on his pillar in a lava moat, fire-proof, selling brews for cinder.
		BlockPos spot = floor.slots(FloorSlotKind.EVENT).getFirst().pos();
		helper.assertTrue(InfernalEvents.place(level, InfernalEvents.STRANDED_TRADER, spot), "the stranded trader event places");
		helper.assertTrue(level.getBlockState(spot.offset(3, -1, 0)).is(Blocks.LAVA), "a lava moat round the pillar");
		WanderingTrader trader = level.getEntitiesOfClass(WanderingTrader.class, new AABB(spot).inflate(3)).stream().findFirst().orElse(null);
		helper.assertTrue(trader != null && trader.hasEffect(MobEffects.FIRE_RESISTANCE), "a fire-proof trader on the pillar");
		helper.assertTrue(trader.getOffers().stream().anyMatch(offer -> offer.getResult().is(InfernalContent.ASHCAP_TONIC)
				&& offer.getCostA().is(InfernalContent.CINDER)), "he sells the Ashcap Tonic for cinder");
		trader.discard();

		// The brews: the Scorch Draught's hits set the target burning.
		InfernalContent.SCORCH_DRAUGHT.finishUsingItem(new ItemStack(InfernalContent.SCORCH_DRAUGHT), level, player);
		helper.assertTrue(player.hasEffect(InfernalContent.SCORCHING), "the draught gives Scorching");
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(2, 2, 2));
		pig.hurtServer(level, player.damageSources().playerAttack(player), 1.0F);
		helper.assertTrue(pig.getRemainingFireTicks() > 0, "a Scorching hit sets the target burning");

		// Lava sends you back to the camp; the Ashcap Tonic's fire resistance lets you wade.
		player.teleportTo(lavaCell.getX() + 0.5, lavaCell.getY(), lavaCell.getZ() + 0.5);
		helper.assertTrue(!InfernalHazard.checkLava(player), "creative players are exempt");
		helper.assertTrue(InfernalHazard.burn(player), "lava sends the player back");
		helper.assertTrue(player.blockPosition().distManhattan(camp) <= 4, "back at the camp, at " + player.blockPosition().toShortString());
		ItemStack tonic = new ItemStack(InfernalContent.ASHCAP_TONIC);
		UseRemainder remainder = tonic.get(DataComponents.USE_REMAINDER);
		helper.assertTrue(remainder != null && remainder.convertInto().create().is(Items.GLASS_BOTTLE), "the tonic leaves its bottle");
		InfernalContent.ASHCAP_TONIC.finishUsingItem(tonic, level, player); // creative mock: the stack isn't used up
		helper.assertTrue(player.hasEffect(MobEffects.FIRE_RESISTANCE), "the tonic gives fire resistance");
		player.teleportTo(lavaCell.getX() + 0.5, lavaCell.getY(), lavaCell.getZ() + 0.5);
		helper.assertTrue(!InfernalHazard.burn(player), "fire resistance wades the lava");

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
