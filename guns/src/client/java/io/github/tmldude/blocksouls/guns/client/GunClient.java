package io.github.tmldude.blocksouls.guns.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.logic.FireGate;
import io.github.tmldude.blocksouls.guns.logic.ShotMath;
import io.github.tmldude.blocksouls.guns.net.FirePayload;
import io.github.tmldude.blocksouls.guns.net.ReloadPayload;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;

/**
 * Client input for guns: attack clicks become trigger pulls (the server decides whether a shot happens), R
 * reloads. Predicts the fire rate locally for recoil and muzzle flash so shooting feels instant.
 */
public final class GunClient {
	private static final int RECOIL_RECOVER_TICKS = 6;

	private static KeyMapping reloadKey;
	private static int pendingClicks;
	private static final FireGate GATE = new FireGate();
	private static long ticks;
	private static int lastSlot = -1;

	private static int reloadTicks;
	private static int reloadTotal;

	private static float recoilToRecover;
	private static int recoilTicksLeft;
	/** Extra crosshair spread from recent shots, in degrees; decays every tick. */
	private static float bloom;

	private GunClient() {
	}

	public static void register(KeyMapping reload) {
		reloadKey = reload;
	}

	public static @Nullable GunItem heldGun() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return null;
		}
		return GunItem.of(player.getMainHandItem());
	}

	/** Called from the start of {@code Minecraft.handleKeybinds}: takes attack clicks before vanilla turns them into hits. */
	public static void captureAttackClicks() {
		Minecraft minecraft = Minecraft.getInstance();
		if (heldGun() == null || minecraft.gui.screen() != null) {
			return;
		}
		while (minecraft.options.keyAttack.consumeClick()) {
			pendingClicks++;
		}
	}

	/** Vanilla's held-attack (mining) is off while a gun is in hand. */
	public static boolean blocksAttack() {
		return heldGun() != null;
	}

	public static boolean isAiming() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
				&& player.getUseItem().getItem() instanceof GunItem;
	}

	public static void tick(Minecraft minecraft) {
		ticks++;
		LocalPlayer player = minecraft.player;
		GunItem gun = heldGun();
		if (player == null || gun == null) {
			pendingClicks = 0;
			reloadTotal = 0;
			lastSlot = -1;
			recover(player);
			return;
		}

		int slot = player.getInventory().getSelectedSlot();
		if (slot != lastSlot) {
			// Swapping cancels a reload on the server too.
			reloadTotal = 0;
			pendingClicks = 0;
			lastSlot = slot;
		}

		while (reloadKey.consumeClick()) {
			ClientNetworking.sendToServer(ReloadPayload.INSTANCE);
		}
		if (reloadTotal > 0 && ++reloadTicks > reloadTotal + 10) {
			reloadTotal = 0; // the server's "done" never came
		}

		ItemStack stack = player.getMainHandItem();
		GunStats stats = gun.stats();
		boolean held = stats.automatic() && minecraft.gui.screen() == null && minecraft.options.keyAttack.isDown()
				&& minecraft.mouseHandler.isMouseGrabbed();
		if (PlayerStatus.isDowned(player) || reloading()) {
			pendingClicks = 0;
		} else if (pendingClicks > 0 || held) {
			pull(player, stack, gun, stats, pendingClicks > 0);
		}

		recover(player);
		bloom = Math.max(0, bloom * 0.8F - 0.05F);
	}

	private static void pull(LocalPlayer player, ItemStack stack, GunItem gun, GunStats stats, boolean click) {
		if (gun.mag(stack) <= 0) {
			// Empty: the server auto-reloads on a trigger pull. Only real clicks count, so a held trigger doesn't spam.
			if (click) {
				ClientNetworking.sendToServer(FirePayload.INSTANCE);
			}
			pendingClicks = 0;
			return;
		}
		if (!GATE.tryFire(ticks, ShotMath.fireIntervalTicks(stats.rpm()), 0)) {
			// Too soon: keep one click buffered so a fast tap still fires at the next allowed tick.
			pendingClicks = Math.min(pendingClicks, 1);
			return;
		}

		pendingClicks = Math.max(0, pendingClicks - 1);
		ClientNetworking.sendToServer(FirePayload.INSTANCE);
		kick(player, (float) stats.recoil() * (isAiming() ? 0.6F : 1.0F));
		bloom = Math.min(bloom + (float) stats.recoil() * 0.4F, 6.0F);
		GunFx.muzzleFlash(player, stats);
	}

	private static void kick(LocalPlayer player, float degrees) {
		player.setXRot(player.getXRot() - degrees);
		recoilToRecover += degrees * 0.8F;
		recoilTicksLeft = RECOIL_RECOVER_TICKS;
	}

	private static void recover(@Nullable LocalPlayer player) {
		if (recoilTicksLeft <= 0) {
			recoilToRecover = 0;
			return;
		}
		float step = recoilToRecover / recoilTicksLeft;
		if (player != null) {
			player.setXRot(player.getXRot() + step);
		}
		recoilToRecover -= step;
		recoilTicksLeft--;
	}

	public static void onReloadState(int ticksTotal) {
		reloadTotal = ticksTotal;
		reloadTicks = 0;
	}

	public static boolean reloading() {
		return reloadTotal > 0;
	}

	/** 0..1 */
	public static float reloadProgress(float partialTick) {
		return reloadTotal <= 0 ? 0 : Math.min(1, (reloadTicks + partialTick) / reloadTotal);
	}

	public static float bloom() {
		return bloom;
	}
}
