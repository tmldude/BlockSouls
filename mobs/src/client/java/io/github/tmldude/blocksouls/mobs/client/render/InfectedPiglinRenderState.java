package io.github.tmldude.blocksouls.mobs.client.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

import io.github.tmldude.blocksouls.common.mob.Affix;

/** The humanoid state plus the elite affix tint. */
public class InfectedPiglinRenderState extends HumanoidRenderState {
	public @Nullable Affix affix;
}
