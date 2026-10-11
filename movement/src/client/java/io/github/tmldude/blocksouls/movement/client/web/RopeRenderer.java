package io.github.tmldude.blocksouls.movement.client.web;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import io.github.tmldude.blocksouls.movement.web.WebServer;

/** Draws every swinging player's web: a thin white line from their hand to the anchor. */
public final class RopeRenderer {
	private static final int COLOR = 0xFFF2F2F2;

	private RopeRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(RopeRenderer::render);
	}

	private static void render(LevelRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		float partialTicks = camera.cameraEntityPartialTicks;
		float width = client.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
		PoseStack poseStack = context.poseStack();

		for (AbstractClientPlayer player : level.players()) {
			// The local player's swing starts before the server confirms it, so use the local anchor.
			@Nullable Vec3 anchor = player == client.player ? WebHandler.anchor() : WebServer.anchor(player);
			if (anchor == null) {
				continue;
			}

			Vec3 hand = handPosition(client, player, camera, partialTicks);
			float dx = (float) (anchor.x - hand.x);
			float dy = (float) (anchor.y - hand.y);
			float dz = (float) (anchor.z - hand.z);

			poseStack.pushPose();
			poseStack.translate(hand.x - camera.pos.x, hand.y - camera.pos.y, hand.z - camera.pos.z);
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
				buffer.addVertex(pose, 0, 0, 0).setNormal(pose, dx, dy, dz).setColor(COLOR).setLineWidth(width);
				buffer.addVertex(pose, dx, dy, dz).setNormal(pose, dx, dy, dz).setColor(COLOR).setLineWidth(width);
			});
			poseStack.popPose();
		}
	}

	/** Same hand position the fishing line uses, so the web leaves the player's hand in both views. */
	private static Vec3 handPosition(Minecraft client, AbstractClientPlayer player, CameraRenderState camera, float partialTicks) {
		int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		if (camera.isFirstPerson && player == client.player) {
			float fov = client.options.fov().get();
			Vec3 offset = client.gameRenderer.mainCamera().getNearPlane(fov).getPointOnPlane(side * 0.525F, -0.1F).scale(960.0 / fov);
			return player.getEyePosition(partialTicks).add(offset);
		}

		float bodyYaw = Mth.lerp(partialTicks, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double sin = Mth.sin(bodyYaw);
		double cos = Mth.cos(bodyYaw);
		float scale = player.getScale();
		double right = side * 0.35 * scale;
		double forward = 0.8 * scale;
		double down = (player.isCrouching() ? 0.1875 : 0.0) + 0.45 * scale;
		return player.getEyePosition(partialTicks).add(-cos * right - sin * forward, -down, -sin * right + cos * forward);
	}
}
