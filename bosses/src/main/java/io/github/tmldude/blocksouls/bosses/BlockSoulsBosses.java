package io.github.tmldude.blocksouls.bosses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossParts;
import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.bar.BossBars;
import io.github.tmldude.blocksouls.bosses.command.BossCommands;
import io.github.tmldude.blocksouls.bosses.field.FieldBosses;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnights;
import io.github.tmldude.blocksouls.bosses.registry.BossBlocks;
import io.github.tmldude.blocksouls.bosses.registry.BossSounds;
import io.github.tmldude.blocksouls.common.Config;

public class BlockSoulsBosses implements ModInitializer {
	public static final String MOD_ID = "blocksouls_bosses";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static BossesConfig CONFIG = new BossesConfig();

	@Override
	public void onInitialize() {
		CONFIG = Config.load("bosses", BossesConfig.class, BossesConfig::new);
		BossSounds.init();
		BossBlocks.init();
		BossBars.init();
		Bosses.init();
		FieldBosses.init();
		BossCommands.init();

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof BossEntity boss) {
				BossParts.track(boss);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof BossEntity boss) {
				BossParts.untrack(boss);
			}
		});
		ServerLevelEvents.UNLOAD.register((server, level) -> BossParts.forget(level));

		// Bosses: one line each. Stage 2b sessions add theirs below.
		FurnaceKnights.register();
		io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrms.register();
	}
}
