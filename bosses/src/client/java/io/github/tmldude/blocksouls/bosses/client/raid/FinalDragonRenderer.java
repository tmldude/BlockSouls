package io.github.tmldude.blocksouls.bosses.client.raid;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.AABB;

import io.github.tmldude.blocksouls.bosses.client.render.BossRenderState;
import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;
import io.github.tmldude.blocksouls.bosses.raid.FinalDragon;

/** {@link BossRenderer} plus the dragon's own state, and culling that accounts for its 19-block wingspan. */
public class FinalDragonRenderer extends BossRenderer<FinalDragon, FinalDragonModel> {
	public FinalDragonRenderer(EntityRendererProvider.Context context) {
		super(context, new FinalDragonModel(context.bakeLayer(FinalDragonClient.LAYER)), 3.0f, "final_dragon", 2.0f);
	}

	@Override
	public BossRenderState createRenderState() {
		return new FinalDragonRenderState();
	}

	@Override
	public void extractRenderState(FinalDragon entity, BossRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof FinalDragonRenderState s) {
			s.mode = entity.mode();
			s.shielded = entity.shielded();
			s.enraged = entity.enraged();
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(FinalDragon entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(10, 3, 10);
	}
}
