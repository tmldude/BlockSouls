package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.event.DirectorEvents;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.WorldItems;

/**
 * Skyrift's Director events (floor 3), placed through {@link DirectorEvents#PLACE_EVENT} when the Director rolls one
 * of the floor-tagged ids in its pool: the wind altar (gamble a relic), the falling-shard chest (a timed grab) and
 * the bridge ambush (a cache on a bridge; the game arms the ambush). The world builds them; nothing here is saved,
 * like the Director's own event state.
 */
public final class SkyEvents {
	public static final Identifier WIND_ALTAR = BlockSoulsWorld.id("wind_altar");
	public static final Identifier FALLING_SHARD_CHEST = BlockSoulsWorld.id("falling_shard_chest");
	public static final Identifier BRIDGE_AMBUSH = BlockSoulsWorld.id("bridge_ambush");
	/** The relic ladder the wind altar climbs, by item id (the world never imports game). */
	static final List<Identifier> RELICS = List.of(Identifier.fromNamespaceAndPath("blocksouls_game", "relic_common"),
			Identifier.fromNamespaceAndPath("blocksouls_game", "relic_rare"), SkySecret.LEGENDARY_RELIC);
	private static final int FLAGS = Block.UPDATE_CLIENTS;

	/** A shard waiting to fall: its blocks, its chest, and the server tick it goes. */
	record FallingShard(ResourceKey<Level> dimension, List<BlockPos> shard, List<BlockPos> scaffold, BlockPos chest, long fallsAt) {
	}

	static final List<FallingShard> SHARDS = new ArrayList<>();

	private SkyEvents() {
	}

	static void init() {
		DirectorEvents.PLACE_EVENT.register((level, event, pos, floor) -> place(level, event, pos));
		ServerTickEvents.END_SERVER_TICK.register(SkyEvents::tickShards);
	}

