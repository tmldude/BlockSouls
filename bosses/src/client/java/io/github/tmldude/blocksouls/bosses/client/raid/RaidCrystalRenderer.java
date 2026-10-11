package io.github.tmldude.blocksouls.bosses.client.raid;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.client.render.BossRenderer;
import io.github.tmldude.blocksouls.bosses.raid.RaidCrystal;

/**
 * Draws a {@link RaidCrystal} with vanilla's crystal model in its own bone-and-violet skin. When it's the crystal's
 * turn in the obelisk's show it burns full-bright in its sequence color and beams at the obelisk.
 */
public class RaidCrystalRenderer extends EntityRenderer<RaidCrystal, RaidCrystalRenderState> {
	private static final Identifier TEXTURE = BossRenderer.texture("raid_crystal");
	private final EndCrystalModel model;

	public RaidCrystalRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5f;
		this.model = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
	}

	@Override
	public void submit(RaidCrystalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean lit = state.color != 0;
		int light = lit ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
		int tint = lit ? ARGB.opaque(state.color) : -1;
		poseStack.pushPose();
		poseStack.scale(2.0f, 2.0f, 2.0f);
		poseStack.translate(0.0f, -0.5f, 0.0f);
		collector.submitModel(model, state, poseStack, model.renderType(TEXTURE), light, OverlayTexture.NO_OVERLAY, tint, null, state.outlineColor);
		poseStack.popPose();
		Vec3 beam = state.beamOffset;
		if (beam != null) {
			float crystalY = EndCrystalRenderer.getY(state.ageInTicks);
			poseStack.translate(beam);
			EnderDragonRenderer.submitCrystalBeams((float) -beam.x, (float) -beam.y + crystalY, (float) -beam.z, state.ageInTicks, poseStack, collector,
					LightCoordsUtil.FULL_BRIGHT);
		}
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public RaidCrystalRenderState createRenderState() {
		return new RaidCrystalRenderState();
	}

	@Override
	public void extractRenderState(RaidCrystal entity, RaidCrystalRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.ageInTicks = entity.time + partialTicks;
		state.showsBottom = false;
		state.color = entity.color();
		BlockPos target = entity.beamTarget();
		state.beamOffset = target == null ? null : Vec3.atCenterOf(target).subtract(entity.getPosition(partialTicks));
	}

	@Override
	public boolean shouldRender(RaidCrystal entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		return super.shouldRender(entity, culler, camX, camY, camZ, partialTicks) || entity.beamTarget() != null;
	}
}
