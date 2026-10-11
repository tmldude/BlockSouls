package io.github.tmldude.blocksouls.game.test;

import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.downed.Revive;

public class DownedGameTests {
	@GameTest
	public void lethalDamageDownsInsteadOfKilling(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);

		helper.assertTrue(PlayerStatus.isDowned(player), "player should be downed");
		helper.assertFalse(player.isDeadOrDying(), "player should not be dead");
		helper.assertTrue(player.getHealth() == 1.0F, "downed health should be 1, was " + player.getHealth());
		helper.succeed();
	}

	@GameTest
	public void voidDamageStillKills(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Downed.goDown(player);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fellOutOfWorld(), 1000.0F);

		helper.assertTrue(player.isDeadOrDying(), "void damage should be a real death");
		helper.assertFalse(PlayerStatus.isDowned(player), "a real death clears the downed flag");
		helper.succeed();
	}

	@GameTest
	public void downedPlayersTakeNoDamage(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Downed.goDown(player);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 5.0F);
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000.0F);

		helper.assertTrue(PlayerStatus.isDowned(player), "still downed");
		helper.assertTrue(player.getHealth() == 1.0F, "no damage while downed, health " + player.getHealth());
		helper.assertFalse(player.isDeadOrDying(), "not dead");
		helper.succeed();
	}

	@GameTest(maxTicks = 200)
	public void holdingUseRevivesAfterThreeSeconds(GameTestHelper helper) {
		ServerPlayer downed = DownedTestPlayers.make(helper, 1, 1);
		ServerPlayer reviver = DownedTestPlayers.make(helper, 2, 1);
		Downed.goDown(downed);
		helper.assertTrue(Revive.start(reviver, downed), "reviver in range with line of sight can start");

		int ticks = PlayerSystem.CONFIG.downed.reviveTicks;
		helper.runAfterDelay(ticks - 10, () -> helper.assertTrue(PlayerStatus.isDowned(downed), "not revived before the hold time"));
		helper.runAfterDelay(ticks + 5, () -> {
			helper.assertFalse(PlayerStatus.isDowned(downed), "revived after the hold time");
			helper.assertTrue(downed.getHealth() == Math.min(PlayerSystem.CONFIG.downed.reviveHealth, downed.getMaxHealth()),
					"revive health, was " + downed.getHealth());
			helper.succeed();
		});
	}

	@GameTest
	public void reviveNeedsRange(GameTestHelper helper) {
		ServerPlayer downed = DownedTestPlayers.make(helper, 1, 1);
		ServerPlayer reviver = DownedTestPlayers.make(helper, 6, 6);
		Downed.goDown(downed);
		helper.assertFalse(Revive.start(reviver, downed), "too far to revive");
		helper.succeed();
	}

	@GameTest
	public void everyoneDownIsATeamWipe(GameTestHelper helper) {
		ServerPlayer a = DownedTestPlayers.make(helper, 1, 1);
		ServerPlayer b = DownedTestPlayers.make(helper, 2, 2);

		Downed.goDown(a);
		helper.assertFalse(Downed.isTeamWiped(List.of(a, b)), "one standing player means no wipe");
		Downed.goDown(b);
		helper.assertTrue(Downed.isTeamWiped(List.of(a, b)), "everyone down is a wipe");
		helper.assertTrue(Downed.isTeamWiped(List.of(a)), "solo: going down is a wipe");
		Downed.revive(b, null);
		helper.assertFalse(Downed.isTeamWiped(List.of(a, b)), "revived player breaks the wipe");
		helper.succeed();
	}
}
