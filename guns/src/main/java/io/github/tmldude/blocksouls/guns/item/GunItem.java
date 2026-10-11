package io.github.tmldude.blocksouls.guns.item;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.logic.Perk;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

/**
 * A gun. Firing and reloading are handled by {@code GunServer} (attack fires, R reloads); holding use aims down
 * sights, which is vanilla item use so the server knows who is aiming.
 */
public class GunItem extends Item {
	private final String weaponId;

	public GunItem(Properties properties, String weaponId) {
		super(properties);
		this.weaponId = weaponId;
	}

	public String weaponId() {
		return weaponId;
	}

	public GunStats stats() {
		return Weapons.get(weaponId);
	}

	public static @Nullable GunItem of(ItemStack stack) {
		return stack.getItem() instanceof GunItem gun ? gun : null;
	}

	/** Rounds in the magazine. */
	public int mag(ItemStack stack) {
		return Math.clamp(stack.getOrDefault(GunComponents.MAG, stats().magSize()), 0, stats().magSize());
	}

	public void setMag(ItemStack stack, int rounds) {
		int clamped = Math.clamp(rounds, 0, stats().magSize());
		if (clamped == stats().magSize()) {
			stack.remove(GunComponents.MAG);
		} else {
			stack.set(GunComponents.MAG, clamped);
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (PlayerStatus.isDowned(player)) {
			return InteractionResult.FAIL;
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
		return false;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!GunRolls.isRolled(stack) && GunRolls.canRoll(stack)) {
			GunRolls.roll(stack, GunRolls.odds(null), level.getRandom());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		GunStats stats = stats();
		String key = "tooltip." + BlockSoulsGuns.MOD_ID + ".";

		if (stats.infiniteAmmo()) {
			builder.accept(Component.translatable(key + "infinite_ammo").withStyle(ChatFormatting.GREEN));
		} else {
			builder.accept(Component.translatable(key + "ammo." + stats.ammo()).withStyle(ChatFormatting.GRAY));
		}
		String damage = stats.pellets() > 1 ? fmt(stats.damage()) + "×" + stats.pellets() : fmt(stats.damage());
		builder.accept(Component.translatable(key + "stats", damage, fmt(stats.rpm()), stats.magSize(), fmt(stats.reloadSeconds()))
				.withStyle(ChatFormatting.DARK_GRAY));
		builder.accept(Component.translatable(key + (stats.automatic() ? "automatic" : "semi_auto"), fmt(stats.precisionMult()))
				.withStyle(ChatFormatting.DARK_GRAY));

		if (!stats.perks()) {
			return;
		}
		if (!GunRolls.isRolled(stack)) {
			builder.accept(Component.translatable(key + "unrolled").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			return;
		}

		GunRarity rarity = GunRolls.rarity(stack);
		builder.accept(Component.translatable("rarity." + BlockSoulsGuns.MOD_ID + "." + rarity.id()).withStyle(switch (rarity) {
			case COMMON -> ChatFormatting.WHITE;
			case RARE -> ChatFormatting.AQUA;
			case LEGENDARY -> ChatFormatting.LIGHT_PURPLE;
		}));
		List<Perk> perks = GunRolls.perks(stack);
		for (Perk perk : perks) {
			String perkKey = "perk." + BlockSoulsGuns.MOD_ID + "." + perk.id();
			builder.accept(Component.literal("◆ ").append(Component.translatable(perkKey)).withStyle(ChatFormatting.GOLD));
			builder.accept(Component.literal("   ").append(Component.translatable(perkKey + ".desc")).withStyle(ChatFormatting.GRAY));
		}
	}

	private static String fmt(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 100) / 100.0);
	}
}
