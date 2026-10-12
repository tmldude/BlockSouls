package io.github.tmldude.blocksouls.game.player;

import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.game.player.boon.BoonCatalog;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.downed.Downed;
import io.github.tmldude.blocksouls.game.player.downed.Revive;
import io.github.tmldude.blocksouls.game.player.origin.OriginCommands;
import io.github.tmldude.blocksouls.game.player.origin.PlayerOrigins;
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
		PlayerBoons.register(CONFIG.boons);
		RelicItems.register();
		RelicOffers.register(CONFIG.boons);
		BoonCatalog.register();
		PlayerOrigins.init();
		PlayerOrigins.register(CONFIG.origins);
		OriginCommands.register();
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> PlayerBoons.refreshAll(server));
		PlayerCommands.register();
	}
}
