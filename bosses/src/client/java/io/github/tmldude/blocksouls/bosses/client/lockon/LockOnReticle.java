package io.github.tmldude.blocksouls.bosses.client.lockon;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A small pulsing diamond over the locked target's chest. */
public final class LockOnReticle {
	private static final int COLOR = 0xFFFFE2A8;
	private static final int SHADOW = 0xA0000000;

	private LockOnReticle() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LivingEntity target = LockOn.target();
		if (target == null || minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
		Vec3 rel = LockOn.chest(target, partial).subtract(camera.position());
		Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
		Vector4f clip = matrix.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f));
		if (clip.w <= 0.05f) {
			return;
		}
		float ndcX = clip.x / clip.w;
		float ndcY = clip.y / clip.w;
		if (Math.abs(ndcX) > 1.2f || Math.abs(ndcY) > 1.2f) {
			return;
		}
		int x = Math.round((ndcX + 1) / 2 * graphics.guiWidth());
		int y = Math.round((1 - ndcY) / 2 * graphics.guiHeight());
		int r = 4 + Math.round(Mth.sin(target.tickCount * 0.3f + partial * 0.3f) * 1.0f);
		diamond(graphics, x + 1, y + 1, r, SHADOW);
		diamond(graphics, x, y, r, COLOR);
	}

	/** A hollow diamond of "radius" r. */
	private static void diamond(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
		for (int i = 0; i <= r; i++) {
			int w = r - i;
			graphics.fill(cx - w, cy - i, cx - w + 1, cy - i + 1, color);
			graphics.fill(cx + w, cy - i, cx + w + 1, cy - i + 1, color);
			graphics.fill(cx - w, cy + i, cx - w + 1, cy + i + 1, color);
			graphics.fill(cx + w, cy + i, cx + w + 1, cy + i + 1, color);
		}
	}
}
