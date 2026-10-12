package io.github.tmldude.blocksouls.common;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.ability.Abilities;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.common.run.Threat;

public class BlockSoulsCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		PlayerTraits.init();
		PlayerStatus.init();
		Capabilities.init();
		Abilities.init();
		DebugCommands.init();
		VersionCheck.init();
		PartyScaling.load();
		Threat.load();
	}
}
