package io.github.tmldude.blocksouls.bosses.client.lockon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.client.Keybinds;

/**
 * Souls-style lock-on. The key (middle mouse) locks onto the nearest boss or elite in view within 32 blocks; pressing
 * it again cycles to the next target, or unlocks when there is no other (sneak + key always unlocks). Scrolling while
 * locked cycles too. The camera eases toward the target's chest every frame. The lock breaks after a second without
 * line of sight, beyond 40 blocks, or when the target dies.
 */
public final class LockOn {
	public static final double ACQUIRE_RANGE = 32;
	public static final double BREAK_RANGE = 40;
	/** Half-angle of the "in view" cone for picking targets. */
	private static final double VIEW_HALF_ANGLE = 60;
	private static final int LOST_SIGHT_TICKS = 20;
	/** Share of the remaining angle closed per 1/60 s. */
	private static final double LERP = 0.25;
	private static final long SCROLL_COOLDOWN_MS = 150;

	private static KeyMapping key;
	private static @Nullable LivingEntity target;
	private static int lostSight;
	private static long lastFrameNanos;
	private static long lastScrollMs;

	private LockOn() {
	}

	public static void init() {
		key = Keybinds.mouse("lock_on", 2);
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
	}

	public static @Nullable LivingEntity target() {
		return target;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			target = null;
			while (key.consumeClick()) {
				// Drain presses made while not in a world.
			}
			return;
		}
		while (key.consumeClick()) {
			if (target == null) {
				target = nearest(minecraft, player);
			} else if (player.isShiftKeyDown()) {
				target = null;
			} else {
				LivingEntity next = cycle(minecraft, player, 1);
				target = next == target ? null : next;
			}
			lostSight = 0;
		}
		if (target == null) {
			return;
		}
		if (!target.isAlive() || target.isRemoved() || target.level() != player.level() || player.distanceTo(target) > BREAK_RANGE
				|| player.isSpectator()) {
			target = null;
			return;
		}
		lostSight = player.hasLineOfSight(target) ? 0 : lostSight + 1;
		if (lostSight >= LOST_SIGHT_TICKS) {
			target = null;
		}
	}

	/** Mouse wheel while locked: cycle. Returns true when the scroll was used (so the hotbar doesn't move). */
	public static boolean onScroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		if (target == null || minecraft.player == null || minecraft.gui.screen() != null || amount == 0) {
			return false;
		}
		long now = Util.getMillis();
		if (now - lastScrollMs >= SCROLL_COOLDOWN_MS) {
			lastScrollMs = now;
			target = cycle(minecraft, minecraft.player, amount < 0 ? 1 : -1);
		}
		return true;
	}

	/** Every frame (after mouse input): ease the view toward the target's chest. */
	public static void onFrame() {
		long now = System.nanoTime();
		double seconds = lastFrameNanos == 0 ? 0 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
		lastFrameNanos = now;
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (target == null || player == null || minecraft.gui.screen() != null || seconds <= 0) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 eye = player.getEyePosition(partial);
		Vec3 chest = chest(target, partial);
		Vec3 d = chest.subtract(eye);
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float wantYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float wantPitch = (float) -(Mth.atan2(d.y, horizontal) * Mth.RAD_TO_DEG);
		double alpha = 1 - Math.pow(1 - LERP, seconds * 60);
		float dYaw = (float) (Mth.wrapDegrees(wantYaw - player.getYRot()) * alpha);
		float dPitch = (float) ((wantPitch - player.getXRot()) * alpha);
		player.setYRot(player.getYRot() + dYaw);
		player.setXRot(Mth.clamp(player.getXRot() + dPitch, -90f, 90f));
		player.yRotO += dYaw;
		player.xRotO = Mth.clamp(player.xRotO + dPitch, -90f, 90f);
	}

	public static Vec3 chest(LivingEntity entity, float partial) {
		return entity.getPosition(partial).add(0, entity.getBbHeight() * 0.65, 0);
	}

	private static List<LivingEntity> candidates(Minecraft minecraft, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1f);
		double cos = Math.cos(Math.toRadians(VIEW_HALF_ANGLE));
		AABB box = player.getBoundingBox().inflate(ACQUIRE_RANGE);
		List<LivingEntity> found = new ArrayList<>();
		for (LivingEntity entity : minecraft.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()
				&& (e.is(CommonTags.BOSSES) || e.is(CommonTags.ELITES)))) {
			Vec3 to = chest(entity, 1f).subtract(eye);
			double distance = to.length();
			if (distance > ACQUIRE_RANGE || distance < 1e-3) {
				continue;
			}
			if (to.scale(1 / distance).dot(look) >= cos && player.hasLineOfSight(entity)) {
				found.add(entity);
			}
		}
		return found;
	}

	private static @Nullable LivingEntity nearest(Minecraft minecraft, LocalPlayer player) {
		return candidates(minecraft, player).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
	}

	/** The next candidate left-to-right (by screen angle) after the current target; the current one if it's alone. */
	private static @Nullable LivingEntity cycle(Minecraft minecraft, LocalPlayer player, int step) {
		List<LivingEntity> list = candidates(minecraft, player);
		if (target != null && !list.contains(target)) {
			list.add(target);
		}
		if (list.isEmpty()) {
			return null;
		}
		list.sort(Comparator.comparingDouble(e -> relativeYaw(player, e)));
		int index = Math.max(0, list.indexOf(target));
		return list.get(Math.floorMod(index + step, list.size()));
	}

	private static double relativeYaw(LocalPlayer player, LivingEntity entity) {
		Vec3 d = entity.position().subtract(player.position());
		double yaw = Math.toDegrees(Math.atan2(d.z, d.x)) - 90;
		return Mth.wrapDegrees(yaw - player.getYRot());
	}
}
