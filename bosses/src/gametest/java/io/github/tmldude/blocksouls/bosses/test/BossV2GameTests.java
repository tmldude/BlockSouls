package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.archetype.Archetype;
import io.github.tmldude.blocksouls.bosses.archetype.Feint;
import io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrms;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnights;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRiders;
import io.github.tmldude.blocksouls.bosses.presentation.BossSplash;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateKnight;
import io.github.tmldude.blocksouls.bosses.stormplate.StormplateKnights;

/** Bosses v2 (PLAN2 §3h): posture and the visceral window, part breaks, archetype telegraphs and the name splash. */
public class BossV2GameTests {
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	private record Run(int telegraph, int firstHit) {
	}

	private static <T extends BossEntity> T boss(GameTestHelper helper, EntityType<T> type) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		T boss = helper.spawn(type, CENTER);
		boss.setHome(helper.absolutePos(CENTER));
		boss.setArenaRadius(3);
		return boss;
	}

	private static ServerPlayer outsider(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(CENTER).offset(200, 0, 200);
		player.snapTo(far.getX(), far.getY(), far.getZ());
		return player;
	}

	/** An iron golem that can't wander and is healed every tick: hits land on it, but it never dies. */
	private static Mob punchingBag(GameTestHelper helper) {
		Mob golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(6, 1, 4));
		helper.onEachTick(() -> golem.setHealth(golem.getMaxHealth()));
		return golem;
	}

	// ---------------------------------------------------------------------------------------------------------
	// Posture

	/** Poise fills, the visceral window opens (no attack, double damage), then closes and the fight goes on. */
	@GameTest(environment = ENV, maxTicks = 400)
	public void postureFillsAndOpensAVisceralWindow(GameTestHelper helper) {
		BossEntity knight = boss(helper, FurnaceKnights.TYPE);
		ServerLevel level = helper.getLevel();
		knight.engage(outsider(helper));
		knight.forceTarget(punchingBag(helper));
		float[] normalHit = {0};
		boolean[] visceralChecked = {false};
		helper.runAfterDelay(2, () -> {
			float before = knight.getHealth();
			knight.hurtServer(level, level.damageSources().generic(), 10);
			normalHit[0] = before - knight.getHealth();
			helper.assertTrue(normalHit[0] > 0, "the boss took no damage before the break");
			double max = knight.maxPoise();
			knight.addPoise(max * 0.5);
			helper.assertTrue(knight.posture() > 0.4f && knight.posture() < 0.6f, "posture at half: " + knight.posture());
			helper.assertTrue(!knight.isPostureBroken(), "broke at half poise");
			knight.addPoise(max * 0.6);
			helper.assertTrue(knight.isPostureBroken(), "full poise didn't open the visceral window");
			helper.assertTrue(knight.posturesBroken() == 1, "posture breaks: " + knight.posturesBroken());
			helper.assertTrue(knight.currentAttack() == null, "still attacking in the visceral window");
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(knight.isPostureBroken(), "visceral window closed too early");
			helper.assertTrue(knight.currentAttack() == null, "attacking in the visceral window");
			float before = knight.getHealth();
			knight.hurtServer(level, level.damageSources().generic(), 10);
			float visceralHit = before - knight.getHealth();
			helper.assertTrue(visceralHit > normalHit[0] * 1.5f, "visceral hit " + visceralHit + " vs normal " + normalHit[0]);
			visceralChecked[0] = true;
		});
		int closes = BossEntity.framework().posture.visceralTicks + 10;
		int finished = knight.attacksFinished();
		helper.runAfterDelay(closes, () -> helper.assertTrue(!knight.isPostureBroken(), "the visceral window never closed"));
		helper.succeedWhen(() -> {
			helper.assertTrue(visceralChecked[0], "visceral window not checked yet");
			helper.assertTrue(!knight.isPostureBroken(), "still in the visceral window");
			helper.assertTrue(knight.posture() == 0, "poise not reset after the window");
			helper.assertTrue(knight.attacksFinished() > finished, "the boss never attacked again");
			helper.assertTrue(knight.isEngaged(), "the fight reset");
		});
	}

	// ---------------------------------------------------------------------------------------------------------
	// Part breaks

	/** Breaking the Stormplate pauldron takes the spear throw away (and its feint into it) and speeds the knight up. */
	@GameTest(environment = ENV, maxTicks = 700)
	public void brokenPauldronLosesTheThrow(GameTestHelper helper) {
		StormplateKnight knight = boss(helper, StormplateKnights.TYPE);
		ServerLevel level = helper.getLevel();
		knight.engage(outsider(helper));
		knight.forceTarget(punchingBag(helper));
		knight.setHealth(knight.getMaxHealth() * 0.4f);
		Attack spearThrow = knight.definition().attacks().stream().filter(a -> a.name().equals("throw")).findFirst().orElseThrow();
		helper.assertTrue(!knight.attackLost(spearThrow), "throw lost before the break");
		double speed = knight.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double threshold = knight.definition().breaks().get(knight.definition().breakIndex("pauldron")).shareOfMaxHealth() * knight.getMaxHealth();
		knight.damagePart(level, "pauldron", threshold * 0.5);
		helper.assertTrue(!knight.isPartBroken("pauldron"), "broke at half its threshold");
		knight.damagePart(level, "pauldron", threshold * 0.6);
		helper.assertTrue(knight.isPartBroken("pauldron"), "pauldron didn't break at its threshold");
		helper.assertTrue(knight.attackLost(spearThrow), "throw still available after the break");
		helper.assertTrue(knight.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed, "no speed bonus after the break");
		Set<String> used = new LinkedHashSet<>();
		int finished = knight.attacksFinished();
		helper.onEachTick(() -> {
			Attack current = knight.currentAttack();
			if (current != null) {
				used.add(current.name());
			}
		});
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(knight.attacksFinished() >= finished + 3, "attacks stalled after the break: " + used);
			helper.assertTrue(!used.contains("throw"), "threw the spear with a broken pauldron: " + used);
			helper.assertTrue(!used.contains("feint"), "feinted into the lost throw: " + used);
			helper.assertTrue(knight.isPartBroken("pauldron"), "pauldron repaired mid-fight");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------------------------------------------------
	// Archetypes: the telegraph always comes before the hit

	@GameTest(environment = ENV, maxTicks = 1600)
	public void furnaceKnightArchetypesTelegraphFirst(GameTestHelper helper) {
		telegraphsFirst(helper, boss(helper, FurnaceKnights.TYPE));
	}

	@GameTest(environment = ENV, maxTicks = 1600)
	public void stormplateArchetypesTelegraphFirst(GameTestHelper helper) {
		telegraphsFirst(helper, boss(helper, StormplateKnights.TYPE));
	}

	@GameTest(environment = ENV, maxTicks = 1600)
	public void paleRiderArchetypesTelegraphFirst(GameTestHelper helper) {
		telegraphsFirst(helper, boss(helper, PaleRiders.TYPE));
	}

	@GameTest(environment = ENV, maxTicks = 1600)
	public void ashwyrmArchetypesTelegraphFirst(GameTestHelper helper) {
		telegraphsFirst(helper, boss(helper, Ashwyrms.TYPE));
	}

	/**
	 * Forces each archetype attack (and feint) the boss has, one after another, and checks every run telegraphed during
	 * its windup, before any hit landed.
	 */
	private static void telegraphsFirst(GameTestHelper helper, BossEntity boss) {
		ServerLevel level = helper.getLevel();
		Mob target = punchingBag(helper);
		boss.engage(outsider(helper));
		boss.forceTarget(target);
		Map<String, Attack> archetypes = new LinkedHashMap<>();
		for (Attack attack : boss.definition().attacks()) {
			if (attack instanceof Archetype || attack instanceof Feint) {
				archetypes.putIfAbsent(attack.name(), attack);
			}
		}
		helper.assertTrue(archetypes.size() >= 2, "only " + archetypes.keySet() + " archetypes");
		List<Attack> order = new ArrayList<>(archetypes.values());
		Map<String, Run> runs = new LinkedHashMap<>();
		int[] state = {0, boss.attacksFinished()};
		helper.onEachTick(() -> {
			if (boss.attacksFinished() != state[1]) {
				state[1] = boss.attacksFinished();
				String name = boss.lastRunAttack();
				if (name != null) {
					runs.putIfAbsent(name, new Run(boss.lastTelegraphTick(), boss.lastFirstHitTick()));
				}
			}
			while (state[0] < order.size() && runs.containsKey(order.get(state[0]).name())) {
				state[0]++;
			}
			if (state[0] < order.size()) {
				Attack next = order.get(state[0]);
				Attack current = boss.currentAttack();
				if (current == null || current != next && !(next instanceof Feint feint && current == feint.follow())) {
					boss.cancelAttack();
					boss.startAttack(level, next, target);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(state[0] >= order.size(), "ran " + runs.keySet() + " of " + archetypes.keySet());
			for (Attack attack : order) {
				Run run = runs.get(attack.name());
				helper.assertTrue(run.telegraph() >= 0, attack.name() + " never telegraphed");
				helper.assertTrue(run.telegraph() < Math.max(1, attack.stats().windupTicks),
						attack.name() + " telegraphed at tick " + run.telegraph() + ", after its windup");
				helper.assertTrue(run.firstHit() < 0 || run.telegraph() < run.firstHit(),
						attack.name() + " hit at tick " + run.firstHit() + " before its telegraph at " + run.telegraph());
			}
		});
	}

	// ---------------------------------------------------------------------------------------------------------
	// Name splash

	/** The name splash fires once when the fight starts; late joiners and re-engages mid-fight don't replay it. */
	@GameTest(environment = ENV, maxTicks = 40)
	public void splashFiresOncePerFight(GameTestHelper helper) {
		BossEntity knight = boss(helper, FurnaceKnights.TYPE);
		helper.assertTrue(BossSplash.sent(knight) == 0, "splash before the fight");
		knight.engage(outsider(helper));
		helper.assertTrue(BossSplash.sent(knight) == 1, "splashes on engage: " + BossSplash.sent(knight));
		knight.engage(outsider(helper));
		knight.engage(outsider(helper));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(knight.isEngaged(), "fight ended");
			helper.assertTrue(BossSplash.sent(knight) == 1, "splash replayed mid-fight: " + BossSplash.sent(knight));
			helper.succeed();
		});
	}
}
