package io.github.tmldude.blocksouls.bosses.palerider;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The Wraith Steed and its whistle: registration, and which steed belongs to whom. One steed per player: blowing the
 * whistle summons it (and puts you in the saddle), blowing it again while it's out recalls it to you.
 */
public final class WraithSteeds {
	public static EntityType<WraithSteed> TYPE;
	public static Item WHISTLE;

	/** Owner → their current steed (server thread only). */
	private static final Map<UUID, UUID> STEEDS = new HashMap<>();

	private WraithSteeds() {
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, CommonIds.id(CommonIds.BOSSES, "wraith_steed"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				FabricEntityType.Builder.createMob(WraithSteed::new, MobCategory.CREATURE, mob -> mob.defaultAttributes(WraithSteed::createAttributes))
						.sized(1.3964844f, 1.6f)
						.eyeHeight(1.52f)
						.passengerAttachments(1.35f)
						.clientTrackingRange(10)
						.build(key));
		ResourceKey<Item> whistleKey = ResourceKey.create(Registries.ITEM, CommonIds.id(CommonIds.BOSSES, "wraith_steed_whistle"));
		WHISTLE = Registry.register(BuiltInRegistries.ITEM, whistleKey,
				new WraithWhistleItem(new Item.Properties().setId(whistleKey).stacksTo(1).rarity(Rarity.EPIC)));
		ResourceKey<CreativeModeTab> tools = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("tools_and_utilities"));
		CreativeModeTabEvents.modifyOutputEvent(tools).register(output -> output.insertAfter(Items.SADDLE, WHISTLE));
	}

	/** A steed telling the registry it exists (every tick); the newest one per owner wins, older ones fade. */
	static void claim(WraithSteed steed) {
		UUID owner = steed.ownerId();
		if (owner == null) {
			steed.discard();
			return;
		}
		UUID current = STEEDS.get(owner);
		if (current == null || current.equals(steed.getUUID())) {
			STEEDS.put(owner, steed.getUUID());
			return;
		}
		WraithSteed other = find(steed.level().getServer(), current);
		if (other == null || !other.isAlive()) {
			STEEDS.put(owner, steed.getUUID());
		} else if (steed.level() instanceof ServerLevel level) {
			steed.fade(level);
		}
	}

	private static @Nullable WraithSteed find(@Nullable MinecraftServer server, UUID id) {
		if (server == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(id);
			if (entity instanceof WraithSteed steed) {
				return steed;
			}
		}
		return null;
	}

	/** {@code player}'s steed if it's out (any dimension), else null. */
	public static @Nullable WraithSteed of(ServerPlayer player) {
		if (player.getVehicle() instanceof WraithSteed riding && riding.isOwnedBy(player)) {
			return riding;
		}
		UUID id = STEEDS.get(player.getUUID());
		WraithSteed steed = id == null ? null : find(player.level().getServer(), id);
		if (steed == null || !steed.isAlive() || !steed.isOwnedBy(player)) {
			STEEDS.remove(player.getUUID());
			return null;
		}
		return steed;
	}

	/**
	 * The whistle: summons {@code player}'s steed at their side and mounts them, or recalls the one that's out. Returns
	 * the steed, or null if it couldn't appear (e.g. the player is riding something else).
	 */
	public static @Nullable WraithSteed whistle(ServerPlayer player) {
		ServerLevel level = player.level();
		WraithSteed steed = of(player);
		if (steed != null && steed.hasPassenger(player)) {
			return steed;
		}
		if (steed != null && steed.level() != level) {
			// Left in another dimension: let it fade there and call a fresh one.
			steed.discard();
			steed = null;
		}
		if (player.isPassenger()) {
			return null;
		}
		if (steed == null) {
			steed = TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (steed == null) {
				return null;
			}
			steed.bind(player);
			steed.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
			level.addFreshEntity(steed);
			STEEDS.put(player.getUUID(), steed.getUUID());
		} else {
			if (steed.isVehicle()) {
				steed.ejectPassengers();
			}
			level.sendParticles(ParticleTypes.CLOUD, steed.getX(), steed.getY() + 1, steed.getZ(), 15, 0.5, 0.5, 0.5, 0.02);
			steed.teleportTo(player.getX(), player.getY(), player.getZ());
			steed.setYRot(player.getYRot());
		}
		level.sendParticles(ParticleTypes.CLOUD, steed.getX(), steed.getY() + 0.8, steed.getZ(), 25, 0.7, 0.5, 0.7, 0.03);
		level.sendParticles(ParticleTypes.SOUL, steed.getX(), steed.getY() + 1, steed.getZ(), 10, 0.5, 0.5, 0.5, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), PaleRiderSounds.STEED_SUMMON, SoundSource.PLAYERS, 1.5f, 1.0f);
		player.startRiding(steed);
		return steed;
	}

	/** Server stopped: forget everything. */
	static void clear() {
		STEEDS.clear();
	}
}
