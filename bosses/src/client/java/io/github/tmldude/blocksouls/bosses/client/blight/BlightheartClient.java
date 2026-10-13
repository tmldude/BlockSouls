package io.github.tmldude.blocksouls.bosses.client.blight;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import io.github.tmldude.blocksouls.bosses.blight.Blighthearts;
import io.github.tmldude.blocksouls.bosses.client.furnace.FurnaceKnightClient;
import io.github.tmldude.blocksouls.bosses.client.furnace.FurnaceKnightModel;
import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;

/** Client registration for the Blightheart: the Furnace Knight's rig with its own skin and a heart glow. */
public final class BlightheartClient {
	private BlightheartClient() {
	}

	public static void register() {
		EntityRendererRegistry.register(Blighthearts.TYPE, ctx ->
				new BossRenderer<>(ctx, new FurnaceKnightModel(ctx.bakeLayer(FurnaceKnightClient.LAYER)), 1.2f, "blightheart", 0.95f)
						.glow("blightheart_glow", state -> true));
	}
}
