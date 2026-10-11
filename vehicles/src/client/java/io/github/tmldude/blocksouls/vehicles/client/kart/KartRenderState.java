package io.github.tmldude.blocksouls.vehicles.client.kart;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Shared by the kart and the wreck (same model, the wreck with parts missing). */
public class KartRenderState extends EntityRenderState {
	public float yRot;
	public float hurtTime;
	public float wheelSpin;
	public float steer;
	public boolean wreck;
	/** Wheels fitted (0..4). */
	public int wheels = 4;
	public boolean engine = true;
	/** Share of the wreck's scrap fitted, 0..1 (1 for a kart). */
	public float scrap = 1;
}
