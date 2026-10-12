package io.github.tmldude.blocksouls.game.player.trinket;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.common.CommonIds;

/** A Trinket Case ({@code CommonIds.TRINKET_CASE}): using it offers a pick-1-of-3 trinket; picking consumes it. */
public class TrinketCaseItem extends Item {
	private static TrinketCaseItem item;

	public TrinketCaseItem(Properties properties) {
		super(properties);
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CommonIds.TRINKET_CASE);
		item = Registry.register(BuiltInRegistries.ITEM, key, new TrinketCaseItem(new Item.Properties().setId(key).stacksTo(16).rarity(Rarity.EPIC)));
	}

	public static TrinketCaseItem get() {
		return item;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			TrinketOffers.open(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.blocksouls_game.trinket_case.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
