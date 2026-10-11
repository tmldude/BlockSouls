package io.github.tmldude.blocksouls.bosses.client.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import io.github.tmldude.blocksouls.bosses.api.Stage;

/** What boss models animate from (filled by {@link BossRenderer}). */
public class BossRenderState extends LivingEntityRenderState {
	/** Current stage (windup / active / recovery / stagger / none). */
	public Stage stage = Stage.NONE;
	/** Name of the current attack ({@code Attack.name()}), null between attacks. */
	public @Nullable String attack;
	/** Seconds since the stage started (smooth, includes partial ticks). */
	public float stageSeconds;
	/** Phase index (0 = first). */
	public int phase;
	public boolean engaged;
}
