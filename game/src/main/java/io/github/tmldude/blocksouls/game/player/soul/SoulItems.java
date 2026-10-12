package io.github.tmldude.blocksouls.game.player.soul;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.relic.RelicOffers;
import io.github.tmldude.blocksouls.game.player.trinket.TrinketOffers;

/** The Soul ({@code CommonIds.SOUL}, the run's currency) and the Reroll Charm (soul-infused crafting). */
public final class SoulItems {
	public static final Identifier REROLL_CHARM = Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "reroll_charm");

	private static Item soul;
	private static Item rerollCharm;

	private SoulItems() {
	}

	public static void register() {
		ResourceKey<Item> soulKey = ResourceKey.create(Registries.ITEM, CommonIds.SOUL);
		soul = Registry.register(BuiltInRegistries.ITEM, soulKey, new Item(new Item.Properties().setId(soulKey).rarity(Rarity.UNCOMMON)));
		ResourceKey<Item> charmKey = ResourceKey.create(Registries.ITEM, REROLL_CHARM);
		rerollCharm = Registry.register(BuiltInRegistries.ITEM, charmKey,
				new RerollCharmItem(new Item.Properties().setId(charmKey).stacksTo(16).rarity(Rarity.RARE)));
	}

	public static Item soul() {
		return soul;
	}

	public static Item rerollCharm() {
		return rerollCharm;
	}

	/** Using it rerolls the pending relic or trinket offer; nothing pending leaves it unspent. */
	public static class RerollCharmItem extends Item {
		public RerollCharmItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			if (RelicOffers.reroll(serverPlayer) || TrinketOffers.reroll(serverPlayer)) {
				player.getItemInHand(hand).shrink(1);
				return InteractionResult.SUCCESS;
			}
			serverPlayer.sendSystemMessage(Component.translatable("message.blocksouls_game.reroll.nothing").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.blocksouls_game.reroll_charm.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}
}
