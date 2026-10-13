package io.github.tmldude.blocksouls.world.floor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.event.ActEvents;
import io.github.tmldude.blocksouls.common.event.FloorEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.floor.FloorRequest;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.floor.Floors;
import io.github.tmldude.blocksouls.common.floor.PlacedFloor;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;

/**
 * The floors run shape's world side (PLAN2 §1): the {@link Floors} provider that places a floor per act in the floor
 * dimension, the death plane, the exit portal, resource nodes and crate tracking. The camp's horn and shopkeeper and
 * the event / mini-boss objects are placed by the game's Director from the floor's slots when the act is entered.
 */
public final class WorldFloors {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, CommonIds.FLOORS_DIMENSION);
	/** Ticks between portal trips for one player (stops a lit portal re-firing while they stand in it). */
	private static final int PORTAL_COOLDOWN = 40;

	private static FloorsConfig config = new FloorsConfig();
	private static final Map<UUID, Long> portalCooldown = new HashMap<>();
	private static @Nullable ResourceKey<Level> testLevel;

	private WorldFloors() {
	}

	public static FloorsConfig config() {
		return config;
	}

	/**
	 * Builds floors in another level instead of the floors dimension (null restores it). Only for game tests: the
	 * game-test server bakes no datapack dimensions, so the floors dimension doesn't exist there.
	 */
	public static void useLevelForTests(@Nullable ResourceKey<Level> level) {
		testLevel = level;
	}

	static ResourceKey<Level> levelKey() {
		return testLevel != null ? testLevel : DIMENSION;
	}

	private static @Nullable ServerLevel level(MinecraftServer server) {
		return server.getLevel(levelKey());
	}

	public static void init() {
		config = Config.load(FloorsConfig.FILE, FloorsConfig.class, FloorsConfig::new);
		FloorBlocks.init();
		FloorTemplateCatalog.register();
		SkyriftFloor.init();
		Floors.setProvider(new Floors.Provider() {
			@Override
			public Optional<PlacedFloor> open(MinecraftServer server, FloorRequest request) {
				return WorldFloors.open(server, request);
			}

			@Override
			public List<PlacedFloor> all(MinecraftServer server) {
				return List.copyOf(FloorData.get(server).floors);
			}
		});
		RunEvents.RUN_STARTED.register((server, state) -> FloorData.get(server).reset(0));
		ActEvents.PORTAL_CHARGE_COMPLETED.register((server, act) -> Floors.forAct(server, act.ordinal()).ifPresent(floor -> lightPortal(server, floor)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				deathPlane(server);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			if (!player.level().dimension().equals(levelKey())) {
				sendToCurrentFloor(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sendToCurrentFloor(newPlayer));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
				openedCrate(serverLevel, hit.getBlockPos(), serverPlayer);
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether a floors run is going: a run is active and has placed a floor. */
	public static boolean active(MinecraftServer server) {
		return Runs.get(server).isActive() && !FloorData.get(server).floors.isEmpty();
	}

	// ---------------------------------------------------------------- placing floors

	/** Places the act's floor (or returns the one already placed for it this run). */
	public static Optional<PlacedFloor> open(MinecraftServer server, FloorRequest request) {
		FloorData data = FloorData.get(server);
		for (PlacedFloor floor : data.floors) {
			if (floor.actOrdinal() == request.actOrdinal()) {
				return Optional.of(floor);
			}
		}
		ServerLevel level = level(server);
		if (level == null) {
			BlockSoulsWorld.LOGGER.warn("The floor dimension {} isn't loaded", CommonIds.FLOORS_DIMENSION);
			return Optional.empty();
		}
		if (data.runSeed == 0) {
			data.runSeed = request.seed() != 0 ? request.seed() : server.overworld().getRandom().nextLong() | 1;
		}
		long floorSeed = (request.seed() != 0 ? request.seed() : data.runSeed) * 6364136223846793005L + request.floorNumber();
		FloorTemplate template = FloorPlanner.pick(FloorTemplateCatalog.all(), request.pool().toString(), request.floorNumber(), floorSeed);
		if (template == null) {
			BlockSoulsWorld.LOGGER.warn("No floor template in pool {} for floor {}", request.pool(), request.floorNumber());
			return Optional.empty();
		}
		FloorPlanner.FloorPlan plan = FloorPlanner.plan(template, config, request.floorNumber(), request.players(), floorSeed);
		int cell = data.nextCell++;
		int columns = Math.max(1, config.gridColumns);
		BlockPos centre = new BlockPos((cell % columns) * config.gridSpacing, plan.groundY(), (cell / columns) * config.gridSpacing);
		List<FloorSlot> slots = new FloorBuilder(level, template, plan, config, centre, floorSeed).build();
		BlockPos camp = slots.stream().filter(slot -> slot.kind() == FloorSlotKind.CAMP).findFirst().map(FloorSlot::pos).orElse(centre.above());
		Identifier templateId = Identifier.tryParse(template.id);
		PlacedFloor floor = new PlacedFloor(request.actOrdinal(), request.floorNumber(), request.pool(),
				templateId != null ? templateId : BlockSoulsWorld.id("unknown"), level.dimension().identifier(), camp, plan.radius(), plan.groundY(),
				floorSeed, plan.tags(), slots);
		data.floors.add(floor);
		data.setDirty();
		BlockSoulsWorld.LOGGER.info("Placed floor {} ({}) for act {} at {}", request.floorNumber(), template.id, request.actOrdinal(), camp.toShortString());
		FloorEvents.FLOOR_OPENED.invoker().onFloor(server, floor);
		if (request.actOrdinal() == 1) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				sendToCamp(player, floor);
			}
		}
		return Optional.of(floor);
	}

	public static void sendToCamp(ServerPlayer player, PlacedFloor floor) {
		ServerLevel level = level(player.level().getServer());
		if (level == null) {
			return;
		}
		BlockPos camp = floor.camp();
		player.teleportTo(level, camp.getX() + 0.5, camp.getY(), camp.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		player.resetFallDistance();
	}

	/** Sends a player who joined or respawned during a floors run to the current act's camp. */
	private static void sendToCurrentFloor(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (active(server)) {
			Floors.forAct(server, Acts.current(server).ordinal()).ifPresent(floor -> sendToCamp(player, floor));
		}
	}

	// ---------------------------------------------------------------- the death plane

	private static void deathPlane(MinecraftServer server) {
		ServerLevel level = level(server);
		if (level == null || level.players().isEmpty()) {
			return;
		}
		List<PlacedFloor> floors = FloorData.get(server).floors;
		for (ServerPlayer player : List.copyOf(level.players())) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			PlacedFloor floor = nearest(floors, player.blockPosition());
			if (floor != null && player.getY() < floor.groundY() - config.deathPlaneDepth) {
				fellOff(player, floor);
			}
		}
	}

	private static @Nullable PlacedFloor nearest(List<PlacedFloor> floors, BlockPos pos) {
		PlacedFloor best = null;
		long bestDistance = Long.MAX_VALUE;
		for (PlacedFloor floor : floors) {
			long dx = pos.getX() - floor.camp().getX();
			long dz = pos.getZ() - floor.camp().getZ();
			if (dx * dx + dz * dz < bestDistance) {
				bestDistance = dx * dx + dz * dz;
				best = floor;
			}
		}
		return best;
	}

	/** Falling off the island: back to the camp, minus a little health (never below one). */
	public static void fellOff(ServerPlayer player, PlacedFloor floor) {
		sendToCamp(player, floor);
		player.setHealth(Math.max(1.0F, player.getHealth() - Math.max(0, config.fallPenalty)));
		player.sendOverlayMessage(Component.translatable("message.blocksouls_world.floor.fell"));
	}

	// ---------------------------------------------------------------- the exit portal

	static void usePortal(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		Optional<PlacedFloor> floor = Floors.at(level.getServer(), level.dimension().identifier(), pos);
		if (floor.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.floor.portal_stray"));
		} else if (state.getValue(FloorPortalBlock.LIT)) {
			enterPortal(level, pos, player);
		} else {
			FloorEvents.EXIT_PORTAL_USED.invoker().onPortalUsed(player, floor.get());
		}
	}

	static void enterPortal(ServerLevel level, BlockPos pos, ServerPlayer player) {
		long now = level.getGameTime();
		Long last = portalCooldown.get(player.getUUID());
		if (last != null && now - last < PORTAL_COOLDOWN) {
			return;
		}
		portalCooldown.put(player.getUUID(), now);
		Optional<PlacedFloor> floor = Floors.at(level.getServer(), level.dimension().identifier(), pos);
		Optional<PlacedFloor> next = floor.flatMap(f -> Floors.forAct(level.getServer(), f.actOrdinal() + 1));
		if (next.isPresent()) {
			sendToCamp(player, next.get());
			level(level.getServer()).playSound(null, next.get().camp(), SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.4F, 1.2F);
		} else {
			player.sendOverlayMessage(Component.translatable("message.blocksouls_world.floor.portal_end"));
		}
	}

	/** Lights a floor's exit portal (its charge completed) and fires {@code EXIT_PORTAL_LIT}. */
	public static void lightPortal(MinecraftServer server, PlacedFloor floor) {
		ServerLevel level = level(server);
		if (level == null || !FloorData.get(server).litPortals.add(floor.actOrdinal())) {
			return;
		}
		FloorData.get(server).setDirty();
		for (FloorSlot slot : floor.slots(FloorSlotKind.EXIT_PORTAL)) {
			for (int y = 0; y <= 2; y++) {
				BlockPos pos = slot.pos().above(y);
				if (level.getBlockState(pos).is(FloorBlocks.FLOOR_PORTAL)) {
					level.setBlock(pos, FloorBlocks.FLOOR_PORTAL.defaultBlockState().setValue(FloorPortalBlock.LIT, true), Block.UPDATE_CLIENTS);
				}
			}
			level.playSound(null, slot.pos(), SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
		FloorEvents.EXIT_PORTAL_LIT.invoker().onFloor(server, floor);
	}

	// ---------------------------------------------------------------- nodes and crates

	/** Pops a node: removes it, bursts its loot table ({@code <ns>:nodes/<node>}) and fires {@code NODE_POPPED}. */
	static void popNode(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		Identifier node = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, node.withPrefix("nodes/"));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		for (int roll = 0; roll < Math.max(1, config.nodeBurstRolls); roll++) {
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
					.withLuck(player.getLuck()).create(LootContextParamSets.CHEST);
			for (ItemStack stack : table.getRandomItems(params)) {
				Block.popResource(level, pos, stack);
			}
		}
		level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
		FloorEvents.NODE_POPPED.invoker().onNodePopped(player, Floors.at(level.getServer(), level.dimension().identifier(), pos).orElse(null), pos, node);
	}

	/** A player used a block: if it is one of a floor's crates, opened for the first time, fire {@code CRATE_OPENED}. */
	static void openedCrate(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!level.dimension().equals(levelKey())) {
			return;
		}
		FloorData data = FloorData.get(level.getServer());
		if (data.openedCrates.contains(pos.asLong())) {
			return;
		}
		for (PlacedFloor floor : data.floors) {
			if (!floor.contains(pos)) {
				continue;
			}
			for (FloorSlot slot : floor.slots(FloorSlotKind.CRATE)) {
				if (slot.pos().equals(pos)) {
					data.openedCrates.add(pos.asLong());
					data.setDirty();
					Identifier loot = Identifier.tryParse(slot.tag());
					FloorEvents.CRATE_OPENED.invoker().onCrateOpened(player, floor, pos, loot != null ? loot : BlockSoulsWorld.id("chests/floor_crate"));
					return;
				}
			}
		}
	}
}
