package io.github.tmldude.blocksouls.guns.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.client.GunClient;
import io.github.tmldude.blocksouls.guns.exotic.KindlingEngine;
import io.github.tmldude.blocksouls.guns.exotic.KindlingState;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.net.HitMarkerPayload;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

/** Ammo counter, reload bar, gun crosshair (widens with spread), hit markers and the sniper scope. */
public final class GunHud {
	private static final Identifier SCOPE = Identifier.withDefaultNamespace("textures/misc/spyglass_scope.png");
	private static final int WHITE = 0xFFFFFFFF;
	private static final int YELLOW = 0xFFFFD84A;
	private static final int RED = 0xFFFF4040;
	private static final int MARKER_TICKS = 6;

	private static int markerKind = -1;
	private static int markerTicks;
	private static float scopeScale = 0.5F;

	private GunHud() {
	}

	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> crosshair(vanilla, graphics, delta));
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath(BlockSoulsGuns.MOD_ID, "ammo"), GunHud::ammo);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, Identifier.fromNamespaceAndPath(BlockSoulsGuns.MOD_ID, "scope"), GunHud::scope);
	}

	public static void onHitMarker(int kind) {
		// A kill or precision marker isn't overwritten by a later body hit within the same flash.
		if (markerTicks <= 0 || kind >= markerKind) {
			markerKind = kind;
		}
		markerTicks = MARKER_TICKS;
	}

	public static void tick() {
		if (markerTicks > 0) {
			markerTicks--;
		}
	}

	private static boolean scoped(Minecraft minecraft) {
		GunItem gun = GunClient.heldGun();
		return gun != null && gun.stats().scope() && GunClient.isAiming() && minecraft.options.getCameraType().isFirstPerson();
	}

	private static void crosshair(HudElement vanilla, GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		GunItem gun = GunClient.heldGun();
		if (gun == null || !minecraft.options.getCameraType().isFirstPerson()) {
			vanilla.extractRenderState(graphics, delta);
			return;
		}

		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		if (!scoped(minecraft)) {
			GunStats stats = gun.stats();
			float spread = (float) (GunClient.isAiming() ? stats.adsSpread() : stats.hipSpread()) + GunClient.bloom();
			// Degrees to pixels: the GUI is ~guiHeight tall over the vertical FOV.
			float fov = minecraft.options.fov().get().floatValue();
			int gap = 2 + Math.round(spread / fov * graphics.guiHeight());
			int color = 0xDDFFFFFF;
			graphics.fill(cx - gap - 4, cy, cx - gap, cy + 1, color);
			graphics.fill(cx + gap + 1, cy, cx + gap + 5, cy + 1, color);
			graphics.fill(cx, cy - gap - 4, cx + 1, cy - gap, color);
			graphics.fill(cx, cy + gap + 1, cx + 1, cy + gap + 5, color);
			graphics.fill(cx, cy, cx + 1, cy + 1, color);
		}

		if (markerTicks > 0 && markerKind >= 0) {
			hitMarker(graphics, cx, cy);
		}
	}

	private static void hitMarker(GuiGraphicsExtractor graphics, int cx, int cy) {
		int color = markerKind == HitMarkerPayload.KILL ? RED : markerKind == HitMarkerPayload.PRECISION ? YELLOW : WHITE;
		int inner = markerKind == HitMarkerPayload.KILL ? 3 : 4;
		int outer = markerKind == HitMarkerPayload.KILL ? 9 : 7;
		for (int i = inner; i <= outer; i++) {
			graphics.fill(cx - i, cy - i, cx - i + 1, cy - i + 1, color);
			graphics.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
			graphics.fill(cx - i, cy + i, cx - i + 1, cy + i + 1, color);
			graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
		}
	}

	private static void ammo(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		GunItem gun = GunClient.heldGun();
		if (player == null || gun == null) {
			return;
		}

		ItemStack stack = player.getMainHandItem();
		GunStats stats = gun.stats();
		Font font = minecraft.font;
		int mag = gun.mag(stack);
		String reserve = stats.infiniteAmmo() || player.hasInfiniteMaterials() ? "∞" : Integer.toString(count(player.getInventory(), GunsItems.ammoFor(stats.ammo())));
		String magText = Integer.toString(mag);
		String reserveText = " / " + reserve;

		int right = graphics.guiWidth() - 8;
		int bottom = graphics.guiHeight() - 8;
		int magWidth = font.width(magText) * 2;
		int reserveWidth = font.width(reserveText);
		int magColor = mag == 0 ? RED : mag <= Math.max(1, stats.magSize() / 4) ? YELLOW : WHITE;

		graphics.pose().pushMatrix();
		graphics.pose().translate(right - reserveWidth - magWidth, bottom - 16);
		graphics.pose().scale(2, 2);
		graphics.text(font, magText, 0, 0, magColor, true);
		graphics.pose().popMatrix();
		graphics.text(font, reserveText, right - reserveWidth, bottom - 8, 0xFFBBBBBB, true);

		if (GunClient.reloading()) {
			float progress = GunClient.reloadProgress(delta.getGameTimeDeltaPartialTick(false));
			int width = 60;
			int left = right - width;
			int top = bottom - 22;
			graphics.fill(left - 1, top - 1, right + 1, top + 3, 0x99000000);
			graphics.fill(left, top, left + Math.round(width * progress), top + 2, YELLOW);
		}
		if (Weapons.KINDLING_ENGINE.equals(gun.weaponId())) {
			// Kindling Engine: heat above the ammo counter, red while venting.
			long now = player.level().getGameTime();
			KindlingState state = KindlingEngine.state(stack);
			float heat = (float) (KindlingEngine.heat(stack, now) / KindlingState.MAX);
			int width = 60;
			int left = right - width;
			int top = bottom - 28;
			graphics.fill(left - 1, top - 1, right + 1, top + 3, 0x99000000);
			graphics.fill(left, top, left + Math.round(width * heat), top + 2, state.venting(now) ? RED : heat > 0.6F ? 0xFFFF8A3A : YELLOW);
		}
	}

	private static void scope(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!scoped(minecraft)) {
			scopeScale = 0.5F;
			return;
		}
		scopeScale = Mth.lerp(0.5F * delta.getGameTimeDeltaTicks(), scopeScale, 1.125F);

		float size = Math.min(graphics.guiWidth(), graphics.guiHeight());
		float ratio = Math.min(graphics.guiWidth() / size, graphics.guiHeight() / size) * scopeScale;
		int width = Mth.floor(size * ratio);
		int height = Mth.floor(size * ratio);
		int left = (graphics.guiWidth() - width) / 2;
		int top = (graphics.guiHeight() - height) / 2;
		int right = left + width;
		int bottom = top + height;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SCOPE, left, top, 0.0F, 0.0F, width, height, width, height);
		graphics.fill(RenderPipelines.GUI, 0, bottom, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
		graphics.fill(RenderPipelines.GUI, 0, 0, graphics.guiWidth(), top, 0xFF000000);
		graphics.fill(RenderPipelines.GUI, 0, top, left, bottom, 0xFF000000);
		graphics.fill(RenderPipelines.GUI, right, top, graphics.guiWidth(), bottom, 0xFF000000);
		// Fine crosshair inside the scope.
		int cx = graphics.guiWidth() / 2;
		int cy = graphics.guiHeight() / 2;
		graphics.fill(left + width / 6, cy, right - width / 6, cy + 1, 0xCC000000);
		graphics.fill(cx, top + height / 6, cx + 1, bottom - height / 6, 0xCC000000);
	}

	private static int count(Inventory inventory, Item ammo) {
		if (ammo == null) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ammo)) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
