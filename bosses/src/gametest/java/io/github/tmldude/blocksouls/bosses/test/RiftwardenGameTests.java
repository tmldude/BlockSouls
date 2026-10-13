package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.riftwarden.Riftwarden;
import io.github.tmldude.blocksouls.bosses.riftwarden.Riftwardens;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.BossEvents;

/** The Riftwarden's damage rush: landing the fraction kills it (the kill event), a timeout mends it back into phase 2. */
public class RiftwardenGameTests {
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	private static final List<UUID> KILLED = new ArrayList<>();

	static {
		BossEvents.KILLED.register((level, bossId, boss, players) -> {
			if (bossId.equals(CommonIds.RIFTWARDEN)) {
				KILLED.add(boss.getUUID());
			}
		});
	}

	/** A Riftwarden in phase 3 with a fight on (an outside participant, a pig to chase). */
	private static Riftwarden rushing(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		Riftwarden boss = helper.spawn(Riftwardens.TYPE, CENTER);
		boss.setHome(helper.absolutePos(CENTER));
		boss.setArenaRadius(3);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(CENTER).offset(200, 0, 200);
		player.snapTo(far.getX(), far.getY(), far.getZ());
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(6, 1, 4));
		pig.setNoAi(true);
		pig.setPermanentlyInvulnerable(true);
		boss.engage(player);
		boss.forceTarget(pig);
		boss.forcePhase(2);
		helper.assertTrue(boss.rushing(), "phase 3, was " + boss.phase());
		helper.assertTrue(boss.rushSecondsLeft() > 0 && boss.rushNumber() == 1, "the first rush started");
		return boss;
	}

	@GameTest(environment = ENV, maxTicks = 600)
	public void landingTheRushFractionKillsTheRiftwarden(GameTestHelper helper) {
		Riftwarden boss = rushing(helper);
		ServerLevel level = helper.getLevel();
		float startHealth = boss.getHealth();
		helper.onEachTick(() -> {
			if (boss.isAlive() && !boss.isStaggered()) {
				boss.hurtServer(level, level.damageSources().magic(), 60);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(KILLED.contains(boss.getUUID()), "not killed yet, rush " + boss.rushProgress());
			// The kill came from the rush, not from emptying the bar.
			helper.assertTrue(boss.getMaxHealth() * Riftwardens.CONFIG.rushFraction < startHealth, "health ran out first");
		});
	}

	@GameTest(environment = ENV, maxTicks = 400)
	public void aTimedOutRushMendsItBackIntoPhase2(GameTestHelper helper) {
		Riftwarden boss = rushing(helper);
		boss.setHealth(boss.getMaxHealth() * 0.2f);
		boss.shortenRush(5);
		helper.succeedWhen(() -> {
			helper.assertTrue(boss.phase() == 1, "back in phase 2, was " + boss.phase());
			helper.assertTrue(boss.getHealth() >= boss.getMaxHealth() * (Riftwardens.CONFIG.rushFailHealShare - 0.01),
					"mended to " + boss.getHealth() / boss.getMaxHealth());
			helper.assertTrue(boss.isAlive() && !boss.isUnbound(), "alive, still bound");
		});
	}
}
