package io.github.tmldude.blocksouls.world.armor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.WorldItems;

/**
 * Four-piece set bonuses. Scrap-plate: less explosion damage (see {@code LivingEntityMixin}) and knockback
 * resistance. Blight-forged: fire immunity and attackers take damage.
 */
public final class SetBonuses {
	public enum ArmorSet {
		SCRAP_PLATE, BLIGHT_FORGED
	}

	private static final Identifier SCRAP_KNOCKBACK = BlockSoulsWorld.id("scrap_plate_set");
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private SetBonuses() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SetBonuses::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(source.is(DamageTypeTags.IS_FIRE) && wearsFullSet(entity, ArmorSet.BLIGHT_FORGED)));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> thorns(entity, source));
	}

	public static boolean wearsFullSet(LivingEntity entity, ArmorSet set) {
		Item[] pieces = pieces(set);
		for (int i = 0; i < SLOTS.length; i++) {
			if (!entity.getItemBySlot(SLOTS[i]).is(pieces[i])) {
				return false;
			}
		}
		return true;
	}

	private static Item[] pieces(ArmorSet set) {
		return switch (set) {
			case SCRAP_PLATE -> new Item[] {WorldItems.SCRAP_PLATE_HELMET, WorldItems.SCRAP_PLATE_CHESTPLATE,
					WorldItems.SCRAP_PLATE_LEGGINGS, WorldItems.SCRAP_PLATE_BOOTS};
			case BLIGHT_FORGED -> new Item[] {WorldItems.BLIGHT_FORGED_HELMET, WorldItems.BLIGHT_FORGED_CHESTPLATE,
					WorldItems.BLIGHT_FORGED_LEGGINGS, WorldItems.BLIGHT_FORGED_BOOTS};
		};
	}

	/** Damage after the scrap-plate explosion reduction (applied to the incoming amount). */
	public static float modifyIncoming(LivingEntity entity, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.IS_EXPLOSION) && wearsFullSet(entity, ArmorSet.SCRAP_PLATE)) {
			return damage * (1.0F - BlockSoulsWorld.CONFIG.armor.scrapExplosionReduction);
		}
		return damage;
	}

	private static void thorns(LivingEntity wearer, DamageSource source) {
		// Thorns never answers thorns, so two blight-forged fighters can't loop.
		if (source.is(DamageTypes.THORNS) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == wearer) {
			return;
		}
		if (wearer.level() instanceof ServerLevel level && wearsFullSet(wearer, ArmorSet.BLIGHT_FORGED)) {
			attacker.hurtServer(level, level.damageSources().thorns(wearer), BlockSoulsWorld.CONFIG.armor.blightThornsDamage);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyPassives(player);
		}
	}

	/** Keeps the passive (attribute / status) parts of the bonuses in sync with what the entity wears. */
	public static void applyPassives(LivingEntity entity) {
		AttributeInstance knockback = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null) {
			boolean scrap = wearsFullSet(entity, ArmorSet.SCRAP_PLATE);
			if (scrap && !knockback.hasModifier(SCRAP_KNOCKBACK)) {
				knockback.addTransientModifier(new AttributeModifier(SCRAP_KNOCKBACK,
						BlockSoulsWorld.CONFIG.armor.scrapKnockbackResistance, AttributeModifier.Operation.ADD_VALUE));
			} else if (!scrap && knockback.hasModifier(SCRAP_KNOCKBACK)) {
				knockback.removeModifier(SCRAP_KNOCKBACK);
			}
		}
		if (entity.isOnFire() && wearsFullSet(entity, ArmorSet.BLIGHT_FORGED)) {
			entity.clearFire();
		}
	}
}
