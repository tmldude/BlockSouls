package io.github.tmldude.blocksouls.common.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.player.Capabilities;
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

	@GameTest
	public void capabilitiesDefaultToTheV1VerbsOnly(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(Capabilities.allGranted(player), "no set attached by default");
		helper.assertTrue(Capabilities.has(player, Capabilities.CLIMB) && Capabilities.has(player, Capabilities.SWING)
				&& Capabilities.has(player, Capabilities.MULTI_JUMP), "every v1 verb by default");
		helper.assertTrue(!Capabilities.has(player, Capabilities.GLIDE), "glide is opt-in: never granted by default");
		Capabilities.grant(player, Capabilities.GLIDE);
		helper.assertTrue(Capabilities.allGranted(player) && !Capabilities.has(player, Capabilities.GLIDE), "grant is a no-op on the default set");

		Capabilities.restrictTo(player, Set.of(Capabilities.GLIDE));
		helper.assertTrue(Capabilities.has(player, Capabilities.GLIDE) && !Capabilities.has(player, Capabilities.CLIMB), "an explicit set is exact");
		Capabilities.grant(player, Capabilities.CLIMB);
		helper.assertTrue(Capabilities.has(player, Capabilities.CLIMB), "cross-training grants onto an explicit set");
		Capabilities.grantAll(player);
		helper.assertTrue(Capabilities.allGranted(player) && !Capabilities.has(player, Capabilities.GLIDE), "back to v1");
		helper.succeed();
	}

	@GameTest
	public void downedShootingGatesCanShoot(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean before = PlayerStatus.downedShooting();
		try {
			PlayerStatus.setDownedShooting(false);
			helper.assertTrue(PlayerStatus.canShoot(player), "standing players shoot");
			PlayerStatus.setDowned(player, true);
			helper.assertTrue(!PlayerStatus.canShoot(player), "v1 rule: no shooting while downed");
			PlayerStatus.setDownedShooting(true);
			helper.assertTrue(PlayerStatus.canShoot(player), "v2 downed gameplay: the pistol keeps firing");
		} finally {
			PlayerStatus.setDownedShooting(before);
			PlayerStatus.setDowned(player, false);
		}
		helper.succeed();
	}
}
