package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.stormplate.DragonscaleArmor;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateKnight;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateKnights;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateRuin;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.BossEvents;

/** The Stormplate Knight: its ruin, its phases, Thunder call, the dragonscale set and the kill event. */
public class StormplateGameTests {
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	private static final List<Kill> KILLS = new ArrayList<>();

	private record Kill(UUID boss, Identifier id, List<UUID> players) {
	}

	static {
		BossEvents.KILLED.register((level, bossId, boss, players) ->
				KILLS.add(new Kill(boss.getUUID(), bossId, players.stream().map(Entity::getUUID).toList())));
	}

	private static void floor(GameTestHelper helper, BlockState state) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, state);
			}
		}
	}

	/** A knight at the center with an arena that fits the test area (no ruin: tests don't run finalizeSpawn). */
	private static StormplateKnight knight(GameTestHelper helper) {
		floor(helper, Blocks.STONE.defaultBlockState());
		StormplateKnight knight = helper.spawn(StormplateKnights.TYPE, CENTER);
		knight.setHome(helper.absolutePos(CENTER));
		knight.setArenaRadius(3);
		return knight;
	}

	private static ServerPlayer outsider(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(CENTER).offset(200, 0, 200);
		player.snapTo(far.getX(), far.getY(), far.getZ());
		return player;
	}

	private static Attack attack(StormplateKnight knight, String name) {
		return knight.definition().attacks().stream().filter(a -> a.name().equals(name)).findFirst().orElseThrow();
	}

	@GameTest(environment = ENV)
	public void stormplateIsRegisteredTaggedAndLightningProof(GameTestHelper helper) {
		StormplateKnight knight = knight(helper);
		helper.assertTrue(knight.is(CommonTags.BOSSES), "Stormplate Knight missing from #blocksouls:bosses");
		helper.assertTrue(Bosses.type(CommonIds.STORMPLATE_KNIGHT).isPresent(), "not in the boss registry");
		helper.assertTrue(knight.getMaxHealth() >= StormplateKnight.config().health - 0.01, "base health " + knight.getMaxHealth());
		helper.assertTrue(knight.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "should be knockback-immune");
		float before = knight.getHealth();
		knight.hurtServer(helper.getLevel(), helper.getLevel().damageSources().lightningBolt(), 20);
		helper.assertTrue(knight.getHealth() == before, "took lightning damage");
		helper.succeed();
	}

	/** The ruin fills grass/dirt and open air only: planks, stone and glass a player put down stay exactly as they were. */
	@GameTest(environment = ENV)
	public void ruinNeverReplacesNonNaturalBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, Blocks.GRASS_BLOCK.defaultBlockState());
		helper.setBlock(2, 0, 2, Blocks.OAK_PLANKS);
		helper.setBlock(5, 0, 5, Blocks.STONE);
		helper.setBlock(1, 1, 1, Blocks.OAK_PLANKS);
		helper.setBlock(6, 1, 2, Blocks.GLASS);
		helper.setBlock(4, 1, 5, Blocks.COBBLESTONE_WALL);
		helper.setBlock(3, 1, 6, Blocks.SHORT_GRASS);
		Map<BlockPos, BlockState> before = new HashMap<>();
		for (int x = -1; x < 9; x++) {
			for (int y = 0; y < 10; y++) {
				for (int z = -1; z < 9; z++) {
					BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
					before.put(pos, level.getBlockState(pos));
				}
			}
		}
		List<BlockPos> changed = StormplateRuin.build(level, helper.absolutePos(CENTER), 3, 4, 1.0, 2, RandomSource.create(42));
		helper.assertTrue(!changed.isEmpty(), "the ruin placed nothing");
		for (BlockPos pos : changed) {
			BlockState was = before.get(pos);
			helper.assertTrue(was != null && StormplateRuin.isNatural(was), "replaced " + was + " at " + pos);
		}
		helper.assertBlockPresent(Blocks.OAK_PLANKS, 2, 0, 2);
		helper.assertBlockPresent(Blocks.STONE, 5, 0, 5);
		helper.assertBlockPresent(Blocks.OAK_PLANKS, 1, 1, 1);
		helper.assertBlockPresent(Blocks.GLASS, 6, 1, 2);
		helper.assertBlockPresent(Blocks.COBBLESTONE_WALL, 4, 1, 5);
		int floorY = helper.absolutePos(CENTER).getY() - 1;
		helper.assertTrue(changed.stream().anyMatch(pos -> pos.getY() == floorY), "no floor laid");
		helper.succeed();
	}

	/** Scripted health walks P1 → P2: a stagger, attacks keep finishing, and the floor starts to crackle. */
	@GameTest(environment = ENV, maxTicks = 900)
	public void phaseMachineWalksToPhase2(GameTestHelper helper) {
		StormplateKnight knight = knight(helper);
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(6, 1, 4));
		pig.setNoAi(true);
		pig.setPermanentlyInvulnerable(true);
		knight.engage(outsider(helper));
		knight.forceTarget(pig);
		knight.setHealth(knight.getMaxHealth() * 0.7f);
		int[] p1Attacks = {-1};
		int[] staggered = {0};
		int[] puddles = {0};
		helper.onEachTick(() -> {
			if (knight.isStaggered()) {
				staggered[0]++;
			}
			puddles[0] = Math.max(puddles[0], knight.puddleCount());
		});
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(knight.phase() == 0, "still phase 1 at 70%");
			helper.assertTrue(knight.attacksFinished() >= 2, "only " + knight.attacksFinished() + " attacks finished in phase 1");
			helper.assertTrue(knight.puddleCount() == 0, "puddles in phase 1");
			p1Attacks[0] = knight.attacksFinished();
			knight.setHealth(knight.getMaxHealth() * 0.4f);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(p1Attacks[0] >= 0, "phase 1 not done yet");
			helper.assertTrue(knight.phase() == 1, "not in phase 2 yet");
			helper.assertTrue(staggered[0] > 0, "no stagger on the phase change");
			helper.assertTrue(puddles[0] > 0, "no charged puddles in phase 2");
			helper.assertTrue(knight.attacksFinished() >= p1Attacks[0] + 2, "attacks stalled in phase 2");
			helper.assertTrue(knight.isEngaged(), "fight reset unexpectedly");
		});
	}

	/** Thunder call hurts what stands in its circle, but sets nothing alight and leaves every block alone. */
	@GameTest(environment = ENV, maxTicks = 200)
	public void thunderCallDamagesWithoutFireOrBlockDamage(GameTestHelper helper) {
		StormplateKnight knight = knight(helper);
		floor(helper, Blocks.GRASS_BLOCK.defaultBlockState());
		helper.setBlock(1, 1, 4, Blocks.HAY_BLOCK);
		helper.setBlock(7, 1, 4, Blocks.OAK_PLANKS);
		Mob golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(6, 1, 4));
		knight.engage(outsider(helper));
		knight.forceTarget(golem);
		knight.setTarget(golem);
		float before = golem.getHealth();
		Attack thunder = attack(knight, "thunder");
		knight.startAttack(helper.getLevel(), thunder, golem);
		int strikeOver = thunder.stats().windupTicks + thunder.stats().activeTicks + 4;
		helper.runAfterDelay(strikeOver, () -> {
			helper.assertTrue(golem.getHealth() < before, "the strike didn't hurt (" + golem.getHealth() + "/" + before + ")");
			helper.assertTrue(!golem.isOnFire(), "the strike set the target on fire");
			for (int x = 0; x < 8; x++) {
				for (int z = 0; z < 8; z++) {
					if (!(z == 4 && (x == 1 || x == 7))) {
						// Grass under the hay and planks may decay to dirt on its own; everywhere else it must survive.
						helper.assertBlockPresent(Blocks.GRASS_BLOCK, x, 0, z);
					}
					for (int y = 1; y < 6; y++) {
						BlockState state = helper.getBlockState(new BlockPos(x, y, z));
						helper.assertTrue(!state.is(Blocks.FIRE), "fire at " + x + "," + y + "," + z);
					}
				}
			}
			helper.assertBlockPresent(Blocks.HAY_BLOCK, 1, 1, 4);
			helper.assertBlockPresent(Blocks.OAK_PLANKS, 7, 1, 4);
			helper.succeed();
		});
	}

	/** Four dragonscale pieces: lightning does nothing, knockback resistance goes up; three pieces don't count. */
	@GameTest(environment = ENV)
	public void dragonscaleSetGrantsLightningImmunity(GameTestHelper helper) {
		floor(helper, Blocks.STONE.defaultBlockState());
		ServerLevel level = helper.getLevel();
		Mob full = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		Mob partial = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 6));
		equip(full, DragonscaleArmor.HELMET, DragonscaleArmor.CHESTPLATE, DragonscaleArmor.LEGGINGS, DragonscaleArmor.BOOTS);
		equip(partial, Items.IRON_HELMET, DragonscaleArmor.CHESTPLATE, DragonscaleArmor.LEGGINGS, DragonscaleArmor.BOOTS);
		helper.assertTrue(DragonscaleArmor.wearsFullSet(full), "full set not recognised");
		helper.assertTrue(!DragonscaleArmor.wearsFullSet(partial), "three pieces counted as a set");

		DamageSource lightning = level.damageSources().lightningBolt();
		float fullBefore = full.getHealth();
		full.hurtServer(level, lightning, 10);
		helper.assertTrue(full.getHealth() == fullBefore, "full set took lightning damage");
		float partialBefore = partial.getHealth();
		partial.hurtServer(level, lightning, 10);
		helper.assertTrue(partial.getHealth() < partialBefore, "partial set should still take lightning damage");

		double kbBefore = full.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		DragonscaleArmor.applyPassives(full);
		double kbAfter = full.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		helper.assertTrue(kbAfter >= Math.min(1.0, kbBefore + StormplateKnight.config().setKnockbackResistance) - 1e-6,
				"knockback resistance " + kbBefore + " -> " + kbAfter);
		helper.succeed();
	}

	/** The kill fires BossEvents.KILLED with the Stormplate Knight's id and the participants. */
	@GameTest(environment = ENV, maxTicks = 60)
	public void killFiresKilledEvent(GameTestHelper helper) {
		StormplateKnight knight = knight(helper);
		ServerPlayer player = outsider(helper);
		knight.engage(player);
		UUID id = knight.getUUID();
		knight.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(knight.isDeadOrDying(), "not dead");
			Kill kill = KILLS.stream().filter(k -> k.boss().equals(id)).findFirst().orElse(null);
			helper.assertTrue(kill != null, "no BossEvents.KILLED");
			helper.assertTrue(kill.id().equals(CommonIds.STORMPLATE_KNIGHT), "KILLED with id " + kill.id());
			helper.assertTrue(kill.players().contains(player.getUUID()), "KILLED without the participant");
			helper.assertTrue(knight.puddleCount() == 0, "puddles left after death");
		});
	}

	/** The main table drops the whole dragonscale set and 2 Ender Eyes. */
	@GameTest(environment = ENV)
	public void stormplateDropsTheSet(GameTestHelper helper) {
		StormplateKnight knight = knight(helper);
		ServerPlayer player = outsider(helper);
		ServerLevel level = helper.getLevel();
		DamageSource source = level.damageSources().playerAttack(player);
		List<ItemStack> drops = new ArrayList<>();
		knight.dropFromLootTable(level, source, true, knight.getLootTable().orElseThrow(), drops::add);
		for (var table : knight.definition().extraLoot()) {
			knight.dropFromLootTable(level, source, true, table, drops::add);
		}
		for (Item piece : List.of(DragonscaleArmor.HELMET, DragonscaleArmor.CHESTPLATE, DragonscaleArmor.LEGGINGS, DragonscaleArmor.BOOTS)) {
			helper.assertTrue(count(drops, piece) == 1, piece + ": " + count(drops, piece));
		}
		helper.assertTrue(count(drops, Items.ENDER_EYE) == 2, "ender eyes: " + count(drops, Items.ENDER_EYE));
		helper.succeed();
	}

	private static void equip(LivingEntity entity, Item head, Item chest, Item legs, Item feet) {
		entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
		entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
		entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
		entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
	}

	private static int count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
