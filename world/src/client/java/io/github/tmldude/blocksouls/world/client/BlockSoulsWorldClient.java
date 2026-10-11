package io.github.tmldude.blocksouls.world.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.WorldConfig;

public class BlockSoulsWorldClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(BlockSoulsWorldClient::driftAsh);
		WorldTooltips.init();
	}

	/** Ash drifting around the player in the Nether (the fog itself is {@code FogRendererMixin}). */
	private static void driftAsh(Minecraft mc) {
		WorldConfig.AshFog cfg = BlockSoulsWorld.CONFIG.ashFog;
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (!cfg.enabled || level == null || player == null || mc.isPaused() || level.dimension() != Level.NETHER) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int i = 0; i < cfg.particlesPerTick; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 24.0;
			double y = player.getY() + (random.nextDouble() - 0.3) * 12.0;
			double z = player.getZ() + (random.nextDouble() - 0.5) * 24.0;
			level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, x, y, z,
					(random.nextDouble() - 0.5) * 0.02, -0.01 - random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.02);
		}
	}
}
