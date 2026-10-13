package io.github.tmldude.blocksouls.guns.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;

/**
 * An exotic: a gun with a rule-breaking behavior ({@code exotic/}) on top of its JSON stats. Always
 * {@code GunRarity.EXOTIC} (one rolled perk); the tooltip names the trait. Registered alongside the regular guns,
 * never in their place.
 */
public class ExoticGunItem extends GunItem {
	public ExoticGunItem(Properties properties, String weaponId) {
		super(properties, weaponId);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		String key = "exotic." + BlockSoulsGuns.MOD_ID + "." + weaponId();
		builder.accept(Component.literal("✦ ").append(Component.translatable(key)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		builder.accept(Component.literal("   ").append(Component.translatable(key + ".desc")).withStyle(ChatFormatting.YELLOW));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
