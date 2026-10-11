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
	/** Off the ground and out of water: mid-pounce or mid-leap. */
	public boolean airborne;
	/** Per-mob offset (ticks) for idle loops, so a horde doesn't twitch in sync. */
	public float phase;
}
