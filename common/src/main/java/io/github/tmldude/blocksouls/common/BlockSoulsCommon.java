package io.github.tmldude.blocksouls.common;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;

public class BlockSoulsCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		PlayerTraits.init();
		PlayerStatus.init();
		DebugCommands.init();
		VersionCheck.init();
	}
}
