package io.github.tmldude.blocksouls.vehicles.client.kart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;
import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;
import io.github.tmldude.blocksouls.vehicles.kart.KartWreckEntity;
import io.github.tmldude.blocksouls.vehicles.kart.RepairProgress;

/** Renders karts and wrecks with {@link KartModel}. */
public class KartRenderer<T extends Entity> extends EntityRenderer<T, KartRenderState> {
	private static final Identifier KART_TEXTURE = VehicleRegistry.id("textures/entity/kart.png");
	private static final Identifier WRECK_TEXTURE = VehicleRegistry.id("textures/entity/kart_wreck.png");

	private final KartModel model;

	public KartRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new KartModel(context.bakeLayer(KartModel.LAYER));
		this.shadowRadius = 0.9f;
	}

	@Override
	public KartRenderState createRenderState() {
		return new KartRenderState();
	}

	@Override
	public void extractRenderState(T entity, KartRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		if (entity instanceof KartEntity kart) {
			state.wreck = false;
			state.hurtTime = Math.max(0, kart.getHurtTime() - partialTicks);
			state.wheelSpin = Mth.lerp(partialTicks, kart.wheelSpinO, kart.wheelSpin);
			state.steer = Mth.lerp(partialTicks, kart.steerO, kart.steer);
			state.wheels = 4;
			state.engine = true;
			state.scrap = 1;
		} else if (entity instanceof KartWreckEntity wreck) {
			var cfg = BlockSoulsVehicles.CONFIG.wreck;
			RepairProgress progress = wreck.getProgress();
			state.wreck = true;
			state.hurtTime = 0;
			state.wheelSpin = 0.4f;
			state.steer = 0.3f;
			state.wheels = cfg.wheels <= 0 ? 4 : Math.min(4, progress.wheels() * 4 / cfg.wheels);
			state.engine = progress.engines() >= cfg.engines;
			state.scrap = cfg.scrap <= 0 ? 1 : (float) progress.scrap() / cfg.scrap;
		}
	}

	@Override
	public void submit(KartRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.rotateDegrees(Axis.YP, 180.0f - state.yRot);
		if (state.hurtTime > 0) {
			poseStack.rotateDegrees(Axis.XP, Mth.sin(state.hurtTime) * state.hurtTime * 0.4f);
		}
		if (state.wreck && state.wheels < 4) {
			// Slumped on its missing wheels.
			poseStack.translate(0, -0.12f, 0);
			poseStack.rotateDegrees(Axis.ZP, 4.0f);
			poseStack.rotateDegrees(Axis.XP, -2.5f);
		}
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		collector.submitModel(model, state, poseStack, state.wreck ? WRECK_TEXTURE : KART_TEXTURE, state.lightCoords,
				OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
