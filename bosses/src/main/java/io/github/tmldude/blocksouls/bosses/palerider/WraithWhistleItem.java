package io.github.tmldude.blocksouls.bosses.palerider;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Summons (or recalls) the owner's Wraith Steed. Goes on a long cooldown when the steed is killed. */
public class WraithWhistleItem extends Item {
	public WraithWhistleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		WraithSteed steed = WraithSteeds.whistle(serverPlayer);
		if (steed == null) {
			return InteractionResult.FAIL;
		}
		player.getCooldowns().addCooldown(player.getItemInHand(hand), PaleRider.config().steed.useCooldownTicks);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.blocksouls_bosses.wraith_steed_whistle.tooltip").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("item.blocksouls_bosses.wraith_steed_whistle.tooltip2").withStyle(ChatFormatting.GRAY));
	}
}
