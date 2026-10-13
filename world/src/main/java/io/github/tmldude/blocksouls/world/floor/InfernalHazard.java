package io.github.tmldude.blocksouls.world.floor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.event.ActEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;

/**
 * Floor 2's hazard (v2-floor2-infernal). Lava is the death plane: a player in it without fire resistance goes back to the
 * camp with the fall penalty. And {@code riseSeconds} into the portal-charge siege the lava rises {@code riseBlocks},
 * sinking the low ring bridges so the camp is reachable only over the stone arch; a few seconds of rumble and bursting
 * lava announce it, no text. The rise is live state only (a restart mid-siege just doesn't rise).
 */
public final class InfernalHazard {
	/** The floor tag that marks an Infernal floor. */
	public static final String TAG = "infernal";
	private static final int FLAGS = Block.UPDATE_ALL;

	/** Act ordinal → server tick the lava rises at. */
	private static final Map<Integer, Long> pending = new HashMap<>();

	private InfernalHazard() {
	}

	public static void init() {
		ActEvents.PORTAL_CHARGE_STARTED.register((server, act) -> Floors.forAct(server, act.ordinal()).ifPresent(floor -> schedule(server, floor)));
		ActEvents.PORTAL_CHARGE_COMPLETED.register((server, act) -> pending.remove(act.ordinal()));
		RunEvents.RUN_STARTED.register((server, state) -> pending.clear());
		ServerTickEvents.END_SERVER_TICK.register(InfernalHazard::tick);
	}

	/** Starts the siege clock on an Infernal floor (no-op on other floors). */
	public static boolean schedule(MinecraftServer server, PlacedFloor floor) {
		if (!floor.tags().contains(TAG)) {
			return false;
		}
		pending.put(floor.actOrdinal(), server.getTickCount() + Math.max(0, WorldFloors.config().infernal.riseSeconds) * 20L);
		return true;
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (!pending.isEmpty()) {
			int warnTicks = Math.max(0, WorldFloors.config().infernal.warnSeconds) * 20;
			for (Map.Entry<Integer, Long> entry : List.copyOf(pending.entrySet())) {
				long left = entry.getValue() - now;
				if (left <= 0) {
					pending.remove(entry.getKey());
					Floors.forAct(server, entry.getKey()).ifPresent(floor -> rise(server, floor));
				} else if (left <= warnTicks && left % 10 == 0) {
					Floors.forAct(server, entry.getKey()).ifPresent(floor -> warn(server, floor));
				}
			}
		}
		if (now % 10 == 0 && WorldFloors.config().infernal.lavaSendsBack) {
			lavaContact(server);
		}
	}

	// ---------------------------------------------------------------- lava contact

	private static void lavaContact(MinecraftServer server) {
		for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
			checkLava(player);
		}
	}

	/**
	 * Sends a player standing in an Infernal floor's lava without fire resistance back to the camp (the fall penalty
	 * applies). Returns whether they were sent back.
	 */
	public static boolean checkLava(ServerPlayer player) {
		if (player.isSpectator() || player.isCreative() || player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
			return false;
		}
		boolean inLava = player.isInLava() || player.level().getFluidState(player.blockPosition()).is(FluidTags.LAVA);
		if (!inLava) {
			return false;
		}
		Optional<PlacedFloor> floor = Floors.at(player.level().getServer(), player.level().dimension().identifier(), player.blockPosition())
				.filter(placed -> placed.tags().contains(TAG));
		if (floor.isEmpty()) {
			return false;
		}
		WorldFloors.fellOff(player, floor.get());
		player.clearFire();
		player.sendOverlayMessage(Component.translatable("message.blocksouls_world.floor.burned"));
		return true;
	}

	// ---------------------------------------------------------------- the rise

	private static @Nullable ServerLevel level(MinecraftServer server, PlacedFloor floor) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, floor.dimension()));
	}

	private static LavaChannels.@Nullable Plan channels(PlacedFloor floor) {
		FloorTemplate template = FloorTemplateCatalog.get(floor.template().toString());
		return template == null ? null : FloorPlanner.channels(template, floor.radius(), floor.seed());
	}

	/** The lava surface's y on this floor before any rise. */
	public static int lavaY(PlacedFloor floor) {
		FloorTemplate template = FloorTemplateCatalog.get(floor.template().toString());
		return template == null || template.channels == null ? floor.groundY() : InfernalFeatures.lavaY(floor.groundY(), template.channels);
	}

	/** The rumble before the rise: lava bursting and smoke over the channels, and a deep pop at every player. */
	public static void warn(MinecraftServer server, PlacedFloor floor) {
		ServerLevel level = level(server, floor);
		LavaChannels.Plan channels = channels(floor);
		if (level == null || channels == null) {
			return;
		}
		int y = lavaY(floor) + 1;
		int radius = floor.radius();
		for (int i = 0; i < 40; i++) {
			int x = level.getRandom().nextInt(2 * radius + 1) - radius;
			int z = level.getRandom().nextInt(2 * radius + 1) - radius;
			if (!channels.inChannel(x, z)) {
				continue;
			}
			double px = floor.camp().getX() + x + 0.5;
			double pz = floor.camp().getZ() + z + 0.5;
			level.sendParticles(ParticleTypes.LAVA, px, y, pz, 3, 0.3, 0.1, 0.3, 0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, px, y + 0.5, pz, 2, 0.3, 0.3, 0.3, 0.01);
		}
		for (ServerPlayer player : level.players()) {
			if (floor.contains(player.blockPosition())) {
				level.playSound(null, player.blockPosition(), SoundEvents.LAVA_AMBIENT, SoundSource.AMBIENT, 1.5F, 0.5F);
			}
		}
	}

	/**
	 * Raises the lava {@code riseBlocks} in every channel column, swallowing air and low bridge decks (stone arches and
	 * banks stay). Returns the number of blocks turned to lava.
	 */
	public static int rise(MinecraftServer server, PlacedFloor floor) {
		ServerLevel level = level(server, floor);
		LavaChannels.Plan channels = channels(floor);
		if (level == null || channels == null) {
			return 0;
		}
		int lava = lavaY(floor);
		int top = lava + Math.max(0, WorldFloors.config().infernal.riseBlocks);
		int radius = floor.radius();
		BlockState lavaBlock = Blocks.LAVA.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int changed = 0;
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (x * x + z * z > radius * radius || !channels.inChannel(x, z)) {
					continue;
				}
				for (int y = lava + 1; y <= top; y++) {
					BlockState state = level.getBlockState(pos.set(floor.camp().getX() + x, y, floor.camp().getZ() + z));
					if (state.isAir() || state.is(InfernalFeatures.LOW_DECK.getBlock()) || state.is(Blocks.FIRE)) {
						level.setBlock(pos, lavaBlock, FLAGS);
						changed++;
					}
				}
			}
		}
		for (ServerPlayer player : level.players()) {
			if (floor.contains(player.blockPosition())) {
				level.playSound(null, player.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.AMBIENT, 1.2F, 0.6F);
			}
		}
		return changed;
	}

	/** Whether the lava rise is counting down on this act's floor. */
	public static boolean pending(int actOrdinal) {
		return pending.containsKey(actOrdinal);
	}
}
