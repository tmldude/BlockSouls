package io.github.tmldude.blocksouls.bosses.stormplate;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Stormplate Knight's drop: a netherite-tier set, not craftable, repaired with netherite ingots. Four pieces:
 * immune to lightning, +2 knockback resistance, and the wearer's kills call a small lightning strike on the next
 * enemy within range (harmless to the wearer, on a cooldown).
 */
public final class DragonscaleArmor {
	public static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, CommonIds.id(CommonIds.BOSSES, "repairs_dragonscale_armor"));
	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, CommonIds.id(CommonIds.BOSSES, "dragonscale"));
	/** Netherite numbers (durability 37, 3/6/8/3, toughness 3, 0.1 knockback), its own enchantability. */
	public static final ArmorMaterial MATERIAL = new ArmorMaterial(37, defense(3, 6, 8, 3), 15, SoundEvents.ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F,
			REPAIRS, ASSET);

	public static Item HELMET;
	public static Item CHESTPLATE;
	public static Item LEGGINGS;
	public static Item BOOTS;

	private static final Identifier KNOCKBACK_ID = CommonIds.id(CommonIds.BOSSES, "dragonscale_set");
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Player → game time when the chain lightning is ready again. */
	private static final Map<UUID, Long> CHAIN_READY = new HashMap<>();

	private DragonscaleArmor() {
	}

	static void register() {
		HELMET = item("dragonscale_helmet", ArmorType.HELMET);
		CHESTPLATE = item("dragonscale_chestplate", ArmorType.CHESTPLATE);
		LEGGINGS = item("dragonscale_leggings", ArmorType.LEGGINGS);
		BOOTS = item("dragonscale_boots", ArmorType.BOOTS);
		ResourceKey<CreativeModeTab> combat = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));
		CreativeModeTabEvents.modifyOutputEvent(combat).register(output ->
				output.insertAfter(Items.NETHERITE_BOOTS, HELMET, CHESTPLATE, LEGGINGS, BOOTS));

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.is(DamageTypes.LIGHTNING_BOLT) && wearsFullSet(entity)) {
				// A real bolt sets you alight before it hurts; the set shrugs off both.
				entity.clearFire();
				return false;
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(DragonscaleArmor::onKill);
		ServerTickEvents.END_SERVER_TICK.register(DragonscaleArmor::tick);
	}

	private static Item item(String name, ArmorType type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CommonIds.id(CommonIds.BOSSES, name));
		Item.Properties properties = new Item.Properties().humanoidArmor(MATERIAL, type).fireResistant().setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
	}

	private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helmet) {
		Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
		map.put(ArmorType.BOOTS, boots);
		map.put(ArmorType.LEGGINGS, legs);
		map.put(ArmorType.CHESTPLATE, chest);
		map.put(ArmorType.HELMET, helmet);
		map.put(ArmorType.BODY, chest);
		return map;
	}

	public static boolean isPiece(ItemStack stack) {
		return stack.is(HELMET) || stack.is(CHESTPLATE) || stack.is(LEGGINGS) || stack.is(BOOTS);
	}

	public static boolean wearsFullSet(LivingEntity entity) {
		Item[] pieces = {HELMET, CHESTPLATE, LEGGINGS, BOOTS};
		for (int i = 0; i < SLOTS.length; i++) {
			if (!entity.getItemBySlot(SLOTS[i]).is(pieces[i])) {
				return false;
			}
		}
		return true;
	}

	private static StormplateConfig config() {
		return StormplateKnights.CONFIG;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyPassives(player);
		}
	}

	/** Keeps the knockback bonus in sync with what the entity wears. */
	public static void applyPassives(LivingEntity entity) {
		AttributeInstance knockback = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback == null) {
			return;
		}
		boolean full = wearsFullSet(entity);
		if (full && !knockback.hasModifier(KNOCKBACK_ID)) {
			knockback.addTransientModifier(new AttributeModifier(KNOCKBACK_ID, config().setKnockbackResistance, AttributeModifier.Operation.ADD_VALUE));
		} else if (!full && knockback.hasModifier(KNOCKBACK_ID)) {
			knockback.removeModifier(KNOCKBACK_ID);
		}
	}

	/** A kill by a full-set wearer (melee, bow or gun: anything they're credited with) calls lightning on the next enemy. */
	private static void onKill(LivingEntity dead, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || player == dead || !(dead.level() instanceof ServerLevel level)
				|| !wearsFullSet(player)) {
			return;
		}
		long now = level.getGameTime();
		if (CHAIN_READY.getOrDefault(player.getUUID(), 0L) > now) {
			return;
		}
		LivingEntity next = nextEnemy(level, player, dead);
		if (next == null) {
			return;
		}
		CHAIN_READY.put(player.getUUID(), now + config().setChainCooldownTicks);
		strike(level, player, next);
	}

	/** The nearest living enemy within range of the one that just died (not the wearer, never another player). */
	static @Nullable LivingEntity nextEnemy(ServerLevel level, ServerPlayer player, LivingEntity dead) {
		double range = config().setChainRange;
		AABB box = dead.getBoundingBox().inflate(range);
		LivingEntity best = null;
		double bestDist = range * range;
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != dead && e != player && e.isAlive() && e instanceof Enemy && !e.isSpectator())) {
			double dist = candidate.distanceToSqr(dead);
			if (dist <= bestDist) {
				bestDist = dist;
				best = candidate;
			}
		}
		return best;
	}

	/** A visual bolt (no fire, no stray damage) plus the strike's own damage, credited to the wearer. */
	static void strike(ServerLevel level, ServerPlayer player, LivingEntity target) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(target.getX(), target.getY(), target.getZ());
			bolt.setVisualOnly(true);
			bolt.setSilent(true);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f, 1.4f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 20, 0.3, 0.5, 0.3,
				0.2);
		target.hurtServer(level, level.damageSources().playerAttack(player), (float) config().setChainDamage);
	}
}
