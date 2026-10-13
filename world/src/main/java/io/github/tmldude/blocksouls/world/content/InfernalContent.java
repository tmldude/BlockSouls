package io.github.tmldude.blocksouls.world.content;

import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.floor.FloorsConfig;
import io.github.tmldude.blocksouls.world.floor.WorldFloors;

/**
 * Floor 2's items (v2-floor2-infernal): cinder from cinder ore nodes, the ashcap herb, the cinder plate chestplate (heat
 * armor: burning wears off faster), and the two ashcap brews, the Ashcap Tonic (fire resistance) and the Scorch Draught
 * (Scorching: your hits set the target burning). Durations are {@code floors.json} {@code infernal} fields.
 */
public final class InfernalContent {
	public static final TagKey<Item> REPAIRS_CINDER_PLATE = TagKey.create(Registries.ITEM, BlockSoulsWorld.id("repairs_cinder_plate_armor"));
	public static final ResourceKey<EquipmentAsset> CINDER_PLATE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, BlockSoulsWorld.id("cinder_plate"));
	/** Between scrap plate (iron) and blight-forged: iron's defense, more toughness, a hotter enchant pull. */
	public static final ArmorMaterial CINDER_PLATE = new ArmorMaterial(22, java.util.Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5,
			ArmorType.CHESTPLATE, 7, ArmorType.HELMET, 2, ArmorType.BODY, 7), 12, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.0F,
			REPAIRS_CINDER_PLATE, CINDER_PLATE_ASSET);

	public static final Holder<MobEffect> SCORCHING = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, BlockSoulsWorld.id("scorching"),
			new Effect(MobEffectCategory.BENEFICIAL, 0xE8641E));

	public static final Item CINDER = WorldItems.item("cinder", Item::new, new Item.Properties().fireResistant());
	public static final Item ASHCAP = WorldItems.item("ashcap", Item::new, new Item.Properties().fireResistant());
	public static final Item CINDER_PLATE_CHESTPLATE = WorldItems.item("cinder_plate_chestplate", Item::new, new Item.Properties()
			.humanoidArmor(CINDER_PLATE, ArmorType.CHESTPLATE).fireResistant()
			.attributes(CINDER_PLATE.createAttributes(ArmorType.CHESTPLATE).withModifierAdded(Attributes.BURNING_TIME,
					new AttributeModifier(BlockSoulsWorld.id("cinder_plate_burn_time"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
					EquipmentSlotGroup.CHEST)));
	public static final Item ASHCAP_TONIC = WorldItems.item("ashcap_tonic",
			properties -> new Brew(properties, () -> new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * Math.max(1, infernal().tonicSeconds))),
			brew().rarity(Rarity.UNCOMMON));
	public static final Item SCORCH_DRAUGHT = WorldItems.item("scorch_draught",
			properties -> new Brew(properties, () -> new MobEffectInstance(SCORCHING, 20 * Math.max(1, infernal().scorchSeconds))),
			brew().rarity(Rarity.UNCOMMON));

	private InfernalContent() {
	}

	/** Forces class loading (registration) and hooks Scorching into damage. */
	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> scorch(entity, source, blocked));
	}

	private static FloorsConfig.Infernal infernal() {
		return WorldFloors.config().infernal;
	}

	private static Item.Properties brew() {
		return new Item.Properties().stacksTo(16).component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build())
				.usingConvertsTo(Items.GLASS_BOTTLE);
	}

	/** A Scorching attacker (melee, or a gun's bullet credited to them) sets what they hit burning. */
	private static void scorch(LivingEntity target, DamageSource source, boolean blocked) {
		if (blocked || target.isDeadOrDying() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == target) {
			return;
		}
		MobEffectInstance scorching = attacker.getEffect(SCORCHING);
		if (scorching != null) {
			target.igniteForSeconds(infernal().scorchIgniteSeconds * (scorching.getAmplifier() + 1));
		}
	}

	/** A drink that applies its effect, read from the config when drunk. */
	private static final class Brew extends Item {
		private final Supplier<MobEffectInstance> effect;

		Brew(Properties properties, Supplier<MobEffectInstance> effect) {
			super(properties);
			this.effect = effect;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (!level.isClientSide()) {
				entity.addEffect(effect.get());
			}
			return super.finishUsingItem(stack, level, entity);
		}
	}

	private static final class Effect extends MobEffect {
		Effect(MobEffectCategory category, int color) {
			super(category, color);
		}
	}
}
