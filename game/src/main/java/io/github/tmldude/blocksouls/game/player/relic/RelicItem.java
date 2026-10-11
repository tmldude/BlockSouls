package io.github.tmldude.blocksouls.game.player.relic;

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

import io.github.tmldude.blocksouls.game.player.boon.BoonTier;

/** Using a relic offers its boon cards; picking one consumes it. Unused relics are ordinary items. */
public class RelicItem extends Item {
	private final BoonTier tier;

	public RelicItem(BoonTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public BoonTier tier() {
		return tier;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			RelicOffers.open(serverPlayer, tier);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.blocksouls_game.relic.tooltip",
				Component.translatable("tier.blocksouls_game." + tier.id())).withStyle(ChatFormatting.GRAY));
	}
}
