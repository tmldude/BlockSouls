package io.github.tmldude.blocksouls.mobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What our models animate from: the mob's telegraphed action and attack swing. */
public class MobRenderState extends LivingEntityRenderState {
	/** {@code WaveMob.action()}, 0 when idle. */
	public byte action;
	/** Ticks into the action, with partial ticks. */
	public float actionTicks;
	/** 0..1 through a melee swing. */
	public float swing;
	public boolean aggressive;
}
