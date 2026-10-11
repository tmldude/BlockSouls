package io.github.tmldude.blocksouls.common.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.common.run.Runs;

public class CommonGameTests {
	@GameTest
	public void traitsSetAndClear(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP) == 0, "no traits by default");

		PlayerTraits.set(player, PlayerTraits.DOUBLE_JUMP, 2);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP) == 2, "trait level stored");

		PlayerTraits.set(player, PlayerTraits.DOUBLE_JUMP, 0);
		helper.assertTrue(!PlayerTraits.has(player, PlayerTraits.DOUBLE_JUMP), "level 0 removes the trait");
		helper.succeed();
	}

	@GameTest
	public void downedToggles(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!PlayerStatus.isDowned(player), "not downed by default");
		PlayerStatus.setDowned(player, true);
		helper.assertTrue(PlayerStatus.isDowned(player), "downed after set");
		PlayerStatus.setDowned(player, false);
		helper.assertTrue(!PlayerStatus.isDowned(player), "up again");
		helper.succeed();
	}

	@GameTest
	public void runStateDefaultsToNotStarted(GameTestHelper helper) {
		// Only common is loaded in this test run, so no game module installs a provider.
		helper.assertTrue(Runs.get(helper.getLevel().getServer()) == RunState.NOT_STARTED, "no run without the game module");
		helper.succeed();
	}
}
