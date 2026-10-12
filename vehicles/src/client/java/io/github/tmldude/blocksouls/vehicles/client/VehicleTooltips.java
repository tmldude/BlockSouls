package io.github.tmldude.blocksouls.vehicles.client;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;
import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;

/** Mechanics-only tooltip lines: what wheels, engines and fuel cans are for. */
final class VehicleTooltips {
	private VehicleTooltips() {
	}

	static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			var wreck = BlockSoulsVehicles.CONFIG.wreck;
			if (stack.is(VehicleRegistry.WHEEL)) {
				lines.add(line("wheel", wreck.wheels));
			} else if (stack.is(VehicleRegistry.ENGINE)) {
				lines.add(line("engine", wreck.engines));
			} else if (stack.is(VehicleRegistry.FUEL_CAN)) {
				Integer fuel = BlockSoulsVehicles.CONFIG.fuel.items.get(BuiltInRegistries.ITEM.getKey(VehicleRegistry.FUEL_CAN).toString());
				int share = fuel == null ? 0 : Math.round(100f * fuel / Math.max(1, BlockSoulsVehicles.CONFIG.fuel.fuelCapacity));
				lines.add(line("fuel_can", share));
			}
		});
	}

	private static Component line(String item, int amount) {
		return Component.translatable("tooltip." + BlockSoulsVehicles.MOD_ID + "." + item, amount).withStyle(ChatFormatting.GRAY);
	}
}
