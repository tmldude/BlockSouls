package io.github.tmldude.blocksouls.movement.client.web;

import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.client.Keybinds;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.movement.MovementConfig;
import io.github.tmldude.blocksouls.movement.stamina.StaminaAttachment;
import io.github.tmldude.blocksouls.movement.trait.MovementTraits;
import io.github.tmldude.blocksouls.movement.web.Pendulum;
import io.github.tmldude.blocksouls.movement.web.WebPayloads;
import io.github.tmldude.blocksouls.movement.web.WebServer;

/**
 * Web swing for the local player: press to shoot, hold to swing, release to let go; sneak reels in. The swing is
 * simulated here (player movement is client-side in vanilla); the server validates the shot and spends stamina.
 */
public final class WebHandler {
	private static MovementConfig config;
	private static KeyMapping key;
	private static boolean wasDown;

	private static @Nullable Vec3 anchor;
	private static double length;
	/** Rope point last tick; this tick's movement is the swing's velocity. */
	private static Vec3 lastPoint = Vec3.ZERO;
	private static @Nullable LocalPlayer swinger;

	private WebHandler() {
	}

	public static void register(MovementConfig movementConfig) {
		config = movementConfig;
		key = Keybinds.keyboard("web_swing", SDLScancode.SDL_SCANCODE_V);
		ClientTickEvents.END_CLIENT_TICK.register(WebHandler::tick);
		ClientNetworking.receive(WebPayloads.Drop.TYPE, (payload, context) -> anchor = null);
	}

	/** The local player's anchor, null when not swinging. */
	public static @Nullable Vec3 anchor() {
		return anchor;
	}

	public static boolean isSwinging() {
		return anchor != null;
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player != swinger) {
			// New world or respawn: the old swing belonged to a player object that's gone.
			anchor = null;
			swinger = player;
		}
		if (player == null) {
			wasDown = false;
			return;
		}

		boolean down = key.isDown();
		if (down && !wasDown) {
			tryShoot(client, player);
		} else if (!down && anchor != null) {
			release(player);
		}
		wasDown = down;

		if (anchor != null) {
			if (canSwing(player)) {
				swing(player);
			} else {
				release(player);
			}
		}
	}

	private static boolean canSwing(LocalPlayer player) {
		return config.web.enabled && player.isAlive() && !player.isSpectator() && !player.isPassenger() && !player.isFallFlying()
				&& !player.getAbilities().flying && !player.isInLiquid() && !PlayerStatus.isDowned(player) && MovementTraits.canSwing(player);
	}

	private static void tryShoot(Minecraft client, LocalPlayer player) {
		if (anchor != null || !canSwing(player)) {
			return;
		}

		BlockHitResult hit = StaminaAttachment.get(player).canAfford(config.web.staminaCost) ? findAnchor(player) : null;
		// No swing at all on servers without the mod: nothing would stop the floating kick or fall damage.
		if (hit == null || !ClientNetworking.sendToServer(new WebPayloads.Attach(hit.getLocation(), hit.getBlockPos()))) {
			playSound(player, 0.4F, 0.6F);
			return;
		}

		Vec3 point = WebServer.ropePoint(player);
		anchor = hit.getLocation();
		length = Pendulum.initialLength(point.distanceTo(anchor), config.web);
		// Seed last tick's movement with the current velocity so the swing keeps the player's momentum.
		lastPoint = point.subtract(player.getDeltaMovement());
		playSound(player, 0.8F, 1.8F);
	}

	/**
	 * Raycasts along the look direction; if that misses (or only finds something below the player), tries a few rays
	 * tilted upward, since anchors above make for real swings.
	 */
	private static @Nullable BlockHitResult findAnchor(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		double range = MovementTraits.webRange(player);
		double ropeY = WebServer.ropePoint(player).y;

		BlockHitResult look = raycast(player, eye, player.getViewVector(1.0F), range);
		if (look != null && look.getLocation().y > ropeY) {
			return look;
		}
		for (int i = 1; i <= config.web.fallbackRays; i++) {
			float pitch = (float) Math.max(-90, player.getXRot() - i * config.web.fallbackRayStepDegrees);
			BlockHitResult hit = raycast(player, eye, Vec3.directionFromRotation(pitch, player.getYRot()), range);
			if (hit != null && hit.getLocation().y > ropeY) {
				return hit;
			}
		}
		return look;
	}

	private static @Nullable BlockHitResult raycast(LocalPlayer player, Vec3 from, Vec3 direction, double range) {
		BlockHitResult hit = player.level().clip(new ClipContext(from, from.add(direction.scale(range)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	private static void swing(LocalPlayer player) {
		Vec3 point = WebServer.ropePoint(player);
		Vec3 moved = point.subtract(lastPoint);
		lastPoint = point;

		boolean reel = player.isShiftKeyDown();
		if (player.onGround() && !reel) {
			// Walking around under the anchor: let vanilla move the player; the rope pays out.
			length = Math.max(length, point.distanceTo(anchor));
			return;
		}

		Pendulum.Step step = Pendulum.step(vec(point), vec(moved), vec(anchor), length, reel,
				player.getAttributeValue(Attributes.GRAVITY), config.web);
		length = step.length();
		Pendulum.Vec velocity = step.velocity();
		player.setDeltaMovement(velocity.x(), velocity.y(), velocity.z());
	}

	private static void release(LocalPlayer player) {
		if (anchor == null) {
			return;
		}
		anchor = null;
		Vec3 velocity = player.getDeltaMovement();
		Pendulum.Vec released = Pendulum.release(vec(velocity), config.web);
		player.setDeltaMovement(released.x(), released.y(), released.z());
		ClientNetworking.sendToServer(WebPayloads.Detach.INSTANCE);
	}

	private static void playSound(LocalPlayer player, float volume, float pitch) {
		player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.COBWEB_BREAK, SoundSource.PLAYERS, volume, pitch, false);
	}

	private static Pendulum.Vec vec(Vec3 v) {
		return new Pendulum.Vec(v.x, v.y, v.z);
	}
}