	/** Builds one of Skyrift's events at {@code pos} (the air over the event spot); false for any other id. */
	public static boolean place(ServerLevel level, Identifier event, BlockPos pos) {
		if (event.equals(WIND_ALTAR)) {
			level.setBlock(pos, FloorBlocks.WIND_ALTAR.defaultBlockState(), Block.UPDATE_ALL);
			return true;
		}
		if (event.equals(FALLING_SHARD_CHEST)) {
			fallingShard(level, pos);
			return true;
		}
		if (event.equals(BRIDGE_AMBUSH)) {
			bridge(level, pos);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- wind altar

	/**
	 * The wind altar, once: offer a held common or rare relic. A win ({@code windAltarWinChance}) gives it back one tier
	 * up plus updraft tonics; a loss and the wind throws you (with slow falling, so it's a scare rather than a death).
	 */
	static InteractionResult useWindAltar(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack held) {
		if (!state.getValue(SkyriftBlock.ACTIVE)) {
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.wind_altar.spent"));
			return InteractionResult.SUCCESS;
		}
		int tier = held.isEmpty() ? -1 : RELICS.indexOf(BuiltInRegistries.ITEM.getKey(held.getItem()));
		if (tier < 0 || tier >= RELICS.size() - 1) {
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.wind_altar.hint"));
			return InteractionResult.SUCCESS;
		}
		held.shrink(1);
		level.setBlock(pos, state.setValue(SkyriftBlock.ACTIVE, false), Block.UPDATE_ALL);
		FloorsConfig config = WorldFloors.config();
		if (level.getRandom().nextDouble() < config.windAltarWinChance) {
			Optional<Item> better = BuiltInRegistries.ITEM.getOptional(RELICS.get(tier + 1));
			better.ifPresent(item -> give(player, new ItemStack(item)));
			give(player, new ItemStack(WorldItems.UPDRAFT_TONIC, Math.max(0, config.windAltarTonics)));
			level.playSound(null, pos, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, 1.0F, 1.3F);
			player.sendSystemMessage(Component.translatable("message.blocksouls_world.wind_altar.won"));
		} else {
			double dx = player.getX() - (pos.getX() + 0.5);
			double dz = player.getZ() - (pos.getZ() + 0.5);
			double d = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
			player.push(dx / d * 1.6, 1.1, dz / d * 1.6);
			player.needsSync = true;
			level.playSound(null, pos, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
			player.sendSystemMessage(Component.translatable("message.blocksouls_world.wind_altar.lost"));
		}
		level.sendParticles(ParticleTypes.GUST, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.0);
		return InteractionResult.SUCCESS;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.spawnAtLocation(player.level(), stack);
		}
	}

	// ---------------------------------------------------------------- falling-shard chest

	/**
	 * A small shard {@code fallingShardRise} blocks over the spot with a crate chest on it and a ladder up a calcite
	 * pole. After {@code fallingShardSeconds} the shard falls and the chest (and whatever is still in it) is gone.
	 */
	static void fallingShard(ServerLevel level, BlockPos pos) {
		FloorsConfig config = WorldFloors.config();
		int rise = Math.max(3, config.fallingShardRise);
		BlockPos centre = pos.above(rise);
		List<BlockPos> shard = new ArrayList<>();
		List<BlockPos> scaffold = new ArrayList<>();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (x * x + z * z <= 5 && !(x == 2 && z == 0)) {
					BlockPos at = centre.offset(x, 0, z);
					level.setBlock(at, Blocks.CALCITE.defaultBlockState(), FLAGS);
					shard.add(at);
				}
			}
		}
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
		for (int y = 0; y <= rise; y++) {
			BlockPos pole = pos.offset(3, y, 0);
			level.setBlock(pole, Blocks.CALCITE.defaultBlockState(), FLAGS);
			level.setBlock(pole.west(), ladder, FLAGS);
			scaffold.add(pole.west());
			scaffold.add(pole);
		}
		BlockPos chest = centre.above();
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), FLAGS);
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, FloorBuilder.CRATE_LOOT);
		level.playSound(null, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.8F);
		SHARDS.add(new FallingShard(level.dimension(), shard, scaffold, chest,
				level.getServer().getTickCount() + Math.max(1, config.fallingShardSeconds) * 20L));
	}

	private static void tickShards(MinecraftServer server) {
		if (SHARDS.isEmpty()) {
			return;
		}
		SHARDS.removeIf(shard -> {
			if (server.getTickCount() < shard.fallsAt()) {
				return false;
			}
			ServerLevel level = server.getLevel(shard.dimension());
			if (level != null) {
				collapse(level, shard);
			}
			return true;
		});
	}

	/** The shard drops: the chest's contents and the chest go, the ladder goes, the shard's blocks fall. */
	static void collapse(ServerLevel level, FallingShard shard) {
		if (level.getBlockEntity(shard.chest()) instanceof Container container) {
			container.clearContent();
		}
		level.setBlock(shard.chest(), Blocks.AIR.defaultBlockState(), FLAGS);
		for (BlockPos at : shard.scaffold()) {
			level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
		}
		for (BlockPos at : shard.shard()) {
			BlockState state = level.getBlockState(at);
			if (state.is(Blocks.CALCITE)) {
				FallingBlockEntity.fall(level, at, state);
			}
		}
		level.playSound(null, shard.chest(), SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.BLOCKS, 1.5F, 0.6F);
		level.sendParticles(ParticleTypes.CLOUD, shard.chest().getX() + 0.5, shard.chest().getY(), shard.chest().getZ() + 0.5, 30, 1.5, 0.5, 1.5,
				0.05);
	}

	// ---------------------------------------------------------------- bridge ambush

	/**
	 * A two-wide plank bridge five blocks up between two stone-brick pillars, a ladder up the west pillar and a crate
	 * chest in the middle. The ambush itself is the game's (the event's spec has {@code ambush}).
	 */
	static void bridge(ServerLevel level, BlockPos pos) {
		int high = 5;
		BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
		for (int y = -1; y <= high; y++) {
			for (int z = 0; z <= 1; z++) {
				level.setBlock(pos.offset(-5, y, z), bricks, FLAGS);
				level.setBlock(pos.offset(5, y, z), bricks, FLAGS);
			}
		}
		for (int x = -4; x <= 4; x++) {
			for (int z = 0; z <= 1; z++) {
				level.setBlock(pos.offset(x, high, z), planks, FLAGS);
			}
			level.setBlock(pos.offset(x, high + 1, -1), Blocks.SPRUCE_FENCE.defaultBlockState(), Block.UPDATE_ALL);
		}
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
		for (int y = 0; y <= high; y++) {
			level.setBlock(pos.offset(-6, y, 0), ladder, FLAGS);
		}
		BlockPos chest = pos.offset(0, high + 1, 1);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), FLAGS);
		RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), chest, FloorBuilder.CRATE_LOOT);
	}
}
