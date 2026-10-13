package io.github.tmldude.blocksouls.world.floor;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.warden.Warden;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Vault's (floor 4) two mini-bosses, in the Director's {@code vault} mini-boss pool: the Listener, a blind hunter
 * ({@link VaultListener}, warden-bodied), and the Colossus ({@link VaultColossus}, golem-bodied). One more of the two
 * waits in the chamber over the secret room ({@code VaultHazards}).
 */
public final class VaultMobs {
	public static final EntityType<VaultListener> VAULT_LISTENER = register(CommonIds.VAULT_LISTENER,
			FabricEntityType.Builder.<VaultListener>createMob(VaultListener::new, MobCategory.MONSTER, mob -> mob.defaultAttributes(Warden::createAttributes))
					.sized(0.9F, 2.9F).attach(EntityAttachment.WARDEN_CHEST, 0.0F, 1.6F, 0.0F).clientTrackingRange(16).fireImmune());
	public static final EntityType<VaultColossus> VAULT_COLOSSUS = register(CommonIds.VAULT_COLOSSUS,
			FabricEntityType.Builder.<VaultColossus>createMob(VaultColossus::new, MobCategory.MONSTER, mob -> mob.defaultAttributes(IronGolem::createAttributes))
					.sized(1.4F, 2.7F).clientTrackingRange(12));

	private VaultMobs() {
	}

	private static <T extends Entity> EntityType<T> register(Identifier id, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** Loads the class (the types register on load). */
	static void init() {
	}
}
