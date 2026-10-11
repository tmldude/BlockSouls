package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.raid.FinalDragon;
import io.github.tmldude.blocksouls.bosses.raid.FinalRaids;
import io.github.tmldude.blocksouls.bosses.raid.RaidArena;
import io.github.tmldude.blocksouls.bosses.raid.RaidCrystal;
import io.github.tmldude.blocksouls.bosses.raid.logic.CrystalOrder;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.BossEvents;

/**
 * The final raid without the End: a scripted arena in the test area (three crystal spots, two plate spots, a perch at
 * the center) and scripted health, so the phase machine and every mechanic can be checked on a headless server.
 */
public class FinalRaidGameTests {
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	private static final List<Kill> KILLS = new ArrayList<>();

	private record Kill(UUID boss, Identifier id, List<UUID> players) {
	}

	static {
		BossEvents.KILLED.register((level, bossId, boss, players) ->
				KILLS.add(new Kill(boss.getUUID(), bossId, players.stream().map(Entity::getUUID).toList())));
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A final dragon in a scripted arena: crystals at three corners, plates two blocks east and west of the center. */
	private static FinalDragon dragon(GameTestHelper helper) {
		floor(helper);
		BlockPos center = helper.absolutePos(CENTER);
		Vec3 c = Vec3.atBottomCenterOf(center);
		List<Vec3> crystals = List.of(c.add(-3, 2, -3), c.add(3, 2, -3), c.add(-3, 2, 3));
		List<BlockPos> plates = List.of(center.offset(-2, -1, 0), center.offset(2, -1, 0));
		FinalDragon dragon = helper.spawn(FinalRaids.TYPE, CENTER);
		// Tiny flight scale: every orbit (P2/P3 at most 46 × 0.08 ≈ 3.7 blocks) stays over the 9×9 test area, whose chunks
		// are the only ones guaranteed to tick.
		dragon.setArena(RaidArena.of(center, crystals, c.add(0, 3, 0), c, plates, 0.08), false);
		return dragon;
	}

	/** A (creative, so never targeted) mock player standing at the arena center: it counts as one player in the fight. */
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(CENTER));
		// teleportTo, not snapTo: the mock connection never confirms its login teleport, and vanilla re-sends a pending
		// teleport every second, snapping a plain snapTo back.
		player.teleportTo(at.x, at.y, at.z);
		return player;
	}

	private static Pig dummy(GameTestHelper helper) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(6, 1, 6));
		pig.setNoAi(true);
		pig.setPermanentlyInvulnerable(true);
		return pig;
	}

	private static RaidCrystal crystalAt(FinalDragon dragon, int slot) {
		return dragon.standingCrystals().stream().filter(c -> c.slot() == slot).findFirst().orElse(null);
	}

	private static void breakCrystal(GameTestHelper helper, FinalDragon dragon, int slot, ServerPlayer player) {
		RaidCrystal crystal = crystalAt(dragon, slot);
		helper.assertTrue(crystal != null, "no crystal standing in slot " + slot);
		ServerLevel level = helper.getLevel();
		crystal.hurtServer(level, level.damageSources().playerAttack(player), 1);
	}

	@GameTest(environment = ENV)
	public void finalDragonIsTaggedAndScaled(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		helper.assertTrue(dragon.is(CommonTags.BOSSES), "final dragon missing from #blocksouls:bosses");
		helper.assertTrue(dragon.definition().id().equals(CommonIds.FINAL_BOSS), "boss id " + dragon.definition().id());
		helper.assertTrue(dragon.getMaxHealth() >= FinalRaids.CONFIG.health - 0.01, "base health " + dragon.getMaxHealth());
		helper.assertTrue(dragon.definition().phases().size() == 3, "three phases");
		helper.succeed();
	}

	/** Breaking the next crystal advances; a wrong one heals 5%, re-forms and reshuffles; the full order drops the shield. */
	@GameTest(environment = ENV, maxTicks = 400)
	public void crystalOrderRightWrongAndDone(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		CrystalOrder order = dragon.crystalOrder();
		helper.assertTrue(order != null && order.remaining().size() == 3, "expected 3 crystals in the order, got " + order);
		helper.assertTrue(dragon.standingCrystals().size() == 3, "crystals standing: " + dragon.standingCrystals().size());

		// Right one first.
		int first = order.next();
		breakCrystal(helper, dragon, first, player);
		helper.assertTrue(order.destroyed() == 1 && !order.contains(first), "the right crystal didn't count");

		// Then a wrong one.
		dragon.setHealth(dragon.getMaxHealth() * 0.8f);
		List<Integer> before = List.copyOf(order.remaining());
		int wrong = before.get(1);
		breakCrystal(helper, dragon, wrong, player);
		float healed = dragon.getHealth() / dragon.getMaxHealth();
		helper.assertTrue(Math.abs(healed - 0.85f) < 0.002f, "wrong crystal should heal 5%: at " + healed);
		helper.assertTrue(order.remaining().size() == 2 && order.contains(wrong), "the wrong crystal must stay in the order");
		helper.assertTrue(!order.remaining().equals(before), "the order must reshuffle after a mistake");
		helper.assertTrue(order.destroyed() == 1, "a mistake isn't progress");
		helper.assertTrue(crystalAt(dragon, wrong) == null, "the wrong crystal burst");

		helper.runAfterDelay(FinalRaids.CONFIG.crystalReformTicks + 5, () -> {
			helper.assertTrue(crystalAt(dragon, wrong) != null, "the wrong crystal didn't re-form: " + dragon.debugInfo());
			while (!order.isDone()) {
				breakCrystal(helper, dragon, order.next(), player);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(order.isDone(), "order not done");
			helper.assertTrue(!dragon.shielded(), "shield still up");
			helper.assertTrue(dragon.standingCrystals().isEmpty(), "crystals left standing");
		});
	}

	/** While the shield is up, damage is mostly turned away and can't push the dragon out of phase 1. */
	@GameTest(environment = ENV, maxTicks = 100)
	public void shieldHoldsPhaseOne(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		dragon.engage(player(helper));
		ServerLevel level = helper.getLevel();
		float before = dragon.getHealth();
		dragon.hurtServer(level, level.damageSources().magic(), 10);
		float lost = before - dragon.getHealth();
		helper.assertTrue(Math.abs(lost - 10 * FinalRaids.CONFIG.shieldDamageMult) < 0.01, "shielded hit took " + lost);
		dragon.hurtServer(level, level.damageSources().magic(), 100000);
		helper.assertTrue(dragon.isAlive(), "died through the shield");
		helper.assertTrue(dragon.getHealth() / dragon.getMaxHealth() > FinalRaids.CONFIG.phase2Threshold, "pushed below phase 2's threshold");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(dragon.phase() == 0, "left phase 1 with the shield up");
			helper.succeed();
		});
	}

	/** Phase 2: nobody on a plate → it stays in the air; one player on a plate (solo needs one) → pulled down and perched. */
	@GameTest(environment = ENV, maxTicks = 500)
	public void platesGateThePerch(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		// Something to fight, so the fight never resets while we wait.
		dragon.forceTarget(dummy(helper));
		dragon.skipCrystals();
		dragon.forcePhase(1);
		helper.assertTrue(dragon.plateCenters().size() == 2, "solo gets two plates, got " + dragon.plateCenters().size());
		BlockPos plate = dragon.plateCenters().getFirst();
		helper.assertTrue(helper.getLevel().getBlockState(plate).is(Blocks.CRYING_OBSIDIAN), "plate not set into the floor");

		helper.runAfterDelay(160, () -> {
			helper.assertTrue(dragon.mode() == FinalDragon.Mode.FLYING, "came down with nobody on a plate: " + dragon.mode());
			Vec3 on = Vec3.atBottomCenterOf(plate.above());
			player.teleportTo(on.x, on.y, on.z);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dragon.mode() == FinalDragon.Mode.PERCHED, "not perched yet: " + dragon.debugInfo() + "; player at "
					+ player.position() + " in fight " + dragon.inFight(player) + ", plate " + plate.toShortString());
			Vec3 perch = dragon.arena().perch();
			helper.assertTrue(dragon.position().distanceTo(perch) < 0.5, "perched away from the perch");
		});
	}

	/** A wipe in phase 2 heals to phase 2's start (not full), keeps the phase, and takes the plates out until re-engage. */
	@GameTest(environment = ENV, maxTicks = 100)
	public void wipeResetsToThePhaseStart(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		dragon.skipCrystals();
		dragon.forcePhase(1);
		BlockPos plate = dragon.plateCenters().getFirst();
		dragon.setHealth(dragon.getMaxHealth() * 0.45f);

		dragon.resetFight();
		helper.assertTrue(!dragon.isEngaged(), "still engaged after the wipe");
		helper.assertTrue(dragon.phase() == 1, "wipe should keep phase 2, got phase " + (dragon.phase() + 1));
		float fraction = dragon.getHealth() / dragon.getMaxHealth();
		helper.assertTrue(Math.abs(fraction - FinalRaids.CONFIG.phase2Threshold) < 0.002, "healed to " + fraction);
		helper.assertTrue(helper.getLevel().getBlockState(plate).is(Blocks.STONE), "plate not removed");

		// Back in: the phase's mechanic comes back.
		dragon.engage(player);
		helper.assertTrue(dragon.plateCenters().size() == 2, "plates not back after re-engaging");
		helper.succeed();
	}

	/** A wipe in phase 1 resets the Crystal Order: all crystals back, no progress, full health. */
	@GameTest(environment = ENV, maxTicks = 100)
	public void wipeResetsTheCrystals(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		breakCrystal(helper, dragon, dragon.crystalOrder().next(), player);
		dragon.setHealth(dragon.getMaxHealth() * 0.9f);

		dragon.resetFight();
		helper.assertTrue(dragon.phase() == 0, "phase changed");
		helper.assertTrue(dragon.getHealth() >= dragon.getMaxHealth() - 0.01f, "not healed to full");
		helper.assertTrue(dragon.standingCrystals().isEmpty(), "crystals left after the wipe");
		dragon.engage(player);
		CrystalOrder order = dragon.crystalOrder();
		helper.assertTrue(order != null && order.remaining().size() == 3 && order.destroyed() == 0, "order not reset: " + order);
		helper.assertTrue(dragon.standingCrystals().size() == 3, "crystals not back");
		helper.succeed();
	}

	/** Scripted health walks P1 → P2 → P3; attacks keep finishing, including on the perch (no softlock). */
	@GameTest(environment = ENV, maxTicks = 1600)
	public void phaseMachineWalksAllPhases(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		Pig pig = dummy(helper);
		dragon.engage(player(helper));
		dragon.forceTarget(pig);
		dragon.skipCrystals();
		int[] mark = {0, -1, -1};
		helper.runAfterDelay(10, () -> dragon.setHealth(dragon.getMaxHealth() * 0.6f));
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(dragon.phase() == 1, "not in phase 2 at 60%: " + dragon.debugInfo());
			helper.assertTrue(dragon.attacksFinished() >= 2, "only " + dragon.attacksFinished() + " attacks in phase 2 (flying): " + dragon.debugInfo());
			mark[1] = dragon.attacksFinished();
			dragon.forcePerch();
		});
		helper.runAfterDelay(700, () -> {
			helper.assertTrue(dragon.attacksFinished() > mark[1], "no attack finished while perched: " + dragon.debugInfo());
			dragon.setHealth(dragon.getMaxHealth() * 0.2f);
		});
		helper.runAfterDelay(1000, () -> mark[2] = dragon.attacksFinished());
		helper.succeedWhen(() -> {
			helper.assertTrue(mark[2] >= 0, "not done yet");
			helper.assertTrue(dragon.phase() == 2, "not in phase 3 at 20%");
			helper.assertTrue(dragon.attacksFinished() >= mark[2] + 2, "attacks stalled in phase 3: " + dragon.debugInfo());
			helper.assertTrue(dragon.isEngaged(), "fight reset unexpectedly");
		});
	}

	/** Death fires BossEvents.KILLED with the final boss id and the participant (the game module's endless switch). */
	@GameTest(environment = ENV, maxTicks = 200)
	public void deathFiresKilledWithFinalBoss(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerPlayer player = player(helper);
		dragon.engage(player);
		UUID id = dragon.getUUID();
		dragon.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(dragon.isDeadOrDying(), "not dead");
			Kill kill = KILLS.stream().filter(k -> k.boss().equals(id)).findFirst().orElse(null);
			helper.assertTrue(kill != null, "no BossEvents.KILLED");
			helper.assertTrue(kill.id().equals(CommonIds.FINAL_BOSS), "KILLED with id " + kill.id());
			helper.assertTrue(kill.players().contains(player.getUUID()), "KILLED without the participant");
			helper.assertTrue(dragon.standingCrystals().isEmpty(), "crystals left after death");
		});
	}

	/** The main table drops the trophy; the guns table only loads with the guns module and never breaks loading. */
	@GameTest(environment = ENV)
	public void dropsTheTrophy(GameTestHelper helper) {
		FinalDragon dragon = dragon(helper);
		ServerLevel level = helper.getLevel();
		List<ItemStack> drops = new ArrayList<>();
		var source = level.damageSources().playerAttack(helper.makeMockServerPlayerInLevel());
		dragon.dropFromLootTable(level, source, true, dragon.getLootTable().orElseThrow(), drops::add);
		for (var table : dragon.definition().extraLoot()) {
			dragon.dropFromLootTable(level, source, true, table, drops::add);
		}
		helper.assertTrue(drops.stream().anyMatch(s -> s.is(FinalRaids.TROPHY)), "no trophy in " + drops);
		helper.succeed();
	}
}
