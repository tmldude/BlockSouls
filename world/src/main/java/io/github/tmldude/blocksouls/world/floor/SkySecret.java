package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.world.content.WorldItems;

/**
 * Skyrift's secret (floor 3): take the glowing seed from the sky temple's pedestal and carry it to the camp altar
 * without taking a hit. Any damage (or falling off the island, or letting go of it) dims it and it goes back to its
 * pedestal. Planted, it gives everyone on the floor a legendary relic (a legendary boon); without the game module, a
 * roll of the floor secret chest instead.
 */
public final class SkySecret {
	/** The game's legendary relic, looked up by id (the world never imports game). */
	static final Identifier LEGENDARY_RELIC = Identifier.fromNamespaceAndPath("blocksouls_game", "relic_legendary");
	private static final int FLAGS = Block.UPDATE_ALL;
	/** Seeds that hit the ground this tick: sent home next tick (not while the level is adding the entity). */
	private static final List<ItemEntity> DROPPED = new ArrayList<>();

	private SkySecret() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (damage > 0 && entity instanceof ServerPlayer player) {
				dim(player);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && item.getItem().is(seedItem())) {
				DROPPED.add(item);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SkySecret::returnDropped);
	}

	static Item seedItem() {
		return WorldItems.GLOWING_SEED;
	}

	/** A seed that remembers the pedestal it came from. */
	public static ItemStack seedFrom(BlockPos pedestal) {
		ItemStack seed = new ItemStack(seedItem());
		CompoundTag tag = new CompoundTag();
		tag.putInt("px", pedestal.getX());
		tag.putInt("py", pedestal.getY());
		tag.putInt("pz", pedestal.getZ());
		seed.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return seed;
	}

	public static @Nullable BlockPos pedestalOf(ItemStack seed) {
		CustomData data = seed.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return tag.contains("px") ? new BlockPos(tag.getIntOr("px", 0), tag.getIntOr("py", 0), tag.getIntOr("pz", 0)) : null;
	}

	/** Using the pedestal: hands over its seed (once; it comes back if the carrier is hit). */
	public static void take(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		if (!state.getValue(SkyriftBlock.ACTIVE)) {
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.sky_seed.gone"));
			return;
		}
		level.setBlock(pos, state.setValue(SkyriftBlock.ACTIVE, false), FLAGS);
		ItemStack seed = seedFrom(pos);
		if (!player.getInventory().add(seed)) {
			player.spawnAtLocation(player.level(), seed);
		}
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.blocksouls_world.sky_seed.taken"));
	}

	/**
	 * The seed dims: every seed the player carries goes back to its pedestal. Called on any damage, from
	 * {@link WorldFloors#fellOff} (the fall penalty isn't a hit), and by the {@code /bs floor hazard} tests. Returns
	 * whether the player had one.
	 */
	public static boolean dim(ServerPlayer player) {
		boolean had = false;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(seedItem())) {
				sendHome(player.level().getServer(), stack);
				inventory.setItem(slot, ItemStack.EMPTY);
				had = true;
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (carried.is(seedItem())) {
			sendHome(player.level().getServer(), carried);
			player.containerMenu.setCarried(ItemStack.EMPTY);
			had = true;
		}
		if (had) {
			player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.sky_seed.dimmed"));
		}
		return had;
	}

	private static void returnDropped(MinecraftServer server) {
		if (DROPPED.isEmpty()) {
			return;
		}
		for (ItemEntity item : List.copyOf(DROPPED)) {
			if (!item.isRemoved()) {
				sendHome(server, item.getItem());
				item.discard();
			}
		}
		DROPPED.clear();
	}

	/** Relights the seed's pedestal (in the floors level, where every pedestal is). */
	private static void sendHome(MinecraftServer server, ItemStack seed) {
		BlockPos pedestal = pedestalOf(seed);
		ServerLevel level = server.getLevel(WorldFloors.levelKey());
		if (pedestal == null || level == null) {
			return;
		}
		BlockState state = level.getBlockState(pedestal);
		if (state.is(FloorBlocks.SEED_PEDESTAL)) {
			level.setBlock(pedestal, state.setValue(SkyriftBlock.ACTIVE, true), FLAGS);
			level.sendParticles(ParticleTypes.END_ROD, pedestal.getX() + 0.5, pedestal.getY() + 1.2, pedestal.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.02);
		}
	}

	/** The seed used on the camp altar: it takes root and everyone on the floor gets the reward. */
	public static InteractionResult plant(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack seed) {
		if (state.getValue(SkyriftBlock.ACTIVE)) {
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.sky_seed.rooted"));
			return InteractionResult.SUCCESS;
		}
		seed.shrink(1);
		level.setBlock(pos, state.setValue(SkyriftBlock.ACTIVE, true), FLAGS);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 40, 0.4, 1.0, 0.4, 0.05);
		level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
		for (ServerPlayer each : party(level, pos, player)) {
			reward(level, pos, each);
			each.sendSystemMessage(Component.translatable("message.blocksouls_world.sky_seed.planted", player.getDisplayName()));
		}
		return InteractionResult.SUCCESS;
	}

	/** Everyone standing on the same floor as the altar (just the planter off a floor). */
	private static List<ServerPlayer> party(ServerLevel level, BlockPos pos, ServerPlayer planter) {
		PlacedFloor floor = WorldFloors.floorNear(level, pos);
		if (floor == null) {
			return List.of(planter);
		}
		List<ServerPlayer> party = new ArrayList<>();
		for (ServerPlayer each : level.players()) {
			PlacedFloor theirs = WorldFloors.floorNear(level, each.blockPosition());
			if (each == planter || (theirs != null && theirs.camp().equals(floor.camp()))) {
				party.add(each);
			}
		}
		return party;
	}

	private static void reward(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Optional<Item> relic = BuiltInRegistries.ITEM.getOptional(LEGENDARY_RELIC);
		List<ItemStack> stacks = new ArrayList<>();
		if (relic.isPresent()) {
			stacks.add(new ItemStack(relic.get()));
		} else {
			LootTable table = level.getServer().reloadableRegistries().getLootTable(FloorBuilder.SECRET_LOOT);
			stacks.addAll(table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
					.withLuck(player.getLuck()).create(LootContextParamSets.CHEST)));
		}
		for (ItemStack stack : stacks) {
			if (!player.getInventory().add(stack)) {
				player.spawnAtLocation(player.level(), stack);
			}
		}
	}
}
