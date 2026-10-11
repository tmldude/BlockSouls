package io.github.tmldude.blocksouls.world.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.WorldItems;

/** Mechanics-only tooltip lines: what each wacky enchantment does, and armor set bonuses. */
final class WorldTooltips {
	private WorldTooltips() {
	}

	static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			describeEnchantments(stack.get(DataComponents.ENCHANTMENTS), lines);
			describeEnchantments(stack.get(DataComponents.STORED_ENCHANTMENTS), lines);
			setBonus(stack, lines);
		});
	}

	private static void describeEnchantments(ItemEnchantments enchantments, List<Component> lines) {
		if (enchantments == null) {
			return;
		}
		for (Holder<Enchantment> holder : enchantments.keySet()) {
			holder.unwrapKey().filter(key -> key.identifier().getNamespace().equals(BlockSoulsWorld.MOD_ID)).ifPresent(key ->
					lines.add(Component.translatable("enchantment." + BlockSoulsWorld.MOD_ID + "." + key.identifier().getPath() + ".desc")
							.withStyle(ChatFormatting.DARK_GRAY)));
		}
	}

	private static void setBonus(ItemStack stack, List<Component> lines) {
		String set = null;
		if (isAny(stack, WorldItems.SCRAP_PLATE_HELMET, WorldItems.SCRAP_PLATE_CHESTPLATE, WorldItems.SCRAP_PLATE_LEGGINGS, WorldItems.SCRAP_PLATE_BOOTS)) {
			set = "scrap_plate";
		} else if (isAny(stack, WorldItems.BLIGHT_FORGED_HELMET, WorldItems.BLIGHT_FORGED_CHESTPLATE, WorldItems.BLIGHT_FORGED_LEGGINGS, WorldItems.BLIGHT_FORGED_BOOTS)) {
			set = "blight_forged";
		}
		if (set != null) {
			lines.add(Component.translatable("tooltip." + BlockSoulsWorld.MOD_ID + ".set_bonus").withStyle(ChatFormatting.GRAY));
			lines.add(Component.literal(" ").append(Component.translatable("tooltip." + BlockSoulsWorld.MOD_ID + "." + set + "_bonus"))
					.withStyle(ChatFormatting.BLUE));
		}
	}

	private static boolean isAny(ItemStack stack, Item... items) {
		for (Item item : items) {
			if (stack.is(item)) {
				return true;
			}
		}
		return false;
	}
}
