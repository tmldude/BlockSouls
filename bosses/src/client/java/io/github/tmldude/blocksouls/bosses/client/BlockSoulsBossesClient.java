package io.github.tmldude.blocksouls.bosses.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.tmldude.blocksouls.bosses.BlockSoulsBosses;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossParts;
import io.github.tmldude.blocksouls.bosses.client.bar.BossBarHud;
import io.github.tmldude.blocksouls.bosses.client.bar.ClientBossBars;
import io.github.tmldude.blocksouls.bosses.client.furnace.FurnaceKnightClient;
import io.github.tmldude.blocksouls.bosses.client.lockon.LockOn;
import io.github.tmldude.blocksouls.bosses.client.lockon.LockOnReticle;
import io.github.tmldude.blocksouls.common.CommonIds;

public class BlockSoulsBossesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientBossBars.init();
		LockOn.init();
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, CommonIds.id(BlockSoulsBosses.MOD_ID, "boss_bar"), BossBarHud::extract);
		HudElementRegistry.addLast(CommonIds.id(BlockSoulsBosses.MOD_ID, "lock_on"), LockOnReticle::extract);

		// Weak-point parts must be pickable by the crosshair on the client too.
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof BossEntity boss) {
				BossParts.track(boss);
			}
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof BossEntity boss) {
				BossParts.untrack(boss);
			}
		});

		// Bosses: one line each. Stage 2b sessions add theirs below.
		FurnaceKnightClient.register();
		io.github.tmldude.blocksouls.bosses.client.ashwyrm.AshwyrmClient.register();
	}
}
