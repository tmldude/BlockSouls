package io.github.tmldude.blocksouls.mobs.client.render;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

import io.github.tmldude.blocksouls.common.mob.Affix;

/**
 * An elite's affix tint: the model drawn again, full-bright and translucent, with the affix's pattern
 * ({@code textures/entity/affix/<affix>.png}, painted by {@code tools/assets/mobs.py}). The pattern tiles, so one
 * texture fits every model's UV layout.
 */
public class AffixLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
	private static final Map<Affix, RenderType> TYPES = new EnumMap<>(Affix.class);

	static {
		for (Affix affix : Affix.values()) {
			TYPES.put(affix, RenderTypes.entityTranslucentEmissive(BlockSoulsMobRenderer.texture("affix/" + affix.getSerializedName())));
		}
	}

	private final Function<S, @Nullable Affix> affixOf;

	public AffixLayer(RenderLayerParent<S, M> renderer, Function<S, @Nullable Affix> affixOf) {
		super(renderer);
		this.affixOf = affixOf;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		Affix affix = affixOf.apply(state);
		if (affix == null || state.isInvisible) {
			return;
		}
		collector.order(1).submitModel(getParentModel(), state, poseStack, TYPES.get(affix), lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
	}
}
