package io.github.tmldude.blocksouls.vehicles.client.kart;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;
import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.VehiclesConfig;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;
import io.github.tmldude.blocksouls.vehicles.kart.KartFuel;

/**
 * While driving: a fuel can icon and a vertical gauge right of the hotbar (past the attack indicator), amber
 * when there's fuel, flashing red under the low-fuel share.
 */
final class KartFuelHud {
	private static final int HEIGHT = 18;
	private static final int WIDTH = 4;
	private static final int OK_COLOR = 0xFFE0A030;
	private static final int LOW_COLOR = 0xFFE03020;

	private KartFuelHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || !BlockSoulsVehicles.CONFIG.hud.showFuelGauge
				|| !(minecraft.player.getVehicle() instanceof KartEntity kart) || kart.getControllingPassenger() != minecraft.player) {
			return;
		}

		VehiclesConfig.Fuel cfg = BlockSoulsVehicles.CONFIG.fuel;
		int fuel = kart.getFuel();
		float share = (float) KartFuel.share(fuel, cfg);
		boolean low = KartFuel.low(fuel, cfg);
		// Flashes about twice a second when low.
		boolean flashOn = !low || (minecraft.player.tickCount / 5) % 2 == 0;

		int x = graphics.guiWidth() / 2 + 91 + 26;
		int bottom = graphics.guiHeight() - 2;
		int top = bottom - HEIGHT;
		graphics.fakeItem(new ItemStack(VehicleRegistry.FUEL_CAN), x, top + 1);

		int barX = x + 18;
		graphics.fill(barX - 1, top - 1, barX + WIDTH + 1, bottom + 1, 0xB0000000);
		int filled = Math.round(HEIGHT * share);
		if (share > 0 && filled == 0) {
			filled = 1;
		}
		if (flashOn) {
			graphics.fill(barX, bottom - filled, barX + WIDTH, bottom, low ? LOW_COLOR : OK_COLOR);
		}
		if (low && flashOn && fuel == 0) {
			// Empty: an outline so the dead gauge still blinks.
			graphics.outline(barX - 1, top - 1, WIDTH + 2, HEIGHT + 2, LOW_COLOR);
		}
	}
}
