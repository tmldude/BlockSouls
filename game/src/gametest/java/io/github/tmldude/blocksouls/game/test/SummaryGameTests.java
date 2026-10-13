package io.github.tmldude.blocksouls.game.test;

import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;
import io.github.tmldude.blocksouls.game.player.summary.PlayerRunSummary;
import io.github.tmldude.blocksouls.game.player.summary.SummaryPayloads;
import io.github.tmldude.blocksouls.game.player.summary.SummaryStats;

/** Run summary v2: game events land on the right player's card. */
public class SummaryGameTests {
	@GameTest
	public void cardsTallyRevivesBossKillsClassAndBestBoon(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer striker = DownedTestPlayers.make(helper, 1, 1);
		ServerPlayer medic = DownedTestPlayers.make(helper, 2, 1);
		SummaryStats stats = PlayerRunSummary.stats();
		try {
			PlayerClasses.choose(striker, Identifier.fromNamespaceAndPath("blocksouls_game", "striker"));
			PlayerRunEvents.REVIVED.invoker().onRevived(striker, medic);
			PlayerRunEvents.REVIVED.invoker().onRevived(medic, null); // a self-revive (Last Stand) credits nobody
			BossEvents.KILLED.invoker().onBoss(helper.getLevel(), CommonIds.FURNACE_KNIGHT, striker, List.of(striker, medic));
			PlayerBoons.grant(striker, BoonCatalog.get(Identifier.fromNamespaceAndPath("blocksouls_game", "swift_feet")));
			PlayerBoons.grant(striker, BoonCatalog.get(Identifier.fromNamespaceAndPath("blocksouls_game", "powder_burn")));

			SummaryPayloads.Cards cards = PlayerRunSummary.build(server, Runs.get(server));
			List<ServerPlayer> online = server.getPlayerList().getPlayers();
			SummaryPayloads.Card strikerCard = cards.cards().get(online.indexOf(striker));
			SummaryPayloads.Card medicCard = cards.cards().get(online.indexOf(medic));

			helper.assertTrue(cards.cards().size() == online.size(), "one card per player");
			helper.assertTrue(medicCard.revivesGiven() == stats.get(medic.getUUID()).revivesGiven && medicCard.revivesGiven() >= 1, "the reviver gets the credit");
			helper.assertTrue(strikerCard.bossKills() >= 1 && medicCard.bossKills() >= 1, "every player in the fight gets the boss");
			helper.assertTrue(strikerCard.playerClass().map(id -> id.getPath().equals("striker")).orElse(false), "the class crest");
			helper.assertTrue(strikerCard.bestBoon().map(id -> id.getPath().equals("powder_burn")).orElse(false),
					"a keystone is the best boon, got " + strikerCard.bestBoon());
			helper.assertTrue(cards.columns() >= 1, "columns from config");
		} finally {
			PlayerBoons.clearAll(striker);
			PlayerClasses.clear(striker);
		}
		helper.succeed();
	}
}
