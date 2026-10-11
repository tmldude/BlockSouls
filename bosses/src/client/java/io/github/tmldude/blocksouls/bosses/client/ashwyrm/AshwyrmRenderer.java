package io.github.tmldude.blocksouls.bosses.client.ashwyrm;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.AABB;

import io.github.tmldude.blocksouls.bosses.ashwyrm.Ashwyrm;
import io.github.tmldude.blocksouls.bosses.client.render.BossRenderState;
import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;

/** The boss renderer plus the flight flag (wings and legs pose differently in the air), drawn at 2× scale. */
public class AshwyrmRenderer extends BossRenderer<Ashwyrm, AshwyrmModel> {
	/** Render state plus the flight flag. */
	public static class State extends BossRenderState {
		public boolean flying;
	}

	public AshwyrmRenderer(EntityRendererProvider.Context context, AshwyrmModel model) {
		super(context, model, 2.5f, "ashwyrm", 2.0f);
	}

	@Override
	public BossRenderState createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Ashwyrm entity, BossRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		if (state instanceof State wyrm) {
			wyrm.flying = entity.isFlying();
		}
	}

	@Override
	protected AABB getBoundingBoxForCulling(Ashwyrm entity, float partialTicks) {
		// The model (neck, tail, wings) reaches far past the 3.5-block hitbox.
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(6.5, 2, 6.5);
	}
}
