package io.github.tmldude.blocksouls.game.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.downed.DownedPlay;
import io.github.tmldude.blocksouls.game.player.downed.LastStand;
import io.github.tmldude.blocksouls.game.player.party.PartyVote;
import io.github.tmldude.blocksouls.game.player.party.PartyVotes;

/** Solo Last Stand, downed gameplay (the lent pistol, scaled damage) and the party-vote hook. */
public class LastStandGameTests {
	@GameTest(maxTicks = 200)
	public void threeKillsWhileDownSelfRevive(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		LastStand.resetForTests(server);
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Downed.goDown(player);
		helper.assertTrue(LastStand.tryStart(player, 1), "a solo down starts a Last Stand");
		helper.assertTrue(LastStand.isActive(player), "active");
		helper.assertTrue(!LastStand.tryStart(player, 1), "only one at a time");

		for (int i = 0; i < PlayerSystem.CONFIG.downed.lastStandKills; i++) {
			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(player), 1000.0F);
		}
		// The self-revive happens inside the killing blow, so check right away: the siege flag is shared state that
		// other tests in the batch (run starts, resets) may clear a few ticks later.
		helper.assertTrue(!PlayerStatus.isDowned(player), "three kills while down: back on your feet");
		helper.assertTrue(!LastStand.isActive(), "the stand is over");
		helper.assertTrue(LastStand.usedThisSiege(), "spent for this siege");
		Downed.goDown(player);
		helper.assertTrue(!LastStand.tryStart(player, 1), "once per siege");
		Downed.revive(player, null);
		LastStand.resetForTests(server);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void theTimerCountsDownAndNeverStartsForAParty(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		LastStand.resetForTests(server);
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		Downed.goDown(player);
		helper.assertTrue(!LastStand.tryStart(player, 2), "a party revives each other: no Last Stand");
		helper.assertTrue(!LastStand.tryStart(player, 8), "not at eight either");
		helper.assertTrue(LastStand.tryStart(player, 1), "solo");
		int at = LastStand.ticksLeft();
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(LastStand.ticksLeft() < at, "counting down: " + LastStand.ticksLeft() + " < " + at);
			Downed.revive(player, null);
			helper.assertTrue(!LastStand.isActive(), "standing up ends it");
			LastStand.resetForTests(server);
			helper.succeed();
		});
	}

	@GameTest
	public void downedPlayersAreLentAPistolAndDealHalfDamage(GameTestHelper helper) {
		ServerPlayer downed = DownedTestPlayers.make(helper, 1, 1);
		boolean lend = PlayerSystem.CONFIG.downed.lendPistol;
		boolean shoot = PlayerSystem.CONFIG.downed.keepShooting;
		try {
			PlayerSystem.CONFIG.downed.lendPistol = true;
			PlayerSystem.CONFIG.downed.keepShooting = true;
			var pistol = io.github.tmldude.blocksouls.game.run.StarterKit.pistol();
			helper.assertTrue(pistol.isPresent(), "the test mod registers a pistol");
			// A run started by another test in the batch may already have handed this player a starter pistol.
			int pistolsBefore = count(downed, pistol.get());
			Downed.goDown(downed);
			helper.assertTrue(PlayerStatus.canShoot(downed), "downed shooting is on");
			helper.assertTrue(downed.getInventory().getItem(downed.getInventory().getSelectedSlot()).is(pistol.get()), "a lent pistol in hand");

			// Compare with a standing player's hit for the scaled amount: armor and other damage rules apply to both.
			float scaled = (float) (4.0 * PlayerSystem.CONFIG.downed.downedDamageMultiplier);
			ServerPlayer standing = DownedTestPlayers.make(helper, 2, 1);
			Zombie reference = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));
			float referenceBefore = reference.getHealth();
			reference.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(standing), scaled);
			float expected = referenceBefore - reference.getHealth();

			Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
			float before = zombie.getHealth();
			zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(downed), 4.0F);
			float dealt = before - zombie.getHealth();
			helper.assertTrue(expected > 0 && Math.abs(dealt - expected) < 0.01, "downed 4.0 lands like a standing " + scaled + " (" + expected + "), dealt " + dealt);

			Downed.revive(downed, null);
			helper.assertTrue(count(downed, pistol.get()) == pistolsBefore, "the lent pistol is taken back: " + count(downed, pistol.get()) + " vs " + pistolsBefore + " before");
		} finally {
			PlayerSystem.CONFIG.downed.lendPistol = lend;
			PlayerSystem.CONFIG.downed.keepShooting = shoot;
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void partyVotesNeedAMajorityAndTimeOut(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		PartyVotes.clearForTests();
		List<ServerPlayer> party = List.of(DownedTestPlayers.make(helper, 1, 1), DownedTestPlayers.make(helper, 2, 1), DownedTestPlayers.make(helper, 3, 1));
		boolean[] result = new boolean[2];
		helper.assertTrue(PartyVotes.open(server, "relic", party, Component.literal("Open the party relic?"), 40, passed -> {
			result[0] = true;
			result[1] = passed;
		}), "opened");
		helper.assertTrue(!PartyVotes.open(server, "relic", party, Component.literal("again"), 40, passed -> { }), "one vote per id");
		helper.assertTrue(PartyVotes.vote(party.get(0), "relic", true), "voter");
		helper.assertTrue(!result[0], "one of three is not a majority (" + PartyVote.needed(3) + " needed)");
		helper.assertTrue(PartyVotes.vote(party.get(1), "relic", true), "second voter");
		helper.assertTrue(result[0] && result[1], "two of three passes");

		helper.assertTrue(PartyVotes.open(server, "chest", party, Component.literal("Open the cursed chest?"), 10, passed -> {
			result[0] = true;
			result[1] = passed;
		}), "second vote");
		result[0] = false;
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(result[0] && !result[1], "a vote nobody answers times out as a no");
			PartyVotes.clearForTests();
			helper.succeed();
		});
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				count += player.getInventory().getItem(i).getCount();
			}
		}
		return count;
	}
}
