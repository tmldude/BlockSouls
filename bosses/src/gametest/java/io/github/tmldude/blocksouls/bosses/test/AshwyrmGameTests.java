package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.BossPart;
import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.api.Stage;
import io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrm;
import io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrms;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.BossEvents;

public class AshwyrmGameTests {
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	/** KILLED events by boss uuid → boss id. */
	private static final Map<UUID, Identifier> KILLS = new HashMap<>();

	static {
		BossEvents.KILLED.register((level, bossId, boss, players) -> KILLS.put(boss.getUUID(), bossId));
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static Ashwyrm wyrm(GameTestHelper helper) {
		floor(helper);
		Ashwyrm wyrm = helper.spawn(Ashwyrms.TYPE, CENTER);
		wyrm.setHome(helper.absolutePos(CENTER));
		wyrm.setArenaRadius(3);
		return wyrm;
	}

	private static ServerPlayer outsider(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(CENTER).offset(200, 0, 200);
		player.snapTo(far.getX(), far.getY(), far.getZ());
		return player;
	}

	private static Pig dummy(GameTestHelper helper, int x, int z) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(x, 1, z));
		pig.setNoAi(true);
		pig.setPermanentlyInvulnerable(true);
		return pig;
	}

	private static Attack attack(Ashwyrm wyrm, String name) {
		return wyrm.definition().attacks().stream().filter(a -> a.name().equals(name)).findFirst().orElseThrow();
	}

	@GameTest(environment = ENV)
	public void ashwyrmIsTaggedAndRegistered(GameTestHelper helper) {
		Ashwyrm wyrm = wyrm(helper);
		helper.assertTrue(wyrm.is(CommonTags.BOSSES), "Ashwyrm missing from #blocksouls:bosses");
		helper.assertTrue(Bosses.type(CommonIds.ASHWYRM).isPresent(), "not in the boss registry");
		helper.assertTrue(Math.abs(wyrm.getMaxHealth() - Ashwyrm.config().health) < 0.01, "base health " + wyrm.getMaxHealth());
		helper.assertTrue(wyrm.fireImmune(), "Ashwyrm should be fire-immune");
		helper.assertTrue(wyrm.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "Ashwyrm should be knockback-immune");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(CommonIds.ASHWYRM_SCALE), "no ashwyrm_scale item");
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(CommonIds.EMBER_BARREL), "no ember_barrel item");
		helper.succeed();
	}

	/**
	 * Scripted health walks P1 → P2 → P3: attacks finish on the ground, in P2 it flies and lands for its stunned window at
	 * least once, and in P3 it is back on the ground and still attacking.
	 */
	@GameTest(environment = ENV, maxTicks = 2400)
	public void phaseMachineWalksAllThreePhases(GameTestHelper helper) {
		Ashwyrm wyrm = wyrm(helper);
		Pig pig = dummy(helper, 6, 4);
		wyrm.engage(outsider(helper));
		wyrm.forceTarget(pig);
		wyrm.setHealth(wyrm.getMaxHealth() * 0.8f);
		int[] step = {0};
		int[] mark = {0};
		boolean[] flew = {false};
		boolean[] wasVulnerable = {false};
		helper.onEachTick(() -> {
			if (step[0] == 1 && wyrm.isFlying()) {
				flew[0] = true;
			}
			if (step[0] == 1 && wyrm.isVulnerable()) {
				wasVulnerable[0] = true;
			}
		});
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(wyrm.phase() == Ashwyrm.GROUND, "still phase 1 at 80%");
			helper.assertTrue(wyrm.attacksFinished() >= 2, "only " + wyrm.attacksFinished() + " attacks finished in phase 1");
			mark[0] = wyrm.attacksFinished();
			step[0] = 1;
			wyrm.setHealth(wyrm.getMaxHealth() * 0.5f);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(wyrm.isEngaged(), "fight reset unexpectedly");
			if (step[0] == 1) {
				helper.assertTrue(wyrm.phase() == Ashwyrm.AIR, "not in phase 2 yet");
				helper.assertTrue(flew[0], "never took off in phase 2");
				helper.assertTrue(wyrm.landings() >= 1, "hasn't landed in phase 2 yet");
				helper.assertTrue(wasVulnerable[0], "no vulnerability window after landing");
				helper.assertTrue(wyrm.attacksFinished() >= mark[0] + 3, "attacks stalled in phase 2");
				mark[0] = wyrm.attacksFinished();
				step[0] = 2;
				wyrm.setHealth(wyrm.getMaxHealth() * 0.2f);
			}
			helper.assertTrue(step[0] == 2, "phase 2 not done");
			helper.assertTrue(wyrm.phase() == Ashwyrm.ENRAGED, "not in phase 3 yet");
			helper.assertTrue(!wyrm.isFlying(), "still flying in phase 3");
			helper.assertTrue(wyrm.attacksFinished() >= mark[0] + 2, "attacks stalled in phase 3");
		});
	}

	/** Head hits go through the part and deal 2×. */
	@GameTest(environment = ENV)
	public void headWeakPointTakesDoubleDamage(GameTestHelper helper) {
		Ashwyrm wyrm = wyrm(helper);
		ServerLevel level = helper.getLevel();
		wyrm.aiStep();
		BossPart head = wyrm.parts()[0];
		helper.assertTrue(head.partName().equals("head"), "first part is " + head.partName());
		helper.assertTrue(level.getEntityOrPart(head.getId()) == head, "ServerLevel.getEntityOrPart doesn't find the head");
		float before = wyrm.getHealth();
		head.hurtServer(level, level.damageSources().magic(), 10);
		float lost = before - wyrm.getHealth();
		helper.assertTrue(Math.abs(lost - 20) < 0.01, "head hit took " + lost);
		helper.succeed();
	}

	/** Lying stunned after a landing it takes 2×. */
	@GameTest(environment = ENV, maxTicks = 200)
	public void stunnedAfterLandingTakesDoubleDamage(GameTestHelper helper) {
		Ashwyrm wyrm = wyrm(helper);
		Pig pig = dummy(helper, 6, 4);
		wyrm.engage(outsider(helper));
		wyrm.forceTarget(pig);
		wyrm.startAttack(helper.getLevel(), attack(wyrm, "land"), pig);
		boolean[] done = {false};
		helper.succeedWhen(() -> {
			if (!done[0]) {
				helper.assertTrue(wyrm.attackRun() != null && wyrm.attackRun().stage() == Stage.RECOVERY, "not stunned yet");
				helper.assertTrue(wyrm.isVulnerable(), "recovery after landing isn't the vulnerability window");
				ServerLevel level = helper.getLevel();
				float before = wyrm.getHealth();
				wyrm.hurtServer(level, level.damageSources().magic(), 10);
				float lost = before - wyrm.getHealth();
				helper.assertTrue(Math.abs(lost - 20) < 0.01, "stunned hit took " + lost);
				done[0] = true;
			}
		});
	}

	/** A full fireball run over flammable blocks: no block changes (no fire, no craters) with griefing off. */
	@GameTest(environment = ENV, maxTicks = 300)
	public void fireballsBreakNoBlocks(GameTestHelper helper) {
		Ashwyrm wyrm = wyrm(helper);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, (x + z) % 2 == 0 ? Blocks.OAK_PLANKS : Blocks.GRASS_BLOCK);
			}
		}
		helper.setBlock(1, 1, 6, Blocks.BOOKSHELF);
		helper.setBlock(6, 1, 1, Blocks.HAY_BLOCK);
		Pig pig = dummy(helper, 6, 4);
		// Engage first: the fog wall it raises is the framework's, not a fireball's doing.
		wyrm.engage(outsider(helper));
		wyrm.forceTarget(pig);
		Map<BlockPos, BlockState> before = new HashMap<>();
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 4; y++) {
				for (int z = 0; z < 8; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					before.put(pos, helper.getBlockState(pos));
				}
			}
		}
		wyrm.startAttack(helper.getLevel(), attack(wyrm, "fireball_run"), pig);
		int start = wyrm.attacksFinished();
		helper.succeedWhen(() -> {
			helper.assertTrue(wyrm.attacksFinished() > start, "fireball run hasn't finished");
			List<String> changed = new ArrayList<>();
			before.forEach((pos, state) -> {
				if (helper.getBlockState(pos) != state) {
					changed.add(pos + ": " + state + " -> " + helper.getBlockState(pos));
				}
			});
			helper.assertTrue(changed.isEmpty(), "blocks changed: " + changed);
		});
	}

	/** Dying fires BossEvents.KILLED with the Ashwyrm's id. */
	@GameTest(environment = ENV, maxTicks = 60)
	public void killFiresEventWithAshwyrmId(GameTestHelper helper) {
		Ashwyrm wyrm = wyrm(helper);
		wyrm.engage(outsider(helper));
		UUID id = wyrm.getUUID();
		wyrm.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(wyrm.isDeadOrDying(), "not dead");
			helper.assertTrue(CommonIds.ASHWYRM.equals(KILLS.get(id)), "KILLED id: " + KILLS.get(id));
		});
	}

	/** 2 Ender Eyes, 4–6 scales and an ember barrel. */
	@GameTest(environment = ENV)
	public void ashwyrmDropsLoot(GameTestHelper helper) {
		Ashwyrm wyrm = wyrm(helper);
		ServerLevel level = helper.getLevel();
		DamageSource source = level.damageSources().playerAttack(outsider(helper));
		List<ItemStack> drops = new ArrayList<>();
		wyrm.dropFromLootTable(level, source, true, wyrm.getLootTable().orElseThrow(), drops::add);
		for (var table : wyrm.definition().extraLoot()) {
			wyrm.dropFromLootTable(level, source, true, table, drops::add);
		}
		Item scale = BuiltInRegistries.ITEM.getValue(CommonIds.ASHWYRM_SCALE);
		Item barrel = BuiltInRegistries.ITEM.getValue(CommonIds.EMBER_BARREL);
		helper.assertTrue(count(drops, Items.ENDER_EYE) == 2, "ender eyes: " + count(drops, Items.ENDER_EYE));
		int scales = count(drops, scale);
		helper.assertTrue(scales >= 4 && scales <= 6, "scales: " + scales);
		helper.assertTrue(count(drops, barrel) == 1, "ember barrels: " + count(drops, barrel));
		helper.succeed();
	}

	private static int count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
