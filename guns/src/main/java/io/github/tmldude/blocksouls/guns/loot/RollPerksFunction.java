package io.github.tmldude.blocksouls.guns.loot;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunRolls;

/**
 * {@code blocksouls_guns:roll_perks}: rolls a gun's rarity and perks. Optional {@code tier}
 * ({@code common|rare|legendary}) picks the odds table; without it the crafted odds apply. Other items pass through.
 * {@code "tier": "exotic"} turns any gun entry into a random exotic (weights in {@code loot.exoticWeights}) with its
 * perk rolled, so a boss or chest table can drop exotics without naming them.
 */
public class RollPerksFunction extends LootItemConditionalFunction {
	private static final List<String> TIERS = List.of("common", "rare", "legendary", "exotic");
	private static final Codec<String> TIER_CODEC = Codec.STRING.validate(tier -> TIERS.contains(tier)
			? DataResult.success(tier)
			: DataResult.error(() -> "Unknown roll_perks tier '" + tier + "', expected one of " + TIERS));

	public static final MapCodec<RollPerksFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
			.and(TIER_CODEC.optionalFieldOf("tier").forGetter(f -> f.tier))
			.apply(i, RollPerksFunction::new));

	private final Optional<String> tier;

	private RollPerksFunction(Optional<Holder<LootItemCondition>> condition, Optional<String> tier) {
		super(condition);
		this.tier = tier;
	}

	public static Builder<?> roll(String tier) {
		return simpleBuilder(condition -> new RollPerksFunction(condition, Optional.ofNullable(tier)));
	}

	@Override
	protected ItemStack run(ItemStack stack, LootContext context) {
		if (tier.isPresent() && "exotic".equals(tier.get()) && stack.getItem() instanceof GunItem) {
			ItemStack base = stack;
			stack = GunLoot.randomExotic(context.getRandom()).map(exotic -> base.transmuteCopy(exotic, base.getCount())).orElse(stack);
		}
		GunRolls.roll(stack, GunRolls.odds(tier.orElse(null)), context.getRandom());
		return stack;
	}

	@Override
	public MapCodec<RollPerksFunction> codec() {
		return MAP_CODEC;
	}
}
