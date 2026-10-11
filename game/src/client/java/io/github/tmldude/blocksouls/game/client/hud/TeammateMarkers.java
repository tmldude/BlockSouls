package io.github.tmldude.blocksouls.game.client.hud;

import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/**
 * A marker and distance over every downed teammate, drawn on the HUD so it shows through walls. Off-screen
 * teammates get the marker pinned to the screen edge in their direction.
 */
final class TeammateMarkers {
	private static final int EDGE_MARGIN = 14;

	private TeammateMarkers() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (GameHud.hidden(minecraft) || !GameHud.config().showTeammateMarkers) {
			return;
		}

		Camera camera = minecraft.gameRenderer.mainCamera();
		float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		double maxDistance = GameHud.config().markerMaxDistance;

		for (Player other : minecraft.level.players()) {
			if (other == minecraft.player || !PlayerStatus.isDowned(other)) {
				continue;
			}
			Vec3 target = other.getPosition(partial).add(0, 0.9, 0);
			double distance = minecraft.player.getPosition(partial).distanceTo(target);
			if (distance > maxDistance) {
				continue;
			}

			int[] screen = project(camera, target, width, height);
			int x = screen[0];
			int y = screen[1];
			GameHud.blitIcon(graphics, GameHud.icon("downed"), x - 8, y - 8, 16);
			String label = Math.round(distance) + "m";
			graphics.centeredText(minecraft.font, label, x, y + 9, 0xFFFF8A8A);
		}
	}

	/** Screen position of {@code target}, clamped inside the screen edges when it's off-screen or behind. */
	static int[] project(Camera camera, Vec3 target, int width, int height) {
		Vec3 rel = target.subtract(camera.position());
		Vector3fc forward = camera.forwardVector();
		Vector3fc up = camera.upVector();
		Vector3fc left = camera.leftVector();
		double z = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
		double right = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
		double upward = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();

		double tanHalf = Math.tan(Math.toRadians(camera.getFov()) / 2.0);
		double aspect = (double) width / height;
		double halfW = width / 2.0;
		double halfH = height / 2.0;

		if (z > 0.05) {
			double ndcX = right / (z * tanHalf * aspect);
			double ndcY = upward / (z * tanHalf);
			double sx = halfW * (1 + ndcX);
			double sy = halfH * (1 - ndcY);
			if (sx >= EDGE_MARGIN && sx <= width - EDGE_MARGIN && sy >= EDGE_MARGIN && sy <= height - EDGE_MARGIN) {
				return new int[] {(int) sx, (int) sy};
			}
		}

		// Off-screen or behind: pin to the edge along the direction from the screen center.
		double dx = right;
		double dy = -upward;
		if (Math.abs(dx) < 1.0E-4 && Math.abs(dy) < 1.0E-4) {
			dy = 1;
		}
		double scale = Math.min((halfW - EDGE_MARGIN) / Math.max(Math.abs(dx), 1.0E-4), (halfH - EDGE_MARGIN) / Math.max(Math.abs(dy), 1.0E-4));
		return new int[] {(int) (halfW + dx * scale), (int) (halfH + dy * scale)};
	}
}
