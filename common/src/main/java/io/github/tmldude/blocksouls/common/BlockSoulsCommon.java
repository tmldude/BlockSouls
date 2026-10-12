package io.github.tmldude.blocksouls.common;

import net.fabricmc.api.ModInitializer;

import io.github.tmldude.blocksouls.common.debug.ConfigCommands;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;
import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.common.run.Threat;
import io.github.tmldude.blocksouls.common.run.ThreatConfig;

public class BlockSoulsCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		PlayerTraits.init();
		PlayerStatus.init();
		Capabilities.init();
		DebugCommands.init();
		ConfigCommands.init();
		VersionCheck.init();
		PartyScaling.load();
		Threat.load();
		// Reloads copy into the instances loaded above; re-pointing also covers a test that swapped them out.
		// (Registered here rather than in PartyScaling/Threat so those stay pure, Fabric-free math.)
		ConfigEvents.RELOADED.register((name, config) -> {
			if (config instanceof PartyConfig party) {
				PartyScaling.setConfig(party);
			} else if (config instanceof ThreatConfig threat) {
				Threat.setConfig(threat);
			}
		});
	}
}
