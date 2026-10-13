package io.github.tmldude.blocksouls.world.content;

import java.util.List;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.food.FoodProperties;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * Item properties for the Blighted Surface's makes (v2-floor5-blight). Ghostcap Draught is the run's only full heal
 * (Instant Health VI heals 128, more than any boosted max health) and purges poison, wither and the blight's
 * weakness; Ghostcap Broth is the cheap siege-night stew. The Hollow Crown is the cursed armor piece: tougher than a
 * blight-forged helm and it hits harder, but it costs 4 max health while worn.
 */
final class WorldConsumables {
	private WorldConsumables() {
	}

	static Item.Properties draught() {
		return new Item.Properties().stacksTo(16).rarity(Rarity.RARE).usingConvertsTo(Items.GLASS_BOTTLE)
				.food(new FoodProperties.Builder().nutrition(0).saturationModifier(0).alwaysEdible().build(), Consumables.defaultDrink()
						.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, 5)))
						.onConsume(new RemoveStatusEffectsConsumeEffect(MobEffects.POISON))
						.onConsume(new RemoveStatusEffectsConsumeEffect(MobEffects.WITHER))
						.onConsume(new RemoveStatusEffectsConsumeEffect(MobEffects.WEAKNESS))
						.build());
	}

	static Item.Properties broth() {
		return new Item.Properties().stacksTo(1).usingConvertsTo(Items.BOWL)
				.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).alwaysEdible().build(), Consumables.defaultFood()
						.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.REGENERATION, 200, 1),
								new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0))))
						.build());
	}

	static Item.Properties hollowCrown() {
		EquipmentSlotGroup head = EquipmentSlotGroup.HEAD;
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, modifier("hollow_crown_armor", 5), head)
				.add(Attributes.ARMOR_TOUGHNESS, modifier("hollow_crown_toughness", 3), head)
				.add(Attributes.ATTACK_DAMAGE, modifier("hollow_crown_damage", 2), head)
				.add(Attributes.MAX_HEALTH, modifier("hollow_crown_curse", -4), head)
				.build();
		return new Item.Properties().humanoidArmor(WorldArmor.BLIGHT_FORGED, ArmorType.HELMET).rarity(Rarity.RARE).fireResistant()
				.attributes(attributes);
	}

	private static AttributeModifier modifier(String name, double amount) {
		return new AttributeModifier(BlockSoulsWorld.id(name), amount, AttributeModifier.Operation.ADD_VALUE);
	}
}
