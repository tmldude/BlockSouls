package io.github.tmldude.blocksouls.game.run;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;

/**
 * The guarantees that make wave loot feel like a roguelike: a reward chest at every cleared wave's last kill (scaled by
 * wave and party), the night-2 pity gun, and elite drops that always land and glow.
 */
final class WaveRewards {
	private WaveRewards() {
	}

	private static RunConfig config() {
		return RunSystem.config();
	}

	// ---------------------------------------------------------------- reward chest

	/**
	 * A reward chest on a free spot near where the wave's last mob died (else near the last killer, else any player in the
	 * active dimension), filled with {@link RewardMath#bundleRolls} rolls of the reward table, plus the pity gun when owed.
	 * Never replaces blocks. Returns the chest's position, or null if there was no spot.
	 */
	static @Nullable BlockPos placeRewardChest(MinecraftServer server, RunData data, int wave, @Nullable UUID killerId, @Nullable BlockPos killPos) {
		List<ServerPlayer> players = WaveManager.activePlayers(server);
		ServerLevel level = RunSystem.activeLevel(server);
		BlockPos origin = killPos;
		if (origin == null) {
			ServerPlayer near = killerId == null ? null : server.getPlayerList().getPlayer(killerId);
			if (near == null || near.level() != level) {
				near = players.isEmpty() ? null : players.getFirst();
			}
			origin = near == null ? null : near.blockPosition();
		}
		if (origin == null) {
			return null;
		}
		BlockPos spot = findChestSpot(level, origin, Math.max(1, config().rewardSearchRadius));
		if (spot == null && killPos != null && !players.isEmpty()) {
			// The last kill was somewhere unbuildable (water, mid-air): fall back to a player.
			spot = findChestSpot(level, players.getFirst().blockPosition(), Math.max(1, config().rewardSearchRadius));
		}
		if (spot == null) {
			return null;
		}
		level.setBlockAndUpdate(spot, Blocks.CHEST.defaultBlockState());
		if (!(level.getBlockEntity(spot) instanceof ChestBlockEntity chest)) {
			return spot;
		}
		Vec3 center = Vec3.atCenterOf(spot);
		int rolls = RewardMath.bundleRolls(wave, players.size(), config());
		List<ItemStack> loot = new ArrayList<>();
		for (int i = 0; i < rolls; i++) {
			loot.addAll(roll(level, CommonIds.WAVE_CLEAR_REWARD, center));
		}
		boolean pity = false;
		if (RewardMath.gunPityDue(data.night, partyHasGun(players), data.gunPityGiven, config())) {
			List<ItemStack> guns = rollPityGuns(level, players.size(), center);
			if (!guns.isEmpty()) {
				// Pity guns go in first so a full chest never pushes them out.
				loot.addAll(0, guns);
				data.gunPityGiven = true;
				data.setDirty();
				pity = true;
			}
		}
		for (ItemStack stack : loot) {
			ItemStack left = insert(chest, stack);
			if (!left.isEmpty()) {
				level.addFreshEntity(new ItemEntity(level, center.x, center.y + 1, center.z, left));
			}
		}
		chest.setChanged();

		level.playSound(null, spot, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 0.8F);
		if (pity) {
			level.playSound(null, spot, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 0.8F, 1.2F);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, spot.getY() + 1, center.z, 20, 0.5, 0.5, 0.5, 0.1);
		level.sendParticles(ParticleTypes.END_ROD, center.x, spot.getY() + 1.2, center.z, 12 + 4 * rolls, 0.2, 0.6, 0.2, 0.05);
		return spot;
	}

