package io.github.tmldude.blocksouls.game.player.origin;

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

import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/**
 * {@code blocksouls_game:origin_sigil}: using it opens the origin select screen; picking an origin consumes it. Handed
 * out at run start when origins are on. Not craftable (an end item, exempt from the crafting web).
 */
public class OriginSigilItem extends Item {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "origin_sigil");
	public static OriginSigilItem INSTANCE;

	public OriginSigilItem(Properties properties) {
		super(properties);
	}

	public static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ID);
		INSTANCE = Registry.register(BuiltInRegistries.ITEM, key, new OriginSigilItem(new Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			PlayerOrigins.openSelect(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.blocksouls_game.origin_sigil.tooltip").withStyle(ChatFormatting.GRAY));
	}
}
