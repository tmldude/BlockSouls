package io.github.tmldude.blocksouls.bosses.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

/**
 * Registry of boss entity types by boss id (debug commands, spawn eggs, tests). Register your boss's type with
 * {@link #register} in your initializer; the id is the boss's {@code CommonIds} constant and becomes the entity id.
 */
public final class Bosses {
	private static final Map<Identifier, EntityType<? extends BossEntity>> TYPES = new LinkedHashMap<>();
	private static final Map<EntityType<?>, Item> SPAWN_EGGS = new LinkedHashMap<>();

	private Bosses() {
	}

	/** Shape of a boss's hitbox. */
	public record Size(float width, float height, float eyeHeight) {
	}

	/**
	 * Registers {@code blocksouls_bosses:<path>} as a boss entity type plus its spawn egg
	 * ({@code <path>_spawn_egg}, needs an item model + texture + lang entry).
	 *
	 * @param fireImmune bosses that live in the Nether should be
	 */
	public static <T extends BossEntity> EntityType<T> register(Identifier id, EntityType.EntityFactory<T> factory, Size size,
			boolean fireImmune, Supplier<AttributeSupplier.Builder> attributes) {
		EntityType.Builder<T> builder = FabricEntityType.Builder.createMob(factory, MobCategory.MONSTER, mob -> mob.defaultAttributes(attributes))
				.sized(size.width(), size.height())
				.eyeHeight(size.eyeHeight())
				.clientTrackingRange(16)
				.updateInterval(2);
		if (fireImmune) {
			builder = builder.fireImmune();
		}
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		if (TYPES.putIfAbsent(id, type) != null) {
			throw new IllegalArgumentException("Boss already registered: " + id);
		}
		ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spawn_egg"));
		SPAWN_EGGS.put(type, Registry.register(BuiltInRegistries.ITEM, eggKey, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(eggKey))));
		return type;
	}

	/**
	 * Base attributes every boss shares: knockback-immune, steps up full blocks, sees its whole arena.
	 *
	 * @param health base max health (scaled per fight by the framework)
	 */
	public static AttributeSupplier.Builder attributes(double health, double armor, double speed, double attackDamage) {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, health)
				.add(Attributes.ARMOR, armor)
				.add(Attributes.MOVEMENT_SPEED, speed)
				.add(Attributes.ATTACK_DAMAGE, attackDamage)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.FOLLOW_RANGE, 64);
	}

	public static Optional<EntityType<? extends BossEntity>> type(Identifier id) {
		return Optional.ofNullable(TYPES.get(id));
	}

	public static Map<Identifier, EntityType<? extends BossEntity>> all() {
		return Collections.unmodifiableMap(TYPES);
	}

	/** Puts the boss spawn eggs after the mobs' eggs in the Spawn Eggs tab. Called once by the module. */
	public static void init() {
		ResourceKey<CreativeModeTab> spawnEggs = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("spawn_eggs"));
		CreativeModeTabEvents.modifyOutputEvent(spawnEggs).register(output ->
				output.insertAfter(Items.ZOMBIFIED_PIGLIN_SPAWN_EGG, SPAWN_EGGS.values().toArray(Item[]::new)));
	}
}
