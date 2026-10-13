package io.github.tmldude.blocksouls.guns.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.item.PartUses;

/** Gun parts say which guns they're crafted into. */
final class PartTooltips {
	private PartTooltips() {
	}

	static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
			if (!BlockSoulsGuns.MOD_ID.equals(id.getNamespace())) {
				return;
			}
			List<String> guns = PartUses.of(id.getPath());
			if (guns.isEmpty()) {
				return;
			}
			MutableComponent names = Component.empty();
			for (int i = 0; i < guns.size(); i++) {
				if (i > 0) {
					names.append(", ");
				}
				names.append(Component.translatable("item." + BlockSoulsGuns.MOD_ID + "." + guns.get(i)));
			}
			// Right under the name.
			lines.add(Math.min(1, lines.size()), Component.translatable("tooltip." + BlockSoulsGuns.MOD_ID + ".part_used_in", names)
					.withStyle(ChatFormatting.GRAY));
		});
	}
}
