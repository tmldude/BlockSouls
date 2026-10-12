package io.github.tmldude.blocksouls.game.player;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.downed.DownedPlay;
import io.github.tmldude.blocksouls.game.player.downed.LastStand;
import io.github.tmldude.blocksouls.game.player.party.PartyCommands;
import io.github.tmldude.blocksouls.game.player.party.PartyVotes;
import io.github.tmldude.blocksouls.game.player.ping.Pings;
import io.github.tmldude.blocksouls.game.player.downed.Revive;
import io.github.tmldude.blocksouls.game.player.classes.ClassCommands;
import io.github.tmldude.blocksouls.game.player.classes.ClassMovement;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;
import io.github.tmldude.blocksouls.game.player.relic.RelicItems;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Owned by the "game-players" session (docs/sessions/game-players.md): downed/revive, run-over detection,
 * boons and the relic choice.
 */
public final class PlayerSystem {
	public static PlayersConfig CONFIG = new PlayersConfig();

	private PlayerSystem() {
	}

	public static void init() {
		CONFIG = Config.load("game-players", PlayersConfig.class, PlayersConfig::new);

		Downed.init();
		PlayerBoons.init();
		Downed.register(CONFIG.downed);
		Revive.register(CONFIG.downed);
		DownedPlay.init();
		DownedPlay.register(CONFIG.downed);
		LastStand.register(CONFIG.downed);
		Pings.register(CONFIG.ping);
		PartyVotes.register();
		PartyCommands.register();
		PlayerBoons.register(CONFIG.boons);
		RelicItems.register();
		RelicOffers.register(CONFIG.boons);
		BoonCatalog.register();
		PlayerClasses.init();
		PlayerClasses.register(CONFIG.classes);
		ClassCommands.register();
		ClassMovement.register();
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> PlayerBoons.refreshAll(server));
		PlayerCommands.register();
	}
}