	private static @Nullable BlockPos findChestSpot(ServerLevel level, BlockPos origin, int radius) {
		if (isFreeChestSpot(level, origin)) {
			return origin;
		}
		for (int r = 1; r <= radius; r++) {
			for (int dy = 0; dy <= 2; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						for (int sign : new int[] {0, -1, 1}) {
							BlockPos pos = origin.offset(dx, sign * dy, dz);
							if ((Math.abs(dx) == r || Math.abs(dz) == r) && isFreeChestSpot(level, pos)) {
								return pos;
							}
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isFreeChestSpot(ServerLevel level, BlockPos pos) {
		if (!level.isInsideBuildHeight(pos.getY()) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/** Merges into matching stacks first, then empty slots. Returns what didn't fit. */
	private static ItemStack insert(Container container, ItemStack stack) {
		for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
			ItemStack slot = container.getItem(i);
			if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
				int move = Math.min(stack.getCount(), Math.min(container.getMaxStackSize(), slot.getMaxStackSize()) - slot.getCount());
				if (move > 0) {
					slot.grow(move);
					stack.shrink(move);
				}
			}
		}
		for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, stack.copy());
				stack.setCount(0);
			}
		}
		return stack;
	}

	private static List<ItemStack> roll(ServerLevel level, ResourceKey<LootTable> key, Vec3 origin) {
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
		return new ArrayList<>(table.getRandomItems(params));
	}

	// ---------------------------------------------------------------- pity gun

	/** Whether anyone in the party carries a gun other than the Starter Pistol. */
	static boolean partyHasGun(List<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			if (player.getInventory().contains((ItemStack stack) -> stack.is(CommonTags.GUNS)
					&& !CommonIds.STARTER_PISTOL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())))) {
				return true;
			}
		}
		return false;
	}

	/** {@link RewardMath#pityGuns} rolls of the pity table (filled by guns; empty without it). */
	static List<ItemStack> rollPityGuns(ServerLevel level, int players, Vec3 origin) {
		List<ItemStack> guns = new ArrayList<>();
		int count = RewardMath.pityGuns(players, config());
		for (int i = 0; i < count; i++) {
			guns.addAll(roll(level, CommonIds.GUN_PITY, origin));
		}
		return guns;
	}

	/** At dawn: a pity gun still owed (no wave got cleared tonight) lands at the party's feet. */
	static void onDawn(MinecraftServer server, RunData data) {
		List<ServerPlayer> players = WaveManager.activePlayers(server);
		if (players.isEmpty() || !RewardMath.gunPityDue(data.night, partyHasGun(players), data.gunPityGiven, config())) {
			return;
		}
		ServerLevel level = RunSystem.activeLevel(server);
		List<ItemStack> guns = rollPityGuns(level, players.size(), players.getFirst().position());
		if (guns.isEmpty()) {
			return;
		}
		data.gunPityGiven = true;
		data.setDirty();
		for (int i = 0; i < guns.size(); i++) {
			ServerPlayer player = players.get(i % players.size());
			ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), guns.get(i));
			item.setGlowingTag(true);
			level.addFreshEntity(item);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.2F);
		}
	}

	// ---------------------------------------------------------------- elites

	/** Elites always drop: their tier table plus {@code eliteDropRolls} rolls of the elite table, glowing on the ground. */
	static void dropElite(ServerLevel level, Mob mob, DamageSource source, ResourceKey<LootTable> tierTable, boolean playerKilled) {
		boolean glow = config().eliteDropsGlow;
		List<ResourceKey<LootTable>> tables = new ArrayList<>();
		tables.add(tierTable);
		for (int i = 0; i < config().eliteDropRolls; i++) {
			tables.add(CommonIds.WAVE_DROPS_ELITE);
		}
		for (ResourceKey<LootTable> table : tables) {
			mob.dropFromLootTable(level, source, playerKilled, table, stack -> {
				ItemEntity item = mob.spawnAtLocation(level, stack);
				if (item != null && glow) {
					item.setGlowingTag(true);
				}
			});
		}
		level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 1, mob.getZ(), 24, 0.4, 0.8, 0.4, 0.08);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.5F, 0.7F);
	}
}
